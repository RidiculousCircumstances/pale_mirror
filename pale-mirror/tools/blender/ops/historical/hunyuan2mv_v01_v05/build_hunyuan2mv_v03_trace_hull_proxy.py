"""Build a closed trace-led visual-hull proxy with Hunyuan v03 depth hints.

Unlike the rejected source-fill experiment, this operation never inserts a
flat silhouette patch.  It reconstructs one closed, variable-depth volume from
the literal primary trace.  The pinned Hunyuan v03 mesh is sampled only for
hidden-side thickness, then hidden as a non-rendered reference.  The output is
still a fixed, non-exportable research proxy: it cannot open the canonical
Collector source, alter runtime data or become an accepted mesh automatically.
"""

from __future__ import annotations

from collections import deque
from pathlib import Path
import math
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(1, str(Path(__file__).resolve().parent.parents[1]))
from import_hunyuan2mv_candidate import candidate_spec  # noqa: E402
from common import ASSET_ID, asset_id, load_primary_trace, organic_material  # noqa: E402

import bpy
from mathutils import Vector
from mathutils.bvhtree import BVHTree


_CANDIDATE_ID = "hunyuan2mv_v03_primary_front"
_MINIMUM_Y = 0.04
_SAMPLE_STRIDE = 4
_RAW_DEPTH_SCALE = 0.26
_MIN_DEPTH = 0.16
_MAX_DEPTH = 7.5
_HULL_NAME = "collector_hunyuan2mv_v03_trace_depth_hull"


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    spec = candidate_spec(payload)
    if spec.candidate_id != _CANDIDATE_ID:
        raise ValueError("Only the pinned Hunyuan v03 proposal may provide trace-hull depth hints.")
    if not spec.review_source.is_file():
        raise FileNotFoundError("Import the fixed Hunyuan v03 proposal before building its trace hull.")
    if bpy.data.filepath != str(spec.review_source):
        bpy.ops.wm.open_mainfile(filepath=str(spec.review_source))
    scene = bpy.context.scene
    _require_raw_review_scene(scene, spec)
    if scene.get("pm_trace_depth_hull_proxy") is not None:
        raise ValueError("Trace-depth hull already exists; re-import the immutable v03 proposal before another trial.")

    proposal = bpy.data.collections.get(spec.proposal_collection)
    if proposal is None:
        raise ValueError("Pinned Hunyuan v03 proposal collection is missing.")
    meshes = [
        object_
        for object_ in proposal.all_objects
        if object_.type == "MESH" and object_.get("pm_candidate_id") == _CANDIDATE_ID
    ]
    if len(meshes) != 1:
        raise ValueError("Pinned Hunyuan v03 proposal must contain exactly one raw candidate mesh.")
    raw = meshes[0]
    root = bpy.data.objects.get(spec.proposal_root)
    if root is None:
        raise ValueError("Pinned Hunyuan v03 proposal root is missing.")

    trace, trace_hash = load_primary_trace()
    _shift_behind_primary_trace(root, raw)
    cells, crop_left, crop_top, grid = _trace_cells(trace)
    distances = _inside_boundary_distances(cells)
    depth_hints = _sample_depth_hints(raw, cells, crop_left, crop_top, grid, trace)
    depth_by_cell = _depth_field(cells, distances, depth_hints, grid, trace)
    hull = _build_closed_hull(proposal, cells, crop_left, crop_top, grid, trace, depth_by_cell)
    hull.name = _HULL_NAME
    hull.data.name = f"{_HULL_NAME}_mesh"
    hull.data.materials.append(organic_material("PM_HUNYUAN2MV_V03_TRACE_HULL", (0.25, 0.010, 0.028), 0.46))
    for polygon in hull.data.polygons:
        polygon.use_smooth = True
    hull["pm_candidate_id"] = _CANDIDATE_ID
    hull["pm_candidate_stage"] = spec.stage
    hull["pm_export_exclude"] = True
    hull["pm_trace_depth_hull_proxy"] = True
    hull["pm_trace_id"] = trace["trace_id"]
    hull["pm_trace_sha256"] = trace_hash
    hull["pm_depth_rule"] = "literal trace silhouette with locally sampled hidden-side Hunyuan v03 depth"
    raw.hide_render = True
    raw.hide_set(True)
    raw["pm_hunyuan_reference_only"] = True
    scene["pm_trace_depth_hull_proxy"] = {
        "candidate_id": _CANDIDATE_ID,
        "trace_id": trace["trace_id"],
        "trace_sha256": trace_hash,
        "sample_stride": _SAMPLE_STRIDE,
        "raw_depth_scale": _RAW_DEPTH_SCALE,
        "runtime_export_forbidden": True,
    }
    scene["pm_review_scope"] = (
        "Trace-led closed visual-hull proxy with Hunyuan v03 depth hints only; semantic anatomy, acceptance and runtime export remain forbidden"
    )
    scene["pm_runtime_export_forbidden"] = True
    bpy.ops.wm.save_as_mainfile(filepath=str(spec.review_source))
    return {
        "asset_id": ASSET_ID,
        "candidate_id": _CANDIDATE_ID,
        "trace_id": trace["trace_id"],
        "hull": hull.name,
        "hull_triangles": _triangles(hull),
        "raw_depth_samples": len(depth_hints),
        "raw_depth_hits": sum(1 for value in depth_hints.values() if value is not None),
        "minimum_world_y": _minimum_world_y(hull),
        "runtime_export_forbidden": True,
    }


def _require_raw_review_scene(scene: bpy.types.Scene, spec) -> None:
    if (
        scene.get("pm_asset_id") != ASSET_ID
        or scene.get("pm_candidate_id") != _CANDIDATE_ID
        or scene.get("pm_candidate_stage") != spec.stage
        or scene.get("pm_runtime_export_forbidden") is not True
    ):
        raise ValueError("Current scene is not the pinned non-exportable Hunyuan v03 review scene.")
    if scene.get("pm_trace_conformal_proxy") is not None:
        raise ValueError("Re-import the immutable v03 proposal; the rejected fill proxy cannot seed a depth hull.")


def _shift_behind_primary_trace(root: bpy.types.Object, raw: bpy.types.Object) -> None:
    minimum_y = _minimum_world_y(raw)
    root.location.y += _MINIMUM_Y - minimum_y
    bpy.context.view_layer.update()
    if _minimum_world_y(raw) < _MINIMUM_Y - 0.0001:
        raise ValueError("Hunyuan v03 proposal could not be placed behind the locked primary trace.")


def _trace_cells(trace: dict[str, object]) -> tuple[set[tuple[int, int]], int, int, int]:
    crop_left, crop_top, _right, _bottom = (int(value) for value in trace["sampling"]["crop"])
    grid = int(trace["sampling"]["grid_px"])
    cells: set[tuple[int, int]] = set()
    for row in trace["rows"]:
        row_index = (int(row["y"]) - crop_top) // grid
        for left, right in row["runs"]:
            for column in range((int(left) - crop_left) // grid, (int(right) - crop_left) // grid):
                cells.add((column, row_index))
    if not cells:
        raise ValueError("Pinned primary trace contains no occupied cells.")
    return cells, crop_left, crop_top, grid


def _inside_boundary_distances(cells: set[tuple[int, int]]) -> dict[tuple[int, int], int]:
    distances: dict[tuple[int, int], int] = {}
    queue: deque[tuple[int, int]] = deque()
    for cell in sorted(cells):
        if any(neighbour not in cells for neighbour in _neighbours(cell)):
            distances[cell] = 0
            queue.append(cell)
    while queue:
        current = queue.popleft()
        following_distance = distances[current] + 1
        for neighbour in _neighbours(current):
            if neighbour in cells and neighbour not in distances:
                distances[neighbour] = following_distance
                queue.append(neighbour)
    if len(distances) != len(cells):
        raise ValueError("Pinned trace contains an unreachable interior cell.")
    return distances


def _neighbours(cell: tuple[int, int]) -> tuple[tuple[int, int], ...]:
    column, row = cell
    return ((column - 1, row), (column + 1, row), (column, row - 1), (column, row + 1))


def _sample_depth_hints(
    raw: bpy.types.Object,
    cells: set[tuple[int, int]],
    crop_left: int,
    crop_top: int,
    grid: int,
    trace: dict[str, object],
) -> dict[tuple[int, int], float | None]:
    vertices = [raw.matrix_world @ vertex.co for vertex in raw.data.vertices]
    faces = [tuple(polygon.vertices) for polygon in raw.data.polygons if len(polygon.vertices) >= 3]
    tree = BVHTree.FromPolygons(vertices, faces, all_triangles=False)
    buckets: dict[tuple[int, int], tuple[int, int]] = {}
    for cell in sorted(cells):
        key = (cell[0] // _SAMPLE_STRIDE, cell[1] // _SAMPLE_STRIDE)
        existing = buckets.get(key)
        if existing is None or cell < existing:
            buckets[key] = cell
    return {
        bucket: _ray_thickness(tree, *_cell_centre(cell, crop_left, crop_top, grid, trace))
        for bucket, cell in sorted(buckets.items())
    }


def _ray_thickness(tree: BVHTree, x: float, z: float) -> float | None:
    origin = Vector((x, 40.0, z))
    direction = Vector((0.0, -1.0, 0.0))
    hits: list[float] = []
    for _ in range(24):
        location, _normal, _face, _distance = tree.ray_cast(origin, direction, 80.0)
        if location is None:
            break
        hits.append(location.y)
        origin = location + direction * 0.001
    if len(hits) < 2:
        return None
    return max(hits) - min(hits)


def _cell_centre(
    cell: tuple[int, int], crop_left: int, crop_top: int, grid: int, trace: dict[str, object]
) -> tuple[float, float]:
    column, row = cell
    return _world_xz(crop_left + (column + 0.5) * grid, crop_top + (row + 0.5) * grid, trace)


def _world_xz(pixel_x: float, pixel_y: float, trace: dict[str, object]) -> tuple[float, float]:
    mapping = trace["primary_camera_mapping"]
    origin_x, origin_y = (int(value) for value in mapping["origin_pixel"])
    units = float(mapping["world_units_per_pixel"])
    return (pixel_x - origin_x) * units, (origin_y - pixel_y) * units


def _depth_field(
    cells: set[tuple[int, int]],
    distances: dict[tuple[int, int], int],
    hints: dict[tuple[int, int], float | None],
    grid: int,
    trace: dict[str, object],
) -> dict[tuple[int, int], float]:
    units = float(trace["primary_camera_mapping"]["world_units_per_pixel"])
    result: dict[tuple[int, int], float] = {}
    for cell in cells:
        fallback = max(_MIN_DEPTH, min(3.9, (distances[cell] + 1) * grid * units * 1.45))
        raw = hints.get((cell[0] // _SAMPLE_STRIDE, cell[1] // _SAMPLE_STRIDE))
        result[cell] = max(fallback, min(_MAX_DEPTH, raw * _RAW_DEPTH_SCALE) if raw is not None else 0.0)
    return result


def _build_closed_hull(
    target: bpy.types.Collection,
    cells: set[tuple[int, int]],
    crop_left: int,
    crop_top: int,
    grid: int,
    trace: dict[str, object],
    depths: dict[tuple[int, int], float],
) -> bpy.types.Object:
    corners: dict[tuple[int, int], tuple[float, float, float]] = {}
    for column, row in cells:
        for corner in ((column, row), (column + 1, row), (column + 1, row + 1), (column, row + 1)):
            if corner in corners:
                continue
            nearby = [
                depths[neighbour]
                for neighbour in ((corner[0], corner[1]), (corner[0] - 1, corner[1]), (corner[0], corner[1] - 1), (corner[0] - 1, corner[1] - 1))
                if neighbour in depths
            ]
            depth = sum(nearby) / len(nearby)
            x, z = _world_xz(crop_left + corner[0] * grid, crop_top + corner[1] * grid, trace)
            # The visible skin rises gently into the body but never crosses the
            # locked guide plane; a primary view remains trace-exact while side
            # views receive a genuine rounded thickness rather than a card.
            front_y = _MINIMUM_Y + 0.42 * max(0.0, 1.0 - min(1.0, depth / 2.8))
            corners[corner] = (x, front_y, depth)
    vertices: list[tuple[float, float, float]] = []
    front_indices: dict[tuple[int, int], int] = {}
    back_indices: dict[tuple[int, int], int] = {}
    for corner in sorted(corners):
        x, front_y, depth = corners[corner]
        front_indices[corner] = len(vertices)
        vertices.append((x, front_y, _corner_z(corner, crop_top, grid, trace)))
        back_indices[corner] = len(vertices)
        vertices.append((x, front_y + depth, _corner_z(corner, crop_top, grid, trace)))
    faces: list[tuple[int, int, int, int]] = []
    for column, row in sorted(cells):
        corners_for_cell = ((column, row), (column + 1, row), (column + 1, row + 1), (column, row + 1))
        faces.append(tuple(front_indices[corner] for corner in reversed(corners_for_cell)))
        faces.append(tuple(back_indices[corner] for corner in corners_for_cell))
        _append_boundary_faces(cells, (column, row), corners_for_cell, front_indices, back_indices, faces)
    mesh = bpy.data.meshes.new(f"{_HULL_NAME}_mesh")
    mesh.from_pydata(vertices, [], faces)
    mesh.validate(clean_customdata=True)
    mesh.update()
    hull = bpy.data.objects.new(_HULL_NAME, mesh)
    target.objects.link(hull)
    return hull


def _corner_z(corner: tuple[int, int], crop_top: int, grid: int, trace: dict[str, object]) -> float:
    _x, z = _world_xz(0.0, crop_top + corner[1] * grid, trace)
    return z


def _append_boundary_faces(
    cells: set[tuple[int, int]],
    cell: tuple[int, int],
    corners: tuple[tuple[int, int], ...],
    front: dict[tuple[int, int], int],
    back: dict[tuple[int, int], int],
    faces: list[tuple[int, int, int, int]],
) -> None:
    # Edge order is top/right/bottom/left.  The direction is stable and only
    # matters for clean outward normals in the diagnostic volume.
    column, row = cell
    edges = (
        ((column, row - 1), corners[0], corners[1]),
        ((column + 1, row), corners[1], corners[2]),
        ((column, row + 1), corners[2], corners[3]),
        ((column - 1, row), corners[3], corners[0]),
    )
    for neighbour, first, second in edges:
        if neighbour not in cells:
            faces.append((front[first], front[second], back[second], back[first]))


def _minimum_world_y(object_: bpy.types.Object) -> float:
    if not object_.data.vertices:
        raise ValueError(f"Object has no mesh vertices: {object_.name}")
    return min((object_.matrix_world @ vertex.co).y for vertex in object_.data.vertices)


def _triangles(object_: bpy.types.Object) -> int:
    return sum(len(polygon.vertices) - 2 for polygon in object_.data.polygons)


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "candidate_id": _CANDIDATE_ID}))
