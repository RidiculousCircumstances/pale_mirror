#!/usr/bin/env python3
"""Inspect two bounded kitchen drafts, then observe the existing pilot's real chair clicks."""
import asyncio
import base64
import json
import os
from pathlib import Path
import runpy
import sys
import time

from mcp import ClientSession, StdioServerParameters
from mcp.client.stdio import stdio_client
from vibecraft.client_bridge import ClientBridge
from vibecraft.config import VibeCraftConfig

ROOT = Path(__file__).resolve().parents[3]
LAYOUT = json.loads(Path(__file__).with_name('layouts.json').read_text())
OUT = Path(sys.argv[1]).resolve()
BASE = LAYOUT['base']


def xyz(local):
    return ' '.join(str(local[i] + BASE[i]) for i in range(3))


async def main():
    OUT.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, VIBECRAFT_CLIENT_PORT='18766',
               VIBECRAFT_CLIENT_TOKEN='vibecraft-disposable-smoke',
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

            async def command(*commands):
                result = await call('build', {'commands': list(commands)})
                text = '\n'.join(p.text for p in result.content if p.type == 'text')
                if '❌' in text:
                    raise RuntimeError(text)
                return text

            async def shot(name):
                result = await call('capture_screenshot', {'max_width': 1280, 'max_height': 800})
                part = next(p for p in result.content if p.type == 'image')
                (OUT / (name + '.png')).write_bytes(base64.b64decode(part.data))

            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                try:
                    context = json.loads((await call('get_player_context', {})).content[0].text)
                    if context.get('dimension') == 'minecraft:overworld' and abs(context['position']['x'] - 15) < 2:
                        break
                except Exception:
                    pass
                await asyncio.sleep(1)
            else:
                raise RuntimeError('Pilot did not reach its declared kitchen preparation barrier')
            for block in ['handcrafted:spruce_chair', 'handcrafted:spruce_table']:
                search = await call('search_minecraft_item', {'query': block})
                (OUT / (block.split(':')[1] + '-registry.json')).write_text(search.content[0].text)
                assert block in search.content[0].text
            await command('/place template pm_trial:kitchen 0 100 0', '/time set day', '/weather clear')
            await asyncio.sleep(2)
            receipts = []
            for variant in [None] + LAYOUT['variants']:
                name = 'baseline' if variant is None else variant['id']
                if variant:
                    commands = ['/setblock ' + xyz(p) + ' minecraft:air' for p in variant['remove']]
                    commands += ['/setblock ' + xyz(t['position']) + ' handcrafted:spruce_table[shape=' + t['shape'] + ']'
                                 for t in variant['tables']]
                    commands += ['/setblock ' + xyz(c['position']) + ' handcrafted:spruce_chair[facing=' + c['facing'] + ']'
                                 for c in variant['chairs']]
                    await command(*commands)
                await asyncio.sleep(.5)
                scan = await call('scan_region', {'x1': 0, 'y1': 100, 'z1': 0,
                                                  'x2': 19, 'y2': 112, 'z2': 24, 'include_states': True})
                (OUT / (name + '-scan.json')).write_text(scan.content[0].text)
                payload = json.loads(scan.content[0].text)
                blocks = []
                for run in payload['blocks']:
                    blocks.extend([run[0]] * run[1] if isinstance(run, list) else [run])
                assert len(blocks) == 20 * 13 * 25
                if variant:
                    for kind in ['tables', 'chairs']:
                        for item in variant[kind]:
                            x, y, z = item['position']
                            actual = payload['palette'][blocks[(y * 25 + z) * 20 + x]]
                            block = 'handcrafted:spruce_' + ('table' if kind == 'tables' else 'chair')
                            prop = ('shape=' + item['shape']) if kind == 'tables' else ('facing=' + item['facing'])
                            assert actual.startswith(block) and prop in actual, (item, actual)
                for camera in LAYOUT['cameras']:
                    await command('/tp @s ' + xyz(camera['feet']) + ' facing ' + xyz(camera['target']))
                    await asyncio.sleep(.8)
                    await shot(name + '-' + camera['id'])
                receipts.append({'variant': name, 'scan': name + '-scan.json'})
            await command('/clone 0 100 0 19 112 24 40 100 0 replace force',
                          '/fill 40 104 0 59 112 24 minecraft:air',
                          '/tp @s 50 119 12 facing 50 101 12')
            await asyncio.sleep(1)
            await shot('window-six-roof-cut-copy')
            # All interaction remains ordinary input in the existing pilot.
            await command('/gamemode creative', '/setblock 30 101 0 minecraft:gold_block')
            x11 = runpy.run_path(str(ROOT / 'scripts/visual-audit-x11.py'), run_name='pm_trial_x11')
            display = x11['open_display']()
            window = x11['minecraft_window']()
            feedback = ClientBridge(VibeCraftConfig(client_port=18766, client_token='vibecraft-disposable-smoke',
                                                   worldedit_mode='off', enable_version_detection=False))
            async def seat_query(mounted=True):
                query = ('/execute as PMTestPilot on vehicle if entity @s[type=handcrafted:seat]' if mounted else
                         '/execute unless entity @e[type=handcrafted:seat]')
                return await asyncio.to_thread(feedback.execute_command, query)
            for index, chair in enumerate(LAYOUT['variants'][-1]['chairs'], 1):
                deadline = time.monotonic() + 25
                while time.monotonic() < deadline:
                    answer = await seat_query()
                    if 'Test passed' in answer:
                        break
                    await asyncio.sleep(.2)
                else:
                    raise AssertionError('No actual seat vehicle for chair ' + str(index))
                (OUT / ('chair-' + str(index) + '-mount-feedback.txt')).write_text(answer)
                await shot('chair-' + str(index) + '-mounted')
                x11['key_event'](display, window, 'Shift_L', True)
                x11['X11'].XFlush(display)
                await asyncio.sleep(.4)
                x11['key_event'](display, window, 'Shift_L', False)
                x11['X11'].XFlush(display)
                await asyncio.sleep(.4)
                answer = await seat_query(False)
                assert 'Test passed' in answer, 'No confirmed empty-seat removal after ordinary Shift input: ' + answer
                (OUT / ('chair-' + str(index) + '-release-feedback.txt')).write_text(answer)
                context = await call('get_player_context', {})
                (OUT / ('chair-' + str(index) + '-released.json')).write_text(context.content[0].text)
                released = json.loads(context.content[0].text)['position']
                assert abs(released['y'] - (BASE[1] + 2)) < .05, ('Dismount must reach the floor', index, released)
                await command('/setblock 30 101 ' + str(index) + ' minecraft:diamond_block')
                receipts.append({'chair': index, 'layout': chair, 'mounted': True, 'ordinaryShiftRelease': True})
            feedback.close()
            (OUT / 'trial-receipt.json').write_text(json.dumps({'status': 'passed', 'checks': receipts,
                'scope': 'Isolated building copy and real player seating; NPC dining not implemented'}, indent=2) + '\n')


if __name__ == '__main__':
    asyncio.run(main())
