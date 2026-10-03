# Archived Continuity Ledger

2026-09-28 current-source integration checkpoint: the active main-alone
resident-life/resource goal remains in progress; the older paused SA tool goal
cannot be replaced without falsely completing it. A first full frontier test
run exposed 40 failures: most were obsolete settlement-provision tests or
fixtures timed inside the new FREE window. Retired the obsolete provision
test suite in favor of a fresh-world no-legacy-owner check; updated affected
work/codec tests to current schema and schedule. Focused suites for player
stock exit, resident meals, field/bakery work, scout patrol, strategic work,
scene SDK, tactical equipment, engineering route and actor hand pass.
Static analysis also found two real fresh-world defects: initial scout patrol
due times incorrectly depended on the now-larger resident schedule count, and
the physical-intent validator omitted settlement IDs as canonical cause
subjects. Both are repaired and focused tests pass. Settlements 2–12 now have
one finite ledger-backed 64-bread starter lot each; settlement 1 still starts
with wheat for the bakery vertical. Without this, field maturity falls in
FREE and hunger at the next WORK boundary prevents the first harvest where
no bread exists. The old 21,140-tick partial-harvest assertion was replaced
with a truthful READY/FREE check; the worker→meal→resume native scenario
remains the causal work-continuation evidence. NeoForge main/test Java compile
is green. One final full frontier test run is underway on the frozen source;
after it, do only necessary fixes, one final native causal/player check, and
release verification. No live deployment, commit or push.

2026-09-28 claimed-bread product boundary: the new ordinary-client scenario
`build/frontier-v3-scenarios/disposable-resident-claimed-bread-exit-1790619078132.json`
completed 6/6 actions with one opened frame and graceful save/stop. A player
shift-clicked all 64 bread out of the HOT depot while one exact hungry
resident retained a pre-TAKE meal claim. The physical chest then had no bread,
the player inventory visibly held 64, canonical stock/claims were 0, the
resident remained hungry with no phantom meal, and the depot replica was
`OBSERVED_CURRENT` without conflict. Pure classification, registered engine
command/recovery and payload-codec tests also pass. Stock-exit WAL now carries
exact forfeited meal-claim IDs; schema is 212, so disposable earlier worlds do
not replay under this grammar. Unadapted claim purposes are still protected
before a risky player click. Fresh bootstrap no longer creates old settlement
provision counters; old schedulers and reducers are unregistered/fail closed.
Separate older provision code/test fixtures and some legacy declarations remain.
No live deployment, commit or push. Current priority: finish retirement/affected
test cleanup and all-family evidence, then final current-source integration and
player handoff. The task-owned native client/server stopped; Xvfb :95 remains.

2026-09-28 latest main-alone resident-life increment: native post-TAKE/COLD-to-HOT
restart scenario is green at implementation checkout manifest
`build/frontier-v3-scenarios/disposable-resident-after-cold-take-restart-1790617719399.json`.
The same exact resident retained one claimed bread in the actor account across a
graceful server restart, physically consumed it once after ordinary HOT ingress,
became nourished, and finished `RETURN` without a global quarantine. No live
server/world was changed. A source-fingerprint failure in the prior attempt was
caused by editing the worktree during the runner; the green run used a frozen
source. The old provision review/progress/objective schedulers were removed
from the active process catalog and its population reducer now fails closed for
legacy provision events; focused catalog and meal tests pass. Legacy provision
state/codec/classes and overly broad catalog emission declarations remain and
must not be called retired. New-work admission now excludes residents with a
retained meal, without invalidating their existing job; focused test passes.
Current priority: complete claimed player-depot edit disposition and legacy
nutrition retirement, then prove all-work-family safety, recovery and fresh
player check. No subagent, commit, push or deployment.

2026-09-28 main-alone acceptance increment: the corrected disposable worker-meal
native scenario is green at
`build/frontier-v3-scenarios/disposable-resident-worker-meal-1790616655716.json`
in the implementation checkout. It used a task-private display/port and actual
normal client, observed the exact farmer job before hunger, physical HOT meal,
same assignment afterward, then waited for the final field cell, confirmed
terminal physical harvest receipt, growth epoch 2 and current depot replica.
Two captured frames were opened; they show the settlement/field and villagers,
but are not by themselves evidence of bread in the hand. Task-owned client and
server ended with graceful durable save. This closes that one causal work→eat→
resume→terminal story, **not** the whole resident-life exit gate. Static review
also found an active export selector that could pick a pinned bread lot despite
other free stock. It now selects an unclaimed lot while preserving the resident
reserve; focused `SettlementFoodPolicyTest` passes. Remaining active gaps:
claimed player-depot edit disposition, legacy provision executable retirement,
full work-family safety, HOT/COLD/restart after take, and fresh player acceptance.
No live deployment, reset, commit or publication followed.

2026-09-28 active main-alone objective: finish the accepted resident-life/resource
vertical end to end, not merely its intermediate code or tests. The paused
thread goal is the older SA audit and cannot be replaced by `create_goal` while
unfinished; do not falsely complete it. Work remains authorized by the user's
explicit current assignment and this ledger. The failed initial worker-meal
fixture was impossible: its 64-cell field completed COLD at tick 2291 before
client ingress around 2384 and hunger at 2930. The pure test had masked this
by calling the scheduler only once every 20 ticks. The corrected fixture uses
an ordinary 65-cell job after its 64-cell COLD batch was delivered, leaving the
same job with one final cell and an empty farmer hand; it retains a scheduled
activity review alongside the exact hunger review. Per-tick pure engine test
passes. One isolated normal-client worker-meal scenario is green:
`build/frontier-v3-scenarios/disposable-resident-worker-meal-1790615737620.json`.
It observed exact FIELD_HARVEST job retained, HOT yield, MOVE, bread 64→63,
NOURISHED, RETURN and WORK on the same job. The scenario used a task-private
Xvfb and `FRONTIER_V3_PILOT_INITIAL_CANONICAL_HOLD=true`; client/server shut
down cleanly. Its frame was opened: field/worker are not legible, so this is
mechanical causal evidence, not visual/player acceptance. Earlier retries
exposed only absent initial hold and a missing activity-review action in the
custom fixture; neither was counted product-green. OPEN: visual worker proof,
HOT/COLD/restart after take, legacy provision retirement, other work-family
yield contracts, affected player-claim edits and fresh player acceptance.
The unrelated live server was not modified; task-owned Xvfb :95 is retained
only for further native work.

2026-09-28 main-alone resident-life continuation: implementation checkout is
dirty WIP on local `102c06b6`, not pushed or deployed. Typed resident metabolism,
sparse activity/need scheduling, COLD meal and physical HOT depot take/consume
are wired; one isolated normal-client idle-resident meal passes with depot bread
64 -> 63 and nourished status (`build/frontier-v3-scenarios/disposable-resident-hot-meal-1790606959583.json`).
This is not full acceptance. The native worker-meal fixture still loses its
field job before setup observation; cause is unconfirmed, so work yield/resume
is not proven. The pure worker fixture now retains that same job through +900
ticks, narrowing the discrepancy to native/projection behavior or fixture
admission. A bounded static ownership correction now moves HOT activity
retargeting out of the meal reducer into `ResidentActivityProcess`, makes the
ambient work goal respect exact activity choice, removes a hard-coded meal-side
work-family whitelist and unused meal movement-order facade, and wakes activity
arbitration after every consumed portion. The same audit found that all production
jobs were mislabeled `BAKING`; the read-only assignment is now `PRODUCTION`,
with non-bakery jobs explicitly held until they have an owner yield contract.
A focused pure engine test confirms
two portions for a two-unit deficit without waiting for the next day; affected
frontier tests and NeoForge compilation pass. The accepted contract and
architecture clarify that meal `RETURN` is an activity hand-off, while the
retained work owner supplies the resumed goal from the actual body. Legacy
settlement provision, all-family yield, claimed player-edit disposition,
HOT/COLD/restart and player acceptance remain open. No subagents, server
deployment, push, reset or publication for this WIP.

2026-09-28 resident-characteristics decision: the accepted resident-life cut now
requires typed, bounded per-resident characteristics, with metabolism as its
first consumer. World rules provide defaults/limits; HumanPopulation owns each
resident's base and source-identified modifiers; nutrition retains fractional
elapsed progress. A rate change integrates the old rate before applying the
new one and atomically reschedules that exact resident. The contract,
architecture and implementation plan contain the target. Source checkpoint
`f8690422` does **not** implement this seam; resident-life activation remains
incomplete. No subagents, live deployment or push were started for this change.

2026-09-28 11:12 UTC private source checkpoint: implementation checkout is
clean at local commit `f8690422` (`feat: stage resident care and observed depot
edits`), no push. This commit includes the resident model/COLD meal scaffold,
read-only resident-life diagnostic, observed player-depot click boundary and
both green fresh-world normal-client depot scenarios. It is **not** resident
nutrition adoption or full player-edit acceptance. Governance remains a
separate dirty repository with pre-existing WIP; no governance commit or live
deploy occurred. The next implementation step is the exclusive HOT resident
meal/physical custody path and full family-safe work pausing, followed by
retirement of legacy provision and fresh-world product acceptance.

2026-09-28 11:08 UTC supplement: `disposable_depot_player_multistack_exit`
passed on a fresh isolated normal client (12 actions, two frames, graceful
shutdown), manifest
`build/frontier-v3-scenarios/disposable-depot-player-multistack-exit-1790593562135.json`
in the implementation checkout. Two creative-provided 64-bread stacks were
shift-clicked into the PM-owned depot and then removed one by one; ledger
quantity/binding counts converged 64/1 → 128/2 → 192/3 → 128/2 → 64/1,
with current reference custody after the second exit. This is stronger
multi-stack evidence, not proof of drag/swap or affected-claim disposition.
No live deploy. All private Xvfb/server/client tasks are stopped.

2026-09-28 10:54 UTC main-alone implementation checkpoint: a fresh isolated
normal-client scenario `disposable_depot_player_gift_exit` now passes with all
eight actions, two captured frames and graceful server/client shutdown;
manifest is `build/frontier-v3-scenarios/disposable-depot-player-gift-exit-1790592780128.json`
in the implementation checkout. It proves an ordinary player can place one
64-bread stack in the settlement depot, then remove it, with exact ledger
quantity and HOT bindings converging and the container returning to
`OBSERVED_CURRENT`. The first causal run revealed a real global quarantine:
the interaction-keyed strategic stock wake lacked the numeric ordinal expected
by the shared planner. Its `-1` suffix and regression test are now in WIP.
The second causal run exposed a missing reference-replica closure after a
confirmed player click: ledger quantity changed but bindings drained to zero.
The click executor now invokes the shared confirmed-mutation transition after
the canonical receipt; the same fresh-world scenario passes. Two intervening
runner failures were bad diagnostic-assertion step numbers in the newly added
scenario, corrected and statically checked; they were not product failures.
Task-owned Xvfb `:95` and all isolated server/client processes are stopped.
The exact-resident read-only `resident_life` diagnostic was added afterwards:
it reports stored/effective need, schedule window, assignment, work-yield
status, chosen activity, retained meal phase/wait and depot bread/claims.
Its focused JSON test and the fast repository guardrails pass; it does not
activate feeding. These source edits are included in checkpoint `f8690422`.
No live server deploy, world reset or push. The wider accepted cut is
**still incomplete**: resident activity/need schedules remain dormant; HOT
meal movement/physical take/consumption, full work-family yields, affected
claim disposition for player edits, legacy-provision retirement, and
restart/creative multi-stack acceptance remain to implement. Do not claim
resident life active or deploy this source as a finished feature.

2026-09-28 current user assignment: main alone is implementing the accepted
resident-life/resource cut in `docs/frontier-v3-resident-life-resource-contract.md`;
no subagents. User selected resident-owned hunger as a bounded threshold need,
settlement-owned day WORK/night FREE policy, one exclusive WORK/EAT/IDLE activity,
resident self-service at depot, all existing human work adapters, ordinary work
pausing at safe checkpoints, urgent combat/care yielding at first safe point,
and player deposits as gifts. This explicitly supersedes settlement-wide
provision as nutrition authority, but not the separate exact birth target.
Governance contract/architecture/implementation-plan amendments are WIP and
do not claim runtime adoption. Implementation source checkout is
`/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`; the WIP described
here is now checkpointed in local `f8690422`. It adds epoch-fenced stock exit/gift ledger operations and
typed events, pure depot-click classification, a durable NeoForge pre/post menu
witness, an interaction-keyed stock wake, individual lazy hunger, segmented
settlement daily policy, ruleset-schema-7 parameters and pure WORK/EAT/IDLE
arbitration. `HumanPopulation` now persists policy and one retained meal per
resident in world schema 210; a need-integration process and meal-start owner
are registered but not initially scheduled. A COLD-only event advances an
already-started meal through depot arrival, exact bread handoff, one-unit
consumption and return; an addressable test proves the custody/need chain
across a snapshot restart. A goal adapter uses the existing bounded pedestrian
navigation to derive an ephemeral COLD route to the depot, including workshop
egress; focused initial-body/workshop-route tests pass. New resident-life enum persistence uses explicit
closed wire tags. Observed stock mutations have been extracted from the main
ledger to keep its size guardrail. Focused ledger, codec, process, need and
coordinator tests, NeoForge compile and full fast `guardrails` pass as of
2026-09-28 09:44 UTC. A later source cut adds explicit read-only work-yield
assessment (field/bakery safe points; other work families safety-held), rejects
loss/replacement of a meal's retained job at activity and recovery closure,
and suppresses field/bakery COLD work and new HOT scenes during a retained meal.
A focused COLD baker test proves empty-hand yield, one-bread self-care,
unchanged job and resumed work planning; targeted tests and fast guardrails
pass as of 2026-09-28 10:19 UTC. **This is not full resident-life acceptance**:
the later native click causal tests above pass, but claimed or mixed player edits are
rejected, sparse activity scheduling and arbitrary facility egress remain absent,
all-work safe-yield adapters and HOT meal effects/recovery are absent, death
closure is unresolved, and old settlement provision remains the active
nutrition path. Do not deploy, reset the world or claim the plan implemented.
Preserve existing incident evidence and unrelated WIP.

2026-09-28 user decision on resource accounting: retain one cohesive resource-domain ledger for economic owner, fungible quantity, custody account and active work claim; physical HOT slots are transient bindings, not ownership. Ordinary player removal from a PM-owned depot must be captured at the server interaction boundary and recorded as a typed stock departure independent of proving a matching player inventory slot; the resource owner alone updates quantities/claims and wakes dependent work. Do not introduce theft mechanics or a second ledger. The current disposable test world may be recreated after a verified fix; preserve its incident evidence before reset. This decision addresses depot capacity, not the separate HOT-bound fungible ration-progress defect.

2026-09-28 live Clearwater follow-up (read-only diagnosis; no new source/deploy): the player opened `container:7-depot` in creative mode and removed several bread stacks. The physical chest gained free slots but canonical inventory still retained 1417 bread and all 27 slots occupied. The container surface is `CONFLICT`; `settlement:7` harvest admission is `DEPOT_SURFACE_CONFLICT`, not merely `DEPOT_FULL`; `resident:7-13` is alive/idle and `site:7-wheat-field` READY. `FrontierV3FungibleResourceObservationExecutor.observeOnePlayerDeparture` admits only one diminished source binding and one equal-count player inventory stack, with no cursor or multi-stack transaction witness. Unmatched player chest edits become a terminal `ContainerSurfaceStatus.CONFLICT`; freeing slots cannot self-heal this world. This is the controlled-player-container-transaction gap already disclosed in `docs/frontier-v3-material-containers.md`, now player-reproduced. Separately, `FOOD SERVING 0/21` is fulfilled/required rations, not stock (the live diagnostic retained 1417 bread, reserve 63, 21 allocated, 21 nourished, 0 hungry). Static inspection also shows `SettlementProvisionProcess.planFungibleProgress` retries while HOT bread has physical bindings, so the pending ration cycle does not complete while that custody remains live; it needs a real HOT physical-consumption path or another product-correct transition, not a text-only fix. Exact menu click sequence and present physical/player inventory delta are unconfirmed; do not auto-adopt missing stock or claim repair from a later snapshot. Preserve live incident/world and unrelated WIP until evidence is retained for the authorized reset.

## Authority and assignment

Current 2026-09-28 storage-pressure correction: main alone, no subagents. Source checkout `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror` at local `e21194451ffdfa48c57152f9df5e8aad62dd2344` (preceded by local `e0b1e7a9` and `50da10ab`, no push). Full Clearwater depot had left the field READY with `DEPOT_FULL`; another full depot caused a global `28/27` bakery COLD-delivery quarantine. New harvest admission waits/retries; new bread objectives/market admission wait for output capacity; in-flight bread remains with the baker and COLD delivery defers with `STORAGE FULL`. First live deployment of `50da10ab` exposed a real replay incompatibility: a previously committed bakery MOVE was rejected by the new planner. `e2119445` validates historical movement without the new capacity policy while guarding actual delivery. Focused 25-test family plus replay regression, guardrails, detached assemble/JAR verification, pack validation, preflight and same-world post-start verifier passed. Active `far-frontier-v3-live.service` PID 4088967, world `frontier-v3-market-identity-retest-20260927`, seed `20260918065`, port 25565, core SHA-512 `291fe11194498c954273465f2e6f3207722e44eace290b9c7968a326b842e1be2cd1427c6d3702087a00a4991330b4d8c45f22ba01fce49b6f8bc098d03a2af0`; hosted client core is the same. Read-only summaries progressed from revision 198075/instant 704130 to 198578/704823 with status `ok`, green verdict and zero required/inventory conflicts. This is a diagnostic deployment, not HUMAN_CANDIDATE; player-visible resumed harvest/bakery after freeing storage is not yet witnessed. Preserve the existing world and governance/pack WIP.

Prior 2026-09-27 incident: player's Clearwater farmer stopped mid-harvest because the entire old live runtime quarantined at 23:21 on `MarketOrderBook.accept` for a stock-wake production order. The old terminal `order:production-1-1` existed; the stock wake reused review ordinal 1 and produced the same job/order ID. Source fix `f5a52ecdf145c88122706882081a1150a24d4c0f` derives bakery job, lot, claim and output identities from the unique strategic task, not the local ordinal; pushed to `origin/feat/baker-carry-orders-20260926`. Focused 90-test family (one obsolete fixture expectation corrected and rerun), guardrails, NeoForge compile, clean detached assemble/JAR verification passed; full `check`/graphical acceptance not claimed. Old world resumed with no global quarantine and accepted `order:production-objective-stock-site-harvest-1-wheat-field-1-terminal-1`, proving the collision repair, but its interrupted Clearwater scene recovered to a distinct local `CARRIER_FENCE_UNRESOLVED` conflict (11/64 crops); preserve it and its 34 MiB incident copy. The `f5a52ecd` JAR SHA-512 `2063d088c162a0ebd647e1f3a59b178fc1dc1aebde5dc54956984b7a480a8b1bcc9f7a91fcbc6f00fe4544f2e2b6aa541e43274795533a5ca96f6e4101445a93` was formerly deployed in world `frontier-v3-market-identity-retest-20260927`; it is no longer active.

Prior 2026-09-27 cut: main alone, no subagents. Field-cell loss and hybrid work reevaluation are committed at source `b528ec6c3acc0b72341118b5f910497433c10fbe`, pushed to `origin/feat/baker-carry-orders-20260926`; source tree clean. Crop loss atomically accounts CellId/job at zero yield, and depot stock wakes the planner once. Focused tests, guardrails, detached assemble and packaged-JAR verification pass. Broad NeoForge test was stopped; its stale diagnostic slot-0 oracle was corrected and the focused class passes. Full `check` was not rerun. Native `build/frontier-v3-scenarios/disposable-sa-active-target-player-loss-20260927-r9.json` passes player break, 65/65, restart and GROWING epoch 2; see `docs/frontier-v3-field-cell-lifecycle.md` for earlier test-method failures.
Clean detached release `/home/rd/proj/pm-release-b528ec6c` produced core SHA-512 `430f5cdfdb94086e88e910cd4e1a05ccaeaa4ac5896f3d84471d174df9d89122c81af44bcacbcd8e6b44f95525ac14518551d968e64e7702d61848b1f4bf2be0`. Pack validation, preflight and post-start verifier passed. That core was previously hosted and installed on `far-frontier-v3-live.service`, port 25565, world `frontier-v3-field-loss-workwake-20260927` (seed `20260918065`); it is no longer active. Diagnostic deployment only, not full release/HUMAN_CANDIDATE acceptance. Pack `.f0v-baseline/` and unrelated governance WIP remain untouched.

Current 2026-09-27 material-container checkpoint: main alone, no subagents; implementation checkout `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`. User authorized a generic resource/container subsystem for the baker path, with a PM-owned container optional. Uncommitted source WIP adds `MaterialContainerImage`: BULK depot/store stock compares item-kind totals independent of HOT slots; exact items and production-station FIXED_PORTS remain positional. `FungibleResourceLedger`/`ActorContainerItemOrder` remain the sole economic/transfer authority. Generic `MaterialSourceSelection` resolves claimed or explicitly declared unclaimed lot portions from current HOT bindings, and `FrontierV3ActorItemTransfer.FungibleStep` physically executes order-declared TAKE/PLACE for any valid item kind, including partial hand retention and owner/body/port checks. The bakery uses that executor for depot, hand and station handoffs. State schema 208→209 rejects old disposable worlds because replica fingerprint grammar changed. Focused pure/model/persistence and pilot-schema tests plus native reference-projection GameTests pass 7/7; the latter includes real chest move/split/rebind/reconcile across checkpoint/recovery and owner/body negative cases. Normal-client `build/frontier-v3-scenarios/disposable-bakery-owned-stock-reorder-1790514379433.json` passes 14/14: player moves owned wheat between chest slots; the baker takes it, processes and delivers 64 bread, and the terminal depot has current replica plus `ACQUIRED` custody with no unresolved reason. `build/frontier-v3-scenarios/disposable-bakery-full-cycle-restart-1790514573395.json` passes 12/12 through graceful restart with the same terminal custody assertion; both task-owned server/client/display processes stopped. These stronger assertions exposed a real `PROVIDER_LOST` in the earlier superficially green manifests: the baker's historical wheat-source hold was incorrectly treated as live depot authority after pickup, causing a foreign-epoch rejection when delivered bread closed its mutation. `ProductionResourceCustody` now ignores that historical hold only after the bakery phase has left `DEPOT_PICKUP`; a focused post-delivery new-epoch regression passes. `guardrails`, focused model/persistence tests, `assemble`, packaged-JAR verification and 51/51 pilot scenario tests pass. A fresh native reference-projection run passed 7/7 with clean shutdown. Broad NeoForge `check` remains red on the unchanged `FrontierV3DiagnosticJsonTest.authored65CellHarvestDiagnosticReportsItsSemanticGoalAndYield` expected 0/actual 64 assertion; broad Node suite is 381/385 with four unrelated pre-existing carrier/client/route-contract failures. Full milestone/release gate is not claimed. An earlier reorder scenario with 4500-tick fast-forward reached its terminal product assertions but timed out during server-save cleanup; it is not counted green. `docs/frontier-v3-material-containers.md`, contract and architecture describe the boundary. Controlled provenance for arbitrary player deposits/withdrawals, equal-count replacement and hopper transfers is still absent: a later vanilla chest snapshot cannot prove those transaction origins. Do not claim that broader player-custody promise. User-authorized deployment produced local checkpoint commit `db3ce56fe97bf2e7e15977b3ee5bd92b926bec16` (no push) and a clean detached release build `/home/rd/proj/pm-release-db3ce56f` with packaged core JAR SHA-512 `415955b18b3686f278518f967ad3d3453697b1bcbe73d11d1944b33951597fa4c2220f150fafb11c1b76ec47c34202a991002ea85dd08a5ff8e5c11c099b52e0`. Detached `guardrails`, `assemble` and packaged-JAR verification passed; pack validation/preflight passed. Core JAR was published to the client host and installed on `far-frontier-v3-live.service` in a fresh schema-209 world `frontier-v3-material-containers-20260927`, seed `20260918065`; previous `frontier-v3-bakery-repack-20260927` world/JAR remain recoverable. Post-start deploy verifier passed, port 25565 and live summary at revision 55 report 12 settlements, 12 sites, zero required conflicts and verdict green. This is diagnostic deployment, not HUMAN_CANDIDATE or full product acceptance; client must update the core artifact before joining. Pack `.f0v-baseline/` and unrelated governance WIP remain untouched.

Superseding 2026-09-27 depot/bakery checkpoint: main worked alone in `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`; source is clean and locally committed at `032a43b2017c1adade456615f0bf1e7f41d606de`, not pushed. Live Clearwater evidence showed 64 canonical wheat and 343 bread at `container:7-depot`, but a false `FINGERPRINT_MISMATCH` released physical custody, so the baker could not acquire wheat. Cause: after confirmed HOT wheat delivery the COLD lot-sort repacked slots, while the released replica's EXPECTED observation still compared the old physical slot order against the new COLD order. Fixed released EXPECTED observation to verify the retained physical image, then let the existing fence repack and reacquire custody. Focused native `reference-projection` GameTests pass 5/5; compile, guardrails, focused model test and packaged-JAR verification pass. Clean detached artifact from `/home/rd/proj/pm-release-032a43b2/pale-mirror` has SHA-512 `7e2470fb905a9f5f47899278476919c0adb13fecbb52bca07fa42c1ff922654ec29e3f15a29018c0735cb36fc358aeb755d35b5f024e58e45db41c134c17f5e7`; it is installed and hosted on `far-frontier-v3-live.service`, port 25565, fresh disposable world `frontier-v3-bakery-repack-20260927` (seed `20260918065`). Preflight and deploy verifier passed; initial diagnostics green with 0 required conflicts. Old `frontier-v3-area-work-20260927` world is preserved. OPEN: player visual check of farmer delivery, baker pickup/station/output and sustained cycles; no full-suite, HUMAN_CANDIDATE or SA-09 claim. Unrelated SA-06 WIP is untouched.

Latest checkpoint (2026-09-27; supersedes historical entries below): main alone, no subagents. Source `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror` branch `feat/baker-carry-orders-20260926` is locally committed at `134b498e91cb3083e8da20b6ba8547067b3d37f9`, not pushed. The bakery/actor-item vertical carries owned wheat depot → baker hand → station input, performs the station-held recipe, then carries bread station output → baker hand → depot; HOT/COLD, physical custody and restart are represented. Clearwater live incident showed a prepared crop changed by the player becoming `cell-before-owned_drift` conflict, off-survey farmer support preventing bounded continuation, and Minecraft collision jitter indefinitely resetting navigation stall; source now admits the observed external hold, skips an obstructed final crop cell without yield, accepts a bounded exact actor start, and requires real planar progress before renewing the stall timer. Physically bound fungible depot bread is now eligible for settlement provisioning, while the board distinguishes existing stock from an actually missed meal. Focused Java tests, guardrails and packaged-JAR verification pass; 152/152 required scene GameTests pass. Native `build/frontier-v3-scenarios/field-block-progress-20260927.json` passes 5/5 with site GROWING epoch 2 after HOT blocked-cell skip; native `build/frontier-v3-scenarios/bakery-restart-current-20260927.json` passes 12/12 with graceful restart and bread observed at the depot. An earlier baker run passed 12/12 actions but timed out in final teardown; the later restart run closed that specific proof, not every save/performance risk. Broad unrelated Frontier Java suite was stopped after its expensive world-building test; do not claim full suite or formal HUMAN_CANDIDATE. Clean detached build `/home/rd/proj/pm-release-134b498e/pale-mirror` produced JAR SHA-512 `76228bd992740b56481b0de902423a776ea843b9b9365dc965d0f716508d545854106ed42e1966e7b02e5811c5936f25535770b66dacee2af4baf61e1d717406`; it is installed on `far-frontier-v3-live.service`, port 25565, fresh world `frontier-v3-bakery-obstruction-20260927`, and the same artifact is hosted for client update. Preflight and fresh-start deploy verification passed with no new quarantine. Old `frontier-v3-bakery-20260927` world is preserved as incident evidence; its existing site conflict is not healed. OPEN: ordinary-player full-pack visual/interaction acceptance of farmer obstruction/recovery, bakery pickup/station/output/delivery and truthful depot status. SA-06/SA-09 audit remains open; no product-complete claim.

2026-09-26 Clearwater/depot correction and diagnostic deployment: source branch `fix/clearwater-depot-status-guardrails-20260926` at `e745a6aa` was pushed to `origin` (not merged). It distinguishes missed meals from bread stock, closes guardrails, corrects farmer ascent from fractional farmland, fences zero-revision scene release, and updates stale native fixtures.
Focused slices and full core GameTest 426/426 passed with clean shutdown; `guardrails`, detached `assemble` and packaged-JAR verification passed. Broad `check` stopped after 137 NeoForge JUnit classes passed while unrelated long domain tests continued; this is not a full gate receipt.
Clean detached JAR SHA-512 `73bcbb31…745675e` runs in live service `far-frontier-v3-live.service`, fresh world `frontier-v3-supported-farmer-20260926`, seed `20260918065`, port 25565; deploy verify passed with no new quarantine. Previous world `frontier-v3-lean-pack-20260926` remains on disk.
No graphical client/farmer-cycle acceptance yet; diagnostic access only, not HUMAN_CANDIDATE or SA-09 closure.

2026-09-25 farmer hand incident: `site:7-wheat-field` wrote blocks but VillagerOverhaul 3.10.17.16 cancelled offhand changes; later HOT/COLD handoff quarantined `frontier-v3-farmer-hand-20260925` on health representation. The world is preserved; no prior farmer/player evidence closes SA-09.

2026-09-26 user decision: remove VillagerOverhaul and unused EZ Actions, EZ Emerald Pouch, RPG Timeline. Source `94a13e4a` removes the bridge/hard dependency; pack `ac9924aa` removes four manifests. Focused field-turn GameTests 12/12, Visuals GameTests 39/39, JAR checks, pilot layout tests and pack validation pass. Full guardrails remain red on unrelated pre-existing limits; the full-pack two-start harness was stopped after successful mod boot because authored-region planning exceeded four minutes. Previous world `frontier-v3-no-villager-overhaul-20260926` is preserved.
Next user decision: retire nonessential content mods but retain actual scene/animation dependencies. Source `4da8d30a` updates pilot admission and accepts an empty disabled-mod catalog. Pack `c131f31` initially reduced 44 Packwiz manifests to 13; subsequent user correction restored client Simply Tooltips and Architectury/Fzzy Config, then exact JAR metadata and a failed boot exposed Fzzy Config's mandatory Kotlin for Forge dependency. Pack HEAD `152b4a5` has the full 17-manifest closure. Retained scene content: Create, Supplementaries/Moonlight, Quark/Zeta, Farmer's Delight, GeckoLib; performance: Lithium, ModernFix, FerriteCore, Fast Noise, optional disabled C2ME and client Sodium. Railway Untold remains retired. Pack validation, shell syntax, retirement closure test, pilot preflight, JAR assemble/verify and client bootstrap verify pass; broad NeoForge test task was stopped as disproportionate to the profile-only change. Do not repeat the incomplete transitive-dependency audit.
The earlier lean-pack deployment had world `frontier-v3-lean-pack-20260926`, core SHA-512 `c49b0f10…d14c1`, Visuals `2fd468cb…994601`, and RCON instant 68369 with zero conflicts. It is now preserved as the prior world, not the active service. The player's client must resync the new core artifact; visual farmer-cycle acceptance remains unproven and SA-09 OPEN.

2026-09-25 goal-navigation source checkpoint: normal farmer COLD turns now
advance from the actual actor body toward the derived CellId/depot goal using
ephemeral known geometry. The HOT scene pursues legal goal stations with
Minecraft navigation inside a bounded envelope, records one goal-arrival
event, and retains the actual supported body on mid-journey release. Unknown
COLD geometry produces a durable local goal hold; alternate legal depot ports
survive return and full-batch successor planning. Focused domain, codec,
fixture and NeoForge tests pass; native local-navigation 24/24, field-turns
11/11 and harvest-support 1/1 passed. The latest HOT change permits a bounded
two-cell physical detour outside the obsolete known-path stripe; a blocked
goal remains durable throughout transit and clears only on observed arrival;
new per-block HOT traversal and path-plan-only clear commands are rejected.
Domain/Process-Scene SDK regression and native detour GameTest pass. Crop preparation/receipt require the actual canonical farmer at the current CellId station, not just the transitional cursor; displaced-body negative, harvest domain tests and NeoForge compile pass.
Subsequent source cut: derived `MovementOrder` carries CellId/depot goals to shared COLD route search and the HOT Minecraft provider; changed order identity resets physical control/retry state. The active farmer job no longer serializes a route topology or waypoint cursor, and old per-block traversal/renewal reducers reject new events. Snapshot/envelope formats are 203/91; old disposable worlds cannot be reused. Focused domain/codec tests, NeoForge compilation, native local-navigation 24/24, field-turns 11/11 and harvest-support 1/1 pass. The one full 65-cell HOT scenario passed: first 64-crop hand delivered physically, last crop completed, site entered GROWING epoch 2; manifest `build/frontier-v3-scenarios/disposable-sa-field-65-two-batches-1790338544498.json`. The first two harness attempts were rejected before product evidence (display admission and obsolete zero-progress setup). Repeated HOT/COLD hand-offs, obstruction/clearance, hard recovery and smooth client motion remain unproven; SA-09 OPEN.

Diagnostic stand (2026-09-25): old source `c118cf73`/JAR `93c7ee27…f2b9ebfc` world `frontier-v3-goal-nav-diagnostic-20260925` was preserved after a genuine 19:39:56 +05 global quarantine: settlement supply `operation:supply-6-373` tried to add the 28th stack to the 27-slot hive-west store. This exposed an unintended ordinary strategic policy that exported surplus bread to the hostile hive with no recipient-side use. The user ordered that autonomous policy off and a recipient-neutral delivery subsystem scheduled in `docs/frontier-v3-implementation-plan.md`; explicit hive supply remains test-fixture-only. Main checkpoint `ff28a912` (no push) guards full-receiver arrival as a local retained-cargo wait. Focused strategic/inventory/runtime tests and packaged-JAR verification passed; broad Frontier/NeoForge suites were stopped after unrelated or timing-sensitive failures, so this is not a release/HUMAN_CANDIDATE claim. New JAR SHA-512 `22f02af4ad70bf7002b51f2375cd6ac0a6ebefe75ee869551ed057953e33fc05b32db0e02e6681d5ecedbe4e917294c0f79ba1ee599ff981cd978437a41eae05` is installed in fresh world `frontier-v3-goal-nav-repaired-20260925`, same seed 20260918065; `far-frontier-v3-live.service` post-start verify passed on port 25565 and read-only summary at instant 419 showed status ok, 12 settlements/sites and zero conflicts. The previous world was not deleted. Source/pack clean except pack `.f0v-baseline/`; governance retains existing WIP. Player must enter `pale_mirror:frontier_graybox`, not the vanilla overworld; full-pack player check and SA-09 remain open.
See `docs/frontier-v3-goal-navigation.md` for the contract and precise gap.

2026-09-25 continuation checkpoint: an observed blocked CellId prefix whose
next bounded route cannot be compiled no longer falls through to whole-site
HOT conflict or COLD planner quarantine. The same correction covers a failed
route renewal at a bounded field-segment boundary. HOT retains a typed job-local
`CONTINUATION_UNAVAILABLE` goal block naming the next semantic work goal (or
depot), its layout revision and retained farmer station; it is WAL/snapshot
serializable, prevents COLD movement, and is cleared only after a different
canonical prefix or a successful bounded route re-probe. The board names the
paused route. A focused deterministic interior-isolation test proves the
unavailable route, durable block, failed premature clearance, and exact
clearance after changed observed cell state; the full
`ResourceSiteHarvestTraversalTest` class is green (61 tests, 0 failures) and
NeoForge compile passes. This is a safety/locality correction, not the full
hybrid migration: COLD currently waits without a durable planner block until
HOT observes it, the canonical per-cell corridor still exists, and no native
farmer route-block/clearance or product scene has passed for this cut. SA-09
remains OPEN; do not deploy or claim player acceptance from these checks.
One bounded disposable native scenario now passes at
`build/frontier-v3-scenarios/disposable-sa-field-blocked-final-cell-goal-1790310555061.json`:
with a real pilot and an observed foreign stone in the final crop cell, the
site reached GROWING epoch 2 without a whole-site conflict and its terminal
harvest receipt resolved. This is useful liveness evidence but does not by
itself distinguish HOT blocked-cell bypass from COLD continuation or prove
the final cell's zero yield; do not count it as the full navigation acceptance.
The first attempted native invocation lacked the required private-display
flag and failed before the client; the next placed stone before field
initialization and correctly produced a lifecycle conflict, so the scenario
was corrected to wait for an ACTIVE physical ownership claim before the edit.
The green run then used an isolated world/port and private Xvfb; all owned
client/server/display processes were stopped. No live deployment.

2026-09-25 follow-up native discriminator: the declared scenario now waits
for an actual HOT farmer lease *before* placing foreign stone in the final
crop cell. The HOT scene records accepted typed blocked-cell skip, goal block
and clearance results in the bounded non-canonical scene trace. The revised
one-world run is green at
`build/frontier-v3-scenarios/disposable-sa-field-blocked-final-cell-goal-1790311007386.json`.
Its diagnostic shows a HOT lease at revision 44, then accepted
`resource_site_harvest_blocked_cell_skipped` at revision 48 under the same
`lease:site-harvest-1-wheat-field-1-crop-64-r34` and actor `resident:1-13`;
the trace chain includes `resource_site_harvest_hot`. The site reached
GROWING epoch 2 with no conflict and a resolved terminal receipt at revision
55. This *does* distinguish the HOT skip from a merely COLD completion,
unlike the earlier run. It proves this final-cell obstruction path only;
the manifest does not independently assert terminal zero yield or prove a
multi-cell HOT continuation, unavailable/cleared transit goal, restart,
arbitrary terrain, smooth motion or full player acceptance. One green
terminal run was retained; no redundant rerun. The private server/client/
Xvfb stopped and the live service remained untouched. SA-09 stays OPEN.

Current source follow-through after that HOT-skip proof: a typed
`ResourceSiteHarvestGoal` now derives the next stable work CellId or bounded
four-station depot service region from canonical field/layout/settlement state,
independent of the cached physical route. Continuation obstruction targets
use that semantic goal; the process diagnostic exposes its kind, CellId,
revision, capability and legal stations alongside the historical route
cursor. The focused goal-vs-waypoint test passes; the selected harvest
traversal class is now 62/62 and NeoForge compiles. This is an adopted
goal-identity seam, **not** retained goal-only travel: the job still serializes
and executes its per-block `TraversalTopology`/cursor. Finish the connected
migration specified in `docs/frontier-v3-goal-navigation.md`; do not claim
smooth motion or general goal navigation from this cut. No live deployment.

Current 2026-09-25 goal-navigation/SA-09 source checkpoint: the accepted
target design is at `docs/frontier-v3-goal-navigation.md` and linked from the
implementation plan/architecture. Main added an ephemeral bounded Minecraft
pedestrian path provider for the HOT farmer, with an owned-body Mob mixin that
retains the persisted NoAI flag while allowing vanilla travel and cancelling
vanilla task/Brain selection. The local-navigation native slice is green
(21/21), including a two-high obstruction detour, real hydrated farmland
with wheat and a finite unreachable-goal disposition. The provider now has
a physical no-progress deadline across path recomputations and a retry
deadline that cannot be reset by alternating failure reasons. The tests
exposed/fixed a support-vs-feet Y offset in the shared local envelope.
Focused Frontier blocked-cell/recovery tests pass. A
typed WAL/codec-backed skip now accounts a contiguous physically witnessed
foreign-blocked CellId prefix, zero yield, unchanged farmer, and replans from
the same station; the new consecutive-blocked test checks a later blocked
target truncates the next route. This is source WIP, not deployed or player
accepted. A later source cut now retains a typed job-owned HOT transit-goal
block (target/cursor/layout/cause) across snapshot/WAL; COLD cannot advance
it after scene release, and the board/diagnostic distinguish route pause
from whole-site conflict. HOT uses a bounded path reattempt and publishes an
exact clear before motion resumes. A blocked worker's next HOT demand now
follows its retained station without probing an unloaded remote field or
adjacent chunk. The snapshot/envelope versions are 202/90;
focused persistence/architecture checks pass. This removes the prior source
fall-through from exhausted route retry to whole-site conflict, but it has
not been tested through a full native farmer scene or restart. The old
on-disk 201/89 world format is intentionally incompatible; any later test
deployment needs a fresh/disposable world under the server-operation rules.
The canonical per-block corridor is still present, so full goal/work separation,
field-to-depot native continuation, unload/restart, large-field HOT bounds,
and product acceptance remain unproved. Do not claim hybrid migration or
SA-09 complete from these focused results. In particular, route-clear still
needs a native physical/evidence test, and a blocked-cell skip whose revised
route is itself unavailable needs a local disposition instead of conflict.
The actual `ResourceSiteHarvestTraversalTest` class is now green (63/63):
two legacy exact-stack assertions were migrated to current fungible custody,
and two invalid scene-release expectations now check that a bound HOT hand
cannot be released before its physical handoff. Existing separate positive
delivery tests remain in that class. An earlier targeted Gradle filter used
the source filename instead of the JUnit class name and executed no relevant
methods; those no-op successes were discarded. A return-only zero-crop route
after an entirely obstructed field now survives snapshot round-trip, while
the same farmer completes without invented wheat.
`field-turns` native is green (11/11). No live deploy.

Latest SA-09 foreign-cell cut (2026-09-25): a separate typed per-site
foreign-change hold, observed postcondition and acknowledgement now preserve
exact soil/crop NBT in SavedData, gate only that site's COLD work, update the
physical claim after WAL acceptance and reconcile held/orphan windows. Focused
Frontier/NeoForge tests and `git diff --check` pass. One corrected native
disposable support-replacement/graceful-restart scenario is green at
`build/frontier-v3-scenarios/disposable-sa-field-foreign-support-restart-1790300154212.json`:
accepted pre-write hold and observed obstruction, stone retained after restart,
ACTIVE site, canonical `OBSTRUCTED/OBSTRUCTED`, no pending hold. Client/server
and private Xvfb stopped; the unrelated live service was not touched. This is
not proof of blocked-cell farmer continuation, hard crash, unloaded-half edits,
later COLD epoch or player acceptance. The distinct crop-clearance result is
recorded immediately below. Valid-shape
external wheat planting still needs a declared ownership policy. SA-09 OPEN;
no deployment. See the dated audit for details.

Follow-up native crop-space/clearance receipt is green at
`build/frontier-v3-scenarios/disposable-sa-field-foreign-crop-clearance-1790300836889.json`:
actual stone replaced the first crop, canonical cell became
`FARMLAND/OBSTRUCTED` with exact foreign crop NBT; after graceful restart,
actual air clearance returned canonical and committed `FARMLAND/ABSENT` and
removed the foreign incident, with no pending hold. Early failures were
private-display setup and scenario assertion addressing, not product results.
Task-owned client/server/Xvfb stopped and ports free. Remaining SA-09 gaps:
blocked-cell farmer continuation, hard crash, unloaded-half changes, later
COLD behavior, valid-shape external planting policy and player acceptance.
Static follow-through now proves why blocked-cell continuation remains open:
the segment route visits each original crop workstation before the HOT work
executor can emit `SKIPPED_BLOCKED`; a solid foreign crop makes that station
physically unnavigable. The next source correction needs a durable reachable
bypass/route revision from the retained worker position plus exact CellId skip,
without teleporting the farmer or waiving unrelated route conflicts. See the
SA-09 audit; avoid a broad native matrix before this source seam is fixed.
The follow-up source audit further confirmed that `ResourceSiteLifecycle`
requires the worker at the crop cursor for ordinary progress, COLD requires
the same station, and `ResourceSiteHarvestSegmentRenewed` only applies after
the old segment is exhausted. A simple `SKIPPED_BLOCKED` work result or a
motion-side arrival exception cannot solve this. The accepted next design is
one typed blocked-cell skip/replan event from the *current* retained station:
verify exact CellId/foreign physical witness and HOT hand when present, mark
only that cell accounted with zero yield, preserve worker/job/hand, and
compile a bounded continuation from the same station avoiding known blocked
supports (or a direct depot-return route if it was the last cell). HOT and
COLD must both use it before approaching the blocked edge; a failed physical
route remains an exact local obstruction. This described the earlier
checkpoint; the current entry above records the subsequent source work. See
the audit's implementation analysis for invariants.

2026-09-25 latest SA-09 result: mixed HOT→COLD farmer work now has a typed
read-only deferred depot receipt. The reducer requires its retained completed
lineage/HOT history, exact RUNNING intent, current replica and acquired chest
scope; it never transfers/creates wheat again. Stable WAL/snapshot codecs,
full-state validation, and focused positive/negative/recovery tests pass.
Native run `build/frontier-v3-scenarios/disposable-sa-field-world-soil-repair-1790297315554.failure`
observed an ordinary depot visit with `ACTIVE/OBSERVED_CURRENT/ACQUIRED`, no
slot mismatch, then `GROWING` epoch 3 with the prior intent `CONFIRMED` and
`physicalReceiptResolved=true`. The wrapper is red solely because its last
matcher used the wrong JSON level; the scenario was corrected and parsed but
not repeated for a green label. A prior run proved a real released-replica
bug: old physical fungible stock was classified against newer COLD canonical
slots and falsely conflicted. The released-scope comparison now uses the old
replica's plain-fungible fingerprint, while current-state confirmation stays
strict. The native run supports that correction. The run also exposed an
incomplete causal trace despite the confirmed receipt; the terminal COLD
event is now retained when it creates lineage, with focused test green but no
subsequent native proof. SA-09 remains OPEN for foreign support/air aftermath,
hard-crash/recovery and full player/product acceptance; no deployment.

2026-09-25 earlier SA-09 handoff correction: a new typed, atomic
`ResourceSiteHarvestHandRelease` closes the farmer's exact bound wheat hand
and DRAINING scene together; ordinary `SceneLeaseReleased` still rejects a
bound hand. Focused process/codec/planner test and Java compilation pass. One
native soil-repair run showed physical till/plant of the damaged first cell,
accepted `scene_released` revision 262 after player departure, no quarantine,
and COLD progression to `GROWING` epoch 3. At that point no deferred depot
receipt producer existed; the exact gap and later correction are recorded in
the dated audit and the latest entry above. Evidence:
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-repair-1790294479692.failure`.

2026-09-25 latest SA-09 increment: ordinary `Level.setBlock` crop/soil loss
now persists an exact cell witness and accepts its WAL-backed per-site hold
*before* Vanilla writes the block. The bounded scanner remains a backstop.
One native diagnostic is green at
`build/frontier-v3-scenarios/disposable-sa-field-prewrite-diagnostic-1790291819028.json`;
its trace proves hold before observed cell event. The corrected real
soil-damage/graceful-restart scenario is green at
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-damage-1790291932385.json`:
first cell DIRT/ABSENT, ACTIVE matching claim and no world witness after
restart. Two intervening failures were pilot revisit/diagnostic-request
mistakes, not product failures. Static review also removed a reachable blast
capture crash: the old stage/prefix candidate now considers only touched
legacy sites; ACTIVE cell-owned crop/soil cells remain excluded from generic
scar capture without invoking the legacy claim API. Compilation and focused
field-witness/cell-claim JUnit pass. Neither hard-crash nor actual blast,
foreign soil aftermath, direct chunk mutation, later farmer repair or full
SA-09/product acceptance is proven. No deployment. The dated audit has the
specific evidence and remaining obligations. The foreign-cell gap identified
at this earlier checkpoint was later partially corrected by the typed path
above. Blast aftermath still has no corresponding native acceptance receipt;
do not infer blast support from a `/setblock` result.

2026-09-25 SA-09 local world-cell change source correction: the active
projector now observes an exact naturally loaded owned crop-loss or soil-to-dirt
postcondition before projection, saves a per-site physical before/after witness,
then submits `ResourceFieldCellObserved(Source.WORLD)` and updates the claim
only after WAL acceptance. The witness can reconcile physical-before-WAL and
WAL-before-claim-update windows; pending player/farmer/projection, foreign
blocks and COLD lag are excluded. Focused witness/ledger JUnit and Java
main/test compilation passed. Managed native soil reversion remains vetoed;
at that earlier checkpoint, stale-epoch recovery and the whole-site loaded
gate were still open constraints; the later hold/scanner paragraphs below
record their scoped correction. The dated SA-09 audit records this as partial,
not completion.
The new scoped native receipt is green:
`build/frontier-v3-scenarios/disposable-sa-field-world-crop-loss-1790288173516.json`.
After the `field_physical` diagnostic proved an ACTIVE/current first-cell
owner, a real `/setblock` crop loss produced accepted
`resource_field_cell_observed` revision 35 and committed
`FARMLAND/ABSENT/0` without site conflict. Graceful save/restart with the
same client JVM preserved that claim and the absent block. This does not prove
soil damage, hard-crash windows, later farmer repair or full SA-09. The
corrected disposable scenario is checked in; its one green terminal run is
enough for this narrow claim. The separate soil-damage scenario is green at
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-damage-1790288982060.json`:
an ACTIVE first-cell farmland/wheat became real dirt/air, accepted
`resource_field_cell_observed` revision 103, retained `DIRT/ABSENT/0` with no
pending witness, and survived graceful restart in the same client session.
This proves damage admission, not later farmer till/plant, hard-crash recovery
or unloaded detection. Its first attempt was invalid because the setup waited
for a job that had completed during client startup; both world-cell scenarios
now gate on the exact ACTIVE field owner instead of that timed job. The dated
audit records a source-proven stale-epoch risk: pending world witness needs
loaded blocks, but COLD may advance the site epoch first; the witness then
cannot match or rebase. The source now installs an exact WAL-backed per-site
hold from a SavedData witness before COLD advancement, even with its chunk
unloaded. It blocks only that site's growth/harvest and persists through
snapshot format 200/envelope 88. Exact loaded-world before/after and physical
claim confirmation close it; an orphaned hold can reconcile after witness
retirement. A stale site's witness no longer starves others. World-cell
observation now checks one naturally loaded cell per active site per physical
turn independently of whole-site projection/presentation demand. Focused
model/codec/persistence/catalog tests and NeoForge main/test compilation pass.
The changed source's single native soil-damage scenario is green at
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-damage-1790290411299.json`:
real dirt/air, accepted canonical observation, matching physical claim and
graceful-restart retention. This does **not** prove a killed-process boundary,
partial-site observation, pre-witness damage registration or subsequent
farmer till/plant. After the native run, a static-only correction removed
same-block reconciliation without the exact hold and makes hold rejection
fail closed before COLD; focused compilation/tests pass, no second native run.
SA-09 remains OPEN; the dated audit records the limit.
Test server was not deployed.

2026-09-25 SA-09 revision review: the admitted 65-cell field is an immutable
fresh revision-1 manifest, not a mid-world resize. Source proves the current
barriers: `ResourceSiteState.validate/replace` pin the bootstrap layout,
`ResourceSiteHarvestTraversal.supportsSegmentedHarvest` admits only revision 1,
and the physical witness matches the exact old layout fingerprint/CellIds.
`ResourceFieldCycle.revised` has no production caller. The dated audit records
the required single-owner durable migration boundary. Do not relax one guard
in isolation or claim dynamic layout support from the 65-cell native result.
The pure helper now rejects revision after any accounted work, preventing a
reordered CellId list from silently reinterpreting the old worker prefix.
Quiescent revision remains possible; focused `ResourceFieldCycleTest` passed.
No active revision producer, physical claim migration, or native proof exists.
SA-09 now also has one green isolated *graceful* restart receipt for the
post-first-part 65-cell fixture:
`build/frontier-v3-scenarios/disposable-sa-field-65-last-cell-restart-1790286173005.json`.
After server save/restart and same-client reconnect, the field reached GROWING
epoch 2 with its physical intent CONFIRMED/receipt resolved; the depot was
OBSERVED_CURRENT with wheat 64+64+1. The restart split occurred before last-cell
HOT admission, so it does not prove hard-crash or mid-write recovery. Private
Xvfb frames are technical evidence, not human visual acceptance. The old
diagnostic `causalTrace` remains incomplete; SA-09 stays OPEN. Task-owned
disposable server/client and Xvfb were stopped after the run.

Canonical governance is `/home/rd/proj/pm-governance/pale-mirror`; active source
is `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror` (Git root its
parent). Preserve extensive existing WIP and the distinct Minecraft-root,
Pale Mirror source and governance histories. No subagents, unrequested push,
deployment, reset or destructive cleanup. The current source is not a release
candidate. [Engineering protocol](docs/engineering-agent-protocol.md) owns
workflow; [field-cell lifecycle](docs/frontier-v3-field-cell-lifecycle.md),
execution semantics and `architecture.yml` own the product/technical contract.

The full previous ledger is preserved in
[the 2026-09-24 archive](docs/archive/CONTINUITY_2026-09-24_pre_sa_scope_compaction.md).
Its chronology, old active-process claims and older live-world notes are
historical evidence, not competing current authority. Consult a specific
receipt when needed, not the entire archive on each turn.

## Current source state and next work

2026-09-24 priority correction: the protocol now forbids treating an
unreproduced failure story as an active repair. Static analysis of a reachable
production path is sufficient evidence to begin a fix; client reproduction is
not a prerequisite. The duplicate-UUID-across-saved-columns scenario in SA-06
is hypothetical; the bounded census is inactive and has no demonstrated
production result. Do not expand that line merely because its tests pass.
Prioritize wiring one known player-visible SA-09 field/worker cycle through the
active production path to terminal output, then perform focused and direct
native/client verification. SA-06 remains open for its explicit identity and
recovery contract; revisit a proposed uniqueness remedy when a reachable
counterexample or necessary acceptance obligation identifies its exact role.
No WIP is discarded by this priority change.
Source review isolated the immediate SA-09 cutover contradiction: canonical
cell loss produces a non-yielding work outcome, but the active COLD/HOT terminal
and physical output still demand exactly 64 wheat; the current player adapter
marks a break as whole-site conflict before an observed cell result. The dated
audit gives the precondition/transition/wrong result. A source-proven separate
final-crop→field-edge actor snap has now been removed: HOT and COLD use the
retained return corridor. First player ingress during that return tail can
re-admit the same worker without reopening crop work. After the HOT scene
releases a worker already at the terminal station, an explicit COLD returned
fact now terminalizes the same task/schedule without another movement edge or
an unloaded-depot dead end; a RUNNING physical request retains its one pending
receipt. The active retained route now continues from the local field edge to
one declared depot service station for each of 12 current graybox settlements;
the job stores the first-crop cursor explicitly instead of deriving it from a
fixed return length. First HOT ingress during that leg follows the worker's
canonical station rather than the distant last crop. Snapshot schema 191 and
persistence envelope 79 reject old route/cursor bytes. Focused COLD/HOT
traversal, persistence, scene/executor and model tests plus main/pilot/test
compilation passed; no native/client/deploy proof exists. This route still
does **not** transfer wheat from a physical farmer hand to the chest. The
latest source increment credits each confirmed COLD yielding cell into one
stable actor-held fungible lot and, after the retained return route, moves its
positive part to the depot account; a zero-yield cycle creates no lot. The
old terminal exact 64-stack issuance is removed and its NeoForge writer is
inert without that canonical exact item. Focused partial/zero/full COLD and
restart tests pass. This is **not a releasable player path**: HOT currently
does not observe/bind physical offhand increments or execute a paired physical
depot handoff, and COLD delivery still needs custody-safe interaction with a
loaded depot. Do not deploy or call SA-09 complete. Next connect the paired
physical cell/hand witness and batch handoff to this same lot owner, then
retire legacy job/intent/output fields and stage-prefix claim in a versioned
cutover before client verification.

Current SA-09 HOT safety seam (2026-09-24): crop-progress payload/WAL now
retains an optional exact actor-hand observation (envelope 80). The reducer
requires it under the named HOT lease and refuses it in COLD; each yielded unit
extends the one bound actor lot, not a second exact stack. The old exact-stack
harvest completion is rejected, and a DRAINING farmer with a bound hand cannot
release. Focused harvest/codec/persistence tests and NeoForge compile pass.
This is deliberately not a working HOT harvest: the active NeoForge crop executor
still submits no paired hand observation, and there is no physical depot handoff.
Next connect the already-defined per-cell field/hand witness to that executor,
then perform the source-owned hand-to-depot transfer and only then test one
ordinary complete cycle in the native client. Do not infer a product fix from
these model checks or deploy this WIP.

Subsequent active-reader correction (2026-09-24): fresh field admission now
reads the exact current CellId from the mutually exclusive cell-owned SavedData
witness and naturally loaded block observation, rather than calling its legacy
stage/prefix accessor. The ordinary field tick routes cell-owned sites to a
bounded one-cell canonical-first projector, which can project accepted COLD
crop work as well as growth from the exact retained physical predecessor.
The legacy whole-field restart classifier hands cell-owned sites to that
per-cell owner. Main/test compilation passes; no native or player proof yet.
This does not wire HOT paired hand effects, loaded-depot delivery, or all
restart/incident paths. Continue those active owners before any client claim.

Further SA-09 HOT cell work (2026-09-24): the active harvest scene now uses a
durable per-cell physical-first witness. It prepares the exact crop transition
and, for yield, the farmer offhand predecessor before writes; confirms one
loaded-world block step per turn, confirms the real offhand increment, then
submits the typed HOT progress event with the observed hand. On the next turn
the witness retires only against the accepted canonical cell and current
physical postconditions. Non-yielding repair/skip uses the same cell result
without inventing wheat. Main/pilot/test compilation passes; no native
evidence yet. This remains not player-ready: actor-held COLD stock still needs
physical HOT admission, the returned hand still needs a source-owned physical
depot transfer, and crash/recovery plus visual lifecycle remain unverified.

SA-09 return-custody source finding (2026-09-24): generic fungible layout and
handoff commands explicitly reject actor custody. The field ledger has a
specific observed actor-to-depot transfer, but no active field command or
physical adapter consumes it. The legacy job reserves a single exact output
slot, world-state validation keeps that slot vacant while the job is active,
and the old harvest receipt requires exactly 64 wheat. A separate chest
transfer before terminal job removal would therefore be invalid. The new path
needs one field-owned, capacity-checked hand/depot observation with an atomic
resource/job/intent terminal transition, or a bounded part transition with
an explicit reservation migration. Do not use generic handoff or reinstate
the exact stack. Further static review confirms the cutover also reaches the
producer-declared intent OUTPUT_ITEM role, job/lineage exact slot, typed
HARVEST_OUTPUT relation, retirement account, exact receipt codecs and HOT
scene DRAINING branch. COLD-carried stock needs a paired physical HOT
admission before crop work or delivery; no client run can repair this missing
source path. The HOT witness no longer re-reads an already-acknowledged
field cell on every return tick; only an unresolved physical cause requires
loaded blocks. NeoForge compile passes. No depot handoff/native proof yet.

The active harvest job now persists its producer-declared actor account ID
through snapshot/WAL (snapshot 192, envelope 81), and COLD/HOT cell accrual
and COLD terminal delivery consume that retained ID. The closed relationship
view and harvest retirement account both name `HARVEST_ACTOR_ACCOUNT`; the
balance itself appears only after positive yield. Focused relationship/harvest
tests and NeoForge main/pilot compilation pass. This closes one identity gap,
not the missing HOT admission, physical depot handoff or legacy exact-output
cutover; SA-09 is still OPEN and no native/client/deploy proof follows.
Further source tracing constrains the terminal cutover: the active job keeps
its old chest slot vacant, and a HOT harvest lease requires that job; a
terminal lineage permits only DRAINING/UNKNOWN/CONFLICT after removal.
Therefore a positive observed hand→depot transfer cannot precede a separate
job retirement, nor can retirement leave HOT behind. One canonical
publication must settle resource custody, job/intent and HOT→DRAINING
together after a durable physical witness. The zero-yield path requires an
observed empty hand without a resource transfer. The dated SA-09 audit has
the exact source owners. This is an implementation constraint, not a fix.
The producer now also retains its exact destination depot-account ID in the
active harvest job (snapshot 193, envelope 82). COLD terminal delivery uses
that declaration, and the closed relationship/retirement views expose
`HARVEST_DEPOT_ACCOUNT`; existing accounts must have matching container
custody. Focused harvest/COLD/relationship/persistence tests and NeoForge
main/pilot compilation pass, including forged-destination rejection. This
removes a downstream owner lookup but does not make HOT delivery executable.

Further SA-09 source correction (2026-09-24): the active harvest physical-intent
schema now binds the job's declared actor and depot accounts, not a fictional
exact 64-wheat output item (role-schema tag 30; tag 14 retired). The retained
predecessor lineage persists both account IDs; relationship and retirement
views no longer advertise an active exact-output edge. Snapshot format 194 and
WAL envelope 83 reject the previous layouts. Frontier/NeoForge main, test and
pilot compilation plus focused harvest, relationship, persistence and resource
site adapter tests pass. This fixes identity/authority declarations only; the
HOT physical hand→depot observation, atomic terminal publication and COLD→HOT
hand projection remain OPEN. No native/client run or deployment was performed.

The next source increment added a typed HOT harvest-delivery receipt and a
domain terminal reducer that atomically settles the actor-held lot into the
observed depot layout, retires the job/cycle, records the intent and drains its
scene; positive yield closes the reference mutation. One focused full-yield
regression exposed a real projected-slot collision: harvest chose a nominally
free exact slot already occupied by COLD-projected fungible stock. Shared
slot admission/reservation now excludes such projected slots, preserving the
old 64 plus the new 64 in the terminal test. Receipt fingerprint validation
uses the existing `sha256:` reference grammar. Focused harvest, traversal,
reference, yield and production JUnit plus Java compilation pass. This is
source-level progress only: there is no trusted physical hand/chest witness,
no COLD→HOT hand projection, native/client proof or deployment. SA-09 remains
OPEN; wire the active native producer before calling the field playable.

The returned HOT farmer now has a native delivery executor before generic
chest observers. It persists a format-10 site SavedData witness before the
physical chest/offhand pair, then submits the typed terminal observation only
after both postconditions are visible; the scene waits instead of draining
with wheat still in hand. Generic observers defer only the witnessed depot.
NeoForge main/test/pilot compilation and focused SavedData/registry JUnit pass.
This is an unaccepted source path: real-body/chest native execution, crash
seams and foreign-interference disposition remain. A subsequent COLD→HOT
hand-projection event binds the existing farmer-held lot to the observed
offhand without re-issuing wheat; focused model/codec tests pass. The
physical writer now persists a format-11 site-owned hand witness before
its only offhand write, rejects unwitnessed wheat and refuses to re-issue
from an ambiguous pending-empty witness. This is safe fail-closed but a
crash before the entity write may leave a local conflict; actual hard-crash
recovery and native/product acceptance remain open. Static review found
and corrected two active delivery liveness gaps: a COLD-worked job joining HOT
on its last return edge can now durably enter RUNNING at the depot, and one
unloaded pending depot no longer starves other sites. Model regression and
NeoForge compilation pass; no native/client proof or deployment follows.
The active farmer-hand and terminal chest readers now reject modified wheat
components instead of reducing them to ordinary kind/count evidence; reference
and generic fungible chest observers also fail closed on such layouts. Focused
hand JUnit and NeoForge compilation pass. This is a source correction only.
Pending field-delivery witnesses now revalidate the actor lot and turn
foreign chest/hand evidence or the chest-before/empty-hand split into a
recorded local conflict instead of waiting silently; reference-depot drift
is handed to its replica conflict owner. Focused tests/compile pass, but
actual native intervention and process-kill behavior remain unproven.
Do not deploy or claim SA-09/player completion yet.

SA-09: canonical `ResourceFieldLayout` and `ResourceFieldCycle` retain stable
CellIds, explicit geometry and typed per-cell work/loss. Model-level fungible
lot and actor-part custody exists. Active field readers resolve identity from
bootstrap and layout from the canonical cycle. However aggregate validation
still rejects revision 2 because there is no accepted revision/retirement
chain; simply relaxing that guard would be unsafe. Active NeoForge preparation,
projection, HOT work, conflict/explosion and output still use the old uniform
stage/prefix claim and exact-64 yield. The replacement witness and field
claim are inactive: SavedData format 9 has mutually exclusive legacy versus
INITIALIZING/OWNED claims; the first-field plan derives one write at a time
from the canonical layout, checks real neutral soil/irrigation or AIR crop,
and advances only after its own observed block write. A target-looking or
foreign block cannot be adopted, but an interruption between block write and
durable cursor update still fails closed. Activation now verifies every loaded
irrigation slot and actual soil/crop cell before installing the witness; focused
unit and native `field-turns` 8/8 passed (2026-09-24 audit receipt). Production
cutover must switch the whole call graph
together with the canonical cell/lot receipt, hand-to-depot delivery and
capacity/backpressure. Do not infer completion from inactive APIs.
The resource-site SavedData now has atomic fsynced publication; an inactive
level-owned first-field writer persists reservation and prepared cursor before
each real block write, then the advanced cursor and activation. Native
`field-turns` 9/9 reads the actual SavedData file at each boundary; focused
persistence tests pass. The old active stage/prefix path still has no explicit
durable-before-block publication, and a crash between block write and cursor
fsync remains ambiguous. Neither the writer nor the cell claim is in production.
Witness format 7 now separates preaccepted canonical projection (world/revision)
from physical-first farmer work. Only the former can retire after its exact
current canonical target and fresh loaded-world observation; farmer work still
requires accepted work/hand/lot receipt. Native `field-turns` 10/10 and focused
witness JUnit pass (2026-09-24 audit receipt). This is not active projection.
An inactive one-CellId growth projector now writes only accepted canonical
farmland/wheat age changes, with before-effect persisted origin, real loaded
postcondition, same-cause acknowledgement and two saved-pending re-entry
paths. Native 10/10 includes normal growth, no-op repeat, crash-shaped pending
and missing-crop negative. Active scheduler/HOT/conflict/output still use
stage+prefix; the projector does not address old COLD epochs or full-image
SavedData fsync scaling.
Its canonical-first admission now also requires a matching real loaded-world
predecessor review; synthetic comparison alone cannot authorize a write.
Main/pilot compilation and the subsequent native `field-turns` 10/10 passed
(2026-09-24 12:01 server log). This remains inactive and partial.
Source cutover review: registry projection, legacy growth/recovery, HOT harvest,
player/explosion/conflict, native growth pre/post fence and terminal output all
still consume stage/prefix `Claim`; a cell-owned claim is mutually exclusive.
They need one coordinated live-runtime-backed switch, not an isolated projector
call. Native crop-growth pre/post fence in particular must change owner before
cell-field activation. See the dated SA-09 audit receipt.
The inactive growth projector now also has a live admission method that reads
the registered server runtime's current canonical world/revision and rejects a
foreign dimension/runtime before invoking its internal cell executor. Main,
pilot and test compilation passed; no production registry path calls it yet.
The native field-turns slice passed 11/11 after correcting an invalid test
fixture assumption about the ordinary GameTest server's dimensions. The new
negative covers foreign dimension and unregistered runtime owner; it is not a
positive live-WAL/current-world acceptance test. Prior failed and passing
disposable runs are preserved under the SA-09 preflight build directory.
The cell-owned native-growth pre/post path now selects the sealed SavedData
claim variant instead of calling the legacy claim API and throwing. It vetoes
growth; a bounded same-tick snapshot restores only an exact one-age forced
increment with the same owner and intact soil. Native `field-turns` 11/11 passed
(2026-09-24 12:27), including real undo and changed-soil refusal. Active
player/conflict/HOT/output paths remain old; no full cutover or deployment.

SA-07's original stale volatile health-sample overwrite is CLOSED by current
release-path source review, 17/17 departure/consumption JUnit, and the retained
player attack→19 HP→graceful-restart scenario. This does not close SA-06 or
cross-file crash/return continuity; see the scoped audit receipt.
SA-06: actor birth permission is issued before canonical birth publication;
the common first-admission ledger covers bootstrap, scheduled resident birth
and scheduled hive-growth bioform birth. Ordinary ambient materialization uses
the pending permission and reuses the exact body. Adoption/save callbacks
retain pending physical evidence and check current owner. Remaining acceptance
includes WAL/SavedData/entity-region hard-crash windows, pending/late body
return, cargo/scene/ambient handoff composition, historical missing-actor
repair and client observation. Do not treat graceful restart as hard-crash
proof or a local absent body as permission to recreate one.
The transaction constructor now rejects mismatched event envelopes and
duplicate event IDs before actor-birth permission is persisted; focused kernel
and NeoForge tests passed (2026-09-24 static-audit receipt). This does not
close cross-file crash windows.
The wider module run was stopped after an independently reproduced unrelated
assault-test fixture failure (inactive production task); no broad-green claim.
Source review on 2026-09-24 isolated the remaining first-body hard-crash seam:
PENDING is durable before `addFreshEntity`, but a crash before the exact entity
region save can leave no saved body. Local empty-column inspection is not global
absence, and the existing pilot offline recovery explicitly excludes any
first-admission record. The audit records the required separate proof-backed
offline re-arm and negative/re-entry cases. No runtime retry or hard-crash
closure has been claimed. Production release must be tested through its actual
generic path; the `fenceDrainingSceneBody` helper is test-only today.
The separate pilot-only proof-backed offline re-arm is now implemented for an
exact still-PREPARED ambient resident or active bioform with no work/scene/
transfer owner. `PROVEN_ABSENT` is a new typed first-admission phase, not a
reset to `NEVER_CREATED`; only the same binding/UUID can retry. The stopped-
world tool scans all entity regions under `session.lock`, fsyncs an immutable
absence/canonical/SavedData receipt, then atomically publishes the re-armed
SavedData. Failed proof-backed insertion returns to PENDING, requiring another
offline proof. Actual world-file and interruption-boundary tests, old-recovery
isolation, format-1 compatibility and active-bioform model tests pass; see the
dated audit receipt. The subsequent native `scene-departure` slice passed
21/21, including one real Villager consuming the typed re-arm with the same
UUID and no duplicate; that fixture does not run the offline scanner itself.
The previous task-owned GameTest world was moved intact to
`build/runs/frontier-v3-scene-game-test-before-proof-rearm-20260924` before
the new run. No existing player world was operated on and no real process-kill
test was run. SA-06 remains OPEN for killed-process composition and HOT/scene/
work/cargo continuity.
The stopped-world absence scanner subsequently gained `level.dat`, optional
`level.dat_old` and playerdata NBT traversal plus proof hashing, while still
requiring an entity-region file. The focused offline JUnit selection passed
16/16 (six suites, 2026-09-24). This pilot-only scanner hardening was after
the native run; do not count it as a combined hard-crash/runtime proof.
An empty `.mca` is now rejected as a truncated header rather than counted as
scanned entity storage; the focused four scanner tests passed after the change.
Source review found another SA-06 gap: `setRemoved(UNLOADED_TO_CHUNK)` records
a scene-departure witness, and generic release may consume it without an exact
entity-region write+sync confirmation. The separate entity-save observer does
not confirm scene departures. Do not fsync the early receipt and call it a
saved-body proof. The audit records the required confirmed/unconfirmed split,
restart disposition and actual generic release acceptance.
The actor half of that split is now implemented: a bounded observer matches
the vanilla entity write (which precedes `setRemoved`) to the unload receipt
only after the completed pass, successful write and storage sync. Format-6
SavedData distinguishes confirmed from raw departure; format-5 receipts load
unconfirmed. Generic release waits for a current unconfirmed actor receipt,
and exact return can still withdraw it. Focused 16/16 JUnit and native
`scene-departure` 21/21 passed; the native slice did not exercise the full
vanilla unload→save→confirmed-release path. The prior native run directory was
preserved as `build/runs/frontier-v3-scene-game-test-before-departure-save-proof-20260924`.
The cargo half now also separates unload observation from saved proof: a
bounded typed cart snapshot from the entity write is matched to the exact
receipt (UUID, lease/cargo/revision/epoch, position and 27-slot inventory)
only after write/pass/sync, then synchronously published in format-2 cargo
SavedData. Format-1 raw receipts load unconfirmed; generic release waits for
unconfirmed current cargo. The focused cargo/scene departure and release
JUnit selection is green 49/49 (2026-09-24); see the audit for exact scope. This
is followed by native `scene-departure` 21/21 with a real nonempty chest
minecart NBT decoded to the exact receipt; the fixture still synthesizes
unload/return. The prior task-owned run was preserved as
`build/runs/frontier-v3-scene-game-test-before-cargo-saved-nbt-20260924`.
This does **not** prove a real chunk unload through the native
save/release path. Post-save/pre-marker crash recovery and combined native
release proof remain OPEN; no deployment or product acceptance follows.
The actual `storeChunkSections` source shows the entity write is scheduled
before all final-unload callbacks, then the method returns. Raw scene/cargo
observations now publish once at that completed chunk boundary (not per
entity); write/sync confirmation remains separate. Compilation, focused
49/49 JUnit and native `scene-departure` 21/21 passed after the mixin change;
the prior native run was preserved as
`build/runs/frontier-v3-scene-game-test-before-chunk-raw-publication-20260924`.
Static restart review found an additional SA-06 obligation: every active
scene becomes UNKNOWN, while current reclaim waits for player demand and
all historical columns. Exact no-load/stopped-world inspection plus a typed
local recovery disposition is still needed for unattended COLD resumption;
the audit records the negative cases. This is not yet implemented.
The underlying no-load provider is now present: it synchronizes the vanilla
entity-storage worker, reads one named region chunk without loading entities,
and selects bounded typed actor/cart snapshots while retaining malformed
target presence. Two focused selector tests and native `scene-departure` 22/22
passed; the native test proves the mixin/provider read leaves a remote chunk
unloaded, not that it recovers a graybox scene. Prior task-owned world:
`build/runs/frontier-v3-scene-game-test-before-stored-read-20260924`.
Binding this evidence to current raw receipts, excluding newer returns/writes,
and deciding the whole scene after restart remains undone.
The returned-entity read path now has a pre-deserialization durable fence:
matching retained scene/cargo UUIDs mark a return read before vanilla receives
the stored NBT; an exact re-unload revokes old save confirmation. New ledger
formats 7/3 retain read-fence coverage, so pre-fence legacy receipts cannot
silently qualify for no-load recovery after a format rewrite. Focused actor/
cargo tests pass 18/18; no-load recovery is still not connected. Future release
must serialize against in-flight entity reads, check exact current ownership
and stored contents, and retain UNKNOWN on ambiguity. No physical-dimension
killed-process proof, deployment or SA-06 closure follows from this increment.
The next source increment tracks in-flight vanilla entity reads per chunk and
adds exact typed snapshot-to-receipt matches for offline inspection; focused
stored-inspection/actor/cargo tests passed 21/21. The reservation is not yet
consumed by an UNKNOWN-scene offline recovery decision, so unattended COLD
resumption remains open. No native physical restart claim follows.
The entity write entry now increments a bounded per-chunk generation; no-load
snapshots carry that stamp and reject newer writes, index overflow or an
in-flight vanilla read. Focused write-stamp/read-reservation/stored-inspection
tests passed 5/5. Static review raised a non-local SA-06 question: a receipt's
expected entity column alone cannot exclude a duplicate UUID elsewhere in
saved storage. An interrupted-move duplicate has not been reproduced or
established on the actual production path. Do not call it a defect or continue
census expansion without a discriminating case or contract-driven consumer.
A single positive snapshot still does not prove whole-world uniqueness.
The bounded census provider now enumerates occupied vanilla entity-region
columns (64 region files/8192 columns maximum), synchronizes the I/O worker,
and scans in 64-column no-load batches for up to 256 UUIDs. Global entity-write
generation and any-pending-read flags support later commit-time freshness.
Focused header/selector/batch/uniqueness tests passed 5/5; main/pilot/test
compilation passed. It is not yet wired into whole-scene UNKNOWN recovery or
native physical-dimension evidence; the latter and SA-06 remain open.

Next deliver one active SA-09 field/worker cycle through harvest, custody and
terminal output, correcting demonstrated source contradictions together. Keep
unsupported layout revisions rejected until their explicit transition and
obligation retirement exist. Then return to SA-06's explicit recovery and
identity acceptance through the actual vanilla path, choosing the minimum
evidence that discriminates remaining risks; do not mistake inactive
primitives or narrow stopped-world repair for closure. Main runs a native
client after a coherent source path, not by asking the user to debug routine
defects.

Latest SA-09 native evidence (2026-09-24): disposable `r6` entered HOT,
confirmed more than 61/64 cell harvest receipts, then hit a real depot-route
clearance conflict at support `(135,63,13)`; saved chunk has depot access
`yellow_concrete` at the worker-feet cell `(135,64,13)`. The route surveyed
natural terrain beneath a materialized service floor. Shared public-access
support heights now feed the pedestrian survey, with a seed-47 focused route
regression passing. The same conflict exposed a second invariant breach:
canonical DRAINING would generically release a worker still holding bound
wheat and quarantine the runtime. Bound-hand scenes now stay in local CONFLICT
pending typed custody disposition; focused model test and NeoForge compilation
pass. Fresh native `r7` then completed the first HOT crop/depot leg and
confirmed the physical delivery intent; the field entered growth epoch 2
and reached stage 2 without conflict. Its disposable runner failed at an
obsolete exact-item successor assertion (the new wheat is a fungible lot),
before its chest/screenshot steps. The saved same-run depot chest contains
64 bread in slot 0, whereas the earlier action-5 diagnostic listed only four
tools. The scenario now checks physical bread without the stale exact-item
predicate; it has not been rerun. Container diagnostics now disclose typed
fungible bindings separately from exact occupied slots; focused diagnostic
tests pass. One physical harvest-to-bread path has positive evidence, but
restart, intervention, repeated player-visible cycles and SA-09 closure remain
open. The dated static-audit receipt has exact failure and proof boundaries.
The player crop/soil cutover previously lacked an active producer: the
cell-owned native break path rejected it despite pure model reducers. The
required four transitions were canonical prepare, post-Vanilla observation,
cell SavedData witness and restart reconciliation; the dated audit retains
that before-state. Never infer removal from START_DESTROY_BLOCK or regress to
whole-field conflict. Avoid a full native run merely to reprove the already
observed first harvest-to-bread cycle.

2026-09-24 SA-09 player-crop increment: the active crop break now uses that
canonical-before-physical protocol and an exact per-site SavedData action
witness. A stale but owned first-ingress crop receives its one-cell canonical
growth projection before permission; active/delayed Vanilla mining is read
from game-mode state, not expired by time. Focused JUnit and compilation pass.
One disposable ordinary-client scenario on private port 26575 is green at
`build/sa-player-crop-continuity/manifest.json`: server accepted a typed crop
observation at revision 544, the crop became AIR, site 4 remained growing and
healthy, and graceful save/restart returned to a healthy site. The final
scenario did **not** assert the exact crop block after restart; add that to
the next coherent intervention verification rather than rerunning only for
confidence. Non-yielding harvest continuation, dirt repair, arbitrary layout
revision and crash recovery are still open. No deployment or SA-09 closure.
Static follow-on review found the current 64-cell missing-crop path coherent
through planting, no-yield hand preservation and partial terminal delivery,
but it is not yet physically proven to terminal output. Arbitrary 65+ cell
fields remain a source-proven batch-streaming gap: the HOT path counts total
harvested units into one hand and the terminal adapter rejects quantities
above 64. Implement intermediate bounded delivery/return with one stable
part identity and receipt before accepting larger layouts; a model-only 129-
unit yield test does not close that producer.
The next ordinary-client partial-cycle run actually broke the last crop,
confirmed the exact AIR block after graceful restart, advanced the unloaded
field 24,000 ticks and observed site 4 healthy in growth epoch 2. Its saved
Anvil chest contains exactly 63 wheat at `(377,65,-326)`, matching the
current fungible binding; the previously removed crop block is saved as
wheat age 3 in the next epoch. The player loss did not mint a 64th unit. The
runner's final container oracle was red only because it searched legacy
exact items, not fungible slots. That matcher now requires a current replica
and current owned physical chest for fungible matches; focused JUnit passes.
The second native manifest remains **failed**, not green, and no third
full-world replay was spent merely to recolor it. See the dated SA-09 audit
for both failure bundles and exact proof limits. SA-09 stays OPEN, notably
for 65+ streamed batches, dirt/obstruction, crash seams and formal product
acceptance; do not deploy or call this a complete field implementation.
Follow-on static review corrected a historical false lead: active bread
production already selects/reserves multiple exact wheat lots. A focused
63+1 selector/claim regression passes; 63 alone correctly awaits more wheat.
Do not rewrite production for a single-lot defect that is absent. The next
real SA-09 source seam is durable bounded route/batch continuation: the active
job stores one full corridor, the topology caps at 4096 nodes, and the HOT
hand/terminal delivery caps at 64 units. Retain revision-2 rejection until an
actual segment, delivery/return and layout-revision protocol is wired.
The latest static check also confirms that model-level local soil/crop loss has
no general active NeoForge observation producer: ordinary managed-soil reversion
is blocked, player crop break has a narrow prepared producer, and other
cell-owned breaks are rejected. Do not call dirt a reproduced ordinary defect
or submit a world observation from an unexplained mismatch; a physical-first
durable cause and crash reconciliation are needed before enabling that path.
The next batch increment is wired into the active job, not yet into a delivery
transition: `deliveredYieldQuantity` is retained through WAL/snapshot (envelope
84 / state 195) and all active crop/hand/terminal computations use its bounded
offset. Fresh jobs still begin at zero; there is no producer that advances the
cursor, no intermediate depot/return route and still only a 64-cell admission.
Selected focused tests and NeoForge compilation pass. Five broader old harvest
tests still assert the retired exact item or an invalid >64 hand; see the dated
audit receipt, and do not present that suite as green or this source step as
arbitrary-field delivery. Next implement the atomic intermediate full-part
handoff, retained return route and actual 65+ admission together before any
native/client claim.
The job cursor now has an aggregate guard against outrunning actual yielded
cells or retaining an over-capacity hand; focused snapshot/negative JUnit
passes. Static tracing confirms a 65-cell admission would still fail: the
current route visits all crops before the depot, while a nonterminal part is
ready at 64 actual yields. The next coherent cut needs bounded route segments,
one physical/canonical intermediate receipt, atomic reserved-slot migration
and continued work from the next CellId. The topology's 4096-node cap also
requires segment continuation across long non-yield stretches. Do not relax
the 64-cell gate in isolation or mistake this guard for streamed delivery.
The subsequent source WIP now has segment ranges/exact crop route cursors,
COLD/HOT segment renewal, a full-hand return, a reserved successor chest slot,
and COLD/HOT intermediate 64-unit transfer reducers. The native delivery
witness carries the part boundary and reserves successor capacity before its
physical effect; a PREPARED COLD-worked hand joining HOT at the depot now first
enters RUNNING. Snapshot 197 / WAL envelope 86 / site SavedData format 13
reject previous layouts; focused current-64 harvest, persistence and adapter
unit selections and Java compilation pass. **This is not player-ready 65+
support**: `ResourceSiteState.validate` still pins bootstrap geometry and the
active harvest admission gate still rejects non-64 revision-1 layouts. The
new batch path has no native/physical/restart proof; HOT cannot abandon a
bound offhand to COLD if depot delivery waits. Next establish an authoritative
layout-revision/retirement producer, then test the actual 65+ batch and
capacity/recovery seams before claiming SA-09. No deploy or goal closure.
The domain transition and job value now reject crop preparation/progress or a
persisted pending crop before the farmer reaches its exact retained workstation;
focused positive COLD and negative model tests pass. This removes a source-
confirmed admission gap, not the revision/physical-claim cutover.
Further cross-owner review exposed an impossible intermediate HOT publication:
the initial batch reducer wrote its receipt into the terminal-only physical
observation map while the intent remained RUNNING. It now keeps the full receipt
in the WAL event and a bounded last-confirmed batch on the active job; the
native witness retires against that exact job/part/receipt/witness/fingerprint.
Snapshot 198 and envelope 87 retain the new field. Preparation receipts also
use actual bounded layout counts (not literal 64 or byte-sized counts), with
canonical and immutable-bootstrap checks. Focused tests and Java compilation
pass. The batch reducer still lacks an admitted 65+ HOT/native run;
do not call the player path working or deploy. The canonical layout revision/physical
claim handshake remains the next substantive SA-09 boundary.
An explicit revision-1 fresh-field bootstrap manifest now permits a 65-cell
source aggregate without weakening the revision-2 guard. The manifest is
bounded, overlap-checked, snapshot-persisted and pinned at recovery (snapshot
199); the default 8×8 genesis remains unchanged. Harvest admission/rebase use
the bounded segment plan for non-default initial layouts and retain the old
route for default graybox. A 65-cell COLD aggregate test completes two parts
(64+1), snapshot-recovers between them and proves depot quantity increases by
exactly 65 from its pre-existing balance; foreign pinned genesis fails.
An unreachable authored first workstation now blocks only its harvest task
instead of throwing from scheduled route compilation; its negative passes.
`ResourceSiteHarvestProcessTest`, `FrontierPersistenceCodecTest` and Frontier/
NeoForge Java compilation pass. This is no native 65+ claim: active server
genesis still defaults to 8×8, HOT physical handoff, capacity pressure,
revisions, crash seams and player continuity remain OPEN. The first 129-versus-
65 test assertion was a mistaken failure inference: 64 was already in depot.
SA-06 static review also found a reachable no-visit route-patrol loss: after
restart, timer-only `SceneLeaseRecoveryRevoked` closes a previously HOT scene
without reading a saved mob's health, and the late-body firewall rejects that
physical evidence. Nonfatal Vanilla damage has no V3 post-damage canonical
receipt; ordinary release would have captured the changed health. The dated
audit gives the exact code path and correction boundary. Do not use elapsed
time/no demand as physical absence proof; no-load whole-member evidence and a
typed handoff are needed without sacrificing unattended COLD liveness. This
finding is source-confirmed but not yet fully repaired or native-proven.
The unsafe *new-write* path is now contained: the patrol executor does not
submit a timer-only revoke, and command planning rejects one; historical
replay reducer/codec remain. The active patrol no-demand path now consumes
exact saved-departure/read-fence markers, bounded global UUID census and
typed no-load body snapshots with health, rechecks write/read freshness and
canonical owner, then transitions UNKNOWN→DRAINING and calls generic release
for a complete set. Missing/changed/duplicate evidence stays locally UNKNOWN;
two scans maximum prevent a retry storm. Focused patrol and no-load proof
JUnit plus NeoForge compilation pass, including canonical 18-HP release.
This remains unaccepted source composition: actual unload/restart, hard-crash
and generic scene/cargo recovery are not proven. SA-06 stays OPEN; do not deploy.

SA-09 continuation (2026-09-25): a held-clock 65-cell full native run
physically completed and delivered its first 64-unit batch, then stalled at
the final segment's first retained edge. A reducer-derived post-first-part
fixture reproduced the stall quickly. Direct diagnostic, live body position
and saved blocks proved the exact mismatch: support grade one down from depot,
but a 0.125 local lift made the physical foot delta 1.125, which both motion
gates rejected before descent. A bounded exact-retained-edge correction and
focused negative/positive JUnit pass. The focused native retry is green:
same farmer handled physical cell 65 and terminal handoff; depot reported
OBSERVED_CURRENT wheat slots 64+64+1, and site grew into epoch 2 without
conflict. See the dated SA-09 audit for evidence paths and limits. The final
camera-only retry is also green and shows the field/settlement on before and
terminal frames; a prior lifted-camera attempt failed its exact-visit test
admission and is not product evidence. All task-owned disposable server/client
processes stop through the isolated runner.
This does not close SA-09: no production deploy, player acceptance, 65-cell
hard-crash proof, or layout-revision migration yet. Do not repeat the long
first-part run just to prove the already-observed 64-unit delivery.

SA-06 continuation (2026-09-25): shared no-demand stored-body recovery now
supports eligible cargo-free UNKNOWN scenes, not just patrol. A production-work
native graceful-restart case independently proved the saved worker's 19-HP
body, released its lease before the player rejoined, and returned 19 HP to the
canonical actor (`build/sa06-stored-worker-health-restart-zero-region-fix.json`).
The fix also recognizes vanilla zero-byte empty entity-region placeholders;
nonempty truncated headers still fail closed. See dated static audit for the
failed-method/defect sequence and bounded proof conditions. SA-06 remains OPEN
for hard-crash, cargo, other families and race coverage; do not deploy.

SA-06 cargo continuation (2026-09-25): the bounded no-demand stored-scene
proof now covers a whole logistics lease (all living actors plus exact saved
cart); the cargo saved marker and cleanup witness remain separately required.
Unit negatives pass. One native graceful restart is green: no-player release
of `lease:supply-1-11-r112` with three actors/cart at 01:11:22, before player
join at 01:11:33; after join scene CLOSED and same operation continues EN_ROUTE
at route index 1 (`build/sa06-stored-cargo-restart.json`). The first native
attempt failed its incorrect feet-Y expectation before restart, not product.
SA-06 remains OPEN for crash/race and remaining family/stage coverage.

Further SA-06 review found no-demand UNKNOWN also ignored a complete *loaded*
actor/cart set. The branch now reuses the exact loaded reclaim validator after
grace, then falls back to whole saved proof; missing members never qualify.
The exact `scene-restart-reclaim` GameTest selector is 2/2 green. The broad
scene slice was inadvertently selected first and deliberately stopped after
unrelated failures/slow tests; do not rerun it for this boundary. Mixed
loaded/saved sets, interrupted cargo, crashes and read/write races remain open.

SA-06 mixed composition (2026-09-25): no-demand proof now partitions exact
loaded vs saved actor/cart UUIDs, requires full disjoint coverage, current live
ownership and disk proof only for stored targets. A decided scan rearms only
after a changed partition or relevant entity-write epoch, paced at ≥20 ticks;
time alone cannot grant proof. Focused unit tests pass. Current-source native
fully-stored cargo regression remains green (`build/sa06-stored-cargo-mixed-regression.json`),
with release before player join and COLD route index 1 afterward. A purposely
mixed native case is not yet evidenced, so SA-06 stays OPEN.

SA-06 abrupt continuation (2026-09-25): an isolated production-work worker
probe exposed the no-player case where vanilla `save-all flush` had written a
still-loaded HOT body but abrupt JVM stop provided no unload callback/receipt.
The pilot's persistent branch now actually performs and records the verified
flush; an opt-in attack receipt waits for client-observed health loss instead
of merely packet submission. A bounded single-member, cargo-free recovery
path proves the exact saved UUID/owner/lease/health with entity-region census
and read/write stamps, then uses ordinary departure and release. Native
`build/sa06-stored-worker-abrupt-after-flush-r4.json` is green: original lease
released without a player at 01:51:26.870, player joined at 01:51:37.382, and
canonical resident `1-15` retained 19 HP. Focused JUnit passed. Earlier
attempts lacked the flush or confirmed damage; the prior 20-HP canonical HOT
reading was not proof of physical loss. This is only post-completed-flush,
one-body crash evidence; SA-06 remains OPEN for save-interruption, cart and
multi-member recovery and adverse races. After the green native run, one
source-only bound capped changed-evidence full-census retries at two per lease;
the final source compiles but that pacing-only delta has no second native run.
No deployment or commit.

SA-06 whole-scene abrupt extension (2026-09-25): the missing-unload-callback
path now proves an exact bounded saved UUID set for all absent actors and the
cart, then returns to the existing whole-scene custody proof. Native
`build/sa06-stored-cargo-abrupt-after-flush.json` is green after a confirmed
flush and exact JVM stop: three actors plus cart proved at 02:01:47.978,
original lease released at 02:01:48.103 before player join at 02:02:23.521;
same operation continued EN_ROUTE at index 2. A subsequent source-only exact
coverage guard passed focused JUnit, with no second native run. SA-06 is still
OPEN for interruption during writes, intentionally mixed physical partitions,
other scene families and adversarial races; current source is not deployed.
SA-06 PENDING first-body follow-through: the older audit statement that the
offline publisher rejects every first-admission record is stale. Current
pilot-only `OfflineFirstAdmissionRecovery` already requires a stopped-world
session lock and full absence scan, an exact PREPARED ambient PENDING owner,
and an immutable receipt durably written before the re-armed SavedData image.
Its crash-boundary retry, changed-input and present-body negatives are covered
by 15 focused JUnit tests (15/15 green). This is simulated offline-world
evidence, not a native killed-process recovery or proof for scene/work-bound
first bodies. SA-06 remains OPEN at that wider boundary.

## Evidence and operations

Current operational evidence is the superseding checkpoint above; the dated
older native/incident receipts remain in the archived audit. The current
server uses a new world because snapshot/envelope schemas changed; never
open the preserved older world with this JAR. Client graphical acceptance is
still pending and the latest deployment is diagnostic, not a release gate.
