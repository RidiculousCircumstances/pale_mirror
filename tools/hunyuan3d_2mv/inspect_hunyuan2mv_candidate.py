#!/usr/bin/env python3
"""Verify and report geometry integrity for one Hunyuan2mv volume proposal.

This is a review aid only. It cannot produce or export a gameplay asset.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def inspect(candidate: Path, manifest_path: Path) -> dict[str, object]:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    output = manifest.get("output")
    if (
        manifest.get("schema") != "pale_mirror.hunyuan2mv_volume_candidate.v1"
        or manifest.get("runtime_export_forbidden") is not True
        or not isinstance(output, dict)
        or output.get("file") != candidate.name
        or output.get("sha256") != sha256(candidate)
    ):
        raise ValueError("Candidate geometry does not match the non-exportable Hunyuan2mv provenance contract.")
    import trimesh

    mesh = trimesh.load(candidate, force="mesh", process=False)
    if not isinstance(mesh, trimesh.Trimesh):
        raise ValueError("Hunyuan2mv GLB did not contain one mesh.")
    components = mesh.split(only_watertight=False)
    component_summaries = sorted(
        (
            {
                "faces": int(len(component.faces)),
                "vertices": int(len(component.vertices)),
                "bounds": [[float(value) for value in row] for row in component.bounds],
            }
            for component in components
        ),
        key=lambda entry: int(entry["faces"]),
        reverse=True,
    )
    return {
        "schema": "pale_mirror.hunyuan2mv_geometry_review.v1",
        "candidate_id": manifest["candidate_id"],
        "candidate_sha256": output["sha256"],
        "runtime_export_forbidden": True,
        "vertices": int(len(mesh.vertices)),
        "faces": int(len(mesh.faces)),
        "components": len(components),
        "largest_components": component_summaries[:8],
        "watertight": bool(mesh.is_watertight),
        "winding_consistent": bool(mesh.is_winding_consistent),
        "euler_number": int(mesh.euler_number),
        "bounds": [[float(value) for value in row] for row in mesh.bounds],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    arguments = parser.parse_args()
    print(json.dumps(inspect(arguments.candidate, arguments.manifest), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
