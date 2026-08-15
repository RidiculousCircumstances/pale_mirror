#!/usr/bin/env python3
"""Print a deterministic, compact summary of a Pale Mirror Foundry report."""

from __future__ import annotations

import argparse
import collections
import json
import sys
from pathlib import Path
from typing import Any


SEVERITIES = ("BLOCKER", "ERROR", "WARNING", "INFO")


def validate(report: dict[str, Any]) -> None:
    required = ("formatVersion", "regionId", "catalogHash", "phase", "passed", "metrics", "findings")
    missing = [name for name in required if name not in report]
    if missing:
        raise ValueError("missing required fields: " + ", ".join(missing))
    if not isinstance(report["metrics"], list) or not isinstance(report["findings"], list):
        raise ValueError("metrics and findings must be arrays")


def lines(report: dict[str, Any], top: int) -> list[str]:
    validate(report)
    findings = report["findings"]
    severity_counts = collections.Counter(str(item.get("severity", "UNKNOWN")) for item in findings)
    rule_counts = collections.Counter(str(item.get("ruleId", "UNKNOWN")) for item in findings)
    output = [
        f"region={report['regionId']} phase={report['phase']} passed={str(report['passed']).lower()}",
        f"format={report['formatVersion']} catalog={report['catalogHash']}",
        "findings=" + " ".join(f"{name}:{severity_counts[name]}" for name in SEVERITIES),
    ]
    if report["metrics"]:
        metrics = sorted(report["metrics"], key=lambda item: str(item.get("id", "")))
        output.append("metrics=" + " ".join(
            f"{item.get('id', 'UNKNOWN')}:{item.get('value', '?')}{item.get('unit', '')}"
            for item in metrics
        ))
    if rule_counts:
        output.append("rules=" + " ".join(f"{rule}:{count}" for rule, count in sorted(rule_counts.items())))
    severity_rank = {name: index for index, name in enumerate(SEVERITIES)}
    ordered = sorted(findings, key=lambda item: (
        severity_rank.get(str(item.get("severity", "")), len(SEVERITIES)),
        str(item.get("ruleId", "")),
        int(item.get("x", 0)), int(item.get("y", 0)), int(item.get("z", 0)),
    ))
    for item in ordered[:top]:
        output.append(
            "finding="
            f"{item.get('severity', 'UNKNOWN')} {item.get('ruleId', 'UNKNOWN')} "
            f"target={item.get('targetKind', '?')}:{item.get('targetId', '?')} "
            f"at={item.get('x', '?')},{item.get('y', '?')},{item.get('z', '?')} "
            f"message={item.get('message', '')}"
        )
    return output


def self_test() -> int:
    report = {
        "formatVersion": 1,
        "regionId": "test-region",
        "catalogHash": "abc123",
        "phase": "SETTLED",
        "passed": False,
        "metrics": [{"id": "world.checked", "value": 12, "unit": "cells"}],
        "findings": [{
            "ruleId": "world.cell.mismatch", "severity": "ERROR", "targetKind": "module",
            "targetId": "depot", "x": 1, "y": 64, "z": 2, "message": "mismatch",
        }],
    }
    rendered = "\n".join(lines(report, 4))
    expected = ("region=test-region phase=SETTLED passed=false", "ERROR:1", "world.cell.mismatch:1")
    if not all(value in rendered for value in expected):
        raise AssertionError(rendered)
    print("self-test passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", nargs="?", type=Path, help="Path to report.json")
    parser.add_argument("--top", type=int, default=12, help="Maximum detailed findings to print")
    parser.add_argument("--allow-fail", action="store_true", help="Return zero even for ERROR/BLOCKER findings")
    parser.add_argument("--self-test", action="store_true", help="Run an in-memory smoke test")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if args.report is None:
        parser.error("report is required unless --self-test is used")
    try:
        report = json.loads(args.report.read_text(encoding="utf-8"))
        for value in lines(report, max(0, args.top)):
            print(value)
    except (OSError, json.JSONDecodeError, ValueError) as error:
        print(f"invalid Foundry report: {error}", file=sys.stderr)
        return 2
    fails_gate = any(item.get("severity") in {"ERROR", "BLOCKER"} for item in report["findings"])
    return 0 if args.allow_fail or not fails_gate else 1


if __name__ == "__main__":
    raise SystemExit(main())
