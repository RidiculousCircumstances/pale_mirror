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
