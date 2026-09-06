"""HISTORICAL REJECTED: render the locked seven-view v03 sculpt audit."""

from __future__ import annotations

from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_sculpt_common import (  # noqa: E402
    ASSET_ID,
    SCULPT_AUDIT_ROOT,
    SESSION_ID,
    WORKING_OBJECT,
    require_branch_scene,
)
from common import ensure_audit_stage  # noqa: E402
from render_audit import _render_primary_trace  # noqa: E402

import bpy


_LABEL = re.compile(r"^[a-z0-9_-]{1,80}$")


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    label = str(payload.get("label", "manual"))
    if not _LABEL.fullmatch(label):
        raise ValueError("Collector sculpt audit labels use only lowercase letters, digits, underscores and dashes.")
    return render(label)


def render(label: str) -> dict[str, object]:
    _protocol, _protocol_hash, working = require_branch_scene()
    if working.name != WORKING_OBJECT:
        raise ValueError("Collector sculpt audit found an unexpected working mesh.")
    cameras = ensure_audit_stage()
    target = SCULPT_AUDIT_ROOT / label
    target.mkdir(parents=True, exist_ok=True)
    scene = bpy.context.scene
    outputs: dict[str, str] = {}
    for view, camera in cameras.items():
        scene.camera = camera
        output = target / f"{view}.png"
        scene.render.filepath = str(output)
        bpy.ops.render.render(write_still=True)
        outputs[view] = output.as_posix()
    outputs["silhouette_primary"] = _render_silhouette(scene, cameras["primary"], target, working)
    outputs["primary_trace"] = _render_primary_trace(scene, cameras["primary"], target)
    bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath)
    return {"asset_id": ASSET_ID, "session_id": SESSION_ID, "label": label, "outputs": outputs, "runtime_export_forbidden": True}


def _render_silhouette(scene, camera, target: Path, working: bpy.types.Object) -> str:
    material = bpy.data.materials.get("PM_COLLECTOR_SCULPT_AUDIT_SILHOUETTE")
    if material is None:
        material = bpy.data.materials.new("PM_COLLECTOR_SCULPT_AUDIT_SILHOUETTE")
        material.use_nodes = True
        shader = material.node_tree.nodes.get("Principled BSDF")
        shader.inputs["Base Color"].default_value = (1.0, 1.0, 1.0, 1.0)
        shader.inputs["Roughness"].default_value = 1.0
    ground = bpy.data.objects.get("PM_AUDIT_GROUND")
    previous_ground = ground.hide_render if ground else False
    previous_override = scene.view_layers[0].material_override
    previous_transform = scene.view_settings.view_transform
    background = scene.world.node_tree.nodes.get("Background")
    previous_color = tuple(background.inputs["Color"].default_value)
    previous_strength = background.inputs["Strength"].default_value
    hidden = {object_: object_.hide_render for object_ in bpy.data.objects if object_.type == "MESH" and object_ != working}
    try:
        if ground:
            ground.hide_render = True
        for object_ in hidden:
            object_.hide_render = True
        scene.view_layers[0].material_override = material
        scene.view_settings.view_transform = "Standard"
        background.inputs["Color"].default_value = (0.0, 0.0, 0.0, 1.0)
        background.inputs["Strength"].default_value = 0.0
        output = target / "silhouette_primary.png"
        scene.camera = camera
        scene.render.filepath = str(output)
        bpy.ops.render.render(write_still=True)
        return output.as_posix()
    finally:
        if ground:
            ground.hide_render = previous_ground
        for object_, value in hidden.items():
            object_.hide_render = value
        scene.view_layers[0].material_override = previous_override
        scene.view_settings.view_transform = previous_transform
        background.inputs["Color"].default_value = previous_color
        background.inputs["Strength"].default_value = previous_strength


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID, "label": "manual"}))
