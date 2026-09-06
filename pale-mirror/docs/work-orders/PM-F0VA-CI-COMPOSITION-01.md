# PM-F0VA-CI-COMPOSITION-01: preserve matrix semantics across CI shards

Revision: 1. Parent: F0.VA. Risk: critical-code (scenario evidence execution).
Engineer: /root. Executor: /root/terra_executor, gpt-5.6-terra high.
Specification READY. Active phase and grants belong to CONTINUITY.md.

## Baseline and outcome

Previous CI bootstrap order is ACCEPTED and all its grants are closed. Preserve
both repository histories and all inherited WIP. Nested HEAD is
0ac6f97a695d636ae928313de7a807d1f3ea05a1; root HEAD is
891a4dfe1a613857124775696973d065cc9399cb. Capture content hashes of the allowed
files before edits; Git HEAD alone does not identify these untracked sources.

Source review confirms three composition failures: run-f0v-matrix dereferences
both differential manifests even when the shard requested only one lane;
the aggregate never performs that comparison or the distinct-arrival relation;
and the sequential runner calls a merger requiring concurrent worker overlap.
The deliverable is equivalent declared correctness for the complete unsharded,
parallel-sharded and sequential-sharded matrices, with separate temporal proof.

Read architecture.yml's frontier_v3_test_harness and test-pilot-causality flow,
engineering-agent-protocol.md, frontier-v3-execution-semantics.md and the active
CI composition audit. Do not change comparison meaning or gameplay scope.

## Contract and ownership

- The immutable contract-derived plan owns lane membership and comparison
  declarations. A worker owns its exact assigned native evidence; the complete
  merge owns cross-lane acceptance. Partial lane success is not matrix success.
- A selected differential lane retains explicit pending aggregate-comparison
  status and bounded portable evidence. A complete local differential still
  invokes the existing declared comparison. Missing or failed halves in a full
  run fail, rather than becoming implicitly deferred. Existing terminal and
  COLD-progress assertions remain mandatory on the native lane.
- Transfer only declared projections needed for terminal, differential and
  arrival checks. Validate evidence against the immutable plan's exact
  family/variant/lane, view/id/paths and comparator declarations. Do not trust
  a nonempty object or a worker's assertion that comparison passed. Preserve
  exact and predeclared-tolerance behavior through one reusable comparator;
  do not implement a different CI interpretation or synthetic native success.
- Both aggregate modes require complete unique coverage, matching plan/build/
  contract identities and one matching measurement identity. Compare one cold
  and one hot_cold result for each declared pair, and require the declared two
  arrival checkpoints to refer to the same view/id/path and distinct integral
  values. Missing, malformed, foreign or contradictory evidence fails closed.
- Parallel aggregation retains four-worker overlap and the 30-second start-skew
  ceiling. Sequential aggregation instead proves the plan's ordered disjoint
  shard intervals. Share correctness validation; do not relax parallel checks
  or label serial execution as parallel. Both modes expose their checked policy.
- The final timing comparison must bind the sequential report to the same
  plan/build/contract and exact lane count as the parallel reports; failed or
  foreign reports cannot contribute even if their numbers are plausible.
- New evidence requirements must reject old reports lacking them. Version the
  affected evidence formats coherently if necessary; no silent compatibility
  fallback or historical report rewrite. Native world/persistence schemas are
  out of scope. Preparation fingerprints naturally invalidate changed inputs.

## Allowed implementation boundary

Only these files under tools/frontier-v3-test-pilot may be edited:

- src/ci-matrix.mjs, src/f0v-matrix.mjs, src/run-f0v-matrix.mjs
- src/run-ci-matrix-shard.mjs, src/run-ci-matrix-sequential.mjs
- src/merge-ci-matrix.mjs, src/write-ci-matrix-plan.mjs (only coherent schema use)
- test/ci-matrix.test.mjs, test/f0v-matrix.test.mjs
- One adjacent focused test/ci-composition.test.mjs if useful.

Propose any extra helper before adding it. No Java, scenarios/contracts,
dependency/build/workflow changes, lifecycle/fixture/cache redesign, thresholds,
native runtime launches, world edits, deployment, Git mutations or next F0 slice.
Engineer alone edits normative docs/ledger. No recursive delegation.

## Acceptance and verification

1. A deterministic test through the actual runner's extracted/importable
   composition boundary reproduces single-lane failure before correction and
   proves an explicit pending result afterwards. Full local pair comparison
   still fails on divergence; full execution cannot silently drop one half.
2. Real contract-generated lanes plus synthetic diagnostic inputs exercise
   extraction -> shard serialization -> JSON round-trip -> aggregate. Complete
   equal pairs pass. Missing half, differing declared value, wrong view/id/path,
   incomplete projection, duplicate lane and identity/measurement drift fail.
   Equal arrival stages fail; distinct valid stages pass. Exercise declared
   tolerance at and beyond its bound without altering the contract.
3. Ordered non-overlapping synthetic shards pass serial policy and fail parallel
   policy; overlapping shards pass bounded parallel policy and fail serial
   policy. Preserve excessive-start-skew rejection. Exercise both runner call
   sites, not merely an unused new helper.
4. Final timing rejects foreign plan/build/contract, wrong lane count, failed
   status and incomplete samples. Existing exactly-three-sample and >=2.5x
   policies, worker isolation and source identity checks remain unchanged.
5. Focused tests, full Node suite, diff checks and the existing critical gate
   pass. These prove local harness composition, not actual provider execution,
   physical continuity, M3, F0.VA or F0.V closure.

Gate A: ACK design, intended files, initial hashes and exact focused commands;
wait for IMPLEMENTING. Show smallest relevant red regressions before the fix.
Gate B: stop after stable diff and focused tests; main independently reviews
before authorizing full verification. Expected focused command from project:
`node --test tools/frontier-v3-test-pilot/test/ci-matrix.test.mjs tools/frontier-v3-test-pilot/test/f0v-matrix.test.mjs`
(include the new focused test explicitly if created).

After Gate B, main may run `npm test --prefix tools/frontier-v3-test-pilot`
in its loopback-capable environment. Critical gate (one agreed owner only):
`./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar`.
No native matrix or benchmark is granted by this order. Follow existing test
server operations rules before the GameTest launch. Do not weaken tests for
executor sandbox EPERM. Gate C belongs solely to main.

## Stop conditions

Unexpected writer or live measurement, out-of-scope changes, requirement
contradiction, wider evidence redesign or repeated unexplained failure requires
a report before expansion. Preserve the full objective and inherited evidence.

## Review record

Gate A acknowledged on 2026-09-05: executor inspected the current nine allowed
existing files and supplied their pre-edit SHA-256 values. Engineer accepts the
design: one shared declared comparator, explicit selected-lane pending state,
strict plan-bound projection evidence, common cross-lane correctness and
distinct serial/parallel policies. No additional helper is requested.
IMPLEMENTING granted for listed files and the exact two-file focused Node
command above, plus diff checks. Show red regressions before correction; stop
at Gate B. Full gate/native/remote execution remains ungranted.

Gate B reviewed: eight allowed files changed; plan writer is unchanged because
its outer schema-1 envelope embeds the newly schema-2 plan. Main focused tests
passed 23/23. Requested negatives were added for wrong view/id, either missing
differential half, old schema/weak evidence, non-integral arrival, serial order
and all timing identity fields. Source remained frozen during that supplement.
Before evidence is limited: the initial missing-export red only proves API
absence. The old single-half dereference is reproduced from its inspected source
expression, not a retained historical checkout. The retained parallel merger
rejects ordered serial evidence behaviorally. Do not exaggerate this provenance.

Main now owns full Node and the exact critical Gradle gate above. No executor
writes/runs until acceptance or a specific correction. Native matrix, remote
and deployment remain excluded. GameTest resets only the resolved
`pale-mirror-neoforge/build/runs/core-game-test` generated output.

## Gate C acceptance, 2026-09-05

ACCEPTED for local harness composition only. Exactly six sources and the two
existing test files changed. No workflows, contracts/scenarios, Java or lifecycle
implementation changed. All executor grants are closed.

Reviewed working-content identity (SHA-256 of the `sha256sum` output lines for
the following paths in this order, relative to the project):
`05a2f3e478a3f65e2983f281b431ae4168d07d44220f1f9ce34875145e830c8c`.
Under tools/frontier-v3-test-pilot/: src/ci-matrix.mjs, src/f0v-matrix.mjs,
src/run-f0v-matrix.mjs, src/run-ci-matrix-shard.mjs,
src/run-ci-matrix-sequential.mjs, src/merge-ci-matrix.mjs,
test/ci-matrix.test.mjs, test/f0v-matrix.test.mjs. Inherited nested dirty/untracked
entry count remains199; the HEAD alone is not this evidence identity.

- AC-1: runner uses finalizeMatrixEntry; both selected halves yield explicit
  pending evidence, unselected missing halves fail, complete pairs use the
  shared comparator. Source-backed before evidence limitation is retained above.
- AC-2: contract-derived synthetic diagnostics pass extraction, JSON round-trip
  and aggregate. Tests reject either missing half, divergence, duplicate lane,
  foreign build/measurement, malformed projection vocabulary/paths, old weak
  evidence, equal/non-integral arrivals; declared tolerance boundary is tested.
- AC-3: ordered serial intervals pass only serial policy; overlap and order
  failures reject serial evidence, while the original parallel overlap and
  start-skew checks remain. Both actual runner call sites are checked.
- AC-4: final timing rejects failed status, each of plan/build/contract drift
  and wrong lane count before unchanged three-sample median/2.5x admission.
- AC-5: main `npm test --prefix tools/frontier-v3-test-pilot` PASS137/137,
  no failures/skips. Exact critical command above PASS in1m12s,71 tasks
  (37 executed,34 up-to-date); session34561 terminal exit0.

Fresh GameTest log: pale-mirror-neoforge/build/runs/core-game-test/logs/latest.log,
2026-09-05 15:59:43.304+05,290/290 required tests in48.87s; normal shutdown
15:59:57. JAR SHA-256 remains
41ac2b1fe122d03361976f5614c7ef95052e5a94188297a3ff770a8ccdd82d53.
Both repositories' diff checks pass; outer repo retains only the inherited
untracked .f0v-baseline/. No commits, migration, push, deployment or v2 removal.

Native/provider correctness, one persistent client for the actual assigned
matrix, original F0.V physical/crash gates and human M3 remain open. This order
does not certify any of them or reinterpret historical timing evidence.
