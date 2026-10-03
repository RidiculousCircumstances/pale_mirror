# Frontier v3 materialization completeness contract

Status: normative completion gate and active gap register.

This document closes a recurring ambiguity in Frontier v3: a canonical process
can be deterministic, conserve exact actors/items and have a correct physical
postcondition while still not exist as a believable Minecraft process. A wheat
stack appearing in a depot after all field blocks change is a correct endpoint;
it is not evidence that a farmer harvested the field.

The player promise remains stronger: when a process intersects naturally loaded
space, the player sees the same process continue through Minecraft bodies,
objects, movement and effects, can interfere through ordinary play, and sees the
result reconciled back into the same canonical state. COLD execution may omit
rendering and use bounded calibrated physical approximations; it must retain
the same process identities, conserved resources, causal order and declared
exact/statistical outcomes. The execution-semantics contract defines this
boundary; it does not promise bit-identical HOT/COLD physics.

## Evidence levels

Every implementation and report must use the strongest level actually proved.
Evidence never promotes itself to the next level.

| Level | Name | What it proves |
|---|---|---|
| M0 | Canonical | The process, ownership, quantities, schedule and outcome are correct without Minecraft. |
| M1 | Physical endpoint | A loaded-world executor performs and reconciles an exact block, entity or inventory postcondition. |
| M2 | Continuous HOT process | Exact participants or physical actuators execute observable start, progress and completion/failure while the area is naturally loaded; player interference changes that same process. |
| M3 | Player-comprehensible | A player-height native scenario and product review show that an unbriefed player can identify what is happening, who/what is doing it, its progress, interruption and result. |

Diagnostics, boards, captions and screenshots are supporting evidence. They do
not turn an M0/M1 mechanic into M2. Generic wandering near a workplace is not
evidence that the named worker performed the named job.

## Materialization classes

Every new or existing canonical process must be assigned exactly one primary
class before it can be called complete. A process may compose several classes,
but each applicable requirement remains binding.

### A. Baseline and persistent state

Examples: a fresh-world building, a road surface, an already-grown organ or an
idle storage chest.

The desired-state materializer may project this state without acting out its
history. It still requires stable object/part IDs, semantic provenance, exact
baseline preconditions, visible conflict on drift and no overwrite of player or
unknown changes. This includes the current result of legitimately completed
COLD work; it does not replay old harvests, build steps or explosions on entry.
It never authorizes compressing ongoing HOT work into an endpoint or discarding
an unresolved irreversible effect. Projection, physical interaction and deferred
aftermath follow their separate execution-semantics contracts.

Readiness of presentation, eligibility for physical interaction and exclusive
mutation authority are separate facts. Projecting a completed COLD result is M1
evidence for that result, not M2 evidence for how loaded work is performed.

### B. Atomic local interaction

Examples: one hand-to-chest transfer, opening one cocoon, issuing one tool at a
service port or firing one already-prepared projectile.

An interaction may complete in one physical turn only when every required actor,
item and target is simultaneously present at the same semantic station, the
interaction is ordinarily visible/interceptable, and its exact postcondition is
observed. It may not teleport a person or item, mutate a remote endpoint, or
compress a duration-bearing job into the interaction.

### C. Actor- or machine-owned work

Examples: field preparation and harvest, workshop production, building repair,
decontamination, loading, medical work and construction.

The canonical job must retain its exact worker/team or exact autonomous machine,
input custody, output destination, facility ports/work stations and bounded
progress. HOT owns the same participants through a lease and shows at least a
start, an in-progress state and completion/failure through actual movement,
interaction and effects. Killing a worker, stealing an input, breaking the
station or blocking its route changes or blocks that same job. An executor that
directly changes all result blocks/items without the retained actuator is only
M1.

### D. Movement and coordinated operation

Examples: patrol, caravan, migration, raid approach, escort, retreat and a hive
expedition controlled by an Overseer.

The operation owns one exact roster, formation/topology, cursor, target and
command authority. COLD advances that cursor; HOT moves the same bodies through
ordinary Minecraft navigation/collision and commits only observed arrival.
Ambient role movement or a generic body near the destination cannot substitute
for the operation. Departure, march, arrival, contact, retreat and losses must
form one retained continuity chain.

### E. Distributed environmental process

Examples: crop growth, infection spread/retreat, hiveroot nutrient flow, fire,
collapse and territorial recovery.

An actor is not mandatory, but the process itself is physical. It requires
bounded spatial work units, direction and retained partial progress. A loaded
player must be able to observe more than only before/after states, and ordinary
block loss, obstruction or counter-action at the active frontier must change the
same canonical process. Updating an entire field, infection cell, structure or
network in one adapter loop is M1 unless the canonical action is genuinely one
instantaneous effect such as a single explosion.

Initial HOT projection and return from COLD show the current retained partial or
terminal state; they never replay missed history for spectacle. Incremental HOT
requirements apply to work that continues or begins while naturally loaded.

### F. Lifecycle, care and consumption

Examples: provisioning, birth, healing, dormancy/waking, feeding, digestion,
death and biomass conversion.

The process retains every affected exact individual plus the relevant household,
bed/cocoon/treatment/feeding station and exact resources. HOT represents its
meaningful stages and exposes interruption. Consuming an item and later spawning
an adult body, changing a health number or adding an organ without an embodied
transition is not complete materialization. A new body may appear only at the
process's observed physical transition, never merely because its chunk loaded.

### G. Decision and information

Examples: doctrine, market price, quarantine policy, shortage, contract choice
and strategic threat assessment.

The internal decision need not be acted out by a mob. It must, however, produce
at least one distinguishable gameplay consequence through the applicable
classes: changed work/facility state, cargo flow, deployment, closure, behaviour
or physical outcome. A diagnostic value or board text alone is not a gameplay
materialization claim.

## Execution-mode ownership

For every duration-bearing class, the canonical process exists and begins from
domain causes whether or not its area is loaded. Presentation demand and
physical interaction eligibility select bounded execution/custody independently:
COLD advances bounded semantic progress without Minecraft bodies,
while a physical lease lets the process-specific HOT driver advance the same
next steps from observations. The lease is not a second process and owns no
independent progress or outcome.

Apply `frontier-v3-execution-semantics.md` at every completeness level: exact
identity/accounting is not exact physical fidelity; COLD knowledge and allowed
approximation are explicit; scenes have no causal walls; recovery distinguishes
confirmed consequences from ambiguous custody. A visible endpoint cannot prove
unknown-terrain causality, observer-free transfer safety or hard-crash durability.
M2 remains continuous visible work; M3 still requires unbriefed comprehension.

Acquire, checkpoint and release must atomically bind one process version, its
exact participants/resources and its authoritative cursor. Returning to a
loaded area shows the retained current state; it never starts the work, resets
its clock, recreates a participant or reenacts missed history. A process cannot
reach M2 if viable off-screen work waits for a player, or if leaving and
returning loses its actuator even though canonical work remains active.

For an exact actor, safe physical release retains one bounded inactive carrier
with the canonical actor ID, stable Minecraft UUID and physical revision. The
carrier is reconstruction/reconciliation evidence only: it is neither a live
physical custodian nor a second owner of identity, vitality, assignment,
movement, process progress, schedule or inventory. COLD may advance only after
the live binding is fenced. Natural return re-adopts or reconstructs the same
actor and UUID at its later canonical state under one current epoch; it never
chooses a replacement or reports that retained eligible actor as missing.
Missing, duplicate, foreign, stale or revision-mismatched carrier evidence is a
typed local ambiguity. Continuity requires the same person and UUID, not the
same in-memory entity object, and forbids a visible position jump while the body
is observed.

This rule does not force every class into a scene. Class-B atomic effects use a
durable intent and observed postcondition; class-E environmental work owns a
spatial frontier; class-G decisions use downstream consequences; ambient bodies
may hold presence custody without becoming a work operation.

## Universal definition of done

A spatial process reaches M2 only when all applicable conditions pass:

1. one canonical owner retains exact participants, objects, resources and the
   authoritative progress/cursor;
2. domain causes schedule and begin the process independently of player/chunk
   demand, and a registered COLD driver can advance its same progress model;
3. HOT admission leases those same identities and authoritative process version
   before any body/effect appears;
4. semantic access, work, input and output ports are explicit and terrain-aware;
5. start, progress, blocked/interrupted state and terminal result are visually
   distinguishable at player height;
6. ordinary player actions can kill, steal, break, obstruct or counter the
   relevant physical subject and generate typed evidence for the same process;
7. COLD and HOT advance the same progress model without teleport, duplicate work
   or a fabricated reenactment on return;
8. HOT acquire, observed checkpoint and release atomically update the lease and
   owning process, leaving no independent scene cursor or terminal outcome;
9. graceful and abrupt restart recover the exact partial state and inspect
   non-replayable postconditions rather than replaying them;
10. a non-flat case and a blocked/damaged port or route fail visibly;
11. bounded diagnostics and one correlation chain explain owner, lease, progress,
   physical action, observation and terminal result;
12. focused never-loaded, arrive-mid-process, leave/return, intervention and
    restart tests pass, followed by one native
    scenario that asserts the domain result and captures the process in progress.

M3 additionally requires a clean player-height review in which an unbriefed
player can explain the cause, current activity, threatened value, available
intervention and outcome. Graybox colour, particles, pose, carried item and local
object state should carry the explanation; text is a supplement.

## Confirmed current gaps

These are implementation debts, not evidence that their canonical models are
incorrect. Existing exact inventory, receipt and recovery work is retained as
the foundation for the missing HOT layer.

The audit is anchored in the following current-source boundaries:

- `FrontierSceneBehaviors` and `FrontierV3SceneBehaviorRegistry` register the
  closed logistics, settlement-assault, engineering-worksite, medical-treatment,
  resource-harvest, production-work and service-work scene families. The
  service-work adapter is intentionally inert until its canonical planner emits
  an exact retained-worker lease; it may not reuse another family executor;
- `FrontierV3ResourceSiteExecutor`,
  `FrontierV3ResourceSiteHarvestExecutor`,
  `FrontierV3ProductionTransformationExecutor`,
  `FrontierV3StructuralRepairExecutor` and
  `FrontierV3DecontaminationExecutor` perform the listed aggregate endpoint
  mutations;
- `RoutePatrolProcess` owns exact patrol members/waypoints, while
  `AmbientActorProcess.goalFor` maps ordinary resident security work to a
  settlement-local generic goal;
- `PopulationBirthProcess`, `SettlementProvisionProcess`,
  `HiveNutrientTransferProcess`, `HiveGrowthProcess` and `HumanHealthProcess`
  own the listed canonical transitions;
- hive-growth and hive-nutrient pilot scenarios still assert terminal
  diagnostics/receipts and result frames, not their missing in-progress actors
  or physical frontiers; `MAT-001` is the implemented exception below.

V3-AUD-043 and the F0.1 traversal foundation remain accepted. MAT-001 had been
classified M2 from its continuous HOT/death/restart evidence plus never-loaded
COLD progress, repeated hand-offs and recovery over the same farmer. The exact
deployed revision-15 human history now overturns that complete classification:
after the final crop the worker idled, then disappeared on re-entry while the
field reported `farmer is needed`. The earlier evidence remains valid only for
its narrower observed stages. MAT-001 is reopened at M2 until one natural story
proves terminal closure, inactive-carrier hand-off, later COLD progress and
return of the same canonical farmer and Minecraft UUID without replacement,
reset, replay or visible teleport. M3 remains unaccepted.

The system-wide carrier correction changes an evidence dependency, not every
previously proved local behavior. Before MAT-006 resumes, `XACT-001` inventories
all production body creators/adopters and applies a closed composition guard.
MAT-002 through MAT-005 retain their local M2 facts, but any path that depends
on changed live-body/inactive-carrier behavior is `NEEDS_TARGETED_REVALIDATION`
until the smallest representative lifecycle passes. Do not treat either a
blanket retained-green status or a blanket native rerun as dependency analysis.

All remaining rows additionally follow
[`frontier-v3-materialization-hardening-pipeline.md`](frontier-v3-materialization-hardening-pipeline.md):
their natural story must cross visible progress, terminal and declared
successor, ordinary leave/COLD/restart/return, exact identity/custody and one
player intervention. A proof ending at an intermediate receipt is
`PROVEN_NARROWER`, even when every component assertion in that receipt is true.

The broader F0 correction programme in
`docs/frontier-v3-seamless-foundation.md` is now a mandatory predecessor to new
MAT breadth. MAT-001 supplies the paired-driver reference, but does not by
itself close replica/custody separation, deferred unloaded aftermath, fungible
resource custody, bounded operation fronts, local navigation, fenced recovery,
failure taxonomy, observer neutrality or first-visibility readiness. MAT-004
and later rows remain paused until the complete F0 gate passes.

| ID | Priority | Current strongest evidence | Gap and closure requirement |
|---|---:|---|---|
| MAT-001 | P0 | M2 reopened; M3 not accepted | Earlier schema-114/envelope-26 HOT/death/restart evidence, accepted F0.V/F0.1 traversal evidence, four-worker run `34124239973` and proof-harness commit `8217f246079acc9220279ce66386b687c4a7441c` remain valid for their narrower stages. Exact deployed revision-15 human evidence rejects the complete duty cycle: the farmer idled after the last crop, then vanished on re-entry while the field reported `farmer is needed`. Closure now requires one natural terminal/successor story across live-body release, one inactive carrier, COLD progress and return of the same canonical actor and Minecraft UUID, including negative carrier/recovery cases and no replacement, reset, replay or visible teleport. No unbriefed player-comprehension evidence exists, so M3 also remains open. |
| MAT-002 | P0 | M2 closed; M3 not accepted | Fresh-world schema 120/envelope 34 retain staged work and one bounded immutable worker-to-workshop topology/cursor through a semantic port with distinct input/work stations, headroom and public-circulation join. The two station supports are immutable, provenance-owned, traversable cyan/magenta floor cells; they are neither containers nor alternate inventory/movement authority. An observed arrival atomically advances both the job cursor and the persisted HOT lease's exact body position; a split recovered lease fails closed, and old schema/WAL bytes are rejected rather than inferred. Typed reducers accept only a matching HOT lease plus the observed exact worker at the retained station; no executor mutates a job directly. The registered class-C `PRODUCTION_WORK` executor materializes only that worker, advances observed approach/input/processing, and defers exact chest replacement until `OUTPUT_READY`. A loaded full block at the exact next retained worker body produces the persisted `ProductionWorkTraversalBlocked` fact, then `ROUTE_BLOCKED`/task `BLOCKED`/lease `DRAINING`; it cannot choose an alternate edge. A blocked job remains attached through durable release, then `ProductionWorkSceneFinalized` removes it and releases its exact reservation without a zombie lease. The player-driven input-theft scenario `disposable_materialized_production_input_theft` passed on a fresh world: the exact HOT worker drained, the job/order became `BLOCKED`/`CANCELLED`, and the exact wheat moved to player custody. A physical loss of an owned workshop station routes at the physical-observation boundary to the same job's immediate `FACILITY_UNAVAILABLE` block and HOT drain; it cannot wait for a strategic timer. Focused normal, forged-observation, cursor-recovery, theft, station-loss and route-block planner tests pass; the dedicated Scene GameTest proves approach/input/processing, durable current-station retention, exact body death/finalization and full-next-body collision without test force-loading (55/55 Scene slice); Economy 17/17 passes. Native `disposable_materialized_production_work_restart` run `652d7777-f5c8-4a40-adcb-b572c953275d` proved the exact `INDUSTRIAL WORKER`, a live `PROCESSING` stage on the exact magenta work station, terminal `FULFILLED`, then graceful recovery with the exact bread receipt intact. Fresh native `disposable_materialized_production_route_blocked` run `b3f63718-0566-48f4-9fbd-98e230396664` placed ordinary gray concrete at a read-only immutable future body anchor, then reached `CANCELLED`, no active job/reservation and task `BLOCKED`; it neither selected an entity nor altered the canonical plan. The filesystem runtime-restart test retains `PROCESSING(17)` and the exact work cursor before explicitly quarantining that one HOT lease to `UNKNOWN_AFTER_RESTART`, without creating output or releasing the job. Its camera reads only scene topology and cannot select or move a body. The graybox workshop now has a three-wide, two-high loading portal while retaining one canonical centre traversal edge; this is an M3-oriented geometry improvement, not M3 acceptance. Its newest player-height frame contains the worker and both distinct station colors but leaves the worker partly behind the right wall. M3 therefore still requires a reusable semantic presentation pose/grammar, not a hard-coded seed camera. |
| MAT-003 | P0 | M2 closed; M3 not accepted | The canonical decontamination scan retains one exact `SettlementServiceWork`: qualified medic, intact infirmary, active exact depot slot, two immutable stations/topologies/cursors, input-issue intent and endpoint intent. The closed `SERVICE_WORK` scene admits only that retained worker, advances only the next observed body/cursor, issues the same held reagent at the retained source station, performs 80 observed work ticks and enables the terminal effect only at `EFFECT_READY`. A one-edge recovered physical arrival is reconciled only to the retained immediate next cursor; every other body/cursor mismatch is a bounded return to the retained surface or a visible conflict. Each accepted edge atomically updates the work cursor, scene recovery anchor and the sole canonical actor body, so input/effect validation cannot read a stale worker location. The terminal adapter changes only its observed overlay cell and consumes the same exact held reagent; completion atomically drains the lease. A graceful restart quarantines the same work/lease to `UNKNOWN_AFTER_RESTART`, then exact naturally loaded reclaim resumes the retained work rather than replaying its transfer/effect. Focused normal, forged-cursor, one-edge recovery, exact-actor-custody and restart tests pass; the dedicated Scene slice passes 56/56. Fresh native `disposable_service_decontamination_restart` used one ordinary test player and disposable seed 41: pre-restart run `109f8a50-5a47-4090-8ad9-75685310745b` observed the exact named `MEDICAL WORKER` in the active HOT scene; post-restart run `18840c72-2979-402e-8e83-58ccdee13b2f` reached the sole `CONFIRMED` decontamination receipt, consumed the exact reagent and closed the same work. The narrow clean frame is causal/identity evidence only: the medic remains visually indistinct among graybox residents, so it is explicitly rejected as M3 player-comprehension evidence. `STRUCTURAL_REPAIR` remains on its direct path until separately admitted. |
| MAT-004 | P0 | M2 closed; M3 not accepted | Clean schema/envelope `152/62` candidate `aa12f919` replaces the old waypoint/body-rewrite patrol with one exact retained task, ordered roster, individual ingress, distinct formation and immutable pedestrian topology/cursor. COLD advances one retained edge atomically; the registered `ROUTE_PATROL` scene and shared provider commit that same formation only from observed exact-roster arrival. Focused pure/codec/non-flat, ordinary/blocked/body-loss and demand-loss evidence passes; candidate-bound native run `b6e334d4-022d-41c6-ac44-1f9060485a7e` proves ordinary demand, active-scene graceful restart, same exact HOT roster, ordinary-player obstruction and terminal typed block without generic guard substitution or replay. Its player-height graybox frame is only M2 continuity evidence; unbriefed direction/result comprehension and natural-terrain hardening remain M3/release scope. |
| MAT-005 | P0 | M2 closed; M3 not accepted | Clean schema/envelope `154/64` candidate `741d4dda` retains one exact Overseer-led expedition operation, four-member roster, formation, immutable `GROUND_BIOFORM` topology/cursor and command authority from mobilisation departure through COLD/HOT approach, contact and owned retreat/return. Focused evidence covers non-flat progress, snapshot/WAL restart, typed obstruction, body/controller loss and demand return; Scene passes 77/77. Candidate-bound native run `8a2c700f-6997-43cd-beb1-19dceece457c` observes the exact HOT roster and terminal typed occupied-next-body result in one 18-line PMV3 trace; the final critical gate passes 324/324. The graybox frame proves physical presence only: unbriefed Overseer, direction, command-loss, contact and retreat comprehension plus natural-terrain hardening remain M3/release scope. |
| MAT-006 | P0 | M1; target contract accepted; XACT-001 predecessor | Settlement provision consumes exact food then updates several named residents; birth consumes exact food then later adds a resident whose Villager is projected on load. There is no mess/feeding action, return of the same temporary recipient to its retained duty, or atomic household/bed/birth-to-body carrier lifecycle. `frontier-v3-recipient-provision-birth-contract.md` requires class-F exact-recipient allocations, bounded physical batches, duty return and exactly-once child emergence without one loose entity per food item. Implementation/evidence remain pending. |
| MAT-007 | P0 | M1; target contract accepted | Hive nutrient transfer has exact source/target receipts and a COLD corridor but no HOT vascular flow or mixed-mode carrier, while growth consumes biomass and atomically adds an organ, bioform and infection cell. `frontier-v3-hive-metabolism-morphogenesis-contract.md` requires interruptible directional hiveroot flow, irreversible exact digestion and retained organ/bioform morphogenesis with exact conserved biomass, emergence identity, complete-footprint activation and cocoon/attachment dependencies. Implementation/evidence remain pending. |
| MAT-008 | P1 | M1; target contract accepted | Crop and infection projection apply batch block replacement at an aggregate stage. Extent is visible after the fact, but direction, active frontier and complete multi-chunk visible envelope are not necessarily observable. `frontier-v3-spatial-frontier-contract.md` requires bounded retained work units, partial progress, intervention/recovery and truthful multi-direction first visibility for crop, infection and root environmental fronts; a genuinely instantaneous blast remains exempt. Implementation/evidence remain pending. |
| MAT-009 | P1 | M0/M1; target contract accepted | Markets, companies, shortages, quarantine and doctrine can be inspected and may affect canonical choices, but coverage does not yet prove that each decision class has an unambiguous operational consequence in play. `frontier-v3-decision-consequence-contract.md` requires every checked-in causal-matrix row to reach its real exact task/operation, terminal or blocked fact, physical result and delayed aftermath; native sampling by presentation family cannot make an unexecuted row green. Implementation/evidence remain pending. |
| MAT-010 | P1 | M0/M2 partial; target contract accepted after MAT-009 | Exact resident health and nutrition affect assignments, quarantine and medical work, but ordinary ambient presentation exposes profession/tactical function rather than hunger, exposure, infection or recovery. `frontier-v3-resident-condition-presentation-contract.md` binds composed readable cues, COLD deterioration, exact same-body return, physical treatment and duty resumption to one `ActorCondition`, with HOT/COLD neutrality and no second health state. Implementation/evidence remain pending. |

## Existing reference verticals

These are reusable foundations, not blanket M3 acceptance:

- logistics retains exact people, cargo, custody, HOT/COLD travel, theft and
  restart;
- engineering worksite retains the exact crew, tools, material, approach,
  physical cell receipt and return journey;
- medical treatment retains patient, carers, treatment stations, supply and
  HOT/restart lifecycle;
- migration retains one exact person and one shared HOT/COLD cursor;
- settlement combat and the Bomber use real bodies, combat and unrestricted
  observed effects;
- cocoon release/assembly and purposeful Scout patrol prove identity-preserving
  mobilization and sensing foundations.

Each new class should extend the nearest reference vertical rather than add an
endpoint-specific executor or a parallel movement/ownership authority.

## Planning and reporting rule

The implementation plan owns closure order. `MAT-*` remains open until its
native M2 evidence exists; a correct M1 postcondition may close a causality bug
without closing materialization. Progress reports must name the evidence level.
Wave 6 and v2 removal cannot complete while any P0 `MAT-*` gap is open. P1 gaps,
including `MAT-008` through `MAT-010`, must close before the clean-room M3
product gate.
