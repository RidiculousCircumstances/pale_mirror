# R243 — consistent production renewal and procurement advice

Scope: the accepted need → cultivation/production → replenishment timing → cargo
size → delivered cost chain. Same retained world; no subsidy, new resources,
forced transactions, seed-price adjustment or reset.

## Evidence before changes

Retained world `frontier-v3-r241i-clean-20261024`, snapshot 599731 and 621714:
Southgate and Mossbrook have grown from 256 to 320/319 cells (four fields).
Mining settlements have 64 cells and report suitable-land limits. Their graybox
surroundings are stone/coarse dirt; the currently admitted cultivable soils are
farmland/dirt/grass. This is a real content constraint, not proof that expansion
never executes. Do not erase terrain to manufacture agricultural capacity.

All 48 initially issued iron picks have zero remaining durability. New stone
picks and stone/iron hoes do reach users, but Southgate and Mossbrook have three
hoes each at the maintenance reserve (one remaining durability): no usable hoe.
At 599851 both and Sunreach report INSUFFICIENT_HEAT. The latter actually received
five coal at 515977 for 13.174900 goods plus 92.760385 freight. Mossbrook later
received seven coal at 609457; earlier quote refusal is not permanent inability.
Prices are dynamic, with no old 5% step or seed-price floor. Cutting goods prices
alone cannot remove the fixed expense of distant, poorly filled journeys.

## Confirmed contradictions and repairs

* Equipment demand described only one harvest of admitted crop, and only opened
  active mining cells. Repeating cultivation and advancing an active known front
  consume more durability before replacements can be made or imported. Field
  demand now reuses the bounded renewable-work forecast over the economic planning
  horizon, counting tool use rather than output yield. Mining demand forecasts
  only actively requested products in finite known geology and caps wear by the
  shared workforce calendar. No tool, resource or future output is granted.
* Accepted logistics contributed raw route-edge time plus two destination waits,
  while quote/provision planning used ExpeditionTiming, including its duration
  margin. Reserves now consume the same full-journey timing. A dated review also
  excludes expired offers; an undated retained observation remains undated.
* Dispatch-lot advice always assigned zero cash freight. Seller delivery invoices
  provisions, whereas buyer collection uses its own provisions. Mode is now an
  explicit parameter through the common advice path and tariff. Exact settlement
  remains owned by existing trade/custody mechanisms; no duplicate charge.
* The shared provisional mode selector compared provisions alone, although its
  sourcing offer and exact trip comparison also value worker time. It now compares
  the full declared economic transport cost per item. Cash freight stays separate
  and is unchanged; the forecast still grants no crew, route or inventory.

No fixed cargo minimum, automatic price discount, invented demand, free tools or
extra mining/field capacity is introduced. Production/repair/import selection,
stock/cash reservations and authoritative fulfillment retain their existing owners.

## Verification

Implementation commit `da19c0e7d` passed 144 affected frontier tests (31 classes),
a separate final undeclared-mode negative check, architecture guardrails, the
full adapter suite (858 tests) and 55 native economic GameTests. The clean detached
release passed packaging, preflight and fresh startup verification. It was deployed
to the same world with a graceful saved shutdown; no treasury, stock or terrain reset.

The first live COLD observation completed 12,000 requested simulation ticks at
checkpoint 645803 (observed 645917); native tick rate was restored to 20. No new runtime error or
quarantine was observed. This is not new HOT/visual acceptance. At 646402, compared
with startup 633599, actual flows included 85 produced bread, 90 eaten bread,
43 extracted coal and three produced stone hoes. These are measured cumulative-flow
differences, not estimated output or merely scheduled jobs.

Two commercial receipts occurred during that interval: 17 coal collected by
Ashcross from Hearthvale, and one bread delivered by Clearwater to Mossbrook.
The latter contract already existed at startup: its small, expensive terms are
not an admission made by this build. Accepted contracts are not retroactively
repriced. Files in the implementation checkout's ignored `build/` directory retain
`r243-{release,economic-tests,mode-negative,static,deploy-verify}.log`,
`r243-after-start-view.json`, `r243-final-view.json` and `r243-observation.jsonl`.

The second 12,000-tick request completed at checkpoint 661861 (observed 661913).
Both requests are COMPLETED, queue zero; native `tick query` confirmed 20.0 TPS.
At 662351, total since startup: 245 bread produced, 199 consumed, 128 coal extracted,
614 wheat harvested and four stone hoes produced. No new pickaxe production was
observed in this interval; do not substitute the equipment test result for that fact.
Dawnbridge had produced 22 bread, with the larger batch still being carried to storage.
Sunreach hunger decreased from 24 to four; Southgate remained 41 and Mossbrook 38.
Final receipts: `r243-observe-second.log`, `r243-observation-final-{view,perf}.json`.

## What this proves and what remains open

The repaired forecast boundaries are verified; a sustainable economy for every
settlement is not. At 646402 Southgate still had 41 hungry residents, 15.035641
available money and no coal. Mossbrook and Sunreach also remained short of fuel.
A retained-state execution of the actual trip planner at 646820 found no admitted
new purchase for these three: transport/provisions or eligible quotes prevented it.
This is not proof that further source defects are absent, nor permission to insert
cash or force a sale.

Dawnbridge's apparent wheat surplus was independently traced: all 134 units were
claimed INTERNAL_LOGISTICS in its pack container. The collecting caravan was
returning and still moving; this stock was not yet usable by the bakery. By 654281
the caravan had unloaded and a bakery job was PROCESSING. At 658221 the station
had 14,720 stored heat and the next exact job held 63 wheat for 21 bread. The initial
small batch used the existing heat while fuel arrived; it was not a permanent
one-bread limit. At 659136 actual food consumption had increased by three bread.
This distinguishes usable domestic supply from total owned stock. New contracts
observed after deployment included 14 coal from Hearthvale to Clearwater, two
cobblestone from Ironmeadow to Clearwater and two bread in the reverse
Clearwater-to-Hearthvale direction. These are admissions, not delivery receipts.

Price-source review did not justify reinstating a timed discount or erasing
retained resource value. The existing break-even policy deliberately permits keeping
useful stock when selling brings no gain; this is covered by MarketPricingTest.
Likewise, lower goods prices do not remove real transport supplies or create a
funded customer. No price coefficient was changed without causal evidence.

Performance remains a separate open limitation: the accelerated COLD run recorded
a 146.05 ms host peak. The restart's smaller backlog is not optimization evidence,
and the earlier 80 ms performance target is not claimed achieved.
