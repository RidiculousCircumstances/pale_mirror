const CAUSE = 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19';
const PROVENANCE = 'captive-bomber-strike:assault:development-settlement-assault';
const SELECTOR = CAUSE;

/**
 * Reads the one ordinary COLD isolated-scenario receipt. It deliberately does not infer a
 * physical result from a terminal cursor: known-clear history needs both the client-observed
 * AIR postcondition and the durable REALIZED cell after restart.
 */
export function assertF02cAftermathCarrier({ clearDeclaration, clearBefore, clearAfter }) {
  assertClearDeclaration(clearDeclaration);
  const before = aftermathAt(clearBefore, 1); const beforeRepeat = aftermathAt(clearBefore, 2);
  const after = aftermathAt(clearAfter, 3);
  const entryId = pending(before);
  if (pending(beforeRepeat) !== entryId) throw new Error('F0.2C carrier reordered or replaced its exact pending aftermath');
  if (!clearAfter?.recovery || clearAfter.recovery.mode !== 'graceful' || clearAfter.recovery.splitAfterAction !== 2) {
    throw new Error('F0.2C carrier lacks its declared durable restart boundary');
  }
  const postcondition = action(clearAfter, 'wait_until_block');
  if (postcondition?.block !== 'minecraft:air' || !samePosition(postcondition.position, before.position)) {
    throw new Error('F0.2C carrier lacks the actual Minecraft aftermath postcondition');
  }
  if (after.aftermathId !== entryId || after.cause !== before.cause || after.provenance !== before.provenance
      || after.epoch !== before.epoch || after.expectedMaterial !== before.expectedMaterial || after.cellStatus !== 'REALIZED' || after.nextStatus !== 'NONE' || after.terminal !== true
      || !samePosition(after.position, before.position) || !Number.isSafeInteger(after.observedAt)) {
    throw new Error('F0.2C carrier lacks exact recovered cause/receipt/postcondition correlation');
  }
  return Object.freeze({ aftermathId: entryId, cause: before.cause, position: before.position });
}

function assertClearDeclaration(value) {
  const first = value?.actions?.[0]; const restart = value?.restart;
  if (value?.id !== 'disposable_cold_bomber_aftermath_restart' || value?.server?.profile !== 'cold-bomber-aftermath' || first?.type !== 'wait_until_diagnostic' || first.id !== SELECTOR
      || first.expect?.cause !== CAUSE || first.expect?.epoch !== 4 || first.expect?.expectedMaterial !== 'HALL' || first.expect?.provenance !== PROVENANCE || first.expect?.cellStatus !== 'PENDING'
      || value.setup?.some(action => action.type === 'visit') || restart?.afterAction !== 2 || restart.mode !== 'graceful') {
    throw new Error('F0.2C carrier accepts a missing, wrong, or HOT-previsited COLD cause');
  }
}
function pending(value) {
  if (!value || value.kind !== 'aftermath' || value.id !== SELECTOR || value.status !== 'ok' || value.cause !== CAUSE
      || value.provenance !== PROVENANCE || !value.aftermathId || value.terminal !== false || value.nextStatus !== 'PENDING'
      || value.epoch !== 4 || value.expectedMaterial !== 'HALL' || value.cellStatus !== 'PENDING' || !validPosition(value.position) || value.observedAt !== null) {
    throw new Error('F0.2C carrier lacks its exact COLD pending receipt');
  }
  return value.aftermathId;
}
function aftermathAt(manifest, step) {
  return diagnostic(manifest, step, 'aftermath', SELECTOR);
}
function diagnostic(manifest, step, kind, id) {
  const value = manifest?.diagnostics?.map(entry => entry.value ?? entry.observed?.value)
    .find(entry => entry?.kind === kind && entry.id === id && entry.pilotActionStep === step);
  if (!value) throw new Error(`F0.2C carrier lacks ${kind}/${id} at action ${step}`);
  return value;
}
function action(manifest, type) { return manifest?.actions?.map(entry => entry.action).find(value => value?.type === type); }
function validPosition(value) { return Number.isInteger(value?.x) && Number.isInteger(value?.y) && Number.isInteger(value?.z); }
function samePosition(left, right) { return validPosition(left) && validPosition(right) && left.x === right.x && left.y === right.y && left.z === right.z; }
