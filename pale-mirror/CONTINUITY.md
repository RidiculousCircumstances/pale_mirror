# Active continuity — 2026-11-02

## Assignment / delivery
User asked to fix the disclosed existing bugs after variable pace, civilian
flight and guard-tactics delivery. Main alone; no subagents/API goal/push.
Known bug fixes committed and deployed. No required source repair remains.
Final source4e48a32a0d174b9738484e989ac91f21e596ac74. Detailed evidenced causes,
failed-fixture classifications and limits: docs/frontier-v3-security-hot-findings-20261102.md.
No claim that every feature, full harvest, M3 or scale/performance is validated.

## Repositories
IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926 CLEAN4e48a32a0.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached4e48a32a0.
PACK /home/rd/proj/minecraft trackedcleanf72713382957; preserve existing
.f0v-baseline/. No pack/client publication edits or push.
Governance has extensive unrelated WIP: only this task ledger/findings and the
new native_entity_return_fence_threading architecture key may be committed.
Partial-stage architecture as HEAD +that key; never bulk-stage/discard other WIP.
All task-native clients/Xvfb/JVMs and25583/4 STOPPED. MAIN alone remains running.
IMPL native freeze can be lifted; do not repeat unchanged verification.

## Implemented fixes
Ef9f64c85 validates the actual generated agricultural map against canonical
COLD agriculture before genesis/runtime, retaining geological validation. Previous
native field conflict20659 came from stale sibling datapack minecraft:flat/
concrete versus r31 soil, not permission to overwrite concrete. MAIN already
uses agricultural_flat/r31. Native worlds MUST explicitly select the current
companion pack before genesis:
FRONTIER_V3_PILOT_GRAYBOX_DATAPACK_DIRECTORY=/home/rd/proj/minecraft/datapacks/pale-mirror-graybox.
Never retrofit an incompatible saved generator or repair its WAL into evidence.

Three GameTest fixture races fixed: patrol awaits HOT before mock observer loss
(and actually discards that mock); ambient recovery awaits insertion AND common
body confirmation;16 independent assault samples follow PREPARED/RUNNING/
CONFIRMED with finite real no-progress bounds, not fixed unpaced ticks. Exact
identity/effect/formation/cursor assertions and calibration tolerances retained.

246089340 fixes native shutdown ConcurrentModificationException: EntityStorage's
mailbox return-read fence concurrently modified carrier maps/dirty rows while
the server serialized them. Read admission now retains the exact ledger on the
server thread. Aggregate access and atomic fence selection/mutation/immutable
row submission use one monitor; storage workers never access mutable rows or
acquire it. Native deserialization awaits its exact receipt on its own mailbox,
not a server queue that vanilla flush may already block. No schema change.

Later commits correct only native verification data: no incidental cultivation
job identity; observer stands4blocks horizontally outside the body birth cell;
require actual INDEXED/RUNNING, not first native save ESTABLISHED before any save.
A bounded follow-up checks resumed field processing instead of an unrelated
whole-field completion deadline. Existing pilot/oracle reused; no second harness.

## Verification / honest scope
Scoped patrol4/4, ambient3/3, body/recovery/calibration27/27 passed. Focused
carrier/adoption/departure/lifetime/parser116 and guardrails passed, including
real concurrent row mutation/publication. Full416GameTests passed at246;
NeoForge898 passed at a0; final4e48 parser/guardrails/JAR verification PASS17s.
All6426 packaged class files are byte-identical between246 and4e48. Subsequent
changes are scenario data/source metadata. Domain324/Frontier2268/Visuals79
baseline3d evidence reused; corresponding source unchanged. Receipts
IMPL/build/post-threat-bugs/{verified,class-reuse,prepared-progress}.json.

Native2ffc2096, fresh seed41/current pack: same-client checked-in threat carrier
passed6 real harm/escape actions, normal save/restart22:07:56, then4 quiet/resume
actions. Same named UUID retained; safety cleared/depth0/prior field job restored.
The aggregate was deliberately stopped in its unnecessary whole-field wait;
its failed manifest is preserved and MUST NOT be relabelled13/13. Only the
unchanged completed scopes are evidence. Real scoped follow-up on normally saved
same world uses checked-in live-threat-resumed-field-progress.json and exact4e48
JAR: live-progress.json PASS6/6,400canonical ticks, same actor/job, cell-processing
tail67->69 plus physical movement. Final actor health17, FIELD_HARVEST/depth0/
no safety, body RUNNING and first save ESTABLISHED. Both frames reviewed; no
full-grain delivery/full-harvest or human M3 claim. Native/release JARs match.
Private normal stop22:17:36 saved all dimensions, unusedProofs0/pending18 retained,
no CME. All private clients/Xvfb/JVMs and25583/4 closed. Pending18 is not0.
Logs /tmp/pm-post-threat-{native-offset,live-progress,resume-progress}.log.

Earlier failed cc1801c2 exposed CME; its failed save is never recovery acceptance.
44c338c4 falsely pinned a cultivation job, stopped normally/operations0. caaca827
rendezvous was observer interference: BODY_SPACE_OCCUPIED ->ordinary player move
->READY ->same UUID INDEXED/RUNNING. Its exploratory inspection was not formal
acceptance. No additional production actor-loss defect was established there.

## MAIN runtime
RUNNING /home/rd/far-frontier-server, far-frontier-v3-live.service.
Final source4e48a32a0, actual Java22PID3366965/wrapper3366916;25565/RCON25575.
SHA256f73ee84866442c223354d7d3c5522307f7b8e3d09cfb698195e8f9a6a3591738;
SHA512ba66ec50214a01611d9ea0d5ec8ccbd4a48cb895313c0f06f5e88b470bdf2ffb7a0bdfb775bcbdbcd609f7ccfc18e9f0e2fe45487bc0f17e9643815061108490.
Compatible worldthreat-movement-schema321-seed134-20261102 retained, seed134/r31.
Prior normal stop saved all22:18:19/operations0 before root pinned installer.
New start1793639948; root preflight/shared installer/deploy-verify PASS.
Summaryrevision491006/tick151250 green, required/inventory conflicts0,
12towns/363residents. Full binding verified.json; /tmp/pm-post-threat-
{preflight-final,deploy-verify-final}.log. No client update required.
Address100.100.78.29:25565 / LAN192.168.0.103.

## Operations
RCON IMPL/build/forestry-native/query-runtime.mjs silently reads credentials;
never print server.properties. Stop MAIN via normal RCON and require saved
dimensions +real JVM/port exit. Transient unit disappears after stopping;
recreate with systemd-run --user --unit=far-frontier-v3-live --collect
--working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh.
Use root preflight +shared checksum-pinned installer +deploy-verify, exact clean
detached source required. JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Detached catalogue property:
-PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
Artifact host127.0.0.1:18092 serves RELEASE/pale-mirror-neoforge/build/libs.
