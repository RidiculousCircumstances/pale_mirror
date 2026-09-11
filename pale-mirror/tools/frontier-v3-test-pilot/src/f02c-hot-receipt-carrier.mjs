import { createHash } from 'node:crypto';
import { isDeepStrictEqual } from 'node:util';
import { validateLifecycleBarrierRecords } from './lifecycle-barrier.mjs';

const SCENE = 'assault:development-settlement-assault';
const HANDSHAKE = 'settlement-assault-visit';
// Northwatch's canonical settlement anchor, distinct from the player travel coordinate below.
const HANDOFF = Object.freeze({ x: -360, y: 64, z: -340 });
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
      // COLD is an ordinary deterministic scheduler, so it may advance before the first
      // once-per-second diagnostic poll after a CLOSED hand-off.  The retained HOT receipt
      // stays epoch zero; COLD must have advanced from it without replaying that receipt.
      || released.strikeEpoch !== 0 || !Number.isSafeInteger(released.nextStrikeEpoch) || released.nextStrikeEpoch < 1
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
  const ingress = value.actions[0];
  if (ingress?.type !== 'visit' || ingress.dimension !== 'pale_mirror:frontier_graybox' || ingress.settleMs !== 0
      || !isDeepStrictEqual(ingress.position, { x: -360, y: 65, z: -352 })
      || !isDeepStrictEqual(ingress.demandHandshake, { request: HANDSHAKE, assault: SCENE, handoff: HANDOFF })) {
    throw new Error('F0.2C HOT carrier lacks its server-thread demand handshake ingress');
  }
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
  // The completed manifest retains lifecycle records rather than a copied summary.  Re-admit
  // those records through the ordinary journal validator before binding the two durable action
  // checkpoints below.
  const records = validateLifecycleBarrierRecords(manifest.lifecycle, manifest.lifecycle[0]?.identity);
  const barriers = records.map(entry => entry.barrier);
  const expected = ['server_run_ready', 'scenario_segment_complete', 'client_normally_disconnected', 'durable_server_save',
    'game_port_closed', 'recovery_server_ready', 'same_client_reconnected_state_cleared', 'terminal_assertion_complete'];
  let previous = -1;
  for (const barrier of expected) {
    const current = barriers.indexOf(barrier);
    if (current < 0 || current <= previous) throw new Error('F0.2C HOT carrier lifecycle is incomplete or misordered');
    previous = current;
  }
  assertHotRestartPhaseTrace(records, declaration);
  assertDemandHandshake(manifest, declaration.actions[0]);
  assertClientIngress(manifest, declaration.actions[0]);
}

/** The visit cannot become a completed action from client chunk visibility or elapsed settling alone. */
function assertDemandHandshake(manifest, ingress) {
  const outerLifecycleRun = manifest.lifecycle?.[0]?.identity?.runId;
  const records = observedDiagnostics(manifest).filter(entry => entry.actionStep === 1 && entry.value?.kind === 'demand_handshake'
    && entry.value?.id === ingress.demandHandshake.request);
  if (records.length !== 1) throw new Error('F0.2C HOT carrier lacks one server-thread demand handshake receipt');
  const value = records[0].value;
  if (value.assault !== ingress.demandHandshake.assault || value.destinationDimension !== ingress.dimension
      || !isDeepStrictEqual(value.travelAnchor, ingress.position) || !isDeepStrictEqual(value.candidateHandoff, ingress.demandHandshake.handoff)
      || !value.serverPlayerPosition || value.reason !== 'ADMITTED' || typeof value.playerId !== 'string' || value.playerId.length === 0
      || !uuid(value.pilotRunId) || value.pilotRunId !== outerLifecycleRun || value.pilotActionStep !== 1 || !uuid(value.pilotActionAttempt)
      || value.destinationObserved !== true || value.destinationPlayerTicket !== true || value.destinationHolder !== true
      || typeof value.providerIdentity !== 'string' || value.providerIdentity.length === 0 || value.exactCandidateCount !== 1
      || value.sceneDemandChunkLoaded !== true || value.requestedObserverPresent !== true
      || !Array.isArray(value.sceneDemandObserverIds) || !value.sceneDemandObserverIds.includes(value.playerId)) {
    throw new Error('F0.2C HOT carrier has an incomplete or non-admitted demand handshake receipt');
  }
}

/** Client-visible ingress is a separate receipt: server admission never substitutes for it. */
function assertClientIngress(manifest, ingress) {
  const outerLifecycleRun = manifest.lifecycle?.[0]?.identity?.runId;
  const diagnostics = observedDiagnostics(manifest);
  const records = diagnostics.filter(entry => entry.actionStep === 1 && entry.value?.kind === 'visit_ingress'
    && entry.value?.id === ingress.demandHandshake.request);
  if (records.length !== 1) throw new Error('F0.2C HOT carrier lacks one client ingress receipt');
  const value = records[0].value;
  const server = diagnostics.find(entry => entry.actionStep === 1 && entry.value?.kind === 'demand_handshake'
    && entry.value?.id === ingress.demandHandshake.request)?.value;
  if (value.targetDimensionSeen !== true || value.targetChunkSeen !== true || value.finalClientDimension !== ingress.dimension
      || !point(value.finalClientPosition) || !server || value.pilotRunId !== outerLifecycleRun || value.pilotRunId !== server.pilotRunId
      || value.pilotActionStep !== server.pilotActionStep || value.pilotActionAttempt !== server.pilotActionAttempt) {
    throw new Error('F0.2C HOT carrier has incomplete client destination/chunk ingress evidence');
  }
}
function point(value) { return value && Number.isSafeInteger(value.x) && Number.isSafeInteger(value.y) && Number.isSafeInteger(value.z); }

// Assertions retain their observed wrapper, while ordinary diagnostic stream entries are copied
// verbatim by the native scenario runner.  Both describe the same read-only observation; do not
// admit assertion metadata as if it were a raw receipt.
function observedDiagnostics(manifest) {
  return (manifest?.diagnostics ?? []).flatMap(entry => {
    if (entry?.observed?.value) return [entry.observed];
    if (entry?.assertion === undefined && entry?.value) return [entry];
    return [];
  });
}

// The shared journal validator deliberately owns generic identity, sequence and transition
// validity.  This carrier alone owns which acknowledged actions and completed segments belong
// on either side of its graceful restart; searching for matching tuples globally would permit
// those phase details to be exchanged without changing the generic lifecycle shape.
function assertHotRestartPhaseTrace(records, declaration) {
  const restartAfter = declaration?.restart?.afterAction;
  const actionCount = declaration?.actions?.length;
  if (!Number.isSafeInteger(restartAfter) || restartAfter < 1 || !Number.isSafeInteger(actionCount) || actionCount <= restartAfter) {
    throw new Error('F0.2C HOT carrier declaration cannot bind its restart phase trace');
  }
  const checkpoints = records.filter(record => record.barrier === 'action_checkpoint_acknowledged');
  const beforeCheckpoints = phaseCheckpoints(checkpoints, 'before_restart', restartAfter);
  const afterCheckpoints = phaseCheckpoints(checkpoints, 'after_restart', actionCount - restartAfter);
  if (beforeCheckpoints.length !== restartAfter || afterCheckpoints.length !== actionCount - restartAfter
      || !sameSteps(beforeCheckpoints, 1) || !sameSteps(afterCheckpoints, restartAfter + 1)
      || checkpoints.length !== actionCount) {
    throw new Error('F0.2C HOT carrier lifecycle does not bind one exact ordered before_restart/after_restart phase trace');
  }
  const checkpointBefore = beforeCheckpoints.at(-1);
  const completeBefore = exactlyOne(records, 'scenario_segment_complete', record => record.detail?.segment === 'before_restart');
  const disconnected = exactlyOne(records, 'client_normally_disconnected', record => record.detail?.segment === 'before_restart');
  const saved = exactlyOne(records, 'durable_server_save');
  const portClosed = exactlyOne(records, 'game_port_closed');
  const recoveryReady = exactlyOne(records, 'recovery_server_ready');
  const reconnected = exactlyOne(records, 'same_client_reconnected_state_cleared');
  const checkpointAfter = afterCheckpoints.at(-1);
  const completeAfter = exactlyOne(records, 'scenario_segment_complete', record => record.detail?.segment === 'after_restart');
  const terminal = exactlyOne(records, 'terminal_assertion_complete');
  if (records.filter(record => record.barrier === 'scenario_segment_complete').length !== 2
      || !(checkpointBefore.sequence < completeBefore.sequence
        && completeBefore.sequence < disconnected.sequence
        && disconnected.sequence < saved.sequence
        && saved.sequence < portClosed.sequence
        && portClosed.sequence < recoveryReady.sequence
        && recoveryReady.sequence < reconnected.sequence
        && reconnected.sequence < afterCheckpoints[0].sequence
        && afterCheckpoints.every((checkpoint, index) => index === 0 || afterCheckpoints[index - 1].sequence < checkpoint.sequence)
        && checkpointAfter.sequence < completeAfter.sequence
        && completeAfter.sequence < terminal.sequence)) {
    throw new Error('F0.2C HOT carrier lifecycle does not bind one exact ordered before_restart/after_restart phase trace');
  }
}

function phaseCheckpoints(checkpoints, segment, expectedCount) {
  const matching = checkpoints.filter(record => record.detail?.segment === segment);
  return matching.length === expectedCount ? matching : [];
}

function sameSteps(checkpoints, firstStep) {
  return checkpoints.every((checkpoint, index) => checkpoint.detail?.actionStep === firstStep + index);
}

function assertionObservation(manifest, declaration, phase) {
  const records = manifest.diagnostics.filter(entry => entry?.assertion?.after === declaration.after
    && entry.assertion?.view === declaration.view && entry.assertion?.id === declaration.id);
  if (records.length !== 1 || !isDeepStrictEqual(records[0].assertion, declaration)
      || records[0]?.observed?.actionStep !== declaration.after || !records[0]?.observed?.value
      || records[0].observed.value.kind !== 'scene' || records[0].observed.value.id !== SCENE
      || !Object.entries(declaration.expect).every(([key, value]) => isDeepStrictEqual(records[0].observed.value[key], value))) {
    throw new Error(`F0.2C HOT carrier lacks one exact assertion-bound ${phase} observation`);
  }
  return records[0].observed.value;
}

function exactlyOne(records, barrier, predicate = () => true) {
  const matches = records.filter(record => record.barrier === barrier && predicate(record));
  if (matches.length !== 1) throw new Error('F0.2C HOT carrier lifecycle does not bind one exact ordered before_restart/after_restart phase trace');
  return matches[0];
}

function sceneExpectation(value, leaseStatus) {
  return value?.status === 'ok' && value.sceneKind === 'SETTLEMENT_ASSAULT' && value.leaseStatus === leaseStatus
    && value.strikeStatus === 'CONFIRMED' && value.strikeReceiptExact === true && value.strikeHealthChanged === true
    && typeof value.strikeCause === 'string' && typeof value.strikeAttacker === 'string' && typeof value.strikeTarget === 'string'
    && typeof value.strikeIntent === 'string' && typeof value.strikeReceipt === 'string'
    && Number.isSafeInteger(value.strikeEpoch)
    && (leaseStatus === 'CLOSED' || value.nextStrikeEpoch === 1);
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
