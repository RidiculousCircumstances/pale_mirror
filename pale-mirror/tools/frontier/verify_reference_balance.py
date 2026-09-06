#!/usr/bin/env python3
"""Verify Java graybox calibration inputs against the separately owned Python model."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
import sys
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--reference-root", type=Path, required=True,
                        help="absolute path to the independent pale_mirror_ai checkout")
    args = parser.parse_args()
    root = args.reference_root.resolve()
    fixture_path = Path(__file__).resolve().parents[2] / "docs" / "frontier-reference-balance.json"
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    balance_path = root / fixture["source"]["path"]
    actual_hash = hashlib.sha256(balance_path.read_bytes()).hexdigest()
    if actual_hash != fixture["source"]["sha256"]:
        raise SystemExit(f"reference balance hash changed: {actual_hash}; refresh fixture deliberately")
    sys.path.insert(0, str(root))
    balance = importlib.import_module("simulation.balance").BALANCE

    for key, value in fixture["world"].items():
        if balance["world"][key] != value:
            raise SystemExit(f"world.{key}: expected {value!r}, found {balance['world'][key]!r}")
    personnel = balance["strategy"]["operations"]["personnel"]["defend"]
    requirements = balance["strategy"]["operations"]["requirements"]["defend"]
    expected_defend = fixture["operations"]["defend"]
    actual_defend = {
        "minimum_personnel": personnel["minimum"],
        "population_ratio": personnel["population_ratio"],
        **{key: requirements[key] for key in ("food", "medicine", "weapons", "ammo")},
    }
    if actual_defend != expected_defend:
        raise SystemExit(f"operations.defend: expected {expected_defend!r}, found {actual_defend!r}")
    for key in ("human_speed_offroad", "infection_speed"):
        if balance["strategy"]["operations"][key] != fixture["operations"][key]:
            raise SystemExit(f"operations.{key} changed")
    campaign = fixture["campaign"]
    raid_nest = balance["strategy"]["operations"]
    expected_raid = campaign["raid_nest"]
    actual_raid = {
        "minimum_personnel": raid_nest["personnel"]["raid_nest"]["minimum"],
        **{key: raid_nest["requirements"]["raid_nest"][key]
           for key in ("food", "medicine", "weapons", "ammo")},
    }
    if actual_raid != expected_raid:
        raise SystemExit(f"operations.raid_nest: expected {expected_raid!r}, found {actual_raid!r}")
    frontier = balance["v2"]["frontier"]
    expected_frontier = campaign["frontier"]
    actual_frontier = {key: frontier[key] for key in expected_frontier}
    if actual_frontier != expected_frontier:
        raise SystemExit(f"v2.frontier: expected {expected_frontier!r}, found {actual_frontier!r}")
    phases = [phase.name for phase in importlib.import_module("simulation.v2").FrontPhase]
    if phases != campaign["phases"]:
        raise SystemExit(f"v2.FrontPhase: expected {campaign['phases']!r}, found {phases!r}")
    print("Frontier reference balance fixture matches pale_mirror_ai")


if __name__ == "__main__":
    main()
