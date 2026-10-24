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

Pending: focused affected checks, offline retained-state comparison, packaged
build/restart and actual receipt → production → meals observations. Current edits
are not yet a verified deployment or proof that historical cash losses have healed.
