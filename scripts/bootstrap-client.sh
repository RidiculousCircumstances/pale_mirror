#!/usr/bin/env bash
# Fetch and execute the current checksum-pinned Far Frontier client installer.
set -euo pipefail

# The artifact host is announced over mDNS by the development machine.  A
# hostname, rather than the machine's DHCP address, lets the small updater keep
# working after an ordinary LAN address change.
default_source_base='http://rd-EliteMini-Series.local:8092'
source_base=${FAR_FRONTIER_SOURCE_URL:-$default_source_base}
forward=()
verify_only=false

while (($#)); do
  case "$1" in
    --source-base)
      source_base=${2:?--source-base requires a URL}
      shift 2
      ;;
    --verify-only)
      verify_only=true
      shift
      ;;
    *)
      forward+=("$1")
      shift
      ;;
  esac
done

source_base=${source_base%/}
[[ "$source_base" =~ ^https?:// ]] || { echo "Source base must use HTTP or HTTPS" >&2; exit 2; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }

work_dir=$(mktemp -d "${TMPDIR:-/tmp}/far-frontier-bootstrap.XXXXXX")
trap 'rm -rf -- "$work_dir"' EXIT
mkdir -p "$work_dir/scripts/lib"

download() {
  curl --fail --silent --show-error --location --retry 3 \
    --output "$work_dir/$1" "$source_base/$1"
}

sha256_file() {
  if command -v sha256sum >/dev/null; then sha256sum "$1" | awk '{print tolower($1)}'
  elif command -v shasum >/dev/null; then shasum -a 256 "$1" | awk '{print tolower($1)}'
  else echo "sha256sum or shasum is required" >&2; return 1
  fi
}

index_hash_from_pack() {
  awk '
    /^\[index\]$/ { active=1; next }
    /^\[/ && active { exit }
    active && $1 == "hash" { value=$3; gsub(/"/, "", value); print tolower(value); exit }
  ' "$1"
}

file_hash_from_index() {
  local wanted=$1
  awk -v wanted="$wanted" '
    /^\[\[files\]\]$/ { path=""; hash=""; next }
    $1 == "file" { path=$3; gsub(/"/, "", path); next }
    $1 == "hash" { hash=$3; gsub(/"/, "", hash); if (path == wanted) { print tolower(hash); exit } }
  ' "$work_dir/index.toml"
}

download pack.toml
download index.toml
expected_index=$(index_hash_from_pack "$work_dir/pack.toml")
[[ "$expected_index" =~ ^[[:xdigit:]]{64}$ ]] || { echo "Hosted pack has no valid index pin" >&2; exit 1; }
[[ "$(sha256_file "$work_dir/index.toml")" == "$expected_index" ]] || { echo "Hosted index checksum mismatch" >&2; exit 1; }

installer_files=(
  scripts/install-client.sh
  scripts/lib/install-hosted-pale-mirror.sh
  scripts/lib/install-hosted-railway-untold.sh
)
for file in "${installer_files[@]}"; do
  download "$file"
  expected=$(file_hash_from_index "$file")
  [[ "$expected" =~ ^[[:xdigit:]]{64}$ ]] || { echo "No index pin for $file" >&2; exit 1; }
  [[ "$(sha256_file "$work_dir/$file")" == "$expected" ]] || { echo "Checksum mismatch for $file" >&2; exit 1; }
done

hosted_sha512() {
  local path=$1 value
  value=$(curl --fail --silent --show-error --location --retry 3 "$source_base/$path")
  value=${value%%[[:space:]]*}
  value=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
  [[ "$value" =~ ^[[:xdigit:]]{128}$ ]] || { echo "Invalid hosted SHA-512 at $path" >&2; return 1; }
  printf '%s' "$value"
}

has_argument() {
  local wanted=$1 value
  for value in "${forward[@]}"; do [[ "$value" == "$wanted" ]] && return 0; done
  return 1
}

if ! has_argument --pack-url; then forward+=(--pack-url "$source_base/pack.toml"); fi
if ! has_argument --target; then
  launcher_source=${BASH_SOURCE[0]:-bash}
  if [[ "$launcher_source" == /dev/fd/* || "$launcher_source" == /proc/* || "$launcher_source" == bash ]]; then
    target_dir=$PWD
  else
    target_dir=$(cd "$(dirname "$launcher_source")" && pwd)
  fi
  forward+=(--target "$target_dir")
fi
pm_sha=$(hosted_sha512 hosted/pale_mirror-current.jar.sha512)
visuals_sha=$(hosted_sha512 hosted/pale_mirror_visuals-current.jar.sha512)
railway_sha=$(hosted_sha512 hosted/railwaysuntold-pm-current.jar.sha512)
if ! has_argument --pale-mirror-url && ! has_argument --pale-mirror-sha512; then
  forward+=(--pale-mirror-url "$source_base/hosted/pale_mirror-current.jar"
    --pale-mirror-sha512 "$pm_sha")
fi
if ! has_argument --pale-mirror-visuals-url && ! has_argument --pale-mirror-visuals-sha512; then
  forward+=(--pale-mirror-visuals-url "$source_base/hosted/pale_mirror_visuals-current.jar"
    --pale-mirror-visuals-sha512 "$visuals_sha")
fi
if ! has_argument --railway-untold-url && ! has_argument --railway-untold-sha512; then
  forward+=(--railway-untold-url "$source_base/hosted/railwaysuntold-pm-current.jar"
    --railway-untold-sha512 "$railway_sha")
fi

if "$verify_only"; then
  curl --fail --silent --show-error --location --retry 3 --output "$work_dir/pale_mirror.jar" \
    "$source_base/hosted/pale_mirror-current.jar"
  curl --fail --silent --show-error --location --retry 3 --output "$work_dir/railwaysuntold.jar" \
    "$source_base/hosted/railwaysuntold-pm-current.jar"
  curl --fail --silent --show-error --location --retry 3 --output "$work_dir/pale_mirror_visuals.jar" \
    "$source_base/hosted/pale_mirror_visuals-current.jar"
  if command -v sha512sum >/dev/null; then
    [[ "$(sha512sum "$work_dir/pale_mirror.jar" | awk '{print tolower($1)}')" == "$pm_sha" ]]
    [[ "$(sha512sum "$work_dir/railwaysuntold.jar" | awk '{print tolower($1)}')" == "$railway_sha" ]]
    [[ "$(sha512sum "$work_dir/pale_mirror_visuals.jar" | awk '{print tolower($1)}')" == "$visuals_sha" ]]
  else
    [[ "$(shasum -a 512 "$work_dir/pale_mirror.jar" | awk '{print tolower($1)}')" == "$pm_sha" ]]
    [[ "$(shasum -a 512 "$work_dir/railwaysuntold.jar" | awk '{print tolower($1)}')" == "$railway_sha" ]]
    [[ "$(shasum -a 512 "$work_dir/pale_mirror_visuals.jar" | awk '{print tolower($1)}')" == "$visuals_sha" ]]
  fi
  printf 'Far Frontier bootstrap, installer pins, and hosted artifacts verified.\n'
  exit 0
fi

bash "$work_dir/scripts/install-client.sh" "${forward[@]}"
