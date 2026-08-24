"""Export the controlled static PMMesh v1 staging asset from Blender geometry."""

from __future__ import annotations

from pathlib import Path
import hashlib
import json
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import ASSET_ID, EXPORT_PATH, RIG_NAME, SOURCE_PATH, asset_id, collector_objects, ensure_directories, result_path  # noqa: E402

import bpy


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    ensure_directories()
    if not SOURCE_PATH.is_file():
        raise FileNotFoundError("Collector canonical source does not exist; an unaccepted direct-v05 session cannot export.")
    if bpy.data.filepath != str(SOURCE_PATH):
        bpy.ops.wm.open_mainfile(filepath=str(SOURCE_PATH))
    if bpy.context.scene.get("pm_authoring_phase") != "accepted":
        raise ValueError("PMMesh export is blocked until the primary trace and visual acceptance are explicitly complete.")
    meshes = [
        _mesh_payload(object_, lod)
        for lod in (0, 1)
        for object_ in collector_objects(lod)
        if not object_.get("pm_export_exclude")
    ]
    if not meshes:
        raise ValueError("No Collector meshes are available for PMMesh export.")
    bounds = _bounds(meshes)
    payload = {
        "schema": "pale_mirror.pmmesh.v1",
        "asset_id": ASSET_ID,
        "texture": "pale_mirror_visuals:textures/entity/harvester/biomass_collector.png",
        "bounds": bounds,
        "meshes": meshes,
        "skeleton": _skeleton(),
        "animations": {},
    }
    serialized = json.dumps(payload, sort_keys=True, separators=(",", ":")) + "\n"
    EXPORT_PATH.write_text(serialized, encoding="utf-8")
    return {
        "asset_id": ASSET_ID,
        "export": result_path(EXPORT_PATH),
        "sha256": hashlib.sha256(serialized.encode("utf-8")).hexdigest(),
        "mesh_count": len(meshes),
        "triangle_count": sum(len(mesh["indices"]) // 3 for mesh in meshes),
    }


def _mesh_payload(object_: bpy.types.Object, lod: int) -> dict[str, object]:
    mesh = object_.data
    mesh.calc_loop_triangles()
    positions = [_pm_position(object_.matrix_world @ vertex.co) for vertex in mesh.vertices]
    normals = [_pm_normal(object_.matrix_world.to_3x3() @ vertex.normal) for vertex in mesh.vertices]
    uvs = _vertex_uvs(mesh)
    indices = [vertex for triangle in mesh.loop_triangles for vertex in triangle.vertices]
    return {
        "name": object_.name,
        "lod": lod,
        "positions": positions,
        "normals": normals,
        "uvs": uvs,
        "indices": indices,
        "joints": [[0, 0, 0, 0] for _ in positions],
        "weights": [[1.0, 0.0, 0.0, 0.0] for _ in positions],
    }


def _pm_position(vector) -> list[float]:
    return [round(vector.x, 6), round(vector.z, 6), round(-vector.y, 6)]


def _pm_normal(vector) -> list[float]:
    normalized = vector.normalized()
    return [round(normalized.x, 6), round(normalized.z, 6), round(-normalized.y, 6)]


def _vertex_uvs(mesh: bpy.types.Mesh) -> list[list[float]]:
    result = [[0.0, 0.0] for _ in mesh.vertices]
    layer = mesh.uv_layers.active
    if layer is None:
        return result
    for polygon in mesh.polygons:
        for loop_index in polygon.loop_indices:
            vertex_index = mesh.loops[loop_index].vertex_index
            uv = layer.data[loop_index].uv
            result[vertex_index] = [round(uv.x, 6), round(uv.y, 6)]
    return result


def _bounds(meshes: list[dict[str, object]]) -> dict[str, list[float]]:
    positions = [position for mesh in meshes for position in mesh["positions"]]
    return {
        "min": [min(point[index] for point in positions) for index in range(3)],
        "max": [max(point[index] for point in positions) for index in range(3)],
    }


def _skeleton() -> dict[str, object]:
    rig = bpy.data.objects.get(RIG_NAME)
    if rig is None or rig.type != "ARMATURE":
        raise ValueError("Collector root rig is missing.")
    return {
        "bones": [
            {
                "name": bone.name,
                "parent": bone.parent.name if bone.parent else None,
                "head": _pm_position(rig.matrix_world @ bone.head_local),
                "tail": _pm_position(rig.matrix_world @ bone.tail_local),
            }
            for bone in rig.data.bones
        ]
    }
