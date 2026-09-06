#!/usr/bin/env python3
"""Pin Python V2 civic site-work decisions, claim completion and invalidation."""

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


def world(world_module):
    result = world_module.World(world_module.WorldConfig(seed=41, infection_seeds=0, v2=True))
    for settlement in result.settlements.values():
        if settlement.id != 1:
            settlement.alive = False
    return result


def project(item) -> dict[str, object]:
    return {"settlement": item.settlement_id, "site": item.site_id, "action": item.action, "remaining": item.days_remaining}


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        economy_module = importlib.import_module("simulation.economy")
        v2_module = importlib.import_module("simulation.v2")
        files, tree = source_manifest(root)

        cleansed = world(world_module)
        settlement = cleansed.settlements[1]
        for resource in economy_module.Resource:
            settlement.stock[resource] += 10_000.0
        site = cleansed.resource_sites[1]
        site.contamination, site.condition = 1.0, 1.0
        decision = cleansed.v2._civic_site_decision(cleansed, 1)
        cleansed.v2._maintain_civic_sites(cleansed)

        claimed = world(world_module)
        claim_site = claimed.resource_sites[4]
        claim_site.owner_id = None
        claimed.v2.civic_site_projects.append(v2_module.CivicSiteProject(1, claim_site.id, "claim", 1))
        claimed.day = 9
        claimed.v2._advance_civic_site_projects(claimed)

        invalid = world(world_module)
        invalid_site = invalid.resource_sites[4]
        invalid_site.owner_id = None
        invalid.settlements[1].alive = False
        invalid.v2.civic_site_projects.append(v2_module.CivicSiteProject(1, invalid_site.id, "claim", 1))
        invalid.v2._advance_civic_site_projects(invalid)
        return {
            "schema": 1,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 41, "infection_seeds": 0, "v2": True},
            "cleanse": {
                "decision": {"action": decision[0], "site": decision[1].id},
                "contamination": site.contamination,
                "condition": site.condition,
                "last_work_day": cleansed.v2.last_civic_work_day,
                "event": cleansed.events[-1],
            },
            "claim": {"owner": claim_site.owner_id, "claimed_day": claim_site.claimed_day,
                      "projects": [project(item) for item in claimed.v2.civic_site_projects], "event": claimed.events[-1]},
            "invalid_claim": {"owner": invalid_site.owner_id,
                              "projects": [project(item) for item in invalid.v2.civic_site_projects],
                              "events": invalid.events},
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
        raise SystemExit(f"V2 civic-work traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "v2.py").is_file():
        raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("V2 civic-work micro-trace differs from the active Python reference")
        print("V2 civic-work micro-trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2 civic-work micro-trace: {args.output}")


if __name__ == "__main__":
    main()
