import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createFourWorkerMatrixPlan } from './ci-matrix.mjs';
import { portablePreparedBuildIdentity, requirePreparedF0vBuild } from './prepared-build.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

export async function main(argumentsValue = process.argv.slice(2)) {
  const options = parse(argumentsValue);
  const preparedPath = underBuild(options.prepared, 'prepared identity');
  const outputPath = underBuild(options.output, 'CI matrix plan');
  const [prepared, contractBytes] = await Promise.all([readFile(preparedPath, 'utf8'), readFile(resolve(project, options.contract))]);
  const build = Object.freeze(JSON.parse(prepared));
  await requirePreparedF0vBuild(project, build);
  const portablePreparedIdentity = portablePreparedBuildIdentity(build);
  const plan = createFourWorkerMatrixPlan(JSON.parse(contractBytes), { buildIdentitySha256: hash(JSON.stringify(portablePreparedIdentity)), contractSha256: hash(contractBytes) });
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-ci-matrix-plan-artifact', preparedIdentity: options.prepared,
    portablePreparedIdentity, contract: options.contract, plan }, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  console.log(JSON.stringify({ status: 'ok', output: outputPath, planSha256: plan.planSha256, lanes: plan.lanes.length }));
  return plan;
}

export function parse(argumentsValue) {
  const values = { prepared: undefined, contract: 'tools/frontier-v3-test-pilot/contracts/resource-site-harvest-f0v.json', output: undefined };
  const seen = new Set();
  for (const argument of argumentsValue) {
    const [prefix, key] = [['--prepared=', 'prepared'], ['--contract=', 'contract'], ['--output=', 'output']].find(([value]) => argument.startsWith(value)) ?? [];
    if (key === undefined || seen.has(key)) {
      throw new Error(`unknown or duplicate CI matrix option: ${argument}`);
    }
    seen.add(key);
    values[key] = safeRelative(argument.slice(prefix.length), key);
  }
  if (values.prepared === undefined || values.output === undefined) throw new Error('usage: write-ci-matrix-plan.mjs --prepared=<build/identity.json> --output=<build/plan.json> [--contract=<relative.json>]');
  return Object.freeze(values);
}
function underBuild(value, label) {
  const target = resolve(project, value); const build = resolve(project, 'build');
  if (!target.startsWith(build + '/')) throw new Error(`CI matrix ${label} must remain under build/`);
  return target;
}
function safeRelative(value, label) {
  if (typeof value !== 'string' || value.length === 0 || value.startsWith('/') || value.includes('\\') || value.split('/').includes('..')) {
    throw new Error(`CI matrix ${label} must be a safe relative path`);
  }
  return value;
}
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
