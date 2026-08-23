"""Create the literal primary-image traced base for Biomass Collector.

This operation is intentionally an authoring gate, not a finished creature
generator. Its only large mass is constructed from the checked-in source-pixel
trace. A later, separately reviewed operation may add hidden-side volume, but
may not move the primary profile without a new trace version.
"""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    ASSET_ID,
    EXPORT_PATH,
    LOD0_NAME,
    LOD1_NAME,
    REFERENCE_PATH,
    RIG_NAME,
    SECONDARY_TURNTABLE_PATH,
    SOURCE_PATH,
    TRACE_GUIDE_COLLECTION,
    TRACE_GUIDE_NAME,
    asset_id,
    collection,
    ensure_audit_stage,
    ensure_directories,
    ensure_primary_reference_plane,
    fresh_scene,
    load_primary_trace,
    organic_material,
    trace_profile_mesh,
)

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    ensure_directories()
    trace, trace_hash = load_primary_trace()
    fresh_scene()
    lod0 = collection(LOD0_NAME)
    lod1 = collection(LOD1_NAME)
    guide = collection(TRACE_GUIDE_COLLECTION)

    # The primary object is a literal two-pixel source trace. It deliberately
    # has no hidden-side volume yet: that comes only after this render agrees
    # with the locked supplied reference.
    primary_material = organic_material("PM_COLLECTOR_TRACE_BASE", (0.34, 0.018, 0.042), 0.62)
    profile = trace_profile_mesh(lod0, "collector_primary_trace_base", trace, primary_material, depth=-0.04)
    profile["pm_authoring_phase"] = "primary_trace_base"

    # LOD1 retains the same primary authority at this pre-volume stage. It is
    # hidden from audit renders so it cannot double the profile, but keeps the
    # source contract structurally complete for a later LOD simplification.
    lod1_profile = trace_profile_mesh(lod1, "collector_primary_trace_lod1", trace, primary_material, depth=0.04)
    lod1_profile.hide_render = True
    lod1_profile.hide_set(True)
    lod1_profile["pm_authoring_phase"] = "primary_trace_base"

    guide_material = _trace_material()
    guide_profile = trace_profile_mesh(guide, TRACE_GUIDE_NAME, trace, guide_material, depth=-0.08)
    guide_profile.hide_render = True
    guide_profile.hide_set(True)
    guide_profile["pm_trace_role"] = "locked_primary_overlay_guide"
    ensure_primary_reference_plane(trace)
    _build_root_rig()
    ensure_audit_stage()

    # A trace base must never accidentally reuse a stale exported mesh from
    # the rejected primitive branch.
    if EXPORT_PATH.exists():
        EXPORT_PATH.unlink()
    scene = bpy.context.scene
    scene["pm_asset_id"] = ASSET_ID
    scene["pm_reference"] = trace["source"]["file"]
    scene["pm_reference_sha256"] = trace["source"]["sha256"]
    scene["pm_primary_trace_id"] = trace["trace_id"]
    scene["pm_primary_trace_sha256"] = trace_hash
    scene["pm_authoring_phase"] = "primary_trace_base"
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_secondary_reference"] = "secondary/biomass_collector_turntable_v02_canonical_contact_sheet.png"
    scene["pm_secondary_reference_role"] = "reviewed source-local hidden-side camera constraints; literal source trace remains likeness authority"
    scene["pm_contract"] = "literal source-pixel primary profile first; volume is prohibited before trace-overlay review"
    bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE_PATH))
    return {
        "asset_id": ASSET_ID,
        "source": SOURCE_PATH.as_posix(),
        "authoring_phase": scene["pm_authoring_phase"],
        "primary_trace": {
            "id": trace["trace_id"],
            "sha256": trace_hash,
            "rows": len(trace["rows"]),
            "runs": sum(len(row["runs"]) for row in trace["rows"]),
            "guide_polygons": len(guide_profile.data.polygons),
        },
        "reference_present": REFERENCE_PATH.is_file(),
        "secondary_turntable_present": SECONDARY_TURNTABLE_PATH.is_file(),
        "export_invalidated": not EXPORT_PATH.exists(),
    }


def _trace_material() -> bpy.types.Material:
    material = bpy.data.materials.new("PM_COLLECTOR_PRIMARY_TRACE_MAT")
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    emission = nodes.new("ShaderNodeEmission")
    emission.inputs["Color"].default_value = (1.0, 1.0, 1.0, 1.0)
    emission.inputs["Strength"].default_value = 1.0
    links.new(emission.outputs["Emission"], output.inputs["Surface"])
    return material


def _build_root_rig() -> None:
    bpy.ops.object.armature_add(enter_editmode=True, location=(0, 0, 0))
    rig = bpy.context.object
    rig.name = RIG_NAME
    rig.data.name = RIG_NAME
    root = rig.data.edit_bones[0]
    root.name = "root"
    root.head = (0, 0, 0)
    root.tail = (0, 0, 6)
    bpy.ops.object.mode_set(mode="OBJECT")
    rig["pm_role"] = "root_skeleton"


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
