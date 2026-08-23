<#
Install the isolated private Windows environment for the SF3D volume bake-off.

This script is intentionally limited to dependency setup and diagnostics.  It
does not download a gated model, invoke inference, contact Blender, or modify
any Pale Mirror asset.  Its paths remain on E: so the game installation and C:
system volume stay untouched.
#>

[CmdletBinding()]
param(
    [ValidateSet('Torch', 'Requirements', 'Smoke')]
    [string]$Phase = 'Torch',
    [string]$Root = 'E:\PaleMirror'
)

$ErrorActionPreference = 'Stop'
$uv = Get-ChildItem -LiteralPath "$Root\vendor\uv-0.12.5" -Filter uv.exe -Recurse |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $uv) {
    throw "Verified uv 0.12.5 is missing below $Root\vendor"
}
$repository = "$Root\vendor\models\stable-fast-3d"
$python = "$Root\workspace\inference\sf3d\.venv\Scripts\python.exe"
if (-not (Test-Path -LiteralPath $repository)) {
    throw "The pinned SF3D repository is missing: $repository"
}
if (-not (Test-Path -LiteralPath $python)) {
    throw "The isolated SF3D venv is missing: $python"
}
$env:UV_PYTHON_INSTALL_DIR = "$Root\vendor\uv-python"
$env:UV_CACHE_DIR = "$Root\vendor\uv-cache"
$environmentDirectory = Split-Path -Parent $python
$provenancePath = "$Root\workspace\inference\sf3d\environment.json"
$compatibilityPatch = "$Root\workspace\pale-mirror\tools\sf3d\patches\torch_2_3_amp_compat.patch"
$networkModule = "$repository\sf3d\models\network.py"

function Invoke-Uv {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    & $uv @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "uv failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Write-Provenance {
    $commit = (& 'E:\Programs\Git\cmd\git.exe' -C $repository rev-parse HEAD).Trim()
    $freeze = (& $uv pip freeze --python $python | Sort-Object)
    $torch = & $python -c "import torch; print(torch.__version__); print(torch.version.cuda); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'no CUDA')"
    $record = [ordered]@{
        schema = 'pale_mirror.sf3d_environment.v1'
        generated_utc = (Get-Date).ToUniversalTime().ToString('o')
        sf3d_commit = $commit
        python = (& $python --version).Trim()
        torch = @($torch)
        compatibility_patch = [ordered]@{
            file = 'tools/sf3d/patches/torch_2_3_amp_compat.patch'
            sha256 = $(if (Test-Path -LiteralPath $compatibilityPatch) { (Get-FileHash -LiteralPath $compatibilityPatch -Algorithm SHA256).Hash.ToLowerInvariant() } else { $null })
            applied = $(if (Test-Path -LiteralPath $networkModule) { (Get-Content -LiteralPath $networkModule -Raw).Contains('from torch.cuda.amp import custom_bwd') } else { $false })
        }
        packages = @($freeze)
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $provenancePath) | Out-Null
    $record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $provenancePath -Encoding utf8
}

function Apply-Torch23CompatibilityPatch {
    if (-not (Test-Path -LiteralPath $compatibilityPatch)) {
        throw "Required SF3D Torch 2.3 compatibility patch is missing: $compatibilityPatch"
    }
    $commit = (& 'E:\Programs\Git\cmd\git.exe' -C $repository rev-parse HEAD).Trim()
    if ($commit -ne 'ff21fc491b4dc5314bf6734c7c0dabd86b5f5bb2') {
        throw "SF3D checkout changed from the pinned commit; refusing to apply a compatibility patch."
    }
    $network = Get-Content -LiteralPath $networkModule -Raw
    if ($network.Contains('from torch.cuda.amp import custom_bwd')) {
        return
    }
    if (-not $network.Contains('from torch.amp import custom_bwd, custom_fwd')) {
        throw 'SF3D network module has an unexpected local modification; refusing to apply the compatibility patch.'
    }
    & 'E:\Programs\Git\cmd\git.exe' -C $repository apply --check $compatibilityPatch *> $null
    if ($LASTEXITCODE -ne 0) {
        throw 'Pinned SF3D Torch 2.3 compatibility patch does not apply to the expected checkout.'
    }
    & 'E:\Programs\Git\cmd\git.exe' -C $repository apply $compatibilityPatch
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not apply the pinned SF3D Torch 2.3 compatibility patch.'
    }
}

switch ($Phase) {
    'Torch' {
        Invoke-Uv @(
            'pip', 'install', '--python', $python, '--reinstall',
            '--index-url', 'https://download.pytorch.org/whl/cu121',
            'torch==2.3.1+cu121',
            'torchvision==0.18.1+cu121'
        )
        Write-Provenance
    }
    'Requirements' {
        Invoke-Uv @('pip', 'install', '--python', $python, 'setuptools==69.5.1', 'wheel')
        Apply-Torch23CompatibilityPatch
        # SF3D's two local packages are declared as ./texture_baker and
        # ./uv_unwrapper.  uv resolves those relative to the working directory,
        # so make the pinned checkout explicit rather than the SSH user's home.
        Push-Location $repository
        try {
            # texture_baker's setup imports the already pinned torch package;
            # its isolated build environment cannot see that dependency.
            Invoke-Uv @(
                'pip', 'install', '--python', $python, '--no-build-isolation',
                '--reinstall-package', 'texture_baker',
                '--reinstall-package', 'uv_unwrapper',
                '--requirements', "$repository\requirements.txt"
            )
        }
        finally {
            Pop-Location
        }
        Write-Provenance
    }
    'Smoke' {
        Apply-Torch23CompatibilityPatch
        # SF3D is an application checkout rather than an installed wheel; its
        # package is intentionally resolved from the pinned repository root.
        Push-Location $repository
        try {
            & $python -c "import torch; assert torch.cuda.is_available(), 'CUDA is unavailable'; import sf3d; print({'torch': torch.__version__, 'cuda': torch.version.cuda, 'device': torch.cuda.get_device_name(0), 'sf3d': sf3d.__file__})"
            if ($LASTEXITCODE -ne 0) {
                throw "SF3D CUDA import smoke failed with exit code $LASTEXITCODE"
            }
        }
        finally {
            Pop-Location
        }
        Write-Provenance
    }
}
