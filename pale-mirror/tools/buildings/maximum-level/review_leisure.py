#!/usr/bin/env python3
"""Read-only VibeCraft region evidence; named images/behavior belong to the existing native pilot."""
import asyncio
import json
import os
from pathlib import Path
import sys
import time
from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / 'pale-mirror-frontier/src/main/resources/io/farfrontier/palemirror/frontier/v3/buildings'
EXPORT = ROOT / 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/debug/pale-mirror/buildings/leisure_review/settlement.properties'


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines()
                if line and not line.startswith('#') and '=' in line)


def placement(record, markup):
    anchor = tuple(map(int, record['anchor'].split(',')))
    origin = tuple(map(int, markup['origin'].split(',')))
    directions = ['NORTH', 'EAST', 'SOUTH', 'WEST']
    turns = (directions.index(record['facing']) - directions.index(markup['front'])) % 4

    def world(local):
        x, y, z = [local[i] - origin[i] for i in range(3)]
        for _ in range(turns):
            x, z = -z, x
        return anchor[0] + x, anchor[1] + y, anchor[2] + z
    return world, turns


async def main():
    out = Path(sys.argv[1]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    started = time.time_ns()
    env = dict(os.environ, VIBECRAFT_CLIENT_PORT='18766', VIBECRAFT_CLIENT_TOKEN='vibecraft-disposable-smoke',
               VIBECRAFT_WORLDEDIT_MODE='off', VIBECRAFT_ENABLE_VERSION_DETECTION='false')
    params = StdioServerParameters(command=str(ROOT / 'tools/vibecraft/run-mcp.sh'), env=env)
    async with stdio_client(params) as (read, write):
        async with ClientSession(read, write) as session:
            await session.initialize()

            async def call(name, args):
                result = await session.call_tool(name, args)
                if result.isError:
                    raise RuntimeError(str(result.content))
                return result

            pending = {'KITCHEN', 'HALL'}
            receipt = {}
            deadline = time.monotonic() + 420
            while pending and time.monotonic() < deadline:
                try:
                    if EXPORT.stat().st_mtime_ns < started:
                        raise FileNotFoundError('waiting for this run export')
                    plan = properties(EXPORT)
                    records = [{k.removeprefix(f'building.{i}.'): v for k, v in plan.items()
                                if k.startswith(f'building.{i}.')} for i in range(int(plan['count']))]
                    context = json.loads((await call('get_player_context', {})).content[0].text)
                    if context.get('dimension') != 'pale_mirror:frontier_graybox':
                        await asyncio.sleep(.5)
                        continue
                    for function in sorted(pending):
                        record = next(r for r in records if r['function'] == function)
                        asset = ASSETS / record['template'].split(':', 1)[1]
                        markup = properties(asset.with_suffix('.properties'))
                        world, turns = placement(record, markup)
                        maximum = tuple(int(v) - 1 for v in markup['fabric.max'].split(','))
                        lo, hi = world((0, 0, 0)), world(maximum)
                        bounds = {f'{axis}{end}': min(lo[i], hi[i]) if end == 1 else max(lo[i], hi[i])
                                  for i, axis in enumerate('xyz') for end in (1, 2)}
                        raw = (await call('scan_region', dict(bounds, include_states=True))).content[0].text
                        try:
                            payload = json.loads(raw)
                        except json.JSONDecodeError:
                            if raw.startswith('Region scan') and 'chunk' in raw.lower():
                                continue  # Existing cache only; no load request.
                            raise RuntimeError(raw)
                        (out / (function.lower() + '-scan.json')).write_text(json.dumps(payload))
                        blocks = []
                        for run in payload['blocks']:
                            blocks.extend([run[0]] * run[1] if isinstance(run, list) else [run])
                        sx, sy, sz = [bounds[a+'2'] - bounds[a+'1'] + 1 for a in 'xyz']
                        assert len(blocks) == sx * sy * sz
                        counts = {}
                        for line in asset.with_suffix('.blocks').read_text().splitlines():
                            if not line or line.startswith('#'):
                                continue
                            coord, material, _ = line.split('\t')
                            _, kind, _, raw_props, *_ = material.split('|')
                            if not kind.startswith('handcrafted:'):
                                continue
                            pos = world(tuple(map(int, coord.split(','))))
                            x, y, z = [pos[i] - bounds[a+'1'] for i, a in enumerate('xyz')]
                            actual = payload['palette'][blocks[(y * sz + z) * sx + x]]
                            assert actual.startswith(kind), (function, coord, actual, kind)
                            props = dict(v.split('=', 1) for v in raw_props.split(','))
                            if 'facing' in props:
                                facing = ['north', 'east', 'south', 'west']
                                expected = facing[(facing.index(props['facing']) + turns) % 4]
                                assert 'facing='+expected in actual, (function, coord, actual, expected)
                            counts[kind] = counts.get(kind, 0) + 1
                        assert counts.get('handcrafted:spruce_bench') == 2, counts
                        assert counts.get('handcrafted:spruce_table') == (7 if function == 'KITCHEN' else 2), counts
                        if function == 'KITCHEN':
                            assert counts.get('handcrafted:spruce_chair') == 6, counts
                            assert not any('minecraft:campfire' in payload['palette'][index] for index in blocks)
                        receipt[function] = {'bounds': bounds, 'furniture': counts, 'playerContext': context}
                        pending.remove(function)
                except (FileNotFoundError, RuntimeError, ConnectionError):
                    pass
                await asyncio.sleep(.5)
            if pending:
                raise RuntimeError('No complete loaded region evidence for ' + ','.join(sorted(pending)))
            (out / 'receipt.json').write_text(json.dumps(receipt, indent=2))
            print('Actual kitchen and well furniture independently observed through VibeCraft MCP.')


if __name__ == '__main__':
    asyncio.run(main())
