#!/usr/bin/env bash
set -euo pipefail

# Boots every client profile through a temporary virtual display. This is a
# render/bootstrap smoke, not an authenticated multiplayer gameplay test.
repo_dir=${1:?usage: client-smoke-harness.sh /absolute/path/to/pale-mirror-repo}
xvfb_bin=${PALE_MIRROR_XVFB:-}
if [[ -z "$xvfb_bin" ]]; then xvfb_bin=$(command -v Xvfb || true); fi
[[ -x "$xvfb_bin" ]] || { printf 'Client smoke requires Xvfb; set PALE_MIRROR_XVFB to its executable.\n' >&2; exit 2; }

runtime_dir=$(mktemp -d "${TMPDIR:-/tmp}/pale-mirror-client-smoke.XXXXXX")
display=:96
setsid "$xvfb_bin" "$display" -screen 0 1280x720x24 -nolisten tcp >"$runtime_dir/xvfb.log" 2>&1 &
xvfb_pid=$!
trap 'kill -TERM -- "-$xvfb_pid" 2>/dev/null || true' EXIT
sleep 1
kill -0 "$xvfb_pid" 2>/dev/null || { printf 'Xvfb failed: %s\n' "$runtime_dir" >&2; exit 1; }

fail() {
  printf 'Client smoke failed; retained runtime: %s\n' "$runtime_dir" >&2
  exit 1
}

smoke() {
  local profile=$1
  for launch_attempt in 1 2; do
    local log_file="$runtime_dir/$profile-$launch_attempt.log"
    setsid bash -c 'cd "$1" && DISPLAY="$2" LIBGL_ALWAYS_SOFTWARE=1 exec ./gradlew ":pale-mirror-neoforge:run${3}" --no-daemon' \
        harness "$repo_dir" "$display" "$profile" >"$log_file" 2>&1 &
    local client_pid=$!
    local ready=false
    for attempt in $(seq 1 90); do
      if rg -q 'Pale Mirror bootstrapped' "$log_file" && rg -q 'Sound engine started' "$log_file"; then ready=true; break; fi
      if rg -q 'Mod loading has failed|Exception in thread|Failed to initialize the mod loading system' "$log_file"; then break; fi
      if ! kill -0 "$client_pid" 2>/dev/null; then break; fi
      sleep 1
    done
    kill -TERM -- "-$client_pid" 2>/dev/null || true
    wait "$client_pid" 2>/dev/null || true
    if "$ready"; then return 0; fi
    if [[ "$launch_attempt" -eq 1 ]] && rg -q 'Crash during font initialization' "$log_file" \
        && rg -q 'FileSystemNotFoundException' "$log_file"; then
      # NeoForge's pre-mod early display can race its own union filesystem
      # teardown in headless llvmpipe. One fresh JVM retry is sufficient and
      # is deliberately limited to this signature; mod failures never retry.
      continue
    fi
    printf 'Profile %s did not reach client render bootstrap.\n' "$profile" >&2
    tail -80 "$log_file" >&2
    fail
  done
}

smoke CoreClient
smoke CrimsonClient
smoke SporeClient
smoke CreateClient
smoke FtbClient
smoke MillenaireClient
printf 'Client render smoke passed for core, Crimson, Spore, Create, FTB, and Millenaire profiles.\n'
rm -rf -- "$runtime_dir"
