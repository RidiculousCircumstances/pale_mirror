#!/usr/bin/env bash
# Installs the exact private-server Java runtime without replacing the system Java.
set -euo pipefail

version=22.0.2+9
archive=OpenJDK22U-jre_x64_linux_hotspot_22.0.2_9.tar.gz
url="https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2%2B9/$archive"
sha256=41e401f287e1850631b259b483929462217ac6b1cc3c7359d80b1cc01ee5a666
target=${1:-"${XDG_DATA_HOME:-$HOME/.local/share}/far-frontier/java/temurin-22.0.2+9"}
cache_root=${XDG_CACHE_HOME:-$HOME/.cache}/far-frontier
cached="$cache_root/$archive"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v tar >/dev/null || { echo "tar is required" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum is required" >&2; exit 1; }
mkdir -p "$cache_root"
if [[ ! -f "$cached" ]] || ! printf '%s  %s\n' "$sha256" "$cached" | sha256sum -c - >/dev/null 2>&1; then
  curl --fail --location --retry 3 --output "$cached" "$url"
fi
printf '%s  %s\n' "$sha256" "$cached" | sha256sum -c -

if [[ ! -x "$target/bin/java" ]]; then
  parent=$(dirname "$target")
  mkdir -p "$parent"
  staging=$(mktemp -d "$parent/.temurin-22.XXXXXX")
  cleanup() { [[ -d "$staging" ]] && rm -rf -- "$staging"; }
  trap cleanup EXIT
  tar -xzf "$cached" --strip-components=1 -C "$staging"
  "$staging/bin/java" -version 2>&1 | grep -Eq '(version|openjdk) "22([."]|$)' || {
    echo "Downloaded runtime is not Java 22" >&2; exit 1;
  }
  mv "$staging" "$target"
  trap - EXIT
fi

printf 'Java %s installed at %s\n' "$version" "$target"
printf 'Use: JAVA_BIN=%q\n' "$target/bin/java"
