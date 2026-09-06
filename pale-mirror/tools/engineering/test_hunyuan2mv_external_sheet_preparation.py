#!/usr/bin/env python3
"""Focused contracts for external 2x4 Hunyuan input staging."""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import tempfile

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "hunyuan3d_2mv" / "prepare_external_reference_sheet.py"
spec = importlib.util.spec_from_file_location("prepare_external_reference_sheet", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load external-sheet Hunyuan preparation")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

with tempfile.TemporaryDirectory() as temporary:
    temporary_root = Path(temporary)
    sheet = Image.new("RGB", (800, 400), (150, 150, 150))
    draw = ImageDraw.Draw(sheet)
    for row in range(2):
        for column in range(4):
            left, top = column * 200 + 42, row * 200 + 56
            draw.ellipse((left, top, left + 112, top + 80), fill=(128 + column * 12, 30 + row * 10, 25))
            draw.rectangle((left + 34, top + 76, left + 76, top + 126), fill=(72, 18, 15))
    source = temporary_root / "reference.png"
    sheet.save(source)
    output = ROOT / "build" / "automodel" / "test_external_hunyuan_sheet_tmp"
    if output.exists():
        raise SystemExit("test output path must not already exist")
    try:
        data = module.prepare(source, output)
        assert data["candidate_id"] == module.CANDIDATE_ID
        assert data["runtime_export_forbidden"] is True
        assert data["source"]["external_reference_sheet"]["sha256"] == module.sha256(source)
        assert set(data["views"]) == {"front", "left", "back", "right"}
        assert data["views"]["front"]["source_cell"] == [0, 0]
        assert data["views"]["left"]["source_cell"] == [2, 1]
        assert data["views"]["back"]["source_cell"] == [3, 0]
        assert data["views"]["right"]["source_cell"] == [2, 0]
        for entry in data["views"].values():
            assert (output / entry["file"]).is_file()
            assert (output / entry["raw_panel"]["file"]).is_file()
            assert entry["alpha_pixels"] > 10_000
        manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
        assert manifest["source"]["evidence_tier"] == "MODEL_DERIVED"
    finally:
        if output.exists():
            for child in sorted(output.iterdir(), reverse=True):
                child.unlink()
            output.rmdir()

print("Hunyuan2mv external-sheet preparation contracts passed.")
