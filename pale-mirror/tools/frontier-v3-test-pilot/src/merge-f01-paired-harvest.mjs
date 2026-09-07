import { readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { portablePreparedBuildIdentity } from './prepared-build.mjs';
import { requireDistinctArrivalCheckpoints } from './f0v-matrix.mjs';

const values = Object.fromEntries(process.argv.slice(2).map(value => {
  const [key, entry] = value.slice(2).split('=', 2); return [key, entry];
}));
const root = resolve(values.input ?? '');
const output = resolve(values.output ?? '');
const expected = new Map([
  ['worker-0', ['never_loaded', 'arrival_checkpoint_one']],
  ['worker-1', ['arrival_checkpoint_two', 'unload_return']],
  ['worker-2', ['player_intervention', 'graceful_restart']],
  ['worker-3', ['abrupt_restart', 'neutral_observer_differential']]
]);
const aggregate = { schema: 1, kind: 'frontier-v3-f01-paired-harvest-native-matrix', status: 'incomplete', headSha: values.head, runId: Number(values.run), workers: [] };
try {
  const bundles = await readdir(root, { withFileTypes: true });
  const expectedBundles = new Set([...expected.keys()].map(worker => `f01-${values.run}-${worker}`));
  if (bundles.length !== expectedBundles.size
      || bundles.some(entry => !entry.isDirectory() || !expectedBundles.has(entry.name))) {
    throw new Error('F0.1 merge requires exactly the four expected worker evidence bundles');
  }
  let identity;
  const reports = new Map();
  for (const [worker, variants] of expected) {
    const bundle = `f01-${values.run}-${worker}`;
    const entries = await readdir(resolve(root, bundle), { withFileTypes: true });
    if (entries.filter(entry => entry.isFile() && entry.name === 'matrix.json').length !== 1) {
      throw new Error(`F0.1 merge requires exactly one top-level matrix report for ${worker}`);
    }
    const report = JSON.parse(await readFile(resolve(root, bundle, 'matrix.json'), 'utf8'));
    if (report.kind !== 'frontier-v3-f01-paired-harvest-native-matrix' || report.error) throw new Error(`F0.1 matrix report is stale or failed for ${worker}`);
    const currentIdentity = requireIdentity(report, values.head, worker);
    if (identity === undefined) identity = currentIdentity;
    else if (JSON.stringify(identity) !== JSON.stringify(currentIdentity)) throw new Error(`F0.1 matrix report identity drifted for ${worker}`);
    const actual = report.variants?.map(entry => entry.variant).sort();
    if (JSON.stringify(actual) !== JSON.stringify([...variants].sort()) || report.variants.some(entry => entry.runs.some(run => run.status !== 'passed'))) {
      throw new Error(`F0.1 matrix report is incomplete for ${worker}`);
    }
    reports.set(worker, report);
    aggregate.workers.push({ worker, variants: actual, identity: currentIdentity });
  }
  const firstArrival = variant(reports.get('worker-0'), 'arrival_checkpoint_one').arrivalCheckpoint;
  const secondArrival = variant(reports.get('worker-1'), 'arrival_checkpoint_two').arrivalCheckpoint;
  aggregate.arrivalCheckpoints = requireDistinctArrivalCheckpoints(requireArrival(firstArrival), requireArrival(secondArrival));
  aggregate.identity = identity;
  aggregate.status = 'ok';
} catch (failure) {
  aggregate.failure = String(failure?.message ?? failure);
  await writeFile(output, JSON.stringify(aggregate) + '\n', { flag: 'wx' });
  throw failure;
}
await writeFile(output, JSON.stringify(aggregate) + '\n', { flag: 'wx' });

function requireIdentity(report, head, worker) {
  const build = report?.build;
  if (!build || build.sourceCommit !== head || build.sourceDirty !== false || !completeSourceContent(build.sourceContent) || !report.contract
      || report.contract.path !== 'tools/frontier-v3-test-pilot/contracts/resource-site-harvest-f0v.json'
      || !sha(report.contract.sha256)) throw new Error(`F0.1 matrix report is stale or failed for ${worker}`);
  let portable;
  try { portable = portablePreparedBuildIdentity(build); }
  catch { throw new Error(`F0.1 matrix report has incomplete build/JAR identity for ${worker}`); }
  return Object.freeze({ sourceCommit: build.sourceCommit, sourceContent: build.sourceContent,
    contract: Object.freeze({ path: report.contract.path, sha256: report.contract.sha256 }), build: portable });
}

function variant(report, name) {
  const value = report?.variants?.find(entry => entry?.variant === name);
  if (!value) throw new Error(`F0.1 matrix report lacks ${name}`);
  return value;
}

function requireArrival(value) {
  if (!value || typeof value.stage !== 'string' || typeof value.view !== 'string' || typeof value.id !== 'string' || typeof value.path !== 'string'
      || !Number.isSafeInteger(value.beforeAction) || !Number.isSafeInteger(value.before) || !Number.isSafeInteger(value.after)
      || !Number.isSafeInteger(value.minimumAdvance) || value.after < value.before + value.minimumAdvance) {
    throw new Error('F0.1 matrix report lacks an observed HOT arrival checkpoint');
  }
  return value;
}

function sha(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }

function completeSourceContent(value) {
  return value?.schema === 1 && value.kind === 'frontier-v3-working-content' && sha(value.sha256)
    && Array.isArray(value.files) && value.files.length > 0 && value.files.every(entry => typeof entry?.path === 'string'
      && ['PRESENT', 'MISSING'].includes(entry.state) && sha(entry.sha256));
}
