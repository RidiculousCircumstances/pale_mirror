# Wood production, multiple-input stations and timed combustion

Accepted implementation assignment, 2026-10-26. Executor: main alone.
Source baseline:06e399640. Existing production, logistics, resource accounting,
UAE/navigation and physical-operation owners remain authoritative. No second
crafting executor or energy ledger. Current test worlds are disposable; this
changes durable station/resource schemas and therefore requires a new world.

## Product result

Forested settlements start with enough iron axes for their initial logging
crew, explicitly declared in genesis. No unrelated starter equipment or wood is
granted. Logging supplies real logs. A carpenter assignment at an installed
woodworking bench processes logs into planks, then planks into sticks. The forge
uses metal/stone plus sticks for tools. Existing repair retains the individual
item and uses its declared repair materials; it does not recreate its handle
unless the repair recipe explicitly requires one. Materials move through the
existing source/actor/station/output custody and navigation protocol.

A station has a bounded generic input inventory, separate output, optional fuel
and maintenance compartments. An ingredient may occupy any compatible input slot;
no material-specific iron/stick slots. The same item may be present in both input
and fuel. Reordering/splitting/merging uncommitted stacks is an ordinary player
edit; a changed reserved portion is handled by the existing exact effect/claim
protocol rather than overwritten. Broken stations/stores use existing typed loss,
resource drops, reconciliation and replacement paths.

Fuel stays an item until ignition. One confirmed ignition consumes one item and
retains a finite burn interval. It continues during idle/no attendance; another
item is ignited only for admissible production demand. Wood has a shorter burn
than coal, not an implicitly lower temperature. The old indefinite storedHeat
credit and prepaid recipe heat are removed from active execution. No temperature,
cooling simulation, electrical network, EU/FE converter or Create power network
is introduced in this cut. Future energy providers enter an explicit capability
boundary; burn ticks are not silently called joules or FE.

## Source findings driving the change

- ProductionRecipe has one alternative ingredient group; it cannot require both
  ingots and sticks with independent quantities.
- ProductionStationSpec and ProductionBenchBlockEntity have one INPUT slot.
- ProductionStationRecipe.portForItemKind classifies input/output/fuel by item
  identity, which becomes ambiguous for combustible recipe inputs.
- StationWorkState retains one pickup/load portion; operation accounting and HOT
  transformation also assume one stack. Extending the recipe alone is unsafe.
- ProductionStations.Machine adds an indefinite heat credit and subtracts a
  recipe obligation at start. Work clock, supply and valuation share that model.
- Forestry currently replenishes an isolated256-log reserve; wood intermediates
  are not yet a complete market/production chain.

## Ownership and interfaces

1. Recipe catalog owns explicit required ingredient groups, accepted item/tag
   alternatives, quantities, output, processing requirements and supported
   equipment. A bounded deterministic material allocator satisfies every group
   from available quantities without double-counting overlapping alternatives.
2. Common resource/container accounting owns declared logical compartments and
   lot/claim custody. Physical slots implement a compartment; they are neither
   resource identity nor a way to infer input versus fuel. Stations declare
   their layout through the existing container capability composition.
3. The machine owner owns the exact operation, complete material allocation,
   process clock and burn interval. Jobs own worker attendance and delivery
   steps; they do not decide whether a recipe completed or own a fuel clock.
4. Generic item orders and logistics transport declared portions/compartments.
   Multiple ingredients may need several trips; there is still one production
   order and existing actor/item executors.
5. Settlement planning owns demand, workforce, reserves, sourcing and trade.
   It expands all recipe inputs and chooses fuel by useful service cost,
   availability, delivery and protected material obligations. Execution follows
   the accepted alternative, not a fresh alphabetical choice.
6. Minecraft adapters own native recipe/tag and fuel-property reads, physical
   inventory/menu behavior and witnessed effects. Domain remains pure Java.
   Use the installed vanilla recipes for the supported crafting subset, with
   explicit station mapping and frozen accepted definitions. Unsupported mod
   recipe types are not guessed or silently executed.

## Implementation sequence and exits

- [x] A. General recipes, allocations and station compartments. Replace active
  single-input assumptions, update physical inventory/menu and resource/custody
  codecs. Exit: complete multi-material allocation, arbitrary input slot order,
  distinct input/fuel custody for the same item and no type-inferred packing.
- [x] B. Integrate multi-leg input acquisition, station admission/consumption,
  output and repair through existing job, claims, item operations and logistics.
  Exit: partial supply never starts or partially consumes a recipe; stale or
  foreign evidence rejects; cancellation/replacement retain real material.
- [x] C. Timed combustion and process progress. Retain ignition/current fuel,
  start/end simulation ticks and one machine clock; clamp processing to actual
  burning/attendance intervals and schedule semantic boundaries, not per-tick WAL.
  Exit: idle burns out, unlit fuel is preserved, fuel starvation pauses progress,
  HOT/COLD/restart agree, no old heat-credit path remains. Recipe time and fuel
  costs must be recomputed together; old heatPerBatch numbers are not silently
  interpreted as processing durations or electrical energy.
- [x] D. Wood content and graybox integration. Register wood/planks/sticks as
  production/economic resources, woodworking equipment/assignment and explicit
  starter axes. Compile supported vanilla recipes/tags and declared fuel times.
  Exit: actual log->planks->sticks->tool and existing repair, using normal worker
  selection, inventories, animations and paths. Declare portable bench recipe
  support without adding a separate construction system.
- [x] E. Economy and diagnostics. Expand every input and fuel alternative through
  existing demand/capacity/valuation/sourcing/reserve code; replenish logging from
  actual demand and retain bounded stock policy. UI exposes inputs, output,
  running/paused/blocked reason and remaining burn time. Exit: wood competes as
  fuel and material without double promises, impossible input bundles or an
  unexplained production label. Existing food and smelting remain viable.
- [ ] F. Verify and deliver. First review the complete wired source path; focused
  checks cover allocation/ports, interrupted or altered ingredients, partial
  transfers, repair identity and combustion boundaries. Extend the existing
  declarative native station/forestry scenarios for an actual produced tool,
  player inventory reorder/removal and a restart with an active burn. Use the
  existing full-pack client, including installed server optimization mods for
  native queue changes. No timing microbenchmarks or tests of removed internals.
  Commit reviewed changes; clean detached release/package gates; normal stop,
  exact new disposable world, publish/install, fresh identity verification,
  inspect linked real-client frames and final production/stock facts. Report
  limits honestly. No automatic push.

## Concrete initial content

- One log -> four matching planks; two planks -> four sticks.
- Pickaxe/axe: three head-material units + two sticks; hoe: two + two;
  shovel: one + two. Exact supported definitions/tags come from Minecraft.
- Bread uses80baseline work units per loaf (a21-loaf batch is1680), preserving
  the former roughly1600-tick batch throughput. The retired abstract640heat/loaf
  price is not copied into work duration. Fuel demand follows elapsed burning,
  actual attendance/speed and idle losses, so old coal consumption ratios are
  intentionally not held fixed through an invented energy multiplier.
- Coal base burn duration1600 simulation ticks, logs/planks300 (Minecraft1.21.1
  defaults); query the supported native fuel API and freeze the selected data.
- Carpenter bench is attended and does not consume fuel. Smelter remains
  automatic; forge/bakery retain their explicit attendance requirements.
- Existing iron/stone equipment identity, wear and repair remain common tooling.

Progress evidence belongs in the canonical continuity ledger and linked build
receipts. Boxes are marked only after the corresponding wired behavior exists;
this document is an implementation plan, not a completion claim.


## Integration findings (2026-10-27)

The complete reference-container boundary also participates in this migration:
its mutation receipt retains every bound compartment account. A normal layout
release closes all those accounts and the same physical custody lease in one
transaction. Omitting a compartment or supplying a stale epoch is rejected;
resource titles, quantities and physical slot layout remain unchanged.

Native verification must name the current companion pack when the source checkout
is separate. `FRONTIER_V3_PILOT_GRAYBOX_DATAPACK_DIRECTORY` selects it explicitly;
the runner retains the installed dimension digest/generator/ruleset and checks
recovery against that same input. A neighbouring historical vanilla-flat fixture
pack is not evidence for the current agricultural generator or genesis fabric.


## Verification at the delivery boundary

The integrated source is committed in83f925eb3 and a49e66757. Clean detached
release guardrails/check/build/packaged-JAR verification passed in
`build/wood-release-final-verification.log`:1947 Frontier and877 NeoForge tests,
no failures/errors/skips. Focused recovery cases cover partial multi-input loss,
repair identity and atomic closure of all station compartment accounts.

The checked-in `disposable-wood-inputs-fuel-restart.json` scenario completed its
54 ordinary player actions, graceful active-burn restart and five inspected UI
frames. Receipt:`build/wood-native/physical-wood-idle-expiry.json`. With the
current agricultural r29 pack it produced1log->4planks->8sticks->1ironaxe,
using INPUT7/INPUT8. At tick2182 the paused station had0remaining burn ticks
and2unlitcoal; reopening retained PAUSED. Explicit resume consumed3iron and
2sticks, retained6sticks and allowed the player to collect the exact axe.
Final inventory/scene conflict counts were zero. The task-owned client/display
exited. This proves the declared player/physical/recovery story, not human M3
or a complete autonomous NPC wood-stock cycle.

One earlier attempt requested fast-forward while a recovering actor body was
PREPARED; the server correctly rejected it. The scenario now observes natural
idle burnout instead of assuming that physical departure is already complete.

The broad native smoke still has three separately classified pre-existing
failures: an obsolete execution-order diagnostic expectation, fixed-tick patrol
readiness and an inactive hive/assault timeout. The affected native storage/body
and production/economy slices passed (56 and55 tests). These narrower results do
not turn that broad gate green. Final fresh-world deployment/full-pack receipt
remains the delivery exit below.


Full-pack delivery found an additional native binding contradiction: Quark's
`better_stone_tools` removes the three vanilla stone-tool recipe IDs and
supplies its tagged shaped recipes under explicit Quark IDs. An item registry
ID is therefore insufficient as a unique native recipe source. Recipe content
now declares a bounded ordered list of admitted native sources; the first
installed source must still satisfy the complete pinned allocation and output.
Missing or incompatible sources still fail startup. The list and its priority
participate in canonical catalog identity; no output-item scan, inferred alias,
recipe override or disabled consistency check is used. The failed r275 startup
saved normally; its world is retained as diagnostic evidence. Delivery uses a
separate fresh world after verification of the correction.
