#!/usr/bin/env bash
# Atomically publish the current private PM artifacts to the static client host.
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
pm_jar=${1:-/home/rd/proj/pale-mirror/pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar}
visuals_jar=${2:-/home/rd/proj/pale-mirror/pale-mirror-visuals/build/libs/pale_mirror_visuals-0.3.0-SNAPSHOT.jar}
railway_jar=${3:-/home/rd/proj/railways-untold-pm/build/libs/railwaysuntold-neoforge-1.2.1-pm.1.jar}
hosted_dir="$repo_dir/hosted"

command -v sha256sum >/dev/null || { echo "sha256sum is required" >&2; exit 1; }
command -v sha512sum >/dev/null || { echo "sha512sum is required" >&2; exit 1; }

if grep -q '^file = "hosted/' "$repo_dir/index.toml"; then
  echo "Packwiz index must not contain hosted artifacts; they are installed by the checksum-pinned client stage." >&2
  exit 1
fi

expected_index_hash=$(awk '
  /^\[index\]$/ { in_index = 1; next }
  in_index && /^hash = / { gsub(/"/, "", $3); print $3; exit }
' "$repo_dir/pack.toml")
actual_index_hash=$(sha256sum "$repo_dir/index.toml" | awk '{print $1}')
if [[ -z "$expected_index_hash" || "$actual_index_hash" != "$expected_index_hash" ]]; then
  echo "Packwiz index is stale; run 'packwiz refresh' before publishing client artifacts." >&2
  exit 1
fi

for artifact in "$pm_jar" "$visuals_jar" "$railway_jar"; do
  [[ -f "$artifact" ]] || { echo "Missing release artifact: $artifact" >&2; exit 1; }
done
mkdir -p "$hosted_dir"

publish() {
  local source=$1 name=$2 temporary checksum temporary_checksum
  temporary=$(mktemp "$hosted_dir/.${name}.XXXXXX")
  temporary_checksum=$(mktemp "$hosted_dir/.${name}.sha512.XXXXXX")
  trap 'rm -f -- "$temporary" "$temporary_checksum"' RETURN
  install -m 0644 "$source" "$temporary"
  checksum=$(sha512sum "$temporary" | awk '{print tolower($1)}')
  printf '%s\n' "$checksum" >"$temporary_checksum"
  mv -f -- "$temporary" "$hosted_dir/$name"
  mv -f -- "$temporary_checksum" "$hosted_dir/$name.sha512"
  trap - RETURN
  printf 'Published %s (%s)\n' "$name" "$checksum"
}

publish "$pm_jar" pale_mirror-current.jar
publish "$visuals_jar" pale_mirror_visuals-current.jar
publish "$railway_jar" railwaysuntold-pm-current.jar
