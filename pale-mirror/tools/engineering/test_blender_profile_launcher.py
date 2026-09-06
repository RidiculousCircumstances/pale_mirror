#!/usr/bin/env python3
"""Static contract for the narrowly owned private Blender profile launcher."""

from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "tools" / "blender" / "setup_windows_blender_profile.py"
text = SOURCE.read_text(encoding="utf-8")
ast.parse(text, filename=str(SOURCE))

assert "--online-mode" in text
assert r"E:\PaleMirror\bin\pm-blender.cmd" in text
assert r"E:\\PaleMirror\\profile\\extensions\\user_default\\mcp" in text
assert "Private Blender launcher differs from its two reviewed safe forms." in text
assert "New-NetFirewallRule" not in text
assert "subprocess" not in text
assert "--check" in text

print("private Blender profile launcher contracts passed")
