import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { assertF06r3ColdProductionCarrier } from '../src/f06r3-cold-production-carrier.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const job = 'job:production-development-input-theft';
const worker = 'resident:1-15';
const station = { x: -382, y: 65, z: -324 };

test('F0.6R3 COLD production carrier binds an active retained worker cursor to ordinary first placement', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-cold-production.json'), 'utf8'));
  const facts = assertF06r3ColdProductionCarrier({ declaration, manifest: manifest() });
  assert.deepEqual(facts, { worker, coldCursor: 5, firstPlacement: station, postIngressCursor: 5 });
});

test('F0.6R3 COLD production carrier rejects a first placement at a stale route origin', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-cold-production.json'), 'utf8'));
  const stale = manifest();
  stale.diagnostics.find(entry => entry.actionStep === 4).observed.value.physicalAdmission.placement = { x: -340, y: 65, z: -329 };
  assert.throws(() => assertF06r3ColdProductionCarrier({ declaration, manifest: stale }), /replayed a route origin/);
});

function manifest() {
  return {
    status: 'ok', scenarioId: 'disposable_f06r3_cold_production',
    zeroPlayerPrelude: { status: 'completed', clientSegmentsBeforeCompletion: 0, boundedness: { status: 'NO_STALL', noServerTickStall: true } },
    clientSegments: [{ ingressResponsiveness: { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true } }],
    diagnostics: [
      observed(1, 'process', job, { family: 'frontier.production-work', identity: { worker }, claims: { lease: null }, cursor: { index: 5, length: 6, retainedBody: station, actorBody: station } }),
      observed(2, 'actor', worker, { position: station }),
      observed(4, 'actor', worker, { physicalAdmission: { placement: station } }),
      observed(5, 'process', job, { identity: { worker }, cursor: { index: 5, length: 6 } })
    ]
  };
}
function observed(actionStep, kind, id, value) { return { actionStep, observed: { actionStep, value: { kind, id, status: 'ok', ...value } } }; }
