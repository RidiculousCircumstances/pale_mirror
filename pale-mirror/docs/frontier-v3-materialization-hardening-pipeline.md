# Frontier v3 materialization hardening pipeline

Status: accepted architecture and product pipeline. It refines the ordering and
exit evidence of F0.6R3, OBS-002, VIS-002, ARC-001 and `MAT-006` through
`MAT-010`; it does not grant an
implementation order, reopen unrelated accepted evidence or weaken the final
M3/release gates.

## Why this pipeline exists

The F0.6R3 human failures exposed a repeatable acceptance error. Individual
movement, crop, COLD, restart and first-visibility receipts were green, while
the natural story still stopped at the last crop, lost the exact worker on
return and reported `farmer is needed`. The missing proof was the seam between
otherwise implemented mechanisms:

```text
canonical process -> HOT body -> safe release -> inactive physical carrier
  -> COLD continuation -> natural return -> terminal result -> successor work
```

Future materialization stages add more difficult seams: a meal temporarily
claims a worker, birth and emergence create exact actors, digestion destroys an
actor into conserved matter, spatial fronts span several chunks, decisions
delegate to downstream work and health changes must follow the same person.
Endpoint receipts or isolated component tests cannot establish those stories.

## Evidence dependency and reuse

Accepted evidence has one of four explicit dependency states:

- `ACCEPTED_UNCHANGED`: every contract and implementation dependency used by
  the evidence is unchanged;
- `NEEDS_TARGETED_REVALIDATION`: a shared dependency changed, while the
  evidence's local result remains valid;
- `PROVEN_NARROWER`: the evidence proves only named intermediate facts and
  cannot support the complete lifecycle claim; or
- `REJECTED`: an exact stronger observation contradicts the claimed result.

A change to actor-carrier, visible-envelope reconciliation, physical
confirmation, resource custody or decision provenance must produce a bounded
impact inventory before dependent acceptance is reused. It does not trigger a
blanket full-suite or native-matrix rerun. A closed composition/registration
guard covers all implementations mechanically, followed by the smallest
representative lifecycle for each materially distinct actor or effect shape.

## Cross-stage natural-story gate

Every duration-bearing or irreversible `MAT-*` family must map one natural
story before its candidate is frozen. The map names:

1. the canonical owner, exact subjects and resources;
2. autonomous admission without player or chunk demand;
3. visible in-progress HOT execution;
4. terminal semantic fact and every successor promised by that lifecycle;
5. ordinary leave, COLD continuation, restart and natural re-entry;
6. exact identity/custody/revision continuity at every boundary;
7. one ordinary player intervention or loss and its downstream consequence;
8. the strongest retained user/runtime contradiction and the oracle that would
   reject it; and
9. which parts are M0, M1, M2 and still-unaccepted M3 evidence.

A carrier that stops before the declared successor is `PROVEN_NARROWER`.
Elapsed time may bound a hang but is never the causal oracle. Fixtures may
establish initial conditions, not create the result. One green terminal cycle
then proceeds to review; no confidence-only repeat follows.

## Ordered pipeline

Current sequence: finish active F0.6R3 revision 22 including its first online
harvest-progress oracle, then extract the reusable
[`OBS-002`](work-orders/PM-OBS002-ONLINE-SEMANTIC-VERIFICATION-01.md) foundation,
deliver the bounded
[`VIS-002`](work-orders/PM-VIS002-SEMANTIC-SCENE-MAP-CAMERA-01.md) visual-tooling
gate before the next player-visible candidate, execute
[`ARC-001E`](work-orders/PM-ARC001E-ACTIVE-REFERENCE-TRANSITION-CLOSURE-01.md),
and resume preserved MAT-006 onward. Non-visual ARC-001E work need not wait for
VIS-002, but MAT-006 product promotion requires both. REL-001, ARC-001, OBS-001 and XACT-001 are
accepted retained foundations, not instructions to repeat their campaigns. The
ledger supplies exact current identities. Pending human M3 may permit
independent audit/preparation, never a false acceptance or new gameplay breadth.
This amendment does not interrupt the current product correction.

### 1. F0.6R3 — exact actor reference lifecycle

Finish the current lifecycle/quarantine correction under its active order.
The earlier REL-001 interruption is complete; its evidence remains reusable.
The same resident and stable Minecraft UUID
must cross last-crop completion, safe live-body release, one inactive carrier,
COLD progress, natural return under a newer physical epoch and declared
successor work. The complete field and both hives must remain current and
ordinary ingress must remain bounded. Direct human success is still required.

### 2. REL-001 — accepted relationship foundation

Retain `frontier-v3-domain-relations.md` without an ORM, database or second
state store. One canonical owner retains each exact relationship; discovery is
limited to admission, continuation follows the accepted IDs, and a bounded
read-only view exposes incoming/outgoing and causal chains. Preserve the migrated
F0.6R3 task/order/job/actor/resource/provision/successor vertical and accepted
classification of other active relation families. ARC-001 extends temporal
retirement consistency rather than replacing this layer.

### 3. ARC-001 — accepted simulation execution alignment

Retain the locally accepted 2026-09-18 execution-model alignment and its exact
ledger identity. Subsequent work reuses its execution authority, projection/
effect separation, temporal relationships and bounded-work mechanisms. It does
not repeat the original inventory or native campaign.

### 3a. OBS-002 — online semantic progress and promotion gate

The current F0.6R3 harvest vertical must first detect its own silent 6/7 stall
or prove terminal success. At its coherent handoff, extract the family-neutral
progress-obligation verifier, bounded semantic stream, first-failure incident
capsule, deterministic replay/first-divergence path and executable graphical
promotion receipt described by
[`frontier-v3-runtime-verification.md`](frontier-v3-runtime-verification.md).
Prove normal terminal progress, lawful typed blocking, unexplained silent stall
and restart/incomplete evidence. Reuse accepted OBS-001 facts; no second store,
remote telemetry dependency, dashboard or all-family native campaign is part of
this gate.

### 3b. VIS-002 — semantic scene map and camera planner

After OBS-002 and before the next player-visible candidate, add the bounded
read-only visual-test capability in
[`PM-VIS002-SEMANTIC-SCENE-MAP-CAMERA-01`](work-orders/PM-VIS002-SEMANTIC-SCENE-MAP-CAMERA-01.md).
It derives a planned/current/observed/unknown top-down map from typed scene
geometry, evaluates legal player-eye poses for clearance, framing, direct
visibility and observer interference, and stores the selected camera/map
manifest beside the actual screenshot. Farms, workshops, caravans, battles and
hives use one point/area/path/group target contract. An impossible view is
explicitly `INCONCLUSIVE`; the tool never relocates the scene, force-loads it or
substitutes its map for review of the rendered frame.

### 3c. ARC-001E — active reference/replica transition closure

After the current F0.6R3 contradiction is closed and before MAT-006 resumes,
inventory every active producer that consumes, transfers, replaces, retires or
releases a canonical reference or physical replica boundary. Migrate each to one
closed typed transition contract whose common canonical transaction atomically
closes the prior binding and establishes one exhaustive successor, terminal,
ambiguity or owner-local-block disposition. Family executors retain policy and
physical effects only; no active mutating `OWNER_EXPLICIT_UNCHANGED` or legacy
path may keep a private lifecycle protocol. Prove inventory closure mechanically
and use only representative distinct-shape ordinary/negative/recovery evidence,
not an all-family native campaign. The planned execution boundary is
[PM-ARC001E-ACTIVE-REFERENCE-TRANSITION-CLOSURE-01](work-orders/PM-ARC001E-ACTIVE-REFERENCE-TRANSITION-CLOSURE-01.md).

### 4. OBS-001 — accepted bounded causal diagnostics

Retain the accepted diagnostic plane: exact-object reasons, causal envelope,
restart-queryable incident index/trace and bounded identity-complete bundles.
OBS-002 consumes those facts; it does not modify their schema into a second
truth or repeat OBS-001 acceptance.

### 5. XACT-001 — accepted shared actor-carrier composition

Retain the accepted closed composition guard over production body creation,
adoption, retention, release and recovery. Later stages apply only dependency-
driven targeted revalidation when this boundary changes.

Each producer/adopter stamps the complete canonical actor ID + nominal actor
kind + lifecycle owner + stable Minecraft UUID + representation + authority
epoch/revision tuple before admission. The composition validates that exact
declaration and inventory completeness; it never infers a missing dimension
from entity/runtime class, UUID lookup, naming, position/proximity, membership,
assignment, payload shape, sole match or fallback.

Update affected evidence dependencies explicitly. Reuse F0.6R3 and accepted
MAT evidence, then apply only targeted revalidation to materially distinct
shapes: one singular resident duty, one multi-resident operation and one
multi-bioform operation. A separate native proof is required only when a
changed production path is not already exercised by identity-bound retained
evidence. This gate neither reopens every MAT row nor authorizes a broad matrix.

### 6. MAT-006 — provision, duty return and birth

`MAT-006` closes three inseparable outcomes:

- an exact recipient physically consumes one exact allocation and receives
  the one corresponding nutrition change;
- a temporary feeding claim releases at a retained checkpoint and the same
  resident resumes the prior or declared successor duty without replacement;
- a reserved future child crosses one atomic canonical birth/emergence
  boundary into exactly one resident, stable UUID and live body or inactive
  carrier, including restart on both sides of the boundary.

One natural household story must include serving, consumption, release, duty
continuation and birth/re-entry. Batch presentation may aggregate servings but
may not substitute one recipient's physical action for another's result. Split,
merge, player removal and hopper movement at the serving surface retain the
exact sum of confirmed, remaining and externally held allocations.

### 7. MAT-007 — mixed-mode metabolism and irreversible morphogenesis

Close these distinct boundaries before one combined M2 claim:

- an exact nutrient allocation crosses a retained multi-segment route through
  a mixed partially HOT/partially COLD envelope without endpoint teleport or
  duplicate source/target custody;
- living-captive digestion has one irreversible confirmation state machine in
  which rescue, living actor and biomass output cannot coexist;
- a future bioform identity becomes exactly one canonical actor plus live body
  or inactive carrier at emergence, never a replacement selected on load; and
- an organ remains visibly partial and non-operational until its entire
  required footprint and vascular/synaptic connection are confirmed current.

Crash/restart evidence brackets every irreversible boundary. Damage, theft,
rescue and partial geometry affect the same retained lifecycle.

### 8. MAT-008 — complete envelopes and spatial fronts

Separate a facility envelope from a dynamic frontier. A fixed facility may not
claim `CURRENT` or operational capability while a naturally exposed required
sibling chunk is missing, pending or conflicting. A dynamic crop, infection or
root frontier may intentionally be partial, but its completed, active, pending
and damaged units must be truthful and share one process revision.

The reusable visible-envelope gate is exercised through natural approaches
from at least two materially different boundary directions, partial sibling-
chunk availability, reversed observation order, player modification, ordinary
leave/re-entry and restart. It loads no sibling chunk and never repairs foreign
state. This is the recurrence guard for the missing field and invisible-organ
failure class.

### 9. MAT-009 — every decision reaches owned consequences

The criterion matrix must close every declared decision class through its real
objective, exact downstream task/operation, actor/resource/facility owner,
terminal or blocked fact and delayed consequence. Native scenarios may sample
one representative per distinct physical presentation family; that sampling
does not allow unexecuted matrix rows to inherit a green result.

Successful policy state with missing workers, unavailable custody or no owned
downstream action is red. Refusal and unavailable alternatives are first-class
rows, not diagnostic-only branches.

At least one controlled same-perceived-facts comparison must show that distinct
registered settlement or hive policy versions select materially different
allowed physical responses while retaining the same canonical decision
protocol. A deterministic trace with policy labels but identical hard-coded
outcomes does not establish first-class AI behavior.

### 10. MAT-010 — the same person's condition and recovery

Prove one natural same-person story: condition changes while unobserved, the
same canonical resident and UUID return with current composed cues, ordinary
physical treatment consumes the exact resource once, recovery advances and the
resident resumes eligible work. HOT contact and COLD exposure may not both
apply the same interval. Cue precedence/composition is deterministic so one
adapter cannot hide another current condition.

Missing, stale or mismatched actor-carrier evidence is a typed local ambiguity,
not permission to show a replacement body or an obsolete healthy cue.

### 11. Release hardening before cutover

The following remain explicit release blockers after MAT closure:

- close `V3-AUD-055` with bounded graceful shutdown overlapping authentic
  natural generation, or an independently reviewed fail-safe/platform fix;
- close the natural-terrain provider gate at `COMPILED`, `SETTLED` and
  `RELOADED` over slopes, entrances, support, multichunk facilities and damaged
  routes; flat graybox evidence is not substituted;
- pass the sixty-minute Wave-7 profile while retaining per-stage caller-path
  budgets for ingress, projection, navigation and diagnostics; TPS alone is not
  evidence; and
- prove source/packaged-JAR/runtime reachability and a clean new-world run both
  before and after v2 removal, so deletion cannot reveal a hidden v2 callback
  dependency.

## Product-check cadence

After `MAT-006`, `MAT-007`, `MAT-008` and `MAT-010`, one exact deployed
candidate receives a small ordinary-player product probe of the newly visible
family. It checks that the natural event exists, progresses, survives return
and is understandable at the claimed level. A failure becomes that stage's
strongest contradiction; it is not answered by more aggregate tests.

Before that human probe, the OBS-002 promotion gate must hold a complete
machine-readable terminal receipt for every declared duration-bearing part of
the same story. An intermediate frame, endpoint, timeout or `INCONCLUSIVE`
obligation cannot be offered to the user. The product probe remains necessary:
the runtime verifier rejects contradictions but cannot infer unbriefed-player
comprehension or M3.

The same probe also uses VIS-002's reproducible semantic camera/map manifest.
If required targets cannot be framed without occlusion, unknown geometry or
observer interference, the candidate remains `INCONCLUSIVE`; manual coordinate
guessing or moving the scene is not a substitute.

These probes prevent all visual/product feedback from accumulating at Wave 7,
but do not claim final M3. The final unbriefed clean-room, co-op, choice,
pacing, natural-terrain and delayed-consequence gates remain separate and must
still pass before production cutover.

## Verification economy

- Changed/failing cheap semantic, codec, registry and architecture lanes run
  before a frozen native candidate.
- A repeated failure along one causal chain triggers a whole-boundary audit,
  not another patch/rerun loop.
- Existing identity-bound evidence is reused at its honest dependency state.
- A complete native matrix uses the existing parallel workers only when the
  stage actually requires that matrix.
- No stage proves acceleration infrastructure, diagnostics, dashboards or
  test timing as a substitute for the product outcome.
- One green terminal cycle proceeds to independent review. Extra runs require
  a concrete unresolved product decision that cheaper evidence cannot answer.
