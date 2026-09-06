import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, rm, unlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { evidenceCacheKey, fingerprintPaths, readReusableEvidence, storeSuccessfulEvidence } from '../src/evidence-cache.mjs';

const digest = (character) => character.repeat(64);
const input = (overrides = {}) => ({ schema: 1, kind: 'frontier-v3-evidence-cache-key', sourceContentSha256: digest('a'), artifactSha256: digest('b'),
  serverClasspathSha256: digest('c'), clientClasspathSha256: digest('d'), jdkSha256: digest('e'), platformSha256: digest('f'), sdkSha256: digest('1'),
  dependencyManifestSha256: digest('2'), verticalContractSha256: digest('3'), scenarioSha256: digest('4'), commandSha256: digest('5'), environmentPolicySha256: digest('6'),
  seed: 41, profile: 'world', viewDistance: 10, tier: 'T3', fixtureImage: null, ...overrides });

test('content-addressed evidence cache reuses only exact complete success identity', async (context) => {
  const root = await mkdtemp(join(tmpdir(), 'pmv3-evidence-cache-')); context.after(() => rm(root, { recursive: true, force: true }));
  const stored = await storeSuccessfulEvidence(root, input(), ['manifests/one.json']);
  assert.equal((await readReusableEvidence(root, input())).key, stored.key);
  await assert.rejects(storeSuccessfulEvidence(root, input(), ['manifests/one.json']), /already exists/);
  assert.equal(await readReusableEvidence(root, input({ scenarioSha256: digest('7') })), undefined);
});

test('content fingerprint includes current bytes and a missing tracked input representation', async (context) => {
  const root = await mkdtemp(join(tmpdir(), 'pmv3-content-')); context.after(() => rm(root, { recursive: true, force: true }));
  await writeFile(join(root, 'tracked.txt'), 'first');
  const first = await fingerprintPaths(root, ['tracked.txt', 'deleted.txt']);
  await writeFile(join(root, 'tracked.txt'), 'second');
  const second = await fingerprintPaths(root, ['tracked.txt', 'deleted.txt']);
  assert.notEqual(first.sha256, second.sha256); assert.equal(second.files.find((file) => file.path === 'deleted.txt').state, 'MISSING');
  await unlink(join(root, 'tracked.txt'));
  assert.equal((await fingerprintPaths(root, ['tracked.txt'])).files[0].state, 'MISSING');
});

test('cache key and entry fail closed on omitted fields, corrupt content and failed status', async (context) => {
  assert.throws(() => evidenceCacheKey({ ...input(), jdkSha256: 'bad' }), /jdkSha256/);
  const root = await mkdtemp(join(tmpdir(), 'pmv3-evidence-corrupt-')); context.after(() => rm(root, { recursive: true, force: true }));
  const stored = await storeSuccessfulEvidence(root, input(), ['proof.json']);
  await writeFile(join(stored.directory, 'extra.json'), '{}');
  await assert.rejects(readReusableEvidence(root, input()), /incomplete or corrupt/);
});
