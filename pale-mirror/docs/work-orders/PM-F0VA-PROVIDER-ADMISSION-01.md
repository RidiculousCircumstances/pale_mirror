# PM-F0VA-PROVIDER-ADMISSION-01: diagnose workflow admission

Revision1. READ_ONLY. Engineer /root; sole executor Terra high.
Parent: published monorepo05712315; not a native/provider execution grant.

## Evidence and outcome

REMOTE-01 published exact main05712315a81bff8a3a42207fcb736b729ca82704,
tree f5b5750401c9bc03be8dcaf3214dbb452874eb22. Independent clone:
/home/rd/proj/pm-monorepo-remote-verify.wqPlX1. Original ledger remains
canonical; read AGENTS, ledger, this order and release-verification skill.

Automatic runs34010352733 (build) and34010352471 (native reusable workflow)
failed with no jobs/check-runs/logs. Main independently confirmed first run's
failure, empty jobs and exact headSha. Terra reports Actions enabled and zero
registered repository runners. Missing self-hosted labels do not explain
ordinary ubuntu-latest core admission failure. Do not guess or rerun blindly.

Diagnose the actual provider admission error using existing read-only gh/API
run/check-suite/annotation/workflow data and exact committed YAML. Existing
local static validators may be used if non-mutating; no test/server launch.
When needed consult official GitHub documentation. Distinguish YAML/schema/
expression/reusable-workflow admission from account policy, Actions settings,
billing/access and missing runner capacity. Do not infer an outage from absent
logs alone, or silently rewrite an invalid workflow as a runner problem.

Return concrete supporting evidence/locations, smallest coherent proposed
correction, recurrence test/static gate and affected identity boundaries.
Separately report prerequisites for the future actual four-worker correctness
matrix and serialized pinned-host timing: existing runner capacity/labels,
isolation/resources and any missing authority. No requirement to solve those
later prerequisites during this diagnosis.

## Boundaries

Read-only access to this repository and its Actions metadata is granted under
the user's gh authority. Do not print secrets, tokens or private key material.
No source/tests/workflow/docs/config edits, installs, commits/pushes, reruns or
workflow_dispatch, runner registration, account/repository settings changes,
paid provisioning, cleanup, adoption or live services. Do not create a second
active ledger. Main will issue a bounded implementation grant after reviewing
the cause and proposed proof. Report once at diagnosis completion or a genuine
access blocker; no routine heartbeat.

## Revision2: accepted diagnosis and bounded correction grant

The READ_ONLY phase is complete. Terra reports GitHub annotations rejecting
runner.temp at build.yml lines47/112 and native-sample.yml line31. Main read
all three committed job-level env declarations and independently checked the
official context-availability table:
https://docs.github.com/en/actions/reference/workflows-and-actions/contexts
jobs.<job_id>.env does not admit runner context; step contexts do. This is a
workflow-schema defect, independent of the separately absent native runners.
Original failed runs remain failed evidence. No account-policy denial proved.

IMPLEMENTING is now granted to sole Terra in
/home/rd/proj/pm-monorepo-assembly.VJM6JZ only, starting at exact published
05712315a81bff8a3a42207fcb736b729ca82704. Risk small-code CI configuration
and recurrence tests; no simulation, persistence or runtime-lifecycle change.
Keep remote-verify.wqPlX1 as the independent immutable publication checkpoint.

Outcome: the three isolated Gradle homes are initialized on the executing
runner using RUNNER_TEMP and persisted to subsequent steps through GITHUB_ENV,
without illegal job-env context evaluation. Retain their exact preparation,
timing and measurement/worker-specific identities, matrix/ports/labels,
bootstrap-online then proof-offline ordering and worker admission-before-Gradle.
The initializer must run in an existing cwd and before any relevant Gradle
use; protect shell/environment-file construction from unsafe values. Preserve
all other non-Gradle job environment fields. Do not relax identity, admission,
timing thresholds or resource isolation to make provider validation pass.

Expected scope is the two root workflows and existing ci-matrix.test.mjs;
Terra owns local helper/test design where needed inside this exact CI boundary.
Add repeatable regression(s) that reject the original invalid job-level
runner expressions and missing/late/unsafe initialization, while retaining
the existing private-home/bootstrap ordering proofs. Do not reject legitimate
runner context in step-level fields. Ordinary push must remain core-only;
the reusable sample remains workflow_call-only and native matrix stays gated.

Granted verification: focused CI/path/identity Node tests, full pilot Node
suite, root pack validation without refresh, diff checks and one sequential
candidate ./gradlew guardrails check --no-daemon from pale-mirror/. Terra is
the sole heavy owner for that check; no GameTest/native/visible client or
extra full critical build. Preserve all original services/artifact paths and
all diagnostics. Ordinary pinned dependencies allowed, no version upgrade or
new infrastructure/tool installation. Retain changed source identity honestly;
old prepared/native evidence cannot be relabelled for changed workflow bytes.

Return stable Gate B: cause-to-fix mapping, full diff, negative proofs/checks,
new source identity and remaining limits. Main reviews before any commit or
push. No remote mutation, rerun/dispatch, runner installation/settings/security/
billing change, original source edit, cleanup, adoption or deployment in this
grant. Report unexpected failures with their evidence; no blind heavy retry.

## Gate B review: remaining narrow correction

Main reviewed the complete three-file diff. The correction removes the three
illegal job-env expressions, initializes after checkout and before Gradle,
retains isolation/admission ordering and adds historical-failure regressions.
Terra reports focused16/16, full Node180/180, pack179 and the granted
guardrails check green. These remain scoped local evidence, not provider proof.

Before acceptance, replace the generic shell variable `home` in all three
initializers with a task-specific variable, adapting the matching tests.
Session safety instructions prohibit repurposing `home`/`HOME`; this is a
naming-only correction, not a request to redesign initialization. Terra owns
the implementation. Rerun focused tests and the small-code gate on final bytes
(one sequential guardrails check granted), retain the previous receipts and
report the new identity. Main's normative review-note changes also require
original docs diff-check/guardrails, which Terra may execute sequentially
without editing original files. No commit/push or native/provider authority
is added. Return one stable result for acceptance.

## Revision3: accepted local correction; publication and core admission

Main accepts the final complete three-file diff after independent inspection
and actual prepared-source fingerprint equality:1539 entries,
0ad76a4ae0a672f330fdb8b99b903b364c72a80bc71726186056dda58ebdf513.
Terra's final focused16/16 and candidate guardrails check66tasks/18s passed;
original docs guardrails34tasks/18s passed. Full Node180/180 predates only the
naming correction; it is not represented as a final-byte full-suite run.
Main independently checked originals' dirty state and candidate exact scope.
This accepts small-code CI correction only; remote admission is still unproved.

Sole Terra may now publish the accepted correction from candidate VJM6JZ:

1. Freeze and retain the final path/blob/mode inventory and available test
   receipts. Require HEAD05712315a81bff8a3a42207fcb736b729ca82704,
   unchanged accepted source identity and no unknown staged files.
2. Stage and make one Conventional Commit containing ONLY these three paths:

   ```text
   .github/workflows/build.yml
   .github/workflows/f0va-native-correctness-sample.yml
   pale-mirror/tools/frontier-v3-test-pilot/test/ci-matrix.test.mjs
   ```

   Require exact parent, staged diff-check and inventory equality. Do not
   include diagnostics, candidate governance or original WIP. No amend/rebase.
3. Apply REMOTE-01 destination/ref/ancestry safety checks to this new child.
   Exact origin remains git@github.com:RidiculousCircumstances/pale_mirror.git.
   Remote main must still be05712315; unexpected advancement requires review.
   Publish this child main once, nonforce, no other refs or settings changes.
4. Verify exact remote HEAD/tree in a NEW independent remote-only clone under
   /home/rd/proj/pm-monorepo-remote-verify.*; keep wqPlX1 immutable. Reuse
   REMOTE-01 clone invariants and focused pack/identity/selector verification,
   with pinned npm ci permitted. Do not run local Gradle/native in that clone.
5. Observe the automatically triggered core workflow on the exact new SHA
   through gh until terminal or a genuine provider/access blocker. Record run
   URL, admitted job/steps, conclusion and errors. Admission and green core are
   distinct claims. Do not manually rerun/dispatch, fix code or change settings
   on failure; diagnose read-only and return evidence. Ordinary GitHub-hosted
   core build is the intended effect of publication, not a native-matrix grant.

Return commit/tree/inventory, remote and clone equality, source identity,
verification receipts and core evidence plus original/candidate dirty states.
Original normative docs diff-check/guardrails may run once sequentially after
this main-authored revision, without original edits. No native runners, paid
resources, security/visibility changes, deployment, cleanup or adoption.
