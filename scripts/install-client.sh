#!/usr/bin/env bash
# Synchronise a pre-created NeoForge 1.21.1-21.1.248 client instance with Far Frontier.
# This script intentionally does not install or configure a launcher.
set -euo pipefail

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=scripts/lib/install-hosted-pale-mirror.sh
source "$script_dir/lib/install-hosted-pale-mirror.sh"
# shellcheck source=scripts/lib/install-hosted-railway-untold.sh
source "$script_dir/lib/install-hosted-railway-untold.sh"

bootstrap_version=0.0.3
bootstrap_sha256=a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c
bootstrap_url="https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v${bootstrap_version}/packwiz-installer-bootstrap.jar"

usage() {
  cat <<'EOF'
Usage: scripts/install-client.sh --target <NeoForge instance directory> --pack-url <URL> [options]

Options:
  --java <path>
  --pale-mirror-url <URL>
  --pale-mirror-sha512 <128 hex characters>
  --pale-mirror-visuals-url <URL>
  --pale-mirror-visuals-sha512 <128 hex characters>
  --railway-untold-url <URL>
  --railway-untold-sha512 <128 hex characters>

The target must be a Minecraft launcher instance already configured for NeoForge
1.21.1-21.1.248. Pack URL must address pack.toml and be reachable from this client.
Re-run the command after pack updates. Pale Mirror URL and SHA-512 must be supplied
together; PALE_MIRROR_URL and PALE_MIRROR_SHA512 provide equivalent defaults.
The Railway Untold pair is likewise available as RAILWAY_UNTOLD_URL and
RAILWAY_UNTOLD_SHA512.
EOF
}

target=""
pack_url=""
java_bin="${JAVA_BIN:-java}"
pale_mirror_url="${PALE_MIRROR_URL:-}"
pale_mirror_sha512="${PALE_MIRROR_SHA512:-}"
pale_mirror_visuals_url="${PALE_MIRROR_VISUALS_URL:-}"
pale_mirror_visuals_sha512="${PALE_MIRROR_VISUALS_SHA512:-}"
railway_untold_url="${RAILWAY_UNTOLD_URL:-}"
railway_untold_sha512="${RAILWAY_UNTOLD_SHA512:-}"
while (($#)); do
  case "$1" in
    --target) target=${2:?--target requires a directory}; shift 2 ;;
    --pack-url) pack_url=${2:?--pack-url requires a URL}; shift 2 ;;
    --java) java_bin=${2:?--java requires a path}; shift 2 ;;
    --pale-mirror-url) pale_mirror_url=${2:?--pale-mirror-url requires a URL}; shift 2 ;;
    --pale-mirror-sha512) pale_mirror_sha512=${2:?--pale-mirror-sha512 requires a hash}; shift 2 ;;
    --pale-mirror-visuals-url) pale_mirror_visuals_url=${2:?--pale-mirror-visuals-url requires a URL}; shift 2 ;;
    --pale-mirror-visuals-sha512) pale_mirror_visuals_sha512=${2:?--pale-mirror-visuals-sha512 requires a hash}; shift 2 ;;
    --railway-untold-url) railway_untold_url=${2:?--railway-untold-url requires a URL}; shift 2 ;;
    --railway-untold-sha512) railway_untold_sha512=${2:?--railway-untold-sha512 requires a hash}; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$target" && -n "$pack_url" ]] || { usage >&2; exit 2; }
validate_hosted_pale_mirror_args "$pale_mirror_url" "$pale_mirror_sha512"
validate_hosted_pale_mirror_visuals_args "$pale_mirror_visuals_url" "$pale_mirror_visuals_sha512"
validate_hosted_railway_args "$railway_untold_url" "$railway_untold_sha512"
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v "$java_bin" >/dev/null || { echo "Java executable not found: $java_bin" >&2; exit 1; }
"$java_bin" -version 2>&1 | grep -Eq '(version|openjdk) "21([."]|$)' || {
  echo "Java 21 is required" >&2; exit 1;
}

if command -v sha256sum >/dev/null; then
  checksum() { printf '%s  %s\n' "$1" "$2" | sha256sum -c -; }
elif command -v shasum >/dev/null; then
  checksum() { [[ "$(shasum -a 256 "$2" | awk '{print $1}')" == "$1" ]]; }
else
  echo "sha256sum or shasum is required" >&2; exit 1
fi

mkdir -p "$target"
bootstrap="$target/packwiz-installer-bootstrap.jar"
if [[ ! -f "$bootstrap" ]] || ! checksum "$bootstrap_sha256" "$bootstrap" >/dev/null 2>&1; then
  curl --fail --location --retry 3 --output "$bootstrap" "$bootstrap_url"
fi
checksum "$bootstrap_sha256" "$bootstrap"

(
  cd "$target"
  "$java_bin" -jar "$bootstrap" -g "$pack_url"
)

# Migrate clients that previously received the optional Caliber artifact. It is
# no longer part of the default manifest and must not remain active accidentally.
shopt -s nullglob
for jar in "$target"/mods/createcaliber*.jar; do
  mv -f -- "$jar" "$jar.disabled"
  echo "Disabled legacy evaluation mod: $(basename "$jar")"
done

# Iris is now pinned with the renderer stack. Packwiz deliberately preserves
# unmanaged local files, so disable older manually installed Iris builds that
# would otherwise load beside the managed version and crash during mixin setup.
managed_iris="iris-neoforge-1.8.14-beta.1+mc1.21.1.jar"
for jar in "$target"/mods/iris*.jar "$target"/mods/Iris*.jar; do
  [[ -f "$jar" ]] || continue
  [[ $(basename "$jar") == "$managed_iris" ]] && continue
  mv -f -- "$jar" "$jar.disabled"
  echo "Disabled incompatible Iris build: $(basename "$jar")"
done

install_hosted_pale_mirror \
  "$target" "$pale_mirror_url" "$pale_mirror_sha512" \
  "$target/.far-frontier-installer-cache"
install_hosted_pale_mirror_visuals \
  "$target" "$pale_mirror_visuals_url" "$pale_mirror_visuals_sha512" \
  "$target/.far-frontier-installer-cache"
install_hosted_railway_untold \
  "$target" "$railway_untold_url" "$railway_untold_sha512" \
  "$target/.far-frontier-installer-cache"

# These were independent client conveniences, not Pale Mirror or renderer
# dependencies. Packwiz preserves no-longer-managed files, so retire them
# explicitly and recoverably when updating an existing instance.
obsolete_client_mods=(
  'journeymap-neoforge-1.21.1-6.0.4.jar'
  'ezactions-neoforge-1.21.1-2.0.3.5.jar'
  'SimplyTooltips-neoforge-0.1.3.jar'
  'entity_model_features-3.2.4-1.21-neoforge.jar'
  'entity_texture_features_1.21-neoforge-7.1.jar'
  'polytone-1.21-3.11.1-neoforge.jar'
)
retired_client_mods="$target/.far-frontier-retired-client-mods"
for filename in "${obsolete_client_mods[@]}"; do
  source="$target/mods/$filename"
  [[ -f "$source" ]] || continue
  mkdir -p "$retired_client_mods"
  destination="$retired_client_mods/$filename.$(date -u +%Y%m%dT%H%M%SZ)"
  mv -- "$source" "$destination"
  echo "Retired nonessential client mod: $destination"
done

echo "Client pack synchronised in: $target"
