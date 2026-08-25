#!/usr/bin/env python3
"""Generate a source-pinned micro-trace for human/hive infection interactions."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def settlement(Settlement: object, NaturalPotential: object, Facilities: object, identifier: int, x: int, y: int, population: float = 100.0) -> object:
    return Settlement(identifier, f"Settlement {identifier}", x, y, population, 100.0, NaturalPotential(), Facilities())


def latent_summary(item: object) -> dict[str, object]:
    return {"x": item.x, "y": item.y, "spores": item.spores, "strength": item.strength, "memory": item.memory, "source_nest_id": item.source_nest_id}


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.economy import Resource
        from simulation.formations import FormationPhase
        from simulation.infection import AttackEvent, BioformKind, InfectionModel
        from simulation.settlement import Facilities, NaturalPotential, Settlement
        from simulation.trade import TradeRecord

        assault = InfectionModel(7, 5, seed=17)
        assault.seed_infection(2, 2)
        prey = settlement(Settlement, NaturalPotential, Facilities, 9, 4, 2)
        attack = AttackEvent(4, prey.id, 100.0, 1, BioformKind.RAIDER, {BioformKind.RAIDER: 1.0}, FormationPhase.MAIN_ACTION)
        before_detritus = assault.ecosystem.cell(prey.x, prey.y).detritus
        assault.record_attack_harvest(attack, prey, 10.0, False, 12)
        target = assault.exploitation_target(assault.nests[1], 12)
        pending = list(assault.pending_exploitation)
        assault.resolve_exploitation(*target, assault.nests[1], BioformKind.HARVESTER)

        trade = InfectionModel(7, 5, seed=6)
        trade.seed_infection(2, 2)
        seller = settlement(Settlement, NaturalPotential, Facilities, 1, 2, 2)
        buyer = settlement(Settlement, NaturalPotential, Facilities, 2, 4, 2)
        jumps = trade.after_trade(type("World", (), {"settlements": {seller.id: seller, buyer.id: buyer}})(),
                                  [TradeRecord(3, seller.id, buyer.id, Resource.FOOD, 3.0, 3.0, 1.0, (seller.id, buyer.id))])
        trade.introduce_refugees(5, 2, 0.50, 20.0)

        destroyed = InfectionModel(7, 5, seed=17)
        destroyed.seed_infection(2, 2)
        fallen = settlement(Settlement, NaturalPotential, Facilities, 3, 3, 2, population=80.0)
        destroyed.settlement_destroyed(fallen)

        suppression = InfectionModel(7, 5, seed=17)
        suppression.seed_infection(2, 2)
        removed = suppression.suppress_area(2, 2, 2.0, 0.50)

        hotspots = InfectionModel(9, 9, seed=2)
        hotspots.level[1][1] = 0.70
        hotspots.level[1][7] = 0.80
        hotspots.level[4][4] = 0.90
        hotspots.level[7][7] = 0.60
        hotspots.level[4][5] = 0.89
        result = {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "infection_sha256": files["simulation/infection.py"], "trade_sha256": files["simulation/trade.py"]},
            "assault": {
                "illness": prey.illness_burden, "detritus_before": before_detritus,
                "detritus_after_resolve": assault.ecosystem.cell(prey.x, prey.y).detritus,
                "pending": [{"x": item["x"], "y": item["y"], "mass": item["mass"], "genes": item["genes"], "day": item["day"], "source_nest_id": item["source_nest_id"]} for item in pending],
                "target": list(target), "remaining": len(assault.pending_exploitation),
            },
            "trade_refugees": {"jumps": jumps, "latent": [latent_summary(item) for item in trade.latent_colonies], "damage_memory": trade.damage_memory},
            "destroyed": {"core_biomass": destroyed.nests[1].biomass, "core_samples": destroyed.nests[1].samples,
                          "harvested_biomass": destroyed.harvested_biomass, "harvested_genetic": destroyed.harvested_genetic_material},
            "suppression": {"removed": removed, "core_biomass": suppression.nests[1].biomass, "core_vitality": suppression.nests[1].vitality,
                            "core_exists": 1 in suppression.nests, "damage_memory": suppression.damage_memory},
            "hotspots": {"items": [list(item) for item in hotspots.hotspots(3, 3.0)], "fraction": hotspots.infected_fraction()},
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
            raise SystemExit("infection interaction micro-trace differs from the active Python reference")
        print("Frontier infection interaction micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned infection interaction micro-trace to {output}")


if __name__ == "__main__":
    main()
