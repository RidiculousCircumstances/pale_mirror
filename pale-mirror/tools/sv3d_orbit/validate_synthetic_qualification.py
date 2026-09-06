#!/usr/bin/env python3
"""Validate one completed synthetic SV3D technical qualification run.

This check qualifies only a pinned runner profile's ability to create the
expected complete raster sequence. It deliberately has no creature-reference
input and cannot assess likeness, camera truth, or promotion eligibility.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

import cv2
import numpy as np
from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))

from contracts import AutomodelContractError, load_object, sha256, write_object  # noqa: E402


EXPECTED_YAWS = tuple(range(18, 361, 18))


def _resolve_run(path: Path) -> Path:
    run = path.resolve()
    try:
        relative = run.relative_to((ROOT / "build" / "automodel").resolve())
    except ValueError as exc:
        raise AutomodelContractError("synthetic qualification must remain under build/automodel") from exc
    if not relative.parts:
        raise AutomodelContractError("build/automodel itself is not a run directory")
    return run


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise AutomodelContractError(message)


def _load_existing_capture_receipt(path: Path) -> dict[str, object]:
    """Read the single pre-BOM-fix Windows evidence artifact compatibly.

    New capture writers use BOM-free UTF-8.  The fallback is deliberately
    limited to an already immutable host-generated capture receipt so it does
    not weaken the ordinary Automodel JSON contract.
    """
    try:
        value = json.loads(path.read_text(encoding="utf-8-sig"))
    except (OSError, json.JSONDecodeError) as exc:
        raise AutomodelContractError(f"cannot load Windows capture receipt {path}: {exc}") from exc
    _require(isinstance(value, dict), "Windows capture receipt must be a JSON object")
    return value


def _video_facts(path: Path) -> dict[str, int]:
    capture = cv2.VideoCapture(str(path))
    _require(capture.isOpened(), "raw SV3D video cannot be opened")
    count = 0
    width = 0
    height = 0
    try:
        while True:
            ok, frame = capture.read()
            if not ok:
                break
            _require(frame.ndim == 3 and frame.shape[2] == 3, "raw video frame must be BGR")
            height, width = frame.shape[:2]
            _require(width == 576 and height == 576, "raw video frames must stay 576 square")
            _require(np.isfinite(frame).all(), "raw video contains non-finite pixels")
            count += 1
    finally:
        capture.release()
    _require(count == 21, f"raw SV3D video must contain exactly 21 frames; got {count}")
    return {"frame_count": count, "width": width, "height": height}


def _frame_facts(directory: Path) -> list[dict[str, object]]:
    expected_names = [f"frame_{yaw:03d}.png" for yaw in EXPECTED_YAWS]
    actual = sorted(path.name for path in directory.glob("*.png"))
    _require(actual == expected_names, "synthetic orbit must contain exactly the twenty declared yaw frames")
    records = []
    for yaw, name in zip(EXPECTED_YAWS, expected_names, strict=True):
        path = directory / name
        with Image.open(path) as image:
            rgba = np.asarray(image.convert("RGBA"))
        _require(rgba.shape == (576, 576, 4), f"{name} must be 576-square RGBA")
        _require(np.isfinite(rgba).all(), f"{name} contains non-finite pixels")
        _require(int(rgba.var()) > 0, f"{name} is a uniform corrupted frame")
        records.append({"yaw_degrees": yaw, "file": f"outputs/nonprimary_orbit/{name}", "sha256": sha256(path)})
    return records


def validate(run_directory: Path) -> dict[str, object]:
    run = _resolve_run(run_directory)
    output = run / "synthetic_execution_qualification.json"
    if output.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable qualification receipt: {output}")
    synthetic = load_object(run / "synthetic_qualification_receipt.json")
    manifest = load_object(run / "run_manifest.json")
    capture = _load_existing_capture_receipt(run / "execution_capture_receipt.json")
    view_set = load_object(run / "view_set.json")
    _require(synthetic.get("asset_id") == "synthetic_sv3d_signal_tower", "unexpected synthetic asset")
    _require(manifest.get("adapter_id") == "sv3d_p_orbit_fast_fp16_20", "unexpected adapter")
    _require(capture.get("outcome") == "success", "SV3D capture did not succeed")
    _require(capture.get("execution_profile") == "fast_fp16_20", "unexpected execution profile")
    _require(capture.get("memory_profile") == "low_vram_fp16_conditioner_cpu_offload", "unexpected memory profile")
    _require(capture.get("decoding_t") == 1, "unexpected decoding_t")
    frames = view_set.get("frames")
    _require(isinstance(frames, list) and len(frames) == 21, "ViewSet must contain literal primary plus twenty frames")
    _require(frames[0].get("tier") == "PRIMARY_TRACE", "ViewSet must retain literal primary")
    _require([entry.get("declared_yaw_degrees") for entry in frames[1:]] == [float(yaw) for yaw in EXPECTED_YAWS], "ViewSet yaw order is invalid")
    _require(all(entry.get("tier") == "MODEL_DERIVED" for entry in frames[1:]), "generated frames must stay model-derived")
    videos = sorted((run / "outputs" / "raw_video").glob("*.mp4"))
    _require(len(videos) == 1, "synthetic run must contain exactly one raw MP4")
    video = videos[0]
    video_facts = _video_facts(video)
    frame_records = _frame_facts(run / "outputs" / "nonprimary_orbit")
    receipt = {
        "schema": "pale_mirror.automodel.synthetic_sv3d_execution_qualification.v1",
        "asset_id": synthetic["asset_id"],
        "adapter_id": manifest["adapter_id"],
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "result": "technical_execution_passed",
        "capture_receipt": {"file": "execution_capture_receipt.json", "sha256": sha256(run / "execution_capture_receipt.json")},
        "view_set": {"file": "view_set.json", "sha256": sha256(run / "view_set.json")},
        "raw_video": {"file": "outputs/raw_video/" + video.name, "sha256": sha256(video), **video_facts},
        "nonprimary_frames": frame_records,
        "limitations": {
            "does_not_qualify_creature_likeness": True,
            "does_not_establish_camera_truth": True,
            "does_not_promote_generated_views": True,
        },
    }
    write_object(output, receipt)
    return receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-directory", type=Path, required=True)
    arguments = parser.parse_args()
    print(json.dumps(validate(arguments.run_directory), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
