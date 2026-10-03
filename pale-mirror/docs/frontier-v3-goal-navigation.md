# Goal navigation for HOT/COLD actors

Status: accepted target design, 2026-09-25; implementation/evidence are not
certified by this document. The field worker is the first adoption vertical.
`frontier-v3-execution-semantics.md` remains the general authority and
`frontier-v3-field-cell-lifecycle.md` owns agricultural outcomes.

## Decision

### Single HOT locomotion owner (2026-10-01)

The user requires every target-directed HOT actor movement, including ambient
work/guard/patrol travel, assembly, service, medical, production and tactical
scene movement, to enter the shared navigator. Retained topology families may
still issue their next mandatory checkpoint with an explicit restricted scope;
they may not implement another physical path follower. Ordinary gravity and an
already-arrived stationary work gesture do not select destinations or move an
actor between stations. This supersedes the earlier phased-adoption permission
to retain parallel active custom movement for other families below.

Navigation acquisition retires only the preceding actuator's local directive,
never the route/leg it is acquiring. Repeated same-goal refresh preserves local
leg and in-flight motion. Path exhaustion is not observed arrival and cannot
by itself withdraw physical integration from an unfinished jump. Explicit stop
and ownership handoff retire native movement inputs/jump commands as well as
the path. The implementation is under migration; this decision is not a claim
that all active callers already conform.

### Accepted HOT route-policy correction (2026-10-01)

For goal-migrated pedestrians distinguish hard task restrictions from an
ephemeral route hint and an accepted physical-path corridor. The caller supplies
the versioned semantic order, a bounded known-route hint and the authoritative
world/task scope. It does not select micro-waypoints, build an inflated stripe,
or decide local obstacle avoidance. The shared HOT route follower chooses short
physical legs; intermediate arrival never completes the semantic order. An
unreachable hint support may be bypassed toward a later support of the same
route, never a different work target or mandatory semantic checkpoint.

The registered Minecraft provider searches naturally available terrain and one
shared pedestrian policy validates the candidate against hard scope, loaded
nodes, permitted movement medium and finite path bounds. A valid detour outside
the preliminary route stripe is accepted. Only after acceptance is a discardable
local corridor derived from the physical path; changed native paths pass the
same policy before that corridor is refreshed. An explicit topology/formation
envelope remains a hard restriction for families that require one; it must not
be silently widened or reinterpreted as an advisory hint.

Path unavailability, forbidden scope, unsupported medium, unloaded physical
nodes and exhausted path bounds have distinct explanations. Failed local
queries back off before retry; creating another path is not progress and cannot
renew the same no-motion deadline. A local query miss is not proof that no
global route exists. Domain owners retain work/blocked-goal decisions and the
service coordinator retains station access. Physical navigation owns no meal,
crop, custody, schedule or authority transfer.

COLD continues from retained known geometry without requiring first chunk
loading or reading unloaded Minecraft blocks. An unavailable HOT leg is not
permission to advance a concurrent COLD copy: the existing exact-body checkpoint
and authority-release protocol owns handoff. HOT-discovered detours do not
silently become COLD route knowledge. Unknown natural terrain still requires
the declared autonomous knowledge/approximation policy, not invented evidence.
This amendment supersedes earlier references below to a *precomputed route
stripe* as a hard restriction for goal-migrated actors. It does not waive true
task/topology restrictions or claim completed native/player acceptance.

Separate *what an actor intends to do* from *which physical blocks it walks
through*. The canonical process owns its exact actor, task, semantic work
targets (a revisioned target pool for area work), completed outcomes, custody, capability, authority epoch and
current movement goal. A target is a typed work cell, port or bounded arrival
region with a layout/topology revision and an arrival contract. The physical
micro-path and locomotion state are not canonical work progress.

A shared provider-neutral navigation boundary accepts one current goal and
returns `IN_PROGRESS`, observed `ARRIVED` with the exact legal station,
`BLOCKED` with cause/evidence, or `AMBIGUOUS`. It neither selects the next
task nor awards work, resources or arrival. An ambiguous match of multiple
stations cannot be converted into an arbitrary first arrival. Its HOT
pedestrian implementation uses Minecraft pathfinding and
ordinary entity movement within the declared hard capability/task scope;
Minecraft goals must not acquire an independent task or economy authority.
The path is ephemeral, may be recalculated after a real local obstruction and
is validated against hard scope, not an advisory route stripe. A path that exits
a true restriction is not an authorized detour. A different capability (swimming, flying, rail) requires a
declared provider, never an implicit fallback.
The envelope names *support blocks*, not feet blocks: a pedestrian's declared
one-block grade latitude includes support one block below or above the current
support where real collision and headroom permit it. A lower-floor detour is
not flight or a new task goal.

COLD uses the same actor, task, semantic goal and completed work. It advances
only against retained known geometry and declared bounded travel/labour costs;
it does not call Minecraft navigation or inspect unloaded blocks. An observed
player/world edit invalidates the affected knowledge and is binding in both
modes. Unknown geometry uses the family's explicit bounded knowledge/replan or
abandonment policy, not invented exact evidence. The current actor location and
progress are retained at hand-off; entering HOT never restarts the job or
teleports an observed body. A later no-observer admission materializes the
same actor at its current COLD position.

Canonical goals are not physical paths. A workstation may be a bounded set of
legal supports adjacent to an interaction target; exact feet-cell equality is
required only when that interaction really has one atomic station. The
registered navigation provider chooses a legal support *within the declared
goal and envelope*. Only a matching observed arrival permits the process to
prepare and confirm a physical action. HOT path recomputation never advances a
work cursor; COLD never fabricates a physical receipt.

### Known pedestrian geometry and service access

A task issues an actor, capability, versioned goal and legal station set. It may
name an authored facility passage or field-work envelope, but may not assemble
its own raw blocked-block set or decide that another moving actor is permanent
terrain. A pure, revision-bound, read-only route-knowledge provider derives
surveyed support and hard physical/structural obstructions from authoritative
plans and exact observed post-states. A `PhysicalDelta` alone records a changed
cell, not its final block state: until that state is known, the provider must
treat an affected route cell as an epistemic gap, not assert that it is solid
or silently walk through it. It exposes one bounded route operation and may reuse
one immutable view while considering several legal stations; it stores no
second map, actor ownership or work outcome. A field crop/work-cell condition
is a typed field overlay, not a generic obstruction inferred from an actor's
position. A changed physical access cell cannot be cleared merely because its
facility passage was authored.

The service-access coordinator independently owns admission to the exact
shared throat/station. Long approaches and exits may overlap in time; a permit
does not reserve the entire journey. HOT Minecraft navigation handles transient
local actor avoidance within the retained envelope. The canonical movement
owner handles COLD timing, HOT hand-off and observed arrival, while the issuing
activity resumes only on a matching outcome. Neither the geometry provider nor
the coordinator may select the next job or award a meal/work effect.

The reusable COLD implementation is `KnownPedestrianRouteKnowledge`: tasks
declare an exact canonical facility and `EXTERIOR` or `PUBLIC_ACCESS` reach;
the provider validates that facility, takes its port from the same
`FrontierTraversalPlan` used by topology, derives structural/observed route
knowledge, and calls one bounded pedestrian pathfinder. The existing hall,
depot, workshop and infirmary ports are accepted without a route-case switch;
only depot/workshop currently have active route callers. Field work adds
its current canonical CellId/soil/crop overlay to that same provider, not a
second pathfinder or caller-assembled block set. New facility services reuse
the contract by supplying a declared `FacilityTraversalPort`; a new kind of
area may add a typed authoritative overlay, never task-owned raw obstacles.
This source architecture does not itself prove HOT movement, route recovery,
throughput or player-visible product acceptance.

## Field-worker policy

- The durable field plan names layout revision and stable CellIds. It accounts
  each cell once as harvested, repaired/planted, already absent, obstructed or
  retired. Completed work is a count of outcomes, never the next layout index;
  the selected CellId is explicit. A route cursor must not be the work ledger.
- The next eligible CellId supplies a work goal. The navigator selects a safe
  supported work position and a bounded local path. After observed arrival,
  the field owner checks the *current* crop/soil and exact actor hand before a
  one-time work effect. A crop removed by a player contributes zero yield.
- A foreign block occupying crop or support remains foreign. A typed
  obstruction observation marks only that CellId unavailable for this pass,
  with coordinates, cause and revision; the farmer continues to other reachable
  cells without walking through it or inventing wheat. Clearing it later may
  make it eligible for a later growth/repair cycle. An obstruction of a transit
  edge is distinct: attempt a bounded legal local replan; if none exists,
  retain a job-owned blocked goal with exact current station, target, cause and
  route/layout revision. COLD cannot consume the blocked edge merely because
  the HOT scene released. The block is cleared only by a new matching physical
  observation or an explicit valid route revision, never by a timer alone.
  A finite path/no-progress deadline establishes the blocked disposition;
  repeated path creation or changing error strings cannot reset that deadline.
  This condition is visible as `route blocked` for the local job, not a whole-
  site reconciliation conflict or an indefinitely false `harvesting active`.
- After the last eligible cell, the same actor carries the actual positive
  batch to an authorized depot service goal. Empty yield closes without a
  synthetic item. Physical hand/chest custody and durable receipt remain exact.
  A full hand pauses further yield until its part is delivered.
- The board/diagnostic projection distinguishes travelling, working,
  obstruction, awaiting evidence, delivery and completed outcomes. It cannot
  display `harvesting in progress` forever for an unreachable goal.

## Adoption and evidence

The existing retained per-cell route and custom `NoAI` actuator remain
historical implementation, not a permitted second long-term path. Migrate in
one connected farmer vertical: canonical goal/work separation; bounded HOT
Minecraft navigation; COLD goal travel; typed arrival/obstruction and replanning;
field/custody/restart continuity. Do not mark this design implemented because a
new interface exists or one route test passes. Other families adopt the same
contract only after this vertical proves it; no fleet-wide rewrite is implied
before that proof.

Minimum discriminators: ordinary multi-cell harvest and depot delivery; stone
replacing one crop and stone blocking only a transit support; no false yield or
whole-site conflict; clearance and later cycle; first ingress after several
COLD work steps, repeated HOT/COLD hand-offs, graceful and hard-crash recovery
at an in-flight effect; irregular/non-flat field and bounded path; no
observed teleport, no one-cell stop/start cadence, no unmanaged vanilla goal;
current worker/CellId, blocker and motion outcome visible in one causal trace.
Focused domain/adapter tests precede a relevant native scenario. Native success
is not M3/player acceptance and a document is not runtime proof.

## Connected migration of the farmer (implementation boundary)

At adoption, `ResourceSiteHarvestJob` persisted `TraversalTopology`,
`cropRouteCursors` and `traversalCursor`. Those fields were a movement
cache/history, not an acceptable permanent representation of the work goal.
The connected farmer migration preserves the CellId, hand and depot receipts:

1. Introduce a typed retained semantic goal derived from the next unaccounted
   CellId or depot service port, with job/worker, layout revision, capability,
   exact arrival contract and a bounded set of legal supported stations. It
   does not contain a path or choose the next task. The work cursor changes
   only on a typed crop outcome, never because a physical waypoint was crossed.
2. Retain the actor's current supported body and current goal at HOT/COLD
   transfer. COLD may compute a deterministic next known support toward that
   same goal and update the actor there at its scheduled travel cost; any
   route cache is derived and discardable, not a serialized work ledger.
   Unknown/invalidated geometry stays blocked or waits for observation; no
   fabricated loaded-world receipt.
3. HOT gives the goal's legal station set and bounded support envelope to
   Minecraft navigation. It may re-path locally but cannot run vanilla task
   AI, select work, award arrivals or leave the envelope. Record one observed
   goal arrival (plus physical work receipts), not one WAL event per route
   support. On demand loss, capture the same actual supported body and
   remaining goal before releasing the scene, including mid-travel; do not
   restart the goal from its original station.
4. Migrate all farmer reducers, admission/scene, diagnostics, WAL and snapshot
   together. Do not leave a second active traversal authority or a fallback
   that silently converts a goal into the old per-block corridor. Keep a
   deliberate format/version boundary for old disposable worlds.
5. Verify a multi-cell HOT field/depot journey, several COLD steps before
   first ingress, HOT→COLD→HOT mid-journey, local obstruction/clearance,
   zero-yield blocked cell, full-hand delivery, graceful/hard recovery and
   irregular terrain. Compare actor ID/body, CellId ledger, custody and
   physical scene, including smooth client motion; one focused green test or
   final-cell skip is not this acceptance.

This is a migration prescription, not an assertion that steps 1–5 already
exist. The earlier bounded Minecraft provider and native final-cell HOT skip
are useful evidence for the movement and obstruction seams only.

Implementation checkpoint (2026-09-25; source integration, **not** product
acceptance): the farmer's normal COLD scheduler now derives an ephemeral known
path from its retained actor body and emits a typed goal-step event, not one
work-cursor event per physical support. The HOT scene asks Minecraft navigation
to pursue the current legal goal station inside a bounded support envelope;
observed arrival is one goal event. Mid-journey HOT release retains the actual
supported body. A missing known COLD path becomes a durable local goal hold,
visible to diagnostics. For a blocked movement goal, HOT now probes a bounded
start-to-goal latitude before falling back to a long, thin known-route envelope;
mere path creation does not lift the block. Only an observed HOT goal arrival
atomically clears it, so releasing mid-detour cannot license COLD to cross
stale geometry. The command boundary no longer admits a new path-plan-only
clearance for a semantic movement block. New per-block HOT traversal commands
are also rejected; at that checkpoint historical route-clear and traversal
events still retained replay reducers. An alternate depot service port is accepted as a real return and as
the next batch's starting body. Crop work still requires its exact CellId and
physical receipt; HOT navigation itself awards no crop or yield. Preparation
and receipt now also require the actual canonical farmer body at that CellId's
legal work station; a stale transitional route cursor alone cannot authorize
either transition. A displaced-body negative regression passes.

Focused domain/codec/fixture and NeoForge unit tests pass for these seams;
focused native local-navigation (24/24, including a two-cell physical detour),
field-turns and harvest-support slices pass.
This does **not** prove a multi-cell HOT field-to-depot journey, repeated
HOT/COLD transfer, crash recovery, smooth client motion or player acceptance.
At this earlier checkpoint, `ResourceSiteHarvestJob` still serialized `TraversalTopology`,
`cropRouteCursors` and `traversalCursor`, and uses the cursor as a transitional
work gate. Legacy per-block reducers and cursor-based diagnostics remain.
The bounded rectangular latitude may be too large for a long diagonal goal;
that case deliberately falls back to known-route latitude and is not proof of
arbitrary local detours. Transit-support knowledge invalidation and TPS under
loaded multi-farmer motion still require evidence.
Consequently step 4 and the full connected migration are not complete; do not
promote this checkpoint to SA-09 or deploy it as accepted goal navigation.

Source checkpoint (2026-09-25, subsequent): `MovementOrder` is now a
provider-neutral bounded order carrying the declared owner, actor, goal
ordinal/revision, capability, arrival policy and legal stations. The farmer
adapts its existing CellId/depot goal to that order. COLD route selection uses
a shared pure pedestrian planner over the farmer's retained field knowledge;
the knowledge inputs no longer come from the legacy traversal compiler. HOT
passes the same order identity to the Minecraft provider so a changed order
cannot inherit an old physical control or retry deadline merely because the
coordinates happen to match. A typed bounded-route miss cannot disguise a
malformed retained terrain survey. Focused order/traversal tests and NeoForge
compilation pass. The order is derived, not a second durable authority; at this
earlier checkpoint the legacy job corridor/cursor and its work gate still existed. This was a
reusable navigation seam, not completion of steps 2–5 or RTS-style arbitrary
long-distance movement.

Latest source state (2026-09-27; **not player acceptance**): the active farmer
job no longer stores a route topology or waypoint cursor. The same retained
actor body and semantic CellId/depot goal drive COLD's known-geometry planner
and HOT's bounded Minecraft navigator. The HOT scene records a semantic goal
arrival or an interrupted actual support, not a per-block work advance. Old
work-target route failure may retarget to another reachable pending CellId;
the failed CellId remains pending, not harvested or skipped. A route failure
with no reachable alternative retains the typed local navigation block. Old
per-block traversal and route-renewal commands cannot mutate a new job;
the current snapshot/envelope schema deliberately rejects old test worlds.
Historic route classes and event registration remain for source/test history,
not as an alternate active movement authority. Focused domain, codec and
NeoForge tests and native local-navigation, field-turns and harvest-support
slices passed. Full field/depot and restart carriers, client smoothness,
obstruction/clearance and hard-crash recovery remain separate acceptance
obligations; no source-only pass proves them.
