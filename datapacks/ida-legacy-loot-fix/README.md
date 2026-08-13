# IDA legacy data fixes

IDA `2.1.1` retains five loot tables using the pre-1.21 nested loot-table field
`name`. Minecraft 1.21.1 expects `value`. The files in this pack are verbatim
upstream tables except for that exact field in their final nested entry; weights,
pools and functions are intentionally unchanged.

Install before generating a world and verify it is enabled with `/datapack list`.
The pack uses Minecraft 1.21.1 data `pack_format` 48.

The pack also updates IDA 2.1.1's Integrated API map-trade icon values from old
enum names such as `BANNER_RED` to Minecraft 1.21.1 registry IDs such as
`minecraft:banner_red`. Structure-map trades remain enabled; only their legacy
serialization is corrected.

The managed major structure set quarantines only roots proven broken by the
reachable-graph validator. `plague_asylum` references an absent spawner pool;
`aviary`, `bandit_village`, `foundry`, `greenwood_pub`, both campsites,
`infested_temple`, `keep_kayra`, `kisegi_sanctuary`, `mechanical_nest`,
`mining_complex` and `thornborn_towers` contain invalid `minecraft:air` item
stacks, absent templates or absent pools. These roots generated repeated C2ME
worker errors and expensive failed work. Retained roots keep their upstream
weights; placement spacing, separation, salt and exclusion policy are unchanged.
