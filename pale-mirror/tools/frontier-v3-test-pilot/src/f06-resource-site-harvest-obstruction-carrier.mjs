import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f06_resource_site_harvest_obstruction';
const JOB = 'job:site-harvest-1-wheat-field-1';
const SITE = 'site:1-wheat-field';

/** A player-caused next-crop loss must retire the active field work as typed ownership conflict. */
export function assertF06ResourceSiteHarvestObstructionCarrier({ declaration, manifest }) {
  assertF06ResourceSiteHarvestObstructionDeclaration(declaration);
  if (manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO || manifest.recovery != null
      || manifest.actions?.length !== declaration.actions.length) throw new Error('F0.6 harvest obstruction manifest is incomplete');
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) throw new Error('F0.6 harvest obstruction substituted an action');
  });
  const facts = observed(manifest);
  const active = exactly(facts, 2, 'process', JOB);
  const conflict = exactly(facts, 4, 'site', SITE);
  if (active.claims?.lease?.status !== 'HOT' || active.conservation?.completedCropSlots !== 1
      || active.result?.sitePhase !== 'HARVESTING' || active.result?.intentStatus !== 'RUNNING'
      || conflict.phase !== 'CONFLICT') throw new Error('F0.6 harvest obstruction did not replace active work with its typed owner result');
  return Object.freeze({ job: active.identity?.job, firstCrop: 1, conflict: SITE });
}

export function assertF06ResourceSiteHarvestObstructionDeclaration(declaration) {
  const actions = declaration?.actions;
  if (declaration?.id !== SCENARIO || declaration?.server?.profile !== 'resource-site-harvest' || !Array.isArray(actions) || actions.length !== 5
      || actions[0]?.type !== 'visit' || actions[1]?.type !== 'wait_until_diagnostic' || actions[1]?.view !== 'process'
      || actions[1]?.id !== JOB || actions[2]?.type !== 'break' || !isDeepStrictEqual(actions[2]?.position, { x: -342, y: 64, z: -346 })
      || actions[3]?.type !== 'wait_until_diagnostic' || actions[3]?.view !== 'site' || actions[3]?.id !== SITE
      || actions[3]?.expect?.phase !== 'CONFLICT' || actions[4]?.type !== 'inspect' || actions[4]?.view !== 'process' || actions[4]?.id !== JOB) {
    throw new Error('F0.6 harvest obstruction declaration lacks an active-job-to-conflict boundary');
  }
  return Object.freeze({ scenario: SCENARIO, job: JOB, site: SITE });
}

function exactly(values, step, kind, id) {
  const matches = values.filter(entry => entry.actionStep === step && entry.value?.kind === kind && entry.value.id === id).map(entry => entry.value);
  if (matches.length !== 1 || matches[0].status !== 'ok') throw new Error(`F0.6 harvest obstruction lacks one ${kind}:${id} receipt at action ${step}`);
  return matches[0];
}
function observed(manifest) {
  const entries = manifest?.diagnostics ?? [];
  const asserted = entries.flatMap(entry => entry?.observed?.value ? [entry.observed] : []);
  const raw = entries.flatMap(entry => entry?.assertion === undefined && entry?.value ? [entry] : []);
  return asserted.length === 0 ? raw : [...asserted, ...raw.filter(entry => !asserted.some(receipt => receipt.actionStep === entry.actionStep
    && receipt.value?.kind === entry.value?.kind && receipt.value?.id === entry.value?.id))];
}
