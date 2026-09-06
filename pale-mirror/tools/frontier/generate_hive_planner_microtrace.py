#!/usr/bin/env python3
"""Generate a source-pinned micro-trace for the pure HivePlanner boundary."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def cells(CellView: object, width: int, height: int, overrides: dict[tuple[int, int], tuple[float, float, float]]) -> tuple[object, ...]:
    return tuple(
        CellView(x, y, *overrides.get((x, y), (0.0, 0.0, 0.5)), 0.0)
        for y in range(height) for x in range(width)
    )


def view(WorldView: object, CellView: object, *, day: int, width: int, height: int, nests: tuple[object, ...],
         settlements: tuple[object, ...] = (), swarms: tuple[object, ...] = (),
         overrides: dict[tuple[int, int], tuple[float, float, float]] | None = None, sectors: tuple[object, ...] = ()) -> object:
    return WorldView(day, width, height, settlements, (), nests, swarms, cells(CellView, width, height, overrides or {}), (), (), 7, sectors)


def order_summary(order: object) -> dict[str, object]:
    return {
        "kind": order.kind.value, "source_id": order.source_id, "target_x": order.target_x, "target_y": order.target_y,
        "organ_kind": order.organ_kind.value if order.organ_kind else None,
        "bioform_kind": order.bioform_kind.value if order.bioform_kind else None,
        "composition": [[kind.value, amount] for kind, amount in order.composition],
        "target_id": order.target_id, "reason": order.reason,
    }


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.ai.hive import HivePlanner
        from simulation.infection import BioformKind, InfectionModel, OrganKind
        from simulation.views import CellView, NestView, SectorView, SettlementView, SwarmView, WorldView

        planner = HivePlanner()
        core = view(WorldView, CellView, day=6, width=16, height=12, nests=(NestView(1, 5, 5, "core", 200.0, 3.0, 100.0, False, -10_000),),
                    overrides={(9, 5): (0.50, 100.0, 0.5)})
        core_digestive = view(WorldView, CellView, day=6, width=20, height=12, nests=(
            NestView(11, 5, 5, "core", 200.0, 3.0, 100.0, False, -10_000),
            NestView(12, 9, 5, "synapse", 55.0, 3.0, 100.0, False, 4),
        ), overrides={(13, 5): (0.50, 100.0, 0.5)})
        core_sporulator = view(WorldView, CellView, day=6, width=26, height=12, nests=(
            NestView(13, 5, 5, "core", 200.0, 3.0, 100.0, False, -10_000),
            NestView(14, 9, 5, "synapse", 55.0, 3.0, 100.0, False, 4),
            NestView(15, 13, 5, "digestive_pool", 55.0, 3.0, 100.0, False, 4),
        ), overrides={(17, 5): (0.50, 100.0, 0.5)})
        digestive = view(WorldView, CellView, day=6, width=16, height=12, nests=(NestView(16, 5, 5, "digestive_pool", 100.0, 3.0, 100.0, False, -10_000),),
                         overrides={(9, 5): (0.50, 100.0, 0.5)})
        synapse = view(WorldView, CellView, day=6, width=16, height=12, nests=(NestView(17, 5, 5, "synapse", 100.0, 3.0, 100.0, False, -10_000),),
                       overrides={(9, 5): (0.50, 100.0, 0.5)})
        harvest = view(WorldView, CellView, day=6, width=16, height=12, nests=(NestView(2, 5, 5, "brood_sac", 70.0, 3.0, 100.0, False, -10_000),),
                       overrides={(10, 5): (0.0, 100.0, 0.5)})
        armoured = view(WorldView, CellView, day=6, width=16, height=12, nests=(NestView(3, 5, 5, "brood_sac", 120.0, 3.0, 100.0, False, -10_000),),
                        settlements=(SettlementView(9, 8, 5, True, 100.0, 100.0, 0.0, 0.0, 150.0, "trade", ()),))
        soft = view(WorldView, CellView, day=6, width=16, height=12, nests=(NestView(18, 5, 5, "brood_sac", 120.0, 3.0, 100.0, False, -10_000),),
                    settlements=(SettlementView(19, 8, 5, True, 100.0, 100.0, 0.0, 0.0, 10.0, "trade", ()),))
        spore = view(WorldView, CellView, day=6, width=50, height=12, nests=(NestView(4, 5, 5, "sporulator", 100.0, 3.0, 100.0, False, -10_000),),
                     settlements=(SettlementView(10, 33, 5, True, 100.0, 100.0, 0.0, 0.0, 10.0, "trade", ()),),
                     overrides={(30, 5): (0.0, 100.0, 0.8)})
        frontier = view(WorldView, CellView, day=6, width=20, height=12, nests=(NestView(5, 5, 5, "brood_sac", 100.0, 3.0, 100.0, False, -10_000),),
                        sectors=(SectorView("2:1", 2, 1, 100.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0, 3.0, "—", "human", 0.20, 10.0, True),))
        feral = view(WorldView, CellView, day=6, width=16, height=12, nests=(NestView(6, 5, 5, "core", 200.0, 3.0, 100.0, True, -10_000),),
                     overrides={(9, 5): (0.50, 100.0, 0.5)})
        feral_brood = view(WorldView, CellView, day=6, width=16, height=12, nests=(NestView(20, 5, 5, "brood_sac", 120.0, 3.0, 100.0, True, -10_000),),
                           settlements=(SettlementView(21, 8, 5, True, 100.0, 100.0, 0.0, 0.0, 10.0, "trade", ()),))
        not_due = view(WorldView, CellView, day=5, width=16, height=12, nests=(NestView(7, 5, 5, "core", 200.0, 3.0, 100.0, False, -10_000),),
                       overrides={(9, 5): (0.50, 100.0, 0.5)})

        execution = InfectionModel(7, 5, seed=17)
        execution.seed_infection(2, 2)
        execution.nests[1].biomass = 200.0
        from simulation.ai.hive import HiveOrder, HiveOrderExecutor, HiveOrderKind
        executed = HiveOrderExecutor().execute(execution, HiveOrder(HiveOrderKind.MORPH_ORGAN, 1, 4, 2, organ_kind=OrganKind.SYNAPSE,
                                                                      reason="complete local biological complex"), 9)
        result = {
            "schema": SCHEMA,
            "source": {
                "tree_sha256": tree_digest, "hive_sha256": files["simulation/ai/hive.py"],
                "infection_sha256": files["simulation/infection.py"], "views_sha256": files["simulation/views.py"],
            },
            "orders": {
                "core": [order_summary(item) for item in planner.plan(core)],
                "core_digestive": [order_summary(item) for item in planner.plan(core_digestive)],
                "core_sporulator": [order_summary(item) for item in planner.plan(core_sporulator)],
                "digestive": [order_summary(item) for item in planner.plan(digestive)],
                "synapse": [order_summary(item) for item in planner.plan(synapse)],
                "harvest": [order_summary(item) for item in planner.plan(harvest)],
                "armoured": [order_summary(item) for item in planner.plan(armoured)],
                "soft": [order_summary(item) for item in planner.plan(soft)],
                "spore": [order_summary(item) for item in planner.plan(spore)],
                "frontier": [order_summary(item) for item in planner.plan(frontier)],
                "feral": [order_summary(item) for item in planner.plan(feral)],
                "feral_brood": [order_summary(item) for item in planner.plan(feral_brood)],
                "not_due": [order_summary(item) for item in planner.plan(not_due)],
            },
            "execution": {
                "accepted": executed, "biomass": execution.nests[1].biomass,
                "last_project_day": execution.nests[1].last_project_day,
                "project": {
                    "source_nest_id": execution.nest_projects[0].source_nest_id,
                    "x": execution.nest_projects[0].x, "y": execution.nest_projects[0].y,
                    "days_remaining": execution.nest_projects[0].days_remaining,
                    "kind": execution.nest_projects[0].kind.value,
                    "committed_biomass": execution.nest_projects[0].committed_biomass,
                },
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
            raise SystemExit("hive planner micro-trace differs from the active Python reference")
        print("Frontier hive planner micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned hive planner micro-trace to {output}")


if __name__ == "__main__":
    main()
