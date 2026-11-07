#!/usr/bin/env python3
"""Apply the reviewed local furniture draft to the authored kitchen; requires nbtlib==2.0.4."""
import gzip
import io
import json
from pathlib import Path
import hashlib
import nbtlib
from nbtlib import Compound, Int, List, String

root = Path(__file__).resolve().parents[3]
asset = root / 'pale-mirror-frontier/src/main/resources/io/farfrontier/palemirror/frontier/v3/buildings/medieval_spruce/kitchen'
layout_path = Path(__file__).with_name('layouts.json')
layout = json.loads(layout_path.read_text())['variants'][-1]
native = nbtlib.load(asset.with_suffix('.nbt'))
cells = {tuple(map(int, row['pos'])): row for row in native['blocks']}
neutral = {}
for line in asset.with_suffix('.blocks').read_text().splitlines():
    if line and not line.startswith('#'):
        coordinate, *payload = line.split('\t')
        neutral[tuple(map(int, coordinate.split(',')))] = payload
assert len(cells) == len(neutral) and not native['entities']
changes = {tuple(p): None for p in layout['remove']}
for table in layout['tables']:
    changes[tuple(table['position'])] = ('handcrafted:spruce_table', {'color': 'none', 'waterlogged': 'false', 'shape': table['shape']})
for chair in layout['chairs']:
    changes[tuple(chair['position'])] = ('handcrafted:spruce_chair', {'color': 'none', 'waterlogged': 'false', 'facing': chair['facing']})
for position, state in changes.items():
    previous = cells.get(position)
    assert previous is None or 'nbt' not in previous, ('Furniture must not discard visual payloads', position)
    if state is None:
        cells.pop(position, None)
        neutral.pop(position, None)
        continue
    name, properties = state
    palette = Compound({'Name': String(name), 'Properties': Compound({k: String(v) for k, v in properties.items()})})
    if palette not in native['palette']:
        native['palette'].append(palette)
    cells[position] = Compound({'pos': List[Int](position), 'state': Int(native['palette'].index(palette))})
    neutral[position] = ['2|' + name + '|' + name + '|' + ','.join(k+'='+v for k,v in sorted(properties.items())), 'WALL']
native['blocks'] = List[Compound]([cells[p] for p in sorted(cells)])
stream = io.BytesIO()
native.write(stream)
asset.with_suffix('.nbt').write_bytes(gzip.compress(stream.getvalue(), mtime=0))
lines = ['# Pinned Medieval Spruce private adaptation; reviewed Handcrafted window furniture.']
lines += [','.join(map(str,p))+'\t'+'\t'.join(neutral[p]) for p in sorted(neutral)]
asset.with_suffix('.blocks').write_text('\n'.join(lines)+'\n')
properties = asset.with_suffix('.properties').read_text()
asset.with_suffix('.properties').write_text(properties.replace('version=1\n', 'version=2\n'))
provenance_path = asset.with_suffix('.provenance.json')
provenance = json.loads(provenance_path.read_text())
note = 'reviewed Handcrafted 4.0.3 window-six furniture replaces vanilla mock seats/tables; walls, cooking aisle, storage and DINING semantics retained'
if note not in provenance['adaptations']:
    provenance['adaptations'].append(note)
provenance['blocks'] = len(cells)
provenance['furnitureLayoutSha256'] = hashlib.sha256(layout_path.read_bytes()).hexdigest()
provenance_path.write_text(json.dumps(provenance, indent=2)+'\n')
print('Promoted', len(changes), 'local furniture cells;', len(cells), 'native/neutral cells. Regenerate .geometry with native building validate.')
