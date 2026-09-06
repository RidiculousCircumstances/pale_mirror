#!/usr/bin/env python3
"""Static boundary checks for the pinned DA3 reviewed-ring runner."""

from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools" / "da3" / "run_da3_ring.py"
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from checkpoint_receipt import OFFICIAL_CHECKPOINTS  # noqa: E402


source = RUNNER.read_text(encoding="utf-8")
for required in (
    'manifest["adapter_id"] != "da3_base"',
    "DepthAnything3(model_name=\"da3-base\")",
    "load_state_dict(load_file",
    "process_res=504",
    "use_ray_pose=True",
    "collect_pose_depth_evidence",
    "point_cloud_is_not_a_mesh",
    "no_glb_or_mesh_export",
    "canonical_promotion_forbidden",
    "model.safetensors",
):
    assert required in source, required
for forbidden in ("export_format=\"glb\"", ".blend", ".pmmesh", "src/main/resources", "PMMesh"):
    assert forbidden.lower() not in source.lower(), forbidden

assert OFFICIAL_CHECKPOINTS["da3_base"] == {"repository": "depth-anything/DA3-BASE", "filename": "model.safetensors"}
print("DA3 reviewed-ring contracts passed.")
