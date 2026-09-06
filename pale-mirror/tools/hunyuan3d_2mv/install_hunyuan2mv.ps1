<#
Install the isolated official Hunyuan3D-2mv shape runtime on the private
Windows authoring host.  It neither opens Blender nor writes gameplay assets.
#>

[CmdletBinding()]
param(
    [ValidateSet('Bootstrap', 'Requirements', 'Validate')]
    [string]$Phase = 'Validate',
    [string]$Root = 'E:\PaleMirror'
)

$ErrorActionPreference = 'Stop'
$RepositoryCommit = 'f8db63096c8282cb27354314d896feba5ba6ff8a'
$repository = "$Root\vendor\models\Hunyuan3D-2"
$environment = "$Root\workspace\inference\hunyuan3d-2mv\.venv"
$python = "$environment\Scripts\python.exe"
$bootstrapPython = "$Root\workspace\inference\sf3d\.venv\Scripts\python.exe"
$provenance = "$Root\workspace\inference\hunyuan3d-2mv\environment.json"

function Require-Repository {
    if (-not (Test-Path -LiteralPath $repository)) {
        & 'E:\Programs\Git\cmd\git.exe' clone --no-checkout https://github.com/Tencent-Hunyuan/Hunyuan3D-2.git $repository
        if ($LASTEXITCODE -ne 0) { throw "Could not clone official Hunyuan3D-2 into $repository." }
        & 'E:\Programs\Git\cmd\git.exe' -C $repository checkout --detach $RepositoryCommit
        if ($LASTEXITCODE -ne 0) { throw 'Could not check out the pinned Hunyuan3D-2 revision.' }
    }
    $actual = (& 'E:\Programs\Git\cmd\git.exe' -C $repository rev-parse HEAD).Trim()
    if ($actual -ne $RepositoryCommit) { throw "Hunyuan3D-2 checkout must remain pinned to $RepositoryCommit; found $actual." }
}

if ($Phase -eq 'Bootstrap') {
    Require-Repository
    if (-not (Test-Path -LiteralPath $python)) {
        if (-not (Test-Path -LiteralPath $bootstrapPython)) {
            throw 'The verified private Python bootstrap is unavailable; install Python 3.10 or later before Hunyuan3D-2mv.'
        }
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $environment) | Out-Null
        & $bootstrapPython -m venv $environment
        if ($LASTEXITCODE -ne 0) { throw 'Could not create the isolated Hunyuan3D-2mv environment.' }
    }
    & $python --version
    exit 0
}

if ($Phase -eq 'Requirements') {
    Require-Repository
    if (-not (Test-Path -LiteralPath $python)) { throw 'Run -Phase Bootstrap before installing Hunyuan3D-2mv requirements.' }
    & $python -m pip install --upgrade pip setuptools wheel
    if ($LASTEXITCODE -ne 0) { throw 'Could not upgrade isolated Hunyuan3D-2mv packaging tools.' }
    & $python -m pip install torch==2.5.1 torchvision==0.20.1 torchaudio==2.5.1 --index-url https://download.pytorch.org/whl/cu124
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the pinned Hunyuan3D-2mv CUDA PyTorch wheel set.' }
    Push-Location $repository
    try {
        & $python -m pip install -r requirements.txt
        if ($LASTEXITCODE -ne 0) { throw 'Could not install Hunyuan3D-2mv requirements.' }
        & $python -m pip install -e .
        if ($LASTEXITCODE -ne 0) { throw 'Could not install the pinned Hunyuan3D-2 package.' }
    }
    finally { Pop-Location }
    exit 0
}

Require-Repository
if (-not (Test-Path -LiteralPath $python)) { throw 'Hunyuan3D-2mv isolated environment is missing.' }
$smoke = & $python -c "import json, torch; import hy3dgen; assert torch.cuda.is_available(), 'CUDA is unavailable'; print(json.dumps({'python': __import__('sys').version.split()[0], 'torch': torch.__version__, 'cuda': torch.version.cuda, 'device': torch.cuda.get_device_name(0)}))"
if ($LASTEXITCODE -ne 0) { throw 'Hunyuan3D-2mv shape-only CUDA import smoke failed.' }
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $provenance) | Out-Null
[ordered]@{
    schema = 'pale_mirror.hunyuan2mv_environment.v1'
    repository = 'https://github.com/Tencent-Hunyuan/Hunyuan3D-2'
    repository_commit = $RepositoryCommit
    python = (& $python --version).Trim()
    smoke = $smoke | Select-Object -Last 1
    shape_only = $true
    low_vram_cpu_offload_required = $true
} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $provenance -Encoding utf8
Get-Content -LiteralPath $provenance -Raw
