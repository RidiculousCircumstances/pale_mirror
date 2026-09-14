const SCENARIO = 'disposable_f06r3_recovered_duty';
const JOB = 'job:site-harvest-7-wheat-field-1';
const SITE = 'site:7-wheat-field';

/**
 * Binds the actual player-owned HOT lease to a released zero-player COLD interval and the
 * subsequent durable restart.  The initial partial cursor prevents a completed bootstrap
 * prefix from impersonating recovery progress; the client reads the complete recovered field.
 */
export function assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, middleRestart, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.zeroPlayerPrelude?.advanceTicks !== 21010
      || declaration?.restart?.mode !== 'graceful' || declaration.restart.afterAction !== 4
      || declaration.restart.zeroPlayerAdvanceTicks !== 23800 || declaration.restart.secondary?.afterAction !== 10
      || declaration.restart.secondary.zeroPlayerAdvanceTicks !== 21100 || manifest?.status !== 'ok'
      || manifest?.scenarioId !== SCENARIO || manifest?.recovery?.splitAfterAction !== 4
      || manifest.recovery.secondarySplitAfterAction !== 10 || beforeRestart?.status !== 'ok' || middleRestart?.status !== 'ok') {
    throw new Error('F0.6R3 recovered-duty carrier lacks its declared two-boundary HOT-to-COLD recovery cycle');
  }
  const prelude = manifest.zeroPlayerPrelude;
  const firstInterlude = manifest.restartZeroPlayerInterlude;
  const secondInterlude = manifest.secondaryRestartZeroPlayerInterlude;
  if (prelude?.status !== 'completed' || prelude.advanceTicks !== 21010 || prelude.clientSegmentsBeforeCompletion !== 0
      || firstInterlude?.status !== 'completed' || firstInterlude.advanceTicks !== 23800 || firstInterlude.clientSegmentsBeforeCompletion !== 0
      || secondInterlude?.status !== 'completed' || secondInterlude.advanceTicks !== 21100 || secondInterlude.clientSegmentsBeforeCompletion !== 0) {
    throw new Error('F0.6R3 recovered-duty carrier lacks isolated zero-player COLD intervals');
  }
  const before = exactly(observed(beforeRestart), 2, 'process', JOB);
  const motion = exactly(observed(beforeRestart), 3, 'pilot_motion', JOB);
  const release = exactly(observed(beforeRestart), 4, 'process', JOB);
  const after = exactly(observed(middleRestart), 2, 'process', JOB);
  const site = exactly(observed(middleRestart), 3, 'site', SITE);
  const facility = exactly(observed(middleRestart), 4, 'resource_site_facility', SITE);
  const terminalMotion = exactly(observed(middleRestart), 5, 'pilot_motion', JOB);
  const terminalSite = exactly(observed(middleRestart), 6, 'site', SITE);
  const successor = exactly(observed(manifest), 2, 'process', 'job:site-harvest-7-wheat-field-2');
  const successorMotion = exactly(observed(manifest), 3, 'pilot_motion', 'job:site-harvest-7-wheat-field-2');
  const successorSite = exactly(observed(manifest), 4, 'site', SITE);
  const successorIntent = exactly(observed(manifest), 4, 'intent', 'intent:site-harvest-7-wheat-field-2');
  if (before.claims?.lease?.status !== 'HOT' || before.claims.lease.members !== 1
      || !Number.isInteger(completed(before)) || completed(before) < 0 || completed(before) >= 63) {
    throw new Error('F0.6R3 recovered-duty carrier did not retain a genuinely partial HOT lease before ordinary release');
  }
  assertMotionDuty(motion);
  if (release.claims?.lease?.status !== 'HOT' || release.identity?.job !== before.identity?.job
      || release.identity?.worker !== before.identity?.worker || !['PREPARED', 'RUNNING'].includes(release.result?.intentStatus)
      || completed(release) < completed(before) || completed(release) >= 63) {
    throw new Error('F0.6R3 recovered-duty carrier lacks the exact partial HOT owner state immediately before ordinary release');
  }
  if (completed(after) !== 63 || after.conservation?.deferredMaterializationSlots !== 63
      || after.result?.sitePhase !== 'HARVESTING' || !['PREPARED', 'RUNNING'].includes(after.result?.intentStatus)
      || after.identity?.job !== before.identity?.job || after.identity?.worker !== before.identity?.worker
      || after.claims?.intent !== before.claims?.intent || completed(after) <= completed(release)) {
    throw new Error('F0.6R3 released COLD work did not advance the same eligible job before restart/re-entry');
  }
  if (site.phase === 'CONFLICT' || site.phase !== 'HARVESTING' || site.activeWork !== JOB || site.conflictDisposition !== null) {
    throw new Error('F0.6R3 restart reconciliation left the canonical field site conflicted or incoherent');
  }
  if (facility.clientPhysicalRead !== true || facility.cropSlots !== 64 || facility.farmlandSlots !== 64 || facility.waterSlots !== 4
      || facility.completedCropSlots !== 63 || facility.airCropSlots !== 63 || facility.wheatCropSlots !== 1) {
    throw new Error('F0.6R3 restart re-entry did not deliver one complete current field facility to the ordinary client');
  }
  if (successor.claims?.lease?.status !== 'HOT' || successor.claims.lease.members !== 1 || completed(successor) !== 63 || completed(successor) < completed(after)
      || successor.identity?.worker !== before.identity.worker || successorSite.phase !== 'GROWING' || successorSite.growthEpoch !== 3
      || successorSite.activeWork !== '' || successorSite.conflictDisposition !== null || successorIntent.intentStatus !== 'CONFIRMED'
      || successorIntent.intentKind !== 'RESOURCE_SITE_HARVEST' || !Array.isArray(successorIntent.subjects)
      || !successorIntent.subjects.includes('item:site-harvest-7-wheat-field-2-wheat')) {
    throw new Error('F0.6R3 second released COLD interval did not recover one current successor facility after restart');
  }
  assertTerminalContinuity(motion, terminalMotion, terminalSite, successor, successorMotion, before.identity.worker);
  assertLifecycle(manifest.lifecycle);
  assertResponsiveOrdinaryIngress(manifest.clientSegments);
  return Object.freeze({ job: before.identity.job, worker: before.identity.worker, releasedColdAdvance: [completed(before), completed(after)],
    successorColdAdvance: [completed(after), completed(successor)], sitePhase: site.phase, completeFacility: true,
    motion: assertMotionDuty(motion), terminal: assertTerminalContinuity(motion, terminalMotion, terminalSite, successor, successorMotion, before.identity.worker) });
}

function assertTerminalContinuity(before, terminal, site, successor, successorMotion, worker) {
  if (!Array.isArray(terminal?.samples) || terminal.samples.length < 540 || terminal.samples.length > 601
      || typeof before?.entityUuid !== 'string' || before.entityUuid !== terminal.entityUuid
      || site?.phase !== 'GROWING' || site.growthEpoch !== 2 || site.activeWork !== ''
      || successor?.identity?.worker !== worker || successor?.result?.sitePhase !== 'HARVESTING'
      || successorMotion?.entityUuid !== before.entityUuid || !Array.isArray(successorMotion.samples)
      || successorMotion.samples.length < 540 || successorMotion.samples.length > 601) {
    throw new Error('F0.6R3 terminal harvest did not retain one exact farmer body through receipt, release, growth and successor assignment');
  }
  const positions = terminal.samples.map(sample => `${sample.x},${sample.y},${sample.z}`);
  if (positions.length !== terminal.samples.length || !terminal.samples.every(sample => typeof sample?.workAnimation === 'boolean')) {
    throw new Error('F0.6R3 terminal farmer continuity trace is malformed');
  }
  const terminalEnd = terminal.samples.at(-1);
  const successorStart = successorMotion.samples[0];
  if (!exactPosition(terminalEnd) || !exactPosition(successorStart)
      || horizontalDistance(terminalEnd, successorStart) > 1.1) {
    throw new Error('F0.6R3 terminal harvest did not retain the exact physical resident through successor COLD and restart');
  }
  return Object.freeze({ entityUuid: terminal.entityUuid, samples: terminal.samples.length, successorSamples: successorMotion.samples.length, successor: successor.id });
}
function exactPosition(value) { return Number.isFinite(value?.x) && Number.isFinite(value?.y) && Number.isFinite(value?.z); }
function horizontalDistance(left, right) { return Math.hypot(left.x - right.x, left.z - right.z); }

function assertMotionDuty(value) {
  const samples = value?.samples;
  if (value?.entityType !== 'minecraft:villager' || value.sampleEveryTicks !== 1 || value.durationTicks !== 1200 || typeof value.entityUuid !== 'string'
      || !Array.isArray(samples) || samples.length < 1_080 || samples.length > 1_201) {
    throw new Error('F0.6R3 recovered-duty carrier lacks a complete client farmer duty trace');
  }
  const ordered = [...samples].sort((a, b) => a.tick - b.tick);
  let moves = 0; let travellingSamples = 0; let travellingStationaryRun = 0; let longestTravellingStationaryRun = 0;
  let travellingWorkFrames = 0; let harvestingSamples = 0; let harvestingStationarySamples = 0;
  let visibleStationaryWorkFrames = 0; let movingHarvestingSamples = 0;
  const columns = new Set(); const ticks = new Set(); const semanticPhases = new Set();
  for (let index = 0; index < ordered.length; index++) {
    const sample = ordered[index];
    if (!Number.isInteger(sample?.tick) || !Number.isFinite(sample?.x) || !Number.isFinite(sample?.z) || typeof sample?.workAnimation !== 'boolean'
        || typeof sample?.semanticPhase !== 'string' || !/^(TRAVELLING|HARVESTING):(PREPARED|RUNNING)$/.test(sample.semanticPhase)) {
      throw new Error('F0.6R3 recovered-duty motion trace is malformed');
    }
    ticks.add(sample.tick); columns.add(`${Math.floor(sample.x)},${Math.floor(sample.z)}`); semanticPhases.add(sample.semanticPhase);
    if (sample.semanticPhase.startsWith('TRAVELLING:')) {
      travellingSamples++;
      if (sample.workAnimation) travellingWorkFrames++;
    } else harvestingSamples++;
    if (index === 0) continue;
    const moved = Math.hypot(sample.x - ordered[index - 1].x, sample.z - ordered[index - 1].z) > .01;
    if (moved) moves++;
    if (sample.semanticPhase.startsWith('TRAVELLING:')) {
      travellingStationaryRun = moved ? 0 : travellingStationaryRun + 1;
      longestTravellingStationaryRun = Math.max(longestTravellingStationaryRun, travellingStationaryRun);
    } else {
      travellingStationaryRun = 0;
      if (!moved) {
        harvestingStationarySamples++;
        if (sample.workAnimation) visibleStationaryWorkFrames++;
      } else movingHarvestingSamples++;
    }
  }
  // Only an actual retained traversal is judged as travel. A stationary crop station is lawful
  // only when the client receives its ordinary work animation. This rejects a phase label that
  // merely hides the old scheduler-paused body, as well as a synthetic tending orbit that
  // keeps a worker moving and swinging in the same crop cell.
  // Exact observed arrival can take a short reducer/lease acknowledgement turn after the body
  // reaches its retained destination.  The red r63 trace bounds that handshake to 11 ordinary
  // ticks; it is neither a visible multi-second plateau nor a new dwell.  Keep the oracle below
  // one second and reject a sustained cadence plateau rather than mislabelling an acknowledged
  // physical arrival as filler.
  if (ticks.size !== ordered.length || columns.size < 3 || moves < 20 || travellingSamples < 5 || longestTravellingStationaryRun > 20) {
    throw new Error('F0.6R3 farmer retains a cadence-locked visible stationary plateau while a retained traversal is pending');
  }
  if (travellingWorkFrames > 8 || harvestingSamples < 5 || harvestingStationarySamples < 5 || visibleStationaryWorkFrames < 1
      || movingHarvestingSamples !== 0) {
    throw new Error('F0.6R3 declared crop work is not a stationary visible station action');
  }
  return Object.freeze({ samples: ordered.length, columns: columns.size, moves, travellingSamples, travellingWorkFrames, longestTravellingStationaryRun,
    harvestingSamples, harvestingStationarySamples, visibleStationaryWorkFrames, movingHarvestingSamples,
    semanticPhases: [...semanticPhases].sort() });
}

function assertLifecycle(entries) {
  const names = Array.isArray(entries) ? entries.map(entry => entry?.barrier) : [];
  const required = ['client_normally_disconnected', 'normal_demand_loss_release', 'durable_server_save', 'recovery_server_ready'];
  if (required.some(name => names.filter(candidate => candidate === name).length < (name === 'client_normally_disconnected' || name === 'normal_demand_loss_release' || name === 'durable_server_save' || name === 'recovery_server_ready' ? 2 : 1))
      || names.indexOf('client_normally_disconnected') > names.indexOf('normal_demand_loss_release')
      || names.indexOf('normal_demand_loss_release') > names.indexOf('durable_server_save')
      || names.indexOf('durable_server_save') > names.indexOf('recovery_server_ready')) {
    throw new Error('F0.6R3 recovered-duty carrier lacks ordered ordinary release and restart ownership evidence');
  }
}
function assertResponsiveOrdinaryIngress(segments) {
  const results = segments?.flatMap(segment => Array.isArray(segment?.ingressResponsiveness)
    ? segment.ingressResponsiveness : [segment?.ingressResponsiveness]);
  if (!Array.isArray(results) || results.length !== 3 || results.some(result => result?.status !== 'ok'
      || result.ordinaryClientJoined !== true || result.noServerTickStall !== true)) {
    throw new Error('F0.6R3 recovered-duty carrier lacks responsive ordinary-client ingress before and after restart');
  }
}
function completed(value) { return value?.conservation?.completedCropSlots; }
function exactly(diagnostics, actionStep, kind, id) {
  // The pilot may retain the same immutable diagnostic line both as an action receipt and in
  // its raw log slice.  Collapse only byte-identical values; two distinct observations at the
  // same boundary still fail closed rather than being silently selected by position.
  const values = [...new Map(diagnostics.filter(entry => entry?.actionStep === actionStep && entry?.value?.kind === kind && entry.value.id === id)
    .map(entry => [JSON.stringify(entry.value), entry.value])).values()];
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6R3 recovered-duty carrier lacks one ${kind}:${id} receipt at action ${actionStep}`);
  return values[0];
}
function observed(manifest) {
  const entries = manifest?.diagnostics ?? [];
  const asserted = entries.flatMap(entry => entry?.observed?.value ? [entry.observed] : []);
  const raw = entries.flatMap(entry => entry?.assertion === undefined && entry?.value ? [entry] : []);
  return asserted.length === 0 ? raw : [...asserted, ...raw.filter(entry => !asserted.some(receipt => receipt.actionStep === entry.actionStep
    && receipt.value?.kind === entry.value?.kind && receipt.value?.id === entry.value?.id))];
}
