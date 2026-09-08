import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { mergeDirectory, mergeEvidence, preflight } from './f0vc-pipeline.mjs';
import { rewriteConsumerText, runtimeTargetFor } from './f0vc-prepared-runtime.mjs';
import { preparedClientSegment } from './run-f0va-persistent-matrix.mjs';
import { liftSegmentDiagnostics } from './run-ci-matrix-shard.mjs';

const runtime = 'a'.repeat(64);
function plan() { return { schema: 1, kind: 'frontier-v3-f0vc-preflight', runtimeContentSha256: runtime, runId: 77,
  expectedArtifacts: ['f0vc-77-worker-0', 'f0vc-77-worker-1', 'f0vc-77-worker-2', 'f0vc-77-worker-3'],
  workers: [0, 1, 2, 3].map((index) => ({ worker: `worker-${index}`, runtimeContentSha256: runtime, namespace: `/tmp/f0vc-${index}`,
    port: 26300 + index * 2, display: `:${1300 + index * 2}`, matrixPlanSha256: 'b'.repeat(64), assignmentContentSha256: `${index}`.repeat(64),
    worlds: [`world-${index}`], cases: [{ id: `lane-${index}`, world: `world-${index}`, completions: ['terminal'] }] })) }; }
function evidence(value = plan()) { return value.workers.map((entry, index) => ({ worker: entry.worker, status: 'success', runtimeContentSha256: runtime,
  namespace: entry.namespace, port: entry.port, display: entry.display, matrixPlanSha256: entry.matrixPlanSha256, assignmentContentSha256: entry.assignmentContentSha256, artifact: `f0vc-77-${entry.worker}`, jobId: index + 1,
  primaryLifecycle: { worker: entry.worker, artifactPath: 'primary/persistent-matrix.json', sha256: 'c'.repeat(64), assignmentContentSha256: entry.assignmentContentSha256,
    matrixPlanSha256: entry.matrixPlanSha256, projection: { clientPid: index + 10, serverRunIds: [`server-${index}`], worldKeys: [`world-${index}`], lifecycleEventCount: 1, crashReceiptCount: 0, durableFinalStop: true } },
  result: { status: 'ok' } })); }

test('F0.VC admits only four isolated workers and declared same-world batches', () => {
  const admitted = preflight(plan());
  assert.equal(admitted.workers.length, 4);
  assert.equal(admitted.workers[3].display, ':1306');
  const bad = plan(); bad.workers[1].port = bad.workers[0].port;
  assert.throws(() => preflight(bad), /assignment/);
  const overlappingRcon = plan(); overlappingRcon.workers[1].port = overlappingRcon.workers[0].port + 1;
  assert.throws(() => preflight(overlappingRcon), /assignment/);
  const foreignDisplay = plan(); foreignDisplay.workers[1].display = ':1301';
  assert.throws(() => preflight(foreignDisplay), /assignment/);
  const crossWorld = plan(); crossWorld.workers[0].cases[0].world = 'foreign';
  assert.throws(() => preflight(crossWorld), /assignment/);
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

test('F0.VC merge cryptographically binds every retained primary lifecycle receipt and rejects loss or drift', async () => {
  const root = await mkdtemp(join(tmpdir(), 'f0vc-primary-')); const input = join(root, 'workers'); const output = join(root, 'merge.json');
  const values = evidence();
  for (const [index, item] of values.entries()) {
    const directory = join(input, item.artifact); const receipt = { status: 'ok', session: { plan: { kind: 'frontier-v3-assigned-persistent-matrix', workerId: item.worker } },
      client: { clientPid: item.primaryLifecycle.projection.clientPid }, serverRuns: [{ serverRunId: `server-${index}` }],
      evidence: { worldKeys: [`world-${index}`] }, lifecycle: { events: [{}] }, crashReceipts: [], finalStop: { durableSave: true, portClosed: true } };
    receipt.session.plan.workerPlanSha256 = item.assignmentContentSha256;
    receipt.session.plan.source = { planSha256: item.matrixPlanSha256 };
    const encoded = `${JSON.stringify(receipt)}\n`; const crypto = await import('node:crypto');
    item.primaryLifecycle.sha256 = crypto.createHash('sha256').update(encoded).digest('hex');
    await mkdir(join(directory, 'primary'), { recursive: true }); await writeFile(join(directory, 'worker.json'), JSON.stringify(item));
    await writeFile(join(directory, 'primary/persistent-matrix.json'), encoded);
  }
  assert.equal((await mergeDirectory({ plan: plan(), input, output })).status, 'ok');
  await writeFile(join(input, values[2].artifact, 'primary/persistent-matrix.json'), '{"tampered":true}\n');
  await assert.rejects(mergeDirectory({ plan: plan(), input, output: join(root, 'drift.json') }), /digest-drifted/);
  const drift = JSON.parse(await readFile(join(root, 'drift.json'), 'utf8'));
  assert.equal(drift.status, 'incomplete');
});

test('F0.VC merge rejects a rehashed same-worker primary receipt with assignment or source-matrix identity drift', async () => {
  const root = await mkdtemp(join(tmpdir(), 'f0vc-primary-identity-')); const input = join(root, 'workers');
  const values = evidence(); const crypto = await import('node:crypto');
  for (const [index, item] of values.entries()) {
    const directory = join(input, item.artifact); const receipt = { status: 'ok', session: { plan: {
      kind: 'frontier-v3-assigned-persistent-matrix', workerId: item.worker, workerPlanSha256: item.assignmentContentSha256,
      source: { planSha256: item.matrixPlanSha256 } } }, client: { clientPid: item.primaryLifecycle.projection.clientPid },
      serverRuns: [{ serverRunId: `server-${index}` }], evidence: { worldKeys: [`world-${index}`] }, lifecycle: { events: [{}] },
      crashReceipts: [], finalStop: { durableSave: true, portClosed: true } };
    const encoded = `${JSON.stringify(receipt)}\n`; item.primaryLifecycle.sha256 = crypto.createHash('sha256').update(encoded).digest('hex');
    await mkdir(join(directory, 'primary'), { recursive: true }); await writeFile(join(directory, 'worker.json'), JSON.stringify(item));
    await writeFile(join(directory, 'primary/persistent-matrix.json'), encoded);
  }
  assert.equal((await mergeDirectory({ plan: plan(), input, output: join(root, 'valid.json') })).status, 'ok');
  for (const [field, replacement] of [['workerPlanSha256', 'f'.repeat(64)], ['source.planSha256', 'e'.repeat(64)]]) {
    const receiptPath = join(input, values[1].artifact, 'primary/persistent-matrix.json'); const receipt = JSON.parse(await readFile(receiptPath, 'utf8'));
    if (field === 'workerPlanSha256') receipt.session.plan.workerPlanSha256 = replacement;
    else receipt.session.plan.source.planSha256 = replacement;
    const encoded = `${JSON.stringify(receipt)}\n`; values[1].primaryLifecycle.sha256 = crypto.createHash('sha256').update(encoded).digest('hex');
    await writeFile(receiptPath, encoded); await writeFile(join(input, values[1].artifact, 'worker.json'), JSON.stringify(values[1]));
    const output = join(root, `${field.replace('.', '-')}.json`);
    await assert.rejects(mergeDirectory({ plan: plan(), input, output }), /inconsistent/);
    assert.equal(JSON.parse(await readFile(output, 'utf8')).status, 'incomplete');
    receipt.session.plan.workerPlanSha256 = values[1].assignmentContentSha256;
    receipt.session.plan.source.planSha256 = values[1].matrixPlanSha256;
    const restored = `${JSON.stringify(receipt)}\n`; values[1].primaryLifecycle.sha256 = crypto.createHash('sha256').update(restored).digest('hex');
    await writeFile(receiptPath, restored); await writeFile(join(input, values[1].artifact, 'worker.json'), JSON.stringify(values[1]));
  }
});

test('F0.VC preserves launcher member names and Gradle-cache topology in a private consumer view', () => {
  const roots = { projectRoot: '/producer/pale-mirror', gradleRoot: '/producer/.gradle' };
  assert.equal(runtimeTargetFor('/producer/pale-mirror/pale-mirror-neoforge/build/moddev/args.txt', roots),
    'pale-mirror-neoforge/build/moddev/args.txt');
  assert.equal(runtimeTargetFor('/producer/.gradle/caches/modules-2/files-2.1/org.example/demo/1.0/a1/demo-1.0.jar', roots),
    '.f0vc-runtime/gradle/caches/modules-2/files-2.1/org.example/demo/1.0/a1/demo-1.0.jar');
  assert.throws(() => runtimeTargetFor('/outside/demo-1.0.jar', roots), /declared project or Gradle runtime roots/);
});

test('F0.VC remaps both listed members and producer-root launcher paths into a consumer view', () => {
  const producer = '/producer/pale-mirror'; const consumer = '/consumer/pale-mirror';
  const paths = new Map([[`${producer}/build/moddev/args.txt`, `${consumer}/build/moddev/args.txt`]]);
  assert.equal(rewriteConsumerText(`-DgameDir=${producer}/build/runs -args ${producer}/build/moddev/args.txt`, paths, producer, consumer),
    `-DgameDir=${consumer}/build/runs -args ${consumer}/build/moddev/args.txt`);
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
