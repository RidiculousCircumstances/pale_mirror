import assert from 'node:assert/strict';
import test from 'node:test';
import { readdir, readFile } from 'node:fs/promises';
import { correlation, newManifest, validateScenario } from '../src/scenario.mjs';

const scenario = {
  schema: 1,
  id: 'field_player_break',
  server: { host: '127.0.0.1', port: 25565 },
  pilot: { username: 'PMTestPilot' },
  setup: [{ type: 'command', command: '/say setup' }, { type: 'observe', viewer: 'PMAudit' }],
  actions: [{ type: 'walk', position: { x: 1, y: 64, z: 2 } }, { type: 'break', position: { x: 1, y: 64, z: 2 } }],
  assertions: [{ after: 2, view: 'site', id: 'site:1-wheat-field', expect: { status: 'ok' } }],
  frames: [{ after: 2, name: 'after-break' }]
};

test('scenario separates setup from evidence-bearing actions', () => {
  assert.doesNotThrow(() => validateScenario(scenario));
  assert.throws(() => validateScenario({ ...scenario, actions: [{ type: 'command', command: '/kill @s' }] }), /unsupported actions action/);
  assert.throws(() => validateScenario({ ...scenario, setup: [{ type: 'break', position: { x: 1, y: 2, z: 3 } }] }), /unsupported setup action/);
});

test('summary diagnostics need no object identity while object diagnostics do', () => {
  assert.doesNotThrow(() => validateScenario({ ...scenario, assertions: [{ after: 0, view: 'summary', id: '', expect: { status: 'ok' } }] }));
  assert.throws(() => validateScenario({ ...scenario, assertions: [{ after: 0, view: 'site', id: '', expect: { status: 'ok' } }] }), /invalid diagnostic assertion/);
});

test('manifest records stable action correlations', () => {
  const manifest = newManifest({ scenario, sha256: 'abc', runId: 'run-1' });
  assert.equal(correlation(manifest.runId, 2), 'scenario:run-1:2');
  assert.equal(manifest.scenarioId, 'field_player_break');
});

test('pilot module loads its pinned CommonJS pathfinder dependency', async () => {
  const pilot = await import('../src/pilot.mjs');
  assert.equal(typeof pilot.connectPilot, 'function');
  assert.equal(typeof pilot.perform, 'function');
});

test('checked-in scenarios parse through the declared runner boundary', async () => {
  const scenarios = await readdir(new URL('../scenarios/', import.meta.url));
  for (const name of scenarios.filter((value) => value.endsWith('.json'))) {
    const source = await readFile(new URL(`../scenarios/${name}`, import.meta.url), 'utf8');
    assert.doesNotThrow(() => validateScenario(JSON.parse(source)), name);
  }
});
