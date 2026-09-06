#!/usr/bin/env python3
"""Contract test for the reviewed Collector canonical turntable sheet."""

from __future__ import annotations

import importlib.util
import json
import os
from pathlib import Path
import tempfile

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "harvester_turntable_contact_sheet.py"
spec = importlib.util.spec_from_file_location("harvester_turntable_contact_sheet", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load Collector canonical turntable builder")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

manifest = ROOT / "pale-mirror-visuals/src/main/blender/harvester/references/biomass_collector_turntable_v02.provenance.json"
references_root = Path(os.environ.get("PALE_MIRROR_HARVESTER_REFERENCES", str(Path.home() / "harvester_references")))
provenance = json.loads(manifest.read_text(encoding="utf-8"))
derived = provenance["derived_contact_sheet"]
checked_in_sheet = manifest.parent / derived["file"]
assert module.sha256(checked_in_sheet) == derived["sha256"]
with tempfile.TemporaryDirectory() as temporary:
    output = Path(temporary) / "collector_turntable_v02.png"
    digest = module.build_sheet(manifest, references_root, output)
    assert output.is_file() and module.sha256(output) == digest
    assert digest == derived["sha256"]
    assert Image.open(output).size == (module.PANEL_SIZE * 3 + module.GAP * 2, module.PANEL_SIZE * 2 + module.GAP)

print("Collector canonical turntable contact-sheet contract passed.")
