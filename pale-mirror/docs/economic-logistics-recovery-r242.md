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

The diagnostic deployment `ac19979ec` and native run
`ab589295-7b29-4dc2-a41f-3a2f781993f5` attributed the repeated cancellations to
`frontier.extraction_geology_invalidated.v2`. All 826 newly excluded positions
in the copied comparison were at surface Y=63. The native sampled environmental
write path had no cooperative admission and forced unrelated preparation to
restart. The nonindexed geology callback also discarded a Pending command
result and allowed the physical write.

Correction: explicitly sampled native block/fluid updates yield before invocation
when a preparation owns publication, then use the ordinary nonblocking persistence
turn. Player actions and native scheduled work are not intercepted. Every managed
block owner now returns its before-write admission; pending geology/custody rejects
the physical attempt until the exact receipt is durable. The sampled update may
run on a subsequent Minecraft sample, so vanilla background growth cadence under
load is not claimed unchanged. This avoids a broad, unsafe rejection of native
commands and does not disable geology invalidation or subsidize the economy.

R242e verification: full adapter suite and guardrails passed
(`build/r242e-adapter-tests.log`); all three block-extraction GameTests passed
(`build/r242e-terrain-tests.log`), including a delayed durable geology write.
The runtime regression holds preparation explicitly, rejects 32 environmental
samples, then proves readiness resumption and recovered canonical state. Native
GameTest startup also verifies both exact Minecraft random-tick injection sites.


## R242e deployed HOT/COLD result

Clean source `3d13d18bf` was installed on the same retained world at
2026-10-24 19:53:18 +05; fresh deployment verification passed. Exploratory
native run `38794d48-f652-4756-af3b-51820fca1e09` visited Southgate and
Clearwater for 60 seconds each and disconnected normally. No food, funds or
completed-job evidence was injected.

The observation advanced from 501128 to 504235 across client launch, visits
and departure. It completed 1772 scheduled preparations with **zero
cancellations**, admitted 13911 environmental samples and yielded 42481
before invocation. Environmental mutation is therefore still active.
Clearwater's exact production job `6204d2919b1c3044bb5a642dde9fec0a`
progressed during HOT attendance and reached DELIVERY after departure; its
next baker entered production. At 508169 Clearwater had zero hungry residents.
Southgate's previously observed imported fuel, new bread and actual meals remain
separate receipt-based evidence; a full-town recovery is not implied.

Full adapter tests: 858/858; block-extraction native tests: 3/3, including
refusing the physical write while its exact durable fence remains pending.
The native visit is exploratory, not M2/M3 acceptance: the retained screenshot
is poorly framed and the pilot did not assess a complete visual work sequence.
The earlier two combat/patrol test failures remain unresolved.

Performance remains separately open: the fresh deployment's maximum observed
host tick was 81.306 ms; canonical time still accumulated roughly 1700 pending
ordinary ticks. Eliminating cancelled work does not establish an 80 ms global
bound or full real-time throughput.

Mossbrook's later exact candidate differs from the earlier funding refusal:
at approximately 504328 it had 39.025412 available, but a nearby affordable
delivery could not acquire an eligible supplied crew. More expensive candidates
still failed funding. Do not diagnose this state from the older 36.86 balance
or treat hypothetical provisions as actual food.

A suspected fixed one-bread-per-operation capacity limit was rejected by source
inspection: `ProductionBatchSizing` permits multiple recipe batches and
`ProductionStationClock` gives attended production a common operation duration.
No throughput/balance change was made from that unproved hypothesis.

At copied checkpoint 511728, Clearwater had a 20-bread batch processing and
Hearthvale had a 20-bread batch picking up 60 wheat. Thus the batch-capacity
check is also backed by live jobs. Southgate's next job explicitly reports
INSUFFICIENT_HEAT at 511206: its earlier fuel delivery did not establish
sustained fuel sufficiency.

Two retained FIELD_PROJECTION operation tickets logged AMBIGUOUS on the Southgate
visit (`35f599a2-47bc-4430-971e-7d7ab4f3161a`,
`e3abde4b-9e6c-404d-b3cf-88c101b0ebfb`). Their exact replay/disruption history is
not established by the economy fix. The absence of a global quarantine or a
conflict assertion failure must not be reported as absence of these warnings.

## Last long delivery

Sunreach received five coal from Stonefield at tick **515977**, receipt
`shipment-32df1e5a-6b8a-3e20-9606-3c0220018742`: goods 13.174900 and
delivery 92.760385 coins, paid from its retained treasury. At copied 517930
the exact internal shipment `ae54de52-e520-35a3-8a5c-5b12fce4c819` was CARRYING
all five coal from the metals warehouse to the bakery's FUEL port, assigned to
resident 12-12. The warehouse name does not mean smelting has consumed the fuel.
Its baker's schedule at 517651 was FREE until 528000; no meal completion in
Sunreach is inferred from this delivery. Evidence: `build/r242f-receipt-view.json`,
`r242f-sunreach-baker.txt`, `r242f-sunreach-coal.txt`, `r242f-coal-haul.txt`.

These repairs close the evidenced pricing/organizer/crew, service-entry and
HOT preparation-cancellation defects. They do not establish that every town
has recovered or that long-distance trade can fund sustained fuel consumption
in the already impoverished world. Remaining shortages and the two retained
field-operation warnings require their own exact evidence, not another reset.
