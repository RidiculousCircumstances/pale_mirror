# F0.6R3 static audit and accepted remediation decision

Date: 2026-09-22. Status: SA-01/02/03/04/05/08/10 closed by scoped closure receipts below;
SA-06/07/09 and overall product acceptance remain open. Remediation accepted.
This is a supplement to `frontier-v3-architecture-audit.md`, not a competing
defect register or a claim of product acceptance.

Current source-first follow-through: [lifecycle contradictions and repair order](frontier-v3-static-lifecycle-followthrough-2026-09-23.md).
Confirmed new gaps: canonical health omitted at new-body construction (corrected,
focused verification), and family-dependent loaded-survivor release protection
(shared lifecycle correction implemented, user-testing checkpoint). Long-run acceptance is not a substitute for these
source-path checks; SA06/07/09 remain open.

2026-09-24 prepublication boundary correction: `TransactionRecord` now rejects
any event whose transaction/world/revision/instant differs from its enclosing
transaction, and duplicate event IDs within the transaction, at construction
rather than only when `RecoveryImage` replays the WAL. This prevents the actor
birth committer from persisting first-body permission for an envelope that
would fail later recovery. Focused frontier kernel/recovery/engine and NeoForge
birth/crash-probe tests passed; this is not hard-crash or full SA-06/07 closure.
The broader two-module unit run was stopped after five minutes once it exposed
an unrelated, independently reproduced assault-test fixture failure: its
production job references `task:assault-test-true`, which is not an active or
blocked production task (`FrontierWorldState.requireJobTask`). No broad-green
claim; the failing test and production invariant were not changed for this
prepublication correction.

## 2026-09-23: SA-08/09 fungible native scenario preparation

### Two-cycle product acceptance and observation lifetime correction

80033 TERMINAL0/OK19:10:12: all19 checked-in actions passed. Early graceful
restart -> first harvest ->64bread in real opened chest -> second growth and
harvest with same resident7-31 -> second exact outputslot1 CONFIRMED/resolved/
owned -> epoch3GROWINGstage0, finalsummary requiredConflicts0. Manifest
`build/sa-two-harvest-soil-trace-20260923/result.json`, run0b3b0d04-6e1f-4216-
891e-3e2f1064405d, world5ec4c216, preparedartifactSHA256
db7e67d44752a3ab3b7ae653346307fb05bc734b8e9b19fd5b188504209633cc. Client/server
terminal and portsfree. Actual bread64 and young-field/board frames opened;
distant board and still frames are not full M3/continuous-motion acceptance.
This supports early-admission and second-receipt corrections in the full flow.
No soil-change event occurred: it does NOT resolve unexplained dirt mutation82544.
No further confidence rerun of this two-cycle case planned. Remaining health/
custody and soil-attribution work remain explicit; no overall audit closure.

Focused hypothesis check8878 terminal0/26s: new native harvest-support case
`tendingThenLeavingHydratedFarmlandDoesNotInventTrampling` exercises ordinary
entity-tick gravity/tending, crop removal and exact retained exit to adjacent
full-block road. All110 simulated ticks preserve hydrated soil and worker reaches
the road. This simple transition does not reproduce82544's dirt change; no
motion repair inferred. Mixin startup succeeds; actual managed-world trace still
needs its event. Product repeat80033 started under `sa-two-harvest-soil-trace-20260923`
with same scenario and new attribution, specifically to identify the dirt writer.

82544 terminal1/failed18:43:37 at successor wait; isolated ports subsequently
free. Stopped-region read confirms four irrigation WATER sources (139/143,63,
-7/2), adjacent soil moisture7, damaged144,63,-6 DIRT with AIR above64/65.
This does not prove historical mutation cause. Added read-only native attribution
at FarmBlock.turnToDirt TAIL: managed physical-world soil emits PMV3_SOIL_CHANGE
with actual before/after, caller(randomTick/tick/fallOn or unknown), site/cell/time,
nullable causing entity UUID/declaration/position/fallDistance and motion status.
No cancellation, restoration or canonical mutation; compile45540 passed9s.
Runtime injection/trace not yet verified and cannot explain the old event
retroactively. Next diagnosis must attribute mutation before a behavioral repair.

82544 follow-through: first post-restart harvest completed18:40:36, but ordinary
regrowth entered local site CONFLICT18:40:45/rev6811 at144,63,-6, a soil cell
below the last crop. Reason OBSERVED_MANAGED_CELL_MISMATCH; claim stage7/h64,
ADVANCE tostage0/h0, cursor8/64. Exact guarded read-only vanilla block checks
on disposable world4a11fff5 confirm DIRT, not FARMLAND or AIR. First receipt is
CONFIRMED/resolved and outputOwned; this differs from the earlier second-output
receipt collision. Soil mutation cause remains unproven (vanilla drying,
overhead obstruction or entity trampling); inspect actual surroundings/actor
motion rather than hiding damage or restoring owned soil without attribution.
No second-cycle acceptance. Runner still awaiting its bounded successor result.

Current product82544 passed the earlier stuck boundary: after graceful restart
and client resume18:36:35, resident7-31 retains its deterministic UUID and has
an observed SCENE_OWNED body, HOT scene/RUNNING work. Diagnostics advance8->22->37
of64 crops (latest rev6232/t28352). No terminal two-cycle claim yet. Opened actual
af2fe09c harvest-start-field PNG: mature field visible in daylight; board is
clipped and working-farmer motion is not established by this pre-restart still.

SA07 source/coverage review: shared release has no LAST_OBSERVED fallback;
harvest and production delegate to it after custody preparation. Present bodies
supply current health; absent bodies require a validated exact SceneDeparture
(actor/kind/UUID/scene/revision/baseline), persisted fencing precedes release.
Current selection16477 terminal0/13s:18 tests (departure6, consumption6,
readiness6) pass, including changed9HP SavedData recovery, stale/wrong-kind/
epoch/baseline refusal, absent history and both entity-column readiness gates.
Evidence `sa-two-harvest-unstarted-recovery-20260923/departure-closure-regression.log`.
Existing native `departedSceneBodyRetainsFinalHealthAndBlocksDivergentReturn`
tests actual9HP body/NBT/callback/return, but explicitly excludes canonical
release because fixture positions are outside domain. The model separately
checks released7HP. This is complementary evidence, not an end-to-end native
health release/restart claim; that composition gap remains to assess/cover.

Unstarted-admission correction implemented in shared `SceneExecutor.reclaim`:
after natural demand/entity-storage gates and observation interval, non-draining
recovery may invoke the ordinary actor factory only when whole-member preflight
finds exact owned living bodies or explicit unused matching first permissions.
Absent PENDING/ESTABLISHED/unknown history, foreign bodies, ambient handoffs,
retained adoptions/handoffs/carriers and dead members cannot authorize the path.
The existing first-admission boundary persists PENDING before insertion; no cargo
is recreated, and promotion still requires observed owned bodies plus cargo's
independent exact check. No new lifecycle status or duplicate creation fallback.
Compile74860 passed7s. Native99108 terminal0/24s: four first-admission cases pass
(resident/bioform, ordinary/recovered); missing history rejected, exact inserted
body reused, absent attempted body cannot regain unused permission. Log:
`build/sa-two-harvest-receipt-fixed-20260923/unstarted-admission-native.log`.
These fixtures test physical initial-admission policy, not full process restart
or mixed-member acceptance. Full current-source two-cycle scenario started at
`build/sa-two-harvest-unstarted-recovery-20260923`; outcome pending. Live unchanged.

Saved-world follow-through (read-only, no server launched): decoded compressed
NBT `dimensions/pale_mirror/frontier_graybox/data/pale_mirror_frontier_v3_ambient_carriers_ZnJvbnRpZXI6Z3JheWJveA.dat`
in world361dae82. Format5 retains resident:7-31 as RESIDENT,
UUID ef562345-8f47-37ec-af28-d12c259ab948, first-admission phase `never_created`.
All carrier/adoption/handoff/departure collections are empty. The restart
therefore exposes an unstarted first admission, not evidence of a previously
created farmer being lost. Shared `SceneExecutor.reclaim` only accepts already
observed bodies; it cannot consume the explicit unused permit. The ordinary
shared materializer already has a durable permit-before-insertion boundary.
Required correction: distinguish permission-backed initial admission after
restart from observation-only reclamation, through the shared scene owner.
Do not infer permission from absence, reset PENDING/ESTABLISHED history or
authorize cargo reconstruction from actor permission. Exact identity, natural
entity-storage readiness, mixed-member safety and negative recovery coverage
remain required. Root cause localized; implementation and acceptance still open.

Corrected-candidate run56550 failed before second receipt, at first harvest
post-restart18:21:36: semantic observer detected1203 canonical ticks without
progress. Last diagnostic rev3431/t22438: farmer7-31 ALIVE, canonical120/64/28,
no indexed observed body, physical admissionREADY; job PREPARED/cursor2/0cells,
sceneUNKNOWN_AFTER_RESTART. No quarantine. Server saved/stopped18:21:37; world
361dae82 and failurebundle retained in `sa-two-harvest-receipt-fixed-20260923`.
This is not evidence that second-receipt fix failed; it exposes an earlier
recovery/admission case which must be diagnosed before retry. Source common
reclaim reads existing bodies only after demand/load/entity-storage readiness;
saved first-admission/adoption history and exact recovery evidence must be read
before classifying never-created versus physically missing. New camera action
does not wait for an actor, so earlier restart timing is plausible but not proof.

Receipt fix native follow-through:57905 terminal0/27s,3 field-turn tests pass.
The bounded successor fixture now contains the predecessor's actual ledger
receipt before regrowth: pending/foreign cases retain it, stage2 ADVANCE and
mature reverse restore retire it, SavedData reload preserves retirement.
67988 terminal0,13 physical-ownership-fence tests pass, including new
`secondHarvestAfterRegrowthWritesItsOwnOutputWithoutReplacingTheFirst`: real
crop/chest first output, ledger reload, nonmature regrowth, second maturation and
distinct exact output in another slot. First output unchanged, second complete
postcondition true, repeat cannot mint again. This tests physical adapter
composition, not worker duration or canonical scheduling.
Evidence logs `receipt-bounded-regrowth-native.log` and
`second-physical-receipt-native.log` in prior failed run directory. Updated product
run56550 is under `build/sa-two-harvest-receipt-fixed-20260923`, still pending.
Scenario action3 targets field rather than nearest matching resident, and daylight
is fixed for readable frames; no acceptance condition or simulation cadence changed.
Live unchanged; no overall SA closure inferred.

The corrected native run6056 FAILED on a real terminal lifecycle defect at
18:09:56 (world5bbca5f8). First harvest/restart/64bread/regrowth passed; second
growth reached maturity, same farmer7-31 harvested again. At second output write,
ResourceSiteLedger.recordHarvestReceipt rejected the new output against the old
site-keyed receipt and quarantined the instance. Stack: HarvestExecutor.apply320
-> completeRunning232 -> execute200. This is not a test timeout or missing worker.
The final client timeout is a consequence of the already quarantined runtime.

Source root: mature successor restoreOne retires the old receipt, but ordinary
nonmature ADVANCE regrowth only called updateStage and retained it. Shared-owner
fix retires the predecessor receipt when leaving mature stage for new nonmature
growth, after validating the next claim. Same-stage retry preserves the receipt.
New ResourceSiteReceiptLifecycleTest covers two distinct harvest outputs across
NBT reloads, duplicate idempotency, forbidden same-epoch replacement, premature
receipt and invalid-stage no-mutation.12452 units green10s;6487 revised negative
case selection green. Native regrowth/second-output verification remains pending.
No recovery overwrite or live deployment. Failed native result/world retained.

Extended `disposable-sa-harvest-restart-successor.json` from15 to19 actions:
after the existing restart/harvest/bread story, observe increasing growth stage,
wait for HARVESTING epoch2, independently observe its real work, require GROWING
epoch3 with resolved physical receipt and no required conflicts. Declaration
SHA2566087764d0283c62aa06b8aa725503db08ea0c4f86c6ab58054422eaacbe94f66.
Initial schema rejected an extended wait without its existing progress predicate;
added `requireIncreaseAt:growthStage`, not a schema/timeout relaxation.

Source review before the second cycle found the graphical client's
`harvestSemanticOracle` survived `advance()`, including its prior PASS and bound
job. It now resets at every action boundary. New
`FrontierV3HarvestObservationScopeTest` invokes that real boundary twice with a
completed prior observer and verifies no retained result;17144 terminal0/8s.
This is a test-method defect, not a farm runtime fix. Existing one-observation
scenarios are not invalidated by this specific defect; two observations within
one connection could not previously be accepted independently.

10555 intentionally stopped before client entry after discovering this defect:
exact isolated server3488158/world2993e545 received ordinary RCON stop, all
dimensions saved17:49:51; then owner-controller3487117 terminated (exit143), ports
25585/6 free. No product acceptance. Logs/world preserved under
`build/sa-two-harvest-cycles-20260923`. Corrected candidate run is under
`build/sa-two-harvest-cycles-oracle-fixed-20260923`; pending, not accepted.

### SA-06/07: first-admission pending restart across actor kinds and owners

Native follow-through:38961 terminal0/56s,18 scene-departure/death tests passed.
`pendingFirstBioformRejoinsRecoveredRuntimeWithoutAnotherCreation` shares the
existing resident recovery composition but uses the initially deployed scout.
It creates the actual Zombie through runtime-aware ambient admission, retains
its pending first-admission receipt on disk, reopens the canonical file-store,
reloads SavedData and real entity NBT, rejects recreation during loaded absence,
then accepts the exact returning UUID without healing7HP or teleporting. This is
component recovery within Minecraft, not process-crash/region-write atomicity.
Initial86739 failed only the new fixture:west-0 is cocoon-retained and correctly
cannot receive ambient authority. Corrected fixture uses the awake west-1 scout;
no product rule, timeout or acceptance weakening. Both worlds/logs preserved.

74045 terminal0/26s:2 native first-scene tests pass (resident and awake bioform).
An empty column without history cannot create a body; explicit fresh bootstrap
issuance permits one exact scene body, retains PENDING rather than fabricating a
save acknowledgement, and a repeat reuses the same indexed entity. These use the
existing isolated `materializeBodiesForFixture` entry, so they prove the shared
scene creation implementation, not asynchronous entity-storage readiness or
canonical scene admission. New `first-admission` slice selects only this batch.
Logs: `build/sa-population-recovery.WsGyXG/first-scene-admission-native.log` and
`bioform-first-recovery-corrected-native.log`. Both servers saved/stopped; no live
deployment. Dynamic birth-to-waking-to-native-body composition and crash/product
acceptance remain open; bootstrap bioform is not proof of those flows.

Added `pendingRestartRequiresExactLateBodyForBothKindsAndInitialOwners` to
`FrontierV3ActorFirstAdmissionLedgerTest`. It exercises RESIDENT/BIOFORM with
AMBIENT_LEASE/SCENE_LEASE, persists the pending intent before an insertion whose
outcome throws, reloads that exact saved ledger, rejects a second creation and
foreign owner acknowledgement, then accepts the exact late-save acknowledgement.
Another reload retains ESTABLISHED and cannot restore first-creation permission.
The deliberately neutral actor ID also avoids using a prefix to select kind.
Focused first-admission model/ledger selection19441 passed in7s; no production
change was required. This closes the cross-kind/initial-owner model regression
gap, not actual entity insertion, abrupt server restart or dynamic bioform native
acceptance. SA06/07 remain open. Live server unchanged.

### SA-06/07: historical population recovery on a separate stopped copy

Quarantine fence follow-through57899 TERMINAL0/31s:2 runtime units and12 native
board/crop tests green. The native post-growth override previously still required
ACTIVE and lost retained ownership on quarantine; now both lifecycle entry and
resource-site post-veto use the same retained safety state as the pre-veto. No
canonical commands or projection advancement are enabled. Native negatives prove
AIR and foreignSTONE are not replaced; forced wheat growth is corrected only under
existing exact claim. This is component composition evidence, not an actual
quarantined-runtime event-bus run. Evidence quarantine-post-veto-native.log. No
deployment/existing field-state rewrite. Source inventory confirms no explicit
field conflict resolution command; any repair must retain typed prior context,
first incident and retired work closure rather than infer phase from text.

Follow-through verification30705 terminal0:8 focused runtime/readiness tests and
11 native board/crop tests green. Corrupt startup leaves native-growth safety
view absent (no ownership fabricated); normal quarantine retains only read view.
Added exact board-return recovery after this slice: confirmed existing display
must match retained UUID, owner and position before clearing board conflict and
updating current text. No entity creation from absence; moved/foreign negative
behavior retained.75099 terminal0/29s:12 native tests green, saved17:28:24. New
fixture loads conflicted SavedData, observes the same retained display and checks
current conflict text/object identity. Not an asynchronous entity-region replay.
Logs ownership-fences-native.log/exact-board-return-native.log in recovery root.
Full quarantined-runtime crop event hook and existing-world field resolution
remain unverified/unimplemented respectively; no live deployment. Current field
CONFLICT has no found explicit resolution command; no status reset is authorized
by green board recovery. Next repair must preserve exact incident evidence and
avoid overwriting unclassified player/world effects.

Source follow-through on field/board symptoms: incident34818 trace records exact
active stage0 claim, no projection,63 age0 wheat plus1 age1 at139,64,0. No air or
foreign crop. After runtime quarantine, decodedState returned empty and native
growth veto therefore returned false. This demonstrable ownership-fence gap is
consistent with secondary growth during the earlier diagnostic quarantine; exact
historical random-tick causality is not independently proven. Added narrowly
named read-only stateForNativeGrowthFence, retaining validated engine state for
the pre-growth veto without enabling commands/projection.35762 regression green
11s confirms quarantine still rejects normal reads/writes and preserves identity.
Native crop-event/failed-startup/post-listener coverage remains pending.
Read-only NBT inspection57018 confirms site7 board conflict flag. Board executor
could mark disappearance after block chunk load but before entity storage load;
now shared entityStorageReady fences both creation and missing-body decisions.
60897 main/test compile green6s. No claim that this proves the historical board
race; exact old conflict cause was not retained. No existing field/board conflicts
cleared, no live changes. Both fixes still require affected native verification.

Population recovery acceptance39746 TERMINAL0: ordinary full-pack client observes
25/25 exact residents for60 client ticks. Born8/9 exact gen1 recovery12727 applied
and immediately replayed without duplicates,head35086->35090; full prior backup
retained. Visit x154 instead of medic column150 admits7-17 too. No runtime source
change or relaxed population assertion. Artifact unchanged bbe75ad9...; run
7edb571a-7027-45da-9b19-e8e554c2176a. Actual recovered-population PNG opened:
villagers/young field present; close bodies/road partially occlude field, no M2/M3.
Evidence ingress-complete.json/client-complete.log/server-population-complete.log
in recovery root. Server10393 gracefully stopped; client39746 terminal0.
This accepts population recovery, NOT farm closure. Terminal field diagnostic
CONFLICT/epoch5/stage0 at139,64,0, RESTART_RECONCILIATION and
OBSERVED_MANAGED_CELL_MISMATCH. Snapshot incident began34818 on prior run; latest
population repair did not create it. Harvest4 output retained but successor=false.
Next source/saved-cell investigation must distinguish lawful projection progress
from actual drift without overwriting unclassified physical facts. Screenshot
still reports GROWING2/7 whereas diagnostic reportsCONFLICT: presentation also
requires review. Read-only incident inventory64103 confirms19 retained terminal
entries comprise18 historical ambient absences plus this field incident, not19
new independent active failures. No live deployment, no full SA closure.

Changed-candidate continuation70849 (artifact bbe75ad9dae398243ee424c13664795958e109ee60c98928c576d389956af2c4)
resumed the same world, not fresh bootstrap. Prior diagnostic quarantine did not
recur; runtime advanced to35086/t180659 and saved gracefully17:14:11. Population
still FAILED22/25. Improved observer identifies7-17/7-born-8/7-born-9. Read-only
server census independently shows22 INDEXED; medic7-17 READY/PREPARED exactly
at pilot visit column150,65,-10; remaining births CARRIER_MISSING/PREPARED gen1.
Collision with player is a hypothesis to discriminate, not accepted cause yet.
Born8/9 were already canonical but unleased in prior census, hence not among19
historical CARRIER_MISSING selectors. Offline48509 confirms both current gen1
eligible for explicit legacy repair (99 entity chunks,head35086), no writes.
Native server33993 terminal0/client70849 terminal1/ports free; no frames reached.
Summary retained19 required conflicts,0 inventory conflicts: no global acceptance.
Evidence ingress-diagnostic-fix.json/client-diagnostic-fix.log/server-diagnostic-fix.log
and newborn-inspection.log under recovery root. Live unchanged. Next scoped
work is those two safe repairs and non-overlapping ingress, not blind rerun.

Shared diagnostic correction implemented after native failure below:
automatic ID includes full producer-stamped tuple with stable wire tags and
length-prefixed arbitrary IDs. No parsing/inference supplies an owner. Explicit
owner-retained IDs remain unchanged and cannot be reused for a different tuple.
Subject index is a deterministic latest-observation projection, not a uniqueness
constraint across jobs/reasons; repeat and optional eviction refresh it while
all independently retained incidents remain addressable. Terminal capacity and
no-eviction policy unchanged. No codec layout change or historical ID rewriting.
Diagnostic-plane contract now states these semantics.31472:22/23 diagnostic tests
passed; sole WAL-tail test failed system/tmp quota, corrected environment only.
38483:that test green7s with project tmp. Index suite8/8 includes multi-job same
facility/snapshot/repeat/forged ID/multiple terminal owners. Initial37420 fixture
used an invalid registered tuple, corrected. No native rerun or deploy yet.
Logs diagnostic-index-regression.log and diagnostic-wal-tmp.log in recovery root.

Native follow-through19801 FAILED with a real shared diagnostic identity defect.
New sa-recovered-population-ingress scenario, full-pack candidate artifact SHA256
1c01e43fb337a8b05a5c9b7c46d56a261a816a458041631ff1fac079d7bd86e0,
world build/sa-population-recovery.WsGyXG/runtime/world. At17:03:13 server rejected
executor:production-work-route-blocked-r34694: frontier.production_blocked reducer
reported "same diagnostic incident identity has a different tuple", quarantining
runtime. At17:03:16 client population assertion failed expected25/observed22.
No frames reached; this is not population acceptance or a harness-only timeout.
Static cause: DiagnosticIncident.idFor omits owner and other tuple dimensions;
production subject is a reusable facility, owner is the individual work. Distinct
work conflicts alias. DiagnosticIncidentIndex also enforces one retained identity
per subject, so adding owner to ID alone is insufficient. Repair shared identity
and deterministic subject explanation lookup without weakening forged explicit-ID
checks, bounded terminal retention or replay. This product-discovered correction
is required before SA closure; missing3 physical identities still need diagnosis.
Server38336 stopped gracefully/all dimensions saved17:04:42; client19801 terminal1,
ports free. Evidence ingress.json/client.log/server.log. Earlier accidental
old-candidate launch46659 stopped and retained separately, not acceptance evidence;
correct run used fresh copy of repaired head34342. Live server unchanged.

Batch16752 TERMINAL0. Original stopped full-pack-runtime/world at head34304
retained unchanged; copied to build/sa-population-recovery.WsGyXG/world.
Historical census selected19 missing residents; each current lease3 and absence
was independently revalidated across94 saved entity chunks under session.lock.
Existing pilot-only recovery tool published durable receipts, inactive custody
and registered cancellation transitions. Each operation replayed immediately;
no duplicate canonical transitions. Final head34342, instant175552 unchanged.
Read-only aggregate comparison35744 TERMINAL0 proves exactly19 PREPARED->CLOSED
leases with unchanged generations; restoring the original lease map yields
complete canonical equality. No actor/economy/work/health/position/time edits.
Evidence: recovery.log and aggregate-verification.log in the new copy root.
This accepts offline population repair only. Native reappearance/census and
continued work on this repaired copy remain unverified; no live deployment or
live-world repair. Initial read-only batch82640 also terminal0. Actual candidate
count19 corrects the earlier informal20.

### SA-06/07 follow-through: typed physical owner stamping

Removed caller-owned metadata callbacks from SceneExecutor scene admission /
closed-harvest ambient return and both ActorHandoffRecovery targets. One
ActorOwnerBinding writer now installs exact scene identity/revision or ambient
actor/kind, clears opposite-owner tags, and stamps common identity/epoch. Durable
handoff history still precedes the fixed metadata effect; no pose/health/body
replacement or new owner inference. A persistence-only test seam retains the
prior on-disk-before-effect assertion, not a caller-defined metadata operation.
Native regression additionally checks absence of both opposite-owner tag sets.
19918 terminal0/57s:7 unit and17 native tests pass, dimensions saved16:47:43.
Evidence `build/sa-ambient-world-copy.j4JKe3/typed-owner-stamp.log`. Initial95937
failed duplicate new fixture local, corrected. This eliminates the callback
omission hazard; it does not establish OS-crash atomicity or close historical
missing-body recovery. Earlier full farm run predates this change. No deployment.

Harvest/restart/successor2071 TERMINAL0, all15 actions pass, client completes
16:42:32. Result `build/sa-harvest-custody-20260923/result.json`; world
`v3-disposable_sa_harvest_restart_succes-eaaaf515`; client
c86e2862-254b-4738-890b-5af1b2ffdd76. Same farmer resident:7-31 and physical UUID
ef562345-8f47-37ec-af28-d12c259ab948 resume after graceful restart and complete
64 crops. Confirmed harvest is consumed by order:production-7-77/worker7-15,
producing64 bread. rev7154/t35508: GROWING epoch2/stage0, successor TERMINAL,
physical receipt resolved/confirmed; rev7156/t35557 requiredConflicts0. Actual PNGs
opened:64 bread visible in chest, young wheat visible in successor field. Initial
farmer frame occluded; board dark/distant. No full work-motion/M3 acceptance.
Terminal diagnostic's completeForColdHotReceipt=false remains an explicit trace
limitation, not silently promoted. One full cycle with restart is accepted, not
two cycles or historical population recovery. Task processes stopped/ports free,
no failure bundle, no live-server changes. Do not repeat unchanged successful run.

Next acceptance gap: ordinary farm worker custody across restart and subsequent
harvest -> bread -> regrowth. Original no-restart scenario already passed56548
(receipt below), so new `disposable-sa-harvest-restart-successor.json` adds graceful
restart after ordinary HARVESTING visit/worker lookup, then retains the existing
semantic progress observer, chest inspection and successor-field assertions.
Session2071 running, evidence build/sa-harvest-custody-20260923. No result yet;
artifact c143ca9a05b7a5b010df6c4733b1bebbf834b9141127f2b25016067f3d700a28.

Native atomic candidate27005 TERMINAL0: result
`build/sa-fungible-midwork-atomic-20260923/result.json` status ok. Before restart
rev70/t2959 exact worker resident:1-15 HOT/PROCESSING; actual PNG opened. After
graceful restart ordinary work completes: rev207/t4229 lot:production-1-1-bread64,
no claims, observed physical binding epoch2/container:1-depot/slot0. Job not_found
t4231; depot OBSERVED_CURRENT, ACQUIRED epoch2, physicalSocket slots CURRENT t4234.
Client dde6be51-cd71-4112-ad85-fce8c26d8bbe; prepared JAR identity below. No failure
bundle and isolated listeners closed. Evidence accepts this declared COLD-start /
HOT-work / graceful-recovery / output flow, not repeated farm cycles or all audit.
Added narrow loaded-checkpoint native regression; session69277 terminal0/27s,
all3 reference-projection GameTests passed, dimensions saved16:31:21. It exercises
loaded unchanged CHECKPOINTED custody through actual chest reconciliation, asserts
RELEASED and unchanged physical fingerprint/provenance. No native rerun needed
for that package-local test seam. `git diff --check` passes. Root pack retains
pre-existing dirty AGENTS.md/.f0v-baseline; old nested checkout retains existing
governance redirects/docs WIP. Neither was modified by this repair; no commits.

Repair implemented after failed native run: container resource-release command
now emits checkpoint/layout-release/scope-release and resumed production schedule
in one transaction. Exact provider/object/epoch required; generic CustodyReleased
still rejects bound-layout bypass. Both physical adapter callers converge here.
Loaded unchanged CHECKPOINTED scopes also finish draining instead of remaining
live forever after historical split release. No physical rewrite in this path.
45221 terminal0/26s:29 focused tests green, including ACQUIRED/CHECKPOINTED release,
snapshot recovery and rejected late actuator, repeated HOT/COLD composition,
resource observations and catalog. Initial90741 exposed missing emission registry
entries; corrected explicitly. Native candidate prepared17463/7s, SHA256
0dd09b06761f581df511e9f7517bb0ec6ca069022edb5c3e18dedab730fddd3a;
new native verification in progress under build/sa-fungible-midwork-atomic-20260923.
Not yet accepted or deployed.

Native run60949 completed FAILED, retaining world and failure bundle under
`build/sa-fungible-midwork-20260923`. Prepared JAR SHA256
`09bd4dbdcf1d3738eb5daf993d5d72b08f0f594b317a35274801aacd5489c776`.
Before graceful restart rev69/t3003 confirms HOT PROCESSING and exact worker;
actual screenshot opened and shows worker/workshop (not complete motion proof).
After restart no bread: rev290/t5115 read-only diagnostics show PROCESSING40,
FungibleCold input, one due completion, no scene; depot CHECKPOINTED epoch1,
OBSERVED_CURRENT and loaded, but resource bindings absent. Wheat64 and claim persist.
Source-confirmed liveness gap: custody executor releases resource bindings and
scope in separate turns. Loaded matching-fingerprint reconciliation does not finish
the checkpointed scope after bindings disappear. COLD use remains fenced while HOT
work lacks input. Next repair must close the combined release/recovery transition,
with a load-between-steps regression. No acceptance claim or live change.
Runner terminal1; isolated ports25585/25586 verified free after cleanup.

Added `disposable-fungible-production-midwork-restart.json`: ordinary COLD
processing fixture, visible HOT worker, graceful midwork restart, exact output
lot (64 bread), cleared claims, physical binding and observed-current depot.
Profile identity regression passed (session38409,7s); scenario syntax validated.
Native execution remains pending; this is not physical acceptance or closure.

Fixed `run-scenario.mjs` diagnostic comparison through `diagnostic-matcher.mjs`:
array expectations used reference equality in Node while the native Gson matcher
uses exact JSON equality. Resource-lot assertions could incorrectly fail after
successful native observations. Objects remain partial, arrays exact including
length, order and member fields. Three Node tests passed, covering wrong resource,
quantity, additional entries/fields and the existing midwork scenario contract.
No deployment or live-world mutation.

## 2026-09-23: SA-06/07 natural entity-read recovery and observer isolation

Pre-close integration now implemented: shared SceneExecutor release prepares the
exact loaded/unloaded cargo observation durably before member fencing and canonical
close. Loaded capture and real-unload capture share contents/identity validation
but use distinct removal guards. RETAIN ack also marks obsolete pre-handoff
preparation and queues its metadata compaction, without changing player contents.
Native25661 exposed wrong unload-only producer use (fixed);90416 then proved
no-history REMOVE cleanup but rejected two tests attempting player interaction in
DRAINING. Tests now supply explicit registered UNKNOWN/HOT recovery input first.
72533 retained-case second-pass fixture failed because parallel tests replaced the
shared-level runtime index; fixed by announcing that pass's exact provider write.
63033 subsequently terminal0/39s: all7 tests pass, fullsave/shutdown09:50:29.
These are controlled-provider composition tests, not new actual region-file/process-
crash evidence; new test calls producer plus registered close, not full generic
release with natural bodies. No full SA closure. Diff-check clean, no deployment.

Preparatory witness primitive added to CargoCleanupArchive: exact current DRAINING
authority may atomically replace its own unconfirmed prior observation; accepted
terminal witnesses remain immutable via ordinary retain and saved-marker checks.
World/lease/cargo/UUID/revision/current epoch and absence of pending retirement are
checked before replacement.1350 terminal0/12s archive regression passes, including
rejected HOT/stale lease, allowed current preparation refresh, immutable retain and
saved-marker rejection. It is NOT yet wired into release and proves no physical
capture itself. Complete RETAIN staging cleanup and caller observation validation
before integration; no native acceptance or deployment from this helper.

REMOVE closure ordering audit (source-confirmed, not repaired):
- SceneExecutor.release verifies live cargo/departure then submits SceneLeaseReleased
  without first retaining a cleanup witness for a loaded carrier.
- CargoCarrierExecutor.discardClosed creates that witness later, only while it
  still has the historical lease and can validate intact contents.
- cleanRetired without the historical lease delegates to observeJoin, which needs
  a departure ledger/archive receipt; a never-unloaded cart has neither.
- FrontierSceneLeaseStateSupport.mayCompact only checks CLOSED and physical-intent
  scene bindings, not pending cargo retirement. Thus the1024-history cap can erase
  the lease before the first cleanup witness exists. Retaining the terminal UUID
  alone does not give this path sufficient physical evidence to remove the cart.
Required repair: make final physical witness availability part of the shared
release/retirement protocol, with rejected-release/recovery handling. Do not merely
delete by UUID, infer original contents from current cargo, or rely on successful
RETAIN tests. Prewriting the current immutable UUID-only archive blindly is also
unsafe: a rejected release followed by lawful resumption can change the physical
snapshot; a later RETAIN transition must not orphan that staging evidence.
No new native run was started for this static ordering diagnosis.

Destruction test admission: optional boolean `requireRemoval` now makes the pilot
wait for actual selected-entity removal rather than succeeding when attack count
is exhausted. It never exceeds the attack budget or timeout; legacy nonterminal
attack semantics unchanged. Java90139 terminal0/8s,42 scenario tests; Node3 tests
pass, including malformed flag rejection. Native14689 launched for released empty
cart destruction/restart/save/cleanup, receipt target
`build/sa-cargo-destroyed-observed-20260923/result.json`; no result yet.

Result14689: terminal0/92.625s, all11 actions pass. Exact selected empty cart removed
after ordinary attack; full vanilla save09:35:47, restart, player still holds64 bread,
delivery remains INTERRUPTED; ordinary save then CLOSED/cargoCleanupPending=false
at revision231/instant9804. Ports closed, world retained. This establishes actual
final-removal observer + persisted footprint + entity-storage absence + cleanup
ack composition for destroyed RETAIN, not abrupt-crash or full REMOVE_PROJECTION.
SA06/07 remain open for the separately recorded remaining conditions.

Native46144 completed terminal0/92.926s: all10 actions including actual post-restart
save and CLOSED/cargoCleanupPending=false (revision238/instant9840); footprint
archive empty afterwards. Surviving RETAIN cleanup now has actual EntityStorage
composition evidence, not just injected futures. Both disposable ports closed.
Source correction: natural-read admission now includes every retained footprint
column for REMOVE as well as final departure columns; previously an already-empty
older column could be skipped forever.44910 terminal0/9s: footprint5+cleanup11
tests pass. This admission test is not full multi-column REMOVE native acceptance.
Prepared destruction variant is not yet runnable as evidence: pilot attack action
can complete on exhausted attack count without actual removal. An explicit terminal
removal requirement is needed before claiming destruction/recovery from that action.

Cleanup diagnostic increment: scene JSON now exposes `cargoCleanupPending` and
`cargoCleanupEpoch` from exact deterministic carrier UUID and canonical pending
retirement (null for non-logistics; false is not a general physical-safety claim).
Cargo GameTests assert true before failed/pending save and false after accepted
ack.42321 terminal0,39s: all6 cargo-interaction tests pass, full shutdown09:28:09.
Initial compile50099 failed from an undefined test local, corrected before this
run. Node route identity2 pass; diff-check clean. Cargo theft scenario now requests
ordinary `/save-all flush` after restart/visit and requires CLOSED plus pending=false.
Prepared67935 passed5s, artifact SHA256 fd133111fa09fc7e444d04e66663ea1a71c649624fd9b805f02116bb545afcd6;
native check launched with output `build/sa-cargo-cleanup-save-20260923/result.json`.
No completion claim until its actual result; no live deployment.

Native storage verification preparation: the old cargo-theft scenario expected
`item:production-1-1-bread`, but executing the current scene-return fixture shows
that item absent and `custody:cargo-supply-1-11` holding64 bread from the production
lot. Corrected the scenario to resource custody, a structure-free observer point,
and graceful restart with retained player quantity and interrupted operation.
Added a fixture regression for exact cargo/account/lot/quantity semantics:
76311 terminal0,17s, all3 RouteSceneFixtureIdentityTest tests pass; Node route
identity2 pass. Native57569 launched separately; no result yet. This is a
prerequisite inventory/restart check, not proof of cleanup acknowledgement,
multi-column destruction recovery or abrupt crash safety.

Native57569 subsequently completed terminal0 in89.268s, all9 actions passed.
Receipt: `build/sa-cargo-storage-restart-20260923/result.json`; ordinary durable
save09:22:47, reconnect retains64 bread under exact player custody and INTERRUPTED
delivery. Both disposable ports closed. No live deployment or M3 claim.
Read-only decode of retained snapshot142 finds exact RETAIN retirement for
e1754fb1-cb62-3c72-9d5a-dbd31f38796b, epoch1, lease supply-1-11-r123; corresponding
birth archive still exists. Shutdown removes canonical runtime before the entity
save hooks can acknowledge, and runner deliberately disposes the replacement JVM
after terminal assertions without another persistence claim. Thus this run does
not establish cleanup completion; next observation must expose the exact pending
obligation and exercise an ordinary running-server save. Repeating this unchanged
scenario would add no evidence for that separate condition.

### Remaining retained-cart destruction gap: source trace

Unsaved-removal return repaired: removalChunk now retains the FIRST observed
Entity.setRemoved location, not an irrevocable durable-deletion claim. Later
natural-return saves and repeated removal may append columns while preserving
that observation; removing history/changing epoch/overwriting its anchor remains
forbidden. Surviving clean-cart evidence is no longer rejected solely because an
earlier removal was observed. Actual UUID presence/new writes still invalidate
absence, and all historical columns remain required. No deletion or inventory
restoration is replayed by recovery. New columns durably invalidate an older
.saved marker before extended evidence is published, so a prior proof cannot
cover the extension or permanently block publishing a fresh proof.
78208 terminal0,7s:26 targeted tests pass.84020 terminal0,35s:all6 cargo-interaction
GameTests pass, full save/shutdown09:11:06. Added case releases real cart, saves
its actual NBT, physically discards it and records removal, loads that earlier
NBT into a new entity of the same UUID, and closes/cleans without second handoff.
The injected write-failure/retry path preserves inventory and empties metadata.
This is provider serialization + controlled IO futures/ephemeral WAL, not real
process crash or region flush.30866 terminal0,9s:archive6 pass including durable
old-proof invalidation on new returned column. Diff-check clean, not deployed.
NEXT actual production EntityStorage/read/write/sync and lifecycle scenario.
Existing disposable-hot-cargo-theft.json uses scene-return but old exact-item
fixture expectations; inspect current fixture before reusing, do not launch an
unchanged obsolete long scenario. Missing-birth/version policy, capacity/IO retry,
full REMOVE composition and SA08/09 product story still require closure.

Terminal path integration now includes ordinary REMOVE and surviving RETAIN:
REMOVE requires absence over every retained-attempt column; RETAIN requires a
clean serialized cart (no scene OR cleanup-identity key) and absence in every
other retained column. Both use common compare-exact footprint save markers and
post-ack compaction. Terminal owner persists the last cleanup-identity column
before stripping that metadata. Destruction after stripping but before clean
save resolves its exact identity from the pending nominal retirement, not from
current-location inference. Candidate footprint snapshots refresh at save-pass
selection, avoiding stale archive snapshots permanently blocking retries.
Cargo terminal sweep now enumerates pending obligations rather than evictable
CLOSED scene history; compacted history cannot strand a loaded retained cart.
95550 terminal0,37s: all5 cargo-interaction GameTests pass, save/shutdown09:05:42.
Two new cases compose registered domain handoff/closure, actual cart/real archive,
metadata retirement with historical scene deliberately absent from cleanup input,
controlled failed/pending write futures and successful retry, durable-command
acknowledgement, unchanged inventory and empty archive/markers. Futures and
ephemeral WAL are injected: NOT actual region-file flush or OS-crash acceptance.
5315 same5 passed before historical-independent sweep;44033 targeted units green;
27855 cleanup11 passed after adding cleanup-identity rejection; diff-check clean.

NEXT concrete source inconsistency found before deployment: footprint removal is
an observed Entity.setRemoved event, not proof that entity-region deletion already
persisted. After a crash Minecraft can legitimately return the old cart snapshot.
Current include()/extendsEvidence() freeze a removed footprint, beforeWrite then
rejects that cart's save, and RetainedCandidate filtering forbids a later clean
surviving-cart proof. Repair this without erasing historical columns/removal
observations or replaying deletion/contents: actual natural return and clean save
must be representable, while old absence evidence is invalidated. Also cover a
later second removal at another column. No full recovery/SA closure claimed;
keep current diagnostic server unchanged until this and native storage seams close.

Destroyed-RETAIN consumer is now wired (still local integration WIP):
CargoCleanupPersistence builds a distinct DestroyedRetainedCandidate only from
exact current-epoch final removal plus absence over the UNION of all archived
attempt columns. Coverage retains bounded actual serialized UUIDs, not merely
currently pending UUIDs (which would fabricate absence when retirement appears
later). Every later write/read invalidates prior coverage for that column;
malformed data remains unknown. Cross-pass observations become confirmed only
after the exact successful current save-batch ticket and sync. Failed old-column
write still blocks acknowledgement. A final-removal candidate cannot fall back
to the older single-clean-cart RetainedCandidate.
After revalidating exact archive inventory and loaded absence, archive .saved
markers precede DURABLE_BEFORE_EFFECT CargoCleanupSaved; then ordered synced
unlink removes .nbt before .saved. Startup inventories interrupted markers and
compacts at most one per pass only without pending obligation/live scene.
Provider methods never mutate inventory or claim attribution of drops.
77562 terminal0,7s: cleanup11/save-batch8/coverage3/archive5/observer3=30 tests
green (XML checked), diff-check clean. Archive test reopens after an injected
interrupted unlink; this is file-backed component evidence, NOT OS-crash/native
composition acceptance. Earlier85962 passed29;63456 coverage3 passed after
constant-time retained-entry accounting replaced per-observation map scans.
Remaining before deployment: ordinary surviving RETAIN and REMOVE paths still
need footprint-aware terminal metadata retirement/compaction; full native
birth->move->release->destroy->save->ack/restart must run. Missing-birth legacy,
final-removal IO retry and full capacity recovery remain unresolved. No closure
or permission to redeploy from these narrow greens.

Footprint producer integration increment (local WIP, NOT deploy-ready): archive
keys are now exact UUID+authority epoch, not UUID alone. Bounded inventory keeps
prior attempts/columns; compare-exact deletion refuses newer evidence. Retained
file contents must agree with filename/world; no filename-derived authority.
Production materialization persists birth evidence before adding the cart and
stamps separate non-custody cleanup identity. EntityStorage redirect now retains
every serialized column BEFORE submitting vanilla's write, including shutdown
when the canonical runtime may already be stopped. Missing/corrupt birth data
fails that write rather than fabricating history. The final Entity.setRemoved
hook additionally records KILLED/DISCARDED, never UNLOADED_TO_CHUNK as destruction.
New source helpers preserve old-attempt evidence and never restore inventory.
79225 archive+value8 green;24679 terminal0,10s: observer3/archive4/value4 pass,
actual XML counts checked; full main/pilot compilation passed. Tests cover real
archive reopening, concurrent retained epochs, stale compare-delete, renamed
identity rejection, serialized moved-column capture without NBT mutation, missing
birth/final-removal rejection, and absence not implying destruction. No native
hook/OS-crash acceptance claimed yet. Save/ack consumer and post-ack compaction
remain unwired: do NOT deploy this intermediate producer-only implementation.
Next consumer must validate all prior columns for BOTH removed and retained
cart paths, invalidate absence on later writes, and handle cleanup identity
removal before deleting its archive (otherwise subsequent clean-cart saves would
refer to a missing birth). Legacy carts without birth evidence cannot be assumed
to have complete history. Capacity/backpressure and lost final-removal IO retry
also require closure; no force-load or optimistic absence shortcuts.

Post-commit exact-item provenance recovery is now shared by ordinary interaction
and naturally returned RETAIN carts. After exact cart-declaration validation,
`FrontierV3CargoCarrierProvenance` uses each item's explicit canonical WorldCarrier
custody to fill only missing carrier metadata. It validates all slots before any
write, rejects duplicate exact IDs, quantity/kind mismatch, foreign/malformed
carrier declarations, and never recreates missing items or changes slot contents.
Unknown/player-owned contents are preserved. Native48543, cargo-interaction3,
passed with complete save/shutdown08:44:34: provider-input test on real MinecartChest
inventory covers moved slot, absent item, foreign ordinary contents, idempotence,
foreign carrier, duplicate and changed quantity. Existing two interaction tests
also pass. Exact-item case is provider-level evidence, NOT an actual canonical
exact-cargo handoff through OS restart; that composition still needs verification.
Not deployed. No destruction-footprint closure inferred from this repair.

Handoff ordering prerequisite repaired after the diagnostic deployment:
`releaseCargoCarrier` previously called mutating `markReleasedCarrier` before
submitting the durable release. A failed append removed the actual caption even
though interaction was rejected. It now performs read-only exact admission,
commits release, then applies physical provenance/caption/declaration changes on
the same server thread. Unexpected post-commit validation failure is explicit,
not a successful container interaction. Native25945 reproduced missing caption;
34477 terminal0,32s passes both cargo-interaction tests, complete save/shutdown
08:40:54. New test injects pre-commit append failure and checks no durable append,
visible quarantine, unchanged full declaration/contents and preserved real
caption; existing test proves successful release, torn declaration reconciliation
and partial fungible player withdrawal. Initial28375 failed because its test
queried a checkpoint after quarantine; corrected to inspect the store's actual
append count. This is native fault injection, not OS-crash evidence or exact-item
post-commit torn-save repair. Not deployed; destruction footprint integration
below remains OPEN.

Source review after13975 confirms a missing producer, not a slow test:
`FrontierV3EntityDepartureMixin` forwards only UNLOADED_TO_CHUNK;
`observeFinalChunkDeparture` repeats that restriction; CargoDepartureObserver
records only declared scene unloads. `releaseCargoCarrier` removes the declaration
immediately after accepted handoff (before the scene's CLOSED retirement may even
exist). VehicleEntityMixin observes anticipated terminal damage at HEAD, not final
removal, and its lifecycle excludes explosions. Neither hook currently produces
a durable final-destruction cleanup witness for a released cart.

CargoCleanupPersistence selects RETAIN_WORLD_CUSTODY only when serialized entity
data contains that chest-minecart UUID without scene metadata. Therefore a cart
destroyed before its first clean save cannot yield that candidate. Removing its
obligation from a loaded UUID miss would be incorrect: absence is not destruction,
and failed old-chunk writes during movement can retain an older declaration.
The existing save-batch failure barrier must remain effective across this path.

Required repair boundaries (no implementation/acceptance claim yet):
- Keep cleanup identity separate from live cargo custody and from unload snapshots;
  a destruction receipt must not authorize spawning, inventory restoration or
  reclassification of player drops. Do not reuse REMOVE_PROJECTION permission.
- Capture exact identity before declaration retirement and retain final observed
  deletion separately from predicted terminal damage, including explosion removal.
  Handoff-before-CLOSED is part of the producer path, not an absent-record fallback.
- Acknowledge only after relevant stored entity absence and write+sync proof;
  movement/failed old-column save and restart must not turn one new-column absence
  into proof that all old declarations disappeared. Inventory/drop reconciliation
  stays with its existing owner; cleanup must never manufacture loss.
- Verify same-chunk destruction, move-before-destruction, failed old-column save,
  restart before acknowledgement, and plain unload (negative). Evidence/storage
  must remain bounded with visible capacity behavior, not permanent silent leakage.

This is the next SA06/07 source repair, before broader physical save/crash acceptance.

Initial implementation: FrontierV3CargoRetirementFootprint retains exact nominal
world/lease/cargo/UUID/revision/epoch, immutable bounded chunk footprint, and an
explicit final-removal location. Missing historical columns or missing removal
observation cannot satisfy its completeness predicate.58626 terminal0,8s:2 tests
cover move/partial absence/final removal/idempotence/contradiction/capacity without
eviction. This is only the evidence value; persistence and runtime producer/save
composition remain unimplemented. The record itself never grants cleanup or
restoration authority and is not a claim of fixing destroyed-cart retirement.

Footprint persistence increment: strict format1 codec validates mandatory typed
fields, deterministic UUID, bounded unique columns and explicit removal tag.
Monotone update rejects lost columns, changed identity/epoch and revised final
removal. FrontierV3CargoFootprintArchive uses file force, required atomic replace
and directory sync, preserving a published file on failed validation; no unsafe
rename fallback.19932 codec4 pass;88803 terminal0,8s codec+archive6 pass including
real file reopen, partial staging, trailing corruption and foreign world.
This is provider persistence only: handoff/final-removal/write observers, ack and
post-ack compaction still need integration. No product/crash acceptance claim.

Missing-live-target handoff66110 terminal0,44s, all7 scene-strikes pass,
save/shutdown07:51:25. UNKNOWN strike with no owned target and non-dead canonical
actor now transitions HOT/DRAINING scene to UNKNOWN for existing recovery.
Native component verifies actor map unchanged, no invented hit/death, retained
UNKNOWN effect and unchanged inspection-attempt count. Entity absence does not
consume an inspection or permit replacement. No native return/reclaim/terminal
closure claimed by this handoff test.

Route-engagement owner gap repaired: recoveryUnresolvedPlan previously threw
"engagement scene recovery needs its own outcome policy". It now validates the
exact operation/cargo/engagement/intercept and preserves local uncertainty via
None continuation for active UNKNOWN and interrupted/ABORTED engagements.
Missing bodies do not invent a death, defeat, cargo loss or work resumption.
Regression93210 failed before correction;20382 verifies both cases through
registered commands, unchanged actors/inventory/operations/strategic plans,
snapshot roundtrip and WAL replay. Cargo7 and supply10 all pass; engagement14/15.
Overall20382 is FAILED:36tests,1failure,70s. The autonomous scout-interception
test has no sighting by10000 ticks, independently of this physical-recovery
command path. It remains a real untriaged integration finding, not waived.
Interrupted-logistics recoveredStatus now returns DRAINING; only EN_ROUTE with
retained projection disposition may return HOT, terminal work rejects resumption.
Regression91659 failed at defaultHOT;12236 terminal0,26s passes cargo7 plus4
architecture tests. Extended registered-command/replay test goes through recovery
and actual domain CLOSED for released cargo, preserving ABORTED engagement,
INTERRUPTED operation and RETAIN_WORLD_CUSTODY retirement. Active combat still
reclaims HOT. This is domain-input evidence, not native returned-body acceptance.
Next source gap: adapter latches recoveryEvidence and never rechecks returned
bodies. Owner-specific policies are needed before removing it across all families:
ordinary FAILED delivery and BLOCKED patrol must not resume terminated work.
Native return/closure and crash/save proof remain open. No redeployment.

FAILED noncombat delivery return is now a terminal custody-release path, not a
new delivery: recoveredStatus selects DRAINING; expectedMembers permits exact
failed-operation UNKNOWN/DRAINING; releasePlan emits no continuation; a second
missing observation records uncertainty without failing the operation twice.
Regression94385 reproduced the missing return policy.25198 terminal0,36s passes
cargo8+supply10 plus4 architecture checks.55221 terminal0,26s passes cargo8 plus4
architecture after adding repeated drain->UNKNOWN->missing->drain->CLOSED,
snapshot hydration and rejection of HOT revival. Inventory, operation failure
and strategic task state remain unchanged. These are registered domain commands,
not proof that the native adapter has yet resumed checking returned bodies.
UNKNOWN assault with a dead member selected DRAINING while its strategic assault
stayed UNKNOWN, contradicting the DRAINING/HOT invariant. Reproduced38804; fixed
both assault and road-engagement transition owners to atomically recover physical
custody for drain. Strategic HOT here denotes retained physical custody; lease
stays DRAINING and shared strike admission/RUNNING still require lease HOT, so no
new attack is permitted.40007 terminal0,37s:assault6+cargo9 plus4 architecture
checks pass. Assault lethal receipt/snapshot->UNKNOWN->drain->CLOSED retains
death and observations. Road case uses registered ActorDied and recovery/release
commands through CLOSED, preserving cargo and death before COLD resumes.
This establishes the domain transition, not native process-crash acceptance.
Terminal patrol/service/engineering/medical recovery must also be inspected before
removing the shared adapter's recoveryEvidence latch.

Terminal service/medical/patrol: completed/blocked owner state now selects drain,
not default HOT; UNKNOWN hydration retains terminal obligations.89931 reproduces
completed service restart rejection.35862 finds patrol stopped in assembly cannot
release because releasedBody incorrectly demands its travel-route formation.
Terminal patrol release now preserves observed exact-member positions; active
patrol formation/current-plan requirements remain.3519 terminal0,18s:30 checks
pass (service6,medical11,patrol9,architecture4). Service test completes its real
domain endpoint receipt, then UNKNOWN/snapshot/drain/CLOSED without changing
inventory/infection/observations. Medical test supplies completed owner state
and proves release without inventory/health changes, not a native treatment
claim. Patrol test uses a blocked living roster and proves CLOSED with task still
blocked. Initial85552 was a misplaced test insertion (compile error), corrected.
Engineering terminal recovery now hydrates UNKNOWN at the stopped current cell
or exactly one confirmed cell later; owner explicitly chooses drain vs active
work.91385 reproduced rejection;60720 terminal0,14s full maintenance suite green.
Production recovery drains blocked jobs or OUTPUT_READY, using the same blocked
predicate as releasePlan.11270 terminal0,8s verifies blocked-work recovery through
close/finalization.47778 used wrong class name and ran architecture only, not
production evidence. All8 scene owners must now implement recoveredStatus:
no inherited HOT default. Adapter no longer exits solely on recoveryEvidence;
it retains demand/entity-readiness checks and skips only duplicate absence
commands while continuing exact-body inspection.7331 compile checks pass.
78091 terminal0,49s:8/8 native scene-strikes pass, full save/shutdown08:20:03.
New recordedMissingMemberIsReinspectedWithoutDuplicateCommands exercises main
reclaim (not just body helper): real entities, mock observer, naturally elapsed
inspection window. With one exact UUID's custody declaration removed, canonical
absence evidence is recorded; repeated inspection makes no revision change.
Restoring that exact declaration permits HOT reclaim and clears absence evidence
while preserving the original entity object. This is bounded template-local
projection/NBT fault injection, not actual entity-region reload or OS crash.
Previous native directory preserved as frontier-v3-scene-before-reinspect-20260923.
13975 terminal0,10s:confirmed engineering-cell and production OUTPUT_READY
recovery-policy inputs pass UNKNOWN/snapshot/drain/CLOSED without changing
project/job/inventory (2 target regressions plus4 architecture). These fixtures
do not prove physical work receipts; earlier domain/native work evidence is
separate. Cargo physical save/crash and full composition criteria remain open.
No deployment or broad SA06/07 closure is claimed.

Restart-readiness source audit found another SA06 path using block-column
availability as readiness for missing-actor classification. SceneExecutor.reclaim
now additionally calls shared SceneReleaseReadiness.awaitingEntityStorage before
starting/continuing its bounded observation interval. It resets the interval if
current/retained entity columns are not ready; existing exact loaded/departure
evidence remains inspectable. Unit74709 terminal0,13s:6 readiness tests pass,
including UNKNOWN lease/current-only/retained-only/full-ready cases and no
implied actor death. Diff check clean. Not an actual asynchronous chunk-load
native proof; no live redeployment. DRAINING unresolved strike with missing
living target still needs explicit local recovery handoff, not invented death
or effect confirmation.

Removed-dead-target follow-up: exact canonically DEAD target now authorizes
bounded inspection/abandonment of UNKNOWN strike without an extant Minecraft
body. This does not confirm hit attribution or infer death from absence. Living
unloaded/missing target remains unresolved. Native component case actually kills
and removes the owned entity, submits the observed death using its canonical
fixture station, and invokes ordinary release; conflict preserves dead actors,
creates no hit observation and leaves runtime ACTIVE. Test helper uses a closed
scenario enum instead of accumulating boolean combinations.

Method corrections retained:70186 failed because translated GameTest coordinates
were outside canonical bounds;93063 failed because the test redundantly requested
DRAINING after the registered death command had already done it atomically.
Both are test errors, not new product defects. Corrected14455 terminal0,40s,
complete save/shutdown07:47:07; all6 native cases passed.
This is component evidence, NOT production-dimension coordinate/
death-event routing or OS-crash recovery. Missing living target and pending
physical-save boundaries remain OPEN. No live redeployment.

Malformed-witness drain follow-up: inspectUnresolvedStrike now admits a loaded
RUNNING effect into UNKNOWN before bounded inspection. Previously invalid/mismatched
metadata returned before the no-witness drain conversion and trapped RUNNING.
New native malformedRunningWitnessCannotTrapDrainingScene supplies malformed
physical NBT to an actual owned target after production prepares/starts intent,
then calls ordinary DRAINING release four times. Intent becomes CONFLICTED,
health stays unchanged, no observation is fabricated and runtime stays ACTIVE.
Native48704 terminal0,39s, complete shutdown07:42:08; all5 required tests passed
(14.38s test batch). This proves
bounded disposition, not subsequent scene CLOSED, metadata repair, absent/dead
target recovery or entity-region crash durability. No live deployment.

Adapter drain recovery follow-up63127 terminal0,36s: all4 native scene-strikes
pass, complete save/shutdown07:40:05. Existing actual-hurt/storage-failure case
now confirms through ordinary SceneExecutor.release on DRAINING, not direct
executeStrike: exact target witness resolves UNKNOWN before any body release,
without another hurt. Each turn re-reads authority before continuing release.
Pending strike lookup uses exact retained lease and lifecycle owner rather than
the newly selected current attack cause. Exact still-loaded dead body declaration
is eligible for witness inspection, never new damage. No-witness RUNNING during
DRAINING is moved to UNKNOWN rather than replayed. Compile31578 passed10s.

Remaining source corner: malformed/divergent marker branches return before
that RUNNING->UNKNOWN drain transition, so those must enter inspection even
when a marker exists. A removed/missing dead entity still has no target witness;
natural storage evidence/absence disposition and true lethal native composition
remain unresolved. Four green tests do not establish these or region-file crash
safety. No live redeployment; user server retains the earlier diagnostic JAR.

Lethal-strike source follow-through: regression14377 reproduces confirmation
failure after ordinary ActorDied for the exact prepared target. validateObservation
reran COLD participant selection over the now-living subset, selecting another
target and rejecting the already applied hit. Observation now requires equality
with the retained admitted intent and validates its exact roles, lease/epoch,
wound/death and tactical authority, without participant reselection. Selection
remains at admission. Green25891 followed by expanded35006 terminal0,14s:
all6 assault tests pass, including lethal confirmation, no invented death,
UNKNOWN snapshot roundtrip and confirmation with unchanged dead actor/receipt.
This is canonical composition, not native lethal recovery acceptance.

Remaining exact adapter path: body() omits dead targets; executeStrike discovers
pending work from a newly selected current strike cause; both route and assault
DRAINING call release directly, while prepareRelease rejects RUNNING/UNKNOWN
strikes. Recovery must consume retained exact intents independently of selecting
a new attack and run before drain/release, including unavailable-target policy.
No live redeployment, no claim that these adapter gaps are fixed.

Post-diagnostic-deployment source repair: strike abandonment validation moved
from PhysicalIntentLifecycleCapabilities to SceneStrikeStateSupport.transitionOwner,
used by both route-engagement and settlement-assault plan/reduce paths. It requires
the exact EFFECT owner, AMBIGUOUS fence and exhausted attempt budget. Common
PhysicalIntentTransitionStorage receives explicit owner-supplied RecoveryEdges;
only strike reducer opts into INSPECT_AND_ABANDON, existing callers retain
CONFIRM_ONLY. Common recovery fencing consumes phase/epoch, no intent-kind branch.
Architecture regression now forbids strike-specific composition and any kind
dispatch in common transition storage. Verification65209 terminal0,15s:27 selected
tests pass (assault6, fence8, composition11, architecture2); git diff --check clean.
Previous87709 was green before the added recurrence assertion. No native rerun
or live redeployment claimed for this owner-boundary refactor. Lethal/absent
target and actual persistence/crash obligations remain open.

Bounded strike recovery follow-up: native57373 terminal0,38s, all4 strike
cases passed with complete shutdown07:11:34. A missing witness on an actually
owned loaded target now consumes bounded inspections and ends in CONFLICTED /
scene DRAINING without invented damage or observation. Missing/unloaded target
does not consume an inspection. Terminal abandonment is exact and fenced;
matching retained abandoned witness can be cleared without restoring health.

Found and reproduced another boundary defect: `inspectedObserved` rejected exact
evidence after the attempt limit merely because nextAction was ABANDON, although
abandonment had not committed. Regression98897 failed at that boundary. The
common fence now accepts exact observation while still AMBIGUOUS; actual
abandonment removes authority and stale epochs remain rejected. Unit3005 passed
all8 FencedRecoveryState tests plus compilation. Native recovery now deliberately
exhausts inspections with a divergent physical input before loading the separate
exact saved input. First run22624 failed due to a test-only wrong map key
(PhysicalIntentId instead of the declared recovery binding ID), corrected and
preserved under scene-game-test-late-strike-fixture-red-20260923.
Corrected native96414 terminal0,35s: all4 cases pass, complete save/shutdown
07:18:24. Exact witness at the inspection limit confirms without another hurt.
Evidence remains real entity NBT plus in-memory WAL fault injection, not an
OS crash or actual region-file durability claim. No live deployment.

Remaining source review concerns, not closure: strike-specific abandonment
policy currently branches in generic lifecycle/storage support and must move to
the registered family owner under section7 execution semantics. `body()` filters
dead targets out before witness inspection, leaving lethal recovery unproved.
The new bounded rule does not resolve absent targets, region-save crash windows,
or receipt/history compaction. SA06/07 remain OPEN.

Native strike-recovery composition16256 terminal0,35s:all3 scene-strikes tests
pass, complete save/shutdown07:03:52. New case injects append failure only when
production confirms an actual damaging hit; the failed transaction is not put
into the test store. Real target health and producer-created witness serialize
and load through Minecraft NBT; a newly started runtime replays only successful
transactions, then ordinary UNKNOWN transition permits inspection. Divergent
physical health stays UNKNOWN and unchanged. Reloading the separate exact saved
input allows confirmation without another hurt and retires the marker.

Evidence limits: this is explicit fault injection at the storage port, in-memory
WAL replay and real entity serialization, not an OS/process restart or entity
region-file flush. The test does not invent a hit receipt or canonical outcome.
Previous two ordinary/conflict native cases remain passing. Abrupt crash before
entity save, lethal target, missing-witness bounded disposition and compaction
remain open. Previous native directory preserved before run; no live deployment.

Strike recovery WIP adds a single target-local typed NBT witness with exact
world/scene/revision/owner/effect-epoch/entity and SceneStrikeObservation. Written
after actual hurt before confirmation, it is removed only after accepted durable
confirmation. UNKNOWN may inspect matching witness and current physical health,
validate canonical observation and confirm without another hurt. Absent,
malformed or divergent evidence remains unresolved; no health-difference guess.
Already-confirmed exact retained observation permits removal of a torn-save old
marker without changing health. Pre-hit execution requires exact RUNNING effect
authority. Codec test81208 terminal0,10s:2 tests pass, including each missing
field, foreign UUID/owner and impossible health bounds.

This is partial implementation, not crash-safety acceptance: vanilla does not
save entity NBT between same-thread hurt/confirmation/marker-clear. A witness
can persist if confirmation fails and subsequent entity save succeeds; it does
not itself force/synchronize that save or resolve abrupt crash before it. Need
native producer/recovery composition, lethal target handling, missing-evidence
bounded disposition and receipt/history compaction. Existing successful strike
native39602 predates this WIP and cannot prove it. No live deployment.

Combat follow-through: native conflict discriminator identified a contradictory
retirement account. FrontierHiveProcessModule required a hit observation on every
SCENE_STRIKE terminal state, while PhysicalIntentTransition allows that observation
only on CONFIRMED. Registered RUNNING->CONFLICTED therefore failed invariant
validation (red41658). Owner now requires hit evidence only for confirmation;
conflict must leave actor state and physical-observation history unchanged.
Separately, executeStrike's old pending selection admitted CONFLICTED into the
damage branch. It now only admits PREPARED/RUNNING. The red failed before reaching
damage, so no claim is made that this run observed an actual replayed hit.

New scene-strikes slice covers existing real hit/independent receipt and a new
real-entity registered conflict case with two executor retries, unchanged health
and retained CONFLICTED status. Green39602 terminal0,31s:all2 pass, complete save/
shutdown06:55:35. Red evidence preserved in scene-game-test-strike-conflict-red-20260923;
prior cargo native evidence preserved separately. Initial62648 was a corrected
test import compilation error. No live operation or deployment. This does not
resolve UNKNOWN interrupted-hit evidence or close overall SA06/07.

Cargo-drain race repair: explicit LogisticsSceneCause.carrierDisposition is
declared by admission and changed atomically to RETAIN_WORLD_CUSTODY by accepted
CargoCarrierReleased. Scene copies retain it, with terminal handoff rejecting
PREPARED/HOT resurrection. confirmCargo reads that explicit declaration rather
than current inventory. Snapshot179 and logistics payload envelope0xfffd encode
stable disposition tags; old missing-field envelope rejects, not a default.
Assault envelope remains unchanged. No migration/deployment of existing178 worlds.
Original red now passes with full cargo transfer to player, snapshot before and
after transfer, UNKNOWN recovery and final drain. Missing/unknown/null disposition
and old envelope tests reject. Session67890 terminal0,20s:16 selected tests plus
4 architecture checks pass; earlier73519 compiled NeoForge test sources (28s).
This closes that reproduced producer defect, not SA06/07 or native crash acceptance.

Blocking producer defect reproduced: confirmCargo currently selects REMOVE vs
RETAIN from inventory.hasWorldCarrierCustody at scene closure. Between accepted
CargoCarrierReleased and final body drain, ordinary player pickup can remove
the last WorldCarrier account. The carrier was irreversibly released, but close
then manufactures REMOVE_PROJECTION. Regression
playerTakingAllCargoBeforeBodyDrainCannotTurnReleasedCartIntoRemovableProjection
uses actual release and transferObservedToNewAccount before scene release;
session39530 terminal1,10s: RETAIN expected, REMOVE actual. First84420 only exposed
incorrect exact-item fixture assumption; final reproducer uses actual fungible
account/binding and is the relevant red. Four accompanying architecture tests pass.

Required repair: explicit persisted carrier disposition owned by LogisticsSceneCause,
updated atomically by CargoCarrierReleased, preserved in placement/recovery copies,
consumed directly by terminal retirement. Do not infer release from phase, string,
missing cargo or current inventory. Review stable wire version and old payload
boundary, then rerun this discriminator plus existing release/snapshot tests.
Current candidate must not be deployed; SA06/07 remain open.

Retained-world implementation: save/read candidate selection now distinguishes
sealed RemovedCandidate from RetainedCandidate. Bounded UUID->NBT parsing rejects
duplicate identities and malformed trees. A positive retained proof requires an
actual serialized minecraft:chest_minecart with all four scene keys absent;
malformed NeoForgeData is unknown, not absence. Current player contents and
position are not compared/restored. Exact pending RETAIN obligation supplies
authority; successful batch writes/reads and synchronize(true) still precede
CargoCleanupSaved. A loaded declaration blocks ack; the terminal transition does
not alter inventory. Natural reads cover retained carriers outside removal's
archived chunk index. This path does not delete physical entities or create
removal archives. Existing departure observation clears only after durable ack.

Late join relinquishes an exact retained-world declaration before stale inventory
snapshot comparison, preserving player changes. Unit30930 terminal0,6s:19 tests
(persistence11/batch8). Native17625 terminal0,37s:5 departure cases pass; extended
world-custody case removes historical scene/tombstone, changes actual cart contents,
returns actual NBT and checks declaration retirement preserves changed count and
produces the expected real Minecraft serialized postcondition. Shutdown06:41:53.
Server-ops used for isolated target/process/evidence preservation, no live change.
This is NOT actual production-dimension save-to-ack or process-crash evidence;
the test still uses overworld and ephemeral runtime. Destruction/movement before
first post-release save, abort/retry and full provider composition remain open.

World-carrier follow-through: existing Lifecycle interaction already relinquishes
scene declaration after accepted CargoCarrierReleased; it also attempts to repair
a torn physical declaration under canonical world custody. That repair previously
required retained scene history and its latest tombstone. hasCurrentDeclaration
now additionally validates the exact retained RETAIN_WORLD_CUSTODY obligation,
world, actual carrier UUID/type and all four declared fields. This preserves
existing tag-only release after historical compaction without touching player
contents or position. Closed CargoCarrierAuthority likewise accepts the exact
pending retirement, never as permission for live work. Unit53517 terminal0,9s:
4 tests pass; new case covers compacted tombstone, wrong epoch/UUID and each
missing/forged declaration field. Not yet native recovery acceptance. Actual
retained-world save acknowledgement and late-return adaptation remain OPEN.

Normal-ack budget follow-up: at most8 synchronous archive/WAL acknowledgements
per ordinary save pass, deterministic UUID round-robin with cursor advanced
even for a failed attempt. Filter already-acknowledged identities from captured
candidates; retain save evidence until all remaining obligations have completed.
This prevents both an unbounded4096-record fsync burst and failed-prefix
starvation. Metadata recovery separately processes at most1 record per pass.
Unit73486 terminal0,6s:17 tests pass (persistence9/batch8), covering a wholly
failing retained21-record queue across three windows, removed cursor, duplicate
IDs, input-order independence and wraparound. This is bounded scheduling evidence,
not a measured server-latency guarantee or real-dimension end-to-end acceptance.

Follow-up archive crash boundary: actual successful entity save+sync now creates
and directory-syncs an exact `.saved` hardlink before canonical acknowledgement.
Metadata compaction removes `.nbt`, syncs that unlink, then removes the marker
and syncs again. Restart inventory is capped at4096; one marker is processed per
ordinary save pass. Only absent canonical pending obligations permit metadata
compaction; retained obligations still require the normal physical proof path.
Markers do not authorize deletion/spawn/custody or another canonical transition.
Compaction failure remains queued. Marker-publication failure retains batch and
candidates, avoiding loss of retry when EntityStorage suppresses an empty rewrite.

Archive tests simulate reopening before/after the first unlink, contradictory
epoch, missing witness and foreign world. All20 selected tests pass: session97055
terminal0,6s (archive5/persistence7/batch8). Earlier29085 was green before the
final batch-retry correction and is not evidence for that correction. No native
rerun or deployment; runtime crash composition and bound on normal ack loop
remain unverified/unimplemented respectively. This is not SA06/07 closure.

EntityStorage's natural read now contributes exact chunk-position/UUID evidence
to the coherent save batch. This covers already-saved deletion followed by a
restart, where vanilla's emptyChunks cache suppresses another empty write.
No new load request or forced chunk is introduced. Read observation must finish
on the server thread before candidate selection can certify a pass; a later
same-chunk write invalidates it. Successful reads never waive a failed write:
IOWorker may return pending cached data rather than persisted bytes.

Recovered previous-run evidence: XML at 01:23:15Z records 12 passing unit tests;
native scene-departure log records all5 passing and complete shutdown06:23:43
local. No matching task process remains. Original terminal handle was lost;
do not invent its exit status. These native cases still use overworld/ephemeral
runtime and do NOT prove the production-dimension save-to-ack composition.

Further source review found that scheduling/copy failure in the read observer
could poison vanilla's otherwise successful load future. preserveReadOutcome
now isolates observer RuntimeException: its cleanup obligation fails closed,
but original read data continues unchanged. Original storage failure remains
failure; snapshot observation precedes the downstream consumer, and scheduling
alone does not claim async server-thread observation complete. Three new tests
cover these distinctions and failed-observation save-batch rejection.
Focused session66788 terminal0,8s:15 tests (batch8/persistence7), zero failures.
No repeat native campaign for this isolated future-composition correction.

Still open: ack-before-archive-compaction crash recovery, world-custody retained
declaration retirement, abort/retry paths, bounded completion processing and
actual production-dimension save/crash composition. No deployment or closure
of SA-06/07 is claimed.

## 2026-09-23: real harvest successor exposed exact-ascent livelock

Graphical scenario `disposable-sa-harvest-successor.json`, terminal session80788,
failed step7 waiting for terminal harvest `canonicalSuccessor`. Evidence:
`build/sa-harvest-successor-20260923/result.json`, preserved world
`v3-disposable_sa_harvest_successor-caf0853c`, artifact
`c4c9d0c2d140f9b5b2291d4c3b3c9d349b896a843467aeb36fbd59e1f576840c`.
Actual HOT harvest completed64 cells, confirmed wheat64 and entered GROWING
epoch2 without quarantine. This is not a successful production/lifecycle test.

Offline snapshot decoding corrected the initial diagnosis: production DID start.
Job `job:production-7-79`, worker `resident:7-15`, materialized exact wheat input,
HOT lease `lease:production-work-7-79-r6753` remained APPROACH at traversal cursor74
through snapshots at ticks36000–42600. Current support95/63/-1, next95/64/0.
Read-only Anvil/NBT inspection of the exact graybox dimension found that worker
UUID3788d1ce-04f4-3011-9c86-55a0aa2fb87f at95.5/65/-0.95117: clear air above
the source, solid concrete target, not a missing worker/input or absent support.
Saved fallDistance667.55 is abnormal and must not be mistaken for real descent;
check ordinary fall/landing behavior in subsequent validation.

Cause in shared `FrontierV3ControlledMobMotion.apply`: gravity runs before motion;
the exact-endpoint ascent restores target Y then unconditionally returns. Next
turn repeats gravity/lift with no horizontal step. The older grade test called
approximate `moveToward`, whose tolerance bypassed this exact-endpoint loop.
Changed that test to the actual production `pursueRetainedTraversalEdge` call
and exact semantic arrival. Native session73832 reproduced the failure (1/18,
26s); preserved under `frontier-v3-scene-game-test-ascent-red-20260923`.

Repair: after the collision-checked lift, recompute actual delta and allow the
lateral half in the same turn only if the required height has been reached.
Incomplete/blocked lift still returns. No route/cursor/position fabrication,
teleport or gravity disable. Added a `local-navigation` slice for the existing
batch, without removing it from full scene coverage. Session89753 terminal0,
28s:18 native tests and10 focused unit tests passed; shutdown05:05:00 host time.
Native suite includes absent support, foreign obstruction, thin surfaces,
undeclared fluid and retained-edge continuity cases. Prior native evidence
preserved before rerun. No live deployment or full successor closure claimed.
Next: verify corrected actual successor lifecycle/recovery and landing behavior;
do not repeat the unchanged red candidate or inflate its timeout.

Landing follow-through: strengthened the same actual production-edge test to
stop after exact arrival, apply ordinary gravity settling and require full health
and fallDistance0. Session59825 terminal0,26s; all18 native navigation tests pass.
No additional runtime patch needed. This proves a fresh corrected ascent lands
safely; it does not establish recovery of every historically corrupted body.
Prior native green retained under `frontier-v3-scene-game-test-ascent-green-20260923`.
Corrected graphical candidate started as session56548, artifactab7ecc25791b6c13063de58022baad38cc653d9be17d8b8811a8812b93acaef9,
world `v3-disposable_sa_harvest_successor-9249234e`.

Completed56548 terminal0: manifest status `ok`,15 actions, final client timestamp
00:15:04.969Z; server graceful save05:15:15 host, PID1823040 absent. Prepared
source and runtime artifact unchanged throughout. Exact terminal successor
`order:production-7-79` consumed harvest wheat and produced64 bread; production
HOT33900 advanced beyond former cursor74, terminal-effect-ready/released around
05:14:58. Site diagnostic at instant36326 reports epoch2/GROWING stage0,
confirmed harvest receipt, TERMINAL successor and no conflict. Client's actual
container wait/open and132-cell successor-field predicate passed; summary
requiredConflicts0. Evidence `build/sa-harvest-successor-ascent-20260923/result.json`.

Frames personally inspected: bread64 visible in open chest; young field visibly
present on return. Initial farmer frame is occluded and not work-visibility proof;
final board is dark/distant. This is useful exact lifecycle/physical endpoint
evidence, not full continuous-work visual/M3 acceptance. No second harvest or
restart split in this scenario; terminal diagnostic also explicitly reports an
incomplete COLD/HOT causal trace, not a promotion receipt. Do not rerun this
unchanged case for confidence. Remaining successor/restart/other-family findings
remain open and require their own discriminating checks. No live deployment.

### SA-06/07 remaining retirement-retention boundary (read-only follow-through)

Coherent save-pass follow-through: replaced immediate per-chunk sync/ack with
EntitySectionManager autoSave/saveAll completion capability. A skipped chunk
marks autoSave incomplete. Bounded latest-write futures retain earlier unload
failures and cover both old/new chunks before one synchronization. Later writes
invalidate the pass ticket. Exact successful rewrite can settle a prior failure;
different-chunk success cannot hide it. No-candidate passes need no additional
flush; overflow fails closed without evicting unresolved observations.
Unit19863 terminal0,10s:7 tests (batch4/barrier3). Native10208 terminal0,37s:
5 departure tests and new save-pass mixin startup/shutdown,06:16:32. Not actual
production-dimension moved-cart/restart acceptance.
Source-traced remaining edge: after saved deletion and restart, EntityStorage's
emptyChunks cache can suppress the next empty write. A write-only producer then
cannot acknowledge. Implement an exact natural storage read-back + durable sync
path, not inference from absent in-memory entity or force-loaded chunks.

Entity-write hook WIP: exact SimpleRegionStorage.write future is now observed
from both vanilla EntityStorage branches. Candidate absence requires bounded
serialized root/passenger UUID inspection plus retained canonical retirement
and archived physical witness. Successful write then synchronize(true) leads to
server-thread identity/custody/generation recheck, durable CargoCleanupSaved and
exact archive/SavedData compaction. Failed write/sync retains obligation.
Unit51898 terminal0,10s:6 tests; native12362 terminal0,36s:5 existing departure
tests and successful mixin/server lifecycle, shutdown06:10:20. Native overworld
fixtures do not exercise the production-dimension runtime acknowledgement route.

Remaining safety analysis: per-chunk generation does not yet establish coherent
cross-chunk retirement after movement (an older stored copy can belong to a
different chunk). Extend to an exact save-batch/old-location closure before
acceptance; do not treat current producer as delivery-ready. Natural-read retry
after saved deletion/restart, orphan archive cleanup after committed ack,
world-custody declaration retirement and abort/retry paths remain OPEN.

Provider witness durability: found that SavedData.setDirty did not ensure the
final inventory witness survived before cart deletion. Added immutable bounded
CargoCleanupArchive under the exact server world, keyed by world/physical UUID.
It syncs complete NBT before no-replace publication and directory sync; conflicts,
corruption/foreign world, partial unpublished writes and oversize files fail
closed. Parent-directory retry also syncs a prior possibly incomplete creation.
Late-return and ordinary loaded deletion retain this witness before mutation;
archive can supply exact observation after SavedData loss. Loaded deletion clears
contents before discard. No archive record authorizes cleanup without matching
canonical retirement and physical contents. IO failure retains projection.
Unit41414 terminal0,12s:7 tests; native19551 terminal0,38s:5 tests, including
archive-only return after removed SavedData witness and historical compaction.
Shutdown06:02:41; parent retry-sync unit95612 terminal0,8s. This is witness
durability, NOT deletion durability or full process-crash proof. Entity write/sync
hook, acknowledgement, archive compaction and remaining paths remain OPEN.

Canonical acknowledgement implemented: CargoCleanupSaved carries the complete
pending obligation; common recovery owner removes only an exact match. Closed
process routing, ordinary diagnostic classification and WAL codec are registered;
snapshot and payload reuse the same nominal retirement encoding. Ack requests
DURABLE_BEFORE_EFFECT. Registered engine regression releases a scene, admits ack,
checks unchanged custody/inventory and replays the ack WAL from its preceding
snapshot to the identical checkpoint. Model negatives cover forged disposition,
stale repeated ack and preserving another attempt for the same cargo.
Gradle74398 terminal0,17s. Two preceding implementation failures corrected:
test enum name (24749) and missing diagnostic kind registration (34936).
These tests supply acknowledgement as explicit input; they do NOT prove physical
save. Minecraft exact-write+sync producer, loaded/abort paths and crash-window
composition remain OPEN. No runtime cleanup acknowledgement is emitted yet.

Reference/epoch closure follow-through: world construction/full pre-WAL and
recovery validation now validates pending retirement world and any retained
scene endpoint (CLOSED, logistics, exact revision/cargo/world). Historical
compaction remains legal. Relationship inventory and identity-surface manifest
explicitly register the terminal authorization owner, not a live cargo relation.
Recovery aggregate rejects live/pending same physical identity and overreserved
capacity. Epoch allocation and preparation include still-pending retirement
epochs, preventing reuse after latest-tombstone compaction. Distinct successor
owner with higher epoch remains legal. Gradle16965 terminal0,25s:24 focused
tests pass including foreign-world insertion at world boundary, active scene,
stale epoch, same-owner reactivation and legal successor negatives/positive.
No native repetition for this model boundary; durable-save/ack and remaining
physical paths are still OPEN, so neither SA06 nor SA07 is closed.

Physical consumer follow-through: exact pending retirement now replaces the
compacted scene/latest-tombstone requirement for late cart return. Declaration,
revision/epoch, world, cargo, UUID and unchanged final inventory must still match.
Caption removal uses the retained nominal IDs. Neither world custody nor foreign
inventory is deleted. Departure witness is no longer consumed merely because
discard ran; canonical pending obligation remains until durable acknowledgement.
Custody predicate is shared through ExactInventory. Native68208 succeeded41s:
all5 scene-departure/death tests (including both late-return cases after explicit
historical compaction);5 cargo-release model tests pass. Native shutdown05:45:52.
Initial13184 compile failed missing test import, corrected before native startup.
Capacity72355 succeeded13s:5 tests, including4096 pending obligations refusing
new cargo while unrelated body prepare/run/observe/confirm remains available.

Save-boundary inspection of the pinned local Minecraft sources:
EntityStorage.storeEntities submits SimpleRegionStorage.write and reports its
future asynchronously; EntityStorage.flush(true) synchronizes the storage queue.
IOWorker.synchronize(true) observes current pending writes then flushes regions;
an already-completed failed individual write cannot be inferred successful from
a later successful flush. Durable cleanup needs the exact successful chunk write
and subsequent sync, not just setDirty/discard/server-save callback. This seam,
reference registration and normal loaded/abort paths remain OPEN. Keeping all
obligations forever is an intermediate safety state, not completed bounded cleanup.

Terminal-producer follow-through: logistics release now creates its pending
obligation in the same world update as CLOSED/confirmed authority. Existing
world-carrier custody selects RETAIN_WORLD_CUSTODY; ordinary COLD return selects
REMOVE_PROJECTION without removing canonical cargo. New cargo preparation checks
reserved capacity; the physical logistics admission loop yields locally at that
limit. Gradle19317 terminal0,26s:17 focused tests pass and NeoForge compiles.
New coverage exercises ordinary release and full-state roundtrip; registered
cargo-release regression asserts retained world custody. Capacity saturation,
reference closure, physical consumer and durable acknowledgement still need
integration/verification; this is not complete runtime retirement or SA closure.

Implementation follow-through (2026-09-23): bounded nominal cargo-retirement
obligations are now embedded in FencedRecoveryState and preserved through its
internal transitions/compaction. Insertion requires the exact retained terminal
authorization; codec178 stores obligations independently of the compactable
tombstone map. Focused Gradle88308 succeeded in18s: recovery7 + retirement4 +
codec1 tests; NeoForge compilation successful. Codec regression covers roundtrip
after tombstone removal, unsupported disposition rejection and rejection of
unauthorized insertion. This does not yet establish runtime cleanup: terminal
producer/admission capacity, reference registration, provider consumer and
durable-save acknowledgement remain to implement and verify. SA06/07 stay OPEN.
Existing codec177 worlds are untouched and must not be reused with codec178.

Remediation design, 2026-09-23 (implementation still OPEN):

1. Canonical scene retirement must retain an explicit physical-cleanup obligation
   for a still-existing/unloaded projection. Its identity includes declared asset
   type, exact asset/scene/entity, scene revision and authority epoch. It is not
   permission to spawn, replay inventory or infer an owner from tags. Retiring
   economic/scene execution and retiring its physical projection are separate
   results of the same owning transition, not an implicit timed grace period.
2. That obligation must survive scene-history compaction and successor attempts;
   the single latest `FencedRecoveryTombstone` is insufficient. The common recovery
   owner owns the pending obligation; transient adapter maps never supply it.
   Codec versioning and reference-closure registration must accompany the state
   change. Current canonical codec is177; do not silently read the new layout as
   an old schema or alter a running test world to repair it.
3. A returning provider compares exact declaration AND retained departure contents
   before cleanup. Foreign contents remain a local visible conflict. Explicit
   world/player custody forbids deletion even if an older scene retired.
   Cleanup is idempotent and cannot emit duplicated item drops.
4. Removing the pending obligation requires a typed, exact cleanup acknowledgement
   at the provider's real durable-save boundary. In-memory `discard`, `setDirty`,
   a CLOSED scene or an arbitrary later revision is not that boundary. If durable
   terminal proof is archived into provider storage, preserve the canonical
   obligation until the archive is durably acknowledged; never allow compaction
   between a canonical release and an unsaved mirrored proof. Do not introduce a
   mutable second economic/custody authority.
5. Pending/archive capacities remain bounded. Reserve the cleanup capacity before
   admitting a new physical cargo projection; exhausted capacity produces local
   admission backpressure with an explicit reason, not global invariant failure,
   arbitrary eviction, force-loading or deletion of unknown entities. Existing
   current projections must retain room to retire normally.
6. Regression sequence must include old unload -> canonical release -> newer
   attempt -> old-scene/tombstone compaction -> unchanged late return, plus a
   changed-inventory negative and world-custody negative. Split crash/recovery
   before and after physical cleanup/save/acknowledgement. Verify bounded capacity
   refusal leaves unrelated work active. Reuse current ordinary-unload greens;
   they do not cover this retention edge.

Implementation anchors inspected: `FrontierSceneLeaseStateSupport.release` and
`confirmCargo`, `FencedRecoveryState.retire/compactForNewBinding`, canonical
`FencedRecoveryStateCodec`, `FrontierV3SceneExecutor.release/releaseLoaded`,
`FrontierV3CargoDepartureObserver.observeJoin`, cargo departure SavedData and
`FrontierV3CargoCarrierAuthority.matches`. Do not patch only the join callback:
it cannot reconstruct a terminal authorization that both owners have forgotten.

While the corrected graphical candidate runs, source review confirms two
independent expiry paths for a legitimate unloaded cargo projection:
`FencedRecoveryState.compactForNewBinding` drops a terminal tombstone at4096,
and `FrontierSceneLeaseStateSupport.mayCompact` drops CLOSED leases at1024
unless a retained physical intent references them. Neither predicate knows about
`FrontierV3CargoDepartureLedger`'s exact unloaded-cart receipt. Repeated authority
attempts also replace the single tombstone for a cargo binding.
`FrontierV3CargoDepartureObserver.observeJoin` requires BOTH the retained lease
and its exact tombstone before deleting unchanged obsolete cargo; the normal
`discardClosed` path does not rescue a cart whose departure receipt remains,
because `owned` rejects concurrent departure evidence. Therefore late returns
after compaction can remain unresolved and consume bounded departure capacity.

Do not fix this by interpreting missing authority as deletion permission: the
native missing-tombstone/foreign-inventory/world-custody negatives are correct.
The pending repair must retain an explicit exact retirement authorization through
the physical cleanup/acknowledgment boundary (including crash/save ordering),
with a bounded retention policy shared with canonical scene retirement. Required
regression: release unloaded cargo, supersede/compact historical authorities,
return unchanged cart -> retire only obsolete projection; changed inventory and
accepted player/world custody survive; restart between each boundary cannot
replay cargo or silently forget unresolved evidence. No code change or closure
claim was made by this source inspection.

### SA-06/07 — successor scene cannot hide old cleanup (2026-09-23)

Native session12886 completed successfully in122.161s. Evidence:
`build/sa-remove-all-epochs-20260923/result.json` and its correlated PMV3 trace;
prepared artifact SHA256
`5adb1bc94ab3b9296fcc2a1d7ed86ebad325fffb7aa697d70e24df2454b94b95`.
The strengthened route-return scenario observes one pending retirement for the
same cargo after successor lease admission, then gracefully restarts. Its final
action15 observes HOT lease supply-1-11-r179, all three bodies and carrier CURRENT,
and zero pending retirements across all epochs at instant10424/revision322.
Unlike the previous per-current-lease flag, the new read-only diagnostic cannot
hide cleanup belonging to the preceding scene. A focused negative regression
establishes that distinction (31251 passed); scenario Node checks pass5 cases.

This closes the specific ordinary route departure/return/save/recovery cleanup
composition gap. It does not prove every storage crash seam, combat retirement,
or full SA06/07 closure. No additional visual claim is made from the retained frame.
Both owned server PIDs and scenario runner are absent after terminal cleanup;
manifest records port25585 closed. No live deployment or world mutation occurred.

## Evidence boundary

Main personally inspected implementation HEAD
`eaaa6e70d153e32c4856a5dabc487990e3cf9e06` in
`/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`, including the
preserved dirty `FrontierV3ResourceSiteHarvestExecutor.java` receipt-diagnostic
change. No audit-time server run, deployment or repair was performed.

The detailed scope is field growth, harvest, worker scene lifecycle, release,
harvest output and bread production; shared scheduler, scene/effect selection,
observation and reference-validation boundaries were also inspected. This is
not a complete audit of combat, hive, expeditions, medicine or every source
file. Shared mechanism exposure does not prove every family's internal logic.
Static reachability is not attribution of the last deployed incident; correlate
the actual source/world/trace before making that claim.

Source anchors below are relative to the audited implementation's `pale-mirror/`:
`K` = `pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/`;
`N` = `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/`.
Line numbers are audit-time aids; symbols and source identity are authoritative.

## Findings

All SA entries start OPEN. A focused test can establish a narrow correction;
it cannot by itself close the whole player-visible F0.6R3 story.

### SA-01 — retained due head starves independent canonical actions

Status: CLOSED for this scheduler defect (2026-09-23). See requirement-by-requirement
closure receipt below; this is not F0.6R3/native/product acceptance.

- Evidence: `K/kernel/InMemoryFrontierEngine.java:230` (`advanceTo`) requires
  each admitted action to remain queue head; `K/process/ResourceSiteHarvestProcess.java:187`
  (`planColdProgress`) reschedules a HOT/actor-custody-held action unchanged.
- Consequence: A remains the overdue head while independent due B is skipped
  on successive ticks. Canonical time can advance without B's semantic work.
- Owner: kernel scheduling plus the process continuation/hold contract.
- Required regression: held A and independent due B, constrained budget,
  subsequent release of A; B progresses, A retains exact identity/deadline,
  causal ordering, WAL monotonicity and recovery remain correct. Do not blindly
  replace head checks with membership and reorder newly created earlier work.

### SA-02 — abandoned in-flight field projection gates other fields

Status: CLOSED for the cross-field scheduling defect (2026-09-23). See the
criterion mapping below; this is not whole-lifecycle or visual/M3 acceptance.

- Evidence: `N/FrontierV3ResourceSiteExecutor.java:208`
  (`projectOneGrowthStage`) restricts demanded candidates to the global
  in-flight set whenever that set is nonempty; demand loss retains that work.
- Consequence: start A, leave before its bounded writes finish, visit B;
  A is no longer demanded and B is excluded, so selection can remain empty.
- Owner: bounded field-projection scheduling, not field economics.
- Required regression: partially project A, remove its demand, demand B, then
  revisit A. B progresses; A resumes its durable cursor without overwrite,
  duplicated work, force-loading or losing its recovery record.

### SA-03 — first waiting scene/effect monopolizes its family

Status: CLOSED for the shared selection/starvation defect (2026-09-23).
Closure against original criteria:
- Waiting/UNKNOWN A and HOT B: real shared inventory scheduler test services
  A/B/A/B for every SceneCauseKind; CONFLICT/CLOSED excluded, per-family cursors
  isolated, one callback per turn. This proves dispatch fairness, not every
  family's internal domain correctness. All8 source callers use this owner.
- Independent physical effect: new native
  `unavailableFirstEffectDoesNotBlockIndependentPhysicalProduction` starts a
  registered runtime with two explicit finished-labor/input preconditions.
  Unloaded A sorts first; actual `ProductionTransformationExecutor.tick` leaves
  it unchanged. Next tick writes64 bread in B's real chest, registered accounting
  creates the exact output lot and retires B's job. Third tick retains bread64
  and A's exact job/intent; A's chunk remains unloaded throughout.
- Ambiguous A must not replay: existing real effect-protocol regression observes
  only output for UNKNOWN while independent PREPARED B completes; rejected begin,
  foreign input and duplicate output cases remain covered (65814).
- Admission fairness/budget: shared FairTurn tests cover alternate admission,
  waiting attempts, removed cursors and bounded one-attempt service. Native29824
  proves demand filters the whole inventory rather than picking remote A first.
- Recovery safety: ephemeral service order never changes canonical work/custody;
  reset is tested and runtime forget is wired. Unavailable effects retain exact
  state; missing physical evidence never becomes an implicit confirmation.

Native87616 terminal0,24s:all5 production-effect GameTests pass. Initial94372
failed fixture setup because normal bootstrap provisions wheat only for
settlement1; the second input is now explicitly issued as test precondition,
not output. The earlier four tests still pass after reusable fixture extraction.
Failed evidence preserved under scene-game-test-paired-effect-fixture-20260923.
Shutdown05:22:52, no live deployment. This closure does not close SA06/07
recovery semantics, SA08/09 production variants or the full product goal.
Historical open-status follow-through below predates this composition review.

2026-09-23 follow-through: real `SceneTurnScheduler` now exposes its read-only
inventory boundary, with the unchanged production entry delegating to it. New
`FrontierV3SceneTurnSchedulerTest` covers every closed SceneCauseKind, mixed and
reversed inventories, UNKNOWN A -> HOT B -> A -> B, exclusion of CONFLICT/CLOSED,
family isolation, one callback per turn and explicit ephemeral reset. Fixture
leases test selection only; no invented world/physical success is claimed.
Focused scheduler/FairTurn/transformation/consumption selection65814 terminal0,
9s. Source call-path review confirms all8 executor families use the shared
active scheduler and demand-filtered fair admission; runtime forget clears it.

Native demand gate29824 terminal0,25s:2 GameTests plus8 slice unit tests.
The fixture's naturally loaded local candidate is selected despite an earlier
unloaded candidate256 blocks away; a real mock ServerPlayer provides demand,
while loaded-only/no-player remains ineligible. No chunks force-loaded by the
assertion. Added named route-construction slice for those existing tests, which
remain in the full scene gate. Prior landing evidence preserved intact.
Shutdown05:19:08, no task JVM remains. This establishes the physical demand
boundary, NOT paired actual-family terminal effects or full recovery closure;
SA-03 remains open pending that composition review. Do not rerun these unchanged
selector checks as a substitute for the missing composition.

- Evidence: `N/FrontierV3ResourceSiteHarvestSceneExecutor.java:41` and the
  production/medical/engineering/patrol/service/assault selectors pick the first
  active scene before knowing whether it can progress. Shared reclaim can wait
  indefinitely. `N/FrontierV3SceneExecutor.java:157` also selects logistics
  CONFLICT although its execution is a no-op.
- `N/FrontierV3ProductionTransformationExecutor.java:34` picks the first intent
  before chest/custody availability; UNKNOWN with unchanged input remains
  unresolved and still blocks independent later intents.
- Owner: shared bounded selection/lifecycle contracts and their consumers.
- Required regression: unavailable or conflicting A plus eligible B, for scene
  and effect queues; B progresses within budget without replaying ambiguous A.
  Check all affected selectors, not only the harvest example.

### SA-04 — terminal harvest conflict reporting throws and quarantines runtime

Status: CLOSED for the terminal diagnostic/disposition exception (2026-09-23).
Closure review covers each original criterion, without claiming full F0.6R3:
- Incomplete work: actual adapter witness regression checks every pending and
  confirmed crop prefix; the witness remains the exact next existing station.
- Complete work: the last station is used without nextCropSlotIndex. If the
  output receipt already consumed the job, explicit retained predecessor lineage
  supplies the exact site, not a recreated job or inferred string identity.
- Local disposition: registered-engine composition covers incomplete and complete
  jobs, canonical carrier-fence reason/policy, exact schedule cancellation, paired
  scene CONFLICT, journal replay and unrelated field progress with engine ACTIVE.
  Existing accepted evidence61371 is dependency-valid for those unchanged paths.
- Post-receipt branch: terminal regression covers growth to READY, UNKNOWN
  recovery without HOT replay, conflict, observed death and same-body/empty-survivor
  release. Newly added full state/reference ingress validation passes for normal,
  UNKNOWN, CONFLICT and dead states (87494 terminal0,7s). Snapshot parsing alone
  was not used as proof of reference closure.
- No catch/fallback suppresses canonical corruption; site and cause lookups still
  reject absent/foreign authority. Scene-specific command keys prevent independent
  incidents sharing a receipt identity. Shared physical death boundary is separately
  covered by native10556, not presented as direct terminal-harvest native evidence.
Reviewed source hashes: HarvestSceneExecutor
fc678928507f84770b4f68847d698b5966b3fac52531e917b535774ade217097;
FrontierResourceSiteHarvestSceneSupport
c716a45f9ddc79862c1dc7c200217b26dce0345c4b731b0d909dbfc3bd3feaa5.
Remaining full harvest/production successor and actual family restart evidence
stay in SA05/06/08/09 and overall product acceptance. No native/deploy this step.
Earlier open-status notes below record intermediate states, not current closure.

Update 2026-09-23: registered-engine composition now covers incomplete and
completed HOT work in DRAINING, exact local carrier-fence disposition, atomic
continuation cancellation, scene CONFLICT, journal recovery and independent
field progress with engine ACTIVE. The adapter witness regression covers all
64 pending/completed prefixes. This is not native body-loss or full-cycle proof.

Source follow-through found a second defect: carrier-fence command IDs contained
only fence kind and scene revision. Independent scenes with equal revisions
collided in the engine's retained command receipts (DUPLICATE_COMMAND), preventing
the second local disposition. IDs now include a bounded deterministic UUID of
the explicitly supplied SceneLeaseId; that digest is correlation/idempotency,
not inferred ownership. Regression checks separate scenes/revisions and stable
retry identity for every fence result. Final targeted Gradle selection61371
passes (6s): HarvestSceneExecutorTest and cached unchanged
HeldHarvestSchedulingCompositionTest (executed successfully in37612).
Initial edit had missing/wrong identity type and then nested-colon command-ID
format errors; corrected to nominal SceneLeaseId and bounded digest. No invariant
or assertion removed. No native run/deployment in this increment; SA-04 remains
open pending final affected-call-path closure review.

- Evidence: `N/FrontierV3ResourceSiteHarvestSceneExecutor.java:410`
  (`carrierFenceConflict`) asks for `nextCropSlotIndex()` on a possibly complete
  job; `K/model/ResourceSiteHarvestProgress.java:26` rejects that state.
  `N/FrontierV3ServerLifecycle.java:443` quarantines on the escaping exception.
- Consequence: completed harvest -> DRAINING -> carrier fence failure cannot
  report its intended local incident and instead stops the whole simulation.
- Owner: harvest terminal diagnostic/disposition boundary.
- Required regression: incomplete and complete jobs with a failed carrier fence;
  exact valid witness and local disposition, no invented next crop or swallowed
  corruption exception.

### SA-05 — lawful mature successor rejected as physical drift

Status: CLOSED for lawful field-successor classification (2026-09-23).
Final review reuses valid evidence rather than rerunning an unchanged matrix:
- GROWING and READY: shared awaitingHarvest/matchesCanonicalProjectionTarget
  accept the exact requested canonical stage; native FieldTurnGameTests covers
  resolved and pending terminal receipt branches at growth and maturity.
- Active successor: whole predecessor is validated before reverse writes;
  native case covers exact active progress, pending old receipt, foreign last
  cell and resumed projection after volatile cache loss.
- Pending receipt: retains the terminal physical field; no regrowth writes may
  erase its evidence. Interleaved partial terminal writes resume their persisted
  prefix rather than being mistaken for an unrelated pending receipt.
- Real canonical receipt/retirement: ResourceSiteHarvestProcessTestTraversalTest
  runs registered planning/reduction at READY and HARVESTING, preserves exact
  successor job/worker/cursor/inventory, snapshots and rejects duplicate receipt
  (72815). Native fixtures alone were not used to prove that canonical transition.
- Foreign/damaged cells reject before writes and remain unchanged; saved-prefix
  and unvisited-suffix checks remain active through cache loss.
- Player composition: retained manifest1320 is `ok` for COLD first visibility
  through actual graceful restart;56548 is `ok` for HOT64 harvest -> exact bread64
  -> physically complete growing successor. Both manifests re-read at closure.

Native retained logs re-read: partial-receipt-interleave-green and
before-death-release directories both show all3 field tests passing. Current
ResourceSiteExecutor SHA256d08eda024faa8ef32eceb661f1e9dc41e76f4e5bc808060e2c8576c7d001de58
and FieldTurnGameTests1a7b74bc02a3544a071ec42056a86b6a8e6f84ddee655ff7aa7565463919370b
match the previously verified implementations. No new run/code change/deployment
for this closure. Terminal death/UNKNOWN ownership was addressed under SA04 and
shared death-release evidence; broader unload/retention/recovery remains SA06/07.
No second complete harvest, full-pack/M3 or whole-goal acceptance is inferred.
Earlier open notes below record the intermediate state before these later fixes.

Terminal recovery follow-through (2026-09-23): exact predecessor site lookup is
now shared by owner and physical carrier-conflict reporting; consumed jobs are
not reconstructed. Retained terminal relation covers local CONFLICT as well as
GROWING/READY, and scene membership validation admits DRAINING/UNKNOWN/CONFLICT
only with the lineage's exact worker. HOT/PREPARED still require an active job.
Registered SceneBehavior now owns recoveredStatus; harvest terminal recovery
chooses DRAINING, while ordinary scenes retain HOT and dead-member recovery
drains. Native reclaim uses that owner result and returns success only when its
command is accepted (previously it returned true on rejection too).
The harvest death hook leaves completed economic work intact; the common death
owner records death and retires body custody without reopening the output intent.

Extended terminal-receipt test covers UNKNOWN snapshot -> DRAINING -> CLOSED,
rejects attempted terminal HOT, local fence conflict snapshot with unchanged
inventory, and observed death -> snapshot -> empty-survivor release without
intent/output replay. Selection51774 passed13s. Broader scene/death/harvest and
adapter selection58058 exposed a stale hardcoded shipment ordinal in the existing
multiple-death fixture; it now uses the fixture's initialNorthwatchShipment
selector, retaining all death/release assertions. Final58638 passes19s; unchanged
adapter tests reused from58058. A source comment changed afterward only.
No native body-loss/death/restart acceptance or deployment is claimed.

Remaining concrete adapter gap: both harvest and production DRAINING paths call
fenceDrainingSceneBody even for canonically DEAD workers. That helper requires a
live Minecraft Mob, so a lawful death can still become ENTITY_UNAVAILABLE/DEAD
instead of releasing with no survivors. Align both callers through the shared
release/death authority; do not invent an inactive living carrier or erase an
unconfirmed missing body. This and native composition keep SA04/05/06 open.

2026-09-23 follow-through found an additional canonical terminal-release defect:
FrontierResourceSiteHarvestSceneSupport.terminalReceiptSite recognized the retained
predecessor only at successor growth stage0. A lawful scheduled stage0 -> stage1
while the farmer's DRAINING body exit remained pending threw "resource-site scene
has no exact active harvest" from aggregate validation. Regression70399 reproduced
this through the real growth reducer. Recognition now retains the exact lineage
through GROWING/READY in the immediately succeeding epoch with no active work;
foreign job identities and nonadjacent epochs cannot borrow it. No worker recreated,
receipt replayed or growth suppressed. Support source SHA256
95f10a47175337f364aa183b1952b78938437309daaf914c90872ce7ae5a607e.
Extended terminal-receipt regression advances all seven stages, round-trips each
snapshot, rejects a foreign predecessor and releases the original body at READY.
Final selection43984 terminal0,13s (harvest suite, held-scheduling composition and
NeoForge compile); initial negative fixture namespace corrected in68685.
No native acceptance/deployment claim.

Remaining source-backed terminal-release gap, not yet repaired: the adapter's
carrierFenceConflict still calls require(active job) after an output receipt may
have consumed that job. Simply calling field conflict then is not sufficient:
terminal relation currently requires GROWING/READY, and expectedMembers admits
only DRAINING (not recovery UNKNOWN/CONFLICT) without a live job. The same family's
death callback also requires the consumed job. Address these through retained
terminal ownership and common recovery/death handling; do not suppress exceptions
or manufacture a replacement job. SA-04/05/06 remain open for this composition.

- Evidence: `N/FrontierV3ResourceSiteExecutor.java:625` and its confirmed/deferred
  terminal helpers admit GROWING but exclude READY. The selection at :313 can
  then reject the old harvested claim when desired completed slots are zero.
- Counterexample: retained fully harvested physical field; successor grows in
  COLD to READY; player arrives before the successor job starts. The old surface
  is lawful predecessor evidence, not player damage.
- Owner: field epoch/projection transition classification.
- Required regression: GROWING, READY and active successor, with resolved and
  pending receipt variants; foreign/damaged surfaces must still fail closed.

### SA-06 — unloaded body and confirmed body loss conflated across release paths

2026-09-23 shared death-release correction: fenceDrainingSceneBody now returns an
explicit RETIRED_BY_DEATH disposition only for the exact current DRAINING scene,
exact member (actor + entity UUID) and canonically DEAD actor. No Minecraft body
lookup or inactive living-carrier creation is required. Missing/unobserved death
still follows ordinary physical inspection and cannot permit release. Harvest
and production callers both consume permitsRelease; post-release inactive-body
discard remains restricted to actual FENCED results.

New SceneDeathReleaseTest uses a real admitted scene, recorded death and snapshot
round trip; stale scene, foreign UUID, surviving member and all result policies
are checked. Targeted14 tests pass99826 (10s). Initial12674 failed compilation
because a fixture implementation helper is package-private; the test now asserts
one unambiguous fixture shipment instead of accessing that helper or guessing ID.
Native SceneDeathGameTests now checks actual body observer -> retired-death
release readiness, contrasting a physically dead but not yet canonically
observed second member. Scene-departure slice10556 passes all5 tests (37s total,
13.04s tests), shutdown04:36:39. Task JVM check is empty. Existing run evidence
was moved intact to frontier-v3-scene-game-test-before-death-release-20260923;
new logs remain in the ordinary scene-game-test directory. No live deployment.
This proves the shared physical death boundary, not full native terminal harvest
or production lifecycle; family model terminal-death evidence remains complementary.

- Evidence: harvest DRAINING fences its entity before shared unloaded release
  (`N/FrontierV3ResourceSiteHarvestSceneExecutor.java:113`); production has a
  separate unloaded-handoff branch (:268 in its executor). Entity absence maps
  to ENTITY_UNAVAILABLE in `FrontierV3AmbientActorExecutor.fenceSceneBodyResult`.
- Consequence: valid unload/recovery timing can become a false missing-body
  conflict; chunk presence is not consistently distinguished from entity readiness.
- Owner: common physical custody/release boundary, with explicit family hooks.
- Required regression: loaded present, entities not ready, fully unloaded,
  observed missing, dead and foreign-owned bodies. Never manufacture a body or
  treat unknown observation as confirmed disappearance.

### SA-07 — stale volatile release observation can overwrite health

Status: CLOSED for the stale-sample health overwrite defect (2026-09-24),
not for the wider SA-06 actor-recovery or cross-file crash windows. Current
source review of every production `SceneLeaseReleased` and
`AmbientLeaseReleased` constructor found no release fed by
`FrontierV3AmbientActorCaches.lastObserved`. The generic scene release reads
the current owned Mob's health, or accepts only a retained exact departure
receipt when the body is absent; missing/foreign/conflicting receipt cannot
become a stale sample. The ambient loaded release reads its current Mob;
the unloaded reserved continuation requires an exact current departure
receipt; PREPARED no-body cancellation reuses canonical health only after
its never-created or fenced-carrier evidence. The one `lastObserved` read
in the undemanded ambient probe has no release transition.

Current-source JUnit selection (2026-09-24, 17/17) covers stored final health
different from canonical baseline, scene/revision/baseline/kind/epoch mismatch,
missing receipt, conflicting second unload and preserved old evidence after
recovery. The retained native player scenario
`build/sa-worker-health-release-restart-20260923/result.json` has status `ok`:
an ordinary attack was followed by canonical 19 HP at CLOSED and after a
graceful restart. It does not distinguish every possible release route or
prove hard-crash entity-region/SavedData/WAL ordering; those remain SA-06/07
integration work. This closure is the narrower original stale-observation
overwrite, not a claim that all actor continuity is complete.

Integration coverage90291 completed OK19:12:52: checked-in
`disposable-sa-worker-health-release-restart.json` uses the existing production-work
fixture and normal empty-handed player attack on the named HOT worker, then
leaves demand, requires scene CLOSED/current canonical19HP, performs graceful
restart and revisits the same resident with19HP retained. Its schema validates;
no alternate command action or coordinate resolver was added. This is intended
to connect physical damage through common release to canonical persistence,
complementing existing departure/NBT/negative fixtures. All9 actions passed;
it does not independently prove that departure rather than loaded-body release
was the path chosen, nor physical body health from canonical health alone.

- Evidence: harvest/production remember the generic LAST_OBSERVED sample on
  materialization but not ordinary work; shared unloaded release reads it at
  `N/FrontierV3SceneExecutor.java:682`. `K/model/FrontierSceneLeaseStateSupport.java:135`
  copies the supplied health into canonical actor state.
- Consequence: damage/healing after the remembered sample may be replaced by
  old health on that release path. Restart also loses the volatile sample.
- Correction to earlier audit wording: production released position comes from
  the retained route cursor; an inevitable initial-position rollback is NOT proven.
- Required regression: changed health before unload, stale/wrong-epoch evidence
  and absent sample after restart. Only valid custody-bound evidence may update
  canonical state; missing evidence has an explicit bounded disposition.

### SA-08 — stationary production work uses different HOT/COLD time units

Status: CLOSED for the stationary labor-rate inconsistency (2026-09-23).
Closure review inspected the actual HOT adapter due-time guard and bound command,
ProductionProcess.planColdWorkAdvance, and ProductionWorkProgress.nextWorkDue:
both earn one stationary work unit per20 canonical ticks; arrival starts labor
without crediting travel, and delayed physical observation does not invent work.
Current-source selection67826 terminal0/21s passes all6 cases in
ProductionWorkClockTest and ProductionModeCompositionTest. Tests compare every
stationary deadline/progress, reject early HOT mutation, hold COLD work while HOT,
exercise two HOT visits with COLD intervals and checkpoint recovery for both
resource representations, compare final time/output/wage against COLD controls,
and directly compare exact/fungible representations at each boundary. Log:
`build/sa-two-harvest-receipt-fixed-20260923/production-clock-closure.log`.
Previously accepted native fungible midwork/restart and exact harvest-to-bread
manifests were re-opened and remain statusok; they provide adapter composition,
not a timing benchmark. This closure does not assert equal physical navigation
duration, whole-world recovery, SA09 exclusive-custody completion, visual M3 or
the still-running two-harvest product scenario. Those requirements remain open.

- Evidence: `K/process/ProductionProcess.java:452` schedules COLD cadence using
  `20 * workTicks`; `N/FrontierV3ProductionWorkSceneExecutor.java:172` advances
  one work unit per ordinary physical tick. Ordinary runtime advances one
  canonical tick per server tick.
- Consequence: representation mode changes stationary labor rate rather than
  only batching/navigation fidelity.
- Owner: production's canonical labor/cadence model.
- Required regression: equal starting progress/resources and equal canonical
  time for HOT/COLD and repeated switches; compare stationary work separately
  from declared navigation delays. Batching preserves the labor norm.

### SA-09 — resource representation changes production lifecycle

- Evidence: `K/process/ProductionProcess.java:158` completes FungibleCold work
  without route/work-stage completion although `fungibleJob` starts at zero.
  `K/model/FrontierProductionWorkSceneSupport.java:20` admits only Materialized
  input; Cold work has no corresponding return-to-HOT path in the inspected code.
- Consequence: equivalent wheat representations skip labor or prevent truthful
  visible continuation of already-started work.
- Owner: production progress and resource-custody composition, not animations.
- Required regression: equivalent exact/fungible inputs and COLD-start -> player
  ingress; same required work/output, one worker and exclusive resource custody,
  no output before eligible terminal progress and no parallel HOT/COLD writer.

### SA-10 — schedule-only transactions bypass reference closure

Status: CLOSED for the schedule-only publication/replay gap (2026-09-23).
See scoped closure receipt; other lifecycle/reference defects are not waived.

- Evidence: `K/kernel/InMemoryFrontierEngine.java:383` skips transaction
  validation when aggregate identity is unchanged, while scheduled-owner
  validation belongs to `K/model/FrontierReferenceClosure.java:56`.
- Consequence: the barrier cannot reject a bad newly scheduled owner before WAL
  publication merely because only the queue changed. This is a guard gap, not
  proof that the last live incident used that path.
- Owner: transaction validation of changed state AND changed relationships.
- Required regression: valid schedule-only mutation, absent/retired owner,
  atomic owner retirement plus cancellation, and recovery. Avoid rescanning an
  unchanged entire world merely to validate the changed schedule references.

## Accepted architectural decision

### Existing-world ambient recovery boundary (2026-09-23)

Additional release retry correction: retained exact carrier no longer causes
`canFence` to reject the same unfinished release; DRAINING resumes only with that
exact carrier, preserving observed damage and completing normal body retirement.
Native run32260 passed8 scene-departure/death tests46s, including real-body7HP
interrupted-DRAINING fixture and ordinary-release regression. This is not proof of
SavedData/WAL crash atomicity: the carrier fence currently marks SavedData dirty,
so durability ordering before body removal still requires closure. Likewise audit
UNKNOWN-returned-body with retained carrier and repair other historically missing
residents. No full SA06/07 or visual/product acceptance is claimed.

Follow-through finding: full-pack copied-world recovery restored farmer7-13 and
completed two COLD harvest/production cycles (epochs2 and3;64bread each), then a
real client observed that exact UUID HOT/INDEXED. However the population census
failed expected24/observed4: other residents became PREPARED/CARRIER_MISSING.
Source owner `AmbientActorExecutor` independently closed UNKNOWN_AFTER_RESTART
from loaded blocks/no indexed UUID with neither entity-storage readiness nor a
retained carrier. Its old GameTest bypassed ordinary physical admission and thus
did not prove its named fresh-admission claim. This is additional SA06/07 scope,
not a closed-world or cosmetic test failure.

Added entity-storage readiness and exact retained-generation custody guard;
missing custody remains explicit UNKNOWN pending evidenced recovery. Focused
run3125 green10s (four policy tests; production/pilot compile), modified native
test still pending. Prevention alone does not restore historical residents:
full actor recovery/remaining crash-path composition and native product checks
remain open. Evidence is under build/sa-ambient-world-copy.j4JKe3, especially
cold-second-cycle.txt, hot-ingress-diagnostics.txt, census-server-view.txt and
player-ingress-with-census.json. No visual acceptance frame retained. Fullpack
copy stopped via RCON with all-dimensions-saved13:27:10; no live fix deployed.

Live schema180 incident: resident7-13 remains ALIVE/IDLE with PREPARED ambient
revision2 and CARRIER_MISSING; site7 is READY7/epoch2. Read-only decoded snapshots
show revision1 HOT -> CLOSED before revision2 preparation. The precise old release
command is not retained in the inspected WAL interval. Both reservation release
branches contained missing-carrier bypasses; their existence is proven by source,
not proof of which one caused this individual incident. SavedData carrier inventory
is empty. Independent actor-tag and recursive UUID scans read all67 populated
graybox entity chunks and found no941022a1-9431-381b-a497-bb9d553971ac. The service
was running, so these reads are diagnosis, not an atomic repair authorization.

Do not fabricate a historical departure witness, reset lease revision to1, create
a replacement resident, or remove the reconstruction guard. A supported repair
must distinguish newly established offline absence from historical observation:

- Acquire the stopped world's exclusive session lock, validate the exact world,
  persisted canonical head and actor's PREPARED revision; no live repair by file edit.
- Scan every saved entity region in all world dimensions for the exact UUID,
  including passengers; unsupported compression, corruption, external unread data,
  a matching entity or conflicting carrier fails closed. Hash inputs into a receipt.
- On an isolated copy first, cancel the unacknowledged PREPARED authority through
  the registered domain transitions without changing actor identity/body/health,
  inventory, resource custody, tasks or field progress. Establish an explicit new
  inactive recovery carrier for that cancelled generation, not a claimed old body.
- Bind the recovery receipt to the world/head/actor/lease/UUID and resulting
  canonical transaction. Publish recoverably with a backup and restart reconciliation;
  the normal next lease must consume that carrier. Partial persistence must remain
  resumable or fenced, never silently create two writers.
- Verify unchanged actor/economy, actual resumed harvest and following continuation
  on the copy before applying the same bounded procedure during authorized deployment.

Implementation receipt (2026-09-23): pilot-only absence scanner, exact cancellation
plan and durable publication component now exist. Publication persists an immutable
receipt containing original SavedData backup, initial canonical state/head and
entity-input hashes before publishing the new inactive carrier; only then runs the
registered cancellation commands. All writes force file data and atomic rename plus
parent directory synchronization. Retry rejects unrelated head/custody/input changes.
It deliberately avoids runtime checkpointing, which would compact the original WAL.
Focused run93947 passed8 tests in10s, including injected interruption at receipt,
custody and canonical durability boundaries with actual file-store recovery and no
graceful shutdown, unchanged non-lease state/time, no duplicate commands and original
WAL retained. This is filesystem-component evidence, not native/product acceptance.
Locked-world entry wiring and world/seed/head validation now implemented;
run16940 passed9 focused tests in11s including wrong-target and held-lock refusal.
Stopped real-world copy at `build/sa-ambient-world-copy.j4JKe3/world` validated
seed20260918065/head26784/ambient generation2, scanned77 entity chunks; run61911
applied cancellation on that COPY ONLY to head26786/CLOSED. Unmodified copy is
retained as `world-before-recovery`. Live restarted with unchanged JAR and passed
deployment verifier (PID2883737, notBefore1790150912). No graceful-save log was
retained at stop, so this is stopped crash-recoverable-copy evidence, not graceful
restart acceptance. Isolated-copy resumed harvest/following continuation and live
application are still outstanding; cancellation is not full product closure.

No existing-world recovery has yet passed all these criteria.
The user's verified-fix deployment authority remains
in effect, but no reset, publication or unrelated world change is implied.

Keep one canonical simulation and the accepted HOT/COLD, exclusive custody,
typed identity, REL/ARC and persistence contracts. A new database, ORM, universal
SDK, whole-world rewrite or weakening of invariants is not justified by this audit.

Architectural corrections ARE authorized where a finding belongs to a shared
mechanism: bounded fair execution without head-of-line starvation (SA-01/02/03),
one evidence-aware custody/release protocol (SA-06/07), representation-neutral
work semantics (SA-08/09), and reference closure for schedule changes (SA-10).
SA-04/05 require complete local lifecycle/disposition branches. Reuse existing
typed registries and transition APIs; introduce only the abstraction needed to
eliminate an identified defect class. No per-settlement/coordinate/actor patches.

For each correction, identify the violated invariant and owning layer, retain a
small discriminating regression where feasible, repair the owner, and move all
affected consumers off the obsolete bypass. Preserve unaffected WIP and evidence.
Do not block useful repairs on a complete audit or redesign of every subsystem.

Priority: restore progress and safe terminal handling (SA-01 through SA-05), then
converge release evidence, production semantics and the reference barrier. Earlier
discovered dependencies may be fixed together; this is not an artificial gate
between individual fixes. No new MAT breadth before the existing F0.6R3 acceptance.
Integrate common-owner fixes with ARC-001E rather than creating a competing migration.

Verification proceeds from the smallest useful discriminator to affected
composition tests and the actual multi-settlement player story: leave/re-enter,
harvest -> custody/production -> next cycle, with relevant restart boundaries.
Assertions bind semantic state, not arbitrary sleeps. Native verification remains
necessary for visible/recovery claims, but equivalent expensive reruns without
new information are not progress. Do not requalify accepted F0.VA/VB/VC tooling.
No new run-count quotas or mandatory full matrix per finding.

## Execution and closure

User explicitly stopped Terra and assigned this audit and subsequent remediation
to main without subagents. That bounded assignment overrides the older sole-Terra
wording for this correction; it is not an automatic permanent workflow rewrite.
The 2026-09-22 follow-up authorizes main to start fixes after documenting this
decision. Documentation acceptance alone changes no code/deployment status and
does not authorize a new world/reset/publication.

Each SA closure records changed source identity, decisive regression/evidence,
affected consumers and remaining product scope here. SA findings remain open
until supported; reclassification requires a source-backed explanation. The
existing F0.6R3 human/product acceptance is not replaced by ten unit-test passes.

## First remediation increment — 2026-09-22

Main implemented focused corrections in the audited implementation worktree;
HEAD remains `eaaa6e70` with uncommitted changes. The prior harvest-executor
receipt-diagnostic WIP is preserved untouched.

- SA-02: ordinary field projection rotates over currently loaded/demanded
  eligible fields, without reserving all turns for the in-flight set. Per-field
  durable write/recovery cursors remain intact. Focused selection coverage is
  green; the actual leave-A/visit-B/resume-A world story is still pending.
- SA-04: carrier-fence diagnostics use the last field station for completed
  harvest and the next actual station for incomplete work. Focused coverage
  traverses all 64 prepare/confirm steps and the terminal witness. Actual local
  conflict dispatch/quarantine isolation remains an integration verification item.
- SA-05: GROWING and READY both admit lawful terminal predecessor handling;
  pending receipts remain pending. Mature-to-mature regrowth uses the durable
  reverse crop cursor instead of an empty same-stage write plan. Composed
  regrowth now also requires observed exact predecessor evidence, including
  the ordinary path, rather than trusting canonical lineage to overwrite drift.
  Admission negatives and cursor-tag round-trip coverage are green; physical
  writes/re-entry/restart and the full next-cycle story remain pending.

Evidence: before the fix, `:pale-mirror-neoforge:test --tests
'*FrontierV3ResourceSiteExecutorTest'` ran 13 tests with two expected failures
on READY admission. After the correction, the three focused suites
`*FrontierV3ResourceSiteExecutorTest`, `*FrontierV3ResourceSiteHarvestSceneExecutorTest`
and `*FrontierV3ResourceSiteHarvestExecutorTest` passed 32 tests (15 + 11 + 6),
zero failures/errors/skips, through the ordinary Gradle test task. Reports are
under `pale-mirror-neoforge/build/test-results/test/` in the implementation tree.
No server/client/native matrix or deployment was launched.

Governance `git diff --check` and `./gradlew guardrails --no-daemon` passed
(34 tasks). Implementation whitespace validation and focused tests passed, but
the implementation-wide guardrails command FAILED; it is not release evidence.
Failures include the architecture-debt checks and oversized Java files. The
changed field executor already had 1058 lines at HEAD before this increment;
other reported oversized files were untouched. Do not waive or raise ceilings;
remaining gate failures need source-attributed closure before release.

SA-02/04/05 are therefore IMPLEMENTED_FOCUSED, not product-closed. SA-01/03 and
SA-06..SA-10 remain OPEN. Next systemic correction is SA-01; consumption/replay
currently enforce absolute queue-head removal, so merely bypassing `isHead`
would not be a coherent fix. Preserve causal ordering, exact continuation and
bounded independent progress together at the scheduling boundary.

## Schedule-reference increment — 2026-09-22

SA-10 now has an incremental schedule-reference boundary in the kernel validator.
The transaction overlay exposes final retained created/replacement actions,
excluding transient actions cancelled within the same batch. The engine checks
this delta before WAL publication when aggregate identity is unchanged; replay
uses the same check. Initial schedules are validated as well. Changed-aggregate
transactions retain the full composed reference/retirement barrier.

The world implementation uses direct existing subject-index lookups and bounded
bootstrap/site fallback existence checks, rather than constructing a complete
world-ID union for every schedule-only action. This does not discover ownership
or change the accepted subject set. Generic complete-state validators still do
not re-audit an unchanged aggregate.

Focused regressions cover accepted creation, rejected owner replacement before
WAL with unchanged canonical queue/revision, final-overlay cancellation,
accepted replay and rejection of a malformed schedule-only WAL. The existing
harvest atomic retirement/cancellation test remains in the affected test set.
Full frontier-module verification failed as recorded below. SA-10 is not yet
declared closed; source-wide gates and affected composed evidence remain due.

## Owner-held scheduling increment — 2026-09-22

SA-01 uses a pure hold predicate supplied by the scheduled owner in the existing
closed process catalog. Harvest shares its custody predicate with its planner.
Holds are excluded before action/weight budgeting, keep the exact retained
schedule and do not generate no-op WAL transactions. Execution rechecks the
first eligible action, rather than mere snapshot membership, so newly created
earlier runnable work still fences later snapshot entries. Consumption and WAL
recovery use the same policy; ordinary runnable-head ordering is not removed.
A HOT command can consume its own held continuation under the same ordering
fence. No new durable schedule field, heuristic kind/owner discovery or second
continuation was introduced.

Focused regressions exercise budget-one independent progress, exact hold
retention/release, monotonic WAL replay, rejection of unauthorized head skipping,
new earlier work and cancellation after admission, and production registration
of the harvest hold. The final focused check passed 87 tests in 25s: kernel 23,
queue 6, harvest traversal 44, supply 10, ArchUnit 4; zero failures/errors/skips.
Command: `./gradlew :pale-mirror-frontier:test --tests '*InMemoryFrontierEngineTest'
--tests '*ScheduledActionQueueTest' --tests '*ResourceSiteHarvestTraversalTest'
--tests '*SupplyOperationProcessTest' --no-daemon`. Reports are in the ordinary
implementation module `build/test-results/test/`. A subsequent engine indentation
change is nonsemantic. SA-01
remains IMPLEMENTED_PENDING_COMPOSITION, not product-closed.

### Full frontier-module result after SA-10, before SA-01

`./gradlew :pale-mirror-frontier:test --no-daemon` ended in 6m19s:
820 tests, 10 failures. This is failure evidence, not a passing module gate.
Retain these unresolved failures for attribution and relevant repair:

- `HiveRouteEngagementProcessTest.coldCombatPersistsEveryExactStrikeAndFailsTheRouteWithoutAPlayer`:
  foreign-target admission did not throw as expected.
- `HiveSettlementAssaultProcessTest.battleWaitsForDistinctCompiledFloorsAndConflictsInsteadOfMovingASeparatedDefender`:
  `only assault conflict retains a diagnostic tuple`.
- `SettlementAssaultTest.hotReceiptUsesTheColdSelectedBodiesAndSurvivesReleaseAndCodecRecovery`:
  non-selected attacker admission did not throw.
- `MedicalEvacuationOperationTest.exactTreatmentMayCompleteOnlyAfterItsOwnHotSceneAndUnknownReceiptReconcilesWithoutReplay`:
  missing owner-supplied recovery-unknown diagnostic tuple.
- `MedicalEvacuationOperationTest.physicalExactSupplyReceiptPrecedesExactPatientRecoveryAndUnknownRetainsTheTeam`:
  boolean assertion at line 137.
- `ProductionProcessLifecycleTest.activeMaterializedProductionRetainsInputUntilOneDurablePhysicalTransformationConfirmsOutput`:
  boolean assertion at line 651.
- `ProductionProcessLifecycleTest.materializedTransformationCannotBePreparedBeforeTheExactWorkerFinishesItsRetainedCycle`:
  expected admission exception not thrown (line 357).
- `FrontierV3WalTailDiagnosticTest.readsOnlyTheBoundedDecodedTailAndNeverNeedsAStoreOrWorldMutation`:
  `IOException: Disk quota exceeded` writing its tiny temporary WAL fixture.
  Host check afterward showed 574GiB available on root and 6GiB on `/tmp`;
  quota cause remains unclassified, not evidence of a full 2TiB disk.
- `PhysicalIntentLifecycleArchitectureTest.commonIngressDoesNotDispatchByPhysicalKindOrConcreteFamily`:
  textual assertion about executable compaction/fair admission failed.
- `PhysicalIntentLifecycleRetentionIntegrationTest.ownerComposedAdmissionCompactsOnlySettledHistoryAndLeavesAnotherOwnerProgressingAfterRecovery`:
  missing stamped diagnostic tuple on recovery-unknown intent construction.

The original targeted command mistakenly used the traversal source filename
rather than its JUnit class name. The subsequent passing focused command uses
`*ResourceSiteHarvestTraversalTest` and genuinely exercises that suite. Do not
attribute traversal coverage to the earlier unmatched filter.

## Shared physical service increment — 2026-09-22

Risk: critical-code. SA-03 remains open, with common implementation now present.

- `FrontierV3FairTurn` retains one last identity and advances on an attempt,
  not only success. It chooses the next ordered current identity, wraps after
  removal and alternates eligible admission with existing-scene service. A
  no-candidate admission falls through to one active attempt; no tick executes
  all scenes. Service order is ephemeral, not canonical work progress.
- `FrontierV3SceneTurnScheduler` owns bounded runtime/family cursors. All eight
  registered scene families use it for active service and admission selection.
  CLOSED/CONFLICT scenes retain their existing semantics but do not consume an
  executable active turn. Shutdown clears these cursors.
- Logistics combines engagement and route candidates in the same service queue.
  Assault combines approach and battlefield candidates while retaining the
  projection-owned battlefield provider and no-structural-derivation boundary.
  No identity prefix selects an executor or owner.
- A two-assault composition regression initially caught repeated preparation of
  an already retained assault: the historical global first-active guard had
  hidden that admission assumption. The adapter now excludes exact assault
  subjects with any nonclosed scene before attempting a second admission.
  The regression installs two scenes through actual reducer commands and
  checks both receive service while the first execution intentionally waits.
- Production transformation uses the same attempt-based cursor. Its UNKNOWN
  inspection remains evidence-only: unchanged input is never replayed.
- Final focused command: `./gradlew :pale-mirror-neoforge:test --tests
  '*FrontierV3FairTurnTest' --tests
  '*FrontierV3AmbientAdmissionPolicyTest.registeredAssaultAdmissionUsesOneProjectionProviderThroughPreparedReducer'
  --tests '*FrontierV3ResourceSiteHarvestSceneExecutorTest' --tests
  '*FrontierV3ProductionWorkSceneExecutorTest' --no-daemon`: PASS, 17 tests,
  13 seconds. Algorithm tests cover waiting items, reordered inventory, removed
  cursor, empty queue and admission/active budget arbitration.
- Earlier whole `FrontierV3AmbientAdmissionPolicyTest` selection: 24 tests,
  one failure in
  `scheduledNutrientTransitionRetainsProjectionCompatibilityThroughRuntimeJoinAndFirewall`:
  empty runtime optional at the advance boundary (original line 463). Added
  status assertions exposed actual canonical quarantine: the scheduled-subject
  closure inventory omitted live `hiveColony.nutrientTransfers`. Added that exact
  retained owner surface (no prefix inference or relaxed missing-owner rule).
  The same full 24-test selection then passed in 34s. A follow-up negative
  assertion checks the continuation against the earlier state without that
  transfer: an existing requester task cannot substitute for the transfer.
  That exact nutrient test passed independently with the negative assertion
  (JUnit report timestamp `2026-09-22T16:11:42.379Z`).

Remaining SA-03 evidence: real unavailable/UNKNOWN transformation A with eligible
B and no ambiguous replay; scene demand/unload/restart composition across the
affected families; service-state cleanup and relevant native scenarios. The
physical-demand GameTest now exercises the same whole-inventory eligibility
filter consumed by the shared admission cursor (the obsolete first-only helper
was removed); that amended native test is compiled, not yet executed. Work-duration equivalence
remains SA-08, not a property inferred from service fairness. No deployment,
world reset, commit or publication was performed.

Governance `guardrails` initially failed on `/tmp` `Disk quota exceeded`,
including tiny Python temporary files. `/tmp` is a separate 30GiB tmpfs mounted
with `usrquota` (24GiB used, 6GiB free), while root has 574GiB available. No
files were deleted. Repeating with
`TMPDIR=/home/rd/proj/pm-governance/pale-mirror/build/sa-audit-tmp.FMTUBS`
passed all 34 governance tasks in 17s; this local generated directory is not
source or evidence authority. Exact per-user quota figures remain unavailable
(`quota` is not installed). Source guardrail debt still needs remediation.

### Transformation execution protocol regression

The actual loaded-slot branch of `FrontierV3ProductionTransformationExecutor`
now delegates to one package-visible `executeEffect` protocol with Minecraft
I/O supplied by the same production call site. No alternate test executor or
changed persistence semantics were added. Five focused tests exercise UNKNOWN
input followed by an independent PREPARED output through the shared cursor,
UNKNOWN exact output settlement without writing, refused RUNNING transition,
write mismatch and already-written RUNNING output. Terminal intent execution
is rejected before I/O. These are adapter-protocol tests with in-memory I/O,
not evidence that Minecraft saved a chest or recovered it across restart.

`./gradlew :pale-mirror-neoforge:test --tests
'*FrontierV3ProductionTransformationExecutorTest' --tests
'*FrontierV3FairTurnTest' --no-daemon`: PASS 9 tests in 8s,
reports `2026-09-22T16:14:40Z`. Subsequent shared demand-filter/GameTest alignment:
`:pale-mirror-neoforge:compileTestJava --no-daemon` PASS in 9s. Real unavailable
chest/custody, persistence/restart and native acceptance remain open.

SA-06/07 follow-up inspection confirms that server `observeEntityLeave` routes
only to ambient release, not to scene-specific custody-bound observation.
Generic unloaded scene release consumes a volatile sample merely because its
handoff chunk is absent; harvest fences the missing body before reaching it.
Refreshing a sample per tick alone is insufficient proof of final unload health
or custody retirement. The correction must join an exact departure observation
with the common release boundary, retaining safe pending/recovery for missing
or stale evidence; it must not treat chunk availability alone as body-loss proof.

## Release safety increment — 2026-09-22 (SA-06/07 remain open)

Further inspection corrects an important detail: `observeEntityLeave` invokes
ambient `observeLeave`, but that method always returns false, even after its
checks. It is not a working final departure-observation producer for either
ambient or scene custody. No unload/restart claim may rely on that call.

Added common `FrontierV3SceneReleaseReadiness` before harvest/production carrier
fences and generic release. A globally present exact-UUID body can be inspected
without its old handoff chunk. An absent living body waits while either its
canonical-current or retained scene entity column is unavailable; loaded blocks
alone are insufficient. Ready columns only permit further inspection, not a
new death fact. Canonical actor absence still throws as reference corruption.

Removed the generic release branch (and dead release method) which published
old `LAST_OBSERVED` health solely because the handoff chunk unloaded. Production
no longer bypasses physical inspection on that basis. This is a safety
increment, **not the complete unload handoff**: fully unloaded scenes currently
retain custody pending actual evidence; the final departure receipt producer,
epoch/revision validation, cargo and assault-specific paths and recovery
disposition must still be implemented. This intermediate candidate must not be
promoted as a fixed player flow. Merely retaining DRAINING until reload is not
the accepted final state.

Tests exercise the production readiness policy with a real canonical actor and
scene fixture: present moved body ignores the old chunk, absent body checks
both retained/current columns, and block presence without entity storage is
pending. `./gradlew :pale-mirror-neoforge:test --tests
'*FrontierV3SceneReleaseReadinessTest' --tests
'*FrontierV3ResourceSiteHarvestSceneExecutorTest' --no-daemon`: PASS 14 tests in
9s. The subsequent deletion removes only the now-unreferenced old release
method. Native entity lifecycle, health change and restart coverage remain due.

### Final-departure evidence producer (consumer still pending)

Added `FrontierV3SceneDeparture` and `FrontierV3SceneDepartureObserver`, backed
by a bounded separate evidence inventory in the existing physical carrier
SavedData. Records retain the declared actor kind, owner, UUID, physical epoch,
exact scene ID and scene revision, final body/health and canonical health at
capture. Recording a departure does **not** create an inactive carrier or
change canonical state. Carrier-ledger format is now 4; older formats fail
explicitly under the existing fresh-world-only policy. This binary must not be
installed over a format-3 world as an implicit migration.

The observer accepts only actual `UNLOADED_TO_CHUNK`, validates complete scene
and physical-carrier declarations, and captures positive-health surviving bodies.
Ordinary `owns` still rejects removed bodies; the separate `ownsUnloading`
entry accepts only that exact removal reason. No generic removed-body bypass
was added. The production entity-leave callback records this evidence; a
verified returning current scene body invalidates its previous receipt through
the public source-join composition. Other removal reasons never become unload
proof. Callback work does not release a lease or run a canonical command.

The consumer is **not connected yet**. Required next work: exact receipt versus
current scene/actor/fence validation, complete team and cargo observations,
atomic disposition of evidence versus inactive reconstruction authority, retry
after refused canonical release, bounded cleanup, assault adoption and restart
inspection. A callback receipt round-trip is not proof of Minecraft/domain
atomic persistence or scene closure. Current missing-receipt waiting remains
temporary WIP, not an accepted solution.

`./gradlew :pale-mirror-neoforge:test --tests '*FrontierV3SceneDepartureTest'
--tests '*FrontierV3AmbientCarrierLedgerTest' --tests
'*FrontierV3SceneReleaseReadinessTest' --no-daemon`: PASS 12 tests in 9s,
reports `2026-09-22T16:27:16Z`. Tests cover exact NBT round-trip, no custody grant
from evidence, idempotent repeat, rejection of changed health/epoch before a
return invalidates the old receipt, missing required fields, duplicate
persisted records, incompatible old format, foreign actor and revision mismatch.
Observer callback ordering and actual native unload/restart remain unverified.

### Departure-consumer connection — partial SA-06/07

The generic DRAINING release now collects each absent member's exact final
receipt, validating scene ID/revision, UUID, declared actor kind against the
canonical roster, canonical-health baseline and prior ambient revision. Raw
receipt presence permits inspection while entity columns are unloaded; it
does not waive these checks. A stale receipt is a local release conflict, not
silently accepted health or indefinite readiness waiting. Current live bodies
still supply current observations. All member observations are collected
before the generic departure-fence pass and canonical release submission.

Harvest/production pre-release carrier fencing now consumes that same checked
departure evidence when the body is absent. A rejected canonical release keeps
the receipt and idempotent fence; only accepted release removes consumed
receipts, retaining the inactive carrier for same-UUID reconstruction. A
returning same-scene body withdraws its exact provisional fence only when
its recorded scene/epoch/position/health agree. Divergent returning evidence
is retained and prevents the generic release from silently choosing the live
or saved snapshot. This does not yet prove the HOT re-entry conflict policy.

`./gradlew :pale-mirror-neoforge:test --tests '*FrontierV3SceneDeparture*Test'
--tests '*FrontierV3AmbientCarrierLedgerTest' --tests
'*FrontierV3SceneReleaseReadinessTest' --no-daemon`: PASS 15 tests in 10s,
reports `2026-09-22T16:32:47Z`. New tests exercise receipt recovery, final health,
idempotent provisional fencing, retained evidence before canonical acceptance,
rejection of wrong scene/revision/baseline/kind and exact fence withdrawal.
They do not yet execute a complete native release/restart or a faulting store.

Still open: cargo final observations, assault's separate stale-sample release,
UNKNOWN restart handling with outstanding physical effects, torn-save/return
reconciliation, expired evidence cleanup and whole native player story.
Do not turn every UNKNOWN scene into DRAINING merely because bodies departed:
body evidence alone does not settle its process-owned ambiguous physical effects.

### Assault convergence and obsolete-sample removal

The assault DRAINING executor now delegates to the same generic release as the
other scene families. Removed its unavailable-body fallback to LAST_OBSERVED
or `durableReleaseCheckpoint`, including the unused alternate unloaded-release
method. Confirmed strike processing is unchanged; a historical strike receipt
is no longer treated as a complete final health snapshot for absent survivors.
Removed the now-unconsumed volatile sample maps, writers and cleanup branches
from assault and the generic scene executor, together with their family call
sites. Recovery-inspection bounds remain intact under the specific
`MAX_RECOVERY_INSPECTIONS` constant. Source search finds no old sample/fallback
symbols in the scene executors.

After assault convergence, focused departure suites plus the registered
two-assault admission/reducer regression passed: 8 tests, 12s, JUnit timestamps
`2026-09-22T16:36:43Z`. Subsequent removal of unused generic sample machinery
passed `:pale-mirror-neoforge:compileTestJava --no-daemon` in 8s.
No native battle/unload/restart acceptance is claimed. Assault's bespoke
UNKNOWN recovery still requires review; cargo departure and torn-save handling
remain open.

### Cargo ownership checks before departure integration

Source review found that fungible cargo ownership compared item/components but
not quantity, and active cargo selection attempted logistics decoding for every
HOT scene family. Ownership now also requires exact count; selection filters the
registered logistics family before invoking cargo inspection. The focused
`FrontierV3SceneReleaseReadinessTest` report at `2026-09-22T16:41:13Z` has
4 tests, zero failures/errors, including a foreign-family selector regression.

`CargoCarrierIdentity.id` intentionally remains stable across scene revisions;
it therefore cannot prove a current-generation binding. Materialization now
persists an explicit cargo scene revision and ownership rejects missing,
wrongly typed or unequal revisions. Both native fixture producers were aligned.
The materialization GameTest now checks missing/replaced revision rejection and
exact-generation reuse; the impact fixture checks quantity mismatch before
release. `:pale-mirror-neoforge:compileTestJava --no-daemon` passes in 8s,
including pilot compilation. These new native assertions have NOT yet run.
Existing carts without the declaration are deliberately not silently adopted;
no live deployment or migration is claimed.

Follow-up review must distinguish a declared-but-invalid managed carrier from
an unrelated vanilla entity at the player interaction boundary: currently an
empty `activeLease` becomes NOT_MANAGED. Cargo departure, exact release recovery
and this failure disposition remain open; these guards do not close SA-06/07.

### Cargo interaction and native slice — 2026-09-22

Follow-up corrected the active-runtime interaction boundary: a carrier with any
partial scene declaration and no valid active/released custody is REJECTED,
not NOT_MANAGED. Successful canonical release retires its old physical scene
declaration and returns gravity to vanilla. If canonical world custody survived
but the old declaration persisted, that custody permits idempotent declaration
retirement without issuing a second release. This is not complete torn-save
reconciliation for cargo contents.

Another source defect was found in the shared impact hook: every ordinary
chest-minecart reached impact capture, whose missing managed custody exception
quarantined the runtime. Capture now applies only to an explicit canonical
WorldCarrier custody relationship. The actual interaction GameTest checks an
ordinary cart's damage leaves runtime ACTIVE; a partial managed declaration is
rejected without consuming the HOT scene; a corrected declaration passes the
real lifecycle release and subsequent partial fungible withdrawal.

Verification: `./gradlew :pale-mirror-neoforge:test --tests
'*FrontierV3GameTestSliceTest' :pale-mirror-neoforge:runFrontierV3SceneGameTestServer
-PfrontierV3GameTestSlice=cargo --no-daemon` PASS, terminal exit 0, 2 minutes.
5 selector tests and all 9 native cargo tests passed. Native log reports
completion at `2026-09-22 21:52:51` host time, 1.561 minutes; server shutdown
completed at 21:53:03. Evidence is in
`pale-mirror-neoforge/build/runs/frontier-v3-scene-game-test/logs/latest.log`.
The prior 12MiB run directory was preserved as
`frontier-v3-scene-game-test-before-sa-cargo-20260922`, not deleted. The new
explicit cargo slice remains included in the ordinary scene/full gates.

This executes the revision and quantity negatives previously only compiled,
plus retained cargo materialization, graded movement, missing-carrier UNKNOWN,
closed-fence cleanup, player withdrawal and impact/drop cases. It is not a
graphical human preflight or actual process-restart proof. Remaining cargo work:
departure/recovery, inactive-runtime interaction policy and non-quarantining
local disposition for a rejected managed terminal impact. SA-06/07 stay open.

### Scene body return admission — 2026-09-22

Unresolved departure evidence now prevents the common physical `owned` and
family-level `recognizes` checks from permitting HOT work. A distinct
`recognizesDeclaration` retains provenance for source ingress, death observation
and ambient diagnostics; it does not grant an execution permit. All existing
scene-family work callers retain the guarded check. A source scan confirmed
the declaration-only production callers are those lifecycle/diagnostic seams.

Exact return also validates the receipt against the canonical health baseline,
ambient revision and actor declaration before withdrawing a provisional fence.
Matching physical health/position alone cannot erase evidence against a newer
canonical baseline. Focused unit regressions cover wrong epoch, health,
position, baseline and exact resumption. First focused selection: 18 tests,
10s, JUnit timestamps `2026-09-22T16:58:54Z` onward.

Added native `departedSceneBodyRetainsFinalHealthAndBlocksDivergentReturn`:
actual Minecraft body/NBT, actual UNLOADED_TO_CHUNK removal, production departure
callback, changed final health, divergent returned-body refusal, retained
provenance and exact-return resumption. This is explicitly an adapter fixture,
not a real chunk unload/process restart or canonical terminal release: its
physical fixture coordinates are outside domain bounds, and no translated pose
is submitted as canonical truth.

The first 10-test cargo/departure attempt passed the prior 9 tests but failed
the new body's initial ownership precondition before departure. The new fixture
omitted `setPersistenceRequired` used by established scene body fixtures; that
was restored, with an explicit removal-reason/tag diagnostic. No production
ownership criterion was weakened. It also now stamps the complete declared
actor identity instead of the old recovery fixture's prefix/partial tags.

Final command: `./gradlew :pale-mirror-neoforge:test --tests
'*FrontierV3SceneDeparture*Test' --tests '*FrontierV3GameTestSliceTest'
:pale-mirror-neoforge:runFrontierV3SceneGameTestServer
-PfrontierV3GameTestSlice=scene-departure --no-daemon` PASS, exit 0 in 41s.
14 unit tests pass; native test passes in 13.41s, shutdown at host 22:05:30.
The explicit departure slice remains part of the full scene gate. Previous
green cargo evidence and the failed attempt were preserved in sibling run
directories (`*-cargo-interaction-green-20260922` and
`*-departure-fixture-failed-20260922`). No test process remains running.

Still open: terminal release/restart/torn-save composition, cargo final
departure, receipt cleanup and local bounded resolution. Cargo revision alone
also does not distinguish CONFLICT -> PREPARED reattempts: source
`FrontierSceneLeaseStateSupport.transition/reprepareConflictRecovery` retains
lease revision while advancing the recovery authority epoch. That epoch must
be accounted for before claiming complete cargo departure/recovery fencing.

### Cargo attempt epoch and recovery command consistency — 2026-09-22

`FrontierV3CargoCarrierAuthority` now validates declared cargo epoch against the
exact canonical CARGO recovery binding (owner, scene revision and epoch).
Materialization stamps it; ownership, movement and live carrier checks reject
missing/foreign/old epochs. Readiness no longer promises materialization without
current authority. Closed cleanup requires the exact retired epoch/owner/revision
and does not infer permission from absent tombstones. Accepted world-custody
handoff removes the epoch together with the old scene declaration. Legacy carts
without these fields are not silently adopted; no live deployment occurred.

The geometry GameTests now prepare an actual canonical scene before creating
their read-only fixture projection, so they use real recovery authority rather
than a fabricated fallback epoch. Both explicit native cart fixture producers
stamp the current canonical epoch.

The initial 9-test native cargo run passed 8 cases but exposed an additional
production contract contradiction: `SceneLeaseStatus` and the state reducer
allow CONFLICT/UNKNOWN -> PREPARED, and medical recovery constructs that command,
but `SceneLeaseTransition` rejected PREPARED at construction. Fixed the payload
to permit the existing recovery edge, with `appliesTo` validation in the command
planner rejecting absent/wrong-identity/illegal-source transitions before the
reducer. Direct CLOSED transitions remain forbidden; their release/recovery
protocol is unchanged. Codec round-trip and negative source-status tests cover
the command. One new unit fixture initially used an invalid production-job
namespace; corrected its declared ID, not the production validation.

Evidence:
- Cargo authority/selector tests: 8 pass (`2026-09-22T17:15:21Z`).
- Recovery transition/codec plus ordinary ArchUnit selection: 6 pass in 8s
  (`2026-09-22T17:15:53Z`).
- `./gradlew :pale-mirror-neoforge:runFrontierV3SceneGameTestServer
  -PfrontierV3GameTestSlice=cargo-authority --no-daemon`: PASS, terminal exit 0
  in 36s; native 12.77s, host shutdown 22:16:45. It exercises real canonical
  CONFLICT -> PREPARED, unchanged scene revision, advanced epoch, refusal to
  adopt/restamp the old physical cart, and preservation of that conflicting cart.

The focused authority batch stays included in `cargo` and `scene`. The earlier
failed run is preserved as `*-cargo-reprepare-failed-20260922`; prior departure
green is `*-departure-green-20260922`. No task-owned native process is running.
This is not a full medical-recovery, cargo unload/restart or whole-player claim.
Those remain open along with actor recovery-attempt epoch alignment and strict
identity validation on the torn-save world-custody declaration-retirement path.

### Cargo handoff and contradictory scene departures — 2026-09-22

The world-custody retirement path now checks the exact scene declaration and
canonical recovery epoch before clearing leftover managed tags. A foreign epoch
is rejected without erasing evidence; an exact already-released declaration is
retired without a second canonical release. Inactive runtime also rejects managed
container interaction while leaving unrelated vanilla carts unmanaged. Focused
`cargo-interaction` native run passed (13.32s native, 36s Gradle, shutdown
22:20:51); authority/selector eight unit tests passed. This fault-injects old NBT
after canonical handoff; it is not a real process-crash/restart claim.

Source review then found a second-unload defect: after departure health 9, a
divergent return and another departure at health 8, the ledger rejected the new
receipt but left health 9 eligible for canonical release. The common ledger now
retains one alternate conflicting receipt per actor, blocks release validation,
fencing, exact-return resumption and adoption, and preserves both observations
through save/load. Further contradictions cannot grow the inventory. Format 5
requires explicit conflict inventory; malformed list element types, orphan or
duplicate conflicts fail closed rather than silently loading an empty list.
No live migration or deployment has been performed.

Verification: `:pale-mirror-neoforge:test --tests '*FrontierV3SceneDeparture*Test'
--tests '*FrontierV3AmbientCarrierLedgerTest' --no-daemon`: 17 tests passed,
5s final run, reports timestamp 2026-09-22T17:27:49Z. Tests include recovered
contradiction refusing all consumer paths even when the old body snapshot matches.
Source `git diff --check` passed. Native repeated-unload composition and a lawful
resolution mechanism for conflicting physical evidence remain open; merely
blocking an unsafe release does not close SA-06/07 or the product story.

### Death observation must not depend on permission to work — 2026-09-22

Review of conflict resolution exposed an ingress/consumer mismatch: lifecycle
recognized an exact scene declaration despite departure evidence, but the inner
death observer used `owned`, which also requires permission to execute physical
work. It consequently ignored death of a body carrying unresolved observations.
The HOT/DRAINING death observer now uses exact nominal ownership, returns false
for a rejected command, and removes departure observations only after canonical
death is accepted. This does not grant the conflicted body permission to work.

The existing multiple-death native scenario now injects two conflicting retained
observations, asserts work refusal, observes zero-health native bodies, checks
both canonical deaths, retirement of obsolete observations and scene closure
with only survivors. It is observer/command composition, not an actual player
attack or real chunk-restart test. The scene-departure slice includes this batch.
First run failed before death on a fixture physical revision of zero; corrected
to the producer's `max(1, sceneRevision)` rule, with no production relaxation.
Failed run retained as `*-death-revision-fixture-failed-20260922`.

Final evidence: 17 departure/selector unit tests passed (17:31:01Z). Focused
`runFrontierV3SceneGameTestServer -PfrontierV3GameTestSlice=scene-departure`
passed both native cases in 24.05s (Gradle 48s), terminal exit 0, server shutdown
22:32:48. Source diff whitespace check passes. No owned test process remains.
Still open: living conflicting-body resolution, canonical CONFLICT/UNKNOWN death
admission (currently HOT/DRAINING only), provisional inactive-carrier cleanup,
cargo departure and complete actual unload/restart composition. Do not infer
whole SA-06/07 closure from these two native cases.

### Retained-custody death closure — 2026-09-22

The lease now owns `retainsMemberCustody`: all non-closed states retain physical
consequence responsibility, independently of work eligibility. Planner, reducer
and native death observer use it. Death in PREPARED atomically transitions that
scene to CONFLICT; HOT still drains, and an already suspended state stays
suspended. Duplicate death rejects before reduction. Exact body recovery
authority is retired from any current phase with owner/revision/asset/epoch
checks, not only RUNNING. An unrelated cargo binding remains untouched.
Adapter ledger cleanup requires canonical DEAD and absence of current body
authority, then removes the provisional carrier plus departure/conflict evidence.

The new all-state command/codec test caught a genuine incomplete transition:
PREPARED patrol death blocked its patrol while leaving the scene PREPARED, so
snapshot decode failed lifecycle validation. The atomic conflict transition fixes
that inconsistency without pretending physical work ran. An initial test accessor
typo (`nextAction` instead of tombstone `disposition`) was also corrected.

Verification: RoutePatrolSceneSupport/FencedRecovery/automatic ArchUnit selection
20 tests passed in 14s, reports 17:37:59Z–17:38:05Z. Covers all five non-closed
statuses through command admission and snapshot decode, exact tombstones,
duplicate rejection and foreign retirement negatives. Native scene-departure
slice: both cases passed, 26.48s native / 55s Gradle, shutdown 22:38:31, exit 0.
Its death fixture now retains a provisional carrier and proves living cleanup
refused, accepted death cleans it, other deaths and survivor release still work.
Previous green native directory retained as `*-death-observation-green-20260922`.

This closes these reviewed death-admission/cleanup branches, not every scene
family's death consequence composition or full SA-06/07. Living contradictory
evidence still needs an attributed resolution policy; actual unload/process
restart, cargo departure and actor recovery-attempt epoch alignment remain open.
All launched sessions are terminal; no deployment or publication.

### SA-08 shared production labor deadline — 2026-09-22 (partial)

Stationary HOT work no longer earns labor from executor invocation count.
`ProductionWorkProgress` defines the existing COLD norm of 20 canonical ticks
per labor unit. INPUT_READY starts the first deadline from observed station
arrival; PROCESSING requires its retained deadline and schedules the next unit
from the accepted observation time. Delayed physical work earns one unit, not a
catch-up burst for unobserved work. Movement remains ordinary native navigation.
The command carries the engine's exact existing production-completion action;
the kernel checks revision/action identity and the production planner checks its
job/kind/deadline. No second clock, volatile timer or persistence field was added.

COLD uses one unit per the same 20 ticks rather than front-loading eight units
then waiting 160 ticks. This raises stationary COLD scheduling from roughly ten
to eighty bounded actions per recipe; assess the aggregate workload at the later
scale gate instead of claiming a performance improvement. Recipe total labor is
unchanged relative to its COLD norm; previously faster HOT behavior is corrected.

Production's registered schedule owner now retains the action while a worker
scene is open. The first command-level test caught the prior reschedule loop:
the COLD planner moved the deadline forward before HOT could spend it. This uses
the shared SA-01 hold mechanism, without promoting the whole production SDK
descriptor to ENFORCED. A filesystem recovery test then exposed that the central
test fixture catalog wrapped `plan` in a lambda and dropped `held`; it now
delegates the same hold policy as the production configuration.

Evidence so far: all 80 stationary units compared using actual HOT engine
commands, actual COLD planning/reduction, exact deadlines and snapshot decode;
early commands rejected. Three clock tests plus four automatic ArchUnit tests
passed in 10s (17:46:41Z). Earlier base production/clock/architecture selection
passed 34 tests in 21s; it did not include the differently named
`ProductionProcessLifecycleTest`. The correctly selected lifecycle run retained
the two already inventoried failures (materialized input transformation/early
preparation), plus the clock failure subsequently fixed by registered holds.
These lifecycle failures remain open, not waived.

Filesystem restart scenario now advances real canonical ticks and submits bound
progress rather than 17 same-instant progress calls. After a `/tmp` quota failure
before the first command, use task-local java.io.tmpdir
`build/sa08-tmp.IbCODN`. The held-policy correction passed the restart test in
13s; final run with explicit retained-deadline equality also passed in 6s.
Native production-work fixture command binding is updated and compiled, not yet
run. Actual repeated HOT/COLD switching, delayed/admission composition, native
visible work and SA-09 fungible/COLD-to-HOT coverage are still required. No
production deployment or full SA-08 closure claim.

### SA-09 shared COLD labor and production retirement — 2026-09-22 (partial)

FungibleCold now runs the retained route and all 80 stationary work units before
completion instead of immediately converting wheat on its first review. COLD
also records INPUT_READY at the same penultimate input station as HOT, then
moves to the terminal work station. The same job, worker and lot claim persist.
`ProductionJob.requireColdCompletion` gates both event reducers and domain
completion methods: OUTPUT_READY, terminal cursor, living actor and exclusive
COLD custody are required. Physical transformation validation also requires the
terminal cursor. An ambient/open-scene holder defers scheduled completion;
resource representation is not permission for a second writer.

Tests now verify the 80-unit cycle, retained exact allocation and industrial
assignment at every step, no premature output, worker release at completion and
rejection of premature direct-domain/event completion. Old fixed-2200-tick
terminal fixtures now follow their actual retained work deadlines within a
route-plus-labor-derived action bound. One fixture falsely declared OUTPUT_READY
at cursor zero; it now initializes the worker/lease at the terminal station.

The two previously inventoried production failures were tests of the wrong
layer: raw `preparePhysicalIntent` / `transitionPhysicalIntent` storage methods
were expected to validate/complete the recipe. They now use the existing
`PhysicalIntentLifecycleFixture`, which dispatches through the real registered
owner, preserving the original early-rejection and completed-result assertions.
Final production/base/lifecycle/clock/ArchUnit selection: 87 tests pass in 28s,
reports 18:00:47Z–18:01:08Z. This includes the two formerly failing cases; other
failures from the old full frontier run remain unresolved until reviewed.

Native `production-work` slice exposed a real retained-reference defect: death
finalization removed the job but not its held completion schedule. SA-10 rejected
the release before publishing the orphan. The registered production finalizer
now cancels that same stable schedule in the job-retirement transaction. The
native death test explicitly checks absence of job-owned schedules after release.
Final native slice passes 3 tests (1.165s native, 26s Gradle, shutdown 23:03:08,
exit 0); selector tests passed 6 cases in the preceding run. Failed evidence is
preserved at `*-production-orphan-schedule-failed-20260922`. No test process left
running, no deployment/publication. Source diff whitespace check passed.

These tests cover model labor and the existing native station/death handoff
scenario, not a full native visual labor cycle. SA-09 remains OPEN: Cold exact
inputs are still held outside inventory and block depot activation; scene
candidate admission still accepts only Materialized input. FungibleBound exists
as a data type but lacks a complete ingress/effect composition. Implement an
explicit resource-custody handoff before broadening candidate admission; merely
accepting Cold/FungibleCold in the scene would permit invisible or duplicated
input/output effects. Actual COLD-start -> player ingress, repeated mode changes
and equivalent physical exact/fungible transformations remain required.

### SA-09 container activation replay guard — 2026-09-22 (partial)

Source inspection found command/reducer disagreement: container ACTIVE command
admission rejected an exact COLD production hold outside inventory, but applying
the same decoded event through the registered reducer admitted it. This could
publish a partial surface through replay despite the command guard. The reducer
now enforces the same common custody predicate before changing inventory or
recovery state. No schema or admission scope changed.

The existing cold-surface regression now encodes/decodes the activation payload
and invokes the actual configured reducer. It failed before the fix (expected
exception absent, 10s). After the fix, that regression, normal resource ingress,
normal owned-container transition and four architecture tests pass: 7 tests,
10s, exit 0, reports timestamp 18:09:47–18:09:48Z. This proves reducer parity,
not whole-process crash recovery or COLD-to-HOT transfer. SA-09 remains open.
No native run or deployment was needed for this pure reducer correction.

### SA-09 resource schema across custody modes — 2026-09-22 (partial)

Static tracing of the ingress composition found three consumers treating only
FungibleCold as a lot and falling back to EXACT_ITEM for FungibleBound: active
JOB_INPUT/JOB_OUTPUT relationships, production physical-retirement facts, and
TerminalProductionReceipt.of. Binding an existing lot would therefore change
its reported entity kind despite retaining the same identity. ProductionInputHold
now exposes one exhaustive closed schema projection covering all four variants;
these consumers use it without a default-to-exact branch. No resource is
converted, transferred or granted physical authority by this projection.

Two new tests cover exact Cold/Materialized and fungible Cold/Bound at two
epochs, checking resource endpoint kind, terminal receipt kind and unchanged
input/output/worker identity. Together with existing relationship declarations,
terminal fungible bread/provision composition and architecture checks, 11 tests
pass (16s, exit 0; reports 18:13:25–18:13:27Z). These are schema/model tests,
not an actual HOT account-binding or native production proof.

The next handoff must cover both initial surface creation and released-replica
catch-up. Source tracing confirms that generic surface PREPARED/ACTIVE is not
physical custody: ReferenceContainerCustodyExecutor separately declares,
observes and acquires a replica lease. Merely restoring the exact held input on
PREPARED would leave an interval before acquisition where COLD could finish and
change the inventory after the physical write. A complete transfer must close
that interval and preserve the existing before-write/recovery evidence, not
equate ACTIVE with custody. Fungible account binding must retain the existing
claim and epoch and use the physical transformation owner; changing candidate
admission alone is still prohibited by the accepted invariant. SA-09 remains open.

### SA-09 resumed worker handoff — 2026-09-22 (partial)

The production ambient handoff unconditionally called rebaseUnstartedTraversal.
Unlike the existing harvest handoff, it rejected every already-progressed job,
even with canonical and observed body exactly at the retained cursor. Fixed the
production owner: only untouched work recompiles its approach; progressed work
requires both positions to equal its retained station and returns the same job
without rebasing. Stale canonical or observed positions still reject. Adapter
comment now describes both cases; no teleport or relaxed physical observation.

ProductionResumedHandoffTest uses the actual engine command/reducer and snapshot
decode for both a retained approach edge and 17 processing units at the final
station. It proves unchanged complete job/topology/cursor/labor, inventory,
completion schedule and deadline, exact scene position, and closed ambient
custody. Negative cases independently reject stale observed and canonical body.
The valid command failed before the source fix (7s); final selection including
the existing unstarted-rebase/stale-anchor tests and architecture checks passes
7 tests (9s, exit 0). An initial test compile typo was corrected; it was not a
product failure. No processes remain from these checks.

This is model-level resumed worker admission, not actual cold input transfer or
a native visual cycle. The pending physical-write/acquisition gap and fungible
transformation composition remain open; no surface-status-based permanent COLD
block was introduced, preserving the replica/custody separation contract.

### SA-09 explicit pending projection custody — 2026-09-22 (foundation, not wired)

The existing custody registry only acquires after OBSERVED_CURRENT. It cannot
represent exclusive authority over the preceding write interval, which is why
merely moving held production input into inventory on surface PREPARED is unsafe.
Added PREPARING with stable unused wire tag 5 to the common custody model and
both lease decoders. prepareProjection admits only an exact EXPECTED replica,
fresh epoch and nonoverlapping scope/object. confirmProjection atomically updates
the observed replica revision and promotes the same epoch to ACQUIRED only on
matching fingerprint/provenance/revision. Checkpoint, blind release, ordinary
acquisition and duplicate confirmation cannot bypass that boundary. Live custody
and operational permission are explicitly distinct in ReferenceContainerCustody.

PhysicalProjectionCustodyTest covers snapshot retention, independent stale epoch,
canonical/replica revision, fingerprint and provenance rejection, no lost fence,
successful confirmation, duplicate rejection and subsequent normal release with
unchanged stock. Selected common custody/model/codec/architecture tests pass 16
cases; NeoForge compiles (17s total, exit 0, reports 18:21:47–18:21:49Z).

This phase is NOT emitted by runtime yet. Remaining adoption is mandatory before
claiming any COLD/HOT fix: registered durable prepare/confirm commands composed
with input transfer; initial and repeated projection adapter paths; recorded
actual mismatch/absence and lawful recovery rather than permanent preparation;
audit physical consumers to require operational permission, not merely a live
write fence; fungible physical transformation and native lifecycle verification.
Currently mismatching confirm is rejected and the fence retained; that is a
safety primitive, not the final diagnostic/recovery policy. No runtime activation,
deployment or whole-feature claim. Tests are terminal and source diff-check passes.

### SA-09 projection custody commands — 2026-09-22 (partial adoption)

Registered ProjectionCustodyPrepared and ProjectionCustodyConfirmed under the
existing replica-custody owner, with explicit stable payload codecs, declared
emissions and diagnostic-contract classification. Both require durable-before-
effect commitment. Planner checks and reducers call the same common state
transition; reducers additionally reject a foreign event scope. No composition-
root domain switch or implicit provider selection was added.

The new engine regression submits declaration/preparation through actual command
admission, recovers an engine from its checkpoint, rejects a stale confirmation
without changing revision/state, accepts the exact confirmation and rejects a
second confirmation. Codec coverage includes both payloads and trailing-byte
rejection. Reports confirm 21 custody/codec/catalog/architecture tests passed
(13s, exit 0); the requested DiagnosticProducerContractTest filter matched no
separate class, so no additional diagnostic test is claimed. This is checkpoint
recovery, not filesystem crash evidence.

The API is now registered and executable, but the Minecraft adapter still does
not emit these transitions. Resource transfer, initial/catch-up projection,
actual mismatch evidence and local recovery, physical-consumer permission audit,
fungible transformation and full native acceptance remain required. There was
no deployment, stock mutation, claim promotion or background task left running.

### SA-09 atomic exact input projection preparation — 2026-09-22 (partial)

ReferenceProjectionStateSupport and its registered durable
ReferenceProjectionPrepared command now compose the exact resource handoff:
restore each matching COLD held stack to its original free slot, change only
that job's hold to Materialized, prepare the initial container recovery boundary
when needed, declare/reemit the exact resulting fingerprint and acquire
PREPARING custody in the same published state transition. Existing job/worker,
route, cursor, labor, finance and schedule remain unchanged. A repeated projection
requires an ACTIVE surface, matching released predecessor evidence and fresh
epoch; foreign/stale predecessor or occupied input slot fails before publication.

Production completion review now waits on a live but non-operational container;
scene admission and new transformation intent validation likewise reject that
interval. This uses explicit current custody, not old surface history. Already
RUNNING/UNKNOWN physical receipt recovery is not disabled by the new prepare guard.

The new engine test covers exact Cold -> Materialized transfer at 17 work units,
single retained input/no premature output, unchanged schedule/finance/actor state,
no scene or terminal transformation before confirmation, duplicate preparation,
normal confirmation/release, foreign catch-up rejection and epoch-2 preparation.
Payload round-trip and snapshot hydration run through the registered engine.
Selected tests pass (13s, exit 0). Physical observations in this test are model
fixtures, explicitly not native evidence.

The Minecraft adapter still uses the earlier path: adoption remains incomplete.
Before activating it, complete actual mismatch/absence disposition and pending
write recovery, then initial/repeated adapter paths and relevant native tests.
Fungible claims are not converted by this exact-input handoff and still require
their bound-input/effect composition. No deploy or claim of SA-09 closure.

### SA-09 pending projection conflict evidence — 2026-09-22 (partial)

Added the registered durable ProjectionConflictObserved transition. It requires
the exact preparing scope/epoch/canonical/replica versions and an explicitly
stamped custody diagnostic; matching evidence cannot be labelled a conflict.
Actual fingerprint and provenance are retained in the replica with the typed
mismatch reason. The same transaction moves its write lease to UNRESOLVED with
the updated replica revision and retains the diagnostic under the exact scope.
No stock, world repair, release, new epoch or whole-instance quarantine results.
Diagnostic producer contract, extractor and payload codec include this boundary.

The engine test covers command admission, exact mismatch/expected evidence after
checkpoint recovery, same retained epoch, forbidden release/repeat and successful
unrelated command afterward. Payload round-trip/trailing bytes and existing
custody/diagnostic architecture checks also pass (16s, exit 0). No test process
remains. Source diff-check passes.

This closes evidence retention for the pending-write conflict, NOT its lawful
gameplay/recovery disposition. Adapter activation is still withheld until that
policy and interrupted-write resumption are implemented. Never clear the fence
solely because a timeout elapsed or overwrite changed slots. SA-09 stays open;
no deployment or native physical claim from these model tests.

### SA-09 observed projection-conflict resolution — 2026-09-22 (partial)

The existing registered projection confirmation can now consume a later exact
observation of a pending-write conflict, but only the UNRESOLVED /
OBSERVATION_MISMATCH / CONFLICT tuple with its retained diagnostic and exact
current scope/epoch/canonical/replica fences. The replica's expected fingerprint
and provenance must match; the transition performs no world write or stock
change. It promotes the same epoch, advances only the observed replica revision
and retires that active diagnostic. Historical conflict remains an earlier event,
not a rewritten expected projection. This is not generic unresolved-lease release.

The engine conflict/recovery test now independently rejects stale replica
revision, wrong epoch and still-mismatching evidence without mutation, then
accepts exact matching evidence and rejects duplicate recovery. Inventory stays
identical. Custody, reference-input projection and architecture selection passes
18 tests (16s, exit 0); source diff-check clean. No active test process or deploy.

This handles an actually observed restored result, not automatic restitution of
lost resources or ownership of foreign replacement contents. Those need their
ordinary observation/accounting or explicit abandonment path; a matching hash
cannot excuse skipping applicable item/claim custody validation in the adapter.
Initial/interrupted/repeated physical adapter adoption and fungible effects still
remain open. SA-09 is not closed by these model recovery checks.

### SA-09 adapter projection adoption — 2026-09-22 (partial)

Initial reference-container materialization now submits the atomic input transfer
and PREPARING custody before writing the chest, then reads the accepted state.
The reference adapter confirms actual loaded slots or retains their mismatch;
pending-write conflicts remain eligible for later observation. Unloaded pending
or unresolved scopes no longer consume a drain turn without a transition.

Recovered prior run: seven selector tests passed; the actual-chest mismatch and
runtime-snapshot recovery GameTest passed (1.166s), server shut down at 23:47:16.
The fixture translates the whole bootstrap and pins its codec to that bootstrap.
Earlier bounds/codec fixture failures are preserved; neither weakened runtime
bounds nor a default unshifted codec is used. This is adapter-helper/native I/O
and in-memory runtime recovery, not a full graphical lifecycle or disk crash.

Released catch-up now also uses ReferenceProjectionPrepared instead of a bare
ReplicaEmitted followed by a write. Even unchanged inventory fingerprints pass
through preparation: a COLD exact input can be held outside inventory and must
transfer atomically before physical work. A rejected preparation cannot fall
through into ordinary acquisition. Four projection model tests pass. Added a
native epoch-2 write/confirmation test; its first run rejected the fixture's
illegal UNMATERIALIZED-to-ACTIVE shortcut. The fixture now takes PREPARED first;
the corrected focused run passed both native tests (1.714s; Gradle 24s), with
normal shutdown at 23:54:14. The existing negative native test passed
in that run, including eligibility of retained conflict for fresh observation.

Still open: complete materializer tick/recovery composition (including surface
CONFLICT), physical permission consumers, bound fungible input/effects, actual
mode changes and the complete player-visible production lifecycle. No closure
or deployment claim.

### SA-09 operational consumer and fungible ingress audit — 2026-09-22

The adapter's checkpointConfirmedMutation previously accepted any live lease,
including PREPARING. Its ordinary mutation-close attempt then failed and could
misclassify a lawful pending write as PROVIDER_LOST. It now requires operational
custody before taking any action. A real-chest regression verifies rejection
without canonical revision change; both projection GameTests pass (24s, normal
shutdown 23:56:27). This native run preceded the domain guard below.

Fungible layout command/reducer previously admitted an initial physical binding
without checking reference custody; only the Minecraft observer enforced that
precondition. The common reducer now requires the exact current ACQUIRED
reference epoch. No lease, PREPARING and foreign epochs cannot bind slots, both
on command planning and direct reduction. The existing registered split/handoff
test now declares actual epoch-4 custody rather than inventing authority solely
in its observation. Focused fungible-observation and projection-custody tests
pass (11s). No broader native fungible acceptance is inferred.

Static tracing also confirms the remaining fungible production gap is real:
FungibleBound is decoded and validated but has no production creator; layout
binding/release updates only the resource ledger, not the job hold. Production
scene admission and transformation validation remain Materialized-only. The
next coherent repair must compose job binding/unbinding, worker admission and
the physical resource transformation/receipt together, retaining claims, lots,
payment, schedule and terminal retirement. Simply widening scene admission or
letting COLD transform a live chest would not close SA-09.

### SA-09 resource transformation boundary — 2026-09-23 (not yet adopted)

ProductionJob.requireColdCompletion now also rejects live reference custody and
retained local stock conflict. The process reducer already guarded that boundary,
but direct completeFungibleProductionJob could otherwise transform an unbound
account during PREPARING. The full 80-unit COLD lifecycle test now prepares a
projection at its terminal station and proves both entry points reject the
completion; ordinary COLD completion remains covered. Lifecycle/clock/projection
selection initially passed (15s), but a later report audit found the lifecycle
filter used the filename rather than its declared class name and did not execute
that method. The corrected exact ProductionProcessLifecycleTest method passed
(8s), then passed again in the 13-test adoption selection below. The original
15s result is not lifecycle evidence.

FungibleResourceLedger.transformObserved now provides the missing atomic HOT
recipe accounting primitive: validates the current bound epoch and exact claimed
input, applies the same recipe accounting, validates the complete actual output
layout and retains that epoch. Intermediate immutable calculations never publish
a released account. Two new tests cover partial consumption with retained wheat,
new bread/lineage, claim retirement, same-epoch binding, duplicate/stale rejection,
incomplete physical evidence and forbidden unclaimed conversion. All 14 selected
resource ledger tests pass (14s). This is a pure accounting primitive, not yet a
registered physical receipt or Minecraft write path.

Further source tracing: current PRODUCTION_TRANSFORMATION schema explicitly
names INPUT_ITEM/OUTPUT_ITEM and its target/receipt are ExactItemStack-specific;
those identities must not silently become resource lots. Complete adoption needs
an explicit nominal resource transformation schema/receipt in the existing
production owner, shared worker progression and atomic job/claim/binding
transitions. Also cover cancellation/death, which currently rejects FungibleBound
pending recovery, rather than activating binding alone and breaking that path.
No extra test campaign or new product scope is implied by these implementation
dependencies; they are necessary parts of the already accepted SA-09 repair.

### SA-09 atomic production resource hold adoption — 2026-09-23 (partial)

ProductionResourceCustody now composes resource layout binding/release and the
matching production input hold in one immutable world update. Registered layout
and release reducers use it, as does the reference mutation-close transaction.
FungibleCold becomes FungibleBound at the declared account epoch; release restores
the exact original COLD hold without reselection or lost work. Both directions
reuse the existing production hold validator. Same-epoch re-observation retains
the job; foreign epochs reject. Unperformed bound-job cancellation now retires
only its reservation from account/bindings, not physical stock. Existing started
physical-effect restrictions remain in the cancellation owner.

The scheduler explicitly retains a bound job's review instead of falling through
to an ExactItemStack lookup and false INPUT_UNAVAILABLE. This is a temporary
safe incomplete path, not HOT production completion: physical lot transformation
and scene admission are still due. The INPUT_UNAVAILABLE reducer now accepts a
real reference-container conflict for any job representation, while rejecting an
otherwise present input; it no longer requires the job to be Materialized solely
to acknowledge its depot's existing conflict.

The real 80-unit COLD lifecycle test now includes binding, codec recovery, stale
release rejection, exact round-trip restoration, bound scheduling and cancellation
conservation. Final exact-class selection passed 13 tests (13s): lifecycle 1,
reference custody 5, physical observation 3, architecture 4. Filter
ProductionObserved* in an earlier selection matched no test and is not evidence.
No native or full HOT labor/effect claim from this pure adoption.

### SA-09 shared worker admission for bound resources — 2026-09-23

FrontierProductionWorkSceneSupport.hasPhysicalInput replaces the exact-stack-only
predicate in candidate admission and the actual Minecraft worker executor. A
FungibleBound job additionally requires operational reference custody at its
declared binding epoch. Account bindings alone do not grant body/work authority.
requireHotLease now checks the same input predicate, so command/reducer movement
and progress cannot continue after account release while an old body lease still
exists. Exact and fungible jobs retain the same movement and work-clock owners.

The lifecycle regression now composes current epoch custody, scene admission,
HOT traversal and released-input rejection. Work-clock and resumed-handoff
selection passed (12s); NeoForge source and test compilation passed. Broader
production selection initially failed one route-conflict release test: its two
engine fixtures created an active production job without the completion schedule
which terminal retirement now correctly cancels. Both fixtures now retain that
real schedule. Final production selection passes all 98 tests (30s).
No weakened cancellation or missing-schedule fallback was added.

Still not complete: bound-job physical transformation is not implemented, so its
scheduled review remains retained after work readiness. Typed lot intent/receipt,
actual slot replacement, retirement composition, and native full-cycle evidence
remain required; admission itself is not production acceptance.

### SA-09 nominal lot-production receipt — 2026-09-23 (foundation)

Added FungibleProductionObservation to the closed physical observation union.
It explicitly retains account, container, input/output lot, claim, quantity,
authority epoch and the complete actual post-effect stack layout. Exact-item
receipt semantics were not overloaded. Constructor rejects empty/unbounded,
duplicate, foreign-container or out-of-range slot evidence and invalid epochs.
The existing WAL and snapshot observation codecs use new non-reused tag 23 and
one shared bounded body codec; historical tags/layouts are unchanged.

Receipt round-trip/unknown-tag/truncation and malformed-layout tests plus the
observed accounting tests pass (4 tests, 11s); domain compilation passed (8s).
This proves receipt representation only. No producer or lifecycle handler yet
uses this receipt; registered lot schema, job completion/retirement, Minecraft
write/reconciliation and their restart/native composition are still required.

### SA-09 registered lot-production lifecycle — 2026-09-23 (partial)

The existing PRODUCTION_WORK owner now declares PRODUCTION_RESOURCES (stable
schema tag 27) alongside its exact-item schema. New nominal role tags 42–46 name
input/output resource lots, claim, custody account and container; no role is
inferred from a string prefix or repurposed from INPUT_ITEM. The shared full-state
subject validator admits the account/container only in their explicitly declared
roles and only when those subjects exist. Initial lifecycle tests caught that
missing inventory coverage; it was fixed rather than bypassing full validation.

FungibleProductionStateSupport validates the retained epoch, completed work,
released worker scene, finance and complete role binding. Registered confirmation
atomically transforms observed stock, consumes the reservation, settles payment,
records the resource-typed terminal market receipt, completes the task and retires
the job. UNKNOWN retains the same job/stock/claim; retirement verification now
understands resource conservation instead of requiring an ExactItemStack output.
Exact-item confirmation now also validates receipt/schema before reading input.
Account release rejects a started or restart-unknown production effect.

Three lifecycle tests pass: registered confirmation/payment/snapshot, rejection
of wrong epoch or actual product, and actual engine UNKNOWN -> checkpoint recovery
-> one late confirmation with duplicate rejection. Two codec tests also pass.
The combined 11-test selection is nevertheless red on the previously recorded
PhysicalIntentLifecycleArchitectureTest textual compaction assertion; not waived.
The broader production selection and NeoForge compile subsequently passed (34s),
session 47572 terminal. This selection does not include the failed textual test.

Not yet emitted by the production scheduler or handled by the Minecraft adapter:
bound jobs still retain their scheduled review. Actual lot slot-replacement,
PREPARED release disposition, physical reconciliation/replica closure and native
cycles remain due. Registered reducer evidence is not a completed HOT product.

### SA-09 — physical resource production adoption (2026-09-23)

Supersedes the scheduler/adapter gap immediately above: completed bound jobs now
emit the declared resource intent through the existing production scheduler.
FungibleProductionLayout deterministically consumes only the reserved lot portion,
preserves remaining stock/exact items and checks output capacity. The existing
production executor delegates resource-schema I/O to FrontierV3FungibleProductionEffect,
using the same PREPARED/RUNNING/UNKNOWN protocol. Whole-chest kind/count/component
validation precedes writes; confirmation is built from actual post-write contents,
then the existing reference owner establishes the next replica boundary. Shared
UNKNOWN submission now includes its required typed diagnostic stamp.

Five lifecycle tests pass (7s): added split-stack 96 -> 32 wheat + 64 bread layout,
snapshot determinism and rejection of both released and stale retained bound input.
Native production-effect slice passes all four cases (24s, shutdown 00:42:11,
session 89782 terminal): actual resource write -> registered job/payment/lot/replica
closure, altered stock plus foreign item -> retained UNKNOWN without mutation or
replay, and the two existing exact-production/ordering cases. Finished labor is
an explicit fixture precondition, not evidence of a worker traversing the route.
The initial native fixture omitted unrelated canonical exact items from its chest;
the whole-chest guard correctly rejected it. Fixture corrected to include the
complete retained input, failure preserved in build/runs/
frontier-v3-scene-game-test-production-lot-fixture-failed-20260923. Pilot fixtures
remain on the pilot source set. Existing exact test now uses its template interior.

Still open: PREPARED-effect release and retained continuation, interrupted physical
writes/restart composition, full COLD/HOT/successor worker cycle and product evidence.
This is not SA-09 closure or a deployable candidate. No live deployment performed.

### SA-09 — unstarted resource effect returns to COLD (2026-09-23)

Static tracing found that preparation consumes the completion schedule, while
resource release previously only changed Bound -> Cold. That left the old intent
and actuator fence behind with no continuation. ProductionResourceCustody.release
now validates and atomically retires only PREPARED resource effects and their
exact recovery authority while retaining the same job, claim and labor progress.
Started/unknown/terminal effects cannot use this release path. Both registered
release entry points (FungibleStackBindingsReleased and ReferenceMutationClosed)
compose one production-owned completion action in the same transaction. Ordinary
release with no prepared effect creates no additional action.

Two engine regressions cover both entry points, snapshot recovery, stale actuator
rejection, one subsequent COLD completion/payment and no orphan job schedule.
The retained UNKNOWN engine test now also rejects binding release. First expanded
selection caught undeclared kernel.schedule_created emission from replica-custody;
added that explicit capability to its closed registry (no dispatch bypass).
Final focused model selection passes 19 tests, plus NeoForge compile (11s,
session 95471 terminal); git diff --check passes. This proves registered resource
release composition, not actual chunk unloading, process-crash recovery or the
remaining exact-item release path. All broader SA/product scopes remain open.

### SA-09 — admit new work under current resource custody (2026-09-23)

Static successor tracing found a remaining categorical refusal in planStart:
fungible-only stock plus a live reference chest always produced INPUT_UNAVAILABLE.
ProductionResourceCustody now owns admission's explicit Cold/Bound hold selection.
Operational custody requires the current matching bound epoch/lot quantity; pending
projection or a not-yet-observed layout retains the pending start action instead
of failing the task. ProductionStarted reduction and the common state creation API
both reject a forged Cold hold or stale Bound epoch. Admitted work retains its real
route, unstarted labor, claim and ordinary completion schedule; it does not leap
directly to a recipe result. Existing exact-item input preference remains explicit.

Also found a separate shared reserveBound defect: its stock scan compared each
lot to its own binding kind, not the requested claim kind/economic owner. Earlier
unrelated stacks could receive the claim and make a valid mixed-container reserve
fail. The scan now restricts stock to the claim's kind AND owner. A three-stack
regression deliberately orders other-kind and other-owner stock before the valid
stack and verifies neither receives the reservation. Ledger selection passes (14s).

Three admission tests cover actual scheduled start/continuation, planner+reducer
admission/snapshot, negative direct-API and replay inputs, and pending layout.
Production-wide selection + NeoForge compile passed (36s, session 5517). After
adding the common API guard, affected admission/ProductionProcess/lifecycle tests
passed again (32s, session 53070 terminal). git diff --check passes.
This removes a successor admission blocker; a whole observed repeating production
cycle, unload/process recovery and the remaining audit scopes are still unproved.

### SA-09 — exact-item release uses the same continuation guarantee (2026-09-23)

The exact-item path had the same consumed-action gap and an additional escape:
generic CustodyReleased could drop a container while a RUNNING production effect
still needed it. ReferenceContainerCustody now owns registered generic release,
not just mutation closure. Both paths compose ProductionTransformationStateSupport's
unstarted exact-effect retirement; the production owner restores one continuation
for either input representation. Exact stock and job are retained unchanged on
release; the existing COLD completion subsequently consumes the exact input.
Started effects refuse release until resolved. Generic release also rejects a
still-bound resource layout, preventing callers from bypassing its release owner.

Three exact engine regressions cover both release entries -> snapshot recovery ->
stale actuator rejection -> one output/payment, and rejection of both release
entries for a started exact effect. Added a bound-resource bypass negative case.
Final production-release/resource/replica selection plus NeoForge compile passes
(15s, session 96590 terminal), git diff --check passes. Source search confirms
production replica-custody release calls now converge through ReferenceContainerCustody.
These tests establish canonical release composition, not Minecraft chunk unload,
physical-write crash persistence or full repeated player-visible production.

### SA-08/09 — registered cross-mode production composition (2026-09-23)

Additional direct representation comparison (23288 terminal0/21s):
ProductionModeCompositionTest now runs the same initial economy with fungible
wheat and with the equivalent64 exact wheat. At every ordinary scheduled boundary
it compares worker, traversal, labor progress and next deadline, rejects premature
output, then verifies equal completion instants, output kind/quantity and one wage
in each economy. All3 cases pass. This removes the prior evidence gap where each
representation was compared only against its own COLD control. It is still model
composition, not native fungible container materialization or physical output.

ProductionModeCompositionTest now starts both representations through ordinary
scheduled production, advances the retained route/input station and 17 labor units
in COLD, then performs two HOT visits with five observed work units each and COLD
intervals. Each transfer uses registered projection preparation/confirmation,
resource observation where applicable, worker scene admission/drain/release and
container release. No post-start canonical-state injection is used. After each
visit the engine recovers its checkpoint, then eventually completes the job in
COLD without another visit. A separate always-COLD engine is the control.

At each boundary the test compares earned labor, deadline, route/cursor and worker
body/identity, and rejects premature output. Both variants finish at the same
canonical instant as their control, with identical output and one payment. Initial
two-case run passed (16s); with checkpoint recovery/body assertions plus admission
and work-clock regressions, final selection passes (19s, session 16172 terminal).
This establishes composed model semantics for repeated switching, not real chunk
unload, physical navigation or a player-visible/repeating economic cycle.

Existing disposable-materialized-production-work-restart scenario was inspected:
it uses exact-item stock and restarts only AFTER the order is fulfilled. It cannot
be cited for fungible input, mid-work restart or COLD-start ingress. Those native
claim gaps remain explicit; do not substitute that scenario's name for coverage.

### SA-08/09 — native mid-work diagnostic and observer ingress (2026-09-23)

Added disposable-materialized-production-midwork-restart.json: restart follows
observed PROCESSING plus an active order, rather than fulfilled output. This is
an exact-item exploratory diagnostic, not fungible/repeating-cycle acceptance;
the saved checkpoint must still establish that shutdown actually retained work.

First authenticated :0 run (result-visible.json under
build/sa09-midwork-native-20260923, outer run cb1dfcc4-2c63-45b0-83f8-4e6b19d9681f)
failed at setup visit. Client saw the target dimension/chunk but ended at
(-386,64,-326), not (-385,65,-326). Server had entered HOT and subsequently
drained terminal-ready; this is not restart or fulfilled-output evidence.
Runner/client/server PIDs 1283278/1284403/1283794 are absent at recheck.
Failure bundle and world retained; unrelated live server untouched.

Source/compiled-plan inspection explains the test interference risk: the old
observer point coincides with the fixture worker's exterior approach on the
one-block-raised public route. Adjacent ground is lower. New scenario ingress
and resume use (-388,64,-326), outside the worker path; compiled plan has no
body/head obstruction there and the terrain support is Y=63. No visit tolerance,
timeout or production behavior was changed. Exact pushing cause was not directly
observed, so this is a geometry-supported diagnosis, not an asserted collision trace.
Scenario schema passes, hash f0387d34b82da85cb18af8503dda15fc04e27497eb9a8587db2c3bba2380cefd.
Prepared build refreshed (3s, session 6000 terminal); JAR unchanged at
c09bb91e7de049d1bb30599c1827bf48bfa9f61dc50df5e021aac8f71fa8e06a.
Corrected exploratory run is result-ingress.json, exec session 38846.

Earlier launch attempts classified as infrastructure: occupied default port
(no server launched), private display selection mismatch, then Xvfb keymap write
failure under /tmp quota. Their failure bundles remain. Actual :0 is accessible
with its discovered session cookie; no user display/service was modified.

The corrected-ingress run passed visit but exposed an error in the newly authored
scenario: assertions at action 4 named scene/order diagnostics that the visible
entity action never requests. Do not blame the product or accept stale responses.
Added explicit inspect actions 5/6 before the restart, correlated each assertion
to its actual read, and added a focused Node regression (1 pass, 42ms).
result-ingress.failure retains that failed method attempt; session 38846 terminal.

Final result-correlated.json passes, session 28658 terminal, total 160972ms:
source artifact c09bb91e7de049d1bb30599c1827bf48bfa9f61dc50df5e021aac8f71fa8e06a,
outer run 6d2dbc40-37a8-47e1-bf40-23c339d98096, client
6a790030-d58a-4935-aa7f-a08228d7d9f8. Before restart revision 65 at instant 413
has HOT/PROCESSING, named worker and active reserved order. Graceful save and
same-world recovery retained an UNKNOWN scene pending observation; after
reconnection revision 247 at instant 2269 has FULFILLED/completed/no reservation,
then exact 64 bread and OBSERVED_CURRENT chest at instant 2274. Server PIDs
1312517 and 1314887 are absent after completion. Both prior and successful
captured frames were opened: worker and workshop board visible. A still frame
does not prove motion/readability across a full cycle. This is exact-item
mid-work graceful recovery evidence, not fungible/native repeated cycles,
abrupt-save atomicity or full human acceptance. No rerun solely for confidence.

### SA-02 — restart recovery queue also requires fair attempts (2026-09-23)

Static follow-through found reconcileOneAfterRestart still sorted pending fields
and returned after the first eligible field even on DEFERRED. Ordinary-growth
fairness did not fix this separate recovery entry point. Recovery now uses the
same FrontierV3FairTurn as scenes/effects; eligibility stays loaded AND demanded,
pending records survive deferral, and each call still attempts only one field.
Ephemeral cursor is cleared on runtime forget and when recovery completes.

Focused regression exercises A deferred -> B served -> B removed -> A loses
demand -> A revisited, without selector mutation of its pending record. Field
executor plus shared cursor test selection passes (10s, session 67798 terminal),
including recompilation of NeoForge and pilot sources. This is selection-level
evidence; actual durable multi-field leave/re-entry/restart is still required.

### SA-02/05 — bounded field continuation must revalidate its predecessor (2026-09-23)

Following the durable cursor exposed additional concrete inconsistencies:
matchesProjectionPrefix expected AIR for every uncommitted INITIAL cell, although
normal admission requires supported dirt/grass/graybox floor. A valid partial
initial projection therefore conflicted on recovery. Conversely, a cached writer
did not revalidate its prefix/suffix on later turns and could overwrite an
intervening foreign block. The old exact BlockState comparison also treated
ordinary farmland hydration as drift.

The retained mode now explicitly distinguishes INITIAL (its existing blank-AIR
semantics) from new stable named tag INITIAL_SOIL (the admitted supported neutral
soil baseline). Both are declared by admission and round-trip through SavedData;
recovery never chooses the mode from observed block shape. FieldProjectionWork
retains that mode directly instead of reconstructing it from boolean flags.
Cached continuations validate the same committed prefix/unwritten suffix before
each bounded batch, refuse foreign changes and retain durable conflict evidence.
Farmland moisture is physically permitted; crop stage, foreign solids and water
state remain checked. Initial admission/recovery share the same soil predicate.

Three focused native cases cover partial INITIAL_SOIL plus SavedData reload,
hydrated ADVANCE prefix and removed committed crop, and explicit INITIAL/AIR
rejecting even supported dirt introduced later. All 3 pass (29s overall,
session 84231 terminal, server shutdown 01:32:43 Sep23). Domain-side executor/
selector selection also passed 23 tests. An earlier two-case run passed in 28s;
it was extended after source review identified the separate valid AIR branch.
The final baseline predicate deduplication occurred after native compilation;
native results cover the identical predicate before extraction, not a newly
deployed artifact. Final focused recompile/test recorded in the ledger.
These cases test real blocks and SavedData, not process restart, multi-field
observer switching, or the whole farmer successor story. Those remain open.

### SA-01/10 — real held-owner composition and route-fixture drift (2026-09-23)

`HeldHarvestSchedulingCompositionTest` now constructs an actual HOT harvest with
its registered retained continuation and an independent field preparation. With
one-action budget the second field prepares; after checkpoint/recovery its next
growth action succeeds. The exact HOT job and its original continuation remain
unchanged. This supplements the synthetic kernel hold tests with the real domain
owner; it is not Minecraft visibility or full field-cycle acceptance.

The related supply selection exposed fixture drift, not a reason to relax food
reserve policy. Correct labor duration allows Northwatch birth to consume the
export surplus before delivery admission. The isolated route-return fixture now
omits only that settlement's initial birth review, retaining ordinary production
timing and reserve rules. This yields an ordinary shipment numbered 11, not the
historically hardcoded 2. A test-only unique initial-Northwatch-shipment admission
query replaces that guessed ordinal in the bootstrap, derived scout fixtures and
model consumers. The delivery assertion uses the actual contract/task relation;
no canonical ID is rewritten and no outcome is pre-created.

The final kernel/queue/held-harvest/supply/graybox-plan selection passes 61 tests
in 26s (session 59857 terminal); implementation `git diff --check` passes. Earlier
failed selection 63866 (31s, four fixture failures) caught an edit applied to an
earlier identical lookup instead of the intended route-return lookup. That edit
was corrected and the unrelated strike lookup restored. No timeout was enlarged.

Still open: native route-return/scout scenarios contain historical exact IDs and
must be aligned with their declared fixture before reuse. The frozen-scout
progress provider and other exact-ID consumers need the same source review.
These tests do not close SA-01/10 globally, nor atomic owner-retirement coverage,
nor other SA findings. No deployment or new native acceptance occurred here.

### Dependent route/scout fixture consumers — 2026-09-23

The scout-only scheduled planner also hardcoded shipment 2, so it would not stop
the selected shipment after release. Configuration now captures the exact
admitted operation ID once; subsequent schedule handling compares that retained
identity. The ambient scout-patrol fixture no longer requests an unrelated cargo
freeze. Ordinary planners and registered owner holds are unchanged.

`RouteSceneFixtureIdentityTest` verifies seed-41 route-return, scout-sighting and
scout-intercept configurations: actual operation/cargo/contract IDs are ordinal
11; ordinary route progress is not cancelled, scout-profile progress is cancelled
for the exact action, and independent scheduled work delegates unchanged. Final
test passes in 13s (session 2820 terminal). An earlier 17s pass preceded making
the independent-action assertion unconditional; it is superseded by this pass.

All nine JSON scenarios using these three profiles now declare that verified
operation/cargo identity; unrelated strike and assembly scenarios are untouched.
Node declaration/shape tests pass 50 cases in 0.22s, including all checked-in
scenario parsing. A paired identity check links the nine declarations to the
Java fixture test. This is source/fixture consistency, not a successful native
run. Geometry, visual acceptance and release/recovery effects remain unproven
for this corrected candidate. Implementation `git diff --check` passes.

### SA-10 — atomic retirement and actual field disposition recovery (2026-09-23)

The kernel regression now exercises a declared owner disappearing while its
schedule remains: final-transaction validation rejects before the committer,
preserving canonical state and queue. A fresh identical instance accepts owner
retirement plus cancellation in one WAL transaction; replay succeeds, whereas
recovery of a retired owner with a retained continuation rejects. This tests the
kernel publication boundary, not a new production owner classification rule.

The real-domain harvest test submits an ordinary player-conflict observation to
the registered command planner. One durable transaction cancels its exact held
continuation, marks the effect CONFLICTED, and retains the exact work witness.
Both initial-checkpoint-plus-WAL and terminal-checkpoint recovery reproduce the
state without the stale continuation. Field/site identity remains valid: it is
needed for diagnosis and subsequent physical release, not runnable harvest work.

Initial test construction incorrectly treated this retained field witness as a
removed subject (session 61818, 9s). Source inspection showed that the schedule
names the still-existing site and conflict deliberately retains the job; that
test was separated from genuine owner-deletion coverage, rather than changing
production semantics to satisfy it. The kernel test initially reused a correctly
quarantined instance after fault injection (80293, 8s); the valid branch now uses
a fresh identical initial state. Final 36-test kernel/queue/harvest selection
passes in 8s (38687 terminal), and `git diff --check` passes. No native/physical
release evidence is claimed; SA-06/07 and the wider integration backlog remain.

### SA-06/07 — dead return callback and remaining cargo handoff (2026-09-23)

Source inspection found `SceneDepartureObserver.observeJoin` constructing
`SceneMemberPosition` from a returned Mob without excluding zero health. That
record correctly rejects non-survivors, so an actual dead return could throw in
the join callback instead of leaving disposition to the shared death observer.
The callback now ignores non-Mob/dead bodies without removing departure evidence.
The existing native body/NBT test includes zero-health input before the separate
exact-return input; it confirms evidence survives and no survivor is invented.
This is boundary fault injection, not resurrection or an actual death/restart
player story.

First native attempt (74828, 74s) failed both fixtures before reaching the changed
callback: hot-strike bootstrap still waited for shipment 2. It now shares the
corrected route-return precondition and its actual retained instant rather than
inventing tick2600. Existing state-only fixture callers retain a wrapper; native
runtime configurations use state plus time. A focused model run (41888, 25s)
also exposed a route-return cargo-release test's stale ID; corrected to actual
admitted ID/time. Final domain selection passes 16 tests including architecture
checks, and the two native departure/death cases pass in 35s total (44304 terminal,
server shutdown 02:03:03 Sep23). Earlier adapter departure unit tests passed 12
cases. A final comment-only removal of obsolete volatile-cache documentation in
SceneExecutor has no runtime effect. No live deploy or product-candidate claim.

Still concrete/open: generic release checks actor entity readiness but then
requires a currently indexed intact cargo cart. A legitimately unloaded cart
therefore follows `release-carrier-unavailable` even when actors have valid
departure receipts. Do not fix this by trusting the last sample, treating cargo
as absent, or unconditionally spawning a replacement; the exact cargo needs its
own durable departure/fencing/recovery composition. Also audit body physical
representation epoch versus canonical recovery-attempt epoch: they are separate
domains, and fromCanonical validates actor roster, not current recovery epoch.
The bomber native JSON and other supply/assembly fixture consumers still need
identity/time review after the corrected labor duration. Existing narrow passes
do not close these paths or SA-06/07.

### SA-06 cargo final-observation foundation — 2026-09-23 (not wired)

Added `FrontierV3CargoDeparture`: exact lease/cargo/UUID, scene revision, canonical
cargo recovery-attempt epoch, observed body and all 27 serialized Minecraft
inventory slots. Defensive copies prevent mutation through inputs/accessors/save
images. Decoder requires the complete typed fields and exact slot extent; an
absent/wrongly typed inventory never silently becomes empty.

The cargo owner now exposes a separate `captureDeparture` boundary. It accepts
only UNLOADED_TO_CHUNK, exact current declared authority and unchanged canonical
contents. Existing live ownership remains strict about removed entities. A new
native test exercises real cart materialization and removal, rejects periodic
sampling, wrong attempt and changed quantity, and round-trips the final observed
inventory. This tests final observation, not actual chunk storage/restart or COLD
handoff. Two codec/immutability tests and all three native departure/death cases
pass in 35s (73775 terminal, shutdown 02:07:23 Sep23). Compile passed separately
in 7s; `git diff --check` passes.

Remaining implementation: bounded per-world retention/conflicting observations,
production unload/join hooks, current canonical inventory/attempt revalidation,
generic release consumption and exact retired-carrier rejection on late load.
No production caller emits this observation yet and no release fallback has been
enabled. Persisted physical observation alone is not authority: late-return and
torn-save handling must compose before enabling unload-to-COLD. Native cargo
coverage here is the current fungible shipment fixture; exact-item variation
and full recovery still require coverage. No deployment or SA closure claimed.

### SA-06 cargo observation retention — 2026-09-23 (not wired)

`FrontierV3CargoDepartureLedger` now provides per-world SavedData storage, bounded
to 4096 primary observations and one contradictory witness per identity. Exact
repeats are idempotent; later observations cannot overwrite the first. A retained
contradiction survives reload and prevents even the first matching observation
from being silently resolved. Resolution is exact compare-and-remove; callers
must separately prove return or accepted canonical release. No eviction conceals
unresolved evidence when the retention bound is reached.

Strict decoder rejects absent/wrongly typed inventories, duplicate entity IDs,
unsupported format and orphan/equal/duplicate contradiction rows. Ordering is
deterministic, and load does not mark unchanged evidence dirty. Four ledger tests
plus two immutable-observation tests pass in 8s (9162 terminal), including capacity
and recovery/negative cases. `git diff --check` passes. This is codec/ledger
verification, not filesystem crash proof or actual custody transfer. Runtime
unload/join/release remains unconnected until its complete safety composition;
no new native run, server deployment or finding closure is claimed.

### SA-06 cargo observer/admission/release wiring — 2026-09-23 (partial)

Runtime source unload/join now calls the cargo observer. Exact final receipts are
retained; materialization cannot replace a stored original while that evidence
exists, and ordinary ownership cannot authorize HOT work through an unresolved
return. Matching declared UUID/lease/revision/attempt/body/inventory resolves a
current return; changed payload retains the witness. Revalidation reconstructs
the expected complete physical inventory from current canonical cargo and checks
the current authority epoch, not merely the observed UUID.

Shared scene release now permits an absent cart only with that current,
non-conflicting final observation and no concurrently indexed cart. It retains
the receipt after canonical release for late-return handling. A CLOSED exact
retired projection may be cleared/discarded only when its full snapshot matches
and the exact canonical cargo tombstone confirms retirement. Canonical world
custody blocks deletion; contents are cleared before discard to avoid drops.
Wrong/stale/changed evidence is not permission to destroy or reconstruct cargo.

Six observation/ledger unit tests and three native departure/death cases pass
in 35s (52804 terminal, shutdown 02:13:59 Sep23). The cargo native case now covers
actual cart NBT return, replacement rejection, divergent-return work fencing and
exact-current-return resumption. It does NOT exercise the complete generic release,
closed late-cart deletion or filesystem crash. The added world-custody deletion
guard was edited during that run and is outside its coverage; compile recorded
separately. No live deployment, product acceptance or SA closure is claimed.

Remaining: exact-item variation, full release and late-retirement composition,
world-custody/foreign-payload negatives, missed join/recovery ordering, tombstone
retention/compaction and bounded unresolved-ledger cleanup. This wiring must not
be promoted based only on the current-return test.

### SA-06 exact retired cargo return and world-custody preservation — 2026-09-23

Added two native return-boundary cases. The production unload observer captures
the actual removed cart; registered domain commands establish the CLOSED scene
precondition, and its original entity NBT is loaded again. Both cases reject
changed physical contents without deleting them or discarding the original
witness. The exact obsolete projection is cleared before removal; accepted
canonical world custody instead preserves the same cart and contents. Missing
canonical retirement cannot authorize deletion. The matched world-custody return
now consumes its resolved ledger row, avoiding an unnecessary retained witness.

Six unit tests and five native departure/death cases pass in 37s (43558 terminal,
server shutdown 02:17:24 Sep23). This covers the previously untested world-custody
guard and actual loaded-cart deletion/non-deletion, not a complete generic scene
release: the retired fixture submits canonical survivor positions rather than
claiming they came from physical actor unloads. Actual actor+cart release,
filesystem interruption, exact-item payload variant and full scene-cycle evidence
remain required. No timing relaxation, live deployment or whole-SA closure.

### SA-06 graphical route-return setup failure — 2026-09-23

`build/sa06-route-return-native-20260923/result.json` is terminal FAILED at
setup visit step 3, before release/restart assertions. Candidate JAR SHA-256:
`5fcdc09949388101d88b081919a034e99c539e4db41adc7439b3c6b801a36493`.
The requested observer cell (-360,65,-340) sits over a planned hall foundation;
the actual player ended near (-356,64,-339), so arrival timed out. This is
invalid observer setup, not evidence for or against complete cargo release.
The server did admit HOT travel during the failed setup. Task server was stopped
gracefully; result/failure bundle retained. All four recorded runner/server/client
PIDs (1440834,1441316,1441872,1441906) are absent on the subsequent process check.

Initial/restart observer changed to (-354,64,-352), away observer to (0,64,0).
A real compiled-plan regression checks natural support and absence of structural
cells at support/body/head; it also preserves the old obstruction as a negative
fixture. Node checks bind these positions to the actual scenario. Two Node tests
and RouteSceneFixtureIdentityTest pass (Gradle 15s, session38014 terminal).
No timeouts/tolerances weakened. Dynamic operation-relative camera targets still
need geometry review before another native attempt. SA-06 remains open.

### SA-06 natural unload callback gap — 2026-09-23

The corrected camera run (`build/sa06-route-return-native-20260923-camera`,
artifact `2d8d4195c16d27a6432ee6a4540611d449bce939cece77155f031de34e707c90`)
passed initial HOT and ordinary departure, then failed action4: all bodies/cart
absent, scene permanently DRAINING. Session19397 terminated failed; the runner
saved/stopped its server at 02:35:30, with a retained failure bundle. This is a
real integration defect, not another camera or timeout issue.

Pinned Minecraft/NeoForge 1.21.1/21.1.248 source proves the missed callback:
PersistentEntitySectionManager.updateChunkStatus(HIDDEN) calls stopTracking;
ServerLevel.EntityCallbacks.onTrackingEnd emits EntityLeaveLevelEvent while the
entity's removal reason is still null. Later unloadEntity calls setRemoved with
UNLOADED_TO_CHUNK, but its onRemove sees a hidden section and does not call
stopTracking again. The previous observer's actual-removal guard therefore
rejected the only event. Manual remove tests did not model that ordering.

Added required FrontierV3EntityDepartureMixin at Entity.setRemoved TAIL, routing
only server-side actual UNLOADED_TO_CHUNK to the shared lifecycle's existing
actor/cargo departure observers. Tracking-end no longer produces scene/cargo
receipts. No canonical mutation, fabricated body, timing relaxation or repeated
sampling was introduced. Existing ownership/health/inventory/epoch guards remain.
22 departure/consumption/cargo-ledger/readiness unit tests pass (10s,17228).
Actual natural-unload/restart acceptance remains pending the next native run.

Ancillary harness correction: visit_operation now honors its declared current
or cargo anchor, and Java/Node reject unknown anchors. The return scenario uses
fixed plan-checked ground cells, avoiding relative offsets across raised roads.
42 pilot parser tests, two actual plan/fixture tests and two Node tests pass
(combined Java18s,73821). These checks do not establish scene lifecycle correctness.

### SA-06 natural actor/cart release and graceful restart pass — 2026-09-23

The final-unload hook candidate
`6012c219ea5d731deb62f68881d1b79b4f7d1f76cd8f0a4f54fc589d2c30cd20` passes
`build/sa06-route-return-final-unload-20260923/result.json`, isolated native120s,
session40007 terminal exit0. Ordinary player arrival creates HOT; departure
naturally unloads actual actors/cart; registered scene release closes the lease
at revision157. Canonical movement reaches routeIndex1 while away, ordinary
return creates a new HOT scene, and a same-world graceful restart recovers that
scene from UNKNOWN to HOT with exact actors/cart CURRENT. No synthetic release
positions or direct canonical test edits were used in this graphical scenario.
The linked route-current-continuation frame was opened and inspected: named
caravan, residents and chest carrier visible on the road. Recovery server PID
1487984 is absent after runner completion; no deployment was performed.

This falsifies the prior DRAINING failure for the natural-unload path. It does
not prove all-family health changes, abrupt/torn saves, missing/foreign-body
dispositions or indefinite late-return retention. Those SA-06/07 scopes remain
open, as does the full farmer/production/successor product story.

### SA-06 cargo entity-storage readiness aligned — 2026-09-23

Source follow-up found the shared release readiness only guarded actor columns.
If actors were ready while a missing cart's column was still loading, release
could report carrier-unavailable prematurely. FrontierV3SceneReleaseReadiness
now checks both retained/current cargo columns using actual entity-storage
readiness. A present cart or retained observation bypasses the wait and reaches
the unchanged exact validator, so contradictory evidence cannot hide behind
unloaded terrain. Non-logistics scenes and interrupted operations do not acquire
a spurious cargo wait. No chunk loading, custody transfer or body inference.

Five readiness and six departure-consumption tests pass (12s,session30034
terminal0), including split current/retained readiness, available evidence and
non-cargo exclusion. This follow-up was after the successful graphical candidate;
its split-storage branch has focused coverage, not a new graphical acceptance.
All native processes from that completed candidate remain stopped. Shared
missing/foreign/dead-body and abrupt-save composition plus bounded late-cart
retention still require closure; no SA is globally closed by this receipt.

### Remaining integration failures: retention and medical recovery — 2026-09-23

Re-ran only the eight still-unresolved methods from the old 820-test report,
using task-local temporary storage. Session68496 failed in17s: seven methods
still failed; the tiny WAL diagnostic passed. The two production methods had
already passed their earlier registered-owner corrections. No full gate rerun
or waiver of unresolved failures.

Three failures were stale test construction/assertions:
- Retention integration created UNKNOWN without its required diagnostic. Its
  route-owned fixture now uses the explicit ROUTE_OPERATION diagnostic producer;
  original retention, compaction, other-owner progress and recovery assertions
  remain intact.
- Architecture guard searched for an obsolete quota error string. It now
  checks current executable equal-owner quota and aggregate-bound guards; actual
  composition tests still reject enlarged owner shares and verify all quotas.
- Assault conflict expectation constructed an invalid unstamped conflict. It
  now compares the producer-stamped result, checks exact reason/subject, and
  reduces it to verify the separated defender is not moved.
These selected suites passed11s (68710), then again in the final selection.

Medical recovery was not merely a stale test. Routing preparation/transition
through the real registered lifecycle (rather than raw intent storage) first
restored its required recovery fence/diagnostic, then exposed the actual defect:
UNKNOWN treatment fails requiresSupply(), and the same HOT permission guarded
both starting consumption and accepting its already-observed receipt. Thus a
late exact result could not reconcile. FrontierMedicalTreatmentSceneSupport now
separates start permission from receipt permission; registered medical admission
and retirement use the latter only for CONFIRMED. The physical exact-consumption
selector admits medical UNKNOWN for its existing read-only inspection branch,
not another write, and selects medical ownership by the declared lifecycle owner
instead of collection-membership inference.

Regression retains UNKNOWN/team/supply/fence across snapshot encode/decode,
rejects RUNNING replay, rejects receipt permission without the current HOT scene,
then accepts the exact receipt through its registered owner, consumes the one
supply and completes treatment. Final combined medical/retention/composition/
architecture/assault selection passes12s (90237 terminal0); NeoForge compile
passed in prior10s selection (76424). No native medical/restart acceptance yet.
Earlier failed medical selections10560/98868/65116 are diagnosis, not greens.

Remaining from the old full report: HiveRouteEngagementProcessTest foreign-target
admission, SettlementAssaultTest selected-strike lifecycle, and the other
MedicalEvacuationOperationTest physical-supply lifecycle method. All use raw intent
storage in places requiring owner composition; migrate their real preconditions
and preserve negative assertions before attributing results. Also inspect medical
reducer/replay permission parity and native UNKNOWN inspection composition.
SA-01–SA-10 and the complete product story remain open; no deployment/commit.

### Registered medical replay and combat composition — 2026-09-23

Medical command admission had a HOT-scene guard but both ordinary and retirement
reducers omitted it. A genuinely owner-planned terminal event (including its
retirement proof), encoded/decoded and replayed against DRAINING, reproduced the
bypass (25054 failed). A bare unaccounted terminal event had initially failed for
missing retirement proof instead; that weak negative is not evidence of permission
parity. FrontierPopulationProcessModule now uses the same start/receipt predicate
for planning and both reducers. Exact UNKNOWN receipt replay against current HOT
matches registered reduction; DRAINING receipt and UNKNOWN -> RUNNING reject.
Focused replay test passed8s (17784 terminal0).

The second medical test now prepares/transitions through the registered lifecycle
with its exact admitted HOT scene rather than raw storage. It checks actual supply
consumption, patient recovery and retained UNKNOWN team. Shared medical scene
fixture also prepares through the registered owner. All ten medical tests passed
12s (31424 terminal0), and again in the combined selection below. No native medical
recovery claim is made.

SettlementAssaultTest's selected-strike lifecycle now uses the registered owner
for all preparations and transitions, retaining foreign attacker/target, stale
health/expedition, epoch non-replay, release and snapshot assertions. Its class
passed in the combined selection. Route-combat test was likewise migrated, and
exposed a real completion permission inconsistency: the exact lethal receipt
arrives in DRAINING, but SceneStrikeStateSupport.owner calls matchingHotLease;
registered route lifecycle therefore rejects it. Do not change the test to submit
before draining. Resolve retained ownership/receipt permission separately from
new-strike admission, with stale/foreign/closed lease negatives.

Combined medical/assault/route classes:35 tests,2 failures,61s, session26588
terminal1. Other failure: autonomousSupplyProfileCreatesOnlyAnExactScoutBoundInterceptionBeforeTheMobilizationOwnerExists
remains ACTIVE but records no Scout sighting by tick10000; source/fixture attribution
is still open, not waived. Exact report is implementation
`pale-mirror-frontier/build/test-results/test/TEST-io.farfrontier.palemirror.frontier.v3.model.HiveRouteEngagementProcessTest.xml`.
No native/server process was started. Source diff-check passes; no deployment.

Additional SA-03 source follow-up: ExactItemConsumptionExecutor still chooses the
lexically first pending intent before checking loaded chest/custody; a retained
UNKNOWN/unloaded head can starve independent consumption. Adopt the existing fair
turn owner and test ambiguous/unavailable head versus another prepared effect,
without allowing UNKNOWN to replay. This path was not covered by the earlier
production-transformation fairness repair.

### SA-03 consumption fairness and strike binding follow-up — 2026-09-23

ExactItemConsumptionExecutor now services one eligible intent per invocation via
the existing runtime-scoped FrontierV3FairTurn. Attempts rotate even when the
selected chest is unloaded, custody is unavailable or an UNKNOWN postcondition
remains unresolved. Medical permission filtering and the read-only UNKNOWN
execution branch are unchanged. Runtime forget clears the cursor through the
existing scene cleanup boundary; no canonical progress or authority is cached.
Two actual-selector tests plus four fair-turn tests pass6s (53905 terminal0),
with source/test compilation. This is selection evidence, not native chest or
interruption evidence. Initial isolated fixtures incorrectly tried to admit
noncanonical causes into world state (63578/29198 failed); final tests supply an
explicit selector inventory without weakening state validation. Runtime passes
its actual canonical intent inventory to that same selector. No native started.

Combat repair requires more than allowing DRAINING in owner lookup. Route strike
roles retain attacker/target but no explicit scene identity/revision; the native
producer encodes revision in a string ID, which must not become inferred authority.
Assault has a hash-based lease validator, but shared matching still scans scenes.
Add explicit producer-supplied durable strike/scene binding and adopt it in
preparation, continuation, receipt, replay, native pending selection and release
before loosening HOT-only ownership. Retain negative old/foreign/closed/revision
coverage and stable codec/version rejection. The real DRAINING failure and the
independent absent-scout-sighting failure remain open; no combat fix claimed.

### Exact strike scene binding and draining receipt repair — 2026-09-23

Implemented PhysicalSceneBinding(leaseId,revision) in strike role bindings.
Factories now require both values; generic decode cannot create an unbound strike
or attach a scene to another kind. Shared payload/snapshot helper serializes the
binding. New schema tags28/29 replace permanently retired7/8: old unbound records
reject at schema decoding, no ID parsing/backfill. Native producer and all compile
call sites explicitly supply the scene; native pending selection filters that
same binding. Domain lookup selects declared lease ID and checks revision/cause,
not the first HOT scene belonging to an operation. Both ordinary and retirement
planners/reducers use transition permission: RUNNING only HOT, CONFIRMED HOT or
DRAINING, no new transition on CLOSED. Existing postcondition validation remains.

First composed run8586 exposed a second real defect: retirementFacts still called
validateIntent (pre-effect HOT/living-pair admission) even for an already lethal
result. It now validates transition authority and accounts for the exact bound
lease, not merely the cause. Route lethal receipt -> DRAINING confirmation ->
release/snapshot and assault selected-epoch suite then passed (5989,20s).
Added explicit foreign ID/revision admission rejection, no RUNNING in DRAINING,
and no new authority from a retained CLOSED receipt. Payload/snapshot round trips
cover both families; missing binding, negative revision and old wire tags reject.
Final targeted combat/binding/owner-codec selection passes20s (73012 terminal0).
NeoForge main/pilot/tests compiled during8586 before the final domain-only repair.

Not complete combat or SA acceptance: actual native lethal drain/restart, broader
role-consumer/relationship cleanup adoption and ambiguous strike recovery remain
to be inspected. The autonomous supply no-sighting failure remains separate and
open. In particular audit older mobilization fixture that declares a route-owned
strike against an assault scene, plus release/retention callers, before a broad
completion claim. No native/server was started, no deployment/commit. Diff-check
passes. The relations contract now documents exact binding/transition semantics.

### Strike family and retention adoption — 2026-09-23

SceneStrikeStateSupport.boundTo now validates the declared lifecycle family as
well as ID/revision: ROUTE_ENGAGEMENT accepts logistics, SETTLEMENT_ASSAULT accepts
assault, other owners reject. Added wrong-family/exact-ID negative to assault
admission. Older integrated mobilization test incorrectly used ROUTE_ENGAGEMENT
for its assault and raw storage transitions; now explicitly declares assault and
uses registered lifecycle preparation/transition. Mobilization and assault suites
pass44s (21446 terminal0), retaining parent/team/expedition return/recovery checks.

Closed-scene compaction previously considered only CLOSED status, despite retained
physical receipt references. FrontierSceneLeaseStateSupport now excludes scenes
referenced by explicit physical bindings; existing fixed1024 limit remains. Tests
check receipt pins its exact closed scene and an unreferenced closed scene remains
eligible. Final assault/route selection passes20s (72443 terminal0); diff-check
passes. This is not a full retention-capacity or native compaction proof.

Remaining release composition identified by source: generic release and registered
logistics release reducer do not explicitly dispose of unresolved bound strikes.
Need test PREPARED/RUNNING/UNKNOWN release, compose lawful unstarted cancellation
and retain/resolve possible effects before CLOSED; merely leaving a permanently
blocked scene is insufficient. Also reconcile retained receipt/scene compaction
under actual capacity and complete native lethal-drain/recovery validation.
No native/server/deployment from this work; scout-sighting attribution still open.

### Unstarted strike release disposition — 2026-09-23

Shared scene release now invokes SceneStrikeStateSupport.prepareRelease before
closing custody. For an exactly bound PREPARED strike it verifies the PREPARED
effect fence, retires that authority with explicit scene-release-unstarted-strike
ABANDON tombstone and removes the unexecuted intent in the same resulting state.
It creates no hit/observation or strike-epoch advance. RUNNING/UNKNOWN reject
release; CONFIRMED/CONFLICTED retain their terminal history. Registered release
planning runs the same check before producing journal events; reducer/direct model
release repeat it. Other scenes do not acquire strike ownership.

Assault regression checks cancellation, fence retirement, no fabricated epoch,
snapshot recovery and stale-start rejection; RUNNING and UNKNOWN reject both
planning and reduction. Existing accepted-receipt release still passes. Final
assault/route selection passed19s (83708 terminal0), diff-check passes. No native
process or deployment. This is NOT complete started/ambiguous-effect recovery.

Confirmed remaining native gap: SceneExecutor.strike returns immediately for
UNKNOWN pending strikes; it has no persisted exact-hit inspection/settlement
path. RUNNING damage happens before receipt submission, so interruption between
those steps needs explicit physical outcome evidence, not damage replay or a
health-difference guess. Next compose evidence producer, receipt inspection and
draining/restart resolution for the exact binding; preserve local ambiguity when
evidence cannot distinguish an intervening external hit. Do not declare the new
release guard a complete recovery solution or close SA on safety-only blocking.

### SA-01 closure receipt — 2026-09-23

Checked current source against every original SA-01 requirement, not against
test count. Main implementation HEAD remains eaaa6e70 plus preserved WIP.
Changed shared owners: InMemoryFrontierEngine selects owner-eligible work before
budget allocation and rechecks eligible head before each admitted execution;
ScheduledActionQueue preserves ordering against newly inserted earlier work;
ScheduleEffectApplier and TransactionReplayer share the held-owner predicate.
Registered harvest and production continuations supply the actual hold policies.

- Held A / eligible B / constrained budget: kernel ownerHeldHead test proves B
  receives the only slot and a held-only tick creates no no-op WAL transaction.
- Exact A identity/deadline: that test and real-hot-harvest composition retain
  the same ScheduledAction bytes before/after B and release.
- Subsequent A release: kernel test resumes A after hold removal. Extended
  HeldHarvestSchedulingCompositionTest additionally releases actual harvest
  through registered DRAINING/Released commands with exact EngineScheduleBinding,
  takes another snapshot, recovers, and progresses the same job and farmer with
  a one-action budget. Initial test envelope omitted that binding (90019/45426
  failures); corrected fixture supplies actual checkpoint authority, not bypass.
- Causal ordering: eligibleHeadStillFencesEarlierWorkCreatedAfterBatchAdmission
  rejects stale admission when newly inserted earlier work exists, and cancelled
  batch entries cannot execute.
- WAL monotonicity/recovery: ownerHeldHead replay equals live state/schedules;
  heldDueContinuationNeverWritesHistoricalTimeAfterAnInterveningPhysicalCommand
  covers intervening command time; no-hold replay rejects skipping a runnable head.
  Actual field B grows after snapshot recovery while A remains held, and A resumes
  after release/recovery, covering production catalog adoption rather than only
  a synthetic counter.

Final three-suite selection passed8s (42674 terminal0): InMemoryFrontierEngineTest,
ScheduledActionQueueTest, HeldHarvestSchedulingCompositionTest. Source diff-check
passes. No native run needed to prove this pure scheduling defect; physical field
projection, release evidence and complete player cycles remain explicit SA-02–09
and F0.6R3 acceptance obligations, not inferred from this closure.

SHA256 source identity (relative K/kernel/):
- InMemoryFrontierEngine.java: 7b6c849cbd71b542f59a6ba7838d6231e1ba75c786a11b09efadbb658ac885ab
- ScheduledActionQueue.java: cfe7f5e8c0c022ac8b37d51870a82c0f6419ffe0b9532765e137c9efb56df0e3
- ScheduleEffectApplier.java: eeee9a3fef53681fe5b0b6700aadd1ef6213f7cb1e32120f5b991d098e9531c2
- TransactionReplayer.java: ada91b8ecb72dc8139a343f53c4d8c24c2fda0e1a49c18f264ac99fe469cd22d
- Test/model/HeldHarvestSchedulingCompositionTest.java: 8e51a00cbf0e3ceab161f7c1700aa3cb693631529f305189d70944f4c406f2ae

The goal remains active. Do not require another scheduler-wide campaign without
a changed dependency or new counterexample; next prioritize original field and
production acceptance gaps while retaining separately identified combat recovery
debt. No commit, publication, deployment or live-server mutation.

### SA-10 closure receipt — 2026-09-23

Reviewed original requirements against engine, replay, queue overlay and installed
FrontierWorldStateTransitionValidator. Unchanged-state transactions validate
retained added/replaced schedules before WAL; changed-state transactions validate
final state and all retained schedules. Replay applies the same split and checks
initial recovery schedules. Mutation.retainedChanges returns the retained overlay,
not all future work. Existence validation uses direct membership/bounded bootstrap
lookups, not a copied world-wide ID union. Runtime installs this validator.

Reused unchanged source evidence from42674 (8s):
- InMemoryFrontierEngineTest.scheduleOnlyReferencesAreCheckedBeforeWalWithoutReauditingStateOrUnchangedQueue:
  valid creation accepted; missing-owner replacement rejected before WAL/revision/
  queue mutation; only changed references checked; valid replay succeeds and
  history from the old unchecked path rejects.
- ownerRetirementMustCancelItsScheduleInTheSameDurableTransaction: removing owner
  without cancellation rejects before publication; atomic pair accepts/replays;
  initial recovery with lingering retired-owner continuation rejects.
- HeldHarvestSchedulingCompositionTest.fieldConflictCancelsItsContinuationAtomicallyAndRetainsDispositionOnRecovery:
  actual registered field conflict and continuation cancellation in one transaction;
  WAL/snapshot recovery retains witness without runnable retired work.

Production validator adoption additionally passes the current focused
SupplyOperationProcessTest.canonicalReferenceClosureRejectsDuplicatePhysicalCustodyAndRetiredScheduledOwnersBeforePublication
(31485 terminal0,7s): real contract owner accepts; absent contract rejects through
incremental and recovery checks. No additional matrix/native run is needed for
this pure barrier defect. This does not establish all producers' lifecycle
correctness or native product acceptance.

Source identity: kernel/queue/replay hashes unchanged from SA-01; additionally
K/model/FrontierWorldStateTransitionValidator.java SHA256
c30bc65cadc8997730d2791e9dac514b2fecd238842c498be7e344ae4a3302f2;
K/model/FrontierReferenceClosure.java SHA256
c485d845458110ac1f0fe4e6c57fe625c275f54159396c34692ef54b4fd05a60.
SA-02–09, native multi-settlement/repeated cycles and overall goal remain open.

### SA-02 physical two-field bounded writer composition — 2026-09-23

Added pilot-only FrontierV3FieldTurnGameTests and focused `field-turns` slice.
Package-visible production projectLifecycleBounded is the same entry used by
ordinary projection; no alternate writer or forced-load path. In an isolated
template, real Minecraft blocks and per-world ResourceSiteLedger show:
A writes one8-cell batch; only B is demanded by the real candidate selector;
B completes132 managed cells while A's cursor remains8; volatile projection
cache is forgotten; A validates its persisted prefix, resumes at16 rather than
replaying0, and completes its physical field. Runtime identity is test-local;
canonical lifecycle setup is fixture state, not observed player evidence.

Native command: `./gradlew :pale-mirror-neoforge:runFrontierV3SceneGameTestServer
-PfrontierV3GameTestSlice=field-turns --no-daemon`, task-local TMPDIR/JAVA_TOOL_OPTIONS.
One required GameTest passed; process67519 terminal0,26s total, test792ms;
server saved/shut down03:34:08 host time. Process check shows no task native JVM.
CompilePilotJava had passed8s (38472). Source diff-check passes.

Limits: explicit demand input and cache loss, NOT actual player chunk departure,
filesystem crash/restart, screenshot or human M3. SA-02 remains open pending
appropriate real ingress/re-entry composition. No live-server deployment.
Before launch, previous default GameTest run directory was moved intact to
`pale-mirror-neoforge/build/runs/frontier-v3-scene-game-test-before-field-turns-20260923`;
current evidence is `pale-mirror-neoforge/build/runs/frontier-v3-scene-game-test/logs/latest.log`.
Nothing deleted: operation preserved old evidence before Gradle's disposable-run
reset hook. Test-server skill used only to check isolated targets/process ownership,
not its standing live-deployment policy, which current assignment excludes.

### SA-05 physical successor writer evidence — 2026-09-23

`FrontierV3FieldTurnGameTests.resolvedHarvestRestoresGrowingAndReadySuccessorsWithoutOverwritingForeignCells`
now exercises the production bounded writer against actual Minecraft blocks and
SavedData. Adapter preconditions explicitly supply a resolved predecessor lineage
and terminal 64-slot claim; this is not proof of the economic producer of that
lineage. Both GROWING/stage2 and READY/stage7 complete the physical successor.
READY reverses its harvested cursor 64 -> 56 in the first eight-write batch;
volatile-cache loss retains a valid prefix and both variants finish at zero.
A foreign diamond block rejects before starting a projection, remains unchanged,
and retains the old 64-slot cursor.

Compilation passed (83348,4s). Native `runFrontierV3SceneGameTestServer
-PfrontierV3GameTestSlice=field-turns --no-daemon` passed both required tests
(61190 terminal0,24s; tests1.080s; server shutdown03:41:31 host time).
First combined attempt44506 failed because the two fixtures reused site IDs
in level-wide SavedData; distinct IDs10–12 corrected test isolation, not product
semantics. Its evidence remains in the NeoForge build/runs directory
`frontier-v3-scene-game-test-successor-shared-id-failure-20260923`; preceding
green evidence is in `frontier-v3-scene-game-test-before-successor-20260923`.

SA-05 remains OPEN: pending receipt, active successor and actual lifecycle/player
composition still need verification. Source review identifies a further specific
risk to test next: `isExactSuccessorRegrowth` checks the canonical active cursor
but not physical predecessor equality; its true value bypasses `matchesClaim`
on the first writer batch. Later prefix validation cannot protect that first
batch from overwriting a foreign cell. Check and repair this active-successor
path without weakening lawful terminal/current-surface recovery.

### SA-05 active successor first-write protection — 2026-09-23

The above risk is reproduced and repaired. New native case
`activeSuccessorValidatesItsWholePredecessorBeforeTheFirstWrite` supplies an
active three-slot successor and the previous all-AIR terminal field. The lawful
case restores only the remaining mature crops, preserves the three-slot AIR
prefix and completes after volatile-cache loss. The damaged case places a
diamond block in the first reverse-write position. Before the repair it failed
the requirement to reject before the first batch (69192 terminal1,25s).

`isExactSuccessorRegrowth` now checks actual `matchesClaim` as well as the
canonical active cursor. All three callers, including restart reconciliation,
use this shared predicate. Canonical permission alone no longer bypasses
physical predecessor validation. No blanket prefix check was added to unrelated
recovery paths that deliberately recognize different exact predecessor surfaces.

Final native field-turns: all3 pass (63918 terminal0,26s,1.407s tests,
shutdown03:45:12). The negative asserts unchanged foreign block, cursor64 and
no projection. Three affected unit suites also pass (75990 terminal0,9s).
Source diff-check passes; no task GameTest JVM remains. No live deployment.
Native fixture's first attempt53174 failed its own nonadjacent row ordering;
the shared test geometry now uses a contiguous serpentine order. That failure,
the actual product-red run and prior green run are preserved under NeoForge
build/runs with suffixes `active-successor-topology-fixture-20260923`,
`active-successor-red-20260923`, and `resolved-successor-green-20260923`.

This proves the physical writer boundary against explicit adapter preconditions,
not automatic successor-job generation or actual player departure/re-entry.
Pending receipt physical composition and complete product lifecycle remain open;
SA-05 is not yet declared closed.

### SA-05 pending receipt across active successor — 2026-09-23

Extended native resolved-successor test first supplies an unresolved predecessor
to GROWING and READY. Both retain the full terminal AIR field without creating a
projection, including after cache loss; the separately supplied resolved state
then completes regrowth. Native4227 passed (25s,shutdown03:46:59).

Source inspection of `ResourceSiteHarvestProcessTestTraversalTest` confirms that
an active successor can coexist with a pending predecessor. Added this native
adapter precondition to the active-successor case. It reproduced another defect:
the writer returned DEFERRED after writing its first restoration batch, destroying
the pending receipt surface (24997 terminal1,25s). This is why asserting only the
DEFERRED result would miss the bug; the regression asserts actual blocks and
absence of a projection cursor.

The shared `matchesCanonicalProjectionTarget` now recognizes exact demand for
GROWING/READY or the retained HARVESTING cursor. The all-AIR deferred-receipt gate
uses it, so an active successor cannot erase its pending predecessor. The partial
terminal-prefix admission and receipt-executor awaitingProjection query use the
same target predicate/current cursor rather than assuming all pending receipts
have an inactive successor. Exact intent/role/claim/surface checks remain intact.

Final combined field-turns native3 tests and ResourceSiteExecutor unit suite pass
(23993 terminal0,29s; native1.297s;shutdown03:49:53). No task native process.
Evidence directories preserved with suffixes `awaiting-receipt-green-20260923`
and `active-pending-receipt-red-20260923`; latest green remains the default run dir.
Source diff-check passes. No live deployment.

Remaining scope: native active **partial** predecessor with a real retained
RUNNING intent, receipt completion then successor resumption, actual lifecycle/
player composition. Current native fixtures supply adapter state, not registered
economic receipt transitions. SA-05 remains OPEN; no full-product promotion.

### SA-05 partial receipt interleaving and physical output — 2026-09-23

Extended native active-successor fixture with exact RUNNING predecessor intent
and21 harvested cells. After the first bounded terminal-suffix batch the physical
AIR prefix is ahead of the harvest count, which commits only at completion.
awaitingProjection compared the old count and incorrectly stopped waiting.
Native50292 reproduced this (terminal1,25s). The common prefix matcher now
validates an ADVANCE-to-mature64 transition through its persisted write prefix
and original count, retaining exact lineage/intent/role/claim ownership checks.
Native3 and ResourceSiteExecutor unit suite passed10960 (26s).

The extended sequence reaches terminal64 after cache loss, releases the receipt
wait, calls real completeRunning into a Minecraft chest twice and asserts only64
wheat. Given a separately supplied resolved/retired canonical boundary, the same
active successor resumes at its three-slot prefix without duplicating old wheat.
Final native3 pass48167 (terminal0,25s,1.338s tests,shutdown03:54:21).
No task native JVM remains; source diff-check passes. No live deployment.

This is physical adapter composition, not registered canonical confirmation,
economic accounting, filesystem restart or player movement. Initial fixture37156
correctly failed reference validation by resolving lineage while retaining an old
RUNNING intent; the fixture now supplies the resolved/retired tuple atomically.
It does not claim to exercise production retirement; that remains to verify.
Prior runs are preserved under NeoForge build/runs, scene-game-test suffixes:
active-pending-green-20260923, partial-receipt-interleave-red-20260923,
partial-receipt-interleave-green-20260923, receipt-fixture-nonatomic-20260923.
SA-05 remains OPEN for registered receipt/retirement and full player composition.

### SA-05 canonical late receipt in READY and HARVESTING — 2026-09-23

Extended existing `ResourceSiteHarvestTraversalTest` registered-owner composition
`scheduledColdHotLeaseIntentObservationReconciliationAndRestartRetainOneExactReceiptAuthority`.
Its real HOT-started/COLD-completed lineage is snapshotted, grown to READY and
admitted through the normal opportunity/start planners into a next job retaining
the same farmer. At both READY and HARVESTING, the old receipt is planned by
FrontierWorldRuntimeDefinition and reduced through its canonical event dispatcher.
Assertions preserve phase, epoch, exact active job/worker/cursor and inventory;
clear only the predecessor receipt; round-trip the result through snapshot codec;
and reject duplicate confirmation. The original GROWING assertions remain.

Targeted method selection passes72815 (terminal0,7s; inherited selections also
run). First attempt95055 failed only on uppercase phase text in a fixture command
identifier; corrected to locale-stable lowercase. Source diff-check passes.
No native run/deploy in this step. This closes the supplied-state gap for this
canonical planner/reducer transition, not whole-engine WAL/fs recovery or actual
player lifecycle. Next use relevant disposable graphical harvest/unload-return
scenario, not another unchanged adapter campaign. SA-05 remains OPEN for the
composed player boundary and any contradictions it exposes.

### Actual first visibility after COLD completion and restart — 2026-09-23

Prepared current dirty source with prepare-native-build.mjs (36972 terminal0,5s),
artifact SHA256 4c4ff20b4fa00fd025d901761f75e00d681504c19fd1e469f0adbc9f2acffe44.
Ran existing disposable-f06r3-first-visibility-cold-continuity.json with the
prepared native runtime, DISPLAY=:0 and authenticated local Xwayland, isolated
port25585, KEEP_DISPOSABLE=true. Runner1320 terminal0; result status ok,
229.185s total. Evidence: build/sa05-first-visibility-20260923/result.json,
result.before-restart.json, result.pmv3.jsonl and prepared.json. Seed47, world
v3-disposable_f06r3_first_visibility_co-91dddafb. Same persistent client JVM
across graceful server restart; all task client/server JVMs absent at completion.

Zero-player24000-tick request completed at24001 before any client segment.
Before restart, site7 is GROWING epoch2/stage1, old job1 has resolved COLD
composition, exact old wheat belongs to ACTIVE_COLD production7-48. Named farmer
resident7-31 has terminal body148/64/-6. After actual restart and natural visit,
site remains current without restarting the old harvest; production work admits
its named worker into HOT. Three captured frames were inspected: full young
wheat field, board reading GROWTH STAGE1/7, and named agricultural worker at the
terminal field-edge area. No reported quarantine; no server tick-stall marker.
Idle residents remain visible along settlement boundaries; these frames alone
do not prove their motion/first-instant admission or resolve all old complaints.

This is real graphical first-visibility/restart evidence, not a second complete
HOT harvest, leave-A/visit-B/return-A test, full-pack gate or human M3. The old
unload-return scenario only asserts presence/transient phase with a fixed sleep;
do not use it unchanged as full-cycle acceptance. Next use semantic lifecycle
conditions and named terminal/successor outcomes for the remaining player flow.
No live deployment/reset; preserved disposable world/evidence. Server-ops skill
used only for target/display/process checks under current isolated-only authority.

### SA-02 closure audit and actual demand switch — 2026-09-23

New checked-in disposable-sa-fields-demand-return.json passes59566 (terminal0,
212.999s). Prepared artifact unchanged4c4ff20b, seed47, isolated25585, world
v3-disposable_sa_fields_demand_return-daf1bf38. Real client visits site7 then
site1 immediately, exhaustively checks site1, returns and checks site7. Each
client predicate observes64 farmland,64 exact stage-one wheat and4 water cells;
both canonical fields remain GROWING epoch2. Final summary has zero required
conflicts. Graceful stop/save04:10:29; task JVMs absent. Evidence is
build/sa02-demand-return-20260923/result.json and sibling identity/JSONL.

Both frames were inspected and are NOT visual acceptance: chunk render meshes
had not settled although client block storage was complete (floating crops/sky).
Do not promote the block predicate to a claim of seamless rendered presentation.
The scenario has no invented domain sleep; a future visual scenario needs an
explicit render readiness/camera condition. Do not rerun this whole COLD prelude
merely to improve these frames when testing the scheduling defect.

SA-02 original criteria are established by complementary evidence:
- Exact partial-A case: real bounded writer/native FieldTurnGameTests writes8,
  removes A demand, selects/completes B without changing A's cursor; loses cache,
  validates persisted prefix and advances A to16, then completes. Native48167
  retains this passing case; initial67519 and subsequent slices also passed.
- Real player/demand integration: new59566 proves leave-A/visit-B/return-A with
  exhaustive client physical predicates. This run alone does not prove that A
  was still partial at departure; the deterministic native case proves that edge.
- Ownership/recovery: writer restores SavedData cursor and validates committed
  prefix/untouched suffix before resumed writes. Previously passing resource-prefix
  native tests cover SavedData reload/foreign suffix; no overwrite is authorized
  by retained cache. Ordinary and recovery selection both check loaded+demanded;
  loaded uses hasChunkAt, never a force-load. Forget removes only volatile maps.
- Selection no longer depends on a global in-flight set. Recovery round-robin
  retains A's record while independently allowing B's ordinary turn; focused
  selector tests cover departure, return, no demand and recovery exclusion.

Source reviewed at closure: ResourceSiteExecutor SHA256
d08eda024faa8ef32eceb661f1e9dc41e76f4e5bc808060e2c8576c7d001de58;
FieldTurnGameTests1a7b74bc02a3544a071ec42056a86b6a8e6f84ddee655ff7aa7565463919370b.
No new runtime change in this step. SA-02 CLOSED for this scheduling defect;
SA-01/10 also closed, SA-03–09 and complete product goal remain open.

### SA-06/07 missing-history acknowledgement bypass — 2026-09-23

Source audit found that CargoCleanupPersistence applied full-column checks only
when the footprint list was nonempty. An old or missing birth archive could
therefore acknowledge REMOVE or surviving RETAIN from one stored column, even
though historical columns were unknown. An empty archive is not proof of no
historical copies. Removed the bypass: selection requires the exact birth/epoch
footprint, and the final saved-marker boundary independently rejects its absence.
No fabricated history, entity restoration or world migration was introduced.

Session78717: CargoRetirementFootprintTest6 and CargoCleanupPersistenceTest11,
17 passed, zero failures/skips,9s. Negative regression covers empty and wrong-epoch
history for both terminal dispositions. This is focused safety evidence only;
native composition, explicit legacy-world rejection/recovery policy and full
SA-06/07 closure remain open. Live server unchanged.

Native regression follow-up:75644 all7 existing cargo-interaction cases passed
(40s). Added successfulSaveWithoutBirthHistoryCannotAcknowledgeRetainedCart:
ordinary registered handoff/close, real cart serialization, injected loss of its
historical archive, then successful provider-write/sync futures. Exact retirement
remains and both inventory and player cart are preserved.62333 all8 passed42s,
complete save/shutdown10:03:30. This proves server-side selection/ack composition,
not real storage loss/crash recovery. Invalid initial slice4820 ran zero tests
despite Gradle success and is explicitly NOT evidence. No live deployment.

### SA-06/07 cargo footprint compatibility boundary — 2026-09-23

Applied the existing contract's fresh-world-only schema policy, not a new
migration strategy. Canonical schema180 denotes the provider protocol requiring
durable cargo storage footprint from physical birth.179 worlds may contain carts
created before this protocol; no load-time heuristic can reconstruct their prior
storage columns. Header selection and complete hydration both reject old versions
before interpreting mutable state. Errors report actual/required schema numbers.
No legacy data rewrite, inferred archive, reset or live deployment is performed.

56292 terminal0/13s: current180 encode/decode/encode equality, rejection of
177/178/179 at both boundaries with input unchanged, plus retained cargo codec
and explicit carrier disposition regressions pass. This resolves declared old-
format compatibility only. Lost/corrupt archives in a current world, failed final
removal IO, bounded-capacity recovery and crash compositions remain open.
Current live179 diagnostic world must not be upgraded in place; a future authorized
fresh world must preserve it and its rollback artifact.

### SA-06/07 final-removal IO retry — 2026-09-23

The final-removal observer previously logged IOException and discarded the event.
Added a bounded per-level retry queue of exact nominal observations, retained
before archive IO. It never recreates inventory or infers historical columns.
Each retry rereads and validates the exact birth/epoch record, preserves declared
history checks and appends the observed removal column. Only successful durable
publication consumes an event. EntityStorage writes, cleanup save-pass selection
and final acknowledgement flush this queue first; archive changes invalidate the
cleanup index. The queue holds no entities/levels in its values.

Limits:4096 pending events,8 durable writes per pass. Overflow or invalid
declaration latches a visible save fence until restart rather than silently
dropping evidence. This is safety/backpressure, not completed overload recovery
or a product-comprehensible incident path. Abrupt-crash/region ordering remains
unproved; memory retention alone is not a durable-event claim.

49979 component7 green10s;31211 retry4/observer3/cleanup11 green9s. New native
case injects unavailable historical archive after ordinary RETAIN/close, observes
real cart removal, verifies empty-column writes are fenced, restores the original
fixture history, then verifies retry publication before permitting the write and
subsequent exact cleanup.46914 failed compilation from missing checked-exception
handling in this new test; corrected.65498 terminal0/44s, all9 tests passed,
full save/shutdown10:12:35. No deployment or full SA-06/07 closure.

### SA-06/07 abrupt cargo recovery exposes pending cleanup — 2026-09-23

Added declarative disposable-released-cargo-destroyed-abrupt.json, ordinary
64bread theft and observed empty-cart removal followed by authenticated client
disconnect and exact task-JVM abrupt stop. This is not an armed mid-write crash
and does not independently inspect physical player inventory after recovery.
Node4 checks pass; frozen artifact8735c77281515e3586e4c57f2b0d33e91df274c7187e9ff777553a65835d286d.

55691 terminal1: original8 actions pass; recovered canonical player64 and
INTERRUPTED pass, CLOSED cleanup remains pending until the30s assertion fails.
Save completed10:17:05, final controlled shutdown10:17:50. Evidence retained in
build/sa-cargo-abrupt-20260923/result.failure and same-run manifests. Raw retained
footprint confirms exact UUID e1754fb1-cb62-3c72-9d5a-dbd31f38796b, revision123,
epoch1, actual removal flag and column(-23,-22). The event was not simply lost.
Do not repeat unchanged or classify as method-only without locating why saved
coverage/ack is missing. Source currently discards entity reads when there is no
pending retirement and filters other columns, unlike its all-UUID write history;
save/read completion timing is another unresolved discriminator. No closure.

Early-read defect repaired after source review: the observer no longer discards
actual entity reads merely because no retirement exists yet or its column is
not yet indexed. Bounded UUID/column evidence is retained exactly as for writes;
failed reads still cannot establish absence and later writes invalidate reads.
Removed obsolete prefilter-only test in favour of native composition regression
storedAbsenceReadBeforeRetirementStillClosesTheLaterObligation: registered player
handoff, observed removal, controlled empty read BEFORE terminal close, no second
read/write, successful sync and exact cleanup acknowledgement.99538 all10 native
cargo-interaction tests pass49s, shutdown10:22:00. This is an independently
reproduced ordering gap; whether it resolves55691 remains to be checked.

41645 disproved sufficiency: same abrupt flow still pending after ordinary save,
terminal1/shutdown10:25:01; retained build/sa-cargo-abrupt-early-read-20260923.
Actual cached NeoForge EntityStorage source reveals uncovered emptyChunks branches:
loadEntities returns cached absence without SimpleRegionStorage.read; empty
storeEntities suppresses IO entirely for a cached column. The mixin only
redirected IO calls. Vanilla also caches empty BEFORE deletion write completion,
so a failed/fenced write can suppress subsequent retry.

EntityStorageMixin now observes cached-empty load and skipped store as read
evidence (not a fake successful write), and removes the cache entry on failed
write via server-thread callback. Pending/failed writes remain in EntitySaveBatch
and cannot be waived by that read. No chunk request, entity mutation or world edit.
8891 terminal0/8s: batch8/cleanup11 pass and main/pilot compile. This is not yet
native cache/mixin or abrupt-flow acceptance. Further verification remains.

88262 same abrupt scenario still failed (terminal1, shutdown10:30:36), now with
discriminating evidence from bounded read-only save-pass diagnostics. At10:29:52:
completed pass, one eligible candidate, carrier unloaded/absent, sole footprint
column known absent, no failed writes or overflow; ticket unavailable because609
of613 read observations still awaited server-thread processing. EntityStorage's
save completes before those callbacks, and the cleanup owner previously returned
without registering any continuation. No second ordinary save occurs within the
scenario. This is an owner continuation gap, not evidence of lost removal history
and not solved by extending the timeout.

Implemented one deferred read barrier per index: after those actual observations
finish, re-enter completed-pass processing on server thread, rebuild candidates
and use current write/sync fencing. Failed reads remain failures and cannot loop
as unfinished observations. Added unit failure/barrier regression and native
storedAbsenceCompletingAfterSavePassResumesWithoutAnotherSave;5626 terminal0/51s,
batch9 and native11 pass, full shutdown10:32:25. No additional vanilla save,
canonical clock advance or inventory repair. Graphical abrupt flow still pending.

26791 terminal1/shutdown10:35:47: graphical abrupt flow still pending after the
read-barrier repair (build/sa-cargo-abrupt-read-barrier-20260923). No acceptance
claim. Source self-review found a second dropped-continuation path: a new provider
observation during asynchronous sync invalidates the ticket; callback previously
returned silently instead of rebuilding the completed-pass proof. Changed that
branch to recompute candidates/current write+sync evidence, keeping runtime/index
identity checks and never accepting an obsolete generation. Added native
newReadDuringSyncRebuildsCleanupProofWithoutAnotherSave.37623 terminal0/52s:
all12 cargo-interaction tests passed, full shutdown10:37:52. Log confirms the
superseded generation1 -> generation2 continuation branch was exercised.
This source-confirmed gap is not yet conclusively correlated as the remaining
native failure cause. No unchanged confidence run or increased timeout.

Follow-up79728 terminal0, graphical abrupt scenario succeeds with artifact
9173fe7206c6e06b55f501314f756e9540438762166dcae46beb2c04d8e0e2f0;
build/sa-cargo-abrupt-sync-continuation-20260923/result.json. Actual replacement
server logs exercise superseded-proof rebuilding and reach CLOSED/pending=false
at10:48:17, retaining canonical player64. This resolves the retained reproduction,
not all IO-crash windows or physical player inventory acceptance.

## Live empty-world contradiction — 2026-09-23 10:48 investigation

User reported the live world empty. Read-only service/config/log/hash checks
confirm unchanged diagnostic artifact/world, Java2273025, service2273001.
At09:54:13.531, before user rd joined10:47:28, runtime quarantined:
`scheduled action references a retired canonical subject:
schedule:hive-infection-task-task-hive-frontier-hive_expand_infection-417-7`
with outer action frontier.objective.review. Minecraft remains available, but
simulation is not healthy. This does not alone prove the player's dimension or
that every absent physical structure has that sole cause. No restart, reset,
deployment or canonical repair performed. Trace objective/task retirement and
scheduled infection ownership; preserve the failed world's snapshot/WAL.

### Live quarantine cause and repair

Source: StrategicPlanState.compactFor removes the oldest terminal objective and
its tasks at128 objectives/512 tasks, without coordinating the kernel schedule.
HiveInfectionProcess's stale-task cancellation on eventual dispatch is too late:
the publication barrier rejects the earlier objective-review transaction which
first removes that task. This is not an erroneous closure check.

Implemented registered reducer-owner schedule retirement. After each domain
reduction the kernel asks the exact registered event owner for retired actions,
checks each against the pending queue, and appends ordinary Cancelled events
before final validation/WAL. Strategy authorizes only exact task identities that
were terminal and were removed by that reduction. Queue enumeration is lazy,
only when actual removal occurs. No schema/codec change, recovery heuristic,
prefix inference, increased history capacity or disabled reference barrier.
Per-reduction ordering also covers preemption followed by compaction in one
transaction, rather than judging terminality from the transaction's old state.

StrategicScheduleRetirementTest constructs the bounded128-objective history and
an outstanding pulse7: real objective review reproduces quarantine with the old
policy and succeeds with the owner hook. It checks unrelated pulse preservation,
explicit same-transaction cancellation and byte-identical canonical/WAL recovery;
negative missing-subject validation remains strict.5852 terminal0/22s:48 tests
(kernel24, strategic14, infection8, retirement2) passed. Additional injected WAL
failure assertion and NeoForge compile passed72074 terminal0/11s. Live unchanged;
this proves the reproduced mechanism, not recovery/deployment of the live179 world.

### Post-checkpoint integration baseline and fixture identity repair — 2026-09-23

Checkpoint8a626aca full domain run56760 finished in7m31s:887 tests,30 failures.
Retained XML: active checkout build/sa-domain-baseline-8a626aca-20260923/test-results/.
Do not treat the diagnostic deployment as complete integration acceptance.

Fixture planner now forwards the production registered schedule-retirement hook;
parity is asserted by StrategicScheduleRetirementTest. Route/front/assignment/scout
tests that assumed supply ordinal2 now admit the actual unique initial Northwatch
shipment and retain its exact identity thereafter. No production identity fallback
or ownership assertion was removed.54015 XML confirms6 focused passes;38618 passed
route transitions2, route repair2, patrol6 and architecture4 in29s;19846 passed
HOT scout4 and architecture4 but retained the autonomous supply-interception failure.

The latter now reads immutable canonical state every tick instead of serializing
and decoding the whole world10000 times. Acceptance and simulation cadence remain
unchanged. At10000 its diagnostic state has no operations/contracts/production jobs;
this narrows diagnosis to supply history/admission before blaming scout observation,
but does not prove that no shipment ever existed. Baseline assembly, logistics and
other supply consumers remain unresolved. No repeated full matrix or closure claim.

Autonomous interception follow-up:62860 retained a ten-point supply history.
Initial fixture food/reserve was78/78; production added64, but ordinary birth
consumed1 and raised reserve to80. Remaining export surplus61 cannot fund a64
shipment. Thus the missing sighting was a missing valid supply precondition, not
evidence of scout perception failure. The autonomous fixture now funds the three
birth-related rations at bootstrap, leaving birth schedules, actual production,
reserve constraints and interception assertions untouched.60113 terminal0/35s:
all15 HiveRouteEngagement tests and4 architecture checks pass. No production
economy policy, timeout or live server change. Other baseline failures remain open.

### Live field report and bounded diagnostic correction — 2026-09-23

User reported non-growing field near140,66,-2 on deployed8a626aca/new sa180 world.
Read-only snapshots correlate site7: epoch2/GROWING3 at31192/32832, then naturally
GROWING4 at33774. All12 sites were epoch2/stage3 at32832. No quarantine or retained
incident found; first harvest and bread successor are terminal. This contradicts
a canonical stall in this window, not a possible physical projection discrepancy.
Vanilla conditional RCON returned no text; it supplies no crop age evidence.

Settlement7 has SHORTAGE/64available/21hungry. Provision's normal review interval
is24000 ticks, so cached shortage may precede later bread availability. Actual
arrival/schedule correlation remains needed; no planner change or live mutation.
Other read-only discoveries: reference_container ignores target and uses settlement1;
performance queue samples retain completed-owner lags, not current backlog proof.

Lifecycle pressure falsely reported saturation for explicitly non-physical owners
with0 intents/0 quota. Diagnostic now reports OPEN for that declared category;
admission/schema validation unchanged. New regression covers all empty owner queues.
82462 terminal0/16s: lifecycle composition12, assembly9, HOT assembly2, architecture4.
Assembly bootstrap now shares the route-custody fixture's existing birth isolation
and selects its unique actual assembled owner, without per-tick world serialization.
Exact lease rejection, obstruction recovery and terminal travel assertions pass.
Native assembly scenario still requires current identity/roster alignment and native
verification; these model results do not close physical acceptance or whole SA scope.

Food diagnostic follow-up exposes retained cycleOrdinal/cycleStartedAt and exact
scheduled nextReviewAt/reviewOverdueTicks. Missing review remains null; it is not
invented from cadence or another settlement's schedule. Regression checks that
negative case.16442 terminal0/25s:39 diagnostic tests pass. Initial14802 found two
stale test assumptions (shipment ordinal2, bread terminal by2200); replaced with
actual unique shipment and ordinary completed-production route fixture, preserving
lineage/topology/read-only assertions. No deployment or economy behavior change.
The reference_container view was authored specifically for id=f02b; accepting
arbitrary container IDs without rejection is the confirmed scope-labelling gap.
### Exact diagnostic/report scope and assembly identity alignment — 2026-09-23

Fixed reference_container scope-labelling: only the authored f02b ID returns
the fixed composition; arbitrary container IDs yield not_found.82291 green9s
positive/negative/read-only regression; not a general-container implementation.
49165 green13s: actual produced64 bread is loaded into exact cargo; native assembly
bootstrap seed41 pins operation:supply-1-11 and residents1-30/1-16/1-28, no pending
COLD assembly action. The JSON scenario now uses that identity and3-member checks;
Node route identity/schema5 pass. Native route geometry and physical completion
remain unverified. No live update or whole-findings closure.
### Terminal logistics baseline fixture repair — 2026-09-23

TerminalLogisticsProcessTest pinned stale ordinal2 while its source route fixture
now produces shipment11. Aligned operation/contract/cargo/receipt references within
this isolated fixture.22696 terminal0/19s:4 terminal tests plus4 architecture tests
pass. Active operation cannot compact; terminal graph becomes bounded history;
confirmed loading/handoff receipts retire; snapshot recovery preserves compaction.
These tests construct terminal state directly. They do not prove physical loading,
arrival, crash windows or full cargo-retirement acceptance. Production unchanged.
### Domain delivery composition restored — 2026-09-23

34051 terminal0/44s, complete FrontierWorldRuntimeDefinitionTest passes. Supply
tests now start from ordinary produced/assembled route fixture and re-arm the
actual operationProgress schedule that the native fixture freezes for connection.
The physical-receiver fixture keeps strict validation and real observed custody.
No production transition or acceptance assertion changed. Scope includes COLD
arrival/exact64 cargo delivery, WAL flush-before-effect, trusted executor and
sequential handoff, foreign receiver rejection, UNKNOWN recovery blocks task,
HOT lease suspends COLD advancement. Domain evidence only: no Minecraft entity
save/crash acceptance or overall SA06/07 closure is inferred.
### Remaining baseline assembly consumers — 2026-09-23

90474 terminal0/49s: full FrontierWorldStateTest and FrontierV3FixtureCatalogTest,
plus architecture checks, pass. Removed the obsolete2550-tick/ordinal2 assumption
from two assembly tests, using actual fixture boundary/identity/time. HOT movement
now asserts unchanged actor state for all non-observed members, not just one guard.
Catalog successfully constructs every registered profile. Each of the30 original
56760 failures now has green affected-class follow-up; no fresh full integration
pass, physical retirement/crash closure or product-level acceptance is inferred.

### SA-06/07 actor adoption durability gap — 2026-09-23

Source tracing confirms both production callers (AmbientActorExecutor and
SceneExecutor) invoke AmbientCarrierLedger.adopt immediately after creating the
live entity. That method removes the inactive carrier and marks SavedData dirty;
there is no entity-save acknowledgement. An eager ledger flush before another
actor's release would therefore also publish these unrelated, unacknowledged
removals. Do not fix release ordering by blindly flushing this mixed ledger.

The atomic SavedData writer now uses a same-directory temporary file, file fsync,
atomic replacement and directory fsync; failures preserve dirty state and propagate.
78007 terminal0/6s: persistence2 + ambient departure5 tests pass. This protects
one file, not WAL/entity-region transaction atomicity. No eager flush is wired.

Added the explicit FrontierV3ActorAdoption value and strict versioned NBT codec.
It retains the exact predecessor carrier and admitted live declaration, validates
same actor/UUID/kind, a single next physical epoch and the correct declared
recipient-owner revision clock. The predecessor's authority revision and both
owner clocks remain separate. Missing/forged declaration dimensions fail closed.
79136 terminal0/7s:3 tests pass, including both recipient owners, independent clocks,
stale/skipped generations, wrong identities and missing/wrongly typed wire fields.
This is a foundation only: no caller or ledger persistence wiring yet, and it does
not fix current runtime adoption by itself. Diff-check clean; no deployment.

Remaining implementation must retain a bounded pending transfer durably before
body creation; distinguish it from an active inactive-carrier; reconcile an exact
return without granting duplicate custody; and retire it only on current successful
entity write + synchronization or an explicitly superseding physical transition.
Reuse lifecycle entity-write/save-pass hooks and EntitySaveBatch generation checks,
but do not let a late save acknowledge a superseded body/owner. Initial births,
ambient and scene reconstruction, same-body successor ownership, failed admission,
death, unload and restart all need the corresponding disposition. Historical
missing residents still require evidence-backed repair. These are SA-06/07 work,
not reasons to claim full closure from the farmer's two COLD cycles.

### SA-06/07 retained adoption and ordinary save acknowledgement — 2026-09-23

AmbientCarrierLedger.adopt now moves the exact carrier into a bounded serialized
pendingAdoptions inventory rather than deleting its recovery endpoints. It is
not inactive custody. Duplicate actor/UUID, concurrent carrier, combined inventory
overflow and malformed endpoints reject hydration. An authorized successor fence
may supersede only the same physical UUID/kind/epoch; a late acknowledgement cannot
erase that fence. Death retires this metadata under the existing canonical death
guard. Both absent-body creation paths refuse an unresolved adoption. Read-only
ambient diagnostics distinguish ADOPTION_SAVE_PENDING from missing history;
offline legacy-missing recovery explicitly refuses this new unresolved state.

FrontierV3ActorAdoptionPersistence now observes ordinary lifecycle entity writes
and completed save passes, using EntitySaveBatch and the provider synchronize
future. Exact stored NeoForge declaration, UUID and registered entity kind are
required. Write/sync failure, incomplete pass, superseding write generation,
duplicate candidate columns, changed current body and replaced ledger receipt
cannot acknowledge. Callbacks revalidate runtime/index identity on the server
thread. No entity mutation, chunk load or canonical command is performed here.
Pending metadata may remain after an unload or same-body ownership change until
a valid owner disposition; this is intentional retention, not a readiness claim.

61754 terminal0/15s: affected ledger/adoption/departure/offline recovery tests.
23428 terminal0/9s:22 tests (save observer5, save batch9, carrier ledger8).
30459 terminal0/11s:5 offline-plan tests including unresolved-adoption refusal.
45695 terminal0/7s:3 actual-file persistence tests, including saving a second
actor's fence while the first adoption remains recoverable on disk. Diff-check
clean. All evidence is component-level; no new native/client acceptance or deploy.

Still open: admission currently records adoption AFTER entity creation and does
not durably publish it before addFreshEntity; move that boundary before physical
admission with a safe failed-attempt disposition. Initial births are not yet
covered by this predecessor-based record. Durable release-before-WAL/body removal,
return/restart reconciliation, same-body owner transitions, historical population
repair and native world-copy acceptance remain required. Do not deploy this WIP
as a complete fix or close SA-06/07.

### SA-06/07 durable-before-admission and release ordering — 2026-09-23

Both reconstruction producers now call FrontierV3ActorAdoptionAdmission before
addFreshEntity. It records and atomically persists the exact transfer first;
publication failure never invokes the physical effect. A synchronous false add
result restores and persists the exact predecessor. An exception/unknown outcome
retains pending evidence, never assumes absence. Ambient bodies carry the final
physical epoch before join callbacks; the prior after-add restamping/discard path
is removed. Initial births still bypass this predecessor-only protocol.

AmbientCarrierLedger.persist binds the cached ledger instance and exact world's
standard dimension/data SavedData path. Loaded ambient release, unloaded ambient
release, loaded scene fencing and scene final-departure fencing now persist the
retained carrier before canonical release/body retirement. A repeated exact fence
also flushes a prior dirty interrupted write. This does not make Minecraft entity
removal and WAL atomic or resolve stale serialized returns by itself.

46907 terminal0/10s:17 tests (admission4 + save observer5 + ledger8), with actual
file reads inside the pre-effect boundary; intent publication failure, explicit
rejection, unknown effect and failed rollback publication retain the specified
evidence.37361 terminal0/46s:all8 native scene-departure/death tests pass. Loaded
ordinary/interrupted reservation tests now read the actual SavedData file before
shutdown and assert the carrier exists. All dimensions saved13:58:31. Evidence:
build/sa-ambient-world-copy.j4JKe3/durable-adoption-native.log. Prior GameTest world
preserved at frontier-v3-scene-game-test-before-durable-adoption-20260923. No live
change or product/crash acceptance claim.

Additional static seams still needing correction: abandonPreparedForReservation
has a direct reserved PREPARED caller bypassing abandonUndemandedPrepared's absence
gate; its body-present branch discards without the common physical release and
uses canonical rather than observed health. fenceObservedAbsentHarvestReturn still
uses epoch1 and local empty-column inference without a retained complete generation.
Do not let these older paths bypass the new common custody protocol. Initial-birth
retention, pending return/restart recovery, same-body owner changes, historical
population repair, native ingress and remaining SA-08/09 composition remain open.

### SA-06/07 PREPARED cancellation common boundary — 2026-09-23

Moved no-body evidence checks into abandonPreparedForReservation itself, so its
direct reserved caller cannot bypass them. Requires current canonical PREPARED
lease/head, no unindexed body or pending adoption, and the retained carrier or
existing first-admission observed-storage policy. The wrapper now delegates rather
than owning a stronger private gate. Any retained ledger is persisted before the
canonical cancellation. The first-admission policy is NOT yet durable birth history;
first-admission cancellation/next reconstruction still needs that protocol.

Body-present cancellation now delegates to the common release rather than discarding
the mob after a canonical-health release. Common release accepts PREPARED as well as
HOT/DRAINING and verifies the complete stamped ambient tuple and explicit positive
epoch. It persists the carrier and releases observed pose/health. No synthetic HOT
transition is inserted.92841 terminal0/10s:four focused policy tests + pilot compile.

50351 native terminal1:one new test failed because its pre-existing component
fixture relocates the body from remote GameTest coordinates to canonical unloaded
coordinates. Vanilla onMove stops tracking it, so the subsequent indexed lookup
could not exercise the intended body-present branch. Read Minecraft callback
bytecode; corrected test explicitly asserts absent index after this fixture move,
then exercises the shared PREPARED-body release boundary. Direct cancellation's
missing-history rejection is a separate native test and keeps canonical head
unchanged. No timeout or production behavior was changed to accommodate the test.
The helper now uses a local interior cell for initial admission rather than an
offset outside the small template.

36293 terminal0/43s:all10 required scene-departure/death tests pass; PREPARED body
release retains7HP and actual on-disk carrier before shutdown. All dimensions
saved14:05:36. Log build/sa-ambient-world-copy.j4JKe3/prepared-release-native-corrected-fixture.log;
failed log prepared-release-native.log preserved, as are prior/failure test worlds.
This proves the shared release and direct no-body refusal separately, not the
complete indexed PREPARED-body player flow. No live change; initial births,
incomplete save/return reconciliation, closed-harvest inferred epoch, historical
population and broader audit acceptance remain open.

### SA-06/07 no inferred return epoch; exact restart recognition — 2026-09-23

Removed fenceObservedAbsentClosedScene and the epoch1 synthesis in the harvest
return path. Renamed the caller to inspectClosedHarvestReturn: it is read-only,
requires an existing carrier and validates the declared next ambient revision,
identity and actual retained next epoch. Empty/missing history, pending transfer,
wrong owner, stale revision and invented epoch return local conflict. Positive
coverage uses retained epoch5 ->6, not a fixture that legitimizes epoch1 inference.
40224 terminal0/10s:20 ledger/adoption/admission/save-observer tests pass.
This prevents fabricated recovery; it does not restore historically missing bodies.

AmbientCarrierRecognition previously carried only UUID/actor/class/kind, omitting
owner, representation, authority revision and physical epoch. Its raw read-only
observation now carries every stamped dimension and requires current exact ambient
lease revision, LIVE_BODY/AMBIENT_LEASE and a positive explicitly typed epoch.
51844 terminal0/39s: recognition + full AmbientAdmissionPolicyTest pass, including
existing lifecycle/firewall/provider composition with explicit fixture declarations.

Added shared recoverableOwnership: canonical exact identity plus no inactive
carrier/unresolved departure/conflict; a pending adoption requires its exact admitted
tuple, not just positive epoch. Ordinary actor-loop UNKNOWN recovery, actual-entity
join recognition and pending post-projection recovery use this guard. The pending
bridge may retain an entity for inspection without granting HOT recovery, and the
guard never acknowledges entity persistence.76908 terminal0/10s:6 tests, including
stale/future revision, foreign/missing owner, wrong representation, zero/wrong epoch,
retained inactive custody and exact positive pending transfer. Pilot compiles;
new native restart/ingress evidence remains required. No live changes or closure.

Next work remains explicit first-body/birth history and interrupted transfer
reconciliation, including safe return of a body beside a pre-release fence. Merely
blocking competing execution is not autonomous recovery. Then repair historical
population on the retained world copy and perform the actual player/cycle/restart
acceptance. SA-08/09 physical production composition remains separately open.

### SA-06/07 returned body completes retained ambient release — 2026-09-23

UNKNOWN_AFTER_RESTART with an actual body now tries the shared release before
HOT recovery. Release accepts UNKNOWN only when its exact existing inactive fence
matches actor/UUID/kind/owner/revision/physical epoch and both owner clocks; it may
not manufacture that fence during recovery. It preserves observed health/pose,
uses ordinary UNKNOWN -> DRAINING -> CLOSED commands and retires the same body.
Missing/mismatched fence leaves canonical state unchanged; ordinary matching
unfenced bodies retain the separate guarded HOT-recovery path.

96276 compilePilotJava terminal0/8s.75155 native terminal0/46s:all11 required
scene-departure/death tests pass; all dimensions saved14:15:43. New component
test explicitly prepares UNKNOWN (not a real process crash), rejects missing fence
and another physical epoch without changing the checkpoint, then completes exact
retained release with7HP, durable on-disk carrier and subsequent cancellation.
Evidence build/sa-ambient-world-copy.j4JKe3/fenced-restart-native.log. Prior world
preserved as frontier-v3-scene-game-test-before-fenced-restart-20260923. No test
process remains from75155; no live changes, no full restart/product acceptance.

Still open: first-body history, missing-body pending adoption reconciliation,
same-body successor declaration updates, stale CLOSED-body handling, actual
crash/return composition and historical population restoration. Inspect the
existing-body materialize branch: its coarse owned() predicate alone does not
validate the current ambient revision; strengthen it with the common declaration
contract while implementing same-body handoff, not a new ad-hoc identity inference.

### SA-06/07 exact CLOSED retirement and identified scene-to-ambient defect — 2026-09-23

CLOSED ambient status alone no longer discards a returning body. New read-only
retainedClosedRelease requires exact canonical closed owner, stamped identity/
revision/epoch, no active scene or conflicting/pending custody and the matching
inactive fence. The caller persists it before discard. Missing proof retains the
body rather than destroying the only possible physical evidence. Runtime existing
and pending body admission now uses recoverableOwnership, not coarse owned() alone.
2018 terminal0/11s:15 recognition/adoption/admission/save-observer tests pass;
new positive/negative CLOSED policy test covers missing fence, active owner, another
epoch/owner and read-only proof retention. Production/pilot compile, diff-check clean.

Exact next repair identified in source: SceneExecutor.adoptRetainedClosedBodyForAmbient
removes scene LEASE_KEY/REVISION_KEY and writes actor/kind, but never stamps the
common ActorCarrierComposition owner/representation/revision. Its body remains
declared SCENE_LEASE, so ambient owned()/recognition rejects it. This predates the
new strict guard (the old inner materialize already called owned()). Do not call
this the sole historical farmer cause; it is one concrete same-body transition bug.

Implement a durable same-body ownership transfer, not just a late cosmetic retag.
Must preserve current physical epoch/UUID and explicit source+target declarations,
retain earlier possibly saved owner declarations until entity save acknowledges the
current one, support repeated handoffs before save with a declared bound, compose
with pending reconstruction, and reconcile old saved body after crash. Scene mark
and ambient return must use that common boundary. No live change or native claim
for these latest policy changes; first-body history and full SA closure remain open.

### SA-06/07 durable same-body handoff — 2026-09-23

ActorHandoff now retains a bounded chain of explicit LIVE declarations for one
actor/UUID/kind/physical epoch. Each owner's revision increases independently.
Ledger persistence precedes owner metadata changes; repeated handoffs retain prior
possibly saved declarations until an exact current-body write+sync acknowledgement.
Combined pending reconstruction is acknowledged only with the current handoff;
older acknowledgements cannot remove a newer handoff or inactive fence.
Scene mark and closed-harvest ambient return use this shared admission boundary.
Ambient return now actually stamps its common owner/revision, correcting the
concrete rejection identified above. Position, UUID and health are not rewritten.

14957 terminal0/7s:11 focused handoff/save-observer tests. Native97118 terminal0/47s:
all12 scene-departure/death tests passed; dimensions saved14:33:25. Evidence:
build/sa-ambient-world-copy.j4JKe3/live-handoff-native-corrected-fixture.log.
The new native component test reads the on-disk handoff before body retag, checks
unchanged UUID/pose/7HP, repeated ownership changes and idempotent old-tuple replay.
Earlier88848 failed because that test's metadata callback omitted ambient legacy
keys required by recognition; fixture corrected, no timeout relaxation. Its log
and world are retained, not represented as a production failure.

Limits: this is NOT the complete canonical harvest/next-job flow, process-crash
recovery or player acceptance. A dispatcher for an older saved declaration and
first-body history remain unresolved. No deployment or SA closure claimed.

### SA-06/07 closed scene recognition revision bypass — 2026-09-23

Source inspection found ownedByClosedLease ignored both common and legacy scene
revision, despite being used for same-body successor admission as well as cleanup.
SceneLease.withStatus and the other owner updates preserve revision; production
scene admission takes the canonical checkpoint revision. There is no demonstrated
need for the historical revision relaxation. Closed recognition now requires
CLOSED and the same exact declaration predicate as ordinary scene recognition
(without granting live-work permission). Added native component positives and
independent stale common/legacy revision negatives plus non-CLOSED negative.

80840 terminal1: the newly authored positive fixture omitted the separate legacy
custody epoch written by both production factories. Corrected its input, retained
failed log closed-revision-native.log and failed world. Other12 tests passed.
Follow-up92369 terminal0/45s: all13 tests passed, dimensions saved14:41:19.
Evidence closed-revision-native-corrected-fixture.log; diff-check clean.
This is a recognition boundary test, not complete farmer-cycle acceptance.

Coverage gap confirmed: postHarvestWorkLeaseMovesTheSameFarmerAwayFromTheFinalFieldStation
constructs a HOT ambient lease and a plain Villager, then drives pursuit. It does
not call the actual closed-scene adoption path and cannot establish that ownership
transfer works. Full canonical return/successor composition remains the next check.

### SA-06/07 ordinary return composition and historical suppression — 2026-09-23

Added native closedHarvestReturnsThroughOrdinaryAmbientMaterialization: explicit
closed-harvest/PREPARED-ambient component precondition, one actual indexed Villager,
then the production runtime-aware materialize path (not direct target stamping).
Checks same Java body/UUID/pose/7HP, current-owner recognition and idempotent
ordinary admission. 51067 terminal0/45s, all14 passed; log
build/sa-ambient-world-copy.j4JKe3/harvest-return-composition-native.log.
This constructs the initial closed state; it does not claim actual crop completion.

Following the returned body through the ordinary ambient tick exposed another
normal-flow defect: inspectClosedHarvestReturn classified ANY indexed body as
LIVE_BODY whenever a historical closed harvest existed. The tick unconditionally
continued on that result. Once return stamped AMBIENT, the dedicated closed-return
loop no longer selected it, while the generic loop still skipped it forever.
Changed the read-only gate: exact recoverable ambient owner -> NOT_RETAINED;
exact retained closed-scene body -> LIVE_BODY; any other indexed body -> CONFLICT.
No scene history deletion or physical/canonical mutation by the gate.
Extended native composition with pre/post handoff gate assertions and foreign
ambient revision negative. Follow-up73163 terminal0/48s, all14 tests passed;
harvest-return-history-gate-native.log, dimensions saved14:45:44. Diff-check clean.
No deployment; full cycle/restart and missing-body reconciliation remain open.

### SA-06/07 replay of recorded ambient handoff — 2026-09-23

ActorHandoffRecovery.resumeAmbient replays only a retained explicit source tuple
to the journal's exact AMBIENT target, validated against current canonical actor,
UUID/kind/revision and non-CLOSED ambient owner, no concurrent active scene or
indexed duplicate. Ledger transition checks reject fences/departure conflicts.
Persistence precedes legacy/common retag; physical pose/health/epoch are retained.
An already correctly stamped body is a no-op, avoiding per-tick fsync until save
acknowledges the handoff. Wired before source-join recognition and into the bounded
ordinary ambient actor probe, so old tuples can resume before UNKNOWN recovery.
Recovery does not require retaining/rediscovering the old closed scene by scanning.

Native return-composition fixture extended: rejects a forged unrecorded tuple,
restores the known old scene tuple, removes closed scene history in the fixture,
sets ambient UNKNOWN, then exercises production replay, recognition and no-op retry
with unchanged pose/7HP. This models an old entity image/new journal; it is not an
actual process crash. 38031 terminal0/50s: all14 passed,
ambient-handoff-replay-native.log. Diff-check clean; no task process running.
Scene-target replay, first-body history, absent-body recovery and product cycles
remain open; no complete recovery/SA closure or deployment claim.

### SA-06/07 explicit scene identity in physical handoff — 2026-09-23

ActorOwnerBinding combines the common body declaration with a mandatory typed
SceneLeaseId for SCENE_LEASE and forbids that field for AMBIENT_LEASE. Runtime
decoding consumes the entity's explicit scene ID and matching scene revision;
missing identity is rejected, never found by scanning canonical leases.
ActorHandoff now retains bindings, format2. Earlier undeployed format1 lacks the
identity and is explicitly unsupported, not heuristically migrated. Live build
has never received these pending-handoff formats; old native artifacts remain
historical evidence rather than inputs to current recovery.

Production scene admission supplies its canonical lease.id; fresh scene factories
write their explicit initial scene metadata before the shared mark boundary.
Scene/ambient transfer and ambient replay use complete bindings. Save observation
checks the exact saved scene ID/revision and the exact current loaded binding;
another scene with equal UUID/common revision cannot retire the recovery journal.
57678 terminal0/10s: prior11 focused tests + production/pilot compilation passed.
New negative coverage rejects missing/foreign scene ID, unsupported old format,
saved foreign scene and loaded foreign owner. 59175 terminal0/47s:12 focused
tests and all14 native scene-departure tests green; dimensions saved14:56:29,
explicit-owner-binding-native.log. Scene-target replay dispatch is still next.

### SA-06/07 scene-target handoff replay — 2026-09-23

Shared ActorHandoffRecovery.resume dispatches only on the recorded target owner.
SCENE recovery looks up the retained SceneLeaseId directly, validates its exact
revision/member UUID/living actor and absence of active ambient custody. CLOSED,
CONFLICT, missing and mismatched targets cannot authorize replay. The old observed
binding must be in the retained chain, and ledger conflicts/fences still reject.
Persistence precedes retag; position/health/UUID are unchanged. Already-current
metadata is a no-op; journal retirement still requires entity-save acknowledgement.
Wired at source join, bounded actor probe and existing-body scene materialization.
Restoring ownership does not transition a canonical UNKNOWN scene to HOT.

89168 focused terminal0:13 tests passed plus production/pilot compilation. New
policy test uses a real route-scene canonical fixture and covers PREPARED/HOT/
UNKNOWN, missing target, wrong ID/revision/owner and CONFLICT, without scene promotion.
New native fixture retains a pending ambient->scene transfer and an old indexed
body, then exercises shared replay against UNKNOWN canonical ownership and tests
ordinary scene recognition/no replacement/no teleport/no healing/no premature ack.
This is controlled recovery state, not actual process-crash or player acceptance.
39407 terminal0/49s: all15 native tests passed, dimensions saved15:00:45,
scene-handoff-replay-native.log. Diff-check clean, no deployment.
Next inspect source firewall when recorded handoff target is no longer admissible:
replay correctly refuses, but old recorded body still needs read-only retention
as evidence, without execution authority. Current recognition uses canonical
ambient/scene owners; the pending journal itself is not yet a retention proof.

### SA-06/07 source-join retention during refused replay — 2026-09-23

Added read-only retainsRecordedBody: exact complete observed binding must be in
the retained handoff chain, UUID must match the known canonical actor, and an
already indexed different Java entity excludes a duplicate. Source-join firewall
and managed-carrier recognition consume this evidence separately from live-owner
recognition. A missing/conflicted target can refuse replay without destroying the
old physical evidence. This does not grant work, alter canonical state, acknowledge
save or erase the journal. Unrecorded tuples remain rejected.

Native scene replay fixture now invokes actual observeSourceJoin plus the exact
SourceGrayboxEntityAdmission predicate before insertion: target absent, old tuple
retained, source stamp/journal unchanged; forged revision denied. After indexing,
another object with same UUID is not a retained body. 55739 terminal0/50s: all15
native tests passed, dimensions saved15:03:28. Evidence
build/sa-ambient-world-copy.j4JKe3/handoff-firewall-retention-native.log;
diff-check clean. No full crash/player acceptance or deployment claimed.

### SA-06/07 first-body admission: source-confirmed gap and implementation boundary — 2026-09-23

Static trace found two opposite policies sharing no first-materialization history:

- AmbientActorExecutor.preparedCancellationHasEvidence accepts revision1 plus an
  empty loaded local column without an inactive carrier. abandonPreparedForReservation
  then drains/releases it, retaining no proof that creation never occurred. The
  next lease is revision2, whose materialize path refuses NO_FENCED_CARRIER. Thus
  cancellation of an uncreated first lease can strand an otherwise valid actor.
- SceneExecutor.materializeBodiesInReadyColumns has no equivalent first-history
  check: NO_FENCED_CARRIER can select epoch1 and addFreshEntity. A locally absent
  body is not proof that this UUID never existed elsewhere or in saved storage.
- Both first-creation paths bypass ActorAdoptionAdmission (that receipt correctly
  requires a real predecessor). Reusing an invented inactive carrier/epoch0 would
  conceal the gap, not close it.
- Existing PreparedAmbientCancellationTest deliberately expects the first policy;
  it tests the branch, not subsequent re-admission. Do not cite it as lifecycle
  acceptance. No new runtime run was needed to establish these source branches.

Required implementation, still within shared actor custody SA-06/07:
1. Retain an explicit per-actor first-physical-admission history with complete
   actor/UUID/kind and typed owner binding. Distinguish an authoritative never-
   created permit, pending creation, and established physical history. An absent
   map entry in a recovered world means missing history, NOT a fresh permit.
   The existing physical ledger may own physical evidence, not a second roster.
2. Issue initial permits at the explicit fresh-world bootstrap boundary before
   physical ticks, and for dynamically born actors at their registered canonical
   birth/mobilization boundary. ServerLifecycle.startConfigured already has the
   actual RecoveryImage before runtime startup; do not infer freshness later from
   actor coordinates, current revision, absence of leases or a missing SavedData.
   Bind publication/recovery ordering to canonical initialization; a crash between
   these files must resume from retained evidence, not recreate permits.
3. Both ambient and scene producers persist pending creation BEFORE addFreshEntity.
   Explicit synchronous false may restore the unconsumed permit; exception/restart
   ambiguity retains pending evidence. Exact saved-body acknowledgement must not
   erase the distinction between never-created and previously materialized.
   Compose history with same-body handoff, inactive fencing, death and bounded
   retention. No-body PREPARED cancellation preserves an unconsumed permit for the
   next owner; cancellation cannot upgrade missing local history into one.

Acceptance must cover first cancellation -> new lease -> one actual body; first
scene and ambient admission; false/throwing creation; save and same-body handoff;
restart with pending creation and late body; no duplicate from local absence;
existing legacy world repair; dynamically born bioforms; bounded cleanup. Native
component success alone does not discharge the full world/player/restart gate.
Next implementation should start with the shared lifecycle and its issuance
boundary, not another revision-number exception. No first-birth fix claimed yet.

### SA-06/07 first-admission state model — 2026-09-23

Added ActorFirstAdmission with explicit identity, NEVER_CREATED/PENDING/ESTABLISHED
phases, complete OwnerBinding for an attempted first-generation LIVE body, and
strict versioned codec. Only exact synchronous no-creation evidence restores the
permit; saved history cannot return to NEVER_CREATED. Pending creation cannot be
started again. Same-revision foreign scene, other UUID/kind/epoch/representation,
missing fields and unknown phase are rejected. The model owns no canonical actor
state and its factory explicitly requires authoritative initialization; absence
in recovered data is not an issuance path.

71397 terminal0/8s:3 focused model tests passed, production/pilot compile and
diff-check clean. Evidence build/sa-ambient-world-copy.j4JKe3/first-admission-model.log.
This is the implemented state model ONLY: not yet wired to the physical ledger,
bootstrap/dynamic actor issuance, pre-add boundary, save acknowledgement, handoff,
fence/death or cancellation. Existing production first-admission behavior is not
fixed by this model alone. Next integrate those owners, preserving explicit
legacy-world missing-history classification. No native rerun or live deployment.

### SA-06/07 first-admission ledger and pre-effect boundary — 2026-09-23

Physical ledger now retains firstAdmissions (separately bounded4096 lifetime
markers; pending first admissions also consume the existing4096 transfer budget).
Missing inventory in older format5 stays missing. Explicit registration accepts
only a never-created permit without contradictory custody, duplicate UUID or
overflow; it cannot overwrite pending/established history. Exact synchronous
rejection restores the permit; save settles rather than removes history. Handoff
save and exact inactive fencing settle pending first generation; late first-save
or rejection cannot erase the successor fence. Existing death-retirement boundary
removes the marker only with canonical death and no current recovery binding.
Hydration validates phase/current-custody agreement and cross-inventory identity.

ActorFirstAdmissionBoundary orders begin -> persist -> addFreshEntity. Explicit
false restores/persists permit, while persistence/provider exceptions retain the
pending attempt and prohibit another creation. It requires a pre-issued permit;
it does not grant one based on absence or caller's desired revision.

49137 terminal0/10s (ledger integration);59628 terminal0/8s:23 focused tests passed
(first model3, first ledger/boundary6, handoff6, prior carrier ledger8), production/
pilot compilation and diff-check clean. Evidence first-admission-ledger.log and
first-admission-boundary.log in build/sa-ambient-world-copy.j4JKe3.
Tests include roundtrip, missing old history, idempotent issuance, save permanence,
false/throwing/failed-persistence ordering, next-lease retry, late acknowledgements,
handoff save and fence -> next-generation reconstruction.

Still NOT activated in ordinary first-body producers: explicit bootstrap/dynamic
birth issuance, save-observer candidate wiring, cancellation and old-body recovery
must be composed before switching ambient and scene factories. Existing runtime
first-admission defect therefore remains open. No native run/live change here.

### SA-06/07 first save observer and bootstrap issuer — 2026-09-23

Ordinary ActorAdoptionPersistence now selects pending first admissions alongside
reconstruction/handoff receipts. A handoff supersedes the first candidate. Exact
serialized owner binding, current loaded binding and successful write+sync are
required; acknowledgment establishes retained history, never deletes it. Failed
write or changed loaded owner leaves pending evidence.25617 terminal0/8s focused
save/model/handoff checks passed (first-admission-save-observer.log).

Added ActorFirstAdmissionBootstrap issuer for the explicit empty RecoveryImage
branch used by canonical fresh creation. It consumes declared resident and bioform
rosters, validates exact coverage of actorLocations, publishes all permits before
runtime effects, and allows only identical still-unused initialization retries.
Any snapshot/WAL means recovered state: it cannot manufacture fresh permits for
a missing ledger. Used physical history contradicts fresh bootstrap and refuses.
The helper is deliberately NOT activated in ServerLifecycle yet: activation must
be the same coherent change as switching both ordinary producers. Otherwise old
untracked creation would leave NEVER_CREATED markers and subsequently fail fences.

34128 compile failure was a wrong test import for FrontierWorldRuntimeDefinition;
corrected to its runtime package.52285 terminal0/7s:19 focused tests passed,
production/pilot compile and diff-check clean. Evidence
first-admission-bootstrap-corrected.log (same evidence root). No native rerun.
Remaining: lifecycle activation + ambient/scene producers/cancellation as one
change, dynamic birth issuance, pending-first returned-body recovery/retention,
legacy-world recovery and full product/crash verification. No live changes.

### SA-06/07 activation of first-body admission and cancellation repair — 2026-09-23

ServerLifecycle now issues/persists initial permits before starting a genuinely
fresh canonical runtime. Runtime-aware ambient and scene first-body producers use
FirstAdmissionBoundary; missing/used history is not a new creation authorization.
Ambient creation no longer rejects revision2 solely because it lacks an inactive
carrier: an exact still-unused permit authorizes its FIRST body at epoch1.
No-body PREPARED cancellation requires that permit or a real inactive predecessor,
not revision1/local-column absence. It preserves the unused permit through close.
Ambient recognition rejects a NEVER_CREATED marker paired with a purported body,
and pending first-body declarations are retained at source join as evidence.

23424 terminal0/11s:23 focused tests and production/pilot compilation passed.
78486 terminal0/50s:all16 scene-departure/death native tests passed, dimensions
saved15:25:24. New cancelledNeverCreatedLeaseStillAdmitsOneBodyOnNextRevision uses
ordinary prepare/cancel/prepare commands, then the production runtime-aware
materialization boundary, sees one indexed Villager and reuses it on retry.
The physical coordinate is a GameTest-local fixture (not world/player acceptance).
Evidence first-admission-activation-focused.log and first-admission-activation-native.log
under build/sa-ambient-world-copy.j4JKe3. No live deployment.

Still open: explicit dynamic resident/bioform birth issuance; first pending-body
recovery and missing-body reconciliation; legacy-world repair; direct scene-first
and complete player/restart composition. Inspect scene existing-body ownership
against NEVER_CREATED/other pending first owner: ambient guard was updated, scene
recognition still needs equivalent composition. Do not claim full first-admission
or SA closure from this cancellation regression.

### Follow-up: existing scene ownership and legacy repair isolation

SceneExecutor existing-body recognition now checks the exact first-admission
OwnerBinding, including SceneLeaseId, through permitsFirstAdmissionOwner.
NEVER_CREATED cannot authorize an existing body; PENDING cannot authorize another
scene with an otherwise identical declaration. Legacy missing history remains
recognizable, never a new-body creation permit. Focused scene-first-owner-gate.log:
13 tests passed, BUILD SUCCESSFUL10s, production/pilot compilation passed.

OfflineActorRecoveryPlan now rejects any first-admission record at both planning
and application. This tool repairs legacy missing custody; it must not reclassify
unused, pending or established creation history as legacy absence. New regression
checks all three phases and an already-prepared plan against unchanged canonical
revision/state and unchanged ledger. Publication invokes this planner on original
data and rejects external ledger divergence before applying commands.
Verification24498 terminal0: offline-first-history-guard.log, BUILD SUCCESSFUL10s;
6 plan tests plus1 publication interruption test passed. No native/full-cycle
acceptance claimed from this pilot-tool guard.

Dynamic issuance source trace: HumanPopulationStateSupport.completeBirth installs
the resident/ActorLocation and consumes the birth job; HiveGrowthStateSupport.complete
does the analogous bioform transition. ResidentBorn contains a resident tuple;
HiveGrowthCompleted contains only jobId. FrontierStoreTransactionCommitter appends
WAL before canonical installation, and runtime checkpoint compacts WAL. Therefore
an after-tick roster diff, missing-ledger fallback or best-effort event listener is
not a crash-safe birth authorization. Remaining implementation must retain an
explicit typed birth authorization at the canonical transition, recoverable after
checkpoint, and compose its consumption with adapter admission. No dynamic-birth
or full-cycle closure claimed; live deployment unchanged.

### Dynamic birth: explicit event identity and pre-WAL permission publication

Implemented an ordered write-ahead participant rather than an after-commit outbox
listener. ActorBirthIdentity carries actor ID and stable kind tag explicitly from
PopulationBirthProcess/HiveGrowthProcess. ResidentBorn validates its resident tuple;
HiveGrowthCompleted retains the newborn actor independently of its job, and the
reducer validates it against that exact active job. Both codecs use a required
versioned identity prefix and reject the earlier payload format (no migration or
inference). Both events now require DURABLE_BEFORE_EFFECT.

ServerLifecycle injects ActorBirthCommitter into the runtime's existing committer
boundary: persist unused first-body permissions, append canonical birth WAL, then
allow engine state installation. Missing ledger/history never issues a birth.
Failed permission persistence prevents WAL publication. Failed WAL retains only
unused permission; without canonical actor admission it cannot materialize a body.
The identical unused permission can retry; PENDING/ESTABLISHED cannot reset.
No changes to canonical economy or simulated time are used to repair this seam.

78479 terminal0, BUILD SUCCESSFUL14s: birth-before-wal.log under the same evidence
root. 27 focused tests passed: identity codec2, HumanPopulationProcess9,
HiveNutrientTransferProcess10, ActorBirthCommitter4, StoreTransactionCommitter2.
Production/test/pilot compilation passed. Earlier89135 declaration check green17s.
This tests explicit declarations/negative codecs, write ordering and injected
failure behavior, not a physical newborn or actual process crash.

Remaining: full canonical-runtime birth/checkpoint/restart plus physical admission;
safe bounded disposition of unused permissions left by interrupted uncommitted
births; pending first-body absence reconciliation; exact scene adoption binding;
legacy restoration and complete multi-cycle player acceptance. Live unchanged.

### Scheduled resident birth through real WAL and checkpoint recovery

ActorBirthRuntimeTest constructs stocked canonical preconditions, then uses the
registered birth review/completion schedules and the same host committer composition.
Checks: no permission at conception; exact resident and63 remaining bread after
birth; SavedData permission on disk with exact deterministic UUID/kind; real
FrontierFileStore WAL replay does not reissue; checkpoint compaction empties WAL;
snapshot recovery retains canonical birth while PENDING first-admission history
on disk prevents another creation attempt. The admission effect is deliberately
a unit callback, NOT a Minecraft entity or physical acceptance assertion.

18057 terminal0/5s, birth-runtime-recovery-versioned-fixture.log:1 integration
test passed. Initial55635 and diagnostic10834 failed on Game version not set from
SavedData NBT encoding: standalone fixture lacked SharedConstants.tryDetectVersion,
matching existing persistence-test initialization. Fixed fixture only; no retry
timeout, weakened assertion, simulated successful persistence or product fix.
All three logs retained. This is file recovery in one test process, not abrupt
process termination. Bioform runtime/physical birth and orphan permission handling
remain open alongside pending-body reconciliation and exact scene adoption.

### Exact scene owner retained across inactive-to-live adoption

ActorAdoption now retains ActorOwnerBinding instead of the common declaration
alone. FORMAT2 requires the exact scene ID; prior FORMAT1 is rejected, not completed
from current canonical state. Ambient and scene producers supply their real owner
binding to the shared durable-before-add boundary. Save candidates validate both
the serialized owner and the currently loaded owner before retiring the receipt.
Handoff preparation, acknowledgement and ledger hydration compose exact bindings,
not just coincident actor UUID/revision. Scene execution also checks retained
adoption/handoff/first history through permitsRecordedOwner; an existing body with
a different pending scene cannot bypass recovery by presenting matching common tags.

Regression explicitly substitutes a different scene at the SAME actor UUID and
revision: saved scene mismatch, loaded scene mismatch, adoption acknowledgement,
execution ownership and handoff source are rejected; the exact pair succeeds.
Codec regression rejects missing scene identity and the old format. Test-only
ActorAdoptionFixture supplies synthetic scene bindings; no fixture resolver was
added to production and no production owner is inferred from revision or prefixes.

42951 terminal0/11s:56 focused tests passed, production/test/pilot compile passed;
adoption-exact-owner-execution.log. Earlier22360 green9s preceded the execution
gate extension;6482 compile failed on one missed test caller signature, fixed.
Logs retained under build/sa-ambient-world-copy.j4JKe3. Latest source not deployed.
Full native reconstruction/save/restart composition remains unaccepted; pending
first-body absence and unused birth-permission disposition still open. No SA closure.

Native follow-up76759 terminal0/51s, exact-adoption-handoff-native.log:all16
scene-departure/death tests passed, dimensions saved15:51:52 and process stopped.
liveHandoffPersistsBeforeStampAndDoesNotMoveOrReplaceBody now starts a real Villager
through ActorAdoptionAdmission from an explicit inactive fixture. Its insertion
callback reads disk and verifies the exact admitted SceneLeaseId BEFORE adding the
entity. Subsequent two same-body handoffs and recorded-source replay retain UUID,
pose and7HP. This covers native entity insertion + composed adoption/handoff, not
a naturally completed prior release, real process crash or player-visible cycles.
Previous terminal run preserved under before-exact-adoption-20260923; live untouched.

### Pending-body evidence retention and diagnostic parity

Source trace found two omissions after first/adoption activation. Source-join
retention covered first creation and handoff, but not the exact pending adoption
itself when the canonical target is unavailable. Added its full binding to the
read-only retention predicate; this does not promote the body or assign new work.
The native same-body test verifies exact adoption retention without a canonical
scene and rejects a changed scene ID with identical common declaration.

Ambient admission diagnostics still used revision>1 as CARRIER_MISSING despite an
unused first permit allowing creation at that revision. Diagnostics now use that
same explicit unused-permit check, and distinguish FIRST_CREATION_PENDING and
HANDOFF_SAVE_PENDING from absent history. Pure regression proves explanation does
not mutate history; native cancellation/re-admission checks no false missing status.
No absence observation is upgraded into permission by these changes.

33380 terminal0/13s:2 focused tests and production/pilot compilation passed,
pending-history-recognition.log.18076 terminal0/50s:all16 scene-departure/death
native tests passed, dimensions saved15:55:58; pending-history-retention-native.log.
Prior world preserved under before-pending-history-retention-20260923. Live unchanged.
Still not complete pending-first-creation recovery, actual crash, bioform physical
birth or full player lifecycle acceptance. No SA closure from these regressions.

### Dynamic bioform birth through registered runtime and file WAL

Extended ActorBirthRuntimeTest with a canonical stocked-hive/task precondition.
Registered growth start/completion schedules consume the exact fungible biomass,
complete the strategic task, retire the growth job and admit its exact bioform.
First permission is absent while growing; after completion SavedData retains the
producer-declared BIOFORM kind and exact deterministic UUID. A forged newborn ID
on the same job is rejected. File WAL recovery restores identical canonical state
without reissuing permission or looking up the already-retired job. The existing
resident WAL/checkpoint/PENDING recovery case remains green.

38165 terminal0/7s:2 tests passed, bioform-birth-runtime-recovery.log. No changed
production code in this step and no redundant native matrix. Evidence covers the
actual canonical birth and filesystem recovery boundary, not Minecraft bioform
insertion, abrupt OS crash, orphan cleanup or player acceptance. Live untouched.

### First pending body returns after component recovery

Added native pendingFirstBodyRejoinsRecoveredRuntimeWithoutAnotherCreation:
ordinary PREPARED command and runtime-aware first materialization create a real
Villager with a persisted PENDING first record. The fixture serializes entity NBT,
forgets transient pending caches, reloads canonical runtime from actual file WAL,
reloads SavedData from disk and replaces the test world's cached ledger. It removes
the fixture entity; an ordinary materialization attempt while absent must CONFLICT,
never mint another body. It then loads the saved NBT into the returning entity,
verifies exact pending retention before insertion, inserts/indexes it, and checks
the normal materializer returns CURRENT with unchanged UUID, pose and7HP. The
pending marker remains unchanged: recognition does not fabricate save acknowledgement.

86714 compilation green5s;27800 native terminal0/51s:all17 scene-departure/death
tests passed, dimensions saved16:02:48. first-body-rejoin-compile.log and
first-body-rejoin-native.log. Test canonical store is a task-private temporary
directory under configured java.io.tmpdir. This is component recovery inside one
JVM with explicit entity NBT reload, not an OS crash/entity-region atomicity proof,
nor a crop cycle. Previous terminal world preserved before-first-body-rejoin-20260923.
Live untouched. Permanent absence resolution/orphan cleanup and full product gate
remain open; this verifies the exact late-body branch, not every pending outcome.

### Bounded cleanup of uncommitted birth permissions

ActorBirthRecovery is wired after successful full canonical recovery and before
runtime publication/physical startup. It traverses only the bounded permission
journal and retires an exact NEVER_CREATED record only if its actor is absent from
recovered canonical state, its deterministic UUID belongs to that world, and no
other custody record contradicts it. This is deletion of known UNUSED permission,
not inference of a birth or physical absence. PENDING/ESTABLISHED are retained even
when canonical actor is absent; stale expected records and foreign world UUIDs
cannot delete them. All changes persist before startup continues; failed writes
propagate and old disk images can repeat cleanup safely.

Real-file integration injects a failure after permission SavedData was written
but before the birth WAL append. Recovery restores the pending canonical birth
job with no newborn; cleanup durably removes the orphan, and the ordinary
completion schedule reissues exactly one permission and admits its resident.
Initial32520 green11s:10 focused tests+pilot compile.99220 green8s:7 runtime/recovery
tests including the injected failed-publication retry. Logs unpublished-birth-
cleanup.log and unpublished-birth-runtime-retry.log. No unchanged native rerun.
Unused orphan handling now implemented/covered; permanent PENDING absence,
OS-crash/region atomicity and complete player acceptance remain open. Live unchanged.

### SA-08/09: expose existing ordinary COLD fungible worker fixture to native runner

The Java provider fungibleProductionWorkConfiguration already existed but was not
listed in the common pilot profile properties. Registered fungible-production-work
as disposable_lite, production ruleset, production-cold-fungible-labor-boundary,
with the production terminal assertion contract. It is test-only, not a production
profile or injected job/output. Focused catalog regression loads by registered ID
and confirms17 ordinary COLD labor units, retained input lot, no output lot, no HOT
scene and a retained continuation.79261 green7s, fungible-profile-admission-corrected.log;
94063 earlier compile failed on two new-test accessor typos, corrected.

Node scenario validator also recognizes the common profile. This is registry
compatibility only: the existing exact midwork scenario's IDs/output assertions
do NOT become fungible evidence by switching profile. Next: declare actual
fungible job/lot/order/worker observations and native COLD-start ingress + mid-work
recovery, without replaying already accepted exact-item checks for confidence.
No runtime launch or live change in this slice; SA08/09 remain open.

### SA-09: unclaimed admission matches the resource owner (2026-09-24)

Static review found `ProductionResourceCustody.canStart` accepted a lot with
64 wheat even when another retained claim reserved all 64. The scheduler could
publish `ProductionStarted`, but `FungibleResourceLedger.reserveBound` would
reject the same job during reduction. The ledger now exposes one owner/kind
unclaimed-balance calculation used by both admission and reservation; no
second stock ledger or physical inference was added. A focused bound-depot
regression reserves all wheat for another claimant and proves ordinary
`planStart` retains the pending task and next review rather than issuing an
unreservable job. BoundProductionAdmissionTest and FungibleResourceLedgerTest
pass in 11s. This is an admission-consistency repair, not SA09 closure.

The declared disposable fungible-midwork scenario was tightened to inspect
the COLD job before the player's ordinary visit, then inspect the same worker
under bound input after its graceful restart. Scenario validation passes.
One isolated client attempt failed before client readiness because the local
visible path still demanded disabled DISPLAY=:0; a private-display retry
failed because task Xvfb :98 lost XKB initialization. Both isolated servers
saved/stopped and failure bundles were preserved under
`build/frontier-v3-scenarios/disposable-fungible-production-midwork-restart-1790192032161.failure`
and `...-1790192157671.failure`. This is test-environment evidence, not a
production behavior failure or native acceptance. The later :102 run below
provides the native verdict for this one-lot carrier. SA09 remains OPEN.

Follow-up source review identified another representation-dependent admission:
`firstAtContainer` requires one lot of at least64, while the recipe ledger's
`transformCold`/`transformObserved` already accept an exact input-lot map.
Two 32-wheat lots of the same owner therefore fail production despite the
same usable total as one 64-wheat lot. `ProductionInputHold`, the job's input
identity, physical intent/observation and output lineage all still assume one
lot; accepting only the sum in the planner would produce an unexecutable job.
The repair must adopt a bounded multi-lot allocation through those owners as
one versioned transition. This is a source-confirmed SA09/product gap, not a
failed test or an implemented multi-lot claim.

The native carrier's third attempt on private display :99 also stopped before
client readiness; its preserved bundle is
`build/frontier-v3-scenarios/disposable-fungible-production-midwork-restart-1790192716560.failure`.
Bounded Xvfb tracing isolated the environmental fault to `/tmp` user quota:
`xkbcomp` receives `EDQUOT` writing `/tmp/server-101.xkm` although global
`df` reports free bytes. The old unreferenced F0.VC Gradle distribution at
`/tmp/pm-f0vc-r5-gradle` was moved intact to
`pale-mirror/build/pm-f0vc-r5-gradle-preserved-20260924` (~192 MiB) to free
task-owned quota; no source evidence or live world was removed. A fresh Xvfb
:102 passed `xdpyinfo` and then one isolated native client run. Manifest
`build/frontier-v3-scenarios/disposable-fungible-production-midwork-restart-1790193019042.json`
reports `status=ok` (run `793f1bd8-cc4a-43f4-8483-6868accc710a`), persistent
client JVM reused and exact task server port25675 closed. Before ordinary
player ingress, job `job:production-1-1` with worker `resident:1-15` was
COLD/PROCESSING at28/80, holding one wheat lot. Ingress produced a HOT scene;
the saved graybox frame visibly includes the industrial worker. After graceful
restart, the same job/worker were PROCESSING at37/80 with bound input. The
immediate lease result was `UNKNOWN_AFTER_RESTART`, not proof of instant HOT
reacquisition. Subsequent diagnostics observed one output lot of64 bread and
the job gone; the container was current. No duplicate job/output was observed
by these assertions. The output wait took61.75s. Xvfb :102 was stopped after
frame review. This is a positive one-lot COLD-to-HOT/restart/completion
receipt, not SA09 or full player-experience closure: multi-lot composition,
multiple cycles and full-pack visual behavior remain unproven.

SA09 follow-through: a proposed foreign-owner selection repair proved
unnecessary and was removed. `ExactInventory.validateFungibleResourceCustody`
rejects a depot account containing a lot owned by another settlement and
rejects two accounts at one resource location; the rejected fixture was not a
valid product state. A new focused ledger regression constructs two legitimate
32-wheat lots with different provenance under the same account, reserves one
64-unit claim, transforms both atomically into one bread lot, preserves both
lineage IDs and rejects an incomplete lot map. `FungibleResourceLedgerTest` and
`BoundProductionAdmissionTest` pass together in9s. This proves only the ledger
primitive. Production still selects a single64-unit lot; `ClaimAllocation`
reserves owner/kind quantity without pinning the lot map, and the job/hold,
intent, physical layout, receipt, output lineage and persistence remain
single-input. Multi-lot SA09 acceptance is still OPEN.

### SA09 source checkpoint, 2026-09-24: exact multi-lot admission and retirement

The prior paragraph records the pre-cutover state. Current uncommitted source
now retains a bounded input-lot map through `ClaimAllocation`, production
hold/job, snapshot/WAL, physical observation, terminal receipt and output
lineage. `ProductionProcess` admits two 32-unit wheat lots as one 64-unit job
in both COLD and acquired HOT custody. The shared physical binding allocator
places pinned claim portions against their named lots, including when a new
pin requires repositioning an older generic claim column. Strategic objective
review now recognizes the same multi-lot wheat candidate. A focused test
exposed a real late rejection: production retirement counted exactly three
relations, so a two-input job failed after its physical receipt. Retirement
now requires `2 + input lot count` declared relations. Focused tests pass for
COLD/HOT admission, bound release, snapshot recovery, a confirmed two-lot
physical transformation, both input retirements, output lineage and terminal
receipt. World snapshot schema is 184; the live schema-180 world is unchanged.
The seven selected Frontier suites and NeoForge production/test compilation
pass at this checkpoint.

This is not SA09 closure. Multi-claim theft/forfeiture, variable field-yield
composition, broader restart paths and a player-visible two-lot native story
are unproved. No live deploy, client acceptance, commit or push occurred at
this checkpoint.

Provisioning follow-through: source inspection found a distinct claim leak.
`SettlementProvisionResolved` terminal conflict/shortage dropped remaining
ration allocations without releasing their pinned fungible claims, so the
canonical depot could retain unavailable bread after the cycle ended. The
reducer now releases every unspent allocation claim in the same state
transition, without consuming any bread. Focused two-lot/two-claim COLD and
bound conflicts, snapshot roundtrip and forged-claimant rejection pass with
the existing provision suite. This does not
solve an observed HOT departure touching multiple live claims: the current
forfeiture owner still handles only one production/hive claimant and discovers
that owner through collection membership. Under the no-inferred-owner rule,
that path requires an explicit typed claim-owner declaration and complete
retirement/recovery handling for provision and supply as well as production/
hive. Do not treat claim cleanup as physical handoff acceptance.

### SA09 claim-owner boundary, 2026-09-24: source checkpoint, not closure

`ClaimAllocation` now persists a closed `ClaimPurpose` alongside its exact
claimant ID and pinned lot map. All five live claim producers declare their
purpose; partial generic reservation copies retain it. Snapshot schema 185
encodes an explicit stable wire tag. The pre-hydration test rejects schemas
177–184 without rewriting input, and current snapshot/claim and multi-lot
codec checks pass. The live schema-180 world has not changed.
`ClaimPurposeCodecTest` additionally proves that otherwise identical retained
claims differ by exactly the declared wire byte, both meanings survive decode,
and an unknown purpose byte is rejected.

`FungibleClaimForfeitureStateSupport` dispatches only from the declared
purpose, then verifies the production/hive job, economic owner, kind,
quantity and exact lot map. It no longer treats accidental claimant membership
in a collection as ownership. Ration planning and consumption now share
the same exact owner/purpose/account/lot validation; a forged supply-purpose
claim on the active ration is rejected before a consumption event is proposed.
Focused provision and physical-observation tests pass, as do NeoForge main
and test compilation. `git diff --check` is clean at this checkpoint.

This is a defensive source correction, not a multi-owner physical handoff
implementation. The forfeiture path still rejects a handoff touching more
than one claim and has no declared retirement for ration or supply claims.
Source tracing shows why these are not interchangeable: ration claims belong
to the current provision cycle and must release its other unspent allocations;
supply claims travel with a cargo account through delivery/world-carrier
custody. The physical observer currently asks the forfeiture owner to accept
the whole affected set before emitting the handoff; unsupported sets leave
the physical change unresolved rather than silently clearing a claim. Widening
this requires complete owner-specific transitions and recovery, not just
removing the one-claim guard.
`EXTERNAL_RESERVATION` is still usable in primitive/synthetic ledger tests,
but has no world retirement transition; it must not be treated as a product
claim owner. SA09, variable field-yield and full client/product acceptance
remain OPEN. No native run, live deploy, commit or push in this slice.

### SA09 follow-through, 2026-09-24: multi-claim ration departure

The source now admits one observed HOT bread stack carrying multiple current
`SETTLEMENT_RATION` claims. It recomputes the complete affected-claim set from
the source account and moved lot/claim columns; omission or a foreign claim
fails closed. The ration owner verifies every affected claim against the
active provision and verifies the remaining unspent cycle before allowing a
physical handoff. A shared `SettlementProvisionStateSupport.reduceResolved`
performs the same terminal claim/nutrition transition used by ordinary
`SettlementProvisionResolved`; the handoff then strips every released claim
column from its physical evidence and transfers the observed stock in one
immutable reduction. Bread quantity and lineage are preserved, the provision
is `CONFLICT`, and replay rejects. Tests cover the direct owner, snapshot
roundtrip and registered physical-observation process path. Existing
production/hive handoff, ledger and production lifecycle tests pass;
NeoForge production/test compilation passes. One fixture-count assertion
initially expected exactly two claims but actual planning made three; the
assertion was corrected to the real multi-claim invariant and passed.

Still open: this physical handoff event is currently declared ordinary in the
diagnostic producer contract, so its internal provision conflict has no
separate retained incident tuple. The next source boundary must explicitly
stamp and validate the owner's diagnostic in the persisted event (not infer
one from a resulting status). `SUPPLY_CONTRACT` has no retirement/recovery
transition, and mixed owner kinds remain refused. No native client or live
deployment claim follows from these model/process checks. SA09 stays OPEN.

### SA09 follow-through, 2026-09-24: owner-stamped physical ration incident

The prior paragraph records the intermediate diagnostic gap. The persisted
`FungibleResourceHandoffObserved` now carries an optional nominal retirement
diagnostic. For a ration forfeiture, the provision owner stamps exactly
`SETTLEMENT_PROVISION_CONFLICT`; an unstamped or foreign-owner tuple rejects
before reduction. The physical observer stamps the owner result before
submitting its event. The closed diagnostic producer contract admits that
conditional tuple, and the ordinary event reducer retains it with event/cause
coordinates in `DiagnosticIncidentIndex`. Focused tests pass for payload
roundtrip, rejection of missing/foreign tuples, a player-save-fenced event,
engine submission and incident lookup by provision subject. Snapshot schema
is now 186; the live schema-180 world remains unchanged and old schemas
177–185 reject before hydration. NeoForge main/test compilation passes.

The persistence envelope is now 71 as well. An old envelope-70 snapshot or
WAL is rejected before any payload replay; focused tests exercise both
rejection paths and current-envelope decoding. This is required because the
handoff event's persisted diagnostic changes its WAL representation.

This is a registered event/incident proof, not proof that Minecraft has saved
the player's inventory or that the physical adapter observed this case in a
native client. `SUPPLY_CONTRACT`, mixed owner-kind retirement and the wider
SA09 field-yield/product paths remain OPEN. No native run, deploy, commit or
push occurred.

### SA09/SA06 source follow-through: supply claim retirement boundary

The source trace narrows the apparent supply handoff gap. A lawful fungible
shipment is held COLD in cargo; when its cart is released to ordinary physical
custody, `CargoCarrierReleased` interrupts the operation and contract and
releases the shipment claim before player/drop/hopper observation. Delivery
also discharges the cargo claim. The direct handoff owner therefore continues
to reject an unreleased `SUPPLY_CONTRACT` claim instead of inventing an
independent route cancellation.

The release reducer previously cleared every claim found on the new world
carrier. It now requires the sole claim to have `SUPPLY_CONTRACT` purpose and
to match the exact contract, settlement, item kind/count and carrier lot map;
an exact-cargo release refuses stray fungible claims. The existing cargo
release suite passes; a forged-purpose claim is rejected while the lawful
interruption still succeeds. This is a source-level owner guard, not proof of
all logistics failure/recovery modes or native player pickup. SA06/09 remain
OPEN; no deploy/commit/push.

### Field physical-witness identity and static cutover check — 2026-09-24

Source review found that the proposed replacement physical witness verified
its retained layout revision, geometry fingerprint and cell count, but not the
actual recovered cell-ID set. A same-count corrupted SavedData payload could
pass `matchesLayout` and fail later in an individual cell lookup. The layout
and witness now cache/compare a sorted cell-ID SHA-256 fingerprint. A focused
same-count ID substitution regression and the targeted layout/witness tests
pass (18s); no schema or live-world change follows because this witness is
not yet wired as the runtime claim.

The current runtime remains incompatible with heterogeneous field outcomes:
the physical site ledger is uniform-stage/harvest-prefix format7, player
break-start still conflicts the whole site, COLD terminal admission is fixed
64, and the reducer requires 64 yielded cells before creating one exact
64-wheat stack. These are source-confirmed competing-authority points, not
questions to resolve by another client rerun. The field lifecycle document
records the coherent owner cutover; no partial-yield, damaged-cell, arbitrary
geometry, SA09 or whole-product closure is claimed. No native run, deploy,
commit or push.

### Field yield as an exact cell-derived value — 2026-09-24

`ResourceFieldYield.fromCompletedCycle` now provides the future variable-yield
boundary without making an unearned runtime claim. It requires every admitted
cell to be accounted, no pending player action, and derives only actual
`yielded` cells. Zero creates no lot; positive quantities produce deterministic
distinct 1–64-unit wheat lots keyed by site, epoch and part. Layout fingerprint
stays in provenance and result quantity in the lot value; competing positive
outcomes of the same epoch collide on an ID rather than double-crediting. The
factory is the only constructor, so callers cannot hand
it an unrelated quantity/lot list. Focused zero/1/63/64/129 output, lost crop,
recovery identity and invalid-predecessor tests pass (7s). A direct ledger
negative check also rejects a contradictory second positive outcome bearing
the same site/epoch/part lot ID; NeoForge main compilation passes. Zero output
still needs a retained terminal lineage/receipt to reject replay because it
has no lot to collide on.

The active reducer now derives that exact value before its legacy full64
gate; focused reducer/yield tests pass (15s). It still creates one exact
64-wheat item, and the physical receipt still assumes one slot. This is a
typed cutover component, not variable-output completion. Next source
work must replace exact-item output, lineage, capacity/physical binding and
restart receipts together, then retire the old owner. No native run, schema
change, deploy, commit or push.

### Shared container-slot availability includes fungible bindings — 2026-09-24

Static tracing of harvest-output admission found a present runtime defect:
`FrontierWorldState.containerSlotAvailable` and `ExactInventory.firstFreeSlot`
looked only at exact-item slots. A live fungible physical binding in a depot
could therefore be selected as the next exact output; the later inventory
constructor rejected the collision instead of choosing the next vacant slot.
`ExactInventory.slotVacant` now checks both exact occupancy and container-slot
fungible bindings, and both selection paths use it. Production-hold reservation
remains an additional world-state fence. The focused inventory and harvest
process suites pass in 19s, including a binding occupying slot0 while slot1
remains available. This closes this slot-selection defect only; it is not the
multi-stack field-output/capacity cutover or SA09/product closure. No native
run, deploy, commit or push.

### SA-09 capacity admission and atomic physical chest image — 2026-09-24

Source review exposed two adjacent defects. Unbound COLD wheat occupied
canonical depot stock but no exact-slot reservation, so exact producers could
fill all physical slots. `ExactInventory.availableSlots` now budgets at least
one 64-unit stack per item kind and includes current HOT binding slots;
`firstFreeSlot`, world-state output selection and `store` share that admission
result. The budget does not pin COLD stock to one arbitrary address, and
historical conflict records are not treated as currently occupied slots. It
deliberately does not claim a universal per-item stack-size proof.
The NeoForge owned-chest writer/replacer formerly wrote an exact prefix and
then discovered insufficient fungible capacity, leaving a partial or cleared
chest. Both now construct and validate the entire physical image using real
registry/max-stack data for both exact and fungible stacks before any mutation.
An impossible old overcommitted
canonical image remains representable for recovery diagnosis, but cannot be
partly projected over real physical evidence.

Focused `ExactInventoryTest`, `ResourceSiteHarvestProcessTest` and
`ProductionProcessTest` plus NeoForge main compile pass. The focused native
reference-projection slice passes all 4 GameTests (2.363s test time in the
final corrected run,
terminal exit0), including no partial fresh write, no destructive replacement
and complete write after one slot is released. The source worktree and both
histories are preserved. No client/product acceptance, schema change, deploy,
commit or push. SA09 and variable-yield field output remain OPEN; the next
cutover still needs typed multi-stack capacity/receipt, fungible custody and
zero-yield terminal replay fencing.

### SA-09 shared ingress capacity fence and chunk-bound field view — 2026-09-24

The remaining `ExactInventory` ingress pathways were traced. A shared minimum
container footprint now counts exact occupied slots plus the larger of current
HOT binding slots and optimistic 64-unit packed COLD stock. `moveObservedItem`,
exact/fungible cargo completion, direct fungible-ledger replacement and the
legacy consume/store helper reject a transition that newly overcommits this
footprint. The strict constructor still decodes an old overcommitted image;
subsequent transitions may leave or reduce, but may not worsen, its excess.
Negative model checks cover actor-to-container, exact cargo, fungible cargo,
direct stock replacement and old-image non-worsening. This is a capacity
admission fence, not a durable reservation for a future field/production
output, nor a proof for item kinds whose real stack limit is below 64. Physical
projection retains its separate registry/max-stack preflight.

The replacement field witness now exposes `cellsIn(layout, chunk)`: after
cached revision/geometry/CellId matching, it uses the canonical layout's
chunk index to return only cells in that natural chunk. It does not persist a
second chunk partition or create a second geometry owner. The focused
NeoForge witness JUnit passes, including recovered claims across positive and
negative chunk coordinates. It is not wired into format-7 SavedData or live
chunk observation, so the field runtime cutover remains OPEN.

The broad `:pale-mirror-frontier:test` run executed 945 tests in 6m56s and
reported four failures: three readability fixtures changed a field lifecycle
epoch without changing its required cell cycle, and one catalog test pinned a
profile count of 34 while the declared catalog contains 35. Source inspection
showed the domain epoch invariant is correct; the fixtures now construct a
matching mature cycle. The catalog test now checks nonempty/unique declared
profiles and still constructs/verifies every profile without a brittle count.
Both affected classes pass in a focused rerun. The full 945-test suite was not
repeated; do not claim a new broad green from these focused results. No native
client, deployment, schema change, commit or push in this increment.

### SA-09 field output cannot be one terminal depot write — 2026-09-24

Static capacity proof: `ResourceFieldLayout.MAX_CELLS=65,536`, while the
reference 27-slot depot holds at most 1,728 ordinary 64-stack wheat. Existing
`ResourceSiteHarvestJob`, `ResourceSiteHarvestObservation`, lineage,
`FrontierV3ResourceSiteLedger.HarvestReceipt` and the HOT executor all name
one exact item/slot and one 64-cell terminal. Merely replacing the output
type with a list cannot handle a field whose potential yield exceeds the
depot, or preserve physical wheat while waiting for storage. Therefore the
accepted arbitrary-layout promise requires bounded streaming custody:
confirmed yield increments one current carried batch; at 64 actual units or
the final partial amount, the worker delivers it by a real route/physical
handoff. Full storage pauses further yielding work with a bounded retained
batch, not an unbounded invisible terminal balance. Zero-yield completion
retains a replay fence without inventing a stack. This is an architectural
correction within the accepted field lifecycle, not implemented product
behavior yet.

`ResourceFieldYield.nextReadyLot` now derives a stable site/epoch/part lot
from a resolved accounted prefix and previously issued full-batch quantity.
Its per-part provenance no longer depends on the unknown eventual total.
The subsequent actor-custody review removed even the current part quantity
from provenance: the source identity remains stable while a carried lot gains
one observed unit at a time. Different quantities remain different lot values
under the same ID, so a contradictory second result still collides. A focused
same-layout/different-yield regression passes. No actor-held growth or
physical credit is live yet.
Focused yield/harvest-process tests pass: 64+64+2 from 130 cells, a lost
first crop delaying the first full lot to cell65, stale-prefix/skipped-batch
rejection, and equality with the final accounted review. This is pure
source-level progress only. Remaining work includes per-cell actor-held
custody and HOT binding, typed batch receipt/restart, real depot transfer and
capacity backpressure, segmented shared movement, zero-result lineage, and
retirement of the old format-7 fixed-output authority before client testing.
No native run, deployment, schema change, commit or push.

Static scalability follow-up: the initial batch readiness method scanned the
entire work prefix on every call. `ResourceFieldCycle` now retains an exact
contiguous accounted-prefix counter, maintained on cell replacement and rebuilt
from cells on recovery/revision; `ResourceFieldLayout` caches CellId work
positions. A readiness check validates that counter and checks only pending
actions, so a 65,536-cell field does not repeat a full-prefix scan for each
64-unit batch. Focused layout/cycle/yield tests pass, including out-of-order
work, a restored 64-cell prefix and a pending player action outside the issued
batch. The counter is derived, not a second persistent field-work authority.

The next custody trace exposed a separate admitted-state hole: a fungible HOT
binding could declare a container slot or player slot belonging to someone
other than its canonical custody account. The ledger now rejects wrong
container IDs, wrong player UUIDs, player addresses under container custody,
and incompatible world-carrier address types when constructing/recovering the
binding map; the container registry's physical slot checks remain additional.
Focused `FungibleResourceLedgerTest` passes with forged-owner regressions.
Adjacent physical-observation, cargo-release and settlement-provision suites,
plus NeoForge main/test compilation, also pass; this was one affected-boundary
check, not a native/product campaign.
This is a local invariant repair, not an actor-held wheat implementation:
`ResourceCustody.Actor` exists, but no actor stack address, codec, observable
physical equipment bridge or per-cell batch credit yet exists. These must be
introduced together with the versioned harvest output cutover.

Next source increment: the existing HOT farmer path changes ripe wheat directly
to age-zero wheat, while its terminal path creates an exact 64-wheat stack in
the depot. There is no held wheat on the worker between those effects. The
pure field derivation now exposes `currentCarriedLot` after every accounted
yield, with stable part identity/provenance; `nextReadyLot` seals that same
part at 64 actual yields or at the accounted terminal partial. The fungible
ledger now supports a COLD actor-held part gaining exactly one unit at a time,
rejecting replay, skipped units, forged metadata, foreign actor, another live
part and a physical binding that needs an observed HOT update. Focused yield
and ledger suites pass. **Still not live:** the harvest reducer does not call
this transition, actor equipment has no typed address/codec/physical bridge,
the worker has no depot-delivery route, and the old exact-item terminal remains
the only admitted runtime output. No client/product claim follows from this.

Actor-hand custody follow-through: `PhysicalStackAddress.ActorHand` now names
both the producer-declared actor and the exact body UUID. The generic fungible
ledger rejects an actor-hand binding on another actor account; complete world
validation additionally checks the body against the world's deterministic
actor UUID and rejects unknown actors. Snapshot and WAL payload codecs retain
this address under new tag 4; at that checkpoint snapshot schema 187 and WAL envelope 72
explicitly reject 186/71. `accrueObservedActorHarvestPart` atomically grows
one HOT lot and its one binding only from an observed same-hand, same-epoch
stack; replay, skipped quantity, foreign body and stale epoch fail closed.
Focused actor-hand codec/world validation, ledger and persistence-envelope
tests pass, including a complete canonical snapshot/recovery roundtrip;
NeoForge main/test sources compile. The physical Minecraft producer/observer,
saved after-effect receipt, scene release and route/depot transfer are still
OPEN. Merely retaining a typed address does not prove a held ItemStack exists.
Source review then found the generic fungible layout/release/handoff process
would otherwise accept an actor-hand address from a command without any
farmer-work or physical-body proof. It now rejects actor custody on those
generic paths; the future harvest owner must provide its own exact observed
effect and scene-release/transfer protocol. A focused registered-process test
rejects all three forged generic operations while the ordinary container,
player and world-carrier paths remain on their existing owner. This is a
fail-closed interim boundary, not the final actor transfer implementation.

Further source trace found a physical slot collision hidden by the nominal
`ActorHand` address: ambient actor construction equips canonical exact tools,
weapons and service materials into MAINHAND. A farmer's fungible wheat cannot
also occupy that same slot without overwriting a different custody owner.
The address is therefore reserved for OFFHAND; the new read-only NeoForge
observer checks only the current canonical HOT harvest job/lease and its exact
live scene-owned worker before classifying an offhand stack. A missing loaded
body is `UNAVAILABLE`, not proof of an empty hand; foreign owner/item and
bounded wheat are separate results. A focused pure classifier test passes
empty, foreign kind, exact37 wheat and overfull rejection, and NeoForge main
compiles. Plain JUnit cannot instantiate `ItemStack` without Minecraft
registry bootstrap (the first test attempt stopped before classification), so
this is not a native mob-observation claim. No HOT effect or reducer consumes
the observer yet. The paired crop+hand physical effect, COLD one-unit accrual,
streaming delivery and release/recovery path remain OPEN; the exact static
transition is in `frontier-v3-field-cell-lifecycle.md`.
The shared scene-release planner and reducer additionally reject a member
whose actor-hand physical binding remains live; without that fence, generic
release could close/discard the worker while wheat stayed nominally HOT.
Focused `ActorHandCustodyTest` passes both pre-journal and replay rejection.
The typed hand-to-COLD/depot disposition is still unimplemented.
Full-state validation also now rejects a deterministic but lease-free actor-
hand binding. The positive roundtrip uses a real HOT harvest lease; the
previously admissible phantom binding without an admitted (not PREPARED/CLOSED)
scene/ambient owner
is a negative case, as is a merely PREPARED permission. The normal no-actor-hand
state skips the extra lease scan.
This proves only nominal ownership, not actual Minecraft offhand contents.
The direct unknown-patrol revoke planner now dry-runs its actual immutable
post-state before WAL publication; closure with no remaining admitted hand
owner rejects, while a different legitimate owner need not be blocked.
PREPARED scene abort uses the full-state invariant rather than the strict
ordinary-release guard, since its prior ambient owner may still hold the hand.
Typed delivery/death/transfer still need their own owner.

### SA-09 paired crop/offhand witness checkpoint — 2026-09-24

Static recovery review found that a persisted step-zero field effect followed
by both physical block writes was classified as owned drift, even though its
exact terminal condition was a declared later prefix. The one-cell observer
now identifies a unique later prefix and the witness confirms only a matching
naturally loaded-world review of its exact prior claim. A native GameTest for
this recovery window compiles but has not run; focused JUnit passes. This does
not establish actual disk-save ordering across chunks and SavedData.

The replacement field witness has a version-2 optional paired harvest intent.
It retains one job/actor/body/authority epoch and a bounded offhand wheat
before/after quantity together with the crop cursor. The `beginHarvest` API
requires real observed cell and offhand predecessors; pure caller-created
comparisons cannot authorize it. After crop confirmation, pending survives
until the same live HOT worker's actual offhand after-stack is observed.
Malformed persisted owner/quantity and synthetic crop/hand postconditions fail
closed in focused JUnit; the source retains the hand pending after crop
confirmation, but its native positive path is not yet verified. NeoForge
production/test compilation passes. This is an inactive replacement value, not a second SavedData owner,
not an active native worker effect, and not an economic harvest receipt.

Before activation, source review must close four concrete boundaries: one
write-ahead/save-order and restart disposition for block/entity/SavedData
halves; the actual server-thread crop/offhand writer and native positive
observation; an atomic canonical cell-plus-one-unit actor-lot reducer for HOT
and COLD; and physical hand-to-depot delivery/death/release handling. The
current format-7 fixed-output path remains live. No client run, deployment,
commit or push in this checkpoint.

### SA-09 declared harvest owner trace — 2026-09-24

The live `ResourceSiteHarvestProcess` reducers for crop preparation, work
progress and COLD/HOT traversal previously found an active job by scanning
all site lifecycles, then checked the event's site owner. These events already
have an explicit event subject. They now look up exactly that declared site
first and require its active job ID and worker to match; they do not borrow a
job from another site's collection. Focused harvest-process and traversal
tests pass with wrong-job and wrong-site negatives. This is a source ownership
repair in the current path, not a per-cell output or arbitrary-field cutover.

The scheduled COLD continuation is a separate unresolved contract: its
`ScheduledAction.subject` is the job ID only. `activeJob` and terminal
`retiredContinuation` still scan lifecycle collections to find the site/job
relationship. The accepted no-inference architecture requires that schedule
and its recovery/terminal lineage retain an explicit typed site owner (or an
equivalent canonical job-to-site relation), with a versioned persistence change
and negative stale/foreign-owner tests. Do not silently derive site identity
from a job ID prefix or choose a unique matching lifecycle. This remains OPEN
as part of the coherent harvest owner cutover; current schedule bytes were not
changed in this checkpoint.
The same missing declaration exists in the persisted
`ResourceSiteHarvestSceneCause(jobId)` and its snapshot/WAL codecs:
`FrontierResourceSiteHarvestSceneSupport.activeJob` and terminal release scan
all sites because the cause does not name its site. The cutover must add an
explicit typed site ID to the scene cause, validate it against the retained
job/terminal lineage, version the snapshot and WAL payloads, and reject old
or mismatched owner declarations before scene dispatch. The present direct
reducer repair does not close this scene/schedule owner gap.

### SA-09 harvest schedule and scene owner cutover — 2026-09-24

The follow-through closes the specific job-only owner lookup above, without
claiming the wider field/yield cutover. `coldProgress` now persists the explicit
site ID as its action subject while retaining the job-specific schedule ID.
Active and retired-tail planners look up only that site and validate the
retained job/lineage; no lifecycle scan or job-prefix decoding supplies the
owner. `ResourceSiteHarvestCausality` validates the site-owned action.
Persistence envelope 73 rejects version 72 actions, whose `subject` meant
job, before they can be replayed under the new contract.

`ResourceSiteHarvestSceneCause` now requires the declared site/job pair.
Active-job and terminal-release resolution use only the named site; a job
present at another site cannot be borrowed. Scene snapshot schema 188 rejects
job-only schema 187. The harvest-scene WAL body has a new marker and writes
site before job; the old marker is rejected. HOT command planning obtains the
site from its required, kind-checked retained continuation, verifies the
exact job against that site and then compares both site and job against the
HOT lease. Scene release continuation retains both IDs. The physical executor,
HOT traversal/due gates, release binding, actor-hand observer and tester-facing
schedule diagnostics all use the new site-owned action. Focused harvest,
SDK/recovery, persistence-envelope, scene-cause and NeoForge executor/
diagnostic tests pass; production/pilot/test sources compile. This is a
source-level owner/format correction only. No native scene run, client test,
deployment, commit or push; physical crop+hand writes, atomic cell/lot credit,
bounded batch delivery and recovery across actual chunk/entity saves remain
OPEN.

Follow-up on the exact terminal action: the HOT crop executor previously
submitted a receipt with `not_captured` schedule evidence even while it held
the precise due binding, and the retired-tail planner reconstructed a schedule
ID from the predecessor job's string. HOT now stamps the actual bound
`ScheduleId` and due instant in its crop receipt; command admission compares
both to the retained engine binding. `ResourceSiteHarvestProgressed` requires a
typed schedule ID and nonnegative due instant (the missing-evidence constructor
is gone), and reducer replay validates that the receipt names this site's
exact job continuation. Terminal lineage retains those facts; only the exact
resolved ID+due pair can consume a recovered tail. Same-ID/wrong-due and
foreign/missing HOT evidence are rejected in focused regressions. This closes
the no-inference owner evidence of the current fixed-field receipt, not the
remaining physical crop+hand, variable-yield, delivery or disk-order work.

A kernel-level source check exposed two empty-result branches mislabelled as
harmless holds. `InMemoryFrontierEngine` explicitly quarantines any due action
whose planner returns no event. A retained harvest action in terminal CONFLICT
now emits its exact `ScheduleEffect.Consumed` disposition; any unexpected
non-HARVESTING phase still fails closed. After the final HOT crop, the job can
remain HARVESTING while its physical output receipt is pending. Its action is
now marked held by the scheduler and a direct plan reschedules the identical
action instead of returning empty; only the eventual physical confirmation
cancels it. Focused conflict and final-HOT-crop regressions pass. This avoids
false kernel quarantine and premature loss of the receipt's cancellation
authority; it does not make the fixed-64 receipt an arbitrary-field output.

### SA-09 terminal partial-lot review and active-owner boundary — 2026-09-24

The batch derivation accepted only `issuedQuantityBefore` divisible by 64.
After an accounted 130-yield cycle delivered its final two-unit lot, a later
review with `issuedQuantityBefore=130` therefore failed instead of reporting
no remaining lot. `ResourceFieldYield.currentCarriedLot` now permits precisely
the fully accounted terminal equality and returns empty; an early partial
issuance remains invalid. Focused 130/129-unit regression passes. This is a
model correction, not activation of variable-yield output.

The source dependency review confirms why the active cutover cannot be made by
relaxing the final count check: `ResourceSiteHarvestJob` and lineage still
store one `outputItemId`/`outputSlot`; harvest intent roles, retirement account,
relationship graph, snapshot/WAL codecs, exact-item production consumers and
the native depot receipt all name that same stack. The live scene executor
replants the crop but does not write or observe the offhand wheat; its block
prefix is recorded in format-7 `FrontierV3ResourceSiteLedger`. The replacement
format-2 `FrontierV3ResourceFieldWitness` and actor-hand observer are inactive.
Next coherent change must replace the job/output obligation and physical
receipt together: one durable cell+hand effect, one atomic canonical cell+lot
transition, retained batch delivery/backpressure and versioned replay/lineage
through all listed consumers. Do not interpret the pure lot API as live harvest.

### SA-09 two-lot production player path — 2026-09-24

The existing one-lot native story did not exercise the accepted multi-lot
production contract. A disposable-only profile now splits its initial depot
wheat into two distinct 32-unit lots, then uses the ordinary scheduler to
reach one 17/80 COLD production job; it injects neither job nor result. The
focused fixture test confirms the exact two-lot claim, worker and schedule.
`disposable-fungible-two-lot-production-midwork-restart.json` observes that
claim before ingress, a visible HOT industrial worker, the same unfinished
job with `FungibleBound` input after graceful server restart, then one 64-bread
lot, no job and a current depot replica. The private-display run completed
with status `ok` and a reused client JVM; evidence is
`build/frontier-v3-scenarios/disposable-fungible-two-lot-production-midwork-restart-corrected.json`
and its linked frame. I inspected the frame: a villager is present in the
graybox workshop view, but the frame alone does not prove the entire work
animation. The disposable server ports 25677/25678 closed; task Xvfb :102
was stopped. The live server/world were untouched.

Two preceding attempts are preserved as test-method failures, not product
failures: the first omitted the explicit private-display flag; the second
asserted a resource snapshot without requesting that diagnostic. The
corrected scenario requests the snapshot and keeps its restart/frame indexes
aligned. This is a positive one-job, two-input-lot, COLD→HOT→graceful-restart
product slice. It does not prove abrupt recovery, many simultaneous producers,
arbitrary field yield, full-pack human visual acceptance or SA-09 closure.

### SA-09 physical-completion/canonical-receipt seam — 2026-09-24

Source review found that the inactive paired field witness cleared its only
pending record immediately when it observed the farmer's offhand +1. The
canonical `ResourceSiteHarvestProgressed` transaction had not yet been
submitted or committed. A crash in that seam would therefore leave a changed
crop and hand without the exact retained cause needed to reconcile or reject
replay. This is a concrete static inconsistency, not an observed live-server
failure: the replacement witness is not yet wired into SavedData.

The replacement witness first gained a format-3 `handConfirmed` stage after
real same-owner/same-epoch hand observation. Its pending cell/cause and hand
predecessor/successor survive NBT recovery; malformed or premature confirmation
is rejected. It deliberately has no independent clear-on-hand path or generic
retirement method. Focused witness JUnit and NeoForge main/test compilation
pass. The new test constructs a ready-state NBT image to check its decoder
invariant; it does **not** simulate an actual native hand observation.

Another source-level owner gap was found immediately before integration:
revision, geometry and CellId alone did not declare which site owns the
witness. The replacement value now uses format 4 with a required nominal
site ID at creation/recovery; its paired hand effect, chunk view and
confirmation-capable loaded-world observation require the same site. Missing
and foreign site regressions pass the same focused suite. Format 3 is rejected
by the new decoder; there is no active SavedData migration because neither
format was ever installed as a runtime owner.

This closes only the witness-value erasure and nominal-owner gaps. The live format-7 writer,
cross-save ordering, exact canonical cell+actor-lot receipt, hand writer,
streaming depot delivery/backpressure, zero-yield fence and final physical
retirement remain open. The next active cutover must provide an exact
idempotent receipt/ack boundary before it can clear this retained stage. No
client run, deployment, commit or push follows from this checkpoint.

### SA-09 canonical field-cycle owner — 2026-09-24

Further source review found that `ResourceFieldCycle` itself had no nominal
site ID. `ResourceSiteState` matched its map keys to lifecycles and matched
cycles by epoch/layout, but geometrically matching cycle data could still be
retitled under another site. That would let later field-yield derivation use
the caller's site to assign a different lot identity.

The cycle now retains the explicit site through cell work, growth, epoch and
layout revision. `ResourceSiteState` compares the declared cycle owner to its
key before admission and replacement; the yield API checks the same site.
Snapshot schema 189 writes the cycle's own site as a separate declaration and
rejects old schema 188. Focused cycle/yield/state/codec/schema tests and
NeoForge pilot compilation pass. A forged snapshot fragment with correct
geometry but foreign cycle site is rejected before world-state admission.
This is a canonical owner correction, not completion of the live physical
field writer or batch custody. No client, deployment, commit or push.

### SA-09 active harvest output-slot reservation — 2026-09-24

Static trace found a separate live custody collision: harvest admission chose
and retained an exact future depot slot in `ResourceSiteHarvestJob`, but
`FrontierWorldState.containerSlotAvailable` and `firstFreeContainerSlot`
considered inventory and production input holds only. Another canonical
ingress or producer could therefore take the still-working farmer's promised
slot before its terminal receipt; the late `inventory.store` would then fail
instead of making the output available.

The shared slot-admission boundary now treats the active harvest job's declared
output slot as reserved. Full world-state validation rejects any exact item,
fungible HOT binding, exhausted total stock capacity or second harvest job
that collides with that slot. The first-visibility complete chest-image writer
now packs COLD fungible stock around the retained reservation; previously,
that branch would choose the farmer's future output slot for bootstrap wheat.
The reservation is derived from the one canonical job, not a second mutable registry; it survives
state-codec recovery and ends when the terminal transition replaces the active
job with depot custody. Focused harvest/production model tests and the pure
NeoForge projection-slot regression pass. A direct JUnit invocation of the
Minecraft `ItemStack` writer was invalid without the NeoForge runtime bootstrap,
so this checkpoint does **not** claim a native chest-image observation. The
reference container's live observed-binding/conflict path and any pre-existing
foreign physical stock still need coherent receipt handling at field cutover.
The live fungible observer and the chest-image comparison now also reject a
stack observed in an active farmer's reserved output slot; the observer raises
the depot's local conflict rather than adopting that stack as current custody.
The pure reserved-versus-next-slot regression passes. This is fail-closed
classification, not autonomous recovery or a native chest visit.
The focused aggregate regression also rejects a forged HOT fungible binding
to the same reserved slot; harvest, world-state, ledger and ingress suites pass.
This is only an exact-stack admission/projection repair, not bounded carried
batches, partial/zero yield, physical hand receipt or arbitrary field geometry.
No native/client run, deployment, commit or push.

### SA-09 source-discovered task-owner inference in resource theft — 2026-09-24

The next static trace found an authoritative relationship gap, not a test-time
timing problem. `ProductionJob` and `HiveGrowthJob` retain their resource owner,
worker/input and output, but neither retains the `StrategicTask` that admitted
the job. Ordinary completion in `ProductionProcess.activeTask` falls back from
an accepted market order to scanning ACTIVE bread tasks by settlement;
`HiveGrowthProcess.activeTask` scans ACTIVE hive-growth tasks by hive. The
physical multi-lot theft path in `FungibleClaimForfeitureStateSupport` repeats
both scans before retiring the affected job/task. One matching task today is
not a producer-declared relationship and a second matching task changes the
disposition to ambiguity. This violates the project's strict no-inference
boundary and can cancel the wrong work or block a valid interruption.

Required coherent repair: each producer declares the nominal job→task ID at
admission; jobs retain it through changes, snapshot and WAL with new schema
tags. Validate the exact task owner/kind/status and, where present, the market
order's task ID. Completion, cancellation, diagnostics and theft retirement
look up only that retained ID. The relationship view exposes the explicit
edge; no scan or fixture compatibility constructor may reintroduce authority
in production code. Test missing/foreign/stale task IDs, concurrent same-kind
tasks, mixed-claim theft, recovery and the ordinary one-task path. The broader
multi-owner theft and supply-contract retirement remain open. No task-ID
migration or product acceptance is claimed in this checkpoint.

### SA-09 declared production/hive task relation — 2026-09-24 (scoped repair)

The admitting `StrategicTask` ID is now mandatory on `ProductionJob` and
`HiveGrowthJob`; producers stamp it, every immutable job revision retains it,
snapshot schema 190 and WAL envelope 74 persist it, and earlier formats fail
closed. Aggregate admission/recovery validates exact task existence, owner,
kind and live status. The relationship view declares `JOB_TASK` and
`HIVE_GROWTH_TASK` edges and registers the new direct identity components;
accepted market orders must retain the same task as their live job.

Production completion, physical transformation, facility-loss and scene
finalization, hive completion and fungible-claim forfeiture now look up the
retained ID. A source follow-up found a second ownerless surface: a pre-start
`ProductionBlocked` event searched pending tasks by owner/kind in its finance
reducer. It now carries an explicit task ID in the versioned WAL, and all
block producers declare it; the reducer checks it against the exact job or
pending task before evaluating the reason. There is no orderless
settlement/kind fallback for scene finalization.

Focused and expanded production/hive/persistence tests pass, including
snapshot retention, missing job task, another same-kind task with a mismatched
accepted order, blocked-event WAL round-trip and forged task rejection. This
is a static/model repair, **not** mixed-claim theft closure, arbitrary-field
output, native client observation or live deployment. SA-09 remains OPEN.

The same source review removed a now-invalid current-format compatibility
path: `ProductionStarted` no longer infers a missing input-hold type as
materialized, and world-state job decoding no longer accepts a caller flag
that could omit the hold. Schema/envelope rejection handles historical bytes;
the current payload rejects a truncated hold. Focused codec and runtime
definition tests pass.

Static continuation found the next independent boundary: `committedDeparture`
derives *all* claims touched by one physical stack, but
`FungibleClaimForfeitureStateSupport.planCore` then keeps only `affected.getFirst()`
and rejects multiple claims unless they are one ration cycle. Its `Plan` and
`FungibleResourceHandoffObserved.retirementDiagnostic` can represent only one
owner/diagnostic. Thus a mixed-owner withdrawal can be physically observed yet
cannot atomically retire all promised work. Do not fix this by accepting just
the first claim or silently freeing stock. The next repair needs an explicit
bounded set of typed per-owner retirement plans, validation of every affected
claim before any mutation, one atomic aggregate update, and a versioned
multi-diagnostic/incident account where necessary. Verify mixed production/
ration/supply cases, a foreign claim, replay and recovery; preserve a local
conflict for an owner with no valid retirement transition. This remains OPEN.

### SA-09 bounded same-kind claim retirement — 2026-09-24 (partial)

Source review of the actual stack/claim invariants narrowed the previous
mixed-owner hypothesis. One Minecraft binding contains at most 64 units of one
item kind; production and hive growth each reserve 64 units, while multiple
ration allocations may share a bread binding. Ration multi-claim retirement was
already implemented and tested. Supply-contract claims live in cargo custody
and are retired by the cargo-carrier release owner before an ordinary external
handoff. Generic external reservations still have no declared work-retirement
transition and must remain an explicit local rejection, not silently disappear.

The forfeiture reducer now validates every affected production/hive claim and
can fold their task/job/order/intent consequences into one immutable aggregate
transition, rather than assuming exactly one non-ration claim. It releases the
physical claims only after all owner plans validate. This is a narrow model
hardening; it does **not** assert that today's single-workshop/single-growth
admission produces such a multi-owner binding, does not add an external-claim
owner transition, and does not close the broader mixed-owner or field cutover.
The existing physical handoff and provision-focused suites pass. A production
fixture exercising two concurrently admissible jobs in one account and a
versioned multi-diagnostic envelope remain open until the corresponding
producer geometry exists. No server redeployment or new client claim follows
from this change.

### SA-09 physical-before-canonical cell receipt window — 2026-09-24 (partial)

The replacement `FrontierV3ResourceFieldWitness` had a second cause-loss path:
unlike the paired harvest hand, a planting/tillage projection dropped its
pending cause immediately after the last actual block write. Witness format 5
now retains that complete physical transition through NBT recovery and refuses
replay until a future exact canonical acknowledgement. Old format 4 fails closed.
Focused witness JUnit and six real-block `field-turns` GameTests pass. The first
native attempt exposed a stale pilot-only `container:1` fixture and failed
before its assertion; using the site's declared depot corrected that test, and
the single discriminating rerun passed. No broad matrix was run. This closes
only the lost-cause window in the inactive replacement witness; active
SavedData, per-cell work/output and canonical acknowledgement remain open.

### SA-06/07 first-body permission batches are all-or-none — 2026-09-24 (scoped repair)

Source review found `FrontierV3ActorBirthRecovery.retireUnpublished` removed
unused first-body permissions one at a time. A later contradictory permission
could throw after earlier entries had already been removed from the shared
in-memory SavedData image; an ordinary subsequent save could publish that
partial cleanup despite the failed startup. The ledger now preflights the
complete bounded candidate set against the recovered canonical state and its
retained custody before removing any entry. Startup persists only after that
whole-set transition. A foreign-world permission sorted after a valid one
leaves the image byte-equivalent and never invokes persistence; two valid
unpublished permissions retire together with one persistence call. Focused
review found the symmetric issue at fresh-world bootstrap and the pre-WAL
multi-birth committer: both previously registered permissions one by one.
They now use the same ledger-level whole-set preflight. A later already-used
bootstrap or birth identity cannot leave earlier new permissions in memory;
a valid two-birth transaction exposes both permissions before its one WAL
append. Four focused birth/recovery/first-admission suites pass (23 tests,
12s), and `git diff --check` passes. A persistence failure can still leave the validated
change dirty in memory for an exact retry; this repair does not claim
cross-file atomicity with the canonical WAL or entity region files. SA-06/07
and product/restart acceptance remain open. No native run or live deployment.

### SA-06/07 resident birth retains its admitting job — 2026-09-24 (scoped repair)

Static source tracing found that `ResidentBorn` omitted the ID of the
`ResidentBirthJob` which admitted the newborn. `PopulationBirthProcess.reduceBorn`
instead scanned active jobs for matching resident tuple and position. The
scheduled planner already had the exact job as its subject, so this was an
avoidable inferred-owner transition. The event now requires the nominal
`jobId`; the reducer looks up only that job and checks settlement, resident and
position. The planner also requires the exact retained completion schedule ID
and the due tick stored in the resident birth profile. Direct payload format
`RBO2` rejects the old jobless body, and WAL envelope 75 rejects v74 before
replay. This is a fresh-world-only schema boundary; the live diagnostic server
was not updated and its v74 world must not be reopened by this checkout.

Focused frontier process/codec/WAL tests and NeoForge birth runtime/committer
tests pass (25 cases in the first selection, with the process suite passing
again after the exact due-time guard). Negative coverage includes foreign job
ID surviving payload round-trip, foreign/wrong-due schedule, and old payload/
WAL rejection. No native body/client observation or full SA-06/07 closure is
inferred.

### SA-09 typed field-part depot handoff — 2026-09-24 (partial)

`FungibleResourceLedger` now exposes field-specific COLD and HOT transfer
boundaries for a part that `ResourceFieldYield.nextReadyLot` derives from the
exact accounted cell prefix. They consume the one actor-held account; HOT
requires its current actor-hand body/epoch binding and installs the complete
observed depot layout atomically. Generic transfer and cargo delivery can no
longer serve as alternate actor-custody exits. Existing depot reservations
and bound stock are preserved. Focused model and affected generic-ingress/
cargo tests pass. This is not an active harvest cutover: route arrival,
Minecraft hand/chest observation, storage capacity, job/output persistence,
physical SavedData and canonical acknowledgement are still missing. The
current runtime remains on exact64 output, with no client/deployment claim.

### SA-06/07 scheduled resident birth reaches the first real body — 2026-09-24 (native checkpoint)

The source-level birth/permission tests did not cross the Minecraft entity
boundary for a newly born resident. A focused native GameTest now starts the
real scheduled population review with bread, completes its exact retained
birth job through the normal runtime and birth committer, checks that conception
did not issue a body and birth did issue one unused permission, then prepares
an ordinary ambient lease and materializes that new resident as an indexed
Villager. The permission remains PENDING until an actual save acknowledgement;
the second materialization returns CURRENT and reuses the same UUID/entity.
The `scene-departure` native slice passed 19/19 on 2026-09-24; the server saved
and stopped normally. This specifically closes the dynamic resident birth-to-
first-body component gap. It does not prove process crash/region-file ordering,
late-body return, dynamic bioform first materialization, client presentation or
complete SA-06/07 acceptance. No live diagnostic-server change or deployment.

### SA-06/07 scheduled bioform birth reaches the first real body — 2026-09-24 (native checkpoint)

The active `FrontierV3ActorBirthCommitter` already handles declared BIOFORM
births, but the newly grown actor had only a canonical/file-store test. A
native GameTest now starts a real scheduled hive-growth task with its exact
biomass lot, confirms that the unfinished job grants no physical body, then
commits its exact `HiveGrowthCompleted` birth. It verifies the retired growth
job's output identity and one NEVER_CREATED permission, prepares an ordinary
ambient lease, and materializes the named bioform as one indexed Zombie.
Insertion retains PENDING save acknowledgement; a second materialization
returns CURRENT with the same entity. The affected `scene-departure` slice
passed 20/20 and the server saved/stopped normally. This closes the dynamic
bioform birth-to-first-body component gap, not process-crash/region atomicity,
late-body return, client presentation or complete SA-06/07 acceptance.

### SA-09 exact owner and epoch of a physical cell effect — 2026-09-24 (partial)

The replacement `ResourceFieldCellTransition` had layout revision and CellId
but no site or cycle epoch; a later cycle can revisit identical geometry and
block conditions. It now requires both nominal identities, stamped by
`ResourceFieldCycle.physicalWorkTransition`. The replacement witness retains
epoch in format 6 (rejecting format 5), checks site/epoch on `begin`, and
requires the current canonical cycle for naturally loaded observation and
chunk-local cell selection. An observation review also binds epoch, so an old
review cannot confirm a new-cycle effect. Focused model/witness tests and all
six native `field-turns` tests pass, including wrong-site and same-geometry
wrong-epoch negatives. Active format-7 SavedData, its fixed64 output and the
missing canonical acknowledgement are unchanged. SA-09 remains OPEN; no
client/deployment claim.

### SA-09 active field-site resolver uses canonical layout — 2026-09-24 (partial)

Source tracing found that, although `ResourceFieldCycle` already persisted a
versioned layout, active planners/projectors/scenes and diagnostics obtained
their `ResourceSite` geometry again from immutable bootstrap. That would be a
second geometry authority at the first layout revision. A shared resolver now
retains bootstrap site/farm/settlement identity but reads geometry only from the
canonical cycle. State-based active field planners/reducers, NeoForge
preparation/projector/harvest/scene/explosion/soil readers, boards and
diagnostics use it. Bootstrap-only initial scheduling and identity checks
remain intentionally unchanged. A model regression distinguishes a retained
revision-2 cycle from revision-1 bootstrap; focused process/adapter units and
three native resource-prefix tests pass. This does NOT enable revision
admission: aggregate validation still demands the initial layout, while the
physical SavedData and output remain stage+prefix/exact64. No client/deploy;
SA-09 remains OPEN.

### SA-09 cell observation and break permission name the exact cycle — 2026-09-24 (partial)

Static tracing of the next physical cutover found a replay seam: the new cell
transition/witness named site, layout revision, CellId and growth epoch, but
`ResourceFieldPlayerBreakPrepared` and `ResourceFieldCellObserved` named only
the first three. A later epoch can have the same geometry and block predecessor.
Both events and their payload codecs now retain the epoch; the reducers reject
an event whose epoch differs from the canonical cycle before admitting a player
permission or observed world change. WAL envelope 76 rejects the old event
encoding. Focused cell-observation, harvest-process and persistence tests pass,
including wrong-epoch events with matching cell/revision/predecessor and v75
envelope rejection; NeoForge production/test compilation passes. This is an
event-identity fix, not the active postcondition observer or SavedData cutover.
The live v74 diagnostic world must not be opened with this source. SA-09 remains
OPEN; no native/client/deployment claim.

### SA-09 harvest cell receipt declares site and epoch — 2026-09-24 (partial)

The other canonical join for per-cell work, `ResourceSiteHarvestProgressed`,
also omitted site and epoch within its payload. Its job/schedule could identify
the work indirectly, but a physical witness cannot safely acknowledge a cell
by inferred owner. HOT and COLD producers now stamp the canonical cycle's site
and epoch; the reducer checks both against its event subject and retained cycle
before applying the outcome. The payload codec carries both fields, and WAL
envelope 77 rejects v76. Focused harvest, production, payload and persistence
tests pass, including foreign-site and wrong-epoch negative receipts; NeoForge
production/test code compiles. No native/client/deployment claim.

Static cutover constraint: the terminal `reduceProgressed` both accounts the
last cell and advances `ResourceFieldCycle` to the next epoch, while the
replacement physical witness still names the predecessor epoch. Thus a later
acknowledgement that compares only the *current* cycle to the old witness would
lose or reject the last work effect. The cutover must retain and verify the
exact predecessor receipt/lineage through the WAL-backed terminal transition,
including its cell and yield/custody effect; it must not clear a pending
physical witness merely because current blocks look replanted. The old
stage+prefix SavedData and exact64 output remain active. SA-09 remains OPEN.

### SA-09 unloaded COLD epoch versus retained physical witness — 2026-09-24 (partial)

Source review of terminal `nextEpoch` and the per-cell replacement witness
found two recovery cases. With no pending physical cell effect, a later
canonical COLD epoch may be adopted without writing blocks: the witness keeps
each old physical condition and foreign incident, and loaded projection still
owes the real delta. `rebaseColdEpoch` now does this for an exact same-site/
layout later cycle, rejecting a non-successor, foreign site/layout, unresolved
player action or any pending physical effect. Focused NBT and negative tests
pass. This is a value operation on the inactive replacement witness, not the
live SavedData cutover.

A previous-epoch pending physical effect cannot be relabelled without losing
its cause. The witness refuses that transition. A separate read-only
`observePendingPredecessor` path now accepts only an already-pending physical
cell, exact same site/layout and a strictly later canonical epoch; it reads
the real loaded blocks and can confirm the retained old write cursor without
starting another effect. The existing current-cycle observer remains strict.
All six native `field-turns` GameTests pass, including the later-epoch loaded
predecessor and no-pending rejection. This does not acknowledge the canonical
work/lot receipt or retire the pending witness: that exact receipt/custody join
is still necessary before epoch adoption. The path is not yet called by active
SavedData/projector code. No client/deploy claim; stage+prefix/exact64 remain
live and SA-09 OPEN.

### SA-09 active physical-owner cutover inventory — 2026-09-24 (source evidence)

The current SavedData claim is read or mutated at 109 sites in
`FrontierV3ResourceSiteExecutor` alone. The same stage/prefix claim also drives
HOT crop writes (`FrontierV3ResourceSiteHarvestSceneExecutor`), terminal chest
output (`FrontierV3ResourceSiteHarvestExecutor`), player/structural conflict
(`FrontierV3ResourceSiteConflictExecutor`), explosion classification, and
`FrontierV3ResourceSiteDeferredTerminalPrefix`. The latter explicitly rebuilds
a stage/prefix `Claim` to infer a terminal predecessor. Therefore replacing
only the ledger serialization, or layering cell exceptions onto the old claim,
would leave several active writers and observers using a competing physical
interpretation. The cutover must switch this whole call graph together, with
the canonical cell/lot receipt and HOT hand/depot custody. No active switch is
claimed by the witness/read-only recovery increments; SA-09 remains OPEN.

### SA-09 mutually exclusive SavedData claim forms — 2026-09-24 (migration boundary)

Format 9 of the existing `FrontierV3ResourceSiteLedger` now retains a
`fieldClaims` section containing either a pending initial-write cursor or an
active per-cell witness in the *same* SavedData owner. A site may have either
its legacy stage/prefix claim or the new cell claim, never both: insertion, load and the shared twelve-site bound reject
overlap; legacy queries, projection/receipt, native-growth and whole-site
conflict methods fail closed for a cell-owned site. Active witness replacement
requires the exact current object and intent, cannot bypass initialization,
and freezes a CONFLICT claim.
All four current-format list sections reject missing or wrong-typed NBT rather
than silently loading empty. Focused current/old claim NBT, overlap, stale
replacement and corruption tests pass; the native `resource-prefix` slice
passes 3/3 and its server saves/stops normally.

This establishes a **disjoint migration boundary only**. The inactive initial
writer derives each block from the canonical layout and persists a one-step
prepared cursor after observing the declared neutral soil/irrigation or AIR
crop predecessor. Only a block write performed by that call
can advance it. Existing target-looking and foreign blocks are rejected, and
the witness can become active only after the complete seeded cycle. A crash
between block write and durable cursor acknowledgement still fails closed;
there is not yet a complete cross-file recovery transaction. The current
`activateField` validates the supplied witness against the canonical cycle,
not the entire then-current physical field; before production use it needs a
bounded completion-verification pass or equivalent exact physical proof. The
native `field-turns` slice passes 8/8, including neutral dirt/grass/concrete
and irrigation predecessors with a support-ground negative. A write also
requires the ledger's *current* claim for the exact site, not a borrowed
claim with identical geometry. No production
initializer installs a cell claim, no active writer/observer uses it, and the
old stage/prefix branch still serves every current site. The old branch must
be removed after the full call graph is switched; format 9 is not product
acceptance or a second runtime authority for one site. The active diagnostic
world is incompatible with this source and was not opened/deployed. SA-09
remains OPEN.

### SA-09 initial activation requires the current physical field — 2026-09-24 (partial)

`activateField` now requires the real `ServerLevel` and, before installing
`FieldOwnership`, reads every declared irrigation slot and cell in naturally
loaded chunks. Irrigation must still be WATER, and each soil/crop pair must
match the exact seeded canonical condition; an unloaded, foreign, missing or
advanced crop refuses activation without mutating the pending claim. A forged
completed cursor plus supplied witness alone is no longer sufficient. The
serialized active-claim unit fixture tests only SavedData mutation rules, not
physical activation. Focused cell-claim JUnit and main/pilot compilation passed;
the `field-turns` native slice passed all 8 required GameTests in one run
(26s total, 1.319s tests), including absent-water and missing-crop negatives.
The previous task-local native world was backed up under
`pale-mirror-neoforge/build/sa09-field-activation-preflight-20260924/` before
Gradle replaced the test run directory. This is a completion check for the
inactive initial writer, not a durable block-write transaction or production
cutover. SA-09 remains OPEN; no live deployment or client acceptance.

### SA-09 SavedData publication and one-step initial writer — 2026-09-24 (partial)

Source review found that the resource-site ledger previously used only
`setDirty()` at its write-ahead boundaries; none of the old stage/prefix paths
performed an explicit synchronous SavedData publication before touching a
block. The ledger now provides atomic, fsynced single-file persistence with
ordinary Minecraft `data`/`DataVersion` envelope and keeps the dirty flag on
failed publication. The new `FrontierV3ResourceFieldInitialWriter` binds the
level-owned claim to one operation: reserve+persist; observe; prepare+persist
before `setBlock`; advance only a block written and observed in that call;
persist the advanced cursor; and activation+persist after full physical
verification. A persisted prepared cursor facing an already-target-looking
block returns AMBIGUOUS, never silently adopts or writes over it.

The focused persistence JUnit covers valid envelope, no-op save and failed
publication; the native `field-turns` slice passed 9/9 in one 27s run, with a
new test reading the actual SavedData file after reservation, each real block
write and activation. Main/pilot compilation and the cell-claim unit selection
passed. The previous 8/8 native world was copied to the same preflight backup
directory before the task replaced its disposable run world. This writer is
not yet called by production preparation; old stage/prefix writers still lack
this explicit durable boundary. It is also not cross-file atomic with chunk
region save or the canonical WAL: an interruption after a block write but
before cursor fsync remains AMBIGUOUS and requires a declared recovery
disposition. `CURSOR_COMPLETE` is deliberately not activation or canonical
confirmation. The per-block double-fsync path is a correctness boundary, not
yet a scalable 65,536-cell production scheduler; bounded chunk-batch
publication/backpressure must be designed before general-size admission. Do
not infer SA-09 closure, crash recovery or product acceptance.

### SA-09 physical-cause origins cannot share acknowledgement — 2026-09-24 (partial)

Source tracing found a second reason the replacement field witness could not
advance beyond one cell effect: a completed physical pending cause had no safe
retirement transition. Matching the current cell state and a caller-supplied
cause string is insufficient: physical-first farmer work must not be retired
without its accepted canonical work event. Witness format 7 now distinguishes
an exact preaccepted canonical projection source (world and revision) from
physical-first work. `beginCanonicalProjection` requires an immutable canonical
field image whose exact target cell matches the transition before any physical
write. Only that origin may use `acknowledgeCanonicalProjection`, and only after
a complete loaded-world observation, same world, non-older revision, same
site/epoch/layout/cell, cause and final canonical condition. Physical-first
work, including harvest/offhand, remains pending; it still needs its own
event/lot/actor-hand acknowledgement path. Missing/old origin tags fail closed.

Focused witness JUnit and main/test/pilot compilation passed. The native
`field-turns` slice passed 10/10 in one 27s run, including real-block growth
projection closure and refusal to close physical-first work through the
projection API. The native canonical image is a test-local accepted-state
fixture, not a live engine/WAL event, and this API is not production-wired.
The previous 9/9 disposable world was preserved in the field-activation
preflight backup directory before Gradle replaced its run directory. This
does not complete the active projector, work receipt, general-size output or
SA-09 acceptance.

### SA-09 bounded canonical-first growth projector — 2026-09-24 (partial)

`FrontierV3ResourceFieldGrowthProjector` now operates on one declared CellId
from a level-owned active field claim and an immutable accepted canonical
field image. It compares cached layout fingerprints (not an O(field-size)
layout equality), requires natural chunk loading, starts only a farmland/wheat
age increase from an exactly observed owned predecessor, durably saves the
canonical-source pending witness before touching the crop, observes the real
block postcondition, and persists an acknowledged physical witness. Re-entry
from a saved pre-effect pending state writes only its exact next step; re-entry
from a saved pending state with the block already advanced confirms the loaded
postcondition without replaying the write. Absent crop, foreign/drift state,
pending player action, physical-first farmer work, unsupported soil/crop repair
and stale epoch stop locally rather than being projected over.

Compilation and the focused witness JUnit passed. The `field-turns` native
slice passed 10/10 in one 25s run; its disk-backed field test now exercises
the ordinary growth write, no-op revisit, both saved pending re-entry shapes,
and a missing-crop negative. The prior disposable 10/10 world was preserved
before this run. This is a functional *inactive* growth-cell executor, not the
production projector switch: no runtime scheduler calls it, no COLD-epoch
rebase/history receipt is composed, no farmer repair/harvest or player-loss
adapter uses it, and full-image SavedData fsync per cell is not scalable to
the maximum layout. SA-09 and product acceptance remain OPEN.

The final source review tightened admission of canonical-first work:
`beginCanonicalProjection` now requires a matching *loaded-world* predecessor
review of the exact cell, not merely a canonical target and witness memory.
Synthetic `compare` reviews cannot authorize a block write. Main/pilot
compilation passed, and one native `field-turns` run completed 10/10 required
GameTests (2026-09-24 12:01 server log) after this change. No production
projector switch or wider acceptance follows from this focused result.

Source-visible cutover dependency, before any server activation: the physical
registry still invokes `FrontierV3ResourceSiteExecutor::tick`, whose preparation,
recovery and growth write paths use the legacy `Claim` stage/prefix cursor.
`FrontierV3ResourceSiteHarvestExecutor`, the HOT harvest scene, player-break,
explosion/conflict observation, native crop-growth pre/post fences and output
receipt also read or write that same legacy claim. `ResourceSiteState.replace`
and aggregate validation reject changed layouts; the replacement field claim
cannot be layered beside the legacy one (`claim`/`fieldClaim` are mutually
exclusive). In particular, the native growth fence currently reads `claim`,
so a cell-owned field would reach the wrong owner if enabled alone. The
production cutover must bind the new projector to the live runtime canonical
revision, migrate these consumers and their exact output/custody receipt as
one exclusive owner, and supply bounded publication/backpressure. Do not wire
only `projectCurrentOne` into the registry or relax revision guards to make it run.

### SA-09 live-runtime projection admission boundary — 2026-09-24 (partial)

The inactive growth executor now has `projectCurrentOne`: it rejects a foreign
Minecraft dimension, a runtime not registered to that level's actual server,
and a canonical world other than `frontier:graybox` before selecting the current
site, cycle and exact engine revision. The cell executor remains an internal
`projectAccepted` core for the existing native one-cell fixture; that fixture
does not prove a WAL-backed live revision. Main/pilot/test compilation passed.
The focused native `field-turns` slice passed 11/11 required GameTests in one
corrected 27s run (2026-09-24 12:16 server log), including a negative test for
foreign dimension and an unregistered server-runtime owner. The first attempt
passed the previous ten tests but the new test wrongly assumed the ordinary
GameTest server had loaded the Frontier dimension; that invalid fixture run is
preserved under `build/sa09-field-activation-preflight-20260924/`, and the
negative was corrected without widening product behavior. No native positive
test of `projectCurrentOne` against a registered Frontier server exists yet.
No registry call, claim-mode switch or native product flow was added, so this
does not change the active server or close SA-09. The native-growth pre/post
fence still uses legacy `Claim`; switching the projector before migrating that
and the other consumers would violate single ownership.

### SA-09 cell-owned native-growth fence — 2026-09-24 (partial)

The previously identified native pre/post growth path called legacy
`ledger.claim(site)` unconditionally and would throw as soon as a cell-owned
field was active. It now reads a sealed view of the exact versioned SavedData
claim variant. Legacy behavior is unchanged. A cell-owned crop is vetoed at
pre-event; the optional same-tick post-event undo retains its exact observed
wheat block, CellId and immutable physical owner. It restores only a forced
one-age wheat increment while that owner and an owned farmland/crop reading
still match. A missing, changed-soil, foreign, replaced-owner or stale-tick
observation cannot authorize a repair. The in-memory pre/post window is
bounded and never becomes canonical progress or a persistent crop writer.

The native `field-turns` slice passed 11/11 in one 29s run (2026-09-24 12:27
server log), including actual block-age undo and refusal after soil damage;
the server saved and stopped. This proves the new cell-claim fence seam, not
the full registered active-runtime cutover. First-field INITIALIZING still
vetoes native growth but cannot post-restore an unverified foreign/forced crop;
it remains local physical ambiguity. Player-break, explosion/conflict, HOT
work and output still consume legacy stage/prefix ownership. SA-09 remains
OPEN and no deployment/product acceptance follows.
The focused current-source executor/ledger JUnit selection also passed 18/18
(2026-09-24), retaining legacy stage/prefix behavior and SavedData publication
coverage. No broad matrix was run for this isolated pre/post adapter change.

### SA-09 cell-loss to terminal-output contradiction — source-proven, 2026-09-24

This is an explicit contract gap with a concrete source counterexample, not a
claim that a current client run has already reproduced it. A prepared player
crop-removal event can change one canonical `ResourceFieldCycle` cell from a
live crop to ABSENT (`ResourceSiteProcess.reduceCellObserved`). At that cell,
`expectedWorkOutcome` is PLANTED: the farmer accounts for the visit without
yield, and `ResourceFieldYield.fromCompletedCycle` correctly derives fewer
than 64 units after all 64 visits. The fixed-output terminal reducer now
`ResourceSiteHarvestProcess.finishAfterColdReturn` rejects that yield unless
it is exactly 64, then creates an exact 64-wheat stack. Admission and the
COLD terminal still require the fixed 64-cell shape. The HOT scene
always emits HARVESTED and its physical receipt/output owner still requires one
64-stack. Thus the accepted per-cell loss cannot flow through either complete
production path; changing the final count alone would mint or lose custody.

The player adapter currently calls `observeBlockBreak` → whole-site conflict
instead of submitting the later observed cell result, so the current player
route fails even earlier. The required correction is one coordinated active
path: observed loss/repair, typed HOT/COLD work result, exact positive/zero
yield into bounded actor-hand/depot lots, physical receipt and successor epoch.
Retire the exact-stack terminal in that cutover; do not issue fungible lots
alongside the old 64-stack. Verify one complete ordinary cycle and one crop-
loss/partial-yield cycle, with no second resource authority. No client run or
product result was delivered by this source review.

Follow-up source correction (2026-09-24, partial): the fixed-output rejection
and 64-wheat creation now occur after the worker's retained return corridor,
not inside the final crop receipt. `ResourceSiteHarvestJob` allows post-crop
return edges; COLD advances them one canonical pedestrian edge per event and
closes task, continuation and growth successor in the final-edge transaction.
HOT retains its scene through that corridor and only then enters DRAINING.
An ordinary first HOT ingress during a COLD return tail can now re-admit the
same worker and remaining retained edges; the final station cannot open a new
crop scene.
This removes the old final-crop→field-edge actor snap, but it does **not**
implement actor-held yield, depot handoff, partial/zero output or the active
cell-owned physical claim. A HOT worker with an open scene holds its COLD
action; after the exact scene release, the returned worker can complete the
canonical task without a depot-loaded physical receipt. Harvest-traversal WAL payloads
retain their exact due schedule and the persistence envelope is bumped from
77 to 78 because replay under the former terminal semantics is unsafe; no old
world is silently migrated. Focused cold-receipt, traversal, persistence and
payload-codec JUnit selections passed; NeoForge main compilation passed. No
native client, deployment, partial-yield result or SA-09 closure is claimed.

Further source review exposed a post-HOT dead end at the return station. The
initial COLD hold avoided a same-due scheduler loop but would leave an already
returned worker's canonical task in HARVESTING indefinitely when the depot was
unloaded. An explicit `ResourceSiteHarvestReturned` fact now consumes the exact
due continuation without inventing another movement edge. The terminal
transaction completes the task, cancels that schedule and starts growth. An
unstarted physical intent composes away; a RUNNING one stays as the exact
pending receipt. Focused scheduler/reducer, codec and catalog/traversal tests
pass, and NeoForge compiles. The return station is at the field edge, **not**
the depot service port; the old fixed64 stack still appears in canonical depot
custody without an actor-hand transfer. The chosen bounded-lot/depot cutover
remains necessary. No client or deployment claim follows.

Depot-route follow-through (2026-09-24, partial): the active harvest route now
extends beyond the four local field-exit steps to an authored
`SettlementDepotServicePort` station. The bounded surveyed route compiled for
all 12 current graybox sites. A persisted `firstCropCursor` separates crop
identity from the variable-length delivery leg; fresh admission and HOT rebase
verify the same compiled plan. Snapshot schema 191 and persistence envelope 79
reject older route/cursor bytes without migration. COLD/HOT scene admission
during the delivery leg anchors demand to the canonical worker rather than the
last crop and does not require loading the remote field merely to materialize
that worker. Focused harvest/recovery/persistence and NeoForge scene/executor
unit selections passed; main/pilot/test compilation passed. No native player
run or deployment was performed. This is **movement only**: no actor-held
wheat, hand-to-chest observation, variable yield or active cell-owned physical
claim exists yet. The legacy exact64 terminal remains an invalid substitute
for the required custody cutover, so SA-09 remains OPEN.

### SA-06 hard-crash first-body seam — source review, 2026-09-24 (open)

`ActorFirstAdmissionBoundary` durably records PENDING before calling Minecraft's
`addFreshEntity`. The ordinary entity-storage observer changes it to ESTABLISHED
only after an exact region write and storage sync. A process crash between those
events can therefore recover PENDING with no saved body. The existing tests
correctly forbid another insertion from an empty loaded column, but that is a
safe stop, not a recovery path. Conversely, a saved body may be in another entity
region after movement; readiness of the old handoff chunk cannot prove global
absence. Resetting PENDING to NEVER_CREATED on local absence would permit a
duplicate UUID or divergent physical custodian.

The pilot-only `OfflineActorAbsence` already scans all entity-region files under
the stopped world's session lock and fails closed on unsupported storage. Its
`OfflineActorRecovery` publisher currently rejects *every* first-admission
record and handles a different historical PREPARED/no-history case. It cannot
repair this PENDING window as written. An accepted repair must be a separate,
explicit offline operation, restricted initially to an exact still-PREPARED
ambient actor with no scene/work/transfer obligation. Under the same world lock,
it must verify the canonical world/head/lease, exact PENDING attempted binding,
global saved-entity absence and unchanged custody; durably publish an immutable
absence receipt *before* an atomic SavedData transition to a distinct
proof-backed re-arm state. Ordinary admission may then retry the *same* actor
UUID, never a new identity. Re-entry after interruption at receipt and SavedData
publication boundaries must be idempotent and must reject changed entity files,
canonical head or owner. Saved exact body and foreign/duplicate body are negative
cases. This is a recovery decision, not permission to infer absence at runtime.

The current `SceneExecutor.release` correctly treats a missing indexed body as
an unresolved departure unless an exact retained departure receipt exists;
`SceneReleaseReadiness` checks the canonical and retained columns only to decide
when inspection may proceed. That cannot settle a body saved in a third column
after an abrupt crash. Keep such cases UNKNOWN/local rather than treating the
inspection gate as global proof. The public `fenceDrainingSceneBody` helper is
exercised by tests but is not called by the current production release path;
future acceptance must invoke the actual generic release path and both physical
owners, not infer coverage from helper-level tests. SA-06 remains OPEN. No
re-arm, server change or hard-crash acceptance was implemented by this review.

### SA-06 proof-backed first-body offline re-arm — 2026-09-24 (partial)

The first-admission record now has a distinct `PROVEN_ABSENT` phase and a
retained SHA-256 receipt ID (format 2 loads the old format 1 unchanged). It is
not `NEVER_CREATED`, is not live ownership, and cannot be issued by an empty
chunk. Only the exact previous ambient binding and deterministic UUID may
begin another PENDING attempt. That PENDING intent is durably written before
Minecraft insertion. A synchronous failed proof-backed insertion stays PENDING:
the old all-region scan cannot be reused after live-world activity.

The pilot-only `OfflineFirstAdmissionRecovery` runs under the stopped world's
session lock and reuses the known NBT body-storage absence scanner across all
dimensions. It
requires the named seed, canonical head, ambient lease revision and actor UUID;
the actor must still be ALIVE/PREPARED with its original handoff body and no
scene, job, engineering crew, operation, hive mobilization or physical transfer
custody. Resident and active bioform use the same typed boundary. The publisher
retains the exact canonical before-state, SavedData before/after images,
inspected-file hashes and lease in an immutable receipt. It fsyncs that receipt before
atomically publishing the one re-armed SavedData file. Retry across either
durable boundary checks unchanged inputs and does not submit a WAL command.
`inspect` is read-only; `apply` is an explicit maintenance action, not an
automatic recovery on local absence. Neither was run on a live/diagnostic
server or an existing player world.

Focused `:pale-mirror-neoforge:test` selections for birth/first-admission,
ambient recognition/adoption, the old offline recovery and the new offline
plan/publication/controller passed on current source (2026-09-24). Tests cover
receipt-before-ledger and ledger-after-receipt interruption, retry from actual
snapshot/WAL files, changed entity-file proof, wrong seed/head/lease, an actual
entity-region UUID, the world session lock, unchanged canonical revision,
format-1 history, proof-backed failed insertion, and an active bioform plan.
The initial receipt hash test exposed NBT compound-order instability; the
implementation now hashes fields in a fixed order. The old offline cancellation
plan still rejects all first-admission phases, including the new phase; it is
not a competing recovery owner. This is strong component/file evidence, not a
real process kill across Minecraft's region write nor native post-rearm body
admission. HOT/scene/work/cargo cases and full SA-06 remain OPEN. No deployment.

Native follow-through: the `scene-departure` GameTest slice passed 21/21
required tests in one terminal run (2026-09-24 13:03:48 server log; Gradle
terminal 0, 1m07s). The added real-Villager test consumes a typed
`PROVEN_ABSENT` permission through ordinary ambient materialization, observes
the same deterministic UUID indexed once, retains PENDING plus its exact proof
reference until physical save, and reuses that body on a repeated call. The
test constructs the proof-backed state as a fixture; the separate stopped-world
file test proves publication and scanning. Neither is a combined killed-process
world→offline repair→native resumed-server run. The prior GameTest run directory
was preserved as `frontier-v3-scene-game-test-before-proof-rearm-20260924`
before the Gradle task cleared its own fresh run directory. The new GameTest
server saved all dimensions and stopped. Native post-rearm admission is now
covered at its runtime edge, but full SA-06 remains OPEN.

Scanner hardening after that native run: the stopped-world absence proof now
also traverses compressed `level.dat`, optional `level.dat_old`, and player
data (`.dat`/`.dat_old`) for the target UUID, including nested vehicle/body
NBT, and hashes every inspected file into the receipt. A real entity-region
file is still required; a level/player file alone cannot prove absence.
Focused offline recovery/absence JUnit selection passed 16/16 on 2026-09-24
(six suites, zero failures). This is a pilot-only scanner change after the
native run, not a new runtime materialization claim. Unknown persisted body
surfaces must remain fail-closed; neither this check nor the native fixture
closes the combined killed-process recovery scenario or SA-06.
Static review then found that an empty `.mca` had been accepted as a scanned
region; it now fails as a truncated header. The four focused scanner tests
passed after that correction (`:pale-mirror-neoforge:test`, terminal 0).

### SA-06 scene-departure receipt is not an entity-save receipt — 2026-09-24 (open)

The actual final-unload hook is `Entity.setRemoved(UNLOADED_TO_CHUNK)` via
`FrontierV3EntityDepartureMixin`. `SceneDepartureObserver.observeLeave` records
the current health/position and owner in ambient-carrier SavedData at that
hook. `SceneExecutor.release` accepts a missing indexed body when
`validDeparture` matches this record; it fences the inactive carrier and
submits `SceneLeaseReleased`. Neither `validDeparture` nor the release gate
requires the `EntityStorage.storeEntities` write future and `synchronize(true)`
that the separate adoption/first-admission observer uses. Thus the current
source has no proof that a departure record consumed by release corresponds
to an entity-region write durable across an abrupt process crash. The exact
vanilla timing of unload versus store remains to be established; the missing
proof in this acceptance path is source-visible regardless. A matching receipt
is not by itself evidence of a saved body, and persisting it earlier would not
resolve the cross-file ordering.

Next correction must keep an unload observation distinct from confirmed
entity-storage persistence. Only an exact saved UUID/owner/binding observed in
the actual write, after a completed save pass and storage sync, may become a
durable departure release witness; an unconfirmed or failed write remains
pending/local. The restart seam where the entity file is durable but receipt
publication is interrupted needs a bounded exact recovery disposition, not a
fresh-body guess. Test the generic production release path with both physical
actor and cargo owners, plus negative save-failure/late-body cases. No code
change or SA-06 closure is claimed by this source finding.

### SA-06 scene actor departure save-proof split — 2026-09-24 (partial)

The decompiled local Minecraft 1.21.1 `PersistentEntitySectionManager` confirms
`storeChunkSections` calls `storeEntities` before `unloadEntity` invokes
`setRemoved(UNLOADED_TO_CHUNK)`. The write is asynchronous. The new actor
observer therefore retains only bounded typed saved-body fields from the exact
vanilla write, waits for the completed entity-save pass, write futures and
`SimpleRegionStorage.synchronize(true)`, then compares UUID, scene owner,
lease/revision/epoch, body block position and health to the later unload
receipt. Only that exact match can mark the departure saved in format-6
ambient-carrier SavedData. Format-5 departure receipts load as **unconfirmed**;
they are not promoted by migration. The generic scene release now waits for
a current but unconfirmed actor receipt, while stale/contradictory observations
still reach its conflict path. A confirmed marker is persisted before ordinary
release may consume it. Exact return can withdraw an unconfirmed observation.

Main/pilot/test compilation passed. The targeted departure receipt,
consumption and save-batch suites passed 16/16; they cover missing/old proof,
wrong health, failed write, duplicate saved UUID and exact post-sync marker
publication/reload. One existing consumption
fixture initially failed because it supplied only an unload receipt; it now
explicitly supplies a saved confirmation for tests of post-save consumption.
The `scene-departure` native slice passed all 21 required tests in one terminal
run (2026-09-24 13:27:26 server log; Gradle terminal 0, 1m08s); the server
saved and stopped. The preceding task-owned run directory was moved intact to
`frontier-v3-scene-game-test-before-departure-save-proof-20260924` before this
task's built-in disposable-world deletion.

This native slice is a regression of the shared scene paths, **not** a positive
vanilla unload→actual region write→sync→confirmed release case. Cargo departure
still has its own unconfirmed release seam; the actor path also needs explicit
post-restart recovery when entity save succeeded but marker publication did
not. No killed-process composition or player acceptance was run. SA-06 remains
OPEN; this source is not a release/deployment candidate.
After the native run, the save-batch candidate lookup was made linear in its
bounded inventory and exact UUID duplicates were rejected before receipt
selection. The three focused save-batch tests passed after that change; no
second native run was made for this internal selection correction.

### SA-06 cargo departure save-proof split — 2026-09-24 (partial)

The cargo release path had the same premature handoff: `CargoDepartureObserver`
recorded a final unload receipt in SavedData, and generic `SceneExecutor.release`
could accept it without the corresponding chest-minecart region write. Cargo's
existing `.saved` cleanup marker is a **post-retirement removal** witness, not
a pre-release saved-cart witness, so it cannot be reused for this boundary.

The new bounded `CargoDeparturePersistence` selects only current declared
logistics carts at the exact vanilla entity write. It retains normalized typed
fields, not arbitrary entity NBT: UUID, lease, cargo, revision, epoch, block
position and the 27-slot inventory. A candidate must match one exact unload
receipt, and all write futures plus `synchronize(true)` must succeed before
format-2 cargo SavedData records a saved marker. Format-1 raw receipts load
unconfirmed. Generic release waits for a current but unconfirmed cart; stale
or contradictory receipts still conflict. The unloaded-cart preparation path
synchronously persists the confirmation before the canonical release can
proceed, even after a previous failed publication attempt. Exact physical
return removes the marker with the receipt.

Main/pilot/test compilation passed. The focused cargo departure, scene
departure, scene release and cargo cleanup JUnit selection passed 49/49
across nine suites (Gradle terminal 0, 2026-09-24). The new tests cover old-format unconfirmed data,
malformed markers, exact save/sync publication, changed epoch, failed write,
failed sync, duplicate UUID, malformed item slot and an actual compressed
SavedData file round-trip. No native positive
vanilla unload→nonempty cart write→sync→release or killed-process composition
has yet been run. In particular, the post-save/pre-marker restart window
still requires a bounded exact recovery disposition; neither the narrow tests
nor the new observer close SA-06 or authorize deployment.

Native follow-through for the serialized nonempty-cart boundary: the existing
`cargoDepartureRequiresActualUnloadExactAttemptAndUnchangedContents` fixture
now asserts that the new saved-cart decoder matches the actual serialized
chest minecart, including its nonempty first slot, before the fixture's
synthetic unload/return. The `scene-departure` slice passed 21/21 required
tests in one terminal run (2026-09-24 13:52:34 server log, Gradle 0, 1m08s);
server saved and stopped. The prior task-owned run directory was preserved as
`build/runs/frontier-v3-scene-game-test-before-cargo-saved-nbt-20260924`
before Gradle's disposable-world deletion. This establishes the actual NBT
encoding/normalization of nonempty cargo, **not** a true chunk unload or
hard-crash cross-file recovery proof. SA-06 remains OPEN.

### SA-06 chunk-boundary raw witness and restart-reclaim gap — 2026-09-24 (partial)

Local Minecraft 1.21.1 source confirms `PersistentEntitySectionManager`
calls `EntityStorage.storeEntities` and then each `unloadEntity` callback
within `storeChunkSections`; only after that method returns has the complete
chunk's final-unload inventory been observed. The production save-pass mixin
now asks the physical storage boundary to publish raw scene/cargo departure
SavedData at this `storeChunkSections` return, once per chunk rather than one
fsync per entity. This preserves an exact unconfirmed unload across the common
async region-write interval. It does **not** promote raw observation to saved
proof; the separate exact write-future/sync confirmation still gates release.
Failure to publish is logged and remains dirty/unconfirmed. A crash between
an individual `setRemoved` and the enclosing method's return remains a local
ambiguous window, not permission to invent a departure.

Main/pilot/test compilation and the same 49/49 focused JUnit selection passed.
After preserving the prior task-owned GameTest directory as
`build/runs/frontier-v3-scene-game-test-before-chunk-raw-publication-20260924`,
the native `scene-departure` slice passed 21/21 (server log 2026-09-24
14:01:17, Gradle 0, 1m05s); server saved and stopped. This verifies the
updated mixin wiring does not break that slice, **not** a full actual unload,
post-save/pre-marker restart or latency bound for many departing members.

Source review exposes a larger recovery obligation: `SceneLeaseRestartSafety`
unconditionally changes PREPARED/HOT/DRAINING leases to
`UNKNOWN_AFTER_RESTART`; `SceneExecutor.reclaim` returns without natural
player demand and requires all prior columns/entity storage loaded before it
can inspect the whole body/cart set. Thus even a durable raw receipt plus an
entity file cannot currently authorize unattended COLD resumption. The contract
explicitly says loaded-world historical inspection must not permanently
require revisiting every old projection. Complete SA-06 recovery must provide
a bounded, exact, idempotent stopped-world or no-load region inspection of the
retained raw receipts and canonical owners, then a typed disposition that
releases only fully confirmed physical custody or preserves a local UNKNOWN
with visible reason. It must cover missing raw receipt, failed/old write,
changed owner/inventory/health, duplicate UUID and partial actor/cargo sets.
Do not treat the new raw fsync as that recovery or infer scene closure from a
saved entity alone.

### SA-06 no-load stored-entity inventory — 2026-09-24 (partial)

The entity-storage provider now exposes a read-only `SimpleRegionStorage`
inspection through exact `ServerLevel → PersistentEntitySectionManager →
EntityStorage` accessors. It waits for pending writes via `synchronize(true)`,
then reads one named entity chunk without asking Minecraft to load the chunk
or instantiate entities. The internal selector retains only bounded requested
UUID presence and typed scene-body/cargo snapshots; a present but malformed
target remains **present**, not absence evidence. Misplaced chunk headers and
duplicate serialized UUIDs reject the whole result. The public entry rejects
nonphysical dimensions and empty or overlarge target sets. It does not grant
release, change a lease, or settle return-after-unload ambiguity; caller must
still bind the snapshot to a durable raw receipt, current canonical owner and
no newer write/return before making a recovery decision.

Main/test compilation and the two focused selector tests passed. The native
`scene-departure` slice passed 22/22 required tests (2026-09-24 14:09:08
server log, Gradle 0, 1m05s), including an actual storage-worker read of an
unwritten remote entity region that left the chunk unloaded; server saved and
stopped. The previous task-owned world was preserved as
`build/runs/frontier-v3-scene-game-test-before-stored-read-20260924` before
Gradle's disposable-world deletion. This proves the provider/mixin and
no-load property on the GameTest level, not a physical-dimension positive
stored-body recovery, cross-file crash closure or unattended COLD resumption.
SA-06 remains OPEN.

### SA-06 returned-entity read fence before vanilla deserialization — 2026-09-24 (partial)

Source review of the no-load recovery proposal exposed a stale-proof window:
after a confirmed unload, vanilla could read and return the same stored entity,
allow it to move or change, and an abrupt crash could leave the old departure
receipt looking recoverable. The physical-dimension `EntityStorage.loadEntities`
read is now interposed before its dependent future completes. For a chunk
containing a retained scene-body or cargo departure, the exact serialized UUID
is matched and a return-read marker is synchronously published in the matching
SavedData ledger **before** vanilla receives the NBT. Publication failure fails
the read rather than allowing an unjournaled return. A later exact unload
withdraws that marker but also withdraws the previous saved confirmation;
only a new entity write/pass/sync can confirm the new departure. Ledger formats
7 (actor) and 3 (cargo) also record read-fence coverage; migrated older
receipts cannot become no-load recovery candidates merely because an unrelated
save rewrites their format. Unrelated chunks bypass NBT parsing.

The focused actor/cargo ledger and selector tests passed 18/18, including
exact/unrelated/malformed stored inventories, stale-proof revocation, new-unload
reconfirmation and old-format exclusion. A preceding three-suite focused run
also passed; `git diff --check` passed. This is a safety prerequisite, **not**
an active no-load recovery consumer. The current UNKNOWN scene reclaim still
waits for loaded columns and player demand; in-flight vanilla reads must be
excluded or serialized before any future no-load release decision. The physical
dimension read interception has not yet been exercised by a native killed-
process scenario. No deployment, COLD resumption or full SA-06 closure is
claimed.

Follow-through: the vanilla read wrapper now retains a per-chunk in-flight
reservation from read submission through its server-thread publication and
dependent future completion. A future offline recovery decision can reject
any chunk with a pending read rather than racing the not-yet-published return
marker. The no-load selector also exposes exact typed actor/cart receipt
matching: UUID presence or malformed target NBT is insufficient. Main/pilot/
test compilation and the focused stored-inspection/actor/cargo suites passed
21/21. These primitives are not yet an autonomous recovery decision or native
physical-dimension restart proof; pending-read checks must be wired into that
decision before any lease can be released from UNKNOWN without loaded bodies.
The overlapping-read reservation primitive has a separate deterministic
1/1 test: releasing one of two reads leaves the chunk pending, and another
level/chunk is isolated. This tests bookkeeping, not native interception.

### SA-06 offline recovery snapshot freshness and uniqueness — 2026-09-24 (open)

An exact positive saved-body/cart snapshot can become stale if another vanilla
entity write starts before UNKNOWN recovery commits. The physical entity-write
entry now increments a bounded per-chunk generation before issuing the write.
`StoredEntityInspection.inspect` captures that generation with its no-load
snapshot; its `stillCurrent` predicate rejects a changed generation, index
overflow or an in-flight vanilla read. The three affected focused suites
(write generation, read reservation, stored inspection) passed 5/5. This is
volatile race detection within one process, not cross-file crash proof.

Source review raised a whole-world uniqueness question, not a reproduced
duplicate defect. A receipt's expected saved column alone does not exclude
another stored copy of the UUID. Whether an interrupted vanilla move/save can
produce such a duplicate on this production path remains unproved. Cargo has
a retained footprint archive; scene actors do not have equivalent complete
coverage. Before authorizing unattended UNKNOWN → HOT/DRAINING/COLD recovery,
the selected implementation must establish the exact identity/ownership
conditions required by the contract, reject incomplete or ambiguous evidence,
and recheck write/read barriers and canonical owner at commit. A census is one
possible method, not itself a mandatory new subsystem or proof of a defect.
Without sufficient evidence, retain local UNKNOWN. No offline transition or
SA-06 closure is claimed.

The bounded identity census provider is now present, but not yet a scene
consumer: it enumerates occupied entity-region columns from vanilla `.mca`
location tables (up to 64 files/8192 columns), synchronizes the entity I/O
worker once, reads columns without loading chunks in 64-column batches, and
retains up to two sightings per requested UUID (up to 256 UUIDs). Duplicate,
malformed, misplaced, truncated or over-limit evidence cannot authorize
recovery. A global write generation and pending-read check are available for
the eventual commit-time freshness check. Main/pilot/test compilation passed;
focused header/selector/batch/uniqueness tests passed 5/5. The provider has
not yet been proven on a physical-dimension region inventory or connected to
the whole-scene transition. Do not infer SA-06 closure from its unit tests.

### SA-09 COLD carried-part source cutover — 2026-09-24 (not player-ready)

The active COLD crop reducer now derives each positive unit from the exact
worked CellId/outcome and extends one stable fungible lot in the named farmer's
actor account. Its returned-worker terminal transfers that part to the depot
account only after all admitted cells are accounted; zero yield creates no
lot. The legacy exact 64-stack terminal issuance was removed in the same
source change, and the legacy NeoForge output writer cannot execute without
a corresponding canonical exact item. Focused COLD progression, partial/zero
yield, two epochs/restart and legacy executor tests passed; main/pilot/test
compilation passed. No native/client/deploy evidence exists.

This is an incomplete cutover, not SA-09 closure or a build for player testing.
HOT still replants a crop without incrementing/observing the worker's offhand
or binding its fungible part. A loaded depot cannot yet accept the returned
batch through one physical handoff with before/after receipt, and the old
stage/prefix physical field claim remains the production witness. Those exact
owners must be connected/replaced before a release claim; adding the old exact
output back would duplicate quantity rather than solve physical custody.

### SA-09 HOT hand-observation safety boundary — 2026-09-24 (not player-ready)

The HOT crop event now carries an optional observed actor-hand address, body
UUID, authority epoch and quantity; COLD rejects that physical claim. The
canonical reducer requires the named HOT lease and exact hand quantity before
extending the existing farmer-held lot. The legacy exact-stack harvest receipt
is retired, because accepting it after cell accrual would issue a second 64
wheat. The ordinary scene-release guard retains a farmer with a bound hand.
Payload/WAL envelope 80 and focused harvest, codec and snapshot tests pass;
NeoForge main/test compilation passes. Former positive tests for the retired
exact-stack receipt now assert rejection and persistence of the bound hand.

The production HOT executor still uses the old stage/prefix claim, replants
without incrementing or observing the physical hand, and submits a progress
event without this now-required observation. Therefore a HOT harvest currently
fails closed at that seam. The paired field/hand witness and physical depot
handoff are not wired, and no player/native result or SA-09 closure is claimed.
The next implementation step must connect those active owners, not run another
acceptance campaign against this incomplete path.

### SA-09 active cell-owner admission and COLD projection — 2026-09-24 (partial)

Static source tracing found a newer physical ownership split: initial field
materialization already activates `FieldOwnership`/`ResourceFieldWitness`,
while the harvest admission and ordinary growth/restart tick still requested
the mutually exclusive legacy `Claim`. On a fresh cell-owned field that path
could not reach farmer work. Harvest admission now uses the current canonical
CellId, matching witness/cycle, no pending or foreign cell effect, and a real
loaded-block review. The ordinary field tick dispatches one cell-owned cell per
turn to its canonical-first projector; an older COLD epoch can be rebased only
when the witness has no unresolved effect. That projector now supports a
canonical COLD work result (e.g. mature crop to already-accounted replanted
crop), not only growth, using a retained exact block predecessor and bounded
step observations. The old whole-field restart classifier no longer asks a
cell-owned site for a legacy claim. NeoForge main/test compilation passes.

This is a wired source-path increment, not terminal farmer acceptance. The
physical HOT crop/offhand pair and observed hand-to-depot transfer remain
unimplemented in the active executor; no native/client or release claim.

### SA-09 paired physical HOT cell and hand path — 2026-09-24 (partial)

The active harvest scene now routes a cell-owned pending farmer visit through
`FrontierV3ResourceFieldWorkExecutor`. It persists the physical-first cell
transition and exact hand predecessor before any write, then observes and
persists each loaded-world crop/soil step. Yielding work increments the exact
owned farmer offhand once and confirms that observed count; planting, tilling
and skipped cells do not mint wheat. Only the complete observed result is
submitted as `ResourceSiteHarvestProgressed`, with its exact hand address,
epoch and count. The next scene turn clears the retained physical cause only
after the canonical cell receipt and the loaded physical postconditions match.
The old stage/prefix writer cannot submit this new receipt. NeoForge main,
pilot and test compilation passes. No native behavior or product completion is
claimed: COLD actor stock is not yet physically admitted to HOT, the return
hand cannot yet transfer to a loaded depot, and the legacy terminal/recovery
paths still require one coordinated cutover. Do not deploy this incomplete
source or use a passing compile as SA-09 acceptance.

### SA-09 return custody must retire the fixed-slot job atomically — 2026-09-24

Source inspection found that the generic fungible observation module rejects
`ResourceCustody.Actor`, while `FungibleResourceLedger` already defines the
field-specific observed actor-hand → depot-account transfer. No active field
command uses it. A second independent transfer event is not valid with the
current harvest job: `FrontierWorldState.validateHarvestOutputReservations`
requires its single exact output slot vacant until the job is removed, and the
old physical harvest receipt only accepts one 64-wheat exact stack. Therefore
the physical handoff and removal of that reservation/old intent must be one
source-owned terminal transition (or an explicitly bounded batch transition
that migrates the reservation). The returned worker cannot lawfully release
while its hand binding remains. This is a concrete source-level blocker, not
an observed client failure. The already-acknowledged field cell no longer has
to stay loaded through the farmer's return to the depot; an unresolved effect
still requires a real physical read. NeoForge compilation passes. SA-09 OPEN.

The return boundary is wider than the chest writer. The active
`ResourceSiteHarvestProcess` COLD return moves a positive actor lot to the
depot, but the HOT scene's `atWorkReturnStation` branch moves straight to
`DRAINING`; no command invokes `deliverObservedActorHarvestPart` before that
transition. The old harvest intent names `OUTPUT_ITEM`, the job and retained
lineage name one exact output item/slot, `HARVEST_OUTPUT` has `EXACT_ITEM`
type, and the harvest retirement account both binds that relation and checks
for the exact item after confirmation. `ResourceSitePhysicalIntentStateSupport`
and both physical-observation codecs likewise recognize only the old exact
64-wheat receipt. These are active validation/replay boundaries, not labels
that can safely remain as placeholders for a fungible lot. A valid cutover
must migrate the producer-declared role/schema, job/lineage and relation,
retirement proof, receipt codec and scene terminal release together with the
one capacity-fenced physical hand/depot observation. The actor-held COLD lot
also needs a physical admission receipt before HOT can resume a crop or
delivery. This inventory follows the real production callers; it is not a
claim that any of those transitions has already been implemented or tested.

### SA-09 declared farmer resource account — 2026-09-24 (partial)

The active harvest job now stores the producer-chosen actor-account identity
instead of making COLD/HOT accrual and COLD return rediscover it from the job
ID. Current snapshot/WAL codecs retain the declaration (world-state schema
192, persistence envelope 81); the old formats are rejected. The closed
relationship view exposes `HARVEST_ACTOR_ACCOUNT`, and the physical-intent
retirement account now includes that same edge in its declared job account.
No balance is created for a zero-yield field. Focused harvest, persistence and
relationship JUnit plus NeoForge main/pilot compilation pass. This repairs a
source-level identity/retirement mismatch only. The active HOT scene still
cannot admit a COLD-held hand part or transfer it to the depot; exact-output
intent/job/lineage/receipt replacement, restart and player acceptance remain
OPEN. No new client run or deployment was performed.

### SA-09 terminal handoff composition constraint — 2026-09-24 (source-proven)

The remaining transfer cannot be wired as a handoff event followed by an
independent job completion. `FrontierWorldState.validateHarvestOutputReservations`
requires the active job's selected chest slot to stay vacant. Conversely,
`FrontierSceneBehaviors.ResourceSiteHarvestBehavior` requires an active job for
a HOT harvest lease; only DRAINING/UNKNOWN/CONFLICT may follow the exact
terminal lineage after job removal. The current scene executor goes directly
from the returned worker to DRAINING without a hand/depot effect, while the
legacy receipt executor cannot run because its exact output item no longer
exists. These are directly reachable source contradictions, not hypothetical
client failures.

For the current bounded part, the canonical handoff must publish the observed
actor-to-depot resource transfer, final cell/cycle result, job/intent retirement
and HOT→DRAINING scene disposition in one coherent transition after a durable
physical-before-effect witness has established the actual hand and chest
postconditions. That publication releases the old slot reservation with the
job; it may not expose a filled reserved slot or a HOT lease without its job.
For zero yield, the terminal observation confirms an empty hand and makes no
resource transfer. A future layout with more than one 64-unit part requires an
explicit bounded part cursor/reservation policy, not reuse of one fixed slot.
This is the implementation boundary to build next; none of these new terminal
operations is claimed present yet.

### SA-09 declared depot account — 2026-09-24 (partial)

At harvest admission the producer now records the exact destination depot
account alongside the actor-held account. COLD terminal delivery consumes the
retained target, while the harvest relationship and retirement accounts expose
`HARVEST_DEPOT_ACCOUNT`. Current snapshot/WAL formats 193/82 retain it and
reject earlier formats; the job checks that the declared account matches its
exact depot container and the world-state validator rejects foreign existing
custody. Focused harvest, COLD terminal, relationship and persistence JUnit
passed, including a forged destination; NeoForge main/pilot compilation passed.
The HOT return executor still has no physical hand/depot witness or terminal
transfer. This is one live ownership correction, not a product cycle, native
proof, deployment or SA-09 closure.

### SA-09 harvest intent and lineage account roles — 2026-09-24 (partial)

Static follow-through changed the active harvest intent from the retired
tag-14 `OUTPUT_ITEM` claim to tag 30 with declared actor and depot custody
accounts. The lineage retains those same accounts across COLD terminal and
snapshot/WAL recovery (formats 194/83); active relationship and retirement
facts no longer assert a non-existent exact wheat output. A not-yet-funded
account is a declared prospective subject while the job is active, not a
fabricated balance. Focused harvest, relationship, persistence and NeoForge
adapter tests pass; source, test and pilot compilation pass. This does not
complete the field: the old exact receipt/writer is still inert, and the HOT
hand→depot physical transfer and atomic job/intent/scene terminal are absent.
No native/client run, deployment or SA-09 closure is claimed.

### SA-09 observed terminal reducer and projected-slot collision — 2026-09-24 (partial)

The old exact-output receipt remains rejected. A typed delivery observation
now carries the returned HOT body/lease, farmer and depot account IDs, counted
yield, complete observed depot layout and reference fingerprint. The field
owner's confirmed transition composes the observed actor-account transfer,
job/cycle retirement, intent observation and HOT→DRAINING scene disposition;
positive yield also closes the reference chest's custody mutation. Snapshot
and WAL observation codecs retain the new receipt. This is a domain reducer,
not a trusted native physical producer or player-visible completion.

A focused full-yield reducer regression exposed an existing source bug: harvest
admission selected the lowest nominally vacant exact slot even when the depot's
existing COLD fungible stock projected into that same physical slot. The
observed complete chest then held only 64 units where canonical custody
required 128. The shared slot-selection and active-reservation validation now
exclude currently projected fungible slots, while the test requires the old
stock plus the farmer's 64 units, one atomic terminal successor, reference
closure and snapshot round-trip. The receipt fingerprint also now accepts the
reference family's actual `sha256:` grammar. Focused harvest, traversal,
resource-yield, reference-custody and production JUnit pass; Java compilation
passes. No native/client run or deployment follows. SA-09 stays OPEN: the
source-owned physical hand/chest write-ahead witness, COLD→HOT carried-hand
projection, crash recovery and player-observed cycle remain to implement.

### SA-09 returned HOT farmer physical delivery — 2026-09-24 (partial, unaccepted)

The native source path now has a bounded positive-yield delivery executor
registered before fungible/reference chest observers. It requires the exact
returned HOT worker, one actor-hand binding, complete accounted field, empty
reserved slot and current reference custody; a format-10 site SavedData
witness is persisted before the first Vanilla inventory write. The executor
observes chest insertion, clears the same farmer's offhand, and only then
submits the typed atomic terminal observation. The HOT scene no longer drains
its farmer before this owner closes the hand. While the exact witness is
pending, generic chest observers defer only that depot so the effect's own
restart window is not reported as unrelated drift. A zero-yield cycle submits
an empty-hand observation without a chest write. NeoForge main/test/pilot
compilation and focused SavedData/registry tests pass.

This is **not** native or product acceptance. The physical state machine still
needs an end-to-end native scenario with real chest/body, process crash at
each before/after edge, foreign/player interference disposition and a
successful ordinary cycle. A pending witness encountering foreign evidence
currently waits rather than publishing a typed incident; the chest-before/
hand-after crash combination is unresolved and must not be silently treated
as successful or used to mint stock. COLD-carried actor stock still lacks its
physical HOT admission. SA-09 remains OPEN; no deployment occurred.

### SA-09 COLD-carried hand and terminal liveness source review — 2026-09-24 (partial)

The active HOT harvest scene now observes/projects an already-issued COLD
actor-held wheat part into the same owned farmer's offhand, then submits
`ResourceSiteHarvestHandProjected`. Its domain reducer binds that observed
hand to the existing lot/account under the exact scene epoch; it does not
issue wheat a second time. The focused model command/codec/restart test and
NeoForge compilation pass. At this intermediate point the hand write had
**no durable before-effect witness**; the later format-11 correction below
addresses that provenance boundary, but not every crash disposition.

Static tracing also found two reachable liveness failures in the new delivery
executor. A fully COLD-worked farmer can join HOT on the final return edge
with a `PREPARED` intent; the old scene returned at the depot before its crop
`RUNNING` transition, while delivery demanded `RUNNING` and never began.
Delivery now crosses the durable `RUNNING` boundary at the terminal only
after verifying the exact hand/depot preconditions, before preparing the
chest/offhand witness. A focused COLD-work/HOT-final-edge model regression
establishes that this transition is admitted; the native executor compiles.
Also, one unloaded pending depot previously caused an unconditional return
that starved every other site. The bounded loop now skips waiting witnesses,
executes at most one progressing witness per turn, and excludes pending sites
and depots from fresh preparation. The candidate scan likewise continues past
an unready hand/depot rather than returning before other sites. This is
source-level correction, not native proof.

Remaining SA-09 obligations include the hand-projection crash seam, pending
delivery foreign-evidence disposition and chest-before/hand-after ambiguity,
then a real body/chest lifecycle and player-visible complete cycle. No client
run or deployment was performed; SA-09 stays OPEN.

### SA-09 physical wheat observation must not erase item components — 2026-09-24 (source correction)

The active farmer-hand reader previously reduced an `ItemStack` to registry
kind and count before deciding that it was the worker's fungible wheat. A
reachable modified wheat stack with the same kind/count could therefore be
bound to a canonical lot. The hand reader and terminal depot classifier now
require equality with a fresh, unmodified Vanilla stack; the reference chest
observer classifies modified projected stock as foreign, and generic fungible
observation reports a local container conflict before producing a kind/count
layout. The focused hand-classifier test and NeoForge main/test compilation
pass. This is a source-level custody correction, not evidence that a player
can finish a harvest cycle; SA-09 remains OPEN.

### SA-09 COLD→HOT farmer-hand before-effect fence — 2026-09-24 (partial)

A reachable process-kill boundary existed between placing accounted COLD
wheat in the real farmer offhand and accepting its canonical hand binding.
The active scene now persists one format-11, site-owned SavedData witness
(job, actor account, exact lot, worker UUID, lease/epoch, field epoch/prefix
and quantity) **before** its sole hand write. Unwitnessed wheat is no longer
adopted. On re-entry, the same witness plus the exact observed unmodified
hand can submit the binding without issuing a new lot; a pending witness
with an empty or foreign hand fails closed as a local scene conflict rather
than writing a second stack into an ambiguous crash/intervention window.
The witness retires only after accepted canonical binding, and delivery
cannot overtake it. The SavedData roundtrip/old-format rejection and focused
hand tests pass; NeoForge compilation passes.

This provides an explicit write-ahead owner, **not** complete recovery or
product acceptance. An interrupted write whose entity bytes are absent now
stops locally even if the effect never happened; no proof-backed repair or
human-visible recovery has been established. A hard-crash test with actual
entity/SavedData persistence, foreign-hand intervention test and complete
native/client cycle remain required. SA-09 stays OPEN; no deployment.

### SA-09 first native field-to-depot cycle — 2026-09-24 (observed failure and correction)

Disposable pilot `sa09-harvest-successor-20260924-r6` admitted one real HOT
farmer on site 7 after a COLD zero-player prelude. Its paired cell work
advanced from 0 through at least 61/64 crops instead of immediately entering
the former `legacy-harvest-owner-retired` conflict. This does **not** prove a
complete harvest: at revision 7197 the retained depot leg attempted support
`(135,63,13)` and received `FIELD_ROUTE_BLOCKED_CLEARANCE`. The saved
Minecraft chunk shows `yellow_concrete` at `(135,64,13)`, inside the worker's
feet volume. The pure route compiler's pedestrian survey had used the natural
terrain support one block below a materialized depot service floor; its
immutable obstacle set deliberately exempted the service station. Thus the
route and physical projection disagreed about the same column. The canonical
field conflict then moved a scene with bound wheat in the farmer's hand to
`DRAINING`, where ordinary release was correctly rejected; the unhandled
rejection quarantined the entire development runtime.

The source correction shares the graybox plan's exact public-access surfaces
with `SettlementPedestrianGround.localSupports`, so depot/workshop/infirmary
station floors are surveyed at their materialized support height. A focused
seed-47 route regression rejects the observed under-sill target and passes.
The conflict reducer now keeps a scene with a typed bound actor-hand stack in
local `CONFLICT` rather than attempting generic release; a focused custody
regression passes. Both NeoForge main/test compilation and `git diff --check`
pass. The wider 53-test traversal class still has five pre-existing WIP
failures concerning old exact-output/hand assumptions; its new focused test
passes. Native `r7` then completed the 64-cell field route and confirmed the
delivery intent. Site 7 entered growth epoch 2, reached stage 2 without a
conflict, and the world summary reported `requiredConflicts=0` with a green
diagnostic verdict. The runner failed only at its obsolete
`terminalHarvest.canonicalSuccessor=true` assertion: that predicate still
searches for an exact wheat input, while this path transfers a fungible lot.
It therefore never advanced to its chest oracle or screenshots. The saved
`r7` chest at `(137,65,14)` independently contains 64 bread in slot 0;
action-5 container diagnostics before conversion listed only four tools.
The bread was physically added later in the same run. The disposable scenario
now omits the obsolete exact-lineage assertion and retains its bread/chest,
field-board and zero-conflict checks; it has been validated structurally but
not rerun. The container diagnostic previously listed exact stacks only,
making fungible wheat invisible to operators; it now reports separately
`fungibleOccupiedCount`/`fungibleOccupied` from typed physical bindings.
Focused diagnostic tests pass. These receipts demonstrate one complete
physical harvest-to-bread path but not restart/player-intervention or repeated
human-visible cycles. SA-09 remains OPEN; no deployment.

The next player-intervention cutover is not a one-line event hook. The current
`START_DESTROY_BLOCK`/left-click admission reaches
`FrontierV3ResourceSiteExecutor.observeBlockBreak`, which deliberately rejects
a cell-owned field; the pure `ResourceFieldPlayerBreakPrepared` and
`ResourceFieldCellObserved` reducers already exist but have no native producer.
The implementation must: (1) durably prepare the exact cell/player/action
before Vanilla changes it; (2) observe the actual block after successful,
cancelled or aborted mining, without treating a click as removal; (3) advance
the cell SavedData witness only against that accepted canonical postcondition;
and (4) reconcile a pending permission after restart from the same loaded
physical cell. A foreign postcondition stays local and explicit. Do not bypass
this by allowing the break and guessing AIR, or by interpreting a cancelled
click as whole-field conflict. This source-proven active cutover is the next
SA-09 implementation block; the present run has no player-break acceptance.

### SA-09 pending delivery no longer silently waits on foreign evidence — 2026-09-24 (source correction)

The special delivery witness suppresses generic chest observers while its
own physical effect is unresolved. Previously a foreign chest layout,
changed provenance, foreign hand or chest-before/empty-hand crash split just
returned from the executor every tick, leaving a player-visible active
harvest with no explanation. The pending reader now revalidates the exact
actor-held lot/binding before any write; foreign depot evidence is recorded
through the reference replica conflict owner, and the harvest scene enters
an explicit local conflict with a reason in its diagnostic trace. The
chest-before/empty-hand split is treated as ambiguous, never as proof of
delivery or permission to re-mint wheat. Focused existing harvest/ledger/
hand tests and NeoForge compilation pass. A native intervention/crash test
must still demonstrate the physical observation and player-visible reason;
no full cycle or SA-09 closure is claimed.

### SA-09 native player crop removal — 2026-09-24 (narrow positive)

The previous player-cutover gap now has an active owner path. A normal
`START_DESTROY_BLOCK` persists `ResourceFieldPlayerBreakPrepared` in the
canonical WAL, then a bounded one-per-site physical witness before Vanilla
may change the crop. Actual `destroyBlock` completion or aborted/no-op mining
reads the loaded crop and soil; the exact `ResourceFieldCellObserved` result is
accepted before the per-cell SavedData claim advances and the action retires.
Recovery handles the prepare-before-witness and physical-before-canonical
splits without loading a chunk or treating the click itself as crop loss.
Vanilla's real mining/delayed-destroy state, rather than a 20-tick guess,
controls whether a live player action is still pending. On first COLD ingress,
an older-but-owned visible crop is first projected through the existing
canonical-first per-cell growth owner; otherwise its physical stage would
not match the prepared canonical predecessor and admission correctly rejects.

Focused field-witness/cell-claim JUnit and NeoForge main/pilot/test compilation
pass. The disposable ordinary-client scenario
`disposable-sa-player-crop-continuity.json` completed on private port 26575:
the server logged an accepted `resource_field_cell_observed` transaction at
revision 544; client observed AIR after a two-second settle; site 4 remained
`GROWING` and `status=ok`; a graceful save/restart returned to the same site
with `status=ok`, no site conflict and three retained screenshots. Manifest:
`build/sa-player-crop-continuity/manifest.json` (`status=ok`). The earlier
candidate failed only a harness trace assertion that queried no trace view;
the final declaration performs `inspect trace` before that assertion. Earlier
pre-action attempts exposed coordinate/display assumptions, not crop results.
An independent read of the saved pre-restart failed-oracle world
`v3-disposable_sa_player_crop_continuity-dcdbfc7c` found site 4's active
cell-owned SavedData witness at epoch 1 with CellId 64 committed to
`FARMLAND/ABSENT`, no pending player-break witness, and the actual saved Anvil
block at `(384,64,-346)` equal to `minecraft:air`. This confirms the durable
pre-restart physical/canonical pair, not the exact block after recovery.

This is a narrow player-action and restart-liveness receipt, not complete
SA-09 acceptance. The final scenario did not assert exact AIR after restart;
it checked site continuity and before-restart physical AIR. It also does not
prove a following non-yielding harvest cycle, dirt repair, arbitrary layout
revision, hard-crash recovery, repeated player-visible cycles or release
readiness. Those remain explicit work, not inferred from one green manifest.

Follow-on static path check: for the present 64-cell field,
`ResourceFieldCycle.expectedWorkOutcome` maps an observed missing crop to
`PLANTED` (no yield); `FrontierV3ResourceFieldWorkExecutor` writes the exact
new crop without incrementing the farmer hand;
`ResourceSiteHarvestProcess.reduceProgressed` preserves that bound hand; and
`FrontierV3ResourceSiteDeliveryExecutor` admits a positive partial quantity.
`ResourceFieldYieldTest` covers 63/64 output at the model boundary. This is
source coherence, not a native 63-unit terminal receipt. The same inspection
confirms a distinct accepted-layout obligation remains: the HOT hand/result
path uses the total `cycle.harvestedCount()`, `currentCarriedLot(..., 0)` and
terminal delivery explicitly refuses `quantity > 64`. A 65+ cell layout
requires the planned intermediate bounded batch delivery/return protocol; it
cannot be declared supported by the existing arbitrary-size layout value
type or by the 129-unit model-only yield test. SA-09 stays OPEN.

### SA-09 player crop loss through a partial COLD harvest — 2026-09-24 (targeted native evidence)

The disposable `disposable-sa-player-crop-partial-harvest.json` scenario used
an ordinary client to break site 4's last crop, gracefully saved/restarted,
then observed that same cell as AIR after restart. The first attempt stopped
at a bad test-only far-visit height (`y=65` where the client correctly stood
at `y=64`); its failure bundle is retained under
`build/sa-player-crop-partial-harvest/manifest.failure`. With the visit
corrected, the second attempt completed the 24,000-tick unloaded-field
advance. Site 4 reported `status=ok`, `phase=GROWING`, `growthEpoch=2`, no
conflict and a resolved COLD terminal at instant 29,624. Its container
diagnostic reported a current fungible wheat binding of 63 in slot 0 with
`OBSERVED_CURRENT` replica and current owned physical slots. Independently,
the saved Anvil chest block entity at `(377,65,-326)` in the retained world
`v3-disposable_sa_player_crop_partial_ha-eddb293c` contains exactly
`minecraft:wheat ×63` in slot 0. The saved Anvil field block at the exact
player-removed `(384,64,-346)` is wheat age 3 in the new epoch: the farmer
replanted the loss, and subsequent growth continued. This is direct product
evidence that one player-removed crop neither blocked the next cycle nor
minted the missing unit; it does not prove a later second harvest, dirt repair
or crash recovery.

The second runner was red at its final `wait_until_container_item` despite
that saved physical result: `FrontierV3PilotDiagnosticMatcher.containerContains`
searched only the old exact-item `occupied` list and ignored the new
`fungibleOccupied` bindings. The matcher now admits an exact fungible slot
only when the replica is `OBSERVED_CURRENT` and the owned physical chest's
slots are `CURRENT`; focused positive, wrong-count/slot and stale-replica/
slots tests pass. This is a corrected test oracle, not a product repair.
No third full-world run was spent merely to turn the already-observed 63-unit
result green. The declaration remains checked in for a later combined
integration gate; its current manifest at
`build/sa-player-crop-partial-harvest-r2/manifest.json` is still `failed`
and must not be presented as a green scenario. SA-09 remains OPEN for
arbitrary 65+ batch delivery/layout revisions, dirt/obstruction recovery,
hard-crash seams and full product acceptance.

### SA-09 follow-on static discriminator: partial wheat is not a production-selector defect — 2026-09-24

The earlier field-lifecycle blocker describing a single-lot bread input was
historical, not the active production path. `ProductionProcess.planStart` now
uses `FungibleResourceCustodySupport.selectAtContainer`; its deterministic
selection and `ProductionResourceCustody` claim/hold retain exact portions from
multiple lots. The focused selector regression now uses the native-observed
partial shape (63 wheat plus a later 1), rejects the legacy single-lot query
as an oracle for production, and proves that an existing claim prevents
over-allocation. The selected `:pale-mirror-frontier:test --tests
io.farfrontier.palemirror.frontier.v3.model.FungibleResourceCustodySupportTest`
passed. A lone 63-unit depot lot cannot fund a 64-unit bread recipe and is
expected to wait for more wheat; this is not a stuck selector. The canonical
field-lifecycle note was corrected. No production source change or new native
acceptance is claimed; 65+ bounded harvest traversal, batch custody and layout
revision remain the active SA-09 implementation gap.

### SA-09 local soil/crop observation is a producer gap — source review, 2026-09-24

The cell model and reducer accept exact `SOIL_BECAME_DIRT` and `CROP_REMOVED`
world observations. The active NeoForge producer is narrower:
`FrontierV3ResourceFieldPlayerBreakExecutor` admits only a declared crop block,
and `FrontierV3ResourceSiteExecutor.observeBlockBreak` rejects other breaks in
a cell-owned field. `projectOneGrowthStage` calls the one-cell growth projector,
which reports an unwitnessed owned physical difference as `PHYSICAL_CONFLICT`;
it never submits a world cell observation. Thus the model's local repair
transition is not an active server behavior for externally changed soil or
crop. Native managed-soil reversion is separately blocked, so ordinary
trampling/drying is **not** a reproduced dirt defect in the current build.

Any producer correction must first identify an actual post-Vanilla transition
on a naturally loaded claimed cell, retain its exact physical predecessor and
postcondition durably before the canonical observation, and reconcile the
physical-before-WAL and WAL-before-claim-update crash windows. It must not
interpret a COLD projection lag, a pending farmer/player effect or a foreign
block as world-caused crop loss, and must not let the growth projector overwrite
the observed change. A model-only `ResourceFieldCellObservedTest` or simply
submitting `Source.WORLD` after a mismatched read cannot close this gap. No
producer was changed or native repair result claimed by this review.

### SA-09 bounded world-cell observation producer — 2026-09-25 (source/test partial)

The active field projection tick now checks one naturally loaded, already
cell-owned physical cell before projecting it. It classifies only an exact
farmland/crop → farmland/air crop loss or farmland → dirt/air soil change when
the canonical cell and retained physical predecessor agree. A pending player
action, farmer crop effect, physical projection, foreign block, or COLD
projection lag is not relabelled as world damage. The executor persists an
exact per-site physical before/after witness before submitting
`ResourceFieldCellObserved(Source.WORLD)` to the canonical WAL; after acceptance
it updates the field claim and retires the witness. On restart, it reconciles
the exact saved witness before taking new field projection work, including the
physical-before-WAL and WAL-before-claim-update states. The field claim cannot
rebase a later COLD epoch while this witness remains unresolved. Older
format-13 ledgers without the additive witness section load as empty.

`FrontierV3ResourceFieldWitnessTest` and
`FrontierV3ResourceSiteCellClaimTest` pass for classification, immutable claim
acknowledgment, witness serialization and competing-witness rejection; Java
main/test compilation passes. This is **not** native or product acceptance:
ordinary managed-soil reversion remains vetoed, no real-world damage/restart
scenario has exercised the new producer, and a stale canonical epoch while a
world witness is unresolved still needs an explicit recovery/hold decision.
The existing whole-site loaded/demand gate also limits which changed cells can
be observed promptly. SA-09 remains OPEN; no deployment follows.

The bounded native positive is now
`build/frontier-v3-scenarios/disposable-sa-field-world-crop-loss-1790288173516.json`
(`status=ok`). In a fresh 65-cell fixture, the client first waited for the
read-only `field_physical` view to report an `ACTIVE` exact field owner and a
current first-cell claim, then `/setblock` removed that real wheat block. The
native trace retained `resource_field_cell_observed` at revision 35. The first
cell's saved claim became `FARMLAND/ABSENT/0`, its world-change witness retired,
and the site stayed non-CONFLICT. A graceful server save/restart with the same
client JVM returned the same `ACTIVE` physical claim and absent world block;
the runner performed exact-owned terminal cleanup. This proves one already
owned crop-loss path and ordinary restart persistence, **not** soil-to-dirt,
unloaded-cell detection, a crash inside either write boundary, later farmer
repair or all SA-09 cycles. Two earlier attempts were test-method failures:
missing Xvfb, then changing the block before field activation; a further run
observed the correct product result but was deliberately stopped after a
terminal assertion requested a diagnostic at a step with no such request.
The corrected scenario has one green terminal result; do not rerun it merely
for reassurance.

The distinct soil-damage scenario is also green at
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-damage-1790288982060.json`
(`status=ok`). After the exact field claim reached `ACTIVE`, a real dirt block
replaced the first cell's farmland and the wheat above became air. The server
accepted `resource_field_cell_observed` at revision 103; the retained first-cell
claim became `DIRT/ABSENT/0` with no pending world witness. A graceful server
save/restart and same-client reconnection retained that claim and the dirt
block. This is a narrow positive for observed soil damage and ordinary
restart, **not** proof of later `TILLED_AND_PLANTED` farmer work, a hard-crash
boundary or arbitrary off-screen damage. An earlier attempt failed before the
action because its setup asserted the fixture's initial job after that job had
already completed during client startup; the stable precondition is the exact
field owner/cell, not a timed job ID. Both crop-loss and soil-damage scenarios
now use that precondition. The corrected soil scenario has one green run; no
repeat is required for this claim.

Static recovery finding remains open: `FrontierV3ServerLifecycle.tick` performs
one physical turn before ordinary canonical advancement, but
`FrontierV3ResourceSiteExecutor.projectOneGrowthStage` admits field-cell
observation only when the *whole site* is naturally loaded and projection is
demanded. `FrontierV3ResourceFieldWorldChangeExecutor.reconcileOne` cannot
resolve a saved world-change witness without its two loaded blocks, while the
canonical harvest can later execute `ResourceFieldCycle.nextEpoch` in COLD.
Once epochs differ, `change.matches(cycle)` returns false and the field claim's
rebase is blocked by that pending witness. This is a source-proven possible
indefinite local stall, not a reproduced crash incident; the one-cell native
tests above did not exercise it. A correction must preserve the original
physical cause and prevent the affected site's COLD cursor/epoch from outrunning
an unresolved witness, without stopping unrelated sites or relabelling an old
effect as a new-epoch event. Do not 'fix' this by blindly accepting stale
epochs, deleting the witness, force-loading chunks or merely raising a timeout.
The immediate cross-site liveness error is now corrected in source:
`reconcileOne` skips a stale or locally unreconcilable witness and can still
process another site's loaded, current witness in the same bounded pass. The
focused field-witness and cell-claim tests plus NeoForge compilation pass.
This does **not** resolve the stale site's own witness. The source-traced
repair boundary is a WAL-backed per-site recovery hold: on restart the saved
physical witness must register its exact cause before the site's next COLD
growth/harvest action, even if its chunk is unloaded; an exact loaded-world
before/after observation later cancels or confirms that hold. The hold cannot
be an adapter-only scheduler predicate because deterministic canonical replay
must see the same retained state. The initial physical observation path also
needs a bounded naturally-loaded cell admission independent of whole-site
presentation demand, otherwise an unobserved world change can still precede
creation of the witness. No such hold or broader admission has been implemented
or claimed yet.

### SA-09 per-site world-change recovery hold — 2026-09-25 (partial)

The preceding open recovery finding now has a source correction. The exact
SavedData world-cell witness is followed, before the next ordinary canonical
tick, by a WAL-backed `ResourceFieldWorldChangeHeld` event. The canonical
site state retains the original site/epoch/layout/CellId/before/after/cause,
including across snapshots; growth and COLD harvest of that site are held
while other sites may advance. The hold is installed even when the changed
cell is unloaded. A matching loaded-world predecessor cancels the change;
a matching successor permits the original cell event. The hold remains until
the physical field claim and world cell agree with the canonical result and
`ResourceFieldWorldChangeAcknowledged` is accepted. If the SavedData witness
was retired before that final command, the retained hold drives a bounded
read-only orphan reconciliation after restart. The snapshot/envelope schemas
are now 200/88 and reject previous encodings.

Initial world-cell observation is no longer hidden behind whole-site loaded
projection or presentation demand: the physical turn checks one naturally
loaded cell per currently owned site, per tick, with bounded per-site cursors.
It never force-loads a chunk. A stale or unreconcilable witness of one site
does not starve a second site's current witness. Model hold/ack/codec and
current-format recovery tests plus NeoForge compilation pass. One ordinary
client soil-damage scenario is green at
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-damage-1790290411299.json`:
real dirt/air reached the canonical cell, the physical claim became
`DIRT/ABSENT/0`, and both persisted through graceful restart. This run does
not inject a crash between SavedData, WAL, claim, and chunk writes, nor prove
single-chunk/partial-site observation or a complete farmer repair cycle.
After that run, static review removed a permissive no-hold/equal-block
reconciliation branch and made rejection of a persisted witness's exact hold
fail closed before ordinary COLD advancement; targeted compilation and
model/persistence tests passed, but this negative-only source increment was
not exercised by a second native run.
SA-09 remains OPEN. An unobserved physical change occurring before its first
durable witness still needs a separate causal boundary; no claim is made that
the new scanner makes that window impossible.

### SA-09 pre-write world-cell boundary and legacy blast split — 2026-09-25 (scoped)

Source review found that a one-cell-per-site physical scanner still left a
pre-witness interval: a real block write could complete before the next scan,
allowing this site's COLD cursor to advance first. The active `Level.setBlock`
four-argument path now enters `FrontierV3FieldBlockWriteMixin` before Vanilla's
write. For an ACTIVE exact cell owner, already-current physical crop/support and
no competing player, farmer, projection, world or blast effect, the adapter
persists the exact before/after world-cell witness and accepts its canonical
per-site hold before the write. The existing scanner remains a backstop for
already changed loaded cells; this pre-write path does not force chunks to load.
The index is bounded to declared field-cell chunk columns and one runtime;
runtime retirement drops it. The current bootstrap layout is pinned, so any
future live layout migration must rebuild this index at the same durable
boundary as its claim/route migration.

One focused native pre-write diagnostic is green at
`build/frontier-v3-scenarios/disposable-sa-field-prewrite-diagnostic-1790291819028.json`.
The trace records accepted `resource_field_world_change_held:prewrite` before
the corresponding accepted `resource_field_cell_observed`. The corrected
ordinary soil-damage/graceful-restart scenario is green at
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-damage-1790291932385.json`:
an ACTIVE first cell became real DIRT/ABSENT, retained the matching physical
claim across restart, and had no pending world witness. Two intermediate runs
failed for test-method reasons after the product action: an unnecessary exact
feet-position revisit on reconnection, then an assertion for a diagnostic the
pilot had not requested. The final scenario removes the revisit and explicitly
waits for the needed trace; no extra confidence rerun is warranted. These
receipts prove ordinary `/setblock`/graceful restart, **not** a hard crash,
direct chunk mutation, an actual explosion or subsequent farmer repair.

A separate reachable static defect was found in blast capture: the legacy
`FrontierV3ResourceSiteExplosionLedger.candidate` asked for `claim(site)` on
every site *before* checking whether any blast block touched it. `claim(site)`
correctly throws for a current cell-owned field, so even an unrelated blast
could stop capture. Capture now filters to an actually affected site first and
only uses the legacy stage/prefix candidate for a `LegacySiteClaim`. The
generic blast scar filter now recognizes ACTIVE `CellSiteClaim` without calling
the legacy API, preserving the field as the owner of its crop/soil slots. The
cell-owned ordinary block-write path is responsible for its exact supported
crop/soil losses. NeoForge main/test compilation and focused field-witness/
cell-claim JUnit pass. No native blast result is claimed: a blast that destroys
soil into AIR or another foreign postcondition is not yet an admitted local
repair event and must be reviewed as an explicit field conflict/incident, not
silently treated as a crop-loss success. SA-09 remains OPEN.

The exact source gap is now localized: `ResourceFieldCycle` already has
`cropObstructed`/`soilObstructed`, but `ResourceFieldCellObserved.Change` admits
only crop removal, soil-to-dirt and unchanged. The active world-cell producer
returns without an event for `FrontierV3ResourceFieldObservation.Foreign`, and
`projectOneGrowthStage` currently discards the cell projector's
`PHYSICAL_CONFLICT` result. Thus a blast that replaces/destroys support can
remain physically visible yet canonically unreported while off-screen COLD
continues. Fixing this requires one exact before-effect/after-effect foreign
observation, its durable hold and a typed local obstruction outcome; a blanket
whole-site conflict or generic scar alone does not meet the accepted field
contract. This is a reachable source inconsistency, not a reproduced native
blast incident. Preserve the narrowly corrected legacy split while adding
that local path; do not call the split full explosion support.

### SA-09 delivered-batch cursor cutover — 2026-09-24 (partial)

The active harvest job now retains `deliveredYieldQuantity`; the model bounds
it to full 64-unit parts already within the completed cell cursor. The current
hand quantity is `cycle.harvestedCount - deliveredYieldQuantity`, and all
active crop accrual, COLD hand projection, native offhand observation, HOT
terminal chest transfer and canonical terminal settlement use that same
retained offset instead of independently assuming zero. The start WAL payload
and world snapshot carry the cursor under envelope 84 / snapshot schema 195;
old formats reject. Fresh admission still starts at zero. A focused 65-cell
value/payload regression proves a delivered 64 plus a carried 1, rejects a
63-unit delivered cursor and an over-capacity hand. Selected progress,
yield, persistence, harvest-model, scene-executor and one current HOT
hand-to-depot terminal regression pass; NeoForge main compilation passes.
The `ResourceSiteState` aggregate now rejects a delivered offset that outruns
the actual yielded cells or leaves an over-capacity current hand, including a
completed prefix with non-yielding cells. The focused snapshot/negative
regression passes; it is a persistence invariant, not an intermediate delivery.

This is **not** intermediate batch delivery: no active event advances the
cursor, no return-to-field route is retained after a full-batch depot handoff,
and the production layout gate still admits only the present 64-cell graybox.
It must not be deployed or called arbitrary-field support. The broader
`ResourceSiteHarvestTraversalTest` selection has five older tests expecting
the retired exact-item output or an invalid >64 hand; those source-obsolete
oracles remain red and need semantic replacement at the corresponding
integration step, not timeout/retry treatment. Two persistence assertions
for the old schema numbers were updated and are green. SA-09 remains OPEN.

Static batch-transition constraint: `ResourceFieldYield.nextReadyLot` allows
a nonterminal transfer only for a full 64-unit part, while the current job's
topology contains every crop before its depot tail and `atWorkReturnStation`
is reachable only after all crops. Therefore admitting 65 cells by relaxing
the layout gate would deterministically attempt a 65th hand unit, not stream
the first part. A correct next cut must retain an exact bounded current route
segment; at a full hand, route that same worker to the depot, durably settle
the physical/canonical part once, then resume at the next stable CellId with
the cursor advanced. Segmentation must also handle >4096-cell no-yield
stretches without skipping cells, since the topology cap is 4096. The active
job's reserved slot must migrate or be released atomically with the part:
world-state validation requires it vacant while the job stays active, whereas
the confirmed part occupies the slot. Storage exhaustion must hold the real
bounded hand, not mint output or retire the field. No such transition is in
production yet.

### SA-06 patrol no-visit revocation can discard physical injury — source-confirmed, 2026-09-24

Precondition: a route-patrol scene has entered HOT with a real retained mob;
the mob takes nonfatal Vanilla damage before an abrupt restart. The old-source
combat facade does not claim a V3 carrier (its actor adapters require their
separate encounter tags), and `LivingDamageEvent.Post` has no V3 canonical
health reducer. The scene release path normally captures the mob's current
health, but `FrontierV3RoutePatrolSceneExecutor.recover` has a different
no-demand path: after a volatile grace it submits `SceneLeaseRecoveryRevoked`
without inspecting any stored actor body or saved departure. Its pure reducer
closes the lease and revokes the body fence while retaining the pre-injury
canonical `ActorLocation`. The source join firewall correctly rejects the late
closed-lease body, so it prevents duplication but cannot recover the injury.
Thus this is a concrete loss-of-physical-effect path, not the speculative
duplicate-UUID hypothesis. The prior `RoutePatrolSceneSupportTest` covered only
lease/fence status, not changed health. No kill-process reproduction is claimed.

Correction boundary: do not authorize no-visit revocation merely from elapsed
time or absence of player demand. The recovery owner must inspect the exact
saved actor snapshot (health and binding included), prove the full member set
and current uniqueness/return/write fences, then publish one typed canonical
handoff/release disposition before rejecting an old physical body. An exact
never-created first-admission proof may authorize a separate no-body path;
ordinary UNKNOWN or a missing loaded entity may not. If evidence is incomplete,
retain a visible local UNKNOWN and no new body rather than discard injury or
forge COLD continuation. This is part of SA-06, not a new scene family. The
inactive no-load selector/census may help, but their existence is not proof
that this production path consumes them. Preserve liveness as an exit gate;
simply deleting the revocation command would exchange lost effects for a
permanently stalled unattended patrol.

Source correction in progress: the route-patrol executor no longer submits
the timer-only revoke, and command planning rejects new
`SceneLeaseRecoveryRevoked` requests. Its reducer/codec remain for historical
WAL replay; this is not a schema migration or permission to rewrite prior
events. A new active no-demand owner instead requires current saved-departure
markers with return-read coverage for every living member, one bounded
whole-world UUID census, exact typed no-load body snapshots including health,
and fresh entity-write/read stamps. Only a complete current set can transition
UNKNOWN→DRAINING and invoke the ordinary generic release, which publishes the
saved physical health to the canonical actors and resumes the retained patrol.
Missing, stale, duplicate or changed evidence remains locally UNKNOWN, with at
most two scans per lease and no force-loaded chunk. The previous blind command
is rejected by the focused patrol regression; a new two-member proof test
rejects missing/duplicate UUIDs and changed health, and a model regression
shows exact release carries 18-HP injury into canonical state. Focused patrol
and stored-recovery JUnit plus NeoForge compilation pass. This is source-level
composition, **not** a native unload/restart/reader-race or hard-crash proof.
The full generic SA-06 scene/cargo path, first-admission composition and
physical-dimension acceptance remain OPEN; do not deploy or call SA-06 closed.

### SA-09 segmented field-work and part handoff — 2026-09-24 (source WIP, not admitted)

The job now retains a bounded segment range, exact per-cell route cursors, a
full-hand depot-return phase, and an optional reserved successor chest slot.
The segment compiler can build an approach, ordered workstations and depot
tail within the route-node and 64-unit hand limits, backing off the segment
length instead of skipping cells. COLD and HOT reducers can renew an exhausted
non-full segment. A full nonterminal part has separate COLD and observed HOT
delivery transitions; the latter keeps the same job, farmer, RUNNING intent
and HOT lease while atomically moving the part, output reservation and route
to the next segment. The native delivery witness persists the physical
chest/hand before-effect and requires a canonical successor-slot reservation
before writing either surface. Snapshot 197, WAL envelope 86 and site SavedData
format 13 reject older layouts. Current-64 focused harvest, cursor/persistence
and native-adapter unit selections pass after this increment; Java compiles.
A synthetic 65-cell job regression also confirms that a full first part returns
the same worker to the depot, reserves a different slot, then resumes at stable
cell 64 with a zero hand; it is model evidence, not a live field admission.
Further source review found that the domain crop transition trusted the caller's
station check: a pending crop could be constructed with a pre-arrival cursor,
and `advanceHarvest` did not itself require the named workstation. The job
constructor now rejects an off-station pending crop; preparation and progress
transitions require the exact current crop cursor. Focused positive COLD work
and negative off-station tests pass. This closes one authority gap in both the
current 64-cell path and segmented work, not the missing revision producer.

Static review found one exact admission error and corrected it: a COLD-worked
full hand can join HOT only at the depot with a PREPARED intent, but the new
batch-reservation reducer requires RUNNING. The native delivery executor now
publishes that durable RUNNING boundary before requesting batch capacity.
This path still lacks a real 65+ canonical layout revision/retirement producer:
`ResourceSiteState.validate` requires bootstrap geometry, and the active
`supportsCurrentHarvest` gate still deliberately admits only the 64-cell
revision-1 graybox. Consequently none of the batch reducers has a player-
reachable 65+ proof yet. A returned HOT hand also cannot be released to COLD
while physically bound stock remains; if the depot effect cannot finish before
unload, ownership stays HOT/UNKNOWN rather than silently minting a COLD part.
Do not relax the layout gate, deploy, or claim arbitrary-field/player support
until that authority boundary, restart seams, bounded storage pressure and
native/client cycle are verified. SA-09 remains OPEN; SA-06 is unchanged.

Static cross-owner correction: the first intermediate HOT reducer inserted its
batch receipt into `physicalObservations` while leaving its harvest intent
RUNNING. The global receipt union admits an observation only when the matching
intent is CONFIRMED and points to that one terminal observation ID; thus the
intermediate reducer could not publish an aggregate state at all. The batch
event still carries its full physical receipt in WAL, but its reducer now
retains only the latest accepted batch receipt in the active job (bounded
restart witness) and does not insert it into the terminal observation map.
The native SavedData witness retires only against that exact job, delivered
offset, receipt ID, witness ID and depot fingerprint. Job, snapshot and start
payload codecs retain this field; snapshot 198 and WAL envelope 87 reject old
layouts. Focused snapshot/negative cursor and harvest tests plus Frontier and
NeoForge compilation pass. No 65+ aggregate/native run has yet exercised the
reducer, so the transition remains source WIP, not accepted behavior.

Another independent 65+ admission barrier was source-confirmed: preparation
receipts required exactly 64 soil/crop slots and encoded each count in one byte;
the native confirmer emitted literal `64,64`. The receipt now holds bounded
layout-sized counts, the native producer emits the actual initial site counts,
the reducer checks them against its canonical layout, and retained observations
check the immutable bootstrap layout. WAL/snapshot codecs use integers under
the same new versions. A 65-cell codec regression passes and mismatched/zero
counts are rejected. The fresh-world producer remains the fixed 8×8 graybox;
this removes a blocker but does not supply a revised or initially larger field.

### SA-09 explicit fresh-field manifest and admitted 65-cell COLD cycle — 2026-09-24 (partial)

The immutable fresh-world bootstrap can now carry an explicitly authored
revision-1 layout per exact site. The source compiler checks its occupied
footprint, bounds and workstation coordinates against the same structural
reservations as the default field; unspecified sites retain the original 8×8
producer. The snapshot body persists this manifest, the process-local genesis
cache keys on it, and a pinned runtime rejects a different manifest before
mutable hydration. Snapshot schema 199 rejects schema 198. The new runtime
entry accepts the exact bootstrap; default production genesis is unchanged.
This is **not** a mid-world layout revision or permission to reuse retired
CellIds: revision 2 still fails the existing aggregate guard.

Harvest admission retains the historical 64-cell route for default graybox
worlds but uses the bounded segment compiler for other admitted revision-1
layouts. Rebase and admission validation compare the exact segment range and
per-cell route cursors. One 65-cell fresh-world aggregate test starts a real
strategic harvest job, works 64 COLD cells, returns the same farmer and deposits
one full part, snapshot-recovers at the part boundary, works cell 65, returns
and deposits the final one-unit part. The site enters its next growth epoch and
the depot increases by exactly 65 units. A foreign pinned genesis rejects that
snapshot. `ResourceSiteHarvestProcessTest` and `FrontierPersistenceCodecTest`
pass; Frontier and NeoForge Java compilation passes. An initial assertion of
129 versus 65 was corrected after inspecting the depot's preexisting 64 units:
the actual increase is 65, not duplicated yield.
An authored field whose first workstation is unreachable now produces a local
BLOCKED harvest task at route compilation instead of throwing from the
scheduled planner; the negative case passes in the same focused test.

This source/model proof does **not** show native creation/projection of the
65th cell, physical 64+1 delivery, crash recovery, occupied-depot backpressure,
dynamic grow/shrink or player-visible continuity. The active server still
creates only the default 8×8 genesis. Do not deploy this WIP or close SA-09.

### SA-09 first 65-cell native admission — 2026-09-24 (partial, not terminal)

A test-only `resource-site-harvest-65` profile now uses the authored fresh
revision-1 manifest and ordinary harvest ingress. The native diagnostic reports
the actual 65-cell count, delivered/carried yield and segment range instead of
the retired literal-64 total. The first disposable run let canonical COLD time
advance during client startup, so its `completed=0` assertion was invalid; it
is retained as a failed test-method receipt, not a product failure. A held-clock
retry admitted the worker HOT, but then produced a real
`legacy-harvest-owner-retired` conflict at revision 55 before the first crop.
Source trace found the cause: startup `reconcileOneAfterRestart` runs before the
ordinary cell projector and, for a claimless neutral field, invoked the old
stage/prefix projection even in a fresh 65-cell world. The startup recovery
branch now relinquishes only an exact neutral, unclaimed field to the durable
cell initializer; non-neutral or ambiguous surfaces still require their own
restart classification. `FrontierV3ResourceSiteExecutorTest` and compilation
pass. A subsequent isolated native client entered HOT and completed 16
physical crop/hand receipts with no owner conflict before the disposable run
was gracefully stopped. This is positive cell-owner ingress evidence, **not**
first-part delivery or terminal 64+1 proof. The scenario's original 240-second
single wait was shorter than the production crop cadence (about 10 seconds per
cell); it now checks 20/40/60-cell progress in independently bounded windows.
Do not use the interrupted run as a green scenario or deploy it.

### SA-09 65-cell physical batch continuation — 2026-09-25 (scoped native success)

The held-clock full 65-cell native run physically harvested the first 64 cells,
confirmed 64 crop/hand witnesses, returned the same HOT worker to the depot,
and transferred a 64-unit batch into an observed-current chest. It then stalled
on the retained final segment at cursor 0 with no conflict. A separate test-only
post-first-part fixture was built using ordinary COLD reducers for those 64
cells and the first delivery; it reproduced the same HOT stall in about a minute
without replaying the long first part. These are failed liveness receipts, not
green terminal evidence (`build/sa-field-65-native/manifest-progress-windows.json`
and `manifest-post-cold-part.json`).

The exact producer/consumer mismatch is now source- and world-observed. The
next retained support was `(-345,63,-324)`, one grade below the depot station;
saved region blocks show solid support at Y63 and air at Y64. The farmer had
walked to that X/Z but stood at Y65.125, after a permitted 0.125-block local
thin-surface lift. `FrontierV3ControlledMobMotion.submit` and `apply` rejected
the 1.125-foot vertical delta before their staged descent could run, even though
the canonical support grade was one. Both gates now admit only the additional
0.125 for a bounded exact retained edge; other goals and larger gaps retain the
old rejection. `FrontierV3ControlledMobMotionBoundsTest` passes the positive and
two negative boundaries. The process diagnostic now exposes first-crop index
and next support for direct route/body comparison.

The focused native retry (`build/sa-field-65-native/manifest-post-cold-part-grade-fix.json`)
finished green: the same worker descended and crossed the final retained route,
worked cell 65 physically, returned to the depot, and confirmed the terminal
physical receipt. The depot reported an `OBSERVED_CURRENT` physical socket and
fungible wheat slots 64, 64, 1: the original 64 plus the delivered 64+1. The
site advanced to GROWING epoch 2 without conflict. This proves the scoped
post-first-part HOT continuation and physical terminal, **not** a production
deployment, player acceptance, 65-cell hard-crash recovery, or arbitrary
layout-revision migration. The first terminal screenshot was black because
the field-level camera became occluded. A lifted camera could not pass the
pilot's exact-visit admission (it correctly required grounded client feet), so
that failed camera-only attempt is not product evidence. A ground-level lateral
camera then produced a second green terminal native manifest,
`build/sa-field-65-native/manifest-post-cold-part-visual-ground.json`; both
before/terminal screenshots show the field and settlement instead of a black
occlusion. They do not, by themselves, show the whole continuous farmer route;
the physical cell/hand/chest facts above provide that causal evidence. SA-09
remains OPEN until proportionate restart and remaining declared source
boundaries are reviewed.

### SA-09 65-cell post-first-part graceful restart — 2026-09-25 (scoped native success)

The checked-in `disposable-sa-field-65-last-cell-restart.json` scenario starts
from the same ordinary-COLD first-part fixture (64 worked and delivered,
one CellId left), visits the field, saves/stops the disposable server, then
reconnects the *same* client JVM. After restart the remaining work may legally
finish COLD before player ingress; the scenario asserts the terminal domain
fact instead of requiring the retired job to remain active. Its final isolated
manifest `build/frontier-v3-scenarios/disposable-sa-field-65-last-cell-restart-1790286173005.json`
is green. The site is GROWING at epoch 2, the retained intent is CONFIRMED with
`physicalReceiptResolved=true`, and the depot is OBSERVED_CURRENT with wheat
stacks 64, 64 and 1. Before/after technical frames were captured on a private
Xvfb display; they are not human visual acceptance. This is a graceful restart
before last-cell HOT ownership, not a hard crash or a mid-effect physical write
recovery proof. The diagnostic's older `causalTrace` fields remain incomplete,
so the manifest does not establish the broader M3 explanation obligation.

The unsuccessful setup attempts are not product failures: the first used a
busy default port, the second omitted the explicit private-display admission,
and an unheld fixture let the last COLD cell finish before the pilot joined.
The initial restart candidate then asserted that the old active job still
existed after legal COLD completion; a later candidate attached its site
assertion to the following container-inspection step. These were corrected in
the final scenario. Do not repeat the run merely for confidence; keep SA-09
OPEN for hard-crash/effect-boundary, local cell changes, layout migration and
player product acceptance.

### SA-09 mid-world layout revision boundary — 2026-09-25 (static review, OPEN)

The successful 65-cell native case is a *fresh revision-1 manifest*, not a
revision of an existing field. The current production path cannot change the
shape or size of a saved field: `ResourceSiteState.validate` requires every
canonical layout to equal the pinned bootstrap layout, and its `replace` rejects
a different layout; `ResourceSiteHarvestTraversal.supportsSegmentedHarvest`
admits revision 1 only; `FrontierV3ResourceFieldWitness.matchesLayout` requires
the exact old revision, geometry fingerprint and CellId set. The pure
`ResourceFieldCycle.revised` method has no production caller. Its existence is
not migration support. This is a source-proven missing product capability, not
an observed corruption incident or reason to rerun the green 65-cell scenario.

The implementation boundary is one typed, durable layout transition owned by
the field. It must retain a monotonic CellId allocator and explicit retired
identities, reject reuse/movement of surviving cells, and reconcile the old
work-order cursor, accounted results, carried part, active job/scene and any
pending player/farmer/projection effect before admitting the new order. The
bootstrap remains immutable genesis; a recovered revised layout needs its own
versioned provenance instead of a relaxed bootstrap-equality check. The
physical field claim then migrates from its exact predecessor only after the
old claimed cells and any added/removed cells have a bounded, durable
disposition. New cells cannot be silently treated as planted or mature;
retired cells cannot be silently rewritten. A safe first cut may defer a
revision while active work or physical effects exist, but it must retain the
requested revision and provide a deterministic completion/blocked path, not
discard the request. Focused negative and restart coverage must exercise
reorder, expansion, removal, stale cursors, and crash boundaries before any
native player claim. Do not bypass the revision-1 or bootstrap guards alone.

Source correction: the pure `ResourceFieldCycle.revised` helper previously
allowed a reordered layout even after one or more cells had been accounted.
It then rebuilt the prefix in the new order while an eventual retained job
would still have the old cursor. It now rejects any nonzero accounted work
until a single owning transition can migrate that job and its outcomes
explicitly. Quiescent reorder still works. The focused
`ResourceFieldCycleTest` selection passed. This is a safety boundary in an
inactive helper, **not** a production layout-revision implementation or a
native product claim.

### SA-06 no-demand stored-scene recovery is patrol-only — 2026-09-25 (source-confirmed)

`FrontierV3SceneExecutor.reclaim` returns immediately for every UNKNOWN scene
when no player demand exists. Harvest, production, engineering and service
work call this shared method; only route patrol has a separate proof-backed
no-demand recovery owner. Thus a previously HOT non-patrol scene whose saved
body is safely unloaded after restart cannot return to COLD autonomously even
when its exact departure/read/write evidence exists. The canonical UNKNOWN
lease still excludes the same worker from a COLD writer. This is an active
source-path liveness contradiction to the autonomous HOT/COLD contract, not
evidence of a specific reproduced live incident.

The correction must share the patrol's bounded whole-member saved-body proof
for eligible no-cargo scene families, without converting a bodyless PREPARED
first admission into an inferred disappearance or releasing unresolved physical
effects. Cargo scenes need their separate cart/actor proof. A loaded,
interaction-eligible body with no audience also cannot be treated as absent.
First verify a changed family release plan and exact negative/restart behavior;
do not broaden the existing patrol-only adapter by a filename-level rename
alone. SA-06 remains OPEN.

### SA-06 actor-only stored-scene recovery — 2026-09-25 (scoped native success)

The shared `FrontierV3SceneExecutor.reclaim` now offers the bounded stored-body
proof to eligible **cargo-free** UNKNOWN scenes after no-demand grace; cargo
continues through its separate custodian. This is not a timer-based revoke:
`FrontierV3SceneStoredRecovery` requires the exact read-fenced departure of
every surviving member, a synchronized census finding each UUID in exactly one
entity column, a matching saved snapshot including physical health, current
write/read epochs, and an unchanged canonical lease. Only that full disk proof
may install the saved-departure marker and attempt UNKNOWN→DRAINING→release.
An absent body, partial member set, pending read, duplicate UUID, cargo or
unresolved IO cannot gain custody by waiting. Focused ledger/scene/region tests
and NeoForge compilation pass.

The `production-work` native fixture injured the exact HOT industrial worker
from 20 to 19 HP, then gracefully restarted with no client for 20 seconds.
The first attempt exposed a real missing callback marker: the saved entity
region contained one 19-HP villager and a read-fenced departure, but the ledger
had no `savedDeparture`, so the original patrol proof entry could not run.
The no-load proof now admits such a *candidate*, not a release, and writes
the marker only after independent disk verification. The next attempt reached
the census and found another exact defect: vanilla had created zero-byte empty
`.mca` placeholders, which the scanner misclassified as truncated. Zero-byte
placeholders are now empty; nonzero headers below 8192 bytes still fail closed.
One intermediate retry failed before client admission because the runner was
launched against private Xvfb without its required private-display flag; this
is test setup, not product evidence.

The final isolated manifest
`build/sa06-stored-worker-health-restart-zero-region-fix.json` is green.
The replacement server logged `scene_released` for the exact production-work
lease at 00:55:16, before `PMTestPilot joined` at 00:55:26; after reconnect,
the canonical `resident:1-15` had `healthRaw=19000000`. This establishes one
actor-only, no-demand, graceful-restart release with physical health preserved.
It does **not** close SA-06: hard-crash, multiple members, cargo custody,
other scene families and adverse read/write races still need proportionate
source/contract review and targeted evidence. Do not deploy this WIP on the
strength of this single native case.

### SA-06 whole cargo-scene stored recovery — 2026-09-25 (scoped native success)

The actor-only no-demand proof left `LOGISTICS` UNKNOWN forever after an
unattended restart. The same bounded census now selects every living actor
**and** the exact cart UUID. The cart must have a read-fenced, conflict-free
departure, match the current lease/revision/authority epoch and canonical
inventory, occupy exactly one saved entity column, and match the serialized
cart position, declaration and 27 slots. Missing, duplicate, returned or
different-epoch cargo cannot authorize actor release. The cart's saved marker
is persisted only after whole-set proof; generic release still requires its
separate durable cargo-cleanup witness. Focused
`FrontierV3SceneStoredRecoveryTest` and `FrontierV3CargoDepartureLedgerTest`
pass, including absent/different/duplicate cart negatives.

The first native attempt was a test-method failure, not product evidence: the
declared visit required feet Y65 while the actual player stood at Y64. Its
server did physically open HOT, but the runner never reached restart; see
`build/sa06-stored-cargo-restart-wrong-feet.json`. The corrected isolated
`disposable-sa-stored-cargo-restart.json` manifest
`build/sa06-stored-cargo-restart.json` is green. After the graceful restart,
with no player connected, the replacement server recorded `scene_released`
for lease `lease:supply-1-11-r112`, cargo `cargo:supply-1-11`, and all three
members at 01:11:22.504. `PMTestPilot joined` only at 01:11:33.016. The
subsequent client diagnostics show the same scene `CLOSED` and operation
`EN_ROUTE` at route index 1 with the same three participant IDs and cargo ID.
This proves one real multi-member, whole-cart, no-demand graceful-restart
handoff to continuing COLD operation. It does not prove crash ordering,
concurrent return reads, damaged/foreign cargo under restart, every operation
stage or product-visible later arrival. SA-06 stays OPEN; no deployment.

### SA-06 complete loaded set without demand — 2026-09-25 (targeted native test)

Source review after the saved-cart proof found a complementary liveness hole:
the no-demand UNKNOWN branch still returned before its ordinary loaded-body
reclaim, even when all original actor UUIDs and the exact cart were present.
It now waits for restart grace, then admits a **complete loaded** set through
the existing `reclaimObservedBodies` owner/health/cargo validator before trying
the stored proof. A missing member/cart does not take this path; the original
UUIDs are never replaced. An all-loaded HOT scene without demand subsequently
uses the ordinary HOT→DRAINING lifecycle; this change does not infer absence.

The existing `pm-frontier-v3-scene-restart-reclaim` GameTest now checks the
whole loaded set and rejects a partial set. A first command accidentally
selected the broad `scene` slice (146 tests, with unrelated failures and one
slow test); it was deliberately stopped, not treated as a result for this
change. A new exact `scene-restart-reclaim` selector ran only its two tests:
2/2 passed and the task-owned server shut down normally. This is a targeted
loaded-set regression, not a native zero-player graceful-restart proof for a
loaded chunk. Mixed loaded/saved member sets, interrupted cargo, crash windows
and concurrency are still unproven; SA-06 remains OPEN.

### SA-06 mixed loaded/saved no-demand set — 2026-09-25 (source and unit, not native-proven)

The earlier no-demand branches accepted only an entirely loaded scene or an
entirely stored scene. A valid post-restart mix (for example one loaded cart
plus a read-fenced unloaded worker) therefore remained UNKNOWN indefinitely.
`FrontierV3SceneStoredRecovery.currentReceipts` now partitions every living
member and the cargo UUID into exact loaded and stored sets. A loaded actor
must be the current owned positive-health Mob and have no conflicting
departure; a loaded cart must satisfy the current exact cargo validator. Each
unloaded member/cart still needs its read-fenced exact receipt. The two sets
must be disjoint and cover the entire expected scene. The bounded entity-region
census scans **only** the unloaded subset; after the asynchronous proof, the
whole partition and loaded ownership are read again on the server thread.
Generic release then re-observes live health/inventory and separately consumes
the saved witnesses. This does not convert a missing member into absence.

A decided proof attempt can rearm after a minimum 20-tick pacing interval
only when the exact loaded/stored partition changes or a relevant saved entity
column gets a new write epoch. Time alone never reauthorizes a scan or a
release. `FrontierV3SceneStoredRecoveryTest` covers full partition, omission,
overlap, exact stored-subset census and bounded evidence-change rearm; focused
JUnit and NeoForge compilation pass. One native regression of the already
accepted fully-stored cargo path is green with current source:
`build/sa06-stored-cargo-mixed-regression.json`. Its replacement server logged
`scene_released` at 01:25:38.800, before player join at 01:25:49.279; the
client then saw CLOSED and EN_ROUTE index 1 with the same three participants
and cargo. This is a regression of whole-stored behavior, **not** a native
proof that a deliberately mixed physical partition was exercised. That
specific mix, interrupted cargo, hard-crash and adverse read/write races remain
SA-06 open acceptance work.

### SA-06 saved HOT worker across abrupt JVM stop — 2026-09-25 (scoped native success)

Static review and an isolated crash probe exposed a separate no-demand gap. An
ordinary `save-all flush` can durably write a still-loaded HOT worker, then an
abrupt JVM stop prevents the unload callback that would normally create its
departure receipt. On restart, the exact lease becomes UNKNOWN; without a
player, the old stored-recovery path could neither reclaim a loaded body nor
select a receipt-backed stored body. The first test attempt used the persistent
client path, which had not executed its requested pre-stop flush. The next
attempt executed the flush but the pilot's attack action only acknowledged
packet submission, not actual damage. Those attempts were test-method evidence,
not proof that health was lost. A third probe required client-observed damage
and reproduced the no-player UNKNOWN gap; its canonical 20-HP diagnostic while
the scene returned to HOT did **not** by itself prove lost physical health,
because HOT health is transferred to canonical custody on release.

The pilot now has an opt-in `requireDamage` receipt for attacks on living
entities and the persistent abrupt branch records a verified vanilla
`Saved the game` reply before stopping the exact server JVM. Production
recovery adds a narrowly bounded cargo-free, single-member path for this
missing-callback shape: no demand and no loaded body; one unique UUID in a
synced entity-region census; current write/read stamps; exact saved body
owner, kind, lease revision, epoch, position and positive health; unchanged
canonical actor and no conflicting departure. Only after the disk proof does
it publish the departure marker and reuse ordinary DRAINING/release. It does
not infer absence from a timeout or create a replacement body. Follow-up
source review capped changed-evidence re-scans at two per lease so unrelated
world writes cannot create an unbounded census loop; this final pacing-only
change compiled after the native proof and was not itself rerun natively.

The corrected native run `build/sa06-stored-worker-abrupt-after-flush-r4.json`
is green. Its recovery metadata binds the completed `Saved the game` reply to
the pre-crash server PID/run ID. The replacement server logged
`scene_released` for the original production-work lease at 01:51:26.870,
before `PMTestPilot joined` at 01:51:37.382; the same actor then had
`healthRaw=19000000`. Focused `FrontierV3SceneStoredRecoveryTest` and
`FrontierV3TestPilotScenarioTest` passed. This proves an abrupt stop **after a
completed vanilla flush**, with one cargo-free worker and no-player release;
it does not prove interruption inside the save, multi-member/cart crash
recovery, all scene families or adversarial read/write races. SA-06 remains
OPEN and this WIP is not deployed.

### SA-06 whole-scene saved HOT set after abrupt stop — 2026-09-25 (scoped native success)

The single-worker missing-callback proof is now generalized to an exact
loaded/receipt-backed/unobserved partition of the **whole** UNKNOWN scene.
For every missing callback, one bounded no-load census finds exactly one
saved entity column by UUID; saved actor declarations and cargo-cart contents
must match their current lease/owner/epoch and canonical state. The complete
missing subset is checked before any disk-derived receipt is published. The
existing whole-scene stored recovery then independently verifies the final
loaded/saved partition before canonical DRAINING, including the separate cargo
cleanup witness. A changed-evidence retry remains capped at two scans per
lease. The `UnobservedProof` unit test rejects an omitted actor and a UUID
appearing in two entity columns; the focused recovery JUnit suite passes.

The isolated `disposable-sa-stored-cargo-abrupt.json` run
`build/sa06-stored-cargo-abrupt-after-flush.json` is green. It recorded the
completed vanilla `Saved the game` reply for the exact pre-crash JVM.
On the replacement server,
`missing-callback-proved lease=lease:supply-1-11-r111 actors=3 cargo=true`
appeared at 02:01:47.978, followed by `scene_released` for those same three
residents and `cargo:supply-1-11` at 02:01:48.103. The first returning player
joined only at 02:02:23.521. The client then saw that lease CLOSED and the
same operation EN_ROUTE at route index 2, with the same participant and cargo
IDs. The native run preceded a source-only strengthening of exact UUID
coverage/deep-copy in `UnobservedProof`; its focused JUnit passes, but that
small final delta has no second native run.

This is one real multi-member/cart crash **after a completed save flush**,
not a crash inside a write or a proof of every mixed loaded/stored partition,
interrupted cargo, scene family or adverse return-read race. SA-06 remains
OPEN; no deployment or release claim follows from this scoped result.

### SA-06 PENDING first-body offline repair — 2026-09-25 (source/test verification, not native crash acceptance)

The 2026-09-24 hard-crash note above accurately described its then-current
gap, but is no longer current source state. The pilot-only
`FrontierV3OfflineFirstAdmissionRecovery` now exists separately from the older
`OfflineActorRecovery` cancellation publisher. It holds the stopped world's
`session.lock` across a global saved-entity absence scan and publication. The
plan accepts only the exact PENDING epoch-1 first body of a still-PREPARED
ambient resident/active bioform, matching canonical UUID, lease revision and
attempted owner, with no other current scene/work/custody claim. The publisher
durably writes an immutable receipt tied to canonical head, actor, original
SavedData bytes and all scanned file hashes *before* atomically writing the
`PROVEN_ABSENT` SavedData image. It never edits canonical WAL or issues a new
identity. Re-entry after either durable boundary requires the same receipt,
predecessor and unchanged absence inputs; a changed saved body/region, world
seed/head, owner or competing receipt rejects the repair.

Current focused `FrontierV3OfflineFirstAdmissionRecoveryTest`,
`FrontierV3OfflineFirstAdmissionRecoveryPublicationTest` and
`FrontierV3ActorFirstAdmissionLedgerTest` passed 15/15 on 2026-09-25. These
include a simulated stopped-world entry, saved-body and world-lock negatives,
and interrupted receipt/re-arm publication retry. No actual server JVM has
yet been killed specifically between persisted PENDING and the first entity
write, then repaired and restarted with this command. Scene/work-bound first
bodies are intentionally rejected rather than guessed. Thus this closes the
static claim that no explicit offline PENDING path exists, but does **not**
close full SA-06 crash/recovery or product acceptance.

### SA-09 repaired cell, bound farmer hand and COLD handoff — 2026-09-25 (partial)

Static review and the isolated native run
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-repair-1790294479692.failure`
exposed a real HOT→COLD defect. After a damaged first cell was physically
tilled/replanted and HOT work harvested some wheat, player departure requested
generic `SceneLeaseReleased` while the same farmer still had a canonical
`ActorHand` fungible binding. The generic reducer correctly rejected this
stock-losing exit, but the adapter had already fenced the carrier and threw
the rejection into runtime quarantine. The preceding run
`...soil-repair-1790293689747.failure` retains that exact rejection.

The source now has a typed `ResourceSiteHarvestHandRelease`: one durable event
observes the exact owned plain-wheat offhand, checks lease/job/account/epoch,
quantity and binding, releases the binding, and closes the scene atomically.
The generic bound-hand prohibition is unchanged; a forged quantity or replay
rejects. A focused reducer/codec/planner test passed. The next native run
observed `scene_released` for the same job at revision 262 after departure,
then continued COLD work to `GROWING` epoch 3 without quarantine. This is
positive evidence for the repaired cell and the hand-to-COLD boundary, not a
green end-to-end scenario: the test's last assertion wrongly required the
deferred physical depot receipt and timed out. It has been narrowed to the
semantic COLD-cycle condition, without rerunning merely for a green label.

The physical receipt remains an explicit SA-09 gap, not a timing excuse.
`FrontierV3ResourceSiteDeliveryExecutor` does implement fungible HOT
hand→depot delivery, but it requires an active job at its return station and
a HOT lease. This run departed with a bound part, completed the remainder in
COLD and retired the job before re-entry. Its `RUNNING` intent and deferred
lineage therefore have no eligible receipt producer. The older
`FrontierV3ResourceSiteHarvestExecutor` requires a legacy exact output item
and is intentionally inert for cell-owned fungible yield. The native
diagnostic remained `RUNNING`,
`pending_exact_physical_receipt`, `physicalReceiptResolved=false` at epoch 3.
Wire an exact deferred fungible depot/field receipt for this mixed HOT→COLD
path, without replaying the old hand transfer or minting another lot, then
verify it with one targeted native ingress. Foreign support/air aftermath and
the other SA-09 obligations also remain OPEN. No deployment or SA-09 closure.

### SA-09 mixed HOT/COLD late depot receipt — 2026-09-25 (source/test partial)

The active cell-owned path now has a distinct typed deferred observation. Its
reducer requires the retained completed lineage and HOT history, the exact
RUNNING harvest intent/roles, a current same-revision reference replica and
an acquired exact chest custody epoch. Confirmation changes neither stock nor
field/actor state. The native producer reads the naturally ticking owned chest
after reference custody has refreshed it; it never issues a second farmer hand
transfer. Payload and snapshot codecs have a new stable tag, and full-state
receipt validation and the retained trace recognize the type. Focused model
planning, negative fingerprint/revision/lease, payload/snapshot recovery tests
and Java compilation pass. This is not native acceptance.

The first targeted native run (`...soil-repair-1790296123573.failure`) reached
GROWING epoch 3 but retained `RUNNING/pending_exact_physical_receipt`. Its
scenario waited at the field, with no guaranteed naturally ticking depot.
The second run (`...soil-repair-1790296679420.failure`) exposed a concrete
separate product defect before its pilot's depot visit completed: the depot was
already loaded and `ACTIVE`, but its replica was `CONFLICT/FINGERPRINT_MISMATCH`
at revision 428, with an owned plain wheat stack still at slot 0 and canonical
slot 0 now empty. The released-scope catch-up classified that old saved
fungible stack using the *new* canonical layout, so an ordinary COLD successor
made the retained replica look foreign. This is not cured by a longer wait.
Source now compares a released chest against its retained old replica using
plain vanilla fungible classification independent of today's account layout;
only an exact old fingerprint/provenance match may then enter fenced
projection of the new canonical image. The later current-state observation
remains strict. Focused Java compilation passes; native validation of this
specific catch-up correction is still pending. The second run's strict `visit`
action failed because the pilot landed one block below/aside from its target;
its incomplete receipt step is not evidence about the corrected source.
SA-09 remains OPEN; no deployment.

The subsequent targeted run
`build/frontier-v3-scenarios/disposable-sa-field-world-soil-repair-1790297315554.failure`
provides the missing native domain observation despite a scenario-matcher
error: after an ordinary depot visit, the owned chest diagnostic at instant
72474 was `ACTIVE`, `OBSERVED_CURRENT`, `ACQUIRED`, with no slot mismatch.
The exact previous harvest at revision 437 then reported `GROWING` epoch 3,
`physicalReceiptResolved=true`, `physicalReceiptConfirmed=true` and its intent
`CONFIRMED`, with observation
`observation:field-deferred-site-harvest-1-wheat-field-2`. No second wheat
issuance is performed by the reducer. The scenario nevertheless exited red
because its last predicate wrongly searched for `physicalReceiptResolved` at
the JSON root rather than under `terminalHarvest`; the checked-in predicate
has been corrected and parsed, but the costly native run was not repeated
solely for a green wrapper. This is narrow positive physical-receipt evidence,
not a formally green scenario or SA-09 closure.

That native run also showed `completeForColdHotReceipt=false` even though the
receipt itself was confirmed. Static review found the trace's COLD event was
never captured: before the terminal event there is no lineage to update.
`ResourceSiteHarvestTrace` now retains the actual returned/traversal terminal
event once its reducer creates the lineage, as well as the new deferred
observation. The focused reducer/codec test now proves a complete retained
trace after restart; this diagnostic correction has no later native proof.
Foreign support/air aftermath, stronger crash/recovery and full product
acceptance remain open. No deployment.

### SA-09 next static cut: foreign field support/crop space — 2026-09-25 (OPEN)

Source review after the depot receipt found a reachable local-causality gap.
`FrontierV3ResourceFieldObservation.read` correctly returns a typed `Foreign`
incident with exact soil/crop NBT, and the physical witness can retain that
incident. But `FrontierV3ResourceFieldWorldChangeExecutor.observeOne` accepts
only `Owned`; `classify` supports only crop→air and farmland→dirt. The
pre-write hook fences only those two replacements. An actual foreign crop
block or replaced support can therefore be read as foreign yet never acquire
the canonical per-site hold or enter `ResourceFieldCycle.cropObstructed` /
`soilObstructed`. The growth projector reports `PHYSICAL_CONFLICT`, but its
caller does not turn this into a local canonical incident. No generic
whole-site conflict or blind overwrite is acceptable. The next implementation
needs one typed foreign-cell observation with a durable before/after witness,
the same per-site COLD hold/recovery ordering, exact physical block identity,
and a local obstruction/clearance path that preserves unaffected cells.

Source-level implementation boundary for this cut: keep
`ResourceFieldPhysicalSurface.Condition` restricted to *owned* blocks. It is
the physical writer's predecessor and must never encode `OBSTRUCTED` or an
unknown block. A foreign result therefore needs a distinct typed world-cell
cause, not a fabricated owned `after` condition and not a whole-site
`ResourceSiteConflictObserved`. Before any ordinary claimed-cell replacement,
retain the exact site/epoch/layout/CellId, owned or already-obstructed
predecessor, and cause in the physical witness, then accept a per-site canonical
hold before Vanilla's write. The post-write read decides whether the cause
cancelled, produced an owned loss, introduced a foreign soil/crop obstruction,
or cleared a previous obstruction; the pre-write replacement alone is not an
observed outcome because neighbor updates may change the second block. Exact
soil and crop block-state NBT belongs in the durable physical incident; the
canonical event must name the same cause/cell and local typed disposition.
The held cause fences only this site's COLD growth/harvest until the exact
physical postcondition and canonical cell state agree and the claim is updated.
An unloaded cell keeps the hold; a bounded scan is only recovery for a loaded
mutation missed by the pre-write hook. A foreign claim must never be considered
current owned surface or projected over. Clearance needs its own observed
transition back to known bare farmland/dirt or an explicitly unresolved cell,
without inventing a crop/yield. Both held and accepted windows must survive
snapshot/WAL/SavedData recovery; a stale incident cannot block another site.
The first focused checks should cover a foreign crop, foreign support,
cancelled/no-op write, exact-NBT mismatch, unrelated-site progress, and one
restart at each side of WAL/claim acknowledgement. No client matrix is needed
until the active source path is wired.

### SA-09 foreign field-cell admission — 2026-09-25 (scoped, OPEN)

The active source now has a separate `ResourceFieldForeignChangeHeld` /
`ResourceFieldForeignCellObserved` / `ResourceFieldForeignChangeAcknowledged`
path. The per-site hold enters the canonical WAL before a hooked block write;
SavedData retains the hold and exact observed soil/crop block-state NBT. The
post-write read, not the requested replacement, chooses obstruction, owned
damage, clearance or cancellation. The physical claim adopts the result only
after canonical acceptance; restart reconciliation covers the retained
physical witness and orphan canonical hold. Foreign NBT is never passed to
the owned `Condition` writer, and only the affected site's COLD work is held.
The field-work and scene readers now compare exact retained foreign blocks
without treating a changed causation ID as a new obstruction. Player removal
of an exact foreign block can reach the ordinary block-write path. The field
diagnostic exposes the canonical first cell, retained foreign block names and
whether the site hold remains pending.

Focused pure tests cover foreign support, foreign crop, clearance, cancelled
write, snapshot/payload and SavedData round trips, unrelated-site hold scope,
exact-NBT mismatch and no minted harvest; the selected Frontier and NeoForge
tests pass. A single corrected native disposable run is green at
`build/frontier-v3-scenarios/disposable-sa-field-foreign-support-restart-1790300154212.json`.
Its command replaced the loaded first support with `minecraft:stone`; trace
recorded accepted pre-write hold and foreign-cell observation. After graceful
restart, the same world still contained the stone, the site was ACTIVE with
matching layout, canonical first cell `OBSTRUCTED/OBSTRUCTED`, exact retained
foreign soil `minecraft:stone`, and no pending foreign hold. The preceding
attempt failed only because the scenario addressed its final assertion to
action 4 rather than 5; the corrected terminal run passed. Its client/server
and private Xvfb have stopped. An unrelated pre-existing live server was not
touched.

This receipt proves foreign-support admission and graceful persistence, **not**
native foreign-crop clearance, a farmer skipping the obstructed cell while
other work continues, a crash in either SavedData/WAL ordering window, a
foreign change while one half of a cell is unloaded, later COLD epoch behavior,
or player-visible acceptance. Those are distinct remaining SA-09 obligations;
do not deploy or mark SA-09 closed on this result. Source review also found
that valid-shape external wheat planting into an empty owned cell is outside
this foreign-block classifier; decide its ownership semantics before treating
arbitrary field intervention as supported.

A second, distinct crop-space/clearance scenario is green at
`build/frontier-v3-scenarios/disposable-sa-field-foreign-crop-clearance-1790300836889.json`.
The first physical wheat slot was replaced by actual stone; the accepted
pre-write hold and loaded-world postcondition yielded canonical
`FARMLAND/OBSTRUCTED`, an exact `minecraft:stone` crop incident and an ACTIVE
site. After graceful server restart in the same client session, an ordinary
`/setblock ... air` cleared it; the terminal diagnostic showed canonical and
committed `FARMLAND/ABSENT`, no foreign incident or pending hold, and the actual
block was air. Two setup attempts never reached Minecraft because a private
Xvfb marker was omitted; a later run reached the first physical result but
failed a misaddressed scenario assertion. Neither is a product defect, and the
corrected terminal run was green. The task-owned client/server and Xvfb
stopped; ports 25587/25588 are free. This closes only the native crop-space
and graceful-clearance evidence gap above, not farmer continuation,
hard-crash windows, unloaded-half changes, later COLD behavior or product
acceptance. No matrix rerun or deployment was performed.

Static follow-through exposed a real *next* causal gap: admission of an
obstruction is not yet proof that a farmer can work around it.
`ResourceSiteHarvestTraversal.compileSegmentPlan` still builds an immutable
route through every crop's workstation from the authored layout, without the
current `ResourceFieldCycle` or physical foreign incident. The HOT scene
requires the worker to reach that station before
`FrontierV3ResourceFieldWorkExecutor.advance` can return `SKIPPED_BLOCKED`.
For a solid foreign crop, `FrontierV3SemanticMovement.target` correctly finds
blocked body clearance; for a foreign support it may find a changed support.
The scene's route-conflict path can therefore fire *before* the local
`SKIPPED_BLOCKED` receipt is reachable. COLD can also traverse the immutable
station without accounting for its changed physical geometry. This is a
source-proven design inconsistency, not a claim from the green obstruction
scenario. The correction must give each blocked cell a reachable, explicit
non-work station or bypass with a durable route revision at a known current
worker position, then account that CellId without entering or overwriting the
blocked space. It must preserve the worker's stable identity, carried wheat,
the existing job/segment progress and HOT/COLD handoff. Do not simply waive
arrival or turn all route conflicts into skips: unrelated obstacles and
off-contract body positions must still be reported as conflicts. A targeted
reducer/traversal test and one ordinary-client blocked-cell continuation are
the appropriate next checks, not a full matrix.

Implementation analysis of the active job invariants makes the required seam
more precise. `ResourceSiteLifecycle.advanceHarvest` and
`ResourceSiteHarvestJob` require the current traversal cursor at the crop
station; `ResourceSiteHarvestProcess.coldCropReceipt` has the same precondition.
Therefore merely allowing `SKIPPED_BLOCKED` in the work executor cannot fix
this: the event cannot be reduced before the blocked station is reached, and
the existing route cannot advance across that station without inventing an
arrival. `ResourceSiteHarvestSegmentRenewed` currently works only at an
exhausted segment, so it does not repair a mid-segment obstruction either.

The correction should introduce a **typed blocked-cell skip/replan boundary**
at the farmer's *current* retained station, before either HOT or COLD attempts
the blocked edge. It must verify the exact next CellId, canonical obstruction,
unaccounted state, current job/worker/schedule, and (when HOT) the matching
lease, physical foreign witness and unchanged bound hand. In one canonical
transition it marks that cell accounted without yield, increments the job's
crop progress and replaces the remaining route with a bounded route compiled
from that same current station. If the skipped cell was the last, the new
route is the depot return from that station; if subsequent cells are also
blocked, each may be skipped in a later bounded transition without moving the
body. The compiler must exclude known blocked supports from intervening
pedestrian edges and stop a segment at the next blocked cell so neither HOT
nor COLD crosses it silently. Every route revision must be deterministic from
canonical layout/field state, retain stable job and actor identity, and keep
the physical hand/depot accounting unchanged. If no physically reachable
continuation exists, report an exact local route obstruction rather than
teleport, false skip, or whole-world quarantine. This is one owner-level
correction; do not add a parallel field-specific movement engine.

### SA-09 goal-navigation first vertical evidence — 2026-09-25 (OPEN)

The accepted target is `frontier-v3-goal-navigation.md`: canonical CellId/work
and custody must be independent of the physical micro-path; HOT uses bounded
Minecraft navigation, COLD retains the same actor and semantic goal. The
active source now has a bounded HOT path provider, typed job-local transit
blocks with exact clearance, and an atomic blocked-CellId skip/replan from the
retained farmer station. A failed route after the skip or at a bounded segment
boundary now holds that job locally rather than causing a whole-site conflict
or COLD scheduler quarantine. The route-unavailable path has a deterministic
focused recovery test; the harvest traversal test class is green (61 cases),
and NeoForge compiles. These source checks do not retire the historical
canonical per-block corridor or prove full HOT/COLD goal travel.

A corrected, single-world ordinary-client discriminator passed at
`build/frontier-v3-scenarios/disposable-sa-field-blocked-final-cell-goal-1790311007386.json`.
It waited for the actual farmer's HOT lease before a loaded final crop was
replaced by foreign stone. The foreign observation was accepted, then the
same HOT lease `lease:site-harvest-1-wheat-field-1-crop-64-r34` and actor
`resident:1-13` produced the accepted
`resource_site_harvest_blocked_cell_skipped` at revision 48. The bounded
diagnostic trace explicitly links `resource_site_harvest_hot` to that skip;
the site then reached GROWING epoch 2 without conflict and with a resolved
terminal receipt. This disproves the earlier ambiguity that COLD alone might
have completed the blocked cell. It is not evidence for a multi-cell HOT
bypass, transit-route clearance, unloaded/restart continuity, non-flat goals,
smooth motion, or human product acceptance. The earlier simple green run
without a HOT assertion was kept as weaker evidence, not counted twice.
No matrix rerun, live deployment or publication occurred; private pilot
server/client/display all stopped. SA-09 remains OPEN.

The next source cut introduces `ResourceSiteHarvestGoal` as a route-independent
typed identity for a work CellId or the depot's four legal service stations.
The continuation blocker uses this semantic destination rather than taking
the old corridor's final node, and the process diagnostic now exposes the
goal separately from the physical cursor. A focused test proves the current
goal is not merely the next waypoint; selected harvest traversal tests pass
62/62 and NeoForge compilation passes. The goal is derived, not yet retained
as the sole movement authority: the old per-block canonical route remains
active. This is migration progress, not SA-09 completion.

Follow-up source cut: the typed goal now declares the exact worker and an
arrival contract: one work-cell station or any of four bounded depot service
stations. The provider-neutral HOT boundary accepts the legal station set;
the Minecraft provider finds an envelope-contained reachable path to one of
them and reports arrival only from the observed body. A focused local-navigation
GameTest places a real two-high obstruction at the first service station and
proves the same NoAI mob reaches the other declared station. The
`local-navigation` scene slice passed 22/22, selected harvest traversal tests
passed, and NeoForge main/test compilation passed. This proves *provider
selection inside one declared goal*, not that farmer runtime already uses
that goal. Static source still shows `ResourceSiteHarvestJob` persisting its
per-block route/cursor, the HOT scene selecting its next physical support,
and `ResourceSiteHarvestBehavior.releasedBody` snapping an observed mid-edge
body to the old cursor during scene release. `AmbientLeaseStateProcess` also
rejects a farmer body different from that cursor. Those are the connected
handoff/authority seams to migrate before enabling direct HOT farmer goal
travel; changing only the HOT target would introduce a visible snapback.
No live deployment or player acceptance; SA-09 remains OPEN.

The same goal review exposed a structure/container identity distinction:
`structure:…-depot` is not `container:…-depot`. The goal now first validates
the job's declared output container against this settlement's canonical depot
container, then requires exactly one depot service structure; it never chooses
an arbitrary first match as a substitute destination. A negative test with a
foreign declared container rejects the goal before navigation. The selected
62 harvest traversal tests and NeoForge compilation passed after this repair.

The provider result now retains the exact observed legal arrival station;
`ARRIVED` without one, `BLOCKED` without a typed cause, or ambiguous matches
cannot masquerade as success. The multi-station GameTest asserts the station
identity, not just a status flag. NeoForge main/test compilation and the
focused `local-navigation` native slice passed again (22/22). This closes an
adapter-result ambiguity needed for the future depot handoff, not the
farmer's retained-route/handoff migration or SA-09.

The next source seam introduces `ResourceSiteHarvestKnownNavigation.path`:
from the actual actor body and derived current goal, it compiles only a
bounded ephemeral COLD path in surveyed known geometry, with foreign field
supports excluded. It neither serializes a path nor consumes a work cell.
A focused test moves the actor body one valid support ahead of the obsolete
job cursor and proves the path starts at that actual body, reaches the stable
CellId goal, and refuses an obstructed target. Selected harvest tests pass
63/63 and NeoForge compiles. The helper is not yet the active COLD scheduler;
the old cursor remains authoritative there and on HOT scene release. No
claim of farmer goal-travel, restart, smooth motion or SA-09 completion follows.
The current helper consumes bootstrap-surveyed terrain and accepted field-cell
obstructions; it does not yet carry a general observed-change revision for
arbitrary player edits to transit ground. Before wiring it to authoritative
COLD movement, the family must bind such invalidation to retained movement
knowledge or conservatively refuse the affected unknown edge, as execution
semantics section 3 requires. A green pure path test is not evidence that an
unloaded changed bridge is traversable.

Follow-up source checkpoint: `ResourceSiteHarvestHotGoalArrived` now has a
registered command owner and WAL codec. The process checks the current
job/worker/layout/slot/goal, then atomically advances only its transitional
work gate, canonical actor body and HOT lease recovery body. An alternate
declared depot service station is accepted as an observed arrival; the route
cache endpoint no longer overrides the actor's real depot position in the
HOT delivery gate or terminal lineage. The physical delivery executor checks
the same owned Minecraft body at that station before touching the chest.
Focused command/replay/snapshot/alternate-port and displaced-actor tests pass;
NeoForge compiles. The production farmer scene now emits this event on an
already observed legal goal station, bypassing stale intermediate checkpoints;
it still drives movement along the old route. COLD also advances that corridor,
and release still snaps mid-travel movement to its cursor. No native scene or player proof follows,
and SA-09 remains OPEN. The next connected cut must retire these linked
cursor authorities before switching the HOT movement target.

Next source checkpoint (2026-09-25, partial): a typed
`ResourceSiteHarvestColdGoalAdvanced` fact now has a codec, process owner and
focused reducer test. Its reducer recomputes the next known pedestrian support
from the actor's actual retained body, rejects a forged jump/stale replay,
and keeps CellId progress unchanged during travel. The harvest traversal
selection and process-catalog selection pass; Frontier compilation passes.
This fact is **not emitted by the scheduler** yet. The active COLD driver,
scene admission/release and HOT actuator still use the old cursor. Source
review identified additional connected constraints: release captures raw
integer entity Y rather than a proven support surface; ambient release and
handoff require exact cursor equality; arbitrary player-edited transit ground
is not yet represented in retained COLD navigation knowledge. Activating this
fact alone would strand or teleport a farmer, so no product claim or native
run follows. The next connected cut must address these owners together.
