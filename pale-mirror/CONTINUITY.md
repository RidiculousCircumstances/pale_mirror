# Active continuity — 2026-11-06

## Assignment and authority

Main alone; no subagents, new goal or push. Latest user authorized fixing the
traced latency and one-item shipment causes. Source is committed and deployed; bounded post-deploy causal observation is
complete. Report the outcome and limits; do not start another feature unrequested.
Standing delivery requires scoped commit and verified deployment. Live worlds
are disposable, but this task preserves the current world. Canonical AGENTS.md
and docs/engineering-agent-protocol.md remain authority. GOV has extensive
unrelated WIP; never stage whole shared files.

## Working set and identities

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror (Git root parent),
branch feat/baker-carry-orders-20260926, clean e30b168f5869102570883b7a9d45903b9e9735b4.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror, clean detached same ref.
PACK /home/rd/proj/minecraft, HEAD98a6701, tracked clean; unrelated untracked
.f0v-baseline/ retained. Hosted core updated through pack publisher;170indexed
files verified. GOV /home/rd/proj/pm-governance retains unrelated changes.
Task documentation/ledger and the isolated architecture addition are committed
separately from the unrelated WIP, which remains unstaged.

LIVE /home/rd/far-frontier-server,far-frontier-v3-live.service; wrapper3986882,
native JVM3986908,invocation aef9ea83d70242a8a9d520fe7f4100d0,notBefore1793975323.
Port25565/RCON25575. Same world buildings-packet-dining-seed139-20261106,seed139,
frontier-v3-textiles-terrain-r32,schema342/ledger7/Network7. Native3316566 stopped
through RCON and saved all dimensions before install; no reset. New startup and
installed hash passed scripts/frontier-v3-deploy-verify.sh.
Core SHA512:
a7f19a99bdcd1fecc96735b483420fe6543f94906addbe39da784c904d25e7e332f613e67df6d12f9e4f1e40d60aee625be240489e419c773d7bf55e0bcade22
Visuals unchanged SHA512:
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601
Java21build /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2; Java22live
/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java. Read-only RCON
helper IMPL build/forestry-native/query-runtime.mjs; never print property secrets.
No task clients/displays are running. User rd was online earlier;0players before
this restart. Do not stop a human client. Normal20TPS,no fast-forward.

## Current fix and evidence

Plan docs/frontier-v3-local-access-and-dispatch-20261106.md and canonical
architecture local_access_dispatch_boundary. e30b168f5:
- Common KnownLocalAccess rejects out-of-envelope goals before geometry reads;
 optional positive shortcut has128edge checks, then ordinary cooperative routing.
 A shortcut miss is never global NO_PATH. No new geometry or movement authority.
- Internal asset manifests reject insufficient unclaimed volume and combined
 receiving capacity before pickup/drop connectivity. Exact admission remains.
- ReplenishmentLot.economic selects an efficient useful load independently of
 cash. GoodsDispatchAdvice uses it; matching still conserves real goods/money.
 Explicit uncovered deadlines still allow tiny recovery. No global64minimum.
- StorageConsolidationSupply issues ordinary InternalHaulRequest via existing
 ResourceHaulingPolicy for partial same-owner warehouse stacks. Filters/priority,
 claims/capacity and indexed incoming shipments remain owners; no kitchen/buffer
 repurposing, synthetic stock, direct multi-container pickup or reverse pending haul.
 ShipmentState.receiving exposes its existing read-only receiver index publicly.
 No schema/packet/ruleset shape changes.

IMPL build/delay-lot-analysis-20261106:
fix-closure.log PASS78tests including actual meals, supply, commercial mixed trips
and same-owner transfer/recovery;closure-counts.json retains suite counts.
Final updated pre-navigation-capacity/index code:fix-final.log PASS guardrails,
19selected tests,jar/verifyPackagedJar. nonflat-fallback.log PASS12tests including existing layered
bridge/tunnel,unknown connections and fair yielded full routing.
RELEASE build/access-dispatch-release-e30b168f5.log PASS guardrails/package.
Copied-image probe:4239transition/3576surface reads ->0 for a173block goal outside
radius16. Westhaven snapshot190072 buys109wheat with2.786337cash at1.97price:
match1 remains financially possible, plannedLot1/ECONOMIC_LOT ->256/ACCUMULATING.
Dawnbridge4cobble demand versus source1 retains SUPPLY_DEADLINE. These are same-image
work/decision proofs, not measured whole-server speedup or historical trip replay.

Original focused selection exposed5unchanged ResidentMealKnownNavigationTest
old-depot-food failures; all5reproduced with exact HEAD KnownLocalAccess shadow
class (meal-baseline-comparison2.log/meal-baseline.xml). No tests deleted/disabled;
no whole-suite-green claim. New fixture mistakes (width/depth versus maxima and
chest below ground) were corrected. Prior unrelated2ServiceApproachProgress and
portable home-eating premise remain separately recorded in archive.

Post-deploy summary242951:340residents,12towns,0required/inventory/scene conflicts.
Performance242956:pendingOrdinaryTicks0,READY0/CHECKING0,ready/deadline lag0;
recent64hostmean3.67ms/max15.04ms in COLD, not HOT/native-total performance.
45s pinned capture session50140 completed and profiler stopped:
build/delay-lot-analysis-20261106/deployed-causal/report and deployed-trace-summary.json.
903ticks delivered/899host arrivals,debt4->0,20.10TPS catch-up rate. Meal plan139calls,
max24.856ms/0.318exclusive owner wall seconds; no KnownLocalAccess CPU samples.
Hauling exact wait0.537s/45s in this changed world. No controlled whole-world ratio:
source control prevents the demonstrated redundant work, live cuts differ in stock.
Trace quality explicitly incomplete(1.52%unexplained/capture-edge references),no loss.
Only >=80ms recorded ready span:guard100.175ms wall but0.328ms CPU at JFR-start edge;
no overlapping monitor/park/GC event, no expensive guard algorithm established.
Startup earlier had439.7ms native tick/367ms schedule allocation and308ms hive scout;
those remain outside this scoped fix (no hive expansion/repair). No claim that all
future peaks are below80ms. Final game tick query normal20TPS; no task clients.
Economic collector healthy at242946:11hungry/340residents,30missions,all12towns;
existing small accepted missions were not cancelled or rewritten to invent improvement.

## Diagnosis and remaining limits

Original60s causal report in build/server-economy-review-20261106/causal-report:
Northwatch hauling8.133s exact wait, six1.22..1.35CPU-s reviews; sampled stacks lead
to ActorStorageApproach/KnownLocalAccess/canTraverse. Meal planning2.511s/max105ms
in the same local BFS. Internal manifests checked volume only after route work.
Fresh pre-fix30s191676..192276:20.02TPS,no sustained debt; hauling only0.174s in
changed stock state, meal max74.5ms. Both reports explicitly incomplete(1.8%/1.4%
unexplained); no JFR loss. Fresh first flush references3pre-capture appends.
Original1.327s native entry peak lies outside captures and is not attributed/fixed
by those stacks. Current fixes do not prove every future peak below80ms.

Before fix:25paid receipts/64items over one game-day,70missions,most moving;
prolonged Northwatch preparation eventually progressed. Cash total1200 conserved,
Clearwater ~62% and Southgate low liquidity; unequal money alone is no defect.
Iron sell/buy price divergence and declining food in several towns remain distinct
unclassified economic risks; do not claim balance repaired by dispatch changes.

Previous door/porch/dining fix a609 and exact native receipts remain valid for
unchanged dependencies. Details and all current investigation history preserved
in docs/archive/continuity-20261106-before-access-dispatch-delivery.md and older
archive referenced there. No full human M3/global-bugs-closed claim.
