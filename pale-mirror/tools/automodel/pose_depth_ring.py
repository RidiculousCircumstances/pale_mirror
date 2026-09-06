#!/usr/bin/env python3
"""Prepare and compare independent DA3/VGGT pose-depth diagnostics.

The module is deliberately model-agnostic at its boundary.  Registered runners
receive one accepted eight-view input and may emit only poses, relative depth,
confidence and point-cloud diagnostics.  Agreement is not a mesh fuse: it is
recorded solely as a map of places a human should inspect.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from PIL import Image

from contracts import AutomodelContractError, load_object, require_identifier, require_relative_build_path, require_revision, require_sha256, sha256, write_object
from multiview_ring import RING_YAWS, validate_geometry_input, validate_reviewed_view_set
from ring_benchmark import validate_qualification
from registry import load_registry, require_local_preflight


ROOT = Path(__file__).resolve().parents[2]
POSE_DEPTH_KINDS = frozenset({"camera_pose", "depth", "confidence", "point_cloud"})


def _relative(path: Path, repository_root: Path, label: str) -> str:
    try:
        value = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"{label} must stay inside this repository") from exc
    return require_relative_build_path(value, label)


def _require_pinned(record: dict[str, Any], repository_root: Path, label: str) -> Path:
    if not isinstance(record, dict):
        raise AutomodelContractError(f"{label} must be a record")
    relative = require_relative_build_path(record.get("file"), f"{label}.file")
    require_sha256(record.get("sha256"), f"{label}.sha256")
    path = repository_root / relative
    if not path.is_file() or sha256(path) != record["sha256"]:
        raise AutomodelContractError(f"{label} is missing or changed")
    return path


def load_accepted_geometry_input(path: Path, *, repository_root: Path = ROOT) -> dict[str, Any]:
    value = load_object(path)
    validate_geometry_input(value)
    _require_pinned(value["reviewed_view_set"], repository_root, "geometry_input.reviewed_view_set")
    for frame in value["frames"]:
        _require_pinned(frame, repository_root, f"geometry_input.{frame['id']}")
    return value


def prepare_pose_depth_run(
    geometry_input_path: Path,
    adapter_id: str,
    run_directory: Path,
    adapter_revision: str,
    checkpoint_receipt_path: Path,
    environment_lock_path: Path,
    qualification_path: Path,
    *,
    available_vram_gib: int,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    geometry = load_accepted_geometry_input(geometry_input_path, repository_root=repository_root)
    if adapter_id not in {"da3_base", "vggt_official"}:
        raise AutomodelContractError("only DA3-Base and official VGGT may consume the reviewed still ring")
    spec = require_local_preflight(load_registry(), adapter_id, available_vram_gib)
    if spec.kind != "pose_depth":
        raise AutomodelContractError("geometry adapter must have pose_depth role")
    qualification = _require_pinned(
        {"file": _relative(qualification_path, repository_root, "ring_qualification"), "sha256": sha256(qualification_path)},
        repository_root,
        "ring_qualification",
    )
    qualification_record = load_object(qualification)
    validate_qualification(qualification_record, adapter_id=adapter_id)
    if qualification_record["adapter_revision"] != require_revision(adapter_revision, "adapter_revision"):
        raise AutomodelContractError("ring qualification must match the prepared adapter revision")
    if qualification_record["conclusion"] != "adapter_role_qualified":
        raise AutomodelContractError("adapter synthetic eight-view qualification is rejected")
    checkpoint_receipt = _require_pinned(
        {"file": _relative(checkpoint_receipt_path, repository_root, "checkpoint_receipt"), "sha256": sha256(checkpoint_receipt_path)},
        repository_root,
        "checkpoint_receipt",
    )
    environment_lock = _require_pinned(
        {"file": _relative(environment_lock_path, repository_root, "environment_lock"), "sha256": sha256(environment_lock_path)},
        repository_root,
        "environment_lock",
    )
    try:
        relative_run = run_directory.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("pose-depth run directory must stay inside the repository") from exc
    require_relative_build_path(relative_run, "pose_depth_run_directory")
    manifest_path = run_directory / "pose_depth_run_manifest.json"
    if manifest_path.exists() or run_directory.exists():
        raise AutomodelContractError("pose-depth run directory must be fresh and immutable")
    manifest = {
        "schema": "pale_mirror.automodel.pose_depth_run_manifest.v1",
        "asset_id": geometry["asset_id"],
        "adapter_id": adapter_id,
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "run_directory": relative_run,
        "geometry_input": {"file": _relative(geometry_input_path, repository_root, "geometry_input"), "sha256": sha256(geometry_input_path)},
        "ring_qualification": {"file": _relative(qualification, repository_root, "ring_qualification"), "sha256": sha256(qualification)},
        "provenance": {
            "adapter_revision": require_revision(adapter_revision, "adapter_revision"),
            "checkpoint_receipt": {"file": _relative(checkpoint_receipt, repository_root, "checkpoint_receipt"), "sha256": sha256(checkpoint_receipt)},
            "environment_lock": {"file": _relative(environment_lock, repository_root, "environment_lock"), "sha256": sha256(environment_lock)},
            "minimum_vram_gib": spec.minimum_vram_gib,
            "available_vram_gib": available_vram_gib,
            "license": spec.licence,
        },
        "execution": {"status": "prepared_not_executed", "allowed_outputs": sorted(POSE_DEPTH_KINDS)},
        "contract": {
            "requires_adapter_synthetic_8_view_qualification": True,
            "model_agreement_must_not_fuse_mesh_or_point_cloud": True,
            "canonical_blender_pmmesh_runtime_server_world_access_forbidden": True,
        },
    }
    run_directory.mkdir(parents=True)
    write_object(manifest_path, manifest)
    return manifest


def validate_pose_depth_run_manifest(value: dict[str, Any]) -> None:
    if value.get("schema") != "pale_mirror.automodel.pose_depth_run_manifest.v1":
        raise AutomodelContractError("pose-depth run manifest has an unsupported schema")
    if value.get("scope") != "research_only_noncanonical" or value.get("promotion_prohibited") is not True:
        raise AutomodelContractError("pose-depth run manifest must remain noncanonical")
    require_identifier(value.get("asset_id"), "pose_depth_run.asset_id")
    if value.get("adapter_id") not in {"da3_base", "vggt_official"}:
        raise AutomodelContractError("pose-depth run manifest has an unsupported adapter")
    require_relative_build_path(value.get("run_directory"), "pose_depth_run.run_directory")
    qualification = value.get("ring_qualification")
    if not isinstance(qualification, dict):
        raise AutomodelContractError("pose-depth run must pin a successful synthetic eight-view qualification")
    require_relative_build_path(qualification.get("file"), "pose_depth_run.ring_qualification.file")
    require_sha256(qualification.get("sha256"), "pose_depth_run.ring_qualification.sha256")
    if value.get("execution", {}).get("status") != "prepared_not_executed":
        raise AutomodelContractError("pose-depth run must be prepared and unexecuted")


def collect_pose_depth_evidence(
    run_manifest_path: Path,
    observations: dict[str, Path],
    output_path: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    manifest = load_object(run_manifest_path)
    validate_pose_depth_run_manifest(manifest)
    run_directory = repository_root / manifest["run_directory"]
    if run_manifest_path.resolve().parent != run_directory.resolve():
        raise AutomodelContractError("pose-depth run manifest must be directly inside its run directory")
    if set(observations) != POSE_DEPTH_KINDS:
        raise AutomodelContractError("pose-depth evidence requires pose, depth, confidence and point cloud exactly once")
    records: list[dict[str, Any]] = []
    for kind in sorted(POSE_DEPTH_KINDS):
        path = observations[kind].resolve()
        if not path.is_file() or not path.is_relative_to(run_directory.resolve()):
            raise AutomodelContractError(f"{kind} output must be a pre-existing runner file below its fresh run directory")
        if path.suffix.lower() not in {".npz", ".npy", ".png", ".ply", ".json"}:
            raise AutomodelContractError(f"unsupported {kind} output format")
        records.append({"kind": kind, "tier": "MODEL_DERIVED", "file": _relative(path, repository_root, kind), "sha256": sha256(path)})
    evidence = {
        "schema": "pale_mirror.automodel.pose_depth_evidence.v2",
        "asset_id": manifest["asset_id"],
        "adapter_id": manifest["adapter_id"],
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "run_manifest": {"file": _relative(run_manifest_path, repository_root, "run_manifest"), "sha256": sha256(run_manifest_path)},
        "observations": records,
        "contract": {
            "diagnostic_only": True,
            "point_cloud_is_not_a_mesh": True,
            "canonical_blender_pmmesh_runtime_server_world_access_forbidden": True,
        },
    }
    if output_path.exists() or not output_path.resolve().is_relative_to(run_directory.resolve()):
        raise AutomodelContractError("pose-depth evidence must be a fresh file below its run directory")
    write_object(output_path, evidence)
    return evidence


def _normalised_depth(path: Path) -> tuple[int, int, list[float]]:
    """Read a portable depth visual without making guardrails depend on NumPy.

    Actual model runners may save their raw tensor separately.  The agreement
    report consumes the required 8-bit diagnostic PNG, whose scale is already
    relative and therefore suitable only for inspection.
    """

    if path.suffix.lower() != ".png":
        raise AutomodelContractError("depth disagreement requires a portable grayscale PNG diagnostic")
    with Image.open(path) as image:
        values = image.convert("L")
        width, height = values.size
        pixels = [value / 255.0 for value in values.get_flattened_data()]
    if not pixels or min(pixels) == max(pixels):
        raise AutomodelContractError("depth diagnostic has no usable dynamic range")
    return width, height, pixels


def write_disagreement_report(
    da3_depth: Path,
    vggt_depth: Path,
    output_directory: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    first_width, first_height, first = _normalised_depth(da3_depth)
    second_width, second_height, second = _normalised_depth(vggt_depth)
    if (first_width, first_height) != (second_width, second_height):
        raise AutomodelContractError("DA3 and VGGT depth diagnostics must use matching raster dimensions")
    if output_directory.exists():
        raise AutomodelContractError("disagreement output directory must be fresh")
    _relative(output_directory, repository_root, "disagreement_output_directory")
    output_directory.mkdir(parents=True)
    difference = [abs(left - right) for left, right in zip(first, second, strict=True)]
    heat = [(round(value * 255), round((1.0 - value) * 100), round((1.0 - value) * 255)) for value in difference]
    heat_path = output_directory / "depth_disagreement.png"
    heat_image = Image.new("RGB", (first_width, first_height))
    heat_image.putdata(heat)
    heat_image.save(heat_path)
    report = {
        "schema": "pale_mirror.automodel.geometry_disagreement_report.v1",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "inputs": {
            "da3_depth": {"file": _relative(da3_depth, repository_root, "da3_depth"), "sha256": sha256(da3_depth)},
            "vggt_depth": {"file": _relative(vggt_depth, repository_root, "vggt_depth"), "sha256": sha256(vggt_depth)},
        },
        "outputs": {"depth_disagreement": {"file": heat_path.name, "sha256": sha256(heat_path)}},
        "mean_absolute_depth_difference": sum(difference) / len(difference),
        "contract": {
            "inspection_only_uncertainty_not_quality_score": True,
            "does_not_select_or_fuse_a_mesh": True,
            "does_not_promote_any_evidence": True,
        },
    }
    write_object(output_directory / "geometry_disagreement_report.json", report)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare = commands.add_parser("prepare-run")
    prepare.add_argument("--geometry-input", type=Path, required=True)
    prepare.add_argument("--adapter", choices=("da3_base", "vggt_official"), required=True)
    prepare.add_argument("--run-directory", type=Path, required=True)
    prepare.add_argument("--adapter-revision", required=True)
    prepare.add_argument("--checkpoint-receipt", type=Path, required=True)
    prepare.add_argument("--environment-lock", type=Path, required=True)
    prepare.add_argument("--qualification", type=Path, required=True)
    prepare.add_argument("--available-vram-gib", type=int, default=8)
    collect = commands.add_parser("collect")
    collect.add_argument("--run-manifest", type=Path, required=True)
    collect.add_argument("--camera-pose", type=Path, required=True)
    collect.add_argument("--depth", type=Path, required=True)
    collect.add_argument("--confidence", type=Path, required=True)
    collect.add_argument("--point-cloud", type=Path, required=True)
    collect.add_argument("--output", type=Path, required=True)
    disagreement = commands.add_parser("disagreement")
    disagreement.add_argument("--da3-depth", type=Path, required=True)
    disagreement.add_argument("--vggt-depth", type=Path, required=True)
    disagreement.add_argument("--output-directory", type=Path, required=True)
    arguments = parser.parse_args()
    if arguments.command == "prepare-run":
        result = prepare_pose_depth_run(arguments.geometry_input, arguments.adapter, arguments.run_directory, arguments.adapter_revision, arguments.checkpoint_receipt, arguments.environment_lock, arguments.qualification, available_vram_gib=arguments.available_vram_gib)
    elif arguments.command == "collect":
        result = collect_pose_depth_evidence(arguments.run_manifest, {"camera_pose": arguments.camera_pose, "depth": arguments.depth, "confidence": arguments.confidence, "point_cloud": arguments.point_cloud}, arguments.output)
    elif arguments.command == "disagreement":
        result = write_disagreement_report(arguments.da3_depth, arguments.vggt_depth, arguments.output_directory)
    else:
        raise AssertionError("unhandled command")
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
