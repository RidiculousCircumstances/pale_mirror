#!/usr/bin/env bash
# Produces bounded, sequential JFR evidence without competing with the game server.
set -euo pipefail

usage() {
  echo "Usage: $0 --target <server-directory> [--jfr <recording>] [--cpu <id>]" >&2
}

target=""
jfr_file=""
cpu=0
while (($#)); do
  case "$1" in
    --target) target=${2:?--target requires a directory}; shift 2 ;;
    --jfr) jfr_file=${2:?--jfr requires a file}; shift 2 ;;
    --cpu) cpu=${2:?--cpu requires an integer}; shift 2 ;;
    *) usage; exit 2 ;;
  esac
done
[[ -d "$target/benchmark" && "$cpu" =~ ^[0-9]+$ ]] || { usage; exit 2; }
command -v jfr >/dev/null || { echo "The Java jfr tool is required" >&2; exit 1; }
command -v flock >/dev/null || { echo "flock is required" >&2; exit 1; }
command -v taskset >/dev/null || { echo "taskset is required" >&2; exit 1; }
if [[ -z "$jfr_file" ]]; then
  jfr_file=$(find "$target/benchmark" -type f -name server.jfr -printf '%T@ %p\n' \
    | sort -nr | awk 'NR == 1 { sub(/^[^ ]+ /, ""); print }')
fi
[[ -f "$jfr_file" ]] || { echo "JFR recording not found: $jfr_file" >&2; exit 1; }

evidence=${jfr_file%/*}
report="$evidence/jfr-summary.txt"
lock="$target/benchmark/.analysis.lock"
exec 9>"$lock"
flock -n 9 || { echo "Another JFR analysis is already running" >&2; exit 1; }

{
  printf 'recording=%s\nstarted=%s\nanalysisCpu=%s\n' "$jfr_file" "$(date -u +%FT%TZ)" "$cpu"
  taskset -c "$cpu" nice -n 19 jfr summary "$jfr_file"
  printf '\n=== CPU load / GC pauses ===\n'
  taskset -c "$cpu" nice -n 19 jfr print \
    --events jdk.CPULoad,jdk.GarbageCollection,jdk.GCPhasePause "$jfr_file"
} >"$report"

echo "Sequential bounded JFR summary: $report"
