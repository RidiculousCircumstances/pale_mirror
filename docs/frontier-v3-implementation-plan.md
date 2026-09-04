# Pale Mirror Frontier v3 implementation plan

Status: approved execution plan for the target contract in
[`frontier-v3-contract.md`](frontier-v3-contract.md).

The active precondition for further materialization breadth is the normative
[`frontier-v3-seamless-foundation.md`](frontier-v3-seamless-foundation.md)
correction programme. Its F0 slices supersede the previous assumption that a
valid endpoint executor or one monolithic scene is sufficient foundation.

The designated GPT-5.6 Terra continuation agent must follow the executable
[`frontier-v3-terra-implementation-brief.md`](frontier-v3-terra-implementation-brief.md).
It expands the F0 contract into ownership, recovery, scenario, testing and
handoff rules; it does not authorize a deviation from this plan or
`architecture.yml`.

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
- A scene is only a persisted physical-execution lease over an already-owned
  canonical process. Player/chunk demand may select HOT execution but may not
  create, start or own the process. Every duration-bearing process must register
  paired COLD and HOT drivers over one authoritative progress model; atomic and
  distributed process classes use their appropriate intent/frontier boundary
  instead of a fictitious scene.
- Scene kinds use one persisted lifecycle and a closed NeoForge behavior
  registry. Add a new `SceneCause` with one process-specific behavior and its
  normal, negative/recovery tests; do not spread `if cause` branches through
  generic materialization, persistence or diagnostics. Generic lifecycle code
  owns exclusivity and atomic acquire/checkpoint/release, never domain progress.
- Each commit must leave `architecture.yml`, implementation and
  `CONTINUITY.md` consistent. No wave is complete from code existence alone.
- Run only the focused tests for the current behavior plus the risk-level gate
  required by `AGENTS.md`; manual and product evidence is recorded separately.
- Preserve both Git histories. Frontier source/docs belong only to the nested
  `pale-mirror/` repository; packaging/deployment changes belong to the outer
  repository and land only after a verified v3 artifact exists.

## Mandatory seamless-foundation correction gate

Before resuming `MAT-004` or adding any other process, scene, inventory or
effect family, execute F0 from
[`frontier-v3-seamless-foundation.md`](frontier-v3-seamless-foundation.md):

1. **F0.0 — inventory and recurrence guards.** Classify every current duration
   process, physical surface, effect, movement owner and conflict producer;
   install mechanical checks for paired execution ownership and forbidden
   historical-materialization authority.
2. **F0.1 — one process, two drivers.** Close V3-AUD-043 through MAT-001 with
   never-loaded, arrive-mid-process, unload/return, intervention and crash
   evidence over one process cursor.
3. **F0.2 — replica custody and deferred aftermath.** Separate persistent
   physical replicas from temporary authority, remove permanent `ACTIVE`
   economic branching and make COLD consequences produce bounded idempotent
   physical aftermath rather than wait for a player.
4. **F0.3 — fungible exact resources.** Replace permanent Vanilla-stack
   identity with exact lots, allocations, custody accounts and transient
   physical bindings that support ordinary split/merge/partial transfer.
5. **F0.4 — semantic navigation and operation fronts.** Give HOT a bounded
   local navigation envelope under canonical checkpoints and partition large
   exact operations into disjoint local fronts instead of one unbounded scene.
6. **F0.5 — fenced recovery and failure ownership.** Add authority epochs,
   stale-projection rejection and distinct domain-disruption, reconciliation
   and canonical-corruption outcomes with bounded recovery/abandonment.
7. **F0.6 — observer-neutrality, first visibility and scale.** Prove calibrated
   HOT/COLD semantic equivalence, pre-visible graybox catch-up and concurrent
   twelve-settlement/hive-front performance.

F0 is an ordered correction, not a parallel feature list. An implementation
agent works on only the first incomplete slice recorded in `CONTINUITY.md`,
commits each coherent fresh-world format cut independently and updates the
audit/architecture/ledger in the same commit. Existing M0–M2 evidence remains
valid only for the boundary it actually proves; tests encoding a rejected FND
assumption must be rewritten rather than preserved as compatibility behavior.

The detailed brief owns the data responsibilities, negative cases, stop
conditions and exit evidence. This plan owns sequencing: no later MAT or Wave 6
breadth may bypass F0.

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

## Terrain-aware movement foundation gate

The current flat graybox has exposed an architectural mismatch: historical
movement values mix support blocks and body positions, corridors assume one Y
level, and facility approaches can encode a compass-specific offset. Before a
new movement-bearing scene or transport family is added, close
`V3-AUD-019` in this order:

1. **T0.1 — typed spatial values.** Introduce distinct immutable surface anchor,
   body position, facility-port station and transport-node values. Persist one
   explicit current-world format; an older payload is rejected and its world is
   recreated, never inferred from surrounding blocks.
2. **T0.2 — semantic facility ports.** Compile orientation, exterior approach,
   threshold/throat clearance, interior connector and bounded work/formation
   stations from the owning building plan. Remove structure-centre and
   fixed-west/fixed-offset entrance assumptions from reusable movement code.
3. **T0.3 — bounded 3D capability topology.** Retain stable nodes/edges with
   grade, clearance, traversal kind, mobility requirements, provenance,
   revision and explicit availability. Keep pedestrian/bioform and rail views
   distinct; use a compact strategic graph plus bounded local corridors.
4. **T0.4 — one HOT/COLD cursor.** COLD advances only retained topology edges.
   A registered HOT movement provider executes the same next-node goal through
   Minecraft collision/navigation and submits only observed arrival,
   obstruction or damage. Replanning is canonical and bounded; the adapter may
   not sidestep or select a hidden entrance.
5. **T0.5 — physical consequence and recovery proof.** Add non-flat fixtures
   for a stepped/ramped approach, a facility on another datum and a bridge or
   rail-grade edge. Add blocked/destroyed entrance, changed edge and restart
   cases. A disposable player scenario must show the same actor/route cursor
   before and after HOT/COLD/restart without force-loading.

T0.3 now owns a bounded immutable terrain-provider contract: supply routes, active
`OperationTravel` cursors and each active `OperationAssembly` member retain their own persisted topology,
while the bootstrap retains a baseline plus sparse surveyed support columns. Current snapshot schema 98
and persistence-envelope v7 retain typed `BodyPosition` formation cells, `TransportAnchor`, assembly
`SurfaceAnchor` routes and the exact terrain survey. Raised surfaces compile provider-owned footing/deck
cells from that survey; player scaffolding is not part of a valid route and foundation loss blocks the
same retained edge. The present provider deliberately does not excavate or synthesize a facility terrace:
support at or above a declared deck fails closed until an explicit earthworks/bridge provider exists.
Any other snapshot or WAL envelope is rejected fail-closed; v3 worlds are
disposable and recreated for a new format. This is deliberately not T0.4
completion: observed edge damage and its physical restart proof remain separate
owners and gates. Assembly cursors are already typed topology owners.

The partial implementation still has one non-negotiable semantic boundary:
`TraversalTopology` nodes are typed support surfaces while `OperationTravel`
and scene leases retain role-specific body/transport values. Current
actor/assembly/work/medical candidates may deliberately own support cells and
cross only through a named support-to-body conversion. No executor may treat one
value as both a support column and a body/cargo position. Before a non-flat
transit cursor is accepted, move that remaining candidate to role-specific
persisted types and make the physical adapter prove the exact support-to-feet
conversion once at scene admission. Assembly now retains typed pedestrian topology rather
than a raw support list, and its registered HOT provider resolves the explicit feet cell.

The first implementation may keep the graybox physically flat by providing a
uniform-datum topology. It is not required to build the production terrain
surveyor, settlement earthworks or Create provider in this gate. Completion
means domain and scene code can no longer tell that the provider is flat, and
Foundry can audit compiled port connectivity, two-body clearance, supported
surface and permitted grade at `COMPILED`, `SETTLED` and `RELOADED` phases.

## Route-maintenance closure gate

Before another route or transport scene, close `V3-AUD-022` according to
[`frontier-v3-route-maintenance-contract.md`](frontier-v3-route-maintenance-contract.md).
The operation is a distinct exact owner: it carries one observed route-loss
cell through source pickup, retained cargo, the existing bounded engineering
worksite protocol and an observed physical repair receipt. It may reopen only
the exact affected retained traversal edges after the final competing loss has
gone. It may not force-load, substitute generic structural repair, turn a
player road into topology, or perform a bypass cutover. Exit evidence is the
normal/conflict/restart matrix plus the native player break -> repair ->
same-edge recovery scenario defined by that contract.

The route-maintenance owner is deliberately bounded but not globally serial:
each repair cell may retain one exact operation, while distinct cells may make
fair bounded progress when their people, cargo and physical endpoints differ.
An unavailable COLD source is a per-intent deferral; it is never authority to
stall a loaded independent repair. This is required before route-maintenance
closure can claim a living multi-settlement world.

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

- Implement `frontier-v3-human-capabilities.md`: residents, households,
  age/condition, trainable skills, profession/employment, exclusive current
  assignment, exact equipment and work/tactical organization remain separate
  canonical components.
- Replace the provisional six-value `ResidentRole` bootstrap model through a
  new explicitly tagged schema in a fresh world. Do not add a snapshot/WAL
  migration or compatibility decoder; bootstrap exact people, households,
  health, nutrition, contracts and custody directly under the new model, and
  do not infer a current task or military membership from the old role.
- Birth/death and exact-person migration, including assignment, equipment and
  organization conflicts.
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
- Utility/HTN/local-goal AI for all human assignments and derived tactical
  functions with bounded reconsideration and only perceived knowledge.
- Exact crew/unit assembly, leadership degradation, mobilization and return.
  Mobilized civilians stop contributing their conflicting work capacity, while
  already committed physical effects settle or interrupt through their typed
  recovery path.
- Foundation completed: a `SettlementAssault` owns one exact defender unit with
  deterministic stable member ordering and leader identity. Its active members
  derive `SETTLEMENT_DEFENCE`, admission excludes every existing assignment,
  except the implemented exact COLD production interruption: it returns the
  held input and releases the matched market/invoice commitment before the same
  resident becomes a defender. Restart retains the same people. It deliberately
  does not yet interrupt
  committed civilian work or model every future equipment/supply role or leader
  replacement.
- The first pure tactical read model now makes the retained unit legible without
  adding a second roster: exact assignment, leader identity, profession and
  actor-held equipment derive only `CIVILIAN`, `MILITIA`, `ARMED_DEFENDER`,
  `GUARD` or `SQUAD_LEADER`. It owns no equipment mutation.
- The first owning human-mobilization slice is complete for exact outbound
  issue and inverse return. Scheduled processes bind assault/member/source or
  named-target slot/item/body identity; loaded executors durably enter
  `RUNNING`, move the tagged stack only between the naturally loaded active
  chest and matching owned Villager hand, then confirm only their exact inverse
  postconditions. Unloaded endpoints defer; forged, changed or mixed evidence,
  including recovery, remains visible conflict/`UNKNOWN_AFTER_RESTART` rather
  than a direct inventory rewrite or replacement. Native restart scenarios
  prove both custody directions, and the return scenario has the player open
  the exact recovered depot slot. Observed loss/drop/destruction and exact
  first defender readiness are now complete; role expansion is next.
- First readiness is a pure exact projection rather than a second retained unit
  ledger: current assignment, actor vitality and exact actor-held weapon custody
  compile `UNAVAILABLE`, `IMPROVISED`, `DEGRADED` or `READY`. COLD settlement
  combat consumes that same fact, so a lost weapon reduces the same person's
  output and a lost leader degrades surviving members without a replacement.
  The next role owner correction is `V3-AUD-017`: `RoutePatrol` and
  `RouteOperation` embed a shared immutable exact-unit manifest instead of a
  global roster. New patrols are one leader plus one-to-three scouts; a named
  cargo crew is distinct from its two-to-four exact escorts. Schema-82 routes
  may retain only an explicit understrength legacy formation until their
  ordinary terminal outcome, never a hydrated or invented replacement. The
  subsequent expansion must add real engineering/recovery and medical/
  evacuation operation/equipment owners before it introduces sapper or medic
  labels.
- The first engineering/recovery owner is now route reconstruction: each new
  `RouteConstruction` embeds one immutable one-to-four-person local
  `EngineeringRecoveryTeam`, and those same exact people derive exclusive
  `ENGINEERING_RECOVERY` assignment across snapshot/WAL recovery. Schema-83
  autonomous construction is preserved as explicit legacy work, never hydrated
  with invented people. It now issues/returns each exact retained member's
  real tagged iron pickaxe through the common durable equipment receipt and
  naturally loaded executor; it deliberately does not yet let that fact invoke
  the former autonomous route placer. Schema 85 additionally retains one
  bounded immutable COLD approach corridor/cursor per same crew member to a
  distinct declared work-site column, admitting no bypass for which that
  approach cannot compile. A bounded joint compiler selects only a retained
  lane combination whose deterministic one-person COLD schedule can bring the
  full exact crew to its distinct columns without overlap or a head-on swap;
  lanes may cross only through that serialized schedule. Schema 86 makes each accepted replacement-cell
  plan immutable and durable, so neither a COLD tick nor recovery recompiles
  world route geometry. Engineering uses a small deterministic catalogue of
  bounded rectilinear public lanes rather than an unbounded general pathfinder;
  no viable lane visibly blocks task admission. It advances one non-HOT living
  person by one exact cell per turn and is not itself block placement. Schema 87
  adds the exact naturally loaded HOT crew/work-site lease. Its typed cause
  retains `projectId + workCellIndex`, demands the same completed COLD crew and
  permits exactly one durable physical work intent; the separate physical
  adapter alone observes/places the cell. Before the lease can materialize its
  bodies, the same canonical project compiles one temporary, provenance-owned
  worksite floor for every retained worker. It is not a route cell or a hidden
  spawn platform: ordinary loss conflicts the exact project, and an untouched
  floor retires only after the work front moves. A confirmed cell clears the
  old approach so the same people must take a new COLD approach to the next
  immutable cell.

### Verification matrix

For each domain, require a normal path, resource/precondition failure, stale or
duplicate input, subject removal during a plan, long-run retention and
snapshot/WAL recovery. Cross-domain scenarios must include prosperity, ordinary
shortage, trade interruption, company failure, epidemic/quarantine, migration,
evacuation, lost expedition and settlement destruction/recovery.

Human capability coverage must additionally prove job/assignment change without
identity replacement, double-assignment rejection, equipment-loss capability
loss, civilian opportunity cost during mobilization, leader-loss degradation,
death during committed work and the same exact unit across HOT/COLD restart.

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
- Complete the distributed Hivemind and local `GANGLION` implementation: the
  explicit stable organ vocabulary and fresh-world schema cut are in place;
  add the remaining functional ownership for `RELAY`, `DIGESTER`,
  `HIBERNACULUM`, `MORPHER`, `SPORULATOR` and `SENSOR` without fabricating
  inactive bootstrap organs.
- Brood and exact bioform lifecycle split into chassis, visible mutation,
  assignment, cocoon/physical custody, controller, wounds, structural/reserve
  biomass, consumption, carcass and recycling.
- Purposeful territorial Sentinels and exact observation delivery; stationary
  relay coverage inside the hive and a mandatory mobile Overseer for every
  coordinated expedition outside that coverage. Every route interception,
  assault and future expedition must persist exactly one of those authorities;
  direct nearest-body selection is prohibited. The operation retains the
  selected controller/coverage fact, weighted roster and signal state through
  HOT/COLD/restart, then degrades through bounded signal memory, instinct and
  later reclaim rather than silently replanning after controller loss
  (`V3-AUD-036`).
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

## Materialization completeness gate

### Process/execution continuity correction

Before adding another duration-bearing scene family, make the universal
process/execution boundary mechanically true:

This subsection is the original narrow V3-AUD-043 gate. It is now executed as
F0.1 and must be followed by F0.2–F0.6; closing MAT-001 alone does not reopen
materialization breadth.

1. inventory each duration-bearing process's canonical aggregate, COLD driver,
   HOT driver, progress/cursor, lease and terminal effect adapter;
2. reject any flow in which player demand or chunk load creates/starts domain
   work, or in which absence of demand pauses an otherwise viable process;
3. make HOT acquire, each observed checkpoint and HOT release atomic against the
   same process version and exact actor/item/topology identities;
4. keep `PhysicalIntent` for genuinely atomic effects, retained spatial
   frontiers for distributed environmental work and custody-only leases for
   ambient bodies instead of forcing those classes through a work scene;
5. repair `MAT-001` as the reference implementation: begin and advance harvest
   in COLD, attach HOT at its current retained cursor, release to continued COLD
   work, then return to the later current state without replay or disappearance;
6. prove never-loaded, arrive-mid-process, leave/return, player interruption and
   graceful/abrupt restart paths before applying the boundary to production,
   service work, patrols, expeditions and later rail transport.

This gate changes ownership, not game balance: HOT remains real-time Minecraft
execution and COLD remains bounded event-driven semantic progress. Completion
requires a source guard or composition test that prevents a new duration
process from registering only one driver.

Before Wave 6 can claim a process complete, classify and prove it under
`docs/frontier-v3-materialization-completeness.md`. In particular, an exact
physical receipt or final block/container state is only M1 evidence; it does not
close a duration-bearing process without the same worker/actuator, progress,
intervention and HOT/COLD continuity at M2.

Close the confirmed gaps in dependency order:

1. establish one reusable class-C work-scene foundation from the existing
   engineering and medical verticals: exact assignment, facility ports/stations,
   approach, progress, effect hand-off, drain and recovery;
2. make farm preparation/harvest the first end-to-end work reference, then use
   the same boundary for workshop production, structural repair and
   decontamination rather than adding executor-specific shortcuts. The
   accepted `MAT-003` service-work ownership, spatial/recovery and evidence
   contract is [`frontier-v3-service-work-contract.md`](frontier-v3-service-work-contract.md);
3. replace waypoint/body-rewrite human patrols with the fresh-world exact
   ingress, formation, retained topology/cursor and `ROUTE_PATROL` HOT scene
   specified by [`frontier-v3-route-patrol-contract.md`](frontier-v3-route-patrol-contract.md),
   then bind hive expeditions to the same operation-continuity standard;
   generic ambient movement cannot execute an assignment;
4. add staged exact-recipient provisioning and household/birth lifecycle work;
5. materialize hive nutrient flow, digestion, organism/organ growth and their
   player-interruptible hiveroot/cocoon dependencies;
6. convert crop/infection/growth/retreat projection to bounded retained spatial
   progress, preserving atomic execution only for truly instantaneous effects;
7. make exact hunger/exposure/infection/recovery readable on HOT residents and
   reconcile real contact with the same canonical condition model;
8. complete the decision-to-visible-consequence matrix for markets, shortages,
   quarantine and doctrine, then run the unbriefed M3 comprehension gate.

After F0 completes, `MAT-001` through `MAT-007` are P0 Wave-6 blockers. `MAT-008` through
`MAT-010` block Wave-7 product acceptance. An earlier audit closure for conservation,
receipt or recovery remains valid at its evidence level but cannot be cited as
closure of the corresponding `MAT-*` item.

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
- Provider-neutral facility ports and rail contracts retain semantic
  input/output, energy, maintenance, station, segment/junction, train, wagon,
  exact cargo and route-cursor ownership. Graybox block/entity kinds never
  become domain capability, preserving the accepted future Create boundary in
  `frontier-v3-create-direction.md`.
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

- Maintain a machine-reviewed inventory mapping every canonical process to its
  materialization class, current M0–M3 evidence and open `MAT-*` debt. A process
  with no spatial manifestation must carry an explicit accepted class-G reason
  and a visible downstream consequence.
- Focused GameTests for each materialized semantic and failure/recovery path.
- Randomized property tests over lease/body/container uniqueness.
- Repeated leave/return/restart tests during work, transit, battle, explosion,
  infection growth and building collapse.
- JFR profiles separately measure simulation, projection, chunk admission,
  entities, networking and rendering; changes preserve canonical hashes.
- Real-display audits use one full-screen client on `DISPLAY=:0`, visible to the
  user, and capture player-height evidence without operator-only explanation.

### Exit gate

Every agreed canonical process reaches its required M2 level or has an explicit
accepted class-G reason it is not spatial plus a distinguishable downstream
consequence. No P0 `MAT-*` item remains open. The user accepts continuous
motion, work, transport, combat, damage, infection and causal feedback as one
Minecraft world.

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

## Post-graybox Create phase — accepted direction, outside this goal

After Wave 7 validates and cuts over the complete graybox simulation, a
separate goal may implement `frontier-v3-create-direction.md`. Its transport
scope is rail only: long inter-settlement lines, exact freight trains,
stations/yards, switches/signals, bridges/tunnels, loading and repair. Road
freight and aeronautical transport are explicitly excluded from this phase.

The accepted industrial scope combines the already stated factories, farms,
weapons and turrets with extraction, energy, metallurgy/materials,
warehousing/rail logistics and construction/engineering. Create remains a
registered NeoForge physical provider. It never owns canonical recipes,
inventory, cargo, route selection, settlement decisions, damage or COLD
progress.

Implementation begins only from a verified graybox provider contract and must
add same-identity train, exact cargo, mechanism capability, obstruction,
damage, player theft and restart evidence before replacing any graybox path in
the product profile. Provider replacement is incremental; there is no
Create-backed second economy or decorative capability shortcut.

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
