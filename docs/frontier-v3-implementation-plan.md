# Pale Mirror Frontier v3 implementation plan

Status: approved execution plan for the target contract in
[`frontier-v3-contract.md`](frontier-v3-contract.md).

This is the implementation source of truth for Frontier v3. An engineer or
agent works only on the current wave recorded in `CONTINUITY.md`, proves that
wave's exit gate, commits it, and then advances the ledger. Later-wave code is
not added speculatively.

## Execution rules

- Treat every simulation, persistence, observation, materialization and runtime
  change as `critical-code`; include a negative or recovery case.
- Keep v3 greenfield. Do not import, wrap, subclass, call or fixture-gate
  `pale-mirror-domain`, `frontier.reference`, `SourceGraybox*` or Python.
- Preserve v2 as a frozen, independently activatable legacy runtime only until
  the cutover wave. Do not fix or extend it except for a P0 repository/build
  break discovered during transition.
- Use a fresh v3 test world and namespace. Never make a migration path from a v2
  world to satisfy a test.
- A disposable development fixture may establish a deterministic canonical
  precondition, but normal starts must always use the ordinary world profile;
  fixtures may never be an AI, planner, production or live-server fallback and
  must be absent from the production packaged JAR.
- Scene kinds use one persisted lifecycle and a closed NeoForge behavior
  registry. Add a new `SceneCause` with one behavior and its normal,
  negative/recovery tests; do not spread `if cause` branches through generic
  materialization, persistence or diagnostics.
- Each commit must leave `architecture.yml`, implementation and
  `CONTINUITY.md` consistent. No wave is complete from code existence alone.
- Run only the focused tests for the current behavior plus the risk-level gate
  required by `AGENTS.md`; manual and product evidence is recorded separately.
- Preserve both Git histories. Frontier source/docs belong only to the nested
  `pale-mirror/` repository; packaging/deployment changes belong to the outer
  repository and land only after a verified v3 artifact exists.

## Mandatory architecture hardening gate

The evidence-backed register in
[`frontier-v3-architecture-audit.md`](frontier-v3-architecture-audit.md) is part
of this plan. Its checked-in debt policy is a ceiling, not permission to add
similar code. Current Wave 5 breadth pauses before bomber, siege or another
scene/domain family until the following ordered corrections land:

1. **H0.1 — scene ownership.** Implement the closed pure/NeoForge
   `SceneBehavior` registries, remove logistics-only access from generic lease
   code and pass logistics plus assault normal/negative/restart evidence.
2. **H0.2 — fixture isolation.** Move fixture builders, profile selection and
   the one profile catalog to a moddev/test classpath; prove the production JAR
   and production lifecycle cannot select them.
3. **H0.3 — deterministic extension points.** Split command/scheduled/event
   ownership and payload codecs into closed process descriptors, move behavior
   out of `model`, and replace the 22-call physical loop with a
   dependency-checked staged executor registry.
4. **H0.4 — state and persistence safety.** Replace process-side positional
   `FrontierWorldState` reconstruction with named owned updates. Migrate every
   persisted codec out of `model` ownership and every enum family from source
   ordinals to explicit stable wire tags, retaining golden old-byte recovery
   tests.
5. **H0.5 — reproducible world rules (complete).** `FrontierRuleset` is an
   immutable persisted selector with a content hash. It owns cadence, radii,
   gains, economic rates, facility capacity and COLD combat output; recovered
   worlds resolve its exact installed selector or fail closed, while fixtures
   explicitly pin their test-only selected ruleset.
6. **H0.6 — complete-load proof (complete).** Instrument process planning,
   reduction, validation, allocation, queue lag and each physical stage without
   making telemetry canonical. A bounded `maxPendingSchedules` safety limit
   rejects bootstrap/recovery/transaction overlays before canonical mutation.
   The same-seed 12-settlement simultaneous-front pressure route proves total
   order and retained-WAL replay; a separate naturally loaded physical JFR
   route proves the current scene limit. Any future increase to queue shape,
   concurrency or physical body limits repeats both relevant proofs.

Each H0 item must lower the corresponding metric in
`tools/engineering/frontier_v3_architecture_debt.yml`. Raising a ceiling needs
an accepted architecture change, a new finding and a bounded removal gate.
After H0.1 and H0.2, already implemented scene behavior may continue to receive
bug fixes; no new breadth is authorized until H0.3. Wave 5 resumes after H0.5;
H0.6 is the entry gate for high-body-count Wave 6 scenes.

Hardening completion requires:

- all new architecture invariants pass the machine-readable contract tests;
- debt metrics reach their stated exit shape rather than merely staying below
  the current ceiling;
- old snapshot/WAL golden bytes and abrupt/graceful recovery pass;
- no fixture class/profile entry point is present in the production JAR;
- focused negative tests reject duplicate/missing process or executor owners;
- the critical-code gate passes before the hardening milestone commit.

## Target dependency graph

```text
pale-mirror-frontier (pure Java, canonical v3)
        ^
        |
pale-mirror-neoforge (lifecycle, WAL/snapshots, observations, physical execution)
        |
        +-- pale-mirror-visuals through immutable server-authored projections only
        +-- optional adapters through source-neutral API contracts only
```

`pale-mirror-frontier` has no dependency on the existing domain module. The
existing `pale-mirror-domain` remains available to unrelated Pale Mirror
features during transition, but no object crosses between its world state and
v3 canonical state.

## Stable interface design

Wave 1 creates these package-level boundaries before adding gameplay domains:

- `frontier.v3.api`: immutable commands, events, projections, observations,
  result/rejection types and adapter-safe IDs;
- `frontier.v3.kernel`: engine, transaction executor, ordered schedule,
  revisions, deterministic RNG and fixed-point primitives;
- `frontier.v3.model`: canonical aggregates and indexes, added by owning wave;
- `frontier.v3.process`: event handlers, scheduled processes and AI plans;
- `frontier.v3.persistence`: pure schemas/codecs and the `FrontierStore` port,
  with no filesystem or Minecraft implementation;
- `neoforge.internal.frontier.v3`: lifecycle, server-thread host, storage,
  materialization, observation, physical execution and operator diagnostics.

Required call shape:

```text
FrontierEngine.submit(FrontierCommand) -> CommandResult
FrontierEngine.advanceTo(SimInstant, WorkBudget) -> AdvanceResult
FrontierEngine.projection(ProjectionQuery) -> FrontierProjection
FrontierEngine.checkpoint() -> CheckpointImage

FrontierStore.recover(WorldId) -> RecoveryImage
FrontierStore.append(TransactionRecord, Durability) -> AppendReceipt
FrontierStore.installSnapshot(CheckpointImage) -> SnapshotReceipt
FrontierStore.compact(Revision) -> CompactionReceipt
```

`CommandResult` is accepted with transaction/revision IDs or rejected with a
typed reason. `AdvanceResult` reports completed and deferred due work plus
simulation lag. No method returns a mutable aggregate.

The NeoForge host runs the engine only on the Minecraft server thread. Immutable
planning inputs may later be evaluated off-thread, but their result must return
as a command with the pinned input revision and deterministic candidate order;
no such worker is introduced until profiling proves it necessary.

## Wave 0 — contract, guardrails and transition state

### Deliverables

- Add the stable v3 contract and this implementation plan.
- Add the planned v3 component, invariants, lifecycle and critical flows to
  `architecture.yml` while accurately retaining v2 as frozen-active.
- Extend Gradle guardrails so future v3 Java sources are scanned for forbidden
  Minecraft, NeoForge, legacy-domain, wall-clock and random references and for
  the existing 1000-line source limit.
- Mark `frontier-ai-contract.md` and source-reference documents as v2 historical
  contracts, not v3 requirements.
- Archive the pre-v3 continuity ledger and replace it with the current wave,
  verified evidence and next action.

### Exit gate

`git diff --check`, `./gradlew guardrails check`, a clean staged-file review,
and separate nested/outer repository status. No runtime or save behavior changes.

## Wave 1 — deterministic event kernel

### Deliverables

- Add `pale-mirror-frontier` to Gradle with Java 21 and JUnit, then change its
  architecture lifecycle from `planned` to `active-development`.
- Implement typed IDs, `SimInstant`, fixed-point scalar/ratio/position types,
  checked arithmetic and explicit rounding modes.
- Implement keyed counter RNG using world seed plus subsystem, subject,
  decision kind and ordinal. Provide stable golden vectors generated by v3
  itself, not Python.
- Implement immutable commands/events, revision compare-and-set validation,
  cause chains, atomic transactions and typed rejection.
- Implement the persisted due-action heap ordered by instant, priority and
  stable ID; cancellation and rescheduling are events, not mutable queue hacks.
- Implement `FrontierEngine` with one server-thread mutation guard, a bounded
  work budget, deterministic deferral and immutable projections.
- Add a minimal in-memory store for tests and pure codecs for every kernel type.
- Pin the first benchmark fixture and report command/event/schedule throughput,
  allocation and deterministic hashes on the project test machine.

### Tests

- Same seed and command stream produces byte-identical event/state hashes.
- An unrelated keyed RNG decision cannot perturb an existing subject.
- Equal-instant work retains stable ordering across insertion/map orders.
- Stale command, duplicate command ID, overflow, invalid schedule and wrong
  thread fail without partial mutation.
- Budget exhaustion defers without reordering or loss; replay reaches the same
  final state as unbounded execution.
- Codec truncation, unknown version and corrupt reference fail closed.

### Exit gate

Pure kernel tests, deterministic replay across repeated JVM runs,
`guardrails check`, benchmark baseline and no dependency on any legacy module.

## Wave 2 — persistence, world bootstrap and exact ownership

### Deliverables

- Implement the NeoForge `FrontierStore` using a v3-only world directory,
  checksummed append-only WAL and atomic snapshots.
- Enforce `BATCHABLE` and `DURABLE_BEFORE_EFFECT`; only a flushed durable receipt
  may release a physical intent to Minecraft.
- Implement recovery, incomplete-tail truncation, snapshot verification,
  deterministic replay, quarantine diagnostics and bounded compaction.
- Add v3 lifecycle: create fresh instance, start, tick, zero-player continuation,
  save, orderly shutdown and restart. Reject v2 or unknown v3 schemas.
- Bootstrap the finite 1024×1024 profile with twelve deterministic settlement
  identities, twenty-to-forty exact residents each, one distributed hive, two
  seed nests and a deterministic terrain/spatial manifest.
- Add exact ownership records for residents, bioforms, buildings/organs,
  containers, item slots, cargo batches and player UUIDs.
- Add separate indexed coordinate systems for actor block space, sparse 4×4
  infection cells, building semantic parts and larger territory cells.
- Implement baseline-plus-sparse-delta records with compaction and explicit
  unknown/player-change provenance.

### Tests

- Golden bootstrap hashes for multiple v3 seeds and exact 12-settlement/2-seed
  cardinality; no Python fixture is loaded.
- Snapshot plus WAL recovery equals uninterrupted execution.
- Crash at every append/snapshot/compact boundary restores the last complete
  transaction and never reuses an ID.
- Corrupt checksum, gap, duplicate conflicting event, wrong world ID, v2 data
  and dangling ownership all quarantine visibly.
- Zero-player running advances; stopped server does not; sleep advances the
  exact skipped interval; `/time` does not.
- Delta and receipt retention stays bounded after long synthetic runs.

### Exit gate

Packaged restart/crash harness, full-state invariant audit, measured storage
growth and a fresh v3 world that can be inspected without materialized gameplay.

## Wave 3 — complete causal vertical slice

This wave proves the architecture end to end through generic systems, not a
scripted one-off. The fixture activates one settlement/front of the already
complete twelve-settlement world while all identities and clocks remain real.

### Canonical slice

- A settlement facility schedules production, consumes exact input/labor and
  places exact items into slot-owned warehouse storage.
- A demand creates a contract and identified cargo batch; loading moves custody
  from warehouse to carrier without duplication.
- A route and operation move named residents and cargo toward one hive front.
- The hive consumes biomass, grows one organ, spawns named bioforms and advances
  infection through sparse cells.
- Both planners produce durable objectives/tasks; exact actors participate in
  one attack/defence engagement.
- Casualty, structural damage, cargo loss, infection change and operation result
  cause subsequent production/security/hive decisions.

### Physical slice

- Materialize the settlement facility and warehouse, ordinary Villagers,
  visible route, exact carrier/container, hive organ, infection surface and
  Zombie bioforms in naturally loaded chunks.
- Acquire one atomic scene lease for the engagement. HOT actors use bounded
  role goals, ordinary collision and real combat; COLD execution is suspended
  for the leased actions.
- Execute one persisted projectile/explosion through normal Minecraft physics
  without ownership filtering. Reconcile all actually affected known subjects
  and retain every unknown block result as a physical delta/scar.
- Accept exact player kill, item deposit/withdrawal/theft, semantic building
  break and route obstruction observations in the accepting server turn.
- Leave the scene, let COLD events change forces/infection/structures, return
  and materialize only the current continuation with the same surviving IDs.

### Logistics assembly transition

The first visible segment must not be created by moving residents from a Hall
anchor. `RouteOperation` begins in `ASSEMBLING` with one bounded,
versioned `OperationAssembly`: each named participant has an adjacent-cell
approach cursor to a distinct compiled port slot; the cargo carrier is named;
and no household, settlement membership or player/world position is changed by
that record. The port exposes a two-body-clear throat, a public outer cargo
slot and a distinct second participant slot; a wider future formation requires
new compiled geometry rather than overlapping an unrelated route cell.

The only valid lifecycle is:

`cargo loaded -> durable assembly -> exact COLD/HOT approach -> all members and
cargo at port -> atomic start of OperationTravel -> bounded segment cursor ->
atomic segment hand-off -> next segment/arrival`.

Creation must retain each actor's current canonical position. Assembly COLD
movement uses the same pure clear-lane planner as Transit; HOT assembly uses
an actor lease with a distinct `OPERATION_ASSEMBLY` goal and accepts only the
next observed cursor. A naturally loaded obstruction at the throat or assigned
slot becomes a durable identity-specific deferral containing the member's next
cursor and the exact blocked compiled floor; it freezes the shared operation
until that same actor normally reaches that same cursor after ordinary passage
is restored. It neither selects a hidden alternate slot nor changes the world.
An interrupted/failed assembly releases only its own participant and
cargo claims after the corresponding durable reason.

`OperationTravel` is never cleared by a non-atomic `OperationAdvanced`.
`OperationTravelSegmentCompleted` verifies the arrived cursor and advances the
strategic route index in the same canonical transaction while retaining the
formation/cargo positions. Scene demand, carrier projection, route engagement,
death, cargo-loss, recovery and readability consult that same current
position; they cannot assume a route milestone or treat `ASSEMBLING` as an
attackable caravan.

A scene lease preserves that truth at the HOT boundary: its demand point is not
a spawn coordinate. It persists the exact position of every leased actor and
the exact cargo anchor, validates them against the canonical operation before
preparation, and uses those positions when bodies/carriers appear. A legacy
uniform lease or carrier offset is rejected rather than quietly moving a convoy.

Required evidence is a pure lifecycle/recovery test plus a causal pilot:
ordinary player reaches a naturally loaded port, sees the two named villagers
approach and depart, obstructs the owned public sill or throat, observes the
durable assembly deferral, restores ordinary passage, then verifies the same
IDs and cursor after graceful restart. Foundry reports the port at `COMPILED`,
then the pilot supplies `MATERIALIZED`/`RELOADED` evidence without force-load.

### Tests

- Domain trajectory plus rejected insufficient-input/blocked-storage paths.
- No duplicate item/actor/effect across every HOT/COLD transition point.
- Obstruction and mixed-container conflicts preserve foreign state.
- Explosion miss, unrelated target damage and unmodelled terrain damage all
  persist and affect later queries/pathing.
- Crash before effect, during prepared lease and after Minecraft result but
  before observation submission never blindly replays or loses a confirmed
  effect.
- GameTests cover physical state, reverse causality and unload/reload; packaged
  two-start harness proves the same world and IDs.
- One real-display manual pass shows moving residents/bioforms, transport,
  combat, infection, destruction, immediate feedback and seamless return.

### Exit gate

All level-2 automated evidence and one level-3 real-world causal path pass. V2
remains present because breadth, balance and product comprehension are not done.

## Wave 4 — complete human frontier

### Deliverables

- Residents, households, age/health/skills/roles, birth/death and migration.
- Facilities, resource sites, recipes, work allocation, maintenance,
  construction, repair, storage capacity and shortages.
- Companies/institutions, accounts, ownership, wages, prices, market clearing,
  licenses, contracts, credit, debt, investment and insolvency.
- Settlement governance, reserves, rationing, quarantine, doctrine, knowledge,
  diplomacy, territorial claims and threat assessment.
- Trade route selection, exact cargo lifecycle, patrol, escort, reconnaissance,
  clearance, reclamation, evacuation, construction, field-post and resupply
  operations.
- Field posts, modules, hospitals, fortifications, supply lines, engagements and
  aftermath/recovery.
- Utility/HTN/local-goal AI for all human roles with bounded reconsideration and
  only perceived knowledge.

### Verification matrix

For each domain, require a normal path, resource/precondition failure, stale or
duplicate input, subject removal during a plan, long-run retention and
snapshot/WAL recovery. Cross-domain scenarios must include prosperity, ordinary
shortage, trade interruption, company failure, epidemic/quarantine, migration,
evacuation, lost expedition and settlement destruction/recovery.

### Exit gate

All twelve settlements evolve autonomously for one simulated year across at
least four seeds without invalid ownership, hidden resources, stalled schedules
or deterministic divergence. Balance may differ by seed; systemic activity may
not depend on a player or Narrator.

## Wave 5 — complete distributed hive and war

### Deliverables

- Shared hive biomass/energy/nutrient economy and transfer through organ graph.
- Distinct infection substrate and destructible hiveroot graph with independent
  vascular/synaptic capacity, finite extraction, valves, severance and
  resource-backed regrowth.
- Replace provisional `HEART` with distributed Hivemind plus local `GANGLION`;
  add `RELAY`, `DIGESTER`, `HIBERNACULUM`, `MORPHER`, `SPORULATOR` and `SENSOR`
  through explicit stable wire tags and versioned recovery.
- Brood and exact bioform lifecycle split into chassis, visible mutation,
  assignment, cocoon/physical custody, controller, wounds, structural/reserve
  biomass, consumption, carcass and recycling.
- Purposeful territorial Sentinels and exact observation delivery; stationary
  relay coverage inside the hive and a mandatory mobile Overseer for every
  coordinated expedition outside that coverage.
- Task/threat-driven cocoon waking, assembly, return and recovery; chunk load
  never wakes a body or creates ambient wandering.
- Synaptic signal memory and explicit degraded instincts after Relay/Overseer
  loss, including later exact survivor reclamation by another controller.
- The accepted alien mechanics and staging in
  `frontier-v3-hive-physiology.md`, including reflex tissue, mucus lines,
  biomass sacrifice, carcass recovery, evidence-backed adaptation, circulation
  rerouting, premature waking, grafting, unstable payloads and live digestion.
- Infection metabolism, growth/retreat, terrain affinity, settlement/building
  contamination, quarantine interaction and decontamination.
- Perception, territorial beliefs, adaptations, doctrine and utility/HTN plans.
- Scouting, harvesting, supply, assault, siege, bomber, defence, retreat and
  expansion operations with exact forces and cargo.
- Territory control, contested fronts, engagement lifecycle and consequences
  for both economies.

### Verification matrix

Require resource starvation, independently severed vascular/synaptic paths,
blocked infection, destroyed Ganglion/Relay/cocoon, pre-report Sentinel death,
remote assault rejection without an Overseer, Overseer-loss degradation,
failed assault, missed or prematurely detonated payload, carcass denial,
adaptation invalidation, simultaneous fronts, full settlement loss and hive
retreat/recovery. Prove that two seed nests remain one polity and cannot
duplicate economy, biomass, cocoons or actors.

### Exit gate

Four-seed annual runs show a long contest with plausible divergent outcomes,
the accepted one-to-two-day attack grace, no guaranteed winner, no impossible
resource creation and stable deterministic replay.

## Wave 6 — production materialization

### Deliverables

- General desired-state compiler for every functional building, organ, route,
  field object, actor, cargo and infection state.
- Semantic projection for infection surface, raised hiveroot topology, its two
  flow channels, junctions, severed/starved states, cocoons and attached organs;
  baseline plus bounded deltas retain unrestricted physical aftermath.
- Stable semantic block maps and object-local bright boards; one board per
  object, with geometry readable before text.
- Role-aware Villager and bioform local brains, live work cycles, patrols,
  transport, construction, repair, retreat, defence and combat.
- Scene leases for overlapping operations and deterministic conflict policy:
  one actor/cargo/object action belongs to at most one active physical scene.
- Real projectile, TNT-like blast, fire, collapse, construction,
  decontamination and infection-terrain executors with durable effect leases.
- Incremental baseline/delta materialization that never force-loads or restores
  over an unknown/player change.
- Readable distant hive silhouettes, continuous routes and infection contours;
  the player can visually separate alien biome, vascular/synaptic network,
  organ function, dormant population and current mobilization. Later art can
  replace graybox assets without changing canonical contracts.
- Player UUID reputation, contracts, custody, decisions and history through
  ordinary typed commands and observations.

### Verification

- Focused GameTests for each materialized semantic and failure/recovery path.
- Randomized property tests over lease/body/container uniqueness.
- Repeated leave/return/restart tests during work, transit, battle, explosion,
  infection growth and building collapse.
- JFR profiles separately measure simulation, projection, chunk admission,
  entities, networking and rendering; changes preserve canonical hashes.
- Real-display audits use one full-screen client on `DISPLAY=:0`, visible to the
  user, and capture player-height evidence without operator-only explanation.

### Exit gate

Every agreed canonical process has a distinguishable physical manifestation or
an explicit documented reason it is not spatial. The user accepts continuous
motion, combat, damage, infection and causal feedback as one Minecraft world.

## Wave 7 — balance, product validation and cutover

### Automated gates

- Repeated multiseed deterministic hashes and one-year full-world runs.
- Sixty-minute full-scale 20-TPS performance profile meeting the contract
  budgets with bounded WAL, heap, schedules, receipts and physical deltas.
- Packaged fresh-world bootstrap, two-start recovery, crash injection and
  physical-intent recovery.
- Full critical Gradle gate, packaged JAR verification and outer-pack isolated
  installation smoke only after the nested artifact passes.

### Player gates

- Natural discovery without teleport, opaque IDs or developer briefing.
- The user observes a settlement economy, transport, hive growth, infection,
  operation, battle, physical destruction and later aftermath.
- Five clean-room sessions: at least four players explain cause, threatened
  value, available responses, result and delayed consequence; at least four
  independently discover two response paths.
- One 2–4 player session proves shared world facts, independent player history,
  no duplicated outcome and understandable cooperation/refusal.
- Positive recovery is at least as legible as degradation.

### Cutover sequence

1. Freeze v3 code and record all technical and product evidence.
2. Make v3 the only default/new-world Frontier runtime and deploy a verified
   artifact through the outer repository workflow.
3. Prove a clean production-style fresh world, save/restart and manual entry.
4. Remove `io.farfrontier.palemirror.frontier.reference`, all `SourceGraybox*`
   runtime paths, v2-only commands/configuration and executable parity fixtures.
5. Archive useful historical v2 documents; keep unrelated Pale Mirror systems.
6. Re-run critical build, GameTests, restart, packaging and manual entry with no
   v2 code present.
7. Change `architecture.yml` from transition state to v3-only and close the
   durable goal only after the final evidence is recorded in `CONTINUITY.md`.

Rollback before step 4 is deployment rollback to the last verified artifact,
not runtime fallback inside v3. After step 4, Git history is the only v2
recovery path.

## Progress reporting

`CONTINUITY.md` records only:

- the durable v3 goal and invariants relevant to current work;
- completed waves with commit/evidence identifiers;
- one current wave, its verified facts and next smallest coherent slice;
- unresolved decisions that genuinely block the current wave;
- the active files/tests/runtime evidence.

Long logs, prior-wave checklists, balance tables and screenshots live under
`docs/archive/`, test reports or ignored build evidence. A green automated gate
is reported as level 2, a real-world audit as level 3, and unbriefed player
understanding as level 4; none substitutes for the next.
