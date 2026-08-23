<# Run the pinned four-view Hunyuan3D-2mv Collector shape-only proposal. #>

[CmdletBinding()]
param(
    [ValidateSet('Prepare', 'Validate', 'Inference')]
    [string]$Phase = 'Validate',
    [string]$Root = 'E:\PaleMirror',
    [string]$BakeoffId = 'collector_multiview_bakeoff_v01',
    [string]$CandidateId = 'hunyuan2mv_v01'
)

$ErrorActionPreference = 'Stop'
if ($BakeoffId -ne 'collector_multiview_bakeoff_v01' -or $CandidateId -ne 'hunyuan2mv_v01') {
    throw 'Only the pinned Collector Hunyuan2mv v01 candidate is allowed.'
}
$repository = "$Root\vendor\models\Hunyuan3D-2"
$python = "$Root\workspace\inference\hunyuan3d-2mv\.venv\Scripts\python.exe"
$workspace = "$Root\workspace\pale-mirror"
$candidateDirectory = "$workspace\candidates\biomass_collector\$CandidateId"
$inputDirectory = "$candidateDirectory\input"
$checkpointRoot = "$Root\workspace\inference\hunyuan3d-2mv\models"
$inputManifest = "$inputDirectory\manifest.json"
$candidate = "$candidateDirectory\candidate.glb"
$provenance = "$candidateDirectory\manifest.json"
$prepare = "$workspace\tools\hunyuan3d_2mv\prepare_collector_multiview.py"
$runner = "$workspace\tools\hunyuan3d_2mv\run_collector_multiview.py"
$primary = "$workspace\references\01_harvester_biomass_collector.jpg"
$turntable = "$workspace\references\secondary\biomass_collector_turntable_v01.png"

if ((-not (Test-Path -LiteralPath $repository)) -or (-not (Test-Path -LiteralPath $python))) {
    throw 'Pinned Hunyuan3D-2mv runtime is unavailable; install its isolated environment first.'
}
# The default Xet transport over-saturates this private host's link and can
# reduce itself to a single stalled stream.  The ordinary authenticated Hub
# downloader resumes weights normally and keeps this fixed trial reproducible.
$env:HF_HUB_DISABLE_XET = '1'
if ($Phase -eq 'Prepare') {
    if ((Test-Path -LiteralPath $candidate) -or (Test-Path -LiteralPath $provenance)) {
        throw 'Refusing to replace an existing Hunyuan2mv candidate.'
    }
    & $python $prepare --primary $primary --turntable $turntable --output $inputDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the pinned Hunyuan2mv four-view inputs.' }
    exit 0
}
if (-not (Test-Path -LiteralPath $inputManifest)) { throw 'Run -Phase Prepare before Hunyuan2mv validation or inference.' }
if ($Phase -eq 'Validate') {
    & $python $runner --input-manifest $inputManifest --candidate $candidate --model-repository $repository --checkpoint-root $checkpointRoot --help | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Hunyuan2mv runner contract is unavailable.' }
    Get-FileHash -Algorithm SHA256 -LiteralPath $inputManifest | Select-Object -ExpandProperty Hash
    exit 0
}
if ((Test-Path -LiteralPath $candidate) -or (Test-Path -LiteralPath $provenance)) {
    throw 'Refusing to replace an existing Hunyuan2mv inference result.'
}
& $python $runner --input-manifest $inputManifest --candidate $candidate --model-repository $repository --checkpoint-root $checkpointRoot
if ($LASTEXITCODE -ne 0) { throw "Hunyuan2mv inference failed with exit code $LASTEXITCODE." }
