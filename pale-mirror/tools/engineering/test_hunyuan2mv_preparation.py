#!/usr/bin/env python3
"""Focused contracts for the fixed Collector Hunyuan3D-2mv preparation."""

from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import tempfile


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "hunyuan3d_2mv" / "prepare_collector_multiview.py"
spec = importlib.util.spec_from_file_location("prepare_collector_multiview", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load prepare_collector_multiview")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

primary_root = Path(os.environ.get("PALE_MIRROR_HARVESTER_REFERENCES", str(Path.home() / "harvester_references")))
primary = primary_root / module.PRIMARY_FILE
turntable = ROOT / "pale-mirror-visuals" / "src" / "main" / "blender" / "harvester" / "references" / module.TURNTABLE_FILE
with tempfile.TemporaryDirectory() as temporary:
    output = Path(temporary) / "input"
    data = module.prepare(primary, turntable, output)
    assert data["asset_id"] == module.ASSET_ID
    assert data["runtime_export_forbidden"] is True
    assert data["inputs_are_calibrated_photos"] is False
    assert set(data["views"]) == {"front", "left", "back", "right"}
    assert json.loads((output / "manifest.json").read_text(encoding="utf-8"))["candidate_id"] == module.CANDIDATE_ID
    for slot, entry in data["views"].items():
        image = output / entry["file"]
        assert image.is_file(), f"missing {slot} input"
        assert module.sha256(image) == entry["sha256"]
        assert entry["alpha_pixels"] > 10_000

print("Hunyuan2mv four-view input contracts passed.")
