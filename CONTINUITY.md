# Continuity Ledger

## Goal (success criteria)

- Maintain PM as canonical world/scenario owner and transfer source threat content through isolated, version-pinned adapters without granting third-party runtime authority.

## Constraints/Assumptions

- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains Minecraft/NeoForge/persistence/adapter-free.
- Crimson 1.4.3.1 and Spore 2.2.0j are optional exact-version private integrations.
- Source recipes, items, equipment and source-item loot are explicitly out of scope; they are blocked and quarantined rather than adopted or deleted.
- Crimson content is ARR-licensed; public distribution of derived content needs written permission.

## Key decisions

- PM SavedData/domain state owns progression, scenarios, tiers, siege and desired state; chunks/entities/adapters are representations.
- A facility has one immutable `InfectionSourceId`; mixed sources need a future composition policy.
- Third-party internals are allowed only inside isolated, version-pinned adapters; no identifiers leak to the domain or generic materialization.
- Crimson global load/tick is shadowed; PM never reads/writes Crimson Mass, Phase, Points or raids.
- Spore global spawning/infection/evolution paths are suppressed by a top pack, exact mixins and an entity-join firewall.
- PM effect leases are written and marked dirty before any non-replayable physical action. A `RUNNING` lease becomes `UNKNOWN_AFTER_RESTART` and is never replayed implicitly.
- Excluded source stacks are denied at interaction, craft/smelt, pickup, `inventoryTick` and melee boundaries. Legacy stacks remain physically present but only as SavedData quarantine diagnostics.

## State

### Done

- Core vertical slice, deterministic simulation, migration pipeline, provenance-safe materialization, restart harness and settlement flow are implemented.
- Crimson sandbox: sixteen normal forms; APEX Nodes → deterministic boss → Bloodlink I/II/III → PM anchor; CEM/EMF/ETF visual contract, local sound/particles and PM visual children.
- Staged test-mine biome uses 66 registered vanilla cells plus four separate Node cells; unknown changes conflict rather than overwrite.
- Spore sandbox: no-spawn pack, global-handler mixins, entity firewall, four audited native forms, exact-tier persisted compositions, PM movement/combat/cleanup and two-start dedicated harness.
- Schema v15 persists PM-controlled source-actor health/cooldowns and target-bound projectile records; v14→v15 intentionally creates no inferred actor/projectile state.
- Every supported Crimson normal/siege form and Spore roster form is a NoAI visual carrier: PM owns target choice, movement, incoming vanilla-compatible damage, HP, cooldowns, local effects, XP and cleanup.
- Crimson arrows and Spore AcidBall now use one PM projectile pipeline with a persisted target UUID, launch/impact leases, target-only damage and discard-on-restart recovery.
- The packaged-JAR verifier requires the effect ledger and item firewall classes/config. Focused unit tests plus core, Spore and Crimson GameTest servers passed after this change.

### Now

- The supported source combat roster is PM-authoritative end-to-end. The remaining task is validation hardening, not transferring its native combat authority.

### Next

- Run a graphical client smoke test with both optional source mods and capture the supported visual/animation/sound contracts.
- Audit the remaining Spore catalogue one class at a time; add organisms, terrain and any new projectiles only after each has a persisted PM effect profile and side-effect audit.
- Add strict client profile/visual regression harness; `runSporeClient` exists but graphical multiplayer validation remains UNCONFIRMED.

## Open questions

- UNCONFIRMED: real graphical client/multiplayer playthrough has not been automated.
- Obtain written permission before distributing derived Crimson functions, models or tables.

## Working set

- `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `docs/crimson-audit-1.4.3.1.md`, `docs/spore-audit-2.2.0j.md`
- `PaleMirrorSavedData`, `PaleMirrorSnapshotMigrations`, `internal/effect/`, `internal/quarantine/`
- `PaleMirrorRuntime`, `PaleMirrorEvents`, `internal/combat/`, `internal/integration/item/`
- `internal/integration/crimson/CrimsonActorRuntime`, `CrimsonSiegeRuntime`, `internal/integration/spore/SporeCombatRuntime`, `SporeProjectileRuntime`
