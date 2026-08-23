"""Capture the immutable raw-v05 baseline for the direct-sculpt protocol."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_sculpt_common import ASSET_ID, receipt_path, require_branch_scene, write_receipt  # noqa: E402
from render_collector_sculpt_audit import render  # noqa: E402


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, protocol_hash, working = require_branch_scene()
    if receipt_path("raw_baseline").is_file():
        raise ValueError("Collector sculpt raw baseline already exists; create a new session to replace it.")
    result = render("raw_baseline")
    receipt = write_receipt(
        "raw_baseline",
        {
            "schema": "pale_mirror_visuals.collector_sculpt_receipt.v1",
            "session_id": protocol["session_id"],
            "kind": "audit_only",
            "protocol_sha256": protocol_hash,
            "working_object": working.name,
            "working_vertices": len(working.data.vertices),
            "working_faces": len(working.data.polygons),
            "audit": result["outputs"],
            "runtime_export_forbidden": True,
        },
    )
    result["receipt"] = receipt.as_posix()
    return result


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
