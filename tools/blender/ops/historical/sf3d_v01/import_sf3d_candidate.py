"""Import only the pinned SF3D Collector proposal into a disposable review scene.

This operation is deliberately not an asset converter. It imports the one
hash-pinned bake-off candidate into a distinct, non-exportable review `.blend`;
it never opens, changes or exports the canonical Collector source.
"""

from __future__ import annotations

from pathlib import Path
import hashlib
import json
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent.parents[1]))
from common import (  # noqa: E402
    ASSET_ID,
    TRACE_GUIDE_COLLECTION,
    TRACE_GUIDE_NAME,
    asset_id,
    collection,
    ensure_audit_stage,
    ensure_primary_reference_plane,
    fresh_scene,
    load_primary_trace,
    trace_profile_mesh,
)

import bpy


CANDIDATE_ID = "sf3d_v01"
CANDIDATE_STAGE = "volume-proposal-unaccepted"
CANDIDATE_TRACE_ID = "biomass_collector_primary_v02"
WORKSPACE = Path(__file__).resolve().parents[3]
CANDIDATE_ROOT = WORKSPACE / "candidates" / ASSET_ID / CANDIDATE_ID
CANDIDATE_PATH = CANDIDATE_ROOT / "candidate.glb"
CANDIDATE_MANIFEST = CANDIDATE_ROOT / "manifest.json"
REVIEW_SOURCE = CANDIDATE_ROOT / "collector_sf3d_v01_review.blend"
PROPOSAL_COLLECTION = "PM_COLLECTOR_SF3D_V01_PROPOSAL"
PROPOSAL_ROOT = "PM_COLLECTOR_SF3D_V01_ROOT"


def main(payload: dict[str, object]) -> dict[str, object]:
    asset_id(payload)
    if payload.get("candidate_id", CANDIDATE_ID) != CANDIDATE_ID:
        raise ValueError("Only the pinned sf3d_v01 proposal may enter this review scene.")
    manifest = _candidate_manifest()
    trace, trace_hash = load_primary_trace()
    if trace["trace_id"] != CANDIDATE_TRACE_ID:
        raise ValueError(
            "sf3d_v01 belongs to the superseded v02 camera contract and is historical review evidence only."
        )
    fresh_scene()
    guide = collection(TRACE_GUIDE_COLLECTION)
    guide_profile = trace_profile_mesh(guide, TRACE_GUIDE_NAME, trace, _guide_material(), depth=-0.08)
    guide_profile.hide_render = True
    guide_profile.hide_set(True)
    guide_profile["pm_trace_role"] = "locked_primary_overlay_guide"
    ensure_primary_reference_plane(trace)

    proposal = collection(PROPOSAL_COLLECTION)
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=str(CANDIDATE_PATH))
    imported = [object_ for object_ in bpy.data.objects if object_ not in before and object_.type == "MESH"]
    if not imported:
        raise ValueError("Pinned SF3D candidate imported no mesh objects.")
    for object_ in imported:
        for current in list(object_.users_collection):
            current.objects.unlink(object_)
        proposal.objects.link(object_)
        object_["pm_export_exclude"] = True
        object_["pm_candidate_id"] = CANDIDATE_ID
        object_["pm_candidate_stage"] = CANDIDATE_STAGE

    raw_bounds = _bounds(imported)
    trace_bounds = _trace_bounds(trace)
    root = bpy.data.objects.new(PROPOSAL_ROOT, None)
    proposal.objects.link(root)
    for object_ in imported:
        object_.parent = root
    _align_primary_profile(root, raw_bounds, trace_bounds)
    bpy.context.view_layer.update()
    normalized_bounds = _bounds(imported)
    topology = _topology(imported)
    ensure_audit_stage()

    scene = bpy.context.scene
    scene["pm_asset_id"] = ASSET_ID
    scene["pm_candidate_id"] = CANDIDATE_ID
    scene["pm_candidate_stage"] = CANDIDATE_STAGE
    scene["pm_candidate_sha256"] = manifest["output"]["sha256"]
    scene["pm_candidate_input_sha256"] = manifest["input_rgba_sha256"]
    scene["pm_primary_trace_id"] = trace["trace_id"]
    scene["pm_primary_trace_sha256"] = trace_hash
    scene["pm_visual_acceptance"] = "unaccepted"
    scene["pm_runtime_export_forbidden"] = True
    scene["pm_review_scope"] = "SF3D hidden-side volume proposal only; primary trace remains sole likeness authority"
    REVIEW_SOURCE.parent.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.save_as_mainfile(filepath=str(REVIEW_SOURCE))
    return {
        "asset_id": ASSET_ID,
        "candidate_id": CANDIDATE_ID,
        "stage": CANDIDATE_STAGE,
        "review_source": REVIEW_SOURCE.as_posix(),
        "candidate_sha256": manifest["output"]["sha256"],
        "raw_bounds": raw_bounds,
        "normalized_bounds": normalized_bounds,
        "trace_bounds": trace_bounds,
        "topology": topology,
        "runtime_export_forbidden": True,
    }


def _candidate_manifest() -> dict[str, object]:
    if not CANDIDATE_PATH.is_file() or not CANDIDATE_MANIFEST.is_file():
        raise FileNotFoundError("Pinned SF3D candidate/provenance is unavailable in the controlled workspace.")
    # PowerShell's UTF-8 writer may retain a BOM. It is representation-only
    # provenance metadata, never an input hash, so consume it explicitly.
    manifest = json.loads(CANDIDATE_MANIFEST.read_text(encoding="utf-8-sig"))
    if (
        manifest.get("schema") != "pale_mirror.sf3d_volume_candidate.v1"
        or manifest.get("asset_id") != ASSET_ID
        or manifest.get("candidate_id") != CANDIDATE_ID
        or manifest.get("stage") != CANDIDATE_STAGE
        or manifest.get("runtime_export_forbidden") is not True
    ):
        raise ValueError("SF3D candidate provenance does not match the fixed proposal contract.")
    output = manifest.get("output")
    if not isinstance(output, dict) or output.get("file") != "candidate.glb" or not isinstance(output.get("sha256"), str):
        raise ValueError("SF3D candidate provenance does not pin candidate.glb.")
    if _sha256(CANDIDATE_PATH) != output["sha256"]:
        raise ValueError("SF3D candidate differs from its immutable provenance hash.")
    return manifest


def _align_primary_profile(root, raw: dict[str, list[float]], target: dict[str, list[float]]) -> None:
    source_width = raw["max"][0] - raw["min"][0]
    target_width = target["max"][0] - target["min"][0]
    if source_width <= 0.0 or target_width <= 0.0:
        raise ValueError("SF3D candidate or trace has a degenerate primary width.")
    scale = target_width / source_width
    root.scale = (scale, scale, scale)
    root.location.x = (target["min"][0] + target["max"][0]) * 0.5 - (raw["min"][0] + raw["max"][0]) * 0.5 * scale
    root.location.y = -0.12 - raw["max"][1] * scale
    root.location.z = target["min"][2] - raw["min"][2] * scale


def _trace_bounds(trace: dict[str, object]) -> dict[str, list[float]]:
    mapping = trace["primary_camera_mapping"]
    units = float(mapping["world_units_per_pixel"])
    origin_x, origin_y = (int(value) for value in mapping["origin_pixel"])
    grid = int(trace["sampling"]["grid_px"])
    pixels = [(left, int(row["y"])) for row in trace["rows"] for left, _right in row["runs"]]
    pixels.extend((right, int(row["y"]) + grid) for row in trace["rows"] for _left, right in row["runs"])
    xs = [(x - origin_x) * units for x, _y in pixels]
    zs = [(origin_y - y) * units for _x, y in pixels]
    return {"min": [min(xs), 0.0, min(zs)], "max": [max(xs), 0.0, max(zs)]}


def _bounds(objects) -> dict[str, list[float]]:
    vertices = [object_.matrix_world @ vertex.co for object_ in objects for vertex in object_.data.vertices]
    if not vertices:
        raise ValueError("SF3D candidate has no vertices.")
    return {
        "min": [min(vertex[index] for vertex in vertices) for index in range(3)],
        "max": [max(vertex[index] for vertex in vertices) for index in range(3)],
    }


def _topology(objects) -> dict[str, int | bool]:
    edges: dict[tuple[float, ...], int] = {}
    faces = 0
    vertices = 0
    for object_ in objects:
        vertices += len(object_.data.vertices)
        faces += len(object_.data.polygons)
        for polygon in object_.data.polygons:
            indices = polygon.vertices[:]
            for first, second in zip(indices, (*indices[1:], indices[0])):
                points = sorted((object_.data.vertices[first].co, object_.data.vertices[second].co), key=lambda item: tuple(item))
                key = tuple(round(value, 7) for point in points for value in point)
                edges[key] = edges.get(key, 0) + 1
    boundary_edges = sum(1 for count in edges.values() if count == 1)
    non_manifold_edges = sum(1 for count in edges.values() if count != 2)
    return {
        "vertices": vertices,
        "faces": faces,
        "boundary_edges": boundary_edges,
        "non_manifold_edges": non_manifold_edges,
        "closed_manifold": non_manifold_edges == 0,
    }


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _guide_material():
    material = bpy.data.materials.get("PM_SF3D_TRACE_GUIDE")
    if material is not None:
        return material
    material = bpy.data.materials.new("PM_SF3D_TRACE_GUIDE")
    material.diffuse_color = (1.0, 1.0, 1.0, 1.0)
    return material
