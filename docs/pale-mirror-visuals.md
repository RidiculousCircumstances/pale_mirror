# Pale Mirror Visuals

`pale-mirror-visuals` is the required physical-expression module for the private
Pale Mirror product profile. It is deliberately separate from the authoritative
core. Core owns settlement identity, population counts, economy, facilities,
routes, threats, scenarios and revisions. Visuals owns fresh-world layout,
curated structure assets, resident carriers, animation, particles and sound.

## Fresh-world contract

There is intentionally no retrofit path. A new v34 world pins three complete
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

## Runtime materialization contract

Genesis may author natural terrain only while a fresh chunk is being generated.
After that moment every persistent change is a Core-owned job with pinned
policy identity, desired revision and idempotent operations. Visuals supplies
assets and physical executors; it cannot mutate canonical state.

Authored space is divided into explicit parcels. Settlement influence is
read-only. Community and public-infrastructure parcels are PM-managed; reserved
plots and player leases are not. A player-authored asset becomes eligible for
PM repair or development only after an explicit commissioning permit converts
its parcel. Each mutable semantic slot stores exact baseline and last-applied
block states. Unknown edits conflict only that slot and are never overwritten
without a separate previewed reset permit.

Damage, reconstruction and positive development use the same mechanism.
Deterministic damage affects sparse authored shell slots; reconstruction restores
their exact captured block states in stages; a storehouse project commissions an
available reserved plot, then builds foundation, shell and roof under resource,
labour, loaded-chunk and postcondition gates. Canonical integrity or capability
changes only after the physical job completes.

## Residents and journeys

Evacuation and return are canonical `WorldJourney` records over persisted paths.
They advance and take deterministic full-risk losses while chunks are unloaded.
When a player is nearby, Visuals leases the same stable resident identities into
a physical travelling group and reports typed checkpoint/death observations.
The default private profile allows the physical limit to equal the whole group;
`journeys.maxMaterializedResidents` and `journeys.spawnBudgetPerTick` can reduce
load without changing canonical population or progress. Arrival releases leases;
a later return is a new reverse journey, not an entity teleport.

## Dynamic expression

Core publishes a small immutable projection of facility and settlement state.
It grants no domain mutation capability. Visuals currently expresses it as:

- a persistent GeckoLib Threat Heart in the primary mine chamber while the
  canonical facility is infected;
- four synchronized pulse/particle stages for `FOOTHOLD` through `APEX`;
- heartbeat-like sculk audio and infection particles;
- sparse depot smoke/angry cues during shortage or critical crisis;
- recovery and prosperity particles when the region stabilizes.

The Heart is the only visible controller in the product profile. Minecraft's
native health/death remains disabled: incoming attacks are attributed to PM's
persisted combat ledger, and PM alone decides stage, defeat and cleanup. This
avoids a second health pool while still making the Heart the object the player
actually fights. The old zombie anchor is available only in explicit core-only
development/GameTest profiles.

Shelter candidates and development plots are pinned at genesis. Player shelter
choice has priority; after the grace window settlement policy may choose a
degraded fallback. Camps, damage, reconstruction and development all reconcile
only loaded chunks and never force-load their physical destination.

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
