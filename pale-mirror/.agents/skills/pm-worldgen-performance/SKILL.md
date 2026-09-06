---
name: pm-worldgen-performance
description: Profile and optimize Pale Mirror world generation, chunk loading, region planning, runtime simulation, materialization load, and server/client chunk pipelines. Use for slow generation, planner worker count, high CPU or memory, TPS loss, watchdogs, disconnects, JFR, C2ME, Distant Horizons, chunk I/O/sending, or performance-mod configuration. Do not conflate new-chunk generation with loading, networking, or client rendering.
---

# PM Worldgen Performance

Optimize from measured bottlenecks and preserve deterministic world ownership.

## Establish the pipeline

1. Read `CONTINUITY.md`, the performance-related architecture entries, and
   `docs/performance-operations.md` completely.
2. If pack configuration or optimization mods are involved, read the sibling
   pack's `../minecraft/docs/performance.md` and its current config files.
3. Classify the complaint as one or more of:
   - planning/catalog discovery;
   - generation of new chunks;
   - loading/deserialization of existing chunks;
   - lighting or saving;
   - chunk sending/network backpressure;
   - client mesh/render/LOD work;
   - PM simulation or materialization work.

## Measure

Capture a baseline before changing algorithms or worker counts. Use
`scripts/capture-runtime-jfr.sh` and, when relevant, the sibling pack's
`scripts/benchmark-live-worldgen.sh` and `scripts/analyze-worldgen-jfr.sh`.
Follow `references/evidence.md`. Record route, duration, chunks generated,
chunks/second, TPS/MSPT, CPU, heap/GC, I/O, queue depth, and catalog hash.

## Optimize safely

1. Remove duplicated scans, noise/height queries, reflection, object churn,
   forced chunk transitions, and unbounded work before adding threads.
2. Bound queues, per-tick budgets, retry work, and distant generation. Avoid
   nested pools that compete for the same cores.
3. Preserve deterministic catalog hashes, stable IDs, and SavedData results
   across worker counts. Do not let completion order become world state.
4. Never force-load distant chunks merely to validate physical state.
5. Treat C2ME, native acceleration, lighting mods, and DH as independent
   interventions. Change and benchmark one material variable at a time.

## Verify and report

Repeat the same route and profile. Compare throughput, tail latency, TPS,
memory, GC, CPU saturation, and correctness hashes. State whether the change
helps generation, loading, sending, rendering, or only headroom; do not report
a combined stack estimate as a measured Pale Mirror result.
