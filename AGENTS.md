# Pale Mirror engineering guardrails

## Continuity

`CONTINUITY.md` is the compact, canonical session brief. At the start of a
non-trivial task, read it. Update it when the goal, a constraint, a decision,
the `Done` / `Now` / `Next` state, or verification evidence changes. Keep it
under 120 lines, factual, and free of chat transcripts or test logs.

Use these headings exactly:

- Goal (success criteria)
- Constraints/Assumptions
- Key decisions
- State
  - Done
  - Now
  - Next
- Open questions
- Working set

If a fact is missing, record `UNCONFIRMED`; do not invent it.

## Mandatory project skills

Project skills live in `.agents/skills`. Before acting on a matching task,
read the complete `SKILL.md` for every matching skill and announce the skill
use in the commentary update. This routing is mandatory even when the host UI
does not list repo-local skills; open the file directly instead. A summary in
this file never substitutes for reading the selected skill.

- Materialization, Foundry reports/rules, physical ownership, unsupported or
  obstructed structures: `.agents/skills/pm-foundry-audit/SKILL.md`.
- Screenshots, coordinates, settlement/mine/rail aesthetics, terrain
  integration, or visual acceptance: `.agents/skills/pm-visual-audit/SKILL.md`.
- Chunk generation/loading, planner CPU, JFR, C2ME/DH, TPS, watchdogs, or
  performance changes: `.agents/skills/pm-worldgen-performance/SKILL.md`.
- Build/package/release readiness, completion claims, or pre-commit gates:
  `.agents/skills/pm-release-verification/SKILL.md`.
- Test-server rebuild, reset, deploy, restart, logs, pack/client installer, or
  live-server validation: `.agents/skills/pm-test-server-ops/SKILL.md`.
- Product review, version scope, player promise, playtest gates, or roadmap:
  `.agents/skills/pm-product-review/SKILL.md`.

When scopes overlap, use the smallest matching set. Structural visual defects
require both visual and Foundry skills. Verify an implementation before using
the server-operations skill to deploy it. Product decisions precede technical
implementation; release verification follows it.

## Sources of truth

- `architecture.yml` owns component boundaries, ownership, invariants, and
  critical flows.
- `CONTINUITY.md` owns the current engineering state.
- Source code owns implementation details.

If code contradicts the architecture map, report the mismatch and change one
side deliberately in the same task. Do not silently let either drift.

## Before changing code

1. Read `CONTINUITY.md` and the relevant part of `architecture.yml`.
2. Name the risk: `docs`, `small-code`, or `critical-code`.
3. Identify the source of truth for every mutable state change.
4. Identify the owning layer and the observable failure/recovery path.
5. Make the smallest coherent change; do not add speculative compatibility
   paths or unrelated cleanup.

`critical-code` includes canonical world state, persistence, migrations,
simulation, scenarios, materialization, observers, adapters, and server
lifecycle. A test must cover a negative or recovery path for such changes.

## Architecture rules

- `pale-mirror-domain` is pure Java: it must not import Minecraft, NeoForge,
  adapters, persistence, or wall-clock/random APIs.
- `pale-mirror-neoforge` owns Minecraft/NeoForge integration, persistence,
  observation, scheduling, and materialization execution.
- `api` is a narrow experimental adapter SPI and must not expose `internal`
  implementation types.
- Domain state is authoritative. Minecraft, adapters, and physical objects are
  observed/materialized representations, never a second source of truth.
- Every long-lived mutable record has one owner, explicit identity, and a
  retention/compaction or cleanup story.
- Executors check preconditions and postconditions. Unknown/player-owned world
  changes are conflicts to report, never data to overwrite by default.
- Fail closed and visibly on an invariant violation. Do not conceal it with a
  fallback unless the architecture map explicitly permits that fallback.

## Verification and commits

- `docs`: run `git diff --check` and `./gradlew guardrails`.
- `small-code`: run focused tests and `./gradlew guardrails check`.
- `critical-code`: run focused tests plus
  `./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build
  :pale-mirror-neoforge:verifyPackagedJar`; report any unavailable dedicated
  server/restart harness explicitly.

Before every commit, confirm that generated/local files are not staged,
architecture boundaries remain valid, failures are observable, and no
unbounded state or silent fallback was introduced. Use concise Conventional
Commit messages (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`).
