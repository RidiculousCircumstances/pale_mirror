#!/usr/bin/env python3
"""Generate a source-pinned non-V2 HiveDirector micro-trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
from types import SimpleNamespace

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def order_summary(order: object) -> dict[str, object]:
    return {
        "kind": order.kind.value, "source_id": order.source_id, "target_x": order.target_x, "target_y": order.target_y,
        "organ_kind": order.organ_kind.value if order.organ_kind else None,
        "bioform_kind": order.bioform_kind.value if order.bioform_kind else None,
        "composition": [[kind.value, amount] for kind, amount in order.composition], "target_id": order.target_id, "reason": order.reason,
    }


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.ai.hive import HiveDirector
        from simulation.formations import FormationPhase
        from simulation.infection import AttackEvent, BioformKind, InfectionModel, OrganKind
        from simulation.profiles import SOURCE_V2
        from simulation.settlement import Facilities, NaturalPotential, Settlement

        infection = InfectionModel(7, 5, seed=17)
        infection.seed_infection(2, 2)
        brood = infection.create_nest(3, 2, biomass=100.0, samples=3.0, parent_nest_id=1, kind=OrganKind.BROOD_SAC)
        prey = Settlement(9, "Prey", 4, 2, 100.0, 100.0, NaturalPotential(), Facilities())
        infection.record_attack_harvest(
            AttackEvent(4, prey.id, 100.0, 1, BioformKind.RAIDER, {BioformKind.RAIDER: 1.0}, FormationPhase.MAIN_ACTION),
            prey, 10.0, False, 5,
        )
        world = SimpleNamespace(
            infection=infection, day=6, config=SimpleNamespace(width=7, height=5), settlements={prey.id: prey}, resource_sites={},
            field=SimpleNamespace(posts={}, campaigns={}), profile=SOURCE_V2,
        )
        orders = HiveDirector().step(world)
        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "hive_sha256": files["simulation/ai/hive.py"], "infection_sha256": files["simulation/infection.py"]},
            "orders": [order_summary(item) for item in orders],
            "pending_exploitation": len(infection.pending_exploitation),
            "brood_biomass": brood.biomass, "brood_last_project_day": brood.last_project_day,
            "swarms": [{"kind": item.kind.value, "target_x": item.target_x, "target_y": item.target_y, "target_id": item.target_id,
                        "source_nest_id": item.source_nest_id, "composition": {kind.value: count for kind, count in item.composition.items()}}
                       for item in infection.swarms],
            "projects": [{"source_nest_id": item.source_nest_id, "kind": item.kind.value, "x": item.x, "y": item.y,
                          "days_remaining": item.days_remaining, "committed_biomass": item.committed_biomass} for item in infection.nest_projects],
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
            raise SystemExit("hive director micro-trace differs from the active Python reference")
        print("Frontier hive director micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned hive director micro-trace to {output}")


if __name__ == "__main__":
    main()
