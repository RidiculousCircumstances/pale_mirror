#!/usr/bin/env bash
# On-demand operational path for the bounded F0.VB four-runner qualification.
# It deliberately leaves logs, receipts and runner installations in place for review.
set -euo pipefail

readonly F0VB_REPOSITORY='RidiculousCircumstances/pale_mirror'
readonly F0VB_WORKFLOW='f0vb-native-qualification.yml'
script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
project_root="$(cd -- "$script_dir/.." && pwd)"

usage() {
  cat <<'USAGE'
Usage:
  f0vb-native-pipeline.sh start <qualification-id> <absolute-task-root> <runner-archive>
  f0vb-native-pipeline.sh dispatch <qualification-id>
  f0vb-native-pipeline.sh cleanup <absolute-task-root>
  f0vb-native-pipeline.sh status <absolute-task-root>

start performs the visible same-host capacity admission, registers exactly
pm-f0vb-0 through pm-f0vb-3 with the pm-native label, and starts only their
listeners. dispatch starts one qualification at the checked-out revision.
After either a completed or failed dispatch, always run cleanup; it terminates
only PID-file process groups below the supplied task root and deregisters only
the four exact task-owned runner names. It preserves logs, capacity receipts,
work roots and runner installations for review. Registration tokens are read
only into process memory and are never written or echoed.
USAGE
}

absolute_root() {
  [[ "$1" = /* && "$1" != *$'\n'* && "$1" != *$'\r'* ]] || { echo 'task root must be absolute' >&2; exit 2; }
}

require_qualification() {
  [[ "$1" =~ ^[A-Za-z0-9._-]{1,120}$ ]] || { echo 'qualification id is malformed' >&2; exit 2; }
}

runner_names_json() {
  gh api "repos/$F0VB_REPOSITORY/actions/runners" --paginate --jq '.runners[] | select(.name == "pm-f0vb-0" or .name == "pm-f0vb-1" or .name == "pm-f0vb-2" or .name == "pm-f0vb-3") | [.id,.name,.status,.busy] | @json'
}

start() {
  local qualification="$1" root="$2" archive="$3" token index runner pid receipt
  require_qualification "$qualification"; absolute_root "$root"
  [[ -f "$archive" && "$archive" = /* ]] || { echo 'runner archive must be an absolute regular file' >&2; exit 2; }
  mkdir -p "$root"
  receipt="$root/f0vb-capacity-${qualification}.json"
  node "$project_root/tools/frontier-v3-test-pilot/src/f0vb-capacity-preflight.mjs" --root="$root" --workers=4 --output="$receipt"
  [[ -z "$(runner_names_json)" ]] || { echo 'task-owned runner is already registered; cleanup or inspect it first' >&2; exit 1; }
  token="$(gh api --method POST "repos/$F0VB_REPOSITORY/actions/runners/registration-token" --jq .token)"
  [[ -n "$token" ]] || { echo 'GitHub did not issue a runner registration token' >&2; exit 1; }
  for index in 0 1 2 3; do
    runner="$root/runner-$index"
    mkdir -p "$runner" "$root/work-$index"
    if [[ ! -x "$runner/config.sh" ]]; then tar -xzf "$archive" -C "$runner"; fi
    [[ ! -e "$runner/.runner" ]] || { echo "runner-$index is still configured" >&2; exit 1; }
    (cd "$runner" && ./config.sh --unattended --url "https://github.com/$F0VB_REPOSITORY" --token "$token" --name "pm-f0vb-$index" --labels pm-native --work "$root/work-$index") >/dev/null
    (cd "$runner" && exec setsid ./run.sh) >"$root/runner-$index.listener.log" 2>&1 &
    pid="$!"; printf '%s\n' "$pid" > "$root/runner-$index.pid"
  done
  for _ in $(seq 1 60); do
    [[ "$(runner_names_json | wc -l)" -eq 4 ]] && runner_names_json | grep -q '"online"' && [[ "$(runner_names_json | grep -c '"online"')" -eq 4 ]] && return 0
    sleep 1
  done
  echo 'four task-owned runners did not become online; run cleanup' >&2; return 1
}

dispatch() {
  local qualification="$1"; require_qualification "$qualification"
  [[ "$(runner_names_json | grep -c '"online"')" -eq 4 ]] || { echo 'exactly four online task-owned runners are required before dispatch' >&2; exit 1; }
  gh workflow run "$F0VB_WORKFLOW" --repo "$F0VB_REPOSITORY" --ref "$(git -C "$project_root/.." rev-parse --abbrev-ref HEAD)" -f "qualification_id=$qualification"
}

cleanup() {
  local root="$1" index pid id
  absolute_root "$root"
  for index in 0 1 2 3; do
    [[ -f "$root/runner-$index.pid" ]] || continue
    pid="$(<"$root/runner-$index.pid")"
    [[ "$pid" =~ ^[1-9][0-9]*$ ]] || { echo "invalid task-owned PID file for runner-$index" >&2; exit 1; }
    if kill -0 "$pid" 2>/dev/null; then kill -TERM -- "-$pid"; fi
  done
  for _ in $(seq 1 20); do
    local alive=0
    for index in 0 1 2 3; do [[ -f "$root/runner-$index.pid" ]] && kill -0 "$(<"$root/runner-$index.pid")" 2>/dev/null && alive=1; done
    [[ "$alive" -eq 0 ]] && break
    sleep 1
  done
  while IFS=$'\t' read -r id name; do
    [[ "$id" =~ ^[0-9]+$ && "$name" =~ ^pm-f0vb-[0-3]$ ]] || { echo 'refusing to deregister a non-task runner' >&2; exit 1; }
    gh api --method DELETE "repos/$F0VB_REPOSITORY/actions/runners/$id" >/dev/null
  done < <(gh api "repos/$F0VB_REPOSITORY/actions/runners" --paginate --jq '.runners[] | select(.name == "pm-f0vb-0" or .name == "pm-f0vb-1" or .name == "pm-f0vb-2" or .name == "pm-f0vb-3") | [.id,.name] | @tsv')
}

status() {
  absolute_root "$1"
  runner_names_json
  for index in 0 1 2 3; do
    [[ -f "$1/runner-$index.pid" ]] && printf 'runner-%s pid=%s\n' "$index" "$(<"$1/runner-$index.pid")"
  done
}

case "${1:-help}" in
  start) [[ "$#" -eq 4 ]] || { usage >&2; exit 2; }; start "$2" "$3" "$4" ;;
  dispatch) [[ "$#" -eq 2 ]] || { usage >&2; exit 2; }; dispatch "$2" ;;
  cleanup) [[ "$#" -eq 2 ]] || { usage >&2; exit 2; }; cleanup "$2" ;;
  status) [[ "$#" -eq 2 ]] || { usage >&2; exit 2; }; status "$2" ;;
  help|--help|-h) usage ;;
  *) usage >&2; exit 2 ;;
esac
