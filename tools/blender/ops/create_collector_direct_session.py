"""Create the active editable session from immutable generated v05 geometry."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import (  # noqa: E402
    ASSET_ID, CANDIDATE_ID, PHASE, RAW_OBJECT, SOURCE_PATH, WORKING_OBJECT,
    candidate_path, connected_face_components, copy_faces, direct_collections,
    load_protocol, mesh_digest, prepare_trace_guides, write_receipt,
    normalise_primary_extent,
)
from common import (  # noqa: E402
    TRACE_GUIDE_COLLECTION, TRACE_GUIDE_NAME, asset_id, collection,
    ensure_audit_stage, ensure_primary_reference_plane, fresh_scene,
    organic_material, trace_profile_mesh,
)

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if SOURCE_PATH.is_file():
        raise ValueError("Direct v05 session already exists; its evidence is immutable.")
    protocol, protocol_hash = load_protocol()
    trace, trace_hash = prepare_trace_guides()
    fresh_scene()
    lod0, raw_collection, _backups = direct_collections()
    guide = collection(TRACE_GUIDE_COLLECTION)
    profile = trace_profile_mesh(guide, TRACE_GUIDE_NAME, trace, organic_material("PM_COLLECTOR_DIRECT_TRACE", (1.0, 1.0, 1.0), 0.8), depth=-0.08)
    profile.hide_render = True
    profile.hide_set(True)
    profile["pm_export_exclude"] = True
    profile["pm_trace_role"] = "locked_primary_overlay_guide"
    ensure_primary_reference_plane(trace)
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(candidate_path(protocol)))
    imported = [object_ for object_ in bpy.data.objects if object_ not in before and object_.type == "MESH"]
    if not imported:
        raise ValueError("Pinned v05 candidate imported no mesh objects.")
    bpy.ops.object.select_all(action="DESELECT")
    for object_ in imported:
        object_.select_set(True)
        for current in list(object_.users_collection):
            current.objects.unlink(object_)
        raw_collection.objects.link(object_)
    bpy.context.view_layer.objects.active = imported[0]
    if len(imported) > 1:
        bpy.ops.object.join()
    raw = bpy.context.object
    raw.name = RAW_OBJECT
    raw.data.name = f"{RAW_OBJECT}_mesh"
    normalise_primary_extent(raw, trace)
    components = connected_face_components(raw.data)
    if not components or len(components[0]) < 100:
        raise ValueError("Pinned v05 candidate has no credible dominant component.")
    # The direct branch retains all raw geometry.  The initial working copy is
    # still one mesh, so later topology changes cannot hide behind disconnected
    # replacement objects.
    working = copy_faces(raw, range(len(raw.data.polygons)), lod0)
    raw.hide_render = True
    raw.hide_set(True)
    raw["pm_direct_raw_immutable"] = True
    raw["pm_export_exclude"] = True
    raw["pm_direct_candidate_id"] = CANDIDATE_ID
    raw["pm_direct_candidate_sha256"] = protocol["candidate"]["sha256"]
    raw["pm_direct_mesh_sha256"] = mesh_digest(raw)
    working["pm_direct_working"] = True
    working["pm_direct_source_candidate_id"] = CANDIDATE_ID
    working["pm_direct_source_components"] = list(range(len(components)))
    working["pm_export_exclude"] = False
    scene = bpy.context.scene
    scene["pm_asset_id"] = ASSET_ID
    scene["pm_authoring_phase"] = PHASE
    scene["pm_direct_session_id"] = protocol["session_id"]
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_runtime_export_forbidden"] = True
    scene["pm_direct_protocol_sha256"] = protocol_hash
    scene["pm_direct_candidate_sha256"] = protocol["candidate"]["sha256"]
    scene["pm_primary_trace_id"] = trace["trace_id"]
    scene["pm_primary_trace_sha256"] = trace_hash
    scene["pm_direct_rule"] = "Edit only the existing raw-v05 working surface; trace constrains audit and never generates anatomy."
    ensure_audit_stage()
    SOURCE_PATH.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE_PATH))
    receipt = write_receipt("session_created", {
        "schema": "pale_mirror_visuals.collector_direct_receipt.v1",
        "session_id": protocol["session_id"],
        "kind": "session_created",
        "protocol_sha256": protocol_hash,
        "candidate": protocol["candidate"],
        "primary_trace": {"id": trace["trace_id"], "sha256": trace_hash},
        "raw_mesh_sha256": raw["pm_direct_mesh_sha256"],
        "working_mesh_sha256": mesh_digest(working),
        "components": len(components),
        "runtime_export_forbidden": True,
    })
    return {"asset_id": ASSET_ID, "session_id": protocol["session_id"], "working_object": WORKING_OBJECT, "raw_object": RAW_OBJECT, "receipt": receipt.as_posix(), "runtime_export_forbidden": True}


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
