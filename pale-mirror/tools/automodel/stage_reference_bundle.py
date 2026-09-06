#!/usr/bin/env python3
"""Stage a literal primary and clean trace-isolated Automodel input bundle.

The original primary may live outside the repository.  This tool copies its
literal bytes into an ignored run directory, pins the copied hash and, when
requested, derives a *conditioning-only* RGBA subject isolation.  The
isolation removes trace specks and tiny internal holes but deliberately does
not invent, retouch or extend unseen subject pixels.  It never changes the
source image, source trace, Blender, PMMesh or runtime resources.
"""

from __future__ import annotations

import argparse
import shutil
from collections import deque
from pathlib import Path
from typing import Any

from PIL import Image

from contracts import (
    AutomodelContractError,
    EvidenceTier,
    require_identifier,
    require_relative_build_path,
    sha256,
    validate_reference_bundle,
    write_object,
)


ROOT = Path(__file__).resolve().parents[2]
IMAGE_SUFFIXES = frozenset({".jpg", ".jpeg", ".png", ".webp"})
SV3D_FRAME_RATIO = 0.84
SV3D_FRAME_SIZE = 576


def _relative(path: Path, repository_root: Path) -> str:
    try:
        relative = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"staged artifact must remain in the repository: {path}") from exc
    return require_relative_build_path(relative, "staged.file")


def _copy_new(source: Path, target: Path) -> None:
    if not source.is_file():
        raise AutomodelContractError(f"staging source is missing: {source}")
    if target.exists():
        raise AutomodelContractError(f"refusing to overwrite staged Automodel input: {target}")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, target)


def _trace_mask(trace: dict[str, Any]) -> Image.Image:
    source = trace.get("source")
    sampling = trace.get("sampling")
    rows = trace.get("rows")
    if not isinstance(source, dict) or not isinstance(sampling, dict) or not isinstance(rows, list):
        raise AutomodelContractError("primary trace lacks source/sampling rows")
    dimensions = source.get("size")
    grid = sampling.get("grid_px")
    if not isinstance(dimensions, list) or len(dimensions) != 2 or not all(isinstance(value, int) and value > 0 for value in dimensions):
        raise AutomodelContractError("primary trace has invalid source dimensions")
    if not isinstance(grid, int) or grid <= 0:
        raise AutomodelContractError("primary trace has an invalid sampling grid")
    mask = Image.new("L", (dimensions[0], dimensions[1]), 0)
    for row in rows:
        if not isinstance(row, dict) or not isinstance(row.get("y"), int) or not isinstance(row.get("runs"), list):
            raise AutomodelContractError("primary trace has an invalid row")
        for run in row["runs"]:
            if not isinstance(run, list) or len(run) != 2 or not all(isinstance(value, int) for value in run):
                raise AutomodelContractError("primary trace has an invalid run")
            left, right = run
            if not 0 <= left < right <= mask.width:
                raise AutomodelContractError("primary trace run is outside source bounds")
            for vertical in range(row["y"], row["y"] + grid):
                if not 0 <= vertical < mask.height:
                    continue
                for horizontal in range(left, right):
                    mask.putpixel((horizontal, vertical), 255)
    if mask.getbbox() is None:
        raise AutomodelContractError("primary trace has no foreground")
    return mask


def _components(mask: Image.Image, *, foreground: bool) -> list[tuple[int, tuple[int, int, int, int], bool]]:
    """Return 4-connected regions as ``(area, bbox, touches_frame)``.

    The source-pixel trace is deliberately detailed, so it can contain tiny
    isolated specks from its two-pixel sampling grid.  This helper does not
    make an artistic segmentation decision: it only makes those disconnected
    pixels auditable before the upstream SV3D reframe operation sees them.
    """

    width, height = mask.size
    pixels = mask.load()
    seen = bytearray(width * height)
    result: list[tuple[int, tuple[int, int, int, int], bool]] = []
    for y in range(height):
        for x in range(width):
            index = y * width + x
            is_foreground = pixels[x, y] > 0
            if seen[index] or is_foreground is not foreground:
                continue
            seen[index] = 1
            queue: deque[tuple[int, int]] = deque([(x, y)])
            area = 0
            left = right = x
            top = bottom = y
            touches_frame = x in (0, width - 1) or y in (0, height - 1)
            while queue:
                current_x, current_y = queue.popleft()
                area += 1
                left = min(left, current_x)
                right = max(right, current_x)
                top = min(top, current_y)
                bottom = max(bottom, current_y)
                touches_frame = touches_frame or current_x in (0, width - 1) or current_y in (0, height - 1)
                for next_x, next_y in (
                    (current_x - 1, current_y),
                    (current_x + 1, current_y),
                    (current_x, current_y - 1),
                    (current_x, current_y + 1),
                ):
                    if not (0 <= next_x < width and 0 <= next_y < height):
                        continue
                    next_index = next_y * width + next_x
                    if seen[next_index] or (pixels[next_x, next_y] > 0) is not foreground:
                        continue
                    seen[next_index] = 1
                    queue.append((next_x, next_y))
            result.append((area, (left, top, right + 1, bottom + 1), touches_frame))
    return result


def _isolated_subject_mask(mask: Image.Image) -> tuple[Image.Image, dict[str, int | list[int]]]:
    """Remove only non-subject specks and pin the exact cleanup receipt.

    The threshold is resolution-relative and intentionally conservative.  It
    retains every leg/body island with a material screen-space footprint while
    dropping 2px-grid dust that otherwise changes SV3D's bounding rectangle.
    Small enclosed holes are filled because an alpha silhouette denotes the
    object boundary; meaningful gaps between legs/tendrils remain connected to
    the frame and therefore cannot be filled by this rule.
    """

    width, height = mask.size
    frame_area = width * height
    foreground_components = _components(mask, foreground=True)
    main_component_area = max(area for area, _, _ in foreground_components)
    # Keep limbs that are materially connected to the broad silhouette, but
    # reject loose environmental blobs and single-grid dust.  One percent of
    # the dominant connected component is intentionally a conservative
    # lower limit for this conditioning profile—not a rule about canonical
    # anatomy, which remains the source trace.
    min_component_area = max(2, round(main_component_area * 0.01))
    max_enclosed_hole_area = max(4, round(frame_area * 0.00025))
    cleaned = mask.copy()
    pixels = cleaned.load()
    raw_foreground = sum(value > 0 for value in cleaned.tobytes())
    removed_components = 0
    removed_pixels = 0
    for area, (left, top, right, bottom), _ in foreground_components:
        if area >= min_component_area:
            continue
        removed_components += 1
        removed_pixels += area
        for y in range(top, bottom):
            for x in range(left, right):
                if pixels[x, y] > 0:
                    pixels[x, y] = 0

    filled_holes = 0
    filled_pixels = 0
    for area, (left, top, right, bottom), touches_frame in _components(cleaned, foreground=False):
        if touches_frame or area > max_enclosed_hole_area:
            continue
        filled_holes += 1
        filled_pixels += area
        for y in range(top, bottom):
            for x in range(left, right):
                if pixels[x, y] == 0:
                    pixels[x, y] = 255

    bbox = cleaned.getbbox()
    if bbox is None:
        raise AutomodelContractError("isolated subject cleanup removed all trace pixels")
    return cleaned, {
        "raw_foreground_pixels": raw_foreground,
        "retained_foreground_pixels": sum(value > 0 for value in cleaned.tobytes()),
        "removed_components": removed_components,
        "removed_pixels": removed_pixels,
        "filled_holes": filled_holes,
        "filled_pixels": filled_pixels,
        "min_component_area_pixels": min_component_area,
        "max_enclosed_hole_area_pixels": max_enclosed_hole_area,
        "subject_bbox_xyxy": list(bbox),
    }


def _sv3d_effective_frame(source_rgba: Image.Image, subject_mask: Image.Image) -> Image.Image:
    """Mirror the pinned upstream SV3D RGBA bbox -> white 576² transform.

    This is review evidence only. The actual runner still invokes the pinned
    upstream sampler, which repeats this transform from the hash-pinned RGBA
    input. The preview lets visual review reject a bad cutout before model
    inference consumes GPU time.
    """

    bbox = subject_mask.getbbox()
    if bbox is None:
        raise AutomodelContractError("cannot create SV3D review frame without an isolated subject")
    left, top, right, bottom = bbox
    width = right - left
    height = bottom - top
    side_length = int(max(width, height) / SV3D_FRAME_RATIO)
    if side_length <= 0:
        raise AutomodelContractError("isolated subject has invalid SV3D frame dimensions")
    isolated = source_rgba.convert("RGBA")
    isolated.putalpha(subject_mask)
    framed = Image.new("RGBA", (side_length, side_length), (0, 0, 0, 0))
    framed.alpha_composite(isolated.crop(bbox), ((side_length - width) // 2, (side_length - height) // 2))
    framed = framed.resize((SV3D_FRAME_SIZE, SV3D_FRAME_SIZE), Image.Resampling.LANCZOS)
    white = Image.new("RGBA", framed.size, (255, 255, 255, 255))
    white.alpha_composite(framed)
    return white.convert("RGB")


def _write_isolated_subject_conditioning(
    primary: Image.Image,
    trace_mask: Image.Image,
    input_directory: Path,
    repository_root: Path,
) -> dict[str, Any]:
    """Create the reviewable non-authoritative SV3D conditioning triplet."""

    subject_mask, cleanup = _isolated_subject_mask(trace_mask)
    mask_target = input_directory / "isolated_subject_mask_v1.png"
    conditioning_target = input_directory / "isolated_subject_conditioning_v1.png"
    effective_frame_target = input_directory / "isolated_subject_sv3d_effective_v1.png"
    receipt_target = input_directory / "isolated_subject_conditioning_v1.json"
    for target in (mask_target, conditioning_target, effective_frame_target, receipt_target):
        if target.exists():
            raise AutomodelContractError(f"refusing to overwrite staged isolated conditioning artifact: {target}")
    subject_mask.save(mask_target)
    isolated = primary.convert("RGBA")
    isolated.putalpha(subject_mask)
    isolated.save(conditioning_target)
    _sv3d_effective_frame(primary, subject_mask).save(effective_frame_target)
    receipt = {
        "schema": "pale_mirror.automodel.isolated_subject_conditioning.v1",
        "scope": "research_only_noncanonical",
        "profile": "isolated_subject_v1",
        "input_contract": {
            "primary_trace_remains_sole_likeness_authority": True,
            "derivation": "primary RGB under source-pixel trace alpha; disconnected trace specks removed and tiny enclosed alpha holes filled",
            "upstream_contract": "SV3D receives RGBA, computes its own alpha bbox/reframe, then composites alpha over white",
            "retouching_or_unseen_pixel_synthesis": "forbidden",
        },
        "cleanup": cleanup,
        "artifacts": {
            "mask": {"file": _relative(mask_target, repository_root), "sha256": sha256(mask_target)},
            "conditioning": {"file": _relative(conditioning_target, repository_root), "sha256": sha256(conditioning_target)},
            "effective_sv3d_review_frame": {
                "file": _relative(effective_frame_target, repository_root),
                "sha256": sha256(effective_frame_target),
            },
        },
    }
    write_object(receipt_target, receipt)
    return {
        "id": "isolated_subject_v1",
        "role": "conditioning_only",
        "file": _relative(conditioning_target, repository_root),
        "sha256": sha256(conditioning_target),
        "derives_from": ["primary", "primary_trace"],
        "preparation": {
            "profile": "isolated_subject_v1",
            "mask_file": _relative(mask_target, repository_root),
            "mask_sha256": sha256(mask_target),
            "receipt_file": _relative(receipt_target, repository_root),
            "receipt_sha256": sha256(receipt_target),
            "effective_sv3d_review_file": _relative(effective_frame_target, repository_root),
            "effective_sv3d_review_sha256": sha256(effective_frame_target),
        },
    }


def _write_generated_cutout_conditioning(
    generated_source: Path,
    input_directory: Path,
    repository_root: Path,
) -> dict[str, Any]:
    """Stage one explicitly model-derived transparent SV3D preview input.

    Unlike ``isolated_subject_v1``, this image is not a deterministic trace
    transform. It is a generated background extraction retained solely for a
    visual video preview. The primary and trace still decide likeness, and
    this conditioning is forbidden from all geometry/canonical hand-offs.
    """

    if generated_source.suffix.lower() != ".png":
        raise AutomodelContractError("generated SV3D conditioning must be a transparent PNG")
    with Image.open(generated_source) as source:
        generated_rgba = source.convert("RGBA")
    alpha = generated_rgba.getchannel("A")
    alpha_range = alpha.getextrema()
    bbox = alpha.getbbox()
    if alpha_range is None or alpha_range[0] >= 255 or alpha_range[1] <= 0 or bbox is None:
        raise AutomodelContractError("generated SV3D conditioning must contain both transparent background and opaque subject pixels")

    conditioning_target = input_directory / "generated_cutout_r01.png"
    effective_frame_target = input_directory / "generated_cutout_r01_sv3d_effective.png"
    receipt_target = input_directory / "generated_cutout_r01.json"
    for target in (conditioning_target, effective_frame_target, receipt_target):
        if target.exists():
            raise AutomodelContractError(f"refusing to overwrite staged generated conditioning artifact: {target}")
    _copy_new(generated_source, conditioning_target)
    _sv3d_effective_frame(generated_rgba, alpha).save(effective_frame_target)
    receipt = {
        "schema": "pale_mirror.automodel.generated_cutout_conditioning.v1",
        "scope": "research_only_noncanonical",
        "profile": "generated_cutout_r01",
        "tier": EvidenceTier.MODEL_DERIVED.value,
        "input_contract": {
            "primary_trace_remains_sole_likeness_authority": True,
            "derivation": "model-generated background extraction conditioned on the literal primary; alpha restored from its baked checkerboard preview",
            "geometry_or_canonical_use_prohibited": True,
            "permitted_use": "SV3D visual-preview conditioning only",
        },
        "source": {
            # The selected source may be outside the repository before its
            # immutable staged copy exists. Keep a filename/hash receipt, not
            # an arbitrary host path.
            "source_filename": generated_source.name,
            "source_sha256": sha256(generated_source),
            "image_size": list(generated_rgba.size),
            "alpha_range": list(alpha_range),
            "subject_bbox_xyxy": list(bbox),
        },
        "artifacts": {
            "conditioning": {"file": _relative(conditioning_target, repository_root), "sha256": sha256(conditioning_target)},
            "effective_sv3d_review_frame": {
                "file": _relative(effective_frame_target, repository_root),
                "sha256": sha256(effective_frame_target),
            },
        },
    }
    write_object(receipt_target, receipt)
    return {
        "id": "generated_cutout_r01",
        "role": "conditioning_only",
        "tier": EvidenceTier.MODEL_DERIVED.value,
        "file": _relative(conditioning_target, repository_root),
        "sha256": sha256(conditioning_target),
        "derives_from": ["primary"],
        "preparation": {
            "profile": "generated_cutout_r01",
            "receipt_file": _relative(receipt_target, repository_root),
            "receipt_sha256": sha256(receipt_target),
            "effective_sv3d_review_file": _relative(effective_frame_target, repository_root),
            "effective_sv3d_review_sha256": sha256(effective_frame_target),
            "geometry_or_canonical_use_prohibited": True,
        },
    }


def _edit360_front_conditioning(
    isolated_subject: dict[str, Any],
    repository_root: Path,
) -> dict[str, Any]:
    """Expose the reviewed white frame as a separately pinned Edit360 input.

    Edit360's upstream code discards PNG alpha with ``convert('RGB')``.  The
    alpha-bearing SV3D input would therefore become black-background input;
    this model-specific role deliberately points at the already-pinned white
    frame generated from the same literal source and trace instead.
    """

    preparation = isolated_subject["preparation"]
    return {
        "id": "edit360_front_white_r01",
        "role": "conditioning_only",
        "file": preparation["effective_sv3d_review_file"],
        "sha256": preparation["effective_sv3d_review_sha256"],
        "derives_from": ["primary", "primary_trace"],
        "preparation": {
            "profile": "edit360_front_white_r01",
            "source_conditioning_receipt_file": preparation["receipt_file"],
            "source_conditioning_receipt_sha256": preparation["receipt_sha256"],
            "geometry_or_canonical_use_prohibited": True,
        },
    }


def _edit360_generated_front_conditioning(
    generated_cutout: dict[str, Any],
    repository_root: Path,
) -> dict[str, Any]:
    """Expose a separately reviewed clean generated cutout to Edit360 only.

    Unlike ``isolated_subject_v1``, this path never runs the source trace mask
    over a textured creature.  It is deliberately MODEL_DERIVED and remains
    visual-preview-only; the caller must still create a visual-input review
    receipt before the typed Windows runner accepts it.
    """

    preparation = generated_cutout["preparation"]
    return {
        "id": "edit360_generated_front_white_r01",
        "role": "conditioning_only",
        "tier": EvidenceTier.MODEL_DERIVED.value,
        "file": preparation["effective_sv3d_review_file"],
        "sha256": preparation["effective_sv3d_review_sha256"],
        "derives_from": ["primary"],
        "preparation": {
            "profile": "edit360_generated_front_white_r01",
            "source_conditioning_receipt_file": preparation["receipt_file"],
            "source_conditioning_receipt_sha256": preparation["receipt_sha256"],
            "visual_review_required_before_execution": True,
            "geometry_or_canonical_use_prohibited": True,
        },
    }


def _write_edit360_opposite_anchor(
    generated_source: Path,
    input_directory: Path,
    repository_root: Path,
) -> dict[str, Any]:
    """Stage one clean model-derived 180° anchor for the dual-view adapter."""

    if generated_source.suffix.lower() != ".png":
        raise AutomodelContractError("Edit360 opposite anchor must be a transparent PNG")
    with Image.open(generated_source) as source:
        generated_rgba = source.convert("RGBA")
    alpha = generated_rgba.getchannel("A")
    alpha_range = alpha.getextrema()
    bbox = alpha.getbbox()
    if alpha_range is None or alpha_range[0] >= 255 or alpha_range[1] <= 0 or bbox is None:
        raise AutomodelContractError("Edit360 opposite anchor must contain transparent background and opaque subject pixels")
    alpha_target = input_directory / "edit360_opposite_broadside_r01_alpha.png"
    effective_target = input_directory / "edit360_opposite_broadside_r01_effective_576.png"
    receipt_target = input_directory / "edit360_opposite_broadside_r01.json"
    for target in (alpha_target, effective_target, receipt_target):
        if target.exists():
            raise AutomodelContractError(f"refusing to overwrite staged Edit360 anchor artifact: {target}")
    _copy_new(generated_source, alpha_target)
    _sv3d_effective_frame(generated_rgba, alpha).save(effective_target)
    receipt = {
        "schema": "pale_mirror.automodel.edit360_opposite_anchor.v1",
        "scope": "research_only_noncanonical",
        "profile": "edit360_opposite_broadside_r01",
        "tier": EvidenceTier.MODEL_DERIVED.value,
        "input_contract": {
            "primary_trace_remains_sole_likeness_authority": True,
            "declared_yaw_degrees": 180,
            "permitted_use": "Edit360 dual-view visual experiment only",
            "geometry_or_canonical_use_prohibited": True,
        },
        "source": {
            "source_filename": generated_source.name,
            "source_sha256": sha256(generated_source),
            "image_size": list(generated_rgba.size),
            "alpha_range": list(alpha_range),
            "subject_bbox_xyxy": list(bbox),
        },
        "artifacts": {
            "alpha_source": {"file": _relative(alpha_target, repository_root), "sha256": sha256(alpha_target)},
            "effective_edit360_frame": {"file": _relative(effective_target, repository_root), "sha256": sha256(effective_target)},
        },
    }
    write_object(receipt_target, receipt)
    return {
        "id": "edit360_opposite_broadside_r01",
        "role": "dual_view_anchor",
        "tier": EvidenceTier.MODEL_DERIVED.value,
        "file": _relative(effective_target, repository_root),
        "sha256": sha256(effective_target),
        "derives_from": ["primary"],
        "declared_yaw_degrees": 180,
        "preparation": {
            "profile": "edit360_opposite_broadside_r01",
            "alpha_source_file": _relative(alpha_target, repository_root),
            "alpha_source_sha256": sha256(alpha_target),
            "receipt_file": _relative(receipt_target, repository_root),
            "receipt_sha256": sha256(receipt_target),
            "geometry_or_canonical_use_prohibited": True,
        },
    }


def stage_reference_bundle(
    asset_id: str,
    primary_source: Path,
    trace_source: Path,
    run_directory: Path,
    *,
    isolated_subject_conditioning: bool,
    generated_conditioning_source: Path | None = None,
    edit360_opposite_anchor_source: Path | None = None,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    """Create a new staged ReferenceBundle below ``build/automodel``."""

    require_identifier(asset_id, "asset_id")
    if primary_source.suffix.lower() not in IMAGE_SUFFIXES:
        raise AutomodelContractError("literal primary must be an image")
    if trace_source.suffix.lower() != ".json":
        raise AutomodelContractError("primary trace must be JSON")
    run_directory = run_directory.resolve()
    _relative(run_directory, repository_root)
    input_directory = run_directory / "input"
    primary_target = input_directory / f"literal_primary{primary_source.suffix.lower()}"
    trace_target = input_directory / "primary_trace.json"
    _copy_new(primary_source, primary_target)
    _copy_new(trace_source, trace_target)
    if isolated_subject_conditioning and generated_conditioning_source is not None:
        raise AutomodelContractError("reference bundle may stage exactly one conditioning profile")
    if edit360_opposite_anchor_source is not None and not (
        isolated_subject_conditioning or generated_conditioning_source is not None
    ):
        raise AutomodelContractError("Edit360 opposite anchor requires one explicit staged front conditioning input")
    model_inputs: list[dict[str, Any]] = []
    isolated_subject: dict[str, Any] | None = None
    if isolated_subject_conditioning:
        import json

        try:
            trace = json.loads(trace_target.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise AutomodelContractError(f"cannot read staged primary trace: {exc}") from exc
        if not isinstance(trace, dict):
            raise AutomodelContractError("primary trace must contain an object")
        with Image.open(primary_target) as source:
            primary = source.convert("RGBA")
        mask = _trace_mask(trace)
        if primary.size != mask.size:
            raise AutomodelContractError("literal primary size does not match the primary trace source size")
        isolated_subject = _write_isolated_subject_conditioning(primary, mask, input_directory, repository_root)
        model_inputs.append(isolated_subject)
    generated_cutout: dict[str, Any] | None = None
    if generated_conditioning_source is not None:
        generated_cutout = _write_generated_cutout_conditioning(generated_conditioning_source, input_directory, repository_root)
        model_inputs.append(generated_cutout)
    anchors: list[dict[str, Any]] = []
    if edit360_opposite_anchor_source is not None:
        if isolated_subject is not None:
            model_inputs.append(_edit360_front_conditioning(isolated_subject, repository_root))
        elif generated_cutout is not None:
            model_inputs.append(_edit360_generated_front_conditioning(generated_cutout, repository_root))
        else:
            raise AssertionError("validated Edit360 front conditioning is unexpectedly absent")
        anchors.append(_write_edit360_opposite_anchor(edit360_opposite_anchor_source, input_directory, repository_root))
    bundle = {
        "schema": "pale_mirror.automodel.reference_bundle.v1",
        "asset_id": asset_id,
        "scope": "research_only_noncanonical",
        "primary": {
            "role": "sole_likeness_anchor",
            "tier": EvidenceTier.PRIMARY_TRACE.value,
            "file": _relative(primary_target, repository_root),
            "sha256": sha256(primary_target),
        },
        "primary_trace": {"file": _relative(trace_target, repository_root), "sha256": sha256(trace_target)},
        "anchors": anchors,
        "model_inputs": model_inputs,
    }
    validate_reference_bundle(bundle)
    bundle_path = run_directory / "reference_bundle.json"
    if bundle_path.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable reference bundle: {bundle_path}")
    write_object(bundle_path, bundle)
    return bundle


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--asset-id", required=True)
    parser.add_argument("--primary", type=Path, required=True)
    parser.add_argument("--primary-trace", type=Path, required=True)
    parser.add_argument("--run-directory", type=Path, required=True)
    parser.add_argument(
        "--isolated-subject-conditioning",
        action="store_true",
        help=(
            "Create the isolated_subject_v1 RGBA conditioning input from the literal primary and its trace. "
            "The legacy raw trace-mask raster is intentionally no longer emitted."
        ),
    )
    parser.add_argument(
        "--edit360-opposite-anchor",
        type=Path,
        help=(
            "Stage one prepared transparent MODEL_DERIVED 180-degree broadside anchor for the Edit360 dual-view visual experiment. "
            "Requires exactly one front conditioning input and is prohibited from geometry/canonical use."
        ),
    )
    parser.add_argument(
        "--generated-conditioning",
        type=Path,
        help=(
            "Stage the explicitly MODEL_DERIVED generated_cutout_r01 transparent PNG for a visual preview only. "
            "It cannot be combined with --isolated-subject-conditioning or used for geometry/canonical work."
        ),
    )
    arguments = parser.parse_args()
    import json

    print(
        json.dumps(
            stage_reference_bundle(
                arguments.asset_id,
                arguments.primary,
                arguments.primary_trace,
                arguments.run_directory,
                isolated_subject_conditioning=arguments.isolated_subject_conditioning,
                generated_conditioning_source=arguments.generated_conditioning,
                edit360_opposite_anchor_source=arguments.edit360_opposite_anchor,
            ),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
