#!/usr/bin/env python3
"""Generate a versioned source-pixel primary trace for a Harvester reference.

The trace is deliberately a compact run-length representation of the supplied
image's own silhouette mask. It is a construction input for the locked Blender
primary camera, not an automated likeness score and not a generated design.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from collections import deque

from PIL import Image, ImageFilter


ASSET_ID = "biomass_collector"
TRACE_ID = "biomass_collector_primary_v03"
PINNED_FILE = "01_harvester_biomass_collector.jpg"
PINNED_SHA256 = "788cfe68f11e82a4e280de837090d3ebd2209cb598afbb8016946b1ea33a05fa"
PINNED_SIZE = (1280, 720)
GRID_PX = 2
CROP = (0, 80, 1280, 576)
SEED_PIXEL = (260, 180)
MIN_SUBJECT_COMPONENT_CELLS = 500
# These thin silhouette fragments are too dark to survive the conservative
# colour segmentation. They were traced directly from the supplied frame in
# source-pixel coordinates, not inferred from the old candidate or turntable.
MANUAL_SOURCE_PIXEL_STROKES = (
    ((6, 514), (28, 486), (50, 454), (72, 418), (94, 381), (116, 345)),
    ((24, 520), (49, 490), (76, 452), (101, 412), (126, 370)),
    ((1036, 332), (1090, 316), (1154, 316), (1218, 331), (1278, 354)),
    ((1050, 350), (1112, 340), (1178, 349), (1234, 372), (1278, 401)),
    ((1082, 423), (1144, 451), (1211, 491), (1278, 531)),
)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _mask(reference: Image.Image) -> Image.Image:
    """Select the red organic subject without deriving any new anatomy."""
    rgb = reference.convert("RGB")
    values: list[int] = []
    for y in range(rgb.height):
        for x in range(rgb.width):
            red, green, blue = rgb.getpixel((x, y))
            selected = (
                CROP[0] <= x < CROP[2]
                and CROP[1] <= y < CROP[3]
                and red > 20
                and red > green * 1.28
                and red > blue * 1.13
                and red - green > 11
            )
            values.append(255 if selected else 0)
    mask = Image.new("L", rgb.size)
    mask.putdata(values)
    # Small closed lighting gaps belong to the same visible surface; the two
    # passes never bridge a real multi-pixel void between legs.
    return mask.filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MaxFilter(7)).filter(ImageFilter.MinFilter(5))


def _sample_component(mask: Image.Image) -> set[tuple[int, int]]:
    left, top, right, bottom = CROP
    width = (right - left) // GRID_PX
    height = (bottom - top) // GRID_PX
    cells: set[tuple[int, int]] = set()
    for row in range(height):
        for column in range(width):
            x0 = left + column * GRID_PX
            y0 = top + row * GRID_PX
            if mask.crop((x0, y0, x0 + GRID_PX, y0 + GRID_PX)).getbbox() is not None:
                cells.add((column, row))
    seed = ((SEED_PIXEL[0] - left) // GRID_PX, (SEED_PIXEL[1] - top) // GRID_PX)
    if seed not in cells:
        raise ValueError("pinned Collector seed is not part of the extracted subject mask")
    components: list[set[tuple[int, int]]] = []
    while cells:
        component_seed = next(iter(cells))
        component = {component_seed}
        cells.remove(component_seed)
        queue: deque[tuple[int, int]] = deque([component_seed])
        while queue:
            column, row = queue.popleft()
            for neighbour in ((column - 1, row), (column + 1, row), (column, row - 1), (column, row + 1)):
                if neighbour in cells:
                    cells.remove(neighbour)
                    component.add(neighbour)
                    queue.append(neighbour)
        components.append(component)
    seed_component = next(component for component in components if seed in component)
    # Dark separations in the supplied frame split three rear legs and a heavy
    # tail fragment from the main connected silhouette. Their own source-pixel
    # components are retained only above this explicit area floor; tiny red
    # foreground debris and loose tail noise stay excluded.
    selected = set(seed_component)
    for component in components:
        if component is not seed_component and len(component) >= MIN_SUBJECT_COMPONENT_CELLS:
            selected.update(component)
    selected.update(_manual_stroke_cells())
    return selected


def _manual_stroke_cells() -> set[tuple[int, int]]:
    """Rasterise explicit source-pixel filaments into the trace's 2px grid."""
    left, top, right, bottom = CROP
    selected: set[tuple[int, int]] = set()
    for path in MANUAL_SOURCE_PIXEL_STROKES:
        for start, end in zip(path, path[1:]):
            distance = max(abs(end[0] - start[0]), abs(end[1] - start[1]))
            for step in range(distance + 1):
                ratio = step / max(1, distance)
                x = round(start[0] + (end[0] - start[0]) * ratio)
                y = round(start[1] + (end[1] - start[1]) * ratio)
                if left <= x < right and top <= y < bottom:
                    selected.add(((x - left) // GRID_PX, (y - top) // GRID_PX))
    return selected


def _rows(component: set[tuple[int, int]]) -> list[dict[str, object]]:
    left, top, _, _ = CROP
    by_row: dict[int, list[int]] = {}
    for column, row in component:
        by_row.setdefault(row, []).append(column)
    rows: list[dict[str, object]] = []
    for row in sorted(by_row):
        columns = sorted(by_row[row])
        ranges: list[list[int]] = []
        start = previous = columns[0]
        for column in columns[1:]:
            if column == previous + 1:
                previous = column
                continue
            ranges.append([left + start * GRID_PX, left + (previous + 1) * GRID_PX])
            start = previous = column
        ranges.append([left + start * GRID_PX, left + (previous + 1) * GRID_PX])
        rows.append({"y": top + row * GRID_PX, "runs": ranges})
    return rows


def build(reference_file: Path) -> dict[str, object]:
    if reference_file.name != PINNED_FILE:
        raise ValueError(f"trace input must be the pinned {PINNED_FILE}")
    if _sha256(reference_file) != PINNED_SHA256:
        raise ValueError("trace input SHA-256 does not match the pinned supplied reference")
    with Image.open(reference_file) as source:
        if source.size != PINNED_SIZE:
            raise ValueError(f"trace input size must be {PINNED_SIZE}")
        component = _sample_component(_mask(source))
    return {
        "schema": "pale_mirror_visuals.harvester_primary_trace.v1",
        "asset_id": ASSET_ID,
        "trace_id": TRACE_ID,
        "authority": "literal source-pixel silhouette trace from the supplied primary reference; no generated or diagnostic image may change it",
        "source": {"file": PINNED_FILE, "sha256": PINNED_SHA256, "size": list(PINNED_SIZE)},
        "sampling": {
            "grid_px": GRID_PX,
            "crop": list(CROP),
            "seed_pixel": list(SEED_PIXEL),
            "foreground_rule": "red>20; red>green*1.28; red>blue*1.13; red-green>11; close 7px twice then open 5px; keep the seed component plus detached subject components of at least 500 sampled cells",
            "manual_source_pixel_strokes": [list(path) for path in MANUAL_SOURCE_PIXEL_STROKES],
        },
        "primary_camera_mapping": {
            "world_units_per_pixel": 0.025,
            "origin_pixel": [640, 560],
            # Blender defines an orthographic camera's scale as its *frame
            # width*.  The locked source map is 1280×720 at 0.025 world units
            # per pixel, so the correct 16:9 width is 32 rather than 18.
            "orthographic_scale": 32.0,
            "camera_location": [0.0, -60.0, 5.0],
            "camera_target": [0.0, 0.0, 5.0],
        },
        "rows": _rows(component),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    encoded = json.dumps(build(arguments.reference), indent=2, sort_keys=True) + "\n"
    if arguments.check:
        if not arguments.output.is_file() or arguments.output.read_text(encoding="utf-8") != encoded:
            raise SystemExit("primary trace is missing or differs from its pinned source image")
        return
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(encoded, encoding="utf-8")


if __name__ == "__main__":
    main()
