"""Render the pinned SF3D Collector proposal without opening canonical source."""

from __future__ import annotations

from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from import_sf3d_candidate import CANDIDATE_ID, CANDIDATE_ROOT, REVIEW_SOURCE  # noqa: E402
from common import ASSET_ID, asset_id, ensure_audit_stage  # noqa: E402

import bpy


_LABEL = re.compile(r"^[a-z0-9_-]{1,80}$")


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if payload.get("candidate_id", CANDIDATE_ID) != CANDIDATE_ID:
        raise ValueError("Only the pinned sf3d_v01 proposal may be rendered.")
    label = str(payload.get("label", "sf3d_v01"))
    if not _LABEL.fullmatch(label):
        raise ValueError("Audit label must contain only lowercase letters, digits, underscores or dashes.")
    if not REVIEW_SOURCE.is_file():
        raise FileNotFoundError("SF3D review scene is missing; import the pinned proposal first.")
    if bpy.data.filepath != str(REVIEW_SOURCE):
        bpy.ops.wm.open_mainfile(filepath=str(REVIEW_SOURCE))
    scene = bpy.context.scene
    if scene.get("pm_candidate_id") != CANDIDATE_ID or scene.get("pm_runtime_export_forbidden") is not True:
        raise ValueError("Current scene is not the pinned non-exportable SF3D proposal.")
    cameras = ensure_audit_stage()
    target = CANDIDATE_ROOT / "audits" / label
    target.mkdir(parents=True, exist_ok=True)
    outputs: dict[str, str] = {}
    for view, camera in cameras.items():
        output = target / f"{view}.png"
        scene.camera = camera
        scene.render.filepath = str(output)
        bpy.ops.render.render(write_still=True)
        outputs[view] = output.as_posix()
    outputs["silhouette_primary"] = _render_silhouette(scene, cameras["primary"], target)
    bpy.ops.wm.save_as_mainfile(filepath=str(REVIEW_SOURCE))
    return {
        "asset_id": ASSET_ID,
        "candidate_id": CANDIDATE_ID,
        "label": label,
        "outputs": outputs,
        "stage": scene["pm_candidate_stage"],
        "runtime_export_forbidden": True,
    }


def _render_silhouette(scene, camera, target: Path) -> str:
    material = bpy.data.materials.get("PM_SF3D_AUDIT_SILHOUETTE")
    if material is None:
        material = bpy.data.materials.new("PM_SF3D_AUDIT_SILHOUETTE")
        material.use_nodes = True
        shader = material.node_tree.nodes.get("Principled BSDF")
        shader.inputs["Base Color"].default_value = (1.0, 1.0, 1.0, 1.0)
        shader.inputs["Roughness"].default_value = 1.0
    ground = bpy.data.objects.get("PM_AUDIT_GROUND")
    previous_ground = ground.hide_render if ground else False
    previous_override = scene.view_layers[0].material_override
    previous_view_transform = scene.view_settings.view_transform
    background = scene.world.node_tree.nodes.get("Background")
    previous_color = tuple(background.inputs["Color"].default_value)
    previous_strength = background.inputs["Strength"].default_value
    try:
        if ground:
            ground.hide_render = True
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
        scene.view_layers[0].material_override = previous_override
        scene.view_settings.view_transform = previous_view_transform
        background.inputs["Color"].default_value = previous_color
        background.inputs["Strength"].default_value = previous_strength
