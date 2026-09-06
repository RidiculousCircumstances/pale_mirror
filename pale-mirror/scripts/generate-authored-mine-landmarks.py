#!/usr/bin/env python3
"""Generate the remaining PM-owned mine context foundation.

Active above-ground buildings are curated complete NBT modules imported by
``import-curated-frontier-assets.sh``.  Keeping this script from writing those
paths prevents a historical procedural kit from accidentally replacing the
curated library.  The old builders remain below as design history, but only the
dormant Red Valley foundation is emitted.
"""

from __future__ import annotations

import gzip
import hashlib
import pathlib
import struct


ROOT = pathlib.Path(__file__).resolve().parents[1]
STRUCTURES = ROOT / "pale-mirror-visuals/src/main/resources/data/pale_mirror_visuals/structure"


def utf(value: str) -> bytes:
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(tag: int, name: str, payload: bytes) -> bytes:
    return bytes((tag,)) + utf(name) + payload


def integer(value: int) -> bytes:
    return struct.pack(">i", value)


def string(value: str) -> bytes:
    return utf(value)


def list_tag(kind: int, values: list[bytes]) -> bytes:
    return bytes((kind,)) + integer(len(values)) + b"".join(values)


def compound(values: list[bytes]) -> bytes:
    return b"".join(values) + b"\x00"


class Structure:
    def __init__(self, size: tuple[int, int, int]) -> None:
        self.size = size
        self.palette: list[tuple[str, tuple[tuple[str, str], ...]]] = []
        self.palette_index: dict[tuple[str, tuple[tuple[str, str], ...]], int] = {}
        self.blocks: dict[tuple[int, int, int], int] = {}

    def state(self, name: str, **properties: str) -> int:
        key = (name, tuple(sorted(properties.items())))
        if key not in self.palette_index:
            self.palette_index[key] = len(self.palette)
            self.palette.append(key)
        return self.palette_index[key]

    def put(self, x: int, y: int, z: int, name: str, **properties: str) -> None:
        sx, sy, sz = self.size
        if not (0 <= x < sx and 0 <= y < sy and 0 <= z < sz):
            raise ValueError(f"block outside structure: {(x, y, z)} not in {self.size}")
        self.blocks[(x, y, z)] = self.state(name, **properties)

    def cuboid(self, first: tuple[int, int, int], last: tuple[int, int, int],
               name: str, **properties: str) -> None:
        for x in range(first[0], last[0] + 1):
            for y in range(first[1], last[1] + 1):
                for z in range(first[2], last[2] + 1):
                    self.put(x, y, z, name, **properties)

    def shell(self, first: tuple[int, int, int], last: tuple[int, int, int],
              name: str, **properties: str) -> None:
        for x in range(first[0], last[0] + 1):
            for y in range(first[1], last[1] + 1):
                for z in range(first[2], last[2] + 1):
                    if x in (first[0], last[0]) or y in (first[1], last[1]) or z in (first[2], last[2]):
                        self.put(x, y, z, name, **properties)

    def encode(self) -> bytes:
        palette = []
        for name, properties in self.palette:
            values = [named(8, "Name", string(name))]
            if properties:
                values.append(named(10, "Properties", compound([
                    named(8, key, string(value)) for key, value in properties
                ])))
            palette.append(compound(values))
        blocks = []
        for position, state in sorted(self.blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
            blocks.append(compound([
                named(9, "pos", list_tag(3, [integer(value) for value in position])),
                named(3, "state", integer(state)),
            ]))
        root = compound([
            named(9, "size", list_tag(3, [integer(value) for value in self.size])),
            named(9, "entities", list_tag(10, [])),
            named(9, "blocks", list_tag(10, blocks)),
            named(9, "palette", list_tag(10, palette)),
            named(3, "DataVersion", integer(3955)),
        ])
        return named(10, "", root)


def stepped_roof(out: Structure,
                 courses: list[tuple[tuple[int, int, int], tuple[int, int, int], str, str]]) -> None:
    """Build supported slab eaves with solid overlap beneath every raised course.

    A top slab one block above another top slab leaves a visible half-block air
    seam. Each course therefore uses bottom slabs only on its exposed edge and
    a full matching block wherever the next course bears on it.
    """
    for index, (first, last, slab, solid) in enumerate(courses):
        next_bounds = courses[index + 1][:2] if index + 1 < len(courses) else None
        for x in range(first[0], last[0] + 1):
            for z in range(first[2], last[2] + 1):
                supports_next = next_bounds is not None \
                    and next_bounds[0][0] <= x <= next_bounds[1][0] \
                    and next_bounds[0][2] <= z <= next_bounds[1][2]
                out.put(x, first[1], z, solid if supports_next else slab,
                        **({} if supports_next else {"type": "bottom"}))


def portal_hoist() -> Structure:
    # Local X is the broad facade; local Z runs from the freight yard (0)
    # toward the mountain/adit (18).  The building combines a winding house,
    # a masonry portal and an open timber headframe without filling the hill
    # with an artificial cobblestone mound.
    out = Structure((23, 14, 19))
    stone = "minecraft:stone_bricks"
    cracked = "minecraft:cracked_stone_bricks"
    brick = "minecraft:bricks"
    log = "minecraft:stripped_spruce_log"
    plank = "minecraft:spruce_planks"
    roof = "minecraft:deepslate_tile_slab"

    out.cuboid((1, 0, 2), (21, 0, 17), "minecraft:cobblestone")
    # Winding/engine house: an asymmetric brick volume gives the yard a
    # readable industrial silhouette instead of another rectangular bunker.
    for x in range(2, 10):
        for z in range(3, 12):
            out.put(x, 1, z, plank)
            for y in range(2, 7):
                if x in (2, 9) or z in (3, 11):
                    out.put(x, y, z, brick if (x + z + y) % 7 else cracked)
    for x in (2, 9):
        for z in (3, 11):
            out.cuboid((x, 1, z), (x, 8, z), log, axis="y")
    # Tall windows and a yard-side personnel door.
    for z in (5, 8):
        out.cuboid((2, 3, z), (2, 5, z), "minecraft:iron_bars")
    out.cuboid((5, 2, 3), (6, 4, 3), "minecraft:air")
    stepped_roof(out, [
        ((1, 7, 2), (10, 7, 12), roof, "minecraft:deepslate_tiles"),
        ((2, 8, 2), (9, 8, 12), roof, "minecraft:deepslate_tiles"),
        ((3, 9, 2), (8, 9, 12), roof, "minecraft:deepslate_tiles"),
        ((4, 10, 2), (7, 10, 12), roof, "minecraft:deepslate_tiles"),
    ])

    # Masonry adit facade against the mountain side.
    out.cuboid((10, 1, 13), (18, 1, 18), stone)
    for x in range(9, 20):
        out.cuboid((x, 1, 16), (x, 2 + abs(x - 14) // 3, 18), stone)
    for x in (9, 10, 18, 19):
        out.cuboid((x, 1, 14), (x, 7, 18), stone)
    out.cuboid((11, 1, 16), (17, 5, 18), "minecraft:air")
    out.cuboid((12, 6, 17), (16, 6, 18), stone)
    out.cuboid((13, 7, 17), (15, 7, 18), stone)
    out.put(14, 8, 18, stone)

    # Open headframe and sheave support.  Chains terminate over the adit and
    # visually connect the winding house to the underground operation.
    for x in (11, 17):
        for z in (10, 15):
            out.cuboid((x, 1, z), (x, 10, z), log, axis="y")
    out.cuboid((10, 10, 9), (18, 10, 16), log, axis="x")
    out.cuboid((12, 12, 11), (16, 12, 15), log, axis="x")
    for x in (12, 16):
        for y in range(10, 13):
            out.put(x, y, 13, log, axis="y")
    out.cuboid((14, 6, 13), (14, 11, 13), "minecraft:chain", axis="y")
    out.put(14, 5, 13, "minecraft:grindstone", face="floor", facing="north")

    # Ore-transfer deck, safety rail and restrained work lighting.
    out.cuboid((10, 1, 3), (20, 1, 8), plank)
    for x in range(10, 21):
        if x not in (14, 15):
            out.put(x, 2, 3, "minecraft:spruce_fence")
    for x in (10, 20):
        out.cuboid((x, 2, 3), (x, 5, 3), log, axis="y")
        out.put(x, 6, 3, "minecraft:lantern")
    out.cuboid((12, 2, 6), (13, 4, 8), "minecraft:iron_bars")
    out.cuboid((17, 2, 6), (18, 4, 8), "minecraft:iron_bars")
    return out


def crew_outpost() -> Structure:
    """Timber shift office with a porch, glazing and a compact gabled roof."""
    out = Structure((12, 6, 9))
    stone = "minecraft:cobblestone"
    timber = "minecraft:stripped_spruce_log"
    wall = "minecraft:spruce_planks"
    roof = "minecraft:deepslate_tile_slab"
    out.cuboid((1, 0, 1), (10, 0, 7), stone)
    out.cuboid((2, 1, 2), (9, 1, 7), wall)
    for x in range(2, 10):
        for y in range(2, 4):
            for z in (2, 7):
                out.put(x, y, z, wall)
    for z in range(2, 8):
        for y in range(2, 4):
            for x in (2, 9):
                out.put(x, y, z, wall)
    for x in (2, 9):
        for z in (2, 7):
            out.cuboid((x, 1, z), (x, 4, z), timber, axis="y")
    # Door and windows keep the tiny building readable from every approach.
    out.cuboid((5, 2, 2), (6, 3, 2), "minecraft:air")
    for x in (3, 8):
        out.put(x, 2, 2, "minecraft:glass_pane")
        out.put(x, 2, 7, "minecraft:glass_pane")
    out.cuboid((4, 0, 0), (7, 0, 1), "minecraft:stone_bricks")
    out.cuboid((4, 1, 1), (7, 1, 1), "minecraft:spruce_slab", type="bottom")
    stepped_roof(out, [
        ((1, 4, 1), (10, 4, 8), roof, "minecraft:deepslate_tiles"),
        ((2, 5, 2), (9, 5, 7), roof, "minecraft:deepslate_tiles"),
    ])
    for x in (1, 10):
        out.put(x, 2, 1, timber, axis="y")
        out.put(x, 3, 1, "minecraft:lantern")
    return out


def processing_hall() -> Structure:
    """Long ore-dressing hall with masonry bays and a saw-tooth roofline."""
    out = Structure((31, 10, 17))
    base = "minecraft:stone_bricks"
    brick = "minecraft:bricks"
    timber = "minecraft:stripped_spruce_log"
    roof = "minecraft:deepslate_tile_slab"
    copper = "minecraft:waxed_cut_copper_slab"
    out.cuboid((1, 0, 1), (29, 0, 15), base)
    # Four articulated production bays; broad openings and iron glazing avoid
    # the bunker/cube silhouette of the old imported hall.
    for x in range(2, 29):
        for y in range(1, 6):
            for z in (2, 14):
                out.put(x, y, z, brick)
    for z in range(2, 15):
        for y in range(1, 6):
            for x in (2, 28):
                out.put(x, y, z, brick)
    for x in (2, 8, 15, 22, 28):
        for z in (2, 14):
            out.cuboid((x, 1, z), (x, 7, z), timber, axis="y")
    # Loading portals in both long facades and repeated high windows.
    for x in (5, 18, 25):
        out.cuboid((x, 1, 2), (x + 2, 4, 2), "minecraft:air")
    for x in (4, 11, 18, 25):
        out.cuboid((x, 3, 14), (x + 2, 5, 14), "minecraft:iron_bars")
    out.cuboid((2, 1, 7), (2, 4, 9), "minecraft:air")
    out.cuboid((28, 2, 7), (28, 4, 9), "minecraft:iron_bars")
    # Alternating raised roof bays make the industrial function legible from
    # the air while retaining a restrained frontier palette.
    for bay, first in enumerate((1, 8, 15, 22)):
        last = min(29, first + 7)
        raised_slab = copper if bay % 2 else roof
        raised_solid = "minecraft:waxed_cut_copper" if bay % 2 else "minecraft:deepslate_tiles"
        stepped_roof(out, [
            ((first, 6, 1), (last, 6, 15), roof, "minecraft:deepslate_tiles"),
            ((first + 1, 7, 3), (last - 1, 7, 13), raised_slab, raised_solid),
            ((first + 2, 8, 5), (last - 2, 8, 11), roof, "minecraft:deepslate_tiles"),
        ])
    # Exterior ore bins are open and visibly connected to the yard.
    for first in (4, 12, 20):
        out.cuboid((first, 1, 0), (first + 4, 1, 0), "minecraft:cobblestone_wall")
        out.put(first, 2, 0, "minecraft:lantern")
        out.put(first + 4, 2, 0, "minecraft:lantern")
    return out


def power_house() -> Structure:
    """Brick winding/power house with clerestory and integrated smokestack."""
    out = Structure((13, 15, 15))
    base = "minecraft:polished_andesite"
    brick = "minecraft:bricks"
    dark = "minecraft:deepslate_bricks"
    timber = "minecraft:stripped_spruce_log"
    out.cuboid((1, 0, 1), (11, 0, 13), base)
    for x in range(2, 11):
        for y in range(1, 7):
            for z in (2, 12):
                out.put(x, y, z, brick)
    for z in range(2, 13):
        for y in range(1, 7):
            for x in (2, 10):
                out.put(x, y, z, brick)
    for x in (2, 10):
        for z in (2, 12):
            out.cuboid((x, 1, z), (x, 8, z), timber, axis="y")
    out.cuboid((5, 1, 2), (7, 4, 2), "minecraft:air")
    for z in (5, 9):
        out.cuboid((2, 3, z), (2, 5, z + 1), "minecraft:iron_bars")
        out.cuboid((10, 3, z), (10, 5, z + 1), "minecraft:iron_bars")
    stepped_roof(out, [
        ((1, 7, 1), (11, 7, 13), "minecraft:deepslate_tile_slab", "minecraft:deepslate_tiles"),
        ((3, 8, 3), (9, 8, 11), "minecraft:waxed_cut_copper_slab", "minecraft:waxed_cut_copper"),
    ])
    # One narrow stack, visually tied into the boiler room rather than a
    # freestanding solid tower.
    for y in range(5, 15):
        for x in (8, 9):
            for z in (10, 11):
                out.put(x, y, z, dark if y < 12 else "minecraft:deepslate_tiles")
    out.put(8, 14, 10, "minecraft:iron_bars")
    out.put(9, 14, 11, "minecraft:iron_bars")
    return out


def loading_yard() -> Structure:
    """Open freight canopy whose track-side transfer function stays visible."""
    out = Structure((14, 10, 9))
    stone = "minecraft:stone_bricks"
    timber = "minecraft:stripped_spruce_log"
    roof = "minecraft:deepslate_tile_slab"
    out.cuboid((0, 0, 1), (13, 0, 7), stone)
    # Leave a full-height central lane for minecarts and material handling.
    for x in (1, 5, 8, 12):
        for z in (1, 7):
            out.cuboid((x, 1, z), (x, 6, z), timber, axis="y")
    stepped_roof(out, [
        ((0, 6, 0), (13, 6, 8), roof, "minecraft:deepslate_tiles"),
        ((2, 7, 1), (11, 7, 7), "minecraft:waxed_cut_copper_slab", "minecraft:waxed_cut_copper"),
    ])
    for x in (1, 12):
        out.put(x, 5, 1, "minecraft:lantern")
        out.put(x, 5, 7, "minecraft:lantern")
    for x in (3, 10):
        out.cuboid((x, 1, 6), (x, 5, 6), "minecraft:chain", axis="y")
        out.put(x, 1, 5, "minecraft:iron_trapdoor", facing="north", half="bottom", open="false", powered="false", waterlogged="false")
    out.cuboid((0, 1, 0), (3, 1, 0), "minecraft:cobblestone_wall")
    out.cuboid((10, 1, 0), (13, 1, 0), "minecraft:cobblestone_wall")
    return out


def dispatch_foundation() -> Structure:
    out = Structure((13, 7, 13))
    out.cuboid((0, 0, 0), (12, 0, 12), "minecraft:stone_bricks")
    for x in (1, 6, 11):
        for z in (1, 11):
            out.cuboid((x, 1, z), (x, 3, z), "minecraft:stripped_spruce_log", axis="y")
            out.put(x, 4, z, "minecraft:lantern")
    return out


def dispatch_machinery() -> Structure:
    """Open machinery shed used by the staged alternative-source project."""
    out = Structure((19, 9, 22))
    out.cuboid((1, 0, 1), (17, 0, 20), "minecraft:polished_andesite")
    for x in (2, 9, 16):
        for z in (2, 19):
            out.cuboid((x, 1, z), (x, 6, z), "minecraft:stripped_spruce_log", axis="y")
    stepped_roof(out, [
        ((1, 6, 1), (17, 6, 20), "minecraft:deepslate_tile_slab", "minecraft:deepslate_tiles"),
        ((4, 7, 4), (14, 7, 17), "minecraft:waxed_cut_copper_slab", "minecraft:waxed_cut_copper"),
    ])
    for z in (5, 11, 17):
        out.cuboid((5, 1, z), (13, 1, z), "minecraft:iron_bars")
    return out


def main() -> None:
    assets = {"dispatch_foundation": dispatch_foundation()}
    for family in ("temperate", "cold_taiga", "dry_arid"):
        for name, asset in assets.items():
            destination = STRUCTURES / family / f"mine/{name}.nbt"
            destination.parent.mkdir(parents=True, exist_ok=True)
            with destination.open("wb") as output:
                with gzip.GzipFile(filename="", mode="wb", fileobj=output, mtime=0) as compressed:
                    compressed.write(asset.encode())
            digest = hashlib.sha256(destination.read_bytes()).hexdigest()
            print(f"{destination.relative_to(ROOT)} {digest}")


if __name__ == "__main__":
    main()
