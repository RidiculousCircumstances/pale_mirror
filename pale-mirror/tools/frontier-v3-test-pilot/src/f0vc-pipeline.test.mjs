import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, readFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { mergeDirectory, mergeEvidence, preflight } from './f0vc-pipeline.mjs';
import { runtimeTargetFor } from './f0vc-prepared-runtime.mjs';
import { preparedClientSegment } from './run-f0va-persistent-matrix.mjs';
import { liftSegmentDiagnostics } from './run-ci-matrix-shard.mjs';

const runtime = 'a'.repeat(64);
function plan() { return { schema: 1, kind: 'frontier-v3-f0vc-preflight', runtimeContentSha256: runtime, runId: 77,
  expectedArtifacts: ['f0vc-77-worker-0', 'f0vc-77-worker-1', 'f0vc-77-worker-2', 'f0vc-77-worker-3'],
  workers: [0, 1, 2, 3].map((index) => ({ worker: `worker-${index}`, runtimeContentSha256: runtime, namespace: `/tmp/f0vc-${index}`,
    port: 26300 + index * 2, display: `:${1300 + index}`, world: `world-${index}`, cases: index === 0
      ? [{ id: 'smoke-a', lifecycle: 'batch', world: 'world-0' }, { id: 'smoke-b', lifecycle: 'batch', world: 'world-0' }]
      : [{ id: `restart-${index}`, lifecycle: 'isolated', world: `lane-${index}` }] })) }; }
function evidence(value = plan()) { return value.workers.map((entry, index) => ({ worker: entry.worker, status: 'success', runtimeContentSha256: runtime,
  namespace: entry.namespace, port: entry.port, display: entry.display, world: entry.world, artifact: `f0vc-77-${entry.worker}`, jobId: index + 1, result: { status: 'ok' } })); }

test('F0.VC admits only four isolated workers and declared same-world batches', () => {
  assert.equal(preflight(plan()).workers.length, 4);
  const bad = plan(); bad.workers[1].port = bad.workers[0].port;
  assert.throws(() => preflight(bad), /assignment/);
  const overlappingRcon = plan(); overlappingRcon.workers[1].port = overlappingRcon.workers[0].port + 1;
  assert.throws(() => preflight(overlappingRcon), /assignment/);
  const crossWorld = plan(); crossWorld.workers[0].cases[1].world = 'foreign';
  assert.throws(() => preflight(crossWorld), /reuse/);
});
test('F0.VC merge emits incomplete evidence for missing, failed, or foreign jobs', () => {
  assert.equal(mergeEvidence(plan(), evidence()).status, 'ok');
  assert.equal(mergeEvidence(plan(), evidence().slice(0, 3)).status, 'incomplete');
  const failed = evidence(); failed[1].status = 'failure';
  assert.equal(mergeEvidence(plan(), failed).status, 'incomplete');
  const foreign = evidence(); foreign[2].runtimeContentSha256 = 'b'.repeat(64);
  assert.equal(mergeEvidence(plan(), foreign).status, 'incomplete');
  const missingJobIdentity = evidence(); missingJobIdentity[1].status = 'failure'; delete missingJobIdentity[1].jobId;
  const merged = mergeEvidence(plan(), missingJobIdentity);
  assert.equal(merged.status, 'incomplete');
  assert.match(merged.failure, /worker worker-1 did not produce/);
});

test('F0.VC writes an incomplete aggregate when no worker artifact directory exists', async () => {
  const root = await mkdtemp(join(tmpdir(), 'f0vc-merge-'));
  const output = join(root, 'aggregate', 'merge.json');
  await assert.rejects(mergeDirectory({ plan: plan(), input: join(root, 'missing-workers'), output }), /incomplete/);
  const aggregate = JSON.parse(await readFile(output, 'utf8'));
  assert.equal(aggregate.status, 'incomplete');
  assert.match(aggregate.failure, /missing worker evidence/);
});

test('F0.VC preserves launcher member names and Gradle-cache topology in a private consumer view', () => {
  const roots = { projectRoot: '/producer/pale-mirror', gradleRoot: '/producer/.gradle' };
  assert.equal(runtimeTargetFor('/producer/pale-mirror/pale-mirror-neoforge/build/moddev/args.txt', roots),
    'pale-mirror-neoforge/build/moddev/args.txt');
  assert.equal(runtimeTargetFor('/producer/.gradle/caches/modules-2/files-2.1/org.example/demo/1.0/a1/demo-1.0.jar', roots),
    '.f0vc-runtime/gradle/caches/modules-2/files-2.1/org.example/demo/1.0/a1/demo-1.0.jar');
  assert.throws(() => runtimeTargetFor('/outside/demo-1.0.jar', roots), /declared project or Gradle runtime roots/);
});

test('F0.VC waits for the client preparation signal bound to the initial immutable segment', () => {
  assert.equal(preparedClientSegment([{ id: 'segment-immutable-0' }]), 'segment-immutable-0');
  assert.throws(() => preparedClientSegment([]), /initial client segment/);
  assert.throws(() => preparedClientSegment([{ id: '' }]), /initial client segment/);
});

test('F0.VC retains action checkpoints outside diagnostic payloads for declared arrival evidence', () => {
  assert.deepEqual(liftSegmentDiagnostics([{ actionStep: 2, value: { kind: 'process', id: 'job' } }], 3), [
    { observed: { actionStep: 5, value: { kind: 'process', id: 'job' } } }
  ]);
  assert.throws(() => liftSegmentDiagnostics([{ actionStep: 0, value: {} }], 0), /checkpoint/);
});
