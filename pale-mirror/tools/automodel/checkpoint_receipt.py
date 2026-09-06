#!/usr/bin/env python3
"""Record a locally verified official-model checkpoint without copying it.

Automodel checkpoints stay on the private inference host. This tool writes
only a hash-pinned receipt below ``build/automodel`` so a later local runner
can prove which registered official file it was given. It deliberately does
not download files, invoke a model, or copy weights into the repository.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

from contracts import (
    AutomodelContractError,
    require_relative_build_path,
    sha256,
    validate_official_checkpoint_receipt,
    write_object,
)
from registry import load_registry, require_local_preflight


ROOT = Path(__file__).resolve().parents[2]

OFFICIAL_CHECKPOINTS: dict[str, dict[str, str]] = {
    "sv3d_p_orbit": {
        "repository": "stabilityai/sv3d",
        "filename": "sv3d_p.safetensors",
    },
    "edit360_dual_anchor_orbit": {
        "repository": "stabilityai/sv3d",
        "filename": "sv3d_u.safetensors",
    },
    "edit360_dual_anchor_orbit_fast_fp16_20": {
        "repository": "stabilityai/sv3d",
        "filename": "sv3d_u.safetensors",
    },
    "vggt_official": {
        "repository": "facebook/VGGT-Omega",
        "filename": "vggt_omega_1b_512.pt",
    },
    "da3_base": {
        "repository": "depth-anything/DA3-BASE",
        "filename": "model.safetensors",
    },
}


def write_official_checkpoint_receipt(
    model_id: str,
    checkpoint: Path,
    output: Path,
    *,
    repository_root: Path = ROOT,
    available_vram_gib: int = 8,
) -> dict[str, Any]:
    """Write one immutable local receipt for a registered official checkpoint.

    ``checkpoint`` may live on the user's private host, but it is never
    copied, named in the receipt, or treated as a project source artifact.
    The receipt records only the registered origin, byte count and SHA-256.
    """

    expected = OFFICIAL_CHECKPOINTS.get(model_id)
    if expected is None:
        raise AutomodelContractError(f"no official checkpoint contract is registered for {model_id}")
    if not checkpoint.is_file():
        raise AutomodelContractError(f"official checkpoint is missing: {checkpoint}")
    registry = load_registry()
    spec = require_local_preflight(registry, model_id, available_vram_gib)
    resolved_output = output.resolve()
    try:
        relative_output = resolved_output.relative_to(repository_root.resolve()).as_posix()
    except ValueError as exc:
        raise AutomodelContractError("checkpoint receipt must be inside the repository") from exc
    require_relative_build_path(relative_output, "receipt.file")
    if resolved_output.exists():
        raise AutomodelContractError(f"refusing to overwrite immutable Automodel receipt: {resolved_output}")
    receipt = {
        "schema": "pale_mirror.automodel.official_checkpoint_receipt.v1",
        "model_id": model_id,
        "scope": "research_only_noncanonical",
        "promotion_prohibited": True,
        "official_origin": {
            "repository": expected["repository"],
            "filename": expected["filename"],
            "license": spec.licence,
        },
        "checkpoint": {
            "sha256": sha256(checkpoint),
            "byte_size": checkpoint.stat().st_size,
            "storage": "private_local_host",
        },
        "contract": {
            "weights_not_copied_to_repository": True,
            "model_output_is_model_derived": True,
            "canonical_promotion_forbidden": True,
        },
    }
    validate_official_checkpoint_receipt(receipt)
    write_object(resolved_output, receipt)
    return receipt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, choices=sorted(OFFICIAL_CHECKPOINTS))
    parser.add_argument("--checkpoint", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--available-vram-gib", type=int, default=8)
    arguments = parser.parse_args()
    print(
        json.dumps(
            write_official_checkpoint_receipt(
                arguments.model,
                arguments.checkpoint,
                arguments.output,
                available_vram_gib=arguments.available_vram_gib,
            ),
            indent=2,
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
