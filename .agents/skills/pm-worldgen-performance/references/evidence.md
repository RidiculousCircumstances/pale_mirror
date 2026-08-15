# Performance evidence contract

## Reproducible baseline

Record the exact commit/artifact, seed/world identity, server Java and flags,
pack/config hashes, worker counts, view/simulation distances, DH mode, player
route and speed, warm-up, measurement duration, and whether chunks are new or
existing. Change one material variable per comparison.

## Required observations

- planning duration and catalog hash;
- generated/loaded/sent chunks and chunks per second;
- TPS, mean MSPT, and tail stalls;
- process CPU and per-pool saturation;
- heap, allocation rate, GC pauses, and native memory when relevant;
- disk throughput/latency and network send pressure;
- watchdog, disconnect, retry, and queue-growth evidence.

Use JFR stack evidence to name the hot algorithm. Separate Minecraft worldgen,
PM planning/materialization, C2ME scheduling, lighting, saving, networking, and
client rendering.

## Acceptance

Require equal or better canonical/catalog hashes, no new errors, bounded
queues, stable TPS after the route, and an improvement outside normal run
variance. A higher aggregate CPU percentage is acceptable only when throughput
or latency improves without starving the live server.
