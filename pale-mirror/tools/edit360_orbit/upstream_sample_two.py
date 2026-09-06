#!/usr/bin/env python3
"""Invoke the unmodified Edit360 dual-anchor entrypoint behind a typed CLI."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys


def main() -> None:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--source-root", type=Path, required=True)
    arguments, remaining = parser.parse_known_args()
    source_root = arguments.source_root.resolve()
    if not (source_root / "scripts" / "sampling" / "simple_video_sample.py").is_file():
        raise RuntimeError(f"Edit360 source root is invalid: {source_root}")
    sys.path.insert(0, str(source_root))
    sys.argv = [sys.argv[0], *remaining]
    import scripts.sampling.simple_video_sample as upstream

    upstream.main()


if __name__ == "__main__":
    main()
