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

    [Parameter(Mandatory = $true)]
    [string]$InputVisualReview,

    [ValidateSet('upstream_fp32_50', 'bounded_fp16_20')]
    [string]$ExecutionProfile = 'bounded_fp16_20',

    [ValidateRange(1, 14)]
    [int]$DecodingT = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-Sha256([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Required file is missing: $Path" }
    return (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Require-Equal([string]$Actual, [string]$Expected, [string]$Label) {
    if ($Actual.ToLowerInvariant() -ne $Expected.ToLowerInvariant()) { throw "$Label does not match its pinned manifest value." }
}

function Resolve-RepositoryChild([string]$RepositoryRoot, [string]$RelativePath, [string]$Label) {
    if ($RelativePath -match '^[A-Za-z]:') { throw "$Label must be a relative build/automodel path." }
    if ($RelativePath -match '(^[\\/]|\.\.)') { throw "$Label must be a relative build/automodel path." }
    $full = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $RelativePath))
    $buildRoot = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'build\automodel')).TrimEnd([char]92, [char]47)
    if (-not $full.StartsWith($buildRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "$Label must stay under build/automodel."
    }
    return $full
}

function Resolve-RunManifest([string]$RepositoryRoot, [string]$RawPath) {
    $buildRoot = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'build\automodel')).TrimEnd([char]92, [char]47)
    $full = if ([System.IO.Path]::IsPathRooted($RawPath)) {
        [System.IO.Path]::GetFullPath($RawPath)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $RawPath))
    }
    if (-not $full.StartsWith($buildRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'Run manifest must remain below build/automodel.'
    }
    return $full
}

function Get-ManifestInput($Manifest, [string]$Role, [string]$RepositoryRoot) {
    $matches = @($Manifest.inputs | Where-Object { $_.role -eq $Role })
    if ($matches.Count -ne 1) { throw "Manifest must contain exactly one input with role '$Role'." }
    $record = $matches[0]
    $path = Resolve-RepositoryChild $RepositoryRoot $record.file "input '$Role'"
    Require-Equal (Get-Sha256 $path) $record.sha256 "Input '$Role'"
    return $path
}

function Get-ManifestInputAny($Manifest, [string[]]$Roles, [string]$Label, [string]$RepositoryRoot) {
    $matches = @($Manifest.inputs | Where-Object { $Roles -contains $_.role })
    if ($matches.Count -ne 1) { throw "Manifest must contain exactly one input for '$Label'." }
    $record = $matches[0]
    $path = Resolve-RepositoryChild $RepositoryRoot $record.file "input '$Label'"
    Require-Equal (Get-Sha256 $path) $record.sha256 "Input '$Label'"
    return [pscustomobject]@{ record = $record; path = $path }
}

function Get-RequiredJsonProperty($Object, [string]$Name, [string]$Context) {
    if ($null -eq $Object) { throw "$Context is missing." }
    $matches = @($Object.PSObject.Properties.Match($Name))
    if ($matches.Count -ne 1) { throw "$Context is missing required property '$Name'." }
    return $matches[0].Value
}

function Invoke-PinnedPython {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $previousErrorAction = $ErrorActionPreference
    try {
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
$manifestPath = Resolve-RunManifest $repositoryRoot $RunManifest
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Run manifest is missing.' }
$manifest = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
if ($manifest.schema -ne 'pale_mirror.automodel.run_manifest.v1' -or $manifest.scope -ne 'research_only_noncanonical' -or $manifest.promotion_prohibited -ne $true) {
    throw 'Run manifest is not a noncanonical Edit360 dual-anchor manifest.'
}
$profile = switch ($ExecutionProfile) {
    'upstream_fp32_50' {
        [ordered]@{
            adapter_id = 'edit360_dual_anchor_orbit'
            sampling_steps = 50
            precision = 'upstream_fp32_cuda_autocast'
            runner = (Join-Path $scriptRoot 'upstream_sample_two.py')
        }
    }
    'bounded_fp16_20' {
        [ordered]@{
            adapter_id = 'edit360_dual_anchor_orbit_fast_fp16_20'
            sampling_steps = 20
            precision = 'fp16_denoiser_fp32_codecs_cpu_offload_cuda_autocast'
            runner = (Join-Path $scriptRoot 'low_vram_fp16_sample_two.py')
        }
    }
}
if ($manifest.adapter_id -ne $profile.adapter_id) { throw "Execution profile requires adapter $($profile.adapter_id)." }
if (-not (Test-Path -LiteralPath $profile.runner -PathType Leaf)) { throw "Edit360 runner is missing: $($profile.runner)" }
$runDirectory = Resolve-RepositoryChild $repositoryRoot $manifest.run_directory 'run_directory'
if ([System.IO.Path]::GetDirectoryName($manifestPath) -ne $runDirectory -or $manifest.execution.status -ne 'prepared_not_executed') {
    throw 'Edit360 requires a fresh manifest directly inside its declared run directory.'
}
$source = [System.IO.Path]::GetFullPath($SourceRoot)
$pythonExe = [System.IO.Path]::GetFullPath($Python)
if (-not (Test-Path -LiteralPath $pythonExe -PathType Leaf)) { throw 'Python executable is missing.' }
$actualRevision = (& git -C $source rev-parse HEAD).Trim().ToLowerInvariant()
Require-Equal $actualRevision $manifest.provenance.adapter_revision 'Edit360 source revision'
$expectedCheckpoint = [System.IO.Path]::GetFullPath((Join-Path $source 'checkpoints\sv3d_u.safetensors'))
if ([System.IO.Path]::GetFullPath($Checkpoint) -ne $expectedCheckpoint) { throw 'Edit360 checkpoint must be source-root checkpoints\sv3d_u.safetensors.' }
$checkpointSha = Get-Sha256 $Checkpoint
$environmentLockSha = Get-Sha256 $EnvironmentLock
Require-Equal $checkpointSha $manifest.provenance.checkpoint_sha256 'Edit360 checkpoint'
Require-Equal $environmentLockSha $manifest.provenance.environment_lock_sha256 'Edit360 environment lock'
$literalPrimary = Get-ManifestInput $manifest 'primary' $repositoryRoot
$frontInput = Get-ManifestInputAny $manifest @('conditioning:edit360_front_white_r01', 'conditioning:edit360_generated_front_white_r01') 'Edit360 front conditioning' $repositoryRoot
$frontConditioning = $frontInput.path
$frontConditioningRole = $frontInput.record.role
$oppositeAnchor = Get-ManifestInput $manifest 'anchor:edit360_opposite_broadside_r01' $repositoryRoot
$checkpointReceiptPath = Resolve-RepositoryChild $repositoryRoot $CheckpointReceipt 'checkpoint receipt'
$checkpointReceiptSha = Get-Sha256 $checkpointReceiptPath
$officialCheckpointReceipt = Get-Content -Raw -LiteralPath $checkpointReceiptPath | ConvertFrom-Json
if ($officialCheckpointReceipt.schema -ne 'pale_mirror.automodel.official_checkpoint_receipt.v1' `
    -or $officialCheckpointReceipt.model_id -ne $profile.adapter_id `
    -or $officialCheckpointReceipt.official_origin.repository -ne 'stabilityai/sv3d' `
    -or $officialCheckpointReceipt.official_origin.filename -ne 'sv3d_u.safetensors' `
    -or $officialCheckpointReceipt.checkpoint.sha256 -ne $checkpointSha `
    -or $officialCheckpointReceipt.scope -ne 'research_only_noncanonical' `
    -or $officialCheckpointReceipt.promotion_prohibited -ne $true) {
    throw 'Edit360 checkpoint does not match its registered official-checkpoint receipt.'
}
$inputVisualReviewPath = Resolve-RepositoryChild $repositoryRoot $InputVisualReview 'input visual review'
$inputVisualReviewSha = Get-Sha256 $inputVisualReviewPath
$inputVisualReviewRecord = Get-Content -Raw -LiteralPath $inputVisualReviewPath | ConvertFrom-Json
$inputReviewSchema = Get-RequiredJsonProperty $inputVisualReviewRecord 'schema' 'input visual review'
$inputReviewScope = Get-RequiredJsonProperty $inputVisualReviewRecord 'scope' 'input visual review'
$inputReviewPromotionProhibited = Get-RequiredJsonProperty $inputVisualReviewRecord 'promotion_prohibited' 'input visual review'
$inputReviewDecision = Get-RequiredJsonProperty $inputVisualReviewRecord 'decision' 'input visual review'
$inputReviewManifest = Get-RequiredJsonProperty $inputVisualReviewRecord 'run_manifest' 'input visual review'
$inputReviewInputs = Get-RequiredJsonProperty $inputVisualReviewRecord 'inputs' 'input visual review'
$inputReviewContract = Get-RequiredJsonProperty $inputVisualReviewRecord 'contract' 'input visual review'
$inputReviewFront = Get-RequiredJsonProperty $inputReviewInputs 'front_conditioning' 'input visual review inputs'
$inputReviewOpposite = Get-RequiredJsonProperty $inputReviewInputs 'opposite_anchor' 'input visual review inputs'

if ($inputReviewSchema -ne 'pale_mirror.automodel.edit360_input_visual_review.v1' `
    -or $inputReviewScope -ne 'research_only_noncanonical' `
    -or $inputReviewPromotionProhibited -ne $true `
    -or $inputReviewDecision -ne 'accepted_for_noncanonical_visual_preview_only' `
    -or (Get-RequiredJsonProperty $inputReviewManifest 'sha256' 'input visual review run_manifest') -ne (Get-Sha256 $manifestPath) `
    -or (Get-RequiredJsonProperty $inputReviewFront 'role' 'input visual review front_conditioning') -ne $frontConditioningRole `
    -or (Get-RequiredJsonProperty $inputReviewFront 'sha256' 'input visual review front_conditioning') -ne (Get-Sha256 $frontConditioning) `
    -or (Get-RequiredJsonProperty $inputReviewOpposite 'sha256' 'input visual review opposite_anchor') -ne (Get-Sha256 $oppositeAnchor) `
    -or @('verified_opposite_broadside', 'actual_rear_right_three_quarter') -notcontains (Get-RequiredJsonProperty $inputReviewOpposite 'view_kind' 'input visual review opposite_anchor') `
    -or (Get-RequiredJsonProperty $inputReviewContract 'visual_preview_only' 'input visual review contract') -ne $true `
    -or (Get-RequiredJsonProperty $inputReviewContract 'view_set_vggt_geometry_canonical_use_prohibited' 'input visual review contract') -ne $true) {
    throw 'Edit360 requires a matching explicit visual review of its exact staged conditioning inputs.'
}
Invoke-PinnedPython -Arguments @((Join-Path $scriptRoot 'preflight_imports.py'), '--source-root', $source)
if ($pinnedPythonExitCode -ne 0) { throw 'Edit360 dependency/CUDA preflight failed.' }

$preflightPath = Join-Path $runDirectory 'windows_edit360_preflight.json'
if ($Phase -eq 'Preflight') {
    if (Test-Path -LiteralPath $preflightPath) { throw 'Refusing to overwrite Edit360 preflight receipt.' }
    [ordered]@{
        schema = 'pale_mirror.automodel.windows_edit360_preflight.v1'
        asset_id = $manifest.asset_id
        adapter_id = $manifest.adapter_id
        scope = 'research_only_noncanonical'
        promotion_prohibited = $true
        source_revision = $actualRevision
        checkpoint_sha256 = $checkpointSha
        environment_lock_sha256 = $environmentLockSha
        checkpoint_receipt_sha256 = $checkpointReceiptSha
        input_visual_review_sha256 = $inputVisualReviewSha
        literal_primary_sha256 = (Get-Sha256 $literalPrimary)
        front_conditioning_sha256 = (Get-Sha256 $frontConditioning)
        opposite_anchor_sha256 = (Get-Sha256 $oppositeAnchor)
        opposite_anchor_declared_yaw_degrees = 180
        execution_profile = $ExecutionProfile
        sampling_steps = $profile.sampling_steps
        decoding_t = $DecodingT
        precision = $profile.precision
        output_authority = 'MODEL_DERIVED'
        quality_qualification = 'not_performed; views require independent visual and pose review before any geometry experiment'
        status = 'preflight_passed'
    } | ConvertTo-Json -Depth 5 | ForEach-Object {
        [System.IO.File]::WriteAllText($preflightPath, $_, [System.Text.UTF8Encoding]::new($false))
    }
    Write-Output $preflightPath
    exit 0
}

if (-not (Test-Path -LiteralPath $preflightPath -PathType Leaf)) { throw 'Inference requires a separate Edit360 preflight receipt.' }
$preflight = Get-Content -Raw -LiteralPath $preflightPath | ConvertFrom-Json
if ($preflight.status -ne 'preflight_passed' -or $preflight.checkpoint_sha256 -ne $checkpointSha -or $preflight.input_visual_review_sha256 -ne $inputVisualReviewSha -or $preflight.front_conditioning_sha256 -ne (Get-Sha256 $frontConditioning) -or $preflight.opposite_anchor_sha256 -ne (Get-Sha256 $oppositeAnchor) -or $preflight.execution_profile -ne $ExecutionProfile -or $preflight.sampling_steps -ne $profile.sampling_steps -or $preflight.decoding_t -ne $DecodingT) {
    throw 'Edit360 preflight receipt is absent, stale, or does not match the exact inputs.'
}
$rawVideo = Join-Path $runDirectory 'outputs\raw_video'
$rawFrames = Join-Path $runDirectory 'outputs\raw_frames'
$sequenceReview = Join-Path $runDirectory 'outputs\visual_sequence_review'
if ((Test-Path -LiteralPath $rawVideo) -or (Test-Path -LiteralPath $rawFrames) -or (Test-Path -LiteralPath $sequenceReview)) {
    throw 'Edit360 output path already exists; prepare a new immutable run.'
}
New-Item -ItemType Directory -Force -Path $rawVideo, $rawFrames | Out-Null
Push-Location $source
try {
    Invoke-PinnedPython -Arguments @(
        $profile.runner,
        '--source-root', $source,
        '--mode', 'two',
        '--input-path-f', $frontConditioning,
        '--input-path-b', $oppositeAnchor,
        '--version', 'sv3d_u',
        '--num-steps', "$($profile.sampling_steps)",
        '--seed', "$($manifest.seed)",
        '--decoding-t', "$DecodingT",
        '--device', 'cuda',
        '--output-folder-mp4', $rawVideo,
        '--output-folder-img', $rawFrames,
        '--anchor-view-angle', '180'
    )
    if ($pinnedPythonExitCode -ne 0) { throw 'Pinned Edit360 inference failed.' }
} finally { Pop-Location }
if (@(Get-ChildItem -LiteralPath $rawVideo -Filter '*.mp4' -File).Count -ne 1) { throw 'Edit360 must write exactly one MP4.' }
if (@(Get-ChildItem -LiteralPath $rawFrames -Filter '*.png' -File).Count -ne 21) { throw 'Edit360 must write exactly twenty-one PNG positions.' }
Invoke-PinnedPython -Arguments @(
    (Join-Path $scriptRoot 'review_edit360_sequence.py'),
    '--run-manifest', $manifestPath,
    '--frames-directory', $rawFrames,
    '--front-conditioning', $frontConditioning,
    '--opposite-anchor', $oppositeAnchor,
    '--output-directory', $sequenceReview
)
if ($pinnedPythonExitCode -ne 0) { throw 'Edit360 visual-sequence review package failed.' }
