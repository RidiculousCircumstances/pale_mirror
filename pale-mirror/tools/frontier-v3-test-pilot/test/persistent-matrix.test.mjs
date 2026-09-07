import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import test from 'node:test';
import { LifecycleBarrier, LifecycleSignal, awaitLifecycleSignal, createLifecycleBarrierSession, newLifecycleIdentity } from '../src/lifecycle-barrier.mjs';
import { createCrashController } from '../src/crash-controller.mjs';
import { awaitPersistentMatrixFinalClose, createPersistentMatrixSession, matrixSegment, publishPersistentMatrixExpectedCrashArm, publishPersistentMatrixExpectedCrashRelease, publishPersistentMatrixFinalClose, publishPersistentMatrixResult, publishPersistentMatrixResume, publishPersistentMatrixServerReady, readPersistentMatrixResult, sha256Json, validatePersistentMatrixEvidence, validatePersistentMatrixPlan } from '../src/persistent-matrix.mjs';
import { createPersistentExpectedCrashArm, createPersistentExpectedCrashRelease } from '../src/persistent-crash.mjs';

const hash = (value) => sha256Json({ value });
const identity = () => Object.freeze({ workerId: 'worker_0', buildIdentitySha256: hash('build'), runId: randomUUID() });
const plan = () => Object.freeze({ schema: 1, kind: 'frontier-v3-persistent-matrix', workerId: 'worker_0', buildIdentitySha256: hash('build'), segments: [
  { id: 'smoke_alpha', scenarioId: 'smoke_alpha', scenarioSha256: hash('a'), worldKey: 'world_alpha', actionCount: 1, final: false },
  { id: 'restart_before', scenarioId: 'restart', scenarioSha256: hash('b'), worldKey: 'world_restart', actionCount: 1, final: false },
  { id: 'restart_after', scenarioId: 'restart', scenarioSha256: hash('c'), worldKey: 'world_restart', actionCount: 1, final: false },
  { id: 'smoke_beta', scenarioId: 'smoke_beta', scenarioSha256: hash('d'), worldKey: 'world_beta', actionCount: 1, final: true }
] });

async function fresh(name) {
  const directory = join('/tmp', `pmv3-persistent-matrix-${name}-${randomUUID()}`);
  const session = await createPersistentMatrixSession(directory, identity(), plan());
  return { directory, session };
}

test('persistent matrix binds immutable segments to one lifecycle identity and append-only resume tokens', async (context) => {
  const { directory, session } = await fresh('normal'); context.after(() => rm(directory, { recursive: true, force: true }));
  const resume = await publishPersistentMatrixResume(session, 1);
  assert.equal(resume.token, `${session.identity.runId}:1`);
  const descriptor = JSON.parse(await readFile(join(session.directory, 'segments', '0000.json'), 'utf8'));
  assert.equal(descriptor.scenarioId, 'smoke_alpha');
  await assert.rejects(publishPersistentMatrixResume(session, 1), /already exists/);
  await assert.rejects(publishPersistentMatrixResume(session, 0), /epoch is invalid/);
});

test('persistent matrix fails closed on foreign plans, insufficient independent worlds and stale terminal results', async (context) => {
  const directory = join('/tmp', `pmv3-persistent-matrix-negative-${randomUUID()}`); context.after(() => rm(directory, { recursive: true, force: true }));
  const invalid = { ...plan(), segments: plan().segments.map((segment) => ({ ...segment, worldKey: 'same' })) };
  assert.throws(() => validatePersistentMatrixPlan(invalid), /three independent/);
  await assert.rejects(createPersistentMatrixSession(directory, { ...identity(), workerId: 'other' }, plan()), /foreign/);
  const { session } = await fresh('stale');
  await writeFile(join(session.directory, 'results', '0000.json'), JSON.stringify({ schema: 1, kind: 'frontier-v3-pilot-matrix-result',
    runId: randomUUID(), epoch: 0, segment: 'smoke_alpha', scenarioSha256: hash('a'), status: 'ok', terminal: { assertionCount: 1 } }));
  await assert.rejects(readPersistentMatrixResult(session, 0), /foreign or stale/);
  await rm(session.directory, { recursive: true, force: true });
});

test('persistent matrix atomically commits one complete terminal record without exposing its partial staging file', async (context) => {
  const { directory, session } = await fresh('atomic-result'); context.after(() => rm(directory, { recursive: true, force: true }));
  const partial = join(session.directory, 'results', '0000.json.synthetic.partial');
  await writeFile(partial, '{"schema":1');
  await assert.rejects(readPersistentMatrixResult(session, 0), (error) => error?.code === 'ENOENT');
  const segment = session.plan.segments[0];
  const published = await publishPersistentMatrixResult(session, 0, { schema: 1, kind: 'frontier-v3-pilot-matrix-result',
    runId: session.identity.runId, epoch: 0, segment: segment.id, scenarioSha256: segment.scenarioSha256,
    status: 'ok', terminal: { assertionCount: 1, diagnosticCount: 2 } });
  assert.deepEqual(await readPersistentMatrixResult(session, 0), published);
  await assert.rejects(publishPersistentMatrixResult(session, 0, published), /already exists/);
});

test('persistent matrix takes terminality only from its validated immutable plan', () => {
  const checked = plan();
  assert.equal(matrixSegment(checked, 0).final, false);
  assert.equal(matrixSegment(checked, 3).final, true);
  assert.throws(() => matrixSegment(checked, 4), /epoch/);
});

test('crash publication binds its active ready server and release before resume', async (context) => {
  const directory = join('/tmp', `pmv3-persistent-crash-${randomUUID()}`); context.after(() => rm(directory, { recursive: true, force: true }));
  const lifecycle = { ...identity(), nonce: randomUUID(), sessionId: randomUUID() };
  const crashPlan = { ...plan(), segments: [
    { id: 'smoke_alpha', scenarioId: 'smoke_alpha', scenarioSha256: hash('a'), worldKey: 'world_alpha', actionCount: 1, final: false },
    { id: 'crash_before', scenarioId: 'crash', scenarioSha256: hash('crash-before'), worldKey: 'world_restart', actionCount: 1, final: false, completion: 'expected_crash', expectedCrash: { laneId: 'lane_a', boundary: 'hot_checkpoint_durable_before_drain_release', owner: 'job:harvest-1', payloadType: 'frontier.resource_site_harvest_hot_traversal_advanced', expectedRevision: 'observed_at_boundary', expectedAuthorityEpoch: 3 } },
    { id: 'crash_after', scenarioId: 'crash', scenarioSha256: hash('crash-after'), worldKey: 'world_restart', actionCount: 1, final: false, completion: 'recovered_terminal' },
    { id: 'smoke_beta', scenarioId: 'smoke_beta', scenarioSha256: hash('d'), worldKey: 'world_beta', actionCount: 1, final: true }
  ] };
  const session = await createPersistentMatrixSession(directory, lifecycle, crashPlan);
  const signalSession = await createLifecycleBarrierSession(directory, newLifecycleIdentity({
    buildIdentitySha256: lifecycle.buildIdentitySha256, workerId: lifecycle.workerId, runId: lifecycle.runId,
    scenarioId: 'crash', segmentId: 'crash_before', nonce: lifecycle.nonce, sessionId: lifecycle.sessionId
  }));
  const crashedServerRunId = randomUUID();
  await publishPersistentMatrixServerReady(session, 1, { serverRunId: crashedServerRunId, serverPid: 2200, port: 25575, worldKey: 'world_restart', ready: true });
  const runtime = { serverRunId: crashedServerRunId, serverPid: 2200, clientPid: 3300, port: 25575, resolvedRevision: 17, authorityEpoch: 3 };
  const arm = await publishPersistentMatrixExpectedCrashArm(session, 1, runtime);
  await assert.rejects(publishPersistentMatrixExpectedCrashArm(session, 1, { ...runtime, serverPid: 2201 }), /readiness/);
  await publishPersistentMatrixServerReady(session, 2, { serverRunId: 'server_1', serverPid: 2201, port: 25575, worldKey: 'world_restart', ready: true });
  const kills = [];
  const fired = createCrashController({ runId: crashedServerRunId, boundary: arm.descriptor.boundary, owner: arm.descriptor.owner,
    payloadType: arm.descriptor.payloadType, expectedRevision: 17, expectedAuthorityEpoch: 3, serverPid: 2200 }).fire({
    runId: crashedServerRunId, boundary: arm.descriptor.boundary, owner: arm.descriptor.owner, payloadType: arm.descriptor.payloadType,
    revision: 17, authorityEpoch: 3 }, (...args) => kills.push(args));
  assert.deepEqual(kills, [[2200, 'SIGKILL']]);
  const signalPath = join(signalSession.directory, 'signals', `${LifecycleSignal.CLIENT_EXPECTED_LOSS}-crash_before.json`);
  await writeFile(signalPath, JSON.stringify({ schema: 1, signal: LifecycleSignal.CLIENT_EXPECTED_LOSS, suffix: 'crash_before',
    identity: signalSession.identity, detail: { epoch: 1, clientPid: 3300 } }));
  const lossSignal = await awaitLifecycleSignal(signalSession, LifecycleSignal.CLIENT_EXPECTED_LOSS, 'crash_before', 50);
  const proof = { fired, ownedExit: { serverPid: 2200, serverRunId: crashedServerRunId, exited: true }, clientLoss: {
    clientPid: lossSignal.detail.clientPid, epoch: lossSignal.detail.epoch, segment: 'crash_before', runId: lossSignal.identity.runId,
    nonce: lossSignal.identity.nonce, sessionId: lossSignal.identity.sessionId, workerId: lossSignal.identity.workerId,
    buildIdentitySha256: lossSignal.identity.buildIdentitySha256
  }, portClosed: { serverPid: 2200, serverRunId: crashedServerRunId, port: 25575, closed: true } };
  await assert.rejects(publishPersistentMatrixResume(session, 2), /release/);
  const release = await publishPersistentMatrixExpectedCrashRelease(session, 1, proof, { segment: 'crash_after', scenarioSha256: hash('crash-after'), worldKey: 'world_restart', serverRunId: 'server_1', serverPid: 2201 });
  const releasePath = join(session.directory, 'recovery', '0002.json'); const tamperedRelease = structuredClone(release); tamperedRelease.armSha256 = hash('foreign-arm');
  await writeFile(releasePath, JSON.stringify(tamperedRelease));
  await assert.rejects(publishPersistentMatrixResume(session, 2), /foreign|stale/);
  await writeFile(releasePath, JSON.stringify(release));
  assert.equal((await publishPersistentMatrixResume(session, 2)).epoch, 2);
});

test('persistent matrix releases only its validated final segment after supervisor verification', async (context) => {
  const { directory, session } = await fresh('final-close'); context.after(() => rm(directory, { recursive: true, force: true }));
  await assert.rejects(publishPersistentMatrixFinalClose(session, 2), /epoch is invalid/);
  const close = await publishPersistentMatrixFinalClose(session, 3);
  assert.equal(close.token, `${session.identity.runId}:3`);
  assert.equal((await awaitPersistentMatrixFinalClose(session, 3, 50)).token, close.token);
  await assert.rejects(publishPersistentMatrixFinalClose(session, 3), /already exists/);
  const foreign = await fresh('foreign-final-close'); context.after(() => rm(foreign.directory, { recursive: true, force: true }));
  await writeFile(join(foreign.session.directory, 'close', '0003.token'), 'foreign\n', { flag: 'wx' });
  await assert.rejects(awaitPersistentMatrixFinalClose(foreign.session, 3, 50), /foreign or malformed/);
});

test('persistent matrix requires one exact client through every declared server lifecycle', () => {
  const checked = validatePersistentMatrixPlan(plan());
  const clientPid = 1001;
  const serverRuns = checked.segments.map((segment, index) => ({ segment: segment.id, worldKey: segment.worldKey,
    serverRunId: `server-${index}`, serverPid: 2000 + index }));
  const events = [
    { barrier: LifecycleBarrier.SERVER_RUN_READY, detail: { serverRunId: 'server-0', serverPid: 2000, segment: 'smoke_alpha' } },
    { barrier: LifecycleBarrier.PREPARED_CLIENT_READY, detail: { clientPid } },
    { barrier: LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, detail: { clientPid, segment: 'smoke_alpha' } }
  ];
  for (const [index, segment] of checked.segments.entries()) {
    events.push({ barrier: LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, detail: { actionStep: 1, segment: segment.id } });
    events.push({ barrier: LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, detail: { segment: segment.id } });
    if (!segment.final) {
      events.push({ barrier: LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, detail: { segment: segment.id } });
      events.push({ barrier: LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, detail: { serverRunId: `server-${index}`, segment: segment.id } });
      events.push({ barrier: LifecycleBarrier.DURABLE_SERVER_SAVE, detail: { serverRunId: `server-${index}`, segment: segment.id } });
      events.push({ barrier: LifecycleBarrier.GAME_PORT_CLOSED, detail: { port: 25575, serverRunId: `server-${index}`, segment: segment.id } });
      events.push({ barrier: LifecycleBarrier.RECOVERY_SERVER_READY, detail: { serverRunId: `server-${index + 1}`, serverPid: 2001 + index, segment: checked.segments[index + 1].id } });
      events.push({ barrier: LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, detail: { clientPid, segment: checked.segments[index + 1].id } });
    } else {
      events.push({ barrier: LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, detail: { segment: segment.id } });
      events.push({ barrier: LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, detail: { serverRunId: `server-${index}`, segment: segment.id } });
    }
  }
  events.push({ barrier: LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, detail: { assertionCount: checked.segments.length } });
  const results = checked.segments.map((segment, epoch) => ({ status: 'ok', epoch, segment: segment.id,
    scenarioSha256: segment.scenarioSha256, terminal: { assertionCount: 1 } }));
  assert.equal(validatePersistentMatrixEvidence(checked, { clientPid, port: 25575, events, results, serverRuns, crashReceipts: [] }).segmentCount, 4);
  const missing = { clientPid, port: 25575, events: events.slice(0, -1), results, serverRuns, crashReceipts: [] };
  assert.throws(() => validatePersistentMatrixEvidence(checked, missing), /coverage/);
  const foreignClient = structuredClone({ clientPid, port: 25575, events, results, serverRuns, crashReceipts: [] });
  foreignClient.events[9].detail.clientPid = 1002;
  assert.throws(() => validatePersistentMatrixEvidence(checked, foreignClient), /diverges/);
});

test('a declared compatible case retains one exact server only through its reset fence', () => {
  const checked = structuredClone(plan()); checked.segments[1].worldKey = 'world_alpha'; checked.segments[1].reuseServer = true;
  const clientPid = 1001; const serverRuns = checked.segments.map((segment, index) => ({ segment: segment.id, worldKey: segment.worldKey,
    serverRunId: index < 2 ? 'server-0' : `server-${index - 1}`, serverPid: index < 2 ? 2000 : 1999 + index }));
  const events = [
    { barrier: LifecycleBarrier.SERVER_RUN_READY, detail: { serverRunId: 'server-0', serverPid: 2000, segment: 'smoke_alpha' } },
    { barrier: LifecycleBarrier.PREPARED_CLIENT_READY, detail: { clientPid } },
    { barrier: LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, detail: { clientPid, segment: 'smoke_alpha' } },
    ...Array.from({ length: checked.segments[0].actionCount }, (_, action) =>
      ({ barrier: LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, detail: { actionStep: action + 1, segment: 'smoke_alpha' } })),
    { barrier: LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, detail: { segment: 'smoke_alpha' } },
    { barrier: LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, detail: { segment: 'smoke_alpha' } },
    { barrier: LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, detail: { serverRunId: 'server-0', segment: 'smoke_alpha' } },
    { barrier: LifecycleBarrier.SAME_SERVER_RESET_ACKNOWLEDGED, detail: { serverRunId: 'server-0', segment: 'restart_before' } },
    { barrier: LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, detail: { clientPid, segment: 'restart_before' } }
  ];
  for (const [index, segment] of checked.segments.slice(1).entries()) {
    const epoch = index + 1; const run = serverRuns[epoch];
    events.push(...Array.from({ length: segment.actionCount }, (_, action) =>
      ({ barrier: LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, detail: { actionStep: action + 1, segment: segment.id } })),
      { barrier: LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, detail: { segment: segment.id } },
      { barrier: LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, detail: { segment: segment.id } },
      { barrier: LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, detail: { serverRunId: run.serverRunId, segment: segment.id } });
    if (!segment.final) {
      const next = serverRuns[epoch + 1]; const nextSegment = checked.segments[epoch + 1];
      events.push({ barrier: LifecycleBarrier.DURABLE_SERVER_SAVE, detail: { serverRunId: run.serverRunId, segment: segment.id } },
        { barrier: LifecycleBarrier.GAME_PORT_CLOSED, detail: { port: 25575, serverRunId: run.serverRunId, segment: segment.id } },
        { barrier: LifecycleBarrier.RECOVERY_SERVER_READY, detail: { serverRunId: next.serverRunId, serverPid: next.serverPid, segment: nextSegment.id } },
        { barrier: LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, detail: { clientPid, segment: nextSegment.id } });
    }
  }
  events.push({ barrier: LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, detail: { assertionCount: checked.segments.length } });
  const results = checked.segments.map((segment, epoch) => ({ status: 'ok', epoch, segment: segment.id, scenarioSha256: segment.scenarioSha256, terminal: { assertionCount: 1 } }));
  assert.doesNotThrow(() => validatePersistentMatrixEvidence(checked, { clientPid, port: 25575, events, results, serverRuns, crashReceipts: [] }));
  const divergent = structuredClone(serverRuns); divergent[1].serverPid++;
  assert.throws(() => validatePersistentMatrixEvidence(checked, { clientPid, port: 25575, events, results, serverRuns: divergent, crashReceipts: [] }), /compatible case/);
  const undeclared = structuredClone(checked); delete undeclared.segments[1].reuseServer;
  assert.throws(() => validatePersistentMatrixEvidence(undeclared, { clientPid, port: 25575, events, results, serverRuns, crashReceipts: [] }), /outside a declared/);
});

test('final evidence binds all five plan-declared crash receipts including sequential crashes', () => {
  const boundaries = ['lease_recorded_before_physical_materialization', 'physical_effect_visible_before_typed_observation', 'typed_observation_durable_before_next_process_checkpoint', 'hot_checkpoint_durable_before_drain_release', 'release_durable_before_cold_resumption'];
  const crashIdentity = { workerId: 'worker_0', buildIdentitySha256: hash('receipt-build'), runId: randomUUID(), nonce: randomUUID(), sessionId: randomUUID() };
  const clientPid = 3300; const port = 25575;
  const segments = boundaries.flatMap((boundary, index) => [
    { id: `crash_${index}`, scenarioId: `crash_${index}`, scenarioSha256: hash(`crash-${index}`), worldKey: `world_${index % 3}`, actionCount: 1, completion: 'expected_crash', expectedCrash: { laneId: `lane_${index}`, boundary, owner: `job:${index}`, payloadType: 'frontier.one', expectedRevision: 17 }, final: false },
    { id: `recovered_${index}`, scenarioId: `recovered_${index}`, scenarioSha256: hash(`recovered-${index}`), worldKey: `world_${index % 3}`, actionCount: 1, completion: 'recovered_terminal', final: index === boundaries.length - 1 }
  ]);
  const matrix = { schema: 1, kind: 'frontier-v3-persistent-matrix', workerId: crashIdentity.workerId, buildIdentitySha256: crashIdentity.buildIdentitySha256, segments };
  const serverRuns = segments.map((segment, epoch) => ({ segment: segment.id, worldKey: segment.worldKey, serverRunId: `run_${epoch}`, serverPid: 4100 + epoch, ...(segment.completion === 'expected_crash' ? { completedActionSteps: 0 } : {}) }));
  const receipt = (epoch, laneId = segments[epoch].expectedCrash.laneId) => {
    const segment = segments[epoch]; const run = serverRuns[epoch]; const next = serverRuns[epoch + 1]; const successor = segments[epoch + 1];
    const descriptor = { completion: 'expected_crash', segment: segment.id, scenarioId: segment.scenarioId, scenarioSha256: segment.scenarioSha256, worldKey: segment.worldKey,
      laneId, boundary: segment.expectedCrash.boundary, owner: segment.expectedCrash.owner, payloadType: segment.expectedCrash.payloadType, expectedRevision: 17, resolvedRevision: 17 };
    const arm = createPersistentExpectedCrashArm({ identity: crashIdentity, epoch, descriptor, serverRunId: run.serverRunId, serverPid: run.serverPid, clientPid, port, resolvedRevision: 17 });
    const proof = { fired: { runId: run.serverRunId, boundary: descriptor.boundary, owner: descriptor.owner, payloadType: descriptor.payloadType, serverPid: run.serverPid, revision: 17 },
      ownedExit: { serverPid: run.serverPid, serverRunId: run.serverRunId, exited: true }, clientLoss: { clientPid, epoch, segment: segment.id, ...crashIdentity, completedActionSteps: 0 },
      portClosed: { serverPid: run.serverPid, serverRunId: run.serverRunId, port, closed: true } };
    return { epoch, arm, proof, release: createPersistentExpectedCrashRelease({ arm, proof, successor: { serverRunId: next.serverRunId, serverPid: next.serverPid, worldKey: successor.worldKey, segment: successor.id, scenarioSha256: successor.scenarioSha256, epoch: epoch + 1, ready: true } }) };
  };
  const crashReceipts = boundaries.map((_, index) => receipt(index * 2)); const events = [
    { barrier: LifecycleBarrier.SERVER_RUN_READY, detail: { serverRunId: 'run_0', serverPid: 4100, segment: 'crash_0' } },
    { barrier: LifecycleBarrier.PREPARED_CLIENT_READY, detail: { clientPid } }, { barrier: LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, detail: { clientPid, segment: 'crash_0' } }
  ];
  for (const [epoch, segment] of segments.entries()) {
    const run = serverRuns[epoch];
    if (segment.completion === 'expected_crash') {
      events.push({ barrier: LifecycleBarrier.EXPECTED_LOSS_ARMED, detail: { segment: segment.id } }, { barrier: LifecycleBarrier.CRASH_CONTROLLER_FIRED, detail: { serverPid: run.serverPid, segment: segment.id } },
        { barrier: LifecycleBarrier.OWNED_SERVER_EXIT, detail: { serverPid: run.serverPid, segment: segment.id } }, { barrier: LifecycleBarrier.CLIENT_EXPECTED_LOSS, detail: { clientPid, segment: segment.id } },
        { barrier: LifecycleBarrier.GAME_PORT_CLOSED, detail: { port, serverRunId: run.serverRunId, segment: segment.id } });
    } else {
      events.push({ barrier: LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, detail: { actionStep: 1, segment: segment.id } }, { barrier: LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, detail: { segment: segment.id } });
      if (!segment.final) events.push({ barrier: LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, detail: { segment: segment.id } }, { barrier: LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, detail: { serverRunId: run.serverRunId, segment: segment.id } }, { barrier: LifecycleBarrier.DURABLE_SERVER_SAVE, detail: { serverRunId: run.serverRunId, segment: segment.id } }, { barrier: LifecycleBarrier.GAME_PORT_CLOSED, detail: { port, serverRunId: run.serverRunId, segment: segment.id } });
      else events.push({ barrier: LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, detail: { segment: segment.id } }, { barrier: LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, detail: { serverRunId: run.serverRunId, segment: segment.id } });
    }
    if (!segment.final) { const next = serverRuns[epoch + 1]; const nextSegment = segments[epoch + 1]; events.push({ barrier: LifecycleBarrier.RECOVERY_SERVER_READY, detail: { serverRunId: next.serverRunId, serverPid: next.serverPid, segment: nextSegment.id } }, { barrier: LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, detail: { clientPid, segment: nextSegment.id } }); }
  }
  events.push({ barrier: LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, detail: { assertionCount: segments.length } });
  const evidence = { clientPid, port, events, serverRuns, crashReceipts, results: segments.filter((segment) => segment.completion !== 'expected_crash').map((segment, epoch) => ({ epoch: epoch * 2 + 1, segment: segment.id, scenarioSha256: segment.scenarioSha256, status: 'ok', terminal: { assertionCount: 1 } })) };
  assert.equal(validatePersistentMatrixEvidence(matrix, evidence).crashReceiptCount, 5);
  const foreign = structuredClone(evidence); foreign.crashReceipts[0] = receipt(0, 'foreign_lane'); assert.throws(() => validatePersistentMatrixEvidence(matrix, foreign), /receipt is foreign/);
  const missing = structuredClone(evidence); missing.crashReceipts.pop(); assert.throws(() => validatePersistentMatrixEvidence(matrix, missing), /receipt coverage/);
  const badPrefix = structuredClone(evidence); badPrefix.serverRuns[0].completedActionSteps = 1; assert.throws(() => validatePersistentMatrixEvidence(matrix, badPrefix), /prefix/);
  const badRelease = structuredClone(evidence); delete badRelease.crashReceipts[0].release; assert.throws(() => validatePersistentMatrixEvidence(matrix, badRelease), /receipt is foreign/);
  const badSuccessor = structuredClone(evidence); badSuccessor.crashReceipts[0].release.successor.serverRunId = 'foreign_successor';
  assert.throws(() => validatePersistentMatrixEvidence(matrix, badSuccessor), /foreign or stale/);
});
