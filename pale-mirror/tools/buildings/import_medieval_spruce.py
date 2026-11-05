#!/usr/bin/env python3
"""Pinned private asset adaptation. Requires nbtlib==2.0.4 in a development venv.

Source geometry is MineColonies Medieval Spruce baker2, ARR. No public redistribution
permission is established. This script does not fetch, run or embed MineColonies code.
"""
from pathlib import Path
import argparse, base64, gzip, hashlib, io, json
from import_settlement_set import visual_data
import nbtlib
from nbtlib import Compound, List, Int, String

SOURCE_SHA = '82592a9947f2c4c25731ebfaa702415e56852ac9de224dfeb2ae38e6c0349c21'
SOURCE_REF = 'a9b03edfbbd5359b148c85284ea038066d689866'

def point(x, y, z):
    return x + 3, y - 4, z

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--source', type=Path, required=True)
    ap.add_argument('--output', type=Path, required=True)
    args = ap.parse_args()
    data = args.source.read_bytes()
    if hashlib.sha256(data).hexdigest() != SOURCE_SHA:
        raise ValueError('refusing an unpinned source blueprint')
    src = nbtlib.load(args.source)
    sx, sy, sz = (int(src[k]) for k in ('size_x', 'size_y', 'size_z'))
    if (sx, sy, sz) != (13, 22, 17):
        raise ValueError('source dimension mismatch')
    palette = [p.unpack() for p in src['palette']]
    packed = []
    for word in src['blocks']:
        word = int(word) & 0xffffffff
        packed.extend((word >> 16, word & 0xffff))
    if len(packed) != sx * sy * sz or max(packed) >= len(palette):
        raise ValueError('invalid source palette encoding')
    # The pinned Structurize format is y/z/x, high short followed by low short.
    for tile in src['tile_entities']:
        x, y, z = (int(tile[k]) for k in 'xyz')
        block = palette[packed[(y * sz + z) * sx + x]]['Name']
        tile_id = str(tile['id'])
        if not (tile_id == block or tile_id == 'domum_ornamentum:materially_retexturable' and block.startswith('domum_ornamentum:')
                or tile_id == 'minecolonies:colonybuilding' and block == 'minecolonies:blockhutbaker'):
            raise ValueError('source tile alignment mismatch')
    cells = {}
    for y in range(4, sy):
        for z in range(sz):
            for x in range(sx):
                p = palette[packed[(y * sz + z) * sx + x]]
                name, properties = p['Name'], dict(p.get('Properties', {}))
                if name in {'structurize:blocksubstitution', 'structurize:blocksolidsubstitution', 'minecolonies:blockhutbaker'} or name=='minecraft:air' and y>6:
                    continue
                if not name.startswith(('minecraft:', 'domum_ornamentum:')):
                    raise ValueError('undeclared source dependency: ' + name)
                cells[point(x, y, z)] = (name, properties)
    # A common station owns its machine cell, never the architectural template.
    cells.pop(point(5, 8, 2))
    # Flat operator support alongside the existing oven platform; the remaining stairs are real.
    cells[point(5, 7, 3)] = ('minecraft:cobblestone', {})
    # Authored secondary staff entrance, with a supported three-block external apron.
    for x in (0, 1):
        for y in (7, 8):
            cells.pop(point(x, y, 6), None)
    for x in (-3, -2, -1):
        cells[point(x, 6, 6)] = ('minecraft:cobblestone', {})
    # A level public porch makes its named entrance supports physically explicit.
    for x in range(8, 12):
        for z in range(11, 19):
            cells[point(x, 6, z)] = ('minecraft:spruce_planks', {})
    args.output.mkdir(parents=True, exist_ok=True)
    states = sorted(set((name, tuple(sorted(props.items()))) for name, props in cells.values()))
    palette_out = [Compound({'Name': String(name), **({'Properties': Compound({k: String(v) for k, v in props})} if props else {})}) for name, props in states]
    state_ids = {s: i for i, s in enumerate(states)}
    block_tags = []
    lines = ['# x y z | explicit structural material declaration | semantic part']
    counts = {}
    data = {}
    for tile in src['tile_entities']:
        pos = point(*(int(tile[k]) for k in 'xyz')); value = visual_data(tile)
        if pos in cells and value is not None: data[pos] = (value[0], int(src['mcversion']), value[1])
    for pos, (name, props) in sorted(cells.items()):
        part = 'FOUNDATION' if pos[1] <= 2 and name!='minecraft:air' else 'ROOF' if pos[1] > 6 else 'WALL'
        if pos == point(5, 7, 3):
            part = 'WORKSHOP_PROCESS_STATION'
        if pos == point(4, 6, 3):
            part = 'WORKSHOP_INPUT_STATION'
        if pos[0] < 3 or (pos[2] >= 11 and 11 <= pos[0] <= 14 and pos[1] == 2):
            part = 'PUBLIC_ACCESS_SURFACE'
        repair = {'minecraft:dirt_path': 'minecraft:dirt', 'minecraft:coarse_dirt': 'minecraft:dirt', 'minecraft:wall_torch': 'minecraft:torch'}.get(name, name)
        encoded = '2|' + name + '|' + repair + '|' + ','.join(k + '=' + v for k, v in sorted(props.items()))
        if pos in data:
            kind, version, payload = data[pos]
            encoded = '3' + encoded[1:] + '|' + str(kind) + '|' + str(version) + '|' + base64.urlsafe_b64encode(payload.snbt().encode()).decode().rstrip('=')
        lines.append(','.join(map(str, pos)) + '\t' + encoded + '\t' + part)
        counts[name] = counts.get(name, 0) + 1
        block_tags.append(Compound({'pos': List[Int](pos), 'state': Int(state_ids[(name, tuple(sorted(props.items())))] ), **({'nbt': data[pos][2]} if pos in data else {})}))
    # Native NBT is an authoring/interchange artifact; the neutral, checksum-pinned block
    # projection is consumed by the pure domain and checked against this exact native palette.
    nbt = nbtlib.File({'DataVersion': Int(3955), 'size': List[Int]([16, 18, 19]), 'palette': List[Compound](palette_out),
                      'blocks': List[Compound](block_tags), 'entities': List[Compound]([])})
    raw = io.BytesIO()
    nbt.write(raw)
    compressed = io.BytesIO()
    with gzip.GzipFile(fileobj=compressed, mode='wb', filename='', mtime=0) as archive:
        archive.write(raw.getvalue())
    (args.output / 'bakery.nbt').write_bytes(compressed.getvalue())
    (args.output / 'bakery.blocks').write_text('\n'.join(lines) + '\n')
    report = {'source': 'https://github.com/ldtteam/minecolonies', 'ref': SOURCE_REF,
              'sourcePath': 'src/main/resources/blueprints/minecolonies/medievalspruce/craftsmanship/luxury/baker2.blueprint',
              'sourceSha256': SOURCE_SHA, 'license': 'All Rights Reserved; private reference derivative only',
              'inputEntitiesRetained': 0, 'foreignBlockEntitiesRetained': 0, 'inventoryPayloadsRetained': 0,
              'adaptations': [
                  'Original Domum Ornamentum blocks and exact material texture data retained',
                  'Structurize terrain placeholders and MineColonies controller removed',
                  'No entities, inventories or colony controllers imported; typed visual payloads retained',
                  'One source furnace omitted: current common production equipment owns its socket',
                  'Supported operator platform, public porch and secondary staff opening authored',
                  'Full source decoration, foundation and below-floor voids retained; source door state retained',
                  'Wall torch repair input is the actual vanilla torch item'],
              'outputBlocks': len(cells), 'paletteCounts': counts,
              'nbtSha256': hashlib.sha256((args.output / 'bakery.nbt').read_bytes()).hexdigest(),
              'blocksSha256': hashlib.sha256((args.output / 'bakery.blocks').read_bytes()).hexdigest()}
    (args.output / 'bakery.provenance.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k not in ('paletteCounts',)}, indent=2))

if __name__ == '__main__':
    main()
