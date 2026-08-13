#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
server=127.0.0.1:25565
username=PMAudit
settlement_id=
anchor=
output_root="$repo_dir/build/visual-audits"
frame_wait=10
width=1920
height=1080
radius=110
view_height=72
top_height=150

usage() {
  cat <<'EOF'
Usage:
  scripts/capture-settlement-visuals.sh --settlement-id NAMESPACE:ID [options]
  scripts/capture-settlement-visuals.sh --anchor X,Y,Z [options]

Options:
  --server HOST:PORT       Multiplayer server (default 127.0.0.1:25565)
  --username NAME          Dedicated permission-level-4 audit player (default PMAudit)
  --output DIRECTORY       Output root (default build/visual-audits)
  --frame-wait SECONDS     Chunk/render settling time per view (default 10)
  --size WIDTHxHEIGHT      Capture size (default 1920x1080)
  --radius BLOCKS          Horizontal diagonal radius (default 110)
  --view-height BLOCKS     Diagonal camera height over anchor (default 72)
  --top-height BLOCKS      Top camera height over anchor (default 150)

The client pack must already exist in pale-mirror-neoforge/build/runs/railway-client.
Set PALE_MIRROR_XVFB when Xvfb is not on PATH. The audit player must be an op.
EOF
}

while (($#)); do
  case "$1" in
    --settlement-id) settlement_id=${2:?missing settlement id}; shift 2 ;;
    --anchor) anchor=${2:?missing anchor}; shift 2 ;;
    --server) server=${2:?missing server}; shift 2 ;;
    --username) username=${2:?missing username}; shift 2 ;;
    --output) output_root=${2:?missing output directory}; shift 2 ;;
    --frame-wait) frame_wait=${2:?missing frame wait}; shift 2 ;;
    --radius) radius=${2:?missing radius}; shift 2 ;;
    --view-height) view_height=${2:?missing view height}; shift 2 ;;
    --top-height) top_height=${2:?missing top height}; shift 2 ;;
    --size)
      [[ ${2:-} =~ ^([0-9]+)x([0-9]+)$ ]] || { printf 'Invalid --size: %s\n' "${2:-}" >&2; exit 2; }
      width=${BASH_REMATCH[1]}; height=${BASH_REMATCH[2]}; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

if [[ -n "$settlement_id" && -n "$anchor" ]] || [[ -z "$settlement_id" && -z "$anchor" ]]; then
  printf 'Specify exactly one of --settlement-id or --anchor.\n' >&2
  exit 2
fi
[[ "$username" =~ ^[A-Za-z0-9_]{1,16}$ ]] || { printf 'Invalid Minecraft username: %s\n' "$username" >&2; exit 2; }
[[ "$server" =~ ^[^[:space:]:]+:[0-9]+$ ]] || { printf 'Invalid server address: %s\n' "$server" >&2; exit 2; }
[[ "$frame_wait" =~ ^[0-9]+$ && "$frame_wait" -ge 1 ]] || { printf 'Invalid frame wait: %s\n' "$frame_wait" >&2; exit 2; }
[[ "$radius" =~ ^[0-9]+$ && "$radius" -ge 8 ]] || { printf 'Invalid radius: %s\n' "$radius" >&2; exit 2; }
[[ "$view_height" =~ ^[0-9]+$ && "$view_height" -ge 8 ]] || { printf 'Invalid view height: %s\n' "$view_height" >&2; exit 2; }
[[ "$top_height" =~ ^[0-9]+$ && "$top_height" -ge 16 ]] || { printf 'Invalid top height: %s\n' "$top_height" >&2; exit 2; }
if [[ -n "$anchor" ]]; then
  [[ "$anchor" =~ ^(-?[0-9]+),(-?[0-9]+),(-?[0-9]+)$ ]] || { printf 'Invalid anchor: %s\n' "$anchor" >&2; exit 2; }
fi

xvfb_bin=${PALE_MIRROR_XVFB:-}
if [[ -z "$xvfb_bin" ]]; then xvfb_bin=$(command -v Xvfb || true); fi
[[ -x "$xvfb_bin" ]] || {
  printf 'Visual audit requires Xvfb; install it or set PALE_MIRROR_XVFB to its executable.\n' >&2
  exit 2
}
command -v xwininfo >/dev/null || { printf 'Visual audit requires xwininfo.\n' >&2; exit 2; }
python3 -c 'from PIL import ImageGrab' >/dev/null 2>&1 || {
  printf 'Visual audit requires Python Pillow with X11 ImageGrab support.\n' >&2
  exit 2
}

game_dir="$repo_dir/pale-mirror-neoforge/build/runs/railway-client"
[[ -d "$game_dir/mods" ]] || {
  printf 'Audit client pack is missing: %s\nRun scripts/install-client.sh first.\n' "$game_dir" >&2
  exit 2
}
[[ -f "$game_dir/options.txt" ]] || touch "$game_dir/options.txt"
upsert_option() {
  local key=$1 value=$2 file="$game_dir/options.txt"
  if rg -q "^${key}:" "$file"; then sed -i "s/^${key}:.*/${key}:${value}/" "$file"
  else printf '%s:%s\n' "$key" "$value" >>"$file"
  fi
}
upsert_option onboardAccessibility false
upsert_option tutorialStep none

safe_target=${settlement_id:-$anchor}
safe_target=${safe_target//[^A-Za-z0-9_.-]/_}
stamp=$(date -u +%Y%m%dT%H%M%SZ)
output_dir="$output_root/${stamp}-${safe_target}"
mkdir -p "$output_dir"
client_log="$game_dir/logs/latest.log"
launcher_log="$output_dir/client-launch.log"
x11="$repo_dir/scripts/visual-audit-x11.py"

display=
for candidate in $(seq 90 109); do
  if [[ ! -S "/tmp/.X11-unix/X$candidate" ]]; then display=":$candidate"; break; fi
done
[[ -n "$display" ]] || { printf 'No free X11 display in :90..:109.\n' >&2; exit 1; }

setsid "$xvfb_bin" "$display" -screen 0 "${width}x${height}x24" -nolisten tcp >"$output_dir/xvfb.log" 2>&1 &
xvfb_pid=$!
client_pid=
state_changed=false
hud_hidden=false
original_time=
original_x=
original_y=
original_z=
original_yaw=
original_pitch=
original_gamemode=creative
cleanup() {
  if "$state_changed" && [[ -n "$client_pid" ]] && kill -0 "$client_pid" 2>/dev/null; then
    set +e
    if "$hud_hidden"; then python3 "$x11" key F1 >/dev/null 2>&1; fi
    if [[ -n "$original_time" ]]; then send_command "time set $original_time" >/dev/null 2>&1; fi
    if [[ -n "$original_x" ]]; then
      send_command "execute in minecraft:overworld run tp $username $original_x $original_y $original_z $original_yaw $original_pitch" >/dev/null 2>&1
    fi
    send_command "gamemode $original_gamemode $username" >/dev/null 2>&1
    set -e
  fi
  if [[ -n "$client_pid" ]]; then kill -TERM -- "-$client_pid" 2>/dev/null || true; wait "$client_pid" 2>/dev/null || true; fi
  kill -TERM -- "-$xvfb_pid" 2>/dev/null || true
}
trap cleanup EXIT
sleep 1
kill -0 "$xvfb_pid" 2>/dev/null || { printf 'Xvfb failed; inspect %s/xvfb.log\n' "$output_dir" >&2; exit 1; }

printf 'Launching visual-audit client for %s at %s...\n' "$username" "$server"
: >"$client_log"
setsid bash -c 'cd "$1" && DISPLAY="$2" LIBGL_ALWAYS_SOFTWARE=1 exec ./gradlew \
  :pale-mirror-neoforge:runVisualAuditClient --no-daemon \
  -PvisualAuditUsername="$3" -PvisualAuditServer="$4"' \
  audit "$repo_dir" "$display" "$username" "$server" >"$launcher_log" 2>&1 &
client_pid=$!

connected=false
for _ in $(seq 1 150); do
  if [[ -f "$client_log" ]] && rg -q 'JourneyMap: Press|Client on ClientOnly mode connecting|Pale Mirror Atlas snapshot' "$client_log"; then
    connected=true
    break
  fi
  if rg -q 'Mod loading has failed|Failed to connect|Connection refused|Exception in thread' "$launcher_log"; then break; fi
  kill -0 "$client_pid" 2>/dev/null || break
  sleep 1
done
if ! "$connected"; then
  printf 'Client did not enter the world; retained audit: %s\n' "$output_dir" >&2
  tail -100 "$launcher_log" >&2
  exit 1
fi
sleep 8

export DISPLAY="$display"
python3 "$x11" resize "$width" "$height"
send_command() { python3 "$x11" command "$1"; sleep 1; }
query_command() {
  local value=$1 pattern=$2 before line
  before=$(wc -l <"$client_log")
  send_command "$value"
  for _ in $(seq 1 15); do
    line=$(tail -n "+$((before + 1))" "$client_log" | rg "$pattern" | tail -1 || true)
    if [[ -n "$line" ]]; then printf '%s\n' "$line"; return 0; fi
    sleep 1
  done
  return 1
}

original_time_line=$(query_command 'time query daytime' 'The time is [0-9]+' || true)
original_time=$(sed -E 's/.*The time is ([0-9]+).*/\1/' <<<"$original_time_line")
original_position_line=$(query_command 'data get entity @s Pos' 'following entity data: \[' || true)
original_rotation_line=$(query_command 'data get entity @s Rotation' 'following entity data: \[' || true)
original_gamemode_line=$(query_command 'data get entity @s playerGameType' 'following entity data: [0-3]' || true)
original_position=$(sed -E 's/.*\[(-?[0-9.Ee+-]+)d, (-?[0-9.Ee+-]+)d, (-?[0-9.Ee+-]+)d\].*/\1,\2,\3/' <<<"$original_position_line")
original_rotation=$(sed -E 's/.*\[(-?[0-9.Ee+-]+)f, (-?[0-9.Ee+-]+)f\].*/\1,\2/' <<<"$original_rotation_line")
original_gamemode_id=$(sed -E 's/.*following entity data: ([0-3]).*/\1/' <<<"$original_gamemode_line")
[[ "$original_time" =~ ^[0-9]+$ ]] || original_time=
[[ "$original_position" =~ ^-?[0-9.Ee+-]+,-?[0-9.Ee+-]+,-?[0-9.Ee+-]+$ ]] || {
  printf 'Could not capture the audit player position before changing it.\n' >&2
  exit 1
}
[[ "$original_rotation" =~ ^-?[0-9.Ee+-]+,-?[0-9.Ee+-]+$ ]] || {
  printf 'Could not capture the audit player rotation before changing it.\n' >&2
  exit 1
}
IFS=, read -r original_x original_y original_z <<<"$original_position"
IFS=, read -r original_yaw original_pitch <<<"$original_rotation"
case "$original_gamemode_id" in
  0) original_gamemode=survival ;;
  1) original_gamemode=creative ;;
  2) original_gamemode=adventure ;;
  3) original_gamemode=spectator ;;
  *) printf 'Could not capture the audit player game mode.\n' >&2; exit 1 ;;
esac

if [[ -n "$settlement_id" ]]; then
  state_changed=true
  send_command "pale_mirror debug tp settlement $settlement_id"
  teleported=false
  for _ in $(seq 1 180); do
    if rg -Fq "Teleported to $settlement_id " "$client_log"; then teleported=true; break; fi
    if rg -q 'Unknown observed settlement|Unknown physical settlement|requires an in-game operator' "$client_log"; then break; fi
    kill -0 "$client_pid" 2>/dev/null || break
    sleep 1
  done
  if ! "$teleported"; then
    printf 'PM could not teleport to %s; verify the ID and permission level.\n' "$settlement_id" >&2
    cp "$client_log" "$output_dir/client-latest.log"
    exit 1
  fi
  anchor=$(rg -F "Teleported to $settlement_id " "$client_log" | tail -1 \
      | sed -E 's/.* at (-?[0-9]+),(-?[0-9]+),(-?[0-9]+).*/\1,\2,\3/')
fi

IFS=, read -r anchor_x anchor_y anchor_z <<<"$anchor"
[[ "$anchor_x" =~ ^-?[0-9]+$ && "$anchor_y" =~ ^-?[0-9]+$ && "$anchor_z" =~ ^-?[0-9]+$ ]] || {
  printf 'Could not resolve a numeric settlement anchor from: %s\n' "$anchor" >&2
  exit 1
}

state_changed=true
send_command "gamemode spectator $username"
send_command 'time set noon'
python3 "$x11" key F1
hud_hidden=true

capture_view() {
  local name=$1 x=$2 y=$3 z=$4 yaw=$5 pitch=$6
  printf 'Capturing %-12s at %s,%s,%s...\n' "$name" "$x" "$y" "$z"
  send_command "execute in minecraft:overworld run tp $username $x $y $z $yaw $pitch"
  sleep "$frame_wait"
  python3 "$x11" capture "$output_dir/$name.png"
}

capture_view top "$anchor_x" "$((anchor_y + top_height))" "$anchor_z" 0 90
capture_view south_east "$((anchor_x + radius))" "$((anchor_y + view_height))" "$((anchor_z + radius))" 135 28
capture_view south_west "$((anchor_x - radius))" "$((anchor_y + view_height))" "$((anchor_z + radius))" -135 28
capture_view north_east "$((anchor_x + radius))" "$((anchor_y + view_height))" "$((anchor_z - radius))" 45 28
capture_view north_west "$((anchor_x - radius))" "$((anchor_y + view_height))" "$((anchor_z - radius))" -45 28

python3 "$x11" key F1
hud_hidden=false
if [[ -n "$original_time" ]]; then send_command "time set $original_time"; fi
send_command "execute in minecraft:overworld run tp $username $original_x $original_y $original_z $original_yaw $original_pitch"
send_command "gamemode $original_gamemode $username"
state_changed=false
cp "$client_log" "$output_dir/client-latest.log"

TARGET="$safe_target" SETTLEMENT_ID="$settlement_id" ANCHOR="$anchor" SERVER="$server" USERNAME="$username" \
OUTPUT_DIR="$output_dir" python3 - <<'PY'
import json
import os
from pathlib import Path

output = Path(os.environ["OUTPUT_DIR"])
manifest = {
    "target": os.environ["TARGET"],
    "settlementId": os.environ["SETTLEMENT_ID"] or None,
    "anchor": [int(value) for value in os.environ["ANCHOR"].split(",")],
    "server": os.environ["SERVER"],
    "username": os.environ["USERNAME"],
    "views": [path.name for path in sorted(output.glob("*.png"))],
}
(output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
PY

printf 'Visual audit complete: %s\n' "$output_dir"
