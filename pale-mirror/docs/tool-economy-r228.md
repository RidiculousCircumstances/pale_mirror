# R228 — tools as productive equipment

Status: accepted scope; implementation in progress. Main alone; no subagents or
push. Complete the connected feature, verify, commit and deploy a clean ref.
Fresh disposable world is authorized. R227 remains live until a verified release.

## Product result and decisions

Residents obtain actual suitable equipment before mining, digging, tilling or
harvesting. Work consumes its useful life; movement, contention and failed work
do not. Worn equipment can be repaired or replaced. Settlements forecast demand
from planned work and concurrent staffing and choose repair, manufacture or
purchase through the existing economic planner. Every graybox settlement has a
forge and retains its smelter, independently of owning a mineral deposit.
Availability is not an instruction to manufacture: compare complete delivered
chains, real capacity, costs and deadlines. No artificial iron consumption or
promise of permanent demand without actual work.

Initial content: pickaxes for rock/ore, shovels for earth, hoes for tilling and
harvesting. Harvest ability on a hoe is explicit PM content, not a vanilla wheat
requirement. No wood/sticks input in this cut. Repair consumes material and labour
(and recipe-declared heat), keeps the same exact item identity, and occurs at a
station. New manufacture creates a new exact instance. No scrap item or arbitrary
maximum repair count. Retain a non-iron bootstrap route using stone equipment;
forecast replacement before exhaustion. Weapons remain future consumers of item
condition/equipment/production, not an expanded combat scope.

Autonomous work retains the catalog-declared maintenance reserve (initially one
durability unit), returning equipment before destruction. This is not immunity:
player use still follows Minecraft breakage. A tool returned by a player retains
its observed condition, including legitimate external repair; it is not reminted.

## Ownership and connected implementation

1. Equipment definitions describe capabilities, efficiency and lifetime.
   Operation requirements describe capability/material eligibility and use cost.
   Exact inventory remains sole owner of item identity/title/custody; condition
   belongs to the individual item. No parallel tool stock ledger. Definitions
   are immutable ruleset data with deterministic persistence/digest semantics.
2. Work callers request equipment, not item registry names. Shared selection and
   acquisition compose existing storage, item orders, service access, navigation
   and UAE. Keep equipment across need interruptions when still applicable.
   Replace unfit equipment without corrupting the field/site or monopolizing it.
3. Confirmed operation result and tool use share the existing durable effect
   protocol. HOT/COLD/retry/recovery cannot duplicate work or wear. Minecraft
   item state and canonical condition agree; player removal/use/death must not
   restore a pristine tool through projection. Retire active toolKind equality
   checks, not just the initial selector. Preserve actual drop eligibility.
4. Extend common recipes/stations for exact-instance creation and maintenance.
   Materials and the repair target belong to station custody while processing;
   no special forge logistics or resource accounting. A repair target is not
   a fungible ingredient. Preserve generic input/fuel/output responsibilities.
   Multiple ingredient groups are needed only where actual recipes consume
   them; do not implement unused wood or speculative crafting facilities.
5. Add forge content/worker activity through existing registered owners and
   general production paths, not BakeryWorkState branches or a separate tick loop.
   All settlements may use imports. Common accounting must retain condition
   through storage, loose items, player interaction, shipments and sale.
6. Supply forecast counts usable equipment/concurrent slots and remaining work
   capacity; do not count one worn tool as a fresh one or reserve it twice.
   Existing planning compares complete repair/manufacture/purchase alternatives
   and propagates material/heat demand upstream. Replace the unconditional iron
   reserve as tool demand becomes the active iron consumer; no double demand.

## Source findings guiding the cut

- ExtractionToolSelection, ExtractionWorkEffects, ExtractionWorkPolicy and the
  native block adapter use concrete toolKind equality. Surface-cut rules also
  require the same tool as the mineral, blocking a shovel/pickaxe work frontier.
- ExactItemStack has no condition. Copies in ExactInventory and native
  materialization need one lossless shared representation before enabling wear.
- ProductionRecipe has one fungible ingredient group and a kind/count output.
  Maintenance needs an exact target and identity-preserving result, not a fake
  newly manufactured tool. The current three machine ports are not a general
  proof of multi-input or exact-output support.
- Existing engineering equipment is outside the requested active feature scope;
  preserve its functioning through shared representation, do not activate combat.

## Acceptance and work order

Implement in connected increments: exact item/capability foundation and mining;
shared acquisition + field operations; station manufacturing/repair + logistics;
whole-chain economics + graybox. Compile while tracing real callers; focused
tests at meaningful boundaries, not a new campaign after every helper.

Required evidence: suitable interchangeable tools; unsuitable/no tool waits;
one operation/one wear across HOT/COLD and recovery; player removal or mutation;
repair preserves identity and consumes real materials; manufacture output can
actually equip another resident; tools can be supplied through trade; forecast
does not duplicate iron demand and preserves a bootstrap path. Native client
checks show work, station input/output and worn item state on the verified build.
Use actual elapsed work and material throughput for balancing, no forced stock
or funds in live economy. Report evidence limits and remaining defects explicitly.

## Connected delivery and market details

Individual equipment is carried by the existing `Shipment` lifecycle with typed
`ShipmentCargo`, using the same UAE, service access, movement, group and physical
operation owners as fungible freight. A contract allocates the actual item identity
and condition; it does not manufacture a fungible lot for that object. Loading and
unloading change custody. Acceptance changes title and settles reserved money.
Buyer collection changes the same shipment to owned transport after pickup.
External loss closes the outstanding promise without destroying or recreating the
observed object. Same-container rearrangement does not settle a sale.

The first catalog quotes pristine stone/iron pickaxes, hoes and shovels. The
commodity specification carries the minimum remaining-life quality explicitly;
there is no tool-name inference in logistics. Used equipment retains its observed
condition and uses local maintenance; lower-quality market segments are not enabled
by this initial content. No fixed tool reserve or compulsory purchase is introduced.

Equipment acquisition allocates the actual work-pool shortage across repair,
manufacture and funded, timely delivered offers. Comparison includes useful life,
worker time with the tool's speed, recipe cost and delivery cost. Existing promises
and owned collected cargo count once. The common economic planner still owns
purchase funding and upstream raw/fuel sourcing. Necessary actual equipment is
protected at quote/reservation and at the pre-loading commitment review. Available
surplus may be sold; no speculative export stock or artificial revenue is created.

## Implementation verification (2026-10-23)

The connected implementation is in place, schema301 / tool-graybox r25. The
forge is at settlement offset (-20,-26); the full structural plan for all towns
checks that its floor does not intersect roads or other facilities. Exact tools
and fungible goods share the same capacity, transfer, receipt and title owners.
The station owns both manufacture and repair transformations; its worker supplies
transport and labour. Field grain delivery precedes returning equipment, including
when a grain warehouse rejects tools.

Evidence retained under implementation `build/`:

- `r228-release-check.log`: all 1,808 Frontier tests passed. The initial native
  gate failed 14 obsolete setup assumptions; the following corrected run covers
  those cases. Unchanged domain/Visuals and architecture gates reused Gradle's
  dependency-bound results, not claimed as newly executed tests.
- `r228-native-release-final.log`: 821 NeoForge JUnit tests, all399 GameTests,
  guardrails, build and packaged-JAR verification passed (5m2s).
- `r228-storage-support.log`: subsequent common socket provenance correction,
  focused layout checks and guardrails passed. Resource warehouses on authored
  ground wait for that exact geometry rather than misclassifying an unfinished
  road as missing natural ground. No forced block replacement.
- `r228-equipment-repair-native4.json`: actual graphical client, graceful server
  restart, same worn stone pick (damage100→35), real material consumption and
  worker delivery to its warehouse; the exact production order retires. Final
  chest frame reviewed. The early frame was occluded and is not visual evidence
  of working at the forge. The scenario camera was corrected for the subsequent
  run; full-pack release/deployment observation remains to be recorded.

COLD worker manufacture/repair survives snapshot recovery at every phase;
interruption preserves the target; external removal cancels the exact order.
Individual trade verifies reserve/pickup/delivery/acceptance/payment, HOT
before-effect recovery/replay and external withdrawal. This does not establish
long-run market balance or demonstrate an autonomous tool caravan in the client.
