#!/usr/bin/env python3
"""Build dense, editable 256px Minecraft texture sheets from the art studies."""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter


ROOT = Path(sys.argv[1]).resolve()
SOURCE_ROOT = ROOT / "pale-mirror-visuals" / "src" / "main"
ASSET_ROOT = SOURCE_ROOT / "resources" / "assets" / "pale_mirror_visuals"
NAMES = ("biomass_collector", "crusher_stalker", "scythe_stalker")
SHEET_SIZE = 256


def number(name: str, tile_x: int, tile_y: int, salt: str) -> int:
    value = f"{name}:{tile_x}:{tile_y}:{salt}".encode()
    return int.from_bytes(hashlib.blake2s(value, digest_size=4).digest(), "big")


def make_sheet(name: str) -> Image.Image:
    source = Image.open(
        SOURCE_ROOT / "blockbench" / "harvester" / "material_studies" / f"{name}_v2_source.png"
    ).convert("RGB")
    source_width, source_height = source.size
    # One continuous crop is intentional. The old grid of independently
    # sampled 32px tiles made a cuboid creature read as a literal checkerboard
    # at normal viewing distance. Fine cuboids already carry the SRP-style
    # segmentation; the texture should connect them into the same tissue.
    crop_size = min(source_width, source_height, 1080)
    x = number(name, 0, 0, "x") % max(1, source_width - crop_size + 1)
    y = number(name, 0, 0, "y") % max(1, source_height - crop_size + 1)
    sheet = source.crop((x, y, x + crop_size, y + crop_size)).resize(
        (SHEET_SIZE, SHEET_SIZE), Image.Resampling.LANCZOS
    )
    sheet = Image.blend(sheet, sheet.filter(ImageFilter.GaussianBlur(radius=.55)), .28)
    sheet = ImageEnhance.Contrast(sheet).enhance(.91)
    sheet = ImageEnhance.Color(sheet).enhance(.97)
    return sheet.filter(ImageFilter.UnsharpMask(radius=.7, percent=70, threshold=5))


for creature in NAMES:
    result = make_sheet(creature)
    targets = (
        ASSET_ROOT / "textures" / "entity" / "harvester" / f"{creature}.png",
        SOURCE_ROOT / "blockbench" / "harvester" / "textures" / f"{creature}.png",
    )
    for target in targets:
        target.parent.mkdir(parents=True, exist_ok=True)
        result.save(target, format="PNG", optimize=True)
