# Active continuity — 2026-11-03

## Current incident — COMPLETE, DEPLOYED, SAME-WORLD RECOVERY VERIFIED

User-reported11:09:18 MAIN stop: tick25170 activity review for1-5 resumed a
paused meal but emitted a coalescible review request for its exact continuation.
Validator correctly rejected it; not OOM/watchdog. Source3e0eb77fb097e5c848ad96925a178f946af053a2 fixes the producer via ResidentMealProcess.wake:
Rescheduled exact retained meal progress, validator unchanged. Existing nested
work/meal/safety test now crosses real planner/reducer/schedule validation,
rather than stopping at manual prepareResume.45focusedFrontier tests/guardrails
PASS15s; clean RELEASE build/verifyPackage902tests0fail PASS2m36s.
Docs: docs/frontier-v3-meal-resumption-20261103.md. No new follow-up scheduled.

Source IMPL and RELEASE clean3e0eb77fb, detached release published/pinned.
SAME crashed world recovered, no reset. Actual resident1-5 ate at25452;
NOURISHED/satiety993/mealNONE at25941. Latest actualsummary29292/revision50590,
statusok, inventoryConflicts0, no new quarantine. requiredConflicts1 is unchanged
historical kernel-failure incident at25170/revision41320/occurrences1, not a new
failure; do not claim summary wholly green or suppress its history.
IMPL/build/meal-resume-incident/{crash-cause-and-save.log,recovered-summary-final.txt,
recovered-resident.txt,retained-incident.txt,release.json}. No private clients,
no agents/push/goal/ongoing monitoring. Prior combat result below remains valid.

## Assignment and result

PREVIOUS task COMPLETE and DEPLOYED: shared combat BURST/BACKSTEP, native
animation cues and user-reported multi-guard circling hesitation. Sole main
executor; no agents, no push, no goal. No further work implicitly scheduled.
User standing commit/deploy and disposable-world authority remain. Next user
message may steer manual testing; do not restart this completed campaign.
Durable design, findings, actual accepted evidence and invalid intermediate
carriers: docs/frontier-v3-combat-maneuvers-20261103.md.

Source commits: adb13df4b preserves pending field projection causes across
same-epoch append-only extensions; 9ae557ddff61171f64589f6b9ae167844ede2f2a adds
shared maneuvers/stable pursuit and includes that field fix. Both source trees
below are CLEAN; no uncommitted implementation. Previous8aad/schema322 delivery
is superseded, retained worlds/history untouched.

## Decisions and fixes

CombatState alone retains exact execution/body/weapon/target, fixed safe
maneuver destination, bounded preparation/travel/recovery and cooldowns.
Roles supply permission, geometry knowledge and confirmed harm through
CombatCapability. Registered COMBAT_MANEUVER uses existing ActorMovement/UAE,
shared navigator/courtesy; no teleport, invulnerability, extra reach or COLD
combat travel. BURST requests2.2x role combat pace over<=3blocks; BACKSTEP1.5x
over1block. Policy4prep/20maxtravel/6recovery, cooldown100/60/shared40 ticks.
Distinct BURST/BACKSTEP client gestures reuse existing bounded articulated
Villager renderer; network2 requires client update. Schema323/fresh world.

GuardResponse previously invalidated a valid melee station whenever the same
player changed target cell. Same-opponent/execution continuity now preserves
otherwise legal/free/current station/sector; real reach/space/permission loss
still reselects. Only an OPEN maneuver owns movement; closed recovery gates the
next attack/maneuver, never ordinary pursuit. Closed exact attempt remains
historical cooldown across same-owner role resumption, without granting old
body actuation. Role-approved reversible patrol/search interruption is distinct
from INTERCEPT-only strike permission. Optional unattempted proposals expose
policy refusal; witnessed physical effects keep mandatory receipt assertions.

Shared native actor observer consumes immutable HOT goal/order deltas to wake
its existing fair lane; equal order/position/COLD changes do not churn. A native
supported-origin observation is maneuver planning input only; common inspection
remains sole canonical ActorLocation writer. Outside-world/engagement origin
refuses optional admission before transition. No await-every-pose starvation.

Unrelated native field crash fixed: appended land preserves exact old
canonical-first pending causes/history. Physical-first labour/epoch changes
still settle before append. Field witness12 explicitly persists each pending
layoutRevision, rejecting missing identity; no relabelling or legacy fallback.
No economic rate, crop yield or staffing balance change.

## Verification and actual native evidence

Initial full integration PASS9m03, guardrails/check/build/package/all416required
GameTests: /tmp/pm-maneuvers-integration.log. Subsequent affected corrections:
23field GameTests PASS /tmp/pm-maneuvers-field-extension-final.log; missing
pending layout identity negative PASS /tmp/pm-maneuvers-field-identity.log;
combat/authority/navigation/probe/animation/parser scoped PASS26s
/tmp/pm-maneuvers-observed-origin-final.log; outside-origin admission negative
and architecture PASS16s /tmp/pm-maneuvers-origin-permission.log. Node56PASS
/tmp/pm-maneuvers-node-final.log. Unchanged broader Frontier/domain/visuals
coverage reused, no blanket fresh rerun claim.

IMPL build/melee-maneuvers/final-field12-native.json PASS13/13: real ordinary
player harm, admitted BACKSTEP>=.5block and BURST>=1.5block same-cue actual
motion,240tick circuits, escaped target causes no extra damage, terminalclear.
Both guard traces move; coarse wall-second distant stationary interval<=1sec.
334actual JPGs/correctly centred contact sheet reviewed. Later only production
change is outside-origin refusal; this inside-envelope BACKSTEP path unchanged.

Final exact candidate prepared-final-pursuit-valid.json nativeJAR SHA256
1c9b4b5749f1c64c30e10eab0aec1c5fc83afe27c8d0b5c156e9399245d02087:
final-live-pursuit.json PASS13/13 actualBURST/circling/two-guard responseclear.
Saved SAME-world stop10:42:47/all dimensions on
v3-disposable_combat_maneuvers-905b9072, fresh JVM then
final-live-recovery.json PASS4/4 remembered-hostile real hit without new offence,
escape/terminalclear. Frame reviewed. Final normal save10:44:07, all dimensions,
private clients/server/Xvfb and25583/4 STOPPED. No monitoring remains running.
Lite20FPS Xvfb evidence is not full-pack HUMAN_CANDIDATE/M3.

Failed intermediate17carrier remains FAILED: route PLANNING legitimately
prevented optional retreat before an ordinary strike; invalid oracle demanded
a maneuver after every hit. Checked-in carriers now inspect optional reaction,
still require actualBURST/circling/clear; disposable17 declares normal restart
with existing4recovery actions. Accepted final evidence is the actual two live
saved-world runs above, not a relabelled failed aggregate. Earlier standalone
restart used a successful runner's already-deleted world and created an empty
overworld; harness setup failure, superseded by actual retained-world recovery.
No canonical/WAL/body edits manufactured any native outcome.

## Repositories and release

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror, branch
feat/baker-carry-orders-20260926, HEAD3e0eb77fb CLEAN (includes combat9ae557ddf).
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached same ref.
Release guardrails/build/verifyPackagedJar PASS2m38s, all902NeoForge tests0fail,
/tmp/pm-maneuvers-release.log. All6460class files/resources equal final native
JAR; only META-INF/pale-mirror-runtime-identity.properties differs. Receipts
IMPL/build/melee-maneuvers/{release-class-reuse,verified-source}.json.

Release SHA25611bd5849de8edd0fbe1e81d99987765eab82871b08763b6b46a1d50de22fc508.
SHA512412b3d39ba1af6d84ad4262dcf5965b53a51c8f1eb03f2beb81af67eecedb4cf303bde7353ad7df4e7c5be63a0c0fa254e43c1bced53000692fc31410fecfe4f.
PACK /home/rd/proj/minecraft HEADf72713382957 trackedCLEAN; pre-existing untracked
.f0v-baseline/ preserved. Publisher verified158Packwiz inputs and published exact
PM plus unchangedVisuals. Hosted outputs ignored, no root change/empty commit.
Governance has extensive unrelated WIP: commit only own CONTINUITY.md,
docs/frontier-v3-combat-maneuvers-20261103.md and combat_maneuvers_boundary key.
Architecture index staged as HEAD+only that key; working unrelated keys preserved.

## MAIN runtime — latest recovered delivery

Latest source3e0eb77fb, installed by checksum-pinned shared installer after
11:09:21 saved crash shutdown. SAME world combat-maneuvers-schema323-seed136-20261103,
seed136/rootr31 agricultural_flat. Started epoch1793686595,
far-frontier-v3-live.service wrapper2077548/Java22PID2077576,25565/RCON25575.
Release SHA2561dc2104b64193bcf2b841763f53a9123cce5d1500e999ea544ae0a9bc01b868f;
SHA51217939f98a0cf23cf02c06cecdfd8d3b92dffd4af396feee581abe77c817f38c6d57a19d3fa29c59f6106577a087970d64ee1756dd923977befca4cf73350fa71.
Root preflight/deployverify PASS /tmp/pm-meal-resume-{preflight,deploy-verify}.log.
Actual native recovery/remaining historical incident described above. This
server-only correction does not add another client protocol change; prior
network2 combat client requirement still applies.

Build JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Artifact host127.0.0.1:18092 serves RELEASE/pale-mirror-neoforge/build/libs.
Detached build property frontierV3GrayboxDisabledModsCatalog points to root
config/graybox-disabled-mods.txt. RCON helper IMPL/build/forestry-native/
query-runtime.mjs silently reads properties; never print credentials. MAIN
transient service disappears on stop; recreate via systemd-run --user
--unit=far-frontier-v3-live --collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Always require normal
saved dimensions and actual PID/port exit before reset/replacement.
