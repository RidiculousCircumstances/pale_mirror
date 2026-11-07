#!/usr/bin/env bash
set -euo pipefail
tool_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec "$tool_dir/../../gradlew" --init-script "$tool_dir/native-smoke.init.gradle" "$@"
