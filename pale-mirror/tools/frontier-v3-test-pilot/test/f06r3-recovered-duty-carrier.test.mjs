import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { assertF06r3RecoveredDutyCarrier } from '../src/f06r3-recovered-duty-carrier.mjs';
import { restartSegments } from '../src/scenario.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const job = 'job:site-harvest-7-wheat-field-1';
const site = 'site:7-wheat-field';

test('F0.6R3 recovered-duty carrier rejects a physically complete field whose retained restart disposition is conflicted', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const { beforeRestart, middleRestart, manifest } = receipt();
  assert.doesNotThrow(() => assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, middleRestart, manifest }));
  const conflictedSite = middleRestart.diagnostics.find(entry => entry.actionStep === 3).value;
  conflictedSite.phase = 'CONFLICT';
  conflictedSite.conflictDisposition = {
    position: { x: 150, y: 64, z: -10 }, reason: 'OBSERVED_MANAGED_CELL_MISMATCH', policy: 'TERMINAL_REPAIR_REQUIRED', incident: {
      id: 'incident:resource-site:7-wheat-field', category: 'INVARIANT_FAILURE', reason: 'OBSERVED_MANAGED_CELL_MISMATCH',
      owner: site, subject: site, source: 'LIFECYCLE_RECONCILIATION', expected: 'phase=GROWING', observed: 'reason=OBSERVED_MANAGED_CELL_MISMATCH',
      preCanonical: 'phase=GROWING', postCanonical: 'phase=CONFLICT', disposition: 'TERMINAL_REPAIR_REQUIRED',
      traceCorrelation: 'conflict:incident:resource-site:7-wheat-field'
    }
  };
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, middleRestart, manifest }), /conflicted or incoherent/);
});

test('F0.6R3 successor assertions are scheduled after the COLD boundary that can create them', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const segments = restartSegments(declaration);
  assert.deepEqual(segments.middle.assertions.map(({ after, view, id }) => ({ after, view, id })), [
    { after: 2, view: 'process', id: job },
    { after: 3, view: 'site', id: site },
    { after: 6, view: 'site', id: site },
    { after: 9, view: 'container', id: 'container:7-depot' },
    { after: 10, view: 'trace', id: 'production-input:item:site-harvest-7-wheat-field-1-wheat' }
  ]);
  assert.deepEqual(segments.after.assertions.map(({ after, view, id }) => ({ after, view, id })), [
    { after: 2, view: 'process', id: 'job:site-harvest-7-wheat-field-2' },
    { after: 4, view: 'site', id: site },
    { after: 5, view: 'actor', id: 'resident:7-31' },
    { after: 7, view: 'projection_work', id: '' }
  ]);
  const successorAdmission = segments.after.actions[1];
  assert.deepEqual(declaration.restart.zeroPlayerAdvanceBatches, [11900, 11900]);
  assert.deepEqual(declaration.restart.secondary.zeroPlayerAdvanceBatches, [10550, 10550]);
  assert.equal(successorAdmission.timeoutMs, 100000);
  assert.equal(successorAdmission.pollIntervalMs, 50,
    'the transient terminal HOT lease needs bounded sub-second read-only observation');
  assert.equal(successorAdmission.causalMilestone, 'same_farmer_declared_successor_after_retained_start_due');
  assert.equal(segments.after.actions[2].durationTicks, 6);
  assert.equal(segments.after.actions[2].causalMilestone, 'successor_same_farmer_terminal_station_work');
});

test('F0.6R3 recovered-duty carrier requires phase-mapped travel, visible work and both ordinary ingress checks', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const first = receipt();
  first.beforeRestart.diagnostics.find(entry => entry.actionStep === 5).value.samples.splice(1, 199);
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...first }), /complete client farmer duty trace/);
  const second = receipt();
  second.beforeRestart.diagnostics.find(entry => entry.actionStep === 5).value.samples[4].semanticPhase = 'UNOBSERVED';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...second }), /motion trace is malformed/);
  const orbit = receipt();
  for (const sample of orbit.beforeRestart.diagnostics.find(entry => entry.actionStep === 5).value.samples.slice(21)) {
    sample.x += Math.cos(sample.tick / 5) * .16;
    sample.z = Math.sin(sample.tick / 5) * .16;
    sample.workAnimation = true;
  }
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...orbit }), /stationary visible station action/);
  const movingHarvest = receipt();
  const movingHarvestSample = movingHarvest.beforeRestart.diagnostics.find(entry => entry.actionStep === 5).value.samples[100];
  movingHarvestSample.x += .2;
  movingHarvestSample.workAnimation = false;
  movingHarvestSample.semanticPhase = 'HARVESTING:RUNNING';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...movingHarvest }), /stationary visible station action/);
  const plateau = receipt();
  const plateauSamples = plateau.beforeRestart.diagnostics.find(entry => entry.actionStep === 5).value.samples;
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

test('F0.6R3 recovered-duty carrier rejects a successor whose exact farmer was not fed by the conserved ration', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const unserved = receipt();
  unserved.manifest.diagnostics.find(entry => entry.actionStep === 5).value.nutrition = 'STARVING';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...unserved }), /existing COLD provision owner did not restore/);
});

test('F0.6R3 recovered-duty carrier rejects a successor that reaches receipt without its exact rendered station duty', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const incomplete = receipt();
  for (const sample of incomplete.manifest.diagnostics.find(entry => entry.actionStep === 3 && entry.value.kind === 'pilot_motion').value.samples) {
    sample.workAnimation = false;
  }
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...incomplete }), /terminal harvest did not retain one exact farmer body/);
});

test('F0.6R3 recovered-duty carrier rejects a depot bread receipt with the wrong exact slot quantity', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const incomplete = receipt();
  incomplete.middleRestart.diagnostics.find(entry => entry.actionStep === 9).value.occupied[0].count = 63;
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...incomplete }), /conserved depot bread output/);
});

test('F0.6R3 recovered-duty carrier rejects bread whose retained production worker lacks the accepted handoff-to-release chain', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const incomplete = receipt();
  incomplete.middleRestart.diagnostics.find(entry => entry.actionStep === 10).value.chain = ['production_work_handoff', 'production_work_hot'];
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...incomplete }), /accepted handoff-to-terminal-release chain/);
});

test('F0.6R3 recovered-duty carrier rejects the exact pre-ingress COLD server stall', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const stalled = receipt();
  stalled.manifest.zeroPlayerPrelude.boundedness = { status: 'STALL', noServerTickStall: false,
    stallCount: 1, maxBehindMillis: 2263, maxBehindTicks: 45 };
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...stalled }), /server-thread stall/);
});

test('F0.6R3 recovered-duty carrier rejects a stalled batch inside its declared bounded COLD interval', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const stalled = receipt();
  stalled.manifest.restartZeroPlayerInterlude.batches[1].boundedness = { status: 'STALL', noServerTickStall: false,
    stallCount: 1, maxBehindMillis: 2263, maxBehindTicks: 45 };
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...stalled }), /server-thread stall/);
});

test('F0.6R3 recovered-duty carrier rejects a projection account without its bounded caller breakdown', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const incomplete = receipt();
  delete incomplete.manifest.diagnostics.find(entry => entry.actionStep === 7).value.structural.subpaths.cursor;
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...incomplete }), /bounded projection owner/);
});

test('F0.6R3 recovered-duty carrier rejects the exact r15 completed-but-last-cell-parked history', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const parked = receipt();
  const release = parked.middleRestart.diagnostics.find(entry => entry.actionStep === 5).value;
  for (const sample of release.samples.slice(240)) { sample.x = 4; sample.z = 0; }
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...parked }), /orbiting the final harvested cell/);
});

test('F0.6R3 recovered-duty carrier rejects a named replacement in the post-closure trace', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-recovered-duty.json'), 'utf8'));
  const replacement = receipt();
  replacement.middleRestart.diagnostics.find(entry => entry.actionStep === 7).value.entityUuid = '22222222-2222-2222-2222-222222222222';
  assert.throws(() => assertF06r3RecoveredDutyCarrier({ declaration, ...replacement }), /one exact farmer body/);
});

function receipt() {
  const hot = process(19, 'HOT');
  const release = process(21, 'HOT');
  const recovered = process(63, 'CLOSED');
  const motion = { kind: 'pilot_motion', id: job, status: 'ok', entityType: 'minecraft:villager', entityUuid: '11111111-1111-1111-1111-111111111111', sampleEveryTicks: 1,
    durationTicks: 120, samples: Array.from({ length: 120 }, (_, tick) => ({ tick, x: tick < 80 ? tick * .2 : 15.8, z: 0,
      workAnimation: tick >= 80 && tick % 32 < 6,
      semanticPhase: tick < 80 ? 'TRAVELLING:PREPARED' : 'HARVESTING:PREPARED' })) };
  return {
    beforeRestart: { status: 'ok', diagnostics: [{ actionStep: 2, value: hot }, { actionStep: 3, value: actor() }, { actionStep: 5, value: motion }, { actionStep: 6, value: release }] },
    middleRestart: { status: 'ok', diagnostics: [{ actionStep: 2, value: recovered }, { actionStep: 3, value: { kind: 'site', id: site, status: 'ok', phase: 'HARVESTING', activeWork: job, conflictDisposition: null } },
      { actionStep: 4, value: { kind: 'resource_site_facility', id: site, status: 'ok', clientPhysicalRead: true, cropSlots: 64, farmlandSlots: 64,
        waterSlots: 4, completedCropSlots: 63, airCropSlots: 63, wheatCropSlots: 1 } },
      { actionStep: 5, value: terminalMotion() },
      { actionStep: 6, value: { kind: 'site', id: site, status: 'ok', phase: 'GROWING', growthEpoch: 2, activeWork: '', conflictDisposition: null } },
      { actionStep: 7, value: postReleaseMotion() },
      { actionStep: 9, value: { kind: 'container', id: 'container:7-depot', status: 'ok', occupied: [
        { slot: 0, itemKind: 'minecraft:bread', count: 64 }
      ] } }, { actionStep: 10, value: productionWorkerTrace() }] },
    manifest: { status: 'ok', scenarioId: 'disposable_f06r3_recovered_duty', recovery: { splitAfterAction: 6, secondarySplitAfterAction: 16 },
      zeroPlayerPrelude: coldInterval({ status: 'held', targetInstant: 21140, holdAtTarget: true }),
      restartZeroPlayerInterlude: coldInterval({ status: 'completed', advanceTicks: 23800, batches: [coldInterval({ status: 'completed', advanceTicks: 11900 }), coldInterval({ status: 'completed', advanceTicks: 11900 })] }),
      secondaryRestartZeroPlayerInterlude: coldInterval({ status: 'completed', advanceTicks: 21100, batches: [coldInterval({ status: 'completed', advanceTicks: 10550 }), coldInterval({ status: 'completed', advanceTicks: 10550 })] }),
      lifecycle: ['client_normally_disconnected', 'normal_demand_loss_release', 'durable_server_save', 'recovery_server_ready', 'client_normally_disconnected', 'normal_demand_loss_release', 'durable_server_save', 'recovery_server_ready'].map(barrier => ({ barrier })),
      clientSegments: [{ ingressResponsiveness: [{ status: 'ok', ordinaryClientJoined: true, noServerTickStall: true },
        { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }, { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }] }],
      diagnostics: [{ actionStep: 2, value: { ...process(63, 'HOT'), id: 'job:site-harvest-7-wheat-field-2', identity: { job: 'job:site-harvest-7-wheat-field-2', worker: 'resident:7-31' } } },
        { actionStep: 3, value: successorMotion() },
        { actionStep: 4, value: { kind: 'site', id: site, status: 'ok', phase: 'GROWING', growthEpoch: 3, activeWork: '', conflictDisposition: null } },
        { actionStep: 4, value: { kind: 'intent', id: 'intent:site-harvest-7-wheat-field-2', status: 'ok', intentKind: 'RESOURCE_SITE_HARVEST', intentStatus: 'CONFIRMED',
          subjects: ['item:site-harvest-7-wheat-field-2-wheat', 'job:site-harvest-7-wheat-field-2', 'resident:7-31', site] } },
        { actionStep: 5, value: { kind: 'actor', id: 'resident:7-31', status: 'ok', nutrition: 'NOURISHED' } },
        { actionStep: 6, value: { kind: 'performance', id: '', status: 'ok', worstSpan: { stage: 'PHYSICAL', kind: 'graybox-projection', owner: 'projection', maxNanos: 1 },
          stages: [{ stage: 'SCHEDULE_PLAN', kind: 'frontier.resource_site.harvest.cold_progress', samples: 1, maxNanos: 1 }] } },
        { actionStep: 7, value: { kind: 'projection_work', id: '', status: 'ok', structural: { lastTrigger: 'INITIAL_CURSOR', lastDisposition: 'COMPILE_STABLE_BASELINE', lastCompileTrigger: 'INITIAL_CURSOR', planCompilations: 1,
          subpaths: { cursor: cost(), firstVisibility: cost(), stagingRetirement: cost(), deferredProjection: cost() } },
          callerPath: { status: 'RECORDED', maxNanos: 1 } } }] }
  };
}
function cost() { return { calls: 1, totalNanos: 1, maxNanos: 1 }; }
function coldInterval(fields) {
  return { ...fields, clientSegmentsBeforeCompletion: 0,
    boundedness: { status: 'NO_STALL', noServerTickStall: true, stallCount: 0, maxBehindMillis: 0, maxBehindTicks: 0 },
    performance: { kind: 'performance', status: 'ok', fastForwardSlice: { samples: 1, advancedTicks: 8,
      totalNanos: 17, maxNanos: 17, safetyNanos: 5, maxSafetyNanos: 5, advanceNanos: 9, maxAdvanceNanos: 9 } } };
}
function actor() { return { kind: 'actor', id: 'resident:7-31', status: 'ok', ambientLease: 'HOT', physicalAdmission: {
  status: 'INDEXED', entityUuid: '11111111-1111-1111-1111-111111111111' } }; }
function productionWorkerTrace() { return { kind: 'trace', id: 'production-input:item:site-harvest-7-wheat-field-1-wheat', status: 'ok',
  correlation: 'production-work:job:production-7-118', eventKind: 'scene_released', subject: 'job:production-7-118',
  command: 'executor:scene-release-r7743', transaction: 'transaction:revision-7744', acceptedRevision: 7744,
  chain: ['production_work_handoff', 'production_work_hot', 'production_work_draining:terminal-effect-ready', 'scene_released'],
  causal: { operation: '', lease: 'lease:production-work-7-118-r7473', cargo: '', actors: ['resident:7-15'] } }; }
function terminalMotion() { return { kind: 'pilot_motion', id: job, status: 'ok', entityType: 'minecraft:villager', entityUuid: '11111111-1111-1111-1111-111111111111', sampleEveryTicks: 1,
  durationTicks: 600, samples: Array.from({ length: 600 }, (_, tick) => ({ tick, x: tick < 240 ? 4 : Math.min(12, 4 + (tick - 240) * .08), y: 1, z: 0,
    workAnimation: tick >= 220 && tick < 240, semanticPhase: tick < 240 ? 'HARVESTING:RUNNING' : 'AMBIENT:WORK:HOT' })) }; }
function postReleaseMotion() { return { kind: 'pilot_motion', id: job, status: 'ok', entityType: 'minecraft:villager', entityUuid: '11111111-1111-1111-1111-111111111111', sampleEveryTicks: 1,
  durationTicks: 600, samples: Array.from({ length: 600 }, (_, tick) => ({ tick, x: 12, y: 1, z: 0,
    workAnimation: false, semanticPhase: 'AMBIENT:WORK:HOT' })) }; }
function successorMotion() { return { kind: 'pilot_motion', id: 'job:site-harvest-7-wheat-field-2', status: 'ok', entityType: 'minecraft:villager', entityUuid: '11111111-1111-1111-1111-111111111111', sampleEveryTicks: 1,
  durationTicks: 6, samples: Array.from({ length: 6 }, (_, tick) => ({ tick, x: 12, y: 1, z: 0, workAnimation: tick < 4, semanticPhase: 'HARVESTING:RUNNING' })) }; }
function process(completedCropSlots, leaseStatus) {
  return { kind: 'process', id: job, status: 'ok', identity: { job, worker: 'resident:7-31' }, claims: { intent: 'intent:harvest-7',
    lease: { status: leaseStatus, members: 1 } }, conservation: { completedCropSlots, deferredMaterializationSlots: completedCropSlots },
    result: { sitePhase: 'HARVESTING', intentStatus: 'PREPARED' } };
}
