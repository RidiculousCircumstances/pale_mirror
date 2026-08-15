#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/integrated_villages-1.3.3+1.21.1-neoforge.jar" >&2
  exit 2
fi

source_jar=$1
expected_sha512=50d119a972c8813b7c6d54db828a5130f342ff8ec2728bf3c73057d46b1d575d00f37ed76a20c5dbcc84cb74970961ebf31065818f16f49b6c7a297605fba161
actual_sha512=$(sha512sum -- "$source_jar" | awk '{print $1}')
if [[ "$actual_sha512" != "$expected_sha512" ]]; then
  echo "Refusing unpinned Integrated Villages archive: $actual_sha512" >&2
  exit 1
fi

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
structure_root="$repo_root/pale-mirror-visuals/src/main/resources/data/pale_mirror_visuals/structure"
source_root=data/integrated_villages/structure

copy_asset() {
  local source_path=$1
  local target_name=$2
  local family target
  for family in temperate cold_taiga dry_arid; do
    target="$structure_root/$family/$target_name.nbt"
    mkdir -p -- "$(dirname -- "$target")"
    unzip -p -- "$source_jar" "$source_root/$source_path.nbt" > "$target"
  done
}

# Settlement role library. Climate variants share geometry; PM remaps their
# block palette at compile time while keeping one exact entrance contract.
copy_asset marketstead_village/house/marketstead_village_library trading_hall
copy_asset marketstead_village/house/marketstead_village_botanist apothecary
copy_asset cabin_village/house/cabin_village_fisherman bakery
copy_asset marketstead_village/house/marketstead_village_mason smeltery
copy_asset marketstead_village/house/marketstead_village_blacksmith forge
copy_asset clockwork_village/house/clockwork_village_toolsmith engineer_shop
copy_asset marketstead_village/house/marketstead_village_stables stable_yard
copy_asset marketstead_village/house/marketstead_village_engineer assay_office
copy_asset marketstead_village/house/marketstead_village_butcher bunkhouse_2
copy_asset marketstead_village/house/marketstead_village_basichouse2 residence_4
copy_asset marketstead_village/house/marketstead_village_farmhouse residence_5
copy_asset marketstead_village/house/marketstead_village_botanist residence_3
copy_asset cabin_village/house/cabin_village_armorer watch_house

# Complete surface MineSite buildings and staged Red Valley forms.
copy_asset clockwork_village/house/clockwork_village_toolsmith mine/portal_hoist
copy_asset marketstead_village/house/marketstead_village_botanist mine/crew_outpost
copy_asset marketstead_village/house/marketstead_village_mason mine/processing_hall
copy_asset marketstead_village/house/marketstead_village_engineer mine/power_house
copy_asset marketstead_village/house/marketstead_village_stables mine/loading_yard
copy_asset marketstead_village/house/marketstead_village_mason mine/dispatch_shell
copy_asset marketstead_village/house/marketstead_village_engineer mine/dispatch_machinery
copy_asset marketstead_village/house/marketstead_village_stables mine/dispatch_commissioning

echo "Curated frontier assets imported from pinned Integrated Villages archive."
echo "Run :pale-mirror-visuals:test to verify catalog dimensions and SHA-256 pins."
