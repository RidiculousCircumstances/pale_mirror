#!/usr/bin/env bash
# Checksum-pinned installer for the private PM Railway Untold fork.

validate_hosted_railway_args() {
  local url=${1:-} expected=${2:-}
  if [[ -z "$url" && -z "$expected" ]]; then return 0; fi
  if [[ -z "$url" || -z "$expected" ]]; then
    echo "--railway-untold-url and --railway-untold-sha512 must be supplied together" >&2; return 2
  fi
  [[ "$url" =~ ^https?:// ]] || { echo "Railway Untold artifact URL must use HTTP or HTTPS" >&2; return 2; }
  [[ "$expected" =~ ^[[:xdigit:]]{128}$ ]] || { echo "Railway Untold SHA-512 must contain 128 hex characters" >&2; return 2; }
}

railway_sha512() {
  if command -v sha512sum >/dev/null; then sha512sum "$1" | awk '{print tolower($1)}';
  elif command -v shasum >/dev/null; then shasum -a 512 "$1" | awk '{print tolower($1)}';
  else echo "sha512sum or shasum is required" >&2; return 1; fi
}

railway_sha512_matches() {
  [[ -f "$2" ]] && [[ "$(railway_sha512 "$2")" == "$1" ]]
}

install_hosted_railway_untold() {
  local target=$1 url=${2:-} expected=${3:-} cache_dir=$4
  [[ -n "$url" ]] || return 0
  expected=$(printf '%s' "$expected" | tr '[:upper:]' '[:lower:]')
  local mods_dir="$target/mods" managed_jar="$target/mods/railwaysuntold-pm-hosted.jar"
  mkdir -p "$mods_dir" "$cache_dir"
  local nullglob_state; nullglob_state=$(shopt -p nullglob || true); shopt -s nullglob
  local candidates=("$mods_dir"/railwaysuntold*.jar); eval "$nullglob_state"
  if ((${#candidates[@]} > 1)); then
    echo "Multiple Railway Untold JARs are present; refusing to choose:" >&2; printf '  %s\n' "${candidates[@]}" >&2; return 1
  fi
  if ((${#candidates[@]} == 1)); then
    local existing=${candidates[0]}
    if railway_sha512_matches "$expected" "$existing"; then echo "PM Railway Untold verified: $existing"; return 0; fi
    [[ "$existing" == "$managed_jar" ]] || {
      echo "An unmanaged Railway Untold JAR conflicts with the required PM fork: $existing" >&2; return 1;
    }
  fi
  local cache_jar="$cache_dir/railwaysuntold-pm-${expected:0:16}.jar"
  if ! railway_sha512_matches "$expected" "$cache_jar"; then
    local download_tmp; download_tmp=$(mktemp "$cache_dir/.railway-download.XXXXXX.jar")
    curl --fail --location --retry 3 --output "$download_tmp" "$url" || { rm -f "$download_tmp"; return 1; }
    if ! railway_sha512_matches "$expected" "$download_tmp"; then
      echo "PM Railway Untold SHA-512 verification failed" >&2; rm -f "$download_tmp"; return 1
    fi
    mv -f "$download_tmp" "$cache_jar"
  fi
  local install_tmp; install_tmp=$(mktemp "$mods_dir/.railway-install.XXXXXX.jar")
  cp "$cache_jar" "$install_tmp"; chmod 0644 "$install_tmp"; mv -f "$install_tmp" "$managed_jar"
  railway_sha512_matches "$expected" "$managed_jar" || { echo "Installed PM Railway Untold checksum failed" >&2; return 1; }
  echo "PM Railway Untold installed and verified: $managed_jar"
}
