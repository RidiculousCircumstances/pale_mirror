#!/usr/bin/env python3
"""Static contracts for fixed, non-exportable Hunyuan2mv review operations."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
importer = (ROOT / "tools/blender/ops/import_hunyuan2mv_candidate.py").read_text(encoding="utf-8")
renderer = (ROOT / "tools/blender/ops/render_hunyuan2mv_candidate_audit.py").read_text(encoding="utf-8")
proxy = (ROOT / "tools/blender/ops/fit_hunyuan2mv_v03_trace_proxy.py").read_text(encoding="utf-8")
hull = (ROOT / "tools/blender/ops/build_hunyuan2mv_v03_trace_hull_proxy.py").read_text(encoding="utf-8")
smooth = (ROOT / "tools/blender/ops/smooth_hunyuan2mv_v03_trace_hull_proxy.py").read_text(encoding="utf-8")
client = (ROOT / "tools/blender/blender_operation_client.py").read_text(encoding="utf-8")
collector = (ROOT / "tools/blender/collect_windows_blender_outputs.py").read_text(encoding="utf-8")

for candidate in (
    "hunyuan2mv_v01",
    "hunyuan2mv_v02_primary_anchored",
    "hunyuan2mv_v03_primary_front",
    "hunyuan2mv_v04_calibrated_secondary",
    "hunyuan2mv_v05_canonical_turntable",
):
    assert candidate in importer
    assert candidate in collector
assert "def candidate_spec" in importer
assert "runtime_export_forbidden" in importer
assert "candidate_path = spec.root / \"candidate.glb\"" in importer
assert "_reset_review_only_scene_state" in importer
assert "candidate_spec(payload)" in renderer
assert "pm_runtime_export_forbidden" in renderer
assert '"fit_hunyuan2mv_v03_trace_proxy"' in client
assert '"hunyuan2mv_v03_primary_front"' in proxy
assert "trace_silhouette_volume" in proxy
assert "runtime_export_forbidden" in proxy
assert "export_pmmesh" not in proxy
assert '"build_hunyuan2mv_v03_trace_hull_proxy"' in client
assert '"hunyuan2mv_v03_primary_front"' in hull
assert "BVHTree" in hull
assert "pm_hunyuan_reference_only" in hull
assert "runtime_export_forbidden" in hull
assert "export_pmmesh" not in hull
assert '"smooth_hunyuan2mv_v03_trace_hull_proxy"' in client
assert "_VOXEL_SIZE = 0.12" in smooth
assert "IDPropertyGroup" in smooth
assert "pm_runtime_export_forbidden" in smooth
assert "export_pmmesh" not in smooth
assert "operations_root" in client
assert "sys.modules.pop(name, None)" in client

print("Hunyuan2mv review-operation contracts passed.")
