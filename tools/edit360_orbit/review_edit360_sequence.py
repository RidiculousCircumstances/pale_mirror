#!/usr/bin/env python3
"""Create an inspectable, *uncalibrated* Edit360 sequence review package.

Edit360's dual-condition sampler produces an ordered visual orbit but does not
expose a commanded camera path for its ``sv3d_u`` mode.  This tool deliberately
records sequence indices rather than invented yaw angles.  Its review package
is visual evidence only and is structurally unsuitable for ViewSet, VGGT,
geometry, Blender, PMMesh or runtime collection.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "automodel"))

from contracts import AutomodelContractError, load_object, require_relative_build_path, sha256, write_object


FRAME_COUNT = 21
MODEL_SEQUENCE_COUNT = 20
PANEL_SIZE = 192
COLUMNS = 6
LABEL_HEIGHT = 26


def _relative(path: Path, repository_root: Path) -> str:
    try:
        relative = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"Edit360 review artifact is outside repository: {path}") from exc
    return require_relative_build_path(relative, "edit360.review.file")


def _manifest_input(manifest: dict[str, Any], role: str, repository_root: Path) -> Path:
    matches = [record for record in manifest["inputs"] if record.get("role") == role]
    if len(matches) != 1:
        raise AutomodelContractError(f"Edit360 manifest must have exactly one {role!r} input")
    record = matches[0]
    candidate = (repository_root / record["file"]).resolve()
    if not candidate.is_file() or sha256(candidate) != record["sha256"]:
        raise AutomodelContractError(f"Edit360 manifest input {role!r} is missing or stale")
    return candidate


def _manifest_input_any(manifest: dict[str, Any], roles: set[str], label: str, repository_root: Path) -> Path:
    matches = [record for record in manifest["inputs"] if record.get("role") in roles]
    if len(matches) != 1:
        raise AutomodelContractError(f"Edit360 manifest must have exactly one {label} input")
    record = matches[0]
    candidate = (repository_root / record["file"]).resolve()
    if not candidate.is_file() or sha256(candidate) != record["sha256"]:
        raise AutomodelContractError(f"Edit360 manifest input {label!r} is missing or stale")
    return candidate


def _open_panel(path: Path) -> Image.Image:
    with Image.open(path) as opened:
        frame = opened.convert("RGB")
    if frame.size != (576, 576):
        raise AutomodelContractError(f"Edit360 expected a 576-square frame: {path.name} has {frame.size}")
    return frame.resize((PANEL_SIZE, PANEL_SIZE), Image.Resampling.LANCZOS)


def build_review(
    run_manifest_path: Path,
    frames_directory: Path,
    front_conditioning: Path,
    opposite_anchor: Path,
    output_directory: Path,
    *,
    repository_root: Path,
) -> dict[str, Any]:
    manifest = load_object(run_manifest_path)
    if (
        manifest.get("schema") != "pale_mirror.automodel.run_manifest.v1"
        or manifest.get("adapter_id") != "edit360_dual_anchor_orbit_fast_fp16_20"
        or manifest.get("scope") != "research_only_noncanonical"
        or manifest.get("promotion_prohibited") is not True
    ):
        raise AutomodelContractError("Edit360 review requires the bounded noncanonical dual-anchor manifest")
    run_directory = (repository_root / manifest["run_directory"]).resolve()
    if run_manifest_path.resolve().parent != run_directory:
        raise AutomodelContractError("Edit360 manifest must sit directly inside its prepared run directory")
    if not frames_directory.resolve().is_relative_to(run_directory):
        raise AutomodelContractError("Edit360 raw frames must remain under their run directory")
    if output_directory.exists() or not output_directory.resolve().is_relative_to(run_directory):
        raise AutomodelContractError("Edit360 review output must be a fresh directory under its run")
    manifest_front = _manifest_input_any(
        manifest,
        {"conditioning:edit360_front_white_r01", "conditioning:edit360_generated_front_white_r01"},
        "front conditioning",
        repository_root,
    )
    _manifest_input(manifest, "anchor:edit360_opposite_broadside_r01", repository_root)
    if front_conditioning.resolve() != manifest_front:
        raise AutomodelContractError("Edit360 review front conditioning does not match its manifest")
    if opposite_anchor.resolve() != _manifest_input(manifest, "anchor:edit360_opposite_broadside_r01", repository_root):
        raise AutomodelContractError("Edit360 review opposite anchor does not match its manifest")
    expected = {f"{index}.png" for index in range(1, FRAME_COUNT + 1)}
    actual = {path.name for path in frames_directory.iterdir() if path.is_file() and path.suffix.lower() == ".png"}
    if actual != expected:
        raise AutomodelContractError(f"Edit360 must emit exactly 1.png through 21.png; got {sorted(actual)}")

    panels: list[tuple[str, Path]] = [
        ("FRONT CONDITIONING", front_conditioning),
        ("180° MODEL ANCHOR", opposite_anchor),
    ]
    panels.extend((f"MODEL SEQUENCE {index:02d}", frames_directory / f"{index + 1}.png") for index in range(1, MODEL_SEQUENCE_COUNT + 1))
    rows = (len(panels) + COLUMNS - 1) // COLUMNS
    canvas = Image.new("RGB", (COLUMNS * PANEL_SIZE, rows * (PANEL_SIZE + LABEL_HEIGHT)), (24, 24, 28))
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default()
    for index, (label, path) in enumerate(panels):
        x = (index % COLUMNS) * PANEL_SIZE
        y = (index // COLUMNS) * (PANEL_SIZE + LABEL_HEIGHT)
        canvas.paste(_open_panel(path), (x, y))
        draw.rectangle((x, y + PANEL_SIZE, x + PANEL_SIZE, y + PANEL_SIZE + LABEL_HEIGHT), fill=(16, 16, 18))
        draw.text((x + 4, y + PANEL_SIZE + 7), label, fill=(235, 235, 235), font=font)

    output_directory.mkdir(parents=True)
    sheet_path = output_directory / "edit360_visual_sequence_contact_sheet.png"
    manifest_path = output_directory / "edit360_visual_sequence_review.json"
    canvas.save(sheet_path)
    sequence = []
    for index in range(1, MODEL_SEQUENCE_COUNT + 1):
        path = frames_directory / f"{index + 1}.png"
        sequence.append(
            {
                "sequence_index": index,
                "tier": "MODEL_DERIVED",
                "file": _relative(path, repository_root),
                "sha256": sha256(path),
                "camera_pose_or_yaw": "not_exposed_by_edit360_upstream",
                "geometry_or_canonical_use_prohibited": True,
            }
        )
    review = {
        "schema": "pale_mirror.automodel.edit360_visual_sequence_review.v1",
        "asset_id": manifest["asset_id"],
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "run_manifest": {"file": _relative(run_manifest_path, repository_root), "sha256": sha256(run_manifest_path)},
        "inputs": {
            "front_conditioning": {"file": _relative(front_conditioning, repository_root), "sha256": sha256(front_conditioning)},
            "opposite_anchor": {"file": _relative(opposite_anchor, repository_root), "sha256": sha256(opposite_anchor), "declared_yaw_degrees": 180},
        },
        "output_contract": {
            "kind": "ordered_visual_sequence",
            "model_frame_count": MODEL_SEQUENCE_COUNT,
            "camera_pose_or_yaw": "not_exposed_by_edit360_upstream",
            "view_set_prohibited": True,
            "vggt_or_geometry_use_prohibited": True,
            "canonical_blender_pmmesh_runtime_use_prohibited": True,
        },
        "sequence": sequence,
        "review_artifacts": {"contact_sheet": {"file": _relative(sheet_path, repository_root), "sha256": sha256(sheet_path)}},
    }
    write_object(manifest_path, review)
    return review


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-manifest", type=Path, required=True)
    parser.add_argument("--frames-directory", type=Path, required=True)
    parser.add_argument("--front-conditioning", type=Path, required=True)
    parser.add_argument("--opposite-anchor", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--repository-root", type=Path, default=Path(__file__).resolve().parents[2])
    arguments = parser.parse_args()
    print(
        json.dumps(
            build_review(
                arguments.run_manifest,
                arguments.frames_directory,
                arguments.front_conditioning,
                arguments.opposite_anchor,
                arguments.output_directory,
                repository_root=arguments.repository_root,
            ),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
