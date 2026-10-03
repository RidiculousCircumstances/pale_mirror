# Continuity Ledger

2026-10-02 LIVE DIAGNOSIS: user reports food present but20 hungry in Clearwater.
Same3e25d5f6/R12/PID578395; summary green/no quarantine. At revision28285,
tick49531, stock63, claims20, meals20, nourished1/hungry20. Resident7-18 (logistician)
is physically at service135/65/14, mealTAKE, turn available but no pending effect;
others retain MOVE. Depot surfaceCONFLICT, replicaEXPECTED/rev3, custodyRELEASED,
owned loaded chest with CURRENT slots. Actual take refuses non-operational custody;
reference reconciliation requires surfaceACTIVE, generic surface executor never
revisitsCONFLICT. This closes a permanent service deadlock, not a food shortage.
Source gap: both reference/fungible observers suppress bakery/field pending effects
but omit existing ResidentMealPhysicalAuthority.pendingForContainer. Earlier
failed TAKE/restart left physical63 versus prepared canonical64 until7-21 receipt
recovered11:49:34 and consumed11:49:35. That is a concrete false-drift exposure;
exact writer/revision of surfaceCONFLICT is not yet retained in live diagnostics.
No new source fix/restart/reset this diagnosis. MCP runtime index points to an old
checkout and is stale; live RCON/source evidence used instead. Next repair must
coordinate pending-effect ownership and legal verified surface/replica recovery,
not merely clear a flag or overwrite physical stock. Preserve R12 incident.

2026-10-02 DELIVERY: settlement-management cut31ca0867 plus actual meal-pocket
codec fix3e25d5f6816bdb87b5f1b3526425f02ea4fb9a06 are locally committed (31 task
files total), no push. Follow-up focused food-portion/codec run passes9s,
/tmp/pm-meal-pocket-codec-final.log; broader affected checks pass24s.
Detached /home/rd/proj/pm-management-pocket-release.dZVFjC/source assemble and
verifyPackagedJar pass14s (/tmp/pm-management-pocket-release-build.log).
Published/checksum-installed SHA512
f07f6c8037a27b191af57a56130f6229d8f6484820a82e9f833567533cc2438d5dd953534674646121168ebe1dd9734cfeb6d5ce4e4a40bf0bd10b897b00c629.
Players0, save-all flush then graceful stop completed11:38:47/all dimensions
saved. R12 frontier-v3-carry-r12-20261002 preserved, no reset or deletion.
Preflight and deploy-verify pass. Live invocation78005c51c2434dacadb415f4dec4c6d7,
MainPID578395/notBefore1790923153, fresh ready11:39:23/port25565.
Authenticated summary rev6032/tick24337 green, required/inventory/custody
conflicts0, settlements12/residents366; new settlement_management settlement:7
reads retained harvest commitment, no duplicate proposal/hold. Site7 retains
its same active epoch1 harvest. Earlier quarantine is no longer active; full
long-running live meal/farmer acceptance remains unproven. This is a verified
DIAGNOSTIC deployment, not graphical HUMAN_CANDIDATE. Management implements
existing operations only; current lane and profile limits described below.
Implementation clean; outer pack only prior .f0v-baseline/, original nested
source untouched, governance historical WIP retained. Intended live service and
artifact host remain; no task-owned test/build process or subagent remains.

2026-10-02 implementation: user accepted a SOLID settlement-management domain over
existing actual operations; main alone, no subagents. Implementation checkout
/home/rd/proj/pm-f06r3-facility-lane-recovery, base324f7df4 plus task WIP.
SettlementManagement arbitrates injected profile offers using immutable priority
policy and lane-scoped holds. Existing StrategicPlanState decision authorities,
objectives/tasks remain the only durable commitments; no new schema or roster.
Food/field/supply/containment profiles replace the old settlement-candidate
monolith. Shared workforce selection derives retained assignments; common
commitment admission checks current authority, participants and owned facility
through injected owner views before farmer/bakery reducers claim resources.
Execution/activity/navigation remain separate owners. Existing two lanes remain
the actual concurrency limit; no future trade/combat/expansion implementation or
complete medical executor migration is claimed. Requirement catalogue is shared
by task creation and read-only settlement_management diagnostic.
Normative execution semantics and both architecture maps updated.
Focused domain39/39 plus architecture5/5, adapter diagnostic1/1 and static
style/size/debt checks pass (/tmp/pm-settlement-management-terminal.log).
Production-work native initially2/3: stale fixture used bare ambient HOT without
body confirmation. Exact failure reproduced on clean324f7df4 baseline
(/tmp/pm-management-baseline-native.log); corrected only fixture admission to
current typed body receipt. Native now3/3, build successful
(/tmp/pm-settlement-management-native-final.log), no weakened terminal assertion.
Guardrails pass19s; task-only checkpoint31ca0867 (27 files), implementation clean
at that checkpoint. Detached /home/rd/proj/pm-management-release.EVTYJD/source
assemble/verifyPackagedJar pass13s. Before deploy, live baseline324f7df4 was
already quarantined at11:14:49/revision5660: resident7-21 meal TAKE produces a
pocket witness, but HumanPopulationPayloadCodecs reused bakery's container/hand-
only codec. Encoding rejects the actual receipt before WAL and quarantines.
Exact error `bakery observes only its chest or exact actor hand`; log preserved
under /home/rd/far-frontier-server/.management-deploy-evidence.bdaxRr/before.log.
Scoped follow-up WIP extracts common PhysicalObservationStackCodec, preserves
tags0/1 and adds explicit MAIN2/POCKET3. Both meal and bakery use this bounded
grammar; owner reducers still validate allowed custody. Existing HOT multi-slot
meal test now roundtrips its actual TAKE/pocket witness plus both equipment
hands and pocket hydration. Affected meal/bakery/management checks and static
gates pass24s; final focused food-portion run pending. No reset needed.
Management not yet deployed. Next: follow-up checkpoint, detached package and
verified diagnostic deployment retaining R12, then exact management/summary reads.
No new graphical player acceptance or push claimed. Preserve governance history,
original nested source and outer preexisting .f0v-baseline/.

2026-10-02 user requested deployment of carried-resource/self-care cut.
Local task-only checkpoint324f7df40a875e0bff1f1776f35836264ea32f8d (43 files),
implementation clean, no push. Clean detached build at
/home/rd/proj/pm-carry-release.QuU7Ub/source: assemble/verifyPackagedJar pass14s,
reused unchanged focused/native evidence below. Published+checksum installed
SHA5122c51a88c46afc7368545ba47444b9588c2145b6a5cd72a1013019b7fdbba8a39c80c936e02f042778538a4d9ca48a14302d52d95b2323dfff7261e56da87ef89.
RCON notification and save-all flush; vanilla stop finished11:09:00 with all
dimensions saved, oldPID405157 gone. R11 world preserved unchanged at
/home/rd/far-frontier-server/frontier-v3-plants-r11-20261002; no world deletion.
Selected fresh frontier-v3-carry-r12-20261002, same seed20260918065, managed
graybox datapack installed; clean detached/fresh-world preflight passes.
New live invocationd0c19ca3d8e148789bf95bb1eb160f29/MainPID508419,
notBefore1790921369, ready11:09:39/runtime11:09:41, port25565; deploy verifier OK.
Authenticated `pale_mirror v3 inspect summary` rev378/tick725 green, required/
inventory/custody conflicts0, settlements12/residents366; Clearwater site7
GROWING epoch1/stage0 without old harvest lineage. Verified DIAGNOSTIC deployment,
not graphical HUMAN_CANDIDATE; live pause/eat/resume still needs player acceptance.
Hosted PM JAR updated, Visuals unchanged; client installer can fetch new artifact.
Root pack still only prior .f0v-baseline/, original nested source untouched;
governance WIP retained. No task-owned build/install/native process remains;
intended live Minecraft service and unchanged artifact host remain running.

2026-10-02 ACTIVE: universal carried-resource custody across activity changes,
main alone/no subagents, implementation /home/rd/proj/pm-f06r3-facility-lane-recovery
(Gradle subdirectory pale-mirror), HEAD2dc38aa plus task WIP. Multiple bounded
actor accounts, typed ActorItemSlot/physical pockets and exact MAIN/OFF hand
addresses; meal food now pocket0, work cargo retains its account/declared hand.
Registered work-owner carry views keep common activity/adapter code independent
of concrete field/bakery jobs. Owner checkpoints fence pending physical effects,
not cargo. HOT yielding now captures a supported actual body instead of requiring
arrival at the next goal (last crop can already retarget depot). No cargo drop.
User observed hungry farmer waits until reconnect after harvest: included as an
explicit incident/acceptance requirement, not yet claimed fixed in live world.
Read-only current R11 snapshot rev42230/tick62690 showed resident7-13 nourished
(783), previous batch delivered, green summary; this later snapshot does not
disprove the reported delay. Live build remains2dc38aa, new code NOT deployed.
Focused custody/codec/bakery-HOT/farmer-cargo checks and adapter compilation
pass26s; earlier cargo regression pass19s, native field-turns15/15 includes real
Villager pocket take plus entity save/load with both equipment hands preserved.
ExactInventory's former one-account-per-actor invariant was the actual integration
failure during meal TAKE and is corrected (non-actor custody uniqueness retained).
Final slot validation/guardrails/native reference-projection checks pass42s;
8/8 native tests, log /tmp/pm-carried-resource-final.log. Meal tests12/12 plus
architecture5/5 and native field-turns15/15 pass30s on final logic, log
/tmp/pm-carried-resource-field-final.log. Work release wakes resident/depot keys
in existing canonical scheduler; no reconnect-specific wake added. Full live
pause/eat/resume and the reported reconnect delay remain product-unverified.
Gradle uses explicit JDK21; live Java22
installation is a JRE, not a build compiler. No commit/push/deploy/world reset.
Source and governance WIP preserved, outer pack has only prior .f0v-baseline/.

2026-10-02 user authorized a fresh world with the independent plant-growth cut.
Local checkpoint2dc38aa032cb116dc45979a2b1ba2f270bbffd27 committed task-only19
source files; no push. Implementation clean; canonical governance WIP preserved.
Detached /home/rd/proj/pm-plant-growth-release.27So2X/source assemble and packaged
JAR verification pass8s, prior affected domain58/native14 evidence reused.
SHA51249cd2798b54d6b628ae168099cdd9c775a945df546b3d57ece9c7749040655907378361835a83c919a54154e7952978bd78e7032b43776b10e7b125a08dc1743.
RCON notification+save-all flush then vanilla stop complete10:21:32 with all
dimensions saved; oldPID345939/345963 gone. Old R10 world preserved unchanged
at /home/rd/far-frontier-server/frontier-v3-food-r10-20261001, not deleted.
Selected NEW frontier-v3-plants-r11-20261002/seed20260918065, copied only managed
graybox datapack into fresh directory; absent level.dat preflight passes.
Root publisher/checksum installer used; prior JAR recoverably archived at
/home/rd/far-frontier-server/.far-frontier-installer-cache/retired-pale-mirror/pale_mirror-hosted.jar.20261002T052150Z.
New service invocationae93e1c9eea24d4fa2eea45774f0eb0a/MainPID405157,
notBefore1790918510, ready10:22:00/runtime10:22:02. Post-start deploy verify OK,
port25565; real RCON summaryrevision378/tick99 green, required/inventory/custody
conflicts0, settlements12/residents366/sites12. Sites1/8 GROWING epoch1/stage0,
no prior harvest lineage. Verified DIAGNOSTIC deployment; player visual acceptance
not claimed. Root pack still only pre-existing .f0v-baseline/, original nested
source untouched. No task-owned build/test process remains, intended live service
and unchanged client artifact host remain. User may reconnect for new-world test.

2026-10-02 active assignment: independent plant lifecycle, main alone/no agents,
same checkout44091633 plus task WIP. ResourceFieldGrowthProcess now owns one
site clock surviving maturity, active harvest and work-epoch retirement; cells
grow without changing accounted/yielded batch flags. Delivery preserves plant
age and cadence. Exact physical receipts accept monotonic subsequent growth,
not crop/soil loss or fabricated observations. Normative execution semantics
and source architecture map updated. No old test-world schedule migration;
fresh test world is required for this semantic cut at a later deployment.
No deploy/reset/commit/push this assignment; live R10/44091633 unchanged.
Focused domain/recovery+guardrails passed34s. Final domain28/28 and adapter30/30
plus architecture/contract/style/size pass27s; native real-block receipts14/14
pass29s (test execution2s), /tmp/pm-independent-growth-native.log and
/tmp/pm-independent-growth-final.log. Terminal engine test now retains the
independent clock from initialization and proves delivery preserves its exact
deadline plus grown plant stage3; no replacement clock at work retirement.
No task-owned build/native process remains. Source WIP retained; original pack
still only pre-existing .f0v-baseline/, original nested source untouched;
governance historical WIP preserved. Earlier cold meal assertion89
also fails on clean44091633 baseline (9s); not caused by this cut, not silently
weakened. This growth cut does not claim to repair resident8-31 unsafe body
admission/delivery from the incident below.

2026-10-02 live user incident after44091633: Ironmeadow HARVESTING, no farmer
on field and visible wheat stage0. Read-only RCON at revision2284543/tick715758
and2284806/tick716342 confirms site:8-wheat-field epoch5, exactjob
job:site-harvest-8-wheat-field-5 has64/64 harvested,64 carried,0 delivered,
64 deferred materialization slots; semantic goal DEPOT_SERVICE, no current scene,
last crop scene CLOSED. Exact worker resident:8-31 alive at canonicalbody377,63,0;
ambient WORK lease PREPARED, physicalAdmission BLOCKED/no loaded body or motion.
Diagnostic BLOCKED maps to hasExactStandingColumn failure at support377,62,0;
do not claim who originally wrote that unsafe checkpoint without tracing producer.
field_physical confirms epoch/layout current, first cell physical committed and
canonical both FARMLAND/GROWING/stage0, no pending physical/foreign write. Thus
stage0 is post-harvest replanting, not a proven stale growth projection; current
stall is delivery/body admission and board collapses it to harvest in progress.
Summary green/requiredConflicts0. No source/runtime mutation or restart this
diagnostic turn. Optional runtime-context MCP indexes stale different worktree,
not usable live evidence; actual authenticated read-only RCON used instead.

2026-10-02 user authorized deployment. Local source checkpoint
440916335846db3947be262cef2d62044d374e69 contains bounded HOT/COLD work,
shared pedestrian detours and codec normalization; active implementation clean,
no push. Detached /home/rd/proj/pm-bounded-release.JPtraE/source package/verify
passed14s using explicit disabled-mod catalog; prior dependency-bound focused
and native evidence reused, no new full matrix or graphical claim.
Preflight passes. One player rd online was notified before restart. Explicit
RCON save-all flush confirmed all dimensions saved09:53:04; oldPID276378 exited
after service stop09:53:07. Exact same R10 world/seed preserved. Root publisher
and checksum-pinned installer used; previous JAR archived recoverably at
/home/rd/far-frontier-server/.far-frontier-installer-cache/retired-pale-mirror/pale_mirror-hosted.jar.20261002T045325Z.
Installed SHA5129a9a6a8543a9fa332ec4db3773b4b271222fac0f77f10ebf9e109357cc4a8f47cc35672b9ef97cc9e9b2f6ebb24dd3942a81166fcbdeebb102c31673dc3da036.
New service invocation1592fea6bb7c45beabbdab9766458adb/MainPID345939,
notBefore1790916805; deployment verifier proves fresh runtime+ready, port25565
and no new quarantine. Verified DIAGNOSTIC deployment, not full graphical
HUMAN_CANDIDATE. Player can reconnect and inspect actual baker/body contention;
no claim that the specific full-pack live incident is product-validated yet.
Original pack only pre-existing .f0v-baseline/; nested original source untouched;
governance WIP preserved. No task-owned build/test process remains.

2026-10-02 user requested guardrails normalization. Extracted the complete
replica/custody snapshot grammar into package-private PhysicalReplicaCustodyStateCodec;
FrontierWorldStateCodec now754 lines vs unchanged808 ceiling (previous812).
Wire order/tags/schema and rejection semantics unchanged; shared diagnostic
grammar remains one implementation, package-private inside persistence.
guardrails plus FrontierPersistenceCodecTest/PhysicalReplicaCustodyPayloadCodecTest
pass21s; additional PhysicalReplicaCustodyStateTest10/10 pass9s, including retained
conflict/custody recovery. Logs /tmp/pm-guardrails-normalize.log and
/tmp/pm-replica-codec-regression.log. This resolves the old debt failure below.
Task WIP preserved; no commit/push/deploy/reset. No owned build/test process left;
original pack retains only pre-existing .f0v-baseline/, original nested source
untouched. Live server unchanged.

2026-10-02 bounded-work implementation checkpoint: active checkout remains
19346dad plus task-owned uncommitted WIP; no commit/push/deploy/reset this turn.
All five items have scoped implementations: lightweight execution/schedule views
instead of ordinary snapshot encoding; family-dependency relation validation and
assignment cache plus exact meal invalidation; field/container chunk indexes;
fair container discovery windows (8 candidates), shared slot-map compilation and
32 deferred preparation attempts per turn; empty COLD advances stop at next due,
parked audit or periodic-save boundary, preserving one-step overdue backlog.
Shared HOT navigator now performs bounded native body-aware detours and retries
when goal occupants change; no bakery-specific route, teleport or COLD-body wall.
Navigation native30/30 and reference-projection native8/8 passed. Focused snapshot,
handoff/window/fast-forward and COLD120-tick equivalence+recovery checks passed;
latest assignment/meal/domain checks + architecture/contract/style/size passed30s.
New negative unchanged-job/retired-target full-vs-incremental rejection passed7s.
Relevant logs: /tmp/pm-bounded-work-final-focused.log,
/tmp/pm-bounded-work-final-validation.log, /tmp/pm-bounded-work-dependency-negative.log.
Full guardrails NOT green: HEAD FrontierWorldStateCodec already812 lines against
unchanged808 debt ceiling, confirmed identical at HEAD and working copy. No ceiling
increase or unrelated codec cleanup. Scheduled-subject audit stays complete;
physical readiness is fresh (not revision-cached); bounded deferred retries are
not yet a physical dirty-event queue. No measured speedup or graphical acceptance
claimed. Live same-world diagnostic19346dad remains unchanged; new fix not live.
No task-owned test/build process remains. Original pack only pre-existing
.f0v-baseline/; original nested source untouched; governance historical WIP preserved.

2026-10-02 active user assignment: implement HOT/COLD optimization items1–5
from the source audit plus reported baker-grain movement blocked by another
resident. Main alone/no subagents. Scope/acceptance:
docs/work-orders/PM-HOT-COLD-BOUNDED-WORK-20261002.md. Preserve same live world
and incident; no new deployment/reset/publication authority inferred. Source
starts clean at19346dad. Prior current-state handoff is deployed but graphical
acceptance remains open. Begin with shared pedestrian contention and removing
snapshot serialization from ordinary execution reads; retain exact custody,
causal order and recovery audits. No fresh benchmark/matrix campaign required.

2026-10-02 handoff source checkpoint19346dada5a2538333a0aaf5a11ab775d73ce1f2
is committed/pushed; remote branch exact ref verified, implementation clean.
Clean detached package/verifyPackagedJar pass6s with explicit disabled-mod catalog.
Root preflight passes; players0, graceful stop09:21:46 saves all dimensions and
oldPID172414 exits. Installer archives prior JAR recoverably, no world reset.
Same R10 world/seed, SHA5123e84159dc9472d99c34e4826e7b078e06d5f5f57a6ea56a20fe593f100ae50489e2e01654778e90bc1961acad46085046213f559e3475fbcbfa385e87976fa09.
New service invocation92f19acad6bd43eda5512c1346525ee4, MainPID276378,
notBefore1790914920, ready09:22:10/runtime09:22:12. Root deploy verifier passes.
Actual read-only runtime revision2234875/tick673280 green, required/inventory/
custody conflicts0; normal restart retains5 ambient HOT leases UNKNOWN pending
loaded-world recovery. Actual first_visibility8,-1 renders the new owner/reason/
revision fields and honestly WAITING/field_epoch_reconciliation while unloaded.
This is a verified DIAGNOSTIC deployment, not HUMAN_CANDIDATE or a graphical
terminal COLD/HOT acceptance claim. No task-owned test/build process remains;
only intended live service. Root pack retains only pre-existing .f0v-baseline/;
governance historical WIP preserved plus explicit contract/map/ledger amendments.
Next evidence boundary is ordinary player reentry: actual current field/stock,
same farmer identity/current position and no old COLD work replay or starvation
of neighbouring chunks. No further infrastructure/matrix proof campaign needed.

2026-10-02 user accepted current-state COLD/HOT handoff implementation; main
alone/no subagents, same active checkout/world. This supersedes "not authorized"
below. WIP adds read-only HotHandoff participant composition, current field
witness/canonical/actual checks, reference-container stock/provenance and pending
effect checks; STATIC_CURRENT no longer promotes READY merely after an executor
pass. Native PlayerChunkSender filter requeues WAITING chunks before batch
counts/acks. Classified conflict is presentable but not HOT-ready. PREPARED
identity-verified retained ambient bodies use current placement zone; already
HOT refresh never reprojects. Failed probes restore pose/support. Opt-in
first_visibility adds exact owner/status/reason/proof revision. Normative
execution semantics and architecture map amended deliberately.
Initial focused owner/admission/architecture/style/size passes37s and36s.
Native local-navigation28 tests exposed missing contact refresh after retained
placement; fixed. Final local-navigation29/29 (including PREPARED current-target
placement and HOT no-teleport), prepared-recovery3/3 and reference-projection8/8
pass. New native fixture initially exceeded canonical bounds at GameTest's random
coordinates; corrected to the actual declared-placement provider boundary, not
weakened production bounds. Field canonical obstruction remains READY only with
exact retained/actual foreign NBT; its regression caught Condition.of called
before foreign handling (fixed). New batch policy prevents nearest deferred
chunks from starving ready neighbours, preserving exact sent-count/acks.
Current tracking-view presentation, not proximity, selects field projection.
Existing reference owner can prepare/reconcile stale visible images outside
block-ticking range under its exact projection custody; unchanged far images
never reacquire HOT. Existing reference projection/recovery native8/8 passes.
Affected69 NeoForge unit tests + architecture/contract/style/size + assemble/
packaged-JAR pass38s and40s; final physical-executor/field/batch/admission subset
passes10s. No full graphical COLD/HOT terminal acceptance claimed. Current source
is reviewed WIP awaiting checkpoint and same-world diagnostic deployment;
server still880cb78c/SHA5ffa2b75. Read-only players0/runtime green at671201.
No world reset or broad proof campaign. Remaining product evidence: ordinary
network-player COLD/HOT/reentry with linked visible state and exact actor/custody
continuity, especially far-view container projection and retained farmer return.

2026-10-02 read-only COLD/HOT audit requested by user, plus Ironmeadow hungry
resident report. No implementation/runtime mutations this turn. Actual old farmer
scene reconciles08:31:46 and releases08:31:56; at tick616103 resident8-31 NOURISHED,
IDLE/no meal, oldjob not_found. Latest two hungry residents8-1/8-21 had real HOT
MOVE claims; repeated reads/traces prove both TAKE/CONSUME then NOURISHED/IDLE
at616340, normal motion changed position. No sustained feeding stall proven here.
Static findings: no generic historical animation replayer found. Actor/meal new
lease uses as-of current canonical instant. Harvest overdue continuation rebases
nextDue on now, explicitly forbidding rapid old-due catchup. BUT field projection
is visibly incremental: fresh physical initialization writes wheat age0/cell one
write per admitted turn, then GrowthProjector selects current canonical target
one cell per rotating site turn. completeDynamicCatchUp promotes STATIC_CURRENT
to READY solely after execution pass; does not require current field/body/inventory
receipts. Existing ambient body materialize returnsCURRENT without aligning to
handoffBody; admission accepts any allowed placement candidate and writes observed
body back into canonical state. Conditional stale-body acceptance within allowed
service zone is reachable in source, live occurrence not yet proven. Nonclosed/
unknown/conflict leases hold COLD progression through ActorExecutionCoordinator;
unloaded serialized body alone is not released authority. This can cause work
resuming only on loaded-world recovery, not necessarily historical animation.
Next proposed work: real current-revision readiness barrier and reconcile retained
body vs as-of canonical position, separating unresolved physical-effect recovery
from rendering completed COLD history. User has not yet authorized this new cut.

2026-10-02 Ironmeadow recovery follow-up: user authorized fixing retained farmer
without reset. Exact physical body UUIDc22a7ab2-39a0-3241-924b-2a744520b9f9 has
carrier epoch6 and offhand59 wheat; actual saved canonical BODY recovery owns same
scene/revision but epoch7 AMBIGUOUS. Adapter incorrectly supplied carrier epoch
as canonical recovery epoch. Read-only reducer analysis on actual snapshot proves
receipt6 rejected as stale, receipt7 accepts HOT without inventory/site changes.
These clocks are independent: scene recovery increments on scene admission;
carrier incarnation increments on physical reconstruction. Local commit880cb78c
(pushed) reads exact scene-owned AMBIGUOUS recovery fence, retains physical
owned()/ledger proof, renames receipt field recoveryEpoch; binary layout unchanged.
Focused domain recovery positives/negatives and NeoForge adapter clock regression,
architecture/contract/style/size gates pass9s final. Clean detached package pass6s;
preflight OK, graceful stop08:29:47 all dimensions saved, oldPID152727 gone.
Same-world install SHA5125ffa2b75cedb499344a3744e8b9a2668246446488a6994ea25a8166f1e981f839788eda6aaddecfad7f66d41a39f06eb6a195125de01c0679011f37b8f96f015.
New service invocation7f1e249729154a67ba625602fe160df8 starts08:29:59,
MainPID172414/notBefore1790911799. Fresh deploy verifier OK. Read-only
rev2185118/tick611275 green/required+inventory conflicts0, players0. Retained
Ironmeadow job remains60/64 sceneCONFLICT while unloaded; actual loaded-world
inspection/resumption not yet observed. Ordinary visit needed, no claim completed.
No world reset/teleport/stock rewrite, no additional matrix/native campaign.

2026-10-02 current implementation/deployment: main alone, no subagents. User
accepted service-area turnover and explicitly authorized commit/push. Source
branch feat/baker-carry-orders-20260926 is clean and remote verified at
42b834ba90af91290824497631ff8e3b1c0f6e85. Commits341c21e8 (temporary buffer
turnover),8c197eeb (external crop replant),42b834ba (historical interruption fence).
Same world frontier-v3-food-r10-20261001/seed20260918065 preserved, no reset.
Clean detached build/verifyPackagedJar and root preflight pass. Graceful stops
08:18:42 and08:19:59 saved all dimensions. First8c197eeb restart quarantined:
old parked activity admission queried newer COLD travel before departure. Exact
owner checkpoint now refuses that historical interruption without weakening
TimedKnownRoute or fabricating body evidence; regression reproduces this seam.
Final42b834ba unit started08:20:57, MainPID152727, invocation
b7b54981e0fd4f6d94791ae89f42182e, notBefore1790911257. Fresh deploy verifier OK;
SHA512 fc0e46f9884b709e4283a5593ae440359966d0deb0a0bbe62e0ec124534087d25564af04dca3d0461df3bbb607e0f1e772eb9c162465d7846db9aa6e70efb577.
Read-only rev2175540/tick601135 green, required/inventory/custody conflicts0,
players0; formerly clearance-blocked bakers10-3/11-3 now retain real MOVE meals
with exact food claims/routes. This is canonical progress, not client acceptance.

Turnover uses reachable free2D temporary buffers, geometry-owned rest targets
outside all buffers and existing interruptible ActorMovement HOT/COLD owner.
Explicit additive movement-start codec/pinned schema220 descriptor upgrade
preserves exact world. Focused activity/movement/meal/navigation/registry/upgrade
tests and architecture/style/size gates pass. Governance resident-life contract
amended separately; historical governance WIP retained, not swept into source push.

User's Ironmeadow defect: site8/job:site-harvest-8-wheat-field-2, resident8-31,
cell46 at382/64/-4, sceneCONFLICT08:07:23 cell-before-owned_drift. Read-only
RCON proves wheat age0 on farmland while snapshot590528 and physical SavedData
retain MATURE7, no pending effect/break/hold. Exact mutation producer unconfirmed.
Previously WORLD classifier recognizedAIR/soil loss but not replant. New exact
WORLD CROP_REPLANTED wiretag4 records young crop and zero-yield accounts only
that cell, cancels selected pending operation and chooses another pool cell.
Existing exact field/hand/body/epoch inspector accepts safe partial bound batches,
not unresolved effects; no resetting conflict blindly or minting yield. Focused
frontier/NeoForge/witness/partial-inspection tests pass30s plus final16s; movement
historical checkpoint regression and affected gates pass16s. No broad matrix.
Latest read-only follow-up after user visit: player rd online, rev2180371/tick606022
green, no global quarantine/inventory conflicts. Same Ironmeadow job now60/64,
pending-1/selected46(cell47)/harvested59/carried59/delivered0. Thus real external
replant was observed and excluded with zero yield as intended. BUT same exact
scene remainsCONFLICT; safe partial inspector has not resumed it. This is an
unclosed recovery seam, not proof of full farmer repair. Next inspect which exact
field/body/hand/recovery prerequisite refuses reconciliation; no world reset to
hide this retained incident. No forced chunk load, world reset or hand repair. Source and clean
release worktree clean; original pack only retained .f0v-baseline/, original
nested/governance WIP untouched except explicit normative/ledger edits. Only
intended live service remains; no test/build sessions still running.

Current deployed sourceeaa4cdd4 (includes79b6ec07): second graceful stop saved
all dimensions07:55:14; same world/seed preserved. Root installer archives old
diagnostic JAR, starts unit07:55:33; MainPID96695, invocation
525f33ecbdd74e49ad24c381651d80d4, notBefore1790909733. Done07:55:44/runtime07:55:46.
Fresh deploy verifier passes. Read-only rev2155704/tick569471 green, required
and inventory/custody conflicts0. Same resident10-3 now liveWAIT/MEAL_CLEARANCE
with explicit four dependency addresses; no activity-review transaction stage
in new process telemetry. Generic recovery released scene (active/hot scene0),
not a world reset or erased farmer history. Needs remain independently retained.
One ordinary relative5000-tick request569609→574609 COMPLETED07:56:46.934
(queued07:56:10.401,36.53s wall), players0, no failure/new quarantine.
Telemetry730slices/5000ticks:18.699s total/18.629s advancement/0.063s safety,
~3.73ms advance/tick. Prior user's5000-tick run took~250s wall/45.9ms advance
per tick: useful observed improvement, NOT controlled benchmark (state/HOT demand
differ). New metrics have no named activity-review stage, but droppedAttributions
1363 means telemetry absence alone is not exhaustive proof of no transactions.
Live resident10-3 stillWAIT/MEAL_CLEARANCE, hungry with556bread; underlying
clearance availability is NOT repaired by parking. Audit-ready-without-wake0,
Post-run actual snapshot574928 retains BOTH exact activity actions due568927
(6001ticks old), confirming no per-tick replacement of those durable waiters.
summary rev2156742/tick574706 green, required/inventory/custody conflicts0.
No full client/feeding acceptance or claimed farmer hunger partial-batch fix.
Implementation clean; original pack only retained`.f0v-baseline/`; original nested
and governance historical WIP preserved. Local commits only, no push, no reset,
no client/public-host update. Only intended live service remains after checks.

Deployment correction: first79b6ec07 restart was NOT healthy.07:51:42 generic
stored recovery of exact lease:site-harvest-8-wheat-field-2-crop-7-r2125296
proved the saved body, transitioned toDRAINING, then generic unbound release
was rejected: resource-site harvest release has no engine schedule binding.
New admission code is absent from the failing call chain. Source counterexample:
normal harvest release supplies retained continuation, stored recovery called
generic release withOptional.empty. Local checkpoint
eaa4cdd40c06db0f6a9fc886a21ff9da2c88df1b repairs this integration: every behavior
explicitly registers its release-binding policy; generic release/recovery resolves
the registered family policy before any physical fencing. Harvest reuses its
normal exact binding, including unbound terminal/conflicted receipt exceptions.
No fabricated continuation or weakened authority. Focused13harvest-executor+
5stored-recovery tests pass (12s), includes registered-path positive/missing-action
negative; architecture/contract/size/style/diffcheck pass. Clean detached updated
source builds/verifies package5s. New SHA512
5f85d5e1c857ef84753d6880a6cb7da447a21cc77afd6bf678b011f728961250a90b05ac083ba60cf138d22b60940547046c1c6136d74e726c9279b9a48a47cb.
Preflight passes; second graceful RCONstop issued. Fresh deployment check remains.

2026-10-02 activity-wait repair: user accepted explicit admission/wake proposal;
main alone implemented local checkpoint79b6ec072ba3f7135fa23c648810c0fec197c079,
no push. ResidentActivityProcess now refuses actual unavailable meal clearance,
not only cheap source eligibility, before any scheduled transaction. Typed
Ready/Waiting explains food stock, source custody, service access, resident state,
work checkpoint, retained meal and movement handoff. Existing generic queue
parks the SAME durable action under resident/depot/settlement/work-owner keys,
with its existing1200-tick missed-signal audit; geometry and work-owner changes
now invalidate matching waiters. No new persisted state/event/schema/descriptor.
Invalid authority remains an error, never converted to Waiting. Owner food
admission explains economic refusals; scheduler has no food business logic.
Resident-life diagnostics expose activityAdmission/activityWait/activityWakeKeys.
Architecture and resident-life contract updated deliberately.
Focused30 frontier tests (activity6, coordinator5, meal10, queue9) plus1 NeoForge
diagnostic pass; modified real-occupancy queue regression rechecked after its
final edit (6tests10s build), not synthetic native acceptance. It proves1000
unchanged ticks check once/no admitted transaction, body clearance wakes same
action, queue reconstruction retains it, and executor-subject geometry signals
also invalidate. Architecture/contract/size/style/diffcheck pass.
Actual live snapshot567855 with candidate/executable contradiction confirms
new code returns MEAL_CLEARANCE for same residents10-3/11-3; not physical eating.
Clean detached `.pm` release source actually
`/home/rd/proj/pm-activity-wait-release.Cw4FyV/source` assemble/verifyPackagedJar
pass, preflight passes. SHA51291014277b7f12e6789f08d74ba3d6a1bd7b3d4237f71aac96259f0eea347191d3783b9b8ec06a65af06d11adca7be2f946dcbad8b360d7642d038f158ad697b7.
Graceful RCONstop07:51:01 saves all dimensions07:51:02; oldMainPID3553655 gone.
Announcement command executes but returns no RCON output (clienttimeout); chained
save-all was not reached. Actual stop/save logs, not that timeout, prove save.
Root checksum installer archives oldJAR; new unit invocation
2ce3dc6cef3040a6a155b0f19dc4f5cc launched07:51:16. World/seed preserved.
Fresh ready/deploy verifier and one ordinary fast-forward remain to check;
do not claim measured speedup or repaired clearance geometry from parking alone.
No full client/HUMAN_CANDIDATE claim. Farmer partial-batch yield remains NEXT.

Fresh read-only follow-up2026-10-02 07:24–07:37: user reports empty field/no
bread and slow5000-tick fast-forward. Same872c0321/MainPID3553655, no restart
or code edits. First visit07:23:51 emits exact harvest-scene-reconciled;07:23:56
same scene releases after confirmed harvest handoff. Existing harvest now terminal
CONFIRMED, epoch2GROWING (stage1at545937, stage3at551674), no active harvest.
Real depot377/65/14 contains64 wheat in slot0, no bread. Task
task:objective-stock-site-harvest-8-wheat-field-1-terminal-1 PENDING, no production
job yet; market-clear continuation due552000 (next WORK window), baker8-3FREE.
This settles retained farmer delivery, NOT whole bakery/repeated-cycle acceptance.
Resident8-11 consumption now traced07:23:52; old fixed-eating-point stall resolved.
By tick552693 bakery SAME delivered wheat job is HOT/PROCESSING,15 work ticks,
input in ACQUIRED station/OBSERVED_CURRENT, no pending effect/local block.
Trace07:34:25 handoff/07:34:26HOT,07:34:32 and48 bakery-effect-observed confirm
the next WORK window did start real processing. Bread completion not yet claimed.
Physical first crop377/64/-6 matches wheat[age=3]; the field is not allAIR.

Fast-forward request1 admitted540908→545908, COMPLETED07:28:36 after queue07:24:26
(~250s wall). Request2 rejected while1 active; remaining0, no failure/quarantine.
Telemetry4991 slices/5000ticks,229.66s total,229.51s advance,0.134s safety:
~45.9ms/tick exceeds20ms slice budget, almost one tick per slice. Cumulative WAL
1,846,430 writes,15,916.65s; repeated resident activity reschedules dominate
named transaction contributors. Exact snapshot counterexample residents10-3/11-3:
meal candidate=true, executable find=false, assessIDLE, held=false. Source
ResidentActivityProcess.nextReview retries hungry/no-meal every1tick, while held
checks only cheap candidate and SAFE_CHECKPOINT, not rejected geometry/admission.
Therefore unchanged unavailable meal retries cause durable no-op reschedule churn;
fix must give that rejection a causal wake/bounded retry without losing work/food
changes, not merely enlarge fast-forward budgets. Diagnosis only; no source fix yet.

Current priority: main alone repairs and deploys the confirmed Ironmeadow
meal/first-visibility/harvest authority defects. User explicitly requests systemic
code fixes and rollout; no subagents or world reset. Accepted farmer safe hunger
yield remains NEXT, not implemented: finish the pending cell, deliver a partial
wheat batch, shared activity selects eating, resume SAME job. Intermediate
delivery still hardcodes64 and lot identity assumes64-unit partitions.

Deployed implementation `872c03210430de5012b07dca7cca96d9b002b918`
(local, no push; includescfc44f5e), source clean. Fixes: reference chest creation starts only in
block-ticking chunks and confirms the exact own write/custody in its lifecycle
turn; CONSUME accepts a supported observed body outside the service boundary,
without requiring the eating waypoint; crop acknowledgement does not mistake
COLD history/field initialization/canonical projection for physical-first work.
New field-owned inspected reconciliation resumes a completed isolated scene only
with exact retained job/worker/hand resource binding and same AMBIGUOUS body epoch;
actual field, body provenance and hand inspected before emitting its WAL receipt.
No replay, stock creation, generic conflict suppression or replacement entity.
Focused frontier21 and NeoForge14 tests pass; architecture/contract/size/style
pass. Native reference-projection8 (including same-turn write/restart regression)
and field-turns13 pass. No whole-client/product acceptance claimed.
Additive receipt registration changed the descriptor fingerprint and would reject
the existing world. Exact schema220 BEFORE569e65ce→AFTER811327ab upgrade is pinned
in both snapshot-header selection and state decoding; no layout/old payload changed,
unknown/future registry pairs and physical lifecycle mismatches still reject.
Its positive/negative/re-encoding test passes; real existing snapshot decodes and
the exact job's inspected receipt reduces toHOT offline. This is not physical proof.

Deployment completed2026-10-02 00:47 local from clean detached
`/home/rd/proj/pm-ironmeadow-release.PN7NlW/updated`, assemble/verifyPackagedJar
pass; read-only preflight and fresh deploy verifier pass. SHA512:
`02df5767fa80629b1adfd7a794d0ab1374adc09ab95cc3f760f051cc240303e7fefea4519d6c4d1aa0bdf21c2080fc50ae80e26a6ac057bdacff2145187e7c28`.
RCON save-all flush before stopping; unit stopped in3s, old process gone.
Stopped144MiB world preserved in `.ironmeadow-fix-backup.3K5yVy`; exact current
world/seed unchanged, old JAR archived by checksum-pinned root installer.
Fresh ready00:47:03/runtime00:47:06; MainPID3553655, invocation
4a7aefe560c34670a7d33220edc89ea3, notBefore1790884013. No reset/push/public host
or client installer changes. Poststart rev218638/tick83238 green, required and
inventory/custody conflicts0, incidentIndex1. Players0: exact farmer scene remains
CONFLICT and8-11 CONSUME while their physical chunks are unloaded. Resume requires
naturally loaded exact body/field/hand inspection; NOT yet live-confirmed.
Startup retains38 former ambientHOT leases asUNKNOWN pending loaded-world recovery;
not a quarantine, but do not claim unloaded ambiguous work magically resumed.
Next useful check is actual Ironmeadow visitation:8-31 delivers same64wheat batch,
8-11 consumes held bread, no repeat scene conflict. Whole client acceptance open.
Original outer repo only pre-existing `.f0v-baseline/`; original nested history/WIP
and governance historical dirty state preserved. No subagents/test processes left.

New Ironmeadow live incident (2026-10-02, user first-visit report):29 hungry,
mass eating on arrival, harvest active without visible farmer, possible body
congestion. Same941a63ba service/MainPID3441827/world as above. Read-only
rev48088/tick36345 shows37 nourished/2 hungry,39 residents,26 bread and1 meal.
Exact job job:site-harvest-8-wheat-field-1, worker resident:8-31, has64/64
processed/64 carried/0 delivered and lease crop-6-r21160 CONFLICT. Log00:00:01
records field-work-accepted-cell-postcondition at revision21651. Farmer body
DOES exist (UUID c22a7ab2-39a0-3241-924b-2a744520b9f9, observed375/64/4),
physical offhand64 wheat agrees with canonical quantity; not the prior7-vs8
Clearwater incident. Snapshots19948/20740 prove64/64 completed in COLD before
first HOT admission, whose completed-job path admits while field initialization
can still be PENDING; old acknowledgePrevious wrongly requires ACTIVE owner.
Summarygreen/conflicts0 and scene not_found again miss this process conflict.
Resident8-11 also remains meal CONSUME, held, hungry; live actual body376/64/3
differs from retained eating waypoint375/64/3, navigatorIDLE, so old exact-point
consumption guard has no recovery movement. At rev187897/tick75540 stillCONSUME;
therefore do not claim all nonfarm feeding complete. Canonical census shows
39 residents and overlapping retained bodies8-36/8-39 at375/64/-1, not proof
of duplicated physical residents or a collision deadlock. Snapshot20740/tick28800
retains8-2 mealTAKE since22835 and depotPREPARING since6720, before player visit.
Source initial write used merely-loaded eligibility while its separate confirmation
requires block-ticking; this retains COLD stock indefinitely until physical demand.
Preserve world/evidence. The source fixes above supersede the read-only diagnosis.

Fresh farmer hunger inspection: user reports farmer works but may not eat.
Read-only Clearwater resident7-13 at rev11158/tick26384 is HUNGRY/satiety655,
meal NONE, activity WORK/pending SAFE_CHECKPOINT, bread46/claimed1. Exact job
at rev14300/tick27173 has22/64 crops harvested,22 carried,0 delivered, HOT scene,
no pending crop and no obstruction. Work is progressing, not old CONFLICT.
Source owner checkpoint explicitly returns CARRYING_RESOURCE while the field
actor account exists, so eating cannot begin until held harvest is handed off.
Scene-owned diagnostic itself reports SCENE_OR_AMBIENT_AUTHORITY, not the
owner's underlying cargo hold. This confirms current policy defers hunger while
carrying work output; it does not prove post-delivery eating. No edits/restart
or world mutation for this read-only check.

Fresh-world reset (2026-10-01 23:50 local): user confirms resident7-9 ate after
the waiting-pocket fix, then explicitly requests recreating the test world to
remove preserved old conflict. RCON save-all flush confirmed, service stopped.
Old active world was moved recoverably (NOT deleted) to
`/home/rd/far-frontier-server/.world-before-reset.1MSDDG/frontier-v3-food-r10-20261001`.
Recreated only exact active world directory, copying its existing graybox
datapack; no canonical journal, chunks, entities or player state copied.
Same level-name `frontier-v3-food-r10-20261001`, configured seed20260918065,
same verified941a63ba JAR/SHA512 below. Fresh-world preflight passes with
--require-world-absent. Live unit MainPID3441827, invocation
039d94bc4797470e9247fd8cb76cf740, notBefore1790880602, runtime ready23:50:15.
Deploy-verify passes. Fresh summary revision378/tick269 is OK/green:
12settlements,366residents,12sites,48bioforms, ambient/scenes/intents0,
all reported conflicts0, incidentIndex0. No old scene/job state remains in this
new world; this is a clean retest, not repair of the archived conflict. User
confirmation supports the second resident's observed eating, not full repeated
farmer HOT/COLD story. No push, code/config/client/public-host changes. Source
clean; pack's preexisting `.f0v-baseline/` and other histories/WIP preserved.

Second hungry resident fix/delivery (2026-10-01 23:46 local): user requested
diagnosis and fix for resident7-9, not a world reset. Live pre-fix evidence:
INDUSTRIAL_WORKER, MEAL/PREPARED at135/64/20, BODY_SPACE_OCCUPIED; nearby vanilla
query sees medic/industrial worker/engineer/logistician. Source contradiction:
AmbientPlacementPolicy.serviceZone discovers alternatives only through
occupiedPoint (service throat), so a retained waiting-pocket origin gets just
one candidate. Common SettlementServiceAccessPoints.placementPoint now recognizes
both exact service boundary and its declared waiting surfaces. Existing physical
provider still requires loaded storage, exact support, free body space and a
connected scoped path; no arbitrary remote spawn or live-body teleport.
Repeated origin candidate is deduplicated. Meal pending-effect/CONSUME relocation
restrictions remain. Regression covers waiting-spot HOT/COLD/re-admission,
retained claim/inventory, MOVE not false TAKE, canonical body and state recovery;
existing negative admission/meal/architecture checks reused.21 focused frontier
tests plus architecture/contract/size/style pass (21s).
Local checkpoint `941a63ba3cac90ba8118baf89c1bdc181664eb0e`, no push. Clean
detached `/home/rd/proj/pm-waiting-release.FWfJN2/source` passes assemble and
verifyPackagedJar (15s). Preflight pass, RCON save-all flush accepted, service
stopped and affected JAR installed with checksum-pinned repository installer.
World/seed unchanged. SHA512:
`630213236281fe21477e6105a5e4f994aafcd6e9fac33e0a3af999e9f998ef21923c77445bf7daa86bf8b4d03b12737bcf07625fe1d556e1032b258f69dff8a4`.
Live MainPID3435032, invocation6b1e400411c548c58036d3e63ea227bf,
notBefore1790880385, ready23:46:35; final deploy-verify passes.
Read-only rev62321/tick39190: resident7-9 no longer PREPARED at waiting spot;
canonical body135/65/14, meal TAKE, ambient CLOSED, physical admission UNLOADED,
meal action held, satiety508. This is changed canonical progress, NOT proof of
physical successful eating: natural HOT visit and consumption receipt remain.
Preserved farmer conflict remains unresolved; no implicit conflict deletion or
reset. Source clean, original pack only `.f0v-baseline/`, governance/original
nested historical WIP preserved. Build processes finished; intended live unit
remains. Available for targeted human diagnosis, not formal HUMAN_CANDIDATE.

Live follow-up (2026-10-01): user still sees stationary Clearwater farmer and
two hungry residents; suggests recreating world (not yet explicit reset grant).
Read-only exact process inspection rev27586/tick30513 proves preserved old
harvest lease crop7-r8571 remains CONFLICT, harvested/carried8, delivered0,
8/64 processed, next cell8; continuation due25440 is retained but blocked.
Actor is scene-owned, ambient CLOSED. Vanilla read-only entity NBT proves same
UUID941022a1-9431-381b-a497-bb9d553971ac tagged to that conflict lease/revision8571
still has7 wheat in offhand, whereas canonical carried quantity is8. Do not
accept the stack by kind/count or erase conflict. Summary green/conflicts0 and
individual scene not_found are incomplete diagnostics, not proof of recovery;
the previous delivery wording must be read with this correction.
Census at rev34006/tick32125 identifies hungry resident7-13 (farmer, satiety580)
and resident7-9 (satiety597, retained meal MOVE, atWaitingPocket true, access
available, meal action due27633 held). Bread45, claimed1. Second wait cause is
not yet closed. No mutations/restart/reset during this inspection. Existing
incident backup remains available; a fresh-world retest must not be called a
repair of this preserved conflict.

Latest delivery (2026-10-01 23:35 local): user explicitly authorized the local
checkpoint without push. Commit `2ff7bef6cbbb57323311a994e663f160c6a3c7f0`
contains the11 reviewed fix/test files below; implementation worktree clean.
Clean detached build `/home/rd/proj/pm-incident-release.mITZGU/source` passed
assemble/verifyPackagedJar with explicit graybox catalogue (7s, compilation cache
reused). Installed SHA512:
`e3bbc8a53ddf20dc3ef702265a7ef32e9cc016514c380da43200a3f83b5c07d57887d00335315a9d9dff84527fd02ab650b154c7818c5a9a0df8cb6af7980daa`.
Read-only deployment preflight and final deploy-verify pass. First immediate
verify was too early (port not yet open); final verification follows real ready
23:35:01 and Frontier runtime23:35:03. Live unit `far-frontier-v3-live.service`,
MainPID3412112 (wrapper), JavaPID3412136, invocation
`3c0c8eea0d304cef9a924a8ec4e23d66`, notBefore1790879690. Same world
`frontier-v3-food-r10-20261001`, same seed20260918065; no reset, no push,
no public host/client-installer changes. Before starting, the52MiB world,
crash report, previous log/properties/JAR were copied to
`/home/rd/far-frontier-server/.incident-preserved.cXV3Dv`.
Fresh summary revision11057/tick26345 is OK/green, required/inventory/replica
conflicts0. Read-only site at revision14062/tick27110 is HARVESTING epoch1,
growth7, exact old job retained, conflictDisposition null; resident7-13 is alive,
FIELD_HARVEST/WORK, yield SCENE_OR_AMBIENT_AUTHORITY. Startup retains20 ambient
HOT leases as UNKNOWN awaiting naturally loaded-world recovery. Therefore no
claim that the old farmer now completed/recovered its entire visible cycle;
player HOT visit and exact actor/resource observation remain necessary.
No new quarantine/error in inspected fresh log. Startup lag2s and scheduled
backlog are visible: do not claim stable20TPS or measured speedup. Available for
exploratory diagnostic visit, not formal HUMAN_CANDIDATE. The original Minecraft
pack remains only preexisting `.f0v-baseline/`; original nested/governance WIP
preserved. No temporary test JVMs left; intended live service remains running.

Current fix checkpoint (2026-10-01 23:32 local): main-alone WIP on f651dde8,
11 source/test files in `/home/rd/proj/pm-f06r3-facility-lane-recovery`.
No subagents, commits, pushes, live install or world reset. Queue held eligibility
now uses economic meal Candidate and an explicit eligibility assessment; actual
activity/meal admission still proves clearing geometry. Food selection precedes
geometry. One immutable identity-keyed bootstrap occupancy cache is bounded to
one entry; every route receives an independent mutable overlay. Clearance point
compilation and route selection share one knowledge view rather than compiling
it twice. No measured performance multiplier is claimed.
Static farmer finding: harvest release may retain an observed loaded body while
ResourceSiteHarvestHandRelease atomically unbinds its offhand resources; successor
adoption retains the same body's old physical stack and projectColdCarriedHand
rejects it as unwitnessed. This is a concrete code path consistent with the live
trace; old saved body's complete provenance has not been independently inspected.
Release policy now forbids live-body retention when releasing a bound hand:
existing exact inactive-carrier fence precedes WAL release, then old body discard;
existing restart recovery rejects/discards that fenced body and successor uses
the same resident/UUID. Empty observed terminal farmer retention is unchanged.
No item-kind/count-only acceptance or weakening of projection ambiguity guards.
Focused final verification: 51 frontier tests +12 NeoForge tests pass, including
blocked-clearance eligibility/admission distinction, mutable geometry cache
isolation, activity/meal/field/yield and architecture checks. verifyArchitecture,
verifyArchitectureContract, verifyLargeFiles, verifyJavaStyle, assemble and
verifyPackagedJar pass (33s). Native scene-restart-reclaim passes both required
tests; this is existing shared recovery evidence, NOT a complete farmer HOT/COLD
product story. WIP JAR SHA512:
`159e855925f54619eece991f0743a80ae71b57dee214fd78c871f749f572ece19b83ce6ebdaf1fddd20463cb00df3c6fb69973b9b8adb8c29fef78223a95fd1a`.
Live service remains absent; current R10 world is52MiB, preserved. Deployment
preflight requires a clean detached immutable checkpoint; commit authority needs
confirmation for this new fix. The persisted Clearwater conflict is not repaired
by these source changes alone. Next: checkpoint/release identity, preserve incident
world, deploy/start verified artifact, inspect exact recovery/conflict state;
do not label live farm fixed or HUMAN_CANDIDATE without that evidence. Original
Minecraft pack only has preexisting `.f0v-baseline/`; original nested documentation
and governance historical WIP are preserved untouched except this ledger entry.

Latest incident (2026-10-01 23:13 local): user reports jerky movement and farmer
stopping after probable HOT/COLD/HOT; confirms use of fast-forward. Source/JAR
is the f651dde8 deployment below. Service is now absent/MainPID0, RCON refused.
Watchdog killed the server23:13:23 after a60s tick; earlier lag2–22s is logged.
Crash report `/home/rd/far-frontier-server/crash-reports/crash-2026-10-01_23.13.23-server.txt`
captures server thread inside held-activity eligibility -> meal opportunity ->
clearance-target geometry -> whole-bootstrap structure occupancy compilation.
Source confirms expensive geometry/path selection occurs before checking actual
edible stock and is invoked again by activity assessment/held evaluation. This
is a source-proven costly hot path introduced by the clearance cut; profiling
has not quantified its share or excluded other contributors. Fast-forward may
amplify it, not excuse it. Separate farmer incident: lease crop63 released23:11:58,
crop7 re-admittedHOT23:12:01, then conflict `cold-carried-hand-unwitnessed-item`
23:12:06. Guard sees an already nonempty physical hand while COLD canonical
portion has no current binding/projection fence; exact stale-hand origin is
not yet established. No repair/restart/reset occurred; preserve world/evidence.

Latest delivery (2026-10-01 23:03 local): user explicitly authorized deployment.
Source `f651dde82795fb0ce1fb285b416658c27a4dedef` installed on the disposable live
server, including eef39f28/bc96b434/91fd059d and the pre-consumption clearance cut.
Clean detached source `/home/rd/proj/pm-food-release.FsiVTS/source` passed explicit
graybox-catalog assemble/verifyPackagedJar (8s); dependency-valid focused/native
evidence above is reused, not claimed as a new full product acceptance campaign.
Installed SHA512:
`13b3feb9d9d97a6fc271b658fc4a44d1bb7b14840aa6ed9589cd331410f829cb4510d99d2bb436c21ad15191512b14513b687c305c126dff5f0b23de39a0104e`.
Players0; RCON save-all flush confirmed, service stopped gracefully. State220
requires fresh world `frontier-v3-food-r10-20261001`, seed20260918065,
dimension `pale_mirror:frontier_graybox`. Old R9 world remains intact on disk;
properties/JVM arguments/previous JAR copied to `.food-deploy-backup.wEKe5K`
inside `/home/rd/far-frontier-server`; installer also archived the retired JAR.
No deletion, public hosting change, push or client-installer update.
Service `far-frontier-v3-live.service`, PID3351269, invocation
`fe4fd0cb1b1341d3bc0163b0fa78959b`, notBefore1790877792. Fresh ready23:03:23 and
v3 runtime23:03:25; deploy-preflight (fresh-world) and deploy-verify pass.
Read-only summary at tick1312/revision397 is OK/green:12 settlements,366 residents,
all conflict/custody incident counts0. Calendar agrees with Minecraft tick1312.
Deployment is available for exploratory retest, not HUMAN_CANDIDATE: no graphical
whole-meal story was verified this deployment. Implementation clean; outer
Minecraft still only pre-existing `.f0v-baseline/`, governance historical WIP
preserved. No temporary task-owned processes remain beyond intended live service.

Current assignment (2026-10-01): user authorized main-alone implementation of
the accepted resident activity/interruption and bounded-satiety plan, with SOLID
and dependency inversion. No subagents; the later explicit deployment grant and
its delivery result above supersede the earlier source-only boundary. Work
ordering is work/assignment separation -> owner interruption capability ->
activity lifecycle -> food opportunities/portions -> priority reevaluation.
Geometry correction is a scoped dependency. Existing placement WIP preserved.
The previous live baseline was9f979615/R9; it is superseded above.
Epoch14 carried64/delivered0 at645252–647548, but traces16:49:44–16:49:57 and
tick662227 prove it later delivered and epoch15 is GROWING4 without intervention.
Initial historic wait reason lacks a complete admission trace; FREE-window
admission rejects even cargo-bearing delivery while active HOT yield excludes
occupied hands, a confirmed static inconsistency, not proof of permanent hang.
Sixteen residents remain starving with305 bread; consumed meals are closed,
but mandatory afterMeal movement blocks another meal/work. Logs identify
buried intermediate target115/63/14; read-only block checks confirm roadY64
over foundationY63. Known settlement navigation omits current road footprint;
BLOCKED retains the same leg/route. Medic's separate135/64/5 stall still needs
exact leg/support evidence; do not claim all stalls share that target.
Accepted design/implementation order recorded in resident-life-resource contract
2026-10-01 amendment and architecture.yml: bounded satiety, separate starvation
health condition, portion meals, source access released after pickup/clearance,
interruptible optional home movement, common current geometry/replan, one
registered safe-yield policy for cargo. Numeric food balance remains an explicit
implementation choice. Existing placement WIP is not deployed. Historical
acceptance does not prove the new design. Current WIP has common road/local/
terrain supports, blocked-hint final-goal fallback and topology/delta cache
invalidation. Wired state-bound ActivityInterruptionPlanner port with movement
owner strategy: outside shared access, executable EAT/retained WORK interrupts
optional home travel through ActorMovementInterrupted, checkpoints actual/as-of
body, cancels old schedule and starts the next activity atomically. HOT purpose
retarget belongs to orchestration. Held activity wakes on actor AND depot change.
Generic workYield no longer checks all cargo; registered farm/bakery capabilities
own pending-effect/cargo assessment, shared by HOT admission and yield.
Checkpoint commit `eef39f28` now preserves this placement/interruption source cut;
it is not a release or deployment. Commit `bc96b434` preserves the next bounded-satiety
source step; worktree was clean at that checkpoint, no push/deploy. It replaces missed-meal deficit
with bounded satiety in the active nutrition state, event, selectors, food reserves,
diagnostics and codecs. Production selector is R8/schema10; state schema217
rejects earlier test worlds. Balance: capacity1000, eating below668, target900,
bread nutrition1000, one nutritional unit per72 ticks at metabolism1000.
Default hunger onset remains about one day (23976 ticks); prolonged empty stomach
cannot accumulate food debt. Need wakes target hunger onset, empty stomach and
the later daily health integration seam, not every nutritional unit. Empty state
drops fractional debt; long elapsed intervals saturate before multiplication.
Focused interruption/engine/road/activity,
affected existing meal/field/runtime and codec checks pass; Java compile,
architecture/size/style pass. One missing diagnostic payload registration was
caught and fixed. The bounded-satiety affected run passed66 frontier tests and
one NeoForge resident-life diagnostic test, architecture/size gates. The final
focused nutrition/need/metabolism/codec/ruleset pass plus NeoForge compile and
architecture/size checks also passed (20s), covering post-run hydration/numeric
validation edits. Old fixed-clock nutrition fixtures were updated without changing
their intended due instants. Following main-alone uncommitted WIP implements the
accepted minimal starvation condition in ResidentHealth, independently of disease,
nutrition and ActorCondition vitality, with no gameplay penalties. Its dedicated
owner emits exact predecessor-fenced health facts before need, HOT/COLD consumed
receipt and metabolism transitions retire the nutrition interval. The existing
need schedule remains the only clock; unchanged health effects emit no event.
R9/schema11, state218: severity0..1000, gain1 per240 empty ticks, recovery1 per120
ticks at satiety>=668; low positive satiety prevents growth but does not heal.
Diagnostics expose effective/stored severity without mutation. Pure split/rate,
long-interval saturation, gradual recovery, disease independence, payload/state
recovery, stale/foreign facts and actual need-engine transaction checks pass;
the affected41-test frontier pass plus architecture/size checks passed (26s).
A final focused HOT-consumption/restart + health pass passed (12s); the unchanged
diagnostic test reused its green result. Nutrition/meal/metabolism now use the
`NutritionIntervalEffects` port; only `ResidentPhysiologyComposition` names the
health implementation. The post-binding affected tests, NeoForge compile and
architecture/size checks also passed (25s). No remaining blocker for this minimal
health-condition step. Following user-authorized main-alone food WIP implements
FoodCatalog in hashed ResidentLife rules and an explicit FoodPortion in each meal.
Production registers bread only (nutrition1000/max64). The active opportunity
query uses as-of satiety and unclaimed stock; portions retain item kind, nutrition
and exact multi-lot quantities, with incomplete stock admitted rather than held.
Reservation, shared actor-item transfer, HOT hand projection/release, consumption,
need requeue and reference closure use the retained portion. HOT preparation
fences all selected source slots/counts under one epoch; consumption fences its
exact hand quantity. A leftover one-bread constraint in ClaimAllocation was
removed: the meal owner validates registered edible policy, not the ledger.
R10/schema12/state219 now select this grammar; older test worlds are rejected.
Diagnostic output includes meal kind/count/nutrition. Existing bread production/
export policy remains intentionally separate; no diets/preferences/spoilage.
Affected resident meal/activity/life/need/metabolism/starvation, persistence,
ruleset and bakery tests plus NeoForge compileTest/architecture/size passed
(2m27s). New partial-stock and less-nutritious multi-lot/multi-slot HOT/COLD
component tests, receipt/codec/replay rejection and affected fungible tests passed
(30s). Final food/portion/persistence + NeoForge resident-life diagnostic,
architecture/size/style pass (17s). Invalid new fixture assumptions were corrected;
no weakened assertions or infrastructure campaign. Source/governance diff checks
pass. Commit `91fd059d` preserves the food/starvation source cut (47 files);
checkpoint reused this focused evidence, no push/deploy. Unrelated governance WIP
was deliberately not staged with implementation.

Following the user's commit-and-next-step request, main alone now implements
pre-consumption source clearance, state schema220. Active meal path is
MOVE -> TAKE -> CLEAR_ACCESS -> CONSUME -> retirement. The portion remains in
exact actor custody during clearance; the common pedestrian navigator executes
the declared goal. ServiceClearanceTargets selects a reachable declared free
pocket, never a mandatory home target; reservations constrain final destinations,
not terrain. Access ends at witnessed boundary exit before eating; nutrition
changes only on the consumption receipt. That receipt wakes activity selection
without creating afterMeal home travel. The obsolete afterMeal factory is gone.
ResidentActivityProcess alone retargets HOT purpose after food receipts; the meal
owner only changes food/body/stage. HOT hand projection/release covers clearance
as well as consumption, and route replanning uses the observed physical start.
The affected existing meal/food/activity/starvation/persistence checks and
NeoForge compileTest passed (29s); final expanded handoff/recovery and architecture
checks passed (32s, `/tmp/pm-meal-clearance-final-r4.log`):42 frontier tests and3
NeoForge tests, plus architecture verification. Size/style/architecture-contract
checks passed (4s, `/tmp/pm-meal-clearance-guards.log`). Commit `f651dde8`
preserves this22-file clearance cut; implementation worktree is clean and no
task processes remain. Unrelated governance WIP remains outside both commits;
outer Minecraft checkout still only has pre-existing `.f0v-baseline/`.
No native acceptance,
deployment, world reset or push is claimed or authorized. Remaining individual
capacity characteristics and any further owner extraction stay separate from
this source-clearance cut; do not widen into an infrastructure campaign.

Active scoped repair (2026-10-01, main alone/no subagents): user accepted
fundamental COLD/HOT placement + service occupancy separation, requested SOLID
implementation. Checkout `/home/rd/proj/pm-f06r3-facility-lane-recovery`,
base9f979615; placement implementation preserved in `eef39f28`, no deployment this turn.
Added epoch-bound `AmbientBodyConfirmed` (ADMISSION/SERVICE_OCCUPANCY), closed
ambient placement Strategies and typed service-point geometry; ambient admission
now confirms actual supported body atomically before HOT. Minecraft placement
checks bounded connected free candidates, support, physical collision and body
occupancy, with no force-load or HOT teleport. Meal-owned hook preserves claims
and reapproaches after relocated unbegun TAKE. Coordinator excludes PREPARED
phantom occupancy; denied arrivals can exit, actual HOT occupants arbitrate
ahead of COLD endpoints, idle actors clear the point through shared navigator.
Scene-reserved pre-lease confirmation holds rather than arms ambient motion.
Canonical execution/resource contracts and architecture map updated; scope is
resident depot access, not every scene family. Live server remains9f979615.
Final focused verification passes93 frontier +28 NeoForge tests, guardrails,
assemble and packaged-JAR verification (`/tmp/pm-service-handoff-final-r3.log`).
Native local-navigation28/28 pass (`/tmp/pm-service-handoff-native-r5.log`),
including occupied/free/disconnected/fully-occupied placement; prepared recovery
3/3 pass (`/tmp/pm-service-handoff-prepared-r5.log`), actual carpet support,
UUID continuity and exact hand hydration. Native processes stopped/saved.
Checks exposed missing deterministic command/wake registration for the new
receipt (fixed), model dependency/1000-line violations (fixed without raising
ceilings), old bare-HOT fixture commands (updated explicit unit/component
receipts), stale schema213 expectation (current215, all old tags rejected), and
a construction census misclassifying a dimensions query (now compiled create/
constructor calls). Old descending fixture now uses the shared physical cycle;
carpet assertion now checks actual supported collision top rather than floating
one block above it. Broad initial Java check failed on these recorded causes;
all failed classes plus changed owners were rechecked focused, not another full
matrix. One obsolete failed-build test worker was terminated; no live process
was touched. `git diff --check` passes. No new live/client/product acceptance
claimed; server remains9f979615. Source WIP and unrelated governance/outer WIP
preserved. Original outer Git still only has pre-existing `.f0v-baseline/`.

Live follow-up incident after9f979615 (2026-10-01): user reports brief movement,
security stalled at depot, farmer appearing beside entrance, then stationary
residents. Read-only RCON: runtime remains green, ticks448521→457504,
no required/inventory/custody conflicts. Clearwater resident:7-4 physically
occupies depot service body135/65/14 but retains canonical104/65/-30,
mealMOVE, serviceAvailable=false. Resident:7-11 retains135/65/14,
mealTAKE, serviceAvailable=true, ambientPREPARED and no physical body.
Exact source explains circular wait: ambient meal arrival stops7-4 before
checking hotArrivalHasServiceTurn; denied arrival is not recorded and leaves
it physically in the socket. Known meal navigation also returns singleton
start when already at service, without checking admission. Coordinator grants
the turn from canonical positions to7-11, whose physical admission rejects
the occupied body volume. Neither participant can advance. Admission UI
READY checks standing geometry but omits foreign-body occupancy, so obscures
the deferral. goalPositionY64 is SUPPORT, observed/bodyY65 is FEET: normal,
not evidence of a height defect. Farmer7-13 is PATROL/IDLE at135/65/13;
its reported appearance provenance and separate home-route stalls remain
unresolved. No code change/restart/reset in this diagnosis; preserve live
failure. Next repair must reconcile physical occupancy with service arbitration
and provide a legal yielding path, not relax arrival or allow overlapping bodies.

Previous live deployment (2026-10-01 14:00 local): user requested deployment of
the single HOT pedestrian-navigation correction. Source checkpoint
`9f97961558ff8165ed7a9635dd08b6a9feedc01f`, implementation clean; no push.
Clean detached build `/home/rd/proj/pm-single-navigation-release.Zk6J7D/source`
passed explicit graybox-catalog assemble/verifyPackagedJar; applicable focused
native/architecture/guardrails evidence retained below, no confidence reruns.
Installed SHA512:
`fba095ca2ab841bf9775844b0ab0e563656c6929eadaa61b0effb26e5000bd060e4b0aba8bc5125ccf818ffe85c61e9850b2fd0782a83756e01c32fcbfbb477c`.
Preflight passed, players0, save-all flush completed before graceful stop.
R9 world preserved, stopped-world backup
`/home/rd/far-frontier-server/.single-navigation-deploy-backup.qP3NmJ/`.
Checksum-pinned root installer archived the old JAR; no public publication,
client-required change or world reset. Service invocation
`2d3b2c2272644eb3b50ead37016a2822`, MainPID1441728, notBefore1790845246;
fresh ready14:00:57/runtime14:00:59 and deploy verifier pass. Live summary
revision367656/tick445919: OK/green, requiredConflicts0, inventoryConflicts0,
replicaCustodyDiagnostics0. This supersedes all not-deployed/WIP and33c3 live
statements below. Clearwater real-player passage/meal/farmer acceptance remains
open; healthy deployment is not HUMAN_CANDIDATE. Outer repo retains only
pre-existing `.f0v-baseline/`; governance historical WIP preserved.


## Authority and next boundary

Main alone implements; Terra stopped and subagents prohibited. The active source
is `/home/rd/proj/pm-f06r3-facility-lane-recovery` (monorepo), Java build under
`pale-mirror/`; do not migrate either original Minecraft workspace Git history.
Canonical instructions/contracts remain in this governance checkout. Preserve
all histories, unrelated WIP and live worlds. The requested deployment is now
completed as recorded above; no publication or push was requested. The SA audit is
paused; ARC-001 remains the mandatory adoption checkpoint after the current
F0.6R3 repair, not an instruction to widen this cut.

Previous implementation/incident chronology is preserved in
[the archived ledger](docs/archive/continuity-before-service-placement-20261001.md).
Its older live-build claims are superseded by the latest deployment above.
Current sparse COLD travel, canonical daylight, unit navigator, resource custody
and meal/work owner protocols remain in scope as existing dependencies, not
new optimization campaigns. Existing home-route stalls and the reported farmer
appearance need their own evidence; this repair does not claim to close them.
