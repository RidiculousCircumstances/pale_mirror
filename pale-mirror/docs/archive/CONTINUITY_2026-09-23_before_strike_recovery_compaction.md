# Continuity Ledger

## Goal (success criteria)

- Temporary active goal: main alone closes SA-01–SA-10 under the accepted
  [static audit](docs/frontier-v3-static-audit-2026-09-22.md), including affected
  integration and product evidence. No subagents. Narrow passing tests do not
  close the whole F0.6R3 player story.
- Long-term Frontier v3 scope and stage sequence remain in the contract,
  execution semantics and implementation plan. No production cutover/v2 removal
  without the required automated, recovery, load and human gates.

## Constraints/Assumptions

- Current execution mode: **single executor — main**. Main owns code, tests,
  diagnosis, technical review and documentation. Terra is stopped. Old PM-only/
  sole-Terra wording is historical, not a current prohibition or delegation grant.
- Canonical governance: /home/rd/proj/pm-governance/pale-mirror.
- Active implementation: /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror.
  HEAD eaaa6e70d153e32c4856a5dabc487990e3cf9e06 plus uncommitted fixes.
  Preserve prior FrontierV3ResourceSiteHarvestExecutor receipt-diagnostic WIP.
- Original /home/rd/proj/minecraft and its nested Git history remain preserved.
  Their entry files redirect here; neither is the current source checkout.
- No new live deployment, reset, publication or commit is implied by this
  correction. Inspect exact authority and targets before operational mutations.
  Source/test work and relevant local verification are authorized.
- Preserve canonical HOT/COLD history, exclusive custody, typed REL/ARC identity,
  deterministic recovery, bounded execution and player changes. No fabricated
  native evidence, forced chunks, silent fallback or acceptance by unit count.

## Key decisions

- Further workflow cleanup: removed remaining blanket per-edit HOT/COLD test
  wording, compulsory foundation-document rereading and repeated pre-commit
  architecture audit wording. Select coverage for changed behavior and reuse
  valid context/evidence; inspect staged changes before an authorized commit.
  Product criteria, technical invariants and operational authority are unchanged.
  Documentation only; no test campaign or runtime mutation for this cleanup.
- Further user-approved workflow cleanup: consult orders/contracts for unresolved
  scope or invariants, not ceremonial rereading; choose isolated run-directory
  count freely; select negative/recovery coverage by changed behavior rather
  than a critical-file label. Existing relevant evidence is reusable. Storage
  cap, operational safety and product acceptance remain unchanged. Docs only.
- Removed the remaining blanket automated-promotion prerequisite for human
  inspection. Targeted diagnostic visits may precede full acceptance; formal
  HUMAN_CANDIDATE/M3 claims retain their evidence requirements. Documentation
  only; no runtime change, test run or deployment.
- Further user-approved workflow cleanup: removed exact ledger headings,
  duplicate plan synchronization, pre-edit risk-label/checklist requirements and
  mandatory release-skill consultation. Select checks by affected risk; actual
  release/product criteria and safety remain unchanged. Documentation-only edit.
- User-approved further instruction cleanup (2026-09-23): project-local skill
  routes are now on-demand references, not compulsory topic-triggered loading.
  Selected/host-required skills still follow host rules. An evidenced invalid
  harness deadline may be corrected without approval; masking product hangs
  remains prohibited. No product criterion, safety boundary or code changed.
- User-approved workflow cleanup (2026-09-23): removed fixed hourly self-review,
  two-hour reporting deadlines and mandatory four-worker matrix prescription.
  Retain useful parallelism and cumulative-cost/stall assessment without ritual
  gates. Product acceptance, technical invariants and safety are unchanged.
- SA repairs belong to common owners: fair scheduling, evidence-aware release,
  representation-neutral production and reference closure. No farmer-only
  coordinate bypass, database/ORM migration or speculative universal rewrite.
- Current instruction cleanup: one canonical entry and compact ledger; read
  unchanged instructions once per retained context, not on every automatic turn.
  Re-read changed instructions or after context loss. Archives are on-demand.
  Work in coherent implementation/verification blocks, not bootstrap/report loops.
- Follow-up cleanup removes mandatory all-tools startup smoke and prescribed
  repository-context/Serena ordering. Tools are selected on demand; direct
  source analysis is valid. Remaining tool-role references use executor, not
  Terra. Technical invariants and claim-specific acceptance remain unchanged.
- User-approved final workflow cleanup removes remaining per-edit native/test
  ordering and compulsory map/selector queries from active instructions.
  Protocol METHOD-AUTONOMY explicitly governs method prescriptions in skills
  too. Exploratory diagnosis is allowed; repeatable native/product acceptance,
  required skill reading and technical/safety constraints remain unchanged.
- Choose diagnosis/tests by information gain and affected risk. Reuse valid
  evidence and accepted F0.VA/VB/VC tools. Justified reruns are allowed; no
  confidence-only matrices, timeout inflation or mandatory method sequence.
- Real-client graphical preflight remains required before human-candidate claims.
  Inspect the actual relevant story and frames; diagnostics are supporting
  evidence. Human M3 is never inferred. User contradictions outrank narrow greens.
- Actors must appear at truthful retained positions on first visibility.
  Harvest must lead through resources/production to a successor cycle without
  losing the farmer, replaying work or freezing unrelated settlements.

## State

### Done

- Workflow cleanup completed: canonical ledger 778 -> 110 lines before this
  receipt, protocol 585 -> 130; original/implementation entries redirect here.
  Historical bytes preserved in archives. Governance guardrails passed 34 tasks;
  original and implementation redirect/skill checks passed 2 tasks each; four
  edited skills validated. No runtime code or deployment changed by this cleanup.
- Static audit and owner-level remediation accepted; active order revision 23.
- SA-02/04/05 focused repairs: field selection, terminal conflict witness,
  mature-successor regrowth and reverse cursor. 32 NeoForge tests passed;
  native leave/re-entry/restart and local-disposition composition still pending.
- SA-10: incremental retained-schedule reference checks before WAL and during
  replay, including initial schedules. No full-world re-audit for unchanged state.
- SA-01: registered owner holds excluded before budget admission, exact retained
  continuation, eligible-head ordering and matching live/replay consumption.
- Latest targeted kernel/queue/harvest/supply/ArchUnit: 87 tests passed in 25s;
  only indentation changed afterward. Governance guardrails: 34 tasks passed.
- Detailed commands, failure inventory and claim boundaries are in the audit.
  Prior source/pack/runtime receipts remain usable only within unchanged scope.

### Now

- Strike recovery WIP: new FrontierV3SceneStrikeReceipt stores one target-local
  NBT witness (world, scene/revision, explicit lifecycle owner, effect epoch,
  target UUID, exact observation). ExecuteStrike writes it after actual hurt,
  before confirmation, and removes only after accepted WAL confirmation; exact
  retained confirmed observation also clears a torn-save leftover. UNKNOWN can
  inspect matching saved witness without hurt/distance replay; missing/foreign/
  health-diverged evidence does not execute. Pre-hit effect fence now checked.
  Unit81208 terminal0,10s:2 codec/negative tests pass; no native test yet for this
  new path. Important limit: ordinary same-tick hurt->confirm->clear does not
  force entity storage; this marker is NOT an atomic WAL/entity-save guarantee.
  It can survive a failed confirm followed by entity save; abrupt-crash gap,
  lethal target, missing witness bounded disposition and retained-history
  compaction remain unresolved. Do not declare recovery complete or deploy.
- SA06/07 combat follow-through: native new conflicted-strike test exposed
  FrontierHiveProcessModule requiring hit observation for CONFLICTED, impossible
  under transition API. Fixed owner verification: CONFIRMED requires evidence;
  CONFLICTED must preserve actor state and observation history. ExecuteStrike
  now permits effects only for PREPARED/RUNNING, never terminal CONFLICTED.
  Native39602 terminal0,31s:2 tests pass (ordinary real hit/receipt plus registered
  RUNNING->CONFLICTED and two no-damage executor calls), shutdown06:55:35.
  Red41658 failed at conflict admission, so it does NOT prove actual replayed
  damage before fix; static branch risk separately corrected. Initial62648 was
  missing test import only. New scene-strikes slice isolates these2 cases.
  No deployment. UNKNOWN exact-hit crash recovery remains unresolved; medical
  UNKNOWN uses shared reclaim except PREPARED exact-body reassembly. Cargo full
  save/crash + destruction-before-save and SA08/09 product verification remain.
- Fixed last turn's red cargo-drain race at the owner: LogisticsSceneCause now
  carries non-null explicit carrierDisposition (stable existing1/2 wire tags).
  Admission declares REMOVE; accepted CargoCarrierReleased atomically declares
  RETAIN with DRAINING. Status/position/recovery copies preserve it; RETAIN cannot
  enter PREPARED/HOT. confirmCargo consumes this declaration, never inventory
  membership or phase. Snapshot format179 (was178); logistics WAL envelope0xfffd
  rejects old0xfffe rather than defaulting a missing disposition. Assault format
  unchanged. Existing worlds178 must not be opened/reset silently under179.
  Unit67890 terminal0,20s:16 focused tests +4 architecture checks pass; release6,
  retirement8, codec2. Red regression now green across pickup, UNKNOWN/restart,
  snapshot and closure; missing/forged wire values and old envelope reject.
  Earlier73519 also compiled all NeoForge tests (28s). No deployment/native
  rerun yet. Still open: whole production-dimension save/crash chain, destruction
  before first post-release save, retry/supersession, medical/strike recovery,
  and SA08/09 product criteria. Next prioritize these, not another tag helper.
- NEW reproduced SA06/07 causal defect: confirmCargo chooses retirement disposition
  from CURRENT inventory.hasWorldCarrierCustody. Player moving all cargo during
  DRAINING makes it choose REMOVE_PROJECTION after an accepted irreversible
  handoff. Added playerTakingAllCargoBeforeBodyDrainCannotTurnReleasedCartIntoRemovableProjection
  regression in CargoCarrierReleaseStateTest: normal release -> actual observed
  fungible transfer to player -> close scene. Session39530 terminal1,10s fails
  expected RETAIN vs actual REMOVE; four included architecture checks pass.
  Initial84420 failed only fixture assumption (cargo is fungible, not exact),
  corrected before identifying actual failure. This new regression is RED.
  NEXT PRIORITY: persist explicit irreversible carrier disposition in owning
  LogisticsSceneCause, set by CargoCarrierReleased, preserve through copies/
  recovery and encode with stable tag/version; confirmCargo consumes that value,
  never inventory membership or OBSERVED-phase inference. Six constructor sites
  located (SceneLease2, registry sample, payload legacy reader, state codec, test).
  Need current/legacy payload boundary review and snapshot version increment.
  Do not deploy current candidate. Logistics abortPrepared has no production
  caller: only ProductionWorkScenePreparationAborted invokes it after validating
  production cause; do not invent a new cargo-abort workflow just to close a note.
- SA06/07 RETAIN_WORLD_CUSTODY saved-proof path wired: bounded entity NBT parser
  now preserves exact UUID->NBT and rejects duplicate identities. Retained cart
  requires actual chest_minecart serialized without any scene tag (malformed
  NeoForgeData fails); no inventory/position rollback. Sealed Removed/Retained
  candidates distinguish absent projection from saved free-world cart. Same
  successful read/write+sync batch gates durable CargoCleanupSaved; recheck no
  loaded declaration, then clear stale departure observation. No archive is
  needed for a path that does not delete the entity. Natural reads also inspect
  pending world-retained carts outside archived removal chunks.
  Join now relinquishes an exact RETAIN declaration before old snapshot-content
  checks; changed player contents preserved. Unit30930 terminal0,6s:19 tests.
  Native17625 terminal0,37s:all5 departure tests, including actual returned cart
  after scene/tombstone compaction, modified contents and actual serialized
  declaration absence; shutdown06:41:53. These still use ephemeral/overworld
  runtime, NOT production-dimension save->ack/crash proof. Remaining: destruction
  or movement before first post-release save, aborted/retry paths, actual full
  provider/recovery composition. No live deployment; previous native evidence
  preserved in scene-game-test-before-world-retained-20260923.
- SA06/07 torn world-carrier declaration now validates exact retained retirement
  even after scene/tombstone history compaction. hasCurrentDeclaration requires
  actual MinecartChest, matching world, RETAIN_WORLD_CUSTODY, current canonical
  world custody and all exact typed declaration fields. Existing accepted-release
  interaction then relinquishes only scene tags/gravity, not contents/position.
  Closed cargo authority also reads retained obligation before compactable history;
  no live-work permission from retired evidence. Unit53517 terminal0,9s:4 tests
  pass, including compaction, missing/forged fields and wrong UUID/epoch. Physical
  integration and world-retained save acknowledgement remain OPEN; next must
  wire actual post-release serialized declaration absence into saved proof,
  not merely add another tag validator. No deployment/native run.
- SA06/07 ordinary cleanup ack now has an eight-record safety maximum per save
  pass with deterministic UUID round-robin. Failed attempts advance the cursor;
  remaining obligations retain the successful batch/candidate evidence rather
  than requiring another empty-chunk write. Already-acknowledged obligations are
  excluded; metadata recovery remains one record per pass. Unit73486 terminal0,
  6s:17 tests pass (persistence9/batch8), including unchanged failing queue
  fairness, removed cursor, duplicate/input-order normalization. No native run
  or deployment. Remaining primary gap: RETAIN_WORLD_CUSTODY has no save ack.
  IMPORTANT: interaction already relinquishes declaration after accepted release
  and repairs a current torn-save declaration under world custody (Lifecycle
  around883–910); do not duplicate this. Late-return branch only preserves cart;
  normal discardClosed handles only REMOVE. Join those existing paths to exact
  retained obligation and serialized entity proof before changing this path.
  Abort/retry and production-dimension crash composition also remain open.
- SA06/07 archive post-ack recovery added: after actual save+sync, persist an
  exact hardlinked `.saved` witness before canonical ack; delete `.nbt`, sync,
  then delete marker and sync. Restart inventories at most4096 markers and
  compacts one acknowledged record per ordinary entity save pass. Pending
  canonical obligation forbids metadata recovery from treating marker as ack;
  no physical/canonical mutation in this compactor. Failed compaction queues
  retry. Failed marker publication retains batch/candidates for retry even when
  vanilla suppresses another empty write. Unit97055 terminal0,6s:20 tests pass
  (archive5/persistence7/batch8); no native rerun/deployment. This verifies file
  restart/unlink cuts and future logic, NOT production runtime crash composition.
  Remaining: bound normal ack loop, RETAIN_WORLD_CUSTODY and abort/retry paths,
  actual production-dimension save/crash proof. SA06/07 still OPEN.
- SA06/07 natural stored-read recovery implemented and verified: actual read
  observation checks chunk coordinates/UUIDs on the server thread, waits before
  save-pass certification, and cannot hide a previous failed write. Recovered
  prior XML:12 unit tests pass; native5 pass, shutdown06:23:43, no task process.
  Its terminal handle was lost; no exit status inferred. New observer-isolation
  correction preserves successful vanilla loading if diagnostic scheduling/copy
  throws, while blocking cleanup ack. Session66788 terminal0,8s:15 unit tests
  pass. No unchanged native rerun or deployment. SA06/07 remain OPEN.
  Next: crash-safe archive compaction, retained-world-custody/abort/retry paths,
  bounded completion and production-dimension end-to-end save/recovery proof.
- SA06/07 per-chunk acknowledgement replaced with coherent entity save-pass
  barrier. Entity manager autoSave/saveAll RETURN invokes provider capability;
  skipped storeChunkSections defers incomplete autoSave. Bounded latest-write
  futures cover all observed chunks (including earlier unloads), so old-chunk
  failure blocks new-chunk cleanup until exact rewrite succeeds. One sync per
  candidate-bearing pass; later write in any chunk invalidates its ticket.
  Empty/no-cleanup passes compact only successful observation futures, no extra
  sync. Overflow retains obligations and logs, never evicts unknown evidence.
  Unit19863 terminal0,10s:7 tests pass (batch4/write-barrier3). Native10208
  terminal0,37s:5 departure tests and both save mixins load; shutdown06:16:32.
  This still is not production-dimension end-to-end moving-cart/save/crash proof.
  Next: natural stored-read recovery. EntityStorage.emptyChunks suppresses an
  empty rewrite after restart if deletion already saved, so write-only ack can
  retain an obligation forever. Add exact read-back + sync, not absence inferred
  from unloaded memory. Also ack-before-archive-delete orphan GC, world-custody
  declaration and abort/retry paths. No deployment or active task process.
- SA06/07 actual entity-write hook added (WIP, not delivery-ready): EntityStorage
  mixin observes both SimpleRegionStorage.write branches and original futures.
  Cleanup index uses retained exact archive/position, parses root/passenger UUIDs
  with a bound, and pairs write success with synchronize(true). Server-thread
  completion rechecks same runtime, chunk generation, pending identity, absence
  and custody before CargoCleanupSaved; only then compare-deletes archive and
  clears SavedData witness. Runtime forget drops transient index.
  Unit51898 terminal0,10s:6 archive/save-barrier tests pass, including write
  failure not hidden by successful sync, sync failure, nested/malformed NBT,
  exact archive removal. Native12362 terminal0,36s:5 departure/death tests pass;
  mixin loads and vanilla writes complete, shutdown06:10:20. These tests use
  overworld/ephemeral runtime, NOT end-to-end production-dimension save->ack.
  Next safety priority: extend per-chunk generation to a coherent save-batch /
  cross-chunk movement boundary; a cart may have an older stored location in
  another chunk. Also natural-read recovery when deletion was saved before
  crash, ack-before-archive-delete orphan cleanup, RETAIN_WORLD_CUSTODY and
  aborted/retry paths. Do not deploy or claim retirement closed from this WIP.
  No active task process or live deployment; SA06/07 remain OPEN.
- SA06/07 physical witness crash gap addressed: new CargoCleanupArchive persists
  immutable world-bound exact NBT inventory/attempt witness before late-return
  and ordinary loaded-cart cleanup. Complete file forced before no-replace hard
  link, directory forced; partial staging retry, contradictory/corrupt published
  witness fail closed. Bounds4096 files/1MiB each; no archive eviction yet.
  Late return can recover this witness if independent SavedData observation is
  missing. Loaded discard now clears contents before discard, retaining pending
  obligation. IO errors retain projection and log exact entity, not quarantine.
  Unit41414 terminal0,12s:7 archive/departure tests pass. Native19551 terminal0,
  38s:5 departure/death tests pass, including removed SavedData receipt with
  archive-only late return after scene/tombstone compaction; shutdown06:02:41.
  Evidence previous run moved to scene-game-test-before-cleanup-archive-20260923.
  Additional parent-directory retry-sync correction verified unit95612 terminal0,
  8s (3 archive tests); no redundant native rerun. No task JVM or deployment.
  Still OPEN: exact entity write+sync hook, ack emission and archive deletion only
  after durable canonical ack, retained-world-custody declaration retirement,
  aborted/prepared/retry paths and crash composition. Archive itself is NOT
  proof the entity deletion was saved and must never directly emit ack.
- SA06/07 canonical durable-ack path implemented: typed CargoCleanupSaved carries
  the entire exact obligation; common recovery owner compare-removes only that
  pending record. Closed process catalog, diagnostic ordinary inventory and WAL
  codec registered; WAL/snapshot share retirement encoding. Requires
  DURABLE_BEFORE_EFFECT before provider-witness compaction. Registered engine
  release->ack test proves unchanged inventory, snapshot+single-WAL replay exact
  checkpoint equality; model rejects forged disposition/repeated stale ack and
  preserves a newer attempt for the same cargo. Gradle74398 terminal0,17s.
  Prior24749 test enum spelling error and34936 missing diagnostic registration
  corrected; no native process or deployment. Physical save callback is NOT
  connected yet: pending obligations intentionally still retained at runtime.
  Next implement exact physical write+sync producer, loaded/abort coverage and
  crash windows. SA06/07 remain OPEN; no full closure claim from these tests.
- SA06/07 reference/epoch closure progressed: CargoProjectionRetirements validates
  exact world and any retained CLOSED logistics endpoint during full world
  construction/pre-WAL/recovery validation. Missing historical scene remains
  legal. Relationship inventory/surface manifest declares this terminal owner.
  FencedRecoveryState rejects live/pending identity overlap and unreserved
  aggregate capacity; epoch allocation/preparation now accounts for pending
  retirements after the latest tombstone is compacted. This prevents epoch reset
  and reactivation of the same old physical UUID under a recycled scene owner.
  Gradle16965 terminal0,25s:24 focused tests pass (release5, retirement7,
  recovery7, codec1, relationships4), diff check clean. Full-world foreign
  retirement insertion, active endpoint, stale epoch and same-owner reuse
  negatives covered; successor with distinct owner/higher epoch remains legal.
  Next actual durable-save/ack integration; no native repeat or deployment here.
  SA06/07 remain OPEN, including loaded/abort paths and crash/save composition.
- SA06/07 late-return consumer now uses exact pending retirement identity even
  after scene/tombstone compaction; exact departure inventory remains required.
  World custody/foreign payloads are preserved. Cleanup keeps its departure
  witness and canonical obligation until durable acknowledgement (not yet wired).
  Caption cleanup accepts exact nominal scene/cargo without historical lease.
  Shared custody predicate moved to ExactInventory (domain + adapter callers).
  Native68208 terminal0,41s:5 scene-departure/death tests pass;5 cargo-release
  model tests pass. Initial13184 failed missing test import before server launch,
  corrected. Native shutdown05:45:52, task JVM absent; previous evidence retained
  in frontier-v3-scene-game-test-before-retirement-consumer-20260923.
  Saturation72355 terminal0,13s:5 retirement tests pass, including4096 pending
  obligations blocking new cargo while unrelated body recovery still completes.
  Next: durable ack + reference registration. Local Minecraft source confirms
  EntityStorage.storeEntities writes asynchronously and only reports its own
  failed future; flush(true) alone is not proof of an earlier failed individual
  write. Need exact write success AND subsequent synchronization before ack.
  Normal loaded discard and abort/retry paths must join this retirement protocol;
  no perpetual retention or full-world forced save per cart as final solution.
  SA06/07 remain OPEN; no live deployment, running task process or subagent.
- SA06/07 terminal producer connected: logistics release confirms the exact
  cargo binding and retains REMOVE_PROJECTION or RETAIN_WORLD_CUSTODY in the
  same immutable world update. Recovery preparation reserves pending-cleanup
  capacity; native logistics admission yields locally when capacity is full.
  Gradle19317 terminal0,26s:17 focused tests pass, including ordinary release
  with cargo retained COLD/full snapshot roundtrip and registered world-custody
  release; NeoForge compile and diff check pass. Still WIP, not deployment-ready:
  full-capacity negative, common custody predicate extraction, reference closure,
  physical consumer/durable save acknowledgement and crash-window tests remain.
  No native server/client launched, no deployment. SA06/07 remain OPEN.
- SA06/07 implementation progress: FencedRecoveryState now retains bounded
  CargoProjectionRetirements through every internal transition and tombstone
  compaction. Admission of an obligation requires the exact terminal tombstone;
  codec178 persists complete nominal identity and authorization, with closed
  disposition tags. No old-world migration or runtime deployment performed.
  Focused Gradle88308 terminal0,18s:12 tests pass (recovery7, retirement4,
  codec1); NeoForge compile succeeds, diff check clean. This is storage/model
  coverage only. Release producer, admission backpressure, reference-closure
  registration, physical consumer and durable save/ack remain OPEN, as do
  SA06/07/08/09. Do not launch old codec177 evidence worlds with this candidate.
- SA06/07 retention design recorded in audit after source/contract tracing:
 canonical pending physical-cleanup obligation must outlive scene/tombstone
 compaction and be retired only by exact durable provider acknowledgement.
 Reserve bounded cleanup capacity before cargo admission; local backpressure,
 no unknown-entity deletion or global quarantine. Codec177 and release/save/ack
 seams identified. Implementation remains OPEN; do not mistake this design for
 a fix or skip crash-window negatives. No runtime/source change or test launched
 by this design step. Existing successful worlds/evidence remain untouched.
- SA05 CLOSED after original-criteria audit: GROWING/READY/active successor,
 pending/resolved receipt, foreign surfaces and cache-loss native cases; registered
 late receipt/duplicate/snapshot regression; actual COLD+restart1320 and HOT
 harvest->bread->young field56548. Re-read manifests/native logs and current source
 hashes match accepted field implementation. No redundant rerun/code/deploy.
 Closed SA01/02/03/04/05/10; OPEN SA06/07/08/09 and whole product goal.
 Next repair unresolved cargo-retirement/late-return retention under SA06/07;
 second-cycle/restart and production variants remain explicitly unproved.
- SA03 CLOSED for shared queue starvation after final composition/source review.
 Native87616 terminal0,24s:all5 production-effect tests pass, including actual
 unloadedA then B chest->bread64/registered job retirement, subsequent A retry
 leaves B output unchanged and never loads A. Combined with all8 scene-family
 scheduler and native-demand coverage, original selector criteria are met.
 Initial94372 was a missing second initial-input fixture error, corrected with
 explicit test stock (not fabricated output). Evidence retained, shutdown05:22:52.
 Closed SA01/02/03/04/10; OPEN SA05/06/07/08/09 and whole goal. No deployment.
- SA03 selector follow-through: all8 typed families tested through real shared
 inventory scheduler, waitingA/eligibleB/conflict exclusion/budget/reset; focused
 suites65814 terminal0,9s. Native demand29824 terminal0,25s:2 tests prove unloaded
 first candidate does not suppress player-demanded local B and residency alone
 grants no effect authority;8 slice tests pass. Shutdown05:19:08, no task JVM.
 Source inspected across all8 callers. Not actual paired-family terminal
 acceptance; SA03 stays open for composition review. No deployment.
- Corrected successor graphical run PASSED: exec56548 terminal0, serverPID1823040
 absent after graceful save05:15:15. Full HOT harvest64 -> confirmed exact wheat
 -> production7-79 terminal -> chest bread64 -> epoch2 young field132-cell
 client check, summary conflicts0. Artifactab7ecc25, result
 build/sa-harvest-successor-ascent-20260923/result.json. Chest and young-field
 frames inspected; first farmer frame occluded, final board dark/distant, no M3
 claim. This is one HOT lifecycle, NOT second harvest/restart acceptance.
 Next relevant evidence: retained-world restart/successor worker continuity;
 SA03/05/06/07/08/09 remain open. No task native process or live deployment.
- Superseded launch details for that completed run: exec56548, serverPID1823040,
 world v3-disposable_sa_harvest_successor-9249234e, runId
 6b751f6e-23fa-463f-b83b-2f641783ecb9, isolated25585/RCON25586. Prelude20500
 queued05:07:52. Prepared27846 terminal0,5s, artifactab7ecc25791b6c13063de58022baad38cc653d9be17d8b8811a8812b93acaef9;
 evidence build/sa-harvest-successor-ascent-20260923/result.json.
 Landing extension59825 terminal0,26s:18 native tests pass,
 actual raised-support arrival -> ordinary gravity settlement retains full
 health and fallDistance0. Previous green evidence preserved in ascent-green dir.
- Full HOT harvest-successor80788 failed at production completion, not harvest:
 64 crops/output receipt confirmed, epoch2 growing. Offline snapshots show job
 production-7-79/worker7-15 HOT, cursor74 held ticks36000–42600 before grade-one
 edge95/63/-1 ->95/64/0. Exact saved body/blocks confirm ascent oscillation, not
 missing farmer/input. Shared motion unconditionally returned after every lift;
 gravity undid it before lateral movement. Corrected actual collision-result
 continuation; strengthened native test to actual production exact-endpoint call.
 Red73832 reproduced1/18; green89753 terminal0,28s:18 native +10 unit tests pass.
 Evidence/world retained; audit records identities. Native shutdown05:05:00.
 Next full corrected successor/restart evidence and ordinary landing behavior
 (old stuck body's saved fallDistance667.55). No deployment; SA03/05–09 OPEN.
- SA04 CLOSED for original terminal diagnostic/disposition exception after source
  and criterion review. Full reference/state validator now covers terminal normal,
  UNKNOWN, CONFLICT and death snapshots (87494 terminal0,7s), complementing actual
  registered-engine conflict/WAL/independent-field tests and adapter witness cases.
  Audit records current source hashes and exact limits; no full native terminal
  family/story claim. SA01/02/04/10 closed; SA03/05/06/07/08/09 and overall goal
  remain OPEN. Next meaningful product evidence is full harvest -> production ->
  successor with relevant restart, not another unchanged SA04 run. No deployment.
- Shared dead-member release fixed: explicit RETIRED_BY_DEATH requires canonical
  death plus exact current DRAINING scene/member, never body absence. Harvest and
  production accept that disposition without manufacturing/discarding a living
  inactive carrier.14 unit tests pass99826 (10s); native scene-departure10556
  passes5 tests (37s), including observed-vs-unrecorded physical death distinction.
  Shutdown04:36:39, task JVMs absent; prior native evidence moved intact to
  frontier-v3-scene-game-test-before-death-release-20260923. No deployment.
  Next terminal harvest family native/composed recovery and remaining SA03/05–09
  scopes; this shared-boundary pass is not full product closure.
- Terminal recovery/death increment: common owner resolves exact terminal site;
  DRAINING/UNKNOWN/CONFLICT retain lineage worker, not deleted job. Registry-owned
  recoveredStatus directs completed harvest to DRAINING; native reclaim reports
  success only on Accepted. Death retains completed output and lets common owner
  retire body. Extended snapshot/conflict/death/release regression passes; final
  scene/harvest/adapter selection58638 terminal0,19s (adapter reused58058). Old
  multiple-death fixture's guessed shipment ID fixed via declared fixture selector.
  Next actual defect: harvest AND production release still fence a live body for
  canonical DEAD workers; shared explicit death disposition needed, not FENCED
  fabrication. Native terminal composition remains due. No deploy/native process.
- Terminal harvest follow-through: reproduced70399 canonical growth quarantining
  a lawful pending body exit after output receipt. Exact predecessor relation now
  survives all GROWING/READY stages of adjacent epoch (no active successor), not
  stage0 only. Test covers all stages, snapshots, foreign predecessor and same-body
  release. Final43984 terminal0,13s; harvest/held suites and NeoForge compile pass.
  Next concrete gap: post-receipt carrierFenceConflict/death still require the
  consumed job; expectedMembers rejects terminal UNKNOWN/CONFLICT. Need coherent
  retained terminal ownership/recovery, not a catch/fake job. Audit records it.
  No native/deploy; goal and SA04/05/06 remain open.
- SA-04 engine composition passes for incomplete/terminal carrier-fence conflict,
  paired scene CONFLICT, WAL recovery and independent field progress. Source
  review additionally found cross-scene command-ID collisions; repaired with
  explicit SceneLeaseId-derived bounded idempotency digest. Adapter regression
  covers distinct scenes/revisions and stable retries. Final61371 terminal0,6s;
  domain suite passed37612 and reused unchanged. Initial compile/identifier-format
  mistakes corrected. No native/deploy; SA04 awaits final call-path closure review.
- SA-02 CLOSED for cross-field scheduling after source/criteria review combining
  deterministic native partial8 -> B complete -> A resume16 with real player
  demand-switch59566 (213s). New disposable-sa-fields-demand-return scenario:
  site7 -> site1 -> site7, both exhaustive client132-cell predicates pass,
  epoch2,zero conflicts. Evidence build/sa02-demand-return-20260923/result.json.
  Frames inspected but unusable for visual acceptance (render mesh not settled);
  no M3/seamless-render claim. No task JVM remains/deploy. SA01/02/10 closed;
  SA03–09/full goal OPEN. Next SA04 actual conflict-dispatch composition and
  remaining full harvest successor lifecycle, not another unchanged field matrix.
- Actual graphical first-visibility after COLD+graceful restart passes1320
  (229s), evidence build/sa05-first-visibility-20260923/result.json, artifact4c4ff20b.
  Zero-player24000 completed; site7 already epoch2/stage1 and old wheat in COLD
  bread job7-48 before visit. Three frames inspected: current young field/1-of-7
  board and same named farmer at terminal edge. Persistent client, seed47,
  isolated25585; no remaining task native JVM. Not second HOT harvest/full-pack/M3.
  Preserved world/evidence; no live deploy. Next semantic complete lifecycle and
  leave-A/visit-B/return-A; old unload-return script's fixed sleep/presence checks
  are insufficient. No repeat of this unchanged first-visibility scenario needed.
- SA-05 canonical late receipt now verified in READY and HARVESTING using actual
  registered planner/reducer after HOT-start/COLD completion, normal successor
  admission and snapshot round trips. Same job/worker/cursor/inventory preserved;
  duplicate rejects. Target method72815 terminal0,7s; uppercase fixture-ID error
 95055 corrected. No native/deploy/process this turn. Next actual disposable
  graphical harvest/unload-return composition; no repeat of unchanged adapter
  cases. This is not a whole-engine WAL or player acceptance claim.
- SA-05 partial receipt interleave defect reproduced50292 and fixed: receipt
  wait recognizes exact persisted mixed prefix instead of stale harvest count.
  Native3/unit pass10960 (26s). Extended physical sequence reaches terminal64,
  writes64 wheat once (repeat acknowledgement), then resumes three-slot successor
  given explicit resolved/retired canonical precondition; native48167 terminal0
  (25s,shutdown03:54:21). Non-atomic fixture failure37156 preserved/corrected.
  No task native JVM/deploy. Still needs registered receipt/retirement composition
  and actual player lifecycle; supplied adapter state is not that evidence.
- SA-05 second native defect fixed: active successor erased an unresolved
  predecessor's terminal field. Repro24997 -> combined native3/unit green23993
  (29s,shutdown03:49:53). Shared matchesCanonicalProjectionTarget now preserves
  pending receipts for exact active cursors too; partial-prefix admission and
  receipt awaitingProjection query aligned. GROWING/READY pending+cache tests
  also pass. Evidence retained; no task native JVM/deploy. Next: active partial
  predecessor with real RUNNING intent, receipt completion -> successor resume,
  full lifecycle/player composition. Adapter fixtures do not prove that whole flow.
- SA-05 active-successor first-write bypass reproduced natively and fixed in
  shared isExactSuccessorRegrowth: all callers now require matchesClaim before
  admitting restoration. Native red69192 -> green63918 (3 tests,26s), lawful
  three-slot successor/cache resume and foreign first-write block protection.
  Three affected unit suites pass75990 (9s). Initial topology fixture corrected
  to contiguous serpentine order; all evidence retained. No task native JVM/deploy.
  SA-05 still OPEN for pending receipt/native lifecycle/player composition.
- SA-05 physical writer test now covers resolved GROWING/READY successors,
  reverse cursor, cache-loss resume and foreign-cell rejection. Field-turns native
  slice passes2 tests (61190 terminal0,24s, shutdown03:41:31). Initial shared-site-ID
  fixture collision fixed; failed/prior evidence preserved. No runtime change or
  deploy. Still OPEN: pending/active successor and full lifecycle/player evidence.
  Next source-identified risk: isExactSuccessorRegrowth bypasses matchesClaim on
  the first batch despite not checking physical predecessor; reproduce foreign
  cell overwrite in active successor and repair common admission. Audit has details.
- SA-02 native two-field writer composition passes: A partial8 writes, real
  selector services demanded B to completion without advancing A, cache loss then
  A resumes at16 and completes. New pilot FieldTurnGameTests/field-turns slice;
  same bounded production writer exposed package-locally. Native67519 terminal0,
  26s, shutdown03:34:08; no task JVM remains. NOT actual player visit/unload or
  filesystem restart; SA-02 still open. Old native evidence preserved at
  build/runs/frontier-v3-scene-game-test-before-field-turns-20260923 inside NeoForge;
  new logs in ordinary scene-game-test dir. No deletion/live deployment. Audit
  records scope/command; next physical mature-successor and player composition.
- SA-10 CLOSED for original schedule-only publication/replay barrier gap after
  source/evidence audit. Reused unchanged42674 kernel/real-field atomic-retirement
  evidence; current production contract validator negative/recovery test passes
  (31485 terminal0,7s). Audit maps every criterion and source hashes. SA-01 also
  closed; SA-02–09/full product goal remain open. Next physical multi-field
  leave-A/visit-B/resume-A and mature successor evidence, not another blanket
  scheduler/reference campaign. No source/deploy/native mutation this turn.
- SA-01 CLOSED for original scheduler defect after requirement-by-requirement
  source/evidence review. Real held-harvest test now also releases via registered
  commands/exact schedule binding, snapshots, recovers and resumes same farmer/job;
  B grows while A held. Kernel/queue cover budget, earlier insertions, exactdeadline,
  monotonic WAL and replay. Final3 suites pass8s (42674 terminal0); audit has source
  hashes/closure boundaries. SA-02–10 and full F0.6R3 remain open. Prior blanket
  statements that all SA remain open are historical. Next original field/production
  acceptance gaps; retain combat UNKNOWN recovery debt, do not expand endlessly
  or re-prove unchanged scheduler. No native/deploy/task process from this turn.
- Shared strike release now atomically cancels only PREPARED exact-bound intents
  with ABANDON fence tombstone; no hit/epoch fabricated. RUNNING/UNKNOWN retain
  custody; planner checks before journal and reducer repeats. Cancellation,
  snapshot/stale start and both release negatives pass with route/assault suite
  (83708 terminal0,19s). Native ambiguity unresolved: SceneExecutor.strike simply
  returns on UNKNOWN and lacks exact persisted-hit inspection. Need physical
  evidence/settlement across damage -> receipt interruption, not replay/health
  inference or permanently blocked release. Audit records scope; no native/deploy.
- Strike family/retention adoption: exact scene binding now rejects foreign
  lifecycle families; integrated mobilization uses correct assault owner and
  registered transitions (mobilization+assault pass44s,21446 terminal0).
  Closed-scene compaction now preserves scenes referenced by physical receipts,
  unchanged1024 limit; pin/unreferenced tests and route/assault pass20s (72443
  terminal0). Next unresolved strike release composition: generic release has
  no explicit PREPARED cancellation / RUNNING-UNKNOWN resolution before CLOSED.
  Need actual disposition and capacity/native evidence, not permanent safety-only
  blocking. No task process/native/deployment; audit updated.
- Strike binding repair implemented: typed leaseID/revision required at producer,
  stable role schemas28/29 retire unbound7/8; payload/snapshot preserve binding.
  Exact lookup + common command/reducer transition permissions allow existing
  DRAINING receipt but no new strike/CLOSED authority. Real second defect fixed:
  retirementFacts was rechecking pre-effect HOT/living-target admission after a
  lethal strike. Final targeted route/assault/binding/owner-codec tests pass20s
  (73012 terminal0); NeoForge compiled earlier in8586. Native combat/recovery,
  release/relationship retention consumers and old mobilization fixture owner
  mismatch still need review. Scout-sighting failure remains open. Audit/relations
  updated; no native/live deployment or full-SA closure.
- SA-03 exact-consumption selector now uses shared runtime-scoped fair turns;
  cleanup removes cursor. Actual-selector tests cover UNKNOWN/waiting head and
  excluded medical entry; with fair-turn suite6 tests pass6s (53905 terminal0).
  Native consumption/recovery not claimed. Initial invalid selector fixtures
  corrected without weakening canonical validation. Combat follow-up found
  missing explicit route strike -> scene/revision binding: do not simply permit
  DRAINING by scanning scenes or decoding string IDs. Add typed retained binding
  across producer/codec/owner/replay/receipt before completing that repair.
  Existing route receipt and scout-sighting failures remain. No task process/live
  deployment from this work. Details in audit.
- Medical command/replay permission parity fixed: actual accounted event rejected
  without HOT after meaningful red -> green (17784,8s). All10 medical tests pass
  after registered-owner/real-scene fixture repair (31424,12s). Assault class also
  passes registered-owner migration. Combined35 tests leaves2 failures (26588,
  terminal1,61s): route lethal receipt in DRAINING rejected by HOT-only owner
  lookup (real composition defect); autonomous supply remains ACTIVE but no scout
  sighting by10000 (attribution open). Next separate strike start from retained
  receipt authority with exact stale/foreign/closed negatives, not test reordering.
  Exact-consumption first-pending selection also still has SA-03 starvation gap;
  use shared fair turns with no UNKNOWN replay. Audit records evidence/details.
  No task process remains from these runs; no native/deployment or whole-SA closure.
- Targeted old full-gate backlog: seven failures reproduced; tiny WAL passes
  with task-local temp. Retention diagnostic, quota-text guard and assault
  conflict fixtures corrected without dropping assertions. Medical owner-level
  test exposed real UNKNOWN receipt rejection: start/receipt permission now
  separate, native selector permits read-only UNKNOWN inspection via declared
  owner. Snapshot/negative/late-confirmation regression and related suites pass
  (12s,90237 terminal0); NeoForge compiles. Native medical verification and
  reducer/replay parity remain due. Old backlog now retains two combat methods
  and the other medical lifecycle method using raw storage APIs. Audit has details;
  no test/server process running from this work and no whole-SA closure.
- SA-06 follow-up after the native pass: common release readiness now also waits
  for an absent cargo carrier's retained/current entity-storage columns. Present
  or recorded cargo reaches the exact validator; contradictions cannot be hidden
  by readiness. Five readiness + six departure-consumption tests pass (12s,
  30034 terminal0). This branch is newer than graphical candidate6012c219;
  do not claim that native run covered it. Next resolve bounded late-cart
  retention/compaction and remaining missing/foreign/dead/recovery composition.
- SA-06 corrected camera run reached HOT then exposed a real natural-unload gap:
  EntityLeaveLevelEvent is tracking-end before removal; actual hidden-entity unload
  produces no second event. Final setRemoved(UNLOADED_TO_CHUNK) mixin now feeds
  shared actor/cargo observers. 22 focused tests pass (10s,17228). Candidate
  6012c219ea5d731deb62f68881d1b79b4f7d1f76cd8f0a4f54fc589d2c30cd20 passed actual
  natural actor/cart unload -> CLOSED -> COLD routeIndex1 -> HOT return -> same
  world graceful restart/reclaim, session40007 terminal0,120s. Output
  build/sa06-route-return-final-unload-20260923/result.json; linked frame inspected.
  Recovery server PID1487984 absent; no task-native process remains from this run.
  Prior camera run19397 terminal failed/saved/stopped; evidence retained. Audit
  records exact Minecraft callback ordering and harness corrections. SA stays open.
- Latest SA-06 graphical route-return attempt failed setup arrival, not cargo
  release: old observer overlapped a hall foundation. Failure bundle retained
  under implementation build/sa06-route-return-native-20260923. Task processes
  are now absent. Initial/restart/away points corrected with real plan geometry
  and scenario-binding regressions: two Node tests and Java fixture tests pass
  (15s, 38014 terminal). Review remaining dynamic camera geometry before rerun;
  no timeout relaxation, live deployment or SA closure. See audit receipt.
- SA-06 late-cart boundary now has native coverage: production unload observer
  retains receipt; registered domain commands establish CLOSED precondition;
  actual NBT return rejects changed contents and missing tombstone, removes only
  exact obsolete projection without inventory drops, and preserves accepted
  world custody. Exact world-custody return now also consumes its resolved
  observation instead of leaking it. Six unit/five native cases pass (37s,
  43558 terminal, shutdown02:17:24). This is isolated physical return/deletion,
  NOT generic actor-release observation or filesystem crash. Those remain next.
- SA-06 cargo observer now wired into source unload/join and shared release.
  Full current inventory + exact attempt are revalidated; retained departure
  prevents replacement or HOT work. Matching current return resolves; divergent
  return remains fenced. CLOSED late return requires exact tombstone + snapshot
  before clearing/discarding the obsolete cart, and cannot delete world custody.
  Six unit + three native cases pass (35s, 52804 terminal, shutdown02:13:59).
  Native case covers NBT return, altered contents and normal resumption, NOT full
  release/restart or retired-cart deletion. World-custody guard was added during
  native run and needs its own changed-path test; final compile passes (4s,
  45989 terminal). No task-owned native process remains.
  Next: exact-item variant, actual generic release, late retired return/foreign
  payload/world-custody negatives, torn-save recovery and bounded cleanup policy.
- SA-06 cargo evidence retention added: per-world SavedData ledger, maximum4096
  identities plus one alternate observation each; exact repeats idempotent,
  contradictions survive reload and forbid exact resolution, malformed/duplicate/
  orphan rows reject. Six departure/ledger tests pass (8s, 9162 terminal).
  Still not connected to runtime callbacks/release: next compose producer,
  current-state inventory comparison, return resolution, accepted release and
  late-loaded stale-cart suppression. Ledger alone is not a safe handoff.
- SA-06 cargo departure foundation: immutable exact scene/cargo/UUID/revision/
  recovery-attempt/body/27-slot observation with strict NBT decoding. Capture
  permits only actual UNLOADED_TO_CHUNK with current declared authority and exact
  contents; ordinary ownership still rejects removed entities. Two codec tests
  and three native departure/death cases pass (35s, session 73775 terminal,
  shutdown 02:07:23 Sep23). NOT yet emitted/stored by runtime or consumed by
  release: next add bounded per-world ledger, exact return/conflict handling,
  canonical-baseline revalidation, release and stale-return fencing. Do not
  accept a serialized observation alone as safe custody transfer/restart proof.
- SA-06/07 source follow-through fixed scene-return observer constructing a
  positive-health survivor from a dead returned body. Zero-health join now leaves
  evidence for the shared death owner; native callback test covers it plus
  divergent/exact returns. Two native departure/death cases pass, shutdown
  02:03:03 Sep23; session 44304 terminal (35s), selected domain tests also pass.
  Native first attempt failed old hot-strike fixture before execution (74828,
  74s): now derives actual shipment/time from route-return, not ordinal 2/tick2600.
  Cargo release model consumer likewise uses actual ID/time. Bomber JSON and
  other independent supply/assembly fixture consumers still need source review.
  Cargo fully-unloaded release remains a concrete open path: shared readiness
  checks actors, then cart `intact` conflates absent/unloaded with loss. Need exact
  cargo departure/fencing/recovery, not just a wait or permission to recreate.
  Body representation epochs and canonical recovery-attempt epochs are distinct;
  verify their correlation rather than equating numeric values. No deployment.
- SA-10 added atomic owner-retirement kernel regression: omission of schedule
  cancellation rejects before WAL, preserves initial state/queue, valid atomic
  transition replays, and invalid recovered queue rejects. Actual field-conflict
  engine test separately verifies one transaction cancels held continuation and
  preserves exact conflict witness through WAL and checkpoint recovery. Do not
  conflate it with deleting the site/job: conflict intentionally retains them.
  Final 36-test kernel/queue/harvest selection passes (8s, session 38687 terminal).
  Two test-construction failures corrected from source, recorded in audit;
  no runtime change or native/release acceptance in this increment.
- Route fixture follow-through: scout freeze now retains the actual admitted
  operation ID instead of hardcoded ordinal 2; ambient scout patrol needs no
  unrelated cargo freeze. Seed-41 identity test covers route + both scout
  configurations, normal route progression, exact scout cancellation and
  independent scheduled work (13s, session 2820 terminal). Nine dependent native
  scenario declarations now use the verified operation/cargo ordinal 11; parser
  and identity checks pass 50 Node tests (0.22s). This supersedes the declaration
  mismatch below, not native execution acceptance. Other independent supply/
  strike/assembly fixtures still need attribution; do not rewrite their IDs.
- SA-01/10 follow-through: real HOT harvest now has a registered-engine budget-1
  test showing another field prepares/grows, including snapshot recovery, while
  the exact held harvest/job/deadline remain unchanged. Kernel/queue/supply/plan
  selection passes 61 tests (26s, session 59857 terminal). Route fixture had two
  unrelated precondition drifts: birth consumed export surplus after corrected
  production timing, and shipment ordinal changed from 2 to 11. Fixture excludes
  Northwatch birth only and admits its unique actual initial shipment; model
  consumers no longer guess its ID. Native route/scout scenario hardcoded IDs
  still require alignment before their reuse; these are not accepted natively.
  Previous failed selection (63866) also exposed my patch hitting another similar
  lookup; corrected exact location and restored unrelated strike fixture.
- SA-02/05 field cursor follow-through: fixed initial recovery expecting AIR
  where supported neutral soil remained, cached batches skipping drift checks,
  and false drift from farmland hydration. Persist explicit INITIAL_SOIL versus
  existing INITIAL/AIR; no inferred baseline. Three real-block/SavedData cases
  pass (29s, session 84231 terminal). Final equivalent baseline deduplication
  recompiled and 23 focused tests pass (9s, session 73990 terminal); no full field
  lifecycle acceptance yet. No task-owned test process remains running.
- SA-08/09 native exact-item mid-work restart now passes: result-correlated.json
  under build/sa09-midwork-native-20260923, session 28658 terminal, 161s. Fresh
  PROCESSING/active-order reads precede graceful save; same-world recovery
  finishes order, 64 bread and current chest. Frame inspected. Initial visit
  interference and new scenario's missing action-bound reads corrected; failures
  retained. Fungible native ingress/repeated cycles remain open. No deployment.
- SA-02 restart queue now also uses shared fair turns: first DEFERRED field cannot
  own every recovery attempt. Retains pending records, loaded/demand eligibility
  and one-attempt budget; forget/empty clear cursor. Focused field/cursor tests
  pass (10s, session 67798 terminal). Actual multi-field restart evidence remains.
- SA-08/09 composed model test now covers real scheduled COLD start/route/labor,
  two HOT visits through registered custody/scene commands, checkpoint recovery
  between visits, and COLD completion for exact and resource inputs. Labor,
  deadlines, route/body and final output/payment match always-COLD controls.
  Final selection passes (19s), session 16172 terminal. Native counterpart still
  due: existing production-work-restart scenario is exact-only and post-completion,
  not mid-work recovery or cold-start ingress. See audit for evidence limits.
- SA-09 exact-item release now also revokes PREPARED actuator/fence and restores
  one continuation through generic custody release and mutation closure. Started
  exact effects retain custody; generic release cannot bypass bound resources.
  Both exact release paths pass snapshot -> COLD output/payment and stale-actuator
  rejection. Focused release/replica tests + NeoForge compile pass (15s), session
  96590 terminal. Actual unload/crash and full visible cycles remain outstanding.
- SA-09 new bound-job admission now works through the normal scheduler, reducer
  and common creation API; incomplete physical layout retains pending start,
  forged Cold/stale epoch reject. Shared reserveBound corrected to skip other
  kinds/economic owners. Production-wide tests + NeoForge compile pass (36s);
  final common-API/admission/ProductionProcess selection passes (32s), session
  53070 terminal. Full observed repeating cycles and recovery remain open.
- SA-09 PREPARED resource release now retires the unstarted intent/fence and
  restores one completion action through both registered release entry points.
  Engine snapshot/recovery -> stale-actuator rejection -> one COLD completion
  passes; UNKNOWN release rejects. Focused 19 tests + NeoForge compile pass
  (11s), session 95471 terminal. Registry emission omission was caught/fixed.
  Actual chunk/process interruption, exact-item path and full cycles remain open.
- SA-09 resource adapter now emits/executes the declared terminal intent through
  the existing production owner. Split-stock/released-input model cases pass;
  native production-effect passes 4 cases (24s, shutdown 00:42:11 Sep23,
  session 89782 terminal), including real chest/accounting/payment/replica closure
  and unchanged foreign contents in UNKNOWN. Initial incomplete-chest fixture
  failure preserved and corrected, not a weakened guard. Details in audit.
  PREPARED release, interrupted writes and full worker/successor cycles remain.
- SA-09 registered lot lifecycle: PRODUCTION_WORK declares nominal resource
  schema 27/roles 42–46; confirmation settles stock/claim/job/finance/receipt.
  Actual engine UNKNOWN + snapshot recovery + one late confirmation passes;
  3 lifecycle and 2 codec tests pass. Combined selection still has the retained
  textual compaction architecture failure. Broader production/NeoForge compile
  passed (34s), session 47572 terminal. Scheduler emission/native lot writer and PREPARED
  release/reconciliation remain incomplete; no product candidate/deployment.
- SA-09 lot receipt foundation: FungibleProductionObservation retains explicit
  account/container/input-output lots/claim/epoch/actual layout; new stable tag
  23 in WAL and snapshot with shared bounded codec. Four receipt/accounting tests
  pass (11s), session 83262 terminal. No production receipt producer/handler yet;
  complete nominal schema, lifecycle retirement and native write remain due.
- SA-09 shared scene admission now accepts bound resources only with matching
  operational reference epoch; HOT command/reducer progress checks the same
  input predicate. Lifecycle test covers admission/traversal and rejects progress
  after release. NeoForge source/tests compile. Full production selection: 98
  pass (30s), after correcting two route-conflict engine fixtures missing their
  real completion schedule. Typed lot effect/receipt and native cycles remain
  unfinished; bound completion still retains its scheduled review.
- SA-09 resource hold adoption: registered layout/release and reference mutation
  closure now atomically update matching jobs through ProductionResourceCustody.
  Bound cancellation releases claims, not stock; scheduler retains bound work
  without exact-item fallthrough. Final 13-test selection passes (13s), session
  56001 terminal. Lifecycle filter corrected from filename to actual class:
  earlier 15s selection did not execute that method; actual test now passes.
  Full HOT lot intent/receipt/effect and scene admission remain next, not closed.
- SA-09: direct COLD completion now checks resource custody as well as worker
  custody; terminal-work/pending-projection regression passes (15s selection).
  Added pure same-epoch observed fungible transformation accounting, 14 resource
  tests pass (14s). Not wired into production yet. Current exact-item intent
  schema cannot be reused for lot identities; next compose explicit resource
  receipt/schema, job binding/release, worker/physical effect and death/cancel
  paths. Sessions 98405 and 96814 terminal, no deployment.
- SA-09 consumer audit: pending projection no longer becomes PROVIDER_LOST
  through the ordinary effect checkpoint; two native cases pass (24s), shutdown
  23:56:27. Fungible layout reducer now requires exact ACQUIRED reference epoch;
  command/replay negative coverage and projection tests pass (11s). Sessions
  84991 and 69933 terminal. Static tracing confirms FungibleBound still has no
  production creator; next repair must compose binding/release, worker admission
  and actual fungible transformation rather than enabling only one fragment.
- Latest SA-09 adapter adoption supersedes the historical "adapter not activated"
  entries below: initial and released projection now use atomic preparation;
  actual observation confirms or retains a local conflict. First native mismatch/
  runtime recovery test passed, shutdown 23:47:16. Four model tests passed.
  New released epoch-2 test first exposed an illegal fixture surface shortcut;
  corrected run passed both native cases (24s), shutdown 23:54:14; session 57303
  exited successfully. Full materializer/fungible/player lifecycle
  remains open. Details and retained failure locations are in the static audit.
- SA-01 and SA-10 implemented with focused evidence, not fully closed.
  SA-02/04/05 likewise not product-closed. SA-03/06/07/08/09 remain open.
- SA-03 shared round-robin service now covers all eight scene families and
  production transformations, including active/admission arbitration. Two-assault
  composition caught and fixed duplicate admission previously hidden by the
  global first-active guard. Final focused 17 tests pass (13s); real effect,
  unload/restart/native composition and cleanup evidence still pending.
- SA-03 actual transformation write protocol now has 5 adapter-I/O regressions;
  with cursor tests 9 pass (8s), including UNKNOWN non-replay versus independent
  PREPARED work. Shared demand filter/GameTest aligned and compiled (9s), not
  yet natively run. No Minecraft persistence claim from in-memory I/O.
- SA-06/07 WIP: shared readiness plus durable exact departure producer and
  generic DRAINING consumer replace stale unloaded samples. Harvest/production
  fencing uses the same receipts; rejected release retains evidence and exact
  return withdraws its provisional fence. 15 focused tests pass (10s).
  Assault DRAINING now delegates to shared release; obsolete periodic-sample
  caches/fallbacks removed from scene executors. 8 focused tests pass (12s),
  later dead-cache removal compiles (8s). Cargo, UNKNOWN/effect recovery,
  torn saves,
  cleanup and native acceptance remain open. Ambient observeLeave remains a
  no-op; scene departure now has its own producer. Ledger format 5 rejects older formats;
  no live migration/deployment authorized. This is not a product candidate.
- Returned-body guard now blocks physical work on unresolved departure evidence
  while preserving declaration-only ingress/death recognition. Exact resumption
  validates canonical baseline as well as body snapshot. 18 focused tests pass;
  final 14-test unit selection + one native body/NBT/departure/return test pass
  (41s; native 13.41s, shutdown 22:05:30). First native fixture failed initial
  ownership; persistence setup corrected, failure retained. This is not actual
  chunk/process restart or terminal canonical release evidence.
- Cargo WIP: ownership now checks fungible quantity and explicit scene revision;
  active selection rejects non-logistics families before decoding. Selector
  regression passes (4-test report, 16:41:13Z). Partial managed declarations now
  reject interaction; accepted release retires scene tags. Ordinary-cart impact
  no longer attempts managed custody capture/quarantines the runtime. Focused
  cargo run passes 9 native tests + 5 selector tests (2m, shutdown 21:53:03 host
  time); actual lifecycle interaction is covered. Prior run directory preserved.
  Cargo departure/recovery, inactive-runtime policy and rejected managed-impact
  local disposition remain open; no full restart/player acceptance claim.
- Cargo now carries its exact canonical recovery epoch, including same-revision
  attempts. A native reattempt exposed and fixed the payload's contradictory
  PREPARED prohibition; planner now checks legal source status before reduction.
  Authority/selector 8 tests pass, transition/codec/ArchUnit 6 pass; focused native
  reattempt passes (36s total, shutdown 22:16:45). Prior failed run retained.
  Medical recovery still needs its own composition check. Actor attempt-epoch
  alignment remains open. Strict world-custody retirement now validates epoch;
  native cargo-interaction passes (36s, shutdown 22:20:51), including inactive
  runtime rejection. This is old-NBT fault injection, not process-crash evidence.
- Repeated departure contradiction now retains bounded alternate evidence and
  blocks consumption/fencing/resumption/adoption after recovery; old health can
  no longer silently win. Strict inventory decoding added. 17 focused tests pass
  (5s, 17:27:49Z); native composition and lawful conflict resolution remain open.
- Shared HOT/DRAINING death observer now distinguishes nominal ownership from
  work permission; canonical acceptance retires contradictory departure receipts.
  17 unit tests and 2 native departure/death cases pass (48s total, shutdown
  22:32:48). Initial fixture revision-zero failure preserved and corrected.
  Follow-up common death admission now covers every non-closed state; PREPARED
  death atomically leaves preparation, exact recovery authority and provisional
  carrier retire only after canonical death. 20 domain/ArchUnit tests pass (14s),
  2 native tests pass (55s, shutdown 22:38:31). New command/codec test exposed
  and fixed PREPARED-death lifecycle disagreement. Living conflict resolution,
  other-family consequence composition and actual unload/restart remain open.
- SA-08 WIP: HOT and COLD stationary production now use the same retained engine
  deadline, 20 canonical ticks per labor unit; no invocation-count labor or
  front-loaded eight-unit COLD batch. Registered production holds preserve the
  action during open scenes. Command/reducer comparison covers all 80 units,
  rejects early work and decodes snapshots: 3 clock + 4 ArchUnit tests pass.
  Test fixture catalog now preserves the production `held` policy instead of
  losing it in a lambda. Filesystem progress/deadline restart passes (final 6s).
  Use JAVA_TOOL_OPTIONS java.io.tmpdir
  under implementation build/sa08-tmp.IbCODN to avoid proven /tmp quota failure.
  SA-09 follow-up removes fungible labor bypass: same route, input station,
  80 work units and retained worker/claim; direct/event completion now requires
  terminal work and exclusive custody. Production selection passes 87 tests
  (28s), including the two old failures corrected to use the registered owner
  rather than raw intent storage. Native work slice passes 3 cases (26s, shutdown
  23:03:08); it found/fixed atomic cancellation missing from job finalization.
  Real COLD-start -> HOT resource handoff remains open: exact Cold input still
  blocks depot activation, candidate admission is Materialized-only, and
  FungibleBound has no complete physical ingress/effect path. Do not simply
  broaden admission without composing custody. Full visual cycle and actual
  repeated mode switches remain due.
- SA-09 source audit additionally found container activation's COLD-input guard
  missing from the reducer. Added matching replay enforcement; encoded-event
  regression failed before and passes after, alongside normal container/resource
  ingress and architecture checks (7 tests, 10s, 18:09:47Z). No new HOT admission
  or custody transfer is claimed. Test sessions terminal; no deployment.
- SA-09: fixed FungibleBound being labelled EXACT_ITEM in live job relations,
  terminal receipts and physical-retirement facts. All use one exhaustive
  resource-schema projection. 11 selected tests pass (16s, 18:13:25Z), including
  cold/bound epoch variants and terminal fungible provision. Actual handoff is
  still missing: PREPARED/ACTIVE surface is not a replica custody lease; transfer
  must fence COLD completion across the physical-write/acquisition interval and
  released-replica catch-up, not merely put the held stack back into inventory.
- SA-09 resumed worker handoff now accepts already-progressed jobs only at their
  exact retained station, preserving topology/labor/deadline instead of always
  invoking the unstarted-only rebase. Engine command/codec regression reproduced
  the rejection before repair. Final 7 tests pass (9s), including approach,
  processing(17), stale body/canonical position and existing unstarted cases.
  Physical input transfer and native whole-cycle proof remain open.
- SA-09 pending-write custody foundation added: PREPARING (wire tag 5) fences an
  exact EXPECTED projection without granting operational HOT permission; exact
  confirmation atomically promotes the same epoch. Model snapshot/negative tests
  and existing custody tests pass (16 cases), NeoForge compiles (17s). NOT wired
  into runtime yet. Next: registered durable transition + atomic input transfer,
  initial/repeated adapter projection, evidence-preserving mismatch/recovery,
  operational-vs-live consumer audit, then fungible effects/native acceptance.
- Projection prepare/confirm now have registered durable commands and codecs in
  the existing owner. Engine checkpoint recovery preserves preparation, rejects
  stale confirmation without mutation and accepts the same epoch exactly once.
  Targeted selection passes (13s). Adapter does not emit them yet; input transfer
  and the remaining adoption scope above are still open.
- Registered ReferenceProjectionPrepared now atomically restores exact held input,
  preserves job/labor/schedule, and establishes expected replica + PREPARING fence.
  Initial and released catch-up transitions have engine/codec coverage, including
  premature completion and foreign predecessor rejection (13s pass). Production
  admission/intent preparation now distinguish live from operational custody.
  Adapter not activated; mismatch/absence recovery and fungible composition remain.
- Pending projection mismatch now records actual fingerprint/provenance and typed
  reason with the same retained epoch in an atomic local UNRESOLVED transition.
  Registered command, codec and diagnostic extraction covered; engine recovery,
  forbidden release and unrelated-command progress pass (16s selection). This is
  evidence retention, not final conflict resolution; adapter remains unactivated.
- Pending-write conflict can now resolve through a later exact observation at its
  current replica revision and same epoch, without world/stock mutation. Stale,
  foreign-epoch, still-mismatching and duplicate observations reject. Selected
  18 tests pass (16s). This is not automatic restitution/foreign-content adoption;
  adapter custody/accounting validation, interruption and fungible paths remain.
- Broader ambient-admission failure classified: reference closure omitted live
  hive nutrient transfers and quarantined their scheduled continuation. Exact
  owner inventory corrected; 24-test selection passes (34s). Missing-transfer
  negative assertion added; no prefix inference or weakened closure.
- Governance guardrails pass all 34 tasks using task-local TMPDIR after `/tmp`
  quota failures. `/tmp` has `usrquota`; root still has 574GiB free. No cleanup
  was performed. Use task-local temp storage for affected temporary-file tests.
- Full frontier test: 820 tests, 10 failures in 6m19s. Audit lists all ten:
  combat/medical/production lifecycle, diagnostic tuple fixtures, textual
  architecture check and a tiny-WAL temporary-file quota failure. Attribution
  and relevant repair remain due; do not silently waive or call all preexisting.
- Implementation guardrails also fail on architecture debt/oversized Java.
  Do not raise ceilings to pass. Governance green is not implementation green.
- No task-owned test/native process left running at the latest completed check;
  all recorded Gradle sessions are terminal. Live server identity is UNCONFIRMED
  now; historical candidate IDs are not a fresh deployment check.

### Next

- Finish common release SA-06/07 with actual final departure evidence and
  recovery; safety-only pending is not final acceptance. Then remaining SA-03 evidence,
  then remaining field/production
  corrections and composed/native verification; retain all open finding scopes.
- Before declaring completion, audit every SA against source and required
  negative/recovery/product evidence. Do not replace this with another blanket run.
- Existing later sequence remains: F0.6R3 acceptance, OBS-002 online semantic
  verification, VIS-002 before the next player-visible candidate, ARC-001E
  reference-transition coverage before MAT-006. No new MAT breadth now.

## Open questions

- Exact remaining physical release evidence, bread custody and successor-cycle
  continuity need verification on the corrected implementation.
- Full-gate failures need source attribution; quota cause remains UNCONFIRMED.
  Last host check: root 574GiB free, /tmp 6GiB free, not a full 2TiB disk.
- Human visual/readability, co-op and clean-room gates remain open.

## Working set

- [Active order](docs/work-orders/PM-F06R3-FIRST-VISIBILITY-COLD-CONTINUITY-CORRECTION-02.md),
  revision 23; [audit and results](docs/frontier-v3-static-audit-2026-09-22.md).
- AGENTS.md, architecture.yml, [workflow](docs/engineering-agent-protocol.md),
  Frontier-v3 contract, execution semantics, domain relations and implementation plan.
- [Preserved pre-cleanup ledger](docs/archive/CONTINUITY_2026-09-22_pre_workflow_simplification.md)
  contains exact historical receipts, contradictions, cumulative blocker history,
  old candidate IDs and links. Archive movement does not reset cost or acceptance.
