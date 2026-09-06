#!/usr/bin/env python3
"""Build deterministic reference/model contour evidence for Harvester audits.

The tool never assigns a likeness score. It extracts the light solid-mode
Blockbench projection, aligns it uniformly to a curator-pinned subject box in
the original reference image, and emits a visual comparison for review.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from PIL import Image, ImageChops, ImageDraw, ImageFilter


def normalized_bounds(value: str) -> tuple[float, float, float, float]:
    try:
        bounds = tuple(float(item) for item in value.split(","))
    except ValueError as exc:
        raise argparse.ArgumentTypeError("bounds must contain four decimal values") from exc
    if len(bounds) != 4:
        raise argparse.ArgumentTypeError("bounds must be left,top,right,bottom")
    left, top, right, bottom = bounds
    if not (0.0 <= left < right <= 1.0 and 0.0 <= top < bottom <= 1.0):
        raise argparse.ArgumentTypeError("normalized bounds must be ordered inside 0..1")
    return left, top, right, bottom


def subject_box(
    size: tuple[int, int], bounds: Sequence[float]
) -> tuple[int, int, int, int]:
    width, height = size
    left, top, right, bottom = bounds
    return (
        round(left * width),
        round(top * height),
        round(right * width),
        round(bottom * height),
    )


def solid_projection_mask(image: Image.Image) -> Image.Image:
    """Extract the solid model from a clipped dark audit render or viewport.

    The audit capture disables grids and uses solid mode. Checkerboard pixels
    remain dark while the model is neutral grey. Small fixed UI regions are
    removed before thresholding so the viewport gizmo cannot expand the model
    bounds. The result remains visual evidence, not a semantic segmentation of
    the reference image.
    """

    rgb = image.convert("RGB")
    width, height = rgb.size
    luminance = rgb.convert("L")
    mask = luminance.point(lambda value: 255 if value >= 64 else 0, mode="1").convert("L")

    draw = ImageDraw.Draw(mask)
    draw.rectangle((0, 0, width, round(height * 0.035)), fill=0)
    draw.rectangle((round(width * 0.94), 0, width, height), fill=0)
    draw.rectangle((0, round(height * 0.965), width, height), fill=0)
    draw.rectangle(
        (round(width * 0.80), round(height * 0.76), width, height), fill=0
    )

    # Close one-pixel shading seams without turning separated limbs into one
    # artificial mass.
    return mask.filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.MinFilter(3))


def align_mask(
    mask: Image.Image,
    canvas_size: tuple[int, int],
    target: tuple[int, int, int, int],
    alignment: str,
) -> tuple[Image.Image, dict[str, object]]:
    model_box = mask.getbbox()
    if model_box is None:
        raise ValueError("solid Blockbench projection contains no light model pixels")
    cropped = mask.crop(model_box)
    model_width, model_height = cropped.size
    left, top, right, bottom = target
    target_width = right - left
    target_height = bottom - top
    scale = min(target_width / model_width, target_height / model_height)
    scaled_size = (
        max(1, round(model_width * scale)),
        max(1, round(model_height * scale)),
    )
    scaled = cropped.resize(scaled_size, Image.Resampling.NEAREST)
    x = left + (target_width - scaled_size[0]) // 2
    if alignment == "bottom_center":
        y = bottom - scaled_size[1]
    elif alignment == "center":
        y = top + (target_height - scaled_size[1]) // 2
    else:
        raise ValueError(f"unsupported contour alignment: {alignment}")

    placed = Image.new("L", canvas_size, 0)
    placed.paste(scaled, (x, y))
    return placed, {
        "source_model_bounds": list(model_box),
        "source_model_size": [model_width, model_height],
        "target_subject_bounds": list(target),
        "placed_model_bounds": [x, y, x + scaled_size[0], y + scaled_size[1]],
        "uniform_scale": round(scale, 6),
        "alignment": alignment,
        "model_aspect_ratio": round(model_width / model_height, 6),
        "subject_aspect_ratio": round(target_width / target_height, 6),
    }


def labelled_panel(image: Image.Image, label: str) -> Image.Image:
    panel = Image.new("RGB", (image.width, image.height + 34), "#111317")
    panel.paste(image.convert("RGB"), (0, 34))
    ImageDraw.Draw(panel).text((12, 10), label, fill="#f2f4f8")
    return panel


def build_comparison(
    reference_file: Path,
    silhouette_file: Path,
    bounds: Sequence[float],
    alignment: str,
    output_file: Path,
    metadata_file: Path,
) -> dict[str, object]:
    with Image.open(reference_file) as source:
        reference = source.convert("RGB")
    with Image.open(silhouette_file) as source:
        silhouette = source.convert("RGB")

    mask = solid_projection_mask(silhouette)
    target = subject_box(reference.size, bounds)
    placed, metadata = align_mask(mask, reference.size, target, alignment)

    model_panel = Image.new("RGB", reference.size, "#101317")
    model_panel.paste((238, 241, 244), mask=placed)

    overlay = reference.convert("RGBA")
    cyan = Image.new("RGBA", reference.size, (0, 220, 255, 74))
    overlay.alpha_composite(Image.composite(cyan, Image.new("RGBA", reference.size), placed))
    dilated = placed.filter(ImageFilter.MaxFilter(7))
    eroded = placed.filter(ImageFilter.MinFilter(7))
    edge = ImageChops.subtract(dilated, eroded)
    magenta = Image.new("RGBA", reference.size, (255, 35, 170, 235))
    overlay.alpha_composite(Image.composite(magenta, Image.new("RGBA", reference.size), edge))
    overlay_draw = ImageDraw.Draw(overlay)
    overlay_draw.rectangle(target, outline=(255, 214, 64, 255), width=2)

    panels = [
        labelled_panel(reference, "PINNED BASE REFERENCE"),
        labelled_panel(model_panel, "NORMALIZED SOLID MODEL PROJECTION"),
        labelled_panel(overlay.convert("RGB"), "CONTOUR OVERLAY — VISUAL REVIEW REQUIRED"),
    ]
    comparison = Image.new(
        "RGB", (sum(panel.width for panel in panels), max(panel.height for panel in panels))
    )
    x = 0
    for panel in panels:
        comparison.paste(panel, (x, 0))
        x += panel.width

    output_file.parent.mkdir(parents=True, exist_ok=True)
    comparison.save(output_file)
    metadata.update(
        {
            "schema": "pale_mirror_visuals.harvester_contour_evidence.v1",
            "reference_size": list(reference.size),
            "silhouette_capture_size": list(silhouette.size),
            "normalized_subject_bounds": list(bounds),
            "comparison_panels": ["reference", "model_projection", "overlay"],
            "automated_score": None,
            "acceptance_note": "This alignment guide is mandatory evidence; a vision-capable reviewer owns likeness and contour acceptance.",
        }
    )
    metadata_file.parent.mkdir(parents=True, exist_ok=True)
    metadata_file.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--silhouette", required=True, type=Path)
    parser.add_argument("--subject-bounds", required=True, type=normalized_bounds)
    parser.add_argument(
        "--alignment", choices=("bottom_center", "center"), default="bottom_center"
    )
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    args = parser.parse_args()
    build_comparison(
        args.reference,
        args.silhouette,
        args.subject_bounds,
        args.alignment,
        args.output,
        args.metadata,
    )


if __name__ == "__main__":
    main()
