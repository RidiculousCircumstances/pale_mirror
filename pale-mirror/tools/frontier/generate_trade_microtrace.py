#!/usr/bin/env python3
"""Generate the source-pinned aggregate TradeNetwork micro-trace."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 1


def route_state(route: object, day: int) -> dict[str, object]:
    return {
        "key": list(route.key()),
        "capacity": route.capacity_on(),
        "capacity_on_day": route.capacity_on(day),
        "infection": route.infection_on(),
        "infection_on_day": route.infection_on(day),
    }


def path_state(path: object) -> object:
    if path is None:
        return None
    cost, nodes, edges = path
    return {"cost": cost, "nodes": list(nodes), "edges": [list(edge.key()) for edge in edges]}


def trade_state(record: object) -> dict[str, object]:
    return {
        "day": record.day,
        "seller": record.seller_id,
        "buyer": record.buyer_id,
        "resource": record.resource.value,
        "shipped": record.shipped,
        "delivered": record.delivered,
        "unit_price": record.unit_price,
        "value": record.value,
        "path": list(record.path),
    }


def settlement(identifier: int, cash: float, profile: object, settlement_type: object, natural: object, facilities: object) -> object:
    return settlement_type(identifier, str(identifier), 0, 0, 100.0, cash, natural(), facilities(), profile=profile)


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.economy import EconomyEngine, Resource
        from simulation.profiles import GRAYBOX_1_40, SOURCE_V2
        from simulation.settlement import Facilities, NaturalPotential, Settlement
        from simulation.trade import Route, TradeNetwork

        economy = EconomyEngine(SOURCE_V2)
        seller = settlement(1, 0.0, SOURCE_V2, Settlement, NaturalPotential, Facilities)
        buyer = settlement(2, 100.0, SOURCE_V2, Settlement, NaturalPotential, Facilities)
        intermediate = settlement(3, 0.0, SOURCE_V2, Settlement, NaturalPotential, Facilities)
        seller.add(Resource.FOOD, 500.0)
        settlements = {1: seller, 2: buyer, 3: intermediate}
        network = TradeNetwork(economy)
        first = Route(1, 3, 5.0, 100.0, risk=0.2, quality=0.5, infection=0.4,
                      quarantine_until=5, disruption_until=9, disruption_multiplier=0.5,
                      checkpoint_capacity_multiplier=0.8)
        second = Route(3, 2, 5.0, 100.0, risk=0.1, quality=0.8, infection=0.2)
        direct = Route(1, 2, 13.0, 100.0, risk=0.4, quality=0.25, infection=0.5)
        for route in (first, second, direct, Route(1, 1, 1.0, 1.0), Route(2, 1, 1.0, 1.0)):
            network.add_route(route)
        normal_path = network.shortest_path(1, 2, Resource.FOOD, settlements, 20)
        quarantine_path = network.shortest_path(1, 2, Resource.FOOD, settlements, 5)
        signals = network.market_signal_for(1, settlements)
        records = network.clear_day(20, settlements, max_matches_per_resource=1)
        intermediate.alive = False
        broken_with_direct = network.shortest_path(1, 2, Resource.FOOD, settlements, 20)
        disconnected = TradeNetwork(economy)
        disconnected.add_route(first)
        disconnected.add_route(second)
        fully_broken = disconnected.shortest_path(1, 2, Resource.FOOD, settlements, 20)

        gray_economy = EconomyEngine(GRAYBOX_1_40)
        gray_seller = settlement(1, 0.0, GRAYBOX_1_40, Settlement, NaturalPotential, Facilities)
        gray_buyer = settlement(2, gray_economy.human_amount(1_000.0), GRAYBOX_1_40, Settlement, NaturalPotential, Facilities)
        gray_seller.add(Resource.FOOD, gray_economy.human_amount(2_000.0))
        gray_settlements = {1: gray_seller, 2: gray_buyer}
        gray_network = TradeNetwork(gray_economy)
        gray_network.add_route(Route(1, 2, 13.0, gray_economy.human_amount(100.0), risk=0.4, quality=0.25, infection=0.5))
        gray_records = gray_network.clear_day(20, gray_settlements, max_matches_per_resource=1)
        return canonical_bytes({
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "trade_sha256": files["simulation/trade.py"]},
            "routes": {
                "count_after_rejections": len(network.routes),
                "first_normal": route_state(first, 20),
                "first_quarantined": route_state(first, 5),
                "shadow_cost_normal": network.shadow_cost(first, Resource.FOOD, 20),
                "shadow_cost_quarantined": network.shadow_cost(first, Resource.FOOD, 5),
            },
            "paths": {
                "normal": path_state(normal_path),
                "quarantined": path_state(quarantine_path),
                "dead_intermediate_with_direct": path_state(broken_with_direct),
                "dead_intermediate_no_direct": path_state(fully_broken),
            },
            "market_signal_food": signals[Resource.FOOD],
            "clearing": {
                "records": [trade_state(record) for record in records],
                "seller_food": seller.amount(Resource.FOOD),
                "buyer_food": buyer.amount(Resource.FOOD),
                "seller_cash": seller.cash,
                "buyer_cash": buyer.cash,
                "recent_volume_day_20": network.recent_volume(5, 20),
                "recent_volume_day_25": network.recent_volume(5, 25),
            },
            "graybox": {
                "records": [trade_state(record) for record in gray_records],
                "seller_food": gray_seller.amount(Resource.FOOD),
                "buyer_food": gray_buyer.amount(Resource.FOOD),
                "seller_cash": gray_seller.cash,
                "buyer_cash": gray_buyer.cash,
            },
        })
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
            raise SystemExit("trade micro-trace differs from the active Python reference")
        print("Frontier trade micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned trade micro-trace to {output}")


if __name__ == "__main__":
    main()
