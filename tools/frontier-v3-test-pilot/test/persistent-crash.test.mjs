import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash } from 'node:crypto';
import { createPersistentExpectedCrashArm, createPersistentExpectedCrashRelease, validatePersistentExpectedCrashArm, validatePersistentExpectedCrashProof, validatePersistentExpectedCrashRelease } from '../src/persistent-crash.mjs';

const identity = { buildIdentitySha256: 'a'.repeat(64), workerId: 'worker-0', runId: '00000000-0000-0000-0000-000000000001', nonce: '00000000-0000-0000-0000-000000000002', sessionId: '00000000-0000-0000-0000-000000000003' };
const descriptor = { completion: 'expected_crash', segment: 'crash-before', scenarioId: 'crash_scenario', scenarioSha256: 'b'.repeat(64), worldKey: 'world-a', laneId: 'lane-a', boundary: 'hot_checkpoint_durable_before_drain_release', owner: 'job:harvest-1', payloadType: 'frontier.resource_site_harvest_hot_traversal_advanced', expectedRevision: 'observed_at_boundary', resolvedRevision: 17, expectedAuthorityEpoch: 3 };
const input = { identity, epoch: 2, descriptor, serverRunId: 'server-2', serverPid: 2222, clientPid: 3333, port: 25575, resolvedRevision: 17, authorityEpoch: 3 };
const rehash = (value) => {
  delete value.contentSha256;
  value.contentSha256 = createHash('sha256').update(JSON.stringify(value)).digest('hex');
  return value;
};

test('expected crash arm is immutable and proof needs every independent causal observation', () => {
  const arm = createPersistentExpectedCrashArm(input);
  assert.equal(validatePersistentExpectedCrashArm(JSON.parse(JSON.stringify(arm)), input).contentSha256, arm.contentSha256);
  const proof = { fired: { runId: 'server-2', boundary: descriptor.boundary, owner: descriptor.owner, payloadType: descriptor.payloadType, serverPid: 2222, revision: 17, authorityEpoch: 3 },
    ownedExit: { serverPid: 2222, serverRunId: 'server-2', exited: true }, clientLoss: { clientPid: 3333, epoch: 2, segment: 'crash-before', runId: identity.runId, nonce: identity.nonce, sessionId: identity.sessionId, workerId: identity.workerId, buildIdentitySha256: identity.buildIdentitySha256 }, portClosed: { serverPid: 2222, serverRunId: 'server-2', port: 25575, closed: true } };
  assert.equal(validatePersistentExpectedCrashProof(arm, proof).arm.contentSha256, arm.contentSha256);
  for (const key of Object.keys(proof)) { const missing = { ...proof }; delete missing[key]; assert.throws(() => validatePersistentExpectedCrashProof(arm, missing), /incomplete|foreign/); }
  const tampered = structuredClone(arm); tampered.serverPid = 3333;
  assert.throws(() => validatePersistentExpectedCrashArm(tampered, input), /foreign|stale/);
  for (const mutate of [
    (value) => { value.clientPid = 3334; },
    (value) => { value.serverRunId = 'foreign-server'; },
    (value) => { value.resolvedRevision = 18; },
    (value) => { value.authorityEpoch = 4; },
    (value) => { value.descriptor.boundary = 'not_a_crash_boundary'; }
  ]) {
    const rehashed = rehash(structuredClone(arm)); mutate(rehashed);
    rehash(rehashed); assert.throws(() => validatePersistentExpectedCrashArm(rehashed, input), /foreign|stale/);
  }
  const early = structuredClone(proof); early.clientLoss.clientPid = 3334;
  assert.throws(() => validatePersistentExpectedCrashProof(arm, early), /incomplete|foreign/);
  assert.throws(() => createPersistentExpectedCrashArm({ ...input, descriptor: { ...descriptor, expectedRevision: 16 } }), /malformed/);
  assert.throws(() => createPersistentExpectedCrashArm({ ...input, resolvedRevision: 18 }), /declaration/);
  assert.throws(() => createPersistentExpectedCrashArm({ ...input, authorityEpoch: 4 }), /declaration/);
  assert.throws(() => createPersistentExpectedCrashArm({ ...input, authorityEpoch: undefined }), /declaration/);
  assert.throws(() => createPersistentExpectedCrashArm({ ...input, descriptor: { ...descriptor, boundary: 'not_a_crash_boundary' } }), /malformed/);
  assert.throws(() => createPersistentExpectedCrashArm({ ...input, descriptor: { ...descriptor, completion: 'terminal' } }), /malformed/);
});

test('verified crash release binds one exact successor and cannot re-arm it', () => {
  const arm = createPersistentExpectedCrashArm(input);
  const proof = { fired: { runId: 'server-2', boundary: descriptor.boundary, owner: descriptor.owner, payloadType: descriptor.payloadType, serverPid: 2222, revision: 17, authorityEpoch: 3 },
    ownedExit: { serverPid: 2222, serverRunId: 'server-2', exited: true }, clientLoss: { clientPid: 3333, epoch: 2, segment: 'crash-before', runId: identity.runId, nonce: identity.nonce, sessionId: identity.sessionId, workerId: identity.workerId, buildIdentitySha256: identity.buildIdentitySha256 }, portClosed: { serverPid: 2222, serverRunId: 'server-2', port: 25575, closed: true } };
  const successor = { epoch: 3, segment: 'crash-after', scenarioSha256: 'c'.repeat(64), worldKey: 'world-a', serverRunId: 'server-3', serverPid: 2223, ready: true };
  const release = createPersistentExpectedCrashRelease({ arm, proof, successor });
  assert.equal(validatePersistentExpectedCrashRelease(JSON.parse(JSON.stringify(release)), { arm, proof, successor }).successor.serverRunId, 'server-3');
  const producerOrderedSuccessor = { serverRunId: 'server-3', serverPid: 2223, worldKey: 'world-a', segment: 'crash-after', scenarioSha256: 'c'.repeat(64), epoch: 3, ready: true };
  const producerRelease = createPersistentExpectedCrashRelease({ arm, proof, successor: producerOrderedSuccessor });
  assert.equal(producerRelease.contentSha256, release.contentSha256);
  assert.equal(JSON.stringify(producerRelease), JSON.stringify(release));
  assert.equal(validatePersistentExpectedCrashRelease(JSON.parse(JSON.stringify(producerRelease)), { arm, proof, successor }).successor.serverRunId, 'server-3');
  for (const key of Object.keys(proof)) { const incomplete = { ...proof }; delete incomplete[key]; assert.throws(() => createPersistentExpectedCrashRelease({ arm, proof: incomplete, successor }), /incomplete|foreign/); }
  assert.throws(() => createPersistentExpectedCrashRelease({ arm, proof, successor: { ...successor, epoch: 4 } }), /successor/);
  assert.throws(() => createPersistentExpectedCrashRelease({ arm, proof, successor: { ...successor, ready: false } }), /successor/);
  assert.throws(() => createPersistentExpectedCrashRelease({ arm, proof, successor: { ...successor, unexpected: 'field' } }), /successor/);
  const missingSuccessorField = { ...successor }; delete missingSuccessorField.ready;
  assert.throws(() => createPersistentExpectedCrashRelease({ arm, proof, successor: missingSuccessorField }), /successor/);
  for (const mutate of [
    (value) => { value.armSha256 = 'f'.repeat(64); },
    (value) => { value.proof.fired.revision = 18; },
    (value) => { value.proof.ownedExit.exited = false; },
    (value) => { value.proof.clientLoss.workerId = 'foreign-worker'; },
    (value) => { value.proof.portClosed.closed = false; },
    (value) => { value.successor.serverRunId = 'foreign-server'; },
    (value) => { value.successor.epoch = 4; },
    (value) => { value.successor.worldKey = 'foreign-world'; },
    (value) => { value.successor.unexpected = 'field'; }
  ]) {
    const rehashed = rehash(structuredClone(release)); mutate(rehashed);
    rehash(rehashed); assert.throws(() => validatePersistentExpectedCrashRelease(rehashed, { arm, proof, successor }), /foreign|stale/);
  }
});
