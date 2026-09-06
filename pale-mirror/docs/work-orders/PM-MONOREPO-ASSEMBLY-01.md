# PM-MONOREPO-ASSEMBLY-01: isolated history-preserving assembly

Revision2. Parent: post-local-F0.VA monorepo gate. Risk: critical-code
verification infrastructure; no gameplay or durable world changes.
Engineer /root; sole executor /root/terra_crash_completion, Terra high.

## Baseline and prerequisites

Read AGENTS, ledger, engineering protocol, implementation-plan migration gate
and accepted PM-MONOREPO-RECOVERY-01. Apply release-verification; apply server
operations skill if inspecting host processes or runtime logs. No server actions.

Immutable inputs in /home/rd/proj/pm-migration-recovery.OaRskN:
pale-mirror-all.bundle, SHA256
c53ae205ea0d7bda629c87148d53e18a96093e5b4b6374d6ef73712219c76062;
workspace-root-all.bundle, SHA256
4a54192a16364d3c37e8ee44ee436d0a201bd8140eff33ecae50b4d487b3d8d1.
Nested main bafbb8ebb59435ebb50138c8d87dac002ad6054d,
tree 0e3656d4823fd7e8637635e184e11d7d6d6ff252;
outer master 891a4dfe1a613857124775696973d065cc9399cb,
tree 78bfdbee3d9eee32f927b13abb6a6265a7099a57.
Original nested changes after checkpoint are engineer-owned governance only;
do not import them implicitly, stage or commit in either original repository.
Root .f0v-baseline/ and all original linked worktrees/local artifacts stay put.

## Outcome and invariants

Create one unique disposable checkout outside original repos/worktrees and
recovery directory, using mktemp -d under /home/rd/proj/ with prefix
pm-monorepo-assembly. Restore outer history from the verified bundle and import
the exact nested main under pale-mirror/ without squashing. Both old commits
must be ancestors of candidate main. Preserve outer files and source bytes
exactly at the import checkpoint; record tree equality before CI adaptation.
Only the disposable checkout may have its layout changed. It has one root
Git metadata directory, no embedded repository or gitlink; .git is not tracked.

Process safety is scoped to affected paths and evidence inputs: prove no
writer uses the new checkout and no current measurement input is mutated.
Unrelated live JVMs/artifact readers do not require shutdown. Do not build into
original served artifact paths or change common Git metadata/worktrees.

## Write scope and local design authority

Within the new checkout only, executor owns Git assembly, ignore-rule removal,
root CI workflow composition/path adaptation and necessary test-harness/build
path-resolution changes caused solely by the monorepo boundary. Retain both
workflow families. Explicitly account for shell cwd, action paths, artifacts,
cache keys, Git-root/source inventory, selectors and baseline worktree paths.
Git-root discovery must neither omit Pale Mirror inputs nor mistake unrelated
pack changes for proven equivalent source; unknown impact must remain safe.
Terra selects helper design and focused regression tests inside this scope.
No gameplay changes, acceptance relaxation, old evidence rewrite or cache-key
substitution. Historical native/timing evidence remains historical.

Normative Markdown, root/scoped AGENTS and ledger remain engineer-owned.
Report proposed doc updates and exact new checkout path at Gate B; main will
apply them there before final verification. Keep the original ledger canonical
until explicit checkout adoption; imported ledger is a historical snapshot.

## Acceptance and permission gates

- Gate A: ACK exact inputs, outcome, safety, approach and unresolved questions.
  Wait for explicit IMPLEMENTING grant.
- Granted after Gate A: local bundle reads, new checkout creation, non-squashed
  assembly commits needed to establish ancestry/tree checkpoint, implementation
  edits and focused checks. No commit of adaptation edits before review.
- Gate B packet: checkout path, exact import commit/parents/tree proof, stable
  full adaptation diff, chosen commands and focused outcomes, negative tests
  for changed path/inventory logic, original states, required normative edits
  and proposed final gate command/cwd/resource plan.
- Required focused outcomes: both workflow families retained with valid syntax
  and resolved paths; root pack validation discovered from its own instructions;
  full Node pilot suite plus monorepo path regressions where relevant; docs
  guardrails and diff checks. Terra may use ordinary dependency installation
  for these checks but cannot change pinned versions to make migration pass.
- Full Gradle check/GameTest/build/verifyPackagedJar gate must run freshly in
  candidate pale-mirror/ after Gate B, under a separate explicit heavy grant.
  Fresh bootstrap may use ordinary dependency access, not production outputs.
- Gate C will require final ancestry/tree/diff reconciliation, exact verification
  identity, original safety and engineer acceptance. Assembly is not complete
  migration/F0.VA acceptance; remote/provider/original F0.V remain later.

No remote GitHub access/config/push, deployment, service stop, native visible
client, full critical run, cleanup, reset/amend, history rewrite or original
checkout adoption in this grant. Local bundle inputs are not an authorization
to configure the eventual GitHub origin. Retain every attempt and backup.

Stop for input drift, unexpected writers/WIP, collisions or non-equivalent
import trees, public/persistent semantic changes, needed external authority,
or contradictory requirements. Report a stable milestone/blocker; no routine
heartbeat. Main reviews outcomes, not intermediate helper choices.

## Revision2: pack validation boundary correction

Terra stopped after packwiz refresh changed candidate pack/index manifests.
Main read the actual diff in /home/rd/proj/pm-monorepo-assembly.VJM6JZ:
six executor-created .assembly-* evidence files and two relocated CI workflows
were newly indexed, and scripts/validate.sh hash changed with its wrapper-JAR
exception. pack.toml changed only its index hash. This is explained candidate
adaptation/evidence contamination, not evidence of unrelated baseline drift.
No pack validation waiver and no dependency/content upgrade is authorized.

Preserve both candidates (including failed bootstrap R8Amtm), the failed
generated manifests and logs as evidence. Continue in VJM6JZ from import
9f3bcb12affc74b98fc000a58ecff782f9ea77c7; no reimport needed.
Allowed scope now explicitly includes root pack-validation scripts, packwiz
exclusion configuration and derived index/pack hashes necessary for this
repository-layout migration. Engineering/CI/evidence files must not become
player-pack payload. Do not use blanket exclusions that hide real pack assets.
Keep diagnostic outputs outside pack inputs or in an explicitly excluded
evidence location; retain originals before any relocation or regeneration.

Terra owns a bounded correction and regression proving source/build/evidence/CI
exclusion while legitimate pack assets remain indexed and forbidden pack JARs
remain rejected. Account for ordinary Pale Mirror generated build JARs too:
the root pack check must enforce pack payload policy without rejecting valid
source-project build outputs excluded from the pack. No blanket permission
to commit arbitrary binary dependencies in the source project.

After the negative regression/preflight, regenerate candidate-only derived
metadata, prove the exact semantic delta against the immutable outer manifest
and run refresh again to prove idempotence. Expected changes are the reviewed
validation-script hash (and any other explicitly explained migration-only
indexed file hash) plus resulting index hash; no mod versions, downloadable
artifacts, gameplay configs, assets or unaccounted payload additions/removals.
Fresh failed artifacts remain evidence, never relabelled green. No reset or
deletion is needed; preserve before overwriting these exact generated files.

Gate A amendment: acknowledge cause/scope, then continuation IMPLEMENTING is
granted for this bounded correction plus the prior focused assembly scope.
Gate B still requires complete diff/evidence review; adaptation commit, full
critical/native/remote/adoption remain closed. Report unexplained residual
payload changes, not routine expected derived-hash changes, as input drift.

## Gate B review: changes required

Engineer review on 2026-09-06 independently confirms import9f3bcb12 has exact
parents891a4dfe/bafbb8e and source tree0e3656d. Candidate stays VJM6JZ.
Terra reports177/177Node and34guardrails, plus scoped pack negative/idempotence
evidence. These do not close the following missed migration invariants:

1. Actual fingerprintPreparedSource(candidate/pale-mirror) returned1537 entries
   and workflowEntries=[] after relocation. PREPARED_SOURCE_PREFIXES still
   names .github/workflows/ relative to the source subproject. Root workflows
   remain evidence-producing inputs, not unrelated pack content. Preserve
   their exact dirty/untracked content contribution in prepared identity and
   affected selection/cache boundaries. Add focused regressions proving root
   workflow content changes invalidate relevant identity/evidence and are not
   omitted; preserve explicit separation from unrelated root pack payload.
   The current inventory test only proves absence of parent paths and misses
   this required positive coverage. Terra selects safe path representation
   and local implementation; no requirement to encode unsafe ../ traversal.
2. scripts/validate.sh removed its stale-lock rejection and now compares the
   pack/index hashes only AFTER packwiz refresh rewrites both. A structurally
   valid but stale lock can therefore be silently repaired and accepted.
   Restore validation of the incoming checked snapshot, including both pack
   and index metadata, without forcing every legitimate reviewed WIP lock
   update to be committed before validation. Explicit regeneration for this
   migration is allowed; ordinary validation must not bless its own repair.
   Require checked-in regressions for stale valid payload hash/addition/removal,
   pack index hash drift, ordinary synchronized input and repeated validation.
   Preserve failed input/output evidence; do not merely rerun on repaired files.

Also make the new payload policy's promised malformed/duplicate/excluded and
genuine nested-worldgen coverage repeatable in checked-in focused tests, not
only retained one-off fixtures. Verify path normalization cannot bypass its
source/CI/evidence/JAR exclusions. Report any additional gap found in the
affected selector/cache/path boundaries within the same scope.

Existing bounded IMPLEMENTING/focused correction authority renewed for sole
Terra; no new order needed for helper choices. Return revised stable Gate B.
Main has not yet applied candidate normative docs; full critical/native,
adaptation commit, remote/adoption/service authority remain closed.

### Revised Gate B: changed-path coordinate correction

Main read the revised implementation and checked actual read-only discovery in
the candidate. Root workflows now contribute actual bytes to prepared/cache
identity, and incoming pack validation no longer refreshes its own inputs.
Those corrections are directionally accepted; final assembly acceptance waits.

One remaining migration defect: `git diff --name-only HEAD` from the nested
cwd still returns repository-root paths, whereas `git ls-files --others`
returns project-relative paths. Actual discoverChangedPaths returns tracked
`pale-mirror/tools/...` together with untracked `tools/...`, root pack.toml and
scripts/validate.sh. Registered source owners become UNKNOWN_PATH and select
conservative tiers. The new regression changes only a tracked root workflow
and an UNTRACKED root pack file, so it does not expose this inconsistency.

Continue the same bounded correction: establish one explicit coordinate
boundary for tracked/staged/deleted/renamed and untracked project changes,
separate unrelated root pack inputs, and retain relevant root CI changes.
Unknown relevant CI/source changes must fail closed or remain conservative,
not disappear through an exact-known-workflow filter. Prove these outcomes
with genuine Git fixtures, including a tracked root pack mutation and a
tracked owned project mutation; prove selective owner resolution is retained.
Terra owns implementation choices and focused iteration. No gameplay changes,
full critical/native run, commit, remote or adoption authority yet.

Main will settle migration-plan wording in both original and candidate before
the next guardrails gate. Return one stable revised packet, no heartbeats.

## Gate B accepted; fresh critical verification grant

Engineer accepted the revised stable Gate B on 2026-09-06 after reading the
implementation and genuine Git regressions. Independent read-only execution
now returns normalized project coordinates and root workflows only, with no
pack/index/script leakage. Prepared source has1539 entries, SHA256
37081dda3cb9a64c5ca530c7aef2b9eef1826717945ec1dce5274f3dac6858e7;
both root workflow byte records are present. Unknown workflow discovery and
identity use the whole workflow prefix, not a two-file omission filter.
Incoming pack validation and reviewed migration-only lock delta accepted at
this boundary. Terra reports focused Node30/30, full Node180/180, Python3/3,
root validation and candidate guardrails34tasks passing. This is not full
critical, migration, provider or product acceptance.

Candidate AGENTS and implementation-plan edits by main are settled. Original
HEADs remain891a4dfe/bafbb8e; original outer retains only .f0v-baseline/ and
original nested only the documented engineer-owned normative changes.

Sole Terra is granted one fresh critical invocation, cwd exactly
/home/rd/proj/pm-monorepo-assembly.VJM6JZ/pale-mirror:

```bash
./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar --no-daemon
```

Before launch apply the required release/server-operation skills and prove
exclusive affected-checkout/heavy ownership, candidate-only output paths and
no collision with live services. This grants the normal disposable GameTest
server for that task, not a visible client or a native scenario/matrix. Leave
unrelated original live services and artifact hosts untouched. Ordinary pinned
dependency bootstrap is permitted; no clean, timeout inflation, source edits,
cache bypass substitution or historical result rewrite.

Retain fresh command/exit/report evidence and artifact checksum under excluded
candidate build evidence. Report exact test counts, task outcomes, packaged-JAR
receipt, final diff/status/ancestry reconciliation, any generated untracked
files, original safety and remaining gates. Do not stage diagnostic logs or
Python bytecode. On failure preserve evidence, diagnose read-only and report
before any repair or rerun. Freeze after completion; adaptation commit,
remote/push, checkout adoption, deployment and additional native runs still
require their separate gates. Governance-record transfer remains main-owned.

## Gate C technical acceptance

Engineer independently read the completed candidate critical receipt and
matched its SHA256 bff6f7c807aadc139507cbc32ae3f304154b06792fd85f0bdc88ba32a7c7d978.
One invocation succeeded in1m25s,70tasks (55executed/15from cache); GameTest
290/290 in52.33s with saved worlds and clean shutdown. JUnit XML totals by
module: domain324/77reports, frontier564/98, neoforge222/46, visuals79/18;
1189tests/239reports overall, zero failures/errors (these Test tasks reused
ordinary Gradle build-cache results, not freshly executed test JVMs).
verifyPackagedJar executed successfully; actual packaged JAR SHA256
96197fc6e15d0e0188907f4870ccce69c12d24f46b3d0f87aff3152f032a2ad9.
Main found no candidate Gradle/GameTest process remaining and independently
confirmed exact import parents/tree and both cached/working diff checks.
Original repositories retain only their previously documented state.

This accepts local assembly's technical gate, not migration/F0.VA completion,
new native proof or product acceptance. Before an adaptation commit, main
must reconcile the post-checkpoint governance records into the candidate;
then run the docs gate with explicit staged-file hygiene. No technical rerun
is required merely for excluded governance Markdown. No commit grant yet.

## Governance reconciliation and conditional local commit grant

Main now supplies exact copies of this order, the accepted recovery order and
docs/archive/CONTINUITY_2026-09-06_recovery_handoff.md to the candidate. Its
imported ledger receives only an explicit historical-snapshot notice linking
these records; the original ledger remains the one active authority. This is
not checkout adoption. Root/scoped AGENTS and the migration-plan corrections
were already covered by Gate C.

Sole Terra may perform the final docs/pre-commit gate and then ONE local,
non-amending adaptation commit on candidate main, only if all following
conditions pass. No additional permission round is needed for routine staging
or the commit when these conditions match exactly:

- Original and candidate HEADs still match the accepted checkpoint; no other
  writer, unreviewed source changes or unexpected staged paths.
- Candidate ./gradlew guardrails --no-daemon from pale-mirror/ passes after
  supplied governance records; root pack validation and working/cached diff
  checks pass without regeneration. No full critical/native rerun.
- Stage only the following exact paths (including the two relocated workflow
  deletions), verifying bytes/modes against the frozen reviewed worktree:

```text
.github/workflows/build.yml
.github/workflows/f0va-native-correctness-sample.yml
.github/scripts/validate-pack-payload-index.py
.github/scripts/test_validate_pack_payload_index.py
.packwizignore
AGENTS.md
index.toml
pack.toml
scripts/validate.sh
pale-mirror/.github/workflows/build.yml
pale-mirror/.github/workflows/f0va-native-correctness-sample.yml
pale-mirror/AGENTS.md
pale-mirror/CONTINUITY.md
pale-mirror/docs/frontier-v3-implementation-plan.md
pale-mirror/docs/work-orders/PM-MONOREPO-RECOVERY-01.md
pale-mirror/docs/work-orders/PM-MONOREPO-ASSEMBLY-01.md
pale-mirror/docs/archive/CONTINUITY_2026-09-06_recovery_handoff.md
pale-mirror/tools/frontier-v3-test-pilot/src/evidence-cache.mjs
pale-mirror/tools/frontier-v3-test-pilot/src/prepared-build.mjs
pale-mirror/tools/frontier-v3-test-pilot/src/verification-runner.mjs
pale-mirror/tools/frontier-v3-test-pilot/test/ci-matrix.test.mjs
pale-mirror/tools/frontier-v3-test-pilot/test/monorepo-ci-layout.test.mjs
pale-mirror/tools/frontier-v3-test-pilot/test/prepared-build.test.mjs
pale-mirror/tools/frontier-v3-test-pilot/test/verification-runner.test.mjs
```

- No logs, .assembly-* files, Python bytecode, build outputs, new binaries or
  secrets enter the commit. Keep existing diagnostics locally; do not delete
  or hide them to manufacture a clean status.
- Use a concise Conventional Commit identifying the monorepo/verification
  adaptation. Preserve import9f3bcb12 as its exact parent and prove both old
  histories remain ancestors. Verify the committed changed-path/blob/mode set
  matches the frozen inventory, and source fingerprint remains37081dda...e8e7.
  If identity differs, diagnose and report rather than rewriting evidence.

Freeze and return exact commit/parent/tree, frozen inventory location, checks,
artifact/source identities, remaining dirty files and original statuses.
No source repairs, resets/amends, remote access/config/push, cleanup, deployment,
native run, provider job or checkout adoption is authorized. Stop on a failed
condition; do not silently broaden this grant. Main independently accepts the
result before issuing the destination-inspection/push gate.
