import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CI_MATRIX_MAX_START_SKEW_MILLIS, CI_MATRIX_SCHEMA, createFourWorkerMatrixPlan, evidenceFromManifest, mergeFourWorkerMatrix, mergeSequentialFourWorkerMatrix, requireCiMatrixMedianSpeedup, requireCiMatrixSpeedup, terminalSemanticsFromManifest, validateCiCorrectnessAdmission, validateCiShardResult } from '../src/ci-matrix.mjs';
import { parse as parsePlan } from '../src/write-ci-matrix-plan.mjs';
import { compileWorkerPersistentPlan, parse as parseShard, requireWorkerIdentity } from '../src/run-ci-matrix-shard.mjs';
import { compareTiming, mergeMeasurements, parse as parseMerge } from '../src/merge-ci-matrix.mjs';
import { parse as parseSequential, workerEnvironment } from '../src/run-ci-matrix-sequential.mjs';
import { parse as parsePrepared } from '../src/prepare-native-build.mjs';
import { parseEnvironment as parseCiWorkerEnvironment } from '../src/validate-ci-worker-sample.mjs';

const hash = (letter) => letter.repeat(64);
const contract = async () => JSON.parse(await readFile(new URL('../contracts/resource-site-harvest-f0v.json', import.meta.url), 'utf8'));
const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const workflowRoot = resolve(project, '..', '.github', 'workflows');
const workflows = async () => await Promise.all([
  readFile(resolve(workflowRoot, 'build.yml'), 'utf8'),
  readFile(resolve(workflowRoot, 'f0va-native-correctness-sample.yml'), 'utf8')
]);

test('four-worker plan deterministically covers every generated semantic lane exactly once', async () => {
  const input = await contract();
  const first = createFourWorkerMatrixPlan(input, { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  const second = createFourWorkerMatrixPlan(input, { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  assert.equal(first.planSha256, second.planSha256);
  assert.equal(first.shards.length, 4);
  assert.equal(new Set(first.shards.flatMap((shard) => shard.lanes.map((lane) => lane.id))).size, first.lanes.length);
  assert.ok(first.lanes.some((lane) => lane.crash));
  assert.ok(first.lanes.some((lane) => lane.restart));
});

test('CI shard compiles and exclusively retains persistent-worker preflight before native lane allocation', async () => {
  const current = createFourWorkerMatrixPlan(await contract(), { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  const preflight = compileWorkerPersistentPlan(current, 'worker-0', 'correctness-1');
  assert.equal(preflight.source.workerId, 'worker-0');
  assert.equal(preflight.source.measurementId, 'correctness-1');
  const source = await readFile(new URL('../src/run-ci-matrix-shard.mjs', import.meta.url), 'utf8');
  assert.match(source, /const persistentWorkerPlan = compileWorkerPersistentPlan\(plan, options\.worker, options\.measurement\);\n  const root/);
  assert.match(source, /persistent-worker-plan\.json[\s\S]*flag: 'wx'/);
  assert.match(source, /compileAssignedPersistentMatrix\(persistentWorkerPlan, plan/);
  assert.match(source, /run-f0va-persistent-matrix\.mjs/);
  assert.doesNotMatch(source, /run-f0v-matrix\.mjs/);
});

test('merge rejects missing, duplicate, foreign, and semantically incomplete worker evidence', async () => {
  const plan = createFourWorkerMatrixPlan(await contract(), { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  const results = plan.shards.map((shard) => result(plan, shard));
  const merged = mergeFourWorkerMatrix(plan, results);
  assert.equal(merged.lanes.length, plan.lanes.length);
  assert.throws(() => mergeFourWorkerMatrix(plan, results.slice(1)), /exactly four/);
  const duplicate = structuredClone(results); duplicate[1].workerId = duplicate[0].workerId;
  assert.throws(() => mergeFourWorkerMatrix(plan, duplicate), /duplicate|coverage|foreign/);
  const incomplete = structuredClone(results); delete incomplete[0].lanes[0].terminal.schedule;
  assert.throws(() => validateCiShardResult(plan, incomplete[0]), /terminal semantic/);
  const foreign = structuredClone(results); foreign[0].buildIdentitySha256 = hash('c');
  assert.throws(() => mergeFourWorkerMatrix(plan, foreign), /foreign/);
  const mixedMeasurement = structuredClone(results); mixedMeasurement[1].measurementId = 'correctness-2';
  assert.throws(() => mergeFourWorkerMatrix(plan, mixedMeasurement), /mixed measurement/);
  const queued = structuredClone(results); queued[3].startedEpochMillis += CI_MATRIX_MAX_START_SKEW_MILLIS + 1;
  queued[3].finishedEpochMillis += CI_MATRIX_MAX_START_SKEW_MILLIS + 1;
  assert.throws(() => mergeFourWorkerMatrix(plan, queued), /bounded concurrent/);
  const sequential = structuredClone(results); sequential[3].startedEpochMillis = sequential[2].finishedEpochMillis + 1;
  sequential[3].finishedEpochMillis = sequential[3].startedEpochMillis + sequential[3].executionMillis;
  assert.throws(() => mergeFourWorkerMatrix(plan, sequential), /concurrently/);
});

test('serial admission accepts ordered disjoint shard intervals but never labels them parallel', async () => {
  const plan = createFourWorkerMatrixPlan(await contract(), { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  const serial = plan.shards.map((shard, index) => ({ ...result(plan, shard),
    startedEpochMillis: 1_000 + index * 1_000, finishedEpochMillis: 1_500 + index * 1_000 }));
  assert.equal(mergeSequentialFourWorkerMatrix(plan, serial).policy, 'sequential');
  assert.throws(() => mergeFourWorkerMatrix(plan, serial), /concurrently/);
  assert.throws(() => mergeSequentialFourWorkerMatrix(plan, plan.shards.map((shard) => result(plan, shard))), /overlap/);
  const outOfOrder = structuredClone(serial);
  outOfOrder[0].startedEpochMillis = 3_000; outOfOrder[0].finishedEpochMillis = 3_500;
  outOfOrder[1].startedEpochMillis = 1_000; outOfOrder[1].finishedEpochMillis = 1_500;
  assert.throws(() => mergeSequentialFourWorkerMatrix(plan, outOfOrder), /out of order/);
  const source = await readFile(new URL('../src/run-ci-matrix-sequential.mjs', import.meta.url), 'utf8');
  assert.match(source, /mergeSequentialFourWorkerMatrix\(plan, results\)/);
});

test('CI timing gate requires the measured 2.5x ratio and excludes weaker claims', () => {
  assert.throws(() => requireCiMatrixSpeedup({ sequentialMillis: 2_500, parallelMillis: 1_001 }), /below/);
  assert.equal(requireCiMatrixSpeedup({ sequentialMillis: 2_500, parallelMillis: 1_000 }).speedup, 2.5);
  assert.equal(requireCiMatrixMedianSpeedup({ sequentialSamples: [2_400, 2_500, 2_600], parallelSamples: [900, 1_000, 1_100] }).speedup, 2.5);
  assert.throws(() => requireCiMatrixMedianSpeedup({ sequentialSamples: [2_500], parallelSamples: [1_000] }), /exactly three/);
});

test('aggregate rejects altered portable evidence and requires cross-lane relations after JSON round-trip', async () => {
  const plan = createFourWorkerMatrixPlan(await contract(), { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  const results = JSON.parse(JSON.stringify(plan.shards.map((shard) => result(plan, shard))));
  assert.equal(mergeFourWorkerMatrix(plan, results).crossLane.arrivalCheckpoints.first.value <
    mergeFourWorkerMatrix(plan, results).crossLane.arrivalCheckpoints.second.value, true);
  const divergent = structuredClone(results);
  const cold = divergent.flatMap((entry) => entry.lanes).find((lane) => lane.id.endsWith(':neutral_observer_differential:cold'));
  cold.differential[0].values['identity.job'] = 'foreign';
  assert.throws(() => mergeFourWorkerMatrix(plan, divergent), /diverged/);
  const equalArrival = structuredClone(results);
  const arrivals = equalArrival.flatMap((entry) => entry.lanes).filter((lane) => lane.arrival !== undefined);
  arrivals[1].arrival.value = arrivals[0].arrival.value;
  assert.throws(() => mergeFourWorkerMatrix(plan, equalArrival), /distinct retained/);
  const wrongPath = structuredClone(results);
  wrongPath[0].lanes[0].terminal.identity.paths = ['foreign'];
  assert.throws(() => validateCiShardResult(plan, wrongPath[0]), /immutable declaration/);
  for (const key of ['view', 'id']) {
    const wrongTerminal = structuredClone(results);
    wrongTerminal[0].lanes[0].terminal.identity[key] = 'foreign';
    assert.throws(() => validateCiShardResult(plan, wrongTerminal[0]), /immutable declaration/);
  }
  const incomplete = structuredClone(results);
  delete incomplete[0].lanes[0].terminal.identity.values[incomplete[0].lanes[0].terminal.identity.paths[0]];
  assert.throws(() => validateCiShardResult(plan, incomplete[0]), /immutable declaration/);
  const duplicateLane = structuredClone(results);
  duplicateLane[0].lanes.push(structuredClone(duplicateLane[0].lanes[0]));
  assert.throws(() => mergeFourWorkerMatrix(plan, duplicateLane), /coverage/);
  for (const laneSuffix of [':neutral_observer_differential:cold', ':neutral_observer_differential:hot_cold']) {
    const missingHalf = structuredClone(results);
    const owner = missingHalf.find((entry) => entry.lanes.some((lane) => lane.id.endsWith(laneSuffix)));
    owner.lanes = owner.lanes.filter((lane) => !lane.id.endsWith(laneSuffix));
    assert.throws(() => mergeFourWorkerMatrix(plan, missingHalf), /coverage/);
  }
  const differentialOwner = results.find((entry) => entry.lanes.some((lane) => lane.id.endsWith(':neutral_observer_differential:cold')));
  for (const key of ['view', 'id']) {
    const wrongDifferential = structuredClone(differentialOwner);
    wrongDifferential.lanes.find((lane) => lane.id.endsWith(':neutral_observer_differential:cold')).differential[0][key] = 'foreign';
    assert.throws(() => validateCiShardResult(plan, wrongDifferential), /immutable declaration/);
  }
  const nonIntegralArrival = structuredClone(results.find((entry) => entry.lanes.some((lane) => lane.arrival !== undefined)));
  nonIntegralArrival.lanes.find((lane) => lane.arrival !== undefined).arrival.value = 1.5;
  assert.throws(() => validateCiShardResult(plan, nonIntegralArrival), /arrival evidence/);
  const legacySchema = structuredClone(results[0]); legacySchema.schema = 1;
  assert.throws(() => validateCiShardResult(plan, legacySchema), /malformed, foreign, failed or incomplete/);
  const legacyWeakEvidence = structuredClone(results[0]); legacyWeakEvidence.lanes[0].terminal.identity = { exact: true };
  assert.throws(() => validateCiShardResult(plan, legacyWeakEvidence), /immutable declaration/);
});

test('timing report rejects failed, foreign, and incomplete serial evidence', async () => {
  const plan = createFourWorkerMatrixPlan(await contract(), { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  const timing = { schema: CI_MATRIX_SCHEMA, kind: 'frontier-v3-ci-sequential-timing', status: 'ok', host: 'test',
    planSha256: plan.planSha256, buildIdentitySha256: plan.buildIdentitySha256, contractSha256: plan.contractSha256,
    samples: [1, 2, 3].map((index) => ({ measurementId: `correctness-${index}`, sequentialMillis: 3_000, laneCount: plan.lanes.length })) };
  assert.equal(compareTiming(JSON.stringify(timing), plan, [1_000, 1_000, 1_000]).speedup, 3);
  for (const mutate of [
    (value) => { value.status = 'failed'; }, (value) => { value.planSha256 = hash('c'); },
    (value) => { value.buildIdentitySha256 = hash('c'); }, (value) => { value.contractSha256 = hash('c'); },
    (value) => { value.samples[0].laneCount--; }
  ]) {
    const invalid = structuredClone(timing); mutate(invalid);
    assert.throws(() => compareTiming(JSON.stringify(invalid), plan, [1_000, 1_000, 1_000]), /malformed, failed or foreign|malformed/);
  }
});

test('reusable CI worker admission rejects a malformed complete matrix before Gradle', () => {
  const workers = { include: [
    { worker: 'worker-0', port: '25575' }, { worker: 'worker-1', port: '25577' },
    { worker: 'worker-2', port: '25579' }, { worker: 'worker-3', port: '25581' }
  ] };
  const admitted = validateCiCorrectnessAdmission({ measurement: 'correctness-1', workers, worker: 'worker-2', port: '25579' });
  assert.equal(admitted.workers.length, 4);
  assert.throws(() => validateCiCorrectnessAdmission({ measurement: 'correctness-4', workers, worker: 'worker-2', port: '25579' }), /measurement/);
  assert.throws(() => validateCiCorrectnessAdmission({ measurement: 'correctness-1', workers: { include: workers.include.slice(0, 3) }, worker: 'worker-2', port: '25579' }), /exactly four/);
  const duplicateWorker = structuredClone(workers); duplicateWorker.include[3].worker = 'worker-2';
  assert.throws(() => validateCiCorrectnessAdmission({ measurement: 'correctness-1', workers: duplicateWorker, worker: 'worker-2', port: '25579' }), /missing, duplicate or foreign/);
  const duplicatePort = structuredClone(workers); duplicatePort.include[3].port = '25579';
  assert.throws(() => validateCiCorrectnessAdmission({ measurement: 'correctness-1', workers: duplicatePort, worker: 'worker-2', port: '25579' }), /duplicate private ports/);
  const malformed = structuredClone(workers); malformed.include[0].extra = true;
  assert.throws(() => validateCiCorrectnessAdmission({ measurement: 'correctness-1', workers: malformed, worker: 'worker-2', port: '25579' }), /malformed/);
  assert.throws(() => validateCiCorrectnessAdmission({ measurement: 'correctness-1', workers, worker: 'worker-2', port: '25580' }), /does not match/);
});

test('reusable CI worker environment parser fails closed before any build input is consumed', () => {
  const environment = {
    FRONTIER_V3_CI_MEASUREMENT: 'correctness-2',
    FRONTIER_V3_CI_WORKERS_JSON: JSON.stringify({ include: [
      { worker: 'worker-0', port: '25583' }, { worker: 'worker-1', port: '25585' },
      { worker: 'worker-2', port: '25587' }, { worker: 'worker-3', port: '25589' }
    ] }),
    FRONTIER_V3_PILOT_WORKER_ID: 'worker-3', FRONTIER_V3_PILOT_PORT: '25589'
  };
  assert.equal(parseCiWorkerEnvironment(environment).worker, 'worker-3');
  assert.throws(() => parseCiWorkerEnvironment({ ...environment, FRONTIER_V3_CI_WORKERS_JSON: '{' }), /valid JSON/);
  assert.throws(() => parseCiWorkerEnvironment({ ...environment, FRONTIER_V3_CI_WORKERS_JSON: undefined }), /missing/);
});

test('CI merge rejects an incomplete or duplicate three-measurement series', async () => {
  const plan = createFourWorkerMatrixPlan(await contract(), { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  const all = ['correctness-1', 'correctness-2', 'correctness-3'].flatMap((measurementId) =>
    plan.shards.map((shard) => result(plan, shard, measurementId)));
  assert.equal(mergeMeasurements(plan, all).length, 3);
  assert.throws(() => mergeMeasurements(plan, all.slice(1)), /exactly three|needs exactly|exactly four/);
  const duplicate = structuredClone(all); duplicate[4].measurementId = 'correctness-1';
  assert.throws(() => mergeMeasurements(plan, duplicate), /exactly three|needs exactly|exactly four/);
});

test('worker evidence extracts every terminal semantic class from a real declared lane vocabulary', async () => {
  const plan = createFourWorkerMatrixPlan(await contract(), { buildIdentitySha256: hash('a'), contractSha256: hash('b') });
  const lane = plan.lanes[0];
  const diagnostic = { kind: 'process', id: 'job:site-harvest-4-wheat-field-1', identity: { job: 'job', worker: 'resident' },
    claims: { site: 'site', worker: 'resident', lease: null }, conservation: { outputItem: 'wheat', completedCropSlots: 0, pendingCropSlot: -1, totalCropSlots: 64 },
    schedule: { count: 1, entries: [{ kind: 'continuation' }] }, cursor: { index: 4, retainedBody: { x: 1 }, actorBody: { x: 1 } },
    result: { sitePhase: 'HARVESTING', complete: false } };
  const evidence = terminalSemanticsFromManifest(lane, { status: 'ok', diagnostics: [{ observed: { value: diagnostic } }] });
  assert.deepEqual(Object.keys(evidence).sort(), ['claims', 'conservation', 'identity', 'result', 'schedule']);
  assert.equal(evidence.identity.values['identity.job'], 'job');
});

test('CI command parsers are closed to duplicate, unsafe, and incomplete inputs', () => {
  assert.equal(parsePlan(['--prepared=build/id.json', '--output=build/plan.json']).prepared, 'build/id.json');
  assert.throws(() => parsePlan(['--prepared=build/id.json', '--prepared=build/id2.json', '--output=build/plan.json']), /duplicate/);
  assert.equal(parseShard(['--plan=build/plan.json', '--prepared=build/id.json', '--worker=worker-3', '--measurement=correctness-1', '--output=build/out.json']).worker, 'worker-3');
  assert.throws(() => parseShard(['--plan=../plan.json', '--prepared=build/id.json', '--worker=worker-0', '--output=build/out.json']), /safe relative/);
  assert.equal(parseMerge(['--plan=build/plan.json', ...Array.from({ length: 12 }, (_, index) => `--result=build/${index}.json`), '--output=build/merge.json']).results.length, 12);
  assert.equal(parseSequential(['--plan=build/plan.json', '--prepared=build/id.json', '--results-root=build/results', '--samples=3', '--output=build/timing.json']).resultsRoot, 'build/results');
  assert.equal(parsePrepared(['--output=build/id.json']), 'build/id.json');
});

test('native CI runs three ordered four-worker samples with private ports and exact lifecycle identity', async () => {
  const [workflow, sample] = await workflows();
  const measurements = [...workflow.matchAll(/measurement: (correctness-[1-3])\s+workers: >-\s+(\{[^\n]+\})/g)];
  assert.deepEqual(measurements.map((entry) => entry[1]), ['correctness-1', 'correctness-2', 'correctness-3']);
  const assignments = measurements.flatMap((entry) => JSON.parse(entry[2]).include.map((worker) => ({ measurement: entry[1], ...worker })));
  assert.equal(assignments.length, 12);
  for (const measurement of ['correctness-1', 'correctness-2', 'correctness-3']) {
    assert.deepEqual(assignments.filter((entry) => entry.measurement === measurement).map((entry) => entry.worker).sort(),
      ['worker-0', 'worker-1', 'worker-2', 'worker-3']);
  }
  assert.equal(new Set(assignments.map((entry) => entry.port)).size, 12);
  assert.match(workflow, /f0va-correctness-2:[\s\S]*needs: f0va-correctness-1/);
  assert.match(workflow, /f0va-correctness-3:[\s\S]*needs: f0va-correctness-2/);
  assert.match(sample, /max-parallel: 4/);
  assert.match(sample, /matrix: \$\{\{ fromJSON\(inputs\.workers\) \}\}/);
  assert.match(sample, /FRONTIER_V3_CI_WORKERS_JSON: \$\{\{ inputs\.workers \}\}/);
  assert.match(sample, /Admit exact four-worker correctness matrix before Gradle/);
  assert.match(sample, /node tools\/frontier-v3-test-pilot\/src\/validate-ci-worker-sample\.mjs/);
  assert.match(sample, /FRONTIER_V3_PILOT_PORT: \$\{\{ matrix\.port \}\}/);
  assert.doesNotMatch(workflow, /merge-multiple: true/);
  assert.match(workflow, /results\/f0va-correctness-1-worker-0\/worker-0\.json/);
  assert.doesNotThrow(() => requireWorkerIdentity('worker-2', 'worker-2'));
  assert.throws(() => requireWorkerIdentity('worker-1', 'worker-2'), /identity/);
});

test('each isolated native CI job bootstraps its private Gradle home before offline proof', async () => {
  const [workflow, sample] = await workflows();
  const prepare = jobBlock(workflow, 'f0va-prepare', 'f0va-correctness-1');
  const sequential = jobBlock(workflow, 'f0va-sequential-timing', 'f0va-merge');
  const worker = jobBlock(sample, 'native-correctness');
  assertColdHomeBootstrap(prepare, 'frontier-v3-f0va-prepare-gradle', 'node tools/frontier-v3-test-pilot/src/prepare-native-build.mjs');
  assertColdHomeBootstrap(sequential, 'frontier-v3-f0va-timing-gradle', OFFLINE_NATIVE_PREPARATION);
  assertColdHomeBootstrap(worker, 'frontier-v3-f0va-${FRONTIER_V3_CI_MEASUREMENT}-${FRONTIER_V3_PILOT_WORKER_ID}-gradle',
    OFFLINE_NATIVE_PREPARATION, 'node tools/frontier-v3-test-pilot/src/validate-ci-worker-sample.mjs', true);

  assert.throws(() => assertColdHomeBootstrap(prepare.replace(ONLINE_NATIVE_PREPARATION, ''),
    'frontier-v3-f0va-prepare-gradle', 'node tools/frontier-v3-test-pilot/src/prepare-native-build.mjs'), /bootstrap/);
  assert.throws(() => assertColdHomeBootstrap(sequential.replace(ONLINE_NATIVE_PREPARATION, '').replace(OFFLINE_NATIVE_PREPARATION,
    `${OFFLINE_NATIVE_PREPARATION}\n      - run: ${ONLINE_NATIVE_PREPARATION}`), 'frontier-v3-f0va-timing-gradle', OFFLINE_NATIVE_PREPARATION), /precede offline/);
  assert.throws(() => assertColdHomeBootstrap(worker.replace(ONLINE_NATIVE_PREPARATION,
    `${ONLINE_NATIVE_PREPARATION} --offline`), 'frontier-v3-f0va-${FRONTIER_V3_CI_MEASUREMENT}-${FRONTIER_V3_PILOT_WORKER_ID}-gradle',
  OFFLINE_NATIVE_PREPARATION, 'node tools/frontier-v3-test-pilot/src/validate-ci-worker-sample.mjs', true), /must stay online/);
  assert.throws(() => assertColdHomeBootstrap(worker.replace('node tools/frontier-v3-test-pilot/src/validate-ci-worker-sample.mjs',
    `${ONLINE_NATIVE_PREPARATION}\n      - run: node tools/frontier-v3-test-pilot/src/validate-ci-worker-sample.mjs`), 'frontier-v3-f0va-${FRONTIER_V3_CI_MEASUREMENT}-${FRONTIER_V3_PILOT_WORKER_ID}-gradle',
  OFFLINE_NATIVE_PREPARATION, 'node tools/frontier-v3-test-pilot/src/validate-ci-worker-sample.mjs', true), /admit.*before bootstrap/);
  assert.throws(() => assertColdHomeBootstrap(prepare.replace('      - name: Initialize isolated Gradle home\n',
    '      - name: Removed isolated Gradle home\n'), 'frontier-v3-f0va-prepare-gradle',
  'node tools/frontier-v3-test-pilot/src/prepare-native-build.mjs'), /initializer/);
  assert.throws(() => assertColdHomeBootstrap(prepare.replace('      - name: Initialize isolated Gradle home\n',
    '      - name: Deactivated isolated Gradle home\n') + '\n      - name: Initialize isolated Gradle home\n',
  'frontier-v3-f0va-prepare-gradle', 'node tools/frontier-v3-test-pilot/src/prepare-native-build.mjs'), /initializer.*before Gradle/);
  assert.throws(() => assertColdHomeBootstrap(prepare.replace('case "$RUNNER_TEMP" in', 'case "$UNSAFE_HOME" in'),
    'frontier-v3-f0va-prepare-gradle', 'node tools/frontier-v3-test-pilot/src/prepare-native-build.mjs'), /RUNNER_TEMP/);
  assert.throws(() => assertColdHomeBootstrap(prepare.replace('>> "$GITHUB_ENV"', '>> "$UNSAFE_ENV"'),
    'frontier-v3-f0va-prepare-gradle', 'node tools/frontier-v3-test-pilot/src/prepare-native-build.mjs'), /GITHUB_ENV/);
  assert.throws(() => assertColdHomeBootstrap(prepare.replace('    steps:\n',
    '    env:\n      GRADLE_USER_HOME: ${{ runner.temp }}/unsafe\n    steps:\n'),
  'frontier-v3-f0va-prepare-gradle', 'node tools/frontier-v3-test-pilot/src/prepare-native-build.mjs'), /job env/);
  assert.doesNotThrow(() => assertColdHomeBootstrap(prepare + '\n      - name: Legitimate step runner context\n        env:\n          DIAGNOSTIC_TMP: ${{ runner.temp }}',
    'frontier-v3-f0va-prepare-gradle', 'node tools/frontier-v3-test-pilot/src/prepare-native-build.mjs'));
});

test('native launcher helpers never shadow the Node process environment while spawning', async () => {
  const launchers = [
    '../src/prepare-native-build.mjs',
    '../src/run-ci-matrix-shard.mjs',
    '../src/run-ci-matrix-sequential.mjs',
    '../src/run-f0v-matrix.mjs',
    '../src/run-f0v-timing.mjs',
    '../src/run-f0va-independent-matrix.mjs'
  ];
  const sources = await Promise.all(launchers.map((path) => readFile(new URL(path, import.meta.url), 'utf8')));
  for (const source of sources) assert.doesNotMatch(source, /\bconst\s+process\s*=\s*spawn\s*\(/);
});

test('serialized timing invokes each shard under its immutable worker identity', () => {
  assert.deepEqual(workerEnvironment({ FRONTIER_V3_PILOT_WORKER_ID: 'sequential-timing', KEEP: 'value' }, 'worker-2'), {
    FRONTIER_V3_PILOT_WORKER_ID: 'worker-2', KEEP: 'value'
  });
  assert.throws(() => workerEnvironment({}, 'worker-4'), /worker identity/);
});

function result(plan, shard, measurementId = 'correctness-1') {
  const start = 1_000 + Number(shard.workerId.at(-1)) * 10;
  return { schema: CI_MATRIX_SCHEMA, kind: 'frontier-v3-ci-shard-result', status: 'ok', planSha256: plan.planSha256,
    buildIdentitySha256: plan.buildIdentitySha256, contractSha256: plan.contractSha256, workerId: shard.workerId,
    measurementId, executionMillis: 100 + shard.declaredWeight, startedEpochMillis: start, finishedEpochMillis: start + 100 + shard.declaredWeight,
    lanes: shard.lanes.map((lane) => ({ id: lane.id, ...evidenceFromManifest(lane, manifestForLane(lane)) })) };
}

function manifestForLane(lane) {
  const diagnostics = new Map();
  const declarations = [...lane.scenario.f0vTerminalProjections, ...(lane.scenario.f0vDifferential ?? [])];
  for (const projection of declarations) {
    const key = `${projection.view}\u0000${projection.id}`;
    const value = diagnostics.get(key) ?? { kind: projection.view, id: projection.id };
    for (const path of projection.paths) setPath(value, path, declaredValue(lane, path));
    diagnostics.set(key, value);
  }
  const arrival = lane.scenario.f0vArrivalCheckpoint;
  if (arrival !== undefined) {
    const key = `${arrival.view}\u0000${arrival.id}`;
    const value = diagnostics.get(key) ?? { kind: arrival.view, id: arrival.id };
    setPath(value, arrival.path, lane.variant === 'arrival_checkpoint_one' ? 1 : 2);
    diagnostics.set(key, value);
  }
  return { status: 'ok', diagnostics: [...diagnostics.values()].map((value) => ({ observed: { value } })) };
}
function declaredValue(lane, path) { return `${lane.family}:${lane.variant}:${path}`; }
function setPath(target, path, value) {
  const keys = path.split('.'); let cursor = target;
  for (const key of keys.slice(0, -1)) cursor = cursor[key] ??= {};
  cursor[keys.at(-1)] = value;
}

const ONLINE_NATIVE_PREPARATION = './gradlew :pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment --no-daemon';
const OFFLINE_NATIVE_PREPARATION = './gradlew :pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment --offline --no-daemon';

function jobBlock(workflow, job, nextJob = undefined) {
  const start = workflow.indexOf(`  ${job}:\n`);
  assert.notEqual(start, -1, `workflow lacks ${job} job`);
  const end = nextJob === undefined ? workflow.length : workflow.indexOf(`  ${nextJob}:\n`, start + 1);
  assert.notEqual(end, -1, `workflow lacks ${nextJob} job after ${job}`);
  return workflow.slice(start, end);
}

function assertColdHomeBootstrap(block, homeSuffix, firstOfflinePreparation, admission = undefined, workerIdentity = false) {
  const environment = jobEnvironment(block);
  assert.doesNotMatch(environment, /\bGRADLE_USER_HOME\b/, 'isolated Gradle home must not use job env');
  const initializer = block.indexOf('      - name: Initialize isolated Gradle home\n');
  assert.notEqual(initializer, -1, 'native job lacks isolated Gradle-home initializer');
  const bootstrap = block.indexOf(ONLINE_NATIVE_PREPARATION);
  assert.notEqual(bootstrap, -1, 'native job must bootstrap its cold Gradle home');
  assert.ok(initializer < bootstrap, 'isolated Gradle-home initializer must run before Gradle');
  const initializerBlock = block.slice(initializer, bootstrap);
  assert.match(initializerBlock, /shell: bash/);
  assert.match(initializerBlock, /test -n "\$\{RUNNER_TEMP:-\}"/);
  assert.match(initializerBlock, /test -n "\$\{GITHUB_ENV:-\}"/);
  assert.match(initializerBlock, /case "\$RUNNER_TEMP" in/);
  assert.match(initializerBlock, new RegExp(`frontier_v3_gradle_home="\\$RUNNER_TEMP/${escapeRegExp(homeSuffix)}"`));
  assert.match(initializerBlock, /\*\$'\\n'\*\|\*\$'\\r'\*/);
  assert.match(initializerBlock, /printf 'GRADLE_USER_HOME=%s\\n' "\$frontier_v3_gradle_home" >> "\$GITHUB_ENV"/);
  assert.doesNotMatch(initializerBlock, /(?:^|\n)\s*home=/, 'initializer must not repurpose a generic home variable');
  if (workerIdentity) {
    assert.match(initializerBlock, /case "\$\{FRONTIER_V3_CI_MEASUREMENT:-\}" in/);
    assert.match(initializerBlock, /case "\$\{FRONTIER_V3_PILOT_WORKER_ID:-\}" in/);
  }
  const bootstrapLine = block.slice(bootstrap, block.indexOf('\n', bootstrap));
  assert.equal(bootstrapLine, ONLINE_NATIVE_PREPARATION, 'native bootstrap must stay online');
  const offlinePreparation = block.indexOf(firstOfflinePreparation);
  assert.notEqual(offlinePreparation, -1, 'native job lacks its existing offline preparation');
  assert.ok(bootstrap < offlinePreparation, 'native bootstrap must precede offline preparation');
  if (admission !== undefined) {
    const admissionIndex = block.indexOf(admission);
    assert.notEqual(admissionIndex, -1, 'native worker lacks exact matrix admission');
    assert.ok(admissionIndex < bootstrap, 'native worker must admit its matrix before bootstrap');
  }
}

function jobEnvironment(block) {
  const steps = block.indexOf('    steps:\n');
  const environment = block.indexOf('    env:\n');
  return environment === -1 || environment > steps ? '' : block.slice(environment, steps);
}

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
