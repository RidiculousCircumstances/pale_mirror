"""Render deterministic Blender audit views for the canonical Collector source."""

from __future__ import annotations

from pathlib import Path
import re
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    AUDIT_ROOT,
    ASSET_ID,
    SOURCE_PATH,
    TRACE_GUIDE_NAME,
    asset_id,
    collector_objects,
    ensure_audit_stage,
    ensure_directories,
    load_primary_trace,
    result_path,
)

import bpy


_LABEL = re.compile(r"^[a-z0-9_-]{1,80}$")


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    label = str(payload.get("label", "manual"))
    if not _LABEL.fullmatch(label):
        raise ValueError("Audit label must contain only lowercase letters, digits, underscores or dashes.")
    ensure_directories()
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError("Collector canonical source does not exist; render the active direct-v05 audit instead.")
    if bpy.data.filepath != str(SOURCE_PATH):
        bpy.ops.wm.open_mainfile(filepath=str(SOURCE_PATH))
    cameras = ensure_audit_stage()
    target = AUDIT_ROOT / label
    target.mkdir(parents=True, exist_ok=True)
    outputs: dict[str, str] = {}
    scene = bpy.context.scene
    for view, camera in cameras.items():
        output = target / f"{view}.png"
        scene.camera = camera
        scene.render.filepath = str(output)
        bpy.ops.render.render(write_still=True)
        outputs[view] = result_path(output)
    outputs["silhouette_primary"] = _render_silhouette(scene, cameras["primary"], target)
    outputs["primary_trace"] = _render_primary_trace(scene, cameras["primary"], target)
    bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE_PATH))
    return {"asset_id": ASSET_ID, "label": label, "outputs": outputs}


def _render_silhouette(scene, camera, target: Path) -> str:
    white = bpy.data.materials.get("PM_AUDIT_SILHOUETTE")
    if white is None:
        white = bpy.data.materials.new("PM_AUDIT_SILHOUETTE")
        white.use_nodes = True
        nodes = white.node_tree.nodes
        links = white.node_tree.links
        nodes.clear()
        output = nodes.new("ShaderNodeOutputMaterial")
        emission = nodes.new("ShaderNodeEmission")
        emission.inputs["Color"].default_value = (1.0, 1.0, 1.0, 1.0)
        emission.inputs["Strength"].default_value = 1.0
        links.new(emission.outputs["Emission"], output.inputs["Surface"])
    ground = bpy.data.objects.get("PM_AUDIT_GROUND")
    previous_ground = ground.hide_render if ground else False
    previous_override = scene.view_layers[0].material_override
    # `hide_render` on locked reference planes is normally sufficient for the
    # lit audit.  A view-layer material override can expose stale guide/helper
    # meshes in some Blender versions, however, which would turn the direct
    # contour mask into a false full-frame silhouette.  The binary frame must
    # therefore show *only* current exportable LOD0 anatomy.
    visible = {object_ for object_ in collector_objects(0) if not object_.get("pm_export_exclude")}
    hidden = {
        object_: object_.hide_render
        for object_ in bpy.data.objects
        if object_.type == "MESH" and object_ not in visible
    }
    previous_view_transform = scene.view_settings.view_transform
    background = scene.world.node_tree.nodes.get("Background")
    previous_color = tuple(background.inputs["Color"].default_value)
    previous_strength = background.inputs["Strength"].default_value
    try:
        if ground:
            ground.hide_render = True
        for object_ in hidden:
            object_.hide_render = True
        scene.view_layers[0].material_override = white
        # Trace evidence is a binary construction artefact. Filmic/AgX turns
        # nominal black into mid-grey and would make an external mask reader
        # mistake the entire frame for creature geometry.
        scene.view_settings.view_transform = "Standard"
        background.inputs["Color"].default_value = (0.0, 0.0, 0.0, 1.0)
        background.inputs["Strength"].default_value = 0.0
        output = target / "silhouette_primary.png"
        scene.camera = camera
        scene.render.filepath = str(output)
        bpy.ops.render.render(write_still=True)
        return result_path(output)
    finally:
        if ground:
            ground.hide_render = previous_ground
        for object_, previous_hide_render in hidden.items():
            object_.hide_render = previous_hide_render
        scene.view_layers[0].material_override = previous_override
        scene.view_settings.view_transform = previous_view_transform
        background.inputs["Color"].default_value = previous_color
        background.inputs["Strength"].default_value = previous_strength


def _render_primary_trace(scene, camera, target: Path) -> str:
    """Project the actual locked guide mesh through the locked orthographic map.

    This is intentionally a geometry projection rather than a lit scene render:
    anti-aliasing, material backfaces and colour management must never move a
    source-pixel contour. The guide mesh is still the only input; its vertices
    and faces are projected by the same immutable camera mapping and written as
    a binary Blender image for the external reference overlay.
    """
    del scene, camera
    guide = bpy.data.objects.get(TRACE_GUIDE_NAME)
    if guide is None or guide.type != "MESH":
        raise ValueError("Primary trace guide is missing; rebuild from the pinned trace.")
    trace, _ = load_primary_trace()
    mapping = trace["primary_camera_mapping"]
    width, height = trace["source"]["size"]
    origin_x, origin_y = mapping["origin_pixel"]
    units = float(mapping["world_units_per_pixel"])
    pixels = [0.0] * (width * height * 4)
    for polygon in guide.data.polygons:
        points = [guide.matrix_world @ guide.data.vertices[index].co for index in polygon.vertices]
        left = round(min(point.x for point in points) / units + origin_x)
        right = round(max(point.x for point in points) / units + origin_x)
        top = round(origin_y - max(point.z for point in points) / units)
        bottom = round(origin_y - min(point.z for point in points) / units)
        for image_y in range(max(0, top), min(height, bottom)):
            # Blender's image buffer begins at its lower edge; the pinned
            # source image uses conventional top-left pixel coordinates.
            row_start = ((height - image_y - 1) * width + max(0, left)) * 4
            for image_x in range(max(0, left), min(width, right)):
                offset = row_start + (image_x - max(0, left)) * 4
                pixels[offset : offset + 4] = (1.0, 1.0, 1.0, 1.0)
    image = bpy.data.images.get("PM_AUDIT_PRIMARY_TRACE_MASK")
    if image is not None:
        bpy.data.images.remove(image)
    image = bpy.data.images.new("PM_AUDIT_PRIMARY_TRACE_MASK", width=width, height=height, alpha=True)
    image.pixels.foreach_set(pixels)
    image.filepath_raw = str(target / "primary_trace.png")
    image.file_format = "PNG"
    image.save()
    return result_path(target / "primary_trace.png")
