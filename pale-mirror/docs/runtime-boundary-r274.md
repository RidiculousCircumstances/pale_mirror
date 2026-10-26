# R274: environmental ingress and unused equipment departure

The retained R272 world exposed two independent technical failures on R273.
Hearthvale had 29 starving residents, 192 bread (164 free), and one farmer
occupying warehouse access behind a prepared equipment transfer. The worker's
body had already retired to COLD, while the transfer still required its old HOT
incarnation. Separately, loaded-world random grass updates produced repeated
geological invalidation transactions and 90–356 ms native ticks. Simulation
clock debt grew from 45 to 6,803 ticks while Minecraft's sampled TPS stayed near
20; these are different measures.

## Ownership-preserving repair

- Field equipment declares its pending interaction to the existing common body
  fence. Scene release also checks the whole job's physical obligations. A new
  incarnation cannot supersede an unresolved inventory interaction.
- The field adapter processes exact saved body departures in the existing
  OBSERVATION stage, before actor admission. The common Minecraft operation
  owner proves the prepared action was unissued, or positively cancels its unused
  attempt. An attempted or uncertain effect is never inferred to be unapplied
  from inventory appearance. Existing cancellation tickets close through the
  common canonical-confirmation protocol rather than leaking journal records.
- The field owner accepts that typed abort only for its retained preparation,
  exact execution/body/scope (including the exact retired departure incarnation).
  A body owner's durable RESUME_COLD retirement is sufficient positive departure
  evidence for an unissued action after its transient native receipt was pruned;
  it does not require another entity census or a player visit.
  No inventory changes occur. The next preparation has a new storage revision.
- Cross-owner constructor audits are deferred only within unpublished planning /
  reduction scopes. The mandatory pre-WAL publication, direct construction and
  complete bootstrap/recovery checks remain. Terrain-only publication still
  validates its complete changed geometry/exclusions read set.
- Optional native random samples, including all their ingress work, share one
  soft runtime allowance per actual native tick. An admitted sample finishes;
  exhausted samples are not queued or replayed. Explicit player interactions
  retain their existing causal protocol. This is cooperative admission, not a
  hard deadline capable of interrupting one indivisible native action.

## Evidence

`build/r274-runtime-repair/` contains the 45-second same-process JFR, full-depth
CPU extraction, scoped checks and native receipts. JFR identifies
`SpreadingSnowyDirtBlock.randomTick -> ManagedBlockWrites -> geology invalidation`
and repeated unpublished constructor audits. Owner-loop scheduled work is also
substantial; no complete-tick speedup is claimed before comparable live checks.

Focused Frontier/NeoForge checks passed (29 + 43 tests); guardrails, JAR and
packaged verification passed. The final native shared-storage/body/field slice
passed 54 required tests. Its deliberate attempted-effect negative emits an
expected ambiguous-operation warning. An initial incorrect reference-lane
invocation did not run tests and is not counted. A stale coarse-soil assertion
was aligned with the already-adopted separate earthworks predecessor.

Mossbrook's 47-coal shipment was not stalled: sealed snapshots at 171252 and
195852 prove route progress and an ETA near 217056. Payment at source is not
home delivery. Hunger while that cargo is in transit is not repaired by
teleportation or a fictitious receipt. Live recovery, movement and performance
results are recorded in the canonical continuity ledger after deployment.

## HOT continuation and next-action latency follow-up

The first delivery recovered farmer6-7 in the retained world (nourished again),
and Hearthvale hunger fell29->19. A later real HOT/COLD visit exposed a second
missing fence: resident6-29 retained TAKE at depot6/ambient revision4 while its
body had departed. That held the service point and18 other meals. Food's
container fence existed, but its actor fence was absent from the common body
composition. Food now supplies that declaration; a new incarnation cannot be
created behind a pending TAKE or CONSUME.

The shared departed-body evidence port is used by both food and equipment.
Food first continues an already bound native consequence through the common
confirmation protocol; otherwise the common operation owner proves unused
preparation. Only then does a typed food cancellation clear the preparation.
The phase, exact claim, food amount, hunger and resource accounts stay unchanged.
A current or exact retired living incarnation is required; stale body epochs
and repeated cancellation reject. No food is granted from an absent body.

A45-second candidate JFR found416/641 scheduled-preparation samples under
FieldDevelopmentLand reachability selection. The land owner now reuses bounded
immutable candidate results for exactly unchanged input identities, including
geometry, physical deltas, protected land, geology and fields. Every admission
still requires a freshly constructed exact-state Survey; actor motion alone
need not repeat the flood. The scene-family executor now retains its fair
cooperative suffix and measures each family independently instead of treating
seven families as one indivisible stage.

Native shared operation/body/field checks and focused recovery checks cover the
changed boundaries. Exploratory live client visits assess actual continued
feeding and simulation lag; no all-activities smooth-motion or universal80ms
hard-real-time claim follows from an empty conflict list.

The extra food payload changes the closed process descriptor inventory. Retained
R272 therefore correctly fails the current fresh-world-only header guard; it is
preserved offline, not migrated or silently accepted. Deployment uses a new
R274 world. Failed startup cleanup also avoids invoking domain worksite
reconciliation before any canonical state was decoded; the original rejection
remains visible and native stores still finish. Live old-world continuation
claims apply only to343, not the new food candidate.

Follow-up HOT JFR also finds repeated expected-container-layout compilation on
the server thread. The common container owner now keeps a bounded per-thread
image memo for exactly one immutable inventory/catalog input, keyed additionally
by the exact reserved-slot set. Slot checks reuse that derived image; changed
stock, physical bindings, station/catalog or reservations recompute. No native
observation, permission to mutate or recovery receipt is cached as authority.

A second HOT/reentry JFR proves owner-thread journal waits (13–36ms each) in
required operation confirmation, saved-adoption cleanup and post-confirmed mining
witness cleanup. Required confirmations now honor the existing ordinary-turn
async durability port, retaining canonical receipt pins until the closure flush.
Saved native adoption proof and post-canonical SETTLED cleanup submit async
metadata; fresh mutation still requires its exact actor/operation durable prefix.
Mid-effect mining witness persistence remains a separate protected effect window
and is not blindly converted to an unsafe fire-and-forget write.

Final first-HOT capture still exposed100–145ms grass-spread turns: one vanilla
random sample issues several independent neighbour writes. Admission now checks
the shared environmental allowance before each outer native set/destroy, while
letting every nested consequence of an admitted write complete. Destroy admission
precedes drops. Exhausted optional neighbours resample naturally; explicit player
and owned effects outside the random scope retain their ordinary protocol.
Deterministic injected-clock coverage checks nesting, exception cleanup and
explicit-write immunity; it asserts semantics, not wall-clock performance.

Vanilla MC1.21.1 autosave still produced a131ms native tick at host sample6000.
ModLens snapshot ac5901ef confirms MinecraftServer.tickServer calls synchronous
saveEverything(true,false,false); all worlds are visited together and ChunkMap's
non-flush branch serializes every dirty visible candidate. This is a separate
native save boundary, not attributed to the2ms extraction stage that happened
to precede it. Added read-only native-save all/terrain spans to distinguish its
components before changing persistence semantics. JFR proves a10ms level.dat
force inside that tick, not the full131ms cause. ptrace syscall tracing was
unavailable (OS restriction); no safety/force policy was weakened.

Destroy preparation also delegates to the complete registered owner composition
before vanilla emits drops, rather than admitting geometry alone and discovering
an owner refusal only after drops. The nested setBlock retains those same
idempotent witnesses. Native block-extraction/terrain-admission4 tests passed;
the shared storage/body/field54 tests passed for the environmental wrapper.

Source audit of NeoForge DimensionDataStorage.save confirms it calls each
SavedData.save(File), whose ordinary implementation enqueues compressed NBT. Our
common journals override that entry with a synchronous prefix barrier, including
clean pending images. Added an explicit native autosave Target port to both
shared journals and actor rows. Only the automatic tickServer save entry uses
that port; direct persist/effect/explicit save and shutdown paths still force
their exact prefixes. All normal rows use the existing async writer and receipt
gates. No vanilla save-cycle replacement or reduced native durability is added.

## Oversized route reuse found during the sustained run

At canonical75502 the R274 server stopped while selecting the next tree for
work:forestry-1. The receipt factory is entered only for paths longer than one
point, so its bounded-leg failure proves a path exceeded4096 points. The copied
sealed snapshot at75401 produces a68-point direct route from the same job's
warehouse to its next tree. The discarded live planner cache was not preserved;
its exact oversized route is therefore not reconstructed from that snapshot.

A source-level reproducer establishes a concrete shared defect: a cached proven
itinerary can send a nearby query along a multi-thousand-cell detour, bypassing
ordinary regional search. The test fails before this repair. Optional corridor
and macro-chain reuse now have a bounded geometric stretch allowance (four times
the endpoint lower bound, with local cell/region slack). An excessive hint falls
back to the unchanged regional search; it is not a negative connectivity result
or a restriction on actual terrain detours. This applies to all work families.

The geometry-only admission API also reports an explicit SAFETY_LIMIT, including
length and endpoints, if a complete result cannot fit its4096-point receipt.
It never truncates a proof and calls the destination reached. Movement segment
and full-route calculation contracts remain separate. No state/schema change or
world reset is required. This removes the demonstrated failure path, but does
not by itself prove all HOT next-action waits or caravan pauses resolved.

## Native scheduled updates were outside the random-sample budget

The f41c87b0e real HOT sequence records600 poses for the exact caravan carrier
resident:8-12: a38-client-tick initial hold, then37.25blocks over the30-second
window, with only two isolated one-tick holds. Forestry resident:6-13 instead
held558ticks, mostly PREPARED, then moved6blocks. These are observed limits,
not a successful all-activities smoothness claim. Simulation debt grew during
HOT and complete native ticks still reached100–275ms. One natural autosave's
complete save span was46ms; that is not the whole worst-tick explanation.

A focused reentry JFR isolates the remaining native path:
LeavesBlock.tick -> LevelTicks.runCollectedTicks -> geological invalidation ->
FrontierFileStore.append -> flushPendingBlocking. These required scheduled
updates are not random samples. A single tick drained many independent callbacks
and synchronously forced each exact pre-effect fact. The earlier random-sample
repair cannot cover that path.

The shared integration now admits whole scheduled callbacks using the same
native-tick allowance. It never skips a descendant write of an admitted callback.
The bounded unexecuted suffix is returned to vanilla's own persistent per-chunk
tick queues with its exact type, position, deadline, priority and sub-tick order.
If an executed callback scheduled a later duplicate after collection, the prior
due record keeps the turn it would have retained had collection stopped earlier.
A batch starts no collection while the canonical owner is already awaiting its
calculation or commit. Nonphysical dimensions retain the original native path.
No custom durable queue, disabled foliage, dropped scheduled event or removed
pre-effect disk barrier is introduced. Actual native LevelTicks coverage checks
suffix persistence ownership, duplicate ordering and eventual execution.

On b14da2f60 the subsequent20FPS live sequence reached host sample9462 with
maximum complete native tick78.92ms (recent maxima7.6–31.5ms). No >80ms tick was
logged; a natural save span was66.9ms. This is a bounded observation window, not
a hard-real-time guarantee. The scenario's zero-conflict assertion FAILED: the
retained incident is exactly the earlier kernel failure at75502 (first=last),
not a new incident. The retained world and diagnostic history were not erased.
The actual600-pose forestry window covered65.55blocks with a170-tick longest
hold, improved relative to the prior558-tick window but not matched work phases.
Canonical debt still grew1488->3241 across98214->101324; throughput/next-action
latency is therefore not closed by the native peak result.

The worker JFR also attributes substantial navigation sampling to forest overlay
lookups in empty regions. Authored patches now publish a conservative canopy-
expanded region index; snapshots bypass image memo locks/allocation outside both
that coverage and retained captured-tree footprints. Existing or newly grown
external trees and cross-boundary aftermath still use the full overlay. Unknown
base terrain remains unknown. This is a derived membership fast path, not a
change to route connectivity, resource quantities or canonical schema.

A follow-up COLD30s JFR still spends most scheduled-worker samples in land
reachability and comparison of reconstructed passage obstacle sets. The complete
land key now uses the geometry owner's immutable version instead of the disposable
wrapper identity. Its other full suitability inputs and exact-state Survey fence
remain. The obstacle owner memoizes passage overlays over the exact complete and
observed obstacle inputs plus the permission set; unchanged overlays retain their
regional objects. A separate bounded exact-cycle memo avoids repeated field-cell
obstruction scans after unrelated geometry-view eviction. Spatial forest coverage
uses the ordinary hash-backed immutable view, avoiding dense SetN probe runs in
the sampled grid. Scoped cultivation/geometry/forest40tests and architecture9,
guardrails/JAR/package passed planning-images-final-check.log. Live throughput
must still be measured; no decrease in clock debt is inferred across a restart.

The next real HOT sequence completed with the declared retained incident count1,
no new kernel failure. It additionally exposed two ready-callback peaks outside
the complete native tick:123.7ms for scene work and85.6ms for COLD pack movement
planning. These are not covered by a native-tick-only maximum. Source tracing
found movement continuation still registered on the owner thread, although its
inputs are immutable model state. It now uses the existing detached preparation
port, preserving exact-state publication, route readiness and WAL execution.
The existing airborne/recovery movement check exercises the real registered
worker preparation and unchanged typed outcome.

The harvest HOT adapter also acquired a COLD route calculation every time it
built optional native-goal advice. The physical navigator can navigate without
that hint, and no canonical owner retained those native-poll dependencies.
It now consumes retained advice only; absent advice keeps the exact same native
order/stations and starts no unused background search. A negative-boundary check
rejects any query acquisition while building that HOT goal. Remaining scene peaks
need comparable profiling if they recur; this is not proof that a120ms scene span
was entirely caused by that query. Resident6-13 had switched from forestry to
FIELD_HARVEST and later IDLE during this run; the empty forestry job view is not a
stalled forestry job. Future observations must bind the current owner.
