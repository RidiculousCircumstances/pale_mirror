"""Fork one reviewed Collector direct-mesh session into its declared continuation.

The operation copies the protected Blender source, not geometry from a new
generator.  The target protocol must declare the exact reviewed parent in the
checked-in session catalog; callers cannot select files or protocol paths.
"""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import (  # noqa: E402
    ASSET_ID, mesh_digest, load_protocol, read_receipt, require_session_scene,
    source_path, write_receipt,
)

import bpy


DEFAULT_FROM_SESSION = "collector_v05_direct_mesh_v01"
DEFAULT_TO_SESSION = "collector_v05_direct_mesh_v02"


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    from_session = str(payload.get("from_session", DEFAULT_FROM_SESSION))
    to_session = str(payload.get("to_session", DEFAULT_TO_SESSION))
    if from_session == to_session:
        raise ValueError("A Collector direct continuation must use a distinct declared target session.")
    source_protocol, _source_hash, working = require_session_scene({"session_id": from_session})
    target_protocol, target_hash = load_protocol(to_session)
    parent = target_protocol.get("parent_session")
    if not isinstance(parent, dict) or parent.get("id") != from_session:
        raise ValueError("The target direct session does not declare this reviewed parent.")
    if parent.get("reviewed_working_mesh_sha256") != mesh_digest(working):
        raise ValueError("The v01 working mesh no longer matches the target continuation pin.")
    if read_receipt(str(source_protocol.get("baseline_receipt", "raw_baseline")), from_session) is None:
        raise ValueError("The parent direct session lacks its protected baseline receipt.")
    for declaration in source_protocol["passes"]:
        pass_id = declaration["id"]
        completed = read_receipt(pass_id, from_session)
        if completed is None:
            continue
        review = read_receipt(f"review_{pass_id}", from_session)
        if review is None or review.get("decision") not in {"continue", "rollback"}:
            raise ValueError(f"The parent direct pass {pass_id} lacks an explicit visual decision.")
        if review.get("decision") == "rollback" and read_receipt(f"{pass_id}_rollback", from_session) is None:
            raise ValueError(f"The rejected parent direct pass {pass_id} lacks an exact rollback receipt.")
    target = source_path(target_protocol)
    if target.exists():
        raise ValueError("The declared Collector continuation source already exists and may not be overwritten.")
    target.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(target))
    scene = bpy.context.scene
    scene["pm_authoring_phase"] = to_session
    scene["pm_direct_session_id"] = to_session
    scene["pm_direct_protocol_sha256"] = target_hash
    scene["pm_direct_parent_session"] = from_session
    scene["pm_direct_parent_working_mesh_sha256"] = mesh_digest(working)
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_runtime_export_forbidden"] = True
    bpy.ops.wm.save_as_mainfile(filepath=str(target))
    receipt = write_receipt("session_continued", {
        "schema": "pale_mirror_visuals.collector_direct_receipt.v1",
        "session_id": to_session,
        "kind": "reviewed_direct_session_continuation",
        "parent_session": from_session,
        "parent_working_mesh_sha256": mesh_digest(working),
        "protocol_sha256": target_hash,
        "runtime_export_forbidden": True,
    }, to_session)
    return {
        "asset_id": ASSET_ID,
        "from_session": from_session,
        "to_session": to_session,
        "source": str(target),
        "receipt": receipt.as_posix(),
        "runtime_export_forbidden": True,
    }


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
