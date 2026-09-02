#!/usr/bin/env bash
# Reject a graybox JAR exclusion that would make an ordinary Packwiz client
# incompatible with the dedicated server.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
catalog="$repo_root/config/graybox-disabled-mods.txt"

[[ -f "$catalog" ]] || { printf 'Missing graybox exclusion catalog: %s\n' "$catalog" >&2; exit 1; }

while IFS= read -r jar; do
  [[ -n "$jar" && "$jar" != \#* ]] || continue
  metadata=$(rg -l -F "filename = \"$jar\"" "$repo_root/mods"/*.pw.toml || true)
  [[ $(printf '%s\n' "$metadata" | sed '/^$/d' | wc -l) -eq 1 ]] || {
    printf 'Graybox exclusion must name exactly one Packwiz mod: %s\n' "$jar" >&2
    exit 1
  }
  side=$(awk -F '"' '$1 == "side = " { print $2; exit }' "$metadata")
  [[ "$side" == server ]] || {
    printf 'Graybox exclusion must be server-only, found side=%s: %s\n' "${side:-missing}" "$jar" >&2
    exit 1
  }
done <"$catalog"

printf 'graybox runtime profile excludes server-only mods only\n'
