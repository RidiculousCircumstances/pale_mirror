#!/usr/bin/env python3
"""Generate a source-pinned full daily InfectionModel lifecycle micro-trace."""

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
        from simulation.infection import InfectionModel, OrganKind
        from simulation.sites import ResourceSite, SiteKind

        model = InfectionModel(7, 5, seed=17)
        model.seed_infection(2, 2, level=0.85, radius=2)
        model.create_nest(3, 2, biomass=30.0, samples=2.0, parent_nest_id=1, kind=OrganKind.SYNAPSE)
        model.create_nest(1, 2, biomass=100.0, samples=3.0, parent_nest_id=1, kind=OrganKind.DIGESTIVE_POOL)
        model.create_nest(4, 2, biomass=5.0, samples=0.0, parent_nest_id=1, kind=OrganKind.BROOD_SAC)
        model.nests[1].biomass = 200.0
        assert model.start_morphogenesis(model.nests[1], OrganKind.SYNAPSE, (5, 2), 10)
        model.damage_memory = {"scorch": 0.04, "combat": 0.02}
        near = ResourceSite(1, SiteKind.FARM, 2, 2, 0.9, 5.0, 1, contamination=0.10, substrate=7.0)
        far = ResourceSite(2, SiteKind.MINE, 6, 4, 0.8, 4.0, 2, contamination=0.12, substrate=3.0)
        world = SimpleNamespace(day=11, resource_sites={near.id: near, far.id: far})

        model.ecology_step(world)
        model.record_economy_snapshot(world.day)

        def organ_row(organ: object) -> dict[str, object]:
            return {
                "id": organ.id,
                "kind": organ.kind.value,
                "biomass": organ.biomass,
                "samples": organ.samples,
                "vitality": organ.vitality,
                "feral": organ.feral,
            }

        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"]},
            "tissue": model.level,
            "ecology": {"organic": model.ecosystem.total_organic(), "scar": model.ecosystem.total_scar()},
            "sites": {
                str(site.id): {"contamination": site.contamination, "substrate": site.substrate}
                for site in (near, far)
            },
            "organs": [organ_row(organ) for organ in model.nests.values()],
            "remaining_projects": [
                {"source_nest_id": project.source_nest_id, "x": project.x, "y": project.y,
                 "days_remaining": project.days_remaining, "kind": project.kind.value,
                 "committed_biomass": project.committed_biomass}
                for project in model.nest_projects
            ],
            "flows": [
                {"day": flow.day, "source_kind": flow.source_kind, "source_id": flow.source_id,
                 "source_x": flow.source_x, "source_y": flow.source_y, "nest_id": flow.nest_id,
                 "amount": flow.amount, "retained": flow.retained, "loss": flow.loss, "distance": flow.distance}
                for flow in model.network_flows
            ],
            "ledger": {str(identifier): values for identifier, values in model.nest_economy.items()},
            "history": model.project_history,
            "damage_memory": model.damage_memory,
            "snapshots": model.nest_economy_history,
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
            raise SystemExit("infection daily lifecycle micro-trace differs from the active Python reference")
        print("Frontier infection daily lifecycle micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned infection daily lifecycle micro-trace to {output}")


if __name__ == "__main__":
    main()
