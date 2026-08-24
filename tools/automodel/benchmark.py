#!/usr/bin/env python3
"""Deterministic known-geometry metrics for Automodel adapter qualification.

The corpus creates compact opaque/mask fixtures with exact yaw metadata.  It
does not claim that a fixture score measures artistic likeness; it merely
qualifies whether an adapter may provide pose/depth suggestions at all.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
from math import fsum
from pathlib import Path
from statistics import median
from typing import Any

from PIL import Image, ImageDraw

from contracts import (
    AutomodelContractError,
    EvidenceTier,
    require_relative_build_path,
    sha256,
    validate_adapter_qualification,
    write_object,
)


YAW_STEP = 18
FRAME_COUNT = 21
QUALIFICATION = {
    "maximum_median_yaw_error_degrees": 15.0,
    "maximum_p90_yaw_error_degrees": 25.0,
    "minimum_mean_mask_iou": 0.95,
    "maximum_mean_depth_error": 0.10,
}


def _circular_error(actual: float, expected: float) -> float:
    delta = (actual - expected + 180.0) % 360.0 - 180.0
    return abs(delta)


def _p90(values: list[float]) -> float:
    if not values:
        raise AutomodelContractError("cannot calculate a percentile for no values")
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, round((len(ordered) - 1) * 0.9))]


def evaluate_report(report: dict[str, Any]) -> dict[str, Any]:
    """Score a normalised adapter report against fixed synthetic ground truth."""

    observations = report.get("observations")
    if not isinstance(observations, list) or len(observations) != FRAME_COUNT:
        raise AutomodelContractError(f"benchmark report requires {FRAME_COUNT} observations")
    yaw_errors: list[float] = []
    ious: list[float] = []
    depths: list[float] = []
    measured_yaws: list[float] = []
    for index, observation in enumerate(observations):
        if not isinstance(observation, dict):
            raise AutomodelContractError(f"benchmark observation {index} must be an object")
        expected = index * YAW_STEP
        yaw = observation.get("yaw_degrees")
        iou = observation.get("mask_iou")
        depth_error = observation.get("depth_error")
        if not all(isinstance(value, (int, float)) for value in (yaw, iou, depth_error)):
            raise AutomodelContractError(f"benchmark observation {index} requires numeric yaw, mask_iou and depth_error")
        if not 0.0 <= iou <= 1.0 or depth_error < 0.0:
            raise AutomodelContractError(f"benchmark observation {index} has an invalid score")
        measured_yaws.append(float(yaw))
        yaw_errors.append(_circular_error(float(yaw), expected))
        ious.append(float(iou))
        depths.append(float(depth_error))
    unwrapped = measured_yaws[0]
    monotonic = True
    for value in measured_yaws[1:]:
        while value < unwrapped:
            value += 360.0
        if value - unwrapped <= 0.0:
            monotonic = False
        unwrapped = value
    metrics = {
        "median_yaw_error_degrees": median(yaw_errors),
        "p90_yaw_error_degrees": _p90(yaw_errors),
        "mean_mask_iou": fsum(ious) / len(ious),
        "mean_depth_error": fsum(depths) / len(depths),
        "monotonic_yaw_order": monotonic,
    }
    qualified = (
        metrics["median_yaw_error_degrees"] <= QUALIFICATION["maximum_median_yaw_error_degrees"]
        and metrics["p90_yaw_error_degrees"] <= QUALIFICATION["maximum_p90_yaw_error_degrees"]
        and metrics["mean_mask_iou"] >= QUALIFICATION["minimum_mean_mask_iou"]
        and metrics["mean_depth_error"] <= QUALIFICATION["maximum_mean_depth_error"]
        and monotonic
    )
    return {"schema": "pale_mirror.automodel.benchmark_result.v1", "metrics": metrics, "qualified_for_model_derived_geometry": qualified, "limits": QUALIFICATION}


def build_fixture_corpus(output: Path) -> dict[str, Any]:
    """Create three deterministic, known silhouettes and a 21-camera ring."""

    output.mkdir(parents=True, exist_ok=True)
    objects = {
        "asymmetric_organic": [(14, 48), (29, 16), (64, 10), (91, 29), (103, 61), (79, 90), (36, 97), (15, 72)],
        "open_drape": [(15, 19), (53, 8), (103, 26), (90, 44), (73, 82), (58, 49), (35, 89), (27, 46)],
        "multi_support": [(21, 45), (37, 18), (77, 14), (103, 44), (90, 68), (75, 58), (65, 105), (53, 61), (39, 105), (31, 60), (17, 74)],
    }
    records: list[dict[str, Any]] = []
    for object_id, polygon in objects.items():
        image = Image.new("L", (120, 120), 0)
        ImageDraw.Draw(image).polygon(polygon, fill=255)
        path = output / f"{object_id}_mask.png"
        image.save(path)
        records.append({"id": object_id, "mask": {"file": path.name, "sha256": sha256(path)}})
    cameras = [{"index": index, "yaw_degrees": index * YAW_STEP, "pitch_degrees": 0.0} for index in range(FRAME_COUNT)]
    corpus = {"schema": "pale_mirror.automodel.known_geometry_corpus.v1", "objects": records, "cameras": cameras, "purpose": "adapter qualification only; not creature likeness evaluation"}
    write_object(output / "corpus.json", corpus)
    return corpus


def write_adapter_qualification(
    adapter_id: str,
    corpus_path: Path,
    report: dict[str, Any],
    output_path: Path,
    *,
    repository_root: Path,
) -> dict[str, Any]:
    """Persist a reproducible adapter-role decision from known geometry.

    This receipt answers only whether an adapter may contribute
    ``MODEL_DERIVED`` pose/depth suggestions.  It is deliberately incapable of
    accepting a creature, a synthetic view, a mesh or an artistic result.
    """

    if not corpus_path.is_file():
        raise AutomodelContractError(f"benchmark corpus is missing: {corpus_path}")
    try:
        corpus_relative = corpus_path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("benchmark corpus must be inside the repository") from exc
    require_relative_build_path(corpus_relative, "corpus.file")
    result = evaluate_report(report)
    receipt = {
        "schema": "pale_mirror.automodel.adapter_qualification.v1",
        "adapter_id": adapter_id,
        "scope": "research_only_noncanonical",
        "authority": EvidenceTier.MODEL_DERIVED.value,
        "corpus": {"file": corpus_relative, "sha256": sha256(corpus_path)},
        "benchmark_result": result,
        "conclusion": "adapter_role_qualified" if result["qualified_for_model_derived_geometry"] else "adapter_role_rejected",
    }
    validate_adapter_qualification(receipt)
    if output_path.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable Automodel artifact: {output_path}")
    try:
        output_relative = output_path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("qualification receipt must be inside the repository") from exc
    require_relative_build_path(output_relative, "qualification.file")
    write_object(output_path, receipt)
    return receipt
