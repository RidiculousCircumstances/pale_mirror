# Cash-cycle investigation after R260

Investigation requested after the live economy check. Production code2ef90f7e9,
report-only HEAD0e49789b2; same selected disposable world
frontier-v3-r260-clean-20261025, seed126/r26/schema304,
UUIDf09d5442-d3dc-4c12-b792-405df4bc1e21. No source implementation, restart,
acceleration, world reset, credit, stock or cash injection in this investigation.

Evidence: analytics build/r260-check-1319-economy.json at174075, source-correlated
frozen actual snapshot build/r260-check-1319-snapshot.bin at174212/revision596863
(SHA2561c4b387aa0a9f6428b0599524d69f2fe29a3857c5da59aaeb438699295245077),
build/r261-{market-block-read,market-price-read,final-market-read}.txt.
The build-local Java readers use actual owner functions on that immutable snapshot.
Counterfactual calculations below are NOT published orders, trips or deliveries.

## Observed causal chain

Both Southgate and Mossbrook received100initial coins, spent exactly100 and have
no sale receipts. Southgate acquired4coal:3@20coins plus22.202286seller freight,
then1@17.797714 by buyer collection. Mossbrook acquired6coal:4@20coins,
14.801524seller freight, and2for5.198476coins by buyer collection. Payments and
transfers are real; the world total remains1200coins. Money did not disappear.

At174075 Southgate had750wheat and Mossbrook694. Their public wheat SELL orders
and warehouses are usable. The frozen owner projections permit744/659units to
be sold respectively. Thus neither a vanished warehouse nor total reserve
protection explains the missing sales.

Southgate's3hoes are130/131 and cannot perform another autonomous operation.
Mossbrook's tools remain serviceable. Its station actually completed a bread
operation between176299 and178340 on retained heat2560, raising cumulative bread
output10->11. Coal inventory0 is not equivalent to a cold/empty machine. A
repair-only explanation cannot account for both towns' hunger.

## 1. A historical coal-price intervention still governs startup

FrontierRulesets.balancedFuelTrade sets coal seed ask20/bid30 instead of the
underlying2/3. Git blame binds it to b9ed9065a6 (2026-10-18). Its comment explicitly
justifies this with one coal heating20bread and insufficient miner export income.

Later higherFuelDemand multiplies bread heat by8. The current recipe consumes
640heat per bread, while coal still supplies1600heat:2.5bread/coal. The price
intervention and100coin treasury remain. The first paid receipts above confirm
that20was an actual initial transaction price, not merely an irrelevant UI field.

This is a stale manually calibrated initial condition, not an ongoing subsidy or
permanent20coin floor. Later coal offers/receipts do fall. That later adaptation
does not refund earlier cash or finance another batch. The evidence establishes
an expensive startup shock; it does NOT establish that simply changing20to2
would make the complete economy sustainable.

Source: FrontierRulesets.java balancedFuelTrade/higherFuelDemand;
ProductionCatalog.fuelledGraybox. A future profile correction must have its own
identity/hash and must not silently reinterpret the recovered r26 world.

## 2. There is an actual buyer, but prices and delivery disqualify these sellers

Northwatch has197.98free coins and needs83wheat in the frozen accepted plan. Its
published landed-price limit is2.722891. Other active buyers' commodity budgets
are tiny: Redwillow0.895807, Hearthvale0.191288, Ironmeadow0.000004. Several towns
with free cash have no current wheat purchase requirement.

Using the actual64unit price-advice boundary:

| Supplier | Goods ask | Trip resource cost | Landed per wheat | Forecast delivery ticks |
| --- | ---: | ---: | ---: | ---: |
| Southgate | 2.48 | 81.235336 | 3.749303 | 160250 |
| Mossbrook | 1.47 | 104.445432 | 3.101960 | 209750 |
| Clearwater | 1.885864 | 48.140296 | 2.638057 | 92750 |
| Sunreach | 1.37 | 84.245518 | 2.686337 | 167000 |

Southgate/Mossbrook currently cannot provision seller delivery; these quoted
modes use buyer collection. The cheaper competitors can provide seller delivery.
Trip resources are not all cash: Southgate/Mossbrook's collection quotes invoice
zero freight, but still consume the buyer's real provisions/time. Ignoring that
cost would not be a correct rescue. Delivery estimates are advice, not arrivals.

Their lack of sales therefore is not proof of broken quote matching. Current
funding, alternatives and timing legitimately matter. Nor is there zero demand
in the world. The connection to their financial failure must be examined at the
pricing/finance decision layer.

## 3. Replacement cost is also an unconditional reservation price for old stock

GoodsProductionPricing derives wheat replacement opportunities from installed
field capacity and SourcingValuation, including configured labour valuation.
GoodsEconomicPricing passes this as ResourceEconomicValue.retained;
SellerPriceDiscovery.ask enforces minimum=max(1,retained).

Frozen Southgate retained value2.13 and Mossbrook1.47 prevent sufficiently lower
asks. Their suppliers' own work is not a cash wage, yet its estimated replacement
cost constrains all saleable existing grain, including grain whose conversion
into bread is unfunded. The production anchor remains useful; the issue is using
it as the unconditional minimum for a separate old-stock sale decision.

Diagnostic only: with all other frozen market facts unchanged, passing zero
retention to the existing price function changes proposed asks2.48->1.368753 and
1.47->1.006096. This demonstrates which constraint binds; it does NOT authorize
zero value, indiscriminate liquidation or prove that a complete trip would win
and arrive. New-production profitability and sale of existing stock must retain
separate semantics. Blindly deleting the floor would repeat the earlier cheap
bulk-sale failure; bounded working-capital recovery needs an explicit owner value.

## 4. Price advice and trip selection describe different alternatives

GoodsBuyerDemand.read caps demand at rules.maximumBatch=64, a contract/stack
boundary. The same rules allow256units in a mission; GoodsAcquisitionPricing
already considers the mission bound. For Northwatch's actual83unit need, the same
Mossbrook quote can carry83 for the SAME104.445432 trip-resource estimate. Its
unit freight becomes1.258379 instead of1.631960 (about23%less). Southgate can quote
79, cash-bounded, rather than64. These are read-only quotations, not dispatched
caravans. The current64unit advice does not uniformly represent the whole trip.

There is also a selection-policy mismatch. SellerPriceDiscovery.Demand/Offer
contain prices, quantities, budgets and freight, but no deadline/arrival. Its
sold() prediction gives demand to cheaper competing landed prices. Actual
ProcurementSelection.select first prefers timely offers; when all are late it
prefers useful recovery per journey time BEFORE comparing price. It then includes
crew labour and departure eligibility. A price-only sales prediction therefore
cannot generally describe the actual choice. This is source-proven disagreement;
its individual contribution to each historical missing sale has not been measured.

## 5. Financial planning protects known needs, not a complete renewal cycle

EconomicFunding allocates known restarts and forecast stock budgets.
EconomicPurchaseFunding can make all current cash available for a restart;
GoodsTripPlanning admits exact affordable candidates. These are necessary cash
and custody checks, but not a requirement that the town retains enough working
capital to repeat its productive cycle or restores cash by an executable export.
EquipmentReserveSupply adds materials only from the planned deficit response;
healthy-tool use has no separate ongoing renewal funding allowance.

The late repair request is thus not the sole cause. Money may be spent on fuel
before future renewal appears, while already produced grain cannot earn cash
under the current market/valuation constraints. No new funds, refunds or accepted
contract cancellation were introduced to make the diagnosis look repaired.

## Proposed coherent correction (not implemented by this report)

1. Remove the historical resource-specific revenue compensation from the active
   startup profile. Establish consistent initial price references from the current
   production balance, keeping prices free to adapt and total initial cash fixed.
   Do not replace it with another permanent coal-price bound.
2. Separate cost of additional production from the opportunity value of retaining
   already owned stock. The economic owner must expose an explicit bounded
   recovery sale/cash requirement and funded alternative use. Market clearing
   continues to require a real buyer, goods, cash and actual logistics. Selling
   existing stock below replacement cost must not authorize more loss-making output.
3. Evaluate minimum operating continuity across food, fuel and durable renewal in
   the existing single economic planner. Protect the corresponding shared cash
   envelope; count actual receipts only, never prospective sales as available money.
   This is broader than just a special tool-repair reserve.
4. Use one bounded lot/transport/arrival evaluation consistently for price advice,
   supplier feedback and actual procurement. Keep64unit allocations inside a larger
   mission rather than truncating the market need. Reuse the actual buyer-choice
   policy instead of maintaining an independent price-only prediction of sales.
5. Verify against the retained failures and a consistent fresh startup: actual
   payments, provisions, funded inputs, bread consumption and terminal tool
   restoration where a real feasible market opportunity exists. Compare true
   trade/production flows; do not infer sustainability from totals or a reset.

A valid model need not guarantee every town profits or survives any terrain.
The target is that it evaluates real alternatives and operating feasibility
consistently. Current evidence does not justify labelling these failures simply
natural resource exhaustion, or promising that one budget/price tweak fixes all.
