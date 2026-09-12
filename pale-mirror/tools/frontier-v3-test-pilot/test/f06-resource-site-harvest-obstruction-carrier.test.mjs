import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF06ResourceSiteHarvestObstructionCarrier, assertF06ResourceSiteHarvestObstructionDeclaration } from '../src/f06-resource-site-harvest-obstruction-carrier.mjs';
import { validateScenario } from '../src/scenario.mjs';

const declaration = JSON.parse(await readFile(new URL('../scenarios/disposable-f06-resource-site-harvest-obstruction.json', import.meta.url)));
const observed = (actionStep, value) => ({ observed: { actionStep, value } });
function manifest() { return { status: 'ok', scenarioId: declaration.id, recovery: null, actions: declaration.actions.map(action => ({ action: structuredClone(action) })), diagnostics: [
  observed(2, { kind: 'process', id: 'job:site-harvest-1-wheat-field-1', status: 'ok', identity: { job: 'job:site-harvest-1-wheat-field-1' }, claims: { lease: { status: 'HOT' } }, conservation: { completedCropSlots: 1 }, result: { sitePhase: 'HARVESTING', intentStatus: 'RUNNING' } }),
  observed(4, { kind: 'process', id: 'job:site-harvest-1-wheat-field-1', status: 'ok', identity: { job: 'job:site-harvest-1-wheat-field-1' },
    conservation: { completedCropSlots: 1 }, result: { sitePhase: 'CONFLICT', intentStatus: 'CONFLICTED',
      obstruction: { position: { x: -342, y: 64, z: -346 }, reason: 'PLAYER_REMOVED_MANAGED_CELL', policy: 'TERMINAL_REPAIR_REQUIRED' } } })
] }; }
test('F0.6 obstruction declaration reaches a typed conflict after active natural harvest', () => {
  validateScenario(declaration); assert.equal(assertF06ResourceSiteHarvestObstructionDeclaration(declaration).site, 'site:1-wheat-field');
});
test('F0.6 obstruction carrier rejects a silent HARVESTING stall or a missing active owner', () => {
  assert.deepEqual(assertF06ResourceSiteHarvestObstructionCarrier({ declaration, manifest: manifest() }), { job: 'job:site-harvest-1-wheat-field-1', firstCrop: 1,
    conflict: 'site:1-wheat-field', reason: 'PLAYER_REMOVED_MANAGED_CELL', policy: 'TERMINAL_REPAIR_REQUIRED' });
  for (const mutate of [value => { value.diagnostics[1].observed.value.result.sitePhase = 'HARVESTING'; }, value => { value.diagnostics[0].observed.value.claims.lease = null; },
    value => { value.diagnostics[1].observed.value.result.obstruction.reason = 'RECOVERY_UNRESOLVED'; }]) {
    const value = manifest(); mutate(value); assert.throws(() => assertF06ResourceSiteHarvestObstructionCarrier({ declaration, manifest: value }), /F0\.6/);
  }
});
