# PM-F0VA-PERSISTENT-PLAN-01: immutable assigned-worker execution plan

Revision: 1. Parent: F0.VA. Risk: critical-code (scenario execution preflight).
Engineer: /root. Executor: /root/terra_executor, gpt-5.6-terra high.
Specification READY; Gate A ACK required before implementation.

## Baseline and bounded outcome

Persistent-design order is ACCEPTED; all its grants closed. Preserve inherited
WIP and both histories. CI-composition schema2 is accepted with137 Node tests
and290 GameTests; current21 proves fixed smoke/graceful reuse, not CI adoption.

Introduce a pure bounded compiler/admission boundary for the actual immutable
CI worker assignment, and consume it as a preflight in run-ci-matrix-shard
before any native lane runs. Its result is a prepared execution plan, never
client-lifecycle evidence. Existing per-lane execution remains until the next
crash-protocol/integration orders; label this prerequisite honestly.

Read active AGENTS/ledger, engineering-agent-protocol, accepted design order,
architecture.yml's harness/causality ownership, and F0.VA.1/.5 requirements.
Relevant existing sources: ci-matrix, persistent-matrix, scenario.restartSegments,
run-isolated-scenario and run-ci-matrix-shard. Source snapshots are untracked:
record pre-edit content identity and retain enough baseline for causal red tests.

## Contract

- Immutable schema-2 CI plan owns exact lane membership/order and scenario
  content. Compiler takes that validated plan, one admitted worker and one
  correctness measurement. No process discovery, clocks, RNG, I/O, Minecraft
  state or alternate assignment. Identical inputs yield identical frozen
  plan/content hash. Caller mutations may not change the compiled result.
- Validate shard lane descriptors against the authoritative plan lane content,
  not IDs alone. Reject missing/duplicate/foreign lanes and content drift before
  native work. Source scenarios and original plan remain unchanged.
- Expand ordinary lanes to one segment; graceful and current declared
  before_restart crash lanes to two, using the existing restart splitter for
  exact setup/action/assertion/frame rebasing. Preserve all terminal/COLD/
  differential/arrival declarations. Identify each lane, original scenario hash,
  segment hash/order and unique safe world key. Two recovery halves share only
  their own lane world; independent lanes never alias it.
- Distinguish ordinary terminal, graceful handoff, expected crash boundary and
  recovered terminal as closed completion modes. Crash-pre is not a terminal
  success. Retain exact boundary/owner/payload/revision-or-epoch declaration;
  the recovery half is not re-armed. Reject unsupported crash phase or invalid
  combinations, not silent conversion to graceful. No new gameplay assertion.
- Admit a nonempty exact assignment including one/two worlds, with a hard
  maximum32 segments consistent with the existing session capacity. Do not
  change benchmark validation or its >=3-independent-world proof requirement.
  Runtime server PID/run/ready/nonce authority remains with the later live
  session owner, not invented compiler evidence.
- Use one explicit versioned kind/hash and bind source plan/build/contract,
  worker and measurement. A validator must reject tampering even if the caller
  updates a superficial hash: validate against the originating immutable plan.
- Actual shard CLI invokes this preflight before root/native allocation and
  retains the compiled artifact exclusively in its existing unique build root.
  Do not change existing lane invocations, result schema2, terminal assertions,
  timings, identity checks or failure policy in this cut. The later integration
  consumes the retained compiler output; it is not a second session protocol.

## Write scope and exclusions

Only tools/frontier-v3-test-pilot/:

- new src/persistent-worker-plan.mjs and test/persistent-worker-plan.test.mjs;
- src/run-ci-matrix-shard.mjs for compiler preflight/artifact retention only;
- test/ci-matrix.test.mjs for actual CLI call-site/preflight regression only.

No existing benchmark validator rewrite, Java, scenarios/contracts, workflow,
cache/fixture/lifecycle protocol, dependency/build, normative docs or root edits.
No native clients/servers, process termination, remote operations, deployment,
Git mutation or further F0 work. Request amendment for any extra file.

## Acceptance and verification

1. All actual four worker assignments compile deterministically, covering all13
   current lanes exactly once; worker0/3 two-world plans are admitted without
   padding. Original plan/scenarios remain unchanged. Minimal valid one-world
   assignment supported; empty/oversized/foreign assignments fail.
2. Pure round-trip validation proves segment/world relationships, exact
   restart rebasing and every current crash-window declaration. Tampered hash,
   lane content, duplicate/omitted segment, world alias, completion kind,
   worker/measurement/source identity and re-armed recovery fail closed.
3. Existing >=3-world benchmark tests still reject their smaller benchmark
   plans. No historical proof is changed or promoted.
4. Actual shard call site consumes preflight before native launch and retains
   plan with exclusive creation. Tests must exercise an importable invoked
   preflight boundary with real generated plans, plus inspect wiring; do not
   claim a missing-export error alone proves the original admission defect.
5. Focused/full Node and exact critical Gradle gate pass before Gate C. This
   proves compiler/preflight only, not one persistent client, crash recovery,
   provider correctness, F0.VA/F0.V or M3.

Gate A: ACK exact edits/design/commands, then wait for IMPLEMENTING. Approved
focused command after grant:
`node --test tools/frontier-v3-test-pilot/test/persistent-worker-plan.test.mjs tools/frontier-v3-test-pilot/test/persistent-matrix.test.mjs tools/frontier-v3-test-pilot/test/ci-matrix.test.mjs`.
Stop at Gate B after focused tests/diff checks. Main reviews before full
`npm test --prefix tools/frontier-v3-test-pilot` and
`./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar`.
One agreed heavy-run owner only; native matrix/restarts are not granted here.

## Stop conditions and remaining integration

Conflicting WIP/writer, authority mismatch, unsupported current lane, ambiguous
scenario split, new protocol ownership or repeated unexplained failure requires
review, not a broader implementation. Subsequent orders must extend existing
crash/session owners, replace per-lane client spawning and prove the actual
worker workload natively. Do not close that requirement on this prerequisite.

## Review record

Gate A ACK accepted on2026-09-05. Compiler/validator design and four-file scope
accepted. Compiler runs before shard root/native allocation and retains an
exclusive artifact afterwards. IMPLEMENTING granted only for those four files,
the exact focused Node command and diff checks. Stop at Gate B; no full/native
or external run grant. Existing client/lifecycle owners remain unchanged.

Gate B review correction: main focused27/27 passes, but direct validation of
compiled expected_crash.scenario rejects its inherited crash field after the
restart splitter removes restart. Executable segment scenarios must themselves
pass the existing scenario validator. Retain the exact crash arm solely in
expected_crash segment metadata, not in either executable scenario half; the
original lane hash remains evidence of the source declaration. This is within
the compiler contract, not permission to weaken scenario validation. Compiler
and focused-test correction granted; complete exact rebase/JSON round-trip and
re-armed-recovery negatives, then return to Gate B. No full/native grant yet.

Gate B supplement reviewed: main focused28/28 passes. All generated executable
segments validate; exact source crash arm exists only on expected_crash metadata.
Trusted expected worker/measurement reject valid-but-foreign compiled reports.
Exact setup/action/assertion/frame rebasing, JSON round-trip and re-armed recovery
are tested, along with one/two-world admission and unchanged benchmark minimum.
Main now owns full Node and the exact critical Gradle gate; source writes are
closed. No native matrix/provider/deployment permission is added.

## Gate C acceptance, 2026-09-05

ACCEPTED for assigned-worker compiler and real shard preflight, not persistent
execution. Exactly four allowed files changed; two new files raise nested
dirty/untracked entries from199 to201. Main independently reviewed and ran:

- Focused command above:28/28 tests pass.
- Full Node command above:144/144 pass, no skipped/failed tests.
- Exact critical Gradle command above: BUILD SUCCESSFUL in1m11s,71 tasks
  (37 executed,34 up-to-date), session22719 terminal exit0.
- Fresh core GameTest log:290/290 required tests,50.89s, completion
  2026-09-05 16:23:22.644+05; normal shutdown16:23:34.631.

AC-1 maps to all-four-assignment, exact13-lane,1/2world and capacity tests.
AC-2 maps to exact graceful/abrupt rebasing, all5crash arms, validated executable
halves, independent-world ownership and reconstructed JSON/identity/tamper
negatives. AC-3 keeps the existing three-world benchmark rejection. AC-4 maps
to the invoked importable preflight and exclusive artifact before native lane
allocation. AC-5 maps to the independent full commands above.

Reviewed working-content tuple SHA-256:
`d8d307f8eb476c5ba861bb8b6a6b0d2ad416cd53dea2bbfa2794275704b02ed4`.
Compute as SHA-256 of `sha256sum` lines, relative to project, for these paths
under tools/frontier-v3-test-pilot/ in order: src/persistent-worker-plan.mjs,
test/persistent-worker-plan.test.mjs, src/run-ci-matrix-shard.mjs,
test/ci-matrix.test.mjs. JAR remains
41ac2b1fe122d03361976f5614c7ef95052e5a94188297a3ff770a8ccdd82d53.
Both repo diff checks pass; outer repo still only has inherited .f0v-baseline/.
Generated core-game-test output was regenerated, no live-server world changed.

All executor grants closed. Crash/session protocol and actual persistent runner
integration are the next design steps; native worker/provider evidence, F0.VA,
original F0.V, later F0 slices and M3 remain unproved. No Git mutation,
deployment, actual native matrix, percentage benchmark or historical rewrite.
