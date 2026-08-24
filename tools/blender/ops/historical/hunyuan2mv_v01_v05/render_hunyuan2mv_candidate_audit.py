"""Render one fixed, non-exportable Hunyuan3D-2mv proposal."""

from __future__ import annotations

from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
sys.path.insert(1, str(Path(__file__).resolve().parent.parents[1]))
from import_hunyuan2mv_candidate import candidate_spec  # noqa: E402
from common import ASSET_ID, asset_id, ensure_audit_stage, ensure_camera  # noqa: E402
from render_audit import _render_primary_trace  # noqa: E402

import bpy


_LABEL = re.compile(r"^[a-z0-9_-]{1,80}$")


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    spec = candidate_spec(payload)
    label = str(payload.get("label", spec.candidate_id))
    if not _LABEL.fullmatch(label):
        raise ValueError("Audit label must contain only lowercase letters, digits, underscores or dashes.")
    if not spec.review_source.is_file():
        raise FileNotFoundError("Hunyuan2mv review scene is missing; import the pinned proposal first.")
    if bpy.data.filepath != str(spec.review_source):
        bpy.ops.wm.open_mainfile(filepath=str(spec.review_source))
    scene = bpy.context.scene
    if (
        scene.get("pm_candidate_id") != spec.candidate_id
        or scene.get("pm_candidate_stage") != spec.stage
        or scene.get("pm_runtime_export_forbidden") is not True
    ):
        raise ValueError("Current scene is not the selected non-exportable Hunyuan2mv proposal.")
    cameras = _candidate_audit_cameras(spec)
    target = spec.root / "audits" / label
    target.mkdir(parents=True, exist_ok=True)
    outputs: dict[str, str] = {}
    for view, camera in cameras.items():
        output = target / f"{view}.png"
        scene.camera = camera
        scene.render.filepath = str(output)
        bpy.ops.render.render(write_still=True)
        outputs[view] = output.as_posix()
    outputs["silhouette_primary"] = _render_silhouette(scene, cameras["primary"], target)
    outputs["primary_trace"] = _render_primary_trace(scene, cameras["primary"], target)
    bpy.ops.wm.save_as_mainfile(filepath=str(spec.review_source))
    return {
        "asset_id": ASSET_ID,
        "candidate_id": spec.candidate_id,
        "label": label,
        "outputs": outputs,
        "stage": scene["pm_candidate_stage"],
        "runtime_export_forbidden": True,
    }


def _render_silhouette(scene, camera, target: Path) -> str:
    material = bpy.data.materials.get("PM_HUNYUAN2MV_AUDIT_SILHOUETTE")
    if material is None:
        material = bpy.data.materials.new("PM_HUNYUAN2MV_AUDIT_SILHOUETTE")
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


def _candidate_audit_cameras(spec) -> dict[str, bpy.types.Object]:
    """Frame all review-only volume proposals without changing the primary map."""
    standard = ensure_audit_stage()
    objects = [
        object_
        for object_ in bpy.data.objects
        if object_.type == "MESH"
        and object_.get("pm_candidate_id") == spec.candidate_id
        and not object_.get("pm_hunyuan_reference_only")
    ]
    if not objects:
        raise ValueError("Selected Hunyuan2mv review scene has no tagged proposal mesh.")
    vertices = [object_.matrix_world @ vertex.co for object_ in objects for vertex in object_.data.vertices]
    minimum = [min(vertex[index] for vertex in vertices) for index in range(3)]
    maximum = [max(vertex[index] for vertex in vertices) for index in range(3)]
    centre = tuple((left + right) * 0.5 for left, right in zip(minimum, maximum))
    span = max(right - left for left, right in zip(minimum, maximum))
    orbit = max(52.0, span * 1.75)
    diagonal = orbit * 0.70710678
    height = max(7.0, (maximum[2] - minimum[2]) * 0.52)
    target = (centre[0], centre[1], centre[2])
    return {
        "primary": standard["primary"],
        "opposite": ensure_camera(
            "PM_HUNYUAN2MV_AUDIT_OPPOSITE", (centre[0], centre[1] + orbit, centre[2] + height), target
        ),
        "front": ensure_camera(
            "PM_HUNYUAN2MV_AUDIT_FRONT", (centre[0] - diagonal, centre[1] - diagonal, centre[2] + height), target
        ),
        "side": ensure_camera(
            "PM_HUNYUAN2MV_AUDIT_SIDE", (centre[0] + diagonal, centre[1] - diagonal, centre[2] + height), target
        ),
        "elevated_rear": ensure_camera(
            "PM_HUNYUAN2MV_AUDIT_ELEVATED_REAR",
            (centre[0] + diagonal * 0.82, centre[1] + diagonal * 0.82, centre[2] + height * 2.6),
            target,
        ),
    }
