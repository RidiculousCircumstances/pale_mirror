import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { hostname } from 'node:os';
import { basename, dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadScenario } from './scenario.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';
import { compareNativeTimingMedians } from './timing.mjs';
import { writeFailureBundle } from './failure-bundle.mjs';

const [scenarioArgument, identityArgument,
  reportArgument = `build/frontier-v3-scenarios/f0v-timing-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioArgument || !identityArgument) {
  throw new Error('usage: npm run f0v:timing -- <graceful-scenario.json> <prepared-build.json> [report.json]');
}
if (!process.env.DISPLAY) throw new Error('F0.V native timing requires the one visible DISPLAY=:0 pilot');

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const scenarioPath = resolve(project, scenarioArgument);
const identityPath = resolve(project, identityArgument);
const reportPath = resolve(project, reportArgument);
const reportRoot = resolve(project, 'build');
if (!reportPath.startsWith(reportRoot + '/')) throw new Error('F0.V timing report must remain under build/');
const { scenario, sha256: scenarioSha256 } = await loadScenario(scenarioPath);
if (scenario.restart?.mode !== 'graceful') throw new Error('F0.V timing requires one checked-in graceful-restart reference scenario');
const identity = Object.freeze(JSON.parse(await readFile(identityPath, 'utf8')));
await requirePreparedF0vBuild(project, identity);

const runId = randomUUID();
const environment = Object.freeze({
  host: hostname(),
  seed: scenario.isolation?.seed,
  profile: scenario.server.profile ?? 'world',
  viewDistance: scenario.server.viewDistance ?? 10,
  sourceCommit: identity.sourceCommit,
  preparedArtifactSha256: identity.preparedArtifact?.sha256,
  serverClasspathSha256: identity.classpaths?.server?.sha256,
  clientClasspathSha256: identity.classpaths?.client?.sha256
});
const report = { schema: 1, kind: 'frontier-v3-f0v-native-timing', runId,
  scenario: { path: relative(project, scenarioPath), sha256: scenarioSha256, restart: scenario.restart },
  identity: relative(project, identityPath), environment, baseline: [], candidate: [] };
await mkdir(dirname(reportPath), { recursive: true });

try {
  for (let sample = 1; sample <= 3; sample++) report.baseline.push(await runSample('baseline', sample, false));
  for (let sample = 1; sample <= 3; sample++) report.candidate.push(await runSample('candidate', sample, true));
  report.comparison = compareNativeTimingMedians({ baseline: report.baseline, candidate: report.candidate });
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  console.log(JSON.stringify({ status: 'ok', report: reportPath, comparison: report.comparison }));
} catch (failure) {
  report.error = String(failure?.stack ?? failure);
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  // Every failed child native run already creates its own world-bearing bundle.  This parent
  // bundle covers preparation/hash/measurement failures that happen before a child can own one.
  const bundle = await writeFailureBundle({ project, output: relative(project, reportPath), scenarioPath, runId,
    timing: { runner: 'f0v-native-timing', status: 'failed' }, failure, build: identity,
    process: { timingMatrix: true, completedBaseline: report.baseline.length, completedCandidate: report.candidate.length },
    termination: { timingMatrixStopped: true } });
  console.error(`PMV3_F0V_TIMING failure_bundle=${bundle}`);
  throw failure;
}

async function runSample(cohort, sample, persistentClient) {
  await requirePreparedF0vBuild(project, identity);
  const output = resolve(dirname(reportPath), `${basename(reportPath, '.json')}-${cohort}-${sample}.manifest.json`);
  const runner = resolve(dirname(fileURLToPath(import.meta.url)), 'run-isolated-scenario.mjs');
  const code = await child(process.execPath, [runner, scenarioPath, output], {
    cwd: project,
    env: { ...process.env, FRONTIER_V3_PREPARED_BUILD_IDENTITY: identityPath,
      FRONTIER_V3_PILOT_USE_PERSISTENT_CLIENT: persistentClient ? 'true' : 'false' },
    stdio: 'inherit'
  });
  if (code !== 0) throw new Error(`F0.V ${cohort} native sample ${sample} failed (${code}); its isolated runner retained a diagnostic bundle`);
  await requirePreparedF0vBuild(project, identity);
  const manifest = JSON.parse(await readFile(output, 'utf8'));
  if (manifest.status !== 'ok' || JSON.stringify(manifest.build) !== JSON.stringify(identity)) {
    throw new Error(`F0.V ${cohort} native sample ${sample} lacks its exact successful prepared-build manifest`);
  }
  if (persistentClient) {
    if (manifest.recovery?.clientSession?.reusedJvm !== true || manifest.clientSegments?.length !== 1
        || manifest.clientSegments[0]?.reusedJvm !== true) {
      throw new Error(`F0.V candidate sample ${sample} did not retain one persistent client across both server JVMs`);
    }
  } else if (manifest.recovery?.clientSession !== undefined || manifest.clientSegments?.length !== 2) {
    throw new Error(`F0.V baseline sample ${sample} did not retain two independent client segments`);
  }
  const totalMillis = manifest.timing?.totalMillis;
  if (!Number.isFinite(totalMillis) || totalMillis <= 0) throw new Error(`F0.V ${cohort} native sample ${sample} lacks complete timing evidence`);
  return Object.freeze({ cohort, sample, status: 'ok', totalMillis, environment, manifest: relative(project, output) });
}

function child(command, args, options) {
  return new Promise((resolveExit, reject) => {
    const childProcess = spawn(command, args, options);
    childProcess.once('error', reject);
    childProcess.once('exit', (code, signal) => resolveExit(code ?? (signal == null ? 1 : 128)));
  });
}
