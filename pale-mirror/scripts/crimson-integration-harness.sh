#!/usr/bin/env bash
set -euo pipefail

# Starts final artifacts in a disposable server. The Gradle task verifies the
# Crimson checksum before this script receives the JAR.
mod_jar=${1:?usage: crimson-integration-harness.sh /absolute/path/to/pale_mirror.jar /absolute/path/to/crimson.jar}
crimson_jar=${2:?usage: crimson-integration-harness.sh /absolute/path/to/pale_mirror.jar /absolute/path/to/crimson.jar}
installer=${NEOFORGE_INSTALLER:-${XDG_CACHE_HOME:-${HOME}/.cache}/far-frontier/tools/neoforge-21.1.248-installer.jar}
java_bin=${PALE_MIRROR_JAVA:?PALE_MIRROR_JAVA must point to the Java 21 executable}
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dpale_mirror.profile=core-only"
export PATH="$(dirname "$java_bin"):$PATH"
runtime_dir=$(mktemp -d "${TMPDIR:-/tmp}/pale-mirror-crimson.XXXXXX")
log_one="$runtime_dir/first-start.log"
log_two="$runtime_dir/restart.log"

fail() {
  printf 'Crimson integration harness failed; retained runtime: %s\n' "$runtime_dir" >&2
  exit 1
}

[[ -f "$mod_jar" ]] || { printf 'Missing Pale Mirror JAR: %s\n' "$mod_jar" >&2; fail; }
[[ -f "$crimson_jar" ]] || { printf 'Missing Crimson JAR: %s\n' "$crimson_jar" >&2; fail; }
[[ -f "$installer" ]] || { printf 'Missing NeoForge installer: %s\n' "$installer" >&2; fail; }

(
  cd "$runtime_dir"
  "$java_bin" -jar "$installer" --installServer . >/dev/null
)
printf 'eula=true\n' > "$runtime_dir/eula.txt"
printf 'online-mode=false\nserver-port=0\n' > "$runtime_dir/server.properties"
mkdir "$runtime_dir/mods"
cp "$mod_jar" "$crimson_jar" "$runtime_dir/mods/"
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
printf 'Crimson integration packaged-JAR restart harness passed.\n'
rm -rf -- "$runtime_dir"
