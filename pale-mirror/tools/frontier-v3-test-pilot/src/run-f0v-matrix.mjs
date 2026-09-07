import { randomUUID } from 'node:crypto';
import { spawn, execFile } from 'node:child_process';
import { mkdir, readFile, readdir, stat, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { basename, dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { targetColdDifferentialScenario, finalizeMatrixEntry, materializeF0vMatrix, requireArrivalCheckpoint, requireColdProgress, requireDistinctArrivalCheckpoints, requireTerminalEvidence } from './f0v-matrix.mjs';
import { loadScenario } from './scenario.mjs';
import { fingerprintPreparedBuild, fingerprintPreparedSource, requirePreparedF0vBuild } from './prepared-build.mjs';
import { writeFailureBundle } from './failure-bundle.mjs';

const [contractPath, ...options] = process.argv.slice(2);
if (!contractPath) throw new Error('usage: npm run f0v:matrix -- <vertical-contract.json> [--variant=<name>]');
const requestedVariant = options.find((option) => option.startsWith('--variant='))?.slice('--variant='.length);
const requestedLane = options.find((option) => option.startsWith('--lane='))?.slice('--lane='.length);
const preparedIdentityArgument = options.find((option) => option.startsWith('--prepared-identity='))?.slice('--prepared-identity='.length);
const outputRootArgument = options.find((option) => option.startsWith('--output-root='))?.slice('--output-root='.length);
if (options.some((option) => !option.startsWith('--variant=') && !option.startsWith('--lane=') && !option.startsWith('--prepared-identity=') && !option.startsWith('--output-root='))
    || ['--variant=', '--lane=', '--prepared-identity=', '--output-root='].some((prefix) => options.filter((option) => option.startsWith(prefix)).length > 1)) {
  throw new Error('F0.V matrix accepts at most one --variant, --lane, --prepared-identity and --output-root option');
}
if (!process.env.DISPLAY) throw new Error('an F0.V native matrix requires one visible DISPLAY=:0 pilot');

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const source = resolve(project, contractPath);
const contract = JSON.parse(await readFile(source, 'utf8'));
const matrix = materializeF0vMatrix(contract);
const selectedMatrix = requestedVariant === undefined ? matrix : matrix.filter((entry) => entry.variant === requestedVariant);
if (selectedMatrix.length === 0) throw new Error(`F0.V matrix has no declared variant ${requestedVariant}`);
if (requestedLane !== undefined && !/^[A-Za-z0-9_.:-]{1,127}$/.test(requestedLane)) throw new Error('F0.V matrix lane is invalid');
if (requestedLane !== undefined && requestedVariant === undefined) throw new Error('F0.V matrix --lane requires one declared --variant');
const runId = randomUUID();
const root = outputRootArgument === undefined
  ? resolve(project, `build/frontier-v3-scenarios/f0v-${basename(source, '.json')}-${runId}`)
  : containedBuildPath(outputRootArgument, 'F0.V matrix output root');
const identityPath = resolve(root, 'prepared-build.json');
const reportPath = resolve(root, 'matrix.json');
const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
await absent(root, 'F0.V matrix output root');
await mkdir(root, { recursive: true });
let build;
const report = { schema: 1, kind: 'frontier-v3-f0v-native-matrix', runId, contract: relative(project, source),
  selectedVariant: requestedVariant ?? null, selectedLane: requestedLane ?? null, build: null, variants: [] };
const arrivalCheckpoints = new Map();
const failedVariants = [];
try {
  build = preparedIdentityArgument === undefined
    ? await prepare(project, gradle)
    : await preparedIdentity(containedBuildPath(preparedIdentityArgument, 'F0.V prepared identity'));
  report.build = build;
  await writeFile(identityPath, `${JSON.stringify(build, null, 2)}\n`, 'utf8');
  for (const entry of selectedMatrix) {
    const runs = [];
    const failedLanes = [];
    const executions = requestedLane === undefined ? [...entry.executions] : entry.executions.filter((execution) => execution.lane === requestedLane);
    // A full local differential runs HOT/COLD first, then asks the server-side
    // mutation lane to hold COLD at the measured absolute terminal instant. Isolated single-lane work remains
    // explicitly pending aggregate evidence and never invents a counterpart.
    if (requestedLane === undefined && entry.driver === 'COLD_VS_HOT_COLD') {
      executions.sort((left, right) => left.lane === 'hot_cold' ? -1 : right.lane === 'hot_cold' ? 1 : 0);
    }
    if (executions.length === 0) {
      if (requestedVariant !== undefined) throw new Error(`F0.V variant ${requestedVariant} has no declared lane ${requestedLane}`);
      continue;
    }
    for (const execution of executions) {
      await requirePreparedF0vBuild(project, build);
      const previousHotCold = entry.driver === 'COLD_VS_HOT_COLD' && execution.lane === 'cold'
        ? runs.find((run) => run.lane === 'hot_cold' && run.status === 'passed') : undefined;
      const executionScenario = previousHotCold === undefined ? execution.scenario
        : targetColdDifferentialScenario(entry, execution.scenario, previousHotCold.result);
      const scenarioPath = resolve(root, `${entry.variant}-${execution.lane}.json`);
      const manifestPath = resolve(root, `${entry.variant}-${execution.lane}.manifest.json`);
      await writeFile(scenarioPath, `${JSON.stringify(executionScenario, null, 2)}\n`, 'utf8');
      const code = await child(process.execPath, [resolve(dirname(fileURLToPath(import.meta.url)), 'run-isolated-scenario.mjs'), scenarioPath, manifestPath], {
        cwd: project,
        env: { ...process.env, FRONTIER_V3_GRADLE: gradle, FRONTIER_V3_PREPARED_BUILD_IDENTITY: identityPath },
        stdio: 'inherit'
      });
      if (code !== 0) {
        const failure = `F0.V matrix native lane failed: ${entry.variant}/${execution.lane} (${code})`;
        runs.push({ lane: execution.lane, scenario: relative(project, scenarioPath), manifest: relative(project, manifestPath), status: 'failed', failure });
        failedLanes.push(failure);
        // Matrix lanes and variants use independent disposable worlds. A failed lane is
        // evidence, never permission to skip another required proof. In particular, all five
        // crash windows must leave their own diagnostic bundle; the same rule makes the
        // generated matrix a complete report rather than a first-failure sampler.
        continue;
      }
      await requirePreparedF0vBuild(project, build);
      const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
      requireTerminalEvidence(executionScenario, manifest);
      if (JSON.stringify(manifest.build) !== JSON.stringify(build)) throw new Error(`F0.V matrix build identity mismatch: ${entry.variant}/${execution.lane}`);
      const coldProgress = requireColdProgress(execution.scenario, manifest);
      runs.push({ lane: execution.lane, scenario: relative(project, scenarioPath), manifest: relative(project, manifestPath), status: 'passed', result: manifest,
        ...(coldProgress === undefined ? {} : { coldProgress }) });
    }
    if (failedLanes.length > 0) {
      report.variants.push({ id: entry.id, variant: entry.variant, driver: entry.driver,
        terminalInvariants: entry.declaration.terminalInvariants,
        runs: runs.map(({ result, ...metadata }) => metadata) });
      failedVariants.push(`${entry.variant}: ${failedLanes.join(', ')}`);
      await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
      continue;
    }
    const { differential } = finalizeMatrixEntry(entry, runs, { selectedLane: requestedLane !== undefined });
    report.variants.push({ id: entry.id, variant: entry.variant, driver: entry.driver,
      terminalInvariants: entry.declaration.terminalInvariants, runs: runs.map(({ result, ...metadata }) => metadata), differential });
    if (entry.variant === 'arrival_checkpoint_one' || entry.variant === 'arrival_checkpoint_two') {
      if (runs.length !== 1) throw new Error(`F0.V arrival variant has an unexpected execution count: ${entry.variant}`);
      arrivalCheckpoints.set(entry.variant, requireArrivalCheckpoint(entry, runs[0].result));
    }
    if (arrivalCheckpoints.size === 2) {
      const first = arrivalCheckpoints.get('arrival_checkpoint_one');
      const second = arrivalCheckpoints.get('arrival_checkpoint_two');
      report.arrivalCheckpoints = requireDistinctArrivalCheckpoints(first, second);
    }
    await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  }
  if (failedVariants.length > 0) {
    throw new Error(`F0.V matrix native failures after all independent lanes completed: ${failedVariants.join('; ')}`);
  }
  console.log(JSON.stringify({ status: 'ok', report: reportPath }));
} catch (failure) {
  report.error = String(failure?.stack ?? failure);
  await writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  const bundle = await writeFailureBundle({ project, output: relative(project, reportPath), scenarioPath: source, runId,
    timing: { runner: 'f0v-native-matrix', status: 'failed' }, failure, build,
    process: { matrix: true }, termination: { matrixStopped: true } });
  console.error(`PMV3_F0V_MATRIX failure_bundle=${bundle}`);
  throw failure;
}

async function prepare(projectDirectory, gradleExecutable) {
  const code = await child(gradleExecutable, [':pale-mirror-neoforge:classes', ':pale-mirror-neoforge:pilotClasses', ':pale-mirror-neoforge:jar',
    ':pale-mirror-neoforge:verifyPackagedJar', ':pale-mirror-neoforge:writeFrontierV3PilotServerLegacyClasspath',
    ':pale-mirror-neoforge:writeFrontierV3PilotClientLegacyClasspath', ':pale-mirror-neoforge:writeFrontierV3PilotPreparedLaunchManifest'],
  { cwd: projectDirectory, env: process.env, stdio: 'inherit' });
  if (code !== 0) throw new Error(`F0.V matrix prepared build failed (${code})`);
  const libs = resolve(projectDirectory, 'pale-mirror-neoforge/build/libs');
  const jars = (await readdir(libs)).filter((name) => /^[a-z0-9_-]+-.*\.jar$/i.test(name)
    && !name.includes('-sources') && !name.includes('-javadoc')).sort();
  if (jars.length !== 1) throw new Error(`F0.V matrix expected one packaged artifact, found ${jars.join(', ') || 'none'}`);
  const git = promisify(execFile);
  const [commit, status] = await Promise.all([
    git('git', ['rev-parse', 'HEAD'], { cwd: projectDirectory }).then((value) => value.stdout.trim()),
    git('git', ['status', '--porcelain'], { cwd: projectDirectory }).then((value) => value.stdout.trim())
  ]);
  return Object.freeze({ sourceCommit: commit, sourceDirty: status.length > 0, profile: 'disposable_lite',
    sourceContent: await fingerprintPreparedSource(projectDirectory),
    ...(await fingerprintPreparedBuild(projectDirectory, resolve(libs, jars[0]))) });
}

async function preparedIdentity(path) {
  const identity = Object.freeze(JSON.parse(await readFile(path, 'utf8')));
  await requirePreparedF0vBuild(project, identity);
  return identity;
}
function containedBuildPath(value, label) {
  if (typeof value !== 'string' || value.length === 0 || value.startsWith('/') || value.includes('\\') || value.split('/').includes('..')) {
    throw new Error(`${label} must be a safe relative path`);
  }
  const target = resolve(project, value); const buildRoot = resolve(project, 'build');
  if (!target.startsWith(buildRoot + '/')) throw new Error(`${label} must remain under build/`);
  return target;
}
async function absent(path, label) {
  try { await stat(path); throw new Error(`refusing to overwrite ${label}: ${path}`); }
  catch (error) { if (error?.code !== 'ENOENT') throw error; }
}

function child(command, args, options) {
  return new Promise((resolveExit, reject) => {
    const childProcess = spawn(command, args, options);
    childProcess.once('error', reject);
    childProcess.once('exit', (code, signal) => resolveExit(code ?? (signal == null ? 1 : 128)));
  });
}
