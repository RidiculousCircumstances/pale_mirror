#!/usr/bin/env bash
set -euo pipefail
trial_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(cd "$trial_dir/../../.." && pwd)
exec "$project_dir/gradlew" --init-script "$project_dir/tools/vibecraft/native-smoke.init.gradle" --init-script "$trial_dir/trial.init.gradle" "$@"
