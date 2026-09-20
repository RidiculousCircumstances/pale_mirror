import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { assertF06r3RecoveredDutyCarrier } from '../src/f06r3-recovered-duty-carrier.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const job = 'job:site-harvest-7-wheat-field-1';
const site = 'site:7-wheat-field';
const farmer = 'resident:7-31';

test('F0.6R3 recovered-duty carrier binds a COLD terminal to the original farmer without crop replay', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  assert.doesNotThrow(() => assertF06r3RecoveredDutyCarrier({ declaration, ...receipt() }));
});

test('F0.6R3 recovered-duty carrier rejects a stale crop-63/HOT-final substitution', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const value = receipt();
  value.middleRestart.diagnostics.find(entry => entry.actionStep === 2).value.phase = 'HARVESTING';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...value }), /COLD terminal\/restart lineage/);
});

test('F0.6R3 recovered-duty carrier rejects a terminal owned by a replacement farmer', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const value = receipt();
  value.middleRestart.diagnostics.find(entry => entry.actionStep === 2).value.terminalHarvest.worker = 'resident:7-30';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...value }), /COLD terminal\/restart lineage/);
});

test('F0.6R3 recovered-duty carrier rejects a missing retained terminal trace or replayable predecessor intent', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const missingTrace = receipt();
  missingTrace.middleRestart.diagnostics.find(entry => entry.actionStep === 4).value.status = 'not_found';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...missingTrace }), /retained trace/);
  const replay = receipt();
  replay.middleRestart.diagnostics.find(entry => entry.actionStep === 5).value.status = 'ok';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...replay }), /not_found intent/);
});

test('F0.6R3 recovered-duty carrier rejects a restart identity change or stalled natural ingress', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const replacement = receipt();
  replacement.middleRestart.diagnostics.find(entry => entry.actionStep === 3).value.physicalAdmission.entityUuid = '22222222-2222-2222-2222-222222222222';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...replacement }), /COLD terminal\/restart lineage/);
  const stalled = receipt();
  stalled.manifest.clientSegments[1].ingressResponsiveness[0].noServerTickStall = false;
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...stalled }), /ordinary natural-ingress/);
});

function receipt() {
  const entityUuid = '11111111-1111-1111-1111-111111111111';
  const actor = () => ({ kind: 'actor', id: farmer, status: 'ok', physicalAdmission: { status: 'INDEXED', entityUuid } });
  const process = { kind: 'process', id: job, status: 'ok', identity: { job, worker: farmer }, claims: { lease: { status: 'HOT', members: 1 } },
    conservation: { completedCropSlots: 19 }, result: { intentStatus: 'PREPARED' } };
  const motion = { kind: 'pilot_motion', id: job, status: 'ok', entityType: 'minecraft:villager', entityUuid, sampleEveryTicks: 1, durationTicks: 120,
    samples: Array.from({ length: 120 }, (_, tick) => ({ tick, x: tick, y: 64, z: 0, workAnimation: tick > 90, semanticPhase: tick < 90 ? 'TRAVELLING:PREPARED' : 'HARVESTING:PREPARED' })) };
  const interval = fields => ({ ...fields, clientSegmentsBeforeCompletion: 0, boundedness: { status: 'NO_STALL', noServerTickStall: true, stallCount: 0, maxBehindMillis: 0, maxBehindTicks: 0 },
    performance: { fastForwardSlice: { samples: 1, advancedTicks: 10, totalNanos: 1, maxNanos: 1, safetyNanos: 1, maxSafetyNanos: 1, advanceNanos: 1, maxAdvanceNanos: 1 } } });
  return {
    beforeRestart: { status: 'ok', diagnostics: [{ actionStep: 2, value: process }, { actionStep: 3, value: actor() }, { actionStep: 5, value: motion }, { actionStep: 6, value: { ...process, conservation: { completedCropSlots: 21 } } }] },
    middleRestart: { status: 'ok', diagnostics: [
      { actionStep: 2, value: { kind: 'site', id: site, status: 'ok', phase: 'GROWING', growthEpoch: 2, activeWork: '', conflictDisposition: null,
        terminalHarvest: { job, worker: farmer, outputItem: 'item:site-harvest-7-wheat-field-1-wheat', outputSlot: 0, physicalReceiptConfirmed: false, intentStatus: 'PREPARED' } } },
      { actionStep: 3, value: actor() },
      { actionStep: 4, value: { kind: 'trace', id: `resource-site-harvest:${job}`, status: 'retained', correlation: `resource-site-harvest:${job}`, complete: true,
        cold: { command: 'command:cold', event: 'event:cold', revision: 1, instant: 22_000 } } },
      { actionStep: 5, value: { kind: 'intent', id: 'intent:site-harvest-7-wheat-field-1', status: 'not_found', reason: 'not_found' } },
      { actionStep: 6, value: { kind: 'summary', id: '', status: 'ok', inventoryConflicts: 0 } }
    ] },
    manifest: { status: 'ok', scenarioId: 'disposable_f06r3_recovered_duty', recovery: { splitAfterAction: 6 },
      zeroPlayerPrelude: interval({ status: 'held', targetInstant: 21140, holdAtTarget: true }), restartZeroPlayerInterlude: interval({ status: 'completed', advanceTicks: 19000 }),
      lifecycle: ['client_normally_disconnected', 'normal_demand_loss_release', 'durable_server_save', 'recovery_server_ready'].map(barrier => ({ barrier })),
      clientSegments: [{ ingressResponsiveness: [{ status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }] }, { ingressResponsiveness: [{ status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }] }] }
  };
}
