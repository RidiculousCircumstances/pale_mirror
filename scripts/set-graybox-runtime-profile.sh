#!/usr/bin/env bash
# Apply the quiet, source-authoritative Pale Mirror graybox runtime profile.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/set-graybox-runtime-profile.sh --target RUNTIME [--restore] [--dry-run]

The profile is for the disposable Pale Mirror graybox server. It moves only
server-only competing gameplay JARs to a reversible `.graybox-disabled` suffix.
Shared client/server mods remain installed on both sides; PM suppresses their
activity in the graybox through its world-side ownership boundary. On a server
the profile also configures natural-spawn policy. It never touches Pale Mirror,
Create, player items, client rendering, optimisation libraries, worlds or
player data.

Stop a server/client before changing its runtime and restart it afterwards.
Use --restore to return the JARs and the recorded server.properties values.
EOF
}

target=
restore=false
dry_run=false
while (($#)); do
  case "$1" in
    --target) target=${2:?--target requires a runtime directory}; shift 2 ;;
    --restore) restore=true; shift ;;
    --dry-run) dry_run=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) printf 'Unknown argument: %s\n' "$1" >&2; usage >&2; exit 2 ;;
  esac
done

[[ -n "$target" ]] || { usage >&2; exit 2; }
[[ -d "$target/mods" ]] || { printf 'Runtime has no mods directory: %s\n' "$target" >&2; exit 2; }
runtime=$(cd "$target" && pwd -P)
[[ "$runtime" != / ]] || { printf 'Refusing to operate on filesystem root.\n' >&2; exit 2; }

mods_dir="$runtime/mods"
profile_dir="$runtime/.far-frontier-graybox-profile"
properties="$runtime/server.properties"
properties_backup="$profile_dir/server.properties.before-graybox"
script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
competing_jars_catalog="$script_dir/../config/graybox-disabled-mods.txt"

# These mods create their own actors, infection, events or time-line authority.
# In a source-graybox run they compete with PM's exact population and
# reverse-causality model. Dependencies are deliberately left installed: a
# dormant library cannot create world events, and retaining it keeps the profile
# reversible without guessing a transitive dependency graph.
[[ -f "$competing_jars_catalog" ]] || { printf 'Missing graybox mod catalog: %s\n' "$competing_jars_catalog" >&2; exit 1; }
mapfile -t competing_jars < <(awk 'NF && $1 !~ /^#/ { print }' "$competing_jars_catalog")
((${#competing_jars[@]})) || { printf 'Graybox mod catalog is empty: %s\n' "$competing_jars_catalog" >&2; exit 1; }
"$script_dir/validate-graybox-runtime-profile.sh"

# Pale Mirror Visuals has an exact declared dependency on Villager Overhaul.
# Keep it loaded as the required rendering/attribute bridge; PM's named-resident
# runtime remains the owner of identity, movement, combat and mortality.
required_jars=(
  'villageroverhaul-neoforge-1.21.1-3.10.17.16.jar'
)

# The retained disposable world has a saved `far-frontier-spore-zones`
# datapack which registers spore:* worldgen entries. NeoForge rejects the
# world before PM can start when this one registry provider is absent. Native
# Spore spawning remains disabled by the server profile and PM's sandbox owns
# its lifecycle, so this is a load compatibility dependency, not an authority
# granted to the source-graybox simulation.
pinned_source_jars=(
  'spore_1.21.1_2.2.0j_neo.jar'
)

run() {
  if "$dry_run"; then
    printf 'DRY-RUN:' >&2
    printf ' %q' "$@" >&2
    printf '\n' >&2
  else
    "$@"
  fi
}

disable_jar() {
  local source="$mods_dir/$1" disabled="$mods_dir/$1.graybox-disabled"
  if [[ -f "$source" && -f "$disabled" ]]; then
    # Packwiz restores managed files by their canonical filename. A later pack
    # refresh can therefore recreate the enabled JAR beside the deliberate
    # graybox-disabled copy. Preserve that former copy for diagnosis rather
    # than silently choosing or deleting it, then make the freshly managed
    # artifact the one disabled copy.
    local duplicate_root="$profile_dir/packwiz-replaced-jars"
    local archived="$duplicate_root/$1.$(date -u +%Y%m%dT%H%M%SZ)"
    run mkdir -p "$duplicate_root"
    run mv -- "$disabled" "$archived"
    printf 'Archived previous disabled gameplay mod after Packwiz refresh: %s\n' "$archived"
  fi
  if [[ -f "$source" ]]; then
    run mv -- "$source" "$disabled"
    printf 'Disabled competing gameplay mod: %s\n' "$1"
  elif [[ -f "$disabled" ]]; then
    printf 'Already disabled: %s\n' "$1"
  else
    printf 'Not installed (nothing to disable): %s\n' "$1"
  fi
}

restore_jar() {
  local source="$mods_dir/$1.graybox-disabled" restored="$mods_dir/$1"
  if [[ -f "$source" && -f "$restored" ]]; then
    printf 'Profile conflict: both enabled and disabled copies exist for %s\n' "$1" >&2
    exit 1
  fi
  if [[ -f "$source" ]]; then
    run mv -- "$source" "$restored"
    printf 'Restored gameplay mod: %s\n' "$1"
  fi
}

ensure_required_jar() {
  local disabled="$mods_dir/$1.graybox-disabled" required="$mods_dir/$1"
  if [[ -f "$disabled" && -f "$required" ]]; then
    printf 'Profile conflict: both enabled and disabled copies exist for required %s\n' "$1" >&2
    exit 1
  fi
  if [[ -f "$disabled" ]]; then
    run mv -- "$disabled" "$required"
    printf 'Restored required Pale Mirror dependency: %s\n' "$1"
  elif [[ -f "$required" ]]; then
    printf 'Retained required Pale Mirror dependency: %s\n' "$1"
  else
    printf 'Required Pale Mirror dependency is missing: %s\n' "$1" >&2
    exit 1
  fi
}

ensure_pinned_source_jar() {
  local disabled="$mods_dir/$1.graybox-disabled" pinned="$mods_dir/$1"
  if [[ -f "$disabled" && -f "$pinned" ]]; then
    printf 'Profile conflict: both enabled and disabled copies exist for pinned source %s\n' "$1" >&2
    exit 1
  fi
  if [[ -f "$disabled" ]]; then
    run mv -- "$disabled" "$pinned"
    printf 'Restored required world-registry provider: %s\n' "$1"
  elif [[ -f "$pinned" ]]; then
    printf 'Retained required world-registry provider: %s\n' "$1"
  else
    printf 'Required world-registry provider is missing: %s\n' "$1" >&2
    exit 1
  fi
}

upsert_property() {
  local key=$1 value=$2
  if rg -q "^${key}=" "$properties"; then
    run sed -i "s/^${key}=.*/${key}=${value}/" "$properties"
  else
    if "$dry_run"; then
      printf 'DRY-RUN: append %s=%s to %s\n' "$key" "$value" "$properties" >&2
    else
      printf '%s=%s\n' "$key" "$value" >>"$properties"
    fi
  fi
}

snapshot_server_properties() {
  [[ -f "$properties_backup" ]] && return
  if "$dry_run"; then
    printf 'DRY-RUN: record original natural-spawn properties in %s\n' "$properties_backup" >&2
    return
  fi
  mkdir -p "$profile_dir"
  : >"$properties_backup"
  local key
  for key in spawn-animals spawn-monsters spawn-npcs; do
    if rg -q "^${key}=" "$properties"; then
      rg "^${key}=" "$properties" >>"$properties_backup"
    else
      printf '%s=true\n' "$key" >>"$properties_backup"
    fi
  done
}

enable_server_spawns() {
  [[ -f "$properties" ]] || return 0
  snapshot_server_properties
  local key
  # These flags do more than prevent natural creation: vanilla discards an
  # already materialized Villager while spawn-npcs=false.  PM owns ambient
  # exclusion at the graybox EntityJoinLevel boundary instead, so canonical
  # residents and bioforms retain normal entity lifetime semantics.
  for key in spawn-animals spawn-monsters spawn-npcs; do upsert_property "$key" true; done
  printf 'Enabled vanilla spawn flags; Pale Mirror owns non-source-mob exclusion inside the graybox dimension.\n'
}

restore_server_spawns() {
  [[ -f "$properties_backup" && -f "$properties" ]] || return 0
  local entry key value
  while IFS= read -r entry; do
    key=${entry%%=*}
    value=${entry#*=}
    [[ -n "$key" && "$key" != "$entry" ]] || continue
    upsert_property "$key" "$value"
  done <"$properties_backup"
  printf 'Restored recorded natural-spawn settings for this server runtime.\n'
}

if "$restore"; then
  for jar in "${competing_jars[@]}"; do restore_jar "$jar"; done
  for jar in "${required_jars[@]}"; do ensure_required_jar "$jar"; done
  for jar in "${pinned_source_jars[@]}"; do ensure_pinned_source_jar "$jar"; done
  restore_server_spawns
  exit 0
fi

for jar in "${competing_jars[@]}"; do disable_jar "$jar"; done
for jar in "${required_jars[@]}"; do ensure_required_jar "$jar"; done
for jar in "${pinned_source_jars[@]}"; do ensure_pinned_source_jar "$jar"; done
enable_server_spawns
printf 'Graybox runtime profile is active for %s. Restart this runtime before use.\n' "$runtime"
