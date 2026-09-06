#!/usr/bin/env python3
"""Pin source V2 daily checkpoints across the first 30 authoritative days."""

from __future__ import annotations

import argparse
import importlib
import json
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


CHECKPOINTS = (1, 2, 3, 5, 10, 15, 20, 25, 30)


def settlement_state(world: object) -> list[dict[str, object]]:
    return [
        {
            "id": settlement.id,
            "alive": settlement.alive,
            "population": settlement.population,
            "cash": settlement.cash,
            "integrity": settlement.integrity,
            "threat": settlement.threat,
            "food_fulfillment": settlement.food_fulfillment,
            "medicine_fulfillment": settlement.medicine_fulfillment,
            "illness_burden": settlement.illness_burden,
            "mobilized_personnel": settlement.mobilized_personnel,
            "wounded_personnel": settlement.wounded_personnel,
            "stock": {resource.value: settlement.amount(resource) for resource in settlement.stock},
            "production": {resource.value: settlement.daily_production[resource] for resource in settlement.stock},
            "consumption": {resource.value: settlement.daily_consumption[resource] for resource in settlement.stock},
        }
        for settlement in world.settlements.values()
    ]


def checkpoint(world: object) -> dict[str, object]:
    return {
        "day": world.day,
        "history": world.history[-1],
        "settlements": settlement_state(world),
        "market": world.microeconomy.summary(),
        "v2": world.v2.summary(),
        "events": world.events[-12:],
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        world = world_module.World(world_module.WorldConfig(
            width=64, height=44, settlement_count=12, seed=42, infection_seeds=2, v2=True, profile="source_v2",
        ))
        checkpoints: list[dict[str, object]] = []
        for day in range(1, CHECKPOINTS[-1] + 1):
            world.tick()
            if day in CHECKPOINTS:
                checkpoints.append(checkpoint(world))
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
    if not root.joinpath("simulation", "engine.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2 daily trajectory differs from the active Python reference")
        print("V2 daily trajectory matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2 daily trajectory: {args.output}")


if __name__ == "__main__":
    main()
