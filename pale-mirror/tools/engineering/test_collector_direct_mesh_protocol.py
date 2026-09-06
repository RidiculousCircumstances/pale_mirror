#!/usr/bin/env python3
"""Contract for the active protected direct-v05 Collector authoring path."""

from __future__ import annotations

import ast
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PROTOCOL = ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_protocol/collector_v05_direct_mesh_v01.json"
PROTOCOL_V02 = ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_protocol/collector_v05_direct_mesh_v02.json"
PROTOCOL_V03 = ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_protocol/collector_v05_direct_mesh_v03.json"
PRODUCTION_MASTER = ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_protocol/collector_v05_production_master_v01.json"
SESSION_CATALOG = ROOT / "pale-mirror-visuals/src/main/blender/harvester/sculpt_protocol/collector_direct_mesh_sessions_v01.json"
TRACE = ROOT / "pale-mirror-visuals/src/main/blender/harvester/traces/biomass_collector_primary_v03.json"
OPS = ROOT / "tools/blender/ops"
ACTIVE = (
    "collector_direct_common.py",
    "create_collector_direct_session.py",
    "continue_collector_direct_session.py",
    "capture_collector_direct_baseline.py",
    "begin_collector_direct_mesh_pass.py",
    "apply_collector_primary_composition.py",
    "repair_collector_direct_backup_metadata.py",
    "apply_collector_diagnostic_solidity.py",
    "apply_collector_tail_cleanup.py",
    "apply_collector_front_mantle_grounding.py",
    "collector_artist_region.py",
    "inspect_collector_artist_mantle_topology.py",
    "prepare_collector_artist_leading_mantle.py",
    "prepare_collector_master_artist_session.py",
    "inspect_collector_master_sculpt_assets.py",
    "inspect_collector_tail_components.py",
    "complete_collector_direct_mesh_pass.py",
    "record_collector_direct_review.py",
    "rollback_collector_direct_mesh_pass.py",
    "render_collector_direct_audit.py",
    "validate_collector_direct_session.py",
)
ALLOWLIST = ROOT / "tools/blender/blender_operation_client.py"
CLI = ROOT / "tools/blender/pm_blender_cli.py"
MCP = ROOT / "tools/blender/pm_blender_mcp.py"
SYNC = ROOT / "tools/blender/sync_windows_blender_workspace.py"
COLLECT = ROOT / "tools/blender/collect_windows_blender_outputs.py"
HISTORICAL = OPS / "historical"


def source(path: Path) -> str:
    value = path.read_text(encoding="utf-8")
    ast.parse(value, filename=str(path))
    return value


protocol_bytes = PROTOCOL.read_bytes()
protocol = json.loads(protocol_bytes.decode("utf-8"))
assert protocol["schema"] == "pale_mirror_visuals.collector_direct_mesh_session.v1"
assert protocol["session_id"] == "collector_v05_direct_mesh_v01"
assert protocol["candidate"]["id"] == "hunyuan2mv_v05_canonical_turntable"
assert protocol["candidate"]["sha256"] == "3bfcddfd428b30c94573d63a2fb4be01162402f8fce8375fb2e51139b2780963"
assert protocol["primary_trace"] == {
    "id": "biomass_collector_primary_v03",
    "sha256": hashlib.sha256(TRACE.read_bytes()).hexdigest(),
    "role": "sole primary-view likeness, silhouette and landmark authority",
}
assert [entry["id"] for entry in protocol["passes"]] == ["primary_composition_v01", "diagnostic_solidity_v01", "tail_cleanup_v01"]
assert "local face deletion, creation and merge on the existing working surface" in protocol["topology_policy"]["allowed"]
assert set(protocol["topology_policy"]["forbidden"]) >= {"primitive construction", "trace or semantic cage construction", "new disconnected body meshes", "runtime export"}

catalog = json.loads(SESSION_CATALOG.read_text(encoding="utf-8"))
assert catalog["schema"] == "pale_mirror_visuals.collector_direct_mesh_sessions.v1"
assert set(catalog["sessions"]) == {
    "collector_v05_direct_mesh_v01",
    "collector_v05_direct_mesh_v02",
    "collector_v05_direct_mesh_v03",
    "collector_v05_production_master_v01",
}
protocol_v02 = json.loads(PROTOCOL_V02.read_text(encoding="utf-8"))
assert protocol_v02["session_id"] == "collector_v05_direct_mesh_v02"
assert protocol_v02["parent_session"]["id"] == "collector_v05_direct_mesh_v01"
assert protocol_v02["parent_session"]["reviewed_working_mesh_sha256"] == "5dd9a79fb21685f95467bc307a6381dbf5648f3a01c9cf696106220af12ff38b"
assert {key: protocol_v02["candidate"][key] for key in ("id", "file", "sha256")} == {key: protocol["candidate"][key] for key in ("id", "file", "sha256")}
assert protocol_v02["primary_trace"] == protocol["primary_trace"]
assert protocol_v02["baseline_receipt"] == "continuation_baseline_v02"
assert [entry["id"] for entry in protocol_v02["passes"]] == ["front_mantle_grounding_v02", "dorsal_cascade_v02", "support_hierarchy_v02", "tail_fan_v02", "surface_coherence_v02"]
assert set(protocol_v02["topology_policy"]["forbidden"]) >= {"primitive construction", "trace or semantic cage construction", "new disconnected body meshes", "runtime export"}
protocol_v03 = json.loads(PROTOCOL_V03.read_text(encoding="utf-8"))
assert protocol_v03["session_id"] == "collector_v05_direct_mesh_v03"
assert protocol_v03["parent_session"] == {
    "id": "collector_v05_direct_mesh_v02",
    "reviewed_working_mesh_sha256": "5dd9a79fb21685f95467bc307a6381dbf5648f3a01c9cf696106220af12ff38b",
    "reason": "v02's first broad interior mantle deformation was independently rejected and exactly rolled back. v03 begins from that recovered mesh as an artist-prepared, one-region leading-mantle repair."
}
assert protocol_v03["candidate"]["sha256"] == protocol["candidate"]["sha256"]
assert protocol_v03["primary_trace"] == protocol["primary_trace"]
assert protocol_v03["artist_region"]["id"] == "leading_mantle_v03"
assert len(protocol_v03["artist_region"]["primary_polygon_px"]) == 10
assert [entry["id"] for entry in protocol_v03["passes"]] == ["leading_mantle_artist_v03"]
assert protocol_v03["passes"][0]["kind"] == "artist_direct_sculpt_and_local_retopology"
assert "may not automate the dominant-mass deformation" in protocol_v03["passes"][0]["authoring_requirement"]
assert set(protocol_v03["topology_policy"]["forbidden"]) >= {"primitive construction", "trace or semantic cage construction", "new disconnected body meshes", "runtime export"}
production_master = json.loads(PRODUCTION_MASTER.read_text(encoding="utf-8"))
assert production_master["session_id"] == "collector_v05_production_master_v01"
assert production_master["parent_session"]["id"] == "collector_v05_direct_mesh_v02"
assert production_master["parent_session"]["reviewed_working_mesh_sha256"] == "5dd9a79fb21685f95467bc307a6381dbf5648f3a01c9cf696106220af12ff38b"
assert production_master["candidate"]["sha256"] == protocol["candidate"]["sha256"]
assert production_master["primary_trace"] == protocol["primary_trace"]
assert production_master["baseline_receipt"] == "production_master_baseline_v01"
assert [entry["id"] for entry in production_master["passes"]] == ["production_master_form_v01", "production_master_surface_v01"]
assert production_master["passes"][0]["kind"] == "artist_direct_sculpt_and_connected_local_retopology"
assert set(production_master["topology_policy"]["forbidden"]) >= {"primitive construction", "replacement body meshes", "disconnected anatomy", "runtime export"}

active_source = {name: source(OPS / name) for name in ACTIVE}
for text in active_source.values():
    for forbidden in ("primitive_uv_sphere_add", "primitive_torus_add", "primitive_cylinder_add", "trace_silhouette_volume", "MeshBuilder", "trace_cage"):
        assert forbidden not in text, forbidden
assert "pm_direct_raw_immutable" in active_source["create_collector_direct_session.py"]
assert "target protocol must declare the exact reviewed parent" in active_source["continue_collector_direct_session.py"]
assert "copy_faces(raw, range(len(raw.data.polygons)), lod0)" in active_source["create_collector_direct_session.py"]
assert "pm_direct_open_pass" in active_source["begin_collector_direct_mesh_pass.py"]
assert "primary_composition_v01" in active_source["apply_collector_primary_composition.py"]
assert "no new meshes or topology construction" in active_source["apply_collector_primary_composition.py"]
assert 'snapshot.pop("pm_direct_working", None)' in active_source["collector_direct_common.py"]
assert "Backup metadata repair changed protected geometry" in active_source["repair_collector_direct_backup_metadata.py"]
assert "diagnostic_solidity_v01" in active_source["apply_collector_diagnostic_solidity.py"]
assert "no new meshes or topology construction" in active_source["apply_collector_diagnostic_solidity.py"]
assert "tail_cleanup_v01" in active_source["apply_collector_tail_cleanup.py"]
assert "trace-invisible disconnected geometry only" in active_source["apply_collector_tail_cleanup.py"]
assert "front_mantle_grounding_v02" in active_source["apply_collector_front_mantle_grounding.py"]
assert "no new meshes or topology construction" in active_source["apply_collector_front_mantle_grounding.py"]
assert "leading_mantle_v03" in active_source["collector_artist_region.py"]
assert "read_only_artist_region_topology" in active_source["inspect_collector_artist_mantle_topology.py"]
assert "geometry_changed\": False" in active_source["prepare_collector_artist_leading_mantle.py"]
assert "must not change the protected working mesh geometry" in active_source["prepare_collector_artist_leading_mantle.py"]
assert "production_master_form_v01" in active_source["prepare_collector_master_artist_session.py"]
assert "Artist-session preparation changed protected Collector geometry" in active_source["prepare_collector_master_artist_session.py"]
assert "bpy.ops.object.mode_set(mode=\"SCULPT\")" in active_source["prepare_collector_master_artist_session.py"]
assert 'bpy.context.window.workspace = sculpting_workspace' in active_source["prepare_collector_master_artist_session.py"]
assert "visible Blender Sculpting workspace must select Grab" in active_source["prepare_collector_master_artist_session.py"]
assert "primary_camera.data.background_images.new()" in active_source["prepare_collector_master_artist_session.py"]
assert "prepare_trace_guides()" in active_source["prepare_collector_master_artist_session.py"]
assert "ensure_primary_reference_plane(trace)" in active_source["prepare_collector_master_artist_session.py"]
assert "Sculpt-asset inspection changed protected Collector geometry" in active_source["inspect_collector_master_sculpt_assets.py"]
assert "Direct pass made no working-mesh change" in active_source["complete_collector_direct_mesh_pass.py"]
assert "decision must be exactly continue or rollback" in active_source["record_collector_direct_review.py"]
assert "exact_backup_rollback" in active_source["rollback_collector_direct_mesh_pass.py"]
assert "raw v05 mesh was mutated" in active_source["collector_direct_common.py"]
assert "pm_runtime_export_forbidden" in active_source["validate_collector_direct_session.py"]

allowlist = ALLOWLIST.read_text(encoding="utf-8")
for operation in ("create_collector_direct_session", "continue_collector_direct_session", "capture_collector_direct_baseline", "begin_collector_direct_mesh_pass", "apply_collector_primary_composition", "repair_collector_direct_backup_metadata", "apply_collector_diagnostic_solidity", "apply_collector_tail_cleanup", "apply_collector_front_mantle_grounding", "inspect_collector_artist_mantle_topology", "prepare_collector_artist_leading_mantle", "prepare_collector_master_artist_session", "inspect_collector_master_sculpt_assets", "inspect_collector_tail_components", "complete_collector_direct_mesh_pass", "record_collector_direct_review", "rollback_collector_direct_mesh_pass", "render_collector_direct_audit", "validate_collector_direct_session"):
    assert f'"{operation}"' in allowlist
for retired in ("create_collector_base", "build_collector_semantic_cage", "build_collector_primary_masses", "create_collector_sculpt_branch", "apply_collector_sculpt_pass"):
    assert f'"{retired}"' not in allowlist
assert (HISTORICAL / "collector_trace_reconstruction/create_collector_base.py").is_file()
assert (HISTORICAL / "collector_v05_sculpt_v03/apply_collector_sculpt_pass.py").is_file()
assert '"historical" not in child.parts' in SYNC.read_text(encoding="utf-8")
assert "collector_v05_direct_mesh_v01.json" in SYNC.read_text(encoding="utf-8")
assert "collector_v05_direct_mesh_v02.json" in SYNC.read_text(encoding="utf-8")
assert "collector_v05_direct_mesh_v03.json" in SYNC.read_text(encoding="utf-8")
assert "collector_v05_production_master_v01.json" in SYNC.read_text(encoding="utf-8")
assert "collector_v05_front_mantle_contour_v03.json" not in SYNC.read_text(encoding="utf-8")
assert "collector_v05_front_mantle_grounding_v02.json" in SYNC.read_text(encoding="utf-8")
assert "collector_direct_mesh_sessions_v01.json" in SYNC.read_text(encoding="utf-8")
assert "collector_v05_direct_mesh_v01" in COLLECT.read_text(encoding="utf-8")
assert "collector_v05_direct_mesh_v02" in COLLECT.read_text(encoding="utf-8")
assert "collector_v05_direct_mesh_v03" in COLLECT.read_text(encoding="utf-8")
assert "collector_v05_production_master_v01" in COLLECT.read_text(encoding="utf-8")
assert "--direct-session" in COLLECT.read_text(encoding="utf-8")
for pass_id in ("primary_composition_v01", "diagnostic_solidity_v01", "tail_cleanup_v01", "front_mantle_grounding_v02", "dorsal_cascade_v02", "support_hierarchy_v02", "tail_fan_v02", "surface_coherence_v02", "leading_mantle_artist_v03", "production_master_form_v01", "production_master_surface_v01"):
    assert pass_id in CLI.read_text(encoding="utf-8")
    assert pass_id in MCP.read_text(encoding="utf-8")
assert "collector_v05_production_master_v01" in MCP.read_text(encoding="utf-8")

print("collector direct-v05 protocol contracts passed")
