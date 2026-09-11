#!/usr/bin/env python3
"""Build a review-only normalized canonical Harvester turntable contact sheet.

The resulting sheet is human-audit evidence. Hunyuan receives the individual,
hash-pinned views named in the same manifest, never crops from this sheet.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageFilter


PANEL_SIZE = 640
GAP = 8
BACKGROUND = (20, 20, 20, 255)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def source_trace_mask(trace_path: Path, expected_hash: str) -> Image.Image:
    if sha256(trace_path) != expected_hash:
        raise ValueError("The literal source trace differs from the canonical turntable manifest.")
    trace = json.loads(trace_path.read_text(encoding="utf-8"))
    source = trace.get("source")
    sampling = trace.get("sampling")
    rows = trace.get("rows")
    if not isinstance(source, dict) or not isinstance(sampling, dict) or not isinstance(rows, list):
        raise ValueError("The literal source trace is malformed.")
    size = source.get("size")
    grid = sampling.get("grid_px")
    if not isinstance(size, list) or len(size) != 2 or not all(isinstance(value, int) for value in size):
        raise ValueError("The literal source trace has no valid size.")
    if not isinstance(grid, int) or grid < 1:
        raise ValueError("The literal source trace has no valid sample grid.")
    mask = Image.new("L", tuple(size), 0)
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("y"), int) or not isinstance(row.get("runs"), list):
            raise ValueError("The literal source trace row is malformed.")
        for left, right in row["runs"]:
            if not isinstance(left, int) or not isinstance(right, int) or right <= left:
                raise ValueError("The literal source trace run is malformed.")
            for y in range(row["y"], row["y"] + grid):
                for x in range(left, right):
                    if 0 <= x < mask.width and 0 <= y < mask.height:
                        mask.putpixel((x, y), 255)
    return mask


def generated_subject_mask(panel: Image.Image) -> Image.Image:
    rgb = panel.convert("RGB")
    selected = [
        255 if red >= 18 and red >= green * 1.22 and red >= blue * 1.12 and red - green >= 6 else 0
        for red, green, blue in rgb.get_flattened_data()
    ]
    mask = Image.new("L", panel.size)
    mask.putdata(selected)
    return mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(3))


def normalize_panel(image: Image.Image, mask: Image.Image) -> Image.Image:
    bounds = mask.getbbox()
    if bounds is None:
        raise ValueError("A canonical turntable panel has no retained subject pixels.")
    left, top, right, bottom = bounds
    side = max(right - left, bottom - top)
    margin = max(16, round(side * 0.08))
    crop_box = (
        max(0, left - margin),
        max(0, top - margin),
        min(image.width, right + margin),
        min(image.height, bottom + margin),
    )
    crop = image.convert("RGBA").crop(crop_box)
    crop.putalpha(mask.crop(crop_box))
    scale = (PANEL_SIZE * 0.84) / max(crop.width, crop.height)
    resized = crop.resize(
        (max(1, round(crop.width * scale)), max(1, round(crop.height * scale))),
        Image.Resampling.LANCZOS,
    )
    panel = Image.new("RGBA", (PANEL_SIZE, PANEL_SIZE), BACKGROUND)
    panel.alpha_composite(resized, ((PANEL_SIZE - resized.width) // 2, (PANEL_SIZE - resized.height) // 2))
    return panel


def build_sheet(manifest_path: Path, references_root: Path, output: Path) -> str:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schema") != "pale_mirror_visuals.harvester_canonical_turntable.v1":
        raise ValueError("Not a canonical Harvester turntable manifest.")
    primary = manifest.get("source_primary")
    panels = manifest.get("panels")
    if not isinstance(primary, dict) or not isinstance(panels, list) or len(panels) != 6:
        raise ValueError("The canonical turntable must declare one primary and six ordered panels.")
    primary_path = references_root / str(primary.get("file"))
    if not primary_path.is_file() or sha256(primary_path) != primary.get("sha256"):
        raise ValueError("The pinned literal primary reference is unavailable or changed.")
    trace_path = manifest_path.parent.parent / "traces" / str(primary.get("trace", {}).get("file"))
    trace_mask = source_trace_mask(trace_path, str(primary.get("trace", {}).get("sha256")))
    with Image.open(primary_path) as source:
        primary_image = source.convert("RGBA")
    if primary_image.size != trace_mask.size:
        raise ValueError("The literal primary and its trace have incompatible dimensions.")

    rendered: list[Image.Image] = []
    for panel in panels:
        if panel.get("source") == "literal_primary":
            rendered.append(normalize_panel(primary_image, trace_mask))
            continue
        file_name = panel.get("file")
        expected_hash = panel.get("sha256")
        source_path = manifest_path.parent / str(file_name)
        if not source_path.is_file() or sha256(source_path) != expected_hash:
            raise ValueError(f"Generated canonical panel is unavailable or changed: {file_name}")
        with Image.open(source_path) as source:
            image = source.convert("RGBA")
        rendered.append(normalize_panel(image, generated_subject_mask(image)))

    sheet = Image.new("RGBA", (PANEL_SIZE * 3 + GAP * 2, PANEL_SIZE * 2 + GAP), BACKGROUND)
    for index, panel in enumerate(rendered):
        column, row = index % 3, index // 3
        sheet.alpha_composite(panel, (column * (PANEL_SIZE + GAP), row * (PANEL_SIZE + GAP)))
    output.parent.mkdir(parents=True, exist_ok=True)
    sheet.convert("RGB").save(output)
    return sha256(output)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--references-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    print(build_sheet(arguments.manifest, arguments.references_root, arguments.output))


if __name__ == "__main__":
    main()
