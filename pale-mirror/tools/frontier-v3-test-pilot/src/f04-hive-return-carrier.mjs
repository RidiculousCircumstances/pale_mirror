import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_hive_return_restart';
const MOBILIZATION = 'mobilization:development-hive-mobilization';
const RETURN_FRONTIER = Object.freeze({ x: 417, y: 64, z: 420 });

/** Validates the retained native manifest, never a fixture copy or a derived summary. */
export function assertF04HiveReturnCarrier(manifest) {
  if (manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO || manifest.recovery?.mode !== 'graceful'
      || manifest.recovery?.splitAfterAction !== 3 || !Array.isArray(manifest.actions) || manifest.actions.length !== 5) {
    throw new Error('F0.4 return carrier lacks one exact persistent graceful lifecycle');
  }
  const before = exactSnapshot(manifest, 3);
  const after = exactSnapshot(manifest, 5);
  if (!before || !after || before.survivors !== 4 || after.survivors !== 4 || before.returnComplete || after.returnComplete
      || !retainedSurvivorProgress(before, after) || !observedBodies(before) || !observedBodies(after)) {
    throw new Error('F0.4 return carrier observed reset, replacement, loss, or unbound survivor positions');
  }
  return Object.freeze({ mobilization: MOBILIZATION, survivors: Object.freeze(before.survivorPositions), recovered: Object.freeze(after.survivorPositions) });
}

/** Rejects altered scenario geometry before a native runner can consume it. */
export function assertF04HiveReturnDeclaration(declaration) {
  const actions = declaration?.actions;
  const visit = actions?.[0];
  if (declaration?.id !== SCENARIO || declaration?.isolation?.mode !== 'disposable_lite' || declaration.server?.profile !== 'hive-return'
      || declaration.restart?.mode !== 'graceful' || declaration.restart?.afterAction !== 3 || !Array.isArray(actions) || actions.length !== 5
      || visit?.type !== 'visit' || visit.dimension !== 'pale_mirror:frontier_graybox' || !isDeepStrictEqual(visit.position, RETURN_FRONTIER)
      || actions[2]?.type !== 'inspect' || actions[2].view !== 'hive_mobilization' || actions[2].id !== MOBILIZATION
      || actions[4]?.type !== 'inspect' || actions[4].view !== 'hive_mobilization' || actions[4].id !== MOBILIZATION) {
    throw new Error('F0.4 return carrier declaration lacks its exact retained-return lifecycle');
  }
  return Object.freeze({ scenario: SCENARIO, mobilization: MOBILIZATION, returnFrontier: RETURN_FRONTIER });
}

/** Binds the final manifest to the one declaration and persistent client attempt. */
export function assertF04HiveReturnIdentity({ declaration, declarationSha256, manifest, outerAttempt }) {
  if (!uuid(outerAttempt) || !sha256(declarationSha256) || manifest?.schema !== 2 || manifest.status !== 'ok'
      || manifest.scenarioId !== SCENARIO || manifest.scenarioSha256 !== declarationSha256
      || manifest.scenarioDeclarationSha256 !== declarationSha256 || !uuid(manifest.runId) || manifest.runId === outerAttempt
      || manifest.recovery?.mode !== 'graceful' || manifest.recovery?.splitAfterAction !== 3
      || manifest.recovery?.clientSession?.runId !== outerAttempt || manifest.recovery.clientSession.reusedJvm !== true
      || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length
      || !Array.isArray(manifest.clientSegments) || manifest.clientSegments.length !== 1
      || manifest.clientSegments[0]?.segment !== 'persistent_restart' || manifest.clientSegments[0]?.runId !== manifest.runId
      || manifest.clientSegments[0]?.reusedJvm !== true) {
    throw new Error('F0.4 return carrier has a foreign persistent manifest identity');
  }
  for (const [index, entry] of manifest.actions.entries()) {
    if (!isDeepStrictEqual(entry?.action, declaration.actions[index]) || entry.correlation !== `scenario:${manifest.runId}:${index + 1}`) {
      throw new Error('F0.4 return carrier has a stale or foreign action correlation');
    }
  }
  return Object.freeze({ outerAttempt, clientRunId: manifest.runId });
}

function retainedSurvivorProgress(before, after) {
  if (!Number.isInteger(before.returnedMembers) || !Number.isInteger(after.returnedMembers)
      || before.returnedMembers < 0 || after.returnedMembers < before.returnedMembers) return false;
  const first = normalizeSurvivors(before.survivorPositions); const second = normalizeSurvivors(after.survivorPositions);
  // A current physical return may advance its exact cursor while Minecraft is
  // down.  If it did not, the restored bodies must remain exactly where the
  // canonical pre-stop receipt left them; otherwise the later observation is a
  // reset/replacement rather than continuous execution.
  return first.length === 4 && first.every(validSurvivor) && second.length === 4 && second.every(validSurvivor)
    && sameIds(first, second) && (after.returnedMembers > before.returnedMembers || isDeepStrictEqual(first, second));
}

function normalizeSurvivors(values) {
  return Array.isArray(values) ? values.map(value => ({ id: value?.id, x: value?.x, y: value?.y, z: value?.z }))
    .sort((a, b) => String(a.id).localeCompare(String(b.id))) : [];
}
function observedBodies(snapshot) {
  const bodies = normalizeSurvivors(snapshot?.physicalSurvivors?.map(value => ({ id: value?.id, ...value?.observed })));
  const canonical = normalizeSurvivors(snapshot.survivorPositions);
  return bodies.length === 4 && bodies.every(validSurvivor) && sameIds(canonical, bodies)
    && canonical.every((value, index) => value.x === bodies[index].x && value.y + 1 === bodies[index].y && value.z === bodies[index].z);
}

function validSurvivor(value) { return typeof value.id === 'string' && Number.isInteger(value.x) && Number.isInteger(value.y) && Number.isInteger(value.z); }
function sameIds(left, right) { return left.every((value, index) => value.id === right[index]?.id); }
function exactSnapshot(manifest, actionStep) {
  const matches = (manifest?.diagnostics ?? []).filter(entry => entry?.observed?.actionStep === actionStep
    && entry.observed.value?.kind === 'hive_mobilization' && entry.observed.value.id === MOBILIZATION
    && entry.observed.value.status === 'ok' && entry.observed.value.mobilizationStatus === 'RETURNING');
  if (matches.length !== 1 || !Array.isArray(matches[0].observed.value.survivorPositions)) {
    throw new Error('F0.4 return carrier lacks retained pre/post-restart survivor observations');
  }
  return matches[0].observed.value;
}
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function uuid(value) { return typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value); }
