import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { execFile } from 'node:child_process';
import { readdir } from 'node:fs/promises';
import { promisify } from 'node:util';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { correlation, hasDiagnosticResponses, loadScenario, newManifest, saveManifest, selectMutterXauthority } from './scenario.mjs';

const [scenarioPath, outputPath = `build/frontier-v3-scenarios/${basename(process.argv[2] ?? 'scenario.json', '.json')}-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioPath) throw new Error('usage: npm run scenario -- <scenario.json> [manifest.json]');
if (!process.env.DISPLAY) throw new Error('a native visible pilot requires DISPLAY=:0');

const { scenario, sha256 } = await loadScenario(resolve(scenarioPath));
const runId = randomUUID();
const manifest = newManifest({ scenario, sha256, runId });
const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
const args = [':pale-mirror-neoforge:runFrontierV3PilotClient', '--no-daemon',
  `-PfrontierV3PilotScenario=${resolve(scenarioPath)}`,
  `-PfrontierV3PilotUsername=${scenario.pilot.username}`,
  `-PfrontierV3PilotServer=${scenario.server.host}:${scenario.server.port}`];
// The native pilot and its capture helper must authenticate to the same
// user-owned XWayland display.  The helper discovers the short-lived Mutter
// cookie without serializing it; pass that private child environment to
// Gradle as well, otherwise an explicitly XWayland pilot cannot start.
const auditEnvironment = await x11AuditEnvironment(process.env);
const child = spawn(gradle, args, { cwd: project, env: auditEnvironment, stdio: ['ignore', 'pipe', 'pipe'] });
const diagnostics = [];
const frameTasks = [];
const executeFile = promisify(execFile);
const auditScript = resolve(project, 'scripts/visual-audit-x11.py');
let complete = false;
let failure = null;
let pilotStartedAt = null;
for (const stream of [child.stdout, child.stderr]) stream.setEncoding('utf8').on('data', (chunk) => {
  process.stdout.write(chunk);
  for (const line of chunk.split(/\r?\n/)) {
    const marker = line.indexOf('PMV3_DIAG ');
    if (marker >= 0) {
      try { diagnostics.push({ at: new Date().toISOString(), value: JSON.parse(line.slice(marker + 'PMV3_DIAG '.length)), line: line.slice(marker) }); }
      catch { failure ??= `malformed diagnostic line: ${line}`; }
    }
    if (line.includes('PMV3_PILOT completed scenario')) complete = true;
    if (line.includes('PMV3_PILOT failed') || line.includes('PMV3_PILOT rejected')) failure ??= line;
    const completed = line.match(/PMV3_PILOT complete action step=(\d+) type=/);
    if (line.includes('PMV3_PILOT step=')) pilotStartedAt ??= Date.now();
    if (completed) {
      for (const frame of (scenario.frames ?? []).filter((value) => value.after === Number(completed[1]))) {
        const destination = resolve(frame.destination ?? `build/frontier-v3-scenarios/${scenario.id}-${runId}-${frame.name}.png`);
        frameTasks.push(executeFile('python3', [auditScript, 'capture', destination], { cwd: project, env: auditEnvironment })
          .then(() => manifest.frames.push({ after: frame.after, name: frame.name, path: destination }))
          .catch((error) => { failure ??= `frame ${frame.name} capture failed: ${String(error?.stderr ?? error)}`; }));
      }
    }
  }
});

try {
  await waitForPilot(child, () => failure || (complete && hasDiagnosticResponses(diagnostics, scenario.assertions ?? [])), () => pilotStartedAt,
    300_000, 120_000);
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
  await saveManifest(resolve(outputPath), manifest);
  child.kill('SIGINT');
}
if (manifest.status !== 'ok') throw new Error(manifest.error);
console.log(JSON.stringify({ status: 'ok', manifest: resolve(outputPath), runId }));

function waitForPilot(child, predicate, startedAt, startupTimeoutMs, scenarioTimeoutMs) {
  return new Promise((resolve, reject) => {
    const launchedAt = Date.now();
    const timer = setInterval(() => {
      if (predicate()) { clearInterval(timer); resolve(); return; }
      const scenarioStarted = startedAt();
      const deadline = scenarioStarted == null ? launchedAt + startupTimeoutMs : scenarioStarted + scenarioTimeoutMs;
      if (Date.now() > deadline) {
        clearInterval(timer);
        reject(new Error(scenarioStarted == null ? `native pilot did not start within ${startupTimeoutMs}ms`
          : `native pilot scenario timed out after ${scenarioTimeoutMs}ms`));
      }
    }, 100);
    child.once('exit', (code) => { clearInterval(timer); if (!predicate()) reject(new Error(`native pilot exited before completion (${code})`)); });
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
