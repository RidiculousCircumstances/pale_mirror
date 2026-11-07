# Active continuity — 2026-11-07

## Assignment and authority
Main alone; no subagents or automatic push. User accepted crossbow repertoire
progression and ranged tendencies. Implemented, committed and deployed below.
Skill opens techniques; permanent attributes govern execution; existing personal
style governs choice. No XP/learning or temporary hunger/injury combat penalties.
Standing commit/deploy applies. No new goal requested. Canonical AGENTS,
execution protocol and HOT/COLD semantics remain authoritative. No whole-suite,
graphical player-combat, balance, HUMAN_CANDIDATE or M3 claim.

## Repositories and ownership
IMPL `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`, Git root parent,
branch feat/baker-carry-orders-20260926, source
6717178bdbb0e690945cf6ad77a95ac2a4068b05. Tracked clean; local untracked
`.serena/` retained. RELEASE `/home/rd/proj/pm-r242-economic-release/pale-mirror`
clean detached same commit. PACK `/home/rd/proj/minecraft` HEAD98a6701 tracked
clean; unrelated `.f0v-baseline/` retained. Ignored hosted artifacts published;
no tracked pack edit. Preserve independent histories.
GOV `/home/rd/proj/pm-governance/pale-mirror` has extensive unrelated WIP.
This cut owns ONLY CONTINUITY.md, its new progression receipt and the new
`frontier-v3-ranged-techniques` architecture invariant. Stage that invariant
against HEAD separately, preserving all other architecture/doc/skill edits.

## Ranged progression delivery
Receipt: docs/frontier-v3-ranged-progression-20261107.md.
Source6717178bd:24files,321add/58remove. Wire346/ruleset
frontier-v3-ranged-guards-r34 deliberately require a new disposable world.
Techniques: ordinary0, hurried15, precise30, after-maneuver45, held60,
countershot75, repositioning sequence90. Existing generated skill/permanent
profile/style survive role changes. Supporting is a fourth explicit personal
style, alongside Pressing/Cautious/Mobile; preferences rank legal choices,
preferred distance and support stations, never grant a skill or damage.

Prepared intent retains exact technique/profile/origin and existing
body/execution/weapon/target-incarnation/ammo binding. Only confirmed native
launch records one bounded encounter-scoped followup. Cancellation creates no
credit; common combat retirement removes memory. Optional relocation expires
after80simulation ticks; a valid selected destination survives reviews. All
movement reuses known geometry, spatial claims and UAE/navigation. Walking
repositioning does not grant new EVASION actions. No COLD combat.

Every technique reloads ordinarily and emits one real vanilla arrow with one
weapon wear through the existing durable native operation/receipt. Hurried
trades precision for faster aim; precise does the inverse. Held/counter opening
waits have finite perception-based reaction and expiry; absent opening falls
back to an ordinary release opportunity, persistent cover closes without a
launch/debit/wear. High-tier sequence withdraws physically and uses an ordinary
second shot with another full reload. No homing, virtual charged arrow,
projectile recreation or guaranteed hit. Combat trace records pending/last
technique, attempt and timing; read-only profile inspection lists learned moves.

## Verification and limits
IMPL /tmp/pm-ranged-progression-gate.log PASS:31JVM checks (9ranged guard,
3manufacture,3tactics,6existing style,10architecture),64required native
local-navigation GameTests, guardrails/style/architecture/debt, JAR/package.
Native cases cover ordinary skill0/100, hurried/precise aim, finite held wait,
new-cover positive refusal, counter recovery/reaction, actual ammo/wear,
native save/load and vanilla projectile collision. Domain cases cover gates,
choice/style differences, actual retreat/second-shot selection, encounter
scoping/expiry, cancellation, snapshot and duplicate-receipt rejection.
A read-only learned-move JSON list was added afterward; unchanged native/model
evidence retained. /tmp/pm-ranged-progression-final.log PASS guardrails/JAR/package.
Committed identity rebuilt in clean RELEASE: /tmp/pm-ranged-progression-release.log
PASS guardrails/JAR/package;19guardrails selftests. Native64 not redundantly rerun
for that independent projection edit. GOV /tmp/pm-ranged-progression-governance.log guardrails PASS.
Bounded build/log/pre/post evidence: IMPL build/ranged-progression-20261107.
No task-owned client/pilot/Xvfb launched; actual player combat and readability
of each technique still require playtesting. No claim that all old tests pass.

## Live deployment
LIVE `/home/rd/far-frontier-server`; servicefar-frontier-v3-live.service ACTIVE,
wrapperPID2685238, invocationa7184166b1204b2888b63f5bddea178e,
notBefore1794049502. NEW worldranged-techniques-seed139-20261107, seed139,
rulesetr34/schema346. Old funded-caravans-seed139-20261107 preserved on disk.
RCON stop saved all dimensions16:04:28.661; JVM/service and25565 exited before
new exact world/config/artifact installation. Fresh datapack copied before
startup. Clean-source preflight and postverify PASS:
/tmp/pm-ranged-progression-{preflight,verify}.log. Pack170checksums published.
Core SHA512
d59a88ee79ee6448b1c3fdd4f7211e6653b80fd8ad075b255cbfa21e4621e1b04f30e6c1737d0cfefa314262d28954b6c3f43ef1c4efa1d7057859911ab9c5ca.
Visuals unchanged SHA512
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
New-world352and2029summaries green:319residents/12settlements,
0required/scene/inventory conflicts, incidentIndex0; no new ERROR/quarantine.
This is startup health, not prolonged economy/armed-guard acceptance. Prior
world predeploy123935green,336residents, incidentIndex2 retained in evidence.
JDK21build `/home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`;
liveJava22 `/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java`.

## Prior repairs and retained open findings
41aa4eb77 wired existing skill-dependent aim into native preparation: base8,
variation500 gives skill0/50/100 aim12/8/4ticks; ordinary behavior preserved.
5b04adf84 atomic obsolete home/refill approach retirement with actual as-of
checkpoint, funded refunds, retained stock-access readiness and reversible
cancellation before physical preparation. Receipt
 docs/frontier-v3-caravan-refill-access-repair-20261107.md.
6028787c0 funded planned supplier food purchases before departure with actual
money holds/payment/refunds/retirement/recovery, schema345. Full home roundtrip
food preferred/zero budget; paid personal return supply uses already-visited
trading settlement. Animal food still full-home; off-itinerary third-settlement
diversion not implemented. Receipt
 docs/frontier-v3-caravan-supply-funding-20261107.md.
d730ef07e resource-site delivery null-witness crash and immediate NoAI crossbow
head/body facing. Older recovery receipt
 docs/frontier-v3-task-deadlock-repair-20261107.md.
Old schema344worldcrossbow-guards-fixed-seed139-20261107 also preserved; later
new-world health does not prove recovery of its old starving actors. Prior
funded-world60398had21sales/0hungry,64685had33missions/0hungry. Small1–3item
lots, long-term balance and occasional/startup>80ms peaks remain separately
unproven.12old meal/depot/adoption JVM fixture failures reproduced on472bcccb4;
old full-native460had18not fully classified failures. Selected green gates do
not erase that baseline debt.
RCON helper IMPL build/forestry-native/query-runtime.mjs accepts properties and
ONE command; reads credentials without printing. Never print server.properties
wholesale. Observatory100.100.78.29:8093/api/view. No automatic push.
