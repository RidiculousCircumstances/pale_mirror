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
probe="$PWD/scripts/private-xvfb-glx-probe.py"
[[ -f "$probe" ]] || { printf 'private CI graphics probe is absent: %s\n' "$probe" >&2; exit 2; }
python_bin=$(command -v python3 || true)
[[ -x "$python_bin" ]] || { printf 'private CI display requires python3 for the GLX admission probe\n' >&2; exit 2; }

# Xvfb's display locks and Unix sockets are hard-wired to /tmp. The consumer
# must therefore run in the same task-private mount namespace as Xvfb: an
# abstract-socket assumption is not a graphics admission proof. The retained
# directory is bounded on exit and is the attribution root for either a failed
# GLX admission or a failed command.
set +e
"$bwrap_bin" --die-with-parent --bind / / --bind "$PWD" "$PWD" --bind "$namespace_parent" "$namespace_parent" \
  --dev /dev --proc /proc --tmpfs /tmp --chdir "$PWD" \
  bash -ceu '
    runtime_dir=$1; display=$2; xvfb_bin=$3; python_bin=$4; probe=$5
    shift 5
    xvfb_log="$runtime_dir/xvfb.log"; probe_receipt="$runtime_dir/glx-probe.json"; result="$runtime_dir/result"
    status=1
    finish() {
      status=$?
      kill "$xvfb_pid" 2>/dev/null || true
      wait "$xvfb_pid" 2>/dev/null || true
      if [[ -f "$xvfb_log" ]]; then tail -c 65536 "$xvfb_log" >"$xvfb_log.bounded"; mv "$xvfb_log.bounded" "$xvfb_log"; fi
      printf "status=%s\\ndisplay=%s\\n" "$status" "$display" >"$result"
      if (( status != 0 )); then printf "PM_PRIVATE_XVFB_DIAGNOSTIC=%s\\n" "$runtime_dir" >&2; fi
      exit "$status"
    }
    "$xvfb_bin" "$display" -screen 0 1920x1080x24 -nolisten tcp >"$xvfb_log" 2>&1 &
    xvfb_pid=$!
    trap finish EXIT INT TERM
    for _ in $(seq 1 20); do
      kill -0 "$xvfb_pid" 2>/dev/null || break
      [[ -S "/tmp/.X11-unix/X${display#:}" ]] && break
      sleep 0.1
    done
    kill -0 "$xvfb_pid" 2>/dev/null || { printf "private Xvfb exited before admission\\n" >&2; exit 1; }
    [[ -S "/tmp/.X11-unix/X${display#:}" ]] || { printf "private Xvfb socket is absent\\n" >&2; exit 1; }
    if ! DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 "$python_bin" "$probe" >"$probe_receipt" 2>&1; then
      printf "private Xvfb GLX admission was rejected\\n" >&2
      exit 1
    fi
    DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 "$@"
  ' bash "$runtime_dir" "$display" "$xvfb_bin" "$python_bin" "$probe" "$@"
status=$?
set -e
exit "$status"
