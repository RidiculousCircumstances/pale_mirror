#!/usr/bin/env python3
"""Validate the machine-readable Pale Mirror architecture contract."""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Any

import yaml


V3_INVARIANTS = {
    "frontier-v3-greenfield-isolation",
    "frontier-v3-single-writer",
    "frontier-v3-event-time",
    "frontier-v3-hot-cold",
    "frontier-v3-scene-behavior-registry",
    "frontier-v3-extension-registries",
    "frontier-v3-owned-state-updates",
    "frontier-v3-stable-wire-tags",
    "frontier-v3-versioned-ruleset",
    "frontier-v3-test-fixture-isolation",
    "frontier-v3-physical-causality",
    "frontier-v3-exact-economy",
    "frontier-v3-persistence",
    "frontier-v3-bounded-runtime",
}

V3_FLOWS = {
    "frontier-v3-command-event",
    "frontier-v3-scheduled-world",
    "frontier-v3-hot-cold-scene",
    "frontier-v3-physical-feedback",
    "frontier-v3-restart-recovery",
    "frontier-v3-cutover",
}

V3_FORBIDDEN_DEPENDENCIES = {
    "pale-mirror-domain",
    "frontier.reference",
    "Minecraft",
    "NeoForge",
    "persistence implementations",
    "adapters",
    "wall-clock time",
    "ambient random APIs",
}


class ContractError(ValueError):
    """Raised when architecture.yml is syntactically valid but unsafe."""


def _mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ContractError(f"{label} must be a mapping")
    return value


def _list(value: Any, label: str) -> list[Any]:
    if not isinstance(value, list):
        raise ContractError(f"{label} must be a list")
    return value


def _unique_records(
    records: list[Any], key: str, label: str, *, allow_missing: bool = False
) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for index, raw_record in enumerate(records):
        record = _mapping(raw_record, f"{label}[{index}]")
        value = record.get(key)
        if value is None and allow_missing:
            continue
        if not isinstance(value, str) or not value.strip():
            raise ContractError(f"{label}[{index}].{key} must be a non-empty string")
        if value in indexed:
            raise ContractError(f"duplicate {label} {key}: {value}")
        indexed[value] = record
    return indexed


def validate(document: Any) -> None:
    root = _mapping(document, "architecture")
    if root.get("version") != 3:
        raise ContractError("architecture version must be 3")

    components = _unique_records(
        _list(root.get("components"), "components"), "name", "components"
    )
    frontier = components.get("frontier_v3")
    if frontier is None:
        raise ContractError("frontier_v3 component is missing")
    if frontier.get("lifecycle") not in {"planned", "active-development", "active"}:
        raise ContractError("frontier_v3 has an unknown lifecycle")
    if frontier.get("path") != (
        "pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3"
    ):
        raise ContractError("frontier_v3 has an unexpected source path")
    forbidden = set(_list(frontier.get("must_not_depend_on"), "frontier_v3.must_not_depend_on"))
    missing_forbidden = V3_FORBIDDEN_DEPENDENCIES - forbidden
    if missing_forbidden:
        raise ContractError(
            "frontier_v3 is missing forbidden dependencies: "
            + ", ".join(sorted(missing_forbidden))
        )

    transitions = _unique_records(
        _list(root.get("runtime_transitions"), "runtime_transitions"),
        "id",
        "runtime_transitions",
    )
    transition = transitions.get("frontier-v3-greenfield-cutover")
    if transition is None:
        raise ContractError("frontier-v3-greenfield-cutover transition is missing")
    expected_transition = {
        "current_status": "frozen-active",
        "target_runtime": "frontier_v3",
        "target_status": "active-development",
        "migration": "fresh-world-only",
    }
    for key, expected in expected_transition.items():
        if transition.get(key) != expected:
            raise ContractError(f"frontier transition {key} must be {expected}")

    invariant_records = _list(root.get("invariants"), "invariants")
    invariants = _unique_records(
        invariant_records, "id", "invariants", allow_missing=True
    )
    missing_invariants = V3_INVARIANTS - set(invariants)
    if missing_invariants:
        raise ContractError(
            "missing v3 invariants: " + ", ".join(sorted(missing_invariants))
        )
    for index, raw_record in enumerate(invariant_records):
        record = _mapping(raw_record, f"invariants[{index}]")
        if not isinstance(record.get("statement"), str) or not record["statement"].strip():
            raise ContractError(f"invariants[{index}].statement must be non-empty")

    flow_records = _list(root.get("critical_flows"), "critical_flows")
    flows = _unique_records(flow_records, "id", "critical_flows")
    missing_flows = V3_FLOWS - set(flows)
    if missing_flows:
        raise ContractError("missing v3 flows: " + ", ".join(sorted(missing_flows)))
    for flow_id in V3_FLOWS:
        flow = flows[flow_id]
        if flow.get("lifecycle") not in {"planned", "active-development", "active"}:
            raise ContractError(f"{flow_id} has an unknown lifecycle")
        for key in ("path", "owner", "verification"):
            if not isinstance(flow.get(key), str) or not flow[key].strip():
                raise ContractError(f"{flow_id}.{key} must be non-empty")

    legacy = flows.get("frontier-graybox-projection")
    if legacy is None or legacy.get("lifecycle") != (
        "frozen-active-until-frontier-v3-cutover"
    ):
        raise ContractError("legacy frontier flow must remain explicitly frozen-active")


def validate_path(path: Path) -> None:
    try:
        document = yaml.safe_load(path.read_text(encoding="utf-8"))
    except yaml.YAMLError as error:
        raise ContractError(f"invalid YAML: {error}") from error
    validate(document)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("architecture", type=Path)
    args = parser.parse_args()
    try:
        validate_path(args.architecture)
    except ContractError as error:
        parser.error(str(error))
    print(f"Architecture contract valid: {args.architecture}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
