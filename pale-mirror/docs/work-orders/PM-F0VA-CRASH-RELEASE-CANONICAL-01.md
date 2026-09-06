# PM-F0VA-CRASH-RELEASE-CANONICAL-01: order-independent crash evidence

Specification revision: 1. Parent: F0.VA native qualification.
Risk: critical-code (test evidence authentication, no gameplay change).
Engineer: /root. Sole executor: /root/terra_crash_completion, Terra high.
State: ACCEPTED (Gate C). Source/full grants CLOSED; source frozen.
Native qualification requires the separate parent order's current grant.

## Baseline and outcome

Native r13 failed in validatePersistentExpectedCrashRelease after 36 lifecycle
events and terminal_assertion_complete(assertionCount=4). The release producer
and final reader supply equal successor fields in different insertion orders;
structuredClone preserves that order, JSON.stringify hashing/comparison rejects
the legitimate release. The failed result remains failed, not retrospectively
accepted. No benchmark or native retry is granted.

Retained result: build/pm-f0va-native-qual-01-r13/worker-3.json.
Bundle: build/frontier-v3-ci-shards/worker-3-59dc7d52-585d-402f-a304-f515ee74ca4a/persistent-matrix.failure/bundle.json.
Baseline persistent-crash.mjs SHA256:
8423962c9528e98263851610de02148349b23d4ded0b00d7fddffc7e0f9814b0.
Baseline persistent-crash.test.mjs SHA256:
0bccc38042b7ec7dcd2c6e62ee2213620a08fc13ef442ca7ef01b0bf799596ca.
Preserve both repository histories and all WIP/worlds/bundles. Main independently
verified recorded PIDs absent and ports25631/32/41/42 closed. Required reading:
AGENTS, ledger, engineering protocol, parent native order and release skill.

## Contract and boundary

Equivalent typed successor inputs must produce one deterministic release
representation independent of object insertion order. Evidence authority remains
with authenticated arm, four independent crash observations and exact successor
readiness. Reject foreign/stale identity, altered fields or hash, invalid values
and unrecognized fields; canonicalization must not silently discard evidence
that previously caused rejection. Keep array/sequence meaning unchanged.

No new canonical world owner, schema version, persistent gameplay meaning,
fallback or acceptance relaxation. Historical failed evidence is immutable;
report any compatibility implication rather than introducing a migration or
accepting old failed measurements. Terra owns local design and focused iterations.

Write scope: tools/frontier-v3-test-pilot/src crash-release construction and
consumption boundary, and corresponding test/. Expected starting files are
persistent-crash.mjs, persistent-crash.test.mjs and persistent-matrix.test.mjs;
these are guidance, not a helper whitelist. No Java, scenarios, build/CI,
deployment, diagnostic overrides, broad wire refactor or unrelated cleanup.

## Acceptance and permissions

1. Retain a deterministic red reproduction of r13's actual producer/consumer
   ordering mismatch before correction, then green evidence.
2. Equal successor inputs with different insertion orders yield equal canonical
   bytes/hash and pass the genuine matrix final-reader route, not only a
   same-helper self-roundtrip. JSON roundtrip preserves validity.
3. Existing and targeted negatives reject foreign successor identity/epoch/world,
   changed evidence including self-consistently rehashed tampering, malformed
   fields and unknown fields. Do not loosen arm/proof authentication.
4. Run relevant focused Node suites and both repo diff checks/guardrails.
   Report exact commands/results, changed file hashes and compatibility impact.
5. Gate B independent review precedes the exact full critical gate from AGENTS;
   full/heavy/native commands need a separate grant. Gate C source acceptance
   precedes a separately authorized fresh native qualification.

No commit/push/migration/deploy/live reset/module clean. Source authority begins
only after ACK and grant; no competing writer or heavy owner. Stop and report
if the correction needs a changed schema, owner, public/persistent meaning or
permission expansion. Main reviews stable milestones, not local iterations.

## Gate A record

Terra acknowledged baseline hashes, 210/1 dirty state, owned-process cleanup,
scope, authentication invariants and direct plus genuine-reader regression plan.
Main grants implementation and ordinary focused iterations inside the stated
boundary, including node --test test/persistent-crash.test.mjs
test/persistent-matrix.test.mjs from the pilot directory, related focused tests
as needed, both repo diff checks and ./gradlew guardrails --no-daemon.
No unresolved decision. Retained r13 bytes remain historical failed evidence,
not a compatibility target or retroactive pass. Report Gate B on stable delivery.

## Gate B review

Main read all three delivered files and genuine matrix-reader reconstruction.
Successor admission is exact for its seven JSON fields, canonical construction
precedes hashing, and outer arm/proof/release reconstruction is unchanged.
Tests cover independent server-first and epoch-first producer inputs, JSON
roundtrip, the actual final-reader path across five crash boundaries and
self-consistently rehashed foreign/unknown evidence. No gameplay owner change.

Reviewed SHA256 identities:
- src/persistent-crash.mjs: 8fc38b630154ad4d520ec1de96a5a2a43f51a93092399c4114e830e416611292
- test/persistent-crash.test.mjs: 26ff46ca15327ff2e93294b590cf26d03dcefd92a706307ce16133941e104a2a
- test/persistent-matrix.test.mjs: 5caedd665e4bc00488fc917296f3092c1f8baf70a3247f2a5999f9caa135ef84

Terra's original red8/10 (73.13ms), green10/10 (75.99ms) and guardrails34tasks/18s
are tool-session output only, not saved log files. This limitation is explicit;
do not label an unrelated red artifact as this correction's evidence. Retained
native r13 supplies the original failure, and checked-in regression preserves
its causal shape. Main independently ran the two-file focused command10/10
(82.47ms), verified hashes and inspected negative tests. Gate B ACCEPTED.

Terra exclusively runs from the nested repository:

```bash
./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar --no-daemon
```

Retain exact full-gate output, GameTest/save evidence and packaged JAR identity/
server-only hook exclusions; report both repository diff checks/dirty states
and owned cleanup. No module clean/source changes/native retries or benchmark.
Main reviews Gate C before any new client-server qualification.

## Gate C acceptance

Exact full command passed in1m15s,71tasks (37executed/34up-to-date).
Main inspected /home/rd/.gradle/daemon/9.2.1/daemon-3855428.out.log:820 and
pale-mirror-neoforge/build/runs/core-game-test/logs/latest.log:539:
290/290 at02:31:19.467, all-dimensions save02:31:31.457, normal shutdown.
Main rechecked all three source/test hashes, JAR
e4eefc3deab557c382fd79eeef4c35c22903a019a859faaec219c60123e5a996,
absent Gradle PID3855428 and closed four owned ports. Terra reports no surviving
GameTest process, exact server-only crash/fixture exclusions and both diff
checks PASS, dirty210/1. Unchanged JAR retains previous packaging evidence.
Gate C ACCEPTED for this bounded correction; F0.VA and native pair remain open.
