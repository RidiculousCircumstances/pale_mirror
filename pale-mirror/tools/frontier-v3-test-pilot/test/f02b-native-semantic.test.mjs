import assert from 'node:assert/strict';
import test from 'node:test';
import { assertPrimaryEvidence, f02bNamespaces, hashJson, laneFor, mergeSemanticMatrix } from '../src/f02b-native-semantic.mjs';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const sha = 'a'.repeat(40); const hash = 'b'.repeat(64);
const expected = Object.freeze({ qualificationId: 'f02b-r1', repository: 'RidiculousCircumstances/pale_mirror', headSha: sha, workflowSha: sha,
  workflowRef: 'RidiculousCircumstances/pale_mirror/.github/workflows/f02b-reference-container-semantic.yml@refs/heads/main', runId: 44, runAttempt: 1 });

function evidence(worker, index = Number(worker.at(-1))) {
  const lane = laneFor(worker); const namespaces = f02bNamespaces({ workspace: `/tmp/f02b/workspace-${worker}`, temp: `/tmp/f02b/${worker}`, runId: 44, runAttempt: 1, worker });
  const conflict = lane === 'conflict-restart';
  const replica = conflict
    ? { state: 'CONFLICT', revision: 3, canonicalRevision: 7, conflict: 'FINGERPRINT_MISMATCH', fingerprint: 'sha256:expected', provenance: 'pale-mirror:reference-container:test', observedFingerprint: 'sha256:missing-container:1-depot', observedProvenance: 'missing:container:1-depot' }
    : { state: 'OBSERVED_CURRENT', revision: 3, canonicalRevision: 7, conflict: '', fingerprint: 'sha256:current', provenance: 'pale-mirror:reference-container:test', observedFingerprint: '', observedProvenance: '' };
  const domain = lane === 'depot-never-visited'
    ? { family: 'settlement-provision', foodStatus: 'SECURE', available: 27, fulfilled: 37, intentStatus: 'CONFIRMED' }
    : lane === 'depot-visited-unloaded'
      ? { family: 'settlement-production', orderStatus: 'FULFILLED', itemCount: 64, itemCustody: 'CONTAINER_SLOT' }
      : lane === 'hive-zero-player'
        ? { family: 'hive-growth', growthJobs: 0, addedOrgans: 1, spawnedBioforms: 1, intentStatus: 'CONFIRMED' }
        : { family: 'depot-conflict', recovery: 'abrupt' };
  const custody = { status: conflict ? 'RELEASED' : 'ACQUIRED', epoch: 1, replicaRevision: 3 };
  const conflicts = conflict ? [
    { scenario: 'disposable-f02b-depot-changed-restart.json', replica: { ...replica, conflict: 'FINGERPRINT_MISMATCH', observedFingerprint: 'sha256:changed', observedProvenance: replica.provenance } },
    { scenario: 'disposable-f02b-depot-foreign-restart.json', replica: { ...replica, conflict: 'FINGERPRINT_AND_PROVENANCE_MISMATCH', observedFingerprint: 'sha256:foreign', observedProvenance: 'foreign:container-owner=untagged;replica-provenance=missing' } },
    { scenario: 'disposable-f02b-depot-conflict-restart.json', replica }
  ] : undefined;
  return { schema: 1, kind: 'f02b-reference-container-native-semantic', status: 'passed', ...expected, worker, lane,
    jobId: 100 + index, runnerId: 200 + index, runnerName: `pm-f02b-${index}`, startedAtMillis: 1_700_000_000_000 + index,
    finishedAtMillis: 1_700_000_020_000 + index, gradlePid: 300 + index, launchTarget: 'forgeserverdev',
    requiredTest: `scenario:${lane}`, requiredTestCount: conflict ? 3 : 1, runtimeContentSha256: hash, jarSha256: hash, primarySha256: hash, namespaces, launchTarget: 'normal-disposable-v3-server',
    terminal: { lane, domain, container: { status: 'ok', replica, custody }, replica, custody, ...(conflicts === undefined ? {} : { conflicts }) } };
}

test('F0.2B semantic aggregate requires four immutable native Minecraft lanes', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  const merged = mergeSemanticMatrix(complete, expected);
  assert.equal(merged.status, 'ok'); assert.equal(merged.overlapMillis, 19_997);
  assert.deepEqual(merged.lanes.map(value => value.lane), ['depot-never-visited', 'depot-visited-unloaded', 'hive-zero-player', 'conflict-restart']);
});

test('F0.2B primary receipts bind the retained runtime and complete normal-world scenario facts', () => {
  const semantic = evidence('worker-0');
  const primary = { schema: 1, kind: 'f02b-reference-container-native-semantic-primary', status: 'passed', worker: semantic.worker, lane: semantic.lane,
    runtimeContentSha256: semantic.runtimeContentSha256, jarSha256: semantic.jarSha256,
    identity: { qualificationId: semantic.qualificationId, repository: semantic.repository, headSha: semantic.headSha, workflowSha: semantic.workflowSha,
      workflowRef: semantic.workflowRef, runId: semantic.runId, runAttempt: semantic.runAttempt, jobId: semantic.jobId, runnerId: semantic.runnerId,
      runnerName: semantic.runnerName, launchTarget: semantic.launchTarget, requiredTest: semantic.requiredTest, requiredTestCount: semantic.requiredTestCount },
    runtime: { receipt: { worker: semantic.worker, runtimeContentSha256: semantic.runtimeContentSha256 }, preparedIdentity: { sourceContent: { sha256: hash }, artifactSha256: hash } },
    manifests: [{ scenario: 'disposable-settlement-provision.json', value: { status: 'ok', recovery: { mode: 'graceful' }, diagnostics: [] } }], terminal: semantic.terminal };
  primary.manifests[0].sha256 = hashJson(primary.manifests[0].value);
  assert.equal(assertPrimaryEvidence(primary, semantic), primary);
  const drifted = structuredClone(primary); drifted.runtime.receipt.runtimeContentSha256 = 'c'.repeat(64);
  assert.throws(() => assertPrimaryEvidence(drifted, semantic), /lacks the consumed prepared runtime identity/);
  const corrupt = structuredClone(primary); corrupt.manifests[0].value.status = 'foreign';
  assert.throws(() => assertPrimaryEvidence(corrupt, semantic), /corrupt scenario receipt/);
});

test('F0.2B reserves adjacent RCON ports outside every other worker game socket', () => {
  const spaces = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(worker => f02bNamespaces({ workspace: `/tmp/f02b/${worker}`, temp: `/tmp/f02b/${worker}`, runId: 44, runAttempt: 1, worker }));
  assert.deepEqual(spaces.map(value => value.port), [26200, 26202, 26204, 26206]);
  assert.equal(new Set(spaces.flatMap(value => [value.port, value.port + 1])).size, 8);
});

test('F0.2B consumers use a private checkout and prepare only a disposable world from immutable runtime bytes', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const [workflow, runner, isolated, scenarioRunner, build] = await Promise.all([
    readFile(resolve(project, '..', '.github/workflows/f02b-reference-container-semantic.yml'), 'utf8'),
    readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-f02b-native-semantic.mjs'), 'utf8'),
    readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs'), 'utf8'),
    readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-scenario.mjs'), 'utf8'),
    readFile(resolve(project, 'pale-mirror-neoforge/build.gradle'), 'utf8')
  ]);
  assert.match(workflow, /path: f02b-\$\{\{ matrix\.worker \}\}-workspace/);
  assert.match(workflow, /working-directory: f02b-\$\{\{ matrix\.worker \}\}-workspace\/pale-mirror/);
  assert.match(workflow, /path: f02b-merge-workspace/);
  assert.match(workflow, /path: f02b-\$\{\{ matrix\.worker \}\}-workspace\/pale-mirror\/build\/f02b-producer/);
  assert.match(workflow, /path: f02b-merge-workspace\/pale-mirror\/f02b-evidence/);
  assert.match(runner, /FRONTIER_V3_PILOT_PREPARED_RUNTIME: 'true'/);
  assert.match(isolated, /-PfrontierV3PilotPreparedRuntime=true/);
  assert.match(scenarioRunner, /ensurePreparedLaunchWorkingDirectory/);
  assert.match(build, /frontierV3PilotPreparedRuntime != 'true'/);
});

test('F0.2B semantic aggregate fails closed for missing, stale, duplicate and non-native evidence', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  assert.throws(() => mergeSemanticMatrix(complete.slice(0, 3), expected), /missing or extra/);
  const stale = complete.map(value => ({ ...value })); stale[0].headSha = 'c'.repeat(40);
  assert.throws(() => mergeSemanticMatrix(stale, expected), /invalid immutable identity/);
  const duplicate = complete.map(value => structuredClone(value)); duplicate[3] = structuredClone(complete[2]);
  assert.throws(() => mergeSemanticMatrix(duplicate, expected), /duplicate workers/);
  const nonNative = complete.map(value => ({ ...value })); nonNative[1].launchTarget = 'node';
  assert.throws(() => mergeSemanticMatrix(nonNative, expected), /no completed native semantic assertion/);
  const semanticHole = complete.map(value => structuredClone(value)); semanticHole[0].terminal.domain.available = 64;
  assert.throws(() => mergeSemanticMatrix(semanticHole, expected), /settlement provision terminal is not exact/);
  const fabricatedConflict = complete.map(value => structuredClone(value)); fabricatedConflict[3].terminal.conflicts[2].replica.observedProvenance = 'pale-mirror:reference-container:test';
  assert.throws(() => mergeSemanticMatrix(fabricatedConflict, expected), /changed\/foreign\/missing evidence/);
  const missingRuntime = complete.map(value => ({ ...value })); delete missingRuntime[0].runtimeContentSha256;
  assert.throws(() => mergeSemanticMatrix(missingRuntime, expected), /invalid immutable identity/);
  const mixedRuntime = complete.map(value => ({ ...value })); mixedRuntime[2].runtimeContentSha256 = 'c'.repeat(64);
  assert.throws(() => mergeSemanticMatrix(mixedRuntime, expected), /mixed prepared runtimes/);
});
