# Pale Mirror Visuals

`pale-mirror-visuals` is the required physical-expression module for the private
Pale Mirror product profile. It is deliberately separate from the authoritative
core. Core owns settlement identity, population counts, economy, facilities,
routes, threats, scenarios and revisions. Visuals owns fresh-world layout,
curated structure assets, resident carriers, animation, particles and sound.

## Fresh-world contract

There is intentionally no retrofit path. A new v40 world pins the configured
set of complete `AuthoredRegionSeed` manifests and compiles them into an
immutable chunk-addressed catalog before players are admitted. The default requires
at least three, targets five and caps output at six regions. The first is 1024–4096 blocks from spawn; the remainder are
distributed inside the configured map radius with a hard minimum spacing.
The immutable manifest contains every object identity and coordinate needed by
core: settlement bounds, freight gate, receiving depot, typed Mine17 and Red
Valley blueprints, baseline rail path, functional buildings, open spaces,
development reservations and 48 resident identities with exact slot bindings.
Each MineSite pins a portal, loading endpoint, controller anchor, orientation,
complete bounds, semantic surface buildings, underground modules, independently
surveyed local foundations, staged modules and semantic volumes.

Terrain survey calls generator-only biome and bounded height APIs through one
dedicated coordinator and a configurable bounded worker pool. It does not load
prospective chunks. Candidate ranking and complete feasibility attempts may run
concurrently, but their results remain index-addressed; one sequential reducer
alone assigns region ordinals, applies spacing and accepts manifests. Cheap biome-footprint checks
rank deterministic horizontal candidates. Each accepted region then receives
one exact center/cardinal settlement survey and two bounded mountain-face
searches; at most 24 exact candidates per MineSite may be attempted. Once the
primary mine is accepted, Township planning captures a 130-column coarse height
snapshot on a sixteen-block grid, tries at most 24 deterministic grammar variants
and admits at most 768 coarse and exact probes together. Every one of its twenty
building pads receives exact nine-column validation and may cut or fill at most
four blocks. A mine is accepted only when every surface-building pad is dry, has
at most eight blocks of relief and fits within four-block cut/fill limits, while
its portal retains mountain-biome evidence and a sampled
continuous median rise of at least ten blocks over 48 blocks. Mine17 is 160–320 blocks
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
plannerWorkers = 6
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
generation, then persists a chunk attachment stamp. Terrain-relative furniture
is deliberately deferred to one post-decoration finalizer. That finalizer
captures one stable height datum per column, deduplicates semantic `(x,z,offset)`
slots and is protected by a separate persistent finalization stamp. Lamps,
fences and planters therefore cannot become the heightmap datum for a second
copy of themselves. `ChunkEvent.Load` performs stamp reconciliation and resident
commissioning; it never replans terrain, structures, MineSites or rails. The
manifest and observed-stamp ledger survive restart, with no tickets, retrofit
pass or loaded-chunk bulk construction.

Settlement preparation also compiles a six-block cleanup-only halo. The halo does
not flatten or claim neighbouring ground; during fresh chunk generation it scans
from below the planned datum through the original world-surface top, before grading,
so arbitrarily tall crowns and attachments cannot survive a lowered pad. Logs, leaves, saplings, flowers, vines, cocoa, moss, cave vegetation and
tagged beehives are included. This prevents a trunk cut by the fort footprint
from leaving floating foliage or bee nests just outside the authored site.
Mine17 applies the same original-surface rule over an organic union around its
six surveyed pads. Its wider 18-block crown allowance removes large modded-tree
overhangs from the working yard without turning the campus into a rectangular
clear-cut or claiming the surrounding forest.

## Settlement grammar

The current `iron_frontier` grammar produces a terrain-led industrial Township
inside a 145×193 master envelope with an active irregular footprint of roughly
120×160 blocks and exactly 20 functional buildings. It has no
radial-fort fallback and does not flatten one global platform. The bounded
terrain survey chooses one of three reusable layout archetypes:

- `FOOTHILL_RIBBON`: a freight spine follows the buildable shelf and buildings
  form an asymmetric linear town;
- `TERRACED_BASIN`: local foundations occupy two or three shallow tiers joined
  by stairs and retaining walls;
- `FREIGHT_CROSSROADS`: civic and industrial streets meet the baseline freight
  route on a broad low-relief site.

Each building has a stable ID, category, namespaced functions, one independently
bounded local foundation, semantic interior slots, a state profile and typed
public/service/freight/rail ports. Foundations retain a flat structural footprint,
cut/fill no more than four blocks and blend over a bounded sixteen-block apron.
Ordinary streets, sidewalks, plazas and open spaces follow microrelief rather than
forcing a second terrace. The planner proves non-overlap, connects every public
entrance to circulation, emits six typed open spaces, reserves five free future
parcels plus depot/smeltery annex easements and derives an irregular managed-area
union. Mixed cobblestone/andesite/stone-brick streets, distinct sidewalks,
paths, stairs, freight roads, ditches, retaining walls, terrain-following lamps,
planters and partial low wall/fence defences make the address legible without
drawing an impermeable geometric circle around it. Reserved future parcels use
survey stakes and small material stores, so their emptiness reads as planned
development rather than unfinished generation.

Public-realm paving is functional microgeometry, not a texture swap. Each
climate owns compatible full-block, slab and stair states. Sidewalks, footpaths,
market edges and street shoulders use half-block profiles, while actual tier
changes compile oriented stair blocks. Street furniture uses region-global
occupancy reservations against buildings, circulation and railway cells; a
lamp/planter pair is accepted or rejected as one assembly. Hanging lamps declare
real hanging support and raised fences must have vertical or horizontal support.

The static Township composition is: town hall, market hall, inn, clinic, bakery,
receiving depot, smeltery, smithy, mechanical workshop, stable, assay office,
barracks, watch house, five family houses and two worker bunkhouses. Six open
spaces provide a market square, community green, allotments, freight court,
smeltery yard and training yard. Freight and industry face Mine17, civic/market
functions occupy the center and housing occupies the quieter rear shelf.

The versioned archetype catalog also validates coherent population/building
snapshots for Prospecting Post (12), Mining Camp (24), Township (48) and Mining
Town (72). Only Township materializes in v40: there is deliberately no runtime
growth or stage-transition implementation. The five parcels and two annex
easements reserve the future 72-person masterplan without pretending it already
exists.

Temperate, cold-taiga and dry-arid settlements share functional roles but not
only a block substitution. Their freight threshold gains climate-specific
planting, snow/windbreak or shaded water-stop forms. Copper and soul-lantern
freight marks provide a restrained common Pale Mirror identity kit. Depot and
MineSite power buildings may contain one small isolated Create kinetic display;
it is presentation only and never a second economy or route simulation.

Temperate, cold-taiga and dry-arid variants are real addressable asset families.
Cold variants remap timber and masonry toward spruce/deepslate; dry variants use
acacia and sandstone families while preserving compatible block-state properties.
Required modules are fail-closed and SHA-pinned; there is no silent fallback to
a generic box. The original private inputs and hashes are recorded
in [`VISUAL_ASSET_PROVENANCE.md`](VISUAL_ASSET_PROVENANCE.md).

## Mountain MineSites

Mine17 is a complete frontier-industrial mountain campus rather than a surface
cube. Its composed grammar includes a large portal/hoist works, processing hall,
power house, crew outpost, loading yard, maintenance workshop, supported descending
adit, iron gallery and a deep controller chamber. Every surface building has a
stable semantic identity, work slots and its own bounded foundation
and two-block apron. Three-wide gravel/cobble paths, steps, lamps and short safety
fences connect those levels; graded pads transition smoothly back into natural terrain. The drift and chambers
continue into the selected mountain face inside a continuous dry masonry shell.

The five principal surface landmarks are generated from the reproducible
PM-authored frontier-industrial kit in
`scripts/generate-authored-mine-landmarks.py`; the maintenance workshop remains
a curated private Integrated Villages derivative. PM composes them with an
irregular gravel/coarse-dirt working yard, drainage seams, open ore-sort bins,
timber/material stores, safety lighting, local foundations, circulation and
climate transforms. The underground adit, gallery and controller spaces remain
curated private source modules and use the same sanitizer. Entities and
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

Each carrier has a stable UUID, region, cohort, namespaced role, exact home
building/bed slot and exact workplace or patrol building/slot binding.
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

The dedicated visual GameTests cover manifest NBT round-trip, Threat Heart
identity/stage persistence and an exhaustive structural matrix of 216 public
realms (three climates, three layout archetypes and 24 bounded variants). The
matrix rejects duplicate surface slots, missing slab/stair articulation and
unsupported lantern/fence assemblies. A separate semantic-camera test proves
the 25-view grammar across all nine climate/archetype representatives.

Graphical model, texture, animation, audible range, building composition and
terrain quality remain a real-client gate rather than a pixel-diff assertion.
[`settlement-visual-audit.md`](settlement-visual-audit.md) describes the
automated street-level capture and contact-sheet workflow used for that review.
