#!/usr/bin/env python3
"""Static contract for the restricted native Windows Blender visual bridge."""

from __future__ import annotations

import ast
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOLS = ROOT / "tools" / "blender"
COMMON = TOOLS / "winapp_common.py"
CLI = TOOLS / "pm_winapp_gui.py"
SETUP = TOOLS / "setup_windows_winapp.py"
WORKER = TOOLS / "windows" / "pm_winapp_console.ps1"


def read_python(path: Path) -> str:
    source = path.read_text(encoding="utf-8")
    ast.parse(source, filename=str(path))
    return source


common = read_python(COMMON)
cli = read_python(CLI)
setup = read_python(SETUP)
worker = WORKER.read_text(encoding="utf-8")

for text in (common, cli, setup, worker):
    assert "0.0.0.0" not in text
for text in (common, cli):
    assert "arbitrary" in text.lower()

assert "PaleMirror.WinAppConsole" in common
assert "-LogonType Interactive" in common
assert "WINAPP_VERSION = \"0.6.0\"" in common
assert "WINAPP_EXECUTABLE_SHA256" in common
assert "Get-AuthenticodeSignature" in common
assert "StrictHostKeyChecking=yes" in common
assert "capture_blender" in common
assert "dismiss_tightvnc_firewall" in common
assert "open_collector_production_baseline" in common
assert "open_collector_production_master" in common
assert "restart_collector_production_baseline" in common
assert "confirm_blender_first_run_preferences" in common
assert "select_collector_master_grab" in common
assert "sculpt_collector_master_leading_mantle_grounding_v01" in common
assert "interactive_collector_master" in common
assert "inspect_collector_master_windows" in common
assert "dismiss_collector_master_stale_crash_dialog" in common
assert "capture_blender" in worker
assert "dismiss_tightvnc_firewall" in worker
assert "open_collector_production_baseline" in worker
assert "Open-PmCollectorProductionMaster" in worker
assert "restart_collector_production_baseline" in worker
assert "pm-blender.cmd" in worker
assert "confirm_blender_first_run_preferences" in worker
assert "Select-PmCollectorMasterGrab" in worker
assert "select_collector_master_grab" in worker
assert "Invoke-PmCollectorMasterLeadingMantleGrounding" in worker
assert "leading_mantle_grounding_v01" in worker
assert "ui', 'pen'" in worker
assert "680,650 664,690 642,734 618,776" in worker
assert "already has a receipt" in worker
assert "Invoke-PmCollectorMasterArtistInput" in worker
assert "Get-PmCollectorMasterArtistWindow" in worker
assert "interactive_collector_master" in worker
assert "Inspect-PmCollectorMasterArtistWindows" in worker
assert "Dismiss-PmCollectorMasterStaleCrashDialog" in worker
assert "View Crash Log" in worker
assert "artist interaction id" in worker.lower()
assert "ctrl\\+alt\\+del" in worker.lower()
assert "win\\+" in worker.lower()
assert "--capture-screen" in worker
assert "--focus" in worker
assert "ui', 'click', $localizedCancel" in worker
assert "0x041E" in worker
assert "TightVNC Server" in worker
assert "Assert-PmNoTightVncFirewallRule" in worker
assert "Get-NetFirewallRule" in worker
assert "New-NetFirewallRule" not in worker
assert "send-keys', 'enter'" in worker
assert "send-keys', 'g'" in worker
assert "send-keys', $keys" in worker
assert "Artist key request is malformed or a system key" in worker
assert "'drag'" in worker
assert "'click'" in worker
assert "Assert-PmArtistPoint $from" in worker
assert "Assert-PmArtistPoint $to" in worker
assert "Invoke-PmCollectorMasterArtistClick" in worker
assert "Focus-PmCollectorMasterArtistWindow" in worker
assert "SetForegroundWindow" in worker
assert "mouse_event" in worker
assert "GetDpiForWindow" in worker
assert "capture_point" in worker
assert "set-value" not in worker
assert "\"capture\"" in cli
assert "open-collector-production-baseline" in cli
assert "open-collector-production-master" in cli
assert "restart-collector-production-baseline" in cli
assert "confirm-blender-first-run-preferences" in cli
assert "select-collector-master-grab" in cli
assert "sculpt-collector-master-leading-mantle" in cli
assert "artist-key" in cli
assert "artist-pen" in cli
assert "artist-drag" in cli
assert "artist-click" in cli
assert "artist-inspect-windows" in cli
assert "dismiss-collector-master-stale-crash-dialog" in cli
assert "dismiss-tightvnc-firewall" in cli
assert "--require-visible" in cli
assert "frame_has_visible_pixels" in cli
assert "provision_console" in setup

print("private Blender WinApp control contracts passed")
