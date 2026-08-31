# Pale Mirror Frontier v3 contract

Status: accepted target architecture; not yet implemented.

This document is the stable contract for Frontier v3. Implementation details may
change, but changing an invariant or player promise here requires an explicit
product and architecture decision in the same commit.

## Player promise

The player enters an autonomous world that was living before they arrived,
continues to live both near and far from them, and treats every physical action
and consequence as a cause of what happens next without exposing a switch
between "simulation" and "Minecraft".

The graybox proves that promise with readable placeholder geometry. It is not a
separate diagnostic simulation and cannot use labels or operator commands to
hide a missing world process.

## Target and exclusions

Frontier v3 is a greenfield Java implementation. It is not a continuation of
the Python parity port or an adapter over `ReferenceGrayboxSimulation`.

- The new pure-Java module is `pale-mirror-frontier`; its stable package root is
  `io.farfrontier.palemirror.frontier.v3`.
- It depends only on the Java standard library. It does not import
  `pale-mirror-domain`, `frontier.reference`, Minecraft, NeoForge, persistence
  adapters, wall-clock APIs, or ambient random APIs.
- Python, `docs/frontier-reference-*`, source checkpoints and v2 calibration
  fixtures are historical evidence only. They do not gate v3 behavior or
  require parallel development.
- Frontier v3 supports fresh worlds only. It does not migrate or reinterpret a
  v2 save.
- Existing v2 runtime remains frozen and isolated until the v3 cutover gate.
  There is no v3-to-v2 fallback and no shared canonical state.
- A launch with `pale_mirror.frontier_v3.enabled=true` exclusively owns
  `pale_mirror:frontier_graybox`: v2 ticking and source-graybox commands are
  disabled for that launch, including after a v3 quarantine. v2 remains
  independently runnable only on a non-v3 launch; it may never republish into
  a v3 physical world.
- General techniques learned from v2 may become new requirements or tests, but
  production v3 code may not import or delegate to v2 implementations.

The accepted post-graybox industrial direction is defined by
`frontier-v3-create-direction.md`. Create remains a future physical provider,
not a dependency or second simulation inside the current pure domain. Its
accepted transport scope is rail only; road freight and aeronautical transport
are excluded until a later explicit decision. Current v3 facilities, exact
cargo, routes, construction and physical leases must preserve the
provider-neutral seams required for future extraction, energy, metallurgy,
warehousing/rail logistics and construction/engineering mechanisms. Actual
Create implementation is not an exit condition for the current graybox goal.

## Authority and mutation

Frontier v3 has one canonical world state and one ordered mutation lane per
Minecraft world. NeoForge invokes that lane on the server thread; no worker,
renderer, entity AI, adapter, or persistence callback mutates canonical state.

Every mutation follows this flow:

```text
typed command or due action
  -> validation against one revision
  -> deterministic domain events
  -> durable transaction when required
  -> atomic state transition
  -> immutable projections and physical intents
```

The domain owns the meaning of settlements, people, companies, resources,
routes, operations, territory, infection and the hive. Minecraft is not a
second strategic simulation. However, a confirmed physical result is immutable
evidence: an actual death, explosion, stolen item, blocked route or changed
block must enter through a typed observation and cannot be denied or repaired
away because the former desired state disagrees.

Commands may be rejected; accepted events may not. A rejected command returns
a typed reason and changes nothing. Invariant failure quarantines the affected
frontier instance visibly and stops its advancement; it never regenerates or
falls back to v2.

## Stable interfaces

The pure module exposes immutable values and narrow ports, not mutable
aggregate internals:

| Contract | Responsibility |
| --- | --- |
| `FrontierEngine` | Submit commands, advance due work under a budget, publish a revision and checkpoint state. |
| `FrontierCommand` | Versioned intent with command ID, world ID, expected revision, actor/cause and payload. |
| `FrontierEvent` | Versioned accepted fact with event ID, transaction ID, simulation instant and cause chain. |
| `ScheduledAction` | Persisted future work with stable ID, due instant, priority and deterministic tie-break key. |
| `FrontierProjection` | Immutable query result for presentation, materialization, audit and operator diagnostics. |
| `PhysicalIntent` | Durable request for one non-replayable Minecraft effect, lease or custody transition. |
| `PhysicalObservation` | Deduplicated evidence of the actual Minecraft result, including changed blocks, entities and items. |
| `FrontierStore` | Append/recover transactions, snapshots, schedules, physical leases and bounded receipts. |

NeoForge implements storage and Minecraft ports. It may submit commands and
observations and consume projections/intents, but it never receives mutable
domain collections. Public API types contain stable IDs, enums, fixed-point
values and immutable records only.

## Identity, quantities and geometry

Every long-lived object has a typed stable ID scoped by frontier world ID. IDs
are never derived from list position or a current Minecraft entity ID. Events,
commands, schedules, effects, cargo batches, containers and materialization
claims also have stable IDs and bounded retention rules.

The initial production profile is:

- one finite 1024×1024-block frontier world;
- twelve autonomous settlements;
- twenty to forty exact residents per settlement at bootstrap;
- one resident equals one canonical person and one managed Villager when HOT;
- one distributed hive with two seed nests and one shared organ/economy graph;
- one canonical bioform equals one managed hive creature, represented by a
  Zombie in graybox;
- one ordinary Minecraft item equals one matching canonical item; there is no
  hidden stack or cohort multiplier;
- graybox roles and object kinds differ through colour and geometry, not
  population aggregation.

Buildings, settlement structures and hive organs are domain objects with an
ID, type, footprint, semantic parts, capacity, integrity, infection state and
operational state. Their blocks are indexed by object and semantic part so a
physical effect can damage the correct subject without reducing a building to
one arbitrary health number.

Territory, navigation and infection use separate spatial resolutions. The
initial infection surface is a sparse 4×4-block cell field. Each seed nest
begins with a real 3×3 cluster of those exact cells (saturated core, active
cardinal cells and infected corners), giving its initial territory a readable
12×12 physical footprint without a presentation-only population or area
multiplier. Each materialized cell is one complete sixteen-column surface patch whose measured physical
positions are retained as bounded provenance, while its intensity remains one
canonical value. Before its first block write the surface patch records those
exact columns as `PREPARED`; recovery may activate only a complete matching
patch, retry an entirely absent patch, or visibly conflict a mixed/foreign
patch—never complete or stack an interrupted write. Strategic territory uses larger cells; actors and physical
effects retain fixed-point block-space positions. Baseline terrain plus a sparse
physical-delta index records scars, obstructions, craters, player construction
and infection overlays without copying the entire Minecraft world into
canonical state.

## Time and event execution

`SimInstant` is a monotonic signed 64-bit integer. One normal simulation unit is
one running Minecraft server tick; 24,000 units are a nominal Minecraft day.
There is no indivisible daily phase and no loop over every object each tick.

- The engine executes persisted actions whose due instant has arrived.
- Processes schedule their next meaningful transition. Periodic bounded pulses
  are allowed only for systems whose continuous state cannot be represented by
  a known next event, such as local infection diffusion or market clearing.
- Stable priority and ID order resolve equal due instants; collection iteration
  and thread completion order never do.
- The server continues COLD simulation while it is running with zero players.
- A stopped server advances no time.
- Completed player sleep submits an explicit elapsed-time command for the
  skipped interval. `/time` changes presentation time only and do not advance
  the simulation.
- Operator fast-forward invokes the same due-action engine and event rules; it
  is not a second fast simulation or a daily shortcut.
- Work budgets may defer execution to a later server tick but may not reorder,
  merge or discard due actions. Simulation lag is measured and visible.
- If a known due action has become obsolete because a prior durable fact changed
  its domain precondition, its planner emits one persisted `Cancelled` effect.
  An unknown action kind or invalid planner state remains fail-closed; neither
  case may be silently discarded.

All canonical arithmetic uses integers or named fixed-point value types with
checked overflow and explicit rounding. Random decisions use a counter/keyed
generator derived from world seed, subsystem, subject ID, decision kind and
decision ordinal. Adding an unrelated random call cannot perturb another
subject's sequence.

Determinism means that the same v3 input command/event stream produces the same
canonical events and state. It does not mean numerical equality with Python or
v2.

## Autonomous humans and hive

Settlements own residents, households, capabilities, professions, assignments,
organizations, exact disease state, quarantine policy, facilities, inventories,
production processes, needs, governance, security, companies, contracts,
credit, investment, prices, trade, migration, diplomacy and operations. A
company or institution owns real accounts and custody; resources do not exist
as an unowned global pool.

The normative human capability and unit model is defined by
`frontier-v3-human-capabilities.md`. A resident's profile/condition, learned
skills, profession/employment, current assignment, equipment and organization
are separate canonical concerns. Tactical functions such as scout, sapper,
rifle fighter or fixed-weapon crew are derived from those facts, never permanent
human classes. Mobilizing an exact worker therefore suspends or terminates
their conflicting civilian work explicitly and removes that labour from its
settlement capacity; it cannot produce civilian output and military action at
once. The existing six-value `ResidentRole` is provisional bootstrap affinity,
not the final assignment model.

The first concrete tactical organization is an assault-owned exact settlement
defender unit: it retains one ordered member list and leader identity, derives
the sole `SETTLEMENT_DEFENCE` assignment from the active assault, and admits
living, nourished residents with no conflicting claim, plus a deliberately
released COLD production worker when the same fresh sighting requires defence.
That interruption atomically returns the exact input, releases its invoice
reservation and cancels its market order before the unit claims the person. It
is not a global second roster. Other committed work, materialized/running
effects, exact equipment and return/recovery remain separate mobilization work;
they block admission rather than being silently reassigned.

Migration is an exact-person Transit journey, never a population-counter
adjustment. A displaced resident retains their origin household and settlement
during a bounded immutable corridor of adjacent positions, carries an exact
cursor and owns one destination-bed reservation. The compiler uses the visible
route network but excludes semantic building/organ volume and every other
living actor's canonical hand-off cell; a graybox route is a thin visible
surface and Transit uses its adjacent clear lane. COLD advances only a small bounded distance when
no HOT or scene executor owns that body; a later HOT journey executor must
continue the same cursor, resident identity and
reservation rather than create a second migration truth. Home changes only at
the arrived cursor. Quarantine, loss of the reserved destination housing or a
known route loss make that same journey visibly blocked; they never spawn a
replacement person, teleport one through an obstacle, oversubscribe housing or
silently erase the journey.

The distributed hive owns biomass, energy, nutrients, organs, brood, bioforms,
adaptations, infection, territorial knowledge, logistics and operations. The
two seed nests are topology roots of one polity, not independent scripted
encounters. They are not, however, a magic global inventory: an exact nutrient
crosses stores only through one retained organ-network transfer with an exact
source, target, cargo identity, bounded continuation and terminal receipt.
Growth and brood consume a local store input or that completed transfer, never
an item silently located at another nest.

The normative physiology, control and visual model is defined by
`frontier-v3-hive-physiology.md`. Infection surface, destructible hiveroot and
attached organs are three distinct layers. Hiveroot is one physical graph with
independently degradable vascular and synaptic capacities. The whole
distributed intelligence is the `HIVEMIND`; its local integration organ is a
`GANGLION`, not a heart. Connected stationary `RELAY` organs coordinate the
territory, while every coordinated expedition beyond their coverage requires
an exact mobile `OVERSEER`. Exact mature bioforms remain in owned cocoon slots
unless current work or a perceived threat mobilizes them; chunk loading never
causes awakening or ambient wandering.

Both sides use the same three-level decision boundary:

1. deterministic utility selection chooses a strategic objective from facts
   available to that side;
2. an HTN-like process expands the objective into durable tasks with
   preconditions, resources, dependencies and failure branches;
3. HOT actors receive bounded local goals for Minecraft movement, work and
   combat.

AI cannot inspect hidden enemy state or Minecraft objects outside observations.
Decisions are triggered by events and scheduled reconsideration, not one global
daily planner. The initial balance grants settlements a one-to-two-day grace
period before serious hive attacks and targets a long struggle rather than a
scripted guaranteed outcome; exact balance constants remain profile data.

## HOT and COLD execution

HOT/COLD is an execution-location change, not a change of truth or fidelity.
Every exact actor, cargo batch, operation and building remains canonical in both
states.

Scenes are not authored timelines or pre-placed encounters. A production scene
exists only when its owning canonical process emits a current typed `SceneCause`
from current perceived facts, exact actors and retained spatial state. A
disposable development fixture may construct such a state solely to make one
causal/recovery proof fast and deterministic; it is unavailable to a normal
world start and is never an alternative AI/planning path. Fixture builders,
their profile catalog and their selecting bootstrap provider exist only on a
dedicated moddev/test classpath and are absent from the production JAR;
production startup cannot select one through JVM properties.

All scene kinds share one generic lifecycle (`candidate → admission → PREPARED
→ HOT → DRAINING → CLOSED/UNKNOWN/CONFLICT`) and one persisted `SceneLease`.
A closed NeoForge `SceneBehavior` registry dispatches each known typed cause to
its own candidate, physical admission, local goals/effects, drain, recovery and
diagnostic rules. Generic infrastructure owns ordering, lease identity, actor
exclusivity, persistence and failure policy. A behavior may submit only its
own typed canonical commands/observations; it may not coerce another cause into
logistics, invent cargo, substitute an actor or bypass loaded-world admission.
Adding a scene kind therefore adds one registered behavior plus its canonical
cause and negative/recovery evidence, rather than scattering type branches
through the lifecycle.

- COLD execution advances exact actors and processes through domain events
  without Minecraft entities or force-loaded chunks.
- A scene becomes HOT only from naturally loaded non-spectator player demand.
  One persisted scene lease names its revision, members, custody and hand-off
  instant before any body or container appears. The broad hand-off point is
  demand/readability metadata only: the lease separately snapshots one exact
  canonical floor-anchor position for every member and one exact cargo floor
  anchor. A floor anchor is the semantic support-cell column, not a mob's
  feet-air cell: materialization may place feet only in the first two-cell-clear
  space directly above that same loaded column.
  Materialization may not arrange a formation around that broad point or offset
  a carrier for convenience.
- While HOT, the domain chooses intent and constraints; Minecraft movement,
  collision, combat, inventory and explosion results supply the physical facts.
  COLD rules do not execute the same action concurrently.
- Every spatial logistics operation owns an immutable bounded `OperationTravel`:
  strategic route milestones remain planning facts, while its adjacent-cell
  corridor, cursor, formation positions and cargo anchor are the one movement
  truth. COLD may advance that cursor by a bounded distance; HOT accepts only
  observed physical arrival at its next cursor. A HOT acceptance atomically
  replaces both that travel checkpoint and the lease's complete member/cargo
  anchors with the same adjacent formation; a rejected observation changes
  neither. Leaving or restarting midway
  retains that same cursor and formation, never teleports the convoy to a
  milestone or lets a second COLD route action run.
- A logistics operation may begin that travel only after a durable assembly has
  brought its exact participants and cargo to a compiled public access port.
  A settlement's strategic anchor or a resident's ambient hand-off slot is not
  implicitly a convoy location. Access ports are semantic graybox geometry with
  an unblocked two-body-high throat into the public route graph, so an operation
  cannot hide an initial transfer through a wall or duplicate a body at its
  first visible segment.
- `OperationAssembly` is a separate bounded movement truth, not a migration or
  a formation teleport: every member has a distinct assigned port slot and
  adjacent-cell cursor, while the named carrier retains cargo provenance.
  Creation leaves all actor locations unchanged; only exact COLD/HOT cursor
  transitions move them. A loaded physical obstruction is one durable,
  identity-specific fact: it records the participant's next cursor plus either
  that assigned slot or the same compiled Hall-throat floor. It freezes the
  shared assembly until the named actor reaches that unchanged cursor through
  ordinary Minecraft movement after the same floor is clear; no timer, COLD
  poll, alternate port or generic repair command may erase the fact. Completion atomically starts the first
  `OperationTravel`; an arrived travel atomically advances its strategic
  segment while retaining the formation/cargo positions. Assembly is not an
  attackable route operation, and a player/world obstruction of its compiled
  throat or slot is the visible durable deferral above or a semantic damage observation, never
  permission to choose a hidden alternative path. COLD actor records do not
  represent physical bodies and therefore do not obstruct pure corridor
  compilation; only a live HOT lease reserves its canonical floor cell, and
  the naturally loaded Minecraft world remains the final admission authority.
- HOT-to-COLD waits through a bounded no-demand hysteresis, then captures exact
  surviving bodies, positions, health, inventories, damage and unfinished
  intents. If its hand-off surface remains naturally loaded, it durably closes
  the lease and removes the exact body before serializing the chunk. If vanilla
  unloads the surface before the hysteresis elapses, the runtime may use only
  its bounded last complete HOT observation to close the lease; the unchanged
  serialized projection is removed on that chunk's ordinary next load before a
  new scene can claim the actor. After restart, where that volatile observation
  does not exist, DRAINING waits for natural inspection rather than inventing a
  release. A late Minecraft entity-leave callback never closes a HOT lease: it
  is recovery evidence for the same UUID, not proof that a COLD hand-off
  happened. Only then may physical custody be released and future domain work
  scheduled.
- COLD-to-HOT first advances the scene to the lease instant, then reconstructs
  its current state. Dead actors and completed effects are never replayed to
  make a cinematic history.
- A body UUID is deterministically derived from its canonical world and actor,
  never from a scene or ambient lease. Lease revision/tags grant execution
  authority, so an ambient-to-scene hand-off adopts the same body rather than
  cloning or recreating it. Missing, duplicated or obstructed bodies fail
  visibly and do not imply death.
- If a naturally loaded player-demand point disproves recovery of a logistics
  scene's complete exact body set or cargo carrier, the runtime persists the
  exact missing identities as recovery evidence and blocks that delivery. It
  does not recreate actors, infer deaths, duplicate the cargo, or reschedule
  the same unknown scene forever; unrelated settlement work continues.
- Battles and operations use one scene-level lease so participants, terrain,
  cargo and effects cross the boundary atomically.

If a player leaves a battle and later returns, they see current survivors,
positions, damage, infection and structures produced by COLD continuation. They
do not see frozen mobs, a reset scene, or a reenactment of already committed
events.

## Materialization and physical causality

NeoForge keeps four independent responsibilities:

1. the desired-state materializer projects current actors, objects, containers,
   routes, infection and readable graybox cues into naturally loaded chunks;
2. the physical scene executor drives bounded local actor goals and transport;
3. the causal effect executor performs persisted non-replayable physical
   actions such as projectiles, explosions, construction or decontamination;
4. the observation bridge converts actual entity, block and inventory results
   into typed evidence for domain reconciliation.

The desired-state materializer may update only an exact still-owned claim whose
observed baseline matches its precondition. Unknown or player-changed blocks are
recorded as conflicts/deltas and are never silently overwritten to restore a
template.

Physical effects have no protected settlement, player, parcel or operation
boundary. A bomber's projectile follows ordinary Minecraft flight, collision
and explosion geometry; it may hit its target, an unrelated building, a player
base, terrain, infrastructure or another actor. The executor persists cause and
lease before invocation, then records every actual affected block/entity/item.
Known subjects receive semantic damage; all other changes enter the bounded
physical-delta/scar index and can affect later pathing, infection and building.

This distinction is invariant: materialization cannot overwrite unknown
reality, while a legitimate causal effect is allowed to change any physically
reachable reality and must account for it afterwards.

Infection materializes as an obviously foreign dynamic surface and visible
contamination of structures and organs. Growth, retreat, removal and spread
remain canonical processes; the adapter does not paint infection merely for
appearance. Graybox may substitute semantic colours and cubes, but it must make
extent, direction, severity and affected objects readable from player height.
An affected object board derives its warning only from current infection cells
intersecting that object's semantic geometry; this is a readable projection, not
a second mutable infection state.

## Physical economy and custody

Canonical storage is exact slot-level inventory, even while COLD. Every stored
resource has a named owner, container/cargo ID, item kind, count and slot or
custody position.

An economic need is not permission to fabricate work or money. The bounded
market aggregate retains one exact buyer demand (resource kind, exact count,
maximum total price, durable reason and expiry), zero or more deterministic
seller quotes, and at most one accepted work order. A quote names its active
legal company, its exact offer and expiry; a work order binds the winning quote
to the producing task and immutable accepted total. Every visible workshop job
is therefore explainable as a buyer need, a seller offer and a particular
price, rather than a planner-only instruction.

Accepting a work order creates one named financial reservation before any
physical or COLD transformation begins. Confirmation consumes exact input,
delivers exact output to the named buyer and settles that reservation; failure
before irreversible work explicitly releases it. An effect that may already
have happened stays inspectable and unresolved until its physical postcondition
is known; it is never silently cancelled just to free funds. Market clearing is
a bounded deterministic due action ordered by demand urgency, expiry and stable
ID. It may leave a demand unfilled, expire stale quotes or reject an
unaffordable offer, but may not mint credit, choose an unknown seller, overwrite
player custody or create a second order for the same demand. Terminal records
compact only after no job, reservation, cargo or physical intent refers to them.

- One iron ingot is one canonical iron ingot; one stack is sixty-four units.
- HOT warehouses and depots use ordinary Minecraft containers representing
  those exact slots. They are displays and interaction surfaces for the same
  inventory, not a second stock counter.
- Player deposits, withdrawals, theft, destruction and delivery become exact
  versioned observations immediately.
- Transport uses identified batches in real carriers while HOT and exact
  canonical custody while COLD. It never creates an entity per resource unit.
- Production blocks, spills or schedules new capacity when no valid storage is
  available; it cannot deposit into a hidden aggregate.
- Foreign items are never deleted or adopted silently. Mixed or invalid custody
  becomes a visible reconciliation conflict.

The economy is calibrated so meaningful player supply can require tens or
hundreds of real stacks without changing the one-item-to-one-unit invariant.

## Persistence and recovery

NeoForge stores each v3 world in a versioned v3 namespace using periodic
snapshots plus an append-only write-ahead log. Snapshot and WAL records include
schema, world ID, revision, transaction bounds, checksum and deterministic
codec version.

Transactions have two durability classes:

- `BATCHABLE` contains purely canonical progression and may flush at a bounded
  interval before acknowledgement to external systems;
- `DURABLE_BEFORE_EFFECT` covers player custody changes, physical leases and
  non-replayable Minecraft effects and must be flushed before the effect is
  acknowledged or invoked.

Recovery loads the newest valid snapshot, replays complete WAL transactions in
order and rejects gaps, checksum errors, duplicate conflicting IDs or invalid
references. A prepared/running physical intent is inspected against the real
world and completed or quarantined from its postcondition; it is never blindly
replayed.

Checkpointing atomically writes a new snapshot, verifies it, then compacts only
WAL segments fully covered by that snapshot. Detailed terminal events,
observations, schedules, deltas and receipts have explicit count/age bounds;
compaction retains deterministic causal summaries. Shutdown drains accepted
server-thread commands, flushes durable state and reports failure visibly.

V2 files, SavedData and schemas are neither read nor overwritten.

## Performance and observability

The event engine does no world-wide per-tick scan. Spatial queries use bounded
indexes; due actions use an ordered queue; projections are incremental by
revision and chunk. No simulation, audit or materialization path force-loads a
chunk.

Before feature expansion, Wave 1 pins a reproducible benchmark on the project
test machine. The default cutover budget is sustained 20 TPS for sixty minutes,
mean server tick below 35 ms, p99 below 50 ms, Frontier v3 p95 work below 5 ms
per server tick, no watchdog event and no monotonic heap, WAL, receipt or delta
growth after compaction. A later profile may tighten these values but may not
silently relax them.

Metrics expose due/backlogged actions, simulation lag, command rejection,
events by subsystem, projection work, active HOT leases, reconciliation
conflicts, WAL/snapshot size, recovery result and per-stage timing. Metrics and
operator commands are read-only unless they submit an ordinary validated v3
command.

## Graybox and product acceptance

Graybox uses normal Villagers and Zombies, coloured cubes/rectangles, visible
route corridors, storage containers, object-local boards and strongly distinct
hive silhouettes. Infection surface, raised hiveroot trunks, vascular flow,
synaptic pulses, severance and organ silhouettes remain visually distinct.
Bioform markers expose chassis, important mutation and current assignment and
prevent only vanilla daylight ignition; actual fire and explosion effects
remain physical events to observe and reconcile. One board belongs to one
object. Geometry and motion must
communicate function before text; labels explain state, cause, risk and possible
action without exposing opaque IDs to ordinary players.

Boards are a pure projection, not a second state store: their deterministic
identity, position, scope and content come from the current canonical object.
Landmarks orient an approach; object-local facts are compact, use a local view
range and are occluded by physical geometry, so a settlement does not become a
wall of text or a remote HUD. The loaded-chunk executor owns a bounded
provenance record. A moved, replaced or missing claimed board is a visible
presentation conflict; it is never silently recreated over an unknown world
entity.

Evidence is graded separately:

1. contract/types and architecture exist;
2. deterministic domain, persistence and GameTests pass;
3. the process remains visible and continuous in a real Minecraft world;
4. an unbriefed player discovers it, changes it and correctly explains cause
   and delayed consequence.

Frontier v3 is not complete at level 1 or 2. Production cutover requires full
scope tests, long-run balance/performance, crash/restart recovery, real-display
manual HOT/COLD and physical-causality audits, and user acceptance of the
graybox's seamlessness/readability. Release readiness additionally retains the
clean-room comprehension and cooperative-player gates defined for Living
Frontier.

## Legacy removal gate

V2 runtime may be removed only when all of the following are true:

- a fresh v3 world bootstraps the complete twelve-settlement/two-seed profile;
- the full human and hive domain matrix is implemented without a legacy import;
- exact HOT/COLD battle, logistics, infection, destruction and player feedback
  survive unload/reload and restart;
- deterministic, negative, recovery, performance and manual product gates pass;
- deployment selects v3 as the sole Frontier runtime and no production command,
  config or save path can activate v2.

The removal deletes `frontier.reference`, `SourceGraybox*`, v2-only commands,
fixtures and runtime wiring. Historical documents may move to `docs/archive/`;
Git history remains the recovery mechanism. Unrelated Pale Mirror systems are
not deleted merely because they predate Frontier v3.
