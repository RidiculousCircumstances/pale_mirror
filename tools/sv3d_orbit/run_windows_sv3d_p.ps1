[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('Preflight', 'Infer')]
    [string]$Phase,

    [Parameter(Mandatory = $true)]
    [string]$RunManifest,

    [Parameter(Mandatory = $true)]
    [string]$SourceRoot,

    [Parameter(Mandatory = $true)]
    [string]$Python,

    [Parameter(Mandatory = $true)]
    [string]$Checkpoint,

    [Parameter(Mandatory = $true)]
    [string]$EnvironmentLock,

    [Parameter(Mandatory = $true)]
    [string]$CheckpointReceipt,

    [ValidateSet('full_fp32_50', 'fast_fp16_20')]
    [string]$ExecutionProfile = 'full_fp32_50',

    [ValidateSet('official_default', 'low_vram_conditioner_cpu_offload', 'low_vram_fp16_conditioner_cpu_offload')]
    [string]$MemoryProfile = 'official_default',

    [ValidateRange(1, 14)]
    [int]$DecodingT = 2,

    [ValidateSet('isolated_subject_v1', 'generated_cutout_r01')]
    [string]$ConditioningId = 'isolated_subject_v1'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-Sha256([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Required file is missing: $Path"
    }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Require-Equal([string]$Actual, [string]$Expected, [string]$Label) {
    if ($Actual.ToLowerInvariant() -ne $Expected.ToLowerInvariant()) {
        throw "$Label does not match its pinned manifest value."
    }
}

function Resolve-RepositoryChild([string]$RepositoryRoot, [string]$RelativePath, [string]$Label) {
    if ($RelativePath -match '^[A-Za-z]:') {
        throw "$Label must be a relative build/automodel path."
    }
    if ($RelativePath -match '(^[\\/]|\.\.)') {
        throw "$Label must be a relative build/automodel path."
    }
    $full = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $RelativePath))
    $buildRoot = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'build\automodel')).TrimEnd([char]92, [char]47)
    $buildPrefix = $buildRoot + [System.IO.Path]::DirectorySeparatorChar
    if (-not $full.StartsWith($buildPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must stay under build/automodel."
    }
    return $full
}

function Get-ManifestInput($Manifest, [string]$Role, [string]$RepositoryRoot) {
    $matches = @($Manifest.inputs | Where-Object { $_.role -eq $Role })
    if ($matches.Count -ne 1) {
        throw "Manifest must contain exactly one input with role '$Role'."
    }
    $record = $matches[0]
    $path = Resolve-RepositoryChild $RepositoryRoot $record.file "input '$Role'"
    Require-Equal (Get-Sha256 $path) $record.sha256 "Input '$Role'"
    return $path
}

function Invoke-PinnedPython {
    param(
        [Parameter(Mandatory = $true)]
        [string[]]$Arguments
    )
    # Python packages emit version and optional-acceleration warnings on
    # stderr.  Merge that stream into the captureable transcript, then make
    # the explicit Python exit code—not warning text—the only success signal.
    # This is required for a PowerShell caller that deliberately uses
    # `$ErrorActionPreference = 'Stop'` to keep a failed experiment visible.
    $previousErrorAction = $ErrorActionPreference
    try {
        # Native stderr becomes an ErrorRecord when the caller globally uses
        # `Stop`; temporarily use Continue while the stream is redirected.
        $ErrorActionPreference = 'Continue'
        $captured = & $pythonExe @Arguments 2>&1
        $script:pinnedPythonExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorAction
    }
    $captured | Write-Output
}

$scriptRoot = Split-Path -Parent $PSCommandPath
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot '..\..'))
$manifestPath = [System.IO.Path]::GetFullPath($RunManifest)
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Run manifest is missing: $manifestPath"
}
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($manifest.schema -ne 'pale_mirror.automodel.run_manifest.v1' -or $manifest.scope -ne 'research_only_noncanonical' -or $manifest.promotion_prohibited -ne $true) {
    throw 'Run manifest is not a noncanonical Automodel manifest.'
}
$profile = switch ($ExecutionProfile) {
    'full_fp32_50' {
        [ordered]@{
            adapter_id = 'sv3d_p_orbit'
            sampling_steps = 50
            precision = 'fp32'
        }
    }
    'fast_fp16_20' {
        [ordered]@{
            adapter_id = 'sv3d_p_orbit_fast_fp16_20'
            sampling_steps = 20
            precision = 'fp16_denoiser_fp32_codecs_cuda_autocast'
        }
    }
}
if ($manifest.adapter_id -ne $profile.adapter_id) {
    throw "This execution profile only accepts the $($profile.adapter_id) adapter."
}
if ($ExecutionProfile -eq 'fast_fp16_20' -and $MemoryProfile -ne 'low_vram_fp16_conditioner_cpu_offload') {
    throw 'fast_fp16_20 requires low_vram_fp16_conditioner_cpu_offload.'
}
if ($ExecutionProfile -eq 'full_fp32_50' -and $MemoryProfile -eq 'low_vram_fp16_conditioner_cpu_offload') {
    throw 'full_fp32_50 may not use the fp16 low-VRAM helper.'
}
$runDirectory = Resolve-RepositoryChild $repositoryRoot $manifest.run_directory 'run_directory'
if ([System.IO.Path]::GetDirectoryName($manifestPath) -ne $runDirectory) {
    throw 'Run manifest must be stored directly in its declared run directory.'
}
if ($manifest.execution.status -ne 'prepared_not_executed') {
    throw 'Runner requires a freshly prepared manifest; never reuse or mutate a recorded run.'
}
$source = [System.IO.Path]::GetFullPath($SourceRoot)
$pythonExe = [System.IO.Path]::GetFullPath($Python)
if (-not (Test-Path -LiteralPath $pythonExe -PathType Leaf)) {
    throw "Python executable is missing: $pythonExe"
}
$actualRevision = (& git -C $source rev-parse HEAD).Trim().ToLowerInvariant()
if ($manifest.provenance.adapter_revision.Length -ne 40) {
    throw 'SV3D runner requires a pinned 40-character upstream Git revision.'
}
Require-Equal $actualRevision $manifest.provenance.adapter_revision 'SV3D source revision'
Require-Equal (Get-Sha256 $Checkpoint) $manifest.provenance.checkpoint_sha256 'SV3D checkpoint'
Require-Equal (Get-Sha256 $EnvironmentLock) $manifest.provenance.environment_lock_sha256 'SV3D environment lock'
$expectedCheckpoint = [System.IO.Path]::GetFullPath((Join-Path $source 'checkpoints\sv3d_p.safetensors'))
if ([System.IO.Path]::GetFullPath($Checkpoint) -ne $expectedCheckpoint) {
    throw 'SV3D checkpoint must be the private source-root checkpoints\sv3d_p.safetensors file pinned in the manifest.'
}
$literalPrimary = Get-ManifestInput $manifest 'primary' $repositoryRoot
$conditioningRole = "conditioning:$ConditioningId"
$conditioning = Get-ManifestInput $manifest $conditioningRole $repositoryRoot
$memoryProfileRunner = switch ($MemoryProfile) {
    'low_vram_conditioner_cpu_offload' { Join-Path $scriptRoot 'low_vram_sample.py' }
    'low_vram_fp16_conditioner_cpu_offload' { Join-Path $scriptRoot 'low_vram_fp16_sample.py' }
    default { $null }
}
if ($null -ne $memoryProfileRunner) {
    if (-not (Test-Path -LiteralPath $memoryProfileRunner -PathType Leaf)) {
        throw "SV3D low-VRAM helper is missing: $memoryProfileRunner"
    }
}
$memoryProfileRunnerSha256 = if ($null -eq $memoryProfileRunner) { $null } else { Get-Sha256 $memoryProfileRunner }
$checkpointReceiptPath = Resolve-RepositoryChild $repositoryRoot $CheckpointReceipt 'checkpoint receipt'
if (-not (Test-Path -LiteralPath $checkpointReceiptPath -PathType Leaf)) {
    throw "Official checkpoint receipt is missing: $checkpointReceiptPath"
}
$officialCheckpointReceipt = Get-Content -Raw -LiteralPath $checkpointReceiptPath | ConvertFrom-Json
if ($officialCheckpointReceipt.schema -ne 'pale_mirror.automodel.official_checkpoint_receipt.v1' `
    -or $officialCheckpointReceipt.model_id -ne 'sv3d_p_orbit' `
    -or $officialCheckpointReceipt.scope -ne 'research_only_noncanonical' `
    -or $officialCheckpointReceipt.promotion_prohibited -ne $true `
    -or $officialCheckpointReceipt.official_origin.repository -ne 'stabilityai/sv3d' `
    -or $officialCheckpointReceipt.official_origin.filename -ne 'sv3d_p.safetensors' `
    -or $officialCheckpointReceipt.checkpoint.sha256 -ne (Get-Sha256 $Checkpoint) `
    -or $officialCheckpointReceipt.checkpoint.byte_size -ne (Get-Item -LiteralPath $Checkpoint).Length `
    -or $officialCheckpointReceipt.checkpoint.storage -ne 'private_local_host' `
    -or $officialCheckpointReceipt.contract.weights_not_copied_to_repository -ne $true `
    -or $officialCheckpointReceipt.contract.canonical_promotion_forbidden -ne $true) {
    throw 'SV3D checkpoint does not match a registered official-checkpoint receipt.'
}
Invoke-PinnedPython -Arguments @(
    (Join-Path $scriptRoot 'preflight_imports.py'),
    '--source-root', $source,
    '--memory-profile', $MemoryProfile,
    '--runner-root', $scriptRoot
)
if ($pinnedPythonExitCode -ne 0) {
    throw 'SV3D dependency/CUDA preflight failed.'
}

$preflightPath = Join-Path $runDirectory 'windows_sv3d_preflight.json'
if ($Phase -eq 'Preflight') {
    if (Test-Path -LiteralPath $preflightPath) {
        throw "Refusing to overwrite preflight receipt: $preflightPath"
    }
    $receipt = [ordered]@{
        schema = 'pale_mirror.automodel.windows_sv3d_preflight.v1'
        asset_id = $manifest.asset_id
        adapter_id = $manifest.adapter_id
        scope = 'research_only_noncanonical'
        promotion_prohibited = $true
        source_revision = $actualRevision
        checkpoint_sha256 = (Get-Sha256 $Checkpoint)
        environment_lock_sha256 = (Get-Sha256 $EnvironmentLock)
        checkpoint_receipt_sha256 = (Get-Sha256 $checkpointReceiptPath)
        literal_primary_sha256 = (Get-Sha256 $literalPrimary)
        conditioning_id = $ConditioningId
        conditioning_sha256 = (Get-Sha256 $conditioning)
        memory_profile = $MemoryProfile
        memory_profile_runner_sha256 = $memoryProfileRunnerSha256
        execution_profile = $ExecutionProfile
        sampling_steps = $profile.sampling_steps
        precision = $profile.precision
        decoding_t = $DecodingT
        output_authority = 'MODEL_DERIVED'
        quality_qualification = 'not_performed; generated views require independent identity and pose review before any geometry experiment'
        status = 'preflight_passed'
    }
    [System.IO.File]::WriteAllText(
        $preflightPath,
        ($receipt | ConvertTo-Json -Depth 5),
        [System.Text.UTF8Encoding]::new($false)
    )
    Write-Output $preflightPath
    exit 0
}

if (-not (Test-Path -LiteralPath $preflightPath -PathType Leaf)) {
    throw 'Inference requires a separate successful Windows SV3D preflight receipt.'
}
$preflight = Get-Content -Raw -LiteralPath $preflightPath | ConvertFrom-Json
$preflightMatches = ($preflight.status -eq 'preflight_passed' -and $preflight.checkpoint_sha256 -eq (Get-Sha256 $Checkpoint) -and $preflight.conditioning_id -eq $ConditioningId -and $preflight.conditioning_sha256 -eq (Get-Sha256 $conditioning) -and $preflight.memory_profile -eq $MemoryProfile -and $preflight.memory_profile_runner_sha256 -eq $memoryProfileRunnerSha256 -and $preflight.execution_profile -eq $ExecutionProfile -and $preflight.sampling_steps -eq $profile.sampling_steps -and $preflight.precision -eq $profile.precision -and $preflight.decoding_t -eq $DecodingT)
if (-not $preflightMatches) {
    throw 'Windows SV3D preflight receipt is absent, stale, or does not match this checkpoint.'
}

$rawOutput = Join-Path $runDirectory 'outputs\raw_video'
$nonPrimaryOutput = Join-Path $runDirectory 'outputs\nonprimary_orbit'
if ((Test-Path -LiteralPath $rawOutput) -or (Test-Path -LiteralPath $nonPrimaryOutput)) {
    throw 'Inference output path already exists; prepare a new immutable run instead of reusing it.'
}
New-Item -ItemType Directory -Force -Path $rawOutput | Out-Null
$azimuths = ((1..20 | ForEach-Object { $_ * 18 }) + 0) -join ','
$samplingScript = Join-Path $source 'scripts\sampling\simple_video_sample.py'
Push-Location $source
try {
    if ($null -ne $memoryProfileRunner) {
        $lowVramArguments = @(
            "--source-root=$source",
            "--input_path=$conditioning",
            '--version=sv3d_p',
            "--num_steps=$($profile.sampling_steps)",
            "--seed=$($manifest.seed)",
            "--decoding_t=$DecodingT",
            '--device=cuda',
            "--output_folder=$rawOutput",
            '--elevations_deg=10.0',
            "--azimuths_deg=[$azimuths]",
            '--image_frame_ratio=0.84'
        )
        $memoryProfileArguments = @($memoryProfileRunner) + $lowVramArguments
        Invoke-PinnedPython -Arguments $memoryProfileArguments
        if ($pinnedPythonExitCode -ne 0) {
            throw 'SV3D low-VRAM inference failed.'
        }
    } else {
    Invoke-PinnedPython -Arguments @(
        $samplingScript,
        "--input_path=$conditioning",
        '--version=sv3d_p',
        "--num_steps=$($profile.sampling_steps)",
        "--seed=$($manifest.seed)",
        "--decoding_t=$DecodingT",
        '--device=cuda',
        "--output_folder=$rawOutput",
        '--elevations_deg=10.0',
        "--azimuths_deg=[$azimuths]",
        '--image_frame_ratio=0.84'
    )
    if ($pinnedPythonExitCode -ne 0) {
        throw 'SV3D inference failed.'
    }
    }
} finally {
    Pop-Location
}
$video = @(Get-ChildItem -LiteralPath $rawOutput -Filter '*.mp4' -File)
if ($video.Count -ne 1) {
    throw "SV3D must write exactly one video; found $($video.Count)."
}
Invoke-PinnedPython -Arguments @(
    (Join-Path $scriptRoot 'extract_sv3d_orbit_frames.py'),
    '--video', $video[0].FullName,
    '--output', $nonPrimaryOutput
)
if ($pinnedPythonExitCode -ne 0) {
    throw 'SV3D frame extraction failed.'
}
Invoke-PinnedPython -Arguments @(
    (Join-Path $repositoryRoot 'tools\automodel\orchestrator.py'),
    'collect-views',
    '--run-manifest', $manifestPath,
    '--literal-primary', $literalPrimary,
    '--generated-directory', $nonPrimaryOutput,
    '--output', (Join-Path $runDirectory 'view_set.json')
)
if ($pinnedPythonExitCode -ne 0) {
    throw 'Automodel ViewSet collection failed.'
}
