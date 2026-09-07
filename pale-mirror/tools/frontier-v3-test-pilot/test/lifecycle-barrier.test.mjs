import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, readFile, readdir, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { LifecycleBarrier, LifecycleSignal, awaitLifecycleBarrier, awaitLifecycleSignal, createLifecycleBarrierSession, exactLifecycleClientPid, exactLifecycleCompletedSegment, newLifecycleIdentity, openLifecycleBarrierSession, publishLifecycleBarrier, readLifecycleBarriers } from '../src/lifecycle-barrier.mjs';

const BUILD = 'a'.repeat(64);

test('versioned lifecycle barriers preserve an exact identity and a monotonic normal restart flow', async () => {
  const session = await fresh('normal');
  for (const [barrier, detail] of [
    [LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 41 }],
    [LifecycleBarrier.SERVER_RUN_READY, { serverPid: 42 }],
    [LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { fixtureRevision: 7 }],
    [LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 1 }],
    [LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 1 }],
    [LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: 1 }],
    [LifecycleBarrier.DURABLE_SERVER_SAVE, { revision: 8 }],
    [LifecycleBarrier.GAME_PORT_CLOSED, { port: 25575 }],
    [LifecycleBarrier.RECOVERY_SERVER_READY, { serverPid: 43 }],
    [LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, { clientPid: 41 }],
    [LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 2 }],
    [LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, { assertions: 2 }]
  ]) await publishLifecycleBarrier(session, barrier, detail);
  const events = await readLifecycleBarriers(session);
  assert.equal(events.length, 12);
  assert.equal((await awaitLifecycleBarrier(session, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, 50)).detail.assertions, 2);
  assert.equal((await openLifecycleBarrierSession(session.directory, session.identity)).identity.nonce, session.identity.nonce);
});

test('a clean crash-pilot exit is admissible only after its exact segment completion', () => {
  const completed = { barrier: LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, detail: { segment: 'before_restart' } };
  assert.equal(exactLifecycleCompletedSegment(completed, 'before_restart'), completed);
  assert.throws(() => exactLifecycleCompletedSegment({ ...completed, detail: { segment: 'after_restart' } }, 'before_restart'),
    /foreign, stale or malformed/);
  assert.throws(() => exactLifecycleCompletedSegment({ ...completed, barrier: LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE }, 'before_restart'),
    /foreign, stale or malformed/);
});

test('a fresh generated scenario parent is created without weakening exact session collision rejection', async () => {
  const parent = await mkdtemp(join(tmpdir(), 'pmv3-lifecycle-parent-'));
  const root = join(parent, 'generated', 'scenarios');
  const identity = newLifecycleIdentity({ buildIdentitySha256: BUILD, workerId: 'local-worker',
    runId: '00000000-0000-0000-0000-000000000011', scenarioId: 'f0v-parent', segmentId: 'fresh-parent',
    nonce: '00000000-0000-0000-0000-000000000013', sessionId: '00000000-0000-0000-0000-000000000014' });
  const session = await createLifecycleBarrierSession(root, identity);
  assert.equal((await readFile(join(session.directory, 'identity.json'), 'utf8')).includes(identity.runId), true);
  await assert.rejects(createLifecycleBarrierSession(root, identity), /EEXIST/);
});

test('lifecycle permits server readiness before the prepared persistent client without weakening later order', async () => {
  const session = await fresh('server-first');
  await publishLifecycleBarrier(session, LifecycleBarrier.SERVER_RUN_READY, { serverPid: 11 });
  await publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 12 });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid: 12 });
  await assert.rejects(publishLifecycleBarrier(session, LifecycleBarrier.RECOVERY_SERVER_READY), /out of order/);
});

test('typed abrupt lifecycle is repeatable without inventing normal disconnect or durable save', async () => {
  const session = await fresh('abrupt');
  for (const [barrier, detail] of [
    [LifecycleBarrier.SERVER_RUN_READY, { serverPid: 11 }], [LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 12 }],
    [LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid: 12, segment: 'crash-before' }],
    [LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 1, segment: 'crash-before' }],
    [LifecycleBarrier.EXPECTED_LOSS_ARMED, { segment: 'crash-before' }],
    [LifecycleBarrier.CRASH_CONTROLLER_FIRED, { serverPid: 11, segment: 'crash-before' }],
    [LifecycleBarrier.OWNED_SERVER_EXIT, { serverPid: 11, segment: 'crash-before' }],
    [LifecycleBarrier.CLIENT_EXPECTED_LOSS, { clientPid: 12, segment: 'crash-before' }],
    [LifecycleBarrier.GAME_PORT_CLOSED, { port: 25575, segment: 'crash-before' }],
    [LifecycleBarrier.RECOVERY_SERVER_READY, { serverPid: 13, segment: 'crash-after' }],
    [LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, { clientPid: 12, segment: 'crash-after' }]
  ]) await publishLifecycleBarrier(session, barrier, detail);
  const events = await readLifecycleBarriers(session);
  assert.equal(events.some((event) => event.barrier === LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED), false);
  assert.equal(events.some((event) => event.barrier === LifecycleBarrier.DURABLE_SERVER_SAVE), false);
});

test('a completed ordinary segment may arm only its server-owned release crash sequence', async () => {
  const session = await fresh('release-after-completion');
  for (const [barrier, detail] of [
    [LifecycleBarrier.SERVER_RUN_READY, { serverPid: 11 }], [LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 12, segment: 'before_restart' }],
    [LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid: 12, segment: 'before_restart' }],
    [LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 4, segment: 'before_restart' }],
    [LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 'before_restart' }],
    [LifecycleBarrier.EXPECTED_LOSS_ARMED, { clientPid: 12, serverPid: 11, segment: 'before_restart' }],
    [LifecycleBarrier.CRASH_CONTROLLER_FIRED, { serverPid: 11, segment: 'before_restart' }],
    [LifecycleBarrier.OWNED_SERVER_EXIT, { serverPid: 11, segment: 'before_restart' }],
    [LifecycleBarrier.CLIENT_EXPECTED_LOSS, { clientPid: 12, segment: 'before_restart' }],
    [LifecycleBarrier.GAME_PORT_CLOSED, { port: 25575, serverPid: 11, segment: 'before_restart' }]
  ]) await publishLifecycleBarrier(session, barrier, detail);
  await assert.rejects(publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED,
    { segment: 'before_restart' }), /out of order/);
});

test('independent supervisor processes serialize complete lifecycle publications before assigning sequence IDs', async () => {
  const session = await fresh('concurrent-supervisors');
  await Promise.all([
    publishLifecycleBarrier(session, LifecycleBarrier.SERVER_RUN_READY, { serverPid: 11 }),
    publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 12 })
  ]);
  const events = await readLifecycleBarriers(session);
  assert.equal(events.length, 2);
  assert.deepEqual(new Set(events.map((event) => event.barrier)), new Set([
    LifecycleBarrier.SERVER_RUN_READY, LifecycleBarrier.PREPARED_CLIENT_READY
  ]));
  assert.equal((await readdir(join(session.directory, 'staging'))).length, 0);
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid: 12 });
  assert.equal((await readLifecycleBarriers(session)).at(-1).sequence, 3);
});

test('lifecycle permits one completion per restart segment but rejects duplicate one-shot barriers', async () => {
  const session = await fresh('two-segments');
  await publishLifecycleBarrier(session, LifecycleBarrier.SERVER_RUN_READY, { serverPid: 11 });
  await publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 12 });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid: 12 });
  await publishLifecycleBarrier(session, LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 1 });
  await publishLifecycleBarrier(session, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: 'one' });
  await publishLifecycleBarrier(session, LifecycleBarrier.GAME_PORT_CLOSED, { port: 25575 });
  await publishLifecycleBarrier(session, LifecycleBarrier.RECOVERY_SERVER_READY, { serverRunId: 'two' });
  await publishLifecycleBarrier(session, LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, { clientPid: 12 });
  await publishLifecycleBarrier(session, LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 2 });
  await publishLifecycleBarrier(session, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 'after_restart' });
  await assert.rejects(publishLifecycleBarrier(session, LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: 'one' }), /duplicate/);
});

test('a replacement client has its own exact recovery segment while a same-segment duplicate still fails closed', async () => {
  const session = await fresh('replacement-client');
  await publishLifecycleBarrier(session, LifecycleBarrier.SERVER_RUN_READY, { serverRunId: 'one', segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 12, segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid: 12, segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 1, segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: 'one', segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.GAME_PORT_CLOSED, { port: 25575, serverRunId: 'one', segment: 'before_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.RECOVERY_SERVER_READY, { serverRunId: 'two', segment: 'after_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 13, segment: 'after_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid: 13, segment: 'after_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 1, segment: 'after_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 'after_restart' });
  await publishLifecycleBarrier(session, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, { assertions: 2 });
  await assert.rejects(publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY,
    { clientPid: 13, segment: 'after_restart' }), /duplicate/);
  assert.equal((await readLifecycleBarriers(session)).filter((entry) => entry.barrier === LifecycleBarrier.PREPARED_CLIENT_READY).length, 2);
});

test('final matrix terminal assertion follows its supervisor-directed normal disconnect', async () => {
  const session = await fresh('final-normal-disconnect');
  await publishLifecycleBarrier(session, LifecycleBarrier.SERVER_RUN_READY, { serverPid: 11 });
  await publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 12 });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid: 12 });
  await publishLifecycleBarrier(session, LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: 1 });
  await publishLifecycleBarrier(session, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 'final' });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: 'final' });
  await publishLifecycleBarrier(session, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, { assertions: 1 });
  assert.equal((await readLifecycleBarriers(session)).at(-1).barrier, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE);
});

test('persistent matrix admits distinct lifecycle cycles but rejects a repeated exact acknowledgement', async () => {
  const session = await fresh('matrix-cycles');
  await publishLifecycleBarrier(session, LifecycleBarrier.SERVER_RUN_READY, { serverRunId: 'server-0' });
  await publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: 12 });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { segment: 'alpha' });
  await publishLifecycleBarrier(session, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 'alpha' });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: 'alpha' });
  await publishLifecycleBarrier(session, LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: 'server-0' });
  await publishLifecycleBarrier(session, LifecycleBarrier.GAME_PORT_CLOSED, { port: 25575, serverRunId: 'server-0' });
  await publishLifecycleBarrier(session, LifecycleBarrier.RECOVERY_SERVER_READY, { serverRunId: 'server-1' });
  await publishLifecycleBarrier(session, LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, { clientPid: 12, segment: 'restart-before' });
  await publishLifecycleBarrier(session, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: 'restart-before' });
  await publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: 'restart-before' });
  await publishLifecycleBarrier(session, LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: 'server-1' });
  await publishLifecycleBarrier(session, LifecycleBarrier.GAME_PORT_CLOSED, { port: 25575, serverRunId: 'server-1' });
  await publishLifecycleBarrier(session, LifecycleBarrier.RECOVERY_SERVER_READY, { serverRunId: 'server-2' });
  await assert.rejects(publishLifecycleBarrier(session, LifecycleBarrier.RECOVERY_SERVER_READY, { serverRunId: 'server-2' }), /duplicate/);
});

test('lifecycle barriers fail closed on foreign, stale, duplicate and out-of-order acknowledgements', async () => {
  const session = await fresh('negative');
  await assert.rejects(publishLifecycleBarrier(session, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY), /cannot start/);
  await publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY);
  await assert.rejects(publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY), /duplicate/);
  await assert.rejects(openLifecycleBarrierSession(session.directory, { ...session.identity, nonce: '00000000-0000-0000-0000-000000000001' }), /foreign or stale/);
  const eventPath = join(session.directory, 'events', '0002-server_run_ready.json');
  await writeFile(eventPath, JSON.stringify({ schema: 1, sequence: 2, barrier: LifecycleBarrier.SERVER_RUN_READY,
    identity: { ...session.identity, runId: '00000000-0000-0000-0000-000000000002' }, detail: {} }));
  await assert.rejects(readLifecycleBarriers(session), /foreign or stale/);
  assert.equal((await readFile(join(session.directory, 'identity.json'), 'utf8')).includes(session.identity.runId), true);
});

test('lifecycle barrier waits time out as an attributable missing acknowledgement', async () => {
  const session = await fresh('timeout');
  await publishLifecycleBarrier(session, LifecycleBarrier.PREPARED_CLIENT_READY);
  await assert.rejects(awaitLifecycleBarrier(session, LifecycleBarrier.SERVER_RUN_READY, 25), /was not acknowledged/);
});

test('a child-exit cancellation releases an outstanding lifecycle watcher immediately', async () => {
  const session = await fresh('cancelled-signal');
  const controller = new AbortController();
  const pending = awaitLifecycleSignal(session, LifecycleSignal.CLIENT_CONNECTED_FIXTURE_READY, 'cancelled', 300_000, controller.signal);
  controller.abort(17);
  await assert.rejects(pending, /was cancelled/);
});

test('pilot signals bind exact immutable lifecycle identity and reject stale or malformed evidence', async () => {
  const session = await fresh('signals');
  const signal = LifecycleSignal.CLIENT_CONNECTED_FIXTURE_READY;
  const suffix = 'before_restart';
  const path = join(session.directory, 'signals', `${signal}-${suffix}.json`);
  await writeFile(path, JSON.stringify({ schema: 1, signal, suffix, identity: session.identity, detail: { playerPid: 12 } }));
  const observed = await awaitLifecycleSignal(session, signal, suffix, 50);
  assert.equal(observed.detail.playerPid, 12);

  const stale = await fresh('stale-signals');
  await writeFile(join(stale.directory, 'signals', `${signal}-${suffix}.json`), JSON.stringify({
    schema: 1, signal, suffix, identity: session.identity, detail: {}
  }));
  await assert.rejects(awaitLifecycleSignal(stale, signal, suffix, 50), /foreign or stale/);
  await assert.rejects(awaitLifecycleSignal(session, signal, '../escape', 50), /suffix/);
});

test('expected-loss Java signal envelopes retain the exact lifecycle identity', async () => {
  const session = await fresh('expected-crash');
  for (const [signal, suffix, detail] of [
    [LifecycleSignal.EXPECTED_LOSS_ARMED, 'crash_before', {}],
    [LifecycleSignal.CLIENT_EXPECTED_LOSS, 'crash_before', { epoch: 2, clientPid: 1234 }]
  ]) {
    await writeFile(join(session.directory, 'signals', `${signal}-${suffix}.json`), JSON.stringify({
      schema: 1, signal, suffix, identity: session.identity, detail
    }));
    assert.equal((await awaitLifecycleSignal(session, signal, suffix, 50)).signal, signal);
  }
});

test('expected-loss cleanup accepts only the direct client JVM from its exact prepared segment', () => {
  const prepared = { barrier: LifecycleBarrier.PREPARED_CLIENT_READY,
    detail: { clientPid: 4321, segment: 'before_restart' } };
  assert.equal(exactLifecycleClientPid(prepared, 'before_restart'), 4321);
  assert.throws(() => exactLifecycleClientPid({ ...prepared, detail: { clientPid: 4321, segment: 'after_restart' } }, 'before_restart'), /foreign, stale or malformed/);
  assert.throws(() => exactLifecycleClientPid({ ...prepared, detail: { clientPid: 1, segment: 'before_restart' } }, 'before_restart'), /foreign, stale or malformed/);
  assert.throws(() => exactLifecycleClientPid({ ...prepared, barrier: LifecycleBarrier.SERVER_RUN_READY }, 'before_restart'), /foreign, stale or malformed/);
});

async function fresh(segmentId) {
  const root = await mkdtemp(join(tmpdir(), 'pmv3-lifecycle-'));
  return await createLifecycleBarrierSession(root, newLifecycleIdentity({ buildIdentitySha256: BUILD, workerId: 'local-worker',
    runId: '00000000-0000-0000-0000-000000000001', scenarioId: 'f0v-smoke', segmentId, nonce: '00000000-0000-0000-0000-000000000003',
    sessionId: '00000000-0000-0000-0000-000000000004' }));
}
