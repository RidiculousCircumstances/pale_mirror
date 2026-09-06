# PM-F0VA-NATIVE-CRASH-MIXIN-BOOTSTRAP-01: load pilot crash probes before targets

Specification revision: 2. Parent: `PM-F0VA-NATIVE-QUAL-01` revision4.
Risk: critical-code test-only launch/instrumentation correction.
Engineer: `/root`. Executor: `/root/terra_crash_completion`, gpt-5.6-terra high.
State: GATE_C_ACCEPTED; implementation grant CLOSED; executor READ_ONLY.

Revision1 Gate A was challenged read-only because it named the generic
`--mixin` spelling. Independent bytecode inspection of the active Mixin 0.8.7
and ModLauncher 11.0.5 path confirms that the transformation service is named
`mixin`, declares its local option as `config`, and ModLauncher joins those
names with `.` before registering the option. Revision2 therefore requires the
active ModLauncher spelling `--mixin.config`; no implementation was written
under revision1.

## Reproduced defect and desired boundary

Revision4 proved the ordinary client, natural HOT lease and durable release,
but never reached the crash controller. Its prepared server program arguments
contain no pilot crash mixin option. `FrontierV3PilotFixtureBootstrap` calls
`FrontierV3PilotCrashHooks.install()` only during `ServerStartedEvent`; this is
later than Mixin's launch configuration selection and gives no pre-transform
guarantee for `FrontierStoreTransactionCommitter`. The exact release event at
revision2423 therefore committed with no `PMV3_CRASH_BOUNDARY` marker.

The non-packaged pilot crash configuration must be selected at the prepared
server JVM's Mixin bootstrap, before any target may load. The prepared-build
boundary must fail closed when that exact early launch pair is absent,
duplicated, reordered or assigned to the client. Runtime fixture startup must
not try to add the configuration a second time. Production metadata/JAR and
ordinary server/client launches remain unchanged.

## Exact scope and baselines

Allowed files only:

- `pale-mirror-neoforge/build.gradle`, baseline
  `98b59e9eb679a5149fd8c89714ddf5cc905e9d586f17d7b881e67fb814345c18`;
- `pale-mirror-neoforge/src/pilot/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3PilotFixtureBootstrap.java`,
  baseline `4a1e293217ba968e24eac4b044b7c8166cb6ce00f4ea7e96df632cee5d872201`;
- `pale-mirror-neoforge/src/pilot/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3PilotCrashHooks.java`,
  baseline `3ab872aea8e3a0c531bff7b164be21bc1937e48821231427f49e188160c80544`;
- `tools/frontier-v3-test-pilot/src/prepared-build.mjs`, baseline
  `9399f2b53fc95167d8b2cc37ed50f7428e28c648252ff71c1d96933a2832c645`;
- `tools/frontier-v3-test-pilot/test/prepared-build.test.mjs`, baseline
  `4ce26a144aef09fa2c5ecc2f805d653708a845d06669f0c983f5a0150d4a6158`.

No scenario/contract, crash matching semantics, runner/lifecycle deadlines,
main resources/metadata, packaged classes, canonical/domain/runtime code,
other test, docs/ledger, live service, Git or remote edit. No native run.

## Acceptance

1. Regression first extends the prepared-build fixture with one valid server
   pair `--mixin.config` then
   `pale_mirror.frontier_v3.pilot_crash.mixins.json`, and
   proves missing, duplicate, non-adjacent/wrong-config and client-only forms
   are rejected. Baseline must fail because prepared-build currently hashes
   but does not interpret this launch invariant.
2. Only `frontierV3PilotServer` adds that exact adjacent program-argument pair.
   It remains part of the already fingerprinted server program arguments and
   is absent from pilot client, ordinary runs and production metadata.
3. `fingerprintPreparedBuild` validates exactly one adjacent pair in parsed
   non-comment server program arguments and none in client program arguments
   before it returns an identity. Existing byte hashing/path checks remain.
4. Remove the late `ServerStartedEvent` call and the now-unused dynamic
   `Mixins.addConfiguration`/atomic install mechanism. `probe()` remains the
   sole access to the property-bound rendezvous; matching/parking is unchanged.
5. Executor may run only:

```bash
node --test tools/frontier-v3-test-pilot/test/prepared-build.test.mjs
./gradlew :pale-mirror-neoforge:compilePilotJava \
  :pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment --no-daemon
git diff --check
```

The Gradle task may refresh only its normal generated `build/moddev` launch
inputs; it starts no server/client. Main will independently inspect the exact
generated pair, run full Node/critical gates and own any fresh preparation or
native execution.

## Stop rules

Gate A must ACK the retained revision4 evidence, Mixin bootstrap timing, exact
five-file scope and fail-closed launch invariant. Do not write before explicit
grant. Stop READ_ONLY if early selection requires production metadata, a main
source-set hook, a service connector, scenario weakening, another file or any
native process.

## Gate A evidence

Terra accepted revision2 read-only on 2026-09-05. All five SHA-256 baselines
matched. The executor independently confirmed the active Mixin/ModLauncher
option construction and early initialization path, retained revision4 causal
evidence, server-only launch invariant, regression-first matrix, isolation,
scope and stop rules. No file, test or native process was changed or started.

## Gate B evidence

Terra implemented exactly the five granted files and froze. Regression-first
proved the former prepared fingerprint accepted malformed launch inputs: the
focused fixture had one intended failure before production logic. The final
focused Node test passed. The first compile/preparation exposed a stale server
program-argument file because the aggregate environment task prepared only the
client; the in-scope Gradle correction now also depends on the existing pilot
server preparation and orders manifest generation after both roles. A second
allowed preparation passed and materially generated exactly:

```text
--nogui
--mixin.config
pale_mirror.frontier_v3.pilot_crash.mixins.json
```

The generated client input contains no such option or configuration. Terra's
final compile/preparation passed 21 tasks in 6 seconds and `git diff --check`
passed; no JVM was launched. Main independently reviewed all five files,
repeated the focused Node pass and compile/preparation pass, and inspected the
same generated server/client inputs. Final SHA-256 values:

- `pale-mirror-neoforge/build.gradle`:
  `5ec51edc0ddd90be354ac31248c59e0bf5c8b97a560b9a4b57dd17b99cde5d1d`;
- `FrontierV3PilotFixtureBootstrap.java`:
  `9c8adc8a14976d8765c80b8fd090fe61c4b047103490e4f2fc7d6402175534f5`;
- `FrontierV3PilotCrashHooks.java`:
  `ab50794d03400cec7bfcf24f6b3dc5af9375b09bb26a3918971faf132779b06a`;
- `tools/frontier-v3-test-pilot/src/prepared-build.mjs`:
  `d63100b2e5e59186bfe532b6417c2d7848f4c3d504ccf7d31670bacefbe658dd`;
- `tools/frontier-v3-test-pilot/test/prepared-build.test.mjs`:
  `127f4bb211f4c43ad012bc5a97b1ddd338d343fc12a175d637df9c5affa325c0`.

Gate B accepts the bounded implementation and closes its writer grant. Full
Node, critical Gradle, package isolation and a fresh native revision remain
main-owned Gate C/qualification evidence.

## Gate C evidence

Main's full Node suite passed 169/169 in 391 milliseconds. The exact critical
gate

```bash
./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer \
  :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar --no-daemon
```

passed 71 tasks in 2 minutes 15 seconds. All 290 required GameTests passed in
59.91 seconds and the server normally saved overworld, End and Nether. Package
verification passed; direct JAR inspection found none of the pilot mixin
configuration, mixin classes, crash probe or crash-hook/bootstrap classes. The
sole packaged artifact remains
`pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar`, SHA-256
`4ea05b2cec716dd53980a57d0016631df3fb1af311a11a774a6a259f52e4ce5a`.
`git diff --check` passed. Nested/root HEADs remain
`0ac6f97a695d636ae928313de7a807d1f3ea05a1` and
`891a4dfe1a613857124775696973d065cc9399cb`; inherited WIP remains 208 nested
status entries plus outer `.f0v-baseline/`.

Gate C accepts only the early crash-mixin bootstrap correction. It does not
promote revision4 native evidence or prove that the crash boundary is now
reachable. That requires a fresh, identity-bound native qualification root.
