import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { execFile } from 'node:child_process';
import { appendFile, mkdir, readdir } from 'node:fs/promises';
import { promisify } from 'node:util';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { correlation, hasDiagnosticResponses, loadScenario, newManifest, saveManifest, selectMutterXauthority, traceRecord } from './scenario.mjs';

const [scenarioPath, outputPath = `build/frontier-v3-scenarios/${basename(process.argv[2] ?? 'scenario.json', '.json')}-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioPath) throw new Error('usage: npm run scenario -- <scenario.json> [manifest.json]');
if (!process.env.DISPLAY) throw new Error('a native visible pilot requires DISPLAY=:0');

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const scenarioFile = resolve(scenarioPath);
const output = resolve(project, outputPath);
const { scenario, sha256 } = await loadScenario(scenarioFile);
const runId = randomUUID();
const manifest = newManifest({ scenario, sha256, runId });
const tracePath = output.replace(/\.json$/i, '') + '.pmv3.jsonl';
manifest.trace = tracePath;
await mkdir(dirname(tracePath), { recursive: true });
let traceWrites = Promise.resolve();
let activeAction = null;
function trace(kind, data = {}) {
  traceWrites = traceWrites.then(() => appendFile(tracePath, `${JSON.stringify(traceRecord({ runId, kind, ...data }))}\n`, 'utf8'));
  return traceWrites;
}
trace('run_started', { scenarioId: scenario.id, scenarioSha256: sha256, profile: process.env.FRONTIER_V3_PILOT_PROFILE ?? 'lite' });
const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
const pilotTask = process.env.FRONTIER_V3_PILOT_PROFILE === 'pack'
  ? ':pale-mirror-neoforge:runFrontierV3PilotPackClient' : ':pale-mirror-neoforge:runFrontierV3PilotClient';
const args = [pilotTask, '--no-daemon',
  `-PfrontierV3PilotScenario=${scenarioFile}`,
  `-PfrontierV3PilotUsername=${scenario.pilot.username}`,
  `-PfrontierV3PilotServer=${scenario.server.host}:${scenario.server.port}`];
// The native pilot and its capture helper must authenticate to the same
// user-owned XWayland display.  The helper discovers the short-lived Mutter
// cookie without serializing it; pass that private child environment to
// Gradle as well, otherwise an explicitly XWayland pilot cannot start.
const auditEnvironment = await x11AuditEnvironment(process.env);
const child = spawn(gradle, args, { cwd: project, env: auditEnvironment, stdio: ['ignore', 'pipe', 'pipe'] });
let childExit;
child.once('exit', (code) => { childExit = code ?? 1; });
const diagnostics = [];
const frameTasks = [];
const executeFile = promisify(execFile);
const auditScript = resolve(project, 'scripts/visual-audit-x11.py');
let complete = false;
let failure = null;
let pilotStartedAt = null;
const buffers = new Map();
for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => {
  process.stdout.write(chunk);
  const buffer = (buffers.get(stream) ?? '') + chunk;
  const lines = buffer.split(/\r?\n/);
  buffers.set(stream, lines.pop());
  for (const line of lines) {
    const marker = line.indexOf('PMV3_DIAG ');
    if (marker >= 0) {
      try {
        const diagnostic = { at: new Date().toISOString(), value: JSON.parse(line.slice(marker + 'PMV3_DIAG '.length)), line: line.slice(marker) };
        diagnostics.push(diagnostic); trace('diagnostic_received', { correlation: activeAction, diagnostic: diagnostic.value });
      }
      catch { failure ??= `malformed diagnostic line: ${line}`; }
    }
    if (line.includes('PMV3_PILOT completed scenario')) complete = true;
    if (line.includes('PMV3_PILOT failed') || line.includes('PMV3_PILOT rejected')) failure ??= line;
    const completed = line.match(/PMV3_PILOT complete action step=(\d+) type=/);
    const started = line.match(/PMV3_PILOT step=(\d+) phase=(setup|action) type=([^\s]+)/);
    if (started) {
      pilotStartedAt ??= Date.now(); activeAction = started[2] === 'action' ? correlation(runId, Number(started[1])) : null;
      trace('action_started', { correlation: activeAction, phase: started[2], step: Number(started[1]), actionType: started[3] });
    }
    if (completed) {
      trace('action_completed', { correlation: correlation(runId, Number(completed[1])), step: Number(completed[1]) });
      for (const frame of (scenario.frames ?? []).filter((value) => value.after === Number(completed[1]))) {
        const destination = resolve(frame.destination ?? `build/frontier-v3-scenarios/${scenario.id}-${runId}-${frame.name}.png`);
        frameTasks.push(executeFile('python3', [auditScript, 'capture', destination], { cwd: project, env: auditEnvironment })
          .then(() => { manifest.frames.push({ after: frame.after, name: frame.name, path: destination }); trace('frame_captured', { correlation: correlation(runId, frame.after), name: frame.name, path: destination }); })
          .catch((error) => { failure ??= `frame ${frame.name} capture failed: ${String(error?.stderr ?? error)}`; }));
      }
    }
  }
});

try {
  await waitForPilot(child, () => failure || (complete && hasDiagnosticResponses(diagnostics, scenario.assertions ?? [])), () => pilotStartedAt,
    300_000, scenarioDeadlineMs(scenario));
  if (failure) throw new Error(failure);
  await Promise.all(frameTasks);
  for (const [index, action] of (scenario.actions ?? []).entries()) manifest.actions.push({ correlation: correlation(runId, index + 1), action });
  for (const assertion of scenario.assertions ?? []) {
    const observed = diagnostics.findLast((entry) => entry.value.kind === assertion.view && entry.value.id === assertion.id);
    if (!observed || !matches(observed.value, assertion.expect)) throw new Error(`diagnostic assertion failed: ${assertion.view} ${assertion.id}`);
    manifest.diagnostics.push({ assertion, observed });
  }
  manifest.status = 'ok';
} catch (error) {
  manifest.status = 'failed'; manifest.error = String(error?.stack ?? error);
} finally {
  manifest.finishedAt = new Date().toISOString(); manifest.diagnostics.push(...diagnostics);
  trace('run_finished', { status: manifest.status, error: manifest.error ?? null });
  await traceWrites;
  await saveManifest(output, manifest);
  child.kill('SIGINT');
}
if (manifest.status !== 'ok') throw new Error(manifest.error);
console.log(JSON.stringify({ status: 'ok', manifest: output, runId }));

function scenarioDeadlineMs(scenario) {
  const requested = (scenario.actions ?? []).reduce((total, action) => total + (action.type === 'wait' ? action.ms : action.timeoutMs ?? 0), 0);
  return Math.max(120_000, Math.min(600_000, requested + 30_000));
}

function waitForPilot(child, predicate, startedAt, startupTimeoutMs, scenarioTimeoutMs) {
  return new Promise((resolve, reject) => {
    const launchedAt = Date.now();
    const timer = setInterval(() => {
      if (predicate()) { clearInterval(timer); resolve(); return; }
      if (childExit !== undefined) { clearInterval(timer); reject(new Error(`native pilot exited before completion (${childExit})`)); return; }
      const scenarioStarted = startedAt();
      const deadline = scenarioStarted == null ? launchedAt + startupTimeoutMs : scenarioStarted + scenarioTimeoutMs;
      if (Date.now() > deadline) {
        clearInterval(timer);
        reject(new Error(scenarioStarted == null ? `native pilot did not start within ${startupTimeoutMs}ms`
          : `native pilot scenario timed out after ${scenarioTimeoutMs}ms`));
      }
    }, 100);
  });
}
function matches(actual, expected) {
  return Object.entries(expected).every(([key, value]) => value && typeof value === 'object' && !Array.isArray(value)
    ? actual[key] && matches(actual[key], value) : actual[key] === value);
}

async function x11AuditEnvironment(environment) {
  const resolved = { ...environment };
  if (!resolved.DISPLAY || resolved.XAUTHORITY || !resolved.XDG_RUNTIME_DIR) return resolved;
  try {
    const authority = selectMutterXauthority(await readdir(resolved.XDG_RUNTIME_DIR));
    if (authority) resolved.XAUTHORITY = join(resolved.XDG_RUNTIME_DIR, authority);
  } catch {
    // The capture command emits the attributable failure if the session directory cannot be inspected.
  }
  return resolved;
}
