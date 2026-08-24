#!/usr/bin/env python3
"""Run the pinned SV3D sampler as a bounded 20-step mixed-fp16 experiment.

This is intentionally a distinct research profile, not a replacement for the
upstream full-precision path.  It keeps SV3D's 21-frame, 576-square temporal
batch and its camera schedule, while making three explicit implementation
changes:

* load the FP32 checkpoint on CPU, convert the denoiser-bearing model to FP16
  on CPU, then move it to CUDA so the conversion cannot transiently exceed an
  8 GiB GPU;
* retain the upstream FP32 image-codec boundaries: its conditioner VAE and
  first-stage decoder receive FP32 tensors before their own autocast scopes;
* retain the existing conditioner/safety-filter CPU offload after conditioning;
* run the fixed sampler under CUDA FP16 autocast and inference mode.

The runner, not this shim, pins the 20-step schedule and immutable manifest.
It writes no Pale Mirror asset and can only be invoked through the constrained
SV3D runner below ``build/automodel``.
"""

from __future__ import annotations

import argparse
import gc
from pathlib import Path
import sys
import weakref

import torch


def _parse_source_root() -> Path:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--source-root", type=Path, required=True)
    arguments, remaining = parser.parse_known_args()
    source_root = arguments.source_root.resolve()
    if not (source_root / "scripts" / "sampling" / "simple_video_sample.py").is_file():
        raise RuntimeError(f"SV3D source root is invalid: {source_root}")
    sys.argv = [sys.argv[0], *remaining]
    return source_root


class _ConditionerOffloadSampler:
    """Release conditioning-only modules immediately before denoising."""

    def __init__(self, delegate: object, model: object) -> None:
        self._delegate = delegate
        self._model_ref = weakref.ref(model)
        self._offloaded = False

    def __call__(self, *args: object, **kwargs: object) -> object:
        if not self._offloaded:
            model = self._model_ref()
            if model is None:
                raise RuntimeError("SV3D model was released before sampling")
            for embedder in tuple(model.conditioner.embedders):
                embedder.to("cpu")
            gc.collect()
            torch.cuda.empty_cache()
            self._offloaded = True
            print("SV3D fp16 profile: moved post-conditioning embedders to CPU before denoise.")
        return self._delegate(*args, **kwargs)


def main() -> None:
    source_root = _parse_source_root()
    sys.path.insert(0, str(source_root))
    import fire
    import scripts.sampling.simple_video_sample as upstream

    original_sample = upstream.sample
    original_filter = upstream.DeepFloydDataFiltering

    def load_model_cpu_half(
        config_path: str,
        device: str,
        num_frames: int,
        num_steps: int,
        verbose: bool = False,
    ) -> tuple[object, object]:
        if device != "cuda" or not torch.cuda.is_available():
            raise RuntimeError("SV3D fp16 profile requires CUDA")
        if torch.cuda.get_device_capability(0)[0] < 7:
            raise RuntimeError("SV3D fp16 profile requires a Tensor-Core-capable CUDA GPU")
        config = upstream.OmegaConf.load(config_path)
        config.model.params.sampler_config.params.verbose = verbose
        config.model.params.sampler_config.params.num_steps = num_steps
        config.model.params.sampler_config.params.guider_config.params.num_frames = num_frames
        # Unlike upstream CUDA loading, keep OpenCLIP and all checkpoint tensors
        # on the CPU until they have become FP16.  This avoids a second FP32 GPU
        # copy while converting an 8+ GiB checkpoint.
        model = upstream.instantiate_from_config(config.model).to(device="cpu", dtype=torch.float16).eval()
        first_stage = getattr(model, "first_stage_model", None)
        if first_stage is None:
            raise RuntimeError("SV3D fp16 profile requires model.first_stage_model")
        # `simple_video_sample.py` creates FP32 conditioning frames before the
        # codec's nested autocast region. Keep that codec and the equivalent
        # VideoPredictionEmbedder encoder FP32; the U-Net remains FP16.
        first_stage.float()
        fp32_conditioning_codecs = 0
        for embedder in tuple(model.conditioner.embedders):
            encoder = getattr(embedder, "encoder", None)
            if encoder is not None:
                encoder.float()
                fp32_conditioning_codecs += 1
        if fp32_conditioning_codecs == 0:
            raise RuntimeError("SV3D fp16 profile found no FP32 conditioning codec")
        print(
            "SV3D fp16 profile: retained FP32 first-stage and "
            f"{fp32_conditioning_codecs} conditioner codec(s); denoiser remains FP16."
        )

        def cpu_filter(*filter_args: object, **filter_kwargs: object) -> object:
            filter_kwargs["device"] = "cpu"
            return original_filter(*filter_args, **filter_kwargs)

        model.to(device)
        data_filter = cpu_filter(verbose=False)
        model.sampler = _ConditionerOffloadSampler(model.sampler, model)
        return model, data_filter

    def sample_mixed_fp16(*args: object, **kwargs: object) -> object:
        with torch.inference_mode(), torch.autocast(device_type="cuda", dtype=torch.float16):
            return original_sample(*args, **kwargs)

    upstream.load_model = load_model_cpu_half
    upstream.sample = sample_mixed_fp16
    fire.Fire(upstream.sample)


if __name__ == "__main__":
    main()
