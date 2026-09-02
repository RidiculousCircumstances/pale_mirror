import assert from 'node:assert/strict';
import test from 'node:test';
import { readdir, readFile } from 'node:fs/promises';
import { correlation, diagnosticForAssertion, diagnosticFromPilotLine, hasDiagnosticResponses, jfrCaptureRequest, logOffsetAfterMarker, newManifest, pilotDiagnosticActionStep, pilotServerPid, pilotServerReady, restartSegments, selectMutterXauthority, traceRecord, validateScenario } from '../src/scenario.mjs';

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
  assert.doesNotThrow(() => validateScenario({ ...inspection, actions: [{ type: 'inspect', view: 'performance', id: '' }] }));
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

test('native pilot recognizes the distinct read-only route construction and maintenance views', () => {
  const routeConstruction = { ...scenario, actions: [{ type: 'wait_until_diagnostic', view: 'route_construction', id: 'settlement:1',
    expect: { status: 'ok', phase: 'BUILDING' }, timeoutMs: 30_000 }], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(routeConstruction));
  assert.throws(() => validateScenario({ ...routeConstruction, actions: [{ ...routeConstruction.actions[0], id: '' }] }), /wait_until_diagnostic/);
  const routeMaintenance = { ...routeConstruction, actions: [{ ...routeConstruction.actions[0], view: 'route_maintenance' }] };
  assert.doesNotThrow(() => validateScenario(routeMaintenance));
});

test('native pilot recognizes the bounded declared-route diagnostic view', () => {
  const routeTopology = { ...scenario, actions: [{ type: 'wait_until_diagnostic', view: 'route_topology', id: 'settlement:1',
    expect: { status: 'ok', gradedEdges: 4, maximumGrade: 1 }, timeoutMs: 30_000 }], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(routeTopology));
  assert.throws(() => validateScenario({ ...routeTopology, actions: [{ ...routeTopology.actions[0], id: '' }] }), /wait_until_diagnostic/);
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
    { type: 'quick_move_from_container', item: 'minecraft:wheat', count: 64, timeoutMs: 10_000 },
    { type: 'wait_until_container_item', containerId: 'container:4-depot', item: 'minecraft:glowstone_dust', count: 1, slot: 0, timeoutMs: 30_000 }
  ], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(ingress));
  assert.throws(() => validateScenario({ ...ingress, actions: [{ ...ingress.actions[1], count: 65 }] }), /quick_move_from_inventory/);
  assert.throws(() => validateScenario({ ...ingress, actions: [{ ...ingress.actions[3], containerId: 'depot:4' }] }), /wait_until_container_item/);
  assert.throws(() => validateScenario({ ...ingress, actions: [{ ...ingress.actions[2], count: 0 }] }), /quick_move_from_container/);
});

test('an isolated scenario has an explicit deterministic disposable-world seed', () => {
  const isolated = { ...scenario, isolation: { mode: 'disposable_lite', seed: 41 } };
  assert.doesNotThrow(() => validateScenario(isolated));
  assert.throws(() => validateScenario({ ...isolated, isolation: { mode: 'shared', seed: 41 } }), /isolation/);
});

test('chunk visits are ordinary-player travel and may be causal evidence actions', () => {
  const visit = { ...scenario, setup: [{ type: 'visit', dimension: 'pale_mirror:frontier_graybox', position: { x: 1, y: 65, z: 2 }, settleMs: 1000 }] };
  assert.doesNotThrow(() => validateScenario(visit));
  assert.throws(() => validateScenario({ ...visit, setup: [{ ...visit.setup[0], dimension: 'frontier_graybox' }] }), /visit needs/);
  assert.doesNotThrow(() => validateScenario({ ...visit, actions: [{ ...visit.setup[0] }], assertions: [], frames: [] }));
});

test('native pilot permits only named isolated development profiles', () => {
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'scene-return' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'settlement-assault' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'hive-nutrient-transfer' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'hot-scout-sighting' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'hot-scout-intercept' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'hot-scout-patrol-recovery' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'operation-assembly' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'settlement-provision' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'health-quarantine' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'medical-treatment' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'resident-transit' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'production-worker-death' } }));
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'stepped-route' } }));
  assert.throws(() => validateScenario({ ...scenario, server: { ...scenario.server, profile: 'arbitrary-fixture' } }), /profile/);
});

test('native pilot permits one ordinary bounded block placement as causal evidence', () => {
  const placement = { ...scenario, actions: [{ type: 'place', item: 'minecraft:stone', position: { x: 1, y: 65, z: 2 }, timeoutMs: 10_000 }], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(placement));
  assert.throws(() => validateScenario({ ...placement, actions: [{ ...placement.actions[0], item: 'stone' }] }), /place needs/);
  assert.throws(() => validateScenario({ ...placement, actions: [{ ...placement.actions[0], timeoutMs: 120_001 }] }), /place needs/);
});

test('isolated server view distance is bounded and explicit when a scenario needs a COLD chunk', () => {
  assert.doesNotThrow(() => validateScenario({ ...scenario, server: { ...scenario.server, viewDistance: 4 } }));
  assert.throws(() => validateScenario({ ...scenario, server: { ...scenario.server, viewDistance: 1 } }), /viewDistance/);
  assert.throws(() => validateScenario({ ...scenario, server: { ...scenario.server, viewDistance: 33 } }), /viewDistance/);
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

test('field materialization may follow one declared read-only crop anchor but mutation remains literal', () => {
  const crop = { diagnostic: { view: 'site', id: 'site:1-wheat-field', field: 'firstCrop' } };
  const anchored = { ...scenario, setup: [], actions: [
    { type: 'wait_until_block', position: crop, block: 'minecraft:wheat', timeoutMs: 30_000 },
    { type: 'look', at: crop },
    { type: 'assert_visible_block', position: crop, timeoutMs: 10_000 }
  ], assertions: [], frames: [] };
  assert.doesNotThrow(() => validateScenario(anchored));
  assert.throws(() => validateScenario({ ...anchored, actions: [{ ...anchored.actions[0], position: { diagnostic: { view: 'site', id: 'site:1-wheat-field', field: 'cropSlots' } } }] }), /position/);
  assert.throws(() => validateScenario({ ...anchored, actions: [{ type: 'break', position: crop }] }), /position/);
});

test('native pilot may right-click one visible v3 board and await only its local card receipt', () => {
  const interaction = { ...scenario, setup: [], actions: [
    { type: 'interact_board', text: 'WHEAT FIELD', title: 'Northwatch', position: { x: 4, y: 67, z: 5 }, radius: 3, maxDistance: 64, maxAngleDeg: 50, timeoutMs: 30_000 }
  ], assertions: [], frames: [{ after: 1, name: 'object-card', presentation: 'player' }] };
  assert.doesNotThrow(() => validateScenario(interaction));
  assert.throws(() => validateScenario({ ...interaction, actions: [{ ...interaction.actions[0], title: '' }] }), /interact_board/);
  assert.throws(() => validateScenario({ ...interaction, actions: [{ ...interaction.actions[0], maxDistance: 129 }] }), /interact_board/);
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

test('stepped-route recovery binds each physical-loss assertion to its producing diagnostic action', async () => {
  const steppedRoute = JSON.parse(await readFile(new URL('../scenarios/disposable-stepped-route-restart.json', import.meta.url), 'utf8'));
  const foundation = steppedRoute.assertions.find((value) => value.view === 'physical_delta' && value.id === '-372,65,-343');
  const deck = steppedRoute.assertions.find((value) => value.view === 'physical_delta' && value.id === '-372,66,-343');
  assert.equal(steppedRoute.actions[foundation.after - 1].type, 'wait_until_diagnostic');
  assert.equal(steppedRoute.actions[foundation.after - 1].id, foundation.id);
  assert.equal(steppedRoute.actions[deck.after - 1].type, 'wait_until_diagnostic');
  assert.equal(steppedRoute.actions[deck.after - 1].id, deck.id);
  const segments = restartSegments(steppedRoute);
  assert.equal(segments.before.assertions.length, 2);
  assert.equal(segments.after.assertions.filter((value) => value.view === 'physical_delta').length, 2);
  assert.equal(segments.after.assertions.find((value) => value.view === 'route_topology').after, 1);
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

test('an action-bound assertion retains its own diagnostic when a later action reads the same object', () => {
  const assertion = { after: 4, view: 'operation', id: 'operation:supply-1-2' };
  const cold = { actionStep: 4, value: { kind: 'operation', id: 'operation:supply-1-2', hiveEngagement: { status: 'COLD_COMBAT' } } };
  const hot = { actionStep: 6, value: { kind: 'operation', id: 'operation:supply-1-2', hiveEngagement: { status: 'HOT' } } };
  assert.equal(diagnosticForAssertion([cold, hot], assertion), cold);
});

test('native pilot diagnostics use the quiet structured marker before legacy chat', () => {
  const quiet = diagnosticFromPilotLine('[Render thread] PMV3_PILOT_DIAGNOSTIC {"kind":"settlement","id":"settlement:1"}');
  assert.deepEqual(quiet.value, { kind: 'settlement', id: 'settlement:1' });
  const legacy = diagnosticFromPilotLine('PMV3_DIAG {"kind":"summary","id":""}');
  assert.deepEqual(legacy.value, { kind: 'summary', id: '' });
  assert.equal(diagnosticFromPilotLine('ordinary log line'), null);
});

test('pilot diagnostic action identity survives interleaved Gradle output', () => {
  assert.equal(pilotDiagnosticActionStep({ pilotActionStep: 3 }, 1), 3);
  assert.equal(pilotDiagnosticActionStep({ pilotActionStep: 0 }, 2), 2);
  assert.equal(pilotDiagnosticActionStep({}, 2), 2);
});

test('recovery log boundary follows the unique current server marker, not stale log size', () => {
  const marker = 'PMV3_PILOT_SERVER runId=current';
  const log = `old data\nPMV3_PILOT_SERVER runId=foreign pid=1\n${marker} pid=2\nnew data\n`;
  assert.equal(logOffsetAfterMarker(log, marker), log.indexOf('new data'));
  assert.equal(logOffsetAfterMarker(log, 'absent'), undefined);
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

test('isolated runner waits for its exact server marker rather than generic startup output', () => {
  const runId = '9f4809f5-16e2-43f6-a4f5-fc0404a4e0dd';
  assert.equal(pilotServerReady('Done\nFrontier v3 runtime started', runId), false);
  assert.equal(pilotServerReady(`Done\nFrontier v3 runtime started\nPMV3_PILOT_SERVER runId=${runId} pid=12345`, runId), true);
});

test('JFR evidence is opt-in, bounded and confined to the disposable build profile directory', () => {
  assert.equal(jfrCaptureRequest({}, '/tmp/frontier-v3'), undefined);
  assert.deepEqual(jfrCaptureRequest({ FRONTIER_V3_JFR_OUTPUT: 'build/profiles/scale-41.jfr', FRONTIER_V3_JFR_DURATION: '120s' }, '/tmp/frontier-v3'),
    { output: '/tmp/frontier-v3/build/profiles/scale-41.jfr', duration: '120s' });
  assert.throws(() => jfrCaptureRequest({ FRONTIER_V3_JFR_OUTPUT: '../outside.jfr' }, '/tmp/frontier-v3'), /build\/profiles/);
  assert.throws(() => jfrCaptureRequest({ FRONTIER_V3_JFR_OUTPUT: 'build/profiles/scale-41.jfr', FRONTIER_V3_JFR_DURATION: 'forever' }, '/tmp/frontier-v3'), /JFR_DURATION/);
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
