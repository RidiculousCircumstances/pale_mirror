# Pale Mirror Visuals

`pale-mirror-visuals` is the required physical-expression module for the private
Pale Mirror product profile. It is deliberately separate from the authoritative
core. Core owns settlement identity, population counts, economy, facilities,
routes, threats, scenarios and revisions. Visuals owns fresh-world layout,
curated structure assets, resident carriers, animation, particles and sound.

## Fresh-world contract

There is intentionally no v32 retrofit path. A new v33 world pins three complete
`AuthoredRegionSeed` manifests before any block is changed. The first region is
1024–2048 blocks from spawn; two later regions are sparse at 6000–10000 blocks.
The immutable manifest contains every object identity and coordinate needed by
core: settlement bounds, freight gate, receiving depot, Mine17, Red Valley,
baseline rail path, modules, expansion plots and 48 resident identities.

Terrain survey calls generator-only height, column and biome APIs. It does not
load prospective chunks. Once a player naturally loads an intersecting chunk,
the visual module performs only that chunk's bounded first-generation work.
The completion ledger and pinned manifests survive restart. At startup, loaded
but incomplete chunks are resumed without tickets or force-loading.

## Settlement grammar

The current `iron_frontier` grammar produces a 176-block-diameter timber fort:

- civic hall and market in the center;
- housing, clinic, inn and workshops in functional rings;
- barracks, smithy, stable, freight depot and one outward freight gate;
- radial paths, a ring road and wooden palisade;
- six reserved development plots;
- temperate, cold-taiga and dry-arid palette families.

Temperate and cold variants first attempt normalized private Integrated
Villages-derived NBT modules. If a module is absent, too large or not yet fully
loaded, a deterministic programmatic building remains the safe fallback. The
original private inputs and hashes are recorded in
[`VISUAL_ASSET_PROVENANCE.md`](VISUAL_ASSET_PROVENANCE.md).

## Managed residents

Every frontier starts with exactly 48 commissioned physical carriers:

| Cohort | Count |
| --- | ---: |
| Civilians | 20 |
| Workers | 14 |
| Specialists | 4 |
| Guards | 6 |
| Children | 4 |

Each carrier has a stable UUID, region, cohort, role, home and workplace.
Villager Overhaul's exact pinned API provides guard patrol/combat and worker
behavior through one isolated bridge. A fail-closed exact-version mixin denies
player recruitment of PM residents. Ambient breeding inside the authored fort
is rejected unless a canonical growth permit is present.

Missing or unloaded entities never change population. Only a confirmed death
event for a registered UUID becomes a typed observation. Core deduplicates it,
checks the settlement field-authority profile, and only then reduces the
canonical cohort.

## Dynamic expression

Core publishes a small immutable projection of facility and settlement state.
It grants no domain mutation capability. Visuals currently expresses it as:

- a persistent GeckoLib Threat Heart in the primary mine chamber while the
  canonical facility is infected;
- four synchronized pulse/particle stages for `FOOTHOLD` through `APEX`;
- heartbeat-like sculk audio and infection particles;
- sparse depot smoke/angry cues during shortage or critical crisis;
- recovery and prosperity particles when the region stabilizes.

The Heart is an invulnerable visual carrier. The existing PM combat/controller
contract remains the authoritative objective, so animation cannot create a
second health pool or resolve a scenario by itself. Reconciliation is
idempotent and operates only while the relevant chunk is loaded.

## Verification

```bash
./gradlew :pale-mirror-visuals:test
./gradlew :pale-mirror-visuals:runGameTestServer
./gradlew :pale-mirror-visuals:verifyVisualsJar
./gradlew :pale-mirror-visuals:visualsIntegrationHarness
./gradlew verifyDistribution
```

The dedicated visual GameTests cover manifest NBT round-trip and Threat Heart
identity/stage persistence. Graphical model, texture, animation, audible range,
building composition and terrain quality still require a real-client seed
matrix before the visual cutover can be called product-validated.
