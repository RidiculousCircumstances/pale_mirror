#!/usr/bin/env bash
set -euo pipefail
fixture_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$fixture_dir/../../../.." && pwd)
exec "$project_dir/gradlew" --init-script "$fixture_dir/mods.init.gradle" "$@"
