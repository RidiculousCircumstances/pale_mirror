import test from 'node:test';
import assert from 'node:assert/strict';
import { matches } from '../src/diagnostic-matcher.mjs';

test('resource arrays compare JSON contents, not allocation identity', () => {
  const expected = { lots: [{ id: 'lot:output', itemKind: 'minecraft:bread', quantity: 64 }], claims: [] };
  assert.equal(matches(JSON.parse(JSON.stringify(expected)), expected), true);
  for (const lots of [[], [{ ...expected.lots[0], quantity: 63 }],
    [{ ...expected.lots[0], itemKind: 'minecraft:wheat' }],
    [...expected.lots, expected.lots[0]], [{ ...expected.lots[0], extra: true }]]) {
    assert.equal(matches({ lots, claims: [] }, expected), false);
  }
});

test('nested objects remain partial; arrays cannot impersonate objects', () => {
  assert.equal(matches({ replica: { state: 'CURRENT', revision: 4 } }, { replica: { state: 'CURRENT' } }), true);
  assert.equal(matches({ lots: ['bread'] }, { lots: { 0: 'bread' } }), false);
  assert.equal(matches({ value: null }, { value: null }), true);
  assert.equal(matches({}, { value: null }), false);
});
