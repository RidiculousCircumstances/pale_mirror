"""Provision a private, loopback-only TightVNC Blender desktop on Windows.

The visible artist desktop is an interactive application task, never a Windows
service. The only listener is Windows loopback; Linux reaches it through the
pinned SSH key. Viewer/control passwords are generated once and held only in a
DPAPI record outside this repository. The individual provisioning phases stay
small deliberately: Windows OpenSSH can otherwise retain a cancelled large
PowerShell payload after the caller has exited.
"""

from __future__ import annotations

import json
import sys

from blender_common import BlenderSettings, load_settings
from vnc_common import run_windows_powershell, windows_private_path


TIGHTVNC_VERSION = "2.8.88"
TIGHTVNC_MSI_URL = "https://www.tightvnc.com/download/2.8.88/tightvnc-2.8.88-gpl-setup-64bit.msi"
TIGHTVNC_MSI_SHA256 = "fa86d817ac29c5ffe1e8e7095e738d9ba5ca28aa62304ac234580916622a8ca2"
_TASK = "PaleMirror.VncConsole"


def main() -> None:
    settings = load_settings()
    secret_path = windows_private_path(settings).replace("'", "''")
    private_root = secret_path.rsplit("\\", 1)[0].replace("'", "''")
    installer_path = f"{private_root}\\installers\\tightvnc-{TIGHTVNC_VERSION}-setup-64bit.msi".replace("'", "''")

    secret = _phase(settings, "credentials", _credential_status_script(secret_path))
    if secret["missing"]:
        secret = _phase(settings, "create credentials", _credential_creation_script(secret_path, private_root))
    installation = _phase(settings, "installer status", _installer_status_script(installer_path))
    if installation["installation_required"]:
        installation = _phase(settings, "installer", _installer_script(secret_path, installer_path))
    _phase(settings, "application policy", _application_policy_script())
    policy = _phase(settings, "service policy", _service_policy_script())
    _phase(settings, "desktop quiesce", _desktop_quiesce_script())
    _phase(settings, "desktop registration", _desktop_registration_script())
    desktop = _phase(settings, "interactive desktop", _desktop_launch_script())

    result = {
        "schema": "pale_mirror.tightvnc_setup.v1",
        "version": TIGHTVNC_VERSION,
        "credential_created": secret["credential_created"],
        "installer_applied": installation["installer_applied"],
        "installer_sha256": installation["installer_sha256"],
        "listener_addresses": desktop["listener_addresses"],
        "listener_session": desktop["listener_session"],
        "console_task_state": desktop["console_task_state"],
        "capture_backend": "standard_gdi",
        "d3d_enabled": False,
        "mirror_driver_enabled": False,
        "service_status": policy["service_status"],
        "firewall_exception_requested": False,
        "loopback_only": True,
    }
    if result["console_task_state"] != "Running":
        raise RuntimeError(f"TightVNC postcondition failed: {result}")
    print(json.dumps(result, indent=2, sort_keys=True))


def _phase(settings: BlenderSettings, name: str, script: str) -> dict[str, object]:
    """Run one bounded repository-owned provisioning phase and parse its JSON."""
    print(f"TightVNC: {name}…", file=sys.stderr, flush=True)
    completed = run_windows_powershell(settings, script)
    try:
        result = json.loads(completed.stdout.strip().splitlines()[-1])
    except (IndexError, json.JSONDecodeError) as error:
        raise RuntimeError(f"TightVNC {name} phase did not return a JSON receipt.") from error
    print(f"TightVNC: {name} complete.", file=sys.stderr, flush=True)
    return result


def _credential_status_script(secret_path: str) -> str:
    return f"""
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$secretPath = '{secret_path}'
if (-not (Test-Path -LiteralPath $secretPath -PathType Leaf)) {{ [ordered]@{{ missing = $true; credential_created = $false }} | ConvertTo-Json -Compress; exit 0 }}
$ciphertext = [System.IO.File]::ReadAllBytes($secretPath)
$plain = [System.Security.Cryptography.ProtectedData]::Unprotect($ciphertext, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
$record = [System.Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
if (([string]$record.viewer).Length -ne 8 -or ([string]$record.control).Length -ne 8) {{ throw 'The DPAPI VNC credential record is malformed.' }}
[ordered]@{{ missing = $false; credential_created = $false }} | ConvertTo-Json -Compress
"""


def _credential_creation_script(secret_path: str, private_root: str) -> str:
    return f"""
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$privateRoot = '{private_root}'
$secretPath = '{secret_path}'
function Set-PmPrivateAcl([string]$Path) {{
  New-Item -ItemType Directory -Force -Path $Path | Out-Null
  $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
  & icacls.exe $Path /inheritance:r /grant:r "${{identity}}:(OI)(CI)F" '*S-1-5-32-544:(OI)(CI)F' '*S-1-5-18:(OI)(CI)F' | Out-Null
  if ($LASTEXITCODE -ne 0) {{ throw "Could not set private VNC credential ACL (icacls $LASTEXITCODE)." }}
}}
function New-PmVncSecret {{
  $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789'.ToCharArray()
  $bytes = New-Object byte[] 8
  [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
  return -join ($bytes | ForEach-Object {{ $alphabet[$_ % $alphabet.Length] }})
}}
Set-PmPrivateAcl $privateRoot
$credentialCreated = $false
if (-not (Test-Path -LiteralPath $secretPath -PathType Leaf)) {{
  $record = [ordered]@{{ schema = 'pale_mirror.vnc_secret.v1'; viewer = (New-PmVncSecret); control = (New-PmVncSecret) }}
  $plain = [System.Text.Encoding]::UTF8.GetBytes(($record | ConvertTo-Json -Compress))
  $cipher = [System.Security.Cryptography.ProtectedData]::Protect($plain, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
  [System.IO.File]::WriteAllBytes($secretPath, $cipher)
  $credentialCreated = $true
}}
$ciphertext = [System.IO.File]::ReadAllBytes($secretPath)
$plain = [System.Security.Cryptography.ProtectedData]::Unprotect($ciphertext, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
$record = [System.Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
if (([string]$record.viewer).Length -ne 8 -or ([string]$record.control).Length -ne 8) {{ throw 'The DPAPI VNC credential record is malformed.' }}
[ordered]@{{ missing = $false; credential_created = $credentialCreated }} | ConvertTo-Json -Compress
"""


def _installer_status_script(installer_path: str) -> str:
    return f"""
$ErrorActionPreference = 'Stop'
$installerPath = '{installer_path}'
$expectedInstallerHash = '{TIGHTVNC_MSI_SHA256}'
$serverPath = Join-Path $env:ProgramFiles 'TightVNC\\tvnserver.exe'
$installedVersion = if (Test-Path -LiteralPath $serverPath -PathType Leaf) {{
  @((Get-Item -LiteralPath $serverPath).VersionInfo.ProductVersion -split '[^0-9]+' | Where-Object {{ $_ -ne '' }}) -join '.'
}} else {{ $null }}
$installationRequired = $installedVersion -ne '{TIGHTVNC_VERSION}.0'
if ((-not $installationRequired) -and (Test-Path -LiteralPath $installerPath -PathType Leaf)) {{
  $installerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $installerPath).Hash.ToLowerInvariant()
  if ($installerHash -ne $expectedInstallerHash) {{ throw 'The cached TightVNC installer does not match the pinned checksum.' }}
}} else {{
  $installerHash = $expectedInstallerHash
}}
[ordered]@{{ installation_required = $installationRequired; installer_applied = $false; installer_sha256 = $installerHash }} | ConvertTo-Json -Compress
"""


def _installer_script(secret_path: str, installer_path: str) -> str:
    return f"""
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Security
$secretPath = '{secret_path}'
$installerPath = '{installer_path}'
$downloadUrl = '{TIGHTVNC_MSI_URL}'
$expectedInstallerHash = '{TIGHTVNC_MSI_SHA256}'
$serverPath = Join-Path $env:ProgramFiles 'TightVNC\\tvnserver.exe'
$installedVersion = if (Test-Path -LiteralPath $serverPath -PathType Leaf) {{
  @((Get-Item -LiteralPath $serverPath).VersionInfo.ProductVersion -split '[^0-9]+' | Where-Object {{ $_ -ne '' }}) -join '.'
}} else {{ $null }}
$installerApplied = $installedVersion -ne '{TIGHTVNC_VERSION}.0'
if (-not $installerApplied) {{ throw 'Installer phase was requested even though the required TightVNC version is already present.' }}
if (-not (Test-Path -LiteralPath $installerPath -PathType Leaf)) {{ Invoke-WebRequest -UseBasicParsing -Uri $downloadUrl -OutFile $installerPath }}
$installerHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $installerPath).Hash.ToLowerInvariant()
if ($installerHash -ne $expectedInstallerHash) {{ throw "TightVNC installer SHA-256 differs from the pinned official release hash: $installerHash" }}
$ciphertext = [System.IO.File]::ReadAllBytes($secretPath)
$plain = [System.Security.Cryptography.ProtectedData]::Unprotect($ciphertext, $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser)
$credentials = [System.Text.Encoding]::UTF8.GetString($plain) | ConvertFrom-Json
$properties = @(
    'ADDLOCAL=Server', 'SERVER_REGISTER_AS_SERVICE=0', 'SERVER_ADD_FIREWALL_EXCEPTION=0', 'SERVER_ALLOW_SAS=0',
    'SET_ACCEPTHTTPCONNECTIONS=1', 'VALUE_OF_ACCEPTHTTPCONNECTIONS=0', 'SET_ACCEPTRFBCONNECTIONS=1', 'VALUE_OF_ACCEPTRFBCONNECTIONS=1',
    'SET_ALLOWLOOPBACK=1', 'VALUE_OF_ALLOWLOOPBACK=1', 'SET_LOOPBACKONLY=1', 'VALUE_OF_LOOPBACKONLY=1',
    'SET_BLOCKREMOTEINPUT=1', 'VALUE_OF_BLOCKREMOTEINPUT=0', 'SET_DISCONNECTACTION=1', 'VALUE_OF_DISCONNECTACTION=0',
    'SET_NEVERSHARED=1', 'VALUE_OF_NEVERSHARED=1', 'SET_RUNCONTROLINTERFACE=1', 'VALUE_OF_RUNCONTROLINTERFACE=0',
    'SET_USEVNCAUTHENTICATION=1', 'VALUE_OF_USEVNCAUTHENTICATION=1', 'SET_PASSWORD=1', "VALUE_OF_PASSWORD=$($credentials.viewer)",
    'SET_USECONTROLAUTHENTICATION=1', 'VALUE_OF_USECONTROLAUTHENTICATION=1', 'SET_CONTROLPASSWORD=1', "VALUE_OF_CONTROLPASSWORD=$($credentials.control)",
    'SET_ENABLEFILETRANSFERS=1', 'VALUE_OF_ENABLEFILETRANSFERS=0', 'SET_REMOVEWALLPAPER=1', 'VALUE_OF_REMOVEWALLPAPER=0',
    'SET_RFBPORT=1', 'VALUE_OF_RFBPORT=5900', 'SET_USED3D=1', 'VALUE_OF_USED3D=0', 'SET_USEMIRRORDRIVER=1', 'VALUE_OF_USEMIRRORDRIVER=0'
)
$process = Start-Process -FilePath 'msiexec.exe' -ArgumentList (@('/i', $installerPath, '/quiet', '/norestart') + $properties) -Wait -PassThru
if ($process.ExitCode -notin @(0, 3010)) {{ throw "TightVNC MSI installation failed with exit code $($process.ExitCode)." }}
if (-not (Test-Path -LiteralPath $serverPath -PathType Leaf)) {{ throw 'TightVNC Server executable was not installed.' }}
$actualHash = if (Test-Path -LiteralPath $installerPath -PathType Leaf) {{ (Get-FileHash -Algorithm SHA256 -LiteralPath $installerPath).Hash.ToLowerInvariant() }} else {{ $expectedInstallerHash }}
if ($actualHash -ne $expectedInstallerHash) {{ throw 'The cached TightVNC installer does not match the pinned checksum.' }}
[ordered]@{{ installation_required = $false; installer_applied = $installerApplied; installer_sha256 = $actualHash }} | ConvertTo-Json -Compress
"""


def _application_policy_script() -> str:
    return """
$ErrorActionPreference = 'Stop'
$machineRoot = 'HKLM:\\SOFTWARE\\TightVNC\\Server'
$applicationRoot = 'HKCU:\\SOFTWARE\\TightVNC\\Server'
if (-not (Test-Path -LiteralPath $machineRoot)) { throw 'TightVNC installer did not create its global configuration.' }
$machine = Get-ItemProperty -LiteralPath $machineRoot
if ($null -eq $machine.Password -or $null -eq $machine.ControlPassword) { throw 'TightVNC installer did not create opaque authentication material.' }
New-Item -Path $applicationRoot -Force | Out-Null
$applicationPolicy = [ordered]@{
  AcceptRfbConnections = 1; AcceptHttpConnections = 0; AllowLoopback = 1; LoopbackOnly = 1
  UseVncAuthentication = 1; UseControlAuthentication = 1; RunControlInterface = 0
  BlockRemoteInput = 0; DisconnectAction = 0; NeverShared = 1; EnableFileTransfers = 0
  RemoveWallpaper = 0; RfbPort = 5900; UseD3D = 0; UseMirrorDriver = 0
}
foreach ($entry in $applicationPolicy.GetEnumerator()) {
  New-ItemProperty -LiteralPath $applicationRoot -Name $entry.Key -PropertyType DWord -Value $entry.Value -Force | Out-Null
}
$application = Get-ItemProperty -LiteralPath $applicationRoot
[ordered]@{ d3d = $application.UseD3D; mirror = $application.UseMirrorDriver; loopback = $application.LoopbackOnly } | ConvertTo-Json -Compress
"""


def _service_policy_script() -> str:
    return """
$ErrorActionPreference = 'Stop'
$service = Get-Service -Name 'tvnserver' -ErrorAction SilentlyContinue
if ($null -ne $service) {
  if ($service.Status -ne 'Stopped') { Stop-Service -Name 'tvnserver' -Force }
  Set-Service -Name 'tvnserver' -StartupType Disabled
}
[ordered]@{ service_status = if ($null -eq $service) { 'absent' } else { (Get-Service -Name 'tvnserver').Status.ToString() } } | ConvertTo-Json -Compress
"""


def _desktop_quiesce_script() -> str:
    return """
$ErrorActionPreference = 'Stop'
Get-Process -Name 'tvnserver' -ErrorAction SilentlyContinue | Stop-Process -Force
$taskDeadline = [DateTime]::UtcNow.AddSeconds(10)
do {
  $task = Get-ScheduledTask -TaskName 'PaleMirror.VncConsole' -ErrorAction SilentlyContinue
  if ($null -eq $task -or $task.State -ne 'Running') { break }
  Start-Sleep -Milliseconds 250
} while ([DateTime]::UtcNow -lt $taskDeadline)
if ($null -ne $task -and $task.State -eq 'Running') { throw 'The previous interactive TightVNC task did not release after its process stopped.' }
[ordered]@{ quiesced = $true } | ConvertTo-Json -Compress
"""


def _desktop_registration_script() -> str:
    return """
$ErrorActionPreference = 'Stop'
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$serverPath = Join-Path $env:ProgramFiles 'TightVNC\\tvnserver.exe'
if (-not (Test-Path -LiteralPath $serverPath -PathType Leaf)) { throw 'TightVNC Server executable is missing.' }
$action = New-ScheduledTaskAction -Execute $serverPath -Argument '-run'
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $identity
$principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Highest
$taskSettings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -MultipleInstances IgnoreNew -StartWhenAvailable
Register-ScheduledTask -TaskName 'PaleMirror.VncConsole' -Action $action -Trigger $trigger -Principal $principal -Settings $taskSettings -Force | Out-Null
[ordered]@{ registered = $true } | ConvertTo-Json -Compress
"""


def _desktop_launch_script() -> str:
    return """
$ErrorActionPreference = 'Stop'
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
Start-ScheduledTask -TaskName 'PaleMirror.VncConsole'
$deadline = [DateTime]::UtcNow.AddSeconds(12)
do {
  Start-Sleep -Milliseconds 250
  $listeners = @(Get-NetTCPConnection -State Listen -LocalPort 5900 -ErrorAction SilentlyContinue)
} while ($listeners.Count -eq 0 -and [DateTime]::UtcNow -lt $deadline)
if ($listeners.Count -eq 0) { throw 'The interactive TightVNC task did not open its RFB listener on port 5900.' }
$addresses = @($listeners | Select-Object -ExpandProperty LocalAddress | Sort-Object -Unique)
if (@($addresses | Where-Object { $_ -notin @('127.0.0.1', '::1') }).Count -ne 0) { throw "TightVNC listener escaped loopback-only binding: $($addresses -join ', ')" }
$listenerProcess = Get-Process -Id $listeners[0].OwningProcess -IncludeUserName -ErrorAction Stop
if ($listenerProcess.SessionId -eq 0 -or $listenerProcess.UserName -ne $identity) { throw "TightVNC listener is not owned by the interactive author session (session $($listenerProcess.SessionId), user $($listenerProcess.UserName))." }
$task = Get-ScheduledTask -TaskName 'PaleMirror.VncConsole' -ErrorAction Stop
[ordered]@{ listener_addresses = $addresses; listener_session = $listenerProcess.SessionId; console_task_state = $task.State.ToString() } | ConvertTo-Json -Compress
"""


if __name__ == "__main__":
    main()
