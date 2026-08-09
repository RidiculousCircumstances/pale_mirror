# Continuity Ledger

## Goal (success criteria)

- Maintain a server-authoritative Pale Mirror core slice and expand the
  version-pinned Crimson layer without making it a second source of truth.

## Constraints/Assumptions

- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains independent of Minecraft and NeoForge.
- Core slice must work without Crimson Curse. The exact-version Crimson sandbox
  is optional; PM remains the owner of all world progression and global threat state.
- Crimson content is an ARR-licensed internal prototype; public distribution of
  derived content is blocked pending the author's written permission.

## Key decisions

- Canonical state resides in Pale Mirror SavedData/domain state, not chunks,
  entities, adapters, or quest presentation.
- Architecture guardrails are mechanical Gradle checks plus focused tests;
  `architecture.yml` is the compact boundary map.
- Current work imports selected general practices from Stream Miner, adapted to
  this Java/NeoForge repository rather than copied verbatim.
- Third-party internals may be used only behind an isolated, version-pinned
  sandbox adapter; their identifiers cannot leak to domain, scenario, or
  generic materialization layers.
- PM-native tiers, not Crimson Phase/Points/Mass, select encounter content and
  advance only from deterministic domain simulation.

## State

### Done

- Initial core vertical slice committed as `d89e411`.
- Stream Miner LLM and architecture guardrails inspected.
- Adapted continuity ledger, architecture map, risk profiles, and blocking
  Gradle guardrails added; `./gradlew guardrails` passed.
- Replaced `DomainEngine` with cohesive simulation, threat-lifecycle, and
  event-factory services; focused deterministic and negative-path tests pass.
- Runtime GameTest exposed a ModDev classpath defect: the domain source set was
  embedded in the distributable JAR but absent from the dev mod runtime.
- Fixed the ModDev source-set wiring. `runGameTestServer` now passes the full
  core loop with a mock server player and real controller death observation.
- Both the normal dev server and a clean NeoForge installation containing only
  the packaged mod JAR reached successful server startup on Java 21.
- Final critical-code profile passed: guardrails, unit tests, repeated
  NeoForge GameTest, build, and embedded-domain JAR verification.
- Runtime-test coverage and its ModDev source-set fix committed as `365dbb6`.
- Domain mutations now enter through explicit `DomainCommand` values; typed
  Minecraft facts pass through a bounded persisted reconciliation ledger.
- Materialization is a deterministic persisted plan with independently saved,
  idempotent operations and verified postconditions. The scheduler never
  force-loads chunks and works only near a player.
- Generic World Registry entries now own physical identity, bounds, template,
  and representation lifecycle for the controlled mine.
- Snapshot schema is v7. Released v5 and v6 formats migrate sequentially; other
  legacy/unknown snapshot formats stop server startup
  before Minecraft can silently replace canonical SavedData; manual reset is
  required after an external backup.
- Datapack scenarios now pin their authored stage/capability snapshot and
  version into each active instance. Capability loss is explicit `BLOCKED` /
  resume state, and `NO_SCENARIO` is causally recorded.
- The first settlement/resource-flow expansion is active: an infected test mine
  emits `SETTLEMENT_SUPPLY_DISRUPTED`, removes iron supply, and lowers defense;
  recovery restores both.
- Crimson Curse 1.4.3.1 public and private protocol audits completed. The
  `CrimsonSandboxAdapter` uses a top-priority built-in datapack to suppress its
  global bootstrap and tick, then materializes a PM-owned Crimsonified Human
  without Mass, phase, raid, or spread changes. PM anchors remain canonical.
- Core and Crimson GameTests prove actor identity persistence, actor-death
  observation without controller resolution, cleanup, and shadowing of the
  original global Crimson tick.
- A final-JAR dedicated-server restart harness now creates a clean NeoForge
  runtime, force-crashes it, and verifies the same world starts again. The
  GameTest separately serializes a partially completed `RUNNING` job.
- Sixteen PM-managed forms are active: seven Crimsonified, seven Decayed,
  Rusher and Raptor. Their tier-filtered slots and persisted references drive a
  bounded local runtime; global Crimson tick remains disabled and actors are
  leashed to their owning threat site.
- Rusher's PM dash and Raptor's PM invisibility/target aura replace only safe
  local behaviors; their global-score, Bloodlink, door-breaking and animation
  paths remain disabled.

### Now

- The sixteen-profile roster and special-actor runtime are committed as
  `a6aa86a`; its critical verification suite passed.

### Next

- Audit the first threat object with explicit provenance, safe placement and
  cleanup. Bloodlinks and destructive effects remain separate milestones.

## Open questions

- UNCONFIRMED: an external client/real-player flow and official Crimson resource
  pack rendering have not yet been automated.
- Obtain written permission before distributing derived Crimson content.

## Working set

- `AGENTS.md`
- `CONTINUITY.md`
- `architecture.yml`
- `build.gradle`
- `docs/llm_guardrails.md`
- `pale-mirror-domain/src/main/java/io/farfrontier/palemirror/domain/DomainServices.java`
- `pale-mirror-domain/src/main/java/io/farfrontier/palemirror/domain/DomainCommandProcessor.java`
- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/PaleMirrorRuntime.java`
- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/materialization/MaterializationScheduler.java`
- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/observation/ObservationReconciler.java`
- `pale-mirror-domain/src/main/java/io/farfrontier/palemirror/domain/SettlementSimulation.java`
- `docs/crimson-audit-1.4.3.1.md`
- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/integration/crimson/`
- `scripts/dedicated-restart-harness.sh`
- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/gametest/CoreRecoveryGameTests.java`
- `pale-mirror-neoforge/build.gradle`
