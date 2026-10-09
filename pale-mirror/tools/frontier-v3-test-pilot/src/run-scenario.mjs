import { randomUUID } from 'node:crypto';
import { matches } from './diagnostic-matcher.mjs';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { execFile } from 'node:child_process';
import { appendFile, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { basename, dirname, isAbsolute, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { correlation, diagnosticForAssertion, diagnosticFromPilotLine, hasDiagnosticResponses, loadScenario, newManifest, pilotDiagnosticActionStep, saveManifest, scenarioDeadlineMs, traceRecord } from './scenario.mjs';
import { SemanticProgressMonitor } from './semantic-progress.mjs';
import { PhaseTiming } from './timing.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';
import { ensurePreparedLaunchWorkingDirectory, preparedLaunch } from './prepared-launch.mjs';
import { LifecycleBarrier, LifecycleSignal, awaitLifecycleBarrier, awaitLifecycleSignal, openLifecycleBarrierSession, publishLifecycleBarrier } from './lifecycle-barrier.mjs';
import { awaitChildExit, deadlineWatchdog } from './deadline-watchdog.mjs';
import { createEarlyDisplayFailureDetector } from './native-client-fatal-state.mjs';
import { verifiedPrivateDisplayEnvironment, verifiedVisibleDisplayEnvironment } from './visible-display.mjs';

const [scenarioPath, outputPath = `build/frontier-v3-scenarios/${basename(process.argv[2] ?? 'scenario.json', '.json')}-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioPath) throw new Error('usage: npm run scenario -- <scenario.json> [manifest.json]');
if (!process.env.DISPLAY) throw new Error('a native visible pilot requires DISPLAY=:0');

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const packPilot = process.env.FRONTIER_V3_PILOT_PROFILE === 'pack';
const PACK_PILOT_REQUIRED_MOD_IDS = Object.freeze(['create', 'geckolib', 'pale_mirror', 'pale_mirror_visuals', 'supplementaries']);
const configuredPackPilotDirectory = process.env.FRONTIER_V3_PILOT_PACK_DIRECTORY;
if (packPilot && (configuredPackPilotDirectory === undefined || !isAbsolute(configuredPackPilotDirectory))) {
  throw new Error('FRONTIER_V3_PILOT_PACK_DIRECTORY must name an absolute materialized full-pack directory');
}
const packPilotDirectory = packPilot ? resolve(configuredPackPilotDirectory) : undefined;
if (packPilot && !existsSync(join(packPilotDirectory, 'mods'))) {
  throw new Error('FRONTIER_V3_PILOT_PACK_DIRECTORY must contain mods/');
}
// Polling state changes must not register one `exit` listener per poll. Native scenarios can
// legitimately wait through many diagnostic revisions; process termination is one shared
// lifecycle fact, not a fresh subscription for every wait iteration.
const EXIT_CODES = new WeakMap();
const scenarioFile = resolve(scenarioPath);
// F0.V's persistent-client restart owns a mutable runtime copy of the scenario.  The immutable
// checked-in source above remains the manifest/assertion contract; only the runner writes the
// controlled before/after segment file, and the Minecraft client remains an ordinary client.
const runtimeScenarioFile = resolve(process.env.FRONTIER_V3_PILOT_RUNTIME_SCENARIO ?? scenarioFile);
const output = resolve(project, outputPath);
const { scenario, sha256 } = await loadScenario(scenarioFile);
// The persistent-restart supervisor mutates only its disposable runtime copy to
// install the lane-owned loopback endpoint.  The checked-in scenario remains
// the assertion contract, but every client connection must consume that exact
// runtime endpoint rather than falling back to the contract's default port.
const runtimeScenario = runtimeScenarioFile === scenarioFile ? scenario : (await loadScenario(runtimeScenarioFile)).scenario;
const sessionControlDirectory = process.env.FRONTIER_V3_PILOT_SESSION_CONTROL_DIRECTORY
  ? resolve(process.env.FRONTIER_V3_PILOT_SESSION_CONTROL_DIRECTORY) : undefined;
const lifecycleControlDirectory = process.env.FRONTIER_V3_PILOT_LIFECYCLE_CONTROL_DIRECTORY
  ? resolve(process.env.FRONTIER_V3_PILOT_LIFECYCLE_CONTROL_DIRECTORY) : undefined;
if (sessionControlDirectory !== undefined && scenario.restart === undefined) {
  throw new Error('persistent pilot session requires one declared restart boundary');
}
const lifecycle = lifecycleControlDirectory === undefined ? undefined : await openLifecycleBarrierSession(lifecycleControlDirectory,
  JSON.parse(await readFile(join(lifecycleControlDirectory, 'identity.json'), 'utf8')));
const lifecycleTerminalAssertion = process.env.FRONTIER_V3_PILOT_LIFECYCLE_TERMINAL !== 'false';
const lifecycleClose = lifecycleTerminalAssertion || process.env.FRONTIER_V3_PILOT_LIFECYCLE_CLOSE === 'true';
const runId = randomUUID();
const manifest = newManifest({ scenario, sha256, runId });
const timing = new PhaseTiming();
const tracePath = output.replace(/\.json$/i, '') + '.pmv3.jsonl';
const captureControlDirectory = output.replace(/\.json$/i, '') + `.${runId}.capture-control`;
manifest.trace = tracePath;
await mkdir(dirname(tracePath), { recursive: true });
await mkdir(captureControlDirectory, { recursive: true });
let traceWrites = Promise.resolve();
let lifecycleWrites = Promise.resolve();
let activeAction = null;
let activeActionStep = null;
let pilotRevision = 0;
const pilotWaiters = new Set();
function publishPilotState() {
  pilotRevision++;
  for (const resolveWaiter of pilotWaiters) resolveWaiter();
  pilotWaiters.clear();
}
function pilotStateAfter(revision) {
  if (pilotRevision !== revision) return Promise.resolve();
  return new Promise((resolveWaiter) => pilotWaiters.add(resolveWaiter));
}
function trace(kind, data = {}) {
  traceWrites = traceWrites.then(() => appendFile(tracePath, `${JSON.stringify(traceRecord({ runId, kind, ...data }))}\n`, 'utf8'));
  return traceWrites;
}
function lifecycleSegment() {
  if (sessionControlDirectory !== undefined) {
    return existsSync(join(sessionControlDirectory, 'resumed')) ? 'after_restart' : 'before_restart';
  }
  const segment = process.env.FRONTIER_V3_PILOT_LIFECYCLE_SEGMENT ?? 'single';
  if (!/^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}$/.test(segment)) {
    throw new Error('F0.V lifecycle segment is malformed');
  }
  return segment;
}
const lifecycleCancellation = new AbortController();
function lifecycleBarrier(barrier, signal = undefined, suffix = undefined, detail = {}) {
  if (lifecycle === undefined) return;
  lifecycleWrites = lifecycleWrites.then(async () => {
    if (signal !== undefined) await awaitLifecycleSignal(lifecycle, signal, suffix, 300_000, lifecycleCancellation.signal);
    await publishLifecycleBarrier(lifecycle, barrier, detail);
  });
  lifecycleWrites.catch((error) => { failure ??= `lifecycle barrier failure: ${String(error?.message ?? error)}`; publishPilotState(); });
}
timing.begin('client.launch_preparation');
trace('run_started', { scenarioId: scenario.id, scenarioSha256: sha256, profile: process.env.FRONTIER_V3_PILOT_PROFILE ?? 'lite' });
const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
const preparedIdentityPath = process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY;
const preparedIdentity = preparedIdentityPath === undefined || preparedIdentityPath === '' ? undefined
  : JSON.parse(await readFile(resolve(project, preparedIdentityPath), 'utf8'));
if (packPilot && preparedIdentity !== undefined) throw new Error('F0.V prepared launch supports only the disposable lite client');
// A local pilot uses the authenticated user Xwayland display; the repository-owned private
// Xvfb wrapper instead proves a task-private numeric display before it invokes this runner.
// Keep those admissions explicit so a private carrier never silently borrows :0 or its cookie.
const privateDisplay = process.env.FRONTIER_V3_PILOT_PRIVATE_DISPLAY === 'true';
const auditEnvironment = { ...(privateDisplay
  ? await verifiedPrivateDisplayEnvironment(process.env)
  : await verifiedVisibleDisplayEnvironment(process.env)) };
// The semantic pilot is one ordinary GLFW client on its admitted X11 display. Do not let an
// inherited Wayland session make GLFW wait on an unrelated compositor path before multiplayer.
auditEnvironment.XDG_SESSION_TYPE = 'x11';
auditEnvironment.WAYLAND_DISPLAY = '__pale_mirror_pilot_xwayland_only__';
auditEnvironment.PALE_MIRROR_CLIENT_SCREENSHOTS = resolve(project,
  `pale-mirror-neoforge/build/runs/${packPilot ? 'frontier-v3-pilot-pack-client' : 'frontier-v3-pilot-client'}/screenshots`);
timing.end('client.launch_preparation');
timing.begin('client.jvm_boot_and_connect');
const child = preparedIdentity === undefined
  ? launchViaGradle()
  : await launchPreparedClient();
// Direct prepared launch owns this JVM. Never send F2 to a leftover client's window.
if (preparedIdentity !== undefined) auditEnvironment.PALE_MIRROR_CLIENT_PID = String(child.pid);
// Spawn identity plus the pre-launch prepared-build verification is the only authority for this
// external barrier. Minecraft will separately acknowledge connection/fixture readiness.
const initialLifecycleSegment = lifecycleSegment();
lifecycleBarrier(LifecycleBarrier.PREPARED_CLIENT_READY, undefined, undefined, { clientPid: child.pid, segment: initialLifecycleSegment });
lifecycleBarrier(LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, LifecycleSignal.CLIENT_CONNECTED_FIXTURE_READY,
  initialLifecycleSegment, { clientPid: child.pid, segment: initialLifecycleSegment });
let childExit;
exited(child).then((code) => { childExit = code; publishPilotState(); });
const diagnostics = [];
const semanticProgress = new SemanticProgressMonitor();
const frameTasks = [];
const executeFile = promisify(execFile);
const auditScript = resolve(project, 'scripts/visual-audit-x11.py');
let complete = false;
let completedActionStep = 0;
let failure = null;
let pilotStartedAt = null;
let fixtureStarted = false;
let actionTimingName = null;
let recoveryBarrierScheduled = false;
let terminalSegmentBarrierScheduled = false;
let loadedModInventory = null;
let beforeRestartSnapshot = Promise.resolve();
let beforeRestartSnapshotScheduled = false;
const buffers = new Map();
const earlyDisplayFailure = createEarlyDisplayFailureDetector();
const sessionActionOffset = () => sessionControlDirectory !== undefined && existsSync(join(sessionControlDirectory, 'resumed'))
  ? scenario.restart.afterAction : 0;
for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => {
  process.stdout.write(chunk);
  const buffer = (buffers.get(stream) ?? '') + chunk;
  const lines = buffer.split(/\r?\n/);
  buffers.set(stream, lines.pop());
  for (const line of lines) {
    const fatalDisplay = earlyDisplayFailure.observe(line);
    if (fatalDisplay !== null) failure ??= fatalDisplay;
    const pilotDiagnostic = diagnosticFromPilotLine(line);
    if (pilotDiagnostic != null) {
      try {
        if (pilotDiagnostic.error) throw new Error(pilotDiagnostic.error);
        semanticProgress.observe(pilotDiagnostic.value);
        // A persistent Minecraft JVM intentionally reloads an after-restart segment whose local
        // pilot counter starts at one.  The immutable contract remains global, so its runner
        // correlation is authoritative during that one test-only hand-off.
        const actionStep = sessionControlDirectory !== undefined ? activeActionStep
          : pilotDiagnosticActionStep(pilotDiagnostic.value, activeActionStep);
        const diagnostic = { at: new Date().toISOString(), actionStep, value: pilotDiagnostic.value, line: pilotDiagnostic.line };
        diagnostics.push(diagnostic); trace('diagnostic_received', { correlation: actionStep == null ? null : correlation(runId, actionStep), diagnostic: diagnostic.value });
      }
      catch (error) { failure ??= `diagnostic/progress violation: ${String(error?.message ?? error)}; line=${line}`; }
    }
    const inventory = line.match(/PMV3_PILOT_LOADED_MODS\s+(\{.*\})\s*$/);
    if (inventory) {
      try {
        const value = JSON.parse(inventory[1]);
        const actual = Array.isArray(value.loaded) ? value.loaded : [];
        const required = Array.isArray(value.required) ? value.required : [];
        const missing = Array.isArray(value.missing) ? value.missing : [];
        if (!packPilot || value.status !== 'PASS'
            || JSON.stringify(required) !== JSON.stringify(PACK_PILOT_REQUIRED_MOD_IDS)
            || missing.length !== 0 || !PACK_PILOT_REQUIRED_MOD_IDS.every((id) => actual.includes(id))) {
          throw new Error('runtime full-pack loaded-mod inventory does not prove the required client closure');
        }
        loadedModInventory = value;
        manifest.loadedModInventory = value;
        trace('loaded_mod_inventory', { value });
      } catch (error) { failure ??= `invalid full-pack loaded-mod inventory: ${String(error?.message ?? error)}`; }
    }
    if (line.includes('PMV3_PILOT completed scenario')) complete = true;
    if (line.includes('PMV3_PILOT completed scenario') && !terminalSegmentBarrierScheduled) {
      terminalSegmentBarrierScheduled = true;
      const segment = lifecycleSegment();
      lifecycleBarrier(LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, LifecycleSignal.SCENARIO_SEGMENT_COMPLETE, segment, { segment });
    }
    if (line.includes('PMV3_PILOT session_segment_complete') && sessionControlDirectory !== undefined) {
      const segment = 'before_restart';
      // The persistent JVM remains alive across the server restart, so its final manifest is
      // necessarily an after-restart record.  Seal the completed first segment before exposing
      // its lifecycle barrier: the recovery carrier must bind actual HOT departure evidence,
      // never reconstruct it from the final manifest or a control nonce.
      capturePersistentBeforeRestart().then(() => {
        lifecycleBarrier(LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, LifecycleSignal.SCENARIO_SEGMENT_COMPLETE, segment, { segment });
        lifecycleBarrier(LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, LifecycleSignal.CLIENT_NORMALLY_DISCONNECTED, segment, { segment });
      }).catch((error) => { failure ??= `before-restart manifest capture failed: ${String(error?.message ?? error)}`; publishPilotState(); });
    }
    if (line.includes('PMV3_PILOT loaded scenario=') && sessionControlDirectory !== undefined
        && existsSync(join(sessionControlDirectory, 'resumed')) && !recoveryBarrierScheduled) {
      recoveryBarrierScheduled = true;
      lifecycleBarrier(LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED,
        LifecycleSignal.SAME_CLIENT_RECONNECTED_STATE_CLEARED, 'after_restart', { clientPid: child.pid });
    }
    if (line.includes('PMV3_PILOT failed') || line.includes('PMV3_PILOT rejected')) failure ??= line;
    const completed = line.match(/PMV3_PILOT complete action step=(\d+) type=/);
    const started = line.match(/PMV3_PILOT step=(\d+) phase=(setup|action) type=([^\s]+)/);
    if (started) {
      if (pilotStartedAt == null) {
        pilotStartedAt = Date.now();
        timing.end('client.jvm_boot_and_connect');
      }
      activeActionStep = started[2] === 'action' ? Number(started[1]) + sessionActionOffset() : null;
      activeAction = activeActionStep === null ? null : correlation(runId, activeActionStep);
      if (started[2] === 'setup') {
        if (!fixtureStarted) { timing.begin('fixture_readiness'); fixtureStarted = true; }
      } else {
        if (fixtureStarted) { timing.end('fixture_readiness'); fixtureStarted = false; }
        actionTimingName = `evidence.action.${activeActionStep}`;
        timing.begin(actionTimingName, { actionType: started[3] });
      }
      trace('action_started', { correlation: activeAction, phase: started[2], step: activeActionStep ?? Number(started[1]), actionType: started[3] });
    }
    if (completed) {
      if (actionTimingName !== null) { timing.end(actionTimingName); actionTimingName = null; }
      const step = Number(completed[1]) + sessionActionOffset();
      completedActionStep = Math.max(completedActionStep, step);
      trace('action_completed', { correlation: correlation(runId, step), step });
      const segment = lifecycleSegment();
      lifecycleBarrier(LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, LifecycleSignal.ACTION_CHECKPOINT,
        `${segment}-${String(Number(completed[1])).padStart(4, '0')}`, { actionStep: step, segment });
    }
    const frameReady = line.match(/PMV3_PILOT frame_ready after=(\d+) name=([^\s]+) presentation=(clean|player)/);
    if (frameReady) {
      const after = Number(frameReady[1]); const frame = (scenario.frames ?? []).find((value) => value.name === frameReady[2]);
      if (!frame) { failure ??= `pilot announced undeclared frame after=${after} name=${frameReady[2]}`; return; }
      const destination = resolve(project, frame.destination ?? `build/frontier-v3-scenarios/${scenario.id}-${runId}-${frame.name}.png`);
      const timingName = `frame.capture.${frame.name}`;
      timing.begin(timingName);
      frameTasks.push(executeFile('python3', [auditScript, 'capture', destination], { cwd: project, env: auditEnvironment })
        .then(async () => {
          timing.end(timingName);
          manifest.frames.push({ after: frame.after, name: frame.name, presentation: frameReady[3], path: destination });
          await writeFile(join(captureControlDirectory, `frame-${after}.captured`), `${frame.name}\n`, 'utf8');
          trace('frame_captured', { correlation: correlation(runId, frame.after), name: frame.name, presentation: frameReady[3], path: destination });
        })
        .catch((error) => { failure ??= `frame ${frame.name} capture failed: ${String(error?.stderr ?? error)}`; })
        .finally(() => publishPilotState()));
    }
  }
  publishPilotState();
});

try {
  await waitForPilot(child, () => failure || ((!packPilot || loadedModInventory !== null)
    && (complete || completedActionStep === (scenario.actions ?? []).length)
    && hasDiagnosticResponses(diagnostics, scenario.assertions ?? [])
    && manifest.frames.length === (scenario.frames ?? []).length), () => pilotStartedAt,
    300_000, scenarioDeadlineMs(scenario));
  if (failure) throw new Error(failure);
  await Promise.all(frameTasks);
  for (const [index, action] of (scenario.actions ?? []).entries()) manifest.actions.push({ correlation: correlation(runId, index + 1), action });
  timing.begin('terminal_assertions');
  for (const assertion of scenario.assertions ?? []) {
    const observed = diagnosticForAssertion(diagnostics, assertion);
    if (!observed || !matches(observed.value, assertion.expect)) throw new Error(`diagnostic assertion failed: ${assertion.view} ${assertion.id}`);
    manifest.diagnostics.push({ assertion, observed });
  }
  timing.end('terminal_assertions');
  manifest.semanticProgress = semanticProgress.receipts();
  // The stream queue may still contain unrelated late stdout work after the client has
  // durably published this segment.  Terminal authority is the nonce-bound journal, not an
  // incidental promise chain: wait for the exact published completion barrier before granting
  // the close token.  This keeps a completed client from being parked behind a stale stream
  // waiter while retaining the same monotonic action→segment→terminal ordering.
  if (lifecycle !== undefined) {
    const segment = lifecycleSegment();
    await awaitLifecycleSignal(lifecycle, LifecycleSignal.SCENARIO_SEGMENT_COMPLETE, segment, 300_000);
    await awaitLifecycleBarrier(lifecycle, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, 300_000,
      entry => entry.detail.segment === segment);
  } else await lifecycleWrites;
  if (lifecycleTerminalAssertion) {
    lifecycleBarrier(LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, undefined, undefined, { assertionCount: (scenario.assertions ?? []).length });
    if (lifecycle !== undefined) {
      await awaitLifecycleBarrier(lifecycle, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, 300_000,
        entry => entry.detail.assertionCount === (scenario.assertions ?? []).length);
    } else await lifecycleWrites;
  }
  if (lifecycle !== undefined && lifecycleClose) {
    const segment = lifecycleSegment();
    const close = join(lifecycle.directory, `close-client-${segment}.token`);
    await writeFile(close, `${lifecycle.identity.runId}:${segment}\n`, { encoding: 'utf8', flag: 'wx' });
    await lifecycleSignalOrExit(LifecycleSignal.CLIENT_NORMALLY_DISCONNECTED, segment,
      'native pilot exited before its normal disconnect acknowledgement');
    await publishLifecycleBarrier(lifecycle, LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { clientPid: child.pid, segment });
    const exitCode = await exited(child);
    if (exitCode !== 0) throw new Error(`native pilot exited after its normal disconnect acknowledgement (${exitCode})`);
  }
  manifest.status = 'ok';
} catch (error) {
  manifest.status = 'failed'; manifest.error = String(error?.stack ?? error);
} finally {
  if (fixtureStarted) timing.end('fixture_readiness');
  if (actionTimingName !== null) timing.end(actionTimingName);
  manifest.finishedAt = new Date().toISOString(); manifest.diagnostics.push(...diagnostics);
  manifest.semanticProgress = semanticProgress.receipts();
  trace('run_finished', { status: manifest.status, error: manifest.error ?? null });
  await traceWrites;
  if (manifest.status !== 'ok') lifecycleCancellation.abort();
  // A lifecycle signal can be absent only because the scenario itself failed; retain the exact
  // journal for the parent failure bundle instead of turning cleanup into an unbounded wait.
  await lifecycleWrites.catch(() => undefined);
  await beforeRestartSnapshot.catch(() => undefined);
  timing.begin('client.cleanup');
  await stopOwnedClient(child);
  await rm(captureControlDirectory, { recursive: true, force: true });
  timing.end('client.cleanup');
  timing.abortOpen({ status: manifest.status });
  manifest.timing = timing.finish({ runner: 'native-client' });
  await saveManifest(output, manifest);
}
if (manifest.status !== 'ok') throw new Error(manifest.error);
console.log(JSON.stringify({ status: 'ok', manifest: output, runId }));

/**
 * A direct prepared JVM is an exact task-owned client, not a detached display process.
 * A scenario timeout used to send one best-effort SIGINT and let a blocked GLFW/JVM remain
 * orphaned after its Node parent had published failure evidence.  Preserve the first failure,
 * but close that one client with bounded graceful and escalation windows before declaring the
 * disposable segment cleaned up.
 */
async function stopOwnedClient(client) {
  if (client.exitCode !== null || client.signalCode !== null) return;
  const attempts = [
    { signal: 'SIGINT', timeoutMs: 15_000 },
    { signal: 'SIGTERM', timeoutMs: 10_000 },
    { signal: 'SIGKILL', timeoutMs: 5_000 }
  ];
  for (const attempt of attempts) {
    if (client.exitCode !== null || client.signalCode !== null) return;
    client.kill(attempt.signal);
    try {
      await awaitChildExit(client, attempt.timeoutMs, `task-owned native client did not exit after ${attempt.signal}`);
      return;
    } catch (error) {
      if (attempt.signal === 'SIGKILL') throw error;
    }
  }
}

/** Immutable first-segment receipt for a one-client graceful restart. */
function capturePersistentBeforeRestart() {
  if (beforeRestartSnapshotScheduled) return beforeRestartSnapshot;
  beforeRestartSnapshotScheduled = true;
  const split = scenario.restart?.afterAction;
  if (!Number.isInteger(split) || split < 1 || split >= (scenario.actions ?? []).length) {
    return beforeRestartSnapshot = Promise.reject(new Error('persistent pilot restart split is invalid'));
  }
  const path = output.replace(/\.json$/i, '') + '.before-restart.json';
  beforeRestartSnapshot = beforeRestartSnapshot.then(async () => {
    const assertions = (scenario.assertions ?? []).filter(assertion => assertion.after <= split);
    const asserted = assertions.map(assertion => {
      const observed = diagnosticForAssertion(diagnostics, assertion);
      if (!observed || !matches(observed.value, assertion.expect)) {
        throw new Error(`first segment diagnostic assertion failed: ${assertion.view} ${assertion.id}`);
      }
      return { assertion, observed };
    });
    const snapshot = {
      ...manifest,
      status: 'ok',
      finishedAt: new Date().toISOString(),
      actions: (scenario.actions ?? []).slice(0, split).map((action, index) => ({ correlation: correlation(runId, index + 1), action })),
      // Keep assertion-bound observations plus the raw phase/motion receipts.  Consumers choose
      // the assertion-bound fact where available and cannot silently substitute later polls.
      diagnostics: [...asserted, ...diagnostics.filter(entry => entry.actionStep == null || entry.actionStep <= split)],
      frames: [],
      timing: { runner: 'native-client', segment: 'before_restart', status: 'ok' }
    };
    await saveManifest(path, snapshot);
  });
  return beforeRestartSnapshot;
}

function launchViaGradle() {
  const pilotTask = packPilot
    ? ':pale-mirror-neoforge:runFrontierV3PilotPackClient' : ':pale-mirror-neoforge:runFrontierV3PilotClient';
  const args = [pilotTask, '--no-daemon',
    `-PfrontierV3PilotScenario=${runtimeScenarioFile}`,
    `-PfrontierV3PilotCaptureControlDirectory=${captureControlDirectory}`,
    `-PfrontierV3PilotUsername=${scenario.pilot.username}`,
    `-PfrontierV3PilotServer=${runtimeScenario.server.host}:${runtimeScenario.server.port}`];
  if (sessionControlDirectory !== undefined) args.push(`-PfrontierV3PilotSessionControlDirectory=${sessionControlDirectory}`);
  if (lifecycleControlDirectory !== undefined) {
    args.push(`-PfrontierV3PilotLifecycleControlDirectory=${lifecycleControlDirectory}`,
      `-PfrontierV3PilotLifecycleSegment=${lifecycleSegment()}`);
  }
  if (packPilot) {
    args.push(`-PfrontierV3PilotPackSource=${packPilotDirectory}`,
      `-PfrontierV3PilotRequiredMods=${PACK_PILOT_REQUIRED_MOD_IDS.join(',')}`);
  }
  return spawn(gradle, args, { cwd: project, env: auditEnvironment, stdio: ['ignore', 'pipe', 'pipe'] });
}

async function launchPreparedClient() {
  // The matrix prepared this disposable directory before fingerprinting its build. A client
  // launch may only verify it now; running Gradle reset/setup here would mutate a selected
  // launch input between the server and client halves of one causal experiment.
  await requirePreparedF0vBuild(project, preparedIdentity);
  const launch = await preparedLaunch(project, preparedIdentity, 'client', {
    'pale_mirror.frontier_v3.test_pilot.capture_control_directory': captureControlDirectory,
    'pale_mirror.frontier_v3.test_pilot.scenario': runtimeScenarioFile,
    'pale_mirror.frontier_v3.test_pilot.server': `${runtimeScenario.server.host}:${runtimeScenario.server.port}`,
    'pale_mirror.frontier_v3.test_pilot.session_control_directory': sessionControlDirectory ?? '',
    'pale_mirror.frontier_v3.test_pilot.lifecycle_control_directory': lifecycleControlDirectory ?? '',
    'pale_mirror.frontier_v3.test_pilot.lifecycle_segment': lifecycleSegment(),
    'pale_mirror.frontier_v3.test_pilot.lifecycle_close': lifecycleClose ? 'true' : 'false',
    'pale_mirror.frontier_v3.test_pilot.lifecycle_terminal': lifecycleTerminalAssertion ? 'true' : 'false'
  }, ['--username', scenario.pilot.username, '--quickPlayMultiplayer', `${runtimeScenario.server.host}:${runtimeScenario.server.port}`]);
  await ensurePreparedLaunchWorkingDirectory(launch, { automatedSemanticClient: true });
  auditEnvironment.XDG_SESSION_TYPE = 'x11';
  // An actual Xwayland session must not let the semantic client select Wayland directly.
  // A task-private Xvfb has no Wayland compositor at all; inventing a nonempty socket name
  // there makes GLFW select an unavailable platform before it can use the valid X display.
  if (auditEnvironment.WAYLAND_DISPLAY) auditEnvironment.WAYLAND_DISPLAY = '__pale_mirror_pilot_xwayland_only__';
  return spawn(launch.command, launch.args, { cwd: launch.cwd, env: auditEnvironment, stdio: ['ignore', 'pipe', 'pipe'] });
}

function exited(child) {
  if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve(child.exitCode ?? 1);
  let observed = EXIT_CODES.get(child);
  if (observed === undefined) {
    // The Gradle/Minecraft child can emit its final lifecycle line after its process `exit`
    // event but before its stdout/stderr streams close.  Treat this runner as complete only
    // after those streams have drained, otherwise a persistent restart can publish its typed
    // reconnect acknowledgement too late for the outer lifecycle supervisor to consume it.
    observed = new Promise((resolveExit) => child.once('close', (code) => resolveExit(code ?? 1)));
    EXIT_CODES.set(child, observed);
  }
  return observed;
}

async function lifecycleSignalOrExit(signal, suffix, exitLabel) {
  const acknowledgement = awaitLifecycleSignal(lifecycle, signal, suffix, 300_000);
  const code = await Promise.race([
    acknowledgement,
    exited(child).then((exitCode) => { throw new Error(`${exitLabel} (${exitCode})`); })
  ]);
  return code;
}

function waitForPilot(child, predicate, startedAt, startupTimeoutMs, scenarioTimeoutMs) {
  return (async () => {
    const launchedAt = Date.now();
    while (true) {
      if (predicate()) return;
      if (childExit !== undefined) throw new Error(`native pilot exited before completion (${childExit})`);
      const scenarioStarted = startedAt();
      const deadline = scenarioStarted == null ? launchedAt + startupTimeoutMs : scenarioStarted + scenarioTimeoutMs;
      const remaining = deadline - Date.now();
      if (remaining <= 0) throw new Error(scenarioStarted == null ? `native pilot did not start within ${startupTimeoutMs}ms`
        : `native pilot scenario timed out after ${scenarioTimeoutMs}ms`);
      const revision = pilotRevision;
      // The deadline is an attributable failure boundary, not a background handle that may
      // keep an otherwise completed native client/runner alive for its entire original window.
      // Every non-timeout winner therefore closes its own watchdog; the next loop constructs a
      // fresh one from the same absolute deadline.
      const watchdog = deadlineWatchdog(remaining, `native pilot did not publish a required state within ${remaining}ms`);
      try {
        await Promise.race([pilotStateAfter(revision), exited(child), watchdog.wait]);
      } finally {
        watchdog.close();
      }
    }
  })();
}
