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

The live settlement-11 depot stall adds one required movement discriminator:
the post-meal actor still occupied the service boundary while its route home
was unavailable and therefore blocked every waiting meal. Static review found
that meal, service-exit and bakery callers each constructed their own hard
obstacles; meal/exit treated every other COLD actor body as a wall, while field
used a different known-geometry provider. The accepted correction is the
`frontier-v3-goal-navigation.md` route-knowledge boundary: tasks declare goals
and authored passages; one provider determines known static/physical
passability without making other COLD pedestrians permanent walls; the
service coordinator alone serializes the throat. Verify a crowded exit and a
changed physical service cell, then check actual live service release and
multiple distinct meals. This source correction alone does not close the
resident order, field-specific geometry migration or HOT/player acceptance.

Source checkpoint `2b648ab4` implements the first settlement-provider slice:
meal, post-meal exit and bakery routes consume one immutable known-geometric
view and typed depot/workshop passage rather than adding actor body blocks in
their callers. The provider keeps observed physical deltas hard even at an
authored passage; the final short depot entrance is checked through it. A
crowd regression proves the actual `ActorMovementProcess` can begin and reach
the first boundary-clear segment, and a changed service cell remains blocked.
Focused resident/bakery tests, Java style/debt and NeoForge compilation passed.
Clean detached JAR SHA-512 `6d328e4d134de2c1dc5b43061aa4f4dac3dda5f80852601c6a9570706855babb9c94c898c6a4bf9c5d279538df569eb2a516d3fc3cea6cc002a2bc012518e44b`
was installed on the preserved fresh diagnostic world after save/normal stop;
preflight and startup verify passed. Live settlement-11 depot bread fell 59→54
while retained meals fell 31→27 and world status stayed `ok`; this resolves
the observed no-route blockade narrowly, not the full queue/TPS or player
criterion. Next measurement must assess sustained lag/TPS and exact meal
successors, then the relevant HOT/client visit. Field geometry remains a
separate provider and bakery/field movement is still process-owned.

Subsequent same-world observations reached canonical instant ~150,638 with
`status=ok`, ~2-tick current runnable lag and ~19.8 canonical TPS over a
194-second sample, not the sixty-minute 20-TPS cutover proof. Bread stock
emptied and later replenished to 61; resident `11-32` reduced hunger deficit
4→1 and cleared an intervening movement, while `11-1`, `11-5`, `11-20` and
`11-32` had new exact meal phases. The selected isolated native meal scenario
could not enter its client: the physical `DISPLAY=:0` is unavailable and the
runner correctly rejected task-private Xvfb `:95`. Its disposable server
stopped normally and the task-owned display was stopped. This is missing HOT
evidence, not an observed product failure; do not inflate the COLD/server
result into player acceptance. One targeted manual visit was requested.

Source-only follow-up `a3e68e6e` moves the farmer's raw field occupancy,
survey-support and authored depot-passage handling out of its work navigator
and into `ResourceSiteHarvestKnownGeometry.route`. The farmer still chooses
the exact typed work/depot goal; the field-specific overlay determines
passability and invokes the shared bounded pathfinder. Focused harvest and
process-SDK tests plus style/size/debt and NeoForge compilation pass. No
behavioral field claim or live artifact update follows from this refactor.
Follow-up source checkpoint `a49ce4c0` replaces the settlement-only provider
with `KnownPedestrianRouteKnowledge` for meal, exit, bakery and farmer COLD
routes. A caller declares an exact canonical facility plus `EXTERIOR` or
`PUBLIC_ACCESS` reach. The provider validates the facility against the same
typed port compiler used by `FrontierTraversalPlan`; existing hall, depot,
workshop and infirmary ports need no route-specific branch. The field uses a
typed canonical crop/soil overlay on that provider, and the active work path
no longer assembles its own block set. Generic path search is called only by
the shared provider. Crowd/forged-passage, four-port, changed depot cell,
field-route, resident, bakery and movement tests pass, as do style/size/debt
and NeoForge compilation. This is a reusable source boundary, not evidence
that every future area type or all movement owners are migrated. In particular,
a `PhysicalDelta` is changed-cell evidence without recorded final block state;
unresolved cells conservatively make a route unavailable but are not proven
solid obstacles. Do not infer exact post-state from the delta key. No build or
server deployment follows this checkpoint. Read-only live performance of the
older deployed artifact at instant ~164,932 stayed `ok` with
~2-tick runnable lag, but recurring 40–96 tick keep-up warnings remain; no
sustained TPS or HOT/player acceptance is claimed.

Live check of the shared-provider artifact `a49ce4c0` found an additional
real depot liveness defect, not a route-geometry failure. In preserved world
`frontier-v3-queue-fresh-20260930`, baker `11-3` waited at the depot on
`DEPOT_SERVICE_WAIT` and resident `11-12` held a meal inside the same physical
boundary. The meal admission rejected the baker's body, while work admission
rejected the meal body: symmetric denial with 30 other claimed meals waiting.
Commit `9d246294` makes the already-present meal win this overlap, matching
the work side's existing yield rule, and adds a direct two-body regression.
The focused meal, bakery HOT/COLD, movement and common-route tests pass.
Clean detached guardrails/assemble/package verification passed; the full
unrelated module suite was intentionally not awaited for this intermediate
diagnostic release. Exact JAR SHA-512
`d7100b234fc3066906f5124a4be9850d884a0c182c90e2abccc7d0fcd0381dd7f53691b7da50fd213acb1e37f0e820df68671121b066498d9acd3907c774e540`
was installed after save/normal stop on the same world; preflight and fresh
startup verification passed. Subsequent status at instant ~249,574 was `ok`;
bread fell 34→32, the baker moved from its previous boundary cell, and
resident `11-12` had a new meal claim. At ~252,387, active meals had dropped
31→16, stock 32→16 and `11-12` had completed that meal with hunger deficit
4→3; status remained `ok`/NORMAL. The baker still reported
`DEPOT_SERVICE_WAIT` from a position outside the depot. Treat the queue
progress as narrow evidence, not closure: bakery liveness, sustained
throughput/TPS, field delivery, HOT/player and restart acceptance remain open.
Avoid repeated full matrices without a product discriminator.

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

## Post-consumption movement source checkpoint, 2026-09-30

Source `064a2ad3` (local only, not deployed) closes the meal/movement ownership
split for the first depot provider. Confirmed bread consumption atomically
retires the meal, creates a separately scheduled `ActorMovement` with a
producer-declared typed `ServiceExit(settlement,depot)` context and retains the
same exact resident/body. The movement owner saves a bounded timed COLD route
under snapshot schema 215, checkpoints the first service-boundary exit,
hands off the as-of body to one ambient HOT lease, resumes from a witnessed HOT
release, and wakes resident activity only at movement arrival. The physical
service turn is based on the actual body, not the former meal phase. A known
physical delta intersecting the retained route atomically checkpoints its
as-of body and wakes its exact action; an engine command regression confirms
the cross-owner event contract. Old `CONSUME -> RETURN` creation is disabled;
fresh worlds are required for the changed schema, while legacy wire IDs remain
reserved.

Focused actor-movement/meal/depot/bakery/catalog and NeoForge directed-goal
tests pass, as do Frontier and NeoForge compilation, large-file and Java-style
checks. This is source-level and modeled recovery evidence, not a native HOT
visual or TPS claim. The aggregate `guardrails` command remains blocked by a
pre-existing architecture-debt ceiling mismatch in the untouched
`FrontierWorldRuntimeDefinition.java` (87 lines in both HEAD-before and current
source versus 80 configured); do not raise the ceiling to mask it. No new
server artifact/world was installed and no player acceptance was performed.
Next: audit competing due-event journey boundaries, resolve that pre-existing
gate separately, calibrate queue/write/TPS on one preserved/fresh-world diagnostic
run, then one coherent HOT/COLD/restart
and player-visible depot visit on the exact candidate. Do not infer F0.6R3
completion from this checkpoint.

## Post-meal owner-exclusion checkpoint, 2026-09-30

Source `cd8be538` (local only) closes a reachable owner overlap: after bread
consumption, the old meal is gone but the resident still has an active
post-service movement order. Work admissions, field/workshop scene candidates,
medical/migration selection and meal-start validation now respect that movement
owner. A retained baker can release the depot's physical access turn on first
exit while still being unavailable to bakery work until arrival. Focused
actor-movement, resident-meal and bakery vertical tests plus Frontier/NeoForge
compilation passed. The previously failing architecture-debt `guardrails`
check now passes after a behavior-preserving simplification of the runtime
composition overloads; its ceiling was not raised. This remains source-level
evidence, not a live TPS or player-visible result. Next is a single fresh-world
diagnostic host-budget/queue run followed by the exact HOT/COLD/restart visit.
