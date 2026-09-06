import assert from 'node:assert/strict';
import test from 'node:test';
import { chmod, mkdtemp, mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { acquireDevelopmentFixtureConsumer, assertFixturePermitted, createDevelopmentFixtureImage, locateDevelopmentFixtureImage } from '../src/development-fixture-image.mjs';

const HASH = (letter) => letter.repeat(64);
const metadata = Object.freeze({ sourceContentSha256: HASH('0'), artifactSha256: HASH('a'), serverClasspathSha256: HASH('b'), clientClasspathSha256: HASH('c'),
  launchManifestSha256: HASH('d'), snapshotSchema: 126, walEnvelope: 41, rulesetId: 'frontier-v3-test', rulesetSha256: HASH('e'),
  seed: 41, profile: 'world', viewDistance: 10, fixtureDeclarationSha256: HASH('f') });
const durableStop = Object.freeze({ serverRunId: '7a7d1f7a-f73e-4e70-8a5b-c9f4e6bca083', durableSave: true, portClosed: true, lifecycleSha256: HASH('0') });

test('development fixture images bind opaque world bytes to exact durable bootstrap inputs and clone distinct writable consumers', async (context) => {
  const project = await fixture(); context.after(() => cleanup(project));
  const source = join(project, 'pale-mirror-neoforge/build/source-world'); await mkdir(join(source, 'region'), { recursive: true });
  await writeFile(join(source, 'level.dat'), 'opaque-level-v1'); await writeFile(join(source, 'region/r.0.0.mca'), 'opaque-region-v1');
  const image = await createDevelopmentFixtureImage({ project, sourceDirectory: 'pale-mirror-neoforge/build/source-world', imageId: 'world-baseline-v1', metadata, durableStop });
  assert.equal(image.manifest.world.fileCount, 2);
  assert.equal((await stat(join(image.directory, 'world/level.dat'))).mode & 0o222, 0, 'published image must be readonly');
  const located = await locateDevelopmentFixtureImage({ project, imageId: 'world-baseline-v1', metadata });
  assert.equal(located.status, 'READY'); assert.equal(located.image.imageSha256, image.imageSha256);
  const first = await acquireDevelopmentFixtureConsumer({ project, image: located.image, consumerId: 'consumer-one', targetDirectory: 'pale-mirror-neoforge/build/worlds/one', policy: { tier: 'T3' } });
  const second = await acquireDevelopmentFixtureConsumer({ project, image: located.image, consumerId: 'consumer-two', targetDirectory: 'pale-mirror-neoforge/build/worlds/two', policy: { tier: 'T3' } });
  assert.equal(first.status, 'READY'); assert.equal(second.status, 'READY');
  await writeFile(join(project, 'pale-mirror-neoforge/build/worlds/one/level.dat'), 'consumer-one-change');
  assert.equal(await readFile(join(project, 'pale-mirror-neoforge/build/worlds/two/level.dat'), 'utf8'), 'opaque-level-v1');
  assert.equal(await readFile(join(image.directory, 'world/level.dat'), 'utf8'), 'opaque-level-v1');
  await assert.rejects(acquireDevelopmentFixtureConsumer({ project, image: located.image, consumerId: 'consumer-one', targetDirectory: 'build/worlds/three', policy: { tier: 'T3' } }), /consumer already exists/);
});

test('fixture images fail closed on source mutation, metadata/schema drift, tampering, occupied target and forbidden evidence classes', async (context) => {
  const project = await fixture(); context.after(() => cleanup(project));
  const source = join(project, 'build/source-world'); await mkdir(source, { recursive: true }); await writeFile(join(source, 'level.dat'), 'opaque-level');
  const image = await createDevelopmentFixtureImage({ project, sourceDirectory: 'build/source-world', imageId: 'world-baseline-v1', metadata, durableStop });
  assert.deepEqual(await locateDevelopmentFixtureImage({ project, imageId: 'world-baseline-v1', metadata: { ...metadata, snapshotSchema: 127 } }), { status: 'UNAVAILABLE', reason: 'METADATA_DRIFT' });
  await mkdir(join(project, 'build/worlds/existing'), { recursive: true });
  await assert.rejects(acquireDevelopmentFixtureConsumer({ project, image, consumerId: 'occupied', targetDirectory: 'build/worlds/existing', policy: { tier: 'T3' } }), /target already exists/);
  await chmod(join(image.directory, 'world/level.dat'), 0o644);
  await writeFile(join(image.directory, 'world/level.dat'), 'tampered');
  assert.deepEqual(await locateDevelopmentFixtureImage({ project, imageId: 'world-baseline-v1', metadata }), { status: 'UNAVAILABLE', reason: 'WORLD_HASH_MISMATCH' });
  assert.throws(() => assertFixturePermitted({ tier: 'T4' }), /forbidden/);
  assert.throws(() => assertFixturePermitted({ tier: 'T3', freshEvidence: true }), /forbidden/);
  assert.throws(() => assertFixturePermitted({ tier: 'T3', timing: true }), /forbidden/);
});

test('image creation rejects incomplete durable stop and does not publish a cacheable world', async (context) => {
  const project = await fixture(); context.after(() => cleanup(project));
  const source = join(project, 'build/source-world'); await mkdir(source, { recursive: true }); await writeFile(join(source, 'level.dat'), 'opaque-level');
  await assert.rejects(createDevelopmentFixtureImage({ project, sourceDirectory: 'build/source-world', imageId: 'world-baseline-v1', metadata,
    durableStop: { ...durableStop, portClosed: false } }), /durable clean-stop/);
  assert.deepEqual(await locateDevelopmentFixtureImage({ project, imageId: 'world-baseline-v1', metadata }), { status: 'UNAVAILABLE', reason: 'NO_IMAGE' });
});

test('one fixture label may retain historical images, but selection admits only the one exact full metadata identity', async (context) => {
  const project = await fixture(); context.after(() => cleanup(project));
  const source = join(project, 'build/source-world'); await mkdir(source, { recursive: true }); await writeFile(join(source, 'level.dat'), 'opaque-level');
  await createDevelopmentFixtureImage({ project, sourceDirectory: 'build/source-world', imageId: 'world-baseline-v1', metadata, durableStop });
  const next = { ...metadata, sourceContentSha256: HASH('9') };
  await createDevelopmentFixtureImage({ project, sourceDirectory: 'build/source-world', imageId: 'world-baseline-v1', metadata: next, durableStop });
  assert.equal((await locateDevelopmentFixtureImage({ project, imageId: 'world-baseline-v1', metadata })).status, 'READY');
  assert.equal((await locateDevelopmentFixtureImage({ project, imageId: 'world-baseline-v1', metadata: next })).status, 'READY');
});

async function fixture() {
  const project = await mkdtemp(join(tmpdir(), 'pmv3-development-fixture-'));
  await mkdir(join(project, 'build'), { recursive: true }); return project;
}
async function cleanup(path) {
  async function writable(directory) {
    let entries;
    try { entries = await readdir(directory, { withFileTypes: true }); } catch { return; }
    for (const entry of entries) {
      const target = join(directory, entry.name);
      if (entry.isDirectory()) await writable(target);
      else if (entry.isFile()) await chmod(target, 0o644).catch(() => undefined);
    }
    await chmod(directory, 0o755).catch(() => undefined);
  }
  await writable(path); await rm(path, { recursive: true, force: true });
}
