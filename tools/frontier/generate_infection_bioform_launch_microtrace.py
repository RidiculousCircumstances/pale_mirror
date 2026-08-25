#!/usr/bin/env python3
"""Generate a source-pinned InfectionModel bioform-launch micro-trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def swarm_summary(swarm: object) -> dict[str, object]:
    return {
        "id": swarm.id, "x": swarm.x, "y": swarm.y, "power": swarm.power, "target_id": swarm.target_id,
        "speed": swarm.speed, "kind": swarm.kind.value, "composition": {key.value: value for key, value in swarm.composition.items()},
        "phase": swarm.phase.value, "readiness": swarm.readiness, "source_nest_id": swarm.source_nest_id,
        "target_x": swarm.target_x, "target_y": swarm.target_y, "forage_x": swarm.forage_x, "forage_y": swarm.forage_y,
        "feral": swarm.feral,
    }


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.infection import BioformKind, InfectionModel, OrganKind

        model = InfectionModel(7, 5, seed=17)
        model.seed_infection(2, 2)
        brood = model.create_nest(3, 2, biomass=100.0, samples=3.0, parent_nest_id=1, kind=OrganKind.BROOD_SAC)
        harvester = model.launch_bioform(brood, BioformKind.HARVESTER, 6, 4, composition={BioformKind.RAIDER: 8.0})
        assault = model.launch_bioform(brood, BioformKind.RAIDER, 6, 4, 9, {BioformKind.RAIDER: 1.0, BioformKind.BREAKER: 1.0})
        wrong_source = model.launch_bioform(model.nests[1], BioformKind.RAIDER, 0, 0)

        discrete = InfectionModel(7, 5, seed=17, discrete_bioforms=True)
        discrete.seed_infection(2, 2)
        discrete_brood = discrete.create_nest(3, 2, biomass=100.0, samples=3.0, parent_nest_id=1, kind=OrganKind.BROOD_SAC)
        try:
            discrete.launch_bioform(discrete_brood, BioformKind.RAIDER, 6, 4, composition={BioformKind.RAIDER: 0.5})
        except ValueError as error:
            discrete_error = str(error)
        else:
            discrete_error = None

        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"]},
            "brood_biomass": brood.biomass,
            "swarms": [swarm_summary(harvester), swarm_summary(assault)],
            "wrong_source": wrong_source is None,
            "discrete_error": discrete_error,
            "discrete_brood_biomass": discrete_brood.biomass,
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
            raise SystemExit("infection bioform-launch micro-trace differs from the active Python reference")
        print("Frontier infection bioform-launch micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned infection bioform-launch micro-trace to {output}")


if __name__ == "__main__":
    main()
