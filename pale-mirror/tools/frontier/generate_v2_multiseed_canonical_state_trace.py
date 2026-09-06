#!/usr/bin/env python3
"""Pin complete source-V2 canonical state over independent generation streams."""

from __future__ import annotations

import argparse
import hashlib
import importlib
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest
from generate_v2_canonical_state_trace import StateEncoder, numeric_conformance


CHECKPOINTS = (0, 1, 10, 30, 60)
SEEDS = (7, 17, 41, 73)
REFERENCE_PYTHON = (3, 11)


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        runs: list[dict[str, object]] = []
        for seed in SEEDS:
            world = world_module.World(world_module.WorldConfig(
                width=64, height=44, settlement_count=12, seed=seed, infection_seeds=2, v2=True, profile="source_v2",
            ))
            checkpoints: list[dict[str, object]] = []
            for day in range(CHECKPOINTS[-1] + 1):
                if day in CHECKPOINTS:
                    encoder = StateEncoder()
                    components = {
                        "world_root": encoder.world_root(world),
                        "diagnostics": encoder.diagnostics(world),
                        "settlements": encoder.encode(world.settlements),
                        "resource_sites": encoder.encode(world.resource_sites),
                        "trade": encoder.encode(world.trade),
                        "market": encoder.encode(world.microeconomy),
                        "infection": encoder.encode(world.infection),
                        "operations": encoder.encode(world.operations),
                        "field": encoder.encode(world.field),
                        "v2": encoder.encode(world.v2),
                    }
                    state = encoder.state(world)
                    payload = canonical_bytes(numeric_conformance(state))
                    checkpoints.append({
                        "day": day,
                        "state_numeric_conformance_sha256": hashlib.sha256(payload).hexdigest(),
                        "events": len(world.events),
                        "operations": len(world.operations.active) + len(world.operations.completed),
                        "front_campaigns": len(world.v2.front_campaigns),
                        "chrysalises": len(world.v2.chrysalises),
                        "components": {
                            name: hashlib.sha256(canonical_bytes(numeric_conformance(value))).hexdigest()
                            for name, value in components.items()
                        },
                    })
                if day < CHECKPOINTS[-1]:
                    world.tick()
            runs.append({"seed": seed, "checkpoints": checkpoints})
        return {
            "schema": 1,
            "codec": "frontier_reference_state_v1",
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "infection_seeds": 2, "v2": True},
            "seeds": list(SEEDS),
            "checkpoints": list(CHECKPOINTS),
            "runs": runs,
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
        raise SystemExit(f"multi-seed V2 traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "world.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("multi-seed V2 canonical-state trace differs from the active Python reference")
        print("multi-seed V2 canonical-state trace matches the active Python reference")
        return
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(payload)
    print(f"wrote multi-seed V2 canonical-state trace: {args.output}")


if __name__ == "__main__":
    main()
