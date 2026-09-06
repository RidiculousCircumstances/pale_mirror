#!/usr/bin/env python3
"""Focused contracts for primary-anchored Collector Hunyuan2mv preparation."""

from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import tempfile


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "hunyuan3d_2mv" / "prepare_collector_multiview_v02_primary_anchored.py"
spec = importlib.util.spec_from_file_location("prepare_collector_multiview_v02", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load primary-anchored Hunyuan preparation")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

reference_root = Path(os.environ.get("PALE_MIRROR_HARVESTER_REFERENCES", str(Path.home() / "harvester_references")))
primary = reference_root / module.PRIMARY_FILE
generated_root = ROOT / "pale-mirror-visuals" / "src" / "main" / "blender" / "harvester" / "references"
trace = module.SOURCE_TRACE_PATH
with tempfile.TemporaryDirectory() as temporary:
    output = Path(temporary) / "input"
    data = module.prepare(primary, generated_root, trace, output, "hunyuan2mv_v02_primary_anchored")
    assert data["candidate_id"] == "hunyuan2mv_v02_primary_anchored"
    assert data["primary_is_model_conditioning"] is True
    assert data["primary_slot"] == "left"
    assert data["views"]["left"]["source"] == "pinned_primary_reference_clipped_by_literal_trace"
    assert data["views"]["front"]["source"] == "generated_single_view"
    assert set(data["views"]) == {"front", "left", "back", "right"}
    assert json.loads((output / "manifest.json").read_text(encoding="utf-8"))["bakeoff_id"] == module.TRIALS["hunyuan2mv_v02_primary_anchored"]["bakeoff_id"]
    for entry in data["views"].values():
        image = output / entry["file"]
        assert image.is_file() and module.sha256(image) == entry["sha256"]
        assert entry["alpha_pixels"] > 10_000

with tempfile.TemporaryDirectory() as temporary:
    output = Path(temporary) / "input"
    data = module.prepare(primary, generated_root, trace, output, "hunyuan2mv_v03_primary_front")
    assert data["candidate_id"] == "hunyuan2mv_v03_primary_front"
    assert data["primary_slot"] == "front"
    assert data["views"]["front"]["source"] == "pinned_primary_reference_clipped_by_literal_trace"
    assert data["views"]["left"]["source"] == "generated_single_view"

with tempfile.TemporaryDirectory() as temporary:
    output = Path(temporary) / "input"
    data = module.prepare(primary, generated_root, trace, output, "hunyuan2mv_v04_calibrated_secondary")
    assert data["candidate_id"] == "hunyuan2mv_v04_calibrated_secondary"
    assert data["primary_slot"] == "front"
    assert data["views"]["front"]["source"] == "pinned_primary_reference_clipped_by_literal_trace"
    assert data["views"]["left"]["source"] == "generated_single_view"
    assert set(data["views"]) == {"front", "left", "back", "right"}

with tempfile.TemporaryDirectory() as temporary:
    output = Path(temporary) / "input"
    data = module.prepare(primary, generated_root, trace, output, "hunyuan2mv_v05_canonical_turntable")
    assert data["candidate_id"] == "hunyuan2mv_v05_canonical_turntable"
    assert data["primary_slot"] == "front"
    assert data["views"]["front"]["source"] == "pinned_primary_reference_clipped_by_literal_trace"
    assert data["views"]["left"]["source"] == "generated_single_view"
    assert data["views"]["back"]["role_hypothesis"].startswith("source-local 180-degree opposite-broadside")
    assert data["views"]["right"]["role_hypothesis"].startswith("source-local clockwise 270-degree head-end-on")
    assert set(data["views"]) == {"front", "left", "back", "right"}

print("Hunyuan2mv primary-anchored input contracts passed.")
