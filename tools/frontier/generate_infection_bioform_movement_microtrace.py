#!/usr/bin/env python3
"""Generate a source-pinned InfectionModel bioform-movement micro-trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
from types import SimpleNamespace

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.infection import BioformKind, InfectionModel, OrganKind

        harvest = InfectionModel(7, 5, seed=17)
        harvest.seed_infection(2, 2)
        brood = harvest.create_nest(3, 2, biomass=100.0, samples=3.0, parent_nest_id=1, kind=OrganKind.BROOD_SAC)
        world = SimpleNamespace(day=7)
        harvest._digest_ecology(world)
        harvester = harvest.launch_bioform(brood, BioformKind.HARVESTER, 6, 4)
        positions = []
        for day in range(8, 19):
            world.day = day
            harvest.advance_swarms({}, world)
            positions.append({"day": day, "alive": len(harvest.swarms), "x": harvester.x, "y": harvester.y,
                              "state": harvester.state, "cargo": harvester.cargo, "genes": harvester.genetic_cargo})

        carrier = InfectionModel(7, 5, seed=17)
        carrier.seed_infection(2, 2)
        sporulator = carrier.create_nest(3, 2, biomass=100.0, samples=3.0, parent_nest_id=1, kind=OrganKind.SPORULATOR)
        carrier.launch_bioform(sporulator, BioformKind.SPORE_CARRIER, 6, 4)
        for day in range(1, 8):
            carrier.advance_swarms({}, SimpleNamespace(day=day))

        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"]},
            "harvester": {"positions": positions, "core": harvest.nests[1].summary(),
                          "harvested_biomass": harvest.harvested_biomass, "harvested_genetic_material": harvest.harvested_genetic_material,
                          "flow": harvest.network_flows[-1].__dict__ if hasattr(harvest.network_flows[-1], "__dict__") else {
                              key: getattr(harvest.network_flows[-1], key) for key in harvest.network_flows[-1].__dataclass_fields__},
                          "history": harvest.project_history[-1]},
            "carrier": {"remaining_swarms": len(carrier.swarms), "tissue": carrier.level[4][6],
                        "latent": [{"x": item.x, "y": item.y, "spores": item.spores, "strength": item.strength,
                                    "source_nest_id": item.source_nest_id, "memory": item.memory} for item in carrier.latent_colonies]},
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
            raise SystemExit("infection bioform-movement micro-trace differs from the active Python reference")
        print("Frontier infection bioform-movement micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned infection bioform-movement micro-trace to {output}")


if __name__ == "__main__":
    main()
