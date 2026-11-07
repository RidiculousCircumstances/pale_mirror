#!/usr/bin/env python3
"""Read the ordinary pilot's exported plan and inspect its actual kitchen via VibeCraft MCP.

No block/entity/inventory writes. Camera travel belongs only to the disposable spectator.
Run beside disposable-northwatch-maximum-levels.json, which owns the native lifecycle.
"""
import asyncio
import base64
import json
import os
from pathlib import Path
import sys
import time

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(sys.argv[1]).resolve()
EXPORT = ROOT / 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/debug/pale-mirror/buildings/maximum_review/settlement.properties'
ASSETS = ROOT / 'pale-mirror-frontier/src/main/resources/io/farfrontier/palemirror/frontier/v3/buildings'
START_NS = time.time_ns()


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if line and not line.startswith('#') and '=' in line)


async def main():
    OUT.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, VIBECRAFT_CLIENT_PORT='18766',
               VIBECRAFT_CLIENT_TOKEN='vibecraft-disposable-smoke',
               VIBECRAFT_WORLDEDIT_MODE='off', VIBECRAFT_ENABLE_VERSION_DETECTION='false')
    params = StdioServerParameters(command=str(ROOT / 'tools/vibecraft/run-mcp.sh'), env=env)
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            async def call(name, arguments):
                result = await session.call_tool(name, arguments)
                if result.isError:
                    raise RuntimeError(str(result.content))
                return result

            async def command(value):
                result = await call('build', {'commands': [value]})
                text = '\n'.join(p.text for p in result.content if p.type == 'text')
                if '❌' in text:
                    raise RuntimeError(text)

            deadline = time.monotonic() + 600
            while time.monotonic() < deadline:
                try:
                    if EXPORT.stat().st_mtime_ns < START_NS:
                        raise FileNotFoundError('No current-run export yet')
                    plan = properties(EXPORT)
                    assert plan['style'] == 'pale_mirror:medieval_spruce_max'
                    records = [{key.removeprefix(f'building.{i}.'): value
                                for key, value in plan.items() if key.startswith(f'building.{i}.')}
                               for i in range(int(plan['count']))]
                    kitchen = next(b for b in records if b['function'] == 'KITCHEN')
                    assert all(b['template'].endswith('_5') or b['template'].endswith('/hall') for b in records)
                    markup = properties(ASSETS / (kitchen['template'].split(':', 1)[1] + '.properties'))
                    anchor = tuple(map(int, kitchen['anchor'].split(',')))
                    origin = tuple(map(int, markup['origin'].split(',')))
                    turns = (['NORTH', 'EAST', 'SOUTH', 'WEST'].index(kitchen['facing'])
                             - ['NORTH', 'EAST', 'SOUTH', 'WEST'].index(markup['front'])) % 4

                    def world(local):
                        x, y, z = [local[i] - origin[i] for i in range(3)]
                        for _ in range(turns):
                            x, z = -z, x
                        return anchor[0] + x, anchor[1] + y, anchor[2] + z

                    context = json.loads((await call('get_player_context', {})).content[0].text)
                    target = world((11, 2, 12))
                    if context.get('dimension') == 'pale_mirror:frontier_graybox' and all(
                            abs(context['position'][axis] - target[i]) < 2 for i, axis in enumerate('xyz')):
                        break
                except (FileNotFoundError, KeyError, StopIteration, RuntimeError, ValueError):
                    pass
                await asyncio.sleep(1)
            else:
                raise RuntimeError('Pilot did not reach its declared final kitchen inspection window')

            (OUT / 'settlement.properties').write_bytes(EXPORT.read_bytes())
            for block in ('handcrafted:spruce_chair', 'handcrafted:spruce_table'):
                result = await call('search_minecraft_item', {'query': block})
                (OUT / (block.split(':')[1] + '-registry.txt')).write_text(result.content[0].text)
            lo, hi = world((0, 0, 0)), world(tuple(int(v) - 1 for v in markup['fabric.max'].split(',')))
            bounds = {f'{axis}{end}': (min(lo[i], hi[i]) if end == 1 else max(lo[i], hi[i]))
                      for i, axis in enumerate('xyz') for end in (1, 2)}
            # A dimension/restart teleport can be acknowledged before its chunk packets.
            # Observe the existing client cache; never request/load a missing chunk.
            scan_deadline = time.monotonic() + 30
            while True:
                scan = await call('scan_region', dict(bounds, include_states=True))
                raw = scan.content[0].text
                try:
                    payload = json.loads(raw)
                    break
                except json.JSONDecodeError:
                    unavailable = raw.startswith('Region scan') and 'chunk' in raw.lower()
                    if not unavailable or time.monotonic() >= scan_deadline:
                        raise RuntimeError(raw)
                    await asyncio.sleep(.25)
            (OUT / 'kitchen-scan.json').write_text(json.dumps(payload, indent=2))
            blocks = []
            for run in payload['blocks']:
                blocks.extend([run[0]] * run[1] if isinstance(run, list) else [run])
            palette = payload['palette']
            assert not any('minecraft:campfire' in palette[index] for index in blocks)
            sx, sy, sz = [bounds[a+'2'] - bounds[a+'1'] + 1 for a in 'xyz']
            assert len(blocks) == sx * sy * sz

            def actual(local):
                p = world(local)
                x, y, z = [p[i] - bounds[axis+'1'] for i, axis in enumerate('xyz')]
                return palette[blocks[(y * sz + z) * sx + x]]

            chairs = 0
            tables = 0
            for line in (ASSETS / 'medieval_spruce/kitchen_5.blocks').read_text().splitlines():
                if not line or line.startswith('#'):
                    continue
                coord, material, _ = line.split('\t')
                _, kind, _, raw_properties, *_ = material.split('|')
                if not kind.startswith('handcrafted:'):
                    continue
                p = tuple(map(int, coord.split(',')))
                observed = actual(p)
                assert observed.startswith(kind), (p, observed, kind)
                props = dict(v.split('=', 1) for v in raw_properties.split(','))
                if kind.endswith('_chair'):
                    chairs += 1
                    directions = ['north', 'east', 'south', 'west']
                    facing = directions[(directions.index(props['facing']) + turns) % 4]
                    assert 'facing='+facing in observed, (p, observed)
                else:
                    tables += 1
            assert (chairs, tables) == (6, 5)
            cameras = [
                ('maximum-kitchen-facade', (11, 5, 38), (11, 9, 11)),
                ('clear-central-floor', (12, 2, 12), (14, 2, 9)),
                ('window-table', (16, 2, 11), (19, 3, 12)),
                ('counter-seats', (18, 2, 15), (16, 3, 17)),
            ]
            for name, feet, target in cameras:
                position, look = world(feet), world(target)
                await command('/execute anchored eyes run tp @s '
                              + ' '.join(map(str, position)) + ' facing ' + ' '.join(map(str, look)))
                await asyncio.sleep(2)
                image = await call('capture_screenshot', {'max_width': 1600, 'max_height': 1000})
                part = next(p for p in image.content if p.type == 'image')
                (OUT / (name+'.png')).write_bytes(base64.b64decode(part.data))
                capture_metadata = json.loads(next(p.text for p in image.content if p.type == 'text'))
                (OUT / (name+'-camera.json')).write_text(json.dumps(
                    {'feet': position, 'target': look, 'capture': capture_metadata}))
            (OUT / 'receipt.json').write_text(json.dumps({'status': 'ok', 'style': plan['style'],
                'buildings': records, 'kitchenChairs': chairs, 'kitchenTables': tables,
                'campfires': 0, 'scanBounds': bounds}, indent=2))
            print('Actual maximum-level layout, six chairs/five tables and no campfires observed; four camera frames captured.')


asyncio.run(main())
