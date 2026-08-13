# Pale Mirror runtime performance

Schema v37 has one server-thread admission controller. Non-critical regional,
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
NoiseChunks. An accepted region normally spends one exact five-point settlement
survey, two bounded mountain-face searches and nine bounded rail-control alternatives.
Rejected terrain may consume at most six settlement surveys and 24 exact
candidates per MineSite. The planner never sweeps every route column; local
cut/fill remains current-chunk worldgen work. `performance` reports site, mine
and rail probe classes separately, along with planning and catalog compile
time. With the output range set to 3/5/6, a 160-center survey cap, 20,000-block
radius and 2,500-block final spacing, the full private modpack selected four
strict mountain-native regions on seed `3374619285067712046` in 222.059 seconds.
It used 3,030 site probes, 10,561 mine probes, 43 rail probes, 8,329 unique
heights and 769,400 cheap biome samples. Eight otherwise valid centers were
discarded by final spacing. The target of five was not forced by weakening terrain.

For an evidence-grade profile, run the server on JDK 21 and capture JFR:

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
