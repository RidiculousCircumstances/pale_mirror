# Frontier v3 diagnostic plane

Status: accepted architecture and locally accepted `OBS-001` foundation at the
identity recorded in `CONTINUITY.md`. Its registered reasons, versioned envelope,
persisted incident index, restart-queryable trace and bounded identity-complete
bundle are reused by current F0.6R3 and later corrections rather than
reimplemented. The remaining missing capability is not another forensic store:
[`frontier-v3-runtime-verification.md`](frontier-v3-runtime-verification.md)
defines `OBS-002`, a separate bounded online verifier over these facts that can
detect silent non-progress and gate an incomplete product story. Neither layer
is a second simulation or canonical authority.

## Product outcome

An operator investigating a stalled worker, missing facility, inconsistent
hive, custody anomaly, restart ambiguity or server failure can start from the
affected world object and obtain one bounded causal account of:

1. what the object is doing now;
2. why it entered that state;
3. what was expected and what was observed;
4. which command, schedule, process, lease, physical intent and observation
   contributed;
5. whether the result is normal waiting, an owned domain disruption, a
   reconciliation conflict, recovery ambiguity or a software/infrastructure
   error; and
6. which owner will continue, inspect, repair, abandon or quarantine it.

This shortens diagnosis; it does not make diagnostics a second simulation,
persistence authority or acceptance oracle. A board, log, trace or green
summary cannot overrule canonical state or an observed physical contradiction.
`OBS-002` may evaluate explicit owner-declared progress obligations and reject a
test/promotion, but it does so as a read-only execution verdict; it never turns
diagnostic facts into world mutation or recovery policy.

## Execution-model inspection

For an exact subject, expose its canonical owner, process generation, job/action,
current stage and next owned transition, plus the independent presentation demand,
physical eligibility, actual custody and authority epoch. Distinguish current-state
projection, irreversible physical interaction and deferred aftermath; show desired
and observed revisions, confirmation/ambiguity and any retirement disposition.
Expose the relevant incoming/outgoing relationship chain from its canonical view,
not a second graph. Absent, unknown and inapplicable fields are explicit.

Expected stale or superseded unbegun work reports its registered disposition,
not a generic error or invented player conflict. A potentially applied effect
cannot be labelled superseded merely because its job ended. Genuine dangling
references/corruption remain visible failures with their actual isolation scope.
A next transition may be blocked or require inspection; diagnostics never invent
an ETA or promise progress when no continuation owner exists.

## Existing foundation and missing boundary

Frontier v3 already has typed commands/events, cause chains, transaction and
revision identity, scheduled actions, scene leases, physical intents and
observations, immutable projections, `PMV3_DIAG`, bounded recent traces,
scenario manifests and failure bundles. Retain and extend these mechanisms.

The missing boundary is semantic completeness and composition. A generic
`CONFLICT`, a textual exception or an independently calculated carrier boolean
does not say which owner failed, preserve the exact reason through restart or
connect the canonical and physical facts into one queryable account. The
diagnostic plane standardizes that account without copying ownership.

## Conflict incident core

F0.6R3 establishes one reusable conflict-incident contract and integrates it
completely for the resource-site/harvest path. A canonical owner may enter a
conflict state only through one accepted incident carrying:

- a stable immutable incident identity and the first causal source;
- one registered category/reason, exact owner and affected subject or semantic
  slot;
- bounded expected and observed facts, including the relevant claim, intent,
  lease, observation or restart epoch when applicable;
- canonical state/revision before and after the transition; and
- a typed disposition and responsible continuation, inspection, repair,
  abandonment or quarantine owner.

The canonical aggregate retains the stable reason, disposition and incident
link through snapshot/WAL/restart. The bounded diagnostic timeline retains the
larger causal chain; it is not copied into the aggregate. Repeated equivalent
observations may update bounded count/last-seen metadata but never replace the
first accepted source or silently change the reason. A physical adapter ledger
may mirror conflict only after the matching canonical incident is accepted; a
raw adapter flag without that incident cannot terminally stop canonical work.

One affected subject may have multiple incidents from different exact owners or
registered reasons. Automatically generated incident identities encode the full
nominal tuple with stable wire tags and unambiguous identifier boundaries;
subject identity alone is not incident identity. Explicit owner-retained incident
links remain immutable and reject reuse with a different tuple. The bounded
`why(subject)` index selects the latest retained observation by canonical revision,
instant and stable incident ID as tie-breaker. It is an explanatory projection,
not exclusive ownership or resolution of older incidents: each retained bundle
remains directly addressable, and unresolved terminal entries remain counted.
Repetition and optional compaction must refresh this lookup without dropping a
different retained incident's explanation. Persisted links are not rewritten
when the automatic identity generator changes.

Internal projection lag, a lawful lifecycle transition, player/world damage,
restart ambiguity, invariant failure and adapter/infrastructure failure are
different registered outcomes. An implementation-generated transitional
mismatch must not be persisted as player damage or a terminal reconciliation
conflict. Unknown or incomplete evidence remains typed and fail-closed without
inventing an expected or observed fact.

The current resource-site vertical must make `inspect why/trace/incident`, the
PMV3 correlation stream and the relevant carrier resolve the same incident and
first source after restart. OBS-001 later migrates the remaining producers onto
this same core; it does not redesign or weaken it.

## F0.6R3 incident-trace bootstrap

The current incident proved that missing observability is itself blocking the
product correction: an exact naturally HOT resident was present, while the
read-only actor query and motion observation emitted no admitted UUID, and the
expensive projection caller remained ambiguous after narrower hypotheses were
falsified. Before another equivalent native carrier, F0.6R3 establishes one
bounded usable vertical of this architecture for those exact paths:

- an actor query returns the exact current actor/UUID/physical-epoch ownership
  account or one typed owned reason for absence, incompleteness or routing
  failure; silence is never a result;
- the harvest lifecycle correlates command or schedule, process/assignment,
  HOT/COLD driver, lease/carrier, physical observation and canonical result
  under one stable subject/trace identity;
- `graybox-projection` work identifies its triggering owner/reason, cache or
  compilation disposition and complete bounded caller-path cost, so moving the
  same cost to another synchronous boundary cannot appear as a fix;
- the same immutable diagnostic facts feed the operator query and relevant
  carrier assertion without acquiring world authority or changing an outcome.

This bootstrap and the conflict-incident core are immediately used to diagnose
and close F0.6R3. They do not require the global producer inventory, migration
of unrelated producer families, remote export or their requalification.
OBS-001 later completes those remaining foundation criteria by extending,
rather than replacing, the accepted core and vertical.

## Three authorities, kept separate

| Plane | Authority | Retention |
| --- | --- | --- |
| Canonical causality | Domain events and owned process state decide world meaning, state and recovery. Any reason or disposition that affects behavior is retained here. | Snapshot/WAL and existing bounded canonical history rules. |
| Diagnostic timeline | A read-only, derived correlation of canonical transitions and adapter observations. It may index and summarize but cannot decide or repair. | Bounded per-world and per-subject recent history plus retained incident summaries. |
| Operational telemetry | Structured logs, metrics, JFR and optional external export explain runtime health and cost. | Configured bounded files, recordings and queues; never a correctness dependency. |

Loss of derived diagnostic detail is reported as an incomplete trace. It never
changes canonical truth. Conversely, a canonical conflict/recovery reason must
not exist only in a log line that can be rotated away.

Active and unresolved cause chains, plus terminal incident summaries awaiting
their declared disposition or acceptance review, are not evicted by the recent-
history ring. Their admission remains bounded: reaching the configured cap
must reject or degrade new optional tracing visibly rather than compact the
only explanation of an unresolved object. Once disposition and review close,
the normal bounded summary/compaction policy applies.

## Closed outcome classification

Every diagnostic terminal or non-progress result has exactly one category and
one registered stable reason code:

- `WAIT_OR_BLOCKED`: valid nonterminal inability to advance, with a next
  eligibility/retry/inspection policy;
- `DOMAIN_DISRUPTION`: expected gameplay such as death, theft, severance or
  obstruction that the smallest canonical owner handles;
- `RECONCILIATION_CONFLICT`: exact known disagreement between canonical owner,
  claim and observed physical state;
- `RECOVERY_UNKNOWN`: bounded ambiguity across a declared persistence or
  non-replayable physical boundary, awaiting exact inspection;
- `CANONICAL_INVARIANT_FAILURE`: impossible ownership, conservation or state
  transition requiring visible quarantine of the affected frontier instance;
- `ADAPTER_OR_INFRASTRUCTURE_ERROR`: implementation, codec, storage, thread,
  runner, transport or host failure that prevented a trustworthy result.

`CONFLICT` is therefore a classified domain result, not a synonym for an
exception. `RECOVERY_UNKNOWN` is legal only when the declared confirmation
boundary genuinely leaves evidence ambiguous. `ERROR` never substitutes for a
known player/world disruption, and a normal disruption never conceals an
invariant or persistence failure.

Expected work that is not selected, an owned actor that is not assigned or
adopted, and an expected body/facility that is absent are also non-progress
results. They must expose a typed owner and reason even when no exception or
state transition occurred. Silence cannot be the explanation for `farmer is
needed`, a missing organ or a policy with no downstream task.

## Reason registry

One closed versioned registry owns every diagnostic reason. Each entry has:

- a stable, explicit and never-reused wire tag;
- category, default severity and terminal/nonterminal meaning;
- permitted owner and subject kinds;
- required evidence fields;
- a recovery disposition and responsible owner;
- operator and, where applicable, player presentation keys;
- codec/version compatibility rules.

Unknown, duplicate, incompatible or under-specified entries fail closed.
Entering `CONFLICT`, `UNKNOWN_AFTER_RESTART`, quarantine or a terminal failure
without a registered reason and disposition is invalid. A process-specific
reason stays registered with that process family; the shared registry and
renderer do not accumulate concrete-family branches.

The authoritative producer stamps the complete nominal reason + category +
owner + typed subject + disposition tuple at the first admissible boundary.
The registry validates that exact tuple and its required evidence; it never
discovers or fills a dimension from exception class/message, generic state,
identifier spelling, collection membership, payload shape, current world
context, a sole compatible candidate or a default. Deterministic classification
after the fact is still inference. Legacy or incomplete retained facts report an
explicit unsupported/incomplete diagnostic result and cannot drive recovery or
an aggregate verdict. Human text is rendered only from a validated tuple and is
never parsed back into authority. Negative coverage removes or forges each
dimension independently and proves rejection before verdict or mutation.

## Diagnostic event envelope

Every retained diagnostic event uses one versioned envelope. Fields are present
when applicable and absent explicitly rather than synthesized:

- identity: diagnostic event, world, runtime, restart epoch and artifact/
  ruleset identity;
- causality: trace, parent event, cause chain, command, transaction, scheduled
  action and simulation instant;
- ownership: process/front, actor/team, facility, stable semantic slot, item/
  custody owner and physical scope;
- execution: process generation and job/action identity, independent presentation
  demand/physical eligibility, scene/custody/navigation lease and authority epoch;
  projection/interaction/aftermath kind, physical intent, observation and actual
  confirmation boundary;
- transition: canonical revision before/after, typed state before/after and
  source boundary;
- explanation: reason code, expected fact, observed fact, disposition,
  responsible owner and next transition/eligibility condition or explicit absence;
- runtime: bounded work count/duration and exception identity where relevant.

High-cardinality or sensitive values are not promoted to indexed labels by
default. Credentials, environment secrets and arbitrary player text are never
included. Exact player UUID may appear only where it is already the canonical
cause/owner required to explain an ordinary player action.

## End-to-end causal timeline

The common trace shape is:

```text
command or scheduled action
  -> process selection / typed non-selection reason
  -> COLD driver or HOT admission
  -> scene, custody or navigation lease
  -> physical intent
  -> Minecraft effect
  -> physical observation and confirmation
  -> reconciliation
  -> canonical transition, conflict, unknown or error
```

Not every operation uses every stage, but it cannot silently skip a stage that
its execution archetype requires. Numeric scenario indices and log order are
transport metadata, not causal identity. Parent/subject links must survive
unrelated action insertion, compaction and the restart claimed by the result.

Routine successful high-frequency samples may be aggregated. Semantic state
transitions, leases, durable intents, observations, confirmation boundaries,
conflicts, unknowns and errors are never sampling-only evidence.

## Read-only operator surface

Extend the existing bounded `/pale_mirror v3 inspect` capability rather than
introducing another mutable service. Its conceptual views include:

```text
inspect why <typed-subject-id>
inspect trace <trace-id>
inspect incident <incident-id>
inspect actor|site|operation|intent <typed-id>
```

`why` returns current state, the last causal transition, exact blocking or
failure reason, expected/observed facts, owner/disposition and a bounded parent
chain. `not_found`, `trace_incomplete` and `history_compacted` are explicit
results. Queries read one immutable checkpoint/projection, load no chunks,
submit no commands and perform no repair.

Human-readable console lines and machine JSONL are two renderings of the same
schema. Free-form log text is useful context but never the only copy of a typed
fact.

## Incident capture

Create one bounded incident bundle when any of these first becomes true:

- a terminal reconciliation conflict;
- a recovery unknown reaches its bounded inspection/escalation boundary;
- an invariant failure or quarantine occurs;
- a watchdog, persistence, adapter or scenario failure prevents a result;
- a configured whole-path performance threshold is exceeded.

The bundle contains the affected subjects' bounded causal window, current
immutable projections, expected/observed physical summaries, relevant claim/
intent/receipt identity, world/source/tree/JAR/ruleset identity, restart epoch,
bounded structured-log context, performance counters and a JFR reference when
one exists. It excludes world copies, unbounded logs, secrets and unrelated
objects. Repeated equivalent incidents increment count/first/last fields rather
than producing unbounded duplicates.

CI workers retain compact bundles on failure and merge them by exact build,
contract, worker, world and run identity. A bundle is diagnostic evidence; it
does not turn an unexecuted or failed semantic lane green.

## Truthful summaries

Scenario carriers and aggregate reports derive their verdict from typed
terminal facts. They may not maintain a second manually calculated truth.

- A required subject in terminal conflict, error, quarantine or unresolved
  recovery unknown cannot yield a green result.
- A terminal state without its required registered reason/disposition is red.
- A missing required causal stage or a truncated/wrong-subject chain is red or
  explicitly incomplete, never inferred from an endpoint.
- Snapshot/WAL/restart must retain the same cause and disposition where the
  canonical state survives.
- A human-friendly summary cannot contradict diagnostic facts nested in the
  same manifest.

The rejected F0.6R3 candidate `ba482535` is the initial regression case: a
physically complete field accompanied by canonical site `CONFLICT` must reject
`restartContinuity`, expose the exact reason/disposition and retain its causal
path across restart.

## Bounded performance

- No world-wide scan, chunk force-load or synchronous external/file export may
  run on the Minecraft server thread.
- The server thread records only bounded semantic facts and counters needed by
  the current work set; formatting/export is isolated from tick authority.
- Per-world/per-subject buffers, persistent incident summaries, queue bytes,
  file count/age and bundle size all have declared bounds and compaction.
- Critical semantic facts are not dropped. Optional verbose samples may be
  sampled or rate-limited only with an exposed dropped count and trace-
  completeness result.
- Performance instrumentation measures the complete caller path. JFR may carry
  configurable duration/counter events; it does not carry canonical truth.
- OpenTelemetry/OTLP is an optional adapter over the stable envelope. External
  collectors are never required for server correctness, recovery or local
  diagnosis.

## Foundation acceptance

The first bounded implementation slice is complete when:

1. all current Frontier v3 producers of terminal conflict, recovery unknown,
   invariant quarantine and adapter/infrastructure error are inventoried and
   mechanically require registered reasons and dispositions; current producers
   of expected-but-absent selection, assignment, adoption and physical
   materialization also expose a typed non-progress reason;
2. every producer supplies a complete nominal reason/category/owner/typed-
   subject/disposition tuple, and the reason registry/envelope reject duplicate
   or unknown wire tags, every independently missing/forged dimension, wrong
   combinations and incompatible schema versions without candidate scanning,
   exception/message parsing or fallback classification;
3. at least one scheduled process crosses the complete applicable COLD/HOT,
   lease, intent, observation, reconciliation and restart chain under one
   queryable trace, while a player-caused conflict proves the independent
   ordinary-action path;
4. canonical cause/disposition round-trip through snapshot/WAL and remain the
   same after the relevant restart; derived trace loss remains explicit;
5. a required underlying conflict makes the aggregate/carrier fail, including
   the `ba482535` contradiction class;
6. one representative conflict/error automatically produces a bounded,
   identity-complete incident bundle and equivalent repeats do not grow without
   bound;
7. `inspect why/trace/incident` is read-only, bounded and useful without chunk
   loading or canonical mutation;
8. focused registry/codec/query/negative checks precede one applicable
   integration milestone gate; no native matrix is repeated merely to prove
   the observability infrastructure itself; and
9. instrumentation remains within the existing Frontier tick, storage and
   retention budgets, with optional export disabled by default.

This foundation does not require Grafana, a collector cluster, remote telemetry,
full-world replay, a UI dashboard or re-proving every accepted gameplay family.
Those are later operational choices justified only by demonstrated need.
