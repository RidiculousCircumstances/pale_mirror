"""Shared, fail-closed helpers for the unaccepted Collector v05 sculpt branch."""

from __future__ import annotations

from collections import defaultdict, deque
import hashlib
import json
from pathlib import Path
from typing import Any, Iterable

import bpy

from common import (
    ASSET_ID,
    LOD0_NAME,
    PINNED_REFERENCE_SHA256,
    REFERENCE_PLANE_NAME,
    TRACE_GUIDE_COLLECTION,
    TRACE_GUIDE_NAME,
    TRACE_ID,
    WORKSPACE,
    collection,
    load_primary_trace,
    organic_material,
)


SESSION_ID = "collector_v05_sculpt_v03_composition"
CANDIDATE_ID = "hunyuan2mv_v05_canonical_turntable"
PHASE = "collector_v05_sculpt_branch_v03"
PROTOCOL_PATH = WORKSPACE / "sculpt_protocol" / f"{SESSION_ID}.json"
SCULPT_SOURCE_PATH = WORKSPACE / "assets" / "harvester" / "biomass_collector_sculpt_v03_composition.blend"
SCULPT_AUDIT_ROOT = WORKSPACE / "sculpt_sessions" / SESSION_ID / "audits"
RAW_COLLECTION = "PM_COLLECTOR_SCULPT_RAW_V05"
BACKUP_COLLECTION = "PM_COLLECTOR_SCULPT_BACKUPS"
RAW_OBJECT = "collector_v05_raw_immutable"
WORKING_OBJECT = "collector_v05_sculpt_working"


def load_protocol() -> tuple[dict[str, Any], str]:
    encoded = PROTOCOL_PATH.read_bytes()
    digest = hashlib.sha256(encoded).hexdigest()
    value: dict[str, Any] = json.loads(encoded.decode("utf-8"))
    if value.get("schema") != "pale_mirror_visuals.collector_sculpt_session.v1":
        raise ValueError("Collector sculpt protocol has an unsupported schema.")
    if value.get("session_id") != SESSION_ID or value.get("asset_id") != ASSET_ID:
        raise ValueError("Collector sculpt protocol has an invalid identity.")
    candidate = value.get("candidate")
    if not isinstance(candidate, dict) or candidate.get("id") != CANDIDATE_ID:
        raise ValueError("Collector sculpt protocol does not name the locked v05 candidate.")
    if not isinstance(candidate.get("sha256"), str) or len(candidate["sha256"]) != 64:
        raise ValueError("Collector sculpt protocol does not pin the raw candidate hash.")
    return value, digest


def candidate_root() -> Path:
    return WORKSPACE / "candidates" / ASSET_ID / CANDIDATE_ID


def load_candidate_manifest(protocol: dict[str, Any]) -> tuple[dict[str, Any], Path]:
    root = candidate_root()
    manifest_path = root / "manifest.json"
    candidate_path = root / "candidate.glb"
    if not manifest_path.is_file() or not candidate_path.is_file():
        raise FileNotFoundError("The hash-pinned v05 candidate is unavailable in the controlled workspace.")
    manifest: dict[str, Any] = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    output = manifest.get("output")
    expected = protocol["candidate"]["sha256"]
    if (
        manifest.get("candidate_id") != CANDIDATE_ID
        or manifest.get("stage") != "volume-proposal-unaccepted"
        or not isinstance(output, dict)
        or output.get("file") != "candidate.glb"
        or output.get("sha256") != expected
        or sha256(candidate_path) != expected
    ):
        raise ValueError("The v05 candidate differs from the sculpt protocol pin.")
    return manifest, candidate_path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def prepare_trace_guides() -> tuple[dict[str, Any], str]:
    trace, trace_hash = load_primary_trace()
    if trace["source"].get("sha256") != PINNED_REFERENCE_SHA256:
        raise ValueError("Collector sculpt branch does not use the pinned supplied reference.")
    if trace["trace_id"] != TRACE_ID:
        raise ValueError("Collector sculpt branch does not use the active primary trace.")
    return trace, trace_hash


def branch_collections() -> tuple[bpy.types.Collection, bpy.types.Collection, bpy.types.Collection]:
    return collection(LOD0_NAME), collection(RAW_COLLECTION), collection(BACKUP_COLLECTION)


def neutral_clay() -> bpy.types.Material:
    return organic_material("PM_COLLECTOR_SCULPT_NEUTRAL_CLAY", (0.23, 0.045, 0.055), 0.78)


def require_branch_scene() -> tuple[dict[str, Any], str, bpy.types.Object]:
    if bpy.data.filepath != str(SCULPT_SOURCE_PATH):
        if not SCULPT_SOURCE_PATH.is_file():
            raise FileNotFoundError("Collector sculpt v03 composition branch does not exist; create it from the locked v05 candidate.")
        bpy.ops.wm.open_mainfile(filepath=str(SCULPT_SOURCE_PATH))
    protocol, protocol_hash = load_protocol()
    trace, trace_hash = prepare_trace_guides()
    scene = bpy.context.scene
    if scene.get("pm_authoring_phase") != PHASE:
        raise ValueError("Current Blender source is not the Collector v03 composition sculpt branch.")
    if scene.get("pm_sculpt_protocol_sha256") != protocol_hash:
        raise ValueError("Collector sculpt source was created by a different protocol revision.")
    if scene.get("pm_sculpt_candidate_sha256") != protocol["candidate"]["sha256"]:
        raise ValueError("Collector sculpt source does not retain the locked v05 candidate hash.")
    if scene.get("pm_primary_trace_id") != trace["trace_id"] or scene.get("pm_primary_trace_sha256") != trace_hash:
        raise ValueError("Collector sculpt source has drifted from the primary trace.")
    raw = bpy.data.objects.get(RAW_OBJECT)
    working = bpy.data.objects.get(WORKING_OBJECT)
    if raw is None or working is None or raw.type != "MESH" or working.type != "MESH":
        raise ValueError("Collector sculpt branch is missing the raw or working mesh.")
    if raw.get("pm_sculpt_raw_immutable") is not True or not raw.get("pm_export_exclude"):
        raise ValueError("Collector raw v05 reference is not protected.")
    if working.get("pm_sculpt_working") is not True:
        raise ValueError("Collector working mesh is not tagged as the sculpt target.")
    if bpy.data.objects.get(TRACE_GUIDE_NAME) is None or bpy.data.objects.get(REFERENCE_PLANE_NAME) is None:
        raise ValueError("Collector sculpt branch is missing its locked primary guide.")
    return protocol, protocol_hash, working


def connected_face_components(mesh: bpy.types.Mesh) -> list[list[int]]:
    vertex_faces: dict[int, list[int]] = defaultdict(list)
    for polygon in mesh.polygons:
        for vertex in polygon.vertices:
            vertex_faces[vertex].append(polygon.index)
    unseen = set(range(len(mesh.polygons)))
    components: list[list[int]] = []
    while unseen:
        start = min(unseen)
        unseen.remove(start)
        queue: deque[int] = deque((start,))
        component: list[int] = []
        while queue:
            polygon_index = queue.popleft()
            component.append(polygon_index)
            for vertex in mesh.polygons[polygon_index].vertices:
                for adjacent in vertex_faces[vertex]:
                    if adjacent in unseen:
                        unseen.remove(adjacent)
                        queue.append(adjacent)
        components.append(sorted(component))
    return sorted(components, key=lambda part: (-len(part), part[0]))


def component_summary(mesh: bpy.types.Mesh, components: list[list[int]]) -> list[dict[str, Any]]:
    summary: list[dict[str, Any]] = []
    for index, faces in enumerate(components):
        vertices = sorted({vertex for face in faces for vertex in mesh.polygons[face].vertices})
        coords = [mesh.vertices[vertex].co for vertex in vertices]
        summary.append(
            {
                "index": index,
                "faces": len(faces),
                "vertices": len(vertices),
                "bounds": {
                    "min": [min(point[axis] for point in coords) for axis in range(3)],
                    "max": [max(point[axis] for point in coords) for axis in range(3)],
                },
            }
        )
    return summary


def copy_component_mesh(source: bpy.types.Object, faces: Iterable[int], target: bpy.types.Collection) -> bpy.types.Object:
    ordered_faces = sorted(faces)
    source_vertices = sorted({vertex for face in ordered_faces for vertex in source.data.polygons[face].vertices})
    remap = {source_vertex: index for index, source_vertex in enumerate(source_vertices)}
    matrix = source.matrix_world.copy()
    vertices = [tuple(matrix @ source.data.vertices[index].co) for index in source_vertices]
    polygons = [tuple(remap[index] for index in source.data.polygons[face].vertices) for face in ordered_faces]
    mesh = bpy.data.meshes.new(f"{WORKING_OBJECT}_mesh")
    mesh.from_pydata(vertices, [], polygons)
    mesh.validate(clean_customdata=True)
    mesh.update()
    object_ = bpy.data.objects.new(WORKING_OBJECT, mesh)
    target.objects.link(object_)
    material = neutral_clay()
    mesh.materials.append(material)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    return object_


def new_backup(working: bpy.types.Object, pass_id: str) -> bpy.types.Object:
    backups = collection(BACKUP_COLLECTION)
    name = f"{WORKING_OBJECT}_before_{pass_id}"
    existing = bpy.data.objects.get(name)
    if existing is not None:
        # A failed pre-link operation can leave an orphaned datablock inside a
        # live Blender process. It was never part of the scene or evidence;
        # clean up only that exact orphan, never an actual stored backup.
        if existing.users_collection:
            raise ValueError(f"Collector sculpt pass {pass_id} was already started; restore or create a new session.")
        bpy.data.objects.remove(existing, do_unlink=True)
    copy = working.copy()
    copy.data = working.data.copy()
    copy.name = name
    copy.data.name = f"{name}_mesh"
    backups.objects.link(copy)
    copy.hide_render = True
    copy.hide_viewport = True
    copy.pop("pm_sculpt_working", None)
    copy["pm_export_exclude"] = True
    copy["pm_sculpt_backup_for"] = pass_id
    return copy


def ensure_semantic_group(working: bpy.types.Object, name: str, vertices: Iterable[int]) -> list[int]:
    indices = sorted(set(vertices))
    if not indices:
        raise ValueError(f"Collector sculpt semantic group {name} selected no vertices.")
    group = working.vertex_groups.get(name) or working.vertex_groups.new(name=name)
    group.remove(range(len(working.data.vertices)))
    group.add(indices, 1.0, "REPLACE")
    return indices


def receipt_path(pass_id: str) -> Path:
    return SCULPT_AUDIT_ROOT.parent / "receipts" / f"{pass_id}.json"


def write_receipt(pass_id: str, data: dict[str, Any]) -> Path:
    target = receipt_path(pass_id)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return target


def read_receipt(pass_id: str) -> dict[str, Any] | None:
    target = receipt_path(pass_id)
    return json.loads(target.read_text(encoding="utf-8")) if target.is_file() else None
