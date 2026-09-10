# PM-F02A-REPLICA-CUSTODY-01: separate replica history from current custody

Status: accepted. Specification revision: 2. Parent slice: F0.2. Risk:
critical-code. Engineer: supervising root. Executor: `/root/terra_f02a_kernel`,
`gpt-5.6-terra`, reasoning `high`. Active phase and acceptance owner:
`CONTINUITY.md`.

## Baseline and prerequisites

- The adopted monorepo `/home/rd/proj/pale-mirror-monorepo` is the sole writable
  checkout. Baseline `main` and `origin/main` are
  `8217f246079acc9220279ce66386b687c4a7441c`. Accepted F0.V is `089495ed`,
  accepted F0.VB is `f2ea3108`, and accepted F0.1 consists of runtime/native
  candidate `3d46ec26`, proof-harness follow-up `8217f246` and run
  `34124239973`.
- The F0.1 executor assignment is terminal and its runner/process/credential
  cleanup is independently zero. Reuse the same sole executor; do not resume
  either older terminal agent.
- Preserve the eight existing engineer-owned dirty/untracked Markdown files
  verbatim and unstaged, including this order once created. No implementation
  WIP is currently dirty. Terra proposes requirement corrections rather than
  editing engineer-owned normative docs or declarative architecture.
- Read root/scoped `AGENTS.md`, `CONTINUITY.md`, this order, the engineering
  protocol, Frontier v3 contract, execution semantics, seamless foundation,
  implementation plan, architecture map/audit, materialization inventory and
  applicable mandatory project skills before implementation.

## Revision 2 bounded outcome and precedence

Revision1 attempted to combine a new persistent authority model, every legacy
consumer conversion, two physical container verticals and native qualification.
Terra correctly reported that as several independently reviewable cuts. This
revision supersedes every revision1 outcome, acceptance, execution-permission
and stop-condition paragraph below; those paragraphs remain only as the
recorded rejected broad proposal.

Deliver the reusable pure replica/custody persistence kernel only. Integrate
one authoritative aggregate into `FrontierWorldState`, commands/events/
reducers, immutable projections/diagnostics and the snapshot/WAL/payload codec
boundary. Make its ownership and transitions mechanically complete under one
fresh-world schema/envelope cut. Existing production container/economy behavior
must remain unchanged in this order: no settlement/hive runtime conversion,
no removal or reinterpretation of `ContainerSurfaceStatus.ACTIVE`, no physical
eligibility observer, no native scenario and no aftermath/effect activation.
The next order will consume the accepted kernel through reference containers.

The existing uncommitted `PhysicalReplica*` and `PhysicalCustodyLease*` files
are preserved executor WIP, not accepted design. Terra may keep, reshape,
rename or replace them. The required observable contract is:

1. Replica state is non-authoritative evidence for one exact physical object:
   stable object identity and semantic kind, expected/emitted canonical
   revision, deterministic fingerprint, evidence provenance/validity and a
   bounded lifecycle. It never stores stock, reservations, process progress,
   economic eligibility or presentation demand.
2. Custody state is exclusive current authority over the smallest exact
   physical scope: stable scope/provider identities, expected canonical and
   replica revisions, monotonic non-reused epoch, lifecycle and bounded
   unresolved classification. It never becomes a second inventory or generic
   scene lease.
3. One named world-state owner stores both registries and is the only reducer
   boundary. Commands/events validate exact expected revision/epoch; direct
   full-world reconstruction outside bootstrap/hydration remains forbidden.
4. Missing/duplicate/overlapping scope, stale version/epoch, epoch reuse,
   illegal transition, changed fingerprint and attempt to release unresolved
   custody fail closed at the smallest record. Unrelated records remain usable.
5. Snapshot, WAL and payload codecs use explicit stable non-ordinal tags,
   preserve deterministic ordering and round-trip the complete records.
   Incomplete, unknown-tag, duplicate-key, invalid-reference and old fresh-v3
   bytes are rejected rather than inferring authority from legacy `ACTIVE`.
6. Immutable projections and diagnostics expose identity, revision, epoch,
   lifecycle and bounded conflict reason for later adapters without granting
   mutation or physical truth. No Minecraft type enters the pure module.
7. This is a format/ownership foundation, not an unused alternate authority:
   the registered world configuration, state invariant checker, codecs and
   projections must all require the kernel consistently. Production behavior
   cannot consult it until the next vertical order, and no container may be
   silently initialized from load/presentation state.

Terra owns diagnosis, local design, algorithms, internal types/helpers, file
choices and focused-through-full correction cycles inside the pure Frontier v3
state/kernel/model/process/projection/persistence owners and their tests, plus
only the NeoForge file-store/runtime recovery tests needed to prove the pure
format round-trip. It may update executable architecture-debt/codec guards when
required to reject missing ownership, but may not weaken or raise a ceiling.
It must not edit engineer-owned Markdown or `architecture.yml`, change current
container/economy/executor semantics, add a Minecraft observer, start a native
client/server/matrix, provision CI runners, implement aftermath, or touch
F0.3+ behavior. A need for a concrete physical consumer to make the kernel
coherent is an `ARCHITECTURE` stop rather than implicit expansion.

### Revision 2 acceptance

| ID | Required outcome/invariant | Positive proof | Negative/recovery proof | Final proof |
| --- | --- | --- | --- | --- |
| K-1 | Exactly one pure world-state owner retains replica and custody registries without duplicating inventory/process state | composition and complete-state invariant tests | duplicate scope/owner, overlap and missing registered ownership fail closed | architecture/source guards identify one reducer/codec owner |
| K-2 | Replica lifecycle is versioned, deterministic and evidence-only | create/expected/observed-current round trips with exact identity/fingerprint/provenance | stale revision, changed fingerprint, illegal transition and forged identity isolate one record | immutable projection/diagnostic reports the exact retained state |
| K-3 | Custody lifecycle is exclusive and epoch-fenced | acquire/checkpoint/release and unresolved classification over exact revisions | concurrent acquire, stale checkpoint/release, epoch reuse and unresolved release fail closed | unrelated registry entries and canonical economy state remain unchanged |
| K-4 | Fresh persistence is complete and deterministic | payload, world-state, snapshot and WAL recovery round trips are byte-stable | unknown tag, incomplete/old bytes, duplicate key and invalid reference reject without fallback | one explicit schema/envelope cut; stable tags are non-ordinal and packaged |
| K-5 | Existing product behavior has not changed or gained dual authority | current focused process suites remain green | production `ACTIVE` debt does not grow or move behind a renamed helper; kernel is never inferred from chunk/player state | full critical-code gate passes; packaged JAR contains no fixture/fault authority |

Required terminal verification is focused pure state/codec/registry and
file-store recovery tests plus the complete critical-code gate from
`AGENTS.md`: `guardrails check`, Core GameTests, build and packaged-JAR
verification. No native matrix, GitHub runner or physical/M3 claim is required
or authorized because this cut exposes no physical behavior. The authentic
supplied-reference gate may reuse exact unchanged-input evidence because this
order cannot touch its inputs or tool.

Gate A grants diagnosis, implementation, all authorized local focused/full
tests, ordinary corrections, one coherent commit and a non-force fast-forward
push to `origin/main`. Preserve and exclude engineer-owned docs from staging.
No intermediate permission stop or heartbeat is required. Report only a real
`ARCHITECTURE`, `AUTHORITY`, `RISK` or `IMPASSE` boundary, or one coherent Gate
C packet with exact source/schema/envelope/JAR identities, commands/results,
full path inventory, retained WIP/failures and cleanup/dirty state.

While executing, the engineer performs one bounded `LIVENESS` check after each
complete ten-minute silent interval and never earlier. It is limited to agent
state and an exact task-owned command plus one progress marker; it does not
inspect source/diffs/implementation. Stop before any physical vertical,
legacy-behavior conversion, aftermath, native work, destructive cleanup,
force-push, deployment, original-checkout mutation or new public/persistent
meaning beyond the accepted contract. Preserve evidence and WIP on stop.

## Revision 1 superseded broad proposal

The remainder of this document records revision1 for audit history only. It is
non-operative under revision2 and must not be implemented or used as an
acceptance checklist in the current assignment.

## Outcome and exclusions

Close V3-AUD-044 as the first independently reviewable half of F0.2. Replace
historical container realization as economic authority with one reusable
fresh-world replica/custody contract, then prove it through one settlement
depot and one hive store. A serialized or previously visited chest is only a
versioned physical replica. Current physical eligibility and exclusive custody
are independent, bounded, epoch-bearing facts; presentation demand is neither.
Canonical production and hive growth remain live when their container was
never visited or is safely outside physical custody.

This order does not close F0.2. It excludes the V3-AUD-045 destructive-effect
and chunk-indexed deferred-aftermath vertical, crop/output activation, fungible
lot/split/merge/hopper semantics assigned to F0.3, general actor/cargo stale-
projection recovery assigned to F0.5, new scenes/processes/gameplay breadth,
natural-terrain hardening, balance, deployment, v2 removal and M3/human
acceptance.

## Contract and transition

1. Canonical container identity, exact quantities and reservations remain in
   the inventory/economy owner. Replica state records only the expected
   physical identity/semantic kind, last emitted canonical revision,
   fingerprint and evidence provenance. It is not a second inventory, liveness
   switch, reservation or process clock.
2. One separately persisted custody record owns the smallest container/object
   scope while naturally available Minecraft mechanisms may affect it. It
   names exact scope, provider/owner, expected canonical and replica revisions,
   a monotonic non-reused authority epoch, acquisition/checkpoint/release state
   and bounded recovery classification. Missing, duplicate, overlapping or
   stale custody fails closed.
3. Presentation demand selects readable materialization only. Physical
   eligibility derives from naturally available loaded/ticking world evidence
   and can exist with zero players. A visit does not create economic work;
   player absence does not by itself revoke custody; an unloaded serialized
   replica does not by itself retain custody.
4. COLD may advance an economy process whenever its exact inputs are
   canonically available and the affected scope has no live or unresolved
   physical custodian. It cannot spend a scope concurrently with physical
   custody. Safe checkpoint/release fences the old epoch before COLD resumes.
5. Remove `ContainerSurfaceStatus.ACTIVE` and equivalent historical-realization
   predicates from canonical production, provision, service, equipment,
   construction, cargo, hive nutrient and hive growth eligibility. The checked
   architecture-debt baseline for production `ACTIVE` references must fall to
   zero rather than move behind a compatibility helper or a renamed boolean.
6. Natural materialization first compares the current loaded chest and its
   owner/provenance with the retained replica fingerprint and authority epoch.
   An unchanged replica may acquire custody and idempotently catch up from the
   current canonical state. A changed, missing, foreign or ambiguous replica
   becomes a typed smallest-container observation before any projection write;
   it is never silently overwritten, adopted or used as proof of canonical
   capacity.
7. Never-visited, visited-then-unloaded and loaded-without-player histories
   share the same economic rules, schedules and exact accounting. Under equal
   canonical inputs, merely visiting or changing presentation mode cannot
   change whether settlement production or hive growth is eligible, cannot
   create an extra due edge and cannot duplicate or lose a resource.
8. This is one coherent fresh-world persistence cut. Snapshot, WAL, payload
   codecs, projections and diagnostics retain replica and custody state with
   explicit stable wire tags and reject old/incomplete/cyclic/foreign bytes;
   do not infer the new authority from legacy `ACTIVE`, v2 or a loaded chunk.
9. Graceful and abrupt restart preserve the last confirmed replica/custody
   boundary exactly. A prepared or unresolved physical custodian blocks only
   its container until typed inspection/reconciliation; unrelated production
   and hive work continue. No restart path fabricates release, reuses an epoch,
   double-spends contents or waits universally for a future visitor.
10. Per-family knowledge for the two reference containers declares evidence
    source, scope, revision/validity, invalidation and the conservative action
    for unknown/stale evidence. Unknown capacity cannot authorize an
    irreversible dependent action. No path force-loads a chunk or converts
    elapsed time into an invented observation.

## Write scope

Terra owns diagnosis, local design, internal types, algorithms, helpers and
focused-through-full correction cycles inside the pure Frontier v3 inventory,
container, economy/process, state/projection/codec owners; registered NeoForge
container materialization, observation, custody and recovery owners; the
architecture-debt executable guard; and declarative test-pilot/CI evidence
code and tests required for this result. Existing class names are navigation
hints, not a prescribed patch or exact file whitelist.

Terra may introduce the required fresh-world schema/envelope version, stable
wire tags and smallest coherent reusable interfaces. It may make minimal
checked-in CI/test-harness changes needed to run the accepted four-slot path.
It must not edit engineer-owned Markdown or `architecture.yml`, raise/deactivate
the debt ceiling, preserve `ACTIVE` meaning under a new name, introduce a
second inventory/process authority, enable aftermath/effect behavior, resume
terrain WIP or weaken an assertion/timeout. A public/persistent meaning conflict
or unavoidable F0.2B/F0.3/F0.5 behavior is an `ARCHITECTURE` or `AUTHORITY`
stop, not implicit scope expansion.

## Acceptance matrix

| ID | Required outcome/invariant | Focused positive proof | Negative/recovery proof | Physical/final proof |
| --- | --- | --- | --- | --- |
| AC-1 | Replica, custody and canonical inventory have exactly one non-overlapping owner each; presentation is not authority | registry/composition and state transition tests for depot and hive store | missing/duplicate owner, overlap, stale revision/epoch and invalid transition fail closed | diagnostics correlate one container to one replica/custodian without a second stock counter |
| AC-2 | Economic liveness is independent of realization and presentation | equal-input pure runs for never-visited, visited-unloaded and zero-player-loaded settlement/hive cases | one visit/mode switch cannot change eligibility, due ordering, quantities or reservations | fresh native lanes prove the same terminal liveness/accounting across all three histories |
| AC-3 | Naturally active zero-player physical scope excludes concurrent COLD use without making the whole polity HOT | focused eligibility/custody acquire-checkpoint-release proof | presentation absence cannot revoke live custody; serialized unloaded replica cannot retain it; unrelated processes continue | loaded-without-player lane proves bounded container custody and independent unrelated COLD progress |
| AC-4 | Unchanged replica catches up idempotently only after fingerprint/epoch comparison | depot and hive-store projection tests | stale/foreign/missing fingerprint or wrong epoch cannot write, adopt or duplicate | naturally loaded return shows current canonical contents once, without replay or force-load |
| AC-5 | Changed replica is observed before any write and isolates the smallest container | typed observation/reconciliation test for an ordinary chest change | changed/player/foreign content is not overwritten or credited as capacity; other container/economy work proceeds | ordinary player chest change reaches a durable attributable observation before projection |
| AC-6 | Fresh persistence retains the exact confirmed boundary | snapshot/WAL/payload/diagnostic round trips with stable tags | legacy/incomplete/corrupt bytes, epoch reuse and unresolved restart cannot infer authority or double-spend | graceful and abrupt filesystem lanes retain exact replica/custody/accounting and bounded local uncertainty |
| AC-7 | Historical `ACTIVE` no longer controls canonical work anywhere | architecture-debt guard reaches zero production references and all affected process tests use the replacement contract | new or renamed realization-as-liveness branch fails mechanically | packaged production JAR contains no fixture/fault authority or legacy compatibility path |
| AC-8 | Result is bounded, attributable and reusable | focused Java/Node tests plus smallest applicable Economy/Scene GameTests | evidence merge rejects missing lanes and source/spec/build/JAR/launch identity drift | complete fresh native matrix uses four simultaneous isolated CI slots; all owned runners/processes/credentials/listeners are zero afterward |

At the coherent terminal candidate run the complete critical-code gate from
`AGENTS.md`, applicable module-specific release gates, focused scenario/merge
tests and the mandatory authentic supplied-reference gate unless exact
unchanged-input evidence is validly reusable. Report source/spec/schema/build/
JAR/launch identities, commands/results, scenario/world/client/server/job/
runner identities, retained failures/corrections, debt count and final dirty
state. Distinguish code existence, automated correctness and physical evidence;
this order cannot claim F0.2 completion or M3.

Every newly started complete native matrix uses the accepted F0.VB path with
four simultaneously available isolated slots and one immutable assignment.
Focused native diagnosis may be proportionate; a failed complete matrix may be
completed with identity-exact replacement lanes only when the fail-closed
aggregate retains original provenance. Any runtime/contract/JAR/launch change
invalidates affected physical evidence.

## Review and execution permissions

Gate A grants this complete bounded outcome now: autonomous diagnosis, design,
implementation, focused/full local gates, ordinary corrections, reviewable
commits/pushes and one terminal four-worker native matrix. There is no routine
intermediate permission stop or executor heartbeat. Terra reports only a real
`ARCHITECTURE`, `AUTHORITY`, `RISK` or `IMPASSE` boundary, or one coherent Gate
C packet.

Terra may use `gh`, the accepted reusable runner installations under
`/home/rd/proj/pm-f0vb-native-pipeline`, and a bounded task root under
`/home/rd/proj/pm-f02a-replica-custody`. It may register/start four ephemeral
task-owned runners, dispatch the exact reviewable commit, download evidence,
then stop and deregister them. Each slot owns disjoint work/temp/cache/world/
port/display/evidence namespaces; at most one visible client per slot. Tokens
and credentials never enter logs/artifacts. Unrelated runners/services are
read-only.

Terra may commit coherent in-scope implementation/test/CI changes and push only
non-force fast-forwards to `origin/main`, staging explicit reviewed paths and
excluding engineer-owned docs. No force push, ref deletion, destructive broad
cleanup, deployment or original-checkout mutation is authorized.

While this order is `EXECUTING`, the engineer performs exactly one bounded
`LIVENESS` check after each complete ten-minute interval without an executor
event, never earlier. It is limited to collaboration state, exact task-owned
process/job liveness and one bounded progress marker; it never inspects source,
diffs or implementation choices. Healthy evidence causes no message or
direction. Final review examines the stable result and selected independent
evidence, not ordinary iterations.

## Stop conditions

Stop before expanding for conflicting ownership, another writer, changed or
unknown baseline/WIP, need for a new public/persistent contract, unavoidable
deferred aftermath/fungible-resource/general fenced-recovery behavior, natural-
terrain dependency, secret exposure, unrelated-service mutation, missing
external authority or repeated unexplained failure. Preserve all WIP/evidence
and return the exact unmet invariant. Do not weaken the contract, hide legacy
authority, broaden a timeout, force-load, retry blindly or self-start F0.2B.

## Review record

Revision1 was authorized after independent acceptance of F0.1. It attempted to close only
V3-AUD-044 and the replica/custody half of F0.2. V3-AUD-045 deferred aftermath
remains a separately reviewable follow-up; F0.2 is not accepted until both are
closed and their shared transition is independently reviewed.

Revision2 records the executor's valid architecture stop and replaces that
broad grant with the pure persistent kernel above. It does not close
V3-AUD-044, reduce the legacy `ACTIVE` debt, qualify a physical container or
authorize native work. After independent acceptance, a separate reference-
vertical order will consume the kernel before deferred aftermath begins.

First revision2 Gate C candidate `5b7c8329213e4df5c9f10b2984d3cadbb8a85956`
is not accepted. Independent review reproduced its focused tests and packaged
JAR identity but found that lower-bound fence checks could admit fabricated
future canonical/replica revisions, the immutable projection could choose an
arbitrary released lease from a valid multi-scope object history, and the
declared replica/custody WAL/file-store and malformed-codec recovery boundary
was not directly proved. The existing revision2 authority continues for one
coherent correction: exact revision/epoch fencing, deterministic truthful
diagnostics for every valid retained history, and direct persistence/recovery
recurrence evidence. This does not authorize a physical consumer, native work,
legacy `ACTIVE` conversion, aftermath or any later slice.

Second revision2 Gate C candidate `d43013a52bea1596b45a41e1dcd29861e64e93ab`
is not accepted. Independent review confirmed that it repairs all three first-
candidate findings, reproduced the focused state/codec/file-store tests, matched
the packaged JAR SHA-256 `56df72d6f516f45a64d1f07d37f25921112b66241ffba9eb798fb58e7a8afa23`,
confirmed unchanged production `ACTIVE` debt at 56 exact source references and
read the retained 292/292 Core GameTest shutdown. The reusable FND-01 lifecycle
is nevertheless incomplete: one stable object can only pass through one
declaration/observation cycle, so a later canonical emission or naturally
active re-observation cannot advance its retained evidence before a new
custody acquisition. The existing public lifecycle descriptor even permits
`OBSERVED_CURRENT -> OBSERVED_CURRENT` while the sole aggregate rejects it.
In addition, changed physical evidence is reduced to `CONFLICT` while its
actually observed fingerprint/provenance and bounded replica-conflict reason
are discarded; the projection therefore cannot expose the exact retained
conflict required for reconciliation. A forged non-initial replica revision on
declaration and a repeated unresolved transition are not rejected as illegal
lifecycle transitions.

Revision2 authority continues for one bounded conceptual correction, not a
prescribed patch: make the same stable replica identity support deterministic,
monotonic, exactly fenced re-emission/re-observation cycles; require acquisition
against the latest explicitly confirmed matching evidence; retain the actual
conflicting evidence and a bounded reason in persistence and diagnostics; and
make every initial/version/lifecycle combination closed and fail-fast. Prove at
least two successive cycles for one object, conflict retention, forged initial
version rejection and duplicate/illegal terminal transitions through payload,
world-state and recovery boundaries. Terra owns the concrete state machine and
internal API design. No physical consumer, native work, `ACTIVE` conversion,
aftermath or later slice is authorized.

Corrected candidate `355d877c117eb07b5366911fbd3b79375f274881`
(parent `d43013a5`, tree `d4eaee9c736b6f8910e808895be4769a9a5f19ed`)
is independently accepted for revision2's pure-kernel scope. It adds a closed,
monotonic re-emission/observation cycle for one stable object, exact acquisition
against the latest matching evidence, retained observed conflict fingerprint/
provenance with stable reason tags, and fail-closed initial and terminal
lifecycle transitions. State schema is 131 and persistence envelope is 45.
Main reproduced the focused replica/custody, persistence and actual file-store
snapshot/WAL tests; matched the packaged JAR SHA-256
`11ad9da8606e078918777d628d28d8410aea1c878b22de5bb0fcb8ee8d66c334`;
confirmed `HEAD == origin/main`, unchanged production `ACTIVE` reference count
56, clean implementation staging and the retained normal shutdown after all
292 Core GameTests passed. The executor changed no engineer-owned Markdown and
left no task-owned process.

This acceptance is deliberately narrow. It proves a reusable persisted pure
authority kernel, not a trusted Minecraft observer or physical owner. F0.2B
must still consume it through real settlement and hive reference containers,
prove acquire/checkpoint/release and changed-replica-before-write across
naturally active/unloaded/restart histories, and expose any missing
reacquisition/reconciliation transition rather than creating a second
authority. `ContainerSurfaceStatus.ACTIVE`, deferred aftermath, F0.2 as a whole,
V3-AUD-044 and every native/product claim remain open.
