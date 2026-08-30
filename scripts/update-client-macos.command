#!/bin/bash
# Double-clickable Far Frontier client updater for macOS.
# Keep this file in the root of the NeoForge instance it should update.
set -euo pipefail

# This is the mDNS name announced by the artifact host, not a DHCP address.
# It keeps the Finder updater stable when the development host changes LAN IP.
readonly default_source_base='http://rd-EliteMini-Series.local:8092'
readonly source_base="${FAR_FRONTIER_SOURCE_URL:-$default_source_base}"
readonly instance_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bootstrap_file=''

finish() {
  local status=$?
  trap - EXIT
  if [[ -n "$bootstrap_file" ]]; then
    rm -f -- "$bootstrap_file"
  fi

  echo
  if ((status == 0)); then
    echo 'Far Frontier client is up to date.'
  else
    echo 'Far Frontier update failed. See the error above.' >&2
  fi

  # Finder opens .command files in Terminal. Keep that window readable, while
  # calls from another script or CI remain non-interactive.
  if [[ -t 0 ]]; then
    read -r -p 'Press Return to close this window...' _ || true
  fi
  exit "$status"
}
trap finish EXIT

java_is_21() {
  local candidate=$1 version
  [[ -x "$candidate" ]] || return 1
  version=$("$candidate" -version 2>&1) || return 1
  printf '%s\n' "$version" | grep -Eq '(version|openjdk) "21([."]|$)'
}

find_java_21() {
  local candidate home runtime_root

  if [[ -n "${JAVA_BIN:-}" ]]; then
    java_is_21 "$JAVA_BIN" || {
      echo "JAVA_BIN does not point to a working Java 21 executable: $JAVA_BIN" >&2
      return 1
    }
    printf '%s\n' "$JAVA_BIN"
    return 0
  fi

  if [[ -x /usr/libexec/java_home ]]; then
    home=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
    candidate="$home/bin/java"
    if java_is_21 "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi

  candidate=$(command -v java 2>/dev/null || true)
  if [[ -n "$candidate" ]] && java_is_21 "$candidate"; then
    printf '%s\n' "$candidate"
    return 0
  fi

  # The official launcher keeps its managed Java runtimes below this path.
  # The glob covers Intel and Apple Silicon directory names.
  runtime_root="$HOME/Library/Application Support/minecraft/runtime"
  for candidate in \
    "$runtime_root"/*/mac-os*/**/Contents/Home/bin/java \
    "$runtime_root"/*/mac-os*/java-runtime-*/jre.bundle/Contents/Home/bin/java \
    "$runtime_root"/*/mac-os*/java-runtime-*/bin/java; do
    if java_is_21 "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done

  echo 'Java 21 was not found.' >&2
  echo 'Install a Java 21 JDK, start Minecraft 1.21.1 once in the official launcher,' >&2
  echo 'or set JAVA_BIN to the full path of a Java 21 executable.' >&2
  return 1
}

command -v curl >/dev/null 2>&1 || {
  echo 'curl is required (it is included with macOS).' >&2
  exit 1
}

java_bin=$(find_java_21)
bootstrap_file=$(mktemp "${TMPDIR:-/tmp}/far-frontier-client.XXXXXX.sh")

echo "Far Frontier instance: $instance_dir"
echo "Java 21: $java_bin"
echo "Pack source: ${source_base%/}"
echo 'Downloading the current client updater...'

curl --fail --silent --show-error --location --retry 3 \
  --output "$bootstrap_file" "${source_base%/}/scripts/bootstrap-client.sh"

JAVA_BIN="$java_bin" bash "$bootstrap_file" \
  --source-base "${source_base%/}" \
  --target "$instance_dir" \
  "$@"
