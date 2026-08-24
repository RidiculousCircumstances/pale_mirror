#!/usr/bin/env python3
"""Static contracts for archived Hunyuan2mv evidence and active-path isolation."""

from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HISTORICAL = ROOT / "tools/blender/ops/historical/hunyuan2mv_v01_v05"
SF3D_HISTORICAL = ROOT / "tools/blender/ops/historical/sf3d_v01"
importer = (HISTORICAL / "import_hunyuan2mv_candidate.py").read_text(encoding="utf-8")
renderer = (HISTORICAL / "render_hunyuan2mv_candidate_audit.py").read_text(encoding="utf-8")
proxy = (HISTORICAL / "fit_hunyuan2mv_v03_trace_proxy.py").read_text(encoding="utf-8")
hull = (HISTORICAL / "build_hunyuan2mv_v03_trace_hull_proxy.py").read_text(encoding="utf-8")
smooth = (HISTORICAL / "smooth_hunyuan2mv_v03_trace_hull_proxy.py").read_text(encoding="utf-8")
sf3d_importer = (SF3D_HISTORICAL / "import_sf3d_candidate.py").read_text(encoding="utf-8")
sf3d_renderer = (SF3D_HISTORICAL / "render_sf3d_candidate_audit.py").read_text(encoding="utf-8")
client = (ROOT / "tools/blender/blender_operation_client.py").read_text(encoding="utf-8")
cli = (ROOT / "tools/blender/pm_blender_cli.py").read_text(encoding="utf-8")
mcp = (ROOT / "tools/blender/pm_blender_mcp.py").read_text(encoding="utf-8")
collector = (ROOT / "tools/blender/collect_windows_blender_outputs.py").read_text(encoding="utf-8")
sync = (ROOT / "tools/blender/sync_windows_blender_workspace.py").read_text(encoding="utf-8")

for candidate in (
    "hunyuan2mv_v01",
    "hunyuan2mv_v02_primary_anchored",
    "hunyuan2mv_v03_primary_front",
    "hunyuan2mv_v04_calibrated_secondary",
    "hunyuan2mv_v05_canonical_turntable",
):
    assert candidate in importer
assert "def candidate_spec" in importer
assert "runtime_export_forbidden" in importer
assert "candidate_path = spec.root / \"candidate.glb\"" in importer
assert "_reset_review_only_scene_state" in importer
assert "parent.parents[1]" in importer
assert "candidate_spec(payload)" in renderer
assert "pm_runtime_export_forbidden" in renderer
assert "parent.parents[1]" in renderer
assert '"hunyuan2mv_v03_primary_front"' in proxy
assert "trace_silhouette_volume" in proxy
assert "runtime_export_forbidden" in proxy
assert "export_pmmesh" not in proxy
assert "parent.parents[1]" in proxy
assert '"hunyuan2mv_v03_primary_front"' in hull
assert "BVHTree" in hull
assert "pm_hunyuan_reference_only" in hull
assert "runtime_export_forbidden" in hull
assert "export_pmmesh" not in hull
assert "parent.parents[1]" in hull
assert "_VOXEL_SIZE = 0.12" in smooth
assert "IDPropertyGroup" in smooth
assert "pm_runtime_export_forbidden" in smooth
assert "export_pmmesh" not in smooth
assert "parent.parents[1]" in smooth
assert "operations_root" in client
assert "sys.modules.pop(name, None)" in client
assert "parent.parents[1]" in sf3d_importer
assert "parent.parents[1]" in sf3d_renderer
for inactive_operation in (
    "import_sf3d_candidate",
    "render_sf3d_candidate_audit",
    "import_hunyuan2mv_candidate",
    "render_hunyuan2mv_candidate_audit",
    "fit_hunyuan2mv_v03_trace_proxy",
    "build_hunyuan2mv_v03_trace_hull_proxy",
    "smooth_hunyuan2mv_v03_trace_hull_proxy",
):
    assert inactive_operation not in client
    assert inactive_operation not in cli
    assert inactive_operation not in mcp
assert "--candidate" not in collector
assert '"historical" not in child.parts' in sync

print("Archived Blender proposal evidence and active-path isolation contracts passed.")
