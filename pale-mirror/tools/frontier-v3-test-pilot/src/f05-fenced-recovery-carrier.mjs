import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f05_fenced_route_recovery';
const OPERATION = 'operation:supply-1-2';
const BINDING = 'recovery:body_resident_1-16';

/** Native receipt oracle for one ordinary player-driven HOT reclaim across graceful restart. */
export function assertF05FencedRecoveryCarrier(manifest) {
  if (manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO || manifest.recovery?.mode !== 'graceful'
      || manifest.recovery?.splitAfterAction !== 9 || !Array.isArray(manifest.actions) || manifest.actions.length !== 12) {
    throw new Error('F0.5 fenced recovery manifest lacks one exact persistent lifecycle');
  }
  const before = exactFence(manifest, 9); const after = exactFence(manifest, 11);
  if (!sameFence(before, after)) throw new Error('F0.5 fenced recovery replaced or weakened the active body authority across restart');
  return Object.freeze({ operation: OPERATION, binding: BINDING, epoch: before.epoch, owner: before.owner, ownerRevision: before.ownerRevision });
}

export function assertF05FencedRecoveryDeclaration(declaration) {
  const actions = declaration?.actions;
  if (declaration?.id !== SCENARIO || declaration?.isolation?.mode !== 'disposable_lite' || declaration.server?.profile !== 'scene-return'
      || declaration.restart?.mode !== 'graceful' || declaration.restart?.afterAction !== 9 || !Array.isArray(actions) || actions.length !== 12
      || actions[5]?.type !== 'visit_operation' || actions[5]?.operationId !== OPERATION
      || !recoveryInspection(actions[7]) || !recoveryInspection(actions[10]) || !recoveryWait(actions[8])
      || actions[9]?.type !== 'wait_until_diagnostic' || actions[9]?.view !== 'scene') {
    throw new Error('F0.5 fenced recovery declaration lacks its exact ordinary reclaim path');
  }
  return Object.freeze({ scenario: SCENARIO, operation: OPERATION, binding: BINDING });
}

export function assertF05FencedRecoveryIdentity({ declaration, declarationSha256, manifest, outerAttempt }) {
  if (!uuid(outerAttempt) || !sha256(declarationSha256) || manifest?.schema !== 2 || manifest.status !== 'ok'
      || manifest.scenarioId !== SCENARIO || manifest.scenarioSha256 !== declarationSha256
      || manifest.scenarioDeclarationSha256 !== declarationSha256 || !uuid(manifest.runId) || manifest.runId === outerAttempt
      || manifest.recovery?.clientSession?.runId !== outerAttempt || manifest.recovery?.clientSession?.reusedJvm !== true
      || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length) {
    throw new Error('F0.5 fenced recovery carrier has a foreign persistent manifest identity');
  }
  for (const [index, entry] of manifest.actions.entries()) {
    if (!isDeepStrictEqual(entry?.action, declaration.actions[index]) || entry.correlation !== `scenario:${manifest.runId}:${index + 1}`) {
      throw new Error('F0.5 fenced recovery carrier has a stale or foreign action correlation');
    }
  }
  return Object.freeze({ outerAttempt, clientRunId: manifest.runId });
}

function recoveryInspection(action) { return action?.type === 'inspect' && action.view === 'recovery' && action.id === BINDING; }
function recoveryWait(action) { return action?.type === 'wait_until_diagnostic' && action.view === 'recovery' && action.id === BINDING
  && action.expect?.status === 'ok' && action.expect.asset === 'BODY' && action.expect.phase === 'RUNNING' && action.expect.nextAction === 'RECLAIM'; }
function exactFence(manifest, actionStep) {
  const matches = (manifest?.diagnostics ?? []).filter(entry => entry?.observed?.actionStep === actionStep
    && entry.observed.value?.kind === 'recovery' && entry.observed.value.id === BINDING && entry.observed.value.status === 'ok');
  const value = matches.length === 1 ? matches[0].observed.value : null;
  if (!value || value.asset !== 'BODY' || value.phase !== 'RUNNING' || value.nextAction !== 'RECLAIM'
      || typeof value.owner !== 'string' || !value.owner.startsWith('scene:') || !positive(value.ownerRevision) || !positive(value.epoch)
      || value.reversible !== true || value.attempts !== 0) throw new Error('F0.5 fenced recovery lacks one exact current body fence');
  return value;
}
function sameFence(before, after) { return before.owner === after.owner && before.ownerRevision === after.ownerRevision && before.epoch === after.epoch; }
function positive(value) { return Number.isSafeInteger(value) && value > 0; }
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function uuid(value) { return typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value); }
