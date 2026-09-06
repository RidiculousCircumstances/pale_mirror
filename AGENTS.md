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

### Managed engineer / executor workflow

- `docs/engineering-agent-protocol.md` is the accepted delivery protocol.
  The supervising engineer owns specifications, normative docs, the ledger
  and independent acceptance; it must not edit source, tests, scripts, build/CI
  or executable configuration. Only the assigned `gpt-5.6-terra` executor with
  reasoning `high` writes implementation within an approved work order.
- Read the ledger's active order under `docs/work-orders/` before working.
  A READ_ONLY assignment grants no writes or test launches. The executor
  reports proposed ledger updates rather than editing the engineer's ledger.
- Explicit engineer acceptance is required before the next order or F0 slice.
  Preserve existing WIP and obtain previous-writer handoff before granting a
  new writer; finish identity-bound measurements before changing their inputs.
- Delegate bounded engineering outcomes: Terra owns diagnosis, local design,
  helper/file choices and focused test iterations inside the approved boundary.
  Expected files are guidance unless a specific safety constraint says otherwise.
  Engineer reviews stable milestones and invariants, not every local choice;
  do not routinely duplicate the executor's investigation or full test runs.
  Public/persistent meaning, owner boundaries, requirements and external or
  destructive authority still require explicit agreement.

### Canonical sources

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
- A `bastion/mobs/empty` GameTest must address only its own template interior.
  Coordinates beyond that tiny template can overlap a neighbouring parallel
  test cell; use one local interior fixture position rather than an arbitrary
  large offset, and prove the affected full GameTest gate once.

## Frontier v3 materialization completeness discipline

- `docs/frontier-v3-execution-semantics.md` is mandatory for HOT/COLD, scenes,
  physical custody, movement knowledge, aftermath and recovery changes. It
  defines exact versus calibrated guarantees and augments the existing F0 exit
  gates. At the next safe F0.VA measurement boundary, align outstanding family
  descriptors/tests before starting the next slice; preserve running evidence
  and WIP. Documentation acceptance is not implementation completion.
- Presentation demand is not physical interaction eligibility. No COLD writer
  may compete with an observer-free live physical custodian. Unknown terrain
  is not exact knowledge; scene boundaries do not shield effects; a flushed WAL
  intent is not proof of an atomically saved player/container consequence.

- Before extending a v3 process, scene, inventory surface, physical effect or
  movement family, read `docs/frontier-v3-seamless-foundation.md`. Its active
  F0 correction slices block new MAT breadth until their stated exit gates and
  recurrence guards pass; an older green endpoint/HOT test does not waive them.
- Classify every canonical process with
  `docs/frontier-v3-materialization-completeness.md` before calling it
  materialized. Report M0 canonical, M1 physical endpoint, M2 continuous HOT and
  M3 player-comprehensible evidence separately; never promote one level from a
  lower-level test.
- A duration-bearing loaded process must retain and visibly use its exact named
  worker/team, machine or distributed physical frontier. Generic ambient motion,
  final inventory/block state, diagnostics, a board or before/after frames are
  not M2 evidence.
- Permit one-turn physical execution only for a genuinely atomic interaction at
  one semantic station with every exact participant/input/target present, or an
  intrinsically instantaneous effect. Bootstrap projection is not permission to
  compress later construction, work, lifecycle or environmental change.
- A new process or executor must update the materialization inventory and add
  ordinary, intervention/negative and restart evidence at its required level.
  P0 `MAT-*` debt blocks Wave 6 completion; do not close it by weakening the
  process, hiding its duration or asserting only its endpoint.

## Frontier v3 terrain and movement discipline

- New canonical movement state must use typed surface/body/port/topology values;
  do not extend the historical convention where one `BlockPosition` may mean a
  support block, feet-air cell, facility anchor or transport node.
- A new route, assembly, migration or scene approach may not assume constant Y,
  a fixed compass-facing entrance or an offset from a structure centre. It must
  consume a bounded immutable three-dimensional traversal topology and a
  semantic facility port compiled by the owning plan.
- COLD advances only a retained topology edge/cursor. HOT delegates local
  collision and navigation to the registered physical movement provider and
  advances that same cursor only from observed arrival. The provider may find
  a bounded local path inside the process-owned navigation envelope, but may
  not leave it, skip a checkpoint, teleport, or select a different semantic
  entrance or strategic route.
- Every movement-bearing extension needs a focused non-flat fixture plus a
  blocked or damaged edge/entrance case. The flat graybox is one provider test,
  not evidence that the architecture supports real terrain.
- Pedestrian/bioform traversal and rail topology are distinct capability
  graphs. Future Create integration may implement the rail physical provider,
  but may not introduce a second route, cargo or movement authority.

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
- Recognized player/world actions are ordinary typed domain disruptions with
  an owner and recovery/abandonment path. Unclassified or ambiguous physical
  drift is an isolated reconciliation conflict, never data to overwrite by
  default; only canonical invariant or persistence corruption quarantines the
  whole frontier instance.
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

## Recurrence prevention

- Treat every native-scenario, watchdog, persistence or deployment failure as a
  candidate product defect until its exact cause is classified. Do not lengthen
  a timeout, retry a world or suppress a log line as the fix.
- Before rerunning a failed causal flow, add the smallest deterministic
  regression or preflight that would have failed first: a pure/state test for
  canonical rules, a codec/registry test for durable payloads, a bounded
  performance guard for tick work, or a script-level dry-run/preflight for
  deployment. Record the cause and remaining evidence in the architecture
  audit/ledger.
- A deployment may use only a verified clean detached source ref and must
  prove Java/runtime/profile compatibility, exact target/reset paths, pinned
  artifact checksum, fresh startup evidence and no new quarantine. A running
  service or stale log line alone is never a successful deployment.
