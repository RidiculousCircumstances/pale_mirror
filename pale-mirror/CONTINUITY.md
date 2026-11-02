# Active continuity — 2026-11-03

## Assignment / authority
User accepted melee improvements1/2/4 and distinct short-strike/lunge animations;
implement, verify, commit and deploy. Main alone, no subagents/API goal/push.
Standing authority permits fresh disposable worlds, verified publication/deploy.
Canonical protocol docs/engineering-agent-protocol.md. No full-pack human/M3 claim.

## Current result
Source COMMITTED8aad1392042d17f88a156e041b3ee6df2a74c828 in IMPL
/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror, branch
feat/baker-carry-orders-20260926, CLEAN. Accepted mechanics implemented:
QUICK preparation4ticks vs LUNGE10; opportunity allowance does not extend actual
weapon reach. Exact same INTERCEPT movement may continue during preparation;
no teleport/second actuator. Recovery delays the next strike without stopping
pursuit. Only current role permission, exact body/weapon/target incarnation,
LOS/reach and finite native hit window permit journaled damage. Dead pending
actor cannot resume movement; pending effect cannot reselect a mission.
Client payload/cache and original articulated vanilla Villager model animate
arm/elbow/sword/torso, retract cancellation and show remaining cue on tracking.
Visual focus yaw is independent of physical navigation heading. Server entities
and UUIDs stay Villagers. No new animation dependency; client update required.
Schema322 intentionally needs a fresh MAIN world. Durable design and verification:
docs/frontier-v3-melee-techniques-20261102.md. Earlier unrelated fixes/cause record:
docs/frontier-v3-security-hot-findings-20261102.md.

## Verification / reuse
Full integration PASS8m47s: Frontier2270, NeoForge900, native GameTests416,
guardrails/check/build/package; receipt IMPL/build/melee-techniques/integration.json,
/tmp/pm-melee-integration.log. Subsequent scoped combat/death/pending movement,
client/animation/parser checks PASS; logs /tmp/pm-melee-{preparation-finish,
final-pose}.log. Domain324/Visuals79 unchanged evidence reused, not rerun.
Node scenario56 PASS /tmp/pm-melee-scenario-node.log.
Final exact native JAR SHA25608e7adc39971b2cd382a4195fe58109ba9de3aca598555998b516fceaa0a8f3e,
identity build/melee-techniques/prepared-final-native.json. Checked-in
live-melee-techniques.json PASS16/16 (live-before5.json): ordinary real player
harm/shared response, actual rendered QUICK/LUNGE and actual prep movement,
escape during preparation causes no additional damage, terminal cleared response.
Normal saved stop00:40:28 then SAME-world restart:
live-melee-hostility-recovery.json PASS4/4 (live-recovery.json), remembered player
was actually hit without a new offence, then escaped and response cleared.
Actual frames-final5/0011 raised short strike and0124 lunge plus neighbouring
frames reviewed; recovery manifest links its real frame. Task20FPS graphical
client on private Xvfb, not full-pack HUMAN_CANDIDATE. All task-native clients,
private display/server and25583/4 STOPPED normally, no save CME.
Seed41/schema322 native world retained at IMPL/pale-mirror-neoforge/build/runs/
frontier-v3-pilot-server/v3-disposable_melee_tactics_restart-e6d9768f.
Original aggregate/followups remain FAILED: inside-head/lost camera, early
sampling or over-specified PRESERVED reserve while guards ate. Current observer
follows actual rendered actor and waits preparation; no canonical fixture edits.
Logs showed restored moving bodies, not proof of a separate body/meal defect.

## Completed release / delivery
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror is CLEAN detached8aad.
Release guardrails/build/verifyPackagedJar PASS2m37s, all900 NeoForge tests0
failures, log /tmp/pm-melee-release.log. Unchanged SourceGraybox first-year seed
check accounts for most of that release test time; no repeat needed.
Native and release6445class files byte-identical; only artifact difference is
META-INF/pale-mirror-runtime-identity.properties. Exact reuse/verification receipts
IMPL/build/melee-techniques/{release-class-reuse,verified-release}.json.
Release JAR SHA2568dd120f46365265f8c33806a85f93a9c71b5242c00bcf8b5e71b01979572175f.
Root publisher validated all158 Packwiz inputs, published required PM and
unchanged Visuals; public hosted checksum matches. Hosted outputs are ignored,
so PACK has no tracked change to commit. No automatic push.
MAIN normally stopped00:46:00 with all dimensions saved/operations0; installed
exact pinned artifact through shared root installer. Fresh selected world
melee-techniques-schema322-seed135-20261103 with rootr31/agricultural_flat pack.
Root preflight and deploy-verify PASS; no world/history deletion required.
/tmp/pm-melee-{preflight-final,deploy-verify}.log; actual runtime health green.
No remaining work for the accepted scope. User must update client for animation.

## Repositories / preserve WIP
PACK /home/rd/proj/minecraft HEADf72713382957 trackedCLEAN, preserve existing
untracked .f0v-baseline/. Hosted publication complete; tracked pack unchanged. RELEASE clean as above.
Governance HEADf2d41157b has extensive unrelated WIP. Only own CONTINUITY.md,
docs/frontier-v3-melee-techniques-20261102.md and architecture key
melee_techniques_boundary may be committed. Partial-stage architecture as HEAD
plus only this key; never bulk-stage/discard others. Earlier native return-fence
threading fix is already source history, not new scope.

## MAIN runtime (verified)
RUNNING /home/rd/far-frontier-server, far-frontier-v3-live.service. Wrapper3929406,
Java22PID3929432,25565/RCON25575, source8aad13920. Start1793648795.
Fresh schema322 world melee-techniques-schema322-seed135-20261103, seed135/r31.
SHA2568dd120f46365265f8c33806a85f93a9c71b5242c00bcf8b5e71b01979572175f;
SHA51213a91831d6486a4d19da5788fe5bbc176bf3359fa2601981ec6989094c387a2f01b0e3530e97eff7193e142e2436fe183956bf5c6273aad15768eff9618176e1.
Actual inspect summaryrevision965/tick384 green, required/inventory conflicts0,
12settlements/356residents. IMPL/build/melee-techniques/deployed-summary.txt.
Earlier old321 world remains untouched, no backwards compatibility claim.
RCON helper IMPL/build/forestry-native/query-runtime.mjs silently reads properties;
never print credentials. Normal stop must confirm all saved dimensions and actual
PID/ports exit. Transient service disappears on stop; recreate via systemd-run
--user --unit=far-frontier-v3-live --collect
--working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh.
JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Artifact host127.0.0.1:18092 serves RELEASE/pale-mirror-neoforge/build/libs.
Detached Gradle catalogue property points to
/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
