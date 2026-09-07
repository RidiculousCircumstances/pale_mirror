import { workerNames } from './f0vb-qualification.mjs';

export const F02B_SCHEMA = 1;
export const F02B_KIND = 'f02b-reference-container-native-semantic';
const SHA = /^[0-9a-f]{40}$/;
const HASH = /^[0-9a-f]{64}$/;
const QUALIFICATION = /^[A-Za-z0-9._-]{1,120}$/;
const LANES = Object.freeze({
  'worker-0': 'depot-never-visited',
  'worker-1': 'depot-visited-unloaded',
  'worker-2': 'hive-zero-player',
  'worker-3': 'conflict-restart'
});

export function laneFor(worker) {
  if (!(worker in LANES)) throw new Error('F0.2B semantic matrix has an unknown worker');
  return LANES[worker];
}

export function assertSemanticEvidence(value, expected = {}) {
  if (!value || typeof value !== 'object' || value.schema !== F02B_SCHEMA || value.kind !== F02B_KIND) throw new Error('F0.2B evidence has unknown schema or kind');
  if (laneFor(value.worker) !== value.lane || !QUALIFICATION.test(value.qualificationId ?? '')) throw new Error('F0.2B evidence has an invalid matrix assignment');
  if (!SHA.test(value.headSha ?? '') || value.headSha !== value.workflowSha || !HASH.test(value.jarSha256 ?? '')
    || !String(value.workflowRef ?? '').includes('.github/workflows/f02b-reference-container-semantic.yml@')) throw new Error('F0.2B evidence has an invalid immutable identity');
  for (const key of ['runId', 'runAttempt', 'jobId', 'runnerId', 'startedAtMillis', 'finishedAtMillis', 'gradlePid']) {
    if (!Number.isSafeInteger(value[key]) || value[key] <= 0) throw new Error(`F0.2B evidence has invalid ${key}`);
  }
  if (value.startedAtMillis >= value.finishedAtMillis || value.finishedAtMillis - value.startedAtMillis > 20 * 60_000) throw new Error('F0.2B evidence has an unbounded launch interval');
  if (value.launchTarget !== 'forgeserverdev' || value.requiredTest !== `pm-frontier-v3-reference-${value.lane}:0`
    || value.requiredTestCount !== 1 || value.status !== 'passed') throw new Error('F0.2B evidence has no completed native semantic assertion');
  if (!value.namespaces || typeof value.namespaces !== 'object') throw new Error('F0.2B evidence has no isolated namespaces');
  for (const field of ['workspace', 'temp', 'gradle', 'cache', 'world', 'process']) if (typeof value.namespaces[field] !== 'string' || !value.namespaces[field].startsWith('/')) throw new Error(`F0.2B evidence has invalid ${field} namespace`);
  for (const [key, valueExpected] of Object.entries(expected)) if (valueExpected !== undefined && value[key] !== valueExpected) throw new Error(`F0.2B evidence is foreign or stale for ${key}`);
  return value;
}

export function mergeSemanticMatrix(evidence, expected) {
  if (!Array.isArray(evidence) || evidence.length !== workerNames().length) throw new Error('F0.2B merge rejects missing or extra worker evidence');
  const checked = evidence.map(value => assertSemanticEvidence(value, expected));
  if (new Set(checked.map(value => value.worker)).size !== workerNames().length) throw new Error('F0.2B merge rejects duplicate workers');
  for (const field of ['jobId', 'runnerId', 'runnerName']) if (new Set(checked.map(value => value[field])).size !== checked.length) throw new Error(`F0.2B merge rejects duplicate ${field}`);
  for (const field of ['workspace', 'temp', 'gradle', 'cache', 'world', 'process']) if (new Set(checked.map(value => value.namespaces[field])).size !== checked.length) throw new Error('F0.2B merge rejects shared native namespace');
  const latestStart = Math.max(...checked.map(value => value.startedAtMillis));
  const earliestFinish = Math.min(...checked.map(value => value.finishedAtMillis));
  if (latestStart >= earliestFinish) throw new Error('F0.2B merge rejects non-overlapping Minecraft launches');
  return { schema: F02B_SCHEMA, kind: 'f02b-reference-container-native-semantic-merge', status: 'ok', ...expected,
    workers: checked.map(value => value.worker).sort(), overlapMillis: earliestFinish - latestStart,
    lanes: checked.map(({ worker, lane, jobId, runnerId, runnerName, jarSha256, namespaces }) => ({ worker, lane, jobId, runnerId, runnerName, jarSha256, namespaces })).sort((a, b) => a.worker.localeCompare(b.worker)) };
}
