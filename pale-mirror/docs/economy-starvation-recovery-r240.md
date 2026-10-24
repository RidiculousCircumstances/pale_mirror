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
