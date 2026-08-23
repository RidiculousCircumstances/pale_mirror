"""Build a manual, primary-rail Collector cage without Blender primitives.

The old ``trace_cage_v01`` is intentionally retained as review evidence: it
used visually convenient spheres and tapered tubes.  This operation replaces
only the active canonical-source geometry with one editable mesh whose large
forms are compiled from hand-transcribed source-pixel rails.  The supplied
primary image owns every x/z rail.  The reviewed generated turntable supplies
only documented positive depth, never silhouette or landmark placement.

This is a cage-stage asset.  It is not an accepted creature, does not export a
PMMesh and remains deliberately easy to refine in a later manual Blender pass.
"""

from __future__ import annotations

from dataclasses import dataclass, field
import hashlib
import json
import math
from pathlib import Path
import sys
from typing import Any, Iterable

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    ASSET_ID,
    LOD0_NAME,
    SOURCE_PATH,
    WORKSPACE,
    asset_id,
    collector_objects,
    ensure_audit_stage,
    load_primary_trace,
    organic_material,
)

import bpy
from mathutils import Vector


_PHASE = "semantic_cage_v01"
_MIN_DEPTH_Y = 0.04
_TRACE_PROFILE = "collector_primary_trace_base"
_SEMANTIC_PATH = WORKSPACE / "traces/biomass_collector_semantic_cage_v01.json"


@dataclass
class _MeshBuilder:
    """A tiny explicit mesh assembler; it never calls Blender primitives."""

    vertices: list[tuple[float, float, float]] = field(default_factory=list)
    faces: list[tuple[int, ...]] = field(default_factory=list)
    material_indices: list[int] = field(default_factory=list)

    def vertex(self, point: Iterable[float]) -> int:
        self.vertices.append(tuple(float(value) for value in point))
        return len(self.vertices) - 1

    def face(self, vertices: Iterable[int], material: int) -> None:
        indices = tuple(vertices)
        if len(indices) < 3:
            raise ValueError("A semantic cage face needs at least three vertices.")
        self.faces.append(indices)
        self.material_indices.append(material)

    def bridge(self, first: list[int], second: list[int], material: int) -> None:
        if len(first) != len(second):
            raise ValueError("Semantic cage ring bridge has incompatible vertex counts.")
        for index, current in enumerate(first):
            self.face((current, first[(index + 1) % len(first)], second[(index + 1) % len(second)], second[index]), material)


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError("Collector source does not exist; run create_collector_base first.")
    if bpy.data.filepath != str(SOURCE_PATH):
        bpy.ops.wm.open_mainfile(filepath=str(SOURCE_PATH))

    trace, trace_hash = load_primary_trace()
    semantic, semantic_hash = _load_semantic_cage(trace, trace_hash)
    scene = bpy.context.scene
    _require_trace(scene, trace, trace_hash)
    lod0 = bpy.data.collections.get(LOD0_NAME)
    if lod0 is None:
        raise ValueError("Collector LOD0 collection is missing; rebuild the trace base first.")

    _clear_previous_primary_volume()
    _retain_locked_trace_profile()
    materials = (
        organic_material("PM_COLLECTOR_SEMANTIC_TISSUE_V01", (0.19, 0.004, 0.012), 0.60),
        organic_material("PM_COLLECTOR_SEMANTIC_SHELL_V01", (0.36, 0.012, 0.038), 0.43),
        organic_material("PM_COLLECTOR_SEMANTIC_RIDGE_V01", (0.54, 0.030, 0.066), 0.36),
    )
    builder = _MeshBuilder()
    front_y = float(semantic["primary_front_y"])
    _append_body(builder, semantic["body_sections"], trace, front_y)
    for lobe in semantic["dorsal_lobes"]:
        _append_lobe(builder, lobe, trace, front_y)
    for support in semantic["supports"]:
        _append_support(builder, support, trace)
    _append_mantle(builder, semantic["mantle"], trace, front_y)
    _append_tail(builder, semantic["tail"], trace)
    cage = _build_object(lod0, builder, materials)
    _tag_cage(cage, semantic, semantic_hash)

    scene["pm_authoring_phase"] = _PHASE
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_semantic_cage_rule"] = (
        "One primitive-free editable mesh assembled from literal source-pixel rails; the reviewed "
        "turntable constrains positive depth and hidden attachments only."
    )
    scene["pm_semantic_cage_sha256"] = semantic_hash
    scene["pm_primary_profile_render_policy"] = (
        "No source-pixel relief or silhouette-volume substrate may render; the locked literal trace "
        "remains an export-excluded guide for every primary overlay."
    )
    ensure_audit_stage()
    bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE_PATH))
    return {
        "asset_id": ASSET_ID,
        "authoring_phase": _PHASE,
        "semantic_id": semantic["semantic_id"],
        "semantic_sha256": semantic_hash,
        "mesh_object": cage.name,
        "cage_triangles": sum(len(face) - 2 for face in builder.faces),
        "semantic_features": {
            "dorsal_lobes": len(semantic["dorsal_lobes"]),
            "supports": len(semantic["supports"]),
            "mantle_folds": len(semantic["mantle"]["folds"]),
            "tail_fibres": len(semantic["tail"]["fibres"]),
        },
        "primitive_free": True,
        "runtime_export_forbidden": True,
    }


def _load_semantic_cage(trace: dict[str, object], trace_hash: str) -> tuple[dict[str, Any], str]:
    encoded = _SEMANTIC_PATH.read_bytes()
    semantic_hash = hashlib.sha256(encoded).hexdigest()
    data: dict[str, Any] = json.loads(encoded.decode("utf-8"))
    if data.get("schema") != "pale_mirror_visuals.harvester_semantic_cage.v1":
        raise ValueError("Collector semantic cage uses an unsupported schema.")
    if data.get("asset_id") != ASSET_ID or not isinstance(data.get("semantic_id"), str):
        raise ValueError("Collector semantic cage has an invalid identity.")
    source = data.get("primary_trace")
    if not isinstance(source, dict) or source.get("trace_id") != trace["trace_id"] or source.get("sha256") != trace_hash:
        raise ValueError("Collector semantic cage is not pinned to the active literal trace.")
    for field, count in (("dorsal_lobes", 6), ("supports", 6)):
        value = data.get(field)
        if not isinstance(value, list) or len(value) != count:
            raise ValueError(f"Collector semantic cage must contain exactly {count} {field}.")
    if not isinstance(data.get("body_sections"), list) or len(data["body_sections"]) < 6:
        raise ValueError("Collector semantic cage needs primary body rails.")
    if not isinstance(data.get("mantle"), dict) or not isinstance(data.get("tail"), dict):
        raise ValueError("Collector semantic cage lacks mantle or tail rails.")
    return data, semantic_hash


def _require_trace(scene: bpy.types.Scene, trace: dict[str, object], trace_hash: str) -> None:
    allowed = {
        "primary_trace_base",
        "trace_cage_v01",
        "trace_cage_v02",
        _PHASE,
    }
    if str(scene.get("pm_authoring_phase", "")) not in allowed:
        raise ValueError("Semantic cage may only follow the locked primary trace source.")
    if scene.get("pm_primary_trace_id") != trace["trace_id"]:
        raise ValueError("Collector source no longer matches the required trace identity.")
    if scene.get("pm_primary_trace_sha256") != trace_hash:
        raise ValueError("Collector source no longer matches the locked primary trace hash.")


def _clear_previous_primary_volume() -> None:
    for object_ in list(collector_objects(0)):
        if object_.name != _TRACE_PROFILE:
            bpy.data.objects.remove(object_, do_unlink=True)


def _retain_locked_trace_profile() -> None:
    profile = bpy.data.objects.get(_TRACE_PROFILE)
    if profile is None:
        raise ValueError("Collector literal primary trace profile is missing.")
    profile.hide_render = True
    profile.hide_set(True)
    profile["pm_export_exclude"] = True
    profile["pm_trace_role"] = "literal_primary_silhouette"


def _world(trace: dict[str, object], pixel: Iterable[float]) -> tuple[float, float]:
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = (float(value) for value in mapping["origin_pixel"])
    units = float(mapping["world_units_per_pixel"])
    pixel_x, pixel_y = (float(value) for value in pixel)
    return ((pixel_x - origin_x) * units, (origin_y - pixel_y) * units)


def _append_body(builder: _MeshBuilder, sections: list[list[float]], trace: dict[str, object], front_y: float) -> None:
    rings: list[list[int]] = []
    sides = 18
    for pixel_x, top_pixel, bottom_pixel, depth in sections:
        x, top = _world(trace, (pixel_x, top_pixel))
        _, bottom = _world(trace, (pixel_x, bottom_pixel))
        centre = (top + bottom) * 0.5
        radius = (top - bottom) * 0.5
        ring: list[int] = []
        for side in range(sides):
            angle = math.tau * side / sides
            # The primary x/z rails are literal; only y thickness is inferred.
            y = front_y + float(depth) * (1.0 - math.cos(angle)) * 0.5
            z = centre + radius * math.sin(angle)
            ring.append(builder.vertex((x, y, z)))
        rings.append(ring)
    _append_closed_rings(builder, rings, material=0)


def _append_lobe(builder: _MeshBuilder, lobe: dict[str, Any], trace: dict[str, object], front_y: float) -> None:
    rails = lobe["rails"]
    if not isinstance(rails, list) or len(rails) < 4:
        raise ValueError("Each semantic dorsal lobe needs at least four source rails.")
    hidden_depth = float(lobe["hidden_depth"])
    asymmetry = float(lobe.get("asymmetry", 0.0))
    rings: list[list[int]] = []
    sides = 20
    for section_index, (pixel_x, top_pixel, bottom_pixel) in enumerate(rails):
        x, top = _world(trace, (pixel_x, top_pixel))
        _, bottom = _world(trace, (pixel_x, bottom_pixel))
        centre = (top + bottom) * 0.5
        radius = (top - bottom) * 0.5
        progress = section_index / (len(rails) - 1)
        section_depth = hidden_depth * (0.58 + 0.42 * math.sin(math.pi * progress))
        ring: list[int] = []
        for side in range(sides):
            angle = math.tau * side / sides
            asymmetry_scale = max(0.35, 1.0 + asymmetry * math.sin(angle))
            y = front_y + section_depth * asymmetry_scale * (1.0 - math.cos(angle)) * 0.5
            z = centre + radius * math.sin(angle)
            ring.append(builder.vertex((x, y, z)))
        rings.append(ring)
    _append_closed_rings(builder, rings, material=1)


def _append_support(builder: _MeshBuilder, support: dict[str, Any], trace: dict[str, object]) -> None:
    centreline = support["primary_centerline"]
    depths = support["depth_y"]
    radii = support["radii"]
    if not (len(centreline) == len(depths) == len(radii) and len(centreline) >= 4):
        raise ValueError("A semantic support needs matching primary rails, depth and radius values.")
    points = []
    for pixel, depth in zip(centreline, depths):
        x, z = _world(trace, pixel)
        points.append(Vector((x, max(_MIN_DEPTH_Y + float(depth), float(radii[0]) + _MIN_DEPTH_Y), z)))
    _append_path(builder, points, [float(value) for value in radii], material=0, sides=16)
    # Source-visible ring rhythm: these are explicitly placed profile bands,
    # not separately spawned sphere or torus primitives.
    for index in range(1, len(points) - 1):
        _append_path(
            builder,
            [points[index] + Vector((0.0, -0.045, 0.0)), points[index] + Vector((0.0, 0.045, 0.0))],
            [float(radii[index]) * 1.08, float(radii[index]) * 1.08],
            material=2,
            sides=16,
        )
    _append_foot(builder, points[-1], float(radii[-1]), material=0)


def _append_mantle(builder: _MeshBuilder, mantle: dict[str, Any], trace: dict[str, object], front_y: float) -> None:
    outer = mantle["outer_primary_rail"]
    inner = mantle["inner_primary_rail"]
    if not isinstance(outer, list) or not isinstance(inner, list) or len(outer) != len(inner) or len(outer) < 4:
        raise ValueError("Collector mantle needs matching literal outer and inner primary rails.")
    across = 22
    rows = len(outer)
    hidden_width = float(mantle["hidden_half_width"])
    layers: list[list[list[int]]] = []
    for depth_factor in (0.12, 1.0):
        layer: list[list[int]] = []
        for row, (outer_pixel, inner_pixel) in enumerate(zip(outer, inner)):
            outer_x, outer_z = _world(trace, outer_pixel)
            inner_x, inner_z = _world(trace, inner_pixel)
            row_vertices: list[int] = []
            for column in range(across):
                progress = column / (across - 1)
                arch = math.sin(math.pi * progress)
                # The boundary follows the literal two rails.  Interior only
                # receives a restrained fold, leaving the overlay auditable.
                fold = math.sin(math.tau * 1.5 * progress + row * 0.43) * arch
                x = outer_x * (1.0 - progress) + inner_x * progress - 0.20 * fold
                z = outer_z * (1.0 - progress) + inner_z * progress + 0.14 * fold
                y = front_y + hidden_width * depth_factor * arch
                row_vertices.append(builder.vertex((x, y, z)))
            layer.append(row_vertices)
        layers.append(layer)
    for layer_index, layer in enumerate(layers):
        for row in range(rows - 1):
            for column in range(across - 1):
                first = layer[row][column]
                if layer_index == 0:
                    builder.face((first, layer[row][column + 1], layer[row + 1][column + 1], layer[row + 1][column]), 0)
                else:
                    builder.face((first, layer[row + 1][column], layer[row + 1][column + 1], layer[row][column + 1]), 0)
    for row in range(rows - 1):
        for column in (0, across - 1):
            builder.face((layers[0][row][column], layers[0][row + 1][column], layers[1][row + 1][column], layers[1][row][column]), 0)
    for row in (0, rows - 1):
        for column in range(across - 1):
            builder.face((layers[0][row][column], layers[1][row][column], layers[1][row][column + 1], layers[0][row][column + 1]), 0)
    for fold in mantle["folds"]:
        points = []
        for pixel, depth in zip(fold["points"], fold["depth_y"]):
            x, z = _world(trace, pixel)
            points.append(Vector((x, front_y + float(depth), z)))
        _append_path(builder, points, [float(value) for value in fold["radii"]], material=2, sides=12)


def _append_tail(builder: _MeshBuilder, tail: dict[str, Any], trace: dict[str, object]) -> None:
    core = tail["core"]
    _append_primary_path(builder, core, trace, material=0, sides=18)
    for fibre in tail["fibres"]:
        _append_primary_path(builder, fibre, trace, material=2, sides=8, radii=(0.13, 0.09, 0.055, 0.018))


def _append_primary_path(
    builder: _MeshBuilder,
    feature: dict[str, Any],
    trace: dict[str, object],
    *,
    material: int,
    sides: int,
    radii: tuple[float, ...] | None = None,
) -> None:
    points = []
    for pixel, depth in zip(feature["points"], feature["depth_y"]):
        x, z = _world(trace, pixel)
        points.append(Vector((x, max(_MIN_DEPTH_Y + float(depth), _MIN_DEPTH_Y), z)))
    widths = list(radii) if radii is not None else [float(value) for value in feature["radii"]]
    _append_path(builder, points, widths, material=material, sides=sides)


def _append_path(builder: _MeshBuilder, points: list[Vector], radii: list[float], *, material: int, sides: int) -> None:
    if len(points) < 2 or len(points) != len(radii):
        raise ValueError("Semantic rail path needs matching point and radius values.")
    rings: list[list[int]] = []
    for index, point in enumerate(points):
        tangent = (points[min(index + 1, len(points) - 1)] - points[max(index - 1, 0)]).normalized()
        reference = Vector((0.0, 0.0, 1.0)) if abs(tangent.z) < 0.94 else Vector((0.0, 1.0, 0.0))
        normal = tangent.cross(reference).normalized()
        bitangent = tangent.cross(normal).normalized()
        ring: list[int] = []
        for side in range(sides):
            angle = math.tau * side / sides
            offset = normal * math.cos(angle) * radii[index] + bitangent * math.sin(angle) * radii[index]
            candidate = point + offset
            ring.append(builder.vertex((candidate.x, max(_MIN_DEPTH_Y, candidate.y), candidate.z)))
        rings.append(ring)
    _append_closed_rings(builder, rings, material)


def _append_foot(builder: _MeshBuilder, centre: Vector, radius: float, *, material: int) -> None:
    rings: list[list[int]] = []
    for z_offset, scale in ((-radius * 0.42, 0.72), (-radius * 0.12, 1.12), (radius * 0.22, 0.94)):
        ring: list[int] = []
        for side in range(16):
            angle = math.tau * side / 16
            x = centre.x + math.cos(angle) * radius * 1.28 * scale
            y = max(_MIN_DEPTH_Y, centre.y + math.sin(angle) * radius * 0.70 * scale)
            ring.append(builder.vertex((x, y, centre.z + z_offset)))
        rings.append(ring)
    _append_closed_rings(builder, rings, material)


def _append_closed_rings(builder: _MeshBuilder, rings: list[list[int]], material: int) -> None:
    if len(rings) < 2:
        raise ValueError("Semantic cage needs at least two rings.")
    for first, second in zip(rings, rings[1:]):
        builder.bridge(first, second, material)
    builder.face(tuple(reversed(rings[0])), material)
    builder.face(tuple(rings[-1]), material)


def _build_object(
    target: bpy.types.Collection,
    builder: _MeshBuilder,
    materials: tuple[bpy.types.Material, bpy.types.Material, bpy.types.Material],
) -> bpy.types.Object:
    mesh = bpy.data.meshes.new("collector_semantic_cage_v01_mesh")
    mesh.from_pydata(builder.vertices, [], builder.faces)
    mesh.validate(clean_customdata=True)
    mesh.update()
    for material in materials:
        mesh.materials.append(material)
    for polygon, material_index in zip(mesh.polygons, builder.material_indices):
        polygon.material_index = material_index
        polygon.use_smooth = True
    object_ = bpy.data.objects.new("collector_semantic_cage_v01", mesh)
    target.objects.link(object_)
    return object_


def _tag_cage(cage: bpy.types.Object, semantic: dict[str, Any], semantic_hash: str) -> None:
    minimum_y = min((cage.matrix_world @ vertex.co).y for vertex in cage.data.vertices)
    if minimum_y < _MIN_DEPTH_Y:
        raise ValueError(f"Semantic cage crossed the locked primary plane: y={minimum_y:.3f}")
    cage["pm_trace_cage_geometry"] = True
    cage["pm_semantic_cage_geometry"] = True
    cage["pm_semantic_cage_id"] = semantic["semantic_id"]
    cage["pm_semantic_cage_sha256"] = semantic_hash
    cage["pm_volume_behind_primary_trace"] = True
    cage["pm_primitive_free"] = True
    cage["pm_export_exclude"] = False


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
