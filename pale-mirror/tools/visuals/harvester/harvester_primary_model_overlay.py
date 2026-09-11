#!/usr/bin/env python3
"""Build direct, non-fitted primary-contour evidence for a Blender cage.

The locked primary camera and trace already share the supplied reference's
1280×720 source-pixel map.  This tool consequently forbids any translation,
uniform fitting or rescaling.  It visualises the literal traced silhouette,
the actual rendered model silhouette, and both disagreement regions for a
human reviewer; it deliberately produces no likeness score.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter


PINNED_SHA256 = "788cfe68f11e82a4e280de837090d3ebd2209cb598afbb8016946b1ea33a05fa"
PINNED_SIZE = (1280, 720)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def binary_mask(path: Path) -> Image.Image:
    image = Image.open(path).convert("RGBA")
    if image.size != PINNED_SIZE:
        raise ValueError(f"{path} must use the locked {PINNED_SIZE[0]}x{PINNED_SIZE[1]} primary mapping")
    alpha = image.getchannel("A")
    alpha_range = alpha.getextrema()
    source = alpha if alpha_range[0] < alpha_range[1] else image.convert("L")
    return source.point(lambda value: 255 if value >= 32 else 0)


def edge(mask: Image.Image) -> Image.Image:
    return ImageChops.subtract(mask.filter(ImageFilter.MaxFilter(7)), mask.filter(ImageFilter.MinFilter(7)))


def composite(reference: Path, trace_render: Path, silhouette: Path, output: Path, metadata: Path) -> dict[str, object]:
    if sha256(reference) != PINNED_SHA256:
        raise ValueError("reference does not match the pinned supplied Collector image")
    source = Image.open(reference).convert("RGB")
    if source.size != PINNED_SIZE:
        raise ValueError("reference does not use the pinned Collector source size")
    trace = binary_mask(trace_render)
    model = binary_mask(silhouette)
    trace_only = ImageChops.subtract(trace, model)
    model_only = ImageChops.subtract(model, trace)
    overlap = ImageChops.multiply(trace, model)

    model_panel = Image.new("RGB", PINNED_SIZE, "#101317")
    model_panel.paste((238, 241, 244), mask=model)
    overlay = source.convert("RGBA")
    overlay.alpha_composite(Image.composite(Image.new("RGBA", PINNED_SIZE, (0, 220, 255, 74)), Image.new("RGBA", PINNED_SIZE), overlap))
    overlay.alpha_composite(Image.composite(Image.new("RGBA", PINNED_SIZE, (52, 255, 120, 235)), Image.new("RGBA", PINNED_SIZE), edge(trace)))
    overlay.alpha_composite(Image.composite(Image.new("RGBA", PINNED_SIZE, (255, 206, 45, 235)), Image.new("RGBA", PINNED_SIZE), edge(trace_only)))
    overlay.alpha_composite(Image.composite(Image.new("RGBA", PINNED_SIZE, (255, 40, 170, 235)), Image.new("RGBA", PINNED_SIZE), edge(model_only)))

    panels = [
        labelled(source, "PINNED BASE REFERENCE"),
        labelled(model_panel, "ACTUAL LOCKED-CAMERA MODEL SILHOUETTE"),
        labelled(overlay.convert("RGB"), "DIRECT OVERLAY — GREEN=TRACE, YELLOW=MISSING, MAGENTA=EXCESS"),
    ]
    result = Image.new("RGB", (sum(panel.width for panel in panels), max(panel.height for panel in panels)))
    x = 0
    for panel in panels:
        result.paste(panel, (x, 0))
        x += panel.width
    output.parent.mkdir(parents=True, exist_ok=True)
    result.save(output)
    data = {
        "schema": "pale_mirror_visuals.harvester_primary_model_evidence.v1",
        "reference": {"path": str(reference), "sha256": sha256(reference)},
        "trace_render": {"path": str(trace_render), "sha256": sha256(trace_render)},
        "silhouette": {"path": str(silhouette), "sha256": sha256(silhouette)},
        "output": str(output),
        "mapping": "direct locked 1280x720 pixels; no fit, translation, scale, score, or acceptance decision",
        "pixels": {
            "trace": trace.histogram()[255],
            "model": model.histogram()[255],
            "overlap": overlap.histogram()[255],
            "trace_only": trace_only.histogram()[255],
            "model_only": model_only.histogram()[255],
        },
    }
    metadata.parent.mkdir(parents=True, exist_ok=True)
    metadata.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return data


def labelled(image: Image.Image, label: str) -> Image.Image:
    from PIL import ImageDraw

    panel = Image.new("RGB", (image.width, image.height + 34), "#111317")
    panel.paste(image.convert("RGB"), (0, 34))
    ImageDraw.Draw(panel).text((12, 10), label, fill="#f2f4f8")
    return panel


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--trace-render", required=True, type=Path)
    parser.add_argument("--silhouette", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--metadata", required=True, type=Path)
    arguments = parser.parse_args()
    composite(arguments.reference, arguments.trace_render, arguments.silhouette, arguments.output, arguments.metadata)


if __name__ == "__main__":
    main()
