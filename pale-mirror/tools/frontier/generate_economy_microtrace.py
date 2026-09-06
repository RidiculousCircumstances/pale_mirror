#!/usr/bin/env python3
"""Generate the source-pinned EconomyEngine phase micro-trace."""

from __future__ import annotations

import argparse
import math
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest


SCHEMA = 2


def state(settlement: object, resources: object) -> dict[str, object]:
    return {
        "stock": {resource.value: settlement.stock[resource] for resource in resources},
        "daily_production": {resource.value: settlement.daily_production[resource] for resource in resources},
        "daily_consumption": {resource.value: settlement.daily_consumption[resource] for resource in resources},
        "food_fulfillment": settlement.food_fulfillment,
        "medicine_fulfillment": settlement.medicine_fulfillment,
    }


def site_state(site: object, resource: object) -> dict[str, float]:
    return {
        "capacity": site.capacity,
        "condition": site.condition,
        "contamination": site.contamination,
        "substrate": site.substrate,
        "stock": site.stock[resource],
    }


def payload(root: Path) -> bytes:
    files, tree_digest = source_manifest(root)
    sys.path.insert(0, str(root))
    try:
        from simulation.economy import EconomyEngine, Resource
        from simulation.profiles import GRAYBOX_1_40, SOURCE_V2
        from simulation.settlement import Facilities, NaturalPotential, Settlement
        from simulation.sites import ResourceSite, SiteKind

        settlement = Settlement(1, "a", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=SOURCE_V2)
        economy = EconomyEngine(SOURCE_V2)
        for resource in Resource:
            settlement.add(resource, 100.0)
        economy.reset_daily_flows(settlement)
        economy.production_phase(settlement)
        production = state(settlement, Resource)
        site = ResourceSite(2, SiteKind.MINE, 0, 0, 0.8, 1.2, 1, haul_capacity=10.0, condition=0.9, contamination=0.2)
        site_output = economy.site_production_phase(settlement, site, 0.7)
        delivered = economy.haul_site_output(settlement, site, 0.5)
        economy.consumption_phase(settlement)
        final = state(settlement, Resource)
        metrics = {
            resource.value: {
                "demand": economy.expected_daily_demand(settlement, resource),
                "target": economy.target_stock(settlement, resource),
                "coverage": (
                    "infinity"
                    if math.isinf(economy.coverage_days(settlement, resource))
                    else economy.coverage_days(settlement, resource)
                ),
                "value": economy.local_value(settlement, resource),
                "sellable": economy.sellable_quantity(settlement, resource),
                "import": economy.desired_import(settlement, resource),
            }
            for resource in Resource
        }
        graybox = Settlement(1, "g", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=GRAYBOX_1_40)
        gray_economy = EconomyEngine(GRAYBOX_1_40)
        projects: dict[str, object] = {}
        for action in ("upgrade", "repair", "cleanse", "scorch", "restore"):
            project_settlement = Settlement(3, action, 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=SOURCE_V2)
            for resource in Resource:
                project_settlement.add(resource, 100.0)
            project_site = ResourceSite(
                4, SiteKind.MINE, 0, 0, 0.8, 1.2, 3,
                condition=0.5, contamination=0.8, substrate=70.0,
            )
            projects[action] = {
                "applied": economy.site_project(project_settlement, project_site, action),
                "settlement": state(project_settlement, Resource),
                "site": site_state(project_site, Resource.ORE),
            }
        rejected_site = ResourceSite(5, SiteKind.MINE, 0, 0, 0.8, 1.2, 3)
        rejected_settlement = Settlement(3, "reject", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=SOURCE_V2)
        projects["rejected"] = {
            "applied": economy.site_project(rejected_settlement, rejected_site, "unknown"),
            "settlement": state(rejected_settlement, Resource),
            "site": site_state(rejected_site, Resource.ORE),
        }
        claim = Settlement(6, "claim", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=SOURCE_V2)
        claim.add(Resource.TIMBER, 12.0)
        claim.add(Resource.ORE, 8.0)
        claim.add(Resource.TOOLS, 8.0)
        claim_before = economy.can_start_claim(claim)
        economy.pay_claim_materials(claim)

        investment = Settlement(7, "investment", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=SOURCE_V2)
        for resource in Resource:
            investment.add(resource, 100.0)
        investment.threat = 0.6
        investment.integrity = 85.0
        accessible = {Resource.TOOLS: 4.5, Resource.WEAPONS: 12.0, Resource.MEDICINE: 7.0}
        first_investment = economy.invest(investment, 100, accessible, {"site": 1.0, "defence": 1.0, "cleanse": 1.0})
        cooldown_investment = economy.invest(investment, 123, accessible, {"site": 1.0, "defence": 1.0, "cleanse": 1.0})
        doctrinal = Settlement(8, "doctrine", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=SOURCE_V2)
        for resource in Resource:
            doctrinal.add(resource, 100.0)
        doctrinal_choice = economy.invest(
            doctrinal, 100, accessible, {"site": 0.1, "defence": 0.1, "cleanse": 3.0},
        )
        gray_projects = Settlement(9, "gray-project", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=GRAYBOX_1_40)
        for resource in Resource:
            gray_projects.add(resource, gray_economy.human_amount(100.0))
        gray_site = ResourceSite(10, SiteKind.MINE, 0, 0, 0.8, 1.2, 9, condition=0.5, contamination=0.8, substrate=70.0)
        gray_upgrade = gray_economy.site_project(gray_projects, gray_site, "upgrade")
        gray_investment = Settlement(11, "gray-investment", 0, 0, 100.0, 0.0, NaturalPotential(), Facilities(), profile=GRAYBOX_1_40)
        for resource in Resource:
            gray_investment.add(resource, gray_economy.human_amount(100.0))
        gray_investment.threat = 0.6
        gray_investment.integrity = 85.0
        gray_choice = gray_economy.invest(gray_investment, 100, accessible, {"site": 1.0, "defence": 1.0, "cleanse": 1.0})
        return canonical_bytes({
            "schema": SCHEMA,
            "source": {"tree_sha256": tree_digest, "economy_sha256": files["simulation/economy.py"]},
            "production": production,
            "site": {"output": site_output, "delivered": delivered, "remaining": site.stock[Resource.ORE]},
            "final": final,
            "metrics": metrics,
            "graybox": {
                "food_rule": gray_economy._resource_rule(Resource.FOOD).__dict__ if hasattr(gray_economy._resource_rule(Resource.FOOD), "__dict__") else [
                    gray_economy._resource_rule(Resource.FOOD).reference_value,
                    gray_economy._resource_rule(Resource.FOOD).reserve_days,
                    gray_economy._resource_rule(Resource.FOOD).scarcity_elasticity,
                    gray_economy._resource_rule(Resource.FOOD).minimum_target,
                ],
                "food_demand": gray_economy.expected_daily_demand(graybox, Resource.FOOD),
                "food_target": gray_economy.target_stock(graybox, Resource.FOOD),
                "upgrade_applied": gray_upgrade,
                "upgrade_settlement": state(gray_projects, Resource),
                "upgrade_site": site_state(gray_site, Resource.ORE),
                "investment_choice": gray_choice,
                "investment": {
                    "settlement": state(gray_investment, Resource),
                    "workshop": gray_investment.facilities.workshop,
                    "armory": gray_investment.facilities.armory,
                    "clinic": gray_investment.facilities.clinic,
                    "fortification": gray_investment.facilities.fortification,
                    "last_investment_day": gray_investment.last_investment_day,
                },
            },
            "site_projects": projects,
            "claim": {
                "can_start_before": claim_before,
                "can_start_after": economy.can_start_claim(claim),
                "settlement": state(claim, Resource),
            },
            "investment": {
                "choice": first_investment,
                "cooldown_choice": cooldown_investment,
                "settlement": state(investment, Resource),
                "workshop": investment.facilities.workshop,
                "armory": investment.facilities.armory,
                "clinic": investment.facilities.clinic,
                "fortification": investment.facilities.fortification,
                "last_investment_day": investment.last_investment_day,
                "doctrinal_choice": doctrinal_choice,
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
            raise SystemExit("economy micro-trace differs from the active Python reference")
        print("Frontier economy micro-trace matches the active Python reference")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(result)
    print(f"wrote source-pinned economy micro-trace to {output}")


if __name__ == "__main__":
    main()
