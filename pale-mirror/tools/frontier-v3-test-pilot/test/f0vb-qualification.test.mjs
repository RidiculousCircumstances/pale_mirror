import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { evaluateCapacity } from '../src/f0vb-capacity-preflight.mjs';
import { declaredNamespaces, mergeQualification, workerNames } from '../src/f0vb-qualification.mjs';

const sha = 'a'.repeat(40);
const expected = Object.freeze({ qualificationId: 'f0vb-r5-contract', repository: 'RidiculousCircumstances/pale_mirror', headSha: sha,
  workflowSha: sha, workflowRef: 'RidiculousCircumstances/pale_mirror/.github/workflows/f0vb-native-qualification.yml@refs/heads/main', runId: 44, runAttempt: 1 });

function evidence(worker, index = Number(worker.at(-1))) {
  return { schema: 2, kind: 'f0vb-native-lease', ...expected, worker, jobId: 100 + index, runnerId: 200 + index,
    runnerName: `pm-f0vb-${index}`, lease: `/tmp/f0vb/${worker}`, startedAtMillis: 1_700_000_000_000 + index,
    finishedAtMillis: 1_700_000_020_000 + index, namespaces: declaredNamespaces({ workspace: `/tmp/f0vb/workspace-${worker}`, temp: `/tmp/f0vb/${worker}`, runId: 44, runAttempt: 1, worker }),
    consumption: { pid: 500 + index, boundHost: '127.0.0.1', port: 26100 + index, display: `:${1100 + index}`, processMarker: `/tmp/f0vb/${worker}/f0vb-44-1-${worker}/process/f0vb-native-lease-${500 + index}.json` } };
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
  const duplicate = complete.map(copy); duplicate[3].worker = 'worker-2'; duplicate[3].namespaces = { ...declaredNamespaces({ workspace: '/tmp/f0vb/workspace-duplicate', temp: '/tmp/f0vb/duplicate', runId: 44, runAttempt: 1, worker: 'worker-2' }) }; duplicate[3].consumption = { pid: 503, boundHost: '127.0.0.1', port: 26102, display: ':1102', processMarker: '/tmp/f0vb/duplicate/f0vb-44-1-worker-2/process/f0vb-native-lease-503.json' };
  assert.throws(() => mergeQualification(duplicate, expected), /duplicate or incomplete/);
  const stale = complete.map(copy); stale[0].runAttempt = 2;
  assert.throws(() => mergeQualification(stale, expected), /foreign or stale/);
  const foreign = complete.map(copy); foreign[1].headSha = 'b'.repeat(40); foreign[1].workflowSha = 'b'.repeat(40);
  assert.throws(() => mergeQualification(foreign, expected), /foreign or stale/);
  const sharedNamespace = complete.map(copy); sharedNamespace[3].namespaces.workspace = sharedNamespace[2].namespaces.workspace;
  assert.throws(() => mergeQualification(sharedNamespace, expected), /shared isolation namespace/);
});

test('F0.VB evidence rejects malformed timestamps and cross-worker namespace assignment', () => {
  const complete = workerNames().map(evidence);
  complete[0].finishedAtMillis = complete[0].startedAtMillis + 120_001;
  assert.throws(() => mergeQualification(complete, expected), /unbounded/);
  const wrongDisplay = workerNames().map(evidence).map(copy); wrongDisplay[1].namespaces.display = ':260'; wrongDisplay[1].consumption.display = ':260';
  assert.throws(() => mergeQualification(wrongDisplay, expected), /incomparable assignment namespace/);
});

function copy(value) { return { ...value, namespaces: { ...value.namespaces }, consumption: { ...value.consumption } }; }

test('same-host capacity admission requires four CPU slots and bounded memory and disk', () => {
  assert.deepEqual(evaluateCapacity({ workers: 4, cpu: 4, memAvailableMiB: 8192, diskAvailableMiB: 20480 }),
    { admitted: true, workers: 4, cpu: 4, memoryPerWorkerMiB: 2048, requiredMemoryMiB: 8192, memAvailableMiB: 8192, diskAvailableMiB: 20480, reasons: [] });
  assert.equal(evaluateCapacity({ workers: 4, cpu: 3, memAvailableMiB: 8192, diskAvailableMiB: 20480 }).admitted, false);
  assert.equal(evaluateCapacity({ workers: 4, cpu: 4, memAvailableMiB: 8191, diskAvailableMiB: 20480 }).admitted, false);
  assert.equal(evaluateCapacity({ workers: 4, cpu: 4, memAvailableMiB: 8192, diskAvailableMiB: 20479 }).admitted, false);
  assert.equal(evaluateCapacity({ workers: 4, cpu: 4, memoryPerWorkerMiB: 6144, memAvailableMiB: 24575, diskAvailableMiB: 20480 }).admitted, false);
  assert.equal(evaluateCapacity({ workers: 4, cpu: 4, memoryPerWorkerMiB: 6144, memAvailableMiB: 24576, diskAvailableMiB: 20480 }).admitted, true);
});

test('F0.VB merge retains a visible incomplete aggregate when worker artifacts are missing', async () => {
  const root = await mkdtemp(join(tmpdir(), 'f0vb-incomplete-'));
  const output = join(root, 'merge.json');
  const merger = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'merge-f0vb-qualification.mjs');
  try {
    assert.throws(() => execFileSync('node', [merger, `--input=${root}`, `--output=${output}`, '--qualification=f0vb-r6-test',
      '--repository=RidiculousCircumstances/pale_mirror', `--head=${sha}`, `--workflow-sha=${sha}`,
      '--workflow=RidiculousCircumstances/pale_mirror/.github/workflows/f0vb-native-qualification.yml@refs/heads/main', '--run=44', '--attempt=1'], { stdio: 'pipe' }), /Command failed/);
    const aggregate = JSON.parse(await readFile(output, 'utf8'));
    assert.equal(aggregate.status, 'incomplete');
    assert.match(aggregate.failure, /missing worker evidence bundle/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test('F0.VB artifact bundles retain worker identity and fail closed on invalid layouts', async () => {
  const root = await mkdtemp(join(tmpdir(), 'f0vb-layout-'));
  const merger = resolve(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'merge-f0vb-qualification.mjs');
  const argumentsFor = output => [merger, `--input=${root}`, `--output=${output}`, `--qualification=${expected.qualificationId}`,
    `--repository=${expected.repository}`, `--head=${expected.headSha}`, `--workflow-sha=${expected.workflowSha}`,
    `--workflow=${expected.workflowRef}`, `--run=${expected.runId}`, `--attempt=${expected.runAttempt}`];
  const writeBundles = async ({ omit, duplicate, foreign, extra } = {}) => {
    for (const worker of workerNames()) {
      if (worker === omit) continue;
      const bundle = join(root, `f0vb-${expected.runId}-${worker}`);
      await mkdir(bundle, { recursive: true });
      await writeFile(join(bundle, 'lease.json'), JSON.stringify(evidence(worker)));
      if (worker === duplicate) {
        await mkdir(join(bundle, 'duplicate'), { recursive: true });
        await writeFile(join(bundle, 'duplicate', 'lease.json'), JSON.stringify(evidence(worker)));
      }
    }
    if (foreign) await mkdir(join(root, foreign));
    if (extra) await writeFile(join(root, extra), '{}');
  };
  const run = async (name, expression) => {
    const output = join(root, `${name}.json`);
    if (expression) {
      assert.throws(() => execFileSync('node', argumentsFor(output), { stdio: 'pipe' }), /Command failed/);
      const aggregate = JSON.parse(await readFile(output, 'utf8'));
      assert.equal(aggregate.status, 'incomplete');
      assert.match(aggregate.failure, expression);
    } else {
      execFileSync('node', argumentsFor(output), { stdio: 'pipe' });
      assert.equal(JSON.parse(await readFile(output, 'utf8')).status, 'ok');
    }
  };
  try {
    await writeBundles();
    await run('valid');
    await rm(root, { recursive: true, force: true });
    await mkdir(root, { recursive: true });
    await writeBundles({ omit: 'worker-3' });
    await run('missing', /missing worker evidence bundle/);
    await rm(root, { recursive: true, force: true });
    await mkdir(root, { recursive: true });
    await writeBundles({ duplicate: 'worker-0' });
    await run('duplicate', /exactly one top-level lease\.json/);
    await rm(root, { recursive: true, force: true });
    await mkdir(root, { recursive: true });
    await writeBundles({ foreign: 'f0vb-45-worker-0' });
    await run('foreign', /foreign or unexpected evidence bundle/);
    await rm(root, { recursive: true, force: true });
    await mkdir(root, { recursive: true });
    await writeBundles({ extra: 'stray.json' });
    await run('extra', /unexpected evidence entry/);
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
