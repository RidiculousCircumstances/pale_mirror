# PM-F0VA-PERSISTENT-CRASH-01: authenticated expected server loss

Revision: 3. Parent: F0.VA. Risk: critical-code (pilot/session lifecycle).
Status: ACCEPTED at Gate C,2026-09-05; protocol prerequisite only, not F0.VA closure.
Engineer: /root. Executor: /root/terra_crash_completion, gpt-5.6-terra high.
Specification READY; acknowledge Gate A before any writes or tests.

## Baseline and outcome

PM-F0VA-PERSISTENT-PLAN-01 is ACCEPTED. Preserve its compiler and exact shard
assignment, all inherited WIP, both histories and current21 reports. Main
observes201 nested dirty/untracked entries, outer .f0v-baseline/ only, executor
idle and no active native/Gradle harness process at order preparation.

Implement the expected-crash control/acknowledgement boundary in the existing
Node persistent-session owner and Java pilot session/logout owner. This enables
one living client to survive a declared server crash without reporting normal
disconnect or terminal success. Actual assigned-worker orchestration remains a
following integration order; this prerequisite alone cannot close F0.VA.1.

Read AGENTS/ledger, engineering-agent-protocol, accepted design/compiler orders,
architecture.yml harness/causality flow, execution-semantics section6 and
accelerated-verification-loop F0.VA.1/.5, plus matching mandatory skills.

## Contract and owner transitions

- Compiler owns immutable membership and original crash declaration. Existing
  Node session owner owns external control publication and ordering; existing
  crash controller alone owns matching the actual probe and firing once on the
  exact nonce-owned server PID. Java only acknowledges what its connection
  actually observed. No canonical/world authority or second coordinator.
- A crash arm binds lifecycle identity (build/worker/session/run/nonce), exact
  active epoch/segment/scenario hash, world/lane identity and exact server run/PID
  plus the declared boundary/owner/payload/revision-or-epoch. Use bounded,
  versioned, immutable exclusive publication, never a mutable global marker.
  An arm is permission to EXPECT loss, not proof of causality or server death.
- The Java active session authenticates the arm and acknowledges readiness
  before the supervisor may trigger the fault. Arming must not halt the ordinary
  player action that reaches the crash boundary, request disconnect or complete
  the segment. Reject arms for an ordinary, final or recovered segment. Admission
  must derive from its immutable descriptor, not the presence of an arm alone.
- Actual LoggingOut for the armed active connection claims one expected-loss
  acknowledgement. Duplicate callbacks cannot duplicate evidence or turn a
  publication failure into a retry. Clear transient inputs/target/frame/diagnostic
  state; preserve only session identity needed to await recovery. No normal
  disconnect signal and no fabricated terminal assertion for the crash half.
- Unexpected loss, failed reconnect, foreign/malformed/stale arm, publication
  failure or client death is fatal/failed, not a reset that silently resumes.
  A claimed loss may wait for supervisor verification but must not independently
  authorize reconnect. Node must combine exact fired-controller evidence, owned
  JVM termination, actual same-client loss acknowledgement and port closure;
  none substitutes for another. Early unrelated loss with no matching fired
  controller cannot be accepted as the expected crash.
- Recovery release binds the exact successor epoch/world and replacement server
  run/readiness. It requires the complete verified crash boundary; a plain old
  resume token cannot bypass it. Login clears transient state and consumes the
  exact next executable scenario; recovered halves are never re-armed. Retain
  existing ordinary/graceful/final-close semantics and existing benchmark proof
  requirement of three independent worlds.
- Account for callback/observation races explicitly. The Java loss signal is an
  observation, not a supervisor journal event. The supervisor may serialize
  already-observed independent acknowledgements only after validating all their
  identities, not invent their wall-clock order. No sleeps-as-ack, fresh client
  fallback, timeout increase, crash-window downgrade or weakening validators.

## Write scope

Under tools/frontier-v3-test-pilot/:

- src/persistent-matrix.mjs and test/persistent-matrix.test.mjs;
- src/lifecycle-barrier.mjs and test/lifecycle-barrier.test.mjs;
- src/crash-controller.mjs and test/crash-controller.test.mjs only if needed to
  expose existing exact-controller causal evidence, not replace its owner;
- one cohesive src/persistent-crash.mjs + test/persistent-crash.test.mjs helper
  may hold the bounded wire contract if needed; persistent-matrix must consume it.

Under pale-mirror-neoforge/src/{main,test}/java/
io/farfrontier/palemirror/internal/frontier/v3/client/:

- FrontierV3PilotSessionControl.java and its Test;
- FrontierV3TestPilotClient.java only the actual lifecycle call sites;
- one FrontierV3PilotExpectedCrash.java helper and its Test if needed to keep
  state ownership coherent and the client below the1000-line ratchet.

No CLI runner/CI switch in this cut; no scenarios, compiler, contracts,
workflows, gameplay, persistence formats, dependencies/build, normative docs,
root edits, Git mutations, deployments, native processes or process kills.
Any additional file or changed persistent/public meaning needs an amendment.

## Acceptance and verification

1. Node produces an immutable arm accepted by the actual Java session boundary;
   matching active declaration/identity only. Tests exercise the same wire shape,
   descriptor and publication functions that the subsequent runner will consume.
2. Java real logout call site distinguishes ordinary versus expected loss and
   invokes tested session logic. Negative tests cover no arm, foreign identity,
   stale epoch/server, wrong scenario, ordinary/recovered/final misuse, duplicate
   callbacks, publication failure and unauthorized early resume. Source-string
   checks alone do not prove the state machine.
3. Node proof/release validation rejects each missing or foreign causal input
   separately, including an armed-but-not-fired crash, wrong owned PID, missing
   actual client loss, incomplete closure and stale replacement readiness. Cover
   successive crash cycles and clean recovery without re-arming; ordinary and
   graceful benchmark paths retain their existing negative tests.
4. Unknown versions/completion modes and malformed/oversized values fail closed;
   no crash pre-half is accepted as terminal success. Tests explicitly establish
   that arbitrary early loss cannot succeed just because a crash was armed.
5. Focused Node/Java tests, full Node and exact critical gate pass. Native proof
   remains pending actual runner integration and requires a separate grant.

Gate A ACK must name exact planned files, wire version and transition design,
baseline content identity, concrete focused Node/Java commands and race handling.
Only after engineer IMPLEMENTING grant may focused commands run. Stop at Gate B
with stable sources and evidence. Main independently reviews before full
`npm test --prefix tools/frontier-v3-test-pilot` and
`./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar`.
One explicit heavy-run owner; no native client or disposable-world run yet.

## Stop conditions

Conflicting writer/WIP, incompatible existing protocol, impossible causal ordering,
new ownership, ambiguous crash classification or repeated unexplained failure:
report evidence and request review. Do not turn this into another architecture
framework or broaden to gameplay. Follow-up integrates all actual assigned lanes
into the existing persistent runner and proves actual worker3 abrupt+graceful
same-client execution; schema2 CI semantic merge stays authoritative.

## Review record

Gate A ACK accepted,2026-09-05. Node arm/proof/release schema1 has a distinct
kind and exact identities; Java owns only active-connection acknowledgement.
The existing persistent-matrix owner must publish an explicit crash-capable
immutable descriptor and validate it, not allow an arbitrary extra JSON field
to opt ordinary legacy descriptors into crash handling. Pin/version that new
descriptor shape and reject unknown modes; legacy benchmark semantics stay
unchanged. Completion is forbidden for a declared expected-crash segment even
before an arm arrives, not merely after ARM_ACKED. Resolved probe revision is
validated against the retained original declaration.

IMPLEMENTING granted within the listed files (helper paths are
src/persistent-crash.mjs and test/persistent-crash.test.mjs, not src/test).
Node may collect independent loss/exit/port observations in either order but
the trusted source assignment and exact live handles remain the validation
anchors. Duplicate logout for the already-claimed same connection is idempotent;
publication failure or a different connection is fatal. Never retry the failed
publication on a duplicate callback. Preserve the live reconnect tick pathway.

Authorized focused commands:

`node --test tools/frontier-v3-test-pilot/test/persistent-crash.test.mjs tools/frontier-v3-test-pilot/test/persistent-matrix.test.mjs tools/frontier-v3-test-pilot/test/lifecycle-barrier.test.mjs tools/frontier-v3-test-pilot/test/crash-controller.test.mjs`

`./gradlew :pale-mirror-neoforge:test --tests io.farfrontier.palemirror.internal.frontier.v3.client.FrontierV3PilotSessionControlTest --tests io.farfrontier.palemirror.internal.frontier.v3.client.FrontierV3PilotExpectedCrashTest`

Terra owns those focused runs and diff checks. Main docs-only guardrails session
37564 already completed exit0 (34tasks,16s), so no conflicting Gradle owner.
Stop at Gate B; no full/native/CI/provider/migration/deployment permission yet.

Intermediate review (not Gate B acceptance): initial Node proof compared the
controller server run ID to the matrix run ID and omitted several causal-input
checks. Terra reports correcting those; main still requires integrated evidence.
The initial Java positive fixture supplied only schema/kind/epoch/segment/hash,
with a schema-only lifecycle identity. Main rejected this as a positive target:
it must be a malformed-input negative, and the actual positive wire must match
the complete Node arm and trusted immutable descriptor/lifecycle identity.
The ordinary resume token must not bypass expected-crash proof/release. These
are existing contract requirements, not added scope. Restore system properties
after Java tests, retaining preexisting values. The final Gate B packet must
include the complete four-file focused Node command and both Java test classes,
not just isolated helper-test counts. No full/native grant has been issued.

Main pure API diagnostic on the in-progress helper (no files/worlds/processes
mutated) reproduced four accepted-invalid arms: fixed declared revision17 with
top-level resolvedRevision18; declared authority epoch3 with actual4; declared
epoch3 with no actual epoch; and boundary not_a_crash_boundary. Existing test
fixture had duplicated resolvedRevision inside its descriptor, masking the
top-level mismatch. Require one coherent declaration-to-observation comparison
and closed admitted-boundary validation, with all four regression negatives.
Terra received these findings before Gate B. They are not final reviewed code
and no acceptance has been granted.

Engineer causal-review checkpoint: active executor interrupted, previous status
running, without resetting files or restarting any native process. Source/test
grants are temporarily CLOSED pending a concise READ_ONLY reconciliation of the
four reproduced Node defects, the incomplete Java positive/implementation, and
the missing verified-release transition. This is review of observed contract
violations, not acceptance or a new implementation owner. The same Terra executor
must name exact remaining fixes and any active command handle, then await renewed
IMPLEMENTING. No change to the requested endpoint or original acceptance tests.

READ_ONLY reconciliation accepted: Terra confirms all four Node counterexamples,
incomplete Java admission and missing crash-release path. It reports its prior
escalated focused Gradle tool call waited1219s and was aborted without a reusable
process/session; this is NOT a test execution or a test failure. Main sees no
active Gradle/native process. Root guardrails72669 then finished exit1 in16s,
34tasks: TestPilotClient exceeds1000lines and ExpectedCrashTest line23 exceeds
250columns. These are ordinary correction targets; do not raise the ratchets.

IMPLEMENTING renewed within the same source scope. Complete the agreed Node and
Java wire/transition, four direct Node negatives, complete Java positive plus
malformed negative, successor-bound release and existing lifecycle regressions.
Use the admitted cohesive helper to keep client call sites small. Preserve WIP.
Terra may run the four-file focused Node command and diff checks only. ALL Gradle
execution now belongs to main, including the already specified two-class focused
Java command. Report stable sources for main to run it before Gate B. While main
tests, freeze source until its result/review. No escalated tool request or waiting
for permission: if a permitted command is denied in the executor environment,
report immediately with no retry/escalation; main uses its available verification
environment. No full/native/CI/provider/deployment grant. Do not resume the aborted
approval call or treat it as a live Gradle process.

## Revision2 transfer (same contract, replacement executor)

Revision1 executor /root/terra_executor twice ended with an acknowledgement-only
final claiming continued implementation; main verified status COMPLETED both
times. Its grants are CLOSED and it was explicitly told not to resume. No source
reset, test/native restart or new concurrent writer. Replacement is
/root/terra_crash_executor, same requested gpt-5.6-terra high. Revision2 changes
execution ownership only: preserve every partial edit and finish the full
existing contract, not a helper-only subset. Fresh Gate A ACK before new writes.

Transfer baseline: nested204dirty/untracked entries, outer only .f0v-baseline/;
main sees no active Gradle/native/test harness. Main exact four-file Node command
passes22individual tests (not4tests; four files). These are mostly inherited
tests and do not prove Java/proof-release integration. Guardrails72669 failed
the two documented size/style checks; no Java focused/full/native acceptance.

Current source SHA256 anchors:

- src/persistent-crash.mjs: eaa9a4a6220dda8bf54d5f4afba9be6484e9473bb887905e586e99f4a41f47fb
- src/persistent-matrix.mjs: a26981325240e792686d15bc36c6c1766d932f22907814e7d1ef1e9a9ae55bd7
- src/lifecycle-barrier.mjs: 7c36174c86b4c7d14484dd20cab0c3cd30db3e5a0869e2b10adb224d2b696524
- src/crash-controller.mjs: 8fe3d309778d6e61c6bfc567b396933b4ecf2825de1e7dafc64d6c5aa85e0f46
- Java SessionControl: 3cb6e3d0a5b2d86dd57f371dbba3e1ea1db3943702e8972a65a520e9a7725e82
- Java TestPilotClient: 21c494be08e1aa54159ddabd5fe06891668aa24edbc1d343ba39e72cae76c291
- Java ExpectedCrashTest: 2736034dfc3ebccb1ef9a1b26b1a62b819f9812f9e4951ba3db523aa09ce0c0c

Node paths above are under tools/frontier-v3-test-pilot; Java full paths are in
the write scope. Snapshot other scoped file identities on adoption. Current
helper counterexamples have been fixed narrowly, but Java still accepts the
incomplete arm, legacy resume still bypasses verified release, actual immutable
Node arm/proof/release publication is absent, lifecycle/crash-controller receipt
integration is unfinished and TestPilotClient has1011lines. Complete these
actual gaps and all required positive/negative tests before asking main for
focused Java. Main alone runs ALL Gradle. Executor only scoped code/Node after
grant; no escalation or approval wait. No implementation/full/native grant is
inherited by the replacement from revision1.

Revision2 Gate A ACK ACCEPTED,2026-09-05. Replacement confirmed transferred WIP,
exact scope, complete arm/proof/release and Java transition requirements; no
unresolved decision. IMPLEMENTING granted to /root/terra_crash_executor alone
for the listed files, exact four-file focused Node command and diff checks.
Main retains all Gradle ownership. Submit coherent implementation, not an
isolated helper milestone, for independent focused Java and Gate B review.
If the runtime server binding cannot be authenticated against the active
immutable segment/ready evidence within these owners, report that specific
design issue rather than self-validating the arm against its own fields.
No other grant or accepted endpoint changes.

## Revision2 Gate B correction, 2026-09-05

Gate B NOT ACCEPTED. Replacement confirmed all sources frozen after its24/24
four-file Node packet; main independently obtained24/24. This does not prove
the Java/runtime protocol. Main recovered missing session52750 from the exact
Gradle daemon log:17:26:53.238+05, BUILD FAILED in3s,17tasks; compileTestJava
rejects ExpectedCrashTest:90 because Fixture.restore() shadows the outer
restore(String,String). No focused Java tests ran; old XML is not fresh evidence.
No process restart is needed to recover this result.

Correct the following connected contract failures within the existing scope:

1. Wire arm admission into the actual client lifecycle. Source search finds
   armExpectedLoss only in its declaration and tests, never in a runtime caller.
   The existing LifecycleSignal closed registry also rejects both emitted new
   signal names. Exercise actual publication/reader shapes and the reachable
   lifecycle transition, including continued actions after arming; helper-only
   positives and source-string checks cannot substitute for this.
2. Validate the complete Java arm against retained declaration/readiness and
   actual client identity: client PID, resolved/fixed revision, optional authority
   epoch presence/value, admitted boundary, exact version/types and finite bounds.
   Current Java checks omit these fields and coerce fractional/string primitives.
   The Java positive fixture must match the actual Node-generated wire and
   descriptor, not invent a more permissive descriptor. Add independent mutated
   negatives with a recomputed hash so the checksum alone cannot mask them.
3. Make recovery consumption enforce the verified release, not a shallow marker.
   Java currently checks only presence of four proof keys, ignores armSha256 and
   most replacement-ready fields; Node readPersistentMatrixExpectedCrashRelease
   skips hash/proof validation. Bind the retained exact arm, verified observations,
   full lifecycle identity and immutable successor/readiness. Reject empty/null or
   foreign proof, missing client loss/closure, wrong epoch/world/scenario/server,
   stale ready and plain legacy resume. Retain Node as the independent causal
   proof owner; do not introduce another coordinator. Main pure API diagnostics
   accepted foreign worker/build in clientLoss and replacement reusing the killed
   server's run ID/PID. Do not reject valid OS PID reuse with a fresh authenticated
   server run; the same dead server run cannot certify its own replacement.
4. Complete state-machine failures and cleanup: actual unexpected active-session
   loss and failed reconnect must fail visibly, not reset into inactivity. Bind
   duplicate callbacks to the actual connection and prevent a foreign callback
   from claiming that connection's loss. Publication failure is never retried.
   Clear transient action/target/input/frame/diagnostic state while preserving
   the reconnect tick pathway. Reject expected-crash terminal results in the Node
   result boundary too, and reject invalid final/ordinary/recovered crash modes.
5. Add the already-required full negative/recovery matrix, including successful
   release-to-reconnect and successive crash cycles. The two existing Java tests
   exercise neither release nor real client call sites; the Node test named
   "cannot re-arm" does not actually attempt recovered-segment rearming. Fix the
   compile error without weakening property restoration, size/style ratchets or
   any ordinary/graceful/final-close regression.

Main retains ALL Gradle ownership. No full/native/CI/provider/deployment grant.
Main docs/source guardrails58730 completed exit0 (34tasks,16s), both repository
diff checks pass; client965lines and style ratchets pass. Nested206dirty/untracked
entries, outer only inherited .f0v-baseline/. IMPLEMENTING is renewed for the
same sole executor, scoped implementation plus exact four-file Node/diff checks.
Address all five findings as one coherent packet; freeze again before main Java.
The endpoint, executor and file scope are unchanged.

Second correction checkpoint: main focused Java51068 PASS (18tasks,1s); this
fixes compilation only, with the two existing SessionControl tests and two
ExpectedCrash tests. Runtime arm invocation and closed signal registration now
exist. Gate B remains NOT ACCEPTED: Java release still checks only proof-key
presence; client PID/declaration revision/authority and bounded numeric decoding
remain incomplete. Node release reader reconstructs a new value without comparing
the input release/hash/identity. Actual connection ownership, fatal unexpected
loss/reconnect and recovery/successive-cycle tests remain missing.

Main requested READ_ONLY reconciliation instead of rerunning those green tests.
Terra explicitly confirmed each omission, acknowledged premature completion,
and proposed corrections within the existing owners/scope. Reconciliation is
accepted; IMPLEMENTING is renewed for all remaining five findings. Before the
next Gate B, supply a compact acceptance-to-method table, including actual
positive recovery and each malformed/foreign/duplicate/failure path. Mark any
missing item honestly instead of calling the packet complete. Same focused Node
grant and main-only Gradle ownership; no full/native grant.

Follow-up runner review (NOT additional current write scope): startServer in
run-f0va-persistent-matrix assigns the signal PID before comparing it to the same
signal, so this comparison does not prove spawn-handle ownership. Actual runner
integration must compare the readiness PID against the retained launched JVM
handle without overwriting its authority anchor, in addition to its fresh run ID.

## Revision3 transfer: complete the existing contract

Revision2 executor /root/terra_crash_executor is terminal COMPLETED after
explicitly failing to complete the requested remaining two-cycle fixture. Main
closed all its grants and instructed it not to resume. No rollback or new
concurrent writer. Replacement /root/terra_crash_completion uses the same
user-requested gpt-5.6-terra high with fresh context. Same order endpoint,
acceptance criteria and implementation file scope; no additional process owner.

Transferred code now has actual event.getConnection reference binding at Java
login/logout, current-PID/revision/authority and proof checks, raw Node release
comparison, one Java recovered-login positive and rehashed client-PID/foreign
connection negative. These latest edits are NOT independently Java-verified.
The last main Java51068 pass predates them; only the earlier four tests passed.
Main docs guardrails43120 passed34tasks/16s before the latest executor edits.

First bounded implementation subtask after Gate A: finish the actual two-crash
cycle fixture in one session/client without reset(), including recovered modes
never rearmed; add independently rehashed arm/release negatives covering the
existing acceptance matrix. Correct the corresponding existing implementation
when these regressions reveal failures. Follow with remaining publication,
connection/unexpected/failed-reconnect checks within the same order. This is
sequencing, not acceptance of a reduced helper-only endpoint. Report actual
test method names and unimplemented rows; stop a subtask honestly rather than
claiming invisible work after a terminal final answer.

Replacement begins READ_ONLY for bootstrap/baseline/Gate A ACK, no write/run
permission inherited. Main retains every Gradle command; after explicit grant,
executor may run only the exact four-file Node command and diff/style checks.
No full/native/provider/Git/deploy grants. Preserve all inherited WIP.

Revision3 Gate A ACK ACCEPTED. Main transfer guardrails65333 PASS34tasks/16s;
fresh focused Java26982 PASS18tasks/1s (2SessionControl+4ExpectedCrash).
Independent review rejects the new truncated Fixture.addVerifiedRecovery positive:
fired={runId},exit={serverPid},loss={clientPid,runId},closure={port} is not complete
Node wire and must be a malformed-input negative. The replacement explicitly
acknowledged this and the two-cycle/rehashed fixture design, exact13-file scope
(8Node+5Java), ownership/race rules and no outstanding decision. Nested206entries,
outer only .f0v-baseline/; previous writer closed and no inherited active run.

IMPLEMENTING granted to /root/terra_crash_completion only after main's current
docs guardrail finishes. First bounded subtask is as specified above; all other
acceptance rows still block whole-order Gate B/C. Same exact Node/diff grant,
main-only Gradle, no full/native grant. Report full wire positives and explicit
negative test methods, with honest remaining rows; freeze before main Java.

Revision3 first subtask review: main Java69884 PASS18tasks/1s (2SessionControl
and4ExpectedCrash methods), exact four-file Node24/24 PASS177ms. Two sequential
crash/recovered cycles now execute without mid-session reset; full four-record
proof and rehashed incomplete/foreign inputs are exercised. Whole-order Gate B
is NOT accepted; actual-event/failure/publication/remaining lifecycle rows remain.

Three first-subtask corrections are required before proceeding: Java's closed
boundary set must retain all5 existing controller/CI boundaries, not only the2
used by the fixture. Fixed expectedRevision in the immutable declaration must
equal the resolved revision, even if both top-level and arm-descriptor resolved
fields are changed consistently and rehashed. Retained optional authority epoch
must match presence/value in both arm copies; coordinated change or removal may
not bypass the immutable declaration. Existing one-field negatives did not
cover these coordinated mutations. Terra confirmed all3 causes READ_ONLY.

Renew IMPLEMENTING after main docs gate for these same-scope corrections and
all5-boundary positives, fixed-revision positive/coupled rehashed negatives,
coupled authority change/removal negatives and valid undeclared/no-authority
positive. Same exact Node/diff grant, all Gradle main; freeze for independent
Java again. No full/native or reduced endpoint acceptance.

Revision3 first bounded subtask reviewed: main Java51420 PASS18tasks/1s
(2SessionControl+6ExpectedCrash methods). Main inspected all5 admitted boundary
constants, retained fixed-revision and authority presence/value checks, and the
positive/coupled-negative methods. These3 corrections and the two-cycle/full-wire
subtask are accepted as local progress, NOT whole-order Gate B/C or native proof.
Latest independent Node24/24 was69884's companion177ms run; executor's subsequent
same four-file run reports24/24/161ms. No full/native grant.

### Remaining bounded lifecycle packet, same revision and write scope

Complete these original acceptance rows together, then submit a full mapping:

| Row | Required code boundary and evidence |
| --- | --- |
| R1 | Validate full successor descriptor identity against retained lifecycle, including worker/build/nonce/session. Bound bytes read for the new crash control records before parsing; reject oversized records and malformed primitives with focused negatives. Preserve the full valid Node/Java wire. |
| R2 | Prove admission/refusal for no arm, ordinary/final/recovered misuse, unknown schema/completion, foreign/stale epoch/server/scenario. Crash pre-half never produces a terminal result; ordinary actions continue when armed and cannot continue after loss has been claimed. |
| R3 | Test same actual connection duplicate callbacks, foreign connection rejection, claim-before-publication failure with no retry, immediate transient input/target/frame/diagnostic cleanup and retained reconnect viability. Unexpected active loss and failed reconnect must be observable failures through the actual caller, not an exception/reset that becomes idle. |
| R4 | Exercise actual Node publication/read functions with the Java signal envelope shape and the real crash controller's returned receipt using an injected non-killing test callback. Combine every exact causal input; independently reject missing/foreign fired receipt, owned exit, client loss, port closure and successor readiness. Preserve immutable exclusive publication and tested successive-cycle identities. Never invoke a real kill for these tests. |
| R5 | Retain ordinary/graceful/final-close and publication-negative regressions. Prove helper state-machine routes via focused tests consumed by actual client call sites; source-string assertions alone cannot establish behavior. Report exact method names for every row and any remaining gap. |

The existing SessionControl remains the transition/failure owner; make its
decisions testable and keep the actual client delegate reachable. Do not add
a second lifecycle framework, gameplay authority or test-only world mutation.
Keep client within1000lines by cohesive lifecycle cleanup inside the approved
files, never a higher ratchet. Main explicitly retains ALL Gradle and approves
the same two-class focused command after frozen sources; executor has only the
same four-file Node plus diff/style checks after renewed IMPLEMENTING. No new
files, CLI/CI integration, native processes, Git/deployment or timeout increases.
Whole-order full-gate permission still requires main Gate B review of R1–R5.

R1–R5 review checkpoint: main focused Java94665 PASS18tasks/1s and exact
four-file Node25/25 PASS179.58ms. Successor full identity, admission/refusal,
claim-before-publication failure and actual controller receipt are local progress.
Gate B remains unaccepted for three concrete gaps, acknowledged READ_ONLY:

1. ExpectedCrash.read uses readAllBytes before checking65536bytes. Replace with
   bounded streaming of at most limit+1 bytes; reject overflow before parsing.
2. Direct Minecraft event-bus unit invocation is NOT required. Make the existing
   SessionControl failure classification/one-shot claim testable and consumed by
   actual callers; cover unexpected active loss, reconnect failure, duplicate
   fatal claim, no actions after fatal, and allowed ordinary/final paths. Clear
   expected-loss transient state even when acknowledgement publication throws.
   No new lifecycle owner/framework; preserve the client1000-line ratchet.
3. R4 tests currently read Java envelopes separately from manually flattened
   clientLoss proof. Feed the actual awaitLifecycleSignal result into the matrix
   publication/release test alongside the existing controller.fire fake-kill
   receipt. Keep independent proof negatives; no real kill or native test.

After main's docs guardrail completes, IMPLEMENTING is renewed for these three
same-scope corrections only, then freeze for independent focused review. Sole
executor remains /root/terra_crash_completion; exact Node/diff grant only, all
Gradle main. No full/native or optimization runs: the accepted22.52% restart
benefit is sufficient,25% remains advisory. Full-order acceptance still requires
the original contract and critical gate, not repeated timing samples.

Next review: bounded MAX+1 stream reading and integrated Java-envelope reader
plus controller receipt are inspected. The new tick catch introduced a specific
failure regression: reconnectFailureRequiresFatal requires reconnectInFlight,
but malformed/foreign release or resume-token rejection happens while only
awaitingResume is true. Exceptions would be swallowed and retried. Executor
confirmed READ_ONLY. Correct this distinction in existing SessionControl/client:
tick transport failure must cover BOTH request/awaiting and in-flight phases;
logout must retain legitimate expected/ordinary awaitingResume as nonfatal.
Add the invalid release/token-before-inFlight regression and the nonfatal
awaiting-logout policy assertion. No other scope change or new framework.
After main docs gate, renew the same implementation/Node grant for this fix,
then freeze; all Gradle stays main and full/native remains ungranted.

## Revision3 Gate B: accepted for full automated verification

Main Java25149 PASS18tasks/1s,12tests (2SessionControl+10ExpectedCrash),
2026-09-05T13:52:54Z; independent exact four-file Node25/25 PASS147.79ms.
Main inspected bounded MAX+1 input, retained successor identity, full wire/proof,
two-cycle recovery, claim-before-publication, cleanup on failure, no-post-fatal
actions and actual client delegates. The request-phase regression is fixed:
tick uses awaitingResume OR reconnectInFlight; logout retains in-flight-only
classification. Focused failure test exercises a foreign token before reconnect.

Gate B ACCEPTED, not Gate C or F0.VA closure. Stable sources remain frozen.
Main alone now owns full npm test and the exact critical Gradle gate above.
Resolved generated GameTest target is
/home/rd/proj/minecraft/pale-mirror/pale-mirror-neoforge/build/runs/core-game-test;
the existing Gradle task recreates only this generated fixture. Live Java2330125
is unrelated and untouched. No graphical/native crash harness, provider,
deployment, Git or optimization-run permission. Actual assigned-runner adoption
and physical same-client proof remain the following order's endpoint.

## Revision3 Gate C acceptance

Main critical session19859 exit0:71tasks (39executed,32up-to-date),2m8s.
Exact command is the Gate B critical command above; guardrails/check/build and
verifyPackagedJar completed successfully. Fresh generated GameTest log records
All290required tests passed at2026-09-05 18:55:14.259+05, then all dimensions
saved and normal test-server shutdown. Full Node148/148 PASS391.38ms. Focused
Java25149 and Node25/25 evidence above predates this same frozen-source full gate.
JAR SHA256:259e8fde6516539315216616c483a040df856e1b71a052b5019bba549d0746c3.

| Acceptance | Reviewed code and decisive focused evidence |
| --- | --- |
| 1,R1 | ExpectedCrash validates retained descriptor/lifecycle/ready identity and bounded MAX+1 reads; completeImmutableArmClaimsOneObservedLossWithoutNormalDisconnect, successorIdentityAndControlRecordSizeFailClosed, fixedRevisionAndRetainedAuthorityCannotBeCoupledAway and all5boundary test. |
| 2,R2–R3 | SessionControl owns connection claim, action/failure policy and recovery; actual login/logout/tick delegates inspected. armAdmissionRefusesAbsentOrdinaryFinalRecoveredAndUnknownModes, expectedLossPublicationFailureClaimsOnceAndStopsFurtherActions, lifecycleFailureClassificationKeepsOrdinaryAndReconnectStatesDistinct cover admission, no retry and request-phase failure. |
| 3,R4 | persistent-matrix publication/read consumes full wire; crash publication binds its active ready server and release before resume combines actual Java-shaped signal reader and controller.fire receipt with non-killing callback. Pure proof/release tests independently reject missing/foreign observations; Java rehashedIncompleteOrForeignReleaseCannotAuthorizeReconnect checks release inputs and readiness. |
| 3–4,R5 | twoExpectedCrashCyclesRecoverInOneSessionWithoutReset proves sequential recovery/no rearm. Existing SessionControl normal publication negatives and Node ordinary/graceful/final-close, immutable publication and benchmark world-count regressions remain green. |
| 5 | Focused Java12, focused Node25, full Node148 and critical290GameTests/build/package gates above. |

Scoped implementation SHA256 anchors (Node paths relative to tools/frontier-v3-test-pilot;
Java names in the approved client package):

```text
src/persistent-crash.mjs 8423962c9528e98263851610de02148349b23d4ded0b00d7fddffc7e0f9814b0
src/persistent-matrix.mjs fa6880ac0e94421dc4cd0b8759b898ea3d7eef97fab91079af402581432337d5
src/lifecycle-barrier.mjs 759f3a9782c11c6fd4880a66e2723ac61e08ddf1baab5002304237fadb8e742e
src/crash-controller.mjs 7f2deec52949ed3e74c46b689c4806958154fb758788bdf403c42cb9e96a4395
test/persistent-crash.test.mjs 0bccc38042b7ec7dcd2c6e62ee2213620a08fc13ef442ca7ef01b0bf799596ca
test/persistent-matrix.test.mjs 53253a0748a387396e937e10d5dfdaebd052d680f23dcb961b8ace59fde10b09
test/lifecycle-barrier.test.mjs 09cb6c2700aec556f9c7fa9f8b26dbca191c5385c3be86353e9c99c079899396
test/crash-controller.test.mjs 2c06cc57e4bb4762759e07029fbc01d7bb2b21258d7fa089d11e8cbae86c4769
FrontierV3PilotSessionControl.java 60c4930cf26c878d0232a66983c5102248fe40c3fb17c6999a7e911616e20036
FrontierV3TestPilotClient.java b5e7dc7eb6bd77edd4b8071e3f23c941983c5ae6905d02f5cb491f1403203a85
FrontierV3PilotExpectedCrash.java 12865a937ebdd5ed23cb81ff0bde726b2553dfb6a0bfa037ce2bf1d52f9dad89
FrontierV3PilotSessionControlTest.java 753396d7c2d38b207b615dc1c6c698cff4adef1535ae4cf9b56411c745ac8502
FrontierV3PilotExpectedCrashTest.java 2b1e93b734050c11f493e73f00d70d1c31dd2926c7157a7c819b050b31cc1344
```

Both repositories diff-check clean; nested206dirty/untracked entries and outer
only inherited .f0v-baseline/. HEADs unchanged, no commit/push/deployment.
Main accepts this protocol order. No physical client/crash, provider, timing or
human acceptance is inferred from GameTests. Actual compiled-worker adoption,
bounded diagnostic values and exact retained spawned-server ownership remain
mandatory in the following integration order. Current implementation/run grants
close; executor may receive a separate READ_ONLY planning request, not writes.
