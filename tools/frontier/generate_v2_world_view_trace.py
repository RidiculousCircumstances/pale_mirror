#!/usr/bin/env python3
"""Pin Python's complete immutable V2 WorldView at deterministic checkpoints."""

from __future__ import annotations

import argparse
import dataclasses
import hashlib
import importlib
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


CHECKPOINTS = (0, 1, 5, 10, 15, 20, 25, 30)


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        world = world_module.World(world_module.WorldConfig(
            width=64, height=44, settlement_count=12, seed=42, infection_seeds=2, v2=True, profile="source_v2",
        ))
        checkpoints: list[dict[str, object]] = []
        for day in range(CHECKPOINTS[-1] + 1):
            if day in CHECKPOINTS:
                view = dataclasses.asdict(world.view())
                payload = canonical_bytes(view)
                checkpoints.append({"day": day, "sha256": hashlib.sha256(payload).hexdigest(), "bytes": len(payload), "view": view})
            if day < CHECKPOINTS[-1]:
                world.tick()
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 42, "infection_seeds": 2, "v2": True},
            "checkpoints": checkpoints,
        }
    finally:
        sys.path.remove(str(root))
        for name in tuple(sys.modules):
            if name == "simulation" or name.startswith("simulation."):
                del sys.modules[name]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "world.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2 WorldView trace differs from the active Python reference")
        print("V2 WorldView trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2 WorldView trace: {args.output}")


if __name__ == "__main__":
    main()
