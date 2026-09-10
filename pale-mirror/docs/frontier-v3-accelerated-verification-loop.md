# Frontier v3 accelerated verification loop

Status: accepted normative implementation brief for mandatory substage
`F0.VA` inside `F0.V`.

Execution follows `engineering-agent-protocol.md` and the ledger's active
versioned work order. The engineer owns requirements and independent acceptance;
Terra high alone implements and owns an explicitly granted local test run.
Finish normative input edits before preparation; do not compete with or mutate
inputs of an identity-bound measurement.

The player promise remains one autonomous world with shared foreground/background
rules, identities and history, not identical physical fidelity. Faster feedback
is valuable only when it finds violations of that promise earlier; it may not
replace ordinary Minecraft actions, real restart/crash boundaries or terminal
semantic evidence with mocks.

`F0.VA` interrupts further expensive `F0.V` matrix closure after the current
process reaches a safe boundary. Preserve the dirty `F0.1`/`F0.V` worktree and
all current timing and failure artifacts. Do not reset, rewrite or discard them.
The required order is:

```text
preserve current F0.V evidence
  -> F0.VA accelerated loop
  -> locally green F0.VA / history-preserving monorepo gate
  -> publish the reproducible four-worker workflow
  -> resume and close the original F0.V native/crash matrix
  -> observe CI timing during ordinary subsequent F0 work
  -> resume preserved F0.1
```

No gameplay, MAT breadth, new process family or unrelated refactor is admitted
during `F0.VA`.

At the next safe measurement boundary, perform the mandatory requirements
alignment in `frontier-v3-execution-semantics.md`. Retain all six features and
the current measurement artifacts; do not kill a run or rewrite its assertions
mid-measurement. The implementation plan owns the monorepo/provider order.
Comparator, assertion and semantic-contract versions are evidence-cache inputs:
invalidate affected acceptance when their meaning changes, and never promote
old green runs into proof of a stronger contract. Build reuse still follows its
actual content fingerprint. Exact projections and statistical comparisons must
remain distinct in selector coverage and merged CI reports.

## Outcome and measurement

User amendment, 2026-09-05: the graceful reference restart's 25-percent figure
is an advisory optimization target, not a minimum gate. Its retained measured
22.52-percent benefit is sufficient; do not spend native runs on that gap.
Retain exact sample identities, correctness/recovery gates and explicit review
of future regressions.

User amendment, 2026-09-06: numeric feedback and provider speedups are
observability signals, not release or slice gates. The retained 5.065026x local
measurement is sufficient to start using the loop. Do not run or repair a
standalone provider campaign solely to certify 3x or 2.5x. Collect comparable
timing passively from ordinary subsequent F0 work and review a regression when
it occurs. Correctness, isolation, exact identity, fail-closed evidence merge
and required recovery semantics remain mandatory regardless of timing.

User amendment, 2026-09-07: do not confuse an acceleration mechanism with a
correctness criterion. Provision and use four simultaneously available isolated
CI worker slots for each newly started complete native matrix so ordinary F0
work benefits from sharding, but execution topology does not change the matrix's
semantic acceptance rules. A valid sequential local matrix already in flight
may finish and satisfy its declared F0 evidence; never repeat it solely to prove
worker topology or speed. The four slots may share a host only after a bounded
capacity and isolation preflight proves separate work roots, caches, ports,
displays, worlds, process custody and evidence identities; otherwise use
separate hosts. Runner queue time and any measured speedup remain advisory.

Before implementation, retain the existing phase reports and add three
repeatable same-host workflow baselines:

1. a process-descriptor or pure-contract edit;
2. a scenario/semantic-assertion edit affecting one vertical;
3. a client/server lifecycle edit affecting restart execution.

For each class, measure from change classification to the first trustworthy
pass or attributable failure. Record selected tiers, cache status, build and
artifact identity, elapsed wall time and executed work. Compare three-run
medians against the former conservative path that runs all locally applicable
checks. Record the aggregate ratio without making it an acceptance threshold.
A cache hit alone is not proof: selection and invalidation negatives must
demonstrate why the hit is legal.

Also retain two distinct matrix measurements:

- sequential local native matrix with one client JVM kept for the matrix;
- four-worker isolated CI correctness matrix, excluding provider queue time.

When a comparable four-worker run and sequential sample arise from ordinary
work, record their wall-clock ratio. A dedicated timing job, if run, remains
separate, sequential and same-host and never runs under competing matrix load;
neither the sample nor any numeric floor blocks product work.

## Non-negotiable evidence boundaries

- Final slice evidence runs with `fresh-evidence` semantics: no cached pass may
  stand in for an execution required by the F0.V exit gate.
- Every native execution owns a fresh uniquely named disposable world. Only
  the before/after halves of one declared recovery execution share that world.
- Reusing a client JVM, prepared build or immutable bootstrap image never
  permits reusing canonical runtime state, a terminal assertion, a lease,
  scenario-local memory or a mutable world.
- A timeout is one attributable failure. Do not retry, lengthen it or convert
  it into a timing sample.
- A diagnostic capability required to justify an expensive run must be proved
  through the actual dispatcher/worker/child transport that will consume it;
  a green helper/unit test alone is insufficient. The run admission fails
  before Minecraft when its required observer, evidence root, identity binding
  or declared capture slots are absent, and result validation rejects a missing
  required artifact instead of reporting an unattributed product timeout.
- Coordination uses exact bounded protocol events. A fixed sleep is allowed
  only when elapsed gameplay time is itself the subject of the assertion, and
  the scenario must name and justify it.
- Local execution retains the existing limit of one visible client on
  `DISPLAY=:0`. Each isolated CI worker may own one private virtual display and
  at most one client/server pair at a time. Visual/M3 acceptance remains a
  separate real-display gate.
- Parallel correctness workers never contribute samples to same-host timing,
  JFR or performance acceptance.
- The harness remains outside canonical ownership. It may read bounded
  diagnostics and copy opaque test artifacts, but may not decode/edit NBT,
  mutate world files, force-load chunks or introduce a canonical mutation API.

## F0.VA.1 — one persistent client across the matrix

Extend the restart-session protocol from one scenario to one worker's complete
assigned native matrix. One prepared NeoForge client JVM must:

1. connect as the declared ordinary player to an exact server run ID;
2. clear transient scenario, target, screen, diagnostic and frame state;
3. execute one scenario segment through ordinary client actions;
4. publish the exact segment-complete nonce;
5. disconnect normally and publish an exact disconnect acknowledgement;
6. wait without owning canonical authority;
7. reconnect only after the next server publishes its exact run-ID readiness;
8. repeat for the next fresh world or the declared recovery half.

The server JVM remains fresh where the scenario or independent-world boundary
requires it. A client process exit, stale nonce, missing disconnect, retained
assertion, connection to a wrong run ID or cross-scenario target must fail the
worker and emit one bounded failure bundle. The supervisor may not infer a
disconnect from elapsed time or a closed window.

Prove at least three independent scenarios, including one two-server graceful
restart, through one client PID. Their manifests must show distinct worlds,
run IDs and cleared transient state while retaining one client/build identity.

Assigned-worker integration clarification: the three-independent-scenario
minimum is a proof requirement, not generic runtime admission. A worker runs
exactly its nonempty bounded immutable assignment, including a one- or two-world
shard; never pad or rebalance it to pass a smoke-protocol validator.

For a declared abrupt fault, normal-disconnect/save steps above are replaced
only at that fault boundary by authenticated expected-server-loss evidence:
the exact armed lane/segment/server and causal crash observation, termination
of that exact JVM, actual client connection-loss acknowledgement and closed
port. A planned crash is not normal disconnect or a successful terminal domain
result. Reconnect the same living client only after exact replacement readiness;
the recovery half supplies the terminal assertion. Unexpected server loss or
client death still fails the worker. No fresh-client exception, elapsed-time
acknowledgement, re-armed recovery half or silent skip is permitted. This
clarifies how existing abrupt correctness lanes compose with one client; it
does not weaken any crash boundary or certify the current implementation.

## F0.VA.2 — content-addressed evidence cache

Add a bounded local/CI evidence cache owned by the external test harness. A
successful entry key includes at least:

- exact source content fingerprint, including relevant dirty and untracked
  inputs rather than Git commit alone;
- server/client production artifact and classpath hashes;
- JDK, OS/architecture and runner/tool schema versions;
- process descriptor, codec/vocabulary and dependency-manifest hashes;
- vertical contract and generated scenario hashes;
- seed, profile, view distance, execution tier and fixture-image identity;
- test command and relevant environment policy.

The cache stores bounded manifests and proof references, never mutable worlds,
client state, secrets or an unbounded log. Failed, incomplete, quarantined,
timed-out or schema-unknown runs are never reusable successes. Missing key
fields, corrupt entries and a hash mismatch fail closed.

Add a checked-in dependency manifest mapping owned source/test/tool inputs to
their pure, codec, GameTest, native, crash and package gates. The selector must
explain every selected and reused result in a machine-readable execution plan.
An unknown or multiply owned changed path widens to the conservative gate; it
never guesses a narrow path. Changes to generic lifecycle, persistence,
scenario semantics, client/server launch or dependency-selection code
invalidate every affected downstream vertical.

The final F0.V closure command ignores cached successful execution evidence,
while still reusing the exact already-prepared build identity within that one
fresh run.

## F0.VA.3 — enforced test pyramid and selector

Make the existing iteration ladder executable rather than advisory:

| Tier | Purpose | Normal use |
| --- | --- | --- |
| `T0` | schema, static guards and scenario composition | every edit |
| `T1` | focused pure Java, Node, reducer/scheduler/codec contracts | every affected owner; warm target remains at most 15 seconds |
| `T2` | smallest matching GameTest batch | physical seam or NeoForge integration change |
| `T3` | one smallest generated native variant | changed causal/player/restart boundary |
| `T4` | full required native/crash matrix and critical/package gates | slice commit or explicit fresh-evidence request |

The dependency selector consumes the changed-content fingerprint and emits a
deterministic plan before executing work. It must reject a request to skip a
required lower tier, explain conservative widening and support an explicit
`fresh-evidence` mode. Seeded negative tests must show that changes to a
generic SDK assertion, payload codec, HOT executor, restart runner and package
boundary select every dependent tier. A test-only formatting edit may remain
narrow only when the dependency manifest proves it has no semantic consumer.

Never report `T0`/`T1` as Minecraft evidence, `T2` as ordinary-player evidence,
or a cached `T3` as current final-gate evidence.

## F0.VA.4 — exact event-driven barriers

Replace coordination sleeps and ambiguous log polling with a versioned bounded
control protocol. It must carry the exact build identity, worker ID, run ID,
scenario ID, segment ID and nonce where applicable and expose at least:

- prepared client ready;
- exact server run-ID ready;
- client connected and fixture ready;
- action/checkpoint or frame barrier acknowledged;
- scenario segment complete;
- client normally disconnected;
- durable server-save marker and game port closed;
- recovery server ready;
- same client reconnected with transient state cleared;
- terminal assertion complete.

Each state transition is monotonic, one-shot and timeout-bounded. Duplicate,
out-of-order, stale-run or foreign-worker messages fail closed and are covered
by protocol tests. Log text may remain diagnostic evidence but cannot be the
sole synchronization authority when a typed acknowledgement is available.
The outer scenario watchdog reserves every declared bounded action window,
including canonical fast-forward completion, and rejects an aggregate beyond
its explicit safe ceiling rather than silently consuming a later action's
timeout.

Audit every F0.V scenario wait. Replace coordination delays with the matching
barrier. Retain only declared gameplay-time waits, such as a real hysteresis
window, and prove they cannot be shortened without changing the product rule.

## F0.VA.5 — four isolated GitHub Actions workers

Extend `.github/workflows/build.yml` with a provider-visible, reproducible
correctness pipeline:

1. one preparation job checks out the exact source, runs fast static/pure
   guards, prepares and fingerprints artifacts, and emits the deterministic
   native execution plan;
2. a matrix job shards the plan across four isolated workers with
   `strategy.max-parallel: 4`;
3. each worker owns a private temporary root, Gradle/user cache namespace,
   world namespace, port allocation and virtual display, and runs no more than
   one normal NeoForge client/server pair at once;
4. each worker uploads bounded manifests, traces, failure bundles and the exact
   build/plan identity even on failure;
5. one merge job rejects missing, duplicate, foreign-build, stale-contract or
   semantically incomparable lanes and produces the complete matrix report;
6. one separately serialized timing job runs only on a pinned same-host runner
   with no matrix contention and compares three-run medians.

Correctness shards may use isolated GitHub-hosted Linux workers with one
validated virtual display each. If the native stack cannot run faithfully
there, label and use equivalent self-hosted `pm-native` workers; do not silently
replace the normal client with a server command or mock. The pinned timing host
must be self-hosted and identified in its report. Provider credentials and
runner provisioning are operational configuration, but the checked-in shard,
merge, artifact and fail-closed workflow is part of F0.VA.

Shard assignment is deterministic from the sorted execution identity and
declared weight, not completion order. Crash lanes and ordinary lanes use
independent worlds. Cancellation must still upload completed evidence and mark
the aggregate incomplete. No shard is retried automatically.

Any actual four-worker workflow run used as correctness evidence must prove
complete coverage. Queue time is reported separately and is not hidden inside
execution speed. An actual provider timing result is not required to close
F0.VA; it is gathered opportunistically from later ordinary work rather than by
repeated standalone certification runs.

Historical F0.VA work was allowed to record unavailable provider execution as
`UNCONFIRMED_EXTERNAL` after completing the checked-in workflow, deterministic
shard/merge CLI and local protocol tests. For subsequent newly started complete
native matrices, provision the bounded four-slot capacity, execute the exact
candidate through the sharded pipeline and retain its aggregate and lane
artifacts. Provider timing remains optional. A valid sequential matrix already
running when this amendment was accepted retains its ordinary semantic value;
do not duplicate it merely to change execution topology. Report unavailable
access or capacity as an infrastructure defect to correct before the next new
full matrix, not as grounds to weaken or expand a current slice's semantics.

## F0.VA.6 — immutable development fixture images

Add an optional development-only bootstrap-image cache for scenarios whose
claim begins after ordinary deterministic world/profile creation. The image is
created only by a normal server bootstrap followed by its durable clean stop.
Its manifest binds source/artifact/classpath, snapshot/WAL schema, ruleset,
seed, profile, view distance, fixture declaration and a complete content hash.

Every run receives a new copy-on-write/reflink clone when supported, otherwise
a verified bounded copy, under its own disposable world name. The harness
treats the image as an opaque directory: it never reads, edits or repairs NBT,
region, snapshot or WAL contents. The server alone mutates the fresh clone
after launch. Hash mismatch, an incomplete stop marker, schema drift or an
existing mutable consumer invalidates the image and performs a normal
bootstrap instead.

This fast path is permitted only for `T2`/development `T3` claims that do not
test bootstrap, initial recovery or world generation. `fresh-evidence`, timing,
bootstrap, persistence-format, packaged-JAR and final F0.V closure runs must
bypass it. Manifests state whether an image was used so it cannot be mistaken
for fresh-world evidence.

## Implementation order

Implement the six capabilities in this dependency order while retaining their
numbered acceptance requirements:

```text
F0.VA baseline
  -> F0.VA.4 exact lifecycle/barrier protocol
  -> F0.VA.1 persistent matrix client
  -> F0.VA.2 cache plus dependency manifest
  -> F0.VA.3 selector and enforced pyramid
  -> F0.VA.5 CI sharding and evidence merge
  -> F0.VA.6 optional development fixture images
  -> measured local F0.VA gates
  -> history-preserving monorepo and verified remote push
  -> engineer F0.VA acceptance
  -> resume original F0.V matrix closure
  -> gather provider timing from ordinary subsequent work
```

The barrier protocol comes first because caching or distributing a racy runner
would only make failures faster and less attributable.

## F0.VA exit gate

`F0.VA` is complete only when:

- all six capabilities above have normal, negative and failure/recovery tests;
- one client PID executes three independent native scenarios including one
  real two-server graceful restart with exact disconnect/reconnect barriers;
- cache and selector tests reject stale, dirty-content-mismatched, corrupt,
  failed and under-selected evidence;
- the fresh-evidence command bypasses cached successes and development fixture
  images mechanically;
- no coordination sleep remains in F0.V unless gameplay elapsed time is the
  named assertion;
- four-worker workflow composition and merge tests reject duplicate, missing,
  stale, foreign and incomparable lanes; newly started complete native matrices
  use four simultaneous isolated worker slots and produce one complete
  exact-identity aggregate, while a valid matrix already in flight when the
  requirement was adopted is not repeated solely for topology; no provider
  timing result or numeric speedup floor is required;
- the representative iterative-workflow timing is recorded as advisory
  evidence; the retained 5.065026x local result is sufficient for F0.VA;
- the graceful-reference optimization has accepted measured benefit (the
  retained22.52 percent is accepted;25 percent is advisory), without waiving
  current-source correctness or recovery evidence;
- focused tests, guardrails, check and packaged-JAR fixture-absence checks pass;
- bounded evidence records exact inputs, cache/fixture decisions, worker/run
  identities and failures without retaining mutable worlds as success cache;
- the architecture audit and Continuity Ledger record the measured evidence
  and explicitly return current work to the remaining original F0.V exit gate.

Only after this gate and the ordered history-preserving monorepo/publication
boundary may the engineer authorize further full-matrix time closing the
remaining original F0.V variants and crash windows. Historical
`UNCONFIRMED_EXTERNAL` records why F0.VA lacked provider execution. It does not
invalidate a semantically complete sequential F0.V matrix that was already
running when the acceleration amendment was adopted. F0.1 remains paused until
the original F0.V exit gate, not merely F0.VA, is complete.

## Pulled-forward checkpoint: F0.VC prepared native pipeline

The original sequence activated this checkpoint after F0.2B acceptance. By
explicit user authority on 2026-09-08, the shared mutable-run collision in the
failed F0.2B native attempt pulls it forward at a safe incomplete F0.2B pause.
The mandatory
[`PM-F0VC-PREPARED-NATIVE-PIPELINE-01`](work-orders/PM-F0VC-PREPARED-NATIVE-PIPELINE-01.md)
checkpoint extends the accepted loop without reopening F0.VA, F0.VB, F0.V or
F0.1 and without accepting F0.2B. The required dependency order is:

```text
paused F0.2B lineage and preserved evidence
  -> early deterministic semantic/preparation preflight
  -> one exact immutable prepared runtime producer
  -> content-addressed immutable inputs and private COW worker views
  -> four simultaneous isolated consumers
  -> checked compatible-case JVM reuse inside one shard/world
  -> always-retained per-worker evidence and fail-closed aggregate
  -> zero task-owned runner/process/credential state
  -> engineer F0.VC acceptance
  -> resume and complete the same F0.2B order
```

The producer binds source, production/test classpaths, packaged JAR, JDK,
platform, dependencies, workflow/tool and selected scenario identities.
Consumers verify that identity and must not compile, transform or package the
candidate independently. Cache reuse covers immutable preparation only, never
worlds, canonical runtime state, terminal assertions or fresh-evidence success.
Every independent shard/lane still receives its own disposable world and
mutable namespace. Only explicitly compatible non-restart test cases inside
that one shard/world may share one living JVM after an exact reset
acknowledgement. Bootstrap, graceful restart, abrupt crash and save-boundary
claims retain their required process boundaries.

Structural plan, identity, compatibility, namespace, capacity and expected-
artifact failures must stop before a Minecraft launch. Success, failure and
cancellation must each preserve uniquely scoped bounded evidence, and merge
must always return a complete or explicit incomplete result. The existing four-
slot topology and semantic coverage remain unchanged. Record phase timings from
the ordinary qualification but do not create a numeric speed floor or repeat a
product matrix only for benchmarking.

## Post-acceptance verification-economy policy

F0.VC is reusable accepted infrastructure, not a recurring proof campaign.
Requalify its own build-once/run-many, isolation, reuse or merge mechanics only
when their relevant inputs/contract change or retained evidence is concretely
contradicted. Product work consumes the capability without reopening it.

For every product candidate, all changed or previously failing native lanes
must pass a faithful local preflight/regression before a complete four-worker
dispatch. A matrix failure is retained once and diagnosed locally; do not send
another complete matrix until the exact failure has a smallest reproducer, a
new testable hypothesis and a passing candidate-specific regression. If the
failure exists only in provider admission or identity, use the smallest bounded
provider preflight that stops before Minecraft. Do not use a full product matrix
as the probe.

One complete green terminal matrix with coherent identity, evidence and cleanup
proceeds immediately to independent review. A second green run for confidence,
timing, worker topology or already-accepted infrastructure is prohibited. A new
run is justified only by a review correction that changes the candidate or its
applicable comparator/evidence, and then covers only the invalidated boundary.
The supervising engineer enforces these dispatch rules and rejects redundant
expense without selecting Terra's implementation or increasing polling.

Raw evidence roots are garbage-collected after terminal review. Durable proof
is the compact identity/outcome/checksum recorded in the work order and, when
needed for an unresolved claim, one minimum bounded artifact—not every checkout,
cache, runner installation, runtime copy, world and log that produced it. Keep
at most one local-heavy and one provider root for the active order and keep all
Pale Mirror raw proof roots below 64GiB absent a named user-approved exception.
The supervisor must account and reclaim storage before authorizing another
heavy lane; evidence retention may not become a hidden proof campaign.

Every candidate therefore has exactly this maximum proof path: deterministic
localization, changed/failed-lane local regression, at most one runtime-relevant
physical preflight, one four-worker matrix, then review. The first terminal
physical result and first terminal matrix result exhaust those grants for that
candidate identity. A failed boundary may be repeated only for a changed
candidate/hypothesis after its cheap faithful discriminator passes.

Heavy proof of the same acceptance claim must not span two consecutive hourly
economy audits or exceed four cumulative execution hours after the first heavy
attempt without a safe-boundary stop and explicit work-order reset. Queue time
is accounted separately. At that circuit-breaker the engineer must state what
new product information another run can obtain; absent a discriminating answer,
the run is prohibited. This is the categorical guard against turning evidence
infrastructure into a multi-day product-development substitute.

### Hourly execution-economy observation

While a work order is `EXECUTING`, its bounded chronology records each stable
milestone, material failure and heavy completed operation with purpose, known
start/end or elapsed duration, outcome and evidence handle. The supervising
engineer reviews that sequence after each complete hour from the recorded epoch.
The review asks whether time advanced a product claim, whether the cheapest
faithful discriminator preceded native/CI work, whether an equivalent failure or
accepted capability was repeated, and whether applicable four-worker/prepared
execution was used instead of avoidable serialization.

This is cost-method supervision, not implementation supervision. It uses event
metadata and retained receipt timing summaries, not WIP source/diffs, and creates
no hourly executor heartbeat. A healthy review is recorded without contacting
Terra. A concrete pattern of avoidable cost, repeated no-new-evidence work or an
inadequate expensive test permits one consolidated `ECONOMY_AUDIT` correction
that states the evidence, cost and required product/verification outcome while
leaving diagnosis and remedy to Terra. It neither reruns accepted infrastructure
nor changes the ten-minute liveness cadence.

## Mandatory causal evidence instead of timing luck

Binding user decision, 2026-09-08: apply now within the active F0.2B correction
and to later changed causal scenarios. This is not a new infrastructure
qualification or a blanket rewrite of unchanged accepted tests.

1. **Facts, not timing luck.** Elapsed wall time, fixed sleeps, disappearing
   transient status or cleared request fields cannot establish domain success.
   Bounded wall-clock deadlines remain failure/liveness guards, never success
   criteria; render settling is presentation-only. Exact canonical simulation
   instants and engine-owned due ordering remain valid domain inputs/assertions.
   An absolute-time request needs attributable admission and a confirmed reached
   instant/result. Distinguish completion, rejection/cancellation and missing
   acknowledgement; `target=null` or `remaining=0` alone is not completion.
2. **Retained causal history.** Short-lived admission, reservation, effect and
   custody transitions must remain provable after completion and across the
   claimed restart. Reuse bounded correlated traces/events/receipts where
   possible. Bind facts to the exact run, world, operation, object and applicable
   epoch/revision. Missing, truncated, stale or wrong-subject evidence must not
   pass. Do not require the polling client to catch a transient state to prove
   that it occurred. Evidence remains read-only and bounded, never a second
   schedule, resource/custody authority or production history store.
3. **Semantic references.** Assertions use named causal milestones such as
   admitted/reserved, effect-confirmed and released, with explicit subject
   identity. Unrelated action insertion must not silently rebind an assertion.
   Numeric indices may remain transport/diagnostic metadata, not the sole
   semantic identity. Intermediate snapshots cannot stand in for terminal facts.
4. **Authentic recovery boundary.** Declare the acquire/checkpoint/release/effect
   boundary crossed by restart and the confirmed versus in-flight facts there.
   Establish it by a correlated barrier, not a delay. Moving restart past
   completion does not prove recovery of in-flight work. Explain any required
   coverage change to the engineer; never weaken it silently. Test-only
   synchronization cannot fabricate domain transitions or suppress ordinary
   custody/progression needed by the flow under proof.
5. **Cheap faithful tests first.** Verify transition details, protocol/comparator
   negatives and observation sequencing at the smallest pure/component/runner
   tier. Native Minecraft retains necessary real adapter, chunk/container and
   physical-save/restart proof; synthetic traces alone cannot replace it.
   Repeated sequencing failures require a coherent check of the affected
   scenario's causal dependencies, including remaining steps, before another
   expensive attempt. Add the smallest regression for the classified defect,
   not a general framework or timing campaign.

Acceptance at the changed boundary includes applicable deterministic negatives
for missing, stale/wrong-subject and reordered evidence, discoverability of an
already completed short-lived transition without timing-sensitive polling, and
semantic-reference stability under unrelated action insertion. The real restart
lane must retain its declared causal coverage. Reuse existing mechanisms; any
extension is limited to missing evidence. Public/persistent meaning changes
remain architecture decisions. Do not add unrelated exhaustive campaigns.
Changed/failed lanes pass locally before the single complete four-worker matrix
and independent review; accepted F0.VC is not reopened by this policy.

## Mandatory methodological soundness review

Binding user decision, 2026-09-08: the supervising engineer is accountable for
whether the verification method can establish the claim, in addition to
checking that commands passed. Apply the review when defining a work order and
again to the terminal evidence. If the executor materially changes the proof
carrier, oracle or claimed scope, treat that change as one bounded `RISK`
boundary before its expensive execution; do not turn ordinary local iterations
into recurring approval stops.

The work-order review is a real pre-execution method gate, not a promise to
inspect adequacy only after results exist. Before any expensive native/CI lane,
state why its scenario represents the product claim, which plausible defect its
oracle discriminates, why fixtures cannot create the claimed outcome, which
evidence tier is required and why a cheaper faithful test cannot finish the
claim. If those answers are missing or contradictory, repair the framing or
narrow the claim before spending the run. Terra remains autonomous over the
concrete test implementation inside an already sound framing.

For every material acceptance claim, establish all of the following:

1. **Construct validity.** The observed subject and terminal fact are the
   promised product behavior, not a scenario label, setup state, proxy endpoint
   or implementation detail. State explicitly when evidence supports only a
   narrower claim.
2. **Controlled comparison.** Histories being compared have equal relevant
   canonical inputs, rules, due actions and intervention conditions. A fixture
   may establish read-only preconditions but cannot create the transition or
   outcome under proof. The oracle must not merely read back a value written by
   the same test-only authority.
3. **Causal discrimination.** Evidence binds the exact subject and candidate
   through the relevant transitions, and the test has an applicable negative,
   recovery case or seeded counterexample that would fail for the plausible
   defect being excluded. Missing, stale, reordered or wrong-subject evidence
   cannot accidentally satisfy the oracle.
4. **Correct evidence tier.** Pure/component/runner tests prove rules and
   protocol mechanics; GameTests prove their actual in-game boundary; native
   lifecycle tests prove ordinary bootstrap, adapter, load/unload and restart;
   manual gates prove player comprehension. No lower tier inherits a higher-
   tier claim merely because it is deterministic or green.
5. **Identity and independence.** The test, candidate, rules/schema, world,
   operation and retained result have coherent identities. Comparison lanes and
   the oracle do not share mutable state that can manufacture agreement, and
   reused evidence remains limited to unchanged inputs and its original scope.
6. **Reliability and proportional cost.** Success follows canonical/correlated
   facts rather than wall-clock luck. Run the cheapest faithful discriminator
   first and only the minimum native/CI boundary required for the remaining
   claim. Redundant green runs do not improve methodological validity.

At `FINAL_REVIEW`, report the method as sound for the stated claim, sound only
for a named narrower claim, or insufficient. This classification is independent
of pass/fail status: an unsound green test is not acceptance evidence, while a
sound failing test may reveal a product or harness defect that still requires
classification.
