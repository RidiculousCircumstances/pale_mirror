# Frontier v3 resident life and resource-accounting contract

Status: accepted contract, 2026-09-28; engineering implementation passed its
automated and disposable-client acceptance on 2026-09-29. This is not a claim
that the current build is installed on the live server or has passed human
product acceptance. This contract supersedes the *provision* part of the earlier
exact-recipient provision/birth target; it does not weaken the exact household
and birth requirements.

2026-10-01 amendment: the bounded-satiety and activity-decoupling design below
supersedes the accumulated meal-deficit model and mandatory post-meal destination
barrier. It is accepted design, **not implemented or deployed**. The historical
acceptance record does not prove this amendment or close the live incident.

## One owner per fact

### Portable resources across activity changes — accepted 2026-10-02

Personal resource custody is independent of the current activity. A resident may
pause field or production work with carried resources, eat, then reconsider its
retained assignment from its actual position. Neither changing activity nor
eating transfers, destroys, deposits or recreates that cargo. Resource accounts
retain actor custody, economic ownership, lot quantities and work reservations.
An account's work-purpose identity is not ownership of the resident's hands.
This rule is resource-kind independent; grain is the first field consumer, not
a special exception. Ground dropping and a new hauling activity are out of scope.

Responsibilities:

- `ActorCarriedResources` reads personal custody and checks bounded carry
  admission. Multiple independent resource accounts are permitted; a food
  portion cannot overwrite another account.
- Registered work-owner carry capabilities declare the exact account and its
  physical presentation slot. The common activity coordinator does not inspect
  field cells, bakery phases, resource kinds or concrete cargo implementations.
- The work owner still fences an unfinished crop/transfer/other physical effect.
  A safely retained cargo account alone is not a reason to deny interruption.
- `ActorItemSlot` and the Minecraft storage adapter separate equipment hands
  from indexed personal inventory. Self-care portions currently use pocket 0;
  work cargo remains in its owner's declared slot. There is no slot selected
  implicitly from an item's kind. New consumers declare their storage explicitly.
- Meal allocation, witnessed take and consumption concern only the portion's
  account and claim. Confirmed consumption changes nutrition and retires that
  portion, not the suspended work's resources. Access clearance remains separate
  from consumption and optional subsequent movement.
- Passive cargo presentation preserves an exact canonical account across body
  ownership changes. Returning to a work scene rebinds a witnessed retained stack,
  rather than issuing a replacement because a different activity ran.

Physical pocket addresses retain actor ID, deterministic body ID and bounded
slot index, with explicit persistence tag 5. Hand addresses also name the exact
equipment hand: tag 4 is OFF, tag 6 is MAIN; no enum ordinal is persisted.
Current-body authority, slot
exclusivity and exact counts remain required. A plain matching item/count is not
by itself proof of a suspended cargo projection. Unsupported bodies or storage
are rejected, not silently mapped to a hand.

Acceptance: carried crop survives a pause, food take/consumption and recovery;
the same work job resumes through normal navigation; a different resource kind
uses the same custody/transfer API; physical inventory save/load retains cargo
and food independently. No reconnect may be required to wake eating after a
safe checkpoint or completed delivery. Existing test worlds with retained meals
using the old offhand representation require fresh-world adoption; do not add a
compatibility movement or a second active food-storage implementation.

This amendment supersedes the historical empty-hand-only checkpoint below.
Automated implementation evidence and live deployment/product acceptance must
still be reported separately.

- `FungibleResourceLedger` alone owns ordinary lot quantity, economic owner,
  custody account and active claims. A Minecraft chest slot or actor hand is an
  epoch-fenced physical binding, not another stock balance or permanent item ID.
- `HumanPopulation` owns each exact resident's need state and settlement
  membership. `ActorCondition` owns life and physical health, not a duplicate
  nutrition counter. A dead resident cannot start or continue a self-care
  action. Birth initializes one new need state; migration retains the person
  and need while changing the settlement policy that applies after arrival.
- A settlement owns the schedule *policy*. A resident activity coordinator
  selects exactly one current `WORK`, `EAT` or `IDLE` activity from the current
  schedule window, need and retained work assignment. An assignment is not an
  activity: its owning job retains progress and resource claims while paused.
  Personal self-care is execution under settlement policy, not a thirteenth
  strategic `DecisionAuthority` or a second source of strategic objectives.
- The need owner reports hunger; it does not issue a return-to-work route.
  A meal owner owns food source, custody and consumption stages but does not
  choose the subsequent activity or directly retarget the resident's HOT
  purpose. The activity owner performs that hand-off after completion, and the
  retained work owner supplies its current semantic movement goal. Both the
  depot journey and resumed work use the common navigation providers from the
  resident's actual retained body. Consumption changes satiety immediately.
  Clearing shared access is a separate physical safety obligation; reaching
  a home or old work position is never a prerequisite for another meal or for
  activity selection. Idle return movement is interruptible through its owner's
  safe hand-off, not by deleting an arbitrary live movement order. A resident
  still below the meal target consumes its retained portion or seeks another
  executable portion without a mandatory home trip. If no
  permitted meal can currently be allocated, hunger remains and self-care
  retries, but the resident may continue eligible work.
- `ScheduledAction` remains the only durable due-time/queue record. A need may
  retain its last evaluated instant and level for deterministic integration,
  but not a competing next-due queue. A scene and Minecraft AI may execute a
  current activity, never choose one or advance the need from appearance alone.
  A scheduled planner consumes only its eligible queue head. An asynchronous
  HOT observation instead cancels or reschedules its own retained continuation
  by exact ID in the same atomic transition; it never claims to consume a
  different queue head. Physical `CONSUME` confirms nutrition and bread loss;
  the first witnessed exit from shared access releases its turn. Optional later
  movement arrival is not a nutrition or activity-selection barrier.

## First ruleset and arbitration

### Temporary service buffers and idle destinations

External field intervention is not farmer yield: an observed removed crop or
an older live crop externally replaced with age-zero wheat closes that exact
cell for the current harvest, retains its actual bare/young physical state and
selects another outstanding cell. The held observation and SavedData witness
retain the same exact before/after/cause through recovery. Ordinary growth,
unchanged age-zero crops and ambiguous in-flight physical work are not external
harvest evidence. A conflicted farmer resumes only after the field family
inspects the complete current physical field, no pending effect/foreign change,
the same supported body, exact already bound hand quantity and unchanged
recovery epoch. This applies to a safe partial batch as well as a terminal batch;
it cannot replay harvested cells, mint stock or replace the worker. The canonical
recovery fence and physical carrier incarnation are independent epoch namespaces:
the adapter proves the carrier against its physical owner ledger and submits the
exact canonical recovery epoch for the inspected scene. Numerical equality of
these two counters is neither identity proof nor a recovery prerequisite.

Service access owns the derived request to free a temporary passage/waiting
surface. It does not assign a meal or choose a person's activity. The activity
owner admits optional turnover only for an idle living resident at an owner-safe
checkpoint, with no retained meal, movement or competing physical scene.
Geometry supplies a bounded two-dimensional known-support candidate area and
selects a reachable resting destination outside every temporary service buffer,
excluding living bodies, retained meal destinations and active movement goals.
The common goal navigator executes one explicit interruptible HOT/COLD order;
no teleport, compulsory home destination or second food/movement authority is
introduced. Unavailable support/path defers this local admission.

The depot is the first declared service provider, not the owner of this generic
geometry/turnover policy. The legacy apron strips remain recognized for already
retained targets; new temporary positions also use a population-derived bounded
2D area. Completed consumption owns nutrition immediately. Later turnover
neither consumes another portion nor changes whether the resident is fed.

The additive `frontier.actor_movement_started` payload reuses the current typed
movement encoding. Schema-220 state layout and existing payload semantics do
not change. Recovery accepts only the explicitly pinned pre/post descriptor
inventories (including the already accepted harvest-inspection additive upgrade);
unknown inventory, changed lifecycle grammar and reverse upgrade remain rejected.

Each settlement starts with a replaceable daily policy: `WORK` at ticks
`0..11999` and `FREE` at `12000..23999`. The settlement supplies the policy;
no player schedule editor is required in this cut. The first need is hunger.
The first graybox world must not bootstrap a circular starvation dependency:
settlement one starts with wheat for its bakery vertical, while the other
settlements retain finite, ledger-owned starter bread in their depots until
their first fields can mature, residents can eat, and ordinary work resumes.
This is ordinary visible stock, not a provision counter or invisible ration.
The default resident has bounded satiety, not a debt of missed meal units.
Metabolism lowers satiety to zero; additional time without food affects a
separate starvation condition, never negative satiety. Confirmed consumption
adds the food's nutritional value up to stomach capacity. The default rate,
capacity, eating thresholds, meal target and food values are persisted and
hashed ruleset values. Individual
effective rates may differ under the resident-characteristics contract below;
the fresh-world baseline currently varies by at most 100 permille from the
default, with the spread itself persisted and hashed in the ruleset. The
resident's chosen baseline is deterministic from exact identity and retained
in the profile; chunk load and restart never reroll it.
One global day index is not a valid substitute for each resident's elapsed
need progress. No full-population per-tick scan is allowed: schedule and need
threshold actions wake the exact affected residents. At equal due instant,
reevaluate the need before beginning the new work window.

## Resident characteristics and future changes

`HumanPopulation` owns one bounded, versioned characteristic state for each
exact resident. Its first physiological characteristic is a metabolism rate;
the default reproduces the rate above. This is distinct from learned
`ResidentSkill`/`HumanCapability`, profession, settlement schedule, current
activity and `ActorCondition` vitality. A profession may influence an explicitly
declared policy or modifier in a later feature, but cannot be silently inferred
as a permanent metabolic identity. Migration retains the same resident's
characteristics; birth initializes them from declared rules, not from the
current settlement's stock or a Minecraft entity instance. Bioforms have their
own physiology owner; this cut does not pretend that every actor eats bread.

The extensibility boundary is a closed, typed characteristic definition with
a stable non-reused wire tag, unit, valid range, default, combination rule and
declared consumers. The authoritative resident value is not a `Map<String,
Object>`, an NBT bag, or an unvalidated per-entity config. Ruleset values are
world-level defaults and limits; the per-resident base and any bounded,
source-identified active modifiers are canonical state. Each modifier has an
explicit origin and lifetime/disposition, so an illness, injury, treatment or
later equipment effect can be added or removed without overwriting the base.
The effective value is a deterministic bounded projection, not another mutable
source of truth. Only metabolism is populated in this cut; adding a second
characteristic requires its own definition and consumer, not a general-purpose
effect engine introduced speculatively.

An authorized characteristic change is one typed canonical transition. For a
rate change at instant `t`, first integrate this exact resident's hunger from
its retained last-evaluated instant through `t` using the *old* effective rate,
retaining fractional progress in fixed-point units; then apply the new base or
modifier, derive the next threshold instant and atomically replace this
resident's engine-owned scheduled action. This preserves elapsed progress,
prevents retroactive hunger, and keeps `ScheduledAction` as the sole durable
due-time authority. Use exact bounded arithmetic and stable ordering for
simultaneous changes. The same result must survive HOT/COLD hand-off and
snapshot/WAL recovery; a display-side or Minecraft attribute change is never
the canonical mutation. Death retires the need action; migration retains the
characteristic and schedules under the destination policy. Diagnostics expose
base, active modifier sources, effective rate, fractional need progress and
next due instant separately.

The existing whole-day `ResidentNutrition` fields and
`ResidentNeedIntegrated(previousDay, integratedDay)` event cannot represent a
mid-day rate change. Replace their current-format state and event together;
do not bolt a multiplier onto the day counter or retain it as a second need
clock. This representation may be implemented before the HOT meal path, but
must remain non-authoritative until physical eating, safe work yield and
legacy-provision retirement are ready for one atomic fresh-world adoption.

An actionable hunger need wins over ordinary work only when a permitted meal
can currently be allocated. Hunger alone never excludes a living resident
from work admission or holds a food-producing job while no meal is available;
the exact need continues to accrue and a bounded activity wake retries food.
An activity admission refusal is an explicit derived wait with its source,
container custody, service access, clearance or work-checkpoint reason and exact
causal owner dependencies. An unchanged refusal retains the original scheduled
action without a retry transaction or repeated route search each tick. Stock,
access/body, geometry or work-owner changes wake the applicable waiters; the
generic queue retains a bounded fallback audit for a missed signal. Waiting is a
reconstructible execution index, not a new persisted meal/activity state. Need
integration remains independent, ordinary work is not blocked merely by hunger,
and invalid authority remains a conflict/error rather than a retryable wait.
`WORK` and `FREE` are activity preferences, not execution prohibitions. Available
ordinary work outranks idle in both windows, with a lower priority in FREE;
executable hunger relief outranks work in either window. FREE alone neither
requests owner yield nor suspends delivery of retained cargo. New ordinary work
may be admitted in either window when no higher-priority activity owns the actor.
Only hunger and work/idle are populated now; sleep and recreation are not implied.
Battle, evacuation and urgent
care reach their first declared safe checkpoint before yielding. A worker with
an in-flight physical effect, occupied hands or unavailable route retains a
visible pending reason; the coordinator never drops its cargo, cancels its
job, overwrites an actor hand or teleports it to a food station. Every current
resident work family must expose a suspend/resume contract before this model
can be called active. Returning to work uses the same exact job and ordinary
goal navigation from the retained actor body.
The derived assignment kind names the actual work family, not one convenient
recipe: a generic production job is `PRODUCTION`, while a bakery-specific
safe-yield adapter may currently be its only admitted subcase. An unadapted
production or other work family retains an explicit owner safety hold rather
than being misclassified as a bakery failure or silently starting a meal.

`EAT` is one retained activity with exact resident, source, portion allocation,
stage and actor/custody epochs. The current schema-215 steps are `MOVE`, `TAKE`
and `CONSUME`; the old `RETURN` wire tag is not an active transition. The accepted
replacement adds explicit carrying/clearing to an eating place before consumption
and updates the fresh-world schema atomically. It must not silently reinterpret
old stage tags. The first food-source provider
is the settlement depot. It can later be replaced by a dining station or
personal food account without changing need or arbitration. Bread remains
economically settlement-owned while the resident holds it. HOT has an actual
resident approach, physical take and visible consumption; COLD advances the
same stages and custody without force-loading. Only one confirmed consumption
retires its exact portion and increases this resident's satiety once. Entering a chunk
projects the current stage and body; it never starts feeding or replays an
already completed meal.

### Shared depot access

The facility declares its real service surface, exterior approach and bounded
work stations. A pure service-access coordinator admits the exact current
resident meal, bakery job or harvest job from their existing durable owners;
it does not own food, work, a second queue or a second navigation path. A
waiting contender keeps its ordinary scheduled retry outside the throat.
Only one admitted task owns the exact socket at a time. A safely yielded worker
may use their own depot turn to eat, but another worker cannot enter during
that meal. A meal cannot take another worker's active depot turn. All owners
use the same HOT/COLD admission decision, and physical arrival alone cannot
grant an unadmitted transfer. The access turn and the owning task have distinct
lifetimes. After pickup, carrying food toward a safe eating place releases
access when the exact physical body first leaves the declared boundary;
consumption does not need to hold the source socket. There is no mandatory
post-consumption home journey. HOT local avoidance
need not land on a precomputed route cell. The next resident need not wait for
the first to reach home. After bread delivery the bakery job continues toward the
workshop exterior, but its depot turn ends on the same first witnessed exit.
HOT records an exact physical exit checkpoint and COLD records its simulated body
edge; both use the same read-only occupancy decision. An open scene outside a
service boundary does not hold that point merely because the job continues.
The job finalizes at the workshop exterior. A failed route waits locally,
without teleporting the worker or declaring the delivery unfinished. The
harvest side stations remain legal work targets; its depot approach waits for
the current service turn, and its later outbound field journey releases that
turn at the same witnessed boundary. This policy is a first depot provider of
a reusable access-point pattern, not a profession-specific hunger exception.
Every later shared service point must declare its own typed occupied surfaces
and apply the same separation of access release from task completion, with
owner-specific HOT/COLD witnesses but no second durable queue.

Service access uses observed occupancy, not an unmaterialized contender's COLD
endpoint. PREPARED residents do not hold a physical service turn. A resident
standing at the socket without its turn receives a bounded exit to the declared
waiting area. Admission may place a new body on a connected free waiting surface
instead of an occupied socket, retaining its exact identity, claim and epoch.
The body's confirmation updates canonical placement atomically; only the meal
owner may change an unbegun TAKE back to MOVE, and this awards no resource effect.
Actual admitted occupants outrank phantom endpoints. Supported physical
occupancy checkpoints wake the existing service contenders independently of
meal arrival or consumption. Shared placement and navigation never inspect meal
or bakery phases to award a domain outcome.

### Shared depot capacity

An active work owner reserves its exact future output slot through its retained
job, not a second mutable storage ledger. The common capacity view counts these
reservations alongside exact stacks, bound HOT stacks and packed COLD stock.
Fungible projection skips reserved addresses, and production, field and cargo
admission use the same view. A producer with no unreserved capacity retains its
work and waits for a normal stock or custody change instead of attempting an
invalid completion or quarantining the world. A reservation survives COLD/HOT
and restart with its owning job; it is released only by that job's exact
delivery, successor reservation or terminal disposition.

The field's block-projection claim and an already-accounted COLD harvest lot
have different owners. A farmer's exact actor account, job, scene/hand witness
and depot effect fence govern the lot when that worker first becomes HOT; the
field's physical cell writer may still be initializing or unloaded. Hand
projection and delivery must not manufacture a field claim or fail merely
because that unrelated block projection has not finished. Their own pending
witnesses survive restart and prevent a second hand or depot transfer.
Conversely, a visible field cell must still meet its own current-claim gate
before any new physical crop work; a carried lot is not permission to replay
the crop. A bakery receipt observes the complete depot layout, including
previously stored bread, and proves the newly prepared target slot within it.
An ambiguous or rejected post-effect receipt retains the baker, prepared step
and local container hold; it does not quarantine unrelated settlement work.

## Player depot boundary

Ordinary player edits to a PM-owned depot are permitted. The server's
controlled inventory interaction boundary records each actual departure as
a typed stock exit, regardless of whether a matching stack can be located in
a player inventory slot (including creative and multi-stack interactions).
It fences the pre-effect intent, observes the post-effect depot layout and
commits the exact ledger reduction, affected-claim disposition and dependent
work wake once. A player deposit is an explicit gift: after physical
confirmation, a new bounded lot owned by the settlement enters the same
ledger. Taking bread out ends PM accounting for those units; later return is
a new contribution, not proof that a historic stack survived. Theft mechanics
and per-item player tracking are outside this cut.

The adapter must preserve the familiar chest presentation but mediate all
supported click, drag, shift-click and creative transactions that mutate a
PM-owned depot. An unsupported or ambiguous mutation is rejected before effect
where possible, otherwise isolated at that depot with exact evidence. Prepared
transactions recover from the witnessed pre/post physical state; an
unclassified difference is never guessed into stock. A player departure
cannot leave a full canonical account behind in a physically emptied chest.

## Retirement and acceptance

Retire the old settlement-wide `SettlementProvisionProcess`, provision
nutrition counters, ration claim purpose, physical consumption executor and
`FOOD SERVING fulfilled/required` UI as active nutrition authorities in the
same fresh-world schema change. Rebase food reserve, export, production
admission and birth eligibility on current ledger stock plus exact resident
needs/claims. Do not preserve old test-world WAL compatibility or leave a
legacy executable path. Birth implementation remains its own later stage.

Acceptance requires: one exact resident works, becomes hungry, safely yields,
walks to the depot, takes and eats one bread, then resumes the same job; the
same account and stage survive HOT/COLD and a restart after take. An ordinary
player can remove several stacks in creative, free capacity, deposit bread,
and see stock, reservations, field/bakery admission and status converge
without a terminal container conflict. One unavailable source or changed
claimed bread remains an exact local wait/replan, not phantom eating. All
existing work families prove no progress while their member is eating and a
valid resume or explicit safety hold. Diagnostics expose the need level,
schedule window, assignment, activity, stage, stock/claims and wait reason.
The characteristic seam additionally proves two exact residents with different
rates, a mid-cycle rate change after nonzero fractional progress, modifier
removal, restart, death and migration without a second timer, retroactive
hunger or duplicate consumption. The first end-to-end meal may still use the
default rate; the differing-rate checks are focused canonical/recovery evidence,
not a second native campaign.
Use focused tests and one evidence-bearing native causal flow, followed by a
fresh disposable-world player check; do not run a confidence-only matrix per
edit. Preserve the old incident evidence before the authorized world reset.

## Clearwater diagnosis and bounded-satiety implementation plan — 2026-10-01

### Evidence and limits

Read-only diagnosis uses live checkpoint `9f979615`, world
`frontier-v3-body-observation-r9-20261001`, invocation
`2d3b2c2272644eb3b50ead37016a2822`. Uncommitted placement/occupancy repairs
in the implementation checkout are not installed. No restart, advance, world
edit, deployment or client campaign was performed for this diagnosis.

- At canonical ticks645252–647548, field epoch14 had64 completed cells,
  carried64, delivered0, no pending crop and no open harvest scene. This proves
  collection finished while delivery remained, not a permanently stuck job.
  The generic HARVESTING label obscures that distinction. The snapshot fell
  in the FREE schedule window. `ResourceSiteHarvestPlanning.planColdProgress`
  gates continuation through `ordinaryWorkPermitted`; HOT scene admission
  filters every candidate through `requestsYield`, including a cargo-bearing
  delivery. Existing HOT execution, conversely, excludes occupied hands from
  its safe-yield branch. This is a static admission/execution inconsistency.
  The exact historic first wait is not fully captured by these diagnostics;
  do not attribute it to a depot deadlock without its admission evidence.
- Live traces16:49:44–16:49:57 show the same job admitted, executed and
  released. At tick662227 epoch15 is GROWING, stage4, no active harvest job;
  the exact predecessor has a confirmed terminal physical receipt. Thus
  delivery eventually completed without intervention. Do not keep treating
  epoch14 as an unresolved permanent harvest failure. The next work owner
  should expose COLLECTING/DELIVERING/WAITING independently of field lifecycle.
- At tick661915 resident7-11 is STARVING, deficit5, mealNONE, movement held
  since639504, with305 bread in the depot. Earlier7-1 had deficit9. Sixteen
  residents remain STARVING at tick662227, while five are NOURISHED.
  `ResidentMealProcess` commits consumption independently, then installs
  `ActorMovementProcess.afterMeal`. Both meal admission and ordinary activity
  admission refuse a resident with any retained actor movement. Therefore an
  unsuccessful home journey prevents further food and work, despite the meal
  already being consumed. This is a real activity-policy defect, not a failure
  to count consumption or simply a display error.
- Live logs16:46:37 and16:56:40 identify intermediate navigation target
  `(115,63,14)`, support light_gray_concrete, feet gray_concrete, candidate=null.
  Read-only Minecraft block predicates confirm light_gray_concrete atY63 and
  gray_concrete atY64 in that column. The target places feet inside the road.
  `KnownPedestrianRouteKnowledge.forSettlement` obtains local settlement
  surfaces and natural terrain, but does not compose the current intersettlement
  road-network footprint. `FrontierGrayboxPlan.addRoutes` does project that
  footprint. Their geometry views can therefore disagree.
- `FrontierV3RouteNavigation` retains the same intermediate leg on BLOCKED;
  `FrontierV3ActorMovementNavigation` retains its route until lease/goal revision
  changes. Repeated requests may keep pursuing the same invalid hint. Advisory
  route geometry has become an unbounded obstacle to the final semantic goal.
  This explains the logged group at the road. The medic's different physical
  stall at `(135.30,64,5.85)` has only a repeated path-stalled witness, not this
  same buried-target proof. Record its exact selected leg/support before
  claiming the road-column fix closes every actor's physical stall.
- Deficit accrues to255 and one bread removes one unit. A long food outage
  creates a debt of many meals. This is the implemented model, not a bounded
  stomach. Correcting navigation alone would still leave repetitive feeding.

### Responsibility boundaries

| Owner | Owns | Must not own |
| --- | --- | --- |
| Nutrition domain in HumanPopulation | bounded satiety, last integration instant, fixed-point remainder, derived hunger category | paths, food claims, work or return-home policy |
| Resident characteristics | typed stomach capacity and metabolism base/modifiers | another nutrition balance or timer |
| Starvation condition in resident health | bounded severity and time-dependent recovery while nourishment is restored | missed-meal debt, direct navigation or a duplicate vitality counter |
| Food definition/provider | nutritional value and permitted available food sources | resident activity selection or stock balances |
| Meal process | allocation, approach, take, carry, consumption receipts and local wait/replan | profession checks, resumed job selection, mandatory return home |
| FungibleResourceLedger | quantities, economic ownership, custody and claims | need level, schedules or navigation |
| ResidentActivityCoordinator | priority from need, schedule and assignment; reevaluation after consumption | geometry, crop or recipe internals |
| ActorExecutionCoordinator + registered owner capabilities | exclusive actor authority and typed safe interruption/handoff | selecting meals or inspecting each profession's private state |
| ServiceAccessCoordinator + point geometry provider | entry and physical release of a shared point | nutrition, whole journey completion, another durable queue |
| Common navigation + geometry provider | actual goal movement, current traversable support, route hints and bounded replanning | meal outcome, field progress or permissions to use inventory |
| Field/bakery owners | job progress, cargo safety, current work goals and terminal delivery | hunger computation or personal return routes |

The starvation condition uses the existing canonical health relationship. It
does not introduce a parallel health engine: physical vitality remains solely
ActorCondition-owned, and any health consequence is an explicit ordered health
transition. Need integration emits an exposure/recovery fact; it cannot write
health through a hidden side effect.

### Model

1. Store satiety in fixed-point nutritional units, `0 <= current <= capacity`.
   Hunger status is derived from declared thresholds. Once zero is reached,
   elapsed starvation affects the separate condition, not negative satiety.
   Refeeding fills the stomach but does not instantly erase accumulated health
   damage. Existing per-resident metabolic variation is retained.
2. Integrate canonical elapsed time identically in HOT/COLD. Split the interval
   at zero-satiety and relevant physiological changes; integrate with the old
   rate before applying a new characteristic. Keep ScheduledAction as the only
   due-time authority; no per-tick full-population scan or second queue.
3. Start eating below the eating threshold and aim for a higher meal target
   (hysteresis), capped by capacity. Food has a typed nutrition value; bread is
   the only initial populated definition. Compute required units from the
   remaining target, bounded by available stock, carry capacity and allocation
   policy. Exact numerical balance belongs to the persisted ruleset and must
   be chosen explicitly during implementation, not inferred from old deficit.
4. Use approach -> take -> leave source access -> consume at a safe place.
   Reuse common navigation and transfer/custody boundaries. Eating a retained
   portion may involve several confirmed units without repeated home trips.
   Reassess satiety after each consumption, retaining/releasing unused food
   through the ledger. Source disappearance or partial allocation cannot
   fabricate nutrition. With no executable food, keep the need and retry;
   permitted work remains possible at the owner's safe checkpoint.
5. Consumption is the only nutrition effect. Arrival, taking, clearing access,
   finishing a movement or relogging never means the resident ate. Its exact
   receipt atomically retires the consumed quantity and changes satiety once.
   Reevaluate activity immediately after the portion completes or is exhausted.
   Clearing a service throat remains a safety obligation; an optional home
   trip outside that throat is an interruptible idle activity, not SELF_CARE.
6. A work owner with cargo declares a typed safe disposition before yielding.
   Scene admission and active execution consult that same owner capability.
   A generic scheduler must not decide whether wheat/bread may be abandoned,
   and FREE alone must not masquerade as a safe checkpoint for delivery cargo.

This is our bounded deterministic design inspired by RimWorld, not a claim to
copy its exact constants, job graph or health balance. Reference behaviour:
[food need and separate malnutrition](https://github.com/Chillu1/RimWorldDecompiled/blob/master/RimWorld/Need_Food.cs),
[food job selection](https://github.com/Chillu1/RimWorldDecompiled/blob/master/RimWorld/JobGiver_GetFood.cs),
[ingestion stages](https://github.com/Chillu1/RimWorldDecompiled/blob/master/RimWorld/JobDriver_Ingest.cs).
These are publicly decompiled game sources, not official API documentation.

### Implementation order and closure

1. Resolve the evidenced navigation disagreement in the common geometry
   provider: compose road/settlement/terrain supports with explicit overlap and
   revision rules. Invalid HOT intermediate hints must trigger bounded current
   geometry recovery/replan, never endless identical retries or teleport.
   Capture the medic's selected failing leg to decide whether it shares this
   cause. Do not add a medic-specific or depot-specific detour policy.
2. Remove the all-movement self-care barrier. Introduce typed owner capability
   for safe service clearance and interruption of optional idle movement;
   reevaluate from the actual body without double authority. Align cargo-bearing
   field admission/execution with the work owner's safe-yield contract.
3. Replace deficit state/events/ruleset with bounded satiety and separate health
   condition. Update every consumer: food reserves, allocation, activity,
   diagnostics, codecs and event registrations. Fresh test-world schema adoption,
   not backward compatibility or two simultaneously active hunger models.
4. Implement portion-based meals on the existing resource and common movement
   APIs; release access after pickup and physical clearance, not after the
   entire meal or home route. Remove mandatory afterMeal home movement from the
   active lifecycle and update truthful board phases and wait reasons.
5. Verify only affected causal boundaries: bounded long starvation/refeeding;
   one confirmed portion without replay across handoff/restart; failed optional
   return cannot prevent another meal; source access releases independently;
   composed road support and blocked-hint recovery; cargo delivery at a schedule
   boundary. Reuse existing coverage. One relevant ordinary-client scenario
   then checks concurrent approach, safe access, eating to target, actual-position
   work resumption and farmer delivery over HOT/COLD. No confidence-only matrix
   or new infrastructure campaign. Do not claim the live incident closed until
   its exact affected flows are observed on the changed build.

This plan neither changes the server nor authorizes deleting the current
incident world. Preserve its evidence before a later authorized fresh-world
adoption. Existing placement/occupancy WIP is a dependency to reconcile, not a
second competing service or movement implementation.

### Current implementation checkpoint — 2026-10-01

Main alone has wired the first interruption cut, not the full nutrition model.
Activity selection now depends on `ActivityInterruptionPlanner`; its composition
root binds the movement-owned strategy. A state-bound owner result separates
required service clearance from optional continuation. For an executable EAT
or retained WORK choice, an exact `ActorMovementInterrupted` receipt checkpoints
the body, removes the old movement and cancels its own schedule before the next
activity starts in the same engine transaction. The result cannot cross an
immutable state basis or resident boundary. HOT retarget is performed by activity
orchestration, not the movement policy; COLD captures its derived as-of body.
Food stock and actor changes both wake a held movement/activity reconsideration.
No higher-priority executable activity means the optional idle journey continues.

Cargo safety is now assessed by registered field/production owner checkpoints,
not by `ActorExecutionCoordinator.workYield` inspecting all actor accounts.
Harvest/bakery scene admission and existing HOT yield use the same owner-ready
policy. These owners currently require cargo disposition through their existing
delivery/load boundaries; arbitrary dropping and the complete interruption
resource-disposition protocol are not implemented by this checkpoint.

Known navigation composes current road-network, local authored floors and
surveyed terrain; field geometry uses that same base with its typed field overlay.
Where two solid authored floors share a column, the exposed upper floor is the
standable datum. The road cache retains one rebuildable current topology per
weak bootstrap, not another durable geometry authority. Actor route hints are
invalidated by topology/physical-delta changes. A blocked or stalled advisory
leg may fall back once to the final goal under the unchanged hard scope, with
ordinary native bounded querying and no force-loading or teleport.

Schema216 explicitly rejects earlier test-world execution grammar. Focused
canonical checks cover Clearwater's buried road support, food overriding an
unfinished home journey, denied interruption inside shared access, HOT authority
continuity, stale/replayed interruption and an actual engine transaction joining
interruption/cancellation/meal start. Affected existing meal, field, activity,
runtime and codec checks pass; Java compilation, architecture, file-size and
style gates pass. Source checkpoint `eef39f28` preserves that slice; no new
native/client acceptance, deployment or push is claimed. Multi-unit portions,
pre-consumption service release and remaining meal/activity responsibility
extraction are still required before the accepted feature is complete.

### Bounded-satiety source checkpoint — 2026-10-01

Source checkpoint `bc96b434` replaces the active missed-meal deficit,
including its event representation, selectors, reserve calculations, player
diagnostics and state codec. Production ruleset R8/schema10 explicitly selects
capacity1000, eating below668, meal target900, bread nutrition1000 and72 canonical
ticks per nutritional unit at metabolism1000. Hunger begins at23976 ticks from
fullness, preserving approximately the earlier one-day onset. Empty stomach
occurs at72000 ticks and never retains negative contents or a repayment remainder.
One confirmed bread is currently a full-stomach portion; this balance does not
claim implementation of variable-size retained portions or another food definition.

The next scheduled need boundary is hunger onset or zero satiety, not every
nutritional decrement. Empty residents retain a daily review seam for the pending
health integration. Food consumption recomputes the exact need wake; metabolism
edits integrate elapsed time at the previous rate. Hydration validates nutrition
against the selected world rules. Schema217 rejects the earlier deficit grammar;
future deployment needs a fresh disposable world, not an implicit migration.

Focused affected checks cover long starvation/refeeding without meal debt,
fractional/split integration, distinct rates and rate edits, exact food receipt
and current-state recovery, selectors, interruption and diagnostic output.
No new native acceptance or live behaviour is claimed. Variable portions,
leaving source access before eating and removal of the meal
owner's optional home-route creation remain planned work, not completed here.

### Independent starvation condition — 2026-10-01

The accepted minimal condition records severity and fractional exposure/recovery
in `ResidentHealth.starvation`, separately from disease and bounded satiety.
It imposes no speed, work, damage or death consequence. `ActorCondition` remains
the sole vitality owner. A dedicated `ResidentStarvationProcess` owns its exact
health fact and mutation. Need, meal and metabolism producers depend on the
`NutritionIntervalEffects` port; only `ResidentPhysiologyComposition` binds its
concrete owner. Nutrition supplies the elapsed interval and threshold
crossings, not navigation, food allocation or a health side effect.

The current persisted policy bounds severity at1000 units, gains one unit per240
canonical ticks at zero satiety, and recovers one unit per120 ticks while satiety
is at least668. Low positive satiety stops exposure but does not recover severity.
An interval is split at sufficient-nutrition and empty-stomach boundaries. Eating
restores nutrition only; the health condition recovers over subsequent fed time.
There is no independent due-time queue, population scan or HOT-specific clock.

Need reviews, confirmed HOT/COLD consumption and metabolism changes propose any
changed health fact before retiring that nutrition interval in the same engine
transaction. Unchanged conditions emit no health event. The fact retains exact
resident, nutrition predecessor instant and old/new condition; foreign, stale,
forged or repeated effects fail before mutation. Disease transitions preserve
starvation. Diagnostics calculate current severity read-only from the same
retained interval, and show both effective and stored severity. Neither arrival,
taking food, health integration nor a diagnostic query counts as eating.

Ruleset R9/schema11 and state schema218 explicitly adopt this representation;
earlier disposable test-world formats are rejected, not migrated. This is source
implementation and focused canonical/recovery evidence, not new live acceptance.
Variable portions, pre-consumption access clearance and remaining meal/activity
ownership extraction are still the next product work.

### Registered foods and retained portions — 2026-10-01

Following source WIP replaces the one-bread active meal grammar with an immutable
`FoodCatalog` in the hashed `ResidentLife` rules. Each registered item kind declares
nutrition per item and a bounded maximum portion. Production registers only
`minecraft:bread`, nutrition1000, maximum64; additional edibles require explicit
catalog data, not a new meal executor. Diets, preferences, spoilage and nutrient
composition are out of scope. Bread production/export policy remains a separate
bread-specific economy policy; this cut does not generalize every food board.

The read-only opportunity query combines as-of resident nutrition, registered food
definitions and available unclaimed stock. It deterministically chooses one food
kind and up to ceil((mealTarget-currentSatiety)/nutritionPerItem) whole items,
limited by stock and that food's portion maximum. Insufficient stock admits the
available positive portion; no stock does not make eating executable. One portion
may span several resource lots and physical slots. It does not mix food kinds.

`FoodPortion` retains item kind, nutritional value and the exact lot map in the
meal. Admission validates it against the current selected rules and opportunity;
the ledger reserves/transfers/retires that exact quantity. Catalog and nutrition
own no navigation or custody mutation. The resource ledger does not classify
edibles; the meal owner validates the catalog. Shared actor-item transfer executes
the portion, while the Minecraft adapter translates item IDs into real stacks.

HOT TAKE retains every selected source slot's before count and authority epoch,
not just the first slice. CONSUME retains the exact held quantity. Hand projection,
materialization/release and consumption all use the retained kind/count. Confirmed
consumption retires the portion once, adds its nutrition up to stomach capacity,
and atomically recomputes the same resident need wake. Neither pickup nor motion
counts as eating. Diagnostics expose meal kind, quantity and nutritional value.

Ruleset R10/schema12 and state219 select this grammar; older disposable worlds
are explicitly rejected. Focused current-format recovery and component checks
exercise the registered bread path, a test-only less-nutritious food, multi-lot/
multi-slot allocation, partial stock, exact receipts and duplicate rejection.
There is no new native/client acceptance or deployment claim. Leaving service
access before consumption and removing meal-owned optional home-route creation
remain the next accepted slice; this source step does not claim that lifecycle.

## Engineering acceptance record — 2026-09-29

The dirty implementation checkout at
`/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror` passed its final
affected verification: 999 frontier Java tests, the full NeoForge `build` and
packaged-JAR check, guardrails, and all 427 required NeoForge GameTests. Four
fresh disposable worlds used source-content SHA-256
`b99df413d0f3f3f563b23d68b6c3391d013e5bcd0d332200fabb84488c649aad`
and packaged JAR SHA-256
`6942b6f1d25bab55a8a8bd3bce00c0d289be88182b6863e562df1a2458517a3c`:

- `build/frontier-v3-scenarios/disposable-resident-worker-meal-1790623832691.json`:
  exact farmer hunger, safe yield, physical meal, same job and terminal field.
- `build/frontier-v3-scenarios/disposable-depot-player-multistack-exit-1790623982929.json`:
  ordinary-client two-stack gift and withdrawal, current ledger and chest.
- `build/frontier-v3-scenarios/disposable-resident-claimed-bread-exit-1790624056909.json`:
  player removal of claimed bread retires the claim without phantom eating.
- `build/frontier-v3-scenarios/disposable-resident-after-cold-take-restart-1790624128360.json`:
  retained COLD actor hand, graceful restart and single HOT consumption.

The source-level and focused tests cover different rates, fractional midcycle
edits, modifier removal, migration, death, safety holds, stock wakes and
reserve/admission. The old provision scheduler is unregistered, its reducers
fail closed, and fresh worlds contain no provision counter; static legacy
classes/codecs still exist, but have no active fresh-world nutrition path.
This record does not promote the candidate to a live-server or human-accepted
release. The live world, pack and server service were not changed.
