#!/usr/bin/env python3
"""Prepare one transparent, model-derived opposite-broadside Edit360 anchor.

The built-in image generator can return a *baked* light checkerboard even
when asked for transparency.  Edit360 converts every input to RGB, so passing
that preview through would teach the sampler that the checkerboard belongs to
the creature.  This narrow, deterministic preparer admits only that preview
form: it flood-fills the neutral high-light boundary checkerboard, removes
small disconnected foreground debris, emits an RGBA cutout and then composites
it onto Edit360's required white 576-square canvas.

Both source and output are MODEL_DERIVED research evidence.  This tool never
touches a canonical Blender source, PMMesh export, resource or world state.
"""

from __future__ import annotations

import argparse
import json
from collections import deque
from pathlib import Path
import sys

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "automodel"))

from contracts import AutomodelContractError, sha256, write_object
from stage_reference_bundle import SV3D_FRAME_RATIO, SV3D_FRAME_SIZE, _sv3d_effective_frame


CHECKERBOARD_MIN_BRIGHTNESS = 235
CHECKERBOARD_MAX_CHANNEL_SPREAD = 12
MIN_FOREGROUND_COMPONENT_PIXELS = 96


def _checkerboard_candidate(red: int, green: int, blue: int) -> bool:
    return (
        min(red, green, blue) >= CHECKERBOARD_MIN_BRIGHTNESS
        and max(red, green, blue) - min(red, green, blue) <= CHECKERBOARD_MAX_CHANNEL_SPREAD
    )


def _boundary_checkerboard_mask(source: Image.Image) -> Image.Image:
    """Return all neutral near-white pixels connected to the image boundary."""

    image = source.convert("RGB")
    width, height = image.size
    pixels = image.load()
    seen = bytearray(width * height)
    background = Image.new("L", image.size, 0)
    background_pixels = background.load()
    queue: deque[tuple[int, int]] = deque()

    def enqueue(x: int, y: int) -> None:
        index = y * width + x
        if seen[index] or not _checkerboard_candidate(*pixels[x, y]):
            return
        seen[index] = 1
        background_pixels[x, y] = 255
        queue.append((x, y))

    for x in range(width):
        enqueue(x, 0)
        enqueue(x, height - 1)
    for y in range(height):
        enqueue(0, y)
        enqueue(width - 1, y)
    while queue:
        x, y = queue.popleft()
        for next_x, next_y in (
            (x - 1, y),
            (x + 1, y),
            (x, y - 1),
            (x, y + 1),
        ):
            if 0 <= next_x < width and 0 <= next_y < height:
                enqueue(next_x, next_y)
    return background


def _foreground_components(mask: Image.Image) -> list[tuple[int, tuple[int, int, int, int]]]:
    width, height = mask.size
    pixels = mask.load()
    seen = bytearray(width * height)
    components: list[tuple[int, tuple[int, int, int, int]]] = []
    for y in range(height):
        for x in range(width):
            index = y * width + x
            if seen[index] or pixels[x, y] == 0:
                continue
            seen[index] = 1
            queue: deque[tuple[int, int]] = deque([(x, y)])
            area = 0
            left = right = x
            top = bottom = y
            while queue:
                current_x, current_y = queue.popleft()
                area += 1
                left = min(left, current_x)
                right = max(right, current_x)
                top = min(top, current_y)
                bottom = max(bottom, current_y)
                for next_x, next_y in (
                    (current_x - 1, current_y),
                    (current_x + 1, current_y),
                    (current_x, current_y - 1),
                    (current_x, current_y + 1),
                ):
                    if not (0 <= next_x < width and 0 <= next_y < height):
                        continue
                    next_index = next_y * width + next_x
                    if seen[next_index] or pixels[next_x, next_y] == 0:
                        continue
                    seen[next_index] = 1
                    queue.append((next_x, next_y))
            components.append((area, (left, top, right + 1, bottom + 1)))
    return components


def prepare(source_path: Path, output_directory: Path) -> dict[str, object]:
    if source_path.suffix.lower() != ".png" or not source_path.is_file():
        raise AutomodelContractError("Edit360 opposite anchor source must be an existing PNG")
    if output_directory.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable Edit360 anchor directory: {output_directory}")
    with Image.open(source_path) as opened:
        source = opened.convert("RGB")
    background = _boundary_checkerboard_mask(source)
    alpha = Image.new("L", source.size, 0)
    alpha_pixels = alpha.load()
    background_pixels = background.load()
    for y in range(source.height):
        for x in range(source.width):
            if background_pixels[x, y] == 0:
                alpha_pixels[x, y] = 255
    components = _foreground_components(alpha)
    if not components:
        raise AutomodelContractError("checkerboard extraction found no foreground subject")
    largest_area = max(area for area, _ in components)
    keep_threshold = max(MIN_FOREGROUND_COMPONENT_PIXELS, largest_area // 1000)
    removed_components = 0
    removed_pixels = 0
    for area, (left, top, right, bottom) in components:
        if area >= keep_threshold:
            continue
        removed_components += 1
        removed_pixels += area
        for y in range(top, bottom):
            for x in range(left, right):
                alpha_pixels[x, y] = 0
    bbox = alpha.getbbox()
    if bbox is None:
        raise AutomodelContractError("checkerboard cleanup removed every foreground pixel")
    output_directory.mkdir(parents=True)
    rgba_target = output_directory / "edit360_opposite_broadside_r01_alpha.png"
    effective_target = output_directory / "edit360_opposite_broadside_r01_effective_576.png"
    receipt_target = output_directory / "anchor_preparation_receipt.json"
    rgba = source.convert("RGBA")
    rgba.putalpha(alpha)
    rgba.save(rgba_target)
    _sv3d_effective_frame(rgba, alpha).save(effective_target)
    receipt = {
        "schema": "pale_mirror.automodel.edit360_opposite_anchor_preparation.v1",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "tier": "MODEL_DERIVED",
        "input_contract": {
            "primary_trace_remains_sole_likeness_authority": True,
            "permitted_use": "Edit360 dual-view visual experiment only",
            "geometry_or_canonical_use_prohibited": True,
            "declared_yaw_degrees": 180,
        },
        "source": {
            "filename": source_path.name,
            "sha256": sha256(source_path),
            "image_size": list(source.size),
            "input_alpha_was_opaque": True,
        },
        "checkerboard_extraction": {
            "boundary_rule": {
                "min_channel_brightness": CHECKERBOARD_MIN_BRIGHTNESS,
                "max_channel_spread": CHECKERBOARD_MAX_CHANNEL_SPREAD,
                "connectivity": 4,
            },
            "component_keep_threshold_pixels": keep_threshold,
            "removed_components": removed_components,
            "removed_pixels": removed_pixels,
            "subject_bbox_xyxy": list(bbox),
        },
        "artifacts": {
            "rgba_anchor": {"filename": rgba_target.name, "sha256": sha256(rgba_target)},
            "effective_white_576": {"filename": effective_target.name, "sha256": sha256(effective_target)},
            "effective_frame_ratio": SV3D_FRAME_RATIO,
            "effective_frame_size": SV3D_FRAME_SIZE,
        },
    }
    write_object(receipt_target, receipt)
    return receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output-directory", required=True, type=Path)
    arguments = parser.parse_args()
    print(json.dumps(prepare(arguments.source, arguments.output_directory), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
