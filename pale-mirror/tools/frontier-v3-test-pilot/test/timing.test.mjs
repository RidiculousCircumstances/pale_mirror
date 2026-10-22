import assert from 'node:assert/strict';
import test from 'node:test';
import { PhaseTiming } from '../src/timing.mjs';

test('failed native boundaries remain measurable instead of masking the first failure', () => {
  const timing = new PhaseTiming();
  timing.begin('client.jvm_boot_and_connect');
  timing.abortOpen({ status: 'failed' });
  const report = timing.finish({ runner: 'test' });
  assert.equal(report.phases.length, 1);
  assert.equal(report.phases[0].name, 'client.jvm_boot_and_connect');
  assert.equal(report.phases[0].incomplete, true);
  assert.equal(report.phases[0].status, 'failed');
});

test('failure snapshots retain completed phases while cleanup is still open', () => {
  const timing = new PhaseTiming();
  timing.begin('cleanup');
  const snapshot = timing.snapshot({ runner: 'test', status: 'failed' });
  assert.deepEqual(snapshot.open, ['cleanup']);
  assert.equal(snapshot.status, 'failed');
  timing.end('cleanup');
  assert.equal(timing.finish({ runner: 'test' }).phases.length, 1);
});
