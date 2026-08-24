#!/usr/bin/env python3
"""Bridge a reviewed SV3D orbit into the bounded legacy VGGT input shape.

The bridge deliberately changes no evidence tier.  It copies the pinned
trace-masked primary plus the twenty reviewed-*as-a-sequence* synthetic frames
into a fresh noncanonical run directory, where the existing VGGT pilot can
read them.  The resulting manifest still says that the poses are uncalibrated
model evidence and that all outputs are non-exportable.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import shutil
from typing import Any

from contracts import (
    AutomodelContractError,
    EvidenceTier,
    load_object,
    require_relative_build_path,
    sha256,
    validate_run_manifest,
    validate_view_set,
    write_object,
)


ROOT = Path(__file__).resolve().parents[2]
LEGACY_VGGT_SCHEMA = "pale_mirror_visuals.harvester_vggt_turntable_input.v1"
TURN_TABLE_RECEIPT_SCHEMA = "pale_mirror.automodel.turntable_review_receipt.v1"


def _relative(path: Path, repository_root: Path, label: str) -> str:
    try:
        relative = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"{label} must stay inside the repository") from exc
    return require_relative_build_path(relative, label)


def _verified_file(path: Path, expected_sha256: str, label: str) -> Path:
    if not path.is_file() or sha256(path) != expected_sha256:
        raise AutomodelContractError(f"{label} is missing or changed: {path}")
    return path


def _reviewed_orbit_receipt(
    receipt_path: Path,
    view_set_path: Path,
    repository_root: Path,
) -> dict[str, Any]:
    receipt_relative = _relative(receipt_path, repository_root, "turntable_review_receipt.file")
    receipt = load_object(receipt_path)
    if (
        receipt.get("schema") != TURN_TABLE_RECEIPT_SCHEMA
        or receipt.get("scope") != "research_only_noncanonical"
        or receipt.get("promotion_prohibited") is not True
        or receipt.get("decision") != "eligible_for_pose_depth_only"
    ):
        raise AutomodelContractError("VGGT bridge requires an explicit eligible noncanonical turntable-review receipt")
    package = receipt.get("review_package")
    if not isinstance(package, dict):
        raise AutomodelContractError("turntable review receipt has no review package")
    package_relative = require_relative_build_path(package.get("file"), "review_package.file")
    package_path = repository_root / package_relative
    _verified_file(package_path, package.get("sha256"), "turntable review package")
    review_package = load_object(package_path)
    source_view_set = review_package.get("view_set")
    if not isinstance(source_view_set, dict):
        raise AutomodelContractError("turntable review package has no source ViewSet")
    expected_view_relative = _relative(view_set_path, repository_root, "view_set.file")
    if source_view_set.get("file") != expected_view_relative or source_view_set.get("sha256") != sha256(view_set_path):
        raise AutomodelContractError("turntable review receipt does not attest this ViewSet")
    return {"receipt": receipt, "receipt_file": receipt_relative}


def prepare(
    view_set_path: Path,
    turntable_review_receipt_path: Path,
    output_directory: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    """Write the only permitted SV3D -> VGGT hand-off, once per fresh run."""

    view_set_relative = _relative(view_set_path, repository_root, "view_set.file")
    output_relative = _relative(output_directory, repository_root, "vggt_input_directory")
    if output_directory.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable VGGT bridge directory: {output_directory}")
    view_set = load_object(view_set_path)
    validate_view_set(view_set)
    if view_set.get("asset_id") != "biomass_collector":
        raise AutomodelContractError("the existing VGGT pilot is limited to biomass_collector")
    frames = view_set["frames"]
    expected_yaws = [float(index * 18) for index in range(21)]
    if len(frames) != 21 or [frame.get("declared_yaw_degrees") for frame in frames] != expected_yaws:
        raise AutomodelContractError("VGGT bridge requires the ordered fixed 21-position SV3D orbit")
    if frames[0].get("tier") != EvidenceTier.PRIMARY_TRACE.value or any(
        frame.get("tier") != EvidenceTier.MODEL_DERIVED.value for frame in frames[1:]
    ):
        raise AutomodelContractError("VGGT bridge may only consume a literal primary plus unpromoted model-derived orbit")
    review = _reviewed_orbit_receipt(turntable_review_receipt_path, view_set_path, repository_root)

    run_record = view_set.get("run_manifest")
    if not isinstance(run_record, dict):
        raise AutomodelContractError("ViewSet has no run manifest")
    run_manifest_relative = require_relative_build_path(run_record.get("file"), "run_manifest.file")
    run_manifest_path = repository_root / run_manifest_relative
    _verified_file(run_manifest_path, run_record.get("sha256"), "SV3D run manifest")
    run_manifest = load_object(run_manifest_path)
    validate_run_manifest(run_manifest)
    conditioning = [record for record in run_manifest["inputs"] if record.get("role") == "conditioning:trace_masked_primary"]
    if len(conditioning) != 1:
        raise AutomodelContractError("SV3D run manifest must pin exactly one trace-masked primary conditioning input")
    conditioning_record = conditioning[0]
    conditioning_path = repository_root / require_relative_build_path(
        conditioning_record.get("file"), "conditioning.file"
    )
    _verified_file(conditioning_path, conditioning_record.get("sha256"), "trace-masked primary conditioning")

    output_directory.mkdir(parents=True)
    frames_directory = output_directory / "frames"
    frames_directory.mkdir()
    output_frames: list[dict[str, Any]] = []
    primary_target = frames_directory / "frame_00_yaw_000_literal_trace_masked.png"
    shutil.copyfile(conditioning_path, primary_target)
    output_frames.append(
        {
            "index": 0,
            "yaw_degrees": 0,
            "file": str(primary_target.relative_to(output_directory)),
            "sha256": sha256(primary_target),
            "source": "literal_primary_trace_masked",
            "authority": "sole_likeness_anchor",
        }
    )
    for index, source_frame in enumerate(frames[1:], start=1):
        source_path = repository_root / require_relative_build_path(source_frame.get("file"), "view_set.frame.file")
        _verified_file(source_path, source_frame.get("sha256"), f"SV3D frame {index}")
        target = frames_directory / f"frame_{index:02d}_yaw_{index * 18:03d}_model_derived.png"
        shutil.copyfile(source_path, target)
        output_frames.append(
            {
                "index": index,
                "yaw_degrees": index * 18,
                "file": str(target.relative_to(output_directory)),
                "sha256": sha256(target),
                "source": "reviewed_sv3d_orbit_model_derived",
                "authority": "secondary_pose_depth_suggestion_only",
                "view_set_frame": {"id": source_frame["id"], "sha256": source_frame["sha256"]},
            }
        )
    manifest = {
        "schema": LEGACY_VGGT_SCHEMA,
        "asset_id": view_set["asset_id"],
        "status": "reviewed_orbit_pose_depth_only_noncanonical",
        "frame_count": len(output_frames),
        "frame_order": "declared 18-degree SV3D orbit; review permits a bounded pose/depth trial only and does not declare camera calibration",
        "frames": output_frames,
        "automodel_provenance": {
            "view_set": {"file": view_set_relative, "sha256": sha256(view_set_path)},
            "sv3d_run_manifest": {"file": run_manifest_relative, "sha256": sha256(run_manifest_path)},
            "turntable_review_receipt": {"file": review["receipt_file"], "sha256": sha256(turntable_review_receipt_path)},
            "conditioning_primary": {
                "file": conditioning_record["file"],
                "sha256": conditioning_record["sha256"],
            },
        },
        "acceptance_contract": {
            "literal_primary_is_sole_likeness_authority": True,
            "generated_frames_require_manual_consistency_review": True,
            "generated_frames_are_not_camera_calibration": True,
            "turntable_review_permits_pose_depth_only": True,
            "vggt_output_is_noncanonical_nonexportable": True,
            "vggt_may_only_guide_existing_surface_retopology_after_literal_trace_audit": True,
        },
        "manual_rejection_gates": [
            "Any geometry result that conflicts with the literal primary trace is rejected.",
            "VGGT poses that do not progress monotonically with the declared orbit reject the complete pose/depth result.",
            "The point cloud is review-only and never a mesh, Blender edit, PMMesh or runtime asset.",
        ],
    }
    manifest_path = output_directory / "manifest.json"
    write_object(manifest_path, manifest)
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--view-set", required=True, type=Path)
    parser.add_argument("--turntable-review-receipt", required=True, type=Path)
    parser.add_argument("--output-directory", required=True, type=Path)
    arguments = parser.parse_args()
    print(
        json.dumps(
            prepare(
            arguments.view_set,
            arguments.turntable_review_receipt,
            arguments.output_directory,
            ),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
