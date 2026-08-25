#!/usr/bin/env python3
"""Pin which Python modules can affect an active ``source_v2`` world.

The Python repository deliberately retains a legacy strategy branch.  This
fixture makes that boundary explicit, so the Java port cannot quietly omit an
active owner or grow a second implementation of a source-v2-unreachable one.
"""

from __future__ import annotations

import argparse
import ast
import hashlib
import importlib
import json
from pathlib import Path
import sys
from typing import Any


REFERENCE_PYTHON = (3, 11)
SCHEMA = 1


def canonical(value: object) -> bytes:
    return json.dumps(value, allow_nan=False, ensure_ascii=True, separators=(",", ":"), sort_keys=True).encode("utf-8")


def digest(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def source_manifest(root: Path) -> tuple[dict[str, str], str]:
    files = [*root.joinpath("simulation").rglob("*.py"), root / "requirements.txt"]
    values = {path.relative_to(root).as_posix(): digest(path.read_bytes()) for path in sorted(files) if path.is_file()}
    return values, digest(canonical(values))


def source_paths(root: Path) -> list[Path]:
    return sorted(root.joinpath("simulation").rglob("*.py"))


def external_name_sites(root: Path, name: str, excluded: set[str]) -> list[str]:
    sites: list[str] = []
    for path in source_paths(root):
        relative = path.relative_to(root).as_posix()
        if relative in excluded:
            continue
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        for node in ast.walk(tree):
            if isinstance(node, ast.Name) and node.id == name:
                sites.append(f"{relative}:{node.lineno}")
    return sorted(sites)


def attribute_call_sites(root: Path, owner: str, method: str) -> list[str]:
    sites: list[str] = []
    for path in source_paths(root):
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or not isinstance(node.func, ast.Attribute):
                continue
            receiver = node.func.value
            if node.func.attr == method and isinstance(receiver, ast.Attribute) and receiver.attr == owner:
                sites.append(f"{path.relative_to(root).as_posix()}:{node.lineno}")
    return sorted(sites)


def default_profile_value(config: object) -> str:
    value = getattr(config, "profile")
    return getattr(value, "value", str(value))


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        defaults = world_module.WorldConfig()
        if not defaults.v2 or default_profile_value(defaults) != "source_v2":
            raise ValueError("the reference default must remain the active source_v2 profile")

        world = world_module.World(world_module.WorldConfig(
            width=64, height=44, settlement_count=12, seed=41, infection_seeds=2, v2=True, profile="source_v2",
        ))
        if world.strategy is not None or not hasattr(world, "v2"):
            raise ValueError("an active source_v2 world must use V2 state and no legacy strategy")
        if hasattr(world, "expeditions"):
            raise ValueError("ExpeditionManager unexpectedly became a World owner")
        if vars(world.commands):
            raise ValueError("CommandExecutor unexpectedly acquired source_v2 state")
        if vars(world.engine):
            raise ValueError("SimulationEngine unexpectedly acquired source_v2 state")

        world.run(30)
        if world.strategy is not None:
            raise ValueError("the legacy strategy became reachable during a source_v2 tick")

        command_sites = attribute_call_sites(root, "commands", "execute")
        if any(not item.startswith("simulation/strategy.py:") for item in command_sites):
            raise ValueError(f"source_v2 command call escaped legacy strategy: {command_sites}")
        human_sites = external_name_sites(root, "HumanObjectivePlanner", {"simulation/ai/human.py"})
        field_sites = external_name_sites(root, "FieldCampaignPlanner", {"simulation/ai/field_campaigns.py"})
        expedition_sites = external_name_sites(root, "ExpeditionManager", {"simulation/expedition.py"})
        if any(not item.startswith("simulation/strategy.py:") for item in [*human_sites, *field_sites]):
            raise ValueError("a legacy human planner escaped simulation.strategy")
        if expedition_sites:
            raise ValueError(f"ExpeditionManager unexpectedly has a runtime owner: {expedition_sites}")

        files, tree = source_manifest(root)
        return {
            "schema": SCHEMA,
            "source": {"tree_sha256": tree, "files": files},
            "profile": {
                "default_v2": defaults.v2,
                "default_profile": default_profile_value(defaults),
                "fixture": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 2},
                "day_after_run": world.day,
            },
            "active_owners": [
                "simulation.world", "simulation.engine", "simulation.population", "simulation.settlement",
                "simulation.economy", "simulation.microeconomy", "simulation.trade", "simulation.sites",
                "simulation.ecology", "simulation.infection", "simulation.operations", "simulation.field",
                "simulation.formations", "simulation.v2", "simulation.ai.v2", "simulation.ai.hive", "simulation.views",
            ],
            "source_v2_boundary": {
                "strategy": None,
                "expedition_world_owner": False,
                "commands_stateful": False,
                "engine_stateful": False,
                "legacy_call_sites": {
                    "commands_execute": command_sites,
                    "human_objective_planner": human_sites,
                    "field_campaign_planner": field_sites,
                    "expedition_manager": expedition_sites,
                },
            },
            "unreachable_modules": {
                "simulation.strategy": "WorldConfig(v2=True) assigns strategy=None; engine only steps it without v2.",
                "simulation.intents": "Intent values are only consumed by the legacy strategy command bridge.",
                "simulation.commands": "CommandExecutor is stateless and execute() is called only by the legacy strategy.",
                "simulation.ai.human": "HumanObjectivePlanner is instantiated only by legacy SettlementStrategy.",
                "simulation.ai.field_campaigns": "FieldCampaignPlanner is instantiated only by legacy SettlementStrategy.",
                "simulation.expedition": "ExpeditionManager is not constructed or referenced by World/source_v2.",
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
        raise SystemExit(f"source_v2 reachability requires Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "world.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("source_v2 reachability fixture differs from the active Python reference")
        print("source_v2 reachability matches the active Python reference")
        return
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_bytes(payload)
    print(f"wrote source_v2 reachability fixture: {args.output}")


if __name__ == "__main__":
    main()
