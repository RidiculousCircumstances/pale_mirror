# R244 — ordinary simulation review cost

Scope: reduce confirmed active v3 computation, preserving the existing world and
economic decisions. No new cash, timing policy, schema, cadence or cache TTL.

A 45-second live JFR on runtime Java PID3357881 (R243 code da19c0e7d)
contained 357 server-thread and 623 scheduled-preparation execution samples.
About133 server samples included export economic evaluation,132 automatic-station
offers. These are overlapping inclusive counts, not additive percentages.
A simple idle machine check could evaluate domestic supply, buyers' domestic
plans and transport/provisioning alternatives on the Minecraft thread.
Navigation workers contributed12+9 execution samples; they were not the dominant
CPU owner in this COLD interval. Native-poll samples were excluded.

Changes:

- A single-item production query first checks its downstream recipe/fuel closure.
  Without any live BUY envelope for that closure, domestic production is already
  the exact answer; unrelated export evaluations are unnecessary. Any possibly
  relevant quote retains the original full export allocator and competing uses.

- Station reviews enter the existing detached immutable preparation capability only
  when the machine owner reports an eligible economic-demand assessment. Cheap
  progress, unavailable inputs and ongoing operations stay inline.
  Priority, schedule cadence, deterministic event validation and commit owner stay.
- Shared production reachability computes an optimistic material closure from
  releasable stock and recipes remaining after domestic allocation. An impossible
  product cannot trigger counterparties' planning. Reachability is not admission:
  labour, fuel, quantities, profitability and allocation remain with the solver.
- A buy quote below seller ask is rejected before evaluating its buyer. Adding
  freight cannot make it affordable. An envelope below one item's price stops
  before journey and crew calculation.
- Fuel checks short-circuit absent backed inputs, unavailable station/custody or
  absent fuel before requesting broader ingredient/economic work.
- Crew provisioning presence uses the shared eligible-container index rather than
  repeatedly sorting warehouses by distance for each hypothetical crew.

Evidence retained locally under build/r244-*: live JFR, copied immutable world
snapshot and a probe calling the actual station planner at12 distinct instants
per station (2warmups,10measurements). Compare exact encoded event hashes, not
only elapsed time. Initial optimization comparison: all36 hashes equal;
sum of per-station medians59.750→44.911ms; worst station46.692→34.858ms.
These figures predate the final unranked crew-stock change and do not establish
whole-server catch-up speed. No wall-clock assertion was added to unit tests.

Focused verification passed: 195 tests/33 classes, 0 failures, 0 errors, 0 skips; guardrails passed. Existing station recovery/physical-custody tests and
new assertions cover identical prepared/inline events and absence of a background
round trip for a running machine. Economic tests compare full export allocation
with pruned allocation across stock, fuel and upstream-capacity combinations.
No per-method wall-clock assertions or synthetic world-speed claims.

Final frozen-image comparison (including query-specific planning and unranked
crew stock): all36 encoded event hashes identical; summed medians59.750→14.960ms
(about75% less), worst station46.692→4.299ms (about91% less). These are local
same-input measurements, not a blanket server speed multiplier. All144 single-item
queries across12 settlements equal the full economic plans on that snapshot.

Final verification, deployment and live ordinary-debt measurements pending.
