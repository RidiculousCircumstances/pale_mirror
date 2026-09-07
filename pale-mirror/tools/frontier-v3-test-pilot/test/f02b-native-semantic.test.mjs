import assert from 'node:assert/strict';
import test from 'node:test';
import { declaredNamespaces } from '../src/f0vb-qualification.mjs';
import { laneFor, mergeSemanticMatrix } from '../src/f02b-native-semantic.mjs';

const sha = 'a'.repeat(40); const hash = 'b'.repeat(64);
const expected = Object.freeze({ qualificationId: 'f02b-r1', repository: 'RidiculousCircumstances/pale_mirror', headSha: sha, workflowSha: sha,
  workflowRef: 'RidiculousCircumstances/pale_mirror/.github/workflows/f02b-reference-container-semantic.yml@refs/heads/main', runId: 44, runAttempt: 1 });

function evidence(worker, index = Number(worker.at(-1))) {
  const lane = laneFor(worker); const namespaces = declaredNamespaces({ workspace: `/tmp/f02b/workspace-${worker}`, temp: `/tmp/f02b/${worker}`, runId: 44, runAttempt: 1, worker });
  return { schema: 1, kind: 'f02b-reference-container-native-semantic', status: 'passed', ...expected, worker, lane,
    jobId: 100 + index, runnerId: 200 + index, runnerName: `pm-f02b-${index}`, startedAtMillis: 1_700_000_000_000 + index,
    finishedAtMillis: 1_700_000_020_000 + index, gradlePid: 300 + index, launchTarget: 'forgeserverdev',
    requiredTest: `pm-frontier-v3-reference-${lane}:0`, requiredTestCount: 1, jarSha256: hash, namespaces };
}

test('F0.2B semantic aggregate requires four immutable native Minecraft lanes', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  const merged = mergeSemanticMatrix(complete, expected);
  assert.equal(merged.status, 'ok'); assert.equal(merged.overlapMillis, 19_997);
  assert.deepEqual(merged.lanes.map(value => value.lane), ['depot-never-visited', 'depot-visited-unloaded', 'hive-zero-player', 'conflict-restart']);
});

test('F0.2B semantic aggregate fails closed for missing, stale, duplicate and non-native evidence', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  assert.throws(() => mergeSemanticMatrix(complete.slice(0, 3), expected), /missing or extra/);
  const stale = complete.map(value => ({ ...value })); stale[0].headSha = 'c'.repeat(40);
  assert.throws(() => mergeSemanticMatrix(stale, expected), /invalid immutable identity/);
  const duplicate = complete.map(value => ({ ...value })); duplicate[3].worker = 'worker-2'; duplicate[3].lane = laneFor('worker-2'); duplicate[3].requiredTest = `pm-frontier-v3-reference-${duplicate[3].lane}:0`;
  assert.throws(() => mergeSemanticMatrix(duplicate, expected), /duplicate workers/);
  const nonNative = complete.map(value => ({ ...value })); nonNative[1].launchTarget = 'node';
  assert.throws(() => mergeSemanticMatrix(nonNative, expected), /no completed native semantic assertion/);
});
