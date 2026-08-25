#!/usr/bin/env python3
"""Generate a source-pinned micro-trace for retained InfectionModel entry points."""

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
        from simulation.infection import InfectionModel, Mutation, OrganKind
        from simulation.settlement import Facilities, NaturalPotential, Settlement

        model = InfectionModel(7, 5, seed=17)
        model.seed_infection(2, 2)
        core = model.nests[1]
        core.biomass = 200.0
        core.samples = 10.0
        growth_target = model.nest_growth_target(core)
        chosen = model.choose_mutation(core, object())
        mutation_ok = model.launch_mutation(core, Mutation.FIELD, 3)
        spores_ok = model.launch_spores(core, 4)
        project_ok = model.launch_nest_project(core, (4, 2), 5)

        brood = model.create_nest(3, 2, biomass=100.0, samples=3.0, parent_nest_id=1, kind=OrganKind.BROOD_SAC)
        prey = Settlement(9, "Prey", 5, 2, 100.0, 100.0, NaturalPotential(), Facilities())
        swarm = model.launch_swarm(brood, {prey.id: prey}, prey.id, 6)
        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"]},
            "growth_target": list(growth_target), "chosen_mutation": chosen.value, "mutation_ok": mutation_ok,
            "spores_ok": spores_ok, "project_ok": project_ok,
            "core": {"biomass": core.biomass, "samples": core.samples, "mutation_level": core.mutation_level(Mutation.FIELD),
                     "last_project_day": core.last_project_day, "level_2_2": model.level[2][2], "level_4_2": model.level[2][4]},
            "project": {"source_nest_id": model.nest_projects[0].source_nest_id, "x": model.nest_projects[0].x, "y": model.nest_projects[0].y,
                        "kind": model.nest_projects[0].kind.value, "days_remaining": model.nest_projects[0].days_remaining,
                        "committed_biomass": model.nest_projects[0].committed_biomass},
            "swarm": {"kind": swarm.kind.value, "target_id": swarm.target_id, "target_x": swarm.target_x, "target_y": swarm.target_y,
                      "composition": {kind.value: count for kind, count in swarm.composition.items()}, "power": swarm.power, "speed": swarm.speed,
                      "source_nest_id": swarm.source_nest_id},
            "brood": {"biomass": brood.biomass, "last_project_day": brood.last_project_day},
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
            raise SystemExit("infection legacy micro-trace differs from the active Python reference")
        print("Frontier infection legacy micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned infection legacy micro-trace to {output}")


if __name__ == "__main__":
    main()
