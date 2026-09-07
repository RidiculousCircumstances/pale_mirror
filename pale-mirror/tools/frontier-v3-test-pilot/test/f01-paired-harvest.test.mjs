import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const monorepo = resolve(project, '..');
const merger = resolve(project, 'tools/frontier-v3-test-pilot/src/merge-f01-paired-harvest.mjs');
const workerAggregator = resolve(project, 'tools/frontier-v3-test-pilot/src/aggregate-f01-worker.mjs');
const head = 'a'.repeat(40);
const run = 71;
const assignments = new Map([
  ['worker-0', ['never_loaded', 'arrival_checkpoint_one']],
  ['worker-1', ['arrival_checkpoint_two', 'unload_return']],
  ['worker-2', ['player_intervention', 'graceful_restart']],
  ['worker-3', ['abrupt_restart', 'neutral_observer_differential']]
]);

function report(variants, sourceCommit = head) {
  return { kind: 'frontier-v3-f01-paired-harvest-native-matrix', build: { sourceCommit }, variants: variants.map(variant => ({ variant, runs: [{ status: 'passed' }] })) };
}

async function writeEvidence(root, options = {}) {
  for (const [worker, variants] of assignments) {
    if (worker === options.omit) continue;
    const bundle = join(root, `f01-${run}-${worker}`);
    await mkdir(bundle, { recursive: true });
    await writeFile(join(bundle, 'matrix.json'), JSON.stringify(report(variants, worker === options.stale ? 'b'.repeat(40) : head)));
    if (worker === options.duplicate) await writeFile(join(bundle, 'matrix.copy.json'), JSON.stringify(report(variants)));
  }
  if (options.foreign) await mkdir(join(root, `f01-${run}-worker-x`));
  if (options.extra) await writeFile(join(root, 'stray.json'), '{}');
}

async function invoke(root, name, expectedFailure) {
  const output = join(root, `${name}.json`);
  const args = [merger, `--input=${root}`, `--head=${head}`, `--run=${run}`, `--output=${output}`];
  if (expectedFailure) {
    assert.throws(() => execFileSync('node', args, { stdio: 'pipe' }), /Command failed/);
    const aggregate = JSON.parse(await readFile(output, 'utf8'));
    assert.equal(aggregate.status, 'incomplete');
    assert.match(aggregate.failure, expectedFailure);
    return;
  }
  execFileSync('node', args, { stdio: 'pipe' });
  assert.equal(JSON.parse(await readFile(output, 'utf8')).status, 'ok');
}

test('F0.1 merge accepts only exact worker bundles and retains incomplete diagnostics', async () => {
  const root = await mkdtemp(join(tmpdir(), 'f01-merge-'));
  try {
    await writeEvidence(root);
    await invoke(root, 'valid');
    for (const [name, options, failure] of [
      ['missing', { omit: 'worker-3' }, /exactly the four expected worker evidence bundles/],
      ['foreign', { foreign: true }, /exactly the four expected worker evidence bundles/],
      ['extra', { extra: true }, /exactly the four expected worker evidence bundles/],
      ['stale', { stale: 'worker-1' }, /stale or failed/]
    ]) {
      await rm(root, { recursive: true, force: true });
      await mkdir(root);
      await writeEvidence(root, options);
      await invoke(root, name, failure);
    }
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});

test('F0.1 workflow reserves a disjoint server/RCON pair and Gradle home per native worker', async () => {
  const workflow = await readFile(resolve(monorepo, '.github/workflows/f01-paired-harvest.yml'), 'utf8');
  for (const port of [26100, 26110, 26120, 26130]) assert.match(workflow, new RegExp(`port: ${port}`));
  assert.match(workflow, /GRADLE_USER_HOME="\$RUNNER_TEMP\/f01-\$\{\{ matrix\.worker \}\}-gradle"/);
  assert.match(workflow, /FRONTIER_V3_NATIVE_PROCESS_ROOT="\$RUNNER_TEMP\/f01-\$\{\{ github\.run_id \}\}-\$\{\{ matrix\.worker \}\}"/);
  assert.match(workflow, /status=0/);
  assert.match(workflow, /aggregate-f01-worker\.mjs/);
  assert.match(workflow, /exit "\$status"/);
});

test('F0.1 worker aggregate retains a declared missing or failed variant', async () => {
  const root = await mkdtemp(join(tmpdir(), 'f01-worker-'));
  try {
    await mkdir(join(root, 'never_loaded'), { recursive: true });
    const failed = report(['never_loaded']);
    failed.error = 'native failure retained';
    await writeFile(join(root, 'never_loaded', 'matrix.json'), JSON.stringify(failed));
    const output = join(root, 'matrix.json');
    execFileSync('node', [workerAggregator, `--input=${root}`, '--variants=never_loaded,arrival_checkpoint_one', `--output=${output}`], { stdio: 'pipe' });
    const aggregate = JSON.parse(await readFile(output, 'utf8'));
    assert.equal(aggregate.kind, 'frontier-v3-f01-paired-harvest-native-matrix');
    assert.match(aggregate.error, /native failure retained/);
    assert.match(aggregate.error, /arrival_checkpoint_one: report unavailable/);
    assert.deepEqual(aggregate.variants.map(entry => entry.variant), ['never_loaded', 'arrival_checkpoint_one']);
    assert.equal(aggregate.variants[1].runs[0].status, 'failed');
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
