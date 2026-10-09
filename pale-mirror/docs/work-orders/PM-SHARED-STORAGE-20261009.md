# Shared storage: complete adoption and legacy retirement

2026-10-09 static audit and accepted implementation. The inventory/findings
below describe the audited baseline; the implementation receipt at the end
supersedes their code status. The later user-authorized deployment is recorded
in the [R76 receipt](../frontier-v3-shared-storage-r76-20261009.md); no measured
shared-ledger speedup is claimed.
User direction: analyse everything that can use shared storage; leave no obsolete
parallel persistence path. This audit does not authorize another deployment or
change the active server. Main alone, no subagents.

Inspected source: `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`,
HEAD `e3377dd2fad28a6cb166f96512daabeb9a09a317`; deployed production
`57f0278ea278015f26be7d84d0008080e35ca65d`. The difference is a test scenario.
All source anchors below are relative to that Gradle root. `v3/` abbreviates
`pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/`.

## Outcome and boundary

There are **ten v3 SavedData ledger classes**. One uses the shared physical
journal, eight other ledgers are migration candidates, and the tenth belongs
to the obsolete stage/prefix field protocol and should be retired with that
protocol, not given a new durable implementation.

Shared storage means one maintained durability/framing/publication mechanism
with explicitly registered adapters. It does **not** mean one semantic ledger,
one giant snapshot, one unbounded queue, or a new owner of world/business state.
Separate typed namespaces and bounded streams are legitimate storage partitions,
not legacy. Canonical transactions and physical witnesses keep different
semantic contracts while reusing applicable low-level storage primitives.

Minecraft region/entity/player data remains Minecraft-owned. Debug artifacts,
complete-once offline proof publications and client capture markers are not game
state. Neither should be moved into a gameplay journal merely to remove files.

## Inventory

| Source in `v3/` | Current persistence and retained meaning | Disposition / partition |
| --- | --- | --- |
| `FrontierV3AmbientCarrierLedger.java` | Format11 actor identity, admission, custody and departure facets; dirty-actor journal images and durable receipts | Already adopted; retain exact world+actor semantics and bring registration/lifecycle into the common composition |
| `FrontierV3ResourceSiteLedger.java` | Format15; full compressed atomic/forced file at explicit physical boundaries; current per-cell field claims, growth/work/player/world-change/delivery witnesses plus old field state | First migration; dirty bounded site/cell shards with one consistent publication for related facets; delete obsolete stage/prefix state |
| `FrontierV3GrayboxLedger.java` | Format5; up to65,536 block provenance claims; whole SavedData serialization at vanilla save | Migrate bounded chunk/section shards retaining each exact block/owner/target/part; not one giant row and not65,536 rows under the actor profile |
| `FrontierV3DepotClickLedger.java` | Format1; up to1,024 exact player edit witnesses; custom compressed atomic/forced file | Migrate by exact container/witness; retain synchronous before-action durability unless the entire ordinary player-action protocol is deliberately changed |
| `FrontierV3ObjectBoardLedger.java` | Format1; up to256 board owner/position/UUID/conflict claims; vanilla SavedData | Migrate by exact declared board identity; board text/caches are not another authority |
| `FrontierV3HopperCarrierLedger.java` | Format1; up to4,096 UUID-position identities, derived inverse map; vanilla SavedData | Migrate identity rows; reconstruct and validate the inverse index, do not persist two independent owners |
| `FrontierV3PhysicalObservationLedger.java` | Format2; external explosion baselines and sequence, up to64 effects and65,536 candidate cells **per effect**; vanilla SavedData | Migrate effect headers/candidate shards/progress; publish sequence and new effect together; explicit aggregate byte/candidate bounds |
| `FrontierV3ManagedExplosionLedger.java` | Format4; exact intent, block/entity/item baselines, resolution progress and impacts; vanilla SavedData | Migrate bounded effect shards, preserving cause, non-replayable effect and canonical acknowledgement ordering |
| `FrontierV3InfectionOverlayLedger.java` | Format5; up to65,536 patches,16 physical columns each; PREPARED/ACTIVE/DEFERRED/CLEARED provenance; vanilla SavedData | Migrate bounded patch shards and derived position index; infrastructure adoption only, no additional hive feature breadth |
| `FrontierV3ResourceSiteExplosionLedger.java` | Format1; old field stage/site blast witnesses; capture explicitly rejects current cell-owned fields | Retire ledger, executor and registrations with old field protocol; retain current field world-change observation and extract any still-needed read-only field membership query |

Other inspected storage surfaces:

- `v3/FrontierFileStore.java` and `FrontierWalSegment.java`: **current**, not
  legacy, canonical format2 transaction WAL/snapshots with revision checks,
  turn-batched force and DURABLE_BEFORE_EFFECT prefix fences. Do not store its
  transactions as replaceable key images. Framing/checksum/atomic publication
  duplication can be extracted behind a shared storage core while its typed
  `FrontierStore` adapter remains the only canonical persistence owner.
- `v3/FrontierV3SavedEntityColumns.java:51`: read-only native region inspection,
  not a competing writer. Entity/item attached NBT belongs to native storage;
  preserve its write acknowledgements and PM identity protocol.
- `v3/FrontierV3LifecycleFilePublisher.java`, client capture/GLFW markers and
  `src/pilot/.../FrontierV3Offline*Publication.java`: development evidence and
  complete-once publication. Keep separate from mutable gameplay state; reuse
  a small atomic-file primitive only when publication semantics match.
- Historical `internal/world/PaleMirrorSavedData`, `FrontierSavedData`,
  `SourceGrayboxSavedData`, `SourceGrayboxPresentationLedger`; Visuals
  `FrontierGrayboxLedger`, `VisualGenesisSavedData`: **do not migrate historical
  simulation state into v3**. Audit their launch/callback reachability instead.
  Visuals authored genesis is a distinct feature-owned manifest store, not the
  active v3 field or actor ledger. A deliberately supported authored feature
  needs its own explicit adapter/disposition; an obsolete runtime path must be
  detached/removed, not hidden behind a second active backend.
- The pure `pale-mirror-frontier` module has persistence contracts/codecs but no
  direct filesystem writer found by the inspected FileChannel/Files search.
  Economy, needs, assignments, cargo and trade already belong to canonical
  world state/WAL; do not create separate resource/trade databases here.

## Confirmed source findings

### SS-01 — adoption is partial; full-file physical writes remain

`ResourceSiteLedger.persist/save(File)` at91/103 and
`DepotClickLedger.persist/save(File)` at68 onward each rebuild all records,
compress a temporary file, force it, rename it and force the directory. The
field growth projector persists prepared witnesses before the first effect and
results afterward. Those boundaries are necessary; full-registry compression
per boundary is not. R75 changed only actor storage, not these callers.

The other seven non-actor ledgers use vanilla dirty SavedData serialization.
This is an implementation difference, not proof that each causes the observed
server lag. Migrate useful current state, retire obsolete state.

### SS-02 — some claimed before-effect evidence has no explicit durability fence

`PhysicalObservationLedger.captureExternalExplosion` inserts a baseline and
calls `setDirty`; `PhysicalObservationExecutor.captureExternalExplosion` and
`ServerPhysicalInteractions.observeExplosion` do not force that witness before
the vanilla effect proceeds. `InfectionOverlayExecutor.project:153` prepares a
patch in dirty SavedData and immediately replaces world blocks. Managed blast
capture also marks SavedData dirty, without a forced witness receipt there.

`setDirty` is not a disk receipt. These source paths do not by themselves prove
the advertised crash-safe before-effect boundary. This is a confirmed missing
explicit fence, **not a reproduced live crash/data-loss incident**. Adoption
must define and enforce the applicable boundary, not just exchange codecs.
Minecraft's later chunk save still is not atomic with a PM journal append.

### SS-03 — the existing actor storage limits are not universal

`JournalStore:16–22` limits the image to4,096 rows/32MiB, a frame and admitted
queue bytes to16MiB, retained journal to64MiB. Graybox/infection can each retain
65,536 logical records. Observation permits64 effects with65,536 cells each;
large effect lists and NBT baselines are not bounded by an aggregate encoded
byte budget at that domain boundary. Whole-list suffix copies on observation
resolution also remain, independently of disk optimization.

Use explicit bounded partition/profile contracts and incremental progress;
do not globally inflate actor limits or convert a full world into one row.
Sharding alone does not remove the need for aggregate recovery limits.

### SS-04 — obsolete field protocol is still compiled and decoded

`ResourceSiteLedger` encodes both legacy claims/fences/receipts and current cell
claims in the current format. `ResourceSiteExecutor.projectStage:502`, old
harvest reconciliation and legacy preparation branches remain. Fresh ordinary
field admission uses `ResourceFieldInitialWriter`/cell claims; the current
growth path rejects a legacy stage claim rather than granting it cell authority.
`ResourceSiteExplosionLedger.candidate:98` explicitly supports only legacy
claims, but its executor is still reached by the shared explosion hook and by
field pending checks.

Retire the connected old producer/codec/consumer/registration graph together.
Do not migrate this old state. Current cell-owned blast handling must remain
connected through its exact world-change/block observation path. Removing the
old executor also requires relocating its still-used `activeOwnedCells` query,
not deleting current ownership filtering accidentally.

### SS-05 — legacy runtime construction precedes the v3 gate

`PaleMirrorEvents.onServerStarted:79` always constructs `PaleMirrorRuntime`
before starting v3; its constructor loads `PaleMirrorSavedData` and initializes
legacy services. `onServerTick:103` always calls that runtime;
`PaleMirrorRuntime.tick` executes distant-horizons/debug work before its v3
ownership return. Old compatibility preflight also runs at server-about-to-start.
SourceGraybox has an explicit pre-construction selected-launch guard, which
does not remove these other entry points.

This proves legacy runtime reachability during a v3 launch, **not** that its
economy advances or that it caused the measured spike. Detach old construction,
preflight and broad callbacks from v3 before calling the v3 storage transition
legacy-free. Trace debug/DH/API consumers and retain genuinely shared services
under explicit v3 ownership; do not lose them by deleting the old coordinator.

### SS-06 — lifecycle/readers currently cover one journal family

`ServerLifecycle.releaseRuntime:466` explicitly persists/drains the actor ledger
and awaits its checkpoints. Offline actor tools now use `AmbientCarrierLedger`
`readFile` (checkpoint plus tail). Other ledgers have no equivalent common
registration/flush/read contract. Migrating a writer without its shutdown and
every offline/diagnostic reader would leave a checkpoint-only data-loss path.

### SS-07 — nearby codec legacy must not survive a fresh-format transition

`ManagedExplosionLedger.Pending.load` retains unreachable format1 legacy
decoding although its top-level loader accepts only format4. Its item impact
codec writes `Outcome.ordinal()` and reads `values()[tag]`, contrary to the
stable-wire-tag rule. `ResourceSiteLedger.load` retains old-format conditionals
under a current-format-only gate. Remove dead compatibility decoding and give
current outcome tags explicit stable meanings during the affected codec cut.

## Target responsibilities

1. **Domain/family owner** creates exact immutable state/witnesses and decides
   transition, recovery and acknowledgement semantics. It declares which
   receipt is required before which physical/canonical consequence.
2. **Typed storage adapter** encodes that owner's bounded keys/images or ordered
   transactions, tracks dirty keys/tombstones and validates schema/identity. It
   never rediscovers family or owner from prefixes or payload shape.
3. **Shared storage core** owns framing/checksums, bounded ordered disk work,
   force receipts, immutable checkpoint publication and covered-tail cleanup.
   No field/click/explosion/actor business switch belongs here.
4. **Closed composition/lifecycle** declares store kind, schema, world,
   dimension, budgets and adapter, rejects duplicate/missing registrations and
   path owners, drains all admitted writes/checkpoints before release, and
   provides complete read-only recovery to live and offline consumers.

Shared worker infrastructure need not share one sequence across unrelated
streams. Group force covers only records in the actually forced stream; it
does not create a transaction across separate files, canonical WAL or Minecraft.
For an operation touching several facets, co-publish the required witness rows
in one store frame or use an explicit recoverable protocol between owners.
No silent cross-file atomicity promise.

## Implementation sequence

**A. Common registration, bounds and small storage seams.** Introduce explicit
typed descriptors/profiles and one world-scoped store owner/read interface.
Keep current actor guarantees. Support bounded dirty shards, metadata plus
tombstones and whole-effect progress without re-encoding unchanged candidates.
Extract shared atomic publication/framing primitives where the contracts truly
match; keep canonical transaction replay distinct from keyed-image recovery.
Do not require replacing the working canonical WAL before field adoption.

**B. Fields first; remove their old protocol in the same connected cut.** Migrate
current field evidence, initial preparation, growth, player edits, work/hand and
delivery witnesses. Preserve exact pre-effect/result ordering. For PM-owned
unstarted work, deferred receipt plus fresh server-thread validation is allowed;
do not retry an attempted ambiguous effect. Delete legacy stage/prefix writer,
claims/codecs, old field blast ledger/executor/registrations and obsolete tests
of those details. Retain real crop/block intervention and restart coverage.

**C. Remaining useful physical registries.** Adopt graybox/boards/hopper and
player-click witnesses; then generic observation/managed effects/infection
provenance using their explicit capacities and cause contracts. Small unrelated
ledger changes need no repeated whole-product campaign. Infrastructure adoption
does not enable additional hive/battle features. Critical synchronous player
hooks may wait for the bounded durable delta; never let vanilla proceed on an
unacknowledged future. An asynchronous rewrite requires an explicit ordinary
action admission/cancellation protocol, not a storage-only shortcut.

**D. Complete retirement and I/O consolidation.** Remove obsolete v3 writers,
old-format decoder branches and checkpoint-only readers; detach legacy runtime
startup/preflight/callbacks from v3. Audit each historical mode/Visuals consumer
before code removal: supported current content gets an explicit owner, obsolete
runtime and its detail-only tests are removed, not ported. Finish applicable
canonical WAL/shared low-level primitive reuse without replacing the canonical
`FrontierStore` contract or disabling DURABLE_BEFORE_EFFECT/turn-exit force.
Do not add a second canonical writer or Redis/database dependency.

**E. One connected acceptance and deploy checkpoint.** Current ledgers have
exactly one writer/backend, complete checkpoint+tail recovery, explicit schema
rejection, bounded pressure diagnostics and common graceful drain. Use a fresh
disposable world for incompatible formats; no retained-world converter, dual
write or fallback. Measure actual changed field/admission/storage paths, then
one ordinary HOT/COLD + intervention + restart scenario for the affected
product flow. Do not promise that this alone removes the first-visibility peak.

## Verification and completion

Reuse the existing journal failure/torn-tail/checkpoint/group-receipt tests.
Add only missing behavior coverage: shard-boundary recovery, stale/wrong world
or schema receipt, capacity/backpressure before effect, immutable queued bytes,
physical partial application, canonical acknowledgement before witness retirement
and all-store orderly drain. Update callers' existing tests rather than
retaining duplicate tests of removed storage internals.

Completion requires a source scan with no unexplained direct v3 gameplay writer
outside registered storage, no active legacy field protocol, no historical
runtime construction during v3 launch, no checkpoint-only reader and no
legacy-compatible decoder for unsupported worlds. Every remaining disk surface
has an explicit current owner/disposition. Provide verification and dirty-state
reports separately for implementation/governance and outer pack/original nested
repos. A green storage helper alone does not establish connected adoption.

## Implementation receipt — 2026-10-09

Implemented in `/home/rd/proj/pm-f06r3-facility-lane-recovery` on base `e3377dd2`.
This implementation checkpoint initially left live R75 unchanged. The subsequent
user-authorized source commit is `63a7c011297216b58aac68686df0fc9932db40e8`;
clean-ref R76 publication and restart are recorded separately. Main alone,
no subagents.

| Finding | Connected code change |
| --- | --- |
| SS-01 | All eight non-actor families now extend `FrontierV3JournaledSavedData`, using dirty keys/tombstones and family-declared fragments. Fields use immutable256-cell buckets; depot clicks use exact container rows. No family compressed full-file writer remains. |
| SS-02 | External/managed blast capture forces retained evidence before allowing the effect. Infection creation/replacement forces PREPARED with the exact predecessor before replacing blocks, and forces its result. Decontamination publishes its resulting provenance before canonical acknowledgement. |
| SS-03 | Separate declared ACTORS4096/32MiB and PHYSICAL131072/64MiB profiles; existing frame/queue/tail bounds retained. Large effect queues use immutable256-record shards and absolute progress cursors, with complete shard validation on recovery. |
| SS-04 | Old field claims/fences/receipts, stage writer, legacy harvest writer, field explosion ledger/executor/registrations and admission API are removed. Current cell-based ownership/observation, ordinary blast filtering and work/hand/delivery evidence remain connected. |
| SS-05 | Selected-launch check precedes `PaleMirrorRuntime` construction and SavedData loading, including startup, broad events and old network/item callbacks. DH is a separate shared service. Legacy threat-sandbox and old quest bootstrap are not initialized as v3 simulation dependencies. |
| SS-06 | Exact world/store registration, all-level physical-turn flush and orderly drain cover all nine current families. Live/fallback/read-only recovery combines checkpoint plus tail; field observation no longer reads just a compressed checkpoint. Component owner replacement explicitly drains/releases the old owner first. |
| SS-07 | Field16/observation3/managed5/infection6 reject unsupported schemas. Dead managed format branches and ordinal outcome serialization removed; wire identities are explicit. |

Storage owns framing/publication/durability, not crops, custody, trade or actor
policy. Metadata and related facets co-publish in one stream. No cross-file
atomicity, transactional-WAL replacement, automatic world migration or second
native region writer is introduced. Canonical `FrontierStore` remains a current
transaction owner; sharing an image backend would change its meaning and is
deliberately not part of this adoption. Existing atomic publication there is
not an obsolete keyed-ledger path. Native storage and offline proof/capture
publishers retain the dispositions in the inventory above.

Current schema table: actors11; fields16; blocks5; clicks1; boards1; hoppers1;
observations3; managed effects5; infection6. All non-actor disk files now require
the shared physical journal envelope even when their family payload version is
unchanged. A fresh disposable test world is required at deployment.

### Verification and retired-test disposition

`FrontierV3PhysicalStoresTest` exercises all eight real adapter codecs with
nonempty records, delta/tombstone publication, checkpoint-plus-tail recovery,
wrong-world rejection, clean-save no-op and failed disk fence retaining dirty
evidence. It also covers immutable queue/shard progress, exact infection
replacement predecessors, and field bucket/header recovery and corruption.
`FrontierV3JournalStoreTest` retains force/group/checkpoint/torn-tail/corruption/
failure coverage and adds the separate physical capacity profile.
`FrontierV3LaunchOwnershipTest` checks v3 rejection before even dereferencing a
server, hence before legacy hydration.

Removed detail-only tests: old stage-field `ResourceSiteGameTests`,
`ResourceSiteHarvestGameTests`, `ManagedSoilPolicyTest`,
`ResourceSiteHarvestExecutorTest`, and `ResourceSiteReceiptLifecycleTest`.
Their execution API/claims/receipts were removed from the production call graph;
they must not keep a second test-only writer alive. Mixed current tests retain
cell witness/recovery/owner negatives, scheduling fairness, causal ingress,
soil/crop pairs and delivery evidence. Current `ResourceField*` tests,
field-turn GameTests and actor body-lifetime tests retain those actual current
semantic/recovery boundaries. This retirement is not waiver of field/player
acceptance.

Final connected verification passed. The native
shared-storage slice combines current field-turn and body-lifetime coverage
with actual level-owned registration/flush/recovery for all eight adapters;
it is not a full-pack ordinary-player, interrupted-blast or human visual claim.
The first native attempt exposed two component recovery fixtures replacing a
registered actor ledger without releasing it. Exact owner rejection was kept;
the fixtures now perform explicit graceful release before replacement.
An intermediate simultaneous Gradle build/native attempt raced on the same
class outputs; its packaging failure is not accepted evidence. Final gates run
sequentially in that checkout.

Final commands/results, from the Gradle root:

- `./gradlew guardrails check :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar --no-daemon` — PASS,11m5s, `build/shared-storage-gates.log`; Frontier1238tests/222suites,0failures. No Frontier source changed. The changed NeoForge gate also passed; later focused selection replaces its XML inventory, so no unretained full NeoForge count is invented.
- Final focused `:pale-mirror-neoforge:test` selecting `*FrontierV3PhysicalStoresTest`, `*FrontierV3JournalStoreTest`, `*FrontierV3LaunchOwnershipTest`, `*FrontierV3GameTestSliceTest`, plus NeoForge build/package — PASS,34tests/4suites,7s, `build/shared-storage-final-focused.log`. Two local fixture mistakes were corrected: the512-cell case now uses explicit layout rather than the fixed8x8 producer, and disk fixtures initialize Minecraft version themselves rather than depending on class execution order. No production invariant was weakened.
- `./gradlew :pale-mirror-neoforge:runFrontierV3SceneGameTestServer -PfrontierV3GameTestSlice=shared-storage --no-daemon` — PASS,36required/0failed,52s, `build/shared-storage-native-final.log`: body-lifetime20,field-turn15,shared composition1. Normal stop/all dimensions saved11:26:50; server exited. The concurrent body fixture batch emitted one8096ms keep-up warning; this is not a live performance measurement or an all-lag closure.
- Final behavior-neutral cleanup removes unused duplicate file-name declarations, leaving the closed registry as their sole declaration. `guardrails`, `compilePilotJava`, `jar`, `verifyPackagedJar` — PASS,11s, `build/shared-storage-final-package.log`. The prior semantic evidence remains applicable; no confidence native rerun.
- Canonical architecture contract and compact continuity validators PASS; source/governance `git diff --check` PASS.

Final WIP JAR `pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar`
SHA512:
`66cc549ad298857a7bcb491728254f7a6bd1d2430c95c342d6caa3017f4596bb6cfc552f04b2c6e4f266ab4ec4a13356645d19f7ff83c2da3835ed879f69ffe7`.
At the implementation checkpoint this was an uncommitted technical build. The
subsequent clean detached build of `63a7c011` produced the identical SHA512.
Source checkpoint:63dirty paths (53tracked changes/10new files), all this task;
governance has scoped receipt/architecture/performance/continuity edits mixed
with preserved pre-existing WIP. Outer pack remains only `.f0v-baseline/`;
original nested repository retains its23unrelated WIP paths. Neither changed.
No task Gradle/GameTest/client/display remains. Live service is still active
with invocation `4f001fe3fdb149169ea29bfcc54e2c73`,MainPID3590590: no deployment.

No new TPS/latency gain is measured here. Live R75 actor-only measurements remain
valid for that narrower stream, not all eight migrated families. New deploy,
fresh world and ordinary HOT/COLD/player-intervention/restart acceptance remain
a separate deployment checkpoint; no HUMAN_CANDIDATE or whole-feature promotion.

The initial static audit ran **no client, test matrix, benchmark, restart or reset**. Source
and server were not changed. Prior live maxima (~549ms graybox/first visibility,
~89ms field projection) identify expensive stages, not exact root causes or a
guaranteed gain from the proposed migration. The measured R75 actor-ledger7.67x
result must not be extrapolated to all other ledgers or total TPS.
