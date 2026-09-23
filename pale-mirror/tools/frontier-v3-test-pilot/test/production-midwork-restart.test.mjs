import test from 'node:test';
import assert from 'node:assert/strict';
import { loadScenario, restartSegments } from '../src/scenario.mjs';

test('midwork restart samples each asserted subject at its own action before disconnect', async () => {
  const { scenario } = await loadScenario(new URL('../scenarios/disposable-materialized-production-midwork-restart.json', import.meta.url));
  for (const assertion of scenario.assertions) {
    const action = scenario.actions[assertion.after - 1];
    assert.ok(['inspect', 'wait_until_diagnostic'].includes(action.type));
    assert.equal(action.view, assertion.view);
    assert.equal(action.id, assertion.id);
  }
  const split = scenario.restart.afterAction;
  assert.equal(scenario.actions[split - 1].type, 'inspect');
  assert.equal(scenario.assertions.find(value => value.after === split).expect.productionStage, 'PROCESSING');
  assert.equal(scenario.assertions.find(value => value.after === split - 1).expect.jobActive, true);
  assert.ok(scenario.assertions.find(value => value.expect.orderStatus === 'FULFILLED').after > split);
  assert.notDeepEqual(scenario.setup.find(value => value.type === 'visit').position, { x: -385, y: 65, z: -326 });
  assert.ok(restartSegments(scenario));
});
