$ErrorActionPreference = 'Stop'
$env:WINAPP_CLI_TELEMETRY_OPTOUT = '1'

# This is deliberately capture-only.  It neither focuses the Blender window nor
# sends it mouse, keyboard, or Blender API input.
$privateRoot = 'E:\PaleMirror\private'
$tool = Join-Path $privateRoot 'tools\winappcli-v0.6.0\winapp.exe'
$captureRoot = Join-Path $privateRoot 'captures'
$pngPath = Join-Path $captureRoot 'winapp-blender-screen-v02.png'
$receiptPath = Join-Path $captureRoot 'winapp-blender-screen-v02.json'

trap {
    $failure = [ordered]@{
        schema = 'pale_mirror.winapp_screen_capture.v1'
        session_id = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
        failure = $_.Exception.Message
        failed_utc = [DateTime]::UtcNow.ToString('o')
    }
    New-Item -ItemType Directory -Path $captureRoot -Force | Out-Null
    $failure | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $receiptPath -Encoding utf8
    exit 4
}

if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
    throw "Pinned WinApp CLI is missing: $tool"
}

New-Item -ItemType Directory -Path $captureRoot -Force | Out-Null
Remove-Item -LiteralPath $pngPath -Force -ErrorAction SilentlyContinue

$rawWindows = & $tool ui list-windows -a blender --json 2>&1 | Out-String
$listExitCode = $LASTEXITCODE
if ($listExitCode -ne 0) {
    throw "WinApp list-windows exited ${listExitCode}: $($rawWindows.Trim())"
}

$windows = @($rawWindows | ConvertFrom-Json)
if ($windows.Count -ne 1) {
    throw "Expected exactly one visible Blender window in Session $([System.Diagnostics.Process]::GetCurrentProcess().SessionId); found $($windows.Count)."
}

$captureResult = & $tool ui screenshot --window $windows[0].hwnd --output $pngPath --capture-screen --focus --json 2>&1 | Out-String
$captureExitCode = $LASTEXITCODE
$pngExists = Test-Path -LiteralPath $pngPath -PathType Leaf
$pngBytes = if ($pngExists) { (Get-Item -LiteralPath $pngPath).Length } else { 0 }
$pngHeader = if ($pngExists -and $pngBytes -ge 8) { [System.IO.File]::ReadAllBytes($pngPath)[0..7] } else { @() }
$isPng = ($pngHeader.Count -eq 8) -and (($pngHeader -join ',') -eq '137,80,78,71,13,10,26,10')

$receipt = [ordered]@{
    schema = 'pale_mirror.winapp_screen_capture.v1'
    session_id = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
    window_handle = ('0x{0:X}' -f [int64]$windows[0].hwnd)
    window_title = $windows[0].title
    list_exit_code = $listExitCode
    capture_exit_code = $captureExitCode
    capture_result = $captureResult.Trim()
    png_exists = $pngExists
    png_bytes = $pngBytes
    png_signature_valid = $isPng
    png_sha256 = if ($pngExists) { (Get-FileHash -Algorithm SHA256 -LiteralPath $pngPath).Hash.ToLowerInvariant() } else { $null }
    captured_utc = [DateTime]::UtcNow.ToString('o')
}
$receipt | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $receiptPath -Encoding utf8

if ($captureExitCode -ne 0 -or -not $pngExists -or $pngBytes -le 0 -or -not $isPng) {
    exit 3
}
