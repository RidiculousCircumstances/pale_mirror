"""Restore the exact protected backup after a rejected direct-mesh pass."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, WORKING_OBJECT, mesh_digest, read_receipt, require_session_scene, review_key, write_receipt  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    pass_id = str(payload.get("pass_id", ""))
    protocol, protocol_hash, working = require_session_scene(payload)
    review = read_receipt(review_key(pass_id))
    completed = read_receipt(pass_id)
    if review is None or review.get("decision") != "rollback" or completed is None:
        raise ValueError("Only an explicitly rejected completed pass may be rolled back.")
    if read_receipt(f"{pass_id}_rollback") is not None:
        raise ValueError("Direct pass rollback already exists and is immutable.")
    backup_name = completed.get("backup_object")
    backup = bpy.data.objects.get(backup_name) if isinstance(backup_name, str) else None
    if backup is None or backup.get("pm_direct_backup_for") != pass_id or not backup.get("pm_export_exclude"):
        raise ValueError("Protected pre-pass backup is missing or invalid.")
    expected = backup.get("pm_direct_mesh_sha256")
    if expected != mesh_digest(backup):
        raise ValueError("Protected pre-pass backup was mutated.")
    working.data = backup.data.copy()
    working.name = WORKING_OBJECT
    working["pm_direct_working"] = True
    working["pm_export_exclude"] = False
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    receipt = write_receipt(f"{pass_id}_rollback", {
        "schema": "pale_mirror_visuals.collector_direct_receipt.v1",
        "session_id": protocol["session_id"],
        "kind": "exact_backup_rollback",
        "pass_id": pass_id,
        "protocol_sha256": protocol_hash,
        "backup_object": backup.name,
        "restored_mesh_sha256": mesh_digest(working),
        "runtime_export_forbidden": True,
    })
    return {"asset_id": ASSET_ID, "pass_id": pass_id, "receipt": receipt.as_posix(), "restored": True, "runtime_export_forbidden": True}


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "pass_id": "primary_composition_v01"}))
