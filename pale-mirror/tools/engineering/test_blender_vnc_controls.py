#!/usr/bin/env python3
"""Static contract for the private loopback-only Blender VNC control path."""

from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools/blender"
SETUP = TOOLS / "setup_windows_tightvnc.py"
GUI = TOOLS / "pm_vnc_gui.py"
COMMON = TOOLS / "vnc_common.py"
TRANSPORT = TOOLS / "blender_common.py"
README = TOOLS / "README.md"
PROJECT = TOOLS / "pyproject.toml"


def read_python(path: Path) -> str:
    source = path.read_text(encoding="utf-8")
    ast.parse(source, filename=str(path))
    return source


setup = read_python(SETUP)
gui = read_python(GUI)
common = read_python(COMMON)
transport = read_python(TRANSPORT)

for text in (setup, gui, common, transport):
    assert "0.0.0.0" not in text

assert "SERVER_REGISTER_AS_SERVICE=0" in setup
assert "SERVER_ADD_FIREWALL_EXCEPTION=0" in setup
assert "Set-Service -Name 'tvnserver' -StartupType Disabled" in setup
assert "New-ScheduledTaskPrincipal" in setup
assert "-LogonType Interactive" in setup
assert "New-ScheduledTaskTrigger -AtLogOn" in setup
assert "LoopbackOnly" in setup
assert "UseD3D = 0" in setup
assert "UseMirrorDriver = 0" in setup
assert "EnableFileTransfers" in setup
assert "listenerProcess.SessionId -eq 0" in setup
assert "DataProtectionScope]::CurrentUser" in setup
assert "print(" not in common
assert "_REMOTE_ERROR" in common
assert "b64decode(encoded_error.group(1))" in common

assert "127.0.0.1:{local_port}:127.0.0.1:{remote_port}" in transport
assert "ensure_vnc_tunnel" in transport
assert "--require-visible" in gui
assert "_frame_has_nonblack_pixels" in gui
assert "getbbox() is not None" in gui
assert "api.shutdown()" in gui
assert "_REQUEST_TIMEOUT_SECONDS = 20" in gui
assert "timeout=_REQUEST_TIMEOUT_SECONDS" in gui
assert "VNC did not produce a control response within" in gui
assert "{0,2}" in gui
assert "vncdotool==1.2.0" in PROJECT.read_text(encoding="utf-8")
assert '"capture_backend": "standard_gdi"' in setup
assert "framebuffer is entirely black" in README.read_text(encoding="utf-8")

print("private Blender VNC control contracts passed")
