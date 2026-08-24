#!/usr/bin/env python3
"""Prepare validated, non-executable Automodel research manifests.

This orchestrator intentionally does not download checkpoints or execute an
arbitrary command.  A model-specific local runner is added only after a pinned
preflight succeeds.  The manifest prepared here is the required hand-off.
"""

from __future__ import annotations

import argparse
from datetime import UTC, datetime
import json
from pathlib import Path
from typing import Any

from adapters import collect_geometry_evidence, collect_shape_proposal, collect_view_synthesis
from contracts import (
    AutomodelContractError,
    BUILD_ROOT,
    load_object,
    manifest_inputs,
    require_identifier,
    require_relative_build_path,
    require_revision,
    validate_reference_bundle,
    validate_run_manifest,
    write_object,
)
from registry import load_registry, require_local_preflight
from review import build_turntable_review_package, record_turntable_review


ROOT = Path(__file__).resolve().parents[2]


def _required_hash(value: str, label: str) -> str:
    if len(value) != 64 or any(character not in "0123456789abcdef" for character in value):
        raise AutomodelContractError(f"{label} must be a lowercase SHA-256")
    return value


def prepare_run(
    reference_bundle_path: Path,
    adapter_id: str,
    run_directory: Path,
    *,
    available_vram_gib: int,
    adapter_revision: str,
    checkpoint_sha256: str,
    environment_lock_sha256: str,
    seed: int,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    if seed < 0:
        raise AutomodelContractError("seed must be non-negative")
    bundle = load_object(reference_bundle_path)
    validate_reference_bundle(bundle)
    registry = load_registry()
    spec = require_local_preflight(registry, adapter_id, available_vram_gib)
    resolved_run = run_directory.resolve()
    try:
        run_relative = resolved_run.relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("run directory must be within this repository") from exc
    require_relative_build_path(run_relative, "run_directory")
    primary = bundle["primary"]
    trace = bundle["primary_trace"]
    input_paths: list[tuple[str, Path]] = []
    for role, record in (("primary", primary), ("primary_trace", trace)):
        candidate = repository_root / record["file"]
        if candidate.is_file() and candidate.is_relative_to(repository_root / BUILD_ROOT):
            input_paths.append((role, candidate))
    if not input_paths:
        raise AutomodelContractError("reference bundle must point at staged, hash-pinned build inputs")
    for model_input in bundle.get("model_inputs", []):
        candidate = repository_root / model_input["file"]
        if not candidate.is_file() or not candidate.is_relative_to(repository_root / BUILD_ROOT):
            raise AutomodelContractError("model conditioning input must be a staged build artifact")
        input_paths.append((f"conditioning:{model_input['id']}", candidate))
    actual_inputs = []
    for role, path in input_paths:
        record = manifest_inputs([path], repository_root)[0]
        record["role"] = role
        actual_inputs.append(record)
    manifest = {
        "schema": "pale_mirror.automodel.run_manifest.v1",
        "asset_id": bundle["asset_id"],
        "adapter_id": spec.identifier,
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "run_directory": run_relative,
        "created_utc": datetime.now(UTC).isoformat().replace("+00:00", "Z"),
        "seed": seed,
        "provenance": {
            "adapter_revision": require_revision(adapter_revision, "adapter_revision"),
            "checkpoint_sha256": _required_hash(checkpoint_sha256, "checkpoint_sha256"),
            "environment_lock_sha256": _required_hash(environment_lock_sha256, "environment_lock_sha256"),
            "license": spec.licence,
            "executor": spec.executor,
            "minimum_vram_gib": spec.minimum_vram_gib,
            "available_vram_gib": available_vram_gib,
        },
        "inputs": actual_inputs,
        "execution": {
            "status": "prepared_not_executed",
            "reason": "Only a pinned model-specific runner may execute this manifest.",
        },
    }
    validate_run_manifest(manifest)
    write_object(resolved_run / "run_manifest.json", manifest)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    prepare = subparsers.add_parser("prepare-run", help="Write a validated noncanonical local-run manifest.")
    prepare.add_argument("--reference-bundle", type=Path, required=True)
    prepare.add_argument("--adapter", required=True)
    prepare.add_argument("--run-directory", type=Path, required=True)
    prepare.add_argument("--available-vram-gib", type=int, required=True)
    prepare.add_argument("--adapter-revision", required=True)
    prepare.add_argument("--checkpoint-sha256", required=True)
    prepare.add_argument("--environment-lock-sha256", required=True)
    prepare.add_argument("--seed", type=int, required=True)
    views = subparsers.add_parser("collect-views", help="Hash and classify existing local view-synthesis output.")
    views.add_argument("--run-manifest", type=Path, required=True)
    views.add_argument("--literal-primary", type=Path, required=True)
    views.add_argument("--generated-directory", type=Path, required=True)
    views.add_argument("--output", type=Path, required=True)
    geometry = subparsers.add_parser("collect-geometry", help="Hash and classify existing local pose/depth output.")
    geometry.add_argument("--run-manifest", type=Path, required=True)
    geometry.add_argument(
        "--observation",
        action="append",
        required=True,
        metavar="KIND=PATH",
        help="One camera_pose, depth, confidence, normal or point_cloud output.",
    )
    geometry.add_argument("--output", type=Path, required=True)
    shape = subparsers.add_parser("collect-shape", help="Hash and classify existing local shape-proposal output.")
    shape.add_argument("--run-manifest", type=Path, required=True)
    shape.add_argument("--shape", action="append", type=Path, required=True)
    shape.add_argument("--output", type=Path, required=True)
    turntable_review = subparsers.add_parser(
        "review-turntable",
        help="Build an immutable contact-sheet package for a fixed SV3D orbit.",
    )
    turntable_review.add_argument("--view-set", type=Path, required=True)
    turntable_review.add_argument("--output-directory", type=Path, required=True)
    record_review = subparsers.add_parser(
        "record-turntable-review",
        help="Record a human decision without promoting any generated frame.",
    )
    record_review.add_argument("--package", type=Path, required=True)
    record_review.add_argument("--output", type=Path, required=True)
    record_review.add_argument("--reviewer", required=True)
    record_review.add_argument(
        "--decision",
        choices=("eligible_for_pose_depth_only", "rejected"),
        required=True,
    )
    record_review.add_argument("--notes", required=True)
    arguments = parser.parse_args()
    if arguments.command == "prepare-run":
        result = prepare_run(
            arguments.reference_bundle,
            arguments.adapter,
            arguments.run_directory,
            available_vram_gib=arguments.available_vram_gib,
            adapter_revision=arguments.adapter_revision,
            checkpoint_sha256=arguments.checkpoint_sha256,
            environment_lock_sha256=arguments.environment_lock_sha256,
            seed=arguments.seed,
        )
    elif arguments.command == "collect-views":
        generated = sorted(
            path
            for path in arguments.generated_directory.iterdir()
            if path.is_file() and path.suffix.lower() in {".png", ".webp"}
        )
        result = collect_view_synthesis(arguments.run_manifest, arguments.literal_primary, generated, arguments.output)
    elif arguments.command == "collect-geometry":
        observations: list[tuple[str, Path]] = []
        for value in arguments.observation:
            if "=" not in value:
                parser.error("--observation must use KIND=PATH")
            kind, raw_path = value.split("=", 1)
            observations.append((kind, Path(raw_path)))
        result = collect_geometry_evidence(arguments.run_manifest, observations, arguments.output)
    elif arguments.command == "collect-shape":
        result = collect_shape_proposal(arguments.run_manifest, arguments.shape, arguments.output)
    elif arguments.command == "review-turntable":
        result = build_turntable_review_package(
            arguments.view_set,
            arguments.output_directory,
            ROOT,
        )
    elif arguments.command == "record-turntable-review":
        result = record_turntable_review(
            arguments.package,
            arguments.output,
            arguments.reviewer,
            arguments.decision,
            arguments.notes,
            repository_root=ROOT,
        )
    else:
        raise AssertionError(f"unhandled command: {arguments.command}")
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
