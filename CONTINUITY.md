# Continuity Ledger

## Goal (success criteria)

- Close Pale Mirror 0.2 as First Living Region: Ironhill: a player discovers, understands and resolves Mine -> Route -> Settlement -> choice -> durable consequence without operator help; then evolve it into repeatable Living Frontier regions in 0.3.

## Constraints/Assumptions

- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains Minecraft/NeoForge/persistence/adapter-free.
- Crimson 1.4.3.1 and Spore 2.2.0j are optional exact-version private integrations.
- Source recipes, items, equipment and source-item loot are explicitly out of scope; they are blocked and quarantined rather than adopted or deleted.
- Crimson content is ARR-licensed; public distribution of derived content needs written permission.

## Key decisions

- PM SavedData/domain state owns progression, scenarios, tiers, source-neutral gates and desired state; chunks/entities/adapters are representations.
- A facility has one immutable `InfectionSourceId`; mixed sources need a future composition policy.
- Third-party internals are allowed only inside isolated, version-pinned adapters; no identifiers leak to the domain or generic materialization.
- Crimson global load/tick is shadowed; PM never reads/writes Crimson Mass, Phase, Points or raids.
- Spore global spawning/infection/evolution paths are suppressed by a top pack, exact mixins and an entity-join firewall.
- PM effect leases are written and marked dirty before any non-replayable physical action. A `RUNNING` lease becomes `UNKNOWN_AFTER_RESTART` and is never replayed implicitly.
- Excluded source stacks are denied at interaction, craft/smelt, pickup, `inventoryTick` and melee boundaries. Legacy stacks remain physically present but only as SavedData quarantine diagnostics.
- Schema v20 is the breaking Settlement Actor boundary: all v5–v19 snapshots fail closed and require a new world because community/place identity, policy, contracts and membership cannot be inferred safely.
- Crimson is the primary canonical threat source for the next product slice; Spore is optional/test-only unless a future scenario explicitly selects it.
- Create 6.0.10 is an optional, version-pinned, reflection-contained read-only logistics adapter. It certifies a route only after observing the same opaque native vehicle at two named loaded stations; it never drives Create or force-loads chunks.
- Vanilla/Integrated Villages discovery is read-only: a bounded loaded-POI observer needs two villagers plus a stable bell/home cluster and never creates or overwrites settlement blocks. Large vertical villages use a 96x48 POI window; campaign logistics receives a separate ground-projected outskirts anchor rather than inheriting a tower bell's Y coordinate.
- FTB Quests is an optional static journal projection. PM never reads or writes FTB progression; it seeds one non-reward chapter only if its exact PM-owned config file is absent.
- The 0.3 Atlas and optional JourneyMap integration consume a bounded server-authored snapshot; client requests are revalidated server-side, waypoints are session-only and no observed-village block is used as a PM marker.
- Canonical settlement direction is an actor model, not one state object: `SettlementCommunity`, `SettlementPlace`, economy, security, independent `WorldSite`, freshness-bounded `RouteContract`, and deterministic `SettlementPolicy` have separate ownership; `PopulationGroup` is the next separate aggregate, not part of schema v20.
- There is no universal settlement lifecycle or unexplained numeric confidence. Recognition, observation freshness, integrity, operation, occupancy, crisis and population disposition are orthogonal; observations use explicit evidence/reliability classes and causal attribution.
- A crisis is an objective simulation fact. Settlement policy acts without a scenario; Narrator only selects presentation/pacing and may return `NO_SCENARIO`.
- Ordinary containers never mirror canonical stock. Physical delivery/withdrawal requires persisted receipts/leases; adapters declare field ownership as PM/native/derived/observed/reconciled.
- Schema v21 is the breaking physical-economy boundary. IRON crosses through a PM-owned depot and a bounded persisted transfer ledger; ordinary barrel contents remain non-canonical.
- Schema v22 separates population into cohort-bearing `PopulationGroup` records and adds objective grace/evacuation/displacement state. Community identity survives place loss.
- Schema v23 adds positive settlement development: pinned surplus policy, resource reservations, physical storehouse intent, prosperity/housing and bounded growth/return-home.
- Schema v24 pins a generic settlement field-authority profile. Millénaire 9.0.0-beta.2 is an optional exact-version read-only reconciliation profile; native villages are excluded from the campaign unless `allowMillenaireCampaign=true`.
- Schema v25 adds restart-safe campaign railway commissioning, per-cell rail provenance and MineSite preflight baselines. Schema v26 replaces route tickets/heads with persisted loaded-chunk segment progress; v25 commissioned lines remain legacy, untouched plans migrate, and partial physical lines suspend fail-closed.
- The private Railway Untold 1.2.1-pm.1 fork remains an explicit industrial-route executor. PM talks to it only through `RailInfrastructureAdapter`, owns the commissioning record and canonical flow, never force-loads production route chunks, and suspends service if a player changes its schedule. It is not the fresh small-village baseline.
- The baseline Mine17–Ironhill route is a low-capacity, PM-owned vanilla minecart corridor. Railway Untold/Create rail is an explicit later industrial upgrade, not default infrastructure for a small village. The domain distinguishes this provider without learning Minecraft blocks; the vanilla executor persists provenance, requires loaded chunks, and certifies abstract flow through a bounded PM-owned structural validation.
- PM-managed railway construction persists an immutable route split at chunk boundaries (maximum 32 blocks, eight-block footprint margin). Production segments execute independently and out of order only when all footprint chunks are naturally loaded; a bounded dev profile may ticket one next footprint.
- Provider endpoints are independent retryable freight-service postconditions. They need not be loaded simultaneously; train assembly waits only for the origin after both stations exist.
- An authored region is baseline infrastructure, not a mine-discovery-triggered spawn: eligible settlement recognition autonomously preflights and materializes both MineSites, then persists the complete vanilla corridor. Its physical segments arrive only through ordinary player-loaded chunks; player proximity to a planned mine is not a special generation gate.
- The original Create 6.0.10 read-only adapter remains the proof path for a player-built alternate route; it does not overlap with PM-managed baseline railway ownership.
- Server-side runtime debug controls are operator-only. Read commands expose typed evidence and canonical facts; manual binding uses the normal living-region registration path; destructive reset is a two-phase maintenance action that must fail closed once any PM physical work, transfer, lease, actor or projectile exists.
- Debug visualization is ephemeral presentation: per-operator bounded particles expose observed/managed bounds and planned mine columns; advanced client tooltips expose item registry ownership and PM policy without mutating ItemStacks.
- Debug navigation exposes dimension/coordinates and clickable operator teleports only for persisted physical anchors; canonical planned mines remain inspectable but cannot be teleported to until materialized.
- Product status: 0.2 is conditionally accepted as the technical First Living Region release. Technology remains stronger than physical legibility, player comprehension and repeatability.
- The unaided graphical playthrough remains a non-blocking 0.2.x presentation-quality gate: its findings become bounded UX fixes and do not delay 0.3 architecture work unless they expose a canonical-state or safety failure.
- 0.3 is `Living Frontier`: repeatable multi-region causal stories with combat, infrastructure, evacuation/refusal, visible consequences, Narrator candidate scoring and delayed aftermath. It does not broaden infection providers or commodity scope first.
- Schema v28 is an additive 0.3 persistence boundary. `iron_frontier` binds up to three current, strong, eligible observed places separated by 2048 blocks. Each instance derives stable IDs from world seed + observed place, pins its own placement plan, route IDs and display name; a v27 `ironhill_v2` record retains its exact historical IDs and coordinates.
- Narrator v2 scores transient candidates derived from canonical events/history, never from adapter state. Scores are integer/stable and account for urgency, significance, audience relevance, capability fit, distance, recent archetype repetition and active-story intensity. Pending crisis/development/return opportunities are re-derived from history after restart or late region discovery until a cooldown ends or `NO_SCENARIO`/offer is persisted.
- A player may prepare an evacuation with one persisted, audience-bound Refugee Anchor permit. Stack metadata is not authority: placement is validated against the canonical emergency, region dimension, 96–160-block safe footprint and population capacity; PM then materializes only its camp cells. An unprepared settlement still evacuates autonomously to an auto-selected camp after its grace period.

## State

### Done

- Core vertical slice, deterministic simulation, migration pipeline, provenance-safe materialization, restart harness and settlement flow are implemented.
- First Living Region baseline remains proven, and 0.2c replaces its v19 prototype with schema-v20 Community/Place/Economy/Security/Policy, independent WorldSites, freshness-bounded RouteContracts, typed evidence/membership, objective crisis facts and combat/logistics outcomes. Prototype evacuation is removed.
- Policy and contract thresholds are datapack-authored and pinned into SavedData. IRON rationing reduces demand by 25%; missing supply permanently lowers defence; Create capacity ages full through 8 steps, half through 24, then expires.
- Physical region work is a persistent mine-only campaign job. It resolves a terrain anchor, records RUNNING, and recovers idempotently; it does not replace an observed village. Core GameTests (14) and checksum-pinned Create and FTB runtime profiles pass.
- Crimson sandbox: sixteen normal forms; APEX Nodes → deterministic boss → Bloodlink I/II/III → PM anchor; CEM/EMF/ETF visual contract, local sound/particles and PM visual children.
- Staged test-mine biome uses 66 registered vanilla cells plus four separate Node cells; unknown changes conflict rather than overwrite.
- Spore sandbox: no-spawn pack, global-handler mixins, entity firewall, four audited native forms, exact-tier persisted compositions, PM movement/combat/cleanup and two-start dedicated harness.
- Schema v15 persisted PM-controlled source-actor health/cooldowns and target-bound projectile records; the v16 source-neutral persistence boundary intentionally rejects old snapshots rather than inferring gate state.
- Every supported Crimson normal/siege form and Spore roster form is a NoAI visual carrier: PM owns target choice, movement, incoming vanilla-compatible damage, HP, cooldowns, local effects, XP and cleanup.
- Crimson arrows and Spore AcidBall now use one PM projectile pipeline with a persisted target UUID, launch/impact leases, target-only damage and discard-on-restart recovery.
- The packaged-JAR verifier requires the effect ledger and item firewall classes/config. Focused unit tests plus core, Spore and Crimson GameTest servers passed after this change.
- The domain and generic NeoForge bridge now carry only `InfectionSourceId`, `SourceGateState`, source-neutral materialization operations and adapter contracts. Crimson/Spore identities, gate layouts, overlays and item classification live behind their adapters; `verifySourceIsolation` prevents regressions.
- The vanilla village observer, FTB static journal, same-vehicle Create proof, full packaged integration restart harnesses (core/Crimson/Spore/Create/FTB), and Xvfb client-smoke profiles (core/Crimson/Spore/Create/FTB) are implemented. The native train traversal remains a real-world acceptance walkthrough rather than a fake GameTest vehicle.
- The aggregate `fullSmoke` gate passes: five 14-test GameTest profiles, five clean packaged-JAR restart profiles, and five graphical render/audio profiles. Heavy harnesses run serially, retain failed runtimes for diagnosis, and remove successful runtimes to keep CI resource use bounded.
- 0.2d adds all-or-nothing stock mutations, emergency-reserve withdrawal, a bounded retained transfer ledger, reserved-item reconciliation and deterministic provenance-safe supply-depot materialization. Focused domain/NeoForge tests, guardrails, build and packaged-JAR verification pass.
- 0.2e adds an eight-step intervention window, audience-authorized or automatic evacuation, displaced/refugee-camp presentation, PM-owned ruin overlays, structural sampling and damage attribution. Unit/NeoForge tests, packaging and all 14 core GameTests pass.
- 0.2f adds development pressure, exactly releasable IRON reservations, restart-safe storehouse execution, doubled capacity, prosperity/housing growth, population growth and safe return-home/camp cleanup. Unit/NeoForge tests, packaging and all 14 core GameTests pass.
- 0.2g adds generic per-field authority, stable native population reconciliation, persisted opaque native references, an isolated reflection-only Millénaire observer and default-off campaign opt-in. Domain/NeoForge tests, 15 core and Millénaire GameTests, packaged two-start restart, six-profile graphical client smoke, build and packaging pass.
- Runtime debug toolkit adds AUTO/MANUAL observation control, explicit nearest/ID binding through the canonical registration pipeline, candidate/region/verification reports, canonical infection trigger, resource-location command arguments, per-operator zone markers, advanced item diagnostics and a tokenized fail-closed reset for wholly unmaterialized state. Seventeen GameTests include manual binding plus reset rejection/recovery; all six graphical client profiles pass with the client tooltip hook.
- Runtime navigation lists observed settlements, canonical mines and physical registry objects with dimension/coordinates, clickable `[TP]` actions and a fail-closed distinction between planned and materialized mine locations.
- The campaign now materializes a provenance-preflighted MineSite with a surface loading yard, supported descending drift and underground controller chamber instead of the floating cube. Unknown or crafted terrain blocks abort the whole placement before writes.
- Railway Untold PM fork commits `af227f2`, `b9f7be4`, and `70aca1f` provide managed full-line construction, exact stations, scheduled freight service, persisted progress and a placement guard. Autonomous upstream generation is disabled in managed mode.
- `ManagedRailwayRuntime` persists schema-v25 commissioning before work, builds the complete Mine17–Ironhill line outside observed village bounds, validates the canonical primary route only after a baseline arrival, and delays infection until five simulation steps later.
- The native `Regional Ledger` written-book screen explains population, IRON stock/capacity/net flow/reserve, defence/crisis, route/train state, response options, coordinates and timeline. First recognition grants a survey map, letter and ledger; FTB remains an optional projection.
- Core and exact Create/Railway GameTest profiles include persisted rail-provenance conflict recovery. The packaged final PM JAR plus exact Create/Railway fork passes a clean two-start dedicated-server harness with the managed adapter available after restart.
- The 0.2 commissioning fix removes the player-near-mine gate. One bounded temporary ticket autonomously preflights and materializes each authored MineSite after settlement recognition, releases on completion/block/reset/stop, and hands the materialized region to Railway Untold. Core and exact railway profiles now pass 22/22, including no-player success and crafted-cell fail-closed coverage; build, packaged-JAR verification and the two-start railway harness pass.
- Historical deployment: the earlier corrected PM JAR and exact Railway Untold fork were checksum-published to a v25 private test world, later retained only as an archived pre-v27 world state.
- Schema v26 and the Railway Untold segment executor replace the single force-loaded construction head with a fully persisted route plan and independently replayable, chunk-bounded segments. Production touches only naturally loaded footprints; endpoint stations retry independently; train assembly waits for both stations and a loaded origin. Exact Create/Railway and core profiles pass 23/23, guardrails/build/packaging and the packaged two-start railway harness pass.
- The schema-v26 JARs are checksum-published and installed on the private server. The live v25 commissioning migrated and persisted as v26 `LEGACY`/`TRAIN_COMMISSIONING`, preserving its one `READY` managed line and one `RUNNING` freight service without rebuilding or duplication; all required adapters are `AVAILABLE`. A recoverable pre-upgrade data/JAR backup is retained server-side.
- Live discovery exposed the legacy 16-block landmark cube and civic-anchor height as invalid for large Integrated Villages. The observer now uses loaded POIs and a bounded resident cluster, while infrastructure projects separately to ground on the settlement outskirts. Core and exact railway profiles pass 23/23 and the packaged railway restart harness passes.

### Now

- Schema v28 starts the 0.3 `Living Frontier` implementation: repeatable `iron_frontier` instances, three independent region slots, generic region bindings/debug/journal surfaces, player-prepared Refugee Anchor camps, and recovery/resettlement continuation opportunities. Existing v27 Ironhill records remain pinned instead of being rebuilt.
- Narrator v2 can deterministically choose the most relevant candidate across simultaneous regions, honours cooldown/novelty/intensity, persists explicit `NO_SCENARIO`, and retries history-derived delayed opportunities after pacing clears. Development waits for an offered player decision; declined or unpresented policy intents remain autonomous.
- The core GameTest server passes 26/26 tests, including multi-region identity, v27→v28 persistence, readable opaque-ID-free scenario presentation, autonomous mine commissioning and provenance conflicts. Domain tests cover candidate selection, `NO_SCENARIO`, cooldown retry, opportunity resolution and prepared shelter resettlement.
- 0.3 presentation now has a native `P` Atlas, server-validated scenario/evacuation actions, PM-owned nearby mine/depot signal particles and an isolated JourneyMap v2 client projection. The dedicated-server GameTest profile boots without JourneyMap; graphical verification is still pending.
- The private server now runs the checksum-published schema-v27 JAR on a newly generated `world`; its former 84 MiB runtime world was moved to `.pale-mirror-backups/world-reset-pre-schema-v27-20260811T070000Z/world` and is not mounted by Minecraft. `pale_mirror:vanilla_minecart_rail` reported `AVAILABLE` after clean startup.
- 0.2 is conditionally accepted; its remaining unaided graphical exercise is an ongoing presentation-quality check, not a blocker for 0.3.

### Next

- Exercise all four crisis responses in a real client world: clear the mine, certify a player-built Create route, prepare/execute evacuation, and consciously decline/ignore it. Confirm recovery/storehouse and return-home opportunities are intelligible without debug commands.
- Run full critical-code gates (`guardrails`, packaged JAR, restart/crash and graphical profiles) against the v28 artifact; then conduct an unaided multi-region usability pass before widening commodity, threat-provider or settlement scope.

## Open questions

- UNCONFIRMED: authenticated multiplayer playthrough, graphical validation of the PM-managed baseline corridor/representative cart, and the player-built alternate Create scheduled-train traversal.
- UNAVAILABLE IN CURRENT ENVIRONMENT: the new managed-railway graphical client profile is wired into `clientSmoke`, but this run could not execute it because no Xvfb binary is installed; prior six-profile client evidence remains historical only.
- KNOWN MODEL DEBT: Millénaire culture, relations, quests, local economy and native development remain intentionally native-owned; structure inference is bounded and missing NPCs never imply deaths.
- The new Atlas/JourneyMap UI compiles and its bounded server projection has GameTest coverage, but its graphical rendering and live JourneyMap discovery remain UNCONFIRMED until a real client run.
- Obtain written permission before distributing derived Crimson functions, models or tables.

## Working set

- `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `docs/product-review-0.2-and-vision-0.3.md`, `docs/living-frontier-0.3-implementation.md`, `docs/living-frontier-presentation.md`, `docs/product-vision-0.2-first-living-region.md`, `docs/settlement-actor-model.md`, `docs/runtime-debug-toolkit.md`
- `PaleMirrorSavedData`, `SourceGateState`, `internal/adapter/`, `internal/effect/`, `internal/quarantine/`
- `PaleMirrorRuntime`, `PaleMirrorEvents`, `internal/combat/`, `internal/integration/item/`
- `internal/debug/`, `CampaignRegionBootstrapper`, `CampaignMineSiteTemplate`, `ManagedRailwayRuntime`, `SettlementObservationRecord`
- `internal/integration/crimson/CrimsonActorRuntime`, `CrimsonSiegeRuntime`, `internal/integration/spore/SporeCombatRuntime`, `SporeProjectileRuntime`
