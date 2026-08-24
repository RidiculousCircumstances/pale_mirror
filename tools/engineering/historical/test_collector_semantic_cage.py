#!/usr/bin/env python3
"""HISTORICAL REJECTED contract for the primary-rail Collector cage source."""

from __future__ import annotations

import ast
import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SEMANTIC = ROOT / "pale-mirror-visuals/src/main/blender/harvester/traces/biomass_collector_semantic_cage_v01.json"
TRACE = ROOT / "pale-mirror-visuals/src/main/blender/harvester/traces/biomass_collector_primary_v03.json"
OPERATION = ROOT / "tools/blender/ops/build_collector_semantic_cage.py"
ALLOWLIST = ROOT / "tools/blender/blender_operation_client.py"
SYNC = ROOT / "tools/blender/sync_windows_blender_workspace.py"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


semantic = json.loads(SEMANTIC.read_text(encoding="utf-8"))
assert semantic["schema"] == "pale_mirror_visuals.harvester_semantic_cage.v1"
assert semantic["asset_id"] == "biomass_collector"
assert semantic["primary_trace"] == {"trace_id": "biomass_collector_primary_v03", "sha256": sha256(TRACE)}
assert len(semantic["dorsal_lobes"]) == 6
assert len(semantic["supports"]) == 6
assert len(semantic["mantle"]["outer_primary_rail"]) == len(semantic["mantle"]["inner_primary_rail"]) >= 4
assert len(semantic["mantle"]["folds"]) >= 3
assert len(semantic["tail"]["fibres"]) >= 3
for lobe in semantic["dorsal_lobes"]:
    assert len(lobe["rails"]) >= 4
for support in semantic["supports"]:
    assert len(support["primary_centerline"]) == len(support["depth_y"]) == len(support["radii"]) >= 4

source = OPERATION.read_text(encoding="utf-8")
ast.parse(source)
for forbidden in ("primitive_uv_sphere_add", "primitive_torus_add", "add_uv_sphere", "add_tapered_tube", "add_dorsal_lobe"):
    assert forbidden not in source, f"semantic cage must not call generic primitive helper {forbidden}"
for required in ("_MeshBuilder", "_append_lobe", "_append_support", "_append_mantle", "pm_primitive_free", "runtime_export_forbidden"):
    assert required in source
assert '"build_collector_semantic_cage": "build_collector_semantic_cage.py"' in ALLOWLIST.read_text(encoding="utf-8")
assert '"biomass_collector_semantic_cage_v01.json"' in SYNC.read_text(encoding="utf-8")

print("Collector semantic cage contracts passed.")
