"""Apply one bounded, source-landmark-led deformation to the v03 Collector branch.

The raw Hunyuan v05 mesh is deliberately the starting topology, not a generic
proxy. This operation never creates topology: every pass moves a declared
existing local surface, snapshots it first and records the literal-source
landmarks that governed the selection. Individual passes are independent
diagnostic experiments on one shared branch; their visual acceptance remains a
whole-composition decision.
"""

from __future__ import annotations

from collections import defaultdict
from pathlib import Path
import math
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_sculpt_common import (  # noqa: E402
    ASSET_ID,
    SESSION_ID,
    ensure_semantic_group,
    new_backup,
    prepare_trace_guides,
    read_receipt,
    require_branch_scene,
    write_receipt,
)

import bpy
from mathutils import Vector


PASS_ID = "front_mantle_v03"
_PASSES = {
    "front_mantle_v03": {
        "group": "front_mantle_outer_drape_v03",
        "requires": "raw_baseline",
        "landmarks": "front_drape_outer_strokes_pixels",
        "handler": "front_mantle",
    },
    "dorsal_rhythm_v03": {
        "group": "dorsal_sac_rhythm_v03",
        "requires": "raw_baseline",
        "landmarks": "dorsal_sac_peaks_pixels",
        "handler": "dorsal_rhythm",
    },
    "supports_v03": {
        "group": "support_arches_v03",
        "requires": "raw_baseline",
        "landmarks": "support_axes_pixels",
        "handler": "supports",
    },
}


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    requested = str(payload.get("pass_id", PASS_ID))
    specification = _PASSES.get(requested)
    if specification is None:
        raise ValueError("Only the declared v03 direct-mesh composition passes are allowed.")
    protocol, protocol_hash, working = require_branch_scene()
    _require_declared_protocol_pass(protocol, requested, specification)
    required = specification["requires"]
    if read_receipt(required) is None:
        raise ValueError(f"Capture the required {required} evidence before applying {requested}.")
    if read_receipt(requested) is not None:
        raise ValueError(f"The {requested} pass already has a receipt; it cannot be replayed.")

    trace, _trace_hash = prepare_trace_guides()
    before = [vertex.co.copy() for vertex in working.data.vertices]
    selected, weights, rule_detail = _select_declared_region(working, protocol, trace, requested)
    selected, excluded = _exclude_prior_semantic_regions(working, requested, selected)
    if len(selected) < 120:
        raise ValueError(f"{requested} retained too few independent local vertices after semantic-region exclusion.")
    backup = new_backup(working, requested)
    _apply_to_selected(working, protocol, trace, requested, selected, weights)
    group = str(specification["group"])
    ensure_semantic_group(working, group, selected)
    working.data.update()
    displacements = [(working.data.vertices[index].co - before[index]).length for index in selected]
    selection_bounds = _selection_bounds(working, selected)
    _replace_backup_with_before(backup, before, requested)
    receipt = write_receipt(
        requested,
        {
            "schema": "pale_mirror_visuals.collector_sculpt_receipt.v1",
            "session_id": SESSION_ID,
            "kind": "direct_mesh_deformation",
            "pass_id": requested,
            "semantic_group": group,
            "protocol_sha256": protocol_hash,
            "working_object": working.name,
            "backup_object": backup.name,
            "affected_vertices": len(selected),
            "excluded_prior_semantic_vertices": excluded,
            "selection_bounds": selection_bounds,
            "max_displacement": max(displacements, default=0.0),
            "mean_displacement": sum(displacements) / len(displacements) if displacements else 0.0,
            "source_landmark_key": specification["landmarks"],
            "rule": rule_detail,
            "runtime_export_forbidden": True,
        },
    )
    bpy.context.scene["pm_sculpt_completed_passes"] = ",".join(
        pass_id for pass_id in _PASSES if read_receipt(pass_id) is not None
    )
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {
        "asset_id": ASSET_ID,
        "session_id": SESSION_ID,
        "pass_id": requested,
        "working_object": working.name,
        "backup": backup.name,
        "affected_vertices": len(selected),
        "excluded_prior_semantic_vertices": excluded,
        "selection_bounds": selection_bounds,
        "max_displacement": max(displacements, default=0.0),
        "receipt": receipt.as_posix(),
        "required_next": "render_collector_sculpt_audit then independent visual review",
        "runtime_export_forbidden": True,
    }


def _require_declared_protocol_pass(protocol: dict[str, object], requested: str, specification: dict[str, str]) -> None:
    passes = protocol.get("passes")
    if not isinstance(passes, list):
        raise ValueError("Collector v03 protocol has no pass list.")
    declared = next((entry for entry in passes if isinstance(entry, dict) and entry.get("id") == requested), None)
    if declared is None:
        raise ValueError(f"Collector v03 protocol does not declare {requested}.")
    if declared.get("requires_previous") != specification["requires"] or declared.get("regions") != [specification["group"]]:
        raise ValueError(f"Collector v03 protocol drifted for {requested}.")


def _select_declared_region(
    working: bpy.types.Object,
    protocol: dict[str, object],
    trace: dict[str, object],
    requested: str,
) -> tuple[list[int], dict[int, float], str]:
    handler = _PASSES[requested]["handler"]
    if handler == "front_mantle":
        return _front_mantle_mask(working, protocol, trace)
    if handler == "dorsal_rhythm":
        return _dorsal_rhythm_mask(working, protocol, trace)
    if handler == "supports":
        return _support_mask(working, protocol, trace)
    raise ValueError(f"Unknown v03 direct-mesh handler: {handler}")


def _apply_to_selected(
    working: bpy.types.Object,
    protocol: dict[str, object],
    trace: dict[str, object],
    requested: str,
    selected: list[int],
    weights: dict[int, float],
) -> None:
    handler = _PASSES[requested]["handler"]
    if handler == "front_mantle":
        _shape_front_mantle(working, protocol, trace, selected, weights)
    elif handler == "dorsal_rhythm":
        _shape_dorsal_rhythm(working, protocol, trace, selected, weights)
    elif handler == "supports":
        _shape_supports(working, protocol, trace, selected, weights)
    else:
        raise ValueError(f"Unknown v03 direct-mesh handler: {handler}")


def _front_mantle_mask(
    working: bpy.types.Object, protocol: dict[str, object], trace: dict[str, object]
) -> tuple[list[int], dict[int, float], str]:
    controls = _world_points(protocol, trace, "front_drape_outer_strokes_pixels")
    target_by_z = _curve_by_axis([(point.z, point.x) for point in controls], "front drape")
    bins: dict[int, list[tuple[int, Vector]]] = defaultdict(list)
    for vertex in working.data.vertices:
        point = working.matrix_world @ vertex.co
        if -16.8 <= point.x <= -8.6 and 0.65 <= point.z <= 6.10:
            bins[round(point.z / 0.18)].append((vertex.index, point))
    selected: list[int] = []
    weights: dict[int, float] = {}
    for entries in bins.values():
        outer_x = min(point.x for _index, point in entries)
        for index, point in entries:
            skin_distance = point.x - outer_x
            target_x = _interpolate(target_by_z, point.z)
            if skin_distance > 1.15 or point.x <= target_x + 0.04:
                continue
            vertical_weight = _edge_weight(point.z, 0.65, 6.10, 0.42)
            weights[index] = (1.0 - skin_distance / 1.15) * vertical_weight
            selected.append(index)
    if len(selected) < 500:
        raise ValueError("Front-mantle source stencil selected too little of the existing raw surface.")
    return (
        selected,
        weights,
        "two literal left-edge drape strokes define a bounded outer-skin target; only the pre-existing low leading surface moves outward/downward with a local depth pull",
    )


def _shape_front_mantle(
    working: bpy.types.Object,
    protocol: dict[str, object],
    trace: dict[str, object],
    selected: list[int],
    weights: dict[int, float],
) -> None:
    controls = _world_points(protocol, trace, "front_drape_outer_strokes_pixels")
    target_by_z = _curve_by_axis([(point.z, point.x) for point in controls], "front drape")
    for index in selected:
        vertex = working.data.vertices[index]
        point = working.matrix_world @ vertex.co
        weight = weights[index]
        target_x = _interpolate(target_by_z, point.z) + 0.035
        move_x = min(1.55, max(0.0, point.x - target_x)) * weight
        vertex.co.x -= move_x
        # Camera looks from negative Y. This is a shallow volume change on the
        # same skin, not a replacement plane or a new mass.
        vertex.co.y -= 0.32 * weight
        if point.z < 3.0:
            vertex.co.z = max(0.02, vertex.co.z - 0.24 * weight * (3.0 - point.z) / 2.35)


def _dorsal_rhythm_mask(
    working: bpy.types.Object, protocol: dict[str, object], trace: dict[str, object]
) -> tuple[list[int], dict[int, float], str]:
    peaks = _world_points(protocol, trace, "dorsal_sac_peaks_pixels")
    _dorsal_crest_curve(peaks)
    bins: dict[int, list[tuple[int, Vector]]] = defaultdict(list)
    x_min = min(point.x for point in peaks) - 1.25
    x_max = max(point.x for point in peaks) + 1.25
    for vertex in working.data.vertices:
        point = working.matrix_world @ vertex.co
        if x_min <= point.x <= x_max and point.z >= 5.0:
            bins[round(point.x / 0.20)].append((vertex.index, point))
    selected: list[int] = []
    weights: dict[int, float] = {}
    for entries in bins.values():
        outer_z = max(point.z for _index, point in entries)
        for index, point in entries:
            depth = outer_z - point.z
            if depth > 0.90:
                continue
            weights[index] = 1.0 - depth / 0.90
            selected.append(index)
    if len(selected) < 750:
        raise ValueError("Dorsal source crest stencil selected too little of the existing raw top surface.")
    return (
        selected,
        weights,
        "six literal dorsal-crest pixels form a descending peak/valley curve; only the existing upper skin is locally lifted or relaxed into that rhythm",
    )


def _shape_dorsal_rhythm(
    working: bpy.types.Object,
    protocol: dict[str, object],
    trace: dict[str, object],
    selected: list[int],
    weights: dict[int, float],
) -> None:
    crest_curve = _dorsal_crest_curve(_world_points(protocol, trace, "dorsal_sac_peaks_pixels"))
    for index in selected:
        vertex = working.data.vertices[index]
        point = working.matrix_world @ vertex.co
        target_z = _interpolate(crest_curve, point.x)
        delta = max(-0.62, min(0.62, target_z - point.z))
        vertex.co.z += delta * weights[index]
        # A small alternating camera-facing relief makes neighbouring crests
        # read as layered sacks from the authoritative primary side.
        phase = math.sin((point.x - crest_curve[0][0]) * 1.4)
        vertex.co.y -= 0.10 * weights[index] * phase


def _support_mask(
    working: bpy.types.Object, protocol: dict[str, object], trace: dict[str, object]
) -> tuple[list[int], dict[int, float], str]:
    axes = _world_points(protocol, trace, "support_axes_pixels")
    selected: list[int] = []
    weights: dict[int, float] = {}
    for vertex in working.data.vertices:
        point = working.matrix_world @ vertex.co
        if not (0.10 <= point.z <= 5.20):
            continue
        axis = min(axes, key=lambda candidate: abs(candidate.x - point.x))
        radius = 0.75 + 0.35 * min(1.0, point.z / 5.20)
        distance = abs(point.x - axis.x)
        if distance > radius:
            continue
        # The highest belly band is intentionally not selected; this pass is
        # about supports and their ground-negative spaces only.
        weights[vertex.index] = (1.0 - distance / radius) * _edge_weight(point.z, 0.10, 5.20, 0.24)
        selected.append(vertex.index)
    if len(selected) < 750:
        raise ValueError("Support-axis source stencil selected too little of the existing raw support geometry.")
    return (
        selected,
        weights,
        "six literal support-axis pixels select only existing vertical lower-body surfaces; local radial widening preserves the raw feet and does not edit tail or dorsal masses",
    )


def _shape_supports(
    working: bpy.types.Object,
    protocol: dict[str, object],
    trace: dict[str, object],
    selected: list[int],
    weights: dict[int, float],
) -> None:
    axes = _world_points(protocol, trace, "support_axes_pixels")
    for index in selected:
        vertex = working.data.vertices[index]
        point = working.matrix_world @ vertex.co
        axis = min(axes, key=lambda candidate: abs(candidate.x - point.x))
        weight = weights[index]
        side_x = -1.0 if point.x < axis.x else 1.0
        side_y = -1.0 if point.y < 0.0 else 1.0
        # The upper segment expands slightly more than the planted foot,
        # retaining a tapered support and existing ground coordinates.
        taper = 0.05 + 0.16 * min(1.0, point.z / 5.20)
        vertex.co.x += side_x * taper * weight
        vertex.co.y += side_y * 0.12 * weight


def _world_points(protocol: dict[str, object], trace: dict[str, object], key: str) -> list[Vector]:
    landmarks = protocol.get("source_landmarks")
    if not isinstance(landmarks, dict) or key not in landmarks:
        raise ValueError(f"Collector v03 protocol does not pin {key}.")
    raw = landmarks[key]
    flat: list[list[float]] = []
    if isinstance(raw, list):
        for entry in raw:
            if isinstance(entry, list) and len(entry) == 2 and all(isinstance(value, (int, float)) for value in entry):
                flat.append(entry)
            elif isinstance(entry, list):
                flat.extend(point for point in entry if isinstance(point, list) and len(point) == 2)
    if not flat:
        raise ValueError(f"Collector v03 source landmark {key} is empty.")
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    return [Vector(((float(x) - origin_x) * units, 0.0, (origin_y - float(y)) * units)) for x, y in flat]


def _curve_by_axis(points: list[tuple[float, float]], name: str) -> list[tuple[float, float]]:
    ordered = sorted(points)
    if len(ordered) < 2 or any(left[0] == right[0] for left, right in zip(ordered, ordered[1:])):
        raise ValueError(f"Collector v03 {name} source landmarks do not form a usable curve.")
    return ordered


def _dorsal_crest_curve(peaks: list[Vector]) -> list[tuple[float, float]]:
    ordered = sorted((point.x, point.z) for point in peaks)
    if len(ordered) != 6 or any(left[0] == right[0] for left, right in zip(ordered, ordered[1:])):
        raise ValueError("Collector v03 requires six distinct source-marked dorsal peak coordinates.")
    curve: list[tuple[float, float]] = [ordered[0]]
    for left, right in zip(ordered, ordered[1:]):
        # Literal peak pixels establish crests. The shallow midpoint settles
        # into a physical groove without inventing another sac.
        curve.append(((left[0] + right[0]) * 0.5, min(left[1], right[1]) - 0.72))
        curve.append(right)
    return curve


def _interpolate(curve: list[tuple[float, float]], position: float) -> float:
    if position <= curve[0][0]:
        return curve[0][1]
    if position >= curve[-1][0]:
        return curve[-1][1]
    for left, right in zip(curve, curve[1:]):
        if left[0] <= position <= right[0]:
            fraction = (position - left[0]) / (right[0] - left[0])
            return left[1] + (right[1] - left[1]) * fraction
    raise AssertionError("Curve interpolation did not cover its source interval.")


def _edge_weight(value: float, low: float, high: float, fraction: float) -> float:
    span = high - low
    edge = max(0.001, span * fraction)
    return min(1.0, max(0.0, min((value - low) / edge, (high - value) / edge)))


def _exclude_prior_semantic_regions(
    working: bpy.types.Object, requested: str, selected: list[int]
) -> tuple[list[int], list[int]]:
    current_group = str(_PASSES[requested]["group"])
    selected_set = set(selected)
    prior: set[int] = set()
    for pass_id, specification in _PASSES.items():
        if pass_id == requested:
            continue
        group = working.vertex_groups.get(str(specification["group"]))
        if group is None:
            continue
        for vertex in working.data.vertices:
            try:
                group.weight(vertex.index)
            except RuntimeError:
                continue
            prior.add(vertex.index)
    if working.vertex_groups.get(current_group) is not None:
        raise ValueError(f"Collector v03 semantic group {current_group} already exists before its receipt.")
    excluded = sorted(selected_set & prior)
    return sorted(selected_set - prior), excluded


def _selection_bounds(working: bpy.types.Object, selected: list[int]) -> dict[str, list[float]]:
    points = [working.matrix_world @ working.data.vertices[index].co for index in selected]
    return {
        "min": [min(point[axis] for point in points) for axis in range(3)],
        "max": [max(point[axis] for point in points) for axis in range(3)],
    }


def _replace_backup_with_before(backup: bpy.types.Object, before: list[Vector], pass_id: str) -> None:
    """Store the precise pre-pass geometry in the backup made for this pass."""
    if backup.get("pm_sculpt_backup_for") != pass_id or len(backup.data.vertices) != len(before):
        raise ValueError(f"Collector v03 pass {pass_id} did not retain its pre-pass backup.")
    for index, coordinate in enumerate(before):
        backup.data.vertices[index].co = coordinate
    backup.data.update()


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "pass_id": PASS_ID}))
