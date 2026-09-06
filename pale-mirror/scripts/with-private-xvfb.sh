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

runtime_parent=${RUNNER_TEMP:-${TMPDIR:-/tmp}}
runtime_dir=$(mktemp -d "$runtime_parent/frontier-v3-private-xvfb.XXXXXX")
display=:$((port - 25000))
setsid "$xvfb_bin" "$display" -screen 0 1920x1080x24 -nolisten tcp >"$runtime_dir/xvfb.log" 2>&1 &
xvfb_pid=$!
cleanup() {
  kill -TERM -- "-$xvfb_pid" 2>/dev/null || true
  wait "$xvfb_pid" 2>/dev/null || true
  rm -rf -- "$runtime_dir"
}
trap cleanup EXIT
sleep 1
kill -0 "$xvfb_pid" 2>/dev/null || { printf 'private Xvfb failed; inspect %s/xvfb.log\n' "$runtime_dir" >&2; exit 1; }

DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 "$@"
