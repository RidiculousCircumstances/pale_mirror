# Archived continuity before governance checkpoint, 2026-10-03

2026-10-03 20:49 DEPLOYED capacity repair, main alone/no subagents.
Implementation fbe8cbdd in `/home/rd/proj/pm-f06r3-facility-lane-recovery`;
LIVE now fbe8cbdd R18. Old R17 preserved for diagnosis. User reported all farmers
standing, no bread, baker on station chest; t559062 confirms all3farmers carry64
and returningForBatch, depot20wheat stacks+4tools+3reservations,21STARVING.
Source repair protects input-vacated capacity through pending output demands
derived from exact durable production owner/phase until delivery. Common
ContainerInboundCapacity/ContainerStorageAdmission never inspect concrete jobs;
ContainerStorageDemandSources composes owner providers. New reservations,
fungible receipts and cargo admission include pending inbound; output delivery
excludes only exact own declared demand (missing/foreign fails). HOT destination
selection and prepared-event validation use the same owner-aware boundary.
Production admission accounts for input replacement and is checked in planner
and reducer. Existing field slot guarantees are preserved, not revoked.
No schema/tag change, resource issuance or capacity enlargement. Container
diagnostic adds pendingInbound. Regression uses actual full-depot input pickup,
then verifies freed slot blocked for new reservations, allowed for owner,
same after codec recovery. Old full-depot fixture is changed to model external
placement (raw inventory free slot), not compliant producers stealing protected
space. Capacity-neutral conversion assertion updated deliberately.
First attempted soft-reallocation prototype was discarded before deployment:
it would revoke old field guarantees without a durable owner acknowledgement.
Guardrails initially caught hotspot/file growth; generic admission extracted
from WorldState, field validation moved to its owner, diagnostic budget extracted.
Latest changed-candidate tests/architecture/guardrails/package run19893 PASS104s;
95 Java checks passed (58 domain/architecture,37 adapter), no failures/errors.
Source checkpoint fbe8cbdd7d00f47979b82e881ab7bf0050837a20, clean implementation;
detached release `/home/rd/proj/pm-storage-capacity-release.5R1ZZx/source`
build/package PASS9s. User chose NEW test world, keeping R17 for diagnosis.
Server save-all flush confirmed; R17 remains intact in its original directory.
Pinned installer updated server/client JAR and recoverably archived old JARs.
New world `frontier-v3-storage-capacity-r18-20261003`, same seed20260918065;
preflight PASS, start1791042550/invocation2bca82ef04e844fc94fcfefd91a51404.
SHA512 c1cbc304ded5fa8c9694371af7cccc273a1a80344f5c92f1ef79d1a1bcae76d71b360816a01ee93d4b893f7c2665c58c1af4174f24f42b69ad3b2a4bb471c190.
Poststart verification PASS: launcher197508, listening25565, fresh runtime/Done.
Read-only t233 summary:12settlements,366residents,requiredConflicts0;
Clearwater NORMAL,21nourished/0hungry/0starving,initial bread64,field INTACT.
Initial stocks are genesis, NOT proof of production/feeding acceptance.
No native-client product acceptance this increment. Temporary artifact HTTP
stopped. No push. NEXT user test fresh R18; keep old allocation incident open.
earlier focused57 checks had2 expectation failures now corrected, not accepted.
Existing R17 is already overcommitted
and cannot recover just by preventing NEW claims: choose actual resource
withdrawal without deleting stock or implement an explicit safe yield protocol.
Do not represent new-world success as recovery of this existing incident.

2026-10-03 19:50 ACTIVE repair on user's request, main alone/no subagents.
Source checkpoint808ec887d2e6c6d60122886ad6285b398b4afe5e, clean implementation
checkout `/home/rd/proj/pm-f06r3-facility-lane-recovery`, no push. Detached build
`/home/rd/proj/pm-bakery-route-release.0dG2M5/source` PASS11s (-x test, source's
affected tests already passed); exact SHA512
e740c1bab5a3cdbf156025a0d12640ce41336c3ad235370aaa92fc1c8af5ee6b900c94c35e02025b198804fc34b2c56fa0d3cfd28c61e05ed254ef8547b4b12d.
Repair: bakery ROUTE_BLOCKED is revalidated by exclusive COLD planner against
current known geometry/authority/custody/pending-effect gates. Only committed
exact valid successor retires route block, not planning or elapsed time.
HOT witnessed arrival also clears route-only block before goal/effect handling.
All other bakery block categories still demand their physical reconciliation.
No new WAL event/tag/schema, resource issuance, teleport or storage enlargement.
coldRouteBlocker now saysNOT_EVALUATED:<hold> if route was not checked.
ExactInventory's existing capacity budget now exposes a read-only snapshot;
container diagnostic separates packed canonical stock/bound stacks/reservations.
Capacity-accounting suspicion RESOLVED without admission-rule change: live depot account
custody:container-7-depot has1316 wheat/no bindings, requiring21packed stacks,
plus4tools+2active-field reservations=27/27. Previous occupied diagnostic was
misleading, not evidence empty/unnecessarily full storage. Production stalled
while wheat accumulated; subsequent observation also proves an allocation defect
(below), so route recovery alone does not close feeding/production.
Tests81 (44domain including5architecture,37adapter) PASS89s; guardrails PASS22s;
architecture contract/debt/package/diff checks PASS. Regression covers persisted
route recovery vs every other block reason, no resource change on first movement,
HOT exclusivity and1316unbound wheat capacity case. Normative semantics updated.
Server/client pinned installer applied after save-all flush confirmed/no players;
same R17 retained, old JARs archived recoverably. Start1791039038,
invocation7f8febdc7c2b4afbaba99e5032bf2ca0; preflight/poststart verifier PASS.
Actual liveJava90513/launcher90486; temporaryHTTP90023 stopped. No new native
client/test server/Gradle/Xvfb running. No reset/publication/push this turn.
COLD retained baker7-3/job:c33a0eda34040b661b5a0d5e3254d8e4 (full ID below)
resumed without player or fast-forward: t516288 route block null and moving,
t517811 PROCESSING44/80, t518404 73/80, t518959 DEPOT_DELIVERY with bread.
Second baker7-9/job:production-food-expansion-331c55189d27b8237d8fbde7c647da00
also picked input, processed and reached DEPOT_DELIVERY. Thus the route repair
works through manufacture in COLD, not through terminal delivery/feeding yet.
New observed/source-proven blocker: t525336 depot4exact+20packed wheat+3field
reservations=27/27. Grain pickup briefly freed capacity but a new field job16
claimed it; neither bakery job retains output capacity after input pickup.
Both bakers hold bread: 7-9 DEPOT_STORAGE_FULL, 7-3 DEPOT_SERVICE_WAIT. Clearwater
21STARVING/availableBread0, globalNORMAL/no quarantine. No source change for this
second defect yet; do not call complete repair or assume a larger chest fixes it.
At t526445 field13/15 each carry64, delivered64, returningForBatch, outputslots
24/25, bodies128/64/13; field16 carries35, slot26, ordinary harvesting continues.
SOURCE: ResourceSiteHarvestPlanning.batchDeliveryCapacityAvailable and
ResourceSiteHarvestProcess.finishAfterColdBatchReturn require an additional
next-batch reservation before delivering the already reserved current batch.
ProductionProcess.planStart has no durable output-capacity protection;
SettlementFoodPlanner's active-task expansion bypasses its initial breadCapacity
assessment. ServiceAccessCoordinator sorts work applicants by atPort then jobId
without service-readiness, so a capacity-blocked applicant may retain priority.
NEXT design/fix storage admission and current-batch versus next-batch capacity
as one coherent boundary, preserving exact existing cargo and pending physical
receipts. Do not silently revoke farmer claims, overwrite resources, increase
storage, reset the world to hide this incident, or run another confidence matrix.

2026-10-03 19:41–19:45 user requested read-only post-deploy check; no new run,
mutation/reset/deploy. Same03eac149 runtimeJava3708529/invocation4ee77bdc3d13448b9b3704dc3b16732f.
Native diagnostic stay completed normally14:30:43 (result statusok, assertions[]);
client/Gradle/Xvfb3709660/3709252/3709101 absent, cleanup succeeded.
HOT logs14:14–14:30 show11 distinct resident-meal-consume-observed receipts,
farm labour continuing and scenes released on departure.9 overload warnings in
that visit, mostly2s, so performance not closed. No new domain quarantine;
global summarygreen/no required conflicts is not local acceptance.
Current COLDt505121+: Clearwater availableBread0/stock0,21STARVING,activeMeals0;
two new fieldjobs13/15 each accepted128/delivered64/carries64, travelling toward
depot with due schedules and closed ambient authority, no scene. This confirms
progress after prior jobs but not current ability to deliver.
Concrete retained bakery blocker: resident7-3 owner
job:production-food-expansion-c33a0eda34040b661b5a0d5e3254d8e4,
STATION_LOAD, localBlockROUTE_BLOCKED(scopeworkshop), coldBlockerBAKERY_ROUTE_BLOCKED,
coldRouteBlockerNONE, actor133/64/14, station98/65/16, ambientCLOSED/no pending
effect/station and depot custodyRELEASED. BakeryProcess.coldBlocker returns
saved block before evaluating current path. Exact original HOT obstruction and
clearing policy still need source/evidence analysis; no inferred fix implemented.
Depot admission reportsdepotHasFreeSlotfalse but canonical container has only4
engineering tools, fungibleOccupied0/27slots, replicaOBSERVED_CURRENT/custodyRELEASED.
Do NOT describe chest as physically full: reservation/capacity accounting
discrepancy remains unresolved. No players/physical socket currently loaded.
Performance accumulated WAL~2.74h attributed span time/1.329M writes since boot; repeated2s
overload warnings evenCOLD, no measured index speedup or current TPS claim.
Invalid inspect population/production tokens returned non-JSON, helper timedout;
valid inventory issettlement_population and exact jobs useinspect process.
NEXT if fixing authorized: trace persistent bakeryROUTE_BLOCKED invalidation/
COLD retry and destination slot admission vs actual reservations; do not run
another unchanged confidence matrix or call native diagnostic PASSacceptance.

2026-10-03 14:14 DEPLOYED on user's explicit request, existing R17 retained.
Local checkpoint03eac1499cfc8b1726b0e686238baea4303d79ea (no push), implementation
checkout clean; clean detached source/build at
`/home/rd/proj/pm-chunk-service-release.08ycFv/source`.
Build/verifyPackagedJar PASS9s (-x test; affected49 checks already passed).
Exact SHA512 af3a3bb50c81a71c8b3cf899db4964a6f00310677fe3c75d4c0df0053717f07dcfbe2ef385cfe585ad87f7eb46d3575d96a79c89723eeb1514b54a44c6866e14.
Preflight/poststart deploy verifier PASS, same world seed/path, no reset/schema
change. Service start1791018828, launcher3708465/actualJava3708529,
invocation4ee77bdc3d13448b9b3704dc3b16732f, ready14:13:59/runtime14:14:02.
Old server/client JARs archived recoverably by checksum-pinned root installer.
Temporary artifact HTTP3707108 stopped. No publicly hosted pack/remote changes.
PMTestPilot full NeoForge client connected14:14:24, teleported14:14:25 to
Clearwater130.5/68/2.5 (naturally grounds to64), no tick/resource edits.
Native runner98442 still active, clientJava3709660, Gradle3709252,
task-private Xvfb3709101 on :97, auto cleanup via with-private-xvfb wrapper.
Scenario8x120s ordinary wait, then client exits normally (~14:30:25).
Receipt `/home/rd/proj/pm-chunk-service-release.08ycFv/live-clearwater-result.json`,
trace adjacent.pmv3.jsonl; diagnostic-only, not whole-cycle/visual acceptance.
First loaded observation: formerly blocked7-14 CLEAR_ACCESS confirmed14:14:25,
consumed portion by distinct physical receipt same second, independent movement
arrived14:14:27. This supports one repaired case, not all residents/farmers/TPS.
NEXT user's delayed observation/log check; no new matrix or reset needed.

2026-10-03 ACTIVE implementation: user accepted fixes1–5 and chunk indexing;
main alone/no subagents, checkout `/home/rd/proj/pm-f06r3-facility-lane-recovery`.
Uncommitted source extends clean1c46c75a. Live R17/server remains1c46c75a,
Java3424716/invocatione27974274b2d47c29038c032c99a3781; no deploy/reset/push.
Changes: unloaded ambient release uses exact saved observed pose/health rather
than admission-anchor equality, preflights existing owner protocol and refuses
pending meal physical effects/bound food hands. Exact inactive fence stays
before canonical release. Test covers durable receipt/fence recovery and exact
successor adoption retiring the receipt. Removed always-false observed-cache
drain branch; absence/cache alone never awards release.
Ordinary GUARD presentation now admits safe yield, not concurrent COLD;
registered actual assignment capability still governs interruption.
ServiceClearanceTargets enumerates supported exit region; common navigators
resolve alternatives in bounded batches within MovementOrder's existing8-goal
limit. HOT filters current physical availability, no duplicate COLD route
precomputation for enumeration. COLD tries subsequent batches on no path.
Physical idle clearance now covers waiting buffers and selects outside ALL
temporary areas via shared ServiceAreaDestinations, actual availability and
normal navigator, no teleport/food completion coupling.
KnownPedestrianGround road/local views use immutable sparse ChunkSurfaceIndex
with direct16x16 column tables, negative-coordinate/highest-floor tests and
same bootstrap/topology invalidation/terrain fallback. No durability reduction.
Normative contract updated in docs/frontier-v3-execution-semantics.md.
Verification: guardrails PASS; intermediate focused run exposed >8-goal orders,
fixed by bounded batches. Final affected tests/architecture/package run81935
PASS36s:34 frontier checks (including5 architecture),15 adapter checks,
verifyArchitectureContract/verifyFrontierV3ArchitectureDebt/verifyPackagedJar.
Reports: pale-mirror-{frontier,neoforge}/build/test-results/test in checkout.
git diff --check PASS. All Gradle tasks ended; no candidate test server started.
No native candidate/client run or measured speedup yet; product acceptance
of throughput, HOT/COLD continuity and lag remains explicitly unconfirmed.
No task client/Xvfb/background test server remains from prior probes.

2026-10-03 static/runtime diagnosis on unchanged1c46c75a (no implementation):
SOURCE+LIVE: AmbientActorExecutor.releaseUnloadedReservedColdContinuation rejects
non-PATROL departure observed body != original lease.handoffBody (line907).
Actual saved carrier NBT contains6 exact ambientDepartures, no departure conflicts:
7-10 observed135/64/25 vs canonical134/64/21;7-12 135/64/24 vs134/64/20;
farmer7-13 132/64/16 vs119/64/-19;7-18 132/64/17 vs134/65/12;
baker7-3 135/64/23 vs98/65/15;farmer7-7 135/64/22 vs133/64/-16.
These retain HOT authority after unload, blocking ResidentMealProcess.held via
ActorExecutionCoordinator.coldAvailable. drainObservedAfterDemandHysteresis
always returnsfalse, so no alternative closes it. Preserve exact receipt/fencing,
do not simply permit COLD beside unresolved hand/pending effects.

Ordinary short exploratory reconnect13:44:11 (runner55364/private Xvfb89797)
confirmed another active HOT blockade:7-14 TAKE receipt then CLEAR_ACCESS target
135/63/16 rejected native path (end134/64/16), physically remains135/65/14.
Target feet cell135/64/16 occupied by nourished security7-16 at
135.5681/64/16.5933, IDLE/no meal/order, ambientHOT/GUARD. GUARD is produced by
AmbientActorProcess for SECURITY_WORKER, but ActorExecutionCoordinator's ordinary
ambient whitelist only PATROL/WORK/MEAL, so workYield reportsSCENE_OR_AMBIENT_AUTHORITY
and ResidentServiceTurnover.select rejects clearance. Physical idle clearance
only checks narrow occupiedPoint, not waiting buffer; guard is outside that throat.
Meal CLEAR_ACCESS caches single first-exit route without physical availability/
alternative on BLOCKED, keeping service held.18others cannot enter, baker waits.
No need another client to rediscover these active contradictions.

Bounded30s actual-Java3424716 HOT JFR /tmp/pm-stall-hot-20261003.jfr:
1595Server-thread samples,744 first PM frame KnownPedestrianGround lambda;
622 stacks directly MapN.probe/get -> road/local lookup -> supportAt,68 through
SettlementServiceAccessPoints.forDepot. roads cache usesMap.copyOf; TerrainColumn
record hash clustering risk (not independently benchmarked yet). CPU bottleneck
is evidenced lookup path, not proof all past stalls share this single cause.
Current late-stalled HOT tick sample1.8ms does not negate earlier2–12s warnings.
Short probe stopped exact clientJVM3646920 viaTERM after observations; diagnostic
run interrupted intentionally, not acceptance. No server stop/reset/deploy or
source edits. Findings are main's own prior defects too, not external-agent blame.

2026-10-03 13:34 delayed read-only check FOUND recurrence, not acceptance:
pilot ended/disconnected13:21:48 normally, no current players. HOT visit logs
show repeated can't-keep-up warnings through13:22, delays2–12s, and bakery
DEPOT_DELIVERY gate=depot-service-unavailable. Snapshott68635 bread40,18hungry,
3nourished,18active meals/claims, no quarantine/registered conflicts. Farmer1
accepted128/delivered64/carries64; farmer2 accepted83/delivered64/carries19;
farmer3 terminal awaits exact physical delivery receipt, newjob4 active.
Farmer1 hungry at t68781, EAT/MOVE held with mealDue52359 and no movement order;
population admissionUNLOADED while several retained ambient leases still HOT
(7-10/12/18); farmer jobs1/2 RUNNING with old due48889/41916. Some other meals
have resumed COLD schedules, so don't infer all boundary releases failed.
Bakery job production-objective-stock-site-harvest-7-wheat-field-1-part-0-1
at DEPOT_DELIVERY, coldBlockerDEPOT_SERVICE_WAIT; terminal cycle unconfirmed.
This proves local retained-service/non-progress and HOT performance symptoms,
NOT exact source cause. Current COLD tick0.1ms does not refute HOT overload.
No source fix/deploy/reset/new client authorized in this check. Reconnect runner
finished; task-private Xvfb3483987 cleaned. NEXT if user authorizes repair:
static trace of held meal/ambient authority after unload and shared service
admission blockers, plus actual HOT CPU attribution; no reassurance matrices.

2026-10-03 13:05 user requested client reconnect. Same full NeoForge PMTestPilot
connected13:05:17, setup teleport Clearwater130/68/2 completed; ordinary16min
idle stay, no fast-forward/resource edits/assertions. Runner session92185,
task-private Xvfb:97 session38002. Result diagnostic-stay-clearwater-reconnect-result.json
under /home/rd/proj/pm-service-stall-repair.lIFBte. No deploy/reset/source changes.
Client will disconnect around13:21; cleanup Xvfb after it exits. This is an
exploratory HOT visit, not formal native/visual acceptance.

2026-10-03 13:04 read-only Clearwater check on fresh1c46c75a runtime:
same service invocation remains live, no quarantine/required/inventory/custody
conflicts. Pilot completed its16min ordinary HOT stay and disconnected12:49:25;
current observations are COLD, not visual acceptance. All21 residents nourished,
0hungry/0starving, canonical bread43/reserve42, no active meals. Three field jobs
advanced from59/58/57 accepted cells at t35500 to65/64/64 at t37589 (193/252 total).
Farmer1 delivered64 and resumed harvest carrying1; farmers2/3 carry64 and travel
to depot, not terminal yet. Settlement selected PRODUCE_BREAD after first grain
delivery; bakery full-cycle completion not checked. Current tick query20TPS,
average0.2ms/p990.4ms on last100 COLD ticks. Two startup/first-visit can't-keep-up
warnings, no subsequent repeated warnings seen; no measured HOT speedup claimed.
Global process_inventory response exceeds diagnostic limit, not used as evidence.
No source edits/reset/deploy/new test campaign. Task-private Xvfb3426741 cleaned
after confirming client/Gradle gone. Next product check should distinguish actual
HOT behaviour from these advancing COLD jobs; full cycle not declared complete.

2026-10-03 12:33 ACTIVE HANDOFF: user authorized fresh-world reset and requests
test player parked in village; stop after deployment/player placement and wait
for user's log-check command in a few minutes. Same path R17 was recreated from
only its graybox datapack (seed20260918065); old3GB world moved recoverably to
`/home/rd/far-frontier-server/frontier-v3-delivery-barrier-r17-20261002.retired-20261003`.
New1c46c75a artifact42b17c9812e074d1... installed via pinned installer/preflight
with --require-world-absent. Fresh runtime/ready, checksum/port/no-quarantine
deployment verifier PASS. Start1791012700, launcher3424692/Java3424716,
invocatione27974274b2d47c29038c032c99a3781. No source changes/push this turn.
PMTestPilot FULL NeoForge pack client connected12:33:18 and teleported12:33:19
to Clearwater130.5/68/2.5, pale_mirror:frontier_graybox. Server readback confirms
online and actual grounded130.5/64.03/2.5. No fast-forward or resource edits.
Mineflayer probe was rejected for lacking NeoForge; not used as native evidence.
Client uses task-private Xvfb:97 (PID3426741), not human visual acceptance.
Native runner session43030 with ephemeral diagnostic-only scenario
`/home/rd/proj/pm-service-stall-repair.lIFBte/diagnostic-stay-clearwater.json`;
result path same prefix -result.json. Eight120s ordinary idle waits keep village
HOT for ~16min after setup, then client exits. Child client3427962, Gradle3427492/
3427537; Xvfb needs cleanup after observation. No automated assertions/promotion.
Task artifact HTTP server stopped. NEXT: user's requested delayed log check,
not another test campaign. Previous R17 rollback/deploy failure below is history,
not current live state. Main implementation checkout remains clean.

2026-10-03 current authority: user orders fixes and further cause diagnosis,
main alone/no subagents. Implementation checkout remains
`/home/rd/proj/pm-f06r3-facility-lane-recovery`; preserve R17, no reset/push.
After our mistaken launcher-PID JFR attach stopped the service11:39:16, restored
same world/artifact9f492164 at11:59:17. Actual Java3352622 (launcher3352593),
invocation91d5e3a440ad49b3b8c0566756b2a474; fresh deployment verifier PASS.
Successful30s COLD JFR `/tmp/pm-stall-java-20261003.jfr`5.5MB: repeated immutable
geometry assembly/map/set construction dominates sampled planner work; WAL cost
is also substantial but no durability weakening implemented. JFR script now
rejects non-Java targets before jcmd; tested launcher refusal without signals.

Local checkpoint1c46c75a88bb15d4c27856d39bf4854fbae260ea fixes NOT ACTIVE: meal CLEAR_ACCESS routes only to first supported
service-boundary exit, not retained later parking; capacity-blocked field owner
can relinquish service via registered activity capability/common turnover,
retaining cargo/job, including HOT scene yield; bakery saved departure retains
exact MAIN hand and can close its bound cargo without loaded Mob. Two bakery WAL
decoders incorrectly defaulted that MAIN hand to OFF: corrected/constrained
records and roundtrip regressions. Exact fresh loaded-body/hand/fence receipt
can reconcile the old safe-phase conflicted bakery scene, rejecting pending
effects, wrong quantity, duplicate/stale or competing authority. Bounded geometry
views/local support cache invalidates bootstrap/topology/organ/delta identities;
coordinator reuses one boundary per query. No movement/entity teleport or item
issuance. COLD settlement2 confirmed1184 bread/19 starving: nourished2-13 with
64 grain stood at depot waiting for next-batch free slot, blocking meals that
could free capacity. HOT Clearwater7-1 clearance blocked at native endpoint
133/64/14 before retained target133/64/13; exact terrain rejection still open.

Final affected35 domain+28 adapter checks and8 scene-handoff Minecraft GameTests
PASS52s, including HOT-yield/MAIN-hand roundtrip/reconciliation assertions;
guardrails PASS in earlier command, latest diff/package checks PASS.
New receipt registration omissions in diagnostic/process closed catalog caught
and corrected before deployment. Three old cold-harvest test assumptions used
64-cell bounds against252-cell live fixture; switched those existing tests to
their explicit small-field fixture and matching pinned recovery bootstrap.
Broad integration gate stopped after ~5min with multiple retained native failures,
not accepted: old fixtures outside their own bootstrap bounds and several ambient
admission fixtures lacking now-required physical confirmation; patrol progression
failure remains unclassified. Logs retained in main checkout neoforge/build/runs/
core-game-test/logs/latest.log. Corrected post-harvest fixture's translated world
bounds, but did not rerun broad campaign or waive failures. No full release/M3.

Clean detached artifact build/package PASS8s at
`/home/rd/proj/pm-service-stall-repair.lIFBte/source/pale-mirror`, SHA512
42b17c9812e074d1e9c00a720303faf68e6832c9139333d2833772941d47e7fc010d38f9f521735d2c11563534ed6155a23db248cce96e33387e7858667ee629.
Diagnostic same-world deployment FAILED: new registered bakery recovery event
changes process/scene descriptor inventory; R17 header correctly rejects it at
startup. No bypass/migration/reset performed. Restored original9f492164 JAR through
pinned installer; original same R17 runtime recovered12:23:59. Launcher3410181,
Java3410205, invocationa124d27b5f7349bb8be8d7e5fa744b0b, start1791012227.
Rollback deployment verifier PASS: exact original SHA512, fresh ready/runtime,
port25565, no new startup quarantine. Only main checkout's own35 files committed; clean,
no push. Original outer/nested unrelated WIP and governance WIP preserved.
Task HTTP18763/18764 servers and broad gate JVMs stopped; no client created.
Physical DISPLAY=:0 unavailable, ordinary HOT visit NOT performed this turn.
NEXT requires user choice on fresh test world for changed process inventory or
a separately scoped explicit schema migration; do not quietly delete R17 or alter
its header. Preserve JFR and local checkpoint. No measured speedup claimed.

Current main-only task: user says latest depot fix did not help and orders a
systemic fix. New local checkpoint9f492164 implements two further source-proven
repairs and is DEPLOYED to same R17; no push/reset, SA paused/Terra stopped/no subagents. Focused domain
meal/HOT-turn/field-growth/harvest-reconciliation and adapter field-witness/scene
tests (29 domain+30 adapter) plus guardrails/package PASS25s (final command).
Clean detached build/package PASS8s at
`/home/rd/proj/pm-meal-harvest-recovery-release.W5dizK/source`.
Canonical execution semantics updated for meal clearance, plant-generation vs
accepted-work history and exact unbound carried-batch recovery.

On deployed d21f/R17, ordinary loaded evidence showed20/21 nourished, bakery
delivery advanced, stock150 then214; earlier waiting-destination fix has partial
benefit, NOT whole-product closure. Last resident7-19 held one bread in
CLEAR_ACCESS despite supported position already outside service. Meal wrongly
required exact parking before eating. HOT observed exit and COLD canonical exit
now advance to CONSUME independently of parking; consumption still requires its
separate physical receipt, never awards nutrition from exit alone.
All three Clearwater farmers instead have local scene CONFLICT, reason
field-work-accepted-cell-postcondition (new leases r77009/77014/77420). Accepted
total252 crop cells, remaining actor cargo23/19/18 after64 each delivered. Growth
correctly resets CURRENT mature-cell availability; acknowledgePrevious wrongly
treated it as missing HISTORICAL acceptance. Pending-free historical work no
longer checks current accounted flag; actual pending effects retain exact guards.
Farmer1 account custody:field-actor-site-harvest-7-wheat-field-1 verified quantity23,
no claims and bindingCount0: admission failed before carried-hand binding. Owner
reconciliation now atomically reuses ordinary exact hand-projection reducer for
an unbound retained batch. Native adapter requires exact actor/account/lot carry
witness, current owned supported body/full field and no pending crop/delivery/
projection. Existing/stale/foreign bindings never replaced; no resource issuance.
Focused restart/conservation/duplicate/wrong-quantity regression added to existing
reconciliation test.

Deployment: notified rd, save-all flush/graceful stop00:18:35/all dimensions saved,
service inactive/port closed; pinned installer/preflight PASS, existing world
retained. SHA512e57f8ef2e55defe12bf2dfb6cb9c7346f264de93c38f1785a422bcc0768492227831fd62dce674ff14008446a77ad71b096fddeca2f96f5dbe1e07de57f3adf9.
Start epoch1790968737, PID2163928,
invocationbc76584870ec46c4818f59fb6ca653ca; fresh ready/runtime/poststart verifier
PASS. Only intended live server remains; HTTP/native clients/builds stopped.
Main checkout clean/local commit; original outer .f0v-baseline and original nested
governance-redirect WIP preserved, no cross-repository mutations.

Ordinary native PMTestPilot visited live Clearwater at130/68/2, no time changes,
canonical diagnostics only. First launch could not connect: old client pack had
Better Combat/VillagerOverhaul absent on server. Second current-server-mods profile
failed NeoForge early-window handoff. Stopped exact clients, corrected only task
profile fml earlyWindowControl=false; actual server untouched. Native visit then
observed three exact scene recovery events115798/115802/115805, held bread consumed
via separate prepare/observed115804/115806, resident7-19 NOURISHED/meal NONE.
Farmer1/2 terminal deliveries CONFIRMED. First probe disconnected before third
deferred receipt; job already canonically returned, no new scene conflict. Extended
same temporary diagnostic to require ALL three final receipts, revisited same world:
all CONFIRMED, third observation:field-deferred-site-harvest-7-wheat-field-3 at
r118665/t86706. Other residents7-8 etc also physically consumed bread.
Successful exploratory native receipt (not a formal full-cycle/M3 promotion):
`/home/rd/proj/pm-meal-harvest-recovery-release.W5dizK/live-recovery-probe-all-deliveries-result.json`,
run9435a27c-d4a5-4f9d-ac8e-cfa298446e05; linked PMV3 trace, temporary exact-world
scenario and retained live-server-postfix.log alongside. No permanent speculative
scenario added. Finalr119324/t87109 green, no required/inventory/custody conflicts,
resident7-19 NOURISHED971/meal NONE, third receipt CONFIRMED. New field jobs4/5/6
already exist; prolonged multi-cycle behaviour NOT verified. Known targeted saved
meal and all three farmer deliveries recovered; do not generalize global green
to every future/local process. Next only concrete recurrence/product follow-up,
not reassurance matrices or a world reset.

Latest user asks to diagnose/fix the live depot stalls. Main-only local checkpoint
d21f535b9475ebc010481daae704e758d03f5b3e is now deployed to SAME R17, no reset/push.
Observed before fix: field jobs accounted252 cells, each delivered64, remaining
23/19/18 carried;14 hungry/42 bread; repeated meal CLEAR_ACCESS/MOVE path waits
and bakery DEPOT_DELIVERY waiting for service. Global summary green is not proof
of local progress. Source-proven defect: meal waiting selection ignored reserved
clearance destinations and actual occupied HOT waiting spots, and cached routes
could remain aimed at those spots. Shared ServiceDestinationClaims now supplies
read-only current/reserved final-position exclusions to clearance and waiting
selection. HOT queries shared collision/clearance provider on naturally loaded
targets and invalidates a blocked waiting target; COLD interrupts conflicting
waiting approaches at ordinary dispatch, retains portion claim and as-of body.
Intermediate COLD approach boundaries are NOT exclusive destination claims:
the initial implementation incorrectly treated them as such; existing cohort
test caught mutual replanning, corrected without weakening the test.
Final relevant domain16 + adapter1 tests PASS22s; guardrails/package PASS20s.
Detached build/package PASS8s at
`/home/rd/proj/pm-service-destinations-release.3o3Y6p/source`.
SHA512 d5f98d02be59eef738a25b7284f2c63436e4ce2960d5ff3c3e7c7e3dc9ac52814d0f191b3353daef6fb5299c0c9b78c4df02c93b2054302cc5a766f5d919691a.
Notified online rd; save-all flush + graceful stop00:02:02, all dimensions saved
00:02:04/service inactive/port closed; exact checksum-pinned installer/preflight
PASS. Restart epoch1790967737, PID2129066,
invocationf63de89c646b42409b69c4a860341ef2; ready00:02:27/runtime00:02:29.
Post-start verifier PASS; r73046/t59848 green, no required/inventory/custody
conflicts.21 retained ambient bodies UNKNOWN awaiting ordinary loaded recovery;
no player after restart at this reading, feeding still21 hungry/42 stock.
This proves implementation/deployment, NOT sustained HOT service turnover or
full farmer final delivery; must inspect natural loaded recovery on this exact
candidate. Temporary HTTP/builds stopped; intended live service remains. Original
outer/nested WIP preserved; implementation clean/local commit only.

Latest follow-up: user orders fixing pending-delivery recovery too. Implemented
local commit6f1f2c23 (main only): closed SceneBehavior registration now requires
an explicit ReleaseBarrier. Generic stored-recovery and both release overloads
consult it before disk-derived departure/draining/binding/hand inspection. Harvest
owner blocks only the scene named by its saved delivery witness; other farmers
on same field remain independent. Pending bakery-step guard moved from generic
release into production's registered barrier, also covering stored recovery.
No resource writes, fake acknowledgements, new WAL event/schema or world edits.
26 focused adapter/recovery tests pass;8 scene-handoff GameTests pass23.74s,
including actual generic release with saved pending witness before continuation
lookup; final guardrails/package plus focused/native command passes49s.
Implementation clean/locally committed, not pushed; detached package build/verify
PASS3s at
`/home/rd/proj/pm-delivery-barrier-release.gO6NjH/source`.
User chose NEW WORLD explicitly.6f1f2c23 deployed, SHA512
`ec0ce7da0548b2c1790123a2e735619ee43c34b0d24d54b78c2b5b8f5fa155600bd973c0e97a1bb782a5672e668742da5880687bad6821c549ff7c270d5c3b52`.
R16 gracefully stopped23:11:30, all dimensions saved/service inactive/game port
closed; moved recoverably to exact
`/home/rd/far-frontier-server/.retired-test-worlds/frontier-v3-three-farmers-r16-20261002`.
NEW selected world `frontier-v3-delivery-barrier-r17-20261002`, seed20260918065;
copied only graybox datapack, no state/chunks/entities/players. Preflight includes
require-world-absent, passes; pinned PM-only installer verifies checksum. Started
epoch1790964717, MainPID2037556/invocation7cb9136d63e14b728503f0c2093f7dd6;
fresh Done23:12:08, runtime startup/post-start verification PASS, port25565.
Summaryr439/t153 green, required/inventory/custody conflicts0;12 settlements,
366 residents/12 sites. Clearwater site7 epoch1/GROWING stage0/no active work;
field118..133 by16, first118/64/-19, last133/64/-19, board117/67/-11;
food64 available/21 nourished/no hungry. View near125/66/-11 in graybox dimension.
HTTP/build/install completed/stopped; intended live server remains. Source clean,
not pushed, original outer/nested WIP untouched. Diagnostic deployment/bootstrap
evidence, NOT a full graphical multi-cycle or graceful pending-delivery product
acceptance. Recurrence guarded; archived R16's existing conflict NOT healed.
No separate historical-world conflict-repair API introduced. Existing delivery continuation requires HOT/RUNNING; the existing
harvest-scene reconciliation supports only HARVESTING+bound physical wheat,
not the CONFLICT+empty-hand+unconfirmed depot effect saved here. Current fix
retains the unknown physical owner until ordinary loaded evidence lets its
existing owner reconcile; no full unloaded-delivery/native whole-cycle claim.

Latest user authorizes investigation, fix and deployment of stalled farmers.
Confirmed whole-runtime quarantine21:25:08.569+05/r81533: bakery DELIVERED exit
navigation submitted BakeryHotBlockChanged, rejected "delivered bakery job cannot
be blocked". Fixed local commit a7b17d6e1829416b44d20d20a2fb0ccc23d1caeb:
BakeryWorkSceneExecutor owns typed navigation outcome; DELIVERED clearance never
changes production block, traces the wait and keeps ordinary navigation/observed
exit. Active work still blocks/clears normally; no catch/suppressed rejection,
fake arrival, resource reissue or domain-invariant relaxation.12 focused checks
pass (1 adapter regression,4 HOT,7 COLD;97s total), guardrails/package15s,
clean detached final build16s. Source clean, locally committed, not pushed.
Detached `/home/rd/proj/pm-bakery-clearance-release.HLGxhD/source`; previous
incident log retained alongside it. SHA512
`232002d2945d250e5284a2370e4ce5fe21e8bad056b46f867b7cbb2af87623674330af87e0b3ccb842b435c06564fb5f5fbcf6a79a39f0dc71eb82540f611f29`.
Graceful stop21:40:01 saved all dimensions/closed port; pinned install/preflight
pass, SAME R16 world preserved (no reset). Restart epoch1790959231,
MainPID1876064, fresh startup/post-start verification PASS. Summary initially
r81710/t64852 green; simulation subsequently progresses beyond original failure.
Not full product acceptance: post-restart recovery21:40:55 raised ONE local
Clearwater carrier-fence conflict; summaryr82579/t65422 blocked, not quarantined.
Exact next unresolved cause established from read-only entity NBT and ledger:
resident7-7/job7 UUID b96f2000-c03f-3032-bc4f-cac3de52fc7e is saved at134.2124/65/13.5131
with EMPTY offhand; fieldDeliveries retains exact64-wheat intermediate witness
`witness:field-delivery-site-harvest-7-wheat-field-7-part-0`, outputslot5,
successorslot11, actorEpoch80957/depotEpoch3. Delivery's physical hand-clear
preceded canonical confirmation when bakery quarantine stopped processing.
SceneStoredRecovery proves saved body then drains/releases it before pending
family-owned delivery reconciliation. Release expects bound wheat in saved hand,
therefore raises bound-hand-release-saved-hand-unavailable, site/scene/intent
CONFLICT. This is a pending-effect ordering defect, NOT proven lost grain,
bad NBT parsing or farmer navigation. Do not restore64 wheat or clear incident
blindly. Next repair needs registered family-owned pending-effect recovery barrier
before generic release and exact chest/hand witness reconciliation; current
delivery continuation only accepts HOT/RUNNING, so persisted conflict cannot be
repaired by simply waiting/restarting. No repair of that second issue yet.
Diagnostic deployment only; no graphical full-cycle acceptance. HTTP stopped.

Current delivery92617ea6ff8d6709a6ddf893d06bc0e1e7a641ed includes three-farmer
bootstrap9e664329 and a common join-bridge repair. Source clean/locally committed,
not pushed. FrontierV3AmbientPendingAdmissions.reclaimProjected formerly recognized
only ambient ownership: a cached exact body adopted by a scene could never release
its bridge entry, and resumeDemandedClosedSceneReturns skips that entry forever.
Now indexed same-object recorded scene custody (exact lease/member/revision and
current ledger binding), including retained CLOSED custody, releases only the
ephemeral bridge. No teleport, cargo change, fence consumption or save acknowledgement.
11 focused handoff/recognition/recovery tests pass20s; seven scene-handoff GameTests
pass20.69s (42s build/lifecycle total), including retained body/pose/17-wheat cache
regression. Guardrails/package build pass15s; detached final build pass16s.
Detached source `/home/rd/proj/pm-farmer-bridge-release.jmIYz2/source`; JAR SHA512
`3a01d9bca50e5f899b699ffa816933ea2ef1453e1d27026a7c805469cf2a9ae20066b688bf39daf516facd1d165e2be1941831a29bb8ef2bba39e37fe7887d9e`.
R15 gracefully saved all dimensions, port closed/service inactive before move to
`/home/rd/far-frontier-server/.retired-test-worlds/frontier-v3-graybox16-r15-20261002`.
Its diagnostic-bridge-incident retains exact actor/life/process/scene JSON and log.
Fresh selected R16 `frontier-v3-three-farmers-r16-20261002`, seed20260918065,
copies only datapack, not entities/state/player files. Preflight passes; pinned
installer verified JAR. Restart epoch1790955050; post-start verification PASS,
service MainPID1753916, port25565. Fresh tick291/revision439 summary green,
no required or inventory conflicts;12 settlements/366 residents/12 sites.
Clearwater site7 GROWING stage0, field118..133 footprint unchanged, board117/67/-11.
Temporary artifact HTTP stopped. This is diagnostic delivery, not full milestone
acceptance: no graphical full-cycle harvest/eat/return proof for this source yet.

Current live diagnosis before requested three-farmer deployment: deployment/reset
paused on user's Clearwater corner-stall observation. Read-only R15 tick29440
shows resident:7-7/job1 HUNGRY, canonical135/65/14 with EAT/TAKE held, but exact
physical UUID b96f2000-c03f-3032-bc4f-cac3de52fc7e remains118.5/63.9375/-3.973
on field edge. NBT still declares SCENE_LEASE, closed lease
lease:site-harvest-7-wheat-field-1-crop-5-r4920, revision4920, epoch2,
passive carried witness17 grain. Admission UUID_CONFLICT means an existing
body lacks current ambient/open-scene recognition, not proof of duplicate UUID.
Resident:7-1/job2 continues normal crop progress24->26 during observation;
no quarantine or site conflict. Exact cause of failed retained-body handoff
still being traced; no fix or live mutation for this observation yet.
Three-farmer source9e664329 committed/clean,17 focused checks pass24s and
guardrails/package plus clean detached build pass15s/16s, NOT deployed/pushed.

Publication update: user explicitly authorized commit/push. Implementation
`13aa350212cb592fb409f16db6c40cf3a39c6b82` successfully pushed to
`origin/feat/baker-carry-orders-20260926` (remote advanced47975452..13aa3502).
Implementation tree clean. Earlier no-push notes below describe prior authority;
unrelated governance and original outer/nested WIP were not committed or pushed.

## Current assignment and implementation

Newest diagnostic delivery441b5ff8950d6575a3f06c016941c25c2d5c9828 is installed
from clean detached `/home/rd/proj/pm-graybox16-release.X6aAGT/source`; final
guardrails/package build passes16s. SHA512
`87b9d45564a4e9010e9de452ca166dc17a787494dc4afdb02843f918b9810df5fad2f92179a5c707262b57b5bd22824aada2a0d7e622e1e1ffa0400e146602cc`.
New selected world `frontier-v3-graybox16-r15-20261002`, same seed20260918065.
Old R14 moved after successful graceful save to exact recoverable archive
`/home/rd/far-frontier-server/.retired-test-worlds/frontier-v3-shared-work-r14-20261002`.
Only its datapack copied, not chunks, residents or simulation data. Preflight
requires absent level.dat and passed. Post-start verification passes; service
MainPID1700238, restart epoch1790953488, port25565. Fresh tick223/revision427
summary green/no conflicts,12 sites/366 residents. Clearwater site7 first crop
118/64/-19, last133/64/-19 (16x16 footprint), board117/67/-11;
human viewpoint near125/66/-11 in pale_mirror:frontier_graybox. Artifact
HTTP stopped. Source tree clean; new commit local, no push for this increment.

User requested16x16 graybox fields and explicitly allowed retiring the old world.
Implemented441b5ff8: default16x16 footprint,252 crop cells plus4 internal water
sources covering every crop by vanilla hydration range; two farmers unchanged.
Generic field/custody/navigation remain unchanged. Isolated one-batch fixtures
explicitly retain64 cells rather than depending on live bootstrap size. Focused
30 model/planning/concurrency/harvest tests pass32s including current live seed
20260918065 placement of all12 fields; guardrails/package gates pass20s.
Clean detached final artifact build pending. Target new world
`frontier-v3-graybox16-r15-20261002`; old R14 will be moved recoverably out of
active use after graceful stop. No push requested for this increment.

User explicitly requests completion, not another intermediate stop. Main alone
implements the accepted shared-work order (two exact farmers and two bakers);
no subagents, no active goal. Implementation checkout remains
`/home/rd/proj/pm-f06r3-facility-lane-recovery`, Gradle under `pale-mirror/`,
branch `feat/baker-carry-orders-20260926`, HEAD13aa3502 (spatial field work selection).
User confirmed local commit without push plus a new-world diagnostic deployment,
and volunteered the physical product test. Feature source is committed/clean;
no push. Original outer/nested histories and unrelated WIP remain untouched.

Latest diagnostic spatial-work update13aa3502 is installed from clean detached
checkout `/home/rd/proj/pm-spatial-field-release.WDBD9N/source`; SHA512
`033d8054a9e8f6ac63d5b36e2a002c0f26a548974dceee524011c78de609cdd7700f941a6e24feb679a670477f6ac825f7df23155a900cd93aa298be74bf5d30`.
Clean final detached guardrails/package build passes16s. Same R14 world gracefully
saved/retained; preflight and post-start verifier pass. MainPID1669250, restart
epoch1790952545; live tick79717/revision67435 green,0 required conflicts and
0 inventory conflicts. Temporary artifact
HTTP process stopped; no source WIP or push. Current diagnostic visual spacing
check belongs to user; in-flight targets are retained, next target selections change.

Previous diagnostic correction97eec929 was deployed from clean detached checkout
`/home/rd/proj/pm-carry-handoff-release.a50Z2i/source`; checksum
`d4e254c94d9ecd34edf046420f445f2650e8d541c1573e70ac5e3d41ba6e5d5b6e9c1a1cec32c57ed8a0be28bfac4546243932269ac99e44f3ebee791c47c396`.
Preflight and post-start verification pass; PID1606041, epoch1790950469,
same R14 world retained. Temporary artifact HTTP process stopped. Live tick37132
summary green/no conflicts; both named farmers alive/NOURISHED with no meal waits,
IDLE and unloaded (no observing player). This confirms healthy canonical state,
not observed loaded body continuity; prior COLD progress cannot be attributed
solely to the correction. Human recheck of next harvest-to-meal handoff remains.

Previous feature delivery: clean detached checkout
`/home/rd/proj/pm-shared-work-release.pNeJqB/source`, guardrails + NeoForge build/
verifyPackagedJar passed16s (`/tmp/pm-shared-work-detached-build.log`, test tasks
excluded to reuse existing affected results, not a fresh complete acceptance).
Preflight and post-start deploy verifier both pass. JAR SHA512
`21cb2c5419be3f43ccdf1e46f8698182718310a3f4daf585a1d149d9ed4f143934b78c4a63ec68d222a077e16c4e491314c1e065539e867177532ac5671fa444`.
Service `far-frontier-v3-live.service`, MainPID1548740, port25565, restart epoch
1790948626. Fresh world `frontier-v3-shared-work-r14-20261002`, seed20260918065.
Old R13 world preserved, old installed JAR archived by the pinned installer.
Old server saved all dimensions before stop; temporary artifact HTTP PID1548061
stopped. Only intended live server remains. No feature completion/HUMAN_CANDIDATE
promotion: native bakery frame/handoff and unrelated failed GameTest gate remain
open. Human diagnostic access is ready, not a claimed green release.
Read-only live summary at tick917/revision432: green,0 required conflicts,
12 settlements/366 residents/48 bioforms. Clearwater site7 growing stage0,
no jobs/conflict yet; first crop137/64/-6, board140/67/2. Human test field near
140/66/-2; bakery near100/66/14, in `pale_mirror:frontier_graybox`.

Human test revealed both Clearwater farmers disappearing while harvesting.
Same deployed identity; live tick25085/revision10373, no quarantine/conflicts.
Both jobs1/2 remain RUNNING, farmers7-7/7-1 alive with cargo18/1; both activityEAT,
mealMOVE/held and ambientPREPARED, physical admissionBLOCKED, no physical body.
Source: harvest shouldYieldAtOwnerCheckpoint calls beginImmediateColdRelease;
accepted SceneReleaseExecutor release discards retireLoadedBodies (line279),
then ambient must rematerialize. Generic aboveExactFloor rejects non-air crops
with empty collision shape; harvest provider separately permits CropBlock.
Thus visible self-care handoff uses despawn/recreate and a mismatched placement
policy blocks the successor. Fix97eec929 removes cargo's override of registered
observed-body retention. Existing atomic hand-release and confirmed passive carry
witness preserve the same loaded body/stack for successor adoption. Common standing
admits collision-free CropBlock for every activity; harvest wrappers delegate it.
Solid obstruction/support/fluid/occupied-position checks remain. Focused custody/
handoff/recovery tests pass13s; all30 local-navigation GameTests pass23s including
crop admission, exact UUID/health, no HOT reprojection and physical blockers. The
known one-cell trace assertion now requires semantic arrival/nonzero motion/envelope,
not an impossible three-sample minimum. Guardrails/package gates pass15s and clean
detached build16s. Same R14 world saved and retained; diagnostic redeploy verified,
new servicePID1606041, epoch1790950469. No full milestone acceptance or push.

User accepted reducing farmers' mutual crossing through soft spatial work selection.
Implemented13aa3502: generic AreaWorkSelection spreads admission seeds farthest
from peers' current work targets, then prefers nearest targets in the worker's
local region with unrestricted fallback. Field owner supplies eligible cells and
exact reservation views. Common selection used by admission, normal HOT/COLD crop
continuation, batch return and observed selected-cell loss (actual worker origin).
No navigator, persisted claim/territory, job schema or in-flight target changes.
Focused26 tests pass39s, including registered two-worker spread/progress and journal
recovery, irregular/non-flat target pool and leftover-sharing. Final selected-cell
loss origin adjustment passes12 existing process tests18s. Guardrails/package build
passed15s before that adjustment; clean detached final package build passed16s.
No visual spacing acceptance yet; user owns manual diagnostic check. No push.

Current model: independent harvestJobs and intent-keyed harvestLineages; explicit
cell/layout/generation claims, per-job labour and output history, independent
actor custody. No production primary activeWork reader exists. Plant maturity
renews availability independently of cargo delivery. Snapshot224 and WAL envelope97
are fresh-world-only. Domain, adapters, fixtures, diagnostics and pilot compile;
retired whole-field stage-prefix projection/restart authorizers no longer run.
Shared ResidentWorkProvider/Offer and typed claim views are consumed immediately
by field and bakery admission; canonical jobs/resources remain reservation owners.

This increment closes a real dispatch gap: station release previously permitted
a second baker only in a manually inserted-task fixture. Registered settlement
planner expansion now creates an executable task under the existing food objective,
and commits nothing on refused admission. Confirmed HOT/COLD station release wakes
policy. Active field tasks admit newly available farmers without a new objective;
growth, exact terminal completion and resident availability wake selection. The
shared arbiter does not inspect crop/recipe/machine phases. Bakery completion
planning was extracted to its family owner to preserve the1000-line guardrail.
Resolved history pruning now requires exact family proof of no open scene or
unresolved intent, rather than receipt resolution alone.

Evidence: registered-engine two-farmer admission/progress and transaction recovery,
cell-generation snapshot recovery, and second-farmer joining an existing task
through normal settlement policy pass. The second-baker test now obtains its task
from registered stock reconsideration; both jobs finish independently after snapshot.
Latest focused domain/persistence/process checks and style/size/debt gates pass23s
(`/tmp/pm-multiworker-integration2.log`). Earlier full guardrails ran other checks but
stopped on size/style; both defects were corrected. Native/player acceptance is
NOT established and nothing has been deployed.

Full frontier integration completed1110 tests with95 failures;76 share one exact
new causal-ID overflow. WorkOpportunityIdentity now uses a128-bit SHA256 prefix,
preserving identifier validation and room for nested scheduler envelopes. Remaining
failures included obsolete single-field/reset assumptions and changed permission/
availability fixtures; scoped migrations retain physical/recovery fences. External
crop loss now closes only the selected old execution claim, leaving the replacement
generation repairable; no global accounted-cell tombstone. Unknown-ground readiness
queries no longer invoke the effect API. Full-hand delivery no longer compares one
worker's progress to collective field accounting. Focused domain/adapter and style/
size/debt pass30s (`/tmp/pm-multiworker-focused3.log`); remaining fixture/market/activity
checks pass93s (`/tmp/pm-multiworker-ingress-final.log`). Prior repair66 tests had
three stale expectations; each corrected and included in the30s green selection.

Historical native graphical session69100 (failed and saved/stopped), `/tmp/pm-multiworker-native-ground-exit.log`, checked-in
`disposable-concurrent-field-harvest-restart.json`, profileconcurrent-field-harvest,
seed126, isolated port26620. Physical DISPLAY0 access verified using its existing
Xwayland authority. Fixture retains two normally admitted jobIDs1/2, workers1-7/1-1,
distinct CellIds1/2, and both exact continuations; no body/physical effect injected.
Oracle accepts an explicit test-only jobId. Scenario observes both HOT executions,
ordinary COLD departure, graceful restart, both resumed owners and exact terminal
intents/stock, with a linked frame. No native result yet, no live deployment/reset.
Two earlier native attempts were not accepted: the first camera was airborne
without flight; the second used spectator, explicitly excluded from HOT demand.
Both isolated servers saved all dimensions and stopped; failed evidence retained.
Current scenario uses a grounded creative observer, not a spectator or forced lease.
Ground-camera native attempt passed job1 HOT progress but rejected job2:
labour complete, exact goal reached, no pending crop, PREPARED intent persisted.
Static cause: fair two-lease rotation aliases with global gameTime%10 effect
admission (and %20 blocked-goal retry), permanently withholding selected workers.
Removed those redundant phase gates; existing labour/due/exact receipt fences
remain. Isolated server saved/stopped, evidence retained; no native success claim.
Native environment rebuild/style/size passed9s in session36695,
`/tmp/pm-multiworker-phase-fix-build2.log`; initial task-name typo was corrected.
Phase-fix native run is session87212, `/tmp/pm-multiworker-native-fair-phase.log`.
Scoped adapter regression is session68747, `/tmp/pm-multiworker-phase-regression.log`.
Scoped adapter regression passed17s. Subsequent native attempt exposed a fixture
preparation error: second intent was inserted through storage only, omitting its
recovery authority. createConcurrent now invokes the same field reducePrepared
owner as the first worker; no recovery validation weakened. Failed isolated run
saved/stopped normally, no live change. Build/native preparation refreshed in
session40914; its focused domain test executor1416426 was stopped explicitly to
avoid concurrent writers to the still-running full frontier test reports. It is
not green evidence. Production lifecycle helper now drains retained already-due
actions at current time and asserts ACTIVE instead of falsely requiring future dueAt.
Before that scoped run replaced adapter XML, the full adapter gate had673 tests,
148 suites, zero failures/errors/skips (read at12:36:11UTC). Full domain still running.
Final Java integration gates are session39351,
`/tmp/pm-multiworker-terminal-gates.log`; finished16m31,1110 domain tests,
10 failures. Three lifecycle-helper future-due assertions, six old supply-1-18
fixture references (actual ordinary operation now supply-1-14), and one testFixtures
class loading failure caused by concurrent recompilation. Never compile shared
class outputs while a test/runtime consumer remains active. Scoped residuals
session83553, `/tmp/pm-multiworker-terminal-residuals.log`, run only these changed
classes; preserve1100 unaffected passing results, not another full reassurance run.
Authority-fixed native attempt passed both independent HOT oracles. Reviewed
actual frame: one worker partly occluded by building, insufficient overview.
Exit visit failed because actual overworld player feet wereY63, not requestedY64
(read directly through exact isolated RCON). All dimensions saved/stopped. Current
scenario usesY63 exit and unobstructed-side field camera; no domain change for it.
Latest native run: actual two-worker frame reviewed (e7b1d9a8 linked frame7);
both HOT oracles pass, ordinary COLD departure passes, graceful all-dimension
save/restart passes, both exact owners resume and pass post-restart HOT oracles.
First terminal delivery passed; second quarantined on duplicate field trace correlation.
Exact isolated server saved all dimensions and stopped; disconnected client1430877
was stopped after save. No native process remains from that run. The defect was
real: terminal traces used one not_captured correlation and selected their owner
by that correlation. Updates now require exact site/job/intent, and pending traces
retain unique execution correlations. A focused regression checks that updating
one of two terminal histories cannot rewrite the sibling. Native recheck pending.
Guardrails plus verifyPackagedJar passed20s (`/tmp/pm-multiworker-guardrails.log`).
Scoped residuals completed69 tests with2 failures: the old lineage helper omits
WorkChanged reduction; route lot expectation names first production but actual
cargo belongs to the independently admitted bakery output. Terminal logistics
and WAL diagnostic pass; no more class loading failure. Correct these fixture
expectations after the source-frozen native run, not a new full matrix.
Scoped residuals session58717 passed49s: lifecycle/concurrent/route classes and
native environment preparation. Node route-fixture checks pass5/5. The remaining
lifecycle assertion expected the retired whole-field predecessor pinning policy;
accepted independent admission does not imply a successor edge or fixed worker.
The test now requires exact admitted ownership and no invented historical link.
Final native recheck session7701 passed, log `/tmp/pm-multiworker-native-exact-trace.log`,
manifest `build/frontier-v3-scenarios/concurrent-field-harvest-20261002-exact-trace.json`.
Both HOT oracles, COLD departure, graceful restart/resumption and both terminal
deliveries/CONFIRMED intents passed. Exact workers delivered33+31, depot128
including64 initial wheat; no remaining claims. Framef8cfa817 was opened and shows
two farmers on the same field. Isolated server/client exited; ports26620/26621 free.
Full release command session59564 completed437 GameTests/16 failures in6m32;
not a green release gate. Several fixtures place bodies at random GameTest
coordinates millions of blocks outside their unchanged 1024-domain bounds;
restored-body fixture uses forbidden direct PREPARED->HOT without body receipt;
one one-cell motion test demands three samples after valid earlier arrival.
Route-patrol advance/release and physics failures remain unclassified, not waived.
Node pilot selection58 tests/56 pass/2 fail: obsolete settlement-provision profiles
in catalog tests/scenarios, unrelated to new concurrent profile. Do not silently
restore retired writers or label the whole pool green.
User requests assessing usefulness/correctness of test pool. Protocol now requires
contract/setup/isolation/assertion classification and explicit retirement reason;
no production relaxation for invalid fixtures, no automatic full rerun.
Static feature audit found a real omitted consumer: workshopText rejects two jobs.
It now keeps single-order detail and renders a bounded multi-baker phase summary,
including delivery/return separately from station work. Existing second-baker
vertical asserts the board with both executions; scoped readability+vertical
checks pass24s in62589, `/tmp/pm-multiworker-bakery-board-final.log`.
Native bakery session7921 completed the physical station/restart/64-bread terminal
flow, `/tmp/pm-multiworker-native-bakery.log`, but its reviewed frame showed sky:
visual acceptance rejected. Camera had been outside the workshop floor.
Grounded-camera recheck31634 failed visible-entity assertion: exact baker was
three blocks away but line of sight was false. Evidence retained in
`/tmp/pm-multiworker-native-bakery-visible.log`; server saved all dimensions and
stopped, no pilot server/client remains. No product cause claimed from this
camera failure, no further native rerun launched. Two-baker physical handoff
and valid station frame remain open. Live service/world still untouched.
Test-pool assessment is recorded in the active shared-work order: keep exact
ownership/claims/recovery and genuine integrated dispatch coverage; repair
source-proven invalid fixtures/assertions, name obsolete profile replacements.
Route-patrol fixture already translates bootstrap; its failures cannot be
dismissed using the ambient coordinate diagnosis. Full failed gate remains open.
Do not edit implementation while a source-identified recovery run is active.
Do not call a compiler/helper/test checkpoint feature completion or end work there.

## Prior increment notes (superseded by current state above)

Current migration checkpoint: ResourceSiteLifecycle retains independent
harvestJobs and exact intent-keyed harvestLineages; no primary activeWork read
projection exists. Domain readers, assignment, custody/capacity views, labour,
scenes, conflict fan-out and snapshot maps are migrated; schema223 is fresh-only.
Field planning now offers both permitted workers against immutable successive
admissions, reserves distinct selected slots and unique execution-sequence IDs.
Per-job pool exhaustion may finish before field-size count; first delivery does
not reset plants or complete a parent task with a retained sibling. Participant
admission is distinct from exclusive station admission; farms are no longer
whole-facility locks. Main compileJava passes3s.
An ephemeral direct production-planner/reducer probe admits two farmers to
different cells, then after134 bounded due turns retains confirmed yields1/4
in two exact actor accounts; snapshot roundtrip retains both jobs and resources.
This is focused domain evidence, not registered-engine/native acceptance.
The first probe exposed a remaining epoch-based job-ID factory, now changed to
the retained admission sequence; a probe-only Cancelled accessor typo was fixed.
Active physical continuation lookups now name the exact job/action rather than
choosing one site action. Adapter migration remains unfinished: old prefix/
projection/restart and diagnostic consumers still reference removed accessors.
No whole-project compile or current tests claimed. Earlier green checks below
are pre-replacement evidence. Cell-generation claims, renewable eligibility,
generic opportunity/claim ports, history retention closure and fixture/native
integration remain mandatory. No deployment, reset, commit or push; no task
build/probe process remains after the completed local checks. Live unchanged.

Current user order: implement shared reusable work opportunities, exact target/
resource/station reservations and independent resident execution, with SOLID
boundaries. Two farmers per current field and two bakers per bakery; one bakery
workplace remains exclusive, unavailable work must not retain the other resident
in a waiting job. Main alone, no subagents. Accepted scope and finite criteria:
[work opportunities](docs/frontier-v3-work-opportunities.md). Source audit found
single active harvest job/site, previous-worker epoch pinning and independent
first-baker selection. The latter two selection restrictions are now removed;
multiple simultaneous field executions remain the next unresolved source cut.
Implementation begun; no multi-worker completion/deployment claim. Prior13 local
commits through47975452 have been pushed to the matching feature branch; the
older "no push" notes below are historical, not current branch status.

Latest implementation increment (uncommitted): DecisionAuthority now owns typed
persisted ResidentWorkPermissions; genesis explicitly authorizes two exact
farmers and two exact bakers per settlement. Profession alone grants no work.
Permission-aware workforce/selection, field admission, bakery admission and
per-worker company employment are wired. Migration removes origin permission
atomically; revocation blocks new work, not an admitted execution. Schema222
rejects prior test-world snapshots, without compatibility fallback.
Harvest part identity is now exact job+delivered offset; actual crop receipts
advance that job's confirmed yield history, while the common ledger alone owns
current hand stock. HOT/COLD terminal and batch delivery use the existing generic
actor/container transfer, explicitly OFF hand for harvest. Terminal conservation
uses the job's own output history, not collective field yield; retirement checks
only the currently transferred part, not earlier stock a baker may have consumed.
BakeryWorkState owns station reservation lifetime through confirmed UNLOAD;
DEPOT_DELIVERY/DELIVERED retain the worker/cargo but release the station.
ProductionFacilityReservations derives occupancy from those owners and validates
hydration as well as starts; the generic settlement coordinator has no bakery
phase branches. Focused53 domain/codec/admission/architecture tests pass45s;
adapter compiles and style/size/debt checks pass. An old bakery hand fixture was
corrected to declare MAIN rather than default OFF. Actual second-baker admission
while the first holds finished bread passes with both exact jobs/custody retained
through snapshot, then both independent jobs reach terminal delivery and task
completion (focused extension19s). Expected temporary depot-service refusal is
handled as a rescheduled opportunity, not a missing mandatory transition.
Remaining main cut: replace single active field job with independent executions,
per-cell generation claims and exact retained histories; migrate all active
scene/schedule/physical/custody/closure callers coherently. Generic opportunity/
claim composition and native two-actor acceptance remain open. No concurrent
farmer result, commit, push, deployment or test-world reset in this increment.

2026-10-02: main alone implements resident-life/resource cut; Terra stopped,
subagents prohibited. Active implementation monorepo:
`/home/rd/proj/pm-f06r3-facility-lane-recovery`, Gradle under `pale-mirror/`,
branch `feat/baker-carry-orders-20260926`, HEAD479754528b420611132a93cd4e948bca7fbd7c5d,
implementation WIP for shared work selection: prior shared resource-operation labour clock and resident work stats;
immature-cell exclusion locally checkpointed, no push.
Governance here is canonical. Preserve both original Minecraft Git histories
and unrelated WIP. SA audit paused; ARC-001 remains the adoption checkpoint after
current F0.6R3 repair, not authority to expand scope. No push requested.
Standing verified diagnostic deployment applies; preserve current R12 world separately.
User explicitly accepts creation of a new test world for this feature.

Work-opportunity increment in active checkout: ResidentWorkSelection owns shared
read-only eligible candidate filtering; BakeryJobAdmission receives an exact
selected resident and no longer rediscoveries the first baker. Production start
checks shared facility occupancy before claiming anyone and tests family finance
against each eligible proposal. Company foundation opens distinct per-resident
baker contracts, not founder-only employment. This changes generated employment
IDs; before feature deployment the final fresh-schema boundary must reject old
saved contracts. No deployment/reset/commit/push in this increment. Two-worker
permissions/bootstrap, true per-cell concurrent harvest executions, provider/
claim protocol and HOT/COLD/recovery integration remain; do not call this complete.
Verification of this wired increment:53 affected tests (bound admission8,
production31, company3, activity6, architecture5), adapter compile and style/
size/debt checks pass20s. Final same-byte check reused dependency-valid cached
results in3s after restoring namespace:path-compatible contract identifiers;
the intervening invalid nested-colon spelling was corrected, not accepted.
Market-backed regression uses the registered engine, snapshots the result and
checks the second exact employee/order while the preferred body remains in
UNKNOWN_AFTER_RESTART. Busy-facility case leaves the other baker idle and stock
unchanged. No native/graphical multi-worker acceptance. Task Gradle runs complete;
existing live service/world unchanged. Source WIP is eight files including the
new selector; governance plan/architecture/ledger changed separately. Outer pack
retains only prior.f0v-baseline/; original nested implementation unchanged.

Second wired increment: ResourceSiteHarvestCargo reads the exact worker's
existing resource account/whole part for physical work, HOT projection/release,
reconciliation, delivery and diagnostics; ActorCarriedResources validates generic
custodian/owner/kind/capacity without knowing field business. Crop hand receipt
uses predecessor cargo plus actual confirmed yield delta. Single-field lot
issuance, batch methods and terminal conservation remain transitional and must
migrate with concurrent jobs; no duplicate resource balance was introduced.
New-cycle field planning uses ResidentWorkSelection, and admission validates
the exact chosen local worker without reselecting the preferred one. Lineage
retains predecessor worker/stock/intent but permits a different successor farmer.
COLD regression blocks the predecessor body as UNKNOWN_AFTER_RESTART, admits
another exact farmer, completes its next cycle and round-trips state. The old
test fixture's invalid all-WORK schedule and omitted existing labour events were
corrected, not bypassed. Final affected domain/codec/architecture tests, adapter
compile and style/size/debt pass39s; no native/player evidence, deployment, commit
or push. Source WIP remains uncommitted; original pack/nested checkout unchanged.
All task Gradle processes ended; intended live service was not touched. Remaining:
individual field execution/claim map and cell generations, worker-scoped lot
issuance/delivery/retirement, generic provider/reservation protocol, exact two-
worker permission rosters/bootstrap, station-place lifetime and fresh schema.

## Current correction: lagging field biology interaction

User reports invisible settlement chunks (villagers audible, not visible), then
orders diagnosis/fix of Clearwater field divergence. Current live TPS20/MSPT1.626,
summary green; missing client chunk cause remains UNCONFIRMED and is separate.
At14:07:53 site7 epoch4/job4 conflicts pending-cell-hand-postcondition:cell-before-owned_drift.
Exact prepared cell58 is144/64/0, soil144/63/0: live read wheat age7/farmland;
saved physical witness committed age1/no pending effect, canonical snapshot61980
MATURE age7/unaccounted. Job retains34 harvested/carried, pending slot57 and completed
labour200000; no delivered yield. Source proves bone-meal admission reads a lagging
physical predecessor while the world-change observer requires canonical equality;
forward growth inside a higher canonical target is consequently unrecognised.
Original write attribution is not proved by the trace; its biology mismatch is.
Local47975452 synchronizes exactly the selected owned cell before vanilla bone-meal
reads it; unresolved physical work/holds/foreign ownership reject without spending
bone meal. Existing growth projector can acknowledge only a real owned-world read
of strict plant-age increase bounded by the already accepted canonical target,
unchanged farmland/no physical pending effect. It neither claims an old write cause
nor changes canonical plants, accounting or resources; beyond-target growth still
needs CROP_GROWN. Farmer pre-effect observation shares this projector, not a reset.
Existing scene reconciliation now permits an untouched prepared mature crop only
with completed labour, exact station/worker/bound pre-effect hand and complete
current field/no pending physical witness; no partial effect replay or yield credit.
Focused domain/adapter/architecture checks pass12s, final affected checks pass9s;
native field-turns15/15 pass38s including bounded observed-growth recovery,
beyond-target/missing-plant rejection and saved projection recovery. Negative
unit rejects a fabricated world read; recovery rejects wrong hand/body.
Logs /tmp/pm-growth-boundary-{focused,native,final}.log. Native evidence is this
physical boundary, not a complete graphical bone-meal/farmer story. Detached
assemble/verifyPackagedJar pass8s at /home/rd/proj/pm-growth-boundary-release.mwcxff/source,
/tmp/pm-growth-boundary-package.log; preflight OK. rd broadcast14:23:46 confirmed
in log (helper timeout is not a broadcast failure), save-all flush/graceful stop
14:24:01, all dimensions saved14:24:02/oldPID975954 gone. Checksum-pinned PM-only
publish/install, retained same R13, no reset. Deploy-verify OK; ready14:24:23,
runtime14:24:25, PID1017249. Rev71008/tick99523 green, required/inventory/custody0.
Same site7 job4 still retains34 carried/pending57 and CONFLICT: zero online players,
no naturally loaded field/body inspection yet. Do NOT claim this old conflict has
resumed or live product closure; next evidence is ordinary Clearwater loading,
current field/hand inspection and scene reconciliation, then the pending crop
receipt. No canonical overrides, force-loading or reset used. Implementation clean,
outer prior .f0v-baseline/ only, original nested historical WIP unchanged. No push;
task builds/tests/install complete, intended live JVM/artifact host remain.

## Current correction: external crop growth

User used bone meal after harvest and ordered the proper world-observation path.
Source-proven omission: external cell changes recognised loss/replant/soil damage,
not increasing crop age. Local8e40db5b adds stable-tag5 CROP_GROWN to the existing
exact cell observation/hold/ack protocol. Before-write durable fencing and actual
successful-write observation commit exact higher age and acknowledge the field
claim; immediate confirmation avoids consecutive bone-meal writes overlapping
the same held predecessor. Existing bounded scan recovers old forward age drift
against an owned current predecessor. Physical pending effects/foreign blocks
are not growth. Physical witness saves explicit successor crop/age, missing data
fails closed. The closed player-break witness accepts only break outcomes.
ResourceFieldGrowthProcess publishes the same registered first-maturity work
opportunity; field readiness updates without creating yield. accounted/yielded
remain current-batch history: regrown counted plants become eligible only after
cargo delivery retires the batch and nextEpoch clears accounting. No schema,
inventory-descriptor, ruleset or world reset; no new process owner or dual clock.
Domain cell/cycle/independent-clock/architecture and adapter physical-witness
tests pass20s (/tmp/pm-external-growth-focused.log); final adapter/style/size pass9s
(/tmp/pm-external-growth-final.log). Covers exact0->3->7, readiness notification,
codec/snapshot, duplicate/backward receipt, retained old yield/new batch and
explicit physical witness recovery. Two older pending-crop fixtures now actually
complete required labour before preparing effects, without weakening that fence.
Detached assemble/package verification pass8s at
/home/rd/proj/pm-external-growth-release.BehgXW/source. Preflight OK; game warning
14:03:33, save-all flush/graceful stop14:03:52, all dimensions saved and
oldPID847883 gone. Outer checksum-pinned PM-only install, same retained R13.
Deploy-verify OK; ready14:04:18/runtime14:04:20. Live rev49317/tick75102 green,
required/inventory/custody conflicts0; same site7 epoch4/job remains active, no
reset or artificial tick advancement. No live bone-meal-to-farmer acceptance or
graphical full-cycle claim; no push. Implementation clean, outer prior
.f0v-baseline/ only, original nested historical WIP untouched. Task tests/builds
complete; intended live JVM and artifact host remain.

## Current correction: schedule preference, not work prohibition

User accepted RimWorld-like priority policy after live farmer completed64 cells,
retained64 wheat/delivered0 and stood in FREE. Source-proven FREE fences existed
in new-work admission, owner yield intent and current activity selection.
Local checkpoint37a21b30 removes those prohibitions through family-neutral
ResidentActivityPreferences: scheduled work outranks optional FREE work, both
outrank idle; executable food outranks either. FREE alone never requests yield.
Actual exclusive admission, owner checkpoints, pending effects and cargo custody
are unchanged. Labour still clips intervals at known policy boundaries then
reassesses; no catch-up work or food owner return-to-work rule was added.
Sleep/recreation remain absent; no claim of a full RimWorld behavior catalogue.
Focused37 tests, adapter compile and style/size/architecture debt checks pass25s,
/tmp/pm-schedule-preferences-final.log. Extended real harvest receipt fixture
through typed hand/scene release and verifies actual COLD delivery travel in FREE.
The old FREE-prohibits-start test is replaced with matching admission coverage;
an unrelated bakery fixture now declares MAIN explicitly instead of observing
default OFF against a MAIN order. An overbroad first selection was stopped after
entering its legacy700-step bakery loop; not accepted as a green campaign.
Detached assemble/package verification pass9s at
/home/rd/proj/pm-schedule-preference-release.kO9GhX/source;
standing diagnostic deployment completed on SAME R13, not a reset. Preflight OK;
rd notified13:42:55, save-all flush/graceful stop13:43:02, all dimensions saved,
oldPID812806 gone. Outer checksum-pinned PM-only update; deploy-verify OK.
Live rev27792/tick50560 green, required/inventory/custody conflicts0. Same epoch2
farmer/job retains13 harvested/carried and partial labour38080/200000, paused under
UNKNOWN_AFTER_RESTART awaiting ordinary loaded-world inspection; no replayed work.
This proves deployment/recovery, not live delivery in FREE or graphical acceptance.
No push; implementation clean, outer prior .f0v-baseline/ only; original nested
historical WIP untouched. Task tests/builds complete, intended live JVM/host remain.

## Current task: resource labour and resident work statistics

User accepted and ordered implementation: operation-defined labour amount,
capability-derived speed and source-identified multiplicative resident modifiers;
shared sparse fixed-point progress retained across meals/HOT-COLD/restart, cancelled
only with its selected target. First consumer is farmer, not all production jobs.
New schema221/ruleset production-r11/schema13 intentionally requires a fresh world;
no compatibility work for disposable old test worlds. Existing R12 is still live
on29ba3877 was superseded by the R13 deployment below. Main alone implements.
Domain catalogue/progress/stats, harvest owner/events/codecs, HOT/COLD integration,
meal interruption and typed modifier edit are wired. Generic scene transitions
invoke registered owner pause strategies on drain/conflict/restart, not family
inspection. Modifier edits settle old-rate work, update only work traits (not
nutrition), and wake the registered owner. Selected-target changes clear only
its labour. Read-only process diagnostics expose amount/rate/interval/projection.
Focused harvest/cycle/persistence/math/architecture checks pass21s; adapter and
affected meal/metabolism/receipt/ruleset checks pass22s. Added actual engine HOT
vs COLD equality, snapshot, partial modifier and restart-suspension coverage;
final labour/WAL and style/size checks pass13s. Logs /tmp/pm-labour-*.log.
No graphical or full-cycle native acceptance claimed. Removed three now-unused
schema220 exact descriptor-upgrade helpers; schema221 rejects old schema and
unknown inventory before hydration. Their history remains in Git.
Local checkpointb0b30a52, no push. Detached assemble/package verification pass8s:
`/home/rd/proj/pm-labour-release.B3WtO8/source`.
Deploy preflight OK; players0; R12 save-all flush/graceful stop13:16:09, all
dimensions saved, PID718483 gone/service inactive. Verified artifact published;
outer full installer now preparing NEW `frontier-v3-labour-r13-20261002`.
R12 directory preserved separately; no deletion. Full install completed and fresh
R13 started13:17:22; deploy-verify OK. Live rev378/tick272 green:12 settlements,
366 residents,48 bioforms,12 sites, required/inventory/custody conflicts0. Later
Clearwater site7/tick510 GROWING/stage0, no active job/conflict; first crop137/64/-6,
board140/67/2. This proves fresh bootstrap/deployment, not a complete player cycle.
Implementation clean; outer pack only prior .f0v-baseline/; original nested repo
historical WIP preserved untouched. No push; no task-owned build/install process
remains, intended live service and artifact host continue.

## Active repair: Clearwater feeding deadlock

Current R13 follow-up: user reports one hungry resident despite food. Exact live
resident7-13 is farmer, HOT jobsite7 epoch1, satiety647->634, no meal, activityWORK
pendingSAFE_CHECKPOINT/SCENE_OR_AMBIENT_AUTHORITY, depot44 unclaimed bread, active
current surface/custody. Farmer continues real cell labour17->later cells, not
absent/frozen in these snapshots. Two direct neoforge tps readings show20TPS,
overall1.701/1.674MSPT; do not attribute this hunger to current TPS loss.
Source-proven circular dependency: requestsYield calls assess==EAT, but assess
uses shared execution admission which is blocked by that HOT scene and returns
WORK+SAFE_CHECKPOINT. Thus scene never sees a food-yield request. Fix derives
request from accrued need plus feasible meal opportunity independently of
admission; owner checkpoint still fences pending effects, and meal admission
still requires actual exclusive transfer. No food means no hunger-only work
abandonment. Generic policy, not a farmer-only exception. Connected HOT-field
regression covers request/admission distinction, no-food and pending-crop fences.
Affected activity/labour/meal/architecture tests + style/size/debt pass25s,
/tmp/pm-hunger-yield-fix.log. Local checkpoint4d16389f, clean; detached package
assemble/verify pass8s at /home/rd/proj/pm-hunger-yield-release.YOQ9qc/source.
Preflight OK; rd notified in-game13:29:31; save-all flush and graceful stop13:29:40,
all dimensions saved/PID788102 gone. Checksum-pinned PM-only install through
outer library, no full pack reinstall/reset. Same R13 restarted13:30:05/runtime07;
deploy-verify OK, PID812806. Fresh rev20250/tick33547 green/conflicts0. Saved20
ambient and1 scene authority correctly UNKNOWN pending loaded-world recovery;
initial farmer was hungry/no meal pending saved-body/scene inspection; subsequent
natural player loading now supplies physical evidence: rd joined13:30:54;
resident7-13 TAKE observed13:31:15, access cleared, CONSUME observed13:31:16/rev20789,
then same job/worker ambient-to-harvest handoff/reacquisition. Live rev21165/tick35914:
farmer nourished987, no meal, same epoch1 job63/64 cells, actual body144/63.9375/-4.5
at selected cell63, carried63, no navblock/conflict. Thus this last observed
approach to field followed a real meal, not a job restart/default-spawn inference.
Other repeated-entry/COLD presentation reports remain unproven by this interval;
do not generalize this one loaded consumption/resumption to full-cycle acceptance.
Outer pack prior .f0v-baseline/ only; original nested history/WIP untouched.
No push or world reset. Intended live JVM/host only; builds/install complete.

Follow-up live diagnosis12:25–12:29 after user HOT/COLD/HOT: farmer visibly waits
and then traverses immature crops. Depot recovery is now actually observed:
reference-surface-verified12:22:19/rev47609; loaded ACTIVE/OBSERVED_CURRENT,
ACQUIRED epoch7/current slots; Clearwater21 nourished/no hungry/starving.
Harvest epoch2 worker7-13 reacquired HOT12:24:04/rev50460, same scene/entity UUID.
Repeated live snapshots show completed15->16->18->21->25->27, moving from139/64/0
to141/64/-4, no navigation block, harvested/carried remains9. Not a permanent
freeze or absent farmer in this captured interval; no claim about earlier unseen
pause. Source-proven mismatch: ResourceFieldCycle.nextWorkSlotAfter and
reachableWorkSlotAfter treat GROWING cells as ordinary eligible destinations;
expectedWorkOutcome later returns SKIPPED_IMMATURE, but HOT executor requires
arrival and due work binding before accounting the skip. continuationInterval
uses resourceHarvestRetryInterval=200 ticks at station (10s nominal at20TPS),
even for a no-effect skip. Thus empty work visits/dwells imitate harvesting.
User confirmed traversal of immature zones. Current repair distinguishes actionable work targets from
no-effect exclusion, account immature cells without travel/gesture/yield, retain
physical/pending-effect validation in HOT and equivalent COLD semantics, and
preserve eventual handoff of carried9. Do not merely speed movement or reset.
Typed immature WAL event/codec and closed ownership registration added; shared
cell-exclusion reducer preserves identity, pending-effect and binding guards.
HOT read-only current-cell witness consumer wired before navigation; COLD excludes
before movement. Real labour cadence stays pinned200 ticks; no-effect exclusion
uses scheduler dispatch rather than labour dwell. Focused harvest/cycle/codec
tests pass17s, adapter compiles. First guardrails found two files over1000 lines;
exclusion reducer and physical evidence helper extracted into immediately used
owners, not waived. Final domain/architecture checks + guardrails + adapter
compile pass21s; final mature-target selection regression passes8s. Local
checkpoint52749d3e, clean detached assemble/verifyPackagedJar passes8s at
`/home/rd/proj/pm-immature-exclusion-release.A6hD6T/source`. Outer preflight OK.
User rd notified; save-all flush/graceful stop12:39:03, all dimensions saved and
oldPID663591 gone. Verified artifact published; retained-world install underway.
First52749d3e deployment rejected saved descriptor inventory before hydration;
world unchanged, previous1168bea2 restored and green. Omitted exact additive
descriptor upgrade was a delivery defect, not corruption or player-caused fault.
Existing narrow schema220 upgrade mechanism extended with pinned BEFORE64bf701b
and AFTER71e93725 only; no state/old WAL layout changes, unknown fingerprints and
physical-lifecycle drift still rejected. Snapshot header and full decoder share
this check. Actual affected upgrade/codec/state tests + static checks pass16s.
Local corrective checkpoint29ba3877; detached corrected build/package pass13s.
Reinstalled just PM through outer checksum-pinned library (pack was already
verified/current; avoids full Packwiz/C2ME redownload). No world reset or push.
Final deploy-verify OK; runtime restored exact R12 revision63059/tick96212 green,
zero required/inventory/custody conflicts. Same farmer7-13/job epoch2 retains
44 accounted,9 carried and no navigation block, ordinary COLD movement resumed.
This proves recovery/deployment, not a graphical HOT skip or full harvest cycle.
Remaining product evidence: natural loaded immature-cell exclusion and delivery
through HOT/COLD handoff on this exact candidate; no HUMAN_CANDIDATE claim.

User: food present,20 hungry. Rev28285/tick49531:63 bread,20 retained meals;
resident7-18 physically at135/65/14 in TAKE, turnavailable/no pending effect.
Depot surfaceCONFLICT, replicaEXPECTED/rev3, custodyRELEASED, owned loaded chest
with CURRENT slots. Generic materializer never revisitsCONFLICT; reference
custody requiresACTIVE. Permanent local deadlock despite green runtime summary.

Previous failed TAKE removed one bread physically before rejected WAL receipt:
old bakery-only physical-stack codec rejected actor pocket. Receipt recovered
11:49:34 and consumed11:49:35 under deployed3e25d5f6 but surfaceCONFLICT remained.
Source proves observer fences omitted prepared meal TAKE. Exact historical
surface-conflict writer/revision is UNCONFIRMED; do not claim otherwise.
Later rev41151/tick66467/player absent/chunkUNLOADED: COLD legitimately progressed
to stock53/meals12/nourished9/hungry11. Retained physical replica still describes
its old version: recovery must validate predecessor before fenced COLD catch-up,
not silently adopt/overwrite physical stock or reset the world.

Implemented current repair (17 files,318 insertions/16 deletions):
- ContainerPhysicalAuthorityComposition is the closed bakery/meal owner-query
  composition; adapter ContainerEffectFence adds saved field deliveries.
  Reference/fungible observers and handoff fence pending effects uniformly.
- ReferenceSurfaceVerified WAL receipt has explicit codec/process/diagnostic
  registration. Sole ReferenceSurfaceRecovery validates retained stock/provenance,
  exact replica versions, same ambiguous surface epoch/reason, no pending effect
  or unfinished custody. Actual replica conflicts remain isolated.
- Atomic surface/binding resume only: no resource changes or custody grant.
  Live/no-lease must match canonical; released custody may match retained physical
  predecessor, then normal fenced projection separately publishes COLD successor.
  Adapter reads naturally loaded supported owned chest; never writes to repair it.
- Surface changes invalidate container waits without reconnect.
- Architecture/semantics updated. Debt guard explicitly admits one lifecycle
  ACTIVE reference in sole recovery owner (35 vs34), not generic custody authority.
  Ordinary CONFLICT->ACTIVE status transition still forbidden.

Focused domain/adapter/static checks pass14s:
`/tmp/pm-container-recovery-focused.log`; adapter executors3/3.
Final affected domain21/21 + architecture5/5 and guardrails pass20s:
`/tmp/pm-container-recovery-final.log`. Recovery3/reference custody5/meal portion2/
custody state10/codec1, including actual pending TAKE negative, command admission,
reducer replay, snapshot and payload persistence. No synthetic product claim.
Existing native reference-projection slice passes28s:
`/tmp/pm-container-recovery-native.log`; regression, not live feeding acceptance.
Local checkpoint1168bea2; clean detached assemble/verifyPackagedJar pass8s at
`/tmp/pm-container-recovery-release-build.log`, source worktree
`/home/rd/proj/pm-container-recovery-release.h52AcA/source`.
Outer-pack preflight passed. Players0, save-all flush/graceful stop12:20:23,
all dimensions saved, oldPID578395 gone. Installed verified artifact and restarted
same R12 (no reset). Deploy-verify OK; fresh ready12:21:03.
Live rev45983/tick73529 green, required/inventory/custody conflicts0,366 residents.
Clearwater container still CONFLICT because chunkUNLOADED/no player/presentation
demand; read-only recovery correctly has not inspected nonexistent loaded evidence.
COLD stock44/meals2/nourished19/hungry0/starving2. The new loaded-surface recovery
and sustained HOT feeding remain UNPROVEN until ordinary player presence; do not
claim observed ACTIVE or full feeding closure from a green summary or unit tests.
Next relevant check: natural Clearwater loading, exact surface-verification trace,
normal fenced COLD projection and successive physical meal receipts.
No graphical HUMAN_CANDIDATE or long-running farmer acceptance claimed.

## Live identity (until next verified deployment)

Runtime `/home/rd/far-frontier-server`, service `far-frontier-v3-live.service`.
World `frontier-v3-labour-r13-20261002`, seed20260918065; old R12 retained separately.
Source479754528b420611132a93cd4e948bca7fbd7c5d, MainPID1017249,
invocationbcac707088b84a33853774958bdecd1a, notBefore1790933052,
ready14:24:23/runtime14:24:25/port25565 (RCON25575). Artifact host8092.
SHA512
`727c69b01330dffe17cfde048d06e89ca8a9e5ac2494fd0dfcdc6702c3c64d77c982fe8f0580b144d52a80b1378a4cc8d31e7df71cb432f8168830264efec7db`.
Management31ca0867 and pocket-codec3e25d5f6 committed locally, no push.
Current detached package `/home/rd/proj/pm-growth-boundary-release.mwcxff/source`.
Incident logs `/home/rd/far-frontier-server/.management-deploy-evidence.bdaxRr`.
Do not print credentials/private inputs. Verified DIAGNOSTIC deployment only.

## Retained system / boundaries

Management uses injected profiles/priority and existing StrategicPlanState
decision/objective/task authority. Actual concurrency remains two existing lanes;
no future trade/combat/expansion or full medical executor migration claimed.
Sparse COLD travel, canonical daylight, shared navigator, carried-resource custody,
bounded satiety/food portions, activity arbitration and service placement exist.
Separate farmer site7 epoch1 previously crop17/64,carried17,outputSlot1,
resident7-13 TRAVELLING PREPARED/BODY_SPACE_OCCUPIED; this fix does not claim
farmer/home-route/appearance closure.

RCON root `pale_mirror v3`, IDs container:7-depot,settlement:7,resident:7-N.
MCP runtime index points to old checkout: stale, not evidence. Use live RCON and
direct active source. RCON helper may be retained in functions store `readRcon`.
Outer pack `/home/rd/proj/minecraft` has only prior `.f0v-baseline/` untracked;
original nested source untouched; governance historical WIP preserved.
Deploy through outer scripts, exact clean detached implementation source,
explicit disabled-mod catalogue. Retain world. Build JDK21
`/home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`; live Java22
`/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java`.
All task builds/install completed; old/failed/rollback live JVMs stopped gracefully.
No task-owned native/build process remains; intended live service and unchanged
artifact host remain. Outer pack still only prior .f0v-baseline/; original nested
source untouched, governance historical WIP retained (new ledger archive below).

Full prior ledger and earlier archive links preserved in
[archive](docs/archive/continuity-before-reference-surface-recovery-20261002.md).
Archived live identities are superseded by latest verified identity above.
