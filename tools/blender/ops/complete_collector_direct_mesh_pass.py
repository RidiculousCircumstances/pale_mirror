"""Seal a manually or versioned-operation edited direct pass and capture its audit."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, mesh_digest, require_session_scene, write_receipt  # noqa: E402
from render_collector_direct_audit import main as render  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    pass_id = str(payload.get("pass_id", ""))
    protocol, protocol_hash, working = require_session_scene(payload)
    if bpy.context.scene.get("pm_direct_open_pass") != pass_id:
        raise ValueError("Only the currently open direct pass can be completed.")
    before_hash = str(bpy.context.scene.get("pm_direct_open_before_sha256", ""))
    after_hash = mesh_digest(working)
    if not before_hash or before_hash == after_hash:
        raise ValueError("Direct pass made no working-mesh change; do not create empty evidence.")
    label = pass_id
    result = render({"asset_id": ASSET_ID, "session_id": protocol["session_id"], "label": label})
    receipt = write_receipt(pass_id, {
        "schema": "pale_mirror_visuals.collector_direct_receipt.v1",
        "session_id": protocol["session_id"],
        "kind": "direct_mesh_retopology",
        "pass_id": pass_id,
        "protocol_sha256": protocol_hash,
        "before_mesh_sha256": before_hash,
        "after_mesh_sha256": after_hash,
        "backup_object": bpy.context.scene["pm_direct_open_backup"],
        "audit": result["outputs"],
        "runtime_export_forbidden": True,
    })
    for key in ("pm_direct_open_pass", "pm_direct_open_before_sha256", "pm_direct_open_backup"):
        bpy.context.scene.pop(key, None)
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {"asset_id": ASSET_ID, "pass_id": pass_id, "receipt": receipt.as_posix(), "required_next": "independent visual review then record_collector_direct_review"}


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "pass_id": "primary_composition_v01"}))
