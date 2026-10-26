# Recovery shipments — R273

## Proven cause

Two R272 contracts carried one coal: Hearthvale -> Clearwater (buyer collection)
and Stonefield -> Dawnbridge (seller delivery). Their original bids and asks
were quantity1, not a display of stacks. Later stock and larger bids are not
evidence that a larger offer existed at their admission.

The retained tick800 plans expose a concrete contradiction: coal's total selected
domestic target is2, stock/incoming0, but purchase1. Direct equipment service
and upstream smelting reused the same forecast unit. Both buyers show the same
result. Their finite restart deadline then permits immediate partial dispatch.
One coal costs2coins; the Stonefield delivery additionally invoices14.990096.

## Responsibility and change

The supply solver now accepts explicit direct uses separately from ordinary
inventory buffers. The economic owner supplies the direct requirements already
selected for its common horizon. The forecast allocator earmarks existing stock
for those uses before evaluating additional recipes, sums direct and upstream
requirements, and reports upstream inputs separately so the economic owner does
not add direct demand twice. No durable stock claim, export ban or invented
production is created. Resource/owner identifiers do not select special logic.

Dependency recovery quantities use the owner's resolved replenishment lead,
including logistical estimates, rather than always retaining the commodity's
initial lead. Unknown sources retain an explicitly provisional estimate; no
estimate forces a delivery or becomes dated incoming stock.

Dispatch separately asks whether a useful recovery remains uncovered at this
route's arrival/review horizon. Current stock, dated incoming cargo and secured
local production can cover it. A historical finite input deadline alone cannot
turn a reserve remainder into a new emergency. Unsecured recovery production
does not count as supply. Direct food consumption retains its dated coverage
calculation and prefix-shortage handling.

Market matching still chooses among actual consented, funded quantities;
logistics owns exact route/crew/provisioning admission. Existing shared freight,
capacity-breakpoint and landed-cost comparisons remain in force for both modes.
Collection food is a real resource expense, not a second cash invoice; labour
remains comparative opportunity value rather than an invented wage payment.
No fixed minimum64, free resources, price override or automatic contract growth.
A genuinely useful small partial shipment remains possible when that is the
feasible recovery alternative. Existing admitted contracts are not rewritten.

## Verification

Read-only recomputation of the SAME sealed tick800 state now requests2coal for
both buyers, matching their target2. Focused tests cover independent direct and
recipe uses in both identifier orders, no double booking of held fuel, real lead
coverage, existing deliveries/local output versus speculative output, ordinary
consolidation and a permitted small emergency. Funding, price, matching, cargo
and codec/recovery checks are retained under `build/coal-trip-diagnosis-r272/`.

The first affected run also exposed a stale partial-unload assertion, reproduced
unchanged on the clean deployed8c15d813a baseline. Capacity loss retains a valid
service declaration with `readyToEnter=false`; it does not erase the declaration.
The test now checks that contract and denied admission, then verifies resumption,
partial payment and recovery. Production service-access behavior is unchanged.

Runtime observation is separate from these checks. This repair does not assert
that every one-item trip is wrong, guarantee a global trade equilibrium, or
manufacture additional demand merely to fill a caravan.
