# PM-F0VC-PREPARED-NATIVE-PIPELINE-01: prepare once, execute many

Status: `ACCEPTED_INFRASTRUCTURE`; pulled forward at a safe paused F0.2B boundary by explicit
user authority on 2026-09-08. Specification revision: 6. Parent checkpoint: F0
verification infrastructure before completion of the F0.2 reference-container
vertical. Risk:
critical test infrastructure. Engineer: supervising root. Intended sole
executor: `/root/terra_f02a_kernel`, `gpt-5.6-terra`, reasoning `high`. Active
phase and acceptance owner: `CONTINUITY.md`.

## Activation condition and evidence basis

- The original post-F0.2B activation condition is superseded by the user's
  explicit 2026-09-08 order. F0.2B is safely paused but incomplete; F0.VC is
  pulled forward because the failed native attempt demonstrated that shared
  mutable preparation/run roots are already obstructing trustworthy product
  evidence. This is an `AUTHORITY` sequencing change, not F0.2B acceptance.
- Exact implementation baseline is published `main == origin/main`
  `b1b955f5fd46efbddece801b63bd8aa295ea3d84`, parent
  `644abbdf27a4d58b9d11efe6db0c7ab0e147299c`, tree
  `596df1734a78e3853c681e3168bacf9f5a2d5906`. The F0.2B implementation on
  this lineage remains unaccepted WIP and its product semantics may not be
  changed, claimed or re-reviewed by this infrastructure order.
- The previous-writer handoff is complete. F0.2B failed evidence is preserved
  under `/home/rd/proj/pm-f02b-native-r4b.UjGg3S/`,
  `/home/rd/proj/pm-f02b-native-r4.Dvjujx/` and the two checked local-hive
  manifests. Independent activation checks found zero repository runner
  registrations, no matching task-owned pilot process and no listener on
  ports 26120, 26121 or 26200--26207. Unrelated host runner services remain
  out of scope.
- GitHub run `34145023478` is retained diagnostic evidence only. Its worker
  steps spent about 5m22s--5m40s preparing/running Gradle while the selected
  GameTests themselves completed in about 0.58s--0.61s. The run ended
  failed/cancelled and produced no acceptable aggregate. It proves neither
  correctness nor a speed target, but it establishes redundant cold
  preparation and evidence-delivery failure as real pipeline costs.
- Read the engineering protocol, root/scoped `AGENTS.md`, active ledger,
  accelerated-verification loop, integration-feedback foundation, accepted
  F0.VB order and the applicable release-verification skill before work.

## Bounded outcome

Make the already-required four-slot native pipeline prepare one immutable exact
candidate runtime once and execute many isolated lanes from it. Implement all
five capabilities below as one infrastructure contract:

1. **Build once, run many.** One producer performs the candidate compilation,
   NeoForge preparation and packaged-artifact verification required by the
   declared plan, then emits a bounded immutable prepared-runtime manifest.
   Consumers launch that exact runtime; they do not independently compile,
   transform or package candidate code.
2. **Content-addressed preparation.** Dependency downloads, NeoForge transforms
   and other source-independent preparation may be retained by exact content
   identity. Every worker receives a private read-only or copy-on-write view;
   no two simultaneous jobs share mutable Gradle, transform or build-output
   state. Unknown, incomplete, dirty, corrupt or mismatched inputs rebuild or
   fail closed according to the declared tier; they never become a cache hit.
3. **Compatible-case process reuse.** A worker may keep one prepared Minecraft
   JVM alive while executing multiple test cases only inside one native shard
   and its one fresh disposable world, when the checked-in plan declares those
   cases batch-compatible and the protocol proves cleared transient scenario
   state. It may not cross independent shard/lane or world boundaries.
   Bootstrap, graceful restart, abrupt crash, save-boundary and process-
   lifecycle claims retain their exact required fresh-process boundaries.
   Grouping is an execution optimization, never a semantic rewrite.
4. **Early semantic preflight.** Before provisioning or launching a heavy JVM,
   validate the complete deterministic assignment, required lower tiers,
   source/spec/runtime identities, lane compatibility, namespace/capacity
   allocation, expected artifact set and merge contract. An attributable
   structural failure stops before Minecraft while preserving a bounded report.
5. **Reliable evidence transport.** Every worker publishes a uniquely scoped
   bounded manifest/failure bundle on success, failure or cancellation. The
   merge always runs, rejects missing/duplicate/stale/foreign/incomparable
   evidence and emits either one complete aggregate or an explicit incomplete
   aggregate. Upload layout, cleanup and cancellation cannot silently discard
   an otherwise attributable lane or convert it into success.

The existing four simultaneous isolated worker rule remains binding. This
checkpoint removes redundant work inside that topology; it does not add more
workers, reduce lane coverage, reuse terminal assertions or make cached success
eligible as fresh evidence. Phase timing is recorded from ordinary
qualification work, but no numeric speedup floor is an acceptance criterion.

## Identity, isolation and lifecycle contract

- The prepared-runtime identity binds exact source content including relevant
  dirty/untracked inputs, production and test classpaths, packaged JAR, JDK,
  OS/architecture, Gradle/NeoForge and dependency locks, workflow/tool schemas,
  selected tests/scenarios and every executable launch input. Commit SHA alone
  is insufficient.
- The producer signs no trust shortcut: consumers recompute or verify the
  declared hashes before launch. A source, test, scenario, dependency, launch
  or package-input change invalidates every affected prepared layer.
- Content addressing identifies the immutable enclosing layer/manifest; it
  must preserve every launcher-significant basename, extension, relative-path
  relation, classpath/module-path order and argument token. Relocation is
  permitted only when the rewritten launch surface is byte-for-byte
  equivalent in meaning and independently verified before Minecraft. A hash
  suffix may not turn a `.jar` into an unrecognized module-path entry.
- Reuse applies to immutable code/tool preparation only. Each independent lane
  still owns a fresh world, port, display where applicable, process namespace,
  runtime scratch, evidence directory and exact run/job/worker identity. Only
  the two declared halves of one recovery lane may share its world.
- A batch-compatible worker clears and acknowledges scenario target, fixture,
  diagnostic, frame and protocol state before the next case in the same shard.
  A stale nonce, surviving authority, unexpected cross-shard/world reuse or
  failed reset aborts the shard; it never falls through to the next case.
- One immutable assignment is the sole source for preflight, actual shard
  execution, lifecycle/fresh-process classification, evidence and merge. A
  synthetic capacity/demo case list may test the compiler in focused tests but
  cannot stand in for, contradict or certify the selected native semantic
  lanes. Worker/lane/world/process facts reported at failure must be derived
  from the executed assignment, not from a parallel illustrative plan.
- Task-owned runners and processes are stopped and deregistered at Gate C.
  Credentials, mutable worlds and successful terminal state never enter the
  prepared cache or retained artifact bundle.

## Write scope and exclusions

Terra owns diagnosis, local design, algorithms, helper/file choices and the
complete focused-through-provider correction cycle inside root CI workflows,
the external test-pilot/selector/cache/runner/merge tooling, Gradle launch or
preparation configuration needed for the immutable runtime, and focused tests
for that infrastructure. Expected paths are navigation hints, not a patch
recipe.

Do not change canonical domain behavior, persistence schema/envelope, process
or scene semantics, materialization requirements, scenario assertions, product
balance or Minecraft gameplay to obtain a cache hit. Do not add natural-terrain
work, new gameplay breadth, F0.2 aftermath, F0.3+, deployment, v2 removal,
force-push/history rewriting, persistent public runners or another executor.
If a public/persistent format outside test-infrastructure evidence is required,
return an `ARCHITECTURE` boundary.

## Acceptance matrix

| ID | Required outcome | Positive proof | Negative/recovery proof | Final proof |
| --- | --- | --- | --- | --- |
| PR-1 | One exact prepared runtime is produced and consumed by all four shards | manifest and launch traces bind one source/classpath/JAR/tool identity | changed source/test/scenario/dependency/launch input, corrupt layer and incomplete producer reject before launch | provider aggregate proves four consumers and no per-shard candidate compile/transform/package task |
| PR-2 | Preparation reuse is isolated and deterministic | four private read-only/COW views run concurrently from one content identity | cross-worker mutation, writable shared cache, foreign architecture/JDK and hash mismatch fail closed | consumed namespace/evidence manifests prove disjoint mutable roots and identical immutable inputs |
| PR-3 | Compatible cases reuse a process without crossing native-execution boundaries | one bounded worker executes at least two declared compatible cases inside one shard/world with exact reset acknowledgements | cross-shard/world reuse, restart/crash/bootstrap grouping, stale nonce/state and failed reset are rejected | process/world/run identities prove allowed reuse and required fresh boundaries separately |
| PR-4 | Structural defects fail before Minecraft | valid plan passes deterministic preflight | missing/duplicate lane, under-selection, incompatible grouping, namespace collision, capacity or expected-artifact mismatch returns an attributable preflight failure | failure trace proves no Minecraft child started for rejected plans |
| PR-5 | Evidence survives every terminal job state and merge fails closed | success bundles retain unique worker/lane paths and produce one complete aggregate | worker failure, cancellation, upload-layout collision, missing/duplicate/foreign evidence produce one explicit incomplete aggregate | provider run retains all available bundles, merge result and exact cleanup evidence |
| PR-6 | The optimized topology preserves existing correctness boundaries | bounded actual-Minecraft qualification executes existing accepted semantics from the prepared runtime on four simultaneous workers | fresh-evidence, restart and crash classifications cannot consume cached success or an impermissible reused JVM/world | exact command/task trace, package verification, lane aggregate and passive phase timings are retained; no numeric floor |

## Verification and execution authority

When activated, Gate A grants the sole Terra executor the complete bounded
outcome: implementation, local focused/full corrections, reviewable commits,
non-force fast-forward publication required for GitHub execution, `gh`
dispatch/observation and four ephemeral task-owned `pm-native` runners under a
dedicated retained task root. Each runner must use distinct work/temp/runtime/
world/port/display/evidence namespaces. Provider credentials never enter logs
or artifacts.

Before provider qualification, pass focused selector/cache/manifest/lifecycle/
merge tests, workflow syntax and repository guardrails, plus the complete
critical-code gate from `AGENTS.md`. Verify the packaged JAR produced by the
same producer identity. Run Visuals or authentic-reference gates only if their
actual inputs change; otherwise cite their exact unchanged-input receipt.

Use one bounded actual-Minecraft infrastructure qualification over semantics
accepted before F0.2B. It does not close, reopen or re-accept F0.2B and cannot
claim a new M-level. Record producer time, per-worker preparation/launch/test/cleanup
time, queue time separately and the comparable old/new wall time when naturally
available. Timing is advisory. Correctness, identity, invalidation, isolation,
fresh-process boundaries, complete/incomplete merge behavior and zero cleanup
are mandatory.

Terra returns only a real `ARCHITECTURE`, `AUTHORITY`, `RISK` or `IMPASSE`
boundary, or one coherent Gate C packet with exact source/spec/tool/runtime/JAR/
workflow identities, commands/results, worker/job/process/world assignment,
cache decisions, task trace proving no redundant shard compilation, negative
results, aggregate, timings, changed paths, retained evidence and zero owned
runner/process/credential state. No routine implementation approval is needed.

While `EXECUTING`, the engineer performs exactly one bounded `LIVENESS` check
after every complete ten-minute interval without an executor event and never
earlier. It reads only collaboration state, the exact task-owned process/job and
one bounded progress marker; it never inspects intermediate source or design.

## Stop conditions

Stop and preserve WIP/evidence before weakening fresh evidence, sharing mutable
worker state, changing a gameplay assertion, replacing a required physical
lane with a mock/server command, grouping a restart/crash lane across its
required process boundary, expanding product scope, touching unrelated services
or runners, exposing credentials, deploying, rewriting history or starting the
next F0 slice. A repeated unexplained identity/cache discrepancy is an
`IMPASSE`, not permission for an unkeyed fallback.

## Review record

Planned by the supervising engineer on 2026-09-07. Revision 2 activates it on
2026-09-08 at the user's explicit direction after a safe F0.2B pause. Gate A
now grants the bounded implementation and provider authority above. After
independent F0.VC acceptance, resume `PM-F02B-REFERENCE-CONTAINERS-01` revision
5 from its preserved `b1b955f` lineage and evidence; do not replace its pending
semantic Gate C with this infrastructure qualification.

The first local direct-Minecraft consumer attempt failed before readiness with
`java.lang.module.FindException: Module format not recognized` because an
external NeoForge module-path JAR had been staged as `*.jar-<digest>`. Evidence
is retained under `/home/rd/proj/pm-f0vc-local-stage/` and
`/home/rd/proj/pm-f0vc-local-view*-root/`; cleanup was zero. This is a
launcher-path fidelity defect inside PR-1/PR-2, not an architecture boundary.
Resume by preserving launcher-significant path semantics and rebuilding the
affected exact prepared identity; no implementation recipe or weakened
isolation is prescribed.

Provider run `34165343906` at published `7c80b003ca2263eb74e7e835462dedb406904400`
is retained fail-closed evidence, not Gate C. The producer and workers 0--2
passed; worker 3 timed out awaiting `durable_server_save` after Minecraft
entered `Saving worlds`, and the merge correctly emitted `incomplete`. The
reported preflight classified worker 3 as one isolated `abrupt-recovery` case,
but the executed immutable matrix assigned it arrival-checkpoint one/two,
`graceful_restart` and player-intervention lanes; its log places the failure at
the graceful stop after earlier lane shutdowns. Revision 3 therefore requires
one assignment identity end to end and an evidence-based diagnosis of that
actual graceful-save failure. Do not retry blindly, inflate 90 seconds,
relabel the lifecycle, drop a lane or accept three-of-four success. Terra owns
the concrete correction, including a coherent compatible-case grouping or
isolation decision that still proves PR-3 and preserves all required restart
boundaries.

Independent FINAL_REVIEW of published `e05f00e24a8de2f8a324f56590ee0e938db37173`
and successful provider run `34170783219` rejects Gate C pending revision 4.
The run proves one producer, four simultaneous actual-Minecraft consumers, one
runtime identity, disjoint declared endpoints and a fail-closed four-worker
merge, but three acceptance boundaries remain unproved or contradicted:

- the seven new `f0vc-pipeline.test.mjs` tests live under `src/`, while the
  canonical `npm test` command executes only `test/*.test.mjs`; the reported
  218/218 suite therefore did not execute any of those F0.VC regressions;
- each uploaded worker artifact retains only the reduced CI shard result and a
  path to `build/frontier-v3-ci-shards/.../persistent-matrix.json`. The named
  receipt is absent from the artifact even though it alone retains the exact
  client/server PIDs, world/run identities, reset barrier, restart/crash
  boundaries, demand-loss releases and durable final stop used to prove PR-3.
  The merge can consequently accept success after losing the primary lifecycle
  evidence required for independent audit, contrary to PR-5;
- the successful run's immutable store and all four consumer classpath views
  are mode-0444 hard links to the same inode. A pathname-private hard link is
  not by itself proof that a same-owner consumer cannot change the producer or
  another consumer; no executed cross-consumer mutation negative accompanies
  the claim. PR-2 requires an enforced read-only boundary or genuine private
  COW/copy semantics whose mutation isolation is demonstrated, not merely a
  `READ_ONLY_HARDLINK` label.

Revision 4 requires one coherent correction outcome: make every F0.VC
regression part of the ordinary canonical fast suite; prove prepared-runtime
invalidation and cross-consumer mutation isolation against the actual chosen
copy/read-only mechanism; and make each terminal worker bundle retain and
cryptographically bind the complete bounded primary native lifecycle receipt
or an equivalently complete independently auditable projection. The merge must
fail closed when that evidence is missing, altered, foreign or inconsistent
with the assignment/result. Return a corrected exact-identity provider Gate C
over four workers plus the normal local gates. Preserve the successful and
failed prior evidence as historical diagnostics; do not weaken coverage,
inflate timeouts, reopen F0.2B semantics or pursue a speed ratio.

Independent FINAL_REVIEW of published
`594db614925a3ca35b40dd5d9d843f0a150ca64` and successful provider run
`34173116717` accepts the three revision-4 repairs but does not yet accept Gate
C. Canonical `npm test` independently passes 228/228 including the F0.VC
regressions; the consumer implementation and negative test use private
reflink-or-copy files rather than hard links; and every downloaded worker
artifact now contains the complete hash-matching
`primary/persistent-matrix.json`. The provider run has one producer, four
successful actual-Minecraft workers, a successful merge and zero registered
repository runners at terminal review.

One identity boundary remains incomplete. The worker evidence repeats the
current assignment and matrix hashes beside the primary receipt, but the merge
validates only those repeated labels, the receipt digest, `workerId` and the
reduced lifecycle projection. It does not prove that the retained receipt's
own `session.plan.workerPlanSha256` equals the current
`assignmentContentSha256`, or that its own `session.plan.source.planSha256`
equals the current `matrixPlanSha256`. A foreign same-worker primary receipt
can therefore be rehashed and relabelled as current while preserving a
compatible reduced projection. Revision 5 requires the merge to validate the
receipt's authoritative internal assignment identities against the admitted
assignment and to reject substituted same-worker receipts, including
assignment-plan drift and source-matrix-plan drift, in canonical negative
tests. Do not equate the separate assigned runtime `contentSha256` with the
persistent worker-plan hash.

The complete local critical command also remains red. Its retained
`pale-mirror-neoforge/build/runs/core-game-test/logs/latest.log` records 296
GameTests with one required failure:
`coldCompletedFieldProjectsOnceFromNeutralBaselineButNeverOverwritesForeignBlocks`
expected `UPDATED` but observed `CONFLICT`. The revision-4 commit changes only
test-pilot Node files and retains the prior packaged JAR, so this is not
permission to alter or waive paused F0.2B product semantics. Terra owns one
bounded evidence-based classification: establish whether the failure is a
pre-existing/reproducible paused-F0.2B baseline condition or a regression/test
isolation defect attributable to the current infrastructure. If it is the
former, return an exact `AUTHORITY` boundary for the engineer to decide the
stage-gate treatment; if it is the latter, correct it inside the applicable
F0.VC test-infrastructure boundary and return a green complete critical gate.
No blind retry, assertion weakening, timeout change or F0.2B gameplay repair is
authorized.

Revision-5 requalification is deliberately incremental. If the correction
changes only the merge validator and its canonical tests, re-run the corrected
merge over the immutable downloaded artifacts from provider run `34173116717`
and retain the new validator/source identity plus positive and foreign-receipt
negative results; do not repeat the four-worker Minecraft run solely for that
pure verifier change. Any change to producer preparation, worker evidence
generation, native execution, workflow topology or the retained receipt shape
requires a fresh exact-identity provider run. All revision-4 evidence remains
immutable historical input, and all prior coverage/isolation/cleanup
requirements remain binding.

Independent FINAL_REVIEW accepts F0.VC infrastructure at published
`d38909d285a9ea1ec92f581fa3c1b536bfc99cad` (parent `594db614`, tree
`3eb0de5f`). The exact two-path revision-5 delta adds the two authoritative
receipt-plan comparisons and a canonical adversarial test that rehashes and
relabels a same-worker receipt with each internal identity drift. Main
independently read the full delta and passed the focused 11/11 tests from a
separate writable temporary filesystem. Terra's pre-quota canonical run passed
229/229, and main's later canonical reproduction reached 224 passes before
five unrelated persistent-matrix tests failed at filesystem `write` with
`EDQUOT`; no assertion failed. The corrected validator successfully remerged
the immutable downloaded run-`34173116717` artifacts as status `ok`, plan
`f71ec116...d84f895`, runtime `faf0e5c5...17b`; no new native run was required.

The local 295/296 critical result is an explicit external limitation of this
scoped infrastructure acceptance, not a green product/release gate. The
failing F0.2B test and its production executor are byte-identical to pre-F0.VC
baseline `b1b955f` (`30f21681...6d89` and `e17fc5ff...80c9` respectively),
while F0.VC did not change that contract. The failure therefore returns to the
resumed F0.2B order for product-level classification/correction; it may not be
waived by any later completion claim. This disposition accepts PR-1--PR-6 as
test infrastructure only, does not claim F0.2B semantics or a globally green
critical gate, and does not relax the final product/release gate.
