#!/usr/bin/env python3
"""Known-scene qualification for the reviewed eight-view pose/depth adapters.

The fixture is deliberately synthetic and unlike the Collector.  It can prove
only that a pinned adapter keeps an ordered, asymmetric eight-camera ring and
returns usable diagnostics.  It cannot award creature likeness or promote a
generated asset.
"""

from __future__ import annotations

import argparse
import json
from math import cos, pi, sin
from pathlib import Path
from statistics import fmean, median
from typing import Any

from PIL import Image, ImageDraw

from contracts import AutomodelContractError, EvidenceTier, require_identifier, require_relative_build_path, require_revision, require_sha256, sha256, write_object
from multiview_ring import RING_YAWS


ROOT = Path(__file__).resolve().parents[2]
LIMITS = {
    "maximum_median_yaw_error_degrees": 12.0,
    "maximum_p90_yaw_error_degrees": 18.0,
    "minimum_mean_mask_iou": 0.90,
    "maximum_mean_relative_depth_error": 0.15,
    "required_dorsal_sac_count": 6,
    "required_leg_pair_count": 5,
}


def _relative(path: Path, repository_root: Path, label: str) -> str:
    try:
        relative = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"{label} must stay inside this repository") from exc
    return require_relative_build_path(relative, label)


def _circular_error(actual: float, expected: float) -> float:
    return abs((actual - expected + 180.0) % 360.0 - 180.0)


def _p90(values: list[float]) -> float:
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, round((len(ordered) - 1) * 0.9))]


def _draw_fixture_view(yaw: int) -> Image.Image:
    """Render an asymmetric ten-support/six-sac test object without a DCC.

    This is not creature art.  The camera-dependent offsets are sufficient for
    an adapter qualification corpus: a mirrored/reordered ring cannot meet its
    yaw/anatomy observations by accident.
    """

    size = 1024
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    angle = yaw * pi / 180.0
    side = cos(angle)
    front = sin(angle)
    cx, cy = 512, 500
    body_w = round(255 + 74 * abs(side))
    body_h = round(132 + 20 * abs(front))
    draw.ellipse((cx - body_w, cy - body_h, cx + body_w, cy + body_h), fill=(121, 93, 67, 255), outline=(54, 37, 28, 255), width=12)
    # Six deliberately uneven dorsal forms, ordered left-to-right in the view.
    for index in range(6):
        x = cx - 180 + index * 72 + round(side * (index - 2.5) * 15)
        y = cy - body_h - 32 - round(abs(front) * ((index % 3) * 8))
        radius_x = 40 + (index % 2) * 9
        radius_y = 55 + ((index + 1) % 3) * 8
        draw.ellipse((x - radius_x, y - radius_y, x + radius_x, y + radius_y), fill=(151, 112, 79, 255), outline=(54, 37, 28, 255), width=9)
    # Five bilateral pairs: near and far member overlap changes with yaw.
    for pair in range(5):
        root_x = cx - 175 + pair * 86
        for member, direction in enumerate((-1, 1)):
            depth_shift = round(direction * side * 28)
            end_x = root_x + depth_shift + direction * (38 + pair * 3)
            end_y = cy + body_h + 115 + abs(pair - 2) * 18 + round(front * direction * 24)
            draw.line((root_x + depth_shift, cy + body_h // 2, end_x, end_y), fill=(60, 43, 32, 255), width=32)
            draw.ellipse((end_x - 23, end_y - 16, end_x + 27, end_y + 20), fill=(89, 63, 45, 255))
    # A unique mantle and tail make the front/back direction measurable.
    mantle_x = cx - round(front * 205)
    draw.polygon(((mantle_x - 88, cy - 36), (mantle_x + 70, cy - 28), (mantle_x + 38, cy + 188), (mantle_x - 102, cy + 146)), fill=(174, 126, 82, 255), outline=(54, 37, 28, 255), width=10)
    tail_x = cx + round(front * 230)
    draw.line((tail_x, cy + 26, tail_x + round(side * 95), cy - 150), fill=(69, 48, 35, 255), width=28)
    return canvas


def build_fixture(output_directory: Path, *, repository_root: Path = ROOT) -> dict[str, Any]:
    if output_directory.exists():
        raise AutomodelContractError("known eight-view fixture directory must be fresh")
    _relative(output_directory, repository_root, "fixture_directory")
    output_directory.mkdir(parents=True)
    frames = []
    for yaw in RING_YAWS:
        target = output_directory / f"{yaw:03d}.png"
        _draw_fixture_view(yaw).save(target)
        frames.append({"yaw_degrees": yaw, "file": target.name, "sha256": sha256(target)})
    fixture = {
        "schema": "pale_mirror.automodel.known_8_view_fixture.v1",
        "scope": "research_only_noncanonical",
        "purpose": "adapter_role_qualification_only_not_creature_likeness",
        "camera_ring": {"yaws": list(RING_YAWS), "background": "transparent_rgba", "size_px": [1024, 1024]},
        "ground_truth": {"dorsal_sac_count": 6, "leg_pair_count": 5, "asymmetric_mantle_and_tail": True},
        "frames": frames,
    }
    write_object(output_directory / "fixture.json", fixture)
    return fixture


def evaluate_observations(observations: list[dict[str, Any]]) -> dict[str, Any]:
    if not isinstance(observations, list) or len(observations) != len(RING_YAWS):
        raise AutomodelContractError("8-view qualification requires exactly eight ordered observations")
    yaw_errors: list[float] = []
    mask_ious: list[float] = []
    depths: list[float] = []
    previous: float | None = None
    monotonic = True
    for expected, observation in zip(RING_YAWS, observations, strict=True):
        if not isinstance(observation, dict) or observation.get("expected_yaw_degrees") != expected:
            raise AutomodelContractError("qualification observations must retain the fixture yaw order")
        actual = observation.get("reported_yaw_degrees")
        iou = observation.get("mask_iou")
        depth = observation.get("relative_depth_error")
        if not all(isinstance(value, (int, float)) for value in (actual, iou, depth)):
            raise AutomodelContractError("qualification observations require numeric pose, mask and depth metrics")
        if not 0 <= iou <= 1 or depth < 0:
            raise AutomodelContractError("qualification observations have an invalid metric")
        if observation.get("dorsal_sac_count") != 6 or observation.get("leg_pair_count") != 5:
            raise AutomodelContractError("qualification must retain six sacs and five bilateral leg pairs")
        unwrapped = float(actual)
        if previous is not None:
            while unwrapped <= previous:
                unwrapped += 360.0
            if unwrapped - previous > 90.0:
                monotonic = False
        previous = unwrapped
        yaw_errors.append(_circular_error(float(actual), expected))
        mask_ious.append(float(iou))
        depths.append(float(depth))
    metrics = {
        "median_yaw_error_degrees": median(yaw_errors),
        "p90_yaw_error_degrees": _p90(yaw_errors),
        "mean_mask_iou": fmean(mask_ious),
        "mean_relative_depth_error": fmean(depths),
        "monotonic_yaw_order": monotonic,
    }
    qualified = (
        metrics["median_yaw_error_degrees"] <= LIMITS["maximum_median_yaw_error_degrees"]
        and metrics["p90_yaw_error_degrees"] <= LIMITS["maximum_p90_yaw_error_degrees"]
        and metrics["mean_mask_iou"] >= LIMITS["minimum_mean_mask_iou"]
        and metrics["mean_relative_depth_error"] <= LIMITS["maximum_mean_relative_depth_error"]
        and monotonic
    )
    return {"schema": "pale_mirror.automodel.known_8_view_result.v1", "metrics": metrics, "limits": LIMITS, "qualified_for_model_derived_geometry": qualified}


def validate_qualification(value: dict[str, Any], *, adapter_id: str | None = None) -> None:
    if value.get("schema") != "pale_mirror.automodel.ring_adapter_qualification.v1":
        raise AutomodelContractError("ring qualification has an unsupported schema")
    recorded_adapter = require_identifier(value.get("adapter_id"), "ring_qualification.adapter_id")
    if adapter_id is not None and recorded_adapter != adapter_id:
        raise AutomodelContractError("ring qualification belongs to a different adapter")
    if value.get("scope") != "research_only_noncanonical" or value.get("authority") != EvidenceTier.MODEL_DERIVED.value:
        raise AutomodelContractError("ring qualification must remain noncanonical MODEL_DERIVED research")
    fixture = value.get("fixture")
    if not isinstance(fixture, dict):
        raise AutomodelContractError("ring qualification requires pinned synthetic fixture provenance")
    require_relative_build_path(fixture.get("file"), "ring_qualification.fixture.file")
    require_sha256(fixture.get("sha256"), "ring_qualification.fixture.sha256")
    result = value.get("result")
    if not isinstance(result, dict) or result.get("schema") != "pale_mirror.automodel.known_8_view_result.v1" or not isinstance(result.get("qualified_for_model_derived_geometry"), bool):
        raise AutomodelContractError("ring qualification requires an explicit synthetic 8-view result")
    if value.get("conclusion") not in {"adapter_role_qualified", "adapter_role_rejected"}:
        raise AutomodelContractError("ring qualification requires an explicit conclusion")
    if (value["conclusion"] == "adapter_role_qualified") != result["qualified_for_model_derived_geometry"]:
        raise AutomodelContractError("ring qualification conclusion must match its measured result")
    require_revision(value.get("adapter_revision"), "ring_qualification.adapter_revision")


def write_qualification(
    adapter_id: str,
    adapter_revision: str,
    fixture_path: Path,
    observations_path: Path,
    output_path: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    fixture = json.loads(fixture_path.read_text(encoding="utf-8"))
    if fixture.get("schema") != "pale_mirror.automodel.known_8_view_fixture.v1":
        raise AutomodelContractError("qualification requires the known eight-view fixture")
    source = json.loads(observations_path.read_text(encoding="utf-8"))
    if source.get("schema") != "pale_mirror.automodel.known_8_view_observations.v1" or source.get("adapter_id") != adapter_id:
        raise AutomodelContractError("qualification observations must identify the same adapter")
    result = evaluate_observations(source.get("observations"))
    receipt = {
        "schema": "pale_mirror.automodel.ring_adapter_qualification.v1",
        "adapter_id": require_identifier(adapter_id, "adapter_id"),
        "adapter_revision": require_revision(adapter_revision, "adapter_revision"),
        "scope": "research_only_noncanonical",
        "authority": EvidenceTier.MODEL_DERIVED.value,
        "fixture": {"file": _relative(fixture_path, repository_root, "fixture"), "sha256": sha256(fixture_path)},
        "observations": {"file": _relative(observations_path, repository_root, "observations"), "sha256": sha256(observations_path)},
        "result": result,
        "conclusion": "adapter_role_qualified" if result["qualified_for_model_derived_geometry"] else "adapter_role_rejected",
        "contract": {"qualifies_pose_depth_role_only": True, "does_not_measure_creature_likeness": True, "does_not_promote_views_or_meshes": True},
    }
    validate_qualification(receipt)
    if output_path.exists():
        raise AutomodelContractError("refusing to overwrite immutable ring qualification")
    _relative(output_path, repository_root, "ring_qualification")
    write_object(output_path, receipt)
    return receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    fixture = commands.add_parser("build-fixture")
    fixture.add_argument("--output-directory", type=Path, required=True)
    qualify = commands.add_parser("write-qualification")
    qualify.add_argument("--adapter", required=True)
    qualify.add_argument("--adapter-revision", required=True)
    qualify.add_argument("--fixture", type=Path, required=True)
    qualify.add_argument("--observations", type=Path, required=True)
    qualify.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    if arguments.command == "build-fixture":
        result = build_fixture(arguments.output_directory)
    else:
        result = write_qualification(arguments.adapter, arguments.adapter_revision, arguments.fixture, arguments.observations, arguments.output)
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
