# Pale Mirror engineering guardrails

## Continuity

Maintain one compact Continuity Ledger in `CONTINUITY.md`. It is the canonical
session brief that survives context compaction; do not rely on older chat
messages unless their durable facts are reflected there.

At session entry or after context loss, read `CONTINUITY.md` before acting.
Reuse already-read unchanged instructions during continuous work; automatic goal
continuations do not require rereading them. Refresh changed files when another
writer, user update or handoff can affect current authority/state. Update
it only when goals, constraints/assumptions, key decisions, progress state,
important verification evidence, open questions or the active working set
change. Keep it factual and at most 1000 lines; never store dialogue summaries,
long changelogs, raw test logs or stale file inventories in the active ledger.
Aim for roughly 1000 words, well below that mechanical line cap; link durable
receipts and historical decisions instead of accumulating successive handoffs.

If history must be retained, archive the previous ledger under `docs/archive/`
and reference that archive from the compact active ledger. If context is
missing, reconstruct only supported facts, mark gaps `UNCONFIRMED`, ask targeted
questions when necessary and continue without inventing state.

Keep the current objective, authority, implementation location, material results
and next unresolved problem easy to find. No exact headings, separate execution
checklist or duplicate plan synchronization is required. Mark missing facts as
unconfirmed; do not invent them.

Give concise updates for material progress, decisions or blockers; no mandatory
Ledger Snapshot or full-ledger dump. Report unrelated findings separately.

## Project reference skills

Project reference skills live in the canonical governance `.agents/skills`.
The routes below are a reference index, not mandatory workflow triggers merely
because a task mentions a topic. Consult a skill when its procedure or technical
detail is needed; do not load a bundle as a prerequisite to reading or fixing
code. When selecting a skill, read it fully and follow the host's skill-use
rules. This does not override host-required skills or waive the project's
technical contracts, operational safety or claim-specific acceptance criteria.

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
- Unfamiliar ownership, cross-module impact, lifecycle tracing, or a repeated
  cross-layer defect: `.agents/skills/pm-repository-context/SKILL.md`.

When references overlap, use only what answers the unresolved question.
Structural visual defects still require physical-ownership and visual checks;
consult the corresponding references as needed. Verify an implementation before
deployment. A reference-reading sequence is not an implementation gate.

## Development assistance tooling

- Engineering capabilities are available through their named MCP surfaces or
  checked-in CLI equivalents. Check availability when a capability is needed;
  there is no mandatory all-tools smoke on session or worktree activation.
  Missing optional tooling does not block direct source analysis or repair.
  Report a limitation when it affects the task or an evidence claim.
- `docs/agent-assist-toolchain.md` defines the advisory runtime-evidence,
  change-selection, LSP, architecture and profiling tools. They reduce context
  and diagnosis cost; none owns product truth, acceptance or repair authority.
- For an unfamiliar cross-layer defect, choose direct source reading,
  repository context or symbol/reference navigation by the unresolved question.
  No prescribed tool sequence or onboarding campaign is required. An index is
  not a substitute for reading the relevant canonical/source anchors.
- Before a changed-candidate verification cycle, the change selector may build
  the smallest declarative plan. It never executes tests and never overrides
  the executor's technical judgment. Unknown ownership widens visibly; native
  evidence always requires an explicit scenario.
- Runtime evidence queries consume only already-produced bounded files. A
  `stale`, `partial`, `not_found` or `trace_incomplete` answer is not proof of
  absence. The navigator is not the OBS-001 producer and cannot complete a
  diagnostic-plane criterion.
- Use existing JFR/jcmd evidence first for a TPS or caller-path question.
  Attach the pinned async-profiler only to an explicit same-user task-owned JVM,
  for a bounded duration and a named unresolved hypothesis; its launcher is a
  dry-run unless execution is explicitly selected. Never attach to an unrelated
  live service.
- Availability does not mean ritual invocation. The executor chooses the matching
  capability by expected information gain: repository context for unclear
  ownership/impact, runtime context for retained evidence, the selector when it
  helps choose affected checks, Serena for a remaining symbol seam,
  ArchUnit through its normal applicable Java gate and the profiler only for the
  guarded unresolved performance case above. A CLI fallback is sufficient for a
  long-lived TUI that cannot refresh its MCP catalog; a missing named surface
  does not require a session restart when the CLI provides the capability.

## Sources of truth

### Current execution authority

The [execution protocol](docs/engineering-agent-protocol.md) is the sole workflow
source. The user's current assignment and compact ledger determine roles.
Currently main alone implements the ledger-named resident-life/resource cut;
the SA audit is paused, Terra is stopped and subagents are prohibited. Older PM-only/sole-Terra rules in skills, archived
orders or source-checkout copies have no assignment authority.

Read the active order for scope and acceptance, not historical chronology.
The executor owns implementation, diagnosis, methods and ordinary scoped retries.
Skills supply technical constraints, not a model choice or another approval chain.
Preserve existing product, ownership, recovery, physical-evidence and safety
requirements. No new publication/deployment/reset authority is created.

Do not turn historical workflow prescriptions into additional blocking steps.
Required outcomes and safety boundaries remain binding; the executor chooses
the implementation and diagnostic sequence. Documentation records material
decisions and results, not permission to take each ordinary development step.

There is no scheduled self-review/report ceremony or fixed CI-worker count.
Use parallelism when useful and reassess a stalled approach based on information
gain. Neither paperwork nor optional tooling is a prerequisite for source repair.
This changes workflow only, not product acceptance or safety constraints.

### Canonical sources

- `docs/frontier-v3-contract.md` owns player promises and system responsibility
  contracts; `docs/frontier-v3-execution-semantics.md` owns the shared execution,
  authority, projection/action/aftermath and retirement protocol.
- `docs/frontier-v3-domain-relations.md` and the diagnostic-plane contract own
  their specialized relationship and explanation rules. The implementation
  plan/hardening pipeline own stage ordering; neither claims code conformance.
  ARC-001 is the mandatory adoption checkpoint after the current F0.6R3 fix;
  only the ledger-named order supplies execution authority.

- `architecture.yml` owns component boundaries, ownership, invariants, and
  critical flows.
- `CONTINUITY.md` owns the current engineering state.
- Source code owns implementation details.

If code contradicts the architecture map, report the mismatch and change one
side deliberately in the same task. Do not silently let either drift.

The development-only repository context engine joins canonical governance to a
local structural code graph. Consult `docs/repository-context-engine.md` when
that capability helps answer an unresolved question. Direct source reading is
equally valid for cross-layer work; no context query or index refresh is a
prerequisite. Graph output is not authority. Report unavailability only when it
affects the task or evidence, and continue from canonical docs and source.

## Before changing code

Apply the protocol's evidence gate: a concrete contradiction in the active
source path can justify a fix without first reproducing it in a client, but a
plausible failure story cannot. Prioritize a wired end-to-end product result
over inactive infrastructure while preserving required safety and acceptance.

Understand the affected owner, authoritative state and failure/recovery path,
reusing retained context. Make a coherent scoped change without speculative
compatibility paths or unrelated cleanup. No risk label or completed checklist
is required before editing; verification follows the actual affected risk.

Changes affecting canonical state, persistence, migrations, simulation,
materialization, observation, adapters or server lifecycle require verification
of the affected negative or recovery behavior. Select this coverage from the
semantic change, not the file's classification alone. Reuse applicable existing
coverage; a comment, formatting or behavior-preserving edit does not by itself
require a new negative test or another runtime campaign.

## Frontier v3 causal-test workflow

For iterative diagnosis and repair, choose direct code analysis, focused tests,
instrumentation or an exploratory client session according to the actual
uncertainty. No native scenario is required per edit, and exploratory evidence
must be labelled with its limits. When claiming repeatable native acceptance
for Frontier v3 materialization, physical observation, player causality,
test-pilot behaviour, scenario execution or restart/recovery, use a relevant
checked-in declarative scenario under `tools/frontier-v3-test-pilot/scenarios/`.
For a new causal flow, add or extend that scenario before claiming acceptance.
Its structure is:

1. a read-only fixture declaring its required canonical/world preconditions;
2. ordinary player setup and one or more evidence-bearing player actions;
3. a terminal domain assertion (not a transient phase), plus its bounded PMV3
   correlation trace.

Add a semantic camera check and linked frame when the change has a player
visible claim. Add a graceful or abrupt restart split when it changes durable
state, non-replayable physical effects, or recovery behaviour. Native fixtures,
diagnostics, semantic checks and scenario setup are evidence only: they must
not force-load chunks, mutate canonical state or edit world files. Isolated
unit tests may construct initial state, never fake an outcome and call it
native evidence. Use at most one native client on the task-authorized display
(physical `DISPLAY=:0` only if available; a task-private Xvfb is not human
visual acceptance). The runner owns its disposable seeded world and ordinary
player connection. Use relevant focused tests during iteration; apply full
gates at integration milestones/releases, not private WIP checkpoints, as the
protocol specifies. A live scenario is evidence for that specific flow, not a
replacement for product/visual acceptance.

Visual `frames` are test-only capture barriers, never ordinary pilot actions.
They default to `presentation: clean`: the isolated client closes incidental
screens, hides generic HUD/chat, waits for render settling, and does not run
the next action until the X11 capture helper acknowledges its exact frame.
Use `presentation: player` only when the player UI itself is the assertion;
never add an ad-hoc HUD toggle to a scenario.

For every duration-bearing player-visible flow, follow
`docs/frontier-v3-runtime-verification.md`. The current exact subject owns a
versioned progress obligation in canonical simulation ticks. The graphical
runner must automatically reject unexplained non-progress, false active/pending
presentation, canonical/physical divergence, an evidence gap or an incomplete
terminal story and retain one bounded incident capsule with a linked frame.
Wall-clock waiting, raw log tails, a changing intermediate stage or a final
endpoint alone cannot satisfy that obligation. A complete machine-readable
promotion receipt is required for a formal HUMAN_CANDIDATE claim, not for
exploratory inspection or a targeted human diagnostic check. Such checks may
precede automated acceptance when they answer a concrete unresolved question;
label the build and missing evidence honestly. Neither a diagnostic visit nor
automated acceptance by itself establishes M3.
Runtime verification and optional OTel/JFR are read-only evidence layers and
must never mutate, repair, advance or reclassify canonical state.

## Frontier v3 daily and scale workflow

- For v3 materialization work, choose focused checks for the affected boundary;
  there is no compulsory pure/Node/GameTest sequence or native run per edit.
  Available named slices include `runFrontierV3SceneGameTestServer` for HOT/COLD
  scenes and `runFrontierV3EconomyGameTestServer` for exact items, production
  transforms or owned boards. Reserve the complete critical gate for an
  integration milestone/release. A fast slice proves its changed boundary,
  not the final milestone; private checkpoints follow the protocol.
- Verify changed HOT/COLD behavior with relevant ordinary and negative/recovery
  coverage, reusing evidence whose dependencies remain unchanged. This is not
  a fresh pair of test runs for every edit. Observing `PREPARED` or `READY` alone
  does not establish a claimed terminal domain result.
- A change that raises a physical-scene or active-mob limit requires a
  reproducible same-seed JFR capture and a causal proof of operation → lease →
  actor/cargo ownership, as the scale contract requires. This applies to a
  raised limit, not every bounded-call-path correction. TPS alone is not
  acceptance evidence.
- A `bastion/mobs/empty` GameTest must address only its own template interior.
  Coordinates beyond that tiny template can overlap a neighbouring parallel
  test cell; use one local interior fixture position rather than an arbitrary
  large offset, and prove the affected full GameTest gate once.

## Frontier v3 materialization completeness discipline

- `docs/frontier-v3-execution-semantics.md` is mandatory for HOT/COLD, scenes,
  physical custody, movement knowledge, aftermath and recovery changes. It
  defines exact versus calibrated guarantees and augments the F0 exit gates.
  Keep changed family descriptors/tests aligned with it and preserve WIP.
  Documentation acceptance is not implementation completion.
- Presentation demand is not physical interaction eligibility. No COLD writer
  may compete with an observer-free live physical custodian. Unknown terrain
  is not exact knowledge; scene boundaries do not shield effects; a flushed WAL
  intent is not proof of an atomically saved player/container consequence.

- `docs/frontier-v3-seamless-foundation.md` defines extension constraints for
  processes, scenes, inventory surfaces, physical effects and movement. Consult
  the relevant constraints when needed; reuse unchanged retained context. Its active
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

- Every target-directed HOT pedestrian move enters the shared goal navigator,
  including ambient, assembly, medical, production and tactical callers.
  Callers cannot drive a parallel direct actuator or stop a native path behind
  its owner. Station-local work gestures and ordinary gravity have no route
  authority. Acquiring one actuator must not recursively cancel its parent
  route; repeated same-goal refresh preserves its leg and in-flight motion.
- New canonical movement state must use typed surface/body/port/topology values;
  do not extend the historical convention where one `BlockPosition` may mean a
  support block, feet-air cell, facility anchor or transport node.
- Goal-migrated callers supply a semantic order, known-route hint and explicit
  hard scope, not local waypoint selection or route-width obstacle policy.
  Shared HOT navigation owns segmentation and physical-path acceptance;
  an accepted detour may leave an advisory stripe but never true hard bounds.
  Derive the ephemeral corridor from the accepted path. Intermediate leg
  arrival cannot award semantic completion. Explicit topology/formation
  envelopes retain their hard meaning; COLD knowledge and authority handoff
  are not replaced by physical pathfinding.
- A new route, assembly, migration or scene approach may not assume constant Y,
  a fixed compass-facing entrance or an offset from a structure centre. It must
  consume the owning plan's bounded three-dimensional known geometry and typed
  semantic target/port; an immutable traversal topology remains valid for
  families that explicitly retain one.
- For a topology-owned family, COLD advances a retained edge/cursor and HOT
  advances that same cursor only from observed arrival. For a family migrated
  under `docs/frontier-v3-goal-navigation.md`, COLD advances the retained
  semantic goal against known geometry and HOT delegates bounded local motion
  to its registered provider, recording only observed goal arrival. The
  provider may not leave true hard task/topology scope, skip semantic work,
  teleport, or select a different task/strategic goal. Do not keep a hidden
  per-block cursor as a second authority in a migrated family.
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
  Use explicit stable, non-reused wire tags, current-format recovery tests and
  incompatible-old-format rejection tests under the fresh-world-only policy.
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

- Shared coordinators own cross-owner protocol, not participants' internal
  business rules. A generic execution, navigation, custody or recovery owner
  must not inspect a concrete family's job/stage/cursor/recipe to decide its
  safe checkpoint or domain outcome. The owning family supplies that decision
  through an explicit typed capability/Strategy registered under its declared
  key. The coordinator validates exact subject/owner and current immutable
  state/epoch, arbitrates exclusive authority and performs the common transition.
  A checkpoint assessment is not an acknowledgement of released ownership.
  Closed registration rejects missing, duplicate and mismatched capabilities;
  unknown families never inherit a permissive default. Moving family-specific
  branches into a helper beside a coordinator does not establish this boundary.
  Adding a supported family must not require editing the coordinator algorithm.
  Composition may name implementations; generic protocol may not rediscover
  them from ID prefixes, runtime types, coordinates or collection membership.
  Review integration and remaining bypasses, and retain focused negative tests
  for wrong/stale evidence and competing authority. Historical coordinates are
  evidence of prior position, never a substitute for completion or ownership.
- Any semantic owner, durable type, capability key, authority epoch or
  behavior-dispatch identity that can affect canonical mutation, persistence,
  recovery, custody or retirement must be an explicit typed value supplied by
  its authoritative producer and, when durable, encoded under its own stable
  non-reused wire tag. Production code may not infer it from another enum or
  kind, an ID/string prefix, coordinates, runtime class, collection membership,
  neighbouring state or a default/fallback. Missing, unknown, stale or
  mismatched identity fails closed before dispatch, replay or mutation. Direct
  decoding of the value's own wire tag and validation of declared compatible
  values are not inference; read-only UI/index projections may derive labels
  only when they cannot become authority. Fixture convenience may exist only
  on test/moddev source sets and may not create an ownerless production API.
  A typed role name is one nominal domain meaning: it may not mean a project
  for one owner and an assault, service job or other subject type for another.
  Validate the closed owner + operation kind + exact role schema together;
  validating those dimensions independently is insufficient. Polymorphic
  placeholders such as `ownerOrProject` merely rename inference and are
  forbidden on authoritative production paths.
  An identifier namespace may validate the spelling of an identity already
  carried by a typed record; it may never supply that record's type, owner or
  dispatch key. Likewise, collection membership may validate an explicitly
  declared relationship but may not discover which relationship or owner was
  intended. Authoritative branching on either convention is prohibited and
  must be rejected by a mechanical architecture/recurrence check.
  This is a strict no-inference boundary, not a preference for explicit code.
  Every authoritative production value must enter its first admissible boundary
  with one complete nominal declaration; a generic ID, nullable type, wildcard,
  polymorphic placeholder or partially typed carrier is not an admissible
  intermediate from which an owner or type may be completed later. A closed
  registry may prove that the producer-declared owner, kind and schema form one
  exact registered tuple, but it may not scan candidates, choose the only
  compatible entry, inspect payload shape or otherwise discover a missing
  dimension. Legacy or recovered data lacking that complete declaration is an
  explicit unsupported schema/conflict and is rejected at the version boundary,
  never repaired by a heuristic. Convenience resolvers, fallback classifiers
  and single-candidate defaults are forbidden even when their answer would be
  deterministic. The affected composition must have negative coverage that
  removes or forges each declaration dimension independently and proves failure
  before any behavior selection or state mutation.
- `pale-mirror-domain` is pure Java: it must not import Minecraft, NeoForge,
  adapters, persistence, or wall-clock/random APIs.
- Durable cross-owner identity follows
  `docs/frontier-v3-domain-relations.md`: discovery is permitted only at
  admission, continuation uses retained exact typed relationships, each relation
  has one canonical owner, and the incoming/outgoing relation view is derived
  read-only state rather than a second authority.
- `pale-mirror-neoforge` owns Minecraft/NeoForge integration, persistence,
  observation, scheduling, and materialization execution.
- `api` is a narrow experimental adapter SPI and must not expose `internal`
  implementation types.
- Domain state owns semantic history. Minecraft supplies authoritative observed
  physical facts through typed observations and exclusive custody, not a second
  economic or process model. Never treat canonical projection as permission to
  overwrite an unclassified player change.
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

Choose affected checks from the actual change and applicable acceptance criteria.
The release-verification reference provides available gates and a module matrix;
consult it when needed, not as a mandatory prerequisite to source work or a
private checkpoint. The unified protocol governs focused iterations,
milestone/release acceptance and evidence reuse. A checkpoint is not a completion
claim; this does not waive an applicable release gate.

Inspect the actual staged diff before an authorized commit; exclude unintended
generated/local files. Architecture boundaries, observable failures and bounded
state remain implementation requirements, not a separate repeated pre-commit
audit or test campaign. Use concise Conventional
Commit messages (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `build:`).

### Private supplied-reference gate during migration

The user approved portable public CI plus a separately mandatory local
authentic-reference check. Full applicable local milestone/release acceptance
in the adopted monorepo requires the following positive evidence from its
`pale-mirror/` directory, not a repeat before every private WIP checkpoint:

```bash
PALE_MIRROR_HARVESTER_REFERENCES=/absolute/private/reference-root ./gradlew verifyHunyuan2mvSuppliedReferenceIntegration --no-daemon
```

Resolve the authorized actual path; do not use the example as a fixture.
Revalidate changes to its tool, test, wiring or reference; reuse unchanged
identity-bound accepted evidence. Missing/wrong/changed input must fail when
the check runs, never skip. Public mechanics/negative tests cannot discharge
the authentic positive or human likeness acceptance. Do not publish private
images or their derived fixtures. The original checkout retains its historical
test wiring until migration adoption; this command describes the candidate
only and does not authorize source edits in the original checkout.

## Recurrence prevention

- Classify native-scenario, watchdog, persistence and deployment failures from
  evidence; do not assume either product fault or harness fault. Correct an
  evidenced invalid test deadline when necessary, preserving a finite hang
  bound and semantic assertions. Increasing a timeout, retrying a world or
  suppressing a log line alone does not fix a product defect.
- Apply the protocol's failure classification and engineering-judgment rule
  before repeating a failed flow. Choose the code audit, focused discriminator,
  instrumentation or bounded runtime action with the best expected information
  gain for its cost; no tier is mechanically first. An impossible local
  reproducer must not block diagnosis. Record the material cause and remaining
  product evidence, not every failed command, in the audit/ledger.
- A deployment may use only a verified clean detached source ref and must
  prove Java/runtime/profile compatibility, exact target/reset paths, pinned
  artifact checksum, fresh startup evidence and no new quarantine. A running
  service or stale log line alone is never a successful deployment.
