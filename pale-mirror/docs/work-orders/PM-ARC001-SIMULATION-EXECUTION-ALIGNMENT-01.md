# PM-ARC001-SIMULATION-EXECUTION-ALIGNMENT-01: one autonomous world, explicit execution boundaries

Revision: 21. Architecture decision: 2026-09-18; independent audit accepted and
implementation activated 2026-09-19. Status: `ACCEPTED_LOCAL`; promotion
`AUTOMATION_COMPLETE`; all ARC-001 dependency slices are complete.
Implementation risk: critical-code.
PM / architect: Sol. Autonomous senior tech lead and sole coder/operator: Terra,
`gpt-5.6-terra`, reasoning high.
Workflow: [PM/TL protocol](../engineering-agent-protocol.md).

## Outcome

The existing Frontier runtime conforms to the amended execution model: the
world advances without visitors, first visibility shows current state, physical
interactions remain causal, and completing a cycle cannot orphan a scheduled
action or physical obligation. State ownership and failure reasons are
inspectable; work and retained history remain bounded as cycles accumulate.

This is mandatory alignment after the current F0.6R3 fix, not a new feature
wave, a v4 rewrite or authorization to interrupt that fix. After ARC acceptance,
complete remaining OBS-001, then XACT-001, then preserved MAT-006 onward.
The [hardening pipeline](../frontier-v3-materialization-hardening-pipeline.md)
owns ordering. Human F0.6R3 acceptance remains required; while it is pending,
PM may separately activate independent audit/preparation without claiming the
fix accepted or enabling new gameplay breadth.

## Context and authority at activation

Canonical governance/ledger: `/home/rd/proj/pm-governance/pale-mirror`.
Current implementation lineage: `/home/rd/proj/pm-f06r3-human-ingress-repair/pale-mirror`.
Exact accepted source, WIP, writer handoff and owned process state must be
established at activation, not inferred from this planned order. Original
`/home/rd/proj/minecraft` repositories and suspended MAT-006 WIP are preserved.

The independent audit/preparation phase is active against exact checkpoint
`5d08d20a55a25a9c284d50169097c01680f06542`, tree
`af4529ddee15ba572c7b2b138fb3920aba7d99b6`, in
`/home/rd/proj/pm-f06r3-human-ingress-repair/pale-mirror`. Terra may read the
canonical documents, source, repository-context output and retained evidence
and return the compact conformance/gap account required below. This phase is
read-only: no source/test/script/config edit, test/build/native launch, commit,
deployment, server/world mutation, publication or cleanup is authorized.

The exact r67 `HUMAN_CANDIDATE`, its implementation checkout and live service
remain frozen for independent M3. Audit findings neither accept F0.6R3 nor
invalidate r67 unless they identify a concrete contradiction in its claimed
scope. PM will revise this order with a coherent implementation/verification
envelope after reviewing the audit; that later activation gives Terra ordinary
technical autonomy without intermediate permission stops. No new gameplay
breadth is part of ARC-001.

## Accepted independent audit

The read-only Terra audit inspected exact checkpoint/tree
`5d08d20a55a25a9c284d50169097c01680f06542` /
`af4529ddee15ba572c7b2b138fb3920aba7d99b6` without changing source, evidence,
processes, server or world. PM final review independently confirmed its two
material structural facts:

- the closed duration/custody inventory contains sixteen active families, but
  only `RESOURCE_SITE_HARVEST` is `ENFORCED` and present in the current paired
  driver composition; the other fifteen active families remain `DEFERRED`;
- common world-state physical-intent preparation, restart transition and
  confirmation still dispatch through concrete intent-family conditions. This
  prevents one closed owner contract from uniformly proving confirmation,
  retirement, late-input disposition and recovery.

ARC-001 is therefore `repair-needed`, not a documentation-only closure. The
existing SDK, staged physical-executor registry, REL layer, engine scheduler,
recovery carriers and diagnostic plane are useful foundations and must be
extended rather than replaced. No second scheduler, relationship graph, world
database or diagnostic authority is justified.

The audit does not contradict r67's exact native harvest result or its frozen
human candidate. It narrows one overbroad description: r67 establishes temporal
referential integrity for repeated renewable resource-site harvest lineage,
not yet for every active physical-intent family.

### Current family classification

- `RESOURCE_SITE_HARVEST`: conformant and proved at automated M2 scope by r21;
  human M3 remains independent.
- resource-site projection/preparation: conformant but not yet proved as the
  general projection/effect/aftermath rule.
- production, service work, patrol, logistics, assault, engagement, migration,
  engineering, medical, hive nutrient/growth, provision, exact consumption,
  birth and ambient custody: active and `repair-needed`; none may be relabelled
  future merely because its paired contract is deferred.
- natural-terrain providers, Create rail materialization and final release/M3
  hardening remain explicit future scope under their planned stages.

Historic MAT-002--005, F0.3/F0.5 and r21/r59 evidence remains reusable only at
its exact dependency scope. It does not prove the missing closed all-family
composition, and it must not be repeated for confidence.

## Prepared implementation envelope

When separately activated, Terra owns one coherent architectural outcome:

1. Every active family has exactly one closed execution-boundary declaration
   covering canonical owner, continuation/schedule ownership when applicable,
   HOT provider or explicit no-HOT policy, custody/authority epoch, physical
   confirmation owner, retirement/late-input disposition, retention bound and
   diagnostic subject. An active family cannot silently remain `DEFERRED`.
2. Common physical-intent lifecycle and retirement validate through that
   closed owner contract. Family semantics stay with their owner rather than a
   growing concrete-family dispatch in common world-state machinery.
3. One owner transition accounts for incoming REL edges, engine schedule,
   physical intent, lease/carrier, resource commitment and bounded late input.
   It preserves ambiguous real effects and rejects missing, duplicate,
   competing, stale and unregistered ownership.
4. Inspection and bounded-work/backpressure facts derive from the same declared
   ownership. Long-lived cycles compact resolved history without discarding
   unresolved obligations; concurrent arrivals remain fair and bounded.

Terra chooses technical decomposition, files, algorithms and verification
sequence. Proportionate closure includes composition negatives, logical-time
repeated-cycle/retirement/concurrent-arrival/compaction coverage, only affected
recovery paths and one representative native lifecycle per materially distinct
changed physical shape. Accepted F0.VC and unchanged crash/native receipts are
reused. No speed-ratio campaign, blanket matrix or duplicate M3 is required.

Implementation uses the isolated target
`/home/rd/proj/pm-arc001-execution-alignment/pale-mirror` on branch
`terra/arc001-execution-alignment`, created from exact audited checkpoint
`5d08d20a55a25a9c284d50169097c01680f06542`. The target was absent and the
source checkout/tree were clean except excluded `.serena/` at activation;
636 GiB was free. Terra may create this exact worktree, edit implementation,
tests, harness and executable configuration required by the outcome, run
proportionate focused through full local/native verification, and create local
checkpoint commits. It owns technical design, decomposition, sequencing,
diagnosis, related corrections and justified retries without intermediate PM
approval.

The exact deployed r67 candidate, its checkout, service invocation
`13bef2da9c604752922d28e831cf754b` and world remain frozen for M3. ARC-001 has
no live-server/world deployment, reset or restart authority and cannot consume
the human gate. Preserve all original worktrees/WIP and unrelated processes;
no remote push/tag/publication, production/v2 mutation, unrelated cleanup or
new gameplay breadth is authorized. Task-owned disposable test processes and
isolated worlds must be bounded and stopped at handoff. Governance Markdown and
the canonical ledger remain PM-owned; Terra returns proposed facts rather than
editing them.

## Rejected first implementation candidate

Checkpoint `44ea2b0c76c183d0ed073dbc456d7094fdfd4ab6`, tree
`2783d291ade3df59aeb47fe136849b975d044ce3`, is preserved as an honest local
WIP but is not an ARC product candidate. It made all sixteen inventory entries
`ENFORCED`, added boundary declarations and reduced `FrontierWorldState`, but
the new common `FrontierPhysicalIntentLifecycle` centrally enumerated concrete
`PhysicalIntentKind` preparation/transition handlers. That relocates the
prohibited family dispatch instead of making each closed owner supply its
executable lifecycle behavior.

The candidate also constructed generic confirmation/late-disposition strings
without proving that the declaration owns the real physical transition and
atomically accounts for REL edges, engine schedule, lease/carrier, resource
commitment and retirement. Metadata plus an `ENFORCED` enum value cannot stand
in for executable composition. Focused tests are useful WIP evidence only; the
stopped broad/domain and GameTest attempts prove no terminal gate and need not
be repeated on this rejected architecture.

Revision 5 returns the same outcome for architectural correction. The exact
technical design remains Terra's. A corrected candidate must make common state
delegate through real closed owner registrations, fail composition when an
owner/capability is absent or mismatched, and include a negative that rejects
the metadata-only/central-dispatch shape. If all-family atomic closure is
technically incoherent, Terra must report the exact dependency decomposition
rather than falsely marking active families enforced.

The follow-up checkpoint `ec5657af8c6b708324b2b3e6d0bf109860f38e81`
was also rejected: its private common `owners()` list still named every family,
intent kind and concrete support implementation, so it changed the syntax of
central dispatch without transferring ownership. Terra then performed the
required causal reassessment and reverted both candidates in commits
`679ba3e9` and `a27b225d`. Independent review confirms current tree
`af4529ddee15ba572c7b2b138fb3920aba7d99b6` is byte-identical to the r67
baseline outside excluded `.serena/`; no rejected behavior remains active.

## Accepted dependency decomposition

The all-family correction cannot honestly be atomic because current persisted
physical intents carry no durable lifecycle-owner capability. Global
`PhysicalIntentPrepared`/`PhysicalIntentTransition` payloads and their reducer
must therefore infer concrete ownership, while recovery fences only physical
bindings and family retirement separately owns relations, schedules, leases and
commitments. Marking all families enforced before changing this seam would be
false conformance.

ARC-001 proceeds through four dependency-ordered implementation slices without
changing the final acceptance criteria:

1. `ARC-001A durable lifecycle owner`: every persisted physical intent carries
   one stable codec-versioned owner/capability identity stamped by its canonical
   producing family. Snapshot/WAL recovery preserves it and rejects unknown,
   missing or incompatible ownership according to an explicit version boundary.
2. `ARC-001B executable family capabilities`: each owning family supplies its
   own prepare/confirm/unknown/retire behavior or an explicit no-physical policy;
   shared ingress resolves only the durable owner identity and is family-agnostic.
3. `ARC-001C retirement closure`: real family capabilities atomically account
   for their REL edges, engine schedule, physical intent, lease/carrier,
   commitment and bounded late-input disposition without a second graph.
4. `ARC-001D composition and acceptance`: startup/recovery reject absent,
   duplicate, stale or mismatched capabilities; diagnostics, compaction and
   bounded-work facts derive from them, followed by the proportionate terminal
   integration/recovery/native evidence for the complete ARC claim.

This is sequencing, not scope reduction. A slice checkpoint is reusable WIP,
not ARC acceptance, and does not mark the remaining families `ENFORCED` early.

Revision 7 makes the explicit-identity rule global and non-negotiable. Every
owner, durable semantic type, capability key, authority epoch or dispatch
identity that can affect mutation, persistence, recovery, custody or retirement
must be supplied explicitly by its authoritative producer and use its own stable
wire identity when durable. Production and recovery code may not infer it from
another kind/enum, ID prefix, coordinates, runtime class, collection membership,
neighbouring state or any default. Missing, unknown, stale and mismatched values
fail closed before dispatch, replay or mutation. Explicit compatibility
validation is permitted; read-only derived presentation is not authority.
Fixtures may not preserve an ownerless production API. ARC-001 adds mechanical
negative coverage for this rule across every execution seam it changes.

### Active ARC-001A outcome

Terra owns a backward-/version-explicit durable owner identity from canonical
intent creation through payload codec, snapshot/WAL and recovery. Every actual
intent producer supplies exactly one valid owner; a missing, unknown, stale or
mismatched owner fails visibly. Existing semantic behavior and family dispatch
remain unchanged in this foundation slice, so no metadata claim may promote
other families. Focused producer/codec/recovery ordinary and negative evidence
is sufficient for this private checkpoint only after every affected production
module compiles and a composition-level recurrence guard rejects an ownerless or
implicitly inferred construction/dispatch/recovery path. Broad/native terminal gates belong
after the behavior-bearing capability slices unless Terra finds a materially
changed runtime seam that requires earlier faithful evidence.

The initial ARC-001A checkpoint `754b4fddae32b71c4bc695cd4e333b7fa5ce7cf3`
and correction `edda2fd3499843d0c79ae7e21481b934f033deba` are retained WIP,
not an accepted slice. They make lifecycle owner durable and remove ownerless
constructors from the core API, but independent final review found unchanged
NeoForge production call sites still invoking the removed ownerless signature.
The reported focused core tests therefore did not prove affected composition
compilation. Terra must close that real seam and demonstrate the global
explicit-identity recurrence guard; no broad or native confidence run is needed
before that cheap discriminating boundary is green.

### ARC-001A acceptance and active ARC-001B outcome

ARC-001A is accepted as a private foundation checkpoint at commit
`cd1799a8f6f963989c72da714d6896163ce6f984`, tree
`a81c6f3d2b9bf8cc6bf50ca81f28844c34189f96`. Every public `PhysicalIntent`
constructor requires `PhysicalIntentLifecycleOwner`; the canonical producer or
owning scene path supplies it explicitly; WAL and snapshot codecs persist and
decode its own versioned stable ID; missing, unknown, stale-version and
owner/kind mismatch fail before recovery or mutation. Core, NeoForge main,
pilot and test compilation passed, as did focused codec/producer/recovery and
composition recurrence coverage. The worktree is clean except excluded
`.serena/`; no task-owned runtime remains. No broad/native gate was required for
this private identity-only checkpoint, and r67 was not touched.

This acceptance does not claim executable owner composition or global
conformance. The existing common `FrontierPhysicalProcessModule` still selects
prepare/transition behavior through concrete `PhysicalIntentKind` branches, and
`FrontierWorldPhysicalDeltaSupport` still classifies structure ownership from a
`structure:` ID prefix. Both contradict the new explicit-identity invariant and
remain blocking ARC gaps; similar authoritative inference found by the bounded
Frontier-v3 production/recovery audit must also be removed before ARC acceptance.
Closed schema declarations that explicitly state compatible endpoint kinds are
validation, not inference, and remain permitted.

ARC-001B now owns one closed executable lifecycle capability per durable
`PhysicalIntentLifecycleOwner`. Each family supplies its own prepare, transition,
confirmation/unknown and retirement behavior, or an explicit no-physical policy.
Common ingress resolves only the intent's explicit stable owner identity and
validates its declared compatibility; it cannot select an owner or implementation
from `PhysicalIntentKind`, subject-ID conventions, scene contents or adjacent
state. Startup/composition reject missing, duplicate, mismatched and undeclared
capabilities. ARC-001B is an internal composition slice: focused ordinary and
negative/recovery evidence plus affected production compilation are sufficient;
terminal broad/native evidence remains for ARC-001D unless the implementation
materially changes a Minecraft-dependent behavior.

### ARC-001B acceptance and active ARC-001C outcome

ARC-001B is accepted as an internal composition checkpoint at amended commit
`e56f44981c2b6912a18d172feacef591921aa088`, tree
`7c198353175bcdc4dc52f0058fd4b7adcd9a1d75`. Common physical ingress resolves
only the durable `intent.lifecycleOwner()` into the closed composed capability;
the former common kind-dispatch process is absent. Canonical family modules
supply prepare, ordinary transition and executable terminal retirement/late-input
policies. `ROUTE_PATROL` and `AMBIENT_ACTOR_CUSTODY` explicitly reject physical
and retirement execution. Missing, duplicate, mismatched, undeclared or
retirement-less composition fails closed.

The implementation also exposed two behaviorally distinct decontamination
owners that had shared one identity. Generic `DECONTAMINATION` and
`SETTLEMENT_SERVICE_DECONTAMINATION` now have separate durable stable IDs under
lifecycle-owner codec v2; v1 fails visibly as stale rather than guessing a
behavior. Focused composition/architecture/codec and representative active-family
tests plus Frontier and NeoForge main/pilot/test compilation passed. No
broad/native/deployment claim was made; r67 remains untouched.

ARC-001C now owns real atomic retirement. Every owner-local terminal policy must
account in one canonical transition for its applicable incoming REL edges,
engine-owned continuation, physical intent, lease/carrier, resource commitment
and bounded late-input/recovery disposition. An inapplicable dimension is an
explicit checked none, not an omitted assumption. Successful closure cannot
leave a dangling reference, competing writer, replay authority or unbounded
tombstone; ambiguous possibly applied physical effects remain explicit and
local. Expected late input consumes the retained disposition exactly once.

ARC-001C also removes the known `structure:` identifier-prefix classification
from `FrontierWorldPhysicalDeltaSupport`: durable physical-delta evidence must
carry its own explicit typed semantic owner/type identity from the authoritative
producer through persistence/recovery and dispatch. The bounded audit must close
any equivalent authoritative inference in the affected Frontier-v3 retirement
path. Closed schema compatibility declarations remain permitted validation.
Logical-time ordinary, missing/stale/duplicate/late-input and restart recovery
coverage is required for the materially distinct retirement shapes; accepted
unchanged native receipts are reused, and no blanket matrix or confidence run
belongs to this internal slice.

Terra chooses the representation, codec/version mechanics, files, tests and
technical sequence within this outcome. Existing revision-4 local authority and
all live-server/publication exclusions remain unchanged.

Revision 10 resolves the durable-schema decision raised at the ARC-001C
boundary. ARC-001C is authorized to change the public/persisted physical-delta,
deferred-aftermath and structural-repair contracts coherently. One
authoritative producer-stamped discriminated semantic target travels with the
exact subject identity through event payloads, snapshot/WAL, recovery, repair
admission, repair intent/context and terminal or late-input disposition. An
explicit unclassified target remains local evidence with no repair or behavior
dispatch authority. Every consumer validates the declaration against canonical
state; none rediscovers structure-versus-organ or another behavior from an ID
prefix, semantic-part coincidence, runtime class or state-map membership.

This is one connected correction, not five local conditionals. The affected
production/recovery audit and recurrence guard must cover at least physical-loss
recording, repair selection, repair completion, production-facility reaction
and the NeoForge observation/repair bridge, plus any equivalent classifier
Terra finds on their actual call paths. Namespace-format checks and membership
checks may remain only where they validate an already explicit typed value and
cannot choose behavior or authority. Current-format codecs use stable
non-reused tags; old/missing/stale values fail before hydration or mutation
under the fresh-world-only policy. Terra retains autonomy over concrete type
shape, decomposition and tests; no broad/native confidence campaign is added.

### Accepted ARC-001C semantic-target foundation

The explicit physical semantic-target boundary is accepted at amended commit
`4d2442033463c9fc56f9a20261a2070abf029268`, tree
`a376d05695ce33b5ce166515f02c666a1cd1fcc9`. `PhysicalDelta`, graybox cells,
deferred-aftermath cells and structural-repair intents now carry a compile-time
typed producer-stamped target with stable non-ordinal tags through payload,
snapshot/WAL and NeoForge provenance recovery. Known-loss consumers validate
that value; unclassified scars cannot acquire repair authority. Structure/organ,
route, worksite and cocoon paths no longer select behavior from an ID prefix.

The first submitted checkpoint was returned because wildcard and deliberately
throwing bare-owner overloads still made invalid authority representable. The
amended checkpoint removes those production signatures and converts affected
main, GameTest, pilot, test and fixture callers. Recurrence coverage rejects
wildcard/bare-owner construction, unknown tags, mismatched targets and terminal
retirement under the wrong target. Frontier/NeoForge affected compilation and
focused repair, aftermath-codec and architecture tests passed; no broad/native
or deployment claim was needed. The worktree is clean except excluded
`.serena/`, and frozen r67 was untouched.

This accepts only the semantic-target prerequisite inside ARC-001C. ARC-001C
remains `EXECUTING`: every owner-local terminal policy must still demonstrate
the atomic REL/schedule/intent/lease-or-carrier/commitment/late-input account
defined above before ARC-001D begins.

Revision 12 resolves the next ownership seam. A family capability cannot claim
terminal ownership while its callback re-enters a shared method that selects a
concrete reducer from `PhysicalIntentKind`, observation subtype, state-map
membership or another incidental value. Each owner-local retirement policy owns
its complete concrete confirmation, conflict and recovery-unknown reduction,
including its atomic obligation accounting. Shared world-state machinery may
validate the family-neutral status transition and update typed intent,
observation and fence storage, but it contains no concrete-family dispatch or
domain side effects and provides no bypass around the composed capability.

The existing central terminal branches are therefore an ARC-001C input to move
behind their already declared family owners, not behavior to wrap with new
bookkeeping. This is an ownership correction within the active outcome; Terra
chooses the technical extraction and state-update shape. The accepted semantic-
target checkpoint remains the base. Proportionate focused/composition/recovery
evidence is required; no blanket or native confidence campaign is added.

Revision 13 authorizes the missing executable atomic-account boundary. An
owner-local planner/reducer callback alone is insufficient when it cannot name
or validate the schedule, relationship, custody and commitment consequences of
its terminal transition. Each composed capability therefore supplies one
transaction-local retirement account that explicitly binds the affected intent
and the exact applicable REL edges, engine continuation/schedule IDs,
lease/carrier IDs, resource commitments and late/recovery disposition. A
genuinely inapplicable dimension is an explicit checked-none statement, never
absence or inference.

This account is not a second durable graph, scheduler or behavior registry. The
family owns its meaning and concrete outcome; common orchestration only checks
the declared account against the authoritative pre-state, the complete proposed
domain/schedule effects and the resulting post-state before the transaction is
accepted. The engine remains the only queue owner and applies its declared
schedule effects through the existing atomic command/event boundary. Generic
validation never chooses a family from kinds, IDs, observation subtypes or
state membership. Missing, duplicate, mismatched or incomplete accounts fail
closed before publication; restart and expected late input consume the retained
disposition once.

Every composed physical capability and explicit no-physical capability must
make this boundary closed. Terra owns the concrete representation, whether the
check is staged around planning/reduction, and the smallest family grouping for
evidence. The acceptance outcome is exact obligation closure, not production of
a metadata table. Focused ordinary, mismatched/missing schedule, duplicate late
input and recovery cases cover materially distinct shapes; no blanket/native
campaign is added.

Revision 14 resolves the relationship-inventory dependency exposed by the
retirement account. The already active `supply-cargo-route`,
`service-work-station` and `hive-operation-roster` families are promoted to the
REL-001 contract inside ARC-001C before their retirement policies bind them.
This migration covers only exact relationships already required by current
active behavior; it does not pull MAT-007 metabolism/morphogenesis, new roster
behavior, logistics breadth or service-work features into ARC-001.

Each relationship remains stored by its existing canonical aggregate owner and
is exposed through the one derived read-only REL view; there is no second edge
store. Where the owner already retains exact typed endpoints, the migration
declares and validates those endpoints directly. If a required endpoint is not
retained explicitly, the owner must gain the minimal typed authoritative value
and current-format persistence rather than reconstruct it from an ID, map
membership, order or nearby state. The closed relationship inventory,
declarations, cardinality/lifecycle validation, owner-surface recurrence and
restart recovery advance together. Only after that may the matching retirement
account bind the exact relation identities or declare a truly inapplicable
dimension checked-none.

Focused schema/owner-surface, wrong-type/cardinality/lifecycle, persistence/
recovery and retirement-account integration evidence is proportionate. No new
native run or broad relationship migration is required. Future MAT-007 still
owns new hive lifecycle/roster breadth; it must extend the now-current relation
family rather than reintroduce inference.

Revision 15 closes the typed-role gap exposed while implementing the retirement
account. `PhysicalIntent.subjectIds` is not an authoritative role schema: list
position, exclusion of the cause subject, identifier spelling, aggregate/map
membership or a neighbouring record may not tell a consumer which subject is a
worker, item, carrier, project, facility, nest, attacker or target. ARC-001C is
therefore authorized to add one producer-stamped sealed typed semantic binding
to `PhysicalIntent`, with explicit stable non-reused wire tags and coherent
payload/state/WAL/recovery persistence under the fresh-world-only boundary.

Every affected producer constructs the exact per-kind binding. Owner-local
planning, reduction, retirement accounting and recovery consume those named
roles and validate them against their authoritative aggregates; they never
reconstruct a role from the compatibility subject collection. If
`subjectIds` remains temporarily exposed, it is a deterministic read-only
projection of the typed binding and has no dispatch, custody or mutation
authority. No ownerless production constructor, missing-binding fallback or
legacy decode may hydrate an executable intent. Unknown, mismatched or swapped
role tags fail before planning, mutation or replay.

The migration is one schema correction, not a new simulation layer or gameplay
wave. It covers all current production constructors and persistence paths so a
partially typed runtime cannot exist. Focused constructor/codec/state/WAL
round-trip, swapped-role, unknown/missing-tag, owner/kind mismatch and forged
recovery evidence is required. A mechanical recurrence gate rejects affected
production behavior that indexes or searches the compatibility subject view to
discover a semantic role. No broad/native/deployment run is added.

Revision 16 rejects contextual pseudo-types inside that binding. One role tag
has one nominal domain meaning across every owner: `PROJECT` cannot also denote
a settlement assault or service work, and a factory parameter such as
`projectOrAssault` is not an explicit type. Construction and recovery validate
the closed tuple of lifecycle owner, physical-intent kind and exact role schema,
not three independently plausible values. Every consumer uses the role matching
the producer's actual subject type; a mechanical recurrence/negative boundary
must reject context-polymorphic role tags and an owner/kind/role-schema mismatch.
This is part of the same revision-15 migration and does not authorize another
test campaign, native run or gameplay change.

Revision 19 records the final review of candidate
`de93ebebd71625b7a13e0727d12af0c9c6a687bf`, tree
`d55c68bad145918e13c1d4cd8bab238783e38569`. The correction to the rejected
central schema discovery is accepted within its exact scope: every physical
family supplies a complete literal owner + kinds + nominal schemas declaration,
the two no-physical owners supply an explicit empty declaration, and common
composition validates the submitted tuples and closed global inventory without
completing a family declaration. Production schema scanning, sole-candidate
selection and owner/kind-only construction are absent; snapshot recovery fences
the exact declaration fingerprint. Existing focused XML receipts contain 92
Frontier and 89 NeoForge tests with zero failures/errors. No materially new
Minecraft shape was introduced, so another native run is not justified.

This scoped correction does not yet make ARC-001 terminal. The same candidate
declares per-owner unresolved and resolved-retention limits and exposes
`COMPACTION_REQUIRED`, but the composition supplies no executable compaction or
admission recovery path for retained terminal intents. The aggregate state also
retains one independent global 4,096-intent limit while every owner declares
4,096; one owner can therefore exhaust the shared aggregate before the declared
per-owner pressure account protects fair admission for another owner. The
terminal packet contains no repeated-cycle/concurrent-arrival evidence that
would close this contradiction. A visible stop at an unreachable compaction
requirement is bounded failure, not the required bounded long-lived progress.

Terra continues ARC-001D autonomously and returns one coherent correction that
aligns declared retention/backpressure with a real safe terminal-history
disposition, preserves every unresolved/recovery obligation, and demonstrates
fair bounded admission under long-lived mixed-owner cycles. This is an outcome,
not a prescribed mechanism: Terra owns the state shape, compaction summary,
admission policy and proportionate logical-time/recovery evidence. Reuse the
green declaration/fingerprint evidence and unchanged native receipts; do not
rerun them for confidence, add gameplay breadth, deploy, publish or touch r67.

Revision 20 rejects terminal candidate
`11ed3df18d60af25b76e1cecab0dc2fd907898e3`, tree
`fd78cd31cd569a63d28dd68d4888e5dc842c23cc`, while preserving its useful WIP.
Its equal sixteen-way quota and confirmed-only/no-current-recovery selection
address the diagnosed arithmetic and retention direction, and the reported 23
focused tests are green. They do not establish the claimed integrated result:
the fairness test proves only `16 * 256 == 4096`, while the compaction test calls
the aggregate storage mutator directly. No test drives the owner-composed
prepare/admission boundary through quota pressure with mixed owners, automatic
compaction, an unresolved/conflicted control and recovery round-trip.

The aggregate method `FrontierWorldState.compactResolvedPhysicalIntents` is
also public. A textual recurrence check shows no current production caller
outside the composition, but the executable API itself permits a future or
adapter caller to bypass owner-supplied retention policy and pressure. That
contradicts the closed-composition authority requirement; absence of a current
bypass is not prevention of the bypass.

Terra continues the same outcome and owns the correction. Acceptance requires
that production mutation authority for terminal-history disposition be
structurally reachable only through the closed owner composition, and that a
faithful logical-time/integration case exercise actual automatic admission at
pressure across at least two owners while proving compactable confirmed history
makes room, unresolved/conflicted/current-recovery evidence is retained, the
other owner still progresses, and snapshot recovery preserves that result.
This is one focused correction, not another full or native proof campaign.

### ARC-001 terminal acceptance

ARC-001 is accepted locally at commit
`2a893a55e68864b11fa13cf7067565151df5b6ec`, tree
`e4579dca9eebfec82d7b7bca2575bc4cde83058f`. Revision 20's public aggregate
compaction bypass is absent. The closed owner composition alone selects
eligible confirmed intent/observation pairs under its declared pressure and
publishes their atomic removal through ordinary validated aggregate state.

The final focused integration drives the real composed reducer with 253 settled
route receipts plus unknown, conflicted and current-recovery controls. Route
admission automatically compacts only eligible settled history; a separate
resource-site owner then admits current work, and snapshot encode/decode retains
the two-owner result and all non-compactable evidence. That test also exposed
and closed the resource-site preparation subject fence: only the active typed
preparation job authorizes its site/job identities. Final XML receipts contain
47 tests with zero failures/errors; affected Frontier and NeoForge main/test
compilation and `git diff --check` passed. Earlier accepted declaration,
retirement, codec/recovery and recurrence receipts remain dependency-valid.

No Minecraft materialization shape, adapter behavior or live runtime changed in
the terminal correction, so another native or broad confidence run would not
add evidence. The tracked implementation tree is clean, `.serena/` is preserved,
no task process remains and frozen r67 was untouched. Acceptance is internal
architecture/automation evidence only: it does not claim F0.6R3 M3, player
comprehension, deployment, release, production cutover or v2 removal. OBS-001 is
the next mandatory independent technical stage.

### ARC-001C acceptance and active ARC-001D outcome

ARC-001C is accepted as an internal automation-complete checkpoint at commit
`3aac3e46f80288f8816a3c87343a7e3b8e74d0be`, tree
`cc1edc679e2cc7039483aca7b4ebdd6c59312a45`. The accepted chain includes
`445256e9`, `96f33d70` and `3aac3e46`: durable nominal owner/kind/role schemas,
owner-declared exact retirement relations without process discovery through the
derived relationship view, and an owner-valid harvest preparation/recovery
fence. The unchanged process/relationship architecture rule passes. Focused
retirement, recovery, producer, codec and affected Frontier/NeoForge
architecture tests pass; compilation was already green. The implementation
worktree is clean except preserved `.serena/`, no task process remains and the
frozen r67 service/world were untouched.

This accepts the ARC-001C boundary only. ARC-001D now owns the complete ARC
composition and terminal claim: startup and recovery reject every absent,
duplicate, stale or owner/kind/schema-mismatched capability; diagnostic,
compaction, retention and bounded-work facts come from the same closed owner
composition; repeated cycles and concurrent arrivals remain bounded and fair;
unresolved obligations survive while resolved history compacts. Terra owns the
technical closure account and proportionate focused-to-terminal evidence under
the existing implementation authority. It must reuse dependency-valid receipts,
run no blanket matrix or confidence campaign, and add native evidence only for
the materially distinct changed recovery/physical shape required by this order.
ARC-001D may use task-owned disposable worlds/processes but has no authority to
restart, replace or deploy over live r67, publish remotely, or begin new gameplay
breadth. One coherent terminal packet is required before ARC-001 is accepted.

Read the contract, execution semantics, relevant architecture invariants,
domain-relationship and diagnostic-plane contracts, performance operations and
the current evidence/incident facts. The rejected r62 empty-world/quarantine
incident and earlier first-visibility/farmer contradictions remain constraints,
not permission to restart every historical run.

## Conformance and acceptance

1. One canonical simulation, not two synchronized worlds. Presentation demand,
   physical eligibility and exclusive mutation authority are separate per
   actor/asset/front. Observer-free physical custody excludes a COLD competitor.
   No whole-settlement visibility switch decides whether work exists.
2. HOT/COLD share identities, resources, labor, stages and semantic checkpoints.
   Bounded COLD advancement respects competing actions and causal boundaries.
   HOT navigation runs continuously through the Minecraft provider; semantic
   checkpoints neither teleport bodies nor restart motion on a slow work clock.
3. Current-state projection, irreversible physical interaction and deferred
   aftermath have distinct responsibilities and supersession/recovery rules.
   Natural entry presents current state without replaying old cycles/effects;
   unresolved possibly applied effects and player modifications remain real.
4. Entity, generation, action and authority epoch are distinct. Retirement
   atomically accounts for incoming relations, schedules, intents and resource
   commitments. Actors outlive jobs. Late inputs consume explicit dispositions;
   bounded compaction does not discard unresolved obligations or keep every
   historical cycle forever. The engine remains the only schedule owner.
5. Domain WAL is not an atomic Minecraft/player save transaction. Confirmation,
   ambiguous effects and recovery retain the real boundary; isolation is local
   only where independence is established. Unknown corruption is not suppressed.
6. Shared archetypes/registries and existing REL/carrier/diagnostic machinery are
   reused. No second mutable relationship graph, generic world database or
   concrete-family branches in common lifecycle machinery. An abstraction must
   have a clear responsibility and remove a demonstrated source of duplication.
   Common code may resolve an explicit stable identity, but may not infer an
   owner/type/capability from a different kind or incidental representation.
   Every authoritative carrier is complete at its first admissible boundary;
   nullable/wildcard/polymorphic/partially typed carriers are not permitted for
   later completion. A closed registry validates only the exact producer-stamped
   owner + kind + role-schema tuple and completeness. Candidate scanning,
   sole-compatible-entry selection, payload-shape classification, fallback
   defaults and heuristic legacy recovery are all inference and are forbidden.
   Missing declaration dimensions fail at construction or schema recovery before
   dispatch or mutation, with focused negatives forging each dimension.
7. Inspection exposes owner, generation/job, stage, custody/epoch, reason,
   confirmation, next transition and relevant relationships from canonical facts.
   Diagnostic logs are derived; behavior-affecting cause/disposition is durable.
8. Indexed changed-set work, immutable-plan reuse, fair bounded queues and
   visible backpressure prevent tick/first-entry cost from growing with past
   cycles. Critical effects and recovery evidence cannot be dropped. Existing
   release performance targets remain binding without a new speedup campaign.

Terra first accounts for every existing family/boundary as conformant and
proved, conformant but unproved, repair-needed, or explicitly future scope.
A compact map or equivalent closure account is enough; no new reporting system.
Do not label a currently active family "future" to avoid an actual defect.
Reuse accepted evidence at its dependency-valid scope, repair real gaps, and
record future-family obligations in their already-planned stages. Technical
design, decomposition, files and verification methodology remain Terra's.

## Evidence proportional to the claim

Map the [seven architectural stories](../frontier-v3-execution-semantics.md#8-architectural-acceptance-stories)
to existing evidence and the smallest necessary additions: repeated COLD cycles
and first visit; unobserved live custody; actor succession/return; retirement,
restart and late input; player edits; partial-save crashes; bounded long-lived
cycles and concurrent arrivals.

These are coverage stories, not seven required new native runs or a blanket
matrix. Many lifecycle/retention properties are exercised in logical time;
wall-clock waiting is not their oracle. Minecraft-dependent claims still need
faithful native evidence and changed irreversible boundaries need their actual
recovery coverage. M0/M1/M2 evidence never claims M3. Reuse accepted F0.VC
infrastructure; no timing qualification or confidence-only reruns.

Return a coherent outcome with conformance/gap closure, evidence dependencies,
strongest-incident resolution, technical self-review, exact source/artifact
identity where relevant and repository/process state. PM reviews product and
system conformance, not intermediate code or command choices. Accepted
diagnostic/carrier coverage counts toward OBS/XACT instead of being rebuilt.
