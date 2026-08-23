"""Create a trace-conformal, non-canonical volume proxy from Hunyuan v03.

This is deliberately a fixed R&D operation, not a generic mesh editor.  It
can only open the hash-pinned Collector v03 review scene, keeps its output in
that non-exportable scene and has no access to the canonical Collector source.

The literal source trace acts twice: a deep cutter removes generated silhouette
excess, then a shallow *real closed volume* supplies only trace regions the
proposal did not cover.  The result is useful for judging whether Hunyuan's
hidden-side volume can support a trace-led retopology.  It is not acceptance,
not semantic anatomy and not an export candidate.
"""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from import_hunyuan2mv_candidate import SPECS, candidate_spec  # noqa: E402
from common import ASSET_ID, asset_id, load_primary_trace, organic_material, trace_silhouette_volume  # noqa: E402

import bpy


_CANDIDATE_ID = "hunyuan2mv_v03_primary_front"
_MINIMUM_Y = 0.04
_CUTTER_DEPTH = 32.0
_FILL_DEPTH = 3.4
_PROXY_NAME = "collector_hunyuan2mv_v03_trace_conformal_proxy"
_CUTTER_NAME = "PM_HUNYUAN2MV_V03_TRACE_CUTTER"
_FILL_NAME = "PM_HUNYUAN2MV_V03_TRACE_MISSING_VOLUME"


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    spec = candidate_spec(payload)
    if spec.candidate_id != _CANDIDATE_ID:
        raise ValueError("Only the pinned Hunyuan v03 proposal may be trace-conformed.")
    if not spec.review_source.is_file():
        raise FileNotFoundError("Import the fixed Hunyuan v03 proposal before building its trace proxy.")
    if bpy.data.filepath != str(spec.review_source):
        bpy.ops.wm.open_mainfile(filepath=str(spec.review_source))
    scene = bpy.context.scene
    _require_review_scene(scene, spec)
    if scene.get("pm_trace_conformal_proxy") is not None:
        raise ValueError("Trace-conformal proxy already exists; re-import the immutable v03 proposal before another trial.")

    proposal = bpy.data.collections.get(spec.proposal_collection)
    if proposal is None:
        raise ValueError("Pinned Hunyuan v03 proposal collection is missing.")
    meshes = [
        object_
        for object_ in proposal.all_objects
        if object_.type == "MESH" and object_.get("pm_candidate_id") == _CANDIDATE_ID
    ]
    if len(meshes) != 1:
        raise ValueError("Pinned Hunyuan v03 proposal must contain exactly one candidate mesh.")
    candidate = meshes[0]
    root = bpy.data.objects.get(spec.proposal_root)
    if root is None:
        raise ValueError("Pinned Hunyuan v03 proposal root is missing.")

    trace, trace_hash = load_primary_trace()
    _shift_behind_primary_trace(root, candidate)
    material = organic_material("PM_HUNYUAN2MV_V03_TRACE_PROXY", (0.26, 0.012, 0.032), 0.48)
    cutter = trace_silhouette_volume(
        proposal,
        _CUTTER_NAME,
        trace,
        material,
        front_y=_MINIMUM_Y,
        depth=_CUTTER_DEPTH,
    )
    cutter["pm_export_exclude"] = True
    _apply_boolean(candidate, "INTERSECT", cutter, "trace_silhouette_intersection")
    bpy.data.objects.remove(cutter, do_unlink=True)
    if not candidate.data.vertices:
        raise ValueError("Trace clipping removed the entire pinned Hunyuan v03 proposal.")

    # Only the screen-space holes left by the proposal receive source-shaped
    # geometry.  The fill is a closed 3D volume, not a render card; it reaches
    # into the candidate where possible and remains deliberately shallow where
    # Hunyuan supplied no support.  Later semantic retopology must replace it.
    fill = trace_silhouette_volume(
        proposal,
        _FILL_NAME,
        trace,
        material,
        front_y=_MINIMUM_Y,
        depth=_FILL_DEPTH,
    )
    fill["pm_export_exclude"] = True
    _apply_boolean(fill, "DIFFERENCE", candidate, "trace_missing_primary_volume")
    if fill.data.vertices:
        fill.name = _FILL_NAME
        fill["pm_candidate_id"] = _CANDIDATE_ID
        fill["pm_candidate_stage"] = spec.stage
        fill["pm_trace_conformal_missing_volume"] = True
    else:
        bpy.data.objects.remove(fill, do_unlink=True)

    candidate.name = _PROXY_NAME
    candidate.data.name = f"{_PROXY_NAME}_mesh"
    candidate["pm_candidate_id"] = _CANDIDATE_ID
    candidate["pm_candidate_stage"] = spec.stage
    candidate["pm_export_exclude"] = True
    candidate["pm_trace_conformal_proxy"] = True
    candidate["pm_trace_id"] = trace["trace_id"]
    candidate["pm_trace_sha256"] = trace_hash
    candidate["pm_proxy_rule"] = "generated depth clipped and completed by literal source silhouette; review-only"
    scene["pm_trace_conformal_proxy"] = {
        "candidate_id": _CANDIDATE_ID,
        "trace_id": trace["trace_id"],
        "trace_sha256": trace_hash,
        "cutter_depth": _CUTTER_DEPTH,
        "fill_depth": _FILL_DEPTH,
        "runtime_export_forbidden": True,
    }
    scene["pm_review_scope"] = (
        "Hunyuan v03 trace-conformal volume proxy only; semantic anatomy, acceptance and runtime export remain forbidden"
    )
    scene["pm_runtime_export_forbidden"] = True
    bpy.ops.wm.save_as_mainfile(filepath=str(spec.review_source))
    return {
        "asset_id": ASSET_ID,
        "candidate_id": _CANDIDATE_ID,
        "trace_id": trace["trace_id"],
        "proxy": candidate.name,
        "proxy_triangles": _triangles(candidate),
        "missing_volume_triangles": _triangles(bpy.data.objects.get(_FILL_NAME)),
        "minimum_world_y": _minimum_world_y(candidate),
        "runtime_export_forbidden": True,
    }


def _require_review_scene(scene: bpy.types.Scene, spec) -> None:
    if (
        scene.get("pm_asset_id") != ASSET_ID
        or scene.get("pm_candidate_id") != _CANDIDATE_ID
        or scene.get("pm_candidate_stage") != spec.stage
        or scene.get("pm_runtime_export_forbidden") is not True
    ):
        raise ValueError("Current scene is not the pinned non-exportable Hunyuan v03 review scene.")


def _shift_behind_primary_trace(root: bpy.types.Object, candidate: bpy.types.Object) -> None:
    minimum_y = _minimum_world_y(candidate)
    root.location.y += _MINIMUM_Y - minimum_y
    bpy.context.view_layer.update()
    if _minimum_world_y(candidate) < _MINIMUM_Y - 0.0001:
        raise ValueError("Hunyuan v03 proposal could not be placed behind the locked primary trace.")


def _apply_boolean(target: bpy.types.Object, operation: str, cutter: bpy.types.Object, name: str) -> None:
    bpy.ops.object.select_all(action="DESELECT")
    target.select_set(True)
    bpy.context.view_layer.objects.active = target
    modifier = target.modifiers.new(name, "BOOLEAN")
    modifier.operation = operation
    modifier.solver = "EXACT"
    modifier.object = cutter
    bpy.ops.object.modifier_apply(modifier=modifier.name)


def _minimum_world_y(object_: bpy.types.Object) -> float:
    if not object_.data.vertices:
        raise ValueError(f"Object has no mesh vertices: {object_.name}")
    return min((object_.matrix_world @ vertex.co).y for vertex in object_.data.vertices)


def _triangles(object_: bpy.types.Object | None) -> int:
    if object_ is None:
        return 0
    return sum(len(polygon.vertices) - 2 for polygon in object_.data.polygons)


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "candidate_id": _CANDIDATE_ID}))
