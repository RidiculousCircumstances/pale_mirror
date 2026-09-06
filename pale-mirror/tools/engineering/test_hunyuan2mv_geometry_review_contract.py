#!/usr/bin/env python3
"""Static contract for the non-exportable Hunyuan geometry inspector."""

from __future__ import annotations

from pathlib import Path


source = (Path(__file__).resolve().parents[2] / "tools/hunyuan3d_2mv/inspect_hunyuan2mv_candidate.py").read_text(encoding="utf-8")
for term in ("runtime_export_forbidden", "mesh.is_watertight", "mesh.is_winding_consistent", "mesh.split", "output.get(\"sha256\")"):
    assert term in source
assert "mesh.export" not in source

print("Hunyuan2mv geometry-review contract passed.")
