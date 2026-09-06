#!/usr/bin/env bash
set -euo pipefail

server_dir=${1:?usage: prepare-living-frontier-exercise.sh /absolute/server/dir --confirm [seed]}
confirmation=${2:-}
seed=${3:-pale-mirror-living-frontier-03}
[[ "$confirmation" == "--confirm" ]] || {
  printf 'This creates a fresh exercise world. Re-run with --confirm; the old world is moved to .pale-mirror-backups.\n' >&2
  exit 2
}
[[ -d "$server_dir" && -f "$server_dir/server.properties" ]] || {
  printf 'Not a Minecraft server directory: %s\n' "$server_dir" >&2
  exit 2
}
server_dir=$(cd "$server_dir" && pwd)
if pgrep -af 'java.*server' | rg -F "$server_dir" >/dev/null 2>&1; then
  printf 'Refusing to prepare an exercise while this server appears to be running.\n' >&2
  exit 2
fi

level_name=$(awk -F= '$1=="level-name" {print substr($0, index($0,"=")+1)}' "$server_dir/server.properties" | tail -1)
level_name=${level_name:-world}
[[ "$level_name" != /* && "$level_name" != *'..'* ]] || { printf 'Unsafe level-name: %s\n' "$level_name" >&2; exit 2; }
world_dir="$server_dir/$level_name"
backup_dir="$server_dir/.pale-mirror-backups"
mkdir -p "$backup_dir"
if [[ -e "$world_dir" ]]; then
  stamp=$(date +%Y%m%d-%H%M%S)
  mv -- "$world_dir" "$backup_dir/$level_name-$stamp"
fi

tmp_properties=$(mktemp "$server_dir/.server.properties.XXXXXX")
awk -v seed="$seed" '
  BEGIN { seen=0 }
  $0 ~ /^level-seed=/ { print "level-seed=" seed; seen=1; next }
  { print }
  END { if (!seen) print "level-seed=" seed }
' "$server_dir/server.properties" > "$tmp_properties"
mv -- "$tmp_properties" "$server_dir/server.properties"

printf 'Fresh Living Frontier exercise prepared. Seed: %s\n' "$seed"
printf 'Enable local metrics for the next start with JAVA_ARGS="-Dpale_mirror.playtest_metrics=true".\n'
