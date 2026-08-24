"""HISTORICAL REJECTED: add the first image-led three-dimensional volume behind Collector trace v02.

The locked profile and its source-pixel guide are deliberately left untouched.
This operation only builds the volume which the supplied primary image cannot
fully specify: the rear depth of the fused belly, dorsal sacs, articulated
supports and draped/tapered trailing forms.  Every primary-facing contour rail
below is recorded in the same primary x/z coordinate system as the trace, and
all generated vertices are held behind the literal profile plane (+Y).
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
    add_annular_ridge,
    asset_id,
    add_dorsal_lobe,
    add_tapered_tube,
    add_uv_sphere,
    collector_objects,
    ensure_audit_stage,
    load_primary_trace,
    organic_material,
    trace_relief_surface,
    trace_silhouette_volume,
)

import bpy


_VOLUME_MARKER = "collector_primary_trace_v02_volume_v09"
_PROFILE_FRONT_Y = 0.14


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError("Collector source does not exist; run create_collector_base first.")
    if bpy.data.filepath != str(SOURCE_PATH):
        bpy.ops.wm.open_mainfile(filepath=str(SOURCE_PATH))
    trace, trace_hash = load_primary_trace()
    scene = bpy.context.scene
    phase = str(scene.get("pm_authoring_phase", ""))
    if phase not in {"primary_trace_base", "volume_iteration_v01", "volume_iteration_v02", "volume_iteration_v03", "volume_iteration_v04", "volume_iteration_v05", "volume_iteration_v06", "volume_iteration_v07", "volume_iteration_v08", "volume_iteration_v09"}:
        raise ValueError(f"Collector volume may only follow the trace base, found phase {phase!r}.")
    if scene.get("pm_primary_trace_id") != trace["trace_id"] or scene.get("pm_primary_trace_sha256") != trace_hash:
        raise ValueError("Collector source no longer matches the locked v02 trace contract.")
    lod0 = bpy.data.collections.get(LOD0_NAME)
    if lod0 is None:
        raise ValueError("Collector LOD0 collection is missing; rebuild the trace base first.")

    _clear_previous_volume()
    _preserve_literal_profile_as_audit_guide()
    shell = organic_material("PM_COLLECTOR_SHELL_V01", (0.31, 0.011, 0.028), 0.42)
    tissue = organic_material("PM_COLLECTOR_TISSUE_V01", (0.16, 0.005, 0.014), 0.58)
    ridge = organic_material("PM_COLLECTOR_RIDGE_V01", (0.42, 0.024, 0.047), 0.36)

    # The renderable primary-facing surface is a connected source-pixel mesh,
    # not a flat card. It locks boundary pixels and gives only interior
    # landmark areas relief depth. A hidden closed duplicate clips all deep
    # diagnostic anatomy without contributing export geometry.
    primary_surface = trace_relief_surface(
        lod0,
        "collector_source_primary_relief",
        trace,
        tissue,
        front_y=_PROFILE_FRONT_Y,
        # Six source pixels per relief cell retain the locked contour to an
        # image-scale tolerance while leaving triangle budget for actual
        # diagnostic anatomy rather than invisible raster tessellation.
        grid_factor=3,
    )
    silhouette = trace_silhouette_volume(
        lod0,
        "collector_source_silhouette_clip",
        trace,
        tissue,
        front_y=_PROFILE_FRONT_Y,
        depth=9.0,
    )
    silhouette.hide_render = True
    silhouette["pm_export_exclude"] = True
    created: list[bpy.types.Object] = []
    # The primary image reads one low, continuous mass beneath its six rounded
    # sacs.  These rails are direct x/z readings of that mass; only the depth
    # values are supplied from the diagnostic turntable.
    created.append(_add_body_hull(lod0, tissue))
    # Large dorsal forms are intentionally uneven and descending.  They are
    # not a row of generic spheres: each centre/top/width is fixed from the
    # supplied image's six clearly visible shell sacs.
    for index, (location, scale, lean, asymmetry) in enumerate(_DORSAL_SACS, start=1):
        object_ = add_dorsal_lobe(
            lod0,
            f"collector_dorsal_sac_{index:02d}",
            location,
            scale,
            shell,
            lean=lean,
            asymmetry=asymmetry,
        )
        created.append(object_)
    # The reference shows six huge, ringed walking supports, with the foremost
    # support visually swallowed by the leading mantle.  Their x/z paths are
    # literal visible rails, while positive Y separates the unseen-side depth.
    for index, (points, radii) in enumerate(_SUPPORTS, start=1):
        created.extend(_add_segmented_support(lod0, f"collector_support_{index:02d}", points, radii, tissue, ridge))
    created.extend(_add_mantle(lod0, tissue, ridge))
    created.extend(_add_tail(lod0, tissue, ridge))
    created = _fuse_structural_anatomy(created)
    created = _clip_to_source_silhouette(silhouette, created)
    _simplify_clipped_anatomy(created)
    created = [primary_surface, *created]
    _tag_volume(created)

    scene["pm_authoring_phase"] = "volume_iteration_v09"
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_volume_iteration"] = _VOLUME_MARKER
    scene["pm_volume_primary_rule"] = "a connected source-pixel relief surface owns the exact primary envelope; deep anatomy is clipped to an excluded closed source volume and starts behind y=0.14"
    scene["pm_primary_profile_render_policy"] = "the literal two-pixel trace remains a hidden direct-overlay guide; the renderable surface is a shared-vertex mesh with source-led interior depth"
    ensure_audit_stage()
    bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE_PATH))
    vertices = [vertex.co.y for object_ in created for vertex in object_.data.vertices]
    return {
        "asset_id": ASSET_ID,
        "authoring_phase": scene["pm_authoring_phase"],
        "trace_id": trace["trace_id"],
        "volume_objects": len(created),
        "volume_triangles": sum(len(polygon.vertices) - 2 for object_ in created for polygon in object_.data.polygons),
        "minimum_local_y": min(vertices),
        "profile_front_y": _PROFILE_FRONT_Y,
    }


def _clear_previous_volume() -> None:
    for object_ in list(bpy.data.objects):
        if (
            object_.get("pm_volume_behind_primary_trace")
            or object_.get("pm_primary_relief")
            or object_.get("pm_primary_relief_shell")
            or object_.get("pm_trace_role") == "source_pixel_silhouette_volume"
        ):
            bpy.data.objects.remove(object_, do_unlink=True)


def _preserve_literal_profile_as_audit_guide() -> None:
    """Keep the sparse source cells as a precise audit guide only.

    The current renderable substrate is `collector_source_silhouette_volume`,
    a closed mesh whose boundary is compiled from these same cells. Rendering
    both would turn a legitimate volume into a visibly rasterised card.
    """
    for object_ in collector_objects(0):
        if object_.get("pm_trace_role") == "literal_primary_silhouette":
            object_.hide_render = True
            object_["pm_export_exclude"] = True


def _clip_to_source_silhouette(
    silhouette: bpy.types.Object,
    anatomy: list[bpy.types.Object],
) -> list[bpy.types.Object]:
    """Prevent diagnostic volume from ever widening the traced primary form."""
    survivors: list[bpy.types.Object] = []
    for object_ in anatomy:
        bpy.ops.object.select_all(action="DESELECT")
        object_.select_set(True)
        bpy.context.view_layer.objects.active = object_
        modifier = object_.modifiers.new("source_silhouette_clip", "BOOLEAN")
        modifier.operation = "INTERSECT"
        modifier.solver = "EXACT"
        modifier.object = silhouette
        bpy.ops.object.modifier_apply(modifier=modifier.name)
        if not object_.data.vertices:
            # A source-shaped clip may legitimately remove a tiny ridge where
            # the pinned silhouette has no visible area. It is not a hidden
            # runtime object, so delete it rather than letting a later audit
            # misread an empty mesh as a valid anatomical feature.
            bpy.data.objects.remove(object_, do_unlink=True)
            continue
        survivors.append(object_)
    return survivors


def _simplify_clipped_anatomy(anatomy: list[bpy.types.Object]) -> None:
    """Spend triangles on source-visible relief, not hidden Boolean tessellation."""
    for object_ in anatomy:
        if object_.name != "collector_fused_source_anatomy":
            continue
        bpy.ops.object.select_all(action="DESELECT")
        object_.select_set(True)
        bpy.context.view_layer.objects.active = object_
        modifier = object_.modifiers.new("diagnostic_volume_budget", "DECIMATE")
        modifier.decimate_type = "COLLAPSE"
        modifier.ratio = 0.34
        bpy.ops.object.modifier_apply(modifier=modifier.name)


def _tag_volume(objects: list[bpy.types.Object]) -> None:
    for object_ in objects:
        object_["pm_volume_iteration"] = _VOLUME_MARKER
        # Relief deliberately sits on the source-facing skin but remains inset
        # in x/z; only the actual hidden-side masses participate in the
        # positive-Y depth invariant.
        object_["pm_volume_behind_primary_trace"] = True


def _fuse_structural_anatomy(created: list[bpy.types.Object]) -> list[bpy.types.Object]:
    """Fuse overlapping source-led masses into one organic Collector volume.

    The earlier individual components are construction strokes, never the
    finished read.  Voxel remesh performs the small, deterministic organic
    fusion that turns their deliberately overlapping source-positioned forms
    into a single low belly, joined dorsal ridge and rooted support masses.
    Fine drape/tail filaments stay separate because a fuse would erase them.
    """
    filaments = [
        object_
        for object_ in created
        if "outer_cord" in object_.name or "tail_fibre" in object_.name or "ridge_band" in object_.name
    ]
    structural = [object_ for object_ in created if object_ not in filaments]
    if not structural:
        raise ValueError("Collector has no structural anatomy to fuse.")
    bpy.ops.object.select_all(action="DESELECT")
    for object_ in structural:
        object_.select_set(True)
    bpy.context.view_layer.objects.active = structural[0]
    bpy.ops.object.join()
    fused = bpy.context.object
    fused.name = "collector_fused_source_anatomy"
    fused.data.name = "collector_fused_source_anatomy_mesh"
    modifier = fused.modifiers.new("source_led_organic_fusion", "REMESH")
    modifier.mode = "VOXEL"
    # The primary surface now owns detailed source-pixel contour.  A 0.48
    # voxel grid keeps the unseen organic volume coherent while reserving the
    # hard LOD0 budget for that image-led surface and the fine filaments.
    modifier.voxel_size = 0.48
    modifier.use_smooth_shade = True
    bpy.ops.object.modifier_apply(modifier=modifier.name)
    for polygon in fused.data.polygons:
        polygon.use_smooth = True
    return [fused, *filaments]


def _add_body_hull(target: bpy.types.Collection, material: bpy.types.Material) -> bpy.types.Object:
    """Create a continuous front-biased organic hull from primary image rails."""
    # x, visible top, visible belly, hidden-side depth.  The top/bottom rails
    # are sampled against source pixels, never inherited from v05-v16.
    sections = (
        (-9.55, 8.78, 2.05, 3.00),
        (-7.82, 9.05, 1.70, 3.42),
        (-5.85, 8.86, 1.56, 3.72),
        (-3.74, 8.34, 1.48, 3.86),
        (-1.55, 7.82, 1.44, 3.94),
        (0.62, 7.18, 1.46, 3.78),
        (2.76, 6.52, 1.58, 3.44),
        (4.74, 5.72, 1.78, 2.94),
        (6.54, 4.88, 2.08, 2.42),
        (8.14, 4.06, 2.46, 1.70),
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
            # The frontmost point stays safely behind the trace plane.  The
            # camera can still see a true ellipse of depth once it moves off
            # the locked primary view.
            y = _PROFILE_FRONT_Y + depth * (1.0 - math.cos(angle)) * 0.5
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
    mesh = bpy.data.meshes.new("collector_continuous_belly_hull_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(material)
    mesh.update()
    object_ = bpy.data.objects.new("collector_continuous_belly_hull", mesh)
    target.objects.link(object_)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    return object_


# ((location), (scale), lean, asymmetry).  All Y scales retain positive-Y
# clearance from the trace cage.  The primary values are literal image-led
# placements: large left cap, fused high shoulder, then smaller descending
# rear caps into the tapered tail.
_DORSAL_SACS = (
    ((-9.26, 2.92, 8.42), (3.02, 2.70, 2.98), -0.13, -0.12),
    ((-4.82, 2.96, 8.16), (3.16, 2.76, 2.92), -0.10, 0.08),
    ((-0.30, 2.82, 7.18), (3.02, 2.62, 2.70), -0.16, -0.04),
    ((3.72, 2.56, 6.02), (2.68, 2.38, 2.28), -0.18, 0.06),
    ((6.96, 2.28, 4.92), (2.34, 2.08, 1.84), -0.21, 0.02),
    ((9.12, 1.98, 3.92), (1.62, 1.72, 1.34), -0.22, -0.02),
)


# (visible x/y/z support path, radii). These are five low, broad primary-image
# arches -- not the old evenly spaced vertical stumps. Each attachment, knee
# and foot is transcribed from the visible support rhythm in the supplied
# frame; the positive Y coordinate only adds diagnostic depth.
_SUPPORTS = (
    (((-6.72, 1.86, 5.12), (-7.18, 1.98, 3.72), (-6.62, 2.08, 1.82), (-5.58, 2.16, 0.42)), (1.28, 1.22, 1.02, 1.30)),
    (((-3.10, 1.92, 4.96), (-3.64, 2.04, 3.48), (-3.02, 2.14, 1.70), (-1.94, 2.22, 0.40)), (1.26, 1.18, 0.98, 1.28)),
    (((0.46, 1.94, 4.62), (-0.12, 2.06, 3.10), (0.48, 2.16, 1.54), (1.44, 2.24, 0.38)), (1.14, 1.08, 0.90, 1.16)),
    (((3.78, 1.88, 4.18), (3.18, 2.00, 2.76), (3.78, 2.10, 1.32), (4.62, 2.18, 0.36)), (1.00, 0.96, 0.80, 1.04)),
    (((6.68, 1.78, 3.62), (6.08, 1.90, 2.44), (6.62, 2.00, 1.12), (7.42, 2.08, 0.34)), (0.84, 0.82, 0.70, 0.90)),
)


def _add_segmented_support(
    target: bpy.types.Collection,
    name: str,
    points: tuple[tuple[float, float, float], ...],
    radii: tuple[float, ...],
    tissue: bpy.types.Material,
    ridge: bpy.types.Material,
) -> list[bpy.types.Object]:
    created: list[bpy.types.Object] = []
    for index, (start, end, start_radius, end_radius) in enumerate(zip(points, points[1:], radii, radii[1:]), start=1):
        midpoint = tuple((left + right) * 0.5 for left, right in zip(start, end))
        created.append(
            add_tapered_tube(
                target,
                f"{name}_fused_segment_{index:02d}",
                [start, midpoint, end],
                [start_radius * 0.92, max(start_radius, end_radius) * 1.08, end_radius * 0.92],
                tissue,
                sides=18,
            )
        )
    for index, (point, radius) in enumerate(zip(points[1:-1], radii[1:-1]), start=1):
        created.append(
            add_uv_sphere(
                target,
                f"{name}_intersegment_{index:02d}",
                point,
                (radius * 1.06, radius * 0.92, radius * 0.80),
                ridge,
                segments=18,
                ring_count=10,
            )
        )
        # Preserve the source-visible annular musculature after fusion as a
        # real raised band, not as a painted line or detached boot cuff.
        created.append(
            add_annular_ridge(
                target,
                f"{name}_ridge_band_{index:02d}",
                point,
                radius * 0.84,
                ridge,
            )
        )
    # Broad flattened foot gives every support a believable ground contact.
    foot = points[-1]
    created.append(
        add_uv_sphere(
            target,
            f"{name}_grounded_foot",
            (foot[0] - 0.08, foot[1] + 0.12, max(0.24, foot[2])),
            (radii[-1] * 1.20, radii[-1] * 0.94, radii[-1] * 0.50),
            tissue,
            segments=20,
            ring_count=10,
        )
    )
    return created


def _add_mantle(target: bpy.types.Collection, tissue: bpy.types.Material, ridge: bpy.types.Material) -> list[bpy.types.Object]:
    """Build the broad left drape and its source-visible outer cords."""
    created: list[bpy.types.Object] = [_add_mantle_hull(target, tissue)]
    # Broad nested folds: the dark leading curtain reaches the ground well in
    # front of the first support rather than resolving as a seventh leg.
    folds = (
        ([(-8.88, 1.34, 8.04), (-10.96, 1.54, 6.72), (-13.32, 1.66, 4.34), (-15.18, 1.72, 2.12), (-16.10, 1.80, 0.30)], [1.24, 1.02, 0.76, 0.48, 0.20]),
        ([(-8.26, 1.44, 7.48), (-10.16, 1.66, 6.04), (-12.18, 1.80, 3.78), (-13.82, 1.90, 1.72), (-14.62, 1.98, 0.28)], [1.04, 0.84, 0.62, 0.38, 0.16]),
        ([(-7.60, 1.54, 6.82), (-9.16, 1.74, 5.30), (-10.88, 1.88, 3.16), (-12.08, 2.00, 1.40), (-12.60, 2.08, 0.28)], [0.82, 0.66, 0.46, 0.28, 0.12]),
    )
    for index, (points, radii) in enumerate(folds, start=1):
        created.append(add_tapered_tube(target, f"collector_mantle_fold_{index:02d}", points, radii, tissue, sides=18))
    cords = (
        [(-9.72, 0.44, 8.14), (-11.92, 0.40, 6.36), (-14.50, 0.34, 3.16), (-16.44, 0.28, 0.26)],
        [(-9.20, 0.52, 7.74), (-11.26, 0.48, 5.76), (-13.36, 0.42, 2.62), (-14.92, 0.36, 0.24)],
        [(-8.68, 0.60, 7.24), (-10.42, 0.56, 5.18), (-12.04, 0.50, 2.18), (-13.02, 0.44, 0.22)],
        [(-8.18, 0.68, 6.72), (-9.62, 0.64, 4.74), (-10.88, 0.58, 1.76), (-11.48, 0.52, 0.22)],
    )
    for index, points in enumerate(cords, start=1):
        created.append(add_tapered_tube(target, f"collector_mantle_outer_cord_{index:02d}", points, [0.14, 0.10, 0.065, 0.025], ridge, sides=8))
    return created


def _add_mantle_hull(target: bpy.types.Collection, material: bpy.types.Material) -> bpy.types.Object:
    """Make the leading drape a broad three-dimensional curtain, not ropes.

    The outer and inner rails are transcribed from the source-facing silhouette
    and fold root.  Width/depth is the only diagnostic-view interpretation.
    """
    rails = (
        ((-9.34, 8.92), (-6.82, 7.82)),
        ((-11.52, 7.78), (-7.84, 6.46)),
        ((-13.74, 5.56), (-8.98, 4.64)),
        ((-15.24, 2.94), (-9.96, 2.56)),
        ((-16.10, 0.28), (-10.94, 0.30)),
    )
    across = 18
    vertices: list[tuple[float, float, float]] = []
    for outer, inner in rails:
        for column in range(across):
            progress = column / (across - 1)
            arch = math.sin(math.pi * progress)
            x = outer[0] * (1.0 - progress) + inner[0] * progress - 0.34 * arch
            z = outer[1] * (1.0 - progress) + inner[1] * progress + 0.15 * math.sin(math.tau * progress)
            y = _PROFILE_FRONT_Y + 2.65 * arch
            vertices.append((x, y, z))
    faces: list[tuple[int, int, int, int]] = []
    for row in range(len(rails) - 1):
        for column in range(across - 1):
            first = row * across + column
            faces.append((first, first + 1, first + across + 1, first + across))
    # The source-facing outer and inner rails receive narrow side walls so the
    # curtain reads as a real heavy fleshy mass from the opposite camera.
    front = len(vertices)
    vertices.extend((x, _PROFILE_FRONT_Y + 0.04, z) for x, _y, z in vertices[: len(rails) * across])
    for row in range(len(rails) - 1):
        for column in (0, across - 1):
            first = row * across + column
            next_first = first + across
            faces.append((front + first, front + next_first, next_first, first))
    for column in range(across - 1):
        top = column
        bottom = (len(rails) - 1) * across + column
        faces.append((front + top, top, top + 1, front + top + 1))
        faces.append((front + bottom + 1, bottom + 1, bottom, front + bottom))
    mesh = bpy.data.meshes.new("collector_leading_mantle_hull_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(material)
    mesh.update()
    object_ = bpy.data.objects.new("collector_leading_mantle_hull", mesh)
    target.objects.link(object_)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    return object_


def _add_tail(target: bpy.types.Collection, tissue: bpy.types.Material, ridge: bpy.types.Material) -> list[bpy.types.Object]:
    """Build the tapered right tail plus the two visible long source fibres."""
    created = [
        add_tapered_tube(
            target,
            "collector_tapered_tail",
            [(7.58, 1.72, 4.16), (9.64, 1.56, 3.62), (11.76, 1.42, 3.08), (13.86, 1.24, 2.40), (15.82, 1.04, 1.64)],
            [1.28, 1.08, 0.78, 0.46, 0.16],
            tissue,
            sides=20,
        )
    ]
    # These secondary filaments preserve the manually source-traced right
    # contour features without promoting a generated turntable into a primary
    # design source.
    fibres = (
        [(9.90, 0.48, 3.60), (11.72, 0.44, 3.48), (13.64, 0.38, 3.04), (15.78, 0.30, 2.38)],
        [(10.32, 0.56, 3.20), (12.26, 0.50, 2.72), (14.24, 0.42, 1.72), (15.88, 0.34, 0.74)],
        [(9.48, 0.66, 3.88), (11.10, 0.62, 4.10), (13.08, 0.56, 3.84), (15.12, 0.48, 3.22)],
    )
    for index, points in enumerate(fibres, start=1):
        created.append(add_tapered_tube(target, f"collector_tail_fibre_{index:02d}", points, [0.13, 0.09, 0.055, 0.018], ridge, sides=8))
    return created


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
