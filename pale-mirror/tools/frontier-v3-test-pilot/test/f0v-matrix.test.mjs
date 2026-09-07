import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { targetColdDifferentialScenario, compareDifferential, finalizeMatrixEntry, materializeF0vMatrix, requireArrivalCheckpoint, requireColdProgress, requireDistinctArrivalCheckpoints, requireTerminalEvidence } from '../src/f0v-matrix.mjs';

const contract = {
  schema: 3, family: 'frontier.test-duration', canonicalOwner: 'TestOwner',
  declaration: { identities: ['actor:test'], cursorVocabulary: ['cursor'], legalCheckpoint: {
    view: 'summary', id: '', paths: ['cursor.index', 'cursor.retainedBody', 'cursor.actorBody'] },
    terminalInvariants: ['identity', 'claims', 'conservation', 'schedule', 'result'] },
  scenario: { idPrefix: 'f0v_matrix_test', isolation: { mode: 'disposable_lite', seed: 1 },
    server: { host: '127.0.0.1', port: 25575 }, pilot: { username: 'Pilot' }, setup: [], actions: [] },
  variants: Object.fromEntries(['never_loaded', 'arrival_checkpoint_one', 'arrival_checkpoint_two', 'unload_return', 'player_intervention', 'graceful_restart', 'abrupt_restart', 'neutral_observer_differential']
    .map((name) => [name, variant(name)]))
};

test('matrix materializes declared lanes and compares every required differential semantic class', () => {
  const matrix = materializeF0vMatrix(contract);
  const differential = matrix.find((entry) => entry.variant === 'neutral_observer_differential');
  assert.deepEqual(differential.executions.map((entry) => entry.lane), ['cold', 'hot_cold']);
  const crashes = matrix.find((entry) => entry.variant === 'abrupt_restart');
  assert.equal(crashes.executions.length, 5);
  assert.deepEqual(crashes.executions.map((entry) => entry.scenario.crash.boundary).sort(), [
    'hot_checkpoint_durable_before_drain_release', 'lease_recorded_before_physical_materialization',
    'physical_effect_visible_before_typed_observation', 'release_durable_before_cold_resumption',
    'typed_observation_durable_before_next_process_checkpoint'
  ]);
  const manifest = { diagnostics: [
    { value: { kind: 'summary', id: '', owner: 'actor:test', identity: 'actor:test', claims: { owner: 1, lease: null }, conserved: 64, conservation: 64,
      schedule: { entries: [{ id: 'schedule:test', dueAt: 10, kind: 'test', weight: 1 }] }, cursor: bodyCursor(7), instant: 112, result: 'done' } }
  ] };
  assert.deepEqual(compareDifferential(differential, manifest, manifest).map((entry) => entry.invariant),
    ['identity', 'claims', 'conservation', 'schedule', 'result']);
  assert.throws(() => compareDifferential(differential, manifest, { diagnostics: [{ value: { kind: 'summary', id: '', owner: 'actor:test', identity: 'actor:test', claims: { owner: 1, lease: null },
    conservation: 64, schedule: { entries: [{ id: 'schedule:test', dueAt: 11, kind: 'test', weight: 1 }] }, cursor: bodyCursor(7), instant: 112, result: 'done' } }] }), /schedule diverged/);
  assert.throws(() => compareDifferential(differential, manifest, { diagnostics: [{ value: { kind: 'summary', id: '', owner: 'actor:test', identity: 'actor:test', claims: { owner: 1, lease: null },
    conservation: 64, schedule: { entries: [{ id: 'schedule:test', dueAt: 10, kind: 'test', weight: 1 }] }, cursor: bodyCursor(8), instant: 112, result: 'done' } }] }), /result diverged at cursor.index/);
});

test('checked-in neutral differential rejects an intent lifecycle mismatch despite equal former fields', async () => {
  const checkedIn = JSON.parse(await readFile(new URL('../contracts/resource-site-harvest-f0v.json', import.meta.url), 'utf8'));
  const differential = materializeF0vMatrix(checkedIn).find((entry) => entry.variant === 'neutral_observer_differential');
  const cold = processDiagnostic(); const hotCold = structuredClone(cold);
  hotCold.result.intentStatus = 'RUNNING';
  assert.throws(() => compareDifferential(differential, { diagnostics: [{ value: cold }] },
    { diagnostics: [{ value: hotCold }] }), /result diverged at result.intentStatus/,
  'RUNNING changes recovery semantics even when cursor, custody, conservation and schedule remain equal');
});

test('selected differential evidence is explicitly pending while a complete local pair still compares', () => {
  const differential = materializeF0vMatrix(contract).find((entry) => entry.variant === 'neutral_observer_differential');
  const manifest = { diagnostics: [{ value: diagnostic() }] };
  for (const lane of ['cold', 'hot_cold']) {
    const pending = finalizeMatrixEntry(differential, [{ lane, result: manifest }], { selectedLane: true });
    assert.equal(pending.differential.status, 'pending_aggregate');
    assert.deepEqual(pending.differential.requiredLanes, ['cold', 'hot_cold']);
    assert.throws(() => finalizeMatrixEntry(differential, [{ lane, result: manifest }]), /incomplete/);
  }
  assert.equal(finalizeMatrixEntry(differential, [{ lane: 'cold', result: manifest }, { lane: 'hot_cold', result: manifest }]).differential.length, 5);
});

test('cold alignment carries the HOT terminal absolute instant to a server-authoritative target action', () => {
  const entry = materializeF0vMatrix(contract).find((value) => value.variant === 'neutral_observer_differential');
  const cold = entry.executions.find((execution) => execution.lane === 'cold').scenario;
  const hot = { diagnostics: [{ observed: { actionStep: 3, value: { ...diagnostic(), instant: 112 } } }] };
  const aligned = targetColdDifferentialScenario(entry, cold, hot);
  assert.deepEqual(aligned.f0vAbsoluteTarget, { view: 'summary', id: '', instantPath: 'instant', targetInstant: 112 });
  assert.deepEqual(aligned.actions.at(-2), { type: 'fast_forward_to_instant', targetInstant: 112, timeoutMs: 180_000 });
  assert.equal(aligned.assertions.at(-1).after, aligned.actions.length);
  assert.throws(() => targetColdDifferentialScenario(entry, cold, { diagnostics: [
    { observed: { actionStep: 3, value: { ...diagnostic(), instant: 0 } } }
  ] }), /absent or invalid/);
  assert.throws(() => compareDifferential(entry, { diagnostics: [{ value: { ...diagnostic(), instant: 111 } }] },
    { diagnostics: [{ value: { ...diagnostic(), instant: 112 } }] }), /terminal instants diverged/);
});

test('actual runner call site replaces the former single-half dereference with declared pending evidence', async () => {
  const differential = materializeF0vMatrix(contract).find((entry) => entry.variant === 'neutral_observer_differential');
  const manifest = { diagnostics: [{ value: diagnostic() }] };
  const originalSingleHalfComposition = () => compareDifferential(differential,
    [{ lane: 'cold', result: manifest }].find((run) => run.lane === 'cold').result,
    [{ lane: 'cold', result: manifest }].find((run) => run.lane === 'hot_cold').result);
  assert.throws(originalSingleHalfComposition, TypeError);
  const runner = await readFile(new URL('../src/run-f0v-matrix.mjs', import.meta.url), 'utf8');
  assert.match(runner, /finalizeMatrixEntry\(entry, runs, \{ selectedLane: requestedLane !== undefined \}\)/);
});

test('arrival comparator requires matching declaration and distinct integral stages', () => {
  assert.deepEqual(requireDistinctArrivalCheckpoints({ view: 'summary', id: '', path: 'cursor.index', value: 1 },
    { view: 'summary', id: '', path: 'cursor.index', value: 2 }).first.value, 1);
  assert.throws(() => requireDistinctArrivalCheckpoints({ view: 'summary', id: '', path: 'cursor.index', value: 1 },
    { view: 'summary', id: '', path: 'cursor.index', value: 1 }), /distinct/);
});

test('declared differential tolerance accepts its bound and rejects a larger deviation', () => {
  const tolerant = structuredClone(contract);
  tolerant.variants.neutral_observer_differential.differential.find((entry) => entry.invariant === 'conservation').tolerance = 1;
  const differential = materializeF0vMatrix(tolerant).find((entry) => entry.variant === 'neutral_observer_differential');
  const within = diagnostic(); within.conservation = 65;
  const beyond = diagnostic(); beyond.conservation = 66;
  assert.equal(compareDifferential(differential, { diagnostics: [{ value: diagnostic() }] }, { diagnostics: [{ value: within }] }).length, 5);
  assert.throws(() => compareDifferential(differential, { diagnostics: [{ value: diagnostic() }] }, { diagnostics: [{ value: beyond }] }), /conservation diverged/);
});

test('terminal evidence is derived from the generated ordinary diagnostic assertion', () => {
  const single = materializeF0vMatrix(contract).find((entry) => entry.variant === 'never_loaded').scenario;
  requireTerminalEvidence(single, { diagnostics: [{ value: { kind: 'summary', id: '', identity: 'actor:test', claims: { owner: 1, lease: null },
    conservation: 64, schedule: 's:1', result: 'done', residents: 1, status: 'ok' } }] });
  assert.throws(() => requireTerminalEvidence(single, { diagnostics: [{ value: { kind: 'summary', id: '', identity: 'actor:test', claims: { owner: 1, lease: null },
    conservation: 64, schedule: 's:1', result: 'done', residents: 0, status: 'ok' } }] }), /terminal assertion/);
});

test('never-loaded evidence requires a lease-free before/after COLD cursor advance with one coherent body', () => {
  const scenario = materializeF0vMatrix(contract).find((entry) => entry.variant === 'never_loaded').scenario;
  const manifest = { diagnostics: [
    { observed: { actionStep: 1, value: { kind: 'summary', id: '', claims: { lease: null }, cursor: bodyCursor(3) } } },
    { observed: { actionStep: scenario.actions.length, value: { kind: 'summary', id: '', claims: { lease: null }, cursor: bodyCursor(5) } } }
  ] };
  assert.deepEqual(requireColdProgress(scenario, manifest), { view: 'summary', id: '', beforeAction: 1, afterAction: scenario.actions.length,
    cursorPath: 'cursor.index', beforeCursor: 3, afterCursor: 5, minimumAdvance: 1 });
  assert.throws(() => requireColdProgress(scenario, { diagnostics: [
    { observed: { actionStep: 1, value: { kind: 'summary', id: '', claims: { lease: null }, cursor: bodyCursor(5) } } },
    { observed: { actionStep: scenario.actions.length, value: { kind: 'summary', id: '', claims: { lease: null }, cursor: bodyCursor(5) } } }
  ] }), /cursor did not advance/);
  assert.throws(() => requireColdProgress(scenario, { diagnostics: [
    { observed: { actionStep: 1, value: { kind: 'summary', id: '', claims: { lease: null }, cursor: bodyCursor(3) } } },
    { observed: { actionStep: scenario.actions.length, value: { kind: 'summary', id: '', claims: { lease: { status: 'HOT' } }, cursor: bodyCursor(5) } } }
  ] }), /HOT authority/);
});

test('arrival evidence rejects a missing or non-integral retained process checkpoint', () => {
  const first = materializeF0vMatrix(contract).find((entry) => entry.variant === 'arrival_checkpoint_one');
  assert.deepEqual(requireArrivalCheckpoint(first, { diagnostics: [{ value: { kind: 'summary', id: '', cursor: 7 } }] }),
    { view: 'summary', id: '', path: 'cursor', value: 7 });
  assert.throws(() => requireArrivalCheckpoint(first, { diagnostics: [{ value: { kind: 'summary', id: '', cursor: 7.5 } }] }),
    /non-integral/);
});

function variant(name) {
  const driver = { never_loaded: 'COLD', arrival_checkpoint_one: 'HOT', arrival_checkpoint_two: 'HOT', unload_return: 'HOT_COLD_HOT',
    player_intervention: 'HOT', graceful_restart: 'SNAPSHOT_WAL', abrupt_restart: 'CRASH_WAL', neutral_observer_differential: 'COLD_VS_HOT_COLD' }[name];
  return {
    driver, evidence: ['actor', 'cursor'], actions: [{ type: 'inspect', view: 'summary', id: '' }, { type: 'inspect', view: 'summary', id: '' }],
    assertions: [
      ...(name === 'never_loaded' ? [{ after: 1, view: 'summary', id: '', expect: { status: 'ok', claims: { lease: null } } }] : []),
      { after: 'terminal', view: 'summary', id: '', expect: { status: 'ok', identity: 'actor:test', claims: { owner: 1, lease: null },
        conservation: 64, schedule: 's:1', result: 'done', residents: 1 } }
    ],
    terminalProjections: [
      { invariant: 'identity', view: 'summary', id: '', paths: ['identity'] },
      { invariant: 'claims', view: 'summary', id: '', paths: ['claims'] },
      { invariant: 'conservation', view: 'summary', id: '', paths: ['conservation'] },
      { invariant: 'schedule', view: 'summary', id: '', paths: ['schedule'] },
      { invariant: 'result', view: 'summary', id: '', paths: ['result'] }
    ],
    ...(driver === 'SNAPSHOT_WAL' || driver === 'CRASH_WAL' ? { restartAfterAction: 1 } : {}),
    ...(driver === 'CRASH_WAL' ? { crashWindows: crashWindows() } : {}),
    ...(name === 'never_loaded' ? { coldProgress: { beforeAction: 1, after: 'terminal', view: 'summary', id: '', cursorPath: 'cursor.index',
      retainedBodyPath: 'cursor.retainedBody', actorBodyPath: 'cursor.actorBody', leasePath: 'claims.lease', minimumAdvance: 1 } } : {}),
    ...((name === 'arrival_checkpoint_one' || name === 'arrival_checkpoint_two') ? { arrivalCheckpoint: {
      view: 'summary', id: '', path: 'cursor' } } : {}),
    ...(driver === 'COLD_VS_HOT_COLD' ? { alignment: { view: 'summary', id: '', instantPath: 'instant' }, differential: [
      { invariant: 'identity', view: 'summary', id: '', paths: ['identity'] },
      { invariant: 'claims', view: 'summary', id: '', paths: ['claims'] },
      { invariant: 'conservation', view: 'summary', id: '', paths: ['conservation'] },
      { invariant: 'schedule', view: 'summary', id: '', paths: ['schedule', 'schedule.entries'] },
      { invariant: 'result', view: 'summary', id: '', paths: ['instant', 'result', 'cursor.index', 'cursor.retainedBody', 'cursor.actorBody'] }
    ], lanes: { cold: { setup: [], actions: [] }, hot_cold: { setup: [], actions: [{ type: 'inspect', view: 'summary', id: '' }] } } } : {})
  };
}

function bodyCursor(index) {
  const body = { x: index, y: 65, z: -index };
  return { index, retainedBody: body, actorBody: { ...body } };
}

function diagnostic() {
  return { kind: 'summary', id: '', owner: 'actor:test', identity: 'actor:test', claims: { owner: 1, lease: null }, conserved: 64, conservation: 64,
    schedule: { entries: [{ id: 'schedule:test', dueAt: 10, kind: 'test', weight: 1 }] }, cursor: bodyCursor(7), instant: 112, result: 'done' };
}

function processDiagnostic() {
  const body = { x: 14, y: 65, z: -22 };
  return { kind: 'process', id: 'job:site-harvest-4-wheat-field-1', instant: 24608,
    identity: { job: 'job:site-harvest-4-wheat-field-1', worker: 'resident:4-31' },
    claims: { site: 'site:4-wheat-field', worker: 'resident:4-31', lease: null },
    conservation: { outputItem: 'item:site-harvest-4-wheat-field-1-wheat', completedCropSlots: 0, pendingCropSlot: -1, totalCropSlots: 64 },
    schedule: { count: 1, entries: [{ id: 'schedule:resource-site-harvest-cold-progress-4-wheat-field-1', dueAt: 24702,
      kind: 'frontier.resource_site.harvest.cold_progress', weight: 1 }] },
    cursor: { index: 17, retainedBody: body, actorBody: { ...body } },
    result: { sitePhase: 'HARVESTING', intentKind: 'RESOURCE_SITE_HARVEST', intentStatus: 'PREPARED', intentObservationId: null, complete: false } };
}

function crashWindows() {
  return [
    ['lease_recorded_before_physical_materialization', 'frontier.resource_site_harvest_scene_lease_prepared'],
    ['physical_effect_visible_before_typed_observation', 'frontier.resource_site_harvest_progressed'],
    ['typed_observation_durable_before_next_process_checkpoint', 'frontier.resource_site_harvest_progressed'],
    ['hot_checkpoint_durable_before_drain_release', 'frontier.resource_site_harvest_hot_traversal_advanced'],
    ['release_durable_before_cold_resumption', 'frontier.scene_lease_released_v2']
  ].map(([boundary, payloadType]) => ({ boundary, phase: 'before_restart', owner: 'job:harvest-1', payloadType, expectedRevision: 1 }));
}
