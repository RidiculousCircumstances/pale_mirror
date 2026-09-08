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
bwrap_bin=$(command -v bwrap || true)
[[ -x "$bwrap_bin" ]] || { printf 'private CI display requires bubblewrap for its task-owned /tmp; install bwrap\n' >&2; exit 2; }

# Xvfb's display locks and Unix sockets are hard-wired to /tmp.  A task can
# have a valid private native root while the shared tmpfs is quota-bound, so
# run the X server and its sole client command together in a private mount
# namespace.  The checkout and declared process root are the only writable
# host mounts; the display socket and lock can never escape into shared /tmp.
# Keeping the command inside this one bubblewrap instance also makes the
# client unable to observe a display owned by a different CI consumer.
set +e
"$bwrap_bin" --die-with-parent --ro-bind / / --bind "$PWD" "$PWD" --bind "$runtime_parent" "$runtime_parent" \
  --dev /dev --proc /proc --tmpfs /tmp --chdir "$PWD" \
  bash -ceu '
    runtime_dir=$1; display=$2; xvfb_bin=$3; shift 3
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
    DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 "$@"
  ' bash "$runtime_dir" "$display" "$xvfb_bin" "$@"
status=$?
set -e
rm -rf -- "$runtime_dir"
exit "$status"
