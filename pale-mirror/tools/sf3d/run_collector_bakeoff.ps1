<#
Run exactly the named, trace-cut SF3D Collector volume proposal on Windows.

The output is intentionally a private, non-canonical staging candidate inside
the controlled Blender workspace.  This script neither invokes Blender nor
exports PMMesh.  It fails instead of replacing an existing candidate so every
model invocation has its own labelled provenance.
#>

[CmdletBinding()]
param(
    [ValidateSet('Validate', 'Inference')]
    [string]$Phase = 'Validate',
    [string]$Root = 'E:\PaleMirror',
    [string]$BakeoffId = 'collector_volume_bakeoff_v01',
    [string]$CandidateId = 'sf3d_v01'
)

$ErrorActionPreference = 'Stop'
if ($BakeoffId -ne 'collector_volume_bakeoff_v01' -or $CandidateId -ne 'sf3d_v01') {
    throw 'Only the pinned Collector SF3D v01 bake-off candidate is allowed.'
}
$repository = "$Root\vendor\models\stable-fast-3d"
$python = "$Root\workspace\inference\sf3d\.venv\Scripts\python.exe"
$workspace = "$Root\workspace\pale-mirror"
$candidateDirectory = "$workspace\candidates\biomass_collector\$CandidateId"
$inputDirectory = "$candidateDirectory\input"
$inputImage = "$inputDirectory\collector_primary_input.png"
$inputManifest = "$inputDirectory\manifest.json"
$candidate = "$candidateDirectory\candidate.glb"
$provenance = "$candidateDirectory\manifest.json"
if ((-not (Test-Path -LiteralPath $repository)) -or (-not (Test-Path -LiteralPath $python))) {
    throw 'Pinned SF3D source or its verified CUDA environment is unavailable.'
}
if ((-not (Test-Path -LiteralPath $inputImage)) -or (-not (Test-Path -LiteralPath $inputManifest))) {
    throw 'Trace-cut Collector input is missing from the controlled candidate workspace.'
}
$input = Get-Content -LiteralPath $inputManifest -Raw | ConvertFrom-Json
if ($input.schema -ne 'pale_mirror_visuals.harvester_volume_bakeoff_input.v1' -or
    $input.bakeoff_id -ne $BakeoffId -or $input.asset_id -ne 'biomass_collector') {
    throw 'Collector bake-off input manifest is not the pinned trace-authoritative contract.'
}
$actualInputHash = (Get-FileHash -LiteralPath $inputImage -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actualInputHash -ne $input.outputs.rgba_input.sha256) {
    throw 'Trace-cut RGBA input differs from its pinned manifest hash.'
}
if ($Phase -eq 'Validate') {
    [PSCustomObject]@{
        bakeoff_id = $BakeoffId
        candidate_id = $CandidateId
        input_sha256 = $actualInputHash
        source_trace_sha256 = $input.source.trace.sha256
        model_commit = (& 'E:\Programs\Git\cmd\git.exe' -C $repository rev-parse HEAD).Trim()
        output_exists = Test-Path -LiteralPath $candidate
    } | ConvertTo-Json
    exit 0
}
if ((Test-Path -LiteralPath $candidate) -or (Test-Path -LiteralPath $provenance)) {
    throw "Candidate $CandidateId already exists; use a new versioned candidate id rather than overwrite evidence."
}
New-Item -ItemType Directory -Force -Path $candidateDirectory | Out-Null
$modelOutput = "$candidateDirectory\model-output"
New-Item -ItemType Directory -Force -Path $modelOutput | Out-Null
$env:HF_HOME = "$Root\vendor\hf-home"
$env:HUGGINGFACE_HUB_CACHE = "$Root\vendor\hf-home\hub"
$env:TRANSFORMERS_CACHE = "$Root\vendor\hf-home\transformers"
$env:SF3D_USE_CPU = '0'
$tokenPath = Join-Path $env:USERPROFILE '.cache\huggingface\token'
if (-not (Test-Path -LiteralPath $tokenPath)) {
    throw 'Hugging Face CLI authentication is missing for the private SF3D environment.'
}
$env:HF_TOKEN = (Get-Content -LiteralPath $tokenPath -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($env:HF_TOKEN)) {
    throw 'Hugging Face CLI authentication token is empty for the private SF3D environment.'
}
try {
    Push-Location $repository
    try {
        # `none` preserves the actual volume proposal.  Any animation-ready
        # topology belongs to the later controlled retopology stage.
        & $python run.py $inputImage --output-dir $modelOutput --texture-resolution 1024 --remesh_option none
        if ($LASTEXITCODE -ne 0) {
            throw "SF3D inference failed with exit code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    Remove-Item Env:HF_TOKEN -ErrorAction SilentlyContinue
}
$glb = Get-ChildItem -LiteralPath $modelOutput -Recurse -Filter '*.glb' | Select-Object -First 1 -ExpandProperty FullName
if (-not $glb) {
    throw 'SF3D reported success but did not produce a GLB candidate.'
}
Move-Item -LiteralPath $glb -Destination $candidate
$record = [ordered]@{
    schema = 'pale_mirror.sf3d_volume_candidate.v1'
    bakeoff_id = $BakeoffId
    candidate_id = $CandidateId
    asset_id = 'biomass_collector'
    stage = 'volume-proposal-unaccepted'
    input_manifest_sha256 = (Get-FileHash -LiteralPath $inputManifest -Algorithm SHA256).Hash.ToLowerInvariant()
    input_rgba_sha256 = $actualInputHash
    source_trace_sha256 = $input.source.trace.sha256
    model = [ordered]@{
        repository = 'https://github.com/Stability-AI/stable-fast-3d'
        commit = (& 'E:\Programs\Git\cmd\git.exe' -C $repository rev-parse HEAD).Trim()
        checkpoint = 'stabilityai/stable-fast-3d'
        licence = 'Stability AI Community License Agreement (2024-07-05)'
    }
    runtime = [ordered]@{
        python = (& $python --version).Trim()
        torch = & $python -c "import torch; print(torch.__version__)"
        cuda = & $python -c "import torch; print(torch.version.cuda)"
        device = & $python -c "import torch; print(torch.cuda.get_device_name(0))"
    }
    output = [ordered]@{
        file = 'candidate.glb'
        sha256 = (Get-FileHash -LiteralPath $candidate -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    generated_utc = (Get-Date).ToUniversalTime().ToString('o')
    runtime_export_forbidden = $true
}
$record | ConvertTo-Json -Depth 7 | Set-Content -LiteralPath $provenance -Encoding utf8
$record | ConvertTo-Json -Depth 7
