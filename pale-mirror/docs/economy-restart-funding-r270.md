# R270 — independent production restarts and cash-cycle check

## Confirmed defect and repair

The R269 funding rule made all protected production inputs one global bundle:
an unquoted or unaffordable unrelated input erased every restart priority.
The purchasing boundary could independently repeat that veto by protecting
all same-deadline input wishes.

The supply solver now emits ephemeral, complete input bundles from the same
selected recipe/fuel/committed-work pass. Economic funding admits those bundles
independently, ordered by deadline, essential output and deterministic product
order. Shared stock and the real treasury are allocated once. Every purchased
member needs a full current quantity-aware quotation including cash freight;
late recovery remains distinct from on-time coverage. Optional partial funding
cannot promote an incomplete bundle. Buying a finished recovery input and
producing that input remain separate alternatives, not a self-dependent recipe.

The accepted EconomicPlan retains its existing durable shape. Its funded item
set describes contributions to complete admitted bundles, rather than coverage
of all unrelated uses of that commodity. Purchase advice preserves funded
complements, does not protect an unfunded sibling as if it held money, and
bounds a shared commodity's protection by its allocation (with current one-unit
repricing). An already-arrived input with zero remaining restart quantity
creates no continued financial protection.

No hardcoded coal/bread order, free cash, starter stock, debt, imposed price,
waived provisions, new inventory holds or physical/persistence changes.
The existing financial and logistics owners still revalidate and admit trades.
No schema or ruleset change; retain the current world and allow ordinary
reviews to replace prior accepted advice.

## Check of the current economic cycle

Evidence is the immutable CRC-checked checkpoint revision2677907, tick691653,
world c3762b96-ead4-475e-847e-04e60dfba421, plus read-only observatory receipts.
Raw evidence is under ignored `build/economy-restart-r270/`: checkpoint,
CycleProbe/RecoveryTransportProbe and their output, before-view.json,
trade-days.json. Probe calculations neither edit saves nor grant simulated
outcomes. This is COLD diagnosis, not graphical HOT acceptance.

There is no demonstrated sustainable recovery of the existing crisis:

* Ironmeadow received60.864390 for45 coal at day24, then spent55.335882
  on three bread including freight at day25. Only6.592975 remained.
* Westhaven received52.26 for39 coal at day27. Its subsequent day28
  purchases are three bread for49.674858 including freight and two wheat
  for3.806310, total53.481168. This observed sequence consumes1.221168
  more cash than that coal receipt and does not supply a whole day's food.
  It is not proof that every possible alternative trade is unprofitable.
* Dawnbridge can afford two coal at1.34 each with18.900018 unreserved
  cash. A live eligible Redwillow coal source exists. The65,750-tick
  round trip needs at least three bread loaded by the seller or four by
  the buyer's actual best-fed one-person crew. Both have zero available
  warehouse bread and fail material provisioning; both-mode quote is empty.
  This is a real logistical input constraint, not the global priority bug.
* Mossbrook's Westhaven source requires three bread for either organiser
  over59,000 ticks; both have zero warehouse bread. Ashcross and Southgate
  examined sources likewise fail provisioning. These are explicit sampled
  alternatives, not an exhaustive proof over every future world state.
* All twelve recomputed snapshots still have no cash-funded restart item:
  ready priced alternatives are absent or inputs are already held. The repair
  therefore cannot honestly be presented as a cure for the current starvation.

Current advertised field capacity supplies2671 wheat over a common96,000-tick
window versus1419 recurring bread requirements, needing4257 wheat at3:1,
before clearing325 immediate food requirements or provisioning caravans.
That is62.7% of recurring grain requirements from currently exposed capacities.
This is a planner snapshot, **not** proof of the world's hard maximum: further
field development, worker/tool recovery and later mining faces can change
capacity, and existing/carrying stocks can bridge a temporary deficit. The
presently exposed coal faces likewise must not be mistaken for all coal in
all deposits. A balanced permanent cycle is not established by these forecasts.

Cash totals1200. Inequality alone is not an invariant violation. The observed
problem is the combination of real input/food transport prerequisites, small
expensive purchases, and currently insufficient new-output advice; cash
conservation or successful isolated receipts do not demonstrate recovery.
Further balancing must compare provisioned recovery packages and feasible
recurring production/export/import quantities, not erase material constraints
or force prices merely to make balances equal.

## Verification

Final affected check: guardrails, Economic*Test, SupplyPlannerTest,
Procurement*Test, SettlementEconomicPublicationTest, native JAR packaging and
verifyPackagedJar pass (`verification-final.log`,25s). Focused additions cover
independent unquoted/expensive siblings, missing complements, shared stock and
cash, a shared input with a larger unrelated requirement, finished-input versus
manufacturing alternatives, purchase-boundary priority, and clearing protection
after arrival. Existing funded-plan codec round-trip now carries a nonempty
funded item set and retains its negative scope case.

Unchanged native conversion, navigation and physical persistence evidence is
reused; this patch changes pure economic advice. Deployment and follow-up are
recorded in canonical continuity and ignored R270 runtime receipts.
