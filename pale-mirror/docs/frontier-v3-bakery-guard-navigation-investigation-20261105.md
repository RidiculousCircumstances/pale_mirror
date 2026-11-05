# Bakery courtyard guard navigation — 2026-11-05

User request: inspect the recorded guard pursuit around the bakery table/seats.
Investigation only; no product edits, player mutations, restart or deployment.

## Evidence

- Complete capture `/home/rd/far-frontier-server/debug/pale-mirror/combat/combat-cd22dace-4aee-4dad-a0e9-d3106501e19f.jsonl`:
  10,372 expected/written events, no data loss; server ticks 56516–57388,
  approximately 43.6 seconds. PID 3253303, seed139/schema336 authored-building
  world and deployed 0d7138e1c artifact. Existing combat_trace.py reports complete
  transport. Nora Bell is resident:1-16, body epoch4.
- Repeated stationary periods of 2–7 seconds beside/on the actual table and
  spruce-stair seats; final stationary segment lasts 148 ticks (7.4 seconds),
  continuing until capture stop. ROUTE_PENDING appears in 339/437 position samples.
  At that final segment the Minecraft navigator reports done, while approach and
  BURST decisions continue. This is a movement/route issue, not a stopped combat
  loop. Rejected burst details include geometry=48,access=48,space=48,tactical=1,
  route=false: no general denial of courtyard access is evident.
- Final observed physical position is approximately
  (-1500.925000011921,65.5,-1349.429945429868). Shortly after capture, the ordinary
  departure log retains BodyPosition(-1502,66,-1350), i.e. support(-1502,65,-1350).
  Actor inspection after departure retains that same position. The support is
  the west-facing spruce-stair seat. The body centre overhangs that support cell;
  part of its bounding box is still physically supported by the lower half.

## Confirmed abstraction mismatch

FrontierV3BodyObservation.contact (lines54–68) accepts contact through bounding-box
overlap with the support's collision shape. It then discards precise X/Z/feet Y
and returns surface.standingBody(). The exact partially supported pose becomes
the named voxel's canonical standing position.

NavigationBodyGeometry.poseOffsets returns only .5 for a villager-width profile.
ProfiledPedestrianGeometry.contacts therefore evaluates the centre of that
support, rather than the accepted overhanging physical pose. For this stair, the
centre is obstructed; the original body pose is clear. Merely using exact native
collision boxes has not made the start-pose projection consistent.

An isolated read-only Java probe used the production seed139 authored bakery,
same WEST rotation, same registered geometry/profile and same local coordinates.
The default pure bootstrap bakery anchor differs by (-1120,-1020) from live, so
the probe translates both actor and target together. It does not reconstruct
historical live observations or replay the complete combat planner.

Results:

- Actual physical pose: NavigationBodyGeometry.clearance = CLEAR.
- Recorded support: known=true, blocked=true, clearStation=false.
- Route from that support to a known clear courtyard support fails with
  `known pedestrian start has no clear support/body column`.
- The same precise body volume remains clear at sampled forward offsets .15,
  .3, .5 and .7 blocks. These are clearance checks, not a complete swept/supported
  escape-route proof.
- An adjacent table support is accepted by the domain and produces a route over
  furniture. The trace also contains native goals above the table (Y67).

Temporary probe/results: `/tmp/BakeryGuardGeometryProbe.java`,
`/tmp/pm-bakery-yard-contact-probe.txt`; existing reader summary:
`/tmp/pm-bakery-guard-trace-summary.json`.

## Repair direction / limits

Fix the common boundary between observed physical contacts and navigable route
starts. A currently valid off-centre/fractional pose needs a bounded,
collision-checked departure to the shared graph; centring on its support voxel
must not be assumed possible. Preserve navigation/actor authority and normal
obstacle checks. Separately distinguish collision-supported furniture tops from
desired standing/intercept destinations using common spatial semantics.

No role-specific courtyard pathfinder, furniture teleport or per-bakery blacklist.
The trace does not retain every route request's exact geometry/contact witness;
the complete sequence of pending-query reasons is not proven by this recording.
The pose-to-node mismatch itself is reproduced and explains the final failure
mode. A repair must verify an actual off-centre seat departure through the native
navigation boundary, not only an exterior-to-workstation route.

IMPL and RELEASE remain unchanged at0d7138e1c. PACK tracked state unchanged;
pre-existing untracked .f0v-baseline/ preserved. Only this governance report and
the active ledger change for this investigation.
