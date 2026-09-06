import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import test from 'node:test';
import { LifecycleBarrier } from '../src/lifecycle-barrier.mjs';
import { admitAssignedPersistentPlan, assignedRunnerInvocation, awaitExpectedCrashBoundary, executeExpectedCrashProtocol, orchestratePersistentSegments, requireAssignedPreparedIdentity, validateExpectedCrashPrefix, validatePersistentClientAssertions } from '../src/run-f0va-persistent-matrix.mjs';
import { createFourWorkerMatrixPlan } from '../src/ci-matrix.mjs';
import { compileAssignedPersistentMatrix, compilePersistentWorkerPlan } from '../src/persistent-worker-plan.mjs';
import { sha256Json, validatePersistentMatrixEvidence } from '../src/persistent-matrix.mjs';
import { createPersistentExpectedCrashArm, createPersistentExpectedCrashRelease } from '../src/persistent-crash.mjs';
import { readFile } from 'node:fs/promises';

test('assigned runner admission retains every immutable segment for one CLI-owned client lifecycle', () => {
  const plan = { kind: 'frontier-v3-assigned-persistent-matrix', workerId: 'worker-3', segments: [
    { id: 'crash-before', laneId: 'crash', worldKey: 'world-a' }, { id: 'crash-after', laneId: 'crash', worldKey: 'world-a' },
    { id: 'graceful-before', laneId: 'graceful', worldKey: 'world-b' }
  ] };
  const invocation = assignedRunnerInvocation(plan, { artifact: 'prepared' });
  assert.deepEqual(invocation.lanes, ['crash', 'graceful']); assert.deepEqual(invocation.segments, ['crash-before', 'crash-after', 'graceful-before']); assert.deepEqual(invocation.worldKeys, ['world-a', 'world-b']);
  assert.throws(() => assignedRunnerInvocation({ ...plan, segments: [...plan.segments, plan.segments[0]] }, {}), /duplicate/);
});

test('crash prefix requires only reached pre-half assertions and never promotes unreached evidence', () => {
  const scenario = { actions: [{ type: 'visit' }, { type: 'inspect' }], assertions: [
    { after: 1, view: 'process', id: 'job', expect: { status: 'ok' } },
    { after: 2, view: 'process', id: 'job', expect: { terminal: true } }
  ] };
  const manifest = { diagnostics: [{ observed: { value: { pilotActionStep: 1, kind: 'process', id: 'job', status: 'ok' } } }] };
  assert.deepEqual(validateExpectedCrashPrefix(scenario, 1, manifest), { completedActionSteps: 1, unreachedAssertions: 1 });
  assert.throws(() => validateExpectedCrashPrefix(scenario, 2, manifest), /reached assertion/);
});

test('CLI-consumed orchestration keeps one client through two injected crash releases', async () => {
  const hash = 'a'.repeat(64); const crash = (laneId) => ({ laneId, boundary: 'lease_recorded_before_physical_materialization', owner: 'job:one', payloadType: 'frontier.one', expectedRevision: 1 });
  const plan = { schema: 1, kind: 'frontier-v3-assigned-persistent-matrix', workerId: 'worker-0', buildIdentitySha256: hash,
    source: { workerId: 'worker-0', buildIdentitySha256: hash }, workerPlanSha256: hash, contentSha256: hash, workerPlan: {},
    segments: [
      { id: 'c0', laneId: 'lane0', scenarioId: 's0', scenarioSha256: hash, originalScenarioId: 'o0', originalScenarioSha256: hash, originalActionOffset: 0, worldKey: 'world0', actionCount: 1, completion: 'expected_crash', expectedCrash: crash('lane0'), final: false },
      { id: 'r0', laneId: 'lane0', scenarioId: 's1', scenarioSha256: hash, originalScenarioId: 'o0', originalScenarioSha256: hash, originalActionOffset: 1, worldKey: 'world0', actionCount: 1, completion: 'recovered_terminal', final: false },
      { id: 'c1', laneId: 'lane1', scenarioId: 's2', scenarioSha256: hash, originalScenarioId: 'o1', originalScenarioSha256: hash, originalActionOffset: 0, worldKey: 'world1', actionCount: 1, completion: 'expected_crash', expectedCrash: crash('lane1'), final: false },
      { id: 'r1', laneId: 'lane1', scenarioId: 's3', scenarioSha256: hash, originalScenarioId: 'o1', originalScenarioSha256: hash, originalActionOffset: 1, worldKey: 'world1', actionCount: 1, completion: 'recovered_terminal', final: true }
    ] };
  const source = plan.segments.map((segment) => ({ id: segment.id })); const effects = []; let clients = 0;
  await orchestratePersistentSegments(plan, source, {
    startServer: async (segment) => ({ id: segment.id }), startClient: async () => { clients++; },
    releaseCrash: async (proof, active) => effects.push(`release:${proof.id}:${active.id}`), resume: async (epoch) => effects.push(`resume:${epoch}`),
    expected: async (segment) => ({ id: segment.id }), terminal: async (segment) => effects.push(`terminal:${segment.id}`)
  });
  assert.equal(clients, 1); assert.deepEqual(effects, ['release:c0:r0', 'resume:1', 'terminal:r0', 'resume:2', 'release:c1:r1', 'resume:3', 'terminal:r1']);
});

test('CLI admission reconstructs all four real compiler assignments before orchestration', async () => {
  const contract = JSON.parse(await readFile(new URL('../contracts/resource-site-harvest-f0v.json', import.meta.url), 'utf8'));
  const source = createFourWorkerMatrixPlan(contract, { buildIdentitySha256: 'a'.repeat(64), contractSha256: 'b'.repeat(64) });
  for (const workerId of ['worker-0', 'worker-1', 'worker-2', 'worker-3']) {
    const worker = compilePersistentWorkerPlan(source, workerId, 'correctness-1');
    const assigned = compileAssignedPersistentMatrix(worker, source, { workerId, measurementId: 'correctness-1' });
    assert.equal(admitAssignedPersistentPlan(assigned, { plan: source }, 'correctness-1').workerId, workerId);
    const altered = structuredClone(assigned); altered.segments[0].originalActionOffset++;
    assert.throws(() => admitAssignedPersistentPlan(altered, { plan: source }, 'correctness-1'), /foreign|drifted/);
  }
});

test('compiled worker3 split lanes retain mandatory original terminal evidence at the real consumer', async () => {
  const evidence = await compiledEvidence('worker-3');
  assert.deepEqual(evidence.assigned.workerPlan.lanes.map((lane) => lane.segments.map((segment) => segment.originalActionOffset)), [[0, 4], [0, 3]]);
  assert.doesNotThrow(() => validatePersistentClientAssertions(evidence.source, evidence.clientManifest, evidence.assigned.workerPlan.lanes));
});

test('compiled assigned lanes retain COLD progress and reject missing, inconsistent, foreign, or incomplete original evidence', async () => {
  const cold = await compiledEvidence('worker-2');
  assert.doesNotThrow(() => validatePersistentClientAssertions(cold.source, cold.clientManifest, cold.assigned.workerPlan.lanes));
  const coldSource = cold.source.find((segment) => segment.loaded.scenario.f0vColdProgress !== undefined);
  assert.ok(coldSource, 'compiled worker-2 is expected to retain the COLD lane');

  const missing = structuredClone(cold); const missingCold = missing.source.find((segment) => segment.id === coldSource.id);
  delete missingCold.loaded.scenario.f0vTerminalProjections;
  assert.throws(() => validatePersistentClientAssertions(missing.source, missing.clientManifest, missing.assigned.workerPlan.lanes), /projections.*foreign|inconsistent/);

  const inconsistent = structuredClone(cold); const inconsistentCold = inconsistent.source.find((segment) => segment.id === coldSource.id);
  inconsistentCold.loaded.scenario.f0vColdProgress.minimumAdvance++;
  assert.throws(() => validatePersistentClientAssertions(inconsistent.source, inconsistent.clientManifest, inconsistent.assigned.workerPlan.lanes), /projections.*foreign|inconsistent/);

  const foreign = structuredClone(cold); foreign.source[0].assigned.laneId = 'foreign-lane';
  assert.throws(() => validatePersistentClientAssertions(foreign.source, foreign.clientManifest, foreign.assigned.workerPlan.lanes), /foreign/);

  const incomplete = structuredClone(cold); incomplete.clientManifest.segments.pop();
  assert.throws(() => validatePersistentClientAssertions(incomplete.source, incomplete.clientManifest, incomplete.assigned.workerPlan.lanes), /incomplete|missing/);
});

test('compiled split final readers reject missing or false terminal observations and insufficient COLD advance', async () => {
  const split = await compiledEvidence('worker-3');
  const recovered = split.source.find((segment) => segment.assigned.completion === 'recovered_terminal');
  const recoveredReport = split.clientManifest.segments.find((segment) => segment.id === recovered.id);
  const terminal = recoveredReport.diagnostics.find((entry) => entry.actionStep === recovered.loaded.scenario.actions.length);

  const missingTerminal = structuredClone(split); const missingReport = missingTerminal.clientManifest.segments.find((segment) => segment.id === recovered.id);
  const missingValue = { ...structuredClone(terminal.value), pilotActionStep: 0 }; delete missingValue.result.complete;
  missingReport.diagnostics.push({ actionStep: 0, value: missingValue });
  assert.throws(() => validatePersistentClientAssertions(missingTerminal.source, missingTerminal.clientManifest, missingTerminal.assigned.workerPlan.lanes), /terminal assertion is absent or false/);

  const falseTerminal = structuredClone(split); const falseReport = falseTerminal.clientManifest.segments.find((segment) => segment.id === recovered.id);
  falseReport.diagnostics.push({ actionStep: 0, value: { ...structuredClone(terminal.value), pilotActionStep: 0,
    result: { ...terminal.value.result, complete: true } } });
  assert.throws(() => validatePersistentClientAssertions(falseTerminal.source, falseTerminal.clientManifest, falseTerminal.assigned.workerPlan.lanes), /terminal assertion is absent or false/);

  const cold = await compiledEvidence('worker-2'); const coldSource = cold.source.find((segment) => segment.loaded.scenario.f0vColdProgress !== undefined);
  const coldReport = cold.clientManifest.segments.find((segment) => segment.id === coldSource.id);
  const coldAfter = coldReport.diagnostics.find((entry) => entry.actionStep === coldSource.loaded.scenario.f0vColdProgress.after);
  coldAfter.value.cursor.index = 1;
  assert.throws(() => validatePersistentClientAssertions(cold.source, cold.clientManifest, cold.assigned.workerPlan.lanes), /COLD progress cursor did not advance/);
});

test('compiled split original membership rejects offset drift, duplicate membership, and cross-lane segments', async () => {
  const evidence = await compiledEvidence('worker-3');
  const wrongOffset = structuredClone(evidence); wrongOffset.source[1].assigned.originalActionOffset++;
  assert.throws(() => validatePersistentClientAssertions(wrongOffset.source, wrongOffset.clientManifest, wrongOffset.assigned.workerPlan.lanes), /foreign|inconsistent/);

  const duplicate = structuredClone(evidence); duplicate.source.push(structuredClone(duplicate.source[0]));
  duplicate.clientManifest.segments.push(structuredClone(duplicate.clientManifest.segments[0]));
  assert.throws(() => validatePersistentClientAssertions(duplicate.source, duplicate.clientManifest, duplicate.assigned.workerPlan.lanes), /incomplete|foreign/);

  const crossLane = structuredClone(evidence); crossLane.source[0].assigned.laneId = crossLane.assigned.workerPlan.lanes[1].id;
  assert.throws(() => validatePersistentClientAssertions(crossLane.source, crossLane.clientManifest, crossLane.assigned.workerPlan.lanes), /incomplete|foreign/);
});

test('genuine worker3 composition counts every authenticated runtime assertion while retaining only terminal result records', async () => {
  const composition = await compiledWorker3FinalEvidence();
  assert.equal(composition.source.length, 4);
  assert.equal(composition.evidence.results.length, 3);
  assert.equal(composition.evidence.crashReceipts.length, 1);
  assert.doesNotThrow(() => validatePersistentClientAssertions(composition.source, composition.clientManifest, composition.assigned.workerPlan.lanes));
  assert.equal(validatePersistentMatrixEvidence(composition.runtimePlan, composition.evidence).segmentCount, 4);

  for (const count of [3, 5]) {
    const invalid = structuredClone(composition.evidence);
    invalid.events.at(-1).detail.assertionCount = count;
    assert.throws(() => validatePersistentMatrixEvidence(composition.runtimePlan, invalid), /diverges/);
  }
  const missing = structuredClone(composition.evidence); missing.results.pop();
  assert.throws(() => validatePersistentMatrixEvidence(composition.runtimePlan, missing), /incomplete/);
  const duplicate = structuredClone(composition.evidence); duplicate.results[1] = structuredClone(duplicate.results[0]);
  assert.throws(() => validatePersistentMatrixEvidence(composition.runtimePlan, duplicate), /duplicate|missing or stale/);
  const foreign = structuredClone(composition.evidence); foreign.results[0].segment = 'foreign_result';
  assert.throws(() => validatePersistentMatrixEvidence(composition.runtimePlan, foreign), /missing or stale/);
  const absentReceipt = structuredClone(composition.evidence); absentReceipt.crashReceipts = [];
  assert.throws(() => validatePersistentMatrixEvidence(composition.runtimePlan, absentReceipt), /receipt coverage/);
  const badProof = structuredClone(composition.evidence); badProof.crashReceipts[0].proof.ownedExit.serverPid++;
  assert.throws(() => validatePersistentMatrixEvidence(composition.runtimePlan, badProof), /proof is incomplete or foreign/);
  const badRelease = structuredClone(composition.evidence); badRelease.crashReceipts[0].release.successor.serverRunId = 'foreign_successor';
  assert.throws(() => validatePersistentMatrixEvidence(composition.runtimePlan, badRelease), /foreign or stale/);
  const falseTerminal = structuredClone(composition.clientManifest);
  const recovered = composition.source.find((segment) => segment.assigned.completion === 'recovered_terminal');
  const report = falseTerminal.segments.find((segment) => segment.id === recovered.id);
  const terminal = report.diagnostics.find((entry) => entry.actionStep === recovered.loaded.scenario.actions.length);
  terminal.value.result.complete = true;
  assert.throws(() => validatePersistentClientAssertions(composition.source, falseTerminal, composition.assigned.workerPlan.lanes), /assertion is absent or false/);
});

test('local single-lane evidence retains the existing terminal validator form', () => {
  const scenario = { id: 'local', actions: [{ type: 'inspect' }], assertions: [
    { after: 1, view: 'process', id: 'job:local', expect: { status: 'ok', result: { complete: false } } }
  ], f0vTerminalProjections: [{ invariant: 'result', view: 'process', id: 'job:local', paths: ['result.complete'] }] };
  const source = [{ id: 'local-segment', assigned: { completion: 'recovered_terminal' }, loaded: { scenario } }];
  const manifest = { segments: [{ id: 'local-segment', diagnostics: [{ actionStep: 1,
    value: { pilotActionStep: 1, kind: 'process', id: 'job:local', status: 'ok', result: { complete: false } } }] }] };
  assert.doesNotThrow(() => validatePersistentClientAssertions(source, manifest));
});

test('assigned CLI preflight has no prepared-build fallback', () => {
  assert.equal(requireAssignedPreparedIdentity('build/worker/prepared.json'), 'build/worker/prepared.json');
  assert.throws(() => requireAssignedPreparedIdentity(''), /requires/);
  assert.throws(() => requireAssignedPreparedIdentity(undefined), /requires/);
});

test('CLI-consumed abrupt protocol orders arm ACK, exact receipt, full loss identity and release inputs for every boundary', async () => {
  const boundaries = ['lease_recorded_before_physical_materialization', 'physical_effect_visible_before_typed_observation', 'typed_observation_durable_before_next_process_checkpoint', 'hot_checkpoint_durable_before_drain_release', 'release_durable_before_cold_resumption'];
  for (const boundary of boundaries) {
    const trace = []; const active = { serverPid: 33, serverRunId: `run-${boundary}` }; const segment = { id: `segment-${boundary}` };
    const proof = await executeExpectedCrashProtocol({ active, segment, clientPid: 44, port: 25575,
      arm: async () => trace.push('arm'), acknowledgeArm: async () => trace.push('ack'), fire: async () => { trace.push('fire'); return { runId: active.serverRunId, boundary, serverPid: 33 }; },
      awaitExit: async () => trace.push('exit'), awaitLoss: async () => ({ identity: { runId: 'r', nonce: 'n', sessionId: 's', workerId: 'w', buildIdentitySha256: 'b' }, detail: { clientPid: 44, epoch: 0 }, suffix: segment.id }),
      barrier: async (barrier) => trace.push(barrier) });
    assert.deepEqual(trace.slice(0, 3), ['arm', 'ack', 'expected_loss_armed']); assert.equal(proof.clientLoss.segment, segment.id); assert.equal(proof.portClosed.closed, true);
  }
});

test('consumed crash probe closes its output and child subscriptions on output, exit, fatal and cancellation', async () => {
  const declaration = { boundary: 'lease_recorded_before_physical_materialization', owner: 'job:one', payloadType: 'frontier.one', expectedRevision: 7 };
  const child = Object.assign(new EventEmitter(), { exitCode: null, signalCode: null });
  const boundary = 'PMV3_CRASH_BOUNDARY runId=run-probe boundary=lease_recorded_before_physical_materialization owner=job:one revision=7 payload=frontier.one';
  let closed = 0; const active = { child, segment: 'probe', serverRunId: 'run-probe', output: () => boundary, outputRevision: () => 1,
    outputAfter: () => ({ wait: Promise.resolve(), close: () => { closed++; } }), fatal: () => null };
  assert.equal((await awaitExpectedCrashBoundary(active, declaration, 50, undefined, child)).revision, 7);
  assert.equal(child.listenerCount('exit'), 0); assert.equal(closed, 0);
  let wake; const waiting = { ...active, output: () => '', outputAfter: () => ({ wait: new Promise((resolveWait) => { wake = resolveWait; }), close: () => { closed++; } }) };
  const cancelled = new AbortController(); const pending = awaitExpectedCrashBoundary(waiting, declaration, 1000, cancelled.signal, child);
  cancelled.abort('test cancellation'); await assert.rejects(pending, /cancelled/); assert.equal(child.listenerCount('exit'), 0); assert.equal(closed, 1); wake();
  const fatal = { ...waiting, fatal: () => new Error('server fatal') };
  await assert.rejects(awaitExpectedCrashBoundary(fatal, declaration, 50, undefined, child), /server fatal/); assert.equal(child.listenerCount('exit'), 0);
  const server = Object.assign(new EventEmitter(), { exitCode: null, signalCode: null }); const client = Object.assign(new EventEmitter(), { exitCode: null, signalCode: null });
  const serverPending = awaitExpectedCrashBoundary({ ...waiting, child: server }, declaration, 1000, undefined, client);
  server.emit('exit', 9); await assert.rejects(serverPending, /server exited/); assert.equal(server.listenerCount('exit'), 0); assert.equal(client.listenerCount('exit'), 0);
  const clientPending = awaitExpectedCrashBoundary({ ...waiting, child: server }, declaration, 1000, undefined, client);
  client.emit('exit', 10); await assert.rejects(clientPending, /client exited/); assert.equal(server.listenerCount('exit'), 0); assert.equal(client.listenerCount('exit'), 0);
  const timeoutServer = Object.assign(new EventEmitter(), { exitCode: null, signalCode: null }); const timeoutClient = Object.assign(new EventEmitter(), { exitCode: null, signalCode: null });
  await assert.rejects(awaitExpectedCrashBoundary({ ...waiting, child: timeoutServer }, declaration, 1, undefined, timeoutClient), /did not announce/);
  assert.equal(timeoutServer.listenerCount('exit'), 0); assert.equal(timeoutClient.listenerCount('exit'), 0); assert.equal(closed >= 4, true);
});

test('Gradle preparation admits only decimal or the runner late-bound crash revision token', async () => {
  const build = await readFile(new URL('../../../pale-mirror-neoforge/build.gradle', import.meta.url), 'utf8');
  const predicate = build.match(/frontierV3PilotCrashRevision ==~ \/([^/]+)\//)?.[1];
  assert.ok(predicate, 'Gradle crash revision predicate is absent');
  const admitted = new RegExp(`^(?:${predicate})$`);
  assert.equal(admitted.test('17'), true);
  assert.equal(admitted.test('observed_at_boundary'), true);
  for (const invalid of ['', '-1', '+1', ' 17', '17 ', 'arbitrary_symbolic_revision']) assert.equal(admitted.test(invalid), false, invalid);
  assert.match(build, /crashValues\.any \{ !it\.isEmpty\(\) \}/);
});

async function compiledEvidence(workerId) {
  const contract = JSON.parse(await readFile(new URL('../contracts/resource-site-harvest-f0v.json', import.meta.url), 'utf8'));
  const matrix = createFourWorkerMatrixPlan(contract, { buildIdentitySha256: 'a'.repeat(64), contractSha256: 'b'.repeat(64) });
  const worker = compilePersistentWorkerPlan(matrix, workerId, 'correctness-1');
  const assigned = compileAssignedPersistentMatrix(worker, matrix, { workerId, measurementId: 'correctness-1' });
  const source = assigned.segments.map((segment) => ({ id: segment.id, assigned: segment, loaded: { scenario: segment.scenario } }));
  return { assigned, source, clientManifest: { segments: source.map((segment) => ({ id: segment.id,
    ...(segment.assigned.completion === 'expected_crash' ? { completedActionSteps: segment.loaded.scenario.actions.length } : {}),
    diagnostics: segment.loaded.scenario.assertions.map((assertion) => ({ actionStep: assertion.after,
      value: diagnosticValue(segment.loaded.scenario, assertion) }))
  })) } };
}

async function compiledWorker3FinalEvidence() {
  const { assigned, source, clientManifest } = await compiledEvidence('worker-3');
  const core = { schema: 1, kind: 'frontier-v3-assigned-persistent-matrix', workerId: assigned.workerId,
    buildIdentitySha256: assigned.buildIdentitySha256, source: assigned.source, workerPlanSha256: assigned.workerPlanSha256,
    compiledContentSha256: assigned.contentSha256, segments: assigned.segments.map((segment, epoch) => ({
      id: segment.id, laneId: segment.laneId, scenarioId: segment.scenarioId, scenarioSha256: segment.scenarioSha256,
      compiledScenarioSha256: segment.scenarioSha256, originalScenarioId: segment.originalScenarioId,
      originalScenarioSha256: segment.originalScenarioSha256, originalActionOffset: segment.originalActionOffset,
      worldKey: segment.worldKey, actionCount: segment.actionCount, completion: segment.completion,
      ...(segment.expectedCrash === undefined ? {} : { expectedCrash: structuredClone(segment.expectedCrash) }),
      final: epoch === assigned.segments.length - 1
    })) };
  const runtimePlan = { ...core, contentSha256: sha256Json(core) };
  const clientPid = 5001; const port = 25575;
  const serverRuns = runtimePlan.segments.map((segment, epoch) => ({ segment: segment.id, worldKey: segment.worldKey,
    serverRunId: `server-${epoch}`, serverPid: 6000 + epoch,
    ...(segment.completion === 'expected_crash' ? { completedActionSteps: segment.actionCount } : {}) }));
  const crashed = runtimePlan.segments[0]; const successor = runtimePlan.segments[1]; const crashedRun = serverRuns[0];
  const crashIdentity = { buildIdentitySha256: runtimePlan.buildIdentitySha256, workerId: runtimePlan.workerId,
    runId: '11111111-1111-4111-8111-111111111111', nonce: '22222222-2222-4222-8222-222222222222', sessionId: '33333333-3333-4333-8333-333333333333' };
  const descriptor = { completion: 'expected_crash', segment: crashed.id, scenarioId: crashed.scenarioId,
    scenarioSha256: crashed.scenarioSha256, worldKey: crashed.worldKey, laneId: crashed.laneId,
    boundary: crashed.expectedCrash.boundary, owner: crashed.expectedCrash.owner, payloadType: crashed.expectedCrash.payloadType,
    expectedRevision: crashed.expectedCrash.expectedRevision, resolvedRevision: 17 };
  const arm = createPersistentExpectedCrashArm({ identity: crashIdentity, epoch: 0, descriptor, serverRunId: crashedRun.serverRunId,
    serverPid: crashedRun.serverPid, clientPid, port, resolvedRevision: 17 });
  const proof = { fired: { runId: crashedRun.serverRunId, boundary: descriptor.boundary, owner: descriptor.owner,
    payloadType: descriptor.payloadType, serverPid: crashedRun.serverPid, revision: 17 },
  ownedExit: { serverPid: crashedRun.serverPid, serverRunId: crashedRun.serverRunId, exited: true },
  clientLoss: { ...crashIdentity, clientPid, epoch: 0, segment: crashed.id, completedActionSteps: crashedRun.completedActionSteps },
  portClosed: { serverPid: crashedRun.serverPid, serverRunId: crashedRun.serverRunId, port, closed: true } };
  const release = createPersistentExpectedCrashRelease({ arm, proof, successor: { epoch: 1, segment: successor.id,
    scenarioSha256: successor.scenarioSha256, worldKey: successor.worldKey, serverRunId: serverRuns[1].serverRunId,
    serverPid: serverRuns[1].serverPid, ready: true } });
  const events = producerLifecycle(runtimePlan, serverRuns, clientPid, port);
  const results = runtimePlan.segments.flatMap((segment, epoch) => segment.completion === 'expected_crash' ? [] : [{ epoch,
    segment: segment.id, scenarioSha256: segment.scenarioSha256, status: 'ok', terminal: { assertionCount: 1 } }]);
  return { assigned, source, clientManifest, runtimePlan, evidence: { clientPid, port, serverRuns, results,
    crashReceipts: [{ epoch: 0, arm, proof, release }], events } };
}

function producerLifecycle(plan, serverRuns, clientPid, port) {
  const events = [
    { barrier: LifecycleBarrier.SERVER_RUN_READY, detail: { serverRunId: serverRuns[0].serverRunId, serverPid: serverRuns[0].serverPid, segment: plan.segments[0].id } },
    { barrier: LifecycleBarrier.PREPARED_CLIENT_READY, detail: { clientPid } },
    { barrier: LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, detail: { clientPid, segment: plan.segments[0].id } }
  ];
  for (const [epoch, segment] of plan.segments.entries()) {
    const run = serverRuns[epoch]; const completed = segment.completion === 'expected_crash' ? run.completedActionSteps : segment.actionCount;
    for (let step = 1; step <= completed; step++) events.push({ barrier: LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, detail: { actionStep: step, segment: segment.id } });
    if (segment.completion === 'expected_crash') {
      const next = serverRuns[epoch + 1]; const nextSegment = plan.segments[epoch + 1];
      events.push({ barrier: LifecycleBarrier.EXPECTED_LOSS_ARMED, detail: { segment: segment.id } },
        { barrier: LifecycleBarrier.CRASH_CONTROLLER_FIRED, detail: { serverPid: run.serverPid, segment: segment.id } },
        { barrier: LifecycleBarrier.OWNED_SERVER_EXIT, detail: { serverPid: run.serverPid, segment: segment.id } },
        { barrier: LifecycleBarrier.CLIENT_EXPECTED_LOSS, detail: { clientPid, segment: segment.id } },
        { barrier: LifecycleBarrier.GAME_PORT_CLOSED, detail: { port, serverRunId: run.serverRunId, segment: segment.id } },
        { barrier: LifecycleBarrier.RECOVERY_SERVER_READY, detail: { serverRunId: next.serverRunId, serverPid: next.serverPid, segment: nextSegment.id } },
        { barrier: LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, detail: { clientPid, segment: nextSegment.id } });
      continue;
    }
    events.push({ barrier: LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, detail: { segment: segment.id } });
    if (segment.final) {
      events.push({ barrier: LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, detail: { segment: segment.id } });
    } else {
      const next = serverRuns[epoch + 1]; const nextSegment = plan.segments[epoch + 1];
      events.push({ barrier: LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, detail: { segment: segment.id } },
        { barrier: LifecycleBarrier.DURABLE_SERVER_SAVE, detail: { serverRunId: run.serverRunId, segment: segment.id } },
        { barrier: LifecycleBarrier.GAME_PORT_CLOSED, detail: { port, serverRunId: run.serverRunId, segment: segment.id } },
        { barrier: LifecycleBarrier.RECOVERY_SERVER_READY, detail: { serverRunId: next.serverRunId, serverPid: next.serverPid, segment: nextSegment.id } },
        { barrier: LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, detail: { clientPid, segment: nextSegment.id } });
    }
  }
  events.push({ barrier: LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, detail: { assertionCount: plan.segments.length } });
  return events;
}

function diagnosticValue(scenario, assertion) {
  const value = { pilotActionStep: assertion.after, kind: assertion.view, id: assertion.id, ...structuredClone(assertion.expect) };
  const cold = scenario.f0vColdProgress;
  if (cold !== undefined && assertion.view === cold.view && assertion.id === cold.id
      && (assertion.after === cold.beforeAction || assertion.after === cold.after)) {
    const body = { dimension: 'pale_mirror:frontier_graybox', x: 388, y: 64, z: -355 };
    value.claims = { ...value.claims, lease: null };
    value.cursor = { index: assertion.after === cold.beforeAction ? 1 : 2, retainedBody: body, actorBody: structuredClone(body) };
  }
  return value;
}
