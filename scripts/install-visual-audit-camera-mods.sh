#!/usr/bin/env bash
# Install hash-pinned, client-only camera/capture tools into the dev audit run.
set -euo pipefail

repo_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)
run_root="$repo_dir/pale-mirror-neoforge/build/runs"
target="$run_root/railway-client"

usage() {
  cat <<'EOF'
Usage: scripts/install-visual-audit-camera-mods.sh [--target CLIENT-RUN]

Installs the pinned Freecam and Power Screenshot JARs only into a Pale Mirror
development run directory. It does not edit Packwiz metadata, a server, or a
normal player client.
EOF
}

while (($#)); do
  case "$1" in
    --target) target=${2:?--target requires a client run directory}; shift 2 ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -d "$target/mods" ]] || { printf 'Client run has no mods directory: %s\n' "$target" >&2; exit 2; }
target=$(cd "$target" && pwd -P)
case "$target" in
  "$run_root"/*) ;;
  *) printf 'Refusing non-development client run: %s\n' "$target" >&2; exit 2 ;;
esac
mods_dir="$target/mods"

if command -v sha512sum >/dev/null; then
  sha512() { sha512sum "$1" | awk '{print $1}'; }
elif command -v shasum >/dev/null; then
  sha512() { shasum -a 512 "$1" | awk '{print $1}'; }
else
  printf 'sha512sum or shasum is required.\n' >&2
  exit 1
fi
command -v curl >/dev/null || { printf 'curl is required.\n' >&2; exit 1; }

install_pinned() {
  local filename=$1 url=$2 expected_sha512=$3 temporary=
  local destination="$mods_dir/$filename"
  if [[ -f "$destination" && "$(sha512 "$destination")" == "$expected_sha512" ]]; then
    printf 'Verified audit tool: %s\n' "$filename"
    return
  fi
  temporary=$(mktemp "$mods_dir/.${filename}.XXXXXX.download")
  trap '[[ -z "${temporary:-}" ]] || rm -f -- "$temporary"' RETURN
  curl --fail --location --retry 3 --output "$temporary" "$url"
  [[ "$(sha512 "$temporary")" == "$expected_sha512" ]] || {
    printf 'SHA-512 mismatch for %s\n' "$filename" >&2
    exit 1
  }
  mv -f -- "$temporary" "$destination"
  temporary=
  trap - RETURN
  printf 'Installed hash-pinned audit tool: %s\n' "$filename"
}

install_pinned \
  'freecam-neoforge-1.3.0+mc1.21.jar' \
  'https://cdn.modrinth.com/data/XeEZ3fK2/versions/ROfcbxxe/freecam-neoforge-1.3.0%2Bmc1.21.jar' \
  '06cfa2acdde8320ca19edf3f49e7a3860cc079bbad02fda493459109d745da237e19ff6a28d879d4c863ea4cd24d83b193ec82dd351540ad27991e239f1e7a72'
install_pinned \
  'PowerScreenshot-Minecraft1.21.1-NeoForge-1.0.2.jar' \
  'https://cdn.modrinth.com/data/hmJnmIp3/versions/pnYFkkbB/PowerScreenshot-Minecraft1.21.1-NeoForge-1.0.2.jar' \
  'eae1213ce29f7212646d4cbdb2c0f8aff270bc2fbd043ba9241c6949fa299e30a3725e3b589d801bf73d211294f2a01eb0db6d954def5def3f61d328264a09d1'
