# R268: production backing and famine investigation

The R267 live world accumulated grain in four fully hungry settlements while
other settlements retained coal. The world had no current scene, inventory or
custody conflicts; this was an economic throughput failure, not a quarantined
simulation. Unequal cash balances alone are not a defect (total cash remained
1200).

## Confirmed defect

`BakeryBatchSelection.selectAt` limited public batches using only a station's
already burned heat. After a firing exhausted its heat, even real unclaimed
warehouse fuel could not back the next load: the proposal fell back to one
recipe. An attended bread operation takes the same configured work interval
for one recipe or a larger load. Repeated one-bread jobs therefore wasted both
processing capacity and pickup/delivery work. The forecast already advertised
stack-sized operations, so actual admission also contradicted that forecast.

The saved revision 2048582 (tick 524017) contains one-recipe/three-wheat jobs
across multiple towns. On that exact immutable state, Sunreach's next stock
proposal changed from 3 to 63 wheat after the repair; Northwatch changed from
3 to 6 (its remaining eligible ingredients limit that batch). Towns without
fuel still propose one explicitly unfunded restart recipe.

Station energy now exposes recipe-neutral obtainable heat from its stored heat,
station fuel, exact incoming fuel and eligible unclaimed warehouse fuel. Bakery
subtracts its outstanding unfunded loads before sizing a new one. This is only
material backing: existing logistics still reserve and carry fuel, the station
still burns it, and recipe execution still requires actual heat. It cannot use
another claim, invent an arrival or spend forecast energy.

The regression checks the actual bakery selection with a cold furnace, real
warehouse fuel and then that same fuel reserved elsewhere; it also checks that
selection changes neither the ledger nor processing permission. Existing cold
recipe/recovery, partial-stack, economic-recovery and transport tests cover the
connected unchanged paths. Runtime delivery evidence is recorded in governance
CONTINUITY.md and ignored build/economy-repair-r268 receipts.

## Provisioning and funding defects

The journey forecast also treated an already hungry resident's first meal as a
series of small refills at the future hunger threshold. With live nutrition
rules, a resident at zero satiety needed three bread in the forecast even for
zero travel, although one actual bread restores full satiety. The forecast now
accounts for that first meal at actual satiety and retains the conservative
threshold yield for subsequent travel, return and margin. Crew admission,
replenishment and return advice share that same calculation. A regression
compares sufficient retained food with real nutrition transitions through a
200000-tick journey at slow, normal and fast metabolism.

Quote-time funding protected full hypothetical operating requirements even
when the planner could allocate only part of them. In the saved Dawnbridge plan,
other input wishes reduced coal purchasing to 1.038273 coins, despite an actual
2.293122-coins allocation. The unfunded remainder could veto the planner's own
allocation. Quote-time protection now retains the allocated operating portion;
existing complementary-restart and earlier-deadline protection still applies.
No balance, order consent or actual reservation is bypassed. The negative
counterexample also reduces available cash after review and proves the old
allocation cannot recreate that money.

Rollout uses the existing normal shutdown, which writes a complete canonical
checkpoint. Existing mission food targets remain their retained allocations;
new forecasts do not rewrite or remove those goods. Snapshot hydration and
new admission/replay use the same final candidate. No old WAL decision is
manually rewritten under the corrected forecast.

## Separate observations

Failed coal trips often really lack journey provisions on both sides. Existing
assembly correctly refuses such departures; bare crew availability does not
prove a provisioned trip. Dawnbridge also had a coal bid below current asks.
Redwillow's originally suspected routing stall progressed to processing in a
later observation. These findings do not justify bypassing provisions, forcing
sales, raising bids without funding, subsidizing cash or cancelling valid work.
The throughput repair must be evaluated in the retained world; it does not by
itself prove recovery from every existing material/cash shortage.
