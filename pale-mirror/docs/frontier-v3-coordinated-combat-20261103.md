# Coordinated melee — 2026-11-03

Accepted scope: group pressure/flanks/cutoff, fast approach runs, bounded
coordinated attack windows, shorter credible lunges and physical player/guard
reach comparison. Preserve one body controller and common navigation; no COLD
hits, NPC commander, omniscient target tracking, extra reach or invulnerability.

## Ownership and active path

CombatEnvelope (renamed old CombatEngagement) remains role permission: target,
purpose and spatial pursuit limit. New CombatEngagement in CombatState owns
cooperation, keyed by declared CombatPartyId +CombatTarget. Guard producer
explicitly supplies its home party; generic mechanics never discovers settlement,
appointment, political allegiance or job internals. Another attacker/escort role
can register its CombatCapability and declare a party using the same mechanism.
No new production attacker role is enabled by this work.

Participants retain exact execution and stable sector. MeleePosition no longer
stores a second authoritative sector. Physical claims remain in the existing
common combat registry. A local pure CombatEngagementPlanner reconciles the
changed encounter on existing typed combat facts; no extra periodic world scan,
process family or actuator. One final FrontierWorldStateUpdate publishes all
changed combat/inventory state, avoiding an intermediate whole-state rebuild.

MeleePositions projects real geometry candidates; CombatApproach ranks outer
arc waypoints, then closing positions. Routes, collision and physical body
movement belong to existing knowledge/navigation and UAE. A completed outer
waypoint is not a failed attack station. BURST must respect flank alignment.
Confirmed NO_PATH/NO_POSITION can relax a blocked flank into SUPPORT; waiting
for an asynchronous route cannot. The pressure vacancy is reassigned when a
member leaves. Role exit/pause/death/retirement calls the same withdrawal API;
removing membership never erases an unsettled exact physical strike.

Wave slots are single-use and retain ActorActuationId (exact execution +physical
epoch). Native readiness distinguishes stopping from being able to begin an
attack while approaching. Ready members get bounded assembly and staggered
windows; one ready member eventually attacks without an unreachable companion.
Actual reach, LOS, equipment, target incarnation and the native journal remain
mandatory at impact. No animation or group label grants damage authority.

## Current policy

Walk unchanged; RUN3x, MANEUVER2.8x actor base gait. Lunge movement1.5x run during
its preparation, then normal role combat gait. Preparation6ticks, active4,
recovery8; approach allowance.65block, actual weapon reach unchanged. Initial
commitment cone55degrees for lunge,90quick. Outer radius5blocks, arc step45deg,
assembly8ticks, release lead2ticks, attack slots spaced12ticks with8tick windows,
up to3 ready attackers per wave. Selection favours earlier attack readiness for
fairness. These values enter the world-hashed melee/locomotion policy.

Vanilla exact1.21.1/NeoForge21.1.248 compile source via ModLens: Player2216–2226
uses interaction-range +caller margin from eye to target AABB. Server packet
handler1558 allows another1block for packet admission. PM and ordinary sword
base use3blocks; do not copy packet tolerance into NPC damage range. LivingEntity
1162 rejects equal repeated damage while invulnerableTime>10; stagger instead
of bypassing the target's normal protection. Fresh Human Era is presentation;
normal Villager body dimensions and hitboxes remain authoritative.

## Verification / delivery

IMPL source344d29b5f40fb0499e6bf17686dc9fd0715af4e0. guardrails/check/full
417GameTests executed in /tmp/pm-encounters-integration.log8m39s:324domain PASS;
2281Frontier had one obsolete RUN1.5 expectation;904NeoForge had one old7.5tick
lunge animation sample. Both expectations corrected; focused EvasionLifecycle/
MeleeAnimation and9scene-strikes (including actual player reach comparison)
PASS /tmp/pm-encounters-boundary.log1m11s. Coherent combat/security/reaction/codec/
layered-navigation checks, including partial geometry, PASS24s
/tmp/pm-encounters-geometry.log. Packaged artifact verified. Node56PASS
/tmp/pm-encounters-node-final.log. No meaningful negative gate suppressed.

Final checked-in disposable-coordinated-melee native carrier PASS15/15,
IMPL/build/combat-engagements/native-3.json, run60e51733-5072-48c5-82ae-698a1dc99ebc,
worldv3-disposable_coordinated_melee-7e0bd81f, seed41/schema324/peaceful-guards.
Ordinary finite inputs manufactured two swords and appointed resident1-16/Finn
Brooks and resident1-28/Finn Moss. Real player sword harm produced a shared plan
with PRESSURE and FLANK_LEFT. Exact second body261poses showed19.61blocks maximum
displacement and98.53degree relative angular span around the actual observed
player (whose position also changes under native knockback). Both LUNGE and
QUICK presentation were sampled; Human Era260/261, first pre-render sample
unobserved. This is actual movement evidence, not an assigned-role-only claim.

Open-fight normal save14:10:41 and new server readiness14:11:01 preserved hostility,
followed by fresh two-member engagement and actual QUICK from the first exact
body120poses/HumanEra120. Same ordinary client JVM reconnected. Escape produced
no additional health loss and terminal contactCount/respondingGuards/engagementCount0
at11429/11431; hostility retained. Exact private JVMs/ports/client/Xvfb stopped.
Both linked frames personally inspected: one foreground human fighter and an
additional sword at the later edge. These views do not establish full-cohort
visual tactics, difficult-terrain coverage or full-pack human M3. Shared stair/
damaged connection route coverage remains an isolated navigation invariant.

Committed source bytes equal the frozen prepared identity (source content
4dea400f9ee2452fa540ccb16e1d265c3c807873598ce2919d2a0467f83d8220), receipt
build/combat-engagements/committed-source-reuse.json. Native JAR SHA256
2a7e2f1b845a48f11ab3c0468bb69c8fbf405784255c850758e7177f77b9c952.
Clean detached RELEASE same source guardrails/build/verifyPackagedJar PASS2m47s,
/tmp/pm-encounters-release.log,904NeoForge0fail. All6486 classes/resources match
native candidate; only META-INF/pale-mirror-runtime-identity.properties differs,
build/combat-engagements/release-class-reuse.json. Release SHA256
5ac25cc3e2607d68d9e6ae0e4ed2d104e55a02bf5f6215fb0984c07fd6a2ee99.

MAIN old323world normal save/all dimensions14:16:22, actual old JVM/ports exited
before install. New selected world combat-engagements-schema324-seed137-20261103,
seed137/rootr31 agricultural-flat installed by shared pack scripts. Service
far-frontier-v3-live started epoch1793697498, wrapper2780522/Java22PID2780546,
25565/RCON25575. Actual artifact SHA512
997ede1189a1e57540451514e6c074a013160f71d578fce3d648ca38368942ca079e8ff538a3d3e124325b664c679509b2e734e9a52d936898c5f31bc28d3c59.
Preflight/install/fresh deployment verification PASS /tmp/pm-encounters-*.log.
Fresh summary677/revision1198 and latest6630/revision9329 both
statusok/diagnosticVerdictgreen, requiredConflicts0/sceneConflicts0/
inventoryConflicts0; latest incidentIndexSize0. Normal fresh economy starts
with no guard appointments; ordinary weapon manufacture/staffing must occur.
No weapons or canonical outcomes injected. No new client dependency/network
protocol required; hosted current PM artifact updated. PACK tracked unchanged at
888ed90d5, existing .f0v-baseline preserved. No push.

## Partial-knowledge correction

The outer-arc selection reuses knowledgeMissing, whose clearance predicate still
uses close weapon reach. A known outer support with unknown clearance can therefore
be classified NO_POSITION instead of ROUTE_PENDING when close supports are known.
Fixed the knowledge predicate to use the same outer/close admissibility as candidate
selection. The focused partial-knowledge negative passes, with all other combat/
security/recovery/layered checks and guardrails, /tmp/pm-encounters-geometry.log24s.
Native2 was explicitly stopped/saved at13:58:09 before source edits: its fixture
incorrectly named the second guard resident1-17; observed natural appointment is
resident1-28. This interrupted run is not acceptance. Preparation exposed two
real armed guards and actual native attack preparations from both. Native1
failed before readiness on a stale sibling flat datapack; the explicit current
PACK agricultural-flat r31 override fixed environment agreement.
