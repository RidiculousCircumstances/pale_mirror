const CAUSE = 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19';
const PROVENANCE = 'captive-bomber-strike:assault:development-settlement-assault';
const OWNER = 'structure:1-hall';
const PART = 'FOUNDATION';
const MATERIAL = 'HALL';
const SELECTOR = CAUSE;

/**
 * Validates the one ordinary COLD recovery history. The final AIR block is
 * insufficient: the same pending owner must survive a real restart before its
 * first natural visit, then advance once through the retained owner revision.
 */
export function assertF02cAftermathCarrier({ clearDeclaration, clearBefore, clearAfter }) {
  assertClearDeclaration(clearDeclaration);
  const preRestart = pending(aftermathAt(clearBefore, 1), 'pre-restart');
  const durablePreRestart = pending(aftermathAt(clearBefore, 2), 'durable pre-restart');
  const recovered = pending(aftermathAt(clearAfter, 3), 'recovered before first visit');
  if (!clearAfter?.recovery || clearAfter.recovery.mode !== 'graceful' || clearAfter.recovery.splitAfterAction !== 2) {
    throw new Error('F0.2C carrier lacks its declared durable restart boundary');
  }
  const postcondition = action(clearAfter, 'wait_until_block');
  const terminal = realized(aftermathAt(clearAfter, 6));
  assertSamePending(preRestart, durablePreRestart, 'durable pre-restart');
  assertSamePending(preRestart, recovered, 'recovered before first visit');
  if (preRestart.revision > durablePreRestart.revision || durablePreRestart.revision > recovered.revision) {
    throw new Error('F0.2C carrier regressed its canonical checkpoint across restart');
  }
  if (!sameIdentity(preRestart, terminal) || terminal.authorityRevision < 0 || terminal.revision <= recovered.revision
      || terminal.observedAt < terminal.eventAt || terminal.authorityRevision === preRestart.authorityRevision) {
    throw new Error('F0.2C carrier has an invalid terminal owner/revision transition');
  }
  if (postcondition?.block !== 'minecraft:air' || !samePosition(postcondition.position, preRestart.position)) {
    throw new Error('F0.2C carrier lacks the exact Minecraft aftermath postcondition');
  }
  return Object.freeze({ aftermathId: preRestart.aftermathId, cause: preRestart.cause, position: preRestart.position,
    owner: preRestart.expectedOwner, semanticPart: preRestart.expectedPart, revision: terminal.revision });
}

function assertClearDeclaration(value) {
  const actions = value?.actions ?? []; const first = actions[0]; const recovered = actions[2]; const visit = actions[3]; const terminal = actions[5];
  if (value?.id !== 'disposable_cold_bomber_aftermath_restart' || value?.server?.profile !== 'cold-bomber-aftermath'
      || first?.type !== 'wait_until_diagnostic' || first.id !== SELECTOR || !pendingExpectation(first.expect)
      || recovered?.type !== 'wait_until_diagnostic' || recovered.id !== SELECTOR || !pendingExpectation(recovered.expect)
      || visit?.type !== 'visit' || terminal?.type !== 'wait_until_diagnostic' || terminal.id !== SELECTOR
      || value.restart?.afterAction !== 2 || value.restart.mode !== 'graceful'
      || (value.setup ?? []).some(isVisit) || actions.slice(0, 3).some(isVisit) || (value.restart.resumeSetup ?? []).some(isVisit)) {
    throw new Error('F0.2C carrier accepts a missing, wrong, or HOT-previsited COLD cause');
  }
}
function pendingExpectation(value) {
  return value?.cause === CAUSE && value.epoch === 4 && value.expectedOwner === OWNER && value.expectedPart === PART
    && value.expectedMaterial === MATERIAL && value.provenance === PROVENANCE && value.authorityRevision === -1
    && value.cellStatus === 'PENDING' && value.nextStatus === 'PENDING' && value.terminal === false;
}
function isVisit(action) { return action?.type === 'visit' || action?.type === 'visit_operation'; }
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
      || value.cursor !== 1 || !revision(value)) {
    throw new Error('F0.2C carrier lacks its exact terminal REALIZED receipt');
  }
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
  if (!sameIdentity(left, right) || right.authorityRevision !== -1 || right.observedAt !== null) {
    throw new Error(`F0.2C carrier replaced its exact pending aftermath at ${phase}`);
  }
}
function aftermathAt(manifest, step) { return diagnostic(manifest, step, 'aftermath', SELECTOR); }
function diagnostic(manifest, step, kind, id) {
  const values = manifest?.diagnostics?.map(entry => entry.value ?? entry.observed?.value)
    .filter(entry => entry?.kind === kind && entry.id === id && entry.pilotActionStep === step) ?? [];
  const value = values.at(-1);
  if (!value) throw new Error(`F0.2C carrier lacks ${kind}/${id} at action ${step}`);
  return value;
}
function action(manifest, type) { return manifest?.actions?.map(entry => entry.action).find(value => value?.type === type); }
function revision(value) { return Number.isSafeInteger(value?.revision) && value.revision >= 0; }
function validPosition(value) { return Number.isInteger(value?.x) && Number.isInteger(value?.y) && Number.isInteger(value?.z); }
function samePosition(left, right) { return validPosition(left) && validPosition(right) && left.x === right.x && left.y === right.y && left.z === right.z; }
