# Create scheduled-train end-to-end acceptance

This is a real-runtime test, not a mocked `LogisticsRouteObservation`. It proves that PM observes one native Create train at both named stations and only then changes canonical route capacity.

1. Start a server with the pinned Create `6.0.10` profile and run `/pale_mirror adapter status`. `pale_mirror:create_logistics` must be `AVAILABLE`.
2. Let PM observe a vanilla or Integrated Villages settlement: put at least two villagers near a bell or beds, approach it, then use the bell/bed. This binds First Living Region without placing a PM settlement template.
3. Approach both mine coordinates reported by `/pale_mirror logistics status`. PM creates only its two controlled mine templates after the relevant chunks are already loaded.
4. Discover the settlement, advance the simulation until Mine17 is infected, accept the offered supply crisis, and select its `RESPOND` state. Operator test worlds may use `/pale_mirror simulate step <count>`.
5. Build real Create track and a train with a schedule which repeatedly visits stations named exactly `PM Red Valley Dispatch` and `PM Ironhill Receiving`. Give the train at least one carriage.
6. Keep the endpoints loaded while the same native train visits both stations. `/pale_mirror logistics status` first reports its opaque vehicle ID at the origin, then the same ID at the destination. Once both visits occur within eight simulation steps, `certified capacity` becomes `12`, `18`, or `36` according to carriage count.
7. Confirm `/pale_mirror explain settlement <observed-settlement-id>` shows the alternate route as operational, then restart the server and re-run the command. The vehicle proof and route observation must remain consistent; a new train or stale proof must never certify the route.

Negative checks: two different trains, decorative stations with no train, unloaded endpoints, a mismatched station name, or a visit outside the proof window must leave certified capacity at `0`.
