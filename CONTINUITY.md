# Continuity Ledger

## Goal (success criteria)

- Reduce full-modpack fresh-world genesis for 20 authored regions from ~174 seconds to at most 30 seconds.
- Use biome-footprint horizontal selection, bounded five-point settlement/mine anchors, and ready current-chunk heightmaps for local worldgen adaptation; railway planning performs zero terrain-height scans.

## Constraints/Assumptions

- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains free of Minecraft, NeoForge, persistence and adapters.
- Existing schema-v35 and older worlds are intentionally unsupported; the development server world will be replaced.
- The new product profile requires Pale Mirror Core, Pale Mirror Visuals, Supplementaries, GeckoLib 4.9.2 and exact Villager Overhaul 3.10.17.16. Create remains an industrial-upgrade integration.
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
- A pure hierarchical selector chooses all configured sites together. Cheap biome-footprint ranking precedes at most six exact center/cardinal settlement surveys per requested region and one exact anchor for each mine; the railway never scans terrain height. Hard map radius and minimum spacing remain enforced. The first region remains 1024–2048 blocks from spawn.
- Canonical railway elevation is a deterministic grade-safe interpolation between depot and mine anchors. Current-chunk `WORLD_SURFACE_WG` data controls only local cut/fill/support representation and never changes route identity or requests another chunk.
- Mine17 is 384–512 blocks from the freight gate; Red Valley is 512–768 blocks away. The complete primary route plan exists at region genesis and materializes as its chunks naturally generate/load.
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

- Added the required `pale-mirror-visuals` JAR and narrow experimental Core/Visuals SPI; schema v36 intentionally rejects all older worlds.
- Replaced the crash-prone loaded-chunk genesis materializer with an asynchronous immutable catalog and a chunk-local worldgen Feature. Static settlement, MineSite and baseline-rail geometry is no longer written from `ChunkEvent.Load` or the normal server tick.
- Added persisted chunk generation stamps, readiness/status SPI, player admission gating, authored MineSite adoption and worldgen-only baseline-rail provenance registration.
- Added the shared runtime work coordinator with configurable 3 ms / 256-op defaults, staggered cadences and operator telemetry.
- Replaced full chunk-volume rail scans with event-driven bounded graph traversal, full loaded-entity ambient counts with Minecraft `SpawnState`, repetitive visual projections with revision suppression, and repeated Threat Heart scans with UUID indexing.
- Kept all 48 authored residents visible while limiting expensive nearby vanilla AI to 16 role-prioritized carriers by default; the AI limit/radius are visual-only configuration.
- Added persisted immutable manifests, generator-only terrain survey, deterministic three-region grammar, three climate palettes, radial forts, six expansion plots and curated private NBT modules with provenance.
- Replaced per-region terrain searches with a configurable 1–64 region batch selector. The default remains three; a 20-region/10,000-block unit profile is deterministic, spacing-safe and capped below 6,000 unique survey samples.
- Replaced pointwise site/rail height scanning with cheap biome-footprint ranking, a hard 640-probe exact-height ceiling for 20 regions, two mine probes per region and zero rail probes. Rail elevation is interpolated between exact endpoints; naturally generating chunks use only their ready local heightmap for cut/fill/support representation.
- Replaced automatic observed-village campaign binding when Visuals is installed; core registers authored communities, places, facilities, sites, route contracts and exact 48-person PM-owned cohort state.
- Added stable resident UUID commissioning, canonical-growth-only breeding, confirmed-death reconciliation and the isolated exact Villager Overhaul guard/worker/recruitment bridge.
- Added the GeckoLib Threat Heart, four projected threat stages, infection sound/particles and depot crisis/recovery/prosperity cues. Core remains authoritative.
- Removed first-generation mine force-loading; authored settlement work, MineSites and baseline rail work advance from naturally loaded chunks only.
- Cut every long-lived representation over to the shared schema-v36 materialization registry, guarded gateway, exact semantic cells and parcel ledger.
- Added explicit influence/community/public-infrastructure/reserved/player-lease parcels; player work becomes PM-managed only through an explicit commissioning permit.
- Added canonical `WorldJourney`, deterministic off-screen travel/risk, exclusive stable-resident identity leases, bounded nearby physical groups and canonical return journeys.
- Made the GeckoLib Threat Heart the sole product controller backed by PM combat state; the zombie anchor remains core-only test infrastructure.
- Added reserved shelter candidates, selected/fallback refugee camps, staged authored damage, exact reconstruction and plot-based positive-development projects.
- Added terrain-costed, grade-safe persisted baseline rail paths with arbitrary turns, loaded-chunk construction and topology-based player reroute adoption.
- Added Visuals planner tests, 5 Visuals GameTests including deterministic chunk-local catalog compilation, powered-rail corner regression and biome-feature registration under exact Sable 2.0.3, JAR verification, and a clean packaged Core+Visuals two-start harness that exercises natural generation plus persisted stamps. All 37 Core GameTests and all 5 Visuals GameTests pass.
- Fixed the previously flaky multi-chunk player-reroute GameTest by observing both dirty chunks.
- Extracted the experimental SPI into `pale-mirror-api`; Visuals can no longer compile against domain or Core internals, while the final Core JAR explicitly embeds the API and domain outputs.
- Made `WorldState` collection views immutable and mutations package-private; production mutations cross `DomainCommandExecutor`/`DomainTransaction`, while a codec-only hydration builder rejects duplicate persisted identities.
- Added schema-v36 canonical and physical integrity validation on load and save, typed command failures, atomic aggregate registration and command-owned Narrator evaluation/reset/registration paths.
- Added a configurable ephemeral NeoForge ambient-spawn budget that strongly reduces natural hostiles and
  moderately reduces natural fauna without deleting existing entities or suppressing authored content.
- Bound every semantic slot to one explicit parcel, added postcondition rollback, rejected duplicate current materialization jobs and compacted terminal jobs into bounded receipts.
- Bounded detailed causal history and terminal journeys with persisted summaries; shared output now uses deterministic demand/weight allocation rather than route-ID priority.
- Split command registration, runtime combat and test-mine persistence out of oversized coordinators; added Java style, portable-path and 500-line debt gates.
- Verification: domain/unit and `check` pass; 37 Core and 5 Visuals GameTests pass; distribution JAR checks, clean packaged Core crash/restart and packaged Core+Visuals two-start harnesses pass.
- The previous schema-v35 playtest world is intentionally obsolete and must not be reused.

### Now

- The bounded-anchor optimization is implemented. Visual definition v4, catalog v2 and genesis SavedData
  schema v3 intentionally reject/rebuild every earlier Visuals world; the playtest world is disposable.
- A clean packaged Sable 20-region run on seed `3374619285067712046` planned all regions in 1.758 seconds:
  310 exact site probes, 40 mine probes, zero rail probes, 350 unique heights, 1,833 biome samples and
  2,900 compiled chunk slices. The former private-modpack run took 174.346 seconds and 4,868 heights.
- Commit `2515df7` is published to the client artifact host and deployed to the private playtest server.
  A clean full-modpack world on seed `-3621189047412558097` planned 20 regions in 8.289 seconds using
  270 site probes, 40 mine probes, zero rail probes and 310 unique heights; its 2,924-slice catalog is ready.
  The closest authored region is centered at `1160 65 -648`.
- Deterministic selection, spacing, fail-closed terrain handling, the combined 640-probe hard ceiling,
  exactly two mine queries per region, grade-safe rail interpolation and current-column earthwork have
  focused regression coverage. Core and Visuals GameTests plus packaged restart harnesses pass.
- The 30-second full-modpack planning gate is closed with 21x observed speedup over the former 174-second run.
- A same-world service restart reopened catalog `7a0bc3d129abce10` with all 2,924 slices and no
  `Batch-planned` pass; persisted manifests therefore avoid terrain planning after the first start.

### Next

- Traverse cut/fill/bridge sections in a graphical client and tune their visual policy without widening
  planning probes.

## Open questions

- UNCONFIRMED: final visual quality and playability until a real client visits naturally generated temperate/cold/dry regions after the v36 cutover.
- The exact VO public surface plus one fail-closed recruitment mixin loads in GameTest and packaged server; live patrol/combat quality still needs client playtesting.
- Public redistribution remains out of scope; re-audit all imported asset and dependency licences before changing that assumption.

## Working set

- `AGENTS.md`, `architecture.yml`, `settings.gradle`, `build.gradle`
- `pale-mirror-api`, domain `WorldState`/commands/history, `PaleMirrorSavedData`, materialization ledgers/gateway/jobs
- `PaleMirrorRuntime`, `PaleMirrorEvents`, resource flow and projection publishers
- `pale-mirror-visuals`, authored-region manifest/genesis, residents, VO bridge and Threat Heart
