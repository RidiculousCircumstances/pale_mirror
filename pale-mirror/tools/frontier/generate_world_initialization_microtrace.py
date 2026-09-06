#!/usr/bin/env python3
"""Generate a source-pinned deterministic World-construction micro-trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.world import World, WorldConfig

        world = World(WorldConfig(seed=42, infection_seeds=2, v2=False))
        result = {
            "schema": SCHEMA,
            "source": {
                "tree_sha256": tree_digest,
                "world_sha256": files["simulation/world.py"],
                "infection_sha256": files["simulation/infection.py"],
            },
            "settlements": [
                {
                    "id": item.id,
                    "name": item.name,
                    "x": item.x,
                    "y": item.y,
                    "population": item.population,
                    "cash": item.cash,
                    "natural": {
                        "fertility": item.natural.fertility,
                        "ore_richness": item.natural.ore_richness,
                        "forest": item.natural.forest,
                        "river_power": item.natural.river_power,
                    },
                    "facilities": {
                        "workshop": item.facilities.workshop,
                        "armory": item.facilities.armory,
                        "clinic": item.facilities.clinic,
                        "fortification": item.facilities.fortification,
                    },
                    "doctrine": item.doctrine,
                    "stock": {resource.value: item.amount(resource) for resource in item.stock},
                }
                for item in world.settlements.values()
            ],
            "sites": [
                {
                    "id": item.id,
                    "kind": item.kind.value,
                    "x": item.x,
                    "y": item.y,
                    "quality": item.quality,
                    "capacity": item.capacity,
                    "owner_id": item.owner_id,
                    "operator_company_id": item.operator_company_id,
                    "condition": item.condition,
                    "haul_capacity": item.haul_capacity,
                }
                for item in world.resource_sites.values()
            ],
            "routes": [
                {
                    "a": item.a,
                    "b": item.b,
                    "distance": item.distance,
                    "capacity": item.capacity,
                    "risk": item.risk,
                    "quality": item.quality,
                }
                for item in world.trade.routes
            ],
            "infection": {
                "organs": [
                    {"id": item.id, "x": item.x, "y": item.y, "biomass": item.biomass, "samples": item.samples}
                    for item in world.infection.nests.values()
                ],
                "organic": world.infection.ecosystem.total_organic(),
                "scar": world.infection.ecosystem.total_scar(),
                "infected_fraction": world.infection.infected_fraction(),
            },
            "market": {
                "summary": world.microeconomy.summary(),
                "companies": [item.summary() for item in world.microeconomy.companies.values()],
                "households": len(world.microeconomy.households),
                "licences": len(world.microeconomy.licences),
            },
        }
        return canonical_bytes(result)
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
    result = payload(args.reference_root.resolve())
    output = args.output.resolve()
    if args.check:
        if not output.is_file() or output.read_bytes() != result:
            raise SystemExit("world initialization micro-trace differs from the active Python reference")
        print("Frontier world initialization micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned world initialization micro-trace to {output}")


if __name__ == "__main__":
    main()
