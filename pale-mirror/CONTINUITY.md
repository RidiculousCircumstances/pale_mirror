# Continuity Ledger

## Assignment
Implement accepted docs/economic-planning-and-native-operation-rework-20261025.md.
Main alone; no subagents or push. Commit/deploy coherent verified slices and finish
C acceptance is complete for this scoped plan (limits below). User also reported new chest rejects pickaxes for about20s; fixed R247.
Existing goal was usageLimited; user explicitly continued. Mark complete only after
final scoped report/commit and current runtime check; no new goal inferred. Earlier detail:
docs/archive/continuity-20261025-r247-before-cleanup.md.

## Locations and delivery
IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror, branch
feat/baker-carry-orders-20260926, HEADb3d2ced2c, clean. Last commit records receipts and grounded native scenario.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror, clean detached4c3fad007.
PACK /home/rd/proj/minecraft HEADf631234, tracked clean; preserve .f0v-baseline/.
Governance has unrelated WIP; never stage-all.
Runtime /home/rd/far-frontier-server; transient far-frontier-v3-live.service.
Current R249 world frontier-v3-r249-clean-20261025, seed126/rulesr26.
Fresh deploy plus final restart verified PASS build/r249-graceful-verify.log,
PID110388 at verification; notBefore1792876783. Code identity remains4c3fad007;
ae315c82e/b3d2ced2c change only report/scenarios; no JAR republish needed. SHA512:
4507b1784e1a71778fb392234b907dbacc9f2e377de11a7891177b210d35b2b56571d2688ca187f5dd9c0580e2af8fa9aa5bf54a09eea1cc43f45e31fbb71afe.
Old frontier-v3-r241i-clean-20261024 retained unchanged for diagnosis. Schema303
cannot read it; do not restart that world with newcode or guess lost endpoint types.

## Implemented
A f1baa8330/R245: shared MinecraftOperation READY/WAIT admission and explicit
APPLIED/REFUSED_WITHOUT_EFFECT/UNKNOWN outcome; prepare geometry/geological
exclusions and durable fences before EACH beginAttempt. All active callers migrated.
Partial batch preserves later permits; unknown field outcomes reach owner/handoff
as CONFLICT, not perpetual WAIT. Historical matching preimage never permits replay.
First original refusal was not logged; reachable geometry veto proven, retrospective
first cause uncertain. Old uncertain field ops remain explicit local restrictions.
Receipt docs/native-operation-preparation-r245.md (corrected old SKY screenshot claim).

B 7b54d42ba/R246: durable SettlementEconomicPlan, addressed input generations,
GoodsParticipantProcess sole calculation/publication owner. Stations/mining/field/
market/protection/diagnostics read accepted decisions + exact local stock/commitments.
Cultivation hypothetical calculation moved into review. Stock/cash/capacity/need/
market/delivery input ports; ordinary movement/cropage/unchanged physical save excluded.
One coalesced REVIEW per participant recorded in same input transaction WAL; replay
never invokes solver. Shared production ceilings retain common coverage baseline.
No pricing/subsidy/balance policy change. docs/published-economic-plans-r246.md.

R247 09e18015f: real Sodium client missing adjacent28,0 held field28,-1 invisible.
Existing declared/provenance-exact container with independent pending effect may
return PRESENTABLE_PENDING; terrain visible, new HOT work/stock writes forbidden.
Missing/foreign/uncreated provider stays WAIT. All static/reload/dynamic paths wired.
Newchest observed--644_61_-2 logs CUSTODY_NOT_ACQUIRED:MISSING. Presentation selector
ignored absent lease when inventory matched. It now prioritizes incomplete custody;
first exact player click invokes existing declare-observe-acquire owner (max3 real
transitions, durable rules, stop on no progress/existinglease), then rereads successor.
No released/unknown bypass. docs/container-first-interaction-and-visibility-r247.md.

e3410faac/R248: read-only harvest oracle includes confirmed deliveredYield as semantic
progress of same job. Prior creative live job223 physically delivered4 thenclosed;
old oracle only counted crop/cursor and falsely rejected terminal. Movement/lease/time
alone still cannot pass. Native rerun pending because next unrelated persistence bug.

4c3fad007/R249: exact server quarantine923480, internal2iron_ingot shipment
internal/c6e469e9-ac3d-3765-bebb-44acf2a085f2 smelter-to-storage decoded FUEL.
ShipmentStateCodec wrote OUTPUT as2, same as FUEL. Fixed shared exhaustive INPUT1/
FUEL2/OUTPUT3; reject maintenance resource port. Existing actual dispatch test now
WAL+snapshot roundtrips BEFORE pickup, then normal delivery. Old test only recovered
afterpickup and missed corruption. Schema303 rejects301/302 before hydration: nominal
identity irrecoverably lost, no heuristic migration. Removed temporary301 migration
and unused generic validateRecovery hook. Evidence snapshotbuild/r248-station-shipment-failure.bin,
build/r249-native-failure-trace.txt; docs/station-output-port-recovery-r249.md.
Freshworld explicitly authorized by standing user grant, not a substitute for diagnosis.

## Verification retained
A preparation4/4 and field/storage/body50/50 PASS. Fullnative408:406PASS+2 excluded
combat/patrol failures; not claimed fixed. Initial adapter858PASS.
B fullfrontier1864/1864 PASS; nativeeconomy55/55PASS. Adapter856/858, both fixture
failures fixed (async persistence readiness / missing accepted plan); focused29adapter
+18publicationPASS including interval/restart. GuardrailsPASS. r246-integration.log,
r246-final-adapter-fixes.log. All36 station event hashes identical on same frozeninput;
paired sum medians18.069->3.683ms(~4.9x), thread allocations3,930,200->1,358,784B
(-65.4%): r245/r246-station-allocations.txt. Not totalTPS/global80ms proof.
R247 handoff +55nativeeconomyPASS: first unacquired playerclick, consecutive tool/
bread/coal ingress, pending visibility/noHOT, foreign provider WAIT.
Native retained-world fieldscenario PASS build/r247-field-hot.json: receipt0.100s,
compiledtarget4.100s, linkedframe4.924s; returnreceipt0.200s/compiled1.300s. Bothframes
inspected. Ready-native visit, not arbitrary worldgen/clientstartup/alltowns guarantee.
Bounded COLD/spectator peak79.23ms, ordinarydebt63->6->42, ready lag0; nativeavg2.8ms.
R24942 focusedfrontier + runtime recovery +55nativeeconomy andguardrailsPASS:
r249-station-port-tests.log, r249-recovery-native.log. Detachedbuild/packagePASS
r249-release.log (applicable test reuse, build -x test; not new full858gate).

## Final runtime evidence and remaining limitations
R249 COLD advance18000+6000 requests completed explicitly, receipts
build/r249-cold-{advance,maturity}-completed.json. No current acceleration/client.
Grounded creative scenario live-published-economy-harvest.json PASS,
build/r249-economy-ground.json, run2c34b659-ed27-44fb-97eb-78ba427478a5.
Same job/worker/intent observed2400 canonicalticks;12cells/48yield and physicalmotion,
PASS:IN_FLIGHT, not full-field terminal delivery. COLDtrip toOverworld10s/return3.200s,
compiled targetvisible, first_visibility27,-2 fullyready, field0mismatches/no pending
orforeignchange. Bothframes inspected. Earlier airbornecreativecamera invalid: player
fell from requested y70 to64, so visit neveracknowledged. Corrected groundedfixture;
not a productfix or accepted native result. No second active client.

First systemdstop had no fullnative save receipt: classify as abrupt, not graceful.
Recovery retained job1/52yield. Subsequent RCONstop explicitly saved all4dimensions;
restart samejob progressed13->14cells/52->56yield, economic acceptedplan available,
treasury unchanged. build/r249-before-graceful.txt, r249-graceful-stop.log,
r249-after-graceful.txt, r249-graceful-verify.log. Runtime recovery smoke, not new
native restartpromotion. Current server running; no newquarantine observed.

COLD nativepeak41.66ms beforevisit. StableHOT nativeTPS~20, short-windowmean3–4ms/
peak~9ms, readyactionlag0, ordinarydebt returned0 (5 atlastreturnedscene).
First generation/unpreparedvisit caused1,776.8ms peak and~19s ingress in invalidcamera
run. This is a real remaining initial-area performance issue requiring separate
attribution, not claimedfixed or masked by ready-native <5s receipts. Routepreparation
still had outstandingrequests; no emptyqueue/general throughput or balanced-economy
claim. No new goal for this remaining breadth without explicituserrequest.

Final exact retired-job native re-entry PASS: live-published-harvest-terminal.json,
build/r249-harvest-terminal.json, run c19af1d5-c1aa-4a70-afcb-0ae2d064150d. Intent
CONFIRMED with observation:field-deferred-site-harvest-7-wheat-field-1; both
physicalReceiptResolved/Confirmed + completeForColdHotReceipt true. Field0mismatches,
frame inspected. Same job terminal physically reconciled, not merely absent. Not
all256cells/repeatedgrowthcycle. Postfinalrestart bounded nativepeak72.93ms,
recentmax21.20ms, ordinarydebt1/readylag0; old1,776.8ms finding retained.

Final source report docs/economic-planning-native-recovery-results-r249.md,
commits ae315c82e/b3d2ced2c. CoreA/B transition and requiredC boundedchecks done. Canonical accepted
plan updated with limitations. PACK trackedclean/.f0v-baselinepreserved; governance
mixednormativeWIP preserved, don'tstageall. No push. Accepted plan/ledger are the only governance files selected for this delivery.
Final report must state the separate first-generation performance limitation.

## Tools
JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
RCON python3 /tmp/pm-read-rcon.py COMMAND... (protectsecret); diagnostics:
pale_mirror v3 inspect site site:7-wheat-field. HTTP8093/api/performance (cache~10s),
/api/view?from=0. Copy rotating snapshot beforeoffline decode, never inspect liveWAL.
Native existing wrapper scripts/with-private-xvfb.sh node tools/frontier-v3-test-pilot/
src/run-scenario.mjs SCENARIO build/OUTPUT.json, with JAVA_HOME=JDK21,
FRONTIER_V3_PILOT_PORT=25565, FRONTIER_V3_NATIVE_PROCESS_ROOT=IMPL/build/r246-native,
FRONTIER_V3_PILOT_PROFILE=pack, FRONTIER_V3_PILOT_PACK_DIRECTORY=IMPL/
pale-mirror-neoforge/build/runs/railway-client. Wrapper owns isolatedXvfb/cleanup.
Verify no old client before launch. No unrelated services killed. No sandbox_permissions.
Transientunit disappears afterstop: systemd-run --user --unit=far-frontier-v3-live
--collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Fresh verify required.
Detachedbuild needs -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/
config/graybox-disabled-mods.txt. Skills already read: release/matrix, serverops/map,
worldgen-performance, client-investigation, execution-semantics, engine-source.
