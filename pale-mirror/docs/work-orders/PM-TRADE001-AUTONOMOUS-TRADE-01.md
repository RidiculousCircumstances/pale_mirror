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

### Step2 connected implementation — scoped logistics complete

Task-owned WIP adds a separate root `ShipmentState`, registered event reducer and
current-schema253 snapshot/payload codecs. Every shipment retains distinct
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

The registered shipment command and native executor are now connected. Common
navigation owns the actual journey; COURIER retains the same resident and shared
resource account through HOT/COLD. A saved departure releases physical hand
bindings only from the exact saved receipt, not player absence. HOT endpoint
transfers retain exact durable preimages and confirm the next replica boundary.
Partial unloading creates an independently acknowledged reception:17 of60 pays17,
retains43 on the carrier, and survives recovery. No return journey gates payment.

Common activity selection can interrupt a road leg for an actual meal, retaining
freight separately from personal food. Resumption updates the same shipment's
execution generation atomically and starts from the resident's real post-meal
position. The interruption port explicitly names the resident; owner wake events
may legitimately address the retained shipment rather than the resident itself.
Confirmed casualty disposition uses the common recorded death fence and an exact
pre-loot/actual dropped-item witness, preserving unsold title and releasing only
unfulfilled funds. Ambiguous prepared effects are retained and explained locally,
not guessed from a missing entity. Terminal records retire explicitly when the
bounded admission history fills, never by erasing live references.

Audit of the old path: production SettlementSupplyPlanner does not offer
bread-to-hive delivery; uncontested-supply/autonomous-supply-interception are
unit-only fixtures, rejected by the graphical pilot. Historical operation/scene
recovery coverage remains for unrelated combat, not a second active commercial
delivery admission. Do not remove those independent responsibilities under this
order. Ordinary market dispatch remains Step3; the new generic admission needs
no bread/hive/mandatory escort checks.

The checked-in `disposable-goods-shipment-hot-cold-restart.json` passed as
`build/trade-step2-native5.json`, run `4e86c2dc-5dff-4f23-bee9-45e032b4bd9c`:
17 ordinary evidence actions, HOT physical pickup32, exact COLD hand departure,
graceful restart of the same world, shared receiver approach, HOT unload and
independent buyer acceptance32. Same resident1-1/entity
1b400b9e-14eb-318c-8d11-467cc5bc9e01/execution generation1 throughout.
The terminal at canonical4820 is DELIVERED, remaining0, accepted32, fulfilled,
no pending effect/reception. The owner-versioned progress receipt is SATISFIED.
Receiver resource inspection and its real opened chest agree: owned by
settlement2, initial64 plus delivered32, physical slots64/32. Both frames were
reviewed: the inventory UI is legible, but the clean courier frame does not
clearly isolate the loaded worker. It does not establish M3 or HUMAN promotion;
the exact local entity-motion trace supplies narrower M2 journey evidence.

Native4 exposed a real ordering race, not a timeout: projection release ran
before the EFFECT stage settled saved cargo, then common body unload rejected
the retained binding. Registered courier release now waits for its exact saved
hand acknowledgement, matching its body checkpoint. The regression uses the
actual ambient release owner before/after that receipt. Native3's Java/Node
anchor mismatch and Native2's wrong terminal-action reference are also corrected;
all failed receipts remain failures. No confidence rerun was used as a fix.

Focused goods21, catalog17, ambient6, harvest reconciliation5, persistence4 and
native scenario parser43 pass, plus guardrails/build/packaged-JAR checks.
Earlier loss, body-lifetime, shared activity and absolute-control evidence is
retained within its unchanged scope. Cargo GameTest slice19/19 passes, including
the old impact-reload case that returned DEFERRED in the421-test aggregate.
The aggregate itself remains failed:1253 frontier tests/6 failures and421
GameTests/1 failure. Focused reports overlapped that aggregate's report directory;
do not claim an exhaustive identity-bound repaired failure inventory or a green
release milestone. This is separate release/test-isolation debt, not permission
to erase failures or widen Step2 into a legacy scene campaign.

Final source review replaces the new private20-tick shipment retry with the
persisted common terminal-logistics review cadence; the partial-full receiver
case verifies that cadence and retained43-unit cargo. Native5 evidence is reused
only for its unchanged successful path, not the revised blocked retry branch.
Existing live runtime remains unchanged; owned disposable server/client stopped
and ports25596/25597 closed. Step2 source privately committed at `3a6e9d7c`
on 2026-10-06 after explicit user authorization; no push/deploy,
full-pack/HUMAN/M3 or whole-trade completion follows. Current explicit goal
authorizes participant agency and market dispatch (Step3) with existing bread
and money; stone remains Step4. Main alone; no subagents.

### Step3 connected implementation — autonomous participant agency

Settlements and registered companies now retain nominal policy, lawful endpoint,
known counterparties and monotonic decision receipts. Registered periodic and
producer-authored opportunity actions run their independent read-only policies.
The matcher rechecks both current consents, price limits, public protected food,
unclaimed source allocations, available funds, receiver capacity and shared
known-route availability. It rotates bounded quote-pair examination; an
unreachable first candidate cannot suppress subsequent feasible candidates.
No recurring capital grant or omniscient inventory scan is introduced.

`ProductionRights` is explicit in admission, all job replacements, snapshot and
WAL. Company production uses the existing strategic admission, actual baker,
station/custody/navigation and completion protocol, with company input/output
and one reserved wage. Existing public manufacturing remains buyer-owned with
its existing service fee. The company's independent procurement/sale policy
does not gain permission to spend public resources merely from its legal home.
Shared-container purchases transfer title against exact accepted custody without
inventing a fake courier trip; inter-settlement purchases use existing Shipment.

Actual connected canonical checks cover company grain procurement and payment,
own-account bakery processing/recovery and wage, sale of company bread into
public food, protected/promised food, unfunded/unknown explained non-matches, and
autonomous inter-settlement delivery through the real registered engine queue.
The delivery starts with finite initial stock, not precreated orders/contracts/
shipments: it selects terms, dispatches, travels, unloads and accepts16 bread,
debiting32 money exactly once. The engine check exposed use of the continuation
gate at fresh courier admission; it now uses common `mayStartOrdinaryWork`, not
a private shipment permission rule. Fixture capital/stock are explicitly finite
setup, not production logic. The company station check goes through actual
strategic planning/production and snapshot recovery while processing; it is not
a native Minecraft restart claim.

Focused51/51 PASS in26s: AutonomousGoodsTradeTest5, GoodsTradeTest21,
BakeryHotVerticalTest4, FrontierWorldProcessCatalogTest17 and
FrontierPersistenceCodecTest4. Command:

```text
./gradlew :pale-mirror-frontier:test --tests '*AutonomousGoodsTradeTest' --tests '*GoodsTradeTest' --tests '*BakeryHotVerticalTest' --tests '*FrontierWorldProcessCatalogTest' --tests '*FrontierPersistenceCodecTest' :pale-mirror-neoforge:compileJava verifyArchitectureContract verifyFrontierV3ArchitectureDebt verifyJavaStyle verifySourceIsolation --no-daemon
```

The subsequent edit only extends the read-only participant diagnostic with known
counterparties, next review and linked shipment identities, and corrects the
initial knowledge comment; the unchanged canonical evidence is reused. This
step is M0/M1 implementation/automation, not M3/HUMAN or a green whole-release
gate. Step2 native5 retains only its unchanged narrow journey evidence. No new
native client/server, world reset, live deployment, push or full matrix was
started for Step3; previous aggregate failures remain explicitly unclosed above.
Fresh-world schema254 / ruleset R12/schema14 is required for this candidate.
The next planned product content is Step4 finite stone extraction and its real
consumer; connected bread/stone graphical/release acceptance remains Step5.

Private source checkpoint `5846ba4d` contains Step3. A subsequent adapter build
completed701 unit tests with19 failures in3m18s; it is NOT a green build gate.
The complete failed XML/binary reports are retained under
`build/trade-step3-adapter-failed-5846ba4d/test/`, before any focused report writer.
Fifteen failures explicitly reject the newly scheduled company participant
review as a retired subject; the maximum COLD interval test also quarantines
at tick1000. The exact closed reference barrier omitted the existing nominal
CompanyRegistry surface. It now validates company existence there, not by ID
prefix or an arbitrary policy exception. A sixth autonomous-trade test runs
ordinary company foundation and its review through the real registered queue,
rather than skipping schedule effects in a direct reducer fixture.
Three other failures time out before the historical cargo assembly boundary;
their reports remain failures and do not establish a new Step3 product cause.
Focused reruns, not a second aggregate, verify the changed company boundary.

Final correction is privately committed at `72732be7`: autonomous6/6 and
`FrontierV3ServerRuntimeTest.maximumColdOperatorIntervalCompactsBeforeTransactionRetentionCanQuarantine`
1/1 PASS in30s. The latter advances the ordinary persisted server runtime through
24,000 COLD ticks, including real company foundation, with no quarantine and
the exact terminal instant. This confirms the production failure's correction;
it is not a native graphical/client test or proof that all19 failed aggregate
cases now pass. Guardrails, compiled JAR and verifyPackagedJar also PASS16s in
the preceding correction cut; only the regression's stop condition changed
afterward, from elapsed tick to the actual participant-review receipt. No
production source changed after those package checks. The failed aggregate is
retained and no full-green release claim follows. Implementation worktree is
clean; main alone, no push/deployment. Scoped Step3 goal is complete.
