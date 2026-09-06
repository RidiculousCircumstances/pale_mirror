"""Private TightVNC credential access over the pinned Windows SSH connection."""

from __future__ import annotations

import base64
from pathlib import PureWindowsPath
import re
import subprocess

from blender_common import BlenderConfigurationError, BlenderSettings, ssh_arguments


_SECRET_FILE = "pm-vnc-secrets.dpapi"
_VIEWER_SECRET = re.compile(r"[A-Za-z0-9]{8}\Z")
_REMOTE_ERROR = re.compile(r"PM_VNC_ERROR_B64:([A-Za-z0-9+/=]+)")


def windows_private_path(settings: BlenderSettings, filename: str = _SECRET_FILE) -> str:
    """Return the fixed private-host location without accepting a caller path."""
    workspace = PureWindowsPath(settings.windows_workspace)
    try:
        host_root = workspace.parents[1]
    except IndexError as error:
        raise BlenderConfigurationError("Windows workspace is too shallow to contain the private authoring root.") from error
    if not host_root.drive or any(part in {".", ".."} for part in host_root.parts):
        raise BlenderConfigurationError("Windows workspace does not have a safe private-host root.")
    return str(host_root / "private" / filename)


def encoded_powershell(script: str) -> str:
    return base64.b64encode(script.encode("utf-16le")).decode("ascii")


def run_windows_powershell(settings: BlenderSettings, script: str) -> subprocess.CompletedProcess[str]:
    """Run a repository-owned PowerShell command over the pinned SSH channel."""
    script = "[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false); $OutputEncoding = [Console]::OutputEncoding\n" + script
    encoded = encoded_powershell(script)
    # Windows OpenSSH starts the requested command through cmd.exe, whose
    # command-line limit is far below a full MSI provisioning script.  Keep the
    # remote command constant and send the UTF-16LE script through SSH stdin.
    # This preserves PowerShell's encoded-command semantics without exposing a
    # secret-bearing installer property in either shell history.
    command = (
        "powershell.exe -NoProfile -NonInteractive -Command "
        '\"$pmEncoded=[Console]::In.ReadToEnd(); '
        "& ([ScriptBlock]::Create([Text.Encoding]::Unicode.GetString("
        '[Convert]::FromBase64String($pmEncoded))))\"'
    )
    result = subprocess.run(
        ssh_arguments(settings)
        + [
            settings.target,
            command,
        ],
        input=encoded,
        check=False,
        capture_output=True,
        text=True,
        errors="replace",
    )
    if result.returncode:
        encoded_error = _REMOTE_ERROR.search(result.stdout)
        if encoded_error:
            detail = base64.b64decode(encoded_error.group(1)).decode("utf-8", errors="replace")
        else:
            detail = result.stderr.strip() or result.stdout.strip() or f"exit status {result.returncode}"
        raise BlenderConfigurationError(f"Windows PowerShell command failed: {detail}")
    return result


def viewer_password(settings: BlenderSettings) -> str:
    """Decrypt the VNC viewer secret in the Windows user's DPAPI context.

    The password is never persisted or printed on Linux.  It travels only inside
    the already authenticated SSH channel and lives in this process until the
    VNC client connects.
    """
    secret_path = windows_private_path(settings).replace("'", "''")
    script = f"""
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$secretPath = '{secret_path}'
if (-not (Test-Path -LiteralPath $secretPath -PathType Leaf)) {{
  throw 'Pale Mirror VNC credentials have not been provisioned on the Windows host.'
}}
$ciphertext = [System.IO.File]::ReadAllBytes($secretPath)
$plaintext = [System.Security.Cryptography.ProtectedData]::Unprotect(
  $ciphertext, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
$record = [System.Text.Encoding]::UTF8.GetString($plaintext) | ConvertFrom-Json
if ([string]::IsNullOrWhiteSpace([string]$record.viewer)) {{
  throw 'Pale Mirror VNC credential record has no viewer secret.'
}}
[Console]::Out.Write([string]$record.viewer)
"""
    result = run_windows_powershell(settings, script)
    password = result.stdout.strip()
    if not _VIEWER_SECRET.fullmatch(password):
        raise BlenderConfigurationError("Windows returned an invalid VNC viewer credential.")
    return password
