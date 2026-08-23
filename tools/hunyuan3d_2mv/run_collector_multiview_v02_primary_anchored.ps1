<# Run only the primary-anchored Collector Hunyuan2mv v02 diagnostic proposal. #>

[CmdletBinding()]
param(
    [ValidateSet('Prepare', 'Validate', 'Inference')]
    [string]$Phase = 'Validate',
    [string]$Root = 'E:\PaleMirror'
)

$ErrorActionPreference = 'Stop'
$repository = "$Root\vendor\models\Hunyuan3D-2"
$python = "$Root\workspace\inference\hunyuan3d-2mv\.venv\Scripts\python.exe"
$workspace = "$Root\workspace\pale-mirror"
$candidateId = 'hunyuan2mv_v02_primary_anchored'
$candidateDirectory = "$workspace\candidates\biomass_collector\$candidateId"
$inputDirectory = "$candidateDirectory\input"
$checkpointRoot = "$Root\workspace\inference\hunyuan3d-2mv\models"
$inputManifest = "$inputDirectory\manifest.json"
$candidate = "$candidateDirectory\candidate.glb"
$provenance = "$candidateDirectory\manifest.json"
$prepare = "$workspace\tools\hunyuan3d_2mv\prepare_collector_multiview_v02_primary_anchored.py"
$runner = "$workspace\tools\hunyuan3d_2mv\run_collector_multiview.py"
$primary = "$workspace\references\01_harvester_biomass_collector.jpg"
$generatedRoot = "$workspace\references\secondary"
$trace = "$workspace\traces\biomass_collector_primary_v03.json"

if ((-not (Test-Path -LiteralPath $repository)) -or (-not (Test-Path -LiteralPath $python))) {
    throw 'Pinned Hunyuan3D-2mv runtime is unavailable; install its isolated environment first.'
}
$env:HF_HUB_DISABLE_XET = '1'
if ($Phase -eq 'Prepare') {
    if ((Test-Path -LiteralPath $candidate) -or (Test-Path -LiteralPath $provenance)) {
        throw 'Refusing to replace an existing primary-anchored Hunyuan2mv candidate.'
    }
    & $python $prepare --primary $primary --generated-root $generatedRoot --trace $trace --output $inputDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the primary-anchored Hunyuan2mv inputs.' }
    exit 0
}
if (-not (Test-Path -LiteralPath $inputManifest)) { throw 'Run -Phase Prepare before validation or inference.' }
if ($Phase -eq 'Validate') {
    & $python $runner --input-manifest $inputManifest --candidate $candidate --model-repository $repository --checkpoint-root $checkpointRoot --help | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Primary-anchored Hunyuan2mv runner contract is unavailable.' }
    Get-FileHash -Algorithm SHA256 -LiteralPath $inputManifest | Select-Object -ExpandProperty Hash
    exit 0
}
if ((Test-Path -LiteralPath $candidate) -or (Test-Path -LiteralPath $provenance)) {
    throw 'Refusing to replace an existing primary-anchored Hunyuan2mv inference result.'
}
& $python $runner --input-manifest $inputManifest --candidate $candidate --model-repository $repository --checkpoint-root $checkpointRoot
if ($LASTEXITCODE -ne 0) { throw "Primary-anchored Hunyuan2mv inference failed with exit code $LASTEXITCODE." }
