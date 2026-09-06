#!/usr/bin/env bash
# Execute only the pinned shape-only Collector Hunyuan2mv proposal on Linux.
# The result remains a review artifact and is never installed into the mod.
set -euo pipefail

root="${1:-$PWD/build/inference/hunyuan3d-2mv-cpu}"
workspace="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
python="$root/.venv/bin/python"
candidate_root="$root/candidates/biomass_collector/hunyuan2mv_v01"
inputs="$candidate_root/input"
candidate="$candidate_root/candidate.glb"
primary_root="${PALE_MIRROR_HARVESTER_REFERENCES:?Set PALE_MIRROR_HARVESTER_REFERENCES to the private Harvester reference directory.}"
primary="$primary_root/01_harvester_biomass_collector.jpg"
turntable="$workspace/pale-mirror-visuals/src/main/blender/harvester/references/biomass_collector_turntable_v01.png"
preparer="$workspace/tools/hunyuan3d_2mv/prepare_collector_multiview.py"

[[ -x "$python" ]] || { echo 'The isolated CPU Hunyuan environment is missing.' >&2; exit 1; }
[[ -f "$primary" ]] || { echo "Pinned Collector primary is unavailable: $primary" >&2; exit 1; }
[[ -f "$turntable" ]] || { echo "Pinned Collector diagnostic turntable is unavailable: $turntable" >&2; exit 1; }
[[ ! -e "$candidate" ]] || { echo "Refusing to overwrite prior candidate: $candidate" >&2; exit 1; }
if [[ ! -f "$inputs/manifest.json" ]]; then
  "$python" "$preparer" --primary "$primary" --turntable "$turntable" --output "$inputs"
fi
exec "$python" "$workspace/tools/hunyuan3d_2mv/run_collector_multiview.py" \
  --input-manifest "$inputs/manifest.json" \
  --candidate "$candidate" \
  --model-repository "$root/vendor/Hunyuan3D-2" \
  --checkpoint-root "$root/models" \
  --device cpu
