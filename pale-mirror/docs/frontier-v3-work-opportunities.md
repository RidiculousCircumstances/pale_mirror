# Shared work opportunities and individual execution

Status: accepted implementation order, 2026-10-02. Main alone implements.
This is a requested feature, not a hypothetical-defect hardening campaign.

Current acceptance update: independent multi-worker domain/HOT/COLD/recovery
implementation is wired. Native concurrent-field restart scenario completed both
exact workers' confirmed deliveries (33+31 wheat), with a reviewed two-farmer frame.
Station release and second eligible baker admission/completion pass the existing
domain vertical; a discovered single-job workshop-board consumer is corrected
and covered by that same vertical. Native bakery acceptance remains open;
historical progress notes below are not the current completion state. User
authorized diagnostic deployment and volunteered manual inspection. Source
6acb3985 is locally committed (no push), packaged from a clean detached checkout
and deployed to fresh world `frontier-v3-shared-work-r14-20261002`. Both operational
deployment gates pass; fresh live summary has0 required conflicts. This enables
human diagnosis, not a formal feature/release acceptance claim.
Diagnostic update13aa3502 adds the accepted soft spatial target distribution;
26 focused selection/concurrency/recovery/crop tests pass, with12 process tests
rechecked after the final actual-position adjustment. Guardrails and final clean
package build pass; same R14 world retained and both deploy gates pass. Live
startup summary is green/no conflicts. Physical reduction in farmers' crossing
remains a manual observation, not an automated visual acceptance claim.
The broad GameTest gate has16 failures: several source-proven invalid coordinate/
authority fixtures, plus unclassified patrol/physics cases. It is not green.
Assess those tests against actual contracts; never loosen domain guards or rerun
the full matrix blindly to appease obsolete expectations. Active obligations
must remain covered; unrelated obsolete infrastructure must be named separately.

## Product outcome

Current graybox geometry amendment: default fields occupy16x16 blocks, with
four internal water-source cells and252 crop cells. Every crop is within vanilla
hydration range. Current staffing amendment permits three farmers and two bakers;
64 denotes a carried batch,
not field size. Explicit small single-batch test layouts remain independent of
the default world producer. This geometry change deploys to a fresh disposable
world, not a live layout migration.

Assign two agricultural workers to each current settlement field and two bakers
to each current bakery. Two farmers can harvest different available cells
concurrently. A bakery with one workstation admits one production execution;
the other authorized baker remains free to choose another allowed activity.
Authorization is not a resident assignment, reservation or obligation to wait.
Unavailable work never creates a resident-owned waiting job or a queue at a
busy machine. Existing needs, schedule preferences, navigator, resource custody
and confirmed physical effects remain authoritative.

The original two-farmer acceptance above is superseded for live graybox staffing
by the user's three-farmer amendment. The shared algorithm is unchanged; registered
admission/progress/replay coverage now exercises three exact workers. Two-worker
native scenarios retain explicit two-worker fixture permissions independently.

## Responsibility boundaries

- Area target selection uses soft spatial separation, not a shared row cursor.
  The first admitted worker starts near its actual position; subsequent workers
  prefer eligible starts farthest from the other current work targets. Continuation
  prefers nearby eligible targets closer to its last work station than to peers'
  targets, falling back to all remaining available work. The generic AreaWorkSelection
  owns deterministic geometry ranking; the field owner supplies eligibility and
  exact reservation views. No territory is persisted or made exclusive, no crop
  receipt/claim is reassigned, and the navigator retains sole path authority.
  Delivery return and external selected-cell loss use the same field selection
  boundary. An already admitted or in-flight target is not rewritten by this policy.
- An observed harvest worker changing activity retains its exact physical body
  and confirmed carried stack. Closing its scene/resource binding is not body
  retirement. The registered release policy governs retention; cargo does not
  silently override it. The successor adopts the existing carrier using the
  shared ownership handoff. Unobserved retirement retains its existing fence.
- Standing clearance belongs to shared physical geometry, not the work family.
  Collision-free crops permit harvest and ambient bodies equally; solid blocks,
  missing support, fluids and occupied admission positions remain blockers.
- Settlement management owns work permissions, priorities and desired staffing,
  not worker movement, plant state or machine processing.
- A registered work provider owns discovery and validation of its family's
  opportunities. Discovery may select a new exact target only at admission.
  The field provider offers actionable CellIds; the bakery provider offers
  recipe executions backed by inputs, output capacity and a station place.
- Shared selection ranks executable opportunities for a resident, after needs,
  permissions, capabilities and schedule preferences. It contains no farmer,
  recipe, field-stage or bakery-phase branches. Composition supplies providers;
  closed declared keys do not come from ID prefixes or payload inference.
- Reservations admit exact target/resource/station claims atomically with the
  individual execution. One active reservation owner exists; assignment and
  occupancy views derive from those persisted owners, not mutable caches.
  Exclusive cell/place, resource quantity and output-capacity claims have
  explicit different semantics; a universal boolean "busy" is insufficient.
  A station place is held only for its actual protected operation, not an
  unrelated worker's later return journey. Resource custody may outlive that
  place reservation without keeping the place occupied.
- Individual execution owns its worker, selected target, labour, movement order,
  hand custody and safe interruption. Navigation, resource transfer and machine
  processing are delegated to their existing owners, not reimplemented.
- The field owns plant growth and cell outcomes, independently of farmer count
  or cargo delivery. A station owns its processing inputs/output. Neither knows
  the shared selector's algorithm. Confirmed consumption alone relieves hunger.

No shared coordinator may inspect concrete family jobs to discover their safe
checkpoint. Family capabilities validate exact owner, worker, epoch and target.
Do not replace a single workerId with a list while retaining shared progress or
hand accounting. Do not split the field into artificial fixed farmer strips.

## Source constraints established before implementation

ResourceSiteLifecycle currently holds one active ResourceSiteHarvestJob.
ResourceSiteHarvestLineage initially pinned the next epoch to the prior worker; physical
intent, scene, delivery and assignment consumers consequently assume that job.
ResourceFieldCycle.accounted/yielded are batch history, not concurrent claims.
That single-job model must be migrated coherently, including HOT/COLD, physical witnesses,
codec/hydration, diagnostics and closure; a second unregistered farmer loop is
forbidden. ProductionProcess/BakeryJobAdmission currently independently select
the first baker, before activity/body admission; admission must retain the exact
eligible selection instead. Existing facility commitment enforces an exclusive
whole facility; field target claims must replace that restriction for harvest,
while one bakery station remains capacity one.

## Implementation sequence

1. Share eligible work selection and preserve the selected resident through
   bakery admission. A temporarily unavailable first candidate cannot prevent
   another eligible authorized worker taking an available execution.
2. Introduce the typed provider/opportunity and claim protocol with immediate
   field and bakery consumers. Offer selection is read-only; start revalidates
   and atomically claims current targets and capacity. No offer means no retained
   resident job. Changes to availability wake selection without WAL polling.
3. Separate field plant state/work availability from individual harvest
   executions. Each execution retains exact worker/target/progress/cargo;
   reserve cells independently, count only confirmed cell outcomes, and close
   one worker without retiring another's claims or resetting plant growth.
  Remove previous-worker epoch pinning as a selection policy, preserving exact
  execution identity across interruption/recovery instead.
   Plant/cell generation and layout revision are independent of an individual
   worker's cargo batch. Never advance the whole field epoch when one worker
   delivers while another retains a pending cell effect. An opportunity names
   the exact current cell generation; renewed maturity offers new work without
   duplicating the preceding generation's outcome. Field-level historical
   yieldedCount cannot determine the amount in an individual worker's hand.
4. Migrate all active field callers, events, codec, relations, scenes, physical
   witnesses, diagnostics and closure to that one model. Fresh-schema-only for
   disposable test worlds; no legacy active executor or compatibility path.
   Reuse existing shared movement and work clocks. Add two-worker permissions
   and two baker permissions in the current settlement policy/bootstrap.
   Profession is not permission or exclusive assignment. Initial staffing must
   declare exact allowed residents rather than dynamically take the first two
   currently free residents or partition the field by coordinates. Bread-works
   employment must support every permitted baker, not just the company founder;
   market admission retains that selected baker's exact employment and price.
5. Verify the actual integrated production path and affected negative/recovery
   boundaries, then package for a diagnostic stand. Deployment/reset follows
   existing authority and server-operation rules, not this document alone.

## Finite acceptance

- Two exact farmers concurrently process distinct cells on the same field;
  no duplicate cell effect/yield, no shared hand or labour progress.
- Losing, blocking or externally harvesting one selected cell releases or
  retargets only its execution; other available work can continue.
- One farmer can eat while the other works; safe pending physical effects and
  held cargo survive interruption without duplicate owners.
- Two bakers are authorized; one workstation prevents concurrent processing,
  and the non-admitted baker has no production assignment or machine wait.
  Releasing the station permits a newly selected eligible baker, not a pinned
  predecessor. Ingredient and capacity claims prevent double allocation.
- HOT/COLD/HOT and restart preserve both exact actors, claims, partial labour,
  cargo and actual plant conditions. Current projection does not replay history.
- Native/player evidence shows named workers and the terminal/successor story;
  focused tests alone cannot claim player-visible completion. Reuse relevant
  tests, add only coverage for these changed obligations, no reassurance matrices.

## Test-pool assessment — 2026-10-02

This is an assessment of the inspected feature checks and the latest failing
integration gate, not a claim that every repository test has been audited.
The pool is valuable but currently mixes product regressions with invalid or
obsolete fixtures. A passing endpoint alone also leaves visible execution
unverified. Keep the obligations; repair or retire the misleading checks.

| Existing evidence/check | Product value and disposition |
| --- | --- |
| Two-worker field admission, distinct cell generations, independent hand/output histories | Keep: protects against duplicate yield, shared progress and retiring a sibling. |
| Snapshot/WAL recovery and exact physical-intent authority | Keep: protects against lost actors/resources and repeated non-replayable effects. |
| Second baker admitted through registered settlement stock reconsideration; station released after output removal | Keep: covers actual dispatch and exclusive station ownership, not manually invented second work. |
| Two-job workshop board in that existing bakery vertical | Keep: found a real active single-job assumption; corrected bounded phase summary. |
| Native concurrent field HOT/COLD/restart and both terminal deliveries | Reuse accepted evidence unless its dependencies change; no confidence rerun. |
| One-cell retained movement requiring at least three fast samples | Repair assertion: the trace reaches the legal target after two samples. Assert semantic arrival, envelope and continuity, not a minimum journey length. |
| Ambient fixtures placing bodies at random GameTest coordinates outside unchanged canonical bounds | Repair setup: bootstrap, body, geometry and hard scope must use one coordinate frame. Do not relax production bounds. |
| Restored-body fixture directly transitioning PREPARED to HOT | Repair setup: supply the actual exact body-confirmation protocol; correct policy rejection is not a product regression. |
| Catalog/scenario checks still naming retired settlement-provision profile | Explicitly migrate or archive the obsolete scenario with its replacement obligation; do not resurrect a removed runtime writer. |
| Route-patrol formation advance/release and ambient physics failures | Open investigation: route fixture already translates its bootstrap, so the coordinate diagnosis cannot be generalized to it. Release rejects a divergent retained formation; do not waive it as harness noise without locating the cause. |
| Native bakery screenshot/visibility | Useful but currently invalid acceptance: first frame showed sky, next camera had no line of sight to the baker. Terminal delivery passed the first run, but neither establishes the visual claim or a physical two-baker handoff. |

The full frontier run took 16m31; the complete GameTest gate took 6m32.
Those costs justify affected selections during iteration, not removing ownership
or recovery coverage. The latest GameTest gate remains 437 tests / 16 failures,
not green. Do not widen the shared-work task into an unbounded repair of every
historical family. First classify the remaining failure and its release impact;
then repair the smallest affected check or raise explicit unrelated debt.

Practical execution: source analysis first when the contradiction is visible;
one focused check for the changed boundary; native evidence for the unresolved
physical/player claim; complete gate at integration, not after every fixture
edit. A new test needs a named product obligation and a failure that changes a
decision. A duplicate test is justified only by an independent boundary or risk.
Do not count assertions, screenshots, runs or green tests as product progress.

## Current progress

Source ownership audit complete. Initial wired increment adds shared read-only
ResidentWorkSelection, per-candidate activity/body admission, exact selected
baker construction and read-only committed-facility refusal before assignment.
CompanyFoundationProcess now emits distinct per-resident contracts for local
bakers; an employee need not be the company founder. The market-backed regression
uses two exact employed bakers and the registered engine transaction. Availability
of finance is checked against proposals rather than silently reselecting a worker
in the block reducer. No new mutable reservation/assignment store exists.

The next wired increment makes the existing exact actor resource account the
source for hand quantity/lot reads in crop work, projection/release,
reconciliation, delivery and diagnostics. Shared ActorCarriedResources validates
custodian, economic owner, homogeneous resource kind and bounded stack quantity;
the harvest family supplies its own unreserved whole-part contract. Crop accrual
checks the predecessor hand plus the confirmed cell's actual yield delta. No
second hand counter or resource store was added. Lot issuance now uses the exact
execution and delivered offset. Confirmed cell outcomes advance that execution's
output history; hand stock remains solely in the common resource ledger. Terminal
conservation compares that history to exact cargo, not whole-field yield.
HOT/COLD batch and terminal transfers use the shared actor/container transfer;
retirement checks the currently transferred part rather than requiring previously
delivered stock to remain unconsumed. These are not parallel-worker acceptance.

New-cycle farmer selection now uses the same eligible candidate selector as
bakery planning. Admission retains the explicitly selected local resident rather
than rediscovering the preferred farmer. Predecessor lineage keeps its own exact
worker/output/intent history but no longer requires a successor's worker to be
the same person. Existing COLD lifecycle coverage now makes the predecessor's
body UNKNOWN_AFTER_RESTART, starts another farmer, completes that next cycle and
round-trips state without rewriting the predecessor. Existing test consumers were
updated for the already-adopted labour events and valid WORK/FREE schedule;
neither elapsed labour nor schedule validation was bypassed.

Affected field/carry/recovery/codec/architecture checks and adapter compile plus
style/size/debt checks pass in39s. No native/player multi-worker acceptance or
deployment occurred. These are production-wired prerequisites, not the final
staffing feature.

Exact permission rosters are now persisted under the settlement DecisionAuthority,
with stable work-kind tags and strict local-resident closure. Initial policy names
two farmers and two bakers; runtime selection reads those declared IDs, never
derives permission from profession. Bakery affiliation/employment covers both.
Migration removes origin permissions atomically, and policy revocation does not
cancel an already admitted execution. Snapshot schema222 rejects earlier test
worlds; no backward-compatible executor is retained.

Bakery station occupancy is now derived from the family's retained execution:
confirmed output removal releases the machine, even while the worker still owns
the finished bread or a later return. The shared facility admission reads owner
views without interpreting bakery phases. Production start/hydration apply the
same reservation closure; unavailable work still creates no second waiting job.
The focused COLD bakery custody regression now starts the other authorized baker
after confirmed UNLOAD while the first still carries bread, snapshots both jobs,
and completes both independent delivery/task results. Temporary depot-service
refusal does not create a station waiter or discard either cargo. This is domain
evidence, not native/player acceptance.
The field owner now retains independent jobs and exact intent-keyed terminal
histories; no primary-job read projection exists. Domain admission plans both
permitted workers against the preceding immutable successor, excluding sibling
cell targets and reserved depot capacity. Execution IDs use the retained admission
sequence, not the shared plant epoch. Assignment, labour, scenes and closure
read all exact jobs; completing one does not reset field biology or finish a task
with a retained sibling. Fresh snapshot schema223 retains both maps.
An ephemeral production-planner/reducer probe admits two distinct farmers,
advances both to confirmed crop yields in separate resource accounts and
round-trips the snapshot. It is focused domain evidence, not registered-engine
or native acceptance. Main domain compile passes; the adapter and old fixtures
are still being migrated, so the complete project is not build/release ready.
Active physical continuation lookup now binds the exact job, not the shared
site alone. Deferred depot-receipt admission validates its retained exact roles,
not the historical worker's current position; later work cannot invalidate
already-delivered stock. Per-cell generation claims/renewable eligibility,
history retention closure, generic provider/claim composition and native
multi-worker acceptance remain unfinished.

This is not the complete opportunity/claim protocol. The old whole-facility
capacity for station work and pending-start cadence still exist. Per-generation
cell claims, full physical integration, native evidence and deployment remain.
No player-visible two-farmer or live two-baker acceptance
claim. The live47975452 build remains unchanged. Employment identity is now
uniform per resident; the final incompatible schema/ruleset boundary must reject
old saved identities before deploying this fresh-world-only feature.
