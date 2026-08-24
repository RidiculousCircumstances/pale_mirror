"""Author the first direct-v05 Collector composition pass from pinned image landmarks.

This is deliberately a fixed, reviewable sculpt operation rather than a
general mesh generator.  It works only while the protected
``primary_composition_v01`` pass is open and changes coordinates on the one
existing raw-v05 working surface.  It neither creates geometry nor derives a
replacement silhouette from trace rails.
"""

from __future__ import annotations

import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, PROTOCOL_PATH, WORKING_OBJECT, mesh_digest, require_session_scene  # noqa: E402
from common import PINNED_REFERENCE_SHA256, load_primary_trace  # noqa: E402

import bpy
from mathutils import Vector


PASS_ID = "primary_composition_v01"
PLAN_PATH = PROTOCOL_PATH.with_name("collector_v05_primary_composition_v01.json")


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, _protocol_hash, working = require_session_scene(payload)
    if working.name != WORKING_OBJECT:
        raise ValueError("Primary composition must edit the one protected direct working mesh.")
    if bpy.context.scene.get("pm_direct_open_pass") != PASS_ID:
        raise ValueError("Open the protected primary_composition_v01 pass before applying its authoring operation.")
    if working.get("pm_direct_primary_composition_applied"):
        raise ValueError("The primary composition operation is one-shot; inspect its audit instead of stacking it.")
    plan = _load_plan(protocol)
    trace, _trace_hash = load_primary_trace()
    before = mesh_digest(working)
    points = [_world(working, vertex.co) for vertex in working.data.vertices]
    front_skin = _front_skin_depth(points)
    changed: set[int] = set()
    changed.update(_shape_dorsal_sacs(working, points, front_skin, plan, trace))
    changed.update(_shape_front_drape(working, points, front_skin, plan, trace))
    changed.update(_lower_underbody(working, points, front_skin, plan, trace))
    changed.update(_plant_support_roots(working, points, front_skin, plan, trace))
    if len(changed) < 500:
        raise ValueError("Primary composition touched too little of the existing v05 surface to be credible.")
    working.data.update()
    after = mesh_digest(working)
    if before == after:
        raise ValueError("Primary composition did not alter the existing working surface.")
    working["pm_direct_primary_composition_applied"] = True
    working["pm_direct_primary_composition_plan"] = PLAN_PATH.name
    working["pm_direct_primary_composition_changed_vertices"] = len(changed)
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {
        "asset_id": ASSET_ID,
        "pass_id": PASS_ID,
        "working_object": working.name,
        "changed_vertices": len(changed),
        "before_mesh_sha256": before,
        "after_mesh_sha256": after,
        "rule": "direct deformation of the existing v05 surface only; no new meshes or topology construction",
        "required_next": "complete_collector_direct_mesh_pass then visual review",
    }


def _load_plan(protocol: dict[str, object]) -> dict[str, object]:
    encoded = PLAN_PATH.read_bytes()
    plan = json.loads(encoded.decode("utf-8"))
    if plan.get("schema") != "pale_mirror_visuals.collector_direct_primary_composition.v1":
        raise ValueError("Primary composition plan has an unsupported schema.")
    if plan.get("asset_id") != ASSET_ID or plan.get("session_id") != protocol.get("session_id") or plan.get("pass_id") != PASS_ID:
        raise ValueError("Primary composition plan does not belong to this direct session.")
    reference = plan.get("reference")
    if not isinstance(reference, dict) or reference.get("sha256") != PINNED_REFERENCE_SHA256:
        raise ValueError("Primary composition plan is not pinned to the literal source reference.")
    targets = plan.get("targets")
    if not isinstance(targets, dict) or len(targets.get("dorsal_sacs", [])) != 6:
        raise ValueError("Primary composition plan must declare exactly six dorsal sacks.")
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


def _front_skin_depth(points: list[Vector]) -> dict[tuple[int, int], float]:
    """Return the nearest pre-existing surface to the locked primary camera.

    The primary camera looks down +Y from negative Y.  These bins choose only
    that existing exterior layer; they do not manufacture a new camera card or
    move hidden opposite-side anatomy through the visible surface.
    """
    result: dict[tuple[int, int], float] = {}
    for point in points:
        key = (round(point.x / 0.22), round(point.z / 0.22))
        result[key] = min(result.get(key, point.y), point.y)
    return result


def _front_weight(point: Vector, front_skin: dict[tuple[int, int], float], depth: float = 1.35) -> float:
    nearest = front_skin.get((round(point.x / 0.22), round(point.z / 0.22)))
    if nearest is None:
        return 0.0
    return max(0.0, min(1.0, 1.0 - (point.y - nearest) / depth))


def _smoothstep(value: float) -> float:
    clamped = max(0.0, min(1.0, value))
    return clamped * clamped * (3.0 - 2.0 * clamped)


def _ellipse_weight(point: Vector, center: Vector, radius_x: float, radius_z: float) -> float:
    if radius_x <= 0.0 or radius_z <= 0.0:
        raise ValueError("Primary composition ellipse has invalid radius.")
    distance = ((point.x - center.x) / radius_x) ** 2 + ((point.z - center.z) / radius_z) ** 2
    return _smoothstep(1.0 - distance)


def _shape_dorsal_sacs(
    working: bpy.types.Object,
    points: list[Vector],
    front_skin: dict[tuple[int, int], float],
    plan: dict[str, object],
    trace: dict[str, object],
) -> set[int]:
    changed: set[int] = set()
    targets = plan["targets"]
    for entry in targets["dorsal_sacs"]:
        center = _pixel(trace, entry["center_px"])
        radius_x = float(entry["radius_px"][0]) * float(trace["primary_camera_mapping"]["world_units_per_pixel"])
        radius_z = float(entry["radius_px"][1]) * float(trace["primary_camera_mapping"]["world_units_per_pixel"])
        push = float(entry["front_push"])
        lift = float(entry["crest_lift"])
        for index, point in enumerate(points):
            # Sack relief is a camera-facing local reshaping of the existing
            # upper exterior.  It leaves low supports and rear fibres alone.
            if point.z < center.z - radius_z * 0.78:
                continue
            weight = _ellipse_weight(point, center, radius_x, radius_z) * _front_weight(point, front_skin)
            if weight < 0.035:
                continue
            updated = point.copy()
            updated.y -= push * weight
            updated.z += lift * weight
            working.data.vertices[index].co = _local(working, updated)
            changed.add(index)
    return changed


def _curve_value(curve: list[list[float]], z: float, trace: dict[str, object]) -> float:
    controls = sorted((_pixel(trace, point) for point in curve), key=lambda point: point.z)
    if z <= controls[0].z:
        return controls[0].x
    if z >= controls[-1].z:
        return controls[-1].x
    for left, right in zip(controls, controls[1:]):
        if left.z <= z <= right.z:
            fraction = (z - left.z) / max(0.0001, right.z - left.z)
            return left.x + (right.x - left.x) * fraction
    raise AssertionError("Drape curve interpolation did not cover its domain.")


def _outer_drape_x(points: list[Vector]) -> dict[int, float]:
    outer: dict[int, float] = {}
    for point in points:
        key = round(point.z / 0.20)
        outer[key] = min(outer.get(key, point.x), point.x)
    return outer


def _shape_front_drape(
    working: bpy.types.Object,
    points: list[Vector],
    front_skin: dict[tuple[int, int], float],
    plan: dict[str, object],
    trace: dict[str, object],
) -> set[int]:
    target = plan["targets"]["front_drape"]
    edge = target["outer_edge_px"]
    controls = [_pixel(trace, point) for point in edge]
    low_z, high_z = min(point.z for point in controls), max(point.z for point in controls)
    outer = _outer_drape_x(points)
    changed: set[int] = set()
    for index, point in enumerate(points):
        if not low_z <= point.z <= high_z or point.x > -6.2:
            continue
        front = _front_weight(point, front_skin, 1.05)
        if front <= 0.0:
            continue
        local_outer = outer.get(round(point.z / 0.20), point.x)
        skin = _smoothstep(1.0 - max(0.0, point.x - local_outer) / 1.25)
        if skin <= 0.02:
            continue
        desired_x = _curve_value(edge, point.z, trace)
        outward = max(0.0, point.x - desired_x)
        updated = point.copy()
        weight = front * skin
        updated.x -= min(1.15, outward) * weight
        updated.y -= float(target["outer_push"]) * weight
        ground = _smoothstep((2.8 - point.z) / 2.6)
        updated.z = max(0.035, updated.z - float(target["ground_pull"]) * weight * ground)
        working.data.vertices[index].co = _local(working, updated)
        changed.add(index)
    # Folds are depth relief on that same pre-existing front skin.  The source
    # curves are visual landmarks, never extruded rails or separate geometry.
    for fold in target["folds_px"]:
        controls = [_pixel(trace, point) for point in fold]
        low, high = min(point.z for point in controls), max(point.z for point in controls)
        for index, point in enumerate(points):
            if not low <= point.z <= high or point.x > -6.0:
                continue
            front = _front_weight(point, front_skin, 0.85)
            if front <= 0.0:
                continue
            curve_x = _curve_value(fold, point.z, trace)
            ridge = _smoothstep(1.0 - abs(point.x - curve_x) / 0.42)
            if ridge <= 0.03:
                continue
            updated = point.copy()
            updated.y -= float(target["fold_push"]) * front * ridge
            working.data.vertices[index].co = _local(working, updated)
            changed.add(index)
    return changed


def _lower_underbody(
    working: bpy.types.Object,
    points: list[Vector],
    front_skin: dict[tuple[int, int], float],
    plan: dict[str, object],
    trace: dict[str, object],
) -> set[int]:
    target = plan["targets"]["underbody"]
    center = _pixel(trace, target["center_px"])
    radius_x = float(target["radius_px"][0]) * float(trace["primary_camera_mapping"]["world_units_per_pixel"])
    radius_z = float(target["radius_px"][1]) * float(trace["primary_camera_mapping"]["world_units_per_pixel"])
    changed: set[int] = set()
    for index, point in enumerate(points):
        front = _front_weight(point, front_skin, 1.0)
        if front <= 0.0 or point.z < center.z - radius_z:
            continue
        weight = _ellipse_weight(point, center, radius_x, radius_z) * front
        if weight <= 0.035:
            continue
        updated = point.copy()
        updated.z = max(0.50, updated.z - float(target["drop"]) * weight)
        working.data.vertices[index].co = _local(working, updated)
        changed.add(index)
    return changed


def _plant_support_roots(
    working: bpy.types.Object,
    points: list[Vector],
    front_skin: dict[tuple[int, int], float],
    plan: dict[str, object],
    trace: dict[str, object],
) -> set[int]:
    changed: set[int] = set()
    units = float(trace["primary_camera_mapping"]["world_units_per_pixel"])
    for target in plan["targets"]["support_roots"]:
        center = _pixel(trace, target["center_px"])
        radius_x = float(target["radius_px"][0]) * units
        radius_z = float(target["radius_px"][1]) * units
        for index, point in enumerate(points):
            # Do not try to straighten the whole limb.  Only thicken the
            # planted near-side root to make the existing arch read heavier.
            weight = _ellipse_weight(point, center, radius_x, radius_z) * _front_weight(point, front_skin, 0.90)
            if weight <= 0.055:
                continue
            updated = point.copy()
            side = -1.0 if point.x < center.x else 1.0
            updated.x += side * float(target["thicken"]) * weight
            updated.y -= 0.08 * weight
            working.data.vertices[index].co = _local(working, updated)
            changed.add(index)
    return changed


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
