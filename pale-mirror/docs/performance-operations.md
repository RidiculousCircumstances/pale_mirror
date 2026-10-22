# Pale Mirror runtime performance

## Receipt-driven section preparation (R215)

The R214 JFR contains107 distinct worksite-path server parks totalling2257ms
across visits (max41.3ms per recorded park). Duplicate events from an explicit
dump must be deduplicated by type/start/thread. The cause was a synchronous
`BlockJournal.appendAsync` default: each section binding, root declaration,
conservative index update and child image forced separately on the server path.
Those samples do not identify every historical multi-second PM maximum.

BLOCKS now submits one bounded immutable image (16MiB/131072 rows) to its existing
I/O lane. A cooperative continuation publishes indices on the owner thread;
at most eight phase transitions are advanced per poll. New binding precedes root
registration; conservative index additions precede data and removals follow it.
One selected section is pinned, and submitted decoded rows survive cache eviction
until the exact aggregate receipt. Newer edits cannot borrow that receipt.
Failed writes latch storage failure and fail the outstanding receipt.

Ordinary turn exit advances submissions without waiting. Explicit flush, native
save and shutdown drain them. Prepared worksite windows are revalidated after
receipt, before mutation; current preimage and owner/custody remain mandatory.
Custody confirmation and projection withdrawal also wait for durable readiness.
Shared soft preparation admission rotates structures, fields and worksites;
it does not skip recovery, external observations or already-fenced effects.
Point ownership queries use the existing regional worksite view instead of
rebuilding the full declared point index when one mine changes.

Focused failure/cache/recovery checks and native affected checks are recorded in
`build/r215-*`. Nonblocking submission alone is not a claim of shorter complete
scene loading; the measured results and their limits follow below.

The first R215 live run at4412a14a passed both storage visits/re-entry, with zero
retained conflicts. Clearwater visit6.7s, Ironmeadow target1.6s, re-entry0.3s:
no total-loading speedup claim versus R214. JFR `r215-live-ingress.jfr` contains
no worksite-path server parks but exposes45 ordinary structural projection
geology-fence waits totalling995ms. This second structural path had bypassed
the shared planned-write batch used by first visibility. It now prepares one
distinct bounded window through the same native adapter before projecting;
actual block callbacks still recheck current permission. Before/after native
storage protocol and force guarantees are unchanged. A broad NeoForge run was
stopped in unchanged reference first-year simulation, not reported green.

The second capture at3db46fe8 (`r215-final-ingress.jfr`) eliminated the ordinary
structural-fence parks. Projection total fell from4485ms to1702ms across these
visits; full visit latency remained6.8s/1.7s, so this is a component improvement,
not complete ingress acceptance. Ambient actors reached347ms in one stage call.
Thirty synchronous carrier checkpoints were reached through reserved unloaded
ambient closure, outside the old departure-only time admission. The actor pass
now admits complete probes under the same soft time budget and retains every
unvisited candidate in the existing fair queue; it never interrupts a custody
transition or removes its durable barrier.

The same profile attributes90 CPU samples at startup to `Map.copyOf` constructing
the structural column index (12:16:12.218–13.254), principally `MapN.probe`.
Packed X/Z longs have strongly colliding boxed hashes. The index now uses a
mixed-hash primitive-long map behind an unmodifiable Map view, preserving exact
column keys, maximum declared heights and null for absent columns. The startup
cost is separate from player-entry latency and must not be added to it.

Final production code `fffc71c2`, clean detached package verified and deployed
to `frontier-v3-r215-peak-repair-20261022`:

- 75 focused storage/execution tests, 47 shared-storage native cases, 1 extraction
  native case, 12 structural native cases; final 14 actor/budget/ceiling checks.
  Guardrails and packaged-JAR verification passed. This is affected coverage,
  not a new full-suite result.
- Fresh storage run `21657915-cda5-4d8e-bea7-8c061a496f90`: zero retained
  conflicts; visits6.8s/1.3s/re-entry0.0s. The first target chunk arrived around
  12:25:04, while the software client compiled its centre mesh/closed loading
  around12:25:08. The 6.8s includes rendering; it is NOT 6.8s of server blocking.
  Three storage GUI frames inspected, but they are not inventory or economy
  acceptance (immediate menu captures can precede slot packets).
- `r215-peak-repair.jfr`: actor-stage maximum347.3->80.9ms versus the preceding
  same-seed fresh scenario; structural projection maximum51.1ms. No server
  keep-up warning during visits. Warm quiet64-turn mean2.0ms/max3.0ms PM,
  mean2.2ms/max3.2ms through native PM-post. No ready backlog or deferred actors.
- Fresh startup still has a1.94s PM invocation and one2.09s accumulated-lag
  warning, before player connection. Profile shows eager planning-dependency
  restoration and initial geometry in `PedestrianPlanning.tick`, before its
  worker-advance budget starts. Column-index MapN clustering disappeared. The
  startup planner boundary is a remaining separate optimization, not fixed by
  the section/actor changes or disguised as chunk-sender time.
- Graceful stop12:26:47 completed all-dimension saves; same-world restart,
  deploy identity check and render scenario `f5033aa1-7551-4be2-974a-13fd3b7663ca`
  completed with revision4637/instant3562 and zero required/inventory/scene/
  custody conflicts. No keep-up warning in this process through the check.
  Two inspected frames show loaded surroundings; the third still lacks neighbour
  meshes at the short observation window, despite all3x3 chunk packets arriving.
  This is persistence/ingress evidence, not full-render acceptance for all three
  views. The pilot's `wait.ms` currently counts client ticks, so reported measured
  wall timings, not its nominal wait setting, define these observations.

No claim of instantaneous first loading or x30 capacity. Test client and capture
are stopped after checks; current deployment remains running for manual testing.

## Initial chunk ingress (R214)

The native sender retains Minecraft's pending membership, batch quota and actual
sent-count acknowledgements. Its per-connection discovery index is populated by
native enqueue/drop events; new candidates are distance-ordered, held candidates
rotate fairly, and a collection probes at most64 candidates. It does not scan and
sort the entire pending queue again after a held nearest chunk. Native readiness
and presentation admission are checked before sending; no old ready verdict is
retained in that index. Disconnect discards it, drop removes its exact key, and
vanilla sends outside the v3 dimension remove their keys too.

Initial dynamic handoff is assessed by the existing bounded preparation owner,
not independently by every sender retry. It publishes READY only after current
owner checks; a classified dynamic conflict may become PRESENTABLE_CONFLICT but
cannot admit work. Such conflicts remain in reconciliation and may later become
READY; later pending effects cannot hide already admitted terrain. Natural load
reopens admission. Nearest/FIFO preparation is shared with fixed-container
discovery. A deferred socket no longer monopolizes the first-ID selection;
the materializer examines a bounded window and executes at most one lifecycle.

The client loading observer retains transition records for every visit, coalescing
position/mesh changes instead of stopping after64 startup records. Visit duration
includes target resolution, teleport/received terrain and the vanilla loading
screen, not only server chunk sending. R213 measured19.3s/11.1s fresh and1.4s/0.1s
after restart; those are different cold/warm cases, not a before/after speed ratio.
The first R214 native measurement was19.6s/11.1s: queue bounding alone did not
improve ingress. Its server JFR attributes173/419 server execution samples during
visits to global service-turnover geometry discovery. This is sampled CPU, not
41% of wall-clock waiting. The follow-up uses declaration-only column discovery
before current local terrain checks, retains authored access-exclusion overlays
independently of shape observations, and avoids resolving navigation to publish
an UNKNOWN-only write fence. The complete-geometry API remains explicit; ordinary
actor turnover does not call it. That follow-up measured16.3s/11.2s: lower CPU
did not remove field preparation latency. Its read-only field timeline showed
roughly two initial block writes per canonical tick, and JFR captured a durable
navigation invalidation commit from the individual `setBlock` callback.

All existing planned block-write windows now prepare navigation invalidations
in bounded groups, including exact retained contextual dependents. The native
per-block guard remains authoritative and rechecks current facts; rejection or a
later neighbour update cannot borrow the batch as a write permit. Worksite
projection uses this same path. UNKNOWN-only fences do not build a route view.
Field effect budgeting starts once at the first actual effect after bounded
durable preparation, not before its disk wait; repeated calls in the same native
tick share the count/time allowance. This removes a preparation-induced zero-work
loop without raising operation ceilings or weakening durable-before-effect.
Final full-pack fresh-world follow-up at4d381880 measured6.5s for first
Clearwater entry (previous19.3s) and0.9s to receive Ironmeadow's target chunk
(previous visit11.1s). The second number is NOT complete scene rendering:
the immediate GUI frame still had missing surrounding terrain. Evidence:
`build/r214-batch-storage.json`, run3e461bc0-d1c4-4053-afd5-ea07594e9cce.

The separate checked-in `live-settlement-ingress-render.json` scenario revisits
two prepared settlements after a native graceful stop/restart and visits a third
previously unvisited settlement. Run ca0270a7-cfd0-43b2-9c8b-b6b92eeea236 passed;
all three captured scenes were personally inspected and contained terrain and
buildings. Clearwater entry took4.8s after restart; Ironmeadow's centre arrived
in0.2s, its3x3 packet neighbourhood and centre mesh were ready about2s later.
The new third target arrived in0.9s; its3x3 packets and centre mesh followed
about4s after teleport. Software-rendered neighbouring meshes continued compiling
after those endpoints. Capture waits are observation windows, not render-time
benchmarks; neither a centre mesh nor these views prove the entire view radius.
Receipts: `build/r214-render-restart.json/log`, current artifact SHA512 beginning
9ed28382bf0e8530. Final revision5920/instant4153 had zero retained required,
inventory, scene or replica-custody conflicts. The client was disconnected.

Focused native navigation42/42 and field19/19 checks passed, including current
shape and partial-write recovery; detached packaging/guardrails passed. Earlier
395/395 native checks remain evidence for unchanged boundaries. The aggregate
check was interrupted in unchanged economic simulations and is not reported
green. Residual2.1–2.3s server lag peaks occurred during ingress; this result is
a measured removal of dominant repeated work, not an instantaneous-loading or
large-world throughput guarantee. Same seed/profile/route were retained, but
single-run cold/warm and JIT variation preclude a universal speedup claim.

## Regional extraction geometry (2026-10-21)

Extraction supplies immutable block facts to `RegionalNavigationProjection`,
which owns resource-independent support/collision/unknown interpretation.
`ExtractionGeometryFacts` retains each site's declared footprint separately
from its current source/observed overrides. A local change samples only affected
16x16 regions, retaining their full vertical columns. Unchanged regions and
other mines are shared. There is no repeatedly flattened world-wide mine union.
`LocalBlockExclusions` intersects only changed regional facts; remote edits do
not resample mines. Existing shape-neighbour/profile invalidation remains active.

Compact geological declarations remain the geology owner's authority, not a
mutable navigation reserve or a worker cursor. Initial construction, changed
declarations, a new knowledge baseline or recovery still need their appropriate
compilation. Changing a layout compares that site's declarations, not every
mine; changed regional facts alone flow into navigation projection. This does
not claim O(1) layout admission, lazy world generation or arbitrary scale support.

The R205 live profile attributed 107 of 874 server-thread execution samples to
the old global footprint's `Set.copyOf` construction. On the same tick560400
snapshot, alternating one authored block between its two retained observations
and building the complete frontier route-knowledge view took a median3819ms per
pair before versus9.25ms after (seven warm pairs, Java21, 2GiB heap; first build
excluded). Both expose117148 retained blocks with the same result hash. Focused
checks additionally compare complete incremental/reconstructed views, mining,
extension, unknown/observed facts, recovery and multi-level support withdrawal.
This is a specific geometry-path comparison, not a server TPS multiplier or
proof of every cause of chunk-delivery delay. Raw probe/input/results remain in
ignored `build/R206GeometryProbe.java`, `r205-economy.snapshot` and
`r206-{geometry,full}-{before,after}.log` in the assigned implementation checkout.

## Frontier v3 current scheduler and diagnostics

Quarry graybox r2 pins a hashed64-action/128-weight execution budget; explicit
policy reviews cost at least8. Only registered stock/trade reconsideration
hints merge by exact owner/kind; facts and physical receipts never merge.
Current queue pressure separates ready/held/future at its observation tick.
Stale owner rows expose last-observed values, not a fabricated current lag.
Host-turn timing includes PM durability exit; native-through-PM-Post timing
does not include later listeners or the idle wait. Immutable extraction geometry
is reused across depletion, and the owned WAL tail is validated incrementally
on append with complete validation on read/recovery. Physical fences remain.
Detailed design and actual verification receipt are owned by canonical
governance `docs/work-orders/PM-RUNTIME-PERFORMANCE-20261010.md`.


Schema v40 has one server-thread admission controller. Non-critical regional,
railway, settlement and projection work shares a default soft budget of 3 ms
and 256 weighted operations per tick. Safety-critical combat attribution,
reconciliation and an already-started idempotent operation are not abandoned
when that soft budget is exhausted.

Use `/pale_mirror performance` as an operator to inspect the current tick,
deferred work, cumulative overrun count and per-work average/max duration.
The settings live in `pale-mirror-server.toml`:

```toml
[runtime]
softBudgetMillis = 3.0
weightBudget = 256
```

Authored residents have a separate visual-only AI LOD in
`pale-mirror-visuals-server.toml`. All 48 stable entities remain visible, but
only 16 nearby residents run vanilla AI by default.

Fresh-world planning is also bounded. Visuals samples mountain biomes cheaply,
derives nearby foothill candidates and ranks their footprint without constructing
NoiseChunks. The default six-worker genesis pool evaluates indexed terrain-ranking
work and region-feasibility waves concurrently. A bounded two-waves-ahead window
lets a free worker take another candidate instead of waiting for the slowest probe. Nested work runs inline to avoid
pool starvation; final acceptance remains sequential and deterministic. Set
`genesis.plannerWorkers` between 1 and 16 in `pale-mirror-visuals-server.toml`;
`1` retains the serial recovery profile. An accepted region normally spends one exact five-point settlement
survey, two bounded mountain-face and local-pad searches and one bounded railway-corridor search.
For a production-sized batch, each surveyed center may try up to 24 exact
settlement candidates and each MineSite may try at most 24 exact candidates.
The accepted Township then caches a 130-column, sixteen-block-grid relief
snapshot and may evaluate 24 deterministic layout transforms inside one shared
768-probe ceiling. Only the selected rail corridor is verified column by
column; local cut/fill remains current-chunk worldgen work. `performance` reports site, mine
and rail probe classes separately, along with planning and catalog compile
time, worker count, evaluated candidates and discarded speculative attempts. The production output range remains 3/5/6 with a 160-center survey cap,
20,000-block radius and 2,500-block final spacing. Packaged two-start and visual
audit gates may explicitly request one complete region because cardinality and
multi-region spacing have separate tests. The latest full-modpack natural visual
audit on seed `7391842605318702447` planned one strict dual-mountain region in
86.932 seconds and compiled 263 chunk slices before observing exact genesis
stamps. This is an integration/aesthetic fixture, not a production five-region
throughput claim. Production never weakens terrain to force its target.

On the full private-pack seed `7391842605318702447`, the production 3/5/6 survey
fell from 172.745 seconds in the sequential profile to 51.245 and 51.330 seconds
with six workers (3.37x). Both clean starts produced the same probe counts, catalog
hash `f9abf85a50a91e27` and byte-identical genesis SavedData. Equal-distance coarse
height samples use a coordinate-key tie-break so immutable-map order cannot alter
roads or managed-area heights between JVM processes.

For an evidence-grade profile, run the private server on its JDK 22 runtime and capture JFR:

```bash
scripts/capture-runtime-jfr.sh <server-pid> 120s build/profiles/pale-mirror-runtime.jfr
```

The Visuals GameTest and packaged two-start harness use only the current supported
runtime profile; the retired Sable renderer integration is not loaded.

The hot-path acceptance target is: no loaded-chunk static construction, no
full loaded-entity census, no vertical chunk-volume rail scan, no repeated
unchanged projection reconciliation, and no watchdog stall. Worldgen slice timing
is also reported by the Visuals runtime and in
the server log when its immutable catalog becomes ready.

## Worksite chunk preparation (R207)

Initial mine visibility uses a shared pending projection queue, not a cyclic census
of all loaded authored blocks. `ExtractionWorksiteView` publishes current immutable
regional facts through the worksite owner SPI; `FrontierV3WorksiteCurrentIndex`
retains unchanged chunk versions. Only changed cells re-enter the queue. Declaration
extension does not reset other chunks. Physical witness changes also wake their
exact positions; restart reconstructs pending work from current declarations and
retained PREPARED/SETTLED/external witnesses, never an ephemeral completion flag.

Loaded chunk incarnation and block-ticking demand changes trigger renewed owner
assessment. The nearest pending chunk to a current viewer receives contiguous progress on alternate
turns, with round-robin service on the other turns so a blocked chunk cannot starve
its neighbours. Existing runtime read/time limits and extraction write ceiling bound
each batch. Durable intent precedes writes; external changes are not overwritten.
Priority also applies to already-presented chunks: `READY` presentation is not a
current HOT custody proof after ticking demand changes. R208 observed699 pending
cells and4.1s of re-entry wait in a `READY` chunk when priority used historical
first-presentation status. The current queue no longer uses that historical gate.
Initial handoff waits cheaply on pending work, then performs the exact physical
proof. A completed queue alone never grants custody or chunk readiness.

Read/witness preparation does not consume the physical-write allowance. Already
matching blocks settle under the bounded read/time budget; only actual mutations
spend the64-write ceiling. All prepared intents are persisted before mutations,
and an unexecuted intent stays pending for the next turn/recovery.

Fixed-container presentation uses the same custody executor with alternating
demanded/fair discovery. The existing spatial index selects naturally loaded
containers from Minecraft's real `ChunkTrackingView`, including not-yet-sent
chunks; no global-world scan or diagnostic-induced demand is required. Each
discovery window remains bounded at8, with at most one reconciled surface. Fair
turns retain unloaded/mobile custody drainage. Selection preserves cyclic order,
not ID-resorting after the window wraps. Epochs, fingerprints, provenance and
durable-before-write checks are unchanged. R208's dense mine trace separated
worksite readiness from a remaining3.3s container catch-up wait: a completed
terrain queue is not evidence that the complete chunk is ready to send.

`first_visibility` includes `worksitePendingCells`. The focused scheduling fixture
uses the same 20 x 512 declarations and 64-write ceiling: first complete chunk in
15 queue turns versus 160 old round-robin turns; one dirty cell among settled
declarations in 1 versus 160 turns. These are deterministic scheduler counts, not
measured native/network milliseconds. The live ingress scenario checks actual
client arrival, current worksite proof and revisit without mutating canonical state.

Layout extension retains every old terrain declaration through indexed key/value
comparison, not `MapN.entrySet().containsAll` (linear membership per old entry).
The immutable terrain maps use hash buckets for clustered block coordinates and
still reject null keys/values. On the retained tick560400 site (5507 terrain
entries), the same one-cell extension check fell from roughly56ms warm to1ms.
This is an isolated admission cost, not a claim that all server stalls disappeared.
Live source-chunk checks still observed about7s first preparation and a separate
9.2s accumulated lag warning. The bounded JFR also contains substantial economic
assessment/continuation CPU; `PedestrianRoutePlanning.observe` is a caller scope,
not evidence that all its inclusive samples are pathfinding. Keep these residual
server costs separate from client send/compile and projection queue latency.

Extraction forecasts and undeveloped-product views retain independent bounded
per-deposit input revisions (`ImmutableKeyedInputView`), not one last-site cache
or an all-deposit rebuild on one depleted cell. Layout owns its immutable position
index; ground-protection views use bucketed sets for dense coordinates. Same-input
tick560400 forecast rounds fell from~10ms to~0.5ms with equal results; the complete
planning round still measured~80-120ms. Neither figure is a chunk latency promise.

## Frontier v3 physical-scene scale protocol

The v3 world keeps exact people, bioforms, cargo and objects. It must not
answer a large event by turning forty residents into one representative body or
by maintaining a second aggregate combat state. A large operation is instead
split by spatially indexed, bounded HOT subscenes; each subscene retains the
same canonical actor/cargo IDs and durable lease/recovery rules as a small
operation.

Before raising a configured physical-scene or active-mob limit, record a
baseline and candidate using the same commit family, seed, world/profile,
player route, view distance and warm-up. Capture a JFR from the actual server:

```bash
scripts/capture-runtime-jfr.sh <server-pid> 120s build/profiles/frontier-v3-scene-<seed>-<limit>.jfr
```

The evidence bundle must state active scene/subscene count, exact managed body
count, operation/lease/cargo correlation, queue depth and deferral age, p50/p95/p99
MSPT, maximum stall, CPU, allocation/GC and save/recovery result. Pair it with
a checked-in declarative causal scenario at the same seed. The candidate is
rejected on any duplicate/lost exact identity, unbounded queue, silent COLD
fallback, missing post-restart evidence or regression outside normal variance.
TPS alone is not a scale result.
