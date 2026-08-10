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
- Schema v14 persists bounded effect leases and quarantine records; sequential v5→v14 migration creates empty records rather than inferring past physical effects.
- Spore direct attacks and Crimson siege dash/pulse/grasp/phase/aura now execute through persisted PM effect leases.
- The packaged-JAR verifier requires the effect ledger and item firewall classes/config. Focused unit tests plus core, Spore and Crimson GameTest servers passed after this change.

### Now

- Controlled-effect foundation and source-item exclusion firewall are implemented. Current source combat roster is still intentionally partial.

### Next

- Replace remaining Crimson vanilla-AI damage/projectile paths with PM behavior/effect profiles before adding more forms.
- Audit the remaining Spore catalogue one class at a time; add projectiles, organisms and terrain only after each has a persisted PM effect profile and side-effect audit.
- Add strict client profile/visual regression harness; `runSporeClient` exists but graphical multiplayer validation remains UNCONFIRMED.

## Open questions

- UNCONFIRMED: real graphical client/multiplayer playthrough has not been automated.
- Obtain written permission before distributing derived Crimson functions, models or tables.

## Working set

- `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `docs/crimson-audit-1.4.3.1.md`, `docs/spore-audit-2.2.0j.md`
- `PaleMirrorSavedData`, `PaleMirrorSnapshotMigrations`, `internal/effect/`, `internal/quarantine/`
- `PaleMirrorRuntime`, `PaleMirrorEvents`, `internal/integration/item/`
- `internal/integration/crimson/CrimsonSiegeRuntime`, `internal/integration/spore/SporeCombatRuntime`
