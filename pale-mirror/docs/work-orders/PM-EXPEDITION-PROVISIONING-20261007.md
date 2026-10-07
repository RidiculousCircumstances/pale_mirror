# Expedition provisioning, portable resources and cohesive travel

Accepted 2026-10-07. Main implements alone; no subagents. This order follows
the completed, not yet deployed, hive-supply removal. It preserves communal
production, inter-settlement money and existing goods contracts. Residents
do not acquire monetary accounts. Test worlds are disposable.

## Product outcome and nine obligations

1. A reusable unit inventory carries food, tools and materials within declared
   capacity. Custody accounts own quantities; hands/pockets are presentation.
   Personal supplies and work cargo coexist without merging their claims.
2. Provisioning reserves and actually loads food for outward travel, work,
   return and a ruleset-owned margin. Estimate from roster, satiety, metabolism,
   nutrition and route duration; reconsider changed material assumptions.
3. Transport choice follows cargo plus supplies and carrying capacity, not a
   hard-coded distance threshold. Walk when feasible, use an available animal
   otherwise; visibly defer infeasible departures. Never invent capacity.
4. Graybox animal transport uses an actual chest donkey and one mobile resource
   container for merchandise and supplies. Claims/purpose distinguish them,
   never permanent slot addresses. Contract cargo is not edible spare stock.
5. Bootstrap one identified donkey per settlement. Retain home and exact
   reservation through departure/return. Death/loss does not respawn it or
   transfer its load magically. Walking missions have no one-per-settlement cap.
6. Members eat personal or locally accessible expedition supplies. An individual
   hunger decision cannot route them home. The mission owns supply-related rest,
   replenishment, itinerary revision and return decisions.
7. Foreign food always requires a paid purchase. Reserve a bounded expedition
   budget from the sender treasury; use existing resource and financial owners.
   Settle on confirmed receipt, release unused commitments on termination.
8. Separate nutrition, source selection, resource accounting, provisioning,
   transport allocation, trade, mission policy and movement. Integrate through
   typed ports; no concrete-job inspection in shared coordinators and no second
   stock, pose, money, scheduler or physical-custody authority.
9. The group owns continuous route progress, pace and slots with bounded
   cohesion. There is no required leader or leader succession. Local navigation
   may stretch the formation but not leave a member hundreds of blocks behind.
   Casualties reconsider feasibility and preserve exact resource dispositions.
   HOT/COLD/restart retain identity and current progress without observed
   teleportation, historical animation replay or per-tick position WAL.

## Implementation boundaries

Trace and integrate the complete active path: dispatch/admission → reservation
and loading → departure → group travel and meals → paid replenishment if needed
→ witnessed delivery/settlement → return → release and retirement. Owners of
resource receipts and actor/animal incarnations remain authoritative. An animal
reservation is not a load receipt. An estimate is not consumed nutrition.
Projection is not an item transfer. Delivery is not payment by proximity.

Reusable domain policies live behind explicit inventory, food-source, transport
and mission contracts. Group navigation consumes those declarations, not trade
job internals. Balance parameters belong to the persisted ruleset. Extend the
closed type/codec/reference registries when adding durable subjects; reject
old disposable schemas rather than inventing identity during hydration.

Connected failures from the current live inspection are retained evidence:
group members separated by about300 blocks; outbound participants returning to
home depots for meals; settlement4/8 meal continuations tens of thousands of
ticks overdue despite available food. Diagnose and repair active connected
causes. Do not assume provisioning alone explains every traffic hold or TPS lag.

## Verification and completion

Read actual producers, reducers, consumers and retirement before testing.
Focused checks must cover conservation, mixed cargo/supplies, insufficient
capacity/food/funds, competing animal/budget reservations, consumption without
home detours, casualty disposition, formation lag and late/stale receipts.
Exercise new persistence roundtrips and interrupted handoff. Reuse existing
evidence where semantics are unchanged; no speedup campaign or repetitive matrix.

Only claim the complete product after the active path is wired and a relevant
declarative native flow demonstrates provisioning, actual carried container,
cohesive outbound travel, local eating, delivery, return and release. State any
missing evidence honestly; a library-only policy is not delivered functionality.
Build/package verification precedes an authorized fresh test-world deployment.
No new push authority. Record meaningful results and next unresolved seam in
the sole canonical ledger, not another task log.
