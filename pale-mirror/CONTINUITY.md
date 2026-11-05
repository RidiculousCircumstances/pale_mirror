# Active continuity — 2026-11-05

## Assignment and completed delivery

Accepted Medieval Spruce settlement rollout implemented, committed and deployed:
remaining functional buildings, variable mixed housing/bed accounting, three
reproducible street schemes, flush paving and bounded developer planning tool.
Future expansion preserves occupied plot/building IDs and coordinates. Sleep,
individual home/bed assignment, live economic expansion and construction AI deferred.
Main alone; no subagents, goal or push requested. Standing scoped commit+verified
disposable-server deployment applies. Preserve human rd/unrelated services and WIP.

Plan: docs/frontier-v3-settlement-layout-plan-20261105.md. Implementation/ownership,
verification and delivery: docs/frontier-v3-settlement-layout-implementation-20261105.md.
Previous ledger archived at docs/archive/continuity-before-settlement-layout-20261105.md.
No active client, display or monitor; server left running. Final response pending.

## Repositories / identities

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror (Git root parent),
branch feat/baker-carry-orders-20260926, initial b5cc15846. Source commits dae577e04
(main162 paths),7e9f6e439,1a9b2612c,23e8b984c,c9f207194. Later scenario-only camera
corrections c7b39197e,ad97c5f04,f603f3391. IMPL clean finalf603f3391.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror, clean detachedc9f207194,
the exact deployed production ref. Later commits affect only native test scenarios;
recovery pilot runs this release against checked-in IMPLf603 scenario.
PACK /home/rd/proj/minecraft clean tracked888ed90d5; unrelated .f0v-baseline/ preserve.
Only ignored hosted artifacts/checksums were published; no tracked pack commit needed.
GOV parent Git repo has extensive unrelated WIP. Only own architecture field,
new plan/report/archive and ledger may be committed. settlement_layout_boundary is
staged through HEAD+exact-line blob; all other architecture edits remain unstaged.
Never stage whole AGENTS/architecture or unrelated historical documents.

Production Core SHA512:
b69b909dba00df102bc534c92706d7e8f3b4d931355cf9dcce2a322f85d5ac2b8083130730675ebe042895c939ca375e40043fcb39983fa015a11c227730ae04
Unchanged Visuals:
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601

## Responsibilities / implementation

SettlementLayout owns bounded style/revision, stable plots/buildings, parcel bounds,
streets and extension fronts; append-only extend rejects replacement. Schema339
persists exact manifest; recovery never reruns allocator. Strict shared validation /
properties/SVG compiler; no new construction writer. Planner reserves route departures,
uses real template envelopes/ports and one of3 hashed schemes;14..18 buildings at139.
Houses1/2/3/5 actual beds;25..43beds for21..35people. Bed-half destruction reduces
capacity. All12 Spruce functions/templates imported from pinned upstreama9b03edfbb,
hashes/provenance/collision certificates. Private ARR derivatives, no public push.

Functional ports use declared authored anchors; public service contacts are walkable
but noParking. Street graph feeds common ingress/rest geometry. Exterior fields/quarries
share land proposals and avoid private parcels; stone-brick roads are flush/cobble
foundations, repair items match. Existing navigation/custody/storage/execution retain
all gameplay authority. Developer building layout export/move/street/connect/validate/
overview/overlay/selected preview shares compiler, bounded atomic drafts only.
Network7 requires matching updated client. No live paste.

Integration defects also repaired: regional authored geometry references retained
blocks/columns/palette instead of repeatedly flattening world; exact memo/union avoids
huge treatment Set.copyOf (captured native stack). Hive obstacle private HashSet avoids
SetN probing; no new Hive behavior. Shipment strategies declare supportingFacility;
economic owner no longer substitutes. Production/migration retain same actor profile
at admission/continuation. Group cohesion excludes peer already at own exact terminal
station from spacing arbitration; only movement owner confirms arrival/cargo/lease.
Mining at equal urgency prefers directly available own-output deficit over prerequisite
access work for another source; commodity-independent. No live speed gain claimed.

## Verification receipts / limits

Skills read: foundry-audit, visual-audit, release-verification/matrix,
server-ops/operations-map, client-investigation, worldgen-performance; protocol read.
All12 native authoring assets PASS;44-file reimport exact;12 offline plan SVG/properties.
Core443 GameTests PASS at1a9;6 affected native service tests PASS at23e;4 extraction
native tests PASS on final production bytes. Domain324/Visuals79 unit tests pass.
Full Frontier verification uses completed XML class partitions, not repeated broad
runs: build/settlement-layout-review/integration-partition (IMPL), combined-receipt.json.
NeoForge920 tests PASS; all13 classes needing fixture/oracle correction subsequently
pass focused checks. Four tests of removed raised-apron/fixed-grid layout retired;
current layout7 tests cover identities/extension/terrain/bed loss/recovery negatives.
No timing tests added. Final release guardrails/focused last3 classes/build/package
PASS: RELEASE build/settlement-layout-release-complete.log. Final same-ref repack hash
matches deployment: settlement-layout-final-artifact-verification.log.
Unchanged legacy Visuals lanes are NOT green: missing test-only navigation template,
and separate natural-world survey0 sites. No active v3 defect proven by those failures.

Real full-pack client layout PASS: RELEASE build/settlement-layout-review/live-layout.json,
run2c87b88b-b188-44b6-b81c-80b77bbba33e. Four actual frames opened. First near-wall
house frame insufficient, corrected recovery street-eye camera. Graceful SAMEworld
restart+graphical recovery PASS: live-layout-recovery.json,
run190a8727-4e09-4be7-a681-69508aa2b3db. Three actual frames opened: coherent whole
village, several complete house facades/roofs, native depot menu before/after restart.
Terminal scene/inventory conflicts0. Original/recovered layout exports byte-identical,
SHA2562e9f6e580eb3a3bb5e4296309bf47c6698cad776a3b385ba348471b970c2be79.
Joined identity/frame/limits receipt RELEASE build/settlement-layout-review/delivery.json.
Private Xvfb automation is not human M3. Bounded layout/menu/restart check does not
claim full live economy or all worker progress. Initial camera trial failed from a
double-added body offset/gravity; scenario corrected, no timeout/arrival weakening.

## Current runtime / final steps

Live /home/rd/far-frontier-server, far-frontier-v3-live.service, wrapperPID2757086,
start22:41:04+05. Fresh world settlement-layout-schema339-seed139-20261105 seed139.
Old338 and new339 both stopped/saved normally at their respective transitions.
PACK preflight/install/fresh startup/restart verifier PASS, no quarantine; logs in
RELEASE build/settlement-layout-deploy-*.log and settlement-layout-restart-verify.log.
Human rd connected before authorized restart; connection interrupted with notice;
no account position/items edited. Preserve ordinary user session.
BuildJava21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtimeJava22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
RCON helper exists in IMPL build/forestry-native/query-runtime.mjs; never print secrets.

Final docs guardrails PASS. Scoped GOV plan/report/archive/ledger/map commit completes
documentation; all unrelated WIP preserved. Final response should state delivered
features, future-expansion limit and matching client update. No further broad tests,
restart/reset or automatic push. Last ordinary list: human rd online, pilot absent.
