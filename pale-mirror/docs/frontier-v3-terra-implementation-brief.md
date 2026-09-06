# Frontier v3 implementation brief for the continuation agent

Status: normative execution brief for the GPT-5.6 Terra continuation agent.

Delivery roles follow [`engineering-agent-protocol.md`](engineering-agent-protocol.md).
Terra runs with reasoning `high` as the sole code executor, not a second
architect or self-accepting project owner. The supervising engineer owns all
normative docs, work orders, the ledger and acceptance. Read the active order
under `docs/work-orders/`; no implementation or expensive run begins without
its explicit Gate A or Gate B grant. Propose requirement changes to the engineer.

This document turns the accepted Frontier v3 direction into an executable
working contract. It does not replace the product contract, the architecture
map, the audit register or the Continuity Ledger. When those sources differ,
resolve the discrepancy explicitly in the same change; do not silently choose
the code currently easiest to extend.

## 1. Mission and non-negotiable product promise

Implement a small but autonomous Frontier v3 world that feels like one living
Minecraft world. The player may enter, leave, return, die, break things, steal
items or cause an explosion. Those actions must change the same world that was
already running; they must not reveal a materialization switch.

`HOT` and `COLD` execute one process with shared rules/history but different
physical detail. The mandatory `frontier-v3-execution-semantics.md` defines
exact versus calibrated comparisons, knowledge limits, independent physical
activity, cross-scene effects and recoverable confirmation:

- `COLD` is canonical, deterministic, event-driven progression without a
  loaded physical scene;
- `HOT` is temporary bounded Minecraft execution of that exact process when
  natural physical availability makes execution possible; presentation demand
  and observer-independent physical custody are separate inputs;
- entering or observing a chunk never creates work, changes its rules, or
  becomes necessary for its future liveness;
- leaving a chunk never deletes the process, invents a restart, rolls work
  back, or makes an old physical projection authoritative.

The target is not a collection of scripted vignettes. A settlement farm, hive
operation, rail shipment or battle is an instance of a canonical process with
identity, resources, participants, topology, progress and an outcome. A scene
is only a short-lived physical executor for a bounded part of that instance.

## 2. Authority and reading order

At the beginning of every continuation turn, before selecting a change:

1. read `AGENTS.md`, `CONTINUITY.md`, `engineering-agent-protocol.md` and the
   active work order (documentation paths are relative to this brief);
2. read the relevant portions of `architecture.yml` and the complete
   `docs/pale-mirror-foundry.md` for materialization work;
3. read this brief and
   [`frontier-v3-execution-semantics.md`](frontier-v3-execution-semantics.md);
   when F0.V is active also read
   [`frontier-v3-integration-feedback-foundation.md`](frontier-v3-integration-feedback-foundation.md),
   followed by
   [`frontier-v3-seamless-foundation.md`](frontier-v3-seamless-foundation.md),
   [`frontier-v3-implementation-plan.md`](frontier-v3-implementation-plan.md)
   and [`frontier-v3-architecture-audit.md`](frontier-v3-architecture-audit.md);
4. inspect the current uncommitted state before editing. Preserve useful WIP;
   do not discard it merely because a new slice starts.

`CONTINUITY.md` names the first incomplete F0 slice and is the operational
source of truth. This brief governs how that slice is approached. The
following sources own their respective facts:

| Source | Owns |
| --- | --- |
| `architecture.yml` | component boundaries, invariants and critical flows |
| `frontier-v3-contract.md` | product-level world and causality promises |
| `frontier-v3-seamless-foundation.md` | FND-01…FND-09 and F0 exit gates |
| `frontier-v3-integration-feedback-foundation.md` | current F0.V SDK/scenario scope and exit gate |
| `frontier-v3-accelerated-verification-loop.md` | mandatory current F0.VA feedback-loop interruption and exit gate |
| `frontier-v3-architecture-audit.md` | defect facts, remediation and closure evidence |
| `frontier-v3-materialization-completeness.md` | M0–M3 classification; never overclaim a lower evidence level |
| `CONTINUITY.md` | current slice, verified evidence, open questions and working set |
| source/tests | implementation detail and executable behavior only |

Do not use the old Python application as a runtime, fixture oracle, migration
input or compatibility layer. Frontier v3 is a fresh-world Java system. Python
is historical design reference only where an explicitly adopted rule still
applies.

## 3. Fundamental architecture laws

These laws are mandatory. A local green test is not a reason to violate one.

### 3.1 Canonical process ownership

For every duration-bearing process, exactly one pure canonical aggregate owns:

- purpose and legal transition vocabulary;
- exact participant, item, structure and target identities;
- the selected semantic topology/ports, retained cursor and work progress;
- schedules, causal correlation, result and abandonment policy;
- all economic and combat consequences.

A NeoForge executor never mutates that aggregate. It submits a typed
observation that is validated against the process revision and authority epoch.
Minecraft blocks, entity NBT, a scene record, a client camera and a diagnostic
board are representations or evidence, never a second source of truth.

### 3.2 Scene and lease ownership

`SceneLease` owns only temporary exclusive *physical* authority: which bounded
front may materialize which exact body/cargo/effect, with what epoch, in which
loaded local area. It must not own a second cursor, a second target, a second
inventory balance or the strategic process clock.

The generic scene lifecycle owns acquire, checkpoint, drain, release and stale
fencing. A closed registered behavior owns its process-specific admission,
physical effect, observations and continuation. Do not solve a new behavior by
adding `if (cause == ...)` branches to generic materialization, persistence or
diagnostics.

Atomic interaction, distributed environmental work, an ambient presence and a
long operation are distinct classes. Use respectively a physical intent,
retained spatial frontier, custody-only lease, or operation/front hierarchy;
do not force all of them into a work scene.

F0.V makes this a closed internal SDK contract. Every physical-capable owner
declares exactly one stable archetype: `DURATION_WORK`,
`COORDINATED_TRAVERSAL`, `COMPOSITE_OPERATION`, `ATOMIC_INTENT`,
`SPATIAL_FRONTIER` or `AMBIENT_CUSTODY`. Its versioned descriptor owns the
production vocabulary/provider bindings and hard actor, cargo/effect,
local-space, observation, retention, cadence and work bounds. Reject a wrong,
multiple, missing, incompatible or unbounded registration before execution.
Generic SDK code may dispatch a registered descriptor but may not branch on a
concrete process family.

Player count is not process input. Aggregate natural demand to at most one
lease per exact process/front; another observer cannot duplicate or accelerate
it, and one departing observer cannot drain it while another remains. A
composite operation remains the sole parent of its purpose, exact roster,
resources and result; children receive disjoint bounded allocations and
deterministic create/join/cancel/partial-result/return transitions. Do not
complete the parent until every child and allocation is terminally accounted.

### 3.3 Replica custody is not process authority

A serialized chest, entity or building replica may be absent, stale or altered
while its natural chunk is unloaded. It can never decide whether a settlement
may eat, work, grow, trade, repair or attack.

F0.2 replaces historical materialization state with separate:

- persistent expected replica identity/revision/fingerprint;
- short-lived exclusive custody lease and monotonic epoch;
- loaded-world observation and smallest-owner reconciliation result.

An unchanged replica may catch up after natural load. A changed one yields typed
evidence before any write. Never silently restore baseline geometry or overwrite
a player/world change.

### 3.4 COLD semantic consequence and HOT physical aftermath

If the canon knows at simulation time `T` that an operation kills someone,
breaks a road, destroys an organ or infects a patch, that semantic fact commits
at `T` even when nobody is nearby. A loaded HOT effect executes real Minecraft
physics after durable-before-effect receipt and is observed exactly. A COLD
effect records bounded chunk-indexed, idempotent deferred aftermath. Natural
later loading presents the *current* aftermath without replaying the explosion
or hiding the damage.

There are no protected player, settlement, hive or operation zones. A real
PM-authorized blast may hurt any physically reachable block/entity. Its
unbounded visual physics does not justify an unbounded canonical ledger: keep
an exact named semantic subject set plus a bounded scar/obstruction record for
other observed space.

### 3.5 Exact economics with fungible physical items

One ingot is one canonical unit. It is not permanently one Minecraft
`ItemStack` UUID. F0.3 replaces immortal stack identity with:

- bounded resource lots (kind, quantity, provenance);
- exact claim allocations for jobs/contracts/equipment;
- custody accounts for containers, actors, cargo and world carriers;
- transient physical bindings that observe ordinary split, merge, partial move,
  hopper, pickup, drop and consumption.

Cargo batches, contracts, equipment and reserved allocations retain stable
identity. Normal stack reshaping does not mint, delete or free a claim. A
player may take a real stack from a real chest; the corresponding observation
must interrupt or transfer only the affected allocation.

### 3.6 Operations, fronts and movement

Never make one scene an entire army. The canonical hierarchy is:

```text
StrategicOperation -> OperationPhase -> one or more disjoint OperationFronts
                  -> at most one temporary PhysicalSceneLease per front
```

The operation owns intent, full roster and coordination. A front owns a bounded
disjoint roster/cargo subset, its local topology/cursor/objective and result.
An actor, allocation or effect belongs to at most one current front/lease.
Scale through fronts, not by raising the physical scene member cap.

Movement uses typed support surface, body/feet position, semantic facility port,
work station and transport node. Do not overload one `BlockPosition`. Canonical
movement owns selected port, immutable 3D checkpoints, capability graph and a
bounded navigation envelope. A HOT local provider owns sub-block navigation,
collision, doors, slopes and avoidance *inside that envelope*. Only an observed
checkpoint advances the canonical cursor. A local navigator cannot pick a new
entrance, skip a checkpoint, teleport, invent a route or perform strategic
rerouting.

Pedestrian, ground bioform, flying bioform and future rail movement are
different physical providers over distinct capability graphs. Future Create is
rail-only and cannot own canonical route selection, cargo truth or economic
state.

### 3.7 Recovery and failure classification

Every physical binding has a monotonic authority epoch. After restart, the
system either proves and reclaims the current binding, fences a safely
checkpointed stale lease and continues COLD under a newer epoch, or isolates
the smallest ambiguous non-replayable effect for observation. It never waits
forever for a player to revisit an old chunk.

Classify every failure before coding a response:

| Class | Result |
| --- | --- |
| Domain disruption | Normal typed event; its owner repairs, replans, replaces, retreats or abandons visibly. |
| Reconciliation ambiguity | Isolate only the affected asset/front; retain evidence and bounded recovery/inspection. |
| Canonical corruption | Quarantine the whole frontier before another mutation. |

`CONFLICT` is not an acceptable terminal catch-all. A player breaking a crop,
stealing a cargo stack, killing a worker or blocking a door must not quarantine
the world or permanently freeze unrelated work.

### 3.8 Semantic equivalence and first visibility

HOT and COLD must have the same identities, conservation laws, strategic goal,
legal actions, checkpoint vocabulary and keyed randomness. They need not have
identical foot trajectories, combat hits or animation frames. Non-intervened
fixed-seed runs must stay inside calibrated outcome tolerances; a neutral player
watching must not systematically improve or worsen the result.

Static genesis geometry belongs to normal chunk generation before the player can
interact with it. On later natural visibility, current dynamic state is
reconciled before managed objects admit interaction or a scene. This is not
permission to force-load, rewrite drift or batch-build in the player's view.

## 4. Execution protocol for every F0 slice

Treat F0 as strictly ordered. Work on only the first incomplete slice listed in
the ledger. Do not start MAT-004, another scene type, an endpoint executor, a
hive combat feature or a Create integration while F0 remains open.

For the active slice:

1. **State the defect precisely.** Name the canonical owner, physical owner,
   present illegal authority path and player-visible symptom. Submit the
   V3-AUD finding for the engineer to record before a broad change.
2. **Confirm the approved target transition.** Specify command/due action → expected
   revision/epoch → event(s) → WAL boundary → canonical state → observable
   effect/observation → recovery path. Identify what remains COLD, what may be
   HOT and who owns postconditions.
3. **Make one coherent fresh-world format cut.** Delete rejected assumptions;
   do not add migration or compatibility code for development worlds. Recreate
   disposable worlds whenever state format changes.
4. **Install a recurrence guard before relying on a scenario.** A source,
   composition, codec, state-machine or property test must reject the exact
   family of error discovered. A timeout, retry or log suppression is not a
   guard.
5. **Add ordinary + adverse evidence.** Critical code needs focused pure tests,
   a negative or recovery test, matching GameTest slice and one checked-in
   declarative native scenario where the player-visible flow matters.
6. **Run only relevant gates during iteration.** Use focused unit/Node and the
   matching named GameTest slice. Run the complete critical-code gate before the
   slice commit. Do not burn time launching unrelated full suites on every
   source edit.
7. **Capture proof for independent acceptance.** Record a bounded correlation
   trace and clean semantic frame for visible claims. Submit audit,
   architecture, materialization inventory and ledger facts to the engineer;
   it owns these edits and Gate C acceptance before a scoped commit or next order.

### Required F0 order

Every row also includes the additional mandatory slice exits in
`frontier-v3-execution-semantics.md` and the foundation brief. Perform the
requirements/evidence alignment at the next safe F0.VA measurement boundary;
an old green endpoint test is not proof of the corrected contract.

| Slice | Deliverable | Do not accept as completion |
| --- | --- | --- |
| F0.0 | inventory plus ratchets for paired driver/legacy authority growth | a prose list without a failing guard |
| F0.V | measured fast contract/crash/scenario runner foundation, including mandatory F0.VA accelerated loop | fake Minecraft evidence, retries, weaker assertions, cached final proof, reused mutable worlds, or discarded F0.1 WIP |
| F0.1 | one process whose COLD and HOT drivers share one cursor and exact worker | crop/output endpoint, scene-only progress, or a demand-created start |
| F0.2 | replica/custody separation and deferred aftermath reference verticals | active-container branch, player-gated explosion, or desired-state repair |
| F0.3 | fungible lots/claims/custody and ordinary vanilla inventory transformations | permanent UUID tags on interchangeable stacks |
| F0.4 | first-class polity decision authority, operation tactical plans, bounded fronts and envelope-bound local navigation | AI policy spread through scenes/providers, a bigger monolithic scene or direct-vector pseudo-AI |
| F0.5 | epoch fencing plus actionable failure owners | permanent `UNKNOWN_AFTER_RESTART`/`CONFLICT` limbo |
| F0.6 | calibrated observer neutrality, first visibility and scale proof | one pretty loaded scene or TPS observation alone |

## 5. Current handoff: F0.V integration feedback

F0.0 is mechanically guarded. Stop feature and F0.1 implementation now,
preserve its complete dirty worktree, and execute
[`frontier-v3-integration-feedback-foundation.md`](frontier-v3-integration-feedback-foundation.md).
Do not reset or rewrite the harvest work to manufacture a clean baseline.

After the currently running command/process reaches a safe boundary, preserve
its manifests and failure bundle and interrupt further full-matrix work to
execute mandatory substage
[`frontier-v3-accelerated-verification-loop.md`](frontier-v3-accelerated-verification-loop.md).
Implement its exact lifecycle barriers first, then the persistent matrix
client, evidence cache/dependency selector, enforced pyramid, four-worker CI
matrix and development-only immutable fixture images. Resume the original F0.V
matrix only after the F0.VA exit gate passes.

F0.V must leave a reusable fast HOT/COLD contract harness, deterministic
crash-window controller, generated semantic scenario matrix, one-build/
one-client restart runner, phase-timing report and automatic failure bundle.
Its exit gate includes unchanged correctness evidence and measured benefit for
the same graceful reference restart. The user accepted 22.52 percent on
2026-09-05; 25 percent is an advisory target, not a blocking floor or reason to
repeat native runs. Current-source correctness/recovery remains required. F0.VA
retains the 5.065026x local measurement as sufficient advisory evidence.
Four-worker correctness composition, isolation and fail-closed merge remain
required implementation properties, but no numeric provider-speed floor or
standalone timing campaign blocks resumption of F0.V. Collect comparable timing
from ordinary subsequent work.

After F0.V is independently accepted, the engineer updates the ledger and issues
the next order for preserved F0.1. Do not begin F0.2 or any MAT breadth first.

### 5.1 Paused resume target: F0.1 resource-site harvest

F0.1 remains the next reference slice after F0.V until the ledger records all
of its exit evidence. Do not declare it complete merely
because the previously existing harvest endpoint/restart tests are green.

The intended harvest model is:

```text
ResourceSiteHarvestJob (canonical owner)
  owns farmer, field/depot identities, 3D traversal, current cursor,
  progress/output rules and its stable COLD schedule
       |
       +-- no physical custody: COLD advances legal retained worker/cursor state
       |              (never secretly removes a crop or mints output)
       |
       +-- natural demand: one harvest scene lease attaches at that exact cursor
                           and the registered HOT behavior observes arrival/work
       |
       +-- no demand/release: checkpoint same cursor -> close lease -> resume
                               the same stable COLD schedule
```

The same exact farmer must survive COLD → HOT → COLD → HOT. No scene-local
cursor, arbitrary ambient rebase or newly selected nearby Villager is allowed.
Only the very first unstarted handoff may compile from a proven ambient body;
after the process has retained a cursor, any handoff must prove that exact
canonical body/cursor or fail locally.

### Known physical regression to resolve correctly

The current native unload/return probe found a specific re-entry defect:

- canonical harvest and its same farmer/cursor continue correctly after the
  field unloads;
- on return, the physical farmer is deferred because generic standing admission
  treats a mature `CropBlock` in the feet cell as a solid obstruction;
- the immutable harvest topology correctly uses the farmland support below
  that crop. Moving the worker one block upward would falsify the plan and
  changing generic admission would permit unrelated bodies through blocks.

The valid fix is a narrowly registered harvest-scene physical standing rule:
the harvest behavior may accept a crop as passable feet-cell vegetation while
still requiring exact owned support and clear headroom. Generic ambient and
other scene admission must keep the ordinary collision rule. A fence/full block
in the same cell must remain blocked. This is a typed physical-provider rule,
not a coordinate or settlement-specific exception.

Required F0.1 evidence, in addition to the existing focused tests:

- a GameTest proves ordinary standing still rejects the mature crop, the
  registered harvest standing rule admits it, and the same rule rejects a
  fence/full obstruction;
- native `disposable-redwillow-harvest-unload-return` starts from an ordinary
  canonical fixture, captures the named farmer during work, leaves naturally,
  returns and captures the same named farmer resuming current work;
- the terminal assertion includes the canonical job/actor/cursor and the
  bounded PMV3 correlation trace, not just an entity lookup;
- no server/client is left running after the disposable scenario.

If this exposes a different defect, classify it instead of layering retries on
the current one. Examples: a duplicate schedule is a schedule-owner problem;
a changed crop is a domain disruption; an unclaimable old body after restart is
an epoch/fencing problem, not a reason to respawn a replacement farmer.

## 6. Testing and evidence discipline

### 6.1 Fast feedback

Run the smallest relevant pure test(s) and Node schema/guard test after a
change. For HOT/COLD scene work use
`./gradlew :pale-mirror-neoforge:runFrontierV3SceneGameTestServer`; use the
economy slice for resource custody. A dedicated GameTest must validate both the
new allowed state and a nearby forbidden state.

Before a coherent critical-code commit run the `AGENTS.md` critical gate:

```text
./gradlew guardrails check \
  :pale-mirror-neoforge:runGameTestServer \
  :pale-mirror-neoforge:build \
  :pale-mirror-neoforge:verifyPackagedJar
```

Report unavailable infrastructure honestly. A passing scenario does not replace
the critical gate; the full gate does not replace a precise causal scenario.

### 6.2 Native scenario rules

Use the checked-in test-pilot scenario runner, one normal visible native client
at most, on `DISPLAY=:0`. It owns a disposable seeded server/world and must not
touch the user live server on port 25565. Scenarios may establish an explicit
read-only fixture precondition and use normal player behavior afterwards. They
must not force-load, edit world files, mutate canon through debug APIs or use a
server-selected target as fake player evidence.

Each scenario needs:

1. stable seed and required canonical/world preconditions;
2. ordinary player actions plus semantic camera barriers where visibility
   matters;
3. terminal domain assertion, not a transient `READY`/`PREPARED` phase;
4. a bounded PMV3 JSONL correlation trace across process → lease →
   actor/cargo/effect → outcome;
5. restart split when durable state, non-replayable effect or recovery changed.

Frames prove readability of the current scene, not pixel-perfect rendering.
Keep presentation clean unless the player UI itself is the assertion.

### 6.3 Foundry and physical evidence

Foundry is read-only. It compares the immutable plan with already loaded world
cells; it never loads, repairs or adopts a changed cell. Select and state the
phase (`PLAN`, `COMPILED`, `MATERIALIZED`, `SETTLED`, `RELOADED`). For a real
materialization defect, report catalog hash, semantic owner, coordinates,
phase, failing rule, physical observation, fix and recovery proof.

An unloaded location is `unverified`, not proof that it is broken or a reason to
force-load it. A changed/foreign loaded owned cell is evidence for the smallest
owner. It remains visible until the normal process policy handles it.

## 7. Change-control checklist

Before writing a line of critical code, answer in the implementation note or
test name:

- What exact canonical record is authoritative before and after the change?
- Which layer owns the mutation, and what typed observation enters it?
- Is the action atomic, duration-bearing, distributed or ambient? Why is the
  chosen boundary correct?
- Which identity, revision and authority epoch fence a stale observation?
- What happens if the player removes the actor, item, support, route or organ?
- What happens if the chunk unloads, the server stops gracefully, or it crashes
  between durable state and physical effect?
- Can an unaffected settlement/front continue?
- What does a player visibly see, and which trace/frame proves it?
- Does the change require a fresh-world schema cut? If yes, delete old bytes
  and reset disposable/deployed worlds; do not add a migration.

Stop, submit the audit finding, and seek an explicit design decision if the only way
forward is force-loading, a silent overwrite, synthetic teleport/replacement,
unbounded entity scan, permanent waiting for a visit, a second process cursor,
or a global quarantine for ordinary play.

## 8. Commit, deployment and handoff

Keep F0 slices coherent and independently reviewable. A slice commit includes
only the source, tests, machine guard, documentation and ledger needed to prove
that slice. Use a concise Conventional Commit message. Do not mix root pack or
deployment files into a nested Pale Mirror commit.

Before every commit:

- inspect both repository statuses and stage each repository separately;
- run `git diff --check` and the required gates;
- confirm generated run data, screenshots and local worlds are not staged;
- verify architecture, audit, materialization inventory and ledger agree;
- state exact passed/failed evidence and remaining F0 gap.

Commit and deployment require their own explicit engineer grants under existing
user authority; this checklist is not a standing permission to stage all WIP.

Deployment is a separate operation after a clean verified commit. It uses the
root repository scripts, a fresh v3 world for state-format changes, pinned
artifact checksum and server-operation preflight. Never call a service running
or a stale log line a successful deployment. The user live server is a product
observation surface, not an iterative test fixture.

At handoff, submit compact factual ledger updates to the engineer: completed slice,
evidence paths/IDs, first incomplete slice, discovered defect and exact next
command. Do not copy raw logs or long chat history into it.

## 9. Completion statement for the continuation agent

The F0 programme may be declared complete only when its machine guards, F0.V
and all F0.0–F0.6 exit gates pass in a fresh disposable world, the audit findings are
closed with evidence at the claimed M-level, and this sentence is true:

> Watching, leaving, returning or restarting changes only how the same living
> world is executed and shown. It does not start its work, preserve a dead
> projection as authority, change its economic rules, freeze its history or
> reveal a materialization switch.

Only then may the engineer authorize resuming MAT-004 and the remaining materialization
breadth. Until then, correctness of this foundation outranks feature count.
