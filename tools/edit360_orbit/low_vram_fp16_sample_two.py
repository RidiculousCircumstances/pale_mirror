#!/usr/bin/env python3
"""Run Edit360's exact dual-anchor sampler in a bounded 8 GiB FP16 profile.

This only changes memory placement and numerical dtype. It retains Edit360's
two inputs and sequence sampler, producing an uncalibrated MODEL_DERIVED
visual sequence. It cannot create a ViewSet, camera-yaw claim, VGGT input,
geometry evidence, Blender candidate, PMMesh or runtime asset.
"""

from __future__ import annotations

import argparse
import gc
from pathlib import Path
import sys
import weakref


def _parse_arguments() -> argparse.Namespace:
    """Parse the narrow typed adapter CLI before importing Edit360.

    Edit360's checked-in README still shows ``argparse``-style underscore
    flags, while its current entrypoint passes them to Tyro, which accepts
    dashed flags instead.  Calling the upstream ``main`` would therefore make
    this adapter dependent on that stale, ambiguous boundary.  The adapter
    accepts one explicit typed spelling and invokes the pinned ``sample_two``
    function directly; sampling itself remains upstream code.
    """

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument("--mode", choices=("two",), required=True)
    parser.add_argument("--input-path-f", type=Path, required=True)
    parser.add_argument("--input-path-b", type=Path, required=True)
    parser.add_argument("--version", choices=("sv3d_u",), required=True)
    parser.add_argument("--num-steps", type=int, required=True)
    parser.add_argument("--seed", type=int, required=True)
    parser.add_argument("--decoding-t", type=int, required=True)
    parser.add_argument("--device", choices=("cuda",), required=True)
    parser.add_argument("--output-folder-mp4", type=Path, required=True)
    parser.add_argument("--output-folder-img", type=Path, required=True)
    parser.add_argument("--anchor-view-angle", type=int, required=True)
    arguments = parser.parse_args()
    source_root = arguments.source_root.resolve()
    if not (source_root / "scripts" / "sampling" / "simple_video_sample.py").is_file():
        raise RuntimeError(f"Edit360 source root is invalid: {source_root}")
    for input_path in (arguments.input_path_f, arguments.input_path_b):
        if not input_path.is_file():
            raise RuntimeError(f"Edit360 input is missing: {input_path}")
    if arguments.num_steps <= 0 or arguments.decoding_t <= 0:
        raise RuntimeError("Edit360 num_steps and decoding_t must be positive")
    if arguments.anchor_view_angle != 180:
        raise RuntimeError("The bounded dual-anchor adapter accepts only the pinned 180-degree anchor")
    return arguments


class _ConditionerOffloadSampler:
    """Release post-conditioning embedders exactly before the first denoise."""

    def __init__(self, delegate: object, model: object) -> None:
        self._delegate = delegate
        self._model_ref = weakref.ref(model)
        self._offloaded = False

    def __call__(self, *args: object, **kwargs: object) -> object:
        if not self._offloaded:
            model = self._model_ref()
            if model is None:
                raise RuntimeError("Edit360 model was released before sampling")
            embedders = tuple(model.conditioner.embedders)
            for embedder in embedders:
                embedder.to("cpu")
            gc.collect()
            torch.cuda.empty_cache()
            self._offloaded = True
            print(
                "Edit360 bounded FP16 profile: moved "
                f"{len(embedders)} post-conditioning embedder(s) to CPU before denoise."
            )
        return self._delegate(*args, **kwargs)


def main() -> None:
    arguments = _parse_arguments()
    source_root = arguments.source_root
    # Keep the typed CLI inspectable on development hosts that intentionally do
    # not carry the private Windows CUDA environment.
    global torch
    import torch

    sys.path.insert(0, str(source_root))
    import scripts.sampling.simple_video_sample as upstream

    original_filter = upstream.DeepFloydDataFiltering
    original_sample_two = upstream.sample_two

    def load_model_cpu_half(
        config_path: str,
        device: str,
        num_frames: int,
        num_steps: int,
        verbose: bool = False,
    ) -> tuple[object, object]:
        if device != "cuda" or not torch.cuda.is_available():
            raise RuntimeError("Edit360 bounded FP16 profile requires CUDA")
        if torch.cuda.get_device_capability(0)[0] < 7:
            raise RuntimeError("Edit360 bounded FP16 profile requires a Tensor-Core-capable CUDA GPU")
        config = upstream.OmegaConf.load(config_path)
        config.model.params.sampler_config.params.verbose = verbose
        config.model.params.sampler_config.params.num_steps = num_steps
        config.model.params.sampler_config.params.guider_config.params.num_frames = num_frames
        model = upstream.instantiate_from_config(config.model).to(device="cpu", dtype=torch.float16).eval()
        first_stage = getattr(model, "first_stage_model", None)
        if first_stage is None:
            raise RuntimeError("Edit360 bounded FP16 profile requires model.first_stage_model")
        first_stage.float()
        fp32_conditioning_codecs = 0
        for embedder in tuple(model.conditioner.embedders):
            encoder = getattr(embedder, "encoder", None)
            if encoder is not None:
                encoder.float()
                fp32_conditioning_codecs += 1
        if fp32_conditioning_codecs == 0:
            raise RuntimeError("Edit360 bounded FP16 profile found no FP32 conditioning codec")

        def cpu_filter(*filter_args: object, **filter_kwargs: object) -> object:
            filter_kwargs["device"] = "cpu"
            return original_filter(*filter_args, **filter_kwargs)

        model.to(device)
        data_filter = cpu_filter(verbose=False)
        model.sampler = _ConditionerOffloadSampler(model.sampler, model)
        print(
            "Edit360 bounded FP16 profile: retained FP32 first-stage and "
            f"{fp32_conditioning_codecs} conditioner codec(s); denoiser remains FP16."
        )
        return model, data_filter

    def sample_mixed_fp16(*args: object, **kwargs: object) -> object:
        with torch.inference_mode(), torch.autocast(device_type="cuda", dtype=torch.float16):
            return original_sample_two(*args, **kwargs)

    upstream.load_model = load_model_cpu_half
    sample_mixed_fp16(
        input_path_f=str(arguments.input_path_f),
        input_path_b=str(arguments.input_path_b),
        num_steps=arguments.num_steps,
        version=arguments.version,
        seed=arguments.seed,
        decoding_t=arguments.decoding_t,
        device=arguments.device,
        output_folder_mp4=str(arguments.output_folder_mp4),
        output_folder_img=str(arguments.output_folder_img),
        anchor_view_angle=arguments.anchor_view_angle,
    )


if __name__ == "__main__":
    main()
