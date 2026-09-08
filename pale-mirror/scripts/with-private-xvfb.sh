#!/usr/bin/env bash
set -euo pipefail

# CI runners deliberately provide only the X server binary.  Do not depend on
# distro-specific xvfb-run: bind a unique virtual display to the already
# assigned private pilot port so concurrent workers cannot collide.
[[ $# -gt 0 ]] || { printf 'usage: with-private-xvfb.sh command [args...]\n' >&2; exit 64; }

port=${FRONTIER_V3_PILOT_PORT:-}
case "$port" in
  ''|*[!0-9]*) printf 'FRONTIER_V3_PILOT_PORT must be numeric\n' >&2; exit 64 ;;
esac
(( port >= 25000 && port <= 65000 )) || { printf 'FRONTIER_V3_PILOT_PORT is outside the private CI range\n' >&2; exit 64; }

xvfb_bin=${PALE_MIRROR_XVFB:-}
if [[ -z "$xvfb_bin" ]]; then xvfb_bin=$(command -v Xvfb || true); fi
[[ -x "$xvfb_bin" ]] || { printf 'private CI display requires Xvfb; set PALE_MIRROR_XVFB to its executable\n' >&2; exit 2; }

runtime_parent=${FRONTIER_V3_NATIVE_PROCESS_ROOT:-${RUNNER_TEMP:-${TMPDIR:-/tmp}}}
[[ "$runtime_parent" = /* && -d "$runtime_parent" ]] || { printf 'private CI process root must be an existing absolute directory\n' >&2; exit 64; }
runtime_dir=$(mktemp -d "$runtime_parent/frontier-v3-private-xvfb.XXXXXX")
display=:$((port - 25000))
xvfb_pid_file="$runtime_dir/xvfb.pid"
# `setsid` must fork when an Actions step is already a process-group leader.
# Its short-lived launcher PID is then not the X server's session leader, so
# record the inner shell PID before exec and use that exact new-session PID for
# both readiness and group cleanup.
setsid bash -c 'printf "%s\\n" "$$" >"$1"; shift; exec "$@"' xvfb-session "$xvfb_pid_file" "$xvfb_bin" "$display" -screen 0 1920x1080x24 -nolisten tcp >"$runtime_dir/xvfb.log" 2>&1 &
launcher_pid=$!; xvfb_pid=
for _ in $(seq 1 20); do
  if [[ -s "$xvfb_pid_file" ]]; then
    xvfb_pid="$(<"$xvfb_pid_file")"
    [[ "$xvfb_pid" =~ ^[1-9][0-9]*$ ]] || { printf 'private Xvfb session PID is malformed\n' >&2; exit 1; }
    break
  fi
  kill -0 "$launcher_pid" 2>/dev/null || break
  sleep 0.1
done
cleanup() {
  if [[ -n "$xvfb_pid" ]] && [[ "$(ps -o pgid= -p "$xvfb_pid" 2>/dev/null | tr -d ' ')" == "$xvfb_pid" ]]; then kill -TERM -- "-$xvfb_pid" 2>/dev/null || true; fi
  wait "$launcher_pid" 2>/dev/null || true
  rm -rf -- "$runtime_dir"
}
trap cleanup EXIT
sleep 1
[[ -n "$xvfb_pid" ]] && kill -0 "$xvfb_pid" 2>/dev/null || { printf 'private Xvfb failed; inspect %s/xvfb.log\n' "$runtime_dir" >&2; exit 1; }
[[ "$(ps -o pgid= -p "$xvfb_pid" 2>/dev/null | tr -d ' ')" == "$xvfb_pid" ]] || { printf 'private Xvfb lacks an exact owned session\n' >&2; exit 1; }

DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 "$@"
