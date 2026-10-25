# Economic situation on R253, 2026-10-25

Read-only investigation of deployed code19d49af61, same world
frontier-v3-r249-clean-20261025 / seed126 / rulesr26 / schema303. No reset,
resource/cash injection, acceleration, deployment or client visit was performed.
Implementation HEAD8f6952213 differs from deployed code only by its latency report.

Evidence: fresh analytics at tick559905/revision2206325, final tick570982; live
participant/production/station diagnostics; source tracing; isolated Java reads
and calculations against a frozen actual snapshot, revision2214031/tick561431.
Snapshot SHA2563fe8d2be82374996e3750c28431c43aab005895e0d8cb0a08984e015133bc5f6.
Offline calculations are explanations/counterfactual quotations, not server-issued
orders, completed deliveries or proof of repaired product behavior.

## Observed condition

At the final sample249/358 residents were hungry; stocks across owners/custodies:
262bread,3101wheat,710coal. These totals are not universally accessible stock.
Nevertheless this is not global physical exhaustion: about1033bread could be
made from that grain, requiring about414coal at the actual recipe's640heat per
bread and1600heat per coal. Ownership, equipment, transport and machine admission
prevent that aggregate from being an executable production plan.

At tick559905, selected examples (total owned stock, not unclaimed availability):

| Settlement | Hungry | Bread | Wheat | Coal | Treasury coins |
| --- | ---: | ---: | ---: | ---: | ---: |
| Dawnbridge | 19 | 1 | 577 | 6 | 1.315761 |
| Southgate | 41 | 0 | 173 | 0 | 3.906107 |
| Mossbrook | 32 | 0 | 631 | 0 | 4.406017 |
| Sunreach | 24 | 0 | 1338 | 0 | 4.192257 |
| Ironmeadow | 23 | 3 | 18 | 193 | 0 |
| Clearwater | 1 | 180 | 128 | 0 | 1090.590682 |

The conserved treasury total is1200coins; Clearwater holds90.88% (balance,
including reservations). Its financial ledger records1550.300405received and
559.709723paid, versus its100initial coins. Unequal wealth alone is not an
invariant violation; the problem is inability of the other producers to restore
production and generate executable sales. The original onset is not attributed
solely to one pricing change from this current-state investigation.

Retained analytics report2878bread produced versus4781consumed over their stored
interval: production has depended on running down prior stock. The latest bread
trade in the retained receipt set is at tick480932; the last receipts before the
initial sample were105wheat at tick544165, for only0.650475coins total. These are
actual receipts, not advertised orders. Price/flow retention and process-restart
gaps are declared by the dashboard; this is not a controlled before/after trial.

## E1 — confirmed local geometry/availability defect blocks fuel supply

Dawnbridge's bread job remains STATION_LOAD with INSUFFICIENT_HEAT, no pending
physical effect, no local block and released source/station custody. Its station
holds320heat, no fuel stacks and no active operation; one recipe needs640heat.
There are6coal in storage, so absence of coal in the settlement is not the cause.

The live navigation wait addresses route[427,64,-1382]→[418,64,-1344], blocked by
cell[419,65,-1344] with WRITE_PENDING. The same wait appears in the R252 native
receipt at tick509086 and the current live sample at569423, over60000ticks later.
The frozen snapshot retains that exact unknown observation with no known cell.
Calling the actual ProductionStationSupply.offer on that snapshot for two
eligible, committable logistics workers returns UNKNOWN_GEOMETRY /
ENDPOINT_LEVEL_NOT_KNOWN. It does not return a fuel shipment.

Source chain: ProductionStationSupply → InternalShipmentPlanning.plannedPath →
shared navigation knowledge. FrontierV3NavigationObservation.blockWrite fences
knowledge before a write; its committed branch does not publish a new shape.
Later captureAvailable/observePending can close the fence only from loaded,
projected native terrain. With no players currently online, an unclosed durable
fence can keep COLD logistics waiting until another physical observation.
The geometry fence's first originating physical write is not recovered here;
therefore neither replay nor blindly declaring the cell known is justified.

This is a technical liveness/recovery defect, distinct from market balancing.
Any fix must publish actual final geometry/dependencies through the shared native
operation/observation lifecycle, retaining genuine unknown/conflict barriers.

## E2 — equipment exhausted in the settlements which expanded fields

Southgate now has341cells and Mossbrook340, across4fields each. Extra sites have
retained completed COLD harvest receipts; expansion was real, not just a diagram.
However each settlement's three retained stone hoes has damage130/131. The
catalog's service reserve prevents another autonomous use. FieldWorkForecast
returns zero equipment-backed workers for both settlements; FieldSupplyCapacity
therefore returns no wheat-producing process. Mature crops remain on all4sites.

The maintenance/replacement mechanism exists, but no replacement hoes are present
in this snapshot. These settlements also lack coal/cobblestone/ingots needed to
restore their productive chain and have very little money. Increasing land cannot
restore missing equipment, and can increase wear/replacement demand. Precise
choice between candidate repair/craft recipes is not claimed proven here.

Anchors: FieldWorkForecast.workers, EquipmentProductiveCapacity.project,
ToolCatalog.serviceable, EquipmentProductionPlanning.

## E3 — provisioning and solvency form a circular logistics constraint

ExpeditionCrewAdmission requires real personal/warehouse supplies for departure
and the forecast journey. GoodsProcurementTransport invokes this gate before a
quote is considered an available sourcing offer; GoodsTripAssembly checks it again
against actual work eligibility and mission commitments. These safety checks are
real constraints, not evidence that a delivery has been promised.

On the actual snapshot, selecting even one resident from ALL living residents,
with no work restrictions, fails at duration0 for Southgate, Mossbrook, Sunreach,
Westhaven and Northwatch: the available stock cannot meet departure nutrition.
Ironmeadow and Dawnbridge can select one person at duration0, but not for24000ticks.
This necessary check already fails before route/worker-contention questions.

Thus self-collection of food/fuel cannot be an escape when no provisioned collector
exists. A supplied seller could deliver instead, but that requires funding goods
and the journey. An offline quotation with actual stock limits shows Hearthvale
could offer14coal to Mossbrook only under a hypothetical larger quotation budget:
28.14coins for goods plus47.115048estimated cash freight, delivery42125ticks /
journey83750ticks. Mossbrook actually has4.406017coins and receives no quote.
No money was added and no contract was created. This quote is a necessary forecast,
not proof that final navigation/mission admission would succeed.

Other tested coal sellers Ironmeadow/Westhaven still provide no quotation even
under the larger quotation budget: their provisioning constraint remains. A
blanket money injection would therefore not resolve every supply failure.

## E4 — valuation loses a production anchor when operational capacity vanishes

GoodsEconomicPricing takes retained commodity value from the currently available
GoodsProductionPricing opportunities; absent process means retained value0.
The exact Southgate/Mossbrook equipment shortage removes field capacity, so their
wheat has no local replacement opportunity in that calculation. This conflates
current executable capacity with the longer-lived economic value of a renewable
resource and its recovery requirements.

Current retained asks are0.011566/0.064366coins per wheat; an actual Mossbrook sale
at tick544165 was0.006195per wheat.105wheat earned0.650475coins, less than one coal
at nearby quotes around1.79–2.01 before transport. That sale does not demonstrate
that continued farming at that price can fund tools, fuel or delivery. The code
explicitly separates sale consent from new-production permission, but the shared
operational-capacity valuation still loses this anchor when equipment is down.
This investigation proves the current missing anchor, not the exact first historical
price transition that created the cheap sale.

Some bread asks are enormous, e.g. Southgate6045.249299, but those participants
have no saleable bread. These are not executed transactions and should not be
mistaken for the main current bread-market price. Actual stocked bread offers from
Clearwater/Hearthvale are about14.11/15.32; many towns have less than the price of
one loaf in their whole treasury. SellerPriceDiscovery preserves prior asks when
there is no surplus or funded buyer, so an inactive quote can remain stale.

## E5 — planning detects the shortage but does not establish a recovery path

The accepted public plans explicitly contain CRITICAL_PRODUCTION_DEADLINE,
UNSECURED_PRODUCTION, CASH_SHORTFALL and NO_EXPORT_MARKET. Most sampled plans are
CURRENT; all know the other11participants. No evidence supports blaming forgotten
neighbours or a globally stopped economic-review queue.

Long reserve horizons (e.g. about240000–427000ticks) describe desired coverage;
they do not mean a delivery is feasible. Purchases, production recovery, minimum
inputs, equipment restoration and provisioned transport must form a jointly
executable, affordable path. Current plans can report hundreds of missing goods
while no funded complete restart exists and current sourcing offers are absent.
Price pressure, additional land and a CRITICAL bread objective do not close this
physical/financial cycle themselves. Some uneven productivity/trade dependence is
intentional; its sustainable cash-flow balance cannot be judged from needs alone.

## Repair order implied by the evidence

1. Close E1 through durable final geometry observation/recovery for all managed
   writes. Do not erase WRITE_PENDING or require a tester to visit every blocked
   workshop. This is a confirmed defect independent of price policy.
2. Restore and forecast the full equipment replacement chain before field/mining
   capacity is exhausted; distinguish unavailable tools from missing people.
3. Separate durable production/recovery valuation from temporary operational
   readiness. Retain actual buyer affordability and avoid forced profitable sales.
4. Plan deficit recovery across food, fuel, equipment, crew provisions and cash
   together. Preserve real supplies and payments; do not disguise an impossible
   trip as an offer or force an unfunded sale. Evaluate feasible supplier delivery
   and priced replenishment/leg consumption before committing a round trip.
5. After operational blocks are removed, measure production/consumption and trade
   balances over several cycles. Decide any resource/population/distance balance
   change from those flows, rather than another arbitrary coal multiplier or
   redistribution of money. No new resource sink, subsidy or credit is proposed
   as a substitute for the confirmed repairs.

## Scope and receipts

The engine continued advancing with0pending ordinary ticks,0ready schedule backlog
and no retained required conflicts/quarantine. Recent64 native ticks had a5.55ms
maximum at the final diagnostic; a cumulative106ms spike exists and is not denied.
This rules out a global scheduler freeze as the explanation of these local waits;
it is not a universal performance guarantee.

No product code was changed. Build-local evidence:
- economy-analysis-current.json / final-view.json: analytics snapshots/history;
- economy-analysis-{runtime,settlements,management,participants,production,stations}.txt;
- economy-analysis-snapshot.bin and EconomicSituationRead.java / valuations.txt;
- EconomicFuelBlockRead.java / economy-analysis-fuel-block.txt;
- EconomicFreightRead.java / economy-analysis-freight.txt;
- economy-analysis-performance-final.txt; earlier r252-first-ingress.json.

Package-private offline probes were compiled into build/economy-analysis-classes
and run with the existing pure Java module classes; they neither touched the live
WAL nor replaced runtime policy. Actual code links/line numbers can drift; named
owners above are the reviewed boundaries. Native-client testing was unnecessary
for proving the retained geometry gate, tool conditions and quote prerequisites.
