# Storage and Minecraft operation integration

Accepted scope: user, 2026-10-21. Implementation and scoped integration delivered
2026-10-22 at 2e185ec0. Main alone, no subagents. This is not a claim of global
Minecraft/canonical multi-file crash atomicity.
Goal: the four storage improvements plus one Minecraft integration protocol.

## Delivered result

All four points and the active operation-family integration below are wired.
Storage is lazy/bounded through the live consumer, normal mutations are local,
retirement retains unresolved obligations and compressed replay fences, and
Minecraft effects use common prepare/evidence/confirm/recovery/retirement.
Business aftermath stays with registered domain owners, not the common coordinator.
The implementation notes below are chronological; their intermediate outstanding
test statements are superseded by the delivery receipt at the end.

## Boundaries

- Domain owners retain business decisions, exact identities, effect permissions
  and outcomes. Common orchestration must not inspect concrete job phases.
- Physical adapters retain Minecraft effects and same-object save evidence.
- Shared operation orchestration retains declared participants, durable intent,
  receipts, recovery classification and obligation-aware retirement.
- Storage owns bounded reads/writes, checksums, persistence acknowledgements,
  checkpoints and cache eviction, never game semantics.
- Vanilla chunks, entities, players and the canonical WAL are independent save
  participants. A same-callback mutation or attachment is NOT a multi-file
  atomic transaction. Missing evidence is not proof of non-execution.
- Player/mod changes remain ordinary external facts. Do not roll them back or
  replay an ambiguous operation based only on matching item counts/block types.
- No transaction per animation/frame/movement sample. Body admission/departure
  and custody transitions use the protocol; ordinary locomotion does not.
- Active families: farming, mining, bakery/stations, nutrition, resource storage,
  ground pickup, internal hauling, commercial caravans and common bodies.
  No hive/combat/medical feature expansion. Preserve their existing fences.

## Source-established starting gaps

1. BlockJournal opens every section at startup. Its image() merges all rows.
   JournaledSavedData.get hydrates/re-encodes that complete image; attachJournal
   calls image again. Thus spatial files do not bound resident memory.
2. BlockJournal.append returns pressure(), which scans every opened section on
   every ordinary write. Diagnostics and write receipts need separate paths.
3. GrayboxLedger keeps world-wide decoded maps and indexes for temporary staging
   and external worksite changes. Lazy recovery must preserve the latter causal
   obligations before COLD work, not silently omit unloaded sections.
4. JournalStore already supplies checksummed ordered append and background
   checkpoint pruning. Reuse it; do not build a second WAL or treat physical
   storage checkpoints as permission to erase semantic recovery witnesses.
5. Minecraft 1.21.1 ServerLevel.save separates level/chunk/entity persistence;
   PlayerDataStorage is separate. ChunkSerializer includes attachments but catches
   their serialization exceptions. Critical receipt omission must fail closed.

## Implementation sequence and exit conditions

### A. Bounded spatial storage through the live consumer

- Journal point reads and a bounded section LRU; independent immutable async
  work may outlive cache residency only under explicit writer lifetime fencing.
- Lazy table hydration through the existing physical storage composition, not
  an unused alternate map. No global decoded/committed row mirror.
- Durable section/query summaries preserve global unresolved obligations and
  staging lookup without reading every block. Summary publication/recovery must
  account for a crash between child and root writes; an index never grants
  missing/false evidence. Explicit version boundary if needed.
- Ordinary append touches changed sections only. Full export/audit remains an
  explicitly expensive operation, not a runtime get/attach/write prerequisite.
- Focused checks: eviction/reopen, exact negative bindings, pending writes,
  corruption/missing section, partial publication, >old quota, bounded cache and
  unchanged-section access counters. Production ledger recovery is mandatory.

### B. Operation contract and Minecraft save participants

- Inventory actual active prepare/apply/observe/confirm/recover/retire paths.
  Reuse existing exact domain intents, custody and body-save bridges.
- One typed operation envelope/participant contract and registered adapters;
  no family-dispatch switch in the coordinator and no guessed IDs/owners.
- Co-serialize receipts with affected blocks/containers/bodies where supported.
  Exact snapshot/save generation, error propagation and recovery binding are
  required; chunk save events alone do not prove disk persistence.
- Every targeted producer consumes the common integration boundary. Remove
  migrated bypasses, retain explicit unsupported/ambiguous outcomes.
- Prove relevant crash cuts before/after physical application, participant save
  and canonical confirmation, including mixed participant generations and player
  intervention. No invented restore/compensation of external player inventories.

### C. Retention and local work

- Separate current world provenance, unresolved operations, replay fences and
  diagnostic history. TTL alone never retires custody evidence.
- Retirement requires participant durability and no live incoming obligation;
  replace detailed resolved history with sufficient monotonic fences only when
  stale/replayed effects remain rejectable. Checkpoint pruning uses this boundary.
- Audit changed-family full scans/serialization; replace normal mutation-path
  work with exact-address updates, dependency wakes and bounded indexed queries.
- Expose cache residency/read misses, pending save age, retained obligations and
  write/checkpoint cost. Diagnostics must not trigger world hydration.

### D. Integration delivery

- Source review of all target paths plus focused invariant/recovery tests.
- Existing native harness only for Minecraft save/reload/player seams, one
  capped-FPS client, shut down after checks. No blanket proof campaign.
- Review task diff, commit implementation, build clean detached ref, deploy,
  verify installed identity/startup and scoped HOT/COLD operation/restart.
- Report source/unit/native guarantees separately. Fresh world may be required
  for a deliberate schema boundary; never reset to hide an unresolved defect.

## Completion standard

All four storage points and targeted operation consumers are wired; an unused
coordinator or merely sectioned files do not meet the goal. Preserve actual world
state/player causality and existing durability while removing global hot-path
work. No unbounded-memory, arbitrary-mod exactly-once or global-ACID claim.

## Native mutation versus durable observation

The active farmer COLD-hand presentation now enters the common operation boundary.
Its retained producer declaration distinguishes materializing unbound COLD stock
from binding previously witnessed carry. The latter never issues missing items;
neither repeats an applied effect from counts. Canonical binding is a durable
consequence. Ordinary and deferred farmer delivery use the registered physical
container port, including its native save participant, rather than a direct
chest adapter. Death during outstanding hand/delivery operations retains explicit
owner aftermath, as described below. Wiring is not completed release acceptance.

The integration has two explicit entrances, not two execution engines:

- A mutation retains its exact intent before its single native callback, then
  co-saves its token with the affected participant. The intended canonical
  consequence may be retained before execution; this is not permission to submit
  it before positive native evidence.
- An already durable native observation (currently body departure) reuses its
  existing exact snapshot/write+sync/read proof. `ENTITY_SNAPSHOT` names the full
  image digest and residence; it is never a live-entity mutation port and never
  acquires replay permission. The producer validates current residence, saved
  image, pending reads, absence and custody fences. Common confirmation retains
  the canonical command and retires the observation after acceptance. No marker
  is retroactively invented in an absent entity.

Body fatality enters at LivingEntity's `dead` field write after the cancellable
NeoForge death event and duplicate-death guard, before loot. A cancellation must
never retire a canonical actor. Native fatality and each resource owner's
aftermath remain distinct obligations; the latter are not proven complete by
body confirmation. Final recovery acceptance must still cover interruption
between canonical death and owner-specific loot settlement.

The common owner outbox now distinguishes canonical command acceptance from
explicit aftermath completion. Body fatality retains immutable bounded pre-loot
native evidence in that outbox; it is not permission to restore inventory.
Registered resource owners supply recovery and completion, and unresolved
aftermath prevents destructive body removal and evidence retirement.

Conditional child outcomes are now retained in that parent context before the
native death boundary. A common journal gate consumes a positively unused child
permit, without replacing its living owner's command. Only positive native
parent application unlocks cancellation; a recovered PREPARED row or historical
APPLIED flag does not. The parent cannot retire while it retains these gates.
Meal, expedition-transfer and mining owners supply typed outcomes; the common
ledger never inspects their phases. The operation journal is format5 and the
pre-loot image format2 (fresh-world-only).

Mining's retired-effect reducer shares ordinary resource accounting but never
selects another source for a dead worker. Its job/source reservation is released
only after the pending effect, held tool and cargo settle. Portable inventory
uses the existing common interaction fences and cannot dispose a pending work
effect's source. An unexplained missing/substituted tool remains unresolved,
not a fabricated loss or free replacement.

Shipment endpoint effects now supply the same conditional aftermath. Applied
loading/unloading uses existing resource accounting and receiver-owned payment;
unused effects clear only the pending step. An uncollected dead carrier releases
its allocation without destroying stock. A retained hand cargo blocks completion
until its actual disposition, not merely until pendingPhysicalStep is empty.
The common resource-transfer adapter explicitly accepts a retired observation
under its exact body/execution identity, not a permission for new work. Dead
attached storage retains its native bindings until the attachment owner drops it;
ordinary COLD/projection and conflict fences are unchanged.

Bakery hand transfers now capture the same conditional outcomes. Its terminal
death transition releases its own job, scene, UAE and financial obligations only
after effect settlement and actual carried-resource disposition. Station-owned
abandonment preserves existing input/output and spent heat. The common inventory
owner cannot erase a retained bakery hand. Domain cuts cover all four transfers,
both outcomes and subsequent terminal closure; native/crash acceptance remains.
Exact station-only recipes now participate through the same owning job's retained
outcome; fungible station recipes keep their independent owner. Applied/unused
domain branches preserve station stock and close the dead worker without another
recipe. Source inspection disproved the suspected DRAINING revision change:
SceneLease.withStatus preserves revision; no speculative epoch schema was added.

Farmer harvest-cell effects now retain the same conditional outcome. Its death
callback no longer conflicts the entire field or blocks the shared strategic
task. Applied accounting preserves yield without choosing another cell; unused
cancellation clears only the exact pending effect. The registered field owner
drops retained yield through the common entity-resource operation and atomically
retires its job/scene/UAE/intent only after physical acceptance. The original work
is abandoned, never misrepresented as a successful delivery. Independent farmers
and the shared task remain unchanged. Delivery, hand projection and terrain-only
sow/till aftermath are wired below; source/unit checks do not establish native
fatality/crash acceptance.

Farmer delivery now shares its native declaration/ports/receipt with retired
aftermath. Applied delivery settles cargo/container and retires only that worker;
unused cancellation leaves stock for actual death disposition. Neither branch
starts another crop or fabricates depot arrival. The family witness retires only
after its common outcome closes. Domain/static checks pass; native fatality and
process-crash acceptance remain outstanding.

Farmer hand projection now captures the original common outcome in fatality's
pre-loot context. Only an existing admitted projection may finish before that
snapshot; a removed previously applied hand cannot be recreated. Retained carry
binding belongs to the exact body/work/account, not to the continued existence
of its old work scene or a sibling's field-writer slot. The shared stock binder
does not issue yield. Applied binding then permits the normal actual death drop.
Domain/static and native hand-component checks pass; this is not complete native
fatality or crash acceptance.

Farmer terrain-only till/sow now uses the same conditional aftermath without
inventing an entity participant. The explicit parent/child causal dependency is
independent of overlap between native media; unused-permit exclusion, immutable
parent context, positive native outcome and no nested/cyclic gates remain.
Pre-loot hand inspection recognizes the retained exact body even at zero native
health, without granting live actuation. Applied field acceptance is retired and
acknowledged in the same death settlement, before terminal job/body closure.
Focused domain coverage includes applied/unused harvest, till and sow; a connected
native till -> actual vanilla death -> canonical accounting -> job/UAE retirement
case passes. This is component acceptance, not process-crash or player-visit proof.

Fixed-container removal now wraps the actual LevelChunk palette mutation before
onRemove. Emptying an inventory is no longer treated as provider destruction.
Its common operation retains a self-contained explicit-owner outbox before the
independently persisted family index, which can be reconstructed without granting
another native attempt. An exact terrain receipt, completed stock disposition,
custody closure and canonical retirement precede final owner acknowledgement.
Provider destruction preserves old block-entity receipts through the existing
retirement bag. Native drop fragments decrement the source only after successful
insertion; partial refusal preserves remaining physical stock and remains an
explicit unresolved operation, never fabricated completion/replay. Handoff
journal schema10 requires a fresh disposable world. Component checks do not
establish process-crash or ordinary player-visit acceptance.

## Final source classification and integration verification

Attached storage already closes through the reference custody owner:
`retiredEmptyAttachment` requires the exact dead body/tombstone, no remaining
lots, claims, bindings or exact items, then the existing executor checkpoints
and releases that lease. Shipment ground pickup likewise has a registered
`GroundPickupRecovery` closure after the exact shipment transition. These are
existing consumers, not reasons to create another recovery owner.

External field/extraction observations retain typed causal facts but do not
execute or replay native writes or award work yield. They therefore must not be
given fabricated APPLIED receipts. Planned terrain writers use the operation
boundary; observations remain the owning family's read-only reconciliation.

Current source audit found no additional producer replacement requirement in
these paths. Remaining work is integration recovery and release validation,
including ordinary player operations and a fresh-schema deployment. Full native
gate: 394/395 passed initially; its patrol failure was a fixed accelerated-tick
expectation for asynchronous admission. The repaired readiness-based patrol
slice passed all four cases. Three obsolete Java assertions were updated to
retain independent field work and the complete registered resource-owner set.
The complete Java gate exposed additional navigation/fixture failures still
being classified; it is not yet a passed release gate.

The ordinary player menu scenario reached a real process crash after the native
withdrawal/save and before canonical confirmation, then recovered the withdrawal
and returned the same64 cobblestone through another ordinary click. Canonical
stock64 and the reviewed native frame show one stack and an empty player inventory.
Passed receipt: build/r213-player-recovery-acceptance.json, run
ca949280-78ed-4fa4-9078-12d6ea4eade1, before/after traces and linked
recovered-stock-returned-once frame. Runner and isolated server/client stopped;
task-owned Xvfb stopped. The earlier final.json run completed the physical action
but failed its reporting oracle; it is not the passing acceptance receipt.
This is a private-display targeted recovery check, not human visual acceptance
or arbitrary mixed-file atomicity. Earlier setup failures were wrong adjacent
floor height and an obsolete expectation of a retained zero-quantity account;
the actual domain retires that empty account. Neither needed a production change.

Integration repairs: field test adapters now reduce both typed labour-change
variants; batch inventory command is explicitly command-only; unknown geometry
assertions preserve typed waiting. Frontier test heap is bounded at2GiB because
the full ArchUnit import exceeded Gradle's default512MiB. Focused113 tests pass.
The release sweep also exposed genuine old geometry contradictions: migration
compiled constant-Y routes (97 unsupported cells in seed91), and engineering
staging borrowed an adjacent road height without a floor. Migration now delegates
to shared actor-profile navigation, retaining its bounded strategic waypoints;
staging uses the common authored-ground provider with current topology. Dynamic
pedestrians are not permanent strategic obstacles. Missing route knowledge does
not admit a resident or reserve housing; normal review remains responsible.
Connected navigation/restart/architecture slice passed43s. Final full integration
finished in build/r213-release-final.log; its exact outcome is recorded below.

## Delivery receipt, 2026-10-22

- Source checkpoints: 4f75ec6c integration, 6b0f117b asynchronous migration
  admission, 2e185ec0 C2ME-compatible bounded shutdown. Clean detached release:
  /home/rd/proj/pm-r213-release.p58eDM/source/pale-mirror.
- Full sweep: frontier1772 with one failing new migration-admission negative;
  NeoForge796 with zero failures/one preexisting skip, native395/395 passed.
  The failure exposed synchronous knowledge.path bypassing the bound planner.
  Switching to plannedPath passed all12 migration cases and architecture7 in
  the detached build. The failed full aggregate is NOT relabelled green;
  its unaffected results are reused with this explicit correction.
  Logs: build/r213-release-final.log and r213-detached-admission-final.log.
- Full-pack startup exposed competing Queue.size redirects with installed
  C2ME0.3.0+alpha.0.93. Actual MC source and installed C2ME bytecode establish
  that C2ME returns MAX_VALUE there. The bounded shutdown owner now snapshots
  actual queue size on the first poll, after scheduling unloads, without
  intercepting that sentinel. Existing retry budget and ordinary running path
  are unchanged. Focused unload tests/guardrails PASS12s; final clean packaged
  JAR PASS6s. Logs: r213-c2me-unload-compat.log, r213-detached-final.log.
- Published and installed through pack scripts, Java22/C2ME combined8/8,
  world frontier-v3-r213-storage-20261022 seed125; old R204 world not reused or
  deleted. JAR SHA512:
  ea400673ae100c60cec26e41bacfb99176563f514d80c77343f9e897fc1f199c8f76542adc768151c57799557d0ec80fc20fed11a7d7dccd1ef4b06ff3e3a79f.
- Actual full-pack player 7->8->7 storage GUI/reentry then exit: PASS run
  71dc844e-1d2c-4b27-8b28-4440e04a7ef1. After save/restart the same scenario
  PASS d9091f57-d1b9-49bf-83a6-61e0ef61b71f. All six exact GUI frames reviewed:
  stock retained, empty player inventory, no conflict. Receipts:
  build/r213-live-storage-boundary.json and r213-live-storage-after-restart.json.
  Private-display integration evidence, not manual human M3 acceptance.
- Pre-restart live diagnostics:126 declared block sections,64 resident (limit64),
  160 evictions; operations pending0, awaiting native save0, awaiting owner0,
  retiredRanges1, every journal queuedWrites0. After restart before client visit:
  same126 declared sections,0 resident/0 sectionReads. This proves lazy hydration
  and bounded residency on the exercised world, not an arbitrary-world speedup.
- Summary green before/after,389 residents, no inventory/scene/replica-custody
  conflicts; no ready schedule backlog. Retained receipts:
  build/r213-live-before-restart.json and r213-live-after-restart.json.
- First restart followed explicit save-all flush then service termination; do
  not mislabel that signal as vanilla graceful shutdown. A subsequent ordinary
  RCON stop completed all-dimension saves and C2ME storage thread shutdown,
  followed by another successful startup. Receipt:r213-live-graceful-stop.log.
  Final installed/runtime verification:r213-live-final-verify.log.
- Initial visits took19.3s/11.1s including new chunk/render readiness; after
  restart the corresponding visits took1.4s/0.1s. Initial host peaks3s remain
  observed; this is not proof that all chunk latency has been eliminated.
  Quiet COLD PM turn averaged about2ms over the sampled64 turns, no ready lag.
  No fresh long economic run or performance multiplier is claimed.
- Test clients and private display stopped. No push. Existing unrelated pack/
  governance files preserved. No further acceptance matrix is required for the
  unchanged evidence above.
