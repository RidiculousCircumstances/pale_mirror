# Simulation delay tracing

`FrontierV3DelayTrace` contributes read-only JFR events while recording is enabled.
It adds no world file, canonical field, timer, worker pool or lifetime sample registry.
The existing `scripts/capture-runtime-jfr.sh` starts a bounded profile recording.
First resolve the actual task-owned Java PID from the selected service cgroup and
verify `/proc/PID/exe`, cwd and service; never attach using a wrapper's MainPID.
Keep recordings bounded (for example 60 seconds and 128 MiB).

Custom events: `pale_mirror.ClockTurn`, `ClockAttempt`, `DelayPoint`,
`ExecutionStage`, `SchedulePressure`. Enable them explicitly in a JFR configuration
if the chosen recording profile disables custom events. Each invocation of a
scheduled owner has an action-pass ID; it is diagnostic correlation, not another
business identity. Async tasks retain that context across threads. A computed
result, notification, owner observation, transaction publication and durable WAL
confirmation are distinct boundaries. Navigation slices include their readiness
notification, and do not identify an entire route or make a global-blocking claim.

Consume a recording with Java 21 or newer:

```sh
java tools/performance/SimulationDelayReport.java /absolute/path/recording.jfr
```

The analyzer streams JFR and retains at most one million custom events / 100,000
tasks; exceeding that bound fails explicitly. Shorten the recording rather than
claiming a truncated result. Reports partition wall time between the first and
last completed owner turns for each world. Nested owner spans are unioned.
Exact calculation and WAL gates are split at observed worker phase boundaries.
A dependency already in flight when recording starts can remain unresolved.
Missing coverage and JFR data loss are reported; zero data-loss events alone is
not proof that a trace has every required subject or boundary.

`RUNNABLE_UNATTRIBUTED_DISPATCH` includes possible Minecraft/GC/other host work,
not proof of a missed wake. Stage spans nest and workers overlap: do not add those
inclusive costs to the exclusive wall-time table. JFR execution/GC/park samples
can resolve that remainder. Queue/budget consumption is not committed work;
committed transaction count is reported separately. Batchable publication is not
proof of a forced WAL prefix: join transaction APPENDED world/sequence to WAL
MEMBER and COMPUTED records before making a durability claim.
