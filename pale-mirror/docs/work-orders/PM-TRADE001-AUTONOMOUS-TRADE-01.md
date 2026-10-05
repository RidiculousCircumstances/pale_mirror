# PM-TRADE001 — autonomous goods trade and reusable logistics

Status: IN_PROGRESS; user authorized implementation after planning acceptance.
Date: 2026-10-05. Executor: main alone, no Terra/subagents.

Contract: [autonomous trade and logistics](../frontier-v3-trade-logistics-contract.md).
Workflow: [engineering protocol](../engineering-agent-protocol.md).
Current assignment and results: [canonical ledger](../../CONTINUITY.md).
The user authorized implementation and can provide human testing when a
connected candidate exists. Ordinary scoped implementation needs no stage-by-stage
permission. Existing release/deployment safety and operational authority apply.

## Outcome and scope

Two autonomous settlements and their companies can trade bread and stone with
real resources, authorized prices, reserved funds, physical cargo and confirmed
receiver acceptance. A company can act on its own account, not merely have its
name printed on a settlement-controlled job. Initial stone is
`minecraft:cobblestone`; commodities are catalog data.

Keep existing multi-worker farming, bakery, food needs, common bodies and
navigation. Modify their ownership seams only where the accepted trade flow
requires it. Do not restart the whole economy/movement architecture, broaden
combat, add a database or implement all historical Python features.
The user treats farming as minimally playable; this does not close all F0.6/UAE,
ARC or materialization debt. Adopt the touched logistics family coherently;
unrelated stage-wide proof campaigns are not this order's prerequisite.

## Starting point: source-backed gaps

Implementation worktree: `/home/rd/proj/pm-f06r3-facility-lane-recovery`.
Gradle root: its `pale-mirror/` directory.
Inspected ref: `d1f9e10a46ce8db3b520e649ebe159ec8e7d8b29`.
Paths below are relative to that Gradle root, not the original nested checkout.

Source prefix F:
`pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/`.
Source prefix N:
`pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/`.

| Anchor | Current limitation / reusable seam |
| --- | --- |
| F `process/SupplyOperationProcess.java` | Bread-only admission, hive recipient, one logistician plus two guards; physical-bound fungible loading is not the new generalized flow |
| F `model/SupplyContract.java` | Settlement-to-hive obligation, 1..64 items, no goods price or acceptance terms |
| F `model/FrontierRouteNetwork.java`, `RouteOperation.java` | Hive-facing endpoint topology and historical formation/cursor journey; not arbitrary commercial endpoints |
| F cargo validation/handoff confirmation support | Receiver and completion retain hive assumptions; must trace real callers, not just change the contract record |
| F `model/FungibleResourceLedger.java`, `ResourceLot.java`, `ClaimAllocation.java` | Reuse sole stock/custody/claim owner; existing cargo retitling requires isolated lots and lacks general partial-sale semantics |
| F `model/EconomicLedger.java` | Reuse sole financial owner; reservation settlement currently pays the entire reservation |
| F `model/CompanyRegistry.java`, production market/process | Companies exist, but current bakery inputs/output are settlement-owned and orders buy a production service, not ordinary goods |
| F `process/SettlementManagementComposition.java` | Registered supply/food/health/field policies; no autonomous company/goods-trade policy |
| F `model/ResourceSiteKind.java`, `ResidentWorkKind.java`, `WorkCatalog.java` | Wheat field only; agriculture/baking work permissions; no finite quarry or stone-consuming work |
| N `FrontierV3CargoLoadingExecutor.java`, `FrontierV3CargoHandoffExecutor.java`, cargo carrier/departure support | Reuse physical custody/effect fencing after tracing receiver, body, loading and recovery assumptions |

Historical Python economy and Java `frontier.reference` have production,
route costs and financial ideas. They are not the active v3 economy and may
not become v3 dependencies. Route-risk friction is a valuation term, not a
license to destroy currency on each trip.

## Implementation sequence

The steps below are coherent integration checkpoints, not separate approval
gates or helper-by-helper testing campaigns. Trace admission through terminal
settlement/recovery before editing; static contradictions justify repair.
Use focused tests for remaining uncertainties, then the wired product scenario.

### 1. Commercial core on the existing ledgers

- Introduce closed typed participant/order/contract/acceptance identities and
  immutable commercial terms; distinguish goods sales from production services.
- Add authorized partial title transfer and financial reservation settlement
  through existing resource/financial owners. Preserve ordinary Vanilla stack
  interchangeability, allocations, physical bindings and unsold lot remainder.
- Commit resource title, exact receipt, paid quantity and remaining obligations
  in one canonical transition. Retain physical applied/unapplied obligations
  independently of actor movement, contract expiry and commercial cancellation.
- Add named owned state updates, bounded retention, stable explicit wire tags
  and current-schema snapshot/WAL recovery. Use fresh test worlds if changed;
  do not maintain historical disposable-save readers as a feature requirement.

Exit: a sale has one exact owner of each obligation; partial/stale/duplicate
receipts cannot duplicate goods/payment or spend reserved funds twice. No
parallel market wallet, resource counter or slot-identity store is introduced.

### 2. Recipient-neutral logistics and shared execution adoption

- Replace the active hive fixture path with semantic sender/receiver endpoints,
  declared receiver acceptance and generic shipment cargo allocations. A sale
  can span shipments; authorized non-sale delivery can reuse logistics.
- Trace and migrate loading, crew admission, approach, journey, unloading,
  service access, drain, loss and restart together. Remove active hive/commodity/
  mandatory-two-guard branches instead of retaining a second delivery engine.
- Use common UAE body/execution capabilities, resource-safe interruption,
  navigation, geometry and access coordination. Economic route selection is
  separate from local pathfinding; no per-service obstacle compiler or body writer.
- Preserve real custody for cargo and each crew member across mixed HOT/COLD.
  Full/blocked receiver retains its exact obligation with a local disposition.
  Payment/contract completion does not wait for the crew's return journey.

Exit: the same delivery protocol handles two declared settlement depots,
physical-bound fungible cargo and partial unloading, without hive identity or
food-specific dispatch. Repeatable native claims require the new causal scenario.

### 3. Participant agency, ownership and minimal market

- Register settlement public procurement/export policy and independent company
  policy through common read-only views and admission ports. Exact authority
  owns each order; another participant cannot silently amend consent.
- Make production ownership explicit at admission. Keep existing public stock
  public; support declared own-account bakery production as well as existing
  buyer-owned contract manufacturing. Add the company's procurement/sale path,
  not an extra instruction hidden inside the market matcher.
- Match known eligible counterparties deterministically within both price
  limits, public food reserves, capacity and available funds. Reserve stock,
  money and receiver capacity together; expiry and supply changes wake owners.
- Public food purchases use the same goods protocol and feed the current needs
  system only after acceptance. Company bread in a public chest is not public
  food before purchase. Keep wages/service fees under existing financial rules.
- Bound searches/reviews and expose why no quote or match is possible. There
  is no world-wide omniscient demand allocator or automatic currency injection.

Exit: company-owned stock and money follow actual orders, while settlement
food protection and feeding remain correct. An unaffordable/unknown/forbidden
counterparty yields an explained non-match, not a forced transaction.

### 4. Real second commodity and complementary graybox

- Register cobblestone production, permissions, work definitions and company
  policy without commodity branches in shared trade/logistics mechanisms.
- Add a finite declared quarry cell pool and extraction work using shared
  work selection, rates/modifiers, custody, navigation and physical-effect APIs.
  No fake wheat-field cycle or infinite abstract stock source for stone.
- Add one bounded stone-consuming construction/repair project with declared
  target blocks and real resource expense. This is minimal content, not generic
  building AI. Already removed/player-mined cells cannot yield stone twice;
  unknown physical changes require classification rather than overwrite.
- Configure two existing settlements for complementary bread/stone supply,
  demand and lawful known endpoints. Provide explicit finite initial capital
  and owned fixtures only; acceptance stock must include produced goods.
  Seed capital is setup, not a recurring rescue of insolvent participants.

Exit: bread has a real feeding consumer and stone a real physical project.
Both sources, allocations and destinations are traceable. A finite quarry or
completed project may legitimately leave no further trade; do not invent demand
to promise an endless loop.

### 5. Connected player result, diagnostics and release checkpoint

- Join participant/order/contract/reservations/shipment/crew/cargo/receipt/
  payment in read-only diagnostics. Show actual public available stock separately
  from company, reserved and pending stock; explain blocked orders/deliveries.
- Update architecture, relation/codec ownership and materialization inventory
  for the actually adopted flow, with honest M0/M1/M2/M3 evidence labels.
- Build one relevant checked-in native bread/stone scenario: observe real
  production/loading, travel, acceptance and public use/project consumption.
  Use semantic camera/frame evidence; HOT activation shows current COLD state,
  never a visible replay. Cover graceful restart at a prepared physical boundary.
- Reuse existing coverage. Add only discriminating checks for overcommit,
  partial acceptance, replay, receiver damage/fullness, player resource changes,
  cargo loss and mixed-authority handoff; choose the smallest coherent fixtures.
- Verify/release through existing source and outer pack ownership rules before
  a human-test deployment. Report tested scope and remaining debt; do not claim
  all economy/guards/terrain/materialization stages complete from this scenario.

Exit: a player can see and explain bread and stone deliveries between two
settlements with real debits/credits and resource consumption. Existing food
and farming remain operational. Failed/partial delivery does not corrupt the
frontier; an invariant failure is visible with exact cause, not silent freezing.

## Completion criteria and exclusions

Commercial quantity is independent of 64-unit stacks and shipment capacity.
Resource/money conservation, retained exact relationships and once-only physical
settlement hold through interruption/restart. A company can reject a bad offer;
a settlement can protect its food reserves. Independent agency is a tested
behavior, not an organizational label.

Do not require an additional confidence rerun, matrix speedup demonstration or
whole Python parity programme. No per-stage approval pause during a later
authorized implementation; stop for a material scope/authority choice or the
completed agreed checkpoint. Full applicable release gates still apply.
Escort AI, diplomacy simulation, loans/taxes/dividends, dynamic pricing and
construction planning beyond the single stone consumer remain later content.

Current action: implement step 1 and its connected seams, then continue the
delivery/participant/content sequence. Do not offer a backend-only checkpoint as
a playable trade feature; invite human testing when there is a useful connected flow.

## Current implementation checkpoint

Step1 commercial core is committed at `460b1c59c8afb6ae4edf4c1448c212f351aae9ab`
after user authorization (no push): registered
event-only owner/codecs, explicit order consent, shared stock/funds/capacity
admission, partial title/payment, independently movable claim portions, source
cancellation, observed allocation-change disposition and expired closed-component
retirement. Resource bookkeeping preserves physical binding identity, epoch,
quantity and player fences. Holder and registered economic owner are independent
for fungible stocks; public food views count only public-owned bread.

Detailed result evidence is bounded to128 entries per contract while cumulative
accepted/disposed quantities and monotonic result revision survive window
eviction. Old receipts cannot become new payments by falling out of this window.
The current fresh-world grammar is canonical schema249; no prior test-world
reader is added and the live schema248 world has not been changed.

Focused78/78 canonical checks, guardrails and NeoForge compile passed in27s;
adapter test compilation also passed earlier in the same implementation cut.
Subsequent shared actor/container-handoff adoption passed45/45 focused commercial,
station, bakery and resident-meal checks in27s. Body placement in commercial tests
is fixture setup at declared service stations, not a navigation/journey proof.
Final food-view correction separates owned total stock, outgoing commitments and
unclaimed COLD stock; public reserve and discretionary growth cannot count goods
promised for delivery as available meals. Latest coherent goods/food/station/
bakery/meal49/49 and guardrails PASS29s; earlier78/78 covers the broader resource,
financial and relation regressions. These overlapping runs are not additive.
No native delivery, autonomous matching or product-completion claim follows.
No matrix, deployment, reset or test client was started for this checkpoint.

Step2 must not send commercial units through the old operation corridor cursor
merely because it already has a logistics name. The inspected active supply
family still requires a hive and two guards and uses OperationTravel. Adopt
declared endpoints and common goal navigation/item transfer/UAE across loading,
travel, unloading, interruption and recovery; remove the old active fixture
admission while preserving unrelated patrol/combat responsibilities. Ordinary
company/settlement policy and finite stone content remain steps3-4.

An additional traced seam was `FungibleActorOrderTransfer.accounts`: a claim's
commercial claimant had to equal the item order's execution owner. Step2 WIP
now carries explicit `ResourceClaimDelegation`, checked against the retained
shipment and exact current UAE authority through closed permission providers.
The generic resource primitive validates claimant/executor/allocation; it does
not inspect concrete trade/job types or duplicate stock claims.
`ActorItemCustody` already uses the existing shared container-before-effect port;
the new shipment owner must register its exact pending-effect fence there.

### Step2 started — transport authority/custody checkpoint, not a caravan

Task-owned WIP adds a separate root `ShipmentState`, registered event reducer and
current-schema250 snapshot/payload codecs. Every shipment retains distinct
facility and container identities, typed supported service stations, one exact
COURIER UAE execution and delegated cargo allocation. Loading/unloading preserve
title and use common item custody, service arbitration and storage admission.
Unloading ends transport authority independently of buyer title/payment and
crew return. Source-side player extraction closes the dispatch without payment;
live shipment references fence cancellation, partition and erasure.

Latest focused goods/service-access/UAE/food/bakery/meal71/71 plus guardrails and
NeoForge main/test compilation PASS35s. Only two focused shipment cases were
added; the existing player-extraction case now also checks dispatch retirement.
Their station placement is explicit fixture setup, not navigation evidence.
No route timing, HOT transfer, partial physical unload, native journey, casualty/
interruption or autonomous production is proved by these checks.

The new shipment family is not admitted by an autonomous producer yet. Its
ambient locomotion is explicitly denied until common goal navigation is wired;
there is no fallback actuator or second body. Next increment must connect the
actual route/physical-effect producer and receiver result, not grow more inactive
infrastructure. The old hive supply admission is not removed yet; do not claim
Step2 complete or deploy this foundation. Existing runtime remains unchanged.
