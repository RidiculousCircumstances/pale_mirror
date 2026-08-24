#!/usr/bin/env python3
"""Normalise outputs from registered local Automodel runners.

This module is intentionally *not* a generic process launcher.  A pinned,
model-specific runner may write files below its prepared run directory; these
helpers then validate, hash and classify those files.  They neither download a
checkpoint nor execute a supplied command, and their output remains inside the
ignored Automodel build tree.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any, Iterable, Sequence

from contracts import (
    AutomodelContractError,
    EvidenceTier,
    load_object,
    require_relative_build_path,
    sha256,
    validate_execution_receipt,
    validate_geometry_evidence,
    validate_run_manifest,
    validate_shape_proposal,
    validate_view_set,
    write_object,
)
from registry import load_registry


ROOT = Path(__file__).resolve().parents[2]
# A literal primary is a byte-for-byte staged source asset.  Staging supports
# common source photograph formats, so collection must accept the same set;
# otherwise a valid JPEG can finish inference but fail closed after producing
# an uncollectable orbit.
IMAGE_SUFFIXES = frozenset({".jpg", ".jpeg", ".png", ".webp"})
GEOMETRY_SUFFIXES = frozenset({".json", ".npy", ".npz", ".png", ".ply"})
SHAPE_SUFFIXES = frozenset({".glb", ".gltf", ".obj", ".ply"})


def _relative(path: Path, repository_root: Path) -> str:
    resolved = path.resolve()
    try:
        relative = resolved.relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"Automodel artifact is outside this repository: {path}") from exc
    return require_relative_build_path(relative, "artifact.file")


def _new_json(path: Path, value: dict[str, Any]) -> None:
    if path.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable Automodel artifact: {path}")
    write_object(path, value)


def _load_prepared_run(run_manifest_path: Path, repository_root: Path) -> tuple[dict[str, Any], Path, str]:
    manifest_path = run_manifest_path.resolve()
    manifest = load_object(manifest_path)
    validate_run_manifest(manifest)
    manifest_relative = _relative(manifest_path, repository_root)
    run_directory = (repository_root / manifest["run_directory"]).resolve()
    if manifest_path.parent != run_directory:
        raise AutomodelContractError("run manifest must be stored directly in its declared run directory")
    if manifest.get("execution", {}).get("status") != "prepared_not_executed":
        raise AutomodelContractError("Automodel adapter requires a freshly prepared, unexecuted run manifest")
    return manifest, run_directory, manifest_relative


def _require_model_kind(adapter_id: str, expected_kind: str) -> None:
    spec = load_registry().get(adapter_id)
    if spec is None or not spec.local_preflight:
        raise AutomodelContractError(f"adapter is not enabled for local evidence collection: {adapter_id}")
    if spec.kind != expected_kind:
        raise AutomodelContractError(f"adapter {adapter_id} is {spec.kind}, not {expected_kind}")


def _require_runner_output(path: Path, run_directory: Path, suffixes: frozenset[str]) -> None:
    if path.suffix.lower() not in suffixes:
        allowed = ", ".join(sorted(suffixes))
        raise AutomodelContractError(f"unsupported Automodel output type {path.suffix}; expected one of {allowed}")
    resolved = path.resolve()
    if not resolved.is_file() or not resolved.is_relative_to(run_directory):
        raise AutomodelContractError("model output must already exist below its prepared run directory")


def _record(path: Path, repository_root: Path, **extra: Any) -> dict[str, Any]:
    return {"file": _relative(path, repository_root), "sha256": sha256(path), **extra}


def _write_receipt(
    manifest: dict[str, Any],
    run_manifest_path: Path,
    repository_root: Path,
    receipt_path: Path,
    stage: str,
    outputs: Iterable[Path],
) -> dict[str, Any]:
    receipt = {
        "schema": "pale_mirror.automodel.execution_receipt.v1",
        "asset_id": manifest["asset_id"],
        "adapter_id": manifest["adapter_id"],
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "stage": stage,
        "run_manifest": _record(run_manifest_path, repository_root),
        "outputs": [_record(output, repository_root) for output in outputs],
    }
    validate_execution_receipt(receipt)
    _new_json(receipt_path, receipt)
    return receipt


def collect_view_synthesis(
    run_manifest_path: Path,
    literal_primary: Path,
    generated_frames: Sequence[Path],
    output_path: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    """Write a ViewSet from an SV3D-style orbit without granting it authority.

    The literal source primary must already be one of the prepared manifest
    inputs.  The fixed 21-position experiment discards its generated zero view,
    therefore it contributes exactly twenty model-derived frames plus that
    literal primary.
    """

    manifest, run_directory, _ = _load_prepared_run(run_manifest_path, repository_root)
    _require_model_kind(manifest["adapter_id"], "view_synthesis")
    literal = literal_primary.resolve()
    literal_record = _record(literal, repository_root)
    if not any(
        input_record.get("role") == "primary"
        and input_record.get("file") == literal_record["file"]
        and input_record.get("sha256") == literal_record["sha256"]
        for input_record in manifest["inputs"]
    ):
        raise AutomodelContractError("literal primary must be one of the hash-pinned run inputs")
    if literal.suffix.lower() not in IMAGE_SUFFIXES:
        raise AutomodelContractError("literal primary must be a supported image")
    if len(generated_frames) != 20:
        raise AutomodelContractError("a 21-position orbit requires exactly twenty non-primary generated frames")
    records: list[dict[str, Any]] = [
        {
            "id": "literal_000",
            "tier": EvidenceTier.PRIMARY_TRACE.value,
            **literal_record,
            "declared_yaw_degrees": 0.0,
            "role": "literal_source_primary",
        }
    ]
    for index, frame in enumerate(generated_frames, start=1):
        _require_runner_output(frame, run_directory, IMAGE_SUFFIXES)
        records.append(
            {
                "id": f"generated_{index * 18:03d}",
                "tier": EvidenceTier.MODEL_DERIVED.value,
                **_record(frame, repository_root),
                "declared_yaw_degrees": float(index * 18),
                "role": "synthetic_orbit_frame",
            }
        )
    view_set = {
        "schema": "pale_mirror.automodel.view_set.v1",
        "asset_id": manifest["asset_id"],
        "run_manifest": _record(run_manifest_path, repository_root),
        "frames": records,
    }
    validate_view_set(view_set)
    _relative(output_path, repository_root)
    if not output_path.resolve().is_relative_to(run_directory):
        raise AutomodelContractError("view set must remain inside its prepared run directory")
    _new_json(output_path, view_set)
    _write_receipt(
        manifest,
        run_manifest_path,
        repository_root,
        run_directory / "execution_receipt_view_set.json",
        "view_set_collected",
        [output_path],
    )
    return view_set


def collect_geometry_evidence(
    run_manifest_path: Path,
    observations: Sequence[tuple[str, Path]],
    output_path: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    """Record depth, pose, normal or point-cloud output as model evidence."""

    manifest, run_directory, _ = _load_prepared_run(run_manifest_path, repository_root)
    _require_model_kind(manifest["adapter_id"], "pose_depth")
    if not observations:
        raise AutomodelContractError("geometry collection requires at least one observation")
    records: list[dict[str, Any]] = []
    for kind, artifact in observations:
        if kind not in {"camera_pose", "depth", "confidence", "normal", "point_cloud"}:
            raise AutomodelContractError(f"unsupported geometry observation kind: {kind}")
        _require_runner_output(artifact, run_directory, GEOMETRY_SUFFIXES)
        records.append(
            {
                "adapter_id": manifest["adapter_id"],
                "tier": EvidenceTier.MODEL_DERIVED.value,
                "kind": kind,
                **_record(artifact, repository_root),
            }
        )
    evidence = {
        "schema": "pale_mirror.automodel.geometry_evidence.v1",
        "asset_id": manifest["asset_id"],
        "scope": "research_only_noncanonical",
        "run_manifest": _record(run_manifest_path, repository_root),
        "observations": records,
    }
    validate_geometry_evidence(evidence)
    _relative(output_path, repository_root)
    if not output_path.resolve().is_relative_to(run_directory):
        raise AutomodelContractError("geometry evidence must remain inside its prepared run directory")
    _new_json(output_path, evidence)
    _write_receipt(
        manifest,
        run_manifest_path,
        repository_root,
        run_directory / "execution_receipt_geometry.json",
        "geometry_evidence_collected",
        [output_path],
    )
    return evidence


def collect_shape_proposal(
    run_manifest_path: Path,
    shape_files: Sequence[Path],
    output_path: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    """Record a local mesh proposal without importing it into Blender."""

    manifest, run_directory, _ = _load_prepared_run(run_manifest_path, repository_root)
    _require_model_kind(manifest["adapter_id"], "shape_proposal")
    if not shape_files:
        raise AutomodelContractError("shape collection requires at least one model proposal")
    proposals: list[dict[str, Any]] = []
    for shape in shape_files:
        _require_runner_output(shape, run_directory, SHAPE_SUFFIXES)
        proposals.append(
            {
                "adapter_id": manifest["adapter_id"],
                "tier": EvidenceTier.MODEL_DERIVED.value,
                "role": "review_only_shape_hypothesis",
                **_record(shape, repository_root),
            }
        )
    proposal = {
        "schema": "pale_mirror.automodel.shape_proposal.v1",
        "asset_id": manifest["asset_id"],
        "scope": "research_only_noncanonical",
        "run_manifest": _record(run_manifest_path, repository_root),
        "proposals": proposals,
    }
    validate_shape_proposal(proposal)
    _relative(output_path, repository_root)
    if not output_path.resolve().is_relative_to(run_directory):
        raise AutomodelContractError("shape proposal must remain inside its prepared run directory")
    _new_json(output_path, proposal)
    _write_receipt(
        manifest,
        run_manifest_path,
        repository_root,
        run_directory / "execution_receipt_shape.json",
        "shape_proposal_collected",
        [output_path],
    )
    return proposal
