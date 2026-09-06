"""Restricted Windows-native UI bridge for the private Blender desktop.

The bridge uses a repository-owned Session-1 task and a fixed JSON request /
receipt protocol.  It intentionally exposes no arbitrary PowerShell, WinApp,
mouse, keyboard, window title or remote-path parameter to its callers.
"""

from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path, PureWindowsPath
import re
import subprocess
import time
from typing import Any

from PIL import Image

from blender_common import BlenderConfigurationError, BlenderSettings, ssh_arguments
from vnc_common import run_windows_powershell


ROOT = Path(__file__).resolve().parents[2]
WORKER_SOURCE = ROOT / "tools" / "blender" / "windows" / "pm_winapp_console.ps1"
TASK_NAME = "PaleMirror.WinAppConsole"
WINAPP_VERSION = "0.6.0"
WINAPP_EXECUTABLE_SHA256 = "0aa2a43086ad07c7fdc35c94ecca0a586e815b1ab46e9b3109d73123e9190c90"
_LABEL = re.compile(r"[a-z0-9][a-z0-9_-]{0,79}\Z")
_CAPTURE_NAME = re.compile(r"winapp-[a-z0-9][a-z0-9_-]{0,79}\.png\Z")
_REQUEST_FILE = "pm-winapp-request.json"
_RECEIPT_FILE = "pm-winapp-receipt.json"
_WORKER_FILE = "pm-winapp-console.ps1"


def private_root(settings: BlenderSettings) -> PureWindowsPath:
    """Derive the fixed private root from the validated PM workspace path."""
    workspace = PureWindowsPath(settings.windows_workspace)
    try:
        root = workspace.parents[1]
    except IndexError as error:
        raise BlenderConfigurationError("Windows workspace is too shallow to contain the private authoring root.") from error
    if not root.drive or any(part in {".", ".."} for part in root.parts):
        raise BlenderConfigurationError("Windows workspace does not have a safe private authoring root.")
    return root / "private"


def provision_console(settings: BlenderSettings) -> dict[str, Any]:
    """Verify pinned WinApp, deploy the fixed worker and register its task."""
    if not WORKER_SOURCE.is_file():
        raise BlenderConfigurationError(f"WinApp console worker is missing: {WORKER_SOURCE}")
    root = private_root(settings)
    worker_path = root / "tools" / _WORKER_FILE
    expected_worker_hash = _sha256(WORKER_SOURCE)
    preflight = _preflight(settings, root, worker_path)
    if preflight["worker_sha256"] != expected_worker_hash:
        _copy(settings, WORKER_SOURCE, worker_path)
    verified = _preflight(settings, root, worker_path)
    if verified["worker_sha256"] != expected_worker_hash:
        raise BlenderConfigurationError("Windows WinApp worker hash differs after verified copy.")
    return _register_task(settings, worker_path, verified)


def execute(
    settings: BlenderSettings,
    operation: str,
    *,
    label: str | None = None,
    action: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Execute one allowlisted worker operation and return its fixed receipt."""
    if operation not in {"capture_blender", "dismiss_tightvnc_firewall", "open_collector_production_baseline", "open_collector_production_master", "restart_collector_production_baseline", "confirm_blender_first_run_preferences", "select_collector_master_grab", "sculpt_collector_master_leading_mantle_grounding_v01", "interactive_collector_master", "inspect_collector_master_ui", "inspect_collector_master_windows", "dismiss_collector_master_stale_crash_dialog"}:
        raise BlenderConfigurationError("WinApp operation is not allowlisted.")
    request: dict[str, Any] = {"schema": "pale_mirror.winapp_console_request.v1", "operation": operation}
    if operation == "capture_blender":
        if label is None or not _LABEL.fullmatch(label):
            raise BlenderConfigurationError("capture label must be 1-80 lowercase letters, digits, underscores or dashes.")
        request["label"] = label
    elif label is not None:
        raise BlenderConfigurationError(f"{operation} does not accept a label.")
    if operation == "interactive_collector_master":
        if not isinstance(action, dict):
            raise BlenderConfigurationError("interactive_collector_master requires a structured Blender-only action.")
        interaction_id = action.get("id")
        kind = action.get("kind")
        if not isinstance(interaction_id, str) or not re.fullmatch(r"[a-z][a-z0-9_-]{0,47}", interaction_id):
            raise BlenderConfigurationError("artist interaction id must be 1-48 lowercase-safe characters.")
        if kind not in {"key", "pen", "drag", "click"}:
            raise BlenderConfigurationError("artist interaction kind must be key, pen, drag or click.")
        request["action"] = action
    elif action is not None:
        raise BlenderConfigurationError(f"{operation} does not accept an artist action.")
    root = private_root(settings)
    payload = base64.b64encode(json.dumps(request, separators=(",", ":")).encode("utf-8")).decode("ascii")
    request_path = root / "captures" / _REQUEST_FILE
    receipt_path = root / "captures" / _RECEIPT_FILE
    script = f"""
$ErrorActionPreference = 'Stop'
$requestPath = '{request_path}'
$receiptPath = '{receipt_path}'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $requestPath) | Out-Null
Remove-Item -LiteralPath $receiptPath -Force -ErrorAction SilentlyContinue
[System.IO.File]::WriteAllBytes($requestPath, [Convert]::FromBase64String('{payload}'))
Start-ScheduledTask -TaskName '{TASK_NAME}'
$deadline = [DateTime]::UtcNow.AddSeconds(20)
do {{
  Start-Sleep -Milliseconds 250
  if (Test-Path -LiteralPath $receiptPath -PathType Leaf) {{ break }}
}} while ([DateTime]::UtcNow -lt $deadline)
if (-not (Test-Path -LiteralPath $receiptPath -PathType Leaf)) {{ throw 'WinApp Session-1 task did not write a receipt within 20 seconds.' }}
Get-Content -LiteralPath $receiptPath -Raw
"""
    completed = run_windows_powershell(settings, script)
    try:
        receipt = json.loads(completed.stdout)
    except json.JSONDecodeError as error:
        raise BlenderConfigurationError("WinApp Session-1 task returned an invalid receipt.") from error
    if not isinstance(receipt, dict) or receipt.get("schema") != "pale_mirror.winapp_console_receipt.v1":
        raise BlenderConfigurationError("WinApp Session-1 task returned an unexpected receipt schema.")
    if receipt.get("operation") != operation:
        raise BlenderConfigurationError("WinApp Session-1 task receipt operation differs from the request.")
    if receipt.get("session_id") != 1:
        raise BlenderConfigurationError("WinApp task did not run in the expected interactive Session 1.")
    if receipt.get("success") is not True:
        detail = receipt.get("error", "unknown Session-1 worker failure")
        raise BlenderConfigurationError(f"WinApp Session-1 task failed: {detail}")
    return receipt


def fetch_capture(settings: BlenderSettings, receipt: dict[str, Any], destination_root: Path) -> Path:
    """Fetch only a worker-declared capture name into the ignored audit root."""
    capture_name = receipt.get("capture_name")
    if not isinstance(capture_name, str) or not _CAPTURE_NAME.fullmatch(capture_name):
        raise BlenderConfigurationError("WinApp receipt has no safe capture filename.")
    source = private_root(settings) / "captures" / capture_name
    destination_root.mkdir(parents=True, exist_ok=True)
    target = destination_root / capture_name
    _copy_from(settings, source, target)
    if not target.is_file() or target.stat().st_size <= 0:
        raise BlenderConfigurationError("WinApp capture transfer produced no local PNG.")
    return target


def frame_has_visible_pixels(path: Path) -> bool:
    """Return whether a decoded lossless Screen-DC frame is not entirely black."""
    with Image.open(path) as image:
        return image.convert("RGB").getbbox() is not None


def _preflight(settings: BlenderSettings, root: PureWindowsPath, worker_path: PureWindowsPath) -> dict[str, Any]:
    script = f"""
$ErrorActionPreference = 'Stop'
$privateRoot = '{root}'
$winapp = Join-Path $privateRoot 'tools\\winappcli-v{WINAPP_VERSION}\\winapp.exe'
$worker = '{worker_path}'
if (-not (Test-Path -LiteralPath $winapp -PathType Leaf)) {{ throw 'Pinned WinApp CLI is absent from the private Windows host.' }}
$exeHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $winapp).Hash.ToLowerInvariant()
if ($exeHash -ne '{WINAPP_EXECUTABLE_SHA256}') {{ throw "Pinned WinApp executable SHA-256 differs: $exeHash" }}
$signature = Get-AuthenticodeSignature -FilePath $winapp
if ($signature.Status.ToString() -ne 'Valid' -or $signature.SignerCertificate.Subject -notlike '*CN=Microsoft Corporation*') {{ throw 'Pinned WinApp executable lacks the expected valid Microsoft Authenticode signature.' }}
[ordered]@{{
  winapp_version = (& $winapp --version).Trim()
  executable_sha256 = $exeHash
  worker_sha256 = if (Test-Path -LiteralPath $worker -PathType Leaf) {{ (Get-FileHash -Algorithm SHA256 -LiteralPath $worker).Hash.ToLowerInvariant() }} else {{ $null }}
}} | ConvertTo-Json -Compress
"""
    try:
        record = json.loads(run_windows_powershell(settings, script).stdout)
    except json.JSONDecodeError as error:
        raise BlenderConfigurationError("WinApp preflight did not return JSON.") from error
    if record.get("winapp_version") != WINAPP_VERSION:
        raise BlenderConfigurationError("Pinned WinApp CLI reports an unexpected version.")
    return record


def _register_task(settings: BlenderSettings, worker_path: PureWindowsPath, verified: dict[str, Any]) -> dict[str, Any]:
    script = f"""
$ErrorActionPreference = 'Stop'
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$worker = '{worker_path}'
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$worker`""
$principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Highest
$taskSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::FromMinutes(2)) -MultipleInstances IgnoreNew
Register-ScheduledTask -TaskName '{TASK_NAME}' -Action $action -Principal $principal -Settings $taskSettings -Force | Out-Null
$task = Get-ScheduledTask -TaskName '{TASK_NAME}'
[ordered]@{{
  schema = 'pale_mirror.winapp_console_setup.v1'
  task = '{TASK_NAME}'
  state = $task.State.ToString()
  user = $task.Principal.UserId
  logon_type = $task.Principal.LogonType.ToString()
  action = $task.Actions[0].Execute
  arguments = $task.Actions[0].Arguments
  worker_sha256 = '{verified["worker_sha256"]}'
  executable_sha256 = '{verified["executable_sha256"]}'
}} | ConvertTo-Json -Compress
"""
    try:
        record = json.loads(run_windows_powershell(settings, script).stdout)
    except json.JSONDecodeError as error:
        raise BlenderConfigurationError("WinApp task registration did not return JSON.") from error
    if record.get("state") not in {"Ready", "Running"} or record.get("logon_type") != "Interactive":
        raise BlenderConfigurationError("WinApp task did not register as an interactive Session-1 task.")
    return record


def _copy(settings: BlenderSettings, source: Path, target: PureWindowsPath) -> None:
    subprocess.run(
        _scp_base(settings) + [str(source), f"{settings.target}:{_scp_remote_path(target)}"],
        check=True,
    )


def _copy_from(settings: BlenderSettings, source: PureWindowsPath, target: Path) -> None:
    subprocess.run(
        _scp_base(settings) + [f"{settings.target}:{_scp_remote_path(source)}", str(target)],
        check=True,
    )


def _scp_base(settings: BlenderSettings) -> list[str]:
    return [
        "scp", "-F", "/dev/null", "-i", str(settings.identity_file),
        "-o", "BatchMode=yes", "-o", "PasswordAuthentication=no", "-o", "KbdInteractiveAuthentication=no",
        "-o", "StrictHostKeyChecking=yes", "-o", f"UserKnownHostsFile={settings.known_hosts_file}",
        "-o", "GlobalKnownHostsFile=/dev/null", "-o", "ConnectTimeout=8", "-p",
    ]


def _scp_remote_path(path: PureWindowsPath) -> str:
    """Make a fixed Windows path unambiguous to OpenSSH's SCP parser."""
    return str(path).replace("\\", "/")


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()
