#!/usr/bin/env python3
"""Pin Python field campaign, post, cargo, module and corridor admission."""

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
    digests = {path.relative_to(root).as_posix(): digest(path.read_bytes()) for path in sorted(files) if path.is_file()}
    return digests, digest(canonical_bytes(digests))


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        field_module = importlib.import_module("simulation.field")
        economy_module = importlib.import_module("simulation.economy")
        files, tree = source_manifest(root)
        world = world_module.World(world_module.WorldConfig(seed=41, infection_seeds=0, v2=True))
        campaign = world.field.create_campaign(
            world, field_module.CampaignKind.CONTAINMENT, 1, {2}, target_kind="cell", target_id=None,
            target_x=10, target_y=10, reason="microtrace",
        )
        assert campaign is not None
        cargo = {resource: 0.0 for resource in economy_module.Resource}
        cargo.update({
            economy_module.Resource.TIMBER: 100.0, economy_module.Resource.ORE: 50.0,
            economy_module.Resource.TOOLS: 30.0, economy_module.Resource.MEDICINE: 2.0,
            economy_module.Resource.FOOD: 60.0, economy_module.Resource.AMMO: 100.0,
            economy_module.Resource.WEAPONS: 100.0,
        })
        post = world.field.start_post(
            world, campaign.id, field_module.FieldPostKind.CHECKPOINT, 1, 1, 1, {1, 2}, {1: 4.0, 2: 2.0}, cargo,
            {1: ("resident:1:3", "resident:1:1"), 2: ("resident:2:2",)},
        )
        second = world.field.start_post(
            world, campaign.id, field_module.FieldPostKind.OBSERVATION, 7, 1, 1, {1, 2}, {1: 1.0}, {}, {},
        )
        assert post is not None and second is not None
        link = world.field.start_link(world, campaign.id, field_module.FieldLinkKind.SUPPLY_CORRIDOR, post.id, second.id)
        assert link is not None
        post.status = field_module.FieldPostStatus.ACTIVE
        link.status = "active"
        module_started = world.field.start_module(world, post.id, field_module.FieldModuleKind.DEPOT)
        post.modules.add(field_module.FieldModuleKind.DEPOT)
        post_power = world.field._post_power(post)
        post.isolation_days = 3
        isolated_power = world.field._post_power(post)
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "campaign": {"id": campaign.id, "contributors": sorted(campaign.contributors), "phase": campaign.phase.value,
                         "event": world.events[0]},
            "post": {
                "id": post.id, "status": post.status.value, "build_days": post.build_days_remaining, "garrison": post.garrison,
                "stock": {resource.value: amount for resource, amount in post.stock.items() if amount},
                "stored": post.stored_volume, "capacity": post.storage_capacity,
                "residents": {str(key): list(value) for key, value in post.resident_ids_by_settlement.items()},
                "module_started": module_started, "module_project": {key.value: value for key, value in post.module_projects.items()},
                "post_power": post_power, "isolated_power": isolated_power,
            },
            "second": {"id": second.id, "position": [second.x, second.y]},
            "link": {"id": link.id, "status": link.status, "build_days": link.build_days_remaining},
            "movement_multiplier": world.field.movement_multiplier(1, 1, 7, 1),
            "negative": {
                "overlapping_post_rejected": world.field.start_post(
                    world, campaign.id, field_module.FieldPostKind.CHECKPOINT, 2, 1, 1, {1}, {1: 1.0}, {}, {}
                ) is None,
                "building_post_module_rejected": not world.field.start_module(world, second.id, field_module.FieldModuleKind.DEPOT),
            },
            "event": world.events[-1],
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
        raise SystemExit(f"Field-foundation traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "field.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("Field-foundation micro-trace differs from the active Python reference")
        print("Field-foundation micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote field-foundation micro-trace: {args.output}")


if __name__ == "__main__":
    main()
