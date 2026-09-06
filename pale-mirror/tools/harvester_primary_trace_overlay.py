#!/usr/bin/env python3
"""Create non-normalized primary-reference trace evidence for a Blender audit.

Unlike the legacy contour comparison, this tool does not fit, translate or
scale a model.  Both images must already use the pinned 1280x720 primary-camera
mapping.  Any visible edge disagreement is therefore a trace/model defect.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageFilter


PINNED_SHA256 = "788cfe68f11e82a4e280de837090d3ebd2209cb598afbb8016946b1ea33a05fa"
PINNED_SIZE = (1280, 720)


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", required=True, type=Path)
    parser.add_argument("--trace-render", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--metadata", type=Path, help="Optional evidence metadata path")
    arguments = parser.parse_args()
    if _sha256(arguments.reference) != PINNED_SHA256:
        raise SystemExit("reference does not match the pinned supplied Collector image")
    reference = Image.open(arguments.reference).convert("RGB")
    trace_image = Image.open(arguments.trace_render).convert("RGBA")
    if reference.size != PINNED_SIZE or trace_image.size != PINNED_SIZE:
        raise SystemExit(f"reference and trace must both use the pinned {PINNED_SIZE[0]}x{PINNED_SIZE[1]} mapping")
    alpha = trace_image.getchannel("A")
    # Transparent audit frames encode the trace mask in alpha. The luminance
    # fallback keeps old evidence readable, but new renders are required to
    # use alpha so no colour transform can affect the outline.
    # Blender's regular audit render is deliberately an opaque black/white
    # frame, while the direct guide projection is transparent off-subject.
    # An all-opaque alpha channel contains no silhouette information, so it
    # must use luminance rather than silently painting the whole reference.
    alpha_range = alpha.getextrema()
    mask = alpha if alpha_range[0] < alpha_range[1] else trace_image.convert("L")
    mask = mask.point(lambda value: 255 if value >= 32 else 0)
    inner = mask.filter(ImageFilter.MinFilter(5))
    edge = ImageChops.subtract(mask, inner)
    overlay = reference.convert("RGBA")
    cyan = Image.new("RGBA", PINNED_SIZE, (0, 220, 255, 92))
    overlay.alpha_composite(Image.composite(cyan, Image.new("RGBA", PINNED_SIZE), mask))
    edge_layer = Image.new("RGBA", PINNED_SIZE, (0, 255, 125, 255))
    overlay.alpha_composite(Image.composite(edge_layer, Image.new("RGBA", PINNED_SIZE), edge))
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    overlay.convert("RGB").save(arguments.output)
    if arguments.metadata:
        arguments.metadata.parent.mkdir(parents=True, exist_ok=True)
        arguments.metadata.write_text(
            json.dumps(
                {
                    "schema": "pale_mirror_visuals.primary_trace_overlay.v1",
                    "reference": {"path": str(arguments.reference), "sha256": _sha256(arguments.reference)},
                    "trace_render": {"path": str(arguments.trace_render), "sha256": _sha256(arguments.trace_render)},
                    "output": str(arguments.output),
                    "mapping": "direct 1280x720 pixels; no fitting, translation, scaling, or score",
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )


if __name__ == "__main__":
    main()
