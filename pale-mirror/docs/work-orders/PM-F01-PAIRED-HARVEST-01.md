# PM-F01-PAIRED-HARVEST-01: close one-process/two-driver harvest continuity

Status: accepted. Specification revision: 5. Parent slice: F0.1. Risk:
critical-code. Engineer: supervising root. Executor: `/root/terra_f0vb_r5`,
`gpt-5.6-terra`, reasoning `high`. Active phase and acceptance owner:
`CONTINUITY.md`.

## Baseline and prerequisites

- The adopted monorepo `/home/rd/proj/pale-mirror-monorepo` is the sole writable
  checkout. Baseline `main` and `origin/main` are
  `f2ea3108581911205748b669ab6495761310f6d0`; accepted F0.V is
  `089495ed630f777f63e3b8952e500ea548bc814c` and accepted F0.VB is the
  baseline commit.
- The previous F0.VB assignment is terminal. Its accepted run `34095008014`
  proves the reusable four-slot infrastructure, and its terminal cleanup
  reported zero registered task runners, zero task-root processes and no local
  runner credentials. Revalidate exact task ownership before provisioning a
  new run; do not treat F0.VB as gameplay evidence.
- At activation the only dirty tracked content is engineer-owned normative
  documentation: root `AGENTS.md`, `pale-mirror/CONTINUITY.md`, the F0.V/F0.VB
  orders, architecture audit, integration-feedback foundation,
  materialization inventory and this order. Preserve those bytes verbatim and
  unstaged. Terra proposes any requirement correction to the engineer rather
  than editing these files.
- Read root and scoped `AGENTS.md`, `CONTINUITY.md`, this order, the engineering
  protocol, Frontier v3 contract, execution semantics, seamless foundation,
  implementation plan, architecture map/audit, materialization completeness
  inventory, resource-site contract and the mandatory applicable project
  skills before implementation.

## Outcome and exclusions

Close V3-AUD-043 through the MAT-001 reference boundary: one canonical harvest
process starts and advances without a player or loaded field/depot; its paired
COLD and HOT drivers advance the same retained unfinished traversal work; HOT
acquires the current process state and release returns the exact next step to
COLD atomically. Repeated leave/return cycles and restart preserve the same
farmer, stage, cursor and engine-owned continuation without replay, reset,
duplicate authority or demand-created work.

This order is deliberately traversal-only. It must preserve but not enable the
irreversible crop transition, output-stack creation or effect/observation crash
windows assigned to F0.2. It does not implement replica/custody separation,
deferred aftermath, fungible resource migration, observer-independent
container activity, new process/scene families, MAT-004 patrols, natural-terrain
hardening, gameplay balance, deployment, v2 removal or human M3 acceptance.

## Contract and transition

1. `ResourceSiteHarvestJob` and its owning canonical resource-site state remain
   the only harvest purpose, participant, reservation, topology, progress and
   outcome authority. The scene lease is a bounded execution capability and
   owns no independent cursor, timer, stage, outcome or recovery history.
2. Domain causes create and schedule viable harvest work independently of
   player/chunk demand. With no physical lease, the registered COLD driver
   advances bounded retained traversal work through the ordinary due-action
   engine. It neither reads unloaded Minecraft state nor waits for a visitor.
3. HOT admission is a compare-and-set over the exact process version, farmer,
   site, topology/cursor, authority and continuation binding. A typed observed
   arrival advances that same process cursor; COLD cannot advance it while the
   physical lease owns execution.
4. Checkpoint, release and reacquisition are atomic process/lease transitions.
   A no-work release preserves the exact current `ScheduledAction`; a semantic
   traversal checkpoint applies the same process-owned cadence transition as
   COLD. No deadline is copied into model/lease state or reconstructed from
   observation, disconnect or release time.
5. For this traversal-only reference, currently applicable retained unfinished
   work consists of the harvest phase/job, exact worker, topology/cursor/body,
   process revision and sole scheduled continuation. Any already-applicable
   stage or timer must remain exact through every hand-off. Do not invent a
   second labor clock merely for testing; completed/pending crop work and output
   remain zero/uncreated in both modes.
6. Neutral acquisition, progress, release and restart retain the harvest
   `PhysicalIntent` kind, `PREPARED` status and empty observation binding.
   Only the later registered F0.2 effect owner may cross to `RUNNING`.
7. Repeated HOT/COLD switches preserve monotonic legal progress and the exact
   next unfinished step. Demand may select presentation/executor but cannot
   start, accelerate, reset or resurrect the operation. Returning displays the
   later current state, never a reenactment or replacement farmer. In
   particular, physical arrival before the retained `ScheduledAction` is due
   cannot consume that action, advance the canonical cursor or install its
   successor early; the HOT semantic checkpoint becomes canonical no earlier
   than the same due edge available to COLD.
8. Ordinary worker death or a physical obstruction at the retained next step
   becomes an attributable typed disruption of this same job. It cannot select
   a replacement actor/route, silently mutate the fixture, mint output, create
   generic quarantine or leave a second driver active.
9. Graceful and abrupt filesystem recovery preserve exact confirmed traversal
   progress and exclusive execution custody. Applicable crash boundaries are
   lease acquisition, HOT checkpoint and release-before-COLD; effect-visible
   and typed-effect-observation windows remain declared F0.2 work. Recovery
   cannot duplicate actor, lease, schedule, process progress or effect.
10. The exact comparison boundary includes process/actor/site identity,
    reservations/custody, phase, topology/cursor/body, continuation identity
    and ordering, lease/authority state, intent kind/status/observation and zero
    crop/output consequence. Physical pose and elapsed local path time need not
    be byte-identical, but any difference must remain within the already
    declared bounded traversal semantics.

## Write scope

Terra owns diagnosis, local design, algorithms, helpers, file choices and
focused-through-full correction cycles inside the existing pure Frontier v3
harvest/process-driver/schedule/codec owners, NeoForge harvest scene and typed
observation/recovery owners, declarative test-pilot contract/runner and their
tests. Expected existing names are navigation hints, not a prescribed patch.

Terra may make the smallest necessary checked-in CI/test-harness change to run
the F0.1 matrix through the accepted four-slot pipeline. It must not edit the
engineer-owned normative Markdown/declarative architecture files, widen a debt
ceiling, alter unrelated process semantics, resume deferred terrain WIP or
weaken an existing assertion/timeout. A contract conflict, required format
change beyond this harvest owner, or need for F0.2 behavior is an
`ARCHITECTURE`/`AUTHORITY` stop rather than an implicit expansion.

## Acceptance matrix

| ID | Required outcome/invariant | Focused positive proof | Negative/recovery proof | Physical/final proof |
| --- | --- | --- | --- | --- |
| AC-1 | Every enforced duration process has exactly one declared COLD and HOT driver, schedule owner and codecs; harvest is registered once | current production composition succeeds | missing, duplicate, wrong-kind, stale-version and one-driver compositions fail closed | N/A |
| AC-2 | Harvest starts and advances retained traversal without player/chunk demand | pure never-loaded run crosses at least two legal scheduled steps with the same job/farmer and one continuation | no crop/output/effect transition; no lease or unloaded-world query | fresh `never_loaded` native lane or equally strong naturally unloaded proof |
| AC-3 | Arrival at two distinct progress stages attaches HOT to the current process cursor and only observed arrival advances it | two independently declared arrival stages retain exact identities and schedule binding | stale/bypassed cursor, wrong actor/body/topology or unobserved arrival changes nothing | fresh arrival-stage lanes show the exact same farmer/body authority and legal checkpoint |
| AC-4 | At least two complete HOT→COLD→HOT hand-off cycles retain the exact unfinished step, stage/timer and continuation | pure/NeoForge repeated-hand-off state and codec coverage | concurrent COLD/HOT advance, duplicate schedule/lease, reset, replay and release-time deadline reconstruction fail | native unload/return shows later current state and the same deterministic farmer identity without disappearance |
| AC-5 | Ordinary intervention changes or blocks this same process without hidden replacement or global corruption | owning reducer handles the exact disruption | worker death or retained-next-step obstruction cannot substitute actor/route, continue both drivers or create output | one ordinary player-caused intervention reaches an attributable terminal/blocked domain assertion |
| AC-6 | Graceful and abrupt recovery preserve exact partial traversal and exclusive ownership | snapshot/WAL/codec recovery retains process plus continuation | three applicable lease/checkpoint/release crash boundaries reject duplication, stale authority and lost progress; two effect windows stay deferred | fresh graceful and abrupt filesystem lanes with exact server/save/JVM/port lifecycle evidence |
| AC-7 | The result is attributable, packaged and bounded to F0.1 | focused Java/Node tests, guardrails, check, Scene GameTests, build and packaged-JAR checks pass | fixture/fault authority remains absent from production JAR; changed semantic evidence cannot reuse stale cache | one complete fresh native matrix uses four simultaneous isolated CI slots and merges fail closed; no task-owned runner/process/credential remains |

The terminal evidence must distinguish prior F0.V/F0.VB evidence from fresh
F0.1 proof and map every F0.1 bullet in execution semantics, seamless
foundation, implementation plan and V3-AUD-043 to an exact result. Report
source/spec/build/JAR identities, commands and results, scenario/run/world/
client/server/job/runner identities, all retained failures and corrections,
and the final local/remote dirty state. Report M0, M1, M2 and M3 separately;
this order cannot claim M3.

## Verification and external authority

- Use focused pure/codec/registry tests and the smallest applicable NeoForge
  Scene GameTest slice during development. At the coherent terminal candidate,
  run the complete critical-code gate required by `AGENTS.md`, applicable Node
  scenario/merge tests, and any module-specific gate selected by the release
  matrix. The mandatory authentic supplied-reference gate remains required for
  pre-commit acceptance unless its exact unchanged-input evidence is validly
  reusable under `AGENTS.md`.
- Any newly started complete native matrix must use the accepted F0.VB path
  with four simultaneously available isolated slots and one immutable
  assignment. A focused native reproduction may remain proportionate, but it
  cannot substitute for the terminal four-worker matrix. Each isolated lane
  owns one client, world, port, display, process and evidence namespace; no
  lane consumes another lane's state.
- A failed complete matrix may be completed by a focused replacement lane
  without repeating already-valid lanes when all runtime, contract, packaged
  JAR and launch identities remain exact. The final aggregate must retain the
  original run/lane provenance and fail closed on any identity drift. A
  proof-tool-only correction does not invalidate already collected physical
  evidence when its non-runtime scope and the unchanged runtime identities are
  independently demonstrated. Any runtime, scenario-contract, JAR or launch
  change requires a new complete four-slot matrix.
- Terra is authorized to use `gh`, the retained clean F0.VB runner
  installations under `/home/rd/proj/pm-f0vb-native-pipeline`, and a bounded
  task root under `/home/rd/proj/pm-f01-paired-harvest`; register/start four
  ephemeral task-owned runners, dispatch and observe the exact reviewable
  commit, download evidence, then stop and deregister them. Tokens/credentials
  never enter logs or artifacts. Unrelated runners and services are read-only.
- Terra may commit coherent in-scope implementation/test/CI changes and push
  only non-force fast-forwards to `origin/main` so GitHub can execute the exact
  candidate. Stage explicit reviewed paths and exclude all engineer-owned docs.
  No force push, ref deletion, destructive cleanup, deployment or original-
  checkout mutation is authorized.

## Review and execution permissions

Gate A grants the complete bounded outcome now: autonomous diagnosis, design,
implementation, focused and full gates, ordinary corrections, reviewable
commits/pushes and one terminal four-worker native matrix. There is no
intermediate permission stop or executor heartbeat. Terra reports only a real
`ARCHITECTURE`, `AUTHORITY`, `RISK` or `IMPASSE` boundary, or one coherent Gate C
packet.

While this order is `EXECUTING`, the engineer performs exactly one bounded
`LIVENESS` check after each complete ten-minute interval without an executor
event, never earlier. It is limited to collaboration state, exact task-owned
process/job liveness and one bounded progress marker; it never inspects source,
diffs or implementation choices. Healthy evidence causes no message or
direction to Terra. Final review examines the stable result and selected
independent evidence, not every implementation iteration.

## Stop conditions

Stop before expanding for conflicting ownership, another writer, changed or
unknown baseline/WIP, need for a new public/persistent contract, unavoidable
F0.2 consequence, natural-terrain dependency, secret exposure, unrelated
service mutation, missing external authority or repeated unexplained failure.
Preserve all WIP/evidence and return the exact unmet invariant. Do not weaken
the contract, broaden a timeout, retry blindly or self-start F0.2.

## Review record

Revision 1 activated after independent F0.V and F0.VB acceptance. No F0.1
implementation or evidence is accepted yet.

Revision 2 records independent final review of published candidate
`0c7e777a7d2168ed31975a2758d0e8decfbdbff9` and GitHub run `34103002882`.
Identity, scope isolation, actual four-job concurrency, all eight lane results,
same source/JAR evidence and terminal runner cleanup are valid, but the slice is
not accepted:

- the production physical-command path still accepts a harvest intent
  `PREPARED -> RUNNING` transition while the F0.2 effect capability is closed;
  the generic SDK harvest descriptor exercises that accepted transition, so
  the current guard on confirmation alone does not establish contract item 6;
- both arrival lanes finish at the COLD-established cursor from which they
  acquired HOT authority (`16` and `19` respectively). Their cross-run
  distinctness proves two admission stages, but neither lane proves a typed
  observed physical arrival advancing the shared cursor as required by AC-3;
- the generic SDK performs two HOT sessions with only one intervening
  release/COLD/reacquisition, and the native unload/return lane independently
  performs one such cycle. Neither proves two complete
  `HOT -> COLD -> HOT` cycles on one retained job as required by AC-4;
- the CI merger checks bundle names, source commit and lane success, but does
  not enforce the cross-worker arrival relation or equality of complete
  source-content, contract/build and packaged-JAR identities. The reviewed run
  happens to have matching source content and JARs, but the gate can still pass
  a semantically incomplete or divergent matrix, so AC-7 is not fail-closed.

The correction outcome is invariant-level: close the F0.2 transition boundary,
prove real observed HOT progress at both declared stages, prove two complete
same-job hand-off cycles, and make the terminal merge reject missing/divergent
cross-worker semantic and build evidence. Terra owns the implementation and
minimal proportional rerun design. Preserve the valid run and failure bundles;
do not repeat unrelated gates, enable crop/output effects, expand terrain or
start F0.2.

Revision 3 records independent final review of published candidate
`a99f937ebe9bdd6be62d2987f2163b1a463d0ff0` and successful GitHub run
`34115726026`. The four isolated jobs, exact shared source/contract/JAR
identity, two same-job hand-off cycles, actual observed HOT advances and
terminal cleanup are valid evidence, but F0.1 remains unaccepted:

- both arrival proofs gain canonical time from presentation demand. In
  `arrival_checkpoint_one`, the process advances cursor `16 -> 18` at
  canonical instant `24468` after retaining its next action at `dueAt=24502`
  at instant `24448`; it therefore consumes two cadence edges before the first
  one is eligible. `arrival_checkpoint_two` likewise advances `18 -> 19` at
  instant `24845` while its retained action is due at `24902`. Production code
  and its focused test explicitly admit consumption before `dueAt`. Anchoring
  the successor to the old deadline prevents long-term clock drift but does
  not remove the immediate HOT advantage or satisfy contract item 7;
- the F0.2 guard throws an invariant exception for ordinary forbidden
  `RUNNING`/`CONFIRMED` commands. The command planner converts only ordinary
  validation failures to a normal rejection, while the engine quarantines on
  this exception. The SDK helper checks only `CommandResult.Rejected` and
  unchanged canonical state, then forks from the checkpoint, so its green test
  does not prove that the live engine remains `ACTIVE`. A forbidden but
  well-formed pre-F0.2 command must reject locally, retain the exact canonical
  state and leave the same engine healthy for a subsequent independent valid
  command;
- the merger requires individually advancing, distinct arrival records for
  one job/path, but it can accept reversed or disconnected stage ranges. The
  aggregate must fail closed unless the declared early and later stages form
  an ordered non-overlapping relation on the same retained job, with the later
  baseline no earlier than the completed early checkpoint.

The correction outcome remains invariant-level: preserve the exact one-action
cadence with no pre-due canonical HOT advance; reject the closed F0.2
transition without quarantining or restart-based test escape; and make the
cross-worker arrival relation ordered and fail-closed. Terra owns diagnosis,
design and the smallest coherent implementation plus proportional focused and
complete four-worker replacement evidence. Preserve all valid prior bundles;
do not enable effects, expand terrain, deploy or start F0.2.

Revision 4 records independent final review of published candidate
`e36103956fe8ba75abde624c09b6f2e453f2e189` and failed-closed GitHub run
`34120973516`. The runtime correction is provisionally valid within its exact
tested boundary, but Gate C remains open:

- both arrival lanes now retain their cursor before `dueAt` and advance only
  after the shared due edge. Worker 0 remains at cursor 18 through instant
  24898 against `dueAt=24902`, then reaches 19 at instant24908. Worker 1
  remains at cursor 21 through instant25350 against `dueAt=25502`, then reaches
  22 at instant25616. This removes the observed presentation-speed advantage;
- the focused SDK proof rejects both closed-F0.2 transitions, observes the
  same engine still `ACTIVE`, and then advances an independent valid COLD edge
  on that same engine. The implementation returns the unavailable transition
  through the ordinary validation-rejection boundary rather than invariant
  quarantine;
- local Node15/15, focused Java/NeoForge, complete critical gate, packaged JAR
  and292/292 GameTests passed. Workers0,1 and3 passed, as did worker2's
  `player_intervention` lane, with exact runtime/JAR/contract identity;
- worker2's `graceful_restart` reached an ordinary stop and `Saving worlds`
  but emitted neither `durable_server_save` nor a terminal save/JVM receipt
  inside the unchanged90-second lifecycle bound. A second RCON stop was still
  answered by its listener while the server thread remained inside shutdown.
  The retained aggregate failure bundle omits the exact server log, thread
  state and retained world, so this remains an unclassified product/runtime
  save stall rather than an allowed flaky retry;
- the corrected merger over-constrains the normative arrival relation by
  requiring `later.before == early.after`. The actual independent stages are
  `18 -> 19` and `21 -> 22`. The contract requires the fixed early/later stage
  order on the same job/path with individually observed advances and a
  non-overlapping monotonic relation: `later.before >= early.after`. It does
  not require adjacent cursors across independent worlds. Do not alter runtime
  progress or scenario timing merely to manufacture adjacency.

The revision-4 outcome is bounded to two items. First, align the proof merger
and its negative tests with the ordered non-overlap rule while retaining
fail-closed rejection of reversed, overlapping, same-stage, wrong-job/path and
non-advancing evidence. Second, classify and close the one graceful lifecycle
gap without raising the timeout or blindly retrying: a focused exact-runtime
reproduction must retain enough exact server/process/save evidence to diagnose
any recurrence. If it succeeds and runtime, contract, JAR and launch bytes are
unchanged, combine it with the valid `34120973516` lanes through a
provenance-preserving fail-closed aggregate; do not repeat the complete matrix
solely for CI topology. If the stall recurs, or any runtime/contract/JAR/launch
change is necessary, return the evidence or run the newly applicable complete
four-slot matrix respectively. Crop/output effects, terrain, deployment and
F0.2 remain excluded.

Revision 5 records independent final review of published candidate
`3d46ec2634300614246bf6df4839748594539665` and successful GitHub run
`34124239973`. The functional and native F0.1 evidence is accepted pending one
test-harness failure-path correction: all four workers and the fail-closed
merge are green; source content `da98b350...`, contract `95ab0e4a...`, packaged
JAR `9ddaff41...` and launch identities match across workers; the aggregate
retains ordered arrival stages `16 -> 17` and `19 -> 20`; graceful save closes
in24.047seconds under the unchanged90-second bound; and independent cleanup
finds zero remote runners, credentials, task processes or declared listeners.
Independent focused Node verification passes17/17.

The newly added graceful-failure forensics is not yet reliable. The outer
`finally` awaits `stopServerForCleanup()` before `writeFailureBundle()`, while
that cleanup helper deliberately rethrows the graceful-stop failure after
capturing process/world metadata. On the exact recurring save-stall path the
throw exits the `finally` early, so the original failure bundle can again omit
the captured `gracefulShutdown`, server log and retained-world evidence. The
green native result does not exercise or prove this negative path.

The revision-5 outcome is therefore one proof-harness correction only: make
the exact-child cleanup and original failure preservation complete without
allowing either cleanup failure to bypass terminal failure-bundle emission,
and add a deterministic Node regression that forces the relevant graceful
stop/cleanup failure ordering and proves retained forensic fields plus no
owned process/port leak. Do not change the90-second bound, runtime, scenario
contract, packaged JAR or launch inputs. Focused Node tests, `guardrails
check`, unchanged runtime identities and clean task ownership are sufficient;
the accepted `34124239973` native matrix must not be repeated for this
proof-tool-only correction. Crop/output effects, terrain, deployment and F0.2
remain excluded.

Final acceptance: published proof-harness commit
`8217f246079acc9220279ce66386b687c4a7441c` has parent `3d46ec26` and changes
only `run-isolated-scenario.mjs`, the extracted failure-finalization helper and
its deterministic Node regression. The regression forces an original causal
failure followed by graceful cleanup failure over a real child TCP listener,
then proves that the original failure, captured graceful process/world state,
cleanup failure, exited PID and closed port all survive in the emitted bundle.
Independent verification passes the selected Node suite18/18 and `./gradlew
guardrails check --no-daemon` in17seconds. Runtime source, scenario contract,
packaged-JAR and launch inputs are byte-unchanged from the accepted native
identity; therefore successful four-worker run `34124239973` remains valid and
no replacement native run is required. Remote task-runner inventory, local
task credentials, task processes and declared listeners are zero. AC-1 through
AC-7 are accepted for the traversal-only F0.1 boundary. Combined with the
still-applicable earlier HOT harvest evidence, this recloses MAT-001 at M2;
crop/output realization, F0.2 custody/aftermath, M3 comprehension, natural
terrain, deployment and product acceptance remain open.
