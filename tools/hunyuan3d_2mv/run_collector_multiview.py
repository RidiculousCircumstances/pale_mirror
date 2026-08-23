#!/usr/bin/env python3
"""Run one fixed, shape-only Hunyuan3D-2mv Collector volume proposal."""

from __future__ import annotations

import argparse
import gc
import hashlib
import json
from pathlib import Path
import time


ASSET_ID = "biomass_collector"
TRIALS = {
    # Historic diagnostic run.  Its output remains rejected because it used
    # only uncalibrated generated views and a shortened denoising schedule.
    "hunyuan2mv_v01": {
        "bakeoff_id": "collector_multiview_bakeoff_v01",
        "inference_steps": 20,
        "octree_resolution": 256,
        "num_chunks": 8_000,
    },
    # Primary-anchored diagnostic run.  The literal source image is prepared
    # with the locked trace and occupies the near-side slot; the remaining
    # slots are a separately reviewed canonical turntable.  It deliberately
    # uses Hunyuan's ordinary 50-step schedule.
    "hunyuan2mv_v02_primary_anchored": {
        "bakeoff_id": "collector_multiview_bakeoff_v02_primary_anchored",
        "inference_steps": 50,
        "octree_resolution": 256,
        "num_chunks": 8_000,
    },
    "hunyuan2mv_v03_primary_front": {
        "bakeoff_id": "collector_multiview_bakeoff_v03_primary_front",
        "inference_steps": 50,
        "octree_resolution": 256,
        "num_chunks": 8_000,
    },
    # A one-off controlled comparison: it preserves the literal primary in
    # Hunyuan's front slot and replaces only the secondary turntable proxies.
    # The candidate remains a non-exportable hidden-volume proposal.
    "hunyuan2mv_v04_calibrated_secondary": {
        "bakeoff_id": "collector_multiview_bakeoff_v04_calibrated_secondary",
        "inference_steps": 50,
        "octree_resolution": 256,
        "num_chunks": 8_000,
    },
    "hunyuan2mv_v05_canonical_turntable": {
        "bakeoff_id": "collector_multiview_bakeoff_v05_canonical_turntable",
        "inference_steps": 50,
        "octree_resolution": 256,
        "num_chunks": 8_000,
    },
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def load_input(path: Path) -> dict[str, object]:
    data = json.loads(path.read_text(encoding="utf-8"))
    candidate_id = data.get("candidate_id")
    if (
        data.get("schema") != "pale_mirror.hunyuan2mv_input.v1"
        or data.get("asset_id") != ASSET_ID
        or not isinstance(candidate_id, str)
        or candidate_id not in TRIALS
        or data.get("bakeoff_id") != TRIALS[candidate_id]["bakeoff_id"]
        or data.get("runtime_export_forbidden") is not True
    ):
        raise ValueError("The Hunyuan2mv input manifest is not a pinned Collector trial contract.")
    views = data.get("views")
    if not isinstance(views, dict) or set(views) != {"front", "left", "back", "right"}:
        raise ValueError("The fixed Hunyuan2mv trial requires exactly front/left/back/right inputs.")
    for slot, entry in views.items():
        if not isinstance(entry, dict) or not isinstance(entry.get("file"), str) or not isinstance(entry.get("sha256"), str):
            raise ValueError(f"The {slot} view has invalid provenance.")
        view = path.parent / entry["file"]
        if not view.is_file() or sha256(view) != entry["sha256"]:
            raise ValueError(f"The {slot} view differs from its pinned input manifest.")
    return data


def prepare_shape_checkpoint(root: Path) -> tuple[Path, dict[str, str]]:
    """Fetch exactly the two files that the shape pipeline reads.

    Hunyuan's convenience loader uses ``snapshot_download`` with a broad
    subfolder glob and downloads both mutually exclusive ``.ckpt`` and
    ``.safetensors`` variants.  The fixed trial only permits safetensors, so
    stage the exact local pair before constructing the official pipeline.
    """
    from huggingface_hub import hf_hub_download

    checkpoint = root / "hunyuan3d-dit-v2-mv"
    required = ("config.yaml", "model.fp16.safetensors")
    paths: dict[str, Path] = {}
    for name in required:
        path = Path(
            hf_hub_download(
                repo_id="tencent/Hunyuan3D-2mv",
                filename=f"hunyuan3d-dit-v2-mv/{name}",
                local_dir=root,
            )
        )
        expected = checkpoint / name
        if path.resolve() != expected.resolve() or not expected.is_file():
            raise ValueError(f"The exact Hunyuan2mv {name} checkpoint file was not staged locally.")
        paths[name] = expected
    return root, {name: sha256(path) for name, path in paths.items()}


def load_shape_pipeline_low_memory(checkpoint_root: Path, torch, *, device: str):
    """Load the three official components without retaining two full copies.

    The upstream convenience loader first keeps every safetensor in one dict,
    builds all modules as fp32, and only then converts them to fp16.  That
    transiently exceeds the private Windows host's 16 GiB RAM.  This fixed
    loader consumes one top-level component at a time, converts its module
    before state transfer, releases its source tensors, and otherwise invokes
    the same official config/component constructors.  It is not a model or
    checkpoint substitution.
    """
    import safetensors.torch
    import yaml
    from hy3dgen.shapegen import Hunyuan3DDiTFlowMatchingPipeline
    from hy3dgen.shapegen.pipelines import instantiate_from_config

    if device not in {"cpu", "cuda"}:
        raise ValueError(f"Unsupported Hunyuan2mv device: {device}.")
    # CPU uses fp32 because the fixed upstream dependency stack does not
    # guarantee every shape-generation kernel for CPU fp16.  CUDA retains the
    # original fp16 + sequential offload path.
    dtype = torch.float16 if device == "cuda" else torch.float32
    module_root = checkpoint_root / "hunyuan3d-dit-v2-mv"
    with (module_root / "config.yaml").open(encoding="utf-8") as source:
        config = yaml.safe_load(source)
    print("Hunyuan2mv stage: load safetensors on CPU", flush=True)
    source_state = safetensors.torch.load_file(module_root / "model.fp16.safetensors", device="cpu")
    print("Hunyuan2mv stage: safetensors loaded", flush=True)

    def consume(prefix: str) -> dict[str, object]:
        selected = [key for key in source_state if key.startswith(f"{prefix}.")]
        if not selected:
            raise ValueError(f"The pinned Hunyuan2mv checkpoint has no {prefix} state.")
        return {key.removeprefix(f"{prefix}."): source_state.pop(key) for key in selected}

    def component(config_key: str, prefix: str, *, strict: bool) -> object:
        print(f"Hunyuan2mv stage: construct {prefix}", flush=True)
        instance = instantiate_from_config(config[config_key])
        print(f"Hunyuan2mv stage: cast {prefix} to {dtype}", flush=True)
        instance.to(dtype=dtype)
        state = consume(prefix)
        print(f"Hunyuan2mv stage: transfer {prefix} state", flush=True)
        instance.load_state_dict(state, strict=strict)
        del state
        gc.collect()
        print(f"Hunyuan2mv stage: {prefix} complete", flush=True)
        return instance

    model = component("model", "model", strict=True)
    vae = component("vae", "vae", strict=False)
    conditioner = component("conditioner", "conditioner", strict=True)
    if source_state:
        raise ValueError(f"The pinned Hunyuan2mv checkpoint has unexpected state groups: {sorted(source_state)[:3]}")
    del source_state
    gc.collect()
    print("Hunyuan2mv stage: construct pipeline", flush=True)
    scheduler = instantiate_from_config(config["scheduler"])
    image_processor = instantiate_from_config(config["image_processor"])
    pipeline = Hunyuan3DDiTFlowMatchingPipeline(
        vae=vae,
        model=model,
        scheduler=scheduler,
        conditioner=conditioner,
        image_processor=image_processor,
        device="cpu",
        dtype=dtype,
        from_pretrained_kwargs={
            "model_path": str(checkpoint_root),
            "subfolder": "hunyuan3d-dit-v2-mv",
            "use_safetensors": True,
            "variant": "fp16",
            "dtype": dtype,
            "device": "cpu",
        },
    )
    # The pinned upstream pipeline implements ``enable_model_cpu_offload``
    # against this registry, but its constructor does not initialise it.  The
    # registry is only a view of the exact official components constructed
    # above; it neither replaces weights nor changes model behaviour.
    pipeline.components = {
        "vae": vae,
        "model": model,
        "scheduler": scheduler,
        "conditioner": conditioner,
        "image_processor": image_processor,
    }
    return pipeline


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-manifest", type=Path, required=True)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--model-repository", type=Path, required=True)
    parser.add_argument("--checkpoint-root", type=Path, required=True)
    parser.add_argument("--device", choices=("cuda", "cpu"), default="cuda")
    arguments = parser.parse_args()
    if arguments.candidate.exists():
        raise ValueError("Refusing to overwrite a prior Hunyuan2mv proposal.")
    input_manifest = load_input(arguments.input_manifest)
    candidate_id = str(input_manifest["candidate_id"])
    trial = TRIALS[candidate_id]

    import torch
    if arguments.device == "cuda" and not torch.cuda.is_available():
        raise RuntimeError("The fixed CUDA Hunyuan3D-2mv trial requires an available CUDA device.")
    checkpoint_root, checkpoint_files = prepare_shape_checkpoint(arguments.checkpoint_root)
    pipeline = load_shape_pipeline_low_memory(checkpoint_root, torch, device=arguments.device)
    if arguments.device == "cuda":
        if not hasattr(pipeline, "enable_model_cpu_offload"):
            raise RuntimeError("The pinned Hunyuan3D-2 checkout lacks the mandatory low-VRAM offload API.")
        print("Hunyuan2mv stage: enable CUDA CPU-offload", flush=True)
        pipeline.enable_model_cpu_offload()
        # The pinned pipeline uses ``self.device`` (rather than its own
        # ``_execution_device`` helper) to allocate latents and scheduler
        # tensors. CPU offload intentionally keeps all modules on CPU until a
        # hook invokes them, so declare CUDA as the execution device after
        # the hooks exist without moving a component eagerly.
        pipeline.device = torch.device("cuda")
    else:
        print("Hunyuan2mv stage: CPU inference (no CUDA offload)", flush=True)
    views = {slot: str(arguments.input_manifest.parent / entry["file"]) for slot, entry in input_manifest["views"].items()}
    if arguments.device == "cuda":
        torch.cuda.reset_peak_memory_stats()
    started = time.monotonic()
    mesh = pipeline(
        image=views,
        num_inference_steps=int(trial["inference_steps"]),
        octree_resolution=int(trial["octree_resolution"]),
        num_chunks=int(trial["num_chunks"]),
        generator=torch.manual_seed(31_415_926),
        output_type="trimesh",
    )[0]
    arguments.candidate.parent.mkdir(parents=True, exist_ok=True)
    mesh.export(arguments.candidate)
    record = {
        "schema": "pale_mirror.hunyuan2mv_volume_candidate.v1",
        "bakeoff_id": trial["bakeoff_id"],
        "candidate_id": candidate_id,
        "asset_id": ASSET_ID,
        "stage": "volume-proposal-unaccepted",
        "input_manifest_sha256": sha256(arguments.input_manifest),
        "views": input_manifest["views"],
        "model": {
            "repository": "https://github.com/Tencent-Hunyuan/Hunyuan3D-2",
            "repository_commit": _git_revision(arguments.model_repository),
            "checkpoint": "tencent/Hunyuan3D-2mv",
            "subfolder": "hunyuan3d-dit-v2-mv",
            "files": checkpoint_files,
            "shape_only": True,
            "low_vram_cpu_offload": arguments.device == "cuda",
            "initial_device": "cpu",
            "inference_steps": trial["inference_steps"],
            "octree_resolution": trial["octree_resolution"],
            "num_chunks": trial["num_chunks"],
            "seed": 31_415_926,
            "loader": "streaming_cpu_components",
        },
        "runtime": {
            "torch": torch.__version__,
            "cuda": torch.version.cuda,
            "device": torch.cuda.get_device_name(0) if arguments.device == "cuda" else "cpu",
            "peak_cuda_mib": round(torch.cuda.max_memory_allocated() / 1024 / 1024, 2)
            if arguments.device == "cuda"
            else None,
            "elapsed_seconds": round(time.monotonic() - started, 2),
        },
        "output": {"file": arguments.candidate.name, "sha256": sha256(arguments.candidate)},
        "runtime_export_forbidden": True,
    }
    (arguments.candidate.parent / "manifest.json").write_text(json.dumps(record, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(record, indent=2, sort_keys=True))


def _git_revision(repository: Path) -> str:
    head = repository / ".git" / "HEAD"
    if not head.is_file():
        raise ValueError("Pinned Hunyuan3D repository metadata is unavailable.")
    content = head.read_text(encoding="utf-8").strip()
    if not content.startswith("ref: "):
        return content
    reference = repository / ".git" / content.removeprefix("ref: ")
    if not reference.is_file():
        raise ValueError("Pinned Hunyuan3D repository HEAD reference is unavailable.")
    return reference.read_text(encoding="utf-8").strip()


if __name__ == "__main__":
    main()
