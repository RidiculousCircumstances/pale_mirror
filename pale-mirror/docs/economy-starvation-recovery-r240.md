# R240: starvation and retained execution recovery

Observed on retained R232 world, deployed `e764f89d7`, 2026-10-24.
At canonical tick 1097073, 322 of 359 residents were hungry. Clearwater held
1064 wheat and no coal; Stonefield held 483 wheat and 83 coal, yet its bakery
was at STATION_LOAD with INSUFFICIENT_HEAT. The snapshot is fresh; this is not
an observatory presentation artifact. Evidence: local `build/r240-*` receipts.

Confirmed blockers:

- Seven Stonefield residents retained UNKNOWN_AFTER_RESTART custody and pending
  actor adoptions. Their meals reserve all seven remaining bread. Courier 2-6
  retained a meal due at 185253 and a fuel pickup obligation.
- Courier 2-8 retains a physical pickup awaiting its receipt since tick 627887.
- Hearthvale courier 6-6 and buyer-collection caravan member 5-6 remain near the
  same station storage port. Exact navigation/service cause is still under
  investigation; `other_context` in the old diagnostic was not a path result.
- The bakery supply planner correctly counts committed incoming fuel. A stalled
  shipment therefore suppresses replacement supply; duplicating those shipments
  would not repair their execution or reservations.

The common body controller already accepts exact positive disk evidence, but
only scene recovery requests that inspection. Ordinary residents without a
scene have no corresponding missing-unload-callback recovery path. R240 connects
bounded existing actor probes to one asynchronous batched body census. It reuses
the shared saved-body proof, checks UUID/epoch/residence/owner and current native
read/write fences, and publishes a saved departure through the existing body
controller. Activity owners still settle their own effects and release scopes.
Missing or duplicate stored bodies never grant reconstruction or COLD authority.
The probe does not force-load chunks, cancel meals, discard cargo or invent food.
Unresolved evidence remains visible, with retries driven by changed evidence.

Read-only diagnostics now inspect all registered movement providers without
starting searches. Economic reports keep core stocks/financial facts and budget
optional plan/reserve/price sections explicitly, instead of replacing oversized
reports with an opaque response-limit result.

This is not yet proof that the whole economy has recovered. Verify retained
fuel deliveries, bakery output, meal completion and trade after deployment;
then assess remaining economic causes. No reserve/price balancing or world reset
has been used to hide execution failures.

R240b narrows Hearthvale's obstruction: the service point is held by resident
6-24, carrying one coal, waiting for ENDPOINT_HANDOFF. The destination fixed
storage `container:structure-6-smeltery-metals` is PREPARING epoch 5 while its
chunk is unloaded. Native saved block-entity data contains 53 coal at its declared
address. The short route to the port is available; the service turn is occupied.
A no-load recovery path now flushes pending terrain writes asynchronously, reads
one saved column, rejects any loading/unloading or intervening write, and decodes
its declared inventory through the existing provider. Only matching fingerprint
and provenance can finish the existing custody boundary. Missing, changed,
foreign or duplicate evidence never grants release or writes native slots.
The existing bounded custody window discovers candidates; there is one read in
flight and unchanged failed evidence is not polled repeatedly.

Storage approach admission also requests regional connectivity rather than a
complete distant cell itinerary. It preserves pending/no-path distinctions and
keeps cargo and crew unassigned while the selected endpoint is unproved. This
change does not yet remove the separate full expedition journey proof.

Verification: 17/18 existing consolidated trade cases passed before updating the
one assertion requiring the old detailed endpoint query; its corrected focused
case passes. Guardrails pass. Native reference-projection slice: 14/14 pass,
including exact saved inventory decoding, mismatching contents, duplicate
identity and absent-image rejection. An initial test fixture wrongly marked a
fresh projection ACTIVE; corrected to the same UNMATERIALIZED lifecycle as the
existing native projection tests. Live recovery/economy results remain pending.

R240a's saved-body census reports seven Stonefield bodies absent from this
dimension. No body is recreated from absence. Their old adoption fences and the
separate pending shipment pickup still require exact recovery evidence; the
shared census hook alone has not repaired those residents.

R240b live revealed the second valid recovery outcome: the Hearthvale stored
53 coal exactly matches the retained preparation preimage
`sha256:8f3af2923b0f729c578e4d9dd72d6cd8779f5bebfe920cc3733d560eeb89acd3`,
while canonical 51 coal matches the pending projection. Thus the write had not
replaced its predecessor. R240c adds a typed deferral at the existing replica
owner: fresh unloaded predecessor evidence withdraws that presentation request,
retains the canonical inventory unchanged and retains the actual physical image
as the next visit's predecessor. A later normal visit acquires fresh authority
and projects current stock. It cannot defer an active resource effect, live
fungible slot bindings, a different provider/epoch/image, or missing evidence.
No economic rollback, repeated pickup, native rewrite, or fabricated receipt.
Native verification covers deferral, duplicate/stale deferral rejection and the
next visit's successful catch-up, alongside the existing projection cases.

Retained-world result after R240c (no reset): at tick 1124617 the exact
Hearthvale fixed storage became RELEASED with its saved predecessor retained;
shipment 6-24 left the active registry. The buyer-collection mission
`fd5197d6ee387f6c7a1045d30f281ce5` loaded coal and became OUTBOUND. Hearthvale
received 28.554570 currency units; the payer balance fell by the same amount.
The bakery fuel courier subsequently delivered eight coal, the bakery's
INSUFFICIENT_HEAT blocker disappeared, and its retained job reached
DEPOT_DELIVERY at tick 1129809. This is progress, not proof of feeding every town.

A second confirmed inefficiency is logistics preflight ordering. On an immutable
copy at tick 1125214 Clearwater requested 55 successive distant endpoint searches
without admitting a mission. Completed requests had connected regional results;
the eventual material rejection occurred after route selection. The chosen
starving crew lacked even its current meal requirement, before travel consumption.
R240d adds a shared provisioning prerequisite before endpoint discovery: current
nutrition, each member's own unclaimed personal food, usable sponsor warehouse
food and the selected real asset's stock. Personal excess is not silently shared.
This is only a necessary condition with zero travel consumption; full duration,
capacity, route, actual source access and reservation checks remain authoritative.
On the identical snapshot the decision now completes without any route request:
WAITING_FOR_AVAILABLE_TRANSPORT_OR_PROVISIONS. No food, money or departure proof
is invented. The expensive impossible trip no longer competes with executable work.

Stored-container recovery logging also distinguishes a matched image whose
transition awaits command durability from an actual image mismatch. Previously
that asynchronous success path could emit SAVED_IMAGE_DIFFERS misleadingly.
The seven historical absent Stonefield bodies and its pending physical pickup
remain unresolved; neither absence nor an empty operation receipt is authority
for recreating bodies, clearing cargo or replaying effects.
