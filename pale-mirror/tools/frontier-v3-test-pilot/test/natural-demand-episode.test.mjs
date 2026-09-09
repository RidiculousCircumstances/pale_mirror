import assert from 'node:assert/strict';
import { watch } from 'node:fs';
import { createServer } from 'node:net';
import test, { after } from 'node:test';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { armNaturalDemandEpisode, awaitNaturalDemandEpisodeArmed, NATURAL_DEMAND_STOP_COMMAND, requestPilotNaturalDemandStop, runPilotWithNaturalDemandArm, stopPilotNaturalDemandCarrier } from '../src/natural-demand-episode.mjs';
import { createLifecycleBarrierSession, newLifecycleIdentity } from '../src/lifecycle-barrier.mjs';
import { decodeRconFrames, encodeRconFrame } from '../src/rcon.mjs';

const BUILD = 'a'.repeat(64);
const SERVER = Object.freeze({ serverRunId: '00000000-0000-0000-0000-000000000021', serverPid: 12345 });
const roots = [];

after(async () => { await Promise.all(roots.map(root => rm(root, { recursive: true, force: true }))); });

test('the actual carrier never invokes client work before the exact server arm acknowledgement', async () => {
  const lifecycle = await fresh('arm-positive');
  let clients = 0;
  const armWritten = awaitArmWritten(lifecycle);
  const pending = runPilotWithNaturalDemandArm({ lifecycle, server: SERVER, timeoutMs: 1_000, runPilot: async () => { clients += 1; } });
  const arm = await armWritten;
  assert.equal(clients, 0);
  await writeArmAck(lifecycle, arm);
  const returned = await pending;
  assert.equal(returned.stopAdmissionNonce, arm.stopAdmissionNonce);
  assert.equal(clients, 1);
});

test('missing, stale, foreign, and malformed arm acknowledgements fail before client work', async () => {
  for (const [name, mutate] of [
    ['missing', null],
    ['stale', value => { value.identity = { ...value.identity, nonce: '00000000-0000-0000-0000-000000000099' }; }],
    ['foreign', value => { value.server.serverRunId = '00000000-0000-0000-0000-000000000099'; }],
    ['malformed', value => { delete value.stopAdmissionNonce; }]
  ]) {
    const lifecycle = await fresh(`arm-${name}`);
    let clients = 0;
    const armWritten = awaitArmWritten(lifecycle);
    const pending = runPilotWithNaturalDemandArm({ lifecycle, server: SERVER, timeoutMs: 20,
      runPilot: async () => { clients += 1; } });
    const arm = await armWritten;
    if (mutate !== null) {
      const value = armAck(lifecycle, arm); mutate(value);
      await writeFile(join(lifecycle.directory, 'signals', `natural-demand-episode-armed-${SERVER.serverRunId}.json`), `${JSON.stringify(value)}\n`);
    }
    await assert.rejects(pending, /arm was not acknowledged|server receipt/i, name);
    assert.equal(clients, 0, name);
  }
});

test('a receipt created in the former read-watch gap is consumed without a second filesystem event', async () => {
  const lifecycle = await fresh('arm-read-watch-gap');
  const arm = await armNaturalDemandEpisode(lifecycle, SERVER);
  let createdAfterWatch = false;
  const acknowledged = await awaitNaturalDemandEpisodeArmed(lifecycle, SERVER, arm, 1_000, {
    afterWatchRegistered: async () => {
      createdAfterWatch = true;
      await writeArmAck(lifecycle, arm);
    }
  });
  assert.equal(createdAfterWatch, true);
  assert.equal(acknowledged.stopAdmissionNonce, arm.stopAdmissionNonce);
});

test('admitted command outcome enters the actual durable phase exactly once', async () => {
  const lifecycle = await fresh('admitted');
  const arm = await armNaturalDemandEpisode(lifecycle, SERVER);
  await writeEligible(lifecycle);
  const rcon = await outcomeRcon(lifecycle, arm, 'admitted');
  let durable = 0;
  try {
    await stopPilotNaturalDemandCarrier({ lifecycle, server: { ...SERVER, rconPort: rcon.port, rconPassword: 'one-time-secret' }, arm, timeoutMs: 1_000,
      awaitDurable: async outcome => { durable += 1; assert.equal(outcome.status, 'admitted'); } });
    assertStopCommand(rcon.commands[0], arm);
    assert.equal(durable, 1);
  } finally { await rcon.close(); }
});

test('rejected command outcome never enters durable-save waiting', async () => {
  const lifecycle = await fresh('rejected');
  const arm = await armNaturalDemandEpisode(lifecycle, SERVER);
  await writeEligible(lifecycle);
  const rcon = await outcomeRcon(lifecycle, arm, { status: 'rejected', reason: 'late natural demand' });
  let durable = 0;
  try {
    await assert.rejects(stopPilotNaturalDemandCarrier({ lifecycle, server: { ...SERVER, rconPort: rcon.port, rconPassword: 'one-time-secret' }, arm,
      timeoutMs: 1_000, awaitDurable: async () => { durable += 1; } }), /stop was rejected: late natural demand/);
    assert.equal(durable, 0);
  } finally { await rcon.close(); }
});

test('a distinct replay gets its own rejected attempt receipt and cannot enter durable work twice', async () => {
  const lifecycle = await fresh('attempt-replay');
  const arm = await armNaturalDemandEpisode(lifecycle, SERVER);
  await writeEligible(lifecycle);
  const rcon = await outcomeRcon(lifecycle, arm, [
    { status: 'admitted' },
    { status: 'rejected', reason: 'pilot natural-demand stop admission was already consumed' }
  ]);
  let durable = 0;
  const server = { ...SERVER, rconPort: rcon.port, rconPassword: 'one-time-secret' };
  try {
    await stopPilotNaturalDemandCarrier({ lifecycle, server, arm, timeoutMs: 1_000,
      awaitDurable: async outcome => { durable += 1; assert.equal(outcome.status, 'admitted'); } });
    await assert.rejects(stopPilotNaturalDemandCarrier({ lifecycle, server, arm, timeoutMs: 1_000,
      awaitDurable: async () => { durable += 1; } }), /stop was rejected: pilot natural-demand stop admission was already consumed/);
    assert.equal(rcon.commands.length, 2);
    const first = assertStopCommand(rcon.commands[0], arm);
    const replay = assertStopCommand(rcon.commands[1], arm);
    assert.notEqual(first, replay);
    assert.equal(durable, 1);
  } finally { await rcon.close(); }
});

test('missing, stale, foreign, and malformed terminal outcomes fail before durable work', async () => {
  for (const [name, status, mutate] of [
    ['missing', 'absent', value => value],
    ['stale', 'admitted', value => { value.stopAdmissionNonce = '00000000-0000-0000-0000-000000000099'; }],
    ['foreign', 'admitted', value => { value.identity = { ...value.identity, workerId: 'worker-3' }; }],
    ['malformed', 'admitted', value => { delete value.status; }]
  ]) {
    const lifecycle = await fresh(`outcome-${name}`);
    const arm = await armNaturalDemandEpisode(lifecycle, SERVER);
    await writeEligible(lifecycle);
    const rcon = await outcomeRcon(lifecycle, arm, { status, mutate });
    let durable = 0;
    try {
      await assert.rejects(stopPilotNaturalDemandCarrier({ lifecycle, server: { ...SERVER, rconPort: rcon.port, rconPassword: 'one-time-secret' }, arm,
        timeoutMs: 20, awaitDurable: async () => { durable += 1; } }), /outcome was absent|server receipt|outcome is malformed/i, name);
      assert.equal(durable, 0, name);
    } finally { await rcon.close(); }
  }
});

test('malformed arm cannot reach authenticated command transport', async () => {
  const lifecycle = await fresh('malformed-command');
  const rcon = await outcomeRcon(lifecycle, { stopAdmissionNonce: '00000000-0000-0000-0000-000000000091' }, 'admitted');
  try {
    await assert.rejects(requestPilotNaturalDemandStop(lifecycle, { ...SERVER, rconPort: rcon.port, rconPassword: 'one-time-secret' },
      { stopAdmissionNonce: 'not-a-uuid' }, 20), /admission is malformed/);
    assert.equal(rcon.connections(), 0);
  } finally { await rcon.close(); }
});

test('runner uses the executable arm and outcome consumers, never a source-only stop fence', async () => {
  const source = await readFile(new URL('../src/run-isolated-scenario.mjs', import.meta.url), 'utf8');
  assert.match(source, /runPilotWithNaturalDemandArm\(/);
  assert.match(source, /stopPilotNaturalDemandCarrier\(/);
  assert.doesNotMatch(source, /requestRconStopWithNaturalDemandFence/);
});

function awaitArmWritten(lifecycle) {
  return new Promise((resolve, reject) => {
    const watcher = watch(lifecycle.directory, { persistent: false });
    watcher.once('error', error => { watcher.close(); reject(error); });
    watcher.on('change', async (_event, filename) => {
      if (typeof filename !== 'string' || !filename.startsWith('natural-demand-episode-arm-')) return;
      try {
        const arm = JSON.parse(await readFile(join(lifecycle.directory, filename), 'utf8'));
        watcher.close(); resolve(arm);
      } catch (error) { watcher.close(); reject(error); }
    });
  });
}

function assertStopCommand(command, arm) {
  const parts = command.split(' ');
  assert.equal(parts[0], NATURAL_DEMAND_STOP_COMMAND);
  assert.equal(parts[1], arm.stopAdmissionNonce);
  assert.match(parts[2], /^[a-f\d]{8}(?:-[a-f\d]{4}){3}-[a-f\d]{12}$/i);
  assert.equal(parts.length, 3);
  return parts[2];
}

function armAck(lifecycle, arm) {
  return { schema: 1, kind: 'f02b-natural-demand-episode-arm-ack', status: 'armed', identity: lifecycle.identity,
    server: { ...SERVER }, stopAdmissionNonce: arm.stopAdmissionNonce };
}
async function writeArmAck(lifecycle, arm) {
  await writeFile(join(lifecycle.directory, 'signals', `natural-demand-episode-armed-${SERVER.serverRunId}.json`), `${JSON.stringify(armAck(lifecycle, arm))}\n`);
}
async function writeEligible(lifecycle) {
  await writeFile(join(lifecycle.directory, 'signals', `natural-demand-episode-eligible-${SERVER.serverRunId}.json`), `${JSON.stringify({
    schema: 1, kind: 'f02b-natural-demand-episode', status: 'eligible', identity: lifecycle.identity, server: { ...SERVER }, released: true,
    members: [{ generationRefCount: 0, readyForSaving: true, terminal: 'zero_ready' }]
  })}\n`);
}

async function outcomeRcon(lifecycle, arm, outcomes) {
  const planned = (Array.isArray(outcomes) ? outcomes : [outcomes]).map(outcome =>
    typeof outcome === 'string' ? { status: outcome } : { ...outcome });
  const commands = [];
  let connectionCount = 0;
  const server = createServer((socket) => {
    connectionCount += 1;
    let bytes = Buffer.alloc(0);
    socket.on('data', async chunk => {
      bytes = Buffer.concat([bytes, chunk]);
      const decoded = decodeRconFrames(bytes); bytes = decoded.tail;
      for (const frame of decoded.frames) {
        if (frame.id === 71_001) socket.write(encodeRconFrame(71_001, 2, ''));
        if (frame.id === 71_002) {
          commands.push(frame.payload);
          const current = planned.shift();
          if (current === undefined) throw new Error('unexpected natural-demand stop command');
          const attempt = assertStopCommand(frame.payload, arm);
          const { status, reason = undefined, mutate = value => value } = current;
          if (status === 'absent') continue;
          const value = { schema: 1, kind: 'f02b-natural-demand-stop-outcome', status, identity: lifecycle.identity,
            server: { ...SERVER }, stopAdmissionNonce: arm.stopAdmissionNonce, stopAttemptId: attempt,
            ...(reason === undefined ? {} : { reason }) };
          mutate(value);
          await writeFile(join(lifecycle.directory, 'signals', `natural-demand-stop-outcome-${SERVER.serverRunId}-${attempt}.json`), `${JSON.stringify(value)}\n`);
        }
      }
    });
  });
  await new Promise((resolve, reject) => server.listen(0, '127.0.0.1', error => error ? reject(error) : resolve()));
  return { port: server.address().port, commands, connections: () => connectionCount,
    close: () => new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())) };
}

async function fresh(segmentId) {
  const parent = await mkdtemp(process.env.PALE_MIRROR_TEST_TMP_PREFIX ?? '/home/rd/proj/pm-f02b-r31-node-');
  roots.push(parent);
  return createLifecycleBarrierSession(join(parent, 'lifecycle'), newLifecycleIdentity({ buildIdentitySha256: BUILD, workerId: 'worker-2',
    runId: '00000000-0000-0000-0000-000000000011', scenarioId: 'f02b-natural-demand', segmentId,
    nonce: '00000000-0000-0000-0000-000000000013', sessionId: '00000000-0000-0000-0000-000000000014' }));
}
