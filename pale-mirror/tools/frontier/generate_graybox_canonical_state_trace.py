#!/usr/bin/env python3
"""Pin the lossless individual-graybox state contract of the Python reference."""

from __future__ import annotations

import argparse
import hashlib
import importlib
from pathlib import Path
import sys

from generate_reference_trace import canonical_bytes, source_manifest
from generate_v2_canonical_state_trace import CHECKPOINTS, StateEncoder, numeric_conformance


CODEC = "frontier_graybox_state_v1"


def _map(pairs: list[tuple[object, object]]) -> dict[str, object]:
    return {"$map": [list(pair) for pair in sorted(pairs, key=lambda pair: canonical_bytes(pair[0]))]}


def _sequence(items: list[str]) -> dict[str, object]:
    return {"$sequence": "list", "items": items}


def bioform_identities(world: object, encoder: StateEncoder) -> dict[str, object]:
    """Encode the private discrete-body ledger excluded from Swarm dataclass fields."""
    swarm_pairs: list[tuple[object, object]] = []
    for swarm in sorted(world.infection.swarms, key=lambda item: item.id):
        swarm.assert_discrete_bioform_invariants()
        by_kind: dict[object, list[str]] = {}
        for bioform_id, kind in swarm.exact_bioforms():
            by_kind.setdefault(kind, []).append(bioform_id)
        swarm_pairs.append((swarm.id, _map([
            (encoder.encode(kind), _sequence(ids))
            for kind, ids in by_kind.items()
        ])))
    return _map(swarm_pairs)


def state(world: object, encoder: StateEncoder) -> dict[str, object]:
    return {
        "codec": CODEC,
        "reference_state": encoder.state(world),
        "bioform_identities": bioform_identities(world, encoder),
    }


def exercise_identity_perturbation(world_module: object) -> dict[str, object]:
    """Pin a non-empty body-ID ledger before and after an exact player kill."""
    infection_module = importlib.import_module("simulation.infection")
    formations_module = importlib.import_module("simulation.formations")
    world = world_module.World(world_module.WorldConfig(
        width=64, height=44, settlement_count=12, seed=7, infection_seeds=2,
        v2=True, profile="graybox_1_40",
    ))
    swarm = infection_module.Swarm(
        id=901, x=12.0, y=13.0, power=90.0, target_id=-1, speed=0.9,
        kind=infection_module.BioformKind.RAIDER,
        composition={
            infection_module.BioformKind.RAIDER: 2.0,
            infection_module.BioformKind.BREAKER: 1.0,
        },
        phase=formations_module.FormationPhase.SCREEN,
    )
    world.infection.swarms.append(swarm)
    before = state(world, StateEncoder())
    if not world.infection.kill_exact_bioform("bioform:901:raider:1"):
        raise AssertionError("graybox identity exercise could not remove the declared body")
    after = state(world, StateEncoder())
    return {
        "before": {
            "sha256": hashlib.sha256(canonical_bytes(before)).hexdigest(),
            "numeric_conformance_sha256": hashlib.sha256(canonical_bytes(numeric_conformance(before))).hexdigest(),
            "bioform_identities_sha256": hashlib.sha256(canonical_bytes(before["bioform_identities"])).hexdigest(),
        },
        "after": {
            "sha256": hashlib.sha256(canonical_bytes(after)).hexdigest(),
            "numeric_conformance_sha256": hashlib.sha256(canonical_bytes(numeric_conformance(after))).hexdigest(),
            "bioform_identities_sha256": hashlib.sha256(canonical_bytes(after["bioform_identities"])).hexdigest(),
        },
    }


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        world = world_module.World(world_module.WorldConfig(
            width=64, height=44, settlement_count=12, seed=42, infection_seeds=2,
            v2=True, profile="graybox_1_40",
        ))
        checkpoints: list[dict[str, object]] = []
        for day in range(CHECKPOINTS[-1] + 1):
            if day in CHECKPOINTS:
                encoder = StateEncoder()
                snapshot = state(world, encoder)
                payload = canonical_bytes(snapshot)
                numeric_payload = canonical_bytes(numeric_conformance(snapshot))
                reference_payload = canonical_bytes(snapshot["reference_state"])
                identities_payload = canonical_bytes(snapshot["bioform_identities"])
                checkpoints.append({
                    "day": day,
                    "sha256": hashlib.sha256(payload).hexdigest(),
                    "bytes": len(payload),
                    "numeric_conformance_sha256": hashlib.sha256(numeric_payload).hexdigest(),
                    "components": {
                        "reference_state": {
                            "sha256": hashlib.sha256(reference_payload).hexdigest(),
                            "bytes": len(reference_payload),
                            "numeric_conformance_sha256": hashlib.sha256(
                                canonical_bytes(numeric_conformance(snapshot["reference_state"]))
                            ).hexdigest(),
                        },
                        "bioform_identities": {
                            "sha256": hashlib.sha256(identities_payload).hexdigest(),
                            "bytes": len(identities_payload),
                        },
                    },
                })
            if day < CHECKPOINTS[-1]:
                world.tick()
        return {
            "schema": 1,
            "codec": CODEC,
            "source": {"tree_sha256": tree, "files": files},
            "config": {
                "width": 64, "height": 44, "settlements": 12, "seed": 42,
                "infection_seeds": 2, "v2": True, "profile": "graybox_1_40",
            },
            "checkpoints": checkpoints,
            "identity_perturbation": exercise_identity_perturbation(world_module),
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
    payload = canonical_bytes(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload:
            raise SystemExit("graybox canonical-state trace differs from the active Python reference")
        print("graybox canonical-state trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote graybox canonical-state trace: {args.output}")


if __name__ == "__main__":
    main()
