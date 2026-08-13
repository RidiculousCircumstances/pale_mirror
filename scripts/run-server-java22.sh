#!/usr/bin/env bash
# Starts a materialised Far Frontier server with the separately pinned Java 22 runtime.
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
server_dir=$(cd "$script_dir/.." && pwd)
runtime_root=${XDG_DATA_HOME:-"$HOME/.local/share"}
java_bin=${JAVA_BIN:-"$runtime_root/far-frontier/java/temurin-22.0.2+9/bin/java"}

[[ -x "$java_bin" ]] || {
  echo "Pinned Java 22 is missing: $java_bin" >&2
  echo "Install it with scripts/install-server-java22.sh or set JAVA_BIN explicitly." >&2
  exit 1
}
"$java_bin" -version 2>&1 | grep -Eq '(version|openjdk) "22([."]|$)' || {
  echo "Far Frontier dedicated server requires Java 22: $java_bin" >&2
  exit 1
}
[[ -f "$server_dir/run.sh" ]] || {
  echo "NeoForge run.sh is missing in $server_dir" >&2
  exit 1
}

cd "$server_dir"
exec env JAVA_HOME="${java_bin%/bin/java}" PATH="${java_bin%/java}:$PATH" ./run.sh nogui
