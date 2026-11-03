# Active continuity — 2026-11-03

## Current assignment — COMPLETE, COMMITTED, DEPLOYED

Human resident appearance: pinned Human Era Villager Only +Fresh Animations,
base movement/carry and existing combat gestures integrated with isolated rig
adapters. User accepted implementation, standing commit/deploy applies. Sole
main; no agents, no goal, no push. No ongoing monitoring/private clients.
Durable design, API findings, verification limits and exact receipts:
`docs/frontier-v3-human-residents-20261103.md`.

Residents remain the same Villager bodies, with unchanged server gameplay,
movement/custody, damage, schema323 and network2. No independent NPC AI added.
Original author archives stay separate pinned client downloads; no foreign
textures/models embedded in PM. Client must run updater for EMF/ETF and packs.
Managed required group applies FA →Human Era →author activator automatically.
Absent/explicitly disabled manifest chooses articulated vanilla; incomplete
active profile fails visibly. Shared pose compositor handles semantics; concrete
rig adapters own bone names, single native model, geometry and hand transforms.
Bounded caches/receipts are client-only and clear/expire, never canonical state.

## Verification and scope

IMPL guardrails/check/verifyPackagedJar PASS9m13s,
/tmp/pm-human-integration-final.log:324domain/2275Frontier/904NeoForge0fail.
Node56PASS /tmp/pm-human-node-terminal.log. Final scoped native20FPS Xvfb:
IMPL/build/human-residents/terminal-combat.json PASS11/11 actual player harm,
human rig99/100 samples, QUICK pose, escape/no extra damage, terminal response
clear. Run a00d6a5d-0bb8-4e6a-82ab-9de1421f0365, frame personally reviewed.
delivery-carry.json PASS2/2, run1f648bb3-52e5-4fcb-97af-8733de7b6423: same-world
reconnect, actual human rig, ordinary patrol/carry; correct sword grip frame
reviewed. Native-assets.json pins actual optional mod/archive inputs.

Early probes found missing run dependencies, EMF_ bone prefixes, cancellation
flag semantics, premature/custom-state binding, a consumed last-created-root
slot from constructing a second model and a doubled grip offset. All addressed.
Failed optional-BURST/player-circuit runs stay failed; new appearance carrier
checks actual QUICK and terminal behavior, leaving original maneuver carrier
unchanged. Final-byte partial BACKSTEP was observed; earlier same-compositor
LUNGE/BURST evidence is narrower, not a fresh full four-maneuver acceptance.
No canonical/WAL edits or actor teleports manufactured outcomes. No full-pack
HUMAN_CANDIDATE/M3 claim; human visual judgement remains separate.

Final nativeJAR SHA256636fc620d5ee74663a28c31895c3a9ae0662ad695617eaa0dce37f3d852ab122.
Clean detached release guardrails/build/verifyPackagedJar PASS2m43s,
/tmp/pm-human-release.log,904NeoForge0fail. All6472 class files/resources equal
nativeJAR, only META-INF/pale-mirror-runtime-identity.properties differs:
IMPL/build/human-residents/release-class-reuse.json. Client-only mods absent on
private server; dedicated startup still passed. All private clients/Xvfb and
25583/4 STOPPED; saved all dimensions12:22:30. No background observers remain.

## Repositories

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926,
HEAD316eb206bf2db0f4152c7a646fd6f8492e462c92 CLEAN.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached same.
PACK /home/rd/proj/minecraft HEAD888ed90d5e3b80b18c278107aa83bb003b541f69,
trackedCLEAN; pre-existing untracked.f0v-baseline/ preserved. Publisher verified
164Packwiz inputs and published exact PM plus unchangedVisuals. Pin descriptors
under config/pale_mirror/resident-packs and mods/entity-{model,texture}-features.
GOV /home/rd/proj/pm-governance has extensive unrelated WIP. Commit only own
CONTINUITY, new human-residents note and human_resident_visual_boundary key.
Stage architecture as HEAD +the human key and matching melee presentation clause;
preserve other working changes.

## MAIN runtime — latest delivery

Same disposable world combat-maneuvers-schema323-seed136-20261103,
seed136/rootr31 agricultural_flat. No reset. Old MAIN saved all dimensions
12:26:49, JVM/ports closed before shared installer. New far-frontier-v3-live.service
started epoch1793690847, wrapper2358470/Java22PID2358496,25565/RCON25575.
Source316eb206b, installed release SHA256
04ae9677ca4de7edef04cfb652f719aaf60d2778563fcf767434ecbfa79583d2;
SHA51229214f53d10ca3147e7e938cf9b7e0da1bacaf005cdf58820e84d701f4c1922574da58051841084721e2cfc9000e563e17bbe00555d3d5187748051e4b18a1d4.
Preflight/install/fresh deployverify PASS /tmp/pm-human-{preflight,install,deploy-verify}.log.
Actual summary109331/revision289985 statusok, sceneConflicts0,
inventoryConflicts0, incidentIndexSize4. requiredConflicts1 remains historical
kernel failure at25170/revision41320/occurrences1; no new quarantine. Summary's
diagnosticVerdict blocked includes retained history, do not claim wholly green.
Latest raw before/after receipts IMPL/build/human-residents/main-{before,after}.txt.

Build JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Artifact host127.0.0.1:18092 serves RELEASE/pale-mirror-neoforge/build/libs.
Pack host8092 serves PACK root. Detached builds require
-PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
RCON helper IMPL/build/forestry-native/query-runtime.mjs silently reads properties.
MAIN transient unit disappears on stop; recreate via systemd-run --user
--unit=far-frontier-v3-live --collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Require saved dimensions
and actual PID/port exit before reset/replacement. Never print credentials.

## Previous retained corrections (do not restart completed work)

3e0eb77fb fixed exact meal continuation after safety via ResidentMealProcess.wake;
same-world resident1-5 recovered/ate. docs/frontier-v3-meal-resumption-20261103.md.
9ae557ddf common BURST/BACKSTEP, stable multi-guard pursuit and body observation;
adb13df4b preserves exact pending field layout identity across appended land.
docs/frontier-v3-combat-maneuvers-20261103.md retains accepted native/full-gate
receipts, failed-carrier classifications and recovery limits. Existing historical
kernel incident has no acknowledgement/resolution UI; do not suppress its data.
