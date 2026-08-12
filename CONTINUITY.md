# Continuity Ledger

## Goal (success criteria)

- Replace the observed-village Living Frontier bootstrap with a fresh-world-only, PM-authored visual/worldgen system delivered as the required `pale_mirror_visuals` mod.
- Generate a deterministic radial frontier fort, 48 registered residents, Mine17, Red Valley and the baseline minecart route; preserve PM canonical ownership and present later state changes through restart-safe visual jobs.

## Constraints/Assumptions

- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains free of Minecraft, NeoForge, persistence and adapters.
- Existing schema-v32 and older worlds are intentionally unsupported; the development server world will be replaced.
- The new product profile requires Pale Mirror Core, Pale Mirror Visuals, Supplementaries, GeckoLib 4.9.2 and exact Villager Overhaul 3.10.17.16. Create remains an industrial-upgrade integration.
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

## State

### Done

- Added the required `pale-mirror-visuals` JAR and narrow experimental Core/Visuals SPI; schema v33 intentionally rejects all older worlds.
- Added persisted immutable manifests, generator-only terrain survey, deterministic three-region grammar, three climate palettes, radial forts, six expansion plots and curated private NBT modules with provenance.
- Replaced automatic observed-village campaign binding when Visuals is installed; core registers authored communities, places, facilities, sites, route contracts and exact 48-person PM-owned cohort state.
- Added stable resident UUID commissioning, canonical-growth-only breeding, confirmed-death reconciliation and the isolated exact Villager Overhaul guard/worker/recruitment bridge.
- Added the GeckoLib Threat Heart, four projected threat stages, infection sound/particles and depot crisis/recovery/prosperity cues. Core remains authoritative.
- Removed first-generation mine force-loading; authored settlement work, MineSites and baseline rail work advance from naturally loaded chunks only.
- Added Visuals planner tests, 2 Visuals GameTests, JAR verification and a clean packaged Core+Visuals two-start harness. All 34 core GameTests pass.
- Fixed the previously flaky multi-chunk player-reroute GameTest by observing both dirty chunks.

### Now

- Implementation is complete and locally verified. Changes are uncommitted; no product server/install profile has been updated and no old world has been retained.

### Next

- Commit the cutover when requested, publish both JARs, update server/client install manifests and create a fresh disposable v33 playtest world.
- Perform real-client temperate/cold/dry seed-matrix review for terrain, module composition, resident behavior, Threat Heart animation/audio and encounter readability.

## Open questions

- UNCONFIRMED: final visual quality and playability until a real client visits generated temperate/cold/dry regions.
- The exact VO public surface plus one fail-closed recruitment mixin loads in GameTest and packaged server; live patrol/combat quality still needs client playtesting.
- Public redistribution remains out of scope; re-audit all imported asset and dependency licences before changing that assumption.

## Working set

- `AGENTS.md`, `architecture.yml`, `settings.gradle`, `build.gradle`
- `pale-mirror-neoforge/.../api`, `PaleMirrorSavedData`, `PaleMirrorRuntime`, `CampaignRegionBootstrapper`
- `pale-mirror-visuals`, authored-region manifest/genesis, residents, VO bridge and Threat Heart
