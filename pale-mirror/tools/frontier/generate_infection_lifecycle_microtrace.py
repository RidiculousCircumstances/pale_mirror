#!/usr/bin/env python3
"""Generate a source-pinned InfectionModel lifecycle micro-trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def seeded_model(InfectionModel: object) -> object:
    model = InfectionModel(7, 5, seed=17)
    model.seed_infection(2, 2)
    return model


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.infection import InfectionModel, LatentColony, OrganKind

        project = seeded_model(InfectionModel)
        project.nests[1].biomass = 200.0
        assert project.start_morphogenesis(project.nests[1], OrganKind.SYNAPSE, (4, 2), 9)
        for day in range(10, 14):
            project._advance_projects(day)

        aborted = seeded_model(InfectionModel)
        aborted.nests[1].biomass = 200.0
        assert aborted.start_morphogenesis(aborted.nests[1], OrganKind.SYNAPSE, (4, 2), 9)
        aborted.nests[1].vitality = 0.0
        aborted._advance_projects(10)
        aborted._cull_destroyed_nests()

        latent = InfectionModel(25, 3, seed=19)
        latent.seed_infection(0, 1)
        latent.latent_colonies.append(LatentColony(24, 2, 6.0, 0.5, {"burrowing": 1.0}, 1))
        latent._decay_latent()

        damage = InfectionModel(1, 1)
        damage.damage_memory = {"scorch": 0.04, "combat": 0.02}
        damage._decay_damage_memory()
        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"]},
            "project": {"organs": [organ.summary() for organ in project.nests.values()], "history": project.project_history,
                        "remaining_projects": len(project.nest_projects)},
            "aborted": {"history": aborted.project_history, "remaining_organs": len(aborted.nests),
                        "detritus": aborted.ecosystem.cell(2, 2).detritus},
            "latent": {"remaining": len(latent.latent_colonies), "tissue": latent.level[2][24],
                       "organs": [organ.summary() for organ in latent.nests.values()], "history": latent.project_history},
            "damage_memory": damage.damage_memory,
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
            raise SystemExit("infection lifecycle micro-trace differs from the active Python reference")
        print("Frontier infection lifecycle micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned infection lifecycle micro-trace to {output}")


if __name__ == "__main__":
    main()
