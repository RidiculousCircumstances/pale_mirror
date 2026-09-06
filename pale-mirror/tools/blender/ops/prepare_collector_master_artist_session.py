"""Prepare the one approved visible Blender session for Collector Master.

This is custody and viewport preparation only.  It selects the declared
working surface, exposes the locked primary camera and activates Blender's
Grab brush, but proves that no mesh coordinate or topology changed.  Visible
pen input is deliberately a separate, narrowly allowlisted Windows action.
"""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_direct_common import ASSET_ID, WORKING_OBJECT, mesh_digest, prepare_trace_guides, require_session_scene  # noqa: E402
from common import REFERENCE_PATH, ensure_primary_reference_plane  # noqa: E402
from render_audit import ensure_audit_stage  # noqa: E402

import bpy


SESSION_ID = "collector_v05_production_master_v01"
PASS_ID = "production_master_form_v01"


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, _protocol_hash, working = require_session_scene(payload)
    if protocol["session_id"] != SESSION_ID:
        raise ValueError("The Collector Master artist preparation only opens the declared production session.")
    if bpy.context.scene.get("pm_direct_open_pass") != PASS_ID:
        raise ValueError("Open exactly production_master_form_v01 before preparing visible artist input.")
    if working.name != WORKING_OBJECT:
        raise ValueError("Collector Master found an unexpected editable object.")
    before_hash = mesh_digest(working)
    if bpy.context.mode != "OBJECT":
        bpy.ops.object.mode_set(mode="OBJECT")
    for object_ in bpy.context.view_layer.objects:
        object_.select_set(False)
    working.select_set(True)
    bpy.context.view_layer.objects.active = working
    stale = bpy.data.brushes.get("PM_COLLECTOR_MASTER_GRAB")
    if stale is not None and stale.users == 0:
        bpy.data.brushes.remove(stale)
    cameras = ensure_audit_stage()
    primary_camera = cameras.get("primary")
    if primary_camera is None:
        raise ValueError("Collector Master could not prepare the locked primary camera.")
    trace, _trace_hash = prepare_trace_guides()
    if not REFERENCE_PATH.is_file():
        raise FileNotFoundError("The pinned Collector primary image is unavailable to the artist workspace.")
    reference = bpy.data.images.load(str(REFERENCE_PATH), check_existing=True)
    primary_camera.data.background_images.clear()
    background = primary_camera.data.background_images.new()
    background.image = reference
    background.frame_method = "FIT"
    background.alpha = 0.86
    background.display_depth = "BACK"
    primary_camera.data.show_background_images = True
    primary_reference = ensure_primary_reference_plane(trace)
    primary_reference.hide_set(False)
    ground = bpy.data.objects.get("PM_AUDIT_GROUND")
    if ground is not None:
        ground.hide_set(True)
    bpy.context.scene.camera = primary_camera
    sculpting_workspace = bpy.data.workspaces.get("Sculpting")
    if sculpting_workspace is None:
        raise ValueError("The isolated Blender profile has no required Sculpting workspace.")
    bpy.context.window.workspace = sculpting_workspace
    view_areas = [area for area in bpy.context.window.screen.areas if area.type == "VIEW_3D"]
    if not view_areas:
        raise ValueError("Collector Master has no visible 3D viewport for artist input.")
    for area in view_areas:
        if area.type != "VIEW_3D":
            continue
        space = area.spaces.active
        space.overlay.show_overlays = False
        space.shading.type = "MATERIAL"
        space.region_3d.view_perspective = "CAMERA"
        space.region_3d.view_camera_zoom = 0
    bpy.ops.object.mode_set(mode="SCULPT")
    brush = bpy.context.tool_settings.sculpt.brush
    if mesh_digest(working) != before_hash:
        raise ValueError("Artist-session preparation changed protected Collector geometry.")
    bpy.context.scene["pm_collector_artist_input"] = {
        "schema": "pale_mirror.collector_master_artist_input.v1",
        "session_id": SESSION_ID,
        "pass_id": PASS_ID,
        "working_object": WORKING_OBJECT,
        "primary_camera": primary_camera.name,
        "primary_reference": REFERENCE_PATH.name,
        "brush": brush.name if brush else None,
        "brush_selection": "The visible Blender Sculpting workspace must select Grab through its own toolbar; code may not inject a hidden brush activation.",
        "geometry_changed": False,
    }
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {
        "asset_id": ASSET_ID,
        "session_id": SESSION_ID,
        "pass_id": PASS_ID,
        "working_mesh_sha256": before_hash,
        "mode": bpy.context.mode,
        "active_brush": brush.name if brush else None,
        "primary_camera": primary_camera.name,
        "primary_reference": REFERENCE_PATH.name,
        "geometry_changed": False,
        "required_next": "capture the visible Sculpting workspace, select Grab through its visible toolbar, then use only a named Collector Master artist stroke",
    }


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "session_id": SESSION_ID}))
