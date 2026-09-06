#!/usr/bin/env python3
"""Fast invariant checks for the trace-authoritative volume bake-off input."""

from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools/harvester_volume_bakeoff.py"
spec = importlib.util.spec_from_file_location("harvester_volume_bakeoff", TOOL)
if spec is None or spec.loader is None:
    raise SystemExit("could not load harvester_volume_bakeoff")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

bakeoff = module.load_json(module.BAKEOFF_PATH)
trace = module.load_json(module.TRACE_PATH)
primary = bakeoff["primary_input"]
assert module.sha256(module.TRACE_PATH) == primary["trace_sha256"]
assert trace["trace_id"] == primary["trace_file"].removesuffix(".json")
mask = module.mask_from_trace(trace)
assert mask.size == tuple(primary["reference_size"])
assert mask.getbbox() is not None
assert mask.getpixel((260, 180)) == 255, "the trace's subject seed must survive mask compilation"
assert mask.getpixel((0, 0)) == 0, "the trace compiler must not turn the full reference into foreground"
foreground = mask.histogram()[255]
assert 1_000 < foreground < mask.width * mask.height // 2, "trace mask foreground must stay bounded"
assert bakeoff["acceptance"]["forbids_primary_trace_mutation"] is True
assert bakeoff["acceptance"]["forbids_runtime_export"] is True
print("Harvester volume bake-off contracts passed.")
