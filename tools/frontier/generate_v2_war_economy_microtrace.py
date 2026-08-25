#!/usr/bin/env python3
"""Pin Python V2 firm distress, procurement, requisition and compensation."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
from pathlib import Path
import sys


REFERENCE_PYTHON = (3, 11)


def canonical_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def digest(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def source_manifest(root: Path) -> tuple[dict[str, str], str]:
    files = [*root.joinpath("simulation").rglob("*.py"), root / "requirements.txt"]
    digests = {
        path.relative_to(root).as_posix(): digest(path.read_bytes())
        for path in sorted(files)
        if path.is_file()
    }
    return digests, digest(canonical_bytes(digests))


def company_state(company) -> dict[str, object]:
    return {"cash": company.cash, "owner": company.owner_kind, "status": company.status, "v2_state": company.v2_state}


def order_state(order) -> dict[str, object]:
    return {
        "id": order.id,
        "settlement": order.settlement_id,
        "resource": order.resource.value,
        "quantity": order.quantity,
        "max_price": order.max_price,
        "issued_day": order.issued_day,
        "fulfilled": order.fulfilled,
        "status": order.status,
    }


def claim_state(claim) -> dict[str, object]:
    return {
        "id": claim.id,
        "settlement": claim.settlement_id,
        "company": claim.company_id,
        "amount": claim.amount,
        "due_day": claim.due_day,
        "reason": claim.reason,
        "status": claim.status,
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        economy_module = importlib.import_module("simulation.economy")
        v2_module = importlib.import_module("simulation.v2")
        files, tree = source_manifest(root)
        world = world_module.World(world_module.WorldConfig(seed=41, infection_seeds=0, v2=True))
        settlement = world.settlements[1]
        food_company = next(
            item for item in world.microeconomy.companies.values()
            if item.home_settlement_id == settlement.id and item.output is economy_module.Resource.FOOD
        )

        food_company.wage_bill = 2.0
        company_states = []
        for label, cash, owner, status, state in (
            ("insolvent", -20.01, "private", "operating", "operating"),
            ("stressed", 7.99, "private", "operating", "insolvent"),
            ("receivership", 100.0, "municipal_receiver", "operating", "stressed"),
            ("closed", 100.0, "private", "closed", "closed"),
        ):
            food_company.cash, food_company.owner_kind, food_company.status, food_company.v2_state = cash, owner, status, state
            events_before = len(world.events)
            world.v2.update_company_states(world)
            company_states.append({
                "case": label,
                "company": company_state(food_company),
                "event": world.events[-1] if len(world.events) > events_before else None,
            })

        civic = world.v2.civics[settlement.id]
        civic.state = v2_module.CivicState.EMERGENCY
        world.day = 5
        for resource in economy_module.Resource:
            settlement.stock[resource] = 0.0
            world.microeconomy.public_inventory[settlement.id][resource] = 0.0
        food_company.inventory[economy_module.Resource.FOOD] = 20.0
        food_company.cash = 100.0
        settlement.treasury = 0.0
        world.v2.issue_procurement(world)
        procurement = next(iter(world.v2.procurements.values()))
        successful_procurement = {
            "settlement_treasury": settlement.treasury,
            "company_food": food_company.inventory[economy_module.Resource.FOOD],
            "company_cash": food_company.cash,
            "public_food": world.microeconomy.public_inventory[settlement.id][economy_module.Resource.FOOD],
            "order": order_state(procurement),
            "orders": [order_state(item) for item in world.v2.procurements.values()],
            "public_critical": {
                resource.value: world.microeconomy.public_inventory[settlement.id][resource]
                for resource in (economy_module.Resource.FOOD, economy_module.Resource.MEDICINE,
                                 economy_module.Resource.AMMO, economy_module.Resource.WEAPONS)
            },
            "critical_companies": [
                {"id": item.id, "resource": item.output.value, "cash": item.cash,
                 "inventory": item.inventory[item.output]}
                for item in world.microeconomy.companies.values()
                if item.home_settlement_id == settlement.id
                and item.output in {economy_module.Resource.FOOD, economy_module.Resource.MEDICINE,
                                    economy_module.Resource.AMMO, economy_module.Resource.WEAPONS}
            ],
            "credits": [
                {"id": credit.id, "borrower": credit.borrower_kind, "borrower_id": credit.borrower_id,
                 "principal": credit.principal, "rate": credit.rate_per_day, "purpose": credit.purpose, "issued_day": credit.issued_day}
                for credit in world.microeconomy.credits.values()
            ],
            "events": world.events[-3:],
        }

        civic.state = v2_module.CivicState.SIEGE
        world.day = 6
        food_company.inventory[economy_module.Resource.FOOD] = 20.0
        food_company.cash = 100.0
        before_food = food_company.inventory[economy_module.Resource.FOOD]
        before_public = world.microeconomy.public_inventory[settlement.id][economy_module.Resource.FOOD]
        before_legitimacy = world.v2.doctrines[settlement.id].legitimacy
        world.v2._requisition(world, settlement, food_company, economy_module.Resource.FOOD, 5.0, 2.0)
        claim = next(iter(world.v2.compensation.values()))
        pending_compensation = {
            "company_food": food_company.inventory[economy_module.Resource.FOOD],
            "public_food": world.microeconomy.public_inventory[settlement.id][economy_module.Resource.FOOD],
            "food_delta": before_food - food_company.inventory[economy_module.Resource.FOOD],
            "public_delta": world.microeconomy.public_inventory[settlement.id][economy_module.Resource.FOOD] - before_public,
            "legitimacy_delta": world.v2.doctrines[settlement.id].legitimacy - before_legitimacy,
            "claim": claim_state(claim),
            "event": world.events[-1],
        }

        civic.state = v2_module.CivicState.EMERGENCY
        before_count = len(world.v2.compensation)
        before_food = food_company.inventory[economy_module.Resource.FOOD]
        world.v2._requisition(world, settlement, food_company, economy_module.Resource.FOOD, 5.0, 2.0)
        blocked_requisition = {
            "claim_count": len(world.v2.compensation),
            "claim_count_delta": len(world.v2.compensation) - before_count,
            "company_food_delta": food_company.inventory[economy_module.Resource.FOOD] - before_food,
        }

        world.day = claim.due_day
        settlement.treasury = 9.99
        world.v2._settle_compensation(world)
        unpaid = {"treasury": settlement.treasury, "company_cash": food_company.cash, "claim": claim_state(claim)}
        settlement.treasury = 10.0
        world.v2._settle_compensation(world)
        paid = {"treasury": settlement.treasury, "company_cash": food_company.cash, "claim": claim_state(claim), "event": world.events[-1]}

        formatting = world_module.World(world_module.WorldConfig(seed=41, infection_seeds=0, v2=True))
        formatting_settlement = formatting.settlements[1]
        formatting_company = next(
            item for item in formatting.microeconomy.companies.values()
            if item.home_settlement_id == formatting_settlement.id and item.output is economy_module.Resource.FOOD
        )
        formatting.v2.civics[formatting_settlement.id].state = v2_module.CivicState.SIEGE
        formatting.v2._requisition(formatting, formatting_settlement, formatting_company, economy_module.Resource.FOOD, 2.25, 1.0)
        half_even_event = formatting.events[-1]

        unclamped = world_module.World(world_module.WorldConfig(seed=41, infection_seeds=0, v2=True))
        unclamped_settlement = unclamped.settlements[1]
        unclamped_company = next(
            item for item in unclamped.microeconomy.companies.values()
            if item.home_settlement_id == unclamped_settlement.id and item.output is economy_module.Resource.FOOD
        )
        unclamped.v2.civics[unclamped_settlement.id].state = v2_module.CivicState.SIEGE
        unclamped_company.inventory[economy_module.Resource.FOOD] = 1.0
        unclamped.v2._requisition(unclamped, unclamped_settlement, unclamped_company, economy_module.Resource.FOOD, 2.0, 1.0)
        unclamped_requisition = {
            "company_food": unclamped_company.inventory[economy_module.Resource.FOOD],
            "public_food": unclamped.microeconomy.public_inventory[unclamped_settlement.id][economy_module.Resource.FOOD],
            "claim": claim_state(next(iter(unclamped.v2.compensation.values()))),
        }

        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "company_states": company_states,
            "successful_procurement": successful_procurement,
            "pending_compensation": pending_compensation,
            "blocked_requisition": blocked_requisition,
            "unpaid": unpaid,
            "paid": paid,
            "half_even_event": half_even_event,
            "unclamped_requisition": unclamped_requisition,
        }
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
    if sys.version_info[:2] != REFERENCE_PYTHON:
        raise SystemExit(f"V2 war-economy traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "v2.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2 war-economy micro-trace differs from the active Python reference")
        print("V2 war-economy micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2 war-economy micro-trace: {args.output}")


if __name__ == "__main__":
    main()
