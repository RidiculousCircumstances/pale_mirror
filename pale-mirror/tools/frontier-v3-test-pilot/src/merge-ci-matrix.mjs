import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { CI_MATRIX_SCHEMA, mergeFourWorkerMatrix, requireCiMatrixMedianSpeedup, validateFourWorkerMatrixPlan } from './ci-matrix.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

export async function main(argumentsValue = process.argv.slice(2)) {
  const options = parse(argumentsValue);
  const planArtifact = JSON.parse(await readFile(underBuild(options.plan, 'plan'), 'utf8'));
  if (!planArtifact || planArtifact.schema !== 1 || planArtifact.kind !== 'frontier-v3-ci-matrix-plan-artifact') throw new Error('CI merge plan artifact is malformed');
  const plan = validateFourWorkerMatrixPlan(planArtifact.plan);
  const results = await Promise.all(options.results.map(async (path) => JSON.parse(await readFile(underBuild(path, 'worker result'), 'utf8'))));
  const mergedRuns = mergeMeasurements(plan, results);
  const timing = options.timing === undefined ? null : compareTiming(await readFile(underBuild(options.timing, 'timing report'), 'utf8'), plan,
    mergedRuns.map((entry) => entry.parallelExecutionMillis));
  const output = underBuild(options.output, 'merge report');
  await mkdir(dirname(output), { recursive: true });
  const merged = Object.freeze({ schema: 1, kind: 'frontier-v3-four-worker-matrix-merge-series', status: 'ok',
    planSha256: plan.planSha256, buildIdentitySha256: plan.buildIdentitySha256, contractSha256: plan.contractSha256,
    runs: Object.freeze(mergedRuns), timing });
  await writeFile(output, `${JSON.stringify({ ...merged, externalProvider: process.env.GITHUB_ACTIONS === 'true' ? 'CONFIRMED_IN_PROVIDER' : 'UNCONFIRMED_EXTERNAL' }, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  console.log(JSON.stringify({ status: 'ok', output, runs: merged.runs.length, timing }));
  return merged;
}

export function parse(argumentsValue) {
  const values = { plan: undefined, output: undefined, timing: undefined, results: [] };
  for (const argument of argumentsValue) {
    if (argument.startsWith('--plan=')) {
      if (values.plan !== undefined) throw new Error(`duplicate CI merge option: ${argument}`);
      values.plan = safeRelative(argument.slice('--plan='.length), 'plan');
    } else if (argument.startsWith('--output=')) {
      if (values.output !== undefined) throw new Error(`duplicate CI merge option: ${argument}`);
      values.output = safeRelative(argument.slice('--output='.length), 'output');
    } else if (argument.startsWith('--timing=')) {
      if (values.timing !== undefined) throw new Error(`duplicate CI merge option: ${argument}`);
      values.timing = safeRelative(argument.slice('--timing='.length), 'timing');
    } else if (argument.startsWith('--result=')) values.results.push(safeRelative(argument.slice('--result='.length), 'result'));
    else throw new Error(`unknown CI merge option: ${argument}`);
  }
  if (values.plan === undefined || values.output === undefined || values.results.length !== 12 || new Set(values.results).size !== 12) {
    throw new Error('usage: merge-ci-matrix.mjs --plan=<build/plan.json> --result=<build/worker.json> (twelve: three measurements) --output=<build/merge.json> [--timing=<build/timing.json>]');
  }
  return Object.freeze({ ...values, results: Object.freeze([...values.results].sort()) });
}
function underBuild(value, label) { const target = resolve(project, value); const build = resolve(project, 'build'); if (!target.startsWith(build + '/')) throw new Error(`CI merge ${label} must remain under build/`); return target; }
function safeRelative(value, label) { if (typeof value !== 'string' || value.length === 0 || value.startsWith('/') || value.includes('\\') || value.split('/').includes('..')) throw new Error(`CI merge ${label} must be a safe relative path`); return value; }
export function mergeMeasurements(plan, results) {
  const measurements = new Map();
  for (const result of results) {
    const id = result?.measurementId;
    if (!/^correctness-[1-3]$/.test(id ?? '')) throw new Error('CI worker result has an unknown measurement identity');
    const group = measurements.get(id) ?? [];
    group.push(result); measurements.set(id, group);
  }
  if (measurements.size !== 3 || [...measurements.keys()].sort().join(',') !== 'correctness-1,correctness-2,correctness-3') {
    throw new Error('CI merge needs exactly three named measurements');
  }
  return Object.freeze([...measurements.entries()].sort(([left], [right]) => left.localeCompare(right)).map(([measurementId, group]) =>
    Object.freeze({ measurementId, ...mergeFourWorkerMatrix(plan, group) })));
}

export function compareTiming(bytes, plan, parallelSamples) {
  const value = JSON.parse(bytes);
  if (!value || value.schema !== CI_MATRIX_SCHEMA || value.kind !== 'frontier-v3-ci-sequential-timing' || value.status !== 'ok'
      || value.planSha256 !== plan.planSha256 || value.buildIdentitySha256 !== plan.buildIdentitySha256 || value.contractSha256 !== plan.contractSha256
      || typeof value.host !== 'string' || value.host.length === 0
      || !Array.isArray(value.samples) || value.samples.length !== 3 || value.parallelMillis !== undefined) {
    throw new Error('CI sequential timing report is malformed, failed or foreign');
  }
  const sequentialSamples = value.samples.map((sample, index) => {
    if (!sample || sample.measurementId !== `correctness-${index + 1}` || !Number.isFinite(sample.sequentialMillis)
        || sample.sequentialMillis <= 0 || !Number.isInteger(sample.laneCount) || sample.laneCount !== plan.lanes.length) {
      throw new Error('CI sequential timing sample is malformed');
    }
    return sample.sequentialMillis;
  });
  return Object.freeze({ host: value.host, ...requireCiMatrixMedianSpeedup({ sequentialSamples, parallelSamples }) });
}
