# Frontier v3 seamless-world foundation correction

Status: accepted normative implementation brief.

This document is the complete hand-off for the foundation correction that must
precede further Frontier v3 materialization breadth. It refines, but does not
replace, `frontier-v3-contract.md`. If an implementation choice conflicts with
this document, the contract and `architecture.yml` must be amended deliberately
in the same commit; the implementation may not silently choose the old shape.

The brief is intentionally self-contained enough for a new implementation
agent. Chat history is not an architecture source.

## Required reading and execution order

Before changing source, read in this order:

1. `AGENTS.md` and `CONTINUITY.md`;
2. `frontier-v3-contract.md`;
3. this document;
4. `frontier-v3-implementation-plan.md`;
5. `frontier-v3-architecture-audit.md` and
   `frontier-v3-materialization-completeness.md`;
6. the process-specific contract for the active slice.

Work only on the current `F0.*` slice recorded in `CONTINUITY.md`. Do not resume
`MAT-004` or add another scene, effect or inventory family until its required
foundation slice has passed its exit gate. Existing uncommitted MAT-001 tests
and scenario work are retained as evidence input; they are not discarded.

## Product invariant

The player must experience one autonomous Minecraft world which existed before
their arrival, continues without observation, and accepts ordinary physical
actions as causes. Loading, watching or leaving a chunk may select where a
process executes; it may not decide whether that process exists, remains viable,
or receives a different systemic advantage.

The following are therefore non-negotiable:

- no Pale Mirror force-loading;
- no protected player, settlement or hive zones;
- no silent overwrite of an unclassified player/world change;
- one canonical person per resident and one canonical organism per bioform;
- one canonical item unit per real Minecraft item unit;
- exact conservation and custody without requiring permanent identity for every
  ordinary fungible Minecraft stack;
- no replayed history when a chunk becomes visible;
- no permanent loss of world autonomy because an old physical projection has
  not been revisited;
- no claim of completeness below M3 for the player experience.

## Foundations retained

The correction is not a v4 rewrite. Retain these v3 foundations:

- the single ordered canonical mutation lane;
- immutable aggregate state, typed commands/events and expected revisions;
- durable scheduled actions, checksummed snapshots and ordered WAL;
- stable IDs for actors, structures, organs, operations, contracts and cargo;
- fixed-point quantities and keyed/counter random streams;
- deterministic process and executor registries;
- typed semantic geometry, ports and traversal capabilities;
- provenance-aware physical observation;
- durable-before-effect receipts for non-replayable physical effects;
- fresh-world-only development with no compatibility migration.

The correction changes ownership granularity and hand-off protocols around
these components.

## Rejected assumptions

### FND-01 — materialized once does not mean physically authoritative forever

`ContainerSurfaceStatus.ACTIVE` currently combines two unrelated facts:

1. an owned chest has been created in Minecraft at least once;
2. Minecraft presently owns live custody of its contents.

Because `ACTIVE` has no transition back to a COLD state, one visit can make later
settlement or hive work wait for an unloaded chest. Historical realization must
not be execution authority.

Replace the concept with two independent records:

- `PhysicalReplicaRecord`: identity, semantic socket, last emitted canonical
  revision, last observed fingerprint, presence/provenance and conflict state;
- `PhysicalCustodyLease`: lease ID, container ID, authority epoch, acquired
  canonical revision, lifecycle and optional recovery evidence.

An owned replica may remain serialized in an unloaded chunk. It is a frozen
projection, not a second inventory. When its chunk is naturally active, the
runtime may acquire custody after comparing the actual fingerprint with the
last emitted fingerprint. If they match, applying later canonical state is a
safe update of an unchanged owned projection. If they differ, the difference
is observed before any write and classified through FND-07.

Canonical economy never branches on whether the replica was historically
materialized. It branches only on a current authoritative custody lease. COLD
work resumes after release and cannot wait for a merely serialized chest.

### FND-02 — semantic consequence and physical realization are different facts

A `PhysicalIntent` is not allowed to become the off-screen process scheduler.
No-force-load makes exact immediate mutation of an unloaded, potentially
player-modified block volume impossible. The architecture explicitly accepts
eventual physical realization instead of player-gated history.

Use three layers:

1. `SemanticConsequence`: the canonical event at simulation time T, resolving
   every known actor, resource, object and topology consequence atomically;
2. `PhysicalEffectReceipt`: the durable-before-effect lease used only when the
   relevant Minecraft region is already HOT and real physics will run now;
3. `DeferredPhysicalAftermath`: a bounded spatial outbox for consequences whose
   physical cells were not available at T.

For a HOT explosion, Vanilla calculates the blast and typed observations commit
its actual result. For a COLD explosion, combat and known object consequences
commit at T and one bounded aftermath footprint is retained. Natural later
loading materializes the current crater, damage and infection state; it does not
spawn the old bomb or replay combat.

Destructive aftermath has no protected-zone exception. Unknown blocks inside a
retained blast footprint are inspected and affected when first available,
unless a later durable event proves they were created after the blast. A
constructive operation may use its last proven clear authored footprint in
COLD; a later contradictory observation immediately removes or suspends the
unverified capacity and creates ordinary obstruction/replan work. It is never
silently overwritten.

Every aftermath record is bounded, chunk-indexed, idempotent, versioned and has
an explicit compaction rule. A pending physical representation cannot block
unrelated canonical work.

### FND-03 — exact quantity does not require permanent stack identity

The current named-stack model is incompatible with normal Minecraft splitting
and merging. Splitting can duplicate one tag across two stacks; merging two
different tagged IDs can be rejected by Vanilla or by Pale Mirror even though
the commodity is valid.

The target inventory model separates:

- `ResourceLot`: resource kind, exact integer quantity, economic owner,
  provenance class and bounded lineage;
- `ClaimAllocation`: an exact reserved quantity belonging to a contract,
  process, cargo batch or equipment owner;
- `CustodyAccount`: exact quantities at a container, actor, cargo, player or
  world-carrier location;
- `PhysicalStackBinding`: a temporary HOT observation binding a Minecraft slot
  or entity stack to one or more lot/allocation quantities.

Ordinary fungible items may split and merge. Each operation is an exact
zero-sum transaction and preserves kind, total quantity, claim allocation and
custody. Stable identity remains mandatory for cargo batches, contracts,
equipment and reserved allocations; it is not forced onto every interchangeable
stack forever.

One iron ingot remains one canonical unit. This correction changes identity
granularity, not the physical-economic scale. Normal chest movement, partial
withdrawal, hopper transfer, item drops, pickup and future recipe consumption
must be first-class observations rather than generic conflicts.

### FND-04 — one strategic operation is not one physical scene

A strategic operation may span many chunks, fronts and hundreds of exact
actors. One monolithic `SceneLease` cannot be its atomic boundary and must not
grow an unbounded participant list.

Use this hierarchy:

```text
StrategicOperation
  -> OperationPhase
      -> one or more bounded OperationFronts
          -> zero or one current PhysicalSceneLease per local front
```

The operation owns purpose, complete roster, resources, global phase and
coordination. Each front owns a disjoint exact actor/cargo subset, local
topology, cursor, current objective and result. A scene is only the temporary
physical execution authority for one spatially coherent front.

Cross-front coordination is canonical and event-driven. It is never achieved
through a global Minecraft entity scan or a transaction containing every army
member. An actor, cargo allocation or physical effect belongs to at most one
front/lease at a time. Reserve and transit actors still exist exactly and are
not hidden cohorts; they are canonically located outside the active local
engagement.

The current safety limit of 32 scene members remains a ceiling until a same-seed
JFR and causal proof justify changing it. Large assaults scale by fronts and
spatial staging, not by raising that constant.

### FND-05 — the canon owns route meaning, not every animation step

An immutable adjacent-cell topology is valid evidence for strategic reachability
and deterministic COLD progress, but a direct no-AI vector to every exact cell
is not the final living movement architecture.

The target split is:

- the canonical `MovementPlan` owns actor, movement capability, semantic origin
  and destination, selected facility port, ordered checkpoints, allowed
  navigation envelope, formation constraints and current checkpoint;
- the HOT `LocalNavigationLease` owns sub-block pose, facing, velocity and a
  bounded local path inside that envelope;
- Minecraft collision, doors, stairs, slopes and local avoidance determine the
  physical path;
- only observed checkpoint arrival advances canonical progress;
- persistent inability to reach the checkpoint emits typed obstruction evidence
  and canonical replanning policy decides what happens next.

The HOT navigator may sidestep inside the declared envelope. It may not choose a
different semantic port, skip a checkpoint, leave the capability graph or
change the strategic destination. Combat locomotion follows the same split:
domain tactics constrain goals and permitted behaviour; the physical brain
owns moment-to-moment movement and attacks while HOT.

Pedestrian, ground-bioform, flying-bioform and rail movement are distinct
providers. Create later implements rail physics without owning route selection
or cargo truth.

### FND-06 — restart uncertainty must be fenced, not wait forever

`UNKNOWN_AFTER_RESTART` is a safe diagnostic state but not a permanent
availability strategy. An unloaded old projection must not freeze its actor or
operation until a player happens to visit it.

Every physical lease and materialized entity/cargo/container binding receives a
monotonic authority epoch. Before an externally visible irreversible action,
the current epoch and effect receipt are durable. After recovery:

- a current, exactly proven physical binding may be reclaimed;
- a safely checkpointed lease may be revoked and its process resume COLD under
  a newer epoch;
- an old entity or binding loaded later is rejected as stale and removed or
  reconciled without emitting a second death, item or effect;
- an ambiguous non-replayable effect remains locally unresolved until its
  postcondition can be inspected, but it does not freeze unrelated actors,
  settlements or fronts.

The WAL is the authority for crash atomicity. A physical change visible only
between the last durable receipt and a process crash may be rolled back as an
uncommitted effect; no architecture can atomically commit arbitrary chunk NBT
and the canonical WAL without such an authority choice.

### FND-07 — expected gameplay disruption is not invariant corruption

Failure handling has three mandatory classes:

| Class | Examples | Required result |
| --- | --- | --- |
| Domain disruption | death, theft, destroyed door, severed root, blocked route, partial delivery | Typed normal event; process repairs, replans, replaces, retreats or abandons visibly. |
| Reconciliation ambiguity | duplicated tag, unknown crash window, conflicting physical evidence | Isolate the smallest asset/front, retain evidence and retry or request inspection. |
| Canonical corruption | impossible ownership, broken conservation, invalid WAL/schema, duplicate current authority | Quarantine the frontier instance before further mutation. |

`CONFLICT` is not a terminal wastebasket. Every domain disruption and
reconciliation ambiguity has an owning aggregate, visible reason, bounded retry
or abandonment policy and retention/compaction rule. Normal player interaction
must not progressively turn the autonomous world into permanently frozen work.

### FND-08 — HOT/COLD equivalence is semantic, not frame-identical

HOT Minecraft physics and COLD deterministic calculation cannot and should not
produce identical trajectories or hit sequences. Requiring them to do so would
turn HOT play into a scripted animation. Allowing unconstrained divergence would
make observation itself change history.

The equivalence contract is:

- identical actor/resource/object identities and initial claims;
- identical strategic goal, legal actions, conservation laws and topology;
- identical process checkpoint vocabulary and causal event classes;
- no systematic benefit or penalty from a neutral player merely observing;
- player interventions and actual HOT physical outcomes are legitimate new
  inputs and may change the result;
- non-intervened HOT/COLD outcomes meet persisted calibration tolerances across
  fixed seed sets, rather than exact per-hit equality.

Random choices use process/actor/event-keyed streams, never tick iteration or
collection order. Verification compares invariants and outcome distributions,
and separately proves exact identity/custody continuity.

### FND-09 — first visibility is an admission boundary

The player must not watch a settlement, road or hive appear in executor batches.
Static genesis geometry belongs to chunk-local world generation before player
admission. Dynamic state is caught up to the current canonical revision before
the chunk's managed objects are exposed for interaction or scene admission.

This does not authorize force-loading or overwriting drift. A naturally
generated fresh chunk receives its immutable genesis slice. A previously
observed chunk is reconciled from its replica fingerprint and deferred aftermath
records. Unresolved drift stays visible and blocks only its smallest owner.

`architecture.yml` already contains the fresh-world genesis direction. F0 must
add a graybox first-visibility scenario so the current provider proves the same
player promise.

## Target component boundaries

The following names describe responsibilities. Exact Java type names may change
if the same boundaries remain mechanically provable.

| Owner | Pure canonical responsibility | NeoForge responsibility |
| --- | --- | --- |
| Process aggregate | Purpose, participants, resources, progress, outcome | None |
| Execution-driver registry | COLD/HOT driver completeness and semantic step vocabulary | Resolve registered HOT provider only |
| Replica registry | Expected physical identity, fingerprint and emitted revision | Observe loaded replica and apply safe current projection |
| Custody lease registry | Current exclusive authority and epoch | Acquire, checkpoint, release and reject stale bindings |
| Consequence/outbox | Semantic result and bounded deferred spatial aftermath | Run loaded physics or realize idempotent aftermath |
| Inventory ledger | Lots, claims, exact quantities and custody transactions | Observe stack split/merge/move/consume/drop/pickup |
| Movement plan/front | Strategic topology, envelope, checkpoints and exact roster | Local path, collision, animation and observed arrival |
| Failure owner | Domain response, isolation, retry/abandonment and compaction | Typed evidence only; no policy fallback |

No NeoForge executor may mutate a process aggregate directly. It submits a typed
observation against the current version/epoch. No pure process may query a
loaded chunk, entity, block entity or Minecraft navigation result.

## Implementation programme

### F0.0 — inventory and mechanical guardrails

Deliver:

- classify every duration process, container surface, physical intent,
  movement owner and conflict producer against FND-01 through FND-09;
- add machine-readable registries or source checks for paired drivers,
  permanent-surface branching and scene/front ownership;
- create a fixed list of affected current process types and do not add breadth
  while it is incomplete;
- bump fresh-world schema/envelope only with the first state-changing slice,
  not for documentation alone.

Exit:

- guardrails fail for a duration process missing either driver;
- guardrails fail for new canonical process logic branching on historical
  replica realization;
- the audit maps every current instance to one correction slice.

#### Checked baseline and ownership map

The initial inventory is deliberately a shrinking technical-debt baseline, not
an approval to keep the old contracts. `tools/engineering/frontier_v3_architecture_debt.yml`
now counts every production reference to `ContainerSurfaceStatus.ACTIVE` (39
references in 30 source files). A new reference or an increase in any listed
file fails the architecture-debt gate. F0.2 removes these entries; it does not
raise their ceiling or hide a new reference behind a helper.

| Current family | Canonical owner | F0 treatment | Why it is not allowed to grow |
| --- | --- | --- | --- |
| Resource-site harvest | `ResourceSiteHarvestProcess` / `ResourceSiteHarvestJob` | F0.1 reference process | It currently proves the scene-owner fault directly. |
| Production, service and engineering work | `ProductionProcess`, `SettlementServiceWorkProcess`, route-work aggregates | Preserve exact current process records; revalidate after F0.1 | Existing HOT episodes cannot certify the shared process contract. |
| Provision, birth, medical treatment, equipment and cargo | population/economy owners and their exact custody supports | F0.2 then F0.3 | Their `ACTIVE` branches currently conflate a physical slot with economic eligibility. |
| Hive growth, nutrient and stores | hive process/organ/container owners | F0.2 then F0.3 | A historically visited Store must not become a permanent off-screen liveness prerequisite. |
| Route construction, maintenance and structural repair | infrastructure process and exact worksite owners | F0.2, F0.4 and F0.5 | Physical material or geometry evidence must not silently choose a second process clock. |
| Transit, patrol, assembly, engagement and assault | their named operation aggregates | F0.4 then F0.5 | They require envelopes/fronts and fenced recovery, not another scene-local cursor. |
| Atomic consumption, transformation, explosion and other physical intents | exact intent/effect owner | F0.2 | Their semantic result and deferred aftermath must be independent of a player visiting the origin. |

`ContainerSurfaceProcess`, `FrontierWireTags` and the read-only settlement
diagnostic are also in the ratchet because they encode or expose the legacy
state vocabulary. They migrate with F0.2 after a replacement replica/custody
projection exists. This inventory is not a statement that every listed family
is already duration-bearing; it assigns the owner of every currently affected
physical/custody branch so a later slice cannot claim an unlisted exception.

### F0.1 — process/execution ownership reference

Complete V3-AUD-043 through MAT-001:

- harvest begins and makes meaningful progress with no player and no loaded
  field/depot: before F0.2 this means the exact COLD worker and process cursor
  reach their retained next physical station, not that an unloaded crop is
  silently removed or an output stack is minted;
- arrival acquires the exact current process cursor, not a scene-local cursor;
- HOT observations advance that same process;
- leave/release returns the next unfinished step to COLD atomically;
- return displays the later current state without restart, disappearance or
  replay. Irreversible crop/output progress remains an F0.2 deferred-aftermath
  responsibility; F0.1 must not close that gap with a hidden direct mutation.

Required evidence:

- pure never-loaded progression;
- arrival during at least two distinct progress stages;
- native unload/return with the same farmer identity;
- worker death or physical obstruction;
- graceful and abrupt filesystem restart;
- composition negative test for a one-driver duration process.

### F0.2 — replica custody and deferred aftermath

Deliver:

- split persistent replica state from temporary container/object custody;
- remove canonical economy decisions based on historical `ACTIVE` state;
- introduce versioned replica fingerprints and lease epochs;
- introduce bounded chunk-indexed deferred aftermath;
- convert one settlement container, one hive store and one destructive effect as
  reference verticals.

Required evidence:

- never-visited, visited-then-unloaded and loaded-without-player economies have
  equivalent liveness;
- one visit cannot change later production or hive-growth eligibility;
- unchanged serialized chest safely catches up on return;
- changed chest becomes a typed observation before any projection write;
- off-screen explosion advances canonical casualties/damage immediately and
  later shows aftermath without replay;
- constructive conflict suspends only the affected capacity;
- restart at every durable-before-effect boundary produces no duplicate effect.

### F0.3 — fungible resource lots

Deliver:

- replace permanent exact-stack identity with lots, claims, custody accounts and
  transient physical bindings;
- retain stable exact equipment/cargo/contract allocation identities;
- provide zero-sum split, merge, partial move, consume and destruction events;
- migrate current production, provision, cargo, equipment and hive biomass
  processes in one fresh-world schema cut.

Required evidence:

- player splits a stack, moves only part, merges it again and total/ownership
  remain exact;
- hopper movement, world drop/pickup and partial recipe consumption work;
- theft of a reserved portion interrupts only its owning work;
- duplicate quantity, mixed kind, over-consumption and stale binding are
  rejected without minting or deleting resources;
- snapshot/WAL replay preserves totals and allocations.

### F0.4 — semantic navigation and front partitioning

Deliver:

- introduce movement envelopes and HOT local navigation leases;
- retain canonical ports/checkpoints while allowing bounded physical local path;
- change operation-to-scene ownership to operation phases and disjoint fronts;
- use the same model for patrol and hive expedition work; do not build a
  combat-only exception.

Required evidence:

- slopes, stairs, doors and a harmless local obstacle produce natural motion;
- leaving the envelope, changing port or skipping a checkpoint is rejected;
- persistent blockage creates canonical obstruction and bounded replan/abort;
- two fronts of one operation progress independently without actor overlap;
- a same-seed JFR proves current budgets before any limit increase;
- a native return scenario shows current survivors and positions, not a reset.

### F0.5 — fenced recovery and failure ownership

Deliver:

- authority epochs for body, cargo, container and effect bindings;
- stale-projection tombstone/rejection on later natural load;
- explicit domain-disruption, reconciliation-ambiguity and corruption results;
- bounded retry, recovery/abandonment and compaction policy per affected owner.

Required evidence:

- graceful and hard crash at PREPARED, HOT/RUNNING, DRAINING and effect windows;
- an unvisited stale projection cannot freeze COLD progress after recovery;
- late stale bodies/cargo do not duplicate or emit false death/theft;
- ordinary player damage does not quarantine the world;
- actual conservation or ownership corruption still quarantines before mutation.

### F0.6 — observer-neutrality, first visibility and scale gate

Deliver:

- fixed-seed HOT/COLD calibration harness with invariant and tolerance checks;
- graybox first-visibility admission scenario;
- multi-front pressure/JFR scenario retaining exact 1:1 actors;
- read-only diagnostics for process cursor, front, lease epoch, replica revision,
  deferred aftermath and failure class.

Exit:

- neutral observation does not systematically alter success, casualties,
  throughput or completion time outside accepted tolerances;
- no static object visibly appears after its chunk is exposed to the player;
- twelve settlements plus concurrent hive fronts meet queue, TPS, heap, WAL and
  physical-body budgets;
- all evidence has bounded correlation traces and relevant clean frames.

## Cross-slice migration rules

- V3 is fresh-world-only. Delete obsolete codecs and assumptions; do not write
  compatibility adapters for current development worlds.
- Make one coherent source-format cut per state-changing slice and recreate all
  disposable/live v3 worlds after verification.
- Do not combine F0.2 and F0.3 merely because both touch inventory. First fix
  authority ownership, then change fungible identity under that owner.
- Preserve existing focused tests as lower-level evidence, but rewrite any test
  whose expected result encodes a rejected assumption.
- A green endpoint test does not prove liveness, player comprehension or
  observer neutrality.
- Commit each completed F0 slice independently after its focused and required
  critical gates. Deployment follows only a clean verified commit and a fresh
  world.

## Stop conditions

Stop the active slice and update the audit before continuing if any of these
occur:

- player demand is required to create, schedule or finish viable canonical work;
- an unloaded replica becomes a canonical inventory or process owner;
- the proposed fix adds a silent overwrite, force-load or hidden replacement;
- a normal Minecraft action reaches quarantine instead of a domain response;
- a scene/front requires an unbounded roster or world scan;
- HOT introduces a second route, target, progress counter or resource balance;
- recovery can duplicate an actor/item/effect or wait forever for an unrelated
  player visit;
- a test passes only by teleport, server-selected target, fixture mutation or a
  transient phase assertion.

## Foundation completion gate

The foundation correction is complete only when all F0 slices are implemented,
the corresponding architecture-audit findings are closed, the machine-readable
guards reject recurrence, and the following product statement is true in a
fresh disposable world:

> Watching, leaving, returning or restarting changes only how the same living
> world is executed and shown. It does not start its work, preserve a dead
> projection as authority, change its economic rules, freeze its history or
> reveal a materialization switch.

Only then may the implementation plan resume MAT-004 through MAT-010 and later
Wave 6 breadth.
