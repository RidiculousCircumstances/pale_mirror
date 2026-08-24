"""Validate the explicit static PMMesh source contract before export."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    ASSET_ID,
    LOD0_NAME,
    REFERENCE_PLANE_NAME,
    RIG_NAME,
    SOURCE_PATH,
    TRACE_GUIDE_NAME,
    TRACE_ID,
    asset_id,
    collector_objects,
    load_primary_trace,
    triangle_count,
)

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError("Collector canonical source does not exist; an unaccepted direct-v05 session cannot export.")
    if bpy.data.filepath != str(SOURCE_PATH):
        bpy.ops.wm.open_mainfile(filepath=str(SOURCE_PATH))
    trace, trace_hash = load_primary_trace()
    lod0 = collector_objects(0)
    lod1 = collector_objects(1)
    problems: list[str] = []
    if not lod0:
        problems.append("LOD0 collection has no meshes")
    if not lod1:
        problems.append("LOD1 collection has no meshes")
    # Locked audit guides and boolean-only source clip volumes must never leak
    # into the runtime budget or the later PMMesh export. They remain in the
    # `.blend` only as reproducible construction evidence.
    exportable_lod0 = [object_ for object_ in lod0 if not object_.get("pm_export_exclude")]
    exportable_lod1 = [object_ for object_ in lod1 if not object_.get("pm_export_exclude")]
    lod0_tris = triangle_count(exportable_lod0)
    lod1_tris = triangle_count(exportable_lod1)
    if lod0_tris > 30_000:
        problems.append(f"LOD0 triangle budget exceeded: {lod0_tris} > 30000")
    if lod1_tris > 7_500:
        problems.append(f"LOD1 triangle budget exceeded: {lod1_tris} > 7500")
    rig = bpy.data.objects.get(RIG_NAME)
    if rig is None or rig.type != "ARMATURE" or "root" not in rig.data.bones:
        problems.append("root armature is missing")
    if bpy.context.scene.get("pm_primary_trace_id") != TRACE_ID:
        problems.append("scene is not pinned to the required primary trace")
    if bpy.context.scene.get("pm_primary_trace_sha256") != trace_hash:
        problems.append("scene primary trace differs from checked-in trace input")
    if bpy.data.objects.get(TRACE_GUIDE_NAME) is None:
        problems.append("locked primary trace guide is missing")
    if bpy.data.objects.get(REFERENCE_PLANE_NAME) is None:
        problems.append("locked primary reference plane is missing")
    phase = bpy.context.scene.get("pm_authoring_phase")
    if phase != "accepted":
        problems.append("canonical export requires an explicitly accepted visual source; use validate_collector_direct_session while v05 remains under review")
    if problems:
        raise ValueError("; ".join(problems))
    return {
        "asset_id": ASSET_ID,
        "valid": True,
        "lod0": {"objects": len(exportable_lod0), "triangles": lod0_tris},
        "lod1": {"objects": len(exportable_lod1), "triangles": lod1_tris},
        "rig": RIG_NAME,
        "primary_trace": {"id": trace["trace_id"], "sha256": trace_hash},
        "authoring_phase": phase,
        "export_ready": bpy.context.scene.get("pm_authoring_phase") == "accepted",
        "trace_stage": {
            "reference_hide_render": bpy.data.objects[REFERENCE_PLANE_NAME].hide_render,
            "guide_hide_render": bpy.data.objects[TRACE_GUIDE_NAME].hide_render,
            "guide_polygons": len(bpy.data.objects[TRACE_GUIDE_NAME].data.polygons),
            "reference_bounds": [list(vertex.co) for vertex in bpy.data.objects[REFERENCE_PLANE_NAME].data.vertices],
            "guide_bounds": [
                [min(vertex.co[index] for vertex in bpy.data.objects[TRACE_GUIDE_NAME].data.vertices) for index in range(3)],
                [max(vertex.co[index] for vertex in bpy.data.objects[TRACE_GUIDE_NAME].data.vertices) for index in range(3)],
            ],
        },
    }
