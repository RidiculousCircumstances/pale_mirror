#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: $0 --target <server-directory> --profile stable|c2me [--worldgen baseline|native|density|combined] [--surface-rules optimized|vanilla] [--java <Java 22 executable>]" >&2
}

target=""
profile=""
java_bin=${JAVA_BIN:-java}
worldgen=combined
surface_rules=vanilla
while (($#)); do
  case "$1" in
    --target) target=${2:?--target requires a directory}; shift 2 ;;
    --profile) profile=${2:?--profile requires stable or c2me}; shift 2 ;;
    --worldgen) worldgen=${2:?--worldgen requires baseline, native, density or combined}; shift 2 ;;
    --surface-rules) surface_rules=${2:?--surface-rules requires optimized or vanilla}; shift 2 ;;
    --java) java_bin=${2:?--java requires an executable}; shift 2 ;;
    *) usage; exit 2 ;;
  esac
done
[[ -d "$target/mods" && ( "$profile" == stable || "$profile" == c2me ) ]] || { usage; exit 2; }
[[ "$worldgen" == baseline || "$worldgen" == native || "$worldgen" == density || "$worldgen" == combined ]] || { usage; exit 2; }
[[ "$surface_rules" == optimized || "$surface_rules" == vanilla ]] || { usage; exit 2; }

set_toml_key() {
  local file=$1 section=$2 key=$3 value=$4 temporary
  temporary=$(mktemp "${file}.XXXXXX")
  awk -v section="$section" -v key="$key" -v value="$value" '
    BEGIN { active = section == "" }
    function trim(value) {
      sub(/^[[:space:]]+/, "", value)
      sub(/[[:space:]]+$/, "", value)
      return value
    }
    {
      stripped = trim($0)
      if (stripped ~ /^\[.*\]$/) active = section != "" && stripped == "[" section "]"
      if (active && stripped ~ ("^" key "[[:space:]]*=")) {
        match($0, /^[[:space:]]*/)
        print substr($0, 1, RLENGTH) key " = " value
        replaced = 1
        next
      }
      print
    }
    END { if (!replaced) exit 42 }
  ' "$file" >"$temporary" || {
    rm -f "$temporary"
    echo "Missing TOML key [$section].$key in $file" >&2
    exit 1
  }
  mv "$temporary" "$file"
}

set_property_key() {
  local file=$1 key=$2 value=$3 temporary
  temporary=$(mktemp "${file}.XXXXXX")
  [[ ! -f "$file" ]] || awk -F= -v key="$key" '$1 != key { print }' "$file" >"$temporary"
  printf '%s=%s\n' "$key" "$value" >>"$temporary"
  mv "$temporary" "$file"
}

jar="$target/mods/c2me-neoforge-mc1.21.1-0.3.0+alpha.0.93.jar"
disabled="$jar.disabled"
if [[ "$profile" == c2me ]]; then
  command -v "$java_bin" >/dev/null || { echo "Java executable not found: $java_bin" >&2; exit 1; }
  "$java_bin" -version 2>&1 | grep -Eq '(version|openjdk) "22([."]|$)' || {
    echo "The C2ME profile requires the pinned Java 22 server runtime" >&2; exit 1;
  }
  [[ -f "$jar" || -f "$disabled" ]] || { echo "Pinned C2ME artifact is missing; rerun install-server.sh" >&2; exit 1; }
  [[ -f "$jar" ]] || mv "$disabled" "$jar"
  c2me_config="$target/config/c2me.toml"
  if [[ -f "$c2me_config" ]]; then
    density=false
    native=false
    [[ "$worldgen" != density && "$worldgen" != combined ]] || density=true
    [[ "$worldgen" != native && "$worldgen" != combined ]] || native=true
    set_toml_key "$c2me_config" "" globalExecutorParallelism 8
    set_toml_key "$c2me_config" noTickViewDistance maxConcurrentChunkLoads 8
    set_toml_key "$c2me_config" vanillaWorldGenOptimizations useDensityFunctionCompiler "$density"
    set_toml_key "$c2me_config" vanillaWorldGenOptimizations.nativeAcceleration enabled "$native"
    set_toml_key "$c2me_config" vanillaWorldGenOptimizations.nativeAcceleration allowAVX512 false
  fi
else
  [[ ! -f "$jar" ]] || mv "$jar" "$disabled"
fi
modernfix_config="$target/config/modernfix-mixins.properties"
mkdir -p "${modernfix_config%/*}"
if [[ "$surface_rules" == optimized ]]; then
  set_property_key "$modernfix_config" mixin.perf.optimize_surface_rules true
else
  set_property_key "$modernfix_config" mixin.perf.optimize_surface_rules false
fi
echo "Far Frontier server performance profile: $profile"
[[ "$profile" != c2me || ! -f "${c2me_config:-}" ]] || echo "C2ME workers=8 concurrent-loads=8 worldgen=$worldgen AVX512=false"
echo "ModernFix surface-rules=$surface_rules"
