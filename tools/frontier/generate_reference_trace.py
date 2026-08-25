#!/usr/bin/env python3
"""Create a source-pinned, deterministic Python Frontier trace.

The Python project is deliberately kept outside this Git repository.  This
tool therefore records every active ``simulation/*.py`` source digest together
with canonical, complete day snapshots.  A Java port consumes the trace as an
oracle; it must never silently treat an unpinned local checkout as truth.
"""

from __future__ import annotations

import argparse
import dataclasses
from enum import Enum
import hashlib
import importlib
import json
import math
from pathlib import Path
from random import Random
import sys
from typing import Any, Mapping


TRACE_SCHEMA = 1
REFERENCE_PYTHON = (3, 11)


def require_reference_python() -> None:
    """Reject interpreter drift before a binary64 source contract is emitted."""
    if sys.version_info[:2] != REFERENCE_PYTHON:
        required = ".".join(str(value) for value in REFERENCE_PYTHON)
        actual = ".".join(str(value) for value in sys.version_info[:3])
        raise SystemExit(f"Frontier source traces require Python {required}; running {actual}")


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def canonical_bytes(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(",", ":"), sort_keys=True).encode("utf-8")


def source_manifest(root: Path) -> tuple[dict[str, str], str]:
    require_reference_python()
    simulation = root / "simulation"
    if not (simulation / "world.py").is_file():
        raise ValueError(f"{root} does not contain simulation/world.py")
    files = [*simulation.rglob("*.py"), root / "requirements.txt"]
    digests = {
        path.relative_to(root).as_posix(): sha256_bytes(path.read_bytes())
        for path in sorted(files)
        if path.is_file()
    }
    return digests, sha256_bytes(canonical_bytes(digests))


class CanonicalEncoder:
    """Encode all mutable Python state without relying on object repr/order."""

    def __init__(self) -> None:
        self._seen: dict[int, int] = {}
        self._next_ref = 1

    def encode(self, value: Any) -> Any:
        if value is None or isinstance(value, (bool, int, str)):
            return value
        if isinstance(value, float):
            if math.isnan(value):
                return {"$float": "nan"}
            if math.isinf(value):
                return {"$float": "infinity" if value > 0 else "-infinity"}
            return value
        if isinstance(value, Enum):
            return {"$enum": f"{type(value).__module__}.{type(value).__qualname__}", "value": self.encode(value.value)}
        if isinstance(value, Random):
            # getstate() constructs short-lived tuples. They must not enter
            # the object-identity reference table: CPython may reuse a dead
            # temporary's id during this same encoding pass.
            version, state, gaussian_cache = value.getstate()
            return {
                "$random_mt19937": {
                    "version": version,
                    "state": list(state),
                    "gaussian_cache": self.encode(gaussian_cache),
                }
            }

        value_id = id(value)
        if value_id in self._seen:
            return {"$ref": self._seen[value_id]}
        reference = self._next_ref
        self._next_ref += 1
        self._seen[value_id] = reference

        if dataclasses.is_dataclass(value) and not isinstance(value, type):
            return {
                "$id": reference,
                "$type": f"{type(value).__module__}.{type(value).__qualname__}",
                "fields": {field.name: self.encode(getattr(value, field.name)) for field in dataclasses.fields(value)},
            }
        if isinstance(value, Mapping):
            encoded = [(self.encode(key), self.encode(item)) for key, item in value.items()]
            encoded.sort(key=lambda pair: canonical_bytes(pair[0]))
            return {"$id": reference, "$map": encoded}
        if isinstance(value, (list, tuple)):
            return {
                "$id": reference,
                "$sequence": "tuple" if isinstance(value, tuple) else "list",
                "items": [self.encode(item) for item in value],
            }
        if isinstance(value, (set, frozenset)):
            encoded = [self.encode(item) for item in value]
            encoded.sort(key=canonical_bytes)
            return {"$id": reference, "$sequence": "frozenset" if isinstance(value, frozenset) else "set", "items": encoded}

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
        if attributes:
            return {
                "$id": reference,
                "$type": f"{type(value).__module__}.{type(value).__qualname__}",
                "attributes": {name: self.encode(item) for name, item in sorted(attributes.items())},
            }
        # Stateless application helpers (for example CommandExecutor) affect
        # no canonical snapshot. Their type still remains visible so adding
        # mutable fields later changes the trace rather than being ignored.
        return {"$id": reference, "$type": f"{type(value).__module__}.{type(value).__qualname__}"}


def world_trace(root: Path, *, seed: int, days: int, width: int, height: int, settlements: int, infection_seeds: int, v2: bool) -> list[bytes]:
    sys.path.insert(0, str(root))
    try:
        world_module = importlib.import_module("simulation.world")
        config = world_module.WorldConfig(
            width=width,
            height=height,
            settlement_count=settlements,
            infection_seeds=infection_seeds,
            seed=seed,
            v2=v2,
        )
        world = world_module.World(config)
        snapshots = [canonical_bytes(CanonicalEncoder().encode(world))]
        for _ in range(days):
            world.tick()
            snapshots.append(canonical_bytes(CanonicalEncoder().encode(world)))
        return snapshots
    finally:
        sys.path.remove(str(root))
        for name in tuple(sys.modules):
            if name == "simulation" or name.startswith("simulation."):
                del sys.modules[name]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=7)
    parser.add_argument("--days", type=int, default=30)
    parser.add_argument("--width", type=int, default=64)
    parser.add_argument("--height", type=int, default=44)
    parser.add_argument("--settlements", type=int, default=12)
    parser.add_argument("--infection-seeds", type=int, default=2)
    parser.add_argument("--legacy", action="store_true", help="trace retained legacy strategy instead of active V2")
    parser.add_argument("--assert-deterministic", action="store_true")
    args = parser.parse_args()

    if args.days < 0:
        raise SystemExit("--days must be non-negative")
    root = args.reference_root.resolve()
    output = args.output_dir.resolve()
    if output.exists() and any(output.iterdir()):
        raise SystemExit(f"refusing to replace non-empty trace directory: {output}")
    output.mkdir(parents=True, exist_ok=True)

    files, tree_digest = source_manifest(root)
    trace_args = {
        "seed": args.seed,
        "days": args.days,
        "width": args.width,
        "height": args.height,
        "settlements": args.settlements,
        "infection_seeds": args.infection_seeds,
        "v2": not args.legacy,
    }
    snapshots = world_trace(root, **trace_args)
    if args.assert_deterministic and snapshots != world_trace(root, **trace_args):
        raise SystemExit("same-seed reference trace is not deterministic")

    entries: list[dict[str, object]] = []
    for day, payload in enumerate(snapshots):
        name = f"day-{day:04d}.json"
        (output / name).write_bytes(payload)
        entries.append({"day": day, "file": name, "sha256": sha256_bytes(payload), "bytes": len(payload)})
    manifest = {
        "schema": TRACE_SCHEMA,
        "source": {
            "project": "pale_mirror_ai",
            "root_name": root.name,
            "tree_sha256": tree_digest,
            "files": files,
        },
        "runtime": {"implementation": sys.implementation.name, "python": sys.version.split()[0], "random": "python.random.MT19937"},
        "profile": {"name": "source_v2" if trace_args["v2"] else "source_legacy", **trace_args},
        "snapshots": entries,
    }
    (output / "manifest.json").write_bytes(canonical_bytes(manifest))
    print(f"wrote {len(entries)} source-pinned snapshots to {output}")
    print(f"source tree sha256: {tree_digest}")
    print(f"final snapshot sha256: {entries[-1]['sha256']}")


if __name__ == "__main__":
    main()
