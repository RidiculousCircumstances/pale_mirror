#!/usr/bin/env python3
"""Fail fast if the fixed SV3D Windows environment cannot execute on CUDA."""

from __future__ import annotations

import argparse
import cv2  # noqa: F401
import einops  # noqa: F401
import fire  # noqa: F401
import imageio  # noqa: F401
import omegaconf  # noqa: F401
from pathlib import Path
import py_compile
import rembg  # noqa: F401
import sys
import torch
import xformers  # noqa: F401


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, required=True)
    parser.add_argument(
        "--memory-profile",
        choices=(
            "official_default",
            "low_vram_conditioner_cpu_offload",
            "low_vram_fp16_conditioner_cpu_offload",
        ),
        required=True,
    )
    parser.add_argument("--runner-root", type=Path, required=True)
    arguments = parser.parse_args()
    source_root = arguments.source_root.resolve()
    if not (source_root / "scripts" / "sampling" / "simple_video_sample.py").is_file():
        raise RuntimeError(f"SV3D source root is invalid: {source_root}")
    runner_root = arguments.runner_root.resolve()
    helper_name = {
        "low_vram_conditioner_cpu_offload": "low_vram_sample.py",
        "low_vram_fp16_conditioner_cpu_offload": "low_vram_fp16_sample.py",
    }.get(arguments.memory_profile)
    if helper_name is not None:
        helper = runner_root / helper_name
        if not helper.is_file():
            raise RuntimeError(f"SV3D low-VRAM helper is missing: {helper}")
        py_compile.compile(str(helper), doraise=True)
    sys.path.insert(0, str(source_root))
    # Import the actual entrypoint without calling it. This is deliberately a
    # stronger check than a hand-maintained module list: all immediate sampler
    # dependencies must be present before we consume an immutable run directory.
    import scripts.sampling.simple_video_sample  # noqa: F401
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA is not available")
    print(torch.__version__)
    print(torch.cuda.get_device_name(0))
    print(arguments.memory_profile)


if __name__ == "__main__":
    main()
