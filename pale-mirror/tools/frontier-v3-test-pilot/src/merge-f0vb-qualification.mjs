import { readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { mergeQualification } from './f0vb-qualification.mjs';
const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const input = resolve(values.input ?? ''); const output = resolve(values.output ?? '');
const evidence = await Promise.all((await readdir(input)).filter(name => name.endsWith('.json')).map(async name => JSON.parse(await readFile(resolve(input, name), 'utf8'))));
const merged = mergeQualification(evidence, {
  qualificationId: values.qualification, repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'],
  workflowRef: values.workflow, runId: Number(values.run), runAttempt: Number(values.attempt)
});
await writeFile(output, JSON.stringify(merged) + '\n', { flag: 'wx' });
