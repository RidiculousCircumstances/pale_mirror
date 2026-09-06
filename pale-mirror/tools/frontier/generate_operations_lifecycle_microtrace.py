#!/usr/bin/env python3
"""Pin Python daily operation and field-lifecycle behaviour for the Java port."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
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


def operation_view(operation: object, resource: object) -> dict[str, object]:
    return {
        "status": operation.status.value,
        "phase": operation.phase.value,
        "position": [operation.x, operation.y],
        "waypoint_index": operation.waypoint_index,
        "station_days": operation.station_days,
        "unsupplied_days": operation.unsupplied_days,
        "outcome": operation.outcome,
        "finished_day": operation.finished_day,
        "personnel": operation.personnel,
        "food": operation.supplies[resource.FOOD],
        "medicine": operation.supplies[resource.MEDICINE],
    }


def recon_trace(world_module: object, operations: object, resource: object, profile: str) -> dict[str, object]:
    world = world_module.World(world_module.WorldConfig(
        width=64, height=44, settlement_count=12, seed=41, infection_seeds=0, v2=True, profile=profile,
    ))
    settlement = world.settlements[1]
    operation = world.operations.launch_human(
        world, operations.OperationKind.RECON, 1, operations.TargetRef(operations.TargetKind.CELL, x=10, y=10),
    )
    assert operation is not None
    snapshots: dict[str, object] = {}
    for day in range(1, 33):
        world.day = day
        world.operations.step(world)
        if day in {1, 16, 32}:
            snapshots[str(day)] = operation_view(operation, resource)
    return {
        "launch_resident_ids": list(operation.resident_ids_by_settlement.get(1, ())),
        "snapshots": snapshots,
        "active_after_day_32": operation in world.operations.active,
        "completed_after_day_32": operation in world.operations.completed,
        "settlement": {
            "population": settlement.population,
            "mobilized": settlement.mobilized_personnel,
            "available": None if settlement.residents is None else len(settlement.residents.available_ids()),
        },
        "event": world.events[-1],
    }


def field_trace(world_module: object, field_module: object, economy: object, infection: object) -> dict[str, object]:
    world = world_module.World(world_module.WorldConfig(
        width=64, height=44, settlement_count=12, seed=41, infection_seeds=0, v2=True,
    ))
    campaign = world.field.create_campaign(
        world, field_module.CampaignKind.CONTAINMENT, 1, set(), target_kind="cell", target_id=None,
        target_x=10, target_y=10, reason="daily-states",
    )
    assert campaign is not None
    post = world.field.start_post(
        world, campaign.id, field_module.FieldPostKind.CHECKPOINT, 1, 1, 1, {1}, {1: 4.0}, {}, {},
    )
    assert post is not None
    snapshots: dict[str, object] = {}
    for day in range(1, 5):
        world.day = day
        world.field.step(world)
        snapshots[str(day)] = {
            "status": post.status.value,
            "build_days_remaining": post.build_days_remaining,
            "isolation_days": post.isolation_days,
            "food": post.stock[economy.Resource.FOOD],
            "medicine": post.stock[economy.Resource.MEDICINE],
        }
    engagement_world = world_module.World(world_module.WorldConfig(
        width=64, height=44, settlement_count=2, seed=151, infection_seeds=0, v2=False, profile="source_v2",
    ))
    engagement_world.infection.level = [[0.0 for _ in range(engagement_world.config.width)] for _ in range(engagement_world.config.height)]
    engagement_campaign = engagement_world.field.create_campaign(
        engagement_world, field_module.CampaignKind.CONTAINMENT, 1, {1}, target_kind="cell", target_id=None,
        target_x=20, target_y=20, reason="engagement",
    )
    assert engagement_campaign is not None
    strongpoint = engagement_world.field.start_post(
        engagement_world, engagement_campaign.id, field_module.FieldPostKind.STRONGPOINT, 15, 15, 1, {1}, {1: 30.0},
        {economy.Resource.FOOD: 100.0, economy.Resource.MEDICINE: 10.0, economy.Resource.WEAPONS: 20.0,
         economy.Resource.AMMO: 60.0, economy.Resource.TIMBER: 100.0, economy.Resource.ORE: 100.0, economy.Resource.TOOLS: 100.0}, {},
    )
    assert strongpoint is not None
    for day in range(1, 7):
        engagement_world.day = day
        engagement_world.field.step(engagement_world)
    swarm = infection.Swarm(901, 16.0, 15.0, 120.0, -1, kind=infection.BioformKind.RAIDER)
    engagement_world.infection.swarms.append(swarm)
    engagement_world.field.detect_swarms(engagement_world)
    engagement_world.day = 7
    engagement_world.field.step(engagement_world)
    engagement = next(iter(engagement_world.field.engagements.values()))
    return {
        "snapshots": snapshots,
        "event": world.events[-1],
        "post_defence": {
            "event": engagement_world.events[-2],
            "swarm_power": swarm.power,
            "post_integrity": strongpoint.integrity,
            "post_garrison": strongpoint.garrison,
            "post_wounded": strongpoint.wounded,
            "ammo": strongpoint.stock[economy.Resource.AMMO],
            "engagement": {
                "id": engagement.id, "status": engagement.status.value, "days": engagement.days,
                "attacker_power": engagement.attacker_power, "defender_power": engagement.defender_power,
                "killed": engagement.killed, "wounded": engagement.wounded,
            },
        },
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        economy = importlib.import_module("simulation.economy")
        field = importlib.import_module("simulation.field")
        infection = importlib.import_module("simulation.infection")
        operations = importlib.import_module("simulation.operations")
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "source_v2_recon": recon_trace(world_module, operations, economy.Resource, "source_v2"),
            "graybox_recon": recon_trace(world_module, operations, economy.Resource, "graybox_1_40"),
            "field_post": field_trace(world_module, field, economy, infection),
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
        raise SystemExit(f"Operation-lifecycle traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "operations.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("Operation-lifecycle micro-trace differs from the active Python reference")
        print("Operation-lifecycle micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote operation-lifecycle micro-trace: {args.output}")


if __name__ == "__main__":
    main()
