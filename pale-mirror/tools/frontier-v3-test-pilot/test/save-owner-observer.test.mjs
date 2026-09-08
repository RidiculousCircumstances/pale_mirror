import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import test from 'node:test';
import {
  captureSaveOwnerObservation,
  classifySaveOwnerProgress,
  prearmSaveOwnerObserver,
  requireCapturedSaveOwnerObservation,
  startSaveOwnerObservation
} from '../src/save-owner-observer.mjs';

const project = process.cwd();
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';

test('owned Java observer retains exact PID/start-time-bound primary capture', { skip: process.platform !== 'linux' }, async () => {
  await withOwnedJava(async fixture => {
    const armed = await arm(fixture);
    const observation = await captureSaveOwnerObservation(armed, fixture.outputSource());
    assert.equal(observation.slots.length, 3);
    assert.equal(observation.slots[0].status, 'captured');
    assert.equal(observation.server.serverPid, fixture.pid);
    assert.equal(requireCapturedSaveOwnerObservation(observation), observation);
    const receipt = JSON.parse(await readFile(observation.receipt, 'utf8'));
    assert.equal(receipt.server.processStartTime, observation.server.processStartTime);
  });
});

test('owned Java observer rejects foreign pilot identity and unavailable /proc PID before capture', { skip: process.platform !== 'linux' }, async () => {
  await withOwnedJava(async fixture => {
    await assert.rejects(arm(fixture, { serverRunId: 'foreign-run' }), /declared pilot identity/);
    await assert.rejects(arm(fixture, { lifecycleDirectory: join(fixture.root, 'foreign-control') }), /declared pilot identity/);
    await assert.rejects(prearmSaveOwnerObserver({ project, root: fixture.root, lifecycleIdentity: fixture.lifecycleIdentity,
      server: { ...fixture.server, serverPid: 999999 } }), /unavailable/);
    const armed = await arm(fixture, { suffix: 'drift' });
    const drifted = await captureSaveOwnerObservation({ ...armed, server: { ...armed.server, processStartTime: '0' } }, fixture.outputSource());
    assert.equal(drifted.slots[0].status, 'unavailable');
    assert.match(drifted.slots[0].reason.failure, /identity drifted/);
  });
});

test('owned Java observer uses SIGQUIT fallback and retains explicit unavailable late slots', { skip: process.platform !== 'linux' }, async () => {
  await withOwnedJava(async fixture => {
    const armed = await arm(fixture);
    const fallbackArm = { ...armed, server: { ...armed.server, jcmd: join(fixture.root, 'missing-jcmd') } };
    const observation = await captureSaveOwnerObservation(fallbackArm, fixture.outputSource());
    assert.equal(observation.slots[0].capture.method, 'sigquit-thread-dump');

    const second = await arm(fixture, { suffix: 'unavailable' });
    const observer = startSaveOwnerObservation(second, fixture.outputSource());
    observer.finish({ kind: 'durable_server_save' });
    const unavailable = await observer.result;
    assert.equal(unavailable.slots.length, 3);
    assert.ok(unavailable.slots.slice(1).every(slot => slot.status === 'unavailable'));
    assert.doesNotThrow(() => requireCapturedSaveOwnerObservation(unavailable));
  });
});

test('captured receipt rejects missing slots, identity drift, and invalid unavailable evidence', () => {
  const valid = { schema: 2, kind: 'frontier-v3-save-owner-observation', status: 'completed', lifecycleIdentity: { runId: 'r' },
    server: { serverPid: 22, processStartTime: '33', serverRunId: 'server' }, slotOffsetsMs: [0, 1, 2], slots: [
      { schema: 1, index: 0, offsetMs: 0, status: 'captured', capture: { path: 'thread.txt', sha256: 'a' }, snapshot: { path: 'one', sha256: 'b' }, facts: { processWriteBytes: 1, processSyscalls: 1, worldFilesSha256: 'w' } },
      { schema: 1, index: 1, offsetMs: 1, status: 'unavailable', reason: { kind: 'durable_server_save' } },
      { schema: 1, index: 2, offsetMs: 2, status: 'unavailable', reason: { kind: 'durable_server_save' } }
    ] };
  assert.doesNotThrow(() => requireCapturedSaveOwnerObservation(valid));
  for (const mutate of [value => { value.status = 'armed'; }, value => { value.server.processStartTime = ''; },
    value => { value.slots.pop(); }, value => { value.slots[0].capture.sha256 = ''; }, value => { value.slots[1].reason.kind = ''; }]) {
    const candidate = structuredClone(valid); mutate(candidate);
    assert.throws(() => requireCapturedSaveOwnerObservation(candidate), /incomplete|invalid/);
  }
});

test('late observer progress classifier distinguishes progressing, stopped and unavailable evidence without naming an owner', () => {
  const base = { schema: 2, kind: 'frontier-v3-save-owner-observation', status: 'completed', lifecycleIdentity: { runId: 'r' },
    server: { serverPid: 22, processStartTime: '33', serverRunId: 'server' }, slotOffsetsMs: [0, 1, 2], slots: [
      { schema: 1, index: 0, offsetMs: 0, status: 'captured', capture: { path: 'a', sha256: 'a' }, snapshot: { path: 'a', sha256: 'a' }, facts: { processWriteBytes: 1, processSyscalls: 1, worldFilesSha256: 'a', serverChunkUnload: true, regionPwrite: true } },
      { schema: 1, index: 1, offsetMs: 1, status: 'captured', capture: { path: 'b', sha256: 'b' }, snapshot: { path: 'b', sha256: 'b' }, facts: { processWriteBytes: 2, processSyscalls: 2, worldFilesSha256: 'b', serverChunkUnload: true, regionPwrite: true } },
      { schema: 1, index: 2, offsetMs: 2, status: 'unavailable', reason: { kind: 'durable_server_save' } }
    ] };
  assert.equal(classifySaveOwnerProgress(base).kind, 'progressing_io');
  const stopped = structuredClone(base); stopped.slots[1].facts = { ...stopped.slots[0].facts };
  assert.equal(classifySaveOwnerProgress(stopped).kind, 'no_io_progress');
  const unavailable = structuredClone(base); unavailable.slots[1] = { schema: 1, index: 1, offsetMs: 1, status: 'unavailable', reason: { kind: 'capture_unavailable' } };
  assert.equal(classifySaveOwnerProgress(unavailable).kind, 'insufficient');
});

test('isolated graceful recovery pre-arms the observer before RCON and retains its receipt', async () => {
  const source = await readFile(new URL('../src/run-isolated-scenario.mjs', import.meta.url), 'utf8');
  const prearm = source.indexOf('server.ownerObserverArm = await prearmSaveOwnerObserver');
  const stop = source.indexOf('await requestRconStop');
  const capture = source.indexOf('const ownerObserver =');
  const durable = source.indexOf('await awaitLifecycleSignal(lifecycle, LifecycleSignal.DURABLE_SERVER_SAVE');
  assert.ok(prearm >= 0 && prearm < stop && stop < capture && capture < durable);
  assert.match(source, /ownerObservation: ownerObservationFact/);
  assert.match(source, /manifest\.ownerObservation = ownerObservationFact/);
});

async function arm(fixture, { serverRunId = fixture.server.serverRunId, lifecycleDirectory = fixture.server.lifecycleDirectory, suffix = '', captureTimeoutMs = undefined } = {}) {
  return await prearmSaveOwnerObserver({ project, root: fixture.root, lifecycleIdentity: fixture.lifecycleIdentity,
    server: { ...fixture.server, serverRunId, lifecycleDirectory }, ...(suffix === '' ? {} : { root: join(fixture.root, suffix) }),
    ...(captureTimeoutMs === undefined ? {} : { captureTimeoutMs }), slotOffsetsMs: [0, 1, 2] });
}

async function withOwnedJava(action) {
  await mkdir(join(project, 'build'), { recursive: true });
  const root = await mkdtemp(join(project, 'build', 'save-owner-observer-'));
  const source = join(root, 'OwnedObserver.java');
  const token = `token-${Date.now()}`;
  const runId = `run-${token}`;
  const controlDirectory = join(root, `control-${runId}`);
  await writeFile(source, `public class OwnedObserver { public static void main(String[] args) throws Exception { System.out.println("READY ${token}"); System.err.flush(); System.out.flush(); Thread.sleep(30000); } }\n`);
  const child = spawn(java, [`-Dpale_mirror.frontier_v3.pilot.run_id=${runId}`,
    `-Dpale_mirror.frontier_v3.pilot.lifecycle_control_directory=${controlDirectory}`, source], { stdio: ['ignore', 'pipe', 'pipe'] });
  let output = ''; let revision = 0;
  for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', chunk => { output += chunk; revision++; });
  try {
    await waitFor(() => output.includes(`READY ${token}`));
    await action({ root, pid: child.pid, lifecycleIdentity: { runId, workerId: 'observer-test', scenarioId: 'owned-java' },
      server: { serverPid: child.pid, serverRunId: runId, lifecycleDirectory: controlDirectory, child },
      outputSource: () => ({ output: () => `Saving worlds\n${output}`, outputRevision: () => revision,
        outputAfter: seen => new Promise(resolveWait => {
          const timer = setInterval(() => { if (revision !== seen) { clearInterval(timer); resolveWait(); } }, 5);
        }) }) });
  } finally {
    if (child.exitCode === null) child.kill('SIGTERM');
    await new Promise(resolveExit => child.once('exit', resolveExit));
    await rm(root, { recursive: true, force: true });
  }
}

async function waitFor(predicate) {
  const deadline = Date.now() + 5_000;
  while (!predicate()) {
    if (Date.now() >= deadline) throw new Error('owned Java fixture did not become ready');
    await new Promise(resolveDelay => setTimeout(resolveDelay, 10));
  }
}
