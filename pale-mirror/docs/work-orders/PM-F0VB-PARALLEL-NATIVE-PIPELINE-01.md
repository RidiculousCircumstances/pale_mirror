# PM-F0VB-PARALLEL-NATIVE-PIPELINE-01: activate the four-worker native pipeline

Status: planned, not active. Specification revision: 1. Parent checkpoint:
accepted F0.V. Risk: critical test infrastructure plus bounded GitHub runner
operations. Engineer: supervising root. Intended executor:
`/root/terra_f0va_unrestricted`, `gpt-5.6-terra`, reasoning `high`.

## Activation condition

This order may be activated only after independent acceptance of the terminal
F0.V Gate C packet and an explicit engineer handoff. It is the mandatory next
checkpoint before F0.1 implementation. It is not part of F0.V correctness and
must never cause a valid F0.V matrix to be repeated.

## Outcome

Make the checked-in four-worker GitHub Actions path operational on demand so
each subsequently started complete native matrix is deterministically sharded
over four simultaneously available isolated `pm-native` slots and merged fail
closed. Prove the provisioning, simultaneous acquisition, isolation and merge
plumbing with the smallest bounded qualification that does not rerun F0.V or
claim gameplay evidence. Numeric speedup and a timing campaign are excluded.

The mechanism must be reusable by later slice orders without source-specific
manual surgery. On-demand runners are stopped and deregistered after each
bounded workflow; do not leave a persistent public-repository executor on the
host.

## Required invariants

- Four slots are simultaneously schedulable under the exact checked-in labels
  and immutable assignment; a serial queue of four jobs is not parallelism.
- Every slot owns distinct work and temporary roots, Gradle/cache namespace,
  disposable world namespace, port allocation, private display, process tree
  and evidence identity. No slot can consume or publish another slot's state.
- Same-host execution is admitted only after a bounded capacity preflight; an
  insufficient host fails visibly instead of oversubscribing or silently
  serializing. Separate hosts remain valid.
- The merge rejects missing, duplicate, stale, foreign-build, foreign-run and
  semantically incomparable shards. Cancellation still preserves completed
  bounded evidence and marks the aggregate incomplete.
- Runner registration tokens and credentials never enter logs, artifacts,
  repository files or retained command transcripts. Only task-owned runners
  are stopped or deregistered.
- Workflow/provider queue time and speed ratios are telemetry, not acceptance
  thresholds. No standalone benchmark is run.
- This infrastructure owns no canonical state, scenario meaning or Minecraft
  mutation authority and cannot weaken fresh-world, restart or recovery gates.

## Acceptance evidence

1. Focused workflow/schema/assignment/merge negatives pass, including missing,
   duplicate, stale and cross-worker evidence.
2. One bounded GitHub qualification records four overlapping active worker
   leases, exact runner/run/job identities and distinct isolation namespaces,
   then produces one complete fail-closed aggregate. It need not launch a full
   Minecraft scenario matrix.
3. Provider inventory and host process checks prove every task-owned runner,
   listener, display and child process stopped and deregistered afterward;
   unrelated services remain untouched.
4. The reusable invocation and failure path are documented for later active
   slice orders. The next F0.1 order must invoke this path for any newly started
   complete native matrix.
5. Applicable docs, workflow syntax, guardrails and focused tests pass. Return
   one terminal packet for independent review; do not self-start F0.1.

## Authority and exclusions

When activated, Terra owns diagnosis, implementation, local design, helper and
file choices, focused-through-full applicable verification, bounded download
and operation of four task-owned on-demand runners, GitHub dispatch/observation
and ordinary in-scope corrections. Use non-force reviewable refs only when an
exact remote revision is required; never expose secrets, delete refs, deploy,
touch unrelated runners/services, run a full F0.V matrix, add gameplay breadth
or begin F0.1. Any need for persistent runners, broader host administration or
untrusted-event execution is an `AUTHORITY`/`RISK` boundary.

## Review record

Not active; no evidence accepted.
