#!/usr/bin/env python3
"""Prepare one transparent front-three-quarter Edit360 preview input.

The built-in image generator can bake a light neutral checkerboard into an
opaque RGB preview even when transparent output was requested.  This narrow,
deterministic preparer removes only boundary-connected neutral checkerboard
pixels, retains the remaining model-derived subject as RGBA, and creates a
white 576-square review frame.  It never infers, repaints, extends or creates
subject pixels.

Its output is offline, noncanonical ``MODEL_DERIVED`` evidence for one
Edit360 visual-preview run.  It cannot become a ViewSet, geometry, Blender,
PMMesh, runtime or gameplay input.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from contracts import AutomodelContractError, require_relative_build_path, sha256, write_object
from stage_reference_bundle import SV3D_FRAME_RATIO, SV3D_FRAME_SIZE, _sv3d_effective_frame
from prepare_opposite_anchor import _boundary_checkerboard_mask, _foreground_components, MIN_FOREGROUND_COMPONENT_PIXELS


PROFILE = "generated_front_left_three_quarter_r01"


def _relative_build(path: Path, label: str) -> str:
    try:
        relative = path.resolve().relative_to(ROOT.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"{label} must remain in the repository build directory") from exc
    return require_relative_build_path(relative, label)


def prepare(source_path: Path, output_directory: Path) -> dict[str, object]:
    """Extract a generated front only from a baked boundary checkerboard."""

    if source_path.suffix.lower() != ".png" or not source_path.is_file():
        raise AutomodelContractError("generated Edit360 front source must be an existing PNG")
    _relative_build(output_directory, "generated_front.output_directory")
    if output_directory.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable generated-front directory: {output_directory}")
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
    rgba_target = output_directory / "generated_front_left_three_quarter_r01_alpha.png"
    effective_target = output_directory / "generated_front_left_three_quarter_r01_effective_576.png"
    receipt_target = output_directory / "generated_front_preparation_receipt.json"
    rgba = source.convert("RGBA")
    rgba.putalpha(alpha)
    rgba.save(rgba_target)
    _sv3d_effective_frame(rgba, alpha).save(effective_target)
    receipt = {
        "schema": "pale_mirror.automodel.edit360_generated_front_preparation.v1",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "tier": "MODEL_DERIVED",
        "profile": PROFILE,
        "input_contract": {
            "primary_trace_remains_sole_likeness_authority": True,
            "permitted_use": "Edit360 dual-view visual experiment only",
            "geometry_or_canonical_use_prohibited": True,
            "camera_intent": "low front-left three-quarter",
        },
        "source": {
            "file": _relative_build(source_path, "generated_front.source"),
            "sha256": sha256(source_path),
            "image_size": list(source.size),
            "input_alpha_was_opaque": True,
        },
        "checkerboard_extraction": {
            "method": "boundary_connected_neutral_checkerboard_only",
            "component_keep_threshold_pixels": keep_threshold,
            "removed_components": removed_components,
            "removed_pixels": removed_pixels,
            "subject_bbox_xyxy": list(bbox),
        },
        "artifacts": {
            "rgba_front": {"file": _relative_build(rgba_target, "generated_front.rgba"), "sha256": sha256(rgba_target)},
            "effective_white_576": {
                "file": _relative_build(effective_target, "generated_front.effective"),
                "sha256": sha256(effective_target),
            },
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
