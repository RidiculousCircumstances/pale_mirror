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

The exact nameless donkey observation on d496aadf9 completed (597 poses,
14.79 blocks). It retained a 45-tick stationary interval despite an active
native path and no coincident host spike. The dependency source supplies a
concrete competing actuator: Minecraft 1.21.1 AbstractHorse.aiStep starts grass
eating outside serverAiStep/NoAI, and isImmobile blocks travel until its
fifty-tick counter ends. The shared native movement integration now suppresses
autonomous grazing only while a current goal-navigation permission controls
that horse, and clears already-started grazing before ordinary travel. Releasing
that permission restores native grazing. The native negative check exercises
both boundaries with a real donkey. No caravan-specific movement driver or
global animal-AI change is added. Width-derived native path points already
matched Path.getEntityPosAtNode; lateral node offsets alone were not a defect.

A separate two-minute exploratory HOT sample of resident6-13 distinguishes
selection, body admission and work. The resident already had FIELD_HARVEST work,
then spent about10.2s with a durable insertion ticket and16.0s indexed before
motion. This is not an unselected activity. Source review found ambient HOT
confirmation checked publication immediately after submit, but did not arm its
actuator when the asynchronous receipt later published. It now uses the
existing exact command continuation, revalidating the captured lease revision
and body incarnation. Handoff-only confirmation still never arms an ambient
actuator. This removes one missed wake, not every admission/scene delay.
The same sample includes stationary HARVESTING with a real200000 milli-work
obligation; actual labour must not be reclassified as idle waiting.

The existing wide-transit native test originally enclosed all three advisory
targets against its east/south walls. Width-correct collision validation thus
selected the legal final-goal fallback instead of transit. Its own bounded
fixture now leaves those target footprints clear; no production collision rule
or terminal-arrival assertion is weakened. Scoped native local-navigation45
tests pass, including real grazing/release. Runtime durability/recovery checks,
guardrails and package verification are retained in
build/r274-runtime-repair/horse-admission-final-check.log.

Live73acaa598 exploration passed12actions on the retained world. The exact
pack4 donkey yielded566poses/16.79blocks, with an initial41-tick admission hold
and no subsequent stationary interval of three ticks or more. This is not a
matched speed comparison. A complete native tick still reached83.26ms; its
object-board stage spent25.6ms. Board publication now checks the existing soft
physical allowance between boards, retaining its cursor and finishing each
individual update. The count cap alone did not bound a whole eight-board batch.

The earlier indexed-body delay also exposed the preceding common boundary:
ActorBodyPresent publication closed the insertion journal but did not notify
the retained actor probe queue. Both insertion confirmation and a returning
saved body's confirmation now notify that existing queue after publication.
The probe rechecks current ownership; a wake neither grants admission nor
replays a physical effect. This complements the exact ambient-arm callback.

Ordinary debt still grew739->1574 during the two-minute city phase. A separate
25s worker JFR attributes its dominant samples to land discovery during goods
planning. A complete geometry revision was placed in ImmutableResultView's
identity-only inputs, although that revision explicitly permits reconstruction
as an equal value after bounded-cache eviction. Land-use exclusions similarly
used derived access-boundary identity, which declaration-cache refresh can
replace without a spatial change. Both caches now compare those complete
derived values in their typed queries; canonical owner inputs retain their
exact identity fences. Reuse still creates a fresh exact-state Survey for
admission. The actual view/scope eviction regression retains unchanged candidate
facts and still rejects changed ground/stale admission; no TTL, smaller search
area, route bypass or relaxed physical check is introduced.

The eviction regression fails with the preceding FieldDevelopmentLand source
and passes with the candidate. Final scoped checks:19 cultivation and18
board/budget/probe/confirmation tests PASS,25 native body-lifetime tests PASS,
guardrails and packaged JAR PASS. Receipts:land-eviction-before-fix.log,
admission-wake-land-check.log,wake-cache-package-final-check.log. These do not
establish a hard80ms maximum or universal next-action latency.

The e09 HOT reentry remained exploratory PASS, but debt630->1395 over2441
native ticks still demonstrated insufficient throughput. Complete native ticks
stayed below62.98ms; a separate ready scene callback reached84.80ms. Neither
of these remaining issues is hidden by the native maximum.

The land query's remaining invalidation was global: any geology/observation
change anywhere replaced its complete world input. Local land selection now
retains the exact regional geometry proof for the bounded discovery footprint
and a shared KnownBlockReadWitness of every suitability block read, including
unknown/protected results. A remote change can reuse only unchanged local
facts; a changed read or local traversal token requires rediscovery. Sample
revision metadata is refreshed from the exact current state before creating
the new Survey, whose old-state admission fence remains mandatory. The read
witness is generic, bounded and has no command/admission authority. Overflow
disables reuse without truncating discovery. The consumer retains at most64
queries and131072 block reads; it holds no old mutable world or host service.
The unused global land-input accessor was removed. Tests cover actual remote
geology changes, local revocation, stale/foreign surveys, scope eviction,
unknown/permission changes and incomplete read witnesses. No time benchmark
or reduced navigation reach is involved.

The next production-path review found why land reuse was still ineffective:
ResourceSiteState.descriptors constructs fresh ResourceSite value records on
every read, while retained land inputs compared those records by identity.
Ordinary economic callers reconstruct that list every review; earlier checks
reused the same local list and missed this path. Retained field declarations
now match by their complete values (identity, ownership, kind and layout).
An additional test uses two real descriptor reads, not reused references.

The native field path had a separate timing dependency: captured activity
permission expired after20 host ticks unless the whole scene executor refreshed
it. It possessed an exact semantic MovementOrder but no continuing goal proof.
The existing closed activity-capability registry now optionally supplies its
own current navigationOrder. The field owner implements that read-only port.
Shared navigation captures that exact order, body incarnation and execution,
and revalidates them at every entity boundary; replacing the cell/storage goal,
interrupting execution or retiring the body revokes it immediately. No larger
timeout or field-state branch is added to the generic navigator. Other owners
without a retained goal keep their bounded local directive. Existing explicit
ActorMovement permissions keep their original owner and validation path.

The following clean HOT window still failed: before profiling, native67.45ms
but a ready callback112.06ms (aggregate scene stage108.78ms), with a farmer
remaining PREPARED. The later271.44ms native maximum includes JFR startup and
must not be presented as a comparable clean measurement. The25s profile shows
land is no longer the dominant preparation stack; goods transport/equipment
valuation and economic change/relationship validation remain significant.

Source review found a shared continuation defect: the outer physical pass
consumed its aggregate scenes stage even when the nested scene-family pass
yielded with an unfinished suffix. Readiness resumed the outer suffix, so later
families had to wait for another complete physical round rather than their
actual dependency. The nested pass and aggregate tick path are removed. Each
closed registered scene behavior now contributes its own resumable stage to
the single physical registry, preserving provider order and each family's
declared scene-write partition. All stages share the original allowance and
one invocation per native turn; ready callbacks cannot replay prior actors or
renew the budget. Composition coverage requires one stage per registered family
and forbids the lost aggregate continuation. Existing physical-pass tests cover
delayed acknowledgement/suffix order/non-replay; native shared storage/lifetime/
field56 and scoped registry/actuation tests pass flat-physical-pass-check.log.


After flattening, the clean three-minute HOT observation on d73092332 recorded
native max67.743ms and ready max44.965ms. Ordinary debt initially109->57, ending
238. This is a bounded improvement, not a hard worst-case guarantee. No new
scene/inventory conflict appeared; the retained75502 incident remains unchanged.
A close exact-body observation of resident:6-13 captured600 native poses at the
same station with field-work animation. Its canonical labour and cell stages
continued; standing during that work is not proof of a next-activity selection
stall. The linked frame and full samples remain in farmer-close-result.json.

Disconnect still exposed a real136.90ms native tick, including83.89ms in worksite
projection. The timestamp-matched JFR samples show ExtractionWorksiteOwner's
beforeTurn repeatedly decoding paged worksite witnesses while capturing several
non-ticking source regions in one invocation. Its ordinary departure review now
uses the existing cooperative suffix cursor and configured read/time admission
between complete source regions. Every region rechecks current state/chunk and
retains all positive observation/durability conditions. No partial scan grants
COLD custody. Native unload still captures its final complete physical image;
shutdown retains its final drain. Changing the declared geometry resets the
scheduling cursor, never an ownership receipt.

The same disconnect profile contains a10.69ms journal join from
abandonPreparedForReservation. That retryable cancellation now uses the existing
exact-actor durability gate. It withholds DRAINING until the current absence
record is durable while unrelated actors proceed. One-shot body release and
unattempted insertion rollback retain their separate mandatory barriers; this
change does not claim that all synchronous recovery/rollback work is eliminated.

The first verification invocation accidentally selected the broad default scene
suite. It exposed route-patrol fixture failures (fixed-tick checks before HOT
admission) and was stopped; it is not a passing receipt. Those inactive patrol
fixtures were not changed or used to excuse active product behavior. The actual
changed boundary uses the existing shared-storage/body-lifetime/field slice and
scoped cancellation, record-durability, suffix and worksite-witness coverage.
