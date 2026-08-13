# Pale Mirror Visuals

`pale-mirror-visuals` is the required physical-expression module for the private
Pale Mirror product profile. It is deliberately separate from the authoritative
core. Core owns settlement identity, population counts, economy, facilities,
routes, threats, scenarios and revisions. Visuals owns fresh-world layout,
curated structure assets, resident carriers, animation, particles and sound.

## Fresh-world contract

There is intentionally no retrofit path. A new v39 world pins the configured
set of complete `AuthoredRegionSeed` manifests and compiles them into an
immutable chunk-addressed catalog before players are admitted. The default is
three regions. The first is 1024–4096 blocks from spawn; the remainder are
distributed inside the configured map radius with a hard minimum spacing.
The immutable manifest contains every object identity and coordinate needed by
core: settlement bounds, freight gate, receiving depot, typed Mine17 and Red
Valley blueprints, baseline rail path, modules, expansion plots and 48 resident
identities. Each MineSite pins a portal, loading endpoint, controller anchor,
orientation, complete bounds, independently surveyed local foundations,
initial/staged modules and semantic volumes.

Terrain survey calls generator-only biome and bounded height APIs on a dedicated
planning worker. It does not load prospective chunks. Cheap biome-footprint checks
rank deterministic horizontal candidates. Each accepted region then receives
one exact center/cardinal settlement survey and two bounded mountain-face
searches; at most six site surveys and 24 exact candidates per MineSite may be
attempted. A mine is accepted only when every surface-module pad is dry, has at
most ten blocks of relief and fits within the four-block cut/six-block fill
limits, while its portal retains mountain-biome evidence and a sampled
continuous rise of at least 32 blocks. Mine17 is 160–320 blocks
from the fort; Red Valley is 320–560 blocks away in another cardinal mountain
search. Railway planning runs a bounded deterministic corridor search which
strongly prefers dry land, verifies every final rail column and permits only
water spans of at most 24 blocks with two blocks of bridge clearance. Grades
steeper than one-in-four are rejected. During natural generation each chunk
uses only its own ready heightmaps and fluid state to choose local cut, fill and bridge
supports. Impossible count/radius/spacing or
terrain combinations fail closed instead of overlapping sites. The relevant `pale-mirror-visuals-server.toml`
settings are:

The reusable placement engine consumes an immutable `RegionPlacementProfile`.
The profile, rather than the iron layout grammar, owns spawn search bands,
candidate and exact-query budgets, settlement footprint tolerances, typed site
distance/terrain requirements, cross-site direction constraints and route grade
policy. `RegionPlacementProfiles.IRON_FRONTIER` preserves the contract above.
Selectors and Minecraft terrain survey receive that profile explicitly; an
unknown site role, broken cross-site reference or layout/profile mismatch fails
when the profile or planner is constructed. A later agricultural or coastal
archetype therefore supplies another profile and matching content grammar rather
than copying the mountain selector or adding conditional constants to it.

Cheap mountain evidence ranks dry buildable candidates but does not reject them
on its own. Bounded exact validation remains authoritative for the two distinct
dry mine sectors, loading aprons and continuous rise; the biome heuristic is
deliberately not a duplicate proof of those physical requirements.

```toml
[genesis]
minimumRegions = 3
targetRegions = 5
maximumRegions = 6
mapRadius = 20000
minimumSpacing = 2500
```

Genesis fails only below `minimumRegions`, reports whether `targetRegions` was
met and may accept up to `maximumRegions` when equally valid sites exist. This
keeps geography authoritative instead of weakening terrain constraints to fill
an exact quota. The iron profile separates this six-region output cap from a
bounded 160-center feasibility survey because a complete dry dual-mountain
region is intentionally rare. Reserve centers use only the profile envelope for
survey distribution; `minimumSpacing` is enforced between accepted regions, so
discarded candidates cannot exclude unexplored geography. `mapRadius` constrains PM-authored centers; a Minecraft world
border remains a separate server policy. A registered NeoForge
worldgen feature applies only the current chunk's precompiled slice during normal
generation, then persists a chunk attachment stamp. `ChunkEvent.Load` performs
read-only stamp observation and resident commissioning; it never edits terrain,
structures, MineSites or rails. The manifest and observed-stamp ledger survive
restart, with no tickets, retrofit pass or loaded-chunk bulk construction.

Settlement preparation also compiles a six-block cleanup-only halo. The halo does
not flatten or claim neighbouring ground; during fresh chunk generation it only
removes natural tree crowns and attachments up to 64 blocks above the settlement
base without trusting the post-grading surface heightmap. Logs, leaves, saplings, flowers, vines, cocoa, moss, cave vegetation and
tagged beehives are included. This prevents a trunk cut by the fort footprint
from leaving floating foliage or bee nests just outside the authored site.

## Settlement grammar

The current `iron_frontier` grammar produces a compact, terrain-led industrial
frontier address of roughly 90×130 blocks and 16 primary buildings. It has no
radial-fort fallback and does not flatten one global platform. The bounded
terrain survey chooses one of three reusable layout archetypes:

- `FOOTHILL_RIBBON`: a freight spine follows the buildable shelf and buildings
  form an asymmetric linear town;
- `TERRACED_BASIN`: local foundations occupy two or three shallow tiers joined
  by stairs and retaining walls;
- `FREIGHT_CROSSROADS`: civic and industrial streets meet the baseline freight
  route on a broad low-relief site.

Each curated module has a stable instance ID, one independently bounded local
foundation, a state profile and typed public/service/freight/rail ports. The
planner proves non-overlap, connects every public entrance to the circulation
graph, reserves six actually free 11×11 development parcels, and emits typed
streets, paths, stairs, freight roads, ditches, retaining walls and segmented
palisades. Defences protect exposed approaches and terminate in walkable watch
posts instead of drawing an impermeable geometric circle around the community.

Temperate, cold-taiga and dry-arid settlements share functional roles but not
only a block substitution. Their freight threshold gains climate-specific
planting, snow/windbreak or shaded water-stop forms. Copper and soul-lantern
freight marks provide a restrained common Pale Mirror identity kit. Depot and
MineSite power buildings may contain one small isolated Create kinetic display;
it is presentation only and never a second economy or route simulation.

Temperate and cold variants use normalized private Integrated Villages-derived
NBT modules. Required modules are fail-closed and SHA-pinned; there is no silent
fallback to a generic box. The original private inputs and hashes are recorded
in [`VISUAL_ASSET_PROVENANCE.md`](VISUAL_ASSET_PROVENANCE.md).

## Mountain MineSites

Mine17 is a compact frontier-industrial mountain complex rather
than a surface cube. Its composed grammar includes a timber portal headframe,
headframe, processing hall, power house and chimney, crew office, loading shed,
freight building, supported descending adit, iron gallery and a deep controller
chamber. Every surface building has its own bounded foundation and two-block
apron. Three-wide gravel paths, steps and short supports connect those levels;
the natural terrain between modules remains untouched. The drift and chambers
continue into the selected mountain face inside a continuous dry masonry shell.

The visible campus remixes the pinned standalone Integrated Villages workshop,
residence, stable and depot assets into a common frontier-industrial language;
PM adds the headframe, chimney, loading platform, foundations and paths.
Dungeons Arise, IDAS and Terralith assets are restricted to underground adit,
gallery and controller spaces and compiled through one sanitizer. Entities and
block-entity payloads are ignored. Spawners, TNT and structure machinery become
air; inventories become inert timber; ore/raw-resource cells become ordinary
deepslate. Consequently visible ore never mints canonical IRON and ordinary
containers never mirror settlement stock. Create blocks that survive this
filter are visual/kinetic machinery only and carry no imported inventories or
contraptions.

Red Valley starts as a small operational adit, not a finished freight factory.
Its dispatch endpoint is canonical `DEGRADED`. After Red Valley is known and
Ironhill requests supply, Atlas may submit the explicit “Build freight works”
decision. The domain atomically reserves 12 canonical IRON; Core then applies
the Visuals-compiled foundation, shell, machinery and commissioning snapshots
through bounded semantic slots. The endpoint becomes `OPERATIONAL` only after
all four physical stages finish. A Create route still has zero canonical
capacity until the ordinary adapter observes the same real train at both
registered endpoints inside its proof window.

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

All 48 carriers remain physically visible when their chunks are loaded, while
vanilla AI is a separate visual budget. By default at most 16 nearby residents
tick AI, prioritized by guards, specialists and workers; the remainder use
`NoAI` until budget becomes available. `residents.activeAiLimit` and
`residents.activeAiRadius` tune this without changing canonical population.

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

Damage, reconstruction, Red Valley commissioning and positive development use the same mechanism.
Deterministic damage uses bounded module-specific shell overlays rather than a
random settlement-wide sample; ruined landmarks have a larger authored mask than
damaged ones. Reconstruction restores the exact captured baseline. A storehouse
project commissions an available reserved plot and compiles a real climate-family
workshop module under resource, labour, loaded-chunk and postcondition gates.
Canonical integrity or capability changes only after the physical job completes.

Overlapping construction stages do not weaken player-edit protection. Every future
stage captures its fresh-world baseline before work starts; after a stage completes,
Core advances only the exact PM-authored postconditions shared with later slots.
Any other block state remains an unknown edit and blocks that slot fail-closed.

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
- a rebuilt climate-family annex on positive development and a 17×17 physical
  refugee camp with tents, bedrolls, fire, water point and administration awning;
- a one-shot raw-iron cargo cue at Red Valley and the receiving depot when the
  alternate dispatch works becomes operational; any persistent Create kinetics
  are physical presentation, never a second resource simulation.

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
