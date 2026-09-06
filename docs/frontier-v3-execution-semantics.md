# Frontier v3 execution semantics and player guarantees

Status: accepted normative correction; implementation and evidence remain staged
under F0. This document does not certify current code or close any audit finding.

The product contract owns the promise, `architecture.yml` owns boundaries and
invariants, and `frontier-v3-seamless-foundation.md` owns implementation order.
This document specifies their shared HOT/COLD acceptance semantics. Historical
implementation descriptions are not exceptions to these requirements.

## Player promise and limits

The world develops without the player. Settlements, the hive, people and
resources retain their identities and history. On approaching, the player sees
current activity and can intervene through ordinary Minecraft actions. Leaving,
returning and restarting do not themselves create resources, reset work or undo
confirmed consequences.

This promises continuity and causality, not an invisible tick-for-tick Minecraft
world. COLD does not simulate every collision, projectile or block update, read
unloaded chunks, run arbitrary unattended mod machinery, or advance while the
server is stopped. No-force-loading and normal Minecraft loading/ticking rules
remain binding. Graybox placeholders must still make real processes readable.

## 1. Shared semantics, different physical detail

Every family declares its comparison contract before implementation acceptance:

| Boundary | Required comparison |
| --- | --- |
| Identity and accounting | Exact actors, ownership, quantities, allocations, custody and once-only effects; no tolerance. |
| Process continuity | Exact goal, commitments, completed stages and retained unfinished work at hand-off; no second cursor. |
| Quiet production | Same labor norm, inputs and outputs under equal conditions; elapsed-time differences require declared bounded navigation/scheduling causes. |
| Travel | Same semantic destination, legal retained route/checkpoints and known obstacles; local physical trajectories need not match. |
| Combat | Same legal weapons, defense, tactical constraints and known terrain semantics; calibrated success, loss and duration distributions, not identical hits. |
| Recovery | Exact confirmed consequences and exclusive custody; explicitly classified in-flight and ambiguous effects. |

For a fixed canonical input stream, replay remains deterministic. HOT physics
supplies additional inputs, so full HOT/COLD world digests need not match.
Statistical calibration never relaxes exact invariants within any individual
run. Compare like initial conditions, knowledge, policy/ruleset and providers.
Declare seed sets, sample sizes, metrics, tolerances and statistical decision
rule before measurement; retain failures, do not tune bounds to obtain green.

Neutral observation is different from a new physical input: a player blocking
a door, hopper transfer, fire or another live entity may legitimately alter an
outcome. Merely changing mode must not reset labor, stage timers, attack
cooldowns, wounds, target commitments or keyed random opportunities. Repeated
arrival/departure must confer no systematic advantage over declared calibration
bounds; one final endpoint comparison is insufficient proof.

## 2. Presentation and physical interaction are independent inputs

Presentation demand determines where detailed visible activity is required.
Physical interaction eligibility determines which objects can currently be
affected through Minecraft. The adapter reports both from naturally available
world state; player absence is not evidence that physical custody is absent.

A ticking chest/hopper, fluid, fire or mod mechanism may interact without a
spectator. The smallest affected container, actor or effect retains one fenced
physical owner while that interaction is possible. Its COLD counterpart cannot
spend or change the same resources concurrently. An actor can be COLD while its
depot has physical custody; a physically active depot does not make the entire
settlement HOT. No observer-independent mechanism may duplicate a scene lease.

Zero aggregate presentation demand plus hysteresis permits a release attempt,
not automatic release. Verify physical eligibility, checkpoint outstanding
effects and fence the old owner before COLD acquires the affected scope.
An unloaded serialized replica alone is not an active physical owner.

## 3. COLD knowledge is explicit and bounded

Canonical authority is not omniscient AI knowledge. Distinguish known geometry
and observed changes, stale/inferred information, and unknown preconditions.
Each process-family descriptor declares evidence source, spatial scope,
revision/validity, invalidation, and its permitted conservative approximation.
Settlement and hive policies use their own perceived knowledge, not arbitrary
global state. A known broken bridge or player wall remains binding in COLD.

For an unknown significant precondition, use a bounded inspect/scout/replan
policy with a named evidence provider and retry budget. A pure COLD scout cannot
read an unloaded Minecraft region or convert elapsed time into exact evidence.
If fresh evidence is unavailable without loading, the family must explicitly
choose a permitted approximation, autonomous alternative, or visible abandonment
reason. Waiting for a visitor is not the universal autonomy strategy.

Approximation must record what was assumed. It cannot override a known change,
mint provisional production capacity into an irreversible dependent chain, or
assert exact damage through unknown shielding. A confirmed obstruction remains
until contrary evidence or an explicit canonical replan changes the route;
switching mode cannot erase it. Local navigation never invents a strategic route.

## 4. Deferred aftermath preserves causality, not a replay

A COLD event commits only consequences justified by valid known facts and the
family's declared approximation. It retains a bounded, versioned, chunk-indexed
aftermath record with cause, event time, evidence provenance and preconditions.
Observation time is separate: late evidence is recorded now and references the
earlier cause; it does not backdate the canonical command stream.

On natural loading, inspect current world evidence before writing. Reconcile
current damage/crater/infection before admitting managed interactions; never
replay an old projectile or explosion. No player/settlement/hive protected zones
are introduced, but an old footprint is not blanket permission to destroy
unknown blocks. Missing evidence that a wall postdates a blast is not evidence
that the blast could penetrate that wall.

Unknown shielding must be handled before claiming an exact casualty behind it.
Constructive capacity remains unavailable to irreversible dependent work until
its required validity is established. Late contradiction isolates/replans the
smallest affected footprint or process; it does not rewrite confirmed unrelated
history or silently overwrite the player's construction. First-visibility work
is bounded; unresolved evidence blocks only its affected managed interaction,
not all world progress or all player movement.

## 5. Scene boundaries are not gameplay boundaries

Scenes are bounded execution leases, not isolated arenas. Projectile flight,
blast, pursuit, cargo transfer and reinforcement can cross fronts and HOT/COLD
boundaries. Disjoint custody does not prohibit causal interaction.

Use one canonical ordered interaction protocol with an exact cause ID, source
and target owners, expected revisions/authority epochs and bounded affected
scope. A registered interaction coordinator acquires or hands off the required
authority and records each consequence once. It is not a second simulation,
global scene lock or unbounded search. Retry, stale epoch, interrupted hand-off
and reversed observation arrival must not duplicate or suppress an effect.

An unavailable target uses its declared knowledge/aftermath policy, not a
second COLD hit in addition to the same physical hit. Transfers preserve exact
actors, cargo, pursuit intent, progress, health, cooldowns and random-stream
position. Tests exercise both directions, mixed modes and restart at the
boundary. Existing first-class AI and distinct tactical/individual policies
remain canonical; the coordinator supplies no strategic purpose of its own.

## 6. Confirmation and crash recovery have an explicit boundary

Distinguish prepared intent, physically observed result, recoverably confirmed
consequence and ambiguous crash window. A flushed intent establishes authority
to attempt an effect, not proof that the effect occurred or was saved everywhere.
Confirmation requires recoverable evidence across every participating canonical
and physical custody owner. WAL, chunk NBT and player inventories are not one
atomic transaction merely because a receipt or fence exists.

Every irreversible family specifies its actual save/acknowledgement boundary,
idempotent postcondition inspection and reconciliation protocol. Do not blindly
replay effects or roll back arbitrary external/player inventories from a WAL.
Only explicitly reversible, unconfirmed pose/checkpoint state may roll back
after a hard crash; ordinary save/restart retains process continuity. If the
provider cannot support a requested confirmation guarantee, keep that boundary
unproven and change the protocol, not the definition of a successful test.

Ambiguous custody isolates the smallest asset and prevents dependent spending
or duplication. Inspection/retry/escalation has a bounded policy and visible
reason; unrelated actors and processes continue. If evidence cannot be obtained
autonomously, do not pretend the asset is resolved: retain local uncertainty
and a declared recovery path without making the whole operation/world wait.

Crash tests cover both sides of intent persistence, physical application,
physical/player save and canonical confirmation, including partial transfers
and late stale bindings. Graceful restart alone is not hard-crash evidence.

## Implementation and evidence activation

At the next safe boundary of the current F0.VA measurement, the implementation
agent must reconcile descriptors, assertions and outstanding audit exits with
this document before starting the next implementation slice. Do not kill a
running measurement, discard harvest WIP or restart the programme as v4.

Record a short family-to-requirement gap map: implemented and proved,
implemented but unproved, or planned under the slices below. Attach an owning
slice, comparator and negative/recovery case to every gap. This alignment is
mandatory before product F0 resumes; it is not a new feature wave.

| Existing slice | Additional mandatory exit coverage |
| --- | --- |
| F0.V / SDK | Versioned exact/statistical comparison policy, knowledge and custody capability declarations; infrastructure conformance is not family completion. |
| F0.1 | Partial labor/stages/timers retained through repeated hand-offs; preserve its existing traversal-only scope before F0.2. |
| F0.2 | Independent presentation/physical eligibility, stale/unknown world evidence, constructive dependency safety and causally valid aftermath. |
| F0.3 | Observer-free physical transfers and exact conservation at physical/player durability boundaries. |
| F0.4 | Cross-front effects/pursuit/transfers, unknown-route policy and existing first-class tactical/individual AI ownership. |
| F0.5 | Confirmed/in-flight/ambiguous classification, narrow permissible rollback and partial-save crash windows. |
| F0.6 | Per-family calibration plus continuity, player causality and rapid-switch anti-exploit evidence. |

Persistent clients, cached builds/evidence, selector, event barriers, four CI
workers and immutable fixtures remain required; the accepted monorepo gate
keeps its existing order. Version comparator/assertion meaning in evidence
identity. A changed semantic contract invalidates affected cached acceptance;
unchanged older evidence remains evidence only for its original boundary.
Re-prepare builds only where their actual fingerprint inputs changed. Never
reinterpret an old green run as proof of the stronger contract.

Acceptance has three pillars: exact continuity, lasting player causality and
no systematic switching advantage. Prove domain/codec cases, focused physical
negative/recovery cases and selected real-client flows separately. M0 canonical,
M1 endpoint, M2 continuous execution and M3 unbriefed player comprehension stay
distinct. No numeric speedup or passing test substitutes for the player promise.
