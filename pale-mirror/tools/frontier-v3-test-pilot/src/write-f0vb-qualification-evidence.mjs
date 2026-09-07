import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { F0VB_EVIDENCE_KIND, F0VB_EVIDENCE_SCHEMA, assertWorkerEvidence } from './f0vb-qualification.mjs';
const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const namespaces = JSON.parse(values.namespaces ?? '');
const evidence = {
  schema: F0VB_EVIDENCE_SCHEMA, kind: F0VB_EVIDENCE_KIND, worker: values.worker, qualificationId: values.qualification,
  repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'], workflowRef: values.workflow, runId: Number(values.run), runAttempt: Number(values.attempt),
  jobId: Number(values.job), runnerId: Number(values.runner), runnerName: values['runner-name'], lease: values.lease,
  startedAtMillis: Number(values.started), finishedAtMillis: Number(values.finished), namespaces
};
assertWorkerEvidence(evidence);
const output = resolve(values.output ?? ''); if (!output.startsWith(`${process.cwd()}/`)) throw new Error('qualification output escapes workspace');
await mkdir(dirname(output), { recursive: true });
await writeFile(output, JSON.stringify(evidence) + '\n', { flag: 'wx' });
