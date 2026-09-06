import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CI_MATRIX_SCHEMA, evidenceFromManifest, validateFourWorkerMatrixPlan } from './ci-matrix.mjs';
import { compileAssignedPersistentMatrix, compilePersistentWorkerPlan } from './persistent-worker-plan.mjs';
import { fingerprintPreparedBuild, fingerprintPreparedSource, portablePreparedBuildIdentity, requirePreparedF0vBuild } from './prepared-build.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

export async function main(argumentsValue = process.argv.slice(2)) {
  const options = parse(argumentsValue);
  if (!process.env.DISPLAY) throw new Error('CI native shard requires one validated private DISPLAY');
  requireWorkerIdentity(process.env.FRONTIER_V3_PILOT_WORKER_ID, options.worker);
  const planPath = underBuild(options.plan, 'plan'); const preparedPath = underBuild(options.prepared, 'prepared identity'); const outputPath = underBuild(options.output, 'result');
  const artifact = JSON.parse(await readFile(planPath, 'utf8'));
  if (!artifact || artifact.schema !== 1 || artifact.kind !== 'frontier-v3-ci-matrix-plan-artifact' || typeof artifact.contract !== 'string'
      || !artifact.portablePreparedIdentity) {
    throw new Error('CI matrix plan artifact is malformed');
  }
  const plan = validateFourWorkerMatrixPlan(artifact.plan);
  const shard = plan.shards.find((entry) => entry.workerId === options.worker);
  if (shard === undefined) throw new Error('CI matrix worker is not assigned by the immutable plan');
  const rawPrepared = await readFile(preparedPath, 'utf8');
  const sourcePrepared = Object.freeze(JSON.parse(rawPrepared));
  if (hash(JSON.stringify(portablePreparedBuildIdentity(sourcePrepared))) !== plan.buildIdentitySha256
      || JSON.stringify(portablePreparedBuildIdentity(sourcePrepared)) !== JSON.stringify(artifact.portablePreparedIdentity)) {
    throw new Error('CI matrix portable prepared identity hash drifted');
  }
  const persistentWorkerPlan = compileWorkerPersistentPlan(plan, options.worker, options.measurement);
  const root = resolve(project, 'build/frontier-v3-ci-shards', `${options.worker}-${randomUUID()}`);
  await mkdir(root, { recursive: true });
  await writeFile(resolve(root, 'persistent-worker-plan.json'), `${JSON.stringify(persistentWorkerPlan, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  const assignedPersistentPlan = compileAssignedPersistentMatrix(persistentWorkerPlan, plan, { workerId: options.worker, measurementId: options.measurement });
  const assignedPlanPath = resolve(root, 'assigned-persistent-matrix.json');
  await writeFile(assignedPlanPath, `${JSON.stringify(assignedPersistentPlan, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  const prepared = Object.freeze({ sourceContent: await fingerprintPreparedSource(project),
    ...(await fingerprintPreparedBuild(project, safeProjectPath(sourcePrepared.preparedArtifact.path, 'artifact'))) });
  if (hash(JSON.stringify(portablePreparedBuildIdentity(prepared))) !== plan.buildIdentitySha256) {
    throw new Error('CI matrix worker artifact/classpath content drifted');
  }
  const localPreparedPath = resolve(root, 'prepared-build.json');
  await writeFile(localPreparedPath, `${JSON.stringify(prepared, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  await requirePreparedF0vBuild(project, prepared);
  const started = process.hrtime.bigint(); const startedEpochMillis = Date.now(); const lanes = [];
  try {
  const runnerOutput = resolve(root, 'persistent-matrix.json');
  const code = await child(process.execPath, [resolve(project, 'tools/frontier-v3-test-pilot/src/run-f0va-persistent-matrix.mjs'),
    `--assigned-plan=${relative(project, assignedPlanPath)}`, `--assigned-source=${relative(project, planPath)}`,
    `--assigned-measurement=${options.measurement}`, relative(project, runnerOutput)],
  { FRONTIER_V3_PREPARED_BUILD_IDENTITY: localPreparedPath });
  if (code !== 0) throw new Error(`CI assigned persistent matrix failed for ${options.worker} (${code})`);
  const report = JSON.parse(await readFile(runnerOutput, 'utf8'));
  if (report?.status !== 'ok' || report?.session?.plan?.kind !== 'frontier-v3-assigned-persistent-matrix'
      || report?.session?.plan?.compiledContentSha256 !== assignedPersistentPlan.contentSha256 || report?.client?.status !== 'ok'
      || report.client.clientPid === null || JSON.stringify(report.build) !== JSON.stringify(prepared)) {
    throw new Error('CI assigned persistent matrix report is malformed or foreign');
  }
  for (const lane of shard.lanes) {
    const segments = report.session.plan.segments.filter((segment) => segment.laneId === lane.id);
    const reports = report.client.segments.filter((segment) => segments.some((planned) => planned.id === segment.id));
    if (segments.length === 0 || reports.length !== segments.length || reports.some((segment) => segment.expectedLoss !== true && !segment.terminal)) {
      throw new Error(`CI assigned persistent lane evidence is incomplete: ${lane.id}`);
    }
    const diagnostics = reports.flatMap((reportSegment) => {
      const planned = segments.find((segment) => segment.id === reportSegment.id);
      return reportSegment.diagnostics.map((item) => ({ observed: { value: {
        ...item.value, pilotActionStep: item.actionStep + planned.originalActionOffset
      } } }));
    });
    lanes.push(Object.freeze({ id: lane.id, ...evidenceFromManifest(lane, { status: 'ok', diagnostics }), report: relative(project, runnerOutput) }));
  }
  await requirePreparedF0vBuild(project, prepared);
  const result = Object.freeze({ schema: CI_MATRIX_SCHEMA, kind: 'frontier-v3-ci-shard-result', status: 'ok', planSha256: plan.planSha256,
    buildIdentitySha256: plan.buildIdentitySha256, contractSha256: plan.contractSha256, workerId: options.worker,
    measurementId: options.measurement,
    executionMillis: Number(process.hrtime.bigint() - started) / 1_000_000, startedEpochMillis, finishedEpochMillis: Date.now(), lanes });
  await writeResult(outputPath, result);
    console.log(JSON.stringify({ status: 'ok', output: outputPath, worker: options.worker, lanes: lanes.length }));
    return result;
  } catch (failure) {
  await writeResult(outputPath, { schema: CI_MATRIX_SCHEMA, kind: 'frontier-v3-ci-shard-result', status: 'failed', planSha256: plan.planSha256,
    buildIdentitySha256: plan.buildIdentitySha256, contractSha256: plan.contractSha256, workerId: options.worker,
    measurementId: options.measurement,
    executionMillis: Number(process.hrtime.bigint() - started) / 1_000_000, startedEpochMillis, finishedEpochMillis: Date.now(), lanes, failure: String(failure?.stack ?? failure) });
    throw failure;
  }
}

/** Pure CLI preflight boundary; retained output is consumed by the later lifecycle integration. */
export function compileWorkerPersistentPlan(plan, worker, measurement) {
  return compilePersistentWorkerPlan(plan, worker, measurement);
}

/** The plan owner and every lifecycle barrier must use the same exact worker identity. */
export function requireWorkerIdentity(actual, expected) {
  if (!/^worker-[0-3]$/.test(actual ?? '') || actual !== expected) {
    throw new Error('CI native shard worker identity does not match its immutable plan assignment');
  }
}

export function parse(argumentsValue) {
  const values = { plan: undefined, prepared: undefined, worker: undefined, measurement: undefined, output: undefined };
  for (const argument of argumentsValue) {
    const pair = [['--plan=', 'plan'], ['--prepared=', 'prepared'], ['--worker=', 'worker'], ['--measurement=', 'measurement'], ['--output=', 'output']]
      .find(([prefix]) => argument.startsWith(prefix));
    if (pair === undefined || values[pair[1]] !== undefined) throw new Error(`unknown or duplicate CI shard option: ${argument}`);
    values[pair[1]] = pair[1] === 'worker' ? argument.slice(pair[0].length) : safeRelative(argument.slice(pair[0].length), pair[1]);
  }
  if (Object.values(values).some((value) => value === undefined) || !/^worker-[0-3]$/.test(values.worker)
      || !/^correctness-[1-3]$/.test(values.measurement)) {
    throw new Error('usage: run-ci-matrix-shard.mjs --plan=<build/plan.json> --prepared=<build/identity.json> --worker=worker-0 --measurement=correctness-1 --output=<build/result.json>');
  }
  return Object.freeze(values);
}
function underBuild(value, label) { const target = resolve(project, value); const build = resolve(project, 'build'); if (!target.startsWith(build + '/')) throw new Error(`CI shard ${label} must remain under build/`); return target; }
function safeProjectPath(value, label) { if (typeof value !== 'string' || value.length === 0) throw new Error(`CI shard ${label} is malformed`); const target = resolve(project, value); const path = relative(project, target); if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`CI shard ${label} escapes project`); return target; }
function safeRelative(value, label) { if (typeof value !== 'string' || value.length === 0 || value.startsWith('/') || value.includes('\\') || value.split('/').includes('..')) throw new Error(`CI shard ${label} must be a safe relative path`); return value; }
async function writeResult(path, value) { await mkdir(dirname(path), { recursive: true }); await writeFile(path, `${JSON.stringify(value, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' }); }
function child(command, args, environment = {}) { return new Promise((resolveExit, rejectExit) => { const childProcess = spawn(command, args, { cwd: project, env: { ...process.env, ...environment }, stdio: 'inherit', shell: false }); childProcess.once('error', rejectExit); childProcess.once('exit', (code, signal) => resolveExit(code ?? (signal == null ? 1 : 128))); }); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
