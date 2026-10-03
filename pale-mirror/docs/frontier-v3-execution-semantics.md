# Frontier v3 execution semantics and player guarantees

Status: accepted normative correction, amended 2026-09-18. Implementation and
evidence remain staged under F0 and ARC-001. This document does not certify
current code or close any audit finding.

The product contract owns the promise, `architecture.yml` owns boundaries and
invariants, and `frontier-v3-seamless-foundation.md` owns implementation order.
This document specifies their shared HOT/COLD acceptance semantics. Historical
implementation descriptions are not exceptions to these requirements.

## Player promise and limits

The world develops without the player. Settlements, the hive, people and
resources retain their identities and history. On approaching, the player sees
current activity and can intervene through ordinary Minecraft actions. Leaving,
returning and restarting do not themselves create resources, reset work or undo
confirmed consequences.

This promises continuity and causality, not an invisible tick-for-tick Minecraft
world. COLD does not simulate every collision, projectile or block update, read
unloaded chunks, run arbitrary unattended mod machinery, or advance while the
server is stopped. No-force-loading and normal Minecraft loading/ticking rules
remain binding. Graybox placeholders must still make real processes readable.

## 0. One world, independent execution dimensions

### Settlement management and operation commitments — accepted 2026-10-02

Settlement management owns goal arbitration, not actor motion, job stages or
nutrition. It uses the existing exact `DecisionAuthority`, strategic objectives,
tasks and operation owners; there is no parallel persisted order/worker queue.
The active implementation is main-alone in the continuity-named checkout.

`SettlementManagement` depends on registered `SettlementOperationPlanner` ports
and an immutable `SettlementManagementPolicy`. Profile planners expose bounded
owner-addressed offers with typed targets, priority and utility; requirements
come from `StrategicOperationSpecifications`, the same catalogue used to build
durable tasks. They may name exact pending tasks to replace, never an active
physical operation. Common arbitration checks owner authority and occupied
decision lanes without branching on farmer, baker, inventory kinds or routes.
Tie-breaking is deterministic and independent of provider invocation order.
A planning hold names its affected lane; route recovery may not globally stop
independent field work.

Current profile providers cover food production, ready-field work and preserve
the existing supply-recovery and containment proposal policies. No trade,
expansion or new combat implementation is implied by their extensibility.
Recurring review and existing stock/field/route event wakes use this contour.
An exact field opportunity uses the same management lane admission; it retains
its own field-specific readiness and delivery-capacity preconditions.

`SettlementWorkforce` owns shared deterministic candidate selection and checks
availability against `HumanAssignmentProjection`. The projection derives from
retained owners and remains the only work-assignment truth. A temporary meal,
FREE window or activity change does not release a retained work commitment.
The activity coordinator independently chooses the resident's immediate action.

`SettlementCommitmentAdmission` is an injected domain service with registered
facility-view ports, composed explicitly by `SettlementCommitmentComposition`.
Bakery and harvest start reducers require an exact live task/authority,
settlement-owned facility and available unique participants. Station admission
also requires an uncommitted station; area work instead reserves exact current
cell targets and does not exclusively commit the whole farm. Participant
admission alone never proves target/resource/capacity availability.
Owner reducers retain their recipe, field,
finance and physical-admission checks. Actual resource reservation/transfers stay
in the existing inventory/claim ledger; do not duplicate those balances inside
management. The engine atomically commits task/job/resource changes under the
existing command/event transaction; terminal owner outcomes retire objectives
and authority commitments through the existing lifecycle.

`settlement_management <settlement-id>` is read-only operator evidence: current
offers, priorities/requirements, lane admission, selected proposal, scoped holds,
pending replacements and retained commitment IDs. It derives the same policy
as execution and never commits a decision or claims a historical explanation.

Extension rule: add a concrete planner and owner execution capabilities in the
closed composition, declare typed requirements and facility/participant claims,
and reuse the shared admission/accounting and activity/navigation mechanisms.
Do not add family dispatch to the arbiter or a second resource/roster store.
The current two decision lanes remain the existing bounded policy, not a promise
of arbitrary simultaneous future armies, construction fronts or multiple farms.
New concurrent scopes require an explicit ownership/lane contract, not guessed
IDs or weakened exclusivity.

### Participant capabilities and shared execution coordination

One actor execution coordinator owns exclusive execution admission and the
common authority-transfer protocol. Activity selection remains with need and
schedule policy; job progress, inventory effects and safe-stop semantics remain
with each registered activity owner. Family-specific bakery/harvest stages must
not be inspected by the coordinator. Owners expose typed checkpoint strategies
under explicit assignment keys in a closed registry; missing, duplicate or
mismatched registrations fail closed. Results bind the exact assignment and
immutable state revision/authority context and cannot be reused after a change.
A ready checkpoint authorizes a transition attempt, not concurrent execution;
the request to interrupt is computed independently from need/schedule and a
feasible successor opportunity. It must not query an admission result that is
itself waiting for the current scene to release: that circular dependency would
prevent a hungry HOT worker from ever asking its owner to yield. Owner readiness
continues to fence unfinished physical effects; requesting food grants no meal
or simultaneous execution. Hunger without an executable food opportunity does
not make a food-producing worker abandon its task.
Schedule windows rank activities rather than granting or revoking execution:
ordinary work outranks idle in both WORK and FREE, at lower priority in FREE;
available food relief outranks work in either window. FREE alone does not request
yield, prohibit a new assignment or stop a retained cargo delivery. The minimal
population is hunger/work/idle; sleep or recreation must enter as actual competing
activities, not a fabricated idle obligation. Existing family checkpoints,
pending-effect fences and cargo custody still govern interruption.
The actual transfer closes the old authority and admits the new one atomically
using observed body evidence. Historical handoff coordinates do not determine
whether a resident may take new work. Recovery uses the same durable jobs,
orders and leases, not a duplicate activity queue or inferred owner.

HOT/COLD is shorthand for execution strategies over one canonical process.
For the smallest affected actor, resource, container or spatial front, distinguish:

| Dimension | Meaning | What it does not imply |
| --- | --- | --- |
| Presentation demand | Detailed current activity is required for an observer. | Permission to start work, grant custody or replay its history. |
| Physical interaction eligibility | Minecraft can currently affect the subject. | A viewer exists or the whole settlement must execute physically. |
| Execution authority | One current owner/epoch may advance the affected scope. | Ownership of another actor, container, process or strategic decision. |

The execution strategy follows these facts and the family's capabilities. An
off-screen active hopper can retain warehouse custody while an unrelated worker
advances in COLD. Absence of viewers never proves absence of physical authority.
No additional HOT/WARM/COLD state hierarchy is required to express this model.

An actor, facility and resource owner outlive the jobs and scenes referring to
them. A process owns its semantic progress; its HOT and COLD drivers provide
different evidence for the same allowed transitions. Scene infrastructure owns
only bounded execution coordination and lease lifecycle. Minecraft is a source
of real observations, including facts that invalidate the intended next step.

## 1. Shared semantics, different physical detail

Every family declares its comparison contract before implementation acceptance:

| Boundary | Required comparison |
| --- | --- |
| Identity and accounting | Exact actors, ownership, quantities, allocations, custody and once-only effects; no tolerance. |
| Process continuity | Exact goal, commitments, completed stages and retained unfinished work at hand-off; no second cursor. |
| Quiet production | Same labor norm, inputs and outputs under equal conditions; elapsed-time differences require declared bounded navigation/scheduling causes. |
| Travel | Same semantic destination, legal retained route/checkpoints and known obstacles; local physical trajectories need not match. |
| Combat | Same legal weapons, defense, tactical constraints and known terrain semantics; calibrated success, loss and duration distributions, not identical hits. |
| Recovery | Exact confirmed consequences and exclusive custody; explicitly classified in-flight and ambiguous effects. |

For a fixed canonical input stream, replay remains deterministic. HOT physics
supplies additional inputs, so full HOT/COLD world digests need not match.

COLD may integrate an interval of labor or travel up to the next causal boundary
instead of reproducing every Minecraft tick. It must preserve exact retained
work, legal topology/checkpoints and the reconstructible current position. It
may not cross a competing due action, interaction, ownership change, known
obstruction or mandatory stage without evaluating that event in canonical order.
Existing cell-level progress remains valid where the cell is a semantic unit;
this permission neither introduces a second cursor nor erases required cells.
The HOT navigation clock remains independent of semantic scheduling cadence:
ordinary movement continues until observed arrival, interruption or obstruction.

For ordinary off-screen travel, retain one canonical movement order with its
goal, bounded legal route/segment, departure or checkpoint instant, declared
travel rate and authority epoch. Derive the as-of supported body from that
order; schedule the earliest arrival or other causal boundary rather than a
durable action for each traversed support. A newly known obstruction or
competing action invalidates the affected interval before its consequence is
committed. Player demand must materialize the derived body at the hand-off
instant, not the departure body or an unobserved future arrival. Recovery
reconstructs the same order, due boundary and as-of position. This is a
movement execution policy, not permission to approximate identity, custody,
work outcomes or combat effects.

Activities issue typed movement goals and consume navigation outcomes; they
do not own locomotion or an automatic return journey. An activity completes
on its own confirmed effect (for eating, consumption), while shared service
access is held only for physical occupancy and released on witnessed clearance.
For meals, a supported observed position outside the service boundary permits
consumption of the retained portion. Reaching a preferred clearance/parking
destination is not an additional eating prerequisite. HOT and COLD use this same
boundary predicate; neither clearance nor arrival itself consumes food. Further
parking is independent activity/movement responsibility.
The next activity may issue its own goal from the actor's actual position.

Service exit is a bounded region of supported legal stations outside the
access boundary, not one retained parking coordinate. Geometry enumerates the
region; the common navigator selects a reachable station, with HOT additionally
checking current physical availability. Do not precompute a separate route for
every exit merely to enumerate HOT goals. Temporary waiting buffers are not
permanent IDLE parking: generic turnover can move an eligible idle actor outside
all nearby temporary service areas. Profession or ordinary GUARD presentation
does not by itself prohibit this yield; an actual assignment's registered
capability, pending effects and custody constraints still govern interruption.

An unloaded ambient body may release from its exact durable departure receipt,
including its observed position and health. The original admission anchor is
not an equality requirement for semantic-goal movement. The owner protocol must
still accept the release, unresolved physical food hands/effects must not be
silently discarded, and the exact inactive fence precedes canonical release.
Keep the unload receipt until exact successor adoption retires it; absence,
elapsed time or a last-observed cache is not release evidence.

Known pedestrian surface lookup uses a rebuildable immutable chunk index:
one sparse chunk directory and a direct 16x16 column table per present chunk.
The common ground provider composes road/local/natural support; activities do
not own terrain indices. Bootstrap/topology changes invalidate the relevant
cached view. The index changes lookup cost, not authority, geometry semantics,
physical collision checks or persistence/recovery guarantees.

A retained failed route is not an inventory-effect conflict or proof of a
permanent obstruction. The exclusive successor provider revalidates the current
goal against its current authoritative geometry. COLD may retire a bakery's
ROUTE_BLOCKED only while committing its exact validated successor step with no
HOT/scene authority or pending physical effect; planning alone never clears the
receipt. A known obstruction still prevents that step. HOT may clear the same
route block on witnessed arrival or successful current navigation. Source/hand,
machine, destination and ambiguous-effect blocks retain their separate physical
reconciliation requirements and are never erased by route retry.

Storage admission accounts for both present inventory and retained incoming
output commitments. Once a process removes its input from storage, its durable
owner supplies the kind, quantity and destination of the pending output; new
slot reservations and unrelated incoming shipments may not consume that space.
The shared storage budget does not inspect bakery phases, field progress or
recipes. Process-specific providers declare demands at the composition root;
the common algorithm only combines explicit owner/container/resource/quantity
values. The view is rebuilt from durable owner state, not a second mutable ledger.
Output delivery excludes only its exact declared owner's demand, then replaces
that commitment with actual stock in the same canonical transition. Foreign or
missing completion owners fail closed. Recipe admission may account for stored
input replacement rather than requiring an extra slot for an equal-size output.
Previously granted field slots and prepared physical writes are never silently
revoked to recover capacity. Pre-existing overcommit remains a visible hold:
recovery requires an ordinary resource withdrawal or an explicit owner-safe
capacity-release transition; no deletion, enlarged storage or fabricated receipt.

Container diagnostics distinguish canonical stock/capacity from physical stack
bindings. Missing bindings in COLD do not mean missing stock or an empty chest.
Report packed fungible slots, exact occupied slots, outstanding reservations
and remaining capacity from the same inventory budget used for admission. A
route diagnostic skipped by an authority/effect hold must say NOT_EVALUATED,
not report the absence of a checked obstruction.

### Canonical calendar and visible daylight

`SimInstant` is monotonic elapsed simulation time. Deadlines, hunger, travel and
work retain that authority; day/night is a derived calendar view, not a second
timer. The isolated pure `SimulationCalendar` maps the committed instant to a
day index and phase using the persisted ruleset's day length. Settlement
schedules may choose different work/free windows, but share that day length and
calendar origin (tick zero is dawn). Neither the calendar nor the schedule
advances the world or selects a resident's activity.

The isolated Minecraft calendar adapter receives an explicit target dimension,
calendar policy and read-only committed-instant source from the composition
root. Only the v3 graybox is bound in production. It installs one read-only
day-time view for both Minecraft level-data aliases, preserving all unrelated
world-data delegation. It projects phase into Minecraft's 24,000-tick day and
eight-day lunar cycle without changing native `gameTime`, Overworld time or
simulating skipped vanilla physics. The full canonical day index remains in
operator diagnostics; the bounded Minecraft value represents the lunar cycle,
not a second absolute elapsed-time counter.

After each canonical turn the adapter sends the reached phase only to players
in the bound dimension, with client autonomous interpolation disabled. Partial
fast-forward, held targets and quarantine cannot display the requested future
instant. Recovery derives the phase from the recovered canonical instant; no
separate calendar snapshot/WAL is needed. Detach restores the original data.
Vanilla sleep and `/time` cannot change this read-only calendar; sleep does not
skip simulation time. `doDaylightCycle` is not its clock owner. An eventual
sleep/time-control gameplay feature must request legal canonical advancement,
not bypass event ordering or rewrite deadlines. Unbound dimensions retain
ordinary Minecraft behavior. Ordinary day/night changes do not interrupt work
mid-effect: the activity coordinator still yields only at a safe checkpoint.

Carried resources are personal custody, not an execution lock. At an owner-declared
safe checkpoint, field/production work may yield while preserving cargo and its
claims. Registered carry providers declare presentation independently of the
activity; the common coordinator cannot branch on a concrete job or resource kind.
Self-care uses a separate explicitly addressed personal inventory slot. Its
witnessed consumption touches only its allocated food account, never suspended
work cargo. Re-admission observes and rebinds retained cargo without reissuing it.
Physical-effect fences remain non-interruptible until their exact receipt settles.
See the portable-resources amendment in `frontier-v3-resident-life-resource-contract.md`.

### Semantic movement and physical navigation

For goal-migrated pedestrians, the task's hard spatial/capability restrictions
are distinct from a preliminary known-route hint. A shared HOT route follower
owns local segmentation and Minecraft candidate acceptance, and only final
observed goal arrival is a semantic outcome. A loaded, dry candidate inside the
true hard scope may detour outside the hint's stripe. Its ephemeral corridor is
derived after acceptance and revalidated when the native path changes. Existing
topology/formation envelopes remain binding where explicitly required. See the
2026-10-01 amendment in `frontier-v3-goal-navigation.md`; the immutable-envelope
wording below does not authorize callers to freeze an advisory route stripe.
Physical-query bounds, unloaded nodes, forbidden scope and unsupported medium
must not be collapsed into an assertion of globally impossible travel. COLD
knowledge, checkpoint/fence and exact execution authority remain unchanged.

Canonical movement retains semantic intent, not a predicted Minecraft
micro-path. Its durable facts are the route/topology identity and revision,
current edge or station, cursor, capability, authority epoch and a typed arrival
contract. A semantic checkpoint names a port, workstation, bounded target region
or other domain station together with its support, clearance and permitted
movement-medium predicates. An exact feet cell is required only when the domain
interaction genuinely has one exact atomic station; it is not the default
meaning of travel arrival.

The registered HOT movement provider owns the ephemeral local trajectory and
ordinary collision/navigation within the hard restrictions of the current
semantic goal/edge. It may step, turn and take a legal bounded detour without
rewriting canonical intent, but it may not leave hard scope, choose another
strategic entrance, skip a cursor, teleport or silently acquire an undeclared
movement capability. Ground, swimming, flying and rail are distinct declared
capabilities. In particular, an ordinary ground route either proves a dry
supported passage or returns a typed blocked result; Vanilla entering water does
not retroactively grant swimming authority or change the checkpoint datum.

The provider reports a typed observation such as in-progress, arrived, blocked
or ambiguous with actual body pose, support, medium and cause. The canonical
cursor advances only from `ARRIVED` satisfying the retained arrival contract.
Raw equality between a predicted `BodyPosition` and a later physical pose is
neither a general arrival rule nor permission to fail a legal route merely
because Vanilla used another valid local cell. Conversely, radius/proximity
alone cannot prove support, clearance, medium or station eligibility. A blocked
or ambiguous result remains local, durable and explainable; it never becomes a
silent stationary body or a whole-world quarantine.

Physical pedestrian pose conversion has one NeoForge observation owner. A body
in actual contact with a naturally loaded dry collision support has nominal
`BodyPosition` immediately above that support, even when the feet are fractional
on farmland or slabs. Unsupported/fluid poses are explicitly distinguished;
they cannot satisfy a supported checkpoint by stale `getOnPos` alone. Physical
handoff, release, departure/rejoin and observation caches use the same conversion,
not independent floor-Y formulas. Entity-save evidence retains that observation
bound to the exact serialized pose; offline recovery validates the witness,
never infers support from a fractional Y or reads unloaded terrain. This is an
observation boundary, not another navigation, actor-position or work authority.

### Body admission is not task arrival

### Object lifecycle is independent of the interacting work (accepted 2026-10-02)

An object's autonomous lifecycle must not wait for the worker's job, transport,
meal or return to complete. Keep plant state, work accounting, resource custody
and actor navigation under distinct owners. A work outcome changes the object
through its explicit API; the worker does not become its biological clock.

For fields, `ResourceFieldGrowthProcess` owns one retained clock per prepared
site across GROWING, READY and HARVESTING. The existing calibrated stage cadence
advances each planted cell independently of that cell's batch-accounted flag.
Confirmed replanting joins the next shared biological boundary immediately;
this is a shared cadence, not an exact planting-time-plus-interval timer per cell.
Absent, obstructed or unknown crops do not grow. Pending player effects and
prepared farmer effects fence their exact cells; an unresolved external-world
site hold retains its existing causal boundary.

Accepted job progress and yielded resource custody do not rewind when an already
processed cell matures again. The cell's current-generation availability may
reopen at maturity; this creates neither wheat in hand nor another work receipt.
The job retains its own historical accounting, independently of those current
cell flags. Delivery advances the work epoch while preserving plant age and the
existing clock deadline. A new batch may target already mature plants without
resetting the whole field.

HOT projects current canonical plants and COLD uses the same growth owner.
A retained physical receipt proves its exact sowing/projection effect; further
canonical growth of that same plant does not invalidate the historical effect.
The actual recorded blocks and any paired hand observation still must match.
Only monotonic plant age is allowed, never soil/crop loss, foreign identity or
an unobserved effect. Closing an older receipt does not rewind the current plant.
Boards distinguish growing/ready plants from harvest collected but undelivered.

This changes plant-clock schedule identity. No old test-world queue migration or
implicit startup repair is introduced: use a fresh test world when deploying
this cut. Existing source/event byte grammar remains readable, but replay of
an old world's continuing simulation is not promised across this semantic cut.

### Current-state COLD/HOT presentation handoff (accepted 2026-10-02)

A naturally loaded physical chunk is not automatically ready to be shown or
to admit new HOT work. Completed COLD history is projected as its current result,
never replayed as physical work. Existing unresolved physical effects retain
their exact recovery owner; they cannot be replaced by a current-looking image.

The read-only handoff coordinator composes owner-supplied current-state checks
against one canonical revision. Field ownership proves current cell conditions,
epoch/layout and absence of pending observation/effect recovery. Resource
ownership proves the actual stock/provenance and pending transfer boundary.
It is not enough that an executor received a turn. Projection and recovery
remain with those existing owners, retain their budgets and never force-load.
The coordinator owns no field, food, inventory mutation or process completion.

Preparation delays initial client chunk transmission as well as new scene
admission. A classified conflict remains presentable for inspection/repair but
cannot authorize HOT execution; readiness and presentation are distinct results.
Packet batching/acknowledgement counts must include only actually sent chunks.
Deferred nearest chunks cannot starve independently ready neighbours. Current
presentation demand covers the player's tracking view; it is not permission
to perform interaction effects outside the ordinary physical-work radius.
The reference-container owner can prepare/reconcile an out-of-date image under
its existing exact projection fence in that naturally loaded view, even outside
block-ticking range. An unchanged current image creates no new HOT acquisition;
the ordinary drain/release protocol closes temporary projection custody. Merely
showing a chest is neither durable observer presence nor interaction authority.
An exactly retained, canonically acknowledged and physically matching obstacle
is current field state, not a reason to block every other available cell.
An immature plant is likewise not an actionable harvest destination. The field
owner prefers actionable cells and accounts an outstanding immature cell through
an exact no-effect exclusion, without approach, work gesture or yield. HOT
requires its current owned physical cell witness and no pending effect; COLD
requires released physical authority. Both retain the farmer's actual position
and carried resources, and can finish the batch into its ordinary delivery goal.
External plant growth (including bone meal) is an exact world observation, not
a canonical-clock prediction or permission to overwrite physical age. The field
interaction boundary first completes the selected cell's existing accepted growth
projection before vanilla reads its bone-meal predecessor. Unresolved cell work,
foreign ownership or world-change holds reject the action without consuming the
bone meal. A historically lagging physical claim may be reconciled only by a fresh
owned-world observation of strictly increasing plant age within the already
accepted canonical growth target, with unchanged farmland and no pending physical
effect. This acknowledges observed biology, not the cause of an old write; it
cannot change canonical age, batch accounting, inventory or yield. Age beyond the
accepted target still requires the ordinary exact external-growth receipt; loss,
replanting and foreign changes cannot use this recovery. A conflicted harvest's
prepared but physically untouched mature cell may resume only after inspection
of the complete current field with no pending physical witness, the exact worker
at that station and its unchanged bound pre-effect hand. A partially applied crop
or hand effect remains unresolved, never replayable under that inspection.
The field
adapter retains a fenced predecessor and declared higher age, observes the actual
successful block change, commits CROP_GROWN, then acknowledges the physical claim.
The existing bounded loaded-cell observer recovers a previously unrecognised
strictly forward age change against a current owned predecessor; unknown soil,
crop replacement and pending effects are not growth. Consecutive ordinary writes
confirm their exact receipts before the next write, not a later polling timeout.
Plant readiness creates the same registered harvest opportunity as clock growth.
CellId identifies the place, not every crop grown there: current cell
accounted/yielded availability and historical job progress are different facts.
Renewed maturity may reopen the cell for future work but cannot retrospectively
revoke or add yield to an already accepted operation. A retired physical work
witness is not revalidated against the plant's later accounted flag. An actually
pending effect still requires its exact cause, canonical receipt and physical
postcondition before retirement.
If scene admission conflicts before binding an already carried batch, its owner
may atomically restore that binding and resume the exact fenced scene only from
the retained actor/account/lot witness and fresh owned-body/hand inspection.
There must be no pending crop, delivery or hand-projection effect. Use the normal
exact hand-projection reducer within the recovery event; never issue another
stack or replace a foreign/stale binding. Type/count alone is not provenance.
No second plant, job, quantity or physical-custody authority is introduced.

Work amount belongs to the ruleset-owned WorkCatalog, keyed by resource kind and
explicit operation (initially wheat harvest, sow and till-and-sow). ResidentWorkStatistics
derives speed from the declared capability and source-identified multiplicative
ResidentWorkModifiers; it owns neither jobs nor hunger. Navigation owns travel,
not labour timing; retry intervals are not work duration. Exclusion consumes a
scheduler turn, not labour. WorkProgress retains fixed-point completed labour and
one bounded admitted interval, clipped at completion, daily-policy and need boundaries.
The field owner starts/settles that shared clock only at its selected CellId,
with identical HOT/COLD mathematics; physical crop effects require completed labour.
Meals, representation handoff and speed changes settle the old interval; travel,
pause and observer admission cannot earn work. Partial labour stays with that
operation, while selected-target loss/retarget cancels it without transfer to
another cell. Other cell loss preserves it. Saves/WAL record sparse transitions,
not per-tick samples. Speed edits wake the registered owner to reconsider its
continuation; shared coordination never inspects field/production stages.
This first consumer is farmer, not a claim that bakery/machines use this clock.
Schema221 and production-r11 deliberately require a new disposable test world;
no old-world compatibility is promised for this change.
Fresh scene admission rechecks current owners even when the physical chunk
remained loaded throughout demand loss. Recovery of an already retained effect
uses its existing authority and must not depend on that fresh-admission gate.
The opt-in `first_visibility` diagnostic reports owner, reason and proof revision.

At PREPARED ambient admission, an identity/custody-verified retained body that
has yielded execution to COLD is placed at the current declared supported
handoff zone, rather than walking from an obsolete saved pose or overwriting the
new canonical pose. Failed placement restores its original physical position
and support observation. An already HOT body is never reprojected by admission.
This awards no task arrival, crop yield, consumption or resource transfer.

Initial implementation scope is field/farmer/reference resource containers and
the existing ambient body boundary. No claim of adoption by every scene family
or of visual/live acceptance follows from compilation or owner component tests.

### Prepared-body receipt

`PREPARED` freezes the existing ambient authority handoff. The admission policy
declares an ordered, bounded placement zone: the retained body position first,
then explicitly permitted connected waiting surfaces. The physical provider
checks naturally loaded support, collision volume, foreign living bodies and
an actual navigable connection; it cannot force-load, choose a remote fallback,
overlap bodies or relocate a body already executing HOT.

Fresh ambient HOT admission requires a durable `AmbientBodyConfirmed` receipt
binding actor, current lease revision, exact predecessor body and observed
supported body. One reduction updates canonical position and the lease handoff
and activates HOT; forged, stale or out-of-zone evidence fails before mutation.
Placement never awards semantic arrival, consumption or resource transfer.
Registered activity capabilities alone decide how their retained stage resumes;
an unbegun meal TAKE admitted outside its socket retains its claim and returns
to MOVE. A pending physical effect or active consumption cannot be relocated.
Scene-reserved pre-lease bodies remain held for their declared handoff rather
than starting ambient locomotion during that transfer.

Physical service-occupancy checkpoints use the same receipt with an explicit
boundary purpose under the current HOT lease. They wake the existing point's
contenders without completing a meal or creating another queue. A PREPARED
actor with no admitted body cannot hold a phantom turn ahead of an actual HOT
occupant. A denied occupant can leave through a legal waiting route, never a
singleton wait in the socket; an idle occupant uses shared navigation to clear
the point. Service arbitration, placement, locomotion and activity effects have
separate owners. The implemented first placement-zone provider is resident
depot access; other ambient purposes remain explicitly exact-only where no
bounded alternative capability is registered. This is not adoption of every
scene family's admission protocol.

Process families consume one closed provider-neutral movement/result contract.
They may own different routes, formations and station semantics, but may not
embed their own coordinate comparison, Minecraft path clock or fallback route.
The same contract applies across agricultural work, service work, patrols,
expeditions and later tactical movement; capability-specific providers may
differ behind it.

For repeated work at local targets, the durable work plan and its exact outcomes
are distinct from the ephemeral physical micro-path. In particular, a field's
CellId work order is not permission to require a body to walk through every crop
cell or to persist a Minecraft node path as agricultural progress. The process
selects the next typed goal; the registered HOT pedestrian provider uses bounded
Minecraft navigation to reach a legal work position and reports an observed
result. COLD advances only the same semantic goal under its declared knowledge
policy. The accepted field-first adoption and blocked-cell policy are specified
in [`frontier-v3-goal-navigation.md`](frontier-v3-goal-navigation.md).

Statistical calibration never relaxes exact invariants within any individual
run. Compare like initial conditions, knowledge, policy/ruleset and providers.
Declare seed sets, sample sizes, metrics, tolerances and statistical decision
rule before measurement; retain failures, do not tune bounds to obtain green.

Neutral observation is different from a new physical input: a player blocking
a door, hopper transfer, fire or another live entity may legitimately alter an
outcome. Merely changing mode must not reset labor, stage timers, attack
cooldowns, wounds, target commitments or keyed random opportunities. Repeated
arrival/departure must confer no systematic advantage over declared calibration
bounds; one final endpoint comparison is insufficient proof.

Canonical recovery discriminators are part of that exact comparison. In
particular, a durable `PhysicalIntent` status and its observation binding are
not disposable HOT implementation detail: `RUNNING` asserts that a
non-replayable effect may have begun and changes restart reconciliation. A
traversal-only scene cannot use that status as a loaded-world readiness bit.
Until the owning effect capability is admitted, neutral acquisition,
checkpoint and release retain the same intent lifecycle as COLD; after it is
admitted, only the effect owner crosses the durable-before-effect boundary.
This restriction gates only the physical effect. A registered traversal HOT
driver still commits an observed due edge to the same retained cursor and
engine-owned continuation; otherwise HOT would merely move the representation
while COLD alone advanced canonical work.

## 2. Presentation and physical interaction are independent inputs

Presentation demand determines where detailed visible activity is required.
Physical interaction eligibility determines which objects can currently be
affected through Minecraft. The adapter reports both from naturally available
world state; player absence is not evidence that physical custody is absent.

A ticking chest/hopper, fluid, fire or mod mechanism may interact without a
spectator. The smallest affected container, actor or effect retains one fenced
physical owner while that interaction is possible. Its COLD counterpart cannot
spend or change the same resources concurrently. An actor can be COLD while its
depot has physical custody; a physically active depot does not make the entire
settlement HOT. No observer-independent mechanism may duplicate a scene lease.

Zero aggregate presentation demand plus hysteresis permits a release attempt,
not automatic release. Verify physical eligibility, checkpoint outstanding
effects and fence the old owner before COLD acquires the affected scope.
An unloaded serialized replica alone is not an active physical owner.

Container observation and handoff compose the registered effect owners'
pending-write queries. A prepared resident meal TAKE fences the same exact
container as bakery or field delivery; ordinary observers cannot classify its
unconfirmed physical result as external drift. Each owner supplies its query;
generic custody and observation code does not inspect owner-specific job stages.

An isolated reference surface may resume only through a durable
`ReferenceSurfaceVerified` receipt: fresh inspection of the owned, supported,
naturally loaded chest must match retained replica fingerprint/provenance and
exact replica and surface-recovery epochs. Pending effects, unfinished custody
recovery, actual replica conflicts and foreign recovery reasons reject it.
The atomic transition resumes only that surface and its recovery binding; it
does not change resources, grant custody or overwrite Minecraft slots.
With released custody, matching the retained physical predecessor is sufficient
even if COLD has since changed canonical stock. The existing fenced projection
protocol separately publishes that successor before physical consumers run.
Surface changes invalidate dependent waiting actions without reconnecting.

An already observed physical change can still have an unresolved canonical
cause after its cell unloads. For a resource field, a persisted physical
before/after witness must first install its exact WAL-backed site hold before
that site's COLD growth or harvest cursor advances. The hold is not a second
simulation authority: it retains the cause and suspends only the affected
site. Loaded-world evidence resolves the physical predecessor or successor;
the hold retires only after the corresponding canonical transition and physical
claim are confirmed. Other sites remain eligible for COLD progress. A scanner
or presentation demand must never be treated as proof that an unobserved
physical change cannot exist.

For an already owned field cell, an ordinary Minecraft block write that can
remove its crop or turn its support to dirt must durably retain the exact
predecessor and install that site's canonical hold **before** the write. A
bounded naturally-loaded scan is recovery/backstop, not the causal admission
barrier. A player/farmer/projection effect with its own pending owner must not
be reclassified as an external world change. A legacy whole-site explosion
stage witness must not inspect a cell-owned claim as though it had a uniform
stage; unsupported blast aftermath must remain visible as an explicit
observation/conflict obligation, never be silently treated as a healed field.

An exact actor uses the same separation. While its Minecraft body is naturally
loaded and interaction-eligible, that body and its current epoch are the sole
physical custodian even with no audience; the matching COLD driver cannot
advance concurrently. Pale Mirror may not retain a chunk merely to avoid this
handoff. A safe release atomically checkpoints the actor and owning process,
fences the live binding and retains one bounded inactive physical carrier keyed
by the canonical actor ID, stable Minecraft UUID and carrier revision. The
carrier contains only reconstruction/reconciliation evidence; it owns no
vitality, assignment, route, process cursor, schedule, inventory balance or
simulation authority. Once the live epoch is fenced, COLD may advance the same
canonical actor and process.

Natural return or restart must re-adopt or reconstruct that same actor and UUID
at the current canonical position and state under one newer physical epoch. It
must not select an otherwise eligible replacement, interpret a safely unloaded
body as a missing worker, or preserve both a live body and an inactive carrier
as current. Missing, duplicate, foreign, stale or revision-mismatched evidence
is a typed ambiguity at that actor/process boundary. Continuity requires the
same canonical person and physical UUID, not the same in-memory Java entity
object. Off-screen COLD travel may therefore place the returning actor at its
later canonical position; movement may never jump while that body is observed.

Native evidence and disposable cleanup are separate causal phases. A
non-restart lane first authenticates and freezes its terminal semantic
projection, then obtains the ordinary client-disconnect acknowledgement and
lets normal demand-loss handling reach a bounded safe checkpoint/release/fence
before requesting server shutdown. Those cleanup transitions cannot count as
scenario success or alter the frozen comparison. Restart and crash lanes do not
drain state that their recovery contract intentionally preserves. In every
case durable save, exact JVM exit and port closure remain observed lifecycle
facts rather than inferred consequences of a request or timeout.

## 3. COLD knowledge is explicit and bounded

Canonical authority is not omniscient AI knowledge. Distinguish known geometry
and observed changes, stale/inferred information, and unknown preconditions.
Each process-family descriptor declares evidence source, spatial scope,
revision/validity, invalidation, and its permitted conservative approximation.
Settlement and hive policies use their own perceived knowledge, not arbitrary
global state. A known broken bridge or player wall remains binding in COLD.

For an unknown significant precondition, use a bounded inspect/scout/replan
policy with a named evidence provider and retry budget. A pure COLD scout cannot
read an unloaded Minecraft region or convert elapsed time into exact evidence.
If fresh evidence is unavailable without loading, the family must explicitly
choose a permitted approximation, autonomous alternative, or visible abandonment
reason. Waiting for a visitor is not the universal autonomy strategy.

Approximation must record what was assumed. It cannot override a known change,
mint provisional production capacity into an irreversible dependent chain, or
assert exact damage through unknown shielding. A confirmed obstruction remains
until contrary evidence or an explicit canonical replan changes the route;
switching mode cannot erase it. Local navigation never invents a strategic route.

## 4. Projection, interaction and deferred aftermath

These responsibilities must be distinguishable at their owning interface and in
diagnostics, even where existing immutable value types or infrastructure are
reused. A generic queue cannot silently treat them as interchangeable:

| Responsibility | Retained meaning | Completion and supersession |
| --- | --- | --- |
| Current-state projection | Desired current revision and exact claim/preconditions for a subject or bounded footprint. | Idempotent observed reconciliation; an obsolete pending projection may be replaced by the current one only if no ownership, unresolved effect or causal constraint is lost. |
| Physical interaction | One identified transfer/effect with cause, subjects, expected revisions/epochs and actual confirmation boundary. | Confirm once, reject an unbegun stale action, or retain explicit ambiguity; never discard a possibly applied effect as obsolete presentation. |
| Deferred aftermath | Already justified consequence, event time, footprint, knowledge and realization preconditions. | Realize or reconcile current consequences without replaying the action; coalesce only when all consequential facts and provenance remain available. |

For example, three completed COLD harvests do not enqueue three physical harvest
animations. First visibility projects the current crop cycle, actor positions
and conserved resource custody. A loaded hand-to-chest transfer is instead a
physical interaction whose result needs confirmation. A destroyed bridge is
lasting aftermath even if the task that caused it has retired. Neither later
growth nor a new projection can erase that damage or a player's construction.

First visibility inspects the current physical baseline, resolves old bindings,
then admits the affected managed interaction. It does not make the player wait
for an unbounded history replay. Only a scope with unmet reconciliation or
readiness requirements remains locally unavailable; unrelated work continues.
This does not authorize an observed worker to teleport or duration work in a
loaded area to complete through an endpoint-only projection.

### Deferred aftermath preserves causality, not a replay

A COLD event commits only consequences justified by valid known facts and the
family's declared approximation. It retains a bounded, versioned, chunk-indexed
aftermath record with cause, event time, evidence provenance and preconditions.
Observation time is separate: late evidence is recorded now and references the
earlier cause; it does not backdate the canonical command stream.

On natural loading, inspect current world evidence before writing. Reconcile
current damage/crater/infection before admitting managed interactions; never
replay an old projectile or explosion. No player/settlement/hive protected zones
are introduced, but an old footprint is not blanket permission to destroy
unknown blocks. Missing evidence that a wall postdates a blast is not evidence
that the blast could penetrate that wall.

Unknown shielding must be handled before claiming an exact casualty behind it.
Constructive capacity remains unavailable to irreversible dependent work until
its required validity is established. Late contradiction isolates/replans the
smallest affected footprint or process; it does not rewrite confirmed unrelated
history or silently overwrite the player's construction. First-visibility work
is bounded; unresolved evidence blocks only its affected managed interaction,
not all world progress or all player movement.

Production-seam acceptance begins before either physical owner has manufactured
the expected claim. It advances the complete closed physical registry used by
server lifecycle against the real loaded-world ledger. Calling projection and
aftermath directly, pairing them in a test-only helper or substituting an
in-memory physical adapter can prove their local algorithms, but cannot prove
registry ordering, intervening-owner safety or SavedData recovery. The HOT side
is a separate required carrier through ordinary scene admission, body
selection, durable intent, physical observation, confirmation, drain/release
and the next COLD epoch; comparing generated IDs or manually installing any of
those intermediate states is insufficient.

These facts may be composed across explicitly bounded evidence tiers. A focused
GameTest can establish the exact lifecycle-called registry turn against a real
`ServerLevel`, SavedData and file-backed runtime without claiming that its
template load is natural player demand. A separately reviewed declarative
native carrier must join that turn to the dedicated dimension, ordinary player
demand, world-root store and actual process restart. Neither receipt is promoted
beyond its tier; together they cover the production seam without inventing a
fake `MinecraftServer` or requiring a client-only history from a unit test.

## 5. Scene boundaries are not gameplay boundaries

Scenes are bounded execution leases, not isolated arenas. Projectile flight,
blast, pursuit, cargo transfer and reinforcement can cross fronts and HOT/COLD
boundaries. Disjoint custody does not prohibit causal interaction.

Use one canonical ordered interaction protocol with an exact cause ID, source
and target owners, expected revisions/authority epochs and bounded affected
scope. A registered interaction coordinator acquires or hands off the required
authority and records each consequence once. It is not a second simulation,
global scene lock or unbounded search. Retry, stale epoch, interrupted hand-off
and reversed observation arrival must not duplicate or suppress an effect.

An unavailable target uses its declared knowledge/aftermath policy, not a
second COLD hit in addition to the same physical hit. Transfers preserve exact
actors, cargo, pursuit intent, progress, health, cooldowns and random-stream
position. Tests exercise both directions, mixed modes and restart at the
boundary. Existing first-class AI and distinct tactical/individual policies
remain canonical; the coordinator supplies no strategic purpose of its own.

## 6. Confirmation and crash recovery have an explicit boundary

Distinguish prepared intent, physically observed result, recoverably confirmed
consequence and ambiguous crash window. A flushed intent establishes authority
to attempt an effect, not proof that the effect occurred or was saved everywhere.
Confirmation requires recoverable evidence across every participating canonical
and physical custody owner. WAL, chunk NBT and player inventories are not one
atomic transaction merely because a receipt or fence exists.

Every irreversible family specifies its actual save/acknowledgement boundary,
idempotent postcondition inspection and reconciliation protocol. Do not blindly
replay effects or roll back arbitrary external/player inventories from a WAL.
Only explicitly reversible, unconfirmed pose/checkpoint state may roll back
after a hard crash; ordinary save/restart retains process continuity. If the
provider cannot support a requested confirmation guarantee, keep that boundary
unproven and change the protocol, not the definition of a successful test.

Ambiguous custody isolates the smallest asset and prevents dependent spending
or duplication. Inspection/retry/escalation has a bounded policy and visible
reason; unrelated actors and processes continue. If evidence cannot be obtained
autonomously, do not pretend the asset is resolved: retain local uncertainty
and a declared recovery path without making the whole operation/world wait.

Crash tests cover both sides of intent persistence, physical application,
physical/player save and canonical confirmation, including partial transfers
and late stale bindings. Graceful restart alone is not hard-crash evidence.

## 7. Lifecycle retirement and temporal referential integrity

Distinguish stable entity identity, process generation, job/action identity and
physical authority epoch. A new harvest generation is not a new field or person.
Identifiers and revisions are retained values, not facts reconstructed from a
nearby actor, current site cycle or projection label.

The same rule applies to semantic owner, durable type and capability identity.
The authoritative producer must carry each as an explicit typed value through
commands/events, physical intents, snapshots/WAL and recovery. Common ingress
may resolve the value's own stable wire tag and validate a declared compatibility
relation; it may not infer the value from another enum/kind, ID prefix,
coordinates, runtime class, collection membership, neighbouring record or
fallback default. Absence, unknown values and mismatches fail closed before
dispatch, replay or mutation. Test fixtures may spell the value concisely but
cannot expose an ownerless production construction path. Read-only projections
may derive presentation labels only when those labels cannot drive behavior.

This is a strict no-inference boundary. An authoritative record reaches its
first admissible production boundary with the complete nominal owner, kind and
schema already stamped by its producer. A generic ID, nullable or wildcard
type, context-polymorphic role, partially typed carrier, or an `UNKNOWN` value
that can later be promoted is not an intermediate representation. A closed
registry validates an exact declared tuple and completeness; it never searches
for a compatible owner/type, selects a sole candidate, or fills a dimension
from payload shape, another tag or current world state. Persisted data from an
older schema that lacks the declaration is rejected by an explicit version
boundary rather than inferred during hydration. Deterministic inference is
still inference and remains forbidden.

For physical change, aftermath and repair, the retained value is a typed
discriminated fact rather than a generic subject ID whose kind is rediscovered.
Its authoritative producer stamps the exact subject plus semantic target type,
and every persistence or continuation boundary preserves both. An explicit
unclassified variant has no repair/dispatch authority. Prefix tests, registry
membership, semantic-part coincidence or neighbouring plan state may validate
an already declared target, but may not select structure-versus-organ, route,
worksite, actor or any other behavior. A mismatch is a local visible conflict;
missing, stale or unknown durable identity fails before mutation or replay.

An owning domain transition that completes, cancels, retires or replaces a
subject must resolve every affected durable relationship, resource commitment,
scheduled continuation and physical operation in the same canonical transaction.
It can retain the subject, transfer a valid obligation explicitly, cancel an
unbegun operation, or retain a bounded terminal/recovery disposition. It cannot
publish a revision containing an unexplained dangling reference. The scheduler
remains the sole owner of due-time/order; retirement coordinates its changes
without copying the queue into the process or lease.

Calling an owner-local policy and then re-entering a common concrete-family
switch is still central dispatch, not delegated ownership. Confirmation,
conflict and recovery-unknown reducers belong to the declared family capability,
which performs its domain side effects and obligation accounting as one owned
transition. Shared state code may implement only family-neutral validation and
typed intent/observation/fence storage; it cannot branch on an intent kind,
observation subtype, identifier or state membership to select domain behavior.

The family also supplies an executable transaction-local retirement account.
It binds the exact affected relationships, schedule/continuation IDs, intent,
lease or carrier, resource commitments and late/recovery disposition, or states
checked-none for an inapplicable dimension. This is neither durable duplicate
state nor a second scheduler: generic orchestration validates it against the
authoritative pre-state, the complete proposed domain and schedule effects and
the resulting post-state. The engine alone mutates its queue. An absent,
duplicate or mismatched account prevents transaction publication.

That account is enforced by one family-neutral reference-closure barrier at the
canonical transaction boundary. For every created or changed durable reference,
the proposed post-state must contain its exact live typed target or its exact
generation-bound terminal/recovery disposition. For every removed, replaced or
terminal subject, the complete actual incoming-obligation set in the pre-state
must equal the set retained, transferred, cancelled or dispositioned by the
same transaction. Validation is based on the declared closed relationship and
obligation inventory; a family cannot make an unregistered ID-bearing field
authoritative or opt out through a special removal path. The implementation may
use a revision-bound derived reverse index and changed-set validation to stay
bounded, but that index is rebuildable and never becomes a second authority.

The barrier rejects an invalid transition before its revision, events, schedule
changes or physical intents are published. It yields an owner-local incident for
a bad command or planned transition. Whole-runtime quarantine is reserved for a
dangling or contradictory reference already present in accepted canonical or
persisted state, because that is integrity corruption rather than an ordinary
process failure. Hydration and ordered WAL recovery run the complete equivalent
audit before independent execution resumes. Thus a missing family-specific
cleanup cannot manufacture a committed dangling reference, while corruption is
still never hidden as a no-op.

Expected late input consumes the retained terminal disposition or fencing proof
and never replays completed work. A potentially applied physical action remains
subject to section 6 even if its originating job ended. Unknown or contradictory
references fail closed; a tombstone is not permission to ignore corruption.

Terminal retention is governed by incoming obligations and the recovery/replay
boundary, with declared count/byte bounds. Age alone cannot evict the only
evidence needed to reject a late effect; neither can every historical cycle be
retained forever. Compact into sufficient causal/fencing summaries after all
dependent obligations are resolved. At capacity, use a visible bounded admission
or recovery disposition rather than losing evidence or growing without limit.
The relationship contract owns the endpoint/lifecycle rules.

## 8. Architectural acceptance stories

ARC-001 maps these stories to existing evidence and actual remaining gaps;
they are coverage requirements, not seven mandatory new native runs:

1. Repeated COLD cycles followed by first arrival show the current field,
   worker and stock without a queue of historical physical actions.
2. An unobserved physically active container and COLD work preserve exclusive
   custody and exact quantities.
3. Departure, job succession and return preserve the same actor and semantic
   progress while allowing continuous HOT movement.
4. Cycle retirement, restart and late input yield a classified disposition
   without an orphan reference or repeated consequence.
5. A player's change to a previously materialized subject enters causality
   and is not overwritten by a newer desired revision.
6. A crash between physical application and saves retains confirmed or
   explicitly ambiguous custody and blocks only its established affected scope.
7. Repeated cycles and concurrent arrivals keep work queues, retained history
   and first-visibility cost within declared bounds with fair progress.

Pure tests can exercise many cycles in logical time; waiting for wall time is
not the lifecycle oracle. Native checks cover only relevant Minecraft seams
and retained real incidents. Existing M0-M3, recovery and release gates retain
their meaning; documentation acceptance never promotes runtime evidence.

## Implementation and evidence activation

The original F0.VA activation instruction has been consumed by the retained
foundation programme. Current assignment comes only from the canonical ledger.
The 2026-09-18 amendment is adopted through ARC-001 after the current F0.6R3
incident correction; it does not interrupt that correction or restart completed
F0 infrastructure. The hardening pipeline owns this next-stage ordering.

ARC-001 records a short family-to-requirement gap map: implemented and proved,
implemented but unproved, repair-needed or explicitly future scope. Attach an
owning boundary, comparator and negative/recovery case to every gap. The table
below preserves original F0 coverage, not an instruction to restart those
slices. Current alignment precedes further MAT breadth, not the active fix.

| Existing slice | Additional mandatory exit coverage |
| --- | --- |
| F0.V / SDK | Versioned exact/statistical comparison policy, knowledge and custody capability declarations; infrastructure conformance is not family completion. |
| F0.1 | Partial labor/stages/timers retained through repeated hand-offs; preserve its existing traversal-only scope before F0.2. |
| F0.2 | Independent presentation/physical eligibility, stale/unknown world evidence, constructive dependency safety and causally valid aftermath. |
| F0.3 | Observer-free physical transfers and exact conservation at physical/player durability boundaries. |
| F0.4 | Cross-front effects/pursuit/transfers, unknown-route policy and existing first-class tactical/individual AI ownership. |
| F0.5 | Confirmed/in-flight/ambiguous classification, narrow permissible rollback and partial-save crash windows. |
| F0.6 | Per-family calibration plus continuity, player causality and rapid-switch anti-exploit evidence. |

Persistent clients, cached builds/evidence, selector, event barriers, four CI
workers and immutable fixtures remain required; the accepted monorepo migration
is consumed history, not a new prerequisite. Version comparator/assertion meaning in evidence
identity. A changed semantic contract invalidates affected cached acceptance;
unchanged older evidence remains evidence only for its original boundary.
Re-prepare builds only where their actual fingerprint inputs changed. Never
reinterpret an old green run as proof of the stronger contract.

Acceptance has three pillars: exact continuity, lasting player causality and
no systematic switching advantage. Prove domain/codec cases, focused physical
negative/recovery cases and selected real-client flows separately. M0 canonical,
M1 endpoint, M2 continuous execution and M3 unbriefed player comprehension stay
distinct. No numeric speedup or passing test substitutes for the player promise.
