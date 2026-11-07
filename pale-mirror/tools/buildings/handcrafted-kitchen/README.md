# Handcrafted kitchen trial

A private, isolated experiment using the existing `medieval_spruce/kitchen.nbt`,
Handcrafted 4.0.3 and ResourcefulLib 3.0.12 for NeoForge 1.21.1. The two dependencies
are checksum-pinned in `dependencies.json`; `prepare.py` downloads them only into
ignored build output. No pack manifest or production building changes are made.

`layouts.json` describes replacements of the existing mock furniture beside the
windows: first two chairs, then six chairs and four table blocks. It leaves the
cooking aisle intact. Dining bounds in that file are proposals only: the existing
production DINING zone does not cover these seats.

From the Pale Mirror project directory, with the VibeCraft client JAR already built:

```sh
python3 tools/buildings/handcrafted-kitchen/prepare.py
mkdir -p build/handcrafted-kitchen/processes
FRONTIER_V3_PILOT_GRAYBOX_DATAPACK_DIRECTORY=/home/rd/proj/minecraft/datapacks/pale-mirror-graybox \
FRONTIER_V3_PILOT_PORT=25595 FRONTIER_V3_NATIVE_PROCESS_ROOT="$PWD/build/handcrafted-kitchen/processes" \
  scripts/with-private-xvfb.sh tools/buildings/handcrafted-kitchen/run-trial.sh
```

This reuses the existing disposable native pilot, one private display/client,
and its normal stop/cleanup. VibeCraft reads the real registry, places an isolated
NBT copy, scans each variant and captures client images. A roof-cut duplicate is
for presentation; the original retains its roof and walls. The checked-in scenario
clicks each chair with an empty hand. The observer checks actual `handcrafted:seat`
mounting and sends ordinary Shift input through the existing X11 helper. An
affirmative native condition confirms that the empty seat entity was removed
and the player reached the floor before admitting the next chair. These read-only
conditions use the same client bridge directly, because the MCP build summary
omits individual command replies. Receipts and images go into ignored
`build/handcrafted-kitchen/trial-*`.

This tests player seating and visual layout. It does not implement resident
seating, dining reservations, pathfinding to chairs or meal behavior. The source
NBT's provenance and redistribution constraints remain unchanged.

Verified 2026-11-08: both draft scans match every declared table shape/chair facing;
all six chairs mount the real player and release on ordinary Shift, with the
empty seat removed and every final player position on floor Y102. The rear pair
uses one window-side chair and one chair at the table end, preserving the wall
and avoiding dismount onto a neighbouring chair. The existing native pilot ends
with status `ok`, run `2d131beb-c129-4773-a8aa-adb1e48763fd`, world
`v3-disposable_handcrafted_kitchen-1f61f6cd`. Client/server/private display exited.
Evidence: `build/handcrafted-kitchen/trial-20261108-002418/` (trial receipt,
three scans, client images, six mount/release receipts and actual released poses).
This is an isolated authoring trial, not full-pack or NPC dining acceptance.
