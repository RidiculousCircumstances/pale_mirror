#!/usr/bin/env python3
"""Extract the twenty non-primary frames from a pinned 21-frame SV3D_p video."""

from __future__ import annotations

import argparse
from pathlib import Path

import cv2


FRAME_COUNT = 21
NON_PRIMARY_FRAME_COUNT = 20


def extract(video: Path, output: Path) -> list[Path]:
    if not video.is_file():
        raise ValueError(f"SV3D video is missing: {video}")
    if output.exists() and any(output.iterdir()):
        raise ValueError(f"refusing to overwrite extracted SV3D frames: {output}")
    output.mkdir(parents=True, exist_ok=True)
    capture = cv2.VideoCapture(str(video))
    if not capture.isOpened():
        raise ValueError(f"cannot decode SV3D video: {video}")
    written: list[Path] = []
    try:
        for index in range(FRAME_COUNT):
            ok, frame = capture.read()
            if not ok:
                raise ValueError(f"SV3D video ended at frame {index}; expected {FRAME_COUNT}")
            if index == NON_PRIMARY_FRAME_COUNT:
                # The upstream sampler is called with a final literal 0-degree
                # camera and overwrites this final frame with its input.  The
                # literal source primary, not this re-encoded duplicate, is
                # inserted into the ViewSet by the Automodel adapter.
                continue
            yaw = (index + 1) * 18
            target = output / f"frame_{yaw:03d}.png"
            if not cv2.imwrite(str(target), frame):
                raise ValueError(f"failed to write extracted frame: {target}")
            written.append(target)
        extra_ok, _ = capture.read()
        if extra_ok:
            raise ValueError("SV3D video has more than the expected 21 frames")
    finally:
        capture.release()
    return written


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--video", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    written = extract(arguments.video, arguments.output)
    print("\n".join(str(path) for path in written))


if __name__ == "__main__":
    main()
