# Accepted-change Reactor experiment — 2026-10-29

Reactor can express the bounded notification flow correctly. This follow-up did
not establish an additional calculation reduction or a stable CPU advantage over
the equivalent small Java control. Do not adopt the experimental adapter in the
live simulation. This is not a general rejection of Reactor or evidence that a
production dependency graph has been migrated.

[Plan and exact scope](frontier-v3-reactor-committed-flow-experiment.md).
Baseline production code: `00e148f3463b9e47d3bc8918ecba82d4eca7b6eb`;
source checkout started at results-only `c6c7dcfdff394187230b5ec225fd54505a07e628`.
Java 21.0.12+8-LTS, Reactor 3.8.8. No production Java source, server configuration,
world, worker allowance or business rule changed; MAIN and SCALE were not restarted.

## Real input and calculation

Read-only capture from the running 100-town SCALE world:

- Snapshot `snapshot-00000000000007773918.bin`, SHA-256
  `7995d8b9f7050ddacc23bf6b5d1389327b0b992f5747befdc88eb9cef59af11c`.
- Nine sealed WAL segments copied; all ten file hashes matched the capture
  manifest. The unmodified native segment reader also checked framing/CRCs.
- Exactly 512 subsequent transactions / 1602 events were decoded and replayed
  through the real reducer and kernel, including normal state/reference/schedule
  validation and final encoding. Private intermediate reducer images were exposed
  to the experiment only after the complete replay succeeded.
- Ticks 455741–455768; 27 distinct accepted-tick observation barriers. Sampled
  public participants: settlements 17, 27, 82, 16. Four consumers were measured,
  not all 100 towns' decisions.

The consumer is the actual `GoodsTripPlanning.plan`, including immutable owner
projections, alternatives and any real disposable draft preparation it reaches.
The route reader honestly reports `PLANNING`; no path, sale, cargo transfer or
arrival is invented. Direct oracle results comprised 54 waits for available
transport/provisions and 54 waits for selected-trip navigation. This corpus did
not reach a completed sale or a selected executable trip.

Every accepted transaction conservatively invalidates all four consumers. The
input descriptor is the complete accepted immutable frame/revision/tick. This
guarantees input coverage in this experiment but does not claim a minimal domain
dependency graph or reproduce current production notification frequency.

## Comparison and results

Both adapters use a host-owned two-worker executor, a queue bounded to eight,
one active operation per key and only the latest pending input. Dispatch drains
at accepted canonical tick boundaries. Reactor uses a bounded unicast sink per
key, `onBackpressureLatest`, `concatMap(..., 0)` and the same host executor. It
does not use a timer, sleep/debounce or default Reactor worker pool.

One warm-up per adapter, then three rotated measured pairs on the same retained
frames. Steady processing includes input dispatch/capture, real calculation,
freshness checks and publication; excludes replay, adapter startup/close and
independent oracle construction/comparison. CPU and allocation sum the caller
and complete worker tasks, including framework callbacks. No timing thresholds
were added to CI.

| Measure per full replay | Java | Reactor |
|---|---:|---:|
| Accepted notifications | 2048 | 2048 |
| Actual trade calculations | 108 | 108 |
| Fresh publications | 108 | 108 |
| Worker dispatches | 108 | 216 |
| Dispatches that skip already-published input | 0 | 108 |
| Peak pending host tasks | 4 | 4 |
| CPU median, seconds | 3.959 | 4.199 |
| CPU range, seconds | 3.877–4.712 | 3.658–4.671 |
| Wall median, seconds | 3.115 | 3.671 |
| Wall range, seconds | 3.106–3.891 | 2.881–3.913 |
| Allocated median, decimal MB | 2080.180 | 2076.714 |
| Allocation range, decimal MB | 2062.768–2106.090 | 2058.453–2080.145 |
| Worker CPU outside calculation, median ms | 1.592 | 3.991 |

Reactor's extra dispatch consumes the latest buffered signal after the active
operation has already captured and published that same latest frame. The guard
skips its expensive calculation. This is overhead, not a duplicate trade effect.
Equal-valued answers still retain the new input generation; value equality never
substitutes for freshness evidence.

The observed CPU difference has overlapping ranges and changes direction
between runs; the final corrected harness median favoured Java. More than 99% of
worker CPU is inside the common real calculation, not the notification operator.
Allocation ranges also overlap. No native TPS, server latency or 20-tick target
improvement follows from these measurements. The 2048→108 coalescing belongs to
the common service policy; it is not a measured improvement over the installed
kernel, which already coalesces some reconsiderations.
The short repeated workload, JIT/cache warm-up and other live host processes
also limit interpretation of small timing differences. Work/dispatch counts
are the stronger evidence here; no additional confidence benchmark is needed
to establish that the library did not eliminate more real calculations.

## Correctness and limitations

Both adapters matched all 108 direct oracle answers at every accepted-tick
barrier, comparing choice, decision, draft-event count and route-demand endpoints,
extent, service, bounds and complete declared terrain/profile version. Initial
harness attempts incorrectly compared newly constructed geometry wrappers by
object identity; the oracle now compares their declared semantic evidence. Those
failed attempts were not product defects or measurements of either adapter.
Final review also put the Java control's publication-map read under the same
lock as its writes; the confirmed receipts were rerun after that correction.

Additional explicit interleavings passed for each adapter:

1. An accepted newer frame arrives after real calculation input capture. Exactly
   one late result is rejected; the latest image is calculated and published.
2. Injected calculation failure publishes no answer. An explicit new notice
   retries successfully; failure does not trigger an unbounded retry loop.
3. Scope closes during real calculation. No late answer is published, later
   notices are rejected, and disposing the adapter does not shut down the host.

These interleavings are deterministic experiment gates, not observed native
completion races. Fixed keys, conservative whole-frame invalidation and the
short captured interval do not prove dynamic owner retirement, a narrowed
dependency graph, completed route admission, transaction acceptance or long-run
live backpressure. Earlier valuation input-change checks remain separate
evidence; they do not turn this experiment into production integration.

## Decision and reproduction

Centralization should operate on explicit producer-owned immutable inputs and
recalculation notices, preserving existing canonical scheduling and exact domain
admission. Reactor can implement that notification layer, but it cannot discover
those dependencies or remove the cost of preparing them. Trying another reactive
library is not the next evidenced performance fix. First identify which real
producer changes genuinely require the expensive calculation and avoid preparing
the same unchanged facts again. A future native integration needs its own scope;
this experiment alone does not authorize or validate it.

Retained receipts under `build/simulation-throughput/reactor-committed-flow/`:
`corpus/capture.json` and immutable input files; `confirmed/identity.txt`,
`confirmed/answers.tsv`, `confirmed/measurements.csv`, `confirmed/semantic-checks.txt`,
`confirmed.log`, `verification-final.log`. Earlier `run-*` and `final` receipts are diagnostic attempts,
not the final measurement set.

```bash
JAVA_HOME=/home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2 ./gradlew \
  :pale-mirror-frontier:frontierV3ReactorCommittedFlow \
  -PreactorCorpus=build/simulation-throughput/reactor-committed-flow/corpus \
  -PreactorOutput=build/simulation-throughput/reactor-committed-flow/repeat
```

The corpus is an intentionally local runtime receipt, not packaged test data.
Reactor, the native-reader bridge and all experiment classes remain confined to
the manually invoked `reactorExperiment` source set. No live deployment or push
is part of this result.

The experiment and six semantic interleaving checks passed. `guardrails` and the
production frontier `jar` task passed; the production runtime classpath reports
no dependencies, and JAR inspection found no Reactor/experiment/reader-bridge
classes. No native acceptance or full unrelated test-suite result is claimed.
