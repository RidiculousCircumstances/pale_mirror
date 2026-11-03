# Active continuity — 2026-11-03

## Current assignment — COMPLETE, COMMITTED, DEPLOYED

User requested limited correction during long melee approach and a broader
visible sword arc. Implemented shared physical correction plus finite early aim
tracking and cross-body rendering; verified real client lateral movement/hits,
negative/recovery boundaries, committed and deployed a new disposable world for
new hashed rules. Sole main/no agents/push/goal. No task client/private server/
Xvfb remains. Detail: `docs/frontier-v3-guided-lunge-20261103.md`.

Long attack half-angle85degrees(total170), early aim tracking650permille of native
preparation, max12degrees per observed native tick; final preparation/contact
freeze facing. Default6-tick long preparation tracks through tick3, then locks.
Physical correction is within2 support steps of the retained approach. Native
reach/LOS, exact current target/body/equipment, ordinary hurt protection, full
post-durability windup, finite contact and one-shot receipt protocol remain.
Client long attack now draws across the torso and follows through sideways;
impact no longer snaps its facing onto a later target. No COLD damage or new
network payload/dependency. Acceleration remains bounded/cooldown-owned.

MeleeGuidance supplies physical requests through registered combat permissions,
using common BodyObservation for both supported bodies. LocalGoalCorrection is
weak/object-bound, complete actuation +original MovementOrder +producer token,
finite geometry and2-tick renewable lifetime. Common ActorMovementNavigation/
GoalNavigation alone actuate under retained parent order and ordinary service/
collision/courtesy permissions. Correction arrival is not parent completion.
COLD/replaced/missing legs cannot borrow it; commitment freezes its last point;
impact/cancel withdraw exact request. No role-specific job branch in navigation.
Schema325 explicitly rejects old worlds because the hashed policy changed.

## Verification

Guardrails +Frontier combat/security/codecs113 +NeoForge906 PASS2m47s,
/tmp/pm-lunge-final-check.log. Scene-strikes12/12 PASS1m20s,
/tmp/pm-lunge-check-3.log. Coverage: gradual/wrap-aware early turning, frozen late
aim, broad side contact with ordinary native reach, rear dodge, corrected-order/
body/expiry fences, no false parent arrival, cross-body animation/recovery and
prior journal wait/full visible windup/no replay checks. No performance timing
assertions or new harness.

Checked-in retained-guided-lunge9/9 PASS,
IMPL/build/guided-lunge/retained-native-4.json,
run739c7427-78d0-4838-9ec5-5379a50d5497. Real saved/restored325world
v3-disposable_guided_lunge-11f451f0, seed41/peaceful-guards, guards/iron swords
naturally manufactured during original10k COLD prelude, remembered hostility.
Ordinary player fill commands built/cleared9x9 flat arena around actual guard;
no NPC position/items/jobs/health or canonical/WAL fabrication. Controlled player
maxhealth1000/no regen/knockback-resistance1.400samples/400ticks, actual keyboard
travel85.58blocks, human_era400/400, rendered LUNGE97samples,6 moving long-prepare
samples. Five real hits DURING motion994->964 plus one before1000->994; later
stationary interval ended946. Escape preserved946 and responders/contact/plan0.
Exact guidance attempts27040/27115 applied real damage. Summary
build/guided-lunge/combat-evidence.json; linked frame reviewed for actual HumanEra
body/floor, dynamic pose proof belongs to samples, not that single frame.
This is scoped warm-world lite mechanics, not survival balance/full-pack/M3.

Earlier setup failures were classified/corrected without product changes:
wrong copied road-height visit; circuit crossing a raised curb (actual client
frame confirmed); exact-two guard count invalid after third naturally armed;
a second roster actor was not necessarily the responding actor. Final case
binds exact nearby first actor and player-built flat arena. Removed scratch
incorrect disposable scenario, no duplicate harness or repeated10k prelude.
All private processes/Xvfb/25583/4 stopped; server saved19:19:49.

Prepared-6 content1762dd9e12b09a7468836e676d8fd8adbb34107cddd8b1302d928f56e9aee2ad
matches committed source, committed-source-reuse.json. Native JAR SHA256
1125b8d8f6a249d91647b083eb76976f6129552a86a07d03213f5bb3cf7dbbd3.
Detached release guardrails/jar/verifyPackagedJar PASS14s; all6693 ZIP entries
match native except META-INF/pale-mirror-runtime-identity.properties
(release-class-reuse.json). Reuse that exact byte-bound evidence, no extra full
run claim. No unresolved product failure from these focused checks.

## Repositories

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926,
HEAD2fc2a8a3d1a7c45a0abb961737e2a165f36e1c82 CLEAN.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached same.
PACK /home/rd/proj/minecraft HEAD888ed90d5e3b80b18c278107aa83bb003b541f69,
trackedCLEAN, pre-existing.f0v-baseline/ retained.164Packwiz inputs validated;
exact PM +unchanged Visuals published. Client update required for visible sweep,
not a new dependency/network protocol. No push. GOV extensive unrelated WIP
preserved; only own combat boundary line/ledger/new guided-lunge note committed.
Canonical AGENTS/protocol unchanged: sole main and subagents prohibited.

## MAIN runtime — latest delivery

Fresh selected world guided-lunge-schema325-seed139-20261103,
seed139/rootr31 agricultural-flat. Old138world saved all dimensions19:25:45 and
actual JVM3263929 plus25565/25575 closed before shared installer. No old schema
or operation edited; new world begins ordinary production, no injected guards.
far-frontier-v3-live started1793716026 (19:27:06 +05), wrapper4077307 /
actual Java22PID4077333,25565/RCON25575. Source2fc2a8a3d; release SHA256
c6f9c01fb4df17182e3d083c324cac20a32f210489605c17a433de1f08d518cf;
SHA512ebb20eefaa804e234d9a39246098fcb20357be0a297198b4c8224bb8bb7f1ce77fe6bb5d78fe6c8b7db258b763ad7eaa7e76a7337f437de985d4ee4f7ee30ac9.
Preflight +fresh-world-absent preflight/install/fresh startup verify PASS,
/tmp/pm-lunge-*.log. Summary3072/rev3414 ok/green, required/scene/inventory
conflicts0, incidentIndexSize0, build/guided-lunge/main-summary.txt. Startup/state
proof, not a new full-pack combat run. Live MAIN running, no task clients.

Build JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Artifact host127.0.0.1:18092 serves RELEASE/pale-mirror-neoforge/build/libs;
pack8092 serves PACK root. Visuals hosted/pale_mirror_visuals-current.jar,
SHA5122fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
Detached build -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
RCON helper IMPL/build/forestry-native/query-runtime.mjs reads properties silently.
Transient MAIN unit disappears after stop; recreate with systemd-run --user
--unit=far-frontier-v3-live --collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Require native all-saved,
actual JVM and ports exit before replace/reset. Worlds disposable; no unrelated
cleanup/history or publication authority.

## Prior deliveries — do not redo

3fb952955 fixes native windup before journal admission and close contact blocked
by ideal flank, removes permanent pursuit sprint; actual6hits/12sec after restart.
`docs/frontier-v3-melee-contact-20261103.md`.
f2de641e5 exact player-item contribution cause instead of malformed/reused-slot ID;
`docs/frontier-v3-plain-item-click-20261103.md`.
344d29b5f common party/envelope/engagement/sectors/body-fenced waves;
`docs/frontier-v3-coordinated-combat-20261103.md`.
316eb206b managed HumanEra/FreshAnimations/EMF/ETF and semantic poses;
`docs/frontier-v3-human-residents-20261103.md`.
9ae557ddf BURST/BACKSTEP;
`docs/frontier-v3-combat-maneuvers-20261103.md`.
3e0eb77fb meal resumption;
`docs/frontier-v3-meal-resumption-20261103.md`.
