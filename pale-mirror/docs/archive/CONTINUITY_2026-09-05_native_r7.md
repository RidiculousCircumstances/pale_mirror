# Continuity Ledger

## Goal (success criteria)
- Implement Pale Mirror Frontier v3 from `docs/frontier-v3-contract.md`, `docs/frontier-v3-execution-semantics.md` and `docs/frontier-v3-implementation-plan.md` as a greenfield Java event-driven simulation with production two-way Minecraft materialization.
- Deliver an autonomous 1024×1024 world with 12 settlements and one distributed hive, exact actor/object identities and resource quantities/custody, continuous HOT/COLD shared rules/history without equal physical fidelity, bounded COLD knowledge, cross-scene causality and no protected zones. Legitimate physical consequences are accounted within bounded work; unloaded aftermath and confirmed/in-flight/ambiguous recovery obey the execution-semantics contract, never blanket immediate-world or WAL-atomicity promises.
- Deliver through `docs/engineering-agent-protocol.md`: the supervisor only engineers/documents/reviews; one `gpt-5.6-terra` high subagent implements approved bounded orders. Prove continuity, lasting player causality and no systematic mode-switch advantage; preserve inherited WIP and human acceptance boundaries.
- Cut production over to v3 and remove the v2 runtime only after deterministic, full-scope, performance, restart/recovery, real-world and player-comprehension gates pass.

## Constraints/Assumptions
- Workspace: root owns pack/deployment; independent ignored nested `pale-mirror/` owns source/docs/tests/build. Preserve both histories and inherited WIP. Inspect both repositories separately; no blanket staging/reset.
- Java21, Minecraft1.21.1, NeoForge21.1.248. V3 is fresh-world-only; current route-patrol cut accepts snapshot126/envelope41 only. No V2 migration/shared state/fallback. World recreation is an explicit scoped operation, never runtime hydration repair.
- Main engineer writes normative Markdown/declarative architecture only, NEVER source/tests/scripts/build/CI/config. Exactly one granted Terra high executor writes implementation. `docs/engineering-agent-protocol.md` owns Gate A/B/C and bounded work orders.
- Assigned executor: `/root/terra_crash_completion`, gpt-5.6-terra high; all source/run grants CLOSED after native revision7 failure. Terra owns read-only causal diagnosis; no test/native retry or benchmark. Former executors must not resume. No escalation/approval waits.
- No force-loading, canonical mutation test APIs, arbitrary world-file edits or fabricated lifecycle acknowledgements. One local heavy-run owner and at most one visible native client on DISPLAY=:0. No commits, migration, push, deployment, live-world reset or V2 removal under the active order.
- `architecture.yml` owns boundaries/invariants; code owns implementation; this file is the only active ledger. Preserve historical evidence unchanged. Java1000-line and style/debt limits are ratchets, not targets to raise.

## Key decisions
- User accepted outcome-based delegation: Terra owns diagnosis/local design,
  helper/file choices and focused test iterations within a component boundary;
  engineer owns requirements, architecture boundaries and independent milestone
  acceptance. No routine duplicate investigation/full-suite runs or per-file
  micromanagement. Protocol/template/AGENTS and active order amended; safety,
  production meaning and human gates are unchanged.
- User amendment2026-09-05: accept retained22.52% restart benefit;25% is advisory, not a blocking floor. No native runs to chase the gap. Preserve correctness/recovery,3x feedback and2.5x four-worker requirements; review future regressions.
- User authorizes GitHub CLI operations needed for CI-worker setup and proof. This removes a future tooling-permission question but does not skip the accepted order: finish local F0.VA source/native gates, then the separately reviewed history-preserving monorepo/push step, then provider workers.
- Player promise is continuity/causality, not identical HOT/COLD physics. Exact actors/meaningful objects and resource quantities/custody survive transitions; fungible stacks may split/merge. COLD knowledge is bounded, aftermath is causal and locally reconciled, scenes give no protected zones, WAL intent is not physical-save confirmation. `docs/frontier-v3-execution-semantics.md` is normative.
- Canonical authority is one ordered server-thread command/event/due-action lane. Server ticks advance zero-player time; stopped time pauses; sleep is an explicit command. No second scene, movement, economy, AI or resource owner.
- F0.VA inside F0.V is mandatory before original native/crash matrix closure; preserved F0.1 harvest and MAT-004 patrol M0 remain PAUSED. No new MAT/gameplay breadth before F0 exits. SDK/first-class AI, terrain-aware movement and family semantics keep their owning F0 slices; infrastructure tests do not close them.
- Local F0.VA -> separate history-preserving NON-squashed monorepo order (workspace root remains root, Pale Mirror at pale-mirror/, both old HEADs ancestors) -> verified main push without force to `git@github.com:RidiculousCircumstances/pale_mirror.git` -> actual4-worker/provider and pinned-host proof -> original F0.V -> F0.1–F0.6 -> remaining waves. External absence does not waive this sequence.
- Separate M0 canonical, M1 endpoint, M2 continuous physical execution and M3 unbriefed player comprehension. Production cutover/V2 deletion require all technical and human gates; automation cannot claim human acceptance.
- Full retained product decisions (human/hive capabilities, Hive physiology, Create rail-only future provider, typed3D movement and finance question) are indexed in the archived ledger and their normative documents. They remain binding; this compaction changes no requirement.

## State

### Done
- Previous external writer explicitly handed off current21 on2026-09-05; main confirmed its PID absent. Source HEAD `0ac6f97a695d636ae928313de7a807d1f3ea05a1`; root HEAD `891a4dfe1a613857124775696973d065cc9399cb`. Histories/WIP preserved.
- Accepted managed orders: PM-HANDOFF-01; PM-F0VA-TIMING-POLICY-01; PM-F0VA-LOCAL-GATE-01; PM-F0VA-CI-BOOTSTRAP-01; PM-F0VA-CI-COMPOSITION-01; PM-F0VA-PERSISTENT-DESIGN-01; PM-F0VA-PERSISTENT-PLAN-01. Individual work orders retain exact scope/evidence; none closes F0.VA.
- Latest accepted compiler/preflight: exact13-lane assignment,1/2world runtime plan admission,5crash declarations, trusted worker/measurement identity, valid executable restart halves. Actual shard consumes preflight but still launches clients per lane. Main focused28, fullNode144, critical290/290GameTests pass; critical session22719 exit0,71tasks/1m11s,2026-09-05 16:23:22.644+05. Tuple d8d307f8…b02ed4 in accepted order.
- Current21 feedback report `build/f0va-current21/feedback-timing.json`:18lanes,5.065026x aggregate >=3x, run9f75476b-7f1f-47d7-b516-19fe8272db4a. Historical feedback evidence only.
- Current21 persistent smoke proof `build/f0va-current21/persistent-native.json`, rund96f13b0-64cd-4fc3-8403-ca73d08bbd7c:one client,4servers,3worlds,28journal barriers plus final durable save/exit/port closure. Fixed smoke/graceful sequence, NOT actual CI assignment proof.
- Historical artifact41ac2b1fe122d03361976f5614c7ef95052e5a94188297a3ff770a8ccdd82d53; prepared source d835013a…c9b6/1531inputs and verification source9f3ccb68…17cc/2016inputs are different namespaces, not drift. Reprepare affected inputs for new evidence.
- Old failed former25% report `build/frontier-v3-scenarios/f0v-timing-smoke-persistent-disconnect.json` retains six successful samples on older artifactecfe42…ff4e. User accepts its22.52%; no report rewrite/current-source promotion.
- PM-F0VA-PERSISTENT-CRASH-01 revision3 ACCEPTED. Main Java25149 PASS18tasks/1s,12tests (2SessionControl+10ExpectedCrash); focusedNode25/25 PASS147.79ms; fullNode148/148 PASS391.38ms. Critical19859 exit0,71tasks/2m8s,290/290GameTests at2026-09-05 18:55:14.259+05; build/package PASS, JAR259e8fde6516539315216616c483a040df856e1b71a052b5019bba549d0746c3. Accepted order retains13source hashes and full mapping; this is protocol/source proof, not actual assigned-runner/native proof. Acceptance docs35012 PASS34tasks/16s.

### Now
- Native revision7 FAILED pre-client: applied-mixin preflight reports durable
  target missing the pilot mixin. Source59af02f1…1a4b/plane54f3dbe…1c79/
  buildcbce7be4…410d; exact worker3 pair. Bundle worker-3-5c0dcf04…be3b
  retains error23:17:50/save23:17:57; no client. Main checked identities/log,
  reported serverPID3383619 absent and all four ports closed. Terra diagnoses
  actual transform absence versus applied-info lookup error read-only. No retry.
- `PM-F0VA-NATIVE-QUAL-01` revisions1–4 are retained failures. Revision1 caught
  the corrected Gradle token mismatch. Revision2 source0cb18ada…72b2/
  planc1fae105…a670/build6eb5e38a…5d12 passed it, started server PID3090549,
  then caught non-idempotent normalized runtime content identity before client
  start/SERVER_READY. Bundle under worker-3-9a6ef13a…; RCON save/exit cleanup
  passed, ports closed, live service untouched, benchmark not started.
- Revision3 source0de9a2b0…9ba3/plan5cd675fb…e9d/build865aa455…6829
  passed both prior blockers and started server/client, then exposed a pre-login
  Quick Play logout incorrectly classified as active connection loss. Bundle
  worker-3-467ea40f… retains both PIDs; client exited1, server RCON save/exit and
  port cleanup passed, live service untouched, benchmark not started.
- Revision4 sourcee8c4af0b…a2a1b/plan83c24f24…7b8/build26624d12…6f1b
  passed all earlier blockers, connected one real client, acquired the natural
  HOT lease and durably emitted the exact release at revision2423. No
  `PMV3_CRASH_BOUNDARY`/expected-loss arm appeared; the client hit its exact
  300000ms deadline. Bundle worker-3-d24d50c0… binds serverPID3156982 and
  clientPID3157491. RCON all-dimensions save/exit and port cleanup passed;
  benchmark was not started.
- Revision5 source8ffd8cf6…38ae/plan756be5b2…b6cd/build4a1f9d38…08ea
  proved the early `--mixin.config` input is consumed, then failed before server
  readiness/client launch: the pilot JSON's broad `internal.frontier.v3` mixin
  package owned ordinary `FrontierV3PilotFixtureBootstrap` and Mixin rejected
  its direct NeoForge load. Bundle worker-3-f1ec75b7… retains the complete log
  and run4ea56364…a295. Ports/processes clean; benchmark not started.
- Historical revision1 detail: fresh JAR05117ce1…68e7/
  source7b62b2b0…b285/plan549dd6f2…e9de3
  correctly selected worker3's two lanes/two worlds, but Gradle rejected the
  contract-valid `observed_at_boundary` crash revision. Failed shard and bundle
  are retained; no server/client started, ports closed, live service untouched,
  and the3world benchmark was not started.
- `PM-F0VA-NATIVE-CRASH-REVISION-01` Gate C ACCEPTED; implementation grant
  CLOSED and executor READ_ONLY. Regression7/8 before,8/8 after; main real
  Gradle positive token passed while arbitrary token and partial tuple failed;
  full Node169/169 and critical71tasks/1m16s with290/290GameTests passed.
  Final build.gradle98b59e9e…5c18/test845f39c0…3de.
- `PM-F0VA-NATIVE-RUNTIME-IDENTITY-01` Gate C ACCEPTED; implementation grant
  CLOSED and executor READ_ONLY. Regression17/18 before,18/18 after; main
  retained-plan direct/repeated/JSON validations passed with unchanged hash and
  tamper negatives. Full Node169/169 and critical71tasks/1m13s with290/290
  GameTests passed. Final source47857597…b504/testc5e2566b…46ac.
- `PM-F0VA-NATIVE-INITIAL-CONNECTION-01` revision2 Gate C ACCEPTED; grant
  CLOSED and executor READ_ONLY. Regression-first exact focus was14 tests/one
  intended fail, then executor/main14/14 pass; final source2574c620…2bcd and
  test76ac461e…c11e. Full Node169/169 passed. The first critical run hit an
  unrelated transient disposable-fixture save integrity failure; its task-owned
  clean retry passed71 tasks/1m15s,290/290GameTests, normal save, build/package
  and JAR4ea05b2c…e5a. Native reachability is not yet proved.
- `PM-F0VA-NATIVE-CRASH-MIXIN-BOOTSTRAP-01` revision2 Gate C is ACCEPTED;
  implementation grant CLOSED and executor READ_ONLY. Revision1 was challenged
  read-only before any
  write because generic `--mixin` is not the active ModLauncher service option;
  independent Mixin0.8.7/ModLauncher11.0.5 bytecode inspection proves the exact
  namespaced spelling `--mixin.config`. Exact five-file correction moves the
  non-packaged crash mixin from late `ServerStartedEvent` registration into the
  pilot-server launch arguments and makes prepared identity fail closed on a
  missing/duplicate/misplaced pair. Regression-first caught the old acceptance;
  final focused Node and21-task compile/preparation pass. Main independently
  repeated both and inspected one exact generated server pair/zero client pair.
  Final five hashes are retained in the order. Main full Node169/169 and exact
  critical71tasks/2m15s with290/290GameTests, all-dimensions save and package
  isolation passed; JAR4ea05b2c…e5a. Fresh native reachability remains open.
- `PM-F0VA-NATIVE-CRASH-MIXIN-NAMESPACE-01` revision1 Gate C is ACCEPTED;
  implementation grant CLOSED and executor READ_ONLY. All six existing
  baselines matched and two
  destinations are absent. It gives only the two pilot mixins a dedicated
  subpackage, retains package-private production targets via string targets and
  a pilot-only typed bridge, ratchets package tests/JAR exclusion, and forbids
  production visibility or metadata changes. Regression-first caught the broad
  package; final8 focused tests and clean22-task pilot prep pass. Main rejected
  a weak contains-only test, then accepted exact structural JSON assertions.
  Old classes absent/new pilot-only classes present/JAR lookup empty. Final
  hashes retained in order. Main full Node169/169 and critical71tasks/2m15s
  with290/290GameTests/all-dimensions save/package isolation pass;
  JAR4ea05b2c…e5a. Fresh native remains open.
- `PM-F0VA-PERSISTENT-RUNNER-01` revision3 Gate C source integration ACCEPTED. Main focused Node67/67 PASS203.45ms, focused Java14 PASS; full Node168/168 PASS447.72ms; critical BUILD SUCCESSFUL2m8s/71tasks,290/290GameTests, check/build/package PASS. JAR05117ce1…68e7. No native/provider evidence yet. WIP and both histories preserved; F0.VA remains open.
- Outcome: actual13lane CI assignment through the existing one-client runner, typed abrupt lifecycle, bounded stamped diagnostic VALUES and unchanged schema2 semantic checks. Pin spawn PID before readiness, distinguish portable/local/serialized identities, retain all assertions and correct restart offsets. All13lanes have zero frames; no M3 claim.
- Declared partial-release correction is implemented in abrupt variant only: ordinary departure after action3/restartAfter4, all5windows/terminal assertions preserved, contract now aed8c1b108b4fb51edb29c41e5917d48ab83ae28516004efb6ee531e67595cc2. Same connected client awaits the real probe within its deadline; early-crash pre-half is not replayed or falsely passed. This is setup/source progress, NOT native reachability proof.
- Accepted runner source now has reconstructive four-worker admission, no assigned build fallback, one Java-authenticated quiescent prefix through ACK/loss/client/supervisor, closeable bounded failure waits, all reached stamped evidence before seal, distinct original/compiled/runtime identities, and retained plan-bound crash receipts for all5 boundaries. This does not yet prove real Minecraft reachability.
- Latest nested diff check PASS,208dirty/untracked entries after runner WIP; no executor test/native command active. Both histories and inherited root .f0v-baseline/ preserved. Previous full-gate generated core-game-test fixture alone was recreated; live server untouched.

- Revision6 source0c67c9a4…65d3/plan46cf237b…1696/build4e0d597e…60bd
  failed after the exact release trace at revision2425: no crash boundary marker
  or expected-loss arm, client deadline300000ms. Bundle worker-3-0a3f3584…
  retains serverPID3277899/clientPID3278342 and all-dimensions save. Main
  rechecked both PIDs absent and all four qualification/benchmark ports closed;
  retained revision6 world exists. Benchmark not started. Both HEADs unchanged;
  nested207 dirty/untracked entries, root only inherited .f0v-baseline/.
- Terra completed the READ_ONLY causal investigation of revision6. Shared
  production/pilot mixin namespace is an unproved hypothesis,
  not an accepted cause. No blind native retry is authorized.
- `PM-F0VA-NATIVE-CRASH-IDENTITY-01` revision3 Gate C ACCEPTED:
  Terra Node169/169 and critical71tasks/2m12s passed. Main checked reviewed hashes,
  JAR4ea05b2c…e5a/pilot isolation, actual290/290 at23:11:08 and all-dimensions
  save23:11:16. No duplicate suite run. Parent revision7 now delegated to Terra,
  fresh identity required; no source writes or benchmark launch authorized.
  Historical Gate B:
  B1 fixed with sticky ambiguity; main reviewed code/tests and actual15/15 XML.
  Final three hashes in order. Terra owns full Node and exact critical gate;
  sources frozen, no native pilot/world reset. Historical review finding:
  B1 duplicate prepares toggle witness null/populated, so third duplicate may
  regain authority; existing test covers only two. Terra chooses correction
  and regression within pilot/test component scope. Terra delivered revision2
  and froze. Baseline10tests/2intended failures independently observed; final
  focused13/13,18tasks/diff check PASS reported. Three hashes retained in order.
  Autonomy amendment preserves current code/evidence, expands only local design
  discretion for future reopened corrections. Native/full acceptance remains.
- Main found a definite independent mismatch: harvest scene support owner is
  site.settlementId(); releaseEvents emits ProposedEvent with that owner, while
  crash probe compares event.subject to the armed job ID. DiagnosticTrace uses
  job ID separately, so its matching subject did not prove event matching.
  Terra must audit all5 windows and propose exact typed participant matching;
  accepting every release of the settlement is forbidden. Actual mixin
  application still needs evidence; do not infer it from this source diagnosis.
- Evidence caveat: namespace order's permitted module clean removed revision5
  world; its root bundle/inline log survives. No future module-wide clean may
  discard retained run worlds. Revision6 world remains present. Docs diff
  checks and34-task guardrails passed18seconds after revision6 recording.

### Next
- Review Terra's causal diagnosis of revision7 preflight failure; agree the
  bounded correction/diagnostic before fresh revision8. Worker3 crash/recovery
  remains unproved; only after success authorize unchanged3world benchmark.
  After local F0.VA qualification, follow monorepo/push/provider; gh authorized.
- After deterministic blockers, consider one fresh V3-AUD-052 arrival_checkpoint_one corrective native proof; no F0.1 breadth. Finish local F0.VA then ordered monorepo/provider steps above, then original matrix and preserved F0.
- Family exits remain: F0.V descriptor/comparator/knowledge/custody; F0.1 partial work; F0.2 independent eligibility/knowledge/aftermath; F0.3 observer-free custody; F0.4 navigation/decision authority/cross-front interactions; F0.5 confirmation/fencing; F0.6 calibration/switch neutrality. None is proved by timing alone.

## Open questions
- No configured remote or actual provider/pinned-host evidence: UNCONFIRMED_EXTERNAL until the approved later migration/push/provider step. GitHub CLI use for that future CI-worker work is user-authorized; do not skip the intervening local and repository-migration gates.
- No current user decision blocks the runner order; scenario reconciliation is an engineer-owned preservation of existing recovery meaning. Future finance slice still needs the accepted-or-replaced rule for bounded interest-free company working-capital credit and missed-payment handling; no credit/debt implementation authorized yet.
- Future Create/add-on pins, rail/industrial balance, exact growth/infection/territory calibration and actor budgets remain deferred product work. Real-display, clean-room, co-op and unbriefed-player M3 gates remain human acceptance, including broader visual/tactical readability.
- Detailed historical facts and unresolved product questions were preserved verbatim in the archives below, not erased or silently closed.

## Working set
- Active order above; `docs/engineering-agent-protocol.md`, `architecture.yml`.
- Contract/execution-semantics/implementation-plan/seamless-foundation, accelerated-verification-loop, integration-feedback-foundation, Terra brief, architecture-audit.
- Active sources: tools/frontier-v3-test-pilot/{src,test} persistent-worker-plan/persistent-matrix/lifecycle-barrier/run-f0va-persistent-matrix/run-persistent-matrix-client/run-ci-matrix-shard; NeoForge SessionControl/TestPilotClient, SessionControlTest and revision3 path17 ExpectedCrashTest.17-file scope includes the declared resource-site-harvest-f0v.json departure; accepted crash wire/controller remain reused read-only.
- `docs/archive/CONTINUITY_2026-09-05_engineer_handoff.md`: inherited pre-managed history.
- `docs/archive/CONTINUITY_2026-09-05_crash_protocol_handoff.md`: verbatim ledger before this compaction, including full product decisions and earlier managed evidence. Both are historical archives; this file alone owns active state.
