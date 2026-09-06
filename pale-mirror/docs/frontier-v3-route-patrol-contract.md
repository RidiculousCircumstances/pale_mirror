# Frontier v3 route-patrol continuity contract

Status: accepted replacement contract for `MAT-004` and `V3-AUD-038`.
The current `RoutePatrol` waypoint/cursor record and its direct COLD actor-body
rewrites are an incomplete M0/M1 implementation. They are not a compatibility
surface. The implementation described here replaces them in one fresh-world
format cut; existing v3 worlds are recreated and their snapshot/WAL bytes are
rejected before hydration.

## Purpose and owner

A route patrol is a class-D operation, not ambient security flavour. One
persisted `RoutePatrol` owns exactly one strategic task, settlement, immutable
`RouteUnitManifest`, ordered roster, route-inspection target, command state and
all movement progress. `HumanAssignmentProjection` derives `ROUTE_PATROL` only
from this record; an ambient `GUARD`, `WORK` or settlement-anchor target can
never stand in for it.

The patrol record retains:

- an immutable, bounded, surveyed pedestrian `TraversalTopology` and one
  direction/cursor over its retained route edges;
- distinct exact `BodyPosition` formation slots for every living roster member;
- a bounded ingress/assembly topology and individual cursor for every member
  before the patrol may enter its route; and
- typed status and evidence for `ASSEMBLING`, `EN_ROUTE`, `ROUTE_CLEAR`,
  `OBSTRUCTION_CONFIRMED`, `BLOCKED`, `FAILED` and
  `UNKNOWN_AFTER_RESTART`.

When a failed cargo operation is the cause, the task additionally retains that
exact operation ID and the observed owned carriageway cell. Its inspection
topology is compiled only from that operation's persisted route; a nearby
settlement corridor may not confirm the loss on its behalf. A resident whose
current body is not on the declared home-ingress topology is ineligible for
this home-originating patrol. Admission tries another exact pair or blocks the
task visibly; it never snaps the stranded resident home or quarantines the
world.

While an active or failed operation retains such a loss, it is the sole
immediate inspection cause. Overlapping settlement corridors may remain
blocked, but their recurring reviews must not fan out generic patrols; the
causal patrol's exact-cell maintenance is the network repair owner.

The operation owns no cargo, alternate route, world repair authority or
unbounded navigator result. `PhysicalDelta` and Foundry observations remain
evidence of a changed retained edge; route maintenance or a separately admitted
bypass owns any explicitly admitted replan. A confirmed loss alone starts no
hidden bypass: the existing exact-cell maintenance owner handles repair.

## Exact continuity lifecycle

```text
task admitted with exact available security roster
  -> ASSEMBLING (each exact resident advances its own retained ingress cursor)
  -> EN_ROUTE (all members occupy distinct retained formation slots)
  -> COLD retained-edge advance OR naturally loaded ROUTE_PATROL HOT lease
  -> ROUTE_CLEAR / OBSTRUCTION_CONFIRMED / BLOCKED / FAILED
```

Admission compiles the one retained topology and the non-overlapping formation
before it creates the patrol. It fails visibly when it cannot assign all exact
members a valid distinct ingress or route slot. It may not place a resident at
the first waypoint, infer a flat Y coordinate, select a different available
guard later, or let a general ambient goal do the approach.

COLD advances only the next retained edge or a bounded sequence of retained
OPEN edges. Every accepted COLD transition translates the complete formation
through that same topology and updates the sole `ActorLocation` owner
atomically. It never jumps to a strategic waypoint, changes formation ordering,
skips a blocked edge or moves one member through another member's occupied body
slot.

## HOT scene

`ROUTE_PATROL` is a distinct typed `SceneCause` with one registered closed
`SceneBehavior`. Candidate selection requires natural player demand, the exact
active patrol, the retained cursor/formation and all living members at their
current canonical bodies. The durable scene lease contains the same member IDs
and body positions; it has no cargo anchor.

The registered physical provider moves only the next retained body cells using
ordinary Minecraft collision and navigation. An observed arrival may commit
exactly one next cursor and the corresponding translated formation. A member
death, changed/missing owned body, occupied next body cell, damaged support or
observed route obstruction produces typed evidence for this patrol and drains
or conflicts the same lease. It may not sidestep, teleport, spawn a substitute,
adopt a nearby Villager or choose a hidden alternate entrance.

While a patrol lease is HOT, its COLD action is suspended. On ordinary demand
loss it captures the unchanged exact formation/cursor and returns the same
patrol to COLD. On restart it becomes `UNKNOWN_AFTER_RESTART` until naturally
loaded exact tagged bodies and the retained next-edge/postcondition are
inspected. It never replays movement or assumes either success or casualties.

## Physical observation and Foundry

Foundry remains read-only. A patrol may use its retained topology or scoped
facility ingress report to diagnose `OPEN`, `BLOCKED` or `UNVERIFIED`; it does
not load chunks, clear a route, repair support or invent a bypass. A player,
explosion, fluid or infection change is admitted only through the normal typed
physical-observation boundary. The earliest affected retained edge is then
visible on the patrol/task route state and can cause the existing strategic
route reconsideration.

## Required evidence before MAT-004 closure

- pure admission, exclusive-roster, distinct-formation, COLD edge-bound,
  blocked-edge, death and snapshot/WAL recovery tests, including a non-flat
  topology;
- a focused `ROUTE_PATROL` Scene GameTest proving one exact observed HOT edge,
  no generic-ambient substitution, blocked next body/edge and owned-body loss;
- a graceful restart split while the scene is active, proving exact retained
  roster/cursor recovery or explicit `UNKNOWN_AFTER_RESTART`, never duplicate
  bodies or a replayed edge; and
- one disposable native scenario with ordinary player demand/action, terminal
  task assertion, correlation trace and a player-height in-progress frame.

Those establish M2. M3 remains separate: an unbriefed player must be able to
recognise a patrol's direction, roster, obstruction/clear result and available
intervention without relying on a debug board.
