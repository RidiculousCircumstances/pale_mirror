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

Clean artifact deployment and connected runtime inspection remain pending.
Frozen-state calculations are diagnostic evidence, not completed native
deliveries or proof of a sustainable whole economy.
