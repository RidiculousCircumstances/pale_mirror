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

Next: clean-ref package, fresh R26 deployment and live HOT/COLD diagnostics.

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
