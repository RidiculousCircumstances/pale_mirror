import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { assertF06r3FirstVisibilityColdContinuityCarrier } from '../src/f06r3-first-visibility-cold-continuity-carrier.mjs';
import { validateScenario } from '../src/scenario.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

test('F0.6R3 first-visibility carrier derives the current worker from the COLD terminal receipt', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-first-visibility-cold-continuity.json'), 'utf8'));
  assert.doesNotThrow(() => validateScenario(declaration));
  const { before, manifest } = fixtures();
  assert.deepEqual(assertF06r3FirstVisibilityColdContinuityCarrier({ declaration, beforeRestart: before, manifest }), {
    worker: 'resident:7-13', workerUuid: '00000000-0000-0000-0000-000000000013', successorJob: 'job:production-7-365',
    successorWorker: 'resident:7-15', growthEpoch: 8, growthStage: 5, frames: ['one.png', 'two.png', 'three.png'],
    fieldBoard: { text: 'WHEAT FIELD', position: { x: 141, y: 67, z: -8 } }
  });
  manifest.diagnostics.find(row => row.actionStep === 9).value.residents[0].first.x = 0.5;
  assert.throws(() => assertF06r3FirstVisibilityColdContinuityCarrier({ declaration, beforeRestart: before, manifest }), /replaced, converged/);
});

function fixtures() {
  const terminalHarvest = { job: 'job:site-harvest-7-wheat-field-7', worker: 'resident:7-13', canonicalSuccessor: true,
    successor: { job: 'job:production-7-365', worker: 'resident:7-15' } };
  const site = { kind: 'site', id: 'site:7-wheat-field', status: 'ok', phase: 'GROWING', growthEpoch: 8, growthStage: 5,
    activeWork: '', conflictDisposition: null, terminalHarvest };
  const resident = { actor: 'resident:7-13', entityUuid: '00000000-0000-0000-0000-000000000013', dutyPhase: 'AMBIENT:IDLE:CLOSED', position: { x: 144, y: 64, z: -6 } };
  const before = { status: 'ok', diagnostics: [{ actionStep: 1, value: site }, { actionStep: 2, value: { kind: 'settlement_population', id: 'settlement:7', status: 'ok', residents: [resident] } }] };
  const manifest = { status: 'ok', scenarioId: 'disposable_f06r3_first_visibility_cold_continuity',
    zeroPlayerPrelude: { status: 'completed', advanceTicks: 24000, clientSegmentsBeforeCompletion: 0, performance: { fastForwardRequests: [{ status: 'COMPLETED', requestedTicks: 24000 }] } },
    restartZeroPlayerInterlude: { status: 'completed' }, frames: [{ presentation: 'player', path: 'one.png' }, { presentation: 'player', path: 'two.png' }, { presentation: 'player', path: 'three.png' }],
    clientSegments: [{ ingressResponsiveness: { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true } }], diagnostics: [
      { actionStep: 9, value: { kind: 'pilot_settlement_population', id: 'settlement:7', status: 'ok', residents: [{ ...resident, first: { x: 144.5, y: 64, z: -5.5 }, maxDisplacement: 0, maxStep: 0 }] } },
      { actionStep: 10, value: { kind: 'settlement_population', id: 'settlement:7', status: 'ok', residents: [{ ...resident, admission: 'INDEXED' }] } },
      { actionStep: 12, value: site }, { actionStep: 13, value: { kind: 'settlement', id: 'settlement:7', status: 'ok', harvestAdmission: 'NO_READY_SITE' } }
    ] };
  return { before, manifest };
}
