"""Fail-closed helpers for the active direct-v05 Collector workflow.

This module deliberately never manufactures primary anatomy from the trace.
The trace provides a locked guide and later audit only.  Every mutable vertex
belongs to an editable copy of the hash-pinned raw v05 candidate.
"""

from __future__ import annotations

from collections import defaultdict, deque
import hashlib
import json
from pathlib import Path
import re
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


SESSION_ID = "collector_v05_direct_mesh_v01"
CANDIDATE_ID = "hunyuan2mv_v05_canonical_turntable"
PHASE = "collector_v05_direct_mesh_v01"
PROTOCOL_PATH = WORKSPACE / "sculpt_protocol" / "collector_v05_direct_mesh_v01.json"
SOURCE_PATH = WORKSPACE / "assets" / "harvester" / "biomass_collector_v05_direct_mesh_v01.blend"
AUDIT_ROOT = WORKSPACE / "direct_mesh_sessions" / SESSION_ID / "audits"
RECEIPT_ROOT = AUDIT_ROOT.parent / "receipts"
SESSION_CATALOG_PATH = WORKSPACE / "sculpt_protocol" / "collector_direct_mesh_sessions_v01.json"
RAW_COLLECTION = "PM_COLLECTOR_DIRECT_RAW_V05"
BACKUP_COLLECTION = "PM_COLLECTOR_DIRECT_BACKUPS"
RAW_OBJECT = "collector_v05_raw_immutable"
WORKING_OBJECT = "collector_v05_direct_working"
SESSION_ID_PATTERN = re.compile(r"^[a-z0-9_]{1,96}$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def mesh_digest(object_: bpy.types.Object) -> str:
    if object_.type != "MESH":
        raise ValueError("A direct Collector receipt requires a mesh object.")
    payload = {
        "matrix_world": [[round(value, 6) for value in row] for row in object_.matrix_world],
        "vertices": [[round(value, 6) for value in vertex.co] for vertex in object_.data.vertices],
        "faces": [list(polygon.vertices) for polygon in object_.data.polygons],
    }
    return hashlib.sha256(json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")).hexdigest()


def _session_catalog() -> dict[str, Any]:
    catalog = json.loads(SESSION_CATALOG_PATH.read_text(encoding="utf-8"))
    if catalog.get("schema") != "pale_mirror_visuals.collector_direct_mesh_sessions.v1":
        raise ValueError("Collector direct-session catalog has an unsupported schema.")
    if catalog.get("asset_id") != ASSET_ID or not isinstance(catalog.get("sessions"), dict):
        raise ValueError("Collector direct-session catalog has an invalid identity.")
    return catalog


def session_id(payload: dict[str, object] | None = None) -> str:
    requested: object | None = None
    if payload is not None and "session_id" in payload:
        requested = payload["session_id"]
    elif bpy.context.scene.get("pm_direct_session_id") is not None:
        requested = bpy.context.scene["pm_direct_session_id"]
    else:
        requested = SESSION_ID
    if not isinstance(requested, str) or not SESSION_ID_PATTERN.fullmatch(requested):
        raise ValueError("Collector direct session id is invalid.")
    if requested not in _session_catalog()["sessions"]:
        raise ValueError("Collector direct session is not declared by the checked-in catalog.")
    return requested


def protocol_path(session: str) -> Path:
    entry = _session_catalog()["sessions"].get(session)
    if not isinstance(entry, dict) or not isinstance(entry.get("protocol"), str):
        raise ValueError("Collector direct-session catalog entry is invalid.")
    relative = Path(entry["protocol"])
    target = (WORKSPACE / relative).resolve()
    workspace = WORKSPACE.resolve()
    if not target.is_relative_to(workspace) or target.suffix != ".json" or not target.is_file():
        raise ValueError("Collector direct-session protocol path is unavailable or unsafe.")
    return target


def load_protocol(active_session: str | None = None) -> tuple[dict[str, Any], str]:
    active_session = session_id({"session_id": active_session}) if active_session is not None else session_id()
    encoded = protocol_path(active_session).read_bytes()
    protocol: dict[str, Any] = json.loads(encoded.decode("utf-8"))
    digest = hashlib.sha256(encoded).hexdigest()
    if protocol.get("schema") != "pale_mirror_visuals.collector_direct_mesh_session.v1":
        raise ValueError("Collector direct session has an unsupported schema.")
    if protocol.get("session_id") != active_session or protocol.get("asset_id") != ASSET_ID:
        raise ValueError("Collector direct session has an invalid identity.")
    candidate = protocol.get("candidate")
    if not isinstance(candidate, dict) or candidate.get("id") != CANDIDATE_ID or not isinstance(candidate.get("sha256"), str):
        raise ValueError("Collector direct session does not pin raw v05.")
    passes = protocol.get("passes")
    pass_ids = [entry.get("id") for entry in passes if isinstance(entry, dict)] if isinstance(passes, list) else []
    if not pass_ids or len(pass_ids) != len(set(pass_ids)) or any(not isinstance(value, str) or not value for value in pass_ids):
        raise ValueError("Collector direct session pass order is invalid.")
    return protocol, digest


def source_path(protocol: dict[str, Any]) -> Path:
    source = protocol.get("source")
    if not isinstance(source, dict) or not isinstance(source.get("blend"), str):
        raise ValueError("Collector direct session has no controlled Blender source path.")
    target = (WORKSPACE / Path(source["blend"])).resolve()
    workspace = WORKSPACE.resolve()
    if not target.is_relative_to(workspace) or target.suffix != ".blend":
        raise ValueError("Collector direct source path is unsafe.")
    return target


def audit_root(protocol: dict[str, Any]) -> Path:
    source = protocol.get("source")
    if not isinstance(source, dict) or not isinstance(source.get("audit_root"), str):
        raise ValueError("Collector direct session has no controlled audit path.")
    target = (WORKSPACE / Path(source["audit_root"])).resolve()
    workspace = WORKSPACE.resolve()
    if not target.is_relative_to(workspace):
        raise ValueError("Collector direct audit path is unsafe.")
    return target


def prepare_trace_guides() -> tuple[dict[str, Any], str]:
    trace, trace_hash = load_primary_trace()
    if trace.get("trace_id") != TRACE_ID or trace.get("source", {}).get("sha256") != PINNED_REFERENCE_SHA256:
        raise ValueError("Collector direct session does not use the pinned primary trace.")
    return trace, trace_hash


def candidate_path(protocol: dict[str, Any]) -> Path:
    root = WORKSPACE / "candidates" / ASSET_ID / CANDIDATE_ID
    manifest_path = root / "manifest.json"
    path = root / "candidate.glb"
    if not manifest_path.is_file() or not path.is_file():
        raise FileNotFoundError("The pinned v05 candidate is unavailable in the controlled workspace.")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    output = manifest.get("output")
    expected = protocol["candidate"]["sha256"]
    if not isinstance(output, dict) or manifest.get("candidate_id") != CANDIDATE_ID or output.get("sha256") != expected or sha256(path) != expected:
        raise ValueError("The v05 candidate differs from the direct-session pin.")
    return path


def normalise_primary_extent(raw: bpy.types.Object, trace: dict[str, Any]) -> None:
    """Match the raw v05 import to the locked primary frame once, before copying.

    This changes only the imported object's transform.  The original GLB stays
    hash-pinned on disk and the transformed Blender raw object is immediately
    made immutable with its own mesh-plus-transform digest.
    """
    vertices = [raw.matrix_world @ vertex.co for vertex in raw.data.vertices]
    if not vertices:
        raise ValueError("Pinned raw v05 candidate has no vertices.")
    mapping = trace["primary_camera_mapping"]
    units = float(mapping["world_units_per_pixel"])
    origin_x, origin_y = mapping["origin_pixel"]
    source_x = [(left - origin_x) * units for row in trace["rows"] for left, _right in row["runs"]]
    source_x.extend((right - origin_x) * units for row in trace["rows"] for _left, right in row["runs"])
    width = max(point.x for point in vertices) - min(point.x for point in vertices)
    target_width = max(source_x) - min(source_x)
    if width <= 0.0 or target_width <= 0.0:
        raise ValueError("Pinned raw v05 candidate has a degenerate primary extent.")
    scale = target_width / width
    raw.scale = tuple(value * scale for value in raw.scale)
    bpy.context.view_layer.update()
    scaled = [raw.matrix_world @ vertex.co for vertex in raw.data.vertices]
    raw.location.x += (min(source_x) + max(source_x)) * 0.5 - (min(point.x for point in scaled) + max(point.x for point in scaled)) * 0.5
    source_z = []
    for row in trace["rows"]:
        source_z.extend(((origin_y - row["y"]) * units, (origin_y - (row["y"] + trace["sampling"]["grid_px"])) * units))
    raw.location.z += min(source_z) - min(point.z for point in scaled)
    bpy.context.view_layer.update()


def direct_collections() -> tuple[bpy.types.Collection, bpy.types.Collection, bpy.types.Collection]:
    return collection(LOD0_NAME), collection(RAW_COLLECTION), collection(BACKUP_COLLECTION)


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
            face = queue.popleft()
            component.append(face)
            for vertex in mesh.polygons[face].vertices:
                for adjacent in vertex_faces[vertex]:
                    if adjacent in unseen:
                        unseen.remove(adjacent)
                        queue.append(adjacent)
        components.append(sorted(component))
    return sorted(components, key=lambda part: (-len(part), part[0]))


def copy_faces(source: bpy.types.Object, faces: Iterable[int], target: bpy.types.Collection) -> bpy.types.Object:
    ordered = sorted(faces)
    source_vertices = sorted({vertex for face in ordered for vertex in source.data.polygons[face].vertices})
    remap = {source_vertex: index for index, source_vertex in enumerate(source_vertices)}
    vertices = [tuple(source.matrix_world @ source.data.vertices[index].co) for index in source_vertices]
    polygons = [tuple(remap[index] for index in source.data.polygons[face].vertices) for face in ordered]
    mesh = bpy.data.meshes.new(f"{WORKING_OBJECT}_mesh")
    mesh.from_pydata(vertices, [], polygons)
    mesh.validate(clean_customdata=True)
    mesh.update()
    material = organic_material("PM_COLLECTOR_DIRECT_NEUTRAL_CLAY", (0.23, 0.045, 0.055), 0.78)
    mesh.materials.append(material)
    for polygon in mesh.polygons:
        polygon.use_smooth = True
    object_ = bpy.data.objects.new(WORKING_OBJECT, mesh)
    target.objects.link(object_)
    return object_


def receipt_root(active_session: str | None = None) -> Path:
    protocol, _digest = load_protocol(active_session)
    return audit_root(protocol).parent / "receipts"


def write_receipt(key: str, value: dict[str, Any], active_session: str | None = None) -> Path:
    target = receipt_root(active_session) / f"{key}.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return target


def read_receipt(key: str, active_session: str | None = None) -> dict[str, Any] | None:
    path = receipt_root(active_session) / f"{key}.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else None


def review_key(pass_id: str) -> str:
    return f"review_{pass_id}"


def backup(working: bpy.types.Object, pass_id: str) -> bpy.types.Object:
    backups = collection(BACKUP_COLLECTION)
    name = f"{WORKING_OBJECT}_before_{pass_id}"
    existing = bpy.data.objects.get(name)
    if existing is not None:
        raise ValueError(f"Direct pass {pass_id} already has a protected backup.")
    snapshot = working.copy()
    snapshot.data = working.data.copy()
    snapshot.name = name
    snapshot.data.name = f"{name}_mesh"
    backups.objects.link(snapshot)
    snapshot.hide_render = True
    snapshot.hide_viewport = True
    # ``Object.copy`` includes custom properties. A backup is immutable
    # evidence, never a second editable source, so it must not inherit the
    # working-surface ownership marker from its parent object.
    snapshot.pop("pm_direct_working", None)
    snapshot["pm_export_exclude"] = True
    snapshot["pm_direct_backup_for"] = pass_id
    snapshot["pm_direct_mesh_sha256"] = mesh_digest(snapshot)
    return snapshot


def require_session_scene(payload: dict[str, object] | None = None) -> tuple[dict[str, Any], str, bpy.types.Object]:
    active_session = session_id(payload)
    protocol, protocol_hash = load_protocol(active_session)
    active_source = source_path(protocol)
    if bpy.data.filepath != str(active_source):
        if not active_source.is_file():
            raise FileNotFoundError("Collector direct session does not exist; create it from v05 first.")
        bpy.ops.wm.open_mainfile(filepath=str(active_source))
    trace, trace_hash = prepare_trace_guides()
    scene = bpy.context.scene
    if scene.get("pm_authoring_phase") != active_session or scene.get("pm_direct_session_id", SESSION_ID) != active_session or scene.get("pm_direct_protocol_sha256") != protocol_hash:
        raise ValueError("This Blender source is not the active Collector direct-v05 session.")
    if scene.get("pm_primary_trace_id") != trace["trace_id"] or scene.get("pm_primary_trace_sha256") != trace_hash:
        raise ValueError("Collector direct session trace pin drifted.")
    if scene.get("pm_runtime_export_forbidden") is not True:
        raise ValueError("Collector direct session must stay runtime-export forbidden.")
    raw = bpy.data.objects.get(RAW_OBJECT)
    working = bpy.data.objects.get(WORKING_OBJECT)
    if raw is None or working is None or raw.type != "MESH" or working.type != "MESH":
        raise ValueError("Collector direct session is missing raw or working mesh.")
    if raw.get("pm_direct_raw_immutable") is not True or not raw.hide_render or not raw.get("pm_export_exclude"):
        raise ValueError("Collector raw v05 mesh is not protected.")
    if raw.get("pm_direct_mesh_sha256") != mesh_digest(raw):
        raise ValueError("Collector raw v05 mesh was mutated.")
    if working.get("pm_direct_working") is not True or working.get("pm_export_exclude"):
        raise ValueError("Collector direct working mesh is invalid.")
    if bpy.data.objects.get(TRACE_GUIDE_NAME) is None or bpy.data.objects.get(REFERENCE_PLANE_NAME) is None:
        raise ValueError("Collector direct session is missing its locked trace/reference guides.")
    return protocol, protocol_hash, working
