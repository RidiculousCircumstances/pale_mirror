const SCENARIO = 'disposable_f06r3_cold_production';
const JOB = 'job:production-development-input-theft';
const WORKER = 'resident:1-15';

/**
 * Real no-player COLD traversal followed by one ordinary workshop visit.  The only permitted
 * HOT contribution is materializing the retained current body; it cannot restart the worker
 * at cursor zero or choose a new origin.
 */
export function assertF06r3ColdProductionCarrier({ declaration, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.server?.profile !== 'production-work'
      || declaration?.zeroPlayerPrelude?.advanceTicks !== 140 || declaration.zeroPlayerPrelude.expectTerminalStatus !== 'COMPLETED'
      || declaration?.assertNoServerTickStallDuringIngress !== true || manifest?.status !== 'ok'
      || manifest.scenarioId !== SCENARIO || manifest.clientSegments?.length !== 1) {
    throw new Error('F0.6R3 COLD production carrier lacks its exact no-player/ordinary-ingress declaration');
  }
  const prelude = manifest.zeroPlayerPrelude;
  if (prelude?.status !== 'completed' || prelude.clientSegmentsBeforeCompletion !== 0
      || prelude.boundedness?.status !== 'NO_STALL' || prelude.boundedness?.noServerTickStall !== true) {
    throw new Error('F0.6R3 COLD production carrier lacks a bounded completed no-player receipt');
  }
  const before = exactly(manifest, 1, 'process', JOB);
  const beforeActor = exactly(manifest, 2, 'actor', WORKER);
  const visible = exactly(manifest, 4, 'actor', WORKER);
  const after = exactly(manifest, 5, 'process', JOB);
  if (before.family !== 'frontier.production-work' || before.identity?.worker !== WORKER
      || before.claims?.lease !== null || !Number.isInteger(before.cursor?.index) || before.cursor.index <= 0
      || !Number.isInteger(before.cursor?.length) || before.cursor.index >= before.cursor.length
      || !samePosition(before.cursor.retainedBody, before.cursor.actorBody) || !samePosition(before.cursor.retainedBody, beforeActor.position)) {
    throw new Error('F0.6R3 COLD production did not retain one active exact industrial worker cursor before ingress');
  }
  const placement = visible.physicalAdmission?.placement;
  if (!samePosition(placement, before.cursor.retainedBody)
      || !Number.isInteger(after.cursor?.index) || after.cursor.index < before.cursor.index
      || after.cursor.index >= after.cursor.length || after.identity?.worker !== WORKER) {
    throw new Error('F0.6R3 first industrial visibility replayed a route origin instead of materializing its retained cursor');
  }
  const ingress = manifest.clientSegments[0]?.ingressResponsiveness;
  if (ingress?.status !== 'ok' || ingress.ordinaryClientJoined !== true || ingress.noServerTickStall !== true) {
    throw new Error('F0.6R3 COLD production carrier lacks an ordinary responsive client ingress receipt');
  }
  return Object.freeze({ worker: WORKER, coldCursor: before.cursor.index, firstPlacement: placement, postIngressCursor: after.cursor.index });
}

function exactly(manifest, actionStep, kind, id) {
  const values = observed(manifest).filter(entry => entry?.actionStep === actionStep && entry.value?.kind === kind && entry.value?.id === id)
    .map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6R3 COLD production carrier lacks ${kind}:${id} at action ${actionStep}`);
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
