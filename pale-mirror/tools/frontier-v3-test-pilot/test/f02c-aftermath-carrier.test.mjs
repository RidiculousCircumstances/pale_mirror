import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF02cAftermathCarrier } from '../src/f02c-aftermath-carrier.mjs';

const selector = 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19';
const position = { x: -360, y: 64, z: -340 };
const pending = (step, revision) => ({ kind: 'aftermath', id: selector, pilotActionStep: step, status: 'ok', aftermathId: 'aftermath:development-settlement-assault-bomber-4',
  cause: selector, epoch: 4, expectedOwner: 'structure:1-hall', expectedPart: 'FOUNDATION', expectedMaterial: 'HALL', provenance: 'captive-bomber-strike:assault:development-settlement-assault',
  eventAt: 400, revision, terminal: false, nextStatus: 'PENDING', cellStatus: 'PENDING', authorityRevision: -1, cursor: 0, observedAt: null, position });
const realized = { ...pending(6, 45), terminal: true, nextStatus: 'NONE', cellStatus: 'REALIZED', authorityRevision: 17, cursor: 1, observedAt: 445 };
const clearDeclaration = JSON.parse(await readFile(new URL('../scenarios/disposable-cold-bomber-aftermath-restart.json', import.meta.url), 'utf8'));
const evidence = () => ({ clearDeclaration: structuredClone(clearDeclaration), clearBefore: { diagnostics: [{ value: pending(1, 41) }, { value: pending(2, 42) }] },
  clearAfter: { recovery: { mode: 'graceful', splitAfterAction: 2 }, actions: [{ action: { type: 'visit' } }, { action: { type: 'wait_until_block', block: 'minecraft:air', position } }],
    diagnostics: [{ value: pending(3, 43) }, { value: structuredClone(realized) }] } });

test('F0.2C native carrier binds pre-restart, recovered-before-visit and terminal owner history', () => {
  assert.deepEqual(assertF02cAftermathCarrier(evidence()), { aftermathId: 'aftermath:development-settlement-assault-bomber-4', cause: selector, position,
    owner: 'structure:1-hall', semanticPart: 'FOUNDATION', revision: 45 });
});
test('F0.2C carrier rejects owner, part, revision, ordering, replay, unrelated-AIR and HOT-previsit substitutions', () => {
  for (const mutate of [
    value => { value.clearAfter.diagnostics[0].value.aftermathId = 'aftermath:replacement'; },
    value => { value.clearAfter.diagnostics[0].value.expectedOwner = 'structure:other-hall'; },
    value => { value.clearAfter.diagnostics[0].value.expectedPart = 'ROOF'; },
    value => { value.clearAfter.diagnostics[0].value.authorityRevision = 1; },
    value => { value.clearAfter.diagnostics[1].value.authorityRevision = -1; },
    value => { value.clearAfter.diagnostics[1].value.revision = 43; },
    value => { value.clearAfter.diagnostics[1].value.cellStatus = 'CONFLICTED'; },
    value => { value.clearAfter.diagnostics[1].value.observedAt = 399; },
    value => { value.clearAfter.recovery.splitAfterAction = 1; },
    value => { value.clearAfter.actions[1].action.position = { x: -359, y: 64, z: -340 }; },
    value => { value.clearDeclaration.actions.unshift({ type: 'visit' }); },
    value => { value.clearDeclaration.restart.resumeSetup.push({ type: 'visit' }); }
  ]) { const value = evidence(); mutate(value); assert.throws(() => assertF02cAftermathCarrier(value), /F0\.2C carrier/); }
});
