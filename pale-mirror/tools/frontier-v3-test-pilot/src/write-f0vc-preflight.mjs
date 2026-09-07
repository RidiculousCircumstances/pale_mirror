import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { preflight } from './f0vc-pipeline.mjs';
import { validateFourWorkerMatrixPlan } from './ci-matrix.mjs';
import { compilePersistentWorkerPlan } from './persistent-worker-plan.mjs';
import { createHash } from 'node:crypto';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

async function main() {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.replace(/^--/, '').split('=', 2); return [key, value]; }));
  const receipt = JSON.parse(await readFile(resolve(project, values.runtime ?? ''), 'utf8'));
  const matrixArtifact = JSON.parse(await readFile(resolve(project, values.matrix ?? ''), 'utf8'));
  const matrix = validateFourWorkerMatrixPlan(matrixArtifact.plan);
  const runId = Number(values.run); const root = resolve(values.root ?? ''); const output = resolve(project, values.output ?? '');
  if (!Number.isSafeInteger(runId) || runId <= 0 || !root.startsWith('/home/rd/proj/pm-f0vc-') || !output.startsWith(resolve(project, 'build') + '/')) {
    throw new Error('usage: write-f0vc-preflight --runtime=build/receipt.json --run=<id> --root=/home/rd/proj/pm-f0vc-* --output=build/f0vc/plan.json');
  }
  const workers = [0, 1, 2, 3].map((index) => {
    const worker = `worker-${index}`; const assignment = compilePersistentWorkerPlan(matrix, worker, 'correctness-1');
    return { worker, runtimeContentSha256: receipt.contentSha256, namespace: `${root}/workers/${worker}`, port: 26300 + index * 2,
      display: `:${1300 + index * 2}`, matrixPlanSha256: matrix.planSha256, assignmentContentSha256: assignment.contentSha256,
      worlds: assignment.lanes.map((lane) => lane.worldKey), cases: assignment.lanes.map((lane) => ({ id: lane.id, world: lane.worldKey,
        completions: lane.segments.map((segment) => segment.completion) })) };
  });
  const value = preflight({ schema: 1, kind: 'frontier-v3-f0vc-preflight', runtimeContentSha256: receipt.contentSha256, runId,
    expectedArtifacts: workers.map((entry) => `f0vc-${runId}-${entry.worker}`), workers });
  await mkdir(dirname(output), { recursive: true }); await writeFile(output, `${JSON.stringify(value, null, 2)}\n`, { flag: 'wx' });
}
function hash(value) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }
