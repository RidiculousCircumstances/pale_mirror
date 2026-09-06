#!/usr/bin/env python3
"""Compare two Pale Mirror Foundry reports and fail on severe regressions."""

from __future__ import annotations

import argparse
import collections
import json
import sys
from pathlib import Path
from typing import Any


SEVERE = {"BLOCKER", "ERROR"}


def load(path: Path) -> dict[str, Any]:
    report = json.loads(path.read_text(encoding="utf-8"))
    for field in ("regionId", "catalogHash", "phase", "findings"):
        if field not in report:
            raise ValueError(f"{path}: missing {field}")
    if not isinstance(report["findings"], list):
        raise ValueError(f"{path}: findings must be an array")
    return report


def identity(item: dict[str, Any]) -> tuple[Any, ...]:
    return (
        item.get("ruleId"), item.get("severity"), item.get("targetKind"), item.get("targetId"),
        item.get("x"), item.get("y"), item.get("z"),
    )


def compare(before: dict[str, Any], after: dict[str, Any]) -> tuple[list[str], bool]:
    before_set = {identity(item) for item in before["findings"]}
    after_set = {identity(item) for item in after["findings"]}
    introduced = sorted(after_set - before_set, key=repr)
    resolved = sorted(before_set - after_set, key=repr)
    before_severity = collections.Counter(str(item.get("severity", "UNKNOWN")) for item in before["findings"])
    after_severity = collections.Counter(str(item.get("severity", "UNKNOWN")) for item in after["findings"])
    severe_introduced = [item for item in introduced if item[1] in SEVERE]
    output = [
        f"region={before['regionId']} before={before['phase']} after={after['phase']}",
        f"catalog_before={before['catalogHash']} catalog_after={after['catalogHash']}",
        "severe="
        f"before:{sum(before_severity[name] for name in SEVERE)} "
        f"after:{sum(after_severity[name] for name in SEVERE)}",
        f"introduced={len(introduced)} resolved={len(resolved)} severe_introduced={len(severe_introduced)}",
    ]
    for item in severe_introduced[:12]:
        output.append(f"regression={item[1]} {item[0]} target={item[2]}:{item[3]} at={item[4]},{item[5]},{item[6]}")
    return output, bool(severe_introduced)


def self_test() -> int:
    common = {"regionId": "r", "catalogHash": "h", "phase": "SETTLED"}
    before = {**common, "findings": []}
    after = {**common, "phase": "RELOADED", "findings": [{
        "ruleId": "rail.world.graph", "severity": "ERROR", "targetKind": "railway",
        "targetId": "legacy", "x": 1, "y": 2, "z": 3,
    }]}
    rendered, regression = compare(before, after)
    if not regression or not any("rail.world.graph" in line for line in rendered):
        raise AssertionError(rendered)
    print("self-test passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", nargs="?", type=Path)
    parser.add_argument("after", nargs="?", type=Path)
    parser.add_argument("--allow-catalog-change", action="store_true")
    parser.add_argument("--allow-regression", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if args.before is None or args.after is None:
        parser.error("before and after reports are required unless --self-test is used")
    try:
        before = load(args.before)
        after = load(args.after)
    except (OSError, json.JSONDecodeError, ValueError) as error:
        print(f"invalid Foundry report: {error}", file=sys.stderr)
        return 2
    if before["regionId"] != after["regionId"]:
        print("reports refer to different regions", file=sys.stderr)
        return 2
    if before["catalogHash"] != after["catalogHash"] and not args.allow_catalog_change:
        print("catalog hashes differ; pass --allow-catalog-change for intentional recompilation", file=sys.stderr)
        return 2
    output, regression = compare(before, after)
    for value in output:
        print(value)
    return 0 if args.allow_regression or not regression else 1


if __name__ == "__main__":
    raise SystemExit(main())
