# R174 — deposits and distributed extraction

Accepted 2026-10-16. Implemented and deployed 2026-10-16; verification scope and
known unrelated debt are recorded below. Human M3 acceptance is not claimed.

## Outcome and boundaries

Settlements manage several finite deposits/worksites concurrently according to
shared commodity demand. Coal and incidental stone are the live content; metal
definitions must not require another scheduling/navigation/storage algorithm.
No metal economy, wages, construction, tunnel supports or arbitrary unknown-world
geology are activated. Fresh test world/schema change is permitted. Main alone.

Geological identity, settlement knowledge and an operating worksite are distinct.
Destroying a chest or closing a job cannot destroy or regenerate a deposit.
A deposit declares real three-dimensional material distribution, not an abstract
stock bag. Depletion/physical edits have one authoritative history; geological
estimates cannot recreate blocks or grant mining before access is known.

## Source findings

- ExtractionSiteState already supports several site IDs and retained jobs.
- ExtractionDeposit currently means worksite state: cell generations, geometry
  changes and developed front, not independent geology.
- ExtractionSite requires its initial container; validation checks that historic
  container even though worker selection already uses general receiving stores.
- ExtractionWorkProcess reviews each site and admits workers locally. A multi-site
  dispatcher must compare opportunities before assigning the same finite workforce.
- ExtractionRules has one optional pocket, and graybox bootstrap creates one site
  per producer. Material definitions and site placement need plural explicit data.
- Existing priority accounting already considers assigned targets across a home;
  retain and extend it rather than introducing a second resource reservation ledger.

## Implementation sequence

1. Independent, persisted deposit declarations and explicit knowledge/access
   relationships, bounded spatial lookup and reusable material definitions.
   Retain exact existing work-cell/physical-effect authority. Update codec/schema;
   reject unsupported old worlds instead of inferred migration.
2. Integrate geology into bootstrap, physical projection, known geometry and
   expansion. Supply multiple coal deposits with differing location/extent/access.
   Avoid scanning all world voxels or loading chunks for planning. No second terrain
   mutation owner. Keep source generation and observed edits in the common pipeline.
3. Settlement-wide extraction opportunity selection, sharing demand and assigned
   output. Rank need and executable cost; capacity and current assignments constrain
   allocation. WAITING is not exhaustion. One blocked candidate must not hide other
   executable sites. Keep jobs stable until safe reassessment, no per-block commuting.
4. Remove initial-container authority from operating geology/worksite lifetime.
   Use general storage selection for haul/return/receiving, preserving exact in-flight
   resource claims and player-change handling. Lifecycle closure must not orphan tools.
5. Diagnostics explain known/available/depleted/blocked/waiting fronts and current
   site assignments. Focused invariants: multi-site parallelism, shared demand,
   resource-agnostic selection, depletion/switching, storage removal, external block
   changes, snapshot/recovery and HOT/COLD boundary. Use existing client machinery
   for actual multi-worksite observations; no new harness/proof campaign.
6. Update architecture and contracts, run affected release gates, commit clean source,
   deploy a fresh world through pack scripts, verify ordinary production behavior.

## Responsibility boundaries

- Geology: finite source distribution and explicit identity, no jobs or containers.
- Knowledge: declared known bounds/materials, no fabricated discovery/reachability.
- Worksite: admitted access and work-front development, no independent economy.
- Extraction planner: translate settlement demand into selected work opportunities;
  no actor movement actuator, hunger logic or private inventory accounting.
- Existing labour/UAE/navigation/extraction/storage/hauling owners: execute exact
  accepted tasks and resources. Economy remains sole owner of aggregate demand.

Initial graybox knowledge is explicitly supplied; full exploration gameplay is not
part of this slice. General multiple-resource behavior gets boundary coverage,
but copper production is not claimed player-ready before its actual content exists.

## Implementation evidence — first integration, not release

Source WIP on bf616928: settlement-scoped review and ranked multi-site discovery
now replace independent site recruitment. Shared assigned-output advice is checked
at discovery and exact start; waiting on one candidate does not conceal another.
Jobs retain the existing UAE/source/cargo relationships, with no second queue.

ExtractionSite no longer owns a container identity. Starter equipment is declared
by the fresh-world Installation. Container service declarations and physical
attachment follow actual inventory records; removing the original container no
longer invalidates the worksite. The remaining ExtractiveSite endpoint denotes an
explicit authored physical attachment, not the required/only storage of a site.
Dynamic ordinary warehouses and per-job replacement remain the shared mechanism.

Schema291 changes site encoding and settlement review addressing. Focused Java
checks cover selection between two sites, one pending/one executable, exact replay,
shared demand, storage replacement and persistence without the initial container.
32 targeted tests passed; main/test compilation of Frontier and NeoForge passed.
No native multi-deposit acceptance, release or deployment is claimed.

## Second integration — geological identity and safe site relocation

MineralDeposit owns finite 3D material sections independently of jobs, storage and
settlement identity. GeologicalKnowledge explicitly references surveyed deposit
revisions. Worksites name their associated known deposits; immutable MineralGeology
has a derived bounded chunk index. Current source/geometry observations and unknown
exclusions override initial geological distribution, with no second depletion ledger.
Schema291 persists these declarations/relationships; old worlds are rejected.

Profile r15 declares two worksites per producer (12 total), with two differently
sized coal deposits at each of the three coal-producing homes (six minerals).
The additional coal face can be opened from existing access without creating
artificial demand for incidental stone. No metal commodity or tunnel construction
is activated; material definitions may use independent tools.

ExtractionSiteSelection supplies the same demand/distance ordering to new admission
and relocation. Relocation is a distinct accepted successor, not false completion:
the same job, UAE and tool change site/source atomically at an empty safe boundary.
Physical effects and movement must be settled first. A held part is delivered by
ordinary storage work before reassessment. Source and storage receipts are retained
and checked on replay without invoking a planner. A route wait neither exhausts a
deposit nor blocks other executable work. Shared assigned demand is checked for
continuations as well as new workers.

Focused Java checks passed for geological separation/indexing, second-front access,
two-site parallel ownership, blocked-site relocation, snapshot/event replay and
wrong/duplicate receipt rejection. Broader integration: 82/87 passed; five external
intervention tests used r1 without complete approach geometry. Updating them to r13
preserved their intervention assertions; all five now pass. The real canonical
mining/courier/home-delivery lifecycle passed. Latest focused runs:27 checks plus
guardrails/compile passed, then17 checks passed including held mined cargo rejecting
relocation and delivery retaining that cargo. Native observations are in progress;
release/deployment gates remain outstanding. No live R173 changes yet.

## Integration verification — 2026-10-16

Final affected checks: `:pale-mirror-frontier:test --tests
'*SettlementExtractionAdmissionTest' --tests '*CoalDepositReserveTest'` passed
17 cases (including seven architecture checks). Depletion coverage starts from
an isolated depleted predecessor with dynamic reserves: unused stone must not
create demand, while another known coal site retains demand. The real process
selects RELOCATE_SITE, keeps worker/tool/execution and preserves depletion and
inventory through snapshot recovery. This is not a claim of native exhaustion
of an entire mineral volume. Blocked/pending alternatives, parallel ownership,
shared demand and removed-starter storage retain their earlier focused evidence.

Full NeoForge test:742 cases,741 passed/one skipped. Full native GameTest gate:
375 required tests passed; build and packaged-JAR verification passed. Guardrails
passed. No production changes followed these gates; subsequent edits repair and
extend only the focused test fixtures.

`check --continue --no-daemon` completed in14m24s. Frontier:1624 cases,14 failures.
The obsolete r14/single-coal-site reserve expectation was corrected for r15:
six physical coal faces,5160 ore blocks, no free initial coal in storage; focused
rerun passes. The other13 failures reproduce unchanged at clean baseline
ff41730c (the active pre-R174 runtime code), with matching methods/messages:
ExpeditionSupplyFlowTest, MovementOrderTest, three PopulationMigrationProcessTest
methods, ProductionBodyContinuationTest, ProductionSpecializationTest,
ResourceSiteColdHarvestReceiptTest, ResourceSiteHarvestProcessTest,
RouteConstructionTaskProcessTest, two RouteMaintenanceProcessTest methods and
FrontierProcessSceneSdkTest. They remain explicitly open baseline debt, not R174
fixes or a green full check. Retained XML: implementation
`build/r174-fullcheck-frontier-results`; baseline release tree
`build/r174-baseline-classification-results` and its final focused test XML.
No second full aggregate is needed to re-prove unchanged results.

Native checked-in scenario `disposable-distributed-quarry-recovery.json` passed:
ordinary player visit, exact worker HOT mining/cargo increase, separate second
site projection, natural leave/COLD custody,2000-tick advance, graceful restart
and return to the preserved mined front; final conflicts0. Runs756ad89c and
db089bd7, receipts `build/r174-distributed-native-final{,.before-restart}.json`.
First HOT frame visibly shows the miner at the coal face; second frame shows the
separate worksite; post-restart frame shows the retained excavated front and
resident. All frames reviewed. Private Xvfb/20FPS is automation evidence, not
human M3. This native scenario does not claim simultaneous physical mining at
both sites; parallel distinct jobs/claims/tools are covered by Java integration.
All task-owned native client/server/display processes exited.

## Delivery

Source commit `4aeb7fce163eddd0c934b69b8e425fac4068b322`; source worktree clean.
Detached release `/home/rd/proj/pm-release-r174.HiMfsM/source/pale-mirror` built
with the explicit companion disabled-mod catalog. `:pale-mirror-neoforge:build
:pale-mirror-neoforge:verifyPackagedJar` passed in2m45s (full NeoForge tests rerun
by the clean release build). Packaged class bytes match the native-tested WIP
artifact; release metadata accounts for the changed JAR checksum.

Installed SHA512:
`153bb3db8f97505e80f1621eb6f2911506297106ce99bed999838dc404d8eee4d8c314cb58be8c3f3dec8edf7357a2fc0ccfb98f8ab1c3bf54c56f2d59c13de7`.
Normal RCON stop saved all old-world dimensions. Pack `install-server.sh`
installed the pinned artifact and datapack into the new selected world
`/home/rd/far-frontier-server/frontier-v3-deposits-r174-20261016`;
old R172 world was not deleted or loaded with the new schema. Explicit runtime
selector r15, schema291, seed20260918065. Preflight with fresh-world absence and
post-start `frontier-v3-deploy-verify.sh` both PASS.

Service `far-frontier-v3-live.service`, start epoch1792161107,
invocation79ce1229cbf440a399ce63da194a32c0, wrapper4069843/Java4069867, port25565.
Fresh ready/startup records at14:31:58/14:32:00UTC. At tick1463: requiredConflicts0,
first home has two independent known mineral/worksite declarations; ordinary
COLD worker resident:1-10 already mined one coal with retained work:extraction-1.
At tick307: ready backlog0/lag0, navigation waiting0; recent host samples around
2.8ms mean/4.7ms maximum, not a controlled speedup or long-run performance claim.
No fast-forward was used on the new live world for this check.

First home's two storage interaction points are(-317,61,-339) and(-361,61,-297)
in `pale_mirror:frontier_graybox`. Concurrent work is demand-driven: one unit of
unmet fuel must not recruit two miners merely to demonstrate parallelism.
These coordinates aid inspection, not an assertion that both sites are always busy.

Temporary artifact HTTP server exited; no task-owned graphical client/display
or disposable scenario server remains. Pack tracked files unchanged; preserved
preexisting `.f0v-baseline/`. Governance unrelated WIP preserved; only R174
documentation/contract/architecture additions committed. No push or client update.
