#!/usr/bin/env python3
"""Pin Python V2 civic regimes, emergency policy and recovery."""

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


def civic_state(world, settlement, company) -> dict[str, object]:
    civic = world.v2.civics[settlement.id]
    plan = world.v2.ration_plans[settlement.id]
    regime = world.v2.emergency_regimes.get(settlement.id)
    return {
        "day": world.day,
        "state": civic.state.value,
        "entered_day": civic.entered_day,
        "food_reserve_days": civic.food_reserve_days,
        "legitimacy": civic.legitimacy,
        "quarantine_until": civic.quarantine_until,
        "war_budget": civic.war_budget,
        "ration_fraction": civic.ration_fraction,
        "reason": civic.reason,
        "ration_plan": {"fraction": plan.fraction, "issued_day": plan.issued_day, "reason": plan.reason},
        "regime": None if regime is None else {
            "state": regime.state.value,
            "activated_day": regime.activated_day,
            "war_budget": regime.war_budget,
            "quarantine_until": regime.quarantine_until,
            "status": regime.status,
        },
        "company_wage": company.wage_offer,
        "route_insurance": [
            {"route": f"{key[0]}:{key[1]}", "underwriter": item.underwriter_id, "premium": item.premium,
             "coverage": item.coverage, "expires_day": item.expires_day, "status": item.status}
            for key, item in world.v2.route_insurance.items()
        ],
        "last_event": world.events[-1] if world.events else None,
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        world = world_module.World(world_module.WorldConfig(seed=41, infection_seeds=0, v2=True))
        settlement = world.settlements[1]
        company = world.microeconomy.companies[1]
        world.v2.update_civics(world)
        normal = civic_state(world, settlement, company)
        settlement.threat, settlement.illness_burden = 0.40, 0.30
        world.day = 5
        world.v2.update_civics(world)
        emergency = civic_state(world, settlement, company)
        settlement.threat = 0.70
        world.day = 6
        world.v2.update_civics(world)
        siege = civic_state(world, settlement, company)
        settlement.threat, settlement.illness_burden = 0.05, 0.0
        world.day = 7
        world.v2.update_civics(world)
        recovery = civic_state(world, settlement, company)

        quiet = world_module.World(world_module.WorldConfig(seed=72, infection_seeds=0, v2=True))
        before = {str(company_id): company.wage_offer for company_id, company in quiet.microeconomy.companies.items()}
        quiet.v2.update_civics(quiet)
        after = {str(company_id): company.wage_offer for company_id, company in quiet.microeconomy.companies.items()}
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "normal": normal,
            "emergency": emergency,
            "siege": siege,
            "recovery": recovery,
            "quiet_wages": {"before": before, "after": after},
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
        raise SystemExit(f"V2 civic-policy traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "v2.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2 civic-policy micro-trace differs from the active Python reference")
        print("V2 civic-policy micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2 civic-policy micro-trace: {args.output}")


if __name__ == "__main__":
    main()
