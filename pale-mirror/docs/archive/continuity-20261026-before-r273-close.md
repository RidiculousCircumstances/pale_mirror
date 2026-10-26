# Active continuity — 2026-10-26, fixing recovery shipment sizing

## CURRENT OVERRIDE — user authorized fix of one-coal trips
Implementation COMMITTED3868d12f6 (prior58e0f1482); source clean. Final release build underway.
Release clean detached same; verified package PASS7sec. Same-world deployment running.
No subagents/push/goal. Commit+deploy coherent fix; retain R272 and existing
contracts, no manual repricing. The delivered soil feature below remains live.
Source traced startup coal target2 vs purchase1: direct equipment fuel and
upstream smelting fuel reused same selected amount. SupplyPlanner new explicit
owner direct-use input separates consumption from inventory buffer; ephemeral
SupplyResources sums upstream with direct uses and does not double-book stock.
EconomicPlanner supplies its already-selected direct requirements; actual reserve
lead replaces normative commodity lead in this advice only. TripDispatchPolicy
now nets already-covered production recovery against stock/dated arrivals at
route horizon, rather than treating any finite input deadline as uncovered.
No hard minimum, grant, waived freight, price cap or opportunity-cost cash debit.
Focused checks running build/coal-trip-diagnosis-r272/check.log; new regression
RecoveryReplenishmentTest. Need inspect failures/callers, counterfactual sealed
snapshots before claiming improved real-world quantities, then commit/build/
deploy and bounded observation. Do not call this delivered yet.


## Assignment and authority
User accepted the generic data-defined soil-preparation mechanism and instructed
implementation. Scoped work delivered; no active goal or test watch remains.
Main alone; no Terra/subagents/push. Standing coherent commit/deploy applies.
Worlds disposable, no further reset approval needed within scoped work. Preserve
actual diagnosis where useful; never clear conflicts or grant food/cash to claim
recovery. Source-first, meaningful tests; clients20FPS and stopped between runs.
Canonical AGENTS/execution protocol applies. Already-read engine-source,
release-verification, server-ops/client references retained. Reuse unchanged
reliable evidence instead of running unrelated campaigns.
Previous economy diagnosis/authority and older deliveries archived in
 docs/archive/continuity-20261026-before-r271-soil-preparation.md.

## Exact repositories and live identity
IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror (Git root parent),
branch feat/baker-carry-orders-20260926 CLEAN HEAD8c15d813a13a38975bc37f9f4112a2c9fe77a06d.
Generic mechanism5ec60334c; connected native initialization fix8c15d813a.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached same.
Pack /home/rd/proj/minecraft HEADcfdc160 tracked CLEAN; unrelated untracked
.f0v-baseline/ retained. Histories separate. No push. Governance has extensive
unrelated WIP; stage only this task's ledger/archive, never stage-all.
Source report docs/soil-preparation-r271.md owns mechanism/findings.

Live /home/rd/far-frontier-server, far-frontier-v3-live.service ACTIVE,
PID3285340, startup2026-10-26 11:32:18+05. Fresh disposable world
frontier-v3-r272-soil-preparation-20261026, UUID533f3469-75a8-422d-930e-dd5f26bc3798,
seed126/profilefrontier-v3-forestry-graybox-r28, canonical schema307,
field witness11/common operation journal6. Definition fingerprint
 a30bc402d50e55bf803066fb5913e138b828b4a5c1fa5aa3e4b205d375432fa6.
Source tree22c0180f07de091559d92289719cdc92ddd986a4 (not a commit hash).
ArtifactSHA512 c43b4dc0fc656361e163c14a631a85bfc3a5ff844548925aa1c327f3ae6145b449150eb47c3e84a77d6bbb81b42deb5b8c51cfc8d716958525c298acb5994b9a.
Normal stop/save/publish/install/preflight/start/exact-artifact verify PASS.
Raw receipts IMPL/build/soil-preparation-r271/r272-deploy*.log and
r272-deployment.log. Clean release package PASS9sec. Old R271 failed world
retained for exact incident; R267 retained for prior economy diagnosis. Neither
was manually repaired/migrated. Source/pack verification and dirty states separate.

Analytics same far-frontier-economy.service, LAN192.168.0.103:8093 and
VPN100.100.78.29:8093 (NOT127.0.0.1); same DB/collector, no analytics code change.
JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.

## Implemented ownership
Immutable SoilPreparationCatalog reads bounded soil-preparation.properties once,
pins canonical definitions into rules hash, validates names/tags/blocks, complete
acyclic paths and tool/effort definitions. Core Soil identities are registered
values, not a closed enum/switch list. Authoritative cells reject foreign values.
An additional soil using an existing action needs data only; new effect semantics
need a real provider, not a silent block-only adapter.
Initial paths: grass->farmland, dirt->farmland, coarse_dirt->dirt->farmland.
Every step is distinct labour and tool wear; sowing separate. ResourceFieldCycle
owns state/generation; FieldEquipment/WorkProgress/ToolUse retain work/tool owners;
FieldWorkForecast sums all steps and memoises repeated identical assessments.
Native FrontierV3SoilPreparation read-only preview uses actual item ability and
Minecraft getToolModifiedState(...,true), exact declared successor. Common
FrontierV3FieldWorkOperation owns durable mutation/tool effect/witness. Changed
predecessor, wrong tool or blocked crop space rejects. Foreign observations,
projection and codecs share definitions. Schema307/witness11 reject old grammar;
no old-world compatibility. No irrigation/seeds/free output/economic rebalance.

FieldDevelopmentLand.Survey binds exact immutable world/home/source fields and
reachable suitable cells. Subset cannot add unsurveyed land; stale/foreign evidence
rejects. FieldLandAdmission extends a safe idle work area over soil islands,
or creates one bounded area when existing fields busy. No field per soil island.
Limits4fields/1024cells per town,64newcells/turn,64radius remain. Active jobs,
receipts/pending effects/player edits retain their geometry fences. Pure probe
previously stopped112cells; new regression reaches256 in one work area.

## Connected live defect and repair
First R271 actual full-pack HOT/COLD/HOT FAILED (requiredConflict1); manifest
live-circulation.json/runa0199e75-7571-4985-86c2-23dadd3fc494. Preserve as failure.
At869 Hearthvale appended33coarse cells to ORIGINAL site:6-wheat-field: area97,
64growing+33absent, layout2. It never disappeared/never created separate
site:cultivation-settlement-6-1; earlier erroneous description corrected to user.
Initial writer at961 treated unchanged admitted coarse ground as AMBIGUOUS;
writer and ledger maintained inconsistent hardcoded harmless-target lists.
Incident resource-site:6-wheat-field firstRevision2883, initialization0/194writes.
Old diagnostic also falsely reported batch start instead of actual failed cell.

Native regression reproduced AMBIGUOUS before fix. InitialPlan Review now
supplies one neutral-predecessor permission to both writer and single/batch
ledger admission. Unchanged admitted ground is harmless; target-looking farmland
or crop without predecessor proof remains forbidden. Durability/cross-owner
fences retained. Refusal carries exact Review; position/index/target/observed
reported correctly. Changed stone blocks entire unprepared batch before any write.

## Evidence and practical limits
Original final-check.log: guardrails +212affected Frontier tests/35classes +
JAR/package PASS51sec. Config-only added soil and effort/wear, invalid definitions,
multi-step cycle/codec, rocky admission, repeated patches and stale surveys.
Native initial field-turn slice22required PASS; connected final slice23required
PASS1m2s (native-initialization-final.log), including actual native hoe agreement,
wrong tool/block/obstruction, intermediate witness recovery/old-format rejection,
new first-display/substitution/no-phantom-tillage case. Original red failure
retained. Intermediate fixed-run failure was a fixture immediate shared-budget
assumption; it now waits explicit RETRY, no timing assertion/production relaxation.
Final35native unit checks across5classes PASS13sec (ledger/claim/executor/witness/
budget consumers), final guardrails PASS10sec. Only doc/fixture whitespace after
native pass; no semantic candidate change requiring another native repetition.

R272 actual full-pack20FPS pilot HOT30s -> COLD3s -> HOT30s PASS13actions,
run a460c813-371c-4808-9f0c-48502a2c9960, r272-live-soil-check.json. Three frames;
final frame reviewed: original growing field visible. First physical sample1141:
ACTIVE OWNERSHIP/layout2, mismatchedCells0, no pending world/foreign changes.
Summaries1661/2401 all conflict counts0. This is actual native flow, not Java calls.

Then single relative5000tick request2867->target7867 COMPLETED at7893,
remaining0/failureNone; no other FF queued. Poller's90sec watch expired near
completion (remaining64), later exact terminal receipt verified rather than
requeueing. r272-cold-performance-final.json retains it. Snapshot800:64farmland+
33coarse;6202:72farmland/growing+25coarse;8803:78farmland/growing+19coarse.
Thus14actual newly prepared/seeded cells, not just land admission. No claim that
all33 were already done. Sealed copies/probe outputs in r272/ and development logs;
never decode rotating snapshots in place or edit native saves.

Return-after-COLD actual full-pack pilot PASS6actions,
run a82db8af-68aa-487b-99c2-3bea21e9d50f, r272-live-cold-result.json.
Native field9622 ACTIVE/currentlayout2, mismatchedCells0, no pending world/foreign
change. Real frame prepared-land-followup reviewed: new separated cultivated
patches visibly planted near buildings. Final9742 summary conflict/index0.
Actual final native summary10201 green: required/scene/inventory/custody0,
preparingReplicaScopes0/incidentIndex0. Player observation10200 empty. Both test
clients/observers/private servers STOPPED; MC/analytics ACTIVE. Expected client
exit ConnectionReset is not a new game failure; no native unresolved-operation,
initialization rejection or quarantine in R272. No universal<5sec/80ms, full
harvest/bakery/caravan-cycle or human/M3 acceptance claim. Fresh first visit
~10sec including readiness/settle remains measured, not called optimized here.

This delivers generic soil and safe dynamic-field admission/materialization.
Long-run economic equilibrium/recovery NOT established. Prior R267 crisis:
mining towns wanted larger fields but hardcoded dirt/grass/farmland excluded
2700+ PUBLIC coarse cells each. R270 independent restart funding delivered384946347,
but old provisioning/cash/production losses do not get refunded. Do not promise
that new soil alone guarantees enough food or balance.

## Tools / future work
RCON sequential: node build/forestry-native/query-runtime.mjs
/home/rd/far-frontier-server/server.properties 'pale_mirror v3 inspect summary'
(other inspect site/field_physical/process/actor/players). Never print credentials.
Snapshot probe needs Frontier build/classes/java/main AND build/resources/main
because immutable definitions are resources. Current ignored deploy-r272.py expects
OLD R271 -> absentR272 and CANNOT rerun unchanged; discover exact currentworld,
normal stop/checkAllDimensionsSaved, root install/preflight/systemd-run/exactverify.
No next work automatically scheduled; user can test current deployed generic soil.

## R272 one-coal-trip question — read-only diagnosis
Current user asks why coal1 trips exist. No code/game/pack edits, test client,
FF/deploy/reset. Same R272. API at15684: two real qty1 contracts, Hearthvale6 ->
Clearwater7 BUYER_COLLECTION and Stonefield2 -> Dawnbridge3 SELLER_DELIVERY.
Original retained orders BOTHqty1, not a UI count of stacks. Clearwater now
wants14more, but copied snapshots800/6202 prove its original demandpurchase1,
urgentPurchase1/deadline0; Dawnbridge original demand likewise1. Snapshot8803
already contains committed Clearwater1 contract. Do not claim current14wanted
at admission or current supplier18stock existed then. Original supplier orders
alsoqty1. Funding not absent: at800 buyers had100coins, coal allowance~53/54;
at6202 Clearwater allowance4coins/coalbudget4. Contracts immutable after admission.
Exact current17603 contract goods2coins each; Stonefield->Dawnbridge freight
14.990096, total16.990096. Clearwater self-collection has0freight cash invoice,
but actual mission loads2bread,1carrier; resource/labour expense still exists.
TripDispatchPolicy.assess lines20-26 explicitly admits any positive uncovered
finite-deadline cargo once next review would be too late, BEFORE economic-lot
check. Thus startup minimal production-input/fuel need becomes urgent qty1 trip;
this does not prove the trip is economically useless (coal may unlock production)
or that qty14alternative existed then. Persistent micro-shipment optimality not
proved. No hard-minimum suggestion: it can deadlock restart. Need compare useful
recovery volume/horizon and complete trip expense while preserving feasible small
emergencies, if user requests policy change.
Raw current-coal-{trips.json,buyer7.log,mission.log} in build/soil-preparation-r271;
sealed read-only snapshot/probe and historical800/6202/8803 outputs in
build/coal-trip-diagnosis-r272. Probe uses canonical codecs/resources, no saves
edited. Current supplier bulk stock13/18 is reservable but developed later.
R273 candidate details: original124affected checks had123PASS and one stale
partial-unload assertion; reproduced same failure on clean deployed8c baseline
(partial-unload-baseline.log). Test corrected to assert retained declaration,
readyToEnter=false and no actual service admission, then recovery/resumption.
No production service-access change. New direct-use earmarking also refuses
forecast double booking of current stock, input report remains upstream-only.
Final80focused tests PASS29sec; final candidate after extra input validation
76tests/11classes PASS35sec +guardrails/package. XML copied candidate-test-results/.
Scope originalGoods/matching/funding cases reused where unchanged, no native
physical-effect change or repeated whole-engine campaign. Read-only SAME tick800
recomputation now Dawnbridge/Clearwater purchase2 (old1), target2. Counterfactual
receipt early-sizing-fixed.log; do not label this a live delivery.
Before deployment currentworld54486 already has coal contracts33,9,3,1 on OLD
build; must not attribute every later large batch to this fix. Retained snapshots,
APIbefore-view/performance/players in build/coal-trip-diagnosis-r272/. No new
world/schema/grants/cash-price change. Source report docs/recovery-shipment-sizing-r273.md.
NEXT finish deployment verify, inspect actual accepted plan/no conflicts, bounded
COLD advance if useful and full-pack graphical preflight; stop client. Existing
qty1 contracts remain valid. Do not promise no small emergency trips ever.

## R273 late finite-need gap — fixed, final deployment pending
The first58e0f1482 same-world5000advance COMPLETED at62472 (target62359), no
failure. New contracts after its post-start baseline: Ironmeadow8->Sunreach12
coal1 and Westhaven11->Mossbrook10 coal10. Do NOT label this complete success.
Sunreach after acceptance had coal target1/incoming1, deadline169315, no actual
need for an early dedicated journey. Its capped lot1 had passed ECONOMIC_LOT.
Final3868d12f6 introduces explicit FINITE/RECURRING goods continuity from accepted
owner needs/dependency graph and selected own production. Dated finite needs
wait for delivery/review window unless sharing a justified recurring manifest;
due/late partial recovery remains. No hard64minimum, new deadline or forcedsale.
Also corrected net old urgency: do not add current arriving stock to an accepted
net shortfall (would recreate urgency after each receipt). New held-stock10
regression covers this. All continuity construction explicit, no default inference.
61tests/8classes including actual matching/consolidation/price callers and new
finite/mixed/deadline/stale-stock cases PASS1m58s; guardrails/package pass. Docs
updated with actual failed first observation and limits. XML finite-test-results/.
Read-only finite-dispatch probe at old54403 instead saw coal91/RECURRING/lot54;
that is NOT evidence for later Sunreach1 finite admission and was NOT relabelled.
Actual later native observation is new-one-coal-buyer12.log at62689; isolated
pure regression uses its actual need/deadline facts. Existing old missions retained.
NEXT final3868 clean package, same-world deploy (archive first deploy receipts
before reuse), bounded500tick control advance +full-pack native preflight;
inspect exact final state, stop client, commit final ledger/scoped contract rule.
