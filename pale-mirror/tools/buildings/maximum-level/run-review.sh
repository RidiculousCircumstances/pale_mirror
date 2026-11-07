#!/usr/bin/env bash
set -euo pipefail
tool_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$tool_dir/../../.." && pwd)
output_dir="$project_dir/build/northwatch-maxlevel-20261108/review-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$output_dir"
draft_dir="$project_dir/pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/debug/pale-mirror/buildings/maximum_review"
# The authoring exporter refuses to overwrite a draft. Preserve the prior report
# outside this run's input path so the observer can require a fresh canonical export.
if [[ -d "$draft_dir" ]]; then mv -- "$draft_dir" "$output_dir/prior-draft"; fi
export VIBECRAFT_NATIVE_JAR="$project_dir/tools/vibecraft/client-mod/build/libs/vibecraft-neoforge-1.21.1-0.1.0-pm.1.jar"
export FRONTIER_V3_GRADLE="$tool_dir/gradle.sh"
cd "$project_dir"
scenario=${1:-tools/frontier-v3-test-pilot/scenarios/disposable-northwatch-maximum-levels.json}
node tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs \
  "$scenario" \
  "$output_dir/pilot.json" >"$output_dir/pilot.log" 2>&1 &
pilot_pid=$!
trap 'wait "$pilot_pid" || true' EXIT
uv run --project tools/vibecraft/mcp-server --frozen python "$tool_dir/review_client.py" \
  "$output_dir" >"$output_dir/mcp.log" 2>&1 &
probe_pid=$!
first_status=0
wait -n -p completed_pid "$pilot_pid" "$probe_pid" || first_status=$?
if [[ "$completed_pid" == "$pilot_pid" && "$first_status" != 0 ]]; then
  kill -TERM "$probe_pid" 2>/dev/null || true
  wait "$probe_pid" || true
  printf 'Native pilot failed; see %s/pilot.log\n' "$output_dir" >&2
  exit "$first_status"
fi
probe_status=0
if [[ "$completed_pid" == "$probe_pid" ]]; then probe_status=$first_status; else wait "$probe_pid" || probe_status=$?; fi
pilot_status=0
wait "$pilot_pid" || pilot_status=$?
trap - EXIT
printf 'Native layout review evidence: %s\n' "$output_dir"
[[ "$probe_status" == 0 && "$pilot_status" == 0 ]]
