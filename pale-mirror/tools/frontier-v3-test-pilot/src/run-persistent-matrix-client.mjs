import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { diagnosticFromPilotLine, loadScenario, pilotFailureFromLine } from './scenario.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';
import { preparedLaunch } from './prepared-launch.mjs';
import { LifecycleSignal, awaitLifecycleSignal, openLifecycleBarrierSession } from './lifecycle-barrier.mjs';
import { awaitPersistentMatrixFinalClose, publishPersistentMatrixResult, validatePersistentMatrixPlan } from './persistent-matrix.mjs';
import { awaitWithin, childExitCancellation } from './deadline-watchdog.mjs';
import { verifiedVisibleDisplayEnvironment } from './visible-display.mjs';

export const MAX_DIAGNOSTIC_VALUES_PER_SEGMENT = 1024;
export const MAX_DIAGNOSTIC_LINE_BYTES = 64 * 1024;
export const MAX_DIAGNOSTIC_SEGMENT_BYTES = 1024 * 1024;
export const MAX_DIAGNOSTIC_WORKER_BYTES = 32 * 1024 * 1024;
const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

export async function main(argumentsValue = process.argv.slice(2)) {
  const [planPath, outputPath] = argumentsValue;
  if (!planPath || !outputPath || argumentsValue.length !== 2) throw new Error('usage: persistent matrix client <client-plan.json> <manifest.json>');
  const source = JSON.parse(await readFile(resolve(project, planPath), 'utf8'));
  const identityPath = resolve(project, process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY ?? source.preparedIdentity);
  const identity = Object.freeze(JSON.parse(await readFile(identityPath, 'utf8')));
  const sessionDirectory = resolve(project, source.sessionDirectory);
  const lifecycle = await openLifecycleBarrierSession(resolve(project, source.lifecycleDirectory), source.lifecycleIdentity);
  const plan = validatePersistentMatrixPlan(source.plan);
  if (plan.workerId !== lifecycle.identity.workerId || plan.buildIdentitySha256 !== lifecycle.identity.buildIdentitySha256) throw new Error('persistent matrix client plan does not match lifecycle identity');
  if (!Array.isArray(source.scenarios) || source.scenarios.length !== plan.segments.length) throw new Error('persistent matrix client scenarios are malformed');
  const entries = await Promise.all(source.scenarios.map(async (entry, epoch) => {
    const contract = plan.segments[epoch]; const path = resolve(project, entry.path); const loaded = await loadScenario(path);
    if (!entry || entry.id !== contract.id || loaded.scenario.id !== contract.scenarioId || loaded.sha256 !== contract.scenarioSha256) throw new Error('persistent matrix client scenario is foreign or stale');
    return Object.freeze({ ...contract, epoch, path, scenario: loaded.scenario });
  }));
  const output = resolve(project, outputPath); const failureAbort = new AbortController(); let lifecycleFailure;
  const fail = (failure) => { lifecycleFailure ??= failure; if (!failureAbort.signal.aborted) failureAbort.abort(failure); };
  const router = createStampedDiagnosticRouter(entries, lifecycle.identity, (line) => {
    const fatal = pilotFailureFromLine(line); if (fatal !== null) fail(new Error(`persistent matrix pilot lifecycle failed: ${fatal}`));
  });
  const report = { schema: 1, kind: 'frontier-v3-persistent-matrix-client', status: 'running', build: identity, lifecycle: lifecycle.identity, clientPid: null, segments: [] };
  const visibleClientEnvironment = await verifiedVisibleDisplayEnvironment(); let child;
  try {
    await requirePreparedF0vBuild(project, identity);
    const launch = await preparedLaunch(project, identity, 'client', {
      'pale_mirror.frontier_v3.test_pilot.scenario': resolve(sessionDirectory, 'runtime-scenario.json'),
      'pale_mirror.frontier_v3.test_pilot.session_control_directory': sessionDirectory,
      'pale_mirror.frontier_v3.test_pilot.session_mode': 'matrix',
      'pale_mirror.frontier_v3.test_pilot.lifecycle_control_directory': lifecycle.directory,
      'pale_mirror.frontier_v3.test_pilot.server': source.server
    }, ['--username', source.username, '--quickPlayMultiplayer', source.server]);
    child = spawn(launch.command, launch.args, { cwd: launch.cwd, env: visibleClientEnvironment, stdio: ['ignore', 'pipe', 'pipe'] }); report.clientPid = child.pid;
    for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => {
      process.stdout.write(chunk); try { router.acceptChunk(stream, chunk); } catch (failure) { fail(failure); }
    });
    for (const entry of entries) {
      const connected = entry.epoch === 0 ? LifecycleSignal.CLIENT_CONNECTED_FIXTURE_READY : LifecycleSignal.SAME_CLIENT_RECONNECTED_STATE_CLEARED;
      await signalOrExit(lifecycle, child, () => lifecycleFailure, failureAbort.signal, connected, entry.id, `client connect for ${entry.id}`);
      if (entry.completion === 'expected_crash') {
        // Java publishes this immutable prefix while claiming the arm and returns from that
        // tick.  It is the sole ownership decision: pipe/lifecycle arrival order cannot add
        // a later checkpoint to the crash half.
        const armed = await signalOrExit(lifecycle, child, () => lifecycleFailure, failureAbort.signal,
          LifecycleSignal.EXPECTED_LOSS_ARMED, entry.id, `expected-loss arm for ${entry.id}`);
        const completedActionSteps = checkedExpectedPrefix(armed.detail, entry.scenario.actions.length, 'arm acknowledgement');
        for (let action = 1; action <= completedActionSteps; action++) {
          await signalOrExit(lifecycle, child, () => lifecycleFailure, failureAbort.signal, LifecycleSignal.ACTION_CHECKPOINT,
            `${entry.id}-${String(action).padStart(4, '0')}`, `acknowledged action ${action} for ${entry.id}`);
        }
        const loss = await signalOrExit(lifecycle, child, () => lifecycleFailure, failureAbort.signal,
          LifecycleSignal.CLIENT_EXPECTED_LOSS, entry.id, `expected loss for ${entry.id}`);
        if (checkedExpectedPrefix(loss.detail, entry.scenario.actions.length, 'expected loss') !== completedActionSteps) {
          throw new Error(`persistent expected-loss prefix diverged for ${entry.id}`);
        }
        await awaitRequiredEvidenceOrExit(router, entry, (values) => reachedAssertionsPresent(entry, values, completedActionSteps), child,
          failureAbort.signal, `reached crash assertions for ${entry.id}`);
        router.seal(entry.id); report.segments.push({ id: entry.id, worldKey: entry.worldKey, expectedLoss: true, completedActionSteps, diagnostics: router.values(entry.id), lateDiagnostics: router.lateValues(entry.id) });
        continue;
      }
      for (let action = 1; action <= entry.scenario.actions.length; action++) await signalOrExit(lifecycle, child, () => lifecycleFailure, failureAbort.signal, LifecycleSignal.ACTION_CHECKPOINT, `${entry.id}-${String(action).padStart(4, '0')}`, `action ${action} for ${entry.id}`);
      await signalOrExit(lifecycle, child, () => lifecycleFailure, failureAbort.signal, LifecycleSignal.SCENARIO_SEGMENT_COMPLETE, entry.id, `terminal segment ${entry.id}`);
      await awaitRequiredEvidenceOrExit(router, entry, (values) => allAssertionsPresent(entry, values), child,
        failureAbort.signal, `all reached assertions for ${entry.id}`);
      const terminal = verifyTerminal(entry, router.values(entry.id));
      await publishPersistentMatrixResult({ directory: sessionDirectory, identity: lifecycle.identity, plan }, entry.epoch,
        { schema: 1, kind: 'frontier-v3-pilot-matrix-result', runId: lifecycle.identity.runId, epoch: entry.epoch, segment: entry.id, scenarioSha256: entry.scenarioSha256, status: 'ok', terminal });
      router.seal(entry.id); report.segments.push({ id: entry.id, worldKey: entry.worldKey, terminal, diagnostics: router.values(entry.id), lateDiagnostics: router.lateValues(entry.id) });
      if (!entry.final) await signalOrExit(lifecycle, child, () => lifecycleFailure, failureAbort.signal, LifecycleSignal.CLIENT_NORMALLY_DISCONNECTED, entry.id, `disconnect for ${entry.id}`);
      else {
        const cancellation = childExitCancellation(child);
        try { await awaitPersistentMatrixFinalClose({ directory: sessionDirectory, identity: lifecycle.identity, plan }, entry.epoch, 300_000, AbortSignal.any([cancellation.signal, failureAbort.signal])); }
        finally { cancellation.close(); }
        await signalOrExit(lifecycle, child, () => lifecycleFailure, failureAbort.signal, LifecycleSignal.CLIENT_NORMALLY_DISCONNECTED, entry.id, `final disconnect for ${entry.id}`);
      }
    }
    report.status = 'ok'; const code = await exitWithin(child, 30_000); if (code !== 0) throw new Error(`persistent matrix client exited with ${code}`);
  } catch (error) { report.status = 'failed'; report.error = String(error?.stack ?? error); if (child && child.exitCode === null && child.signalCode === null) child.kill('SIGINT'); throw error; }
  finally { await mkdir(dirname(output), { recursive: true }); await writeFile(output, `${JSON.stringify(report, null, 2)}\n`); }
  return report;
}

/** Routes diagnostic lines by Java's local session stamp, never pipe arrival order. */
export function createStampedDiagnosticRouter(entries, identity, onCompletedLine = () => {}) {
  const buckets = new Map(entries.map((entry) => [entry.id, { entry, values: [], lateValues: [], bytes: 0, sealedValues: null, waiters: new Set() }])); const fragments = new Map(); const setupTrace = []; let workerBytes = 0;
  const wake = (bucket) => { for (const waiter of [...bucket.waiters]) waiter(); };
  const accept = (line) => {
    if (Buffer.byteLength(line) > MAX_DIAGNOSTIC_LINE_BYTES) throw new Error('persistent diagnostic line exceeds bounded capacity');
    const parsed = diagnosticFromPilotLine(line); if (parsed === null) return;
    if (parsed.error) throw new Error(`persistent matrix pilot diagnostic rejected: ${parsed.error}`);
    const value = parsed.value;
    const stampFields = ['pilotRunId', 'pilotSessionId', 'pilotEpoch', 'pilotSegment', 'pilotActionStep'];
    const present = stampFields.filter((field) => Object.hasOwn(value ?? {}, field));
    if (present.length === 0) {
      const bytes = Buffer.byteLength(JSON.stringify(value));
      if (setupTrace.length >= MAX_DIAGNOSTIC_VALUES_PER_SEGMENT || workerBytes + bytes > MAX_DIAGNOSTIC_WORKER_BYTES) throw new Error('persistent diagnostic setup trace capacity exceeded');
      setupTrace.push(Object.freeze(structuredClone(value))); workerBytes += bytes;
      return;
    }
    if (present.length !== stampFields.length) throw new Error('persistent diagnostic stamp is incomplete');
    const bucket = buckets.get(value.pilotSegment);
    if (!bucket || value.pilotRunId !== identity.runId || value.pilotSessionId !== identity.sessionId || value.pilotEpoch !== bucket.entry.epoch
        || !Number.isInteger(value.pilotActionStep) || value.pilotActionStep < 1) throw new Error('persistent diagnostic stamp is foreign, stale or malformed');
    const bytes = Buffer.byteLength(JSON.stringify(value));
    if (bucket.values.length + bucket.lateValues.length >= MAX_DIAGNOSTIC_VALUES_PER_SEGMENT || bucket.bytes + bytes > MAX_DIAGNOSTIC_SEGMENT_BYTES || workerBytes + bytes > MAX_DIAGNOSTIC_WORKER_BYTES) throw new Error('persistent diagnostic retention capacity exceeded');
    const retained = Object.freeze({ actionStep: value.pilotActionStep, value: structuredClone(value) });
    if (bucket.sealedValues !== null) bucket.lateValues.push(retained); else { bucket.values.push(retained); wake(bucket); }
    bucket.bytes += bytes; workerBytes += bytes;
  };
  return Object.freeze({
    acceptLine: accept,
    acceptChunk(stream, chunk) {
      const joined = `${fragments.get(stream) ?? ''}${chunk}`;
      const lines = joined.split(/\r?\n/); const fragment = lines.pop();
      if (Buffer.byteLength(fragment) > MAX_DIAGNOSTIC_LINE_BYTES) throw new Error('persistent diagnostic unfinished line exceeds bounded capacity');
      fragments.set(stream, fragment); for (const line of lines) { onCompletedLine(line); accept(line); }
    },
    seal(id) { const bucket = buckets.get(id); if (!bucket) throw new Error('persistent diagnostic bucket is unknown'); if (bucket.sealedValues === null) bucket.sealedValues = Object.freeze(bucket.values.map((value) => Object.freeze(structuredClone(value)))); },
    values(id) { const bucket = buckets.get(id); if (!bucket) throw new Error('persistent diagnostic bucket is unknown'); const values = bucket.sealedValues ?? bucket.values; return Object.freeze(values.map((value) => Object.freeze(structuredClone(value)))); },
    lateValues(id) { const bucket = buckets.get(id); if (!bucket) throw new Error('persistent diagnostic bucket is unknown'); return Object.freeze(bucket.lateValues.map((value) => Object.freeze(structuredClone(value)))); },
    async awaitRequired(id, predicate, signal = undefined, label = `diagnostics for ${id}`, timeoutMs = 300_000) {
      const bucket = buckets.get(id); if (!bucket || typeof predicate !== 'function') throw new Error('persistent diagnostic requirement is malformed');
      const until = Date.now() + timeoutMs;
      while (!predicate(bucket.values)) {
        if (signal?.aborted) throw signal.reason instanceof Error ? signal.reason : new Error(`persistent diagnostic wait cancelled: ${label}`);
        const remaining = until - Date.now(); if (remaining <= 0) throw new Error(`persistent diagnostic requirement timed out: ${label}`);
        await new Promise((resolveWait, rejectWait) => {
          const timer = setTimeout(finish, remaining); const abort = () => finish(signal.reason instanceof Error ? signal.reason : new Error(`persistent diagnostic wait cancelled: ${label}`));
          function finish(error = undefined) { clearTimeout(timer); bucket.waiters.delete(wakeWait); signal?.removeEventListener('abort', abort); error === undefined ? resolveWait() : rejectWait(error); }
          const wakeWait = () => finish(); bucket.waiters.add(wakeWait); if (signal?.aborted) abort(); else signal?.addEventListener('abort', abort, { once: true });
        });
      }
    },
    setupTrace() { return Object.freeze(setupTrace.map((value) => Object.freeze(structuredClone(value)))); }
  });
}

function verifyTerminal(entry, values) {
  const assertions = entry.scenario.assertions.filter((assertion) => assertion.after === entry.scenario.actions.length); if (assertions.length === 0) throw new Error(`persistent matrix segment ${entry.id} lacks terminal assertions`);
  if (!assertionsPresent(values, assertions)) throw new Error(`persistent matrix terminal assertion failed: ${entry.id}`);
  return Object.freeze({ assertionCount: assertions.length, diagnosticCount: values.length });
}
function allAssertionsPresent(entry, values) { return assertionsPresent(values, entry.scenario.assertions); }
function reachedAssertionsPresent(entry, values, completedActionSteps) { return entry.scenario.assertions.filter((assertion) => assertion.after <= completedActionSteps).every((assertion) => assertionPresent(values, assertion)); }
function assertionsPresent(values, assertions) { return assertions.length > 0 && assertions.every((assertion) => values.findLast((item) => item.actionStep === assertion.after && item.value.kind === assertion.view && item.value.id === assertion.id && matches(item.value, assertion.expect))); }
function assertionPresent(values, assertion) { return values.findLast((item) => item.actionStep === assertion.after && item.value.kind === assertion.view && item.value.id === assertion.id && matches(item.value, assertion.expect)); }
/** Required pipe evidence races the exact owned client exit, never a synthetic polling timer. */
export async function awaitRequiredEvidenceOrExit(router, entry, predicate, child, failureSignal, label) {
  const cancellation = childExitCancellation(child); const signal = AbortSignal.any([cancellation.signal, failureSignal]);
  try { return await router.awaitRequired(entry.id, predicate, signal, label); }
  catch (error) {
    if (cancellation.signal.aborted) throw new Error(`persistent matrix client exited before ${label} (${cancellation.signal.reason})`);
    throw error;
  } finally { cancellation.close(); }
}
async function signalOrExit(lifecycle, child, failure, failureSignal, signal, suffix, label) {
  if (failure() !== undefined) throw failure(); const cancellation = childExitCancellation(child);
  const aborted = AbortSignal.any([cancellation.signal, failureSignal]);
  try { return await awaitLifecycleSignal(lifecycle, signal, suffix, 300_000, aborted); }
  catch (error) { if (failure() !== undefined) throw failure(); if (cancellation.signal.aborted) throw new Error(`persistent matrix client exited before ${label} (${cancellation.signal.reason})`); throw error; }
  finally { cancellation.close(); }
}
function checkedExpectedPrefix(detail, actionCount, label) { const prefix = detail?.completedActionSteps; if (!Number.isInteger(prefix) || prefix < 0 || prefix > actionCount) throw new Error(`persistent ${label} completed action prefix is malformed`); return prefix; }
function matches(actual, expected) { return Object.entries(expected).every(([key, value]) => value && typeof value === 'object' && !Array.isArray(value) ? actual[key] && matches(actual[key], value) : actual[key] === value); }
function exited(child) { return child.exitCode !== null || child.signalCode !== null ? Promise.resolve(child.exitCode ?? 1) : new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1))); }
function exitWithin(child, timeout) { return awaitWithin(exited(child), timeout, `persistent matrix client did not exit within ${timeout}ms`); }
