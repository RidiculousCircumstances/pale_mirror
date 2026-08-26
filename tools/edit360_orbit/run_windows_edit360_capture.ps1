[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RunManifest,
    [Parameter(Mandatory = $true)][string]$SourceRoot,
    [Parameter(Mandatory = $true)][string]$Python,
    [Parameter(Mandatory = $true)][string]$Checkpoint,
    [Parameter(Mandatory = $true)][string]$EnvironmentLock,
    [Parameter(Mandatory = $true)][string]$CheckpointReceipt,
    [Parameter(Mandatory = $true)][string]$InputVisualReview,
    [ValidateSet('upstream_fp32_50', 'bounded_fp16_20')][string]$ExecutionProfile = 'bounded_fp16_20',
    [ValidateRange(1, 14)][int]$DecodingT = 1
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Require-FreshFile([string]$Path, [string]$Label) { if (Test-Path -LiteralPath $Path) { throw "Refusing to overwrite ${Label}: $Path" } }
function Resolve-RunManifest([string]$RepositoryRoot, [string]$RawPath) {
    $buildRoot = [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'build\automodel')).TrimEnd([char]92, [char]47)
    $full = if ([System.IO.Path]::IsPathRooted($RawPath)) {
        [System.IO.Path]::GetFullPath($RawPath)
    } else {
        [System.IO.Path]::GetFullPath((Join-Path $RepositoryRoot $RawPath))
    }
    if (-not $full.StartsWith($buildRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) { throw 'Capture run manifest must remain below build/automodel.' }
    return $full
}
$scriptRoot = Split-Path -Parent $PSCommandPath
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptRoot '..\..'))
$manifestPath = Resolve-RunManifest $repositoryRoot $RunManifest
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) { throw 'Capture run manifest is missing.' }
$runDirectory = Split-Path -Parent $manifestPath
$stdoutPath = Join-Path $runDirectory 'edit360_inference.stdout.log'
$stderrPath = Join-Path $runDirectory 'edit360_inference.stderr.log'
$receiptPath = Join-Path $runDirectory 'execution_capture_receipt.json'
Require-FreshFile $stdoutPath 'Edit360 stdout capture'
Require-FreshFile $stderrPath 'Edit360 stderr capture'
Require-FreshFile $receiptPath 'Edit360 capture receipt'
$startedUtc = [DateTime]::UtcNow.ToString('o')
try {
    & (Join-Path $scriptRoot 'run_windows_edit360.ps1') -Phase Infer -RunManifest $manifestPath -SourceRoot $SourceRoot -Python $Python -Checkpoint $Checkpoint -EnvironmentLock $EnvironmentLock -CheckpointReceipt $CheckpointReceipt -InputVisualReview $InputVisualReview -ExecutionProfile $ExecutionProfile -DecodingT $DecodingT 1> $stdoutPath 2> $stderrPath
    if (-not $?) { throw 'Edit360 runner returned a failed PowerShell status.' }
    $outcome = 'success'
} catch {
    $_ | Out-String | Add-Content -Encoding utf8 -LiteralPath $stderrPath
    $outcome = 'failure'
    $errorText = ($_ | Out-String).Trim()
}
$receipt = [ordered]@{
    schema = 'pale_mirror.automodel.edit360_capture_receipt.v1'; started_utc = $startedUtc; finished_utc = [DateTime]::UtcNow.ToString('o'); outcome = $outcome
    stdout = 'edit360_inference.stdout.log'; stderr = 'edit360_inference.stderr.log'; execution_profile = $ExecutionProfile; decoding_t = $DecodingT
    scope = 'research_only_noncanonical'; promotion_prohibited = $true
}
if ($outcome -eq 'failure') { $receipt.error = $errorText }
[System.IO.File]::WriteAllText($receiptPath, ($receipt | ConvertTo-Json -Depth 4), [System.Text.UTF8Encoding]::new($false))
if ($outcome -eq 'failure') { exit 1 }
