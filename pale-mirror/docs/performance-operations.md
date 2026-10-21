# Pale Mirror runtime performance

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
