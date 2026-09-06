#!/usr/bin/env python3
"""Pin Python operation vocabulary, human launch admission and source paths."""

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


def operation_view(operation: object) -> dict[str, object]:
    return {
        "id": operation.id,
        "owner": operation.owner.key(),
        "target": operation.target.key(),
        "status": operation.status.value,
        "personnel": operation.personnel,
        "power": operation.power,
        "supplies": {resource.value: amount for resource, amount in operation.supplies.items() if amount},
        "composition": {
            str(settlement_id): {role.value: amount for role, amount in composition.items()}
            for settlement_id, composition in operation.unit_composition_by_settlement.items()
        },
        "waypoints": [list(point) for point in operation.waypoints],
        "return_waypoints": [list(point) for point in operation.return_waypoints],
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        economy = importlib.import_module("simulation.economy")
        operations = importlib.import_module("simulation.operations")
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        target = operations.TargetRef(operations.TargetKind.CELL, x=10, y=10)
        world = world_module.World(world_module.WorldConfig(seed=41, infection_seeds=0, v2=True))
        requirements = world.operations.requirements_for(world, operations.OperationKind.RECON, 1, target)
        operation = world.operations.launch_human(world, operations.OperationKind.RECON, 1, target)
        assert operation is not None

        rejected_world = world_module.World(world_module.WorldConfig(
            seed=41, infection_seeds=0, v2=True, profile="graybox_1_40"
        ))
        settlement = rejected_world.settlements[1]
        before = {resource.value: settlement.amount(resource) for resource in economy.Resource}
        settlement.remove(economy.Resource.AMMO, settlement.amount(economy.Resource.AMMO))
        after_ammo_removal = {resource.value: settlement.amount(resource) for resource in economy.Resource}
        available_before = len(settlement.residents.available_ids())
        rejected = rejected_world.operations.launch_human(rejected_world, operations.OperationKind.RECON, 1, target)
        after = {resource.value: settlement.amount(resource) for resource in economy.Resource}

        post_world = world_module.World(world_module.WorldConfig(
            seed=41, infection_seeds=0, v2=True, profile="graybox_1_40"
        ))
        post_settlement = post_world.settlements[1]
        garrison = tuple(post_settlement.residents.available_ids()[:4])
        post_settlement.assign_people_to_field_post(garrison, 1)
        campaign = post_world.field.create_campaign(
            post_world, importlib.import_module("simulation.field").CampaignKind.CONTAINMENT, 1, set(),
            target_kind="cell", target_id=None, target_x=10, target_y=10, reason="operation-launch",
        )
        assert campaign is not None
        field = importlib.import_module("simulation.field")
        post = post_world.field.start_post(
            post_world, campaign.id, field.FieldPostKind.CHECKPOINT, 1, 1, 1, {1}, {1: 4.0}, {}, {1: garrison},
        )
        assert post is not None
        post.status = field.FieldPostStatus.ACTIVE
        post_rejected = post_world.operations.launch_from_post(
            post_world, operations.OperationKind.RECON, 1, post.id, target, campaign_id=campaign.id,
            participant_ids={1}, details={"test": "post-launch"},
        )
        recovered_garrison = list(post.resident_ids_by_settlement[1])
        post.receive_cargo({
            economy.Resource.FOOD: 2.0, economy.Resource.MEDICINE: .1,
            economy.Resource.WEAPONS: .1, economy.Resource.AMMO: .3,
        })
        post_operation = post_world.operations.launch_from_post(
            post_world, operations.OperationKind.RECON, 1, post.id, target, campaign_id=campaign.id,
            participant_ids={1}, details={"test": "post-launch"},
        )
        assert post_operation is not None
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "vocabulary": {
                "agent": [value.value for value in operations.AgentKind],
                "target": [value.value for value in operations.TargetKind],
                "operation": [value.value for value in operations.OperationKind],
                "status": [value.value for value in operations.OperationStatus],
                "directive": [value.value for value in operations.DirectiveKind],
            },
            "requirements": {resource.value: amount for resource, amount in requirements.items()},
            "operation": operation_view(operation),
            "event": world.events[-1],
            "intel": {
                "future_day": operations.IntelRecord(target, 4, 0.8, "recon").confidence_on(3),
                "six_days": operations.IntelRecord(target, 4, 0.8, "recon").confidence_on(10),
            },
            "negative": {
                "initial_stock_changed_by_setup": before != after_ammo_removal,
                "rejected": rejected is None,
                "stock_unchanged_after_rejection": after_ammo_removal == after,
                "available_before": available_before,
                "available_after": len(settlement.residents.available_ids()),
                "active_operations": len(rejected_world.operations.active),
            },
            "post_launch": {
                "rejected_without_supplies": post_rejected is None,
                "garrison_recovered": recovered_garrison,
                "operation": operation_view(post_operation),
                "remaining_garrison": list(post.resident_ids_by_settlement[1]),
                "remaining_stock": {resource.value: amount for resource, amount in post.stock.items() if amount},
                "event": post_world.events[-1],
            },
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
        raise SystemExit(f"Operation-launch traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "operations.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("Operation-launch micro-trace differs from the active Python reference")
        print("Operation-launch micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote operation-launch micro-trace: {args.output}")


if __name__ == "__main__":
    main()
