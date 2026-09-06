"""Record the bounded visual decision required before the next direct pass."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, read_receipt, review_key, require_session_scene, write_receipt  # noqa: E402


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    pass_id = str(payload.get("pass_id", ""))
    decision = str(payload.get("decision", ""))
    if decision not in {"continue", "rollback"}:
        raise ValueError("Visual decision must be exactly continue or rollback.")
    protocol, protocol_hash, _working = require_session_scene(payload)
    if pass_id not in {entry["id"] for entry in protocol["passes"]}:
        raise ValueError("Review pass is not declared by the checked-in protocol.")
    completed = read_receipt(pass_id)
    if completed is None or completed.get("kind") != "direct_mesh_retopology":
        raise ValueError("Capture the completed pass audit before recording its visual decision.")
    key = review_key(pass_id)
    if read_receipt(key) is not None:
        raise ValueError("The visual decision for this direct pass is immutable.")
    receipt = write_receipt(key, {
        "schema": "pale_mirror_visuals.collector_direct_receipt.v1",
        "session_id": protocol["session_id"],
        "kind": "independent_visual_decision",
        "pass_id": pass_id,
        "decision": decision,
        "protocol_sha256": protocol_hash,
        "audit": completed["audit"],
        "rule": "record only after an independent reference-plus-diagnostic review; contour metrics do not decide this value",
        "runtime_export_forbidden": True,
    })
    return {"asset_id": ASSET_ID, "pass_id": pass_id, "decision": decision, "receipt": receipt.as_posix(), "required_next": "begin next pass" if decision == "continue" else "rollback_collector_direct_mesh_pass"}


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "pass_id": "primary_composition_v01", "decision": "rollback"}))
