# Economic recovery correction R254

Implementation of the evidenced contradictions E1–E5 in
[economic-situation-r253-20261025.md](economic-situation-r253-20261025.md).

## Boundaries and changes

* Navigation observation closes pre-write fences using actual native shapes at
  the end of the shared managed effect, including a partial/unsuccessful effect.
  Collision knowledge is independent of the operation outcome: observing a
  preimage never grants replay or acknowledges production. Context-dependent
  shapes are sampled after neighbour updates. A bounded restart scan rediscovers
  loaded retained fences without an actor/route demand; it neither loads chunks
  nor declares unavailable terrain known. Unattempted/unknown operations remain
  owned by their existing recovery protocol. Initial unprojected terrain retains
  its observation guard.
* Every registered supply owner explicitly separates installed replacement
  valuation from immediately executable supply. Labour potential and replaceable
  worn tools cannot erase the value of admitted fields or known deposits. Actual
  output remains bounded by workers, equipment wear, biology, geology and input
  custody. Destroyed land/retired stations are not invented capacity. Pricing,
  transport-resource valuation and the economic funding forecast share these
  replacement facts; no solver was added to eligibility or stock queries.
* Equipment planning compares executable/funded restoration before long-run
  cost per wear. In an urgent failed pool, an unquoted or unaffordable full fleet
  becomes a first-unit restoration request, leaving the remaining deficit visible.
  This can choose one stone-tool repair instead of requiring an iron fleet first.
  Materials/heat, real suppliers, freight and cash constrain feasibility; labour
  valuation is not a cash invoice. No forecast issues an item or reserves money.

## Constraints preserved

Full journey provisions and real paid procurement remain necessary. E3 includes
real insolvency, not just a code defect: a town with four coins cannot purchase a
28-coin cargo plus a 47-coin seller trip. Existing buyer collection and supplied
seller delivery are evaluated by the shared procurement/mission owners. This
correction must not fake supplies, force an unfunded trade, grant free foreign
food, mint money or erase historical losses. En-route purchase support remains
an explicitly priced actual purchase, not an assumed supplier guarantee.

The old world's low-price sales and consumed provisions are historical facts.
A changed valuation cannot retroactively refund them. Live checks must separate
technical recovery from physical/financial feasibility and report any remaining
balance constraint from measured flows.

## Verification

Affected checks: `build/economy-fix-verified.log` (Goods/Expedition/Equipment,
economic publication/planning, guardrails and43 native local-navigation tests);
`build/economy-fix-final-domain.log` (67 focused domain tests, guardrails);
`build/economy-fix-final-checks.log` (29 operation/navigation unit tests and50
shared-storage native tests). A new fixture initially tried wearing a historical
non-durable decorative pick: corrected to actual durable equipment;
`build/economy-fix-frozen-final.log` passes all3 recovery cases and43 native
navigation tests against final sources, including WAL fence recovery and final
actual shape checkpoint. Native negative tests deliberately reject replay.

Frozen actual snapshot2214031/tick561431: Southgate/Mossbrook retained wheat
production valuations are now2.13/1.37coins instead of0; exhausted fields still
have zero executable output. Their first restoration requests are one stone hoe
repair/one cobblestone, instead of three iron hoes/six ingots plus fuel.
See build/economy-fix-{equipment,valuations}-frozen.txt.

Frozen-state calculations below are diagnostic evidence, not completed native
deliveries or proof of a sustainable whole economy. Deployment evidence follows.

## Retained-world deployment and actual result

Codef83a9c67d installed asR254 on the same r249 world, seed126/rulesr26/schema303.
Clean detached release build/package and pack preflight/publish/install/fresh verify
passed (build/r254-{preflight,publish,install,verify}.log). Java22 servicePID1903333,
notBefore1792905755. ArtifactSHA512:
`d36cc654093c9dd262fe9c6eeceaac0df146ff242c4c4291861145d50e84854042d125c0317a49c1d2519d934429055508c6be5f122c5d2d0bbe2db2abd0485b`.

The actual Dawnbridge cell419,65,-1344 became KNOWN/production_bench after an
ordinary connected visit; it remains known after unloading. Real internal coal
shipment e7bf3c2b-03f5-3d3c-894b-adb8998171ee reached CARRYING, then left active
shipments. At612636 the station had9280heat and an attended bread operation using
3grain/640heat. At614940 the exact previously stuck task
`task:settlement-3-settlement_produce_bread-1180` is COMPLETED. This is a terminal
retained task, not inferring completion from disappearance. Receipts:
build/r254-{after-visit-fuel,cold-progress,task-terminal}.txt.

Client limitations: first run7d91c5c4 reached the actual area but its acceleration
was correctly rejected for another resident's PREPARED body; its grass-filled
camera frame is rejected as visual acceptance. The second diagnostic used a
camera feet anchor one cell above the actual ground and its ingress did not
complete. These are not successful native scenario manifests. While ordinarily
loaded, resident7-7 independently became INDEXED/RUNNING and NOURISHED; no body or
clock fence was erased. The checked-in scenario corrects the observed anchor and
uses ordinary COLD completion instead of requiring acceleration through a pending
physical transition. A formal graphical acceptance is not claimed from these runs.
Actual normal-time terminal bread production above remains independently observed.

Before changing the selected test world, the retained world's diagnostic facts
are preserved; a fresh-world run tests prevention, never retroactive repair of
its historic low-price sales/insolvency. No money or stock is injected.


## Fresh-world prevention run and R255 follow-up

Fresh selected world `frontier-v3-r254-clean-20261025`, same seed126/r26/schema303,
world UUID890c404c-8420-4aa3-beee-8b1a8d8e98fa. All five requested COLD segments
(24k*4+4k) completed, last receipt102114. Native tick rate was restored20 afterward.
The first nutrition wave164hungry at24k fell to6 by35k with342 actual food inputs;
paid inter-town trade and field/mining/bakery outputs occurred. Total settlement
cash remained1200coins. See build/r254-cold-soak.{log,jsonl}, segment receipts and
r254-after-soak-{health.txt,analytics.json}. This is a bounded prevention run, not
an equilibrium claim or proof every town can afford every future input.

The run exposed another connected E5 contradiction: Southgate at82716 requested
4cobblestone for equipment but published an undated optional need with bidRaw1.
Maintenance materials had no minimum/deadline contract into the supply planner;
individual manufacture recipes were also absent from downstream input valuation.

R255 introduces owner-declared ProductionRequirement(quantity,withinTicks) from
reserve ports through the shared supply/funding planner. Equipment declares one
next operation per work pool; remaining fleet investment remains distinct.
An existing admitted incoming unit covers that finite request, preventing repeated
emergency purchases. It neither promises on-time arrival nor authorizes output.
A separate registered individual-manufacture valuation adapter shares installed
recipe costs; repairs are excluded from new-item output valuation. No second
inventory/price model or persisted schema is introduced.

Actual frozen fresh-world snapshot365711/t113055: Southgate's next repair requires
1cobblestone now, protectedInput1/deadline0; its4.000002coin cash can fund the real
request and acquisition value is1.81coins versus prior bid0.000001. No stock,
cash, native outcome or accepted live plan was edited by this offline probe.
Mossbrook's later wear replacement gets a finite88164tick horizon; its exhausted
cash remains an actual constraint. Source: build/r255-recovery-inputs-frozen.txt.
Focused recovery/production-requirement/equipment/funding/publication checks and
guardrails PASS (build/economy-recovery-inputs-tests.log). Runtime R255 results
must be recorded separately after deployment.


## R255 retained-world delivery

Commitd3d776c20, clean detached build/package PASS (RELEASE/build/r255-release.log).
Pack preflight/publish/install and fresh verify PASS on the same selected r254 world:
PID2042069, notBefore1792907904, SHA512
`2559d570f26b4d9749f52a488a6209b7c6aabf72fac7fcfe2a2803d5afe3cb77021309e5e1e0b69e7f21ecaac80545dc1e4e2397e8f8b482a443a15a242c1abf`.
Focused72tests PASS, no failures/skips; architecture guardrails PASS.

At118348 Southgate's actual published policy changed to cobblestone1, deadline0,
bid1.81coins, after the ordinary scheduled quote review. Offline advice was not
substituted for publication (build/r255-published-plan2.txt). At frozen117168 there
was no active cobblestone sell order addressed to it (r255-procurement-frozen.txt).
A priced input requirement cannot manufacture a seller, provisions or money.
No completed restoration is claimed from the improved bid alone.

R255 first24ksegment COMPLETED at140348; analytics snapshot140467 records1200total
coins,747bread,38hungry and11paid receipts. Further paid receipts include45wheat
for57.6coins plus53.731125freight, and1coal for2.705964coins. These are actual
accepted receipts, not advertised sale orders. At153817 cumulative bread output328
versus1933FOOD:INPUT shows continuing drawdown of starter food. Operational recovery
is not proof of sustainable make/import/export balance. Uneven wealth alone is
not reclassified as corruption, and no compensating stock/cash reset is performed.

Both24krequests COMPLETED at140348/164845 (target140171/164507), actual requested
advance48000. Final analytics164948:17paid receipts,531bread,59hungry,1200coins.
Southgate30 andMossbrook19 account for49hungry. They are not proved recovered by
this run. Selected world already ran its first116kticks underR254 before the
minimum-input follow-up; this is not a freshR255 prevention proof.

Full-pack20FPS client `live-first-area-ingress.json` PASS, manifest
build/r255-native-ingress.json/run7812cafa-c51c-42b3-91c3-28425da1fda3. All three
field diagnostics ACTIVE/0mismatches. All three linked frames reviewed: complete
foreground/visible crops in rainy night lighting. This checks display consistency,
not duration-bearing work or whole-economy visual acceptance. Visit durations8.798,
3.800,4.800seconds. First projection incurred1.834s and native host peak1.865s;
strict5s/80ms acceptance is explicitly NOT met. No first-generation optimization
is claimed from this economic correction.

Client exited,0players, tick rate20 restored, no new required conflicts/quarantine.
After HOT visits ordinary delivery debt1095ticks was visible at166457 despite
recent native max6.80ms; retain this performance finding rather than declaring
an idle queue a healthy whole simulation. Receipt build/r255-final-health.txt.
Current scope closes the source contradictions and actual retained Dawnbridge
fuel/bread stall; it does not certify sustainable market balance or terminal
Southgate equipment restoration. Those unresolved outcomes are reported, not
hidden by resetting cash/stock or calling a good bid a completed delivery.


## R256 export cash/resource correction

Further connected source review found conditional export planning treating all
journey-resource expense as buyer cash. GoodsDeliveryAdvice already declared both
resource expense and invoiced cash, but ExportInterest retained only one and
EconomicExportPlanning divided the buyer cash envelope by that economic expense.
This contradicted actual buyer-collection accounting. The immutable advisory
ExportInterest now retains both explicitly; consent still checks total acquisition
value, while export cash feasibility/de-duplication consumes only actual invoice.
No prices, stock, crew nutrition gates, custody, sale settlement or durable schema
were relaxed. Focused planner/funding/recovery/publication and actual organizer
pricing checks plus guardrails PASS (build/r256-export-cash-tests.log). One test
proves provisioned collection can fund5items at2coins with10cash despite owned
resource cost; the same invoiced seller freight still rejects an unfunded sale.

This is not asserted as the cause of Southgate's present refusal. Actual frozen
snapshot585867/t170602 has a2cobblestone Redwillow offer at1.21coins, but Southgate
gets no feasible shared transport quote. It is now out of bread; fallback funding
assigns its4.000002coins to food, leaving the still-retained required repair
unfunded and its bid falls again. There is no executable complete restart proved
by this snapshot. Existing money and provisions remain constraints, and the
appropriateness of that fallback versus productive restoration remains unresolved.
See build/r256-{procurement,needs}-frozen.txt and r256-current-southgate.txt.


## R257 preventive equipment choice

The earlier fix still prioritized minimum cash only AFTER concurrent tools became
unusable. While all tools remained serviceable but their wear coverage was short,
unquoted options were compared by long-run efficiency even if the preferred iron
fleet exceeded current cash. A negative test on three still-working worn hoes and
4coins selected2ironhoes before correction (r257-prevention-before.log).

Requests now compare estimated cash reachability after genuinely executable and
quoted/funded options, before long-run wear efficiency. Missing quotes remain
missing: this creates an intention, never readiness or a supplier promise.
Unfunded/unquoted fleet requests start with one operation before failure as well;
remaining deficits stay visible and follow-up reviews remain owned by equipment.
Actual frozen170602 Mossbrook changes from2ingots+fuel to2cobblestone within59143ticks,
with2coin material funding instead0 (r257-prevention-frozen.txt). Southgate's already
insolvent/no-food state is not claimed restored by that counterfactual.


## R258 unquoted upgrade guard

The preventive test was extended to100coins after the actual early R257 snapshot
still selected an unquoted iron upgrade. It failed the exact recipe choice with
still-working tools (r258-quoted-upgrade-before.log). When neither option has an
obtainable funded input quote, necessary service restoration now chooses minimum
expected material cash before long-run upgrade efficiency at both cash levels.
Actual obtainable/executable options still precede estimates. This is no supplier
promise, forced sale, tool grant or cancellation of a current contract.
The same frozen early Southgate snapshot now plans1stonehoe/2cobble rather than
1ironhoe/2ingots+heat (r258-early-frozen.txt). Focused checks+guardrails PASS
(r258-quoted-upgrade-tests.log).

R256 deployed to fresh frontier-v3-r256-clean-20261025, same seed126/r26/schema303;
R257 installed before firstharvest (~2kticks), verifyPASS PID2132103/notBefore1792909245,
SHA512bb63054153e319102b93a39467a215bd0889df52aa01bfe533d4cbf53b1122ddbc4460fe773ef4a4688148ee0e32c29be93540a1afa7deeed22d3975e2146649.
Headless native rate200 was used as acceleration, not performance evidence.
First24koperatorrequest completed; second stopped explicitly for the R258 update,
so a complete100krequest series is not claimed. At47669 Southgate still had100coins
with65.089136 actually reserved under existing commitments. No contract was
repriced/cancelled and no resources were injected. All native dimensions saved
normally11:27:02. The task-owned observer was stopped; no user client was present.


## Remaining E5 closure: priced conditional production advice

R258's three requested segments56k completed at104742; a further32k completed at
144446. Native rate20 restored, no client. Final analytics144558:13paid receipts,
5hungry and1200coins. No new tool/restoration was proved. A correctly selected and
dated material request alone has not closed E5.

Concrete missing feedback on actual frozen102k: Southgate bids1.81 for2cobble,
has2coins available after current contracts. Registered producers have finite
stone capacity and can return conditional transport advice. At their actual asks,
that advice covers1item with zero invoiced freight but40–113coins of owned journey
resources (build/r259-conditional-quote-frozen.txt). Export planning simply discards
it when total acquisition expense exceeds the buyer's old bid. The buyer cannot
see that future replacement cost through GoodsAcquisitionPricing until ready sell
stock exists. No new goods, cash or route is proved by the conditional advice.
This is a missing price-negotiation feedback, not permission to force the trade.

Complete the connected correction in one owned slice:

1. The existing participant economic review publishes bounded typed conditional
   production counterquotes: exact supplier/recipient, item/quantity, explicit
   organizer/mode, goods price, separate resource/cash freight, source plan/input
   generation and expiry. They are estimates, never stock, an arrival or a contract.
2. Reuse the same pure production-chain/labour allocation. Consented production
   takes capacity first; only remaining feasible known capacity may underpin a
   counterquote. No per-buyer recursive economic solver or catalogue-wide query
   from eligibility/stock/diagnostics. Conditional allocations grant no executor
   production ceiling until the buyer actually consents in its public demand.
3. Buyers read only accepted current peer counterquotes. Real ready offers remain
   separate. Update acquisition willingness and cash estimates independently:
   owned provisions are economic cost, not a second cash invoice. Actual stock,
   funds, crew, trip and delivery admission still run normally. Estimates cannot
   suppress necessary local production or count as incoming stock.
4. Equipment retains the first-unit minimum separately from the affordable desired
   restoration portfolio. Unknown input quotes must not truncate every future
   material requirement to a one-item trip; estimated intentions remain unfunded
   until real availability/admission. No financed revenue before actual payment.
5. Persist quotes in the accepted economic plan under an explicit fresh schema;
   reject old worlds rather than infer missing identities. Invalidate only named
   parties/resources when economic quote content changes; technical renewal and
   generation/time differences alone cannot create a review ping-pong.
6. Verify no stock/output/cash from a counterquote, stale/wrong-owner rejection,
   no duplicated cash/labour, ordinary paid pickup and terminal tool work; then
   commit/deploy and a bounded actual preventive run. Keep old diagnostic worlds.
   No free supplies, money injection, arbitrary new price floor or forced sale.

This extends the accepted quote/planning model through explicit cost feedback; it
adds no credit, new resource sink, autonomous controller or parallel logistics.


### R259 implementation status

Typed ConditionalProductionQuote is retained by the existing accepted plan,
with exact nominal supplier/recipient/organizer, stable fulfillment wire tags,
source generation and explicit expiry. Snapshot schema304 rejects303 without
heuristic migration. Publication and hydration both validate owner tuples.
The common export allocator serves consented demand first, then uses only the
remaining cash/labour/known resource chains to price conditional requests. Only
consented production reaches executor ceilings or conditional revenue. A supplier
also retains price advice after consent to prevent quote disappearance during
manufacture. Buyers read accepted peer estimates; actual offers alone still
back supply, incoming stock, reservations and transport admission.

Acquisition value includes owned journey resources; cash estimates include only
the invoice. GoodsResourceValuation retention/food opportunity anchors remain
independent of these future estimates to avoid circular price inflation.
Expected equipment portfolios keep future material needs separate from first-unit
protection, rather than truncating each unquoted requirement to one-item travel.
Quote content invalidation excludes source epoch, expiry and unconsumed diagnostic
timing changes; unchanged participant plans are skipped before scanning quotes.
Read-only diagnostics show incoming/issued conditional estimates before bulky
history, with explicit total/shown counts. Existing exact stock, authority,
physical custody, pickup/payment and actor/route owners remain unchanged.

Initial69focused checks PASS (r259-conditional-tests3.log). Broader177trade/equipment/
station/economic checks PASS in2m47s (r259-trade-integration.log/counts.json).
Final hydration/nominal-kind/diagnostic checks and guardrails PASS in36s
(build/r259-final-identity-checks.log). Forged owner kinds are rejected both at
publication and snapshot hydration; persisted nonempty quotes survive roundtrip.
No new tool/actual recovery or balanced whole economy is claimed from unit advice.

### R260 remaining quotation publication boundary

The real preventive R259 run completed two24k requests. Its third request was
explicitly interrupted for this correction; no120k completion is claimed. Frozen
revision/tick in build/r259-mid2-accepted.txt is53831. Source plans have priced
counterquotes, buyer retained cobble bid37.298940, available commodity cash17.797714,
but all actual public cobble orders were capped at17.797714. Every producer still
had zero cobble export ceiling. Inspecting StockTargetPolicy proves a second
Math.min(bid,cashBudget) erased the resource/cash distinction after pricing.
This is an actual remaining publication contradiction, not insufficient invoice
cash or a selected-trip scheduling defect.

Publication now preserves independently calculated economic consent when a positive
cash envelope exists. It preserves desired quantity as before. Matching/procurement
still cap actual quantity at supplier goods price and real freight against current
separate budgets; no contract or reserve is granted by an interest quote. A201coin
acquisition value with1coin goods and200coins owned journey supplies previously
published a truncated limit; the regression failed exact intent.limit(), then
checks the preserved limit without a cash mutation. Existing unfunded/admission
and paid delivery regressions remain relevant. No schema change from304 and no
world reset is required for this correction.

R260 focused quote/participant/delivery/trip checks plus guardrails PASS17s
(build/r260-order-envelope-tests.log). Previous broader177 checks remain evidence
for unchanged R259 logic; they are not labelled a final-source rerun.
