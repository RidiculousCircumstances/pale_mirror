#!/usr/bin/env python3
"""Build a bounded, advisory change-to-verification plan.

The selector never executes commands.  It separates cheap change feedback from
milestone-only gates so test infrastructure cannot silently turn every edit into
a full proof campaign.
"""
from __future__ import annotations

import argparse
import fnmatch
import json
import re
from pathlib import Path
from typing import Any, Iterable

CATALOG = Path(__file__).with_name("verification_catalog.json")
MAX_PATHS = 256
MAX_PATH_CHARS = 512


class SelectionError(RuntimeError):
    pass


def load_catalog(path: Path = CATALOG) -> dict[str, Any]:
    try:
        catalog = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SelectionError(f"invalid verification catalog: {exc}") from exc
    if catalog.get("schema_version") != 1:
        raise SelectionError("unsupported verification catalog schema")
    families = catalog.get("families")
    rules = catalog.get("path_rules")
    if not isinstance(families, dict) or not isinstance(rules, list):
        raise SelectionError("verification catalog is incomplete")
    for name, definition in families.items():
        if not isinstance(name, str) or not isinstance(definition, dict):
            raise SelectionError("invalid verification family")
        if not all(isinstance(definition.get(key), str) for key in ("tier", "cwd", "command", "purpose")):
            raise SelectionError(f"verification family {name} is incomplete")
    for rule in rules:
        if not isinstance(rule, dict) or not isinstance(rule.get("pattern"), str):
            raise SelectionError("invalid path rule")
        if not all(name in families for name in rule.get("families", [])):
            raise SelectionError(f"path rule {rule.get('pattern')} references an unknown family")
    return catalog


def normalize_paths(paths: Iterable[str]) -> list[str]:
    normalized: list[str] = []
    for raw in paths:
        if not isinstance(raw, str) or not raw or len(raw) > MAX_PATH_CHARS or "\x00" in raw:
            raise SelectionError("changed path is invalid")
        value = raw.replace("\\", "/")
        while value.startswith("./"):
            value = value[2:]
        if value.startswith("/") or value == ".." or value.startswith("../") or "/../" in value:
            raise SelectionError("changed paths must be repository-relative")
        normalized.append(value)
    unique = list(dict.fromkeys(normalized))
    if not unique or len(unique) > MAX_PATHS:
        raise SelectionError(f"expected 1..{MAX_PATHS} changed paths")
    return unique


def _matches(path: str, pattern: str) -> bool:
    # pathlib-style ** semantics are not portable through fnmatch; match both
    # the authored pattern and a collapsed form for zero-directory occurrences.
    return fnmatch.fnmatchcase(path, pattern) or fnmatch.fnmatchcase(path, pattern.replace("/**/", "/"))


def _focused_java_test(path: str) -> dict[str, str] | None:
    match = re.fullmatch(r"(pale-mirror-[^/]+)/src/test/java/(.+)\.java", path)
    if not match:
        return None
    module, qualified = match.groups()
    class_name = qualified.replace("/", ".")
    return {
        "id": f"focused:{class_name}",
        "tier": "T0",
        "cwd": ".",
        "command": f"./gradlew :{module}:test --tests '{class_name}'",
        "purpose": f"Run the directly changed Java test {class_name}.",
        "reason": path,
        "generated": True,
    }


def select(paths: Iterable[str], *, milestone: bool = False, catalog_path: Path = CATALOG) -> dict[str, Any]:
    changed = normalize_paths(paths)
    catalog = load_catalog(catalog_path)
    selected: dict[str, set[str]] = {}
    forced: set[str] = set()
    generated: list[dict[str, Any]] = []
    unmapped: list[str] = []

    for path in changed:
        matched = False
        direct = _focused_java_test(path)
        if direct:
            generated.append(direct)
            matched = True
        for rule in catalog["path_rules"]:
            if _matches(path, rule["pattern"]):
                matched = True
                for family in rule.get("families", []):
                    selected.setdefault(family, set()).add(path)
        if not matched:
            unmapped.append(path)

    if unmapped:
        selected.setdefault("critical-gate", set()).update(f"unmapped:{path}" for path in unmapped)
        forced.add("critical-gate")

    lowered = " ".join(changed).lower()
    for rule in catalog.get("escalation_rules", []):
        required = rule.get("requires_matching_path_family")
        if required in selected and any(keyword in lowered for keyword in rule.get("keywords", [])):
            selected.setdefault(rule["family"], set()).add("semantic keyword match")

    if milestone:
        for family in catalog.get("milestone_families", []):
            selected.setdefault(family, set()).add("explicit milestone request")

    steps = generated[:]
    deferred: list[dict[str, Any]] = []
    for name, reasons in selected.items():
        definition = dict(catalog["families"][name])
        definition.update({"id": name, "reason": sorted(reasons), "generated": False})
        if definition.get("manual_selection") or (definition.get("milestone_only") and not milestone and name not in forced):
            deferred.append(definition)
        else:
            steps.append(definition)

    tier_order = {f"T{number}": number for number in range(10)}
    steps.sort(key=lambda item: (tier_order.get(item["tier"], 99), item["id"]))
    deferred.sort(key=lambda item: (tier_order.get(item["tier"], 99), item["id"]))
    return {
        "schema_version": 1,
        "mode": "milestone" if milestone else "change-feedback",
        "changed_paths": changed,
        "steps": steps,
        "deferred": deferred,
        "unmapped_paths": unmapped,
        "manual_selection_required": bool(unmapped) or any(item.get("manual_selection") for item in deferred),
        "execution": "advisory_only",
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("paths", nargs="+", help="repository-relative changed paths")
    parser.add_argument("--milestone", action="store_true", help="include the one complete local milestone gate")
    parser.add_argument("--catalog", type=Path, default=CATALOG)
    return parser


def main() -> int:
    arguments = build_parser().parse_args()
    try:
        print(json.dumps(select(arguments.paths, milestone=arguments.milestone, catalog_path=arguments.catalog), indent=2, sort_keys=True))
    except SelectionError as exc:
        print(json.dumps({"error": str(exc), "state": "invalid"}, sort_keys=True))
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
