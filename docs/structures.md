# Structures and spacing

The target feeling is long wilderness, then a small discovery, then a meaningful
destination—not a structure every few hundred blocks. These are design bands, not
literal replacement values: small POI about 1–2 km, significant structures 2–5 km,
large dungeons 5–10 km, and major encounters 10+ km.

## Control policy

- Run `/structurify dump` on the final mod set and commit its output/evidence before
  editing a spacing value.
- Structurify owns WDA-replacement-independent sets: YUNG, IDAS, Cataclysm and other
  eligible generators after their actual IDs are confirmed.
- **IDA is excluded from Structurify.** It receives explicit upstream-compatible
  `structure_set` datapack overrides only.
- **Spore is excluded from Structurify.** Its own documented structure-set JSON is
  overridden by `far-frontier-spore-zones`: all thirteen sets use a 4096/3072
  chunk grid. This preserves the local-contamination role without turning every
  biome into a Spore POI field.
- Integrated Villages remains installed as a private asset/dependency source, but
  both of its settlement sets (`regular_villages` and `air_villages`) are disabled.
  Pale Mirror authored settlements are the only generated living settlements.
- Millénaire and random Integrated Villages settlements are disabled in the current
  profile. PM-authored settlements provide the active civilization layer. This does
  not disable Villager Overhaul or unrelated standalone POIs.

The first live `/structurify dump` (Structurify `2.0.30`) found 107 structure sets
and 254 structures. `config/structurify.json` names only the inspected, non-IDA sets:
Better Dungeons, Better Strongholds, Cataclysm and Integrated Villages. The two
Integrated Villages entries are retained as explicit disabled records so an upstream
config refresh cannot silently restore them. Values are
chunks: 80 is about 1.28 km, 160 about 2.56 km, 480 about 7.68 km and 640 about
10.24 km before biome/placement exclusions. It also enables Structurify's documented
global overlap prevention. IDA (`dungeons_arise:*`) and IDAS (`idas:*`) are explicitly
not touched by this first Structurify file; IDA must use its own datapack model.
`datapacks/idas-optional-integration-quarantine` removes placements requiring absent
integrations and the three pinned noisy structures whose NBT still embeds removed
content; its boot test must remain clean.
`datapacks/ida-legacy-loot-fix` preserves the five affected upstream IDA tables while
converting their obsolete nested loot-table fields.
The Spore datapack was boot-tested with the full profile. Its stock biomass-tower
spacing is three chunks, so leaving it at default would be incompatible with this
pack's wilderness requirement. See `docs/spore-integration.md` for the separate
runtime containment policy.

Millénaire `9.0.0-beta.2` remains documented as a compatibility target, but its JAR and
configuration are absent from the active pack. Re-enabling it requires a separate
profile and a successful chunk-load/performance gate; it must not silently return to
the default world-generation set.

## Required inspection

At least several fixed seeds must cover plains, mountains, coast, ocean, desert,
snow and caves. Inspect terrain breaks, floating terrain, terrain-cut structures,
WDA/IDA-equivalent and Cataclysm overlaps, village overlap, IDAS placement, and both
civilization systems. Pregen at least tens of kilometres only after the initial
small-area test passes.
