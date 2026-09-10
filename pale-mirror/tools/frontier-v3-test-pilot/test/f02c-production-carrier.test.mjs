import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { assertF02cProductionCarrier } from '../src/f02c-production-carrier.mjs';
import { validateScenario } from '../src/scenario.mjs';

const scenarioPath = resolve('tools/frontier-v3-test-pilot/scenarios/disposable-production-carrier-restart.json');
const declaration = async () => JSON.parse(await readFile(scenarioPath, 'utf8'));

test('F0.2C production carrier is one declarative native-only history', async () => {
  const value = await declaration();
  validateScenario(value);
  assert.deepEqual(assertF02cProductionCarrier(value), {
    cause: 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19',
    scene: 'assault:development-settlement-assault', restartAfterAction: 3
  });
});

test('F0.2C production carrier oracle rejects cause, demand, HOT, restart and terminal mutations', async () => {
  for (const mutate of [
    value => { value.actions[0].expect.cellStatus = 'REALIZED'; },
    value => { value.setup.push({ type: 'visit' }); },
    value => { value.actions[2].expect.epoch = 5; },
    value => { value.restart.afterAction = 2; },
    value => { value.actions[5].expect.cellStatus = 'PENDING'; }
  ]) {
    const value = await declaration(); mutate(value);
    assert.throws(() => assertF02cProductionCarrier(value), /F0\.2C production carrier/);
  }
  const malformed = await declaration();
  malformed.actions[4].timeoutMs = 120_001;
  assert.throws(() => validateScenario(malformed), /wait_until_block/);
});
