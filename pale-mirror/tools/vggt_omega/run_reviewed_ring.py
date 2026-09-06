#!/usr/bin/env python3
"""Pinned official VGGT-Omega runner for an accepted Automodel eight-view ring.

This is deliberately separate from the historic SV3D-derived Collector pilot.
It accepts only a user-reviewed still ring and writes pose/depth/confidence and
a bounded point cloud as MODEL_DERIVED diagnostics.  No mesh, Blender source,
PMMesh, resource, server or world path is available here.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys
from typing import Any

from PIL import Image


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools" / "automodel"))
sys.path.insert(0, str(ROOT / "tools" / "vggt_omega"))

from contracts import AutomodelContractError, load_object, require_relative_build_path, sha256, write_object  # noqa: E402
from pose_depth_ring import collect_pose_depth_evidence, load_accepted_geometry_input, validate_pose_depth_run_manifest  # noqa: E402
from ring_benchmark import validate_qualification  # noqa: E402
from run_collector_pilot import (  # noqa: E402
    load_official_checkpoint_receipt,
    load_tensor_state_dict,
    point_cloud_from_predictions,
    unproject_depth,
    write_ply,
)


def _relative(path: Path, repository_root: Path, label: str) -> str:
    try:
        relative = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"{label} must stay inside this repository") from exc
    return require_relative_build_path(relative, label)


def _source_revision(source_root: Path) -> str:
    result = subprocess.run(
        ["git", "-C", str(source_root), "rev-parse", "HEAD"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout.strip().lower()


def _white_background_copy(source: Path, target: Path) -> None:
    with Image.open(source) as raw:
        image = raw.convert("RGBA")
    canvas = Image.new("RGB", image.size, (255, 255, 255))
    canvas.paste(image, mask=image.getchannel("A"))
    canvas.save(target)


def _write_depth_diagnostics(depth: Any, directory: Path) -> list[Path]:
    import numpy as np

    directory.mkdir(parents=True)
    outputs: list[Path] = []
    for index, raw in enumerate(np.asarray(depth, dtype=np.float32)):
        values = raw[..., 0] if raw.ndim == 3 else raw
        finite = values[np.isfinite(values)]
        if finite.size == 0:
            raise AutomodelContractError("VGGT returned non-finite depth")
        lower, upper = np.percentile(finite, (2, 98))
        if upper <= lower:
            raise AutomodelContractError("VGGT returned depth without usable range")
        normal = np.clip((values - lower) / (upper - lower), 0.0, 1.0)
        target = directory / f"depth_{index:03d}.png"
        Image.fromarray((normal * 255).astype(np.uint8), mode="L").save(target)
        outputs.append(target)
    return outputs


def run(
    run_manifest_path: Path,
    source_root: Path,
    checkpoint: Path,
    official_checkpoint_receipt: Path,
    output_directory: Path,
    *,
    image_resolution: int = 512,
    confidence_percentile: float = 20.0,
    maximum_points: int = 250_000,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    manifest = load_object(run_manifest_path)
    validate_pose_depth_run_manifest(manifest)
    if manifest["adapter_id"] != "vggt_official":
        raise AutomodelContractError("VGGT reviewed-ring runner accepts only a vggt_official prepared manifest")
    run_directory = repository_root / manifest["run_directory"]
    if run_manifest_path.resolve().parent != run_directory.resolve() or output_directory.resolve().parent != run_directory.resolve():
        raise AutomodelContractError("VGGT runner paths must remain directly below its prepared run directory")
    if output_directory.exists() or image_resolution not in {512} or not 0.0 <= confidence_percentile <= 100.0 or maximum_points < 1:
        raise AutomodelContractError("VGGT output must be fresh with its fixed 512px profile and bounded point budget")
    if _source_revision(source_root) != manifest["provenance"]["adapter_revision"]:
        raise AutomodelContractError("VGGT source revision does not match the prepared manifest")
    if not checkpoint.is_file():
        raise AutomodelContractError("official VGGT checkpoint is missing")
    receipt = load_official_checkpoint_receipt(official_checkpoint_receipt, checkpoint, repository_root=repository_root)
    if sha256(official_checkpoint_receipt) != manifest["provenance"]["checkpoint_receipt"]["sha256"]:
        raise AutomodelContractError("VGGT official checkpoint receipt does not match the prepared manifest")
    qualification_path = repository_root / manifest["ring_qualification"]["file"]
    if not qualification_path.is_file() or sha256(qualification_path) != manifest["ring_qualification"]["sha256"]:
        raise AutomodelContractError("VGGT synthetic eight-view qualification is missing or changed")
    qualification = load_object(qualification_path)
    validate_qualification(qualification, adapter_id="vggt_official")
    if qualification.get("conclusion") != "adapter_role_qualified":
        raise AutomodelContractError("VGGT synthetic eight-view qualification is rejected")
    geometry_record = manifest.get("geometry_input")
    if not isinstance(geometry_record, dict):
        raise AutomodelContractError("VGGT manifest lacks reviewed geometry input")
    geometry_path = repository_root / geometry_record["file"]
    if not geometry_path.is_file() or sha256(geometry_path) != geometry_record.get("sha256"):
        raise AutomodelContractError("VGGT geometry input is missing or changed")
    geometry = load_accepted_geometry_input(geometry_path, repository_root=repository_root)

    import numpy as np
    import torch

    if not torch.cuda.is_available():
        raise RuntimeError("VGGT reviewed-ring diagnostics require CUDA on the registered private host")
    sys.path.insert(0, str(source_root))
    from vggt_omega.models import VGGTOmega
    from vggt_omega.utils.load_fn import load_and_preprocess_images
    from vggt_omega.utils.pose_enc import encoding_to_camera

    inputs = run_directory / "inputs"
    inputs.mkdir(parents=True)
    staged_inputs = []
    image_paths: list[Path] = []
    for frame in geometry["frames"]:
        source = repository_root / frame["file"]
        target = inputs / f"{frame['yaw_degrees']:03d}.png"
        _white_background_copy(source, target)
        image_paths.append(target)
        staged_inputs.append({"yaw_degrees": frame["yaw_degrees"], "file": _relative(target, repository_root, "vggt_input"), "sha256": sha256(target)})
    output_directory.mkdir()
    torch.cuda.empty_cache()
    torch.cuda.reset_peak_memory_stats()
    model = VGGTOmega().to("cuda").eval()
    model.load_state_dict(load_tensor_state_dict(torch, checkpoint))
    images = load_and_preprocess_images(image_paths, mode="max_size", image_resolution=image_resolution).to("cuda")
    with torch.inference_mode():
        predictions = model(images)
    extrinsic, intrinsic = encoding_to_camera(predictions["pose_enc"], predictions["images"].shape[-2:])
    arrays = {
        "depth": predictions["depth"].detach().float().cpu().numpy()[0],
        "confidence": predictions["depth_conf"].detach().float().cpu().numpy()[0],
        "images": predictions["images"].detach().float().cpu().numpy()[0],
        "extrinsics": extrinsic.detach().float().cpu().numpy()[0],
        "intrinsics": intrinsic.detach().float().cpu().numpy()[0],
    }
    arrays_path = output_directory / "predictions.npz"
    np.savez_compressed(arrays_path, **arrays)
    diagnostics = _write_depth_diagnostics(arrays["depth"], output_directory / "depth_diagnostics")
    points = unproject_depth(arrays["depth"], arrays["extrinsics"], arrays["intrinsics"])
    vertices, colours = point_cloud_from_predictions(points, arrays["confidence"], arrays["images"], confidence_percentile, maximum_points)
    cloud_path = output_directory / "point_cloud.ply"
    write_ply(cloud_path, vertices, colours)
    evidence = collect_pose_depth_evidence(
        run_manifest_path,
        {"camera_pose": arrays_path, "depth": diagnostics[0], "confidence": arrays_path, "point_cloud": cloud_path},
        run_directory / "pose_depth_evidence.json",
        repository_root=repository_root,
    )
    summary = {
        "schema": "pale_mirror.automodel.vggt_ring_output.v1",
        "asset_id": manifest["asset_id"],
        "adapter_id": "vggt_official",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "geometry_input": geometry_record,
        "official_origin": receipt["official_origin"],
        "staged_inputs": staged_inputs,
        "outputs": {
            "predictions": {"file": arrays_path.name, "sha256": sha256(arrays_path)},
            "depth_diagnostics": [{"file": str(path.relative_to(output_directory)), "sha256": sha256(path)} for path in diagnostics],
            "point_cloud": {"file": cloud_path.name, "sha256": sha256(cloud_path), "point_count": int(len(vertices))},
            "evidence": {"file": _relative(run_directory / "pose_depth_evidence.json", repository_root, "vggt_evidence"), "sha256": sha256(run_directory / "pose_depth_evidence.json")},
        },
        "runtime": {"torch": torch.__version__, "cuda": torch.version.cuda, "gpu": torch.cuda.get_device_name(0), "peak_cuda_mib": round(torch.cuda.max_memory_allocated() / 1024 / 1024, 1)},
        "contract": {"point_cloud_is_not_a_mesh": True, "no_glb_or_mesh_export": True, "canonical_promotion_forbidden": True},
    }
    write_object(output_directory / "manifest.json", summary)
    return summary


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-manifest", type=Path, required=True)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--official-checkpoint-receipt", type=Path, required=True)
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--image-resolution", type=int, default=512)
    parser.add_argument("--confidence-percentile", type=float, default=20.0)
    parser.add_argument("--maximum-points", type=int, default=250_000)
    arguments = parser.parse_args()
    print(json.dumps(run(arguments.run_manifest, arguments.source_root, arguments.checkpoint, arguments.official_checkpoint_receipt, arguments.output_directory, image_resolution=arguments.image_resolution, confidence_percentile=arguments.confidence_percentile, maximum_points=arguments.maximum_points), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
