# IDAS optional-integration quarantine

Targets only data requiring absent optional mods. It removes Dread Citadel,
Archmage's Tower and Siren's Cove (Ice and Fire / Ars Nouveau), plus the pinned
IDAS 1.13.7 structures whose NBT embeds unavailable Guard Villagers, Bakery or
Eidolon content. Their otherwise unused integration loot tables are valid empty
chest tables, and their integration spawner pools use vanilla entities.

Quarantined IDAS structures:

- `abandoned_vineyard`
- `bazaar`
- `castle`
- all three `desert_market` variants
- both `dig_site` variants
- `pillager_camp`
- `redhorn_guild`
- `ruins_of_the_deep`
- `tudor_pub`

The active private profile additionally removes `ancient_mines`, `labyrinth` and
`mason_house`. Their pinned structure NBT embeds removed mod entities, attributes
and items and repeatedly spends chunk-worker time parsing invalid payloads. Pale
Mirror's privately imported authored mine modules are separately sanitised and are
not affected by disabling these random IDAS placements.

The profile also removes `abandonedhouse`, `pillager_fortress` and `winter_wagon`.
Their NBT contains removed Alex's Mobs or legacy Quark items (`banana`,
`shark_tooth`, `frog_leg` and `banana_peel`), producing repeated item decode errors
on C2ME workers during live travel.

The reachable-graph NBT audit additionally removes active roots containing
1.21-invalid `minecraft:air` item stacks in filters, inventories or villager
offers. This affects the remaining lumber/desert camps and several large inns,
workshops, ruins and museums; the exact retained roots are the reviewed entries
in the four structure-set overrides. Pale Mirror's imported modules remain
available through their separate entity/block-entity sanitizing pipeline.
The broken underground-camp set is filtered out entirely, while the Nether set
retains only its valid ancient portal root. The three IDAS structure-set exclusion
tags are replaced as well, so they retain valid spacing exclusions without required
references to the removed underground sets.

All retained IDAS structures remain enabled. The placement-set overrides preserve
the upstream spacing, separation, salts and weights of retained structures.
Four biome tags belonging exclusively to absent BYG/Biomes O' Plenty integrations
are replaced with valid empty tags so registry loading does not emit missing-key
errors. Empty aliases also cover three spelling mismatches between IDAS's tag file
names and the tag IDs referenced by its structures.

Data format: Minecraft 1.21.1, `pack_format` 48. Install this folder into a world's
`datapacks/` directory before first generation, then verify with `/datapack list` and
the server boot log. It must not be added to an existing generated world without a
backup and a reload test.
