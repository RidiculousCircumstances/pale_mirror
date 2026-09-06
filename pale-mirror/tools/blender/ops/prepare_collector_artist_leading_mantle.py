"""Prepare the v03 leading-mantle artist scene without deforming geometry."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_artist_region import artist_region, contains_primary_point, primary_polygon_world  # noqa: E402
from collector_direct_common import ASSET_ID, mesh_digest, prepare_trace_guides, require_session_scene, write_receipt  # noqa: E402

import bpy


SESSION_ID = "collector_v05_direct_mesh_v03"
PASS_ID = "leading_mantle_artist_v03"
VOLUME_GROUP = "PM_ARTIST_LEADING_MANTLE_VOLUME_V03"
FRONT_GROUP = "PM_ARTIST_LEADING_MANTLE_FRONT_SURFACE_V03"
ATTACHMENT_GROUP = "PM_ARTIST_LEADING_MANTLE_ATTACHMENT_V03"


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, _protocol_hash, working = require_session_scene({"session_id": SESSION_ID})
    if bpy.context.scene.get("pm_direct_open_pass") != PASS_ID:
        raise ValueError("Open the protected v03 leading_mantle_artist_v03 pass before preparing its artist scene.")
    trace, _trace_hash = prepare_trace_guides()
    region = artist_region(protocol)
    before = mesh_digest(working)
    polygon = primary_polygon_world(protocol, trace)
    positions = [working.matrix_world @ vertex.co for vertex in working.data.vertices]
    selected = [index for index, point in enumerate(positions) if contains_primary_point(polygon, point)]
    if len(selected) < 32:
        raise ValueError("Leading-mantle artist preparation selected too little existing geometry.")
    groups = {name: _replace_group(working, name) for name in (VOLUME_GROUP, FRONT_GROUP, ATTACHMENT_GROUP)}
    groups[VOLUME_GROUP].add(selected, 1.0, "REPLACE")
    front_limit = float(region["front_skin_depth_world"])
    skin = _front_skin(positions, selected)
    front = [
        index for index in selected
        if positions[index].y <= skin[(round(positions[index].x / 0.2), round(positions[index].z / 0.2))] + front_limit
    ]
    groups[FRONT_GROUP].add(front, 1.0, "REPLACE")
    attachment = _attachment_vertices(positions, trace, selected)
    groups[ATTACHMENT_GROUP].add(attachment, 1.0, "REPLACE")
    working.vertex_groups.active_index = groups[VOLUME_GROUP].index
    working["pm_artist_region_v03"] = region["id"]
    working["pm_artist_intent_v03"] = region["intent"]
    working["pm_artist_geometry_digest_before_v03"] = before
    after = mesh_digest(working)
    if before != after:
        raise ValueError("Artist scene preparation must not change the protected working mesh geometry.")
    bpy.context.scene["pm_artist_prepared_pass"] = PASS_ID
    bpy.context.scene["pm_artist_prepared_instruction"] = "Use only the named leading-mantle groups for visible Blender sculpt/edit work; capture an audit before completing."
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    receipt = write_receipt(f"{PASS_ID}_artist_scene", {
        "schema": "pale_mirror_visuals.collector_direct_receipt.v1",
        "session_id": SESSION_ID,
        "kind": "artist_scene_preparation",
        "pass_id": PASS_ID,
        "working_mesh_sha256": before,
        "groups": {
            VOLUME_GROUP: len(selected),
            FRONT_GROUP: len(front),
            ATTACHMENT_GROUP: len(attachment),
        },
        "intent": region["intent"],
        "geometry_changed": False,
        "runtime_export_forbidden": True,
    }, SESSION_ID)
    return {
        "asset_id": ASSET_ID,
        "session_id": SESSION_ID,
        "pass_id": PASS_ID,
        "groups": {name: len(group) for name, group in {VOLUME_GROUP: selected, FRONT_GROUP: front, ATTACHMENT_GROUP: attachment}.items()},
        "working_mesh_sha256": before,
        "receipt": receipt.as_posix(),
        "required_next": "A visible Blender artist sculpts/local-retopologises collector_v05_direct_working, then complete_collector_direct_mesh_pass captures the evidence.",
    }


def _replace_group(working: bpy.types.Object, name: str) -> bpy.types.VertexGroup:
    existing = working.vertex_groups.get(name)
    if existing is not None:
        working.vertex_groups.remove(existing)
    return working.vertex_groups.new(name=name)


def _front_skin(positions, selected: list[int]) -> dict[tuple[int, int], float]:
    result: dict[tuple[int, int], float] = {}
    for index in selected:
        point = positions[index]
        key = (round(point.x / 0.2), round(point.z / 0.2))
        result[key] = min(result.get(key, point.y), point.y)
    return result


def _attachment_vertices(positions, trace, selected: list[int]) -> list[int]:
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    center_x = (302 - origin_x) * units
    center_z = (origin_y - 190) * units
    return [
        index for index in selected
        if ((positions[index].x - center_x) / 2.8) ** 2 + ((positions[index].z - center_z) / 2.3) ** 2 <= 1.0
    ]


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
