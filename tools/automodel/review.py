#!/usr/bin/env python3
"""Build review-only Automodel evidence packages and disagreement maps."""

from __future__ import annotations

import json
from pathlib import Path
import shutil
from typing import Any

from PIL import Image, ImageDraw

from contracts import (
    AutomodelContractError,
    EvidenceTier,
    load_object,
    parse_tier,
    require_relative_build_path,
    sha256,
    validate_geometry_evidence,
    validate_view_set,
    write_object,
)


def promote_secondary_frame(view_set: dict[str, Any], frame_id: str, reviewer: str, decision: str) -> dict[str, Any]:
    """Apply an explicit human receipt; no model signal can call this helper."""

    validate_view_set(view_set)
    if not reviewer.strip():
        raise AutomodelContractError("human review requires a named reviewer")
    if decision != "accept_identity_and_continuity":
        raise AutomodelContractError("only an explicit positive identity decision can promote a secondary view")
    copied = json.loads(json.dumps(view_set))
    for frame in copied["frames"]:
        if frame["id"] != frame_id:
            continue
        if parse_tier(frame["tier"], "frame.tier") is not EvidenceTier.MODEL_DERIVED:
            raise AutomodelContractError("only model-derived frames can become reviewed secondary evidence")
        frame["tier"] = EvidenceTier.REVIEWED_SECONDARY.value
        frame["review"] = {"status": "accepted_by_human", "reviewer": reviewer, "decision": decision}
        validate_view_set(copied)
        return copied
    raise AutomodelContractError(f"view set does not contain frame: {frame_id}")


def _copy_view_frames(view_set: dict[str, Any], output_directory: Path, repository_root: Path) -> list[dict[str, Any]]:
    public = output_directory / "public"
    public.mkdir(parents=True, exist_ok=True)
    copied: list[dict[str, Any]] = []
    for frame in view_set["frames"]:
        relative = require_relative_build_path(frame["file"], "frame.file")
        source = repository_root / relative
        if not source.is_file() or sha256(source) != frame["sha256"]:
            raise AutomodelContractError(f"missing or changed view frame: {relative}")
        target = public / Path(relative).name
        if target.exists():
            raise AutomodelContractError(f"review output already exists: {target}")
        shutil.copyfile(source, target)
        copied.append(
            {
                "frame_id": frame["id"],
                "declared_yaw_degrees": frame.get("declared_yaw_degrees"),
                "tier": frame["tier"],
                "file": str(target.relative_to(output_directory)),
                "sha256": sha256(target),
            }
        )
    return copied


def _write_turntable_contact_sheet(frames: list[dict[str, Any]], output_directory: Path) -> Path:
    columns, rows, cell = 7, 3, 256
    sheet = Image.new("RGB", (columns * cell, rows * cell), (14, 18, 21))
    draw = ImageDraw.Draw(sheet)
    for index, frame in enumerate(frames):
        image_path = output_directory / frame["file"]
        with Image.open(image_path) as source:
            image = source.convert("RGB")
        image.thumbnail((cell - 16, cell - 42), Image.Resampling.LANCZOS)
        x, y = (index % columns) * cell, (index // columns) * cell
        sheet.paste(image, (x + (cell - image.width) // 2, y + 30 + (cell - 38 - image.height) // 2))
        label = f"{frame['frame_id']}  {frame['declared_yaw_degrees']:03.0f}°"
        draw.rectangle((x, y, x + cell - 1, y + 28), fill=(0, 0, 0))
        draw.text((x + 8, y + 8), label, fill=(235, 235, 235))
    target = output_directory / "turntable_contact_sheet.png"
    sheet.save(target)
    return target


def build_turntable_review_package(
    view_set_path: Path,
    output_directory: Path,
    repository_root: Path,
) -> dict[str, Any]:
    """Package a fixed 21-position orbit for actual visual inspection.

    The result deliberately contains no promotion decision. It makes it
    possible to inspect each declared yaw and later record only whether the
    orbit is eligible as *secondary* pose/depth input.
    """

    try:
        view_set_relative = view_set_path.resolve().relative_to(repository_root.resolve()).as_posix()
        output_relative = output_directory.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("turntable review artifacts must stay inside the repository") from exc
    require_relative_build_path(view_set_relative, "view_set.file")
    require_relative_build_path(output_relative, "turntable_review_directory")
    view_set = load_object(view_set_path)
    validate_view_set(view_set)
    frames = view_set["frames"]
    expected_yaws = [float(index * 18) for index in range(21)]
    if len(frames) != 21 or [frame.get("declared_yaw_degrees") for frame in frames] != expected_yaws:
        raise AutomodelContractError("turntable review requires one literal primary and twenty ordered 18-degree frames")
    if frames[0].get("tier") != EvidenceTier.PRIMARY_TRACE.value or any(
        frame.get("tier") != EvidenceTier.MODEL_DERIVED.value for frame in frames[1:]
    ):
        raise AutomodelContractError("turntable review must retain literal primary authority and unreviewed synthetic frames")
    if output_directory.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable turntable review package: {output_directory}")
    output_directory.mkdir(parents=True)
    copied = _copy_view_frames(view_set, output_directory, repository_root)
    sheet = _write_turntable_contact_sheet(copied, output_directory)
    package = {
        "schema": "pale_mirror.automodel.turntable_review_package.v1",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "asset_id": view_set["asset_id"],
        "view_set": {"file": view_set_relative, "sha256": sha256(view_set_path)},
        "frames": copied,
        "contact_sheet": {"file": sheet.name, "sha256": sha256(sheet)},
        "required_review": [
            "Inspect all twenty generated frames against the literal primary, especially the six dorsal sacs, leading drape, supports and tail.",
            "Reject duplicate, omitted, abruptly mutated, background-contaminated or non-monotonic headings.",
            "An eligible orbit is secondary pose/depth evidence only; it does not change frame authority or canonical likeness.",
        ],
    }
    write_object(output_directory / "turntable_review_package.json", package)
    return package


def record_turntable_review(
    package_path: Path,
    output_path: Path,
    reviewer: str,
    decision: str,
    notes: str,
    *,
    repository_root: Path,
) -> dict[str, Any]:
    """Record an explicit review decision without promoting any view frame."""

    if decision not in {"eligible_for_pose_depth_only", "rejected"}:
        raise AutomodelContractError("turntable review decision must be eligible_for_pose_depth_only or rejected")
    if not reviewer.strip() or not notes.strip():
        raise AutomodelContractError("turntable review requires a named reviewer and concrete notes")
    package = load_object(package_path)
    if package.get("schema") != "pale_mirror.automodel.turntable_review_package.v1" or package.get("promotion_prohibited") is not True:
        raise AutomodelContractError("turntable review receipt requires a noncanonical review package")
    try:
        package_relative = package_path.resolve().relative_to(repository_root.resolve()).as_posix()
        output_relative = output_path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("turntable review artifacts must stay inside the repository") from exc
    require_relative_build_path(package_relative, "review_package.file")
    require_relative_build_path(output_relative, "review_receipt.file")
    if output_path.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable turntable review receipt: {output_path}")
    receipt = {
        "schema": "pale_mirror.automodel.turntable_review_receipt.v1",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "asset_id": package["asset_id"],
        "review_package": {"file": package_relative, "sha256": sha256(package_path)},
        "reviewer": reviewer,
        "decision": decision,
        "notes": notes,
        "contract": {
            "does_not_promote_secondary_frames": True,
            "does_not_authorize_blender_or_runtime_mutation": True,
        },
    }
    write_object(output_path, receipt)
    return receipt


def _heat_colour(value: int) -> tuple[int, int, int]:
    # Blue means agreement; red means uncertainty.  It is an inspection aid,
    # not an automatic quality score.
    return (value, max(0, 170 - value), 255 - value)


def write_depth_disagreement(first: Path, second: Path, output: Path) -> dict[str, Any]:
    """Write a deterministic heat map for two registered depth observations."""

    with Image.open(first) as source:
        first_depth = source.convert("L")
    with Image.open(second) as source:
        second_depth = source.convert("L")
    if first_depth.size != second_depth.size:
        raise AutomodelContractError("depth observations require matching dimensions")
    first_pixels = list(first_depth.get_flattened_data())
    second_pixels = list(second_depth.get_flattened_data())
    differences = [abs(left - right) for left, right in zip(first_pixels, second_pixels, strict=True)]
    heatmap = Image.new("RGB", first_depth.size)
    heatmap.putdata([_heat_colour(value) for value in differences])
    output.parent.mkdir(parents=True, exist_ok=True)
    heatmap.save(output)
    return {
        "schema": "pale_mirror.automodel.disagreement_map.v1",
        "kind": "depth_absolute_difference",
        "first": {"file": str(first), "sha256": sha256(first)},
        "second": {"file": str(second), "sha256": sha256(second)},
        "output": {"file": str(output), "sha256": sha256(output)},
        "mean_absolute_difference": sum(differences) / len(differences),
        "interpretation": "inspection_only_not_a_promotion_score",
    }


def build_review_package(
    view_set_path: Path,
    geometry_evidence_path: Path,
    output_directory: Path,
    repository_root: Path,
) -> dict[str, Any]:
    """Copy only declared research artifacts into a noncanonical review package."""

    view_set = load_object(view_set_path)
    geometry = load_object(geometry_evidence_path)
    validate_view_set(view_set)
    validate_geometry_evidence(geometry)
    output_directory.mkdir(parents=True, exist_ok=True)
    public = output_directory / "public"
    public.mkdir(parents=True, exist_ok=True)
    copied = _copy_view_frames(view_set, output_directory, repository_root)
    package = {
        "schema": "pale_mirror.automodel.review_package.v1",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "asset_id": view_set["asset_id"],
        "view_set_sha256": sha256(view_set_path),
        "geometry_evidence_sha256": sha256(geometry_evidence_path),
        "frames": copied,
        "required_review": [
            "Inspect identity continuity and the literal primary trace separately.",
            "Treat disagreement maps as uncertainty diagnostics, not a winner score.",
            "Do not import this package into canonical Blender or PMMesh export.",
        ],
    }
    write_object(output_directory / "review_package.json", package)
    return package
