# Authored buildings (active Frontier v3)

One content definition owns geometry and local annotations. Existing world generation,
structural custody, storage, production and navigation remain the gameplay owners.
The checked-in Medieval Spruce set is a **private test derivative**, not a grant of
redistribution rights. Exact upstream sources and declared functional adaptations are
in the provenance files, `import_medieval_spruce.py` and `import_settlement_set.py`.
Native decorative blocks require the pinned Domum Ornamentum, MineColonies rack
and support-mod dependencies. Colony controllers and their gameplay state are not imported.

The town hall selects upstream **level 5** (`townhall5`), matching the gallery tower.
Other service templates explicitly select **level 2** variants (`hospital2`,
`warehouse2`, etc.; bakery uses `baker2`). Housing
selects explicit level/bed variants. The Style Explorer gallery shows maximum-level
buildings. The current lower-level selections therefore do not match that visual
reference; equality to a level-2 source must not be used to accept the gallery's
complete towers, materials or size. Choosing a different source level is a content/layout change,
not permission to approximate its palette. Compare the same source revision,
variant and level before asserting import fidelity. Architectural visual acceptance
also needs the actual placed view: source equality alone cannot prove terrain fit.

`catalog.properties` declares the styles, every style's functional selections,
asset paths and storage-category membership. A missing selection never chooses a
fallback. All settlement functions select Medieval Spruce templates; housing has explicit one-, two-, three- and five-bed variants.
A selected structure retains style/template/content hash. Content changes require a
fresh world under the current schema policy.

Markup format4 explicitly declares `site.groundY` independently from the entry
floor and names deliberate `site.excavations`; empty source cells below outdoor
grade do not authorize excavation. A common bounded site survey/plan supplies
cut, fill, native foundation and graded access without flattening the settlement.
Unknown or unsafe terrain is not a successful fit. Import content may declare a bounded
`entranceSupports` path and its `entranceThreshold` independently of outdoor ground.
Use the actual native platform levels when the building has a raised entrance;
raising the complete outdoor datum to conceal a mismatched port buries the yard.
The importer rejects non-adjacent supports and a threshold that differs from the
declared door. It also declares each workplace's `equipmentType` and local
`facing`. The front points from the machine support towards its adjacent operator
support. Missing/backwards declarations are errors; no direction is guessed.

Each asset consists of `.nbt` (native interchange, no entities, inventories or controllers;
only explicitly typed visual block payloads),
`.blocks` (the exact neutral projection), `.properties` (rooms/zones/passages/workplaces)
and `.geometry` (Minecraft-compiled collision certificate for every rotation).
The authoring validator checks the native/neutral equality, actual registry block
states and repair items, collision clearance and paths through the shared navigator with the actual
registered equipment shapes installed. The template's reserved equipment volumes
are checked before insertion; operator/input paths are checked afterwards.
Do not hand-edit collision facts or replace stairs with guessed full cubes.

## In-game developer tool

Operator level 4, active v3 world:

```
/pale_mirror v3 building export structure:1-workshop bakery_trial
/pale_mirror v3 building overlay bakery_trial
/pale_mirror v3 building point bakery_trial zone.storage.min ~ ~ ~
/pale_mirror v3 building point bakery_trial zone.storage.max ~3 ~3 ~3
/pale_mirror v3 building set bakery_trial zone.storage.priority 75
/pale_mirror v3 building equipment bakery_trial oven pale_mirror:production_bench WEST
/pale_mirror v3 building validate bakery_trial
/pale_mirror v3 building preview bakery_trial
```

Draft files live in the server's `debug/pale-mirror/buildings/<draft>/`.
`point` converts an ordinary world block position into the retained building's local
frame, including rotation. Zone min/max are feet-space **inclusive/exclusive**;
passages and workplace anchors name **support blocks**, not feet. Use `set` for
room/zone lists, passage chains (`x,y,z;x,y,z;...`), flags and named anchors. An empty
value can be written as `""`. Files can also be edited externally and reimported by
`validate`; all entry points use the same strict bounded parser. Invalid intermediate
edits stay in the draft and never become gameplay state.

`equipment <draft> <workplace> <equipment-id> <world-front>` selects a registered
equipment adapter, converts the supplied world compass direction into the retained
local building frame, and moves the adjacent operator support to that front.
`point` still edits socket/input/support/volume coordinates; use it before/after
selection when relocating a workplace. Input and maintenance bounds remain explicit:
validate rejects an incomplete or obstructed edit rather than moving surrounding
architecture. Use `set` for full local declarations edited by scripts.

`preview` first performs the same native validation, then shows the real registered
models with their full orientation over the existing building, plus the annotation
overlay. It is a requesting-client-only render projection: no blocks, inventories,
entities or collisions change. At most32 models within128 blocks; each new preview
replaces the old one, expires after600 client ticks, and clears on world/disconnect.
Invalid drafts cannot be previewed. The current tool previews equipment in its
retained building placement, not a separate live-paste or production activation.

Overlay is an ordinary Minecraft debug marker projection, corners/labels by zone,
entrance chains and input/operator/equipment/maintenance markers. It is sent only to
the requesting player, within 128 blocks, at most 512 markers, expiring after 30 s.
It creates no world entities, block mutations or server tick job.

Validation compiles current registry shapes and refreshes the draft certificate.
It never activates a draft, pastes a building, grants passage through an obstacle,
restores broken equipment or imports inventory. Promote reviewed draft files into
the packaged catalogue and build normally. A current native observation (including
a shut door) overrides pristine building geometry. Current manual door/gate operation is owned by the shared navigator; developer
annotations do not grant that capability or open a physical door.

For the pinned asset adaptation use Python with `nbtlib==2.0.4`:

```
python tools/buildings/import_medieval_spruce.py --source baker2.blueprint --output <asset-directory>
```

Compile/check the packaged native geometry with the `building-authoring` GameTest
slice. Its authoring output lives under the run directory's `building-authoring/`;
copy the reviewed certificate into the matching asset, then verify/build again.
This asset-only check is not evidence of a baker completing a real production job.

## Settlement planning

The retained settlement plan owns its style, stable plot/building IDs, occupied
parcel envelopes, flush paving and extension fronts. Genesis selects one of three
seed-reproducible street schemes; recovery reads the stored plan. Future extension
can append plots and streets without moving existing buildings. This does not add
a construction process, home occupancy or sleep AI. Beds are exact head/foot pairs;
destruction removes physical capacity, without inventing a bed assignment.

Operator commands edit a bounded draft only:

```
/pale_mirror v3 building layout export settlement:1 village_trial
/pale_mirror v3 building layout overview village_trial
/pale_mirror v3 building layout move village_trial structure:1-housing-1 ~ ~ ~ SOUTH
/pale_mirror v3 building layout street village_trial ~ ~ ~ ~20 ~ ~
/pale_mirror v3 building layout connect village_trial
/pale_mirror v3 building layout validate village_trial
/pale_mirror v3 building layout overlay village_trial
/pale_mirror v3 building layout preview village_trial structure:1-housing-1
```

`settlement.properties` and `overview.svg` live in the same draft directory as the
building editor. Move/rotate retains the exact building and plot identities;
`connect` uses the genesis frontage compiler. Validation checks surveyed terrain,
real native collision geometry, parcel separation and connected public entrances.
The selected-building preview uses real static block models, at most4096 within128
blocks, expires after600 client ticks, and never writes the world. Unloaded chunks
remain unloaded. No block-entity renderer, Create mechanism, internal house route
or bed-access guarantee is implied by a static preview or bed count.

Import the remaining pinned set with `nbtlib==2.0.4`:

```
python tools/buildings/import_settlement_set.py --sources <facility-blueprints> \
  --housing <residence-blueprints> --output <asset-directory>
```

The importer verifies every source checksum from `settlement_sources.json` and
writes provenance for each adaptation. Native materials, shapes and typed visual
payloads are preserved; decorative blocks, roofs, free plants and water are not
replaced with vanilla substitutes. Explicit functional mounts and entrance easements
remain declared adaptations and must be included in source-difference review.
No upstream inventories, entities or colony controllers are imported.
