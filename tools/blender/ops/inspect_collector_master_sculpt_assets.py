"""Read-only Blender 5.x sculpt-tool capability probe for Collector Master."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, mesh_digest, require_session_scene  # noqa: E402

import bpy


SESSION_ID = "collector_v05_production_master_v01"


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, _protocol_hash, working = require_session_scene(payload)
    if protocol["session_id"] != SESSION_ID:
        raise ValueError("This capability probe is restricted to Collector Master.")
    before_hash = mesh_digest(working)
    brushes = []
    for brush in sorted(bpy.data.brushes, key=lambda item: item.name):
        brushes.append({
            "name": brush.name,
            "properties": sorted(property_.identifier for property_ in brush.bl_rna.properties if not property_.is_readonly),
        })
    result = {
        "asset_id": ASSET_ID,
        "session_id": SESSION_ID,
        "working_mesh_sha256": before_hash,
        "mode": bpy.context.mode,
        "brushes": brushes,
        "brush_rna_properties": sorted(property_.identifier for property_ in bpy.types.Brush.bl_rna.properties),
        "wm_tool_set_by_id_available": hasattr(bpy.ops.wm, "tool_set_by_id"),
        "workspaces": sorted(workspace.name for workspace in bpy.data.workspaces),
    }
    if mesh_digest(working) != before_hash:
        raise ValueError("Sculpt-asset inspection changed protected Collector geometry.")
    return result


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "session_id": SESSION_ID}))
