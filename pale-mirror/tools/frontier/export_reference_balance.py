#!/usr/bin/env python3
"""Export the complete active Python BALANCE as a pinned Java-port fixture."""

from __future__ import annotations

import argparse
import hashlib
import importlib
import json
from pathlib import Path
import sys
from typing import Any

from generate_reference_trace import require_reference_python


SCHEMA = 1


def canonical_bytes(value: Any) -> bytes:
    return json.dumps(value, allow_nan=False, ensure_ascii=True, separators=(",", ":"), sort_keys=True).encode("utf-8")


def fixture(reference_root: Path) -> bytes:
    require_reference_python()
    balance_path = reference_root / "simulation" / "balance.py"
    if not balance_path.is_file():
        raise ValueError(f"missing active balance source: {balance_path}")
    sys.path.insert(0, str(reference_root))
    try:
        balance = importlib.import_module("simulation.balance").BALANCE
        return canonical_bytes({
            "schema": SCHEMA,
            "source": {
                "project": "pale_mirror_ai",
                "path": "simulation/balance.py",
                "sha256": hashlib.sha256(balance_path.read_bytes()).hexdigest(),
            },
            "balance": balance,
        })
    finally:
        sys.path.remove(str(reference_root))
        for name in tuple(sys.modules):
            if name == "simulation" or name.startswith("simulation."):
                del sys.modules[name]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference-root", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--check", action="store_true", help="fail instead of refreshing a stale fixture")
    args = parser.parse_args()

    expected = fixture(args.reference_root.resolve())
    output = args.output.resolve()
    if args.check:
        if not output.is_file() or output.read_bytes() != expected:
            raise SystemExit("complete Frontier balance fixture is stale; refresh it deliberately")
        print("Complete Frontier reference balance matches pale_mirror_ai")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(expected)
    print(f"wrote {len(expected)} bytes to {output}")


if __name__ == "__main__":
    main()
