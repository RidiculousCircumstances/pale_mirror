[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Target,

    [string]$PackUrl,

    [string]$SourceBaseUrl = $env:FAR_FRONTIER_SOURCE_URL,

    [string]$Java = "java",

    [string]$PaleMirrorUrl = $env:PALE_MIRROR_URL,

    [string]$PaleMirrorSha512 = $env:PALE_MIRROR_SHA512,

    [string]$PaleMirrorVisualsUrl = $env:PALE_MIRROR_VISUALS_URL,

    [string]$PaleMirrorVisualsSha512 = $env:PALE_MIRROR_VISUALS_SHA512,

    [string]$RailwayUntoldUrl = $env:RAILWAY_UNTOLD_URL,

    [string]$RailwayUntoldSha512 = $env:RAILWAY_UNTOLD_SHA512
)

$ErrorActionPreference = "Stop"

$BootstrapVersion = "0.0.3"
$BootstrapSha256 = "a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c"
$BootstrapUrl = "https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v$BootstrapVersion/packwiz-installer-bootstrap.jar"


function Assert-Java21 {
    # java -version writes its version information to stderr.
    # Windows PowerShell 5.1 can turn that into NativeCommandError when
    # $ErrorActionPreference is "Stop", so temporarily allow stderr output.
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"

    try {
        $version = (& $Java -version 2>&1 | Out-String)
        $exitCode = $LASTEXITCODE
    }
    catch {
        throw "Unable to execute Java: $Java`n$($_.Exception.Message)"
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($exitCode -ne 0) {
        throw "Java failed with exit code $exitCode. Java executable: $Java"
    }

    if ($version -notmatch '(?m)(?:java|openjdk) version "21(?:[."]|$)') {
        throw "Java 21 is required. Java executable: $Java`nDetected version:`n$version"
    }

    Write-Host "Java 21 detected:"
    Write-Host ($version.Trim())
}


function Get-Sha256 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}


function Get-Sha512 {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path
    )

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA512).Hash.ToLowerInvariant()
}


function Install-HostedPaleMirror {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetDirectory,

        [string]$Url,

        [string]$ExpectedSha512
    )

    if ([string]::IsNullOrWhiteSpace($Url) -and [string]::IsNullOrWhiteSpace($ExpectedSha512)) {
        return
    }
    if ([string]::IsNullOrWhiteSpace($Url) -or [string]::IsNullOrWhiteSpace($ExpectedSha512)) {
        throw "-PaleMirrorUrl and -PaleMirrorSha512 must be supplied together"
    }
    if ($Url -notmatch '^https?://') {
        throw "Pale Mirror artifact URL must use HTTP or HTTPS"
    }

    $ExpectedSha512 = $ExpectedSha512.ToLowerInvariant()
    if ($ExpectedSha512 -notmatch '^[0-9a-f]{128}$') {
        throw "Pale Mirror SHA-512 must contain exactly 128 hexadecimal characters"
    }

    $modsDirectory = Join-Path $TargetDirectory "mods"
    $cacheDirectory = Join-Path $TargetDirectory ".far-frontier-installer-cache"
    $managedJar = Join-Path $modsDirectory "pale_mirror-hosted.jar"
    New-Item -ItemType Directory -Force -Path $modsDirectory, $cacheDirectory | Out-Null

    $candidates = @(
        Get-ChildItem -LiteralPath $modsDirectory -File -ErrorAction SilentlyContinue |
            Where-Object {
                ($_.Name -like 'pale_mirror*.jar' -or $_.Name -like 'pale-mirror*.jar') -and
                $_.Name -notlike 'pale_mirror_visuals*.jar' -and
                $_.Name -notlike 'pale-mirror-visuals*.jar'
            }
    )
    if ($candidates.Count -gt 1) {
        $paths = ($candidates | ForEach-Object { "  $($_.FullName)" }) -join "`n"
        throw "Multiple Pale Mirror JARs are present; refusing to choose between them:`n$paths"
    }
    if ($candidates.Count -eq 1) {
        $existing = $candidates[0].FullName
        if ((Get-Sha512 -Path $existing) -eq $ExpectedSha512) {
            Write-Host "Pale Mirror already installed and SHA-512 verified: $existing"
            return
        }
        if ($existing -ne $managedJar) {
            throw @"
An unmanaged Pale Mirror JAR conflicts with the requested hosted artifact:
  $existing
Remove it explicitly or use its SHA-512; it will not be overwritten automatically.
"@
        }
    }

    $cacheJar = Join-Path $cacheDirectory "pale-mirror-$($ExpectedSha512.Substring(0, 16)).jar"
    $cacheValid = (Test-Path -LiteralPath $cacheJar) -and
        ((Get-Sha512 -Path $cacheJar) -eq $ExpectedSha512)
    if (-not $cacheValid) {
        $downloadTemp = Join-Path $cacheDirectory ".pale-mirror-download.$([guid]::NewGuid()).jar"
        try {
            Write-Host "Downloading checksum-pinned Pale Mirror artifact..."
            Invoke-WebRequest -Uri $Url -OutFile $downloadTemp
            $actual = Get-Sha512 -Path $downloadTemp
            if ($actual -ne $ExpectedSha512) {
                throw "Pale Mirror SHA-512 verification failed. Expected: $ExpectedSha512 Actual: $actual"
            }
            Move-Item -LiteralPath $downloadTemp -Destination $cacheJar -Force
        }
        finally {
            Remove-Item -LiteralPath $downloadTemp -Force -ErrorAction SilentlyContinue
        }
    }

    $installTemp = Join-Path $modsDirectory ".pale-mirror-install.$([guid]::NewGuid()).jar"
    try {
        Copy-Item -LiteralPath $cacheJar -Destination $installTemp
        Move-Item -LiteralPath $installTemp -Destination $managedJar -Force
    }
    finally {
        Remove-Item -LiteralPath $installTemp -Force -ErrorAction SilentlyContinue
    }
    $installedHash = Get-Sha512 -Path $managedJar
    if ($installedHash -ne $ExpectedSha512) {
        throw "Installed Pale Mirror JAR failed its final SHA-512 verification"
    }
    Write-Host "Pale Mirror installed and SHA-512 verified: $managedJar"
}

function Install-HostedPaleMirrorVisuals {
    param(
        [Parameter(Mandatory = $true)][string]$TargetDirectory,
        [string]$Url,
        [string]$ExpectedSha512
    )

    if ([string]::IsNullOrWhiteSpace($Url) -and [string]::IsNullOrWhiteSpace($ExpectedSha512)) { return }
    if ([string]::IsNullOrWhiteSpace($Url) -or [string]::IsNullOrWhiteSpace($ExpectedSha512)) {
        throw "-PaleMirrorVisualsUrl and -PaleMirrorVisualsSha512 must be supplied together"
    }
    if ($Url -notmatch '^https?://') { throw "Pale Mirror Visuals artifact URL must use HTTP or HTTPS" }
    $ExpectedSha512 = $ExpectedSha512.ToLowerInvariant()
    if ($ExpectedSha512 -notmatch '^[0-9a-f]{128}$') {
        throw "Pale Mirror Visuals SHA-512 must contain exactly 128 hexadecimal characters"
    }

    $modsDirectory = Join-Path $TargetDirectory "mods"
    $cacheDirectory = Join-Path $TargetDirectory ".far-frontier-installer-cache"
    $managedJar = Join-Path $modsDirectory "pale_mirror_visuals-hosted.jar"
    New-Item -ItemType Directory -Force -Path $modsDirectory, $cacheDirectory | Out-Null
    $candidates = @(Get-ChildItem -LiteralPath $modsDirectory -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'pale_mirror_visuals*.jar' -or $_.Name -like 'pale-mirror-visuals*.jar' })
    if ($candidates.Count -gt 1) { throw "Multiple Pale Mirror Visuals JARs are present; refusing to choose" }
    if ($candidates.Count -eq 1) {
        $existing = $candidates[0].FullName
        if ((Get-Sha512 -Path $existing) -eq $ExpectedSha512) {
            Write-Host "Pale Mirror Visuals already installed and SHA-512 verified: $existing"
            return
        }
        if ($existing -ne $managedJar) {
            throw "An unmanaged Pale Mirror Visuals JAR conflicts with the hosted artifact: $existing"
        }
    }

    $cacheJar = Join-Path $cacheDirectory "pale-mirror-visuals-$($ExpectedSha512.Substring(0, 16)).jar"
    if (-not (Test-Path -LiteralPath $cacheJar) -or (Get-Sha512 -Path $cacheJar) -ne $ExpectedSha512) {
        $downloadTemp = Join-Path $cacheDirectory ".pale-mirror-visuals-download.$([guid]::NewGuid()).jar"
        try {
            Write-Host "Downloading checksum-pinned Pale Mirror Visuals artifact..."
            Invoke-WebRequest -Uri $Url -OutFile $downloadTemp
            $actual = Get-Sha512 -Path $downloadTemp
            if ($actual -ne $ExpectedSha512) {
                throw "Pale Mirror Visuals SHA-512 verification failed. Expected: $ExpectedSha512 Actual: $actual"
            }
            Move-Item -LiteralPath $downloadTemp -Destination $cacheJar -Force
        }
        finally { Remove-Item -LiteralPath $downloadTemp -Force -ErrorAction SilentlyContinue }
    }

    $installTemp = Join-Path $modsDirectory ".pale-mirror-visuals-install.$([guid]::NewGuid()).jar"
    try {
        Copy-Item -LiteralPath $cacheJar -Destination $installTemp
        Move-Item -LiteralPath $installTemp -Destination $managedJar -Force
    }
    finally { Remove-Item -LiteralPath $installTemp -Force -ErrorAction SilentlyContinue }
    if ((Get-Sha512 -Path $managedJar) -ne $ExpectedSha512) {
        throw "Installed Pale Mirror Visuals JAR failed its final SHA-512 verification"
    }
    Write-Host "Pale Mirror Visuals installed and SHA-512 verified: $managedJar"
}

function Install-HostedRailwayUntold {
    param([string]$TargetDirectory, [string]$Url, [string]$ExpectedSha512)
    if ([string]::IsNullOrWhiteSpace($Url) -and [string]::IsNullOrWhiteSpace($ExpectedSha512)) { return }
    if ([string]::IsNullOrWhiteSpace($Url) -or [string]::IsNullOrWhiteSpace($ExpectedSha512)) {
        throw "-RailwayUntoldUrl and -RailwayUntoldSha512 must be supplied together"
    }
    if ($Url -notmatch '^https?://') { throw "Railway Untold artifact URL must use HTTP or HTTPS" }
    $ExpectedSha512 = $ExpectedSha512.ToLowerInvariant()
    if ($ExpectedSha512 -notmatch '^[0-9a-f]{128}$') { throw "Railway Untold SHA-512 must contain 128 hex characters" }
    $modsDirectory = Join-Path $TargetDirectory "mods"
    $cacheDirectory = Join-Path $TargetDirectory ".far-frontier-installer-cache"
    $managedJar = Join-Path $modsDirectory "railwaysuntold-pm-hosted.jar"
    New-Item -ItemType Directory -Force -Path $modsDirectory, $cacheDirectory | Out-Null
    $candidates = @(Get-ChildItem -LiteralPath $modsDirectory -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like 'railwaysuntold*.jar' })
    if ($candidates.Count -gt 1) { throw "Multiple Railway Untold JARs are present; refusing to choose" }
    if ($candidates.Count -eq 1) {
        $existing = $candidates[0].FullName
        if ((Get-Sha512 -Path $existing) -eq $ExpectedSha512) {
            Write-Host "PM Railway Untold already verified: $existing"; return
        }
        if ($existing -ne $managedJar) { throw "An unmanaged Railway Untold JAR conflicts with the PM fork: $existing" }
    }
    $cacheJar = Join-Path $cacheDirectory "railwaysuntold-pm-$($ExpectedSha512.Substring(0, 16)).jar"
    if (-not (Test-Path -LiteralPath $cacheJar) -or (Get-Sha512 -Path $cacheJar) -ne $ExpectedSha512) {
        $downloadTemp = Join-Path $cacheDirectory ".railway-download.$([guid]::NewGuid()).jar"
        try {
            Invoke-WebRequest -Uri $Url -OutFile $downloadTemp
            $actual = Get-Sha512 -Path $downloadTemp
            if ($actual -ne $ExpectedSha512) { throw "PM Railway Untold checksum failed. Expected $ExpectedSha512, got $actual" }
            Move-Item -LiteralPath $downloadTemp -Destination $cacheJar -Force
        } finally { Remove-Item -LiteralPath $downloadTemp -Force -ErrorAction SilentlyContinue }
    }
    $installTemp = Join-Path $modsDirectory ".railway-install.$([guid]::NewGuid()).jar"
    try {
        Copy-Item -LiteralPath $cacheJar -Destination $installTemp
        Move-Item -LiteralPath $installTemp -Destination $managedJar -Force
    } finally { Remove-Item -LiteralPath $installTemp -Force -ErrorAction SilentlyContinue }
    if ((Get-Sha512 -Path $managedJar) -ne $ExpectedSha512) { throw "Installed PM Railway Untold checksum failed" }
    Write-Host "PM Railway Untold installed and verified: $managedJar"
}


function Get-HostedSha512 {
    param([Parameter(Mandatory = $true)][string]$Uri)

    $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri
    $content = if ($response.Content -is [byte[]]) {
        [Text.Encoding]::UTF8.GetString($response.Content)
    } else {
        [string]$response.Content
    }
    $value = $content.Trim().Split()[0].ToLowerInvariant()
    if ($value -notmatch '^[0-9a-f]{128}$') {
        throw "Hosted checksum endpoint returned an invalid SHA-512: $Uri"
    }
    return $value
}


if ([string]::IsNullOrWhiteSpace($SourceBaseUrl)) {
    $SourceBaseUrl = "http://192.168.0.100:8092"
}
$SourceBaseUrl = $SourceBaseUrl.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($PackUrl)) {
    $PackUrl = "$SourceBaseUrl/pack.toml"
}
if ([string]::IsNullOrWhiteSpace($PaleMirrorUrl) -and [string]::IsNullOrWhiteSpace($PaleMirrorSha512)) {
    $PaleMirrorUrl = "$SourceBaseUrl/hosted/pale_mirror-current.jar"
    $PaleMirrorSha512 = Get-HostedSha512 -Uri "$PaleMirrorUrl.sha512"
}
if ([string]::IsNullOrWhiteSpace($PaleMirrorVisualsUrl) -and
    [string]::IsNullOrWhiteSpace($PaleMirrorVisualsSha512)) {
    $PaleMirrorVisualsUrl = "$SourceBaseUrl/hosted/pale_mirror_visuals-current.jar"
    $PaleMirrorVisualsSha512 = Get-HostedSha512 -Uri "$PaleMirrorVisualsUrl.sha512"
}
if ([string]::IsNullOrWhiteSpace($RailwayUntoldUrl) -and [string]::IsNullOrWhiteSpace($RailwayUntoldSha512)) {
    $RailwayUntoldUrl = "$SourceBaseUrl/hosted/railwaysuntold-pm-current.jar"
    $RailwayUntoldSha512 = Get-HostedSha512 -Uri "$RailwayUntoldUrl.sha512"
}


Assert-Java21


# Ensure the target Minecraft directory exists.
New-Item -ItemType Directory -Force -Path $Target | Out-Null
$Target = (Resolve-Path -LiteralPath $Target).Path

$Bootstrap = Join-Path $Target "packwiz-installer-bootstrap.jar"


# Download bootstrap if missing or if its checksum does not match.
$mustDownload = -not (Test-Path -LiteralPath $Bootstrap)

if (-not $mustDownload) {
    $actualHash = Get-Sha256 -Path $Bootstrap

    if ($actualHash -ne $BootstrapSha256) {
        Write-Host "Existing bootstrap checksum does not match; downloading it again."
        $mustDownload = $true
    }
}


if ($mustDownload) {
    Write-Host "Downloading packwiz installer bootstrap $BootstrapVersion..."

    Invoke-WebRequest `
        -Uri $BootstrapUrl `
        -OutFile $Bootstrap
}


# Verify downloaded/existing bootstrap.
$verifiedHash = Get-Sha256 -Path $Bootstrap

if ($verifiedHash -ne $BootstrapSha256) {
    Remove-Item -LiteralPath $Bootstrap -Force -ErrorAction SilentlyContinue

    throw @"
Bootstrap SHA-256 verification failed.

File:
$Bootstrap

Expected:
$BootstrapSha256

Actual:
$verifiedHash
"@
}


Write-Host "Synchronising client pack..."
Write-Host "Target:  $Target"
Write-Host "Pack URL: $PackUrl"


# packwiz installs relative to the current working directory,
# therefore run it from the target Minecraft directory.
Push-Location $Target

try {
    & $Java -jar $Bootstrap -g $PackUrl

    $installerExitCode = $LASTEXITCODE

    if ($installerExitCode -ne 0) {
        throw "packwiz installer failed with exit code $installerExitCode"
    }
}
finally {
    Pop-Location
}

# Migrate clients that previously received the optional Caliber artifact. It is
# no longer part of the default manifest and must not remain active accidentally.
$ModsDirectory = Join-Path $Target "mods"
if (Test-Path -LiteralPath $ModsDirectory) {
    Get-ChildItem -LiteralPath $ModsDirectory -Filter "createcaliber*.jar" -File -ErrorAction SilentlyContinue |
    ForEach-Object {
        $disabledPath = $_.FullName + ".disabled"
        if (Test-Path -LiteralPath $disabledPath) {
            Remove-Item -LiteralPath $disabledPath -Force
        }
        Rename-Item -LiteralPath $_.FullName -NewName ($_.Name + ".disabled")
        Write-Host "Disabled legacy evaluation mod: $($_.Name)"
    }

    # Packwiz preserves unmanaged files. Remove older manually installed Iris
    # builds from the active mod set so only the Sodium-0.8-compatible build
    # pinned by this pack reaches mixin initialization.
    $managedIris = "iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"
    Get-ChildItem -LiteralPath $ModsDirectory -Filter "iris*.jar" -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -cne $managedIris } |
    ForEach-Object {
        $disabledPath = $_.FullName + ".disabled"
        if (Test-Path -LiteralPath $disabledPath) {
            Remove-Item -LiteralPath $disabledPath -Force
        }
        Rename-Item -LiteralPath $_.FullName -NewName ($_.Name + ".disabled")
        Write-Host "Disabled incompatible Iris build: $($_.Name)"
    }
}

Install-HostedPaleMirror `
    -TargetDirectory $Target `
    -Url $PaleMirrorUrl `
    -ExpectedSha512 $PaleMirrorSha512

Install-HostedPaleMirrorVisuals `
    -TargetDirectory $Target `
    -Url $PaleMirrorVisualsUrl `
    -ExpectedSha512 $PaleMirrorVisualsSha512

Install-HostedRailwayUntold `
    -TargetDirectory $Target `
    -Url $RailwayUntoldUrl `
    -ExpectedSha512 $RailwayUntoldSha512


Write-Host ""
Write-Host "Client pack synchronised successfully."
Write-Host "Directory: $Target"
Write-Host "Launch this directory with Minecraft 1.21.1 / NeoForge 21.1.248."
