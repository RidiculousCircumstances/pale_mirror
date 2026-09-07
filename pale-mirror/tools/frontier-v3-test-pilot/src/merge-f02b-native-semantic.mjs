import { readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { mergeSemanticMatrix } from './f02b-native-semantic.mjs';
const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const expected = { qualificationId: values.qualification, repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'], workflowRef: values.workflow, runId: Number(values.run), runAttempt: Number(values.attempt) };
const output = resolve(values.output ?? ''); let aggregate;
try {
  const root = resolve(values.input ?? ''); const expectedBundles = new Set(['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(worker => `f02b-${expected.runId}-${worker}`));
  const entries = await readdir(root, { withFileTypes: true });
  if (entries.length !== 4 || entries.some(entry => !entry.isDirectory() || !expectedBundles.has(entry.name))) throw new Error('F0.2B merge rejects missing or foreign worker evidence bundles');
  const evidence = await Promise.all([...expectedBundles].map(async bundle => JSON.parse(await readFile(resolve(root, bundle, 'semantic.json'), 'utf8'))));
  aggregate = mergeSemanticMatrix(evidence, expected);
} catch (failure) { aggregate = { schema: 1, kind: 'f02b-reference-container-native-semantic-merge', status: 'incomplete', ...expected, failure: String(failure?.message ?? failure) }; await writeFile(output, `${JSON.stringify(aggregate)}\n`, { flag: 'wx' }); throw failure; }
await writeFile(output, `${JSON.stringify(aggregate)}\n`, { flag: 'wx' });
