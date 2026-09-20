const SCENARIO = 'disposable_f06r3_cold_terminal';
const SITE = 'site:7-wheat-field';
const FARMER = 'resident:7-31';

/**
 * One real no-player terminal interval followed by one ordinary field visit.  The completed
 * fast-forward receipt proves that COLD terminal closure is not held by an unloaded physical
 * surface; the later visit may only materialize its already-owned exact output.
 */
export function assertF06r3ColdTerminalCarrier({ declaration, beforeRestart, manifest, topologyRevisionEvidence }) {
  assertF06r3ColdTerminalDeclaration(declaration);
  if (beforeRestart?.status !== 'ok' || manifest?.status !== 'ok'
      || manifest.scenarioId !== SCENARIO || manifest.clientSegments?.length !== 1) {
    throw new Error('F0.6R3 COLD terminal carrier lacks its exact no-player/ordinary-ingress declaration');
  }
  const prelude = manifest.zeroPlayerPrelude;
  if (!f06r3AcceleratedPreludeControl(prelude, manifest)) {
    throw new Error('F0.6R3 COLD terminal carrier lacks its completed no-player semantic receipt');
  }
  const restart = manifest.restartZeroPlayerInterlude;
  if (restart?.status !== 'completed' || restart.advanceTicks !== 4000 || restart.clientSegmentsBeforeCompletion !== 0
      || restart.boundedness?.status !== 'NO_STALL' || restart.boundedness?.noServerTickStall !== true) {
    throw new Error('F0.6R3 COLD terminal carrier lacks its exact restart/recovery COLD receipt');
  }
  const before = exactly(beforeRestart, 1, 'site', SITE);
  const farmer = exactly(beforeRestart, 2, 'actor', FARMER);
  const depot = exactly(manifest, 6, 'container', 'container:7-depot');
  const disposition = exactlyStatus(manifest, 7, 'intent', 'intent:site-harvest-7-wheat-field-1', 'not_found');
  const after = exactly(manifest, 8, 'site', SITE);
  const summary = exactly(manifest, 9, 'summary', '');
  const terminal = before.terminalHarvest;
  if (before.phase !== 'GROWING' || before.activeWork !== '' || before.conflictDisposition !== null
      || terminal?.job !== 'job:site-harvest-7-wheat-field-1' || terminal.worker !== FARMER
      || terminal.outputItem !== 'item:site-harvest-7-wheat-field-1-wheat' || terminal.outputSlot !== 0
      || terminal.outputOwned !== false || terminal.canonicalSuccessor !== true || terminal.physicalReceiptConfirmed !== false || terminal.intentStatus !== 'MISSING'
      || terminal.successor?.state !== 'ACTIVE_COLD' || terminal.successor?.inputItem !== terminal.outputItem
      || terminal.successor?.outputKind !== 'minecraft:bread' || terminal.successor?.outputCount !== 64
      || !samePosition(terminal.terminalBody, farmer.position)) {
    throw new Error('F0.6R3 COLD terminal did not retain the exact wheat-to-bread successor and farmer station before ingress');
  }
  const beforeProduction = exactly(beforeRestart, 3, 'market_order', 'order:production-7-48');
  const orderId = beforeProduction.id;
  const afterProduction = exactly(manifest, 10, 'market_order', orderId);
  const afterReceipt = afterProduction.terminalReceipt;
  if (beforeProduction.orderStatus !== 'ACCEPTED' || beforeProduction.jobActive !== true || beforeProduction.reservationActive !== true
      || beforeProduction.taskStatus !== 'ACTIVE' || beforeProduction.job !== terminal.successor.job
      || afterProduction.orderStatus !== 'FULFILLED' || afterProduction.job !== beforeProduction.job
      || afterReceipt?.job !== terminal.successor.job || afterReceipt.worker !== terminal.successor.worker
      || afterReceipt.outputItem !== terminal.successor.outputItem || !sameExactDecimal(topologyRevisionEvidence?.after, afterReceipt.topology?.revision)
      || !Number.isInteger(afterReceipt.topology?.cursor) || afterReceipt.topology.cursor < 0
      || !integerPosition(afterReceipt.topology?.terminalBody)) {
    throw new Error('F0.6R3 COLD terminal did not convert the exact admitted wheat successor to its durable bread receipt during the no-player interval');
  }
  const retained = after.terminalHarvest;
  if (!depot.occupied?.some(item => item.itemKind === 'minecraft:bread' && item.count === 64)
      || depot.occupied?.some(item => item.itemKind === 'minecraft:wheat') || after.phase !== 'GROWING' || after.conflictDisposition !== null
      || retained?.job !== terminal.job || retained.outputItem !== terminal.outputItem || retained.canonicalSuccessor !== true
      || retained.physicalReceiptConfirmed !== false || retained.intentStatus !== 'MISSING'
      || retained.successor?.state !== 'TERMINAL' || retained.successor?.outputItem !== afterReceipt.outputItem
      || !Number.isInteger(summary.inventoryConflicts) || summary.inventoryConflicts !== 0) {
    throw new Error('F0.6R3 late projection replayed wheat or corrupted the canonical successor');
  }
  if (!f06r3ColdTerminalIngressResponsive(manifest.clientSegments[0]?.ingressResponsiveness)) {
    throw new Error('F0.6R3 COLD terminal carrier lacks an ordinary responsive client ingress receipt');
  }
  return Object.freeze({ advanceTicks: prelude.advanceTicks, restartAdvanceTicks: restart.advanceTicks,
    worker: FARMER, terminalOutput: terminal.outputItem, breadOutput: terminal.successor.outputItem, successorWorker: afterReceipt.worker,
    successorCursor: afterReceipt.topology.cursor, lateProjectionNoReplay: true, restartRetained: true });
}

export function assertF06r3ColdTerminalDeclaration(declaration) {
  if (declaration?.id !== SCENARIO || declaration?.zeroPlayerPrelude?.advanceTicks !== 24_000
      || declaration.zeroPlayerPrelude.expectTerminalStatus !== 'COMPLETED'
      || declaration?.restart?.mode !== 'graceful' || declaration.restart.afterAction !== 3
      || declaration.restart.zeroPlayerAdvanceTicks !== 4000
      || declaration?.assertNoServerTickStallDuringIngress !== true
      || declaration.actions?.[3]?.position?.diagnostic?.view !== 'site'
      || declaration.actions[3].position.diagnostic.id !== SITE || declaration.actions[3].position.diagnostic.field !== 'lastCrop') {
    throw new Error('F0.6R3 COLD terminal carrier lacks its exact no-player/ordinary-ingress declaration');
  }
  return true;
}

/** Both first entry and re-entry must remain ordinary responsive client receipts. */
export function f06r3ColdTerminalIngressResponsive(receipts) {
  return Array.isArray(receipts) && receipts.length === 2 && receipts.every(receipt => receipt?.status === 'ok'
    && receipt.ordinaryClientJoined === true && receipt.noServerTickStall === true);
}

/**
 * The accelerated server control is a bounded semantic operation, not an ordinary-player TPS
 * probe. Vanilla's accumulated scheduler-debt warning remains recorded in `boundedness`, but
 * acceptance requires the exact completed request, its connected work attribution, and a normal
 * terminal lifecycle. Ordinary ingress remains deliberately stricter above.
 */
export function f06r3AcceleratedPreludeControl(prelude, manifest) {
  const slice = prelude?.performance?.fastForwardSlice;
  const request = prelude?.performance?.fastForwardRequests?.at(-1);
  const terminalLifecycle = manifest?.lifecycle?.some(entry => entry?.barrier === 'terminal_assertion_complete');
  return prelude?.status === 'completed' && prelude.advanceTicks === 24_000
    && prelude.clientSegmentsBeforeCompletion === 0 && prelude.clientSegmentsBeforeAdmission === 0
    && request?.kind === 'RELATIVE' && request.requestedTicks === 24_000 && request.status === 'COMPLETED'
    && Number.isInteger(request.admittedCheckpointInstant) && Number.isInteger(request.reachedCheckpointInstant)
    && request.reachedCheckpointInstant >= request.targetInstant
    && ['samples', 'advancedTicks', 'totalNanos', 'maxNanos', 'safetyNanos', 'maxSafetyNanos', 'advanceNanos', 'maxAdvanceNanos']
      .every(field => Number.isInteger(slice?.[field]) && slice[field] >= 0)
    && slice.samples > 0 && slice.advancedTicks >= 24_000 && manifest?.status === 'ok'
    && manifest?.terminalCleanup?.portClosed === true && terminalLifecycle === true;
}

function exactly(manifest, actionStep, kind, id) {
  const values = observed(manifest).filter(entry => entry?.actionStep === actionStep && entry.value?.kind === kind && entry.value?.id === id)
    .map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6R3 COLD terminal carrier lacks ${kind}:${id} at action ${actionStep}`);
  return values[0];
}
function exactlyStatus(manifest, actionStep, kind, id, status) {
  const values = observed(manifest).filter(entry => entry?.actionStep === actionStep && entry.value?.kind === kind && entry.value?.id === id)
    .map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== status) throw new Error(`F0.6R3 COLD terminal carrier lacks ${status} ${kind}:${id} at action ${actionStep}`);
  return values[0];
}
function observed(manifest) {
  const entries = manifest?.diagnostics ?? [];
  const assertions = entries.flatMap(entry => entry?.observed?.value ? [entry.observed] : []);
  const raw = entries.flatMap(entry => entry?.assertion === undefined && entry?.value ? [entry] : []);
  return assertions.length === 0 ? raw : [...assertions, ...raw.filter(entry => !assertions.some(value => value.actionStep === entry.actionStep
    && value.value?.kind === entry.value?.kind && value.value?.id === entry.value?.id))];
}
function samePosition(left, right) { return left !== null && right !== null && [left?.x, left?.y, left?.z, right?.x, right?.y, right?.z].every(Number.isFinite)
  && left.x === right.x && left.y === right.y && left.z === right.z; }
function integerPosition(value) { return [value?.x, value?.y, value?.z].every(Number.isInteger); }
function sameExactDecimal(left, right) { return typeof left === 'string' && /^-?\d+$/.test(left) && left === right; }
