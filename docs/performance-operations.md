# Pale Mirror runtime performance

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

The Visuals GameTest and packaged two-start harness include the exact private
Sable 2.0.3 runtime, because it was present in the original watchdog deadlock.

The hot-path acceptance target is: no loaded-chunk static construction, no
full loaded-entity census, no vertical chunk-volume rail scan, no repeated
unchanged projection reconciliation, and no watchdog stall while Sable is
enabled. Worldgen slice timing is also reported by the Visuals runtime and in
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
