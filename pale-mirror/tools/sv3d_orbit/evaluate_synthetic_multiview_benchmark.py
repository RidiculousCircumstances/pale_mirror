#!/usr/bin/env python3
"""Score completed fast SV3D synthetic orbits against held-back geometry.

The evaluator measures only reproducible view-synthesis behaviour: coarse
silhouette, apparent heading order and exposed thin/offset detail. It cannot
evaluate a creature, establish a real camera calibration or promote a generated
frame beyond ``MODEL_DERIVED`` evidence.
"""

from __future__ import annotations

import argparse
import json
from math import fsum
from pathlib import Path
from statistics import median
import sys
from typing import Any

from PIL import Image, ImageChops, ImageFilter


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from contracts import AutomodelContractError, load_object, sha256, write_object  # noqa: E402


YAWS = tuple(range(0, 361, 18))
NONPRIMARY_YAWS = tuple(range(18, 361, 18))
UNIQUE_YAWS = tuple(range(0, 360, 18))
# Official SV3D positive azimuth moves opposite to the synthetic renderer's
# positive world yaw. This is one sampler-level camera convention, not a
# per-frame fit: 18° always maps to 342°, 90° to 270°, and so on.
SV3D_TO_SYNTHETIC_YAW = "negate_modulo_360"
RESULT_FILE = "synthetic_multiview_benchmark_result_v2.json"
LIMITS = {
    "minimum_mean_silhouette_iou": 0.78,
    "maximum_median_yaw_error_degrees": 18.0,
    "maximum_p90_yaw_error_degrees": 36.0,
    "minimum_mean_exposed_critical_recall": 0.70,
    "maximum_mean_critical_local_excess": 0.50,
    "minimum_exposed_critical_sample_count": 6,
    "requires_strict_monotonic_predicted_yaw": True,
}


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise AutomodelContractError(message)


def _resolve_suite(path: Path) -> Path:
    suite = path.resolve()
    try:
        relative = suite.relative_to((ROOT / "build" / "automodel").resolve())
    except ValueError as exc:
        raise AutomodelContractError("synthetic benchmark must remain under build/automodel") from exc
    if not relative.parts:
        raise AutomodelContractError("build/automodel itself is not a benchmark suite")
    return suite


def _load_capture_receipt(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AutomodelContractError(f"cannot load Windows capture receipt {path}: {exc}") from exc
    _require(isinstance(value, dict), "capture receipt must be a JSON object")
    return value


def _binary(image: Image.Image) -> Image.Image:
    return image.convert("L").point(lambda value: 255 if value >= 128 else 0)


def _mask(path: Path) -> Image.Image:
    with Image.open(path) as image:
        return _binary(image.copy())


def _foreground(path: Path) -> Image.Image:
    """Segment neutral-background SV3D output without object-specific RGB."""

    with Image.open(path) as source:
        image = source.convert("RGB")
    width, height = image.size
    pixels = image.load()
    edge = 24
    corners = [
        pixels[x, y]
        for x in tuple(range(edge)) + tuple(range(width - edge, width))
        for y in tuple(range(edge)) + tuple(range(height - edge, height))
    ]
    background = tuple(median(channel) for channel in zip(*corners, strict=True))
    threshold_squared = 34 * 34
    mask = Image.new("L", image.size)
    mask.putdata(
        [
            255
            if sum((channel - background[index]) ** 2 for index, channel in enumerate(pixel)) >= threshold_squared
            else 0
            for pixel in image.get_flattened_data()
        ]
    )
    foreground = _binary(mask)
    _require((foreground.histogram()[255]) > 256, f"generated frame has no usable foreground: {path.name}")
    return foreground


def _bbox(mask: Image.Image) -> tuple[int, int, int, int]:
    bounds = mask.getbbox()
    _require(bounds is not None, "cannot align an empty mask")
    return bounds


def _align(predicted: Image.Image, target: Image.Image) -> Image.Image:
    """Align global scale and centre only; geometry receives no free warp."""

    px0, py0, px1, py1 = _bbox(predicted)
    tx0, ty0, tx1, ty1 = _bbox(target)
    scale = min((tx1 - tx0) / (px1 - px0), (ty1 - ty0) / (py1 - py0))
    source_center = ((px0 + px1) / 2.0, (py0 + py1) / 2.0)
    target_center = ((tx0 + tx1) / 2.0, (ty0 + ty1) / 2.0)
    inverse = 1.0 / scale
    affine = (
        inverse,
        0.0,
        source_center[0] - target_center[0] * inverse,
        0.0,
        inverse,
        source_center[1] - target_center[1] * inverse,
    )
    return _binary(predicted.transform(target.size, Image.Transform.AFFINE, affine, resample=Image.Resampling.NEAREST))


def _count(mask: Image.Image) -> int:
    return int(mask.histogram()[255])


def _and(left: Image.Image, right: Image.Image) -> Image.Image:
    return ImageChops.multiply(left, right)


def _iou(left: Image.Image, right: Image.Image) -> float:
    intersection = _count(_and(left, right))
    union = _count(ImageChops.lighter(left, right))
    _require(union > 0, "cannot compare two empty masks")
    return intersection / union


def _dilate(mask: Image.Image, radius: int) -> Image.Image:
    return mask.filter(ImageFilter.MaxFilter(radius * 2 + 1))


def _circular_error(actual: int, expected: int) -> float:
    return abs((actual - expected + 180) % 360 - 180)


def _expected_synthetic_yaw(declared_sv3d_yaw: int) -> int:
    _require(declared_sv3d_yaw in NONPRIMARY_YAWS, "SV3D frame must use the declared non-primary ring")
    return (-declared_sv3d_yaw) % 360


def _p90(values: list[float]) -> float:
    ordered = sorted(values)
    _require(bool(ordered), "cannot take a percentile of no values")
    return ordered[min(len(ordered) - 1, round((len(ordered) - 1) * 0.9))]


def _strictly_monotonic(yaws: list[int]) -> bool:
    previous = yaws[0]
    for yaw in yaws[1:]:
        unwrapped = yaw
        while unwrapped < previous:
            unwrapped += 360
        if unwrapped <= previous:
            return False
        previous = unwrapped
    return True


def _read_ground_truth(scene_directory: Path, scene: dict[str, Any]) -> dict[int, dict[str, Image.Image]]:
    records = scene.get("ground_truth_frames")
    _require(isinstance(records, list) and len(records) == len(YAWS), "scene must contain exact known camera ground truth")
    result: dict[int, dict[str, Image.Image]] = {}
    for record in records:
        _require(isinstance(record, dict) and isinstance(record.get("yaw_degrees"), int), "invalid ground-truth frame record")
        yaw = record["yaw_degrees"]
        _require(yaw in YAWS and yaw not in result, "ground-truth yaws must be unique 18-degree positions")
        masks: dict[str, Image.Image] = {}
        for kind in ("full_mask", "core_mask", "critical_mask"):
            asset = record.get(kind)
            _require(isinstance(asset, dict) and isinstance(asset.get("file"), str) and isinstance(asset.get("sha256"), str), f"ground truth lacks {kind}")
            path = scene_directory / asset["file"]
            _require(path.is_file() and sha256(path) == asset["sha256"], f"changed ground-truth {kind}: {path}")
            masks[kind] = _mask(path)
        result[yaw] = masks
    _require(tuple(sorted(result)) == YAWS, "ground truth does not cover the exact camera ring")
    return result


def _critical_metrics(aligned: Image.Image, ground_truth: dict[str, Image.Image]) -> tuple[float, float] | None:
    """Score a semantic detail only in a view where it is actually visible.

    The ground-truth role mask is visibility-aware.  At some headings a thin
    cable, rod, or vane is wholly behind the core, so treating that frame as a
    zero-recall detail failure would measure occlusion rather than SV3D.  The
    suite-level gate separately requires enough exposed views to prevent this
    exemption from hiding an inadequate fixture.
    """

    core = ground_truth["core_mask"]
    critical = ground_truth["critical_mask"]
    exposed = ImageChops.subtract(critical, _dilate(core, 4))
    exposed_count = _count(exposed)
    if exposed_count < 20:
        return None
    recall = _count(_and(aligned, exposed)) / exposed_count
    local = _dilate(exposed, 8)
    excess = ImageChops.subtract(_and(aligned, local), ground_truth["full_mask"])
    return recall, _count(excess) / exposed_count


def score_generated_frame(predicted: Image.Image, declared_yaw: int, ground_truth: dict[int, dict[str, Image.Image]]) -> dict[str, float | int | bool | None]:
    """Return deterministic pose/silhouette/detail observations for one frame."""

    candidate_scores: list[tuple[float, int]] = []
    expected_yaw = _expected_synthetic_yaw(declared_yaw)
    for yaw in UNIQUE_YAWS:
        aligned = _align(predicted, ground_truth[yaw]["full_mask"])
        candidate_scores.append((_iou(aligned, ground_truth[yaw]["full_mask"]), yaw))
    best_iou, predicted_yaw = max(candidate_scores, key=lambda candidate: (candidate[0], -candidate[1]))
    expected_aligned = _align(predicted, ground_truth[expected_yaw]["full_mask"])
    critical = _critical_metrics(expected_aligned, ground_truth[expected_yaw])
    recall, excess = critical if critical is not None else (None, None)
    return {
        "declared_yaw_degrees": declared_yaw,
        "expected_synthetic_yaw_degrees": expected_yaw,
        "best_matching_ground_truth_yaw_degrees": predicted_yaw,
        "yaw_error_degrees": _circular_error(predicted_yaw, expected_yaw),
        "silhouette_iou_at_declared_yaw": _iou(expected_aligned, ground_truth[expected_yaw]["full_mask"]),
        "best_candidate_silhouette_iou": best_iou,
        "critical_detail_exposed": critical is not None,
        "exposed_critical_recall": recall,
        "critical_local_excess": excess,
    }


def _score_scene(suite_directory: Path, scene_record: dict[str, Any]) -> dict[str, Any]:
    _require(isinstance(scene_record.get("run_directory"), str), "suite scene lacks a run directory")
    run = ROOT / scene_record["run_directory"]
    _require(run.is_relative_to(suite_directory), "suite scene run must stay within the suite directory")
    scene_asset = scene_record.get("synthetic_scene")
    _require(isinstance(scene_asset, dict) and isinstance(scene_asset.get("file"), str) and isinstance(scene_asset.get("sha256"), str), "suite scene lacks synthetic-scene provenance")
    scene_path = suite_directory / scene_asset["file"]
    _require(scene_path.is_file() and sha256(scene_path) == scene_asset["sha256"], "synthetic-scene specification changed")
    scene = load_object(scene_path)
    capture = _load_capture_receipt(run / "execution_capture_receipt.json")
    manifest = load_object(run / "run_manifest.json")
    view_set = load_object(run / "view_set.json")
    _require(manifest.get("adapter_id") == "sv3d_p_orbit_fast_fp16_20", "benchmark run used the wrong adapter")
    _require(capture.get("outcome") == "success" and capture.get("execution_profile") == "fast_fp16_20", "benchmark capture was not successful fast execution")
    frames = view_set.get("frames")
    _require(isinstance(frames, list) and len(frames) == 21, "benchmark view set must retain twenty generated frames plus primary")
    _require(frames[0].get("tier") == "PRIMARY_TRACE", "benchmark view set lost its literal primary")
    observed = [float(value.get("declared_yaw_degrees", -1.0)) for value in frames[1:]]
    _require(observed == [float(yaw) for yaw in NONPRIMARY_YAWS], "benchmark view set has the wrong generated yaw order")
    ground_truth = _read_ground_truth(run, scene)
    observations: list[dict[str, float | int | bool | None]] = []
    for yaw in NONPRIMARY_YAWS:
        frame = run / "outputs" / "nonprimary_orbit" / f"frame_{yaw:03d}.png"
        _require(frame.is_file(), f"benchmark frame is missing: {frame}")
        observations.append(score_generated_frame(_foreground(frame), yaw, ground_truth))
    exposed_observations = [item for item in observations if item["critical_detail_exposed"] is True]
    _require(
        len(exposed_observations) >= LIMITS["minimum_exposed_critical_sample_count"],
        "synthetic scene lacks enough exposed critical-detail frames",
    )
    metrics = {
        "mean_silhouette_iou": fsum(float(item["silhouette_iou_at_declared_yaw"]) for item in observations) / len(observations),
        "median_yaw_error_degrees": median(float(item["yaw_error_degrees"]) for item in observations),
        "p90_yaw_error_degrees": _p90([float(item["yaw_error_degrees"]) for item in observations]),
        "exposed_critical_sample_count": len(exposed_observations),
        "mean_exposed_critical_recall": fsum(float(item["exposed_critical_recall"]) for item in exposed_observations) / len(exposed_observations),
        "mean_critical_local_excess": fsum(float(item["critical_local_excess"]) for item in exposed_observations) / len(exposed_observations),
        "strict_monotonic_predicted_yaw": _strictly_monotonic([int(item["best_matching_ground_truth_yaw_degrees"]) for item in observations]),
    }
    qualified = (
        metrics["mean_silhouette_iou"] >= LIMITS["minimum_mean_silhouette_iou"]
        and metrics["median_yaw_error_degrees"] <= LIMITS["maximum_median_yaw_error_degrees"]
        and metrics["p90_yaw_error_degrees"] <= LIMITS["maximum_p90_yaw_error_degrees"]
        and metrics["mean_exposed_critical_recall"] >= LIMITS["minimum_mean_exposed_critical_recall"]
        and metrics["mean_critical_local_excess"] <= LIMITS["maximum_mean_critical_local_excess"]
        and metrics["exposed_critical_sample_count"] >= LIMITS["minimum_exposed_critical_sample_count"]
        and metrics["strict_monotonic_predicted_yaw"] is True
    )
    return {
        "scene_id": scene["scene_id"],
        "run_directory": scene_record["run_directory"],
        "scene_spec": {"file": scene_asset["file"], "sha256": scene_asset["sha256"]},
        "observations": observations,
        "metrics": metrics,
        "qualified_for_bounded_pose_depth_experiment": qualified,
    }


def evaluate(suite_directory: Path) -> dict[str, Any]:
    suite_root = _resolve_suite(suite_directory)
    output = suite_root / RESULT_FILE
    if output.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable benchmark result: {output}")
    suite = load_object(suite_root / "synthetic_multiview_suite.json")
    _require(suite.get("schema") == "pale_mirror.automodel.synthetic_sv3d_multiview_suite.v1", "unsupported synthetic benchmark suite")
    _require(suite.get("adapter_id") == "sv3d_p_orbit_fast_fp16_20", "benchmark suite used the wrong adapter")
    _require(suite.get("scope") == "research_only_noncanonical" and suite.get("promotion_prohibited") is True, "benchmark suite lost evidence boundary")
    scenes = suite.get("scenes")
    _require(isinstance(scenes, list) and len(scenes) == 3, "benchmark requires exactly three scenes")
    results = [_score_scene(suite_root, scene) for scene in scenes]
    qualified = all(result["qualified_for_bounded_pose_depth_experiment"] for result in results)
    result = {
        "schema": "pale_mirror.automodel.synthetic_sv3d_multiview_benchmark_result.v2",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "adapter_id": "sv3d_p_orbit_fast_fp16_20",
        "camera_convention": {
            "sv3d_declared_azimuth_to_synthetic_ground_truth_yaw": SV3D_TO_SYNTHETIC_YAW,
            "calibration": "one global sign reversal verified against known gantry headings; no per-frame pose fitting",
        },
        "suite": {"file": "synthetic_multiview_suite.json", "sha256": sha256(suite_root / "synthetic_multiview_suite.json")},
        "limits": LIMITS,
        "scenes": results,
        "conclusion": "adapter_quality_gate_passed" if qualified else "adapter_quality_gate_rejected",
        "limitations": {
            "does_not_qualify_creature_likeness": True,
            "does_not_establish_real_camera_truth": True,
            "does_not_promote_generated_views": True,
            "requires_human_per_scene_contact_sheet_review": True,
        },
    }
    write_object(output, result)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--suite-directory", type=Path, required=True)
    arguments = parser.parse_args()
    print(json.dumps(evaluate(arguments.suite_directory), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
