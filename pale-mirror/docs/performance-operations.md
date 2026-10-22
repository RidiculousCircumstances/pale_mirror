# Pale Mirror runtime performance

## Cooperative durability and generated geology (R218)

Ordinary canonical transactions now retain one immutable prepared transition
until its exact WAL receipt is available. Neither revision, command receipt nor
schedule consumption is published early. Pending predecessor admission is
distinct from an admitted transaction awaiting durability. The server can run
other Minecraft work; this does not parallelize the ordered canonical aggregate.
Physical protocol closure precedes the producer's bounded completion callback.
Existing operation journals remain the recovery owners, not those callbacks.
Native non-replayable interaction boundaries settle predecessors before reading
state; explicit saves, fatal diagnostics and shutdown remain synchronous.

Worksite projection keeps up to four non-overlapping immutable preparation
windows, with one shared read/write/time allowance and viewer proximity priority.
Pending durability withholds only its own cells. Applying a ready window still
checks the exact owner, declaration, witness and physical preimage.

The graybox natural-geology provider runs as a normal biome feature, before
chunk publication, and records its immutable seed/configuration/block baseline
in a serializable chunk attachment. The provider owns only initial natural
material, not built stairs, containers, economic state or mining progress.
Later extraction and player mutations use the existing worksite operation
protocol. A mutation witness takes precedence over generation history.
The current provider deliberately accepts only AgriculturalFlatGenerator;
arbitrary world-generation mods need an explicit placement/survey adapter.
They are not silently overwritten with graybox rock. Pilot worldgen and
simulation now select the same immutable ruleset before level generation.

Native scenario `disposable-geology-baseline-recovery.json`, seed125/r22,
actual pack agricultural-flat datapack, 20FPS private software-rendered client:
`build/r218-geology-recovery-configured.{json,log,pmv3.jsonl}`. First visit
7.829s; both target chunks READY after9.035s; after graceful restart the
visit took1.218s. Generated attachments were observed in both mine chunks;
a player-broken stone block remained air after unloading and restart, with
exactly one external source change, ACTIVE/OWNED container and zero required,
scene or inventory conflicts. Both frames inspected: coal/stone frontier and
the missing block visible. This is not a mining-production or whole-view
full-pack acceptance claim. Earlier runs without the matching worldgen ruleset
used fallback projection and are not geology acceptance.

JFR `build/profiles/r218-geology-recovery-configured.jfr` contains no ordinary
canonical WAL append parks; one13.7ms append wait belongs to an explicit native
boundary. Periodic checkpoint/compaction still account for9 parks/269.4ms.
R217 captured86 ordinary WAL parks/5387.9ms; profiles and client geometry differ,
so this establishes removed blocking call paths, not a controlled latency ratio
or percentile. First entry is still seconds, not instantaneous.

Verification: NeoForge unit suite815/815; kernel/persistence/geology/fixture
selection174/174 (`build/r218-kernel-recovery-final-2.log`); native storage48/48.
Full native gate395/396 initially passed: one test incorrectly required READY
after a single bounded physical turn. Its original queued/ineligible assertions
remain; it now advances ordinary turns until READY within its existing deadline.
Affected physical-turn slice9/9 passed (`build/r218-physical-turn.log`), including
that correction; unrelated395 native outcomes reused, not repeated.
The all-module aggregate is not claimed green: its long frontier test worker
overlapped a subsequent fixture compilation and encountered invalid classpath
failures; it was stopped. The final scoped kernel/recovery run was isolated.
Guardrails and packaged-JAR checks passed. Runtime source includes a first-poll
failure guard: retain the prepared transaction before polling so a failed force
cannot lead quarantine reporting to append a competing transaction revision.

## Hearthvale first-mine ingress (R217)

Fresh seed125/r22 reproduction at (-121,76,43), mine
`extraction:quarry-1ceecae7-710b-329d-ad71-0cde62b70d24`, chunks(-8,2)/(-8,3):
the baseline client visit took24.61s and both chunks became READY after26.82s.
This is first entry, not a warm teleport. The disposable lite fixture uses the
actual r22 geological volume, view8 and a20FPS software client; it is not an
exact full-pack or human-rendering benchmark. The earlier `world` fixture had
no mine and was rejected, not counted as product evidence.

Confirmed connected bottlenecks:

- Worksite projection waited for the shared block ledger to become entirely
  clean while structures/roads continued adding writes.
- Section binding/root/data/index stages each waited for another server poll;
  narrow waves still serialized a large image across many server ticks.
- Prepared-but-not-applied cells re-entered preparation, and the shared NEW
  preparation admission also delayed execution of already-fenced windows.
- Querying two extraction container support/socket cells rebuilt the complete
  mine declaration.65/521 baseline server CPU samples included `socketSupport`.
- Structural presentation priority ignored spectator cameras while worksite
  priority included them. Priority now follows all viewers; no new ticking or
  physical interaction authority is granted.

The common journal now runs immutable I/O stages through force-driven futures
on its existing single writer. A wave occupies at most half the section cache
(32/64 normally;2/8 in small-cache tests), leaving checkpoint/read headroom.
The shared bounded writer queue is64 tasks; neither physical block-write limits
nor worker count increased. Cache/address/query publication stays on the owner
thread. New cache slots are reserved before asynchronous submission, preventing
the former65/64 residency overflow.

An exact scalar-record durability fence can acknowledge the prepared window
before unrelated sections of the same image finish. It compares the actual
forced records, not a queue-admission flag; failure remains latched globally.
An acknowledged fence is historical evidence, not current execution permission.
Every execution rechecks the exact retained witness, current declaration/owner
and actual preimage. The unfinished bounded suffix keeps its proved fence;
SETTLED successors cannot revoke a force that already happened. Explicit native
save/shutdown still drain the complete image. No persistent format changed.

Focused tests cover interrupted waves, sibling receipts, newer dirty successors,
foreign fences, failures, cache bounds and recovery. The96-section deterministic
case takes three owner continuations after initial admission, not a tick for
every file barrier. Native shared-storage coverage is47 required tests.
Frozen-candidate run `50dc07ed-3bcd-4499-a166-cb97cad40578`:
first target visit7.26s (baseline24.61s), both requested chunks READY12.49s
(baseline26.82s). That is roughly70%/53% lower latency in these runs, not a
percentile guarantee. Zero required/scene/inventory conflicts; native frame
reviewed: quarry stone, coal, access floor and stairs are present. Some outer
software-client meshes are still absent at capture; not whole-scene rendering
acceptance. The two-chunk12.49s remainder is explicitly NOT closed.

Evidence: `build/r217-fresh-reviewed.{json,log,pmv3.jsonl}` and
`build/profiles/r217-fresh-reviewed.jfr`;33 focused NeoForge tests plus one
extraction point-view test and guardrails passed (`r217-final-reviewed-tests`).
The47-test native shared-storage pass is `r217-exact-native-storage.log`.
An earlier exact-record native attempt was aborted before client connection
because source changed during preparation; it is not latency/product evidence.
No instantaneous-loading or complete economy acceptance is claimed.

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

Post-check incident at12:29:46/instant5619: the existing COLD scout route consumed
a legitimately absent support from the shared geometry provider and dereferenced
it. This was not a storage receipt failure. The legacy consumer now skips absent
edges and reports its existing RouteUnavailable result if isolated; it does not
synthesize ground or swallow unrelated errors. Twelve focused patrol/missing-
support checks and guardrails pass, including detour and isolated-start cases.
The same saved world is retained for recovery after deploying this crash fix;
no additional hive feature is activated by this repair.

Recovery receipt: d2c92041 exact deployed hash verified, same world resumed
past5619 to8258 (revision8863), active, no new keep-up/quarantine log entries.
The one retained diagnostic is event revision6442's kernel-quarantine incident,
not a new route/storage conflict; it was not deleted to manufacture green.
Inventory/scene/custody conflicts remain zero. Ready-lag0, deferred actor queue0;
recent64-turn PM mean3.77ms/max56.33ms, host-through-post3.88ms/max56.46ms.

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

## R216: bakery route quarantine, 2026-10-22

The R215 server later stopped at tick54899 (13:14:11 +05), not from an
OOM/watchdog: `BakeryCompletionPlanning` previewed a move whose appended workshop
ingress failed `requireAcceptedRoute` at support `(98,64,15)`. Read-only recovery
of the actual snapshot plus WAL reproduced the same failure. The adjacent
production bench `(99,65,16)` retained `WRITE_PENDING`; both input/work stations
therefore lacked complete clearance knowledge. Bakery had searched only the
outdoor leg and appended the internal cells without a knowledge request.

`BakeryKnownNavigation` now declares the semantic goal and facility access once;
the shared navigator owns the entire path, including workshop entry/exit, for
HOT hints, COLD moves and storage replacement. Replay uses that same knowledge
composition without searching. A real obstruction stays blocked; unknown facts
retain the common addressed planning wait rather than producing an invalid move.
No collision check, custody fence, event validation or quarantine is suppressed.

Focused bakery tests:31 passed, including real pending-geometry admission,
snapshot round-trip, confirmed-shape resumption and stale/blocked receipt refusal.
Guardrails passed. The recovered live failure now yields `UNKNOWN_GEOMETRY` and
an exact wake dependency, with no movement/resource mutation. Native same-world
recovery is separately checked by `live-bakery-workshop-knowledge-recovery.json`;
its result must not be inferred from these isolated checks. R215 startup/render
latency limitations remain open; this correction makes no performance claim.

Deployed clean detached source `eedf4702` into the SAME incident world
`frontier-v3-r215-peak-repair-20261022`, Java22, full pack/C2ME. Detached
guardrails/JAR/package verification passed; deployment identity verification
passed at startEpoch1792657998, JavaPID3847173, service active. PM SHA512:
`f4f96671f7d3ff8e9d7f4b18979e258f752bb9d5ae52b880958d5d0d00944407f3735e34bdcd8a6182385e8daa6a73ba1ff63cc55ad3043e3a66ce47819b8807`.

Native run `f4362475-8041-49f0-bf89-5bdafe4f4278` is exploratory, NOT terminal
acceptance: visit requested feetY66 but the player fell to64, so it never crossed
the visit barrier. Stopped the task client; corrected scenarioY64. Its ordinary
chunk load nevertheless resolved the missing workshop observation. After leaving
HOT, the exact recovered baker advanced from `(135,65,14)` to `(98,65,15)`.
At tick60318 the world had advanced beyond the former crash tick54899, with no
new quarantine and zero inventory/scene/custody conflicts. One old kernel
incident remains retained; it was not erased for a green summary.
Accumulated keep-up warnings remain:2981ms at startup and2132ms during client
ingress. The explicit client termination produced a connection-reset log,
not a server failure. Neither ingress performance nor full visual acceptance
is claimed by this repair.

The exact bakery task is still ACTIVE, not completed: station diagnostic has
storedHeat0, no fuel stock, no pending fuel/recipe and no operation. The shared
station-energy admission reports `INSUFFICIENT_HEAT`; production service access
therefore withholds entry to the work cell. This is not another invented-route
receipt. The completion scenario now explicitly requires fuelReady before
attempting its unchanged terminal assertion. No forced fuel injection, completion
or repeated fruitless native run was used. Fuel replenishment/economy and full
baking-cycle acceptance are not established by this navigation repair.

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
