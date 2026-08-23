#!/usr/bin/env python3
"""Prepare a fixed four-view Hunyuan3D-2mv trial for Biomass Collector.

The supplied Collector frame remains the sole likeness authority.  This tool
uses the generated four-view sheet only as explicitly labelled diagnostic
conditioning for a *private, unaccepted* shape proposal.  It never changes a
primary trace or a canonical Blender source.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageFilter


ASSET_ID = "biomass_collector"
BAKEOFF_ID = "collector_multiview_bakeoff_v01"
CANDIDATE_ID = "hunyuan2mv_v01"
PRIMARY_FILE = "01_harvester_biomass_collector.jpg"
PRIMARY_SHA256 = "788cfe68f11e82a4e280de837090d3ebd2209cb598afbb8016946b1ea33a05fa"
TURNTABLE_FILE = "biomass_collector_turntable_v01.png"
TURNTABLE_SHA256 = "7d62fda968ecb2dc8b2c7aa157f077f77e9cd16f7eb031e54dbb900e72ba168e"
TURNTABLE_SIZE = (1254, 1254)
PANEL_SIZE = 627
OUTPUT_SIZE = 768

# The diagnostic sheet is not a calibrated photo turntable.  These labels are
# an explicit first-pass hypothesis for Hunyuan3D-2mv's canonical view slots,
# kept in the manifest so a failed candidate cannot be mistaken for a formal
# multi-camera reconstruction.
PANELS = (
    ("front", (0, PANEL_SIZE, PANEL_SIZE, PANEL_SIZE * 2), "lower-left direct leading-mantle view"),
    ("left", (0, 0, PANEL_SIZE, PANEL_SIZE), "upper-left near three-quarter view"),
    ("back", (PANEL_SIZE, PANEL_SIZE, PANEL_SIZE * 2, PANEL_SIZE * 2), "lower-right far three-quarter proxy"),
    ("right", (PANEL_SIZE, 0, PANEL_SIZE * 2, PANEL_SIZE), "upper-right broadside view"),
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def subject_mask(panel: Image.Image) -> Image.Image:
    """Keep the red organism, not the black studio, divider lines or shadow."""
    rgb = panel.convert("RGB")
    pixels: list[int] = []
    for red, green, blue in rgb.get_flattened_data():
        selected = red >= 18 and red >= green * 1.22 and red >= blue * 1.12 and red - green >= 6
        pixels.append(255 if selected else 0)
    mask = Image.new("L", panel.size)
    mask.putdata(pixels)
    # Fill only lighting pinholes; the modest close/open sequence cannot join
    # the large negative spaces under the walking arches.
    return mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(3))


def centered_rgba(panel: Image.Image) -> Image.Image:
    mask = subject_mask(panel)
    bounds = mask.getbbox()
    if bounds is None:
        raise ValueError("A diagnostic panel has no retained subject pixels.")
    left, top, right, bottom = bounds
    width = right - left
    height = bottom - top
    side = max(width, height)
    margin = max(16, round(side * 0.08))
    left = max(0, left - margin)
    top = max(0, top - margin)
    right = min(panel.width, right + margin)
    bottom = min(panel.height, bottom + margin)
    crop = panel.crop((left, top, right, bottom)).convert("RGBA")
    alpha = mask.crop((left, top, right, bottom))
    crop.putalpha(alpha)
    scale = (OUTPUT_SIZE * 0.86) / max(crop.width, crop.height)
    resized = crop.resize((max(1, round(crop.width * scale)), max(1, round(crop.height * scale))), Image.Resampling.LANCZOS)
    output = Image.new("RGBA", (OUTPUT_SIZE, OUTPUT_SIZE), (0, 0, 0, 0))
    output.alpha_composite(resized, ((OUTPUT_SIZE - resized.width) // 2, (OUTPUT_SIZE - resized.height) // 2))
    return output


def prepare(primary: Path, turntable: Path, output: Path) -> dict[str, object]:
    if primary.name != PRIMARY_FILE or sha256(primary) != PRIMARY_SHA256:
        raise ValueError("The supplied primary reference does not match the pinned Collector authority.")
    if turntable.name != TURNTABLE_FILE or sha256(turntable) != TURNTABLE_SHA256:
        raise ValueError("The diagnostic four-view sheet does not match its pinned provenance.")
    with Image.open(turntable) as source:
        if source.size != TURNTABLE_SIZE:
            raise ValueError("The diagnostic four-view sheet has an unexpected size.")
        output.mkdir(parents=True, exist_ok=True)
        views: dict[str, dict[str, object]] = {}
        for slot, box, description in PANELS:
            image = centered_rgba(source.crop(box))
            destination = output / f"{slot}.png"
            image.save(destination)
            views[slot] = {
                "file": destination.name,
                "sha256": sha256(destination),
                "source_panel": list(box),
                "role_hypothesis": description,
                "size": list(image.size),
                "alpha_pixels": image.getchannel("A").histogram()[255],
            }
    manifest = {
        "schema": "pale_mirror.hunyuan2mv_input.v1",
        "bakeoff_id": BAKEOFF_ID,
        "candidate_id": CANDIDATE_ID,
        "asset_id": ASSET_ID,
        "authority": "The original primary remains likeness authority; these are generated diagnostic views for a non-canonical volume proposal.",
        "inputs_are_calibrated_photos": False,
        "source": {
            "primary": {"file": PRIMARY_FILE, "sha256": PRIMARY_SHA256},
            "diagnostic_turntable": {"file": TURNTABLE_FILE, "sha256": TURNTABLE_SHA256, "size": list(TURNTABLE_SIZE)},
        },
        "views": views,
        "model_contract": {
            "model": "tencent/Hunyuan3D-2mv",
            "subfolder": "hunyuan3d-dit-v2-mv",
            "shape_only": True,
            "low_vram_cpu_offload": True,
        },
        "runtime_export_forbidden": True,
    }
    manifest_path = output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--primary", type=Path, required=True)
    parser.add_argument("--turntable", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    print(json.dumps(prepare(arguments.primary, arguments.turntable, arguments.output), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
