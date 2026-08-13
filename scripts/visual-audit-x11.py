#!/usr/bin/env python3
"""Minimal X11 input and capture helper for the PM visual-audit harness."""

from __future__ import annotations

import argparse
import ctypes
import ctypes.util
import os
import re
import subprocess
import sys
import time
from pathlib import Path


def library(name: str) -> ctypes.CDLL:
    path = ctypes.util.find_library(name)
    if path is None:
        raise RuntimeError(f"required X11 library is unavailable: {name}")
    return ctypes.CDLL(path)


X11 = library("X11")
XTEST = library("Xtst")
X11.XOpenDisplay.restype = ctypes.c_void_p
X11.XStringToKeysym.argtypes = [ctypes.c_char_p]
X11.XStringToKeysym.restype = ctypes.c_ulong
X11.XKeysymToKeycode.argtypes = [ctypes.c_void_p, ctypes.c_ulong]
X11.XKeysymToKeycode.restype = ctypes.c_ubyte
X11.XFlush.argtypes = [ctypes.c_void_p]
X11.XSetInputFocus.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_ulong]
X11.XMoveResizeWindow.argtypes = [
    ctypes.c_void_p,
    ctypes.c_ulong,
    ctypes.c_int,
    ctypes.c_int,
    ctypes.c_uint,
    ctypes.c_uint,
]
XTEST.XTestFakeKeyEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
XTEST.XTestFakeButtonEvent.argtypes = [ctypes.c_void_p, ctypes.c_uint, ctypes.c_int, ctypes.c_ulong]
XTEST.XTestFakeMotionEvent.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_int, ctypes.c_int, ctypes.c_ulong]


def open_display() -> ctypes.c_void_p:
    display = X11.XOpenDisplay(None)
    if not display:
        raise RuntimeError(f"cannot open X11 display {os.environ.get('DISPLAY', '<unset>')}")
    return display


def minecraft_window() -> int:
    output = subprocess.check_output(["xwininfo", "-root", "-tree"], text=True)
    candidates = []
    for line in output.splitlines():
        match = re.search(r"^\s*(0x[0-9a-f]+)\s+\"Minecraft[^\"]*\"", line, re.IGNORECASE)
        if match:
            candidates.append(int(match.group(1), 16))
    if not candidates:
        raise RuntimeError("Minecraft X11 window was not found")
    return candidates[-1]


def flush(display: ctypes.c_void_p) -> None:
    X11.XFlush(display)


def keycode(display: ctypes.c_void_p, keysym_name: str) -> int:
    keysym = X11.XStringToKeysym(keysym_name.encode("ascii"))
    if not keysym:
        raise RuntimeError(f"unknown X11 keysym: {keysym_name}")
    code = X11.XKeysymToKeycode(display, keysym)
    if not code:
        raise RuntimeError(f"no X11 keycode for: {keysym_name}")
    return code


def key_event(display: ctypes.c_void_p, keysym_name: str, pressed: bool) -> None:
    XTEST.XTestFakeKeyEvent(display, keycode(display, keysym_name), int(pressed), 0)


def press(display: ctypes.c_void_p, keysym_name: str) -> None:
    key_event(display, keysym_name, True)
    key_event(display, keysym_name, False)
    flush(display)
    time.sleep(0.025)


SHIFTED = {
    "_": "minus",
    ":": "semicolon",
    "@": "2",
    "+": "equal",
    "{": "bracketleft",
    "}": "bracketright",
    '"': "apostrophe",
    "?": "slash",
}

PLAIN = {
    " ": "space",
    "/": "slash",
    "-": "minus",
    ".": "period",
    ",": "comma",
    "=": "equal",
    "[": "bracketleft",
    "]": "bracketright",
}


def type_character(display: ctypes.c_void_p, value: str) -> None:
    shifted = value in SHIFTED or value.isupper()
    name = SHIFTED.get(value, PLAIN.get(value, value.lower()))
    if shifted:
        key_event(display, "Shift_L", True)
    press(display, name)
    if shifted:
        key_event(display, "Shift_L", False)
        flush(display)


def focus(display: ctypes.c_void_p, window: int) -> None:
    X11.XSetInputFocus(display, window, 2, 0)
    XTEST.XTestFakeMotionEvent(display, -1, 960, 540, 0)
    XTEST.XTestFakeButtonEvent(display, 1, 1, 0)
    XTEST.XTestFakeButtonEvent(display, 1, 0, 0)
    flush(display)
    time.sleep(0.15)


def send_command(value: str) -> None:
    display = open_display()
    focus(display, minecraft_window())
    press(display, "slash")
    # A freshly connected, software-rendered audit client can drop the first
    # characters while the chat screen is still being constructed.  Waiting
    # for the screen here is cheaper and more reliable than compensating for a
    # truncated command later in the capture workflow.
    time.sleep(0.4)
    for character in value:
        type_character(display, character)
    press(display, "Return")


def resize(width: int, height: int) -> None:
    display = open_display()
    window = minecraft_window()
    X11.XMoveResizeWindow(display, window, 0, 0, width, height)
    flush(display)
    focus(display, window)


def send_key(value: str) -> None:
    display = open_display()
    focus(display, minecraft_window())
    press(display, value)


def capture(destination: Path) -> None:
    try:
        from PIL import ImageGrab
    except ImportError as failure:
        raise RuntimeError("Python Pillow is required for visual-audit capture") from failure
    destination.parent.mkdir(parents=True, exist_ok=True)
    image = ImageGrab.grab()
    if image.width <= 0 or image.height <= 0:
        raise RuntimeError("X11 returned an empty framebuffer")
    image.save(destination)


def main() -> int:
    parser = argparse.ArgumentParser()
    actions = parser.add_subparsers(dest="action", required=True)
    command = actions.add_parser("command")
    command.add_argument("value")
    key = actions.add_parser("key")
    key.add_argument("value")
    window = actions.add_parser("resize")
    window.add_argument("width", type=int)
    window.add_argument("height", type=int)
    screenshot = actions.add_parser("capture")
    screenshot.add_argument("destination", type=Path)
    args = parser.parse_args()
    try:
        if args.action == "command":
            send_command(args.value)
        elif args.action == "key":
            send_key(args.value)
        elif args.action == "resize":
            resize(args.width, args.height)
        else:
            capture(args.destination)
    except (OSError, RuntimeError, subprocess.SubprocessError) as failure:
        print(f"visual-audit X11 failure: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
