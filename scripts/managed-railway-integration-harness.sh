#!/usr/bin/env bash
set -euo pipefail

mod_jar=${1:?usage: managed-railway-integration-harness.sh pale_mirror.jar create.jar railwaysuntold-pm.jar}
create_jar=${2:?missing Create JAR}
railway_jar=${3:?missing PM Railway Untold JAR}
installer=${NEOFORGE_INSTALLER:-/home/rd/.cache/far-frontier/tools/neoforge-21.1.248-installer.jar}
java_bin=${PALE_MIRROR_JAVA:?PALE_MIRROR_JAVA must point to Java 21}
export JAVA_ARGS="${JAVA_ARGS:-} -Dpale_mirror.profile=core-only"
export PATH="$(dirname "$java_bin"):$PATH"
runtime_dir=$(mktemp -d "${TMPDIR:-/tmp}/pale-mirror-railway.XXXXXX")
server_pid=''

fail() { printf 'Managed railway harness failed; retained runtime: %s\n' "$runtime_dir" >&2; exit 1; }
stop_server() {
  [[ -n "${server_pid:-}" ]] || return 0
  kill -KILL -- "-$server_pid" 2>/dev/null || true; wait "$server_pid" 2>/dev/null || true; server_pid=''
}
trap stop_server EXIT
for artifact in "$mod_jar" "$create_jar" "$railway_jar" "$installer"; do [[ -f "$artifact" ]] || fail; done
(
  cd "$runtime_dir"
  "$java_bin" -jar "$installer" --installServer . >/dev/null
)
printf 'eula=true\n' >"$runtime_dir/eula.txt"
printf 'online-mode=false\nserver-port=0\n' >"$runtime_dir/server.properties"
mkdir -p "$runtime_dir/mods" "$runtime_dir/config/railways-untold"
cp "$mod_jar" "$create_jar" "$railway_jar" "$runtime_dir/mods/"
printf '[features]\npaleMirrorManagedMode = true\n' >"$runtime_dir/config/railways-untold/config.toml"

start_server() {
  local log_file=$1
  setsid bash -c 'cd "$1" && exec ./run.sh nogui' harness "$runtime_dir" >"$log_file" 2>&1 & server_pid=$!
  for _ in $(seq 1 90); do
    if rg -q 'Done \([^)]*\)!' "$log_file"; then
      rg -q 'PM adapter pale_mirror:managed_railway: AVAILABLE' "$log_file" || return 1
      ! rg -q 'Mod loading has failed|NoClassDefFoundError|Missing mandatory' "$log_file" || return 1
      return 0
    fi
    kill -0 "$server_pid" 2>/dev/null || return 1; sleep 1
  done
  return 1
}

start_server "$runtime_dir/first-start.log" || fail
stop_server
start_server "$runtime_dir/restart.log" || fail
stop_server
printf 'Managed Railway packaged-JAR restart harness passed.\n'
rm -rf -- "$runtime_dir"
