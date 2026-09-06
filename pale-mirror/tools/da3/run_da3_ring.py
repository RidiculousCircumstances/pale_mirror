#!/usr/bin/env python3
"""Pinned local DA3-Base runner for an accepted eight-view Automodel ring.

It intentionally exports only NumPy pose/depth/confidence data, a bounded
diagnostic point cloud and normalized depth PNGs.  It does not ask DA3 to
export GLB/mesh assets and cannot access canonical Pale Mirror paths.
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

from contracts import AutomodelContractError, load_object, require_relative_build_path, sha256, validate_official_checkpoint_receipt, write_object  # noqa: E402
from pose_depth_ring import collect_pose_depth_evidence, load_accepted_geometry_input, validate_pose_depth_run_manifest  # noqa: E402
from ring_benchmark import validate_qualification  # noqa: E402


def _relative(path: Path, repository_root: Path, label: str) -> str:
    try:
        value = path.resolve().relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError(f"{label} must stay inside this repository") from exc
    return require_relative_build_path(value, label)


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
    target.parent.mkdir(parents=True, exist_ok=True)
    canvas = Image.new("RGB", image.size, (255, 255, 255))
    canvas.paste(image, mask=image.getchannel("A"))
    canvas.save(target)


def _depth_pngs(depth: Any, directory: Path) -> list[Path]:
    import numpy as np

    directory.mkdir(parents=True)
    outputs: list[Path] = []
    for index, values in enumerate(np.asarray(depth, dtype=np.float32)):
        finite = values[np.isfinite(values)]
        if finite.size == 0:
            raise AutomodelContractError("DA3 returned a non-finite depth map")
        lower, upper = np.percentile(finite, (2, 98))
        if upper <= lower:
            raise AutomodelContractError("DA3 returned depth without useful range")
        normal = np.clip((values - lower) / (upper - lower), 0.0, 1.0)
        target = directory / f"depth_{index:03d}.png"
        Image.fromarray((normal * 255).astype(np.uint8), mode="L").save(target)
        outputs.append(target)
    return outputs


def _write_point_cloud(path: Path, depth: Any, extrinsics: Any, intrinsics: Any, images: Any, maximum_points: int) -> int:
    """Write a bounded RGB point cloud, never a mesh, from the DA3 estimates."""

    import numpy as np

    all_vertices: list[Any] = []
    all_colours: list[Any] = []
    for index, values in enumerate(np.asarray(depth, dtype=np.float32)):
        height, width = values.shape
        y, x = np.mgrid[0:height, 0:width]
        intrinsic = np.asarray(intrinsics[index], dtype=np.float32)
        camera = np.stack(
            (
                (x - intrinsic[0, 2]) / intrinsic[0, 0] * values,
                (y - intrinsic[1, 2]) / intrinsic[1, 1] * values,
                values,
            ),
            axis=-1,
        ).reshape(-1, 3)
        extrinsic = np.asarray(extrinsics[index], dtype=np.float32)
        rotation, translation = extrinsic[:, :3], extrinsic[:, 3]
        world = (rotation.T @ (camera - translation).T).T
        rgb = np.asarray(images[index], dtype=np.uint8).reshape(-1, 3)
        finite = np.isfinite(world).all(axis=1) & np.isfinite(values.reshape(-1))
        all_vertices.append(world[finite])
        all_colours.append(rgb[finite])
    vertices = np.concatenate(all_vertices, axis=0)
    colours = np.concatenate(all_colours, axis=0)
    if len(vertices) > maximum_points:
        selected = np.linspace(0, len(vertices) - 1, maximum_points).astype(np.int64)
        vertices, colours = vertices[selected], colours[selected]
    with path.open("w", encoding="ascii", newline="\n") as output:
        output.write("ply\nformat ascii 1.0\n")
        output.write(f"element vertex {len(vertices)}\nproperty float x\nproperty float y\nproperty float z\n")
        output.write("property uchar red\nproperty uchar green\nproperty uchar blue\nend_header\n")
        for vertex, colour in zip(vertices, colours, strict=True):
            output.write(f"{vertex[0]:.7g} {vertex[1]:.7g} {vertex[2]:.7g} {int(colour[0])} {int(colour[1])} {int(colour[2])}\n")
    return int(len(vertices))


def run(
    run_manifest_path: Path,
    source_root: Path,
    checkpoint: Path,
    output_directory: Path,
    *,
    maximum_points: int = 250_000,
    repository_root: Path = ROOT,
) -> dict[str, Any]:
    manifest = load_object(run_manifest_path)
    validate_pose_depth_run_manifest(manifest)
    if manifest["adapter_id"] != "da3_base":
        raise AutomodelContractError("DA3 runner accepts only a da3_base prepared manifest")
    run_directory = repository_root / manifest["run_directory"]
    if run_manifest_path.resolve().parent != run_directory.resolve() or output_directory.resolve().parent != run_directory.resolve():
        raise AutomodelContractError("DA3 runner paths must remain directly below its prepared run directory")
    if output_directory.exists() or maximum_points < 1:
        raise AutomodelContractError("DA3 output directory must be fresh and point budget positive")
    expected_revision = manifest["provenance"]["adapter_revision"]
    if _source_revision(source_root) != expected_revision:
        raise AutomodelContractError("DA3 source revision does not match the prepared manifest")
    if not checkpoint.is_file() or checkpoint.suffix != ".safetensors":
        raise AutomodelContractError("DA3 requires a private local model.safetensors checkpoint")
    geometry_record = manifest.get("geometry_input")
    if not isinstance(geometry_record, dict):
        raise AutomodelContractError("DA3 manifest lacks its reviewed geometry input")
    geometry_path = repository_root / geometry_record["file"]
    if not geometry_path.is_file() or sha256(geometry_path) != geometry_record.get("sha256"):
        raise AutomodelContractError("DA3 geometry input is missing or changed")
    geometry = load_accepted_geometry_input(geometry_path, repository_root=repository_root)
    checkpoint_receipt = repository_root / manifest["provenance"]["checkpoint_receipt"]["file"]
    if not checkpoint_receipt.is_file() or sha256(checkpoint_receipt) != manifest["provenance"]["checkpoint_receipt"]["sha256"]:
        raise AutomodelContractError("DA3 checkpoint receipt is missing or changed")
    checkpoint_record = load_object(checkpoint_receipt)
    validate_official_checkpoint_receipt(checkpoint_record)
    if checkpoint_record.get("model_id") != "da3_base" or checkpoint_record.get("checkpoint", {}).get("sha256") != sha256(checkpoint):
        raise AutomodelContractError("DA3 checkpoint does not match its official prepared receipt")
    qualification_path = repository_root / manifest["ring_qualification"]["file"]
    if not qualification_path.is_file() or sha256(qualification_path) != manifest["ring_qualification"]["sha256"]:
        raise AutomodelContractError("DA3 synthetic eight-view qualification is missing or changed")
    qualification = load_object(qualification_path)
    validate_qualification(qualification, adapter_id="da3_base")
    if qualification.get("conclusion") != "adapter_role_qualified":
        raise AutomodelContractError("DA3 synthetic eight-view qualification is rejected")

    import numpy as np
    import torch
    from safetensors.torch import load_file

    sys.path.insert(0, str(source_root / "src"))
    from depth_anything_3.api import DepthAnything3

    if not torch.cuda.is_available():
        raise RuntimeError("DA3 ring diagnostics require CUDA on the registered private host")
    inputs = run_directory / "inputs"
    inputs.mkdir(parents=True)
    image_paths: list[str] = []
    staged_inputs: list[dict[str, Any]] = []
    for frame in geometry["frames"]:
        source = repository_root / frame["file"]
        target = inputs / f"{frame['yaw_degrees']:03d}.png"
        _white_background_copy(source, target)
        image_paths.append(str(target))
        staged_inputs.append({"yaw_degrees": frame["yaw_degrees"], "file": _relative(target, repository_root, "da3_input"), "sha256": sha256(target)})
    torch.cuda.empty_cache()
    torch.cuda.reset_peak_memory_stats()
    model = DepthAnything3(model_name="da3-base")
    model.load_state_dict(load_file(str(checkpoint), device="cpu"), strict=True)
    model = model.to("cuda").eval()
    with torch.inference_mode():
        prediction = model.inference(image_paths, process_res=504, process_res_method="upper_bound_resize", use_ray_pose=True)
    output_directory.mkdir()
    arrays = {
        "depth": np.asarray(prediction.depth, dtype=np.float32),
        "confidence": np.asarray(prediction.conf, dtype=np.float32),
        "extrinsics": np.asarray(prediction.extrinsics, dtype=np.float32),
        "intrinsics": np.asarray(prediction.intrinsics, dtype=np.float32),
        "images": np.asarray(prediction.processed_images, dtype=np.uint8),
    }
    arrays_path = output_directory / "predictions.npz"
    np.savez_compressed(arrays_path, **arrays)
    diagnostics = _depth_pngs(arrays["depth"], output_directory / "depth_diagnostics")
    cloud_path = output_directory / "point_cloud.ply"
    point_count = _write_point_cloud(cloud_path, arrays["depth"], arrays["extrinsics"], arrays["intrinsics"], arrays["images"], maximum_points)
    # All raw array output is one hash-pinned evidence input; the first depth
    # PNG is the portable per-view comparison image.  The final review compares
    # matching yaws, never a raw scale across providers.
    evidence = collect_pose_depth_evidence(
        run_manifest_path,
        {
            "camera_pose": arrays_path,
            "depth": diagnostics[0],
            "confidence": arrays_path,
            "point_cloud": cloud_path,
        },
        run_directory / "pose_depth_evidence.json",
        repository_root=repository_root,
    )
    summary = {
        "schema": "pale_mirror.automodel.da3_ring_output.v1",
        "asset_id": manifest["asset_id"],
        "adapter_id": "da3_base",
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "geometry_input": geometry_record,
        "checkpoint_sha256": sha256(checkpoint),
        "staged_inputs": staged_inputs,
        "outputs": {
            "predictions": {"file": arrays_path.name, "sha256": sha256(arrays_path)},
            "depth_diagnostics": [{"file": str(path.relative_to(output_directory)), "sha256": sha256(path)} for path in diagnostics],
            "point_cloud": {"file": cloud_path.name, "sha256": sha256(cloud_path), "point_count": point_count},
            "evidence": {"file": _relative(run_directory / "pose_depth_evidence.json", repository_root, "da3_evidence"), "sha256": sha256(run_directory / "pose_depth_evidence.json")},
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
    parser.add_argument("--output-directory", type=Path, required=True)
    parser.add_argument("--maximum-points", type=int, default=250_000)
    arguments = parser.parse_args()
    print(json.dumps(run(arguments.run_manifest, arguments.source_root, arguments.checkpoint, arguments.output_directory, maximum_points=arguments.maximum_points), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
