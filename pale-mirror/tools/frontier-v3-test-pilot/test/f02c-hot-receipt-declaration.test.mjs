import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import test from 'node:test';
import { assertF02cHotReceiptDeclaration } from '../src/f02c-hot-receipt-declaration.mjs';

const scenarioPath = resolve(import.meta.dirname, '../scenarios/disposable-settlement-assault-restart.json');
async function declaration() { return JSON.parse(await readFile(scenarioPath, 'utf8')); }

test('HOT restart declaration retains the exact causal receipt across restart and COLD release', async () => {
  const checked = assertF02cHotReceiptDeclaration(await declaration());
  assert.match(checked.intent, /^intent:scene-strike-assault-[a-f0-9]{64}$/);
  assert.equal(checked.receipt, `observation:${checked.intent.replace(':', '-')}`);
});

test('HOT restart declaration rejects replaced, old, reordered, and self-consistent foreign receipts', async () => {
  for (const mutate of [
    value => { value.assertions[0].expect.strikeReceipt = 'observation:intent:foreign'; },
    value => { value.assertions[1].expect.nextStrikeEpoch = 0; },
    value => { [value.assertions[0], value.assertions[1]] = [value.assertions[1], value.assertions[0]]; value.assertions[1].after = 4; },
    value => { const expected = value.assertions[2].expect; expected.strikeIntent = 'intent:scene-strike-assault-' + 'f'.repeat(64); expected.strikeReceipt = `observation:${expected.strikeIntent}`; }
  ]) {
    const value = structuredClone(await declaration()); mutate(value);
    assert.throws(() => assertF02cHotReceiptDeclaration(value), /F0\.2C HOT carrier/);
  }
});
