#!/usr/bin/env python3
"""Evidence-bounded 8-view still-ring workflow for Automodel research.

This module deliberately stages *external* ImageGen stills rather than
executing an image model.  It records their provenance, rejects technically
unsafe cut-outs, and makes a complete user review receipt the only route into
pose/depth work.  It never reads or writes a canonical Blender source, PMMesh,
runtime resource, server, or world path.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import shutil
from typing import Any, Iterable

from PIL import Image, ImageDraw

from contracts import (
    AutomodelContractError,
    EvidenceTier,
    load_object,
    require_identifier,
    require_relative_build_path,
    require_sha256,
    sha256,
    validate_reference_bundle,
    write_object,
)


ROOT = Path(__file__).resolve().parents[2]
RING_YAWS = tuple(range(0, 360, 45))
REQUIRED_AFFIRMATIONS = frozenset(
    {
        "anatomy_cardinality",
        "ring_continuity",
        "transparent_isolation",
        "hidden_side_hypothesis",
    }
)


def _relative(path: Path, repository_root: Path, label: str) -> str:
    try:
        value = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"{label} must stay inside this repository") from exc
    return require_relative_build_path(value, label)


def _new_path(path: Path, repository_root: Path, label: str) -> str:
    relative = _relative(path, repository_root, label)
    if path.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable {label}: {path}")
    return relative


def _require_file(record: dict[str, Any], repository_root: Path, label: str) -> Path:
    relative = require_relative_build_path(record.get("file"), f"{label}.file")
    require_sha256(record.get("sha256"), f"{label}.sha256")
    path = repository_root / relative
    if not path.is_file() or sha256(path) != record["sha256"]:
        raise AutomodelContractError(f"{label} is missing or changed: {relative}")
    return path


def _require_exact_keys(value: Any, expected: Iterable[int], label: str) -> None:
    if not isinstance(value, dict):
        raise AutomodelContractError(f"{label} must be an object keyed by yaw")
    expected_keys = {str(item) for item in expected}
    if set(value) != expected_keys:
        raise AutomodelContractError(f"{label} must declare exactly {sorted(expected_keys)}")


def validate_anatomy_sheet(value: dict[str, Any]) -> None:
    if value.get("schema") != "pale_mirror.automodel.anatomy_sheet.v1":
        raise AutomodelContractError("anatomy sheet has an unsupported schema")
    require_identifier(value.get("asset_id"), "anatomy_sheet.asset_id")
    if value.get("scope") != "research_only_noncanonical":
        raise AutomodelContractError("anatomy sheet must remain research_only_noncanonical")
    if value.get("primary_authority") != "PRIMARY_TRACE":
        raise AutomodelContractError("anatomy sheet must preserve PRIMARY_TRACE authority")
    ring = value.get("camera_ring")
    if not isinstance(ring, dict) or ring.get("reference_yaw_degrees") != 0 or ring.get("yaws") != list(RING_YAWS):
        raise AutomodelContractError("anatomy sheet must declare the fixed 0..315 degree ring")
    if ring.get("primary_view") != "low_front_left_three_quarter":
        raise AutomodelContractError("anatomy sheet must name the locked Collector primary view")
    if ring.get("background") != "transparent_rgba" or ring.get("output_size_px") != [1024, 1024]:
        raise AutomodelContractError("anatomy sheet requires 1024px transparent RGBA stills")
    parts = value.get("parts")
    if not isinstance(parts, list):
        raise AutomodelContractError("anatomy sheet requires anatomical parts")
    expected = {
        "body_core": 1,
        "dorsal_sac": 6,
        "front_mantle": 1,
        "leg_pair": 5,
        "tail": 1,
    }
    observed: dict[str, int] = {}
    for index, part in enumerate(parts):
        if not isinstance(part, dict):
            raise AutomodelContractError(f"parts[{index}] must be an object")
        part_id = require_identifier(part.get("id"), f"parts[{index}].id")
        instances = part.get("instances")
        if not isinstance(instances, int) or instances < 1:
            raise AutomodelContractError(f"parts[{index}].instances must be positive")
        observed[part_id] = instances
        if part_id == "leg_pair":
            if part.get("bilateral_members") != 2 or part.get("preserve_pairing") is not True:
                raise AutomodelContractError("leg pairs must retain two bilateral members")
    if observed != expected:
        raise AutomodelContractError("anatomy sheet must pin Collector body, six sacs, five leg pairs, mantle and tail")
    _require_exact_keys(value.get("view_expectations"), RING_YAWS, "view_expectations")
    for yaw in RING_YAWS:
        expectation = value["view_expectations"][str(yaw)]
        if not isinstance(expectation, dict):
            raise AutomodelContractError(f"view expectation {yaw} must be an object")
        if expectation.get("required_part_ids") != list(expected):
            raise AutomodelContractError(f"view expectation {yaw} must list all required anatomy")
        if expectation.get("leg_pair_count") != 5 or expectation.get("dorsal_sac_count") != 6:
            raise AutomodelContractError(f"view expectation {yaw} must preserve five leg pairs and six sacs")


def validate_candidate_still(value: dict[str, Any]) -> None:
    if value.get("schema") != "pale_mirror.automodel.candidate_still.v1":
        raise AutomodelContractError("candidate still has an unsupported schema")
    require_identifier(value.get("asset_id"), "candidate_still.asset_id")
    require_identifier(value.get("candidate_id"), "candidate_still.candidate_id")
    if value.get("scope") != "research_only_noncanonical" or value.get("tier") != EvidenceTier.MODEL_DERIVED.value:
        raise AutomodelContractError("candidate still must remain MODEL_DERIVED research")
    yaw = value.get("yaw_degrees")
    if yaw not in RING_YAWS[1:]:
        raise AutomodelContractError("candidate still yaw must be a non-primary ring position")
    if value.get("provider") != "builtin_imagegen_staged":
        raise AutomodelContractError("candidate still must be staged from built-in ImageGen")
    image = value.get("image")
    prompt = value.get("prompt_record")
    if not isinstance(image, dict) or not isinstance(prompt, dict):
        raise AutomodelContractError("candidate still requires image and prompt records")
    if image.get("mode") != "RGBA" or image.get("size_px") != [1024, 1024]:
        raise AutomodelContractError("candidate still must be an RGBA 1024px image")
    _ = image.get("lint")
    if not isinstance(_, dict) or _.get("status") != "passed":
        raise AutomodelContractError("candidate still must pass technical alpha lint")
    if not isinstance(prompt.get("text_sha256"), str):
        raise AutomodelContractError("candidate still must pin its prompt text hash")
    imported = value.get("external_import")
    if not isinstance(imported, dict):
        raise AutomodelContractError("candidate still must pin the deterministic external-still import")
    require_relative_build_path(imported.get("file"), "candidate_still.external_import.file")
    require_sha256(imported.get("sha256"), "candidate_still.external_import.sha256")


def validate_provisional_ring(value: dict[str, Any]) -> None:
    if value.get("schema") != "pale_mirror.automodel.provisional_view_ring.v1":
        raise AutomodelContractError("provisional ring has an unsupported schema")
    if value.get("scope") != "research_only_noncanonical" or value.get("promotion_prohibited") is not True:
        raise AutomodelContractError("provisional ring must stay noncanonical and promotion-prohibited")
    require_identifier(value.get("asset_id"), "provisional_ring.asset_id")
    frames = value.get("frames")
    if not isinstance(frames, list) or len(frames) != len(RING_YAWS):
        raise AutomodelContractError("provisional ring requires exactly eight frames")
    yaws = [frame.get("yaw_degrees") if isinstance(frame, dict) else None for frame in frames]
    if yaws != list(RING_YAWS):
        raise AutomodelContractError("provisional ring frames must be ordered 0..315 in 45 degree steps")
    primary, *secondary = frames
    if primary.get("tier") != EvidenceTier.PRIMARY_TRACE.value or primary.get("role") != "sole_likeness_anchor":
        raise AutomodelContractError("provisional ring 0 degree frame must remain the literal primary")
    for index, frame in enumerate(secondary, start=1):
        if frame.get("tier") != EvidenceTier.MODEL_DERIVED.value or frame.get("role") != "provisional_secondary":
            raise AutomodelContractError(f"provisional ring frame {index} must remain unreviewed")
        if not isinstance(frame.get("candidate"), dict):
            raise AutomodelContractError(f"provisional ring frame {index} requires its staged candidate")


def validate_reviewed_view_set(value: dict[str, Any]) -> None:
    if value.get("schema") != "pale_mirror.automodel.reviewed_view_set.v1":
        raise AutomodelContractError("reviewed view set has an unsupported schema")
    if value.get("scope") != "research_only_noncanonical" or value.get("promotion_prohibited") is not True:
        raise AutomodelContractError("reviewed view set must remain noncanonical")
    validate_provisional_ring(
        {
            "schema": "pale_mirror.automodel.provisional_view_ring.v1",
            "scope": value.get("scope"),
            "promotion_prohibited": value.get("promotion_prohibited"),
            "asset_id": value.get("asset_id"),
            "frames": [
                frame if index == 0 else {**frame, "tier": EvidenceTier.MODEL_DERIVED.value, "role": "provisional_secondary"}
                for index, frame in enumerate(value.get("frames", []))
            ],
        }
    )
    frames = value["frames"]
    for frame in frames[1:]:
        if frame.get("tier") != EvidenceTier.REVIEWED_SECONDARY.value or frame.get("review", {}).get("status") != "accepted_in_complete_ring":
            raise AutomodelContractError("reviewed secondary frames require complete-ring human acceptance")
    receipt = value.get("ring_review")
    if not isinstance(receipt, dict) or receipt.get("decision") != "accepted_complete_ring" or not isinstance(receipt.get("reviewer"), str):
        raise AutomodelContractError("reviewed view set requires its ring review receipt")


def validate_geometry_input(value: dict[str, Any]) -> None:
    if value.get("schema") != "pale_mirror.automodel.geometry_input.v1":
        raise AutomodelContractError("geometry input has an unsupported schema")
    if value.get("scope") != "research_only_noncanonical" or value.get("promotion_prohibited") is not True:
        raise AutomodelContractError("geometry input must remain noncanonical")
    require_identifier(value.get("asset_id"), "geometry_input.asset_id")
    reviewed = value.get("reviewed_view_set")
    if not isinstance(reviewed, dict):
        raise AutomodelContractError("geometry input requires a reviewed view set record")
    frames = value.get("frames")
    if not isinstance(frames, list) or len(frames) != len(RING_YAWS):
        raise AutomodelContractError("geometry input requires the complete eight-frame ring")
    if [frame.get("yaw_degrees") if isinstance(frame, dict) else None for frame in frames] != list(RING_YAWS):
        raise AutomodelContractError("geometry input frames must retain fixed yaw order")
    if frames[0].get("tier") != EvidenceTier.PRIMARY_TRACE.value or any(frame.get("tier") != EvidenceTier.REVIEWED_SECONDARY.value for frame in frames[1:]):
        raise AutomodelContractError("geometry input requires literal primary plus user-reviewed secondary views")
    contract = value.get("contract")
    if not isinstance(contract, dict) or contract.get("permitted_adapters") != ["da3_base", "vggt_official"]:
        raise AutomodelContractError("geometry input must be limited to DA3 and VGGT diagnostics")


def _alpha_components(alpha: Image.Image) -> tuple[int, int, int]:
    # This is intentionally a coarse lint, not segmentation.  It must reject
    # detached blobs without making a 1024² staged still consume hundreds of
    # MiB through a Python set of every visible source pixel.
    coarse = alpha.resize((128, 128), Image.Resampling.BOX)
    width, height = coarse.size
    pixels = coarse.load()
    seen: set[tuple[int, int]] = set()
    components: list[int] = []
    for y in range(height):
        for x in range(width):
            if (x, y) in seen or pixels[x, y] < 16:
                continue
            stack = [(x, y)]
            seen.add((x, y))
            area = 0
            while stack:
                current_x, current_y = stack.pop()
                area += 1
                for next_x, next_y in ((current_x - 1, current_y), (current_x + 1, current_y), (current_x, current_y - 1), (current_x, current_y + 1)):
                    if 0 <= next_x < width and 0 <= next_y < height and (next_x, next_y) not in seen and pixels[next_x, next_y] >= 16:
                        seen.add((next_x, next_y))
                        stack.append((next_x, next_y))
            components.append(area)
    components.sort(reverse=True)
    total = sum(components)
    return len(components), components[0] if components else 0, total


def lint_rgba_still(source: Path) -> dict[str, Any]:
    with Image.open(source) as raw:
        if raw.mode != "RGBA" or raw.size != (1024, 1024):
            raise AutomodelContractError("staged ImageGen still must be a 1024x1024 RGBA PNG")
        alpha = raw.getchannel("A")
    bbox = alpha.getbbox()
    if bbox is None:
        raise AutomodelContractError("staged ImageGen still has no visible subject")
    left, top, right, bottom = bbox
    padding = min(left, top, 1024 - right, 1024 - bottom)
    if padding < 24:
        raise AutomodelContractError("staged ImageGen still clips or nearly clips the subject at the frame edge")
    component_count, largest, total = _alpha_components(alpha)
    if total < 1024 or largest / total < 0.995:
        raise AutomodelContractError("staged ImageGen still has detached alpha debris or a disconnected subject")
    return {
        "status": "passed",
        "subject_bbox_px": [left, top, right, bottom],
        "minimum_padding_px": padding,
        "alpha_component_count": component_count,
        "largest_component_fraction": round(largest / total, 6),
        "manual_checks_remaining": ["anatomy", "background_semantics", "adjacent_view_continuity"],
    }


def _source_alpha_lint(source: Path) -> dict[str, Any]:
    """Reject a bad external asset before any resize/canvas normalization.

    The transform below may standardize ImageGen's square output dimensions,
    but it must never conceal an already-cropped subject or disconnected
    debris.  This maintains an inspectable raw-to-prepared chain.
    """

    with Image.open(source) as raw:
        if raw.mode != "RGBA" or raw.width < 128 or raw.height < 128:
            raise AutomodelContractError("external ImageGen still must be a sufficiently large RGBA PNG")
        alpha = raw.getchannel("A")
        size = raw.size
    bbox = alpha.getbbox()
    if bbox is None:
        raise AutomodelContractError("external ImageGen still has no visible subject")
    left, top, right, bottom = bbox
    padding = min(left, top, size[0] - right, size[1] - bottom)
    if padding < max(16, min(size) // 50):
        raise AutomodelContractError("external ImageGen still clips or nearly clips the subject before normalization")
    component_count, largest, total = _alpha_components(alpha)
    if total < 64 or largest / total < 0.995:
        raise AutomodelContractError("external ImageGen still has detached alpha debris before normalization")
    return {
        "status": "passed",
        "raw_size_px": list(size),
        "subject_bbox_px": [left, top, right, bottom],
        "minimum_padding_px": padding,
        "alpha_component_count": component_count,
        "largest_component_fraction": round(largest / total, 6),
    }


def prepare_external_still(
    source_image: Path,
    output_directory: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    """Copy an external still verbatim and create a deterministic 1024px canvas.

    Built-in ImageGen commonly supplies a square size other than 1024px.  This
    operation is deliberately mechanical: it checks the original alpha first,
    crops to its visible subject and uniformly scales it into a 64px-padded
    transparent 1024px canvas.  It does not generate, erase, inpaint or infer
    pixels, and cannot repair clipping/debris in the raw asset.
    """

    if not source_image.is_file():
        raise AutomodelContractError("external ImageGen source still is missing")
    source_lint = _source_alpha_lint(source_image)
    _new_path(output_directory, repository_root, "external_still_import_directory")
    output_directory.mkdir(parents=True)
    raw_target = output_directory / "raw.png"
    prepared_target = output_directory / "still.png"
    shutil.copyfile(source_image, raw_target)
    with Image.open(raw_target) as raw:
        image = raw.convert("RGBA")
    alpha = image.getchannel("A")
    left, top, right, bottom = alpha.getbbox()  # guaranteed by _source_alpha_lint
    subject = image.crop((left, top, right, bottom))
    scale = min((1024 - 128) / subject.width, (1024 - 128) / subject.height)
    width, height = max(1, round(subject.width * scale)), max(1, round(subject.height * scale))
    fitted = subject.resize((width, height), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    canvas.alpha_composite(fitted, ((1024 - width) // 2, (1024 - height) // 2))
    canvas.save(prepared_target)
    prepared_lint = lint_rgba_still(prepared_target)
    manifest = {
        "schema": "pale_mirror.automodel.external_still_import.v1",
        "scope": "research_only_noncanonical",
        "tier": EvidenceTier.MODEL_DERIVED.value,
        "provider": "builtin_imagegen_staged",
        "raw": {"file": _relative(raw_target, repository_root, "external_raw"), "sha256": sha256(raw_target), "alpha_lint": source_lint},
        "prepared": {"file": _relative(prepared_target, repository_root, "external_prepared"), "sha256": sha256(prepared_target), "transform": {"kind": "crop_subject_uniform_scale_centered_canvas", "output_size_px": [1024, 1024], "padding_px": 64}, "lint": prepared_lint},
        "contract": {"raw_pixels_are_preserved_except_for_crop_uniform_scale_and_transparent_canvas": True, "does_not_inpaint_or_repair_raw_clipping_or_debris": True, "canonical_use_forbidden": True},
    }
    write_object(output_directory / "external_still_import.json", manifest)
    return manifest


def _require_external_import(path: Path, source_image: Path, repository_root: Path) -> dict[str, Any]:
    imported = load_object(path)
    if imported.get("schema") != "pale_mirror.automodel.external_still_import.v1" or imported.get("tier") != EvidenceTier.MODEL_DERIVED.value:
        raise AutomodelContractError("candidate requires a MODEL_DERIVED external still import")
    prepared = imported.get("prepared")
    if not isinstance(prepared, dict):
        raise AutomodelContractError("external still import lacks prepared output")
    prepared_path = _require_file(prepared, repository_root, "external_still_import.prepared")
    if prepared_path.resolve() != source_image.resolve():
        raise AutomodelContractError("candidate source image must be exactly the prepared external import")
    lint_rgba_still(prepared_path)
    return imported


def stage_candidate(
    anatomy_sheet_path: Path,
    run_directory: Path,
    yaw: int,
    candidate_id: str,
    source_image: Path,
    prompt_record: Path,
    external_import_path: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    anatomy = load_object(anatomy_sheet_path)
    validate_anatomy_sheet(anatomy)
    if yaw not in RING_YAWS[1:]:
        raise AutomodelContractError("only non-primary 45 degree ring positions may be staged")
    candidate_id = require_identifier(candidate_id, "candidate_id")
    imported = _require_external_import(external_import_path, source_image, repository_root)
    prompt = load_object(prompt_record)
    if prompt.get("schema") != "pale_mirror.automodel.imagegen_prompt_record.v1" or prompt.get("asset_id") != anatomy["asset_id"]:
        raise AutomodelContractError("candidate requires a matching ImageGen prompt record")
    if prompt.get("yaw_degrees") != yaw or not isinstance(prompt.get("text"), str) or not prompt["text"].strip():
        raise AutomodelContractError("prompt record must pin nonempty text for this exact yaw")
    lint = lint_rgba_still(source_image)
    target_directory = run_directory / "candidates" / f"yaw_{yaw:03d}" / candidate_id
    target_image = target_directory / "still.png"
    target_manifest = target_directory / "candidate_still.json"
    _new_path(target_manifest, repository_root, "candidate_still_manifest")
    target_directory.mkdir(parents=True)
    shutil.copyfile(source_image, target_image)
    prompt_copy = target_directory / "prompt_record.json"
    shutil.copyfile(prompt_record, prompt_copy)
    candidate = {
        "schema": "pale_mirror.automodel.candidate_still.v1",
        "asset_id": anatomy["asset_id"],
        "scope": "research_only_noncanonical",
        "tier": EvidenceTier.MODEL_DERIVED.value,
        "provider": "builtin_imagegen_staged",
        "candidate_id": candidate_id,
        "yaw_degrees": yaw,
        "image": {
            "file": _relative(target_image, repository_root, "candidate_image"),
            "sha256": sha256(target_image),
            "mode": "RGBA",
            "size_px": [1024, 1024],
            "lint": lint,
        },
        "prompt_record": {
            "file": _relative(prompt_copy, repository_root, "candidate_prompt_record"),
            "sha256": sha256(prompt_copy),
            "text_sha256": hashlib.sha256(prompt["text"].encode("utf-8")).hexdigest(),
        },
        "external_import": {"file": _relative(external_import_path, repository_root, "external_import"), "sha256": sha256(external_import_path)},
        "contract": {
            "primary_trace_remains_sole_likeness_authority": True,
            "geometry_requires_complete_user_ring_review": True,
            "canonical_blender_pmmesh_runtime_server_world_access_forbidden": True,
        },
    }
    validate_candidate_still(candidate)
    write_object(target_manifest, candidate)
    return candidate


def _candidate_record(path: Path, anatomy: dict[str, Any], yaw: int, repository_root: Path) -> dict[str, Any]:
    candidate = load_object(path)
    validate_candidate_still(candidate)
    if candidate["asset_id"] != anatomy["asset_id"] or candidate["yaw_degrees"] != yaw:
        raise AutomodelContractError(f"selected candidate does not match yaw {yaw}")
    image_path = _require_file(candidate["image"], repository_root, f"candidate[{yaw}].image")
    _require_file(candidate["prompt_record"], repository_root, f"candidate[{yaw}].prompt_record")
    _require_file(candidate["external_import"], repository_root, f"candidate[{yaw}].external_import")
    return {
        "candidate_manifest": {"file": _relative(path, repository_root, f"candidate[{yaw}].manifest"), "sha256": sha256(path)},
        "image": {"file": _relative(image_path, repository_root, f"candidate[{yaw}].image"), "sha256": sha256(image_path)},
        "candidate_id": candidate["candidate_id"],
    }


def assemble_provisional_ring(
    anatomy_sheet_path: Path,
    reference_bundle_path: Path,
    selections: dict[int, Path],
    output_path: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    anatomy = load_object(anatomy_sheet_path)
    validate_anatomy_sheet(anatomy)
    bundle = load_object(reference_bundle_path)
    validate_reference_bundle(bundle)
    if bundle["asset_id"] != anatomy["asset_id"]:
        raise AutomodelContractError("reference bundle and anatomy sheet must describe one asset")
    if set(selections) != set(RING_YAWS[1:]):
        raise AutomodelContractError("provisional ring requires one selected candidate for each non-primary yaw")
    primary_path = _require_file(bundle["primary"], repository_root, "reference_bundle.primary")
    frames: list[dict[str, Any]] = [
        {
            "id": "primary_000",
            "yaw_degrees": 0,
            "tier": EvidenceTier.PRIMARY_TRACE.value,
            "role": "sole_likeness_anchor",
            "file": _relative(primary_path, repository_root, "provisional_primary"),
            "sha256": sha256(primary_path),
        }
    ]
    for yaw in RING_YAWS[1:]:
        candidate = _candidate_record(selections[yaw], anatomy, yaw, repository_root)
        frames.append(
            {
                "id": f"secondary_{yaw:03d}",
                "yaw_degrees": yaw,
                "tier": EvidenceTier.MODEL_DERIVED.value,
                "role": "provisional_secondary",
                "file": candidate["image"]["file"],
                "sha256": candidate["image"]["sha256"],
                "candidate": candidate,
            }
        )
    ring = {
        "schema": "pale_mirror.automodel.provisional_view_ring.v1",
        "asset_id": anatomy["asset_id"],
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "anatomy_sheet": {"file": _relative(anatomy_sheet_path, repository_root, "anatomy_sheet"), "sha256": sha256(anatomy_sheet_path)},
        "reference_bundle": {"file": _relative(reference_bundle_path, repository_root, "reference_bundle"), "sha256": sha256(reference_bundle_path)},
        "frames": frames,
        "contract": {
            "primary_trace_remains_sole_likeness_authority": True,
            "all_secondary_views_are_unreviewed_model_derived": True,
            "only_complete_user_review_can_promote_secondary_views": True,
            "blender_pmmesh_runtime_server_world_access_forbidden": True,
        },
    }
    validate_provisional_ring(ring)
    _new_path(output_path, repository_root, "provisional_ring")
    write_object(output_path, ring)
    return ring


def _copy_frames(ring: dict[str, Any], directory: Path, repository_root: Path) -> list[dict[str, Any]]:
    public = directory / "public"
    public.mkdir(parents=True)
    copied = []
    for frame in ring["frames"]:
        source = _require_file(frame, repository_root, f"ring.{frame['id']}")
        target = public / f"{frame['yaw_degrees']:03d}.png"
        shutil.copyfile(source, target)
        copied.append({"id": frame["id"], "yaw_degrees": frame["yaw_degrees"], "file": str(target.relative_to(directory)), "sha256": sha256(target)})
    return copied


def _write_contact_sheets(frames: list[dict[str, Any]], directory: Path) -> tuple[Path, Path]:
    cell = 256
    ring_sheet = Image.new("RGBA", (cell * 4, cell * 2), (17, 20, 25, 255))
    pair_sheet = Image.new("RGBA", (cell * 2, cell * 4), (17, 20, 25, 255))
    draw_ring = ImageDraw.Draw(ring_sheet)
    draw_pairs = ImageDraw.Draw(pair_sheet)
    for index, frame in enumerate(frames):
        image = Image.open(directory / frame["file"]).convert("RGBA")
        image.thumbnail((cell - 12, cell - 32), Image.Resampling.LANCZOS)
        x, y = (index % 4) * cell, (index // 4) * cell
        ring_sheet.alpha_composite(image, (x + (cell - image.width) // 2, y + 26 + (cell - 28 - image.height) // 2))
        draw_ring.text((x + 8, y + 8), f"{frame['yaw_degrees']:03d}°", fill=(240, 240, 240, 255))
    for index in range(len(frames)):
        left, right = frames[index], frames[(index + 1) % len(frames)]
        row = index // 2
        for column, frame in enumerate((left, right)):
            image = Image.open(directory / frame["file"]).convert("RGBA")
            image.thumbnail((cell - 12, cell - 32), Image.Resampling.LANCZOS)
            x, y = column * cell, row * cell
            pair_sheet.alpha_composite(image, (x + (cell - image.width) // 2, y + 26 + (cell - 28 - image.height) // 2))
            draw_pairs.text((x + 8, y + 8), f"{left['yaw_degrees']:03d}° → {right['yaw_degrees']:03d}°", fill=(240, 240, 240, 255))
    ring_path, pairs_path = directory / "ring_contact_sheet.png", directory / "adjacent_pairs_contact_sheet.png"
    ring_sheet.convert("RGB").save(ring_path)
    pair_sheet.convert("RGB").save(pairs_path)
    return ring_path, pairs_path


def build_ring_review_package(ring_path: Path, output_directory: Path, *, repository_root: Path = ROOT) -> dict[str, Any]:
    ring = load_object(ring_path)
    validate_provisional_ring(ring)
    _new_path(output_directory, repository_root, "ring_review_directory")
    output_directory.mkdir(parents=True)
    frames = _copy_frames(ring, output_directory, repository_root)
    ring_sheet, pair_sheet = _write_contact_sheets(frames, output_directory)
    checklist = output_directory / "review_checklist.md"
    checklist.write_text(
        "# Complete-ring review\n\n"
        "Approve only if every image preserves the same Collector: one low body, six dorsal sacs, "
        "five bilateral leg pairs, one front mantle and one tail. Inspect every adjacent transition, "
        "especially 315° -> 0°. Confirm transparent isolation, no extra/missing limbs and treat the rear "
        "as an explicitly accepted artistic hypothesis. This approval creates secondary volume evidence only.\n",
        encoding="utf-8",
    )
    package = {
        "schema": "pale_mirror.automodel.ring_review_package.v1",
        "asset_id": ring["asset_id"],
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "provisional_ring": {"file": _relative(ring_path, repository_root, "review.provisional_ring"), "sha256": sha256(ring_path)},
        "frames": frames,
        "contact_sheets": {
            "ring": {"file": ring_sheet.name, "sha256": sha256(ring_sheet)},
            "adjacent_pairs": {"file": pair_sheet.name, "sha256": sha256(pair_sheet)},
        },
        "checklist": {"file": checklist.name, "sha256": sha256(checklist)},
        "required_affirmations": sorted(REQUIRED_AFFIRMATIONS),
    }
    write_object(output_directory / "ring_review_package.json", package)
    return package


def record_ring_review(
    package_path: Path,
    output_path: Path,
    reviewer: str,
    decision: str,
    affirmations: Iterable[str],
    notes: str,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    package = load_object(package_path)
    if package.get("schema") != "pale_mirror.automodel.ring_review_package.v1" or package.get("promotion_prohibited") is not True:
        raise AutomodelContractError("ring review requires a noncanonical review package")
    if decision not in {"accepted_complete_ring", "rejected"} or not reviewer.strip() or not notes.strip():
        raise AutomodelContractError("ring review requires reviewer, notes and a supported decision")
    supplied = frozenset(affirmations)
    if decision == "accepted_complete_ring" and supplied != REQUIRED_AFFIRMATIONS:
        raise AutomodelContractError("accepted ring review requires every explicit anatomy and continuity affirmation")
    if decision == "rejected" and supplied:
        raise AutomodelContractError("rejected ring review must not assert acceptance affirmations")
    ring_record = package.get("provisional_ring")
    if not isinstance(ring_record, dict):
        raise AutomodelContractError("ring review package lacks its provisional ring")
    ring_path = _require_file(ring_record, repository_root, "ring_review.provisional_ring")
    ring = load_object(ring_path)
    validate_provisional_ring(ring)
    _new_path(output_path, repository_root, "ring_review_receipt")
    receipt = {
        "schema": "pale_mirror.automodel.ring_review_receipt.v1",
        "asset_id": ring["asset_id"],
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "review_package": {"file": _relative(package_path, repository_root, "review_package"), "sha256": sha256(package_path)},
        "reviewer": reviewer,
        "decision": decision,
        "affirmations": sorted(supplied),
        "notes": notes,
        "contract": {
            "complete_ring_review_is_the_only_secondary_promotion_path": True,
            "does_not_authorize_blender_pmmesh_runtime_server_or_world_mutation": True,
        },
    }
    if decision == "accepted_complete_ring":
        reviewed_frames = []
        for index, frame in enumerate(ring["frames"]):
            copied = dict(frame)
            if index:
                copied["tier"] = EvidenceTier.REVIEWED_SECONDARY.value
                copied["review"] = {"status": "accepted_in_complete_ring", "reviewer": reviewer}
            reviewed_frames.append(copied)
        reviewed_path = output_path.with_name("reviewed_view_set.json")
        _new_path(reviewed_path, repository_root, "reviewed_view_set")
        reviewed = {
            "schema": "pale_mirror.automodel.reviewed_view_set.v1",
            "asset_id": ring["asset_id"],
            "scope": "research_only_noncanonical",
            "promotion_prohibited": True,
            "frames": reviewed_frames,
            "ring_review": {"decision": "accepted_complete_ring", "reviewer": reviewer},
        }
        write_object(output_path, receipt)
        validate_reviewed_view_set(reviewed)
        write_object(reviewed_path, reviewed)
        receipt["reviewed_view_set"] = {"file": _relative(reviewed_path, repository_root, "reviewed_view_set"), "sha256": sha256(reviewed_path)}
        write_object(output_path, receipt)
    else:
        write_object(output_path, receipt)
    return receipt


def prepare_geometry_input(review_receipt_path: Path, output_path: Path, *, repository_root: Path = ROOT) -> dict[str, Any]:
    receipt = load_object(review_receipt_path)
    if receipt.get("schema") != "pale_mirror.automodel.ring_review_receipt.v1" or receipt.get("decision") != "accepted_complete_ring":
        raise AutomodelContractError("pose/depth requires an accepted complete-ring user review")
    reviewed_record = receipt.get("reviewed_view_set")
    if not isinstance(reviewed_record, dict):
        raise AutomodelContractError("accepted review receipt lacks its reviewed view set")
    reviewed_path = _require_file(reviewed_record, repository_root, "reviewed_view_set")
    reviewed = load_object(reviewed_path)
    validate_reviewed_view_set(reviewed)
    _new_path(output_path, repository_root, "geometry_input")
    geometry_input = {
        "schema": "pale_mirror.automodel.geometry_input.v1",
        "asset_id": reviewed["asset_id"],
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "reviewed_view_set": {"file": _relative(reviewed_path, repository_root, "geometry_input.view_set"), "sha256": sha256(reviewed_path)},
        "frames": [
            {"id": frame["id"], "yaw_degrees": frame["yaw_degrees"], "tier": frame["tier"], "file": frame["file"], "sha256": frame["sha256"]}
            for frame in reviewed["frames"]
        ],
        "contract": {
            "permitted_adapters": ["da3_base", "vggt_official"],
            "models_may_emit_pose_depth_confidence_and_point_cloud_only": True,
            "model_disagreement_marks_uncertainty_never_mesh_fusion": True,
            "canonical_blender_pmmesh_runtime_server_world_access_forbidden": True,
        },
    }
    validate_geometry_input(geometry_input)
    write_object(output_path, geometry_input)
    return geometry_input


def _parse_selection(values: list[str]) -> dict[int, Path]:
    result: dict[int, Path] = {}
    for value in values:
        if "=" not in value:
            raise AutomodelContractError("selection must use YAW=MANIFEST")
        raw_yaw, raw_path = value.split("=", 1)
        try:
            yaw = int(raw_yaw)
        except ValueError as exc:
            raise AutomodelContractError("selection yaw must be an integer") from exc
        if yaw in result:
            raise AutomodelContractError("selection yaw may appear only once")
        result[yaw] = Path(raw_path)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    stage = commands.add_parser("stage-candidate")
    stage.add_argument("--anatomy-sheet", type=Path, required=True)
    stage.add_argument("--run-directory", type=Path, required=True)
    stage.add_argument("--yaw", type=int, required=True)
    stage.add_argument("--candidate-id", required=True)
    stage.add_argument("--source-image", type=Path, required=True)
    stage.add_argument("--prompt-record", type=Path, required=True)
    stage.add_argument("--external-import", type=Path, required=True)
    external = commands.add_parser("prepare-external-still")
    external.add_argument("--source-image", type=Path, required=True)
    external.add_argument("--output-directory", type=Path, required=True)
    assemble = commands.add_parser("assemble-ring")
    assemble.add_argument("--anatomy-sheet", type=Path, required=True)
    assemble.add_argument("--reference-bundle", type=Path, required=True)
    assemble.add_argument("--selection", action="append", required=True)
    assemble.add_argument("--output", type=Path, required=True)
    package = commands.add_parser("build-review-package")
    package.add_argument("--ring", type=Path, required=True)
    package.add_argument("--output-directory", type=Path, required=True)
    review = commands.add_parser("record-review")
    review.add_argument("--package", type=Path, required=True)
    review.add_argument("--output", type=Path, required=True)
    review.add_argument("--reviewer", required=True)
    review.add_argument("--decision", choices=("accepted_complete_ring", "rejected"), required=True)
    review.add_argument("--affirm", action="append", default=[])
    review.add_argument("--notes", required=True)
    geometry = commands.add_parser("prepare-geometry")
    geometry.add_argument("--review-receipt", type=Path, required=True)
    geometry.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    if arguments.command == "stage-candidate":
        result = stage_candidate(arguments.anatomy_sheet, arguments.run_directory, arguments.yaw, arguments.candidate_id, arguments.source_image, arguments.prompt_record, arguments.external_import)
    elif arguments.command == "prepare-external-still":
        result = prepare_external_still(arguments.source_image, arguments.output_directory)
    elif arguments.command == "assemble-ring":
        result = assemble_provisional_ring(arguments.anatomy_sheet, arguments.reference_bundle, _parse_selection(arguments.selection), arguments.output)
    elif arguments.command == "build-review-package":
        result = build_ring_review_package(arguments.ring, arguments.output_directory)
    elif arguments.command == "record-review":
        result = record_ring_review(arguments.package, arguments.output, arguments.reviewer, arguments.decision, arguments.affirm, arguments.notes)
    elif arguments.command == "prepare-geometry":
        result = prepare_geometry_input(arguments.review_receipt, arguments.output)
    else:
        raise AssertionError("unhandled command")
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
