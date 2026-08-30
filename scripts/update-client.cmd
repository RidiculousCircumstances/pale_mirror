@echo off
setlocal
rem The artifact host is reachable through its stable mDNS name, not a DHCP IP.
set "FF_SOURCE=http://rd-EliteMini-Series.local:8092"
set "FF_TARGET=%~dp0"

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference='Stop';" ^
  "$response=Invoke-WebRequest -UseBasicParsing -Uri ($env:FF_SOURCE+'/scripts/install-client.ps1');" ^
  "$source=if ($response.Content -is [byte[]]) {[Text.Encoding]::UTF8.GetString($response.Content)} else {[string]$response.Content};" ^
  "$installer=[ScriptBlock]::Create($source);" ^
  "& $installer -Target $env:FF_TARGET -SourceBaseUrl $env:FF_SOURCE;" ^
  "if ($LASTEXITCODE) { exit $LASTEXITCODE }"

if errorlevel 1 (
  echo.
  echo Far Frontier update failed. See the error above.
  pause
  exit /b 1
)

echo.
echo Far Frontier client is up to date.
pause
