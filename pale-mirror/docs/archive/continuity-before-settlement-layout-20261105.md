# Active continuity — 2026-11-05

## Current result

User requested the existing developer layout tool extended and used to repair
the bakery's brick-wall appearance. Implemented, verified, committed and deployed.
Main alone, no subagents/goal/push requested. Standing delivery policy applied;
user worlds disposable, human rd and unrelated operators/WIP preserved.
No remaining implementation/deployment work in this scope.

Report: `docs/frontier-v3-equipment-layout-authoring-20261105.md`.
Source usage: IMPL `tools/buildings/README.md`.

Concrete source defect was unrotated ProductionBenchBlock/default native state
despite rotated building fabric/operator. Markup2 now explicitly declares
workplace equipment type and local front; BuildingPlacement alone transforms
them. Schema338 StationSpec persists exact world front. Shared native adapter
uses it for initial placement, adoption and stored decoding; native block/player
placement, rotation/mirror and4model variants match all current equipment.
Front must face the declared adjacent operator; missing/backwards data fails.
Final follow-up separates wrong-pose refusal for NEW adoption from inventory
provider recognition: rotating an existing machine cannot erase its container
identity/custody or freeze stock. No guessed orientation or forced repair.

Existing export/set/point/overlay remain draft owners; new equipment/preview
commands use them. Actual registered machine geometry is inserted before shared
entrance-to-port validation. Real models and front/zone annotations render only
for the requesting operator, max32 within128blocks,600client-turn expiry and
exact receiving-world lifetime. No blocks/entities/inventories/collisions change,
no live activation/paste, no alternate gameplay planner. Runtime production,
common navigation and structural custody retain their owners. Protocol6 requires
client update. Existing current stations retain their normal logical slot ports;
no new Create processing or equipment adapter is claimed.

## Source and runtime identity

- IMPL `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`, Gitroot parent,
  branch `feat/baker-carry-orders-20260926`, cleanb5cc158462a91951f406a48fbff03b7be29fdb9c.
  ca5b2a1a1 implements tool/orientation; b5cc15846 separates placement/stock identity.
- RELEASE `/home/rd/proj/pm-r242-economic-release/pale-mirror`, clean detachedsame.
- PACK `/home/rd/proj/minecraft`, trackedclean888ed90d5e3b80b18c278107aa83bb003b541f69;
  pre-existing untracked `.f0v-baseline/` preserved. Hosted ignored artifacts
  published/index checked/pinned installed through PACK scripts. No tracked
  pack change or push.
- GOV `/home/rd/proj/pm-governance/pale-mirror`, Gitroot parent. Task changes only
  this ledger, equipment-layout report and exact equipment_layout_authoring_boundary
  architecture field. Extensive unrelated WIP remains; never stage whole
  architecture, AGENTS, contract, materialization or skill files.

Live `/home/rd/far-frontier-server`, `far-frontier-v3-live.service` active,
wrapper706280, restart1793882211. World
`equipment-layout-schema338-seed139-20261105`, seed139/r31/schema338. Previous
337world saved all dimensions17:21:27 and retained. Current338world normal saves
17:26:38 and17:36:12; two normal same-world recoveries. Final preflight/install/
verifierPASS. Source buildJava21, runtimeJava22.

Final main JAR SHA512:
`367f05575d870b3a868a935a9bbcc78c79e50e9b5cbf03d01c87d8fb5cfbc9e4d0dc68bf61dadf8d92003e49aa7d5ce33b355d3adae03a6fcd67ea7226655389`.
Visuals unchanged SHA512:
`2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.
Final summary in IMPL `build/equipment-layout-native/delivery-summary.txt`: green,
required/inventory/scene conflicts0, no new quarantine; progress NOT_ASSESSED.
Task20FPS client/private display exited; PMBakeryAudit deopped,0players; no
background monitoring. JDK21 path `/home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2`;
runtimeJava22 `/home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java`.

## Verification

Full guardrails/check/build/package:2334Frontier,920NeoForge,443native and
2dedicated native authoring passed. Focused Node scenario56passed. Client-only
receiving-world expiry follow-up compiled/guardrails/package passed; final
placement-versus-inventory follow-up unit2/native2 passed. No obsolete failures
were suppressed. Old schemas/markup intentionally rejected, fresh world used.

Real full-pack carrier `live-building-equipment-layout.json`,
SHA2560793190d583f1639f31d866949d469b1c9b0455585a205973365baca12ec470a.
First native actual-face/tool/preview/menu13actions passed, runf67856dd…;
normal restart replay passed, runbd749871…; exact final binary replay passed
3bdeaaf6-21d1-4949-9216-2b644ae82ee1. Receipts under IMPL
`build/equipment-layout-native/`. Actual west-facing packet and clear oven-front
PNG verified; real registered model/annotation preview and ordinary menu frames
opened/reviewed. Tool-authored draft equals packaged properties semantically;
compiled geometry bytes equal packaged certificate. Prior task drafts retained
in this evidence directory before re-export, not gameplay edits. A connection
reset at completed client shutdown is a task-runner disconnect, not server crash.
No whole-life M3, economy/performance/combat balance or Create compatibility claim.

## Read-only housing follow-up

User asks actual Medieval Spruce housing capacity. Pinned upstreama9b03edf…
fundamentals/residence1..5 and altresidence1..5 downloaded/decoded with existing
build/building-tools-venv, no product/runtime changes. Physical complete beds:
residence1..5=1,2,3,5,5; alternatives=1,2,3,4,5. NBT envelopesX/Z14x15,
Y19regular/Y17alternative, including template margins/foundation. Counts are
not a native path/access proof. MineColonies official residence mechanics cap
occupants by level1..5 despite the extra regular level4 bed. Current v3 housing
remains one graybox with facilityCapacity.intactHousingBeds48, not individual
home/bed assignment. Inspection receipt IMPL build/housing-source-inspect/
capacity-inspection.json. User accepts mixing house levels within one settlement rather than a uniform
level. Housing composition should meet population capacity using actual
available bed places; level is template variation, not a resident rank or
settlement-wide tier. This is a housing-planning preference, not authorization
to launch a full housing implementation yet.

## Retained context

Latest requested direction: introduce remaining Medieval Spruce buildings and
reconsider settlement layout; bed accounting is needed, resident presence in
homes is explicitly deferred. User requires ordinary flush paving, no raised
road strips. Current turn is source investigation/layout proposal, no product
changes/deploy. Confirmed bootstrap hardcodes nine offsets and all frontsWEST;
Settlement rejects duplicate facility kinds, so multiple houses need model and
consumer changes. Local circulation is X-before-Z Manhattan from Hall ports.
SettlementStructureFootprint.settlementDeckY is highest surveyed support+1;
local circulation foundation rejects terrainY>=surfaceY. Thus flush paving
needs a coherent terrain/floor/support ownership change, not only a palette
swap. Native ROUTE/ROUTE_FOUNDATION currently GRAY_CONCRETE. Only bakery is
authored; other eight functions remain graybox catalog entries. Proposed plot/
street planning and functional districts are not yet accepted or implemented.

Earlier door/access delivery: `docs/frontier-v3-manual-passages-and-access-20261105.md`.
Its native door/gate checks passed; its full graphical guard-pursuit fixture
remains unconfirmed, not silently closed by this layout work. Furniture/contact
and production wake corrections: `docs/frontier-v3-pedestrian-passages-result-20261105.md`.
Original combat tracecd22dace-4aee-4dad-a0e9-d3106501e19f retained.
Medieval Spruce bakery provenance/developer basis and earlier bread/recovery
evidence: `docs/frontier-v3-authored-buildings-result-20261105.md`. Other buildings
remain grayboxes; supplied MineColonies derivative remains private test content
without established redistribution rights. No other active assignment/goal.
