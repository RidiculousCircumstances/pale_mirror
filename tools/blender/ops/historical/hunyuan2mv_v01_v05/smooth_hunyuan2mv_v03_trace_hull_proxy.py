"""Smooth the fixed v03 trace-depth hull without opening canonical assets.

The preceding hull preserves every two-pixel trace cell and is deliberately
too faceted to judge as an organic mass.  This review-only operation performs
one bounded voxel remesh on that same closed hull.  It does not add a source
card, cannot run on arbitrary meshes and must be followed by a fresh literal
primary overlay; a contour regression rejects the proxy.
"""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(1, str(Path(__file__).resolve().parent.parents[1]))
from import_hunyuan2mv_candidate import candidate_spec  # noqa: E402
from common import ASSET_ID, asset_id  # noqa: E402

import bpy


_CANDIDATE_ID = "hunyuan2mv_v03_primary_front"
_HULL_NAME = "collector_hunyuan2mv_v03_trace_depth_hull"
_VOXEL_SIZE = 0.12


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    spec = candidate_spec(payload)
    if spec.candidate_id != _CANDIDATE_ID:
        raise ValueError("Only the fixed Hunyuan v03 trace-depth hull may be smoothed.")
    if not spec.review_source.is_file():
        raise FileNotFoundError("Pinned Hunyuan v03 review scene is unavailable.")
    if bpy.data.filepath != str(spec.review_source):
        bpy.ops.wm.open_mainfile(filepath=str(spec.review_source))
    scene = bpy.context.scene
    marker = scene.get("pm_trace_depth_hull_proxy")
    # Blender round-trips nested scene custom properties as IDPropertyGroup,
    # not a plain ``dict``.  Treat it as the mapping it is; rejecting a valid
    # persisted hull here would make the fixed review operation non-resumable.
    if marker is None or marker.get("candidate_id") != _CANDIDATE_ID:
        raise ValueError("Build the fixed trace-depth hull from a fresh Hunyuan v03 import before smoothing.")
    if scene.get("pm_runtime_export_forbidden") is not True:
        raise ValueError("A trace-depth research hull must remain runtime-export forbidden.")
    hull = bpy.data.objects.get(_HULL_NAME)
    if hull is None or hull.type != "MESH" or not hull.get("pm_trace_depth_hull_proxy"):
        raise ValueError("Pinned Hunyuan v03 trace-depth hull is missing.")
    if hull.get("pm_trace_hull_voxel_smoothed"):
        raise ValueError("Trace-depth hull is already smoothed; re-import before another trial.")

    bpy.ops.object.select_all(action="DESELECT")
    hull.select_set(True)
    bpy.context.view_layer.objects.active = hull
    modifier = hull.modifiers.new("trace_depth_hull_organic_remesh", "REMESH")
    modifier.mode = "VOXEL"
    modifier.voxel_size = _VOXEL_SIZE
    modifier.use_smooth_shade = True
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    for polygon in hull.data.polygons:
        polygon.use_smooth = True
    hull["pm_trace_hull_voxel_smoothed"] = True
    hull["pm_trace_hull_voxel_size"] = _VOXEL_SIZE
    scene["pm_trace_depth_hull_proxy"]["voxel_size"] = _VOXEL_SIZE
    scene["pm_trace_depth_hull_proxy"]["smoothed"] = True
    bpy.ops.wm.save_as_mainfile(filepath=str(spec.review_source))
    return {
        "asset_id": ASSET_ID,
        "candidate_id": _CANDIDATE_ID,
        "proxy": hull.name,
        "triangles": sum(len(polygon.vertices) - 2 for polygon in hull.data.polygons),
        "voxel_size": _VOXEL_SIZE,
        "runtime_export_forbidden": True,
    }


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "candidate_id": _CANDIDATE_ID}))
