# PM-F0VA-CI-BOOTSTRAP-01: provision isolated cold Gradle homes

Revision:1. Parent:F0.VA. Risk:small-code (CI preparation, no game behavior).
Engineer:/root. Executor:/root/terra_executor, gpt-5.6-terra high.
Specification READY; no write grant until Gate A acknowledgement.

## Defect and desired transition

Both native workflows allocate unique runner.temp GRADLE_USER_HOME values.
Preparation calls prepare-native-build.mjs, which first invokes Gradle offline;
worker and sequential jobs likewise first prepare offline. No dependency-cache
transfer or earlier resolution seeds those private homes. This cannot bootstrap
a clean isolated worker. Local warm caches are not evidence of portability.

Each of three job forms (f0va-prepare, reusable native-correctness,
f0va-sequential-timing) must explicitly resolve its exact native Gradle
environment online in its own private home before the existing offline
preparation/identity step. Use the existing task
`:pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment --no-daemon`
without --offline for this bounded setup step. The subsequent existing
offline commands and content identity checks remain mandatory. Failure stops
the job visibly; there is no automatic online fallback during measurement.

Keep immutable worker-matrix admission before any worker Gradle step. Bootstrap
is outside measured lane intervals. Do not share mutable Gradle homes, alter
worker/port/display isolation, sample counts,3x/2.5x targets, merge completeness,
native assertions, or the monorepo/provider order.

## Allowed files and exclusions

- `.github/workflows/build.yml`
- `.github/workflows/f0va-native-correctness-sample.yml`
- `tools/frontier-v3-test-pilot/test/ci-matrix.test.mjs`, or one new adjacent
  `ci-bootstrap.test.mjs` if that keeps the test focused.

No Java, runtime harness, scenarios, dependencies/lockfiles, source fingerprints,
docs/ledger, local installs, real dependency download, native client/server,
remote jobs, commits or migration. Do not add npm provisioning on the false
premise that native Java pilot execution imports the unrelated Mineflayer
pilot.mjs. Node test-job provisioning is a separate concern if needed later.

## Acceptance and verification

1. All three native job forms explicitly bootstrap their own cold Gradle home
   before first offline preparation. Setup failure cannot proceed to proof.
2. Worker admission still precedes bootstrap, and identity/portable classpath
   verification remains after it; no unchecked native fallback is added.
3. Deterministic workflow-contract tests fail on removing bootstrap, moving it
   after offline preparation, making it offline, or placing worker admission
   after Gradle. Tests inspect each scoped job, not a global matching string.
4. Existing3x4 workers,13 semantic lanes, timing/merge/failure policies remain.

Gate A: ACK scope, intended exact edits and tests; then wait for IMPLEMENTING.
First add the minimal regression and show it fails on existing workflows;
then fix configuration and run focused ci-matrix/bootstrap Node tests plus
git diff --check. Retain a compact before/after test result. Gate B is main's
diff/ordering/negative-test review before full Node and guardrails check.
Main can run the full Node suite if Terra still has loopback EPERM.

Gate C accepts locally checked workflow preparation only. Actual clean-runner
download/native execution remains UNCONFIRMED_EXTERNAL until the ordered real
provider run. Do not claim the provider gate green from static tests.

## Engineer acceptance, 2026-09-05

Gate C ACCEPTED. Exactly two workflow files and ci-matrix.test.mjs changed.
The new scoped regression failed11/12 before configuration correction, then
passed with missing/moved/offline/bootstrap-admission mutation negatives.
Main reviewed all three job forms and unchanged offline identity steps.
Main full Node suite:130/130 passed, none skipped. Terra guardrails check:
67 tasks,16 seconds, successful. Tracked and inherited-untracked diff checks
passed. No actual online bootstrap, native or provider run was performed.

Provider evidence remains open. Separate source review identified a later
cross-shard differential-comparison defect; this order does not fix or accept it.
