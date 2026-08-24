#!/usr/bin/env python3
"""HISTORICAL REJECTED contract for v03 direct Collector sculpt sessions."""

from __future__ import annotations

import ast
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROTOCOL_ROOT = ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_protocol"
PROTOCOL = PROTOCOL_ROOT / "collector_v05_sculpt_v03_composition.json"
HISTORIC_PROTOCOLS = (
    PROTOCOL_ROOT / "collector_v05_sculpt_v01.json",
    PROTOCOL_ROOT / "collector_v05_sculpt_v02.json",
)
REVIEW = ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_reviews/collector_v05_sculpt_v02_front_mantle_v02.json"
V03_REVIEWS = {
    "front_mantle_v03": ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_reviews/collector_v05_sculpt_v03_front_mantle_v03.json",
    "dorsal_rhythm_v03": ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_reviews/collector_v05_sculpt_v03_dorsal_rhythm_v03.json",
    "supports_v03": ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_reviews/collector_v05_sculpt_v03_supports_v03.json",
    "full_composition_v03": ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_reviews/collector_v05_sculpt_v03_full_composition_v03.json",
}
COMMON = ROOT / "tools/blender/ops/collector_sculpt_common.py"
CREATE = ROOT / "tools/blender/ops/create_collector_sculpt_branch.py"
BASELINE = ROOT / "tools/blender/ops/capture_collector_sculpt_baseline.py"
PASS = ROOT / "tools/blender/ops/apply_collector_sculpt_pass.py"
REPAIR = ROOT / "tools/blender/ops/repair_collector_sculpt_branch_metadata.py"
RENDER = ROOT / "tools/blender/ops/render_collector_sculpt_audit.py"
VALIDATE = ROOT / "tools/blender/ops/validate_collector_sculpt_branch.py"
ALLOWLIST = ROOT / "tools/blender/blender_operation_client.py"
SYNC = ROOT / "tools/blender/sync_windows_blender_workspace.py"
COLLECT = ROOT / "tools/blender/collect_windows_blender_outputs.py"
CLI = ROOT / "tools/blender/pm_blender_cli.py"
MCP = ROOT / "tools/blender/pm_blender_mcp.py"


def source(path: Path) -> str:
    text = path.read_text(encoding="utf-8")
    ast.parse(text, filename=str(path))
    return text


protocol_bytes = PROTOCOL.read_bytes()
protocol = json.loads(protocol_bytes.decode("utf-8"))
assert protocol["schema"] == "pale_mirror_visuals.collector_sculpt_session.v1"
assert protocol["session_id"] == "collector_v05_sculpt_v03_composition"
assert protocol["asset_id"] == "biomass_collector"
assert protocol["candidate"] == {
    "id": "hunyuan2mv_v05_canonical_turntable",
    "stage": "volume-proposal-unaccepted",
    "file": "candidate.glb",
    "sha256": "3bfcddfd428b30c94573d63a2fb4be01162402f8fce8375fb2e51139b2780963",
    "role": "immutable hidden-side volume and editable-topology starting point; never likeness authority",
}
assert protocol["primary_trace"] == {
    "id": "biomass_collector_primary_v03",
    "sha256": "e3150d88594a8c712c22dfe60395c43f2da038c6dea7bc53601bff8c69692cf7",
    "role": "sole primary-view likeness and contour authority",
}
assert protocol["source"]["blend"].endswith("biomass_collector_sculpt_v03_composition.blend")
assert protocol["topology_policy"]["initial"].startswith("retain the dominant raw v05 component")
assert set(protocol["topology_policy"]["forbidden"]) >= {"runtime export", "rigging", "infection registration"}
assert {pass_["id"] for pass_ in protocol["passes"]} == {
    "raw_baseline", "front_mantle_v03", "dorsal_rhythm_v03", "supports_v03"
}
for pass_ in protocol["passes"][1:]:
    assert pass_["requires_previous"] == "raw_baseline"
assert len(protocol["source_landmarks"]["front_drape_outer_strokes_pixels"]) == 2
assert len(protocol["source_landmarks"]["dorsal_sac_peaks_pixels"]) == 6
assert len(protocol["source_landmarks"]["support_axes_pixels"]) == 6
assert "failed local review does not prevent" in protocol["composition_rule"]["execution"]
assert "whole-composition audit" in protocol["shape_gate"]["required_review"]
assert hashlib.sha256(protocol_bytes).hexdigest()

historic_v01 = json.loads(HISTORIC_PROTOCOLS[0].read_text(encoding="utf-8"))
historic_v02 = json.loads(HISTORIC_PROTOCOLS[1].read_text(encoding="utf-8"))
assert historic_v01["session_id"] == "collector_v05_sculpt_v01"
assert historic_v02["session_id"] == "collector_v05_sculpt_v02"
assert {pass_["id"] for pass_ in historic_v02["passes"]} >= {"raw_baseline", "front_mantle_v02"}
review = json.loads(REVIEW.read_text(encoding="utf-8"))
assert review["session_id"] == "collector_v05_sculpt_v02"
assert review["pass_id"] == "front_mantle_v02"
assert review["verdict"] == "failed_declared_pass_goal"
assert review["acceptance"] == "unaccepted_non_exportable"
assert review["runtime_export_forbidden"] is True
for pass_id, path in V03_REVIEWS.items():
    reviewed_pass = json.loads(path.read_text(encoding="utf-8"))
    assert reviewed_pass["session_id"] == "collector_v05_sculpt_v03_composition"
    assert reviewed_pass["pass_id"] == pass_id
    assert reviewed_pass["verdict"] in {"failed_declared_pass_goal", "hard_rejected"}
    assert reviewed_pass["acceptance"] == "unaccepted_non_exportable"
    assert reviewed_pass["runtime_export_forbidden"] is True

common = source(COMMON)
create = source(CREATE)
baseline = source(BASELINE)
pass_source = source(PASS)
repair = source(REPAIR)
render = source(RENDER)
validate = source(VALIDATE)
for forbidden in ("primitive_uv_sphere_add", "primitive_torus_add", "primitive_cylinder_add", "quadri", '"REMESH"'):
    assert forbidden not in pass_source, forbidden
assert 'SESSION_ID = "collector_v05_sculpt_v03_composition"' in common
assert 'PHASE = "collector_v05_sculpt_branch_v03"' in common
assert "load_candidate_manifest" in create
assert "pm_sculpt_raw_immutable" in create
assert "copy_component_mesh" in create
assert "excluded_components" in create
assert "raw_baseline" in baseline and "audit_only" in baseline
assert 'PASS_ID = "front_mantle_v03"' in pass_source
for pass_id, group, landmark in (
    ("front_mantle_v03", "front_mantle_outer_drape_v03", "front_drape_outer_strokes_pixels"),
    ("dorsal_rhythm_v03", "dorsal_sac_rhythm_v03", "dorsal_sac_peaks_pixels"),
    ("supports_v03", "support_arches_v03", "support_axes_pixels"),
):
    assert pass_id in pass_source
    assert group in pass_source
    assert landmark in pass_source
assert "new_backup" in pass_source and "ensure_semantic_group" in pass_source
assert "_exclude_prior_semantic_regions" in pass_source
assert "runtime_export_forbidden" in pass_source
assert 'REPAIR_ID = "backup_working_tag_repair_v01"' in repair
assert 'backup.pop("pm_sculpt_working", None)' in repair
assert "PM_COLLECTOR_SCULPT_AUDIT_SILHOUETTE" in render
assert "pm_runtime_export_forbidden" in validate
assert "retain the dominant v05 component" in validate

allowlist = ALLOWLIST.read_text(encoding="utf-8")
for operation in (
    "create_collector_sculpt_branch",
    "capture_collector_sculpt_baseline",
    "apply_collector_sculpt_pass",
    "render_collector_sculpt_audit",
    "validate_collector_sculpt_branch",
):
    assert f'"{operation}"' in allowlist
sync = SYNC.read_text(encoding="utf-8")
for name in ("collector_v05_sculpt_v01.json", "collector_v05_sculpt_v02.json", "collector_v05_sculpt_v03_composition.json"):
    assert f'"{name}"' in sync
collect = COLLECT.read_text(encoding="utf-8")
assert '"collector_v05_sculpt_v03_composition"' in collect
assert "arguments.candidate and arguments.sculpt_session" in collect
assert "sculpt_sessions/{arguments.sculpt_session}/audits/{arguments.label}" in collect
assert "sculpt-session collection requires --reference-root" in collect
assert "harvester_primary_model_overlay.py" in collect
cli = CLI.read_text(encoding="utf-8")
for pass_id in ("front_mantle_v03", "dorsal_rhythm_v03", "supports_v03"):
    assert f'"{pass_id}"' in cli
mcp = MCP.read_text(encoding="utf-8")
for pass_id in ("front_mantle_v03", "dorsal_rhythm_v03", "supports_v03"):
    assert f'"{pass_id}"' in mcp

print("collector sculpt protocol contracts passed")
