# Continuity Ledger

Updated2026-10-07. Main alone; Terra stopped, subagents prohibited.
Canonical governance is here; sole workflow docs/engineering-agent-protocol.md.
Prior chronology: docs/archive/continuity-2026-10-07-before-addressed-planning.md.

## Current assignment and source

User authorized completing points4–5 on top of1–3, committing and deploying.
Implemented/committed/deployed; final integration gates passed. No owned test task
or client remains. Full-capacity historical-world startup admission is a recorded
separate open issue; this deployment uses the user-approved fresh world.
No push requested. User explicitly allows a fresh world; preserve R36 incident.
Main implements receipt-pressure admission and explicit caravan pause diagnostics.
Implementation /home/rd/proj/pm-f06r3-facility-lane-recovery, Gradle root pale-mirror/.
Branch feat/baker-carry-orders-20260926, HEAD45901af1 (recovery follow-up to83b41446).
The204 preexisting WIP entries are the prior delivered expedition cut retained
in the clean R36 snapshot; preserve and include dependency-closed source on commit.
SA audit paused; ARC-001 adoption separate.

## Preserved R36 incident

R36 QUARANTINED2026-10-07 18:23:08+05, revision24205/tick25610.
Minecraft service alive, PM runtime unavailable and all PM progress frozen.
Read-only snapshot24108+97 WAL:4096 active receipts,2834 group-navigation-ready
(~69%),829 actor-body-inspected. Global planner completion/invalidation broadcasts
to all loading/unloading/refill groups, including unrelated calculations.
Northwatch50 bread/18 hungry:17 meal MOVE,1 CLEAR_ACCESS.
Six caravans: five LOADING/READY,one OUTBOUND/TRAVELLING.
Earlier individual pauses not conclusively correlated; cohesion can intentionally
hold an advancing member. No measured steady TPS after quarantine; two previous
overruns ~2s, no observed host-wide CPU/RAM exhaustion.
Evidence implementation pale-mirror/build/expedition-r36-incident.json.

## Current deployment / prior recovery

Clean detached source45901af13054b4d77f9670c8ca64a077e813578c at
/home/rd/proj/pm-planning-r38-release-20261007; focused29 kernel checks,
guardrails and packaged-JAR gate PASS17s. Published/installed SHA512
b2517b1329db366ef95cb30121d266bf5d4ceb3cbab4393cd1802953894db390393f43826e121fcb05e926129e7ada6a8d24f34fe22c7aeaf362ffdba8666505.
New world frontier-v3-planning-r39-20261007 selected, seed20260918065;
service invocationf93e4f4fbdc04c1abee1ce862f7a2683, start1791383388.
Fresh-world preflight/post-start PASS; wrapper3326007 listens25565.
Pilot hold released, request1. Live summary3090/revision1557 green; required and
inventory conflicts0. Receipts4094 available, physicalAdmissionHeld=false.
Clearwater created five LOADING missions by1985; the new member view at882 explicitly
reports LOAD_APPROACH_OR_TAKE, real COLD member pose, exact next movement1508.
Evidence implementation build/planning-r39-deployment.json. No test client.
No full live caravan trip, TPS speedup or HUMAN/M3 claim.
Same R36 world startup after the recovery fix still fails: ambient-restart-unknown
is mandatory but all4096 active receipts are occupied. General full-capacity
startup maintenance admission remains OPEN; do not claim old incident recovery.
Do not bypass this by rewriting WAL or increasing limits. Both failed starts
preserved R36 files. Fresh-world deployment accepted by user; old world retained.

R37 source83b41446 was installed (SHA512a30ec82b9cf4af5782436b456af0ebf87f85b84653d8a75c7c834fe5ef8227e1ff5fec32397f54e9d6a9a5da2ef9deae285c2c2c937feeeac9c2c33fc064adc1).
Invocation573e2af7f9434747ab29677fd8a5d27d failed at startup19:26:04;
that failed invocation is inactive. Exact cause: recovery checks snapshot+WAL receipt union
against4096 before applying expiry at the replayed instant. The active R36 count
was4096, not proof that every historical snapshot receipt still belongs to it.
Recovery follow-up45901af1 applies the shared live cutoff after verified replay,
then checks active duplicates/capacity; no WAL edit, resource/time repair or limit bump.
Kernel29 + guardrails PASS14s, including full snapshot + post-expiry WAL receipt.
R38 focused recovery/package gates finished. R37 full guardrails/check/GameTest/
build/package gate PASS12m2s, including all360 required GameTests; core and
NeoForge check PASS. Unchanged integration reused for the focused R38 kernel fix.
Incident snapshot/WAL and R36 JAR copied to build/r36-pre-r37-recovery/.
R36 retention problem cannot be healed by silently skipping restart safety.

Clean detached source f1a86c0474bd06036c89b15d0e8fbaa926b0c9b8 at
/home/rd/proj/pm-expedition-r36-release-20261007, private release ref, no remote push.
Installed JAR SHA2562cd0233d86086a8b90558f92c167f9fc5838a8c738fe6981e9028bc70d54e744.
Runtime /home/rd/far-frontier-server; far-frontier-v3-live.service.
Invocationdd2c6af6ab174984aa40f0855be5a3d4, wrapper3153157/Java3153181.
World frontier-v3-expedition-r36-20261007, seed20260918065,
trade-playtest-r3/rules17/state262/envelope106; ports25565/25575.
Deployment preflight/package/installer/post-start passed, but the later quarantine
supersedes startup health. Receipt implementation build/expedition-r36-deployment.json.
Old frontier-v3-boundaries-r34-20261006 preserved; config backup
.far-frontier-installer-cache/expedition-r36-predeploy. No owned client.
Clearwater farm140/64/-12, visit140/68/-2; exact finite pack actor:pack/settlement-7.

## Implemented points1–3 and verification

Exact volatile calculation addresses and completion/invalidation signals implemented.
Derived wait index binds actual pending queries to producer-declared successor
ScheduledAction. Repeated results coalesce per exact continuation. A registered
kernel-bound planning-ready command only reschedules that retained generation;
the original owner still decides movement, stock and authority. Old group-wide
readiness command/broadcast policy removed. No volatile domain-state owner.
Recovery reconstructs pending query dependencies; proposed world outcomes discarded.
Final source verification: build/planning-addressed-verified.log has37/38 core
checks passing; sole failure was the added alternative fixture using EXACT_STATION
for two goals. Fixture corrected to ANY_DECLARED_STATION; all4 index tests PASS9s
in build/planning-addressed-alternatives.log, unchanged37 checks reused.
NeoForge restart/rebind PASS, including no clock/resource/job changes during
recovery; real provisioning then starts collective travel before6000-tick review.
Guardrails/architecture/debt/style PASS. Exact bound/unbound/stale command checks,
NO_PATH/invalidation, cancellation/replacement/due generations, diagnostics-not-
subscribing,5000 repeated signals -> one continuation, and usable alternative
without spurious wait are covered. Existing broader transport tests passed in
build/planning-addressed-second.log; no full confidence matrix/native trip rerun.
Only subscribed unresolved route results register waits; discarded alternatives
do not. These early source checks preceded the now-committed/deployed cut.
No throughput/HUMAN acceptance claim.

## Points4–5 implemented and deployed

Read-only kernel retention capacity mirrors exact receipt expiry. Optional wakes
are capped at8 per invocation and cannot consume the final quarter of receipt/
transaction capacity. Ready waits persist until accepted; completion-order fairness.
Capacity refusal defers only optional work; unexpected identity/invariant rejection
still fails visibly. A physical turn/stage waits for the quarter-capacity causal
headroom; ordinary canonical time continues so recovered receipt pressure expires.
This is not a reservation/atomicity claim for arbitrary physical executor batches.
One typed MovementPermission supplies both execution and explanation, including
exact lagging peer, formation stretch, refill and mission stop. Group diagnostics
use observed HOT/canonical COLD positions and show retained route evidence, loading/
refill service waits; performance exposes wait counts and retention pressure.
Focused regression found a tick-zero wake contradiction; notifications now reschedule
for the next canonical tick and already-next-tick owners consume no wake receipt.
Focused/kernel/restart/diagnostic checks PASS; focused delivery34s, connected
supply/cohesion cut3m19s. Core engine28/index5/navigation8/group4 and adapter
planning2/diagnostic34 are green; unchanged group checks reused after next-tick
notification until release integration. Source committed83b41446 (231 files,
including previously delivered expedition WIP); worktree clean, no push.
Clean detached source /home/rd/proj/pm-planning-r37-release-20261007.
guardrails/check/GameTest/build/package gate PASS12m2s there;
log implementation pale-mirror/build/planning-r37-release-gates.log.
R36 restart attempts disclosed the preserved startup issue; approved R39 is live.

## Retained evidence and boundaries

Prior nine-point expedition provisioning goal COMPLETE; functional native18 PASS:
actual goods/food loading, chest donkey movement and attached storage, portable
nutrition, same-mission graceful restart, COLD delivery/independent acceptance16,
return, mission retirement, finite animal/budget release and HOT stock confirmation.
Evidence build/frontier-v3-scenarios/expedition-native-18.json; input digest
37deec486f157de21595735ed3ade53c65810de42d8689f4828aad24dc1dc4a0.
Not full-live/HUMAN/M3 (terminal camera sky-only). Do not replay trip for photograph.
Use source-first coherent repairs and relevant focused checks; no unchanged matrix
or native trip for reassurance. Class dirs must not change under active test JVMs.
Test worlds disposable under scoped implementation/reset authority, not human saves.
Diagnosis-only requests do not authorize mutation. Clients20FPS/stopped between checks.

Outer /home/rd/proj/minecraft HEADd0fca592eadb884e6de0669c1b858aea86bbe57b,
only preexisting .f0v-baseline/. Original ignored nested pale-mirror23 unrelated WIP
entries untouched. Governance mixed WIP preserved. Inspect/stage each repo separately,
preserve both histories; no repository migration.
