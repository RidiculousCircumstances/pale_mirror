# PM-F02C-PLAYER-INGRESS-FIREWALL-33: keep actor custody firewall off players

> Historical execution record. Current authority is
> [order35 revision2](PM-F02C-PILOT-COMMAND-GRAMMAR-35.md) and the
> [PM/TL protocol](../engineering-agent-protocol.md). Preserve evidence and
> product guarantees; old microtask stops, design prescriptions and permission
> checkpoints do not govern resumed work.

Revision: 1. Parent slice: F0.2C. Risk: critical-code/intercomponent ingress.
Status: ACCEPTED_PRODUCT_NATIVE_CLIENT_BLOCKED. Engineer: Sol (`gpt-5.6-sol`). Executor:
`/root/f02c_projection_snapshot30_correction`, `gpt-5.6-terra`, reasoning
`high`. Workflow: [unified protocol](../engineering-agent-protocol.md),
revision 2026-09-11. Canonical governance:
`/home/rd/proj/pm-governance/pale-mirror`.

## Outcome and scope

The source-graybox actor custody firewall must never cancel a non-`Mob` entity,
especially a `ServerPlayer`, while retaining fail-closed rejection of duplicate
or unverified mob bodies. This restores ordinary player entry into the physical
graybox and lets the accepted native demand handshake observe authentic
destination state.

This order fixes one production join-composition defect. It does not suppress
the vanilla exception, manipulate Minecraft player/chunk tracking, force-load
chunks, add tickets, weaken managed-actor identity, change scene demand or
admission, alter coordinates/timeouts, or manufacture a successful receipt.
Order32's handshake and diagnostic semantics are frozen. F0.2C remains open
until the native HOT/restart/CLOSED/next-COLD lifecycle is actually observed.

## Baseline and continuation

- Baseline is clean commit `182c7efd43ba303fb8bf4d0ca48a205e499bd5af`,
  tree `78f2ba400a9ed538cc199fd64505f798ee7c6cd3`, in
  `/home/rd/proj/pm-f02c-projection-provider-snapshot-30`, branch
  `terra/f02c-projection-provider-snapshot-30`. The previous writer returned
  from read-only diagnosis without edits, tests or processes.
- Canonical governance is this repository and its `CONTINUITY.md`; workflow is
  the unified protocol revision 2026-09-11. Stale worktree copies grant no
  authority.
- Reuse order32's focused Java 41/41 and Node 7/7 method receipt, green critical
  envelope (304/304), JAR SHA-256 `55c2e520...39f5d`, and terminal native bundle
  `3e8f757a...65768`. Unchanged handshake behavior is not re-proved separately.
- The launched carrier teleported the player from overworld into graybox.
  `ServerLevel.addPlayer` posted `EntityJoinLevelEvent`; the first subsequent
  movement packet then failed in `DistanceManager.removePlayer` because
  destination player registration had not completed. No `ADMITTED` receipt or
  scene transition occurred.
- Engineer and independent source tracing falsified a transport/timing-only
  explanation. The exact production path is `PaleMirrorEvents.onEntityJoin ->
  observeSourceJoin -> SourceGrayboxEntityAdmission.rejects(level, entity,
  proof)`. This proof-aware overload delegates directly to
  `rejectsSourceMob(...)` without the `entity instanceof Mob` guard present in
  the older resource-key overload. For an ordinary player in the source
  dimension, `NOT_MANAGED/false` therefore returns true and cancels the join.
  NeoForge then returns before destination entity-manager/chunk-map registration
  while `ServerPlayer.changeDimension` continues, explaining the exact NPE.
- Terra's read-only diagnosis proposed waiting for source tracking readiness,
  but it relied on the wrong overload and is rejected. The player is ordinarily
  registered in the source world already; waiting cannot make a canceled
  destination join legal and would hide the production violation in harness.

## Owners and invariants

`PaleMirrorEvents.onEntityJoin` is the ordinary NeoForge entrypoint.
`FrontierV3ServerLifecycle.observeSourceJoin` owns exact ambient/scene carrier
observation and its immutable proof. `SourceGrayboxEntityAdmission` owns the
source-dimension custody decision. Minecraft owns player dimension transfer,
entity-manager indexing, chunk-map player registration and tickets.

The firewall's documented domain is every non-player living actor represented
in the source dimension. Entity kind is therefore a prerequisite to every
duplicate/unverified-body rejection: `DUPLICATE_UNINDEXED` describes an actor
body and must not acquire authority over a non-`Mob`. For mobs, existing exact
identity behavior remains intact: duplicate unindexed bodies fail closed;
unverified unmanaged source mobs fail closed; verified retained/current ambient
or scene carriers continue through their existing paths.

Terra owns the smallest coherent design, implementation and ordinary
corrections. Expected impact is the admission composition and focused tests;
touch the event/lifecycle owner only if required to preserve one connected exact
decision path. Hard exclusions are Minecraft/NeoForge internals, schema,
canonical state, projection/provider/scene algorithms, pilot coordinates,
native runner timing and ticket/chunk machinery. Return `ARCHITECTURE` if the
invariant cannot be restored without one of those changes.

## Acceptance and verification envelope

| Product claim / plausible defect | Cheapest faithful evidence | Reuse / missing scope |
| --- | --- | --- |
| The exact proof-aware production decision never rejects `ServerPlayer` or another non-`Mob`, including `NOT_MANAGED` and `DUPLICATE_UNINDEXED` controls | Connected focused Java regression through the same decision consumed by `onEntityJoin`; it must fail on baseline | Actual destination registration remains native |
| Duplicate unindexed and unverified unmanaged source mobs still fail closed, while verified/managed bodies retain prior behavior | Focused positive/negative matrix at the same composition boundary | Reuse existing ambient admission/recovery tests where dependencies are unchanged |
| The correction does not bypass actor lifecycle observation or mutate state/tickets/chunks | Source/diff review and focused composition evidence | Existing order30/31 semantics remain accepted on unchanged dependencies |
| A real player enters, causes authentic scene demand, and the lifecycle crosses HOT, restart identity, CLOSED and next COLD | One changed-candidate critical envelope, then the unchanged order32 native carrier | Still required; no unit test claims this physical result |

Terra first returns `METHOD_READY` after the connected focused regression,
affected admission/recovery tests, large-file/diff checks and a clean private
checkpoint. No aggregate, GameTest, Minecraft or native run before engineer
method review. The method is invalid if it special-cases the pilot identity,
swallows the vanilla NPE, manually repairs or waits on player/chunk maps,
changes demand, or tests only a detached boolean the event path does not use.

After method acceptance, run the changed-candidate critical envelope once:
`./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer
:pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar`.
Only if green, run one unchanged order32 HOT native carrier with its existing
causal receipt. The required result is destination `ADMITTED` evidence before
HOT, followed by exact HOT-before-restart identity, the same identity after
graceful restart, CLOSED release and next-COLD availability. A red carrier is
classified from retained evidence before any repeat; justified retry rules
apply without a confidence rerun or new proof campaign.

A separate native ingress smoke is not required by default: the focused test
directly covers the lost type fence, the critical envelope covers the changed
artifact, and the already-required carrier observes the same ingress plus the
remaining product result. Add a smoke only if focused evidence cannot reach the
production-shared decision or review identifies a distinct unresolved ingress
fact that would otherwise waste the full carrier.

## Resource and external authority

- Reuse the existing worktree, task-private evidence/process root, isolated
  port/display/world namespace and order32 carrier. Do not touch unrelated
  services or the disconnected physical display `:0`.
- Private WIP checkpoints are granted; they are neither integration acceptance
  nor publication. No commit to canonical/adopted branches, push, deployment,
  external write or destructive cleanup is authorized.
- Keep compact receipts and the current useful native bundle. Raw task proof
  remains under the project-wide 64 GiB cap. No new CI/JFR/terrain/load matrix
  belongs to this correction.
- Terra may choose and run ordinary focused checks/corrections inside this
  envelope without renewed approval. Heavy work begins only after method
  acceptance as stated above.

## Product-blocker chronology

Blocker identity: F0.2C authentic shared HOT/COLD assault lifecycle, continuous
across orders27-33. First start: UNCONFIRMED; retained chronology is in
`CONTINUITY.md` and orders27-32. Observation epoch for this ingress diagnosis:
2026-09-11T08:14:22Z. At08:18:45Z the candidate was clean and494MiB; task
evidence was12KiB, filesystem free space662GiB and host available memory28GiB.
No new test had been launched.

Whole-blocker economy audit 2026-09-11T08:23:23Z: `CONTINUE_NARROW`. The last
hour reduced uncertainty materially: order32 connected native demand evidence
to the real ingress boundary, and one launched carrier exposed a generally
reachable player-join cancellation defect; independent source tracing
discriminated it from Terra's rejected harness-wait hypothesis without a retry.
The next result is a baseline-failing focused type-domain regression, not
another proof campaign. Candidate494MiB, task evidence12KiB, disk662GiB free
and28GiB host memory available; no matching task-owned Gradle/Minecraft/native
process was active. Terra was dispatched at08:23:14Z. Silence liveness is due
08:33:14Z if no executor event; the next cumulative economy audit is due
09:22:32Z if the blocker remains active.

## Delivery and next boundary

Deliver exact commit/tree/parent and changed paths; defect and design rationale;
focused commands, counts/durations and report handles; known gaps; clean/dirty
state; and exact task-owned running-process check. Engineer reviews the coherent
method before heavy evidence and the terminal result afterward. Acceptance of
this correction permits continuation of the existing order32 native lifecycle;
it does not by itself accept F0.2C or authorize the next product slice.

METHOD_ACCEPTED 2026-09-11T08:29:39Z at clean `3dc8287c`, tree
`a240e97d`, parent `182c7efd`. The proof-aware `ServerLevel` overload now reaches
one guarded production predicate whose first condition is `mob`; duplicate,
unverified and verified actor decisions all remain behind it. Focused JUnit2/2,
large-file and diff checks pass. Ordinary JUnit supplies `mob` explicitly rather
than constructing a bootstrapped `ServerPlayer`; source review establishes the
one-line `entity instanceof Mob` mapping, and the required native carrier retains
responsibility for the physical player-registration claim. A separate GameTest
or ingress smoke would duplicate that carrier without resolving another fact,
so none is added. One changed-candidate critical envelope is authorized; only a
green result permits the unchanged order32 native carrier. Silence liveness is
due08:39:39Z after dispatch; economy remains09:22:32Z.

CRITICAL_GREEN 2026-09-11T08:34:16Z on the same clean identity:71 tasks in
2m21s,304/304 required GameTests in1.112min, verified JAR SHA-256
`ad2fc41a...a1df4`. Engineer independently matched commit/tree, JAR hash and
terminal GameTest log; no task-owned runtime process remained. The single
unchanged order32 native carrier is authorized in a fresh order33 evidence
namespace with an existing absolute process root. Its first launched result is
terminal classification evidence; no automatic confidence retry. Silence
liveness resets from native dispatch; economy remains09:22:32Z.

TERMINAL_REVIEW 2026-09-11T08:50:25Z accepts order33's product result. The sole
launched native carrier ran2m47s and no longer produced the
`DistanceManager.removePlayer` NPE. The server accepted the same cross-dimension
teleport and authentic production demand emitted `settlement_assault_prepared`
at revision15 and `settlement_assault_hot` at revision35. This is direct native
evidence that the destination player entered the server demand path and clears
the source-firewall defect. Bundle`6f34780f...e9b0d7f`, manifest
`53ccfa6e...465f5b`, console`254bfa26...73eb55`; source/tree/JAR match the
accepted candidate and no owned process remains.

The whole carrier is not green. Its client-side visit oracle timed out after
121.391s before sending the order32 receipt: it collapsed target dimension and
target chunk into one unreported conjunction and used that conjunction as a
prerequisite for the read-only server diagnostic. Retained evidence cannot say
whether the client missed respawn, dimension transition or chunk delivery.
Server HOT clears server admission but not client presentation/control,
restart/CLOSED or next-COLD. Independent review classifies the oracle as a
demonstrated harness-method defect and a client compatibility defect as still
possible. Order34 owns only the two-channel causal observation correction; no
unchanged retry is useful.
