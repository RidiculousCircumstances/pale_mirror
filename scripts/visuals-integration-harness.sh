#!/usr/bin/env bash
set -euo pipefail

core_jar=${1:?usage: visuals-integration-harness.sh core.jar visuals.jar geckolib.jar villager-overhaul.jar supplementaries.jar moonlight.jar sable.jar create.jar}
visuals_jar=${2:?missing Pale Mirror Visuals JAR}
geckolib_jar=${3:?missing GeckoLib JAR}
villager_jar=${4:?missing Villager Overhaul JAR}
supplementaries_jar=${5:?missing Supplementaries JAR}
moonlight_jar=${6:?missing Moonlight JAR}
sable_jar=${7:?missing Sable JAR}
create_jar=${8:?missing Create JAR}
installer=${NEOFORGE_INSTALLER:-${XDG_CACHE_HOME:-${HOME}/.cache}/far-frontier/tools/neoforge-21.1.248-installer.jar}
java_bin=${PALE_MIRROR_JAVA:?PALE_MIRROR_JAVA must point to the Java 21 executable}
export PATH="$(dirname "$java_bin"):$PATH"
runtime_dir=$(mktemp -d "${TMPDIR:-/tmp}/pale-mirror-visuals.XXXXXX")
server_pid=''
command_fifo="$runtime_dir/server.stdin"

fail() {
  printf 'Visuals integration harness failed; retained runtime: %s\n' "$runtime_dir" >&2
  exit 1
}

for input in "$core_jar" "$visuals_jar" "$geckolib_jar" "$villager_jar" \
  "$supplementaries_jar" "$moonlight_jar" "$sable_jar" "$create_jar" "$installer"; do
  [[ -f "$input" ]] || { printf 'Missing required JAR: %s\n' "$input" >&2; fail; }
done

(
  cd "$runtime_dir"
  "$java_bin" -jar "$installer" --installServer . >/dev/null
)
printf 'eula=true\n' > "$runtime_dir/eula.txt"
printf 'online-mode=false\nserver-port=0\nview-distance=3\nsimulation-distance=3\nlevel-seed=3374619285067712046\n' > "$runtime_dir/server.properties"
mkdir "$runtime_dir/mods"
cp "$core_jar" "$visuals_jar" "$geckolib_jar" "$villager_jar" \
  "$supplementaries_jar" "$moonlight_jar" "$sable_jar" "$create_jar" "$runtime_dir/mods/"
mkfifo "$command_fifo"
exec 9<>"$command_fifo"

stop_server() {
  [[ -n "${server_pid:-}" ]] || return 0
  kill -KILL -- "-$server_pid" 2>/dev/null || true
  wait "$server_pid" 2>/dev/null || true
  server_pid=''
}
trap stop_server EXIT

start_server() {
  local log_file=$1
  setsid bash -c 'cd "$1" && exec ./run.sh nogui' harness "$runtime_dir" \
    <"$command_fifo" >"$log_file" 2>&1 &
  server_pid=$!
  for attempt in $(seq 1 120); do
    if rg -q 'Done \([^)]*\)!' "$log_file" && rg -q 'Installed authored manifest' "$log_file"; then
      rg -q 'Pale Mirror bootstrapped' "$log_file" || return 1
      rg -q 'Pale Mirror Visuals registered the fresh-world authored-region provider' "$log_file" || return 1
      ! rg -q 'Mod loading has failed|Missing or unsupported mandatory dependencies|NoClassDefFoundError|Caused by: java\.lang\.ClassNotFoundException' "$log_file" || return 1
      return 0
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then return 1; fi
    sleep 1
  done
  return 1
}

exercise_authored_worldgen() {
  local log_file=$1
  local coordinates
  coordinates=$(awk '/Installed authored manifest/ {
    for (i = 1; i <= NF; i++) if ($i == "at") { print $(i + 1), $(i + 3); exit }
  }' "$log_file")
  [[ -n "$coordinates" ]] || return 1
  printf 'forceload add %s\n' "$coordinates" >&9
  for attempt in $(seq 1 120); do
    if rg -q 'Observed first exact authored worldgen stamp' "$log_file"; then return 0; fi
    if ! kill -0 "$server_pid" 2>/dev/null; then return 1; fi
    sleep 1
  done
  return 1
}

stop_server_gracefully() {
  [[ -n "${server_pid:-}" ]] || return 0
  printf 'save-all flush\nstop\n' >&9
  for attempt in $(seq 1 60); do
    if ! kill -0 "$server_pid" 2>/dev/null; then
      wait "$server_pid" 2>/dev/null || true
      server_pid=''
      return 0
    fi
    sleep 1
  done
  stop_server
  return 1
}

start_server "$runtime_dir/first-start.log" || fail
exercise_authored_worldgen "$runtime_dir/first-start.log" || fail
stop_server_gracefully || fail
start_server "$runtime_dir/restart.log" || fail
exercise_authored_worldgen "$runtime_dir/restart.log" || fail
stop_server_gracefully || fail

printf 'Visuals packaged-JAR two-start harness passed.\n'
rm -rf -- "$runtime_dir"
