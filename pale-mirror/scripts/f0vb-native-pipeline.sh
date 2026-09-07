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
After either a completed or failed dispatch, always run cleanup. It verifies
each recorded PID still owns its exact runner directory before signalling its
process group, removes local registration state with GitHub's removal token,
then proves no exact task runner remains registered or alive. It preserves
logs, capacity receipts, work roots and runner binaries for review. Tokens are
read only into process memory and are never written or echoed.
USAGE
}

absolute_root() {
  [[ "$1" = /* && "$1" != *$'\n'* && "$1" != *$'\r'* ]] || { echo 'task root must be absolute' >&2; exit 2; }
}

require_qualification() {
  [[ "$1" =~ ^[A-Za-z0-9._-]{1,120}$ ]] || { echo 'qualification id is malformed' >&2; exit 2; }
}

runner_inventory() {
  gh api "repos/$F0VB_REPOSITORY/actions/runners" --paginate
}

assert_runner_inventory() {
  local expected="$1" readiness="$2" inventory
  inventory="$(runner_inventory)"
  node -e '
    const source=JSON.parse(process.argv[1]).runners ?? [];
    const task=source.filter((runner)=>/^pm-f0vb-[0-3]$/.test(runner.name));
    const expected=Number(process.argv[2]); const readiness=process.argv[3] === "ready";
    if (task.length !== expected) throw new Error(`expected ${expected} task runners, found ${task.length}`);
    if (expected === 4) {
      const names=task.map((runner)=>runner.name).sort().join(",");
      if (names !== "pm-f0vb-0,pm-f0vb-1,pm-f0vb-2,pm-f0vb-3") throw new Error("task runner names are not exact");
      if (readiness && task.some((runner)=>runner.status !== "online" || runner.busy || !runner.labels.some((label)=>label.name === "pm-native"))) throw new Error("task runners are not all online, idle pm-native slots");
    }
  ' "$inventory" "$expected" "$readiness"
}

start() {
  local qualification="$1" root="$2" archive="$3" token index runner pid receipt
  require_qualification "$qualification"; absolute_root "$root"
  [[ -f "$archive" && "$archive" = /* ]] || { echo 'runner archive must be an absolute regular file' >&2; exit 2; }
  mkdir -p "$root"
  receipt="$root/f0vb-capacity-${qualification}.json"
  node "$project_root/tools/frontier-v3-test-pilot/src/f0vb-capacity-preflight.mjs" --root="$root" --workers=4 --output="$receipt"
  assert_runner_inventory 0 absent || { echo 'task-owned runner is already registered; cleanup or inspect it first' >&2; exit 1; }
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
    if assert_runner_inventory 4 ready; then return 0; fi
    sleep 1
  done
  echo 'four task-owned runners did not become online; run cleanup' >&2; return 1
}

dispatch() {
  local qualification="$1"; require_qualification "$qualification"
  assert_runner_inventory 4 ready || { echo 'exactly four online idle task-owned runners are required before dispatch' >&2; exit 1; }
  gh workflow run "$F0VB_WORKFLOW" --repo "$F0VB_REPOSITORY" --ref "$(git -C "$project_root/.." rev-parse --abbrev-ref HEAD)" -f "qualification_id=$qualification"
}

cleanup() {
  local root="$1" index pid runner removal_token alive
  absolute_root "$root"
  for index in 0 1 2 3; do
    runner="$root/runner-$index"
    [[ -f "$root/runner-$index.pid" ]] || continue
    pid="$(<"$root/runner-$index.pid")"
    [[ "$pid" =~ ^[1-9][0-9]*$ ]] || { echo "invalid task-owned PID file for runner-$index" >&2; exit 1; }
    if kill -0 "$pid" 2>/dev/null; then
      [[ "$(readlink -f "/proc/$pid/cwd")" == "$runner" ]] || { echo "runner-$index PID does not own its runner directory" >&2; exit 1; }
      [[ "$(ps -o pgid= -p "$pid" | tr -d ' ')" == "$pid" ]] || { echo "runner-$index PID is not its process-group owner" >&2; exit 1; }
      kill -TERM -- "-$pid"
    fi
  done
  for _ in $(seq 1 20); do
    local alive=0
    for index in 0 1 2 3; do [[ -f "$root/runner-$index.pid" ]] && kill -0 "$(<"$root/runner-$index.pid")" 2>/dev/null && alive=1; done
    [[ "$alive" -eq 0 ]] && break
    sleep 1
  done
  [[ "$alive" -eq 0 ]] || { echo 'task-owned runner process survived graceful cleanup' >&2; exit 1; }
  removal_token="$(gh api --method POST "repos/$F0VB_REPOSITORY/actions/runners/remove-token" --jq .token)"
  [[ -n "$removal_token" ]] || { echo 'GitHub did not issue a runner removal token' >&2; exit 1; }
  for index in 0 1 2 3; do
    runner="$root/runner-$index"
    [[ -e "$runner/.runner" ]] || continue
    (cd "$runner" && ./config.sh remove --token "$removal_token") >/dev/null
    [[ ! -e "$runner/.runner" && ! -e "$runner/.credentials" && ! -e "$runner/.credentials_rsaparams" ]] || { echo "runner-$index local registration state survived cleanup" >&2; exit 1; }
  done
  assert_runner_inventory 0 absent || { echo 'task-owned runner remained registered after cleanup' >&2; exit 1; }
}

status() {
  absolute_root "$1"
  runner_inventory | node -e 'const source=JSON.parse(require("fs").readFileSync(0,"utf8")).runners ?? []; for (const runner of source.filter((value)=>/^pm-f0vb-[0-3]$/.test(value.name))) console.log(JSON.stringify({id:runner.id,name:runner.name,status:runner.status,busy:runner.busy}));'
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
