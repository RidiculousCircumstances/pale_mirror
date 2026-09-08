import assert from 'node:assert/strict';
import test from 'node:test';
import { chmod, mkdtemp, readFile, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { copyConsumerView, requireRuntimeSourceIdentity } from '../src/f0vc-prepared-runtime.mjs';

test('F0.VC consumer views are private reflink-or-copy files: a consumer mutation cannot reach the store or a sibling', async (context) => {
  const root = await mkdtemp(join(tmpdir(), 'f0vc-private-view-')); context.after(async () => { await (await import('node:fs/promises')).rm(root, { recursive: true, force: true }); });
  const producer = join(root, 'producer.jar'); const first = join(root, 'first.jar'); const second = join(root, 'second.jar');
  await writeFile(producer, 'immutable-store');
  await copyConsumerView(producer, first, false); await copyConsumerView(producer, second, false);
  assert.notEqual((await stat(producer)).ino, (await stat(first)).ino, 'consumer must not hard-link the producer');
  assert.notEqual((await stat(first)).ino, (await stat(second)).ino, 'consumer views must not share an inode');
  await chmod(first, 0o644); await writeFile(first, 'first-consumer-mutation');
  assert.equal(await readFile(producer, 'utf8'), 'immutable-store');
  assert.equal(await readFile(second, 'utf8'), 'immutable-store');
});

test('F0.VC rejects a prepared runtime when selected source identity is invalidated before launch', async () => {
  const root = new URL('../../../', import.meta.url).pathname;
  const actual = await requireRuntimeSourceIdentity(await (await import('../src/prepared-build.mjs')).fingerprintPreparedSource(root), root);
  await assert.rejects(requireRuntimeSourceIdentity({ ...actual, sha256: '0'.repeat(64) }, root), /source\/test\/workflow identity drifted/);
});
