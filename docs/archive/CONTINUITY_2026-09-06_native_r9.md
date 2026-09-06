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
- Assigned executor: `/root/terra_crash_completion`, gpt-5.6-terra high; reconnect-departure order IMPLEMENTING after Gate A ACK. Sole source writer/focused-test owner in client lifecycle component. Full/native/benchmark grants CLOSED. Former executors must not resume. No escalation/approval waits.
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
- Previous writer handed off current21 explicitly; both histories/WIP preserved.
  Nested HEAD `0ac6f97a695d636ae928313de7a807d1f3ea05a1`, root HEAD
  `891a4dfe1a613857124775696973d065cc9399cb`. Last nested dirty count207;
  root only inherited `.f0v-baseline/`. No blanket staging/commits.
- Managed handoff, timing policy, local/CI composition, persistent plan/crash/
  runner source orders ACCEPTED; detailed gates/hashes remain in work orders
  and the native-r7 archive. No source acceptance closes F0.VA.
- Current21 historical evidence:18feedback lanes,5.065026x aggregate;
  `build/f0va-current21/feedback-timing.json`. Persistent smoke proved one
  client/4servers/3worlds/28barriers, NOT actual assigned CI runner evidence.
- Native qualification revisions1–7 remain failed evidence. Corrected source
  defects: Gradle late-revision token; runtime identity idempotence; initial
  QuickPlay logout; early namespaced mixin CLI input; mixin package isolation;
  typed crash participant identity plus ambiguity handling. Root bundles survive.
- Latest accepted order `PM-F0VA-NATIVE-CRASH-IDENTITY-01` revision3 Gate C:
  baseline10tests/2intended failures, then B1 duplicate-witness correction
  baseline15tests/1intended failure; final15/15. Terra full Node169/169 and
  critical71tasks/2m12s pass. Main reviewed stable sources, exact hashes/JAR,
  actual290/290GameTests23:11:08 and all-dimensions save23:11:16.
- Latest accepted source hashes: probe
  `9e34aed83bcc1050968362966ec5bfbb7b364e93493020cfd7b2f3675a4046de`;
  test `3cf8631ad1022d230b046fc3d55e80d7894d2aed3a9bb3a3153ecd2e5c5696c6`;
  bootstrap `c48e2c46e5b93a26a2f3c41f10e5189f81e189fa2180663f973ebcbb81aebb81`.
  JAR `4ea05b2cec716dd53980a57d0016631df3fb1af311a11a774a6a259f52e4ce5a`;
  pilot classes/resources absent. This is source proof, NOT native reachability.

### Now
- PM-F0VA-NATIVE-RECONNECT-DEPARTURE-01 IMPLEMENTING. Terra found retained old
  activeConnection plus cleared expected-loss flags at descriptor activation
  cause the synchronous reconnect-launch logout to be falsely fatal. Main
  verified relevant SessionControl and client logout branches. Outcome is exact
  predecessor identity classification, bounded to validated reconnect and
  retired at replacement bind/complete/reset; foreign losses remain fatal.
  Gate A acknowledged outcome/risks/baseline; implementation and focused
  Java/Node/docs iterations granted, stable Gate B next. Exact baseline hashes
  retained in order. r9 terminal docs guardrails34tasks18s PASS.
- Active parent `PM-F0VA-NATIVE-QUAL-01`: REVISION9_FAILED_READ_ONLY_DIAGNOSIS.
  Main inspected failed worker result77.87s and bundle
  build/frontier-v3-ci-shards/worker-3-be1f7dac-dc63-45e0-9cbb-6c730375aeeb/persistent-matrix.failure/bundle.json.
  Real release crash marker2425 at00:35:49; authenticated lifecycle1–13 proves
  prefix1–4, arm/controller fire, owned exit, expected loss, closed port and
  recovery server ready. Replacement connection failed at00:36:01:
  persistent_reconnect_lost from ConnectScreen.startConnecting logout callback.
  No recovery receipt or completed lane; graceful/benchmark did not run.
  Main verified saved00:36:07, retained world present, PIDs3578632/3580748/
 3579102/3579071 absent and all four owned ports closed. Terra diagnoses read-only.
  Source6850d4b22a021ee1e6f1b4949c8173789a97134773be2f2c0d5c8f8e8b410f71;
  builda547447715ca0a65c953990db5733df073d3d8d7dfcff6171582dd012dcd1baa;
  plan031058afaf39ef3c3df8e96b6e0ed9c38d1c3521f8e7770851e143178f98ee37.
- Historical records below preserve prior grants/evidence only; none override
  the current CLOSED grants above.
  Handoff-witness Gate C ACCEPTED. Main verified unchanged probe20fcade4,
  testfd139f09 and JAR4ea05b2c; actual290/290GameTests00:29:52.534,
  all-dimensions save00:30:01.725 and shutdown. Exact full gate71tasks/2m13s PASS.
  Server crash hooks/bootstrap/mixins absent from JAR; existing client pilot
  classes remain present (not blanket pilot exclusion). Terra owns fresh exact
  worker3 pair under build/pm-f0va-native-qual-01-r9/, ports25631/25632 and
  one visible client. First failure stops; no benchmark or source edit.
- New source order `PM-F0VA-NATIVE-HANDOFF-WITNESS-01` VERIFYING. Gate B
  ACCEPTED after main inspected Handoff-first same-lease duplicate tests and
 20/20XML19:26:50.085Z. Probe20fcade4 unchanged; final testfd139f09.
  Terra owns exact full critical command in order AC-5; source frozen, no native.
  Historical Gate A
  acknowledged exact outcome/boundaries and verified baseline; focused Java/Node
  and docs gates granted, stable Gate B next. Terra found
  real durable Handoff admission before r8 release, while probe observes only
  Prepared. Main verified typed durable Handoff, native executor branch and
  Prepared-only extraction. Outcome is pilot-only admission-complete exact
  witness capture with preserved ambiguity/negative paths; no gameplay change.
  Parent terminal documentation passed both diff checks and34-task guardrails18s.
  Initial Gate B: main verified probe20fcade4/test6b23d9ae and retained
  behavioral red18/1 -> green19/19XML. Two-type extraction looks correct;
  AC-2 needs Handoff-first/same-lease mixed ambiguity coverage. Terra continues
  focused test iteration; full/native grants remain closed.
- Active parent `PM-F0VA-NATIVE-QUAL-01`: REVISION8_FAILED_READ_ONLY_DIAGNOSIS.
  Worker result terminal failed after342.85s; no lane completed. Main inspected
  `build/pm-f0va-native-qual-01-r8/worker-3.json` and retained bundle
  `build/frontier-v3-ci-shards/worker-3-30c70c28-4a3a-4c3d-a5da-9858fecd7eb0/persistent-matrix.failure/bundle.json`.
  Actual release trace revision2424 at00:05:31 lacks crash marker; runner reports
  client exit before crash boundary. All-dimensions save00:10:14 retained.
  Main confirmed PIDs3502483/3503095/3503737 absent and all four owned
  qualification/benchmark ports closed. Terra diagnoses read-only; no retry.
  Logical plan d18a86c6e3b0b15ac813687e128cb268ef023d0a4be3a97647d92df42dd2cf4a;
  build identity b3a2e01df76bb48a64c7e1c5203166f6895f9b4101f3f5740438bd4844765448.
  Source preflight order Gate C ACCEPTED; full gate2m11s/71tasks. Main verified
 290/290GameTests23:58:41.503 and all-dimensions save23:58:50.931, unchanged
 source/test/JAR hashes and pilot exclusion. Source grants CLOSED.
 Revision8 exact worker3 pair stopped on its first failure; no benchmark.
  Main does not duplicate the investigation. No user decision currently needed.
- Gate B source reviewed: exact mixin-key lookup through shared production
  preflight path; all-five-boundary regression, absent/foreign rejection and
  unarmed no-op; classfile guard enforces exact one-target declarations.
  Terra reports17/17Java,169/169Node and34/34guardrails. Initial red was only
  missing-seam compilation; subsequent old-key mutation produced17tests/1intended
  assertion failure. Main verified exact restored hashes and noncached17/17XML
  timestamp18:56:16.682Z. Full critical completed; native revision8 granted.
  Source hash c05fb8c7192ae9fdba454b7330a57aac9272b29095429086b64823aba5872db9;
  test hash0bd83f2376615a1b4a39d7e3acffba280997d00ec1d2e0b8d9c356099f0d5555.
- Terra ruled out dot/slash lookup mismatch; main verified active Mixin source
  normalizes cache keys and records applied mixins after application. Retained
  logs cannot locate registration/selection/mapping failure. Terra now proposes
  the least invasive bounded diagnostic: stock debug/verbose/export plus
  output-only target logging before any pilot plugin. Proposal accepted;
  terminal server-only run proves actual transformation plus a preflight false
  negative. Main inspected log23:43:54 and exported bytecode's injected handler
  calling afterDurableAppend; hashes match result. All run grants now closed;
  Main verified API cause: MixinInfo.postApply records success on the mixin's
  ClassInfo, not the target's. New order corrects lookup while testing exact
  single-target mappings and preserving empty/foreign fail-closed checks.
- Evidence: `build/pm-f0va-native-mixin-diagnostic-r8/attempt-2/result.json`,
  run90d2a7b1-bc87-4981-b734-49c59002829d, unchanged r7 source/JAR.
  Saved23:44:01; main confirmed PID3450294 absent and ports25651/25652 closed.
  No client, actual armed crash or recovery was exercised by this diagnostic.
- Diagnostic preparation initially failed before server launch: output helper
  passed build profile `disposable_lite` as the fixture-profile property.
  Terra retains failure/cleanup evidence. Revision2 permits autonomous local
  output-helper/preparation corrections using existing worker3 semantics;
  the allowance remains one actual server launch, no source patch or retry.
- Revision7 source `59af02f11c96e49acafb4b66d7dc13f632aedd67d186247068619aa1f1341a4b`,
  plan `e54f3dbe84c1904a1415917897cd2003d245b4c35603abad9ee6395c852e1c79`,
  build `cbce7be4443cfb5b9e77ab1ccd8deaa73266883ae2c4db86e609b28ad27e410d`.
  Prepared root `build/pm-f0va-native-qual-01-r7/`;13lanes, exact worker3
  abrupt-release + graceful pair. Applied-mixin preflight failed before client
  launch at23:17:50; save23:17:57. Worker failed after24.5seconds, no retry.
- Revision7 bundle:
  `build/frontier-v3-ci-shards/worker-3-5c0dcf04-9d88-4023-b5fe-0d75ea84be3b/persistent-matrix.failure/bundle.json`.
  Server run `573802ce-4690-44ca-a23a-d5a7d99be6ff`, PID3383619 absent;
  main checked ports25631/25632/25641/25642 closed. ClientPid null;
  retained r7 world reported present; live service untouched.
- Revision6 reached natural HOT/release revision2425 without a crash marker.
  Its diagnostic job subject differs from canonical event settlement owner;
  accepted typed witness correction addresses this definite mismatch. Revision7
  now exposes the separate application/preflight gap; do not presume its cause.
- Evidence caveat: earlier permitted module clean removed revision5 world.
  Its complete inline log/root bundle survives. Never use module-wide clean to
  discard retained worlds again; r6 world was independently confirmed present.
- Local3world benchmark, actual4-worker/provider proof and original F0.V matrix
  remain unproved. Revision9 grant closed on first failure.

### Next
- Review Terra's reconnect causal finding and bounded correction proposal.
  Prove exact worker3 application/crash/recovery/cleanup before3world benchmark.
- Finish local F0.VA -> separately reviewed history-preserving monorepo/push ->
  provider/pinned-host proof -> original F0.V -> preserved F0.1–F0.6 and waves.
- F0 exits remain: F0.V descriptor/comparator/knowledge/custody; F0.1 partial
  work; F0.2 eligibility/knowledge/aftermath; F0.3 observer-free custody;
  F0.4 navigation/AI/cross-front authority; F0.5 confirmation/fencing;
  F0.6 calibration/switch neutrality. No timing result waives these.
- V3-AUD-052 arrival_checkpoint_one may need a fresh corrective proof after
  current blockers; no new F0.1/MAT breadth is authorized.

## Open questions
- No configured remote or actual provider/pinned-host evidence: UNCONFIRMED_EXTERNAL until the approved later migration/push/provider step. GitHub CLI use for that future CI-worker work is user-authorized; do not skip the intervening local and repository-migration gates.
- No current user decision blocks the runner order; scenario reconciliation is an engineer-owned preservation of existing recovery meaning. Future finance slice still needs the accepted-or-replaced rule for bounded interest-free company working-capital credit and missed-payment handling; no credit/debt implementation authorized yet.
- Future Create/add-on pins, rail/industrial balance, exact growth/infection/territory calibration and actor budgets remain deferred product work. Real-display, clean-room, co-op and unbriefed-player M3 gates remain human acceptance, including broader visual/tactical readability.
- Detailed historical facts and unresolved product questions were preserved verbatim in the archives below, not erased or silently closed.

## Working set
- `docs/archive/CONTINUITY_2026-09-05_native_r7.md`: verbatim ledger before this compaction; full accepted-order and failed-native history. This compact ledger alone is active.
- Active order above; `docs/engineering-agent-protocol.md`, `architecture.yml`.
- Contract/execution-semantics/implementation-plan/seamless-foundation, accelerated-verification-loop, integration-feedback-foundation, Terra brief, architecture-audit.
- Active sources: tools/frontier-v3-test-pilot/{src,test} persistent-worker-plan/persistent-matrix/lifecycle-barrier/run-f0va-persistent-matrix/run-persistent-matrix-client/run-ci-matrix-shard; NeoForge SessionControl/TestPilotClient, SessionControlTest and revision3 path17 ExpectedCrashTest.17-file scope includes the declared resource-site-harvest-f0v.json departure; accepted crash wire/controller remain reused read-only.
- `docs/archive/CONTINUITY_2026-09-05_engineer_handoff.md`: inherited pre-managed history.
- `docs/archive/CONTINUITY_2026-09-05_crash_protocol_handoff.md`: verbatim ledger before this compaction, including full product decisions and earlier managed evidence. Both are historical archives; this file alone owns active state.
