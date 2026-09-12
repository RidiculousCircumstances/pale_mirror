import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f06_observer_neutrality';
const DIMENSION = 'pale_mirror:frontier_graybox';
const CHUNK = '24,-23';
const ARRIVAL = Object.freeze({ x: 388, y: 65, z: -355 });
const STATIC_ANCHOR = Object.freeze({ x: 380, y: 64, z: -340 });
const SITE = 'site:4-wheat-field';

/**
 * F0.6 receipt predicate. It consumes only action-bound pilot/server observations: a natural
 * client visit makes a static chunk current before it can be shown, and an ordinary player
 * intervention remains a retained conflict through restart and a later neutral return.
 */
export function assertF06ObserverNeutralityCarrier({ declaration, manifest }) {
  assertF06ObserverNeutralityDeclaration(declaration);
  if (manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO || manifest.recovery?.mode !== 'graceful'
      || manifest.recovery?.splitAfterAction !== 10 || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length) {
    throw new Error('F0.6 observer-neutrality manifest lacks its declared persistent lifecycle');
  }
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) throw new Error('F0.6 observer-neutrality manifest substituted an action');
  });
  const diagnostics = observed(manifest);
  const first = exactly(diagnostics, 3, 'first_visibility', CHUNK);
  if (first.visibility !== 'READY' || !Number.isSafeInteger(first.staticCells) || first.staticCells < 1
      || !Number.isSafeInteger(first.replicaRevision) || first.replicaRevision < 0) {
    throw new Error('F0.6 first arrival did not retain a nonempty static-current chunk before scene eligibility');
  }
  for (const step of [2, 13]) assertNaturalClientIngress(diagnostics, step);
  const intervention = exactly(diagnostics, 10, 'site', SITE);
  const afterRestart = exactly(diagnostics, 11, 'site', SITE);
  const afterReturn = exactly(diagnostics, 14, 'site', SITE);
  if (![intervention, afterRestart, afterReturn].every(value => value.phase === 'CONFLICT')) {
    throw new Error('F0.6 intervention was reset, replayed, or hidden by restart/neutral return');
  }
  return Object.freeze({ chunk: CHUNK, replicaRevision: first.replicaRevision, staticCells: first.staticCells,
    intervention: SITE, restartContinuity: true, returnContinuity: true });
}

/** Rejects a stale coordinate, an unbounded scenario, or a pre-first-visibility intervention. */
export function assertF06ObserverNeutralityDeclaration(declaration) {
  const actions = declaration?.actions;
  if (declaration?.id !== SCENARIO || declaration?.isolation?.mode !== 'disposable_lite' || declaration?.server?.profile !== 'world'
      || declaration.restart?.mode !== 'graceful' || declaration.restart?.afterAction !== 10 || !Array.isArray(actions) || actions.length !== 14
      || actions[0]?.type !== 'fast_forward' || actions[0].ticks !== 24_000 || actions[0].timeoutMs !== 180_000
      || actions[1]?.type !== 'visit' || actions[1].dimension !== DIMENSION || !isDeepStrictEqual(actions[1].position, ARRIVAL)
      || actions[2]?.type !== 'wait_until_diagnostic' || actions[2].view !== 'first_visibility' || actions[2].id !== CHUNK
      || actions[2].expect?.visibility !== 'READY' || actions[3]?.type !== 'look' || !isDeepStrictEqual(actions[3].at, STATIC_ANCHOR)
      || actions[4]?.type !== 'assert_visible_block' || !isDeepStrictEqual(actions[4].position, STATIC_ANCHOR)
      || actions[8]?.type !== 'break' || !isDeepStrictEqual(actions[8].position, { x: 388, y: 64, z: -355 })
      || !conflict(actions[9]) || actions[10]?.type !== 'inspect' || actions[10].view !== 'site' || actions[10].id !== SITE
      || actions[11]?.type !== 'visit' || actions[11].dimension !== 'minecraft:overworld'
      || actions[12]?.type !== 'visit' || actions[12].dimension !== DIMENSION || !isDeepStrictEqual(actions[12].position, ARRIVAL) || !conflict(actions[13])) {
    throw new Error('F0.6 observer-neutrality declaration lacks a pre-ingress safe advance or exact first-visibility/intervention continuity');
  }
  return Object.freeze({ scenario: SCENARIO, chunk: CHUNK, arrival: ARRIVAL, staticAnchor: STATIC_ANCHOR, site: SITE });
}

function conflict(action) { return action?.view === 'site' && action.id === SITE && action.expect?.phase === 'CONFLICT'; }
function exactly(diagnostics, actionStep, kind, id) {
  const values = diagnostics.filter(entry => entry.actionStep === actionStep && entry.value?.kind === kind && entry.value.id === id).map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6 observer-neutrality lacks one ${kind}:${id} receipt at action ${actionStep}`);
  return values[0];
}
function assertNaturalClientIngress(diagnostics, step) {
  const values = diagnostics.filter(entry => entry.actionStep === step && entry.value?.kind === 'visit_ingress').map(entry => entry.value);
  if (values.length !== 1 || values[0].targetDimensionSeen !== true || values[0].targetChunkSeen !== true
      || values[0].finalClientDimension !== DIMENSION || !point(values[0].finalClientPosition)) {
    throw new Error('F0.6 observer-neutrality lacks a real client dimension/chunk arrival receipt');
  }
}
function observed(manifest) {
  return (manifest?.diagnostics ?? []).flatMap(entry => entry?.observed?.value ? [entry.observed]
    : entry?.assertion === undefined && entry?.value ? [entry] : []);
}
function point(value) { return value && Number.isSafeInteger(value.x) && Number.isSafeInteger(value.y) && Number.isSafeInteger(value.z); }
