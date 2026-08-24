#!/usr/bin/env python3
"""Render a VGGT point cloud into reproducible diagnostic orthographic views.

This intentionally consumes only the ASCII PLY emitted by the bounded pilot.
It does not construct a mesh, modify Blender, or assign any likeness result.
The generated views are diagnostic evidence for the mandatory human review.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
from typing import Any

from PIL import Image, ImageDraw


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_ascii_ply(path: Path) -> tuple[np.ndarray, np.ndarray]:
    import numpy as np

    with path.open("r", encoding="ascii") as source:
        for line_number, line in enumerate(source, start=1):
            if line.strip() == "end_header":
                break
        else:
            raise ValueError("PLY header has no end_header")
    values = np.loadtxt(path, dtype=np.float32, skiprows=line_number)
    if values.ndim != 2 or values.shape[1] != 6:
        raise ValueError("expected x y z r g b ASCII PLY records")
    points, colours = values[:, :3], values[:, 3:].clip(0, 255).astype(np.uint8)
    if len(points) < 100:
        raise ValueError("point cloud is too small to review")
    if not np.isfinite(points).all():
        raise ValueError("point cloud contains non-finite coordinates")
    return points, colours


def unit(vector: tuple[float, float, float]) -> np.ndarray:
    import numpy as np

    value = np.asarray(vector, dtype=np.float32)
    length = float(np.linalg.norm(value))
    if length == 0:
        raise ValueError("projection vector must be non-zero")
    return value / length


def render_projected_points(
    projected: np.ndarray,
    colours: np.ndarray,
    title: str,
    width: int,
    height: int,
) -> Image.Image:
    import numpy as np

    horizontal_bounds = np.quantile(projected[:, 0], (0.005, 0.995))
    vertical_bounds = np.quantile(projected[:, 1], (0.005, 0.995))
    span = max(
        float(horizontal_bounds[1] - horizontal_bounds[0]),
        float(vertical_bounds[1] - vertical_bounds[0]) * width / height,
        1e-5,
    )
    horizontal_center = float(horizontal_bounds.mean())
    vertical_center = float(vertical_bounds.mean())
    horizontal_low = horizontal_center - span / 2
    vertical_low = vertical_center - span * height / width / 2
    x = ((projected[:, 0] - horizontal_low) / span * (width - 1)).astype(np.int32)
    y = (height - 1 - (projected[:, 1] - vertical_low) / (span * height / width) * (height - 1)).astype(np.int32)
    mask = (x >= 0) & (x < width) & (y >= 0) & (y < height)
    # The point cloud lacks surfaces.  Depth sorting provides an intentionally
    # simple, repeatable painter's order solely for legibility in review.
    order = np.argsort(projected[mask, 2])
    x, y, visible_colours = x[mask][order], y[mask][order], colours[mask][order]
    canvas = np.full((height, width, 3), (18, 23, 25), dtype=np.uint8)
    for offset_x, offset_y in ((0, 0), (1, 0), (0, 1), (1, 1)):
        paint_x, paint_y = x + offset_x, y + offset_y
        paintable = (paint_x >= 0) & (paint_x < width) & (paint_y >= 0) & (paint_y < height)
        canvas[paint_y[paintable], paint_x[paintable]] = visible_colours[paintable]
    image = Image.fromarray(canvas, mode="RGB")
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, width, 30), fill=(0, 0, 0))
    draw.text((10, 8), title, fill=(235, 235, 235))
    return image


def render_projection(
    points: np.ndarray,
    colours: np.ndarray,
    right: tuple[float, float, float],
    up: tuple[float, float, float],
    forward: tuple[float, float, float],
    title: str,
    width: int,
    height: int,
) -> Image.Image:
    import numpy as np

    return render_projected_points(
        np.column_stack((points @ unit(right), points @ unit(up), points @ unit(forward))),
        colours,
        title,
        width,
        height,
    )


def camera_views_from_pilot_manifest(path: Path) -> list[tuple[str, int, list[list[float]]]]:
    value = json.loads(path.read_text(encoding="utf-8"))
    frames = value.get("input", {}).get("frames")
    poses = value.get("estimated_camera_poses")
    if not isinstance(frames, list) or not isinstance(poses, list) or len(frames) != len(poses):
        raise ValueError("pilot manifest has no frame-aligned estimated camera poses")
    selected: list[tuple[str, int, list[list[float]]]] = []
    for desired_yaw, label in ((0, "source_yaw_000_primary"), (180, "source_yaw_180_front")):
        matches = [(index, pose) for index, frame in enumerate(frames) if frame.get("yaw_degrees") == desired_yaw for pose in [poses[index]]]
        if len(matches) != 1:
            raise ValueError(f"pilot manifest has no unique {desired_yaw}-degree source camera")
        index, pose = matches[0]
        if not isinstance(pose, list) or len(pose) != 3 or any(not isinstance(row, list) or len(row) != 4 for row in pose):
            raise ValueError(f"pilot manifest has an invalid camera matrix for yaw {desired_yaw}")
        selected.append((label, index, pose))
    return selected


def render_camera_projection(
    points: np.ndarray,
    colours: np.ndarray,
    pose: list[list[float]],
    title: str,
    width: int,
    height: int,
) -> Image.Image:
    import numpy as np

    extrinsic = np.asarray(pose, dtype=np.float32)
    camera_points = points @ extrinsic[:, :3].T + extrinsic[:, 3]
    return render_projected_points(
        np.column_stack((camera_points[:, 0], -camera_points[:, 1], camera_points[:, 2])),
        colours,
        title,
        width,
        height,
    )


def run(
    input_path: Path,
    output_dir: Path,
    width: int,
    height: int,
    minimum_colour_sum: int,
    pilot_manifest_path: Path | None,
) -> dict[str, Any]:
    if width < 64 or height < 64:
        raise ValueError("review dimensions must each be at least 64px")
    if not 0 <= minimum_colour_sum <= 765:
        raise ValueError("minimum colour sum must be between 0 and 765")
    points, colours = load_ascii_ply(input_path)
    visible = colours.sum(axis=1) >= minimum_colour_sum
    points, colours = points[visible], colours[visible]
    if len(points) < 100:
        raise ValueError("colour filtering left too few points to review")
    output_dir.mkdir(parents=True, exist_ok=True)
    views = {
        "primary": ((1, 0, 0), (0, -1, 0), (0, 0, 1)),
        "opposite": ((-1, 0, 0), (0, -1, 0), (0, 0, -1)),
        "side": ((0, 0, 1), (0, -1, 0), (-1, 0, 0)),
        "elevated": ((0.707, 0, -0.707), (-0.302, 0.905, -0.302), (0.64, 0.426, 0.64)),
    }
    images: list[tuple[str, Image.Image]] = []
    if pilot_manifest_path is not None:
        for name, index, pose in camera_views_from_pilot_manifest(pilot_manifest_path):
            images.append(
                (
                    name,
                    render_camera_projection(
                        points,
                        colours,
                        pose,
                        title=f"VGGT point cloud — {name} (frame {index})",
                        width=width,
                        height=height,
                    ),
                )
            )
    for name, vectors in views.items():
        image = render_projection(points, colours, *vectors, title=f"VGGT point cloud — {name}", width=width, height=height)
        image.save(output_dir / f"{name}.png")
        images.append((name, image))
    sheet = Image.new("RGB", (width * 2, height * math.ceil(len(images) / 2)), color=(0, 0, 0))
    for index, (_name, image) in enumerate(images):
        sheet.paste(image, ((index % 2) * width, (index // 2) * height))
    sheet_path = output_dir / "point_cloud_contact_sheet.png"
    sheet.save(sheet_path)
    report = {
        "schema": "pale_mirror_visuals.vggt_point_cloud_review.v1",
        "input": {
            "file": str(input_path),
            "sha256": sha256(input_path),
            "point_count": int(len(points)),
            "minimum_colour_sum": minimum_colour_sum,
        },
        "render": {
            "width": width,
            "height": height,
            "views": [name for name, _image in images],
            "pilot_manifest": str(pilot_manifest_path) if pilot_manifest_path is not None else None,
        },
        "outputs": {"contact_sheet": {"file": sheet_path.name, "sha256": sha256(sheet_path)}},
        "contract": {
            "diagnostic_only": True,
            "does_not_construct_a_mesh": True,
            "does_not_assign_likeness": True,
            "requires_locked_primary_trace_review": True,
        },
    }
    (output_dir / "manifest.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--width", type=int, default=960)
    parser.add_argument("--height", type=int, default=720)
    parser.add_argument("--minimum-colour-sum", type=int, default=64)
    parser.add_argument("--pilot-manifest", type=Path)
    arguments = parser.parse_args()
    print(
        json.dumps(
            run(
                arguments.input,
                arguments.output_dir,
                arguments.width,
                arguments.height,
                arguments.minimum_colour_sum,
                arguments.pilot_manifest,
            ),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
