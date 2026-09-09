#!/usr/bin/env python3
"""Prove that the private Xvfb can create the GLX context a GLFW client needs."""

import ctypes
import json
import os
import sys


GLX_RGBA = 4
GLX_DEPTH_SIZE = 12
GL_VERSION = 0x1F02


def fail(reason):
    print(json.dumps({"schema": 1, "status": "rejected", "reason": reason}), file=sys.stderr)
    raise SystemExit(1)


def main():
    display_name = os.environ.get("DISPLAY")
    if not display_name:
        fail("display is absent")
    try:
        x11 = ctypes.CDLL("libX11.so.6")
        gl = ctypes.CDLL("libGL.so.1")
    except OSError as error:
        fail(f"GLX libraries are unavailable: {error}")

    x11.XOpenDisplay.argtypes = [ctypes.c_char_p]
    x11.XOpenDisplay.restype = ctypes.c_void_p
    x11.XDefaultScreen.argtypes = [ctypes.c_void_p]
    x11.XDefaultScreen.restype = ctypes.c_int
    x11.XRootWindow.argtypes = [ctypes.c_void_p, ctypes.c_int]
    x11.XRootWindow.restype = ctypes.c_ulong
    x11.XFree.argtypes = [ctypes.c_void_p]
    x11.XCloseDisplay.argtypes = [ctypes.c_void_p]

    gl.glXChooseVisual.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.POINTER(ctypes.c_int)]
    gl.glXChooseVisual.restype = ctypes.c_void_p
    gl.glXCreateContext.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_int]
    gl.glXCreateContext.restype = ctypes.c_void_p
    gl.glXMakeCurrent.argtypes = [ctypes.c_void_p, ctypes.c_ulong, ctypes.c_void_p]
    gl.glXMakeCurrent.restype = ctypes.c_int
    gl.glXDestroyContext.argtypes = [ctypes.c_void_p, ctypes.c_void_p]
    gl.glGetString.argtypes = [ctypes.c_uint]
    gl.glGetString.restype = ctypes.c_char_p

    display = x11.XOpenDisplay(display_name.encode())
    if not display:
        fail("XOpenDisplay was rejected")
    visual = context = None
    try:
        screen = x11.XDefaultScreen(display)
        attributes = (ctypes.c_int * 5)(GLX_RGBA, GLX_DEPTH_SIZE, 24, 0, 0)
        visual = gl.glXChooseVisual(display, screen, attributes)
        if not visual:
            fail("glXChooseVisual was rejected")
        context = gl.glXCreateContext(display, visual, None, 1)
        if not context:
            fail("glXCreateContext was rejected")
        if not gl.glXMakeCurrent(display, x11.XRootWindow(display, screen), context):
            fail("glXMakeCurrent was rejected")
        version = gl.glGetString(GL_VERSION)
        if not version:
            fail("glGetString(GL_VERSION) was rejected")
        print(json.dumps({"schema": 1, "status": "admitted", "display": display_name,
                          "glVersion": version.decode("utf-8", "replace")}))
    finally:
        if context:
            gl.glXMakeCurrent(display, 0, None)
            gl.glXDestroyContext(display, context)
        if visual:
            x11.XFree(visual)
        x11.XCloseDisplay(display)


if __name__ == "__main__":
    main()
