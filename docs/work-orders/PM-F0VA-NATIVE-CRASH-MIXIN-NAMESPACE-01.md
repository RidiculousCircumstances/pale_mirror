# PM-F0VA-NATIVE-CRASH-MIXIN-NAMESPACE-01: isolate pilot mixin ownership

Specification revision: 1. Parent: `PM-F0VA-NATIVE-QUAL-01` revision5.
Risk: critical-code test-only launch instrumentation correction.
Engineer: `/root`. Executor: `/root/terra_crash_completion`, gpt-5.6-terra high.
State: GATE_C_ACCEPTED; implementation grant CLOSED; executor READ_ONLY.

## Reproduced defect and desired boundary

Revision5 proved the early configuration is selected by ModLauncher/Mixin, then
failed before server readiness. Its JSON declares
`io.farfrontier.palemirror.internal.frontier.v3` as the mixin package. Mixin
owns every class below that package and correctly rejects NeoForge's direct load
of the ordinary `FrontierV3PilotFixtureBootstrap`.

Only the two actual pilot mixin classes may live in a dedicated
`io.farfrontier.palemirror.internal.frontier.v3.mixin` namespace. Ordinary
fixture, lifecycle, hook and probe classes remain in the parent package. The
three package-private production target/runtime classes must remain
package-private. Cross-package mixins select inaccessible targets by exact
string name and call a public pilot-only hook facade; the one inaccessible
runtime handler argument is passed as `@Coerce Object` and type-checked/cast by
that facade inside the owning parent package. No reflection, production
visibility change or main-code test seam is permitted.

## Exact scope and baselines

Allowed paths only:

- `pale-mirror-neoforge/build.gradle`, baseline
  `5ec51edc0ddd90be354ac31248c59e0bf5c8b97a560b9a4b57dd17b99cde5d1d`;
- `pale-mirror-neoforge/src/pilot/resources/pale_mirror.frontier_v3.pilot_crash.mixins.json`,
  baseline `0cec8e60f8eb93aa88f1965a9e8ef29640bd550e9504e8be9993df1140bad590`;
- old `.../frontier/v3/FrontierV3DurableCrashWindowMixin.java`, baseline
  `2ca937c734ade9991e9813d02bb05812d7c63117a119b11f78b63c71912d7764`,
  removed only as the source half of the relocation;
- old `.../frontier/v3/FrontierV3HarvestCrashWindowMixin.java`, baseline
  `70fdfd5f4cb44d76ca1bbe00bb24590ed10b105dc269ad8504eba379b9f4b756`,
  removed only as the source half of the relocation;
- new `.../frontier/v3/mixin/FrontierV3DurableCrashWindowMixin.java`, absent;
- new `.../frontier/v3/mixin/FrontierV3HarvestCrashWindowMixin.java`, absent;
- `.../frontier/v3/FrontierV3PilotCrashHooks.java`, baseline
  `ab50794d03400cec7bfcf24f6b3dc5af9375b09bb26a3918971faf132779b06a`;
- `pale-mirror-neoforge/src/test/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3CrashBoundaryProbeTest.java`,
  baseline `421debf4e542a062fd3c2cd05e6f76b27f6d62d3110975a5df35128eb6fcb9ac`.

The abbreviated Java prefixes above resolve under
`pale-mirror-neoforge/src/pilot/java/io/farfrontier/palemirror/internal`.
No target production class, other pilot class/resource, prepared-build logic,
scenario/contract, metadata, workflow, docs/ledger, Git or live-service edit.
No native run.

## Acceptance

1. Regression first adds a focused test which reads the actual pilot mixin JSON
   and rejects a declared mixin package that owns the ordinary fixture/hook/
   probe package. It also requires the exact dedicated package and exact two
   relative mixin class names. The unchanged baseline must fail this test.
2. Relocate exactly the two mixin sources to the matching `v3.mixin` directory
   and package. JSON `package` becomes exactly that namespace; its remaining
   required/minVersion/compatibility/mixins/injector semantics are unchanged.
3. Both mixins use `@Mixin(targets = "<exact current target FQCN>")`; no target
   class becomes public. Public static methods on the pilot-only
   `FrontierV3PilotCrashHooks` facade expose only the three existing probe
   notifications. The harvest mixin passes its inaccessible runtime parameter
   as `@Coerce Object`; the facade rejects a wrong runtime type before its
   in-package cast and delegates unchanged probe semantics. No reflection.
4. Update `verifyPackagedJar` to forbid the relocated class paths and retain all
   existing pilot-resource/symbol exclusions. Old compiled mixin paths must be
   absent after a task-owned clean pilot compilation; new compiled mixin paths
   must exist only in pilot output. The distributable JAR contains neither.
5. Executor may run only:

```bash
./gradlew :pale-mirror-neoforge:test \
  --tests '*FrontierV3CrashBoundaryProbeTest' --no-daemon
./gradlew :pale-mirror-neoforge:clean \
  :pale-mirror-neoforge:compilePilotJava \
  :pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment --no-daemon
git diff --check
```

Task discovery confirms the module owns `clean` and no narrower generated pilot
clean task. That Gradle-owned clean is permitted solely to prevent stale old
package class files from contaminating review; do not delete build directories
directly. Main owns full Node, critical Gradle, JAR inspection and fresh native
revision6.

## Stop rules

Gate A must verify the Java/Mixin coercion and target-string design against the
active toolchain, exact eight-path scope/baselines, regression and package/JAR
ratchet. Stop READ_ONLY if compilation needs a production target/runtime class
made public, reflection, remap/require weakening, a connector/plugin, metadata,
another source file or native execution.

## Gate A evidence

Terra accepted revision1 read-only on 2026-09-05. The retained revision5 bundle
matches the stated cause; all six existing hashes matched and both destinations
were absent. The executor confirmed from the active Mixin 0.8.7 contracts that
string targets support package-private targets and callback coercion admits the
runtime reference as `@Coerce Object`. The parent-package type check/cast,
pilot-output test resource, exact eight paths, module-owned clean, package/JAR
ratchets and stop rules are sufficient. No file, test or native process was
changed or started.

## Gate B evidence

Terra implemented exactly the eight granted paths and froze. After adding only
the actual-resource regression, the focused baseline ran seven tests with one
intended failure on the broad package. Final focused execution passes eight
tests. A Gradle-owned module clean followed by pilot compile/preparation passed
22 tasks in 9 seconds; only the two relocated `v3/mixin` class files exist in
pilot output and both old parent-package class files are absent. The JAR lookup
for the JSON, hooks, probe and old/new crash-mixin paths was empty.

Main challenged the first Gate B freeze because string `contains` assertions
did not enforce an exact two-entry array. The executor changed only the granted
test to structurally parse the real JSON and compare the exact package and
ordered two-name list, then re-froze. Main reviewed all implementation files and
independently repeated the focused pass. Final SHA-256 values:

- `pale-mirror-neoforge/build.gradle`:
  `169cbca2cf4570700df96e0ea18639acf0ae35b05352e9cd014b46a5f3ab21e6`;
- pilot mixin JSON:
  `2b14326a8285a9f0e44b4c7637ded16a709db69f488f7fed7cb0b735c6334956`;
- relocated durable mixin:
  `95d14dcddfb3a28ed5ab7de86af7aa75b22e8a0161f88c64b3a2c780a94eb1f8`;
- relocated harvest mixin:
  `03104e44fb29a753ae2a6112f16c0d5d1004ccf39fd766649b87841798de279a`;
- `FrontierV3PilotCrashHooks.java`:
  `624d8ab2b83041b31ffd88facf40c88c1b710423bcbb3469ae4eb12c0f436a22`;
- `FrontierV3CrashBoundaryProbeTest.java`:
  `823694721f77f1f49f010bb764e313b5f2fbbccffddfb6c0494bd43d93c19c97`.

Both old source paths are absent. `git diff --check` passes. Gate B accepts the
bounded implementation and closes its writer grant. Full Node, critical Gradle
and a fresh native revision remain main-owned.

## Gate C evidence

Main's full Node suite passed 169/169 in 395 milliseconds. The exact critical
gate passed 71 tasks in 2 minutes 15 seconds, including 290/290 required
GameTests in 57.29 seconds, normal all-dimensions save, `check`, `build` and
`verifyPackagedJar`. Direct inspection again found none of the pilot JSON,
probe, hook or old/new crash-mixin paths in the sole packaged JAR. Its SHA-256
remains `4ea05b2cec716dd53980a57d0016631df3fb1af311a11a774a6a259f52e4ce5a`.
`git diff --check` passed.

Gate C accepts only the namespace/isolation correction. Revision5 remains a
failed native result; actual bootstrap, target application and crash
reachability require a fresh identity-bound revision6.
