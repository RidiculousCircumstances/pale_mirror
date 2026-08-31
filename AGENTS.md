# Pale Mirror engineering guardrails

## Continuity

Maintain one compact Continuity Ledger in `CONTINUITY.md`. It is the canonical
session brief that survives context compaction; do not rely on older chat
messages unless their durable facts are reflected there.

At the start of every assistant turn, read `CONTINUITY.md` before acting. Update
it only when goals, constraints/assumptions, key decisions, progress state,
important verification evidence, open questions or the active working set
change. Keep it factual and at most 1000 lines; never store dialogue summaries,
long changelogs, raw test logs or stale file inventories in the active ledger.

If history must be retained, archive the previous ledger under `docs/archive/`
and reference that archive from the compact active ledger. If context is
missing, reconstruct only supported facts, mark gaps `UNCONFIRMED`, ask targeted
questions when necessary and continue without inventing state.

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

`update_plan` is a short-cycle execution checklist. `CONTINUITY.md` is the
long-lived intent and state anchor. Keep them synchronized at the goal/state
level without copying plan micro-steps into the ledger.

Start user-facing work updates with a short **Ledger Snapshot** containing:

- Goal
- Now / Next
- Open questions

Show the full ledger only when it changed or the user requests it. If unrelated
architectural flaws or code smells are discovered, report them separately.

## Mandatory project skills

Project skills live in `.agents/skills`. Before acting on a matching task,
read the complete `SKILL.md` for every matching skill and announce the skill
use in the commentary update. This routing is mandatory even when the host UI
does not list repo-local skills; open the file directly instead. A summary in
this file never substitutes for reading the selected skill.

- Materialization, Foundry reports/rules, physical ownership, unsupported or
  obstructed structures: `.agents/skills/pm-foundry-audit/SKILL.md`.
- Screenshots, coordinates, settlement/mine/rail aesthetics, terrain
  integration, Blockbench creature modelling, or visual acceptance:
  `.agents/skills/pm-visual-audit/SKILL.md`.
- Creating or mutating an image-to-3D experiment, generated turntable,
  pose/depth reconstruction, model-generated geometry, texture inference,
  rigging proposal or Automodel adapter: `.agents/skills/pm-automodel/SKILL.md`.
  Merely locating, copying or delivering an already generated artifact does
  not activate this skill.
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

## Frontier v3 causal-test workflow

For a change to Frontier v3 materialization, physical observation, player
causality, test-pilot behaviour, scenario execution or restart/recovery,
select the smallest relevant checked-in declarative scenario under
`tools/frontier-v3-test-pilot/scenarios/`; do not substitute an ad-hoc client
session or the full suite. A new causal flow must add or extend a scenario
whose structure is:

1. a read-only fixture declaring its required canonical/world preconditions;
2. ordinary player setup and one or more evidence-bearing player actions;
3. a terminal domain assertion (not a transient phase), plus its bounded PMV3
   correlation trace.

Add a semantic camera check and linked frame when the change has a player
visible claim. Add a graceful or abrupt restart split when it changes durable
state, non-replayable physical effects, or recovery behaviour. Fixtures,
diagnostics, semantic checks and scenario setup are evidence only: they must
not force-load chunks, mutate canonical state or edit world files. Use at most
one visible native client on `DISPLAY=:0`; the scenario runner owns its
disposable seeded world and the normal player connection. Keep fast unit and
Node schema tests for every edit, then apply the existing critical-code gate
before committing. A live scenario is evidence for that specific flow, not a
replacement for product/visual acceptance.

Visual `frames` are test-only capture barriers, never ordinary pilot actions.
They default to `presentation: clean`: the isolated client closes incidental
screens, hides generic HUD/chat, waits for render settling, and does not run
the next action until the X11 capture helper acknowledges its exact frame.
Use `presentation: player` only when the player UI itself is the assertion;
never add an ad-hoc HUD toggle to a scenario.

## Frontier v3 daily and scale workflow

- For a v3 materialization edit, run focused pure/Node tests and the smallest
  matching named GameTest slice first (`runFrontierV3SceneGameTestServer` for
  HOT/COLD scenes; `runFrontierV3EconomyGameTestServer` for exact items,
  production transforms or owned boards); reserve the complete critical gate for a commit
  or milestone. A fast slice is evidence only for its changed boundary, not
  permission to skip the final gate.
- Every HOT/COLD change needs both its ordinary path and a negative or recovery
  path. A test that merely observes `PREPARED` or `READY` is insufficient when a
  terminal domain result is available.
- A change that raises a physical-scene or active-mob limit requires a
  reproducible same-seed JFR capture and a causal proof of operation → lease →
  actor/cargo ownership. TPS alone is not acceptance evidence.

## Frontier v3 extension discipline

- `docs/frontier-v3-architecture-audit.md` is the active defect register and
  `tools/engineering/frontier_v3_architecture_debt.yml` is its machine-checked
  debt ceiling. Ordinary changes may only keep or lower every ceiling. Raising
  one requires an accepted architecture amendment with a new finding and exit
  gate; never update the baseline merely to make a build pass.
- Do not add a bomber, siege or other scene family until the closed
  `SceneBehavior` registry owns admission, HOT effects, drain, recovery and
  diagnostics. Type-specific branches outside registered behavior are limited
  to explicit sealed-codec compatibility and read-only formatting.
- Do not add a command, scheduled kind, event family or physical executor to a
  composition-root switch/manual tick list. Register exactly one owner in the
  relevant closed deterministic process or staged executor registry, including
  exactly one stable codec for every process payload; duplicate, missing,
  undeclared and cyclic ownership must fail a focused negative test.
- Never persist an enum with `ordinal()` or decode it with `values()[tag]`.
  Use explicit stable, non-reused wire tags and retain old-byte recovery tests.
- Process/support code must not directly reconstruct the complete
  `FrontierWorldState`. Use its named owned update boundary; full construction
  belongs only to fresh bootstrap and versioned hydration.
- Fixture builders, profile catalogs and fixture bootstrap selection are
  test/moddev classpath code and must be absent from the production JAR. A run
  ID is correlation evidence, never authority to select a fixture.
- Put tunable cadence, gains, radii and costs in the persisted hashed
  `FrontierRuleset`; keep only safety maxima and algorithmic invariants as code
  constants. Do not silently change recovered-world rules through a binary
  update.

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
