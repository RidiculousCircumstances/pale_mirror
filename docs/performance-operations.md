# Pale Mirror runtime performance

Schema v36 has one server-thread admission controller. Non-critical regional,
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

Fresh-world planning is also bounded. Visuals ranks deterministic horizontal
candidates through five biome-footprint points without constructing NoiseChunks. An
accepted region normally spends one exact five-point settlement survey and two exact mine
anchors; rejected terrain may consume at most six such surveys per requested
region. Railway elevation is interpolated between those anchors and performs
zero terrain-height probes. For 20 regions this makes 140 exact probes normal
and 640 the hard upper bound, versus 4,868 probes and 174 seconds observed in
the previous full-modpack benchmark. `performance` reports the site, mine and
rail probe classes separately, along with planning and catalog compile time.
The packaged Sable profile selected 20 regions from the accepted benchmark
seed in 1.758 seconds with 310 site probes, 40 mine probes and zero rail probes;
the larger private-modpack gate remains the release-grade timing measurement.

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
