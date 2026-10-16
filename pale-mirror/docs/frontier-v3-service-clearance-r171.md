# R171 — private-origin service clearance

## Confirmed defect

On R170 Hearthvale, Vera Hill (`resident:6-18`) remained at exact position
`(-103.90950389461887,65,14.300000011920929)`. Mila Ash (`resident:6-24`)
reported her as the physical obstruction of the food-service approach. Bread
stock remained 173, of which 167 was unreserved, while six residents starved.

Saved tick87800 has valid routes from support `(-104,64,14)` through
`(-105,64,14)` to public resting destinations. The origin is the private inner
threshold, outside the service boundary; the next surface is inside that boundary.
`ServiceAccessBoundary.allowsWaitingRoute` correctly forbids ordinary outside
entry, but applying it to the complete private-departure route traps its occupant.
All eight first candidate routes were rejected by that same service boundary.
An isolated native diagnostic with reconstructed collision geometry and the
three nearby physical peers found routes to all eight destinations. This was
not an exact full live-world replay or human acceptance.

Separately, the HOT selector spent only eight native feasibility probes and then
treated every later candidate as unavailable. It restarted the same enumeration
on retry and mislabeled exhausted computation as no safe destination.

## Correction and boundaries

`ServiceClearanceRoutes` composes the existing declared `PedestrianLocalDeparture`
and public geometry. A valid private prefix may reach its first public service
contact and then leave that boundary. It may not finish inside service, cross
another protected boundary, or re-enter after exiting. A public-origin actor
receives no private-departure exemption; missing support remains invalid.

The common selector uses this policy for HOT courtesy, idle HOT point clearance
and resident service turnover. Strict public waiting-route semantics remain
available and unchanged for ordinary waiting and unrelated service consumers.
This grants only departure geometry, never a service turn, inventory access,
resource receipt, new activity, teleport or another position writer. UAE,
physical observation and the common goal navigator retain their existing roles.

Native destination feasibility runs after canonical route permission. The bounded
HOT probe retains rejected destinations between slices of the same actor/body
generation, origin and geometry. Budget exhaustion reports SEARCH_PENDING.
Completed passes can reconsider moved occupants; stale permission/origin/geometry
and lost requests discard the ephemeral pass. Search state is bounded and not
persisted: restart/HOT-COLD resumes from common actual-body state, not old probes.

No schema or economic-policy change is required. Deploy into the existing R170
world to retain the reported incident, rather than clearing it by world reset.

## Evidence

Implementation checkout: `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`.
Diagnostic receipts: `build/r170-courtesy-{snapshot.bin,routes.log,native.log}`.
Saved-state corrected selection: `build/r171-courtesy-saved-state.log` selects
public support `(-103,63,20)` instead of rejecting the private exit.
Focused regression receipt: `build/r171-focused.log`.
Source commit: `d37fcbd3cadf4f4e8c83969796922da9512d9ab8`.
`build/r171-native-package.log`: 42/42 local navigation and 375/375 general
native tests pass; NeoForge build/package pass (741 JUnit cases, one skipped).
This is not a claim that the unrelated complete Frontier test suite is green.
Pre-deploy live observation at tick113621 shows the old incident had already
cleared on R170: Vera was COLD in a caravan and Mila was nourished. Consequently,
post-restart progress alone cannot prove this exact HOT departure; its evidence
is the captured-state selection, native reachability and focused policy/probe
regressions, with the live deployment check separately scoped to runtime health.
Delivery and live verification identity are recorded in the canonical ledger.
