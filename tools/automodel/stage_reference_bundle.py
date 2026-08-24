#!/usr/bin/env python3
"""Stage a literal primary and trace as an immutable Automodel input bundle.

The original primary may live outside the repository.  This tool copies its
literal bytes into an ignored run directory, pins the copied hash and, when
requested, derives a *conditioning-only* trace-masked RGBA image.  It never
changes the source image, source trace, Blender, PMMesh or runtime resources.
"""

from __future__ import annotations

import argparse
import shutil
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


def stage_reference_bundle(
    asset_id: str,
    primary_source: Path,
    trace_source: Path,
    run_directory: Path,
    *,
    trace_masked_conditioning: bool,
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
    model_inputs: list[dict[str, Any]] = []
    if trace_masked_conditioning:
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
        conditioning_target = input_directory / "trace_masked_conditioning.png"
        if conditioning_target.exists():
            raise AutomodelContractError(f"refusing to overwrite staged conditioning input: {conditioning_target}")
        primary.putalpha(mask)
        primary.save(conditioning_target)
        model_inputs.append(
            {
                "id": "trace_masked_primary",
                "role": "conditioning_only",
                "file": _relative(conditioning_target, repository_root),
                "sha256": sha256(conditioning_target),
                "derives_from": ["primary", "primary_trace"],
            }
        )
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
        "anchors": [],
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
        "--trace-masked-conditioning",
        action="store_true",
        help="Create a transparent RGBA conditioning input derived only from the literal primary and its trace.",
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
                trace_masked_conditioning=arguments.trace_masked_conditioning,
            ),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
