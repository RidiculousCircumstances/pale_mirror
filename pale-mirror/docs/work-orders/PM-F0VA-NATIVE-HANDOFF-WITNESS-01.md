# PM-F0VA-NATIVE-HANDOFF-WITNESS-01: recognize exact handoff release witnesses

Specification revision: 1. Parent: F0.VA native qualification. Risk: critical-code.
Engineer: `/root`. Executor: `/root/terra_crash_completion`, Terra high.
State: ACCEPTED. Gate C complete; source and full-run grants CLOSED.

## Baseline and outcome

Preserve inherited WIP and both repository histories. Nested HEAD
`0ac6f97a695d636ae928313de7a807d1f3ea05a1`; root HEAD
`891a4dfe1a613857124775696973d065cc9399cb`. Last observed nested dirty count208;
root only inherited `.f0v-baseline/`. No other writer or heavy run is granted.
Read AGENTS, ledger, engineering protocol, release skill, relevant architecture
test-harness ownership and parent `PM-F0VA-NATIVE-QUAL-01` terminal r8 record.

The accepted preflight correction remains intact. Frozen probe SHA
`c05fb8c7192ae9fdba454b7330a57aac9272b29095429086b64823aba5872db9`, test SHA
`0bd83f2376615a1b4a39d7e3acffba280997d00ec1d2e0b8d9c356099f0d5555`.
Terra identified the r8 handoff admission followed by release revision2424.
Main verified the executor's Prepared/Handoff branch, durable typed Handoff
model and probe's Prepared-only witness extraction. This omission necessarily
rejects a release without a previously captured witness.

Outcome: the pilot release-boundary detector recognizes either legitimate
typed resource-harvest admission, while retaining exact identity and bounded,
fail-closed ambiguity handling. No canonical ownership or gameplay change.

## Contract and scope

The durable transaction remains sole evidence; a trace string is not authority.
Retain exact world, job, lease and event owner correlation, exact release plus
COLD continuation structure, revision selection and the existing crash window.
Keep bounded retained state, sticky duplicate ambiguity, unrelated-traffic
isolation, legitimate turnover and malformed/foreign release rejection.
Missing/preexisting recovered witnesses remain fail closed under the fresh-world
qualification contract. Never infer identity from nearby actors or settlement
alone. Do not move the hook or fabricate acknowledgements.

Implementation boundary is the pilot crash-witness detector and its focused
tests under NeoForge `src/pilot/java/.../internal/frontier/v3/` and matching
`src/test/java/.../internal/frontier/v3/`. Expected entry points are
`FrontierV3CrashBoundaryProbe` and its test; executor chooses local extraction,
helpers and fixtures within this component, not per-file approval.
Production runtime/domain, scenarios, runner/wire protocol, mixin configuration,
build scripts and public/persistent meanings are excluded. Normative docs and
ledger are engineer-owned. If another crash-window contract is affected by the
same admission distinction, report it; do not silently broaden its meaning.

## Acceptance and verification

1. A realistic typed Handoff followed by its valid durable release/continuation
   fails a behavioral assertion on the frozen detector and passes after the fix.
   Preserve red evidence before a green run overwrites test reports.
2. Prepared retains its existing behavior. Handoff wrong job, mismatching lease,
   world or event owner cannot arm the release. Duplicate Prepared/Handoff in
   one or separate callbacks remains ambiguous, including mixed ordering and
   later traffic; unrelated jobs cannot erase a valid witness.
3. Existing ambiguity, malformed release, turnover, all-five mapping/preflight,
   unarmed and exact-window tests remain green; no production pilot leakage.
4. Executor supplies stable source/test hashes, exact focused commands/results,
   criterion-to-test mapping and material local design choices at Gate B.
   Focused Java and Node tests and docs diff/guardrails are included in the
   implementation grant; executor chooses exact focused commands.
5. After separate Gate B authorization, run the exact critical gate:
   `./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar --no-daemon`.
   Engineer reviews stable evidence, not a duplicate whole-suite run.
6. Native exact worker3 pair requires a later fresh-identity run grant under
   the parent order; source acceptance alone does not prove native recovery.

## Permissions and stop conditions

Gate A acknowledges outcome, boundaries and risks, then engineer explicitly
grants implementation. Gate B reviews focused evidence before full/native
execution. Gate C is engineer-owned. No native, full critical, benchmark,
commit, migration, push, live service or deployment authority is granted here.
No clean/reset; preserve every retained world, diagnostic and failure bundle.
Stop and report owner/meaning changes, unknown writer, conflicting WIP or
evidence contradicting this cause. Local implementation/test iteration inside
the boundary needs no per-method approval.

## Gate A record

Terra verified both HEADs and frozen probe/test hashes, preserved nested208/root1
dirty entries, and acknowledged exact two-admission handling with no generic
admission widening. No unresolved boundary question or owned active run remains.
Engineer grants implementation and the declared focused Java/Node/docs gates;
Terra owns their local iteration. Full critical/native authority remains closed
until stable Gate B review. Preserve behavioral red evidence before green.

## Gate B initial review

Main verified exact two-type extraction with unchanged release assessment and
bounded ambiguity handling. Probe SHA
`20fcade441f1a21b7a2cd8b541d71af84f2020389fb0596a88d68b2c27a594e2`;
test SHA `6b23d9ae1b59920de196261d95803f44fa36881d704db09d5a58d1765df27f02`.
Retained red XML `build/pm-f0va-native-handoff-witness-01-r1/red/FrontierV3CrashBoundaryProbeTest-red.xml`
SHA `2aed038e4dd4a2cb345ded6ba7b4d18d3d34dc6ec0c10c0f0d09c5c4473aa1cc`
proves18tests/1intended assertion failure; current XML proves19/19green.
Terra reports Node crash-wire2/2 and docs34-task guardrails18s.

Gate B remains open for AC-2 coverage: mixed cases currently begin Prepared;
add Handoff-first duplicates, including same lease identity, and release the
retained lease to test ambiguity rejection rather than only lease mismatch.
Existing implementation/test iteration grant continues; no algorithm rewrite
is prescribed. Full/native grants remain closed until this stable evidence.

## Gate B accepted / full gate grant

Main verified Handoff-first same-lease ambiguity tests in both one transaction
and separate callbacks, and final20/20XML timestamp2026-09-05T19:26:50.085Z.
Probe SHA remains20fcade441f1a21b7a2cd8b541d71af84f2020389fb0596a88d68b2c27a594e2;
final test SHA fd139f099f67a8a9a87aeae457b40644a029e12476c7c9b7a7ae1fbd862cf606.
Gate B ACCEPTED. Source frozen. Terra exclusively operates the exact AC-5
critical command, including updated docs guardrails; no main competing run.
Report terminal tasks/GameTests/save/JAR identity and packaging exclusion.
On failure retain evidence and report cause; no automatic native retry or
scope expansion. Existing Gradle-owned test fixture lifecycle only; no module
clean or retained-world reset. Native/benchmark, commit and all live/deployment
grants remain closed pending independent Gate C review.

## Gate C acceptance

Exact AC-5 command passed71tasks (38executed/33up-to-date),2m13s. Main inspected
core-game-test latest.log:290/290 at2026-09-06 00:29:52.534, all-dimensions save
00:30:01.725 and shutdown00:30:01.960. Reviewed probe/test hashes remain those
accepted at Gate B. JAR SHA
`4ea05b2cec716dd53980a57d0016631df3fb1af311a11a774a6a259f52e4ce5a`
unchanged; direct inspection confirms absence of server CrashBoundaryProbe,
PilotCrashHooks, PilotFixtureBootstrap and crash-window mixins/config. Existing
client pilot classes are present; this is not a blanket no-pilot-class claim.
Terra reports both diff checks pass and unchanged nested208/root1 dirty state.
Gate C ACCEPTED for the pilot witness correction only. Native recovery remains
unproved and is assigned separately as parent revision9 qualification.
