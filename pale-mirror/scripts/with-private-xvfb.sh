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
namespace_parent=$(dirname "$runtime_parent")
[[ "$namespace_parent" != / && "$namespace_parent" != /tmp ]] || { printf 'private CI namespace parent is unsafe\n' >&2; exit 64; }
display=:$((port - 25000))
bwrap_bin=$(command -v bwrap || true)
[[ -x "$bwrap_bin" ]] || { printf 'private CI display requires bubblewrap for its task-owned /tmp; install bwrap\n' >&2; exit 2; }

# Xvfb's display locks and Unix sockets are hard-wired to /tmp. A task can have
# a valid private native root while the shared tmpfs is quota-bound, so place
# only the X server in a private mount namespace. X11's abstract Unix socket
# remains available in the shared IPC namespace, so the Minecraft command can
# use the assigned display from its ordinary host namespace without exposing a
# TCP listener. NeoForge's native/session facilities therefore retain the same
# launch behaviour as the accepted runtime. The X server's lock and filesystem
# socket can never escape into shared /tmp, while a client cannot accidentally
# use another worker's display because the display number is derived from the
# worker-owned pilot port.
set +e
bwrap_ready="$runtime_dir/ready"
"$bwrap_bin" --die-with-parent --bind / / --bind "$PWD" "$PWD" --bind "$namespace_parent" "$namespace_parent" \
  --dev /dev --proc /proc --tmpfs /tmp --chdir "$PWD" \
  bash -ceu '
    runtime_dir=$1; display=$2; xvfb_bin=$3; ready=$4
    "$xvfb_bin" "$display" -screen 0 1920x1080x24 -nolisten tcp >"$runtime_dir/xvfb.log" 2>&1 &
    xvfb_pid=$!
    cleanup() {
      kill "$xvfb_pid" 2>/dev/null || true
      wait "$xvfb_pid" 2>/dev/null || true
    }
    trap cleanup EXIT INT TERM
    for _ in $(seq 1 20); do
      kill -0 "$xvfb_pid" 2>/dev/null || break
      if [[ -S "/tmp/.X11-unix/X${display#:}" ]]; then break; fi
      sleep 0.1
    done
    kill -0 "$xvfb_pid" 2>/dev/null || { printf "private Xvfb failed; inspect %s/xvfb.log\\n" "$runtime_dir" >&2; exit 1; }
    [[ -S "/tmp/.X11-unix/X${display#:}" ]] || { printf "private Xvfb socket is absent\\n" >&2; exit 1; }
    printf "ready\\n" >"$ready"
    wait "$xvfb_pid"
  ' bash "$runtime_dir" "$display" "$xvfb_bin" "$bwrap_ready" &
bwrap_pid=$!
cleanup() {
  kill "$bwrap_pid" 2>/dev/null || true
  wait "$bwrap_pid" 2>/dev/null || true
}
trap cleanup EXIT INT TERM
for _ in $(seq 1 50); do
  [[ -s "$bwrap_ready" ]] && break
  kill -0 "$bwrap_pid" 2>/dev/null || break
  sleep 0.1
done
[[ -s "$bwrap_ready" ]] || { printf 'private Xvfb failed; inspect %s/xvfb.log\n' "$runtime_dir" >&2; exit 1; }
DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 "$@"
status=$?
set -e
trap - EXIT INT TERM
cleanup
rm -rf -- "$runtime_dir"
exit "$status"
