"""HISTORICAL REJECTED: build the first true three-dimensional, trace-constrained Collector cage.

The preceding volume iterations preserved the locked primary silhouette by
rendering a source-pixel relief sheet in front of their anatomy.  That made
the reference view look superficially correct but produced a flat, serrated
cut-out from every diagnostic view.  This operation deliberately removes that
sheet.  It retains the trace as a locked non-rendered guide and creates only
real, positive-depth organic forms whose primary-facing rails were positioned
from the supplied image.

This is still an unaccepted authoring iteration.  It has no runtime export
path, cannot replace the trace, and must pass the ordinary five-view audit
before any further modelling decision.
"""

from __future__ import annotations

from pathlib import Path
import math
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    ASSET_ID,
    LOD0_NAME,
    SOURCE_PATH,
    add_dorsal_lobe,
    add_drape_surface,
    add_tapered_tube,
    add_uv_sphere,
    asset_id,
    collector_objects,
    ensure_audit_stage,
    load_primary_trace,
    organic_material,
)

import bpy


_PHASE = "trace_cage_v02"
_FRONT_Y = 0.16
_MIN_DEPTH_Y = 0.04
_TRACE_PROFILE = "collector_primary_trace_base"


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError("Collector source does not exist; run create_collector_base first.")
    if bpy.data.filepath != str(SOURCE_PATH):
        bpy.ops.wm.open_mainfile(filepath=str(SOURCE_PATH))

    trace, trace_hash = load_primary_trace()
    scene = bpy.context.scene
    _require_trace(scene, trace, trace_hash)
    lod0 = bpy.data.collections.get(LOD0_NAME)
    if lod0 is None:
        raise ValueError("Collector LOD0 collection is missing; rebuild the trace base first.")

    _clear_previous_primary_volume()
    _retain_locked_trace_profile()
    shell = organic_material("PM_COLLECTOR_CAGE_SHELL_V01", (0.35, 0.012, 0.034), 0.42)
    tissue = organic_material("PM_COLLECTOR_CAGE_TISSUE_V01", (0.18, 0.004, 0.010), 0.58)
    ridge = organic_material("PM_COLLECTOR_CAGE_RIDGE_V01", (0.52, 0.028, 0.060), 0.36)

    created: list[bpy.types.Object] = [_add_body_cage(lod0, tissue)]
    for index, (location, scale, lean, asymmetry) in enumerate(_DORSAL_SACS, start=1):
        created.append(
            add_dorsal_lobe(
                lod0,
                f"collector_cage_dorsal_sac_{index:02d}",
                location,
                scale,
                shell,
                lean=lean,
                asymmetry=asymmetry,
            )
        )
    for index, (points, radii) in enumerate(_SUPPORTS, start=1):
        created.extend(_add_support_cage(lod0, index, points, radii, tissue, ridge))
    created.extend(_add_mantle_cage(lod0, tissue, ridge))
    created.extend(_add_tail_cage(lod0, tissue, ridge))
    _tag_cage(created)

    scene["pm_authoring_phase"] = _PHASE
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_trace_cage_rule"] = (
        "The locked v02 trace remains a non-rendered literal guide; every rendered "
        "Collector form is a positive-depth three-dimensional cage object."
    )
    scene["pm_primary_profile_render_policy"] = (
        "No source-pixel relief or silhouette volume may render; primary likeness is reviewed "
        "against the literal guide through the locked camera."
    )
    ensure_audit_stage()
    bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE_PATH))
    return {
        "asset_id": ASSET_ID,
        "authoring_phase": _PHASE,
        "trace_id": trace["trace_id"],
        "cage_objects": len(created),
        "cage_triangles": sum(len(polygon.vertices) - 2 for object_ in created for polygon in object_.data.polygons),
        "minimum_world_y": min(
            (object_.matrix_world @ vertex.co).y
            for object_ in created
            for vertex in object_.data.vertices
        ),
        "runtime_export_forbidden": True,
    }


def _require_trace(scene: bpy.types.Scene, trace: dict[str, object], trace_hash: str) -> None:
    allowed = {
        "primary_trace_base",
        "volume_iteration_v01",
        "volume_iteration_v02",
        "volume_iteration_v03",
        "volume_iteration_v04",
        "volume_iteration_v05",
        "volume_iteration_v06",
        "volume_iteration_v07",
        "volume_iteration_v08",
        "volume_iteration_v09",
        "trace_cage_v01",
        _PHASE,
    }
    if str(scene.get("pm_authoring_phase", "")) not in allowed:
        raise ValueError("Collector cage may only follow the locked primary trace source.")
    if scene.get("pm_primary_trace_id") != trace["trace_id"]:
        raise ValueError("Collector source no longer matches the required trace identity.")
    if scene.get("pm_primary_trace_sha256") != trace_hash:
        raise ValueError("Collector source no longer matches the locked primary trace hash.")


def _clear_previous_primary_volume() -> None:
    """Remove all failed render geometry but leave immutable trace evidence intact."""
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


def _add_body_cage(target: bpy.types.Collection, material: bpy.types.Material) -> bpy.types.Object:
    """Create the low fused belly with source-positioned primary rails.

    Each x/top/bottom pair was transcribed from the visible body envelope.  It
    is a real closed hull; only its depth is inferred from the diagnostic
    views.  The front-most surface is always behind the locked trace plane.
    """
    sections = (
        (-10.05, 5.94, 4.28, 2.60),
        (-8.18, 5.72, 3.92, 3.05),
        (-6.10, 5.52, 3.70, 3.42),
        (-3.92, 5.34, 3.52, 3.64),
        (-1.72, 5.14, 3.40, 3.72),
        (0.48, 4.92, 3.25, 3.64),
        (2.62, 4.66, 3.12, 3.32),
        (4.60, 4.36, 2.98, 2.86),
        (6.36, 4.00, 2.84, 2.30),
        (7.90, 3.66, 2.70, 1.70),
    )
    sides = 18
    vertices: list[tuple[float, float, float]] = []
    rings: list[list[int]] = []
    for x, top, bottom, depth in sections:
        centre_z = (top + bottom) * 0.5
        radius_z = (top - bottom) * 0.5
        ring: list[int] = []
        for side in range(sides):
            angle = math.tau * side / sides
            y = _FRONT_Y + depth * (1.0 - math.cos(angle)) * 0.5
            z = centre_z + radius_z * math.sin(angle)
            ring.append(len(vertices))
            vertices.append((x, y, z))
        rings.append(ring)
    faces: list[tuple[int, ...]] = []
    for first, second in zip(rings, rings[1:]):
        for side in range(sides):
            faces.append((first[side], first[(side + 1) % sides], second[(side + 1) % sides], second[side]))
    faces.append(tuple(reversed(rings[0])))
    faces.append(tuple(rings[-1]))
    mesh = bpy.data.meshes.new("collector_cage_belly_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(material)
    mesh.update()
    object_ = bpy.data.objects.new("collector_cage_belly", mesh)
    target.objects.link(object_)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    return object_


# Six separate source-visible dorsal arcs, ordered from the broad left cap to
# the subordinate right shoulder.  Their x/z contact rails come from trace v02;
# depth/lean only resolves the unseen side.
_DORSAL_SACS = (
    ((-9.36, 2.94, 8.16), (2.82, 2.74, 2.80), -0.13, -0.12),
    ((-5.18, 2.98, 8.02), (3.00, 2.78, 2.86), -0.11, 0.08),
    ((-0.78, 2.86, 7.14), (2.90, 2.66, 2.64), -0.16, -0.04),
    ((3.10, 2.62, 5.98), (2.64, 2.42, 2.22), -0.18, 0.06),
    ((6.20, 2.34, 4.80), (2.24, 2.12, 1.80), -0.21, 0.02),
    ((8.64, 2.02, 3.90), (1.64, 1.80, 1.30), -0.22, -0.02),
)


# The six walking arches are deliberately broad and unequal.  x/z is taken
# from the primary image's ground contacts and knee rhythm; y separates
# overlapping near/far supports without letting any form cross the trace plane.
_SUPPORTS = (
    (((-7.74, 1.84, 5.06), (-8.28, 1.96, 3.50), (-7.72, 2.06, 1.62), (-6.70, 2.12, 0.32)), (1.30, 1.22, 1.02, 1.24)),
    (((-4.18, 1.90, 4.94), (-4.72, 2.02, 3.30), (-4.12, 2.12, 1.54), (-3.08, 2.20, 0.30)), (1.26, 1.18, 0.98, 1.20)),
    (((-0.62, 1.96, 4.72), (-1.18, 2.08, 3.06), (-0.54, 2.18, 1.44), (0.48, 2.28, 0.30)), (1.18, 1.10, 0.92, 1.14)),
    (((2.78, 1.92, 4.34), (2.22, 2.04, 2.80), (2.84, 2.14, 1.30), (3.72, 2.24, 0.30)), (1.06, 0.98, 0.84, 1.04)),
    (((5.70, 1.84, 3.94), (5.18, 1.96, 2.48), (5.72, 2.06, 1.12), (6.50, 2.16, 0.28)), (0.92, 0.86, 0.74, 0.92)),
    (((8.00, 1.74, 3.52), (7.58, 1.86, 2.24), (8.04, 1.96, 1.02), (8.70, 2.04, 0.26)), (0.78, 0.74, 0.64, 0.80)),
)


def _add_support_cage(
    target: bpy.types.Collection,
    index: int,
    points: tuple[tuple[float, float, float], ...],
    radii: tuple[float, ...],
    tissue: bpy.types.Material,
    ridge: bpy.types.Material,
) -> list[bpy.types.Object]:
    created: list[bpy.types.Object] = []
    name = f"collector_cage_support_{index:02d}"
    for segment, (start, end, start_radius, end_radius) in enumerate(
        zip(points, points[1:], radii, radii[1:]), start=1
    ):
        midpoint = tuple((left + right) * 0.5 for left, right in zip(start, end))
        created.append(
            add_tapered_tube(
                target,
                f"{name}_segment_{segment:02d}",
                (start, midpoint, end),
                (start_radius * 0.90, max(start_radius, end_radius) * 1.06, end_radius * 0.90),
                tissue,
                sides=20,
            )
        )
    for joint, (point, radius) in enumerate(zip(points[1:-1], radii[1:-1]), start=1):
        created.append(
            add_uv_sphere(
                target,
                f"{name}_joint_{joint:02d}",
                point,
                (radius * 1.08, radius * 0.88, radius * 0.78),
                ridge,
                segments=20,
                ring_count=12,
            )
        )
    foot = points[-1]
    created.append(
        add_uv_sphere(
            target,
            f"{name}_grounded_foot",
            (foot[0] - 0.12, foot[1] + 0.16, max(0.24, foot[2])),
            (radii[-1] * 1.30, radii[-1] * 0.94, radii[-1] * 0.52),
            tissue,
            segments=22,
            ring_count=12,
        )
    )
    return created


def _add_mantle_cage(
    target: bpy.types.Collection,
    tissue: bpy.types.Material,
    ridge: bpy.types.Material,
) -> list[bpy.types.Object]:
    """Build the left curtain as a convex multi-fold surface, not a card."""
    before = set(target.all_objects)
    # `add_drape_surface` derives its outer rail from the trace, creates a
    # closed broad surface plus folds/cords, and does not use generic volume
    # primitives.  Its authored primary extents are scaled slightly inward to
    # leave the exact trace outline visible as an audit tolerance rather than
    # covering it with a Boolean-cut sheet.
    add_drape_surface(target, "collector_cage_mantle", tissue)
    created = [object_ for object_ in target.all_objects if object_ not in before and object_.type == "MESH"]
    for object_ in created:
        object_.scale.x = 0.88
        object_.location.y = 3.50
        if object_.data.materials:
            object_.data.materials[0] = tissue if "outer_cord" not in object_.name else ridge
        _move_behind_primary_plane(object_)
    return created


def _move_behind_primary_plane(object_: bpy.types.Object) -> None:
    """Translate an authored diagnostic depth without changing its x/z rails."""
    bpy.context.view_layer.update()
    lowest = min((object_.matrix_world @ vertex.co).y for vertex in object_.data.vertices)
    if lowest < _MIN_DEPTH_Y:
        object_.location.y += _MIN_DEPTH_Y - lowest
        bpy.context.view_layer.update()


def _add_tail_cage(
    target: bpy.types.Collection,
    tissue: bpy.types.Material,
    ridge: bpy.types.Material,
) -> list[bpy.types.Object]:
    created = [
        add_tapered_tube(
            target,
            "collector_cage_tapered_tail",
            ((7.42, 1.74, 3.48), (9.48, 1.58, 3.10), (11.58, 1.44, 2.62), (13.70, 1.26, 2.02), (15.72, 1.06, 1.30)),
            (1.26, 1.06, 0.78, 0.46, 0.14),
            tissue,
            sides=22,
        )
    ]
    fibres = (
        ((9.72, 0.46, 3.06), (11.66, 0.42, 2.94), (13.60, 0.36, 2.56), (15.74, 0.30, 1.98)),
        ((10.18, 0.54, 2.70), (12.18, 0.48, 2.28), (14.18, 0.40, 1.42), (15.86, 0.32, 0.62)),
        ((9.40, 0.64, 3.28), (11.10, 0.58, 3.46), (13.10, 0.52, 3.22), (15.10, 0.44, 2.72)),
    )
    for index, points in enumerate(fibres, start=1):
        created.append(
            add_tapered_tube(
                target,
                f"collector_cage_tail_fibre_{index:02d}",
                points,
                (0.13, 0.09, 0.055, 0.018),
                ridge,
                sides=8,
            )
        )
    return created


def _tag_cage(objects: list[bpy.types.Object]) -> None:
    bpy.context.view_layer.update()
    for object_ in objects:
        for vertex in object_.data.vertices:
            if (object_.matrix_world @ vertex.co).y < _MIN_DEPTH_Y:
                raise ValueError(f"Trace cage crossed the locked primary plane: {object_.name}")
        object_["pm_trace_cage_geometry"] = True
        object_["pm_volume_behind_primary_trace"] = True
        object_["pm_trace_cage_phase"] = _PHASE
        object_["pm_export_exclude"] = False


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
