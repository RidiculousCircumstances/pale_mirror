#!/usr/bin/env bash
# Install an isolated official Hunyuan3D-2mv CPU runtime for the fixed
# Collector proposal. It creates no canonical asset and never contacts Blender.
set -euo pipefail

phase="${1:-Validate}"
root="${2:-$PWD/build/inference/hunyuan3d-2mv-cpu}"
repository_commit="f8db63096c8282cb27354314d896feba5ba6ff8a"
repository="$root/vendor/Hunyuan3D-2"
environment="$root/.venv"
python="$environment/bin/python"

require_repository() {
  if [[ ! -d "$repository/.git" ]]; then
    mkdir -p "$(dirname "$repository")"
    git clone --no-checkout https://github.com/Tencent-Hunyuan/Hunyuan3D-2.git "$repository"
    git -C "$repository" checkout --detach "$repository_commit"
  fi
  actual="$(git -C "$repository" rev-parse HEAD)"
  [[ "$actual" == "$repository_commit" ]] || {
    echo "Hunyuan3D-2 checkout must remain pinned to $repository_commit; found $actual." >&2
    exit 1
  }
}

case "$phase" in
  Bootstrap)
    require_repository
    if [[ ! -x "$python" ]]; then
      python3.11 -m venv "$environment"
    fi
    "$python" --version
    ;;
  Requirements)
    require_repository
    [[ -x "$python" ]] || { echo 'Run Bootstrap before Requirements.' >&2; exit 1; }
    "$python" -m pip install --upgrade pip setuptools wheel
    "$python" -m pip install torch==2.5.1 torchvision==0.20.1 torchaudio==2.5.1 --index-url https://download.pytorch.org/whl/cpu
    "$python" -m pip install -r "$repository/requirements.txt"
    "$python" -m pip install -e "$repository"
    ;;
  Validate)
    require_repository
    [[ -x "$python" ]] || { echo 'The isolated CPU environment is missing.' >&2; exit 1; }
    "$python" -c "import json, torch, hy3dgen; assert not torch.cuda.is_available(); print(json.dumps({'python': __import__('sys').version.split()[0], 'torch': torch.__version__, 'device': 'cpu'}))"
    ;;
  *)
    echo 'Usage: install_hunyuan2mv_cpu.sh {Bootstrap|Requirements|Validate} [root]' >&2
    exit 64
    ;;
esac
