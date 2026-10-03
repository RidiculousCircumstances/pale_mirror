# Renewable fields: layout-independent cell lifecycle

Status: user-accepted architectural requirement, 2026-09-23. Implementation is
not complete. Supersedes the fixed 64-cell/64-output assumption for fields;
does not authorize a live-world reset or an implicit persistence migration.
Current assignment remains main alone. Main owns source review, focused checks
and client verification; human product judgement may be requested separately.

## Identity and geometry

- A field is a stable domain object, not a square template, one output stack,
  or the presence of every expected crop block.
- The owning plan supplies a versioned layout of explicitly identified cells.
  Shape may be irregular, contain holes or disconnected patches, and use different
  support heights. Every cell has explicit typed support/crop/workstation geometry.
  Physical traversal capability and reachability still constrain actual work.
- Size is independent of Minecraft stack capacity. Production limits are explicit
  resource budgets, not a semantic constant of 64 cells or a machine-word bitmap.
  Read/write/navigation work is bounded and chunk-indexed; arbitrary layout does
  not permit force-loading the field or scanning every cell each tick.
- Cell IDs persist through layout revisions. A saved cursor, observation, pending
  effect or route cannot reinterpret index N against a different current array.
  Expansion adds cells; removal retires affected obligations explicitly. Neither
  resets the same worker, repeats completed labor nor teleports the actor.

## Cell state versus process state

- Soil condition, crop/growth state, occupancy and harvest outcome are per-cell
  facts. A global phase or board percentage is only a summary; it cannot replace
  heterogeneous crop state or make late-added cells instantly mature.
- Player crop removal, plain soil needing tillage and a foreign obstruction have
  different typed observations/dispositions. They do not mean field destruction.
- Known lost crop is not settlement yield. Actual harvested quantity and completed
  cell work are distinct values. No air-to-wheat projection may erase a recorded
  current-cycle loss. Canonical accounting must retain loss across HOT/COLD/restart.
- Plain dirt may enter explicit worker-owned repair/tillage followed by planting;
  no silent projection heals the cell. Foreign blocks are not overwritten.
  Blocked/removed cells are skipped or remain locally pending according to the
  declared work plan; other reachable cells continue.
- If no cells can currently be worked, expose that reason, release idle physical
  work safely and retain a bounded continuation/replan policy. Do not show endless
  active harvesting, keep generating completed tasks, or manufacture positive yield.
- Crop breaking is accepted only from the actual observed outcome. A cancelled
  block-break event must not count as lost harvest. An interruption during an
  already prepared farmer action retains its explicit ambiguous/exact disposition.

### Observed cell loss during an active harvest

- The stable CellId, site, layout revision, cycle epoch and cause identify the
  change. For a player break, admit the action before Vanilla mutates the block,
  then confirm the exact postcondition; a cancelled action changes neither the
  cell nor the job. Other world mutations need the same typed, durable
  cause/observation boundary. A later crop-age comparison is only a physical
  validation fact, never proof that the player removed a crop or that the
  farmer harvested it.
- One field/work owner applies a confirmed loss to both the cell and the active
  harvest job in one canonical transition. If the cell has no already-confirmed
  farmer harvest, account it as zero-yield `SKIPPED_LOST_CURRENT_CYCLE` in that
  job's current-cycle work pool. It remains a field cell and may grow or be
  repaired for a later cycle; regrowth cannot retroactively add yield to the
  closed current-cycle outcome. This is not a whole-field failure.
- If the lost cell was the selected target, invalidate that goal and choose
  another still-unaccounted reachable cell. Do not replay completed work or
  teleport the worker. If it was the last outstanding cell, finish the current
  batch from actual held yield and use ordinary movement/custody to deliver it;
  zero yield creates no wheat. Board phase, job count, hand quantity and field
  outcome must agree through HOT/COLD handoff and restart.
- A prepared or in-flight farmer effect on that same CellId is resolved from its
  exact crop/hand witness before applying the external-loss disposition. An
  ambiguous effect remains local and pending; it may not be guessed into either
  a harvest receipt or a skip. Duplicate/stale observations are idempotently
  rejected or recognized without double yield or double completion.
- Acceptance must cover cancelled break, loss before and during farmer work,
  loss of the last outstanding cell, regrowth within the same cycle, HOT/COLD
  handoff and restart, exact yield, and physical delivery or zero-yield closure.
  The current source implements the cell/job transition for confirmed owned and
  foreign crop loss. One ordinary-client native case now proves an actual player
  break of the last outstanding cell, zero extra yield, graceful restart and
  entry to growth epoch 2. It does not prove every interruption or a complete
  subsequent harvest cycle.

### Work reevaluation after a resource transfer

- Work execution is hybrid, not tick-polling for every choice and not a chain of
  worker-to-worker callbacks. A confirmed resource transfer wakes the owning
  settlement's planner once. It reads current stock, custody, station, workers,
  active tasks and policy, then may choose the next job. A farmer never directly
  commands a baker; a baker never trusts an unconfirmed chest image.
- Movement, growth, recipe duration, retry and recovery remain scheduled bounded
  processes. Events wake decisions when their prerequisites change; the periodic
  strategic review remains a recovery backstop, not the primary latency path.
- The wake is owner-scoped, idempotently identified by its producing harvest part
  or terminal receipt, and replayable after restart. Empty/obsolete wakes must
  not create work or duplicate an active objective. Other production and custody
  transitions should use the same one-shot reevaluation pattern as they gain
  verified consumers; no global event-bus rewrite is implied by this field cut.

## Work, yield and successor

- A job retains one layout/revision-bound pool of stable CellIds and explicit
  per-cell outcomes. The completed count is not a position in the layout.
  Navigation consumes semantic workstations through the shared movement provider,
  not a serpentine formula tied to one template. Reachability failure is local.
  The accepted field-first goal/provider split, including bounded Minecraft HOT
  navigation and COLD knowledge, is defined in
  [`frontier-v3-goal-navigation.md`](frontier-v3-goal-navigation.md). Its adoption
  is not established by this requirement alone.
- Completion accounts for every admitted cell as harvested, already removed,
  blocked or retired; it does not require every cell to have produced one item.
- A physically witnessed unavailable target is accounted locally, one CellId
  at a time, without changing its crop, body location or yield. The area owner
  then selects another unaccounted target; it never routes through the blocked
  workstation merely to preserve an ordering prefix. A blocked target remains
  in the field identity and can be retried in a later cycle after an observed
  change. An obstacle in the transit route is a distinct navigation problem:
  it may not be misreported as a blocked crop cell or used to invent work. If
  the selected target has no route, the area owner may select another
  physically/epistemically reachable outstanding CellId while retaining the
  failed target as pending. When none is reachable, the worker holds a typed
  navigation block and retries on a bounded cadence when the world/route condition changes;
  it does not falsely complete or repeatedly rotate through unreachable cells.
- The owned crop/soil and workstation headroom are independent observed facts.
  A player block above a sound crop may make the workstation inaccessible;
  removing the block does not imply the crop or soil changed. A removed crop
  or damaged soil likewise does not destroy the field or cancel other targets.
- Yield may be zero, below one stack or above one stack. Zero yield completes a
  cycle without creating an item; positive yield is packed into separately identified
  stacks under actual storage capacity/custody. No synthetic zero-count stack.
- A field may contain 65,536 cells, while the reference depot holds only
  27 stacks (at most 1,728 ordinary 64-stack wheat). Consequently a single
  all-at-once terminal deposit cannot implement arbitrary field size. Work
  streams one bounded carried batch at a time. Each confirmed yielding cell
  extends that batch's exact canonical quantity and its HOT physical evidence;
  the same site/epoch/part identity is retained until the batch reaches 64
  units or the last admitted cell is accounted. The farmer then uses the shared
  movement path to deliver it; if storage is unavailable, the worker retains
  the bounded batch and pauses further yield-producing work. No resource may
  be hidden in an unbounded pending total or teleported into the depot.
- A crop lost before work adds no unit to the batch. The 64th actual yield,
  not the 64th visited cell, closes a full batch. A final partial batch closes
  only after the cycle is accounted; a zero-yield terminal has no lot but still
  needs a durable one-time completion fence. Each delivery has its own
  before/after physical receipt and stable part identity, so restart may
  confirm or hold one batch without replaying the whole field.
- Quantity, output identities, delivery receipts, relation retirement and successor
  activation must agree. The next growth/repair cycle preserves the same field and
  worker continuity independently of whether the previous cycle produced items.

### Chosen physical/custody cutover

- Replace the stage-plus-prefix physical claim, rather than layering per-cell
  exceptions over it. A versioned claim records layout revision and the exact
  last-committed PM-owned physical condition of each claimed cell, partitioned
  by chunk. Foreign/unknown blocks are not guessed from the canonical
  `OBSTRUCTED` category: retain their exact observed block identity in a local
  incident and exclude that cell from owned writes until resolution.
  A bounded projection retains an exact before/after cell transition and cursor;
  restart validates the committed prefix and untouched suffix before resuming.
  This SavedData is a physical witness, not a second yield or growth authority.
- Natural observation compares a loaded cell against its physical claim. Actual
  AIR crop and plain DIRT support are typed local loss observations; cancelled
  clicks leave no changed postcondition and create no loss. Foreign obstruction
  is local blocked evidence. A cell with an unresolved farmer effect stays
  ambiguous until its exact postcondition is known; it cannot gain yield from
  an inferred cause. Unknown foreign drift remains visible, never overwritten.
- An ordinary loaded-world block mutation of an already claimed crop/support
  retains the exact pre-write cause and a durable per-site canonical hold before
  Vanilla changes the block. The later one-cell observation confirms or cancels
  that cause. Periodic scanning is a bounded backstop, not authority to let COLD
  outrun an unobserved physical edit. Explosion handling must discriminate
  legacy whole-site and cell-owned claims explicitly; invalid/foreign blast
  results remain diagnosable and cannot silently vanish from the field owner.
- Validate the physical witness at distinct checkpoints: immutable layout at
  COMPILED, actual owned blocks after MATERIALIZED/SETTLED, and the same claim
  plus committed projection prefix after RELOADED. Read-only inspection may
  report pending or unverified unloaded cells, but never force-load, adopt a
  matching-looking unclaimed block or repair an incident as a side effect.
- HOT and COLD emit the same layout-revision- and CellId-bound work result.
  COLD does not touch an unloaded block; later projection catches up only from
  an exact owned predecessor. HOT verifies the actual cell action before
  advancing the retained worker cursor. A farmer's ripe-crop visit replants
  age-zero wheat; a missing/dirt visit plants/repairs without yield.
- Reuse the existing fungible-resource lot/account/binding model for wheat
  quantity instead of inventing permanent identities for arbitrary Minecraft
  stack slots. Zero yield creates no lot. Positive yield is split into bounded
  lots and physically bound to 1–64-unit carried/depot stacks at real capacity;
  if capacity is absent, production remains pending/blocked rather than hiding
  stock or minting a zero stack. Downstream bread work consumes the same exact
  lot quantities. Existing exact-item harvest is retired in one versioned
  cutover, not left as a parallel owner.

## Source-confirmed current blockers

1. The retained progress count and `ResourceSiteHarvestJob` cursor now use the
   admitted cell total, but `ResourceSiteHarvestTraversal`, output admission and
   related physical execution still bind work to the fixed 8x8 geometry. The
   current traversal assumes each successive workstation is adjacent on one
   height and `TraversalTopology.MAX_NODES` is 4096, whereas a layout may have
   65,536 cells. A general executor therefore needs retained bounded route
   segments/work batches tied to stable CellIds, not merely a longer single
   corridor or a larger progress integer. A blocked segment must not reinterpret
   the remaining work plan or silently teleport its worker.
2. `ResourceSiteExecutor.observeBlockBreak` unconditionally records a field conflict;
   `matchesClaim`/`matchesHarvestProgress` reject a missing individual crop.
3. `ResourceSiteHarvestProcess.reduceProgressed` creates exactly 64 wheat;
   `ResourceSiteHarvestObservation` and `ResourceSitePhysicalIntentStateSupport`
   validate fixed-size outcomes. Partial/zero harvest cannot be made correct by
   weakening the adapter's block comparisons alone.
4. `ResourceSiteLifecycle` owns one uniform growth stage and no per-cell conditions;
   snapshot/WAL codecs and retained lineage must explicitly adopt the new model.
5. The physical harvest receipt/exact output-slot logic assumes one named output
   stack. Multiple/zero outputs require a real outcome, not a count-only patch.
6. The current player-break admission runs on `START_DESTROY_BLOCK` / left click,
   before Minecraft proves the block was actually removed. A cancelled break must
   not become a durable lost-crop event. Local crop/soil transitions need an
   admitted action plus a later exact physical postcondition, including restart.
7. The loaded-field check currently requires every field chunk at once, and
   growth/projection scans all cells. That is acceptable for the existing 8x8
   producer, not for arbitrary field size. Per-chunk work and a retained bounded
   cursor are required before larger plans can be admitted.
8. `ResourceSiteLifecycle.harvested*` immediately enters GROWING stage zero;
   `FrontierV3ResourceSiteExecutor` projects wheat back onto every slot. There is
   no planting or tillage work owner. Wiring honest ABSENT/NEEDS_TILLAGE cell
   state into the current lifecycle without a successor planting phase would
   stall the second cycle; letting stage projection fill those cells would
   manufacture an unobserved repair. The replacement must own a bounded
   planting/repair duty and its HOT/COLD continuation before switching authority.
   The selected minimal design is one retained farmer work visit per cell:
   harvest ripe crop if present, till plain dirt if needed, then plant the
   successor. Yield is counted only for the ripe crop actually harvested.
   Obstructions remain untouched and receive a local skipped/pending result.
   The cell work receipt is durable before COLD or HOT may advance its cursor.
9. `FrontierV3ResourceSiteExecutor` and its saved physical ledger describe one
   uniform crop stage plus an AIR prefix. They compare or project the entire
   field and treat a single missing crop as whole-field conflict. Changing only
   the domain cycle would make canonical and physical facts disagree. Replace
   this projection/receipt protocol with exact cell outcomes; do not patch
   selected AIR comparisons while leaving restart branches on the old model.
10. `ResourceSiteHarvestSceneExecutor.observePreparedCrop` now proves and writes
    age-zero wheat for the full-yield path, but cannot yet repair/skip a damaged
    cell. HOT and COLD must commit the same typed work outcome; neither may infer
    yield from the cursor count. The progress event now carries exact revision,
    CellId and outcome; the remaining gap is honest physical outcome observation
    and executor support for all five declared results.
11. `ResourceSiteState.validate` currently compares every recovered layout to
    immutable bootstrap geometry, so revision 2 cannot be admitted even though
    `ResourceFieldLayout.revise` exists. A real layout-change transition must
    retain the accepted revision/retirement chain and become the sole geometry
    authority before field expansion is claimed.
12. Historically, bread production selected one wheat lot of at least 64 units
    and retained only that lot ID. The active production path now calls
    `FungibleResourceCustodySupport.selectAtContainer` and pins an exact
    multi-lot allocation in one claim, job and input hold. Thus two co-located
    32-unit lots can supply one 64-unit recipe; a lone 63-unit harvest correctly
    waits for more stock and is not evidence of a stuck production selector.
    Do not reintroduce a single-lot requirement or silently merge lots with
    different provenance or physical bindings.
    `ExactInventory.validateFungibleResourceCustody` already enforces one
    account per resource location and matching depot/lot economic owner, so
    foreign-owner selection is not an admissible-state defect. A rejected
    mixed-owner fixture confirmed this invariant; no redundant owner filter
    should be added to the production planner. The completed source cutover crosses
    `ProductionJob`/`ProductionInputHold`, its snapshot and `ProductionStarted`
    WAL codecs, `FungibleProductionLayout`, the intent role/observation body,
    output lineage and release/restart validation. `ClaimAllocation` originally
    reserved owner/kind quantity rather than exact input lots. The current SA09
    WIP pins a bounded lot map in the claim, job, hold, observation, terminal
    receipt and version-184 snapshot/WAL; two 32-unit lots now pass COLD/HOT
    admission and a focused HOT snapshot-to-confirmed-receipt lifecycle. The
    physical stack allocator places pinned claims against matching lot
    portions. This is a source-level checkpoint, not native/player acceptance
    or the field's variable-yield cutover. Multi-claim physical theft and full
    recovery/product composition remain open; current schema 187 must not be deployed
    against the live schema-180 world.
13. The replacement `FrontierV3ResourceFieldWitness` is presently a pure
    SavedData value indexed by 256-ID buckets, not the active runtime owner.
    Its `cellsIn(layout, chunk)` view first proves the recovered witness matches
    the canonical layout and then reads only that layout's naturally loaded
    chunk cells. Persisting a second chunk partition in SavedData would create
    redundant geometry authority; the exact layout fingerprint and recovered
    CellId fingerprint are the binding. The runtime cutover must actually use
    this bounded view for observation/recovery rather than scanning or force-
    loading the whole field per tick. The pure API alone does not close this.

## Implementation checkpoint, 2026-09-23

The current source now carries `ResourceFieldLayout`: stable cell IDs, revision,
explicit crop/soil/workstation/irrigation geometry, bounded allocation, immutable
cell and chunk indexes, and revision validation that rejects ID reuse. The
existing 8x8 producer declares its geometry explicitly. Cell/claim lookup uses
the index and the loaded check inspects distinct natural chunk columns.
`ResourceFieldCycle` now models exact per-cell soil, crop stage, loss, tillage,
planting and harvest outcomes. Lost crops retain farmer work; the cell visit
replants them without yield. Epoch advance itself never plants, and layout
revision requires explicit retirement of removed cell IDs. Partial, unverified
WIP now puts that cycle in `ResourceSiteState`, preparation/growth reducers and
snapshot schema 183. Harvest progress now updates each exact cell and the
full-yield terminal reducers advance the cycle epoch, rejecting a partial yield
instead of fabricating 64 wheat. Output identity/receipt and physical execution
remain fixed64. The full-yield HOT/COLD adapter now writes and checks age-zero
wheat for worked cells, matching the canonical successor planting. SavedData
format7 rejects the old AIR-prefix meaning. Its uniform ledger still cannot
represent heterogeneous per-cell damage. This WIP is not a runnable candidate;
do not launch a client or deploy until local observations, repair, output and
restart paths close.
`ResourceFieldPhysicalSurface` is now an immutable, chunk-indexed pure value for
last-known PM-owned physical cell conditions, without work/yield counters. It
rejects unknown/foreign conditions rather than inventing their block identity. It
is tested but not yet retained by SavedData or used by the runtime projector.
The canonical site register now rejects disagreement between the farmer's
completed cursor and accounted cell count; those counts are cached in the cycle
to keep this check bounded during unrelated updates.
Source review also found that a foreign crop-space block was representable only
when it replaced a live crop. A missing crop or plain dirt can likewise have an
independent obstruction. The pure cycle now retains that obstruction and, after
its removal, restores the known soil condition without inventing crop growth or
yield. The focused cycle and snapshot-codec suites pass, including recovery of
blocked bare dirt; physical observation/admission is still unwired. The
`ResourceSiteHarvestProgressed` event now declares layout revision, CellId and
typed work outcome; its reducer rejects a stale/mismatched cell or an outcome
inconsistent with the retained canonical cell. COLD selects the exact next
cell/outcome and the existing HOT executor declares its observed full-yield
harvest. The payload codec uses explicit stable outcome tags and focused
process/codec/traversal tests pass. This is not a dynamic-layout or damaged-field
acceptance: the retained job/traversal and physical ledger still depend on the
original fixed layout, and the HOT executor cannot yet perform repair/skip.
The canonical resource-site process now also admits a typed
`ResourceFieldCellObserved` event for a proved crop-to-air or farmland-to-dirt
postcondition. It binds site, layout revision, CellId, exact owned before/after,
source and causation; the reducer rejects a stale predecessor, changed layout,
unprepared/conflicted site or overlap with the pending farmer action. One cell
changes without retiring its neighbors, and snapshot recovery retains the loss.
Focused reducer/codec/closed-owner tests pass. The loaded-world adapter does not
yet produce this event: it still treats the left-click admission as whole-site
conflict. Therefore this increment is not proof that cancelled clicks are safe
or that player damage is locally handled in the running game. The next cutover
must durably admit the pending physical action, observe its actual postcondition,
then submit this event or retain a local ambiguity; it may not fabricate an AIR
observation from a click packet.
The pure resource-site owner now has a durable, cell-specific
`ResourceFieldPlayerBreakPrepared` permission with exact player/action identity
and owned predecessor. It is serialized in snapshot schema 183 and closed only
by the matching `ResourceFieldCellObserved` postcondition, including an explicit
UNCHANGED result for cancelled/no-op removal. After restart, the COLD farmer
holds its retained action at the next cell while that cell's player break is
unresolved, then resumes when the exact observation closes it. Focused
prepare/codec/restart/hold/resume tests pass. This does not yet authorize a
physical break: no native removal-boundary hook emits the permission or
postcondition, and the old stage/prefix SavedData would still disagree with a
locally damaged field. A recovered pending permission needs bounded physical
reconciliation before work at that cell can resume; indefinitely holding it is
not a completed recovery policy.
The retained harvest-progress value now carries the admitted field-cell count;
completion and the work-station cursor use that count rather than the global
64-cell constant. Snapshot/WAL fields for the total, completed/pending index,
cell-preparation/progress event and traversal cursor have versioned, non-byte-
truncating encodings (world schema 183). Tests cover 1/65 cells, index 257,
round trips and rejection of a job cursor sized for another layout. The old
8x8 traversal/admission and fixed 64-wheat terminal remain explicit blockers:
this cursor change does not make a 65-cell harvest executable or allow a partial
yield to be represented. The fixed terminal fails closed for other sizes until
the variable-output and physical-custody cutover is complete.
The implicit 64-cell progress constructor was removed: all producers and fixtures
must now declare the admitted total. Snapshot hydration also rejects one pending
player-break action ID appearing on two cells; a single physical action cannot
be replayed as two permissions. Focused regression and pilot/test compilation pass.
The pure `ResourceFieldCellTransition` now derives one exact block-step sequence
from an admitted owned predecessor and target cell, bound to layout revision and
CellId. Tilling/planting has a farmland-only committed prefix before crop
placement; soil loss clears the crop before changing support. A malformed,
repeated-block or out-of-range prefix fails closed. `ResourceFieldCycle` exposes
that same physical delta for harvest/replant, plant and till/plant work; local
skip has no write. Focused 1/2-step and work-outcome tests pass. This value is
not yet a SavedData cursor or a native writer: the active stage/prefix ledger
still owns Minecraft blocks. The existing work-receipt codec negative was also
corrected to corrupt the actual outcome tag after its index widened from byte
to int; it now asserts the intended decoder error rather than incidental count
corruption.
An immutable `FrontierV3ResourceFieldWitness` value now retains a versioned,
layout-revision/CellId-bound exact committed condition for each claimed cell,
an optional precommitted one/two-block effect cursor, and exact raw block-state
NBT plus causation for a foreign incident. It uses bounded local 256-ID groups
for cell updates; NBT reload rejects duplicate IDs, invalid effect prefixes,
missing or wrongly typed cursor/incident tags, and incompatible format. Focused
NeoForge JUnit coverage passed, including interruption between till and plant
and restart of that prefix. **It is not embedded in the active SavedData and
does not authorize any live block write.** The format-7 resource-site ledger
and the fixed stage/prefix projector remain sole runtime owners. Activation
requires one coherent replacement of their claim, writer, read-only matcher,
restart paths and output receipt; attaching this value as an independent ledger
would create the dual physical authority explicitly forbidden above.
Further source review caught a distinct harvest/replant interruption: actual
farmer work removes a ripe crop before placing age-zero wheat, whereas a direct
owned-state projection may replace it in one step. The physical work transition
now declares `MATURE -> ABSENT -> GROWING` as two crop writes with an exact
recoverable AIR prefix. Its persisted mode and step count distinguish this
from a direct single-write projection and reject accidental reinterpretation on
reload. The pending effect also requires an exact nonblank causation ID across
every cursor advance and recovery; a cursor without attribution is rejected.
Focused model/witness tests and production/test compilation pass. The
live HOT worker is not yet using that program, so this is a source-contract fix,
not proof of repaired gameplay.
The next read-only NeoForge seam, `FrontierV3ResourceFieldObservation`, now
inspects only one naturally loaded crop/support pair. It classifies exact owned
farmland/dirt and wheat/air conditions, retains raw NBT and causation for a
foreign block, and distinguishes a current committed cell from one applied but
not yet durably confirmed next step. An unloaded pair stays `UNLOADED`; the
reader neither tickets chunks nor confirms a cursor. A focused native
`field-turns` GameTest run passed 4/4, including the new loaded/foreign/unloaded
observation. Two older adapter fixtures in that slice initially failed because
they reused a bootstrap epoch-0 cycle for a synthetic epoch-3 lifecycle and
expected AIR where the accepted physical successor is age-zero wheat. Their
fixtures were aligned with the canonical bootstrap layout/epoch and the actual
terminal seedling surface; the single subsequent run passed. This native proof
is limited to the read-only classifier and existing isolated stage-prefix
adapter tests. It is not player-break admission, a SavedData cutover, a
multi-cycle product result, or permission to run the client yet.
Source review then found that revision/CellId/count alone could accept the
geometry of a different field with the same IDs, and comparing all cells on
every observation would be too expensive. `ResourceFieldLayout` now computes a
versioned SHA-256 fingerprint once over its ordered cell IDs, crop/support/work
coordinates, allocator and irrigation geometry. The physical witness persists
that fingerprint; one-cell comparisons use constant-time layout identity, while
different same-revision geometry/work order fails closed. A revision that
explicitly retires every cell remains a valid zero-cell physical claim instead
of becoming an unrecoverable decoder error. Focused layout/witness tests pass.
This secures the value/inspection seam, not its still-missing live SavedData
adoption or arbitrary-field execution.
The witness cursor now accepts only a review tied to its exact layout,
CellId and prior cell claim. A pure comparison of caller-supplied values is
non-confirming; the confirmation-capable review reads a naturally loaded
world cell itself. Native `field-turns` passed 5/5 on 2026-09-24, including
actual dirt-to-farmland and farmland-to-seedling writes, persistence between
steps and rejection of a stale review. This protects the eventual physical
writer API but does not activate the new claim in format-7 SavedData or make
the running field lifecycle correct. The future adapter must read and confirm
on the server thread at the same bounded physical step; observation alone is
not a transaction against later third-party block changes.
Source-first follow-through: a crash after both writes of a two-step field
transition can leave the persisted cursor at step zero while the loaded cell
already matches step two. The read-only matcher now distinguishes that exact
later prefix from unrelated owned drift; confirmation still requires a real
loaded-world review of the exact predecessor claim. The focused witness JUnit
passes, and a new native GameTest case compiles but has not yet been run.
This is a recoverable cursor comparison, not proof that the chunk and SavedData
were durably saved in an atomic order.
The replacement witness now also has a version-4 optional paired harvest
intent: the same cell pending value retains exact job/actor/body/authority epoch
and offhand wheat count before/after. Starting that intent requires real
pre-effect reviews of both the loaded ripe cell and the current owned HOT
worker's offhand; a caller-created classification cannot start it. Confirming
the crop writes leaves the intent pending until a real same-owner/same-epoch
offhand after-stack is observed. That observation now seals a persisted
`handConfirmed` stage rather than clearing the intent: physical completion
precedes the canonical cell/lot receipt, and the retained cause must survive a
crash in that seam. Only an exact canonical receipt may eventually retire it;
the replacement value has no such retirement API until the owning bridge is
defined. The focused JUnit verifies stage invariants and NBT recovery and
rejects synthetic/corrupt evidence; production/test sources compile. There is
no live caller/writer, native positive hand receipt or cross-save crash proof.
Source review then found that revision, geometry and cell identity still did
not nominally name the owning site. Version 4 declares the site in the witness
and hand effect, persists both with exact equality on reload, and requires the
site at chunk-view and live observation entry. A matching-looking field cannot
borrow another site's physical review; missing/foreign site regressions pass.
This is the inactive value boundary, not the SavedData/runtime owner cutover.
The same owner omission existed in the active canonical `ResourceFieldCycle`:
the enclosing `ResourceSiteState` map key was checked against lifecycle identity
but not against a declaration inside the cycle. The cycle now retains `siteId`
through every cell/epoch/revision transition; its snapshot fragment writes the
independent site declaration under world schema 189. State admission and recovery
reject a foreign cycle even with identical geometry and cell IDs. The yield
derivation requires that same site. Focused model, codec, world-schema and
NeoForge pilot compilation pass. This is a canonical ownership correction,
not the physical SavedData/streaming-output adoption.
The active format-7 ledger remains the only runtime physical authority, and
no canonical wheat is credited by this witness alone.
Source inspection of the bundled NeoForge 21.1.248
`ServerPlayerGameMode.destroyBlock` confirms a narrower physical boundary:
`CommonHooks.fireBlockBreak` and game-mode restrictions run first; only then is
private `removeBlock(BlockPos, BlockState, boolean)` called immediately before
`BlockState.onDestroyedByPlayer`. Its return follows the attempted mutation.
The public `destroyBlock` method can return `true` even when private
`removeBlock` returned `false`, so a boolean success flag alone cannot prove
crop removal. A future native adapter should fence at the private removal HEAD,
read the actual post-block state at RETURN, and reconcile unchanged/foreign
outcomes. This is a source finding, not a verified injection or permission to
skip a durable pre-effect boundary/restart disposition.

This is the geometry foundation only. The current harvest executor still owns a
64-cell linear corridor and exactly one 64-wheat output. It now rejects layouts
outside that declared capability before admitting a job; generic geometry must
not be mistaken for generic execution. Player crop removal still takes the
whole field to conflict, and direct soil damage is not yet a local repairable
cell state. Do not offer this checkpoint as the requested manual acceptance build.

## Coherent implementation order

1. Replace the fixed model with explicit layout/cell identity, per-cell state and
   typed cycle outcomes; align persisted codecs and version handling deliberately.
2. Make that state the only canonical field authority. Migrate bootstrap,
   snapshot/WAL codec, recovery and the site resolver together; do not leave
   bootstrap geometry and a mutable layout as competing sources of truth.
3. Bind ordinary player/world observations to those transitions after their
   actual physical postconditions, preserving cancelled events, pending effects
   and exact economic accounting.
4. Replace implicit regrowth with retained harvest/till/plant cell duty; adapt shared
   projection and HOT/COLD harvest to the same per-cell work plan. A missing or
   obstructed cell has a local result, while other cells continue.
   When COLD advances an unloaded field to a later epoch, a physical witness
   with no pending effect may adopt that epoch while preserving its old block
   conditions for later projection. A witness with an old-epoch pending effect
   must instead read/settle that exact predecessor effect and its canonical
   receipt before rebasing; epoch relabelling may never erase the cause.
5. Replace the one terminal exact item with bounded fungible batch custody:
   credit each observed yielding cell into the current actor-held part,
   seal at 64 actual units or at the accounted terminal partial, route the
   same worker to an owned depot for an exact physical handoff, and wait with
   one retained batch when storage is full. Keep per-part WAL/SavedData
   before/after receipts, anti-replay IDs and zero-yield terminal lineage;
   never require the whole potential field yield to fit one chest.
6. Focused checks: irregular/holed/non-flat layouts, 1/63/64/65+ cells, grow/shrink
   mid-work, player crop removal before/after preparation, blocked cell, partial
   and zero yield, 1,729+ yielding cells against a 27-slot depot, actor carry,
   occupied depot/backpressure, return route, restart/replay and no duplicate
   per-cell or per-batch credit.
7. Before client verification, statically inspect the complete changed path:
   source producers/reducers, cell and job identity, persistence/recovery,
   HOT/COLD projection, player observations, physical receipts and output custody.
   Resolve code-visible contradictions with focused checks; then main personally
   runs a bounded client scenario for the remaining player-visible questions.
   No automatic long native campaign or repeated confidence runs. Do not call a
   standalone soil guard the completed field fix.

## Current code increment (not this architecture's completion)

Source-first follow-through, 2026-09-24: the replacement physical witness
previously accepted the retained layout revision/fingerprint and cell *count*
without checking that its recovered cell IDs were exactly the layout's IDs. A
same-count corrupt SavedData entry could therefore pass `matchesLayout` and
fail only later at an individual cell lookup. The layout and witness now cache
the same sorted-ID SHA-256 fingerprint and compare it at the layout boundary;
individual witness updates retain that identity. A forged same-count cell ID
is rejected by the focused witness regression. The targeted layout/witness
Gradle tests pass (18s). This hardens the not-yet-live replacement value; it
does not wire it into SavedData, solve partial yield, or authorize a client run.

The model now has `ResourceFieldYield.fromCompletedCycle` as the intended
quantity/identity boundary for the later custody cutover. It accepts only an
entirely accounted cell cycle with no pending player break, counts only
`yielded` outcomes, emits no lot at zero, and deterministically packs positive
yield into distinct wheat lots of at most 64 units. Lot identity is fixed by
site/epoch/part; layout is retained in provenance and quantity in the value.
That stable ID makes competing positive outcomes of one
epoch collide instead of being credited as different resources. Focused zero, 1, 63,
64 and 129-unit cases pass, including a lost-crop case and incomplete-action
rejection (7s). The active harvest reducer now derives this value before its
legacy full64 gate, so one producer validates the quantity it then credits;
focused reducer/yield tests pass (15s). It is not yet connected to the
SavedData receipt, fungible custody or physical slots. The current exact
64-item path remains the only runtime path until that versioned owner cutover;
these tests do not prove partial harvest works in-game.
The value now also derives `nextReadyLot` from an exact accounted work prefix
and previously issued full-batch quantity. A full lot becomes ready only on
the 64th actual yield; a smaller lot requires the final accounted cycle.
`currentCarriedLot` derives the same part after each yielding cell, so a
one-unit loss produces no cargo and each later unit grows the exact part while
its site/epoch/part identity remains stable. The fungible ledger now has a
COLD actor-part accrual transition: first yield opens one actor account,
subsequent yields require exactly +1 quantity with unchanged lot metadata,
and replay, skipped units, foreign actor, second live part or bound physical
stack fail closed. Focused yield/ledger regressions pass. This is not wired to
the harvest reducer yet; HOT physical hand/equipment observation, deposit
route, durable part progress and batch receipt remain necessary before the
current fixed-output path can be retired.
The HOT hand now has an explicit nominal `ActorHand(actorId, entityId)` stack
address. The fungible ledger requires its account's exact actor; world-state
validation requires the entity UUID deterministically bound to that actor and
world. Snapshot and WAL payload codecs use the new non-reused address tag 4;
snapshot schema 187 and WAL envelope 72 reject their immediate predecessors.
An observed HOT accrual can add exactly one wheat to the same address/authority
epoch, while replay, forged body, changed epoch or non-actor address reject.
Focused actor-hand, codec, envelope and ledger tests pass. The Minecraft
adapter now has a read-only, naturally loaded offhand observer: it requires
the current canonical HOT lease, active exact harvest job/worker, scene-body
provenance and live carrier authority before returning an exact wheat stack.
Unavailable body, empty hand, foreign owner and foreign item remain distinct.
The address reserves offhand because Frontier exact tools, weapons and service
materials use main hand. This observer does not write the mob's hand, submit
an accrual receipt or authorize release; neither it nor the codec is yet
player-visible harvest custody.

Static producer/reducer trace for the required cutover: HOT mutates the crop
block in `observePreparedCrop` and submits `ResourceSiteHarvestProgressed`
only after that postcondition; COLD proposes the same progress directly in
`coldCropReceipt`. Their shared `reduceProgressed` works the cell but credits
no carried wheat until the fixed-64 terminal exact-item branch. The first
durable owner change must therefore be a per-cell, outcome-derived receipt
that atomically advances the cell and exactly one actor-held lot unit only
when the outcome yielded. HOT additionally needs a recoverable paired
crop+offhand physical effect with a saved before/after witness: a block-only
postcondition cannot prove the extra wheat, and a hand-only postcondition
cannot prove the cell. Crash states with neither, one or both physical halves
must reconcile idempotently before the canonical receipt. COLD accrues the
same deterministic part without claiming a physical hand. At a full 64-unit
part (or final partial), the job must retain depot delivery with capacity
backpressure and a physical hand-to-chest receipt before the next part; zero
yield needs an explicit terminal replay fence. HOT/COLD handoff, body death,
equipment interaction and scene release must preserve or retire the same
actor-held account without an owner-inference shortcut. The current path
satisfies none of these completion conditions; do not use a client run to
claim arbitrary-field harvest before this owner cutover is coherent.
Generic fungible layout, binding release and handoff commands now reject actor
custody, since they have no proof of the farmer's crop effect, body or delivery
route. The exact resource-site owner must later admit, observe and retire that
hand through its typed receipt rather than opening a generic bypass.
The ordinary scene-release planner and reducer also refuse to close any scene
member while a fungible actor-hand binding still names that actor. The hand
must first be transferred or unbound by its typed owner; otherwise the shared
release could discard the Villager body and strand a nominally HOT stack.
Focused release-path regression covers both pre-journal rejection and reducer
replay rejection. This is a fail-closed safety fence, not a completed transfer.
Conversely, full canonical-state validation now rejects an actor-hand binding
with the right deterministic UUID but no admitted (not PREPARED/CLOSED) scene or ambient physical
owner. A test uses an actual HOT harvest lease for the positive snapshot case
and rejects both a merely PREPARED permission and the previous lease-free
phantom-binding fixture. This is a
nominal owner proof, not evidence that Minecraft currently holds the item;
only the typed native adapter can establish that postcondition.
Lot identity/provenance is site/epoch/layout/part-local, independent of both
the current part quantity and eventual total, so one carried lot can grow
without changing its source identity and a lot sealed early equals its later
terminal review. The lot value still carries exact quantity and conflicting
outcomes under the same ID do not compare equal.
Focused 130-cell (64+64+2), lost-crop-at-cell-one (full lot at cell65), stale
prefix, skipped batch and unresolved-action checks pass. This proves a pure
batch derivation, not actor custody, per-cell quantity growth, depot delivery,
the physical receipt or backpressure; those are the next coherent cutover.
The first batch API re-read every accounted cell on every readiness check.
The current area-work cutover uses the exact accounted count and per-cell yield
facts, not a contiguous prefix, for batch readiness. It checks only pending
player actions on already-accounted cells; an unresolved future cell cannot
erase an earned full batch. The old cached prefix remains a legacy diagnostic
but is not output authority. Focused layout/cycle/yield tests cover out-of-order
work, recovery and an unresolved player action without rescanning every field
cell for each batch.
Custody tracing also found that a HOT stack address could name a different
player or container than its canonical account. The fungible ledger now checks
their exact owner identity (and the world-carrier address category) at binding
construction/recovery; focused forged-owner regressions pass. The future
farmer-held wheat needs an explicit actor stack address and actual observable
body/equipment ownership, not a `ResourceCustody.Actor` account with a foreign
player/container address. No actor-held bridge is live yet.
Zero yield has no lot-ID collision fence; the terminal job/lineage transition
must retain its zero outcome and reject a second result on replay.

The source trace also found a current runtime admission defect relevant to
field output: `ExactInventory.firstFreeSlot` and
`FrontierWorldState.containerSlotAvailable` treated a HOT fungible stack
binding as a free exact-item slot. A harvest could select that slot, then fail
only when the inventory constructor detected the overlap. Both callers now
share `ExactInventory.slotVacant`, which rejects occupied exact or fungible
binding slots; the existing production-hold reservation remains an additional
world-state fence. Focused inventory and harvest-process tests pass (19s).
This repairs slot selection for current exact outputs, but it does not supply
the multi-stack capacity/receipt protocol needed for variable field yield.

Further source review found that unbound COLD fungible stock was also invisible
to exact-slot admission. A depot with 64 canonical wheat could fill every
physical slot with exact stacks, leaving no possible chest image. The shared
`ExactInventory.availableSlots` now reserves a minimum stack budget for that
stock and current HOT bindings; `firstFreeSlot`, world-state selection and
`store` use the same result. The reservation is a capacity count, not a fixed
slot address: any vacant slot may take an exact item while enough total space
remains for COLD stock. This is an optimistic **64-unit packing** rule, not a
general item-catalog stack-size proof. The NeoForge
writer separately builds the *entire* physical chest image against actual
item max-stack sizes for both exact and fungible stacks before mutating any
slot. Retained conflict diagnostics do not count as current slot occupancy;
only current exact items and HOT bindings do. Impossible old overcommitted
states therefore fail without partly writing or clearing an owned chest.
Focused inventory/harvest/production tests and NeoForge compilation pass;
the reference-projection native slice passes 4/4, including old-state
overcommit, unchanged pre-existing chest on failed replacement and successful
write once capacity is restored. Neither this nor the full64 reducer proves
zero/partial/multi-stack field yield, restart receipts or player acceptance.

All canonical inventory ingress methods that can newly occupy container space
now compare the post-transition minimum footprint to the prior one. This
includes observed exact transfers, exact/fungible cargo arrival and direct
resource-ledger changes. Old overcommitted snapshots are still readable and
may be reduced; they cannot worsen through those methods. The check remains
an optimistic 64-unit packing bound. The current fixed-output harvest now
reserves its one declared exact depot slot while work is active, including
full-state capacity validation and first-visibility chest packing. That does
not reserve capacity for multiple future field batches, handle a non-64 stack
limit, or provide the typed output/capacity receipt required by the arbitrary-
field cutover; none is a claim of current field completion.

The replacement witness can derive a chunk-local view from the one canonical
layout after proving revision, geometry and recovered CellIds match. No second
SavedData chunk partition is needed or wanted. Its focused JUnit test covers
recovered, irregular cross-chunk cells including negative coordinates. The
active format-7 site ledger does not yet use this view, so live observation and
restart remain fixed-stage/prefix until the owner cutover.

The static path still has one important ordering constraint: the domain
`ResourceFieldCycle` can record a missing/damaged cell and a non-yielding visit,
but `ResourceSiteHarvestProcess` admits a COLD terminal only for the fixed
64-cell producer and `reduceProgressed` demands every cell yielded before it
creates one exact 64-wheat stack. The format-7 physical site ledger separately
records a uniform stage plus harvested prefix, and player break admission still
records a whole-site conflict at break *start*. Thus relaxing the reducer's
yield check or teaching the adapter to tolerate AIR would create competing
canonical/physical histories. The next implementation must cut over the
SavedData claim, postcondition observer, work receipt and output custody as a
single versioned owner path; use focused tests on that path before a client.

Native `FarmBlock.turnToDirt` is now cancelled only for physically present farmland
inside an ACTIVE exact managed soil claim on a non-destroyed field. Ordinary
external farms, unowned cells and direct player block edits are not protected by
this hook. Attribution remains for unprotected mutations. This prevents vanilla
trampling/drying retirement; it does not repair already-damaged soil or implement
partial-cell recovery.

11251 terminal0/10s: production/pilot compile, soil-policy and receipt-lifecycle
unit tests, local JAR. `build/sa-managed-soil-checkpoint-20260923.log`.
No native run, deployment, schema change, commit or world mutation in this increment.

### Physically complete non-harvest work still needs a canonical receipt — 2026-09-24

Source review found a lost-cause window in the replacement physical witness:
`confirm` previously cleared a planting/tillage pending effect as soon as the
last block write was observed. A crash before the corresponding canonical work
receipt could then recover the changed blocks without the causal transition.
Witness format 5 retains the completed direct effect and its exact cause across
NBT recovery, just as format 4 already retained a confirmed crop+hand pair.
Format 4 is rejected because its completed direct effects may have already
forgotten that cause. The focused witness JUnit passes; the native `field-turns`
slice passes all six real-block tests after correcting one unrelated pilot
fixture's invalid `container:1` to the site's declared depot. This is a
precondition for the future atomic receipt/acknowledgement boundary, **not**
that boundary itself: no canonical acknowledgement API or active SavedData
cutover exists yet, and the current live server remains on the old projection.

### Typed actor-to-depot part transfer — 2026-09-24 (model boundary, not runtime adoption)

The fungible ledger now has two field-specific transfers derived from the
accounted cell prefix and already-issued quantity: a ready COLD actor-held
part may enter its settlement depot account, and a HOT part may leave only
its exact bound farmer hand/UUID/authority epoch while an entire replacement
depot binding layout is installed in the same transaction. Zero/unfinished
parts, a foreign actor, replay, wrong body, missing chest layout and a
different depot owner reject. Existing COLD depot reservations and existing
HOT depot stacks remain intact. Generic cold/observed resource transfers and
cargo-delivery methods now refuse actor custody, so a caller cannot route
farmer-held wheat through an unrelated handoff owner. Focused yield/ledger,
inventory, physical-observation and cargo-release suites pass.

These are **resource-ledger transitions only**. The active harvest reducer
still creates a fixed exact 64-wheat depot stack; no job has yet retained a
delivery route, no loaded adapter has proven an actual hand-to-chest movement,
and no capacity/backpressure or SavedData/witness cutover is active. Calling
these methods without the future job/route/physical postcondition would not
establish player-visible field delivery. SA-09 remains open.

### Field physical transition retains site and cycle — 2026-09-24 (replacement-path correction)

Static review found that a cell transition previously carried only a layout
revision and CellId. A different field with identical geometry, or the same
field in a later growth epoch, could give those values the same meaning.
`ResourceFieldCellTransition` now requires the nominal site and epoch; the
canonical cycle stamps both. The replacement physical witness retains the
epoch in format 6 and rejects format 5/missing epoch. It refuses a foreign-site
or stale-epoch transition. Confirmation-capable loaded-cell observation and
chunk-cell selection now require the current canonical `ResourceFieldCycle`,
not a geometry-only tuple; reviews retain epoch and cannot be reused across
cycles. Model/witness regressions and all six focused native field-turn tests
pass, including loaded-world refusal of an old epoch on the same blocks.

This is a prerequisite, **not active SavedData adoption**. The running field
executor still uses its uniform-stage/harvest-prefix format-7 ledger and exact
64-wheat terminal. It has no canonical acknowledgement or actor-hand/depot
delivery using this witness; SA-09 and product acceptance remain open.

### Active field readers use the canonical layout descriptor — 2026-09-24 (owner migration)

`ResourceSiteState.descriptor` now combines stable bootstrap site/farm/settlement
identity with the layout retained by the canonical `ResourceFieldCycle`.
`FrontierWorldState.resourceSite`/`resourceSiteDescriptors` expose that single
read boundary. Harvest/preparation planners and reducers, the active NeoForge
field projector, harvest effect and scene executors, explosion/soil observers,
visibility/boards and operator diagnostics now use it rather than independently
recompiling bootstrap geometry. Fresh-world scheduling and identity-only
validation still use the bootstrap plan deliberately. A model test proves a
revision-2 cycle descriptor uses its retained layout while bootstrap remains
revision 1. Focused resource-site model/NeoForge unit selections and the
three native `resource-prefix` tests pass; the native server saved/stopped.

This moves **active readers**, not writes or admission. `ResourceSiteState.validate`
still rejects any layout differing from bootstrap; no revision transition is
admitted until the physical SavedData/projection and job/output owners migrate.
The current 8×8 field and exact64 output are unchanged. Do not infer arbitrary
geometry or player-visible harvest support from this resolver step.
