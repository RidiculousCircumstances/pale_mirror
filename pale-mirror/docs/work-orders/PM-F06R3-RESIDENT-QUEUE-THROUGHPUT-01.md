# PM-F06R3 resident queue throughput and depot liveness

Status: active correction, 2026-09-30. Main agent is the sole executor under
`engineering-agent-protocol.md`; no subagents. This interrupts further resident
feature breadth. It does not change the Frontier v3 player promise or authorize
publication, a world reset, or a product-completion claim by itself.

## Observed contradiction

On the live disposable world `frontier-v3-parallel-meals-20260930`, one or two
residents visibly took bread while others stopped. Read-only diagnostics at
canonical instant ~90,078 reported 366 residents, about 1,055 retained due
actions and an oldest deferred action about 62,556 ticks late. Five Clearwater
residents retained meal `MOVE`; their COLD positions did not advance over more
than 2,000 ticks. The runtime was active, not globally quarantined. Ordinary
server advancement admits only one due action per tick, while hungry activity
reviews retry every 200 ticks and each COLD meal leg uses another 20-tick due
action. This is direct evidence of simulation-service debt; a physical-route
obstruction remains a separate unproven possibility until the debt is removed.

## Required design

1. Keep one deterministic canonical simulation and ordered engine-owned due
   queue. Measure due inflow, completions, oldest runnable age, owner/kind,
   server-thread cost and TPS. Bound the per-tick execution budget by an
   explicitly calibrated count/weight; no wall-clock-dependent canonical
   order, silent dropping, catch-up teleport or concealed backlog.
2. Replace routine per-resident polling for unchanged bread, service-access
   and route conditions with owner-addressed wakeups. The resident retains one
   exact waiting activity/meal; the source/container, service boundary or route
   change wakes eligible waiters in stable order. A bounded diagnostic fallback
   may detect a lost wake but must not recreate a population-wide 200-tick
   workload. Actual hunger thresholds remain exact scheduled facts.
3. In COLD, advance a resident's retained movement order over a legal known
   route by elapsed interval up to the next causal boundary. Persist the exact
   order/goal, start/checkpoint and authority epoch; derive the current body
   deterministically without WAL-writing every traversed block. Do not pass a
   competing due action, known obstruction, interaction, service boundary or
   mandatory station. HOT uses the same order and Minecraft navigation, with
   a witnessed handoff and no reset or teleport of an observed body.
   The existing 20-tick-per-support resident-meal continuation is not the
   target: it recalculates a full known route and commits a WAL transaction for
   each step, and late execution moves the next deadline later. Retain the
   planner for causal events but replace this travel granularity. Do not call
   a larger WAL batch or a raised action budget a substitute for the journey
   model. A meal issues a goal, consumes an arrival, and ends on confirmed
   bread consumption; clearance of the shared physical service boundary is
   separate, and return/next movement belongs to the shared navigator and
   the next activity, not to the eating effect.
4. The shared service point chooses one current turn at the physical access
   boundary. Several residents may travel concurrently; release wakes the next
   eligible waiter when the prior body actually clears. This is a reusable
   service-access policy, not a depot-only task scheduler or second resource
   owner. Player withdrawal, replenishment, obstruction and restart must not
   strand a retained meal or mint bread.
5. Existing overdue actions in a test world must have an explicit disposition.
   This project accepts fresh disposable worlds: if a semantics/schema change
   makes their continuation unsafe, fail closed and deploy a fresh world only
   after preserving the old one and running the normal deployment checks.

## Finite acceptance

- A focused deterministic test covers a many-resident hungry cohort, multiple
  concurrent approaches, one exact bread/claim per meal, service-boundary
  release, absent/replenished stock and a blocked route. No settled waiter
  continues creating frequent no-op WAL actions.
- A COLD→HOT→COLD/restart check shows the same resident, legal as-of body,
  destination, custody and due ordering; a player-visible visit shows several
  distinct residents take/consume bread and later leave the depot without
  forced chunk loading or a new task caused by arrival.
- On a fresh 12-settlement seed, queue depth and oldest runnable age remain
  bounded rather than rising monotonically, the next meal advances while the
  player is absent, and the TPS/transaction cost is reported alongside the
  exact scenario. A unit test is not a measured throughput claim; the full
  60-minute release profile remains a later cutover gate.

Implementation order is diagnostic baseline → event-driven waiting/service
turn → sparse COLD travel → calibrated budget and queue guard → fresh-world
end-to-end check. A coherent smaller increment may be deployed for diagnosis,
but must be labelled intermediate and cannot close this order.

## Implementation checkpoint, 2026-09-30

Static review found a second independent serialization defect. The shared
service selector treated every retained `MOVE` meal as occupying the depot
throat, even when its resident was still travelling, and treated a distant
baker/harvester applicant as a physical occupant. This directly contradicted
the intended boundary-only permit. The first source increment now counts only
the actual body inside the access boundary; bread reservations remain
independent and several residents can approach concurrently. A four-resident
kernel cohort has completed distinct COLD meals under a four-action budget;
focused depot/bakery tests exercise the revised access rule.

The same increment retains unchanged unavailable activity and HOT-owned meal
actions instead of writing no-op retry transactions, and evaluates a late
wake at its current canonical instant. This is **not yet** owner-addressed
event wakeup: the queue still re-evaluates held predicates while scanning due
actions. It also does not solve per-cell COLD movement or establish server TPS.
The present file store forces one immutable WAL file per transaction, so
raising the ordinary ceiling from one to four can increase synchronous I/O on
busy ticks. Treat that ceiling as a diagnostic candidate until measured on a
fresh disposable world; do not claim performance acceptance from the kernel
cohort alone.

## Remaining implementation sequence and decision gates

Implementation checkpoint (2026-09-30, source commit `27cf781f`): the first
sparse resident-meal adoption retains a bounded timed route in the new
schema-214 snapshot, schedules its causal segment arrival instead of every
support, projects a pre-arrival body for player demand, and atomically hands
that body to an ambient HOT lease. A HOT release requeues the interrupted
journey from its witnessed body. Separate side pockets and a committed short
entrance stop distant applicants from occupying the depot throat; the COLD
return segment first stops at the physical exit. A newly observed block delta
on a retained route now atomically checkpoints the as-of body, clears that
route and wakes its exact resident; an engine command test caught and closed
the explicit process-emission contract for this cross-owner event. A resident
waiting in a side pocket now holds the exact due continuation instead of
durably rescheduling a no-op every 200 ticks; the queue still evaluates its
readiness predicate, so this is not the final addressed-wakeup index. Both
unavailable activity and meal reviews use a one-tick due fence plus a hold,
so an actual source/service change can run on the next ordinary turn rather
than inheriting the old 200-tick retry latency; the hold itself writes no
transaction. Focused resident, bakery,
navigation and ambient-runtime/store tests pass. This is not yet a generic
movement owner, a complete owner-addressed wake index, known-obstacle
invalidation, calibrated live throughput, or player-visible/restart acceptance.
The existing meal record still retains `RETURN` after consumption; that
transitional coupling must be removed before claiming the activity/navigation
separation above is implemented. A focused two-resident test demonstrated why
simply completing the meal on first service exit is invalid: the former meal
return is presently the only COLD movement order that clears the exit cell;
removing it strands the first resident there and blocks the next visitor. The
proper replacement must atomically transfer movement ownership to a shared
post-service order with a real next destination, then retire the meal. Do not
deploy or close from this checkpoint.

Verification discipline: focused resident/activity/navigation/catalog and
ambient tests passed after this increment. A later attempt at the entire
Frontier+NeoForge Java suite was deliberately interrupted after it spent
minutes in unrelated settlement-assault/graybox scenarios with no relevant
failure. Treat that run as incomplete, not green; do not repeat it until a
release candidate or a failure points there.

1. **Readiness without full due-queue re-evaluation.** Keep the engine's
   stable total order, but index blocked resident actions by exact resident
   and the depot/service/route condition that holds them. A canonical stock,
   custody, scene or body transition invalidates only the relevant waiters;
   source selection and safe-yield are rechecked before admission. The index
   is derived/cache state, rebuilt from snapshot/WAL on restart, not a second
   canonical scheduler. Audit `ScheduledActionQueue`, the world process
   catalog, resident activity/meal processes, and the actual resource/player
   edit and lease-release events. A broad `state changed` invalidation is not
   an acceptable endpoint if it still scans hundreds of waiters per tick.
2. **Sparse exact COLD journey.** Extend the retained meal movement order with
   an explicit route/checkpoint time and authority epoch. Route interpolation
   must provide the legal as-of body at a player-demand/HOT boundary, while
   persistence records a segment or interaction boundary rather than each
   block. The route may not cross a service station, work interaction, known
   changed obstacle or competing due causal event without a checkpoint. Use
   the existing navigation/lease handoff; do not invent a parallel movement
   owner or teleport a loaded resident. Keep old test worlds only as evidence
   if the retained schema cannot continue safely; use a preserved/fresh-world
   deployment decision instead of silent migration.
   Verify both on-time and late scheduler admission: a delayed due action may
   create bounded service debt, but must not reduce the actor's declared travel
   rate or move the next travel deadline to `actual-run-time + one block`.
   Demand mid-route must choose the as-of body *before* a HOT lease is prepared;
   tests must cover an obstruction, competing due action, restart and a second
   COLD handoff after a HOT interruption.
3. **Calibrate the host ceiling.** Separate runnable lag from retained held
   waiters in diagnostics. Record admitted transactions per turn, due inflow,
   persistence p50/p95 and server-tick p95/TPS on a fresh seed. The budget of
   four stays provisional until this measurement; if four WAL file forces
   exceed the tick envelope, reduce event frequency or change the durable
   write shape rather than merely raising the cap again. No wall-clock-based
   canonical ordering or unbounded catch-up burst.
4. **Close at product level.** One focused HOT/COLD/restart check plus one
   player-demand visit must show several different residents obtain and eat
   bread, then leave the throat, while a farmer/baker can use the depot when
   actually free. Compare the same resident IDs and bread claims before/after;
   inspect queue age and TPS. Stop after one informative terminal run, not a
   repetition campaign.

## Addressed-wait source checkpoint, 2026-09-30

Source `3979c2df` implements a reconstructible engine-side runnable index.
Held activity reviews are parked by resident/depot when bread or a retained
meal is their exact blocker; side-pocket meal waits are parked only while no
HOT/scene/physical authority competes. Accepted state transitions wake the
affected keys after durable commit, retaining the original due ordering.
Unaddressable holds stay directly checked. A 1,200-tick derived-index audit
and fail-open invalidation limit the consequence of a missed key; they are not
a substitute for closing the remaining causal owner inventory. Queue metrics
now separate parked and unparked depth. Focused queue, resident activity/meal,
engine checkpoint/recovery tests and NeoForge compilation passed. No live TPS
or product result is claimed; the current server still runs the prior build.
Follow-up source `4e62ed91` reports an `auditReadyWithoutWake` lower-bound
counter in the performance diagnostic when an eligible waiter reappears only
at the bounded audit. Focused queue, recovery and diagnostic JSON tests passed.
Next implementation must close post-consumption movement ownership and route
invalidation before a candidate deployment, then calibrate the host budget on
one fresh disposable diagnostic world.
