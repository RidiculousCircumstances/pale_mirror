import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises';
import { createConnection } from 'node:net';
import { basename, dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defaultPilotProfile, loadScenario, pilotCrashBoundary, pilotFailureFromLine, restartSegments, scenarioDeadlineMs } from './scenario.mjs';
import { createCrashController } from './crash-controller.mjs';
import { requireColdProgress, requireTerminalEvidence } from './f0v-matrix.mjs';
import { requestRconStop } from './rcon.mjs';
import { fingerprintPreparedBuild, fingerprintPreparedSource, portablePreparedBuildIdentity, requirePreparedF0vBuild } from './prepared-build.mjs';
import { preparedLaunch } from './prepared-launch.mjs';
import { LifecycleBarrier, LifecycleSignal, awaitLifecycleSignal, createLifecycleBarrierSession, newLifecycleIdentity, publishLifecycleBarrier, readLifecycleBarriers } from './lifecycle-barrier.mjs';
import { awaitPersistentMatrixResult, createPersistentMatrixSession, matrixSegment, publishPersistentMatrixExpectedCrashArm, publishPersistentMatrixExpectedCrashRelease, publishPersistentMatrixFinalClose, publishPersistentMatrixResume, publishPersistentMatrixServerReady, sha256Json, validatePersistentMatrixEvidence, validatePersistentMatrixPlan } from './persistent-matrix.mjs';
import { writeFailureBundle } from './failure-bundle.mjs';
import { awaitWithin, childExitCancellation } from './deadline-watchdog.mjs';
import { verifiedPrivateDisplayEnvironment, verifiedVisibleDisplayEnvironment } from './visible-display.mjs';
import { terminateOwnedProcessGroup } from './owned-process-group.mjs';
import { compileAssignedPersistentMatrix } from './persistent-worker-plan.mjs';
import { prepareNativeWorld } from './prepare-f0vc-native-world.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const isMain = process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) await (async () => {
const runnerStartedAt = process.hrtime.bigint();
const runId = randomUUID();
const assignedArgument = process.argv.slice(2).find((value) => value.startsWith('--assigned-plan='));
const assignedSourceArgument = process.argv.slice(2).find((value) => value.startsWith('--assigned-source='));
const assignedMeasurementArgument = process.argv.slice(2).find((value) => value.startsWith('--assigned-measurement='));
const outputArgument = process.argv.slice(2).find((value) => !value.startsWith('--'));
if (process.argv.slice(2).some((value) => value !== assignedArgument && value !== assignedSourceArgument && value !== assignedMeasurementArgument && value !== outputArgument)) throw new Error('unknown persistent matrix runner argument');
if ((assignedArgument === undefined) !== (assignedSourceArgument === undefined) || (assignedArgument === undefined) !== (assignedMeasurementArgument === undefined)) {
  throw new Error('assigned persistent runner requires plan, trusted source and measurement together');
}
const assignedCandidate = assignedArgument === undefined ? undefined : JSON.parse(await readFile(safeProjectPath(assignedArgument.slice('--assigned-plan='.length), 'assigned persistent plan'), 'utf8'));
const assignedSource = assignedSourceArgument === undefined ? undefined : JSON.parse(await readFile(safeProjectPath(assignedSourceArgument.slice('--assigned-source='.length), 'assigned persistent source'), 'utf8'));
const assignedPlan = assignedCandidate === undefined ? undefined : admitAssignedPersistentPlan(assignedCandidate, assignedSource, assignedMeasurementArgument.slice('--assigned-measurement='.length));
const output = resolve(project, outputArgument ?? `build/frontier-v3-scenarios/f0va-persistent-matrix-${runId}.json`);
const port = Number(process.env.FRONTIER_V3_PILOT_PORT ?? 25575);
if (!Number.isInteger(port) || port < 1024 || port >= 65535) throw new Error('FRONTIER_V3_PILOT_PORT must be 1024..65534');
if (await portOpen(port) || await portOpen(port + 1)) throw new Error(`F0.VA persistent matrix ports ${port}/${port + 1} are already occupied`);
await absent(output, 'persistent matrix manifest');
const visibleClientEnvironment = assignedPlan === undefined
  ? await verifiedVisibleDisplayEnvironment()
  : await verifiedPrivateDisplayEnvironment();

const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
const suppliedPreparedIdentity = process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY;
if (assignedPlan !== undefined) requireAssignedPreparedIdentity(suppliedPreparedIdentity);
const preparedIdentityPath = suppliedPreparedIdentity === undefined || suppliedPreparedIdentity === ''
  ? resolve(project, `build/frontier-v3-scenarios/${runId}-prepared-build.json`)
  : safeProjectPath(suppliedPreparedIdentity, 'prepared build identity');
const build = suppliedPreparedIdentity === undefined || suppliedPreparedIdentity === ''
  ? await prepareBuild(project, gradle)
  : await readPreparedBuild(preparedIdentityPath);
// CI assigns lanes against the portable identity; the local artifact identity is retained in the
// manifest but must never be compared to that different namespace.
const buildIdentitySha256 = assignedPlan === undefined ? sha256Json(build) : sha256Json(portablePreparedBuildIdentity(build));
const workerId = process.env.FRONTIER_V3_PILOT_WORKER_ID ?? 'local-f0va';
const lifecycle = await createLifecycleBarrierSession(resolve(project, 'build/frontier-v3-scenarios'), newLifecycleIdentity({
  buildIdentitySha256, workerId, runId, scenarioId: 'f0va_persistent_matrix', segmentId: 'matrix'
}));
const controlDirectory = resolve(project, `build/frontier-v3-scenarios/${runId}-persistent-matrix`);
if (suppliedPreparedIdentity === undefined || suppliedPreparedIdentity === '') {
  await writeExclusive(preparedIdentityPath, `${JSON.stringify(build, null, 2)}\n`);
}

const source = assignedPlan === undefined ? await sourceSegments(port) : await assignedSourceSegments(port, assignedPlan);
const plan = assignedPlan === undefined ? Object.freeze({ schema: 1, kind: 'frontier-v3-persistent-matrix', workerId, buildIdentitySha256,
  segments: source.map(({ id, loaded, worldKey }, epoch) => Object.freeze({ id, scenarioId: loaded.scenario.id,
    scenarioSha256: loaded.sha256, worldKey, actionCount: loaded.scenario.actions.length, final: epoch === source.length - 1 })) }) : assignedRuntimePlan(assignedPlan, source);
if (assignedPlan !== undefined) assignedRunnerInvocation(plan, build);
const session = await createPersistentMatrixSession(controlDirectory, lifecycle.identity, plan);
const clientPlanPath = resolve(controlDirectory, 'client-plan.json');
await writeExclusive(clientPlanPath, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-persistent-matrix-client-plan',
  preparedIdentity: relative(project, preparedIdentityPath), lifecycleDirectory: lifecycle.directory,
  lifecycleIdentity: lifecycle.identity, sessionDirectory: controlDirectory, plan,
  server: `127.0.0.1:${port}`, username: source[0].loaded.scenario.pilot.username,
  scenarios: source.map(({ id, path }) => ({ id, path: relative(project, path) }))
}, null, 2)}\n`);

const runtimeScenario = resolve(controlDirectory, 'runtime-scenario.json');
const worlds = new Map([...new Set(source.map((segment) => segment.worldKey))].map((worldKey) => [worldKey, {
  key: worldKey, name: `f0va-${worldKey}-${runId.slice(0, 8)}`, password: randomUUID(), prepared: false
}]));
const serverRuns = [];
const results = [];
const crashReceipts = [];
let server = null;
let client = null;
let nativeClientPid = null;
let completed = false;
let failure = null;
let serverOutput = '';
let clientOutput = '';
let pendingCrash = null;

try {
  await orchestratePersistentSegments(plan, source, {
    activeServer: () => server,
    startServer: async (segment, epoch) => {
      await writeRuntimeScenario(runtimeScenario, segment.loaded);
      server = await startServer(segment, epoch, worlds.get(segment.worldKey));
      await publishPersistentMatrixServerReady(session, epoch, { serverRunId: server.serverRunId, serverPid: server.serverPid, port, worldKey: segment.worldKey, ready: true });
      return server;
    },
    reuseServer: async (segment, epoch, active) => {
      if (active.worldKey !== segment.worldKey) throw new Error(`F0.VA compatible case ${segment.id} changed its disposable world`);
      await writeRuntimeScenario(runtimeScenario, segment.loaded);
      active.segment = segment.id; active.epoch = epoch;
      await publishPersistentMatrixServerReady(session, epoch, { serverRunId: active.serverRunId, serverPid: active.serverPid, port, worldKey: segment.worldKey, ready: true });
      serverRuns.push({ segment: segment.id, worldKey: segment.worldKey, serverRunId: active.serverRunId, serverPid: active.serverPid });
      await publishLifecycleBarrier(lifecycle, LifecycleBarrier.SAME_SERVER_RESET_ACKNOWLEDGED,
        { serverRunId: active.serverRunId, segment: segment.id });
      return active;
    },
    startClient: async () => { client = await startClient(); nativeClientPid = await awaitPreparedClient(preparedClientSegment(source)); },
    releaseCrash: async (crash, active, epoch, segment) => {
      const release = await publishPersistentMatrixExpectedCrashRelease(session, crash.epoch, crash.proof, { serverRunId: active.serverRunId,
        serverPid: active.serverPid, worldKey: segment.worldKey, segment: segment.id, scenarioSha256: matrixSegment(plan, epoch).scenarioSha256 });
      crashReceipts.push(Object.freeze({ epoch: crash.epoch, arm: crash.arm, proof: crash.proof, release }));
    },
    resume: publishResume,
    expected: async (segment, epoch, active) => { pendingCrash = await runExpectedCrashSegment(segment, epoch, active); server = null; return pendingCrash; },
    terminal: async (segment, epoch, active, contractSegment, nextSegment) => {
      await recordClientSegment(segment, epoch); results.push(await awaitResultOrClient(epoch));
      if (!contractSegment.final) {
        await awaitClientSignal(LifecycleSignal.CLIENT_NORMALLY_DISCONNECTED, segment.id, 300_000, `disconnect for ${segment.id}`);
        await publishLifecycleBarrier(lifecycle, LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: segment.id });
        if (nextSegment?.reuseServer === true) await awaitNormalDemandLossRelease(active, segment.id, epoch);
        else { await stopServerWithBarriers(active, segment.id, epoch); server = null; }
      } else {
        await publishPersistentMatrixFinalClose(session, epoch);
        await awaitClientSignal(LifecycleSignal.CLIENT_NORMALLY_DISCONNECTED, segment.id, 300_000, `final disconnect for ${segment.id}`);
        await publishLifecycleBarrier(lifecycle, LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: segment.id });
      }
    }
  });
  const clientCode = await awaitWithin(exited(client), 30_000, 'persistent matrix client did not terminate after its final close acknowledgement');
  if (clientCode !== 0) throw new Error(`persistent matrix client exited with ${clientCode}`);
  const clientManifest = JSON.parse(await readFile(resolve(controlDirectory, 'client-manifest.json'), 'utf8'));
  if (clientManifest.status !== 'ok' || clientManifest.clientPid !== nativeClientPid || clientManifest.segments.length !== source.length) {
    throw new Error('persistent matrix client manifest is incomplete or foreign');
  }
  validatePersistentClientAssertions(source, clientManifest, assignedPlan?.workerPlan?.lanes);
  // The client transport acknowledgement precedes actual server player removal.  Make that
  // server-owned release observable before terminalizing the matrix, then retain the ordinary
  // shutdown save as cleanup evidence rather than an after-terminal lifecycle event.
  await awaitNormalDemandLossRelease(server, server.segment, server.epoch);
  await publishLifecycleBarrier(lifecycle, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, { assertionCount: source.length });
  const events = await readLifecycleBarriers(lifecycle);
  const evidence = validatePersistentMatrixEvidence(plan, { clientPid: nativeClientPid, port, events, results, serverRuns, crashReceipts });
  // The terminal client assertion is complete before this normal shutdown; do not manufacture a
  // further matrix barrier after its terminal state. The typed server save signal remains in the
  // retained manifest as cleanup evidence.
  const finalStop = await stopServerWithoutBarrier(server, { demandLossReleased: true });
  server = null;
  await writeExclusive(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-f0va-persistent-native-matrix', status: 'ok',
    runId, build, lifecycle: { directory: relative(project, lifecycle.directory), identity: lifecycle.identity, events },
    session: { directory: relative(project, controlDirectory), plan }, client: clientManifest,
    results, serverRuns, crashReceipts, evidence, finalStop, freshWorlds: true, fixtureImage: null,
    timing: { runner: 'f0va-persistent-matrix', elapsedMillis: elapsedMillis() }
  }, null, 2)}\n`);
  completed = true;
} catch (error) {
  failure = error;
  throw error;
} finally {
  if (client && client.exitCode === null && client.signalCode === null) {
    await terminateOwnedProcessGroup(client, { label: 'F0.VA failed persistent client' })
      .catch((cleanup) => { console.error(`PMV3_F0VA client cleanup failed: ${cleanup}`); });
  }
  if (server !== null) await stopServerForFailure(server).catch((cleanup) => { console.error(`PMV3_F0VA cleanup failed: ${cleanup}`); });
  if (!completed) {
    const retainedWorld = [...worlds.values()].map((world) => resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server', world.name));
    const bundle = await writeFailureBundle({ project, output, scenarioPath: clientPlanPath, runId, failure,
      timing: { runner: 'f0va-persistent-matrix', status: 'failed', elapsedMillis: elapsedMillis() }, build,
      process: { port, clientPid: client?.pid ?? null, serverRuns }, serverLogText: serverOutput, clientLogText: clientOutput,
      worldDirectory: retainedWorld[0], lifecycleDirectory: lifecycle.directory });
    console.error(`PMV3_F0VA failure_bundle=${bundle}`);
  } else if (process.env.FRONTIER_V3_KEEP_DISPOSABLE !== 'true') {
    for (const world of worlds.values()) {
      const directory = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server', world.name);
      await rm(directory, { recursive: true, force: true });
    }
  }
}

console.log(JSON.stringify({ status: 'ok', manifest: output, runId, lifecycle: lifecycle.directory, elapsedMillis: elapsedMillis() }));

async function sourceSegments(gamePort) {
  const root = resolve(project, 'tools/frontier-v3-test-pilot/scenarios');
  const smoke = (await loadScenario(join(root, 'disposable-lite-smoke.json'))).scenario;
  const restart = (await loadScenario(join(root, 'disposable-lite-smoke-graceful-restart.json'))).scenario;
  const split = restartSegments(restart);
  if (split?.mode !== 'graceful') throw new Error('F0.VA persistent matrix requires the declared graceful restart scenario');
  const values = [
    { id: 'smoke_alpha', worldKey: 'world_alpha', scenario: { ...smoke, id: 'f0va_smoke_alpha' } },
    { id: 'restart_before', worldKey: 'world_restart', scenario: { ...split.before, id: 'f0va_graceful_restart' } },
    { id: 'restart_after', worldKey: 'world_restart', scenario: { ...split.after, id: 'f0va_graceful_restart' } },
    { id: 'smoke_beta', worldKey: 'world_beta', scenario: { ...smoke, id: 'f0va_smoke_beta' } }
  ].map((entry) => Object.freeze({ ...entry, scenario: Object.freeze({ ...entry.scenario,
    server: Object.freeze({ ...entry.scenario.server, host: '127.0.0.1', port: gamePort }) }) }));
  const directory = resolve(project, `build/frontier-v3-scenarios/${runId}-persistent-matrix-scenarios`);
  await mkdir(directory, { recursive: false });
  return await Promise.all(values.map(async (entry) => {
    const path = resolve(directory, `${entry.id}.json`);
    await writeExclusive(path, `${JSON.stringify(entry.scenario, null, 2)}\n`);
    return Object.freeze({ id: entry.id, worldKey: entry.worldKey, path, loaded: await loadScenario(path) });
  }));
}

async function assignedSourceSegments(gamePort, assigned) {
  if (!assigned || assigned.kind !== 'frontier-v3-assigned-persistent-matrix' || assigned.workerId !== workerId
      || assigned.buildIdentitySha256 !== buildIdentitySha256 || !Array.isArray(assigned.segments) || assigned.segments.length < 1) {
    throw new Error('assigned persistent plan is foreign to this worker/build');
  }
  const directory = resolve(project, `build/frontier-v3-scenarios/${runId}-assigned-persistent-scenarios`);
  await mkdir(directory, { recursive: false });
  return await Promise.all(assigned.segments.map(async (entry) => {
    if (!entry || typeof entry.id !== 'string' || typeof entry.worldKey !== 'string' || !entry.scenario || entry.scenario.crash !== undefined) {
      throw new Error('assigned persistent segment is malformed or executable crash metadata leaked');
    }
    const scenario = { ...structuredClone(entry.scenario), server: { ...entry.scenario.server, host: '127.0.0.1', port: gamePort } };
    const path = resolve(directory, `${entry.id}.json`); await writeExclusive(path, `${JSON.stringify(scenario, null, 2)}\n`);
    return Object.freeze({ id: entry.id, worldKey: entry.worldKey, path, loaded: await loadScenario(path), assigned: entry });
  }));
}

function assignedRuntimePlan(assigned, entries) {
  const core = { schema: 1, kind: 'frontier-v3-assigned-persistent-matrix', workerId: assigned.workerId,
    buildIdentitySha256: assigned.buildIdentitySha256, source: assigned.source, workerPlanSha256: assigned.workerPlanSha256,
    compiledContentSha256: assigned.contentSha256,
    segments: entries.map((entry, epoch) => ({ id: entry.id, laneId: entry.assigned.laneId, scenarioId: entry.loaded.scenario.id,
      // The compiled byte is what the worker plan admitted; the runtime byte includes only
      // the bound local port.  Keeping both prevents either identity from blessing the other.
      scenarioSha256: entry.loaded.sha256, compiledScenarioSha256: entry.assigned.scenarioSha256, originalScenarioId: entry.assigned.originalScenarioId,
      originalScenarioSha256: entry.assigned.originalScenarioSha256, originalActionOffset: entry.assigned.originalActionOffset,
      worldKey: entry.worldKey, actionCount: entry.loaded.scenario.actions.length, completion: entry.assigned.completion,
      ...(entry.assigned.reuseServer === true ? { reuseServer: true } : {}),
      ...(entry.assigned.expectedCrash === undefined ? {} : { expectedCrash: entry.assigned.expectedCrash }), final: epoch === entries.length - 1 })) };
  return Object.freeze({ ...core, contentSha256: sha256Json(core) });
}

async function prepareBuild(projectDirectory, gradleExecutable) {
  const child = spawn(gradleExecutable, [':pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment', '--offline', '--no-daemon'], {
    cwd: projectDirectory, env: process.env, stdio: 'inherit'
  });
  if (await exited(child) !== 0) throw new Error('F0.VA prepared native build failed');
  const libraries = resolve(projectDirectory, 'pale-mirror-neoforge/build/libs');
  const candidates = (await readdir(libraries)).filter((name) => /^[a-z0-9_-]+-.*\.jar$/i.test(name)
    && !name.includes('-sources') && !name.includes('-javadoc')).sort();
  if (candidates.length !== 1) throw new Error(`F0.VA expected exactly one packaged pilot artifact, found ${candidates.join(', ') || 'none'}`);
  return Object.freeze({ sourceContent: await fingerprintPreparedSource(projectDirectory),
    ...(await fingerprintPreparedBuild(projectDirectory, resolve(libraries, candidates[0]))) });
}
async function readPreparedBuild(path) {
  let identity;
  try { identity = JSON.parse(await readFile(path, 'utf8')); }
  catch (error) { throw new Error(`F0.VA supplied prepared build is unreadable: ${path}`, { cause: error }); }
  await requirePreparedF0vBuild(project, identity);
  return Object.freeze(identity);
}

async function writeRuntimeScenario(target, loaded) {
  const bytes = await readFile(loadedPath(loaded));
  if (createHash('sha256').update(bytes).digest('hex') !== loaded.sha256) throw new Error('immutable matrix scenario hash drifted before client resume');
  const temporary = `${target}.${randomUUID()}.new`;
  await writeFile(temporary, bytes, { flag: 'wx' });
  // The next resume token is written only after this exact replacement. The idle client cannot
  // reconnect before that token, and Java verifies this byte hash again at login.
  await rename(temporary, target);
}
function loadedPath(loaded) {
  const segment = source.find((entry) => entry.loaded === loaded);
  if (!segment) throw new Error('matrix runtime scenario is not one immutable plan member');
  return segment.path;
}
function safeProjectPath(candidate, label) {
  if (typeof candidate !== 'string') throw new Error(`${label} is malformed`);
  const target = resolve(project, candidate); const path = relative(project, target);
  if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} escapes project`);
  return target;
}

async function startServer(segment, epoch, world) {
  await requirePreparedF0vBuild(project, build);
  const run = randomUUID(); const crash = segment.assigned?.expectedCrash;
  const directory = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server', world.name);
  if (!world.prepared) {
    if (process.env.FRONTIER_V3_F0VC_PREPARED_RUNTIME === 'true') {
      await prepareNativeWorld({ world: world.name, seed: segment.loaded.scenario.isolation.seed, port, rconPort: port + 1,
        password: world.password, username: segment.loaded.scenario.pilot.username,
        profile: segment.loaded.scenario.server.profile ?? defaultPilotProfile(), viewDistance: segment.loaded.scenario.server.viewDistance ?? 10 });
    } else {
      const prepare = spawn(gradle, [':pale-mirror-neoforge:prepareFrontierV3PilotServerWorld', '--offline', '--no-daemon',
        `-PfrontierV3PilotWorld=${world.name}`, `-PfrontierV3PilotSeed=${segment.loaded.scenario.isolation.seed}`,
        `-PfrontierV3PilotPort=${port}`, `-PfrontierV3PilotRconPort=${port + 1}`,
        `-PfrontierV3PilotUsername=${segment.loaded.scenario.pilot.username}`, '-PfrontierV3PilotReset=true',
        `-PfrontierV3PilotRunId=${run}`, `-PfrontierV3PilotProfile=${segment.loaded.scenario.server.profile ?? defaultPilotProfile()}`,
        `-PfrontierV3PilotViewDistance=${segment.loaded.scenario.server.viewDistance ?? 10}`,
        ...(crash === undefined ? [] : [`-PfrontierV3PilotCrashBoundary=${crash.boundary}`, `-PfrontierV3PilotCrashOwner=${crash.owner}`,
          `-PfrontierV3PilotCrashRevision=${crash.expectedRevision}`, `-PfrontierV3PilotCrashPayload=${crash.payloadType}`])], {
        cwd: project, env: { ...process.env, FRONTIER_V3_PILOT_RCON_PASSWORD: world.password }, stdio: 'inherit'
      });
      if (await exited(prepare) !== 0) throw new Error(`F0.VA world preparation failed for ${segment.id}`);
    }
    world.prepared = true;
  } else {
    try { if (!(await stat(directory)).isDirectory()) throw new Error('not a directory'); }
    catch (error) { throw new Error(`F0.VA retained restart world is unavailable for ${segment.id}`, { cause: error }); }
  }
  await requirePreparedF0vBuild(project, build);
  const launch = await preparedLaunch(project, build, 'server', {
    'pale_mirror.frontier_v3.enabled': 'true', 'pale_mirror.frontier_v3.pilot.run_id': run,
    'pale_mirror.frontier_v3.pilot.lifecycle_control_directory': lifecycle.directory,
    'pale_mirror.frontier_v3.pilot.profile': segment.loaded.scenario.server.profile ?? defaultPilotProfile(),
    ...(crash === undefined ? {} : {
      'pale_mirror.frontier_v3.pilot.crash.boundary': crash.boundary,
      'pale_mirror.frontier_v3.pilot.crash.owner': crash.owner,
      'pale_mirror.frontier_v3.pilot.crash.revision': crash.expectedRevision,
      'pale_mirror.frontier_v3.pilot.crash.payload': crash.payloadType
    })
  });
  const child = spawn(launch.command, launch.args, { cwd: launch.cwd,
    env: { ...process.env, FRONTIER_V3_PILOT_RCON_PASSWORD: world.password }, stdio: ['ignore', 'pipe', 'pipe'] });
  let output = ''; let outputRevision = 0; let outputFragment = ''; let fatal = null; const outputWaiters = new Set();
  const publishOutput = () => { outputRevision++; for (const wake of outputWaiters) wake(); outputWaiters.clear(); };
  for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => {
    serverOutput = appendBoundedLogTail(serverOutput, chunk); output = appendBoundedLogTail(output, chunk);
    const lines = `${outputFragment}${chunk}`.split(/\r?\n/); outputFragment = lines.pop();
    for (const line of lines) { const marker = pilotFailureFromLine(line); if (marker !== null) fatal ??= new Error(`persistent server fatal marker: ${marker}`); }
    publishOutput(); process.stdout.write(chunk);
  });
  const spawnedJvmPid = child.pid;
  const serverRun = { child, serverRunId: run, serverPid: spawnedJvmPid, spawnedJvmPid, rconPort: port + 1,
    rconPassword: world.password, segment: segment.id, epoch, worldKey: world.key, worldDirectory: directory, crash,
    output: () => output, fatal: () => fatal, outputRevision: () => outputRevision,
    outputAfter: (revision) => {
      let wake; const wait = outputRevision !== revision ? Promise.resolve() : new Promise((resolveWake) => { wake = resolveWake; outputWaiters.add(wake); });
      return Object.freeze({ wait, close: () => { if (wake !== undefined) outputWaiters.delete(wake); } });
    } };
  try {
    const signal = await serverSignalOrExit(serverRun, LifecycleSignal.SERVER_RUN_READY, run, 180_000, `readiness for ${segment.id}`);
    if (!Number.isInteger(signal.detail.serverPid) || signal.detail.serverPid <= 1) {
      throw new Error(`F0.VA server ${segment.id} readiness signal lacks its exact JVM PID`);
    }
    if (signal.detail.serverRunId !== run || signal.detail.serverPid !== spawnedJvmPid) {
      throw new Error(`F0.VA server ${segment.id} readiness signal is foreign`);
    }
    await publishLifecycleBarrier(lifecycle, epoch === 0 ? LifecycleBarrier.SERVER_RUN_READY : LifecycleBarrier.RECOVERY_SERVER_READY,
      { serverRunId: run, serverPid: serverRun.serverPid, segment: segment.id });
    serverRuns.push({ segment: segment.id, worldKey: world.key, serverRunId: run, serverPid: serverRun.serverPid });
    return serverRun;
  } catch (error) {
    await stopServerForFailure(serverRun).catch(() => undefined);
    throw error;
  }
}

async function startClient() {
  await requirePreparedF0vBuild(project, build);
  const outputPath = resolve(controlDirectory, 'client-manifest.json');
  const child = spawn(process.execPath, [resolve(dirname(fileURLToPath(import.meta.url)), 'run-persistent-matrix-client.mjs'),
    clientPlanPath, outputPath], { cwd: project, env: { ...visibleClientEnvironment, FRONTIER_V3_PREPARED_BUILD_IDENTITY: preparedIdentityPath },
      detached: true, stdio: ['ignore', 'pipe', 'pipe'] });
  for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => {
    clientOutput = appendBoundedLogTail(clientOutput, chunk); process.stdout.write(chunk);
  });
  return child;
}

async function awaitPreparedClient(segment) {
  const signal = await awaitClientSignal(LifecycleSignal.PREPARED_CLIENT_READY, segment, 300_000, 'native client preparation');
  if (!Number.isSafeInteger(signal.detail.clientPid) || signal.detail.clientPid <= 1) {
    throw new Error('F0.VA native client preparation signal lacks its exact JVM PID');
  }
  await publishLifecycleBarrier(lifecycle, LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid: signal.detail.clientPid });
  return signal.detail.clientPid;
}

/**
 * The only abrupt path: a server-authored parked boundary first becomes an immutable arm, then
 * the client ACK and the existing exact-PID controller make loss/release observable. Actions
 * may end before the probe, but no terminal result or synthetic normal shutdown is permitted.
 */
async function runExpectedCrashSegment(segment, epoch, active) {
  const declaration = segment.assigned?.expectedCrash;
  if (!declaration || active.crash !== declaration) throw new Error(`persistent crash declaration is missing for ${segment.id}`);
  const connected = epoch === 0 ? LifecycleSignal.CLIENT_CONNECTED_FIXTURE_READY : LifecycleSignal.SAME_CLIENT_RECONNECTED_STATE_CLEARED;
  const connectedBarrier = epoch === 0 ? LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY : LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED;
  await awaitClientSignal(connected, segment.id, 300_000, `connection for ${segment.id}`);
  await publishLifecycleBarrier(lifecycle, connectedBarrier, { clientPid: nativeClientPid, segment: segment.id });
  const observation = await awaitExpectedCrashBoundary(active, declaration, scenarioDeadlineMs(segment.loaded.scenario), undefined, client);
  let completedActionSteps = -1;
  let armReceipt;
  const proof = await executeExpectedCrashProtocol({ observation, declaration, active, epoch, segment, arm: async () => {
    armReceipt = await publishPersistentMatrixExpectedCrashArm(session, epoch, {
    serverRunId: active.serverRunId, serverPid: active.serverPid, clientPid: nativeClientPid, port, resolvedRevision: observation.revision,
    ...(declaration.expectedAuthorityEpoch === undefined ? {} : { authorityEpoch: declaration.expectedAuthorityEpoch })
    }); return armReceipt;
  }, acknowledgeArm: async () => {
    const ack = await awaitClientSignal(LifecycleSignal.EXPECTED_LOSS_ARMED, segment.id, 300_000, `expected-loss arm for ${segment.id}`);
    completedActionSteps = checkedExpectedPrefix(ack.detail, segment.loaded.scenario.actions.length, 'arm acknowledgement');
    for (let action = 1; action <= completedActionSteps; action++) {
      await awaitClientSignal(LifecycleSignal.ACTION_CHECKPOINT, `${segment.id}-${String(action).padStart(4, '0')}`, 300_000, `acknowledged action ${action} for ${segment.id}`);
      await publishLifecycleBarrier(lifecycle, LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: action, segment: segment.id });
    }
    return ack;
  },
  fire: () => createCrashController({ runId: active.serverRunId, boundary: declaration.boundary, owner: declaration.owner, payloadType: declaration.payloadType,
    expectedRevision: observation.revision, ...(declaration.expectedAuthorityEpoch === undefined ? {} : { expectedAuthorityEpoch: declaration.expectedAuthorityEpoch }), serverPid: active.serverPid }).fire(observation),
  awaitExit: () => ownedServerExit(active.child, port, 90_000, `persistent crash server ${segment.id}`),
  awaitLoss: async () => {
    const loss = await awaitClientSignal(LifecycleSignal.CLIENT_EXPECTED_LOSS, segment.id, 90_000, `expected loss for ${segment.id}`);
    if (checkedExpectedPrefix(loss.detail, segment.loaded.scenario.actions.length, 'expected loss') !== completedActionSteps) throw new Error(`persistent expected-loss prefix diverged for ${segment.id}`);
    return loss;
  },
  barrier: (barrier, detail) => publishLifecycleBarrier(lifecycle, barrier, detail), clientPid: nativeClientPid, port });
  releaseWrapper(active.child);
  const run = serverRuns.find((candidate) => candidate.serverRunId === active.serverRunId);
  if (!run) throw new Error(`persistent crash server receipt is missing for ${segment.id}`);
  run.completedActionSteps = completedActionSteps;
  return Object.freeze({ epoch, completedActionSteps, arm: armReceipt, proof });
}

function checkedExpectedPrefix(detail, actionCount, label) {
  const prefix = detail?.completedActionSteps;
  if (!Number.isInteger(prefix) || prefix < 0 || prefix > actionCount) throw new Error(`persistent ${label} completed action prefix is malformed`);
  return prefix;
}

async function recordClientSegment(segment, epoch) {
  const connected = epoch === 0 ? LifecycleSignal.CLIENT_CONNECTED_FIXTURE_READY : LifecycleSignal.SAME_CLIENT_RECONNECTED_STATE_CLEARED;
  const barrier = epoch === 0 ? LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY : LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED;
  await awaitClientSignal(connected, segment.id, 300_000, `connection for ${segment.id}`);
  await publishLifecycleBarrier(lifecycle, barrier, { clientPid: nativeClientPid, segment: segment.id });
  for (let action = 1; action <= segment.loaded.scenario.actions.length; action++) {
    await awaitClientSignal(LifecycleSignal.ACTION_CHECKPOINT, `${segment.id}-${String(action).padStart(4, '0')}`, 300_000,
      `action ${action} for ${segment.id}`);
    await publishLifecycleBarrier(lifecycle, LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: action, segment: segment.id });
  }
  await awaitClientSignal(LifecycleSignal.SCENARIO_SEGMENT_COMPLETE, segment.id, 300_000, `completion for ${segment.id}`);
  await publishLifecycleBarrier(lifecycle, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: segment.id });
}

async function awaitClientSignal(signal, suffix, timeoutMs, label, externalAbort = undefined) {
  const cancellation = childExitCancellation(client);
  const abort = externalAbort === undefined ? cancellation.signal : AbortSignal.any([cancellation.signal, externalAbort]);
  try {
    return await awaitLifecycleSignal(lifecycle, signal, suffix, timeoutMs, abort);
  } catch (error) {
    if (cancellation.signal.aborted) throw new Error(`persistent matrix client exited before ${label} (${cancellation.signal.reason})`);
    throw error;
  } finally {
    cancellation.close();
  }
}

async function awaitResultOrClient(epoch) {
  const cancellation = childExitCancellation(client);
  try {
    return await awaitPersistentMatrixResult(session, epoch, 300_000, cancellation.signal);
  } catch (error) {
    if (cancellation.signal.aborted) throw new Error(`persistent matrix client exited before segment ${epoch} result (${cancellation.signal.reason})`);
    throw error;
  } finally {
    cancellation.close();
  }
}

async function publishResume(epoch) {
  const next = source[epoch];
  if (!next) throw new Error('F0.VA matrix attempted to resume beyond its immutable plan');
  // The replacement server has already passed its typed readiness barrier.  This one-time token
  // can only be consumed by the same client JVM after it validates the next descriptor/hash.
  await publishPersistentMatrixResume(session, epoch);
}

async function stopServerWithBarriers(active, segmentId, epoch) {
  await awaitNormalDemandLossRelease(active, segmentId, epoch);
  await requestRconStop({ port: active.rconPort, password: active.rconPassword });
  await awaitLifecycleSignal(lifecycle, LifecycleSignal.DURABLE_SERVER_SAVE, active.serverRunId, 90_000);
  await ownedServerExit(active.child, port, 90_000, `F0.VA server ${segmentId}`);
  await publishLifecycleBarrier(lifecycle, LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: active.serverRunId, segment: segmentId });
  await publishLifecycleBarrier(lifecycle, LifecycleBarrier.GAME_PORT_CLOSED, { port, serverRunId: active.serverRunId, segment: segmentId });
  releaseWrapper(active.child);
}

async function stopServerWithoutBarrier(active, { demandLossReleased = false } = {}) {
  if (active === null) throw new Error('F0.VA final server is unavailable');
  if (!demandLossReleased) await awaitNormalDemandLossRelease(active, active.segment, active.epoch);
  await requestRconStop({ port: active.rconPort, password: active.rconPassword });
  await awaitLifecycleSignal(lifecycle, LifecycleSignal.DURABLE_SERVER_SAVE, active.serverRunId, 90_000);
  await ownedServerExit(active.child, port, 90_000, 'F0.VA final server');
  releaseWrapper(active.child);
  return Object.freeze({ serverRunId: active.serverRunId, serverPid: active.serverPid, durableSave: true, portClosed: true });
}

/**
 * The client acknowledgement proves its own transport boundary, not that the server has
 * completed removal of that connection.  Admit the server-owned release receipt before RCON
 * shutdown so a world save never races the final player removal.  The token is nonce-bound to
 * this exact server JVM; a retained/stale release cannot advance a replacement run.
 */
async function awaitNormalDemandLossRelease(active, segmentId, epoch) {
  if (!Number.isInteger(epoch) || epoch < 0 || epoch > 9999) throw new Error('F0.VA demand-loss epoch is invalid');
  const sequence = String(epoch).padStart(4, '0'); const suffix = `${active.serverRunId}-${sequence}`;
  const token = join(lifecycle.directory, `demand-loss-${suffix}.token`);
  await writeFile(token, `${lifecycle.identity.runId}:${sequence}\n`, { encoding: 'utf8', flag: 'wx' });
  await awaitLifecycleSignal(lifecycle, LifecycleSignal.NORMAL_DEMAND_LOSS_RELEASE, suffix, 90_000);
  await publishLifecycleBarrier(lifecycle, LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE,
    { serverRunId: active.serverRunId, segment: segmentId });
}

async function stopServerForFailure(active) {
  try {
    await requestRconStop({ port: active.rconPort, password: active.rconPassword, timeoutMs: 10_000 });
    await ownedServerExit(active.child, port, 20_000, 'F0.VA owned failed server');
    releaseWrapper(active.child); return;
  } catch { /* Readiness or RCON may not exist; exact-child cleanup follows. */ }
  terminateIfPresent(active.serverPid);
  try { await ownedServerExit(active.child, port, 30_000, 'F0.VA terminated failed server'); }
  catch {
    killIfPresent(active.serverPid);
    await ownedServerExit(active.child, port, 30_000, 'F0.VA killed failed server');
  }
  releaseWrapper(active.child);
}

function exited(child) {
  if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve(child.exitCode ?? 1);
  return new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1)));
}
function releaseWrapper(child) {
  if (child.exitCode === null && child.signalCode === null) child.kill('SIGTERM');
  child.stdout?.destroy(); child.stderr?.destroy(); child.unref();
}
function appendBoundedLogTail(previous, chunk) {
  const combined = `${previous}${chunk}`;
  const bytes = Buffer.from(combined);
  return bytes.byteLength <= 64 * 1024 ? combined : bytes.subarray(bytes.byteLength - 64 * 1024).toString('utf8');
}
function terminateIfPresent(pid) { try { process.kill(pid, 'SIGTERM'); } catch (error) { if (error.code !== 'ESRCH') throw error; } }
function killIfPresent(pid) { try { process.kill(pid, 'SIGKILL'); } catch (error) { if (error.code !== 'ESRCH') throw error; } }
async function serverSignalOrExit(active, signal, suffix, timeoutMs, label) {
  const cancellation = childExitCancellation(active.child);
  try {
    return await awaitLifecycleSignal(lifecycle, signal, suffix, timeoutMs, cancellation.signal);
  } catch (error) {
    if (cancellation.signal.aborted) throw new Error(`F0.VA server exited before ${label} (${cancellation.signal.reason})`);
    throw error;
  } finally {
    cancellation.close();
  }
}
async function ownedServerExit(child, candidate, timeoutMs, label) {
  await awaitWithin(exited(child), timeoutMs, `${label} did not terminate within ${timeoutMs}ms`);
  if (await portOpen(candidate)) throw new Error(`${label} exited but its reserved game port ${candidate} remains open`);
}
function portOpen(candidate) {
  return new Promise((resolveOpen) => {
    const socket = createConnection({ host: '127.0.0.1', port: candidate });
    const finish = (open) => { socket.removeAllListeners(); socket.destroy(); resolveOpen(open); };
    socket.once('connect', () => finish(true)); socket.once('error', () => finish(false)); socket.setTimeout(500, () => finish(false));
  });
}
function elapsedMillis() { return Number((process.hrtime.bigint() - runnerStartedAt) / 1_000_000n); }
async function absent(path, label) {
  try { await stat(path); throw new Error(`refusing to overwrite ${label}: ${path}`); }
  catch (error) { if (error?.code !== 'ENOENT') throw error; }
}
async function writeExclusive(path, contents) {
  await mkdir(dirname(path), { recursive: true });
  try { await writeFile(path, contents, { encoding: 'utf8', flag: 'wx' }); }
  catch (error) { if (error?.code === 'EEXIST') throw new Error(`refusing to overwrite immutable F0.VA evidence: ${basename(path)}`); throw error; }
}
})();

/** The client binds preparation to its first immutable matrix descriptor, never a synthetic label. */
export function preparedClientSegment(source) {
  const segment = source?.[0]?.id;
  if (typeof segment !== 'string' || segment.length === 0) throw new Error('persistent matrix has no initial client segment');
  return segment;
}

/** Exported bounded probe seam: real server/client exits and every subscription are observed. */
export async function awaitExpectedCrashBoundary(active, declaration, timeoutMs, cancellation, clientChild) {
  if (!clientChild) throw new Error('persistent crash boundary wait requires its exact client child');
  const deadline = Date.now() + timeoutMs;
  const serverExit = childExitCancellation(active.child); const clientExit = childExitCancellation(clientChild);
  try { while (Date.now() < deadline) {
    if (active.fatal() !== null) throw active.fatal();
    if (cancellation?.aborted) throw new Error(`persistent crash boundary wait was cancelled for ${active.segment}`);
    if (serverExit.signal.aborted) throw new Error(`persistent server exited before its crash boundary for ${active.segment}`);
    if (clientExit.signal.aborted) throw new Error(`persistent client exited before its crash boundary for ${active.segment}`);
    const revision = active.outputRevision(); const observation = pilotCrashBoundary(active.output(), active.serverRunId);
    if (observation !== undefined) {
      if (observation.boundary !== declaration.boundary || observation.owner !== declaration.owner || observation.payloadType !== declaration.payloadType
          || (declaration.expectedRevision !== 'observed_at_boundary' && observation.revision !== declaration.expectedRevision)) throw new Error(`persistent server crash boundary is foreign for ${active.segment}`);
      return observation;
    }
    const outputWait = active.outputAfter(revision); const serverWait = abortWaitExternal(serverExit.signal); const clientWait = abortWaitExternal(clientExit.signal); const cancellationWait = abortWaitExternal(cancellation);
    try { await awaitWithin(Promise.race([outputWait.wait, serverWait.wait, clientWait.wait, cancellationWait.wait]), Math.max(1, deadline - Date.now()), `persistent server did not announce ${declaration.boundary} within its scenario deadline`); }
    finally { outputWait.close(); serverWait.close(); clientWait.close(); cancellationWait.close(); }
  }
  throw new Error(`persistent server did not announce ${declaration.boundary} within its scenario deadline`);
  } finally { serverExit.close(); clientExit.close(); }
}
function abortWaitExternal(signal) {
  let listener = () => {}; const wait = signal === undefined ? new Promise(() => {}) : new Promise((resolveWait) => {
    listener = () => resolveWait(); if (signal.aborted) listener(); else signal.addEventListener('abort', listener, { once: true });
  });
  return Object.freeze({ wait, close: () => signal?.removeEventListener('abort', listener) });
}

/** The CLI-consumed abrupt protocol; callbacks provide I/O only, never a synthetic transition. */
export async function executeExpectedCrashProtocol({ active, segment, arm, acknowledgeArm, fire, awaitExit, awaitLoss, barrier, clientPid, port }) {
  await arm(); await acknowledgeArm(); await barrier(LifecycleBarrier.EXPECTED_LOSS_ARMED, { segment: segment.id });
  const fired = await fire(); await barrier(LifecycleBarrier.CRASH_CONTROLLER_FIRED, { serverPid: active.serverPid, segment: segment.id });
  await awaitExit(); await barrier(LifecycleBarrier.OWNED_SERVER_EXIT, { serverPid: active.serverPid, segment: segment.id });
  const loss = await awaitLoss(); await barrier(LifecycleBarrier.CLIENT_EXPECTED_LOSS, { clientPid, segment: segment.id });
  await barrier(LifecycleBarrier.GAME_PORT_CLOSED, { port, serverRunId: active.serverRunId, segment: segment.id });
  return Object.freeze({ fired, ownedExit: { serverPid: active.serverPid, serverRunId: active.serverRunId, exited: true },
    clientLoss: { ...loss.identity, ...loss.detail, segment: loss.suffix },
    portClosed: { serverPid: active.serverPid, serverRunId: active.serverRunId, port, closed: true } });
}

/** Pure runner admission seam: the real shard supplies one compiler-derived plan, never lanes. */
export function assignedRunnerInvocation(workerPlan, preparedIdentity) {
  if (!workerPlan || workerPlan.kind !== 'frontier-v3-assigned-persistent-matrix' || !Array.isArray(workerPlan.segments)
      || workerPlan.segments.length < 1 || workerPlan.segments.length > 32 || !preparedIdentity) {
    throw new Error('assigned persistent runner invocation is malformed');
  }
  const lanes = [...new Set(workerPlan.segments.map((segment) => segment.laneId))];
  if (lanes.length === 0 || new Set(workerPlan.segments.map((segment) => segment.id)).size !== workerPlan.segments.length) {
    throw new Error('assigned persistent runner invocation has foreign or duplicate segments');
  }
  return Object.freeze({ workerId: workerPlan.workerId, lanes: Object.freeze(lanes),
    segments: Object.freeze(workerPlan.segments.map((segment) => segment.id)),
    worldKeys: Object.freeze([...new Set(workerPlan.segments.map((segment) => segment.worldKey))]), preparedIdentity });
}

/** CLI admission reconstructs every assigned byte from the trusted CI artifact, never its hash alone. */
export function admitAssignedPersistentPlan(candidate, sourceArtifact, measurementId) {
  if (!candidate || !sourceArtifact?.plan || typeof measurementId !== 'string') throw new Error('assigned persistent plan source is missing or malformed');
  const rebuilt = compileAssignedPersistentMatrix(candidate.workerPlan, sourceArtifact.plan, { workerId: candidate.workerId, measurementId });
  if (JSON.stringify(candidate) !== JSON.stringify(rebuilt)) throw new Error('assigned persistent plan is foreign or content-drifted');
  return rebuilt;
}

/** Assigned CI work has no build fallback: only its shard-verified local input is admissible. */
export function requireAssignedPreparedIdentity(value) {
  if (typeof value !== 'string' || value.trim() === '') throw new Error('assigned persistent execution requires the shard-verified prepared identity');
  return value;
}

/**
 * The CLI's sole segment state machine. Tests inject only process/evidence I/O; admission,
 * ordering, one client creation and crash-successor release remain this exact implementation.
 */
export async function orchestratePersistentSegments(plan, source, effects) {
  const checked = matrixPlan(plan);
  if (!Array.isArray(source) || source.length !== checked.segments.length || !effects) throw new Error('persistent orchestration input is malformed');
  let crash = null;
  for (const [epoch, segment] of source.entries()) {
    const contractSegment = checked.segments[epoch];
    if (!segment || segment.id !== contractSegment.id) throw new Error('persistent orchestration source is foreign');
    const active = contractSegment.reuseServer === true
      ? await effects.reuseServer(segment, epoch, serverForReuse(source, checked, epoch, effects))
      : await effects.startServer(segment, epoch);
    if (epoch === 0) await effects.startClient();
    else {
      if (crash !== null) { await effects.releaseCrash(crash, active, epoch, segment); crash = null; }
      await effects.resume(epoch);
    }
    if (contractSegment.completion === 'expected_crash') crash = await effects.expected(segment, epoch, active);
    else await effects.terminal(segment, epoch, active, contractSegment, checked.segments[epoch + 1]);
  }
  if (crash !== null) throw new Error('persistent orchestration has no successor for crash release');
}

function serverForReuse(source, plan, epoch, effects) {
  const active = typeof effects.activeServer === 'function' ? effects.activeServer() : undefined;
  if (epoch < 1 || plan.segments[epoch - 1].completion === 'expected_crash' || source[epoch - 1]?.worldKey !== source[epoch]?.worldKey
      || !active) {
    throw new Error('persistent orchestration compatible case has no exact live predecessor');
  }
  return active;
}

function matrixPlan(plan) {
  return validatePersistentMatrixPlan(plan);
}

/** Applies the existing F0.V assertion/COLD readers to retained stamped client evidence. */
export function validatePersistentClientAssertions(source, clientManifest, originalLanes = undefined) {
  if (!Array.isArray(source) || !clientManifest || !Array.isArray(clientManifest.segments)) throw new Error('persistent client assertion evidence is malformed');
  for (const segment of source) {
    const report = clientManifest.segments.find((candidate) => candidate.id === segment.id);
    if (!report || !Array.isArray(report.diagnostics)) throw new Error(`persistent client diagnostics are missing: ${segment.id}`);
    const manifest = { diagnostics: report.diagnostics.map((item) => ({ observed: { actionStep: item.actionStep, value: item.value } })) };
    validateSegmentAssertions(segment.loaded.scenario, segment.assigned?.completion === 'expected_crash' ? report.completedActionSteps : segment.loaded.scenario.actions.length, manifest);
  }
  if (!Array.isArray(originalLanes)) return;
  if (new Set(clientManifest.segments.map((segment) => segment?.id)).size !== clientManifest.segments.length
      || clientManifest.segments.length !== source.length) {
    throw new Error('persistent client assertion evidence is incomplete or foreign');
  }
  const laneIds = new Set(originalLanes.map((lane) => lane?.id));
  if (laneIds.size !== originalLanes.length || source.some((segment) => !laneIds.has(segment.assigned?.laneId))) {
    throw new Error('persistent original lane evidence is incomplete or foreign');
  }
  for (const lane of originalLanes) {
    const original = originalLaneEvidenceScenario(lane, source);
    const parts = source.filter((segment) => segment.assigned?.laneId === lane.id);
    const diagnostics = parts.flatMap((segment) => {
      const report = clientManifest.segments.find((candidate) => candidate.id === segment.id);
      return report.diagnostics.map((item) => ({ observed: { actionStep: item.actionStep + segment.assigned.originalActionOffset, value: item.value } }));
    });
    const manifest = { diagnostics };
    if (!Array.isArray(original.f0vTerminalProjections) || original.f0vTerminalProjections.length === 0) {
      throw new Error(`persistent original lane terminal projections are missing: ${lane.id}`);
    }
    requireTerminalEvidence(original, manifest);
    if (original.f0vColdProgress !== undefined) requireColdProgress(original, manifest);
  }
}

/** Reconstructs only the original-lane evidence surface from every authenticated compiled split. */
function originalLaneEvidenceScenario(lane, source) {
  if (!lane || typeof lane.id !== 'string' || typeof lane.originalScenarioSha256 !== 'string'
      || typeof lane.worldKey !== 'string' || !Array.isArray(lane.segments) || lane.segments.length === 0) {
    throw new Error('persistent original lane evidence is malformed');
  }
  const parts = source.filter((segment) => segment.assigned?.laneId === lane.id);
  if (parts.length !== lane.segments.length || new Set(parts.map((segment) => segment.id)).size !== parts.length) {
    throw new Error(`persistent original lane evidence is incomplete: ${lane.id}`);
  }
  const byId = new Map(parts.map((segment) => [segment.id, segment]));
  const actions = []; const assertions = []; let expectedOffset = 0;
  let originalScenarioId; let terminalProjections; let coldProgress; let declarations;
  for (const retained of lane.segments) {
    const part = byId.get(retained?.id); const assigned = part?.assigned; const scenario = part?.loaded?.scenario;
    if (!retained || !assigned || !scenario || typeof retained.originalScenarioId !== 'string'
        || typeof retained.originalScenarioSha256 !== 'string' || typeof retained.scenarioSha256 !== 'string'
        || assigned.id !== retained.id || assigned.laneId !== lane.id
        || assigned.originalScenarioId !== retained.originalScenarioId || assigned.originalScenarioSha256 !== lane.originalScenarioSha256
        || assigned.originalScenarioSha256 !== retained.originalScenarioSha256 || assigned.originalActionOffset !== retained.originalActionOffset
        || assigned.worldKey !== lane.worldKey || retained.worldKey !== lane.worldKey || assigned.scenarioSha256 !== retained.scenarioSha256
        || scenario.id !== retained.scenario?.id || !Array.isArray(scenario.actions) || scenario.actions.length !== retained.actionCount
        || retained.originalActionOffset !== expectedOffset) {
      throw new Error(`persistent original lane evidence is foreign or inconsistent: ${lane.id}`);
    }
    if (JSON.stringify({ actions: scenario.actions, assertions: scenario.assertions,
      terminal: scenario.f0vTerminalProjections, cold: scenario.f0vColdProgress })
        !== JSON.stringify({ actions: retained.scenario.actions, assertions: retained.scenario.assertions,
          terminal: retained.scenario.f0vTerminalProjections, cold: retained.scenario.f0vColdProgress })) {
      throw new Error(`persistent original lane projections are foreign or inconsistent: ${lane.id}`);
    }
    if (originalScenarioId === undefined) originalScenarioId = retained.originalScenarioId;
    else if (originalScenarioId !== retained.originalScenarioId) throw new Error(`persistent original lane identity is inconsistent: ${lane.id}`);
    const currentDeclarations = JSON.stringify({ terminal: scenario.f0vTerminalProjections, cold: scenario.f0vColdProgress });
    if (declarations === undefined) {
      declarations = currentDeclarations; terminalProjections = structuredClone(scenario.f0vTerminalProjections); coldProgress = structuredClone(scenario.f0vColdProgress);
    } else if (declarations !== currentDeclarations) {
      throw new Error(`persistent original lane projections are inconsistent: ${lane.id}`);
    }
    actions.push(...scenario.actions.map((action) => structuredClone(action)));
    assertions.push(...scenario.assertions.map((assertion) => ({ ...structuredClone(assertion), after: assertion.after + retained.originalActionOffset })));
    expectedOffset += scenario.actions.length;
  }
  return Object.freeze({ id: originalScenarioId, actions: Object.freeze(actions), assertions: Object.freeze(assertions),
    ...(terminalProjections === undefined ? {} : { f0vTerminalProjections: Object.freeze(terminalProjections) }),
    ...(coldProgress === undefined ? {} : { f0vColdProgress: Object.freeze(coldProgress) }) });
}

/** Reached numeric pre-half assertions are required; later assertions remain explicitly unreached. */
export function validateExpectedCrashPrefix(scenario, completedActionSteps, manifest) {
  if (!Number.isInteger(completedActionSteps) || completedActionSteps < 0 || completedActionSteps > scenario.actions.length) {
    throw new Error('persistent crash completed action prefix is malformed');
  }
  for (const assertion of scenario.assertions) {
    if (!Number.isInteger(assertion.after) || assertion.after > completedActionSteps) continue;
    const actual = diagnosticAtStep(manifest, assertion.after, assertion.view, assertion.id);
    if (actual === undefined || !matchesExpectation(actual, assertion.expect)) {
      throw new Error(`persistent crash reached assertion is absent or false: ${assertion.view} ${assertion.id}`);
    }
  }
  return Object.freeze({ completedActionSteps, unreachedAssertions: scenario.assertions.filter((assertion) => Number.isInteger(assertion.after) && assertion.after > completedActionSteps).length });
}

function validateSegmentAssertions(scenario, completedActionSteps, manifest) {
  if (!Number.isInteger(completedActionSteps) || completedActionSteps < 0 || completedActionSteps > scenario.actions.length) {
    throw new Error('persistent segment completed action prefix is malformed');
  }
  for (const assertion of scenario.assertions) {
    if (!Number.isInteger(assertion.after) || assertion.after > completedActionSteps) continue;
    const actual = diagnosticAtStep(manifest, assertion.after, assertion.view, assertion.id);
    if (actual === undefined || !matchesExpectation(actual, assertion.expect)) {
      throw new Error(`persistent segment reached assertion is absent or false: ${assertion.view} ${assertion.id}`);
    }
  }
}

function diagnosticAtStep(manifest, actionStep, view, id) {
  for (const value of [...(manifest?.diagnostics ?? [])].reverse()) {
    const observed = value?.observed?.value;
    if (observed?.pilotActionStep === actionStep && observed.kind === view && observed.id === id) return observed;
  }
  return undefined;
}
function matchesExpectation(actual, expected) { return Object.entries(expected).every(([key, value]) => value && typeof value === 'object' && !Array.isArray(value)
  ? actual[key] && matchesExpectation(actual[key], value) : actual[key] === value); }
