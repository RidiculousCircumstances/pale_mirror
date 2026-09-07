import { readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { mergeQualification } from './f0vb-qualification.mjs';
const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const input = resolve(values.input ?? ''); const output = resolve(values.output ?? '');
const expected = {
  qualificationId: values.qualification, repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'],
  workflowRef: values.workflow, runId: Number(values.run), runAttempt: Number(values.attempt)
};
let merged;
try {
  const evidence = await readLeaseEvidence(input);
  merged = mergeQualification(evidence, expected);
} catch (failure) {
  merged = { schema: 2, kind: 'f0vb-native-qualification-merge', status: 'incomplete', ...expected, failure: String(failure?.message ?? failure) };
  await writeFile(output, JSON.stringify(merged) + '\n', { flag: 'wx' });
  throw failure;
}
await writeFile(output, JSON.stringify(merged) + '\n', { flag: 'wx' });

async function readLeaseEvidence(root) {
  const files = await readdir(root, { recursive: true });
  return Promise.all(files.filter(name => name.endsWith('/lease.json') || name === 'lease.json')
    .map(async name => JSON.parse(await readFile(resolve(root, name), 'utf8'))));
}
