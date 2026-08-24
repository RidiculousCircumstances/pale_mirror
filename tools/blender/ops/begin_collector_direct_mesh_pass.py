"""Open exactly one sequential direct-mesh edit pass with a full backup."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, backup, load_protocol, mesh_digest, read_receipt, review_key, require_session_scene, write_receipt  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    pass_id = str(payload.get("pass_id", ""))
    protocol, protocol_hash, working = require_session_scene(payload)
    declaration = next((entry for entry in protocol["passes"] if entry["id"] == pass_id), None)
    if not isinstance(declaration, dict):
        raise ValueError("Direct pass is not declared by the checked-in protocol.")
    if bpy.context.scene.get("pm_direct_open_pass"):
        raise ValueError("A direct pass is already open; capture and review it or roll it back first.")
    predecessor = declaration["requires_review"]
    baseline_key = protocol.get("baseline_receipt", "raw_baseline")
    if predecessor == baseline_key:
        if read_receipt(str(baseline_key)) is None:
            raise ValueError("Capture the declared direct-session baseline before beginning the first pass.")
    else:
        review = read_receipt(review_key(predecessor))
        if review is None or review.get("decision") != "continue":
            raise ValueError("The preceding direct pass lacks an explicit continue decision.")
    if read_receipt(pass_id) is not None:
        raise ValueError("This direct pass already has a receipt and cannot be replayed.")
    snapshot = backup(working, pass_id)
    before_hash = mesh_digest(working)
    bpy.context.scene["pm_direct_open_pass"] = pass_id
    bpy.context.scene["pm_direct_open_before_sha256"] = before_hash
    bpy.context.scene["pm_direct_open_backup"] = snapshot.name
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    receipt = write_receipt(f"{pass_id}_opened", {
        "schema": "pale_mirror_visuals.collector_direct_receipt.v1",
        "session_id": protocol["session_id"],
        "kind": "direct_mesh_pass_opened",
        "pass_id": pass_id,
        "protocol_sha256": protocol_hash,
        "backup_object": snapshot.name,
        "before_mesh_sha256": before_hash,
        "topology_policy": "free local retopology is allowed only on the existing tagged working surface",
        "runtime_export_forbidden": True,
    })
    return {"asset_id": ASSET_ID, "pass_id": pass_id, "backup": snapshot.name, "receipt": receipt.as_posix(), "required_next": "edit only collector_v05_direct_working, then complete_collector_direct_mesh_pass"}


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "pass_id": "primary_composition_v01"}))
