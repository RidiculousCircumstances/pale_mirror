# Continuity Ledger

## Objective / authority
Active user goal: fully implement docs/work-orders/PM-FORESTRY-20261025.md:
independent compact forest accounting, wood reserves without consumption,
iron-crafted axes, physical HOT and equivalent COLD logging, uneven forests
around three existing towns (two agrarian, one mining). User explicitly asked
to create goal and implement. Goal active, no token budget. Main alone, no
subagents/Terra/push. Finish verification, commit and deploy fresh test world.
Implementation WIP on b63674727; no commit/deploy/push, live server unchanged.
New r28 has explicit forest patches around towns7/3/6 (Clearwater/Dawnbridge/
Hearthvale), iron axe manufacture/repair via existing forge, CHOP and oak work.
Compact TreeBlocks (4-byte relative logs/palette); sparse ForestState source
partitions/jobs integrated in state/update/schema306/codecs. ForestTree distinguishes
external loss from work aftermath and retains exact current extraction operation
IDs. Generated natural forest is lazy, never a permanent whole-forest log index.
Graybox worldgen uses the same immutable forest plan and GeneratedForest chunk
attachment; native TreeFeature RETURN hook captures actual successful log sets
(no flood fill) for later bounded observation/knowledge admission. Growth hook
added but not yet native-verified. No TreeChop dependency.
Real ForestryWork admission/progress/COLD reducer now registered: tool take,
shared semantic route, labour, bounded tree consequence prefix, tool wear +
fungible output, multiple-tree cargo, dynamic storage, tool return and UAE finish.
FORESTRY actor/assignment/carry/service/movement/equipment/staffing/relationships
and container physical fences registered. Extracted common WorkCargoOrders,
WorkStorageSelection, ActorWorkCargoCustody and SourceReplicaProtocol; original
mining uses common cargo/source/hand protocols. Ordinary native block loot shared.
Native ForestryExecutor/WorksiteOwner/BlockWrites/DeathResources adapters added to
closed existing registries, with actual child receipts, external-write capture,
source leases, no force-load or alternate navigator. Still WIP: inspect/review
connected recovery/retirement/tool/storage cases and source epoch transitions,
not merely compilation. Natural geometry is regional/lazy and revokes old
projection authority before new generation admission; dynamic directory includes
captured trees within explicit known patches. Need verify native whole-tree/handoff,
regrowth/bonemeal and damaged-source scenarios, full finite reserve, actual axe
manufacture, retention/metadata growth and performance, then release/deploy.
Verification (still WIP, NO release/commit/deploy): focused connected checks
build/forestry-connected-checks.log PASS47s:76frontier tests +12native unit tests,
guardrails/architecture19 and native/pilot compile. Covers whole-tree COLD credit,
wear/delivery/recovery, external suffix invalidation preserving earned cargo,
regrowth/newID and source fences. Full critical gate build/forestry-release-gates.log
ran1888frontier +861native +408GameTests in11m17:8frontier closed-composition/fixture
expectation failures and2native failures subsequently repaired by the focused
checks; GameTests406/408 passed, first-visibility tight-loop test rewritten to
allow real async server turns (not yet rerun), unrelated old combat calibration
20k-tick timeout remains unresolved. Do not call the full gate green.
Native exploratory read-only scenario now checked in:disposable-forestry-hot-cold.
One private Xvfb only (physical:0 unavailable), capped20FPS; frames NOT human
visual acceptance. Several invalid method starts were corrected:position above
landed feet; too-long ordinary-world prelude; editing test source invalidated
prepared identity. Do not edit ANY source/tests while current native run lives.
Frozen exploratory carrier on old adjacent pack proved actual5HOT log effects,
wood5/tool250->245, exact same actor and next-tree selection, then refused6k
advance because a different resident still held physical body authority. Narrow
proof only, no whole-loop acceptance. It also exposed stale adjacent datapack:
vanilla flat skipped initial natural forest and common projection could create
bare source logs. Fixed explicit owner InitialPlacement.GENERATION_RECEIPT so
forest cannot be created from a bare host; mining retains KNOWN_HOST policy.
PACK datapack ONLY ruleset selector changedr27->r28; other pack files untouched.
Use explicit ORG_GRADLE_PROJECT_frontierV3PilotGrayboxDatapackDirectory=
/home/rd/proj/minecraft/datapacks/pale-mirror-graybox for native runs.
Current native exec20308:build/forestry-native/generated.log and generated-hot-cold
manifest. Profileforestry-equipped declares2INITIAL axes only (no fake work/wood),
separate normalforestry-world has no injected tools. Prelude1200, HOT90s, ordinary
leave, runner-owned normal demand loss and4kCOLD interlude, graceful restart,
return/current state. Source/rootdatapack frozen. Initial forest frame17:23:33;
inspect actual frames/work/cargo/restart/performance before accepting.
Native ordinary sapling growth/negative player case still need proof; newgrowth
hook compiled/loaded but no successful sapling native evidence yet. Finite reserve,
ordinary forge manufacture chain and connected tool/storage retirement still need
end-to-end confirmation. Never equate equipped fixture with manufactured tools.
No work pushed, committed or deployed; liveR263 unchanged. Latest live read308360
normal, native20/noacceleration, mean64ticks4.2ms/maxrecent82.19ms during concurrent
local testing; lifetime223ms remains prior unknown peak, NOT80ms acceptance.
Latest follow-up (17:43 local, still WIP): normal-demand loss receipt falsely
checked ONLY harvest scenes; now delegates existing shared requiresPhysicalStep,
so a RUNNING body of another actor cannot acknowledge global COLD admission.
Focused fast-forward/body checks+guardrails PASS14s. Correct-pack native generated
forest frame17:23:33 proves real canopies/biome terrain; actual HOT3/5prefix,
wood3/tool247. COLD interlude again refused prior false receipt; not accepted.
Added transactional ExactItemDepartureReactions: broadcast complete inventory
facts to explicitly registered consumers (no extension of old owner-inference
resolver). ForestryToolDeparture closes only unacquired exact tool work/current
or suspended execution and its own movement/schedules atomically before inventory
publication. Moves within same chest preserve work. Exact event tick now passes
through composed player edits.27focused forestry/container/departure checks PASS28s;
new parser/client44checks PASS13s. No native tool intervention proof yet.
Added reusable bounded ordinary client use_item_on packet action (no direct world
writes), extracted shared placement/item selection to keep TestPilotClient991lines.
Growth scenario disposable-forestry-growth-intervention:real sapling+bonemeal,
new source count, player break/non-resurrection/place; profile normalforestry-world
(no initial axes). First native attempt grew real trunk but knownTrees stayed695.
Static pinned MC source proves TrunkPlacer.setDirtAt uses the trunk setter too:
TreeFeature local set1 includes dirt under root, not just logs. Capture now filters
actual BlockTags.LOGS before supported species/profile validation, and reports
structured capture stages. Added live ServerLevel getChunkNow lookup rather than
EMPTY future for growth (WorldGenRegion retains non-loading read). Compile/gates
in progress:build/forestry-growth-capture-compile.log. Need rerun growth then HOT-
COLD/restart with ALL source/tests frozen. Prior attempts invalid/failed are not
acceptance. No client/native currently active.
First-visibility corrected GameTests9/9 PASS17.87s (build/forestry-physical-turn-
gametest.log). Clean baseline ef0bf8892 also fails SAME old combat calibration
20k-tick timeout plus2old scout tests under native-regressions: RELEASE/build/
forestry-baseline-native-regressions.log,3m4. Source clean, no baseline edits;
these non-forestry failures predate candidate, do not claim fixed or green.
PACK only one tracked datapack selector editr28; implementation~147main-owned
paths dirty; NO commit/deploy/push. Prior native sessions stopped with
all-dimensions saved. Next finish actual growth and recovery, assess full reserve/
ordinary axe manufacturing +performance, final affected gates/review/commit and
verified fresh deployment. Preserve unrelated governance/pack dirt.

Prior economic assignment: «Понял. нужно приступить к централизованной доработке, приступай».
Implement the accepted coherent economic cash-cycle correction, verify actual
flows, commit and deploy. Main alone: no Terra/subagents, no automatic push,
no inferred goal. User authorizes disposable worlds; never inject money/resources,
force receipts or replay ambiguous native effects. Russian concise updates<=60s.
Do not stop at helper/unit-only delivery. Archive of detailed investigation and
this implementation: docs/archive/continuity-20261025-r263-integration.md.
Canonical AGENTS/protocol/architecture/execution semantics retain authority.

## Repositories
IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror (Gitroot parent),
branch feat/baker-carry-orders-20260926 HEAD b63674727 DIRTY with forestry implementation (main-owned WIP).
Economic implementation592bfe82d, connected navigation repair ef0bf8892.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror clean detached ef0bf8892.
PACK /home/rd/proj/minecraft trackedclean9ab384e; preserve .f0v-baseline/.
Governance unrelated WIP must be preserved; never stage-all. Canonical/source
architecture operating_cash_cycle_boundary deliberately updated. No push.

## Current live identity
Runtime /home/rd/far-frontier-server, transient unit far-frontier-v3-live.service,
wrapperPID2890498, start1792919575 (~14:12:55Oct25).
World frontier-v3-r263-cash-cycle-20261025, UUID895efd44-ccff-42c8-a1f3-8eb6547657c2,
seed126/r27/schema305. Build/package and normal startup verification PASS:
IMPL/build/r263-package.log, r263-fresh-{publish,install,preflight,verify}.log.
SHA512 b6817a716bada12225e999a701a0a403e457af998784b0a08b6a13c6f24b58f2e0ff09a28e9d83ac3d415b8758c08e3dd0d4edc3bcc1e8727143f8141f70c193.
NO native client now. rd was online in oldR260 before deployment and was informed
of restart and REQUIRED client updater: biome source decodes newr27 selector.

## Implemented / verification
Report IMPL/docs/economic-cash-cycle-r262.md; cause investigation
IMPL/docs/economic-cash-cycle-20261025.md (0a3e89ed0).
1. Equipment owner declares healthy-tool renewal materials/heat through closed
ReserveSupplyPort. Common EconomicFunding publishes one operating cash envelope,
net of stock/dated arrivals and complementary protected requirements. Procurement
protects other inputs; actual immediate food may substitute unbacked future work.
2. StockSalePlan uses explicit stable retained-margin/operating-recovery purpose,
no ownerless default. One bounded commodity proposal must plausibly finance whole
operating shortfall; actual receivables bound exposure, never fund purchases.
Recovery spans64-unit contract parcels inside larger mission. Price review reads
uncapped policy surplus, admission applies exposure cap. Additional production
retains its independent profitability gate. No cash/stock issuance or subsidy.
3. Full mission-sized advice and common ProcurementSelection for timely/late
recovery/crew-adjusted costs and deliberate waits across lots, modes, warehouses
and rival providers. Current quote envelopes permit mission quantities.
4. New profile r27 restores coal startup2/3 instead historical20/30 after fuelheatx8.
Old selectors immutable; initial1200cash/endowment unchanged, no permanentfloor.
Schema305 retains operating budgets/sale purpose and rejects old worlds.
127focused Frontier/architecture checks +2NeoForge diagnostic checks PASS,
current-format recovery/old-format rejection and guardrails PASS. Receipts
build/r262-release-verification.log, r262-final-guardrails.log,
r262-architecture-check.log. One stale test required the BUY cash clamp removed
in R260; updated to verify willingness vs actual invoice instead. Unchanged native
physical execution evidence reused, not claimed as new graphical acceptance.

## Failed first run / connected fix
R262 fresh startup592bfe82d/JARd1f8bb... was verified in world
0886eb55-012e-4266-891c-377c99683f42. First24k operator request FAILED before
completion: navigation pool RejectedExecutionException at14:10:05 (2active/8queued).
Product fault. Log build/r262-failed-navigation.log. Final All dimensions saved
14:10:07. World retained. R260 world also retained; its stop exited0 but final
log ended Saving players, so do not claim full native save evidence for that stop.
Root cause: cancelled/invalidated slices recycled their logical executor slot
before actual worker return, overfilling the bounded pool. ef0bf8892 retains each
retired inFlight entry until poll obtains real return, discards its output and
counts spent work. Close cancels all retained flights. No enlarged queue,
CallerRuns fallback or swallowed executor error.47focused navigation worker,
fairness, demand, wakeup and invalidation checks +guardrails PASS12s.

## Actual runtime verification result
R263 build/r263-soak.py finished.3relative24k requests COMPLETED (reached24418,
48985,73320). Request4 REJECTED at89894 because ordinary player rd enteredHOT and
resident6-13:presentation-demand required physical work. This is the declared
barrier, not a product crash or a completed4th request. Native20 restored and
fastForwardRemaining0. No further acceleration. No task client running.
Actual collector sample91333:355residents,10hungry (165@24592 ->20@49036 ->15@73938;
need counts fluctuate);4paid receipts(coal2/9/36,wheat50),122bread produced.
Exactly1200coins conserved. Mossbrook balance/free96 after actual4coincoalreceipt;
Southgate balance100/reserved98.82/free1.18, not100spendable. Actual snapshots
retain operating maps/stock/prices/tool condition. Files build/r263-final-
{economy.json,snapshot.bin,operating.txt,performance.txt},r263-soak.log and
soak-performance.jsonl; interval collector rows r263-segment-{1,2,3}-economy.json.
Current read-only probe EconomicCashCycleRead.java/build/r263-tools.
Final navigation waits/searches/flights/workerqueue all0; no newERROR/quarantine.
Player-connected tick query mean2.7ms/P996.7ms; recent64host max12.55ms. Lifetime
whole-host peak223.19ms remains unexplained by coarse attribution and NOT80ms or
5s acceptance. Reportdocs/economic-cash-cycle-r262.md records limits and outcomes.
Implementation delivered; E5long-term balance OPEN:144k planned window not
completed, no new distressed-town recovery sale or terminal tool-restoration
native outcome in this observation. Do not infer equilibrium from a fresh start.
Final report committed b63674727; final guardrails PASS10s. Executable remains ef0bf8892 (documentation-only successor needs no new binary deployment). Final health94930: native20, mean2.2ms/P9911ms, required/scene/inventory conflicts0, no new quarantine. No code work, client or acceleration running; rd online.

## Tools / operational boundaries
RCON python3 /tmp/pm-read-rcon.py COMMAND...; never print script secret.
API http://100.100.78.29:8093/api/view?from=0; verify UUID; latest sample can lag
actual diagnostics. Copy rotating snapshot before decoding. Current305 uses IMPL
classes. Old304 snapshots require build/r260-diagnostic-artifact.jar; old303
requires build/r258-diagnostic-artifact.jar. Never hydrate old worlds with305.
JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
Runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Fresh deploy scripts build/deploy-r263-fresh.py; same scripts pin detached source,
JAR/profile/seed/world and checksum. Future stops: RCONstop, wait natural Java exit
and final save evidence BEFORE systemctl cleanup. No client currently; if needed
use existing pilot20FPS on demand and shut off between checks.

## Remaining limits
Actual long economic outcome E5 remains OPEN beyond this bounded observation. Prior R260
174-178k:81hungry, Southgate37/41/Moss38/39 with excessgrain/healthytools/cash0;
1200cashconserved. Source-only comparisons are not delivered caravans. Prior first
visit8.798s/projection1.834s/nativepeak1.865s remain performance limits, not strict
5s/80ms acceptance. No native scale/physical limits increased by this task.

R263 first2 requests COMPLETED (actual sample49036), no new quarantine.2paidcoal
receipts, Southgate/Mossbrook retain money. Hungry165@24592 ->20@49036; actual
food consumption progressed, initial routing pressure drained. Bread production0
at49036; active station-loading jobs and in-flight input trips need terminal check.

## Follow-up:223ms cause (read-only)
Exact log:14:27:50.637 rd login ->136.27ms tick14:27:50.867;
teleport to140.5/68/-25.5 at14:28:00.371 ->223.187614ms whole-host tick
sample235872 at14:28:01.605. Same-tick longest instrumented inner operation only
46,006ns(kernel.schedule_rescheduled settlement2). Whole-host maximum is not
causally attributed to economy/navigation by this coarse span. Correlation with
login/teleport suggests native area work, but exact chunk/GC/method cause UNKNOWN.
Verified same live JVM2890522 (child of wrapper2890498) JFR.check: no recordings;
VM.log: only default stdout warnings, no retrospective GC/safepoint history.
No source/runtime/world/rate mutation or new profile capture in this follow-up.

## TreeChop source audit (discussion only; no implementation/deploy)
Inspected tag0.19.3 commit2b77b906b89cbd2882114c1e83dae178a1f0b028 and published
NeoForge1.21.1 JAR (Modrinth iLXWmfax, checksum-verified /tmp/pm-research-treechop-0.19.3-neoforge.jar).
Source /tmp/pm-treechop-source-0.19.3; primary URLs github.com/hammertater/treechop/blob/0.19.3/...
Default TreeDetectorBuilder expands17horizontal/above neighbours per log;
LazyTreeData is per-call lazy search with HashSets/PriorityQueue flood, early
hasLeaves/readyToFell, full numChops/getLogBlocks/leaf traversal. It is geometric
connectivity, not persisted biological ownership; touching trunks may merge.
Config actual default maxTreeBlocks1024 (NOT old sample320), leaf distance7.
maxLeavesBlocks1024 is declared but is not enforced as a traversal limit in the
inspected LazyTreeData -> FellTreeResult path. Base chopped-log flood has no
maxLogs cap; streamLeaves eagerly materializes a LinkedList. No per-tick time
budget or global getTree cache. Native Level.getBlockState/getBlockEntity calls
lack loaded-chunk fences; checked MC1.21.1 Level.getChunkAt/getBlockState path
requests full chunks. Do not call on worker/native-unloaded territory directly.
Static work estimate17N neighbour probes before leaf work; PriorityQueue log
factors and memory O(N+L), not a measured millisecond benchmark. Whole-tree
breakLogs/forEachLeaves are synchronous. Recommendation (NOT user-approved
implementation): reusable bounded snapshot-based detection and compact per-tree
cached descriptors, or generation capture; common PM physical-effects/custody
must remain authority. No new forestry code, dependency install or server change.

Forestry follow-up17:51: growth-owner-fixed native receipt statusOK17actions;
real sapling+bonemeal registers new tree, player break remains AIR and replacement
log persists without forestry output. All dimensions saved17:50:13. Exact observed
crash repaired: extraction reconcile used current geometry BEFORE filtering foreign
forest provenance. Common ledger now externalWorksites(explicit OwnerFamily), both
owners request only their records; own missing geometry remains error. New dispatch/
recovery test added. First focused compile collided with concurrent native prepare
(missing classfiles); method failure, rerun serialized after active build. Do not
parallelize Gradle mutations in same output directory. Full check/build exec5830
still running unrelated old hive fixture test (jcmd retained), not hung source
compile. New native exec47047 handoff-owner-fixed log/receipt active, correct pack,
camera(-6,0,6), equipped INITIAL2axes only. Source/tests/scenario frozen until end.

Power-loss interruption2026-10-25: previous host journal ends18:00:23+05 without
normal shutdown; new boot21:08:10+05, no retained OOM/panic cause. Active native
exec47482 handoff-retained-proof is GONE, no terminal manifest; test NOT accepted.
Source changes have no NUL and distributable ZIP CRC is valid. No task client/JVM
survived. Prior focused retained-proof gate48tests+guardrails/build/package PASS28s;
full check/build prior PASS12m23. New common WorksiteEvidence.retainCurrent now
records positively observed generation witnesses BEFORE durable source confirmation;
forestry+mining use it, addressing COLD release requiring raw journal proof after
virtual generation acquisition. Need native rerun, ALL source frozen during run.
Live oldr263JAR SHA unchangedb6817a...; transient service lost at host reboot.
Restarting SAME old verified server to verify ordinary crash recovery; no fresh
forest deployment yet. Retained old log also contains repeated REJECTED_BY_POLICY
for work:extraction-79, declared storage filter refuses this resource portion,
physical operation9a7afdcb-07f0-4440-8c04-a92f026e9c4c. Capture/diagnose before reset.
Temporary /tmp helper scripts lost at reboot; use checked-in runner/query libs or
recreate bounded helpers without printing RCON secrets. Both root/source WIP intact.

21:13 host recovered; oldR263 server restarted as transient unit, SAME verified
b6817aJAR; summary revision1358149/tick352841 green retained-conflicts0, normal
crash recovery retained27HOT leasesUNKNOWN, no fabricated progress. New native
exec17978 handoff-post-power is active/frozen, same scenario/retained-proof code;
prior interrupted47482 not accepted. New helper build/forestry-native/query-runtime.mjs
uses stored RCON credential silently, args propertiesfile+command.
Important connected concrete bug diagnosed READ-ONLY from oldworld snapshot copy:
build/forestry-native/old-storage-refusal-probe.txt. Mining work79 RETURN_TOOL,
pendingEquipment slot smeltery-metals, toolstone_pickaxe while destination accepts
onlycoal/rawiron/ingots. Its prior toolSource quarry DOES accept picks. Native
preparer can start a tool return at previous cargo destination BEFORE scheduler
retargets it; WorkEquipmentOrders.create checks slot/custody but ignores filter,
so physical effect completes and common ActorItemCustody receipt rejects forever.
Forestry uses same equipment builder and transition, so repair in common admission
is in scope. Do NOT weaken observed receipt filter or force completion. Need common
endpoint policy validation BEFORE native/domain prepare/order creation, preserving
planner retarget. No source edit while current native17978 lives.

Final candidate21:28: implementation committed70b670821 (172files/5885new lines),
then read-only full-pack preflight declaration8631f679f; source clean. PACK a2015c8
r28datapack/README/synchronizedindex; preserve .f0v-baseline. No push. Final affected
103Frontier +864native unit checks+guardrails/build/package PASS1m6 (forestry-storage-
admission-final.log); production reserve stops256/resumes<192 test, committed suffix
not double counted. Common WorkEquipmentOrders now validates ActorItemCustody
endpoint policy at ORDER construction, before native/domain preparation; ore-only
warehouse rejected before creating any pending intent. Observed checks unchanged.
Native growth-final-candidate statusOK17actions at70b670821, full shutdownsaved21:26:16.
Native handoff-post-power statusOK:5->38logs, exact actors/tools/cargo survive4kCOLD,
source scopes0, graceful restart andHOTreturn. This evidence's core path unaffected
by later endpoint-policy check (those runs onlyTAKE equipment, notPLACE). It does
not prove native tool-return policy rejection; unit/source actualold-world trace do.
RELEASE clean detached8631f679f: build864nativeunitchecks/package PASS2m57 atsame
productioncode70b670821, then test-declaration-onlyref repack/verify PASS6s, no needless
metadata-only whole test rerun. LogRELEASE/build/r264-{package,final-package}.log.
Live oldserver stopped normally RCONstop, awaiting all-dimensions save beforefresh
deploy. Deployment exec64326 uses build/deploy-r264-fresh.py, targetworldfrontier-v3-
r264-forestry-20261025/r28/schema306; no claim deployed untilverifyreceipt. Need
complete deploy, full-pack read-only nativepreflight with actualframes, inspectfresh
runtimehealth/forest/ordinarymanufacture progress and updateorder/goalhonestly.

R264deployVERIFIED: finalrelease8631f679f, JARdc479f433f747392c475aaee935aa272f336dc496b996b0cf3fbf8d6c49b2e07d2456c47e9d5cff5a36cadb4102858a1ea89477bed385892d6bcf0ec5c60b1fe.
Worldfrontier-v3-r264-forestry-20261025 UUID359a966a-0b8d-491d-ae7d-8bbff71136dd,
PID95397 atverify, start1792945709. Full-pack read-only live nativepreflight
statusOK6actions+two reviewedforestframes; clientSTOPPED, nohumanM3claim. FreshCOLD
12000requestCOMPLETED at16177(target16134), acceleration0; summaryallconflicts0;
recentmean2.2ms/max14.9ms, firstHOTentry331ms peak retained(not80msguarantee).
Normal world noaxes yetat15.2k;Hearthvale coal18/rawiron0/ingots0. Do notclaimnormal
fullautonomousironprocurementmanufacturedaxes. Sharedforge make/repair tests pass.
Completionaudit found missing native end-to-end manufacture+deposit+cutoff evidence,
so finish ONE new supplied-inputs carrier: profileforestry-supplied seedsONLY home7
wood190(<resume192),6ingots,2coal,128bread, NOaxes/jobs/loss/progress. Ordinaryforge
must createaxes thenactualworkerslogdelta>=66, storedreserve>=256 andretirealljobs.
Nativework/genphysunchanged; diagnosticaddsactiveWorkers/exhaustedTrees only.
ForestryFixture.suppliedConfiguration +catalog/properties +isolatedscenario added
WIP(uncommitted), no productionfreeinputs. Newfocusedfixturevalidation exec5656
build/forestry-supplied-fixture-final.log running; firstpartialscriptcatalogedit
wasincomplete, fixedbeforeanynewnative; prior14srunselectednotest,NOTproof.
Nextfinishfocusedfixturecompile, run disposable-forestry-production-reserve frozen
native: prelude2400; await2workers, HOTactualnewtreedepletion;leave;COLD1000+graceful
restart; HOTreturn+awaitreserveSupplied=true/activeWorkers=0; actualdepotUIframe.
Need obeysourcefreeze, no source/tests editswhilenativeactive. Thenfixanyactualbug,
commitdiag/carrier, repackagecleanrelease(no duplicateunchangedtests), redeploy
samefreshR264worldifonlydiagnosticchange; goalremainsACTIVE untilwholeflowproved.
