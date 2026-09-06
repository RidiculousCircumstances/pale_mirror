import { createHash } from 'node:crypto';
import { CRASH_BOUNDARIES } from './crash-controller.mjs';

export const PERSISTENT_EXPECTED_CRASH_SCHEMA = 1;
const KIND = 'frontier-v3-persistent-expected-crash-arm';
const RELEASE_KIND = 'frontier-v3-persistent-expected-crash-release';
const SUCCESSOR_KEYS = Object.freeze(['epoch', 'segment', 'scenarioSha256', 'worldKey', 'serverRunId', 'serverPid', 'ready']);

/** A pure, immutable arm: authority remains with the existing controller and session owners. */
export function createPersistentExpectedCrashArm({ identity, epoch, descriptor, serverRunId, serverPid, clientPid, port, resolvedRevision, authorityEpoch = undefined }) {
  if (!identity || !sha(identity.buildIdentitySha256) || !token(identity.workerId) || !uuid(identity.runId) || !uuid(identity.nonce) || !uuid(identity.sessionId)
      || !Number.isInteger(epoch) || epoch < 0 || !token(serverRunId) || !Number.isInteger(serverPid) || serverPid <= 1 || !Number.isInteger(clientPid) || clientPid <= 1
      || !Number.isInteger(port) || port < 1024 || port > 65535
      || !Number.isSafeInteger(resolvedRevision) || resolvedRevision < 0 || !crashDescriptor(descriptor)) {
    throw new Error('persistent expected crash arm is malformed');
  }
  if (authorityEpoch !== undefined && (!Number.isSafeInteger(authorityEpoch) || authorityEpoch < 0)) throw new Error('persistent expected crash authority epoch is malformed');
  if (descriptor.resolvedRevision !== resolvedRevision || descriptor.expectedAuthorityEpoch !== authorityEpoch) throw new Error('persistent expected crash declaration does not match resolved observation');
  const core = { schema: PERSISTENT_EXPECTED_CRASH_SCHEMA, kind: KIND,
    identity: { buildIdentitySha256: identity.buildIdentitySha256, workerId: identity.workerId, runId: identity.runId, nonce: identity.nonce, sessionId: identity.sessionId },
    epoch, descriptor: structuredClone(descriptor), serverRunId, serverPid, clientPid, port, resolvedRevision, ...(authorityEpoch === undefined ? {} : { authorityEpoch }) };
  return freeze({ ...core, contentSha256: hash(core) });
}

/** Reconstructive validation rejects a self-consistent replacement hash or identity. */
export function validatePersistentExpectedCrashArm(value, expected) {
  if (!value || value.schema !== PERSISTENT_EXPECTED_CRASH_SCHEMA || value.kind !== KIND || !sha(value.contentSha256)) throw new Error('persistent expected crash arm is malformed');
  const rebuilt = createPersistentExpectedCrashArm({ ...value, identity: expected.identity, epoch: expected.epoch, descriptor: expected.descriptor,
    serverRunId: expected.serverRunId, serverPid: expected.serverPid, clientPid: expected.clientPid, port: expected.port, resolvedRevision: expected.resolvedRevision, authorityEpoch: expected.authorityEpoch });
  if (JSON.stringify(value) !== JSON.stringify(rebuilt)) throw new Error('persistent expected crash arm is foreign or stale');
  return rebuilt;
}

/** All independent observations are required; their filesystem arrival order is intentionally irrelevant. */
export function validatePersistentExpectedCrashProof(arm, { fired, ownedExit, clientLoss, portClosed }) {
  const checked = validatePersistentExpectedCrashArm(arm, arm);
  if (!fired || fired.runId !== checked.serverRunId || fired.boundary !== checked.descriptor.boundary || fired.owner !== checked.descriptor.owner
      || fired.payloadType !== checked.descriptor.payloadType || fired.serverPid !== checked.serverPid || fired.revision !== checked.resolvedRevision
      || (checked.authorityEpoch !== undefined && fired.authorityEpoch !== checked.authorityEpoch)
      || !ownedExit || ownedExit.serverPid !== checked.serverPid || ownedExit.serverRunId !== checked.serverRunId || ownedExit.exited !== true
      || !clientLoss || clientLoss.clientPid !== checked.clientPid || clientLoss.epoch !== checked.epoch || clientLoss.segment !== checked.descriptor.segment
      || clientLoss.runId !== checked.identity.runId || clientLoss.nonce !== checked.identity.nonce || clientLoss.sessionId !== checked.identity.sessionId
      || clientLoss.workerId !== checked.identity.workerId || clientLoss.buildIdentitySha256 !== checked.identity.buildIdentitySha256
      || !portClosed || portClosed.serverPid !== checked.serverPid || portClosed.serverRunId !== checked.serverRunId || portClosed.port !== checked.port || portClosed.closed !== true) {
    throw new Error('persistent expected crash proof is incomplete or foreign');
  }
  return freeze({ arm: checked, fired: freeze(structuredClone(fired)), ownedExit: freeze(structuredClone(ownedExit)), clientLoss: freeze(structuredClone(clientLoss)), portClosed: freeze(structuredClone(portClosed)) });
}

/**
 * A release is deliberately downstream of all four independent observations.  It is not a
 * resume token: the next epoch/world/server readiness are part of the immutable object which
 * the client consumes after the lost connection has been independently verified.
 */
export function createPersistentExpectedCrashRelease({ arm, proof, successor }) {
  const checked = validatePersistentExpectedCrashArm(arm, arm);
  const verified = validatePersistentExpectedCrashProof(checked, proof);
  if (!exactSuccessor(successor) || !Number.isInteger(successor.epoch) || successor.epoch !== checked.epoch + 1
      || !token(successor.segment) || !sha(successor.scenarioSha256) || successor.worldKey !== checked.descriptor.worldKey
      || !token(successor.serverRunId) || !Number.isInteger(successor.serverPid) || successor.serverPid <= 1
      || successor.ready !== true) throw new Error('persistent expected crash successor readiness is malformed or stale');
  const core = { schema: PERSISTENT_EXPECTED_CRASH_SCHEMA, kind: RELEASE_KIND, armSha256: checked.contentSha256,
    identity: checked.identity, predecessorEpoch: checked.epoch, successor: canonicalSuccessor(successor), proof: {
      fired: verified.fired, ownedExit: verified.ownedExit, clientLoss: verified.clientLoss, portClosed: verified.portClosed
    } };
  return freeze({ ...core, contentSha256: hash(core) });
}

export function validatePersistentExpectedCrashRelease(value, expected) {
  if (!value || value.schema !== PERSISTENT_EXPECTED_CRASH_SCHEMA || value.kind !== RELEASE_KIND || !sha(value.contentSha256)) {
    throw new Error('persistent expected crash release is malformed');
  }
  const rebuilt = createPersistentExpectedCrashRelease({ arm: expected.arm, proof: expected.proof, successor: expected.successor });
  if (JSON.stringify(value) !== JSON.stringify(rebuilt)) throw new Error('persistent expected crash release is foreign or stale');
  return rebuilt;
}

function crashDescriptor(value) { return value && value.completion === 'expected_crash' && token(value.segment) && token(value.scenarioId) && sha(value.scenarioSha256)
  && token(value.worldKey) && token(value.laneId) && CRASH_BOUNDARIES.has(value.boundary) && token(value.owner) && token(value.payloadType)
  && ((Number.isSafeInteger(value.expectedRevision) && value.expectedRevision >= 0) || value.expectedRevision === 'observed_at_boundary')
  && (value.expectedRevision === 'observed_at_boundary' || value.expectedRevision === value.resolvedRevision)
  && (value.expectedAuthorityEpoch === undefined || (Number.isSafeInteger(value.expectedAuthorityEpoch) && value.expectedAuthorityEpoch >= 0)); }
function exactSuccessor(value) { return value && typeof value === 'object' && !Array.isArray(value)
  && Object.keys(value).length === SUCCESSOR_KEYS.length && SUCCESSOR_KEYS.every((key) => Object.hasOwn(value, key)); }
function canonicalSuccessor(value) { return { epoch: value.epoch, segment: value.segment, scenarioSha256: value.scenarioSha256,
  worldKey: value.worldKey, serverRunId: value.serverRunId, serverPid: value.serverPid, ready: value.ready }; }
function token(value) { return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(value); }
function sha(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function uuid(value) { return typeof value === 'string' && /^[0-9a-f-]{36}$/.test(value); }
function hash(value) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }
function freeze(value) { if (!value || typeof value !== 'object' || Object.isFrozen(value)) return value; for (const child of Object.values(value)) freeze(child); return Object.freeze(value); }
