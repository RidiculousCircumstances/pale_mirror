import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { createConnection } from 'node:net';
import { basename, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defaultPilotProfile, jfrCaptureRequest, loadScenario, logOffsetAfterMarker, pilotServerPid, pilotServerReady, restartSegments } from './scenario.mjs';

const [scenarioPath, outputPath = `build/frontier-v3-scenarios/${basename(process.argv[2] ?? 'scenario.json', '.json')}-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioPath) throw new Error('usage: npm run scenario:isolated -- <scenario.json> [manifest.json]');
if (!process.env.DISPLAY) throw new Error('a native visible pilot requires DISPLAY=:0');

const sourcePath = resolve(scenarioPath);
const { scenario } = await loadScenario(sourcePath);
if (scenario.isolation?.mode !== 'disposable_lite') throw new Error('isolated runner requires isolation.mode=disposable_lite');
const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const jfr = jfrCaptureRequest(process.env, project);
const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
const runId = randomUUID();
const port = Number(process.env.FRONTIER_V3_PILOT_PORT ?? 25575);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('FRONTIER_V3_PILOT_PORT must be 1024..65535');
// The disposable runner owns this listener exclusively.  Do not let Gradle/Minecraft discover
// a stale pilot JVM only after it has created a fresh world directory and emitted a crash log.
if (await portOpen(port)) throw new Error(`disposable v3 pilot port ${port} is already occupied; stop the exact previous pilot server first`);
const world = `v3-${scenario.id.replace(/[^a-z0-9_-]/g, '-').slice(0, 36)}-${runId.slice(0, 8)}`;
const output = resolve(project, outputPath);
const ephemeralScenario = resolve(project, `build/frontier-v3-scenarios/${runId}-scenario.json`);
const beforeRestartScenario = resolve(project, `build/frontier-v3-scenarios/${runId}-before-restart.json`);
const afterRestartScenario = resolve(project, `build/frontier-v3-scenarios/${runId}-after-restart.json`);
const beforeRestartManifest = output.replace(/\.json$/i, '') + '.before-restart.json';
const disposableWorld = resolve(project, `pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/${world}`);
const serverLog = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/logs/latest.log');
// A loaded 1024×1024 graybox may legitimately take longer than the former
// 45-second budget to flush every vanilla dimension.  This is an observation
// timeout only: deletion still waits for Minecraft's own durable marker.
const DURABLE_STOP_TIMEOUT_MS = 90_000;
await mkdir(dirname(ephemeralScenario), { recursive: true });
let server = null;
let completed = false;
let abruptStopAttempted = false;
try {
  const recovery = restartSegments(scenario);
  server = await startServer(true);
  if (jfr !== undefined) await startJfrCapture(server, jfr);
  if (recovery == null) {
    await writeScenario(ephemeralScenario, scenario);
    await runPilot(ephemeralScenario, output, server);
  } else {
    await writeScenario(beforeRestartScenario, recovery.before);
    await runPilot(beforeRestartScenario, beforeRestartManifest, server);
    console.log(`PMV3_ISOLATED recovery=before-complete mode=${recovery.mode}`);
    if (recovery.mode === 'graceful') await stopServerSafely(server, serverLog, server.logOffset, port);
    else {
      abruptStopAttempted = true;
      await stopServerAbruptly(server, port);
    }
    console.log('PMV3_ISOLATED recovery=server-stopped');
    server = null;
    server = await startServer(false);
    console.log('PMV3_ISOLATED recovery=server-restarted');
    // The replacement server is a normal live JVM and must receive the
    // ordinary durable stop path if the after-restart pilot fails.
    abruptStopAttempted = false;
    await writeScenario(afterRestartScenario, recovery.after);
    await runPilot(afterRestartScenario, output, server);
    console.log('PMV3_ISOLATED recovery=after-complete');
    const manifest = JSON.parse(await readFile(output, 'utf8'));
    manifest.recovery = { mode: recovery.mode, world, splitAfterAction: scenario.restart.afterAction, beforeRestartManifest };
    await writeFile(output, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  }
  if (jfr !== undefined) await awaitJfrEvidence(jfr);
  completed = true;
} finally {
  if (server != null && !abruptStopAttempted) await stopServerForCleanup(server, serverLog, server.logOffset, port);
  await Promise.all([ephemeralScenario, beforeRestartScenario, afterRestartScenario].map((path) => rm(path, { force: true })));
  // A failed recovery run is diagnostic evidence.  In particular, never erase
  // the session.lock/world that prevented the next server from starting.
  if (completed && process.env.FRONTIER_V3_KEEP_DISPOSABLE !== 'true') await rm(disposableWorld, { recursive: true, force: true });
}

console.log(JSON.stringify({ status: 'ok', profile: 'disposable_lite', world, port, manifest: output, jfr: jfr?.output }));

async function startServer(reset) {
  const serverRunId = randomUUID();
  const serverArgs = [':pale-mirror-neoforge:runFrontierV3PilotServer', '--no-daemon',
    `-PfrontierV3PilotWorld=${world}`, `-PfrontierV3PilotSeed=${scenario.isolation.seed}`,
    `-PfrontierV3PilotPort=${port}`, `-PfrontierV3PilotUsername=${scenario.pilot.username}`,
    `-PfrontierV3PilotReset=${reset}`, `-PfrontierV3PilotRunId=${serverRunId}`,
    `-PfrontierV3PilotProfile=${scenario.server.profile ?? defaultPilotProfile()}`,
    `-PfrontierV3PilotViewDistance=${scenario.server.viewDistance ?? 10}`];
  const child = spawn(gradle, serverArgs, {
    cwd: project, env: process.env, stdio: ['pipe', 'pipe', 'pipe']
  });
  let output = '';
  for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => { process.stdout.write(chunk); output += chunk; });
  const session = { child, output: () => output, logOffset: 0, serverRunId, serverPid: undefined };
  await waitForServer(session, 180_000);
  session.serverPid = pilotServerPid(output, serverRunId);
  if (!Number.isInteger(session.serverPid)) throw new Error('disposable v3 server did not announce its exact JVM identity');
  session.logOffset = await logOffsetAfter(serverLog, `PMV3_PILOT_SERVER runId=${serverRunId}`, 10_000);
  return session;
}

async function startJfrCapture(server, request) {
  await mkdir(dirname(request.output), { recursive: true });
  try {
    await stat(request.output);
    throw new Error(`refusing to overwrite existing JFR evidence: ${request.output}`);
  } catch (failure) {
    if (failure?.code !== 'ENOENT') throw failure;
  }
  const script = resolve(project, 'scripts/capture-runtime-jfr.sh');
  const capture = spawn(script, [String(server.serverPid), request.duration, request.output], {
    cwd: project, env: process.env, stdio: 'inherit'
  });
  if (await exited(capture) !== 0) throw new Error('disposable v3 JFR capture could not be scheduled');
  console.log(`PMV3_ISOLATED jfr=scheduled pid=${server.serverPid} duration=${request.duration} output=${request.output}`);
}

async function awaitJfrEvidence(request) {
  const duration = Number.parseInt(request.duration, 10);
  const unit = request.duration.at(-1);
  const durationMs = duration * (unit === 'h' ? 3_600_000 : unit === 'm' ? 60_000 : 1_000);
  const deadline = Date.now() + durationMs + 30_000;
  while (Date.now() < deadline) {
    try {
      if ((await stat(request.output)).size > 0) return;
    } catch (failure) {
      if (failure?.code !== 'ENOENT') throw failure;
    }
    await timeout(250);
  }
  throw new Error(`JFR evidence was not flushed after ${request.duration}: ${request.output}`);
}

function waitForServer(session, timeoutMs) {
  return new Promise((resolveReady, reject) => {
    let settled = false;
    const settle = (callback, value) => { if (!settled) { settled = true; clearInterval(timer); clearTimeout(alarm); callback(value); } };
    const timer = setInterval(() => { if (pilotServerReady(session.output(), session.serverRunId)) settle(resolveReady); }, 100);
    const alarm = setTimeout(() => settle(reject, new Error(`disposable v3 server did not become ready within ${timeoutMs}ms`)), timeoutMs);
    session.child.once('exit', (code) => settle(reject, new Error(`disposable v3 server exited before ready (${code})`)));
  });
}
function exited(child) {
  // A short native pilot can finish before the parent reaches cleanup.  Node
  // records that terminal state, so subscribe only while it is genuinely
  // running; otherwise a late `once('exit')` would wait forever.
  if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve(child.exitCode ?? 1);
  return new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1)));
}
async function runPilot(scenarioFile, manifest, server) {
  const pilot = spawn(process.execPath, [resolve(dirname(fileURLToPath(import.meta.url)), 'run-scenario.mjs'), scenarioFile, manifest], {
    cwd: project, env: { ...process.env, FRONTIER_V3_PILOT_PROFILE: 'lite', FRONTIER_V3_GRADLE: gradle }, stdio: 'inherit'
  });
  const code = await exited(pilot);
  if (code !== 0) throw new Error(`isolated native pilot exited with ${code}`);
  // The outer scenario runner closes its normal client only after it receives
  // the final read-only assertion. Some NeoForge versions do not emit the
  // vanilla leave line until their next network flush, so this is an
  // optimization/evidence point rather than a false failure condition; the
  // subsequent stop still requires Minecraft's own all-dimensions-saved
  // acknowledgement.
  await waitForLog(serverLog, server.logOffset, `${scenario.pilot.username} left the game`, 2_000);
}

async function writeScenario(path, value) {
  await writeFile(path, `${JSON.stringify({ ...value, server: { ...value.server, host: '127.0.0.1', port } }, null, 2)}\n`, 'utf8');
}

async function stopServerSafely(server, logPath, offset, serverPort) {
  // Gradle's JavaExec wrapper is not the world owner. Sending a signal to it
  // can fan out more than one shutdown signal to its child, which leaves
  // Minecraft re-entering chunk-unload/save work while the runner mistakes the
  // wrapper's lifecycle for the server's lifecycle. The exact JVM nonce is
  // therefore the sole graceful-stop target, just as it is for abrupt recovery.
  // A request is never evidence: only Minecraft's own durable marker below may
  // authorize a restart or cleanup.
  requestGracefulStop(server.serverPid);
  const stopped = await waitForLog(logPath, offset, 'ThreadedAnvilChunkStorage: All dimensions are saved', DURABLE_STOP_TIMEOUT_MS);
  if (!stopped) throw new Error('disposable v3 server did not confirm a flushed world; preserving it for diagnosis');
  // The Gradle wrapper may linger after its dedicated Minecraft child has
  // flushed and closed.  A second JVM must not open the same world until the
  // actual game listener is gone; conversely, waiting on an unrelated wrapper
  // forever gives no stronger persistence guarantee.
  if (!await waitForPortClosed(serverPort, DURABLE_STOP_TIMEOUT_MS)) {
    throw new Error('disposable v3 server flushed but still owns its game port; preserving it for diagnosis');
  }
  // The launcher has no state authority once the exact Minecraft listener is
  // closed and its save marker exists. It is a Gradle process, not a world
  // process: waiting for it can deadlock a valid recovery run while adding no
  // persistence evidence. Ask it to exit and release its stdio handles, but
  // let the next exact server lifecycle be governed solely by the durable
  // marker and closed game port above.
  releaseWrapper(server.child);
}
async function stopServerForCleanup(server, logPath, offset, serverPort) {
  try {
    await stopServerSafely(server, logPath, offset, serverPort);
  } catch (failure) {
    // Keep the failed world and its manifest for forensic recovery, but never leave the exact
    // disposable JVM alive. A later scenario must not inherit its port or its loaded world.
    // This fallback has no listener/name scan: it can affect only the nonce-announced JVM that
    // this runner created. The original graceful-stop failure still reaches the caller.
    if (await portOpen(serverPort)) {
      killIfPresent(server.serverPid);
      if (!await waitForPortClosed(serverPort, 45_000)) {
        throw new Error(`disposable v3 cleanup could not stop its exact JVM after graceful failure: ${failure.message}`);
      }
    }
    releaseWrapper(server.child);
    throw failure;
  }
}
async function stopServerAbruptly(server, serverPort) {
  if (!Number.isInteger(server.serverPid) || server.serverPid <= 1) throw new Error('disposable server did not expose an exact JVM identity');
  // The nonce is passed only to this Gradle RunGame task and is echoed by that
  // JVM at server start. Kill that exact Minecraft process, not its launcher,
  // a name match, a listener lookup, or a broad process group.
  killIfPresent(server.serverPid);
  if (!await waitForPortClosed(serverPort, 45_000)) {
    throw new Error(`abruptly stopped exact disposable JVM but game port ${serverPort} remained open; preserving world for diagnosis`);
  }
  // TCP closure comes from that exact JVM. Give its OS file lock one scheduler
  // turn to release before the recovery JVM opens this world.
  await timeout(250);
  releaseWrapper(server.child);
}
function releaseWrapper(child) {
  if (child.exitCode === null && child.signalCode === null) child.kill('SIGTERM');
  child.stdout?.destroy(); child.stderr?.destroy(); child.unref();
}
function killIfPresent(pid) {
  try { process.kill(pid, 'SIGKILL'); }
  catch (failure) { if (failure.code !== 'ESRCH') throw new Error(`could not abruptly stop exact disposable process ${pid}: ${failure}`); }
}
function requestGracefulStop(pid) {
  if (!Number.isInteger(pid) || pid <= 1) throw new Error('disposable server did not expose an exact JVM identity');
  try { process.kill(pid, 'SIGTERM'); }
  catch (failure) { if (failure.code !== 'ESRCH') throw new Error(`could not gracefully stop exact disposable JVM ${pid}: ${failure}`); }
}
async function logOffsetAfter(path, marker, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const output = await readFile(path, 'utf8');
      const offset = logOffsetAfterMarker(output, marker);
      if (offset !== undefined) return offset;
    } catch { /* The exact server has not created its current log line yet. */ }
    await timeout(100);
  }
  throw new Error(`disposable v3 server did not write its exact run marker within ${timeoutMs}ms`);
}
async function waitForLog(path, offset, marker, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const output = await readFile(path, 'utf8');
      // Minecraft truncates/rotates `latest.log` at a fresh launch.  An old
      // byte offset must then mean "the whole new file", not an impossible
      // suffix past EOF.
      if (output.slice(output.length < offset ? 0 : offset).includes(marker)) return true;
    } catch { /* The server has not created its log yet. */ }
    await timeout(100);
  }
  return false;
}
async function waitForPortClosed(serverPort, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (!await portOpen(serverPort)) return true;
    await timeout(100);
  }
  return false;
}
function portOpen(serverPort) {
  return new Promise((resolveOpen) => {
    const socket = createConnection({ host: '127.0.0.1', port: serverPort });
    const finish = (open) => { socket.removeAllListeners(); socket.destroy(); resolveOpen(open); };
    socket.once('connect', () => finish(true));
    socket.once('error', () => finish(false));
    socket.setTimeout(500, () => finish(false));
  });
}
function timeout(ms) { return new Promise((resolveTimeout) => setTimeout(() => resolveTimeout(null), ms)); }
