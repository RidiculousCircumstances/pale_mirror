import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f06_observer_neutrality';
const DIMENSION = 'pale_mirror:frontier_graybox';
const CHUNK = '24,-23';
const ARRIVAL = Object.freeze({ x: 388, y: 65, z: -355 });
const STATIC_ANCHOR = Object.freeze({ x: 380, y: 64, z: -340 });
const SITE = 'site:4-wheat-field';
const COLD_JOB = 'job:site-harvest-4-wheat-field-1';
const LAST_CROP = Object.freeze({ diagnostic: Object.freeze({ view: 'site', id: SITE, field: 'lastCrop' }) });

/**
 * F0.6 receipt predicate. It consumes only action-bound pilot/server observations: a natural
 * client visit makes a static chunk current before it can be shown, and an ordinary player
 * intervention remains a retained conflict through restart and a later neutral return.
 */
export function assertF06ObserverNeutralityCarrier({ declaration, manifest }) {
  assertF06ObserverNeutralityDeclaration(declaration);
  if (manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO || manifest.recovery?.mode !== 'graceful'
      || manifest.recovery?.splitAfterAction !== 11 || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length) {
    throw new Error('F0.6 observer-neutrality manifest lacks its declared persistent lifecycle');
  }
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) throw new Error('F0.6 observer-neutrality manifest substituted an action');
  });
  const diagnostics = observed(manifest);
  const cold = exactly(diagnostics, 2, 'site', SITE);
  if (cold.phase !== 'HARVESTING' || cold.growthStage !== 7 || cold.activeWork !== COLD_JOB) {
    throw new Error('F0.6 COLD progress did not retain the exact harvest job before the first field visit');
  }
  const first = exactly(diagnostics, 4, 'first_visibility', CHUNK);
  if (first.visibility !== 'READY' || !Number.isSafeInteger(first.staticCells) || first.staticCells < 1
      || !Number.isSafeInteger(first.replicaRevision) || first.replicaRevision < 0) {
    throw new Error('F0.6 first arrival did not retain a nonempty static-current chunk before scene eligibility');
  }
  const intervention = exactly(diagnostics, 11, 'site', SITE);
  const afterRestart = exactly(diagnostics, 12, 'site', SITE);
  const afterReturn = exactly(diagnostics, 15, 'site', SITE);
  if (![intervention, afterRestart, afterReturn].every(value => value.phase === 'CONFLICT')) {
    throw new Error('F0.6 intervention was reset, replayed, or hidden by restart/neutral return');
  }
  return Object.freeze({ chunk: CHUNK, coldGrowthStage: cold.growthStage, coldJob: COLD_JOB,
    replicaRevision: first.replicaRevision, staticCells: first.staticCells,
    intervention: SITE, restartContinuity: true, returnContinuity: true });
}

/** Rejects a stale coordinate, an unbounded scenario, or a pre-first-visibility intervention. */
export function assertF06ObserverNeutralityDeclaration(declaration) {
  const actions = declaration?.actions;
  if (declaration?.id !== SCENARIO || declaration?.isolation?.mode !== 'disposable_lite' || declaration?.server?.profile !== 'world'
      || declaration.restart?.mode !== 'graceful' || declaration.restart?.afterAction !== 11 || !Array.isArray(actions) || actions.length !== 15
      || actions[0]?.type !== 'fast_forward' || actions[0].ticks !== 24_000 || actions[0].timeoutMs !== 180_000
      || !coldGrowth(actions[1]) || actions[2]?.type !== 'visit' || actions[2].dimension !== DIMENSION || !isDeepStrictEqual(actions[2].position, ARRIVAL)
      || actions[3]?.type !== 'wait_until_diagnostic' || actions[3].view !== 'first_visibility' || actions[3].id !== CHUNK
      || actions[3].expect?.visibility !== 'READY' || actions[4]?.type !== 'look' || !isDeepStrictEqual(actions[4].at, STATIC_ANCHOR)
      || actions[5]?.type !== 'assert_visible_block' || !isDeepStrictEqual(actions[5].position, STATIC_ANCHOR)
      || actions[6]?.type !== 'wait_until_block' || !isDeepStrictEqual(actions[6].position, LAST_CROP) || actions[6].block !== 'minecraft:wheat'
      || actions[7]?.type !== 'look' || !isDeepStrictEqual(actions[7].at, LAST_CROP)
      || actions[8]?.type !== 'assert_visible_block' || !isDeepStrictEqual(actions[8].position, LAST_CROP)
      || actions[9]?.type !== 'break' || !isDeepStrictEqual(actions[9].position, LAST_CROP)
      || !conflict(actions[10]) || actions[11]?.type !== 'inspect' || actions[11].view !== 'site' || actions[11].id !== SITE
      || actions[12]?.type !== 'visit' || actions[12].dimension !== 'minecraft:overworld'
      || actions[13]?.type !== 'visit' || actions[13].dimension !== DIMENSION || !isDeepStrictEqual(actions[13].position, ARRIVAL)
      || actions[14]?.type !== 'inspect' || actions[14].view !== 'site' || actions[14].id !== SITE) {
    throw new Error('F0.6 observer-neutrality declaration lacks a pre-ingress safe advance, exact field anchor, or first-visibility/intervention continuity');
  }
  return Object.freeze({ scenario: SCENARIO, chunk: CHUNK, arrival: ARRIVAL, staticAnchor: STATIC_ANCHOR, site: SITE });
}

function conflict(action) { return action?.view === 'site' && action.id === SITE && action.expect?.phase === 'CONFLICT'; }
function coldGrowth(action) { return action?.type === 'wait_until_diagnostic' && action.view === 'site' && action.id === SITE
  && action.expect?.phase === 'HARVESTING' && action.expect?.growthStage === 7 && action.timeoutMs === 30_000; }
function exactly(diagnostics, actionStep, kind, id) {
  const values = diagnostics.filter(entry => entry.actionStep === actionStep && entry.value?.kind === kind && entry.value.id === id).map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6 observer-neutrality lacks one ${kind}:${id} receipt at action ${actionStep}`);
  return values[0];
}
function observed(manifest) {
  const entries = manifest?.diagnostics ?? [];
  const asserted = entries.flatMap(entry => entry?.observed?.value ? [entry.observed] : []);
  const raw = entries.flatMap(entry => entry?.assertion === undefined && entry?.value ? [entry] : []);
  // Native manifests retain their assertion-bound facts and the complete raw diagnostic
  // appendix.  Keep raw facts that are not assertion receipts (such as client ingress), but do
  // not count the appendix copy of an assertion a second time.
  return asserted.length === 0 ? raw : [...asserted, ...raw.filter(entry => !asserted.some(receipt => receipt.actionStep === entry.actionStep
    && receipt.value?.kind === entry.value?.kind && receipt.value?.id === entry.value?.id))];
}
