#!/usr/bin/env python3
"""Portable contracts for the private Collector Hunyuan input boundary."""

from __future__ import annotations

import importlib.util
from pathlib import Path
import tempfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "hunyuan3d_2mv" / "prepare_collector_multiview.py"
INTEGRATION = ROOT / "tools" / "engineering" / "test_hunyuan2mv_preparation.py"
BUILD = ROOT / "build.gradle"
spec = importlib.util.spec_from_file_location("prepare_collector_multiview", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load prepare_collector_multiview")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

turntable = ROOT / "pale-mirror-visuals" / "src" / "main" / "blender" / "harvester" / "references" / module.TURNTABLE_FILE
assert turntable.is_file()

with tempfile.TemporaryDirectory() as temporary:
    temporary_root = Path(temporary)
    missing = temporary_root / module.PRIMARY_FILE
    try:
        module.prepare(missing, turntable, temporary_root / "missing")
    except FileNotFoundError:
        pass
    else:
        raise AssertionError("missing supplied primary must fail before any output is accepted")

    wrong_name = temporary_root / "foreign.jpg"
    wrong_name.write_bytes(b"not a Collector reference")
    try:
        module.prepare(wrong_name, turntable, temporary_root / "wrong-name")
    except ValueError as error:
        assert "pinned Collector authority" in str(error)
    else:
        raise AssertionError("wrong-name primary must fail")

    altered = temporary_root / module.PRIMARY_FILE
    altered.write_bytes(b"altered Collector reference")
    try:
        module.prepare(altered, turntable, temporary_root / "altered")
    except ValueError as error:
        assert "pinned Collector authority" in str(error)
    else:
        raise AssertionError("altered primary must fail")

with Image.open(turntable) as source:
    assert source.size == module.TURNTABLE_SIZE
    for slot, box, _ in module.PANELS:
        derived = module.centered_rgba(source.crop(box))
        assert derived.size == (module.OUTPUT_SIZE, module.OUTPUT_SIZE), slot
        assert derived.getchannel("A").histogram()[255] > 10_000, slot

integration = INTEGRATION.read_text(encoding="utf-8")
assert "PALE_MIRROR_HARVESTER_REFERENCES is required" in integration
assert "Path.home" not in integration
build = BUILD.read_text(encoding="utf-8")
guardrails = build.split("tasks.register('guardrails')", 1)[1].split("tasks.named('check')", 1)[0]
assert "tasks.named('testHunyuan2mvPreparationContract')" in guardrails
assert "tasks.named('verifyHunyuan2mvSuppliedReferenceIntegration')" not in guardrails
assert "outputs.upToDateWhen { false }" in build

print("Hunyuan2mv portable input contracts passed.")
