import { readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

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
  for (const [worker, variants] of expected) {
    const bundle = `f01-${values.run}-${worker}`;
    const entries = await readdir(resolve(root, bundle), { withFileTypes: true });
    if (entries.filter(entry => entry.isFile() && entry.name === 'matrix.json').length !== 1) {
      throw new Error(`F0.1 merge requires exactly one top-level matrix report for ${worker}`);
    }
    const report = JSON.parse(await readFile(resolve(root, bundle, 'matrix.json'), 'utf8'));
    if (report.error || !report.build || report.build.sourceCommit !== values.head) throw new Error(`F0.1 matrix report is stale or failed for ${worker}`);
    const actual = report.variants?.map(entry => entry.variant).sort();
    if (JSON.stringify(actual) !== JSON.stringify([...variants].sort()) || report.variants.some(entry => entry.runs.some(run => run.status !== 'passed'))) {
      throw new Error(`F0.1 matrix report is incomplete for ${worker}`);
    }
    aggregate.workers.push({ worker, variants: actual, build: report.build });
  }
  aggregate.status = 'ok';
} catch (failure) {
  aggregate.failure = String(failure?.message ?? failure);
  await writeFile(output, JSON.stringify(aggregate) + '\n', { flag: 'wx' });
  throw failure;
}
await writeFile(output, JSON.stringify(aggregate) + '\n', { flag: 'wx' });
