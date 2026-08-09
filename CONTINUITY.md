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

### Now

- Commit the transferred guardrail baseline, then synchronize domain
  coordination with its bounded-responsibility rules.

### Next

- Refactor the current domain coordination to match the new bounded-responsibility rules.
- Run the critical-code verification profile and commit the synchronized state.

## Open questions

- UNCONFIRMED: Crimson Curse 1.4.3.1 public L1 surface can safely materialize a local controller.
- UNCONFIRMED: dedicated-server restart/crash harness has not yet completed its first full run.

## Working set

- `AGENTS.md`
- `CONTINUITY.md`
- `architecture.yml`
- `build.gradle`
- `docs/llm_guardrails.md`
