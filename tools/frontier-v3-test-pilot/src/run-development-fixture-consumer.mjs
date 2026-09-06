import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { createConnection } from 'node:net';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { acquireDevelopmentFixtureConsumer, digestOpaqueDirectory, locateDevelopmentFixtureImage } from './development-fixture-image.mjs';
import { prepareFixtureConsumerRuntime } from './development-fixture-runtime.mjs';
import { writeFailureBundle } from './failure-bundle.mjs';
import { awaitLifecycleSignal, createLifecycleBarrierSession, LifecycleSignal, newLifecycleIdentity } from './lifecycle-barrier.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';
import { preparedLaunch } from './prepared-launch.mjs';
import { requestRconStop } from './rcon.mjs';

const [bootstrapArgument, formatArgument, identityArgument,
  outputArgument = `build/frontier-v3-scenarios/f0va-fixture-consumer-${Date.now()}.json`] = process.argv.slice(2);
if (!bootstrapArgument || !formatArgument || !identityArgument) {
  throw new Error('usage: node run-development-fixture-consumer.mjs <bootstrap-report.json> <fixture-format.json> <prepared-build.json> [report.json]');
}
const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const bootstrapPath = safeBuildPath(bootstrapArgument, 'fixture bootstrap report');
const formatPath = safeBuildPath(formatArgument, 'fixture format');
const identityPath = safeBuildPath(identityArgument, 'prepared build identity');
const output = safeBuildPath(outputArgument, 'fixture consumer report'); await absent(output, 'fixture consumer report');
const bootstrap = JSON.parse(await readFile(bootstrapPath, 'utf8'));
const format = JSON.parse(await readFile(formatPath, 'utf8'));
const identity = Object.freeze(JSON.parse(await readFile(identityPath, 'utf8')));
if (bootstrap?.status !== 'ok' || !bootstrap?.metadata || !bootstrap?.image?.imageSha256 || typeof format?.imageId !== 'string') {
  throw new Error('fixture consumer needs one successful immutable bootstrap report and format');
}
await requirePreparedF0vBuild(project, identity);
if (bootstrap.metadata.artifactSha256 !== identity.preparedArtifact?.sha256 || bootstrap.metadata.serverClasspathSha256 !== identity.classpaths?.server?.sha256
    || bootstrap.metadata.clientClasspathSha256 !== identity.classpaths?.client?.sha256 || bootstrap.metadata.launchManifestSha256 !== identity.launchManifest?.sha256) {
  throw new Error('fixture bootstrap report does not match the prepared artifact/classpath identity');
}
const port = Number(process.env.FRONTIER_V3_PILOT_PORT ?? 25577);
if (!Number.isInteger(port) || port < 1024 || port >= 65535 || await portOpen(port) || await portOpen(port + 1)) {
  throw new Error('fixture consumer ports are malformed or occupied');
}
const located = await locateDevelopmentFixtureImage({ project, imageId: format.imageId, metadata: bootstrap.metadata });
if (located.status !== 'READY' || located.image.imageSha256 !== bootstrap.image.imageSha256) throw new Error(`fixture image is unavailable or foreign: ${located.reason ?? 'HASH_MISMATCH'}`);
const runId = randomUUID(); const world = `f0va-consumer-${runId.slice(0, 8)}`; const rconPassword = randomUUID();
const lifecycle = await createLifecycleBarrierSession(resolve(project, 'build/frontier-v3-scenarios'), newLifecycleIdentity({
  buildIdentitySha256: hash(JSON.stringify(identity)), workerId: process.env.FRONTIER_V3_PILOT_WORKER_ID ?? 'local-f0va-fixture',
  runId, scenarioId: 'f0va_fixture_consumer', segmentId: 'fixture-consumer'
}));
const worldDirectory = `pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/${world}`;
let server = null; let completed = false; let failure = null;
try {
  const consumer = await acquireDevelopmentFixtureConsumer({ project, image: located.image, consumerId: `fixture-${runId}`,
    targetDirectory: worldDirectory, policy: { tier: 'T3' } });
  await prepareFixtureConsumerRuntime({ project, worldName: world, port, rconPort: port + 1, rconPassword,
    username: 'PMTestPilot', viewDistance: bootstrap.metadata.viewDistance });
  server = await startServer();
  const durable = await stopServer(server); server = null;
  const servedWorld = await digestOpaqueDirectory(project, worldDirectory);
  await writeExclusive(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-development-fixture-consumer', status: 'ok', runId,
    image: { imageSha256: located.image.imageSha256, sourceBootstrap: relative(project, bootstrapPath) }, consumer,
    lifecycle: { serverRunId: durable.serverRunId, durableSave: true, portClosed: true, directory: relative(project, lifecycle.directory) },
    servedWorld
  }, null, 2)}\n`);
  completed = true;
} catch (error) { failure = error; throw error; }
finally {
  if (server !== null) await stopFailedServer(server).catch((cleanup) => console.error(`PMV3_FIXTURE_CONSUMER cleanup failed: ${cleanup}`));
  if (completed) await rm(resolve(project, worldDirectory), { recursive: true, force: true });
  if (!completed) {
    const bundle = await writeFailureBundle({ project, output, scenarioPath: bootstrapPath, runId, failure,
      timing: { runner: 'f0va-development-fixture-consumer', status: 'failed' }, build: identity,
      process: { world, port, serverPid: server?.pid ?? null }, worldDirectory: resolve(project, worldDirectory), lifecycleDirectory: lifecycle.directory });
    console.error(`PMV3_FIXTURE_CONSUMER failure_bundle=${bundle}`);
  }
}
console.log(JSON.stringify({ status: 'ok', report: output, runId }));

async function startServer() {
  const launch = await preparedLaunch(project, identity, 'server', {
    'pale_mirror.frontier_v3.enabled': 'true', 'pale_mirror.frontier_v3.pilot.run_id': runId,
    'pale_mirror.frontier_v3.pilot.lifecycle_control_directory': lifecycle.directory,
    'pale_mirror.frontier_v3.pilot.profile': bootstrap.metadata.profile
  });
  const child = spawn(launch.command, launch.args, { cwd: launch.cwd,
    env: { ...process.env, FRONTIER_V3_PILOT_RCON_PASSWORD: rconPassword }, stdio: ['ignore', 'pipe', 'pipe'] });
  for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => process.stdout.write(chunk));
  const ready = await signalOrExit(child, LifecycleSignal.SERVER_RUN_READY, runId, 'fixture consumer server readiness');
  if (ready.detail.serverRunId !== runId || !Number.isInteger(ready.detail.serverPid) || ready.detail.serverPid <= 1) {
    throw new Error('fixture consumer readiness signal is foreign or lacks its exact JVM');
  }
  return Object.freeze({ child, pid: ready.detail.serverPid, ready });
}
async function stopServer(active) {
  await requestRconStop({ port: port + 1, password: rconPassword });
  const saved = await signalOrExit(active.child, LifecycleSignal.DURABLE_SERVER_SAVE, runId, 'fixture consumer durable save');
  if (saved.detail.serverRunId !== runId) throw new Error('fixture consumer durable-save signal is foreign');
  await exitWithin(active.child, 90_000, 'fixture consumer server');
  if (await portOpen(port)) throw new Error('fixture consumer server exited but its reserved port remains open');
  return Object.freeze({ serverRunId: runId, durableSave: true, portClosed: true });
}
async function stopFailedServer(active) {
  try { await requestRconStop({ port: port + 1, password: rconPassword, timeoutMs: 10_000 }); } catch { /* exact process follows */ }
  try { process.kill(active.pid, 'SIGTERM'); } catch (error) { if (error?.code !== 'ESRCH') throw error; }
  await exitWithin(active.child, 30_000, 'failed fixture consumer server');
  if (await portOpen(port)) throw new Error('failed fixture consumer retained its reserved port');
}
async function signalOrExit(child, signal, suffix, label) {
  const pending = awaitLifecycleSignal(lifecycle, signal, suffix, 180_000);
  const first = await Promise.race([pending.then((value) => ({ value })), exited(child).then((code) => ({ code }))]);
  if ('value' in first) return first.value;
  try { return await Promise.race([pending, timeout(1_000)]); } catch { throw new Error(`${label} was not acknowledged before exact JVM exit (${first.code})`); }
}
async function exitWithin(child, timeoutMs, label) { await Promise.race([exited(child), timeout(timeoutMs)]); }
function exited(child) { return child.exitCode !== null || child.signalCode !== null ? Promise.resolve(child.exitCode ?? 1) : new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1))); }
function timeout(milliseconds) { return new Promise((_, reject) => setTimeout(() => reject(new Error(`fixture consumer timeout after ${milliseconds}ms`)), milliseconds)); }
function portOpen(candidate) { return new Promise((resolveOpen) => { const socket = createConnection({ host: '127.0.0.1', port: candidate }); const finish = (open) => { socket.removeAllListeners(); socket.destroy(); resolveOpen(open); }; socket.once('connect', () => finish(true)); socket.once('error', () => finish(false)); socket.setTimeout(500, () => finish(false)); }); }
function safeBuildPath(candidate, label) { if (typeof candidate !== 'string') throw new Error(`${label} is malformed`); const target = resolve(project, candidate); const path = relative(resolve(project, 'build'), target); if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} must remain under build/`); return target; }
async function absent(path, label) { try { await stat(path); throw new Error(`refusing to overwrite ${label}: ${path}`); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
async function writeExclusive(path, contents) { await mkdir(dirname(path), { recursive: true }); await writeFile(path, contents, { encoding: 'utf8', flag: 'wx' }); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
