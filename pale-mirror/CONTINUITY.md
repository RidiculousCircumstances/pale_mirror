# Active continuity — 2026-11-02

## Current assignment — reusable HOT melee delivered
User approved implementing/integrating the common combat module through guards.
Main alone; no subagents/Terra/API goal/push. Standing commit and verified MAIN
deploy fulfilled. No COLD combat, civilian combat, ranged attacks or convoy policy.
No active implementation remains for this cut. Next user may inspect guards or
request another slice; do not infer an additional goal. Plan in source
`docs/frontier-v3-combat-module-plan.md`; detailed evidence/classification in
`docs/frontier-v3-security-hot-findings-20261102.md` schema319 section.

IMPL /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror, branch
feat/baker-carry-orders-20260926 CLEAN799435d7db9cb78cd5b73941e2b65b120f87d37d.
Core705f58c71; persisted-policy/native-rendezvous03794462c; connected station
restart fix799435d7d. RELEASE /home/rd/proj/pm-r242-economic-release/pale-mirror
CLEAN detached799. PACK /home/rd/proj/minecraft trackedcleanf72713382957;
preserve its existing untracked .f0v-baseline/. No client update/publication/push.
Governance has extensive unrelated WIP; never bulk stage or discard it.

## Architecture
Common model.combat owns CombatState, exact pending MeleeStrike/readiness, weapon
facts and bounded rejected melee stations. Read-only CombatOrder is permission
from a closed execution-kind CombatCapability registry, not a second target/body
controller. GuardCombatCapability owns politics, response references and
patrol/search/return. Common core imports no security policy. Tactical station
ranking/reselection uses common geometry/navigation, weapon reach, unit dimensions
and peer stations; completed negative paths cannot hide later candidates behind
the outstanding-query budget. Retention consumes persisted hashed MeleeRules.

Separate registered combat process/codec and native executor preserve one UAE,
body controller, inventory authority and exact native operation journal. Actual
HOT weapon, LOS/reach, readiness, target incarnation and wear gate every hit.
Registry weapon definitions permit COLD approaches, never COLD hits. Passive
CombatStatus performs no route/forecast work. Schema319, fresh-world-only policy.

Connected recovery fix: scene suspension could call general machine.replace while
pending fuel/recipe pinned its revision, causing startup quarantine. The common
station boundary now permits only validated settlement of the previous labour
clock and withdrawal of attendance while preserving physical effect revision,
pre/postimage and custody. Resume/new work remains forbidden under pending effect.
Same failed world restarted successfully without reset. No format/default change.
Prior local-reaction, authority, station geometry and contact-ending contracts
remain; historical field CONFLICT and retired-body warnings were not silently
claimed repaired. Sources/evidence retained in the HOT findings.

## Verification
Clean705 full guardrails/check/NeoForge build/packaged gate PASS8m34s:
Frontier2253/NeoForge895/domain324/Visuals79, no failures/errors/skips.
Focused core72/adapter48, registry4, Node56 pass. GameTest414/416; same2 inactive
legacy assault/patrol failures reproduce on clean baseline de34 and remain debt,
not a green416 claim. Final037 delta focused/policy/package PASS25s; Neo895 reran
PASS3m8s. Final799 station/recovery29+guardrails/package PASS47s, clean detached
package/verify PASS12s. Reuse unchanged gates; no confidence rerun.
Receipts IMPL/build/combat-module/{focused,executor-registry,integration,gametest,
station-recovery-focused,retained-carrier,verified}.json; /tmp/pm-combat-*.log.

Initial isolated319 receipt native.json FAILED player-entry setup: routeSurface
expected feet65 but real player landed64; no offence/hit occurred. Do not relabel
it passed or claim product geometry fixed. Checked-in scenario uses ordinary
player-only high entry plus setup-only rendezvous with received named guard.
Retained seed41 worldv3-disposable_security_response_restart-6369077c, finite forge
inputs/no sword or appointment injection. Existing restartSegments/run-scenario,
frozen799, one20FPS private-Xvfb client at a time. Before5e056e4c-f179-4837-bc38-
10ad4c62d8f1 PASS8actions: real fist -> SELF_DEFENCE -> native player100->94 ->
safe exit -> bounded search/contact0/responders0/RETURN/no pending strike.
Normal stop saved all dimensions14:48:28 and closed JVM/ports. Same-world restart
20c2a565-96a0-461a-b2e5-cfcb414146cf, after11722a98-b415-498f-a3f9-9827cbd7b837
PASS4actions: remembered hostility, native100->94 without new attack, safe exit,
contact0/responders0/no pending hit. Final private stop saved all dimensions14:51:55.
Frame reviewed: actual armed body, dark partial terrain. No full-pack graphical,
HUMAN/M3, multiplayer/whole-cohort tactics, industrial M2 or scale/global80ms claim.
Private clients/Xvfb/25583/4 and all other private servers are STOPPED.

## MAIN delivery
RUNNING /home/rd/far-frontier-server, far-frontier-v3-live.service.
Actual Java22PID1667411, wrapper1667387;25565/RCON25575. Source799 above; SHA512
55ecdae4358051aacf327a4123aee93340de981b93db3dad188da5647ac0ee87b6581293958b2dc02b60cb2167f2d7a01ef9d9f679a498652d2158e4ebea0e37.
Fresh combat-module-schema319-seed132-20261102, actual persisted seed132,
frontier-v3-simulation-terrain-r31,12town/364residents. Old318 world preserved.
Normal old stop saved all dimensions and closed JVM/ports; required datapack
installed before genesis. Root preflight/shared pinned installer/deploy-verify PASS.
Start epoch1793613160; ready14:52:52/runtime14:52:53. Snapshot1400 proves identity;
summaries1319->1599 green retained-conflict scope/required0, ordinary pending0,
fastforward0. Exact binding IMPL/build/combat-module/verified.json. First town at1329
armed guards0: ordinary manufacture/collection/appointment, no injected roles/items.
Address100.100.78.29:25565 / LAN192.168.0.103. First-town support(-1486,64,-1360),
entry above it then land naturally; diagnostic support has a known entry-height
mismatch in the seed41 carrier, so do not infer exact physical feet from it.

## Operations
RCON IMPL/build/forestry-native/query-runtime.mjs reads password silently; never
print server.properties. Valid pale_mirror v3 inspect summary/performance or
inspect settlement settlement:1. advance N queues; inspect terminal result.
Normal RCON stop and native saved-dimensions/process/port closure precede changes.
Transient unit disappears on stop; recreate with systemd-run --user
--unit=far-frontier-v3-live --collect --working-directory=/home/rd/far-frontier-server
/home/rd/far-frontier-server/scripts/run-server-java22.sh. Root preflight and
frontier-v3-deploy-verify required. Detached build property
-PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt.
JDK21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2; runtime Java22
/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java. Local artifact host
127.0.0.1:18092 serves RELEASE/build/libs. No source edits/build during native runs.
