#!/usr/bin/env bash
# Starts one bounded Java 22/JFR benchmark session and reliably owns its process group.
set -euo pipefail

usage() {
  echo "Usage: $0 --target <server-directory> [--java <Java 22>] [--duration <seconds>]" >&2
}

target=""
java_bin=${JAVA_BIN:-java}
duration=600
while (($#)); do
  case "$1" in
    --target) target=${2:?--target requires a directory}; shift 2 ;;
    --java) java_bin=${2:?--java requires an executable}; shift 2 ;;
    --duration) duration=${2:?--duration requires seconds}; shift 2 ;;
    *) usage; exit 2 ;;
  esac
done
[[ -d "$target/libraries" && "$duration" =~ ^[1-9][0-9]*$ ]] || { usage; exit 2; }
"$java_bin" -version 2>&1 | grep -Eq '(version|openjdk) "22([."]|$)' || {
  echo "Benchmark runtime must be Java 22" >&2; exit 1;
}

mapfile -t active_servers < <(pgrep -af 'libraries/net/neoforged/neoforge/.*/unix_args.txt' || true)
if ((${#active_servers[@]})); then
  printf 'Refusing a contaminated benchmark; another NeoForge server is running:\n%s\n' "${active_servers[*]}" >&2
  exit 1
fi

unix_args=$(find "$target/libraries/net/neoforged/neoforge" -type f -name unix_args.txt -print -quit)
[[ -n "$unix_args" ]] || { echo "NeoForge unix_args.txt is missing" >&2; exit 1; }
evidence="$target/benchmark/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$evidence"
server_pid=""
telemetry_pid=""
cleanup() {
  [[ -z "$telemetry_pid" ]] || kill "$telemetry_pid" 2>/dev/null || true
  [[ -z "$server_pid" ]] || kill -- "-$server_pid" 2>/dev/null || true
  rm -f "$target/.far-frontier-benchmark.pid"
}
trap cleanup EXIT INT TERM

profile=stable
compgen -G "$target/mods/c2me-neoforge-*.jar" >/dev/null && profile=c2me
{
  "$java_bin" -version
  printf 'profile=%s\nduration=%s\nstarted=%s\n' "$profile" "$duration" "$(date -u +%FT%TZ)"
  grep -E '^(view-distance|simulation-distance|sync-chunk-writes)=' "$target/server.properties" || true
  if [[ -f "$target/config/c2me.toml" ]]; then
    grep -E '^(globalExecutorParallelism|[[:space:]]*maxConcurrentChunkLoads|[[:space:]]*useDensityFunctionCompiler|[[:space:]]*allowAVX512|[[:space:]]*enabled) =' "$target/config/c2me.toml" || true
  fi
  grep '^mixin\.perf\.optimize_surface_rules=' "$target/config/modernfix-mixins.properties" 2>/dev/null || true
  grep -E '^[[:space:]]*Rarity = 32$' "$target/config/quark-common.toml" 2>/dev/null | wc -l | awk '{print "quarkThinnedClusters=" $1}'
} >"$evidence/environment.txt" 2>&1

(
  cd "$target"
  exec setsid timeout --signal=INT --kill-after=30 "$duration" \
    "$java_bin" -Xms4G -Xmx12G \
    "-XX:StartFlightRecording=filename=$evidence/server.jfr,settings=profile,dumponexit=true,maxsize=1G" \
    "@$unix_args" nogui
) >"$evidence/server.log" 2>&1 &
server_pid=$!
printf '%s\n' "$server_pid" >"$target/.far-frontier-benchmark.pid"

if command -v pidstat >/dev/null; then
  pidstat -h -r -u -d -G java 1 >"$evidence/pidstat.txt" 2>&1 &
  telemetry_pid=$!
fi

echo "Benchmark profile=$profile for ${duration}s; evidence=$evidence"
echo "Join normally and run the documented generated-route/new-frontier flight stage."
wait "$server_pid" || status=$?
server_pid=""
[[ -z "$telemetry_pid" ]] || { kill "$telemetry_pid" 2>/dev/null || true; wait "$telemetry_pid" 2>/dev/null || true; }
telemetry_pid=""
status=${status:-0}
if [[ "$status" != 0 && "$status" != 124 ]]; then
  echo "Benchmark server exited unexpectedly: $status" >&2
  exit "$status"
fi
grep -E 'Done \(|Can.t keep up|Exception|ERROR|OutOfMemory' "$evidence/server.log" >"$evidence/events.txt" || true
echo "Benchmark complete; inspect $evidence/server.jfr and $evidence/events.txt"
