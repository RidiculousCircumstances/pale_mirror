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
- This private integration reuses the pinned Crimson client resources already
  installed by the pack; PM owns the exact CEM-selecting entity data, not
  copied Crimson assets.
- Presentation stays inside the isolated adapter: effects are derived from
  registered PM entities and never write domain state or call Crimson runtime.
- The staged infection biome is core-only and uses fixed vanilla blocks in
  predeclared `test-mine-v2` cells; it never calls Crimson terrain conversion
  or claims unrecorded cells from legacy mines.

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
- Schema v9 persists canonical `SiegeState`, physical `SiegeRecord` refs and
  per-cell infection-stage provenance. Released v7 threats migrate to an
  explicit siege bypass; v8 cells migrate as legacy Node cells rather than
  silently expanding PM ownership.
- PM now owns four provenance-safe Node cells, deterministic boss selection
  across Juggernaut/Knight/Mangler/Pummeler/Kraken/Osiris, and three persisted
  Bloodlink gates. Only their typed destruction observations advance state.
- PM controller damage is rejected before the chain is clear. All new entities
  carry PM provenance; their local runtime is bounded, non-global and non-griefing.
- Every normal and siege actor profile now validates the exact visual name that
  selects its Crimson CEM form. Siege initializers restore audited visual
  equipment/scale; Pummeler has a PM-owned Crimson model-display passenger.
- The pinned-resource verifier covers all used CEM names and Pummeler's model
  carrier; a GameTest verifies the Pummeler passenger contract.
- `CrimsonPresentationRuntime` now transfers local sounds, particles,
  Raptor/Bloodlink model frames, CEM dash poses, Osiris health-phase cues and
  an Osiris Brain visual child for the complete supported PM roster. All
  presentation children are PM-owned and cleaned with their parent.
- New `test-mine-v2` sites render a bounded staged biome: 66 biome cells move
  through FOOTHOLD/INFESTED/SIEGE/APEX palettes while four separate Node cells
  retain siege ownership. Conflicts block instead of overwriting external edits.

### Now

- The staged infection biome is implemented and its full critical-code suite,
  packaged-JAR checks and Crimson dedicated-server smoke have passed.

### Next

- Perform a graphical client/multiplayer pass against the private pack to
  validate rendered frames, mixing and particles, then tune effects deliberately.

## Open questions

- UNCONFIRMED: a real-client/multiplayer siege playthrough has not been automated;
  the current evidence covers pinned resources and server-side visual data.
- Obtain written permission before distributing derived Crimson functions, models or tables.

## Working set

- `AGENTS.md`, `CONTINUITY.md`, `architecture.yml`, `docs/crimson-audit-1.4.3.1.md`
- `pale-mirror-domain/.../SiegeState.java`, `FacilityState.java`, `DomainCommandProcessor.java`
- `pale-mirror-neoforge/.../PaleMirrorRuntime.java`, `PaleMirrorEvents.java`
- `internal/materialization/`, `internal/observation/`, `internal/integration/crimson/`
- `gametest/CrimsonSiegeGameTests.java`, `pale-mirror-neoforge/build.gradle`
