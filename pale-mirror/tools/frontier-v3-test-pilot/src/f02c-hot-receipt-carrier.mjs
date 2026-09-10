import { createHash } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import { validateLifecycleBarrierRecords } from './lifecycle-barrier.mjs';

const SCENE = 'assault:development-settlement-assault';
const SLOTS = Object.freeze([[2, 'preRestart', 'HOT'], [3, 'recovered', 'HOT'], [5, 'released', 'CLOSED']]);

/** The completed persistent-run manifest, rather than declaration literals, is the HOT receipt authority. */
export function assertF02cHotReceiptCarrier({ declaration, manifest }) {
  const slots = assertF02cHotReceiptDeclaration(declaration);
  assertPersistentTopology(manifest, declaration);
  const preRestart = assertionObservation(manifest, slots.preRestart, 'pre-restart');
  const recovered = assertionObservation(manifest, slots.recovered, 'recovered');
  const released = assertionObservation(manifest, slots.released, 'released');
  assertStrike(preRestart, 'HOT', 'pre-restart'); assertStrike(recovered, 'HOT', 'recovered'); assertStrike(released, 'CLOSED', 'released');
  if (!sameReceipt(preRestart, recovered) || !sameReceipt(preRestart, released)
      || preRestart.strikeEpoch !== 0 || preRestart.nextStrikeEpoch !== 1
      || recovered.strikeEpoch !== 0 || recovered.nextStrikeEpoch !== 1
      || released.strikeEpoch !== 0 || released.nextStrikeEpoch !== 1
      || released.assaultStatus !== 'COLD_COMBAT' || released.coldContinuationAvailable !== true) {
    throw new Error('F0.2C HOT carrier replayed, replaced, or misassociated its released receipt');
  }
  return Object.freeze({ scene: SCENE, intent: preRestart.strikeIntent, receipt: preRestart.strikeReceipt,
    cause: preRestart.strikeCause, lifecycle: Object.freeze({ preRestart: slots.preRestart.after, recovered: slots.recovered.after, released: slots.released.after }) });
}

/** Validates the declarative topology before a persistent native runner may be started. */
export function assertF02cHotReceiptDeclaration(value) {
  if (!value || value.id !== 'disposable_settlement_assault_restart' || value.server?.profile !== 'settlement-assault'
      || value.restart?.mode !== 'graceful' || value.restart.afterAction !== 2 || !Array.isArray(value.actions)
      || value.actions.length !== 6 || !Array.isArray(value.assertions)) {
    throw new Error('F0.2C HOT carrier declaration has an invalid lifecycle topology');
  }
  const initial = value.setup?.find(entry => entry.type === 'assert_fixture')?.checks?.find(entry => entry.view === 'scene' && entry.id === SCENE);
  if (initial?.expect?.status !== 'not_found') throw new Error('F0.2C HOT carrier must begin before lease or strike manufacture');
  const selected = {};
  for (const [after, name, leaseStatus] of SLOTS) {
    const matches = value.assertions.filter(entry => entry?.after === after && entry.view === 'scene' && entry.id === SCENE);
    if (matches.length !== 1 || !sceneExpectation(matches[0].expect, leaseStatus)) {
      throw new Error(`F0.2C HOT carrier lacks one declaration-bound ${name} slot`);
    }
    selected[name] = matches[0];
  }
  if (value.assertions.length !== 4 || !value.assertions.some(entry => entry?.after === 6 && entry.view === 'trace'
      && entry.id === 'assault:assault:development-settlement-assault' && entry.expect?.status === 'ok')) {
    throw new Error('F0.2C HOT carrier declaration lacks its terminal retained trace');
  }
  for (const [index, slot] of [[1, selected.preRestart], [2, selected.recovered], [4, selected.released]]) {
    const action = value.actions[index];
    if (action?.type !== 'wait_until_diagnostic' || action.view !== slot.view || action.id !== slot.id || !isDeepStrictEqual(action.expect, slot.expect)) {
      throw new Error('F0.2C HOT carrier action and declaration slots are misordered');
    }
  }
  return Object.freeze(selected);
}

/** Binds final-manifest bytes to the actual declaration, persistent runner and lifecycle identities. */
export function assertF02cHotReceiptIdentity({ declaration, declarationSha256, manifest, outerAttempt }) {
  if (!uuid(outerAttempt) || !sha256(declarationSha256) || manifest?.schema !== 2 || manifest.status !== 'ok'
      || manifest.scenarioId !== declaration.id || manifest.scenarioSha256 !== declarationSha256
      || manifest.scenarioDeclarationSha256 !== declarationSha256 || !uuid(manifest.runId) || manifest.runId === outerAttempt
      || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length
      || !Array.isArray(manifest.clientSegments) || manifest.clientSegments.length !== 1) {
    throw new Error('F0.2C HOT carrier has a foreign declaration, final manifest, or runner identity');
  }
  for (const [index, entry] of manifest.actions.entries()) {
    if (!isDeepStrictEqual(entry?.action, declaration.actions[index]) || entry.correlation !== `scenario:${manifest.runId}:${index + 1}`) {
      throw new Error('F0.2C HOT carrier has stale or reordered persistent-run actions');
    }
  }
  const client = manifest.clientSegments[0];
  if (client?.segment !== 'persistent_restart' || client.runId !== manifest.runId || client.reusedJvm !== true
      || manifest.recovery?.mode !== 'graceful' || manifest.recovery.splitAfterAction !== 2
      || manifest.recovery?.clientSession?.runId !== outerAttempt || manifest.recovery.clientSession.reusedJvm !== true) {
    throw new Error('F0.2C HOT carrier does not retain one persistent restart runner');
  }
  const records = validateLifecycleBarrierRecords(manifest.lifecycle, {
    schema: 1, buildIdentitySha256: sha256Json(manifest.build), workerId: manifest.lifecycle?.[0]?.identity?.workerId,
    runId: outerAttempt, scenarioId: manifest.scenarioId, segmentId: 'native-scenario',
    nonce: manifest.lifecycle?.[0]?.identity?.nonce, sessionId: manifest.lifecycle?.[0]?.identity?.sessionId
  });
  if (records.length === 0 || records.some(record => record.identity.runId !== outerAttempt)) {
    throw new Error('F0.2C HOT carrier lifecycle does not retain its precommitted attempt');
  }
  return Object.freeze({ outerAttempt, clientRunId: manifest.runId,
    lifecycle: Object.freeze({ runId: records[0].identity.runId, sessionId: records[0].identity.sessionId, nonce: records[0].identity.nonce }) });
}

function assertPersistentTopology(manifest, declaration) {
  if (!Array.isArray(manifest?.diagnostics) || !Array.isArray(manifest.lifecycle)
      || !manifest.actions.every((entry, index) => isDeepStrictEqual(entry?.action, declaration.actions[index]))) {
    throw new Error('F0.2C HOT carrier final manifest omits its persistent-run topology');
  }
  const barriers = manifest.lifecycle.map(entry => entry?.barrier);
  const expected = ['server_run_ready', 'scenario_segment_complete', 'client_normally_disconnected', 'durable_server_save',
    'game_port_closed', 'recovery_server_ready', 'same_client_reconnected_state_cleared', 'terminal_assertion_complete'];
  let previous = -1;
  for (const barrier of expected) {
    const current = barriers.indexOf(barrier);
    if (current < 0 || current <= previous) throw new Error('F0.2C HOT carrier lifecycle is incomplete or misordered');
    previous = current;
  }
}

function assertionObservation(manifest, declaration, phase) {
  const records = manifest.diagnostics.filter(entry => entry?.assertion?.after === declaration.after
    && entry.assertion?.view === declaration.view && entry.assertion?.id === declaration.id);
  if (records.length !== 1 || !isDeepStrictEqual(records[0].assertion, declaration)
      || records[0]?.observed?.actionStep !== declaration.after || !records[0]?.observed?.value
      || !Object.entries(declaration.expect).every(([key, value]) => isDeepStrictEqual(records[0].observed.value[key], value))) {
    throw new Error(`F0.2C HOT carrier lacks one exact assertion-bound ${phase} observation`);
  }
  return records[0].observed.value;
}

function sceneExpectation(value, leaseStatus) {
  return value?.status === 'ok' && value.sceneKind === 'SETTLEMENT_ASSAULT' && value.leaseStatus === leaseStatus
    && value.strikeStatus === 'CONFIRMED' && value.strikeReceiptExact === true && value.strikeHealthChanged === true
    && typeof value.strikeCause === 'string' && typeof value.strikeAttacker === 'string' && typeof value.strikeTarget === 'string'
    && typeof value.strikeIntent === 'string' && typeof value.strikeReceipt === 'string'
    && Number.isSafeInteger(value.strikeEpoch) && Number.isSafeInteger(value.nextStrikeEpoch);
}

function assertStrike(value, leaseStatus, phase) {
  if (!sceneExpectation(value, leaseStatus) || value.strikeHealthAfter >= value.strikeHealthBefore
      || !value.strikeIntent.startsWith('intent:scene-strike-assault-') || value.strikeReceipt !== `observation:${value.strikeIntent.replace(':', '-')}`) {
    throw new Error(`F0.2C HOT carrier has an invalid ${phase} observed receipt`);
  }
}

function sameReceipt(left, right) {
  return left?.strikeCause === right?.strikeCause && left.strikeAttacker === right?.strikeAttacker && left.strikeTarget === right?.strikeTarget
    && left.strikeIntent === right?.strikeIntent && left.strikeReceipt === right?.strikeReceipt && left.strikeHealthBefore === right?.strikeHealthBefore
    && left.strikeHealthAfter === right?.strikeHealthAfter && left.strikeReceiptExact === right?.strikeReceiptExact && left.strikeHealthChanged === right?.strikeHealthChanged;
}
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function uuid(value) { return typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value); }
function sha256Json(value) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }
