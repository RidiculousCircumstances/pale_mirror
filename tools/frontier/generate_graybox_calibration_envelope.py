#!/usr/bin/env python3
"""Generate a behavioural graybox_1_40 calibration envelope from Python."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
from pathlib import Path
import sys
from statistics import median

from generate_reference_trace import source_manifest


SCHEMA = 1
PROFILE = "graybox_1_40"
SEEDS = (7, 17, 41, 73)
DAYS = 365

# These are semantic acceptance bands, not a lossy rewrite of the source
# trace. They are intentionally set before the Java-side assertion and leave
# room for a small numerical drift to choose a different legal future action.
ACCEPTANCE = {
    "minimum_alive_settlements_per_run": 10,
    "trade_active_runs": {"minimum": 3, "maximum": 4},
    "median_population_ratio": {"minimum": 0.65, "maximum": 0.90},
    "median_peak_active_nests": {"minimum": 10.0, "maximum": 25.0},
    "median_peak_active_bioforms": {"minimum": 2.0, "maximum": 15.0},
    "median_destroyed_organs": {"minimum": 25.0, "maximum": 75.0},
    "median_final_ecological_scar": {"minimum": 1000.0, "maximum": 2600.0},
    "median_front_campaigns_started": {"minimum": 10.0, "maximum": 40.0},
    "median_front_campaigns_completed": {"minimum": 8.0, "maximum": 30.0},
    "front_campaign_failed_runs": {"minimum": 0, "maximum": 4},
}


def measure(world_module: object, seed: int) -> dict[str, float | int]:
    world = world_module.World(world_module.WorldConfig(
        width=64, height=44, settlement_count=12, seed=seed, infection_seeds=2,
        v2=True, profile=PROFILE,
    ))
    initial_population = sum(settlement.population for settlement in world.settlements.values())
    world.run(DAYS)
    history = world.history
    final = history[-1]
    peak_illness = max(
        (row["illness_burden"] for rows in world.settlement_history.values() for row in rows),
        default=0.0,
    )
    destroyed = [
        item for item in world.infection.project_history
        if str(item.get("kind", "")).startswith("destroyed:")
    ]
    campaigns = list(world.v2.front_campaigns.values())
    return {
        "seed": seed,
        "days_simulated": world.day,
        "alive_settlements": int(final["alive"]),
        "population_ratio": final["population"] / initial_population if initial_population else 0.0,
        "recent_trade_value": final["trade_30d"],
        "recent_trade_count": sum(record.day > world.day - 30 for record in world.trade.history),
        "peak_infection": max(row["infection"] for row in history),
        "peak_illness_burden": peak_illness,
        "peak_active_nests": max(int(row["nests"]) for row in history),
        "peak_active_bioforms": max(int(row["bioforms"]) for row in history),
        "peak_contaminated_sites": max(int(row["contaminated_sites"]) for row in history),
        "peak_hive_biomass": max(row["hive_biomass"] for row in history),
        "final_ecological_scar": final["ecology_scar"],
        "destroyed_organs": len(destroyed),
        "destroyed_cores": sum(item["kind"] == "destroyed:core" for item in destroyed),
        "remaining_cores": sum(organ.kind.value == "core" for organ in world.infection.nests.values()),
        "front_campaigns_started": len(campaigns),
        "front_campaigns_completed": sum(
            campaign.terminal_outcome is not None and campaign.terminal_outcome.value == "complete"
            for campaign in campaigns
        ),
        "front_campaigns_failed": sum(
            campaign.terminal_outcome is not None and campaign.terminal_outcome.value == "failed"
            for campaign in campaigns
        ),
    }


def between(value: float | int, band: dict[str, float | int]) -> bool:
    return band["minimum"] <= value <= band["maximum"]


def summary(measurements: list[dict[str, float | int]]) -> dict[str, float | int]:
    return {
        "minimum_alive_settlements_per_run": min(int(row["alive_settlements"]) for row in measurements),
        "trade_active_runs": sum(int(row["recent_trade_count"]) > 0 for row in measurements),
        "median_population_ratio": median(float(row["population_ratio"]) for row in measurements),
        "median_peak_active_nests": median(float(row["peak_active_nests"]) for row in measurements),
        "median_peak_active_bioforms": median(float(row["peak_active_bioforms"]) for row in measurements),
        "median_destroyed_organs": median(float(row["destroyed_organs"]) for row in measurements),
        "median_final_ecological_scar": median(float(row["final_ecological_scar"]) for row in measurements),
        "median_front_campaigns_started": median(float(row["front_campaigns_started"]) for row in measurements),
        "median_front_campaigns_completed": median(float(row["front_campaigns_completed"]) for row in measurements),
        "front_campaign_failed_runs": sum(int(row["front_campaigns_failed"]) > 0 for row in measurements),
    }


def assert_acceptance(value: dict[str, float | int]) -> None:
    for key, bound in ACCEPTANCE.items():
        if isinstance(bound, dict):
            if not between(value[key], bound):
                raise AssertionError(f"source calibration {key}={value[key]!r} is outside {bound!r}")
        elif value[key] < bound:
            raise AssertionError(f"source calibration {key}={value[key]!r} is below {bound!r}")


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        _, tree = source_manifest(root)
        measurements = [measure(world_module, seed) for seed in SEEDS]
        aggregate = summary(measurements)
        assert_acceptance(aggregate)
        return {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree},
            "config": {
                "width": 64, "height": 44, "settlements": 12,
                "infection_seeds": 2, "v2": True, "profile": PROFILE,
                "days": DAYS, "seeds": list(SEEDS),
            },
            "measurements": measurements,
            "summary": aggregate,
            "acceptance": ACCEPTANCE,
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
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "world.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = json.dumps(trace(root), sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8") + b"\n"
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("graybox calibration envelope differs from the active Python reference")
        print("graybox calibration envelope matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote graybox calibration envelope: {args.output}")


if __name__ == "__main__":
    main()
