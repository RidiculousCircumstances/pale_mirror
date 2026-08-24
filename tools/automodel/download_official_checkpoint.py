#!/usr/bin/env python3
"""Download one registered official Automodel checkpoint to a private host.

The source is intentionally a tiny fixed allowlist, rather than a generic
URL/command wrapper. It copies no weight into the repository and leaves the
hash/provenance record to ``checkpoint_receipt.py`` after a completed download.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from checkpoint_receipt import OFFICIAL_CHECKPOINTS


def download(model_id: str, local_dir: Path) -> Path:
    if model_id not in OFFICIAL_CHECKPOINTS:
        raise ValueError(f"no official checkpoint contract is registered for {model_id}")
    try:
        from huggingface_hub import hf_hub_download
    except ImportError as exc:
        raise RuntimeError("huggingface_hub is required for the local official-checkpoint download") from exc
    record = OFFICIAL_CHECKPOINTS[model_id]
    local_dir = local_dir.resolve()
    local_dir.mkdir(parents=True, exist_ok=True)
    destination = local_dir / record["filename"]
    if destination.is_file():
        return destination
    downloaded = Path(
        hf_hub_download(
            repo_id=record["repository"],
            filename=record["filename"],
            local_dir=local_dir,
        )
    ).resolve()
    if downloaded != destination.resolve() or not downloaded.is_file() or downloaded.stat().st_size <= 0:
        raise RuntimeError("official checkpoint download did not produce the expected non-empty local file")
    return downloaded


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", required=True, choices=sorted(OFFICIAL_CHECKPOINTS))
    parser.add_argument("--local-dir", required=True, type=Path)
    arguments = parser.parse_args()
    result = download(arguments.model, arguments.local_dir)
    print(json.dumps({"model": arguments.model, "checkpoint": str(result), "status": "downloaded_or_present"}, sort_keys=True))


if __name__ == "__main__":
    main()
