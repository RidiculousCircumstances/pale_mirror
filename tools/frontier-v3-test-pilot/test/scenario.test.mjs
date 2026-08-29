import assert from 'node:assert/strict';
import test from 'node:test';
import { readdir, readFile } from 'node:fs/promises';
import { correlation, hasDiagnosticResponses, newManifest, pilotServerPid, restartSegments, selectMutterXauthority, traceRecord, validateScenario } from '../src/scenario.mjs';

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
  const inspection = { ...scenario, actions: [{ type: 'inspect', view: 'summary', id: '' }], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(inspection));
  assert.throws(() => validateScenario({ ...inspection, actions: [{ type: 'inspect', view: 'site', id: '' }] }), /inspect needs a read-only v3 view and id/);
  assert.doesNotThrow(() => validateScenario({ ...scenario, assertions: [{ after: 0, view: 'summary', id: '', expect: { status: 'ok' } }] }));
  assert.throws(() => validateScenario({ ...scenario, assertions: [{ after: 0, view: 'site', id: '', expect: { status: 'ok' } }] }), /invalid diagnostic assertion/);
});

test('native pilot may await a bounded fresh read-only diagnostic predicate', () => {
  const diagnosticWait = { ...scenario, actions: [{ type: 'wait_until_diagnostic', view: 'site', id: 'site:1-wheat-field',
    expect: { status: 'ok', growthStage: 7 }, timeoutMs: 180_000 }], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(diagnosticWait));
  assert.throws(() => validateScenario({ ...diagnosticWait, actions: [{ ...diagnosticWait.actions[0], expect: [] }] }), /wait_until_diagnostic/);
  assert.throws(() => validateScenario({ ...diagnosticWait, actions: [{ ...diagnosticWait.actions[0], timeoutMs: 300_001 }] }), /wait_until_diagnostic/);
});

test('native pilot has one domain wait for a confirmed exact harvest, not READY', () => {
  const harvest = { ...scenario, actions: [{ type: 'wait_until_harvest_result', siteId: 'site:1-wheat-field',
    intentId: 'intent:site-harvest-1-wheat-field-1', itemId: 'item:site-harvest-1-wheat-field-1-wheat', timeoutMs: 180_000 }], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(harvest));
  assert.throws(() => validateScenario({ ...harvest, actions: [{ ...harvest.actions[0], itemId: 'wheat' }] }), /wait_until_harvest_result/);
});

test('native pilot moves an exact real player stack into a container before accepting ingress', () => {
  const ingress = { ...scenario, actions: [
    { type: 'open_container', position: { x: 1, y: 64, z: 2 }, timeoutMs: 10_000 },
    { type: 'quick_move_from_inventory', item: 'minecraft:glowstone_dust', count: 1, timeoutMs: 10_000 },
    { type: 'wait_until_container_item', containerId: 'container:4-depot', item: 'minecraft:glowstone_dust', count: 1, slot: 0, timeoutMs: 30_000 }
  ], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(ingress));
  assert.throws(() => validateScenario({ ...ingress, actions: [{ ...ingress.actions[1], count: 65 }] }), /quick_move_from_inventory/);
  assert.throws(() => validateScenario({ ...ingress, actions: [{ ...ingress.actions[2], containerId: 'depot:4' }] }), /wait_until_container_item/);
});

test('an isolated scenario has an explicit deterministic disposable-world seed', () => {
  const isolated = { ...scenario, isolation: { mode: 'disposable_lite', seed: 41 } };
  assert.doesNotThrow(() => validateScenario(isolated));
  assert.throws(() => validateScenario({ ...isolated, isolation: { mode: 'shared', seed: 41 } }), /isolation/);
});

test('chunk visits are setup-only ordinary-player travel with bounded settle time', () => {
  const visit = { ...scenario, setup: [{ type: 'visit', dimension: 'pale_mirror:frontier_graybox', position: { x: 1, y: 65, z: 2 }, settleMs: 1000 }] };
  assert.doesNotThrow(() => validateScenario(visit));
  assert.throws(() => validateScenario({ ...visit, setup: [{ ...visit.setup[0], dimension: 'frontier_graybox' }] }), /visit needs/);
  assert.throws(() => validateScenario({ ...visit, actions: [{ ...visit.setup[0] }] }), /unsupported actions action/);
});

test('semantic visible checks are bounded evidence actions, not world mutations', () => {
  const visible = { ...scenario, setup: [], actions: [
    { type: 'assert_visible_block', position: { x: 1, y: 64, z: 2 }, timeoutMs: 10_000 },
    { type: 'assert_visible_board', text: 'WHEAT FIELD', position: { x: 4, y: 67, z: 5 }, radius: 3, maxDistance: 64, maxAngleDeg: 50, timeoutMs: 30_000 }
  ], assertions: [], frames: [{ after: 2, name: 'semantic-frame' }] };
  assert.doesNotThrow(() => validateScenario(visible));
  assert.throws(() => validateScenario({ ...visible, actions: [{ ...visible.actions[1], maxDistance: 129 }] }), /assert_visible_board/);
  assert.throws(() => validateScenario({ ...visible, setup: [visible.actions[0]] }), /unsupported setup action/);
});

test('restart runner slices action-relative assertions without a second scenario language', () => {
  const recoverable = { ...scenario, setup: [{ type: 'command', command: '/time set day' }], actions: [
    { type: 'wait', ms: 10 }, { type: 'inspect', view: 'summary', id: '' }, { type: 'wait', ms: 10 }
  ], assertions: [
    { after: 1, view: 'summary', id: '', expect: { status: 'ok' } },
    { after: 2, view: 'summary', id: '', expect: { status: 'ok' } }
  ], frames: [{ after: 1, name: 'before-restart' }, { after: 3, name: 'after-restart' }],
  restart: { mode: 'abrupt', afterAction: 1, resumeSetup: [{ type: 'visit', dimension: 'pale_mirror:frontier_graybox', position: { x: 1, y: 65, z: 2 }, settleMs: 0 }] } };
  assert.doesNotThrow(() => validateScenario(recoverable));
  const segments = restartSegments(recoverable);
  assert.equal(segments.mode, 'abrupt');
  assert.equal(segments.before.actions.length, 1);
  assert.equal(segments.after.actions.length, 2);
  assert.equal(segments.after.setup[0].type, 'visit');
  assert.deepEqual(segments.after.assertions.map((value) => value.after), [1]);
  assert.deepEqual(segments.after.frames.map((value) => value.after), [2]);
  assert.throws(() => validateScenario({ ...recoverable, restart: { mode: 'graceful', afterAction: 3 } }), /restart needs/);
});

test('native pilot may advance only the bounded canonical v3 clock', () => {
  const advance = { ...scenario, actions: [{ type: 'fast_forward', ticks: 24_000 }], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(advance));
  assert.throws(() => validateScenario({ ...advance, actions: [{ type: 'fast_forward', ticks: 24_001 }] }), /fast_forward/);
  assert.throws(() => validateScenario({ ...advance, actions: [{ type: 'fast_forward', ticks: 1.5 }] }), /fast_forward/);
});

test('manifest records stable action correlations', () => {
  const manifest = newManifest({ scenario, sha256: 'abc', runId: 'run-1' });
  assert.equal(correlation(manifest.runId, 2), 'scenario:run-1:2');
  assert.equal(manifest.scenarioId, 'field_player_break');
});

test('PMV3 JSONL records retain a run and action correlation without modpack logs', () => {
  const record = traceRecord({ runId: 'run-1', kind: 'action_completed', correlation: correlation('run-1', 2), step: 2 });
  assert.deepEqual({ source: record.source, runId: record.runId, kind: record.kind, correlation: record.correlation, step: record.step },
    { source: 'PMV3', runId: 'run-1', kind: 'action_completed', correlation: 'scenario:run-1:2', step: 2 });
});

test('runner waits for every requested asynchronous diagnostic response', () => {
  const assertions = [
    { view: 'site', id: 'site:1-wheat-field' },
    { view: 'trace', id: 'player:pilot' }
  ];
  const site = { value: { kind: 'site', id: 'site:1-wheat-field' } };
  const trace = { value: { kind: 'trace', id: 'player:pilot' } };
  assert.equal(hasDiagnosticResponses([site], assertions), false);
  assert.equal(hasDiagnosticResponses([site, trace], assertions), true);
});

test('visual frames use a unique clean capture barrier unless player UI is explicit', () => {
  assert.doesNotThrow(() => validateScenario({ ...scenario, frames: [{ after: 1, name: 'clean' }, { after: 2, name: 'player', presentation: 'player' }] }));
  assert.throws(() => validateScenario({ ...scenario, frames: [{ after: 1, name: 'one' }, { after: 1, name: 'two' }] }), /invalid frame declaration/);
  assert.throws(() => validateScenario({ ...scenario, frames: [{ after: 0, name: 'zero' }] }), /invalid frame declaration/);
  assert.throws(() => validateScenario({ ...scenario, frames: [{ after: 1, name: 'bad', presentation: 'cinematic' }] }), /invalid frame declaration/);
  assert.throws(() => validateScenario({ ...scenario, actions: [{ type: 'hud', visible: false }], frames: [] }), /unsupported actions action/);
});

test('visible audit resolves only one unambiguous Wayland Xauthority file', () => {
  assert.equal(selectMutterXauthority(['.mutter-Xwaylandauth.AZ4VT3', 'wayland-0']), '.mutter-Xwaylandauth.AZ4VT3');
  assert.equal(selectMutterXauthority(['.mutter-Xwaylandauth.first', '.mutter-Xwaylandauth.second']), undefined);
  assert.equal(selectMutterXauthority(['.Xauthority']), undefined);
});

test('abrupt recovery resolves only the JVM that echoed its exact disposable nonce', () => {
  const runId = '05e2ad0c-69d1-45af-8d3c-4d7908a4a63d';
  assert.equal(pilotServerPid(`other output\nPMV3_PILOT_SERVER runId=${runId} pid=12345\n`, runId), 12345);
  assert.equal(pilotServerPid('PMV3_PILOT_SERVER runId=foreign pid=98765', runId), undefined);
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
