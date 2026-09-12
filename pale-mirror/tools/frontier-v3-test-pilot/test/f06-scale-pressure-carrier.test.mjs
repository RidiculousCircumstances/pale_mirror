import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF06ScalePressureCarrier } from '../src/f06-scale-pressure-carrier.mjs';
import { validateScenario } from '../src/scenario.mjs';

const declaration = JSON.parse(await readFile(new URL('../scenarios/disposable-settlement-assault-scale-jfr.json', import.meta.url)));
const steps = [3, 5, 7, 9, 11];
function performance(bytes = 95_925) { return { status: 'ok', droppedAttributions: 0,
  queues: [{ maxDepth: 1, maxLagTicks: 0 }], frontier: { hotSceneLeases: 2, sceneActorBindings: 29, managedActorBindings: 42,
    recoveryCurrent: 44, recoveryTombstones: 1, checkpointBytes: bytes, deferredAftermath: 0 } }; }
function manifest() { return { status: 'ok', actions: declaration.actions.map(action => ({ action: structuredClone(action) })),
  diagnostics: steps.map(step => ({ assertion: { after: step, view: 'performance' }, observed: { value: performance() } })) }; }

test('F0.6 scale declaration retains five correlated HOT boundaries across the JFR interval', () => {
  validateScenario(declaration);
  assert.deepEqual(declaration.actions.map(action => action.type), ['wait_until_diagnostic', 'wait_until_diagnostic', 'wait_until_diagnostic', 'wait', 'wait_until_diagnostic', 'wait', 'wait_until_diagnostic', 'wait', 'wait_until_diagnostic', 'wait', 'wait_until_diagnostic']);
  assert.deepEqual(declaration.assertions.map(value => value.after), steps);
});
test('F0.6 scale carrier rejects lost HOT custody and monotonic retained-state growth', () => {
  assert.equal(assertF06ScalePressureCarrier({ declaration, manifest: manifest() }).samples, 5);
  const cold = manifest(); cold.diagnostics[3].observed.value.frontier.hotSceneLeases = 1;
  assert.throws(() => assertF06ScalePressureCarrier({ declaration, manifest: cold }), /HOT custody/);
  const growth = manifest(); growth.diagnostics.forEach((entry, index) => { entry.observed.value.frontier.recoveryCurrent = 44 + index; entry.observed.value.frontier.checkpointBytes = 95_925 + index; });
  assert.throws(() => assertF06ScalePressureCarrier({ declaration, manifest: growth }), /grew monotonically/);
});
