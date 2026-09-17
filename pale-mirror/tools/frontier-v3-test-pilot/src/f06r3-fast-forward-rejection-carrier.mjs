import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f06r3_fast_forward_rejection';

/**
 * Product-shaped recurrence for the r44 command incident.  It binds the real client command,
 * the server-owned receipt and a later ordinary projection in one disposable composition.
 */
export function assertF06r3FastForwardRejectionCarrier({ declaration, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.isolation?.seed !== 46 || declaration?.assertNoServerTickStallDuringIngress !== true
      || declaration?.actions?.length !== 7 || manifest?.status !== 'ok' || manifest?.scenarioId !== SCENARIO
      || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length) {
    throw new Error('F0.6R3 relative rejection carrier lacks its exact disposable command/continuity composition');
  }
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) {
      throw new Error('F0.6R3 relative rejection carrier substituted an operator or projection action');
    }
  });
  const command = declaration.actions[1];
  if (command.type !== 'fast_forward' || command.ticks !== 10_000 || command.expectTerminalStatus !== 'REJECTED'
      || command.expectReasonContains !== 'resource-site-projection:site:7-wheat-field') {
    throw new Error('F0.6R3 relative rejection carrier does not declare the exact r44 physical boundary');
  }
  const performance = exactly(manifest.diagnostics, 3, 'performance', '');
  const request = performance.fastForwardRequests?.at(-1);
  if (performance.status !== 'ok' || performance.fastForwardRemaining !== 0 || request?.kind !== 'RELATIVE'
      || request.requestedTicks !== 10_000 || !Number.isInteger(request.requestId) || request.requestId < 1
      || !Number.isInteger(request.targetInstant) || !Number.isInteger(request.admittedCheckpointInstant)
      || !Number.isInteger(request.reachedCheckpointInstant) || request.targetInstant !== request.admittedCheckpointInstant + request.requestedTicks
      || request.status !== 'REJECTED' || typeof request.reason !== 'string'
      || !request.reason.includes('resource-site-projection:site:7-wheat-field')) {
    throw new Error('F0.6R3 relative request lost its stable identity, target, stop boundary or physical owner receipt');
  }
  const after = exactly(manifest.diagnostics, 5, 'settlement', 'settlement:4');
  if (after.status !== 'ok') throw new Error('F0.6R3 runtime did not remain active for ordinary post-rejection projection');
  const ingress = manifest.clientSegments?.flatMap(segment => Array.isArray(segment?.ingressResponsiveness)
    ? segment.ingressResponsiveness : [segment?.ingressResponsiveness]);
  if (!Array.isArray(ingress) || ingress.length !== 1 || ingress[0]?.status !== 'ok'
      || ingress[0]?.ordinaryClientJoined !== true || ingress[0]?.noServerTickStall !== true) {
    throw new Error('F0.6R3 relative rejection carrier lacks an ordinary responsive client composition');
  }
  const frame = manifest.frames?.find(value => value?.name === 'f06r3-post-rejection-site4-road');
  if (frame?.presentation !== 'player' || typeof frame.path !== 'string' || !frame.path.endsWith('.png')) {
    throw new Error('F0.6R3 relative rejection carrier lacks the post-rejection visible road proof');
  }
  return Object.freeze({ requestId: request.requestId, targetInstant: request.targetInstant,
    admittedCheckpointInstant: request.admittedCheckpointInstant, reachedCheckpointInstant: request.reachedCheckpointInstant,
    owner: 'resource-site-projection:site:7-wheat-field', postRejectionSettlement: after.id, ordinaryProjectionContinued: true });
}

function exactly(entries, actionStep, kind, id) {
  const match = entries?.filter(entry => entry?.actionStep === actionStep && entry?.value?.kind === kind && entry.value.id === id) ?? [];
  if (match.length !== 1) throw new Error(`F0.6R3 relative rejection carrier lacks exactly one ${kind}:${id} receipt at action ${actionStep}`);
  return match[0].value;
}
