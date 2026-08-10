# Continuity Ledger

## Goal (success criteria)

- Maintain PM as canonical world/scenario owner and evolve the proven threat loop into one living region: Mine -> Route -> Settlement -> Defence -> player choice -> durable consequence.

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
- Vanilla/Integrated Villages discovery is read-only: a bounded loaded-chunk observer needs two villagers plus a stable bell/bed landmark and never creates or overwrites settlement blocks.
- FTB Quests is an optional static journal projection. PM never reads or writes FTB progression; it seeds one non-reward chapter only if its exact PM-owned config file is absent.
- Canonical settlement direction is an actor model, not one state object: `SettlementCommunity`, `SettlementPlace`, economy, security, independent `WorldSite`, freshness-bounded `RouteContract`, and deterministic `SettlementPolicy` have separate ownership; `PopulationGroup` is the next separate aggregate, not part of schema v20.
- There is no universal settlement lifecycle or unexplained numeric confidence. Recognition, observation freshness, integrity, operation, occupancy, crisis and population disposition are orthogonal; observations use explicit evidence/reliability classes and causal attribution.
- A crisis is an objective simulation fact. Settlement policy acts without a scenario; Narrator only selects presentation/pacing and may return `NO_SCENARIO`.
- Ordinary containers never mirror canonical stock. Physical delivery/withdrawal requires persisted receipts/leases; adapters declare field ownership as PM/native/derived/observed/reconciled.
- Schema v21 is the breaking physical-economy boundary. IRON crosses through a PM-owned depot and a bounded persisted transfer ledger; ordinary barrel contents remain non-canonical.
- Schema v22 separates population into cohort-bearing `PopulationGroup` records and adds objective grace/evacuation/displacement state. Community identity survives place loss.
- Schema v23 adds positive settlement development: pinned surplus policy, resource reservations, physical storehouse intent, prosperity/housing and bounded growth/return-home.
- Schema v24 pins a generic settlement field-authority profile. Millénaire 9.0.0-beta.2 is an optional exact-version read-only reconciliation profile; native villages are excluded from the campaign unless `allowMillenaireCampaign=true`.

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

### Now

- 0.2d–0.2g implementation is complete at schema v24.

### Next

- Perform the authenticated multiplayer playthrough and the player-built scheduled Create train walkthrough as manual release evidence.
- Review 0.2 release acceptance and decide whether culture/reputation begins in 0.3 rather than expanding schema v24.

## Open questions

- UNCONFIRMED: authenticated multiplayer playthrough and the player-built native Create scheduled-train traversal; automation deliberately does not fake a Create train.
- KNOWN MODEL DEBT: Millénaire culture, relations, quests, local economy and native development remain intentionally native-owned; structure inference is bounded and missing NPCs never imply deaths.
- Obtain written permission before distributing derived Crimson functions, models or tables.

## Working set

- `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `docs/product-vision-0.2-first-living-region.md`, `docs/settlement-actor-model.md`, `docs/crimson-audit-1.4.3.1.md`, `docs/spore-audit-2.2.0j.md`
- `PaleMirrorSavedData`, `SourceGateState`, `internal/adapter/`, `internal/effect/`, `internal/quarantine/`
- `PaleMirrorRuntime`, `PaleMirrorEvents`, `internal/combat/`, `internal/integration/item/`
- `internal/integration/crimson/CrimsonActorRuntime`, `CrimsonSiegeRuntime`, `internal/integration/spore/SporeCombatRuntime`, `SporeProjectileRuntime`
