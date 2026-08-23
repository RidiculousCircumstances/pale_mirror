#!/usr/bin/env python3
"""Static contract for the Hunyuan2mv upstream-liveness probe."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL = ROOT / "tools" / "hunyuan3d_2mv" / "run_hunyuan2mv_upstream_probe.py"
source = TOOL.read_text(encoding="utf-8")

assert 'VIEW_ORDER = ("front", "left", "back", "right")' in source
assert 'parser.add_argument("--steps", type=int, default=50)' in source
assert "load_shape_pipeline_low_memory" in source
assert "pipeline.enable_model_cpu_offload()" in source
assert '"runtime_export_forbidden": True' in source

print("Hunyuan2mv upstream probe contract passed.")
