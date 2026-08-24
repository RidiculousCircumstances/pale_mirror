#!/usr/bin/env python3
"""Run a bounded, non-canonical VGGT-Omega Collector depth/pose pilot.

This produces only poses, depth maps and a confidence-filtered point cloud.
It deliberately has no mesh-conversion, Blender mutation, PMMesh export or
runtime path.  The literal primary trace remains the sole likeness authority.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import platform
from pathlib import Path
from typing import Any

REVIEW_ACKNOWLEDGEMENT = "reviewed-for-vggt-pilot"
EXPECTED_SCHEMA = "pale_mirror_visuals.harvester_vggt_turntable_input.v1"
OFFICIAL_CHECKPOINT_SCHEMA = "pale_mirror.automodel.official_checkpoint_receipt.v1"
OFFICIAL_REPOSITORY = "facebook/VGGT-Omega"
OFFICIAL_FILENAME = "vggt_omega_1b_512.pt"
TURN_TABLE_REVIEW_SCHEMA = "pale_mirror.automodel.turntable_review_receipt.v1"
ROOT = Path(__file__).resolve().parents[2]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain an object")
    return value


def select_evenly(items: list[dict[str, Any]], count: int) -> list[dict[str, Any]]:
    if not 2 <= count <= len(items):
        raise ValueError(f"frame count must be between 2 and {len(items)}")
    if count == len(items):
        return items
    indices = [round(index * (len(items) - 1) / (count - 1)) for index in range(count)]
    return [items[index] for index in indices]


def load_pilot_inputs(
    manifest_path: Path,
    frame_count: int,
    acknowledgement: str,
    turntable_review_receipt: Path | None = None,
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    if acknowledgement != REVIEW_ACKNOWLEDGEMENT:
        raise ValueError(
            "The generated turntable requires a deliberate manual review acknowledgement: "
            f"--acknowledge-secondary-review {REVIEW_ACKNOWLEDGEMENT}"
        )
    manifest = load_json(manifest_path)
    if manifest.get("schema") != EXPECTED_SCHEMA:
        raise ValueError("Not a Pale Mirror Collector VGGT turntable manifest")
    if manifest.get("asset_id") != "biomass_collector":
        raise ValueError("VGGT pilot only accepts the Collector input")
    contract = manifest.get("acceptance_contract")
    if not isinstance(contract, dict) or contract.get("vggt_output_is_noncanonical_nonexportable") is not True:
        raise ValueError("Input manifest does not preserve the non-canonical VGGT contract")
    frames = manifest.get("frames")
    if not isinstance(frames, list) or len(frames) != manifest.get("frame_count"):
        raise ValueError("Input manifest has an invalid frame list")
    selected = select_evenly(frames, frame_count)
    for expected_index, frame in enumerate(frames):
        if not isinstance(frame, dict) or frame.get("index") != expected_index:
            raise ValueError("Input frame order is not stable")
        file_name, expected_hash = frame.get("file"), frame.get("sha256")
        if not isinstance(file_name, str) or not isinstance(expected_hash, str):
            raise ValueError("Input frame has no pinned file/hash")
        frame_path = manifest_path.parent / file_name
        if not frame_path.is_file() or sha256(frame_path) != expected_hash:
            raise ValueError(f"Input frame is missing or changed: {file_name}")
    primary = frames[0]
    if primary.get("source") != "literal_primary_trace_masked" or primary.get("authority") != "sole_likeness_anchor":
        raise ValueError("Frame 0 must be the literal trace-masked primary anchor")
    automodel_provenance = manifest.get("automodel_provenance")
    if automodel_provenance is not None:
        if not isinstance(automodel_provenance, dict):
            raise ValueError("Automodel VGGT manifest has invalid provenance")
        expected_review = automodel_provenance.get("turntable_review_receipt")
        if not isinstance(expected_review, dict) or turntable_review_receipt is None:
            raise ValueError("Automodel VGGT input requires its explicit turntable-review receipt")
        if not turntable_review_receipt.is_file() or sha256(turntable_review_receipt) != expected_review.get("sha256"):
            raise ValueError("turntable-review receipt is missing or does not match the prepared Automodel input")
        review = load_json(turntable_review_receipt)
        if (
            review.get("schema") != TURN_TABLE_REVIEW_SCHEMA
            or review.get("scope") != "research_only_noncanonical"
            or review.get("promotion_prohibited") is not True
            or review.get("decision") != "eligible_for_pose_depth_only"
        ):
            raise ValueError("turntable-review receipt does not permit a noncanonical pose/depth pilot")
    return manifest, selected


def unproject_depth(depth: Any, extrinsic: Any, intrinsic: Any) -> Any:
    import numpy as np

    values = np.asarray(depth)[..., 0]
    frame_count, height, width = values.shape
    y, x = np.meshgrid(np.arange(height), np.arange(width), indexing="ij")
    x = np.broadcast_to(x[None], (frame_count, height, width))
    y = np.broadcast_to(y[None], (frame_count, height, width))
    camera_points = np.stack(
        [
            (x - intrinsic[:, 0, 2][:, None, None]) / intrinsic[:, 0, 0][:, None, None] * values,
            (y - intrinsic[:, 1, 2][:, None, None]) / intrinsic[:, 1, 1][:, None, None] * values,
            values,
        ],
        axis=-1,
    )
    rotation = extrinsic[:, :3, :3]
    translation = extrinsic[:, :3, 3]
    return np.einsum(
        "sij,shwj->shwi",
        np.transpose(rotation, (0, 2, 1)),
        camera_points - translation[:, None, None, :],
    )


def point_cloud_from_predictions(
    points: Any,
    depth_confidence: Any,
    images: Any,
    confidence_percentile: float,
    maximum_points: int,
) -> tuple[Any, Any]:
    import numpy as np

    vertices = np.asarray(points).reshape(-1, 3)
    confidence = np.asarray(depth_confidence).reshape(-1)
    rgb = np.transpose(np.asarray(images), (0, 2, 3, 1)).reshape(-1, 3)
    colours = (rgb * 255).clip(0, 255).astype(np.uint8)
    mask = np.isfinite(vertices).all(axis=1) & np.isfinite(confidence) & (confidence > 1e-5)
    mask &= colours.sum(axis=1) >= 16  # same black-background removal rule as the upstream demo
    if mask.any():
        threshold = np.percentile(confidence[mask], confidence_percentile)
        mask &= confidence >= threshold
    vertices, colours = vertices[mask], colours[mask]
    if maximum_points > 0 and len(vertices) > maximum_points:
        indices = np.linspace(0, len(vertices) - 1, maximum_points).astype(np.int64)
        vertices, colours = vertices[indices], colours[indices]
    return vertices.astype(np.float32), colours


def write_ply(path: Path, vertices: Any, colours: Any) -> None:
    header = (
        "ply\nformat ascii 1.0\n"
        f"element vertex {len(vertices)}\n"
        "property float x\nproperty float y\nproperty float z\n"
        "property uchar red\nproperty uchar green\nproperty uchar blue\nend_header\n"
    )
    with path.open("w", encoding="ascii", newline="\n") as output:
        output.write(header)
        for vertex, colour in zip(vertices, colours, strict=True):
            output.write(
                f"{vertex[0]:.7g} {vertex[1]:.7g} {vertex[2]:.7g} "
                f"{int(colour[0])} {int(colour[1])} {int(colour[2])}\n"
            )


def load_tensor_state_dict(torch: Any, checkpoint: Path) -> dict[str, Any]:
    """Load only a plain tensor state dictionary from a checkpoint.

    This pilot must never deserialize arbitrary Python objects from a supplied
    checkpoint.  VGGT's published quick-start expects a plain state dict, so
    reject anything else rather than falling back to pickle-enabled loading.
    """
    state_dict = torch.load(checkpoint, map_location="cpu", weights_only=True)
    if not isinstance(state_dict, dict) or not state_dict:
        raise ValueError("VGGT checkpoint must contain a non-empty tensor state dictionary")
    if not all(isinstance(name, str) and isinstance(value, torch.Tensor) for name, value in state_dict.items()):
        raise ValueError("VGGT checkpoint contains a non-tensor state entry")
    return state_dict


def load_official_checkpoint_receipt(
    receipt_path: Path,
    checkpoint: Path,
    *,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    """Require the pinned official source before opening a VGGT checkpoint."""

    resolved_receipt = receipt_path.resolve()
    expected_root = (repository_root / "build" / "automodel").resolve()
    if not resolved_receipt.is_file() or not resolved_receipt.is_relative_to(expected_root):
        raise ValueError("official checkpoint receipt must be an existing build/automodel artifact")
    receipt = load_json(resolved_receipt)
    if receipt.get("schema") != OFFICIAL_CHECKPOINT_SCHEMA:
        raise ValueError("checkpoint receipt has an unsupported schema")
    if receipt.get("model_id") != "vggt_official" or receipt.get("scope") != "research_only_noncanonical" or receipt.get("promotion_prohibited") is not True:
        raise ValueError("checkpoint receipt is not a noncanonical official VGGT receipt")
    origin = receipt.get("official_origin")
    pinned = receipt.get("checkpoint")
    contract = receipt.get("contract")
    if (
        not isinstance(origin, dict)
        or origin.get("repository") != OFFICIAL_REPOSITORY
        or origin.get("filename") != OFFICIAL_FILENAME
        or not isinstance(pinned, dict)
        or pinned.get("sha256") != sha256(checkpoint)
        or pinned.get("byte_size") != checkpoint.stat().st_size
        or pinned.get("storage") != "private_local_host"
        or not isinstance(contract, dict)
        or contract.get("canonical_promotion_forbidden") is not True
        or contract.get("weights_not_copied_to_repository") is not True
    ):
        raise ValueError("checkpoint does not match the registered official VGGT receipt")
    return receipt


def run(
    manifest_path: Path,
    checkpoint: Path,
    output_dir: Path,
    frame_count: int,
    image_resolution: int,
    confidence_percentile: float,
    maximum_points: int,
    acknowledgement: str,
    official_checkpoint_receipt: Path,
    turntable_review_receipt: Path | None = None,
) -> dict[str, Any]:
    manifest, frames = load_pilot_inputs(
        manifest_path,
        frame_count,
        acknowledgement,
        turntable_review_receipt,
    )
    if not checkpoint.is_file():
        raise ValueError(f"VGGT checkpoint is missing: {checkpoint}")
    if not 0 <= confidence_percentile <= 100:
        raise ValueError("confidence percentile must be between 0 and 100")
    if maximum_points < 1:
        raise ValueError("maximum points must be positive")
    receipt = load_official_checkpoint_receipt(official_checkpoint_receipt, checkpoint)

    import torch
    import numpy as np
    from vggt_omega.models import VGGTOmega
    from vggt_omega.utils.load_fn import load_and_preprocess_images
    from vggt_omega.utils.pose_enc import encoding_to_camera

    if not torch.cuda.is_available():
        raise RuntimeError("VGGT-Omega pilot requires CUDA")
    if output_dir.exists():
        raise ValueError(f"VGGT output directory already exists; prepare a fresh immutable pilot: {output_dir}")
    output_dir.mkdir(parents=True)
    torch.cuda.empty_cache()
    torch.cuda.reset_peak_memory_stats()
    model = VGGTOmega().to("cuda").eval()
    model.load_state_dict(load_tensor_state_dict(torch, checkpoint))
    image_paths = [manifest_path.parent / str(frame["file"]) for frame in frames]
    images = load_and_preprocess_images(image_paths, mode="max_size", image_resolution=image_resolution).to("cuda")
    with torch.inference_mode():
        predictions = model(images)
    extrinsic, intrinsic = encoding_to_camera(predictions["pose_enc"], predictions["images"].shape[-2:])
    prediction_arrays = {
        "depth": predictions["depth"].detach().float().cpu().numpy()[0],
        "depth_conf": predictions["depth_conf"].detach().float().cpu().numpy()[0],
        "images": predictions["images"].detach().float().cpu().numpy()[0],
        "extrinsic": extrinsic.detach().float().cpu().numpy()[0],
        "intrinsic": intrinsic.detach().float().cpu().numpy()[0],
    }
    npz_path = output_dir / "predictions.npz"
    np.savez_compressed(npz_path, **prediction_arrays)
    points = unproject_depth(prediction_arrays["depth"], prediction_arrays["extrinsic"], prediction_arrays["intrinsic"])
    vertices, colours = point_cloud_from_predictions(
        points,
        prediction_arrays["depth_conf"],
        prediction_arrays["images"],
        confidence_percentile,
        maximum_points,
    )
    ply_path = output_dir / "point_cloud.ply"
    write_ply(ply_path, vertices, colours)
    peak_bytes = int(torch.cuda.max_memory_allocated())
    summary = {
        "schema": "pale_mirror_visuals.harvester_vggt_pilot_output.v1",
        "asset_id": manifest["asset_id"],
        "status": "complete_noncanonical_nonexportable",
        "input": {
            "manifest": str(manifest_path),
            "manifest_sha256": sha256(manifest_path),
            "checkpoint": str(checkpoint),
            "checkpoint_sha256": sha256(checkpoint),
            "official_checkpoint_receipt": {
                "file": str(official_checkpoint_receipt),
                "sha256": sha256(official_checkpoint_receipt),
                "origin": receipt["official_origin"],
            },
            "turntable_review_receipt": (
                {"file": str(turntable_review_receipt), "sha256": sha256(turntable_review_receipt)}
                if turntable_review_receipt is not None
                else None
            ),
            "frames": [
                {"index": frame["index"], "yaw_degrees": frame["yaw_degrees"], "sha256": frame["sha256"]}
                for frame in frames
            ],
        },
        "model": {"name": "VGGT-Omega-1B-512", "image_resolution": image_resolution, "preprocess_mode": "max_size"},
        "outputs": {
            "predictions": {"file": npz_path.name, "sha256": sha256(npz_path)},
            "point_cloud": {"file": ply_path.name, "sha256": sha256(ply_path), "point_count": int(len(vertices))},
        },
        "estimated_camera_poses": prediction_arrays["extrinsic"].tolist(),
        "runtime": {
            "torch": torch.__version__,
            "cuda": torch.version.cuda,
            "gpu": torch.cuda.get_device_name(0),
            "peak_cuda_mib": round(peak_bytes / (1024 * 1024), 1),
            "platform": platform.platform(),
        },
        "contract": {
            "generated_secondary_images_are_not_calibration": True,
            "point_cloud_is_not_a_mesh": True,
            "runtime_export_forbidden": True,
            "literal_primary_trace_remains_acceptance_authority": True,
        },
    }
    summary_path = output_dir / "manifest.json"
    summary_path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-manifest", required=True, type=Path)
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    parser.add_argument("--frame-count", type=int, default=12)
    parser.add_argument("--image-resolution", type=int, default=512)
    parser.add_argument("--confidence-percentile", type=float, default=20.0)
    parser.add_argument("--maximum-points", type=int, default=250_000)
    parser.add_argument("--acknowledge-secondary-review", required=True)
    parser.add_argument("--official-checkpoint-receipt", required=True, type=Path)
    parser.add_argument("--turntable-review-receipt", type=Path)
    arguments = parser.parse_args()
    print(
        json.dumps(
            run(
                arguments.input_manifest,
                arguments.checkpoint,
                arguments.output_dir,
                arguments.frame_count,
                arguments.image_resolution,
                arguments.confidence_percentile,
                arguments.maximum_points,
                arguments.acknowledge_secondary_review,
                arguments.official_checkpoint_receipt,
                arguments.turntable_review_receipt,
            ),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
