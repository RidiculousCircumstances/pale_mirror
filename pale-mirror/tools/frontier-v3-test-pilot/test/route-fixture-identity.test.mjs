import assert from 'node:assert/strict';
import { readdir, readFile } from 'node:fs/promises';
import test from 'node:test';
import { validateScenario } from '../src/scenario.mjs';

test('assembly scenario declares the current three-person shipment', async () => {
  const text = await readFile(new URL('../scenarios/disposable-hot-operation-assembly.json', import.meta.url), 'utf8');
  const scenario = JSON.parse(text);
  validateScenario(scenario);
  assert.equal(scenario.isolation.seed, 41);
  assert.doesNotMatch(text, /operation:supply-1-2\b/);
  const check = scenario.setup.find(step => step.type === 'assert_fixture').checks[0];
  assert.equal(check.id, 'operation:supply-1-12');
  assert.equal(check.expect.assemblyMembers, 3);
});

test('abrupt released-cargo recovery observes real removal and requires post-restart custody and cleanup', async () => {
  const value = JSON.parse(await readFile(new URL('../scenarios/disposable-released-cargo-destroyed-abrupt.json', import.meta.url), 'utf8'));
  validateScenario(value);
  assert.equal(value.restart.mode, 'abrupt');
  const split = value.restart.afterAction;
  assert.equal(value.actions[split - 1].type, 'attack_nearest_entity');
  assert.equal(value.actions[split - 1].requireRemoval, true);
  assert.ok(value.actions.slice(split).some(check => check.type === 'wait_until_diagnostic'
    && check.view === 'resource' && check.expect.quantity === 64));
  assert.equal(value.actions.at(-1).expect.cargoCleanupPending, false);
});

test('destroyed cargo scenario requires removal, not merely exhausted attacks', async () => {
  const value = JSON.parse(await readFile(new URL('../scenarios/disposable-released-cargo-destroyed-restart.json', import.meta.url), 'utf8'));
  const attack = value.actions.find(action => action.type === 'attack_nearest_entity');
  assert.equal(attack.requireRemoval, true);
  validateScenario(value);
  for (const invalid of ['true', 1, null, {}]) {
    attack.requireRemoval = invalid;
    assert.throws(() => validateScenario(value), /attack_nearest_entity/);
  }
});

test('route return uses observer cells checked against the real graybox plan', async () => {
  const scenario = JSON.parse(await readFile(new URL('../scenarios/disposable-route-scene-return.json', import.meta.url), 'utf8'));
  assert.ok(scenario.restart.resumeSetup.some(step => step.command === '/save-all flush'));
  assert.equal(scenario.actions.at(-1).expect.cargoPendingRetirements, 0,
    'a successor scene alone cannot establish cleanup of earlier carrier projections');
  const entrance = { x: -354, y: 64, z: -352 };
  assert.deepEqual(scenario.setup.find(step => step.type === 'visit').position, entrance);
  const returnPoint = { x: -354, y: 64, z: -314 };
  assert.deepEqual(scenario.restart.resumeSetup.find(step => step.type === 'visit').position, returnPoint);
  assert.deepEqual(scenario.actions.find(step => step.type === 'visit').position, { x: 0, y: 64, z: 0 });
  assert.deepEqual(scenario.actions.filter(step => step.type === 'visit').slice(1).map(step => step.position), [returnPoint, returnPoint]);
  const withVisit = structuredClone(scenario);
  withVisit.actions[0] = { type: 'visit_operation', operationId: 'operation:supply-1-12',
    dimension: 'pale_mirror:frontier_graybox', anchor: 'travelCargo', offset: { x: 10, y: 0, z: -10 }, settleMs: 500, timeoutMs: 30000 };
  validateScenario(withVisit);
  withVisit.actions[0].anchor = 'unknown';
  assert.throws(() => validateScenario(withVisit), /visit_operation/);
});

test('route/scout scenarios declare the shipment checked by RouteSceneFixtureIdentityTest', async () => {
  const root = new URL('../scenarios/', import.meta.url);
  const profiles = new Set(['scene-return', 'hot-scout-sighting', 'hot-scout-intercept']);
  let checked = 0;
  for (const file of await readdir(root)) {
    if (!file.endsWith('.json')) continue;
    const text = await readFile(new URL(file, root), 'utf8');
    const scenario = JSON.parse(text);
    if (!profiles.has(scenario.server?.profile)) continue;
    validateScenario(scenario);
    assert.equal(scenario.isolation.seed, 41, file);
    assert.ok(text.includes('operation:supply-1-12'), file);
    assert.doesNotMatch(text, /(?:operation|cargo|contract):supply-1-2\b/, file);
    checked++;
  }
  assert.equal(checked, 13);
});
