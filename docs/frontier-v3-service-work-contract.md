# Frontier v3 service-work contract

Status: accepted implementation contract for `MAT-003`; no service-work
implementation is claimed by this document.

## Purpose

`STRUCTURAL_REPAIR` and `DECONTAMINATION` currently prove an exact physical
endpoint only. They must become one reusable class-C human service-work
lifecycle before their endpoint executors may be called player-facing work.

The service-work aggregate is the sole owner of the work, while the existing
physical intent remains the sole owner of its irreversible material effect.
Neither an executor, an ambient role nor a strategic task may substitute for
the aggregate.

## Current scope and ownership

This contract introduces one `SettlementServiceWork` aggregate per active
settlement service task. It owns:

- one stable work ID, settlement, exact living qualified resident and an
  exclusive derived service assignment;
- one semantic facility and its declared port/stations, one exact input item,
  one target, and one existing physical intent ID;
- a bounded immutable pedestrian topology and current cursor from the
  resident's canonical surface to the declared service station;
- the persisted phase `PREPARED`, `APPROACH`, `WORKING`, `EFFECT_READY`,
  `BLOCKED`, `UNKNOWN_AFTER_RESTART` or terminal outcome, plus bounded work
  progress; and
- one typed `SERVICE_WORK` scene cause/lease while the work is HOT.

The initial human forms are only settlement structural repair and settlement
decontamination. Route maintenance remains its own already-implemented
engineering aggregate and is never admitted through this contract. Hive organ
regeneration is biological morphology and belongs to MAT-007; it must not
pretend that a Villager repaired a hive organ merely to reuse this API.

The canonical service-work state is owned by a named Frontier v3 aggregate,
not by `PhysicalIntent`, a scene lease, `HumanAssignmentProjection`, or a
NeoForge executor. The assignment is derived from that persisted work and
therefore excludes the resident from all conflicting civilian/tactical work.

## Lifecycle

1. A canonical planner admits exactly one eligible resident, intact facility,
   active exact input and valid target. It compiles the one bounded topology
   and creates the work plus the existing prepared endpoint intent atomically.
2. COLD advances only the work's next retained topology edge/cursor. It may
   not choose a new entrance, height, station, worker or target. A loaded
   obstruction is one typed block/conflict fact, never a detour.
3. Natural player demand may prepare a `SERVICE_WORK` lease with that same
   resident and cursor. HOT movement advances the same cursor only after
   observed arrival. The actor is a normal exact Villager, not a generated
   service entity.
4. At the retained station, the HOT executor performs bounded visible work and
   records persisted work progress. It may make the endpoint intent eligible
   only at `EFFECT_READY`.
5. The existing repair/decontamination executor remains the only
   durable-before-effect block/overlay and exact-item mutation. It consumes the
   same item, observes the same target, then emits its existing typed receipt.
6. Receipt, death, theft, facility/station loss, route obstruction and restart
   transition the same work. A terminal scene drains/releases its exact body;
   it never invents completion, a replacement worker, a refund, or an
   alternate physical target.

When the relevant space is not naturally loaded, COLD may retain/advance only
the persisted approach and non-irreversible work phase. The actual material
effect remains a durable pending intent until its target and exact input
surface can be observed naturally. This preserves background simulation
without authorizing a desired-state write into an unloaded or player-modified
world.

## Spatial and physical rules

Every target has a semantic service station supplied by the owning immutable
plan. A target block, structure centre, infection-cell origin or arbitrary
loaded pathfinding result is not a station. The topology uses typed support and
body values, satisfies Foundry throat/clearance rules, and remains bounded.
Foundry reports physical drift; it never clears the route, repairs the target
or alters canonical work.

The HOT executor tests only the next retained body for collision. A full block,
missing support, changed station, dead/missing exact body or stolen input is
visible `BLOCKED`/`CONFLICT` evidence. It may not use local pathfinding to
invent a sidestep. A player may freely destroy the facility, reagent, target or
worker; the observed consequence is authoritative.

## Persistence and recovery

The work record, traversal, cursor, phase, progress, exact input and target
are snapshot/WAL state. Service-work payloads and the scene cause use explicit
stable wire tags. This is a fresh-world format change: pre-cut snapshot/WAL
bytes fail before hydration, and affected worlds are explicitly recreated.

On restart, a lease becomes `UNKNOWN_AFTER_RESTART` until its exact naturally
loaded body and endpoint postcondition are inspected. The system cannot repeat
a RUNNING endpoint effect, infer a completed repair/decontamination, or close
the work just because its chunk is absent.

## Required evidence before MAT-003 closure

- pure admission/rejection/forged-cursor/worker-death/input-theft/station-loss
  and snapshot/WAL recovery tests;
- a service-scene GameTest for observed approach, visible in-progress work,
  one terminal effect, and loaded route/station obstruction without forced
  canonical chunk loading;
- a filesystem restart split retaining the exact work/cursor/progress before
  explicit UNKNOWN or postcondition recovery;
- one checked-in native scenario per form using ordinary player action,
  terminal canonical assertion, PMV3 correlation trace and clean
  player-height in-progress frame; and
- the complete critical-code gate. This establishes M2 only. A reusable
  player-comprehension grammar remains M3 evidence and is not implied by a
  board or endpoint frame.
