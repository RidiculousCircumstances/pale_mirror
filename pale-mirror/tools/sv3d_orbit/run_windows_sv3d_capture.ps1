[CmdletBinding()]
param(
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

function Require-FreshFile([string]$Path, [string]$Label) {
    if (Test-Path -LiteralPath $Path) {
        throw "Refusing to overwrite ${Label}: $Path"
    }
}

$scriptRoot = Split-Path -Parent $PSCommandPath
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot '..\\..'))
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'build\\automodel')).TrimEnd([char]92, [char]47)
$manifestPath = [System.IO.Path]::GetFullPath($RunManifest)
$runDirectory = Split-Path -Parent $manifestPath
$buildPrefix = $buildRoot + [System.IO.Path]::DirectorySeparatorChar
if (-not $runDirectory.StartsWith($buildPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'Capture run manifest must remain below build/automodel.'
}
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "Run manifest is missing: $manifestPath"
}

$stdoutPath = Join-Path $runDirectory 'sv3d_inference.stdout.log'
$stderrPath = Join-Path $runDirectory 'sv3d_inference.stderr.log'
$receiptPath = Join-Path $runDirectory 'execution_capture_receipt.json'
Require-FreshFile $stdoutPath 'SV3D stdout capture'
Require-FreshFile $stderrPath 'SV3D stderr capture'
Require-FreshFile $receiptPath 'SV3D capture receipt'

$runner = Join-Path $scriptRoot 'run_windows_sv3d_p.ps1'
if (-not (Test-Path -LiteralPath $runner -PathType Leaf)) {
    throw "SV3D runner is missing: $runner"
}

$startedUtc = [DateTime]::UtcNow.ToString('o')
try {
    & $runner `
        -Phase Infer `
        -RunManifest $manifestPath `
        -SourceRoot $SourceRoot `
        -Python $Python `
        -Checkpoint $Checkpoint `
        -EnvironmentLock $EnvironmentLock `
        -CheckpointReceipt $CheckpointReceipt `
        -ExecutionProfile $ExecutionProfile `
        -MemoryProfile $MemoryProfile `
        -DecodingT $DecodingT `
        -ConditioningId $ConditioningId `
        1> $stdoutPath 2> $stderrPath
    if (-not $?) {
        throw 'SV3D runner returned a failed PowerShell status.'
    }
    [ordered]@{
        schema = 'pale_mirror.automodel.sv3d_capture_receipt.v1'
        started_utc = $startedUtc
        finished_utc = [DateTime]::UtcNow.ToString('o')
        outcome = 'success'
        stdout = 'sv3d_inference.stdout.log'
        stderr = 'sv3d_inference.stderr.log'
        execution_profile = $ExecutionProfile
        memory_profile = $MemoryProfile
        decoding_t = $DecodingT
        conditioning_id = $ConditioningId
        scope = 'research_only_noncanonical'
        promotion_prohibited = $true
    } | ForEach-Object {
        [System.IO.File]::WriteAllText(
            $receiptPath,
            ($_ | ConvertTo-Json -Depth 4),
            [System.Text.UTF8Encoding]::new($false)
        )
    }
    exit 0
} catch {
    $_ | Out-String | Add-Content -Encoding utf8 -LiteralPath $stderrPath
    [ordered]@{
        schema = 'pale_mirror.automodel.sv3d_capture_receipt.v1'
        started_utc = $startedUtc
        finished_utc = [DateTime]::UtcNow.ToString('o')
        outcome = 'failure'
        error = ($_ | Out-String).Trim()
        stdout = 'sv3d_inference.stdout.log'
        stderr = 'sv3d_inference.stderr.log'
        execution_profile = $ExecutionProfile
        memory_profile = $MemoryProfile
        decoding_t = $DecodingT
        conditioning_id = $ConditioningId
        scope = 'research_only_noncanonical'
        promotion_prohibited = $true
    } | ForEach-Object {
        [System.IO.File]::WriteAllText(
            $receiptPath,
            ($_ | ConvertTo-Json -Depth 4),
            [System.Text.UTF8Encoding]::new($false)
        )
    }
    exit 1
}
