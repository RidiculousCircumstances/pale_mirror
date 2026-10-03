# Production stations and actor item-handling vertical

Status: implementation in progress. This contract replaces the current bread
job's depot-slot wheat-to-bread replacement; it does not assert that the new
vertical is already live or accepted.

This is a fresh-world cutover. Existing v3 worlds are disposable test worlds:
new durable bakery phases and custody records increment the snapshot/WAL format
and reject old bytes. Do not implement compatibility migration or infer missing
bakery state from an old job. Reset only the exact task-owned test world during
an authorized deployment, after verifying its target and preserving evidence.

## Ownership

- Settlement demand/market assigns a bread task. The exact baker owns its
  execution and response to an unavailable input, obstructed route, interrupted
  body or full destination. The bakery station holds and processes resources;
  it is neither their economic owner nor an AI or a second job authority.
- The owning work issues a durable, typed item-handling order. A reusable
  executor performs movement and observed `take`, `carry`, `place` or `retain`
  steps for that order. It does not choose recipes, demand, replacement workers,
  another source, or a strategic destination.
- In the current six-building graybox, the existing workshop footprint and
  its declared service port are the bakery facility for this bread vertical.
  Its player-facing name is BAKERY. This does not create a second overlapping
  building or make the facility the job's decision-maker; a future non-food
  workshop must have a distinct declared function before sharing that site.
- Each order names its actor, exact item/lot allocation and quantity, typed
  source and destination, allowed hand, semantic stations, phase, owner and
  authority epoch. A planner may discover a source before admission; an active
  order never reconstructs its owner/type from an ID, location or inventory
  membership. One item quantity has one canonical custodian at every phase.
- A depot slot is the first endpoint adapter, not the type of every item goal.
  The reusable domain order declares endpoint kind and exact identity; its
  executor cannot assume all later sources are containers or all destinations
  are depots. Production-station input/output inventories are first-class
  endpoints of the same contract, not special baker-only hand logic. One generic
  custody transition closes the previous binding and
  establishes the successor or a typed local disposition atomically.
- The shared movement contract carries the current semantic station goal;
  Minecraft navigation supplies only a bounded HOT trajectory. COLD advances
  the same order and actor position under known geometry. No materialized
  worker may be teleported or have its work advanced by a COLD driver.

## Production-station rule

Every resource-processing recipe has an explicitly declared physical station
with bounded input custody, output custody, recipe/capability and work state.
An actor may transport, load, operate and unload it, but cannot transform
ingredients in a carried hand, at a remote depot or merely by arriving at a
work coordinate. The economic owner of the resource remains the declared
owner (currently the settlement) throughout carrying and processing unless a
separate explicit transaction changes it. Custody changes at each handoff:
source container → actor → station input; the recipe consumes station-held
input and creates station-held output; station output → actor → destination.
Intermediate recipes can connect multiple stations using the same transfers.

The graybox must materialize a usable station with a physical input/output
holding surface and a visible processing state. A decorative block, sign or
ordinary Vanilla crafting table without persistent input/output inventory
does not satisfy station custody. The domain names the recipe and progression;
a current HOT provider may use Vanilla or a modded mechanism only as the
observed physical executor. COLD operates on the same retained station
accounts and work clock; a naturally loaded station with current physical
custody excludes a competing COLD inventory writer even without a player.
Station damage, stolen input/output, full output, missing capability and
interrupted processing have typed owner-local outcomes. No generic station
adapter may silently bypass the machine or deposit its output remotely.

Station identity, facility membership, supported recipe/capability, input and
output ports, capacity and authority epoch are explicit typed declarations.
The currently named workshop service port locates the bakery actor, but does
not by itself confer resource custody or a recipe capability. A station must
have its own canonical resource account and physical binding; it may be inside
the workshop footprint. Its machine socket is a physical block position
distinct from the baker's walkable loading/work/unloading station; a chest or
machine must not be placed in the actor's body cell. Input/output ports may
share one block entity if its bounded slots and transitions remain distinct.
The first bakery job retains the same baker operating at that station during
its labor interval; autonomous unattended processing is a separate declared
capability, not inferred from the presence of an oven. Loading, processing
and unloading never infer a
station from the actor's coordinates or a nearby block. A recipe started with
one station cannot silently finish at another. Multiple jobs may share a
station only through an explicit capacity/exclusivity policy; the first bakery
adopter may reserve the whole machine to one job.

## First adopter: bread

One accepted bread task retains this observable sequence for the same baker:

1. Go to the depot's declared pickup station and physically take the reserved
   wheat into the baker's visible carried hand. The source slot and hand change
   under one confirmed custody transition, never merely because the body is
   near the chest.
2. Carry the wheat to the bakery's declared loading station and place it into
   that bakery station's physical input. Confirm the hand-to-station custody
   receipt before recipe work may consume the input.
3. Operate the bakery station for the retained labor clock. Only its confirmed
   station-local recipe effect consumes the station-held wheat and creates
   station-held bread in the output; no hand or depot transformation is legal.
4. At the declared unloading station, physically take that bread from the
   station output into the same baker's carried hand under a confirmed receipt.
5. Carry the bread back to a declared depot deposit station and physically
   place it in available storage. Only this confirmed delivery completes the
   bread task and makes the output available to later food/provision consumers.

Delivery ends the job's exclusive resource custody immediately: the deposited
bread is ordinary settlement stock even while the same baker's retained return
journey and task finalization are still open. Consumers may split, move or eat
it during that journey; a job-state invariant must not pin the delivered batch
to its original depot account or original quantity after the delivery receipt.

`OUTPUT_READY` means the recipe's labor is complete and the bakery station
holds bread; it is not hand pickup or job completion. The market order, payment
and strategic task settle
only after the deposited output is confirmed. Preserve the current graybox
recipe quantity, 64 wheat to 64 bread, in this migration; a later balance or
Vanilla-recipe change is separate and must change claims, prices and lineage
together. A worker's death or loss blocks the exact retained job locally; a
replacement baker requires an explicit profession/assignment transition,
never a silent fallback from an industrial worker.

The exact same phases, worker, item accounting and semantic goals survive
HOT/COLD transitions and restart. In COLD, abstract transfer at an admitted
station is allowed only after the same conserved custody transition; it is not
a deferred Minecraft write to replay on first visibility. A loaded source or
destination retains physical authority even without an observing player.
When observation demand disappears, a baker carrying an observed item must
close the HOT scene and hand binding against that same loaded body in the same
server turn; a generic grace period may outlive chunk retention and strand the
job. The closed scene alone is not permission to advance COLD: source and
station container leases must also release, and the next route must be
available. Fast-forward verification waits for these exact read-only readiness
facts before advancing the canonical clock; it must not spend thousands of
virtual ticks while a physical owner still blocks the job.

## Failure and recovery

Source depletion, foreign item, full destination, blocked station input/output,
worker injury/death, blocked path, player theft, hand mismatch and lost physical evidence have
typed local dispositions. They cannot silently teleport stock, select an
unassigned replacement or cancel a possibly applied physical effect. Each
physical transfer has a durable-before-effect intent, an inspectable actual
postcondition, a confirmed receipt and a bounded ambiguous-restart path.
Closing or transferring the worker's scene requires exact hand/cargo closure;
station custody persists under the job and does not follow a departing actor.

Before pickup, a witnessed withdrawal or same-kind substitution must not cancel
the accepted bread task or replace its baker. Only that pickup pauses while the
original claim is released and an available, unclaimed 64-wheat allocation
owned by the settlement is explicitly rebound to the same job. A physically
identical stack may be used after this accounting reconciliation; item-kind
equality alone cannot silently confer settlement ownership on foreign player
stock or bypass the claim. If no eligible wheat remains, retain the job and
show a local `WHEAT CHANGED` reason; resume when eligible stock is available.
No source reallocation is allowed after a possibly applied pickup intent without
an explicit physical resolution. Station or destination obstruction likewise
retains the order, actor and cargo with a visible local reason and clears only
on an observed reversible condition.

Storage exhaustion is a reversible capacity condition, not a recipe failure or
reason to quarantine the world. Without an available destination, the planner
does not admit a new harvest or bread batch. If capacity disappears during an
accepted job, retain the exact worker, task, station/hand custody and output;
do not destroy or teleport it. Pause its destination handoff, explain
`STORAGE FULL` on the affected player-facing work surface, and retry when
capacity returns. A settlement with no expansion capability remains paused;
future expansion is a separate capacity-creating action, never an implicit
overflow or magical storage fallback.

## Adoption gate

Implementation order for this cutover:

1. Introduce a versioned station record and input/output custody bindings,
   plus a real graybox physical station provider. Reject old snapshot/WAL
   formats at the fresh-world boundary; do not invent a migration.
2. Extend the shared item-order endpoint type and physical transfer adapter
   to distinguish depot and station input/output. Prove observed handoffs and
   capacity without baker-only inventory writes.
3. Retain the five baker phases and their exact custody in the durable job;
   make only the station-local recipe transition eligible for the labor clock.
   Keep economic ownership and claim lineage through every phase.
4. Route HOT and COLD through the same semantic station goals and exclusive
   physical authority. Confirm each loaded effect through an intent, actual
   postcondition and receipt; restart cannot repeat a possibly applied step.
5. Remove the active depot-slot transform and any actor-hand recipe shortcut,
   then verify the whole product story once on a fresh world.

Remove the old active direct depot-slot production transform in the same
cutover as baker admission; it may not remain as a second successful path.
Verify depot pickup, station loading, visibly station-local work, station
unloading and depot delivery with distinct custody receipts. Test source,
station and destination interference, HOT/COLD handoff and restart on the actual bakery
job. The product claim requires an ordinary player/client visit, not only
domain or isolated GameTest success. Do not deploy a partial vertical as the
new bread process.
