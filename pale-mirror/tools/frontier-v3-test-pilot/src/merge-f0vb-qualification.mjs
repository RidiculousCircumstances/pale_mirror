import { readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { mergeQualification } from './f0vb-qualification.mjs';
async function main() {
  const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
  const input = resolve(values.input ?? ''); const output = resolve(values.output ?? '');
  const expected = {
    qualificationId: values.qualification, repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'],
    workflowRef: values.workflow, runId: Number(values.run), runAttempt: Number(values.attempt)
  };
  let merged;
  try {
    const evidence = await readLeaseEvidence(input, expected.runId);
    merged = mergeQualification(evidence, expected);
  } catch (failure) {
    merged = { schema: 2, kind: 'f0vb-native-qualification-merge', status: 'incomplete', ...expected, failure: String(failure?.message ?? failure) };
    await writeFile(output, JSON.stringify(merged) + '\n', { flag: 'wx' });
    throw failure;
  }
  await writeFile(output, JSON.stringify(merged) + '\n', { flag: 'wx' });
}

export async function readLeaseEvidence(root, runId) {
  const expectedBundles = new Set(Array.from({ length: 4 }, (_, index) => `f0vb-${runId}-worker-${index}`));
  const entries = await readdir(root, { withFileTypes: true });
  for (const entry of entries) {
    if (!entry.isDirectory()) throw new Error(`F0.VB merge rejects unexpected evidence entry ${entry.name}`);
    if (!expectedBundles.has(entry.name)) throw new Error(`F0.VB merge rejects foreign or unexpected evidence bundle ${entry.name}`);
  }
  if (entries.length !== expectedBundles.size) throw new Error('F0.VB merge rejects missing worker evidence bundle');
  return Promise.all(Array.from(expectedBundles).map(async bundle => {
    const bundleRoot = resolve(root, bundle);
    const files = await readdir(bundleRoot, { recursive: true });
    const leases = files.filter(name => name === 'lease.json' || name.endsWith('/lease.json'));
    if (leases.length !== 1 || leases[0] !== 'lease.json') {
      throw new Error(`F0.VB merge requires exactly one top-level lease.json in ${bundle}`);
    }
    return JSON.parse(await readFile(resolve(bundleRoot, 'lease.json'), 'utf8'));
  }));
}

await main();
