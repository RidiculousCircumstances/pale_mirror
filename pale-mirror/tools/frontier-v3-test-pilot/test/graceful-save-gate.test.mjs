import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { withGracefulSaveGate } from '../src/graceful-save-gate.mjs';

test('graceful save gate serializes independent durable stops and releases after failure', async () => {
  const root = await mkdtemp(join(tmpdir(), 'pm-graceful-save-gate-'));
  const active = [];
  try {
    const run = (id, fail = false) => withGracefulSaveGate({ directory: root, owner: { id } }, async receipt => {
      active.push(id); assert.deepEqual(active, [id]);
      await new Promise(resolveDelay => setTimeout(resolveDelay, 30));
      active.pop();
      if (fail) throw new Error('expected save failure');
      return receipt;
    });
    const [left, right] = await Promise.all([run('left'), run('right')]);
    assert.equal(left.mode, 'serialized'); assert.equal(right.mode, 'serialized');
    await assert.rejects(run('failed', true), /expected save failure/);
    assert.equal((await run('after-failure')).owner.id, 'after-failure');
  } finally {
    await rm(root, { recursive: true, force: true });
  }
});
