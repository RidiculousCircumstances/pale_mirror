# Active continuity — 2026-11-03

## Current assignment — COMPLETE, COMMITTED, DEPLOYED: work animations

User asked to audit/add resident work animations. During implementation a guard
hit report was withdrawn: the player had creative mode; no combat/damage fix.
Latest user steering: they can manually check workers and report missing poses;
give the specific checks, do not expand the automatic observation campaign.
Standing delivery complete; sole main, no agents, goal or push.
Detail: `docs/frontier-v3-resident-work-animation-20261103.md`.

Source648dc5a3f03c89aedf53581a50783a02d0c65da6 adds bounded read-only exact-body
work cues and shared vanilla/HumanEra work poses. Existing native swings were
masked by carrying, and Villager does not advance the native swing clock like
Monster/Player (ModLens MC1.21.1/NeoForge21.1.248). Mining lacked labour-duration
visuals. Work owners now explicitly emit STRIKE/CULTIVATE/HANDLE at their owned
work branches. Common presentation knows no job, resource, role or work-rate rule.
Client gates UUID/runtime ID/epoch/sequence, settling and finite expiry; combat
wins over work, work over carrying/gait. Main-hand work preserves the carried
opposite hand. Navigation and combat retire work cues. No durable schema, world
reset, business progress, resource, actuation or damage change.

## Verification and precise limits

Guardrails+NeoForge908 pass `/tmp/pm-work-check-2.log`; later presentation and
scenario focus49 pass `/tmp/pm-work-final-focused.log`; Node scenario56 pass
`/tmp/pm-work-scenario-node.log`. Detached guardrails/jar/verifyPackagedJar pass14s
`/tmp/pm-work-release.log`. All6705 ZIP entries equal the native artifact except
META-INF/pale-mirror-runtime-identity.properties;
IMPL/build/work-animation/release-reuse.json. Native SHA256
c95c5316b74c87579a4abee93b7c4d6e9799d73ebf045ccb54b19ac033280f25.

Checked-in retained-resident-work-animation first work prefix accepted locally,
run481e85b1-2c8e-4161-9ded-20fedb6b5f9e. Actual retained Owen Bell body
70d472d7-5dbd-3b85-8ead-072d3db39619:240samples, HumanEra240/240,
95 changing CULTIVATE poses, all HARVESTING:RUNNING; shoulder-1.35..-1.075.
Nine travel samples have zero work receipts. Exact near-camera frame inspected;
dark frame is not human/M3 acceptance. Evidence in IMPL/build/work-animation/
work-pose-series.json, rendered-evidence.json, native-6.pmv3.jsonl.
Overall native-6 manifest is FAILED, not an end-to-end pass: later overworld
visit copied Y64 over actual Y53 terrain. User then offered manual observation;
task native client/private server were stopped, all dimensions saved20:21:56.
Checked-in corrected return targets observed spawn12,66,-138 but is UNRUN.
No all-job, HOT/COLD return, full-pack or M3 claim. Initial fixture/method
failures and their classification are in the work-animation note. No production
invariant was relaxed. All task clients/private JVMs/Xvfb are stopped.

Manual checklist to give user: update client; active farmer tends cells; miner
swings during block labour; woodcutter swings axe; attended production manipulates
with hands (passive furnace waiting can be still); travel/interruption clears
work pose and retains cargo; revisit HOT/COLD shows current active work. Report
resident name, settlement and activity for a failure. No automatic broad campaign.

## Repositories and authority

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926,
HEAD648dc5a3f03c89aedf53581a50783a02d0c65da6 CLEAN.
RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror CLEAN detached same.
PACK /home/rd/proj/minecraft HEAD888ed90d5e3b80b18c278107aa83bb003b541f69,
trackedCLEAN, preexisting.f0v-baseline/ retained.164Packwiz inputs validated;
exact PM and unchanged Visuals published. Client update required for new work
payload/poses; no new dependency. No push. GOV extensive unrelated WIP preserved;
only own human-visual boundary sentence, ledger and new work-animation note are
part of this documentation delivery. Canonical AGENTS/protocol unchanged by task;
sole main/subagents prohibited.

## MAIN runtime — verified current delivery

Existing world guided-lunge-schema325-seed139-20261103, seed139/rootr31 agricultural
flat/schema325 retained. Old139 Java4077333 saved all dimensions20:24:03, then
actual PID+25565/25575 exited before shared installer. No world or WAL edited.
Service far-frontier-v3-live started1793719493, wrapper110312 /actual Java22PID110336,
25565/RCON25575. Source648dc5a3f. SHA256
 a05d4bf7ce3ad41c2fed2c979cbaffa50cb14f393b386236cf029ed71363f939.
SHA512
1ea968b40e5fd1b1de06e74ec21a96205726ce352e8af52e3f0904a34ca40470ef34f2efe0e58634e88adb4033e18f5271d18342f7fa04391eade9ad7718134d.
Preflight/publish/install/fresh-start deployment verify PASS,
/tmp/pm-work-{preflight,publish,install,verify}.log. Current summary instant69828/
revision172741 statusok/green, required/scene/inventory conflicts0, preparing scopes0;
incidentIndexSize3 retained (not diagnosed as new animation incidents).
IMPL/build/work-animation/main-summary.txt. Startup/state proof, not full gameplay
acceptance. MAIN running; no task clients.

Build JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
runtime22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Artifact host127.0.0.1:18092 serves RELEASE/neoforge build/libs; pack8092 serves PACK.
Visuals SHA512
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
Detached build uses -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
RCON helper IMPL/build/forestry-native/query-runtime.mjs reads credentials silently.
Transient service disappears after stop; recreate with systemd-run --user
--unit=far-frontier-v3-live --collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Require native saved
boundary and actual JVM/port exit before replacing/resetting. Worlds disposable;
no unrelated cleanup/history or push authority.

## Prior deliveries — do not redo

2fc2a8a3d guided early long-attack approach and broad170degree sweep;
`docs/frontier-v3-guided-lunge-20261103.md`, GOV88b023f0c.
3fb952955 full native windup before journal/contact after flank;
`docs/frontier-v3-melee-contact-20261103.md`.
f2de641e5 exact player-item contribution cause;
`docs/frontier-v3-plain-item-click-20261103.md`.
344d29b5f cooperative combat ownership;
`docs/frontier-v3-coordinated-combat-20261103.md`.
316eb206b HumanEra/FreshAnimations/EMF/ETF;
`docs/frontier-v3-human-residents-20261103.md`.
9ae557ddf bounded BURST/BACKSTEP;
`docs/frontier-v3-combat-maneuvers-20261103.md`.
3e0eb77fb meal resumption;
`docs/frontier-v3-meal-resumption-20261103.md`.
