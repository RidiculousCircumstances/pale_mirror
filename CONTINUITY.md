# Continuity Ledger

## Goal (success criteria)

- Maintain a server-authoritative Pale Mirror core slice with explicit,
  enforceable architectural and LLM-change guardrails.

## Constraints/Assumptions

- Java 21; Minecraft 1.21.1; NeoForge 21.1.248.
- `pale-mirror-domain` remains independent of Minecraft and NeoForge.
- Core slice must work without Crimson Curse; Crimson remains a soft adapter.

## Key decisions

- Canonical state resides in Pale Mirror SavedData/domain state, not chunks,
  entities, adapters, or quest presentation.
- Architecture guardrails are mechanical Gradle checks plus focused tests;
  `architecture.yml` is the compact boundary map.
- Current work imports selected general practices from Stream Miner, adapted to
  this Java/NeoForge repository rather than copied verbatim.

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
- Snapshot schema is v5. Legacy/unknown snapshot formats stop server startup
  before Minecraft can silently replace canonical SavedData; manual reset is
  required after an external backup.
- Datapack scenarios now pin their authored stage/capability snapshot and
  version into each active instance. Capability loss is explicit `BLOCKED` /
  resume state, and `NO_SCENARIO` is causally recorded.
- The first settlement/resource-flow expansion is active: an infected test mine
  emits `SETTLEMENT_SUPPLY_DISRUPTED`, removes iron supply, and lowers defense;
  recovery restores both.
- Crimson Curse 1.4.3.1 checksum and L1 audit completed. Its public surface
  only changes global scoreboards, so `CrimsonAdapter` remains explicitly
  `BLOCKED`; the audit is documented in `docs/crimson-audit-1.4.3.1.md`.

### Now

- Verify and commit the authoring, settlement-supply, and Crimson-audit slice.

### Next

- Add the standalone dedicated-server restart/crash harness. It must exercise
  a final packaged JAR, not just the in-process SavedData GameTest.

## Open questions

- UNCONFIRMED: dedicated-server restart/crash harness has not yet completed its first full run.

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
- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/gametest/CoreRecoveryGameTests.java`
- `pale-mirror-neoforge/build.gradle`
