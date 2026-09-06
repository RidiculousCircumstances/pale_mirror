#!/usr/bin/env python3
"""Pin one complete V2 source-engine day, including phase-visible diagnostics."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
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


def settlement_trace(world: object, settlement_id: int) -> dict[str, object]:
    settlement = world.settlements[settlement_id]
    return {
        "id": settlement.id,
        "alive": settlement.alive,
        "population": settlement.population,
        "cash": settlement.cash,
        "integrity": settlement.integrity,
        "threat": settlement.threat,
        "illness": settlement.illness_burden,
        "medicine_fulfillment": settlement.medicine_fulfillment,
        "wounded": settlement.wounded_personnel,
        "stock": {resource.value: settlement.amount(resource) for resource in sorted(settlement.stock, key=lambda item: item.value)},
        "production": {resource.value: settlement.daily_production[resource] for resource in sorted(settlement.stock, key=lambda item: item.value)},
        "consumption": {resource.value: settlement.daily_consumption[resource] for resource in sorted(settlement.stock, key=lambda item: item.value)},
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        world = world_module.World(world_module.WorldConfig(
            width=64, height=44, settlement_count=12, seed=42, infection_seeds=2, v2=True, profile="source_v2",
        ))
        world.tick()
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 42, "infection_seeds": 2, "v2": True},
            "day": world.day,
            "history": world.history[-1],
            "settlements": [settlement_trace(world, 1), settlement_trace(world, 12)],
            "routes": [
                {"a": route.a, "b": route.b, "infection": route.infection,
                 "checkpoint_capacity_multiplier": route.checkpoint_capacity_multiplier}
                for route in world.trade.routes[:3]
            ],
            "market": world.microeconomy.summary(),
            "events": world.events,
            "decision_count": len(world.v2.decision_history),
            "chrysalis_count": len(world.v2.chrysalises),
            "front_campaign_count": len(world.v2.front_campaigns),
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
        raise SystemExit(f"daily-engine traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "engine.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("daily-engine micro-trace differs from the active Python reference")
        print("daily-engine micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote daily-engine micro-trace: {args.output}")


if __name__ == "__main__":
    main()
