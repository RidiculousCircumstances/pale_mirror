import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { createFourWorkerMatrixPlan } from '../src/ci-matrix.mjs';
import { validatePersistentMatrixPlan } from '../src/persistent-matrix.mjs';
import { ASSIGNED_PERSISTENT_MATRIX_KIND, compileAssignedPersistentMatrix, compilePersistentWorkerPlan, PERSISTENT_WORKER_PLAN_KIND, PERSISTENT_WORKER_PLAN_SCHEMA, validatePersistentWorkerPlan } from '../src/persistent-worker-plan.mjs';
import { restartSegments, validateScenario } from '../src/scenario.mjs';

const hash = (letter) => letter.repeat(64);
const sha = (value) => createHash('sha256').update(JSON.stringify(value)).digest('hex');
const contract = async () => JSON.parse(await readFile(new URL('../contracts/resource-site-harvest-f0v.json', import.meta.url), 'utf8'));
const plan = async () => createFourWorkerMatrixPlan(await contract(), { buildIdentitySha256: hash('a'), contractSha256: hash('b') });

function rehashPlan(value) {
  for (const shard of value.shards) shard.declaredWeight = shard.lanes.reduce((sum, lane) => sum + lane.weight, 0);
  value.planSha256 = sha({ schema: value.schema, kind: value.kind, buildIdentitySha256: value.buildIdentitySha256,
    contractSha256: value.contractSha256, workers: value.workers, lanes: value.lanes, shards: value.shards });
  return value;
}

function rehashCompiled(value) {
  value.contentSha256 = sha({ schema: value.schema, kind: value.kind, source: value.source, lanes: value.lanes });
  return value;
}

test('actual CI assignments compile deterministically without weakening the three-world benchmark', async () => {
  const source = await plan();
  const compiled = source.shards.map((shard) => compilePersistentWorkerPlan(source, shard.workerId, 'correctness-1'));
  assert.deepEqual(compiled, source.shards.map((shard) => compilePersistentWorkerPlan(source, shard.workerId, 'correctness-1')));
  assert.equal(source.lanes.length, 11);
  assert.equal(new Set(compiled.flatMap((entry) => entry.lanes.map((lane) => lane.id))).size, source.lanes.length);
  assert.equal(compiled.flatMap((entry) => entry.lanes).find((lane) => lane.id === source.shards[0].lanes[0].id).segments.length >= 1, true);
  const modes = new Set(compiled.flatMap((entry) => entry.lanes.flatMap((lane) => lane.segments.map((segment) => segment.completion))));
  assert.deepEqual([...modes].sort(), ['expected_crash', 'graceful_handoff', 'recovered_terminal', 'terminal']);
  for (const segment of compiled.flatMap((entry) => entry.lanes.flatMap((lane) => lane.segments))) validateScenario(segment.scenario);
  const mutable = structuredClone(source); const frozen = compilePersistentWorkerPlan(mutable, 'worker-0', 'correctness-1');
  const retained = JSON.stringify(frozen); mutable.shards[0].lanes[0].scenario.actions.pop();
  assert.equal(JSON.stringify(frozen), retained);
  assert.throws(() => { frozen.lanes[0].segments[0].worldKey = 'foreign'; }, TypeError);
  for (const workerId of ['worker-0', 'worker-3']) {
    const current = compiled.find((entry) => entry.source.workerId === workerId);
    assert.equal(current.lanes.length >= 2, true);
    assert.equal(new Set(current.lanes.map((lane) => lane.worldKey)).size, current.lanes.length);
    const benchmarkSegments = current.lanes.flatMap((lane) => lane.segments.map((segment, index) => ({ id: `${segment.id}-${index}`, scenarioId: segment.originalScenarioId,
      scenarioSha256: segment.scenarioSha256, worldKey: segment.worldKey, actionCount: segment.actionCount })));
    const benchmark = { schema: 1, kind: 'frontier-v3-persistent-matrix', workerId, buildIdentitySha256: source.buildIdentitySha256,
      segments: benchmarkSegments.slice(0, 2).map((segment, index) => ({ ...segment, final: index === 1 })) };
    assert.throws(() => validatePersistentMatrixPlan(benchmark), /three independent|malformed/);
  }
});

test('assigned runtime plan is compiler-anchored and retains inverse restart offsets', async () => {
  const source = await plan(); const compiled = compilePersistentWorkerPlan(source, 'worker-0', 'correctness-1');
  const runtime = compileAssignedPersistentMatrix(compiled, source, { workerId: 'worker-0', measurementId: 'correctness-1' });
  assert.equal(runtime.kind, ASSIGNED_PERSISTENT_MATRIX_KIND); assert.equal(runtime.workerPlanSha256, compiled.contentSha256);
  assert.equal(runtime.segments.every((segment) => Number.isInteger(segment.originalActionOffset) && segment.originalActionOffset >= 0), true);
  assert.doesNotThrow(() => validatePersistentMatrixPlan(runtime));
  assert.throws(() => compileAssignedPersistentMatrix(compiled, source, { workerId: 'worker-1', measurementId: 'correctness-1' }), /foreign|drifted/);
});

test('every real worker assigned plan carries derived crash lane identity and admits before execution', async () => {
  const source = await plan();
  for (const workerId of ['worker-0', 'worker-1', 'worker-2', 'worker-3']) {
    const compiled = compilePersistentWorkerPlan(source, workerId, 'correctness-1');
    const assigned = compileAssignedPersistentMatrix(compiled, source, { workerId, measurementId: 'correctness-1' });
    assert.doesNotThrow(() => validatePersistentMatrixPlan(assigned));
    for (const segment of assigned.segments.filter((segment) => segment.completion === 'expected_crash')) assert.equal(segment.expectedCrash.laneId, segment.laneId);
  }
});

test('runtime assignment retains and recomputes distinct original compiled and runtime identities', async () => {
  const source = await plan(); const compiled = compilePersistentWorkerPlan(source, 'worker-0', 'correctness-1');
  const assigned = compileAssignedPersistentMatrix(compiled, source, { workerId: 'worker-0', measurementId: 'correctness-1' });
  const core = { schema: 1, kind: assigned.kind, workerId: assigned.workerId, buildIdentitySha256: assigned.buildIdentitySha256,
    source: assigned.source, workerPlanSha256: assigned.workerPlanSha256, compiledContentSha256: assigned.contentSha256,
    segments: assigned.segments.map((segment) => ({ id: segment.id, laneId: segment.laneId, scenarioId: segment.scenarioId,
      scenarioSha256: segment.scenarioSha256, compiledScenarioSha256: segment.scenarioSha256,
      originalScenarioId: segment.originalScenarioId, originalScenarioSha256: segment.originalScenarioSha256,
      originalActionOffset: segment.originalActionOffset, worldKey: segment.worldKey, actionCount: segment.actionCount,
      completion: segment.completion, ...(segment.expectedCrash === undefined ? {} : { expectedCrash: segment.expectedCrash }), final: segment.final })) };
  const runtime = { ...core, contentSha256: sha(core) };
  const accepted = validatePersistentMatrixPlan(runtime);
  assert.equal(accepted.contentSha256, runtime.contentSha256);
  assert.doesNotThrow(() => validatePersistentMatrixPlan(accepted));
  assert.doesNotThrow(() => validatePersistentMatrixPlan(JSON.parse(JSON.stringify(accepted))));
  assert.throws(() => validatePersistentMatrixPlan({ ...runtime, compiledContentSha256: 'not-a-hash' }), /compiled identity/);
  for (const mutate of [
    (value) => { value.segments[0].scenarioSha256 = hash('c'); },
    (value) => { value.segments[0].compiledScenarioSha256 = hash('d'); },
    (value) => { value.segments[0].originalScenarioSha256 = hash('e'); },
    (value) => { value.segments[0].originalActionOffset++; },
    (value) => { value.segments.find((segment) => segment.completion === 'expected_crash').expectedCrash.owner = 'job:foreign'; },
    (value) => { value.contentSha256 = hash('f'); }
  ]) {
    const tampered = structuredClone(runtime); mutate(tampered);
    assert.throws(() => validatePersistentMatrixPlan(tampered), /runtime content identity/);
  }
  const malformed = structuredClone(runtime); delete malformed.segments[0].compiledScenarioSha256;
  assert.throws(() => validatePersistentMatrixPlan(malformed), /compiled identity/);
  const unknown = structuredClone(runtime); unknown.segments[0].foreignRuntimeField = true;
  assert.throws(() => validatePersistentMatrixPlan(unknown), /unknown or missing/);
});

test('abrupt release departure follows the durable hot-checkpoint barrier', async () => {
  const input = await contract(); const abrupt = input.variants.abrupt_restart; const graceful = input.variants.graceful_restart;
  assert.equal(abrupt.restartAfterAction, 5); assert.deepEqual(abrupt.actions[3], {
    type: 'wait_until_diagnostic', view: 'process', id: 'job:site-harvest-4-wheat-field-1',
    expect: { status: 'ok', claims: { lease: { status: 'HOT', members: 1 } } }, requireIncreaseAt: 'cursor.index', timeoutMs: 90000
  }); assert.deepEqual(abrupt.actions[4], {
    type: 'visit', dimension: 'pale_mirror:frontier_graybox', position: { x: 0, y: 65, z: 0 }, settleMs: 1000
  });
  assert.equal(abrupt.assertions[0].after, 3); assert.equal(abrupt.assertions.at(-1).expect.result.complete, false);
  assert.equal(graceful.restartAfterAction, 3); assert.equal(graceful.actions.some((action) => action.position?.x === 0 && action.position?.y === 65), false);
  assert.equal(new Set(abrupt.crashWindows.map((window) => window.boundary)).size, 3);
  assert.equal(new Set(abrupt.deferredCrashWindows.map((window) => window.boundary)).size, 2);
});

test('compiler preserves exact restart rebasing and crash closure modes', async () => {
  const source = await plan();
  const compiled = source.shards.map((shard) => compilePersistentWorkerPlan(source, shard.workerId, 'correctness-1'));
  const all = compiled.flatMap((entry) => entry.lanes.flatMap((lane) => lane.segments));
  const crashes = all.filter((segment) => segment.completion === 'expected_crash');
  assert.equal(crashes.length, 3);
  assert.deepEqual(crashes.map((segment) => segment.crash.boundary).sort(), [
    'hot_checkpoint_durable_before_drain_release', 'lease_recorded_before_physical_materialization',
    'release_durable_before_cold_resumption'
  ]);
  for (const crash of crashes) {
    assert.equal(crash.scenario.crash, undefined);
    assert.deepEqual(crash.crash, source.lanes.find((lane) => lane.id === crash.laneId).scenario.crash);
    const recovered = all.find((segment) => segment.laneId === crash.laneId && segment.completion === 'recovered_terminal');
    assert.ok(recovered);
    assert.equal(recovered.scenario.crash, undefined);
    assert.equal(recovered.scenario.restart, undefined);
    assert.equal(recovered.order, crash.order + 1);
    assert.equal(recovered.worldKey, crash.worldKey);
    assert.equal(recovered.scenario.f0vTerminalProjections.length, 5);
  }
  for (const lane of source.lanes.filter((lane) => lane.restart)) {
    const expected = restartSegments(lane.scenario);
    const actual = compiled.flatMap((entry) => entry.lanes).find((candidate) => candidate.id === lane.id).segments;
    assert.equal(actual.length, 2);
    const beforeExpected = lane.scenario.crash === undefined ? expected.before : { ...expected.before, crash: undefined };
    for (const field of ['setup', 'actions', 'assertions', 'frames']) assert.deepEqual(actual[0].scenario[field], beforeExpected[field]);
    const recoveredExpected = lane.scenario.crash === undefined ? expected.after : { ...expected.after, crash: undefined };
    for (const field of ['setup', 'actions', 'assertions', 'frames']) assert.deepEqual(actual[1].scenario[field], recoveredExpected[field]);
  }
});

test('compiler rebases nonempty split assertions and frames on both graceful and abrupt recovery halves', async () => {
  const source = await plan();
  for (const mode of ['graceful', 'abrupt']) {
    const synthetic = structuredClone(source); const target = synthetic.lanes.find((lane) => lane.scenario.restart?.mode === mode);
    const split = target.scenario.restart.afterAction; const assertion = structuredClone(target.scenario.assertions[0]);
    target.scenario.assertions = [...target.scenario.assertions, { ...assertion, after: split }, { ...assertion, after: split + 1 }];
    target.scenario.frames = [{ after: split, name: `split-before-${mode}`, presentation: 'clean' },
      { after: split + 1, name: `split-after-${mode}`, presentation: 'clean' }];
    target.scenarioSha256 = sha(target.scenario);
    const shard = synthetic.shards.find((candidate) => candidate.lanes.some((lane) => lane.id === target.id));
    shard.lanes[shard.lanes.findIndex((lane) => lane.id === target.id)] = structuredClone(target);
    const expected = restartSegments(target.scenario);
    const actual = compilePersistentWorkerPlan(rehashPlan(synthetic), shard.workerId, 'correctness-1').lanes.find((lane) => lane.id === target.id).segments;
    const beforeExpected = mode === 'abrupt' ? { ...expected.before, crash: undefined } : expected.before;
    const recoveredExpected = mode === 'abrupt' ? { ...expected.after, crash: undefined } : expected.after;
    for (const [index, expectedScenario] of [beforeExpected, recoveredExpected].entries()) {
      for (const field of ['setup', 'actions', 'assertions', 'frames']) assert.deepEqual(actual[index].scenario[field], expectedScenario[field]);
    }
    assert.equal(actual[1].scenario.assertions.at(-1).after, 1);
    assert.equal(actual[1].scenario.frames[0].after, 1);
  }
});

test('validator reconstructs from the immutable source plan and rejects malformed admissions', async () => {
  const source = await plan();
  const compiled = compilePersistentWorkerPlan(source, 'worker-0', 'correctness-1');
  const trusted = { workerId: 'worker-0', measurementId: 'correctness-1' };
  assert.equal(validatePersistentWorkerPlan(JSON.parse(JSON.stringify(compiled)), source, trusted).contentSha256, compiled.contentSha256);
  for (const mutate of [
    (value) => { value.contentSha256 = hash('c'); },
    (value) => { value.lanes[0].segments[0].completion = 'terminal'; },
    (value) => { value.lanes[0].segments.pop(); },
    (value) => { value.lanes[0].segments.push(structuredClone(value.lanes[0].segments[0])); },
    (value) => { value.lanes[1].worldKey = value.lanes[0].worldKey; },
    (value) => { value.source.measurementId = 'correctness-4'; },
    (value) => { value.source.workerId = 'worker-1'; },
    (value) => { value.source.buildIdentitySha256 = hash('c'); },
    (value) => { value.source.contractSha256 = hash('c'); }
  ]) {
    const tampered = structuredClone(compiled); mutate(tampered);
    assert.throws(() => validatePersistentWorkerPlan(tampered, source, trusted), /malformed|foreign|content-drifted|invalid/);
  }
  for (const foreignIdentity of [
    compilePersistentWorkerPlan(source, 'worker-0', 'correctness-2'),
    compilePersistentWorkerPlan(source, 'worker-1', 'correctness-1')
  ]) assert.throws(() => validatePersistentWorkerPlan(foreignIdentity, source, trusted), /foreign|content-drifted/);
  const superficiallyRehashed = structuredClone(compiled);
  superficiallyRehashed.lanes[0].segments[0].scenario.actions[0] = { type: 'wait', ms: 1 };
  superficiallyRehashed.lanes[0].segments[0].scenarioSha256 = sha(superficiallyRehashed.lanes[0].segments[0].scenario);
  assert.throws(() => validatePersistentWorkerPlan(rehashCompiled(superficiallyRehashed), source, trusted), /foreign|content-drifted/);
  const rearmedRecovery = structuredClone(compiled); const expectedCrash = rearmedRecovery.lanes[0].segments.find((segment) => segment.completion === 'expected_crash');
  const recovery = rearmedRecovery.lanes[0].segments.find((segment) => segment.completion === 'recovered_terminal');
  recovery.scenario.crash = structuredClone(expectedCrash.crash); recovery.scenarioSha256 = sha(recovery.scenario); recovery.crash = structuredClone(expectedCrash.crash);
  assert.throws(() => validatePersistentWorkerPlan(rehashCompiled(rearmedRecovery), source, trusted), /foreign|content-drifted/);
  assert.throws(() => compilePersistentWorkerPlan(source, 'worker-0', 'correctness-4'), /measurement/);
  const foreign = structuredClone(source); foreign.shards[0].lanes[0] = structuredClone(foreign.shards[0].lanes[0]);
  foreign.shards[0].lanes[0].scenario.id = 'foreign'; foreign.shards[0].lanes[0].scenarioSha256 = sha(foreign.shards[0].lanes[0].scenario);
  assert.throws(() => compilePersistentWorkerPlan(rehashPlan(foreign), 'worker-0', 'correctness-1'), /content-drifted/);
  assert.equal(compiled.schema, PERSISTENT_WORKER_PLAN_SCHEMA);
  assert.equal(compiled.kind, PERSISTENT_WORKER_PLAN_KIND);
});

test('compiler rejects malformed crash declarations before it can classify a completion mode', async () => {
  const source = await plan();
  const malformed = structuredClone(source);
  const target = malformed.lanes.find((lane) => lane.restart === false);
  target.scenario = { ...target.scenario, crash: { phase: 'before_restart', boundary: 'foreign', owner: 'job:foreign', payloadType: 'foreign', expectedRevision: 1 } };
  target.scenarioSha256 = sha(target.scenario); target.crash = true;
  const shard = malformed.shards.find((candidate) => candidate.lanes.some((lane) => lane.id === target.id));
  const index = shard.lanes.findIndex((lane) => lane.id === target.id); shard.lanes[index] = structuredClone(target);
  assert.throws(() => compilePersistentWorkerPlan(rehashPlan(malformed), shard.workerId, 'correctness-1'), /crash needs an abrupt restart/);
});

test('compiler admits a one-world shard but fails closed beyond its 32-segment capacity', async () => {
  const source = await plan();
  const oneWorld = structuredClone(source);
  oneWorld.shards = [
    { workerId: 'worker-0', lanes: [oneWorld.lanes[0]] },
    { workerId: 'worker-1', lanes: [oneWorld.lanes[1]] },
    { workerId: 'worker-2', lanes: [oneWorld.lanes[2]] },
    { workerId: 'worker-3', lanes: oneWorld.lanes.slice(3) }
  ];
  assert.equal(compilePersistentWorkerPlan(rehashPlan(oneWorld), 'worker-0', 'correctness-1').lanes.length, 1);
  const empty = structuredClone(oneWorld); empty.shards[0].lanes = [];
  assert.throws(() => compilePersistentWorkerPlan(rehashPlan(empty), 'worker-0', 'correctness-1'), /shard is malformed|no admitted/);

  const oversized = structuredClone(source);
  const restartable = oversized.lanes.map((lane, index) => ({ ...lane, id: `${lane.id}-restart-${index}`,
    scenario: { ...lane.scenario, id: `${lane.scenario.id}_restart_${index}`, crash: undefined,
      restart: { mode: 'graceful', afterAction: 1, resumeSetup: [] } }, crash: false, restart: true }));
  for (const lane of restartable) lane.scenarioSha256 = sha(lane.scenario);
  for (let index = 0; index < 9; index++) {
    const duplicate = structuredClone(restartable[index]);
    duplicate.id = `${duplicate.id}-extra-${index}`; duplicate.scenario.id = `${duplicate.scenario.id}_extra_${index}`;
    duplicate.scenarioSha256 = sha(duplicate.scenario); restartable.push(duplicate);
  }
  oversized.lanes = restartable;
  oversized.shards = [
    { workerId: 'worker-0', lanes: restartable.slice(0, 17) },
    { workerId: 'worker-1', lanes: restartable.slice(17, 18) },
    { workerId: 'worker-2', lanes: restartable.slice(18, 19) },
    { workerId: 'worker-3', lanes: restartable.slice(19) }
  ];
  assert.throws(() => compilePersistentWorkerPlan(rehashPlan(oversized), 'worker-0', 'correctness-1'), /bounded capacity/);
});
