#!/usr/bin/env bash
# Build or update a dedicated Far Frontier server from this checkout.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
# shellcheck source=scripts/lib/install-hosted-pale-mirror.sh
source "$repo_root/scripts/lib/install-hosted-pale-mirror.sh"
# shellcheck source=scripts/lib/install-hosted-railway-untold.sh
source "$repo_root/scripts/lib/install-hosted-railway-untold.sh"
neoforge_version=21.1.248
neoforge_sha256=68eeab77059ba53df1812f1afa5bf530ab2566a3cdcd5f924aa6e71be42e410c
bootstrap_version=0.0.3
bootstrap_sha256=a8fbb24dc604278e97f4688e82d3d91a318b98efc08d5dbfcbcbcab6443d116c
neoforge_url="https://maven.neoforged.net/releases/net/neoforged/neoforge/${neoforge_version}/neoforge-${neoforge_version}-installer.jar"
bootstrap_url="https://github.com/packwiz/packwiz-installer-bootstrap/releases/download/v${bootstrap_version}/packwiz-installer-bootstrap.jar"

usage() {
  cat <<'EOF'
Usage: scripts/install-server.sh --target <server directory> [options]

Options:
  --pack-url <URL>
  --java <path>
  --level-name <directory name>
  --enable-c2me
  --accept-eula
  --pale-mirror-url <URL>
  --pale-mirror-sha512 <128 hex characters>
  --pale-mirror-visuals-url <URL>
  --pale-mirror-visuals-sha512 <128 hex characters>
  --railway-untold-url <URL>
  --railway-untold-sha512 <128 hex characters>

Without --pack-url, the script temporarily serves this checkout over loopback while
packwiz materialises the server. For LAN clients, publish this checkout separately
(for example: `packwiz serve --port 8091`) and give its reachable pack.toml URL to
scripts/install-client.sh or scripts/install-client.ps1.

The script never deletes an existing server/world. --accept-eula is required only
to write eula=true; read Mojang's EULA before using it. When the pack URL ends in
`/pack.toml`, Pale Mirror, Pale Mirror Visuals and Railway Untold are resolved from
the same hosted source and SHA-512-pinned automatically. Explicit URL/SHA-512 pairs
still override that default and must be supplied together. The dedicated server
runtime must be Java 22; Pale Mirror and all client artifacts remain compiled for
Java 21. C2ME is downloaded but kept disabled unless --enable-c2me is supplied.
EOF
}

target=""
pack_url=""
java_bin="${JAVA_BIN:-java}"
accept_eula=false
enable_c2me=false
level_name=""
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
    --level-name) level_name=${2:?--level-name requires a directory name}; shift 2 ;;
    --accept-eula) accept_eula=true; shift ;;
    --enable-c2me) enable_c2me=true; shift ;;
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

[[ -n "$target" ]] || { usage >&2; exit 2; }
if [[ -n "$level_name" && ! "$level_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]]; then
  echo "--level-name must be a simple relative directory name: $level_name" >&2
  exit 2
fi
validate_hosted_pale_mirror_args "$pale_mirror_url" "$pale_mirror_sha512"
validate_hosted_pale_mirror_visuals_args "$pale_mirror_visuals_url" "$pale_mirror_visuals_sha512"
validate_hosted_railway_args "$railway_untold_url" "$railway_untold_sha512"
command -v curl >/dev/null || { echo "curl is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required for structure validation" >&2; exit 1; }
command -v "$java_bin" >/dev/null || { echo "Java executable not found: $java_bin" >&2; exit 1; }
"$java_bin" -version 2>&1 | grep -Eq '(version|openjdk) "22([."]|$)' || {
  echo "Java 22 is required for the dedicated server runtime" >&2; exit 1;
}

if command -v sha256sum >/dev/null; then
  checksum() { printf '%s  %s\n' "$1" "$2" | sha256sum -c -; }
elif command -v shasum >/dev/null; then
  checksum() { [[ "$(shasum -a 256 "$2" | awk '{print $1}')" == "$1" ]]; }
else
  echo "sha256sum or shasum is required" >&2; exit 1
fi

mkdir -p "$target"
cache_dir="$target/.far-frontier-installer-cache"
mkdir -p "$cache_dir"
fetch_verified() {
  local url=$1 path=$2 expected=$3
  if [[ ! -f "$path" ]] || ! checksum "$expected" "$path" >/dev/null 2>&1; then
    curl --fail --location --retry 3 --output "$path" "$url"
  fi
  checksum "$expected" "$path"
}

neoforge_installer="$cache_dir/neoforge-${neoforge_version}-installer.jar"
bootstrap="$cache_dir/packwiz-installer-bootstrap-${bootstrap_version}.jar"
fetch_verified "$neoforge_url" "$neoforge_installer" "$neoforge_sha256"
fetch_verified "$bootstrap_url" "$bootstrap" "$bootstrap_sha256"

if [[ ! -x "$target/run.sh" ]]; then
  # NeoForge writes its installer log to the current directory. Keep that evidence
  # inside the target's cache instead of polluting the source checkout.
  (
    cd "$cache_dir"
    "$java_bin" -jar "$neoforge_installer" --installServer "$target"
  )
fi

serve_pid=""
cleanup() {
  [[ -n "$serve_pid" ]] && kill "$serve_pid" 2>/dev/null || true
}
trap cleanup EXIT
if [[ -z "$pack_url" ]]; then
  command -v python3 >/dev/null || {
    echo "python3 is required when --pack-url is omitted" >&2; exit 1;
  }
  # Loopback only: this temporary service is for this server installation, not LAN clients.
  python3 -m http.server 18091 --bind 127.0.0.1 --directory "$repo_root" \
    >"$cache_dir/local-pack-server.log" 2>&1 &
  serve_pid=$!
  pack_url=http://127.0.0.1:18091/pack.toml
  for _ in $(seq 1 30); do
    curl --fail --silent "$pack_url" >/dev/null && break
    sleep 1
  done
  curl --fail --silent "$pack_url" >/dev/null || {
    echo "Temporary local pack server did not start" >&2; exit 1;
  }
fi

hosted_base=${pack_url%/pack.toml}
if [[ "$hosted_base" == "$pack_url" ]]; then
  echo "--pack-url must end in /pack.toml to resolve hosted Pale Mirror dependencies automatically" >&2
  exit 2
fi
hosted_sha512() {
  local artifact=$1 value
  value=$(curl --fail --silent --show-error --location --retry 3 "$hosted_base/hosted/$artifact.sha512")
  value=${value%%[[:space:]]*}
  value=$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')
  [[ "$value" =~ ^[[:xdigit:]]{128}$ ]] || {
    echo "Invalid hosted SHA-512 for $artifact" >&2
    return 1
  }
  printf '%s' "$value"
}
resolve_hosted_dependency() {
  local name=$1 url_variable=$2 sha_variable=$3 url sha
  url=${!url_variable}
  sha=${!sha_variable}
  [[ -n "$url" || -n "$sha" ]] && return 0
  printf -v "$url_variable" '%s/hosted/%s' "$hosted_base" "$name"
  printf -v "$sha_variable" '%s' "$(hosted_sha512 "$name")"
  echo "Resolved checksum-pinned hosted dependency: $name"
}
resolve_hosted_dependency pale_mirror-current.jar pale_mirror_url pale_mirror_sha512
resolve_hosted_dependency pale_mirror_visuals-current.jar pale_mirror_visuals_url pale_mirror_visuals_sha512
resolve_hosted_dependency railwaysuntold-pm-current.jar railway_untold_url railway_untold_sha512
validate_hosted_pale_mirror_args "$pale_mirror_url" "$pale_mirror_sha512"
validate_hosted_pale_mirror_visuals_args "$pale_mirror_visuals_url" "$pale_mirror_visuals_sha512"
validate_hosted_railway_args "$railway_untold_url" "$railway_untold_sha512"

(
  cd "$target"
  "$java_bin" -jar "$bootstrap" -g -s server "$pack_url"
)
for managed_script in run-server-java22.sh set-server-performance-profile.sh benchmark-live-worldgen.sh analyze-worldgen-jfr.sh validate-structure-assets.py frontier-v3-deploy-verify.sh; do
  [[ ! -f "$target/scripts/$managed_script" ]] || chmod +x "$target/scripts/$managed_script"
done

# See the matching client script: packwiz-installer CLI accepts optional mods.
shopt -s nullglob
for jar in "$target"/mods/createcaliber*.jar; do
  disabled="$jar.disabled"
  if [[ -e "$disabled" ]]; then
    disabled_backup_root="$cache_dir/disabled-mod-backups"
    mkdir -p "$disabled_backup_root"
    disabled_backup="$disabled_backup_root/${disabled##*/}.$(date -u +%Y%m%dT%H%M%SZ)"
    mv "$disabled" "$disabled_backup"
    echo "Archived previous disabled Caliber JAR: $disabled_backup"
  fi
  mv "$jar" "$disabled"
done

# Packwiz preserves retired JARs. Keep unsupported caves and the native
# renderer/flight stack out of the server's network-mod list after an update.
retired_unsupported_mods="$cache_dir/retired-unsupported-mods"
for source in "$target"/mods/alexscaves*.jar "$target"/mods/AlexsCaves*.jar \
  "$target"/mods/citadel*.jar "$target"/mods/Citadel*.jar \
  "$target"/mods/DistantHorizons-*.jar "$target"/mods/distanthorizons*.jar \
  "$target"/mods/iris*.jar "$target"/mods/Iris*.jar \
  "$target"/mods/sable*.jar "$target"/mods/Sable*.jar \
  "$target"/mods/create-aeronautics*.jar "$target"/mods/Create-Aeronautics*.jar; do
  [[ -f "$source" ]] || continue
  mkdir -p "$retired_unsupported_mods"
  destination="$retired_unsupported_mods/$(basename "$source").$(date -u +%Y%m%dT%H%M%SZ)"
  mv -- "$source" "$destination"
  echo "Retired unsupported server JAR: $destination"
done

c2me_jar="$target/mods/c2me-neoforge-mc1.21.1-0.3.0+alpha.0.93.jar"
c2me_disabled="$c2me_jar.disabled"
if [[ "$enable_c2me" == true ]]; then
  [[ -f "$c2me_jar" || -f "$c2me_disabled" ]] || {
    echo "Pinned C2ME artifact was not materialised by Packwiz" >&2; exit 1;
  }
  [[ -f "$c2me_jar" ]] || mv "$c2me_disabled" "$c2me_jar"
else
  [[ ! -f "$c2me_jar" ]] || mv "$c2me_jar" "$c2me_disabled"
fi

python3 "$target/scripts/validate-structure-assets.py" \
  --mods-dir "$target/mods" \
  --datapacks "$target/datapacks" \
  --structurify "$target/config/structurify.json"

install_hosted_pale_mirror \
  "$target" "$pale_mirror_url" "$pale_mirror_sha512" "$cache_dir"
install_hosted_pale_mirror_visuals \
  "$target" "$pale_mirror_visuals_url" "$pale_mirror_visuals_sha512" "$cache_dir"
install_hosted_railway_untold \
  "$target" "$railway_untold_url" "$railway_untold_sha512" "$cache_dir"

set_server_property() {
  local key=$1 value=$2 properties="$target/server.properties" temporary
  temporary=$(mktemp "$target/.server-properties.XXXXXX")
  if [[ -f "$properties" ]]; then
    awk -F= -v key="$key" '$1 != key { print }' "$properties" >"$temporary"
  fi
  printf '%s=%s\n' "$key" "$value" >>"$temporary"
  mv "$temporary" "$properties"
}

# A custom dimension is world-local data. Set the selected world before
# resolving its datapack location, rather than relying on a later manual edit.
if [[ -n "$level_name" ]]; then
  set_server_property level-name "$level_name"
fi

resolve_world_datapacks_dir() {
  local properties=$1 level_name
  level_name=world
  if [[ -f "$properties" ]]; then
    level_name=$(awk -F= '$1 == "level-name" { print substr($0, index($0, "=") + 1); exit }' "$properties")
    level_name=${level_name:-world}
  fi
  [[ "$level_name" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
    echo "server.properties level-name must be a simple relative directory name: $level_name" >&2
    exit 2
  }
  printf '%s/%s/datapacks\n' "$target" "$level_name"
}

# A custom dimension must be present before Minecraft creates the selected
# level. Do not assume the vanilla `world` default: deployments may choose a
# distinct disposable level-name for a profile such as Frontier graybox.
world_datapacks=$(resolve_world_datapacks_dir "$target/server.properties")

# Crimson Curse's former no-Spore quarantine replaces the exact resources that
# enable its optional Spore integration. It is therefore incompatible with the
# now-pinned Spore profile. Move, rather than delete, that known managed pack so
# an existing world remains recoverable and administrators' unrelated datapacks
# are never touched.
legacy_spore_quarantine="$world_datapacks/crimson-curse-spore-compat-quarantine"
if compgen -G "$target/mods/spore*.jar" >/dev/null && [[ -d "$legacy_spore_quarantine" ]]; then
  backup_root="$cache_dir/datapack-backups"
  mkdir -p "$backup_root"
  backup_path="$backup_root/crimson-curse-spore-compat-quarantine.$(date -u +%Y%m%dT%H%M%SZ)"
  mv "$legacy_spore_quarantine" "$backup_path"
  echo "Archived incompatible Crimson/Spore quarantine: $backup_path"
fi

# Datapacks are versioned at the pack root but Minecraft loads them from a world.
# Names present in this repository are managed pack assets and must follow pack
# updates. Unrelated administrator datapacks remain untouched. If a managed name
# already differs, archive it before installing the exact current version.
mkdir -p "$world_datapacks"
for datapack in "$repo_root"/datapacks/*; do
  [[ -f "$datapack/pack.mcmeta" ]] || continue
  destination="$world_datapacks/${datapack##*/}"
  if [[ ! -e "$destination" ]]; then
    cp -a "$datapack" "$destination"
  elif diff -qr "$datapack" "$destination" >/dev/null; then
    echo "Managed world datapack is current: $destination"
  else
    datapack_backup_root="$cache_dir/datapack-backups"
    mkdir -p "$datapack_backup_root"
    datapack_backup="$datapack_backup_root/${datapack##*/}.$(date -u +%Y%m%dT%H%M%SZ)"
    mv "$destination" "$datapack_backup"
    cp -a "$datapack" "$destination"
    echo "Updated managed world datapack; archived previous copy: $datapack_backup"
  fi
done

if [[ "$accept_eula" == true ]]; then
  printf 'eula=true\n' >"$target/eula.txt"
fi

set_server_property view-distance 8
set_server_property simulation-distance 6
set_server_property sync-chunk-writes true

if [[ "$enable_c2me" == true ]]; then
  "$target/scripts/set-server-performance-profile.sh" --target "$target" --profile c2me \
    --worldgen combined --surface-rules vanilla --java "$java_bin"
else
  "$target/scripts/set-server-performance-profile.sh" --target "$target" --profile stable \
    --surface-rules vanilla --java "$java_bin"
fi

jvm_args="$target/user_jvm_args.txt"
jvm_temporary=$(mktemp "$target/.user-jvm-args.XXXXXX")
if [[ -f "$jvm_args" ]]; then
  awk '/^# BEGIN FAR FRONTIER MANAGED HEAP$/ { managed=1; next }
       /^# END FAR FRONTIER MANAGED HEAP$/ { managed=0; next }
       !managed { print }' "$jvm_args" >"$jvm_temporary"
fi
cat >>"$jvm_temporary" <<'EOF'
# BEGIN FAR FRONTIER MANAGED HEAP
-Xms4G
-Xmx12G
# END FAR FRONTIER MANAGED HEAP
EOF
mv "$jvm_temporary" "$jvm_args"

echo "Server pack synchronised in: $target"
echo "Runtime: Java 22; view-distance=8; simulation-distance=6; heap=4-12 GiB; C2ME=$enable_c2me"
if [[ "$accept_eula" == false ]]; then
  echo "Mojang EULA not accepted by this script. Review it, then set eula=true in $target/eula.txt." >&2
fi
echo "Start it with: '$target/scripts/run-server-java22.sh'"
