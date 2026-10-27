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
- [x] F. Verify and deliver. First review the complete wired source path; focused
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
not turn that broad gate green. Final fresh-world deployment/full-pack evidence is recorded below.


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


## Delivered build and observed scope

Runtime code7406a8b8f is installed in fresh world
`frontier-v3-r275b-wood-production-20261027`, seed126, r29/schema308. Exact
preflight, publication/install and fresh runtime verification passed; receipts
`build/wood-native/deploy-r275b/`. Final clean release guardrails/check/build/JAR
verification passed5m01, with1948 Frontier and877 NeoForge tests and no
failures/errors/skips (`build/wood-release-pack-binding-verification.log`).

The full-pack ordinary player run completed54actions and5personally inspected
frames on that live server:1log->4planks->8sticks->1ironaxe, arbitrary INPUT7/8,
paused unattended fuel expiry, reopening without automatic resume, explicit
resume, exact axe pickup and6surviving sticks. Required loaded-mod inventory
passed, including Quark/Create;20FPS client and private display exited after
the check. Receipt:`build/wood-native/live-wood-inputs-fuel-result.json`,
run4e439ff9-8430-4e09-986e-49ffaef78e79. Semantic progress is SATISFIED/TERMINAL.
Final live summary is green with0required/inventory/scene conflicts.

The live exploratory runner leaves build metadata null; the separate
`build/wood-native/delivery-receipt.json` retains the frozen clean source ref,
matching client-generated/server-packaged runtime sourceTree and installed JAR
checksum. The checked-in isolated scenario supplies the graceful-restart story;
this live run does not claim a second server restart or formal human M3.
Actual forestry jobs were observed with their distinct starter axes; a complete
autonomous NPC wood-stock loop was not observed in this short acceptance run.
The three broad-native-smoke failures above remain openly recorded.


## Demand-driven follow-up (2026-10-27)

The new explicit `frontier-v3-wood-demand-graybox-r30` profile removes the fixed
256-log, 64-plank and 64-stick buffers. It retains recipe authorizations, batch
bounds and complete upstream dependency flows: accepted economic production
ceilings, actual tool/material needs and selected fuel demand decide what is
produced. Zero stock policy is authorization without an unconditional stockpile.
Historical r29 content stays immutable; r30 requires a fresh disposable world.

A positive demand smaller than a recipe output still admits one whole recipe.
For example, two needed sticks require one four-stick recipe; floor division
previously admitted zero batches. Both attended and automatic stock admission
use the common bounded batch-sizing rule. The small recipe surplus is real
inventory, not a manufactured reserve target.

The audit also found a unit contradiction in bakery supply forecasts: capacity
counted up to21 recipes per operation but charged work for only one recipe.
Valuation, capacity and reserve timing now charge the same full-operation work
as ProductionStationClock. Worker capability/schedule still determine elapsed
processing; no fabricated delivery timeout is added. Movement and service delay
remain separate and are not claimed to be covered by this processing estimate.

Retained pre-change runtime evidence is under
`build/wood-demand-and-latency/` and `build/server-economy-r275b-check/`.
Minecraft maintained20TPS while canonical simulation advanced about17.21ticks/s
and its pending ordinary ticks rose2100->2824 over260seconds. Long bakery pickup/
load/delivery phases were observed despite available grain/fuel. Source confirms
StationWork COLD movement advances one cell with a20-tick work wake, rather than
using the shared timed ActorMovement execution. It uses shared path geometry;
this is a temporal execution discrepancy, not proof of a separate pathfinder.
A coherent movement-owner migration remains a separate structural correction.

A45-second same-JVM JFR found costly economic reviews surveying retained field/
forest terrain, many canonical commits/validation samples, and5.34seconds in
recorded journal FileForce events. Navigation workers were mostly parked. Server
parks in this recording were natural tick waiting, not blocking future joins.
These are measured contributors, not a complete additive attribution of the
backlog; cache eviction rate and exact preparation/commit gate impact remain
unconfirmed. No COLD/HOT speedup claim follows from the demand/forecast fix.


A longer pre-deploy counter delta (about1295wall seconds) recorded23913canonical
ticks,25896host ticks and106362canonical journal forced groups: about4.45forces
per simulation tick. Pending ordinary ticks rose3988->5971. Scheduled preparation
completed19353calculations, with225.76seconds accumulated execution time. Source
confirms advanceTo yields on an unfinished scheduled calculation and on each
unpublished WAL predecessor. The server thread remains available, while canonical
advancement waits for those serial publication boundaries. These counts establish
frequent serialized work; they do not make the component durations additive or
identify all time spent waiting. Receipt:`build/wood-demand-and-latency/predeploy-delta.json`.

Correcting full-batch costs also changes economically preferred tool materials.
The conditional-production quote fixture now explicitly supplies historical high
iron prices for its stone-input negotiation story. Its no-phantom-stock, cash
versus own-provisions, price feedback and recovery assertions remain intact;
production is not changed to preserve the fixture's accidental old preference.
