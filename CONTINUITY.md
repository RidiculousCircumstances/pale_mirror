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

### Now

- Commit the runtime-test coverage and the ModDev wiring fix.

### Next

- Add persisted materialization-plan operations and the dedicated-server
  restart/crash harness as the next core-slice expansion.

## Open questions

- UNCONFIRMED: Crimson Curse 1.4.3.1 public L1 surface can safely materialize a local controller.
- UNCONFIRMED: dedicated-server restart/crash harness has not yet completed its first full run.

## Working set

- `AGENTS.md`
- `CONTINUITY.md`
- `architecture.yml`
- `build.gradle`
- `docs/llm_guardrails.md`
- `pale-mirror-domain/src/main/java/io/farfrontier/palemirror/domain/DomainServices.java`
- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/PaleMirrorRuntime.java`
- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/gametest/CoreRecoveryGameTests.java`
- `pale-mirror-neoforge/build.gradle`
