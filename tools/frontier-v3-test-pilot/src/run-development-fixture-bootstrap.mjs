import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { createConnection } from 'node:net';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createDevelopmentFixtureImage } from './development-fixture-image.mjs';
import { writeFailureBundle } from './failure-bundle.mjs';
import { awaitLifecycleSignal, createLifecycleBarrierSession, LifecycleSignal, newLifecycleIdentity } from './lifecycle-barrier.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';
import { preparedLaunch } from './prepared-launch.mjs';
import { requestRconStop } from './rcon.mjs';
import { defaultPilotProfile, loadScenario } from './scenario.mjs';
import { fingerprintWorkingContent } from './evidence-cache.mjs';

const [scenarioArgument, identityArgument, formatArgument,
  outputArgument = `build/frontier-v3-scenarios/f0va-fixture-bootstrap-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioArgument || !identityArgument || !formatArgument) {
  throw new Error('usage: node run-development-fixture-bootstrap.mjs <scenario.json> <prepared-build.json> <fixture-format.json> [report.json]');
}
const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const scenarioPath = safeProjectPath(scenarioArgument, 'fixture scenario');
const identityPath = safeProjectPath(identityArgument, 'prepared build identity');
const formatPath = safeProjectPath(formatArgument, 'fixture format');
const output = safeBuildPath(outputArgument, 'fixture bootstrap report');
await absent(output, 'fixture bootstrap report');
const { scenario, sha256: scenarioSha256 } = await loadScenario(scenarioPath);
if (scenario.isolation?.mode !== 'disposable_lite') throw new Error('fixture bootstrap needs one disposable-lite scenario');
const identity = Object.freeze(JSON.parse(await readFile(identityPath, 'utf8')));
await requirePreparedF0vBuild(project, identity);
const format = JSON.parse(await readFile(formatPath, 'utf8'));
const port = Number(process.env.FRONTIER_V3_PILOT_PORT ?? 25577);
if (!Number.isInteger(port) || port < 1024 || port >= 65535) throw new Error('FRONTIER_V3_PILOT_PORT must be 1024..65534');
if (await portOpen(port) || await portOpen(port + 1)) throw new Error(`fixture bootstrap ports ${port}/${port + 1} are already occupied`);

const runId = randomUUID();
const workerId = process.env.FRONTIER_V3_PILOT_WORKER_ID ?? 'local-f0va-fixture';
const lifecycle = await createLifecycleBarrierSession(resolve(project, 'build/frontier-v3-scenarios'), newLifecycleIdentity({
  buildIdentitySha256: hash(JSON.stringify(identity)), workerId, runId, scenarioId: scenario.id, segmentId: 'fixture-bootstrap'
}));
const world = `f0va-fixture-${scenario.id.replace(/[^a-z0-9_-]/g, '-').slice(0, 32)}-${runId.slice(0, 8)}`;
const worldDirectory = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server', world);
const rconPassword = randomUUID();
const metadata = await fixtureMetadata({ identity, format, scenario, scenarioSha256 });
let server = null; let completed = false; let failure = null;
try {
  await prepareNormalWorld();
  server = await startServer();
  const durable = await stopServer(server);
  server = null;
  const image = await createDevelopmentFixtureImage({ project, sourceDirectory: relative(project, worldDirectory),
    imageId: format.imageId, metadata, durableStop: durable });
  await writeExclusive(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-development-fixture-bootstrap', status: 'ok', runId,
    scenario: { path: relative(project, scenarioPath), sha256: scenarioSha256 }, build: identity, metadata,
    lifecycle: { directory: relative(project, lifecycle.directory), serverRunId: durable.serverRunId, durableSave: true, portClosed: true },
    image: { imageId: format.imageId, directory: relative(project, image.directory), imageSha256: image.imageSha256 }
  }, null, 2)}\n`);
  completed = true;
} catch (error) {
  failure = error;
  throw error;
} finally {
  if (server !== null) await stopFailedServer(server).catch((cleanup) => console.error(`PMV3_FIXTURE cleanup failed: ${cleanup}`));
  // The published image is the only reusable representation.  This exact normal-bootstrap
  // source is a disposable world, never a mutable cache entry.  A failed run retains it for
  // diagnosis instead of silently treating a partial image as success.
  if (completed) await rm(worldDirectory, { recursive: true, force: true });
  if (!completed) {
    const bundle = await writeFailureBundle({ project, output, scenarioPath, runId, failure,
      timing: { runner: 'f0va-development-fixture-bootstrap', status: 'failed' }, build: identity,
      process: { world, port, serverPid: server?.pid ?? null }, worldDirectory, lifecycleDirectory: lifecycle.directory });
    console.error(`PMV3_FIXTURE failure_bundle=${bundle}`);
  }
}
console.log(JSON.stringify({ status: 'ok', report: output, runId }));

async function prepareNormalWorld() {
  const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
  const child = spawn(gradle, [':pale-mirror-neoforge:prepareFrontierV3PilotServerWorld', '--offline', '--no-daemon',
    `-PfrontierV3PilotWorld=${world}`, `-PfrontierV3PilotSeed=${scenario.isolation.seed}`,
    `-PfrontierV3PilotPort=${port}`, `-PfrontierV3PilotRconPort=${port + 1}`,
    `-PfrontierV3PilotUsername=${scenario.pilot.username}`, '-PfrontierV3PilotReset=true',
    `-PfrontierV3PilotRunId=${runId}`, `-PfrontierV3PilotProfile=${scenario.server.profile ?? defaultPilotProfile()}`,
    `-PfrontierV3PilotViewDistance=${scenario.server.viewDistance ?? 10}`], {
    cwd: project, env: { ...process.env, FRONTIER_V3_PILOT_RCON_PASSWORD: rconPassword }, stdio: 'inherit'
  });
  if (await exited(child) !== 0) throw new Error('normal fixture world preparation failed');
}

async function startServer() {
  await requirePreparedF0vBuild(project, identity);
  const launch = await preparedLaunch(project, identity, 'server', {
    'pale_mirror.frontier_v3.enabled': 'true', 'pale_mirror.frontier_v3.pilot.run_id': runId,
    'pale_mirror.frontier_v3.pilot.lifecycle_control_directory': lifecycle.directory,
    'pale_mirror.frontier_v3.pilot.profile': scenario.server.profile ?? defaultPilotProfile()
  });
  const child = spawn(launch.command, launch.args, { cwd: launch.cwd,
    env: { ...process.env, FRONTIER_V3_PILOT_RCON_PASSWORD: rconPassword }, stdio: ['ignore', 'pipe', 'pipe'] });
  for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => process.stdout.write(chunk));
  const ready = await signalOrExit(child, LifecycleSignal.SERVER_RUN_READY, runId, 'fixture server readiness');
  if (ready.detail.serverRunId !== runId || !Number.isInteger(ready.detail.serverPid) || ready.detail.serverPid <= 1) {
    throw new Error('fixture server readiness signal does not name the exact server JVM');
  }
  return Object.freeze({ child, pid: ready.detail.serverPid, ready });
}

async function stopServer(active) {
  await requestRconStop({ port: port + 1, password: rconPassword });
  const saved = await signalOrExit(active.child, LifecycleSignal.DURABLE_SERVER_SAVE, runId, 'fixture durable server save');
  if (saved.detail.serverRunId !== runId) throw new Error('fixture durable-save signal is foreign');
  await exitWithin(active.child, 90_000, 'fixture server');
  if (await portOpen(port)) throw new Error('fixture server exited but its reserved port remains open');
  return Object.freeze({ serverRunId: runId, durableSave: true, portClosed: true,
    lifecycleSha256: hash(JSON.stringify({ ready: active.ready, durable: saved })) });
}

async function stopFailedServer(active) {
  try { await requestRconStop({ port: port + 1, password: rconPassword, timeoutMs: 10_000 }); }
  catch { /* exact process shutdown follows */ }
  try { process.kill(active.pid, 'SIGTERM'); } catch (error) { if (error?.code !== 'ESRCH') throw error; }
  await exitWithin(active.child, 30_000, 'failed fixture server');
  if (await portOpen(port)) throw new Error('failed fixture server retained its reserved port');
}

async function signalOrExit(child, signal, suffix, label) {
  const pending = awaitLifecycleSignal(lifecycle, signal, suffix, 180_000);
  const first = await Promise.race([pending.then((value) => ({ value })), exited(child).then((code) => ({ code }))]);
  if ('value' in first) return first.value;
  try { return await Promise.race([pending, timeout(1_000)]); }
  catch { throw new Error(`${label} was not acknowledged before exact JVM exit (${first.code})`); }
}
async function exitWithin(child, timeoutMs, label) { await Promise.race([exited(child), timeout(timeoutMs)]); }
function exited(child) { return child.exitCode !== null || child.signalCode !== null ? Promise.resolve(child.exitCode ?? 1) : new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1))); }
function timeout(milliseconds) { return new Promise((_, reject) => setTimeout(() => reject(new Error(`fixture bootstrap timeout after ${milliseconds}ms`)), milliseconds)); }
function portOpen(candidate) { return new Promise((resolveOpen) => { const socket = createConnection({ host: '127.0.0.1', port: candidate }); const finish = (open) => { socket.removeAllListeners(); socket.destroy(); resolveOpen(open); }; socket.once('connect', () => finish(true)); socket.once('error', () => finish(false)); socket.setTimeout(500, () => finish(false)); }); }

async function fixtureMetadata({ identity: build, format: declared, scenario: value, scenarioSha256 }) {
  if (!declared || typeof declared !== 'object' || Array.isArray(declared) || typeof declared.imageId !== 'string') throw new Error('fixture format needs an imageId');
  const source = await fingerprintWorkingContent(project, ['pale-mirror-frontier/', 'pale-mirror-neoforge/', 'tools/frontier-v3-test-pilot/',
    'architecture.yml', 'gradle.properties', 'settings.gradle']);
  const metadata = { sourceContentSha256: source.sha256, artifactSha256: build.preparedArtifact?.sha256, serverClasspathSha256: build.classpaths?.server?.sha256,
    clientClasspathSha256: build.classpaths?.client?.sha256, launchManifestSha256: build.launchManifest?.sha256,
    snapshotSchema: declared.snapshotSchema, walEnvelope: declared.walEnvelope, rulesetId: declared.rulesetId,
    rulesetSha256: declared.rulesetSha256, seed: value.isolation.seed, profile: value.server.profile ?? defaultPilotProfile(),
    viewDistance: value.server.viewDistance ?? 10, fixtureDeclarationSha256: scenarioSha256 };
  // The image module owns structural metadata validation.  Calling it only after the direct
  // server lifecycle prevents a user-supplied format file from becoming bootstrap authority.
  return metadata;
}
function safeProjectPath(candidate, label) { if (typeof candidate !== 'string') throw new Error(`${label} is malformed`); const target = resolve(project, candidate); const path = relative(project, target); if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} escapes project`); return target; }
function safeBuildPath(candidate, label) { const target = safeProjectPath(candidate, label); const path = relative(resolve(project, 'build'), target); if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} must remain under build/`); return target; }
async function absent(path, label) { try { await stat(path); throw new Error(`refusing to overwrite ${label}: ${path}`); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
async function writeExclusive(path, contents) { await mkdir(dirname(path), { recursive: true }); await writeFile(path, contents, { encoding: 'utf8', flag: 'wx' }); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
