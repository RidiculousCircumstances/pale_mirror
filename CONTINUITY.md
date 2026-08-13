# Continuity Ledger
## Goal (success criteria)
- Keep every PM-authored settlement influence column free of new tree-like feature growth and background
  hostile spawning, and remove natural vegetation left above its freshly graded terrain.
- Keep operator navigation non-blocking: a teleport into a prospective authored chunk must use one bounded
  asynchronous ticket and may query its safe surface only after the chunk is ready.
- Keep full-modpack fresh-world genesis bounded and fail closed; one strict mountain region must plan within 30 seconds. A separate density redesign is required before restoring the 20-region/10,000-block target.
- Use biome-footprint horizontal selection, bounded five-point settlement surveys, dry-footprint mine resolution, and ready current-chunk heightmaps for local worldgen adaptation; railway planning performs zero terrain-height scans.

## Constraints/Assumptions
- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains free of Minecraft, NeoForge, persistence and adapters.
- Every pre-v37 world is intentionally unsupported; the development server world will be replaced.
- The product profile requires Pale Mirror Core, Pale Mirror Visuals, Create 6.0.10, Supplementaries, GeckoLib 4.9.2 and exact Villager Overhaul 3.10.17.16.
- Millénaire is absent from the active private-pack profile after an observed 32-second village-chunk stall;
  its read-only adapter remains an optional compatibility surface and reports `ABSENT` by design.
- Ordinary loaded ecology is intentionally sparse: ambient hostile attempts use a 0.20 acceptance chance and
  cap 24 per active player/dimension, while passive/ambient/water attempts use 0.55 and cap 32. Explicit PM,
  resident, breeding, structure, spawner, event and command spawns are outside this policy.
- Integrated Villages ARR structures may be copied and remixed for this private non-distributed mod; every imported template retains an origin/version/hash manifest.
- Crimson 1.4.3.1 is the default infection source; Spore remains optional/test-only.

## Key decisions
- Core owns canonical state, revisions, persisted jobs, provenance and reconciliation. Visuals owns worldgen, templates, palettes, render assets and physical behavior providers, and owns no canonical SavedData.
- Immutable genesis terrain is produced by a registered worldgen Feature from precompiled chunk-local slices. Login fails closed until the catalog is ready; loaded-chunk events observe stamps but never construct static geometry. Every post-gen canonical state transition uses persisted materialization jobs and never overwrites unknown player changes.
- Non-critical runtime work shares a configurable 3 ms / 256 weighted-operation admission budget. Cadence is staggered, unchanged projections are suppressed, railway health is event-driven, and `/pale_mirror performance` exposes per-work timing and deferral metrics.
- The settlement generator uses a bounded deterministic hybrid grammar: procedural radial plan and road graph plus curated authored NBT modules. It does not use an unconstrained jigsaw walk.
- The fort has a civic core, two functional rings, wooden palisade, freight gate/depot and at least six reserved development plots. Nominal diameter is 176 blocks and maximum footprint 192.
- Terrain placement prefers dry sites with sampled relief at most 8 blocks, rejects water/extreme terrain, and permits a bounded dry fallback up to 24 blocks only after the preferred candidate fails.
- One immutable `RegionPlacementProfile` owns search bands/budgets, settlement terrain, typed site constraints/relations and route policy; layout grammars consume it explicitly. The iron profile preserves bounded foothill ranking, six exact settlement surveys, sixteen reserve centers, two dry mountain sites with 32-block rise/24 exact validations, nine rail alternatives, one-in-four grade, map spacing and a first region 1024–4096 blocks from spawn.
- Canonical railway geometry uses three bounded dry control-point surveys and a grade-safe path between depot and loading endpoint. Current-chunk `WORLD_SURFACE_WG` data controls local cut/fill/support representation and never changes route identity or requests another chunk.
- A region is accepted only when its shelf has two dry mountain-native MineSites. Mine17 is 160–320 blocks from the settlement; Red Valley is 320–560 blocks away. Both require nearby mountain-biome evidence and a continuous sampled 32-block rise. The complete primary route plan exists at region genesis and materializes as its chunks naturally generate/load.
- Three initial climate families share one grammar and role catalog but use climate palettes and local variants: temperate, cold/taiga and dry/arid.
- The starting population is 48: 20 civilians, 14 workers, 4 specialists, 6 guards and 4 children. Each has stable identity, name, home and workplace/patrol assignment.
- Vanilla villagers are physical carriers. Exact Villager Overhaul supplies loaded guard/farmer behavior through one isolated fail-closed adapter; PM owns roles, population, defence, deaths and off-screen outcomes.
- PM residents may trade but cannot be player-recruited. Breeding is canonical-growth-only. Missing entities are not deaths.
- Ordinary containers are non-canonical. Only managed depot/storehouse endpoints cross the economy boundary through transfer receipts.
- Player edits are allowed. Dynamic visual layers change only semantic mutable cells and conflict locally rather than restoring authored blocks.
- The technical zombie controller is replaced by a GeckoLib PM Threat Heart; PM continues to own health, stages and cleanup.
- Product-profile startup fails closed before opening a world when Visuals, pinned visual dependencies or the asset catalog are unavailable/incompatible. Core-only is an explicit dev/test profile.
- Common materialization jobs, retries, revisions and the guarded world-mutation gateway belong to Core; Visuals compiles semantic operations and supplies physical executors/assets.
- Authored sites use layered policies and zoned parcel ownership. Player changes conflict at semantic-slot granularity; commissioning is explicit.
- Population movement is a generic persisted `WorldJourney`: off-screen progress and full risk are canonical, while nearby stable residents materialize under identity leases. The default materialization limit equals population and is configurable.
- Shelter candidates are reserved at genesis. Player choice takes priority; after the grace window settlement policy chooses a degraded fallback.
- Damage is deterministic and policy-driven. Reconstruction is a joint staged project and may restore pristine authored state; positive development consumes resources/labour on soft reserved plots.
- Resource production shared by multiple routes is allocated deterministically by actual demand and an explicit route weight; route identity is only a remainder tie-breaker.
- Causal history retains a bounded detailed window plus compact per-subject/type summaries. Terminal journeys and materialization jobs have bounded receipt/summary retention.
- The Visuals compile boundary is a dedicated `pale-mirror-api` module; documentation-only separation is insufficient.

## State
### Done
- Added the required `pale-mirror-visuals` JAR and narrow experimental Core/Visuals SPI; schema v37 intentionally rejects all older worlds.
- Replaced the crash-prone loaded-chunk genesis materializer with an asynchronous immutable catalog and a chunk-local worldgen Feature. Static settlement, MineSite and baseline-rail geometry is no longer written from `ChunkEvent.Load` or the normal server tick.
- Added persisted chunk generation stamps, readiness/status SPI, player admission gating, authored MineSite adoption and worldgen-only baseline-rail provenance registration.
- Added the shared runtime work coordinator with configurable 3 ms / 256-op defaults, staggered cadences and operator telemetry.
- Replaced full chunk-volume rail scans with event-driven bounded graph traversal, full loaded-entity ambient counts with Minecraft `SpawnState`, repetitive visual projections with revision suppression, and repeated Threat Heart scans with UUID indexing.
- Kept all 48 authored residents visible while limiting expensive nearby vanilla AI to 16 role-prioritized carriers by default; the AI limit/radius are visual-only configuration.
- Added persisted immutable manifests, generator-only terrain survey, deterministic three-region grammar, three climate palettes, radial forts, six expansion plots and curated private NBT modules with provenance.
- Replaced per-region terrain searches with a configurable 1–64 region batch selector. The default remains three; a 20-region/10,000-block unit profile is deterministic and spacing-safe while exact height work remains bounded separately from cheap biome sampling.
- Replaced pointwise site/rail height scanning with profile-driven cheap biome ranking, bounded exact settlement/site validation and bounded route controls. Iron MineSites reject wet yards and flat terrain; naturally generating chunks use only their ready local heightmap for local representation.
- Replaced automatic observed-village campaign binding when Visuals is installed; core registers authored communities, places, facilities, sites, route contracts and exact 48-person PM-owned cohort state.
- Added stable resident UUID commissioning, canonical-growth-only breeding, confirmed-death reconciliation and the isolated exact Villager Overhaul guard/worker/recruitment bridge.
- Added the GeckoLib Threat Heart, four projected threat stages, infection sound/particles and depot crisis/recovery/prosperity cues. Core remains authoritative.
- Removed first-generation mine force-loading; authored settlement work, MineSites and baseline rail work advance from naturally loaded chunks only.
- Cut every long-lived representation over to the shared schema-v37 materialization registry, guarded gateway, exact semantic cells and parcel ledger.
- Added explicit influence/community/public-infrastructure/reserved/player-lease parcels; player work becomes PM-managed only through an explicit commissioning permit.
- Added canonical `WorldJourney`, deterministic off-screen travel/risk, exclusive stable-resident identity leases, bounded nearby physical groups and canonical return journeys.
- Added reserved shelter candidates, selected/fallback refugee camps, staged authored damage, exact reconstruction and plot-based positive-development projects.
- Added terrain-costed, grade-safe persisted baseline rail paths with arbitrary turns, loaded-chunk construction and topology-based player reroute adoption.
- Added Visuals planner tests, 7 Visuals GameTests including deterministic chunk-local compilation, blueprint sanitizing, powered-rail corners, vegetation cleanup and biome-feature registration under exact Sable 2.0.3, plus JAR and packaged two-start verification. All 39 Core and 7 Visuals GameTests pass.
- Made `WorldState` collection views immutable and mutations package-private; production mutations cross `DomainCommandExecutor`/`DomainTransaction`, while a codec-only hydration builder rejects duplicate persisted identities.
- Added schema-v37 canonical and physical integrity validation on load and save, typed command failures, atomic aggregate registration and command-owned Narrator evaluation/reset/registration paths.
- Added a configurable ephemeral NeoForge ambient-spawn budget that strongly reduces natural hostiles and
  moderately reduces natural fauna without deleting existing entities or suppressing authored content.
- Added indexed settlement-influence ecology protection: natural/chunk-generation/patrol monsters and
  tree-like growth are rejected across the full vertical column, without granting block ownership or
  suppressing explicit PM encounters/spawners. Fresh genesis removes bounded natural vegetation before modules.
- Bound every semantic slot to one explicit parcel, added postcondition rollback, rejected duplicate current materialization jobs and compacted terminal jobs into bounded receipts.
- Bounded detailed causal history and terminal journeys with persisted summaries; shared output now uses deterministic demand/weight allocation rather than route-ID priority.
- Replaced blocking debug teleport height queries with a single bounded asynchronous FULL-chunk ticket; requests
  report progress, cancel on disconnect/replacement/timeout/shutdown, and teleport only after readiness.
- Extended fresh settlement cleanup with a six-block cleanup-only halo and fixed 64-block vertical scan for tree crowns,
  logs, vines, cocoa, moss, cave plants and tagged beehives without grading or claiming neighbouring ground. The
  scan deliberately ignores the post-grading surface heightmap, which may already have collapsed below leftover foliage.

### Now
- The mountain-native mine cutover and reusable placement-profile boundary are implemented. Core schema v37, Visual definition v6, catalog v7 and
  genesis SavedData schema v6 intentionally reject every earlier world; the playtest world is disposable.
- `AuthoredMineSitePlan` now pins each portal, loading endpoint, controller, bounds, orientation, initial/staged
  modules and semantic volumes. Mine17 composes a large industrial mountain complex from pinned private
  Dungeons Arise/IDAS/Terralith assets; imported entities, block-entity data, loot, explosives, spawners and ore are inert.
- Red Valley starts as a small adit with a degraded dispatch endpoint. A discovered supply crisis exposes an
  Atlas decision that reserves 12 canonical IRON and builds foundation, shell, Create machinery and commissioning
  stages through Core semantic jobs. Overlapping stages pass forward only their exact PM-owned postconditions;
  player changes remain guarded. Route proof and resource flow remain blocked until the site is operational.
- The full private modpack seed `3374619285067712046` planned one strict mountain-native region in
  18.230 seconds: 465 exact site probes, 370 mine probes, 9 rail probes, 660 unique heights and 78,031
  cheap biome samples. Its authored settlement is `2408 75 3320`; catalog readiness is confirmed.
- The same strict profile fails closed at 3/10,000 and 20/10,000 on this seed because too few surveyed
  centers have two valid dry mountain sectors. Scaling density now requires a planner/profile redesign,
  not more height probes or silently weaker terrain constraints.
- Deterministic selection, spacing, fail-closed mountain terrain, dry MineSites, grade-safe rail interpolation,
  blueprint sanitizing and current-column earthwork have focused regression coverage.
- Atlas cards render known settlement coordinates from the server-owned projection. Unit/check/guardrail/JAR
  gates, 39 Core plus 7 Visuals GameTests, and the packaged Visuals two-start harness pass for settlement
  territory protection and genesis cleanup.

### Next
- Run a fresh graphical-client seed matrix for foothill selection, mine/rail composition, isolated Create kinetics,
  Red Valley staged construction and real-train proof.

## Open questions
- UNCONFIRMED: final visual quality and playability until a real client visits naturally generated temperate/cold/dry mountain regions after the v37 cutover.
- The exact VO public surface plus one fail-closed recruitment mixin loads in GameTest and packaged server; live patrol/combat quality still needs client playtesting.
- Public redistribution remains out of scope; re-audit all imported asset and dependency licences before changing that assumption.

## Working set
- `AGENTS.md`, `architecture.yml`, Gradle build
- `pale-mirror-api`, domain state/commands/history, SavedData/materialization, runtime/events and projections
- `pale-mirror-visuals`, authored-region manifest/genesis, residents, VO bridge and Threat Heart
