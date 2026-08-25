#!/usr/bin/env python3
"""Pin the complete normalized source-V2 state codec without Python heap IDs."""

from __future__ import annotations

import argparse
import dataclasses
from enum import Enum
import hashlib
import importlib
import math
from pathlib import Path
from random import Random
import sys
from typing import Any, Mapping

from generate_reference_trace import canonical_bytes, source_manifest


CHECKPOINTS = (0, 1, 2, 3, 5, 10, 15, 20, 25, 30)
CODEC = "frontier_reference_state_v1"


class StateEncoder:
    """Source-shaped recursive encoder with no process-local identity tokens."""

    def __init__(self) -> None:
        self._active: set[int] = set()

    def world_root(self, world: Any) -> dict[str, object]:
        self._assert_stateless("commands", world.commands)
        self._assert_stateless("engine", world.engine)
        self._assert_stateless("hive", world.hive)
        if world.strategy is not None:
            raise ValueError("source_v2 must not retain a legacy strategy")
        if world.v2 is None:
            raise ValueError("source_v2 must retain V2 state")
        return {
            "config": self.encode(world.config),
            "profile": self.encode(world.profile),
            "day": world.day,
            "rng": self.encode(world.rng),
            "population_rng": self.encode(world.population_rng),
            "events": self.encode(world.events),
        }

    def state(self, world: Any) -> dict[str, object]:
        return {
            "codec": CODEC,
            **self.world_root(world),
            "diagnostics": self.diagnostics(world),
            "settlements": self.encode(world.settlements),
            "resource_sites": self.encode(world.resource_sites),
            "trade": self.encode(world.trade),
            "market": self.encode(world.microeconomy),
            "infection": self.encode(world.infection),
            "operations": self.encode(world.operations),
            "field": self.encode(world.field),
            "v2": self.encode(world.v2),
        }

    def diagnostics(self, world: Any) -> dict[str, object]:
        return {
            "history": self.encode(world.history),
            "settlement_history": self.encode(world.settlement_history),
            "combat_history": self.encode(world.combat_history),
            "containment_history": self.encode(world.containment_history),
        }

    def encode(self, value: Any) -> Any:
        if isinstance(value, Enum):
            return {"$enum": f"{type(value).__module__}.{type(value).__qualname__}", "value": self.encode(value.value)}
        if value is None or isinstance(value, (bool, int, str)):
            return value
        if isinstance(value, float):
            if not math.isfinite(value):
                raise ValueError("canonical state codec rejects non-finite float")
            return value
        if isinstance(value, Random):
            version, state, gaussian_cache = value.getstate()
            return {"$random_mt19937": {"version": version, "state": list(state), "gaussian_cache": self.encode(gaussian_cache)}}

        identity = id(value)
        if identity in self._active:
            raise ValueError(f"canonical state codec rejects cycle at {type(value).__module__}.{type(value).__qualname__}")
        self._active.add(identity)
        try:
            if dataclasses.is_dataclass(value) and not isinstance(value, type):
                return {
                    "$type": f"{type(value).__module__}.{type(value).__qualname__}",
                    "fields": {field.name: self.encode(getattr(value, field.name)) for field in dataclasses.fields(value)},
                }
            if isinstance(value, Mapping):
                pairs = [(self.encode(key), self.encode(item)) for key, item in value.items()]
                pairs.sort(key=lambda pair: canonical_bytes(pair[0]))
                return {"$map": pairs}
            if isinstance(value, (list, tuple)):
                return {"$sequence": "tuple" if isinstance(value, tuple) else "list", "items": [self.encode(item) for item in value]}
            if isinstance(value, (set, frozenset)):
                items = [self.encode(item) for item in value]
                items.sort(key=canonical_bytes)
                return {"$sequence": "frozenset" if isinstance(value, frozenset) else "set", "items": items}
            attributes = self._attributes(value)
            return {
                "$type": f"{type(value).__module__}.{type(value).__qualname__}",
                "attributes": {name: self.encode(item) for name, item in attributes.items()},
            }
        finally:
            self._active.remove(identity)

    def _assert_stateless(self, label: str, value: Any) -> None:
        if value is None or isinstance(value, (bool, int, float, str, Enum)):
            return
        if isinstance(value, Random):
            raise ValueError(f"excluded helper {label} owns an RNG")
        if isinstance(value, Mapping):
            if value:
                raise ValueError(f"excluded helper {label} owns map state")
            return
        if isinstance(value, (list, tuple, set, frozenset)):
            if value:
                raise ValueError(f"excluded helper {label} owns sequence state")
            return
        attributes = self._attributes(value)
        for name, item in attributes.items():
            self._assert_stateless(f"{label}.{name}", item)

    @staticmethod
    def _attributes(value: Any) -> dict[str, Any]:
        attributes: dict[str, Any] = {}
        if hasattr(value, "__dict__"):
            attributes.update(vars(value))
        for cls in type(value).__mro__:
            slots = getattr(cls, "__slots__", ())
            if isinstance(slots, str):
                slots = (slots,)
            for name in slots:
                if name not in {"__dict__", "__weakref__"} and hasattr(value, name):
                    attributes.setdefault(name, getattr(value, name))
        return {name: attributes[name] for name in sorted(attributes)}


def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        files, tree = source_manifest(root)
        world = world_module.World(world_module.WorldConfig(
            width=64, height=44, settlement_count=12, seed=42, infection_seeds=2, v2=True, profile="source_v2",
        ))
        checkpoints: list[dict[str, object]] = []
        for day in range(CHECKPOINTS[-1] + 1):
            if day in CHECKPOINTS:
                encoder = StateEncoder()
                root_payload = canonical_bytes(encoder.world_root(world))
                diagnostics_payload = canonical_bytes(encoder.diagnostics(world))
                infection_payload = canonical_bytes(encoder.encode(world.infection))
                market_payload = canonical_bytes(encoder.encode(world.microeconomy))
                resource_sites_payload = canonical_bytes(encoder.encode(world.resource_sites))
                settlements_payload = canonical_bytes(encoder.encode(world.settlements))
                payload = canonical_bytes(encoder.state(world))
                checkpoints.append({
                    "day": day,
                    "sha256": hashlib.sha256(payload).hexdigest(),
                    "bytes": len(payload),
                    "components": {
                        "world_root": {"sha256": hashlib.sha256(root_payload).hexdigest(), "bytes": len(root_payload)},
                        "diagnostics": {"sha256": hashlib.sha256(diagnostics_payload).hexdigest(), "bytes": len(diagnostics_payload)},
                        "infection": {"sha256": hashlib.sha256(infection_payload).hexdigest(), "bytes": len(infection_payload)},
                        "market": {"sha256": hashlib.sha256(market_payload).hexdigest(), "bytes": len(market_payload)},
                        "resource_sites": {"sha256": hashlib.sha256(resource_sites_payload).hexdigest(), "bytes": len(resource_sites_payload)},
                        "settlements": {"sha256": hashlib.sha256(settlements_payload).hexdigest(), "bytes": len(settlements_payload)},
                    },
                })
            if day < CHECKPOINTS[-1]:
                world.tick()
        return {
            "schema": 1,
            "codec": CODEC,
            "source": {"tree_sha256": tree, "files": files},
            "config": {"width": 64, "height": 44, "settlements": 12, "seed": 42, "infection_seeds": 2, "v2": True},
            "checkpoints": checkpoints,
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
            raise SystemExit("V2 canonical-state trace differs from the active Python reference")
        print("V2 canonical-state trace matches the active Python reference")
        return
    args.output.write_bytes(payload)
    print(f"wrote V2 canonical-state trace: {args.output}")


if __name__ == "__main__":
    main()
