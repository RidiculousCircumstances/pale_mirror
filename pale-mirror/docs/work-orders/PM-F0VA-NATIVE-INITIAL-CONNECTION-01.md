# PM-F0VA-NATIVE-INITIAL-CONNECTION-01: distinguish pre-login Quick Play logout

Specification revision: 2. Parent: `PM-F0VA-NATIVE-QUAL-01` revision3.
Risk: critical-code, bounded Java lifecycle correction; no scenario/gameplay change.
Engineer: `/root`. Executor: `/root/terra_crash_completion`, gpt-5.6-terra high.
State: GATE_C_ACCEPTED; implementation grant CLOSED; executor READ_ONLY.

## Reproduced defect and desired transition

NeoForge/Minecraft Quick Play calls `Minecraft.disconnect` while replacing its
initial screen and before the first successful `LoggingIn` callback. The pilot
receives `LoggingOut` with no `activeConnection` ever bound. Matrix properties
are already enabled, epoch is still -1 and all transition flags are false, so
`unexpectedActiveLossRequiresFatal()` currently returns true and stops the
client as if a live connection had disappeared.

Only a connection previously bound by the successful login callback can suffer
an unexpected active loss. A pre-login/no-active-connection logout must not be
fatal; the existing caller may perform its ordinary local reset and the later
real login can activate epoch0. Once `bindActiveConnection` has run, an
unclassified logout remains fatal. Expected crash loss, normal handoff/final
close and reconnect classifications remain unchanged.

## Exact scope and baselines

Allowed files only:

- `pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/client/FrontierV3PilotSessionControl.java`,
  baseline `7016958106359baee9016a2226093d246a5d1dd2aa3540d18d332a2ed6ae0e5e`;
- `pale-mirror-neoforge/src/test/java/io/farfrontier/palemirror/internal/frontier/v3/client/FrontierV3PilotExpectedCrashTest.java`,
  baseline `bba3a7a312fa7acbcc3dab99e5f98fc47c8a6912eefdd66e73e87eb42ea2aece`.

No Node/runner, other Java, Gradle configuration, contract/scenario, workflow,
docs/ledger, generated output, live service, Git or remote edit. Do not suppress
an established connection loss, manufacture a prepared signal, change callback
ordering or weaken foreign expected-loss connection identity.

## Acceptance

1. Regression first changes the contradictory lifecycle test: after matrix
   activation but before `bindActiveConnection`, unexpected active loss is
   false; after binding the exact connection it is true. Baseline must fail.
2. Production predicate adds only the existence of the bound active connection
   to existing enabled/matrix/not-final/not-awaiting/not-reconnect conditions.
3. Expected loss remains claimable only by that exact bound object. Awaiting
   resume, reconnect-in-flight and final-close paths remain non-unexpected; an
   established otherwise unclassified loss remains fatal.
4. Executor may run only:

```bash
./gradlew :pale-mirror-neoforge:test --no-daemon \
  --tests io.farfrontier.palemirror.internal.frontier.v3.client.FrontierV3PilotSessionControlTest \
  --tests io.farfrontier.palemirror.internal.frontier.v3.client.FrontierV3PilotExpectedCrashTest
git diff --check
```

Freeze at Gate B with before/after counts, exact two-file diff/hashes and state
mapping. Main owns full/native gates.

## Stop rules

Gate A must ACK actual callback evidence, exact state predicate and tests. Do
not write before explicit grant. Any need to alter event ordering, publish a
synthetic readiness signal, broaden reset semantics or touch another file stops
READ_ONLY for engineer review.

## Gate A record

Gate A was accepted against revision2. The executor independently traced the
retained revision3 Quick Play stack from the pre-login `LoggingOut` callback to
`failUnexpectedActiveLoss`, confirmed that `activeConnection` is null at that
point, and matched both corrected baseline hashes. The accepted source of truth
is the existing matrix transition state plus the existence of one previously
bound active connection. Exact connection equality remains solely in the
expected-loss claim; no callback ordering or reset change is proposed.

The accepted regression changes the pre-bind expectation in
`lifecycleFailureClassificationKeepsOrdinaryAndReconnectStatesDistinct` to
false and proves true after binding the same connection object. Existing
expected-loss, awaiting-resume, reconnect, final-close and foreign-connection
coverage remains intact. No additional file or unresolved design choice was
identified. Implementation is still forbidden until the engineer issues the
explicit grant.

## Gate B/C record

The explicit revision2 grant changed only the two allowed untracked WIP files.
Regression-first execution of the exact focused command completed 14 tests with
one intended failure at the changed pre-bind assertion. After the production
predicate gained only `activeConnection != null`, the executor and engineer
each ran the same focused selection successfully with all 14 tests passing.
`git diff --check` passed. Final source hashes are:

- `FrontierV3PilotSessionControl.java`:
  `2574c6201bcc2c4399822b68292d81f94529d7b5bd2ae87ef4ffaf72acc12bcd`;
- `FrontierV3PilotExpectedCrashTest.java`:
  `76ac461eb2c02248efc7cf8cff9b13dbb052c5b7aac7e34ecd99a63091b0c11e`.

Main acceptance then passed all 169 Node tests. The first critical GameTest run
failed during final world save on an unrelated transient test-fixture integrity
state (`test mine without facility`); it had no connection/lifecycle stack.
The task's own clean disposable-world retry passed all 290 required GameTests,
normal all-dimensions save and the complete 71-task critical gate in 1 minute
15 seconds, including guardrails, check, build and packaged-JAR verification.
The sole packaged JAR is
`pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar`, SHA-256
`4ea05b2cec716dd53980a57d0016631df3fb1af311a11a774a6a259f52e4ce5a`.

Gate C accepts only this bounded initial-connection correction. The grant is
closed and the executor is READ_ONLY. Native reachability remains to be proved
by a fresh parent-order revision; this source acceptance alone does not close
F0.VA.
