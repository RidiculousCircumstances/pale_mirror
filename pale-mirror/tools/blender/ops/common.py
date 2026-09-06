"""Shared, deterministic Blender-side helpers for the PM authoring allowlist."""

from __future__ import annotations

from pathlib import Path
from typing import Any, Iterable
import hashlib
import json
import math

import bpy
from mathutils import Vector


WORKSPACE = Path(__file__).resolve().parents[3]
ASSET_ID = "biomass_collector"
SOURCE_PATH = WORKSPACE / "assets" / "harvester" / "biomass_collector.blend"
EXPORT_PATH = WORKSPACE / "exports" / "biomass_collector.pmmesh.json"
AUDIT_ROOT = WORKSPACE / "audits" / "biomass_collector"
REFERENCE_PATH = WORKSPACE / "references" / "01_harvester_biomass_collector.jpg"
SECONDARY_TURNTABLE_PATH = WORKSPACE / "references" / "secondary" / "biomass_collector_turntable_v02_canonical_contact_sheet.png"
TRACE_PATH = WORKSPACE / "traces" / "biomass_collector_primary_v03.json"
LOD0_NAME = "PM_COLLECTOR_LOD0"
LOD1_NAME = "PM_COLLECTOR_LOD1"
RIG_NAME = "PM_COLLECTOR_RIG"
TRACE_GUIDE_COLLECTION = "PM_COLLECTOR_PRIMARY_TRACE_GUIDE"
TRACE_GUIDE_NAME = "PM_COLLECTOR_PRIMARY_TRACE_GUIDE"
REFERENCE_PLANE_COLLECTION = "PM_COLLECTOR_PRIMARY_REFERENCE"
REFERENCE_PLANE_NAME = "PM_COLLECTOR_PRIMARY_REFERENCE_PLANE"
TRACE_ID = "biomass_collector_primary_v03"
PINNED_REFERENCE_SHA256 = "788cfe68f11e82a4e280de837090d3ebd2209cb598afbb8016946b1ea33a05fa"


def asset_id(payload: dict[str, Any]) -> str:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    return ASSET_ID


def ensure_directories() -> None:
    SOURCE_PATH.parent.mkdir(parents=True, exist_ok=True)
    EXPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
    AUDIT_ROOT.mkdir(parents=True, exist_ok=True)


def load_primary_trace() -> tuple[dict[str, Any], str]:
    """Load and validate the immutable source-pixel constraint input.

    The trace is deliberately not inferred from any current Blender mesh. A
    changed trace is a new versioned art decision, so the scene stores its
    content hash and later validation fails closed on drift. The trace guides
    and audits direct mesh work; it does not authorize reconstructing anatomy
    from profile rails or primitive masses.
    """
    if not TRACE_PATH.is_file():
        raise FileNotFoundError(f"Primary trace is missing: {TRACE_PATH}")
    encoded = TRACE_PATH.read_bytes()
    trace_hash = hashlib.sha256(encoded).hexdigest()
    trace = json.loads(encoded.decode("utf-8"))
    if trace.get("schema") != "pale_mirror_visuals.harvester_primary_trace.v1":
        raise ValueError("Primary trace has an unsupported schema.")
    if trace.get("asset_id") != ASSET_ID or trace.get("trace_id") != TRACE_ID:
        raise ValueError("Primary trace does not belong to the canonical Collector.")
    source = trace.get("source")
    mapping = trace.get("primary_camera_mapping")
    rows = trace.get("rows")
    if not isinstance(source, dict) or source.get("sha256") != PINNED_REFERENCE_SHA256:
        raise ValueError("Primary trace does not pin the supplied primary reference.")
    if not isinstance(mapping, dict) or not isinstance(rows, list) or not rows:
        raise ValueError("Primary trace lacks camera mapping or silhouette rows.")
    previous_y = -1
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("y"), int) or row["y"] <= previous_y:
            raise ValueError("Primary trace rows must have strictly increasing pixel coordinates.")
        previous_y = row["y"]
        runs = row.get("runs")
        if not isinstance(runs, list) or not runs:
            raise ValueError("Primary trace row has no source-pixel runs.")
        previous_right = -1
        for run in runs:
            if not isinstance(run, list) or len(run) != 2 or not all(isinstance(value, int) for value in run):
                raise ValueError("Primary trace run is malformed.")
            if run[0] < previous_right or run[1] <= run[0]:
                raise ValueError("Primary trace runs overlap or are not ordered.")
            previous_right = run[1]
    return trace, trace_hash


def _trace_world(trace: dict[str, Any], pixel_x: int, pixel_y: int) -> tuple[float, float]:
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    return ((pixel_x - origin_x) * units, (origin_y - pixel_y) * units)


def trace_profile_mesh(
    target: bpy.types.Collection,
    name: str,
    trace: dict[str, Any],
    material: bpy.types.Material,
    *,
    depth: float,
) -> bpy.types.Object:
    """Build the visible primary profile directly from literal trace runs."""
    grid = int(trace["sampling"]["grid_px"])
    vertices: list[tuple[float, float, float]] = []
    faces: list[tuple[int, int, int, int]] = []
    for row in trace["rows"]:
        top_pixel = int(row["y"])
        bottom_pixel = top_pixel + grid
        for left_pixel, right_pixel in row["runs"]:
            left, top = _trace_world(trace, left_pixel, top_pixel)
            right, bottom = _trace_world(trace, right_pixel, bottom_pixel)
            first = len(vertices)
            vertices.extend(((left, depth, top), (right, depth, top), (right, depth, bottom), (left, depth, bottom)))
            faces.append((first, first + 1, first + 2, first + 3))
    mesh = bpy.data.meshes.new(f"{name}_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(material)
    mesh.validate(clean_customdata=True)
    mesh.update()
    object_ = bpy.data.objects.new(name, mesh)
    object_["pm_trace_id"] = trace["trace_id"]
    object_["pm_trace_role"] = "literal_primary_silhouette"
    target.objects.link(object_)
    return object_


def trace_silhouette_volume(
    target: bpy.types.Collection,
    name: str,
    trace: dict[str, Any],
    material: bpy.types.Material,
    *,
    front_y: float,
    depth: float,
) -> bpy.types.Object:
    """Make the locked pixel trace into a real closed source-shaped volume.

    This differs from :func:`trace_profile_mesh`: the latter is deliberately a
    sparse two-pixel audit guide.  Here the exact same occupied source cells
    are first converted into their boundary loops, then Blender fills and
    extrudes those loops into a coherent mesh.  The resulting object has the
    literal primary silhouette at ``front_y`` and real diagnostic depth only
    behind that plane.  It is therefore a modelling substrate, not a camera
    card or a collection of disconnected raster rectangles.
    """
    grid = int(trace["sampling"]["grid_px"])
    crop_left, crop_top, _crop_right, _crop_bottom = (int(value) for value in trace["sampling"]["crop"])
    cells: set[tuple[int, int]] = set()
    for row in trace["rows"]:
        row_index = (int(row["y"]) - crop_top) // grid
        for left, right in row["runs"]:
            for column in range((int(left) - crop_left) // grid, (int(right) - crop_left) // grid):
                cells.add((column, row_index))
    if not cells:
        raise ValueError("Collector trace does not contain any source cells.")

    # Every directed edge keeps occupied cells on its right in source-image
    # coordinates.  That deterministic orientation yields opposite winding
    # for exterior and interior loops so Blender's 2D fill retains leg gaps.
    outgoing: dict[tuple[int, int], list[tuple[int, int]]] = {}

    def edge(start: tuple[int, int], end: tuple[int, int]) -> None:
        outgoing.setdefault(start, []).append(end)

    for column, row in cells:
        if (column, row - 1) not in cells:
            edge((column, row), (column + 1, row))
        if (column + 1, row) not in cells:
            edge((column + 1, row), (column + 1, row + 1))
        if (column, row + 1) not in cells:
            edge((column + 1, row + 1), (column, row + 1))
        if (column - 1, row) not in cells:
            edge((column, row + 1), (column, row))
    for ends in outgoing.values():
        ends.sort()

    unused = {(start, end) for start, ends in outgoing.items() for end in ends}
    loops: list[list[tuple[int, int]]] = []
    while unused:
        start, current = min(unused)
        loop = [start]
        unused.remove((start, current))
        while current != start:
            loop.append(current)
            candidates = sorted(end for begin, end in unused if begin == current)
            if not candidates:
                raise ValueError("Collector trace has a non-closed silhouette boundary.")
            following = candidates[0]
            unused.remove((current, following))
            current = following
            if len(loop) > len(cells) * 4 + 4:
                raise ValueError("Collector trace boundary loop exceeded its finite cell budget.")
        if len(loop) >= 3:
            loops.append(loop)
    if not loops:
        raise ValueError("Collector trace did not produce any closed silhouette loops.")

    curve = bpy.data.curves.new(f"{name}_curve", "CURVE")
    curve.dimensions = "2D"
    curve.resolution_u = 1
    curve.render_resolution_u = 1
    curve.resolution_v = 0
    curve.extrude = depth * 0.5
    curve.resolution_u = 1
    curve.materials.append(material)
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    for loop in loops:
        spline = curve.splines.new("POLY")
        spline.points.add(len(loop) - 1)
        for point, (column, row) in zip(spline.points, loop):
            pixel_x = crop_left + column * grid
            pixel_y = crop_top + row * grid
            world_x = (pixel_x - origin_x) * units
            world_z = (origin_y - pixel_y) * units
            # The curve lives in local XY; the object rotation maps local Y
            # onto world Z and its extrusion axis onto world +Y.
            point.co = (world_x, -world_z, 0.0, 1.0)
        spline.use_cyclic_u = True
    object_ = bpy.data.objects.new(name, curve)
    target.objects.link(object_)
    object_.rotation_euler[0] = -math.pi * 0.5
    bpy.context.view_layer.objects.active = object_
    object_.select_set(True)
    bpy.ops.object.convert(target="MESH")
    object_ = bpy.context.object
    bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
    # Curve filling triangulates every raster-adjacent region.  Those internal
    # triangles convey no primary-shape information: all source contour turns
    # and all leg/tail voids live on the boundary.  Dissolving only coplanar
    # faces preserves that literal boundary while keeping the runtime mesh
    # inside the hard LOD0 budget.
    decimate = object_.modifiers.new("source_trace_planar_dissolve", "DECIMATE")
    decimate.decimate_type = "DISSOLVE"
    decimate.angle_limit = math.radians(0.01)
    bpy.ops.object.modifier_apply(modifier=decimate.name)
    # Curves extrude symmetrically around their local origin.  Translate once
    # so the literal source-facing side sits exactly on the trace plane and no
    # part of the source-shaped depth leaks in front of it.
    minimum_y = min(vertex.co.y for vertex in object_.data.vertices)
    object_.location.y += front_y - minimum_y
    object_["pm_trace_id"] = trace["trace_id"]
    object_["pm_trace_role"] = "source_pixel_silhouette_volume"
    object_["pm_trace_depth"] = depth
    for polygon in object_.data.polygons:
        polygon.use_smooth = True
    return object_


def trace_relief_surface(
    target: bpy.types.Collection,
    name: str,
    trace: dict[str, Any],
    material: bpy.types.Material,
    *,
    front_y: float,
    grid_factor: int = 2,
) -> bpy.types.Object:
    """Build a continuous, source-shaped primary anatomical surface.

    The source mask is sampled into a shared-vertex grid rather than the old
    disconnected scan-line rectangles. Boundary vertices remain exactly on the
    locked primary plane. Interior vertices receive only a documented,
    source-led depth field for the six shell sacs, low belly, heavy front
    mantle and leg roots; this makes a true bas-relief surface while preserving
    the source image's outer contour at the camera.
    """
    source_grid = int(trace["sampling"]["grid_px"])
    if grid_factor < 1:
        raise ValueError("Collector primary relief grid factor must be positive.")
    grid = source_grid * grid_factor
    crop_left, crop_top, _crop_right, _crop_bottom = (int(value) for value in trace["sampling"]["crop"])
    cells: set[tuple[int, int]] = set()
    for row in trace["rows"]:
        source_row = (int(row["y"]) - crop_top) // source_grid
        for left, right in row["runs"]:
            for source_column in range(
                (int(left) - crop_left) // source_grid,
                (int(right) - crop_left) // source_grid,
            ):
                cells.add((source_column // grid_factor, source_row // grid_factor))
    if not cells:
        raise ValueError("Collector trace does not contain any relief cells.")

    vertex_indices: dict[tuple[int, int], int] = {}
    vertices: list[tuple[float, float, float]] = []

    def vertex(column: int, row: int) -> int:
        key = (column, row)
        existing = vertex_indices.get(key)
        if existing is not None:
            return existing
        pixel_x = crop_left + column * grid
        pixel_y = crop_top + row * grid
        mapping = trace["primary_camera_mapping"]
        origin_x, origin_y = mapping["origin_pixel"]
        units = float(mapping["world_units_per_pixel"])
        index = len(vertices)
        vertices.append(((pixel_x - origin_x) * units, front_y, (origin_y - pixel_y) * units))
        vertex_indices[key] = index
        return index

    faces: list[tuple[int, int, int, int]] = []
    edge_counts: dict[tuple[int, int], int] = {}
    for column, row in sorted(cells):
        indices = (vertex(column, row), vertex(column + 1, row), vertex(column + 1, row + 1), vertex(column, row + 1))
        faces.append(indices)
        for first, second in zip(indices, (*indices[1:], indices[0])):
            edge = tuple(sorted((first, second)))
            edge_counts[edge] = edge_counts.get(edge, 0) + 1
    boundary_vertices = {index for edge, count in edge_counts.items() if count == 1 for index in edge}
    for index, (x, _y, z) in enumerate(vertices):
        if index not in boundary_vertices:
            vertices[index] = (x, front_y + _collector_primary_relief_depth(x, z), z)

    mesh = bpy.data.meshes.new(f"{name}_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(material)
    mesh.validate(clean_customdata=True)
    mesh.update()
    object_ = bpy.data.objects.new(name, mesh)
    target.objects.link(object_)
    object_["pm_trace_id"] = trace["trace_id"]
    object_["pm_trace_role"] = "source_pixel_primary_relief_surface"
    object_["pm_trace_sampling_grid_px"] = grid
    object_["pm_primary_relief_rule"] = "boundary locked to source plane; interior depth comes only from source-positioned Collector landmarks"
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    return object_


def _collector_primary_relief_depth(x: float, z: float) -> float:
    """Return a bounded depth field from primary-image anatomical landmarks.

    Values deliberately describe only the depth which the supplied single
    image cannot provide. Their x/z centres, widths and descending order are
    read from the six visible shell sacs, mantle and supports in that image.
    """
    depth = 0.18
    dorsal_sacs = (
        (-8.20, 8.58, 2.52, 2.44, 1.96),
        (-4.28, 8.18, 2.54, 2.34, 2.20),
        (-0.22, 7.48, 2.42, 2.20, 2.04),
        (3.56, 6.22, 2.24, 1.92, 1.72),
        (6.92, 4.96, 1.94, 1.56, 1.36),
        (9.24, 4.06, 1.42, 1.18, 0.96),
    )
    for centre_x, centre_z, radius_x, radius_z, height in dorsal_sacs:
        normalized = ((x - centre_x) / radius_x) ** 2 + ((z - centre_z) / radius_z) ** 2
        if normalized < 1.0:
            depth = max(depth, 0.24 + height * (1.0 - normalized) ** 0.55)
    supports = (
        (-5.90, 2.55, 1.16, 2.76, 0.92),
        (-2.08, 2.50, 1.12, 2.62, 0.96),
        (1.46, 2.36, 1.06, 2.36, 0.88),
        (4.66, 2.24, 0.94, 2.04, 0.78),
        (7.18, 2.04, 0.78, 1.70, 0.64),
    )
    for centre_x, centre_z, radius_x, radius_z, height in supports:
        normalized = ((x - centre_x) / radius_x) ** 2 + ((z - centre_z) / radius_z) ** 2
        if normalized < 1.0:
            depth = max(depth, 0.20 + height * (1.0 - normalized) ** 0.62)
    # The leading head/drape is a low, very broad front mass; it must not
    # collapse into an accidental seventh pillar.
    mantle = ((x + 11.78) / 3.86) ** 2 + ((z - 4.18) / 4.78) ** 2
    if mantle < 1.0:
        depth = max(depth, 0.28 + 1.22 * (1.0 - mantle) ** 0.58)
    return min(depth, 2.48)


def ensure_primary_reference_plane(trace: dict[str, Any]) -> bpy.types.Object:
    """Keep the pinned supplied image in the scene at exact camera mapping."""
    if not REFERENCE_PATH.is_file():
        raise FileNotFoundError(f"Pinned primary reference is missing: {REFERENCE_PATH}")
    target = collection(REFERENCE_PLANE_COLLECTION)
    existing = bpy.data.objects.get(REFERENCE_PLANE_NAME)
    if existing is not None:
        return existing
    source = trace["source"]
    width, height = source["size"]
    left, top = _trace_world(trace, 0, 0)
    right, bottom = _trace_world(trace, width, height)
    mesh = bpy.data.meshes.new(f"{REFERENCE_PLANE_NAME}_mesh")
    mesh.from_pydata(
        ((left, 0.20, top), (right, 0.20, top), (right, 0.20, bottom), (left, 0.20, bottom)),
        [],
        ((0, 1, 2, 3),),
    )
    mesh.uv_layers.new(name="UVMap")
    for index, coordinate in enumerate(((0.0, 1.0), (1.0, 1.0), (1.0, 0.0), (0.0, 0.0))):
        mesh.uv_layers.active.data[index].uv = coordinate
    material = bpy.data.materials.new("PM_COLLECTOR_PRIMARY_REFERENCE_MAT")
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    shader = nodes.new("ShaderNodeBsdfPrincipled")
    image_node = nodes.new("ShaderNodeTexImage")
    image_node.image = bpy.data.images.load(str(REFERENCE_PATH), check_existing=True)
    shader.inputs["Roughness"].default_value = 1.0
    links.new(image_node.outputs["Color"], shader.inputs["Base Color"])
    links.new(shader.outputs["BSDF"], output.inputs["Surface"])
    mesh.materials.append(material)
    object_ = bpy.data.objects.new(REFERENCE_PLANE_NAME, mesh)
    object_.hide_render = True
    object_.hide_select = True
    object_.lock_location = (True, True, True)
    object_.lock_rotation = (True, True, True)
    object_.lock_scale = (True, True, True)
    object_["pm_locked"] = True
    object_["pm_reference"] = source["file"]
    object_["pm_reference_sha256"] = source["sha256"]
    target.objects.link(object_)
    return object_


def fresh_scene() -> None:
    # `bpy.ops.object.delete` respects viewport hiding. A prior primary-trace
    # guide is intentionally hidden, so selection-based deletion leaked it
    # into the next build and silently made audits inspect stale geometry.
    # Remove every object directly before rebuilding a canonical source.
    for object_ in list(bpy.data.objects):
        bpy.data.objects.remove(object_, do_unlink=True)
    for collection in list(bpy.data.collections):
        bpy.data.collections.remove(collection)
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 1280
    scene.render.resolution_y = 720
    scene.render.resolution_percentage = 100
    scene.render.image_settings.file_format = "PNG"
    scene.render.film_transparent = False
    scene.world.use_nodes = True
    background = scene.world.node_tree.nodes.get("Background")
    background.inputs["Color"].default_value = (0.003, 0.001, 0.004, 1.0)
    background.inputs["Strength"].default_value = 0.22


def collection(name: str) -> bpy.types.Collection:
    existing = bpy.data.collections.get(name)
    if existing is not None:
        return existing
    created = bpy.data.collections.new(name)
    bpy.context.scene.collection.children.link(created)
    return created


def link_only(object_: bpy.types.Object, target: bpy.types.Collection) -> bpy.types.Object:
    for current in list(object_.users_collection):
        current.objects.unlink(object_)
    target.objects.link(object_)
    return object_


def organic_material(name: str, base: tuple[float, float, float], roughness: float = 0.48) -> bpy.types.Material:
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    shader = nodes.new("ShaderNodeBsdfPrincipled")
    noise = nodes.new("ShaderNodeTexNoise")
    cells = nodes.new("ShaderNodeTexVoronoi")
    ramp = nodes.new("ShaderNodeValToRGB")
    cell_ramp = nodes.new("ShaderNodeValToRGB")
    material_mix = nodes.new("ShaderNodeMixRGB")
    bump = nodes.new("ShaderNodeBump")
    noise.inputs["Scale"].default_value = 2.7
    noise.inputs["Detail"].default_value = 6.0
    noise.inputs["Roughness"].default_value = 0.72
    cells.feature = "DISTANCE_TO_EDGE"
    cells.distance = "EUCLIDEAN"
    cells.inputs["Scale"].default_value = 21.0
    ramp.color_ramp.elements[0].color = (base[0] * 0.28, base[1] * 0.17, base[2] * 0.20, 1.0)
    ramp.color_ramp.elements[1].color = (min(base[0] * 1.38, 1.0), min(base[1] * 1.2, 1.0), min(base[2] * 1.24, 1.0), 1.0)
    cell_ramp.color_ramp.elements[0].position = 0.038
    cell_ramp.color_ramp.elements[0].color = (0.018, 0.001, 0.004, 1.0)
    cell_ramp.color_ramp.elements[1].position = 0.102
    cell_ramp.color_ramp.elements[1].color = (0.75, 0.75, 0.75, 1.0)
    material_mix.blend_type = "MULTIPLY"
    material_mix.inputs["Fac"].default_value = 0.73
    shader.inputs["Roughness"].default_value = roughness
    shader.inputs["Metallic"].default_value = 0.0
    shader.inputs["Specular IOR Level"].default_value = 0.38
    bump.inputs["Strength"].default_value = 0.32
    bump.inputs["Distance"].default_value = 0.16
    links.new(noise.outputs["Fac"], ramp.inputs["Fac"])
    links.new(cells.outputs["Distance"], cell_ramp.inputs["Fac"])
    links.new(ramp.outputs["Color"], material_mix.inputs[1])
    links.new(cell_ramp.outputs["Color"], material_mix.inputs[2])
    links.new(material_mix.outputs["Color"], shader.inputs["Base Color"])
    links.new(noise.outputs["Fac"], bump.inputs["Height"])
    links.new(bump.outputs["Normal"], shader.inputs["Normal"])
    links.new(shader.outputs["BSDF"], output.inputs["Surface"])
    return material


def add_uv_sphere(
    target: bpy.types.Collection,
    name: str,
    location: tuple[float, float, float],
    scale: tuple[float, float, float],
    material: bpy.types.Material,
    *,
    segments: int = 32,
    ring_count: int = 20,
) -> bpy.types.Object:
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=ring_count, location=location)
    object_ = bpy.context.object
    object_.name = name
    object_.scale = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    for polygon in object_.data.polygons:
        polygon.use_smooth = True
    object_.data.materials.append(material)
    return link_only(object_, target)


def add_tapered_tube(
    target: bpy.types.Collection,
    name: str,
    points: Iterable[tuple[float, float, float]],
    radii: Iterable[float],
    material: bpy.types.Material,
    *,
    sides: int = 14,
) -> bpy.types.Object:
    path = [Vector(point) for point in points]
    widths = list(radii)
    if len(path) < 2 or len(path) != len(widths):
        raise ValueError("Tapered tube needs matching point and radius sequences.")
    vertices: list[tuple[float, float, float]] = []
    faces: list[tuple[int, ...]] = []
    rings: list[list[int]] = []
    for index, point in enumerate(path):
        tangent = (path[min(index + 1, len(path) - 1)] - path[max(index - 1, 0)]).normalized()
        reference = Vector((0.0, 0.0, 1.0)) if abs(tangent.z) < 0.94 else Vector((0.0, 1.0, 0.0))
        normal = tangent.cross(reference).normalized()
        bitangent = tangent.cross(normal).normalized()
        ring: list[int] = []
        for side in range(sides):
            angle = math.tau * side / sides
            offset = normal * math.cos(angle) * widths[index] + bitangent * math.sin(angle) * widths[index]
            ring.append(len(vertices))
            vertices.append(tuple(point + offset))
        rings.append(ring)
    for first, second in zip(rings, rings[1:]):
        for side in range(sides):
            faces.append((first[side], first[(side + 1) % sides], second[(side + 1) % sides], second[side]))
    faces.append(tuple(reversed(rings[0])))
    faces.append(tuple(rings[-1]))
    mesh = bpy.data.meshes.new(f"{name}_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(material)
    mesh.update()
    object_ = bpy.data.objects.new(name, mesh)
    target.objects.link(object_)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    return object_


def add_segmented_support(
    target: bpy.types.Collection,
    name: str,
    points: Iterable[tuple[float, float, float]],
    radii: Iterable[float],
    material: bpy.types.Material,
) -> None:
    """Build one living load-bearing support from overlapping rounded segments.

    A Collector leg must read as one heavy, ringed, irregular biological column
    in the primary image. This differs deliberately from a smooth tube with a
    disconnected foot: neighbouring pieces overlap through rounded joint
    masses, while the final mass is allowed to sink into the ground plane.
    """
    path = list(points)
    widths = list(radii)
    if len(path) < 3 or len(path) != len(widths):
        raise ValueError("Segmented support needs matching paths and at least three points.")
    for index, (start, end, start_radius, end_radius) in enumerate(
        zip(path, path[1:], widths, widths[1:]), start=1
    ):
        midpoint = tuple((left + right) * 0.5 for left, right in zip(start, end))
        add_tapered_tube(
            target,
            f"{name}_segment_{index:02d}",
            [start, midpoint, end],
            [start_radius * 0.88, max(start_radius, end_radius) * 1.04, end_radius * 0.88],
            material,
            sides=16,
        )
    for index, (point, radius) in enumerate(zip(path[1:-1], widths[1:-1]), start=1):
        # Deliberately rounded and slightly vertically compressed: these are
        # biological rings, never the old thin torus cuffs or a hard armour
        # disc. Their screen-space width comes from the primary profile.
        add_uv_sphere(
            target,
            f"{name}_ring_{index:02d}",
            point,
            (radius * 1.06, radius * 0.94, radius * 0.82),
            material,
            segments=14,
            ring_count=8,
        )


def add_belly_hull(
    target: bpy.types.Collection,
    name: str,
    material: bpy.types.Material,
) -> bpy.types.Object:
    """Create the low continuous Collector belly from primary-image sections.

    Each section records an x position plus the visible top/bottom silhouette
    and only the hidden-side depth. The lower rail intentionally sags between
    the roots of the supports; it is not a horizontal capsule or a planar
    underside. The secondary view may constrain depth, never these x/z rails.
    """
    # (x, primary top z, primary lower z, secondary-view half depth)
    sections = (
        (-10.45, 5.64, 2.70, 2.55),
        (-8.40, 5.52, 2.04, 3.14),
        (-6.36, 5.35, 1.68, 3.66),
        (-4.18, 5.18, 1.46, 3.94),
        (-2.00, 5.04, 1.40, 4.03),
        (0.18, 4.88, 1.34, 3.98),
        (2.34, 4.67, 1.46, 3.72),
        (4.42, 4.45, 1.38, 3.34),
        (6.38, 4.10, 1.62, 2.83),
        (8.12, 3.74, 1.96, 2.24),
        (9.62, 3.48, 2.46, 1.54),
    )
    sides = 14
    vertices: list[tuple[float, float, float]] = []
    rings: list[list[int]] = []
    for x, top, bottom, depth in sections:
        center_z = (top + bottom) * 0.5
        radius_z = (top - bottom) * 0.5
        ring: list[int] = []
        for side in range(sides):
            # side 0 is the primary-view lower contour, side 7 its upper
            # contour: the x/z values remain literal authored rails.
            angle = -math.pi * 0.5 + math.tau * side / sides
            ring.append(len(vertices))
            vertices.append((x, math.cos(angle) * depth, center_z + math.sin(angle) * radius_z))
        rings.append(ring)
    faces: list[tuple[int, ...]] = []
    for first, second in zip(rings, rings[1:]):
        for side in range(sides):
            faces.append((first[side], first[(side + 1) % sides], second[(side + 1) % sides], second[side]))
    faces.append(tuple(reversed(rings[0])))
    faces.append(tuple(rings[-1]))
    mesh = bpy.data.meshes.new(f"{name}_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(material)
    mesh.update()
    object_ = bpy.data.objects.new(name, mesh)
    target.objects.link(object_)
    bpy.context.view_layer.objects.active = object_
    object_.select_set(True)
    subdivision = object_.modifiers.new("primary_section_smooth", "SUBSURF")
    subdivision.subdivision_type = "CATMULL_CLARK"
    subdivision.levels = 1
    subdivision.render_levels = 1
    bpy.ops.object.modifier_apply(modifier=subdivision.name)
    for polygon in object_.data.polygons:
        polygon.use_smooth = True
    object_.select_set(False)
    return object_


def add_dorsal_lobe(
    target: bpy.types.Collection,
    name: str,
    location: tuple[float, float, float],
    scale: tuple[float, float, float],
    material: bpy.types.Material,
    *,
    lean: float,
    asymmetry: float,
) -> bpy.types.Object:
    """Create one overlapping, image-profiled Collector dorsal lobe.

    The reference reads a descending fused armour of imperfect soft lobes, not
    a mathematical caterpillar of equal hemispheres. `lean` and `asymmetry`
    are intentionally explicit source parameters so their primary outline can
    be reviewed against pixels rather than introduced by random noise.
    """
    object_ = add_uv_sphere(target, name, location, scale, material, segments=36, ring_count=22)
    for vertex in object_.data.vertices:
        normalized_x = vertex.co.x / scale[0]
        normalized_y = vertex.co.y / scale[1]
        # Heavier lower overlap, a gently flattened summit and a deliberately
        # non-symmetric shoulder make the lobes nest as one dorsal ridge.
        cross_section = max(0.0, 1.0 - normalized_x * normalized_x - normalized_y * normalized_y)
        vertex.co.z -= scale[2] * 0.19 * cross_section
        vertex.co.x += scale[0] * asymmetry * cross_section
        vertex.co.z -= scale[2] * lean * normalized_x * cross_section
    object_.rotation_euler[1] = lean * 0.42
    return object_


def add_annular_ridge(
    target: bpy.types.Collection,
    name: str,
    location: tuple[float, float, float],
    radius: float,
    material: bpy.types.Material,
) -> bpy.types.Object:
    """Add one real raised annular leg ridge; not a painted texture proxy."""
    bpy.ops.mesh.primitive_torus_add(
        major_radius=radius,
        minor_radius=max(0.08, radius * 0.13),
        major_segments=12,
        minor_segments=4,
        location=location,
    )
    object_ = bpy.context.object
    object_.name = name
    object_.data.materials.append(material)
    for polygon in object_.data.polygons:
        polygon.use_smooth = True
    return link_only(object_, target)


def add_drape_surface(
    target: bpy.types.Collection,
    name: str,
    material: bpy.types.Material,
) -> bpy.types.Object:
    """Create the Collector's layered, ground-contacting leading mantle.

    The primary image has one contiguous biological front mass, but not a
    single smooth panel: broad overlapping folds descend from the first two
    sacs and fine cords continue along the outside edge.  Both the broad
    contour and each foreground fold are expressed as explicit geometry here.
    """
    # Each pair is (outer leading contour, inner body contour) in the primary
    # camera's x/z plane.  The pairs run top -> ground and were placed directly
    # from the reference envelope, not inferred from the old primitives.
    contour_pairs = (
        # The outer rail starts at the first sac's high leading edge, swings
        # very far forward, then returns to a broad grounded contact.  The
        # inner rail remains buried beneath that same sac/body junction.  This
        # gives the broad elephantine fall visible in the reference rather
        # than a narrow triangular support.
        ((-10.70, 8.72), (-5.92, 7.64)),
        ((-13.18, 7.26), (-7.42, 6.26)),
        ((-15.12, 5.04), (-8.54, 4.58)),
        ((-16.46, 2.46), (-9.46, 2.50)),
        ((-17.12, 0.30), (-10.58, 0.28)),
    )
    across = 22
    vertices: list[tuple[float, float, float]] = []
    # Front and back halves meet along both traced contours.  Their rounded
    # cross-section makes a real convex volume in diagnostic views without
    # altering the primary-view contour contract.
    for side in (-1.0, 1.0):
        for row, ((outer_x, outer_z), (inner_x, inner_z)) in enumerate(contour_pairs):
            half_width = 2.15 + (0.22 * math.sin(math.pi * row / (len(contour_pairs) - 1)))
            for column in range(across):
                progress = column / (across - 1)
                arch = math.sin(math.pi * progress)
                # The sheet's depth undulates as three broad vertical folds.
                # More importantly, its x envelope retains an asymmetric,
                # heavy fall rather than becoming a mathematically clean arch.
                fold = math.sin(math.tau * 1.75 * progress + row * 0.46) * arch
                x = outer_x * (1.0 - progress) + inner_x * progress - 0.98 * arch - 0.38 * fold
                z = outer_z * (1.0 - progress) + inner_z * progress + 0.24 * fold
                vertices.append((x, side * half_width * arch, z))
    faces: list[tuple[int, int, int, int]] = []
    rows = len(contour_pairs)
    half_size = rows * across
    for side_index in range(2):
        offset = side_index * half_size
        for row in range(rows - 1):
            for column in range(across - 1):
                first = offset + row * across + column
                if side_index == 0:
                    faces.append((first, first + 1, first + across + 1, first + across))
                else:
                    faces.append((first, first + across, first + across + 1, first + 1))
    # Stitch both volumes at outer/inner contours and close at root/ground.
    for row in range(rows - 1):
        for column in (0, across - 1):
            first = row * across + column
            next_first = first + across
            faces.append((first, next_first, half_size + next_first, half_size + first))
    for row in (0, rows - 1):
        for column in range(across - 1):
            first = row * across + column
            faces.append((first, half_size + first, half_size + first + 1, first + 1))
    mesh = bpy.data.meshes.new(f"{name}_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.materials.append(material)
    mesh.update()
    object_ = bpy.data.objects.new(name, mesh)
    target.objects.link(object_)
    bpy.context.view_layer.objects.active = object_
    object_.select_set(True)
    subdivision = object_.modifiers.new("contour_smooth", "SUBSURF")
    subdivision.subdivision_type = "CATMULL_CLARK"
    # One subdivision retains the traced broad fold while keeping the whole
    # asset under the explicit LOD0 runtime triangle budget.
    subdivision.levels = 1
    subdivision.render_levels = 1
    bpy.ops.object.modifier_apply(modifier=subdivision.name)
    for polygon in object_.data.polygons:
        polygon.use_smooth = True
    object_.select_set(False)

    # These are visible primary-image fold ridges, not generic tentacles. Each
    # line begins buried under the foremost sac, travels down the real mantle
    # surface and broadens/narrows at a different rhythm before its grounded
    # end. The negative y offsets intentionally keep the folds in front of the
    # sheet in the authoritative primary camera.
    foreground_folds = (
        (
            [(-10.82, -1.08, 8.28), (-12.52, -2.08, 6.68), (-14.10, -2.82, 4.32), (-15.22, -3.12, 2.05), (-15.82, -3.14, 0.34)],
            [0.64, 0.54, 0.42, 0.30, 0.16],
        ),
        (
            [(-9.90, -1.10, 7.70), (-11.36, -2.10, 6.04), (-12.54, -2.76, 4.12), (-13.42, -3.02, 2.12), (-13.94, -3.04, 0.30)],
            [0.56, 0.48, 0.36, 0.25, 0.13],
        ),
        (
            [(-8.94, -1.00, 7.08), (-10.22, -1.94, 5.58), (-11.18, -2.60, 3.76), (-11.86, -2.90, 1.92), (-12.28, -2.92, 0.31)],
            [0.46, 0.39, 0.30, 0.20, 0.10],
        ),
    )
    for index, (points, radii) in enumerate(foreground_folds, start=1):
        add_tapered_tube(target, f"{name}_fold_{index:02d}", points, radii, material, sides=14)

    # The source image's outer edge breaks into long, fine, irregular cords.
    # They are subordinate to the heavy sheet and intentionally all terminate
    # at or above its silhouette; none becomes a substitute load-bearing leg.
    outer_cords = (
        [(-11.35, -1.18, 8.12), (-13.36, -2.48, 6.70), (-15.54, -3.18, 3.08), (-17.12, -3.34, 0.42)],
        [(-10.72, -1.28, 7.92), (-12.64, -2.56, 6.08), (-14.42, -3.22, 3.02), (-15.42, -3.34, 0.30)],
        [(-10.10, -1.36, 7.56), (-11.82, -2.62, 5.58), (-13.14, -3.18, 2.74), (-13.74, -3.32, 0.25)],
        [(-9.54, -1.34, 7.26), (-10.90, -2.54, 5.28), (-11.92, -3.12, 2.44), (-12.38, -3.26, 0.24)],
    )
    for index, points in enumerate(outer_cords, start=1):
        add_tapered_tube(target, f"{name}_outer_cord_{index:02d}", points, [0.17, 0.13, 0.08, 0.028], material, sides=8)
    return object_


def collector_objects(lod: int) -> list[bpy.types.Object]:
    target = bpy.data.collections.get(LOD0_NAME if lod == 0 else LOD1_NAME)
    if target is None:
        return []
    return sorted((object_ for object_ in target.all_objects if object_.type == "MESH"), key=lambda object_: object_.name)


def triangle_count(objects: Iterable[bpy.types.Object]) -> int:
    count = 0
    for object_ in objects:
        count += sum(len(polygon.vertices) - 2 for polygon in object_.data.polygons)
    return count


def look_at(object_: bpy.types.Object, target: tuple[float, float, float]) -> None:
    direction = Vector(target) - object_.location
    object_.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def ensure_camera(name: str, location: tuple[float, float, float], target: tuple[float, float, float]) -> bpy.types.Object:
    camera = bpy.data.objects.get(name)
    if camera is None:
        data = bpy.data.cameras.new(name)
        camera = bpy.data.objects.new(name, data)
        bpy.context.scene.collection.objects.link(camera)
    camera.location = location
    camera.data.lens = 50
    look_at(camera, target)
    return camera


def ensure_audit_stage() -> dict[str, bpy.types.Object]:
    scene = bpy.context.scene
    scene.render.engine = "BLENDER_EEVEE"
    scene.render.resolution_x = 1280
    scene.render.resolution_y = 720
    scene.world.use_nodes = True
    background = scene.world.node_tree.nodes.get("Background")
    background.inputs["Color"].default_value = (0.003, 0.001, 0.004, 1.0)
    background.inputs["Strength"].default_value = 0.22
    ground = bpy.data.objects.get("PM_AUDIT_GROUND")
    if ground is None:
        bpy.ops.mesh.primitive_plane_add(size=80, location=(0, 0, 0))
        ground = bpy.context.object
        ground.name = "PM_AUDIT_GROUND"
        ground.data.materials.append(organic_material("PM_AUDIT_GROUND_MAT", (0.035, 0.018, 0.025), 0.83))
    for name, location, power, size in (
        ("PM_KEY", (-15, -22, 22), 2100, 12),
        ("PM_FILL", (12, -10, 10), 900, 10),
        ("PM_RIM", (18, 14, 18), 1500, 9),
    ):
        light = bpy.data.objects.get(name)
        if light is None:
            data = bpy.data.lights.new(name, "AREA")
            light = bpy.data.objects.new(name, data)
            bpy.context.scene.collection.objects.link(light)
        light.location = location
        light.data.energy = power
        light.data.shape = "DISK"
        light.data.size = size
        look_at(light, (0, 0, 5))
    trace, _ = load_primary_trace()
    mapping = trace["primary_camera_mapping"]
    primary = ensure_camera(
        "PM_AUDIT_PRIMARY",
        tuple(mapping["camera_location"]),
        tuple(mapping["camera_target"]),
    )
    primary.data.type = "ORTHO"
    primary.data.ortho_scale = float(mapping["orthographic_scale"])
    primary["pm_locked_primary_trace_camera"] = trace["trace_id"]
    return {
        # Shares the supplied image's exact pixel mapping; this must not be
        # re-aimed to hide a primary-contour mismatch.
        "primary": primary,
        "opposite": ensure_camera("PM_AUDIT_OPPOSITE", (12, 42, 15), (-0.4, 0.0, 4.7)),
        "front": ensure_camera("PM_AUDIT_FRONT", (-38, -7, 10), (-2.0, 0.0, 4.6)),
        "side": ensure_camera("PM_AUDIT_SIDE", (28, -30, 10), (0.0, 0.0, 4.5)),
        "elevated_rear": ensure_camera("PM_AUDIT_ELEVATED_REAR", (20, 34, 25), (0.0, 0.0, 4.5)),
    }


def result_path(path: Path) -> str:
    return path.as_posix()
