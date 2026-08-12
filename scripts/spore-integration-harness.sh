#!/usr/bin/env bash
set -euo pipefail

# Boots the final PM JAR beside the exact pinned Spore JAR twice.  GameTests
# cover the scenario state; this harness proves that the packaged mixin and
# built-in isolation pack survive a real dedicated-server restart.
mod_jar=${1:?usage: spore-integration-harness.sh /absolute/path/to/pale_mirror.jar /absolute/path/to/spore.jar}
spore_jar=${2:?usage: spore-integration-harness.sh /absolute/path/to/pale_mirror.jar /absolute/path/to/spore.jar}
installer=${NEOFORGE_INSTALLER:-/home/rd/.cache/far-frontier/tools/neoforge-21.1.248-installer.jar}
java_bin=${PALE_MIRROR_JAVA:?PALE_MIRROR_JAVA must point to the Java 21 executable}
export JAVA_ARGS="${JAVA_ARGS:-} -Dpale_mirror.profile=core-only"
export PATH="$(dirname "$java_bin"):$PATH"
runtime_dir=$(mktemp -d "${TMPDIR:-/tmp}/pale-mirror-spore.XXXXXX")
log_one="$runtime_dir/first-start.log"
log_two="$runtime_dir/restart.log"

fail() {
  printf 'Spore integration harness failed; retained runtime: %s\n' "$runtime_dir" >&2
  exit 1
}

[[ -f "$mod_jar" ]] || { printf 'Missing Pale Mirror JAR: %s\n' "$mod_jar" >&2; fail; }
[[ -f "$spore_jar" ]] || { printf 'Missing Spore JAR: %s\n' "$spore_jar" >&2; fail; }
[[ -f "$installer" ]] || { printf 'Missing NeoForge installer: %s\n' "$installer" >&2; fail; }

(
  cd "$runtime_dir"
  "$java_bin" -jar "$installer" --installServer . >/dev/null
)
printf 'eula=true\n' > "$runtime_dir/eula.txt"
printf 'online-mode=false\nserver-port=0\n' > "$runtime_dir/server.properties"
mkdir "$runtime_dir/mods"
cp "$mod_jar" "$spore_jar" "$runtime_dir/mods/"
server_pid=''

stop_server() {
  [[ -n "${server_pid:-}" ]] || return 0
  kill -KILL -- "-$server_pid" 2>/dev/null || true
  wait "$server_pid" 2>/dev/null || true
  server_pid=''
}
trap stop_server EXIT

start_server() {
  local log_file=$1
  setsid bash -c 'cd "$1" && exec ./run.sh nogui' harness "$runtime_dir" >"$log_file" 2>&1 &
  server_pid=$!
  for attempt in $(seq 1 90); do
    if rg -q 'Done \([^)]*\)!' "$log_file"; then
      rg -q 'Pale Mirror bootstrapped' "$log_file" || return 1
      ! rg -q 'Mod loading has failed|Missing or unsupported mandatory dependencies|NoClassDefFoundError|Caused by: java\.lang\.ClassNotFoundException' "$log_file" || return 1
      return 0
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then return 1; fi
    sleep 1
  done
  return 1
}

if ! start_server "$log_one"; then fail; fi
stop_server

if ! start_server "$log_two"; then fail; fi
stop_server

printf 'Spore integration packaged-JAR restart harness passed.\n'
rm -rf -- "$runtime_dir"
