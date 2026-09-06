#!/usr/bin/env python3
"""Pin Python V2 neural-chrysalis formation, maturation and withering."""

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


def prepare(world_module, organ_kind):
    world = world_module.World(world_module.WorldConfig(seed=41, infection_seeds=0, v2=True))
    organ = world.infection.create_nest(12, 12, biomass=170.0, kind=organ_kind.DIGESTIVE_POOL)
    organ.vitality = 100.0
    for y in range(10, 15):
        for x in range(10, 15):
            world.infection.level[y][x] = 0.85
    world.v2.refresh_territory(world)
    return world, organ


def chrysalis(item) -> dict[str, object] | None:
    if item is None:
        return None
    return {
        "organ": item.organ_id,
        "sector": item.sector_key,
        "started_day": item.started_day,
        "remaining": item.days_remaining,
        "biomass_committed": item.biomass_committed,
        "status": item.status,
    }


def lifecycle(world) -> list[dict[str, object]]:
    return [{"component": key, "state": value.value} for key, value in world.v2.hive_lifecycle.items()]


def organ_state(organ) -> dict[str, object]:
    return {
        "id": organ.id,
        "kind": organ.kind.value,
        "role": organ.role,
        "biomass": organ.biomass,
        "vitality": organ.vitality,
        "feral": organ.feral,
        "last_project_day": organ.last_project_day,
    }


def belief_state(item) -> dict[str, object]:
    return {
        "sector": item.sector_key,
        "day": item.observed_day,
        "confidence": item.confidence,
        "source": item.source,
        "chrysalis": item.chrysalis,
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        infection_module = importlib.import_module("simulation.infection")
        files, tree = source_manifest(root)
        world, organ = prepare(world_module, infection_module.OrganKind)
        world.v2.advance_hive_lifecycle(world)
        world.v2.observe(world)
        sector = world.v2.sector_key_at(organ.x, organ.y)
        forming = {
            "day": world.day,
            "organ": organ_state(organ),
            "chrysalis": chrysalis(world.v2.chrysalises.get(organ.id)),
            "lifecycle": lifecycle(world),
            "belief": belief_state(world.v2.hive_perception.beliefs[sector]),
            "event": world.events[-1],
        }
        days = []
        for _ in range(20):
            world.day += 1
            world.v2.refresh_territory(world)
            world.v2.advance_hive_lifecycle(world)
            days.append({
                "day": world.day,
                "organ": organ_state(organ),
                "chrysalis": chrysalis(world.v2.chrysalises.get(organ.id)),
                "lifecycle": lifecycle(world),
                "event": world.events[-1],
            })
        withering, withered_organ = prepare(world_module, infection_module.OrganKind)
        withering.v2.advance_hive_lifecycle(withering)
        withering.day += 1
        withered_organ.biomass = 0.0
        withering.v2.refresh_territory(withering)
        withering.v2.advance_hive_lifecycle(withering)
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "forming": forming,
            "days": days,
            "withering": {
                "day": withering.day,
                "organ": organ_state(withered_organ),
                "chrysalis": chrysalis(withering.v2.chrysalises.get(withered_organ.id)),
                "lifecycle": lifecycle(withering),
                "event": withering.events[-1],
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
        raise SystemExit(f"V2 hive-lifecycle traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "v2.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2 hive-lifecycle micro-trace differs from the active Python reference")
        print("V2 hive-lifecycle micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2 hive-lifecycle micro-trace: {args.output}")


if __name__ == "__main__":
    main()
