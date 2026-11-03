# Active continuity — 2026-11-03

## Current assignment — COMPLETE, COMMITTED, DEPLOYED

Shared cooperative melee encounter, pressure/flank/cutoff/support, faster combat
runs, staggered single-use attack windows and shorter credible lunge. User accepted
implementation. Sole main; no agents/push/goal. No private client/Xvfb or monitoring
process remains. Detailed design, findings, evidence and limits:
`docs/frontier-v3-coordinated-combat-20261103.md`.

CombatEnvelope is the renamed role permission; new CombatEngagement in CombatState
owns the plan keyed by explicit CombatPartyId+CombatTarget. Guard adapter declares
home party; common mechanics never inspects guard/settlement internals. Exact
execution members retain stable roles; MeleePosition alone owns spatial claims.
Wave slots retain ActorActuationId including body epoch, are single-use and persist
under schema324 (323 unsupported). Local pure planner reacts to existing combat
facts; no global scan/process, NPC commander or second actuator. UAE/navigation
alone route/actuate; real outer arc before flank closure. Shared candidate/knowledge
admissibility prevents unknown outer clearance becoming NO_POSITION. Confirmed
flank failure can yield SUPPORT, async waiting cannot. Mission exit/pause/death/
retirement withdraws exact member without erasing unsettled hits; last member
retires plan. HOLD and attackReady are separate physical facts.

World-hashed policy: walk unchanged, RUN3x/MANEUVER2.8x actor base; lunge movement
1.5x run, preparation6/active4/recovery8, approach allowance.65block, actual reach
unchanged. Commitment cones55deg lunge/90quick. Outer5blocks/45deg arc, assembly8,
lead2, slot spacing12/window8/max3. Vanilla exact source confirmed native player
hitbox reach and ordinary hurt protection; no bypasses, COLD hits or new attackers.
Another combat role can register a capability and declare cooperation identity.

## Verification

Critical integration /tmp/pm-encounters-integration.log executed324domain,
2281Frontier,904NeoForge and417GameTests. Two obsolete policy expectations failed
(RUN1.5 and old7.5tick lunge sample); both fixed, focused EvasionLifecycle/
MeleeAnimation plus9scene-strikes (actual player reach comparison) PASS1m11s
/tmp/pm-encounters-boundary.log. Combat/security/reaction/codec/layered checks with
partial-geometry negative and guardrails PASS24s /tmp/pm-encounters-geometry.log.
Packaged JAR verified. Node56PASS /tmp/pm-encounters-node-final.log.

Final checked-in disposable-coordinated-melee native PASS15/15,
IMPL/build/combat-engagements/native-3.json, run60e51733-5072-48c5-82ae-698a1dc99ebc,
worldv3-disposable_coordinated_melee-7e0bd81f, seed41/peaceful-guards/schema324.
Finite ordinary inputs produced swords/appointments naturally (10000tick prelude).
Actual workers resident1-16/Finn Brooks and resident1-28/Finn Moss. Real sword harm
produced PRESSURE+FLANK_LEFT; second exact body261poses:19.61block maximum
displacement/98.53deg relative angular span (player also moves from knockback),
QUICK and LUNGE rendered, HumanEra260/261. Normal open-fight save14:10:41, new
server ready14:11:01, same client reconnect, hostility retained and fresh2-member
plan. First exact body120poses/HumanEra120 included QUICK. Escape produced no late
health loss and terminal contact/respondingGuards/engagementCount0 at11429/11431.
Frames personally viewed; one foreground fighter and another sword at edge. This
is scoped M2 physical evidence, not full-cohort tactics/full-pack M3/global TPS.

Native1 rejected stale sibling flat datapack; explicit PACK agricultural r31
fixed environment. Native2 was deliberately stopped/all-saved13:58:09 because
fixture named second actor1-17 rather than observed1-28, before source correction;
not acceptance. Unknown-outer-clearance source finding fixed after saved stop.
Native3 is final candidate; no suppressed gates or fabricated NPC outcomes.

Committed source bytes equal native source content
4dea400f9ee2452fa540ccb16e1d265c3c807873598ce2919d2a0467f83d8220,
receipt build/combat-engagements/committed-source-reuse.json. Release6486classes/
resources equal native except META-INF/pale-mirror-runtime-identity.properties,
release-class-reuse.json. Detached release guardrails/build/verifyPackagedJar
PASS2m47s /tmp/pm-encounters-release.log,904NeoForge0fail.

## Repositories

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926,
HEAD344d29b5f40fb0499e6bf17686dc9fd0715af4e0 CLEAN.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached same.
PACK /home/rd/proj/minecraft HEAD888ed90d5e3b80b18c278107aa83bb003b541f69,
trackedCLEAN; pre-existing .f0v-baseline/ preserved.164Packwiz inputs verified;
exact PM and unchanged Visuals published. No dependency/network changes.
GOV /home/rd/proj/pm-governance retains extensive unrelated WIP. This task changes
only the ledger, new coordinated-combat note, combat_engagement_boundary key and
new Schema324 materialization section. Architecture/inventory commits include
only those additions; other working changes preserved. No agents/automatic push.

## MAIN runtime — latest delivery

New selected world combat-engagements-schema324-seed137-20261103,
seed137/rootr31 agricultural-flat. Previous323world saved all dimensions14:16:22,
old JVM/ports closed before install. Service far-frontier-v3-live started
1793697498 (14:18:18 +05), wrapper2780522/Java22PID2780546,25565/RCON25575.
Source344d29b5f, release/installed SHA256
5ac25cc3e2607d68d9e6ae0e4ed2d104e55a02bf5f6215fb0984c07fd6a2ee99;
SHA512997ede1189a1e57540451514e6c074a013160f71d578fce3d648ca38368942ca079e8ff538a3d3e124325b664c679509b2e734e9a52d936898c5f31bc28d3c59.
Preflight/install/fresh deployverify PASS /tmp/pm-encounters-*.log.
Latest summary6630/rev9329 statusok, diagnosticVerdictgreen, required/scene/inventory
conflicts0 and incidentIndexSize0; build/combat-engagements/main-summary-final.txt. Settlement1 route surface
-1486,64,-1360, farm-1460,64,-1372. Fresh normal world has no armed guard yet;
at6837 (0hungry); weapons/appointments arise from ordinary production, no injected NPC swords.
Previous historical requiredConflict1 belongs to the old323world, not a new
incident; its diagnostic history is not acknowledged/erased by this feature.

Build JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Artifact host127.0.0.1:18092 serves RELEASE/pale-mirror-neoforge/build/libs.
Pack host8092 serves PACK root; Visuals hosted/pale_mirror_visuals-current.jar.
Detached build requires -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
RCON helper IMPL/build/forestry-native/query-runtime.mjs reads properties silently.
MAIN transient unit disappears on stop; recreate with systemd-run --user
--unit=far-frontier-v3-live --collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Require normal native
save/all dimensions and actual JVM/ports closed before replacement/reset.

## Previous retained deliveries

316eb206b HumanEra/EMF/ETF +FreshAnimations +author activator integration and
melee/carry poses. `docs/frontier-v3-human-residents-20261103.md` holds exact
receipts, original assets, prior failed-case classifications and rig limits.
9ae557ddf common BURST/BACKSTEP, stable pursuit/body observation;
`docs/frontier-v3-combat-maneuvers-20261103.md`.3e0eb77fb exact meal continuation
wake after safety; `docs/frontier-v3-meal-resumption-20261103.md`.adb13df4b exact
pending field layout identity across appended land. Do not restart completed work.
