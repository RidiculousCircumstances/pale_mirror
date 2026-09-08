import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const WORKERS = Object.freeze(['worker-0', 'worker-1', 'worker-2', 'worker-3']);
const SHA = /^[a-f0-9]{64}$/;
const JOB_STATUS = new Set(['success', 'failure', 'cancelled']);

/** Pure, early boundary: this has no Java/Gradle/Minecraft side effect. */
export function preflight(plan) {
  if (!plan || plan.schema !== 1 || plan.kind !== 'frontier-v3-f0vc-preflight' || !SHA.test(plan.runtimeContentSha256 ?? '')
      || !Array.isArray(plan.workers) || plan.workers.length !== 4 || !Array.isArray(plan.expectedArtifacts) || plan.expectedArtifacts.length !== 4) {
    throw new Error('F0.VC preflight plan is malformed');
  }
  const seen = new Set(); const namespaces = new Set(); const endpoints = new Set(); const displays = new Set(); const worlds = new Set(); const matrices = new Set();
  for (const entry of plan.workers) {
    if (!entry || !WORKERS.includes(entry.worker) || seen.has(entry.worker) || entry.runtimeContentSha256 !== plan.runtimeContentSha256
        || typeof entry.namespace !== 'string' || !entry.namespace.startsWith('/') || namespaces.has(entry.namespace)
        || !Number.isInteger(entry.port) || entry.port < 1024 || entry.port >= 65535 || endpoints.has(entry.port) || endpoints.has(entry.port + 1)
        || typeof entry.display !== 'string' || entry.display !== `:${entry.port - 25000}` || displays.has(entry.display)
        || !SHA.test(entry.matrixPlanSha256 ?? '') || matrices.size > 0 && !matrices.has(entry.matrixPlanSha256) || !SHA.test(entry.assignmentContentSha256 ?? '')
        || !Array.isArray(entry.worlds) || entry.worlds.length < 1 || new Set(entry.worlds).size !== entry.worlds.length
        || !Array.isArray(entry.cases) || entry.cases.length < 1 || entry.cases.length > 32) throw new Error('F0.VC preflight worker assignment is malformed');
    seen.add(entry.worker); namespaces.add(entry.namespace); endpoints.add(entry.port); endpoints.add(entry.port + 1); displays.add(entry.display); matrices.add(entry.matrixPlanSha256);
    const caseIds = new Set(); for (const item of entry.cases) {
      if (!item || typeof item.id !== 'string' || caseIds.has(item.id) || !entry.worlds.includes(item.world)
          || !Array.isArray(item.completions) || item.completions.length < 1) throw new Error('F0.VC preflight case assignment is malformed');
      caseIds.add(item.id);
    }
  }
  if ([...seen].sort().join(',') !== WORKERS.join(',')) throw new Error('F0.VC preflight has missing or duplicate workers');
  const expected = new Set(plan.workers.map((entry) => `f0vc-${plan.runId}-${entry.worker}`));
  if (new Set(plan.expectedArtifacts).size !== 4 || plan.expectedArtifacts.some((name) => !expected.has(name))) throw new Error('F0.VC preflight artifact contract is incomplete or foreign');
  return Object.freeze(structuredClone(plan));
}

export function mergeEvidence(plan, evidence) {
  const checked = preflight(plan);
  const base = { schema: 1, kind: 'frontier-v3-f0vc-native-merge', planSha256: hash(JSON.stringify(checked)), runtimeContentSha256: checked.runtimeContentSha256 };
  try {
    if (!Array.isArray(evidence) || evidence.length !== 4) throw new Error('missing worker evidence');
    const expected = new Map(checked.workers.map((entry) => [entry.worker, entry])); const workers = new Set();
    for (const item of evidence) {
      const assignment = expected.get(item?.worker);
      const validJob = Number.isSafeInteger(item?.jobId) && item.jobId > 0;
      if (!assignment || workers.has(item.worker) || !JOB_STATUS.has(item.status) || item.runtimeContentSha256 !== checked.runtimeContentSha256
          || item.namespace !== assignment.namespace || item.port !== assignment.port || item.display !== assignment.display
          || item.matrixPlanSha256 !== assignment.matrixPlanSha256 || item.assignmentContentSha256 !== assignment.assignmentContentSha256
          || item.artifact !== `f0vc-${checked.runId}-${item.worker}` || (item.status === 'success' && (!validJob || !validPrimaryLifecycle(item.primaryLifecycle, assignment, item.worker)))) throw new Error('foreign, duplicate or incomparable worker evidence');
      if (item.status !== 'success' || !item.result || item.result.status !== 'ok') throw new Error(`worker ${item.worker} did not produce successful fresh native evidence`);
      workers.add(item.worker);
    }
    if (workers.size !== 4) throw new Error('incomplete worker coverage');
    return Object.freeze({ ...base, status: 'ok', workers: [...evidence].sort((a, b) => a.worker.localeCompare(b.worker)) });
  } catch (failure) { return Object.freeze({ ...base, status: 'incomplete', failure: String(failure?.message ?? failure), availableWorkers: Array.isArray(evidence) ? evidence.map((item) => item?.worker).filter(Boolean).sort() : [] }); }
}

export async function writeWorkerEvidence({ output, worker, status, runtimeContentSha256, namespace, port, display, matrixPlanSha256, assignmentContentSha256, artifact, jobId, result, primaryLifecycle = undefined }) {
  if (!WORKERS.includes(worker) || !JOB_STATUS.has(status) || !SHA.test(runtimeContentSha256 ?? '') || typeof namespace !== 'string' || !namespace.startsWith('/')
      || !Number.isInteger(port) || typeof display !== 'string' || !SHA.test(matrixPlanSha256 ?? '') || !SHA.test(assignmentContentSha256 ?? '') || typeof artifact !== 'string'
      || (status === 'success' && (!Number.isSafeInteger(jobId) || jobId <= 0))
      || (jobId !== undefined && jobId !== null && (!Number.isSafeInteger(jobId) || jobId <= 0))) throw new Error('F0.VC worker evidence inputs are malformed');
  await mkdir(dirname(output), { recursive: true });
  if (status === 'success' && !validPrimaryLifecycle(primaryLifecycle, { assignmentContentSha256, matrixPlanSha256 }, worker)) throw new Error('F0.VC successful worker evidence lacks its primary lifecycle receipt');
  await writeFile(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-f0vc-worker-evidence', worker, status, runtimeContentSha256, namespace, port, display, matrixPlanSha256, assignmentContentSha256, artifact,
    ...(jobId === undefined || jobId === null ? {} : { jobId }), ...(primaryLifecycle === undefined ? {} : { primaryLifecycle }), result }, null, 2)}\n`, { flag: 'wx' });
}

export async function mergeDirectory({ plan, input, output }) {
  let entries; try { entries = await readdir(input, { withFileTypes: true }); } catch (error) { if (error?.code === 'ENOENT') entries = []; else throw error; }
  const evidence = []; const directories = new Map();
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const path = resolve(input, entry.name, 'worker.json');
    try { const value = JSON.parse(await readFile(path, 'utf8')); evidence.push(value); directories.set(value.worker, resolve(input, entry.name)); } catch { /* represented by incomplete aggregate */ }
  }
  let merged = mergeEvidence(plan, evidence);
  if (merged.status === 'ok') {
    try { for (const item of evidence) await verifyPrimaryLifecycleBundle(directories.get(item.worker), item); }
    catch (failure) { merged = Object.freeze({ ...merged, status: 'incomplete', failure: String(failure?.message ?? failure) }); }
  }
  await mkdir(dirname(output), { recursive: true }); await writeFile(output, `${JSON.stringify(merged, null, 2)}\n`, { flag: 'wx' });
  if (merged.status !== 'ok') throw new Error(`F0.VC merge is incomplete: ${merged.failure}`); return merged;
}

function validPrimaryLifecycle(value, assignment, worker) {
  return value && typeof value === 'object' && value.worker === worker && typeof value.artifactPath === 'string'
    && safeRelative(value.artifactPath) && value.artifactPath === 'primary/persistent-matrix.json'
    && SHA.test(value.sha256 ?? '') && value.assignmentContentSha256 === assignment.assignmentContentSha256
    && value.matrixPlanSha256 === assignment.matrixPlanSha256 && value.projection && Number.isInteger(value.projection.clientPid)
    && value.projection.clientPid > 1 && Array.isArray(value.projection.serverRunIds) && value.projection.serverRunIds.length > 0
    && Array.isArray(value.projection.worldKeys) && value.projection.worldKeys.length > 0
    && Number.isInteger(value.projection.lifecycleEventCount) && value.projection.lifecycleEventCount > 0
    && Number.isInteger(value.projection.crashReceiptCount) && value.projection.crashReceiptCount >= 0
    && value.projection.durableFinalStop === true;
}

async function verifyPrimaryLifecycleBundle(directory, item) {
  if (typeof directory !== 'string' || !validPrimaryLifecycle(item.primaryLifecycle, item, item.worker)) throw new Error('primary lifecycle receipt is missing or foreign');
  const path = resolve(directory, item.primaryLifecycle.artifactPath);
  if (!inside(directory, path) || hash(await readFile(path)) !== item.primaryLifecycle.sha256) throw new Error('primary lifecycle receipt is missing or digest-drifted');
  let report; try { report = JSON.parse(await readFile(path, 'utf8')); } catch { throw new Error('primary lifecycle receipt is unreadable'); }
  const plan = report?.session?.plan;
  if (report?.status !== 'ok' || plan?.kind !== 'frontier-v3-assigned-persistent-matrix' || plan.workerId !== item.worker
      || plan.workerPlanSha256 !== item.assignmentContentSha256 || plan?.source?.planSha256 !== item.matrixPlanSha256
      || !Array.isArray(report?.lifecycle?.events) || report.lifecycle.events.length !== item.primaryLifecycle.projection.lifecycleEventCount
      || !Array.isArray(report?.serverRuns) || report.serverRuns.map((run) => run?.serverRunId).join(',') !== item.primaryLifecycle.projection.serverRunIds.join(',')
      || !Array.isArray(report?.evidence?.worldKeys) || report.evidence.worldKeys.join(',') !== item.primaryLifecycle.projection.worldKeys.join(',')
      || report?.client?.clientPid !== item.primaryLifecycle.projection.clientPid || !Array.isArray(report?.crashReceipts)
      || report.crashReceipts.length !== item.primaryLifecycle.projection.crashReceiptCount || report?.finalStop?.durableSave !== true
      || report?.finalStop?.portClosed !== true) {
    throw new Error('primary lifecycle receipt is inconsistent with worker evidence');
  }
}
function safeRelative(value) { return typeof value === 'string' && value.length > 0 && !value.startsWith('/') && !value.includes('\\') && !value.split('/').includes('..'); }
function inside(root, path) { const value = resolve(path).slice(resolve(root).length + 1); return value.length > 0 && !value.startsWith('..'); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.replace(/^--/, '').split('=', 2); return [key, value]; }));
  if (values.mode === 'merge') await mergeDirectory({ plan: JSON.parse(await readFile(resolve(values.plan), 'utf8')), input: resolve(values.input), output: resolve(values.output) });
  else if (values.mode === 'preflight') { const checked = preflight(JSON.parse(await readFile(resolve(values.plan), 'utf8'))); console.log(JSON.stringify(checked)); }
  else throw new Error('usage: f0vc-pipeline --mode=preflight|merge ...');
}
