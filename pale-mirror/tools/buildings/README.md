# Authored buildings (active Frontier v3)

One content definition owns geometry and local annotations. Existing world generation,
structural custody, storage, production and navigation remain the gameplay owners.
The checked-in Medieval Spruce bakery is a **private test derivative**, not a grant of
redistribution rights. Exact upstream source and substitutions are in its provenance
file and `import_medieval_spruce.py`. No MineColonies runtime is required.

`catalog.properties` declares the styles, every style's functional selections,
asset paths and storage-category membership. A missing selection never chooses a
fallback. Current other functions explicitly select their retained graybox templates.
A selected structure retains style/template/content hash. Content changes require a
fresh world under the current schema policy.

Each asset consists of `.nbt` (native interchange, no entities or block NBT),
`.blocks` (the exact neutral projection), `.properties` (rooms/zones/passages/workplaces)
and `.geometry` (Minecraft-compiled collision certificate for every rotation).
The authoring validator checks the native/neutral equality, actual registry block
states and repair items, collision clearance and paths through the shared navigator.
Do not hand-edit collision facts or replace stairs with guessed full cubes.

## In-game developer tool

Operator level 4, active v3 world:

```
/pale_mirror v3 building export structure:1-workshop bakery_trial
/pale_mirror v3 building overlay bakery_trial
/pale_mirror v3 building point bakery_trial zone.storage.min ~ ~ ~
/pale_mirror v3 building point bakery_trial zone.storage.max ~3 ~3 ~3
/pale_mirror v3 building set bakery_trial zone.storage.priority 75
/pale_mirror v3 building validate bakery_trial
```

Draft files live in the server's `debug/pale-mirror/buildings/<draft>/`.
`point` converts an ordinary world block position into the retained building's local
frame, including rotation. Zone min/max are feet-space **inclusive/exclusive**;
passages and workplace anchors name **support blocks**, not feet. Use `set` for
room/zone lists, passage chains (`x,y,z;x,y,z;...`), flags and named anchors. An empty
value can be written as `""`. Files can also be edited externally and reimported by
`validate`; all entry points use the same strict bounded parser. Invalid intermediate
edits stay in the draft and never become gameplay state.

Overlay is an ordinary Minecraft debug marker projection, corners/labels by zone,
entrance chains and input/operator/equipment/maintenance markers. It is sent only to
the requesting player, within 128 blocks, at most 512 markers, expiring after 30 s.
It creates no world entities, block mutations or server tick job.

Validation compiles current registry shapes and refreshes the draft certificate.
It never activates a draft, pastes a building, grants passage through an obstacle,
restores broken equipment or imports inventory. Promote reviewed draft files into
the packaged catalogue and build normally. A current native observation (including
a shut door) overrides pristine building geometry. This first vertical starts with
open doors; no automatic door-opening gameplay was added.

For the pinned asset adaptation use Python with `nbtlib==2.0.4`:

```
python tools/buildings/import_medieval_spruce.py --source baker2.blueprint --output <asset-directory>
```

Compile/check the packaged native geometry with the `building-authoring` GameTest
slice. Its authoring output lives under the run directory's `building-authoring/`;
copy the reviewed certificate into the matching asset, then verify/build again.
This asset-only check is not evidence of a baker completing a real production job.
