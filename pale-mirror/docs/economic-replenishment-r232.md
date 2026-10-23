# Economic replenishment and distant settlements

Accepted scope: production versus procurement, quantity and departure share one
rolling planning horizon. Stock, money, execution, routes and transport assets
keep their existing authoritative owners. No personal money or artificial income.
Main implements without subagents; no push. Fix Stonefield hunger first (see
`stonefield-hunger-r232.md`). New test world uses irregular3–5x separations.

## Implementation sequence

1. Preserve the current economic/supply owner and bounded recipe expansion.
   Derive planning coverage from real route/provisioning forecasts as well as the
   calendar. Finite offers and dated commitments stay distinct. Protect the
   shared cash needed for essential production inputs.
2. Introduce a reusable transport-capacity/cost quotation from actual available
   walking workers and pack animals. Price/sourcing advice and exact trip
   preparation must use the same options; a stack is a physical parcel, not an
   economic lot. Both fulfillment modes distinguish cash invoices from the
   value of owned provisions and worker time.
3. Replace fixed preferred-lot/load dispatch with bounded economic lot advice:
   useful demand within the horizon, current purchasable quantity, transport
   capacity, receiving capacity, shared cash and full delivered value. Departure
   compares available service benefit against safe waiting until a dated need.
   A deadline allows partial relief, never creates stock/funds or makes a tiny
   trip automatically efficient. Unknown future offers are not commitments.
4. Wire the same quantity/timing/cost semantics into sourcing, quotes, selection
   and exact admission. Evaluate bounded parcel/capacity breakpoints; no global
   per-tick optimization or detailed routes for every hypothetical offer.
5. Add a separately declared irregular distant graybox layout. Keep settlement
   and building sizes unchanged; change inter-town geography, bounds and worldgen
   consistently. Existing saved worlds retain their declared geometry.
6. Verify production/buy alternatives, limited funds/stock, mixed cargo, distant
   delivery, pack-asset unavailability and safe/emergency dispatch. Deploy a new
   world, inspect HOT/COLD work and commerce, commit reviewed changes. Analytics
   must distinguish forecasts/deferrals from actual shipments and receipts.

## Implementation and verification

Hunger cause/fix is committed and deployed as `6b361791a`; archived ambiguous
operations remain isolated in the old disposable world, not blindly replayed.
See `stonefield-hunger-r232.md` for native admission/recovery evidence.

Economic source selection now quotes finite load-dependent tariffs. Buyer
collection pays cash for goods; owned provisions and worker time remain economic
costs. Repeated allocations charge the same uncommitted trip once. The shared
transport adapter compares walking with available pack assets; advice uses a
bounded potential crew, while actual admission still selects eligible workers,
reserves the exact animal and proves routes/provisions. No asset is fabricated.

`ReplenishmentLot` uses gross useful requirements over the common horizon,
protected funding and transport cost per unit. It examines charge/capacity
breakpoints; ties prefer less locked capital. Today's small net deficit does not
become the next lot size. Nonurgent small remainders wait; mixed goods can share
the trip; the last safe departure for an uncovered dated need allows partial
relief. This is a bounded rolling policy, not a claim of a globally optimal
multi-town solver or a promise that every emergency journey is efficient.
Legacy preferred-lot/load fields remain in pinned historical balance records;
no active dispatch calculation reads them.

R26 declares a4096×4096 layout with pairwise distance ratios between3and5, irregular
spacing, unchanged towns and relocated biome regions. Bootstrap reconstruction,
canonical COLD surface data and native agricultural generation share the exact
pinned declaration. The pack datapack must select that same profile. Fresh-world
quarry placement can examine adjacent exterior ground when roads intersect the
four cardinal centre lines; all owner/bounds exclusions remain mandatory.

Verification: final focused domain campaign158/158PASS
(`build/r232-final-economic-tests.log`), including bounded budgets, provisions vs
invoices, recurring small remainders,100-item/capacity-limited loads, actual mixed
shipment/settlement/return and in-flight recovery, and distant genesis recovery.
Native adapter-mirrors12/12PASS includes generated distant biomes/surface matching
unloaded geometry. GuardrailsPASS. No full game-test suite or long-run economy
balance acceptance is claimed by these focused checks.

Clean-ref package, fresh R26 deployment and live HOT/COLD diagnostics are recorded below.

### Distant-world startup correction

The first live startup exposed the compact world fabric ceiling of65536cells.
The immutable, region-indexed world declaration now allows262144; active chunk
and tick budgets are unchanged. Static full-fabric compilation also detected a
road/building overlap with per-town coordinate jitter. The authored orthogonal
road layout now uses aligned rows/columns with unequal gaps (880,1040,1160 along X;
1360,1480 along Z), preserving the requested3–5x distance ratios without crossing
buildings. A new recovery check compiles both initial and stable native fabric;
28layout/fabric/architecture checks and guardrailsPASS. No server-start success
is claimed for the failed first attempt. The partially initialized world is reset.

A second startup exposed a separate4096-node inspection topology ceiling on
distant declared roads. Ordinary settlement infrastructure assessment now reads
the validated sparse road-availability ledger directly, without expanding the
whole route. Explicit engineering/physical-loss graphs retain their own finite
16384-node/32768-edge envelope; native navigation budgets are unchanged. The
distant-layout recovery test also blocks and reopens an exact road support for
every settlement. Focused route/layout tests and guardrailsPASS
(`build/r232-distant-roads.log`). This does not claim arbitrary-world graph scale.

### Live distant search correction

At57599–62403 the distant lane grew from42 to100 waiting requests, with an
oldest wait above1300planning turns; trade remained unadmitted. Static inspection
found `prepareReuse` restarted macro/corridor attempts whenever other searches
published another cache image. Failed reuse was repeatedly placed ahead of the
primary regional search. A controlled cooperative-slice regression fails before
the correction: the stable-cache search reaches ESTIMATED while the equivalent
search exposed to unrelated hint publications stays PLANNING.

Each search now captures its finite reuse hints once. A failed hint resumes
ordinary complete exploration; it never means NO_PATH. Fresh queries can use
new hints; changed terrain still follows existing dependency rejection/rebase.
The obsolete cache-publication counter is removed. Focused hierarchy, worker,
regional invalidation, incremental and churn tests plus guardrailsPASS.
Native pre-correction mining diagnostic `r232-native-mining.json` passes loaded
Stonefield, away, return, zero required/inventory/scene conflicts at56931. Rosa
retains exactwork15, delivers batch3 and returns toward coal. Frames show mine
geology; this is not a full visual harvest/food cycle acceptance. Native shutdown
logs CAPTURE_UNAVAILABLE at unloaded actor handoff; subsequent continuation is
checked, not reclassified as successful capture.


### Quote route fan-out

Even after hint reuse stopped starving primary searches, current distant-world
queues included roughly90 regional estimates. `GoodsTripPlanning` requested both
route directions for every feasible peer/warehouse before choosing an offer.
Quote comparison now uses the shared endpoint travel forecast (retained exact
journey length when available, otherwise declared provisional distance). It is
cost advice only: it never proves arrival, creates incoming stock or authorizes
travel. Only the selected trip requests physical route/provision/crew admission;
actual journey cost, deadline, offer ranking and shared funding are rechecked
before the immutable draft is committed. Pending admission publishes neither
reservations nor actor assignments. Rulesets with sourcing disabled use the
geometric lower-bound distance, without inventing a sourcing policy.

The previous test asserting all-quote regional requests now checks the intended
boundary: one selected pending proof, no speculative regional searches, and no
published cargo/crew/cash reservation. Existing mixed shipment completion and
in-flight recovery coverage remains in place.

Verification of the quote-admission change:63focused trade, sourcing, dispatch,
lot and mixed recovery testsPASS; guardrailsPASS
(`build/r232-selected-route-tests.log`).

## Live R26 result (2026-10-23)

Deployed clean source54572fb75 in frontier-v3-r232-distant-20261023, seed126/r26.
The pack selects the same hashed declaration (root38e27bc). Retained the new
world through follow-up repairs rather than discard its real trade obligations.
Runtime restarted20:19:26+05, deployment verification passed. Code SHA-512:
`cb6e4c6fc45a7ac007acbdd6419e165c8f2740f03d155501df144e8a8c566db86aed0337376e792a863bfd3c53d43d7927154b0f36599a2c2da2c9783de39e9b`.

Canonical commerce now admits and completes real shipments: first receipt97067
Hearthvale→Ashcross coal1, goods19.999999 plus delivery8. Other completed routes
include Hearthvale→Clearwater, Westhaven→Southgate/Sunreach/Mossbrook and
Redwillow→Dawnbridge. At126687 eleven resource receipts are retained, including
coal13 at123214. The shipment grouping is separate from per-resource receipts.
A seller-delivery mission Dawnbridge→Redwillow carries wheat131 (64+64+3) on an
actual reserved donkey with one human. A buyer-collection mission for wheat111
(64+47) also reserves a donkey. The latter is still preparing/travelling toward
assembly; neither admission nor carriage is reported as confirmed delivery.

Small coal lots still occur when the horizon's useful requirement and available
funds are small or need is due. There is no unconditional64-unit minimum and no
invented funding. Larger distances make transport more expensive; they cannot
manufacture demand, capital or supply. This run proves usable delivery/cash
settlement and large-load admission, not long-run economic equilibrium.

Distant search no longer accumulates every possible quotation: a late sample has
zero waiting distant searches and two active with oldest26planning turns, versus
roughly90waiting before selection-only proof. Ordinary full server tick cost is
not solved by that correction: one64-sample window averaged about50ms with a1.46s
peak; startup had a4.94s peak. Older JFR attributes substantial inclusive CPU to
GoodsParticipantAssessment. These are retained performance limitations, not a
claim that the world now scales without further profiling.

HOT/COLD hunger acceptance is recorded separately in stonefield-hunger-r232.md.
Native pilot16468bba-af1c-4565-8ee3-97228476fc16 completed and exited. No testclient
is left running. All sampled canonical/inventory/scene conflict counts were zero;
the diagnostic verdict covers retained conflicts, not absence of every latent bug.
