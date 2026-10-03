---
name: pm-worldgen-performance
description: Profile and optimize Pale Mirror world generation, chunk loading, region planning, runtime simulation, materialization load, and server/client chunk pipelines. Use for slow generation, planner worker count, high CPU or memory, TPS loss, watchdogs, disconnects, JFR, C2ME, Distant Horizons, chunk I/O/sending, or performance-mod configuration. Do not conflate new-chunk generation with loading, networking, or client rendering.
---

# PM Worldgen Performance

Optimize from evidenced bottlenecks and preserve deterministic world ownership.
Follow `docs/engineering-agent-protocol.md` for task autonomy, evidence/retry
cost and review; this skill supplies performance-specific methods, not another
mandatory benchmark campaign.
The assigned executor owns this investigation and its outcome. This skill
does not assign roles or require another agent to approve each measurement.

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

First name the claim: structural boundedness, runtime attribution or measured
speed/latency. Reuse relevant existing traces/baselines. For a bounded-call-path
correction inspect total work, callbacks, cache-key construction/validation and
invalidation; use a connected focused regression/counter when adequate.
Helper query count alone cannot prove caller boundedness.

For runtime attribution use `scripts/capture-runtime-jfr.sh` when needed;
for measured worldgen claims use the relevant pack benchmark/analyzer.
Follow `references/evidence.md` for comparable measurements and relevant
metrics. Do not launch a fresh baseline/JFR campaign merely because an
algorithm changed; existing evidence may already identify the defect.

## Optimize safely

1. Remove duplicated scans, noise/height queries, reflection, object churn,
   forced chunk transitions, and unbounded work before adding threads.
2. Bound queues, per-tick budgets, retry work, and distant generation. Avoid
   nested pools that compete for the same cores.
3. Preserve deterministic catalog hashes, stable IDs, and SavedData results
   across worker counts. Do not let completion order become world state.
4. Never force-load distant chunks merely to validate physical state.
5. Treat C2ME, native acceleration, lighting mods, and DH as independent
   interventions. For a measured comparison change one material variable at a time.

## Verify and report

Validate changed whole-path work and ownership with the faithful evidence that
has the best expected discriminating value for its cost; a code-path audit,
focused counter or runtime trace may each be the right first move. When claiming
a measured gain, compare the same relevant route/profile and baseline metrics;
do not repeat an accepted unchanged baseline for paperwork.
Distinguish structurally bounded work, removed duplicate compilation and an
observed runtime speedup. State whether the change affects generation, loading,
sending, rendering or headroom; never present an estimate as a measurement.
