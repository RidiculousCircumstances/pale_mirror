# Causal simulation delay tracing

`FrontierV3DelayTrace` extends the existing optional JFR evidence boundary. It has
no canonical state, world file, queue, worker pool, simulation timer or repair
permission. There is no runtime telemetry collector. Without an enabled recording,
events/counters are not produced; current trace scope is thread-local and restored
on exit. The existing cooperative WAL wake is observed, never replaced.

## Capture and inspect

Use Java 21+, Python 3.11+, and one exact task-owned user service. Capture resolves
the Java PID from its service cgroup and verifies cwd, process birth, clean frozen
source ref and installed JAR digest. It does not read RCON credentials or issue
simulation commands. The source artifact must live in the frozen checkout.

```sh
python3 tools/performance/capture-simulation-delay.py \
  --service SELECTED.service --runtime /absolute/runtime \
  --source-repo /absolute/frozen-source --source-ref EXACT_COMMIT \
  --artifact /absolute/frozen-source/pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar \
  --jdk /absolute/java21 --seconds 30 --output /absolute/new-capture-directory
```

Capture is bounded to 10–120 seconds / 128 MiB. Optional `--settings /absolute/profile.jfc` selects an observational sampling configuration; its exact hash is retained. Use the same configuration for paired traced comparisons. Preserve provenance and raw JFR.
The capture CLI runs the analyzer. For an existing recording, enable the six PM
events using `simulation-delay.jfc`, then run:

```sh
python3 tools/performance/simulation-delay-report.py /absolute/recording.jfr \
  --java /absolute/java21/bin/java --output /absolute/new-report-directory
python3 -m unittest discover -s tools/performance -p test_simulation_delay.py
```

Open `timeline.html` locally; it embeds its data and needs no network service.
Zoom/pan the timeline, choose a world and click/search task IDs to follow exact
parent and consumer links. `report.json`, `status.json`, normalized `trace.ndjson`
and `timeline.perfetto.json` retain machine-readable evidence. The analyzer records
its source-file hashes separately from the producer artifact identity. `reported` means
the tool ran, not that every causal dependency is known. Errors write failed
status and never silently truncate or reuse earlier success. Reader bounds are
256 MiB JFR, two million scanned events, one million retained events / 150,000
nodes and 512 MiB normalized input; shorten a capture on capacity failure.

## Meaning and limits

- Clock turn → advance attempt → action pass → worker → ready notification →
  coalesced owner delivery uses producer-supplied IDs. Repeated observation can
  connect a shared result to multiple consumers. Scope/observation edges are not
  all precedence edges; this graph is not a longest-path DAG.
- Host tick arrival, budget refill and each turn exit record actual current
  clock gates and secondary pending conditions. Exclusive wall time follows the
  actual barrier order. Nested turns are unioned; overlapping workers are not
  summed into clock waiting. Closed JFR chunk headers define the recording window;
  an event that started earlier cannot expand it. Partial/live/unknown chunk
  formats fail explicitly. Unknown time and recording edges remain explicit.
- Wall duration, per-thread CPU counter deltas, statistical stack samples and
  canonical ticks/debt are distinct quantities. CPU is unavailable explicitly
  when JVM counters are unsupported/disabled. Queue spans identify an emitter,
  not that emitter's CPU occupancy. GCPhasePause is a pause; concurrent GC is not.
- Transaction APPENDED world/sequence joins exact WAL MEMBER records and the
  owner's RECEIPT_OBSERVED boundary. Publication alone is not durability proof.
- Navigation events identify executor slices and their notification cause,
  not a durable whole-route identity. Ordinary game holds/future deadlines do
  not become global clock blockers without an observed clock gate.
- A capture can begin inside an earlier task, lack producer boundaries, or lose
  events. Missing IDs, WAL appends, data loss and unknown wall remainder are
  reported independently. Arithmetic balance does not prove causal completeness.
- Recording and CPU counters have overhead. A traced interval measures that
  instrumented run; it is not an untraced TPS baseline or an optimization result.
