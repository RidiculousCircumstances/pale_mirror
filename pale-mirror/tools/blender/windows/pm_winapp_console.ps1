$ErrorActionPreference = 'Stop'
$env:WINAPP_CLI_TELEMETRY_OPTOUT = '1'

# The task receives no arguments.  It reads a fixed request path, dispatches
# only named operations below, and writes one fixed receipt path.  The standard
# mode has no generic shell, WinApp, coordinate, keystroke or selector
# operation.  The separately authorised Blender artist mode is still confined
# to the one visible protected Collector Master window and records every input
# plus Screen-DC evidence before and after it.
$privateRoot = 'E:\PaleMirror\private'
$tool = Join-Path $privateRoot 'tools\winappcli-v0.6.0\winapp.exe'
$captureRoot = Join-Path $privateRoot 'captures'
$requestPath = Join-Path $captureRoot 'pm-winapp-request.json'
$receiptPath = Join-Path $captureRoot 'pm-winapp-receipt.json'
$collectorProductionBaseline = 'E:\PaleMirror\workspace\pale-mirror\assets\harvester\biomass_collector_v05_direct_mesh_v02.blend'
$collectorProductionMaster = 'E:\PaleMirror\workspace\pale-mirror\assets\harvester\biomass_collector_v05_production_master_v01.blend'
$blenderProfileLauncher = 'E:\PaleMirror\bin\pm-blender.cmd'
$receipt = [ordered]@{
    schema = 'pale_mirror.winapp_console_receipt.v1'
    session_id = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
    operation = $null
    success = $false
    completed_utc = $null
}

function Invoke-PmWinApp([string[]]$Arguments) {
    $output = & $tool @Arguments 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0) {
        throw "WinApp $($Arguments -join ' ') exited ${LASTEXITCODE}: $($output.Trim())"
    }
    return $output.Trim()
}

function Get-PmVisibleWindows {
    $decoded = ConvertFrom-Json -InputObject (Invoke-PmWinApp @('ui', 'list-windows', '--json'))
    foreach ($entry in $decoded) {
        Write-Output $entry
    }
}

function Get-PmBlenderWindow {
    $windows = @(Get-PmVisibleWindows | Where-Object { $_.processName -eq 'blender' })
    if ($windows.Count -ne 1) {
        throw "Expected exactly one visible Blender window in Session $($receipt.session_id); found $($windows.Count)."
    }
    return $windows[0]
}

function Save-PmScreenCapture([string]$Name) {
    if ($Name -notmatch '\Awinapp-[a-z0-9][a-z0-9_-]{0,79}\.png\z') {
        throw 'Capture filename is outside the fixed WinApp evidence grammar.'
    }
    $window = Get-PmBlenderWindow
    $windowHandle = [string]($window.hwnd)
    $output = Join-Path $captureRoot $Name
    Remove-Item -LiteralPath $output -Force -ErrorAction SilentlyContinue
    $result = Invoke-PmWinApp @('ui', 'screenshot', '--window', $windowHandle, '--output', $output, '--capture-screen', '--focus', '--json')
    if (-not (Test-Path -LiteralPath $output -PathType Leaf) -or (Get-Item -LiteralPath $output).Length -le 0) {
        throw 'WinApp did not produce the required Screen-DC PNG.'
    }
    $receipt.capture_name = $Name
    $receipt.capture_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $output).Hash.ToLowerInvariant()
    $receipt.capture_result = $result
    $receipt.window_handle = ('0x{0:X}' -f [int64]$window.hwnd)
    $receipt.window_title = $window.title
}

function Assert-PmNoTightVncFirewallRule {
    $serverPath = Join-Path $env:ProgramFiles 'TightVNC\tvnserver.exe'
    $allowRules = @(
        Get-NetFirewallRule -PolicyStore ActiveStore -ErrorAction Stop |
        ForEach-Object {
            $rule = $_
            $applications = @($rule | Get-NetFirewallApplicationFilter -ErrorAction SilentlyContinue)
            if (@($applications | Where-Object { $_.Program -ieq $serverPath }).Count -gt 0 -and
                    $rule.Enabled.ToString() -eq 'True' -and $rule.Action.ToString() -eq 'Allow') {
                [ordered]@{ name = $rule.DisplayName; direction = $rule.Direction.ToString(); action = $rule.Action.ToString() }
            }
        }
    )
    if ($allowRules.Count -ne 0) {
        throw "TightVNC unexpectedly has enabled Windows Firewall allow rules: $($allowRules | ConvertTo-Json -Compress)"
    }
    $receipt.tightvnc_allow_rules = @()
}

function Dismiss-PmTightVncFirewallPrompt {
    # Windows PowerShell 5.1 can otherwise parse a UTF-8-without-BOM script in
    # the active ANSI code page. Keep the localized host strings as ASCII-only
    # Unicode code points so the verified worker is source-encoding invariant.
    $localizedAlert = [string]::Concat([char[]]@(0x041E, 0x043F, 0x043E, 0x0432, 0x0435, 0x0449, 0x0435, 0x043D, 0x0438, 0x0435, 0x0020, 0x0431, 0x0435, 0x0437, 0x043E, 0x043F, 0x0430, 0x0441, 0x043D, 0x043E, 0x0441, 0x0442, 0x0438, 0x0020, 0x0057, 0x0069, 0x006E, 0x0064, 0x006F, 0x0077, 0x0073))
    $localizedCancel = [string]::Concat([char[]]@(0x041E, 0x0442, 0x043C, 0x0435, 0x043D, 0x0430))
    $prompts = @()
    foreach ($candidate in @(Get-PmVisibleWindows)) {
            $candidateHandle = [string]($candidate.hwnd)
            $inspection = Invoke-PmWinApp @('ui', 'inspect', '--window', $candidateHandle, '--interactive', '--json')
            if ($inspection -match 'TightVNC Server') {
                $prompts += [ordered]@{ window = $candidate; inspection = $inspection }
            }
    }
    $securityPrompts = @($prompts | Where-Object { $_.window.title -in @('Windows Security Alert', $localizedAlert) })
    if ($securityPrompts.Count -eq 0) {
        Assert-PmNoTightVncFirewallRule
        $receipt.dismissed_button = 'already_absent'
        Save-PmScreenCapture 'winapp-tightvnc-firewall-dismissed.png'
        return
    }
    if ($securityPrompts.Count -ne 1) {
        $titles = @($prompts | ForEach-Object { [string]$_.window.title }) -join ' | '
        throw "Expected exactly one recognized TightVNC Windows Security Alert; found $($securityPrompts.Count) among: $titles"
    }
    $prompt = $securityPrompts[0].window
    $promptHandle = [string]($prompt.hwnd)
    $cancelResult = Invoke-PmWinApp @('ui', 'click', $localizedCancel, '--window', $promptHandle, '--json')
    $remaining = @()
    foreach ($candidate in @(Get-PmVisibleWindows)) {
            $candidateHandle = [string]($candidate.hwnd)
            $inspection = Invoke-PmWinApp @('ui', 'inspect', '--window', $candidateHandle, '--interactive', '--json')
            if ($inspection -match 'TightVNC Server' -and $candidate.title -in @('Windows Security Alert', $localizedAlert)) {
                $remaining += $candidate
            }
    }
    if ($remaining.Count -ne 0) {
        throw 'A visible TightVNC window remained after the fixed Cancel action.'
    }
    Assert-PmNoTightVncFirewallRule
    $receipt.dismissed_window_handle = ('0x{0:X}' -f [int64]$prompt.hwnd)
    $receipt.dismissed_window_title = $prompt.title
    $receipt.dismissed_button = 'localized_cancel'
    $receipt.dismiss_result = $cancelResult
    Save-PmScreenCapture 'winapp-tightvnc-firewall-dismissed.png'
}

function Open-PmCollectorProductionBaseline {
    # This is intentionally not a generic file-open primitive. It may replace
    # only Blender's known factory-default scene with the exact reviewed v02
    # continuation, which is the parent pinned by the production-master
    # protocol. It refuses to discard a named or modified user scene.
    if (-not (Test-Path -LiteralPath $collectorProductionBaseline -PathType Leaf)) {
        throw 'The fixed reviewed Collector v02 source is absent from the private workspace.'
    }
    $initial = Get-PmBlenderWindow
    if ($initial.title -ne '(Unsaved) - Blender 5.1.2') {
        throw "Refusing to replace non-factory Blender scene: $($initial.title)"
    }
    $initialProcess = Get-Process -Id ([int]$initial.processId) -ErrorAction Stop
    if (-not (Test-Path -LiteralPath $blenderProfileLauncher -PathType Leaf)) {
        throw 'The fixed Pale Mirror Blender profile launcher is absent.'
    }
    Start-Process -FilePath 'cmd.exe' -ArgumentList ('/d /c ""{0}" "{1}""' -f $blenderProfileLauncher, $collectorProductionBaseline) | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    $opened = $null
    do {
        Start-Sleep -Milliseconds 250
        $opened = @(Get-PmVisibleWindows | Where-Object {
            $_.processName -eq 'blender' -and $_.title -match 'biomass_collector_v05_direct_mesh_v02'
        })
        if ($opened.Count -eq 1) { break }
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($opened.Count -ne 1) {
        throw "Timed out waiting for exactly one fixed Collector v02 Blender window; observed $($opened.Count)."
    }
    Stop-Process -Id $initialProcess.Id -ErrorAction Stop
    Start-Sleep -Milliseconds 500
    $active = Get-PmBlenderWindow
    if ($active.hwnd -ne $opened[0].hwnd -or $active.title -notmatch 'biomass_collector_v05_direct_mesh_v02') {
        throw 'The fixed Collector source did not become the sole visible Blender window.'
    }
    $receipt.collector_source = 'biomass_collector_v05_direct_mesh_v02.blend'
    $receipt.collector_source_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $collectorProductionBaseline).Hash.ToLowerInvariant()
    $receipt.previous_window_title = $initial.title
    $receipt.blender_process_id = [int]$active.processId
    Save-PmScreenCapture 'winapp-collector-production-baseline.png'
}

function Open-PmCollectorProductionMaster {
    # The recovered authoring scene is an exact on-disk Master, never an
    # unsaved editor session.  Opening it is permitted only when Blender is
    # fully absent after a crash; this prevents accidental replacement of a
    # live artist session.
    if (-not (Test-Path -LiteralPath $collectorProductionMaster -PathType Leaf) -or -not (Test-Path -LiteralPath $blenderProfileLauncher -PathType Leaf)) {
        throw 'The fixed Collector Master or its profile launcher is absent.'
    }
    $existing = @(Get-PmVisibleWindows | Where-Object { $_.processName -eq 'blender' })
    if ($existing.Count -ne 0) {
        throw "Refusing to open the protected Master while $($existing.Count) Blender window(s) are still visible."
    }
    Start-Process -FilePath 'cmd.exe' -ArgumentList ('/d /c ""{0}" "{1}""' -f $blenderProfileLauncher, $collectorProductionMaster) | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(25)
    $opened = $null
    do {
        Start-Sleep -Milliseconds 250
        $opened = @(Get-PmVisibleWindows | Where-Object {
            $_.processName -eq 'blender' -and $_.title -match '^biomass_collector_v05_production_master_v01 \[E:\\PaleMirror\\workspace\\pale-mirror\\assets\\harvester\\biomass_collector_v05_production_master_v01\.blend\] - Blender 5\.1\.2$'
        })
        if ($opened.Count -eq 1) { break }
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($opened.Count -ne 1) {
        throw "Timed out waiting for one profile-launched Collector Master window; observed $($opened.Count)."
    }
    $receipt.collector_source = 'biomass_collector_v05_production_master_v01.blend'
    $receipt.collector_source_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $collectorProductionMaster).Hash.ToLowerInvariant()
    $receipt.blender_process_id = [int]$opened[0].processId
    $receipt.profile_launcher = 'pm-blender.cmd'
    Save-PmScreenCapture 'winapp-collector-production-master-recovered.png'
}

function Restart-PmCollectorProductionBaseline {
    # Recovery for the one prior raw-executable launch: a clean fixed v02 scene
    # has no unsaved authoring work, so it can be replaced by the same file via
    # the profile launcher that supplies the private MCP extension.
    if (-not (Test-Path -LiteralPath $collectorProductionBaseline -PathType Leaf) -or -not (Test-Path -LiteralPath $blenderProfileLauncher -PathType Leaf)) {
        throw 'The fixed Collector baseline or its profile launcher is absent.'
    }
    $initial = Get-PmBlenderWindow
    if ($initial.title -notmatch 'biomass_collector_v05_direct_mesh_v02' -or $initial.title -match '\*') {
        throw "Refusing to restart a non-clean fixed Collector v02 scene: $($initial.title)"
    }
    $initialProcess = Get-Process -Id ([int]$initial.processId) -ErrorAction Stop
    Start-Process -FilePath 'cmd.exe' -ArgumentList ('/d /c ""{0}" "{1}""' -f $blenderProfileLauncher, $collectorProductionBaseline) | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    $opened = $null
    do {
        Start-Sleep -Milliseconds 250
        $opened = @(Get-PmVisibleWindows | Where-Object {
            $_.processName -eq 'blender' -and $_.title -match 'biomass_collector_v05_direct_mesh_v02' -and $_.hwnd -ne $initial.hwnd
        })
        if ($opened.Count -eq 1) { break }
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($opened.Count -ne 1) {
        throw "Timed out waiting for one profile-launched fixed Collector v02 window; observed $($opened.Count)."
    }
    Stop-Process -Id $initialProcess.Id -ErrorAction Stop
    Start-Sleep -Milliseconds 500
    $active = Get-PmBlenderWindow
    if ($active.hwnd -ne $opened[0].hwnd -or $active.title -notmatch 'biomass_collector_v05_direct_mesh_v02') {
        throw 'The profile-launched Collector source did not become the sole visible Blender window.'
    }
    $receipt.collector_source = 'biomass_collector_v05_direct_mesh_v02.blend'
    $receipt.collector_source_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $collectorProductionBaseline).Hash.ToLowerInvariant()
    $receipt.previous_window_title = $initial.title
    $receipt.blender_process_id = [int]$active.processId
    $receipt.profile_launcher = 'pm-blender.cmd'
    Save-PmScreenCapture 'winapp-collector-production-profile-baseline.png'
}

function Confirm-PmBlenderFirstRunPreferences {
    # The isolated Blender profile may show this exact first-run card after an
    # application upgrade. Confirming it changes only local editor preferences;
    # it neither loads nor saves a PM asset and refuses all other dialogs.
    $window = Get-PmBlenderWindow
    $windowHandle = [string]($window.hwnd)
    if ($window.title -notmatch 'biomass_collector_v05_direct_mesh_v02' -or $window.title -match '\*') {
        throw "Refusing first-run confirmation outside the fixed clean Collector v02 source: $($window.title)"
    }
    # Blender's startup card is not exposed as UI Automation controls. The
    # prior Screen-DC evidence is reviewed before this operation is enabled;
    # Enter is its fixed default Save New Preferences action. No text, menu
    # name, arbitrary key or global shortcut enters this channel.
    $click = Invoke-PmWinApp @('ui', 'send-keys', 'enter', '--window', $windowHandle, '--via', 'send-input', '--json')
    Start-Sleep -Milliseconds 500
    $receipt.confirmed_dialog = 'blender_5_1_2_first_run_preferences'
    $receipt.confirm_result = $click
    Save-PmScreenCapture 'winapp-collector-production-preferences-confirmed.png'
}

function Select-PmCollectorMasterGrab {
    # This is the first deliberately narrow artist-input action.  It never
    # accepts a caller-selected key or coordinate: with the verified Master
    # title and Sculpting workspace already prepared, Blender's fixed Sculpt
    # hotkey G selects Grab.  The action is captured before any pen stroke.
    $window = Get-PmBlenderWindow
    if ($window.title -notmatch '^biomass_collector_v05_production_master_v01 \[E:\\PaleMirror\\workspace\\pale-mirror\\assets\\harvester\\biomass_collector_v05_production_master_v01\.blend\] - Blender 5\.1\.2$') {
        throw "Refusing Grab selection outside the clean protected Collector Master: $($window.title)"
    }
    $windowHandle = [string]($window.hwnd)
    $result = Invoke-PmWinApp @('ui', 'send-keys', 'g', '--window', $windowHandle, '--via', 'send-input', '--json')
    Start-Sleep -Milliseconds 300
    $receipt.artist_tool = 'Grab'
    $receipt.artist_tool_result = $result
    Save-PmScreenCapture 'winapp-collector-master-grab-selected.png'
}

function Invoke-PmCollectorMasterLeadingMantleGrounding {
    # One reviewed first artist gesture, not a generic remote-pen channel. The
    # points are fixed in the full-screen, locked primary camera after the
    # preparation receipt: the inner leading drape is moved down-left toward
    # the reference ground line. A marker makes the exact stroke non-replayable.
    $window = Get-PmBlenderWindow
    if ($window.title -notmatch '^biomass_collector_v05_production_master_v01 \[E:\\PaleMirror\\workspace\\pale-mirror\\assets\\harvester\\biomass_collector_v05_production_master_v01\.blend\] - Blender 5\.1\.2$') {
        throw "Refusing the first mantle gesture outside the clean protected Collector Master: $($window.title)"
    }
    $marker = Join-Path $captureRoot 'collector-master-leading-mantle-grounding-v01.json'
    if (Test-Path -LiteralPath $marker -PathType Leaf) {
        throw 'The one-time Collector Master leading-mantle gesture already has a receipt.'
    }
    $windowHandle = [string]($window.hwnd)
    $path = '680,650 664,690 642,734 618,776'
    $result = Invoke-PmWinApp @('ui', 'pen', '--path', $path, '--pressure', '0.42', '--duration-ms', '420', '--window', $windowHandle, '--json')
    Start-Sleep -Milliseconds 450
    [ordered]@{
        schema = 'pale_mirror.collector_master_artist_stroke.v1'
        stroke_id = 'leading_mantle_grounding_v01'
        brush = 'Grab'
        path = $path
        pressure = 0.42
        window_title = $window.title
        result = $result
    } | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $marker -Encoding utf8
    $receipt.artist_stroke_id = 'leading_mantle_grounding_v01'
    $receipt.artist_brush = 'Grab'
    $receipt.artist_stroke_result = $result
    Save-PmScreenCapture 'winapp-collector-master-leading-mantle-grounding-v01.png'
}

function Get-PmCollectorMasterArtistWindow {
    $window = Get-PmBlenderWindow
    $title = [string]$window.title
    if ($title -notmatch '^\*? ?biomass_collector_v05_production_master_v01 \[E:\\PaleMirror\\workspace\\pale-mirror\\assets\\harvester\\biomass_collector_v05_production_master_v01\.blend\] - Blender 5\.1\.2$') {
        throw "Refusing interactive artist input outside the protected Collector Master: $title"
    }
    return $window
}

function Save-PmCollectorMasterArtistCapture([string]$Name) {
    if ($Name -notmatch '\Awinapp-artist-[a-z0-9][a-z0-9_-]{0,63}-(before|after)\.png\z') {
        throw 'Artist capture filename is outside the fixed evidence grammar.'
    }
    $window = Get-PmCollectorMasterArtistWindow
    $output = Join-Path $captureRoot $Name
    Remove-Item -LiteralPath $output -Force -ErrorAction SilentlyContinue
    $result = Invoke-PmWinApp @('ui', 'screenshot', '--window', ([string]$window.hwnd), '--output', $output, '--capture-screen', '--focus', '--json')
    if (-not (Test-Path -LiteralPath $output -PathType Leaf) -or (Get-Item -LiteralPath $output).Length -le 0) {
        throw 'WinApp did not produce required artist evidence.'
    }
    return [ordered]@{
        name = $Name
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $output).Hash.ToLowerInvariant()
        result = $result
    }
}

function Assert-PmArtistPoint([string]$Point) {
    if ($Point -notmatch '\A([0-9]{1,4}),([0-9]{1,4})\z') {
        throw "Artist point is malformed: $Point"
    }
    $x = [int]$Matches[1]
    $y = [int]$Matches[2]
    if ($x -lt 0 -or $x -gt 2581 -or $y -lt 0 -or $y -gt 1401) {
        throw "Artist point is outside the active 2582x1402 Blender screen: $Point"
    }
}

function Initialize-PmNativeInput {
    if ("PaleMirrorNativeInput" -as [type]) {
        return
    }
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class PaleMirrorNativeInput {
    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", SetLastError = true)]
    public static extern bool SetCursorPos(int x, int y);

    [DllImport("user32.dll")]
    public static extern uint GetDpiForWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
}
'@
}

function Focus-PmCollectorMasterArtistWindow($Window) {
    Initialize-PmNativeInput
    $handle = [IntPtr][int64]$Window.hwnd
    if (-not [PaleMirrorNativeInput]::SetForegroundWindow($handle)) {
        throw 'Windows rejected focus for the protected Collector Master window.'
    }
    Start-Sleep -Milliseconds 120
    if ([PaleMirrorNativeInput]::GetForegroundWindow().ToInt64() -ne $handle.ToInt64()) {
        throw 'Collector Master did not become the foreground window; no artist input was sent.'
    }
}

function Invoke-PmCollectorMasterArtistClick($Window, [string]$Point) {
    Assert-PmArtistPoint $Point
    Focus-PmCollectorMasterArtistWindow $Window
    $parts = $Point -split ','
    # WinApp Screen-DC returns the protected application's logical pixels,
    # while the Session-1 native pointer call is DPI-virtualized. Convert the
    # audited capture coordinate once through the Master window DPI so an
    # artist can use the screenshot's own coordinate system accurately.
    $dpi = [PaleMirrorNativeInput]::GetDpiForWindow([IntPtr][int64]$Window.hwnd)
    if ($dpi -lt 96 -or $dpi -gt 384) {
        throw "Collector Master reported an unsafe DPI value: $dpi"
    }
    $scale = [double]$dpi / 96.0
    $x = [int][Math]::Round(([double]$parts[0]) / $scale)
    $y = [int][Math]::Round(([double]$parts[1]) / $scale)
    if (-not [PaleMirrorNativeInput]::SetCursorPos($x, $y)) {
        throw 'Windows rejected the Collector Master pointer position.'
    }
    Start-Sleep -Milliseconds 80
    # One normal click is allowed only after the exact protected Master is
    # foregrounded. The structured protocol has no arbitrary-window input.
    [PaleMirrorNativeInput]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 40
    [PaleMirrorNativeInput]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
    return [ordered]@{ capture_point = $Point; native_point = "$x,$y"; dpi = $dpi; foreground_window = ('0x{0:X}' -f [int64]$Window.hwnd); input = 'left_click' }
}

function Invoke-PmCollectorMasterArtistInput($Action) {
    # The user explicitly authorised normal artist interaction.  This remains
    # deliberately Blender-only: no process launch, shell, selector, file path
    # or system hotkey can enter through this request.  Every action has a
    # caller-chosen durable id, before/after Screen-DC evidence and a receipt.
    $id = [string]$Action.id
    if ($id -notmatch '\A[a-z][a-z0-9_-]{0,47}\z') {
        throw 'Artist interaction id is outside the evidence grammar.'
    }
    $kind = [string]$Action.kind
    if ($kind -notin @('key', 'pen', 'drag', 'click')) {
        throw 'Artist interaction kind is not allowlisted.'
    }
    $marker = Join-Path $captureRoot ("artist-$id.json")
    if (Test-Path -LiteralPath $marker -PathType Leaf) {
        throw "Artist interaction id already has a receipt: $id"
    }
    $window = Get-PmCollectorMasterArtistWindow
    $before = Save-PmCollectorMasterArtistCapture ("winapp-artist-$id-before.png")
    $handle = [string]$window.hwnd
    if ($kind -eq 'key') {
        Focus-PmCollectorMasterArtistWindow $window
        $keys = [string]$Action.keys
        if ($keys -notmatch '\A(?:[a-zA-Z0-9+_ -]+|text=[0-9.]{1,12})\z' -or $keys -match '(?i)win\+|alt\+f4|ctrl\+alt\+del|ctrl\+esc') {
            throw 'Artist key request is malformed or a system key.'
        }
        $result = Invoke-PmWinApp @('ui', 'send-keys', $keys, '--window', $handle, '--via', 'send-input', '--json')
    } elseif ($kind -eq 'pen') {
        Focus-PmCollectorMasterArtistWindow $window
        $path = [string]$Action.path
        $points = @($path -split ' ' | Where-Object { $_.Length -gt 0 })
        if ($points.Count -lt 1 -or $points.Count -gt 96) {
            throw 'Artist pen request has an unsafe number of points.'
        }
        foreach ($point in $points) { Assert-PmArtistPoint $point }
        $pressure = [double]$Action.pressure
        if ($pressure -lt 0.05 -or $pressure -gt 1.0) { throw 'Artist pen pressure is outside [0.05,1.0].' }
        $duration = [int]$Action.duration_ms
        if ($duration -lt 20 -or $duration -gt 8000) { throw 'Artist pen duration is outside [20,8000] ms.' }
        $result = Invoke-PmWinApp @('ui', 'pen', '--path', $path, '--pressure', $pressure.ToString([Globalization.CultureInfo]::InvariantCulture), '--duration-ms', $duration, '--window', $handle, '--json')
    } elseif ($kind -eq 'click') {
        $result = Invoke-PmCollectorMasterArtistClick $window ([string]$Action.point)
    } else {
        Focus-PmCollectorMasterArtistWindow $window
        $from = [string]$Action.from
        $to = [string]$Action.to
        Assert-PmArtistPoint $from
        Assert-PmArtistPoint $to
        $result = Invoke-PmWinApp @('ui', 'drag', $from, $to, '--window', $handle, '--json')
    }
    Start-Sleep -Milliseconds 350
    $after = Save-PmCollectorMasterArtistCapture ("winapp-artist-$id-after.png")
    $record = [ordered]@{
        schema = 'pale_mirror.collector_master_artist_interaction.v1'
        interaction_id = $id
        action = $Action
        window_handle = ('0x{0:X}' -f [int64]$window.hwnd)
        window_title_before = $window.title
        before = $before
        after = $after
        result = $result
        completed_utc = [DateTime]::UtcNow.ToString('o')
    }
    $record | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $marker -Encoding utf8
    $receipt.artist_interaction = $record
    $receipt.capture_name = $after.name
    $receipt.capture_sha256 = $after.sha256
    $receipt.window_handle = ('0x{0:X}' -f [int64]$window.hwnd)
    $receipt.window_title = $window.title
}

function Inspect-PmCollectorMasterArtistUi {
    $window = Get-PmCollectorMasterArtistWindow
    $receipt.artist_ui = Invoke-PmWinApp @('ui', 'inspect', '--window', ([string]$window.hwnd), '--interactive', '--json')
    $receipt.window_handle = ('0x{0:X}' -f [int64]$window.hwnd)
    $receipt.window_title = $window.title
    Save-PmScreenCapture 'winapp-artist-ui-inspect.png'
}

function Inspect-PmCollectorMasterArtistWindows {
    # Read-only recovery probe for transient Blender popovers/dialogs.  It
    # never dispatches input; it preserves the complete visible-window list
    # and captures the primary Master only when it remains uniquely known.
    $windows = @(Get-PmVisibleWindows)
    $receipt.artist_windows = @($windows | ForEach-Object {
        [ordered]@{
            hwnd = [int64]$_.hwnd
            process_name = [string]$_.processName
            title = [string]$_.title
            x = $_.x
            y = $_.y
            width = $_.width
            height = $_.height
        }
    })
    $master = @($windows | Where-Object {
        [string]$_.title -match '^\*? ?biomass_collector_v05_production_master_v01 \[E:\\PaleMirror\\workspace\\pale-mirror\\assets\\harvester\\biomass_collector_v05_production_master_v01\.blend\] - Blender 5\.1\.2$'
    })
    if ($master.Count -ne 1) {
        throw "Expected exactly one protected Collector Master among visible windows; found $($master.Count)."
    }
    $receipt.artist_auxiliary_blender_ui = @(
        $windows |
        Where-Object { $_.processName -eq 'blender' -and [int64]$_.hwnd -ne [int64]$master[0].hwnd } |
        ForEach-Object {
            [ordered]@{
                hwnd = [int64]$_.hwnd
                title = [string]$_.title
                inspection = Invoke-PmWinApp @('ui', 'inspect', '--window', ([string]$_.hwnd), '--interactive', '--json')
            }
        }
    )
    $output = Join-Path $captureRoot 'winapp-artist-window-inspect.png'
    Remove-Item -LiteralPath $output -Force -ErrorAction SilentlyContinue
    $result = Invoke-PmWinApp @('ui', 'screenshot', '--window', ([string]$master[0].hwnd), '--output', $output, '--capture-screen', '--json')
    if (-not (Test-Path -LiteralPath $output -PathType Leaf) -or (Get-Item -LiteralPath $output).Length -le 0) {
        throw 'WinApp did not capture the visible Collector Master window state.'
    }
    $receipt.capture_name = 'winapp-artist-window-inspect.png'
    $receipt.capture_sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $output).Hash.ToLowerInvariant()
    $receipt.capture_result = $result
    $receipt.window_handle = ('0x{0:X}' -f [int64]$master[0].hwnd)
    $receipt.window_title = $master[0].title
}

function Dismiss-PmCollectorMasterStaleCrashDialog {
    # Blender can retain a modal report from a prior unclean shutdown while
    # the current Master process remains responsive.  This deliberately
    # recognizes exactly that dialog and closes only the dialog's own Close
    # button; it never restarts Blender or targets the Master window.
    $windows = @(Get-PmVisibleWindows)
    $dialog = @($windows | Where-Object { $_.processName -eq 'blender' -and $_.title -eq 'Blender' })
    if ($dialog.Count -ne 1) {
        throw "Expected exactly one stale Blender report dialog; found $($dialog.Count)."
    }
    $inspection = Invoke-PmWinApp @('ui', 'inspect', '--window', ([string]$dialog[0].hwnd), '--interactive', '--json')
    if ($inspection -notmatch 'View Crash Log' -or $inspection -notmatch 'Restart' -or $inspection -notmatch 'CommandButton_8') {
        throw 'The auxiliary Blender window is not the recognized stale crash-report dialog.'
    }
    $closeResult = Invoke-PmWinApp @('ui', 'click', 'CommandButton_8', '--window', ([string]$dialog[0].hwnd), '--json')
    Start-Sleep -Milliseconds 500
    $remaining = @((Get-PmVisibleWindows) | Where-Object { $_.processName -eq 'blender' -and $_.title -eq 'Blender' })
    if ($remaining.Count -ne 0) {
        throw 'The recognized stale Blender crash-report dialog remained after its fixed Close action.'
    }
    $receipt.dismissed_stale_crash_dialog = [ordered]@{
        hwnd = ('0x{0:X}' -f [int64]$dialog[0].hwnd)
        result = $closeResult
    }
    Save-PmCollectorMasterArtistCapture 'winapp-artist-stale-crash-dialog-dismissed-after.png'
}

try {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
        throw "Pinned WinApp CLI is missing: $tool"
    }
    if (-not (Test-Path -LiteralPath $requestPath -PathType Leaf)) {
        throw "WinApp console request is absent: $requestPath"
    }
    $request = Get-Content -LiteralPath $requestPath -Raw | ConvertFrom-Json
    if ($request.schema -ne 'pale_mirror.winapp_console_request.v1') {
        throw 'WinApp console request schema is invalid.'
    }
    $receipt.operation = [string]$request.operation
    switch ($receipt.operation) {
        'capture_blender' {
            $label = [string]$request.label
            if ($label -notmatch '\A[a-z0-9][a-z0-9_-]{0,79}\z') {
                throw 'Capture label is outside the fixed evidence grammar.'
            }
            Save-PmScreenCapture "winapp-$label.png"
        }
        'dismiss_tightvnc_firewall' {
            Dismiss-PmTightVncFirewallPrompt
        }
        'open_collector_production_baseline' {
            Open-PmCollectorProductionBaseline
        }
        'open_collector_production_master' {
            Open-PmCollectorProductionMaster
        }
        'restart_collector_production_baseline' {
            Restart-PmCollectorProductionBaseline
        }
        'confirm_blender_first_run_preferences' {
            Confirm-PmBlenderFirstRunPreferences
        }
        'select_collector_master_grab' {
            Select-PmCollectorMasterGrab
        }
        'sculpt_collector_master_leading_mantle_grounding_v01' {
            Invoke-PmCollectorMasterLeadingMantleGrounding
        }
        'interactive_collector_master' {
            Invoke-PmCollectorMasterArtistInput $request.action
        }
        'inspect_collector_master_ui' {
            Inspect-PmCollectorMasterArtistUi
        }
        'inspect_collector_master_windows' {
            Inspect-PmCollectorMasterArtistWindows
        }
        'dismiss_collector_master_stale_crash_dialog' {
            Dismiss-PmCollectorMasterStaleCrashDialog
        }
        default {
            throw "WinApp console operation is not allowlisted: $($receipt.operation)"
        }
    }
    $receipt.success = $true
}
catch {
    $receipt.error = $_.Exception.Message
}
finally {
    $receipt.completed_utc = [DateTime]::UtcNow.ToString('o')
    New-Item -ItemType Directory -Path $captureRoot -Force | Out-Null
    $receipt | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $receiptPath -Encoding utf8
}

if (-not $receipt.success) {
    exit 1
}
