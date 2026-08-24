"""HISTORICAL REJECTED: structural validation for the v03 sculpt branch."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_sculpt_common import (  # noqa: E402
    ASSET_ID,
    BACKUP_COLLECTION,
    RAW_COLLECTION,
    RAW_OBJECT,
    SESSION_ID,
    WORKING_OBJECT,
    connected_face_components,
    load_protocol,
    read_receipt,
    require_branch_scene,
)
from common import REFERENCE_PLANE_NAME, TRACE_GUIDE_NAME, triangle_count  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, protocol_hash, working = require_branch_scene()
    raw = bpy.data.objects.get(RAW_OBJECT)
    problems: list[str] = []
    if raw is None or raw.users_collection[0].name != RAW_COLLECTION:
        problems.append("immutable raw v05 mesh is not in its dedicated collection")
    if raw is not None and (not raw.get("pm_sculpt_raw_immutable") or not raw.hide_render or not raw.get("pm_export_exclude")):
        problems.append("raw v05 mesh is not hidden, immutable and export-excluded")
    if len([object_ for object_ in bpy.data.objects if object_.get("pm_sculpt_working")]) != 1:
        problems.append("sculpt branch must contain exactly one tagged editable working mesh")
    if working.name != WORKING_OBJECT or working.get("pm_export_exclude"):
        problems.append("working mesh is missing or accidentally export-excluded from audit")
    if bpy.context.scene.get("pm_runtime_export_forbidden") is not True:
        problems.append("sculpt branch must explicitly forbid runtime export")
    if bpy.context.scene.get("pm_sculpt_protocol_sha256") != protocol_hash:
        problems.append("sculpt branch protocol hash differs from checked-in session")
    if bpy.data.objects.get(TRACE_GUIDE_NAME) is None or bpy.data.objects.get(REFERENCE_PLANE_NAME) is None:
        problems.append("locked primary trace/reference guide is missing")
    if not bpy.data.collections.get(BACKUP_COLLECTION):
        problems.append("sculpt backup collection is missing")
    _validate_completed_passes(protocol, working, problems)
    components = connected_face_components(working.data)
    editable_value = working.get("pm_sculpt_editable_component_indices")
    try:
        editable = [int(index) for index in editable_value]
    except (TypeError, ValueError):
        editable = []
    if not components or 0 not in editable:
        problems.append("working mesh must retain the dominant v05 component and record its source components")
    if problems:
        raise ValueError("; ".join(problems))
    return {
        "asset_id": ASSET_ID,
        "session_id": SESSION_ID,
        "valid": True,
        "working": {"object": working.name, "vertices": len(working.data.vertices), "triangles": triangle_count([working]), "components": len(components), "source_components": editable},
        "raw": {"object": raw.name, "triangles": triangle_count([raw]), "hidden": raw.hide_render},
        "runtime_export_forbidden": True,
        "visual_acceptance": bpy.context.scene.get("pm_visual_acceptance"),
    }


def _validate_completed_passes(protocol: dict[str, object], working: bpy.types.Object, problems: list[str]) -> None:
    """Verify completed v03 regions without requiring all experiments to exist.

    The branch is intentionally inspectable after the baseline and after each
    independent pass. Once a receipt exists, however, its backup and semantic
    selection are mandatory and no later pass may have reused that selection.
    """
    declared = protocol.get("passes")
    if not isinstance(declared, list):
        problems.append("sculpt protocol has no declared passes")
        return
    occupied: set[int] = set()
    for entry in declared:
        if not isinstance(entry, dict) or entry.get("kind") != "direct_mesh_deformation":
            continue
        pass_id = entry.get("id")
        regions = entry.get("regions")
        if not isinstance(pass_id, str) or not isinstance(regions, list) or len(regions) != 1:
            problems.append("sculpt protocol has an invalid direct-mesh pass declaration")
            continue
        receipt = read_receipt(pass_id)
        if receipt is None:
            continue
        group_name = regions[0]
        group = working.vertex_groups.get(group_name)
        backup_name = receipt.get("backup_object") if isinstance(receipt, dict) else None
        backup = bpy.data.objects.get(backup_name) if isinstance(backup_name, str) else None
        if group is None:
            problems.append(f"completed {pass_id} is missing semantic group {group_name}")
            continue
        if backup is None or backup.get("pm_sculpt_backup_for") != pass_id or not backup.get("pm_export_exclude"):
            problems.append(f"completed {pass_id} is missing its protected pre-pass backup")
        selected: set[int] = set()
        for vertex in working.data.vertices:
            try:
                group.weight(vertex.index)
            except RuntimeError:
                continue
            selected.add(vertex.index)
        if not selected:
            problems.append(f"completed {pass_id} has an empty semantic group")
            continue
        overlap = selected & occupied
        if overlap:
            problems.append(f"completed {pass_id} overlaps {len(overlap)} vertices owned by an earlier direct pass")
        occupied.update(selected)


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
