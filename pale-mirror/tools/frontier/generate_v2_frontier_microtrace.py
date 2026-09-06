#!/usr/bin/env python3
"""Pin Python V2 territorial control, supply and cordon resolution for the Java port."""

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


def source_world(world_module: object) -> object:
    return world_module.World(world_module.WorldConfig(
        width=64, height=44, settlement_count=12, seed=41, infection_seeds=0, v2=True, profile="source_v2",
    ))


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        infection = importlib.import_module("simulation.infection")
        v2 = importlib.import_module("simulation.v2")
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)

        world = source_world(world_module)
        settlement = world.settlements[1]
        key = world.v2.sector_key_at(settlement.x + 4, settlement.y)
        sector = world.v2.sectors[key]
        for x, y in sector.cells:
            world.infection.level[y][x] = 0.02
        x, y = sector.cells[0]
        world.infection.latent_colonies.append(infection.LatentColony(x, y, spores=10.0, strength=0.5))
        control = world.v2.sector_control[key]
        control.last_cleared_day = world.day - 30
        control.state = v2.SectorControlState.HUMAN
        world.v2.refresh_territory(world)
        world.v2._recontaminate_unheld(world)
        world.v2.refresh_territory(world)
        recontamination = {
            "sector": key,
            "infection": world.v2.sectors[key].infection,
            "control": world.v2.sector_control[key].state.value,
        }
        campaign = v2.FrontCampaign(99, v2.FrontCampaignKind.CORDON, settlement.id, (settlement.id,), key,
                                    world.day, v2.FrontPhase.HOLD, {settlement.id: 12.0})
        world.v2.front_campaigns[campaign.id] = campaign
        world.v2.supply_lines[campaign.id] = world.v2._supply_line(world, campaign)
        world.v2.update_sector_control(world)
        supplied = world.v2.sector_control[key]

        attack_world = source_world(world_module)
        target = next(iter(attack_world.settlements.values()))
        attack_key = attack_world.v2.sector_key_at(target.x, target.y)
        attack_control = attack_world.v2.sector_control[attack_key]
        attack_control.state, attack_control.cordon_strength, attack_control.garrison = v2.SectorControlState.HUMAN, 0.7, 18.0
        swarm = infection.Swarm(999, target.x, target.y, 60.0, -1, target_x=target.x, target_y=target.y,
                                composition={infection.BioformKind.RAIDER: 6.0, infection.BioformKind.BREAKER: 3.0})
        resolved = attack_world.v2.resolve_frontier_attack(attack_world, swarm)
        engagement = attack_world.v2.sector_engagements[-1]
        counterattack = {
            "resolved": resolved,
            "sector": attack_key,
            "cordon": attack_control.cordon_strength,
            "garrison": attack_control.garrison,
            "infection": attack_world.v2.sectors[attack_key].infection,
            "engagement": engagement.summary(),
            "event": attack_world.events[-1],
        }
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "recontamination": recontamination,
            "supplied_cordon": {
                "connected": world.v2.supply_lines[campaign.id].connected,
                "risk": world.v2.supply_lines[campaign.id].risk,
                "readiness": world.v2.supply_lines[campaign.id].readiness,
                "garrison": supplied.garrison,
                "supplied": supplied.supplied,
                "cordon": supplied.cordon_strength,
                "control": supplied.state.value,
            },
            "counterattack": counterattack,
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
        raise SystemExit(f"V2-frontier traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "v2.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2-frontier micro-trace differs from the active Python reference")
        print("V2-frontier micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2-frontier micro-trace: {args.output}")


if __name__ == "__main__":
    main()
