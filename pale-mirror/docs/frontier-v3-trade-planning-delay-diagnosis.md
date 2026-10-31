# Trade planning delay: source and runtime diagnosis

2026-11-01. Diagnosis requested after the 50-settlement performance assessment.
No production algorithm, policy, schema, worker limit or admission check changed.
MAIN remained stopped. The private benchmark was restarted on its saved world
for one bounded diagnostic capture, then normally saved and stopped again.

## What the reported 37 seconds means

The earlier 742 ticks are `current instant - scheduled action dueAt`, while that
action happens to be in the capacity-wait set. They are **not** a measured
37-second worker execution or necessarily 37 seconds continuously in that set.

`ScheduleExecutionIndex.CalculationWait` stores the action and a capacity flag;
it has no capacity-entry timestamp. `firstCapacityWait()` returns FIFO insertion
order, not a search for the minimum dueAt. `PedestrianPlanningContinuations`
intentionally preserves the same due generation through a calculation-only
navigation wait. Therefore the observed age can include earlier calculation,
route waiting, continuation admission and repeated candidate evaluation.
The old specific 742-tick episode cannot retrospectively be partitioned from
10-second aggregate snapshots. Do not present a reconstructed critical path for
that historical episode.

In the unprofiled busy window, advice execution averaged 16.52 ms. Summed advice
worker wall time was 204.26 seconds over 184.97 seconds with two workers, about
55.2% occupancy; the independent exact worker occupied about 18.1%. This is a
worker-occupancy estimate, not CPU saturation. In 17 of 19 snapshots containing
capacity waiters the host lane was available; 16 contained ready advice awaiting
owner observation. The earlier window had 22/22 available and 20/22 ready.
Simple continuous exhaustion of the two advice threads does not explain the
whole observed lateness.

## Confirmed expensive path: preparing geometry inside trade advice

The active call chain is:

`GoodsTripPlanning.Calculation.get`
→ `GoodsTripAssembly.declaration`
→ `ShipmentEndpointComposition.knowledge`
→ `KnownPedestrianRouteKnowledge.forJourney`
→ `KnownSiteGeometry.forState`
→ `ExtractionKnownGeometry.project`.

Of 2,479 Java/native execution samples on the two advice threads, 1,106 (44.6%)
include `ShipmentEndpointComposition.knowledge`; 914 include
`KnownSiteGeometry.forState`, 900 `ExtractionKnownGeometry.project`, and 589
`ExtractionGeometryFacts.read`. These are inclusive, overlapping stack counts,
not additive CPU percentages. They establish an expensive input-preparation
path in the calculation of selected deliveries, before the separate navigation
workers do their search. The profile also contains region-map comparison and
replacement, surface-map construction, and retained collision projection.

Concrete source causes:

- `ExtractionKnownGeometry.project` keys a four-entry completed-result cache by
  complete immutable extraction registries, exclusions, geology, knowledge and
  navigation definitions (lines 21–29). A root change causes a miss even when a
  particular journey is remote from the changed site.
- `Projection.refresh` loops over every current deposit (lines 60–74), then
  compares and composes each site's regional contributions. There is local
  reuse, but discovery of which contributions changed is still registry-wide.
- `ExtractionGeometryFacts.read` includes complete geology and knowledge roots
  in its basis (lines 60–68). Changing either root can mark every footprint
  region of a site for sampling. This is a source-proved broad dependency; the
  capture does not count which root caused each individual miss.
- `KnownPedestrianRouteKnowledge.refresh` (lines 240–279) compares several global
  inputs and clears all local, field and journey views on a changed basis.
  Computing threads retain separate mutable scratch bases. Different captured
  immutable world images can repeatedly move those bases between revisions.

The shared geometry API is in use. That does **not** mean its immutable spatial
projection is prepared once and handed cheaply to every consumer. Some of that
preparation is still performed by trade's computing threads. The preceding
shared goal-field optimization does not remove this cost of obtaining inputs.

## Confirmed amplification: a choice is a multi-stage continuation

`GoodsTripPlanning.Calculation` preserves candidates and selects them one by one.
A selected candidate can suspend for navigation; late geometry, workforce,
provisions, consolidation or price admission can reject it and cause another
candidate or smaller load to be tried. Each suspension goes through the common
continuation and scheduled-advice machinery before computation resumes.

A concrete captured example is `schedule:goods-opportunity/settlement-22`:
its dueAt remained 18118 in `planning-10.txt` and `planning-15.txt`, while the
retained route request changed from source (-20979,64,-12300) to
(-11566,64,5937), with the same receiver (-12173,64,-3448). These are different
source-route alternatives within a retained due generation, not one worker
spending all the elapsed time on one route.

The recording contains 8,538 scheduler attempts for opportunity actions and
1,736 corresponding advice tasks, with up to 297 attempts at one stable party
schedule ID. An attempt includes admission/polling/continuation work. Stable
IDs can span successor generations; these counts are **not** 297 complete
restarts of one economic choice. Accepted advice is not an admitted caravan.

The kernel promotes one capacity waiter at an `advanceTo` entry, then the owner
must observe/revalidate advice, execute exact preparation, observe that result
and publish under the existing durability protocol. This contributes round trips
between computation and owner scheduling. It does not establish a deadlock or
prove that this admission mechanism is the dominant CPU cost.

Complete captured opportunity phase pairs, with tracing enabled:

| Phase | Mean | p95 | Maximum |
|---|---:|---:|---:|
| Submitted task to worker start | 12.16 ms | 56.26 ms | 149.15 ms |
| Worker calculation | 33.50 ms | 99.82 ms | 181.06 ms |
| Ready advice to owner observation | 13.76 ms | 44.62 ms | 125.84 ms |
| Advice revalidation | 0.098 ms | 0.232 ms | 1.005 ms |
| Exact worker calculation | 1.133 ms | 3.031 ms | 23.971 ms |
| Exact owner completion callback | 0.095 ms | 0.201 ms | 2.575 ms |

Different phase-pair sample sets have capture-edge exclusions. These are
instrumented distributions, not unprofiled baseline values and not an additive
reconstruction of a full logical decision. Owner handoff of an individual
completed result was milliseconds here; the evidence does not support blaming
a single seconds-long handoff or simply increasing worker count.

## Confirmed wasted computation: full reviews becoming stale

Of 319 full economic-review advice tasks, 199 were accepted and 120 rejected:
98 `BUYER_INPUT`, 16 `OWNER_INPUT`, 6 `BUYER_PLAN`. Their measured completed
worker CPU cost was 10.006 seconds, out of 24.361 seconds for complete full-review
worker CPU pairs in this capture. No full review was rejected as `GEOMETRY` in
this recording; do not blame that guard for these particular retries.

`GoodsParticipantReviewAdvice.revalidate` checks declared peer envelopes and
counterparty input revisions (lines 41–48). Relevant inputs can change while a
forecast runs, so rejection itself protects correctness. Repeated expensive
reviews against changing dependencies consume capacity that could serve other
parties. The evidence supports reducing/reusing the calculation and making its
dependency set precise; it does not authorize accepting stale resource or price
consent or declaring every one of these invalidations unnecessary.

## Direction for a subsequent fix

1. Publish immutable spatial inputs through the existing shared geometry owner
   with region/site-specific dependencies and changed-region updates. Trade
   should consume a prepared view; avoid rebuilding the extraction registry as
   part of testing each selected delivery. Keep unknown facts, collision shapes,
   profiles and current geometry fences exact.
2. Keep private choice continuations and their measured candidate facts across
   technical waits. Resume only on their actual changed dependencies. Check
   cheap feasibility bounds before acquiring another complete journey proof;
   do not replace a detailed proof with an optimistic estimate at admission.
3. Reuse unaffected parts of economic reviews and validate their actual consumed
   input tuples. Preserve current owner publication and resource/money/crew
   consent checks. Worker-count increases are not the evidenced first fix.
4. Separate scheduled lateness, capacity-queue residence, route wait, worker CPU
   and ready-result delivery in diagnostics. The current waiter age is useful,
   but its name/context is insufficient for an exact queue-residence claim.

These are diagnosis-based directions, not implemented product changes.

## Capture identity and limits

Frozen product source `de74260078da290c150007475432952d5c9086bb`, the same artifact,
world, rules and flags as the preceding fifty-settlement report. Exact direct
same-user task-owned Java PID 683034, runtime cwd, process birth, artifact hash,
clean detached source and ordinary/no-recovery/no-fast-forward state were checked.
After capture: native all-dimensions save, process exit and benchmark plus MAIN
ports closed were verified. MAIN was never started. There is no new client,
HOT, equilibrium or full-caravan-cycle claim.

The requested 60-second JFR used a 128 MiB ring. Its closed retained recording
contains approximately 50 seconds in ten chunks, with no `jdk.DataLoss` event.
This is a bounded retained interval, not an assertion that all requested seconds
or prior action origins were retained. Probe overhead was material, so use the
unprofiled measurements for baseline performance, the capture for call paths
and individual instrumented phase distributions.

The first report attempt used the Java 22 JRE without `jdk.compiler` and failed;
JDK 21 normalization of the original recording then hit the existing one-million
event safety bound. Those partial reports are explicitly failed/excluded.
For task-lifecycle analysis, `jfr scrub` removed only `ExecutionStage` events from
the original closed recording. All task points, clocks, pressure and JDK stack/
park/GC events remain. The existing analyzer consumed this declared lifecycle
projection: 436,508 events, no JFR loss, clock coverage present. Global graph
references are not closed at recording edges; full serial-stage wall attribution
and a globally closed critical path are not claimed. The original historical
37-second episode is still not reconstructed by this later recording.

Local receipts are under `build/simulation-throughput/trade-planning-delay/`:
`capture.json`, `recording.jfr`, `lifecycle-derivation.json`, `lifecycle-report/`,
`lifecycle-analysis.json`, `rejection-and-stack-summary.json`, `rejected-cpu.json`,
raw performance/planning cuts and `stopped-verified.txt`.
No product behavior or safety oracle was changed to obtain the findings.
