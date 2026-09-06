import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { validateDependencyManifest, selectVerificationPlan } from '../src/verification-selector.mjs';

const manifest = async () => validateDependencyManifest(JSON.parse(await readFile(new URL('../contracts/dependency-manifest.json', import.meta.url), 'utf8')));

test('verification selector explains declared narrow ownership and marks reusable evidence only as a candidate', async () => {
  const plan = selectVerificationPlan(await manifest(), { changedPaths: ['tools/frontier-v3-test-pilot/scenarios/disposable-lite-smoke.json'] });
  assert.deepEqual(plan.tiers, ['T0', 'T1', 'T3']);
  assert.equal(plan.selections[0].owner, 'pilot-scenarios');
  assert.equal(plan.cache, 'CANDIDATE_ONLY');
});

test('unknown or multiply-owned paths fail safe by widening to every tier', async () => {
  const checked = await manifest();
  const unknown = selectVerificationPlan(checked, { changedPaths: ['README.md'] });
  assert.equal(unknown.conservative, true); assert.deepEqual(unknown.tiers, ['T0', 'T1', 'T2', 'T3']);
  assert.deepEqual(unknown.requiredTiers, ['T0', 'T1', 'T2', 'T3', 'T4']);
  const overlap = structuredClone(checked); overlap.rules[0].prefixes.push('tools/frontier-v3-test-pilot/');
  const multiple = selectVerificationPlan(overlap, { changedPaths: ['tools/frontier-v3-test-pilot/scenarios/disposable-lite-smoke.json'] });
  assert.equal(multiple.selections[0].reason, 'MULTIPLE_OWNERS');
});

test('generic SDK, codec, HOT executor, restart runner and package changes select every dependent tier', async () => {
  const checked = await manifest();
  for (const path of [
    'pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/process/FrontierProcessSceneSdk.java',
    'pale-mirror-frontier/src/main/java/io/farfrontier/palemirror/frontier/v3/persistence/FrontierWorldPayloadCodecs.java',
    'pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3SceneExecutor.java',
    'tools/frontier-v3-test-pilot/src/run-f0va-persistent-matrix.mjs',
    'pale-mirror-neoforge/build.gradle'
  ]) {
    const iterative = selectVerificationPlan(checked, { changedPaths: [path] });
    assert.deepEqual(iterative.requiredTiers, ['T0', 'T1', 'T2', 'T3', 'T4']);
    assert.deepEqual(iterative.tiers, ['T0', 'T1', 'T2', 'T3']);
    assert.deepEqual(iterative.deferredFinalTiers, ['T4']);
    assert.deepEqual(selectVerificationPlan(checked, { changedPaths: [path], freshEvidence: true }).tiers, ['T0', 'T1', 'T2', 'T3', 'T4']);
  }
});

test('fresh evidence mechanically bypasses cache and development fixture images', async () => {
  const plan = selectVerificationPlan(await manifest(), { changedPaths: ['tools/frontier-v3-test-pilot/scenarios/disposable-lite-smoke.json'], freshEvidence: true });
  assert.deepEqual(plan.tiers, ['T0', 'T1', 'T2', 'T3', 'T4']);
  assert.equal(plan.cache, 'BYPASS_REQUIRED'); assert.equal(plan.fixtureImage, 'FORBIDDEN');
});

test('an iterative plan cannot omit an affected lower tier or claim that its deferred final gate is irrelevant', async () => {
  const checked = await manifest();
  const plan = selectVerificationPlan(checked, { changedPaths: ['tools/frontier-v3-test-pilot/src/lifecycle-barrier.mjs'] });
  assert.deepEqual(plan.tiers, ['T0', 'T1', 'T2', 'T3']);
  assert.deepEqual(plan.requiredTiers, ['T0', 'T1', 'T2', 'T3', 'T4']);
  assert.throws(() => selectVerificationPlan(checked, {
    changedPaths: ['tools/frontier-v3-test-pilot/src/lifecycle-barrier.mjs'], finalEvidence: true
  }), /requires fresh-evidence/);
});

test('T1 ownership rejects duplicate selectors and unknown module policy rather than widening a narrow command silently', async () => {
  const checked = await manifest();
  const duplicateNode = structuredClone(checked);
  duplicateNode.rules[0].t1.nodeTests.push(duplicateNode.rules[0].t1.nodeTests[0]);
  assert.throws(() => validateDependencyManifest(duplicateNode), /Node selector is duplicate/);
  const unknownModule = structuredClone(checked);
  unknownModule.rules[0].t1.allModules.extra = false;
  assert.throws(() => validateDependencyManifest(unknownModule), /unknown or missing module/);
});
