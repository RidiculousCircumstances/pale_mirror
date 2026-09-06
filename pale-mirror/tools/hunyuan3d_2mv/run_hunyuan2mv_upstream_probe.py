#!/usr/bin/env python3
"""Verify Pale Mirror's low-VRAM Hunyuan2mv loader on upstream example views.

This is deliberately a non-canonical diagnostic.  It receives the official
Hunyuan example's already aligned front/left/back (or front/left/back/right)
views, runs the same component loader and CUDA offload hooks used by the
Collector experiment, and writes a separate probe mesh.  A coherent result
means that a later Collector failure is in its conditioning evidence rather
than in our constrained loader.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import time

from run_collector_multiview import load_shape_pipeline_low_memory, prepare_shape_checkpoint


VIEW_ORDER = ("front", "left", "back", "right")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def upstream_views(root: Path) -> dict[str, str]:
    views: dict[str, str] = {}
    for view in VIEW_ORDER:
        image = root / f"{view}.png"
        if image.is_file():
            views[view] = str(image)
    if set(views) not in ({"front"}, {"front", "left"}, {"front", "left", "back"}, set(VIEW_ORDER)):
        raise ValueError(
            "An upstream probe needs canonical consecutive views: front, optional left, optional back, optional right."
        )
    return views


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--views-dir", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--model-repository", type=Path, required=True)
    parser.add_argument("--checkpoint-root", type=Path, required=True)
    parser.add_argument("--device", choices=("cuda", "cpu"), default="cuda")
    parser.add_argument("--steps", type=int, default=50)
    parser.add_argument("--octree-resolution", type=int, default=380)
    parser.add_argument("--num-chunks", type=int, default=20_000)
    parser.add_argument("--seed", type=int, default=12_345)
    arguments = parser.parse_args()
    if arguments.candidate.exists():
        raise ValueError("Refusing to overwrite an existing upstream probe output.")
    if arguments.steps < 1 or arguments.octree_resolution < 16 or arguments.num_chunks < 1:
        raise ValueError("Probe parameters must be positive and octree resolution must be at least 16.")

    import torch

    if arguments.device == "cuda" and not torch.cuda.is_available():
        raise RuntimeError("The CUDA probe requires an available CUDA device.")
    views = upstream_views(arguments.views_dir)
    checkpoint_root, checkpoint_files = prepare_shape_checkpoint(arguments.checkpoint_root)
    pipeline = load_shape_pipeline_low_memory(checkpoint_root, torch, device=arguments.device)
    if arguments.device == "cuda":
        pipeline.enable_model_cpu_offload()
        pipeline.device = torch.device("cuda")
        torch.cuda.reset_peak_memory_stats()

    started = time.monotonic()
    mesh = pipeline(
        image=views,
        num_inference_steps=arguments.steps,
        octree_resolution=arguments.octree_resolution,
        num_chunks=arguments.num_chunks,
        generator=torch.manual_seed(arguments.seed),
        output_type="trimesh",
    )[0]
    arguments.candidate.parent.mkdir(parents=True, exist_ok=True)
    mesh.export(arguments.candidate)
    record = {
        "schema": "pale_mirror.hunyuan2mv_upstream_probe.v1",
        "purpose": "loader/offload liveness only; never a Pale Mirror asset",
        "checkpoint": {"repository": "tencent/Hunyuan3D-2mv", "files": checkpoint_files},
        "views": {name: sha256(Path(path)) for name, path in views.items()},
        "parameters": {
            "steps": arguments.steps,
            "octree_resolution": arguments.octree_resolution,
            "num_chunks": arguments.num_chunks,
            "seed": arguments.seed,
        },
        "runtime": {
            "device": arguments.device,
            "elapsed_seconds": round(time.monotonic() - started, 2),
            "peak_cuda_mib": round(torch.cuda.max_memory_allocated() / 1024 / 1024, 2)
            if arguments.device == "cuda"
            else None,
        },
        "output": {"file": arguments.candidate.name, "sha256": sha256(arguments.candidate)},
        "runtime_export_forbidden": True,
    }
    (arguments.candidate.parent / "manifest.json").write_text(
        json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print(json.dumps(record, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
