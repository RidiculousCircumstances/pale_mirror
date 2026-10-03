# Performance evidence contract

## Select the claim and evidence

For a structural bound, inspect the whole production path, callbacks, cache
validation and invalidation, and use a connected focused work/behavior check
where possible. Do not substitute output size or helper call count for total
work. This establishes a bound, not a measured throughput/latency gain.

Use retained relevant traces to attribute an observed bottleneck. Add bounded
runtime instrumentation/JFR only when attribution remains unknown or that tier
is necessary. No unconditional baseline, JFR or mutation campaign per edit.
The unified PM / senior-tech-lead protocol governs retry and cost decisions;
Terra owns technical method selection and review, without a PM approval gate.

## Reproducible measured comparison

When claiming a measured gain, retain the relevant commit/artifact, seed/world,
Java/flags, pack/config, worker counts, view/simulation distances, DH mode,
route/speed, warm-up, duration and new-versus-existing chunk conditions.
Reuse an existing comparable baseline; otherwise capture the missing baseline.
Change one material variable per comparison.

Select observations relevant to the claimed bottleneck:

- planning duration and canonical/catalog hash;
- generated/loaded/sent chunks and throughput;
- TPS/MSPT and tail stalls;
- CPU/per-pool saturation, heap/allocation/GC and relevant native memory;
- disk/network pressure, queue growth, watchdogs, disconnects and retries.

Separate worldgen, PM planning/materialization, scheduling, lighting, saving,
networking and rendering. Use JFR stack evidence when needed to locate hot work,
not as an obligatory artifact for a statically evident duplicate call.

## Acceptance

Preserve exact canonical/catalog semantics, stable identities and owned
lifecycle; hashes are equal where inputs/semantics are unchanged, not “better”.
Bound queues/work and retain relevant negative/recovery coverage. For a claimed
runtime gain require improvement outside ordinary variance without new errors
or server starvation. Higher CPU use alone is not an improvement. State any
unmeasured claim explicitly; structural correctness is not a measured speedup.
