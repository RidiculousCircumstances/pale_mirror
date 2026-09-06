#!/usr/bin/env python3
"""Focused contracts for direct primary-model contour evidence."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import tempfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools/harvester_primary_model_overlay.py"
spec = importlib.util.spec_from_file_location("harvester_primary_model_overlay", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load harvester_primary_model_overlay")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    reference = root / "reference.png"
    trace = root / "trace.png"
    model = root / "model.png"
    output = root / "overlay.png"
    metadata = root / "overlay.json"
    Image.new("RGB", module.PINNED_SIZE, "#111317").save(reference)
    # The tool pins the reference hash in production.  Patch only this local
    # deterministic test fixture, never the checked-in production authority.
    module.PINNED_SHA256 = module.sha256(reference)
    trace_image = Image.new("RGBA", module.PINNED_SIZE, (0, 0, 0, 255))
    model_image = Image.new("RGBA", module.PINNED_SIZE, (0, 0, 0, 255))
    for x in range(120, 180):
        for y in range(200, 260):
            trace_image.putpixel((x, y), (255, 255, 255, 255))
    for x in range(140, 200):
        for y in range(200, 260):
            model_image.putpixel((x, y), (255, 255, 255, 255))
    trace_image.save(trace)
    model_image.save(model)
    data = module.composite(reference, trace, model, output, metadata)
    assert output.is_file()
    assert data["mapping"].startswith("direct locked")
    assert data["pixels"]["trace_only"] > 0
    assert data["pixels"]["model_only"] > 0
    assert data["pixels"]["overlap"] > 0
    assert json.loads(metadata.read_text(encoding="utf-8"))["schema"].endswith("v1")

print("Harvester direct primary-model overlay contracts passed.")
