#!/usr/bin/env python3
"""Resize a named X11 client window without requiring a window manager.

The visual-audit Xvfb display intentionally has no desktop environment. Electron
therefore ignores maximise requests and restores Blockbench's compact saved
geometry. This tiny stdlib/ctypes helper uses libX11 directly so the audit can
give Blockbench a reproducible full-size viewport without modifying a user's
interactive Blockbench profile.
"""

from __future__ import annotations

import argparse
import ctypes
import ctypes.util
import re
import subprocess
import sys
import time


Window = ctypes.c_ulong


def x11_api():
    library = ctypes.util.find_library("X11")
    if not library:
        raise RuntimeError("libX11 is unavailable")
    x11 = ctypes.CDLL(library)
    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XCloseDisplay.argtypes = [ctypes.c_void_p]
    x11.XRootWindow.argtypes = [ctypes.c_void_p, ctypes.c_int]
    x11.XRootWindow.restype = Window
    x11.XQueryTree.argtypes = [
        ctypes.c_void_p,
        Window,
        ctypes.POINTER(Window),
        ctypes.POINTER(Window),
        ctypes.POINTER(ctypes.POINTER(Window)),
        ctypes.POINTER(ctypes.c_uint),
    ]
    x11.XQueryTree.restype = ctypes.c_int
    x11.XFetchName.argtypes = [ctypes.c_void_p, Window, ctypes.POINTER(ctypes.c_char_p)]
    x11.XFetchName.restype = ctypes.c_int
    x11.XFree.argtypes = [ctypes.c_void_p]
    x11.XMoveResizeWindow.argtypes = [ctypes.c_void_p, Window, ctypes.c_int, ctypes.c_int, ctypes.c_uint, ctypes.c_uint]
    x11.XFlush.argtypes = [ctypes.c_void_p]
    return x11


def find_named_window(display_name, expected_name):
    # XFetchName only reads the legacy XA_STRING property. Electron writes its
    # title as UTF8_STRING, whereas xwininfo correctly exposes both forms.
    result = subprocess.run(
        ["xwininfo", "-display", display_name, "-root", "-tree"],
        check=False,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "xwininfo could not inspect the X11 root window")
    for line in result.stdout.splitlines():
        if expected_name not in line:
            continue
        match = re.match(r"\s*(0x[0-9a-fA-F]+)", line)
        if match:
            return Window(int(match.group(1), 16))
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--display", required=True)
    parser.add_argument("--title", required=True, help="Distinct substring of the target window title")
    parser.add_argument("--width", type=int, required=True)
    parser.add_argument("--height", type=int, required=True)
    parser.add_argument("--timeout-seconds", type=float, default=8.0)
    arguments = parser.parse_args()
    if arguments.width < 100 or arguments.height < 100:
        raise SystemExit("Requested window dimensions are implausibly small")

    x11 = x11_api()
    display = x11.XOpenDisplay(arguments.display.encode("utf-8"))
    if not display:
        raise SystemExit(f"Could not open X11 display {arguments.display}")
    try:
        deadline = time.monotonic() + arguments.timeout_seconds
        window = None
        while time.monotonic() < deadline:
            window = find_named_window(arguments.display, arguments.title)
            if window:
                break
            time.sleep(0.10)
        if not window:
            raise SystemExit(f"Timed out waiting for X11 window containing title: {arguments.title}")
        x11.XMoveResizeWindow(display, window, 0, 0, arguments.width, arguments.height)
        x11.XFlush(display)
        print(f"Resized X11 window 0x{window.value:x} ({arguments.title}) to {arguments.width}x{arguments.height}")
    finally:
        x11.XCloseDisplay(display)


if __name__ == "__main__":
    main()
