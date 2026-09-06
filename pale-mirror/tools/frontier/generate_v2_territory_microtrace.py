#!/usr/bin/env python3
"""Pin Python V2 construction, territory derivation and initial perception."""

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


def belief(item) -> dict[str, object]:
    return {
        "sector": item.sector_key,
        "day": item.observed_day,
        "confidence": item.confidence,
        "source": item.source,
        "infection": item.infection,
        "organic": item.organic_mass,
        "hive_influence": item.hive_influence,
        "infrastructure": item.infrastructure_value,
        "chrysalis": item.chrysalis,
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        world = world_module.World(world_module.WorldConfig(seed=42, infection_seeds=2, v2=True))
        v2 = world.v2
        files, tree = source_manifest(root)
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 42, "infection_seeds": 2, "v2": True},
            "sectors": [
                {
                    "key": sector.key,
                    "x": sector.x,
                    "y": sector.y,
                    "cells": [f"{x}:{y}" for x, y in sector.cells],
                    "organic": sector.organic_mass,
                    "moisture": sector.moisture,
                    "scar": sector.scar,
                    "infection": sector.infection,
                    "spores": sector.spore_load,
                    "human_access": sector.human_access,
                    "hive_influence": sector.hive_influence,
                    "infrastructure": sector.infrastructure_value,
                    "routes": [f"{a}:{b}" for a, b in sector.route_keys],
                }
                for sector in v2.sectors.values()
            ],
            "controls": [
                {
                    "key": control.sector_key,
                    "state": control.state.value,
                    "cordon": control.cordon_strength,
                    "garrison": control.garrison,
                    "supplied": control.supplied,
                    "cleared_day": control.last_cleared_day,
                    "changed_day": control.last_changed_day,
                    "held_days": control.held_days,
                    "reason": control.reason,
                }
                for control in v2.sector_control.values()
            ],
            "doctrines": [
                {
                    "settlement": settlement_id,
                    "caution": item.caution,
                    "solidarity": item.solidarity,
                    "commercial_dependence": item.commercial_dependence,
                    "militancy": item.militancy,
                    "casualty_tolerance": item.casualty_tolerance,
                    "quarantine_willingness": item.quarantine_willingness,
                    "legitimacy": item.legitimacy,
                }
                for settlement_id, item in v2.doctrines.items()
            ],
            "civics": [{"settlement": settlement_id, **item.summary()} for settlement_id, item in v2.civics.items()],
            "reserves": [
                {"settlement": settlement_id, "food_days": item.food_days, "medicine_days": item.medicine_days,
                 "ammo_target": item.ammo_target, "active": item.active}
                for settlement_id, item in v2.reserve_policies.items()
            ],
            "rations": [
                {"settlement": settlement_id, "fraction": item.fraction, "issued_day": item.issued_day, "reason": item.reason}
                for settlement_id, item in v2.ration_plans.items()
            ],
            "human_perceptions": [
                {"settlement": settlement_id, "beliefs": [belief(item) for item in perception.beliefs.values()]}
                for settlement_id, perception in v2.human_perceptions.items()
            ],
            "hive_perception": [belief(item) for item in v2.hive_perception.beliefs.values()],
            "routes": [
                {"key": f"{route.a}:{route.b}", "sectors": list(route.sector_keys), "infection": route.sector_infection}
                for route in world.trade.routes
            ],
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
        raise SystemExit(f"V2 territory traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "v2.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2 territory micro-trace differs from the active Python reference")
        print("V2 territory micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2 territory micro-trace: {args.output}")


if __name__ == "__main__":
    main()
