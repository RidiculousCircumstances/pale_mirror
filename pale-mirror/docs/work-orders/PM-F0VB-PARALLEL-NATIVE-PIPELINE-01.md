# PM-F0VB-PARALLEL-NATIVE-PIPELINE-01: activate the four-worker native pipeline

Status: accepted. Specification revision: 8. Parent checkpoint:
accepted F0.V. Risk: critical test infrastructure plus bounded GitHub runner
operations. Engineer: supervising root. Intended executor:
`/root/terra_f0vb_r5`, `gpt-5.6-terra`, reasoning `high`.

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

## Mandatory supervision cadence

While this order is `EXECUTING`, the engineer MUST perform exactly one bounded
`LIVENESS` check after every complete ten-minute interval without a Terra
milestone, exception or terminal packet, including across assistant turns. An
executor event resets the interval. The engineer MUST NOT check earlier, skip a
due check or substitute repeated status messages for the scheduled check.

The check is limited to collaboration status, the exact task-owned process/job
handle and one bounded progress marker such as state, exit status, elapsed time
or output modification time. It MUST NOT inspect source, diffs, implementation
choices or semantic intermediate results. Healthy evidence causes no message or
direction to Terra and no ledger update merely to record a heartbeat. This timer
is inactive while the order is `planned`, terminal or otherwise not
`EXECUTING`.

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

Activated by the supervising engineer on 2026-09-07 after independent F0.V
Gate C acceptance at published commit
`089495ed630f777f63e3b8952e500ea548bc814c`. No F0.V rerun is required or
authorized by this activation. No F0.VB implementation evidence is accepted
yet.

The first qualification attempt at published commit
`f0843bff608ac5c08ea91dd5439329a5d50583af` is attributable negative evidence,
not acceptance. Four distinct runners were initially online, but their first
dispatch triggered an automatic `2.329.0` to `2.337.0` update and the listeners
returned at different times. A second dispatch overlapped the cancelled first
run's merge, so it did not observe an uncontended stable four-listener pool.
Both worker attempts that reached the lease step then failed because the
workflow invoked `tools/...` from the monorepo root although the script is under
`pale-mirror/tools/...`. Runs `34090293848` and `34090595954` were correctly
cancelled and their fail-closed merges failed; they prove neither provider
serialization nor four-way overlap.

Revision 4 keeps the original outcome and existing Terra authority. First
finish safe deregistration of the remaining task-owned `pm-f0vb-0` runner,
which independent review observed online and not busy after cancellation.
Then establish four current-version listeners that are simultaneously stable
before one uncontended qualification dispatch, use the actual monorepo layout,
and accept only evidence whose four lease intervals overlap. Do not overlap a
retry with cleanup/merge from an earlier run. Finish with terminal workflow,
runner inventory and task-owned process evidence; no F0.V rerun or broader
scope is authorized.

Revision 5 records an independently reviewed partial success at published
commit `cc49cd2fbe95da698ee3ee4e637de3aa036fed41`. GitHub run
`34091091421` completed successfully on four distinct task-owned runners:
`pm-f0vb-0`/runner 25, `pm-f0vb-1`/29, `pm-f0vb-2`/30 and
`pm-f0vb-3`/31. All four lease steps overlap for approximately 19 seconds,
the merge job completed, five bounded artifacts remain attributable to the
same run and the corrected monorepo paths work. Independent post-run checks
find zero registered repository runners and no process under the exact
`/home/rd/proj/pm-f0vb-native-pipeline` task root. This accepts actual
four-slot acquisition, the path correction and final cleanup as reusable
evidence; it does not yet accept the complete F0.VB contract.

The current evidence schema ignores the required `qualification_id` and does
not bind head SHA, run attempt, job identity or actual runner identity. Its
merge can therefore reject a foreign run number but cannot prove or reject
all stale/foreign-build/foreign-attempt evidence required above. It proves
only distinct `RUNNER_TEMP` lease strings; it does not bind the declared
Gradle/cache/world/port/display/process namespaces that a later native shard
must receive. There is no bounded same-host capacity preflight, no focused
negative suite for the evidence contract, and no checked-in operational
invocation/failure/cleanup description. The raw timestamps are emitted at
nanosecond scale into JavaScript `Number` values while the aggregate names the
result `overlapMillis`, so their unit and safe-integer representation are also
not a trustworthy reusable contract.

The next executor retains local design authority and must close those contract
gaps coherently before one final bounded qualification. The terminal evidence
must bind one immutable qualification/build/run-attempt to four actual distinct
runner/job identities, unambiguous bounded timestamps and every isolation
namespace consumed by the reusable native path; fail closed on missing,
duplicate, stale, foreign or incomparable evidence; run a visible same-host
capacity preflight; and include focused positive and negative tests plus the
documented on-demand invocation and cleanup path. Preserve run `34091091421`
as accepted partial evidence, do not rerun F0.V or conduct a timing campaign,
and finish with zero task-owned runners/processes. The previous Terra turn
ended on an external usage limit after completing cleanup, not on an accepted
terminal packet. Replacement `/root/terra_f0vb_r5` is now the sole executor;
the ten-minute liveness cadence restarted with that assignment.

Revision 6 records published commit
`3ccb1b0e560036de57904e7df53aae0dc844f161` and failed qualification run
`34092979150`. The new schema, identity capture, namespace declaration,
capacity admission, focused negatives and lifecycle script are useful accepted
partial implementation. All four jobs began at 06:55:20Z on distinct runner
IDs 32/35/33/34 and names `pm-f0vb-0`/`3`/`1`/`2`; the host reported 16 CPU
slots, 28,253 MiB available memory and 919,377 MiB available disk. Every lease
writer correctly rejected the host's 19-digit nanosecond-scale output from
`date +%s%3N` as an unsafe millisecond value, and the merge failed closed.
There was no retry. Independent review confirms remote `main` at the stated
commit, zero registered runners and no exact task-root process afterward.

That run is attributable negative evidence, not terminal acceptance. Correct
the timestamp source to produce unambiguous safe integer milliseconds and keep
the existing bounded interval validation. Preserve completed per-worker
capacity, job-binding and namespace evidence even when a later lease or merge
step fails, while making the aggregate visibly incomplete rather than claiming
success.

The isolation contract must describe resources actually consumed by the same
reusable native shard path, not nominal variables that only the qualification
exports. In the current commit several new root variables have no consumer,
and the declared `DISPLAY` differs from the display that the existing private
Xvfb helper derives from the pilot port. Make one owner define the exact
work/temp/cache-or-Gradle/world/port/display/process mapping used by both
qualification and later native invocation; validate its deterministic
containment and disjointness, then bind that actual mapping into evidence.
This remains infrastructure-only and must not launch Minecraft for F0.VB.

The checked-in lifecycle must also be safely reusable. Current cleanup deletes
the GitHub registrations but leaves `.runner`, `.credentials` and
`.credentials_rsaparams` in every retained installation, so the next `start`
refuses the same task root. It also sends a process-group signal using only a
possibly stale PID file. Terminal cleanup must remove local registration and
credential state through an authenticated task-owned path, leave retained
logs/binaries reusable, verify process ownership before signalling, fail
visibly if a task-owned process survives, and never target an unrelated PID or
runner. Pre-dispatch readiness must establish exactly four expected online,
idle task runners rather than merely counting online names.

After focused positive/negative and lifecycle dry-run coverage proves these
corrections, publish reviewably and perform one final small four-runner
qualification. Preserve runs `34091091421` and `34092979150`; do not rerun
F0.V, launch a timing campaign or enter gameplay/terrain/F0.1. Return the full
identity-bound artifacts, terminal aggregate, zero runner inventory, no
task-owned process and reusable clean local runner state for independent
acceptance.

Revision 7 records independently reviewed run `34094284143` at published
commit `2d37ebd0a1923753e2cf6f6c1ebf7062521f80ab`. All four worker jobs and
their always-upload steps passed. Their schema-2 leases bind the exact commit,
workflow, qualification, run/attempt, four distinct job and runner identities,
deterministically contained and disjoint work/temp/Gradle/cache/world/process
paths, ports 26100 through 26103, displays `:1100` through `:1103`, loopback
binds and safe millisecond intervals. Their common overlap is 15,416 ms. Four
capacity/job/namespace/lease artifact bundles and an identity-bound
`incomplete` aggregate are retained. Independent cleanup confirms zero remote
task runners, no task-root process and no `.runner` or credential files in the
four reusable installations.

The only observed aggregate failure is artifact layout: every worker artifact
correctly contains a leaf named `lease.json`, while `download-artifact` uses
`merge-multiple: true`; extraction therefore collapses those same-named leaves
instead of preserving four worker directories, and the merger correctly sees
fewer than four leases. Retain per-worker artifact directory identity through
download and make the merge discover exactly one lease from each of the four
expected worker bundles. Add the focused regression for same-named leaves,
missing/duplicate/foreign directories and unexpected extra evidence. Do not
redesign or repeat already proven identity, capacity, isolation or lifecycle
work. Publish reviewably, perform one final small qualification and finish with
the same complete remote/process/local-registration cleanup evidence. F0.V,
timing, Minecraft, gameplay, terrain, deployment and F0.1 remain excluded.

Revision 8 records independent terminal acceptance at published commit
`f2ea3108581911205748b669ab6495761310f6d0`. GitHub run `34095008014`
completed all four worker jobs and the fail-closed merge successfully. The
schema-2 aggregate binds qualification `f0vb-r7-f2ea3108`, the exact commit and
workflow, run attempt 1, four distinct job/runner identities, disjoint consumed
namespace maps, ports 26100 through 26103, displays `:1100` through `:1103`
and 17,545 ms of common lease overlap. All four capacity/job/namespace/lease
bundles and the merge artifact are retained; downloaded worker lease hashes
match the local evidence copy under
`/home/rd/proj/pm-f0vb-native-pipeline/f0vb-evidence-34095008014`.

Independent focused layout/identity/capacity tests pass 6/6 and shell syntax
passes. Terra reports the complete Node suite 203/203 plus `guardrails check`,
workflow syntax and diff checks green. Remote task-runner inventory is empty,
no exact task-root process remains, and all four reusable installations contain
no `.runner`, `.credentials` or `.credentials_rsaparams`. F0.VB is therefore
accepted as acceleration infrastructure only. It proves neither Minecraft
gameplay nor any F0.1 semantic result; every newly started complete native
matrix in subsequent orders must use this four-slot path and retain its own
ordinary correctness/recovery evidence.
