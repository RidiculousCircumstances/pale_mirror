import { randomUUID } from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { fileURLToPath } from 'node:url';
import { basename, dirname, resolve } from 'node:path';
import { connectPilot, inspect, perform } from './pilot.mjs';
import { correlation, loadScenario, newManifest, saveManifest } from './scenario.mjs';

const [scenarioPath, outputPath = `build/frontier-v3-scenarios/${basename(process.argv[2] ?? 'scenario.json', '.json')}-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioPath) throw new Error('usage: npm run scenario -- <scenario.json> [manifest.json]');

const { scenario, sha256 } = await loadScenario(resolve(scenarioPath));
const runId = randomUUID();
const manifest = newManifest({ scenario, sha256, runId });
const diagnostics = [];
const executeFile = promisify(execFile);
const auditScript = resolve(dirname(fileURLToPath(import.meta.url)), '../../../scripts/visual-audit-x11.py');
const bot = await connectPilot({ ...scenario.server, ...scenario.pilot }, (line) => diagnostics.push({ at: new Date().toISOString(), line }));
try {
  for (const [index, action] of (scenario.setup ?? []).entries()) {
    const result = await perform(bot, action);
    manifest.setup.push({ correlation: correlation(runId, `setup-${index + 1}`), action, result });
  }
  await observeStep(0);
  for (const [index, action] of (scenario.actions ?? []).entries()) {
    const before = diagnostics.length;
    const result = await perform(bot, action);
    manifest.actions.push({ correlation: correlation(runId, index + 1), action, result, diagnostics: diagnostics.slice(before) });
    await observeStep(index + 1);
  }
  manifest.diagnostics = diagnostics;
  manifest.finishedAt = new Date().toISOString();
  await saveManifest(resolve(outputPath), manifest);
  console.log(JSON.stringify({ status: 'ok', manifest: resolve(outputPath), runId }));
} catch (error) {
  manifest.error = String(error?.stack ?? error);
  manifest.diagnostics = diagnostics;
  manifest.finishedAt = new Date().toISOString();
  await saveManifest(resolve(outputPath), manifest);
  throw error;
} finally {
  bot.quit('frontier v3 scenario complete');
}

async function observeStep(after) {
  for (const assertion of (scenario.assertions ?? []).filter((value) => value.after === after)) {
    const id = assertion.id === '$pilot' ? `player:${bot.uuid}` : assertion.id;
    const observed = await inspect(bot, assertion.view, id, assertion.timeoutMs ?? 10_000);
    if (!matches(observed.value, assertion.expect)) {
      throw new Error(`diagnostic assertion failed after step ${after}: expected ${JSON.stringify(assertion.expect)}, got ${observed.line}`);
    }
    manifest.diagnostics.push({ after, assertion: { ...assertion, id }, observed });
  }
  for (const frame of (scenario.frames ?? []).filter((value) => value.after === after)) {
    const destination = resolve(frame.destination ?? `build/frontier-v3-scenarios/${scenario.id}-${runId}-${frame.name}.png`);
    if (!process.env.DISPLAY) throw new Error(`frame ${frame.name} requires one active visible audit client and DISPLAY`);
    await executeFile('python3', [auditScript, 'capture', destination]);
    manifest.frames.push({ after, name: frame.name, path: destination });
  }
}

function matches(actual, expected) {
  return Object.entries(expected).every(([key, value]) => value && typeof value === 'object' && !Array.isArray(value)
    ? actual[key] && matches(actual[key], value)
    : actual[key] === value);
}
