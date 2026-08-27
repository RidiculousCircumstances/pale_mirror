# Frontier v3 Wave 1 kernel baseline

Date: 2026-08-27
Scope: pure in-memory kernel only; this is neither a Minecraft-server TPS claim
nor a full-world profile.

## Fixture

Run:

```bash
./gradlew :pale-mirror-frontier:wave1Benchmark --rerun-tasks --no-daemon
```

The tagged fixture warms the command path outside its measurement, then runs
10,000 single-event commands and 10,000 same-instant due actions separately.
Each run proves the final deterministic checkpoint input `00002710` (decimal
10,000). It reports current-thread allocation through the JDK management API;
`-1` means that API is unavailable and is not treated as zero allocation.

## Initial project-machine results

Java: Eclipse Adoptium OpenJDK 21.0.12+8-LTS.

| Path | Run 1 | Run 2 | Allocation | Canonical checkpoint input |
| --- | ---: | ---: | ---: | --- |
| Command → event → reducer | 87,665 ops/s | 88,354 ops/s | 14.8 MB / 14.8 MB | `00002710` |
| Due action → event → reducer | 233,022 ops/s | 389,892 ops/s | 14.7 MB / 14.2 MB | `00002710` |

The schedule result has normal short-fixture JIT variance; use this fixture to
catch regressions and compare a later change on the same route, not to set a
production capacity. Wave 6/7 must add server-tick, heap/GC, queue-depth,
Minecraft materialization and full-scale profile evidence before any 20-TPS
claim.
