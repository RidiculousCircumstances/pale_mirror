# R226 — autonomous smelting and a local metal warehouse

## Accepted scope

Separate graybox smeltery building and nearby storage preferring ore/ingots.
Raw iron + fuel -> iron ingots; no Create machines, smithing or invented ingot
consumption in this cut. Use actual stock, existing extraction and shared
shipment/UAE/navigation/custody. Workers load and collect; the automatic station
owns processing, without retaining a resident for its duration.

## Implementation boundaries

- Recipe/catalog owns ingredient, product, heat and processing mode. Equipment
  declares capabilities; the installed station owns inventory and one operation.
- Add generic settlement-directed automatic-station admission, distinct from a
  bakery job and a player's explicit operation. No fake bakery/player owner.
- Extend existing station supply through input and output ports, using existing
  internal shipments. Warehouse selection remains filter/priority/capacity-based;
  breaking the preferred chest must not bind production to a dead container.
- Graybox composition declares building, access, equipment and storage once;
  generation, movement and recovery consume the same declaration.
- HOT effects and COLD processing use the existing station clock and exact
  inventory receipt protocol, never simultaneous vanilla/simulation smelting.
- New immutable ruleset; disposable fresh world is authorized. Initial ingot
  reserve accepted by the user:64 ingots per settlement; no free ore/ingots.

## Acceptance

Recipe/energy/stock accounting and explicit initiator roundtrip; partial input,
occupied output, missing fuel, player withdrawal, destroyed/replaced storage and
machine; autonomous processing while workers do other work; HOT/COLD and restart
retain one operation/output. Check real building/warehouse and load-process-unload
with the existing full-pack client. Commit and deploy; no automatic push.

## Source findings

Shared station clock already supports AUTOMATIC; existing task admission is
bakery-specific. Station supply only carries fuel, and production ports reject
OUTPUT even as pickup endpoints. These boundaries must be extended rather than
duplicating the bakery executor or adding a separate hauling runtime.

## Implementation and current evidence

Immutable r24 adds automatic recipe/policy, institution initiator, shared
reserve/supply/stock adapters and SMELTING equipment. Three-slot PM smelter uses
the existing machine clock, heat, physical effect receipts and recovery; ordinary
internal shipments serve input/output. A separate 7x7 building and nearby
priority-100 ore/ingot/coal warehouse are added at each settlement. Reserve64,
1 raw iron ->1 ingot,200 processing ticks and200 heat per ingot, at most8 per
operation. No startup iron, fictional consumption or vanilla furnace clock.

Focused domain tests cover partial batches, actual heat, reserve stop/resume,
mining demand, codec recovery and real common internal-shipment output pickup/
delivery. Affected NeoForge v3 tests and guardrails passed. Native production
effect slice passed5/5. Broad NeoForge invocation was stopped in unchanged
reference-world full-year presentation test; it is NOT a full-suite pass.
Local receipts: build/r226-affected-tests.log, r226-native-production.log and
r226-package.log. Full-pack live scenario is checked in; deployment, native
building/logistics and restart result are pending, not yet claimed.
