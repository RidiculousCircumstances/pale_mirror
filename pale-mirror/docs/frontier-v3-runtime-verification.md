# Frontier v3 runtime verification and debugging acceleration

Status: accepted architecture; the resource-site harvest vertical is required
inside active F0.6R3 revision 22, and reusable completion is `OBS-002` before
`ARC-001E` and further MAT breadth.

## Outcome

Pale Mirror must detect, explain and preserve a reproducible account of a
semantic stall or divergence before a human player has to notice it. A
duration-bearing process that claims to be active either advances through its
declared semantic checkpoints within bounded simulation time, reaches a typed
terminal/successor result, or exposes an exact owned wait, obstruction,
ambiguity or failure. Silence, a changing wall clock, a stationary body beside
an `ACTIVE` board or a client timeout is not an explanation.

The development outcome is one short path from the first violated obligation
to a deterministic synopsis, bounded incident bundle and one-command replay.
This improves diagnosis without introducing another simulation owner, another
relationship graph or an external service required for correctness.

## Four planes and their authority

| Plane | Responsibility | Authority |
| --- | --- | --- |
| Canonical simulation | Events, owned process state, schedules, relationships, custody and recovery. | Sole authority for world meaning and mutation. |
| Diagnostic causality (`OBS-001`) | Versioned reasons, causal envelopes, exact-object traces, incidents and bounded forensic bundles. | Read-only derived view; canonical reasons remain in their owner. |
| Runtime verification (`OBS-002`) | Evaluate owner-declared safety and progress obligations over the diagnostic event stream and reject incomplete evidence. | Read-only verdict about an observed execution; never advances, repairs or reclassifies canonical state. |
| Operational telemetry | Metrics, structured logs, bounded operation traces, JFR and optional OTLP export. | Runtime health/cost evidence only; loss or exporter failure cannot affect simulation. |

`OBS-002` consumes the accepted `OBS-001` envelope. It does not copy state or
invent reasons. A violation may create a diagnostic incident and fail a test or
candidate promotion, but it cannot mutate gameplay. In a live non-test world it
alerts and captures evidence; the canonical owner alone decides recovery.

## Semantic progress obligation

Every registered duration-bearing execution descriptor supplies a bounded
read-only obligation when its subject becomes eligible or advances. The
obligation contains:

- stable rule/schema identity and exact subject, owner, process generation and
  required participant identities;
- baseline canonical revision, `SimInstant`, semantic fingerprint and the
  exact schedule/action/lease/intent binding when applicable;
- one or more satisfying next checkpoints, terminal receipts or declared
  successor facts;
- the owner-declared cadence/deadline in simulation ticks and any bounded
  grace justified by the execution archetype;
- the complete permitted typed `WAIT_OR_BLOCKED`, `DOMAIN_DISRUPTION`,
  `RECOVERY_UNKNOWN`, conflict or failure dispositions; and
- restart and evidence-completeness policy.

The verifier maintains O(1) state per active obligation and a declared bounded
active set. It evaluates domain progress against canonical simulation time.
Wall time is used separately for infrastructure health such as a stalled JVM,
lost client or unacceptable TPS; it never decides that canonical work should
have completed.

For a finite observation the verdict is one of:

- `SATISFIED`: an exact permitted checkpoint or terminal/successor fact was
  observed with a complete causal chain;
- `VIOLATED`: a safety rule failed or the canonical deadline was crossed with
  neither progress nor a permitted typed disposition;
- `PENDING`: the obligation is live and its deadline has not been crossed; or
- `INCONCLUSIVE`: the run ended, restarted incompatibly or lost/truncated
  evidence before a sound verdict. `INCONCLUSIVE` never promotes a candidate.

This finite-trace distinction prevents a short successful-looking prefix such
as field stage 0 -> 3 from standing in for a required terminal harvest.

## Required generic monitors

The first implementation may be vertical, but the contract is family-neutral:

1. every eligible nonterminal process has one next due action or a typed owned
   non-progress disposition;
2. a due action has exactly one declared owner and is consumed at most once;
3. a HOT or COLD checkpoint advances the same process generation/cursor and
   preserves required identities;
4. `PREPARED -> RUNNING` and irreversible attempts retain their exact
   eligibility, lease/intent and observation binding;
5. every irreversible attempt reaches exactly one confirmed, ambiguous,
   abandoned or owner-local-conflict disposition;
6. a physical lease checkpoints/releases within its declared canonical
   cadence, or reports why it cannot;
7. player-facing pending/active/progress text agrees with the exact canonical
   and physical facts; a label or moving body alone is not semantic progress;
8. HOT/COLD handoff and restart preserve continuation identity, due time,
   progress fingerprint and allowed next transitions; and
9. canonical/physical divergence is detected at its first observable boundary,
   not only at final aggregate comparison.

The authoritative process descriptor declares the legal vocabulary and
cadence. The verifier implements an independent monitor over emitted facts; it
must not call the production reducer as its oracle or reproduce family policy
through concrete-family branches.

## Session-scoped semantic stream

The test pilot or an operator-owned diagnostic session may subscribe to one
bounded local stream of the existing versioned diagnostic envelopes. Each
record includes monotonic event sequence, trace/cause identity, exact subject,
owner, revision, `SimInstant`, semantic milestone, schedule/lease/intent/
observation links, artifact/world/restart identity and completeness metadata.

The observing client is also an explicit physical input to the run. Before an
evidence-bearing interval, the pilot records its exact body/clearance volume and
proves that the chosen observation pose does not overlap any current or
declared route, station, interaction or effect envelope. If the observer later
creates a collision, demand or visibility change that can affect the subject,
the verifier classifies the run as `HARNESS_INTERFERENCE`/`INCONCLUSIVE` with
the exact overlapping envelope; it cannot attribute that blocker to the
product or silently retry from another pose. Camera relocation is a new
declared scenario input and requires a fresh bounded observation interval.

The stream is transport, not authority. It never force-loads chunks, performs
world scans or blocks the Minecraft server thread on formatting, disk, network
or a subscriber. Optional samples may be dropped only with an explicit gap and
drop count. A gap affecting an active obligation yields `INCONCLUSIVE`, never a
green result. Critical transitions and violation facts follow the accepted
diagnostic retention rules and are not sampling-only evidence. Raw server text
may accompany an incident but is not parsed into semantic truth.

### Live developer view

The ordinary developer consumer is a change-only semantic view, not a raw log
tail. It can attach before or during a native story, discover the current exact
subject through authoritative relationships and follow that subject across
process generations and restart. It emits a compact record only when an
obligation, milestone, owner binding, typed disposition, evidence-completeness
state or operational health state changes; it never emits one line per tick.

At any point the consumer makes these facts available without manual grep:

- current exact subject, process generation, participants and owner;
- last satisfied semantic checkpoint and its revision/`SimInstant`;
- exact next permitted checkpoint or terminal/successor fact and its deadline;
- current schedule, lease, intent and observation binding;
- a lawful typed wait/block disposition, or the first missing/divergent edge;
  and
- stream sequence, gap/drop state and artifact/world/restart identity.

The first `VIOLATED` or terminal `INCONCLUSIVE` verdict atomically updates one
session-local latest-incident pointer, freezes the capsule and emits one
deterministic synopsis containing the incident ID, affected product promise,
exact subject and owner, last progress, expected next fact, first divergent
boundary and the next bounded diagnostic entry point. A reconnect resumes from
the last acknowledged sequence or reports an explicit gap; it must never make a
fresh healthy-looking stream that hides lost history.

This view is the primary realtime interface for Terra and the graphical pilot:
an agent can start one follow session and receive an actionable incident packet
without polling text logs or reconstructing state from chat. The monitor and
capsule remain the acceptance mechanism; merely watching a changing stream is
not evidence of progress. Concrete command names, rendering and transport are
implementation choices, but status, follow-current-subject, latest-incident and
explain-incident capabilities are mandatory.

## First-failure incident capsule

The first violated obligation freezes one deduplicated bounded capsule. It
contains, where applicable:

- exact source/tree/JAR, ruleset/registry, world/seed, run/session and restart
  identity;
- rule ID, verdict, deterministic fingerprint and a short generated synopsis;
- exact subject/relationship view, baseline/current revision and simulation
  instant, expected and observed semantic fingerprints;
- the bounded causal event window and `why/trace/incident` result;
- schedule, lease, physical intent, observation, receipt and operator-command
  terminal facts;
- materially linked graphical frame(s) and their semantic camera assertion;
- a bounded JFR reference or thread dump only when runtime health/performance is
  relevant;
- replay recipe and checksum manifest; and
- visible gap, truncation, sampling and drop counters.

It excludes full world copies, unbounded logs, unrelated objects, credentials
and arbitrary player text. Equivalent incidents increment first/last/count and
attach a new bounded occurrence rather than creating unlimited duplicates.
Fingerprints use stable rule/family/owner/first-divergent-component/reason data,
not free-form messages alone.

## Defect identity, triage and closure

An incident capsule is immutable execution evidence; a defect is the stable
engineering problem that may own many occurrences. The local development index
groups capsules by the stable fingerprint above and retains:

- first-seen and last-seen build/world/session plus bounded occurrence count;
- affected product promise, severity and explicit canonical component owner;
- current state `OBSERVED`, `TRIAGED`, `FIX_CANDIDATE`, `VERIFIED` or
  `RECURRENT`;
- first divergent boundary and causal hypothesis, each marked as observed or
  inferred rather than blended together;
- fixing source identity, the exact regression/capsule replay identity and the
  product-shaped verification receipt; and
- remaining unverified scope and any superseding defect identity.

Routing uses the producer-declared owner and registered rule, never a message
substring, ID prefix or stack-frame heuristic. A recurrence reopens the same
defect and preserves its cumulative age/cost; a revision, new world or different
surface symptom does not reset it. `VERIFIED` requires the original strongest
contradiction to be rejected on the changed boundary and a retained regression.
Absence in a later run, a green aggregate or manual status editing cannot close
it. Human observations and automated violations enter the same index while
retaining their distinct evidence level.

The index is local and repository/runtime adjacent in the first delivery. It
may later export to an issue tracker, but no hosted tracker owns truth or gates
the simulation. Developer output for one defect is a compact agent-readable
summary plus links to its bounded capsules, not a concatenated log history.

## Monitoring and alert policy

Monitoring keeps three clock and authority domains separate:

1. canonical safety monitors reject an invalid transition immediately;
2. semantic progress monitors evaluate owner-declared obligations in
   `SimInstant`; and
3. operational monitors use wall time for tick latency, executor/queue lag,
   blocked threads, GC, I/O, client loss and exporter health.

Harness monitors additionally own client/body overlap, observer demand and
camera/input continuity. They do not reinterpret domain state: their only
semantic outcome is complete noninterference evidence or an explicit
`INCONCLUSIVE` harness classification.

Only an actionable state transition opens or updates a defect. Healthy samples
are aggregated; repeated equivalent failures are deduplicated; recovery emits
one resolution transition rather than a stream of success noise. An alert names
the exact owner, affected promise, first divergent boundary and next diagnostic
entry point. It does not merely say that a timeout, `CONFLICT` or nonzero counter
occurred.

The minimum bounded health surface includes active obligations by family and
verdict, oldest semantic age, scheduler/action backlog and lateness, active
lease/intent age, incident/drop/truncation counts, tick-latency distribution,
diagnostic queue depth and capsule/storage budget. Metrics use only bounded
dimensions; exact identities stay in event/capsule lookup. Thresholds and
budgets are versioned with the owning rules/profile so a binary update cannot
silently reinterpret an old world's health.

Monitor correctness is proven proportionately: deterministic fault injection
for silence, duplicate/out-of-order events, stream gaps, restart and queue
pressure; a normal-path noninterference check; and telemetry-on/off canonical
hash equality. This is not a second broad test matrix. Each monitor rule has one
positive and one discriminating negative/recovery example, then real incident
capsules become the bounded regression corpus.

## Deterministic replay and first divergence

A reproducible capsule retains the smallest compatible canonical checkpoint,
every external input and physical observation after it, stable scheduler order,
seed/PRNG state where used, and the exact build/ruleset/registry identities.
Replay reconstructs canonical decisions and consumes recorded results for
irreversible physical effects; it never repeats those effects in Minecraft.

Stable component digests are emitted at semantic boundaries. A replay or paired
HOT/COLD check locates the first divergent boundary and named component before
showing later fallout. Every fixed deterministic capsule enters a bounded
regression corpus. Stateful/model-based tests may generate and shrink action
sequences over load/unload, observer arrival/departure, acquire/checkpoint/
release, restart boundaries, delayed/duplicate observations and HOT/COLD
switches, but their reference model remains independent of production code.

Developer entry points must converge on an agent-readable packet rather than a
manual log-reading ritual. The concrete CLI is Terra's design, but it must
support the equivalent of: latest incident, follow one exact trace, explain an
incident, replay a capsule and report the first divergence.

## OTel, metrics and JFR

The stable Pale Mirror envelope and replay schema precede any telemetry vendor.
OpenTelemetry is an optional asynchronous projection:

- structured event/log: one semantic transition;
- bounded span: one operation with a start/end such as handoff, checkpoint,
  restart recovery or physical effect;
- metric: aggregate health/backlog/violation counts; and
- span link plus stable domain correlation identity: continuation across an
  asynchronous handoff or restart without one world-lifetime trace.

Metric labels are limited to bounded enums such as family, mode, phase, result,
reason class and environment. Exact process/world/player IDs, coordinates,
seeds and trace IDs belong in restricted events/capsules, not metric labels.
Exporter queues are bounded and off-thread; errors, violations, restart
ambiguities and stalls receive full retention, while healthy detail may be
sampled with visible counts. Collector failure or cardinality overflow is
observable but cannot affect canonical hashes, tick execution or recovery.

JFR uses a bounded continuous low-overhead ring for JVM, thread, lock, GC, I/O
and selected custom duration events. It is dumped on a semantic/performance
incident and correlated by stable IDs. JFR is not canonical history, and a
heavier profiling configuration is enabled only for a named bounded hypothesis.

## Executable promotion gate

A player-visible candidate may be offered for human audit only when its
machine-readable promotion receipt names every required obligation and proves
the complete product terminal, not a prefix. For the current harvest vertical:

```text
dynamic current field/job/worker discovery
  -> truthful first visibility
  -> canonical growth to maturity
  -> pending/admission
  -> continuous worker/cell progress
  -> terminal harvest receipt
  -> exact custody/production
  -> declared successor cycle
```

Every obligation is `SATISFIED`; `PENDING`, `INCONCLUSIVE`, a stream gap, a
failed frame assertion or a nested red diagnostic fact rejects promotion. One
passing preflight still does not infer unbriefed-player M3 acceptance.

## Delivery sequence and value measurement

1. Active F0.6R3 supplies the first real vertical: dynamic harvest discovery,
   simulation-time progress obligation, autonomous 6/7 detection, linked frame
   and terminal promotion receipt.
2. `OBS-002` extracts the family-neutral verifier, bounded stream, incident
   capsule and promotion gate and proves normal, lawful-blocked, silent-stall
   and restart/incomplete cases.
3. Add deterministic capsule replay, first-divergence digests and a retained
   regression corpus before broadening process-family adoption.
4. Add optional local OTel correlation and rolling JFR only after the semantic
   path works; no dashboard or collector cluster is an acceptance dependency.
5. Adopt obligations incrementally when a duration-bearing family changes or
   blocks a milestone. Do not launch an all-family native campaign.

Track value rather than the volume of telemetry:

- every admitted invariant/non-progress violation creates one capsule;
- deterministic incidents reproduce from one command at a measured rate;
- median incident-to-first-causal-boundary and incident-to-explanation;
- first-divergence localization success rate;
- zero change to canonical hashes with telemetry enabled/disabled;
- same-seed p99 tick/CPU/disk overhead within the declared budget; and
- every drop, truncation, overflow or incomplete trace is visible.

The target is a median causal explanation within five minutes for deterministic
simulation incidents and at least 90% one-command reproduction for eligible
capsules. These are measured engineering targets, not claims until evidence is
collected.

## Research basis

- OpenTelemetry defines correlated traces, metrics, logs/events and resource
  context; its log model carries event name, trace/span identity and source vs
  observed time: <https://opentelemetry.io/docs/specs/otel/logs/data-model/>.
- W3C Trace Context supplies interoperable causal correlation across boundaries:
  <https://www.w3.org/TR/trace-context/>.
- Temporal records bounded event history for recovery/debugging and requires
  deterministic replay of command history while recording nondeterministic
  side effects: <https://docs.temporal.io/workflow-execution/event> and
  <https://docs.temporal.io/workflow-definition>.
- Online runtime verification over partial/out-of-order streams motivates
  explicit satisfied/violated/inconclusive results instead of treating missing
  evidence as success: <https://arxiv.org/abs/1707.05555>.
- Prometheus warns against high-cardinality labels; exact subject identities
  belong in traces/capsules rather than time-series dimensions:
  <https://prometheus.io/docs/practices/instrumentation/>.
- Java Flight Recorder provides bounded continuous low-overhead recording and
  custom events suitable for after-the-fact JVM analysis:
  <https://docs.oracle.com/en/java/javase/21/jfapi/flight-recorder-configurations.html>
  and <https://docs.oracle.com/en/java/javase/21/docs/api/jdk.jfr/jdk/jfr/package-summary.html>.
