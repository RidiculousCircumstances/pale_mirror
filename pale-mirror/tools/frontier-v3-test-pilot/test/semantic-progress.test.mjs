import assert from 'node:assert/strict';
import test from 'node:test';
import { SemanticProgressMonitor } from '../src/semantic-progress.mjs';
const value = (instant, overrides = {}) => ({ id: 'shipment:one', instant, progressObligation: {
  schema: 1, rule: 'frontier.shipment.progress.v1', subject: 'shipment:one', worker: 'resident:one',
  generation: 1, revision: 1, fingerprint: 'load', budgetTicks: 100, disposition: 'ELIGIBLE', next: 'LOAD', ...overrides } });
test('owner-declared progress uses canonical time and rejects silent eligible non-progress', () => {
  const monitor = new SemanticProgressMonitor(); monitor.observe(value(10)); monitor.observe(value(110));
  assert.throws(() => monitor.observe(value(111)), /deadline violated/);
  assert.equal(monitor.receipts()[0].verdict, 'PENDING');
});
test('recovery/holds never become terminal proof and exact worker survives a newer execution generation', () => {
  const monitor = new SemanticProgressMonitor(); monitor.observe(value(10));
  monitor.observe(value(200, { disposition: 'HIGHER_PRIORITY_ACTIVITY' }));
  monitor.observe(value(210, { disposition: 'RECOVERY_UNKNOWN' }));
  assert.equal(monitor.receipts()[0].verdict, 'INCONCLUSIVE');
  monitor.observe(value(220, { generation: 2, revision: 2, fingerprint: 'unload', next: 'UNLOAD' }));
  assert.throws(() => monitor.observe(value(221, { worker: 'resident:other' })), /regressed/);
  monitor.observe(value(230, { generation: 2, revision: 3, fingerprint: 'closed', next: 'CLOSED', disposition: 'TERMINAL' }));
  assert.equal(monitor.receipts()[0].verdict, 'SATISFIED');
});
