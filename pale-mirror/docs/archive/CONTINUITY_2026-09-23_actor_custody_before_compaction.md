# Continuity Ledger

## Objective and authority

Main alone fully closes SA-01–SA-10 under the accepted static audit. Goal ACTIVE.
No subagents; Terra stopped. Preserve all WIP/history. Narrow checks do not
establish full F0.6R3 acceptance. No new deployment/publication/reset authority.

- Governance: /home/rd/proj/pm-governance/pale-mirror.
- Active source: /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror.
  Git root parent; HEAD 8a626acaa286a8390a2c1c628de09253785ffe67 plus preserved WIP.
- Original /home/rd/proj/minecraft and nested source are preserved redirects.
- [Audit](docs/frontier-v3-static-audit-2026-09-22.md) owns findings, decisions and
  detailed receipts. [Order](docs/work-orders/PM-F06R3-FIRST-VISIBILITY-COLD-CONTINUITY-CORRECTION-02.md)
  revision23 owns scope. [Protocol](docs/engineering-agent-protocol.md) owns workflow.
- Canonical execution semantics, contract, REL/ARC and architecture remain binding.
  Choose tests by information gain; no repeated unchanged confidence matrices.
- Full previous ledger preserved in
  [archive](docs/archive/CONTINUITY_2026-09-23_before_cargo_release_compaction.md).
  It contains historical/superseded statuses, not another active assignment.

## Current execution

Latest: removed closed-harvest epoch1 inference/API. inspectClosedHarvestReturn
is read-only, admits only retained carrier with actual next epoch + valid declared
ambient revision; missing history remains conflict for explicit repair.40224 green
10s20 ledger/adoption tests. Recognition now carries stamped owner/representation/
revision/epoch, rejects stale/future/foreign tuples.51844 green39s recognition+full
AmbientAdmissionPolicyTest. Shared recoverableOwnership additionally blocks inactive
carrier/unresolved departure and matches exact pending adoption. Actual entity join,
post-projection pending recovery and ordinary UNKNOWN actor loop use it.76908 green
10s6 focused recognition/restart-policy tests + pilot compile. No active task run.
NEXT explicit first-body/birth history, interrupted pending admission and returned
body+pre-release fence recovery. These guards prevent unsafe recovery but do not
restore missing population. Historical world-copy repair/player acceptance and
SA08/09 remain open. Live unchanged. Details in audit latest section.

Latest PREPARED fix: direct abandonPreparedForReservation now owns exact current
lease/head, no-body custody/pending evidence gates; wrapper delegates. Body-present
path uses common release, which now accepts PREPARED and validates exact stamped
ambient tuple/epoch, retains observed health and durable carrier.92841 green10s4
policy tests+compile.50351 native1 was new test invalid indexed lookup after fixture
setPos to unloaded canonical coordinates; inspected Vanilla tracking callback.
Corrected component test explicitly asserts that index absence then tests shared
PREPARED body release; separate direct missing-history cancellation test proves
unchanged canonical head.36293 terminal0/43s all10 tests, all dimensions saved
14:05:36. Log build/sa-ambient-world-copy.j4JKe3/prepared-release-native-corrected-fixture.log.
Original failed log/world preserved, corrected current GameTest world terminal.
NEXT: explicit first-body/birth history (existing first-admission cancellation
still lacks next-reconstruction receipt), pending adoption crash/return and same-body
owner recovery, remove closed-harvest epoch1 inference, then historical population
and client copy acceptance. No task process active, live unchanged, SA still open.

Latest durable ordering: both ambient/scene reconstruction now invoke shared
FrontierV3ActorAdoptionAdmission BEFORE addFreshEntity, persisting exact pending
transfer first. Explicit false restores predecessor durably; exceptions retain
pending. Ambient body stamped with final epoch before join callbacks. Ledger
persist uses exact dimension/data path + cached instance validation. Loaded and
unloaded ambient releases and loaded/final-departure scene fences now persist
before canonical release/body retirement.46907 green10s17 focused tests.
37361 terminal0/46s native scene-departure/death8 pass; loaded reservation paths
read actual SavedData before shutdown. All dimensions saved13:58:31; evidence
build/sa-ambient-world-copy.j4JKe3/durable-adoption-native.log. Prior testworld moved
to frontier-v3-scene-game-test-before-durable-adoption-20260923. No active task JVM.
NEXT static seam: abandonPreparedForReservation direct reserved PREPARED caller
bypasses absence gate; body-present branch discards without common fence and uses
canonical health. Also fenceObservedAbsentHarvestReturn invents epoch1 from local
absence. Fix these alongside birth/pending return/same-body recovery. Full audit
and real population/native acceptance still open. No new live deployment.

Latest integration supersedes the "record only" note below: adopt now retains
bounded serialized pendingAdoptions; both missing-body creation paths reject
unresolved adoption; exact same physical generation can be superseded by an
authorized fence, late ack cannot erase it. Death cleanup includes pending.
New FrontierV3ActorAdoptionPersistence is wired to lifecycle entity-write/save-pass
and runtime forget. Uses EntitySaveBatch futures+provider synchronization, exact
saved tuple and current loaded body, server-thread/runtime identity checks.
No ack on failed/superseded writes, duplicate candidates or changed body. Offline
legacy recovery refuses pending adoption; diagnostic ADOPTION_SAVE_PENDING added.
61754 green15s affected regressions;23428 green9s22 observer/batch/ledger tests;
30459 green11s5 offline-plan tests;45695 green7s3 actual-file persistence tests incl
mixed adoption+another actor release save. No running test/native process.
NEXT GAP: adoption still recorded AFTER creation; implement durable publication
before physical admission + failed-attempt handling. Initial births currently
uncovered. Then durable release ordering, same-body ownership/return recovery,
historical population and native copy verification. Live unchanged. Details in
audit "retained adoption and ordinary save acknowledgement". No SA closure.

Latest: atomic AmbientCarrierLedger SavedData save verified78007 terminal0/6s
(persistence2 + ambient departure5). Atomic file replacement is NOT cross-WAL/entity
ordering. Source confirms both ambient and scene adopt remove the inactive carrier
before entity persistence; do NOT eagerly flush mixed ledger before release until
pending adoption retention is wired. Added FrontierV3ActorAdoption record+strict
NBT codec only (NOT wired to ledger/callers yet): exact predecessor/admitted tuples,
same identity/kind, next epoch, independent declared recipient revision clocks.
79136 terminal0/7s,3 positive/negative/codec tests pass, diff-check clean. Audit
section "actor adoption durability gap" records remaining whole-flow integration.
Next: bounded durable pending transfer before body admission, ordinary save-pass
ack via existing lifecycle hooks/EntitySaveBatch, exact returned-body and successor
dispositions. No task process active from79136; live artifact unchanged.

Shared loaded release now resumes exact retained-carrier HOT/DRAINING interruption:
previous unconditional canFence rejected the already-created matching carrier and
release only accepted HOT. Exact fence is now idempotent; DRAINING requires it,
skips duplicate transition and completes observed body/health release. Actor loop
routes DRAINING to this owner, never resumes motion/another admission. Scene/ambient
witnesses and conflicts block this path. New native component test retains actual
body at7HP, fences it, records DRAINING, then calls ordinary drainForAdmission and
checks closed state/body retirement/next cancellation. Original ordinary path kept.
32260 terminal0/46s,8 scene-departure/death tests, all-dimensions-saved13:31:13.
Log build/sa-ambient-world-copy.j4JKe3/interrupted-release-native.log; prior native
directory moved to before-interrupted-release-20260923. Diff-check clean.
This proves interrupted in-memory/WAL transition composition, NOT power-loss
durability of carrier SavedData: carrier fence still only marks SavedData dirty
before canonical WAL/body removal. Need examine/close carrier persistence ordering
and UNKNOWN-with-returned-body+retained-carrier before claiming all crash seams.
Also historical other-resident repair and native population/frames remain open.
No task native process left from32260; live build unchanged.

NEW SHARED SA06/07 defect found by actual client census: corrected scenario53600
terminal1, expected24residents but observed4. census-server-view.txt shows many
other residents PREPARED/CARRIER_MISSING; repaired farmer7-13 remains HOT/INDEXED.
Source cause: UNKNOWN_AFTER_RESTART branch called AmbientLeaseRestartAbsenceObserved
on loaded blocks + no indexed UUID, without entity-storage readiness or retained
carrier, then next PREPARED inevitably fails custody. Old native test manufactured
fresh admission through an isolated materialize overload and missed this composition.
Guard NOW added: actual entitiesLoaded + exact same-generation inactive ambient
carrier, no conflicting/unfinished witness. Empty anchor alone leaves UNKNOWN rather
than falsely releasing ownership. Updated old GameTest intent;3125 terminal0/10s,
four focused policy tests incl missing/stale/HOT refusal and exact-carrier positive,
production/pilot compile. Native modified GameTest not yet run. This is prevention,
NOT full historical population repair or SA closure: still implement/verify explicit
recovery for all affected actors and remaining crash/release paths. Don't deploy a
guard-only patch as population restoration. Artifact2daaea does NOT contain newguard.
No screenshot acceptance: capture attempts failed (wrong CLI verb corrected; then
required screenshot dir supplied, but client already closed). No frame fabricated.

Isolated fullpackserver60969 stopped by ordinary RCON stop; terminal0,13:27:10 all
dimensions saved. Client53600 terminal1 and closed. No task server/client left from
this scenario. Live unaffected. Fullpack world and all diagnostic/failure logs retained.

Player ingress has now physically restored resident7-13 with original UUID:
at169732 actor HOT/INDEXED, observed148.4958,64,-5.7399, ALIVE20HP/NOURISHED;
field GROWING epoch4/stage4. hot-ingress-diagnostics.txt retained. No screenshot
acceptance yet.62688 terminal1: my scenario omitted the pre-visit population receipt
required by observe_settlement_population; observer correctly timed out beforeframes.
Fixed setup by explicitly querying settlement_population. This was a scenario-input
error, not proof of absent residents. Same-world retry is reentry, not pristine first
visibility; renamed milestone ordinary_visit accordingly. Authorized-failure manifest
retained. Current corrected client53600 ACTIVE, manifest player-ingress-with-census.json,
log player-ingress-with-census-run.log. Poll exact handle; do not spawn anotherclient.
Fullpack server60969 remains active. No live changes, no farmer visual/SA closure.

Cold recovered copy has completed TWO harvests with same resident7-13:
site-harvest-7-wheat-field-2 and -3; both successors production-7-332 and -385
TERMINAL, each64bread. At165906 field GROWING epoch4/stage3. Both relative advances
request1/10000 and request2/20000 terminalCOMPLETED at145246 and165569.
Evidence build/sa-ambient-world-copy.j4JKe3/cold-second-cycle.txt.
Checked-in targeted scenario sa-offline-farmer-copy-ingress.json waits for epoch4
before ordinary visit, observes population, exact actor and field, retains2frames.
Full-pack source client uses railway-client materializedpack on physical:0.
First runner57252 failed setup because pre-login op used online UUID138ad61c while
offline player joined as a4a58a7a. Confirmed ops+usercache; reapplied op afterlogin
to exact offlineprofile. Client was kicked for spamming failed diagnostics, did
not reach field. Disconnected clientPID2907929 deliberately terminated; runner
terminal1, failure manifest player-ingress.json retained. No product claim.
Corrected retry62688 ACTIVE, output player-ingress-authorized-run.log, manifest
player-ingress-authorized.json; poll this exact handle, do not start anotherclient.
Isolated fullpackserver60969 remains active. No live update. Visual skill consulted
to require actual player frames, not infer visual correctness from cold diagnostics.

NATIVE COPY NOW RUNNING full pack, not live: shell session60969, JavaPID2898696
(parent2898611), loopback25585/RCON25586, runtime
build/sa-ambient-world-copy.j4JKe3/full-pack-runtime, world=world.
Logs full-pack-server.log in parent evidence directory. Prepared15076 terminal0/5s,
artifactSHA2562daaea779a123cd7c0b1c30699ad6d34873277fa1ee38b67547454cba4e70647;
11869 verifyPackagedJar terminal0/5s. Installed only on isolated copy; old JAR
retained as .original-not-loaded, live remains old. Reused full live libraries/mods/
config, own memory2G/6G and ports; world derives repaired copy, originals retained.
Full-pack native recovered at13:15:11:103 prior HOT leases quarantined as UNKNOWN
pending observation (not a global runtime quarantine). No player connected yet.
At134868 site7 GROWING epoch3: second harvest job site-harvest-7-wheat-field-2
completed by resident7-13, original PREPARED deadlock gone. At137883 successor
production-7-332 TERMINAL,64bread available; field epoch3/stage1, farmer free/CLOSED.
Evidence cold-after-advance.txt. Operator advance10000 request1 accepted at135246,
target145246; latest remaining7373, still QUEUED (not terminal). Next poll same
server/request, finish next harvest cycle then ordinary player visibility check.
functions store copyRcon is configured to isolated properties; liveReadRcon stayslive.
This is exploratory real-world-copy evidence, not declarative native acceptance.

Failed setup retained:59802 world preparation lacked RCON password and failed.
An erroneous subsequent launch81335/PID2892064 used old disposable route-return
world; promptly terminated,143 terminal. That old mutable world is no longer its
original evidence snapshot (previous retained result manifests remain historical).
75539 corrected preparation terminal0.85824/PID2894644 then loaded correct copy
in lite profile but exited0 before simulation: full-world datapacks need missing
IDAS/IntegratedAPI etc registries. Logs native-server*.log retained. Both PIDs gone,
not product defects or usable farmer tests. Switched to full pack rather than strip
world dependencies. No stale test process on25585 before current fullpacklaunch.

Offline locked entry now implemented as pilot main FrontierV3OfflineActorRecovery
(inspect|apply WORLD_PATH EXPECTED_SEED EXPECTED_INITIAL_HEAD ACTOR EXPECTED_LEASE).
Uses exclusive session.lock + all-dimension UUID scan, Minecraft level.dat seed,
canonical recovery header/ruleset and exact requested head/generation; derives only
the registered graybox ledger path.16940 terminal0/11s:9 tests including entry
wrong seed/head/generation/held-lock refusal, read-only inspection, apply and retry.
No background runtime/shutdown/compaction created by this tool.

Actual stopped-world copy prepared at
build/sa-ambient-world-copy.j4JKe3/world; original preserved beside it as
world-before-recovery.68375 terminal0: live service stopped, port absent, copied145M,
then restarted unchanged JAR. No graceful Minecraft save lines were retained;
classify as stopped crash-recoverable copy, not graceful-save evidence.
Read-only live before stop:26755/134259 resident7-13 HUNGRY, PREPARED/CARRIER_MISSING.
Copy inspect terminal0: exactseed20260918065, head26784, generation2,77 entitychunks.
61911 terminal0: apply ON COPY ONLY -> head26786/CLOSED. Receipt+original ledger
backup retained in copy's graybox data; pristine world-before-recovery untouched.
Next run isolated native copy to prove actual harvest and following continuation;
canonical cancellation alone is not incident closure. No live recovery deployed.

Live now restarted same artifact/world (not a new deployment): MainPID2883737,
restart notBefore1790150912 (2026-09-23 13:08:32 +05); deployment verifier terminal0
FRONTIER_V3_DEPLOY_VERIFY=OK, port25565, fresh startup/no quarantine. JAR SHA512
unchanged a65c114f...c56b849ce3de. Server-ops procedure used to identify exact target,
restore transient service and verify restart. No reset/commit/publication.

Offline recovery durable publication implemented in pilot-only
FrontierV3OfflineActorRecoveryPublication. Immutable compressed receipt retains
original SavedData bytes, original canonical state, exact revision/instant,
world/path/actor/UUID and entity-file hashes. Receipt fsync+atomic rename+directory
fsync precedes carrier publication with the same durability, then registered WAL
commands cancel the lease. Exact retry permits only initial/DRAINING/CLOSED and
matching original/published ledger; rejects changed entity proof, head or custody.
No runtime checkpoint call (it would compact/delete original WAL).
93947 terminal0/10s:8 focused tests, publication test interrupts at all3 durable
boundaries and recovers from actual disk without graceful shutdown; proves receipt
backup, no duplicate commands, unchanged non-lease state/time, retained2 WAL files,
and changed entity-input refusal. Diff-check clean. This is component filesystem
evidence, not a live-world/product result. Still needs locked-world entry wiring,
exact world/seed/head validation and isolated-copy harvest/continuation check before
live use. No server mutation/deployment this turn.70707 compilePilotJava green5s.

Offline recovery plan now includes exact-state/custody composition (pilot only).
76363 terminal0/8s: seven focused tests pass (absence3 + recovery4), including
real FrontierFileStore cancellation/restart, idempotent retry with serialized
carrier ledger, interrupted DRAINING restart, and conflicting carrier refusal
before any canonical write. Prior three-test file-store run independently found
green in XML (07:54:43 UTC). Plan changes only the named ambient lease; no clock
advance. The apply method is NOT a durable multi-file publisher: caller must
retain world lock, persist plan/absence receipt and publish ledger before startup.
That publisher, isolated-world-copy verification and live deployment remain undone.
No live mutation, service stop, commit or deployment this turn; diff-check clean.

Offline recovery scanner foundation implemented in PILOT ONLY:
FrontierV3OfflineActorAbsence.withProof holds existing session.lock across callback,
inventories all dimensions, hashes every entity region, recursive UUID/passenger
search; rejects symlinks, unrecognized/external files/compression, malformed/overlap
sectors, truncated/missing entity NBT and bounded-size overflow. No repair callback
implemented yet, no live invocation, no world copy/mutation.43612 terminal0/5s:
3 tests cover nested UUID, corrupt/external/overlap refusal, other-dimension scan
and session lock still held inside callback.60862 test-only ambiguous assertEquals
overload compilation failure corrected. Diff-check clean. Next implement exact
canonical/ledger recovery publication and test on isolated copy before live use.

Existing-world recovery design recorded in audit under Accepted architectural
decision: stopped-world lock + exact canonical head + all-dimension recursive UUID
absence (strict corrupt/external-data rejection), isolated-copy first; registered
cancellation of PREPARED preserving actor/economy, explicitly NEW recovery carrier
for cancelled generation, evidence-bound receipt and partial-write recovery. Never
pretend offline absence is an old final-departure observation. Implementation remains
pending. No live maintenance started, no files copied/repaired/deployed this turn.

Historical incident read-only check at live106620/rev21505: resident7-13 ALIVE20HP,
NOURISHED/IDLE, PREPARED PATROL, physicalAdmission CARRIER_MISSING, UUID
941022a1-9431-381b-a497-bb9d553971ac, pending=false. Site7 stillREADY7/epoch2.
79172 read-only manual region-header/zlib+Minecraft NbtIo scan: all67 populated
saved entity chunks across3 nonempty graybox region files contain no resident7-13
tag;4 zero-length region files empty. Earlier78681 hit empty-file header, corrected
without modifying files. UUID-recursive independent scan2708 terminal0:67 chunks,
zero matches including nested passengers. This is saved-state evidence, not a mutation or an atomic offline-world
absence certificate; live service remains active. Need safe existing-world repair.

83829 terminal0/43s: all7 scene-departure/death native component tests pass,
complete shutdown12:43:15. New real managed body9HP: non-final leave rejected,
actual UNLOADED_TO_CHUNK captured, observation alone no carrier, subsequent unloaded
release closes canonical lease at9HP and retains carrier. This uses explicit fixture
removal plus observer invocation, NOT natural player leave/restart acceptance.
Prior6-test run directory preserved before-ambient-unload. After run, added loaded
release guard against any unresolved ambient departure because reservation handoff
executes before normal actor-loop witness guard; exact join must clear it first.
That last guard still needs negative native returned-body verification. Historical
missing carrier/live incident remains unresolved, no deploy or live mutation.

Ambient witness NOW WIRED locally: new AmbientDepartureObserver at actual final
UNLOADED_TO_CHUNK hook, strict declaration/health/current authority capture; exact
return matching withdraws witness (also retry after deferred projection/index join).
Executor suspends unresolved witnessed HOT/DRAINING/UNKNOWN work; unloaded release
requires current receipt, no indexed/pending body, actually unloaded observed column,
exact directed-goal handoff, retained carrier fence, and publishes observed health/body.
Removed previous unloaded canonical-health-only bypass.84778 terminal0/11s: focused
ambient+scene witness tests and pilot compilation passed before final deferred-join
retry edit. Actual observer/unload/return native composition still NOT VERIFIED;
do not deploy/claim historical missing-carrier repair. Live remains unchanged.

Ambient final-departure foundation implemented, NOT WIRED: FrontierV3AmbientDeparture
retains exact inactive declaration/ambient revision, final body+health and canonical
body+health baseline; accepts only same living HOT/DRAINING/UNKNOWN authority with
no scene owner. NBT fields strict. Shared carrier ledger now persists bounded
ambient witness/conflict maps, idempotent exact repeats, rejects duplicate recovery,
preserves first contradictory receipt, no automatic custody grant. Existing format5
without new fields remains empty evidence, not inferred history. 99863 receipt3
tests pass8s;37250 extended ambient5 + existing scene-departure tests pass10s,
diff-check clean. All handles terminal. Next wire actual final unload/return observer
and evidence-bound release, replacing unloaded canonical-health bypass. Not live.

66557 terminal0/38s, complete shutdown12:25:25; all6 native component tests pass.
Follow-through finds unloaded ambient boundary still incomplete: observeLeave and
drainObservedAfterDemandHysteresis intentionally return false; final chunk-departure
observer currently records scene/cargo only. releaseUnloadedReservedColdContinuation
can nevertheless close ambient using canonical handoff/health without a final-body
witness or inactive carrier. Must replace this bypass with actual ambient final
departure evidence, not enable stale LAST_OBSERVED. Loaded release fix remains valid.
72597 bounded read-only WAL1986..2587 investigation returned no retained matching
farmer events; snapshots establish lease transition, not which old adapter command
caused it. Do not claim specific loaded-vs-unloaded live release provenance as proven.
All current task handles terminal. Live unchanged; no deploy.

Shared loaded-body release now fences exact carrier AFTER physical validation and
BEFORE canonical DRAINING/release; ordinary drain, reservation drainForAdmission and
reserved COLD continuation call the same path. Observed physical health replaces
the reserved branch's stale canonical health. New real-body GameTest checks7HP,
carrier retention/body removal, next PREPARED cancellation retaining carrier.
19875 terminal1: test template coordinates outside canonical bounds, not product
regression; retained before-ambient-position directory. Corrected fixture places
observed body within canonical bounds (no navigation/visual claim).
66557 all6 scene-departure/death tests passed at12:25:14; shutdown saving in progress
at last poll. Existing old evidence retained before-ambient-release directory.
Do not deploy yet: historical missing-carrier recovery and unloaded reserved
release remain unresolved. Live farmer still requires safe repair of lost evidence,
not just future-release prevention. No live mutation/commit/publication.

LIVE applicability check37068: read compressed SavedData through Minecraft NbtIo;
ambient carrier ledger format5 has empty carriers/departures/conflicts. Thus new
conservative cancellation does NOT yet fix live farmer revision2. Do not deploy it
as incident resolution. Snapshot history49649: farmer7-13 lease1 HOT at6800,
CLOSED at21200, lease2 PREPARED at26400 (created26286 at completed COLD field body).
Source now identifies release bypasses: drainForAdmission calls release(discard=true)
without the ledger fence used by ordinary drain; drainReservedColdContinuation also
discards without a carrier and publishes canonical rather than observed health.
These are shared SA06/07 owner gaps, not a site7 exception. Next unify validated
physical release/fence for reservation paths, add real-body regression, then handle
already-missing evidence through explicit safe recovery (never fabricate history).
Read-only investigation only this turn; all JShell handles terminal. No live write.

42718 terminal0/9s: local shared ambient no-demand PREPARED cancellation added;
production/pilot compile and3 evidence-policy tests pass. Cancellation refuses any
indexed body or pending admission. Historical revision requires exact ledger READY
(inactive carrier retained); first revision without carrier requires loaded entity
storage observed empty. Stale/conflicting/missing historical evidence never permits
release. Uses existing canonical DRAINING/release, does not delete the carrier.
NOT YET native verified and not deployed. Need real adapter negative/positive
composition and confirm live farmer has the required ledger evidence; do not claim
this conservative path fixes every PREPARED case. Current no-demand handling still
defers absent first-admission bodies whose entity column is unloaded.

USER FIELD priority, latest read-only live investigation: at69361 site7 is READY7,
epoch2; at69550 settlement7 has active facility objective42188, idle farmer7-13.
Offline decode of immutable live snapshot15169 at69600 identifies exact PENDING
task:settlement-7-settlement_harvest_resource_site-42188 and its retained start
schedule due69603. Thus empty pendingHarvestSchedules in settlement view is NOT
missing work: that view filters only opportunity schedules, excludes task starts.
Farmer7-13 ambient lease remains PREPARED since26286, PATROL with identical
handoff/goal body148,64,-6. ResourceSiteHarvestProcess rejects PREPARED ownership;
its exception permits only exact HOT handoff. AmbientActorExecutor no-demand branch
drains HOT but merely clears volatile demand for PREPARED, retaining that lease.
This is a source-backed COLD liveness gap to reproduce/fix at shared admission
owner, not a proven physical wheat-age discrepancy. No players online, exact field
chunk unloaded; no live clock/force-load/world mutation. Global diagnostic green.
Latest user authorizes deployment of verified fixes to their reported incidents;
do not publish unfinished unrelated fixture changes or reset the world.
Interrupted fungible-production fixture provider is local WIP, not yet registered
in properties/tested. Preserve it but prioritize actual live PREPARED deadlock.

23288 terminal0/21s: ProductionModeCompositionTest now directly compares exact
and fungible input representations step-by-step, not only each against its own
COLD control. Equal route/cursor/worker, labor/deadlines, no early output, same
completion instant/output quantity/kind and exactly one wage pass. All3 composition
tests pass; diff-check clean. Native fungible ingress/full successor remains open.
Existing cold-production carrier DOES compare retained body to first placement;
its JSON status assertions alone understate its coverage. It still does not prove
terminal production. Next: reuse ordinary fungible job admission for native
COLD-start/ingress completion, rather than modifying the exact-theft fixture's
already-created job or claiming model observations as physical effects.

LATEST12886 terminal0: graphical route-return scenario passed, output
build/sa-remove-all-epochs-20260923/result.json. Prepared21348 terminal0/5s,
build/sa-remove-all-epochs-20260923/prepared-build.json; artifactSHA256
5adb1bc94ab3b9296fcc2a1d7ed86ebad325fffb7aa697d70e24df2454b94b95.
Ports25585/25586 and physical display0 verified free/accessible before start.
New scene diagnostic cargoPendingRetirements counts ALL pending exact carriers for
the current cargo, not only the current lease (old cargoCleanupPending scope kept).
31251 terminal0/8s: negative test proves successor PREPARED lease has false own
cleanup flag but still exposes1 old obligation.63682 invalid test epoch ordering
and52050 raw/prefixed formatter mismatch corrected, not production defects.
Route-return scenario now performs ordinary save-all flush in restart resumeSetup
and requires count0 at final HOT continuation; Node identity/schema5 pass.
At pre-restart action8 successor HOT lease still exposes one old retirement;
after graceful restart action15 at instant10424/revision322 has HOT, all3 bodies
and carrier CURRENT, cargoPendingRetirements=0. This proves the stronger retained
old-cleanup boundary missing from3312, not every crash seam or whole SA06/07.
Original2734956/replacement2738791 and runner no longer present. Live untouched.

90474 terminal0/49s: FrontierWorldStateTest and complete FrontierV3FixtureCatalogTest
pass with architecture guard. Last two stale assembly tests now start from ordinary
assembly semantic boundary, current identity and current instant; strengthened HOT
assertion checks every non-observed participant remains unchanged. Catalog now builds
all declared fixture profiles successfully. All30 failures from archived56760 have
subsequent affected-class green evidence; this is not a fresh full-suite pass.
No active task process. Next is remaining physical retirement/recovery and production
composition acceptance, not another unchanged confidence matrix.

34051 terminal0/44s: full FrontierWorldRuntimeDefinitionTest now passes after
repairing remaining supply fixtures. Shared test helper starts from actual
route-return shipment and re-arms ordinary operationProgress (native fixture alone
freezes that schedule). Physical receiver helper retains strict state validator,
metrics/reporter, marks exact west store observed/held; no production edits.
Actual current shipment11 used consistently; obsolete arbitrary2700 ingress removed.
Covers real COLD route arrival/64 delivery, WAL durable-before-effect, trusted executor,
sequential observed handoff, foreign receiver rejection, UNKNOWN recovery blocking,
HOT lease suspension and snapshot semantics. This is domain integration, not native
Minecraft IO/crash or full SA06/07. No test process remains active.

22696 terminal0/19s: TerminalLogisticsProcessTest4 plus architecture4 pass after
aligning isolated terminal fixture's pinned shipment/contract/cargo/intent IDs to
supply-1-11. Covers bounded receipt retention, active-operation rejection, confirmed
receipt compaction and snapshot recovery. The fixture constructs terminal state and
receipts directly: NOT ordinary physical loading/arrival/crash acceptance. No code
change to logistics production owners and no broad SA06/07 closure inferred.
No active process. Remaining runtime-definition delivery and physical-intent baseline
tests still need repaired initial supply prerequisites/actual identities; do not
blindly replace intentionally handcrafted ordinal2 fixtures or rerun full suite.

82291 terminal0/9s: reference_container now accepts only its actual fixed-report
id=f02b; arbitrary container IDs return not_found without leaking mislabeled
settlement1 tasks. Positive/negative/read-only regression passes. Local only.
49165 terminal0/13s: exact production-to-cargo64 assertion now uses actual semantic
route fixture boundary instead of arbitrary4000/ordinal2. New seed41 assembly
identity check proves operation:supply-1-11 and exact3 members30/16/28 before any
COLD assembly step. Checked-in native assembly scenario updated to that identity
and3-member expectations; all5 Node route identity/schema tests pass. Native
assembly geometry/player lifecycle still unverified, no acceptance inferred.
No active process. Remaining full-baseline delivery/terminal-logistics fixture
failures and SA06/07/08/09 scope remain; no full rerun, commit or deploy.

16442 terminal0/25s: all39 FrontierV3DiagnosticJsonTest cases pass. Settlement food
diagnostics now expose cycleOrdinal/cycleStartedAt/nextReviewAt/reviewOverdueTicks
from actual retained provision and checkpoint schedules; missing exact review is
null, tested against foreign remaining schedules (no cadence-based invented date).
14802 first run failed2 unrelated stale fixtures: supply ordinal2 and production
completion assumed by2200. Repaired test prerequisites to actual unique shipment
and ordinary route-admission-after-production boundary. No assertion weakened.
reference_container follow-up: existing intended ID is f02b (fixed reference
composition); bug is accepting arbitrary IDs and labelling that fixed view with
them, not proof that general container ownership is wrong. Still unresolved.
No deployment/new commit; no task process active. Source changes not yet live.

USER LIVE CONTRADICTION priority: field near140,66,-2 visually not growing while
others cycle. Read-only live review after11:42: site7,epoch2,GROWING3 at31192 and
32832. All12 sites GROWING3/epoch2 at32832; global green,0 incidents/conflicts.
Ruleset source interval3000 ticks (150s at20TPS): these samples alone do not prove
stalled canonical growth. First site7 harvest and bread successor TERMINAL.
Physical blocks not verified: vanilla conditional RCON replies empty; do not infer
age/absence from silence. No restart/advance/world mutation performed.
Additional live/source findings: settlement7 food SHORTAGE/available64/reserve42,
21 hungry/0fulfilled, needs actual provision/custody diagnosis; lifecycle diagnostics
report UNSOLVED saturation for zero-capacity/no-payload owners (0>=0); reference_container
ignores requested container7 and hardcodes settlement1 (not useful site7 evidence).
Performance contains old completed-harvest queue lags and12922 dropped attributions;
not current backlog/TPS proof. No post-start PM exception/quarantine found in latest.log.

Read-only follow-up at33774: site7 advanced naturally to GROWING4/epoch2.
Canonical growth stall contradicted; physical correspondence still unverified.
Provision reviews are24000 ticks apart, initial settlement7 review at24600;
next ordinary review48600 by current producer cadence. Need actual schedule/food
arrival evidence before classifying delayed SHORTAGE as product defect.
Confirmed pressure diagnostic fixed: explicitly non-physical owners report OPEN
instead of comparing zero retained intents >= zero quota. No admission changes.
82462 terminal0/16s: lifecycle composition12 (new empty-queue regression), assembly9,
HOT assembly admission/deferral2, architecture4 pass. No test process running.

Before user interruption, assembly fixture WIP now shares route custody's scoped
birth isolation, selects unique actual ASSEMBLING shipment and reads canonical state
without per-tick codec. Two runtime assembly tests and OperationAssemblyTest updated
to actual fixture identity. Compiled/verified by82462 above. Native assembly
scenario still old shipment ordinal2/two members and must align after identity check.

56760 terminal1: complete :pale-mirror-frontier:test on compiled8a626aca,
887 tests / 30 failures, 7m31s. Baseline XML retained separately under
build/sa-domain-baseline-8a626aca-20260923/test-results/.
Several failures share obsolete supply-1-2 fixture assumptions after SA08/09 labor
changes; autonomous scout sighting still fails and needs evidence-based diagnosis.
Do not repeat full suite before reading its final XML/result. Live server untouched.
Post-compilation source WIP (not in56760 binary): native fixture planner now delegates
the same registered schedule-retirement policy as production through a shared facade;
StrategicScheduleRetirementTest asserts wrapper parity. Two focused consumer tests
(OperationFront/HumanAssignmentProjection) now select the real route-return shipment
instead of obsolete ordinal2/arbitrary2750 ticks, preserving all ownership assertions.
54015 handle now terminal/missing; retained XML confirms all6 focused tests passed
(HumanAssignmentProjection3,OperationFront1,StrategicScheduleRetirement2).
Further test-only route/scout consumers select the same fixture's actual initial
shipment, retaining exact IDs for continuation.38618 terminal0/29s: route transition2,
route repair2, patrol6 plus architecture4 pass.19846 terminal1/22s: HOT scout4 and
architecture4 pass; autonomous supply interception still fails. Its improved failure
reports operations={},contracts={},production={} at10000; do not attribute missing
sighting to scout code without tracing earlier supply admission/history. Replaced
per-tick full codec serialization in that behavior test with immutable canonical
read, retaining every tick and all assertions. No timeout extension or new commit.
Autonomous failure now diagnosed and repaired in test preconditions:62860 terminal1/10s
history shows initial food/reserve78/78, ordinary production completes, birth consumes
one ration, final food141/reserve80 leaves61 rather than required64 for export.
Autonomous perception fixture now supplies3 additional initial bread (one birth ration
plus two new resident reserve rations); production planners, birth schedules and
reserve rules unchanged.60113 terminal0/35s: all15 HiveRouteEngagement tests plus
architecture4 pass. Bounded ten-sample failure history retained without topology dumps.
No task-owned test process remains active. Next: remaining baseline fixture
consumers/assembly and physical supply composition, not another full suite.
Live read-only recheck: revision2048/instant21011, green, zero required/inventory
conflicts,12 settlements/366 residents/49 bioforms. No additional live mutations.

CURRENT DEPLOYMENT completed with explicit user authority for fresh world and local
release commit, no push. Source checkpoint8a626acaa286a8390a2c1c628de09253785ffe67
contains244 implementation/test/scenario files; unrelated governance redirects/WIP
remain unstaged. Clean detached build /home/rd/proj/pm-sa180-release-20260923.
26249 packaging/verifyPackagedJar + targeted retirement regression green8s (cache
reuse identified, not fresh test execution). Clean detached preflight passed.
Live new world frontier-v3-sa180-20260923, same seed20260918065, schema180;
start11:16:07 epoch1790144167, MainPID2628708, Java2628734. Installed SHA512:
a65c114f0dbcb9b274495f8032e479ba42cf10a0401e8c6e859a77729131ce59a4cc92277953c68566aeff49940d7d060412251d6cbd7476fbf2c56b849ce3de.
Post-start deploy verifier passed. Read-only RCON: revision23 instant1176/1177,
status ok, diagnostic green,12 settlements/12 sites/366 residents/48 bioforms,
zero required/inventory conflicts. Not full graphical acceptance/HUMAN_CANDIDATE.
Old world frontier-v3-sa-diagnostic-20260923 retained intact; config/JAR/log backup
/home/rd/far-frontier-server/.far-frontier-installer-cache/sa180-rollback-20260923.Ierit5/.
Old JAR additionally recoverably archived by installer. Loopback artifact server
2628250 stopped after install; no publication, deletion or client update.
Active implementation checkout remains pm-f06r3-facility-lane-recovery, now8a626aca.
Historical live179 facts below describe the superseded diagnostic deployment.

User explicitly prioritized live quarantine repair. Source cause reproduced:
strategic bounded compaction drops terminal task while pending infection pulse
remains; eventual stale-pulse cancellation is too late for reference closure.
Implemented registered reducer-owner exact schedule retirement, same WAL batch,
lazy queue view, unchanged strict validation.5852 terminal0/22s,48 focused tests
pass, including old-policy reproduction and new-policy WAL replay.72074 terminal0/11s:
added WAL-failure atomicity regression and NeoForge compile pass. No live reset/restart
or deployment. Full SA objective remains active, latest source schema180 vs live179.

LIVE INCIDENT confirmed after user reported empty world: at09:54:13.531 installed
diagnostic runtime quarantined with `scheduled action references a retired canonical
subject: schedule:hive-infection-task-task-hive-frontier-hive_expand_infection-417-7`
while executing frontier.objective.review. Live Java2273025/service2273001 remains
active; user rd joined10:47:28, after quarantine. Installed SHA512 still matches
the recorded diagnostic artifact, world unchanged. No restart/reset/update done.
Prioritize this product contradiction; do not call live suitable for manual tests.

79728 terminal0: graphical abrupt cargo scenario now passes, output
build/sa-cargo-abrupt-sync-continuation-20260923/result.json. Prepared36043 green3s,
artifact9173fe7206c6e06b55f501314f756e9540438762166dcae46beb2c04d8e0e2f0.
Actual logs show superseded-proof rebuild then CLOSED/cargoCleanupPending=false
at10:48:17 after restart; canonical player64 retained. This closes this specific
pending-cleanup reproduction, not all crash windows or whole SA06/07.

37623 terminal0/52s: all12 cargo-interaction tests passed with superseded-sync
continuation fix; complete shutdown10:37:52, independently rechecked in latest.log.
26791 terminal1, same abrupt cleanup pending (build/sa-cargo-abrupt-read-barrier-20260923),
full shutdown10:35:47. Read barrier alone insufficient. Source callback also dropped
the entire completed-pass request when any new observation superseded sync ticket.
Now it rebuilds candidates/current sync proof on generation change, never accepts
stale proof; new native case supplies read during controlled pending sync and no
second save. Log confirms generation1 -> generation2 rebuilding branch executed.
Graphical verification of this last fix remains pending; no task-owned run active
at continuation entry. Previous interrupted documentation patch did not apply.

5626 terminal0/51s: EntitySaveBatch9 + cargo-interaction11 tests pass for
deferred read-observation continuation, full shutdown10:32:25.
Native88262 terminal1/shutdown10:30:36 (build/sa-cargo-abrupt-cache-20260923).
Added bounded save-pass diagnostic established exact stall at10:29:52:
passComplete=true,ticket=false,candidates=1,loaded=false; sole footprint column
known absent, writes7,reads613,pendingReads609,no failures/overflow. Thus completed
save returned before server-thread read observations; no continuation was registered.
Repair: optional pending-read barrier, one callback/index, resume completed pass
on server thread, rebuild candidates/current write+sync proof, preserve read/write
failures. Unit negative + new native delayed-read test supply no second save.
Cached provider paths remain fixed; previous native did not prove full closure.

41645 terminal1, same pending-cleanup failure after early-read repair; shutdown10:25:01.
Output build/sa-cargo-abrupt-early-read-20260923/result.json; prepared49925 green3s,
SHA2565ef676dffc665679994accb7fb9ebd7c17890774fcc894e6975ae14807cf8c1f.
No live changes. Do not run again before addressing provider cache boundary.
Actual Minecraft source EntityStorage.java inspected from cached NeoForge sources:
loadEntities bypasses SimpleRegionStorage.read on emptyChunks cache hit;
storeEntities skips SimpleRegionStorage.write for already-cached empty column.
Our mixin redirects ONLY those IO calls, so cached absence never reaches PM.
Furthermore vanilla adds emptyChunks BEFORE submitting deletion write; a failed
footprint-fenced write leaves cache incorrectly empty and suppresses retry.
Repair implemented in EntityStorageMixin: observe cached-empty load and skipped
empty-store branches as READ evidence, never a successful rewrite; invalidate
vanilla empty cache on actual/fenced write failure using server thread. This
allows a future deletion retry and preserves failed-write fencing.8891 terminal0/8s,
EntitySaveBatch8 + CleanupPersistence11 tests pass; main/pilot compile and diff-check
clean. Native mixin/cache composition and original abrupt flow NOT yet rechecked.
No active task process. Next: targeted provider/native verification, not more
timeout inflation or a claim that early-read alone fixed the incident.

Early-read admission repaired: CargoCleanupPersistence now records bounded actual
entity reads even before a terminal retirement exists, matching write coverage.
Removed obsolete column prefilter and its test; replacement server regression
reads absence before registered close, supplies no second read/write, then syncs
and verifies terminal ack.99538 terminal0/49s, all10 cargo-interaction tests pass,
full shutdown10:22:00. This proves the ordering defect, not yet the cause/repair
of native55691's pending cleanup. Preparing corrected candidate for that flow.

55691 terminal1 graphical disposable-released-cargo-destroyed-abrupt.json.
Output build/sa-cargo-abrupt-20260923/result.json; prepared-final.json from35092
terminal0/3s, artifact SHA2568735c77281515e3586e4c57f2b0d33e91df274c7187e9ff777553a65835d286d.
Node route identity4 passes, profile scenario count11. Scenario uses ordinary
theft64bread, actual empty-cart removal, authenticated disconnect then exact JVM
abrupt stop, restart and canonical player64/INTERRUPTED/CLOSED pending=false.
This is NOT a rendezvous inside entity storage write or a physical player-item
inventory assertion. Before-restart all8 actions passed; replacement server
recovered player quantity64 and INTERRUPTED but cleanup stayed pending through
30s assertion. Ordinary save completed10:17:05; final shutdown10:17:50. Failure
bundle result.failure retained, no automatic rerun. Archive e1754fb1...-1.nbt
retains revision123/epoch1, removal flag1 and sole column(-23,-22), so the final
removal observation itself survived. Cause of missing ack not yet established.
Source gap to examine: observeRead ignores all reads while pending ledger empty,
and filters other columns before later retirement can exist; write observation
already retains all UUID coverage precisely to avoid late-retirement ambiguity.
Also inspect save-pass candidate/read-completion ordering (single save may precede
available coverage). Do not label this solely a timeout/harness problem. Live untouched.

65498 terminal0/44s: all9 cargo-interaction GameTests passed after removal-retry wiring,
with ninth test exercising real final-removal observer, unavailable birth archive,
blocked empty-column write, original evidence restoration and successful retry.
Full save/shutdown10:12:35.46914 failed compilation only (test IOException handling), fixed.
No new world/server deployment. Prior62333 evidence directory preserved pre-removal-retry.

New helper CargoRemovalRetry retains nominal final-removal observations before IO;
MAX_PENDING4096, at most8 writes/pass, no eviction on failure. Observer flushes
before EntityStorage writes, save candidate refresh and final cleanup ack;
successful retry invalidates archive index. Capacity/invalid-identity loss fences
entity saves until restart, not a transparent recovery claim. Weak per-level map
contains no level references in values.49979 first7 component tests passed10s;
31211 observer3/retry4/cleanup11 passed9s, before latest bounds-exception wrapping.
Crash window and overload UX/native recovery still need verification.

Native session3312 terminal0: disposable-route-scene-return.json, output
build/sa-remove-route-return-20260923/result.json. Existing scenario tests ordinary
HOT departure, COLD route continuation, return and graceful restart. It does not
by itself assert the original retired UUID's cleanup after a successor lease.
Actual player return/restart assertions passed. No task-owned run remains active.

Latest user authorized a server update. Packaging18116 passed in5s (jar and
verifyPackagedJar); deployment NOT performed. Current WIP is not a clean detached
release ref, and existing-world missing-birth footprint compatibility remains
unresolved below. Live service2273001 confirmed active, same world, unchanged.
Resolve these boundaries before replacing the live artifact; no reset authorized.

Prepared94414 terminal0/3s:
build/sa-remove-route-return-20260923/prepared-build.json,
SHA2566178a406b7077b1c448d362a3bb4eb9a9cc48e96d07bb2f7b5944d0c204b2427.
Runtime25585/RCON25586, physical display0; live server25565/25575 untouched.
All earlier task sessions terminal. No other monitoring claim.

## Finding status and retained evidence

Scoped CLOSED: SA01 scheduler eligibility, SA02 fair field writer, SA03 shared
physical selection, SA04 terminal field disposition, SA05 field classification,
SA10 incremental reference closure. OPEN: SA06/07 custody/recovery and SA08/09
production composition/product requirements. Whole goal NOT complete.

- SA01/10:87 focused tests.
- SA02: native partial-A/B fairness plus player A/B/A59566,212.999s,
  build/sa02-demand-return-20260923/result.json.
- SA03: all8 families shared selection; production-effect87616,5 tests/25s.
- SA04: shared canonical-death readiness;99826/10556, not every family lifecycle.
- SA05: first visibility/restart1320,229s,
  build/sa05-first-visibility-20260923/result.json; zero-player24000,
  successor epoch2 GROWING survives restart.
- Actual HOT harvest64 -> wheat64 -> production -> bread64 -> growing epoch2:
  56548, build/sa-harvest-successor-ascent-20260923/result.json.
  Shared exact-ascent livelock repaired; local-navigation18 green.
  Not second harvest, M3, co-op or clean-room acceptance.
- SA08/09 common20canonical ticks/unit,80total, retained worker/input handoff and
  PREPARING fencing implemented. Midwork native restart28658,161s,
  build/sa09-midwork-native-20260923/result-correlated.json.
  Repeated switches, equivalence and full successor story remain open.

## SA06/07 implemented, with evidence limits

Common release uses final custody-bound departures, not stale LAST_OBSERVED.
Entity storage readiness is distinct from loaded blocks or confirmed absence.
Exact canonical death permits terminal drain without inventing living bodies.
All8 SceneBehavior owners explicitly select recovery disposition; terminal jobs
drain, ordinary valid work resumes. No default HOT or recovery-evidence recheck latch.
Native78091 all8 scene-strikes green49s includes same-entity reinspection after
missing declaration; this is recognition recovery, not disk reload/crash.

Pending strike effects can inspect exact witnesses while DRAINING. Missing living
target becomes explicit UNKNOWN, never new damage or inferred death. Dead target
receipts preserve canonical death; unconfirmed effects retain fences. Lethal
region-save/crash and receipt compaction acceptance remain incomplete.

Cargo now has:
- bounded canonical retirements independent of CLOSED history; durable WAL ack;
- immutable cleanup receipt archive plus synced .saved markers and retry compaction;
- birth/epoch footprint archive, actual pre-write columns and final removal observer;
- exact full-column absence coverage, failed-write fencing and fair8 ack/pass;
- natural-read admission for older REMOVE columns, not just final departure column;
- separate RETAIN-surviving, RETAIN-destroyed and REMOVE proof paths;
- natural return after unsaved removal permits new columns; new columns invalidate
  older .saved footprint proof before archive publication;
- player handoff mutates caption/provenance only after durable acceptance;
- exact-item missing provenance repaired only from committed custody, without
  restoring quantity/items or overwriting foreign declarations.

Recent native evidence:
- 57569 terminal0/89.268s: actual64bread theft + graceful restart, player retains
  cargo and delivery INTERRUPTED. No cleanup claim from that original run.
- 46144 terminal0/92.926s:
  build/sa-cargo-cleanup-save-20260923/result.json. Running-server save after
  restart yields CLOSED/pending=false; footprint directory empty.
- 14689 terminal0/92.625s:
  build/sa-cargo-destroyed-observed-20260923/result.json. Actual selected empty
  cart removal, full save/restart,64bread retained, CLOSED/pending=false.
  Pilot requireRemoval now waits for observed removal, not attack-count exhaustion.
  Java scenario42/Node3 checks pass. These prove graceful RETAIN composition,
  not abrupt crashes or full REMOVE acceptance.

Latest source fix AFTER those native runs:
Loaded REMOVE closure previously allowed history compaction before cleanup witness.
Shared SceneExecutor.release now calls prepareRelease after physical checks,
before member fencing/canonical close. Loaded and actual-unload capture have
separate guards and common contents/epoch validation. Archive preparation may be
atomically refreshed only by exact current DRAINING owner with no pending retirement;
saved terminal witnesses remain immutable. RETAIN ack also marks/compacts prior
REMOVE preparation after actual clean save, without changing player contents.

1350 archive6 green12s. Native63033 all7 cargo-interaction green39s, shutdown09:50:29:
no-history REMOVE deletion and staged REMOVE -> registered UNKNOWN/HOT input ->
player RETAIN -> failed-write retry -> metadata cleanup. Controlled IO futures;
NOT actual disk/process-crash proof or full generic release with natural bodies.
Earlier25661 wrong unload-only producer fixed;90416 DRAINING interaction fixture
corrected;72533 parallel shared-level index fixture corrected. All retained in
named preclose-*-red sibling directories. Current GameTest directory63033.
Diff-check clean at last review. Source not deployed.

## Remaining work

Latest safety repair: CargoCleanupPersistence no longer treats an empty footprint
inventory as permission to acknowledge REMOVE or surviving RETAIN. Both candidate
selection and final archive-marker boundary require an exact birth/epoch footprint.
78717 focused footprint + cleanup tests passed in9s, including empty/wrong-epoch
negative coverage for both dispositions; diff-check clean. This prevents false
completion, NOT migration/recovery of missing historical columns. Need native
composition and explicit old-world policy; no deployment occurred.

Native follow-up:75644 terminal0/40s all7 existing cargo-interaction tests pass.
Added real-cart regression removing its archive after ordinary RETAIN handoff,
then supplying a successful save+sync: exact retirement must remain, inventory
and player cart unchanged.62333 terminal0/42s all8 passed; full shutdown10:03:30.
Controlled provider futures, not an OS-crash claim. Prior directories preserved.
4820 used invalid slice scene-cargo-interaction; no tests ran despite Gradle exit0.
Correct slice is cargo-interaction. All handles terminal, live server untouched.

1. Inspect retained native3312 evidence and close real shared release/REMOVE composition.
   Never infer original retirement cleanup merely from a successor HOT scene.
2. Legacy schema policy implemented: format180 requires birth-time cargo footprints;
   header selection and hydration reject177/178/179 without rewriting input.
   Existing contract is fresh-world-only, not a migration mandate.56292: codec
   roundtrip/legacy rejection + cargo retirement/disposition tests green13s.
   Live world remains179; next deployment needs an explicitly authorized fresh
   world, preserving the current one. No reset or deployment performed.
   Still open: missing/corrupt evidence within180, capacity/backpressure/recovery
   and lost final-removal IO handling. Versioning does not resolve these cases.
3. Finish actual crash/save seams for cargo and combat, without replaying effects,
   restoring player assets, UUID-only deletion or retaining all history as a substitute.
4. Finish SA08/09 repeated HOT/COLD/full production successor story; audit every
   original criterion and all affected owners before closure.
5. Diagnose remaining pure-COLD HiveRouteEngagementProcessTest scout-sighting
   failure (20382:36 tests/1 failure). Historical full frontier820tests/10failures;
   many repaired since, but no fresh full green. Architecture oversized-class debt
   remains; never raise ceilings to pass.

Later sequence: F0.6R3 acceptance -> OBS002/VIS002 -> ARC001E before MAT006.
M3, co-op, clean-room and load gates remain open. No new breadth.

## Live diagnostic deployment

User-approved earlier diagnostic update remains installed; NOT HUMAN_CANDIDATE.
Service far-frontier-v3-live.service, MainPID2273001 at last check, start08:34:58.
World frontier-v3-sa-diagnostic-20260923, seed20260918065; no reset.
Installed /home/rd/far-frontier-server/mods/pale_mirror-hosted.jar SHA512:
63db48af1bb8bc73323262da4885c2504e0e8b793398d0f8e18bee7363e9373cb5b6e6bad9fc82c71bec37c0084b2d680ef3b99999ff1d518242459353afd504.
Deployment18367 package checks and69538 deployment verifier passed.
Backup: /home/rd/far-frontier-server/.far-frontier-installer-cache/sa-update-rollback-20260923.LvRuRP/.
All later cargo work above remains local. Preserve old worlds and live player work.
Pack dirty AGENTS.md/.f0v-baseline and source extensive WIP retained; no commits.
Site1 firstcrop(-343,64,-346),board(-339,67,-338), dimension pale_mirror:frontier_graybox.

## Operational facts

Pinned Java21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2/bin.
TMPDIR and JAVA_TOOL_OPTIONS java.io.tmpdir:
 /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror/build/sa08-tmp.IbCODN
(/tmp quota issue, not full disk). Native display0 XAUTHORITY:
 /run/user/1000/.mutter-Xwaylandauth.AZ4VT3
Verify availability/ports before new run; one graphical client only.
Scene GameTest task resets exact build/runs/frontier-v3-scene-game-test:
preserve completed previous directory by validated unique rename before reuse.
Do not touch unrelated services. Read credentials in memory only; never print.
Project server ops/release skills and execution semantics already read; reuse
unchanged instructions. No deployment until verified and authorized.
