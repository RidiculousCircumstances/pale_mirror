#!/usr/bin/env python3
"""Pin Python V2 settlement decisions, a chrysalis charter and frontier authorisation."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
from math import hypot
from pathlib import Path
import sys


REFERENCE_PYTHON = (3, 11)


def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def digest(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def source_manifest(root: Path) -> tuple[dict[str, str], str]:
    files = [*root.joinpath("simulation").rglob("*.py"), root / "requirements.txt"]
    values = {path.relative_to(root).as_posix(): digest(path.read_bytes()) for path in sorted(files) if path.is_file()}
    return values, digest(canonical(values))


def source_world(world_module: object) -> object:
    return world_module.World(world_module.WorldConfig(
        width=64, height=44, settlement_count=12, seed=41, infection_seeds=0, v2=True, profile="source_v2",
    ))


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        v2 = importlib.import_module("simulation.v2")
        world_module = importlib.import_module("simulation.world")
        resources = importlib.import_module("simulation.settlement")
        files, tree = source_manifest(root)
        world = source_world(world_module)
        world.day = 1
        leader = world.settlements[1]
        partner = next(item for item in sorted(world.settlements.values(), key=lambda item: item.id)
                       if item.id != leader.id and hypot(item.x - leader.x, item.y - leader.y) <= 22.0)
        leader.facilities.armory = 1.0
        partner.facilities.armory = 1.0
        world.v2.doctrines[leader.id].solidarity = .9
        world.v2.doctrines[partner.id].solidarity = .9
        target = world.v2.sectors["0:0"]
        world.v2.human_perceptions[leader.id].beliefs[target.key] = v2.Belief(
            target.key, world.day, .91, v2.ObservationSource.SCOUT, .42, target.organic_mass,
            .30, 1.25, True,
        )
        world.v2.sector_control[target.key].state = v2.SectorControlState.HIVE
        leader.stock[resources.Resource.AMMO] = 100.0
        world.v2.plan_humans(world)
        decision = next(item for item in reversed(world.v2.decision_history) if item["agent"] == f"settlement:{leader.id}")
        charter = next(iter(world.v2.charters.values()))
        campaign = next(iter(world.v2.front_campaigns.values()))
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "decision": decision,
            "charter": {
                "id": charter.id, "leader": charter.leader_id, "members": list(charter.members), "target": charter.target,
                "expires": charter.expires_day, "status": charter.status, "reason": charter.reason,
                "contribution": charter.contribution, "reserve": charter.reserve_commitment, "compensation": charter.compensation_due,
            },
            "campaign": {
                "id": campaign.id, "leader": campaign.leader_id, "contributors": list(campaign.contributors), "target": campaign.target_sector,
                "phase": campaign.phase.value, "personnel": campaign.personnel_by_settlement,
                "roles": {str(key): {kind.value: amount for kind, amount in value.items()}
                          for key, value in campaign.unit_composition_by_settlement.items()},
            },
            "events": world.events[-2:],
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
        raise SystemExit(f"V2-human-planner traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "v2.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2-human-planner micro-trace differs from the active Python reference")
        print("V2-human-planner micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2-human-planner micro-trace: {args.output}")


if __name__ == "__main__":
    main()
