"""Capture the unchanged v05 working-copy baseline before direct editing."""

from __future__ import annotations

from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, mesh_digest, read_receipt, require_session_scene, write_receipt  # noqa: E402
from render_collector_direct_audit import main as render  # noqa: E402


KEY = re.compile(r"^[a-z0-9_]{1,80}$")


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, protocol_hash, working = require_session_scene(payload)
    baseline_key = str(payload.get("baseline_key", protocol.get("baseline_receipt", "raw_baseline")))
    if not KEY.fullmatch(baseline_key) or baseline_key != protocol.get("baseline_receipt", "raw_baseline"):
        raise ValueError("Direct-session baseline key is not declared by the checked-in protocol.")
    if read_receipt(baseline_key) is not None:
        raise ValueError("Direct-session baseline already exists; create a new session to replace it.")
    result = render({"asset_id": ASSET_ID, "session_id": protocol["session_id"], "label": baseline_key})
    receipt = write_receipt(baseline_key, {
        "schema": "pale_mirror_visuals.collector_direct_receipt.v1",
        "session_id": protocol["session_id"],
        "kind": "audit_only",
        "protocol_sha256": protocol_hash,
        "working_mesh_sha256": mesh_digest(working),
        "audit": result["outputs"],
        "runtime_export_forbidden": True,
    })
    result["receipt"] = receipt.as_posix()
    return result


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
