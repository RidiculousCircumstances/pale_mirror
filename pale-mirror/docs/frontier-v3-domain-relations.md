# Frontier v3 domain relationship contract

Status: active architecture contract. The first implementation and adoption
boundary was `PM-REL001-DOMAIN-RELATIONSHIP-LAYER-01`, now accepted at its
recorded scope. The 2026-09-18 temporal-integrity amendment is adopted through
ARC-001 after the current F0.6R3 fix, not by rebuilding REL-001.

## Decision

Frontier v3 uses an explicit pure-domain relationship layer for durable links
between canonical entities. It is not an ORM, database, generic entity store or
second world model. Existing aggregate state, versioned snapshots and ordered
WAL remain authoritative.

An authoritative relationship is retained by the domain record that owns its
meaning. The relationship layer declares and validates that ownership, endpoint
types, cardinality and lifecycle, then derives a bounded read-only relationship
view from the current canonical state. The derived view may explain and inspect
the world but may not choose actors, resources, work or outcomes.

## Problem being removed

Many current records already retain exact IDs, but their contracts are spread
across process-specific code and most endpoints share the broad `SubjectId`
representation. This permits four recurring defects:

- a later transition re-discovers a related object by settlement, kind, status,
  ordering or proximity instead of following the ID accepted earlier;
- two valid same-kind candidates make a formerly unique inference ambiguous;
- duplicated forward/reverse facts drift across process, assignment, physical
  custody and diagnostic views; and
- an operator can see several correct local objects without seeing the exact
  durable chain that connects or blocks them.

The relationship layer makes those states unrepresentable or locally visible.
It does not replace the process aggregates that own them.

## Vocabulary

UAE amendment (PM-UAE001, accepted target, adoption in progress): the actor
execution aggregate solely owns actor -> current execution and one bounded
suspended continuation. Each reference declares activity kind, exact activity
owner and monotonic actor execution generation. Family records retain work
assignments and progress; body lifecycle retains actor/UUID and physical epoch.
Neither is copied into the execution aggregate. Finish/cancel retains the last
generation until actor retirement so a later execution cannot reuse it. Scene
participant relations confer process/effect participation, not another body or
position owner. Resource operation references survive activity replacement
under their existing operation owner's reconciliation contract.

The common body lifetime also owns a bounded actor -> loaded-residency generation
high-water mark in physical recovery evidence. It is neither a job generation
nor a new UUID/physical incarnation. Exact insertion/return is its producer;
scene and ambient departure projections retain that same generation and cannot
mint it. Withdrawal of an unload receipt retains the high-water mark, preventing
ABA reuse of an earlier same-position save/acknowledgement. Actor retirement may
remove it only with the common lifetime's terminal cleanup.

Route interception retains two different declared purposes: transport crew use
LOGISTICS under their exact RouteOperation; attackers use ROUTE_INTERCEPTION
under their exact RouteEngagement. A logistics scene references both cohorts;
membership or combat side cannot supply a missing activity declaration. The
engagement's start, travel, signal, strike and resolution payloads carry exact
execution identities. Resolution and cargo interruption retire the appropriate
cohorts atomically with their owning process changes, not by deleting bodies.

- **Entity identity** is a stable canonical ID plus its declared entity kind.
  The same wire string cannot silently stand for a different kind.
- **Process generation**, **job/action identity** and **physical authority epoch**
  are distinct from entity identity. A new crop cycle does not replace the
  field or its worker; a physical hand-off does not create a new job.
- **Authoritative relationship** is one durable reference or bounded group of
  references stored by exactly one canonical owner.
- **Relationship kind** is one stable, explicit, non-reused semantic tag with a
  declared source kind, target kind, owner, cardinality and lifecycle.
- **Admission** is the one transition at which policy may discover and select a
  previously unrelated entity from eligible candidates.
- **Relationship view** is a deterministic read-only projection of all declared
  relationships for one exact canonical revision.
- **Relationship incident** is the typed local explanation of a missing,
  duplicate, foreign, stale, wrongly typed or lifecycle-incompatible relation.

## Authority rules

1. Every durable cross-owner relationship has exactly one canonical owner.
   The owner stores the accepted endpoint identity in its ordinary domain
   state and carries it through its typed commands and events.
2. Reverse navigation is derived. A second mutable reverse list, lookup table,
   adapter ledger or Minecraft tag may not become relationship authority.
3. Discovery is allowed only at admission. Once admitted, continuation,
   completion, cancellation, recovery and successors follow the persisted exact
   relationship. They may not search again by kind, settlement, status,
   position, list order or apparent uniqueness.
4. A workflow that intentionally has no relation records an explicit absence or
   alternate provenance at admission. It may not treat absence as permission to
   infer a target later.
5. A relationship endpoint never grants ownership of the endpoint's state.
   For example, a job may name its worker, input and output while actor vitality,
   inventory quantity and physical custody remain with their declared owners.
6. One canonical transition changes the owning record and every inseparable
   relationship fact atomically. No WAL revision may expose a newly terminal
   object while retaining a contradictory required relationship.

## Closed relationship contract

Every relationship kind declares at least:

- stable semantic tag and lifecycle version;
- source and target entity kinds;
- the canonical record/component that owns the relationship;
- source and target cardinality, including exclusivity where applicable;
- the admission transition and allowed source/target lifecycle states;
- whether the edge is immutable, replaceable under an expected revision or
  terminally retained for history;
- endpoint absence, duplicate, stale-version and wrong-type dispositions;
- retention/compaction semantics and the diagnostic facts required to explain
  the current edge or its termination; and
- whether cycles are meaningful, forbidden or bounded by a declared hierarchy.

Unknown, duplicate or incompatible relationship descriptors fail closed before
execution or recovery. Stable tags are explicit and never enum ordinals.

## Relationship view and queries

The pure domain relationship view is derived from authoritative records for one
exact `FrontierWorldState` revision. It exposes bounded incoming and outgoing
edges, owner, lifecycle state and causal correlation. It has no mutation API and
is fully rebuildable; a cached or serialized acceleration copy, if any, is
disposable and must prove its source revision before use.

At minimum, read-only diagnostics can answer for a named entity:

- what it currently owns and what currently owns or references it;
- which exact relationship admitted each active process/custody fact;
- the chain from objective/task through work, actor and resource to terminal or
  successor state;
- the first incompatible/missing relationship and responsible disposition; and
- the canonical revision and trace correlation supporting that answer.

Diagnostics and tests may traverse the view. Domain decisions consume the
authoritative exact IDs already carried by their owner; they do not query the
view to select a convenient target.

## Retirement and bounded temporal integrity

The [execution protocol](frontier-v3-execution-semantics.md#7-lifecycle-retirement-and-temporal-referential-integrity)
requires retirement to resolve incoming obligations, not just outgoing fields.
Completing, cancelling or replacing a lifecycle generation must atomically
retain, transfer, cancel or terminally dispose of its affected relations,
resource commitments, scheduled continuations and physical intents. The engine
remains the only durable schedule owner; the relationship layer cannot copy its
queue or derive deadlines from load/release time.

The actor outlives a job. Job completion releases assignment/custody through the
same exact relationship and leaves that actor available for its declared next
state. A terminal receipt is immutable evidence, not a scheduler or a second
worker record. Late input checks exact generation/action/epoch and consumes an
explicit disposition; a possibly applied effect retains its recovery obligation.

A physical scene strike retains its producer-declared `PhysicalSceneBinding`
(exact lease ID and revision) in its durable role binding. Cause, attacker/target,
intent spelling, matching roster or current-scene search cannot reconstruct that
authority. Preparation and starting require the exact HOT scene; an already
performed strike may settle its exact observed receipt while that scene drains.
Closed scenes retain history but grant no new transition authority. Retirement
validates the retained transition, not the pre-effect requirement that the target
is still alive. Both payload and snapshot codecs preserve the binding; obsolete
unbound strike schemas are rejected rather than heuristically upgraded.

Reverse indexes are bounded, revision-bound and derived. Admission/retirement
must reach all affected incoming dependencies without whole-world scans per
event. Compaction may summarize resolved generations only after their incoming
and replay/recovery obligations are discharged. Declare count/byte bounds and
an admission/recovery outcome at capacity. TTL alone is not safe deletion;
retaining every old generation indefinitely is not a valid retention policy.

One canonical reference-closure barrier compares the authoritative pre-state,
the owner's complete transaction-local retirement account and the proposed
post-state before publication. New or changed edges must resolve to an exact
live typed endpoint or an exact terminal/recovery disposition. Removal,
replacement or terminal transition of an endpoint must account for the complete
actual incoming set; equality, not a best-effort subset, is required. Every
durable ID-bearing relationship or obligation family participates in the closed
inventory, including engine schedules, physical intents, leases/carriers and
resource commitments. An unregistered authoritative reference or an alternate
deletion path is a composition error, not a locally tolerated convention.

The reverse view used to make this check bounded is derived for one revision
and can be rebuilt from canonical owners. A missing or stale acceleration index
cannot authorize publication. Recovery performs the complete audit before any
due action or physical executor resumes. Ordinary invalid transitions are
rejected atomically at their smallest owner; only evidence that an already
accepted or hydrated canonical revision contains a dangling reference is world
integrity corruption eligible for quarantine.

Unknown references remain violations. A retired-generation disposition must
not turn a genuinely unknown live subject or corruption into a harmless no-op.

## Validation and incidents

Every admitted or changed relationship is validated against the same canonical
revision for endpoint existence and kind, owner, cardinality, exclusivity,
lifecycle compatibility and expected authority/revision where applicable.

A violation fails at the smallest owning process. Its first accepted immutable
relationship incident retains the relationship kind, owner, source, expected
target or target class, observed candidates/fact, canonical revision, reason,
disposition and causal trace link. Repeats cannot overwrite the first cause.
Only canonical/persistence corruption can quarantine the whole frontier.

Relationship incidents compose with the existing conflict-incident and
diagnostic-plane contracts. A generic `CONFLICT`, exception string or empty
lookup is not an adequate relationship result.

## Persistence and recovery

Authoritative IDs remain in ordinary canonical records and typed event/WAL
payloads. Snapshot hydration and ordered WAL replay restore those owners, then
the relationship validator audits the reconstructed revision before independent
scheduled or physical execution resumes.

The read-only relationship view is reconstructed from that state. It does not
require SQLite, JDBC, an ORM, lazy loading, a separate transaction manager or a
second persistence log. A missing or stale derived index is a visible diagnostic
limitation and never permission to infer or mutate a relationship.

## Immediate F0.6R3 vertical

The first mandatory migration is the complete currently failing chain, not an
isolated helper:

1. objective and strategic task;
2. demand/quote, accepted market order and financial reservation;
3. production job, exact worker, retained input and declared output;
4. work scene/lease and the same actor's live binding or inactive carrier;
5. exact produced item or fungible lot/claim;
6. provision cycle, allocation and exact recipient nutrition result; and
7. the same farmer's predecessor and declared successor work.

This vertical must remain navigable and restart-stable from every named object.
Two same-kind blocked tasks, another eligible resident, another bread item or a
stale carrier must not change the accepted chain. Failure produces a typed local
incident rather than re-selection, disappearance or a misleading status.

For a field harvest, the admitted job declares the exact actor-held resource
account before the first yielding cell. A zero-yield job may retain that reserved
identity without creating a balance; a yielding cell creates or extends only
that account. HOT/COLD continuation and retirement follow the declared
job-to-account relation, never reconstruct account ownership from the job ID.
The job also declares the exact destination depot account at admission; the
resource transition may not reconstruct that identity from the chest ID.
The physical hand-to-depot transition must settle both accounts and job together.
The current harvest physical-intent schema declares both account IDs as typed
roles, including before the first positive yield. Its predecessor lineage
retains those same IDs through snapshot/WAL and a deferred receipt. The retired
exact-output role/tag 14 cannot be used to infer a 64-wheat stack from a field.
This identity correction does not itself implement the HOT physical handoff.

## Incremental adoption without dual authority

`REL-001` inventories every current long-lived cross-owner relationship family
and classifies it as:

- `RELATION_LAYER_CURRENT`: governed and exposed by this contract;
- `OWNER_EXPLICIT_UNCHANGED`: already exact and safe, with its future migration
  boundary identified;
- `MIGRATION_REQUIRED_BEFORE_TOUCH`: no new behavior or modification may use
  that family until it adopts this contract; or
- `NOT_A_DOMAIN_RELATION`: a value, derived presentation fact or local
  implementation detail, with a stated owner.

The complete F0.6R3 vertical becomes `RELATION_LAYER_CURRENT` immediately. No
new or changed Frontier v3 feature may introduce an unregistered long-lived
cross-owner ID or post-admission inference. OBS-001 extends relationship
visibility across current reason producers after ARC-001 temporal alignment;
XACT-001 migrates and validates all
body producer/adopter relationships before MAT-006 resumes. Later MAT stages
must migrate every relation family they touch before adding breadth.

ARC-001C is the migration boundary for the already active
`supply-cargo-route`, `service-work-station` and `hive-operation-roster`
families because their terminal policies now require exact retirement accounts.
They become `RELATION_LAYER_CURRENT` before those accounts are accepted. This
promotes only relationships already present in current canonical behavior: it
does not add logistics/service features or MAT-007 hive lifecycle breadth.
Existing aggregate fields remain authoritative and the REL view stays derived;
any missing endpoint becomes an explicit typed owner field with current-format
persistence, never a lookup or string convention. Later stages extend these
current families rather than creating an inferred compatibility path.

This is a strangler migration of authority, not a permanent compatibility
bridge. A classified unchanged family may continue only while untouched and
must never compete with the relationship layer for the same fact.

The post-ARC runtime contradiction narrows that allowance. It applies only to
relationships that are read-only in the current runtime or to explicitly future
unreachable behavior. Every active producer that consumes, transfers, replaces,
retires or releases a canonical reference or physical replica boundary must
adopt the common typed transition contract in `ARC-001E` before further MAT
breadth, even if its stored endpoints were previously classified
`OWNER_EXPLICIT_UNCHANGED`. Domain families choose a complete legal disposition;
they do not separately close the old binding or manufacture the successor.

The common transaction accepts no partial carrier: subject, authoritative owner,
expected revision/epoch, affected obligations and exactly one successor,
terminal, ambiguity or block disposition arrive together. Omission is rejected
at the production construction boundary before events or state publish. The
closed relationship/obligation audit remains a second independent check and the
recovery audit rejects forged persisted state; neither is the primary lifecycle
implementation.

## Acceptance properties

- Exact endpoint type errors and forbidden cardinalities fail deterministically.
- Two equivalent semantic candidates cannot alter an already admitted chain.
- Forward and derived reverse views agree for one exact revision.
- Restart produces the same authoritative relationships and deterministic view.
- Missing, duplicate, stale and lifecycle-incompatible endpoints yield typed
  owner-local incidents with a trace link.
- A transition that omits any incoming scheduled, physical, custody or resource
  obligation cannot publish a new revision; a recovery audit detects the same
  forged persisted state before execution resumes.
- No authoritative ID-bearing field can bypass the closed relationship and
  obligation inventory merely because its current family has custom lifecycle
  code.
- The current production/provision/farmer chain is explainable from task, job,
  worker, output, allocation or recipient and leads to the same terminal and
  successor facts.
- Removing the read-only view or cache cannot change simulation behavior.
- No database, ORM, adapter state or Minecraft object becomes canonical truth.
