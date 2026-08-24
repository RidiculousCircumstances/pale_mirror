"""Fail closed on direct-v05 session ownership, provenance and review-state drift."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import (  # noqa: E402
    ASSET_ID, BACKUP_COLLECTION, RAW_COLLECTION, RAW_OBJECT, WORKING_OBJECT,
    mesh_digest, read_receipt, require_session_scene,
)
from common import REFERENCE_PLANE_NAME, TRACE_GUIDE_NAME, triangle_count  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, protocol_hash, working = require_session_scene(payload)
    raw = bpy.data.objects.get(RAW_OBJECT)
    problems: list[str] = []
    if raw is None or RAW_COLLECTION not in {collection.name for collection in raw.users_collection}:
        problems.append("raw v05 mesh is outside its dedicated collection")
    if raw is not None and raw.get("pm_direct_mesh_sha256") != mesh_digest(raw):
        problems.append("raw v05 mesh digest drifted")
    if len([object_ for object_ in bpy.data.objects if object_.get("pm_direct_working")]) != 1:
        problems.append("direct session must have exactly one editable working mesh")
    if working.name != WORKING_OBJECT or working.get("pm_export_exclude"):
        problems.append("working mesh has invalid identity or export flag")
    if not bpy.data.collections.get(BACKUP_COLLECTION):
        problems.append("direct session backup collection is missing")
    if bpy.context.scene.get("pm_direct_protocol_sha256") != protocol_hash:
        problems.append("direct protocol hash drifted")
    if bpy.context.scene.get("pm_runtime_export_forbidden") is not True:
        problems.append("direct session accidentally permits runtime export")
    if bpy.data.objects.get(TRACE_GUIDE_NAME) is None or bpy.data.objects.get(REFERENCE_PLANE_NAME) is None:
        problems.append("locked trace/reference guide is missing")
    for declaration in protocol["passes"]:
        pass_id = declaration["id"]
        completed = read_receipt(pass_id)
        if completed is None:
            continue
        backup = bpy.data.objects.get(completed.get("backup_object", ""))
        if backup is None or backup.get("pm_direct_backup_for") != pass_id or backup.get("pm_direct_mesh_sha256") != mesh_digest(backup):
            problems.append(f"completed {pass_id} lacks an intact protected backup")
    if problems:
        raise ValueError("; ".join(problems))
    return {
        "asset_id": ASSET_ID,
        "valid": True,
        "working": {"object": working.name, "vertices": len(working.data.vertices), "triangles": triangle_count([working]), "mesh_sha256": mesh_digest(working)},
        "raw": {"object": raw.name, "mesh_sha256": mesh_digest(raw), "hidden": raw.hide_render},
        "runtime_export_forbidden": True,
        "visual_acceptance": bpy.context.scene.get("pm_visual_acceptance"),
    }


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
