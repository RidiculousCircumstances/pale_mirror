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
test -f .packwizignore
for excluded in AGENTS.md .github/ '.assembly-*' .work/ build/ crash-reports/ hosted/ logs/ 'neoforge-*-installer.jar.log' pale-mirror/ perf.data run/ '/world*/'; do
  grep -Fx -- "$excluded" .packwizignore >/dev/null || {
    echo "Packwiz exclusion is missing repository-only path: $excluded" >&2
    exit 1
  }
done

scripts/validate-graybox-runtime-profile.sh

if find . -path './.git' -prune -o -path './.github' -prune -o \
  -path './.assembly-*' -prune -o -path './.work' -prune -o \
  -path './build' -prune -o -path './crash-reports' -prune -o \
  -path './hosted' -prune -o -path './logs' -prune -o \
  -path './pale-mirror' -prune -o -path './run' -prune -o \
  -path './world*' -prune -o \
  -name '*.jar' -print | grep -q .; then
  echo "JAR files must not be committed; use Packwiz metadata instead" >&2
  exit 1
fi

packwiz list >/dev/null
.github/scripts/validate-pack-payload-index.py
git diff --check

if [[ -n "${FAR_FRONTIER_SERVER:-}" ]]; then
  python3 scripts/validate-structure-assets.py \
    --mods-dir "$FAR_FRONTIER_SERVER/mods" \
    --datapacks datapacks \
    --structurify config/structurify.json
fi

echo "validation passed: payload policy and manifest/index identity are synchronized"
