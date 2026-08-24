"""Give the existing v05 dorsal surface six coherent diagnostic-volume chambers.

The operation is intentionally restricted to the currently open
``diagnostic_solidity_v01`` pass. It moves vertices on the existing near-side
dorsal skin only: no meshes, faces, primitives, rail guides or replacement
objects are created.
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


PASS_ID = "diagnostic_solidity_v01"
PLAN_PATH = PROTOCOL_PATH.with_name("collector_v05_diagnostic_solidity_v01.json")


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, _protocol_hash, working = require_session_scene(payload)
    if working.name != WORKING_OBJECT or bpy.context.scene.get("pm_direct_open_pass") != PASS_ID:
        raise ValueError("Open diagnostic_solidity_v01 on the protected working mesh before applying it.")
    if working.get("pm_direct_diagnostic_solidity_applied"):
        raise ValueError("Diagnostic-solidity authoring is one-shot; inspect it instead of stacking a second operation.")
    plan = _load_plan(protocol)
    trace, _trace_hash = load_primary_trace()
    before = mesh_digest(working)
    points = [_world(working, vertex.co) for vertex in working.data.vertices]
    front_skin = _front_skin_depth(points)
    changed: set[int] = set()
    changed.update(_inflate_chambers(working, points, front_skin, plan, trace))
    changed.update(_relax_saddles(working, points, front_skin, plan, trace))
    if len(changed) < 500:
        raise ValueError("Diagnostic solidity touched too little of the existing dorsal surface.")
    working.data.update()
    after = mesh_digest(working)
    if before == after:
        raise ValueError("Diagnostic solidity did not alter the existing working surface.")
    working["pm_direct_diagnostic_solidity_applied"] = True
    working["pm_direct_diagnostic_solidity_plan"] = PLAN_PATH.name
    working["pm_direct_diagnostic_solidity_changed_vertices"] = len(changed)
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {
        "asset_id": ASSET_ID,
        "pass_id": PASS_ID,
        "changed_vertices": len(changed),
        "before_mesh_sha256": before,
        "after_mesh_sha256": after,
        "rule": "direct existing-surface dorsal deformation only; no new meshes or topology construction",
        "required_next": "complete_collector_direct_mesh_pass then visual review",
    }


def _load_plan(protocol: dict[str, object]) -> dict[str, object]:
    plan = json.loads(PLAN_PATH.read_text(encoding="utf-8"))
    if plan.get("schema") != "pale_mirror_visuals.collector_direct_diagnostic_solidity.v1":
        raise ValueError("Diagnostic solidity plan has an unsupported schema.")
    if plan.get("asset_id") != ASSET_ID or plan.get("session_id") != protocol.get("session_id") or plan.get("pass_id") != PASS_ID:
        raise ValueError("Diagnostic solidity plan does not belong to the active direct session.")
    reference = plan.get("reference")
    if not isinstance(reference, dict) or reference.get("sha256") != PINNED_REFERENCE_SHA256:
        raise ValueError("Diagnostic solidity plan does not pin the literal source reference.")
    chambers = plan.get("dorsal_chambers")
    saddles = plan.get("saddles")
    if not isinstance(chambers, list) or len(chambers) != 6 or not isinstance(saddles, list) or len(saddles) != 5:
        raise ValueError("Diagnostic solidity plan must declare six chambers and five saddles.")
    return plan


def _world(working: bpy.types.Object, coordinate: Vector) -> Vector:
    return working.matrix_world @ coordinate


def _local(working: bpy.types.Object, coordinate: Vector) -> Vector:
    return working.matrix_world.inverted() @ coordinate


def _pixel(trace: dict[str, object], point: list[float]) -> Vector:
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    return Vector(((float(point[0]) - origin_x) * units, 0.0, (origin_y - float(point[1])) * units))


def _front_skin_depth(points: list[Vector]) -> dict[tuple[int, int], float]:
    result: dict[tuple[int, int], float] = {}
    for point in points:
        key = (round(point.x / 0.22), round(point.z / 0.22))
        result[key] = min(result.get(key, point.y), point.y)
    return result


def _front_weight(point: Vector, front_skin: dict[tuple[int, int], float]) -> float:
    nearest = front_skin.get((round(point.x / 0.22), round(point.z / 0.22)))
    if nearest is None:
        return 0.0
    return max(0.0, min(1.0, 1.0 - (point.y - nearest) / 1.55))


def _smoothstep(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return value * value * (3.0 - 2.0 * value)


def _ellipse_weight(point: Vector, center: Vector, radius_x: float, radius_z: float) -> float:
    distance = ((point.x - center.x) / radius_x) ** 2 + ((point.z - center.z) / radius_z) ** 2
    return _smoothstep(1.0 - distance)


def _inflate_chambers(
    working: bpy.types.Object,
    points: list[Vector],
    front_skin: dict[tuple[int, int], float],
    plan: dict[str, object],
    trace: dict[str, object],
) -> set[int]:
    units = float(trace["primary_camera_mapping"]["world_units_per_pixel"])
    changed: set[int] = set()
    for chamber in plan["dorsal_chambers"]:
        center = _pixel(trace, chamber["center_px"])
        radius_x = float(chamber["radius_px"][0]) * units
        radius_z = float(chamber["radius_px"][1]) * units
        for index, point in enumerate(points):
            # The image-side exterior is selected only above the low body. This
            # leaves the approved primary mantle and every support untouched.
            if point.z < center.z - radius_z * 0.74:
                continue
            weight = _ellipse_weight(point, center, radius_x, radius_z) * _front_weight(point, front_skin)
            if weight <= 0.045:
                continue
            updated = point.copy()
            updated.y -= float(chamber["front_push"]) * weight
            updated.z += float(chamber["crest_lift"]) * weight
            working.data.vertices[index].co = _local(working, updated)
            changed.add(index)
    return changed


def _relax_saddles(
    working: bpy.types.Object,
    points: list[Vector],
    front_skin: dict[tuple[int, int], float],
    plan: dict[str, object],
    trace: dict[str, object],
) -> set[int]:
    units = float(trace["primary_camera_mapping"]["world_units_per_pixel"])
    changed: set[int] = set()
    for saddle in plan["saddles"]:
        center = _pixel(trace, saddle["center_px"])
        radius_x = float(saddle["radius_px"][0]) * units
        radius_z = float(saddle["radius_px"][1]) * units
        for index, point in enumerate(points):
            if point.z < center.z - radius_z:
                continue
            weight = _ellipse_weight(point, center, radius_x, radius_z) * _front_weight(point, front_skin)
            if weight <= 0.045:
                continue
            updated = point.copy()
            updated.y += float(saddle["recede"]) * weight
            updated.z -= float(saddle["drop"]) * weight
            working.data.vertices[index].co = _local(working, updated)
            changed.add(index)
    return changed


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
