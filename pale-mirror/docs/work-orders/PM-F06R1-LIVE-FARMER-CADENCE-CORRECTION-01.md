# PM-F06R1-LIVE-FARMER-CADENCE-CORRECTION-01: eliminate stepwise farmer travel

Revision: 3. Parent slice: reopened F0.6 V3-AUD-056. Risk: critical-code and
test-server delivery. Status: USER_RETEST_FAILED on the later F0.6R2 live
artifact; superseded by
[`PM-F06R3-SHARED-HOT-LOCOMOTION-DUTY-CYCLE-01`](PM-F06R3-SHARED-HOT-LOCOMOTION-DUTY-CYCLE-01.md).
PM / architect: Sol. Autonomous senior tech lead and sole coder/operator: Terra,
gpt-5.6-terra, reasoning high.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md), revision
`2026-09-11-PM-TL`.

## Product discrepancy and outcome

The user observed the exact freshly deployed F0.6 farmer move one cell at a
time with visible pauses. PM independently verified that the server was running
accepted commit `2190797d`, JAR SHA-256 `171f8e7d...abd9fc` and new world
`frontier-v3-f06-r1`. Fresh runtime evidence places the user in that world and
records real `resource_site_harvest_handoff`/`resource_site_harvest_hot` leases
for `resident:3-31` and `resident:4-31` after the user's fast-forward. This is a
live product contradiction of V3-AUD-056, not old-client, old-world or ambient-
Villager evidence.

Correct the reusable HOT duration-work movement boundary so an unobstructed
farmer visibly travels through consecutive field-route cells as one continuous,
believable movement episode. A legitimate labor dwell may occur only at its
exact semantic work cell and must read as work on that cell; a multi-second
stationary gap between one-cell travel bursts may not masquerade as continuous
locomotion.

Preserve all accepted semantics: the exact farmer, job, field, topology and
canonical cursor remain authoritative; HOT and COLD advance the same process;
canonical labor/economy timing is not accelerated; no second clock, route,
cursor, wandering goal or outcome authority is introduced; progress commits
only from ordinary observed Minecraft arrival; obstruction, death and demand
loss remain typed and causal.

## Acceptance boundary

The earlier provider-speed assertion is insufficient because it can measure
only the nonzero-motion part of one short edge and ignore the full duty cycle.
Acceptance now requires all of the following:

- an actual production harvest composition, naturally HOT, retains one exact
  farmer across at least five consecutive intended route/work transitions;
- tick-correlated physical samples cover the complete interval, including
  stationary samples, and distinguish `TRAVELLING` from an intentional semantic
  work dwell. While unobstructed travel is pending, the actor does not exhibit
  repeated multi-second stationary plateaus between adjacent-cell bursts;
- observed movement remains normal-speed and collision-respecting, while the
  canonical harvest cadence, crop order, progress and output remain unchanged;
- a blocked next body/cell, worker loss and demand release still affect the same
  retained job and never cause hidden wandering, teleport, cursor advance or
  COLD duplication;
- the focused production Scene evidence covers the entire multi-edge duty cycle,
  not merely one edge or a derived provider callback. Use deterministic server/
  simulation ticks and observed positions rather than wall-clock sleeps or a
  transient exact-value oracle;
- one bounded disposable native run proves the same full-window behavior in a
  real world with ordinary demand and returns its position/phase/crop trace.
  Frames may support identity and work-cell context but cannot prove motion;
- one final critical integration/package gate applies to the coherent
  correction. Reuse all unaffected F0.6 calibration, pressure, recovery and
  infrastructure evidence; no full native matrix, JFR campaign or repeated
  foundation proof is authorized;
- after verification, publish/install the exact correction candidate to the
  disposable live server and pass the normal release preflight and fresh
  startup verifier. Preserve the current F0.6 world and use an ordinary
  same-world upgrade if format semantics are unchanged; if a necessary format
  cut occurs, preserve it as rollback and create a new explicitly identified
  world. Do not deploy MAT-004.

Automated evidence can restore M2 candidate status, but the player-facing clause
remains open until the user observes the corrected deployed farmer. Do not call
the result human/M3 acceptance.

## Context, ownership and authority

- Accepted F0.6 source identity is commit
  `2190797dcbd9d747df34a8e2cf12ed646eb1b195`, tree
  `923954b31721e8567d68bb2957c6f18554655546`, schema/envelope `150/60`.
  Start the correction from that immutable identity in a separate clean
  worktree/branch. Do not reset, switch, clean or merge the MAT-004 worktree.
- MAT-004 is safely suspended at clean WIP commit `c193b152`, tree `0c48b9fc`.
  Its last focused Scene run finished normally with 74 tests and one retained
  fixture failure; no MAT-004 Gradle, Minecraft or native process remains.
- The live service remains available during development and is mutated only
  after the correction candidate passes its proportional gates. Preserve its
  current world, pack history, retained evidence and unrelated services/data.

Terra owns cause analysis, technical design, implementation, test methodology,
review, proportional retries, packaging and authorized test-server delivery
through the complete outcome. This order grants correction-related source,
test/harness, isolated evidence, hosted artifact and disposable live-service
mutation. It does not grant a public release, remote push, broad cleanup,
natural-terrain campaign, unrelated gameplay change, production cutover or v2
removal. A genuine change to product meaning or persistence promises returns
one coherent `NEEDS_DECISION`; ordinary implementation, test and deployment
problems remain Terra's responsibility.

## Terminal packet and continuation

Return one terminal packet mapping the criteria above to exact candidate
commit/tree/schema/envelope/JAR, full-window Scene/native traces, crop/economy
invariants, negative/release results, final critical gate, technical self-review,
live hosted/installed identity, service/world/verifier state, repository dirt
and owned-process state. State precisely what the user should observe and where.

After PM verifies the automated and operational packet, request the user's live
retest. Only a successful live observation recloses V3-AUD-056/F0.6. Then resume
MAT-004 autonomously from preserved `c193b152`; no correction evidence promotes
MAT-004 or M3.

## PM review of candidate 1

Candidate `cdc47e1183bc956aaf582bd120f3e7a80b9dbf6f`, tree
`938964fcb6ed2c1244858627bf2b215f03763f39`, is operationally healthy and is
currently installed in the preserved `frontier-v3-f06-r1` world. Its packaged,
hosted and installed JARs share SHA-256
`b9236e5cbc2a50dd51a5c881910790c7f7fb8af56b5d6520ba62f59e91a6c523`.
The focused Scene lane passes73/73, the final critical gate passes320/320
GameTests plus1,360 zero-failure/error XML tests, and the native carrier proves
the exact worker/job, crop1→6, physical crop effect, release and return. The
fresh live-server verifier also passes.

This candidate is not accepted for the existing product criterion. PM sorted
the carrier's 189 retained five-tick physical samples by server tick. Across an
unobstructed straight-axis net displacement of approximately5.295 blocks, the
actor travelled approximately18.154 blocks and reversed direction64 times,
repeatedly oscillating around each retained cell while waiting for the next
semantic due turn. A maximum identical-pose interval of five ticks therefore
proves only non-stationarity; it does not prove believable forward travel.

Replacing a multi-second stop with filler pacing over the same two-support edge
does not satisfy “continuous, believable movement through consecutive field-
route cells.” The same retained evidence also does not identify those intervals
as a visible semantic work dwell, so they cannot be reclassified as harvesting.
The order remains `EXECUTING`. The next candidate must distinguish useful net
route advance from reversal/filler motion and make any intentional cadence wait
read as work at its exact station. Terra owns the technical resolution and the
smallest faithful evidence; unaffected native/gate/deployment facts are retained
and no broad rerun is requested.

## PM review of candidate 2

Candidate `32fb402f29d07f3fda043aeb731e9901c6ac060f`, tree
`576d249d0c8b948529d1bca110d917379976ba5a`, is clean and descends from the
accepted F0.6 source. Schema/envelope remain `150/60`. Its packaged JAR has
SHA-256 `5065cb4286f8835fbc8e0746ab3a6b4120c40b0651aff2c143958ea6d9f9f32e`
and SHA-512
`087ddcd41fbd410360943344451a141d5349da4fa6de509c8ebfa70c7a50afd35e0a34c7a80c9ee65fa033929d746c7f338243e45738df53bcaf4cc08154b1cb`.

The retained native receipt
`build/f06r1-native/20260912T182048Z-overlay-observed/resource-site-harvest-progress-carrier.json`
has SHA-256 `102244459e14d7c2815ffd5a4e5b0e61e2c711611e7fb844acbcb785995f6e11`;
its manifest and PMV3 trace have SHA-256 `ea00e0fc...78e6e` and
`b78fa3ab...1e153`. It binds a clean native source `5d48515d`, the exact
`resident:1-31` and harvest job, crop progress1->6, the physical crop effect,
release and same-identity return. Across175 complete-duty samples and seven
cells, useful net travel is5.0 blocks against5.934 total, replacing candidate
1's 18.154/5.295 filler path. The carrier classifies150 stationary samples as
visible station work and limits a silent stationary run to two samples.

PM independently inspected the primary samples rather than promoting that
classification to human acceptance. They expose160 client break-overlay
samples, but zero separate Villager `workAnimation` or `workFacingStation`
samples. This is sufficient automated evidence that the retained worker is no
longer filler-pacing and that its cadence wait has a visible work-cell effect;
only direct player observation can establish whether that combination actually
reads as natural harvesting. The native run predates the final commit only by
the bounded cargo GameTest selector/contract change; no farmer/runtime behavior
changed, so its scoped evidence is retained rather than ceremonially rerun.

The focused Scene lane passes73/73, the isolated cargo discriminator passes9/9,
and the one final critical gate records320/320 GameTests with saved shutdown
plus63 JUnit suites/302 tests with zero failure/error. The built, hosted, HTTP-
served and installed JAR identities match. The read-only deployment verifier
returns `OK` for active `far-frontier-v3-live.service`, PID4090210/Java4090249,
port25565 and the preserved `frontier-v3-f06-r1` world; fresh startup contains
both the dedicated-server ready record and Frontier v3 runtime start with no new
quarantine. Port25604 and all task-private native/Xvfb processes are absent.

Automated and operational acceptance is therefore sufficient for the required
live retest, but not for human/M3 acceptance or F0.6 reclosure. The user should
observe forward travel through consecutive field cells and a visible crop-break
work overlay during longer waits at the current station. A successful direct
observation recloses V3-AUD-056/F0.6 and releases preserved MAT-004; any remaining
stepwise or unintelligible behavior returns this same order to `EXECUTING`.

The user explicitly deferred that live observation on 2026-09-13 and directed
development to continue. This removes the retest as a sequencing barrier for
MAT-004 only; it does not convert automation into human/M3 acceptance, close the
open player-facing observation, authorize production cutover/v2 removal or let
later work overwrite the deployed candidate/evidence needed for the retest.

## Later F0.6R2 live result

The deferred human question is now answered negatively. On 2026-09-13 the user
observed the same one-cell/several-second movement pattern in managed mobs,
including a resident, on the exact operational F0.6R2 deployment `38890fa3` /
JAR `dd0ec21c...ffe3`. PM matched the live service/world/artifact and the same
session's exact HOT resident lease. Candidate 2's crop-overlay classification
therefore did not establish believable locomotion in the real full duty cycle.
V3-AUD-056 is reopened; the stronger shared-provider correction and live retest
are owned by F0.6R3. All sound identity, custody, route, crop, recovery and
unrelated F0.6R2 evidence remains reusable.
