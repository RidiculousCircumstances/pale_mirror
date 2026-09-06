#!/usr/bin/env bash
set -euo pipefail

pid=${1:?usage: capture-runtime-jfr.sh <server-pid> [duration] [output.jfr]}
duration=${2:-120s}
output=${3:-pale-mirror-runtime.jfr}

[[ "$pid" =~ ^[0-9]+$ ]] || { printf 'Server PID must be numeric\n' >&2; exit 2; }
[[ -d "/proc/$pid" ]] || { printf 'No live process with PID %s\n' "$pid" >&2; exit 2; }
[[ "$duration" =~ ^[1-9][0-9]*[smh]$ ]] || { printf 'Duration must look like 120s, 5m or 1h\n' >&2; exit 2; }
command -v jcmd >/dev/null || { printf 'jcmd from JDK 21 is required\n' >&2; exit 2; }

output=$(realpath -m "$output")
mkdir -p "$(dirname "$output")"
jcmd "$pid" JFR.start name=pale_mirror_runtime settings=profile duration="$duration" filename="$output"
printf 'JFR recording scheduled: %s (duration %s)\n' "$output" "$duration"
