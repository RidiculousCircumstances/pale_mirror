#!/usr/bin/env python3
"""Fail-closed import/CUDA preflight for the pinned Edit360 dual-view runner."""

from __future__ import annotations

import argparse
import importlib
from pathlib import Path
import sys


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", required=True, type=Path)
    arguments = parser.parse_args()
    source_root = arguments.source_root.resolve()
    if not (source_root / "scripts" / "sampling" / "simple_video_sample.py").is_file():
        raise RuntimeError("pinned Edit360 upstream sampler is missing")
    sys.path.insert(0, str(source_root))
    for module_name in ("cv2", "einops", "fire", "imageio", "omegaconf", "rembg", "torch", "tyro", "xformers"):
        importlib.import_module(module_name)
    import torch

    if not torch.cuda.is_available():
        raise RuntimeError("Edit360 requires CUDA for this registered Windows experiment")
    importlib.import_module("scripts.sampling.simple_video_sample")
    print(f"cuda={torch.cuda.get_device_name(0)}")


if __name__ == "__main__":
    main()
