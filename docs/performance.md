# Chunk performance policy

Far Frontier optimises live world generation rather than pregenerating the map. The
acceptance target is 40–50 blocks/s through generated terrain and at least 20 blocks/s
(target 30) through unknown terrain at server `view-distance=8` and
`simulation-distance=6`.

## Profiles

The stable dedicated-server profile is Java 22 plus Lithium `0.15.4`, ModernFix
`5.27.2`, Fast Noise `1.0.13` and FerriteCore `7.0.3`. The current client profile
uses the vanilla renderer. Synchronous chunk writes remain enabled.

C2ME `0.3.0-alpha.0.93` is downloaded as a disabled artifact. It may be enabled only
with the profile script and only after the complete compatibility suite. Its nested
native-math code requires the Java 22 server runtime. PM and clients remain Java 21
compatible.

Start a materialised server with `scripts/run-server-java22.sh`. The wrapper uses
the pinned Temurin 22 runtime (or an explicit `JAVA_BIN`) and prevents an accidental
fallback to the system Java while leaving client Java 21 untouched.

The C2ME profile pins its global executor to eight workers. This deliberately leaves
CPU headroom for the main server thread and Create construction.
Its worldgen accelerator can be selected independently for controlled A/B runs:

```bash
scripts/set-server-performance-profile.sh --target /srv/far-frontier --profile c2me --worldgen baseline
scripts/set-server-performance-profile.sh --target /srv/far-frontier --profile c2me --worldgen native
scripts/set-server-performance-profile.sh --target /srv/far-frontier --profile c2me --worldgen density
scripts/set-server-performance-profile.sh --target /srv/far-frontier --profile c2me --worldgen combined
```

`native` uses the bundled AVX2 path and always keeps AVX-512 disabled. The nested
C2ME module is created after Java's boot layer, so Java 22's suggested module-scoped
native-access flag cannot target it; the verified Java 22 warning mode remains in use.
Test the density compiler separately on identical fresh copies before accepting `combined`.
The C2ME profile fixes no-tick chunk-load concurrency at eight, matching its bounded
worker count. ModernFix's
surface-rule optimizer can be isolated without changing the remaining stack:

```bash
scripts/set-server-performance-profile.sh --target /srv/far-frontier --profile c2me --surface-rules optimized
scripts/set-server-performance-profile.sh --target /srv/far-frontier --profile c2me --surface-rules vanilla
```

The first live-flight JFR promoted `vanilla`: the optimizer itself consumed 4.4% of
CPU samples and its temporary maps dominated allocation pressure on the current
Terralith/Tectonic/Biolith graph. Re-run three alternating fresh-world samples before
ever promoting `optimized` again.

The first corrected live-flight comparison matched eight C2ME concurrent loads to
eight workers. Against the immediately prior
recording, average JVM CPU fell from 18.37% to 11.57%, average machine load from
26.14% to 16.30%, and total GC pause time from 4.14 seconds to 1.96 seconds. The
corrected run produced no `Can't keep up`, disconnect or PM rail
exception while traversing substantially more region files. Treat this as strong
profile evidence, not a normalized chunks-per-second benchmark; the route and newly
generated terrain were not identical.

## Reproducible measurement

Before every comparison, stop every other NeoForge test server and Gradle workload.
Use the same seed, Java heap and fresh copy of the same world. Run:

```bash
scripts/benchmark-live-worldgen.sh \
  --target /srv/far-frontier \
  --java "$HOME/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java" \
  --duration 600
```

The harness refuses a contaminated host, owns the complete server process group and
writes JFR, server events and optional `pidstat` evidence under `benchmark/`.
Analyse recordings sequentially with `scripts/analyze-worldgen-jfr.sh`; it uses a
single low-priority CPU and refuses concurrent analysis instead of feeding an
entire JFR JSON document to `jq`.

Measure these stages separately:

1. idle and active PM settlement;
2. return flight through generated chunks;
3. straight flight into new non-PM terrain at 10/20/30/40/50 blocks/s;
4. first entry into an authored PM region;
5. active Create train and PM railway;
6. Overworld, Nether, End and modded structures;
7. clean save/restart and interrupted recovery.

Record p50/p95/p99 MSPT, maximum stall, CPU, RSS, GC, I/O, disconnects and client
frame times. A profile passes only when generated travel holds 50 blocks/s, unknown
terrain holds at least 20, p95 MSPT is at most 50 ms, p99 at most 100 ms, no main
thread stall exceeds two seconds, and PM structures/jobs remain valid after restart.
Idle MSPT may not regress more than 10%.

C2ME is promoted only if it also improves throughput by at least 25% or p95 MSPT by
20%. Any crash, corrupted/partial structure, duplicate PM job or dimension/Create
incompatibility rejects it regardless of speed.
