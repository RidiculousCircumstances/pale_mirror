import assert from 'node:assert/strict';
import test from 'node:test';
import { createCrashController } from '../src/crash-controller.mjs';

const config = {
  runId: '00000000-0000-0000-0000-000000000001',
  boundary: 'hot_checkpoint_durable_before_drain_release',
  owner: 'job:harvest-1', payloadType: 'frontier.resource_site_harvest_hot_traversal_advanced',
  expectedRevision: 17, expectedAuthorityEpoch: 3, serverPid: 12345
};

test('crash controller can target only its exact nonce, owner, revision and epoch once', () => {
  const controller = createCrashController(config); const calls = [];
  assert.throws(() => controller.fire({ ...config, revision: 16, authorityEpoch: 3 }, (...args) => calls.push(args)), /does not match/);
  assert.throws(() => controller.fire({ ...config, payloadType: 'frontier.other', revision: 17, authorityEpoch: 3 }, (...args) => calls.push(args)), /does not match/);
  const fired = controller.fire({ runId: config.runId, boundary: config.boundary, owner: config.owner, payloadType: config.payloadType,
    revision: 17, authorityEpoch: 3 },
    (...args) => calls.push(args));
  assert.deepEqual(fired, { runId: config.runId, boundary: config.boundary, owner: config.owner,
    payloadType: config.payloadType, serverPid: 12345, revision: 17, authorityEpoch: 3 });
  assert.deepEqual(calls, [[12345, 'SIGKILL']]);
  assert.throws(() => controller.fire({ runId: config.runId, boundary: config.boundary, owner: config.owner, payloadType: config.payloadType,
    revision: 17, authorityEpoch: 3 }), /exactly once/);
});

test('crash controller rejects broad or malformed process targeting before execution', () => {
  assert.throws(() => createCrashController({ ...config, serverPid: 1 }), /exact server PID/);
  assert.throws(() => createCrashController({ ...config, boundary: 'sleep_then_kill' }), /boundary/);
  assert.throws(() => createCrashController({ ...config, runId: 'all-servers' }), /runId/);
});

test('crash controller admits each explicit semantic crash window and no synthetic timing window', () => {
  for (const boundary of [
    'lease_recorded_before_physical_materialization',
    'physical_effect_visible_before_typed_observation',
    'typed_observation_durable_before_next_process_checkpoint',
    'hot_checkpoint_durable_before_drain_release',
    'release_durable_before_cold_resumption'
  ]) {
    assert.doesNotThrow(() => createCrashController({ ...config, boundary }));
  }
  assert.throws(() => createCrashController({ ...config, boundary: 'wal_append_durable_before_engine_install' }), /boundary/);
});

test('exact revision remains sufficient when a durable boundary has no authority epoch', () => {
  const { expectedAuthorityEpoch, ...revisionOnly } = config;
  const controller = createCrashController(revisionOnly); const calls = [];
  controller.fire({ runId: config.runId, boundary: config.boundary, owner: config.owner, payloadType: config.payloadType,
    revision: 17 }, (...args) => calls.push(args));
  assert.deepEqual(calls, [[12345, 'SIGKILL']]);
});
