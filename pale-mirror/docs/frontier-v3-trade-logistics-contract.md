# Frontier v3 autonomous trade and logistics

Status: accepted target; steps1-3 have scoped implementation, steps4-5 remain.
Accepted 2026-10-05. This is not full trade/product acceptance.
This contract extends [the player/system contract](frontier-v3-contract.md).
[PM-TRADE001](work-orders/PM-TRADE001-AUTONOMOUS-TRADE-01.md) owns implementation
sequencing; [execution semantics](frontier-v3-execution-semantics.md),
[domain relations](frontier-v3-domain-relations.md) and the engineering protocol
remain authoritative. Document approval is not code or native acceptance.

## Product goal and first content

Independent settlements and legal companies buy and sell real goods, pay from
their own accounts and transport identified cargo between declared receivers.
The player can understand the need, seller, buyer, price, carrier, destination,
delivery result and blocking reason. Neither a visit nor HOT activation starts
the economy; COLD continues the same retained obligations.

First content: bread and stone, two settlements with complementary production,
one currency, configured price/reserve policies, finite stone extraction and
a real stone-consuming construction or repair project. The initial Minecraft
stone commodity is `minecraft:cobblestone`, matching ordinary stone drops;
this is a catalog choice, not a special case in matching or transport.
Bread retains its currently declared production recipe until separately changed.
No infinite quarry, invented consumer, invisible transfer or automatic money
creation is permitted to keep trade moving. Exhaustion, adequate stock and
insufficient funds can correctly stop new trade, with a readable explanation.

## Single owners and dependency boundaries

| Owner | Responsibility | Must not own |
| --- | --- | --- |
| Settlement policy | Public needs, food reserves, public purchasing/export authority and budgets | Company stock or unilateral company sales |
| Company policy | Its own inputs, outputs, stock targets, quotes and authorized purchases | Public treasury or another participant's choices |
| Goods market | Match compatible authorized orders and retain accepted terms | Invent demand, price consent, resources or money |
| Trade contract | Commercial obligation, accepted quantity, price and disposition | Body movement or duplicate inventory |
| Logistics | Shipment, cargo allocation, crew, loading, journey and unloading | Price policy or implicit title transfer |
| Resource ledger | Quantities, economic owner, custody, allocations and title transfers | Market strategy or financial balances |
| Financial ledger | Accounts, available funds, reservations and settlement | Item custody or a parallel trading wallet |
| UAE/activity/navigation | Exact worker execution, interruption and physical motion | Commercial completion from position alone |
| Geometry/route knowledge | Known traversability, explicit ports and route facts | Task choice or trading permission |

Policies depend on bounded read-only views and explicit admission ports.
Common matching, logistics and settlement protocols consume typed capabilities,
not concrete bakery/quarry jobs, company purposes or ID prefixes. Closed
composition registers participant policies, receiver acceptance and production
capabilities; adding a commodity must not require editing the matcher or carrier.
Record names and interface shapes are implementation choices, not mandated classes.

### Current participant/production composition (Step3)

`GoodsParticipantPolicies` is the closed strategy composition. Policies consume
one participant's read-only stock, protected reserve, incoming obligations and
available funds. `GoodsOrderMatching` consumes independently authorized quotes;
`GoodsTradeStateSupport` commits through the sole stock/financial/capacity owners.
`GoodsShipmentPlanning` delegates actual carriage to the existing Shipment/UAE
protocol. Neither policy nor matcher writes bodies or another inventory.

`ProductionRights` explicitly distinguishes buyer-owned public manufacturing
from company own-account manufacturing. The company proposes use of its own
input and pays its worker; settlement operations arbitrate access to shared
machinery/workforce. `CompanyBakeryPlanning` submits to the same bakery admission,
station processing, item custody, navigation and completion path. It does not
create a second production engine. Own-account production neither issues a
public manufacturing invoice nor pays the worker twice. Output retains the
declared input owner's title; placing company bread in a public depot does not
make it public food. An authorized goods acceptance is required.

Production rights currently declare the participant's home depot. Current
autonomous stock accounting uses fungible lots. Initial acquaintance is a stable
settlement-ID chain, plus explicit introductions of home companies; it is not a
claim of nearest-neighbor geography, diplomacy AI or discovery of remote stock.
Actual routes still require the shared known geometry. Route-risk valuation and
the bread/stone complementary content remain subsequent work, not Step3 evidence.

Ruleset `frontier-v3-production-r12` / ruleset schema14 hashes the commodity
catalogs, prices, stock targets and review/search limits. Current defaults are a
400-tick fallback review, 24,000-tick quote lifetime, at most64-unit autonomous
batches and64 compatible-pair examinations per review. The commercial core still
supports larger obligations/partial shipments; a policy batch is not its limit.
Public bread target is the greater of four units per living resident and the
existing needs-system protected reserve. Both policies target64 wheat; public
bread quotes use minimum2/maximum3, company bread minimum2/maximum2, wheat1/1.
These are catalog data, not branches inside matching or logistics.

Producer-authored stock changes, bakery deliveries, new quotes and accepted
goods wake the relevant participants through `GoodsParticipantWakeup`.
Notifications only invalidate decisions; owners reread current authority before
acting. The periodic fallback also handles expiry and otherwise missed changes.
No arbitrary world-wide event scan or new permanent per-stock polling loop is
introduced. Current fresh-world canonical schema254 persists participant
knowledge/review receipts and production rights in snapshots/WAL; prior
disposable schemas are rejected rather than silently reconstructed.

Read-only `diagnose process <participant-id>` exposes policy/decision, owned and
unclaimed stock, protected minimum, incoming quantities, available funds, known
counterparties, next scheduled review, orders, contracts and linked shipment IDs.
The shipment diagnostic owns the deeper physical journey/cargo detail.

Company residence, founder and settlement membership are relationships, not
permission to spend another account or infer ownership. Each durable participant,
contract, shipment, endpoint and capability carries a complete explicit nominal
identity. Continuation retains exact relationships rather than rediscovering them.

## Resource rights and production

Economic owner, physical holder and reserved claimant are independent dimensions.
A company may own goods inside a settlement container; the container owner does
not thereby own its contents. Carriage by a worker, station or cargo account
does not sell the goods. Interchangeable stacks can split, merge and change slots
through the existing fungible-resource reconciliation mechanism.

Production admission declares the input owner, output owner and destination.
Contract manufacturing consumes the buyer's input and returns the buyer's output
for a service fee. Own-account production uses company input and yields company
output. The selected recipe validates its declared ownership mode; a general
coordinator may not infer it from job kind. Existing public stock stays public
unless an explicit authorized transaction transfers it.

The current production work-order market and the goods market have different
obligations: purchasing a manufacturing service is not purchasing already-owned
goods. They share the resource and financial ledgers, not duplicate claims or
ambiguous order IDs. Public food procurement must make acquired bread available
to the existing feeding system; company reserves are not falsely advertised as
publicly available meals.

## Autonomous orders and matching

A buy order retains exact buyer, commodity, quantity, maximum delivered unit
price/total budget, receiving endpoint, purpose and expiry. A sell order retains
exact seller, commodity, authorized unclaimed quantity, minimum delivered price,
source endpoint and expiry. All quantities/prices use checked bounded integer
or existing fixed-point arithmetic, never floating money or ordinal wire tags.

The participant owns consent. A matcher cannot raise a buyer's ceiling, lower
a seller's floor or commandeer protected food stock. First policies use configured
limits in the persisted hashed ruleset; bargaining and dynamic pricing are later.
Known counterparties, diplomacy, lawful access, endpoint capability, available
capacity and known reachability constrain eligibility. No omniscient stock scan
creates knowledge for a settlement or company.

Matching is deterministic, bounded and fair within declared policy. Compatible
orders establish one immutable contract at an agreed price within both limits.
Admission reserves exact seller-owned quantities, buyer funds and feasible
receiving capacity through their existing owners in one canonical transition.
Orders cannot overcommit the same stock, funds or capacity across contracts.
One contract may require several bounded shipments; neither a Minecraft stack
nor the old 64-unit supply record defines the commercial quantity limit.
Order expiry affects only unmatched quantity, not already accepted contract
terms. Receiving-capacity reservation is not a promise that a player cannot
change the container; later loss of capacity has an explicit delivery disposition.

Distance, risk, known route quality and estimated duration inform selection.
Risk is not an automatic cash sink. Actual delivery charges, if present, are
declared contract terms and accounted payments. First scope uses seller-arranged
delivery and a delivered price; a separately priced carrier market is deferred.

## Delivery, acceptance and payment

The causal chain is:

`authorized orders -> reserved contract -> allocated shipment -> load -> travel
-> receiver acceptance -> resource title + payment -> terminal commercial result`

Shipment completion and crew return are separate. A paid accepted delivery does
not wait for the courier to return; crew turnover/return uses normal activities.
Logistics may also carry a non-commercial authorized transfer, using the same
receiver/loading/journey interfaces but without fabricating a sale.

Goods stay seller-owned in transit. At the receiving endpoint, independent
quantity/custody evidence and the declared acceptance capability produce an
exact versioned receipt. A physical deposit alone does not grant the buyer
unreserved use while acceptance is unresolved. The canonical acceptance commit
atomically records the receipt, transfers ownership of the accepted portion,
settles its price and updates remaining goods/funds/capacity reservations.
Partial transfer splits lot rights without retitling an unsold remainder.
The same receipt cannot transfer or pay twice, including after restart.

This atomicity is canonical, not a claim that Minecraft chunk/container saves
and WAL are one database transaction. A prepared physical step, its captured
epochs and applied/unapplied/ambiguous evidence use the existing persistence and
reconciliation protocol. Applied effects must settle before cancellation frees
their obligation. Missing evidence never becomes inferred delivery or loss.

Full or inaccessible receiver, stale offer, player withdrawal, damaged route,
interruption, partial acceptance, cargo loss and refusal have explicit local
dispositions. Pay only accepted quantities; retain unaccepted surviving goods
under seller ownership. Return/reoffer/cancellation needs an authorized policy,
not an implicit fallback. Release only positively unspent/unapplied reservations;
an uncertain physical effect keeps its exact obligation. Real loss is recorded
once with cause and quantity, not minted back into seller stock.

## Routes, bodies and HOT/COLD

Trade-route selection chooses a known endpoint-to-endpoint economic journey.
The shared navigator chooses local pedestrian motion; callers provide semantic
goals and constraints, never their own obstacle set or movement actuator.
Existing common geometry provides explicit supported surfaces and facility ports.
Rail/carrier capabilities remain distinct from pedestrian capabilities.

Shipment owns cargo and crew relationships, not a second actor position/body
registry. Crew members enter UAE through their registered activity capability.
Their interrupted civilian work follows its own resource-safe checkpoint.
Mandatory escort count is policy, not a hidden hard-coded two-guard requirement.
First scope may use an unescorted legal graybox corridor. Future escort missions
reference the exact shipment and real guards; tactics belongs to combat, not trade.

COLD uses retained known geometry and event-time journey/progress boundaries,
without unloaded-world reads, per-frame durable movement history or a second
writer for a physically held body/cargo. HOT preserves the same shipment and
uses current physical facts. Activation projects current state rather than
replaying missed commercial or movement history in front of the player.
Mixed HOT/COLD crew/cargo custody, drain and recovery must be adopted explicitly;
the old supply family's historical checks do not prove this new flow.

## Observability, bounds and acceptance

Read-only diagnostics join participant -> order -> contract -> allocations/funds
-> shipment -> actors/cargo -> receiver receipt -> payment. Every wait/failure
names its exact owner, blocking operand, cause, expected condition and next due
review in simulation ticks. Presentation distinguishes stock, reserved stock,
available public food and a pending delivery. Developer order commands enter
normal admission; they cannot manufacture a successful outcome or repair state.

Changes to stock, funds, route or receiver availability wake the relevant owner;
bounded fallback reviews handle missed/expired knowledge, not a full-world scan
each tick. Ruleset bounds cover open orders/contracts/shipments, lot allocations,
path queries and terminal retention. Terminal records compact only after reference
closure and outstanding physical/custody/financial obligations are settled.

First acceptance is a real two-settlement bread/stone trading loop with witnessed
production, cargo, receiver stock and conserved money; unchanged farming/feeding
continues. Negative/recovery coverage addresses overcommit, blocked/full receiver,
partial/lost cargo, stale/duplicate acceptance and restart at a physical boundary.
Use focused existing coverage and one relevant checked-in native causal scenario
with semantic camera evidence before claiming repeatable visual acceptance.
No repeated broad matrix or timing-speedup proof is created by this contract.

Out of scope: taxes/dividends, credit expansion, theft/crime, dynamic exchange,
share ownership, full combat escorts, generic construction AI and porting the
entire Python economy. Historical reference behavior informs requirements only;
v3 must not depend on Python, v2 or `frontier.reference` at runtime or admission.
