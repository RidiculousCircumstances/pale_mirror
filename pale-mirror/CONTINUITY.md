# Active continuity — 2026-11-07

## Assignment and authority
Main alone; no subagents or automatic push. Latest user accepted connecting the
existing crossbow skill-dependent aim duration. Implemented, committed and
same-world deployed. Standing commit/deploy applies. Canonical AGENTS/execution
protocol/semantics remain authoritative. No new goal requested. No world reset,
invented resources or full-suite/HUMAN/M3 claim.

## Repositories and ownership
IMPL `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`, Git root parent,
branch feat/baker-carry-orders-20260926, HEAD41aa4eb771df24cb61238e362dbdadd9d2c7e73d.
Tracked clean; untracked local `.serena/` retained. RELEASE
`/home/rd/proj/pm-r242-economic-release/pale-mirror` clean detached same ref.
PACK `/home/rd/proj/minecraft` HEAD98a6701 tracked clean; unrelated
`.f0v-baseline/` preserved. Ignored hosted artifacts updated, no pack source edit.
GOV `/home/rd/proj/pm-governance/pale-mirror` HEADfbf735d02 before this ledger
update has extensive unrelated WIP; this cut owns ONLY CONTINUITY.md. Preserve
all other governance changes. Source/pack histories remain independent.

## Current crossbow aim repair
Read-only audit found RangedMechanics.aimTicks existed but had no production
caller; native prepare used constant rules.aimTicks8. Native preparation now
uses the existing skill-derived helper, alongside existing agility-based
reload and skill/perception-based dispersion. Current baseline8, variation500:
CROSSBOW levels0/50/100 give12/8/4aim ticks. No change to damage, ranges,
projectile physics, generation, progression, styles, schema345 or rulesetr33.
Source41aa4eb77:2reviewed files,18added/3removed. All3RangedMechanics helpers
now have real production callers; no new system or duplicated formula.

Existing native emission/save/load/facing test is shared between skill100 and
new skill0 variant. Both wait during reload/aim, refuse readiness before their
own skill duration, preserve real arrows/wear during preparation and native
save/load, then emit one actual vanilla arrow, spend one wear and hit through
native collision. Clock checks are deterministic gameplay semantics, not a
wall-clock performance benchmark. Both skill extremes would expose the old
constant8behavior. No graphical human acceptance or live combat recording.

## Verification
IMPL /tmp/pm-crossbow-aim-skill.log:59required local-navigation Minecraft
GameTests PASS, guardrails/style/architecture/debt, JAR/package PASS.
Same unchanged committed identity rebuilt in clean detached RELEASE:
/tmp/pm-crossbow-aim-release.log guardrails/JAR/package PASS. Native59evidence
reused from identical committed source; no redundant second native run.
19guardrails selftests PASS. Governance guardrails
/tmp/pm-crossbow-aim-governance.log PASS.
Bounded evidence under IMPL build/crossbow-aim-skill-20261107 (native log,
pre/post summaries, shutdown). No new domain behavior/schema, so prior
appropriate model/recovery evidence retained; no entire-project-green claim.

## Live deployment
LIVE `/home/rd/far-frontier-server`, SAME world
funded-caravans-seed139-20261107, seed139/rulesetfrontier-v3-ranged-guards-r33,
schema345. RCON stop saved all dimensions15:33:53.138, pending physical
operations0, JVM/25565 exited before installation. Clean-source preflight and
fresh postverify PASS (/tmp/pm-crossbow-aim-{preflight,verify}.log).
Servicefar-frontier-v3-live.service ACTIVE, wrapper2552278,
invocationd6e8c03cf7a1463fb65db8eb4bb8f713, notBefore1794047650.
Core SHA512
e7ce0104cc0e31e677f2551743a7ac9b6dfd46db896f5aaf9bf155e58ad34729224cd12cc346a5813f5dc9f13384fe95ad5fae8bd88c6bbd6e7afccfbc984615.
Visuals unchanged SHA512
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.
Pack170checksums verified/published. Predeploy88581and post-start91004green,
334residents/12settlements,0required/scene/inventoryconflicts; incident index2
already present before restart, not erased or claimed repaired. No new
quarantine/error before deployment. No source/world injection or fast-forward.
JDK21build /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
liveJava22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
No owned client/pilot/Xvfb running. No user client-update requirement asserted
for this server-side timing correction. No automatic push.

## Prior fixes and retained findings
5b04adf84: atomic source-loss checkpoint for only exact obsolete home/refill
approaches, supported pose preserved, correct payment refund; retained-only
stock-access readiness and reversible cancellation before physical preparation,
then alternative service approach/itinerary.62selected JVM and58native PASS,
actual same-world recovery;6sampled groups moved62–66blocks/49.54s including
settlement2donkey/worker. Receipt
 docs/frontier-v3-caravan-refill-access-repair-20261107.md.

6028787c0: funded planned supplier food purchases before departure, ordinary
paid receipt/cancellation/refund/close/recovery (wire345). Full home round-trip
food stays preferred with zero budget; paid personal return supply uses trading
settlement already visited. Shared animal food still fully loaded at home;
off-itinerary third-settlement diversion not implemented. Receipt
 docs/frontier-v3-caravan-supply-funding-20261107.md.
d730ef07e: resource-site delivery null-witness crash and immediate native NoAI
crossbow head/body facing. Prior route/body/deadlock repairs:
 docs/frontier-v3-task-deadlock-repair-20261107.md.

Old schema344world crossbow-guards-fixed-seed139-20261107 remains on disk;
new-world funding deployment was not proof its old starving Mossbrook actors
recovered. At earlier27068analytics67hungry despite bread; immutable29617had
5all MOVE/CONSUME and no hungry-without-meal;60398had21sales/0hungry,64685had
33missions/0hungry. Ordinary observed meal wave, not whole-economy balance.
Small1–3item lots, old-world cash/fuel shortages, occasional/startup>80ms peaks
and long-term balance remain separately unproven.12old meal/depot/adoption JVM
fixture failures reproduced on472bcccb4; old full-native460had18not fully
classified failures. Selected green gates do not erase unrelated baseline debt.
Combat profiles are actor-owned/permanent/generated once/no XP. Crossbow
reload uses agility; dispersion uses mean crossbow skill/perception; aim now
uses crossbow skill. Lead/speed/range/cooldown shared constants. No ranged
technique unlock/style catalogue analogous to melee; not added by this fix.
RCON helper IMPL build/forestry-native/query-runtime.mjs reads credentials;
never print server.properties wholesale. Observatory100.100.78.29:8093/api/view.

## Delivery state
Crossbow aim hook is implemented and deployed in the same world. Scoped ledger update records the delivery; preserve other GOV WIP. Report source/ref,
59native checks and current service health. Do not push or claim graphical M3.
