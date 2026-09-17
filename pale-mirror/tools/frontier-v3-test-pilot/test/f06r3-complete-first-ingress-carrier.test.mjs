import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { assertF06r3CompleteFirstIngressCarrier } from '../src/f06r3-complete-first-ingress-carrier.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

test('F0.6R3 complete first-ingress carrier binds every local first position to a pre-visit canonical resident', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-complete-first-ingress.json'), 'utf8'));
  assert.deepEqual(assertF06r3CompleteFirstIngressCarrier({ declaration, manifest: manifest() }), {
    settlement: 'settlement:7', residents: 20, firstPositions: 20, stationaryResidents: 20
  });
  const row = manifest();
  row.diagnostics[1].value.residents[0].first = { x: 0.5, y: 64, z: 0.5 };
  assert.throws(() => assertF06r3CompleteFirstIngressCarrier({ declaration, manifest: row }), /retained canonical station/);
  const departure = manifest();
  departure.diagnostics[1].value.residents[1].maxDisplacement = .46;
  assert.throws(() => assertF06r3CompleteFirstIngressCarrier({ declaration, manifest: departure }), /left its bounded canonical station/);
  const missing = manifest();
  missing.diagnostics[1].value.residents.pop(); missing.diagnostics[1].value.residentCount--;
  assert.throws(() => assertF06r3CompleteFirstIngressCarrier({ declaration, manifest: missing }), /complete pre-visit population/);
});

function manifest() {
  const residents = Array.from({ length: 20 }, (_, index) => {
    const x = 100 + index; const uuid = `00000000-0000-0000-0000-${String(index + 1).padStart(12, '0')}`;
    return { actor: `resident:7-${index + 1}`, entityUuid: uuid, dutyPhase: 'AMBIENT:PATROL:HOT', position: { x, y: 64, z: 0 } };
  });
  return { status: 'ok', scenarioId: 'disposable_f06r3_complete_first_ingress', clientSegments: [{ ingressResponsiveness:
    { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }
  }], diagnostics: [
    { actionStep: 1, value: { kind: 'settlement_population', id: 'settlement:7', status: 'ok', residentCount: residents.length, residents } },
    { actionStep: 3, value: { kind: 'pilot_settlement_population', id: 'settlement:7', status: 'ok', residentCount: residents.length,
      residents: residents.map(row => ({ actor: row.actor, entityUuid: row.entityUuid, dutyPhase: row.dutyPhase,
        first: { x: row.position.x + .5, y: row.position.y, z: row.position.z + .5 }, samples: 40, maxDisplacement: 0, maxStep: 0 })) } }
  ] };
}
