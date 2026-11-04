#!/usr/bin/env python3
"""Read a console combat capture without contacting or mutating Minecraft."""
import argparse
import collections
import json
import math
from pathlib import Path


def summarize(path):
    actors = {}
    decisions = collections.defaultdict(collections.Counter)
    strikes = collections.defaultdict(collections.Counter)
    gestures = collections.defaultdict(collections.Counter)
    damage = collections.defaultdict(lambda: [0, 0.0])
    movement = collections.defaultdict(list)
    deaths = []
    header = footer = None
    count = 0
    sequences = set()

    def remember(body):
        if not body:
            return None
        uid = body['uuid']
        actors[uid] = {k: body[k] for k in ('name', 'actor', 'type', 'weapon', 'gameMode', 'entityId') if k in body}
        return uid

    with Path(path).open(encoding='utf-8') as stream:
        for line in stream:
            row = json.loads(line)
            kind = row['kind']
            if kind == 'capture_start':
                header = row
                continue
            if kind == 'capture_end':
                footer = row
                continue
            count += 1
            sequences.add(row['sequence'])
            if header is None or row['capture'] != header['capture']:
                raise ValueError('capture identity changed or missing header')
            if kind == 'position':
                body = row['body']
                uid = remember(body)
                movement[uid].append((row['serverTick'], body['x'], body['y'], body['z'], body.get('health'), body['entityId'], row['dimension']))
            elif kind == 'decision':
                uid = remember(row['fighter'])
                remember(row['target'])
                decisions[uid][row['gate']] += 1
            elif kind == 'strike':
                uid = remember(row['fighter'])
                remember(row['target'])
                strikes[uid][row['stage'] + ':' + row['detail']] += 1
            elif kind == 'gesture_start':
                uid = remember(row['fighter'])
                gestures[uid][row['gesture']] += 1
            elif kind == 'damage_applied':
                source, target = remember(row.get('source')), remember(row['target'])
                damage[(source, target)][0] += 1
                damage[(source, target)][1] += row['damage']
            elif kind == 'death':
                deaths.append({'source': remember(row.get('source')), 'target': remember(row['target']), 'result': row['result']})
    if header is None:
        raise ValueError('missing capture header')
    complete = bool(footer and footer['complete'] and count == footer['writtenEvents'] == footer['expectedEvents']
                    and sequences == set(range(1, count + 1)))
    subjects = []
    for uid, identity in actors.items():
        points = sorted(movement[uid])
        speeds = []
        distance = 0.0
        for before, after in zip(points, points[1:]):
            elapsed = after[0] - before[0]
            if elapsed <= 0 or before[5:] != after[5:]:
                continue
            step = math.hypot(after[1] - before[1], after[3] - before[3])
            distance += step
            speeds.append(step / elapsed)
        subjects.append({'uuid': uid, **identity, 'positionSamples': len(points),
                         'observedHorizontalDistance': round(distance, 3),
                         'maxBlocksPerServerTick': round(max(speeds, default=0), 4),
                         'healthFirst': points[0][4] if points else None,
                         'healthLast': points[-1][4] if points else None,
                         'decisionTransitions': dict(decisions[uid]), 'strikeEvents': dict(strikes[uid]),
                         'gestures': dict(gestures[uid])})
    return {'capture': header, 'footer': footer, 'transportComplete': complete,
            'warning': None if complete else 'Partial recording: absence of an event is not proof that it did not occur.',
            'subjects': subjects,
            'damage': [{'source': a, 'target': b, 'events': value[0], 'total': round(value[1], 3)} for (a, b), value in damage.items()],
            'deaths': deaths}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    args = parser.parse_args()
    print(json.dumps(summarize(args.capture), ensure_ascii=False, indent=2))
