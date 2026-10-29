# Reactor on accepted trade-input changes

Status: completed 2026-10-29; [results and decision](frontier-v3-reactor-committed-flow-results.md).

Approved follow-up to the request-driven valuation experiment. This experiment
uses a read-only copy of a native 100-town snapshot and contiguous sealed WAL.
The real storage reader verifies framing/CRC; normal kernel recovery validates
accepted transaction envelopes, state and schedules. Notifications are derived
only after complete validated transactions, never from provisional trade drafts.
MAIN/SCALE code, configuration and worlds remain unchanged.

The implemented input descriptor is deliberately conservative: the complete
accepted immutable state image, canonical revision and tick. It does not infer
that the existing opportunity-advice rejection guards constitute a complete
dependency graph for a fresh trade calculation. All 512 accepted transactions
produce notifications for four sampled public participants; this is an offline
invalidation workload, NOT a claim that the installed scheduler emits these
same notifications. Actual owner projections/preparation run inside the real
calculation and are included in its cost. This experiment does not evaluate a
new narrower domain dependency descriptor.

The measured consumer is actual trade selection/reservation preparation up to
explicit missing route evidence. It cannot create a path, arrival, transfer or
sale. Ordinary current admission stays outside the experimental adapter.

Compare bounded plain Java and Reactor consumers over the SAME captured facts,
keys, worker budget, calculation and freshness rules. Reactor handles per-key
latest notification and serial work through `onBackpressureLatest`/`concatMap`;
it does not discover domain dependencies or replace canonical scheduling.
The reference control is a small comparison implementation, not a new production
reactive framework. One key has at most one in-flight calculation and one latest
pending notification. Canonical events themselves are never dropped or merged.

Use actual canonical tick boundaries as deterministic observation barriers;
also exercise delayed completion while accepted input changes arrive. Record
capture, execution, queue work, stale results and publication separately. Compare
every barrier result with a fresh direct call, including a full selected choice,
not only counters. The dependency descriptor must remain conservative and honour
the existing market/owner/geometry guards. A mismatch fails the experiment rather
than silently widening to an unrelated successful path.

Measure complete flow wall/thread CPU/allocation and actual calls, pending/active
peaks, coalesced notifications, errors and stale results. Attribute replay/setup
separately from measured scheduling plus real calculation. Rotate implementation
order after warm-up. An eager notification reference, if used, is labelled as
such; it is not a replica of the installed async scheduler. No result here is
native TPS or proof of completed caravan delivery.

The dispatch harness accepts each canonical tick's transaction burst before
draining a bounded host queue with two workers. Both adapters capture the latest
image when their queued work starts. This deterministic service policy makes
coalescing comparable; it does NOT reproduce live CPU completion interleavings.
Delayed-completion checks separately inject an accepted newer image after an
actual calculation's input capture. The captured workload covers absence of a
purchase and unresolved route evidence; it cannot prove a completed selection,
sale, owner retirement or production graph migration.

Measurements cover steady notification processing, immutable-input capture,
calculation, freshness checks and answer publication. Record decoding/replay,
adapter setup/close and independent direct-oracle comparison are outside those
timings. Worker CPU/allocation include Reactor callbacks as well as calculation;
caller CPU/allocation include input dispatch and observation capture. The
experiment's deep answer/geometry oracle is not production admission work.

Reuse the existing isolated `reactorExperiment` source set and pinned Reactor
3.8.8. No dependency enters production/test runtime or packaged artifacts. Verify
equal-value evidence changes, failure/retry, bounded dispatch,
close/cancellation and late result rejection. A conservative whole-frame experiment
does not prove narrower domain change/removal dependencies. Only a convincing whole-path result
would justify a separately scoped native integration. This task ends with an
evidenced library/architecture decision, not automatic adoption or deployment.

Local receipts: `build/simulation-throughput/reactor-committed-flow/`.
