# Continuity Ledger

## Goal (success criteria)

- Close the complete architecture and code-style audit through an intentionally incompatible schema-v35 cutover.
- Make domain mutation, persistence integrity, semantic materialization, bounded history/jobs/journeys, multi-route resource allocation and the Core/Visuals API boundary enforceable by types and build gates.

## Constraints/Assumptions

- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains free of Minecraft, NeoForge, persistence and adapters.
- Existing schema-v34 and older worlds are intentionally unsupported; the development server world will be replaced.
- The new product profile requires Pale Mirror Core, Pale Mirror Visuals, Supplementaries, GeckoLib 4.9.2 and exact Villager Overhaul 3.10.17.16. Create remains an industrial-upgrade integration.
- Millénaire is absent from the active private-pack profile after an observed 32-second village-chunk stall;
  its read-only adapter remains an optional compatibility surface and reports `ABSENT` by design.
- Integrated Villages ARR structures may be copied and remixed for this private non-distributed mod; every imported template retains an origin/version/hash manifest.
- Crimson 1.4.3.1 is the default infection source; Spore remains optional/test-only.

## Key decisions

- Core owns canonical state, revisions, persisted jobs, provenance and reconciliation. Visuals owns worldgen, templates, palettes, render assets and physical behavior providers, and owns no canonical SavedData.
- Immutable genesis terrain is produced during normal world generation from a complete `RegionSeedManifest`. Every post-gen canonical state transition uses persisted materialization jobs and never overwrites unknown player changes.
- The settlement generator uses a bounded deterministic hybrid grammar: procedural radial plan and road graph plus curated authored NBT modules. It does not use an unconstrained jigsaw walk.
- The fort has a civic core, two functional rings, wooden palisade, freight gate/depot and at least six reserved development plots. Nominal diameter is 176 blocks and maximum footprint 192.
- Terrain placement selects the best moderate site, rejects water/extreme terrain and then applies bounded local leveling: normal cut/fill 4 blocks, foundations 6, guaranteed-region fallback 8.
- The first region is placed 1024–2048 blocks from spawn; later regions are sparse at approximately 6000–10000 blocks.
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

- Added the required `pale-mirror-visuals` JAR and narrow experimental Core/Visuals SPI; schema v35 intentionally rejects all older worlds.
- Added persisted immutable manifests, generator-only terrain survey, deterministic three-region grammar, three climate palettes, radial forts, six expansion plots and curated private NBT modules with provenance.
- Replaced automatic observed-village campaign binding when Visuals is installed; core registers authored communities, places, facilities, sites, route contracts and exact 48-person PM-owned cohort state.
- Added stable resident UUID commissioning, canonical-growth-only breeding, confirmed-death reconciliation and the isolated exact Villager Overhaul guard/worker/recruitment bridge.
- Added the GeckoLib Threat Heart, four projected threat stages, infection sound/particles and depot crisis/recovery/prosperity cues. Core remains authoritative.
- Removed first-generation mine force-loading; authored settlement work, MineSites and baseline rail work advance from naturally loaded chunks only.
- Cut every long-lived representation over to the shared schema-v35 materialization registry, guarded gateway, exact semantic cells and parcel ledger.
- Added explicit influence/community/public-infrastructure/reserved/player-lease parcels; player work becomes PM-managed only through an explicit commissioning permit.
- Added canonical `WorldJourney`, deterministic off-screen travel/risk, exclusive stable-resident identity leases, bounded nearby physical groups and canonical return journeys.
- Made the GeckoLib Threat Heart the sole product controller backed by PM combat state; the zombie anchor remains core-only test infrastructure.
- Added reserved shelter candidates, selected/fallback refugee camps, staged authored damage, exact reconstruction and plot-based positive-development projects.
- Added terrain-costed, grade-safe persisted baseline rail paths with arbitrary turns, loaded-chunk construction and topology-based player reroute adoption.
- Added Visuals planner tests, 2 Visuals GameTests, JAR verification and a clean packaged Core+Visuals two-start harness. All 37 Core GameTests and both Visuals GameTests pass.
- Fixed the previously flaky multi-chunk player-reroute GameTest by observing both dirty chunks.
- Extracted the experimental SPI into `pale-mirror-api`; Visuals can no longer compile against domain or Core internals, while the final Core JAR explicitly embeds the API and domain outputs.
- Made `WorldState` collection views immutable and mutations package-private; production mutations cross `DomainCommandExecutor`/`DomainTransaction`, while a codec-only hydration builder rejects duplicate persisted identities.
- Added schema-v35 canonical and physical integrity validation on load and save, typed command failures, atomic aggregate registration and command-owned Narrator evaluation/reset/registration paths.
- Bound every semantic slot to one explicit parcel, added postcondition rollback, rejected duplicate current materialization jobs and compacted terminal jobs into bounded receipts.
- Bounded detailed causal history and terminal journeys with persisted summaries; shared output now uses deterministic demand/weight allocation rather than route-ID priority.
- Split command registration, runtime combat and test-mine persistence out of oversized coordinators; added Java style, portable-path and 500-line debt gates.
- Verification: domain/unit and `check` pass; 37 Core and 2 Visuals GameTests pass; distribution JAR checks, clean packaged Core crash/restart and packaged Core+Visuals two-start harnesses pass.
- Replaced the playtest world with a clean schema-v35 world using seed `9001648283219134473`;
  all three authored manifests installed asynchronously and the server reached `Done` without Millénaire.

### Now

- Schema-v35 architecture-debt cutover complete. Atlas now has a bounded vertical viewport, clipping,
  wheel scrolling and a draggable scrollbar; scrolling action buttons follow the viewport and cannot be
  activated while hidden. Risk verified as `small-code`; guardrails, check, build and packaged-JAR checks pass.

### Next

- Run a real graphical client pass over fresh authored temperate/cold/dry regions, including Atlas scrolling
  at the player's GUI scale; automated runtime gates cannot judge visual quality or encounter pacing.

## Open questions

- UNCONFIRMED: final visual quality and playability until a real client visits generated temperate/cold/dry regions after the v35 cutover.
- The exact VO public surface plus one fail-closed recruitment mixin loads in GameTest and packaged server; live patrol/combat quality still needs client playtesting.
- Public redistribution remains out of scope; re-audit all imported asset and dependency licences before changing that assumption.

## Working set

- `AGENTS.md`, `architecture.yml`, `settings.gradle`, `build.gradle`
- `pale-mirror-api`, domain `WorldState`/commands/history, `PaleMirrorSavedData`, materialization ledgers/gateway/jobs
- `PaleMirrorRuntime`, `PaleMirrorEvents`, resource flow and projection publishers
- `pale-mirror-visuals`, authored-region manifest/genesis, residents, VO bridge and Threat Heart
