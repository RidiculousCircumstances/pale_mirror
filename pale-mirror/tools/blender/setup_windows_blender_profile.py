"""Provision the fixed private Blender launcher required by the MCP extension.

The official Blender MCP add-on refuses even loopback TCP while Blender's
``online_access`` flag is false.  This installer owns one private launcher only;
it never touches global Blender configuration, Windows firewall state or a
Pale Mirror asset.
"""

from __future__ import annotations

import argparse
import base64
import hashlib
import json

from blender_common import BlenderConfigurationError, load_settings
from vnc_common import run_windows_powershell


LAUNCHER = r"E:\PaleMirror\bin\pm-blender.cmd"
BLENDER = r"E:\PaleMirror\Blender\5.1.2\blender-5.1.2-windows-x64\blender.exe"
EXPECTED_LEGACY = """@echo off
setlocal
set \"PM_ROOT=E:\\PaleMirror\"
set \"BLENDER_USER_CONFIG=%PM_ROOT%\\profile\\config\"
set \"BLENDER_USER_SCRIPTS=%PM_ROOT%\\profile\\scripts\"
set \"BLENDER_USER_EXTENSIONS=%PM_ROOT%\\profile\\extensions\"
set \"BLENDER_USER_DATAFILES=%PM_ROOT%\\profile\\datafiles\"
\"%PM_ROOT%\\Blender\\5.1.2\\blender-5.1.2-windows-x64\\blender.exe\" %*
"""
DESIRED = EXPECTED_LEGACY.replace('blender.exe" %*', 'blender.exe" --online-mode %*')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify the private launcher without changing it.")
    arguments = parser.parse_args()
    settings = load_settings()
    desired_hash = _sha256(DESIRED.encode("ascii"))
    legacy_encoded = base64.b64encode(EXPECTED_LEGACY.encode("ascii")).decode("ascii")
    encoded = base64.b64encode(DESIRED.encode("ascii")).decode("ascii")
    mode = "check" if arguments.check else "provision"
    script = f"""
$ErrorActionPreference = 'Stop'
$launcher = '{LAUNCHER}'
$blender = '{BLENDER}'
$expectedLegacy = [Text.Encoding]::ASCII.GetString([Convert]::FromBase64String('{legacy_encoded}'))
$desired = [Text.Encoding]::ASCII.GetString([Convert]::FromBase64String('{encoded}'))
if (-not (Test-Path -LiteralPath $blender -PathType Leaf)) {{ throw 'Pinned Blender executable is absent.' }}
if (-not (Test-Path -LiteralPath 'E:\\PaleMirror\\profile\\extensions\\user_default\\mcp' -PathType Container)) {{ throw 'Pinned official MCP extension is absent.' }}
if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {{ throw 'Private Blender launcher is absent.' }}
$current = [IO.File]::ReadAllText($launcher, [Text.Encoding]::ASCII).Replace("`r`n", "`n")
if ($current -ne $expectedLegacy -and $current -ne $desired) {{ throw 'Private Blender launcher differs from its two reviewed safe forms.' }}
if ('{mode}' -eq 'provision' -and $current -ne $desired) {{ [IO.File]::WriteAllText($launcher, $desired, [Text.Encoding]::ASCII) }}
$final = [IO.File]::ReadAllText($launcher, [Text.Encoding]::ASCII).Replace("`r`n", "`n")
[ordered]@{{
  schema = 'pale_mirror.blender_profile_launcher.v1'
  online_mode = $final -eq $desired
  launcher_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $launcher).Hash.ToLowerInvariant()
  expected_sha256 = '{desired_hash}'
}} | ConvertTo-Json -Compress
"""
    record = json.loads(run_windows_powershell(settings, script).stdout)
    if record.get("online_mode") is not True or record.get("launcher_sha256") != desired_hash:
        raise BlenderConfigurationError("Private Blender profile launcher is not pinned to the reviewed online-mode form.")
    print(json.dumps(record, sort_keys=True))


def _sha256(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


if __name__ == "__main__":
    main()
