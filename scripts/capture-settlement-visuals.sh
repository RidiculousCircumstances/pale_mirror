#!/usr/bin/env bash
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
server=127.0.0.1:25565
username=pmaudit
settlement_id=
anchor=
first_authored=false
output_root="$repo_dir/build/visual-audits"
frame_wait=10
width=1920
height=1080
radius=110
view_height=72
top_height=150
only_prefix=

usage() {
  cat <<'EOF'
Usage:
  scripts/capture-settlement-visuals.sh --settlement-id NAMESPACE:ID [options]
  scripts/capture-settlement-visuals.sh --anchor X,Y,Z [options]
  scripts/capture-settlement-visuals.sh --first-authored [options]

Options:
  --server HOST:PORT       Multiplayer server (default 127.0.0.1:25565)
  --username NAME          Dedicated permission-level-4 audit player (default pmaudit)
  --first-authored         Resolve the first authored settlement from the live server
  --output DIRECTORY       Output root (default build/visual-audits)
  --frame-wait SECONDS     Chunk/render settling time per view (default 10)
  --size WIDTHxHEIGHT      Capture size (default 1920x1080)
  --radius BLOCKS          Horizontal diagonal radius (default 110)
  --view-height BLOCKS     Diagonal camera height over anchor (default 72)
  --top-height BLOCKS      Top camera height over anchor (default 150)
  --only PREFIX            Capture only semantic view ids with this prefix

The client pack must already exist in pale-mirror-neoforge/build/runs/railway-client.
Set PALE_MIRROR_XVFB when Xvfb is not on PATH. Alternatively set
PALE_MIRROR_DISPLAY to an unlocked X11/Xwayland display. The audit player must be an op.
EOF
}

while (($#)); do
  case "$1" in
    --settlement-id) settlement_id=${2:?missing settlement id}; shift 2 ;;
    --anchor) anchor=${2:?missing anchor}; shift 2 ;;
    --first-authored) first_authored=true; shift ;;
    --server) server=${2:?missing server}; shift 2 ;;
    --username) username=${2:?missing username}; shift 2 ;;
    --output) output_root=${2:?missing output directory}; shift 2 ;;
    --frame-wait) frame_wait=${2:?missing frame wait}; shift 2 ;;
    --radius) radius=${2:?missing radius}; shift 2 ;;
    --view-height) view_height=${2:?missing view height}; shift 2 ;;
    --top-height) top_height=${2:?missing top height}; shift 2 ;;
    --only) only_prefix=${2:?missing view prefix}; shift 2 ;;
    --size)
      [[ ${2:-} =~ ^([0-9]+)x([0-9]+)$ ]] || { printf 'Invalid --size: %s\n' "${2:-}" >&2; exit 2; }
      width=${BASH_REMATCH[1]}; height=${BASH_REMATCH[2]}; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

mode_count=0
[[ -n "$settlement_id" ]] && mode_count=$((mode_count + 1))
[[ -n "$anchor" ]] && mode_count=$((mode_count + 1))
"$first_authored" && mode_count=$((mode_count + 1))
if [[ "$mode_count" -ne 1 ]]; then
  printf 'Specify exactly one of --settlement-id, --anchor or --first-authored.\n' >&2
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

external_display=${PALE_MIRROR_DISPLAY:-}
xvfb_bin=${PALE_MIRROR_XVFB:-}
if [[ -z "$external_display" ]]; then
  if [[ -z "$xvfb_bin" ]]; then xvfb_bin=$(command -v Xvfb || true); fi
  [[ -x "$xvfb_bin" ]] || {
    printf 'Visual audit requires Xvfb or PALE_MIRROR_DISPLAY pointing at an unlocked session.\n' >&2
    exit 2
  }
fi
command -v xwininfo >/dev/null || { printf 'Visual audit requires xwininfo.\n' >&2; exit 2; }
python3 -c 'from PIL import Image, ImageDraw' >/dev/null 2>&1 || {
  printf 'Visual audit requires Python Pillow for contact sheets.\n' >&2
  exit 2
}

game_dir="$repo_dir/pale-mirror-neoforge/build/runs/railway-client"
[[ -d "$game_dir/mods" ]] || {
  printf 'Audit client pack is missing: %s\nRun scripts/install-client.sh first.\n' "$game_dir" >&2
  exit 2
}
[[ -f "$game_dir/options.txt" ]] || touch "$game_dir/options.txt"
mkdir -p "$game_dir/logs" "$game_dir/screenshots"
upsert_option() {
  local key=$1 value=$2 file="$game_dir/options.txt"
  if rg -q "^${key}:" "$file"; then sed -i "s/^${key}:.*/${key}:${value}/" "$file"
  else printf '%s:%s\n' "$key" "$value" >>"$file"
  fi
}
upsert_option onboardAccessibility false
upsert_option tutorialStep none

safe_target=${settlement_id:-${anchor:-first-authored}}
safe_target=${safe_target//[^A-Za-z0-9_.-]/_}
stamp=$(date -u +%Y%m%dT%H%M%SZ)
output_dir="$output_root/${stamp}-${safe_target}"
mkdir -p "$output_dir"
client_log="$game_dir/logs/latest.log"
launcher_log="$output_dir/client-launch.log"
x11="$repo_dir/scripts/visual-audit-x11.py"

display=$external_display
xvfb_pid=
if [[ -z "$display" ]]; then
  for candidate in $(seq 90 109); do
    if [[ ! -S "/tmp/.X11-unix/X$candidate" ]]; then display=":$candidate"; break; fi
  done
  [[ -n "$display" ]] || { printf 'No free X11 display in :90..:109.\n' >&2; exit 1; }
  setsid "$xvfb_bin" "$display" -screen 0 "${width}x${height}x24" -nolisten tcp >"$output_dir/xvfb.log" 2>&1 &
  xvfb_pid=$!
fi
client_pid=
state_changed=false
hud_hidden=false
disabled_mod_sources=()
disabled_mod_targets=()
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
  if [[ -n "$xvfb_pid" ]]; then kill -TERM -- "-$xvfb_pid" 2>/dev/null || true; fi
  for index in "${!disabled_mod_sources[@]}"; do
    if [[ -f "${disabled_mod_targets[$index]}" ]]; then
      mv "${disabled_mod_targets[$index]}" "${disabled_mod_sources[$index]}"
    fi
  done
}
trap cleanup EXIT

# The audit deliberately renders only vanilla full-detail chunks. Client-only DH
# currently attempts an unsupported sync payload when the server-side mod is
# disabled, and cached LODs would make before/after captures nondeterministic.
mkdir -p "$output_dir/disabled-client-mods"
shopt -s nullglob
for client_mod in "$game_dir"/mods/DistantHorizons*.jar; do
  disabled_target="$output_dir/disabled-client-mods/$(basename "$client_mod")"
  mv "$client_mod" "$disabled_target"
  disabled_mod_sources+=("$client_mod")
  disabled_mod_targets+=("$disabled_target")
done
shopt -u nullglob
if [[ -n "$xvfb_pid" ]]; then
  sleep 1
  kill -0 "$xvfb_pid" 2>/dev/null || { printf 'Xvfb failed; inspect %s/xvfb.log\n' "$output_dir" >&2; exit 1; }
fi

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

if "$first_authored"; then
  before_settlements=$(wc -l <"$client_log")
  send_command 'pale_mirror debug settlements list'
  settlement_id=
  for _ in $(seq 1 20); do
    settlement_id=$(tail -n "+$((before_settlements + 1))" "$client_log" \
      | rg -o 'pale_mirror:iron_frontier_[a-f0-9]+_place' | head -1 || true)
    [[ -n "$settlement_id" ]] && break
    sleep 1
  done
  [[ -n "$settlement_id" ]] || {
    printf 'PM returned no authored settlement from the live server.\n' >&2
    cp "$client_log" "$output_dir/client-latest.log"
    exit 1
  }
  printf 'Resolved first authored settlement: %s\n' "$settlement_id"
fi

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
    if rg -q 'Unknown observed or authored settlement|Unknown observed settlement|Unknown physical settlement|requires an in-game operator' "$client_log"; then break; fi
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

captured_rows=()

capture_frame() {
  local name=$1 view_id=$2 kind=$3 target_id=$4 dimension=$5 x=$6 y=$7 z=$8 yaw=$9 pitch=${10}
  local before latest
  before=$(find "$game_dir/screenshots" -maxdepth 1 -type f -name '*.png' -printf '%T@ %p\n' 2>/dev/null \
    | sort -n | tail -1 | cut -d' ' -f2- || true)
  python3 "$x11" key F2
  latest=
  for _ in $(seq 1 20); do
    latest=$(find "$game_dir/screenshots" -maxdepth 1 -type f -name '*.png' -printf '%T@ %p\n' 2>/dev/null \
      | sort -n | tail -1 | cut -d' ' -f2- || true)
    if [[ -n "$latest" && "$latest" != "$before" && -s "$latest" ]] \
        && file "$latest" | rg -q 'PNG image data'; then
      first_size=$(stat -c '%s' "$latest")
      sleep 1
      second_size=$(stat -c '%s' "$latest" 2>/dev/null || printf 0)
      if [[ "$first_size" -eq "$second_size" ]]; then break; fi
    fi
    sleep 1
  done
  [[ -n "$latest" && "$latest" != "$before" && -s "$latest" ]] \
      && file "$latest" | rg -q 'PNG image data' \
      || { printf 'Minecraft did not finish saving %s.\n' "$name" >&2; exit 1; }
  cp "$latest" "$output_dir/$name.png"
  captured_rows+=("$view_id"$'\t'"$kind"$'\t'"$target_id"$'\t'"$dimension"$'\t'"$x"$'\t'"$y"$'\t'"$z"$'\t'"$yaw"$'\t'"$pitch"$'\t'"$name.png")
}

capture_view() {
  local name=$1 x=$2 y=$3 z=$4 yaw=$5 pitch=$6
  printf 'Capturing %-28s at %s,%s,%s...\n' "$name" "$x" "$y" "$z"
  send_command "execute in minecraft:overworld run tp $username $x $y $z $yaw $pitch"
  sleep "$frame_wait"
  capture_frame "$name" "$name" settlement "${settlement_id:-manual_anchor}" minecraft:overworld \
    "$x" "$y" "$z" "$yaw" "$pitch"
}

if [[ -n "$settlement_id" ]]; then
  before_views=$(wc -l <"$client_log")
  send_command "pale_mirror debug visual-audit list $settlement_id"
  audit_lines=
  previous_count=-1
  stable_count=0
  for _ in $(seq 1 20); do
    audit_lines=$(tail -n "+$((before_views + 1))" "$client_log" | rg 'PM_AUDIT_VIEW\|' || true)
    current_count=$(wc -l <<<"$audit_lines")
    if [[ "$current_count" -gt 0 && "$current_count" -eq "$previous_count" ]]; then
      stable_count=$((stable_count + 1))
      if [[ "$stable_count" -ge 2 ]]; then break; fi
    else
      stable_count=0
    fi
    previous_count=$current_count
    sleep 1
  done
  [[ -n "$audit_lines" ]] || { printf 'PM returned no semantic visual-audit views.\n' >&2; exit 1; }
  while IFS= read -r raw_line; do
    line=${raw_line#*PM_AUDIT_VIEW|}
    IFS='|' read -r view_id kind target_id dimension x y z yaw pitch <<<"$line"
    [[ -z "$only_prefix" || "$view_id" == "$only_prefix"* ]] || continue
    name=${view_id//\//__}
    printf 'Capturing %-28s at %s,%s,%s...\n' "$view_id" "$x" "$y" "$z"
    # The list response is the canonical semantic camera plan. Teleport from
    # its resolved coordinates instead of feeding the slash-delimited view ID
    # back through Brigadier's single-word argument parser.
    send_command "execute in $dimension run tp $username $x $y $z $yaw $pitch"
    sleep 2
    kill -0 "$client_pid" 2>/dev/null \
      || { printf 'Client exited while preparing semantic view %s.\n' "$view_id" >&2; exit 1; }
    sleep "$frame_wait"
    capture_frame "$name" "$view_id" "$kind" "$target_id" "$dimension" "$x" "$y" "$z" "$yaw" "$pitch"
  done <<<"$audit_lines"
  ((${#captured_rows[@]} > 0)) || {
    printf 'No semantic views matched --only %s.\n' "${only_prefix:-<unset>}" >&2
    exit 1
  }
else
  capture_view top "$anchor_x" "$((anchor_y + top_height))" "$anchor_z" 0 90
  capture_view south_east "$((anchor_x + radius))" "$((anchor_y + view_height))" "$((anchor_z + radius))" 135 28
  capture_view south_west "$((anchor_x - radius))" "$((anchor_y + view_height))" "$((anchor_z + radius))" -135 28
  capture_view north_east "$((anchor_x + radius))" "$((anchor_y + view_height))" "$((anchor_z - radius))" 45 28
  capture_view north_west "$((anchor_x - radius))" "$((anchor_y + view_height))" "$((anchor_z - radius))" -45 28
fi

python3 "$x11" key F1
hud_hidden=false
if [[ -n "$original_time" ]]; then send_command "time set $original_time"; fi
send_command "execute in minecraft:overworld run tp $username $original_x $original_y $original_z $original_yaw $original_pitch"
send_command "gamemode $original_gamemode $username"
state_changed=false
cp "$client_log" "$output_dir/client-latest.log"

view_rows=$(printf '%s\n' "${captured_rows[@]}")
TARGET="$safe_target" SETTLEMENT_ID="$settlement_id" ANCHOR="$anchor" SERVER="$server" USERNAME="$username" \
OUTPUT_DIR="$output_dir" VIEW_ROWS="$view_rows" python3 - <<'PY'
import json
import os
from pathlib import Path
from PIL import Image, ImageDraw

output = Path(os.environ["OUTPUT_DIR"])
views = []
for row in os.environ.get("VIEW_ROWS", "").splitlines():
    if not row:
        continue
    view_id, kind, target_id, dimension, x, y, z, yaw, pitch, filename = row.split("\t")
    views.append({
        "id": view_id, "kind": kind, "targetId": target_id, "dimension": dimension,
        "position": [int(x), int(y), int(z)], "yaw": float(yaw), "pitch": float(pitch),
        "file": filename,
    })
manifest = {
    "target": os.environ["TARGET"],
    "settlementId": os.environ["SETTLEMENT_ID"] or None,
    "anchor": [int(value) for value in os.environ["ANCHOR"].split(",")],
    "server": os.environ["SERVER"],
    "username": os.environ["USERNAME"],
    "views": views,
}
(output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

def contact_sheet(name, selected):
    if not selected:
        return
    thumb_w, thumb_h, label_h, columns = 480, 270, 28, 4
    rows = (len(selected) + columns - 1) // columns
    sheet = Image.new("RGB", (thumb_w * columns, (thumb_h + label_h) * rows), "#161616")
    draw = ImageDraw.Draw(sheet)
    for index, view in enumerate(selected):
        image = Image.open(output / view["file"]).convert("RGB")
        image.thumbnail((thumb_w, thumb_h))
        left = (index % columns) * thumb_w
        top = (index // columns) * (thumb_h + label_h)
        sheet.paste(image, (left + (thumb_w - image.width) // 2, top + (thumb_h - image.height) // 2))
        draw.text((left + 8, top + thumb_h + 6), view["id"], fill="white")
    sheet.save(output / name)

contact_sheet("contact-sheet-all.png", views)
for kind in sorted({view["kind"] for view in views}):
    contact_sheet(f"contact-sheet-{kind}.png", [view for view in views if view["kind"] == kind])
PY

printf 'Visual audit complete: %s\n' "$output_dir"
