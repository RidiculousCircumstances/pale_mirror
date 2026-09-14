import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { assertF06r3RecoveredDutyCarrier } from '../src/f06r3-recovered-duty-carrier.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const job = 'job:site-harvest-7-wheat-field-1';
const site = 'site:7-wheat-field';

test('F0.6R3 recovered-duty carrier rejects a physically complete field whose retained restart disposition is conflicted', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const { beforeRestart, middleRestart, manifest } = receipt();
  assert.doesNotThrow(() => assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, middleRestart, manifest }));
  middleRestart.diagnostics.find(entry => entry.actionStep === 3).value.conflictDisposition = {
    position: { x: 150, y: 64, z: -10 }, reason: 'OBSERVED_MANAGED_CELL_MISMATCH', policy: 'ISOLATE'
  };
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, middleRestart, manifest }), /conflicted or incoherent/);
});

test('F0.6R3 recovered-duty carrier requires phase-mapped travel, visible work and both ordinary ingress checks', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const first = receipt();
  first.beforeRestart.diagnostics.find(entry => entry.actionStep === 3).value.samples.splice(1, 199);
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...first }), /complete client farmer duty trace/);
  const second = receipt();
  second.beforeRestart.diagnostics.find(entry => entry.actionStep === 3).value.samples[4].semanticPhase = 'UNOBSERVED';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...second }), /motion trace is malformed/);
  const orbit = receipt();
  for (const sample of orbit.beforeRestart.diagnostics.find(entry => entry.actionStep === 3).value.samples.slice(21)) {
    sample.x += Math.cos(sample.tick / 5) * .16;
    sample.z = Math.sin(sample.tick / 5) * .16;
    sample.workAnimation = true;
  }
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...orbit }), /stationary visible station action/);
  const movingHarvest = receipt();
  const movingHarvestSample = movingHarvest.beforeRestart.diagnostics.find(entry => entry.actionStep === 3).value.samples[100];
  movingHarvestSample.x += .2;
  movingHarvestSample.workAnimation = false;
  movingHarvestSample.semanticPhase = 'HARVESTING:RUNNING';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...movingHarvest }), /stationary visible station action/);
  const plateau = receipt();
  const plateauSamples = plateau.beforeRestart.diagnostics.find(entry => entry.actionStep === 3).value.samples;
  for (let index = 24; index < 50; index++) {
    plateauSamples[index].x = plateauSamples[23].x;
    plateauSamples[index].z = plateauSamples[23].z;
    plateauSamples[index].workAnimation = false;
    plateauSamples[index].semanticPhase = 'TRAVELLING:PREPARED';
  }
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...plateau }), /cadence-locked visible stationary plateau/);
  const third = receipt();
  third.manifest.clientSegments[0].ingressResponsiveness.pop();
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...third }), /responsive ordinary-client ingress/);
});

test('F0.6R3 recovered-duty carrier rejects a successor that exists but did not advance during the later COLD interval', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const stalled = receipt();
  stalled.manifest.diagnostics.find(entry => entry.actionStep === 2).value.conservation.completedCropSlots = 0;
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...stalled }), /create and advance a successor/);
});

function receipt() {
  const hot = process(19, 'HOT');
  const release = process(21, 'HOT');
  const recovered = process(63, 'CLOSED');
  const motion = { kind: 'pilot_motion', id: job, status: 'ok', entityType: 'minecraft:villager', entityUuid: '11111111-1111-1111-1111-111111111111', sampleEveryTicks: 1,
    durationTicks: 1200, samples: Array.from({ length: 1200 }, (_, tick) => ({ tick, x: tick < 80 ? tick * .2 : 15.8, z: 0,
      workAnimation: tick >= 80 && tick % 32 < 6,
      semanticPhase: tick < 80 ? 'TRAVELLING:PREPARED' : 'HARVESTING:PREPARED' })) };
  return {
    beforeRestart: { status: 'ok', diagnostics: [{ actionStep: 2, value: hot }, { actionStep: 3, value: motion }, { actionStep: 4, value: release }] },
    middleRestart: { status: 'ok', diagnostics: [{ actionStep: 2, value: recovered }, { actionStep: 3, value: { kind: 'site', id: site, status: 'ok', phase: 'HARVESTING', activeWork: job, conflictDisposition: null } },
      { actionStep: 4, value: { kind: 'resource_site_facility', id: site, status: 'ok', clientPhysicalRead: true, cropSlots: 64, farmlandSlots: 64,
        waterSlots: 4, completedCropSlots: 63, airCropSlots: 63, wheatCropSlots: 1 } },
      { actionStep: 5, value: terminalMotion() },
      { actionStep: 6, value: { kind: 'site', id: site, status: 'ok', phase: 'GROWING', growthEpoch: 2, activeWork: '', conflictDisposition: null } }] },
    manifest: { status: 'ok', scenarioId: 'disposable_f06r3_recovered_duty', recovery: { splitAfterAction: 4, secondarySplitAfterAction: 10 },
      zeroPlayerPrelude: { status: 'completed', advanceTicks: 21010, clientSegmentsBeforeCompletion: 0 },
      restartZeroPlayerInterlude: { status: 'completed', advanceTicks: 23800, clientSegmentsBeforeCompletion: 0 },
      secondaryRestartZeroPlayerInterlude: { status: 'completed', advanceTicks: 21100, clientSegmentsBeforeCompletion: 0 },
      lifecycle: ['client_normally_disconnected', 'normal_demand_loss_release', 'durable_server_save', 'recovery_server_ready', 'client_normally_disconnected', 'normal_demand_loss_release', 'durable_server_save', 'recovery_server_ready'].map(barrier => ({ barrier })),
      clientSegments: [{ ingressResponsiveness: [{ status: 'ok', ordinaryClientJoined: true, noServerTickStall: true },
        { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }, { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }] }],
      diagnostics: [{ actionStep: 2, value: { ...process(63, 'HOT'), id: 'job:site-harvest-7-wheat-field-2', identity: { job: 'job:site-harvest-7-wheat-field-2', worker: 'resident:7-31' } } },
        { actionStep: 3, value: { ...terminalMotion(), id: 'job:site-harvest-7-wheat-field-2' } },
        { actionStep: 4, value: { kind: 'site', id: site, status: 'ok', phase: 'GROWING', growthEpoch: 3, activeWork: '', conflictDisposition: null } },
        { actionStep: 4, value: { kind: 'intent', id: 'intent:site-harvest-7-wheat-field-2', status: 'ok', intentKind: 'RESOURCE_SITE_HARVEST', intentStatus: 'CONFIRMED',
          subjects: ['item:site-harvest-7-wheat-field-2-wheat', 'job:site-harvest-7-wheat-field-2', 'resident:7-31', site] } }] }
  };
}
function terminalMotion() { return { kind: 'pilot_motion', id: job, status: 'ok', entityType: 'minecraft:villager', entityUuid: '11111111-1111-1111-1111-111111111111', sampleEveryTicks: 1,
  durationTicks: 600, samples: Array.from({ length: 600 }, (_, tick) => ({ tick, x: 4, y: 1, z: 0, workAnimation: tick % 32 < 6, semanticPhase: 'HARVESTING:RUNNING' })) }; }
function process(completedCropSlots, leaseStatus) {
  return { kind: 'process', id: job, status: 'ok', identity: { job, worker: 'resident:7-31' }, claims: { intent: 'intent:harvest-7',
    lease: { status: leaseStatus, members: 1 } }, conservation: { completedCropSlots, deferredMaterializationSlots: completedCropSlots },
    result: { sitePhase: 'HARVESTING', intentStatus: 'PREPARED' } };
}
