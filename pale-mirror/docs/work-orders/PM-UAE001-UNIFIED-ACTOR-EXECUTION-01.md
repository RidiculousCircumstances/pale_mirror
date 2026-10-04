# PM-UAE001: Unified Actor Execution

Revision 1, 2026-10-03. Status: EXECUTING; single system goal active.
Russian name: «Единая модель исполнения юнитов».
Executor: main alone; no Terra or subagents. Workflow authority remains
[the execution protocol](../engineering-agent-protocol.md).

## Outcome and scope

Replace activity-dependent ownership of Minecraft NPC bodies with one shared
actor execution protocol and one physical body lifecycle/actuator owner.
Assignments, current activities, body representation and outstanding physical
operations have separate responsibilities. Changing work to food or another
activity must not transfer, recreate or remove an observed body.

This is a comprehensive migration of existing actor-control consumers, not a
farmer-only experiment or a second controller above active legacy controllers.
Connect the existing farmer, baker and resident self-care loop first, then all
other registered paths; the epic remains open until old active paths are gone.
Keep current product behavior and materialization levels: migrating a latent
combat/service path does not authorize new combat, trade or medical features.

The subsequent user execution request has started one `/goal` for the entire
epic. The original planning-only boundary is superseded. Private checkpoints are not
completion; do not stop merely because an internal migration step passed.
Actual authority/safety gaps and user-requested boundaries remain valid stops.
This document grants no additional publication, reset or unrelated mutation.

Active single `/goal` objective:

> Полностью внедрить Unified Actor Execution по PM-UAE001: разделить назначение,
> текущее исполнение, физическое тело и ресурсные операции; мигрировать все
> существующие пути управления жителями и биоформами на единственный протокол;
> удалить старое активное владение телом через ambient/scenes; сохранить
> прерывание/возобновление, ресурсы, HOT/COLD и recovery; проверить интегрированный
> продуктовый цикл и развернуть идентифицированный тестовый билд в рамках
> действующей авторизации. Работать самостоятельно, без субагентов, до всех
> конечных критериев; промежуточные коммиты не завершают цель.

## Starting facts, not speculative defects

Implementation: `/home/rd/proj/pm-f06r3-facility-lane-recovery`, branch
`feat/baker-carry-orders-20260926`, inspected checkpoint `fbe8cbdd`.
Canonical governance: `/home/rd/proj/pm-governance/pale-mirror`.

- `HumanAssignmentProjection` derives retained work claims from family records;
  `ResidentActivityCoordinator` chooses activities from assignments, meals,
  movement, schedule and nutrition. There is no complete shared retained
  execution lifecycle spanning those activities.
- `ActorExecutionCoordinator` already arbitrates authority. Its checks depend
  on ambient/scene leases; `workYield` fences scene-owned actors before invoking
  family checkpoint capabilities.
- `FrontierV3ActorCarrierComposition.Owner` is AMBIENT_LEASE/SCENE_LEASE.
  `FrontierV3ActorOwnerBinding` stamps that owner into entity metadata;
  `FrontierV3ActorHandoffAdmission/Recovery` durably transfer/recover the binding.
- `SceneLease` retains member custody and body positions until CLOSED. Harvest
  observations update both actor location and scene member positions.
- HOT meal validation requires the matching ambient revision/MEAL goal; HOT
  harvest validation requires the exact scene lease. Closed scene body recovery
  contains a harvest-specific path back to ambient ownership.
- `ActivityExecutionCapabilities` has adapted harvest/production providers and
  explicit safety holds for other work kinds. A complete migration must replace
  unexplained kind-wide holds with declared owner interruption semantics.
- Shared goal navigation, typed supported surfaces, timed COLD travel,
  resource accounting, prepared effects, stable actor UUIDs and recovery fences
  already exist. Preserve these obligations rather than inventing replacements.

R18 diagnostics showed farmers advancing and residents feeding after historical
closed-scene-return waits. Those warnings are not proof of a permanent current
failure. The architectural obligation is separation and complete adoption,
not a claim that every present implementation is broken. Performance warnings
and R17 storage overcommit remain separate retained incidents.

## Target ownership and dependency direction

```text
Settlement/hive policies and resident needs
                    ↓ candidates / priorities
             Activity selection
                    ↓ typed selection
         Actor execution lifecycle
                    ↔ registered activity capability
                    ↓ authorized movement / operation requests
        Navigation and physical-operation boundaries
                    ↓ exclusive body actuation
            Actor body lifecycle

Physical observations → exact operation/activity owner → canonical transitions
Scenes reference participants and process/effect scopes; they do not own bodies.
```

| Owner | Owns | Must not own |
| --- | --- | --- |
| Settlement/hive policy | Objectives, assignments, priorities, allowed schedules | Body actuation, route microsteps, physical receipts |
| Needs/schedule and activity selection | Need state, eligibility and candidate ranking | Work progress, meal consumption effects, body lifetime |
| Actor execution lifecycle | One current execution, suspend/resume/cancel/finish protocol, exact participant/owner and execution generation | Recipes, crop selection, food policy, route search, inventory balances |
| Registered activity capability | Family continuation, legal interruption checkpoint, job-owned progress and obligations | Entity creation/removal, competing actor authority |
| Navigation | Versioned semantic movement, bounded path/provider selection, arrival/block evidence | Strategic/activity selection, work completion, body creation |
| Body lifecycle/adapter | Actor-to-UUID binding, physical epoch, admission/unload/recovery/death, exclusive actuator access | Assignments, hunger, job stages, resource economy |
| Resource/effect owners | Quantities, custody, claims and prepared/applied/confirmed effects | Current activity selection or NPC body ownership |
| Scene/process coordination | Exact participants, group constraints, process/effect scope and presentation | A second actor lifecycle, movement writer or position authority |

These are logical components, not one global mutable controller or a required
class per row. Pure lifecycle policy belongs in the frontier domain; Minecraft
body/path/effect adapters remain in NeoForge. Activity implementations plug into
closed typed capabilities (Strategy); generic lifecycle never reads bakery,
crop, combat or medical stages. Composition names implementations, not dispatch
inference. Use explicit state transitions; avoid an unnecessary generic workflow
engine, behavior-tree framework or mutable message bus.

## Required contracts

2026-10-04 field/bakery HOT-position adoption: common body inspection replaces
the pose-only bakery goal/exit and field interrupted-transit events, including
their command/reducer/codec and diagnostic/process catalog declarations.
Field blocked-goal clearance captures full execution/body/scope and plant
generation and cannot install pose or yield. The native field and bakery
navigators retain semantic-target/scope guards; actual physical inspection
precedes family progress. Prepared resource owners/receipts remain separate.
Fresh schema242 rejects older disposable worlds; no historical fallback.
Compilation passed; selected67 distinct modeled checks pass by composition
(47 from run90514, the20 initial failures corrected in run64183).
Initial failures were stale closed-event inventories, an incorrectly positive
plant-generation lower bound (generation0 is valid), and an obsolete sequential
cell-choice test. The latter now asserts executable outstanding work instead
of prescribing list order. This is not integrated native acceptance.
Checkpoint9eca26cb subsequently removes meal and personal movement HOT pose
writers. Arrival/access/goal events retain captured body/execution/scope evidence
and require common inspection; resource acknowledgements never install pose.
The legacy post-consumption RETURN phase and its event/reducer/codec/catalog are
removed, with tag3 reserved and fresh schema243 rejecting older saves. Confirmed
consumption completes feeding; activity selection owns subsequent travel.
Focused run45037 PASS36s (67 frontier checks plus one native-adapter navigation
check), guardrails/native/pilot/test compilation run51799 PASS10s. These are
modeled/adapter-boundary facts, not native graphical acceptance.
2026-10-04 ordinary production/service checkpoint2e86479e (schema244):
semantic arrival/progress/block payloads capture full body/execution/scope plus
family cursor/stage predecessors and require independent common inspection.
Their reducers no longer write HOT position. Production departure checkpoints
retain a versioned bounded approach to the same unfinished station, including
an explicit non-advancing origin when known geometry is unavailable. COLD
requires exact current execution/body/spatial predecessor and shared known
geometry; rejoin never awards processing. HOT delegates the retained approach
to the common goal navigator without changing the original work topology.
Snapshot and job/event codecs retain the current format; old worlds fail closed.
Focused production/service model coverage:66 checks passed in run45352;
the combined command failed only its separate filesystem fixture because the
new common observation incorrectly excluded legal scene revision0. That source
contradiction is repaired; run30964 passed the same filesystem restart check,
eight movement/field authority checks plus five automatic architecture checks
and guardrails in20s. Codec/schema run11757 PASS25s; final native compilation
77722 PASS18s. Both architecture maps validate. Native/pilot/test sources
compile. Service HOT navigation accepts lawful intermediate retained-edge motion
without awarding semantic arrival. Retired the old pilot
method that fabricated production arrivals and later teleported an unowned body;
retained native collision/modeled scope component checks are not a substitute
for integrated lifecycle/death acceptance.
Checkpoint6def4df8 (schema245) subsequently closes service off-cursor departure
and actual-origin re-admission, including scope reopening before body unload.
Service owns the versioned StationApproachState and exact spatial predecessor;
shared geometry owns paths/supports. Input effect settlement is separate from
travel/new-take permission. Presentation can drain without completing work.
Service admission's terrain+1 support mistake is removed. Focused run59386 passed
nine service/two production checks plus five automatic architecture checks,
guardrails and native/pilot/test compilation; final compile recovered from
daemon2522515 passed7s. This is not native graphical acceptance. Unknown-geometry
HOT re-wake and service input settlement after death remain explicit open edges.
Checkpointd3884758 (schema246) closes logistics HOT formation pose writing.
The native arrival captures exact full crew/body/scope and travel predecessor;
common inspection alone installs HOT positions. Original formation/cargo goals
remain stable through saved member approaches and scope re-admission. COLD
explicit provider receipts exclude physical custody, fence physical history and
check shared known geometry. Saved member approach does not credit route/cargo
progress. Delivered cargo cannot start return before custody/scope/approach holds;
terminal segment completion requires actual member formation. One codec retains
snapshot/WAL current bytes. Run75564 PASS61s covers four transition/nine supply/
five architecture checks, guardrails and native/pilot/test compilation. Existing
codec/repair checks remain reusable; recovered71901 passed21s. These are modeled/
adapter facts, not graphical acceptance. Real remaining consumer inventory,
unknown-geometry wake, retired service input, integrated UAE and deployment
remain OPEN under the same goal.

Checkpoint8b249f68 separates service input retained settlement from new takes:
UNKNOWN updates its owner atomically; a late exact receipt cannot revive BLOCKED
work or retired execution/pose. Native retired-body recognition is read-only.
Medical readiness now requires role-assigned final clinical stations within the
planned port capacity, not sorted-member ingress proximity. A registered family
strategy captures/rechecks full live participant authority before remedy mutation;
retained RUNNING/UNKNOWN confirmation needs no reopened HOT scope and cannot
replay consumption. Run27407 PASS28s covers nine service/five architecture checks;
run13932 PASS17s covers eleven medical/five architecture checks. Guardrails and
native/pilot/test compile passed. Service pre-loot resource disposition, remaining
caller inventory, unknown-geometry wake and integrated/native/deployment exit
conditions remain OPEN. This checkpoint is not epic completion.

Checkpoint35718f17 restores read-only HOT approach hints for production, service
and logistics when geometry becomes known, removes migration's redundant terminal
pose write and re-approaches clinical stations before an unbegun medical effect.
Checkpoint627fdd54 (schema247) makes migration's unavailable-path departure retain
actual waiting origin rather than throw, with owned COLD recheck/HOT hint refresh,
unchanged purpose/reservation and shared support/feet/head geometry checks. Eleven
migration checks plus five architecture checks/native/pilot/test compile passed
in48322/17s. Checkpoint8a2b4162 corrects the native harvest matcher to join the
exact retained intent/site/job/worker/output rather than demand an output resource
inside the participant list; its two focused checks passed in80250. This method
fix does not repaint the failed native run green. Worker-meal35718f17 observed
real consumption, resumption and confirmed exact harvest delivery, but failed
at its obsolete terminal matcher; its inspected frame is dark/distant. Complete
integrated graphical/restart/multicrew acceptance, service pre-loot disposition,
remaining caller closure and release/deployment conditions remain OPEN.

1. **Assignment is not current execution.** A paused job retains its exact
   worker relation without permission to act. Execution holds a typed reference
   to its activity owner, never a copied job/progress/resource ledger. Derived
   assignment views remain read-only. Declare ownership of any new relationship
   under [the relationship contract](../frontier-v3-domain-relations.md).
2. **One actor, one current execution.** Retained suspended continuations are
   explicit and bounded; no second independently ticking activity queue.
   Selection may propose a successor, but only a committed transition admits it.
   The kernel remains sole owner of durable due actions and causal ordering.
3. **Distinct identities.** Actor ID/UUID, activity kind+owner+execution generation,
   step/operation ID, movement goal version and physical body epoch have distinct
   meanings. Complete typed declarations precede admission; stable wire tags,
   expected-version validation and closed registration reject stale/foreign
   claims before mutation. Do not infer identity from prefixes, positions or
   collection membership. Replacing an activity is not a new body generation.
4. **Interrupt by operation safety, not scene membership.** Owner capabilities
   return explicit ready/wait/terminal results bound to current state. Accrue
   work through the actual pause instant and retain cargo and reservations
   deliberately. A genuinely atomic pending physical operation must settle or
   enter its typed reconciliation path; an arbitrary full route need not finish
   before yielding. No unconditional "all kinds except harvest cannot yield".
5. **Food ends at consumption.** Confirmed consumption changes nutrition and
   completes feeding. Clearance and later travel are independent activities.
   The selector, not hunger or a meal executor, chooses whether to resume work.
   Without an executable food opportunity, preserve useful allowed work.
6. **Body ownership is activity-independent.** New work or food does not despawn,
   teleport, restamp a family owner or require a scene return/adoption cycle.
   All target-directed movement enters the shared navigator through exact
   current execution authority. Local work gestures and ordinary physics cannot
   grant arrival or become a hidden route follower.
7. **HOT/COLD changes provider, not purpose.** Current physical custody excludes
   competing COLD advancement even without viewers. A safe handoff retains
   supported position, movement/time, labour and pending obligations. Natural
   return projects current state, not historical playback. No forced chunks,
   first-visit restart of work or jump of an observed body.
8. **Positions have one authority.** ActorLocation retains canonical supported
   body state. Scene/formation targets and observed checkpoints are explicitly
   typed references/evidence, not another mutable actor location. Do not retain
   two position stores requiring ad-hoc cross-updates for every movement.
9. **Cancellation does not undo applied effects.** Fence superseded movement
   and uncommitted actuator commands; reject their stale completion. A prepared
   or possibly applied resource effect retains its exact operation identity
   and reconciliation obligation even when the activity is cancelled/replaced.
   No duplicate consumption, output, stock overwrite or fabricated receipt.
10. **Multi-actor processes remain coordinated.** One scene/group references
    independent actor executions under its process contract. Joint irreversible
    effects require all exact participants/preconditions; no global scene lock
    that unnecessarily prevents an unrelated actor from acting or draining.
11. **Recovery is common.** One body admission/join/unload/death path validates
    same-ID/same-UUID identity and exclusive representation. Inactive evidence
    stores reconstruction facts only, not job/route/vitality authority. Keep
    ambiguity visible and local; never reconstruct merely because lookup is empty.
12. **Bounded wake-driven progress.** Blocked execution states name owner/dependency
    and resume condition. Reuse causal wakes and sparse timed travel; no permanent
    per-unit retry loop, microstep WAL or full-world scan introduced by UAE.

## Complete adoption inventory

This is a seed inventory verified from current registries, not permission to
ignore a production caller absent from it. At implementation entry inspect all
body create/adopt/remove, position, navigation/stop, death and unload callers;
resolve any additional reachable caller under the same protocol.

| Existing surface | Required adoption |
| --- | --- |
| AMBIENT_BODY / SCENE_BODY | Replace dual body producers/owners with common lifecycle; remove family-owner stamping and work-to-ambient handoff |
| FIELD_HARVEST / RESOURCE_HARVEST | Crop labour, batch/depot travel, food interruption, multicrew continuity through UAE |
| PRODUCTION / PRODUCTION_WORK | Bakery pickup/station/processing/output/clearance; station and inventory owners unchanged |
| IDLE, WORK, GUARD, PATROL | Ordinary presence/local goals and service turnover use common execution, not a fallback owner |
| MEAL / ACTOR_MOVEMENT | Take/clear/consume and independent personal/service-exit travel; retire ambient-epoch coupling |
| CARGO_TRANSPORT, ESCORT / LOGISTICS | Crew travel and effect participation; cargo carrier lifecycle remains separately owned |
| HIVE_ROUTE_ENGAGEMENT / LOGISTICS interception participants | Existing interception approach/combat/terminal callbacks require ROUTE_INTERCEPTION execution under their exact RouteEngagement owner; transport crew retain LOGISTICS under their distinct RouteOperation. This reachable caller is part of complete adoption, not new combat content |
| ROUTE_PATROL | Retained patrol duty and physical route consumer |
| SETTLEMENT_DEFENCE / SETTLEMENT_ASSAULT | Resident and bioform tactical participants; group policy remains outside body controller |
| ENGINEERING_RECOVERY / ENGINEERING_WORKSITE | Construction/maintenance teams, assembly, station work and tools |
| SETTLEMENT_SERVICE / SERVICE_WORK | Existing service/decontamination participants and exact effect boundaries |
| MEDICAL_EVACUATION / MEDICAL_TREATMENT | Patient and team roles, voluntary/forced incapacity constraints explicit |
| TRANSIT / resident migration | Existing travel/status continuity without alternate body owner |
| SCOUT_PATROL, HIVE_TASK_ASSEMBLY, HIVE_TASK_RETURN | Bioform motion/assembly/return; keep hive policy, physiology and cocoon emergence ownership |
| OPERATION_ASSEMBLY / ENGINEERING_ASSEMBLY | Team rendezvous and formation constraints through typed activity capabilities |

Residents and bioforms share the execution/body protocol, not resident hunger
or a universal priority policy. Dormant/cocooned actors have explicit physiology
eligibility, not dummy walking activities or forced physical creation.

## Implementation sequence, one terminal goal

1. **Close the architecture seam.** Reuse the inspected source inventory and
   define minimal typed execution/capability/body-provider contracts. Update
   `architecture.yml`, execution semantics, navigation/materialization and
   relationship references wherever dual body ownership would contradict UAE.
   Preserve effect-scoped scene authority; supersede only actor-body ownership
   clauses explicitly. Add no competing normative document or placeholder API.
2. **Wire the shared owner and main product loop.** Connect lifecycle, kernel
   events/wakes, body adapter and navigator. Migrate harvest, feeding, service
   clearance, idle and bakery together; preserve current three farmers/two
   bakers and storage-capacity guarantees. A family already migrated has no
   selectable legacy fallback. Do not deploy a half-migrated owner mixture.
3. **Migrate the remaining inventory.** Register actual family checkpoint,
   resume/cancel and HOT/COLD strategies for all rows above. Preserve existing
   topology/formation and physiological constraints. Do not expand content to
   conceal incomplete integration or remove a family to make UAE appear complete.
4. **Close persistence and removal.** Persist execution references/versions with
   stable tags and bounded retention. Hydration, scheduling, first creation,
   joins, unload, death and replay enter one protocol. Remove active ambient vs
   scene body owners, transfer/closed-harvest-return special cases, obsolete
   events/codecs/callers and family-owned physical loops. Deliberate fresh-world
   schema change is allowed; reject incompatible old saves clearly, no migration
   heuristics or dual decoding/execution for disposable historical worlds.
5. **Verify integrated lifecycle, deploy and hand off.** Use the finite evidence
   below; inspect actual client behavior. Bind exact source/JAR/world/start for
   deployment under existing release/server rules. Preserve R17 diagnosis data;
   any new test world uses an explicitly resolved authorized path. Close the
   single goal only after all exit conditions, not at a private checkpoint.

Intermediate source states may be non-release candidates; preserve clean
checkpoints and continue. Step names do not create extra approval gates or
separate system goals. If discovery requires a substantive new responsibility,
amend this contract explicitly rather than silently expanding the controller.

## Acceptance and verification economy

2026-10-04 private checkpoint 3bcac252 closes the connected engineering observed
work-admission cut after the journey checkpoint. Construction and maintenance
retain owner-issued work stations; independent common body observations cannot
retarget those stations. Unbegun effects require the exact current work crew,
tools, HOT process scope and RUNNING common physical authority at those stations.
The shared read-only worksite port rechecks exact actual indexed bodies and
supported positions immediately before mutation. Already RUNNING effects only
inspect and settle their retained postcondition, without replay or waiting for
crew recovery. Construction reuses the existing bounded actionable-intent
selector so a naturally unavailable endpoint cannot block another crew;
malformed canonical targets remain visible owner conflicts. Candidate search
waits normally between cell settlement and its next assembly execution. The
worksite fixture now uses the real assembly-to-work transition. Schema238 is
unchanged; source and canonical architecture flow descriptions are aligned.

39 distinct Java checks are green by composition: 94057 selected 29, passed 26 and
identified three failures from a stale worksite fixture which retained assembly
execution after representing completed approach; 54015 passed the affected 12
after correction, and 35273 passed 10 construction/adapter checks. Final five
native-boundary architecture checks, production compilation, Java style and
updated source architecture validation PASS10s/29795. No native/client/matrix,
packaging, publication or deployment claim. This is not full engineering native
acceptance or UAE completion; remaining inventory and integrated exit conditions
below remain mandatory.

2026-10-04 private checkpoint6a734828 closes the connected engineering journey
spatial cut for construction and maintenance: MUSTER_DEPOT, WORKSITE and
RETURN_DEPOT retain their exact semantic routes and crew while the registered
family checkpoint saves a bounded versioned rejoin from the actual departure
pose. Shared body authority remains the sole HOT position writer. Captured
body/execution/scope evidence and exact predecessor validation fence arrivals;
COLD checks shared known geometry and waits on blocked edges through its
existing cadence. Snapshot and event assemblies use one codec, schema238.
Diagnostics expose actual pose separately from retained route/rejoin progress.
Ordinary tool, cargo, physical-cell and terminal semantics remain unchanged.

Verification is composed, not a repeated full campaign: selected41 Java checks
in72537 passed36 and identified five fixture setup failures; affected25 checks
in33822 PASS20s after those fixes, and final9 (four engineering continuation
checks plus five required architecture checks) with guardrails in45238 PASS22s.
The union is42 distinct checks; unchanged green construction/persistence checks
were reused, not rerun or labelled fresh. Production/pilot/test Java compiled
in72537; final production compile in45911 PASS11s after equivalent formatting/
typed-target cleanup. The engineering checks exercise both owners and all
three journey purposes, HOT departure/scope closure/COLD rejoin across hydration,
stale route/body/scope/execution rejection, blocked-edge waiting and a graded
rejoin. Independent physical presence was added to two old maintenance HOT
fixtures rather than relaxing common body authority. No native/client/matrix
or deployment claim. Registered family spatial continuation for patrol/assault,
remaining physical-effect adoption and integrated multiworker/restart/player
acceptance remain OPEN under the existing order.

| Exit condition | Decisive evidence |
| --- | --- |
| All production control consumers use UAE; no second body/locomotion owner | Source inventory closure plus mechanical architecture checks on real callers/registry; no active legacy switch or fallback |
| Shared code knows no concrete work stages and cannot accept incomplete/stale authority | Capability registration and focused wrong-owner/version/kind/declaration negative checks |
| Work → food → successor preserves the same person, cargo and progress | Integrated farmer/baker path, resource conservation, pause/resume and actual next work outcome |
| Old activity completion cannot affect its successor; possibly applied effects still settle once | Focused late-arrival/cancel and interrupted physical-effect recovery checks |
| HOT/COLD and restart retain one actor, valid continuation and current presentation | Relevant ordinary transition and durable restart split; no historical playback, duplicate NPC or observed teleport |
| Multiworker/facility/service behavior remains usable | Three farmers/two bakers; station/access reservations and clearance; no whole-return-route access lock |
| Remaining registered families have real adoption rather than bypasses | Each inventory row mapped to live capability/caller and proportionate existing or focused ordinary/negative coverage; uninterruptible phases explained |
| Player-visible lifecycle is ready for a consolidated human check | Exact full-pack graphical client story with correlated semantic progress/terminal evidence and inspected readable frames |
| Deployment is real, not a build claim | Clean identity-bound artifact, packaged checks, pinned install and fresh service/world verification under existing authority |

Use static inspection first for reachable ownership contradictions. Extend
existing focused tests; add a permanent test only for a named contract or
reachable defect. Native checks resolve physical uncertainty, not rediscover
known static mismatches. Follow existing declarative scenario/progress-oracle
rules for claimed repeatable acceptance. Inspect at least two successive
agricultural/bakery outcomes and the interruption/handoff/restart story; do not
substitute endpoint stock or an arbitrary waiting duration for those results.
Compose independent evidence where valid; no omnibus native matrix for every
family, repeated unchanged confidence runs or re-proof of accepted CI tooling.

Current performance evidence is not proof UAE fixes TPS. Avoid new algorithmic
hotspot/full-roster scans and use retained metrics plus a targeted changed-path
check if costs change. A measured speedup claim needs comparable measurements;
unrelated projection optimization is not an UAE exit gate.

No product/feature completion while any inventory row, recovery obligation or
old active ownership path remains open. Formal HUMAN_CANDIDATE uses existing
promotion criteria; diagnostic availability alone is not product validation.

## Explicit exclusions and handoff

Private checkpoint bd595b53 (2026-10-04): all actor birth/reconstruction demand
now reaches one FrontierV3ActorBodyController construction, physical placement
and durable-before-insertion boundary. ACTOR_BODY alone has the producer role;
ambient/scene and all scene families are adopters. The compiled constructor and
admission caller census checks that actual wiring. Focused23 checks,
production/pilot/test compilation and architecture passed (14s); extended common
reconstruction assertion passed (8s). Existing-body binding, handoff/removal and
closed-harvest-return are still legacy; this is not single-owner lifetime,
native acceptance, epic completion or deployment. Continue their removal under
the same objective, with no new scene/ambient metadata compatibility path.

Private checkpoint 69cf32d0 connects captured body/execution actuation fences to
farmer, bakery, meals and service-exit movement/STOP, including the deferred
entity boundary. Farmer station waiting no longer installs a separate local
target follower. Resident selection now admits passive PRESENCE; exact resume
declares the current predecessor as well as the suspended claim. Fresh-world
schema227 rejects old worlds. Domain selector/catalog/lifecycle checks PASS14s/
run11217 (32 cases); adapter fence/navigation/bakery checks and compilation
PASS12s/run91073; architecture ratchet passes. These are focused/model checks,
NOT native or integrated acceptance. The source is a private NON-RELEASE migration
checkpoint: dual physical producers/owner stamping, other navigation/local
actuator consumers, bioform passive eligibility, position ownership and full
death/recovery still require closure. Unversioned navigation rejects a fenced
body; remove old release/transfer paths instead of granting them an unversioned
override. No push, native run or deployment; the full goal remains active.

Private checkpoint 569d87a2 adopts MEDICAL_TREATMENT (all seventeen strategies).
The patient and medical team enter one exact declared cohort; transitions and
terminal release retain full participant keys. Physical supply effects remain
separate from activity authority, including terminal/uncertain outcomes. Ordinary
clinical/recovery/lifecycle/diagnostic checks PASS28s/run9592; changed partial,
stale, root-closure and terminal-group checks PASS13s/run74670. Compilation and
architecture pass. Automatic passive presence, actual sole physical controller,
actuation fences, general death/recovery and integrated acceptance remain open.
No UAE native acceptance, artifact publication or deployment.

Private checkpoint 27f5e525 adopts SETTLEMENT_SERVICE (sixteen strategies).
Admission, HOT traversal/block and work progress retain mandatory exact execution.
Terminal effect/block/death acknowledgement retires activity authority, not its
resource/effect obligations. Endpoint dispatch uses explicit lifecycle ownership.
Domain/service/lifecycle and diagnostic/ambient checks PASS34s/run82498; changed
block/late-arrival/owner checks PASS13s/run50893. Compilation/architecture pass.
No native/death-recovery completion or sole physical-controller claim. Medical,
passive issuance and the physical/lifecycle/integration exit conditions remain.

Private checkpoint 12832cd6 adopts engineering assembly/work (fifteen strategies).
Exact crew declarations cover admission, assembly, work successor and terminal
settlement. Confirmed work effects survive casualties without a fictitious next
journey. Construction retirement waits for tools/effects/worksite closure. HOT
stale payload rejection occurs before transaction admission; a following valid
receipt still succeeds. Focused corrected engineering checks PASS21s/run47853;
terminal-readiness checks PASS12s/run14668, source/adapter compilation and
architecture pass. Service, medical, passive issuance, common physical body and
actuation, general death/recovery and integrated release acceptance remain open.
No native acceptance, deployment or publication of UAE.

Private source checkpoint 467b5817 adopts SETTLEMENT_ASSAULT (thirteen strategies).
All assault movement/state/strike/outcome payloads and hive departure carry exact
combat cohorts. Group handoff supports explicitly declared roster growth and
survivor subsets without concrete-family dispatch in the common lifecycle.
The family reducer owns its terminal acknowledgement; root reference closure
rejects missing/foreign combat ownership. Production settlement precedes defence
admission. HOT formation advances canonical actor positions in the same update
as scene evidence. Focused assault/mobilization/tactical/restart/diagnostic checks
PASS1m17s/run44732; affected generic lifecycle/production/admission coverage
passed41/42 domain checks plus adapter checks/run7194. The lone obsolete registry
assumption was corrected; it and the new HOT pose assertion PASS10s/run5981.
No unchanged confidence rerun. Compilation/architecture pass, no native/release
claim. Four inventory kinds plus automatic presence issuance, general death and
recovery, common physical body/controller removal and integration remain open.

Private source checkpoint 199539ee adds HIVE_TASK_RETURN (twelve strategies).
Return admission explicitly binds the parent, survivor routes and participant
executions; final strike planning uses its post-strike state. Current-owner
lookup replaces continuation roster scans. HOT/COLD progress and group terminal
retirement use full retained keys. Focused process/tactical/return checks
PASS57s/run96725; adapter restart check remained up-to-date. Wrong parent,
truncated codec, dropped participant ownership and stale generation are rejected.
This is domain/protocol adoption, not a sole physical-controller or native
completion claim. Assault, engineering assembly/work, service, medical,
passive issuance, physical owner removal and integrated acceptance remain open.

Private source checkpoint 82dcddee: HIVE_TASK_ASSEMBLY now retains exact
participant execution from confirmed cocoon release through group departure.
Dormant physiology is not replaced by dummy locomotion. Actual goal, HOT
continuation and release consumers use the retained declared owner, not roster
discovery; conflicts retain owned obligations and stop ordinary fallback motion.
Lost crew cannot keep moving under assembly progress. Eleven registered
strategies; hive return, assault, engineering assembly/work, service and medical
remain unadopted. General owner death acknowledgements and automatic presence
issuance also remain open. Ordinary/changed fixture checks PASS1m49s/run96241,
affected key/receipt/lost-crew checks PASS15s/run92426, changed HOT owner lookup,
departure/conflict and adapter restart checks PASS15s/run90638; compilation and
architecture pass. No physical-controller or release/native completion claim.

Private source checkpoint 4a1f0658: ROUTE_PATROL uses complete crew execution
declarations on its real COLD/HOT producers, mandatory codecs, admission and
terminal/death outcomes. Observed HOT formation updates the canonical actor pose
in the same transaction. Ten strategies are registered, not ten completed
physical-controller migrations; passive presence issuance is still open.
Focused changed/negative/recovery checks passed by composition (49 unchanged
checks/run39536, corrected body-authority fixtures and route-return/diagnostic
architecture/run87104 PASS26s); adapter/pilot/test compilation and architecture
passed. The old body-owner loops, captured actuation versions, remaining seven
families and integrated native/deployment acceptance remain required. No UAE
deployment or epic-completion claim.

### Current implementation checkpoint (not epic completion)

2026-10-03: accepted UAE amendments recorded in architecture, execution
semantics, navigation, materialization and relationship references. Source WIP
adds retained ActorExecutionState and complete ActorExecutionId (actor, nominal
activity kind, exact owner, generation), stable tags and fresh-only draft
state schema226. Immutable derived active-kind indices are built on execution
transitions, not by repeated full-roster queries. Execution records retain their
generation after completion; no body, route, labour or stock is copied there.

Actual connected consumers: SERVICE_EXIT and MEAL admission, progress,
interruption/completion and observed source-claim loss. Their movement/effect
events carry exact execution identity; resource pre-effect fences preserve it.
Family reference checks reject orphan/mismatched current authority; unadopted
families are explicitly rejected until their providers are wired, never
accepted through an inferred fallback. Harvest/production admission and
terminal retirement now update their job and execution atomically. Seven
registered strategies own checkpoints and bounded continuation. The activity
selector commits resumption with a new generation; confirmed eating leaves
work paused until that choice. COLD labour/work require current job authority.
Movement/meal timer identity is generation-scoped. This is a private partial
migration, not permission to deploy mixed ownership.

Related reachable state-validation gap closed: complete validation formerly
dropped actorMovements; sparse validation shortcuts did not compare that
component. Both now retain/check movement and execution state.

Focused movement/meal/portion/activity/claim-loss checks and adapter/pilot/test
compilation passed; architecture debt stayed within existing ceilings. Later
generation-scoped timer/index and invariant checks passed (30s).
Private checkpoints f8103da4 and fece968b retain these changes, not pushed or deployed.
Affected farmer/meal/resume, baker continuation, stale identity, codec/catalog
and architecture checks passed; latest focused checks PASS15s. The complete
native body lifecycle has not been checked or claimed.
Common body fencing uses the existing recovery store. Scene/ambient canonical
demand, presence confirmation, death and family reconciliation now delegate
that owner. A scene completion/conflict neither retires nor replaces its actor
incarnation; generic recovery payloads cannot bypass body authority. Actual
physical metadata and body controllers are still legacy and require migration.
ActorLocation now retains declared ActorKind:
fresh creation/birth supplies it, observations preserve it, own stable snapshot
tags and canonical/physical validation reject mismatches. Existing physical
UUID/key spelling is preserved under ActorBodyId, not scene ownership.
Owner interruption disposition distinguishes retained work, releasable passive
presence and terminal-only activity. These APIs do not constitute physical
adoption or automatically create a passive activity. Affected source/identity,
recovery, continuation and compile checks passed, no native evidence yet.
TRANSIT admission, advancement, blocking, resumption, completion and death
carry/validate its retained exact execution and retire it atomically. Its timer
identity is generation-scoped; old callbacks fail before mutation.
Private checkpoint 7c91162a connects a registered common body-release event to loaded
removal and saved scene/ambient departure. Epoch allocation in actual body
creation comes from canonical ActorBodyAuthority, not the inactive ledger.
Ambient departure now requires exact entity-region write, storage sync and
persisted acknowledgement before physical custody is released. Carrier evidence
format8 explicitly rejects historical test schemas; canonical draft226 remains.
Focused registered-release/stale-receipt, catalog, saved-departure and recovery
checks and source/pilot/test compilation pass. Common physical custody now
excludes COLD progression even after scene closure in the main loop, transit,
scout patrol, engineering/hive assembly and logistics; COLD combat admission
uses the same gate. Body-free field aborts name the exact PREPARED incarnation;
prepared cancellation uses retained no-insertion evidence. Restart absence may
cancel an unattempted incarnation, never an already-running one on lookup alone.
Changed domain checks PASS2m57s, adapter/recovery and architecture PASS16s.
Full crash/join/death recovery, owner stamping/actuation and old body return
logic remain open; these are private checks, not native acceptance.
Open: remaining inventory registration and work callback identity;
navigation actuation fencing; sole activity-independent
body lifecycle; old ambient/scene body ownership, position duplication and
special return-path removal; integrated native/restart evidence and deployment.
Private checkpoint dc0e1039 adopts SCOUT_PATROL: the owning scout policy issues
its exact context before HOT/COLD motion; advances retain full identity and
mandatory predecessor. Old predecessor-less decoding and obsolete lease repair
are removed for fresh worlds. Release-only passive death is acknowledged through
registered strategies, not concrete-family dispatch. Remaining work-family death
outcomes are not claimed complete. 46 affected domain/catalog checks PASS21s;
source/pilot/test compilation and final architecture/adapter compile PASS7s.
No UAE build deployed: R18/fbe8cbdd remains live, R17 preserved.

Private checkpoint 0c9fba79 adds actual OPERATION_ASSEMBLY/LOGISTICS admission,
HOT/COLD progress keys, atomic group generation replacement and owner terminal
retirement. Nine strategies are now registered, not nine fully migrated physical
controllers. Group identities contain exact actor/kind/owner/generation only;
the operation remains the sole crew/route/cargo-purpose record. Current execution
and operation references resolve in both directions. Existing coordinated crew
policy is declared terminal-only, not a generic unexplained safety hold. Changed
domain/cargo/recovery/terminal checks pass with separate physical-departure
acknowledgement in pure fixtures; source/pilot/test compilation and architecture
pass. Remaining family adoption, passive issuance, physical owner removal,
captured actuation versions and native/deployment acceptance remain open.

No SQL/ORM, new needs, new priority scheme, new navigation engine, new recipes,
new content/armies or unrelated cleanup. Do not increase storage or erase stock
to bypass admission. No compatibility work for old disposable worlds. Preserve
them where diagnostic retention was requested, especially R17.

The ledger stores current progress/open facts and links this order; do not copy
the plan into multiple ledgers. Track adoption rows here only when their facts
materially change. Final handoff names actual migrated/deleted boundaries,
source and deployment identity, decisive evidence and remaining unrelated
incidents. No automatic push/commit/deploy is authorized by this planning turn.
