# PM-F0VA-NATIVE-CRASH-IDENTITY-01: exact semantic crash participants

Specification revision: 3. Parent: F0.VA / native qualification revision6.
Risk: critical-code, pilot-only crash instrumentation.
Engineer: /root. Executor: /root/terra_crash_completion, gpt-5.6-terra high.
State: GATE_C_ACCEPTED. Source and verification grants CLOSED.

## Revision3 autonomy amendment (takes precedence below)

The user accepted outcome-based delegation. Preserve revision2 implementation
and its reported regression evidence; no rewrite is requested by this amendment.
The single-witness design, exact helper layout, continuation checks and Mixin
API below document the reviewed approach, not mandatory implementation recipes.
Binding outcomes remain exact semantic job/lease identity across all five
declared windows, bounded test-only state, fail-closed ambiguity, early proof
of applied instrumentation, unchanged production semantics and regression/native
evidence appropriate to those claims.

For any review correction explicitly reopened on this order, Terra may design
the solution and add/reorganize directly relevant helpers within NeoForge's
pilot Java `io/farfrontier/palemirror/internal/frontier/v3/` and corresponding
test Java package. The three files below are expected starting points, not a
hard whitelist. Production source, build/config, scenarios/contracts, docs,
Git, deployment and world edits remain excluded. Focused local test selection
and correction iterations belong to Terra; native/full gates retain explicit
resource ownership. Main reviews stable output and risk-selected checks rather
than automatically duplicating every executor run.

Revision2 delivery reports baseline10tests/2intended failures, then13/13 PASS,
18Gradle tasks and diff check PASS. Source hashes: probe
`be47cac853dd2462281f33f49b460fcc9e0a6c5a7af21f0c0867fd426548060b`,
test `e135bd94b9ce152ee585366e1d9bfce957ab4df335a3f07f3fd495874a1be158`,
bootstrap `c48e2c46e5b93a26a2f3c41f10e5189f81e189fa2180663f973ebcbb81aebb81`.
This records handoff, not engineer acceptance or native proof.

Review finding B1: observeReleaseWitness toggles the witness between populated
and null for each matching prepare. Three duplicate prepares consequently leave
a usable witness, although two are rejected; the existing duplicate regression
checks only two. Ambiguous duplicate evidence must not become valid merely
because another duplicate arrives, within one transaction or across callbacks.
Terra owns the correction and suitable regression design, while preserving
ordinary unrelated-job traffic and legitimate lease turnover. Local source/test
grant is reopened within the component boundary above; focused tests permitted,
no native/full run yet. Freeze at the next stable review delivery.

## Baseline and cause

Preserve both independent repositories and all inherited WIP. Nested HEAD is
`0ac6f97a695d636ae928313de7a807d1f3ea05a1`; root HEAD remains
`891a4dfe1a613857124775696973d065cc9399cb`. No native measurement is active.
Read AGENTS, CONTINUITY, engineering-agent-protocol, execution-semantics,
architecture's fixture-isolation/test-pilot flow and the parent qualification
order. Release-verification applies; no live operation is authorized.

Actual harvest prepare/release events use the settlement as event subject;
their diagnostic trace uses the job. The probe incorrectly compares its armed
job ID with event subject. Thus both named windows reject valid events even
if the mixin applies. Progress/HOT checkpoint and physical windows use site
identity and must retain their existing meaning. Shared mixin package is not
an established defect and is outside this correction.

## Outcome and invariants

Match the exact semantic participant from real typed facts. Never replace the
armed job with the settlement or accept another job's release. Preserve all
five boundaries, exact revision/late-bound semantics, payload names, marker
format and park-after-durable-before-canonical-install ordering. No canonical
or physical mutation, production seam, reflection, log parsing or wildcard
identity matching.

Prepare must use the real ResourceSiteHarvestSceneLeasePrepared payload and
its typed harvest cause/job ID, not a payload-type string alone.

For release, retain at most one pilot-only immutable witness of the armed
job's real observed prepare: exact lease ID, job and event owner, scoped to the
probe's world/run. Match the real SceneLeaseReleased lease ID against it;
validate any required continuation using actual typed scheduling payloads.
A same-transaction scheduled event for the job is not by itself a lease→job
proof. Foreign/ambiguous/duplicate witnesses cannot replace a live witness or
cause a match. Clear obsolete witness after its release; fixed-revision arms
must be able to observe a later legitimate prepare/release without unbounded
history. No witness means no successful release match.

The declared native qualification starts fresh and observes prepare before
release. Do not claim this volatile witness supports arming against a lease
recovered before probe observation; such future coverage requires an explicit
read-only hydration design. This order does not narrow production recovery
requirements or waive any existing scenario. Gate A must verify this fit.

## Exact write scope

Only three files, relative to pale-mirror-neoforge:

- `src/pilot/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3CrashBoundaryProbe.java`,
  baseline `d11c6bf201570d4d91f8c53d197820ce9229cdd24824eb933ec3f0bc9887a1de`;
- `src/test/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3CrashBoundaryProbeTest.java`,
  baseline `823694721f77f1f49f010bb764e313b5f2fbbccffddfb6c0494bd43d93c19c97`.
- `src/pilot/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3PilotFixtureBootstrap.java`,
  baseline `9c8adc8a14976d8765c80b8fd090fe61c4b047103490e4f2fc7d6402175534f5`.

No source outside these paths, build/mixin config, hooks, scenario/contract,
docs/ledger, CI, Git or runtime edits. Propose a scope amendment if a separate
test fixture is necessary; never weaken tests to fit the file list.

## Acceptance and evidence

1. Regression-first tests use real typed prepare/release and scheduling facts,
   with settlement subject distinct from the armed job. The baseline must fail
   the intended positive cases. Prefer real planner output where existing
   fixture helpers make it practical; synthetic string-only payload positives
   do not prove these two boundaries.
2. Prove prepare and release exact matching, wrong job within same settlement,
   wrong lease, missing witness, unrelated scheduled job event, duplicate or
   contradictory witness, wrong revision, wrong payload, and sequential lease
   turnover for a fixed-revision arm. No test may actually park indefinitely.
3. Retain all existing physical/progress/HOT-window, malformed-arm, late-revision
   and mixin-resource isolation checks. Matching helper tests must exercise the
   same state transition used by the actual afterDurableAppend callback.
4. No production state/API/codec changes. Probe memory is constant-size and
   no observation changes Minecraft or canonical state. JAR isolation remains.
5. Before serverRunReady, only when crash instrumentation is configured,
   resolve the selected target with an ordinary class literal and require the
   exact expected pilot mixin in Mixins.getMixinsForClass(targetName). Main
   inspected active Mixin0.8.7 source: this returns ClassInfo.getAppliedMixins,
   not just registered configurations. Do not use reflection or force-load
   chunks. Unknown boundary/missing/wrong applied mixin fails visibly before
   client start. Add focused positive/negative checks of this preflight policy
   within the existing test file; real application is still native evidence.

Release continuation corroboration is the current exact two-event ordered
planner result: typed release followed by typed Rescheduled for the same job,
matching schedule/replacement IDs and expected COLD_PROGRESS kind, deterministic
ID, priority, weight and dueAt=transaction.instant+1. Reject malformed/extra/
duplicate candidate facts. Unrelated jobs' ordinary transactions must not
erase the armed job's witness; contradictions concerning that witness must
fail closed. Tests cover both distinctions.

Executor's commands after explicit IMPLEMENTING only:

```bash
./gradlew :pale-mirror-neoforge:test --tests '*FrontierV3CrashBoundaryProbeTest' --no-daemon
git diff --check
```

No clean, native or full gate until Gate B. Main independently repeats focus,
full Node and critical gate, checks packaged isolation and reviews all five
window mappings. Actual mixin application/native reachability remains an open
qualification item; a pure test is never promoted to physical proof.

## Gates and stops

Gate A: acknowledge exact cause, single-witness lifecycle, fresh-run fit,
baselines, three-file scope and regression strategy; challenge unsound assumptions.
Engineer grants IMPLEMENTING explicitly. Gate B: stable diff and focused
before/after evidence, freeze writes. Gate C: independent acceptance only.
No commit, migration, push, deployment, broad clean, force-loading or world edit.
Stop for a required authority/scope change; never adjust timeouts or scenarios
to manufacture a pass. Parent native revision7 remains paused until acceptance.

## Gate A record

Terra read revision2 and confirmed all three baseline hashes, typed event cause,
single-witness lifecycle, unrelated-job preservation, fixed-revision turnover,
fresh qualification scope and applied-mixin preflight. No design question or
executor process remained. Main documentation diff/guardrails verification
passed34tasks in18seconds and exited before granting executor tests.
Engineer accepts Gate A and grants only the three paths and focused commands
above; freeze at Gate B for independent review. No native/full gate grant.

## Revision3 Gate B acceptance and verification grant

Main reviewed the stable B1 correction: ambiguous evidence stays marked until
related release cleanup; duplicates cannot toggle eligibility back on. The
same production matching path is tested for repeated callbacks, one-transaction
duplicates and legitimate later turnover. Actual XML confirms15tests/0failures/
0errors at2026-09-05T18:07:30.195Z. Terra reports baseline15tests/1intended
failure. Bootstrap unchanged. Final reviewed hashes:

- probe: `9e34aed83bcc1050968362966ec5bfbb7b364e93493020cfd7b2f3675a4046de`;
- test: `3cf8631ad1022d230b046fc3d55e80d7894d2aed3a9bb3a3153ecd2e5c5696c6`;
- bootstrap: `c48e2c46e5b93a26a2f3c41f10e5189f81e189fa2180663f973ebcbb81aebb81`.

Gate B accepts the bounded source correction, not native reachability. Terra
now owns one sequential local verification run, with sources frozen:

```bash
npm --prefix tools/frontier-v3-test-pilot test
./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar --no-daemon
git diff --check
```

Inspect the sole packaged JAR for pilot isolation and retain its SHA, command
exit status, required GameTest count, normal all-dimensions save and failure
evidence if any. Only the existing Gradle-owned GameTest runtime is authorized;
no native pilot client, module clean, world reset, production service, Git or
deployment action. Stop at the first failed gate for diagnosis; no blind retry.
Main checks outputs/identity independently without duplicating the full suite.

## Gate C acceptance

Terra ran full Node169/169 in403.6ms and the exact critical gate71tasks in2m12s.
Main independently checked all three reviewed source hashes, the packaged JAR
SHA `4ea05b2cec716dd53980a57d0016631df3fb1af311a11a774a6a259f52e4ce5a`,
and absence of pilot bootstrap/probe/hooks/crash mixins/resource entries.
`build/runs/core-game-test/logs/latest.log` under NeoForge records all290required
tests passed at2026-09-05 23:11:08.144 and all-dimensions save at23:11:16.822.
Both repository diff checks passed. The verification process terminated.

Gate C accepts the exact semantic-identity/ambiguity correction and preflight
source. Native instrumentation application and crash/recovery remain unproved
until the parent qualification succeeds. No F0.VA closure or product claim.
