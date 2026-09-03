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
rendering; it may not define a cheaper history or a different outcome model.

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
unknown changes. This exception covers bootstrap/current-state projection only;
it never covers construction, repair, harvesting or another change process.

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

## Universal definition of done

A spatial process reaches M2 only when all applicable conditions pass:

1. one canonical owner retains exact participants, objects, resources and the
   authoritative progress/cursor;
2. HOT admission leases those same identities before any body/effect appears;
3. semantic access, work, input and output ports are explicit and terrain-aware;
4. start, progress, blocked/interrupted state and terminal result are visually
   distinguishable at player height;
5. ordinary player actions can kill, steal, break, obstruct or counter the
   relevant physical subject and generate typed evidence for the same process;
6. COLD and HOT advance the same progress model without teleport, duplicate work
   or a fabricated reenactment on return;
7. graceful and abrupt restart recover the exact partial state and inspect
   non-replayable postconditions rather than replaying them;
8. a non-flat case and a blocked/damaged port or route fail visibly;
9. bounded diagnostics and one correlation chain explain owner, lease, progress,
   physical action, observation and terminal result;
10. focused normal plus negative/recovery tests pass, followed by one native
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

- `FrontierSceneBehaviors` and `FrontierV3SceneBehaviorRegistry` register only
  logistics, settlement assault, engineering worksite and medical treatment;
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

| ID | Priority | Current strongest evidence | Gap and closure requirement |
|---|---:|---|---|
| MAT-001 | P0 | M2 closed; M3 narrow evidence | Fresh-world schema 114/envelope 26 retain an exact worker-specific bounded pedestrian topology and cursor from the canonical worker surface to the 64 serpentine crop workstations. A HOT scene advances one observed edge before preparing a crop; a direct movement target, bypassed station or pre-arrival crop preparation is rejected. Each crop remains durable-before-effect and observed as AIR before its cursor advances; the depot receipt remains prohibited until all 64 observations. Pure normal, pre-arrival-negative, death and snapshot-restart cursor tests pass; Scene GameTests pass 44/44. Native `disposable_redwillow_harvest` run `5110c424-f5b0-4321-894e-a6fdec3b7f36` visibly found the exact named Villager, gracefully restarted while the same `job:site-harvest-4-wheat-field-1` remained `HARVESTING`, then reached its one `CONFIRMED` exact receipt and autonomous bread transformation. Its clean pre-restart frame is `build/frontier-v3-scenarios/disposable_redwillow_harvest-28f640db-6137-4b58-9ad6-87c5a1d2d0d2-farmer-harvesting-visible.png`. This closes the continuous-worker materialization boundary; unbriefed comprehension of field-work presentation and aggregate crop growth remain separate M3/MAT-008 work. |
| MAT-002 | P0 | M1 | `PRODUCTION_TRANSFORMATION` physically replaces exact depot input with output, but no workshop station, worker action, machine state or visible work cycle owns the transformation. Add a class-C workshop process using the existing exact worker/input/output facts. |
| MAT-003 | P0 | M1 | Generic `STRUCTURAL_REPAIR` and `DECONTAMINATION` directly consume one exact item and change target blocks. Unlike route engineering, neither has a retained crew/machine, approach, work lease or progress. Add service-work owners and HOT scenes/actuators; keep the current effect executors as terminal atomic steps. |
| MAT-004 | P0 | M0/M1 | `RoutePatrol` advances exact residents between canonical waypoints, while resident ambient goals still collapse security work to generic `GUARD` at the settlement anchor. Generic `WORK`, `GUARD` and `PATROL` motion is activity, not proof of the assigned operation. Bind patrol and later role work to exact assignment goals, topology/cursors and observed HOT arrival. |
| MAT-005 | P0 | M1/M2 partial | Hive mobilization proves exact cocoon release, assembly and durable departure, and assault combat has a HOT scene; the same roster does not yet have an evidenced continuous physical march from departure to contact. Carry one retained expedition topology/cursor/Overseer authority through COLD/HOT approach, contact and retreat. |
| MAT-006 | P0 | M1 | Settlement provision consumes exact food then updates several named residents; birth consumes exact food then later adds a resident whose Villager is projected on load. There is no mess/feeding action or household/bed lifecycle transition. Add class-F staged exact-recipient provisioning and birth materialization; do not require one loose entity per food item. |
| MAT-007 | P0 | M1 | Hive nutrient transfer has exact source/target receipts and a COLD corridor but no HOT vascular flow or carrier, while growth consumes biomass and atomically adds an organ, bioform and infection cell. Materialize transfer as interruptible hiveroot flow and growth as staged digestion/morphogenesis/cocoon work with exact biomass and organism identity. |
| MAT-008 | P1 | M1 | Crop and infection projection apply batch block replacement at an aggregate stage. Extent is visible after the fact, but direction and active frontier are not necessarily observable. Introduce bounded spatial work units and retained partial progress for growth, spread, retreat and decontamination; a real instantaneous blast remains exempt. |
| MAT-009 | P1 | M0/M1 | Markets, companies, shortages, quarantine and doctrine can be inspected and may affect canonical choices, but coverage does not yet prove that each decision class has an unambiguous operational consequence in play. Maintain a decision-to-visible-consequence matrix and reject board/diagnostic-only completion claims. |
| MAT-010 | P1 | M0/M2 partial | Exact resident health and nutrition affect assignments, quarantine and medical work, but ordinary ambient presentation exposes profession/tactical function rather than hunger, exposure, infection or recovery. In HOT space, environmental contact must address the same exact resident and condition changes need readable body/behaviour cues; COLD may use bounded canonical exposure but cannot be contradicted by simultaneous HOT reality. Add symptom/recovery/starvation presentation and ordinary contact/intervention evidence without making labels a second health state. |

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
