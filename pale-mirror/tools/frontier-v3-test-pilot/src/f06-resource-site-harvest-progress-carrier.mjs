import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f06_resource_site_harvest_progress';
const DIMENSION = 'pale_mirror:frontier_graybox';
const JOB = 'job:site-harvest-1-wheat-field-1';
const SITE = 'site:1-wheat-field';
const FIRST_CROP_VISIT = Object.freeze({ x: -333, y: 65, z: -355 });

/**
 * F0.6 harvest receipt: the ordinary demand path must create one exact HOT farmer lease,
 * commit one observed crop, release it when the visitor leaves, then retain the same job and
 * progress when that visitor returns.  It deliberately does not accept a generic HARVESTING
 * phase as evidence of field work.
 */
export function assertF06ResourceSiteHarvestProgressCarrier({ declaration, manifest }) {
  assertF06ResourceSiteHarvestProgressDeclaration(declaration);
  if (manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO || manifest.recovery != null
      || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length) {
    throw new Error('F0.6 resource-site harvest manifest lacks its declared ordinary lifecycle');
  }
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) throw new Error('F0.6 resource-site harvest manifest substituted an action');
  });
  const diagnostics = observed(manifest);
  const first = exactly(diagnostics, 2, 'process', JOB);
  const released = exactly(diagnostics, 6, 'process', JOB);
  const returned = exactly(diagnostics, 8, 'process', JOB);
  requireFirstCrop(first);
  if (released.claims?.lease !== null || completed(released) !== 1 || released.result?.intentStatus !== 'RUNNING') {
    throw new Error('F0.6 resource-site harvest did not release the exact progressed job on departure');
  }
  if (returned.claims?.lease?.status !== 'HOT' || returned.claims.lease.members !== 1 || completed(returned) < 1
      || returned.result?.intentStatus !== 'RUNNING' || returned.identity?.job !== first.identity?.job
      || returned.identity?.worker !== first.identity?.worker || returned.claims?.intent !== first.claims?.intent) {
    throw new Error('F0.6 resource-site harvest return reset, replayed, or replaced the exact farmer custody');
  }
  const block = exactly(diagnostics, 3, 'block', `${SITE}:firstCrop`);
  if (block.block !== 'minecraft:air') throw new Error('F0.6 resource-site harvest lacks the observed first-crop world effect');
  return Object.freeze({ job: first.identity.job, worker: first.identity.worker, firstCrop: completed(first),
    returnedCropFloor: completed(returned), released: true, physicalCropEffect: true });
}

/** The literal visit is bound to the fixture's first crop, not a broad settlement demand radius. */
export function assertF06ResourceSiteHarvestProgressDeclaration(declaration) {
  const actions = declaration?.actions;
  if (declaration?.id !== SCENARIO || declaration?.isolation?.mode !== 'disposable_lite'
      || declaration?.server?.profile !== 'resource-site-harvest' || !Array.isArray(actions) || actions.length !== 8
      || actions[0]?.type !== 'visit' || actions[0].dimension !== DIMENSION || !isDeepStrictEqual(actions[0].position, FIRST_CROP_VISIT)
      || !processExpectation(actions[1], { lease: 'HOT', completed: 1 }) || actions[2]?.type !== 'wait_until_block'
      || actions[2].position?.diagnostic?.view !== 'site' || actions[2].position.diagnostic.id !== SITE
      || actions[2].position.diagnostic.field !== 'firstCrop' || actions[2].block !== 'minecraft:air'
      || actions[4]?.type !== 'visit' || actions[4].dimension !== 'minecraft:overworld' || !processExpectation(actions[5], { lease: null, completed: 1 })
      || actions[6]?.type !== 'visit' || actions[6].dimension !== DIMENSION || !isDeepStrictEqual(actions[6].position, FIRST_CROP_VISIT)
      || !processExpectation(actions[7], { lease: 'HOT', completed: 1 })) {
    throw new Error('F0.6 resource-site harvest declaration lacks first-crop demand/effect/return continuity');
  }
  return Object.freeze({ scenario: SCENARIO, job: JOB, site: SITE, firstCropVisit: FIRST_CROP_VISIT });
}

function processExpectation(action, { lease, completed: cropCount }) {
  const expected = action?.expect;
  return action?.type === 'wait_until_diagnostic' && action.view === 'process' && action.id === JOB
    && expected?.conservation?.completedCropSlots === cropCount
    && (lease === null ? expected?.claims?.lease === null : expected?.claims?.lease?.status === lease);
}
function requireFirstCrop(value) {
  if (value.claims?.lease?.status !== 'HOT' || value.claims.lease.members !== 1 || completed(value) !== 1
      || value.conservation?.pendingCropSlot !== -1 || value.result?.sitePhase !== 'HARVESTING'
      || value.result?.intentStatus !== 'RUNNING' || value.result?.complete !== false
      || value.identity?.job !== JOB || typeof value.identity?.worker !== 'string' || !value.identity.worker) {
    throw new Error('F0.6 resource-site harvest did not commit its first exact HOT crop boundary');
  }
}
function completed(value) { return value?.conservation?.completedCropSlots; }
function exactly(diagnostics, actionStep, kind, id) {
  const values = diagnostics.filter(entry => entry.actionStep === actionStep && entry.value?.kind === kind && entry.value.id === id).map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6 resource-site harvest lacks one ${kind}:${id} receipt at action ${actionStep}`);
  return values[0];
}
function observed(manifest) {
  return (manifest?.diagnostics ?? []).flatMap(entry => entry?.observed?.value ? [entry.observed]
    : entry?.assertion === undefined && entry?.value ? [entry] : []);
}
