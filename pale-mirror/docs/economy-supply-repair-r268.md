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

## Separate observations

Failed coal trips often really lack journey provisions on both sides. Existing
assembly correctly refuses such departures; bare crew availability does not
prove a provisioned trip. Dawnbridge also had a coal bid below current asks.
Redwillow's originally suspected routing stall progressed to processing in a
later observation. These findings do not justify bypassing provisions, forcing
sales, raising bids without funding, subsidizing cash or cancelling valid work.
The throughput repair must be evaluated in the retained world; it does not by
itself prove recovery from every existing material/cash shortage.
