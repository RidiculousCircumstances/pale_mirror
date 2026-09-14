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
  const { beforeRestart, manifest } = receipt();
  assert.doesNotThrow(() => assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, manifest }));
  manifest.diagnostics.find(entry => entry.actionStep === 7).value.conflictDisposition = {
    position: { x: 150, y: 64, z: -10 }, reason: 'OBSERVED_MANAGED_CELL_MISMATCH', policy: 'ISOLATE'
  };
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, manifest }), /conflicted or incoherent/);
});

test('F0.6R3 recovered-duty carrier requires phase-mapped travel, visible work and both ordinary ingress checks', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const first = receipt();
  first.beforeRestart.diagnostics.find(entry => entry.actionStep === 3).value.samples.splice(1, 199);
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...first }), /complete client farmer duty trace/);
  const second = receipt();
  second.beforeRestart.diagnostics.find(entry => entry.actionStep === 3).value.samples[4].semanticPhase = 'UNOBSERVED';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...second }), /motion trace is malformed/);
  const third = receipt();
  third.manifest.clientSegments[0].ingressResponsiveness.pop();
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...third }), /responsive ordinary-client ingress/);
});

function receipt() {
  const hot = process(19, 'HOT');
  const release = process(21, 'HOT');
  const recovered = process(63, 'CLOSED');
  const motion = { kind: 'pilot_motion', id: job, status: 'ok', entityType: 'minecraft:villager', sampleEveryTicks: 1,
    durationTicks: 1200, samples: Array.from({ length: 1200 }, (_, tick) => ({ tick, x: tick * .2, z: 0,
      workAnimation: tick >= 20 && tick < 40 && tick % 4 === 0,
      semanticPhase: tick < 20 ? 'TRAVELLING:PREPARED' : 'HARVESTING:PREPARED' })) };
  return {
    beforeRestart: { status: 'ok', diagnostics: [{ actionStep: 2, value: hot }, { actionStep: 3, value: motion }, { actionStep: 4, value: release }] },
    manifest: { status: 'ok', scenarioId: 'disposable_f06r3_recovered_duty', recovery: { splitAfterAction: 4 },
      zeroPlayerPrelude: { status: 'completed', advanceTicks: 21010, clientSegmentsBeforeCompletion: 0 },
      restartZeroPlayerInterlude: { status: 'completed', advanceTicks: 24000, clientSegmentsBeforeCompletion: 0 },
      lifecycle: ['client_normally_disconnected', 'normal_demand_loss_release', 'durable_server_save', 'recovery_server_ready'].map(barrier => ({ barrier })),
      clientSegments: [{ ingressResponsiveness: [{ status: 'ok', ordinaryClientJoined: true, noServerTickStall: true },
        { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }] }],
      diagnostics: [{ actionStep: 6, value: recovered }, { actionStep: 7, value: { kind: 'site', id: site, status: 'ok', phase: 'HARVESTING', activeWork: job, conflictDisposition: null } },
        { actionStep: 8, value: { kind: 'resource_site_facility', id: site, status: 'ok', clientPhysicalRead: true, cropSlots: 64, farmlandSlots: 64,
          waterSlots: 4, completedCropSlots: 63, airCropSlots: 63, wheatCropSlots: 1 } }] }
  };
}
function process(completedCropSlots, leaseStatus) {
  return { kind: 'process', id: job, status: 'ok', identity: { job, worker: 'resident:7-31' }, claims: { intent: 'intent:harvest-7',
    lease: { status: leaseStatus, members: 1 } }, conservation: { completedCropSlots, deferredMaterializationSlots: completedCropSlots },
    result: { sitePhase: 'HARVESTING', intentStatus: 'PREPARED' } };
}
