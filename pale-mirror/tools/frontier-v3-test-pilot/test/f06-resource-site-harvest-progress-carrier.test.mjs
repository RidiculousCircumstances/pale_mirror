import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF06ResourceSiteHarvestProgressCarrier, assertF06ResourceSiteHarvestProgressDeclaration } from '../src/f06-resource-site-harvest-progress-carrier.mjs';
import { buildF06ResourceSiteHarvestProgressReceipt, isolatedScenarioSupervisorInvocation, preflightF06ResourceSiteHarvestProgressCarrier } from '../src/run-f06-resource-site-harvest-progress-carrier.mjs';
import { validateScenario } from '../src/scenario.mjs';

const declaration = JSON.parse(await readFile(new URL('../scenarios/disposable-f06-resource-site-harvest-progress.json', import.meta.url)));
function observed(actionStep, value) { return { observed: { actionStep, value } }; }
function ingress(dimension) { return { kind: 'visit_ingress', id: 'ordinary_visit', targetDimensionSeen: true, targetChunkSeen: true,
  finalClientDimension: dimension, finalClientPosition: { x: -333, y: 65, z: -355 } }; }
function process({ lease, completed }) { return { kind: 'process', id: 'job:site-harvest-1-wheat-field-1', status: 'ok',
  identity: { job: 'job:site-harvest-1-wheat-field-1', worker: 'resident:1-3' }, claims: { intent: 'intent:site-harvest-1-wheat-field-1', lease },
  conservation: { completedCropSlots: completed, pendingCropSlot: -1 }, result: { sitePhase: 'HARVESTING', intentStatus: 'RUNNING', complete: false } }; }
function manifest() { return { status: 'ok', scenarioId: declaration.id, actions: declaration.actions.map(action => ({ action: structuredClone(action) })), diagnostics: [
  observed(1, ingress('pale_mirror:frontier_graybox')),
  observed(2, process({ lease: { status: 'HOT', members: 1 }, completed: 1 })),
  observed(3, { kind: 'block', id: 'site:1-wheat-field:firstCrop', status: 'ok', block: 'minecraft:air' }),
  observed(5, ingress('minecraft:overworld')),
  observed(6, process({ lease: null, completed: 1 })),
  observed(7, ingress('pale_mirror:frontier_graybox')),
  observed(8, process({ lease: { status: 'HOT', members: 1 }, completed: 1 }))
] }; }

test('F0.6 declaration binds ordinary farmer demand to the first retained crop, not a manufactured result', () => {
  validateScenario(declaration);
  assert.deepEqual(assertF06ResourceSiteHarvestProgressDeclaration(declaration).firstCropVisit, { x: -333, y: 65, z: -355 });
  const stale = structuredClone(declaration); stale.actions[0].position.z = -354;
  assert.throws(() => assertF06ResourceSiteHarvestProgressDeclaration(stale), /first-crop/);
});

test('F0.6 carrier requires physical first-crop progress and preserves exact farmer/job custody over departure and return', () => {
  assert.deepEqual(assertF06ResourceSiteHarvestProgressCarrier({ declaration, manifest: manifest() }), {
    job: 'job:site-harvest-1-wheat-field-1', worker: 'resident:1-3', firstCrop: 1, returnedCropFloor: 1,
    released: true, physicalCropEffect: true
  });
});

test('F0.6 carrier rejects a silent harvest phase, synthetic crop receipt, reset return, and wrong ingress', () => {
  for (const mutate of [
    value => { value.diagnostics[1].observed.value.conservation.completedCropSlots = 0; },
    value => { value.diagnostics[2].observed.value.block = 'minecraft:wheat'; },
    value => { value.diagnostics[6].observed.value.conservation.completedCropSlots = 0; },
    value => { value.diagnostics[5].observed.value.finalClientPosition = null; },
    value => { value.diagnostics.push(structuredClone(value.diagnostics[1])); }
  ]) {
    const value = manifest(); mutate(value);
    assert.throws(() => assertF06ResourceSiteHarvestProgressCarrier({ declaration, manifest: value }), /F0\.6/);
  }
});

test('F0.6 native preflight binds the exact harvest declaration and private process custody', async () => {
  const source = await readFile(new URL('../scenarios/disposable-f06-resource-site-harvest-progress.json', import.meta.url));
  const terminal = Buffer.from(`${JSON.stringify(manifest())}\n`);
  const declarationSha256 = (await import('node:crypto')).createHash('sha256').update(source).digest('hex');
  const validated = preflightF06ResourceSiteHarvestProgressCarrier({ declarationSource: source, declarationSha256, manifestSource: terminal });
  assert.equal(buildF06ResourceSiteHarvestProgressReceipt(validated).finalManifest.sha256,
    (await import('node:crypto')).createHash('sha256').update(terminal).digest('hex'));
  const invocation = isolatedScenarioSupervisorInvocation({ scenarioPath: '/checkout/scenario.json', manifestPath: '/checkout/manifest.json',
    root: '/checkout/evidence', outerAttempt: '00000000-0000-0000-0000-000000000066' });
  assert.equal(invocation.options.env.FRONTIER_V3_NATIVE_PROCESS_ROOT, '/checkout/evidence/process');
});
