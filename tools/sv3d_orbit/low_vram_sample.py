#!/usr/bin/env python3
"""Run the pinned SV3D sampler with a narrowly-scoped low-VRAM profile.

The sampler, checkpoint, camera schedule and 576-square image contract remain
the upstream implementation. This shim only moves modules which are not used
by denoising out of VRAM at the first sampler call:

* DeepFloyd's post-generation safety filter is loaded on CPU; it is only used
  after decoded frames already exist.
* The SV3D conditioner has already produced c/uc at that point, so its
  embedders can move to CPU before the first U-Net denoise step.

It deliberately does not split the 21-frame temporal batch, reduce resolution,
reduce steps, alter weights, or change camera parameters.
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
    """Delegate sampler that releases post-conditioning VRAM exactly once."""

    def __init__(self, delegate: object, model: object) -> None:
        self._delegate = delegate
        self._model_ref = weakref.ref(model)
        self._offloaded = False

    def __call__(self, *args: object, **kwargs: object) -> object:
        if not self._offloaded:
            model = self._model_ref()
            if model is None:
                raise RuntimeError("SV3D model was released before sampling")
            embedders = tuple(model.conditioner.embedders)
            for embedder in embedders:
                embedder.to("cpu")
            gc.collect()
            torch.cuda.empty_cache()
            self._offloaded = True
            print(
                "SV3D low-vram profile: moved "
                f"{len(embedders)} post-conditioning embedder(s) to CPU before denoise."
            )
        return self._delegate(*args, **kwargs)


def main() -> None:
    source_root = _parse_source_root()
    sys.path.insert(0, str(source_root))
    import fire
    import scripts.sampling.simple_video_sample as upstream

    original_load_model = upstream.load_model
    original_filter = upstream.DeepFloydDataFiltering

    def load_model_low_vram(*args: object, **kwargs: object) -> tuple[object, object]:
        def cpu_filter(*filter_args: object, **filter_kwargs: object) -> object:
            filter_kwargs["device"] = "cpu"
            return original_filter(*filter_args, **filter_kwargs)

        upstream.DeepFloydDataFiltering = cpu_filter
        try:
            model, data_filter = original_load_model(*args, **kwargs)
        finally:
            upstream.DeepFloydDataFiltering = original_filter
        model.sampler = _ConditionerOffloadSampler(model.sampler, model)
        return model, data_filter

    upstream.load_model = load_model_low_vram
    fire.Fire(upstream.sample)


if __name__ == "__main__":
    main()
