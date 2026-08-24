"""Remove only trace-invisible disconnected generator debris from active v05 work.

This is a conservative topology operation on the existing working mesh.  It
never creates a mesh or replacement tail. Every deleted connected component is
small and has zero projected vertices in the literal source-trace occupancy
grid, so the primary image remains the safety boundary for cleanup.
"""

from __future__ import annotations

import bmesh
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, PROTOCOL_PATH, WORKING_OBJECT, connected_face_components, mesh_digest, require_session_scene  # noqa: E402
from common import PINNED_REFERENCE_SHA256, load_primary_trace  # noqa: E402

import bpy
from mathutils import Vector


PASS_ID = "tail_cleanup_v01"
PLAN_PATH = PROTOCOL_PATH.with_name("collector_v05_tail_cleanup_v01.json")


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, _protocol_hash, working = require_session_scene(payload)
    if working.name != WORKING_OBJECT or bpy.context.scene.get("pm_direct_open_pass") != PASS_ID:
        raise ValueError("Open tail_cleanup_v01 on the protected working mesh before applying it.")
    if working.get("pm_direct_tail_cleanup_applied"):
        raise ValueError("Tail cleanup is one-shot; inspect its audit instead of stacking deletions.")
    plan = _load_plan(protocol)
    trace, _trace_hash = load_primary_trace()
    components = connected_face_components(working.data)
    trace_cells = _trace_cells(trace)
    removable = [
        component
        for component in components
        if len(component) <= int(plan["maximum_detached_faces"])
        and not _component_projects_into_trace(working, component, trace, trace_cells)
    ]
    if not removable:
        raise ValueError("Tail cleanup found no trace-invisible disconnected debris; do not alter visible anatomy.")
    removed_faces = sum(len(component) for component in removable)
    if removed_faces >= len(working.data.polygons):
        raise ValueError("Tail cleanup selection would remove the entire working mesh.")
    before = mesh_digest(working)
    _delete_faces(working.data, removable)
    after = mesh_digest(working)
    if before == after:
        raise ValueError("Tail cleanup did not alter the existing working mesh.")
    working["pm_direct_tail_cleanup_applied"] = True
    working["pm_direct_tail_cleanup_plan"] = PLAN_PATH.name
    working["pm_direct_tail_cleanup_removed_components"] = len(removable)
    working["pm_direct_tail_cleanup_removed_faces"] = removed_faces
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {
        "asset_id": ASSET_ID,
        "pass_id": PASS_ID,
        "removed_components": len(removable),
        "removed_faces": removed_faces,
        "remaining_components": len(connected_face_components(working.data)),
        "before_mesh_sha256": before,
        "after_mesh_sha256": after,
        "rule": "face deletion on existing trace-invisible disconnected geometry only; no new meshes or topology construction",
        "required_next": "complete_collector_direct_mesh_pass then visual review",
    }


def _load_plan(protocol: dict[str, object]) -> dict[str, object]:
    plan = json.loads(PLAN_PATH.read_text(encoding="utf-8"))
    if plan.get("schema") != "pale_mirror_visuals.collector_direct_tail_cleanup.v1":
        raise ValueError("Tail cleanup plan has an unsupported schema.")
    if plan.get("asset_id") != ASSET_ID or plan.get("session_id") != protocol.get("session_id") or plan.get("pass_id") != PASS_ID:
        raise ValueError("Tail cleanup plan does not belong to the active direct session.")
    reference = plan.get("reference")
    maximum = plan.get("maximum_detached_faces")
    if not isinstance(reference, dict) or reference.get("sha256") != PINNED_REFERENCE_SHA256 or not isinstance(maximum, int) or maximum < 1:
        raise ValueError("Tail cleanup plan has invalid reference or component limit.")
    return plan


def _trace_cells(trace: dict[str, object]) -> set[tuple[int, int]]:
    sampling = trace["sampling"]
    left, top, _right, _bottom = sampling["crop"]
    grid = int(sampling["grid_px"])
    cells: set[tuple[int, int]] = set()
    for row in trace["rows"]:
        row_index = (int(row["y"]) - int(top)) // grid
        for start, end in row["runs"]:
            for column in range((int(start) - int(left)) // grid, (int(end) - int(left)) // grid):
                cells.add((column, row_index))
    return cells


def _component_projects_into_trace(
    working: bpy.types.Object,
    component: list[int],
    trace: dict[str, object],
    trace_cells: set[tuple[int, int]],
) -> bool:
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    left, top, _right, _bottom = trace["sampling"]["crop"]
    grid = int(trace["sampling"]["grid_px"])
    indices = {vertex for face in component for vertex in working.data.polygons[face].vertices}
    for index in indices:
        point = working.matrix_world @ working.data.vertices[index].co
        pixel_x = round(point.x / units + origin_x)
        pixel_y = round(origin_y - point.z / units)
        cell = ((pixel_x - int(left)) // grid, (pixel_y - int(top)) // grid)
        if cell in trace_cells:
            return True
    return False


def _delete_faces(mesh: bpy.types.Mesh, components: list[list[int]]) -> None:
    indices = sorted({face for component in components for face in component})
    before_faces = len(mesh.polygons)
    bm = bmesh.new()
    try:
        bm.from_mesh(mesh)
        bm.faces.ensure_lookup_table()
        targets = [bm.faces[index] for index in indices]
        bmesh.ops.delete(bm, geom=targets, context="FACES")
        bm.to_mesh(mesh)
    finally:
        bm.free()
    mesh.update()
    if len(mesh.polygons) != before_faces - len(indices):
        raise ValueError("Tail cleanup face deletion did not preserve the declared topology delta.")


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
