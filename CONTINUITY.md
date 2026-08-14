# Continuity Ledger
## Goal (success criteria)
- Cut fresh-world visuals to schema v40 and make `iron_frontier` a reusable, typed authored-settlement framework: a static 48-person Township with coherent functional districts, semantic interiors, terrain-led streets/open spaces, compact industrial Mine17/Red Valley campuses and three real climate families.
- Define validated 12/24/48/72 composition snapshots and reserve the 72-person masterplan, but do not implement settlement stage transitions, population growth or stage construction in this cut.
- Keep live unexplored-world flight responsive: C2ME owns bounded parallel chunk generation while DH remains a client-side LOD cache by default; the measured server cache is benchmark-only.
- Keep authored settlement columns free of tree growth, background hostile spawning and grading debris; keep debug teleport non-blocking through one bounded asynchronous chunk ticket.
- Keep full-modpack fresh-world genesis bounded and geography-led: require 3, target 5 and cap 6 strict mountain regions inside a 20,000-block radius without weakening site constraints.
- Keep that production genesis deterministic while using bounded multithreaded terrain ranking and feasibility waves; repeat clean starts must produce byte-identical manifests.
- Use biome-footprint horizontal selection, bounded settlement/mine surveys, dry-preferring rail graph search, and ready current-chunk height/fluid data for local worldgen adaptation.
## Constraints/Assumptions
- Java 21 bytecode/toolchain; Minecraft 1.21.1; NeoForge 21.1.248. The private dedicated server runs Java 22; clients and PM artifacts remain Java 21 compatible.
- `pale-mirror-domain` remains free of Minecraft, NeoForge, persistence and adapters.
- Every pre-v40 world is intentionally unsupported; the development server world will be replaced.
- The product profile requires Pale Mirror Core, Pale Mirror Visuals, Create 6.0.10, Supplementaries, GeckoLib 4.9.2 and exact Villager Overhaul 3.10.17.16.
- Millénaire is absent from the active private-pack profile after an observed 32-second village-chunk stall; its read-only adapter remains an optional compatibility surface and reports `ABSENT` by design.
- Ordinary loaded ecology is intentionally sparse: ambient hostile attempts use a 0.20 acceptance chance and
  cap 24 per active player/dimension, while passive/ambient/water attempts use 0.55 and cap 32. Explicit PM,
  resident, breeding, structure, spawner, event and command spawns are outside this policy.
- Integrated Villages ARR structures may be copied and remixed for this private non-distributed mod; every imported template retains an origin/version/hash manifest.
- Crimson 1.4.3.1 is the default infection source; Spore remains optional/test-only.
## Key decisions
- Schema v40 replaces string/index-based building semantics with stable building, function, slot, open-space and resident bindings. Core remains authoritative; the stage catalog and physical plans are Visuals-owned immutable genesis facts.
- Only `TOWNSHIP` materializes. Prospecting Post (12), Mining Camp (24) and Mining Town (72) are validated composition requirements without runtime transitions. The future Town uses five reserved parcels plus depot/smeltery annex easements.
- The active Township has exactly 20 functional buildings, six typed open spaces, fully semantic interiors, an irregular managed footprint and a nearly complete terrain-following enclosure with four semantic gates. Freight/industry faces the mine, civic/market forms the center, housing occupies the quieter rear shelf.
- Detailed settlement terrain work is lazy and bounded after primary-mine acceptance: one cached coarse snapshot, deterministic grammar variants, exact pad validation and reserve-candidate rejection. Pads cut/fill at most four blocks; no bad site is rescued by destructive grading.
- Temperate, cold-taiga and dry-arid are independent visual families. Mine17 and compact Red Valley share the same building semantics and frontier-industrial art bible; curated private NBT has provenance and no generic-box fallback.
- Core owns canonical state, revisions, persisted jobs, provenance and reconciliation. Visuals owns worldgen, templates, palettes, render assets and physical behavior providers, and owns no canonical SavedData.
- Immutable genesis terrain is produced by a registered worldgen Feature from precompiled chunk-local slices. Login fails closed until the catalog is ready; loaded-chunk events observe stamps but never construct static geometry. Every post-gen canonical state transition uses persisted materialization jobs and never overwrites unknown player changes.
- Non-critical runtime work shares a configurable 3 ms / 256 weighted-operation admission budget. Cadence is staggered, unchanged projections are suppressed, railway health is event-driven, and `/pale_mirror performance` exposes per-work timing and deferral metrics.
- Distant Horizons 3.2.0-b is client-side by default. Its exact optional server cache is a benchmark opt-in; when present PM disables the importer, keeps PRE_EXISTING_ONLY dormant and throttles its public runtime ratio during fast travel. DH never affects canonical state.
- The settlement generator uses a bounded deterministic curated-hybrid grammar: a surveyed site selects foothill-ribbon, terraced-basin or freight-crossroads form, then curated NBT modules bind to typed foundations and semantic ports. It does not use radial or unconstrained jigsaw fallback.
- The authored address occupies a 145x193 master envelope with an irregular roughly 120x160 active footprint, connected paved freight/civic circulation, terrain-following low defences and lamps. Five parcels plus depot/smeltery annex easements reserve the 72-person masterplan.
- Terrain placement prefers dry sites with sampled relief at most 8 blocks, rejects water/extreme terrain, and permits a bounded dry fallback up to 24 blocks only after the preferred candidate fails.
- One immutable `RegionPlacementProfile` owns search bands/budgets, settlement terrain, typed site constraints/relations and route policy; layout grammars consume it explicitly. The iron profile separates a six-region output cap from a 160-center survey cap and preserves bounded foothill ranking, six exact settlement surveys per center, two dry mountain sites with a median cross-section rising at least 10 blocks over 48 blocks and 24 validations, bounded rail graph search, one-in-four grade and a first region 1024–4096 blocks from spawn.
- Region cardinality is a range rather than a quota: 3 is the fail-closed minimum, 5 the desired population and 6 the opportunistic cap. The default search radius is 20,000 blocks with 2,500-block center spacing, anticipating Create travel.
- Fresh genesis uses one coordinator plus a configurable 1-16-worker pool (six by default). Indexed work may complete in any order, but only the sequential reducer accepts candidates and assigns ordinals; equal-distance coarse heights are resolved by coordinate key rather than map iteration.
- Canonical railway geometry uses a bounded deterministic A* corridor between depot and loading endpoint, strongly prefers dry land, permits at most 24-block bridge spans and verifies the final one-in-four grade path. Current-chunk height/fluid data controls local cut/fill/bridge representation and never changes route identity or requests another chunk.
- A region is accepted only when it has two dry mountain-native MineSites. Mine17 is 160–320 blocks from the settlement; Red Valley is 320–560 blocks away. Both require nearby mountain evidence, a rising median cross-section and independent dry surface pads with at most 8 relief/4 cut/4 fill. Pads may move within a bounded foothill search instead of forcing one terrace. The complete primary route plan exists at genesis and materializes as its chunks naturally generate/load.
- Three initial climate families share one role catalog but vary palette and threshold form: temperate planting, cold windbreak/snow and dry shade/water stop.
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
- Damage is a deterministic bounded module-state overlay. Reconstruction restores the captured authored baseline; positive development compiles a real climate-family module on a verified free reserved plot.
- Resource production shared by multiple routes is allocated deterministically by actual demand and an explicit route weight; route identity is only a remainder tie-breaker.
- Causal history retains a bounded detailed window plus compact per-subject/type summaries. Terminal journeys and materialization jobs have bounded receipt/summary retention.
- The Visuals compile boundary is a dedicated `pale-mirror-api` module; documentation-only separation is insufficient.

## State
### Done
- Added the required `pale-mirror-visuals` JAR and narrow experimental Core/Visuals SPI; schema v40 intentionally rejects all older worlds.
- Replaced the crash-prone loaded-chunk genesis materializer with an asynchronous immutable catalog and a chunk-local worldgen Feature. Static settlement, MineSite and baseline-rail geometry is no longer written from `ChunkEvent.Load` or the normal server tick.
- Added persisted chunk generation stamps, readiness/status SPI, player admission gating, authored MineSite adoption and worldgen-only baseline-rail provenance registration.
- Added the shared runtime work coordinator with configurable 3 ms / 256-op defaults, staggered cadences and operator telemetry.
- Added exact optional DH 3.2.0-b cache-only control with its background importer disabled, PRE_EXISTING_ONLY as a dormant fail-safe, one event-fed worker, speed/discontinuity-driven 0.35/0.05 runtime throttling with hysteresis, observable degraded status and cleared shutdown overrides.
- Closed the live powered-rail corner crash: runtime segment compilation now shares the genesis invariant that curves use ordinary rail and non-powered support. A dedicated negative GameTest covers the exact twelfth-segment failure.
- Replaced full chunk-volume rail scans with event-driven bounded graph traversal, full loaded-entity ambient counts with Minecraft `SpawnState`, repetitive visual projections with revision suppression, and repeated Threat Heart scans with UUID indexing.
- Kept all 48 authored residents visible while limiting expensive nearby vanilla AI to 16 role-prioritized carriers by default; the AI limit/radius are visual-only configuration.
- Added persisted immutable v40 manifests, generator-only terrain survey, three terrain-led settlement archetypes, three real climate families, typed buildings/open spaces, five parcels plus two annex easements and curated private NBT modules with provenance.
- Replaced per-region terrain searches with a deterministic spacing-safe batch selector whose output and survey-candidate caps are independent; exact height work remains bounded separately from cheap biome sampling.
- Parallelized center ranking and complete region feasibility through a bounded work-stealing pool with inline nested work, concurrent terrain caches, indexed result reduction and visible candidate/worker telemetry. The production seed improved from 172.745s serial to 51.245/51.330s on six workers (3.37x); two clean starts produced byte-identical genesis SavedData and catalog `f9abf85a50a91e27`.
- Replaced pointwise site/rail height scanning with profile-driven cheap biome ranking, bounded exact settlement/site validation and bounded route controls. Iron MineSites reject wet yards and flat terrain; naturally generating chunks use only their ready local heightmap for local representation.
- Replaced automatic observed-village campaign binding when Visuals is installed; core registers authored communities, places, facilities, sites, route contracts and exact 48-person PM-owned cohort state.
- Added stable resident UUID commissioning, canonical-growth-only breeding, confirmed-death reconciliation and the isolated exact Villager Overhaul guard/worker/recruitment bridge.
- Added the GeckoLib Threat Heart, four projected threat stages, infection sound/particles and depot crisis/recovery/prosperity cues. Core remains authoritative.
- Removed first-generation mine force-loading; authored settlement work, MineSites and baseline rail work advance from naturally loaded chunks only.
- Cut every long-lived representation over to the shared schema-v40 materialization registry, guarded gateway, exact semantic cells and parcel ledger.
- Added explicit influence/community/public-infrastructure/reserved/player-lease parcels; player work becomes PM-managed only through an explicit commissioning permit.
- Added canonical `WorldJourney`, deterministic off-screen travel/risk, exclusive stable-resident identity leases, bounded nearby physical groups and canonical return journeys.
- Added reserved shelter candidates, selected/fallback refugee camps, staged authored damage, exact reconstruction and plot-based positive-development projects.
- Added terrain-costed, grade-safe persisted baseline rail paths with arbitrary turns, loaded-chunk construction and topology-based player reroute adoption.
- Added 57 Visuals unit/planner tests and 15 Visuals GameTests including deterministic chunk-local compilation, the 468-case climate/rotation/state showcase matrix, blueprint sanitizing, powered-rail corners, late-write rail recovery, bridge water preservation, full-height vegetation cleanup, terrain blending, safe resident spawn and biome-feature registration under exact Sable 2.0.3, plus JAR and packaged two-start verification. All 42 Core and 15 Visuals GameTests pass.
- Made `WorldState` collection views immutable and mutations package-private; production mutations cross `DomainCommandExecutor`/`DomainTransaction`, while a codec-only hydration builder rejects duplicate persisted identities.
- Added schema-v40 canonical and physical integrity validation on load and save, typed command failures, atomic aggregate registration and command-owned Narrator evaluation/reset/registration paths.
- Added a configurable ephemeral NeoForge ambient-spawn budget that strongly reduces natural hostiles and
  moderately reduces natural fauna without deleting existing entities or suppressing authored content.
- Added indexed settlement-influence ecology protection: natural/chunk-generation/patrol monsters and
  tree-like growth are rejected across the full vertical column, without granting block ownership or
  suppressing explicit PM encounters/spawners. Fresh genesis removes bounded natural vegetation before modules.
- Bound every semantic slot to one explicit parcel, added postcondition rollback, rejected duplicate current materialization jobs and compacted terminal jobs into bounded receipts.
- Bounded detailed causal history and terminal journeys with persisted summaries; shared output now uses deterministic demand/weight allocation rather than route-ID priority.
- Replaced blocking debug teleport height queries with a single bounded asynchronous FULL-chunk ticket; requests
  report progress, cancel on disconnect/replacement/timeout/shutdown, and teleport only after readiness.
- Extended fresh settlement cleanup with a six-block cleanup-only halo and an original-surface scan for arbitrarily
  high tree crowns, logs, vines, cocoa, moss, cave plants and tagged beehives without claiming neighbouring ground.
  Mine17 uses an organic 18-block pad union for large modded crowns. A 28-column one-block gradient blends local pads
  into nature; resident commissioning rejects occupied spawn cells.
- Added a repeatable X11 audit path that captures top and four diagonal daylight views without disturbing camera pitch; a real client pass now supplies settlement and Mine17 image evidence.
- Expanded the audit path into 25 semantic settlement/mine/rail viewpoints with exact runtime teleport commands, a machine-readable capture manifest and contact sheets. Added supported low fixtures, mixed slab/stair public-realm paving and exhaustive 216-case climate/archetype/variant materialization coverage.
- Removed the duplicate early-decoration pass from authored worldgen: surface fixtures and public-realm details now materialize once during stamped late finalization, so lamps, fences and other decorations cannot stack before biome decoration settles.
- Closed the natural-settlement public-realm defects found at the second Township: flat access features use full paving instead of alternating stairs, public fixtures reserve their whole clearance and avoid roads/entry aprons, generic unsupported service-port stacks are gone, every building has a flared paved entrance apron, and freight/climate arches begin on their actual foundations.
- Replaced fragmented rear defences with a reusable full-perimeter grammar and four real fence-gate passages. Baseline rail now rests on non-falling stone masonry, permits only ordinary/powered rails, and is re-applied after all late module/decor writes so the physical graph remains the final owner of its cells.
- Replaced per-pad settlement grading with one bounded managed-union terrain datum: the inhabited core retains at most one block of relief, fills cracks/canyons and blends outward over 14 columns. Streets and open spaces then own exact grades, and transverse slab courses appear only on the lower side of a real one-block rise.
- Rebuilt Mine17 circulation as obstacle-aware five-wide routes between a free yard hub and every surface-building throat. Yard fixtures avoid those routes, all surface details bind to their local floor, and the underground grammar performs a final connected two-block-high carve after imported modules so no NBT wall can reseal the adit or galleries.
- Regenerated the mine art kit with supported stepped roof courses and attached hanging fixtures; surface-module natural blocks are retained because they are structural foundations, while loot, hazards and ores remain sanitized. Rail corners now use actual neighbouring directions instead of the previous travel vector.

### Now
- The approved v40 authored-settlement/POI visual-quality cut is implemented. Risk is `critical-code`: immutable manifests, persistence codecs, worldgen compilation and registration changed; genesis readiness remains the visible fail-closed recovery boundary.
- Schema v40, Visual definition v12, compiled catalog v19, authored mine art-6 and genesis SavedData schema v10 intentionally reject every earlier world; the playtest world is disposable.
- The static 48-person Township has exact functional building/category/function/slot contracts, six open spaces, 20 exact <=4 cut/fill pads, an irregular managed union and stable resident building/slot assignments. Catalog-only 12/24/72 snapshots are validated without runtime stage growth.
- Mine17 materializes a complete typed surface campus from portal/hoist, crew, processing, power, loading and maintenance assets; Red Valley remains a compact staged outpost. True NBT dimensions are part of the fail-closed footprint contract. A shorter branched underground grammar is final-carved after module placement and keeps the adit, galleries and controller chamber connected and traversable.
- Settlement pads merge into a bounded continuous managed-union datum that removes one/two-block trenches and deep cracks without flattening the surrounding landscape. Mixed masonry streets, slab sidewalks, true elevation-only half-step transitions, flared entrance aprons, planters, collision-safe lamps and a nearly complete four-gate enclosure establish a public-realm hierarchy. Reserved plots retain safe survey markers without ambiguous stock-strip clutter.
- Settlement manifests now carry stable module identity, local foundations, semantic ports, typed circulation/defence features and bounded visual-state profiles. Runtime damage/reconstruction, positive development and refugee camps use those authored contracts through Core jobs.
- The baseline railway uses bounded dry-preferring A*, rejects water runs over 24 blocks or insufficient clearance, and materializes short bridges with decks/piers while preserving surrounding water. Ordinary cells use stone-brick support, scheduled accelerators are powered rails over redstone, and late finalization restores the immutable graph after overlapping yard/module writes. It never drains chunks.
- Surface mine pads use a deterministic nearby search, keep structural footprints disjoint while allowing grading aprons to merge, and cap local grading at 8 relief/4 cut/4 fill.
- Red Valley staged commissioning and canonical route proof remain unchanged: resource flow is blocked until the site and transport capability are operational.
- Natural placement finds three strict complete regions on seed `7391842605318702447` in 51.2-51.3s with the production 3/5/6 policy and six workers. The immutable result has 758 slices at settlements 2376,67,11400; -3416,66,19064; and -13240,65,-3976.
- Mine surface NBT is now a reproducible PM-authored block-entity-free industrial kit. The natural client audit shows a compact portal/hoist campus, articulated halls and an irregular equipped yard integrated into a mountain saddle; no DUMMY/air records or overhanging forest remain.
- The latest natural client audit shows intact settlement modules after late biome decoration cleanup, legible mixed paving and intentionally marked development parcels. Empty reserved parcels remain because runtime growth is explicitly outside this cut.

### Next
- The multithreaded planner, deterministic manifest check and complete Core/Visuals verification matrix are green; use the semantic 25-view audit to continue product playtesting of street navigation, fixtures, building interiors and the Mine17 encounter without adding settlement growth stages in v40.

## Open questions
- Temperate natural placement and finalization are visually confirmed; cold-taiga and dry-arid art-family quality still needs equivalent natural client audits.
- The exact VO public surface plus one fail-closed recruitment mixin loads in GameTest and packaged server; live patrol/combat quality still needs client playtesting.
- Public redistribution remains out of scope; re-audit all imported asset and dependency licences before changing that assumption.
## Working set
- `AGENTS.md`, `architecture.yml`, Gradle build; `pale-mirror-api`; Visuals genesis/layout/assets/residents; NeoForge authored registration/persistence.
