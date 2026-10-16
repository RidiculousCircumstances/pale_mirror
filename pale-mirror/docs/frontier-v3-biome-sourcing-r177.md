# R177 — territorial agriculture and economic sourcing

## Outcome and scope

Fixed existing fields in three actual graybox biome profiles: fertile, ordinary,
rocky. Conditions belong to space, not settlement profession. Crop response owns
yield and stage timing; worker and player use one extraction result policy.
HOT observes/projects the same biological state as COLD. Independent rates must
not count biological waiting as worker labour or reset growth on load.

Settlement sourcing chooses feasible own production, purchased inputs, finished
goods or mixtures by total incremental cost and deadline, using current stock,
confirmed obligations, shared money and finite worker/station capabilities.
Actual production/trade admissions consume the choice. Prices alone never
cancel committed work. No duplicate inventory, cash or reservation authority.

Out of scope: changing field area, construction, fertilizer, full weather,
credit/netting/aid, new commodities. Field development/export-driven expansion
is accepted future work, not part of this delivery.

## Implementation boundaries

1. Territory conditions and crop response: pure typed catalog/spatial provider;
   biome adapter supplies the same authored regional map for graybox worldgen
   and canonical unloaded queries. No settlement-ID role inference. Ordinary
   biome defaults are explicit catalog entries, not unknown-data guesses.
2. Actual farm integration: growth cadence, extraction quantity, cargo batching,
   observed hand/replay, player drops, reserve estimates and diagnostics must
   agree. Preserve exact cells, generations and external mutation handling.
3. Economic valuation: separate physical requirements, cash spending and work
   opportunity cost. Own ask is not unquestioned replacement cost. Known bids,
   offers and executable local alternatives ground valuation; defaults are
   explicit estimates. Labour scarcity is not a wage payment.
4. Pure bounded sourcing allocator: compare available source quantities, lead
   times and costs; protect critical deadlines, count shared inputs/capacity/
   cash once, retain uncovered demand, prefer stable plan unless savings matter.
   No unconstrained global optimizer or recursive market/world query.
5. Composition: supply demand/capacity ports, market quotations and existing
   reservations/contracts remain owners. Unconfirmed cheap offers cannot suppress
   essential production; acceptance reserves exact inputs/cash before coverage.
   Existing work/accepted deliveries survive changing quotes. Mining and fields
   use the same downstream demand, including current funded export demand.

## Acceptance

### Implemented decisions

The authored quart-grid territories produce4/2/1 wheat per mature cell in
fertile/temperate/rocky biomes; rocky growth uses two biological clock periods
per stage. Flat ground geometry remains unchanged. Ruleset r16 and schema292
require a fresh test world; no migration/reinterpretation of prior field output.

Supply capabilities expose actual batch output, material/heat requirements,
separate labour ticks, finite source quantity and a shared per-worker daily work
budget. Field/extraction capacity does not create inventory or extraction rights.
Only developed accessible extraction faces are included, not buried ore volume.
The bounded allocator chooses critical deadlines first, shares stock/cash/labour,
and compares partial external offers against local unit costs. Current labour
valuation is an explicit hashed estimate0.01 currency-equivalent per work tick,
not a wage or money transfer. Biological waiting has zero labour cost. Switching
to external supply requires more than10% estimated savings; accepted work and
contracts retain their normal lifecycle. Completed trade references and declared
seed estimates replace self-ask circular input valuation.

Economic replay must never depend on an asynchronous route cache. Retained
expedition itinerary lengths refine quote timing/cost as historical estimates,
without rescanning their physical edges. Current route validation belongs to
actual supply admission and movement, not repeated economic advice. Before the first itinerary,
quote advice uses endpoint Manhattan distance with an explicit hashed1.5
distance multiplier and one departure-day margin. This is a provisional cost
estimate, NOT proof of reachability or a delivery guarantee; unknown geography
still requires actual route planning at trade admission. Unconfirmed offers
never become incoming stock or timed production inputs. This is conservative
bounded advice, not a globally optimal multi-period economy or a guarantee that
every settlement remains solvent.

Static trace each affected caller through admission, work, cargo, restart and
market settlement. Focused checks cover unequal yield and mixed-cell capacity,
growth HOT/COLD equivalence, native player/worker drops, cheap-buy/cheap-make,
unfunded/late/partial offers, input and cash contention, preserved commitments,
and no-demand production suppression. Test actual boundaries, not private shape.
Run applicable integration/package/native gates once coherent; classify inherited
failures rather than restoring obsolete behavior or claiming full green.

Deploy a verified clean ref through pack scripts, exact runtime/world identity.
Observe30–60 real minutes with bounded accelerated COLD and periodic native HOT
visits (20FPS, client/display stopped between checks). Check crop work and output,
feeding, mining, trades/returns, shortages and economy decisions, queues/TPS and
conflicts. A new world or green logs alone does not prove economic viability.
Record actual unfulfilled outcomes; no artificial resources to pass acceptance.

### Verification and deployment evidence

Source1a2820c6 implements the feature;4f2779ad admits the explicit agricultural
flat generator at the no-load geological baseline check while retaining exact
layer validation. Packeb4fd51 supplies the real biome-generator datapack.
Source82b7be4c removes repeated physical route validation from economic cost
advice. Its live30-second JFR predecessor had1348/1518server execution samples
inside ProcurementTravelForecast;1331 included ExpeditionJourneyProof.require.
The next profile has28/691 in the forecast and no route-validation path there.
These are runtime attribution samples at different states, not a controlled
whole-server speedup. Actual admission still validates itinerary geometry.

Focused economic/growth/custody/recovery tests passed;19fertile cells delivered
76grain as full and partial hands with exact recovery. Integration375required
Minecraft cases passed, including ordinary player and worker extraction in all
three biomes. NeoForge742cases:741pass/1skip. Guardrails/package passed. Two
inherited farmer tests fail identically on clean00977c52; this is not a wholly
green full-Frontier-suite claim. Their stale event-shape assumptions are not
reintroduced into production.

Runtime initially exposed a retired bakery-station heat query requiring an
already removed installed machine. Explicit RETIRED surfaces now supply zero
forecast heat; non-retired missing runtime remains an error. Focused recovery
coverage preserves the retained job, buying fallback and snapshot state.

Deployment uses a clean detached82b7be4c release in the fresh
frontier-v3-biomes-r177-20261017 world, r16/schema292. The previous R174 economic
counterexample remains intact. A graceful same-world restart retained current
jobs/trades and conserved1200currency units. First HOT visits observed Hearthvale
and Clearwater, and the farmer's16fertile cells yielded64grain; he ate, delivered
that hand and retired his job. Clearwater completed65grain sales to Ironmeadow;
other completed coal/wheat contracts are retained in live diagnostics.
The initial synchronized hunger wave209->0 and navigation waits138->0 resolved.
Final watch completed2026-10-16T20:24:58.945Z–20:54:59.576Z:31samples,
ticks46505->165715 (+119210), no sample errors, retained conflicts or STARVING
residents. Four full-pack HOT scenarios passed overall (three after final fix),
client/private displays stopped between visits and on completion. Repeated
warm visits had~1–2second entry, not the initial~90second first projection under
fastforward; inputs differ, so this is not a controlled loading speedup.
Sampled recent host-through-PM-post means3.89–29.58ms, largest sampled recent
peak215.06ms. Ready backlog/lag0 at sample instants, not a claim that no action
ever waited. End sample had5active caravan missions and bread in all12homes
(minimum8);10hungry residents each had a retained meal, none STARVING.

Economic limitation, NOT hidden by the successful technical checks: at166497,
home1 had3bread/2wheat/3coal and0.184550available money; Ironmeadow had14bread/
40wheat/0coal and0.855769money. Planner exposes unsecured production and buying
requirements, rather than declaring the missing inputs covered. At158615 the
total money remained exactly1200. These homes face real purchasing/production
shortfalls; long-term solvency/balancing is not established. No cash injection,
free aid, field expansion or new reset was used to hide this outcome. The
implemented sourcing choice and climate differences do not guarantee that every
fixed-size settlement can sustainably fund all imports under current prices.

Receipts live under active source build/r177-observation and
build/r177-final-observation (result.json, finished.json, four pilot manifests,
profiles, low-cash-final.jsonl, economy-snapshot-late.txt). Exploratory client
visits/screenshots are not human/M3 acceptance.
