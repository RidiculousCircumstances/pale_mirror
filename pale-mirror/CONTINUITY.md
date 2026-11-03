# Active continuity — 2026-11-04

## Current delivery and authority

Completed and deployed the player sword-click, HOT route/retarget and existing
security-responder wake repairs. SAME retained world; no reset/fence deletion.
Sole main; no subagents, goal or push authorized. Standing scoped commit+deploy
applies. User latest: sword deposit blocks further edits; jerky residents and
roughly0.5s harm response. Full combat latency/balance is not newly certified.

IMPL `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror` branch
feat/baker-carry-orders-20260926 HEAD389e26a8221608492e8b709a98838ffeef6c0dd5 CLEAN.
RELEASE `/home/rd/proj/pm-r242-economic-release/pale-mirror` CLEAN detached same.
PACK `/home/rd/proj/minecraft` HEAD888ed90d5e3b80b18c278107aa83bb003b541f69,
tracked CLEAN; preexisting untracked.f0v-baseline/ preserved. GOV extensive unrelated
WIP preserved; stage only ledger and this task's new report.

Report: `docs/frontier-v3-player-sword-route-repair-20261104.md`.
Prior baseline combat/body delivery: `docs/frontier-v3-stalls-burst-followup-20261103.md`.
No architecture ownership changed: inventory owns observed contents vssoft forecasts;
common navigation owns routes; security owns threat/response; combat owns mechanics.

## Findings and implemented changes

ac39b712a: player observed ResourceDeposited checks actual inventory.slotVacant,
not prospective production-capacity admission. Exact failed transaction
6071e128-ef48-43ea-8c7a-2ebf1cc39745/operation046ce9b4-1537-4a9e-8fcd-b152f005ac3b
had physical iron_sword in container:1-depot slot2 vs canonicalempty/softreservation.
Original pending appears to have settled OLDbuild after reservation changed;
DO NOT claim new code freed that unchanged pending operation. Exact sword
item:player-contribution-d77368df-1e24-344c-82fd-a7340df5bfa8 retained, held by
resident:1-16 NoraBell on inspection. Existing exact/native operation fences intact.
Same commit: retarget only WORK_CELL, cheap eligible candidates instead of world
state drafts/full nativeA* per candidate; bounded rejectedpass preserved. Shared
RouteNavigation retains accepted advisory leg across same semanticgoal when cache
hint disappears; authority/order/layout/livegeometry checks remain.

ba64cb216/664f62567: ordinary consecutive sword scenario; pilot withdrawal now
requires actual quantitydelta +emptied source (old driver could false-pass second).
Retired obsolete orbit-only test; removed invalid managed-cue calls on unmodeled
fixture bodies, retaining actual movement/gravity/farmland tests and production
body authority. ff16eb968 adds native-fact capture/commit correlation logs.
389e26a82: confirmed Incident also wakes already assigned responders to same threat,
as Seen/ContactEnded already did; allocation unchanged must not leave guard asleep
until another sensor pass. No new role/actuator, no attack-timing/durability bypass.

## Verification

`/tmp/pm-click-route-final2.log` PASS guardrails40domain/9adapter/44native localnav
+JAR/package. `/tmp/pm-click-pilot-check.log` PASS guardrails/client/testcompile/JAR.
`/tmp/pm-harm-latency-check.log` focused confirmation/nativefact+guardrails/package PASS;
`/tmp/pm-harm-wake-check.log`37security/localreaction/codec+guardrails/package PASS.
Detached builds and root preflight/publish/install/runtimeverify passed.

Same affected container UI run3eea5e1a-8d27-40dd-ae03-04cc67338b45 on664f62567:
two consecutive ordinary sword deposits, two withdrawals, no transfer sleeps,
exact socket/canonical assertions PASS; both frames personally inspected.
Manifest IMPL/build/sword-click-stall/client-manifest.json; frames in
build/frontier-v3-scenarios/live_sword_consecutive_edits-3eea5e1a-*.
Pilot retains two tracked diamond swords hotbar7/8, selected0empty; DO NOT /clear.
Controlled45s HOTrepeat d4f7ee01-e5b3-4729-8bf2-258abcffe771 with JFR alreadyrecording:
recent64 avg4.81ms/max8.74ms; no >80ms slowtick00:00:43–00:01:29.34JFR one-second
averages mean2.741/max8.231ms (NOTindividualmax). Firstprofileractivation had
277.678/101.57ms andmanydeopts; notproven solelyprofiler cost. Retain that caveat.

Exploratory guard probes: firsthit duringPREPARED notsteady; warmattempt target
moved17blocks/outofrange(methodfailure). Readyprobe waitsHOT/RUNNING thenvisits
exactactor. Instrumented c5c4b7c6-1f51-4ecf-ba7d-c919450bb245 capture→commit60ms;
laterreaction delay is not that journal delivery. Final389e26a82 run
df3bfc9a-dbd7-4d06-ae14-55bc75c85a6f completed19actions/twonativehits, capture→commit
109/199ms. FirstBACKSTEP selected8simticks aftercapture, launched16ticks after;
secondhit retainsguard/target, cooldown stillapplies. Final ENGAGED/SELF_DEFENCE.
This DOES NOT prove every reaction below0.5s or fullcombatbalance. No arbitrary
cooldown/windup retuning performed. Manifest repeated-harm-final-manifest.json;
/tmp/pm-harm-wake-client.log. All taskclients/private displays exited.

## MAIN runtime

`/home/rd/far-frontier-server`, same world
combat-followup-schema327-seed139-20261103, seed139/rootr31 agricultural-flat/schema327.
Source389e26a82; actual listening Java22PID1027868 (service wrapper1027844), start1793733644,
far-frontier-v3-live.service. `/tmp/pm-harm-wake-{preflight,publish,stop,install,verify}.log`
PASS; previous native saved all dimensions/ports exited before install.
SHA2565aecaedd9096a8b15247d5c2387acdaaeb4117da1584ac672ac61fd869ffb0a4;
SHA512254539d103991b5c9b3e0c668e807e3dd1f77d1be7188a17daed047d8ef14bd1fe92282f347065b62981bbad6f86c86850a95a731ab0b0429feea40a6699b1cc.
Final summary instant63740:12settlements/333residents, required/scene/inventory
conflicts0/incident0, pendingOrdinaryTicks0/fastForward0. Recent64avg3.61ms/max6.61ms;
newJVM max58.73ms/1385samples. No new quarantine/fatal. Native fact logs retained
for future diagnosis; sparse per-event capture/commit, no per-tick world scan.

## Operations

BuildJDK21 `/home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`;
runtime22 `/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java`.
RuntimeJRE has no jcmd; JDK21jcmd canattach owned22JVM. Artifact host127.0.0.1:18092
serves RELEASE/pale-mirror-neoforge/build/libs; pack8092 serves PACK.
Visuals unchangedSHA5122fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
Detachedbuilds supply -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
RCONhelper IMPL/build/forestry-native/query-runtime.mjs readscredentials silently;
query sequentially `pale_mirror v3 inspect ...`. SomeexternalplayersJSONcanprecede
reply; select expected kind, notfirstJSON. Fullpack pilot profilepack,20FPS,on-demand,
packinput build/motion-pose-repair/pack-input; noPREPARED_BUILD_IDENTITYoverride.
Commands setup-only; visit allowedactions. WaitexactHOT/RUNNING beforesteadyhit,
thenvisitactualactoragainifmoving. Diagnostics never conferphysicalauthority.
Preserve unrelatedservices/history/WIP; userworlddisposable butnoresetneededhere.
