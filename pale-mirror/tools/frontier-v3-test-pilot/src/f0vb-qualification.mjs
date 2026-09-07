const WORKERS = Object.freeze(['worker-0', 'worker-1', 'worker-2', 'worker-3']);
const QUALIFICATION = /^[A-Za-z0-9._-]{1,120}$/;
const SHA = /^[0-9a-f]{40}$/;
const ABSOLUTE_PATH = /^\/[^\n\r]*$/;
const DISPLAY = /^:[0-9]+$/;
const REPOSITORY = /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/;

export const F0VB_EVIDENCE_SCHEMA = 2;
export const F0VB_EVIDENCE_KIND = 'f0vb-native-lease';

export function workerNames() { return [...WORKERS]; }

export function assertWorkerEvidence(value, expected = {}) {
  if (!value || typeof value !== 'object' || value.schema !== F0VB_EVIDENCE_SCHEMA || value.kind !== F0VB_EVIDENCE_KIND) {
    throw new Error('F0.VB evidence has an unknown schema or kind');
  }
  if (!WORKERS.includes(value.worker) || !QUALIFICATION.test(value.qualificationId ?? '')) throw new Error('F0.VB evidence has an invalid assignment identity');
  if (!REPOSITORY.test(value.repository ?? '') || !SHA.test(value.headSha ?? '') || !SHA.test(value.workflowSha ?? '')
    || value.workflowSha !== value.headSha || typeof value.workflowRef !== 'string' || !value.workflowRef.includes('.github/workflows/f0vb-native-qualification.yml@')) {
    throw new Error('F0.VB evidence has an invalid immutable build identity');
  }
  for (const key of ['runId', 'runAttempt', 'jobId', 'runnerId']) {
    if (!Number.isSafeInteger(value[key]) || value[key] <= 0) throw new Error(`F0.VB evidence has an invalid ${key}`);
  }
  if (typeof value.runnerName !== 'string' || !value.runnerName || !ABSOLUTE_PATH.test(value.lease ?? '')) throw new Error('F0.VB evidence has an invalid runner or lease identity');
  if (!Number.isSafeInteger(value.startedAtMillis) || !Number.isSafeInteger(value.finishedAtMillis)
    || value.startedAtMillis >= value.finishedAtMillis || value.finishedAtMillis - value.startedAtMillis > 120_000) {
    throw new Error('F0.VB evidence has an unbounded or invalid millisecond lease interval');
  }
  assertNamespaces(value.namespaces, value.worker);
  for (const [key, expectedValue] of Object.entries(expected)) {
    if (expectedValue !== undefined && value[key] !== expectedValue) throw new Error(`F0.VB evidence is foreign or stale for ${key}`);
  }
  return value;
}

export function mergeQualification(evidence, expected) {
  if (!expected || typeof expected !== 'object') throw new Error('F0.VB merge requires an immutable expected identity');
  if (!Array.isArray(evidence) || evidence.length !== WORKERS.length) throw new Error('F0.VB merge rejects missing or extra worker evidence');
  const checked = evidence.map((value) => assertWorkerEvidence(value, expected));
  const workers = checked.map((value) => value.worker).sort();
  if (workers.join(',') !== WORKERS.join(',')) throw new Error('F0.VB merge rejects duplicate or incomplete worker assignment');
  for (const field of ['jobId', 'runnerId', 'runnerName', 'lease']) unique(checked, field, 'F0.VB merge rejects duplicate worker identity');
  for (const field of ['workspace', 'temp', 'gradle', 'cache', 'world', 'process', 'display', 'port']) {
    unique(checked.map((value) => value.namespaces), field, 'F0.VB merge rejects shared isolation namespace');
  }
  const latestStart = Math.max(...checked.map((value) => value.startedAtMillis));
  const earliestFinish = Math.min(...checked.map((value) => value.finishedAtMillis));
  if (latestStart >= earliestFinish) throw new Error('F0.VB merge rejects non-overlapping worker leases');
  return {
    schema: F0VB_EVIDENCE_SCHEMA,
    kind: 'f0vb-native-qualification-merge',
    status: 'ok',
    qualificationId: expected.qualificationId,
    repository: expected.repository,
    headSha: expected.headSha,
    workflowSha: expected.workflowSha,
    workflowRef: expected.workflowRef,
    runId: expected.runId,
    runAttempt: expected.runAttempt,
    workers,
    jobs: checked.map(({ worker, jobId, runnerId, runnerName, namespaces }) => ({ worker, jobId, runnerId, runnerName, namespaces })).sort((a, b) => a.worker.localeCompare(b.worker)),
    overlapMillis: earliestFinish - latestStart
  };
}

export function declaredNamespaces({ workspace, temp, runId, runAttempt, worker }) {
  if (!ABSOLUTE_PATH.test(workspace ?? '') || !ABSOLUTE_PATH.test(temp ?? '') || !WORKERS.includes(worker) || !Number.isSafeInteger(runId) || !Number.isSafeInteger(runAttempt)) {
    throw new Error('F0.VB cannot declare an invalid isolation namespace');
  }
  const index = Number(worker.slice('worker-'.length));
  const root = `${temp}/f0vb-${runId}-${runAttempt}-${worker}`;
  return Object.freeze({
    workspace, temp: root + '/temp', gradle: root + '/gradle', cache: root + '/cache', world: root + '/world', process: root + '/process',
    display: `:${260 + index}`, port: 26100 + index
  });
}

function assertNamespaces(value, worker) {
  if (!value || typeof value !== 'object') throw new Error('F0.VB evidence has no isolation namespace');
  for (const field of ['workspace', 'temp', 'gradle', 'cache', 'world', 'process']) {
    if (!ABSOLUTE_PATH.test(value[field] ?? '')) throw new Error(`F0.VB evidence has invalid ${field} namespace`);
  }
  if (!DISPLAY.test(value.display ?? '') || !Number.isSafeInteger(value.port) || value.port < 1024 || value.port > 65535) {
    throw new Error('F0.VB evidence has invalid display or port namespace');
  }
  const index = Number(worker.slice('worker-'.length));
  if (value.display !== `:${260 + index}` || value.port !== 26100 + index) throw new Error('F0.VB evidence has an incomparable assignment namespace');
}

function unique(values, field, message) {
  if (new Set(values.map((value) => value[field])).size !== values.length) throw new Error(message);
}
