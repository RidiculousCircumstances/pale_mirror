#!/usr/bin/env python3
"""Fail when known Frontier v3 architecture debt spreads or grows."""

from __future__ import annotations

import argparse
import re
from pathlib import Path
from typing import Any

import yaml


class DebtError(ValueError):
    """Raised when the checked-in implementation exceeds an accepted ceiling."""


STATE_CONSTRUCTOR = re.compile(r"new\s+FrontierWorldState\s*\(")
ENUM_POSITION_TAG = re.compile(r"\.ordinal\s*\(\)|\.values\s*\(\)\s*\[")
SCENE_CAUSE_BRANCH = re.compile(
    r"instanceof\s+(?:[\w.]+\.)?(?:LogisticsSceneCause|SettlementAssaultSceneCause)\b"
)
SCHEDULED_STRING_CASE = re.compile(r'case\s+"frontier\.[^"]+"\s*->')
COMMAND_PAYLOAD_TYPE_TEST = re.compile(r"command\.payload\(\)\s+instanceof")
RUNTIME_REDUCER_CASE = re.compile(r"^\s*case\s+\w+", re.MULTILINE)
MANUAL_EXECUTOR_TICK = re.compile(r"FrontierV3\w+Executor\.tick\s*\(")
DEVELOPMENT_CONFIGURATION = re.compile(
    r"public\s+static\s+FrontierEngineConfiguration<FrontierWorldState,\s*"
    r"FrontierWorldProjection>\s+development\w+Configuration\s*\("
)
FIXTURE_PROFILE_BRANCH = re.compile(r'profile\.equals\("(?!world")[^"]+"\)')

FRONTIER_MAIN = Path(
    "pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3"
)
NEOFORGE_MAIN = Path(
    "pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3"
)
RUNTIME_DEFINITION = FRONTIER_MAIN / "model/FrontierWorldRuntimeDefinition.java"
SERVER_LIFECYCLE = NEOFORGE_MAIN / "FrontierV3ServerLifecycle.java"


def _mapping(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise DebtError(f"{label} must be a mapping")
    return value


def _positive_int(value: Any, label: str) -> int:
    if not isinstance(value, int) or value < 0:
        raise DebtError(f"{label} must be a non-negative integer")
    return value


def _text(root: Path, relative: Path) -> str:
    path = root / relative
    if not path.is_file():
        raise DebtError(f"required architecture-debt source is missing: {relative}")
    return path.read_text(encoding="utf-8")


def _java_files(root: Path, relative: Path) -> list[Path]:
    path = root / relative
    if not path.is_dir():
        raise DebtError(f"required source root is missing: {relative}")
    return sorted(path.rglob("*.java"))


def _counts(
    root: Path,
    source_roots: tuple[Path, ...],
    pattern: re.Pattern[str],
    *,
    codec_only: bool = False,
) -> dict[str, int]:
    result: dict[str, int] = {}
    for source_root in source_roots:
        for path in _java_files(root, source_root):
            if codec_only and not path.name.endswith(("Codec.java", "Codecs.java")):
                continue
            count = len(pattern.findall(path.read_text(encoding="utf-8")))
            if count:
                result[path.relative_to(root).as_posix()] = count
    return result


def collect(root: Path) -> dict[str, Any]:
    constructors = _counts(root, (FRONTIER_MAIN,), STATE_CONSTRUCTOR)
    enum_tags = _counts(
        root, (FRONTIER_MAIN,), ENUM_POSITION_TAG, codec_only=True
    )
    scene_branches = _counts(
        root, (FRONTIER_MAIN, NEOFORGE_MAIN), SCENE_CAUSE_BRANCH
    )
    scene_branches = {
        path: count
        for path, count in scene_branches.items()
        if not Path(path).name.endswith("GameTests.java")
    }
    runtime = _text(root, RUNTIME_DEFINITION)
    lifecycle = _text(root, SERVER_LIFECYCLE)
    model_root = root / FRONTIER_MAIN / "model"
    return {
        "hotspot_lines": {},
        "manual_executor_ticks": len(MANUAL_EXECUTOR_TICK.findall(lifecycle)),
        "runtime_scheduled_string_cases": len(
            SCHEDULED_STRING_CASE.findall(runtime)
        ),
        "runtime_command_payload_type_tests": len(
            COMMAND_PAYLOAD_TYPE_TEST.findall(runtime)
        ),
        "runtime_reducer_cases": len(RUNTIME_REDUCER_CASE.findall(runtime)),
        "production_development_configurations": len(
            DEVELOPMENT_CONFIGURATION.findall(runtime)
        ),
        "lifecycle_fixture_profile_branches": len(
            FIXTURE_PROFILE_BRANCH.findall(lifecycle)
        ),
        "process_classes_in_model": len(list(model_root.glob("*Process.java"))),
        "codec_classes_in_model": len(list(model_root.glob("*Codec.java")))
        + len(list(model_root.glob("*Codecs.java"))),
        "direct_world_state_construction": constructors,
        "persisted_enum_position_tags": enum_tags,
        "scene_cause_type_branches": scene_branches,
    }


def _validate_counted_files(
    actual: dict[str, int], raw_policy: Any, label: str
) -> None:
    policy = _mapping(raw_policy, label)
    max_total = _positive_int(policy.get("max_total"), f"{label}.max_total")
    allowed_raw = _mapping(policy.get("allowed_files"), f"{label}.allowed_files")
    allowed = {
        str(path): _positive_int(limit, f"{label}.allowed_files[{path}]")
        for path, limit in allowed_raw.items()
    }
    unexpected = sorted(set(actual) - set(allowed))
    if unexpected:
        raise DebtError(f"{label} spread to unapproved files: {', '.join(unexpected)}")
    exceeded = [
        f"{path}={count}>{allowed[path]}"
        for path, count in sorted(actual.items())
        if count > allowed[path]
    ]
    if exceeded:
        raise DebtError(f"{label} per-file ceiling exceeded: {', '.join(exceeded)}")
    total = sum(actual.values())
    if total > max_total:
        raise DebtError(f"{label} total ceiling exceeded: {total}>{max_total}")


def validate(root: Path, policy_document: Any, actual: dict[str, Any] | None = None) -> None:
    policy = _mapping(policy_document, "frontier v3 architecture debt")
    if policy.get("version") != 1:
        raise DebtError("frontier v3 architecture debt version must be 1")
    audit = policy.get("audit")
    if not isinstance(audit, str) or not (root / audit).is_file():
        raise DebtError("frontier v3 architecture debt must reference its audit document")
    metrics = collect(root) if actual is None else actual

    hotspots = _mapping(policy.get("hotspot_line_ceiling"), "hotspot_line_ceiling")
    for relative, raw_limit in hotspots.items():
        limit = _positive_int(raw_limit, f"hotspot_line_ceiling[{relative}]")
        lines = len(_text(root, Path(relative)).splitlines())
        if lines > limit:
            raise DebtError(f"architecture hotspot grew: {relative}={lines}>{limit}")

    manual_limit = _positive_int(
        policy.get("max_manual_executor_ticks"), "max_manual_executor_ticks"
    )
    if metrics["manual_executor_ticks"] > manual_limit:
        raise DebtError(
            "manual physical executor pipeline grew: "
            f"{metrics['manual_executor_ticks']}>{manual_limit}"
        )
    scheduled_limit = _positive_int(
        policy.get("max_runtime_scheduled_string_cases"),
        "max_runtime_scheduled_string_cases",
    )
    if metrics["runtime_scheduled_string_cases"] > scheduled_limit:
        raise DebtError(
            "runtime scheduled dispatcher grew: "
            f"{metrics['runtime_scheduled_string_cases']}>{scheduled_limit}"
        )
    command_type_limit = _positive_int(
        policy.get("max_runtime_command_payload_type_tests"),
        "max_runtime_command_payload_type_tests",
    )
    if metrics["runtime_command_payload_type_tests"] > command_type_limit:
        raise DebtError(
            "runtime command dispatcher grew: "
            f"{metrics['runtime_command_payload_type_tests']}>{command_type_limit}"
        )
    reducer_case_limit = _positive_int(
        policy.get("max_runtime_reducer_cases"),
        "max_runtime_reducer_cases",
    )
    if metrics["runtime_reducer_cases"] > reducer_case_limit:
        raise DebtError(
            "runtime reducer dispatcher grew: "
            f"{metrics['runtime_reducer_cases']}>{reducer_case_limit}"
        )

    fixture_configuration_limit = _positive_int(
        policy.get("max_production_development_configurations"),
        "max_production_development_configurations",
    )
    if metrics["production_development_configurations"] > fixture_configuration_limit:
        raise DebtError(
            "production development configurations grew: "
            f"{metrics['production_development_configurations']}>"
            f"{fixture_configuration_limit}"
        )
    profile_branch_limit = _positive_int(
        policy.get("max_lifecycle_fixture_profile_branches"),
        "max_lifecycle_fixture_profile_branches",
    )
    if metrics["lifecycle_fixture_profile_branches"] > profile_branch_limit:
        raise DebtError(
            "production lifecycle fixture profile branches grew: "
            f"{metrics['lifecycle_fixture_profile_branches']}>{profile_branch_limit}"
        )
    process_in_model_limit = _positive_int(
        policy.get("max_process_classes_in_model"), "max_process_classes_in_model"
    )
    if metrics["process_classes_in_model"] > process_in_model_limit:
        raise DebtError(
            "process ownership spread inside model: "
            f"{metrics['process_classes_in_model']}>{process_in_model_limit}"
        )
    codecs_in_model_limit = _positive_int(
        policy.get("max_codec_classes_in_model"), "max_codec_classes_in_model"
    )
    if metrics["codec_classes_in_model"] > codecs_in_model_limit:
        raise DebtError(
            "persistence ownership spread inside model: "
            f"{metrics['codec_classes_in_model']}>{codecs_in_model_limit}"
        )

    for label in (
        "direct_world_state_construction",
        "persisted_enum_position_tags",
        "scene_cause_type_branches",
    ):
        _validate_counted_files(metrics[label], policy.get(label), label)


def validate_path(root: Path, policy_path: Path) -> None:
    try:
        document = yaml.safe_load(policy_path.read_text(encoding="utf-8"))
    except yaml.YAMLError as error:
        raise DebtError(f"invalid architecture debt YAML: {error}") from error
    validate(root, document)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("policy", type=Path)
    parser.add_argument("--root", type=Path, default=Path.cwd())
    args = parser.parse_args()
    try:
        validate_path(args.root.resolve(), args.policy.resolve())
    except DebtError as error:
        parser.error(str(error))
    print(f"Frontier v3 architecture debt within ceiling: {args.policy}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
