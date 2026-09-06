#!/usr/bin/env python3
"""Focused contracts for the non-canonical VGGT Collector input preparer."""

from __future__ import annotations

import hashlib
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools/vggt_omega/prepare_collector_turntable.py"
spec = importlib.util.spec_from_file_location("prepare_collector_turntable", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load prepare_collector_turntable")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


with TemporaryDirectory(prefix="pm-vggt-turntable-test-") as temporary:
    temporary_path = Path(temporary)
    literal = temporary_path / "literal.png"
    literal_image = Image.new("RGB", (20, 12), (4, 4, 4))
    ImageDraw.Draw(literal_image).rectangle((4, 2, 15, 10), fill=(90, 18, 14))
    literal_image.save(literal)
    trace = {
        "trace_id": "test_primary",
        "source": {"file": literal.name, "sha256": digest(literal), "size": [20, 12]},
        "sampling": {"grid_px": 1},
        "rows": [{"y": y, "runs": [[4, 16]]} for y in range(2, 11)],
    }
    trace_path = temporary_path / "trace.json"
    trace_path.write_text(json.dumps(trace), encoding="utf-8")

    sheet = Image.new("RGB", (40, 30), (8, 8, 8))
    draw = ImageDraw.Draw(sheet)
    for index in range(12):
        x, y = (index % 4) * 10, (index // 4) * 10
        draw.rectangle((x + 2, y + 2, x + 7, y + 7), fill=(60 + index, 18, 14))
    sheet_path = temporary_path / "generated.png"
    sheet.save(sheet_path)

    output = temporary_path / "prepared"
    manifest = module.prepare(literal, sheet_path, output, trace_path, panel_size=64)
    assert manifest["status"] == "generated_unreviewed_noncanonical"
    assert manifest["frame_count"] == 12
    assert manifest["frames"][0]["source"] == "literal_primary_trace_masked"
    assert manifest["frames"][0]["authority"] == "sole_likeness_anchor"
    assert manifest["discarded_generated_panel"]["index"] == 0
    assert manifest["frames"][1]["source_panel"] == {"column": 1, "row": 0, "index": 1}
    assert all(frame["sha256"] == digest(output / frame["file"]) for frame in manifest["frames"])
    assert digest(output / manifest["review_sheet"]["file"]) == manifest["review_sheet"]["sha256"]
    for frame in manifest["frames"]:
        with Image.open(output / frame["file"]) as image:
            assert image.size == (64, 64)
    with Image.open(output / manifest["review_sheet"]["file"]) as image:
        assert image.size == (256, 192)

print("VGGT Collector turntable preparer contracts passed.")
