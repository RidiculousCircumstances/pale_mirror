#!/usr/bin/env python3
"""Generate source-pinned bootstrap/projection micro-traces for MarketEconomy."""

from __future__ import annotations

import argparse
from pathlib import Path
from types import SimpleNamespace
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 5


def settlement_state(settlement: object, resources: object) -> dict[str, object]:
    return {
        "cash": settlement.cash,
        "stock": {resource.value: settlement.amount(resource) for resource in resources},
        "primary_capacity": settlement.primary_capacity,
        "facilities": {
            "workshop": settlement.facilities.workshop,
            "armory": settlement.facilities.armory,
            "clinic": settlement.facilities.clinic,
            "fortification": settlement.facilities.fortification,
        },
    }


def company_state(company: object, resources: object) -> dict[str, object]:
    return {
        "id": company.id,
        "name": company.name,
        "sector": company.sector.value,
        "home": company.home_settlement_id,
        "cash": company.cash,
        "capacity": company.capacity,
        "assets": company.assets,
        "sites": sorted(company.site_ids),
        "inventory": {resource.value: company.inventory[resource] for resource in resources},
    }


def daily_state(settlement: object, resources: object) -> dict[str, object]:
    return {
        "production": {resource.value: settlement.daily_production[resource] for resource in resources},
        "consumption": {resource.value: settlement.daily_consumption[resource] for resource in resources},
    }


def market_day_state(world: object, market: object, records: list[object], Resource: object) -> dict[str, object]:
    return {
        "settlements": {str(identifier): {**settlement_state(value, Resource), "daily": daily_state(value, Resource)}
                        for identifier, value in world.settlements.items()},
        "companies": [company_state(company, Resource) | {"employees": company.employees, "debt": company.debt,
                       "wage": company.wage_offer, "last_profit": company.last_profit, "owner_kind": company.owner_kind,
                       "status": company.status} for company in market.companies.values()],
        "households": {str(identifier): {"cash": household.cash, "wage_income": household.wage_income,
                            "dividend_income": household.dividend_income, "food_coverage": household.food_coverage}
                       for identifier, household in market.households.items()},
        "contracts": [{"id": contract.id, "seller": contract.seller_company_id, "buyer": contract.buyer_settlement_id,
                       "resource": contract.resource.value, "daily": contract.daily_quantity, "price": contract.price_index,
                       "status": contract.status, "delivered": contract.delivered, "breached": contract.breached_quantity,
                       "route": contract.route} for contract in market.contracts.values()],
        "credits": [{"id": credit.id, "borrower_kind": credit.borrower_kind, "borrower": credit.borrower_id,
                     "principal": credit.principal, "rate": credit.rate_per_day, "purpose": credit.purpose,
                     "status": credit.status} for credit in market.credits.values()],
        "records": [{"seller": record.seller_id, "buyer": record.buyer_id, "resource": record.resource.value,
                     "shipped": record.shipped, "delivered": record.delivered, "price": record.unit_price,
                     "path": record.path} for record in records],
        "history": market.history,
        "trade_history": [{"seller": record.seller_id, "buyer": record.buyer_id, "resource": record.resource.value,
                           "shipped": record.shipped, "delivered": record.delivered, "price": record.unit_price,
                           "path": record.path} for record in world.trade.history],
        "events": world.events,
    }


def expansion_state(world: object, market: object, records: list[object], Resource: object) -> dict[str, object]:
    return {
        "companies": [{"id": company.id, "cash": company.cash, "debt": company.debt, "capacity": company.capacity,
                       "assets": company.assets, "last_investment_day": company.last_investment_day, "sites": sorted(company.site_ids)}
                      for company in market.companies.values()],
        "reports": [{"id": report.id, "company": report.company_id, "settlement": report.settlement_id,
                     "kind": report.kind.value, "x": report.x, "y": report.y, "quality": report.quality,
                     "confidence": report.confidence, "status": report.status} for report in market.reports.values()],
        "projects": [{"id": project.id, "company": project.company_id, "report": project.report_id,
                      "remaining": project.days_remaining, "status": project.status} for project in market.projects.values()],
        "sites": {str(identifier): {"owner": site.owner_id, "operator": site.operator_company_id,
                                      "capacity": site.capacity, "haul": site.haul_capacity}
                  for identifier, site in world.resource_sites.items()},
        "events": world.events,
        "records": [{"seller": record.seller_id, "buyer": record.buyer_id, "resource": record.resource.value,
                     "shipped": record.shipped, "price": record.unit_price} for record in records],
    }


def completed_projects_state(world: object, market: object) -> dict[str, object]:
    return {
        "reports": [{"id": report.id, "status": report.status} for report in market.reports.values()],
        "projects": [{"id": project.id, "status": project.status, "remaining": project.days_remaining}
                     for project in market.projects.values()],
        "sites": {str(identifier): {"kind": site.kind.value, "owner": site.owner_id,
                                      "operator": site.operator_company_id, "capacity": site.capacity,
                                      "haul": site.haul_capacity} for identifier, site in world.resource_sites.items()},
        "company_sites": {str(identifier): sorted(company.site_ids) for identifier, company in market.companies.items()},
    }


def build(profile: object, Settlement: object, NaturalPotential: object, Facilities: object, Resource: object, ResourceSite: object, SiteKind: object, MarketEconomy: object, EconomyEngine: object, TradeNetwork: object, Route: object) -> dict[str, object]:
    first = Settlement(1, "Alpha", 0, 0, 100.0, 1_000.0, NaturalPotential(), Facilities(1.2, 0.4, 0.6, 0.8), profile=profile)
    second = Settlement(2, "Beta", 9, 0, 80.0, 500.0, NaturalPotential(), Facilities(0.5, 0.8, 0.25, 0.3), profile=profile)
    first_stock = {Resource.FOOD: 200.0, Resource.TIMBER: 50.0, Resource.ORE: 100.0, Resource.ENERGY: 80.0, Resource.TOOLS: 30.0, Resource.MEDICINE: 10.0, Resource.WEAPONS: 20.0, Resource.AMMO: 40.0, Resource.SEEDS: 8.0}
    second_stock = {Resource.FOOD: 120.0, Resource.TIMBER: 20.0, Resource.ORE: 40.0, Resource.ENERGY: 60.0, Resource.TOOLS: 12.0, Resource.MEDICINE: 6.0, Resource.WEAPONS: 14.0, Resource.AMMO: 25.0, Resource.SEEDS: 4.0}
    for resource, amount in first_stock.items():
        first.add(resource, amount if not profile.discrete_people else amount / profile.person_scale)
    for resource, amount in second_stock.items():
        second.add(resource, amount if not profile.discrete_people else amount / profile.person_scale)
    farm = ResourceSite(7, SiteKind.FARM, 2, 0, 0.8, 1.5, 1, condition=0.7, haul_capacity=4.0)
    mine = ResourceSite(3, SiteKind.MINE, 7, 0, 0.9, 1.1, 2, condition=0.6, haul_capacity=6.0)
    world = SimpleNamespace(settlements={1: first, 2: second}, resource_sites={7: farm, 3: mine}, events=[])
    market = MarketEconomy(EconomyEngine(profile))
    market.bootstrap(world)
    farm.add(Resource.FOOD, 7.0 if not profile.discrete_people else 7.0 / profile.person_scale)
    mine.add(Resource.ORE, 11.0 if not profile.discrete_people else 11.0 / profile.person_scale)
    extractions: list[tuple[str, int, int, float]] = []

    class Ecosystem:
        @staticmethod
        def human_output_factor(kind: str, x: int, y: int) -> float:
            return {"farm": 0.75, "mine": 0.9}.get(kind, 1.0)

        @staticmethod
        def human_extract(kind: str, x: int, y: int, amount: float) -> None:
            extractions.append((kind, x, y, amount))

    world.day = 1
    world.infection = SimpleNamespace(ecosystem=Ecosystem(), route_infection=lambda *_: 0.5)
    market.capture_external_warehouse_changes(world)
    for entry in world.settlements.values():
        market.economy.reset_daily_flows(entry)
    market._assign_labour(world)
    market._haul_site_outputs(world)
    market._produce(world)
    market.sync_compatibility(world)
    result = {
        "settlements": {str(identifier): settlement_state(value, Resource) for identifier, value in world.settlements.items()},
        "sites": {str(identifier): {"operator": site.operator_company_id, "owner": site.owner_id} for identifier, site in world.resource_sites.items()},
        "companies": [company_state(company, Resource) for company in market.companies.values()],
        "households": {str(identifier): {
            "workers": household.workers, "owners": household.owners, "dependents": household.dependents, "cash": household.cash,
        } for identifier, household in market.households.items()},
        "licences": [{"id": licence.id, "settlement": licence.settlement_id, "company": licence.company_id, "sector": licence.sector.value, "site": licence.site_id} for licence in market.licences.values()],
        "minimum_workers": market._minimum_workers(),
        "minimum_lot": market._minimum_lot(),
        "seasons": [market.season(day) for day in (0, 1, 30, 31, 60, 61, 90, 91, 120)],
        "daily": {
            "settlements": {str(identifier): daily_state(value, Resource) for identifier, value in world.settlements.items()},
            "sites": {str(identifier): {"stock": site.stock[site.resource]} for identifier, site in world.resource_sites.items()},
            "companies": [{"id": company.id, "employees": company.employees, "wage": company.wage_offer,
                           "cash": company.cash, "inventory": {resource.value: company.inventory[resource] for resource in Resource}}
                          for company in market.companies.values()],
            "extractions": extractions,
        },
    }
    second.stock[Resource.FOOD] = 0.0
    second.cash = 0.0
    world.trade = TradeNetwork(EconomyEngine(profile))
    world.trade.add_route(Route(1, 2, 9.0, 100.0))
    extractions.clear()
    records = market.run_day(world)
    result["market_day"] = market_day_state(world, market, records, Resource)
    result["market_day"]["extractions"] = extractions
    world._place_site_position = lambda host, kind: (host.x + 20 + len(market.reports), host.y - 10 - len(market.reports))
    world._site_cell_quality = lambda kind, x, y: 0.71
    world.day = 91
    records = market.run_day(world)
    result["expansion_day"] = expansion_state(world, market, records, Resource)
    for day in range(92, 102):
        world.day = day
        market.run_day(world)
    result["completed_projects"] = completed_projects_state(world, market)
    return result


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.economy import EconomyEngine, Resource
        from simulation.microeconomy import MarketEconomy
        from simulation.profiles import GRAYBOX_1_40, SOURCE_V2
        from simulation.settlement import Facilities, NaturalPotential, Settlement
        from simulation.sites import ResourceSite, SiteKind
        from simulation.trade import Route, TradeNetwork
        return canonical_bytes({
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "microeconomy_sha256": files["simulation/microeconomy.py"]},
            "source_v2": build(SOURCE_V2, Settlement, NaturalPotential, Facilities, Resource, ResourceSite, SiteKind, MarketEconomy, EconomyEngine, TradeNetwork, Route),
            "graybox": build(GRAYBOX_1_40, Settlement, NaturalPotential, Facilities, Resource, ResourceSite, SiteKind, MarketEconomy, EconomyEngine, TradeNetwork, Route),
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
            raise SystemExit("microeconomy bootstrap micro-trace differs from the active Python reference")
        print("Frontier microeconomy bootstrap micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned microeconomy bootstrap micro-trace to {output}")


if __name__ == "__main__":
    main()
