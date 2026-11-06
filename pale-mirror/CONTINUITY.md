# Active continuity — 2026-11-06

## Assignment and authority

Main alone; no subagents, new goal or push. User authorized completing the connected
repair of door flicker/paired leaves, missing original porches, hunger with available
bread and stationary MOVING reports. Code was committed and deployed. Native
verification is complete for the bounded cases below; no human M3 or whole-repository
green claim. Wait for further user steering; do not start a new feature unrequested.

Current canonical plans:
- docs/frontier-v3-structural-integrity-and-repair-20261106.md
- docs/frontier-v3-placement-and-northwatch-food-20261106.md
Historical diagnosis/receipts preserved in docs/archive/continuity-20261106-doors-meals-before-final-report.md.
Canonical AGENTS/protocol remain authority; retain source/pack/GOV histories and WIP.

## Repositories and delivery

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror (Git root parent),
branch feat/baker-carry-orders-20260926, clean HEAD
a609bb030b09c84ab029358c87487ec12b7a7e32. Connected fixes include4b07a61c8,
prior d2d0699f9 and9aff7b582. RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror,
clean detached at the exact same a609 commit; only that verified JAR is installed.
PACK /home/rd/proj/minecraft HEAD98a6701: tracked clean,170indexed files valid,
unrelated untracked .f0v-baseline/ retained. GOV /home/rd/proj/pm-governance has extensive
unrelated WIP. Commit only the two owned task plans, compact ledger and exact owned
archive as appropriate; no wholesale staging or unrelated architecture edits.

LIVE /home/rd/far-frontier-server; far-frontier-v3-live.service; wrapper3316541,
port25565; invocation16b704e1f5704fc290b9c8fd180470e1; notBefore1793965270.
World buildings-packet-dining-seed139-20261106,seed139,frontier-v3-textiles-terrain-r32,
schema342/graybox ledger7/Network7. A fresh world was selected when4b07 added a typed
meal event and changed descriptor inventory. A609 resumed that SAME world. All
prior native servers saved every dimension and exited before installs. Diagnostic
older d2/6a worlds retained for identified evidence, not overwritten or migrated.
Core SHA512:
f4bace11724477940e48a8c5f5efa045cc26f18d57ea884fa36eac66adf311df1f86b352cb85e593c8f6f2fa4bc41222c31169241c8c295668ddb498ae823f50
Visuals unchanged, SHA512:
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601

Native tick rate RESTORED20, no sprint, no queued fast-forward. All task pilot/native
test clients and isolated server JVMs CLOSED; none left idle. Live server remains up.
Final snapshot52748: NORMAL,21nourished,2hungry/2active new meals,92bread/90available;
0starving. This is a progressing next demand cycle, not a claim of perpetual zero
hunger. Summary52744: ok,0required/inventory/scene conflicts. Final deploy verifyPASS
in IMPL build/doors-porches-followup/public-waiting-deploy-verify.log. Native query
reports normal20TPS; its post-client0.2ms average is COLD, not a HOT performance claim.

## Root causes and ownership

Door flicker: actual Minecraft ServerGamePacketListenerImpl.handleUseItemOn
acknowledges prediction and sends block corrections outside the game-mode call.
Deferring only ServerPlayerGameMode.useItemOn exposed old closed state between
predicted open and admitted open. The isolated PlayerUsePacket adapter now defers
the WHOLE native packet before ack/correction; cancellation reconciles exact planned
writes and cumulative ack once. Native events, validation and WAL durability remain
owners. Client pilot observes actual property transitions through acknowledgement.
Paired native plans retain matching leaves/vertical halves, reject unrelated doors.
Prior d2 fixed dependent geometry WRITE_PENDING fence oscillation (same dependencies
for direct and dependent cells);9aff stopped importer headroom carving that deleted
original porch stairs and added connected stair-edge geometry fallback.

Hunger: storage buffers were centred on interior pantry and hashed across roofs;
shared waiting ignored existing FacilityPedestrianContacts.noParking and used service
permissions for applicants. Providers now supply declared exterior, public waiting
knowledge and no-parking policy to ONE common cache/selector. Existing bounded
ReachableSurfaceArea proves connected candidates; cache checks both knowledge
versions. Egress retains service geometry. No pantry-specific routing or whole-building
box exclusion (authentic courtyards are inside source fabric boxes).

Eater1-10 drifted outside dining after HOT arrival; subsequent COLD CONSUME caused d2
quarantine at33420/revision74094 and normal server stop. Meal owner now emits typed
DiningRequired, retains portion/claim, resumes dining navigation. Pending HOT consume
keeps its fence, may regain only its declared dining area, and rechecks actual supported
pose before spending food. No suppressed invariant, fabricated consumption or retake.
Resident1-1 PREPARED birth was physically obstructed by a chest outside service zones,
proven using exact old snapshot/d2 decoder. Eligible service-purpose births now reuse
existing bounded local placement proofs outside service zones; indexed bodies never
teleport. Owner permissions/pending effects remain enforced.

MOVING analysis: raw ActorLocation.body is last persisted position; compare retained
COLD ActorMovementProjection as-of. Old MOVING records were not overdue. Many stationary
PRESENCE actors were already at goal; pendingAdoption is entity-save synchronization,
not by itself a gameplay handoff deadlock. Actual meal stalls were repaired above;
no claim that every possible movement case has been exhausted.

## Verification and limits

All receipts under IMPL build/doors-porches-followup unless stated:
- placement-dining-closure3.log:36Frontier+50NeoForge scoped checks PASS;
  Node packet-scenario-schema.log PASS. Final public-waiting-closure3.log:
 34affected Frontier checks+guardrails PASS. RELEASE structural-release342/
 public-waiting-release.log:guardrails+affected checks+jar/verifyPackagedJar PASS.
- Native building-authoring7/7PASS, original registry/NBT/materials and actual pantry
 path from declared kitchen entrance. Rear-side exploratory probe was not a global
 connectivity proof; fixture now has terrain below original gravel.
-4b07 full-pack packet-fullpack2/manifest.json PASS77actions/12frames,
 run69483220-27fc-41e4-aa0b-1da2b2d56afe;16clicks recorded only false->true / true->false
 with ack, both paired leaves verified, loaded-mod inventoryPASS. Opened actual textile
 porch and kitchen frames. Door protocol/assets unchanged by a609.
-4b07 isolated checked-in disposable-communal-dining PASS, run97e62a24-7c7d-440f-
 bf2d-923214332d5d:actual HOT resident6-1,pantry192->191,NOURISHED/NONE/claim0,
 replica current. Opened endpoint resident frame; not an eating-animation/M3 claim.
- a609 full-pack public-waiting-kitchen-cohort/manifest.json PASS7actions,
 rund68e9a01-c408-4653-a357-4fcbbd6f49fc,loaded-mod inventoryPASS,continuous normal20TPS
 HOT kitchen46513..51319. Retained read-only cohort samples prove actual progress:
46891:1hungry,47607:2new hungry,48916/49037:23nourished/0meals/0claims;
 later needs recur (not failure). Nora1-20 had one rejected decorative waiting target,
 then consumed at50680 BEFORE client exit51319, with no repeat/refreeze. A transient
 rejected candidate is not a global route failure. Final kitchen bread100,4new active
 meals at51319; source and food effects continued after exit, no quarantine.
- public-waiting-cohort-watch.json/log contain9bounded snapshots with native positions,
 authority and nutrition. Some far actors remain COLD outside the loaded area; do not
 claim all23 bodies stayed HOT. Observed loaded eaters consumed under physical custody.
- First build-only HOT fast_forward was correctly REJECTED for physical work, not a
 product crash. Ordinary COLD24000 interval stopped safely on presentation demand;
 no forced HOT advancement. A609 door replay fixture failed because an NPC had left
 the kitchen door open, violating its initial-closed precondition; door end-state was
 not patched or forced. Successful unchanged door receipt reused appropriately.

Expanded unit selection exposed2obsolete ServiceApproachProgressTest depot-food
fixtures and ResidentPortableMealTest's old home-eats-in-place premise. They were not
silently deleted/disabled; no all-historical-tests-green claim. Affected body-confirmation
fixture was migrated to actual retained pantry and passes. Whole old suite failures
and unsupported families remain separately documented; do not reopen indiscriminately.

Build Java21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2; live Java22
/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java. RCON helper IMPL
build/forestry-native/query-runtime.mjs; never print server.properties secrets.
