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
field/station GameTests in that aggregate pass. Full frontier unit run ongoing.
