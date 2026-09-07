import assert from 'node:assert/strict';
import test from 'node:test';
import { evaluateCapacity } from '../src/f0vb-capacity-preflight.mjs';
import { declaredNamespaces, mergeQualification, workerNames } from '../src/f0vb-qualification.mjs';

const sha = 'a'.repeat(40);
const expected = Object.freeze({ qualificationId: 'f0vb-r5-contract', repository: 'RidiculousCircumstances/pale_mirror', headSha: sha,
  workflowSha: sha, workflowRef: 'RidiculousCircumstances/pale_mirror/.github/workflows/f0vb-native-qualification.yml@refs/heads/main', runId: 44, runAttempt: 1 });

function evidence(worker, index = Number(worker.at(-1))) {
  return { schema: 2, kind: 'f0vb-native-lease', ...expected, worker, jobId: 100 + index, runnerId: 200 + index,
    runnerName: `pm-f0vb-${index}`, lease: `/tmp/f0vb/${worker}`, startedAtMillis: 1_700_000_000_000 + index,
    finishedAtMillis: 1_700_000_020_000 + index, namespaces: declaredNamespaces({ workspace: `/tmp/f0vb/workspace-${worker}`, temp: `/tmp/f0vb/${worker}`, runId: 44, runAttempt: 1, worker }) };
}

test('F0.VB merge accepts one complete comparable four-worker qualification', () => {
  const merged = mergeQualification(workerNames().map(evidence), expected);
  assert.equal(merged.status, 'ok');
  assert.equal(merged.overlapMillis, 19_997);
  assert.deepEqual(merged.workers, workerNames());
  assert.equal(merged.jobs.length, 4);
});

test('F0.VB merge fails closed for missing, duplicate, stale, foreign and incomparable evidence', () => {
  const complete = workerNames().map(evidence);
  assert.throws(() => mergeQualification(complete.slice(0, 3), expected), /missing or extra/);
  const duplicate = complete.map(copy); duplicate[3].worker = 'worker-2'; duplicate[3].namespaces = { ...declaredNamespaces({ workspace: '/tmp/f0vb/workspace-duplicate', temp: '/tmp/f0vb/duplicate', runId: 44, runAttempt: 1, worker: 'worker-2' }) };
  assert.throws(() => mergeQualification(duplicate, expected), /duplicate or incomplete/);
  const stale = complete.map(copy); stale[0].runAttempt = 2;
  assert.throws(() => mergeQualification(stale, expected), /foreign or stale/);
  const foreign = complete.map(copy); foreign[1].headSha = 'b'.repeat(40); foreign[1].workflowSha = 'b'.repeat(40);
  assert.throws(() => mergeQualification(foreign, expected), /foreign or stale/);
  const sharedNamespace = complete.map(copy); sharedNamespace[3].namespaces.cache = sharedNamespace[2].namespaces.cache;
  assert.throws(() => mergeQualification(sharedNamespace, expected), /shared isolation namespace/);
});

test('F0.VB evidence rejects malformed timestamps and cross-worker namespace assignment', () => {
  const complete = workerNames().map(evidence);
  complete[0].finishedAtMillis = complete[0].startedAtMillis + 120_001;
  assert.throws(() => mergeQualification(complete, expected), /unbounded/);
  const wrongDisplay = workerNames().map(evidence).map(copy); wrongDisplay[1].namespaces.display = ':260';
  assert.throws(() => mergeQualification(wrongDisplay, expected), /incomparable assignment namespace/);
});

function copy(value) { return { ...value, namespaces: { ...value.namespaces } }; }

test('same-host capacity admission requires four CPU slots and bounded memory and disk', () => {
  assert.deepEqual(evaluateCapacity({ workers: 4, cpu: 4, memAvailableMiB: 8192, diskAvailableMiB: 20480 }),
    { admitted: true, workers: 4, cpu: 4, memAvailableMiB: 8192, diskAvailableMiB: 20480, reasons: [] });
  assert.equal(evaluateCapacity({ workers: 4, cpu: 3, memAvailableMiB: 8192, diskAvailableMiB: 20480 }).admitted, false);
  assert.equal(evaluateCapacity({ workers: 4, cpu: 4, memAvailableMiB: 8191, diskAvailableMiB: 20480 }).admitted, false);
  assert.equal(evaluateCapacity({ workers: 4, cpu: 4, memAvailableMiB: 8192, diskAvailableMiB: 20479 }).admitted, false);
});
