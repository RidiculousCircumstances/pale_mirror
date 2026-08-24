"""Read-only structural inspection for the controlled Collector source."""

from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import ASSET_ID, SOURCE_PATH, asset_id, collector_objects, ensure_audit_stage, triangle_count  # noqa: E402

import bpy
from bpy_extras.object_utils import world_to_camera_view


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError("Collector canonical source does not exist; inspect the active direct-v05 session instead.")
    if bpy.data.filepath != str(SOURCE_PATH):
        bpy.ops.wm.open_mainfile(filepath=str(SOURCE_PATH))
    primary_camera = ensure_audit_stage()["primary"]
    objects = []
    for lod in (0, 1):
        for object_ in collector_objects(lod):
            world_vertices = [object_.matrix_world @ vertex.co for vertex in object_.data.vertices]
            objects.append(
                {
                    "lod": lod,
                    "name": object_.name,
                    "triangles": triangle_count([object_]),
                    "export_exclude": bool(object_.get("pm_export_exclude")),
                    "hide_render": object_.hide_render,
                    "volume": bool(object_.get("pm_volume_behind_primary_trace")),
                    "trace_role": object_.get("pm_trace_role"),
                    "bounds": {
                        "min": [round(min(vertex[index] for vertex in world_vertices), 4) for index in range(3)],
                        "max": [round(max(vertex[index] for vertex in world_vertices), 4) for index in range(3)],
                    },
                    "primary_screen_bounds": _primary_screen_bounds(bpy.context.scene, primary_camera, world_vertices),
                }
            )
    return {
        "asset_id": ASSET_ID,
        "authoring_phase": bpy.context.scene.get("pm_authoring_phase"),
        "objects": objects,
        "runtime_triangles": sum(item["triangles"] for item in objects if not item["export_exclude"]),
    }


def _primary_screen_bounds(
    scene: bpy.types.Scene,
    camera: bpy.types.Object,
    vertices: list[object],
) -> dict[str, list[float]]:
    projected = [world_to_camera_view(scene, camera, vertex) for vertex in vertices]
    return {
        "min": [round(min(point.x for point in projected), 4), round(min(point.y for point in projected), 4)],
        "max": [round(max(point.x for point in projected), 4), round(max(point.y for point in projected), 4)],
    }
