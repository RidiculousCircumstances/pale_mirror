import { spawn } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { hostname } from 'node:os';
import { CI_MATRIX_SCHEMA, mergeSequentialFourWorkerMatrix, validateFourWorkerMatrixPlan } from './ci-matrix.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

export async function main(argumentsValue = process.argv.slice(2)) {
  const options = parse(argumentsValue);
  if (!process.env.DISPLAY) throw new Error('sequential CI matrix requires one validated private DISPLAY');
  const artifact = JSON.parse(await readFile(underBuild(options.plan, 'plan'), 'utf8'));
  if (!artifact || artifact.schema !== 1 || artifact.kind !== 'frontier-v3-ci-matrix-plan-artifact') throw new Error('sequential CI matrix plan is malformed');
  const plan = validateFourWorkerMatrixPlan(artifact.plan);
  const samples = [];
  for (let sample = 1; sample <= options.samples; sample++) {
    const started = process.hrtime.bigint(); const outputs = [];
    for (const shard of plan.shards) {
      const output = `${options.resultsRoot}/serial-${sample}/${shard.workerId}.json`;
      const code = await child(process.execPath, [resolve(project, 'tools/frontier-v3-test-pilot/src/run-ci-matrix-shard.mjs'),
        `--plan=${options.plan}`, `--prepared=${options.prepared}`, `--worker=${shard.workerId}`,
        `--measurement=correctness-${sample}`, `--output=${output}`], workerEnvironment(process.env, shard.workerId));
      if (code !== 0) throw new Error(`sequential CI matrix shard failed: ${shard.workerId} (${code})`);
      outputs.push(output);
    }
    const results = await Promise.all(outputs.map(async (path) => JSON.parse(await readFile(underBuild(path, 'sequential result'), 'utf8'))));
    const merged = mergeSequentialFourWorkerMatrix(plan, results);
    samples.push(Object.freeze({ measurementId: `correctness-${sample}`,
      sequentialMillis: Number(process.hrtime.bigint() - started) / 1_000_000, laneCount: merged.lanes.length }));
  }
  const output = underBuild(options.output, 'sequential timing');
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify({ schema: CI_MATRIX_SCHEMA, kind: 'frontier-v3-ci-sequential-timing', status: 'ok', host: hostname(),
    planSha256: plan.planSha256, buildIdentitySha256: plan.buildIdentitySha256, contractSha256: plan.contractSha256,
    samples }, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  console.log(JSON.stringify({ status: 'ok', output, samples: samples.length }));
}

export function parse(argumentsValue) {
  const values = { plan: undefined, prepared: undefined, resultsRoot: undefined, samples: undefined, output: undefined };
  for (const argument of argumentsValue) {
    const pair = [['--plan=', 'plan'], ['--prepared=', 'prepared'], ['--results-root=', 'resultsRoot'], ['--samples=', 'samples'], ['--output=', 'output']]
      .find(([prefix]) => argument.startsWith(prefix));
    if (pair === undefined || values[pair[1]] !== undefined) throw new Error(`unknown or duplicate sequential CI matrix option: ${argument}`);
    values[pair[1]] = pair[1] === 'samples' ? Number(argument.slice(pair[0].length)) : safeRelative(argument.slice(pair[0].length), pair[1]);
  }
  if (Object.values(values).some((value) => value === undefined) || values.samples !== 3) throw new Error('usage: run-ci-matrix-sequential.mjs --plan=<build/plan.json> --prepared=<build/id.json> --results-root=<build/results> --samples=3 --output=<build/timing.json>');
  return Object.freeze(values);
}
function underBuild(value, label) { const target = resolve(project, value); const build = resolve(project, 'build'); if (!target.startsWith(build + '/')) throw new Error(`sequential CI matrix ${label} must remain under build/`); return target; }
function safeRelative(value, label) { if (typeof value !== 'string' || value.length === 0 || value.startsWith('/') || value.includes('\\') || value.split('/').includes('..')) throw new Error(`sequential CI matrix ${label} must be a safe relative path`); return value; }
/** A serial timing run must still use the shard's immutable worker identity. */
export function workerEnvironment(base, workerId) {
  if (!/^worker-[0-3]$/.test(workerId)) throw new Error('sequential CI matrix worker identity is malformed');
  return Object.freeze({ ...base, FRONTIER_V3_PILOT_WORKER_ID: workerId });
}
function child(command, args, env) { return new Promise((resolveExit, rejectExit) => { const childProcess = spawn(command, args, { cwd: project, env, stdio: 'inherit', shell: false }); childProcess.once('error', rejectExit); childProcess.once('exit', (code, signal) => resolveExit(code ?? (signal == null ? 1 : 128))); }); }
