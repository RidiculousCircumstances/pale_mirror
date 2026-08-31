#!/usr/bin/env python3
"""Minimal X11 input and capture helper for the PM visual-audit harness."""

from __future__ import annotations

import argparse
import ctypes
import ctypes.util
import os
import re
import shutil
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
X11.XSendEvent.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_int, ctypes.c_long, ctypes.c_void_p]
X11.XSendEvent.restype = ctypes.c_int
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


class XKeyEvent(ctypes.Structure):
    _fields_ = [
        ("type", ctypes.c_int),
        ("serial", ctypes.c_ulong),
        ("send_event", ctypes.c_int),
        ("display", ctypes.c_void_p),
        ("window", ctypes.c_ulong),
        ("root", ctypes.c_ulong),
        ("subwindow", ctypes.c_ulong),
        ("time", ctypes.c_ulong),
        ("x", ctypes.c_int),
        ("y", ctypes.c_int),
        ("x_root", ctypes.c_int),
        ("y_root", ctypes.c_int),
        ("state", ctypes.c_uint),
        ("keycode", ctypes.c_uint),
        ("same_screen", ctypes.c_int),
    ]


class XEvent(ctypes.Union):
    _fields_ = [("xkey", XKeyEvent), ("pad", ctypes.c_long * 24)]


class XImage(ctypes.Structure):
    _fields_ = [
        ("width", ctypes.c_int),
        ("height", ctypes.c_int),
        ("xoffset", ctypes.c_int),
        ("format", ctypes.c_int),
        ("data", ctypes.c_void_p),
        ("byte_order", ctypes.c_int),
        ("bitmap_unit", ctypes.c_int),
        ("bitmap_bit_order", ctypes.c_int),
        ("bitmap_pad", ctypes.c_int),
        ("depth", ctypes.c_int),
        ("bytes_per_line", ctypes.c_int),
        ("bits_per_pixel", ctypes.c_int),
        ("red_mask", ctypes.c_ulong),
        ("green_mask", ctypes.c_ulong),
        ("blue_mask", ctypes.c_ulong),
        ("obdata", ctypes.c_void_p),
        ("funcs", ctypes.c_void_p),
    ]


X11.XGetImage.argtypes = [
    ctypes.c_void_p,
    ctypes.c_ulong,
    ctypes.c_int,
    ctypes.c_int,
    ctypes.c_uint,
    ctypes.c_uint,
    ctypes.c_ulong,
    ctypes.c_int,
]
X11.XGetImage.restype = ctypes.POINTER(XImage)
X11.XDestroyImage.argtypes = [ctypes.POINTER(XImage)]
X11.XGetGeometry.argtypes = [
    ctypes.c_void_p,
    ctypes.c_ulong,
    ctypes.POINTER(ctypes.c_ulong),
    ctypes.POINTER(ctypes.c_int),
    ctypes.POINTER(ctypes.c_int),
    ctypes.POINTER(ctypes.c_uint),
    ctypes.POINTER(ctypes.c_uint),
    ctypes.POINTER(ctypes.c_uint),
    ctypes.POINTER(ctypes.c_uint),
]
X11.XGetGeometry.restype = ctypes.c_int
X11.XTranslateCoordinates.argtypes = [
    ctypes.c_void_p,
    ctypes.c_ulong,
    ctypes.c_ulong,
    ctypes.c_int,
    ctypes.c_int,
    ctypes.POINTER(ctypes.c_int),
    ctypes.POINTER(ctypes.c_int),
    ctypes.POINTER(ctypes.c_ulong),
]
X11.XTranslateCoordinates.restype = ctypes.c_int


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


def window_size(display: ctypes.c_void_p, window: int) -> tuple[int, int]:
    root = ctypes.c_ulong()
    x = ctypes.c_int()
    y = ctypes.c_int()
    width = ctypes.c_uint()
    height = ctypes.c_uint()
    border = ctypes.c_uint()
    depth = ctypes.c_uint()
    if not X11.XGetGeometry(
        display,
        window,
        ctypes.byref(root),
        ctypes.byref(x),
        ctypes.byref(y),
        ctypes.byref(width),
        ctypes.byref(height),
        ctypes.byref(border),
        ctypes.byref(depth),
    ):
        raise RuntimeError("X11 could not read the Minecraft window geometry")
    return width.value, height.value


def root_capture_geometry(display: ctypes.c_void_p, window: int) -> tuple[int, int, int, int, int]:
    """Return the composited XWayland root backing a fullscreen audit client.

    GLFW's child surface may legally return an all-black backing image under a
    Wayland compositor even while the user sees a fully rendered game.  Root
    capturing the fullscreen composited root reads the visible pixels instead,
    without changing focus, camera or world state.
    """
    root = ctypes.c_ulong()
    ignored_x = ctypes.c_int()
    ignored_y = ctypes.c_int()
    width = ctypes.c_uint()
    height = ctypes.c_uint()
    border = ctypes.c_uint()
    depth = ctypes.c_uint()
    if not X11.XGetGeometry(
        display,
        window,
        ctypes.byref(root),
        ctypes.byref(ignored_x),
        ctypes.byref(ignored_y),
        ctypes.byref(width),
        ctypes.byref(height),
        ctypes.byref(border),
        ctypes.byref(depth),
    ):
        raise RuntimeError("X11 could not read the Minecraft window geometry")
    root_x = ctypes.c_int()
    root_y = ctypes.c_int()
    root_width = ctypes.c_uint()
    root_height = ctypes.c_uint()
    root_border = ctypes.c_uint()
    root_depth = ctypes.c_uint()
    root_parent = ctypes.c_ulong()
    if not X11.XGetGeometry(
        display,
        root.value,
        ctypes.byref(root_parent),
        ctypes.byref(root_x),
        ctypes.byref(root_y),
        ctypes.byref(root_width),
        ctypes.byref(root_height),
        ctypes.byref(root_border),
        ctypes.byref(root_depth),
    ):
        raise RuntimeError("X11 could not read the composited root geometry")
    if root_width.value <= 0 or root_height.value <= 0:
        raise RuntimeError("X11 returned an empty composited root")
    # The audit client is launched fullscreen. Capturing the root avoids a
    # HiDPI mismatch between a GLFW window's framebuffer dimensions and the
    # logical XWayland root, and avoids the black child-surface read above.
    return root.value, 0, 0, root_width.value, root_height.value


def keycode(display: ctypes.c_void_p, keysym_name: str) -> int:
    keysym = X11.XStringToKeysym(keysym_name.encode("ascii"))
    if not keysym:
        raise RuntimeError(f"unknown X11 keysym: {keysym_name}")
    code = X11.XKeysymToKeycode(display, keysym)
    if not code:
        raise RuntimeError(f"no X11 keycode for: {keysym_name}")
    return code


def key_event(
    display: ctypes.c_void_p,
    window: int,
    keysym_name: str,
    pressed: bool,
    modifiers: int = 0,
) -> None:
    """Deliver one key to the target window without relying on XTEST focus.

    Some real XWayland sessions accept synthetic input from XTEST but do not
    forward it to a captured GLFW window.  The audit client already owns the
    focused Minecraft window, so a direct X11 key event is deterministic there
    and also keeps the isolated-Xvfb path free of pointer/camera movement.
    """
    event = XEvent()
    event.xkey.type = 2 if pressed else 3  # KeyPress / KeyRelease
    event.xkey.send_event = 1
    event.xkey.display = display
    event.xkey.window = window
    event.xkey.root = window
    event.xkey.subwindow = 0
    event.xkey.time = 0
    event.xkey.x = event.xkey.y = event.xkey.x_root = event.xkey.y_root = 1
    event.xkey.state = modifiers
    event.xkey.keycode = keycode(display, keysym_name)
    event.xkey.same_screen = 1
    mask = 1 if pressed else 2  # KeyPressMask / KeyReleaseMask
    if not X11.XSendEvent(display, window, False, mask, ctypes.byref(event)):
        raise RuntimeError(f"X11 rejected key event for {keysym_name}")


def press(display: ctypes.c_void_p, window: int, keysym_name: str, modifiers: int = 0) -> None:
    key_event(display, window, keysym_name, True, modifiers)
    key_event(display, window, keysym_name, False, modifiers)
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


def type_character(display: ctypes.c_void_p, window: int, value: str) -> None:
    shifted = value in SHIFTED or value.isupper()
    name = SHIFTED.get(value, PLAIN.get(value, value.lower()))
    press(display, window, name, 1 if shifted else 0)  # ShiftMask


def focus(display: ctypes.c_void_p, window: int) -> None:
    X11.XSetInputFocus(display, window, 2, 0)
    flush(display)
    time.sleep(0.15)


def send_command(value: str) -> None:
    display = open_display()
    window = minecraft_window()
    focus(display, window)
    # Slash opens command chat directly.  Do not prepend T: after an F2 capture
    # Minecraft can treat that key as literal text in a still-open command
    # field, corrupting the next audit command (for example `t@s`).
    type_character(display, window, "/")
    time.sleep(0.4)
    for character in value:
        type_character(display, window, character)
    press(display, window, "Return")


def resize(width: int, height: int) -> None:
    display = open_display()
    window = minecraft_window()
    X11.XMoveResizeWindow(display, window, 0, 0, width, height)
    flush(display)
    focus(display, window)


def send_key(value: str) -> None:
    display = open_display()
    # Minecraft uses captured relative pointer motion for camera rotation.  A
    # synthetic click is useful before typing chat, but it also changes the
    # audited camera angle immediately before F2.  Keyboard-only actions need
    # focus without moving or clicking the pointer.
    window = minecraft_window()
    X11.XSetInputFocus(display, window, 2, 0)
    flush(display)
    time.sleep(0.05)
    press(display, window, value)


def click(x: int, y: int) -> None:
    display = open_display()
    window = minecraft_window()
    X11.XSetInputFocus(display, window, 2, 0)
    XTEST.XTestFakeMotionEvent(display, -1, x, y, 0)
    XTEST.XTestFakeButtonEvent(display, 1, 1, 0)
    XTEST.XTestFakeButtonEvent(display, 1, 0, 0)
    flush(display)


def capture(destination: Path) -> None:
    """Ask Minecraft itself for its rendered framebuffer, then copy that exact PNG.

    XGetImage reads an XWayland backing surface, which may be black or have a
    different logical size from the composited display. Minecraft's F2 path is
    the authoritative client framebuffer and does not alter simulation state.
    """
    screenshot_root = os.environ.get("PALE_MIRROR_CLIENT_SCREENSHOTS")
    if not screenshot_root:
        raise RuntimeError("PALE_MIRROR_CLIENT_SCREENSHOTS is required for a native frame capture")
    source_directory = Path(screenshot_root)
    source_directory.mkdir(parents=True, exist_ok=True)
    before = {path: path.stat().st_mtime_ns for path in source_directory.glob("*.png")}
    send_key("F2")
    deadline = time.monotonic() + 15.0
    observed_sizes: dict[Path, int] = {}
    while time.monotonic() < deadline:
        candidates = [path for path in source_directory.glob("*.png") if path.stat().st_mtime_ns > before.get(path, -1)]
        if candidates:
            source = max(candidates, key=lambda path: path.stat().st_mtime_ns)
            size = source.stat().st_size
            # Screenshot writes asynchronously. Wait for a non-empty file to
            # stop growing so the capture barrier never acknowledges a
            # truncated PNG.
            if size > 0 and observed_sizes.get(source) == size:
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination)
                return
            observed_sizes[source] = size
        time.sleep(0.1)
    raise RuntimeError("Minecraft did not create an F2 screenshot within 15 seconds")


def main() -> int:
    parser = argparse.ArgumentParser()
    actions = parser.add_subparsers(dest="action", required=True)
    command = actions.add_parser("command")
    command.add_argument("value")
    key = actions.add_parser("key")
    key.add_argument("value")
    click_action = actions.add_parser("click")
    click_action.add_argument("x", type=int)
    click_action.add_argument("y", type=int)
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
        elif args.action == "click":
            click(args.x, args.y)
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
