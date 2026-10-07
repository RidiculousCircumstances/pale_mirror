import { createHash } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import { validateLifecycleBarrierRecords } from './lifecycle-barrier.mjs';

const CAUSE = 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19';
const PROVENANCE = 'captive-bomber-strike:assault:development-settlement-assault';
const OWNER = 'structure:1-hall';
const PART = 'FOUNDATION';
const MATERIAL = 'HALL';
const SELECTOR = CAUSE;

/** The final persistent-client manifest is authority; raw diagnostics are trace only. */
export function assertF02cAftermathCarrier({ clearDeclaration, clearManifest }) {
  const assertions = assertClearDeclaration(clearDeclaration);
  assertPersistentTopology(clearManifest, clearDeclaration);
  const preRestart = pending(assertionObservation(clearManifest, assertions.preRestart, 'pre-restart'), 'pre-restart');
  const recovered = pending(assertionObservation(clearManifest, assertions.recovered, 'recovered before first visit'), 'recovered before first visit');
  const terminal = realized(assertionObservation(clearManifest, assertions.terminal, 'terminal realization'));
  const postcondition = clearManifest.actions[4]?.action;
  assertSamePending(preRestart, recovered, 'recovered before first visit');
  if (preRestart.revision > recovered.revision || recovered.revision > terminal.revision) throw new Error('F0.2C carrier regressed its canonical checkpoint across recovery');
  if (!sameIdentity(preRestart, terminal) || terminal.authorityRevision < 0 || terminal.revision <= recovered.revision
      || terminal.observedAt < terminal.eventAt || terminal.authorityRevision === preRestart.authorityRevision) {
    throw new Error('F0.2C carrier has an invalid terminal owner/revision transition');
  }
  if (postcondition?.type !== 'wait_until_block' || postcondition.block !== 'minecraft:air' || !samePosition(postcondition.position, preRestart.position)) {
    throw new Error('F0.2C carrier lacks the exact Minecraft aftermath postcondition');
  }
  return Object.freeze({ aftermathId: preRestart.aftermathId, cause: preRestart.cause, position: preRestart.position,
    owner: preRestart.expectedOwner, semanticPart: preRestart.expectedPart, revision: terminal.revision });
}

export function assertF02cAftermathDeclaration(value) { return assertClearDeclaration(value); }

/**
 * Identity checks for the exact final persistent manifest.  Semantic receipt
 * parsing remains below this boundary, after all byte/source/run checks pass.
 */
export function assertF02cAftermathIdentity({ declaration, declarationSha256, manifest, outerAttempt }) {
  if (!uuid(outerAttempt) || !sha256(declarationSha256) || manifest?.schema !== 2 || manifest.status !== 'ok'
      || manifest.scenarioId !== declaration.id || manifest.scenarioSha256 !== declarationSha256
      || manifest.scenarioDeclarationSha256 !== declarationSha256) {
    throw new Error('F0.2C carrier has a foreign declaration or final manifest identity');
  }
  if (!uuid(manifest.runId) || manifest.runId === outerAttempt || !Array.isArray(manifest.actions)
      || manifest.actions.length !== declaration.actions.length || !Array.isArray(manifest.clientSegments)
      || manifest.clientSegments.length !== 1) {
    throw new Error('F0.2C carrier conflates or omits its outer/client run identities');
  }
  for (const [index, entry] of manifest.actions.entries()) {
    if (!isDeepStrictEqual(entry?.action, declaration.actions[index]) || entry.correlation !== `scenario:${manifest.runId}:${index + 1}`) {
      throw new Error('F0.2C carrier has a stale or foreign client action correlation');
    }
  }
  const client = manifest.clientSegments[0];
  if (client?.segment !== 'persistent_restart' || client.runId !== manifest.runId || client.reusedJvm !== true) {
    throw new Error('F0.2C carrier lacks its one persistent client identity');
  }
  if (manifest.recovery?.clientSession?.runId !== outerAttempt || manifest.recovery.clientSession.reusedJvm !== true) {
    throw new Error('F0.2C carrier lacks its precommitted recovery attempt');
  }
  const buildIdentitySha256 = sha256Json(manifest.build);
  const records = validateLifecycleBarrierRecords(manifest.lifecycle, {
    schema: 1, buildIdentitySha256, workerId: manifest.lifecycle?.[0]?.identity?.workerId,
    runId: outerAttempt, scenarioId: manifest.scenarioId, segmentId: 'native-scenario',
    nonce: manifest.lifecycle?.[0]?.identity?.nonce, sessionId: manifest.lifecycle?.[0]?.identity?.sessionId
  });
  if (records.length === 0 || records.some((record) => record.identity.runId !== outerAttempt)) {
    throw new Error('F0.2C carrier lifecycle does not retain its precommitted attempt');
  }
  return Object.freeze({ outerAttempt, clientRunId: manifest.runId,
    lifecycle: Object.freeze({ runId: records[0].identity.runId, sessionId: records[0].identity.sessionId, nonce: records[0].identity.nonce }) });
}

function assertClearDeclaration(value) {
  const actions = value?.actions ?? [];
  if (value?.id !== 'disposable_cold_bomber_aftermath_restart' || value?.server?.profile !== 'cold-bomber-aftermath'
      || value.restart?.afterAction !== 2 || value.restart.mode !== 'graceful'
      || (value.setup ?? []).some(isVisit) || actions.slice(0, 3).some(isVisit) || (value.restart.resumeSetup ?? []).some(isVisit)) {
    throw new Error('F0.2C carrier accepts a missing, wrong, or HOT-previsited COLD cause');
  }
  const assertions = value.assertions;
  if (!Array.isArray(assertions) || assertions.length !== 3) throw new Error('F0.2C carrier declaration lacks its three semantic assertions');
  const expected = new Map([[1, 'preRestart'], [3, 'recovered'], [6, 'terminal']]); const selected = {};
  for (const assertion of assertions) {
    const phase = expected.get(assertion?.after);
    if (!phase || assertion.view !== 'aftermath' || assertion.id !== SELECTOR || selected[phase] !== undefined) {
      throw new Error('F0.2C carrier declaration has an unexpected assertion topology');
    }
    selected[phase] = assertion;
  }
  if (!pendingExpectation(selected.preRestart?.expect) || !pendingExpectation(selected.recovered?.expect) || !terminalExpectation(selected.terminal?.expect)) {
    throw new Error('F0.2C carrier declaration lacks its exact pending/realized expectations');
  }
  if (actions.length !== 6 || actions[0]?.type !== 'wait_until_diagnostic' || actions[0]?.id !== SELECTOR
      || actions[2]?.type !== 'wait_until_diagnostic' || actions[2]?.id !== SELECTOR || actions[3]?.type !== 'visit'
      || actions[4]?.type !== 'wait_until_block' || actions[5]?.type !== 'wait_until_diagnostic' || actions[5]?.id !== SELECTOR) {
    throw new Error('F0.2C carrier declaration lacks its global recovery action order');
  }
  return Object.freeze(selected);
}

function assertPersistentTopology(manifest, declaration) {
  if (manifest?.status !== 'ok' || manifest?.recovery?.mode !== 'graceful' || manifest.recovery.splitAfterAction !== 2
      || manifest.recovery?.clientSession?.reusedJvm !== true || !Array.isArray(manifest.actions)
      || manifest.actions.length !== declaration.actions.length || !Array.isArray(manifest.diagnostics) || !Array.isArray(manifest.lifecycle)) {
    throw new Error('F0.2C carrier lacks the persistent final-manifest topology');
  }
  if (!manifest.actions.every((entry, index) => isDeepStrictEqual(entry?.action, declaration.actions[index]))) {
    throw new Error('F0.2C carrier final manifest does not retain declared global action ordering');
  }
  const barriers = manifest.lifecycle.map((entry) => entry?.barrier);
  const order = ['server_run_ready', 'scenario_segment_complete', 'client_normally_disconnected', 'durable_server_save',
    'game_port_closed', 'recovery_server_ready', 'same_client_reconnected_state_cleared', 'terminal_assertion_complete'];
  let previous = -1;
  for (const barrier of order) {
    const index = barriers.indexOf(barrier);
    if (index < 0 || index <= previous) throw new Error('F0.2C carrier lacks ordered graceful recovery lifecycle evidence');
    previous = index;
  }
}

function assertionObservation(manifest, declaration, phase) {
  const sameSlot = manifest.diagnostics.filter((entry) => entry?.assertion?.after === declaration.after
    && entry.assertion?.view === declaration.view && entry.assertion?.id === declaration.id);
  if (sameSlot.length !== 1 || !isDeepStrictEqual(sameSlot[0].assertion, declaration)
      || sameSlot[0]?.observed?.actionStep !== declaration.after || !sameSlot[0].observed?.value) {
    throw new Error(`F0.2C carrier lacks one exact assertion-bound ${phase} receipt`);
  }
  return sameSlot[0].observed.value;
}

function pendingExpectation(value) {
  return value?.cause === CAUSE && value.epoch === 4 && value.expectedOwner === OWNER && value.expectedPart === PART
    && value.expectedMaterial === MATERIAL && value.provenance === PROVENANCE && value.authorityRevision === -1
    && value.cellStatus === 'PENDING' && value.nextStatus === 'PENDING' && value.terminal === false;
}
function terminalExpectation(value) {
  return value?.cause === CAUSE && value.epoch === 4 && value.expectedOwner === OWNER && value.expectedPart === PART
    && value.expectedMaterial === MATERIAL && value.provenance === PROVENANCE && value.cellStatus === 'REALIZED'
    && value.nextStatus === 'NONE' && value.terminal === true;
}
function isVisit(action) { return action?.type === 'visit'; }
function pending(value, phase) {
  if (!sameIdentity(value, value) || value.terminal !== false || value.nextStatus !== 'PENDING' || value.cellStatus !== 'PENDING'
      || value.authorityRevision !== -1 || value.observedAt !== null || value.cursor !== 0 || !revision(value)) {
    throw new Error(`F0.2C carrier lacks its exact ${phase} PENDING receipt`);
  }
  return value;
}
function realized(value) {
  if (!sameIdentity(value, value) || value.terminal !== true || value.nextStatus !== 'NONE' || value.cellStatus !== 'REALIZED'
      || !Number.isSafeInteger(value.authorityRevision) || value.authorityRevision < 0 || !Number.isSafeInteger(value.observedAt)
      || value.cursor !== 1 || !revision(value)) throw new Error('F0.2C carrier lacks its exact terminal REALIZED receipt');
  return value;
}
function sameIdentity(left, right) {
  return left?.kind === 'aftermath' && left.id === SELECTOR && left.status === 'ok' && left.aftermathId
    && left.aftermathId === right?.aftermathId && left.cause === CAUSE && left.cause === right?.cause
    && left.provenance === PROVENANCE && left.provenance === right?.provenance && left.epoch === 4 && left.epoch === right?.epoch
    && left.expectedOwner === OWNER && left.expectedOwner === right?.expectedOwner && left.expectedPart === PART && left.expectedPart === right?.expectedPart
    && left.expectedMaterial === MATERIAL && left.expectedMaterial === right?.expectedMaterial && samePosition(left.position, right?.position)
    && Number.isSafeInteger(left.eventAt) && left.eventAt >= 0 && left.eventAt === right?.eventAt;
}
function assertSamePending(left, right, phase) {
  if (!sameIdentity(left, right) || right.authorityRevision !== -1 || right.observedAt !== null) throw new Error(`F0.2C carrier replaced its exact pending aftermath at ${phase}`);
}
function revision(value) { return Number.isSafeInteger(value?.revision) && value.revision >= 0; }
function validPosition(value) { return Number.isInteger(value?.x) && Number.isInteger(value?.y) && Number.isInteger(value?.z); }
function samePosition(left, right) { return validPosition(left) && validPosition(right) && left.x === right.x && left.y === right.y && left.z === right.z; }
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function uuid(value) { return typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value); }
function sha256Json(value) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }
