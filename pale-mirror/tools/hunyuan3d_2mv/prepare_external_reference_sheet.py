#!/usr/bin/env python3
"""Stage one selected 2x4 external reference sheet for Hunyuan3D-2mv.

The tool writes an ignored, hash-pinned noncanonical input bundle only.  It
does not change the Collector source image, trace, Blender, PMMesh or runtime.
The fixed cells document a human visual choice: source-local broadside,
tail-end, opposite broadside, then head-end.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from typing import Final

from PIL import Image, ImageFilter


ROOT: Final = Path(__file__).resolve().parents[2]
ASSET_ID: Final = "biomass_collector"
CANDIDATE_ID: Final = "hunyuan2mv_external_reference_sheet_r02"
BAKEOFF_ID: Final = "collector_multiview_bakeoff_external_reference_sheet_r02"
GRID: Final = (4, 2)
INSET_PX: Final = 4
OUTPUT_SIZE: Final = 768

# Hunyuan's pinned processor expects front, clockwise 90, back, clockwise 270.
# The input sheet is visually reviewed before this mapping; selection is not
# inferred from pixels or model metadata.
SLOTS: Final = {
    "front": {
        "cell": (0, 0),
        "role": "source-local broadside; mantle at image-left and tail at image-right",
    },
    "left": {
        "cell": (2, 1),
        "role": "tail end-on volume hypothesis",
    },
    "back": {
        "cell": (3, 0),
        "role": "opposite broadside; mantle at image-right and tail at image-left",
    },
    "right": {
        "cell": (2, 0),
        "role": "head end-on volume hypothesis",
    },
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require_fresh_build_output(path: Path) -> Path:
    resolved = path.resolve()
    build_root = (ROOT / "build" / "automodel").resolve()
    if not resolved.is_relative_to(build_root):
        raise ValueError("external Hunyuan inputs must be written below build/automodel")
    if resolved.exists():
        raise ValueError(f"refusing to overwrite existing external-sheet input: {resolved}")
    return resolved


def crop_cell(sheet: Image.Image, column: int, row: int) -> Image.Image:
    columns, rows = GRID
    if not 0 <= column < columns or not 0 <= row < rows:
        raise ValueError("selected source cell lies outside the fixed 2x4 sheet")
    if sheet.width < columns * (INSET_PX * 2 + 16) or sheet.height < rows * (INSET_PX * 2 + 16):
        raise ValueError("reference sheet is too small for the fixed 2x4 selection")
    left = round(column * sheet.width / columns) + INSET_PX
    top = round(row * sheet.height / rows) + INSET_PX
    right = round((column + 1) * sheet.width / columns) - INSET_PX
    bottom = round((row + 1) * sheet.height / rows) - INSET_PX
    if right <= left or bottom <= top:
        raise ValueError("reference sheet inset leaves an empty cell")
    return sheet.crop((left, top, right, bottom)).convert("RGB")


def subject_mask(panel: Image.Image) -> Image.Image:
    """Remove the panel's uniform studio background without painting pixels."""

    rgb = panel.convert("RGB")
    selected = [
        255
        if (
            (red >= 18 and red >= green * 1.16 and red >= blue * 1.08 and red - green >= 3)
            or max(red, green, blue) <= 110
        )
        else 0
        for red, green, blue in rgb.get_flattened_data()
    ]
    mask = Image.new("L", panel.size)
    mask.putdata(selected)
    return mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(3))


def centered_rgba(panel: Image.Image, mask: Image.Image) -> Image.Image:
    bounds = mask.getbbox()
    if bounds is None:
        raise ValueError("selected external-sheet cell has no retained subject pixels")
    left, top, right, bottom = bounds
    side = max(right - left, bottom - top)
    margin = max(16, round(side * 0.08))
    crop_box = (max(0, left - margin), max(0, top - margin), min(panel.width, right + margin), min(panel.height, bottom + margin))
    crop = panel.convert("RGBA").crop(crop_box)
    crop.putalpha(mask.crop(crop_box))
    scale = (OUTPUT_SIZE * 0.86) / max(crop.width, crop.height)
    resized = crop.resize((max(1, round(crop.width * scale)), max(1, round(crop.height * scale))), Image.Resampling.LANCZOS)
    output = Image.new("RGBA", (OUTPUT_SIZE, OUTPUT_SIZE), (0, 0, 0, 0))
    output.alpha_composite(resized, ((OUTPUT_SIZE - resized.width) // 2, (OUTPUT_SIZE - resized.height) // 2))
    return output


def entry(image: Path, raw: Path, *, slot: str) -> dict[str, object]:
    alpha = Image.open(image).getchannel("A")
    return {
        "file": image.name,
        "sha256": sha256(image),
        "raw_panel": {"file": raw.name, "sha256": sha256(raw)},
        "source": "external_reference_sheet_panel_model_derived",
        "role_hypothesis": SLOTS[slot]["role"],
        "source_cell": list(SLOTS[slot]["cell"]),
        "size": list(Image.open(image).size),
        "alpha_pixels": alpha.histogram()[255],
    }


def prepare(reference_sheet: Path, output: Path) -> dict[str, object]:
    if not reference_sheet.is_file():
        raise ValueError(f"external reference sheet is missing: {reference_sheet}")
    destination = require_fresh_build_output(output)
    with Image.open(reference_sheet) as source:
        sheet = source.convert("RGB")
    destination.mkdir(parents=True)
    views: dict[str, dict[str, object]] = {}
    for slot, spec in SLOTS.items():
        column, row = spec["cell"]
        raw = crop_cell(sheet, column, row)
        raw_path = destination / f"raw_{slot}.png"
        raw.save(raw_path)
        normalized = centered_rgba(raw, subject_mask(raw))
        image_path = destination / f"{slot}.png"
        normalized.save(image_path)
        views[slot] = entry(image_path, raw_path, slot=slot)
        if int(views[slot]["alpha_pixels"]) < 10_000:
            raise ValueError(f"external-sheet {slot} view has too little retained subject area")
    manifest = {
        "schema": "pale_mirror.hunyuan2mv_input.v1",
        "bakeoff_id": BAKEOFF_ID,
        "candidate_id": CANDIDATE_ID,
        "asset_id": ASSET_ID,
        "authority": "All four panels are MODEL_DERIVED external references selected by a human for a noncanonical volume proposal; they cannot establish Collector likeness.",
        "inputs_are_calibrated_photos": False,
        "primary_is_model_conditioning": False,
        "source": {
            "external_reference_sheet": {"file": str(reference_sheet.resolve()), "sha256": sha256(reference_sheet), "size": list(sheet.size)},
            "grid": {"columns": GRID[0], "rows": GRID[1], "cell_inset_px": INSET_PX},
            "selection": {slot: {"cell": list(spec["cell"]), "role_hypothesis": spec["role"]} for slot, spec in SLOTS.items()},
            "evidence_tier": "MODEL_DERIVED",
        },
        "views": views,
        "model_contract": {"model": "tencent/Hunyuan3D-2mv", "subfolder": "hunyuan3d-dit-v2-mv", "shape_only": True},
        "runtime_export_forbidden": True,
    }
    (destination / "manifest.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-sheet", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    print(json.dumps(prepare(arguments.reference_sheet, arguments.output), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
