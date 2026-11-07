#!/usr/bin/env bash
set -euo pipefail
tool_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
exec uv run --project "$tool_dir/mcp-server" --frozen python -m vibecraft.server
