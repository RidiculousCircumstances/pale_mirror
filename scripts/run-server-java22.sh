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

# Frontier v3 has no overworld fallback. The custom level stem is world-local,
# so reject a manually changed level-name before spending JVM startup time only
# to fail inside the v3 lifecycle hook.
if [[ -f "$server_dir/user_jvm_args.txt" ]] \
  && grep -Eq '^[[:space:]]*-Dpale_mirror\.frontier_v3\.enabled=true([[:space:]]|$)' "$server_dir/user_jvm_args.txt"; then
  level_name=world
  if [[ -f "$server_dir/server.properties" ]]; then
    level_name=$(awk -F= '$1 == "level-name" { print substr($0, index($0, "=") + 1); exit }' "$server_dir/server.properties")
    level_name=${level_name:-world}
  fi
  [[ "$level_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
    echo "server.properties level-name must be a simple relative directory name: $level_name" >&2
    exit 2
  }
  graybox_definition="$server_dir/$level_name/datapacks/pale-mirror-graybox/data/pale_mirror/dimension/frontier_graybox.json"
  [[ -f "$graybox_definition" ]] || {
    echo "Frontier v3 requires $graybox_definition before this world is created." >&2
    echo "Re-run install-server.sh with --level-name $level_name, then start the server again." >&2
    exit 1
  }
fi

cd "$server_dir"
exec env JAVA_HOME="${java_bin%/bin/java}" PATH="${java_bin%/java}:$PATH" ./run.sh nogui
