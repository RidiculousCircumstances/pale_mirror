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

### Now

- Commit the synchronized bounded-responsibility domain state.

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
