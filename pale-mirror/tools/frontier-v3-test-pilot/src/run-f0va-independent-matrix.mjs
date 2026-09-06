import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { basename, dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { independentMatrixPlan, validateIndependentMatrixEvidence } from './independent-matrix.mjs';
import { writeFailureBundle } from './failure-bundle.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';
import { loadScenario } from './scenario.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

export async function main(argumentsValue = process.argv.slice(2)) {
  const output = safeBuildPath(argumentsValue[0] ?? `build/frontier-v3-scenarios/f0va-independent-matrix-${Date.now()}.json`, 'independent matrix output');
  if (argumentsValue.length > 1) throw new Error('usage: node run-f0va-independent-matrix.mjs [build/output.json]');
  if (!process.env.DISPLAY) throw new Error('F0.VA independent matrix requires one visible client on DISPLAY=:0');
  const preparedPath = process.env.FRONTIER_V3_PREPARED_BUILD_IDENTITY;
  if (preparedPath === undefined || preparedPath === '') throw new Error('F0.VA independent matrix requires one prepared build identity');
  const identityPath = safeProjectPath(preparedPath, 'prepared build identity');
  await absent(output, 'independent matrix manifest');
  const build = Object.freeze(JSON.parse(await readFile(identityPath, 'utf8'))); await requirePreparedF0vBuild(project, build);
  const port = Number(process.env.FRONTIER_V3_PILOT_PORT ?? 25575);
  if (!Number.isInteger(port) || port < 1024 || port >= 65535) throw new Error('F0.VA independent matrix port is malformed');
  const scenarios = resolve(project, 'tools/frontier-v3-test-pilot/scenarios');
  const smokePath = join(scenarios, 'disposable-lite-smoke.json');
  const gracefulPath = join(scenarios, 'disposable-lite-smoke-graceful-restart.json');
  const smoke = await loadScenario(smokePath);
  const graceful = await loadScenario(gracefulPath);
  const plan = independentMatrixPlan({ build, smokeScenario: smoke.scenario, gracefulScenario: graceful.scenario });
  const runId = randomUUID(); const outputs = []; let failure = null;
  const started = process.hrtime.bigint();
  try {
    for (const segment of plan.segments) {
      const scenarioPath = segment.restart ? gracefulPath : smokePath;
      const manifest = resolve(dirname(output), `${basename(output, '.json')}-${segment.id}.manifest.json`);
      await absent(manifest, `independent ${segment.id} manifest`);
      const code = await child(process.execPath, isolatedScenarioInvocation(
        resolve(project, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs'), scenarioPath, manifest), {
        ...process.env, FRONTIER_V3_PREPARED_BUILD_IDENTITY: relative(project, identityPath), FRONTIER_V3_PILOT_PORT: String(port),
        FRONTIER_V3_PILOT_USE_PERSISTENT_CLIENT: 'false', FRONTIER_V3_PILOT_WORKER_ID: `${process.env.FRONTIER_V3_PILOT_WORKER_ID ?? 'local-f0va'}-independent`
      });
      if (code !== 0) throw new Error(`F0.VA independent ${segment.id} failed (${code}); its child retained a failure bundle`);
      outputs.push(manifest);
    }
    await requirePreparedF0vBuild(project, build);
    const manifests = await Promise.all(outputs.map(async (path) => JSON.parse(await readFile(path, 'utf8'))));
    const evidence = validateIndependentMatrixEvidence(plan, manifests);
    await mkdir(dirname(output), { recursive: true });
    await writeFile(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-f0va-independent-native-matrix', status: 'ok', runId,
      build, plan, manifests: outputs.map((path) => relative(project, path)), evidence,
      timing: { totalMillis: Number(process.hrtime.bigint() - started) / 1_000_000 }, freshWorlds: true, fixtureImage: null
    }, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
    console.log(JSON.stringify({ status: 'ok', manifest: output, runId }));
  } catch (error) {
    failure = error;
    const bundle = await writeFailureBundle({ project, output, scenarioPath: join(scenarios, 'disposable-lite-smoke-graceful-restart.json'), runId,
      timing: { runner: 'f0va-independent-matrix', status: 'failed', totalMillis: Number(process.hrtime.bigint() - started) / 1_000_000 },
      failure, build, process: { port, childManifests: outputs.map((path) => relative(project, path)) } });
    console.error(`PMV3_F0VA_INDEPENDENT failure_bundle=${bundle}`);
    throw error;
  }
}

/** The old independent baseline must pass the real immutable scenario path, never loader metadata. */
export function isolatedScenarioInvocation(runner, scenarioPath, manifestPath) {
  if ([runner, scenarioPath, manifestPath].some((value) => typeof value !== 'string' || value.length === 0)) {
    throw new Error('F0.VA independent isolated scenario invocation is malformed');
  }
  return Object.freeze([runner, scenarioPath, manifestPath]);
}

function child(command, args, environment) {
  return new Promise((resolveExit, rejectExit) => {
    const childProcess = spawn(command, args, { cwd: project, env: environment, stdio: 'inherit', shell: false });
    childProcess.once('error', rejectExit); childProcess.once('exit', (code, signal) => resolveExit(code ?? (signal == null ? 1 : 128)));
  });
}
function safeProjectPath(candidate, label) { if (typeof candidate !== 'string') throw new Error(`${label} is malformed`); const target = resolve(project, candidate); const path = relative(project, target); if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} escapes project`); return target; }
function safeBuildPath(candidate, label) { const target = safeProjectPath(candidate, label); const path = relative(resolve(project, 'build'), target); if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} must remain under build/`); return target; }
async function absent(path, label) { try { await stat(path); throw new Error(`refusing to overwrite ${label}: ${path}`); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
