#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

command -v packwiz >/dev/null || {
  echo "packwiz is required; install it before validation" >&2
  exit 1
}

test -f pack.toml
test -f index.toml
test -f docs/mod-compatibility.md
test -f docs/testing.md

scripts/validate-graybox-runtime-profile.sh

if find . -path './.git' -prune -o -path './hosted' -prune -o -name '*.jar' -print | grep -q .; then
  echo "JAR files must not be committed; use Packwiz metadata instead" >&2
  exit 1
fi

packwiz list >/dev/null
packwiz refresh >/dev/null
git diff --check

if [[ -n "${FAR_FRONTIER_SERVER:-}" ]]; then
  python3 scripts/validate-structure-assets.py \
    --mods-dir "$FAR_FRONTIER_SERVER/mods" \
    --datapacks datapacks \
    --structurify config/structurify.json
fi

if git diff --quiet -- index.toml; then
  echo "validation passed: manifest and index are synchronized"
else
  echo "index.toml changed during refresh; inspect and commit the generated lockfile" >&2
  exit 1
fi
