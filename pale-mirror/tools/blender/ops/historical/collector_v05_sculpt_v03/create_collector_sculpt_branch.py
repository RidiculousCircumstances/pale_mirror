"""HISTORICAL REJECTED: create an isolated v03 direct-sculpt branch from Hunyuan v05."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_sculpt_common import (  # noqa: E402
    BACKUP_COLLECTION,
    CANDIDATE_ID,
    PHASE,
    RAW_COLLECTION,
    RAW_OBJECT,
    SCULPT_SOURCE_PATH,
    SESSION_ID,
    WORKING_OBJECT,
    branch_collections,
    candidate_root,
    component_summary,
    connected_face_components,
    copy_component_mesh,
    load_candidate_manifest,
    load_protocol,
    prepare_trace_guides,
    receipt_path,
    write_receipt,
)
from common import (  # noqa: E402
    ASSET_ID,
    TRACE_GUIDE_COLLECTION,
    TRACE_GUIDE_NAME,
    asset_id,
    collection,
    ensure_audit_stage,
    ensure_primary_reference_plane,
    fresh_scene,
    trace_profile_mesh,
)

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if SCULPT_SOURCE_PATH.is_file() or receipt_path("branch_created").is_file():
        raise ValueError(
            "Collector v05 sculpt branch already exists; its evidence is immutable. "
            "Create a new session ID rather than replacing a direct-sculpt branch."
        )
    protocol, protocol_hash = load_protocol()
    manifest, candidate_path = load_candidate_manifest(protocol)
    trace, trace_hash = prepare_trace_guides()
    fresh_scene()
    lod0, raw_collection, _backups = branch_collections()
    guide = collection(TRACE_GUIDE_COLLECTION)
    guide_profile = trace_profile_mesh(guide, TRACE_GUIDE_NAME, trace, _guide_material(), depth=-0.08)
    guide_profile.hide_render = True
    guide_profile.hide_set(True)
    guide_profile["pm_trace_role"] = "locked_primary_overlay_guide"
    ensure_primary_reference_plane(trace)

    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(candidate_path))
    imported = [object_ for object_ in bpy.data.objects if object_ not in before and object_.type == "MESH"]
    if not imported:
        raise ValueError("The locked v05 candidate imported no mesh objects.")
    raw = _join_imported_raw(imported, raw_collection)
    _normalise_primary_extent(raw, trace)
    bpy.context.view_layer.update()
    components = connected_face_components(raw.data)
    summary = component_summary(raw.data, components)
    dominant = components[0]
    if len(components) < 2 or len(dominant) < 100:
        raise ValueError("The v05 candidate does not contain a credible dominant raw mesh component.")
    editable_indices = [0]
    for index, component in enumerate(components[1:], start=1):
        if len(component) * 200 >= len(dominant) or _component_intersects_trace(raw, component, trace):
            editable_indices.append(index)
    editable_faces = [face for index in editable_indices for face in components[index]]
    working = copy_component_mesh(raw, editable_faces, lod0)
    working["pm_sculpt_working"] = True
    working["pm_sculpt_session_id"] = SESSION_ID
    working["pm_sculpt_raw_candidate_id"] = CANDIDATE_ID
    working["pm_sculpt_editable_component_indices"] = editable_indices
    working["pm_export_exclude"] = False
    raw.name = RAW_OBJECT
    raw.data.name = f"{RAW_OBJECT}_mesh"
    raw.hide_render = True
    raw.hide_set(True)
    raw["pm_sculpt_raw_immutable"] = True
    raw["pm_export_exclude"] = True
    raw["pm_sculpt_candidate_id"] = CANDIDATE_ID
    raw["pm_sculpt_candidate_sha256"] = protocol["candidate"]["sha256"]
    raw["pm_sculpt_component_summary"] = summary

    scene = bpy.context.scene
    scene["pm_asset_id"] = ASSET_ID
    scene["pm_authoring_phase"] = PHASE
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_runtime_export_forbidden"] = True
    scene["pm_sculpt_session_id"] = SESSION_ID
    scene["pm_sculpt_protocol_sha256"] = protocol_hash
    scene["pm_sculpt_candidate_id"] = CANDIDATE_ID
    scene["pm_sculpt_candidate_sha256"] = protocol["candidate"]["sha256"]
    scene["pm_primary_trace_id"] = trace["trace_id"]
    scene["pm_primary_trace_sha256"] = trace_hash
    scene["pm_sculpt_rule"] = "direct local edits of the dominant raw v05 component only; raw evidence stays hidden and immutable"
    ensure_audit_stage()
    SCULPT_SOURCE_PATH.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(SCULPT_SOURCE_PATH))
    receipt = write_receipt(
        "branch_created",
        {
            "schema": "pale_mirror_visuals.collector_sculpt_receipt.v1",
            "session_id": SESSION_ID,
            "candidate": {"id": CANDIDATE_ID, "sha256": protocol["candidate"]["sha256"]},
            "candidate_manifest_sha256": manifest["output"]["sha256"],
            "protocol_sha256": protocol_hash,
            "primary_trace": {"id": trace["trace_id"], "sha256": trace_hash},
            "raw_components": summary,
            "editable_component_indices": editable_indices,
            "excluded_components": [index for index in range(len(components)) if index not in editable_indices],
            "working_object": WORKING_OBJECT,
            "raw_object": RAW_OBJECT,
            "runtime_export_forbidden": True,
        },
    )
    return {
        "asset_id": ASSET_ID,
        "session_id": SESSION_ID,
        "source": SCULPT_SOURCE_PATH.as_posix(),
        "raw_candidate": {"id": CANDIDATE_ID, "sha256": protocol["candidate"]["sha256"]},
        "collections": {"raw": RAW_COLLECTION, "working": lod0.name, "backups": BACKUP_COLLECTION},
        "components": {"total": len(components), "editable": editable_indices, "editable_faces": len(editable_faces), "excluded": len(components) - len(editable_indices)},
        "receipt": receipt.as_posix(),
        "runtime_export_forbidden": True,
    }


def _join_imported_raw(imported: list[bpy.types.Object], target: bpy.types.Collection) -> bpy.types.Object:
    bpy.ops.object.select_all(action="DESELECT")
    for object_ in imported:
        object_.select_set(True)
        for existing in list(object_.users_collection):
            existing.objects.unlink(object_)
        target.objects.link(object_)
    bpy.context.view_layer.objects.active = imported[0]
    if len(imported) > 1:
        bpy.ops.object.join()
    return bpy.context.object


def _normalise_primary_extent(raw: bpy.types.Object, trace: dict[str, object]) -> None:
    vertices = [raw.matrix_world @ vertex.co for vertex in raw.data.vertices]
    if not vertices:
        raise ValueError("The locked v05 candidate has no mesh vertices.")
    width = max(point.x for point in vertices) - min(point.x for point in vertices)
    mapping = trace["primary_camera_mapping"]
    units = float(mapping["world_units_per_pixel"])
    source_x = [(left - mapping["origin_pixel"][0]) * units for row in trace["rows"] for left, _right in row["runs"]]
    source_x.extend((right - mapping["origin_pixel"][0]) * units for row in trace["rows"] for _left, right in row["runs"])
    target_width = max(source_x) - min(source_x)
    if width <= 0.0 or target_width <= 0.0:
        raise ValueError("The v05 candidate or source trace has a degenerate width.")
    scale = target_width / width
    raw.scale = tuple(value * scale for value in raw.scale)
    bpy.context.view_layer.update()
    scaled = [raw.matrix_world @ vertex.co for vertex in raw.data.vertices]
    raw.location.x += (min(source_x) + max(source_x)) * 0.5 - (min(point.x for point in scaled) + max(point.x for point in scaled)) * 0.5
    source_z = []
    for row in trace["rows"]:
        source_z.extend(
            (
                (mapping["origin_pixel"][1] - row["y"]) * units,
                (mapping["origin_pixel"][1] - (row["y"] + trace["sampling"]["grid_px"])) * units,
            )
        )
    raw.location.z += min(source_z) - min(point.z for point in scaled)


def _component_intersects_trace(raw: bpy.types.Object, faces: list[int], trace: dict[str, object]) -> bool:
    """Keep a small component only when its actual primary projection is anatomy.

    The source-pixel lookup is deliberately a retention test, not an automated
    fitting mechanism. It makes accidental floating debris excludable while
    preserving real thin tail/drape components that Hunyuan emitted separately.
    """
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    grid = int(trace["sampling"]["grid_px"])
    crop_top = int(trace["sampling"]["crop"][1])
    rows = {int(row["y"]): row["runs"] for row in trace["rows"]}
    vertices = {vertex for face in faces for vertex in raw.data.polygons[face].vertices}
    for index in vertices:
        point = raw.matrix_world @ raw.data.vertices[index].co
        pixel_x = round(point.x / units + origin_x)
        pixel_y = round(origin_y - point.z / units)
        row_y = crop_top + ((pixel_y - crop_top) // grid) * grid
        for left, right in rows.get(row_y, ()):
            if left <= pixel_x < right:
                return True
    return False


def _guide_material() -> bpy.types.Material:
    material = bpy.data.materials.get("PM_COLLECTOR_SCULPT_TRACE_GUIDE")
    if material is None:
        material = bpy.data.materials.new("PM_COLLECTOR_SCULPT_TRACE_GUIDE")
        material.diffuse_color = (1.0, 1.0, 1.0, 1.0)
    return material


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
