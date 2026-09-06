"""Repair ownership metadata copied into an immutable direct-session backup.

The first implementation of ``backup`` copied every custom property from the
working object.  That made a backup look like a second editable mesh to the
fail-closed validator.  This operation may only remove that mistaken marker;
it checks the exact geometry digest before and after so it cannot become a
general source-editing backdoor.
"""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, BACKUP_COLLECTION, WORKING_OBJECT, mesh_digest, require_session_scene  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    _protocol, _protocol_hash, working = require_session_scene(payload)
    if working.name != WORKING_OBJECT:
        raise ValueError("Direct backup repair cannot run without the canonical working mesh.")
    collection = bpy.data.collections.get(BACKUP_COLLECTION)
    if collection is None:
        raise ValueError("Direct session has no protected backup collection.")
    repaired: list[str] = []
    for backup in collection.objects:
        if backup.type != "MESH" or not backup.get("pm_direct_backup_for"):
            raise ValueError("Backup collection contains an unrecognised object.")
        before = mesh_digest(backup)
        if backup.get("pm_direct_working"):
            backup.pop("pm_direct_working", None)
            repaired.append(backup.name)
        if backup.get("pm_export_exclude") is not True:
            raise ValueError("A protected backup unexpectedly permits export.")
        if before != mesh_digest(backup):
            raise ValueError("Backup metadata repair changed protected geometry.")
    if any(object_.get("pm_direct_working") for object_ in bpy.data.objects if object_ != working):
        raise ValueError("A non-working object still claims direct working ownership.")
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {
        "asset_id": ASSET_ID,
        "repaired_backups": repaired,
        "rule": "only stale backup ownership metadata was removed; protected mesh hashes were unchanged",
    }


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
