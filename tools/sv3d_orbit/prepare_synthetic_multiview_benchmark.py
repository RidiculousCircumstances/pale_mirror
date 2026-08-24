#!/usr/bin/env python3
"""Prepare three known 3D scenes for the SV3D fast-orbit benchmark.

Each scene is a tiny deterministic software-rendered asset. The literal 0°
primary is the only image supplied to SV3D; the remaining 21 known renders and
semantic masks are held back as research-only ground truth. This is an
adapter-quality benchmark, not creature evidence and never an asset source.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
from math import cos, pi, sin, sqrt
from pathlib import Path
import sys

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from contracts import AutomodelContractError, sha256, write_object  # noqa: E402
from stage_reference_bundle import stage_reference_bundle  # noqa: E402


CANVAS = 576
# Keep every asymmetric endpoint inside the source frame: clipping a cable,
# rod or load would turn the qualification corpus into a weaker 2D fixture.
RENDER_SCALE = 78
ELEVATION_DEGREES = 10.0
YAWS = tuple(range(0, 361, 18))
SUITE_SCHEMA = "pale_mirror.automodel.synthetic_sv3d_multiview_suite.v1"
Vec3 = tuple[float, float, float]


@dataclass(frozen=True)
class Component:
    """One convex coloured semantic object in a synthetic scene."""

    role: str
    colour: tuple[int, int, int]
    vertices: tuple[Vec3, ...]
    faces: tuple[tuple[int, ...], ...]


@dataclass(frozen=True)
class Scene:
    identifier: str
    description: str
    components: tuple[Component, ...]


def _add(left: Vec3, right: Vec3) -> Vec3:
    return tuple(a + b for a, b in zip(left, right, strict=True))  # type: ignore[return-value]


def _subtract(left: Vec3, right: Vec3) -> Vec3:
    return tuple(a - b for a, b in zip(left, right, strict=True))  # type: ignore[return-value]


def _scale(vector: Vec3, amount: float) -> Vec3:
    return tuple(amount * value for value in vector)  # type: ignore[return-value]


def _cross(left: Vec3, right: Vec3) -> Vec3:
    return (
        left[1] * right[2] - left[2] * right[1],
        left[2] * right[0] - left[0] * right[2],
        left[0] * right[1] - left[1] * right[0],
    )


def _dot(left: Vec3, right: Vec3) -> float:
    return sum(a * b for a, b in zip(left, right, strict=True))


def _normalise(vector: Vec3) -> Vec3:
    magnitude = sqrt(_dot(vector, vector))
    if magnitude <= 1e-8:
        raise ValueError("synthetic geometry has a zero-length direction")
    return _scale(vector, 1.0 / magnitude)


def _mean_depth(vertices: tuple[Vec3, ...]) -> float:
    return sum(vertex[2] for vertex in vertices) / len(vertices)


def _box(role: str, colour: tuple[int, int, int], center: Vec3, size: Vec3) -> Component:
    cx, cy, cz = center
    sx, sy, sz = (value / 2.0 for value in size)
    vertices = (
        (cx - sx, cy - sy, cz - sz), (cx + sx, cy - sy, cz - sz), (cx + sx, cy + sy, cz - sz), (cx - sx, cy + sy, cz - sz),
        (cx - sx, cy - sy, cz + sz), (cx + sx, cy - sy, cz + sz), (cx + sx, cy + sy, cz + sz), (cx - sx, cy + sy, cz + sz),
    )
    return Component(role, colour, vertices, ((0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (3, 2, 6, 7), (1, 5, 6, 2), (0, 3, 7, 4)))


def _frustum(
    role: str,
    colour: tuple[int, int, int],
    *,
    y_bottom: float,
    y_top: float,
    bottom_size: tuple[float, float],
    top_size: tuple[float, float],
    center: tuple[float, float] = (0.0, 0.0),
) -> Component:
    cx, cz = center
    bx, bz = (value / 2.0 for value in bottom_size)
    tx, tz = (value / 2.0 for value in top_size)
    vertices = (
        (cx - bx, y_bottom, cz - bz), (cx + bx, y_bottom, cz - bz), (cx + bx, y_bottom, cz + bz), (cx - bx, y_bottom, cz + bz),
        (cx - tx, y_top, cz - tz), (cx + tx, y_top, cz - tz), (cx + tx, y_top, cz + tz), (cx - tx, y_top, cz + tz),
    )
    return Component(role, colour, vertices, ((0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (3, 2, 6, 7), (1, 5, 6, 2), (0, 3, 7, 4)))


def _cylinder(role: str, colour: tuple[int, int, int], start: Vec3, end: Vec3, radius: float, *, sides: int = 12) -> Component:
    if sides < 3:
        raise ValueError("synthetic cylinder needs at least three sides")
    axis = _normalise(_subtract(end, start))
    helper: Vec3 = (0.0, 1.0, 0.0) if abs(_dot(axis, (0.0, 1.0, 0.0))) <= 0.92 else (1.0, 0.0, 0.0)
    first_perpendicular = _normalise(_cross(axis, helper))
    second_perpendicular = _normalise(_cross(axis, first_perpendicular))
    vertices: list[Vec3] = []
    for endpoint in (start, end):
        for index in range(sides):
            angle = (2.0 * pi * index) / sides
            offset = _add(_scale(first_perpendicular, radius * cos(angle)), _scale(second_perpendicular, radius * sin(angle)))
            vertices.append(_add(endpoint, offset))
    faces: list[tuple[int, ...]] = [tuple(range(sides - 1, -1, -1)), tuple(range(sides, 2 * sides))]
    for index in range(sides):
        next_index = (index + 1) % sides
        faces.append((index, next_index, sides + next_index, sides + index))
    return Component(role, colour, tuple(vertices), tuple(faces))


def _scene_signal_tower() -> Scene:
    return Scene(
        "signal_tower",
        "Broad asymmetric tower with a side wheel, diagonal vane and offset arm.",
        (
            _box("core", (35, 80, 91), (0.0, -1.55, 0.0), (3.45, 0.55, 2.55)),
            _frustum("core", (54, 131, 145), y_bottom=-1.35, y_top=1.55, bottom_size=(3.0, 2.20), top_size=(1.80, 1.38)),
            _frustum("core", (67, 161, 169), y_bottom=1.55, y_top=2.70, bottom_size=(1.65, 1.20), top_size=(1.10, 0.82)),
            _box("core", (237, 175, 63), (0.0, 3.12, 0.0), (0.98, 0.70, 0.78)),
            _cylinder("marker", (126, 193, 101), (-1.14, -0.48, -0.78), (-2.10, -0.48, -0.78), 0.62, sides=18),
            _cylinder("critical", (204, 95, 57), (0.80, 0.18, -0.25), (2.52, -0.04, -0.50), 0.26, sides=8),
            _box("critical", (108, 69, 158), (1.12, 1.05, -0.20), (0.34, 1.62, 0.72)),
        ),
    )


def _scene_needle_fork() -> Scene:
    return Scene(
        "needle_fork",
        "Three thin, differently angled rods with coloured end masses around an asymmetric core.",
        (
            _frustum("core", (51, 112, 139), y_bottom=-1.65, y_top=0.85, bottom_size=(3.25, 2.60), top_size=(1.80, 1.65)),
            _box("core", (45, 75, 105), (0.0, -1.73, 0.0), (3.55, 0.32, 2.78)),
            _cylinder("critical", (238, 165, 49), (0.20, 0.48, 0.12), (2.42, 2.18, 0.48), 0.105, sides=8),
            _cylinder("critical", (147, 68, 172), (0.10, 0.35, -0.24), (1.82, -0.10, 1.86), 0.105, sides=8),
            _cylinder("critical", (206, 79, 82), (-0.25, 0.58, 0.18), (-1.82, 1.48, -1.42), 0.105, sides=8),
            _cylinder("critical", (240, 195, 72), (2.42, 2.18, 0.48), (2.78, 2.46, 0.55), 0.29, sides=12),
            _cylinder("critical", (162, 94, 187), (1.82, -0.10, 1.86), (2.12, -0.18, 2.25), 0.29, sides=12),
            _cylinder("critical", (217, 97, 96), (-1.82, 1.48, -1.42), (-2.08, 1.64, -1.69), 0.29, sides=12),
            _cylinder("marker", (121, 202, 127), (-1.02, -0.72, 0.66), (-1.82, -0.72, 0.66), 0.43, sides=16),
        ),
    )


def _scene_gantry_hook() -> Scene:
    return Scene(
        "gantry_hook",
        "Offset gantry with a long beam, a thin hanging cable and a separated load.",
        (
            _box("core", (39, 92, 107), (0.0, -1.62, 0.0), (4.25, 0.42, 2.80)),
            _box("core", (39, 92, 107), (-0.85, 0.08, 0.0), (0.82, 3.12, 0.82)),
            _box("core", (39, 92, 107), (-0.85, 1.65, 0.0), (2.35, 0.54, 1.12)),
            _cylinder("critical", (230, 162, 52), (-0.38, 1.48, 0.0), (2.88, 1.48, 0.36), 0.16, sides=8),
            _cylinder("critical", (115, 70, 169), (2.88, 1.48, 0.36), (2.88, -0.78, 0.36), 0.055, sides=8),
            _box("critical", (205, 85, 62), (2.88, -1.10, 0.36), (0.72, 0.56, 0.72)),
        ),
    )


SCENES = (_scene_signal_tower(), _scene_needle_fork(), _scene_gantry_hook())


def _camera_space(vertex: Vec3, yaw_degrees: float) -> Vec3:
    yaw = yaw_degrees * pi / 180.0
    elevation = ELEVATION_DEGREES * pi / 180.0
    x, y, z = vertex
    yaw_x, yaw_y, yaw_z = cos(yaw) * x + sin(yaw) * z, y, -sin(yaw) * x + cos(yaw) * z
    return yaw_x, cos(elevation) * yaw_y - sin(elevation) * yaw_z, sin(elevation) * yaw_y + cos(elevation) * yaw_z


def _render(scene: Scene, yaw_degrees: int) -> tuple[Image.Image, dict[str, Image.Image]]:
    multiplier = 2
    dimensions = CANVAS * multiplier
    image = Image.new("RGB", (dimensions, dimensions), (255, 255, 255))
    visible_role = Image.new("L", image.size, 0)
    role_codes = {"core": 1, "critical": 2, "marker": 3}
    faces: list[tuple[float, Component, tuple[Vec3, ...]]] = []
    for component in scene.components:
        transformed = tuple(_camera_space(vertex, float(yaw_degrees)) for vertex in component.vertices)
        for face in component.faces:
            face_vertices = tuple(transformed[index] for index in face)
            faces.append((_mean_depth(face_vertices), component, face_vertices))
    draw = ImageDraw.Draw(image)
    role_draw = ImageDraw.Draw(visible_role)
    # The camera looks along +Z, so far faces are painted first.
    for _, component, vertices in sorted(faces, key=lambda value: value[0]):
        projected = tuple(
            (int(round(dimensions / 2.0 + point[0] * RENDER_SCALE * multiplier)), int(round(dimensions / 2.0 - point[1] * RENDER_SCALE * multiplier)))
            for point in vertices
        )
        normal = _normalise(_cross(_subtract(vertices[1], vertices[0]), _subtract(vertices[2], vertices[0])))
        light = max(0.24, min(1.0, 0.64 + 0.28 * normal[2]))
        colour = tuple(int(round(channel * light)) for channel in component.colour)
        draw.polygon(projected, fill=colour)
        role_draw.polygon(projected, fill=role_codes[component.role])
    full_mask = visible_role.point(lambda value: 255 if value > 0 else 0)
    resized = image.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)
    masks = {"full": full_mask.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)}
    for role in ("core", "critical"):
        code = role_codes[role]
        masks[role] = visible_role.point(lambda value, code=code: 255 if value == code else 0).resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)
    return resized, masks


def _trace(mask: Image.Image) -> dict[str, object]:
    pixels = mask.load()
    rows: list[dict[str, object]] = []
    for y in range(CANVAS):
        runs: list[list[int]] = []
        start: int | None = None
        for x in range(CANVAS):
            foreground = pixels[x, y] >= 128
            if foreground and start is None:
                start = x
            elif not foreground and start is not None:
                runs.append([start, x])
                start = None
        if start is not None:
            runs.append([start, CANVAS])
        if runs:
            rows.append({"y": y, "runs": runs})
    return {
        "schema": "pale_mirror.automodel.synthetic_trace.v1",
        "source": {"size": [CANVAS, CANVAS]},
        "sampling": {"grid_px": 1},
        "rows": rows,
        "purpose": "synthetic known-geometry SV3D benchmark only; not creature likeness evidence",
    }


def _transparent_primary(render: Image.Image, mask: Image.Image) -> Image.Image:
    output = render.convert("RGBA")
    output.putalpha(mask.point(lambda value: 255 if value >= 128 else 0))
    return output


def _record(path: Path, root: Path) -> dict[str, str]:
    return {"file": path.relative_to(root).as_posix(), "sha256": sha256(path)}


def _write_scene(scene: Scene, suite_root: Path, repository_root: Path) -> dict[str, object]:
    run = suite_root / scene.identifier
    run.mkdir(parents=True)
    source = run / "source"
    ground_truth = run / "ground_truth"
    source.mkdir()
    for directory in ("colour", "full_mask", "core_mask", "critical_mask"):
        (ground_truth / directory).mkdir(parents=True)
    frames: list[dict[str, object]] = []
    primary_render, primary_masks = _render(scene, 0)
    primary_path = source / f"{scene.identifier}_primary.png"
    trace_path = source / f"{scene.identifier}_trace.json"
    _transparent_primary(primary_render, primary_masks["full"]).save(primary_path)
    write_object(trace_path, _trace(primary_masks["full"]))
    for yaw in YAWS:
        colour, masks = _render(scene, yaw)
        colour_path = ground_truth / "colour" / f"frame_{yaw:03d}.png"
        full_path = ground_truth / "full_mask" / f"frame_{yaw:03d}.png"
        core_path = ground_truth / "core_mask" / f"frame_{yaw:03d}.png"
        critical_path = ground_truth / "critical_mask" / f"frame_{yaw:03d}.png"
        colour.save(colour_path)
        masks["full"].save(full_path)
        masks["core"].save(core_path)
        masks["critical"].save(critical_path)
        frames.append(
            {
                "yaw_degrees": yaw,
                "colour": _record(colour_path, run),
                "full_mask": _record(full_path, run),
                "core_mask": _record(core_path, run),
                "critical_mask": _record(critical_path, run),
            }
        )
    stage_reference_bundle(
        f"synthetic_sv3d_{scene.identifier}",
        primary_path,
        trace_path,
        run,
        trace_masked_conditioning=True,
        repository_root=repository_root,
    )
    spec = {
        "schema": "pale_mirror.automodel.synthetic_sv3d_scene.v1",
        "scene_id": scene.identifier,
        "description": scene.description,
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "camera": {"elevation_degrees": ELEVATION_DEGREES, "yaw_degrees": list(YAWS), "projection": "orthographic"},
        "primary": _record(primary_path, run),
        "primary_trace": _record(trace_path, run),
        "reference_bundle": _record(run / "reference_bundle.json", run),
        "ground_truth_frames": frames,
        "semantic_roles": {
            "core": "dominant support/body volume; excluded from exposed critical-detail recall",
            "critical": "thin or offset structural detail that must persist rather than smear or disappear",
        },
        "purpose": "known-geometry SV3D view-consistency benchmark only; no creature or canonical-art authority",
    }
    write_object(run / "synthetic_scene.json", spec)
    return {
        "scene_id": scene.identifier,
        "run_directory": run.relative_to(repository_root).as_posix(),
        "synthetic_scene": _record(run / "synthetic_scene.json", suite_root),
        "reference_bundle": _record(run / "reference_bundle.json", suite_root),
    }


def prepare(output_directory: Path, *, repository_root: Path = ROOT) -> dict[str, object]:
    output = output_directory.resolve()
    try:
        relative = output.relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("synthetic benchmark must remain inside the repository") from exc
    if not relative.startswith("build/automodel/"):
        raise AutomodelContractError("synthetic benchmark must remain under build/automodel")
    if output.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable synthetic benchmark: {output}")
    output.mkdir(parents=True)
    scenes = [_write_scene(scene, output, repository_root) for scene in SCENES]
    suite = {
        "schema": SUITE_SCHEMA,
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "adapter_id": "sv3d_p_orbit_fast_fp16_20",
        "camera": {"elevation_degrees": ELEVATION_DEGREES, "yaw_step_degrees": 18, "frame_count": len(YAWS)},
        "scenes": scenes,
        "purpose": "three-scene known-geometry SV3D benchmark; throughput and view consistency only",
        "does_not_qualify_creature_likeness": True,
        "does_not_promote_generated_views": True,
    }
    write_object(output / "synthetic_multiview_suite.json", suite)
    return suite


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-directory", type=Path, required=True)
    arguments = parser.parse_args()
    print(json.dumps(prepare(arguments.output_directory), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
