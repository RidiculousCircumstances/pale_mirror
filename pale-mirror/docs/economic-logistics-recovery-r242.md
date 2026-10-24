# R242: starvation / procurement recovery

## Evidenced failure

R241i world, snapshot 365627 (copied read-only): Southgate had 201 wheat,
no coal or bread and 45.840016 spendable coins. Coal existed at other settlements.
Every collection alternative failed provisioning. Seller delivery failed funding:
4–14 journey bread were valued at the historical 41.424079 coins per bread,
creating freight of 165.696316–579.937106 coins before buying any coal.
Sunreach had previously spent its entire 100-coin treasury on two coal and had no
realized export receipts. That historical first price was not reconstructed.

The advisory purchase/source path evaluated buyer collection only, whereas exact
logistics supported both modes. Crew assembly also took the first eligible prefix
before asking whether it could be supplied; a different provisioned worker was
ignored. Economic input valuation could promote high production costs into output
value, and a cheaper executable acquisition did not bound willingness to pay.

## Repair and boundaries

- Shared `GoodsProcurementTransport` compares both organizers with real material
  prerequisites and funding. Cash freight applies only to seller delivery. It is
  advice, never a worker/vehicle reservation, route or stock commitment.
- `GoodsResourceValuation` obtains replacement costs from existing physical
  capability ports. It does not ask the reserve or market planner recursively.
  Inputs without a current local capability retain explicit external estimates.
  Cheap replacement advice is not evidence of available provisions.
- Exact crew assembly chooses among eligible residents by provisioning need;
  the work owner still validates labour commitments. Work cargo is excluded from
  personal food. Exact journey provisioning, receipt and money settlement remain
  with their existing owners.
- A current executable acquisition bounds purchase consent; expensive historical
  sales and expensive production cannot increase it above that alternative.
  Production cost alone does not create independent value for its own inputs.
- Advice requested by undated reserve projections consumes retained nutrition at
  or after its recorded instant. It never rewinds or mutates hunger. Actual
  departure still uses the canonical admission time.

No new money, food, debt, refund, forced sale, persistent schema or world reset.
Already completed historical trades are not rewritten.

## Verification so far

The actual `GoodsTripPlanning` on copied snapshot 365627 now admits seller 6 →
buyer 9: 7 coal, 14.933429 goods + 19.979078 delivery, within Southgate's existing
45.840016 coins. Full exact crew/provision/route/funding checks ran; this is an
unpublished offline decision, not evidence of physical delivery.

Focused coverage includes unfed versus supplied crew, caller work permission,
non-shareable personal supplies, seller-funded provisioning, insufficient buyer
cash, expensive historical food, active quote / reserve recursion and retained
nutrition. Runtime completion and sustained recovery remain to be checked after
deployment. No claim of general economic equilibrium or 80 ms performance bound.

## Existing verification corrections

The aggregate exposed three stale adapter assertions unrelated to pricing:
body insertion acknowledgement now also belongs to the common saved-operation
integration (R241H); calendar progress can yield during asynchronous work; and
performance JSON has a separate costly-stage summary. Tests now assert the
closed body-owner boundary, actual committed calendar/quarantine state and
bounded structured telemetry with explicit omissions. No runtime guard was
relaxed to satisfy those assertions.

Additional checks: final focused pricing/provision/source suite passes. Local
replacement uses actual capability cost in both directions: a cheap historical
receipt cannot become a fictional cheap replacement either. Snapshot 365627
also admits 4 coal to Mossbrook (8.533388 goods + 14.984304 freight); Sunreach's
zero-cash purchase remains correctly unavailable. At the later live copy 405627,
Southgate/Mossbrook still require available transport/provisions. The offline
365627 result does not prove immediate recovery at a later state.

Release aggregate 58448ffc6: 858 NeoForge unit tests, 324 domain tests and 79
Visuals tests pass. Native aggregate completes 406 tests, 404 pass; failures are
`fixedSeedSettlementAssaultUsesIndependentColdPlansAndHotReceipts` (admission
fixture timeout) and `ordinaryDemandLossReturnsTheUnchangedFormationAndCursorToCold`
(patrol lease not CLOSED at the fixed 277-tick assertion). They exercise combat /
patrol fixtures outside the economic repair; no new code for those owners was
introduced. The full native gate is RED, not waived or described as passed.
These scenarios remain separate investigation debt. Active economics/custody/
field/station GameTests in that aggregate pass. Full frontier unit run was stopped after a superseding live access defect was established; it is incomplete, not passed.

## Additional live access blockage

After deploying f2d802fe5, copied checkpoint 417668 showed Ashcross's donkey
shipment `54988df4d1ee5559b8a6384228d396b2` still carrying wheat: its final approach
had been retained since tick 349549. Exact route was two surfaces, but service
arbitration trimmed it to the exterior surface. Another provisioning claim won
lexical ordering while its resident was still far away. This is a shared service
queue error, not missing stock or an impossible donkey route.

Uncommitted work approaches now compete by distance to the declared service
boundary, then request time and stable identity. Occupants, exit clearance,
committed entrances and meal priority retain their existing protection. The
common coordinator reads only spatial/commitment facts; it does not reinterpret
production, food or trade jobs. No arbitrary search radius or depot special case.


## Staged approach, not a journey-long entrance reservation

Copied checkpoint 438k exposed the remaining Hearthvale lock: resident 6-6 had
retained a 3167-surface provisioning route ending at the depot. Its departure was
at 378217 and arrival at 441537. That retained journey outranked a courier three
steps from service. ExpeditionSupplyMovementProvider published no service
approach and bypassed the shared segment boundary. Waiting for this journey to
finish could eventually clear the lock, but did not make the protocol correct.

All declared service approaches now split before first entry even when the
point is presently free. At the exterior boundary a separately admitted final
entry retains its turn. Provisioning and replenishment use the same boundary
policy as shipments and mining; assembly has no service transaction. Existing
unsplit routes checkpoint their actual progress before entry rather than replay
an unadmitted arrival. The activity owner still owns loading/consumption.

On the same copied Hearthvale state the internal courier's access changes from
false to true and its first leg reaches the exterior boundary. This is an
actual-state counterfactual, not a deployed recovery claim.

Observed live receipts at 436076–436194 paid Sunreach 153.270890 coins for 210
wheat after its cash had been zero; it then contracted a five-coal import. Ashcross
resumed baking and meals. Attribution limit: its old wheat mission completed
before the second access deployment, so that completion alone does not prove
the new arbitration. Southgate/Mossbrook fuel recovery remains unconfirmed.

## Integrated release and retained-world observation

The combined service change is committed as `60387330a0`; `da0427f12`
implements the equivalent next-step commitment check without scanning a whole
route during sorting. The clean detached artifact was deployed on October 24
at 19:16:19 +05 to the existing `frontier-v3-r241i-clean-20261024` world.
No cash, food, route endpoint or actor position was injected.

Verification: 102 affected frontier tests, 858 adapter tests and 55 economy
GameTests passed. The final equivalent comparator change passed the focused
service/courier tests and packaged JAR verification. Evidence lives in
`build/r242c-integrated-tests.log`, `r242c-release.log`,
`r242c-final-artifact.log` and `r242c-deploy-verify.log`.
The earlier unrelated combat/patrol native failures remain open; this is not a
claim that the full native gate is green.

During observation Hearthvale progressed from 9 hungry residents at deployment
to zero at tick 464540, with continuing production and consumption. Southgate's
three-coal seller delivery has a retained journey arrival at 472549; Sunreach's
five-coal delivery is longer, with the first route section ending at 501532.
These are actual journeys, not stuck depot permits. Receipt and subsequent
production still require separate observation.

Mossbrook at copied checkpoint 464854 is a distinct remaining financial limit:
36.863642 available, and the full sum is available to its coal purchase.
The nearest seller quotes 34.352286 delivery plus 3.351216 for one coal, exceeding
that sum. Other delivery estimates are higher; collection has no provisioned
eligible crew. This particular refusal does not prove another budget defect.
The prior hundreds-of-coins freight error is not its current explanation.

Dawnbridge's 256 claimed wheat at that checkpoint is already collected cargo
inside its own pack animal on the return journey, not grain reserved for export
at the home warehouse. Only one wheat is actually available at home. Keep these
custodies distinct when diagnosing a town that reports both stock and hunger.

## Receipt and HOT follow-up

Southgate received three coal at 472634; fuel entered the bakery as stored heat.
The first new bread was consumed by 480499, and eight bread had been baked by
483770 (seven then remained in the delivery job). Clearwater received 34 coal
at 481034. These prove fuel recovery and one complete meal, not recovery of
every hungry resident. Hearthvale produced/consumed 19/20 bread and Ironmeadow
36/26 during the retained-world observation.

Exploratory native run b20e51b8-2ec3-4d77-9a62-e33401eab3ea visited Southgate
and Clearwater. Conflict assertions passed, but progress did not: canonical time
remained at 485379 through much of the Clearwater visit. After departure,
546 scheduled preparations had been cancelled (three already ready), with 1891
ordinary ticks pending. Source review shows external commands can cancel a
preparation outside a persistence turn. Attribution to a particular callback
is still unconfirmed; bounded invalidating-command diagnostics will resolve it.
A global pending-calculation rejection is unsafe for non-replayable native
observations, so it is not applied as an assumed fix.
