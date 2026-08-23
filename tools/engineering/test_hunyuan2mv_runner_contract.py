#!/usr/bin/env python3
"""Static contract for the fixed Hunyuan2mv low-memory loader."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RUNNER = ROOT / "tools" / "hunyuan3d_2mv" / "run_collector_multiview.py"
source = RUNNER.read_text(encoding="utf-8")

assert "pipeline.components = {" in source
for component in ("vae", "model", "scheduler", "conditioner", "image_processor"):
    assert f'"{component}": {component}' in source
assert "pipeline.enable_model_cpu_offload()" in source
assert 'pipeline.device = torch.device("cuda")' in source
assert "--device" in source
assert '"hunyuan2mv_v02_primary_anchored"' in source
assert '"hunyuan2mv_v03_primary_front"' in source
assert '"hunyuan2mv_v04_calibrated_secondary"' in source
assert '"hunyuan2mv_v05_canonical_turntable"' in source
assert '"inference_steps": 50' in source

print("Hunyuan2mv low-memory runner contract passed.")
