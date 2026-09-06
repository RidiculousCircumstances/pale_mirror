#!/usr/bin/env python3
"""Prepare a trace-anchored Collector turntable for a review-only VGGT pilot.

The literal source image is always frame 0.  Generated images provide only
unseen-side suggestions and are deliberately marked non-canonical.  This tool
does not invoke a reconstruction model and cannot modify Blender assets,
runtime resources, or the Collector primary trace.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Any

from PIL import Image, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_TRACE = ROOT / "pale-mirror-visuals/src/main/blender/harvester/traces/biomass_collector_primary_v03.json"
BACKGROUND = (8, 8, 8, 255)
YAW_DEGREES = tuple(range(0, 360, 30))


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain an object")
    return value


def mask_from_trace(trace: dict[str, Any]) -> Image.Image:
    source = trace.get("source")
    sampling = trace.get("sampling")
    rows = trace.get("rows")
    if not isinstance(source, dict) or not isinstance(sampling, dict) or not isinstance(rows, list):
        raise ValueError("Collector primary trace is malformed")
    dimensions = source.get("size")
    grid = sampling.get("grid_px")
    if not isinstance(dimensions, list) or len(dimensions) != 2 or not all(isinstance(value, int) for value in dimensions):
        raise ValueError("Collector primary trace lacks valid source dimensions")
    if not isinstance(grid, int) or grid <= 0:
        raise ValueError("Collector primary trace lacks a positive sampling grid")
    mask = Image.new("L", tuple(dimensions), 0)
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("y"), int) or not isinstance(row.get("runs"), list):
            raise ValueError("Collector primary trace row is malformed")
        for run in row["runs"]:
            if not isinstance(run, list) or len(run) != 2 or not all(isinstance(value, int) for value in run):
                raise ValueError("Collector primary trace run is malformed")
            left, right = run
            if right <= left:
                raise ValueError("Collector primary trace contains an empty run")
            for pixel_y in range(row["y"], row["y"] + grid):
                for pixel_x in range(left, right):
                    if 0 <= pixel_x < mask.width and 0 <= pixel_y < mask.height:
                        mask.putpixel((pixel_x, pixel_y), 255)
    if mask.getbbox() is None:
        raise ValueError("Collector primary trace has no foreground")
    return mask


def generated_subject_mask(panel: Image.Image) -> Image.Image:
    """Select the generated crimson subject while removing contact-sheet lines.

    This is a framing convenience, not a segmentation authority.  The result
    remains unreviewed until a person accepts its anatomy and frame consistency.
    """

    rgb = panel.convert("RGB")
    selected = [
        255 if red >= 20 and red >= green * 1.18 and red >= blue * 1.10 and red - green >= 5 else 0
        for red, green, blue in rgb.get_flattened_data()
    ]
    mask = Image.new("L", panel.size)
    mask.putdata(selected)
    return mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(3))


def normalize_subject(image: Image.Image, mask: Image.Image, panel_size: int) -> Image.Image:
    bounds = mask.getbbox()
    if bounds is None:
        raise ValueError("Turntable panel has no retained subject pixels")
    left, top, right, bottom = bounds
    side = max(right - left, bottom - top)
    margin = max(8, round(side * 0.08))
    crop_box = (
        max(0, left - margin),
        max(0, top - margin),
        min(image.width, right + margin),
        min(image.height, bottom + margin),
    )
    crop = image.convert("RGBA").crop(crop_box)
    crop.putalpha(mask.crop(crop_box))
    scale = (panel_size * 0.84) / max(crop.width, crop.height)
    resized = crop.resize(
        (max(1, round(crop.width * scale)), max(1, round(crop.height * scale))),
        Image.Resampling.LANCZOS,
    )
    panel = Image.new("RGBA", (panel_size, panel_size), BACKGROUND)
    panel.alpha_composite(resized, ((panel_size - resized.width) // 2, (panel_size - resized.height) // 2))
    return panel


def crop_grid_panel(sheet: Image.Image, columns: int, rows: int, index: int) -> tuple[Image.Image, int, int]:
    if sheet.width % columns or sheet.height % rows:
        raise ValueError("Generated contact sheet dimensions must divide exactly into the declared grid")
    if not 0 <= index < columns * rows:
        raise ValueError("Generated contact sheet panel index is out of range")
    panel_width, panel_height = sheet.width // columns, sheet.height // rows
    column, row = index % columns, index // columns
    return (
        sheet.crop((column * panel_width, row * panel_height, (column + 1) * panel_width, (row + 1) * panel_height)),
        column,
        row,
    )


def build_review_sheet(frames: list[Path], output: Path, panel_size: int, columns: int) -> None:
    rows = (len(frames) + columns - 1) // columns
    sheet = Image.new("RGBA", (columns * panel_size, rows * panel_size), BACKGROUND)
    for index, frame_path in enumerate(frames):
        with Image.open(frame_path) as source:
            panel = source.convert("RGBA")
        sheet.alpha_composite(panel, ((index % columns) * panel_size, (index // columns) * panel_size))
    sheet.convert("RGB").save(output)


def prepare(
    literal_anchor: Path,
    generated_sheet: Path,
    output_dir: Path,
    trace_path: Path = DEFAULT_TRACE,
    panel_size: int = 512,
    columns: int = 4,
    rows: int = 3,
) -> dict[str, Any]:
    if panel_size < 64:
        raise ValueError("panel size must be at least 64 pixels")
    if columns * rows != len(YAW_DEGREES):
        raise ValueError("The Collector pilot requires exactly twelve 30-degree frames")
    trace = load_json(trace_path)
    trace_source = trace.get("source")
    if not isinstance(trace_source, dict):
        raise ValueError("Collector primary trace lacks a source declaration")
    if literal_anchor.name != trace_source.get("file") or sha256(literal_anchor) != trace_source.get("sha256"):
        raise ValueError("Literal anchor differs from the pinned Collector primary reference")
    with Image.open(literal_anchor) as source:
        literal_image = source.convert("RGBA")
    if list(literal_image.size) != trace_source.get("size"):
        raise ValueError("Literal anchor dimensions differ from the pinned Collector primary trace")
    literal_mask = mask_from_trace(trace)
    if literal_mask.size != literal_image.size:
        raise ValueError("Collector literal anchor and primary trace have incompatible dimensions")
    with Image.open(generated_sheet) as source:
        generated = source.convert("RGBA")

    output_dir.mkdir(parents=True, exist_ok=True)
    frames_dir = output_dir / "frames"
    frames_dir.mkdir(parents=True, exist_ok=True)
    rendered: list[Path] = []
    frame_records: list[dict[str, Any]] = []

    literal_frame = frames_dir / "frame_00_yaw_000_literal_anchor.png"
    normalize_subject(literal_image, literal_mask, panel_size).convert("RGB").save(literal_frame)
    rendered.append(literal_frame)
    frame_records.append(
        {
            "index": 0,
            "yaw_degrees": 0,
            "file": str(literal_frame.relative_to(output_dir)),
            "sha256": sha256(literal_frame),
            "source": "literal_primary_trace_masked",
            "authority": "sole_likeness_anchor",
        }
    )

    for generated_index, yaw in enumerate(YAW_DEGREES[1:], start=1):
        panel, column, row = crop_grid_panel(generated, columns, rows, generated_index)
        frame_path = frames_dir / f"frame_{generated_index:02d}_yaw_{yaw:03d}_generated.png"
        normalize_subject(panel, generated_subject_mask(panel), panel_size).convert("RGB").save(frame_path)
        rendered.append(frame_path)
        frame_records.append(
            {
                "index": generated_index,
                "yaw_degrees": yaw,
                "file": str(frame_path.relative_to(output_dir)),
                "sha256": sha256(frame_path),
                "source": "generated_turntable_panel_unreviewed",
                "source_panel": {"column": column, "row": row, "index": generated_index},
                "authority": "secondary_depth_suggestion_only",
            }
        )

    review_sheet = output_dir / "review_sheet.png"
    build_review_sheet(rendered, review_sheet, panel_size, columns)
    manifest = {
        "schema": "pale_mirror_visuals.harvester_vggt_turntable_input.v1",
        "asset_id": "biomass_collector",
        "status": "generated_unreviewed_noncanonical",
        "frame_count": len(frame_records),
        "frame_order": "yaw increases clockwise only as a generated-image convention; it is not camera calibration",
        "source": {
            "literal_anchor": {"file": str(literal_anchor), "sha256": sha256(literal_anchor)},
            "primary_trace": {"file": str(trace_path), "sha256": sha256(trace_path), "trace_id": trace.get("trace_id")},
            "generated_contact_sheet": {"file": str(generated_sheet), "sha256": sha256(generated_sheet), "grid": [columns, rows]},
        },
        "discarded_generated_panel": {
            "index": 0,
            "column": 0,
            "row": 0,
            "reason": "The literal source image replaces the generated 0-degree panel.",
        },
        "frames": frame_records,
        "review_sheet": {"file": review_sheet.name, "sha256": sha256(review_sheet)},
        "acceptance_contract": {
            "literal_primary_is_sole_likeness_authority": True,
            "generated_frames_require_manual_consistency_review": True,
            "generated_frames_are_not_camera_calibration": True,
            "vggt_output_is_noncanonical_nonexportable": True,
            "vggt_may_only_guide_existing_surface_retopology_after_literal_trace_audit": True,
        },
        "manual_rejection_gates": [
            "Any changed dorsal-sac count, limb count, pose, tail attachment, or head-drape identity rejects the frame.",
            "A frame that is not the same neutral creature on the same ground line rejects the frame.",
            "A fused VGGT proxy without a locked-primary trace pass cannot guide the Collector Master.",
        ],
    }
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--literal-anchor", required=True, type=Path)
    parser.add_argument("--generated-sheet", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--trace", type=Path, default=DEFAULT_TRACE)
    parser.add_argument("--panel-size", type=int, default=512)
    arguments = parser.parse_args()
    print(
        json.dumps(
            prepare(
                arguments.literal_anchor,
                arguments.generated_sheet,
                arguments.output_dir,
                arguments.trace,
                arguments.panel_size,
            ),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
