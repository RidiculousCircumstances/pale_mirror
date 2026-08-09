# Continuity Ledger

## Goal (success criteria)

- Maintain a server-authoritative Pale Mirror core slice and expand the
  version-pinned Crimson layer without making it a second source of truth.

## Constraints/Assumptions

- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains independent of Minecraft and NeoForge.
- Core slice must work without Crimson Curse; the exact-version sandbox is optional.
- Crimson content is ARR-licensed; public distribution of derived content needs written permission.

## Key decisions

- PM SavedData/domain state owns world progression, scenarios, threat tiers,
  siege stage and desired state; chunks/entities/adapters are representations.
- `architecture.yml` is the boundary source of truth; Gradle guardrails enforce it.
- Third-party internals are allowed only in an isolated, version-pinned
  adapter; identifiers cannot leak to domain or generic materialization.
- PM-native tiers—not Crimson Phase/Points/Mass—drive deterministic progression.
- At APEX, an available sandbox runs `4 Nodes → deterministic boss →
  Bloodlink I/II/III → PM anchor`; adapter loss blocks the scenario. Core-only
  activation bypasses the optional chain instead of changing core behavior.

## State

### Done

- Core vertical slice, restart harness, deterministic simulation, migrations,
  authored scenarios, provenance-safe materialization and settlement flow are implemented.
- The domain is command/event based; typed Minecraft observations reconcile
  through a bounded persisted deduplication ledger.
- Crimson 1.4.3.1 is sandboxed by a top-priority built-in datapack that shadows
  its global load/tick. PM owns spread, raids, phases and actor lifecycle.
- Sixteen PM-managed local forms are tier-selected and bounded to their site:
  seven Crimsonified, seven Decayed, Rusher and Raptor.
- Schema v8 persists canonical `SiegeState` and physical `SiegeRecord` refs.
  Released v7 infected facilities migrate to an explicit bypass to avoid
  surprise locks.
- PM now owns four provenance-safe Node cells, deterministic boss selection
  across Juggernaut/Knight/Mangler/Pummeler/Kraken/Osiris, and three persisted
  Bloodlink gates. Only their typed destruction observations advance state.
- PM controller damage is rejected before the chain is clear. All new entities
  carry PM provenance; their local runtime is bounded, non-global and non-griefing.
- Core and Crimson GameTests pass, including the full siege clearance chain.

### Now

- Siege implementation is complete locally; full critical-code verification and
  final commit remain for the current change.

### Next

- Run clean packaged-JAR/restart profiles with the new siege data, then audit
  manual multiplayer/client behavior and resource-pack presentation.

## Open questions

- UNCONFIRMED: a real-client/multiplayer siege playthrough has not been automated.
- Obtain written permission before distributing derived Crimson functions, models or tables.

## Working set

- `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `docs/crimson-audit-1.4.3.1.md`
- `pale-mirror-domain/.../SiegeState.java`, `FacilityState.java`, `DomainCommandProcessor.java`
- `pale-mirror-neoforge/.../PaleMirrorRuntime.java`, `PaleMirrorEvents.java`
- `internal/materialization/`, `internal/observation/`, `internal/integration/crimson/`
- `gametest/CrimsonSiegeGameTests.java`, `pale-mirror-neoforge/build.gradle`
