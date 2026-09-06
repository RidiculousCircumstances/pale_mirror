#!/usr/bin/env python3
"""Pin local V2 observations and the non-omniscient hive view for the Java port."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
from math import hypot
from pathlib import Path
import sys


REFERENCE_PYTHON = (3, 11)


def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def digest(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def source_manifest(root: Path) -> tuple[dict[str, str], str]:
    files = [*root.joinpath("simulation").rglob("*.py"), root / "requirements.txt"]
    values = {path.relative_to(root).as_posix(): digest(path.read_bytes()) for path in sorted(files) if path.is_file()}
    return values, digest(canonical(values))


def source_world(world_module: object) -> object:
    return world_module.World(world_module.WorldConfig(
        width=64, height=44, settlement_count=12, seed=41, infection_seeds=0, v2=True, profile="source_v2",
    ))


def center(sector: object) -> tuple[int, int]:
    return sector.x * 4 + 2, sector.y * 4 + 2


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        field = importlib.import_module("simulation.field")
        hive_v2 = importlib.import_module("simulation.ai.v2")
        infection = importlib.import_module("simulation.infection")
        views = importlib.import_module("simulation.views")
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        world = source_world(world_module)
        settlement = world.settlements[1]
        target = next(sector for sector in world.v2.sectors.values() if hypot(center(sector)[0] - settlement.x, center(sector)[1] - settlement.y) > 14.0)
        post_x, post_y = center(target)
        world.field.posts[91] = field.FieldPost(91, field.FieldPostKind.OBSERVATION, post_x, post_y, 1, settlement.id,
                                                 {settlement.id}, 100.0, 0, status=field.FieldPostStatus.ACTIVE)
        world.v2.observe(world)
        post_belief = world.v2.human_perceptions[settlement.id].beliefs[target.key]

        tissue = world.v2.sectors["0:0"]
        for x, y in tissue.cells:
            world.infection.level[y][x] = 0.9
        swarm_sector = world.v2.sectors["15:10"]
        swarm_x, swarm_y = swarm_sector.cells[0]
        world.infection.swarms.append(infection.Swarm(55, swarm_x, swarm_y, 20.0, -1, target_x=swarm_x, target_y=swarm_y,
                                                      composition={infection.BioformKind.RAIDER: 1.0}))
        world.v2.refresh_territory(world)
        world.v2.observe(world)
        perceived = hive_v2.HiveMind().perceived_view(world, views.world_view(world), world.v2.hive_perception)
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "post": {"sector": target.key, "source": post_belief.source, "confidence": post_belief.confidence},
            "hive_view": {
                "cells": len(perceived.cells), "sectors": [sector.key for sector in perceived.sectors],
                "swarms": [swarm.id for swarm in perceived.swarms],
                "tissue_cell_known": any((cell.x, cell.y) == tissue.cells[0] for cell in perceived.cells),
                "swarm_cell_known": any((cell.x, cell.y) == (swarm_x, swarm_y) for cell in perceived.cells),
                "unknown_cell_known": any((cell.x, cell.y) == (32, 20) for cell in perceived.cells),
            },
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
    if sys.version_info[:2] != REFERENCE_PYTHON:
        raise SystemExit(f"V2-perception traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "v2.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2-perception micro-trace differs from the active Python reference")
        print("V2-perception micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2-perception micro-trace: {args.output}")


if __name__ == "__main__":
    main()
