"""Inspect the existing leading-mantle surface without changing the mesh."""

from __future__ import annotations

from collections import defaultdict, deque
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from collector_artist_region import contains_primary_point, primary_polygon_world  # noqa: E402
from collector_direct_common import ASSET_ID, mesh_digest, prepare_trace_guides, require_session_scene, write_receipt  # noqa: E402


SESSION_ID = "collector_v05_direct_mesh_v03"
PASS_ID = "leading_mantle_artist_v03"


def main(payload: dict[str, object]) -> dict[str, object]:
    if payload.get("asset_id") != ASSET_ID:
        raise ValueError("Only the canonical biomass_collector asset is supported.")
    protocol, _protocol_hash, working = require_session_scene({"session_id": SESSION_ID})
    trace, _trace_hash = prepare_trace_guides()
    polygon = primary_polygon_world(protocol, trace)
    world_vertices = [working.matrix_world @ vertex.co for vertex in working.data.vertices]
    selected_vertices = {index for index, point in enumerate(world_vertices) if contains_primary_point(polygon, point)}
    if len(selected_vertices) < 32:
        raise ValueError("Leading-mantle artist region did not select a meaningful existing surface.")
    selected_faces = [
        face.index for face in working.data.polygons
        if sum(vertex in selected_vertices for vertex in face.vertices) >= max(3, len(face.vertices) - 1)
    ]
    if not selected_faces:
        raise ValueError("Leading-mantle artist region contains no usable existing faces.")
    components = _face_components(working, selected_faces)
    boundaries = _selected_region_boundaries(working, selected_faces)
    report = {
        "schema": "pale_mirror_visuals.collector_artist_topology_report.v1",
        "session_id": SESSION_ID,
        "pass_id": PASS_ID,
        "kind": "read_only_artist_region_topology",
        "working_mesh_sha256": mesh_digest(working),
        "selected_vertices": len(selected_vertices),
        "selected_faces": len(selected_faces),
        "face_islands": [
            {
                "faces": len(component),
                "vertices": len({vertex for face in component for vertex in working.data.polygons[face].vertices}),
            }
            for component in components[:8]
        ],
        "selected_face_island_count": len(components),
        "region_boundary_edges": boundaries,
        "judgement": _judgement(components, len(selected_faces), boundaries),
        "next": "Open the prepared v03 artist scene and sculpt/local-retopologise only the named leading-mantle groups; no scripted broad vertex deformation is permitted.",
    }
    receipt = write_receipt("leading_mantle_artist_topology_v03", report, SESSION_ID)
    return {**report, "receipt": receipt.as_posix()}


def _face_components(working, selected_faces: list[int]) -> list[list[int]]:
    by_vertex: dict[int, list[int]] = defaultdict(list)
    for face_index in selected_faces:
        for vertex in working.data.polygons[face_index].vertices:
            by_vertex[vertex].append(face_index)
    unseen = set(selected_faces)
    components: list[list[int]] = []
    while unseen:
        seed = min(unseen)
        unseen.remove(seed)
        queue: deque[int] = deque((seed,))
        component: list[int] = []
        while queue:
            face_index = queue.popleft()
            component.append(face_index)
            for vertex in working.data.polygons[face_index].vertices:
                for adjacent in by_vertex[vertex]:
                    if adjacent in unseen:
                        unseen.remove(adjacent)
                        queue.append(adjacent)
        components.append(sorted(component))
    return sorted(components, key=lambda component: (-len(component), component[0]))


def _selected_region_boundaries(working, selected_faces: list[int]) -> int:
    edge_use: dict[tuple[int, int], int] = defaultdict(int)
    for face_index in selected_faces:
        vertices = list(working.data.polygons[face_index].vertices)
        for left, right in zip(vertices, vertices[1:] + vertices[:1]):
            edge_use[tuple(sorted((left, right)))] += 1
    return sum(count == 1 for count in edge_use.values())


def _judgement(components: list[list[int]], selected_faces: int, boundaries: int) -> str:
    largest = len(components[0]) if components else 0
    if largest / selected_faces < 0.82:
        return "fragmented: do not sculpt until an artist chooses a single attached semantic patch or repairs local topology"
    if boundaries < 12:
        return "overbroad: artist selection likely covers a closed mass; narrow the edit in Blender before sculpting"
    return "usable: one dominant existing mantle patch is available for an artist-led local sculpt/retopology pass"


if __name__ == "__main__":
    print(main({"asset_id": ASSET_ID}))
