# Oriented equipment authoring and bakery repair — 2026-11-05

## Delivered boundary

Sourceca5b2a1a13a2a7048bc20119ab875f2d03de7562 extends the existing building
authoring flow; it does not create another gameplay planner or placement owner.
The reported bakery showed the back/brick side because ProductionBenchBlock
had no facing property, its model front was permanently north, and initial
installation and stored-container construction used default block states.
The building transform rotated its fabric and worker posts but not equipment.

Building markup2 now explicitly retains each workplace's registered equipment
type and local front. The front must face its adjacent declared operator.
BuildingPlacement transforms that direction together with all support/volume
coordinates. Schema338 ProductionStationSpec retains the exact world direction;
installation validates the facility mount. The shared native container adapter
uses that same state for initial placement, adoption and stored observation.
Player placement faces the operator using ordinary native block placement;
rotation/mirror and four block model variants are registered for all current
stations. Wrong orientation is refused at new-installation admission, never silently repaired
or adopted. Inventory-provider recognition remains based on the registered type:
rotating an already installed machine does not erase its container identity or
freeze access to its stock. The final follow-up separates those two predicates.
Older schemas/markup are deliberately unsupported; no guessed direction.

The existing native authoring compiler now inserts actual registered equipment
geometry before checking shared-navigation entrance-to-input/operator paths.
Reserved template equipment/maintenance volumes remain checked against fabric;
exact stations need actual centred clearance. Native collision is the authority,
not a marker or a fake full-cube exception. Recipes, stock, fuel and work remain
with their existing owners. Current machines use one physical container with
existing logical input/output/fuel ports, not a new Create production simulation.

## Developer use

Operator level4, active v3 world, matching client6:

```text
/pale_mirror v3 building export structure:1-workshop bakery_layout
/pale_mirror v3 building equipment bakery_layout oven pale_mirror:production_bench WEST
/pale_mirror v3 building validate bakery_layout
/pale_mirror v3 building preview bakery_layout
```

The direction supplied to `equipment` is a world compass direction. The tool
converts it into the draft's local frame and moves the adjacent operator post
to the declared front. Existing `point` edits socket/input/worker supports and
volume corners; `set` edits local properties and category policies. Input and
maintenance space stay explicit; validate rejects incomplete edits.

Preview shows real registered equipment models over the existing building with
its annotation overlay. It does not place blocks, spawn entities, load chunks
or alter collision/inventories. One preview replaces the previous one, at most
32models within128blocks,600client turns, bound to the receiving ClientLevel.
An invalid draft is not shown. The author promotes reviewed files through the
normal source/build/catalogue flow; preview cannot activate or repair a world.
See source `tools/buildings/README.md`.

## Verification

- Full guardrails/check/build/package integration:2334Frontier and920NeoForge
  tests passed. Native full443 passed; dedicated native authoring2 passed.
  The latter checks real registry/collision/template and the exact native
  oriented station states, wrong-face rejection and rotation across facilities.
- Pure catalogue regression covers all4direction transforms and rejects absent
  or backwards fronts before native placement. Existing resource ownership,
  lifecycle, wire and replacement tests retain their semantics with explicit
  direction. No obsolete fixture failures were hidden.
- A small client follow-up binds preview expiry to the exact receiving world;
  final guardrails/compiler/command/package gate passed afterwards. Headless
  native gameplay is unchanged by that client-only lifetime check.
- Scenario tool56tests passed. Authoring-only commands are permitted as evidence
  actions; arbitrary administrative world commands remain prohibited.
- Checked-in full-pack20FPS carrier:
  `tools/frontier-v3-test-pilot/scenarios/live-building-equipment-layout.json`,
  SHA2560793190d583f1639f31d866949d469b1c9b0455585a205973365baca12ec470a.
  Actual player visit, facing=west packet observation, visible block, export,
  equipment selection, native validation, model preview and ordinary menu
  open/close completed13actions. First runf67856dd-7b1c-44ea-a9be-7160652873bb,
  receipt `build/equipment-layout-native/full-pack.json`. Three distinct PNGs
  opened: clear actual oven front, annotated model preview and working menu.
- The reviewed server draft equals packaged catalogue properties semantically;
  freshly compiled geometry bytes equal the packaged certificate exactly.
  Draft retained under `build/equipment-layout-native/`. This is the actual
  developer tool applied to the repaired oven mount.

The original reference frame under `build/frontier-v3-scenarios/` was also
opened; it had the old unrotated machine and annotation clutter. The new clear
frame shows both real furnace fronts aligned in the existing stone oven shell.
Template/NBT/fabric textures were not replaced; no private asset rights changed.

Normal all-dimensions save17:26:38 and same-world restart were followed by
a repeat of the checked-in13-action full-pack carrier: PASS,
runbd749871-e179-4ad1-bc50-50d8be93074c, receipt
`build/equipment-layout-native/after-restart-full-pack.json`. The task's prior
draft was retained under `before-restart-draft/` before ordinary re-export;
no gameplay data was edited. Actual west-facing block and ordinary menu passed
again; the clear after-restart PNG was opened and matches the repaired front.
Both task clients and private displays exited; task operator was removed.
Final read8171 green, required/scene/inventory conflicts0, incidentIndex0,
preparingReplicaScopes0; progress NOT_ASSESSED.

A final source audit separated placement orientation from inventory-provider
recognition. The original shared provider predicate also gated inspection;
including direction there could misclassify the same block entity after a
rotation. New-installation admission keeps exact pose checking, while existing
container inspection retains its registered-type identity. The native test
proves wrong-pose rejection AND surviving inventory recognition after rotation.
Focused provider/native/package checks and final delivery identity below.
 A TCP
reset-by-peer log17:23:09 belongs to the task runner terminating its completed
client, not a server crash; the player disconnected and the server stayed active.
No whole-life M3, combat, economy balance, performance or Create compatibility
acceptance is inferred from this layout check.

## Deployment

Clean detached sourceca5b2a1a13a2a7048bc20119ab875f2d03de7562; JAR SHA512:
`7c10ad2f5e4fcd374dc5d84f3d785da9a4b27c249c9759d7969dc3e7578372faa1fedb3ce76345ef4bef905edd41ffefd8894a1c68aeb6d5e79bd8cc657b7110`.
Visuals unchanged SHA512:
`2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.
PACK preflight/index/publish/pinnedinstall/deploymentverifyPASS. Fresh world
`equipment-layout-schema338-seed139-20261105`, seed139/r31. Previous337 world
saved all dimensions17:21:27 and retained. Current Java22 service
`far-frontier-v3-live.service`; initial wrapper647474/start1793881324. Normal
338 save17:26:38, same-world restart wrapper669745/start1793881675. Exact final
health/cleanup below. This release requires the client updater for model/block
states and protocol6. No root pack tracked edits, public push or redistribution.

## Final delivery closure

Follow-upb5cc158462a91951f406a48fbff03b7be29fdb9c separates exact mount admission
from existing inventory identity. Final affected unit2/native authoring2,
guardrails and package verification passed; all earlier2334/920/443 coverage
remains dependency-valid for the unchanged model/layout, codecs and simulation.
Final detached package and fresh deploy verifier passed. JAR SHA512:
`367f05575d870b3a868a935a9bbcc78c79e50e9b5cbf03d01c87d8fb5cfbc9e4d0dc68bf61dadf8d92003e49aa7d5ce33b355d3adae03a6fcd67ea7226655389`.

Normal same338-world save17:36:12, final service wrapper706280, restart1793882211.
Exact final source/binary full-pack20FPS run
3bdeaaf6-21d1-4949-9216-2b644ae82ee1 passed13actions and its terminal summary.
Receipt `build/equipment-layout-native/final-delivery-full-pack.json`; clear
front and annotated-preview frames opened/reviewed again. Menu opened/closed
through ordinary player packets. Task draft from the prior run was retained
under `after-restart-draft/` before ordinary re-export; gameplay untouched.
Final health is in `delivery-summary.txt`; required/inventory/scene conflicts0,
no new quarantine. Task client/private display exited, PMBakeryAudit deopped,
0players; human rd and unrelated operators preserved. No monitoring left.

IMPL and clean detached RELEASE atb5cc15846. PACK tracked clean888ed90 with
only its pre-existing untracked `.f0v-baseline/`; ignored hosted artifacts were
published through PACK scripts. GOV task ledger/report and one precise
architecture addition are scoped separately; unrelated WIP remains. No push.
