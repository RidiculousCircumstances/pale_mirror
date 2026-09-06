"""Capture and operate the private Windows Blender desktop through SSH-tunneled VNC.

This command has no direct network route to Windows: it first opens the pinned
SSH forward to a TightVNC service that is itself bound only to Windows
loopback.  The viewer credential is DPAPI-decrypted on Windows and never
written or printed on Linux.  Screenshots are intentionally stored under the
ignored build directory as auditable operator evidence.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re

from PIL import Image

from blender_common import ensure_vnc_tunnel, load_settings
from vnc_common import viewer_password


ROOT = Path(__file__).resolve().parents[2]
OUTPUT_ROOT = ROOT / "build" / "blender-gui"
_LABEL = re.compile(r"[a-z0-9][a-z0-9_-]{0,79}\Z")
_KEY_PART = r"(?:[A-Za-z0-9]|alt|bsp|ctrl|del|delete|down|end|enter|esc|f(?:[1-9]|1[0-9]|20)|home|ins|left|pgdn|pgup|return|right|shift|space|spacebar|tab|up)"
_KEY = re.compile(rf"{_KEY_PART}(?:-{_KEY_PART}){{0,2}}\Z")
_REQUEST_TIMEOUT_SECONDS = 20


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="command", required=True)
    capture = subcommands.add_parser("capture", help="Capture the current Windows desktop.")
    capture.add_argument("--label", required=True)
    capture.add_argument(
        "--require-visible",
        action="store_true",
        help="Fail if the decoded VNC frame contains no non-black pixels.",
    )
    click = subcommands.add_parser("click", help="Click one screen coordinate.")
    click.add_argument("--x", type=_coordinate, required=True)
    click.add_argument("--y", type=_coordinate, required=True)
    drag = subcommands.add_parser("drag", help="Drag between two screen coordinates.")
    drag.add_argument("--from-x", type=_coordinate, required=True)
    drag.add_argument("--from-y", type=_coordinate, required=True)
    drag.add_argument("--to-x", type=_coordinate, required=True)
    drag.add_argument("--to-y", type=_coordinate, required=True)
    key = subcommands.add_parser("key", help="Press one VNC key name.")
    key.add_argument("--value", required=True)
    arguments = parser.parse_args()
    if arguments.command == "capture" and not _LABEL.fullmatch(arguments.label):
        raise SystemExit("label must be 1-80 lowercase letters, digits, underscores or dashes")
    if arguments.command == "key" and not _KEY.fullmatch(arguments.value):
        raise SystemExit("key must be one to three supported vncdotool key parts, e.g. shift, ctrl-z or ctrl-shift-s")

    settings = load_settings()
    ensure_vnc_tunnel(settings)
    password = viewer_password(settings)
    client = _connect(settings.vnc_local_port, password)
    try:
        if arguments.command == "capture":
            OUTPUT_ROOT.mkdir(parents=True, exist_ok=True)
            target = OUTPUT_ROOT / f"{arguments.label}.png"
            client.captureScreen(str(target))
            if not target.is_file() or target.stat().st_size == 0:
                raise RuntimeError("VNC reported success but did not create a desktop screenshot.")
            frame_has_nonblack_pixels = _frame_has_nonblack_pixels(target)
            result = {
                "action": "capture",
                "frame_has_nonblack_pixels": frame_has_nonblack_pixels,
                "screenshot": str(target.relative_to(ROOT)),
            }
            if arguments.require_visible and not frame_has_nonblack_pixels:
                raise RuntimeError(
                    f"VNC returned an all-black framebuffer; inspect {target.relative_to(ROOT)} and repair host capture before manual authoring."
                )
        elif arguments.command == "click":
            client.mouseMove(arguments.x, arguments.y)
            client.mousePress(1)
            result = {"action": "click", "x": arguments.x, "y": arguments.y}
        elif arguments.command == "drag":
            client.mouseMove(arguments.from_x, arguments.from_y)
            client.mouseDown(1)
            client.mouseMove(arguments.to_x, arguments.to_y)
            client.mouseUp(1)
            result = {
                "action": "drag",
                "from": [arguments.from_x, arguments.from_y],
                "to": [arguments.to_x, arguments.to_y],
            }
        else:
            client.keyPress(arguments.value)
            result = {"action": "key", "value": arguments.value}
    except TimeoutError as error:
        raise RuntimeError(
            "VNC did not produce a control response within "
            f"{_REQUEST_TIMEOUT_SECONDS} seconds; inspect the Windows interactive display before manual authoring."
        ) from error
    finally:
        client.disconnect()
        # vncdotool owns a non-daemon Twisted reactor.  Stop it explicitly so
        # a one-shot capture/control invocation cannot leak a local process.
        from vncdotool import api

        api.shutdown()
    print(json.dumps(result, sort_keys=True))


def _connect(port: int, password: str):
    from vncdotool import api

    # vncdotool defaults its threaded proxy timeout to ``None`` when callers
    # omit it, which turns a capture failure into an unbounded blocked process.
    # A one-shot authoring control must fail visibly and leave no local child
    # process behind when the desktop cannot deliver a frame.
    return api.connect(
        f"127.0.0.1::{port}",
        password=password,
        timeout=_REQUEST_TIMEOUT_SECONDS,
    )


def _coordinate(value: str) -> int:
    try:
        coordinate = int(value)
    except ValueError as error:
        raise argparse.ArgumentTypeError("coordinates must be whole pixels") from error
    if not 0 <= coordinate <= 7680:
        raise argparse.ArgumentTypeError("coordinates must be within 0..7680")
    return coordinate


def _frame_has_nonblack_pixels(path: Path) -> bool:
    """Return whether a lossless VNC capture contains any visible pixel."""
    with Image.open(path) as image:
        return image.convert("RGB").getbbox() is not None


if __name__ == "__main__":
    main()
