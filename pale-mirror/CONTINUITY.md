# Active continuity — 2026-11-08

## Current task and remaining problem

User reported slow combat, supplied two armed captures and repeated a fight after
the repair. The measured 9–31 second combat-stage freezes are repaired/deployed;
the rematch reveals a separate OPEN strike-window/cadence defect. Do not claim
combat fully accepted. Main alone; subagents prohibited; no push. No active goal
was created for this combat investigation. Prior leisure goal is complete.

Current receipt: `docs/verification/northwatch-combat-latency-20261108.json`.
Baseline captures60b15329-f6bc-453e-92e8-effc259b2fb3 (1996events) and
4911edc4-27a3-4286-bdfe-96ef6f6500a5 (613events), complete/no loss,
showed combat-melee stages9.8099/14.3998/31.4132s and9–14s gaps across2native
ticks. Exact live Thread.print caught CombatManeuverProcess -> destination ->
transientDestinations -> storage geometry/cache -> egressRegion/surface overlay.
Predicate computed full waiting/egress before actual demand and for columns
that cannot be immediate exits.

Production fixca5eadc85270bd97b8077f86faf50672555dc23e retains claims/occupied
exclusions and checks complete cardinal-column negative proof +actual demand
before unchanged geometry. No new navigator, owner, thread or wider damage reach.
PASS71 focused (service9/cache3/security35/routine7/seated7/architecture10),
guardrails/JAR/package PASS, `/tmp/pm-combat-transient-gate.log`.
Clean detached build PASS `/tmp/pm-combat-transient-detached.log`; JAR differences
only runtime identity metadata. Classified baseline debt remains; no blanket green.
Controlled component probe: identical copied snapshot revision155118/instant40023,
49same candidate columns/identical49-bit decisions, old1.615s -> new0.141s.
Single-input component comparison, not overall TPS multiplier. Evidence retained
in IMPL `build/combat-delay-20261108/`; receipt pins snapshot SHA256.

Actual user rematch4639618a-3429-4dbd-9d9d-6004ef191229 on deployed ca5/current
JVM2741914:2325events complete/no loss,474frames,63decisions/63strike events.
Largest frame gap0.716081s across2native ticks; no9–31s freeze in this record.
Different opponent/style/evolving state, not a controlled whole-server comparison.
Of6settled strikes:2legitimate OUT_OF_REACH,4WINDOW_CLOSED; two expired-window
misses were stationary point-blank0.362641blocks, aim error~0.000002degrees.
OVERHEAD attempt42970: first windup worldTick43035, next admission43051 (+16).
QUICK attempt43074: first windup43114, next admission43123 (+9).
Contact window elapsed entirely between checks. No repair for this second defect
yet. Raw worldTick=level gameTime; serverTick=native server tickCount (constant
offset), simulationTick=canonical; no proved clock bug.

Source: pending strikes run before the sensing cadence gate in CombatExecutor,
but only when shared cooperative physical registry reaches combat-melee.
Registry admission needs causal headroom/no predecessor; retained ordered cursor
shares per-tick allowance. Journal mutation follows READY immediately, not a
second async gap. Finite expiry correctly yields a miss; do not widen windows,
grant late hits or bypass durability/incarnation checks. Next unresolved work:
discriminate and repair shared physical cadence/admission.
Rematch JFR17s window10:02:08–10:02:25UTC:336HOST_TICK events,25combat-melee
stage events totaling84.64ms/max29.28ms; ambient actors total3.918s/max242.6ms;
cold-harvest scheduled planning total8.664s/max368.4ms. Nested stages are not
exclusive CPU totals. Configured stack depth remained64. Exact evidence:
`fixed-user-rematch.jsonl`, `fixed-rematch-analysis.json`,
`fixed-user-rematch-2741914.jfr`, `fixed-rematch-stages.txt`.

## Runtime and repositories

IMPL `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`, Git root parent,
branch feat/baker-carry-orders-20260926, HEAD16d439326 (test-only fixture followup).
Tracked clean; unrelated `.serena/` retained. PACK `/home/rd/proj/minecraft`
HEAD909c26c tracked clean; unrelated `.f0v-baseline/` retained. GOV parent repo
has extensive unrelated WIP; stage only own ledger/receipt, preserve the rest.
Canonical instructions in this directory; local entry files are redirects.

LIVE `/home/rd/far-frontier-server`, **user** unit far-frontier-v3-live.service,
active/running, wrapper2741890/java2741914, invocation
efe8d477b2dc421cad18090d23b30893, notBefore1794131094.
Same world northwatch-leisure-seed139-20261108, seed139/r37/schema351/network10.
Deployed production ca5eadc85270bd97b8077f86faf50672555dc23e, clean detached
`/home/rd/proj/pm-combat-latency-release-20261108/pale-mirror`.
CoreSHA512587dd7bf91bab65841917077ab8df0fba6b549013ac530c72e523add8b805283199915a9f40e6092d9566610d39ba657561598d05deacc93287d6bbd3377995b.
Visuals unchanged. Java22 `/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java`.
Root controlled preflight/normal RCON stop/all dimensions saved/old JVM2689444
and ports exited/install SAME world/postverify PASS; logs
`/tmp/pm-combat-transient-{preflight,install,postverify}.log`.
Final summary revision290335/instant57550: scene0/inventory0, requiredConflicts1/
blocked is retained historical14991. Why at291431 confirms first=last14991,
occurrences1. Never remove that incident to manufacture green. HTTP8092/8094/
observatory preserved; LIVE25565/25575 listening.

All task pilot clients/private Xvfb closed. Isolated server25599 saved and stopped;
runner2794289 cancelled SIGINT/exit130 before evidence to prioritize user rematch.
No task client/server/Xvfb remains;25599/8766/18766 closed. Exact live2741914
JFR.check reports no active recordings.
Native acceptance NOT achieved: live pilot1 setup fullreport response_limit;
pilot2 bounded fixture/Eli rendezvous failed step9 before evidence. Earlier Eli
body-availability suspicion unconfirmed; human subsequently fought Eli in this
runtime. Disposable seed41 maneuver scenario operator-cancelled during zero-player
bootstrap, neither pass nor product failure. Human diagnostic rematch is not
declarative acceptance. Receipts retain these limits.

## Earlier completed repairs and reusable acceptance

Server protective stop14:23 was routine/healing fence contradiction, not OOM.
Fix8a351e750: RoutineProcess and capability wait on shared pending native fence.
37focused checks + exact saved snapshot/WAL14990 old/new discriminator PASS;
original STRETCH resident1-13 seq4 and later seq5 CLOSED, healingPendingfalse.
Receipt `docs/verification/northwatch-routine-healing-recovery-20261108.json`,
GOVc5733d5f8 includes one owned architecture annotation. Historical14991 retained
once. Recorder capacity:12older CLOSED captures archived without deletion/hash
manifest under LIVE/debug/pale-mirror/combat/archive/20261108-recording-capacity/.

Prior living/maxlevel5/clean kitchen/routines/dining completed:
`docs/verification/northwatch-living-20261108.json` (61f2bcd03).
Seated HumanEra fix639633fbd preserves authored riding joints/local6pixel hip;
3meals/seated restart/standing legs accepted:
`docs/verification/northwatch-dining-pose-20261108.json` (d68e60e93).
Station-facing actual work160ticks, correct headings/21bread/closure/restart:
`docs/verification/northwatch-station-facing-20261108.json` (0bddff9cf).
Leisure a26414beb: typed bench seats/schema351/r37, isolated kitchen/hall plans,
bounded optional routines; seated positive+restart and occupied-bench removal
negative native acceptance, fresh MCP scans/fullpack visual preflight accepted.
Receipt `docs/verification/northwatch-leisure-20261108.json` owns those claims;
IMPL `build/northwatch-leisure-20261108/final-positive/pilot.json` and
`final-negative2/pilot.json`. Preserve known26JVM/4Node unrelated baseline debt;
prior native479 coverage is not a fresh whole-suite pass for this combat change.
Earlier ledger history: `docs/archive/continuity-2026-11-08-before-leisure-seats.md`.
