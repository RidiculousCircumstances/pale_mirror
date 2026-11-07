#!/usr/bin/env bash
set -euo pipefail
tool_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$tool_dir/../.." && pwd)
output_dir="$project_dir/build/vibecraft-native-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$output_dir"
export VIBECRAFT_NATIVE_JAR="$tool_dir/client-mod/build/libs/vibecraft-neoforge-1.21.1-0.1.0-pm.1.jar"
[[ -f "$VIBECRAFT_NATIVE_JAR" ]] || { echo 'Build client-mod first.' >&2; exit 1; }
export FRONTIER_V3_GRADLE="$tool_dir/native-gradle.sh"
export FRONTIER_V3_PILOT_GRAYBOX_DATAPACK_DIRECTORY="${FRONTIER_V3_PILOT_GRAYBOX_DATAPACK_DIRECTORY:?Set the existing pack graybox datapack directory}"
export FRONTIER_V3_PILOT_PORT="${FRONTIER_V3_PILOT_PORT:-25585}"
cd "$project_dir/tools/frontier-v3-test-pilot"
node src/run-isolated-scenario.mjs scenarios/disposable-vibecraft-bridge.json "$output_dir/pilot.json" >"$output_dir/pilot.log" 2>&1 &
pilot_pid=$!
trap 'wait "$pilot_pid" || true' EXIT
probe_status=0
uv run --project "$tool_dir/mcp-server" --frozen python "$tool_dir/probe-native.py" "$output_dir" >"$output_dir/mcp.log" 2>&1 &
probe_pid=$!
first_status=0
wait -n -p completed_pid "$pilot_pid" "$probe_pid" || first_status=$?
if [[ "$completed_pid" == "$pilot_pid" && "$first_status" != 0 ]]; then
  kill -TERM "$probe_pid" 2>/dev/null || true
  wait "$probe_pid" || true
  printf 'Native pilot failed; see %s/pilot.log\n' "$output_dir" >&2
  exit "$first_status"
fi
if [[ "$completed_pid" == "$probe_pid" ]]; then probe_status=$first_status; else wait "$probe_pid" || probe_status=$?; fi
pilot_status=0
wait "$pilot_pid" || pilot_status=$?
trap - EXIT
printf 'Native bridge evidence: %s\n' "$output_dir"
[[ "$probe_status" == 0 && "$pilot_status" == 0 ]]
