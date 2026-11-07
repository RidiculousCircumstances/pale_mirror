#!/usr/bin/env bash
set -euo pipefail
tool_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$tool_dir/../../.." && pwd)
exec "$project_dir/tools/frontier-v3-test-pilot/fixtures/northwatch-access/gradle.sh" \
  --init-script "$project_dir/tools/vibecraft/native-smoke.init.gradle" "$@"
