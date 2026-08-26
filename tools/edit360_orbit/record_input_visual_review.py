#!/usr/bin/env python3
"""Record a visual preflight for the two Edit360 conditioning images.

This intentionally does not judge creature likeness or promote a model-derived
image.  It makes an operator inspect the exact staged pixels and records only
whether they are suitable inputs for one noncanonical visual-preview run.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import sys

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))

from automodel.contracts import (
    AutomodelContractError,
    load_object,
    require_relative_build_path,
    sha256,
    write_object,
)


FRONT_ROLES = {
    "conditioning:edit360_front_white_r01",
    "conditioning:edit360_generated_front_white_r01",
}
DECISION = "accepted_for_noncanonical_visual_preview_only"
OPPOSITE_ANCHOR_VIEW_KINDS = (
    "verified_opposite_broadside",
    "actual_rear_right_three_quarter",
)


def _manifest_input(manifest: dict[str, object], roles: set[str], label: str) -> dict[str, object]:
    records = [record for record in manifest.get("inputs", []) if isinstance(record, dict) and record.get("role") in roles]
    if len(records) != 1:
        raise AutomodelContractError(f"Edit360 manifest needs exactly one {label} input")
    return records[0]


def _validate_clean_canvas(path: Path, label: str) -> dict[str, object]:
    with Image.open(path) as source:
        image = source.convert("RGB")
    if image.size != (576, 576):
        raise AutomodelContractError(f"{label} must be an exact 576x576 effective frame")
    border = [image.getpixel((x, y)) for x in range(576) for y in (0, 575)]
    border.extend(image.getpixel((x, y)) for y in range(1, 575) for x in (0, 575))
    non_white_border_pixels = sum(pixel != (255, 255, 255) for pixel in border)
    if non_white_border_pixels:
        raise AutomodelContractError(f"{label} must keep a clean white outer border")
    return {"size": list(image.size), "mode": "RGB", "non_white_border_pixels": non_white_border_pixels}


def record_review(
    manifest_path: Path,
    front_path: Path,
    anchor_path: Path,
    output_path: Path,
    reviewer: str,
    front_notes: str,
    anchor_notes: str,
    anchor_view_kind: str,
) -> dict[str, object]:
    manifest = load_object(manifest_path)
    if manifest.get("schema") != "pale_mirror.automodel.run_manifest.v1" or manifest.get("scope") != "research_only_noncanonical":
        raise AutomodelContractError("Edit360 input review requires a noncanonical run manifest")
    front = _manifest_input(manifest, FRONT_ROLES, "Edit360 front")
    anchor = _manifest_input(manifest, {"anchor:edit360_opposite_broadside_r01"}, "Edit360 opposite anchor")
    for label, record, path in (("front", front, front_path), ("opposite anchor", anchor, anchor_path)):
        if not path.is_file() or sha256(path) != record.get("sha256"):
            raise AutomodelContractError(f"{label} does not match the exact staged manifest input")
    if not reviewer.strip() or not front_notes.strip() or not anchor_notes.strip():
        raise AutomodelContractError("input visual review requires named reviewer and non-empty observations for both inputs")
    if anchor_view_kind not in OPPOSITE_ANCHOR_VIEW_KINDS:
        raise AutomodelContractError("Edit360 opposite anchor must be visually verified as a real opposite broadside or rear-right three-quarter view")
    resolved_output = output_path.resolve()
    try:
        relative_output = resolved_output.relative_to(ROOT.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("input visual review must remain in the repository build directory") from exc
    require_relative_build_path(relative_output, "edit360.input_visual_review.file")
    if resolved_output.exists():
        raise AutomodelContractError("refusing to overwrite immutable Edit360 input visual review")
    review = {
        "schema": "pale_mirror.automodel.edit360_input_visual_review.v1",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "decision": DECISION,
        "reviewer": reviewer.strip(),
        "review_kind": "operator_visual_inspection_of_exact_staged_pixels",
        "run_manifest": {"file": str(manifest_path.resolve().relative_to(ROOT.resolve())).replace("\\", "/"), "sha256": sha256(manifest_path)},
        "inputs": {
            "front_conditioning": {
                "role": front["role"],
                "file": str(front_path.resolve().relative_to(ROOT.resolve())).replace("\\", "/"),
                "sha256": sha256(front_path),
                "technical_canvas": _validate_clean_canvas(front_path, "front conditioning"),
                "operator_observation": front_notes.strip(),
            },
            "opposite_anchor": {
                "role": anchor["role"],
                "file": str(anchor_path.resolve().relative_to(ROOT.resolve())).replace("\\", "/"),
                "sha256": sha256(anchor_path),
                "technical_canvas": _validate_clean_canvas(anchor_path, "opposite anchor"),
                "view_kind": anchor_view_kind,
                "operator_observation": anchor_notes.strip(),
            },
        },
        "contract": {
            "visual_preview_only": True,
            "model_output_remains_model_derived": True,
            "view_set_vggt_geometry_canonical_use_prohibited": True,
        },
    }
    write_object(resolved_output, review)
    return review


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-manifest", type=Path, required=True)
    parser.add_argument("--front-conditioning", type=Path, required=True)
    parser.add_argument("--opposite-anchor", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--reviewer", required=True)
    parser.add_argument("--front-notes", required=True)
    parser.add_argument("--anchor-notes", required=True)
    parser.add_argument("--anchor-view-kind", choices=OPPOSITE_ANCHOR_VIEW_KINDS, required=True)
    arguments = parser.parse_args()
    print(
        record_review(
            arguments.run_manifest,
            arguments.front_conditioning,
            arguments.opposite_anchor,
            arguments.output,
            arguments.reviewer,
            arguments.front_notes,
            arguments.anchor_notes,
            arguments.anchor_view_kind,
        )
    )


if __name__ == "__main__":
    main()
