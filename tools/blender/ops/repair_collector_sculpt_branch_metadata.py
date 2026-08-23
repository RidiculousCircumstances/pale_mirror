"""Repair one proven pre-receipt metadata defect in the v05 sculpt branch.

The first implementation of ``new_backup`` copied the working-object tag into
the backup.  This operation is deliberately narrower than a generic scene
editor: it can clear that exact inherited tag only from the declared backup of
the one completed direct-mesh pass, and records that correction as evidence.
"""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_sculpt_common import (  # noqa: E402
    ASSET_ID,
    WORKING_OBJECT,
    read_receipt,
    require_branch_scene,
    write_receipt,
)

import bpy


PASS_ID = "front_mantle_v01"
REPAIR_ID = "backup_working_tag_repair_v01"


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    _protocol, protocol_hash, _working = require_branch_scene()
    pass_receipt = read_receipt(PASS_ID)
    if pass_receipt is None:
        raise ValueError("Cannot repair backup metadata before the declared direct sculpt pass exists.")
    existing = read_receipt(REPAIR_ID)
    if existing is not None:
        return {"asset_id": ASSET_ID, "repair": REPAIR_ID, "already_repaired": True, "receipt": existing}
    backup_name = str(pass_receipt.get("backup_object", ""))
    expected = f"{WORKING_OBJECT}_before_{PASS_ID}"
    backup = bpy.data.objects.get(backup_name)
    if backup is None or backup.name != expected:
        raise ValueError("The only permitted v05 sculpt backup is missing or has unexpected identity.")
    if backup.get("pm_sculpt_backup_for") != PASS_ID or not backup.get("pm_export_exclude"):
        raise ValueError("The target backup does not prove the expected immutable/export-excluded role.")
    inherited = backup.get("pm_sculpt_working") is True
    backup.pop("pm_sculpt_working", None)
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    receipt = write_receipt(
        REPAIR_ID,
        {
            "schema": "pale_mirror_visuals.collector_sculpt_receipt.v1",
            "kind": "metadata_repair",
            "repair": REPAIR_ID,
            "protocol_sha256": protocol_hash,
            "target": backup.name,
            "removed_inherited_working_tag": inherited,
            "rule": "clear only the erroneous pm_sculpt_working tag from the exact export-excluded front_mantle backup",
            "runtime_export_forbidden": True,
        },
    )
    return {
        "asset_id": ASSET_ID,
        "repair": REPAIR_ID,
        "target": backup.name,
        "removed_inherited_working_tag": inherited,
        "receipt": receipt.as_posix(),
        "runtime_export_forbidden": True,
    }


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
