import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import test from 'node:test';
import {
  captureSaveOwnerObservation,
  prearmSaveOwnerObserver,
  requireCapturedSaveOwnerObservation
} from '../src/save-owner-observer.mjs';

const project = process.cwd();
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java') : 'java';

test('owned Java observer retains exact PID/start-time-bound primary capture', { skip: process.platform !== 'linux' }, async () => {
  await withOwnedJava(async fixture => {
    const armed = await arm(fixture);
    const observation = await captureSaveOwnerObservation(armed, fixture.outputSource());
    assert.equal(observation.capture.method, 'jcmd-thread-print');
    assert.equal(observation.server.serverPid, fixture.pid);
    assert.equal(observation.snapshots.length, 2);
    assert.equal(requireCapturedSaveOwnerObservation(observation), observation);
    const receipt = JSON.parse(await readFile(observation.receipt, 'utf8'));
    assert.equal(receipt.server.processStartTime, observation.server.processStartTime);
  });
});

test('owned Java observer rejects foreign pilot identity and unavailable /proc PID before capture', { skip: process.platform !== 'linux' }, async () => {
  await withOwnedJava(async fixture => {
    await assert.rejects(arm(fixture, { serverRunId: 'foreign-run' }), /declared pilot identity/);
    await assert.rejects(prearmSaveOwnerObserver({ project, root: fixture.root, lifecycleIdentity: fixture.lifecycleIdentity,
      server: { ...fixture.server, serverPid: 999999 } }), /unavailable/);
    const armed = await arm(fixture, { suffix: 'drift' });
    await assert.rejects(captureSaveOwnerObservation({ ...armed, server: { ...armed.server, processStartTime: '0' } }, fixture.outputSource()),
      /identity drifted/);
  });
});

test('owned Java observer uses SIGQUIT fallback and rejects an absent fallback receipt', { skip: process.platform !== 'linux' }, async () => {
  await withOwnedJava(async fixture => {
    const armed = await arm(fixture);
    const fallbackArm = { ...armed, server: { ...armed.server, jcmd: join(fixture.root, 'missing-jcmd') } };
    const observation = await captureSaveOwnerObservation(fallbackArm, fixture.outputSource());
    assert.equal(observation.capture.method, 'sigquit-thread-dump');

    const second = await arm(fixture, { suffix: 'absent', captureTimeoutMs: 40 });
    const absentFallback = { ...second, server: { ...second.server, jcmd: join(fixture.root, 'missing-jcmd') } };
    await assert.rejects(captureSaveOwnerObservation(absentFallback, {
      output: () => 'Saving worlds', outputRevision: () => 1, outputAfter: () => new Promise(() => {})
    }), /fallback thread dump/);
  });
});

test('captured receipt rejects missing artifacts, phase drift, and incomplete identity', () => {
  const valid = { schema: 1, kind: 'frontier-v3-save-owner-observation', status: 'captured', lifecycleIdentity: { runId: 'r' },
    server: { serverPid: 22, processStartTime: '33', serverRunId: 'server' }, capture: { path: 'thread.txt', sha256: 'a' },
    snapshots: [{ path: 'one', sha256: 'b' }, { path: 'two', sha256: 'c' }] };
  assert.doesNotThrow(() => requireCapturedSaveOwnerObservation(valid));
  for (const mutate of [value => { value.status = 'armed'; }, value => { value.server.processStartTime = ''; },
    value => { value.snapshots.pop(); }, value => { value.capture.sha256 = ''; }]) {
    const candidate = structuredClone(valid); mutate(candidate);
    assert.throws(() => requireCapturedSaveOwnerObservation(candidate), /incomplete|invalid/);
  }
});

test('isolated graceful recovery pre-arms the observer before RCON and retains its receipt', async () => {
  const source = await readFile(new URL('../src/run-isolated-scenario.mjs', import.meta.url), 'utf8');
  const prearm = source.indexOf('server.ownerObserverArm = await prearmSaveOwnerObserver');
  const stop = source.indexOf('await requestRconStop');
  const capture = source.indexOf('await captureSaveOwnerObservation');
  const durable = source.indexOf('await awaitLifecycleSignal(lifecycle, LifecycleSignal.DURABLE_SERVER_SAVE');
  assert.ok(prearm >= 0 && prearm < stop && stop < capture && capture < durable);
  assert.match(source, /ownerObservation: ownerObservationFact/);
  assert.match(source, /manifest\.ownerObservation = ownerObservationFact/);
});

async function arm(fixture, { serverRunId = fixture.server.serverRunId, suffix = '', captureTimeoutMs = undefined } = {}) {
  return await prearmSaveOwnerObserver({ project, root: fixture.root, lifecycleIdentity: fixture.lifecycleIdentity,
    server: { ...fixture.server, serverRunId }, ...(suffix === '' ? {} : { root: join(fixture.root, suffix) }),
    ...(captureTimeoutMs === undefined ? {} : { captureTimeoutMs }) });
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
