# PM-F0VA-TIMING-POLICY-01: advisory restart target

Revision:1. Parent:F0.VA. Risk:small-code (test-reporting policy only).
Engineer:/root. Executor:/root/terra_executor, gpt-5.6-terra high.
Specification READY; grants are recorded in CONTINUITY.md.

## Outcome and authority

Implement the user's 2026-09-05 amendment:25 percent is an advisory restart
optimization target; the measured22.52 percent is sufficient. Stop spending
native measurements on that gap. This is an explicit requirement change, not
weakening a correctness assertion to conceal a defect.

Preserve all inherited WIP and historical reports/bundles. PM-F0VA-VERIFY-01
preparation/native launch was revoked before execution. Its diff/docs checks
passed; no new prepared identity or native world was created.

## Contract and allowed files

- `tools/frontier-v3-test-pilot/src/timing.mjs`: successful valid same-identity
  three-plus-three samples return exact medians/improvement.25 percent is named
  as a target, not a required minimum; expose whether it was met explicitly.
  A positive22.52-percent result is valid. Zero improvement or regression still
  fails visibly for engineering review; do not silently accept no benefit.
- Version the changed comparison policy in its returned evidence so reports
  cannot confuse the previous hard-floor policy with advisory semantics.
- `tools/frontier-v3-test-pilot/test/timing.test.mjs`: deterministic policy,
  identity, malformed/failed/incomplete sample and regression tests.
- `tools/frontier-v3-test-pilot/src/run-f0v-timing.mjs` only if necessary to
  expose the versioned comparison result correctly. It must retain exactly
  three baseline/three candidate executions and existing failure handling.

Existing comparison callers are confined to this runner and timing tests;
recheck before editing and report unexpected consumers. An extra focused test
file under the same test directory may be proposed; no source boundary growth
without review. Do not add legacy compatibility for the old parameter meaning.

Prohibited: changing native lifecycle/assertions/timeouts, Java/runtime/SDK,
scenario references, prepared-build fingerprints, other timing comparators,
3x/2.5x thresholds, dependency selection, CI workers, docs/ledger or old outputs.
No commit, deployment, migration or external writes under this order.

## Acceptance and gates

1. Valid same-identity samples yielding22.52 percent succeed, retain actual
   values and explicitly report target-not-met under a versioned policy.
2. A result >=25 percent reports target-met; invalid target configuration,
   wrong identity, missing/failed/malformed samples still fail closed.
3. Zero benefit and regression remain visible failures, not accepted speedup.
4. The CLI preserves sample counts, real recovery checks and failure bundles;
   unrelated3x/2.5x policies and historical files remain unchanged.
5. Focused timing tests, full Node harness tests and small-code
   `./gradlew guardrails check` pass. Report inherited/unrelated failures
   separately without fixing them under this order.

Gate A: acknowledge scope/invariants and exact tests, then await IMPLEMENTING
grant. Implement only the bounded change and run
`node --test tools/frontier-v3-test-pilot/test/timing.test.mjs` plus
`git diff --check`; submit diff and evidence for Gate B.

Gate B: after engineer review, run full Node tests and the small-code gate
above. No native run, new preparation or fresh world is needed to test this
reporting-policy change. Gate C is independent engineer acceptance, not full
F0.VA closure. Report next remaining technical gate; do not selfadvance.

## Engineer acceptance, 2026-09-05

Gate C: ACCEPTED for this bounded policy change. Terra changed only timing.mjs
and timing.test.mjs; main reviewed both and the unchanged runner's serialization.
Evidence retains exact medians, version2 advisory policy and targetMet; zero or
negative benefit, invalid identity/configuration and incomplete/failed samples
still reject. Historical outputs and3x/2.5x comparators were not changed.

Focused timing tests and diff checks pass. Terra's `./gradlew guardrails check`
passed (67 tasks,17 seconds). Its full Node run encountered EPERM when the RCON
mock tried to listen on loopback, before test logic; no test was weakened.
Main ran the same `npm test --prefix tools/frontier-v3-test-pilot` in its allowed
environment:129/129 pass, zero skipped or failed. No native/preparation run.
This accepts the reporting policy, not remaining local/provider F0.VA gates.
