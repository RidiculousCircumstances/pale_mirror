# Caravan supply funding and emergency native fixes — 2026-11-07

## Scope and ownership

The user accepted cashless expedition provisioning: the sponsoring settlement
reserves real treasury funds for a priced planned supplier visit before departure.
The ordinary market/financial/resource ledgers, UAE, navigation and physical
receipts remain the only execution and accounting owners. No arbitrary flat
cash allowance, free food, credit, teleported purchase or inferred arrival.

A complete home round-trip load stays the first choice and requires no budget.
When home stock covers the first leg but not the round trip, the planner can
retain a priced personal-food purchase at the trading settlement already on the
itinerary. The declaration contains the actual supplier storage, food, exact
recipient quantities and unit price. It secures money, not future seller stock.
The supplier may refuse later. Shared animal provisions still require the
existing complete home load; this cut does not add third-settlement detours.

Admission atomically reserves the supply budget with the exact mission. A
reversible loading replan changes the plan and budget together. Supplier stock,
consent and funds are rechecked before departure and purchase. After arrival,
the current return-food requirement determines the actual purchase; conservative
forecast surplus never forces unnecessary buying. Eligible supplier warehouses
are selected independently of the cargo container. Current nutrition is accrued
at the actual travel-decision tick rather than using stale stored satiety.

The exact food receipt alone transfers title and pays the supplier. Allocation
moves funds from the mission envelope to a payment hold, without a second cash
reservation. Cancelling an unreceived purchase refunds the same mission budget.
Completion closes unused funds and normal reference-closed retirement removes
the budget. A price increase requires real free treasury funds; another
mission cannot use the hold. Codec schema345 retains the purchase declaration;
schema344 fails explicitly and is not silently upgraded with invented data.

## Concrete issues found during implementation

- The first replan implementation reused initial-only funding declaration.
  Replaced it with an explicit version-fenced plan/budget transition; the
  original one-time admission guard remains.
- A real funded departure was recalled at tick3024 even with first-leg food,
  available supplier stock and sufficient budget: the supplier asked4 but a new
  domestic buyer bid was3.677364. Ordinary domestic repricing had incorrectly
  overridden the already approved expedition price4. Purpose-specific quote
  authorization now distinguishes current domestic demand from the retained
  supply plan. Seller consent, actual stock and funded payment remain required.
- A real route finished at tick32732 against a conservative37175 forecast and
  did not need its planned extra food. This is unused-budget release evidence,
  not proof of an actual paid stop. Separate registered replenishment tests
  exercise payment/cancellation/receipt and recovery.

## Emergency native repair delivered before funding

Source d730ef07e fixes two user-reported native defects in the old schema344
world, without resetting it. At13:29:42 the resource-site delivery pre-effect
classifier dereferenced a deliberately absent witness before its null branch,
quarantining the server. Named pre-effect classification now receives the job's
explicit resource kind; non-null witness recovery shares the same image checks.
Player occupancy still refuses an unproved delivery. The native regression
chooses a slot vacant in both exact and fungible projections.

NoAI actors do not run the native LookControl tick. Queuing a look target alone
therefore left a crossbow guard sideways. Shared combat-facing now applies native
eye look and body yaw immediately during sensing/preparation and along the actual
ballistic lead before emission. A moving-target native shot asserts head/body
facing and actual emission. This is native mechanical evidence, not a graphical
client animation acceptance claim.

Clean detached d730ef07e passed58 local-navigation native GameTests, guardrails,
JAR and package verification. Preflight/postverify passed in the same old world
crossbow-guards-fixed-seed139-20261107, seed139/r33/schema344. Live wrapper1895635,
invocation380b89cafe1442f986db87294141149a, notBefore1794040689. Core SHA512:
`a3736ce34ce230cf58169fd91924a166d10b471e6979fb2c45a4e283495dbd2ecd3c40b2fda177ff332423e940f730f6ad5036bddfef34cee10607b0f0cf27b1`.
Evidence: IMPL build/delivery-crash-facing-20261107 and
/tmp/pm-delivery-facing-{release,preflight,verify}.log.

## Funding verification and deployment

Source `6028787c026b4e7a8b2cd088ae9321d386df0e1f` committed21reviewed files.
IMPL integration35JVM PASS; clean detached supplementary19JVM PASS, with10
architecture checks overlapping (44distinct in total). Selected suites:
ExpeditionSupplyBudgetFlow, ExpeditionPaidReplenishmentFlow, ExpeditionSupplyFlow,
ExpeditionProvisioning, EconomicLedger, ExpeditionTransferDeath, BuyerCollection,
GoodsSpotPurchase, ExpeditionIndependentStorage, ExpeditionClockFlow,
ExpeditionSupplyIdentity, DeliverySupplyPlanning and FrontierArchitecture.
Guardrails/style/architecture/debt PASS (19guardrail selftests); clean detached
JAR/package PASS and all58required local-navigation native GameTests PASS.
Logs /tmp/pm-supply-budget-{integration,release,governance}.log; report inventories
under IMPL build/caravan-supply-funding-20261107/.

Normal RCON stop saved all dimensions14:19:03.067 and JVM/25565 exited before
installation. Previous world remains intact. New disposable selected world
`funded-caravans-seed139-20261107`, seed139confirmed via native seed command,
rulesetfrontier-v3-ranged-guards-r33, schema345. Required pack datapack installed
before first startup. Both deployment preflight and postverify PASS, fresh
runtime startup14:19:53, servicefar-frontier-v3-live.service ACTIVE,
wrapper2233018, invocation993635ea18a34572b5f8d03e607a0311,
notBefore1794043174. Exact core SHA512:
`b7377fda99384004ffbfeb10849020e8faf172f8e3e08dc4dd74dd23da8dbbd5ef931c22d338dffc6a379f1f3b57a8aae042caf0c9d0f107e55ed35e3497dace`.
Visuals unchanged from emergency deployment. Pack170indexed checksums verified;
published hosted artifacts, no tracked pack source edits. IMPL tracked clean
except untracked local.serena; RELEASEclean detached; rootpack tracked clean
with pre-existing.f0v-baseline retained. No automatic push or subagents.

Fresh live364cut: ACTIVE/green,0required/scene/inventoryconflicts,319residents,
12settlements,0initialhungry. A guarded relative+6000tick request was admitted
at812to6812; final result recorded below. This observation does not establish
long-term economy balance, actual paid HOT supplier stop or graphical facing
acceptance. The full funded canonical route and separate actual-receipt flow
prove their respective boundaries; no whole-native/M3 promotion.

Prior12meal/depot/adoption JVM fixture failures were reproduced on clean472bcccb4;
old full-native460had18not fully classified failures. They are not hidden by
claiming a whole-suite pass. Historic starved moving Mossbrook caravans remain
in the preserved schema344world; creating the schema345world is not proof that
those old actors recovered. Off-itinerary supplier diversion remains outside
this paid already-visited-supplier implementation.

Final guarded request1 COMPLETED:812→6812, all6000requested ticks reached;
subsequent ordinary read6904, fastForwardRemaining0. No new ERROR/quarantine.
No trades/missions appeared during this early observation interval; do not
represent it as an observed paid native caravan.
