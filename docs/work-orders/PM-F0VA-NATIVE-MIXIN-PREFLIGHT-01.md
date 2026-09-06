# PM-F0VA-NATIVE-MIXIN-PREFLIGHT-01

Revision: 1. Parent: F0.VA / PM-F0VA-NATIVE-QUAL-01.
Risk: critical-code, test-pilot startup readiness.
Executor: `/root/terra_crash_completion`, gpt-5.6-terra high.
State: ACCEPTED. Gate C verified; all source/verification grants CLOSED.
Native qualification is owned separately by the parent revision8 amendment.

## Outcome and baseline

Correct the demonstrated false-negative applied-mixin preflight without
weakening its fail-closed guarantee or changing actual crash-window behavior.
`PM-F0VA-NATIVE-MIXIN-DIAGNOSTIC-01` Gate C retains exact source/native/API
evidence: transformation succeeded but lookup queried the target cache key.
Current Mixin0.8.7 postApply records successful application on mixin ClassInfo.
Both repository HEADs and inherited WIP remain as in CONTINUITY.md; all previous
run grants are closed and retained worlds/evidence must survive unchanged.

## Contract and ownership

- Selected crash boundary must require successful application of its exact
  pilot mixin before server readiness. Empty/foreign/unapplied evidence fails
  closed; an unarmed ordinary launch retains its existing behavior.
- Preserve the exact one-target mapping: durable crash mixin targets
  FrontierStoreTransactionCommitter; harvest crash mixin targets
  FrontierV3ResourceSiteHarvestSceneExecutor. Enforce the one-target assumption
  with a deterministic regression guard. Multi-target expansion requires a
  new proof, not acceptance merely because one target succeeded.
- No reflective mixin-class loading, canonical writes, chunk loads, synthetic
  arm completion, plugin framework or production hook changes. Preserve all
  five boundary mappings, typed job/lease matching and sticky ambiguity rules.
- Only the pilot preflight owns this correction; canonical state, persistence,
  crash park/release, scenario meanings and production artifact remain unchanged.

## Implementation boundary and autonomy

Terra owns local design, helpers and focused test iterations in the NeoForge
pilot crash-preflight component and its corresponding tests. Expected starting
files are FrontierV3CrashBoundaryProbe.java and its test; adjacent pilot-local
helpers/test fixtures may be chosen as needed. Existing mixin declarations are
inspection/test inputs, not permission to change their injection semantics.
No main/runtime/domain, Gradle, runner, CI or scenario edits. Report a boundary
mismatch instead of broadening scope. Engineer alone updates normative docs.

## Acceptance and verification

1. Show a deterministic failing regression for the old lookup and passing
   corrected behavior, covering both exact mixin identities and all boundary
   selections, absent/foreign evidence and exact single-target declarations.
2. Preserve existing crash-probe positive/negative/ambiguity tests. Tests must
   exercise the production preflight selection path, not only compare constants.
3. Run focused Java tests, existing Node schema suite and documentation
   diff/guardrails; report stable diff, commands/results and local design choices
   at Gate B. No routine intermediate approvals are needed.
4. After Gate B authorization run the existing full critical gate:
   `./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar --no-daemon`.
   Prove pilot classes/resources remain absent from the packaged JAR and report
   both repository states separately. Main reviews exact final evidence.
5. Source acceptance does not prove native reachability or recovery. Fresh
   parent worker3 qualification follows by a separate run grant; no native
   launch, benchmark, commit, migration, push, deploy or live-service operation
   is authorized here. Never run module clean or delete retained exports/worlds.

## Gate B review

Implementation and test structure reviewed; no local design rewrite requested.
Reported green:17Java tests,169Node tests,34guardrails. Initial baseline failed
to compile because it lacked the injected seam, which does not demonstrate
behavioral detection of the old lookup. Before full verification, Terra retains
the new seam/tests, temporarily restores only the old target-key behavior,
proves the intended test assertion fails, then restores the exact reviewed
source and proves green. This focused mutation is within the current grant.
Reviewed source SHA c05fb8c7192ae9fdba454b7330a57aac9272b29095429086b64823aba5872db9;
test SHA 0bd83f2376615a1b4a39d7e3acffba280997d00ec1d2e0b8d9c356099f0d5555.

Behavioral red reported17tests/1intended assertion failure after changing only
the lookup back to target-class name; the test required the durable mixin key.
The red XML was overwritten by the subsequent green; executor tool output
retains the failure, not a separate persistent red XML artifact. Main reviewed
the shared selection test and independently verified restored source hashes and
the noncached final17/17XML at2026-09-05T18:56:16.682Z. Gate B accepted.
Run the exact full gate above without source edits; retain terminal output,
actual GameTest/save evidence and packaged-JAR exclusion/hash. Report the first
failure without retry. No client qualification or benchmark is granted.

## Gate C acceptance

Exact full gate passed in2m11s,71tasks (38executed,33up-to-date). Main verified
unchanged source/test hashes and core-game-test/logs/latest.log:290required
tests passed2026-09-05 23:58:41.503; all-dimensions save23:58:50.931.
JAR SHA4ea05b2cec716dd53980a57d0016631df3fb1af311a11a774a6a259f52e4ce5a;
pilot components remain absent. Both repository diff checks passed; nested
dirty208/root dirty1 with unchanged HEADs. No source acceptance closes F0.VA.
