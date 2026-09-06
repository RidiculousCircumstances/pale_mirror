"""Ground the existing Collector front mantle against the literal primary image.

This fixed v02 operation touches only the already-present image-side mantle
surface and leading attachment.  It does not create a body, profile, card,
mesh, primitive, guide or disconnected anatomy.
"""

from __future__ import annotations

import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, WORKING_OBJECT, mesh_digest, protocol_path, require_session_scene  # noqa: E402
from common import PINNED_REFERENCE_SHA256, load_primary_trace  # noqa: E402

import bpy
from mathutils import Vector


SESSION_ID = "collector_v05_direct_mesh_v02"
PASS_ID = "front_mantle_grounding_v02"
PLAN_PATH = protocol_path(SESSION_ID).with_name("collector_v05_front_mantle_grounding_v02.json")


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, _protocol_hash, working = require_session_scene({"session_id": SESSION_ID})
    if working.name != WORKING_OBJECT or bpy.context.scene.get("pm_direct_open_pass") != PASS_ID:
        raise ValueError("Open the protected v02 front_mantle_grounding_v02 pass before applying it.")
    if working.get("pm_direct_front_mantle_grounding_v02_applied"):
        raise ValueError("Front-mantle grounding is one-shot; inspect its audit instead of stacking an edit.")
    plan = _load_plan(protocol)
    trace, _trace_hash = load_primary_trace()
    before = mesh_digest(working)
    points = [_world(working, vertex.co) for vertex in working.data.vertices]
    front_skin = _front_skin_depth(points)
    changed = _reshape_existing_mantle(working, points, front_skin, plan, trace)
    changed.update(_anchor_existing_attachment(working, points, front_skin, plan, trace))
    if len(changed) < 500:
        raise ValueError("Front-mantle grounding touched too little of the existing surface to be credible.")
    working.data.update()
    after = mesh_digest(working)
    if before == after:
        raise ValueError("Front-mantle grounding did not alter the existing working surface.")
    working["pm_direct_front_mantle_grounding_v02_applied"] = True
    working["pm_direct_front_mantle_grounding_v02_plan"] = PLAN_PATH.name
    working["pm_direct_front_mantle_grounding_v02_changed_vertices"] = len(changed)
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {
        "asset_id": ASSET_ID,
        "session_id": SESSION_ID,
        "pass_id": PASS_ID,
        "changed_vertices": len(changed),
        "before_mesh_sha256": before,
        "after_mesh_sha256": after,
        "rule": "direct deformation of existing image-side mantle and attachment only; no new meshes or topology construction",
        "required_next": "complete_collector_direct_mesh_pass then independent visual review",
    }


def _load_plan(protocol: dict[str, object]) -> dict[str, object]:
    plan = json.loads(PLAN_PATH.read_text(encoding="utf-8"))
    if plan.get("schema") != "pale_mirror_visuals.collector_direct_front_mantle_grounding.v1":
        raise ValueError("Front-mantle plan has an unsupported schema.")
    if plan.get("asset_id") != ASSET_ID or plan.get("session_id") != protocol.get("session_id") or plan.get("pass_id") != PASS_ID:
        raise ValueError("Front-mantle plan does not belong to the active direct session.")
    reference = plan.get("reference")
    mantle = plan.get("mantle")
    if not isinstance(reference, dict) or reference.get("sha256") != PINNED_REFERENCE_SHA256 or not isinstance(mantle, dict):
        raise ValueError("Front-mantle plan does not pin the literal source reference.")
    if len(mantle.get("outer_edge_px", [])) != 7 or len(mantle.get("merged_inner_edge_px", [])) != 6:
        raise ValueError("Front-mantle plan must declare the source-image outer and inner edges.")
    return plan


def _world(working: bpy.types.Object, coordinate: Vector) -> Vector:
    return working.matrix_world @ coordinate


def _local(working: bpy.types.Object, coordinate: Vector) -> Vector:
    return working.matrix_world.inverted() @ coordinate


def _pixel(trace: dict[str, object], point: list[float] | tuple[float, float]) -> Vector:
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    return Vector(((float(point[0]) - origin_x) * units, 0.0, (origin_y - float(point[1])) * units))


def _smoothstep(value: float) -> float:
    clamped = max(0.0, min(1.0, value))
    return clamped * clamped * (3.0 - 2.0 * clamped)


def _front_skin_depth(points: list[Vector]) -> dict[tuple[int, int], float]:
    result: dict[tuple[int, int], float] = {}
    for point in points:
        key = (round(point.x / 0.20), round(point.z / 0.20))
        result[key] = min(result.get(key, point.y), point.y)
    return result


def _front_weight(point: Vector, front_skin: dict[tuple[int, int], float]) -> float:
    nearest = front_skin.get((round(point.x / 0.20), round(point.z / 0.20)))
    if nearest is None:
        return 0.0
    return _smoothstep(1.0 - max(0.0, point.y - nearest) / 1.35)


def _curve_x(trace: dict[str, object], curve: list[list[float]], z: float) -> float:
    controls = sorted((_pixel(trace, entry) for entry in curve), key=lambda point: point.z)
    if z <= controls[0].z:
        return controls[0].x
    if z >= controls[-1].z:
        return controls[-1].x
    for left, right in zip(controls, controls[1:]):
        if left.z <= z <= right.z:
            fraction = (z - left.z) / max(0.0001, right.z - left.z)
            return left.x + (right.x - left.x) * fraction
    raise AssertionError("Front-mantle curve did not cover its selected height.")


def _reshape_existing_mantle(
    working: bpy.types.Object,
    points: list[Vector],
    front_skin: dict[tuple[int, int], float],
    plan: dict[str, object],
    trace: dict[str, object],
) -> set[int]:
    mantle = plan["mantle"]
    outer_curve = mantle["outer_edge_px"]
    inner_curve = mantle["merged_inner_edge_px"]
    outer_controls = [_pixel(trace, entry) for entry in outer_curve]
    inner_controls = [_pixel(trace, entry) for entry in inner_curve]
    low_z = min(point.z for point in outer_controls)
    high_z = max(point.z for point in outer_controls)
    units = float(trace["primary_camera_mapping"]["world_units_per_pixel"])
    selection_margin = float(mantle["selection_margin_px"]) * units
    maximum_pull = float(mantle["maximum_lateral_pull"])
    ground_z = float(mantle["ground_z"])
    ground_blend = float(mantle["ground_blend_world"])
    changed: set[int] = set()
    for index, point in enumerate(points):
        if not low_z <= point.z <= high_z:
            continue
        outer_x = _curve_x(trace, outer_curve, point.z)
        inner_x = _curve_x(trace, inner_curve, point.z)
        if point.x < outer_x - selection_margin or point.x > inner_x + selection_margin:
            continue
        front = _front_weight(point, front_skin)
        if front < 0.05:
            continue
        # The current v01 front has an open C-shaped loop.  Existing vertices
        # on its inner branch are drawn toward the declared inner mantle edge,
        # while the outer branch stays tied to the literal source boundary.
        # This locally closes the visual gap without manufacturing a flat card.
        desired_x = max(outer_x + 0.12, min(inner_x, point.x))
        if point.x > inner_x:
            desired_x = inner_x
        elif point.x < outer_x:
            desired_x = outer_x
        ground_weight = _smoothstep((ground_blend - point.z) / ground_blend)
        span = max(0.20, inner_x - outer_x)
        # Lower inner-branch vertices are the source of the visible open loop.
        # Compress only that existing lower branch toward the middle of the
        # declared mantle band, leaving the outer silhouette edge in place.
        if point.x > outer_x + span * 0.22 and ground_weight > 0.0:
            merged_x = outer_x + span * 0.55
            desired_x = desired_x + (merged_x - desired_x) * ground_weight * 0.78
        lateral = max(-maximum_pull, min(maximum_pull, desired_x - point.x))
        edge_distance = min(abs(point.x - outer_x), abs(point.x - inner_x))
        edge_weight = 0.42 + 0.58 * _smoothstep(1.0 - edge_distance / max(0.20, inner_x - outer_x))
        updated = point.copy()
        updated.x += lateral * front * edge_weight
        updated.y -= float(mantle["front_depth_pull"]) * front * edge_weight
        if ground_weight > 0.0:
            updated.z = max(ground_z, updated.z - (updated.z - ground_z) * ground_weight * front)
        working.data.vertices[index].co = _local(working, updated)
        changed.add(index)
    return changed


def _anchor_existing_attachment(
    working: bpy.types.Object,
    points: list[Vector],
    front_skin: dict[tuple[int, int], float],
    plan: dict[str, object],
    trace: dict[str, object],
) -> set[int]:
    target = plan["attachment"]
    center = _pixel(trace, target["center_px"])
    units = float(trace["primary_camera_mapping"]["world_units_per_pixel"])
    radius_x = float(target["radius_px"][0]) * units
    radius_z = float(target["radius_px"][1]) * units
    changed: set[int] = set()
    for index, point in enumerate(points):
        distance = ((point.x - center.x) / radius_x) ** 2 + ((point.z - center.z) / radius_z) ** 2
        weight = _smoothstep(1.0 - distance) * _front_weight(point, front_skin)
        if weight < 0.05:
            continue
        updated = point.copy()
        updated.y -= float(target["forward_push"]) * weight
        updated.z -= float(target["downward_pull"]) * weight
        working.data.vertices[index].co = _local(working, updated)
        changed.add(index)
    return changed


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
