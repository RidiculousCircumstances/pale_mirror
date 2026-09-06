#!/usr/bin/env python3
"""Write an immutable, redacted local Python environment lock for Automodel.

The lock identifies interpreter, package versions and CUDA capability without
recording machine names, credentials, package cache paths or model weights.
It is a reproducibility input for a local run manifest, not a deployment file.
"""

from __future__ import annotations

import argparse
from importlib import metadata
import json
import platform
from pathlib import Path
import sys

from contracts import AutomodelContractError, require_relative_build_path, write_object


ROOT = Path(__file__).resolve().parents[2]


def write_environment_lock(output: Path, *, repository_root: Path = ROOT) -> dict[str, object]:
    resolved = output.resolve()
    try:
        relative = resolved.relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("environment lock must be stored below this repository") from exc
    require_relative_build_path(relative, "environment_lock.file")
    if resolved.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable Automodel environment lock: {resolved}")
    distributions = sorted(
        (
            {"name": distribution.metadata["Name"].lower(), "version": distribution.version}
            for distribution in metadata.distributions()
            if distribution.metadata.get("Name")
        ),
        key=lambda entry: str(entry["name"]),
    )
    cuda: dict[str, object] = {"torch_available": False}
    try:
        import torch

        cuda = {
            "torch_available": True,
            "torch_version": torch.__version__,
            "cuda_runtime": torch.version.cuda,
            "cuda_available": torch.cuda.is_available(),
        }
        if torch.cuda.is_available():
            cuda["gpu_name"] = torch.cuda.get_device_name(0)
    except ImportError:
        pass
    lock: dict[str, object] = {
        "schema": "pale_mirror.automodel.environment_lock.v1",
        "scope": "research_only_noncanonical",
        "interpreter": {
            "implementation": platform.python_implementation(),
            "version": platform.python_version(),
            "platform": sys.platform,
        },
        "cuda": cuda,
        "distributions": distributions,
        "contract": {
            "contains_no_credentials": True,
            "contains_no_private_paths": True,
            "canonical_promotion_forbidden": True,
        },
    }
    write_object(resolved, lock)
    return lock


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    print(json.dumps(write_environment_lock(arguments.output), indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
