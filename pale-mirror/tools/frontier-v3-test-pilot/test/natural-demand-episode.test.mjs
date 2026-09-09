import assert from 'node:assert/strict';
import { createServer } from 'node:net';
import test, { after } from 'node:test';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { armNaturalDemandEpisode, awaitNaturalDemandEpisodeReady, NATURAL_DEMAND_STOP_COMMAND, naturalDemandStopCommand, requestPilotNaturalDemandStop } from '../src/natural-demand-episode.mjs';
import { createLifecycleBarrierSession, newLifecycleIdentity } from '../src/lifecycle-barrier.mjs';
import { decodeRconFrames, encodeRconFrame } from '../src/rcon.mjs';

const BUILD = 'a'.repeat(64);
const SERVER = Object.freeze({ serverRunId: '00000000-0000-0000-0000-000000000021', serverPid: 12345 });
const roots = [];

after(async () => { await Promise.all(roots.map(root => rm(root, { recursive: true, force: true }))); });

test('the real isolated graceful consumer transports its exact one-use pilot admission command', async () => {
  const lifecycle = await fresh('positive');
  const arm = await armNaturalDemandEpisode(lifecycle, SERVER);
  const rcon = await authenticatedRcon();
  try {
    await requestPilotNaturalDemandStop({ ...SERVER, rconPort: rcon.port, rconPassword: 'one-time-secret' }, arm);
    assert.equal(await rcon.command, `${NATURAL_DEMAND_STOP_COMMAND} ${arm.stopAdmissionNonce}`);
  } finally { await rcon.close(); }
});

test('missing or foreign one-use admission cannot reach the real RCON consumer', async () => {
  const rcon = await authenticatedRcon();
  try {
    assert.throws(() => requestPilotNaturalDemandStop({ ...SERVER, rconPort: rcon.port, rconPassword: 'one-time-secret' },
      { stopAdmissionNonce: 'not-a-uuid' }), /admission is malformed/);
    assert.equal(rcon.connections(), 0);
  } finally { await rcon.close(); }
});

test('server-owned eligibility coordinates the carrier but cannot become its stop authority', async () => {
  const lifecycle = await fresh('coordination');
  await writeFile(join(lifecycle.directory, 'signals', `natural-demand-episode-eligible-${SERVER.serverRunId}.json`), `${JSON.stringify({
    schema: 1, kind: 'f02b-natural-demand-episode', status: 'eligible', identity: lifecycle.identity,
    server: SERVER, released: true, members: [{ generationRefCount: 0, readyForSaving: true, terminal: 'zero_ready' }]
  })}\n`);
  await awaitNaturalDemandEpisodeReady(lifecycle, SERVER, 100);
});

test('the runner arms before pilot work and only carrier paths use the pilot command transport', async () => {
  const source = await (await import('node:fs/promises')).readFile(new URL('../src/run-isolated-scenario.mjs', import.meta.url), 'utf8');
  const carrierId = source.indexOf("scenario.id === 'disposable_f02b_normal_product_recovery'");
  const arm = source.indexOf('server.naturalDemandEpisodeArm = await armNaturalDemandEpisode');
  const pilot = source.indexOf("await runPilot(beforeRestartScenario");
  const coordination = source.indexOf('await awaitNaturalDemandEpisodeReady(lifecycle, server, DURABLE_STOP_TIMEOUT_MS)');
  const carrier = source.indexOf('await requestPilotNaturalDemandStop(server, naturalDemandEpisodeArm)');
  const vanilla = source.indexOf('await requestRconStop({ port: server.rconPort, password: server.rconPassword });');
  assert.ok(carrierId >= 0 && arm >= 0 && arm < pilot && coordination > pilot && carrier >= 0 && vanilla >= 0 && carrier < vanilla);
  assert.equal(source.includes('requestRconStopWithNaturalDemandFence'), false);
});

test('command formatting rejects a replay-shaped missing nonce before transport', () => {
  assert.throws(() => naturalDemandStopCommand({ stopAdmissionNonce: '' }), /admission is malformed/);
});

async function authenticatedRcon() {
  let resolveCommand;
  const command = new Promise(resolve => { resolveCommand = resolve; });
  let connectionCount = 0;
  const server = createServer((socket) => {
    connectionCount += 1;
    let bytes = Buffer.alloc(0);
    socket.on('data', chunk => {
      bytes = Buffer.concat([bytes, chunk]);
      const decoded = decodeRconFrames(bytes); bytes = decoded.tail;
      for (const frame of decoded.frames) {
        if (frame.id === 71_001) socket.write(encodeRconFrame(71_001, 2, ''));
        if (frame.id === 71_002) resolveCommand(frame.payload);
      }
    });
  });
  await new Promise((resolve, reject) => server.listen(0, '127.0.0.1', error => error ? reject(error) : resolve()));
  return { port: server.address().port, command, connections: () => connectionCount,
    close: () => new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve())) };
}

async function fresh(segmentId) {
  const parent = await mkdtemp(process.env.PALE_MIRROR_TEST_TMP_PREFIX ?? '/home/rd/proj/pm-f02b-r30-node-');
  roots.push(parent);
  return createLifecycleBarrierSession(join(parent, 'lifecycle'), newLifecycleIdentity({ buildIdentitySha256: BUILD, workerId: 'worker-2',
    runId: '00000000-0000-0000-0000-000000000011', scenarioId: 'f02b-natural-demand', segmentId,
    nonce: '00000000-0000-0000-0000-000000000013', sessionId: '00000000-0000-0000-0000-000000000014' }));
}
