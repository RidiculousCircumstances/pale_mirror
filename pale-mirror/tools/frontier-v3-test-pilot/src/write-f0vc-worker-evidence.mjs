import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { writeWorkerEvidence } from './f0vc-pipeline.mjs';

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
async function main() {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.replace(/^--/, '').split('=', 2); return [key, value]; }));
  let plan; try { plan = JSON.parse(await readFile(resolve(values.plan ?? ''), 'utf8')); } catch { return writePlanUnavailable(values); }
  const worker = plan.workers.find((entry) => entry.worker === values.worker);
  if (!worker) return writePlanUnavailable(values, 'F0.VC evidence worker is not planned');
  let result; try { result = JSON.parse(await readFile(resolve(values.result), 'utf8')); } catch { result = { status: 'failed', reason: 'native result unavailable' }; }
  let job; try { job = JSON.parse(await readFile(resolve(values.job), 'utf8')); } catch { job = undefined; }
  const reported = ['success', 'failure', 'cancelled'].includes(values.status) ? values.status : 'cancelled';
  const jobId = job?.jobId ?? job?.id;
  const status = reported === 'success' && result.status === 'ok' && Number.isSafeInteger(jobId) && jobId > 0 ? 'success' : reported === 'success' ? 'failure' : reported;
  await writeWorkerEvidence({ output: resolve(values.output), worker: values.worker, status, runtimeContentSha256: plan.runtimeContentSha256,
    namespace: worker.namespace, port: worker.port, display: worker.display, matrixPlanSha256: worker.matrixPlanSha256, assignmentContentSha256: worker.assignmentContentSha256, artifact: `f0vc-${plan.runId}-${worker.worker}`,
    ...(jobId === undefined ? {} : { jobId }), result: job === undefined ? { status: 'failed', reason: 'job identity unavailable', nativeResult: result } : result });
}

async function writePlanUnavailable(values, reason = 'F0.VC producer plan unavailable') {
  const output = resolve(values.output ?? '');
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-f0vc-worker-failure', worker: values.worker,
    status: ['failure', 'cancelled'].includes(values.status) ? values.status : 'failure', reason }, null, 2)}\n`, { flag: 'wx' });
}
