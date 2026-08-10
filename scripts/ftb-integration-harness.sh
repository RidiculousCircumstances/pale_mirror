#!/usr/bin/env bash
set -euo pipefail

# Verifies the optional FTB presentation as a real packaged-server integration.
# The temporary runtime is retained deliberately for failed-test diagnosis.
mod_jar=${1:?usage: ftb-integration-harness.sh /absolute/path/to/pale_mirror.jar /absolute/path/to/ftb-quests.jar /absolute/path/to/ftb-library.jar /absolute/path/to/ftb-teams.jar /absolute/path/to/architectury.jar}
quests_jar=${2:?missing FTB Quests JAR}
library_jar=${3:?missing FTB Library JAR}
teams_jar=${4:?missing FTB Teams JAR}
architectury_jar=${5:?missing Architectury API JAR}
installer=${NEOFORGE_INSTALLER:-/home/rd/.cache/far-frontier/tools/neoforge-21.1.248-installer.jar}
java_bin=${PALE_MIRROR_JAVA:?PALE_MIRROR_JAVA must point to the Java 21 executable}
export PATH="$(dirname "$java_bin"):$PATH"
runtime_dir=$(mktemp -d "${TMPDIR:-/tmp}/pale-mirror-ftb.XXXXXX")
log_one="$runtime_dir/first-start.log"
log_two="$runtime_dir/restart.log"
chapter="$runtime_dir/config/ftbquests/quests/chapters/pale_mirror_first_living_region.snbt"

fail() {
  printf 'FTB presentation harness failed; retained runtime: %s\n' "$runtime_dir" >&2
  exit 1
}

for artifact in "$mod_jar" "$quests_jar" "$library_jar" "$teams_jar" "$architectury_jar" "$installer"; do
  [[ -f "$artifact" ]] || { printf 'Missing artifact: %s\n' "$artifact" >&2; fail; }
done
(
  cd "$runtime_dir"
  "$java_bin" -jar "$installer" --installServer . >/dev/null
)
printf 'eula=true\n' > "$runtime_dir/eula.txt"
printf 'online-mode=false\nserver-port=0\n' > "$runtime_dir/server.properties"
mkdir "$runtime_dir/mods"
cp "$mod_jar" "$quests_jar" "$library_jar" "$teams_jar" "$architectury_jar" "$runtime_dir/mods/"
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
[[ -f "$chapter" ]] || fail
rg -q 'pale_mirror_presentation_version: 1' "$chapter" || fail
stop_server

if ! start_server "$log_two"; then fail; fi
[[ -f "$chapter" ]] || fail
rg -q 'pale_mirror_presentation_version: 1' "$chapter" || fail
stop_server

printf 'FTB presentation packaged-JAR restart harness passed; retained runtime: %s\n' "$runtime_dir"
