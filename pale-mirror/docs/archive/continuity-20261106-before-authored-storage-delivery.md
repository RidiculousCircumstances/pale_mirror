# Active continuity — 2026-11-06

## Assignment and authority

Main alone; no subagents, new goal or push. User accepted implementing authored
storage reuse and removing added settlement containers, plus inactive route-demo
chest/board. Active plan docs/frontier-v3-authored-storage-reuse-20261106.md.
Work is in IMPL, now clean33c101333. Authored catalogue/native rack integration
implemented; clean RELEASE build underway. No deployment of this task yet.
Fresh world is authorized for the changed bootstrap/schema; old runtime remains
untouched until a verified candidate is ready.
Standing delivery requires scoped commit and verified deployment. Live worlds
are disposable, but this task preserves the current world. Canonical AGENTS.md
and docs/engineering-agent-protocol.md remain authority. GOV has extensive
unrelated WIP; never stage whole shared files.

## Working set and identities

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror (Git root parent),
branch feat/baker-carry-orders-20260926, clean33c101333 (storage reuse).
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror, detached33c101333.
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


## Storage placement audit — latest user request

IMPL build/storage-placement-audit-20261106/summary.json and placement.txt from
copied snapshot257783. Registered additional fixed stores:12depot,12pantry barrels,
12smeltery chests,24remote-field buffers,15mine buffers,1route-maintenance chest;
61existing chest/barrel blocks already adopted. Production72inventories are machines,
not extra chests. Northwatch authored potential30chests+32barrels+43MineColonies racks;
some decorative/high cells may lack safe access, counts are not usable-capacity proof.
Screenshot chest is container:frontier-route-maintenance at(-1566,64,-1398),empty,
route maintenance/construction0. Network board is anchored beside it; CARAVAN EN ROUTE
currently means any nonterminal shipment (including internal), not chest purpose.
Depot template has22real racks/0ordinary chest-barrel blocks; kitchen6barrels+1rack;
smeltery1chest. Bootstrap still adds separate depot/pantry/metals containers at
non-authored storage cells. Generic adoption recognizes only native chest/barrel
and runs only in naturally loaded chunks, so existing authored storage is not a
complete COLD genesis catalog. Verified installed MineColonies1.1.1411 with javap:
TileEntityRack ->AbstractTileEntityRack ->BlockEntity, exposes IItemHandlerModifiable;
it is not ChestBlockEntity/BarrelBlockEntity. ModLens compile snapshot lacks this
optional dependency; exact installed binary used instead. No source/asset mutation.
Recommended next scope:authored storage catalog and zone filters/priority/common
access; native inventory capability adapter for racks without replacing models;
use existing kitchen/metals/warehouse storage, remove extra settlement chest
creation and inactive route-demo chest; retain remote buffers where genuinely needed.

## Authored storage implementation checkpoint

33c101333: schema343 fresh-world transition. AuthoredStoragePlan admits actual
provider blocks within marked STORAGEzones, connected through extracted shared
BuildingGeometryValidation geometry and ReachableSurfaceArea.324authored inventories
for12towns(warehouse racks,kitchen barrels,smeltery chests);449total including
remote buffers,machines,mobile,hive. Untagged territorial containers retain ordinary
natural-chunk adoption, now including racks. Initial logical depot alias binds
a real rack/ordinaryRESOURCE_STORAGE; no added authored depot/pantry/metals block.
Road-demo maintenance container/board omitted for authored builds. No building
block/model simplification. Kitchen/metals markup and reproducible import spec updated.
Native optional rack/combined-handler mixins expose exact real inventory/members.
Common click,drop,block-removal,persistence protocol reused; weak identity registry
does not retain unloaded providers or infer slot ownership. Rack fill appearance
is operating state, single/double structural composition still differs.
DepotServiceBoundaries now enumerates actual typed depot records, avoiding duplicate
ordinary-storage declaration for a logical genesis alias.
IMPL build/authored-storage-reuse-20261106/release-closure.log:PASS guardrails,29selected
tests,6nativeGameTests including actual installed MineColonies rack/menu and native
CombinedInvWrapper identity/admission rejection,package verification. One old
ResourceStorageAdoptionTest replacement-materialization premise still fails at
alternative placement: unchanged e30b168f5 reproduces NoSuchElementException line109
in RELEASE build/storage-reuse-baseline.log/XML. Test not disabled/deleted; no
whole-suite-green claim. Native optional provider mechanics are verified; real
packet put/take/break and deployed COLD/HOT/recovery remain next.

Native rollout checkpoint: deployed33c101333 to NEW authored-storage-seed139-20261106
(seed139,r32,schema343),previous world preserved. First packet check reached HOT
but rack stayed ACTIVE/PREPARING;client fixture timed out at2. Found common custody
priority offering unfinished fixed UNMATERIALIZED/PREPARED/ADOPTING births, which
have no observation action and can starve an active neighbour.28104176a excludes
those fixed lifecycle phases from discovery/presentation;mobile births unchanged.
7native socket/provider tests and guardrails/package PASS(discovery-closure.log).
Clean detached RELEASE28104176a built/published/installed;graceful native save passed.
Restart invocation a8f1d78ac69d45edab82502f6f00a5e0,in same NEWworld to test retained
PREPARING recovery;currentnotBefore file build/authored-storage-reuse-20261106/not-before.
Core SHA512 e7bd94ded7b01e3f0ed882eeb0d6cc82b10c23e018cefeb23785cf7a93056282675ec6ade70b3d50c09ac8ae994dd3a7629f1f5271fdc44b3aee7c40f9f28b8e.
IMPL clean28104176a(source test commits cd6cb59e5,c4ef84ee9 included).
Packet scenario checkedin tools/frontier-v3-test-pilot/scenarios/live-authored-storage-menu.json;
first invalid decimal fixture corrected to block offsets. Server-only pack source
lacked EMF/ETF and3humanresource archives:method failure before any game actions.
Prepared ignored native-pack staging with server mods/config plus checksum-pinned
EMF3.3.12/ETF7.2.6 and3verified resource archives from existing railway-client cache.
Task owns Xvfb:1041 PID99838(execsession98014),no human display;cap20FPS bypilot.
No native client remains running after failed attempt. Next:poststart verify then
rerun packet scenario against28104176a,inspect rack custody/chunks/menus. Do not
claim discovery fix solves runtime stall until observed;no full M3 claim.

Second native attempt at28104176a confirmed original rack birth recovered to
OBSERVED_CURRENT/RELEASED, but target chunk still withheld by another rack awaiting
COLD stock catch-up while global simulation calculation was stalled. Read-only
JDK21 jcmd attached exact native152887:PMV3-scheduled-preparation runnable consumed
187.96CPU-s/193.88elapsed in ServiceAreaDestinations/selectClearance ->
ServiceClearanceRoutes.allows/publicDestination. Not a main-thread monitor deadlock.
Stopped normally with0players;all dimensions saved,worldsame retained.
Copied snapshot19730,tick15027 in IMPL build/authored-storage-reuse-20261106.
Offline same-image first eligible actor4-13:old281 3.324s/no destination;newWIP
1.257s/known6block exit. This later image is not the exact sampled earlier job.
FixWIP:unused neighbouring service envelopes permit ordinary transit;the actual
incumbent point is supplied explicitly by service caller,currently demanded points
still constrain crossings.Public continuation is validated by public geometry;
private departure stays explicit.No resource permit granted;no hard blocks ignored.
Memoize read-only public-contact/busy facts per single plan;reject nonpublic parking
before instantiating service geometry.Removed eager whole-point-list selector;
defender return reuses same indexed point-aware API without activating new feature.
Focused source/incumbent tests passed;final guardrails+7native+selected checks running
in clearance-complete.log before scoped commit/deployment and packet retry.
Task Xvfb1041 still ours;no task native client now running.
