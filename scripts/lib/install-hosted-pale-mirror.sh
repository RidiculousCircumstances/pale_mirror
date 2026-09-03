#!/usr/bin/env bash
# Shared checksum-pinned installer for the private Pale Mirror artifact.
# This file is sourced by the client and dedicated-server installers.

validate_hosted_pale_mirror_args() {
  local url=${1:-} expected=${2:-}

  if [[ -z "$url" && -z "$expected" ]]; then
    return 0
  fi
  if [[ -z "$url" || -z "$expected" ]]; then
    echo "--pale-mirror-url and --pale-mirror-sha512 must be supplied together" >&2
    return 2
  fi
  if [[ ! "$url" =~ ^https?:// ]]; then
    echo "Pale Mirror artifact URL must use HTTP or HTTPS" >&2
    return 2
  fi
  if [[ ! "$expected" =~ ^[[:xdigit:]]{128}$ ]]; then
    echo "Pale Mirror SHA-512 must contain exactly 128 hexadecimal characters" >&2
    return 2
  fi
}

validate_hosted_pale_mirror_visuals_args() {
  local url=${1:-} expected=${2:-}

  if [[ -z "$url" && -z "$expected" ]]; then
    return 0
  fi
  if [[ -z "$url" || -z "$expected" ]]; then
    echo "--pale-mirror-visuals-url and --pale-mirror-visuals-sha512 must be supplied together" >&2
    return 2
  fi
  if [[ ! "$url" =~ ^https?:// ]]; then
    echo "Pale Mirror Visuals artifact URL must use HTTP or HTTPS" >&2
    return 2
  fi
  if [[ ! "$expected" =~ ^[[:xdigit:]]{128}$ ]]; then
    echo "Pale Mirror Visuals SHA-512 must contain exactly 128 hexadecimal characters" >&2
    return 2
  fi
}

pale_mirror_sha512() {
  local path=$1
  if command -v sha512sum >/dev/null; then
    sha512sum "$path" | awk '{print tolower($1)}'
  elif command -v shasum >/dev/null; then
    shasum -a 512 "$path" | awk '{print tolower($1)}'
  else
    echo "sha512sum or shasum is required to install Pale Mirror" >&2
    return 1
  fi
}

pale_mirror_sha512_matches() {
  local expected=$1 path=$2 actual
  [[ -f "$path" ]] || return 1
  actual=$(pale_mirror_sha512 "$path") || return 1
  [[ "$actual" == "$expected" ]]
}

archive_retired_pale_mirror() {
  local path=$1 cache_dir=$2 retired_dir timestamp destination
  retired_dir="$cache_dir/retired-pale-mirror"
  timestamp=$(date -u +%Y%m%dT%H%M%SZ)
  mkdir -p "$retired_dir"
  destination="$retired_dir/${path##*/}.$timestamp"
  mv -- "$path" "$destination"
  echo "Archived stale Pale Mirror JAR: $destination"
}

install_hosted_pale_mirror() {
  local target=$1 url=${2:-} expected=${3:-} cache_dir=$4
  [[ -n "$url" ]] || return 0

  expected=$(printf '%s' "$expected" | tr '[:upper:]' '[:lower:]')
  local mods_dir="$target/mods"
  local managed_jar="$mods_dir/pale_mirror-hosted.jar"
  mkdir -p "$mods_dir" "$cache_dir"

  # Do not collect globs in an array here. macOS ships Bash 3.2, where an
  # empty array expanded under `set -u` aborts the updater before it can retire
  # stale JARs. A nullglob-backed loop has the same semantics without that
  # platform-dependent failure mode.
  local nullglob_state candidate verified_existing=""
  nullglob_state=$(shopt -p nullglob || true)
  shopt -s nullglob
  for candidate in "$mods_dir"/pale_mirror*.jar "$mods_dir"/pale-mirror*.jar; do
    [[ -f "$candidate" ]] || continue
    case "${candidate##*/}" in
      pale_mirror_visuals*|pale-mirror-visuals*) continue ;;
    esac
    if pale_mirror_sha512_matches "$expected" "$candidate"; then
      if [[ "$candidate" == "$managed_jar" ]]; then
        verified_existing="$candidate"
        break
      fi
      [[ -n "$verified_existing" ]] || verified_existing="$candidate"
    fi
  done
  eval "$nullglob_state"

  # A previous updater used build-versioned names and would refuse to replace
  # them. That leaves an old client speaking obsolete channels such as
  # `pale_mirror:atlas` after the server has moved on. Keep every obsolete JAR
  # recoverably under the installer cache, then make the checksum-pinned JAR
  # the sole active Pale Mirror artifact.
  if [[ -n "$verified_existing" ]]; then
    nullglob_state=$(shopt -p nullglob || true)
    shopt -s nullglob
    for candidate in "$mods_dir"/pale_mirror*.jar "$mods_dir"/pale-mirror*.jar; do
      case "${candidate##*/}" in
        pale_mirror_visuals*|pale-mirror-visuals*) continue ;;
      esac
      [[ "$candidate" == "$verified_existing" ]] || archive_retired_pale_mirror "$candidate" "$cache_dir"
    done
    eval "$nullglob_state"
    echo "Pale Mirror already installed and SHA-512 verified: $verified_existing"
    return 0
  fi
  nullglob_state=$(shopt -p nullglob || true)
  shopt -s nullglob
  for candidate in "$mods_dir"/pale_mirror*.jar "$mods_dir"/pale-mirror*.jar; do
    case "${candidate##*/}" in
      pale_mirror_visuals*|pale-mirror-visuals*) continue ;;
    esac
    archive_retired_pale_mirror "$candidate" "$cache_dir"
  done
  eval "$nullglob_state"

  local cache_jar="$cache_dir/pale-mirror-${expected:0:16}.jar"
  if ! pale_mirror_sha512_matches "$expected" "$cache_jar"; then
    local download_tmp
    download_tmp=$(mktemp "$cache_dir/.pale-mirror-download.XXXXXX.jar")
    echo "Downloading checksum-pinned Pale Mirror artifact..."
    if ! curl --fail --location --retry 3 --output "$download_tmp" "$url"; then
      rm -f "$download_tmp"
      return 1
    fi
    if ! pale_mirror_sha512_matches "$expected" "$download_tmp"; then
      local actual
      actual=$(pale_mirror_sha512 "$download_tmp" 2>/dev/null || printf 'unavailable')
      rm -f "$download_tmp"
      echo "Pale Mirror SHA-512 verification failed" >&2
      echo "  expected: $expected" >&2
      echo "  actual:   $actual" >&2
      return 1
    fi
    mv -f "$download_tmp" "$cache_jar"
  fi

  local install_tmp
  install_tmp=$(mktemp "$mods_dir/.pale-mirror-install.XXXXXX.jar")
  cp "$cache_jar" "$install_tmp"
  chmod 0644 "$install_tmp"
  mv -f "$install_tmp" "$managed_jar"
  pale_mirror_sha512_matches "$expected" "$managed_jar" || {
    echo "Installed Pale Mirror JAR failed its final SHA-512 verification" >&2
    return 1
  }
  echo "Pale Mirror installed and SHA-512 verified: $managed_jar"
}

install_hosted_pale_mirror_visuals() {
  local target=$1 url=${2:-} expected=${3:-} cache_dir=$4
  [[ -n "$url" ]] || return 0

  expected=$(printf '%s' "$expected" | tr '[:upper:]' '[:lower:]')
  local mods_dir="$target/mods"
  local managed_jar="$mods_dir/pale_mirror_visuals-hosted.jar"
  mkdir -p "$mods_dir" "$cache_dir"

  # See install_hosted_pale_mirror: avoid empty-array expansion under macOS
  # Bash 3.2 with nounset enabled.
  local nullglob_state candidate existing="" candidate_count=0
  nullglob_state=$(shopt -p nullglob || true)
  shopt -s nullglob
  for candidate in "$mods_dir"/pale_mirror_visuals*.jar "$mods_dir"/pale-mirror-visuals*.jar; do
    [[ -f "$candidate" ]] || continue
    existing="$candidate"
    candidate_count=$((candidate_count + 1))
  done
  eval "$nullglob_state"

  if ((candidate_count > 1)); then
    echo "Multiple Pale Mirror Visuals JARs are present; refusing to choose between them:" >&2
    nullglob_state=$(shopt -p nullglob || true)
    shopt -s nullglob
    for candidate in "$mods_dir"/pale_mirror_visuals*.jar "$mods_dir"/pale-mirror-visuals*.jar; do
      [[ -f "$candidate" ]] && printf '  %s\n' "$candidate" >&2
    done
    eval "$nullglob_state"
    return 1
  fi
  if ((candidate_count == 1)); then
    if pale_mirror_sha512_matches "$expected" "$existing"; then
      echo "Pale Mirror Visuals already installed and SHA-512 verified: $existing"
      return 0
    fi
    if [[ "$existing" != "$managed_jar" ]]; then
      echo "An unmanaged Pale Mirror Visuals JAR conflicts with the hosted artifact: $existing" >&2
      return 1
    fi
  fi

  local cache_jar="$cache_dir/pale-mirror-visuals-${expected:0:16}.jar"
  if ! pale_mirror_sha512_matches "$expected" "$cache_jar"; then
    local download_tmp
    download_tmp=$(mktemp "$cache_dir/.pale-mirror-visuals-download.XXXXXX.jar")
    echo "Downloading checksum-pinned Pale Mirror Visuals artifact..."
    if ! curl --fail --location --retry 3 --output "$download_tmp" "$url"; then
      rm -f "$download_tmp"
      return 1
    fi
    if ! pale_mirror_sha512_matches "$expected" "$download_tmp"; then
      rm -f "$download_tmp"
      echo "Pale Mirror Visuals SHA-512 verification failed" >&2
      return 1
    fi
    mv -f "$download_tmp" "$cache_jar"
  fi

  local install_tmp
  install_tmp=$(mktemp "$mods_dir/.pale-mirror-visuals-install.XXXXXX.jar")
  cp "$cache_jar" "$install_tmp"
  chmod 0644 "$install_tmp"
  mv -f "$install_tmp" "$managed_jar"
  pale_mirror_sha512_matches "$expected" "$managed_jar" || {
    echo "Installed Pale Mirror Visuals JAR failed its final SHA-512 verification" >&2
    return 1
  }
  echo "Pale Mirror Visuals installed and SHA-512 verified: $managed_jar"
}
