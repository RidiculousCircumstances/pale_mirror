import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { watch } from 'node:fs';
import { mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { createConnection } from 'node:net';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defaultPilotProfile, jfrCaptureRequest, loadScenario, pilotCrashBoundary, restartSegments } from './scenario.mjs';
import { requestRconStop } from './rcon.mjs';
import { PhaseTiming } from './timing.mjs';
import { writeFailureBundle } from './failure-bundle.mjs';
import { createCrashController } from './crash-controller.mjs';
import { fingerprintPreparedBuild, fingerprintPreparedSource, requirePreparedF0vBuild } from './prepared-build.mjs';
import { preparedLaunch } from './prepared-launch.mjs';
import { LifecycleBarrier, LifecycleSignal, awaitLifecycleBarrier, awaitLifecycleSignal, createLifecycleBarrierSession, newLifecycleIdentity, publishLifecycleBarrier } from './lifecycle-barrier.mjs';
import { awaitChildExit, awaitWithin, childExitWatch, deadlineWatchdog } from './deadline-watchdog.mjs';

const [scenarioPath, outputPath = `build/frontier-v3-scenarios/${basename(process.argv[2] ?? 'scenario.json', '.json')}-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioPath) throw new Error('usage: npm run scenario:isolated -- <scenario.json> [manifest.json]');
if (!process.env.DISPLAY) throw new Error('a native visible pilot requires DISPLAY=:0');

const sourcePath = resolve(scenarioPath);
const { scenario } = await loadScenario(sourcePath);
if (scenario.isolation?.mode !== 'disposable_lite') throw new Error('isolated runner requires isolation.mode=disposable_lite');
// A post-recovery rendezvous would need a third explicitly declared recovery segment. Reject it
// instead of replaying ordinary player actions after a killed JVM and manufacturing evidence.
if (scenario.crash?.phase === 'after_restart') {
  throw new Error('F0.V crash scenarios currently require phase=before_restart with one non-replayed recovery half');
}
const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const timing = new PhaseTiming();
const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
timing.begin('source.build_identity_resolution');
const buildIdentity = await resolvePreparedBuild(project, gradle);
timing.end('source.build_identity_resolution');
const jfr = jfrCaptureRequest(process.env, project);
const runId = randomUUID();
const lifecycle = await createLifecycleBarrierSession(resolve(project, 'build/frontier-v3-scenarios'), newLifecycleIdentity({
  buildIdentitySha256: createHash('sha256').update(JSON.stringify(buildIdentity)).digest('hex'),
  workerId: process.env.FRONTIER_V3_PILOT_WORKER_ID ?? 'local-standalone', runId, scenarioId: scenario.id,
  segmentId: 'native-scenario'
}));
const preparedIdentityPath = process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY === undefined
  || process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY === ''
  ? resolve(project, `build/frontier-v3-scenarios/${runId}-prepared-build.json`)
  : resolve(project, process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY);
if (process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY === undefined || process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY === '') {
  await mkdir(dirname(preparedIdentityPath), { recursive: true });
  await writeFile(preparedIdentityPath, `${JSON.stringify(buildIdentity, null, 2)}\n`, 'utf8');
}
const port = Number(process.env.FRONTIER_V3_PILOT_PORT ?? 25575);
if (!Number.isInteger(port) || port < 1024 || port >= 65535) throw new Error('FRONTIER_V3_PILOT_PORT must be 1024..65534');
const rconPort = port + 1;
const rconPassword = randomUUID();
// The disposable runner owns this listener exclusively.  Do not let Gradle/Minecraft discover
// a stale pilot JVM only after it has created a fresh world directory and emitted a crash log.
if (await portOpen(port)) throw new Error(`disposable v3 pilot port ${port} is already occupied; stop the exact previous pilot server first`);
if (await portOpen(rconPort)) throw new Error(`disposable v3 pilot RCON port ${rconPort} is already occupied; stop the exact previous pilot server first`);
const world = `v3-${scenario.id.replace(/[^a-z0-9_-]/g, '-').slice(0, 36)}-${runId.slice(0, 8)}`;
const output = resolve(project, outputPath);
const ephemeralScenario = resolve(project, `build/frontier-v3-scenarios/${runId}-scenario.json`);
const beforeRestartScenario = resolve(project, `build/frontier-v3-scenarios/${runId}-before-restart.json`);
const afterRestartScenario = resolve(project, `build/frontier-v3-scenarios/${runId}-after-restart.json`);
const sessionDirectory = resolve(project, `build/frontier-v3-scenarios/${runId}-persistent-session`);
const sessionScenario = resolve(sessionDirectory, 'runtime-scenario.json');
const beforeRestartManifest = output.replace(/\.json$/i, '') + '.before-restart.json';
const disposableWorld = resolve(project, `pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/${world}`);
const serverLog = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/logs/latest.log');
const clientLog = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-client/logs/latest.log');
// A loaded 1024×1024 graybox may legitimately take longer than the former
// 45-second budget to flush every vanilla dimension.  This is an observation
// timeout only: deletion still waits for Minecraft's own durable marker.
const DURABLE_STOP_TIMEOUT_MS = 90_000;
await mkdir(dirname(ephemeralScenario), { recursive: true });
let server = null;
// A direct prepared Java launch is its own exact JVM.  Retain its PID before readiness so a
// deterministic failed start can be stopped and attributed without name scans or a leaked port.
let lastServerAttempt = null;
let completed = false;
let abruptStopAttempted = false;
const clientSegments = [];
let recoveryMetadata = null;
// A crash intentionally destroys the socket. Its recovery uses a fresh ordinary client; the
// one-client persistent-restart proof stays a separate F0.V.4 scenario.
const usePersistentClient = process.env.FRONTIER_V3_PILOT_USE_PERSISTENT_CLIENT !== 'false' && scenario.crash === undefined;
let crashEvidence = null;
let failure = null;
timing.begin('scenario.total');
try {
  const recovery = restartSegments(scenario);
  server = await startServer(true);
  if (jfr !== undefined) await startJfrCapture(server, jfr);
  if (recovery == null) {
    await writeScenario(ephemeralScenario, scenario);
    await runPilot(ephemeralScenario, output, server, clientSegments, 'initial');
  } else if (!usePersistentClient) {
    await writeScenario(beforeRestartScenario, recovery.before);
    if (scenario.crash !== undefined) {
      crashEvidence = await runPilotUntilCrash(beforeRestartScenario, beforeRestartManifest, server, clientSegments, 'before_restart', scenario.crash);
      server = null;
    } else {
      await runPilot(beforeRestartScenario, beforeRestartManifest, server, clientSegments, 'before_restart');
    }
    console.log(`PMV3_ISOLATED recovery=before-complete mode=${recovery.mode}`);
    if (server !== null) {
      if (recovery.mode === 'graceful') {
        await timedStop('graceful_save_and_port_close', () => stopServerSafely(server, port));
        await publishLifecycleBarrier(lifecycle, LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: server.serverRunId });
        await publishLifecycleBarrier(lifecycle, LifecycleBarrier.GAME_PORT_CLOSED, { port });
      }
      else {
        abruptStopAttempted = true;
        await timedStop('abrupt_exact_jvm_stop', () => stopServerAbruptly(server, port));
        await publishLifecycleBarrier(lifecycle, LifecycleBarrier.GAME_PORT_CLOSED, { port });
      }
      server = null;
    }
    console.log('PMV3_ISOLATED recovery=server-stopped');
    server = await startServer(false);
    console.log('PMV3_ISOLATED recovery=server-restarted');
    // The replacement server is a normal live JVM and must receive the
    // ordinary durable stop path if the after-restart pilot fails.
    abruptStopAttempted = false;
    await writeScenario(afterRestartScenario, recovery.after);
    await runPilot(afterRestartScenario, output, server, clientSegments, 'after_restart');
    console.log('PMV3_ISOLATED recovery=after-complete');
    recoveryMetadata = { mode: recovery.mode, world, splitAfterAction: scenario.restart.afterAction, beforeRestartManifest,
      crash: crashEvidence };
  } else {
    await mkdir(sessionDirectory, { recursive: true });
    await writeFile(resolve(sessionDirectory, 'run-id'), `${runId}\n`, 'utf8');
    await writeScenario(sessionScenario, recovery.before);
    await requirePreparedF0vBuild(project, buildIdentity);
    const persistentPilot = startPersistentPilot(sourcePath, output, sessionScenario, sessionDirectory);
    await awaitLifecycleBarrierFromPilot(LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, 300_000, persistentPilot,
      (entry) => entry.detail.segment === 'before_restart');
    await awaitLifecycleBarrierFromPilot(LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, 300_000, persistentPilot,
      (entry) => entry.detail.segment === 'before_restart');
    console.log(`PMV3_ISOLATED recovery=before-complete mode=${recovery.mode} client=persistent`);
    if (recovery.mode === 'graceful') {
      await timedStop('graceful_save_and_port_close', () => stopServerSafely(server, port));
      await publishLifecycleBarrier(lifecycle, LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: server.serverRunId });
      await publishLifecycleBarrier(lifecycle, LifecycleBarrier.GAME_PORT_CLOSED, { port });
    } else {
      abruptStopAttempted = true;
      await timedStop('abrupt_exact_jvm_stop', () => stopServerAbruptly(server, port));
      await publishLifecycleBarrier(lifecycle, LifecycleBarrier.GAME_PORT_CLOSED, { port });
    }
    console.log('PMV3_ISOLATED recovery=server-stopped');
    server = null;
    server = await startServer(false);
    console.log('PMV3_ISOLATED recovery=server-restarted');
    abruptStopAttempted = false;
    await writeScenario(sessionScenario, recovery.after);
    // `resumed` is durable test-pilot control evidence while `resume` is the exact nonce the
    // client validates before asking Minecraft to make one ordinary new connection.
    await writeFile(resolve(sessionDirectory, 'resumed'), `${runId}\n`, 'utf8');
    await writeFile(resolve(sessionDirectory, 'resume'), `${runId}\n`, 'utf8');
    await awaitLifecycleBarrierFromPilot(LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, 300_000, persistentPilot,
      (entry) => entry.detail.clientPid === persistentPilot.child.pid);
    await finishPersistentPilot(persistentPilot, clientSegments);
    console.log('PMV3_ISOLATED recovery=after-complete client=persistent');
    recoveryMetadata = { mode: recovery.mode, world, splitAfterAction: scenario.restart.afterAction,
      clientSession: { runId, reusedJvm: true, controlDirectory: sessionDirectory } };
  }
  if (jfr !== undefined) await awaitJfrEvidence(jfr);
  completed = true;
} catch (error) {
  failure = error;
  throw error;
} finally {
  timing.end('scenario.total');
  timing.begin('cleanup');
  if (server != null && !abruptStopAttempted) await stopServerForCleanup(server, port);
  await Promise.all([ephemeralScenario, beforeRestartScenario, afterRestartScenario].map((path) => rm(path, { force: true })));
  if (completed) await rm(sessionDirectory, { recursive: true, force: true });
  // A failed recovery run is diagnostic evidence.  In particular, never erase
  // the session.lock/world that prevented the next server from starting.
  if (completed && process.env.FRONTIER_V3_KEEP_DISPOSABLE !== 'true') await rm(disposableWorld, { recursive: true, force: true });
  timing.end('cleanup');
  timing.abortOpen({ status: failure === null ? 'ok' : 'failed' });
  if (failure !== null) {
    const decodedWalTail = await decodeWalTail(disposableWorld);
    const diagnosticSnapshots = await retainedDiagnostics([output, beforeRestartManifest]);
    const bundle = await writeFailureBundle({ project, output, scenarioPath: sourcePath, runId,
      timing: timing.finish({ runner: 'isolated-native', status: 'failed' }), failure, serverLog,
      clientLog, tracePath: output.replace(/\.json$/i, '') + '.pmv3.jsonl', worldDirectory: disposableWorld,
      build: buildIdentity, crash: crashEvidence,
      process: { world, port, serverRunId: server?.serverRunId ?? lastServerAttempt?.serverRunId ?? null,
        serverPid: server?.serverPid ?? lastServerAttempt?.serverPid ?? null,
        startupLifecycle: server?.startupLifecycle ?? lastServerAttempt?.startupLifecycle ?? null },
      termination: { portClosed: !await portOpen(port), abruptStopAttempted }, decodedWalTail, diagnosticSnapshots,
      lifecycleDirectory: lifecycle.directory });
    console.error(`PMV3_ISOLATED failure_bundle=${bundle}`);
  }
}

if (completed) {
  const manifest = JSON.parse(await readFile(output, 'utf8'));
  // This is correlation evidence only.  The runner remains the sole owner of
  // the disposable directory and still removes it after a successful run.
  // Independent-matrix proof needs the exact distinct world identities even
  // for a one-server scenario that has no recovery record.
  manifest.isolation = { world, port, freshWorld: true };
  manifest.recovery = recoveryMetadata;
  manifest.build = buildIdentity;
  manifest.clientSegments = clientSegments;
  manifest.timing = timing.finish({ runner: 'isolated-native', restartMode: recoveryMetadata?.mode ?? 'none' });
  await writeFile(output, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
}

console.log(JSON.stringify({ status: 'ok', profile: 'disposable_lite', world, port, manifest: output, jfr: jfr?.output }));

async function startServer(reset) {
  await requirePreparedF0vBuild(project, buildIdentity);
  const serverRunId = randomUUID();
  const crash = crashForServer(reset);
  if (reset) {
    const serverArgs = [':pale-mirror-neoforge:prepareFrontierV3PilotServerWorld', '--offline', '--no-daemon',
      `-PfrontierV3PilotWorld=${world}`, `-PfrontierV3PilotSeed=${scenario.isolation.seed}`,
      `-PfrontierV3PilotPort=${port}`, `-PfrontierV3PilotRconPort=${rconPort}`,
      `-PfrontierV3PilotUsername=${scenario.pilot.username}`,
      '-PfrontierV3PilotReset=true', `-PfrontierV3PilotRunId=${serverRunId}`,
      `-PfrontierV3PilotProfile=${scenario.server.profile ?? defaultPilotProfile()}`,
      `-PfrontierV3PilotViewDistance=${scenario.server.viewDistance ?? 10}`];
    if (crash !== undefined) {
      serverArgs.push(`-PfrontierV3PilotCrashBoundary=${crash.boundary}`, `-PfrontierV3PilotCrashOwner=${crash.owner}`,
        `-PfrontierV3PilotCrashRevision=${crash.expectedRevision}`, `-PfrontierV3PilotCrashPayload=${crash.payloadType}`);
    }
    const prepared = spawn(gradle, serverArgs, {
      cwd: project, env: { ...process.env, FRONTIER_V3_PILOT_RCON_PASSWORD: rconPassword }, stdio: 'inherit'
    });
    if (await exited(prepared) !== 0) throw new Error('disposable v3 server world preparation failed');
  } else {
    // Recovery opens the exact already-durable world with a fresh Minecraft JVM.  Re-running
    // Gradle preparation here is neither a persistence check nor a build check: it is a second
    // mutable setup step which used to dominate the persistent-client benchmark.  Do not repair
    // or recreate the directory; a missing durable world is causal failure evidence.
    try {
      if (!(await stat(disposableWorld)).isDirectory()) throw new Error('not a directory');
    } catch (failure) {
      throw new Error(`disposable v3 recovery world is unavailable: ${disposableWorld}`, { cause: failure });
    }
  }
  // Initial preparation can reset only the disposable game directory.  Every launch then proves
  // the same artifact/classpath identity before a server JVM starts; recovery never resolves or
  // rebuilds a replacement world or launch work.
  await requirePreparedF0vBuild(project, buildIdentity);
  const phase = reset ? 'initial_server_jvm_boot' : 'recovery_server_jvm_boot';
  timing.begin(phase, { reset });
  const launch = await preparedLaunch(project, buildIdentity, 'server', {
    'pale_mirror.frontier_v3.enabled': 'true',
    'pale_mirror.frontier_v3.pilot.run_id': serverRunId,
    'pale_mirror.frontier_v3.pilot.lifecycle_control_directory': lifecycle.directory,
    'pale_mirror.frontier_v3.pilot.profile': scenario.server.profile ?? defaultPilotProfile(),
    ...(crash === undefined ? {} : {
      'pale_mirror.frontier_v3.pilot.crash.boundary': crash.boundary,
      'pale_mirror.frontier_v3.pilot.crash.owner': crash.owner,
      'pale_mirror.frontier_v3.pilot.crash.revision': crash.expectedRevision,
      'pale_mirror.frontier_v3.pilot.crash.payload': crash.payloadType
    })
  });
  const child = spawn(launch.command, launch.args, {
    cwd: launch.cwd, env: { ...process.env, FRONTIER_V3_PILOT_RCON_PASSWORD: rconPassword }, stdio: ['pipe', 'pipe', 'pipe']
  });
  let output = ''; let outputRevision = 0; const outputWaiters = new Set();
  const publishOutput = () => { outputRevision++; for (const resolveWaiter of outputWaiters) resolveWaiter(); outputWaiters.clear(); };
  const outputAfter = (revision) => outputRevision !== revision ? Promise.resolve() : new Promise((resolveWaiter) => outputWaiters.add(resolveWaiter));
  for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => { process.stdout.write(chunk); output += chunk; publishOutput(); });
  const session = { child, output: () => output, serverRunId, serverPid: child.pid,
    outputRevision: () => outputRevision, outputAfter, rconPort, rconPassword, crash, startupLifecycle: 'STARTING' };
  lastServerAttempt = session;
  try {
    const signal = await serverSignalOrExit(session, LifecycleSignal.SERVER_RUN_READY, serverRunId, 180_000, 'server readiness');
    if (signal.detail.serverRunId !== serverRunId || !Number.isInteger(signal.detail.serverPid) || signal.detail.serverPid <= 1) {
      throw new Error('pilot server readiness signal does not carry its exact JVM identity');
    }
    session.serverPid = signal.detail.serverPid;
  } catch (failure) {
    session.startupLifecycle = 'FAILED_BEFORE_READY';
    await stopUnreadyServer(session, port);
    throw failure;
  }
  timing.end(phase, { runId: serverRunId });
  await publishLifecycleBarrier(lifecycle, reset ? LifecycleBarrier.SERVER_RUN_READY : LifecycleBarrier.RECOVERY_SERVER_READY,
    { serverRunId, serverPid: session.serverPid });
  session.startupLifecycle = 'READY';
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
    const change = directoryChange(dirname(request.output), deadline);
    try {
      if ((await stat(request.output)).size > 0) { change.close(); return; }
    } catch (failure) {
      if (failure?.code !== 'ENOENT') { change.close(); throw failure; }
    }
    if (!await change.wait) break;
  }
  throw new Error(`JFR evidence was not flushed after ${request.duration}: ${request.output}`);
}

function exited(child) {
  // A short native pilot can finish before the parent reaches cleanup.  Node
  // records that terminal state, so subscribe only while it is genuinely
  // running; otherwise a late `once('exit')` would wait forever.
  if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve(child.exitCode ?? 1);
  return new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1)));
}
async function exitWithin(child, timeoutMs, name) {
  return await awaitChildExit(child, timeoutMs, `${name} did not exit within ${timeoutMs}ms`);
}
async function serverSignalOrExit(server, signal, suffix, timeoutMs, label) {
  const acknowledgement = awaitLifecycleSignal(lifecycle, signal, suffix, timeoutMs);
  const exit = childExitWatch(server.child);
  try {
    const first = await Promise.race([acknowledgement.then((value) => ({ value })), exit.wait.then((code) => ({ code }))]);
    if ('value' in first) return first.value;
    try { return await awaitWithin(acknowledgement, 1_000, `server exited before ${label} (${first.code})`); }
    catch { throw new Error(`disposable v3 ${label} was not acknowledged before exact JVM exit (${first.code})`); }
  } finally {
    exit.close();
  }
}
async function runPilot(scenarioFile, manifest, server, clientSegments, segment) {
  await requirePreparedF0vBuild(project, buildIdentity);
  const phase = `client_segment.${segment}`;
  timing.begin(phase);
  const pilot = spawn(process.execPath, [resolve(dirname(fileURLToPath(import.meta.url)), 'run-scenario.mjs'), scenarioFile, manifest], {
    cwd: project, env: { ...process.env, FRONTIER_V3_PILOT_PROFILE: 'lite', FRONTIER_V3_GRADLE: gradle,
      FRONTIER_V3_PREPARED_BUILD_IDENTITY: preparedIdentityPath,
      FRONTIER_V3_PILOT_LIFECYCLE_CONTROL_DIRECTORY: lifecycle.directory,
      FRONTIER_V3_PILOT_LIFECYCLE_SEGMENT: segment,
      FRONTIER_V3_PILOT_LIFECYCLE_TERMINAL: segment === 'before_restart' ? 'false' : 'true' }, stdio: 'inherit'
  });
  const code = await exited(pilot);
  timing.end(phase, { exitCode: code });
  if (code !== 0) throw new Error(`isolated native pilot exited with ${code}`);
  const clientManifest = JSON.parse(await readFile(manifest, 'utf8'));
  clientSegments.push({ segment, manifest, runId: clientManifest.runId, timing: clientManifest.timing });
}

/**
 * Runs ordinary pilot actions only until the server announces the exact post-WAL rendezvous.
 * The external controller consumes that durable observation and kills only the nonce-announced
 * JVM. Terminal claims are deliberately deferred to the non-replayed recovery half.
 */
async function runPilotUntilCrash(scenarioFile, manifest, server, clientSegments, segment, crash) {
  await requirePreparedF0vBuild(project, buildIdentity);
  if (server.crash !== crash) throw new Error('crash runner server arm did not retain its exact scenario declaration');
  const phase = `client_segment.${segment}`;
  timing.begin(phase, { expectedCrash: crash.boundary });
  const pilot = spawn(process.execPath, [resolve(dirname(fileURLToPath(import.meta.url)), 'run-scenario.mjs'), scenarioFile, manifest], {
    cwd: project, env: { ...process.env, FRONTIER_V3_PILOT_PROFILE: 'lite', FRONTIER_V3_GRADLE: gradle,
      FRONTIER_V3_PREPARED_BUILD_IDENTITY: preparedIdentityPath,
      FRONTIER_V3_PILOT_LIFECYCLE_CONTROL_DIRECTORY: lifecycle.directory,
      FRONTIER_V3_PILOT_LIFECYCLE_SEGMENT: segment }, stdio: 'inherit'
  });
  try {
    const observation = await waitForCrashBoundary(server, pilot, crash, 300_000);
    const controller = createCrashController({ runId: server.serverRunId, boundary: crash.boundary, owner: crash.owner,
      // The probe is already parked at the exact boundary.  Adopt its recorded atomic revision
      // before PID termination instead of guessing a global-world revision in the contract.
      payloadType: crash.payloadType, expectedRevision: crash.expectedRevision === 'observed_at_boundary'
        ? observation.revision : crash.expectedRevision,
      ...(crash.expectedAuthorityEpoch === undefined ? {} : { expectedAuthorityEpoch: crash.expectedAuthorityEpoch }),
      serverPid: server.serverPid });
    const fired = controller.fire(observation);
    await ownedServerExit(server, port, 45_000, `crash controller fired for exact JVM ${server.serverPid}`);
    releaseWrapper(server.child);
    const code = await exitWithin(pilot, 45_000, 'crash-disconnected native pilot');
    timing.end(phase, { exitCode: code, expectedCrash: true, boundary: crash.boundary });
    clientSegments.push({ segment, manifest, expectedCrash: true, exitCode: code, crash: { ...fired, observation } });
    return Object.freeze({ ...fired, observation, pilotExitCode: code });
  } catch (error) {
    timing.end(phase, { expectedCrash: false });
    if (pilot.exitCode === null && pilot.signalCode === null) pilot.kill('SIGINT');
    throw error;
  }
}

function crashForServer(reset) {
  if (scenario.crash === undefined) return undefined;
  return scenario.crash.phase === (reset ? 'before_restart' : 'after_restart') ? scenario.crash : undefined;
}

async function waitForCrashBoundary(server, pilot, crash, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const revision = server.outputRevision();
    const observation = pilotCrashBoundary(server.output(), server.serverRunId);
    if (observation !== undefined) {
      if (observation.boundary !== crash.boundary || observation.owner !== crash.owner
          || observation.payloadType !== crash.payloadType
          || (crash.expectedRevision !== 'observed_at_boundary' && observation.revision !== crash.expectedRevision)) {
        throw new Error('server emitted a durable crash boundary that does not match its exact declared arm');
      }
      return observation;
    }
    if (pilot.exitCode !== null || pilot.signalCode !== null) {
      throw new Error(`native pilot exited before the declared durable crash boundary (${pilot.exitCode ?? pilot.signalCode})`);
    }
    const remaining = deadline - Date.now();
    if (remaining <= 0) break;
    const watchdog = deadlineWatchdog(remaining,
      `server did not announce declared durable crash boundary ${crash.boundary} within ${timeoutMs}ms`);
    const pilotExit = childExitWatch(pilot);
    try {
      await Promise.race([server.outputAfter(revision), pilotExit.wait, watchdog.wait]);
    } finally {
      watchdog.close();
      pilotExit.close();
    }
  }
  throw new Error(`server did not announce declared durable crash boundary ${crash.boundary} within ${timeoutMs}ms`);
}

/** Starts exactly one visible Minecraft client JVM for both sides of a restart boundary. */
function startPersistentPilot(contractScenario, manifest, runtimeScenario, controlDirectory) {
  // The caller checked the exact same identity immediately before this one persistent JVM is
  // born.  Its after-restart half uses the already-running client, not a rebuilt replacement.
  const phase = 'client_segment.persistent_restart';
  timing.begin(phase, { reusableJvm: true });
  const child = spawn(process.execPath, [resolve(dirname(fileURLToPath(import.meta.url)), 'run-scenario.mjs'), contractScenario, manifest], {
    cwd: project,
    env: { ...process.env, FRONTIER_V3_PILOT_PROFILE: 'lite', FRONTIER_V3_GRADLE: gradle,
      FRONTIER_V3_PREPARED_BUILD_IDENTITY: preparedIdentityPath,
      FRONTIER_V3_PILOT_RUNTIME_SCENARIO: runtimeScenario,
      FRONTIER_V3_PILOT_SESSION_CONTROL_DIRECTORY: controlDirectory,
      FRONTIER_V3_PILOT_LIFECYCLE_CONTROL_DIRECTORY: lifecycle.directory },
    stdio: 'inherit'
  });
  return { child, manifest, phase };
}

async function finishPersistentPilot(pilot, clientSegments) {
  const code = await exited(pilot.child);
  timing.end(pilot.phase, { exitCode: code, reusableJvm: true });
  if (code !== 0) throw new Error(`persistent isolated native pilot exited with ${code}`);
  const clientManifest = JSON.parse(await readFile(pilot.manifest, 'utf8'));
  clientSegments.push({ segment: 'persistent_restart', manifest: pilot.manifest, runId: clientManifest.runId,
    timing: clientManifest.timing, reusedJvm: true });
}

async function awaitLifecycleBarrierFromPilot(barrier, timeoutMs, pilot, predicate = () => true) {
  if (pilot?.child === undefined) throw new Error('lifecycle barrier wait requires its exact pilot child');
  const acknowledgement = awaitLifecycleBarrier(lifecycle, barrier, timeoutMs, predicate);
  const exit = childExitWatch(pilot.child);
  try {
    const first = await Promise.race([
      acknowledgement.then((value) => ({ kind: 'acknowledged', value })),
      exit.wait.then((code) => ({ kind: 'exited', code }))
    ]);
    if (first.kind === 'acknowledged') return first.value;
    // The runner serializes typed signal consumption before it appends a journal entry. A normal
    // client can exit immediately after emitting its final signal while the supervisor flushes
    // that validated entry. Give the exact acknowledgement a bounded protocol drain.
    try {
      return await awaitWithin(acknowledgement, 1_000,
        `persistent pilot exited before lifecycle barrier ${barrier} (${first.code})`);
    } catch (error) {
      if (String(error?.message ?? error).includes('persistent pilot exited before lifecycle barrier')) throw error;
      throw new Error(`persistent pilot exited before lifecycle barrier ${barrier} (${first.code}): ${String(error?.message ?? error)}`);
    }
  } finally {
    exit.close();
  }
}

async function timedStop(name, action) {
  timing.begin(name);
  try { await action(); }
  finally { timing.end(name); }
}

async function sourceIdentity(projectDirectory) {
  const execute = promisify(execFile);
  const value = async (...args) => {
    const { stdout } = await execute('git', args, { cwd: projectDirectory });
    return stdout.trim();
  };
  const [commit, status] = await Promise.all([value('rev-parse', 'HEAD'), value('status', '--porcelain')]);
  return Object.freeze({ sourceCommit: commit, sourceDirty: status.length > 0, profile: 'disposable_lite' });
}

/**
 * Compiles and fingerprints the one source/build identity before a multi-JVM scenario begins.
 * The replacement server may be a new Minecraft JVM, but it never gets an implicit rebuild or
 * a differently selected artifact halfway through the same causal experiment.
 */
async function prepareBuild(projectDirectory, gradleExecutable) {
  const build = spawn(gradleExecutable, [':pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment'], {
    cwd: projectDirectory, env: process.env, stdio: 'inherit'
  });
  const code = await exited(build);
  if (code !== 0) throw new Error(`F0.V prepared build failed (${code})`);
  const libs = resolve(projectDirectory, 'pale-mirror-neoforge/build/libs');
  const candidates = (await readdir(libs)).filter((name) => /^[a-z0-9_-]+-.*\.jar$/i.test(name)
    && !name.includes('-sources') && !name.includes('-javadoc')).sort();
  if (candidates.length !== 1) throw new Error(`F0.V expected one packaged pilot artifact, found ${candidates.join(', ') || 'none'}`);
  const artifact = resolve(libs, candidates[0]);
  return Object.freeze({ ...(await sourceIdentity(projectDirectory)), sourceContent: await fingerprintPreparedSource(projectDirectory),
    ...(await fingerprintPreparedBuild(projectDirectory, artifact)) });
}

/** A matrix prepares once, persists this identity, and each child only verifies it before launch. */
async function resolvePreparedBuild(projectDirectory, gradleExecutable) {
  const declared = process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY;
  if (declared === undefined || declared === '') return prepareBuild(projectDirectory, gradleExecutable);
  const path = resolve(projectDirectory, declared);
  const buildRoot = resolve(projectDirectory, 'build');
  if (!path.startsWith(buildRoot + '/')) throw new Error('prepared build identity must be retained under build/');
  const identity = JSON.parse(await readFile(path, 'utf8'));
  await requirePreparedF0vBuild(projectDirectory, identity);
  return Object.freeze(identity);
}

async function writeScenario(path, value) {
  await writeFile(path, `${JSON.stringify({ ...value, server: { ...value.server, host: '127.0.0.1', port } }, null, 2)}\n`, 'utf8');
}

async function stopServerSafely(server, serverPort) {
  // RCON reaches Minecraft's normal `stop` command. SIGTERM reaches the JVM
  // shutdown hook and can interrupt world persistence. A request is never
  // evidence: the pilot-only typed durable-save acknowledgement below must be
  // present before restart or cleanup.
  await requestRconStop({ port: server.rconPort, password: server.rconPassword });
  await awaitLifecycleSignal(lifecycle, LifecycleSignal.DURABLE_SERVER_SAVE, server.serverRunId, DURABLE_STOP_TIMEOUT_MS);
  await ownedServerExit(server, serverPort, DURABLE_STOP_TIMEOUT_MS, 'disposable v3 server flushed but retained its game port');
  releaseWrapper(server.child);
}
async function stopServerForCleanup(server, serverPort) {
  try {
    await stopServerSafely(server, serverPort);
  } catch (failure) {
    // Keep the failed world and its manifest for forensic recovery, but never leave the exact
    // disposable JVM alive. A later scenario must not inherit its port or its loaded world.
    // This fallback has no listener/name scan: it can affect only the nonce-announced JVM that
    // this runner created. The original graceful-stop failure still reaches the caller.
    if (await portOpen(serverPort)) killIfPresent(server.serverPid);
    await ownedServerExit(server, serverPort, 45_000, `disposable v3 cleanup could not stop its exact JVM after graceful failure: ${failure.message}`);
    releaseWrapper(server.child);
    throw failure;
  }
}
async function stopUnreadyServer(server, serverPort) {
  // This is not an abrupt-recovery experiment: readiness never occurred.  First request the
  // ordinary Minecraft stop over the nonce-owned RCON endpoint; if a quarantined lifecycle did
  // not expose it, terminate only the direct child PID created by this runner.  No listener or
  // process-name discovery is used.
  try {
    await requestRconStop({ port: server.rconPort, password: server.rconPassword });
    await ownedServerExit(server, serverPort, 10_000, 'unready disposable server retained its game port after normal stop');
    releaseWrapper(server.child); return;
  } catch { /* The server may have failed before RCON startup; exact-child cleanup follows. */ }
  if (!Number.isInteger(server.serverPid) || server.serverPid <= 1) {
    throw new Error('unready disposable server lacks its exact direct JVM PID');
  }
  terminateIfPresent(server.serverPid);
  try { await ownedServerExit(server, serverPort, 45_000, 'unready exact disposable server did not close its game port'); }
  catch {
    killIfPresent(server.serverPid);
    await ownedServerExit(server, serverPort, 45_000, 'killed unready exact disposable server did not close its game port');
  }
  releaseWrapper(server.child);
}
async function stopServerAbruptly(server, serverPort) {
  if (!Number.isInteger(server.serverPid) || server.serverPid <= 1) throw new Error('disposable server did not expose an exact JVM identity');
  // The nonce is passed only to this Gradle RunGame task and is echoed by that
  // JVM at server start. Kill that exact Minecraft process, not its launcher,
  // a name match, a listener lookup, or a broad process group.
  killIfPresent(server.serverPid);
  await ownedServerExit(server, serverPort, 45_000, `abruptly stopped exact disposable JVM but game port ${serverPort} remained open`);
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
function terminateIfPresent(pid) {
  try { process.kill(pid, 'SIGTERM'); }
  catch (failure) { if (failure.code !== 'ESRCH') throw new Error(`could not terminate exact disposable process ${pid}: ${failure}`); }
}
async function ownedServerExit(server, serverPort, timeoutMs, label) {
  await exitWithin(server.child, timeoutMs, label);
  if (await portOpen(serverPort)) throw new Error(`${label}: reserved game port ${serverPort} remains open`);
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
function directoryChange(directory, deadline) {
  let watcher; let timer; let settled = false; let resolveWait; let rejectWait;
  const wait = new Promise((resolveWaiter, rejectWaiter) => { resolveWait = resolveWaiter; rejectWait = rejectWaiter; });
  const finish = (value) => {
    if (settled) return;
    settled = true; clearTimeout(timer); watcher?.close(); resolveWait(value);
  };
  try {
    watcher = watch(directory, { persistent: false }, () => finish(true));
    watcher.once('error', (error) => { if (!settled) { settled = true; clearTimeout(timer); watcher?.close(); rejectWait(error); } });
  } catch (error) { settled = true; rejectWait(error); }
  const remaining = deadline - Date.now();
  if (remaining <= 0) finish(false); else timer = setTimeout(() => finish(false), remaining);
  return Object.freeze({ wait, close: () => finish(false) });
}

/** Test-fixture-only read of the retained WAL tail after all server processes have stopped. */
async function decodeWalTail(worldDirectory) {
  const directory = resolve(worldDirectory, 'frontier-v3/frontier_graybox');
  const execute = promisify(execFile);
  try {
    const { stdout } = await execute(gradle, [':pale-mirror-frontier:frontierV3WalTailDiagnostic', '--quiet', `--args=${directory}`],
      { cwd: project, maxBuffer: 256 * 1024 });
    return stdout.split('\n').filter((line) => line.startsWith('PMV3_WAL_TAIL ')).map((line) => JSON.parse(line.slice('PMV3_WAL_TAIL '.length)));
  } catch (error) {
    return { unavailable: true, reason: String(error?.message ?? error).slice(0, 2048) };
  }
}

/** Reads only the bounded pilot diagnostics already written by the failed native attempt. */
async function retainedDiagnostics(manifests) {
  const result = [];
  for (const path of manifests) {
    try {
      const manifest = JSON.parse(await readFile(path, 'utf8'));
      if (Array.isArray(manifest.diagnostics)) result.push(...manifest.diagnostics.slice(-24));
    } catch (failure) {
      if (failure?.code !== 'ENOENT' && failure instanceof SyntaxError === false) throw failure;
    }
  }
  return result.slice(-24);
}
