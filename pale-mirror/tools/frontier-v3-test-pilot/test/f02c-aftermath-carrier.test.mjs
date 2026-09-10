import assert from 'node:assert/strict';
import test from 'node:test';
import { assertF02cAftermathCarrier } from '../src/f02c-aftermath-carrier.mjs';

const selector = 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19';
const position = { x: -360, y: 64, z: -340 };
const pending = (step) => ({ kind: 'aftermath', id: selector, pilotActionStep: step, status: 'ok', aftermathId: 'aftermath:development-settlement-assault-bomber-4',
  cause: selector, epoch: 4, expectedMaterial: 'HALL', provenance: 'captive-bomber-strike:assault:development-settlement-assault', terminal: false, nextStatus: 'PENDING', cellStatus: 'PENDING', observedAt: null, position });
const realized = { ...pending(3), terminal: true, nextStatus: 'NONE', cellStatus: 'REALIZED', observedAt: 445 };
const clearDeclaration = { id: 'disposable_cold_bomber_aftermath_restart', server: { profile: 'cold-bomber-aftermath' }, setup: [], actions: [
  { type: 'wait_until_diagnostic', id: selector, expect: { cause: selector, epoch: 4, expectedMaterial: 'HALL', provenance: 'captive-bomber-strike:assault:development-settlement-assault', cellStatus: 'PENDING' } }], restart: { mode: 'graceful', afterAction: 2 } };
const constructiveDeclaration = { id: 'disposable_materialized_production_route_blocked', actions: [
  { type: 'wait_until_diagnostic' }, { type: 'place', item: 'minecraft:gray_concrete' }, { expect: { taskStatus: 'BLOCKED' } },
  { type: 'fast_forward' }, { id: 'settlement:2', expect: { food: { status: 'SECURE' } } }] };
const evidence = () => ({ clearDeclaration: structuredClone(clearDeclaration), clearBefore: { diagnostics: [{ value: pending(1) }, { value: pending(2) }] },
  clearAfter: { recovery: { mode: 'graceful', splitAfterAction: 2 }, actions: [{ action: { type: 'visit' } }, { action: { type: 'wait_until_block', block: 'minecraft:air', position } }], diagnostics: [{ value: realized }] },
  constructiveDeclaration: structuredClone(constructiveDeclaration), constructiveBefore: { actions: [{ action: { type: 'place', item: 'minecraft:gray_concrete' } }], diagnostics: [{ value: { kind: 'scene', id: 'job:production-development-input-theft', pilotActionStep: 1, sceneKind: 'PRODUCTION_WORK', leaseStatus: 'HOT' } }] },
  constructiveAfter: { diagnostics: [
    { value: { kind: 'market_order', id: 'order:development-production-input-theft', pilotActionStep: 3, orderStatus: 'CANCELLED', jobActive: false, reservationActive: false, taskStatus: 'BLOCKED' } },
    { value: { kind: 'settlement', id: 'settlement:2', pilotActionStep: 5, food: { status: 'SECURE', fulfilled: 8 } } }
  ] } });

test('F0.2C native carrier binds exact COLD cause, durable receipt, Minecraft result and constructive conflict', () => {
  assert.deepEqual(assertF02cAftermathCarrier(evidence()), { aftermathId: 'aftermath:development-settlement-assault-bomber-4', cause: selector, position, constructive: 'BLOCKED', unrelated: 'SECURE' });
});
test('F0.2C carrier rejects wrong, missing, reordered, postcondition and HOT-previsit substitutions', () => {
  for (const mutate of [
    value => { value.clearBefore.diagnostics[0].value.cause = 'cause:development-settlement-assault-epoch-5-attacker-bioform-west-19'; },
    value => { value.clearBefore.diagnostics.pop(); },
    value => { value.clearBefore.diagnostics[1].value.aftermathId = 'aftermath:replacement'; },
    value => { value.clearAfter.actions[1].action.block = 'minecraft:stone'; },
    value => { value.clearDeclaration.setup.push({ type: 'visit' }); }
  ]) { const value = evidence(); mutate(value); assert.throws(() => assertF02cAftermathCarrier(value), /F0\.2C carrier/); }
});
