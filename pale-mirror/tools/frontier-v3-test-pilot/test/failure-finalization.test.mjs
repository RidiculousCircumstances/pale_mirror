import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { createConnection } from 'node:net';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { writeFailureBundle } from '../src/failure-bundle.mjs';
import { boundedCleanupFailures, finalizeFailurePath } from '../src/failure-finalization.mjs';

test('graceful cleanup failure still emits forensics after exact child and port cleanup', async () => {
  const project = await mkdtemp(join(tmpdir(), 'pmv3-failure-finalization-'));
  const child = spawn(process.execPath, ['-e', [
    "const net = require('node:net');",
    "const server = net.createServer();",
    "server.listen(0, '127.0.0.1', () => console.log(server.address().port));"
  ].join(' ')], { stdio: ['ignore', 'pipe', 'pipe'] });
  try {
    const port = Number(await onceLine(child.stdout));
    assert.ok(Number.isInteger(port) && port > 1024);
    await mkdir(join(project, 'build/profiles'), { recursive: true });
    await mkdir(join(project, 'world'), { recursive: true });
    await writeFile(join(project, 'scenario.json'), '{"schema":1,"isolation":{"seed":1}}\n');
    await writeFile(join(project, 'server.log'), 'Saving worlds\n');
    const originalFailure = new Error('pilot assertion failed before graceful cleanup');
    const processEvidence = { serverPid: child.pid, port, gracefulShutdown: null };
    const result = await finalizeFailurePath({
      originalFailure,
      cleanup: async (attempt) => {
        await attempt('graceful_exact_child_cleanup', async () => {
          // This is the recurrence ordering: the ordinary graceful save fails, the exact child
          // is still stopped, and only then the original graceful failure is rethrown.
          const gracefulFailure = new Error('durable_server_save was not acknowledged');
          processEvidence.gracefulShutdown = { pid: child.pid, proc_status: 'captured-before-kill',
            world: { files: [{ path: 'level.dat', bytes: 7, modifiedMs: 1 }] } };
          child.kill('SIGKILL');
          await once(child, 'exit');
          assert.equal(await portOpen(port), false);
          throw gracefulFailure;
        });
      },
      emitFailureBundle: async ({ terminalFailure, cleanupFailures }) => writeFailureBundle({
        project, output: 'build/profiles/run.json', scenarioPath: join(project, 'scenario.json'),
        runId: '00000000-0000-0000-0000-000000000003', timing: { totalMillis: 1 }, failure: terminalFailure,
        serverLog: join(project, 'server.log'), worldDirectory: join(project, 'world'),
        process: { ...processEvidence, cleanupFailures: boundedCleanupFailures(cleanupFailures) },
        termination: { portClosed: !await portOpen(port), ownedPidExited: child.exitCode !== null || child.signalCode !== null }
      })
    });
    assert.equal(result.terminalFailure, originalFailure);
    assert.equal(result.cleanupFailures.length, 1);
    assert.equal(result.cleanupFailures[0].label, 'graceful_exact_child_cleanup');
    assert.match(result.cleanupFailures[0].error.message, /durable_server_save/);
    assert.equal(result.bundleFailure, null);
    assert.throws(() => process.kill(child.pid, 0), { code: 'ESRCH' });
    assert.equal(await portOpen(port), false);
    const bundle = JSON.parse(await readFile(join(result.bundle, 'bundle.json'), 'utf8'));
    assert.equal(bundle.process.gracefulShutdown.proc_status, 'captured-before-kill');
    assert.equal(bundle.process.gracefulShutdown.world.files[0].path, 'level.dat');
    assert.deepEqual(bundle.process.cleanupFailures, [{ label: 'graceful_exact_child_cleanup', message: 'durable_server_save was not acknowledged' }]);
    assert.deepEqual(bundle.termination, { portClosed: true, ownedPidExited: true });
    assert.match(bundle.failure, /pilot assertion failed before graceful cleanup/);
  } finally {
    if (child.exitCode === null && child.signalCode === null) child.kill('SIGKILL');
    await rm(project, { recursive: true, force: true });
  }
});

async function portOpen(port) {
  return await new Promise((resolve) => {
    const socket = createConnection({ host: '127.0.0.1', port });
    socket.once('connect', () => { socket.destroy(); resolve(true); });
    socket.once('error', () => resolve(false));
  });
}

function onceLine(stream) {
  return new Promise((resolve, reject) => {
    let buffered = '';
    stream.setEncoding('utf8');
    stream.on('data', (chunk) => {
      buffered += chunk;
      const newline = buffered.indexOf('\n');
      if (newline >= 0) resolve(buffered.slice(0, newline));
    });
    stream.once('error', reject);
  });
}
