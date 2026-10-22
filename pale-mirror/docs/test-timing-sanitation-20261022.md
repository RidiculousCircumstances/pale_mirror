# Timing-test sanitation — 2026-10-22

Scope: wall-clock assertions, performance benchmarks and timing-only native/CI
campaigns in the Java modules and Frontier v3 pilot. This is test maintenance,
not a runtime optimization or proof that gameplay/loading is fixed.

## Retired

- `FrontierKernelBenchmarkTest` and `wave1Benchmark`: historical Wave 1 toy
  counter throughput/allocation baseline, not current world performance. Ordinary
  kernel command/schedule/replay tests retain correctness coverage.
- `FrontierV3CarrierPersistenceBenchmarkTest`: opt-in reflective comparison with
  an external historical JAR and synchronous ledger-save internals. Current
  ledger/recovery tests remain. It was normally skipped; removing it does not
  imply a large reduction in ordinary build duration.
- F0.VA feedback benchmark (three workflows, baseline/candidate, three samples:
  18 executions), F0.V native timing benchmark (six native executions), their
  CLI commands and comparison-only tests. The 3x/positive-improvement gates were
  obsolete workflow obligations, not gameplay invariants.
- Serialized CI timing job/runner and 2.5x acceptance gate, including its merge
  argument and dependent tests. It previously added twelve serial shard
  executions to the optional correctness workflow. Correctness coverage,
  immutable identities and cross-lane semantic comparisons remain. Provider
  start skew/overlap no longer invalidates otherwise complete semantic results.
  Historical reports/work orders remain historical, not runnable instructions.
- `disposable-resident-after-cold-take-restart.json`: requires a transient
  HUNGRY/CONSUME phase to survive native-client startup and restart. R220 showed
  the meal legitimately completes before admission; the current fixture starts
  in CLEAR_ACCESS, not CONSUME. Its fixture remains because domain and diagnostic
  tests use it. `ResidentMealProcessTest` retains exact held-item/claim/schedule
  and codec recovery coverage; admission replay and native HOT meal scenarios
  remain. These are not a replacement claim for native restart at this exact
  meal phase: that specific native acceptance is unproven.

## Retained without arbitrary speed gates

- Route-maintenance conflict/drain test: removed the two-second worksite compile
  assertion; retained the actual lifecycle and ownership assertions.
- Legacy-domain large weighted allocation test: removed the 250ms assertion;
  retained exact large-quantity allocation and named the test accordingly.
- Watchdog tests use the Node test runner's controlled timers instead of real
  5–35ms waiting. Deadline firing, cancellation and listener cleanup still have
  coverage. Production deadlines are unchanged.

## Deliberately retained

- Simulation ticks: crop growth, hunger, action duration, scheduling, replay,
  HOT/COLD consistency. These are game rules, not host-speed requirements.
- Annual/scale audits: opt-in simulated-world determinism, queue fairness and
  recovery coverage. No wall-clock throughput threshold; not run for this cleanup.
- `FrontierV3PerformanceDiagnosticTest`: verifies truthful telemetry, not speed.
- Genesis worker latch/nested-pool tests and navigation worker completion bounds:
  finite deadlock guards around real concurrency, not comparative benchmarks.
- Save serialization, child-process cleanup and observer-readiness tests:
  lifecycle safety with finite waits; not deleted merely for containing a timer.
- `PhaseTiming` and ordinary runtime profiling: measurements remain available
  for diagnosing actual player-visible latency. A test cleanup must not erase
  evidence of a slow server.

No new test campaign, timing gate or replacement framework is introduced.
Unrelated Automodel quality benchmarks are outside this gameplay/performance
cleanup. No production Java or deployed configuration changes in this commit.

## Verification

Focused pilot checks cover CI merge, verification-runner wiring, retained timing
diagnostics and deterministic watchdog behavior. Java checks compile retained
test sets and run the two modified functional cases/classes plus guardrails.
No Minecraft client or native matrix is needed to verify these deletions.
