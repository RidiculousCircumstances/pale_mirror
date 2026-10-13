# Settlement market and economic planning

Accepted 2026-10-13; implemented and bounded verification completed2026-10-14. This amendment governs the
current public-settlement trading cut and supersedes fixed-price/seller-only
assumptions in frontier-v3-trade-logistics-contract.md. Exact verification and
acceptance limits are recorded below; no full-pack/HUMAN/M3 claim. Main is the sole executor; no subagents.

## Outcome

Public production and resident distribution remain moneyless. Settlements own
treasuries, choose provision and production plans, and trade actual resources.
First content is wheat, bread, cobblestone and coal. No invented consumption,
resident wages, automatic credit, insurance or hive trade is introduced.

The settlement planner owns needs, protected provision, feasible production,
export opportunities, purchasing budgets and reservation prices. Needs describe
outcomes; their family supplies resource/service requirements through registered
capabilities. Future medicine must not put diagnosis or care rules in the market.
The market owns orders, deterministic matching and immutable commercial terms.
Logistics owns route/crew/asset/provision estimates and actual carriage. Sole
resource and economic ledgers own quantity, title, claims, cash and holds.

## Accepted trading rules

- Initial commodity price scales are persisted ruleset data, reconciled with
  recipes/yield and initial treasury. Bounded recency/volume-weighted completed
  sales supply subsequent price observations. Quotes are not completed sales.
- Seller policy changes offers at bounded economic reviews. Unsold accessible
  surplus and successful sales are different signals; route failure alone is
  not evidence of an excessive price. No-trade intervals retain the market
  reference. Policy coefficients are hashed ruleset data.
- Buyer compares feasible whole procurement plans: payment, delivery timing,
  own crew/asset use and protected essential work. No conversion of hunger
  points to money or fictional internal wages. No claim of global optimization.
- The selected seller's quoted terms set payment, never the average of bid and
  ask. Buyer limits and available budgets are ceilings. Capacity, actual stock,
  lawful known counterparties and route readiness constrain selection.
- Partial orders and multi-supplier procurement are supported. Confirmed inbound
  obligations prevent duplicate procurement. Trip-size economics remain separate
  from commodity quantities and no one-stack cargo creates a one-stack mandate.
- Unmatched authorizations may expire or be replaced. Accepted obligations do
  not disappear with quote expiry or ordinary demand changes. Admission reserves
  goods, money and the actual transport commitment in one canonical transaction.
- Seller delivery: seller organizes the expedition; title/payment pass at buyer
  reception. Seller owns cargo loss until reception.
- Buyer collection: buyer organizes and provisions the expedition, travels to
  seller, receives title and pays at confirmed pickup, then carries owned goods
  home. Buyer owns loss after pickup. Source, destination and expedition home are
  separate explicit concepts. Transport organizer is fixed at admission.
- Buyer planner selects between both feasible fulfillment offers. Generic market
  protocol must not choose an organizer by ID, residence or resource ownership.
  Read-only logistics assessment is not a reservation or a completed journey.
- Partial actual acceptance settles only that quantity at retained terms.
  Observed forfeiture releases only the corresponding unfulfilled hold. Overdue
  means investigate/wait, not inferred destruction or automatic cancellation.
- Storage replacement uses the common warehouse mechanism. No player edit can
  silently create a successful receipt or delete an unresolved obligation.

## Current source findings

At 8df887e4: GoodsTradeStateSupport already supplies resource/money reservation,
partial acceptance and bounded retirement; retain these owners. GoodsOrderMatching
returns the first rotated feasible pair. GoodsTripPlanning chooses peer/direction
order rather than comparing procurement alternatives. GoodsParticipantPolicies
uses fixed limits; unexpired pair-specific quotes prevent refreshed quantities.
GoodsTripAssembly selects seller labour/assets. TransportMission conflates source
with expedition home; supply, funding, return and declaration validation share
that assumption. Pickup requires a coherent itinerary/title-transition migration,
not merely selecting buyer workers. Existing SupplyPlanner supports production
inputs, fuel and deadlines; extend its capabilities rather than create duplicate
stock truth. Existing TradeStockSupply already proposes bounded export production
from known buyer demand; do not claim that capability is absent.

## Implementation sequence and completion criteria

1. Extract an isolated commodity-neutral offer evaluation/matching boundary.
   Wire real goods matching and consolidated trips to buyer-selected alternatives;
   retain current ledgers and exact reservation validation. Whole-trip candidate
   comparison must not publish losing drafts or claim pending routes feasible.
2. Persist price observations and policy decisions; refresh unmatched quotes,
   protect existing obligations, and wire policy to supply/production advice.
3. Separate organizer/home from cargo endpoints. Introduce explicit delivery and
   collection terms, itinerary stages and pickup acceptance. Migrate supply,
   funding, group travel, shipment authority, return, receipts, codecs and
   diagnostic relations together. No second active transport implementation.
4. Wire feasible seller-delivery and buyer-collection alternatives into purchaser
   planning. Check collective labour/provision/asset/budget admission and partial
   orders; preserve protected public stock and no duplicate inbound planning.
5. Verify the active paths with focused price/selection, accounting, failure and
   current-schema recovery checks; add a bounded declarative physical round trip
   for each new fulfillment mode. State actual observation versus unproved M3.

Fresh disposable worlds may be required for schema changes. No compatibility
inference or silent saved-world rule replacement. No deployment performed by
this document; use the existing release/server contracts for any deployment.

## Current implementation boundary

Source WIP based on 8df887e4 completes the accepted implementation below.
Dependent integration and the declared native collection journey passed.
This bounded functional result is not full-pack/HUMAN/M3 product acceptance.

- `model.market` owns bounded price/time offer indexing, purchaser comparison,
  accepted-sale history and quotation calculations. Registered review/withdrawal
  events refresh unmatched orders without changing committed contract terms.
  Candidate trips are immutable unpublished drafts; only the selected feasible
  draft reserves stock, money and transport. Comparison is bounded greedy,
  not globally optimal packing or an unrestricted anonymous exchange.
- Explicit `GoodsTradeFulfillment` retains commercial organizer and mode;
  `TransportOrganization` retains provisioning sponsor, home and itinerary.
  Cargo endpoints do not determine home by inference. Both modes reuse the
  same mission/group/UAE/custody/navigation owners. Public-settlement treasury
  sponsors current graybox crews; this does not add corporate labour policy.
- Collection prepares at buyer home, travels to seller, loads actual goods,
  and atomically commits title/payment only on the exact pickup receipt. A
  closed commercial claim becomes `OWNED_GOODS_SHIPMENT` with buyer-owned
  allocation. Unloading that cargo at home performs no second sale. Inbound
  counting and loss accounting follow the current allocation owner.
- Commercial authorization validates contract/mission compatibility through
  the registered capability. Generic logistics never inspects concrete trade
  jobs to rediscover their authority. Wrong buyer, stale revision, duplicate
  receipt or mismatched sponsor/home fail before accounting changes.
- Bounded completed-price history includes goods receipts and confirmed paid
  expedition refills. No sale or inaccessible/waiting route is fabricated as
  a price observation. No new trades means no decay of the market reference.
  Price review uses normalized shortage, observed surplus, ingredient/fuel
  replacement costs and declared feasible production/input/deadline capacities.
  Known-route accessibility is positive retained evidence, not a new path
  search triggered by quotation. Unknown accessibility cannot count as unsold
  accessible stock. Arrival and need deadlines are remaining tick durations.
- New explicit `quarry-graybox-r11` / rules schema27 enables this policy.
  Bread seed ask4/bid6 is consistent with wheat/fuel replacement cost. Old r10
  remains immutable. State schema286 rejects incompatible disposable snapshots;
  no saved-world compatibility inference is introduced.
- Diagnostics expose mode, organizer/home, retained quotes and bounded
  completed-sale summaries separately from active contracts. Retirement of a
  commercial contract does not erase the bounded completed-price observation.

The collection kernel test covers actual policy admission, travel, pickup,
payment/title transfer, in-flight snapshot recovery, home unload and crew/group
retirement. Negative accounting tests reject wrong/stale/repeated pickup and
confirm that disposal of already purchased cargo never refunds the purchase.
This isolated disposition test is not a physical death test.

`disposable-buyer-collection-restart.json` declares the ordinary-client physical
flow and graceful recovery boundary. Its finite fixture supplies initial stock
and buyer provisions, disables seller labour, and retains real chest capacity;
it pre-creates no trade, arrival or receipt. Existing seller-delivery evidence
does not establish collection acceptance. Exact final gate/native results and
remaining limitations belong in the completion receipt and Continuity Ledger.

## Verification record — 2026-10-14

- Dependent gate passed:39 targeted Frontier cases,726 NeoForge cases with one
  existing skip,366 required GameTests, guardrails, build and packaged-JAR gate.
  `build/market-final-dependent-gate.log` retains the aggregate result.
- The initial full Frontier run had1498 cases and9 failures. The missing r11
  fixture catalogue entry was fixed; the other8 reproduce unchanged on clean
  baseline8df887e4. They concern migration, obstructed harvest preparation,
  construction, maintenance and scene SDK. This is not a green global `check`.
- Native collection exposed a shared pedestrian classification defect: pushing
  a parked neighbour gave it forward velocity, making it appear to be a moving
  leader. Following now additionally requires an unfinished physical travel
  leg for mobs; parked peers remain detour/courtesy obstacles.35/35 focused
  native navigation checks include parked-pushed and truly slow-moving cases;
  unit following checks, guardrails and package gate passed in36s. This fixes
  the general navigation rule, not a collection-specific alternate path.
- Scenario validation53 cases passed. Native assertions use real sale history,
  released body custody and exact first-crew retirement. Fixed mission digests,
  assumed adaptive quantities, absolute fast-forward clocks and absence of
  unrelated subsequent missions are not valid acceptance conditions.
- Native7 delivered4 coal and39 cobblestone home after graceful recovery, but
  crew retirement timed out. Its saved snapshot shows no remaining movement or
  meal and AT_GOAL2; RETURNING progress was postponed by the strategic6000-tick
  interval. The transport owner now continues a proved stage transition at+1;
  unchanged external waits retain the existing interval.19 recovery/accounting/
  seller-delivery/collection regressions and guardrails/package passed in6m31s
  (`build/market-terminal-continuation-check.log`). The collection case explicitly
  checks prompt completion continuation rather than merely eventual retirement.
  Native8 passed the full physical cycle with this correction.
- Final native receipt `build/market-buyer-collection-native-8.json`:statusok,
  27 actions,3 reviewed clean frames,429.723s. Run
  `2cb217d6-965a-4d1a-9767-2534e532a0f7`; isolated26720/26721, not live.
  Real first mission `b20f08ef2f688a53232870255f84102e` collected4 coal and39
  cobblestone, recovered in flight, unloaded home and retired. Exact actors
  resident:10-30,10-12,10-24 are IDLE with empty assignment owner. Final stock
  has39 cobblestone and8 coal (4 coal initially present). Subsequent lawful
  missions remain active; bounded accepted history also includes9 stone from
  a later purchase and is not the first mission's delivered inventory.
  All three frames were inspected: ordinary geometry and bodies render, but
  departure/home lighting and crowding do not establish polished visual M3.
  Semantic progress declarations are empty for this group scenario; its
  successful diagnostics are not fabricated as a SATISFIED obligation receipt.
  Test client/server stopped, both isolated ports closed; earlier failed worlds
  retained. No additional confidence run is required.
- Verified WIP JAR `pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar`
  SHA512:928625e67eb3068d3c57b9f5f1979c52ceebd2e49182a490a54549f1d13c6c34ad7143ab6d3cbce9a037a6ac2e212fb96230ee49c1d9576300a0a37292870a4a.
- Source committed654f0ee4c5c62908b2a433591c55c33859afb989 and deployed as R135
  from a clean detached build to a fresh schema286 / ruleset r11 world.
  No compatibility conversion or silent recovered-world upgrade was performed.
  No push requested. [Deployment receipt](frontier-v3-market-r135-deployment.md)
  retains exact source/artifact/runtime identity and startup limits.
