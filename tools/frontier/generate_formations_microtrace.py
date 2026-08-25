#!/usr/bin/env python3
"""Pin Python formation composition arithmetic and discrete role allocation."""
from __future__ import annotations

import argparse
import hashlib
import importlib
import json
from pathlib import Path
import sys

REFERENCE_PYTHON = (3, 11)

def canonical(value: object) -> bytes:
    return json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(",", ":"), sort_keys=True).encode()

def sha(value: bytes) -> str: return hashlib.sha256(value).hexdigest()

def manifest(root: Path) -> tuple[dict[str, str], str]:
    files = [*root.joinpath("simulation").rglob("*.py"), root / "requirements.txt"]
    items = {item.relative_to(root).as_posix(): sha(item.read_bytes()) for item in sorted(files) if item.is_file()}
    return items, sha(canonical(items))

def trace(root: Path) -> dict[str, object]:
    sys.path.insert(0, str(root))
    try:
        module = importlib.import_module("simulation.formations")
        infection = importlib.import_module("simulation.infection")
        files, tree = manifest(root)
        kinds = module.HumanUnitKind
        weights = {kinds.LINE: .34, kinds.SCOUT: .12, kinds.ASSAULT: .28, kinds.ENGINEER: .16, kinds.MEDIC: .05, kinds.LOGISTICS: .05}
        tie = {kinds.LINE: 1., kinds.SCOUT: 1., kinds.ASSAULT: 1.}
        display = {kinds.SCOUT: 1.6, kinds.LINE: .4, kinds.ASSAULT: .01}
        half_even = {kinds.LINE: 2.5}
        values = {kinds.SCOUT: -1., kinds.LINE: 3.}
        bioforms = {infection.BioformKind.RAIDER: .25, infection.BioformKind.BREAKER: .75}
        errors = []
        for amount, candidate in ((1, {}), (1, {kinds.LINE: 0.})):
            try: module.integer_composition(amount, candidate)
            except ValueError as error: errors.append(str(error))
        return {"schema": 1, "source": {"tree_sha256": tree, "files": files},
                "seven": module.integer_composition(7, weights), "three": module.integer_composition(3, weights),
                "tie": module.integer_composition(2, tie), "compact": module.compact_composition(display),
                "half_even": module.compact_composition(half_even),
                "total": module.total_units(values), "line_ratio": module.composition_ratio(values, kinds.LINE),
                "bioform_compact": module.compact_composition(bioforms),
                "raider_ratio": module.composition_ratio(bioforms, infection.BioformKind.RAIDER), "errors": errors}
    finally:
        sys.path.remove(str(root))
        for name in tuple(sys.modules):
            if name == "simulation" or name.startswith("simulation."): del sys.modules[name]

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True); parser.add_argument("--output", type=Path, required=True); parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if sys.version_info[:2] != REFERENCE_PYTHON: raise SystemExit(f"Formation traces require Python 3.11; running {sys.version.split()[0]}")
    root = args.reference_root.resolve()
    if not root.joinpath("simulation", "formations.py").is_file(): raise SystemExit(f"reference simulation is unavailable at {root}")
    payload = canonical(trace(root))
    if args.check:
        if not args.output.is_file() or args.output.read_bytes() != payload: raise SystemExit("Formation micro-trace differs from the active Python reference")
        print("Formation micro-trace matches the active Python reference")
    else:
        args.output.write_bytes(payload); print(f"wrote formation micro-trace: {args.output}")

if __name__ == "__main__": main()
