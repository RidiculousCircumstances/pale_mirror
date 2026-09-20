const SCENARIO = 'disposable_f06r3_recovered_duty';
const JOB = 'job:site-harvest-7-wheat-field-1';
const SITE = 'site:7-wheat-field';
const PRODUCTION_INPUT = 'item:site-harvest-7-wheat-field-1-wheat';
const PRODUCTION_TRACE = `production-input:${PRODUCTION_INPUT}`;

/**
 * Binds the actual player-owned HOT lease to a released zero-player COLD interval and the
 * subsequent durable restart.  The initial partial cursor prevents a completed bootstrap
 * prefix from impersonating recovery progress; the client reads the complete recovered field.
 */
export function assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, middleRestart, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.zeroPlayerPrelude?.targetInstant !== 21140 || declaration.zeroPlayerPrelude.holdAtTarget !== true
      || declaration?.restart?.mode !== 'graceful' || declaration.restart.afterAction !== 6
      || declaration.restart.zeroPlayerAdvanceTicks !== 23800 || !sameBatches(declaration.restart.zeroPlayerAdvanceBatches, [11900, 11900])
      || declaration.restart.secondary?.afterAction !== 16 || declaration.restart.secondary.zeroPlayerAdvanceTicks !== 21100
      || !sameBatches(declaration.restart.secondary.zeroPlayerAdvanceBatches, [10550, 10550]) || manifest?.status !== 'ok'
      || manifest?.scenarioId !== SCENARIO || manifest?.recovery?.splitAfterAction !== 6
      || manifest.recovery.secondarySplitAfterAction !== 16 || beforeRestart?.status !== 'ok' || middleRestart?.status !== 'ok') {
    throw new Error('F0.6R3 recovered-duty carrier lacks its declared two-boundary HOT-to-COLD recovery cycle');
  }
  const prelude = manifest.zeroPlayerPrelude;
  const firstInterlude = manifest.restartZeroPlayerInterlude;
  const secondInterlude = manifest.secondaryRestartZeroPlayerInterlude;
  if (prelude?.status !== 'held' || prelude.targetInstant !== 21140 || prelude.holdAtTarget !== true || prelude.clientSegmentsBeforeCompletion !== 0
      || firstInterlude?.status !== 'completed' || firstInterlude.advanceTicks !== 23800 || !sameBatches(firstInterlude.batches?.map(batch => batch.advanceTicks), [11900, 11900])
      || firstInterlude.clientSegmentsBeforeCompletion !== 0 || secondInterlude?.status !== 'completed' || secondInterlude.advanceTicks !== 21100
      || !sameBatches(secondInterlude.batches?.map(batch => batch.advanceTicks), [10550, 10550]) || secondInterlude.clientSegmentsBeforeCompletion !== 0) {
    throw new Error('F0.6R3 recovered-duty carrier lacks isolated zero-player COLD intervals');
  }
  if (![prelude, firstInterlude, secondInterlude].every(interval => interval.boundedness?.status === 'NO_STALL'
      && interval.boundedness.noServerTickStall === true && interval.boundedness.stallCount === 0
      && interval.boundedness.maxBehindMillis === 0 && interval.boundedness.maxBehindTicks === 0
      && (interval.batches === undefined || interval.batches.every(batch => batch.boundedness?.status === 'NO_STALL'
        && batch.boundedness.noServerTickStall === true && batch.boundedness.stallCount === 0)))) {
    throw new Error('F0.6R3 recovered-duty carrier retained a server-thread stall in its exact zero-player COLD interval');
  }
  if (![prelude, firstInterlude, secondInterlude].every(interval => hasFastForwardSlice(interval.performance?.fastForwardSlice)
      && (interval.batches === undefined || interval.batches.every(batch => hasFastForwardSlice(batch.performance?.fastForwardSlice))))) {
    throw new Error('F0.6R3 recovered-duty carrier lacks bounded server-thread attribution for its exact zero-player COLD interval');
  }
  const before = exactly(observed(beforeRestart), 2, 'process', JOB);
  const actor = exactly(observed(beforeRestart), 3, 'actor', before.identity?.worker);
  const motion = exactly(observed(beforeRestart), 5, 'pilot_motion', JOB);
  const release = exactly(observed(beforeRestart), 6, 'process', JOB);
  const after = exactly(observed(middleRestart), 2, 'process', JOB);
  const site = exactly(observed(middleRestart), 3, 'site', SITE);
  const facility = exactly(observed(middleRestart), 4, 'resource_site_facility', SITE);
  const terminalMotion = exactly(observed(middleRestart), 5, 'pilot_motion', JOB);
  const terminalSite = exactly(observed(middleRestart), 6, 'site', SITE);
  const postReleaseMotion = exactly(observed(middleRestart), 7, 'pilot_motion', JOB);
  const breadDepot = exactly(observed(middleRestart), 9, 'container', 'container:7-depot');
  const productionHandoff = exactly(observed(middleRestart), 10, 'trace', PRODUCTION_TRACE);
  const successor = exactly(observed(manifest), 2, 'process', 'job:site-harvest-7-wheat-field-2');
  const successorMotion = exactly(observed(manifest), 3, 'pilot_motion', 'job:site-harvest-7-wheat-field-2');
  const successorSite = exactly(observed(manifest), 4, 'site', SITE);
  const successorIntent = exactly(observed(manifest), 4, 'intent', 'intent:site-harvest-7-wheat-field-2');
  const rationedFarmer = exactly(observed(manifest), 5, 'actor', before.identity?.worker);
  const performance = exactly(observed(manifest), 6, 'performance', '');
  const projection = exactly(observed(manifest), 7, 'projection_work', '');
  if (before.claims?.lease?.status !== 'HOT' || before.claims.lease.members !== 1
      || !Number.isInteger(completed(before)) || completed(before) <= 0 || completed(before) >= 63) {
    throw new Error('F0.6R3 recovered-duty carrier did not retain a genuinely partial HOT lease before ordinary release');
  }
  assertMotionDuty(motion);
  assertExactWorkerAdmission(actor, before.identity?.worker, motion);
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
  if (site.phase === 'CONFLICT') {
    assertConflictIncident(site.conflictDisposition, SITE);
    throw new Error('F0.6R3 restart reconciliation left the canonical field site conflicted or incoherent');
  }
  if (site.phase !== 'HARVESTING' || site.activeWork !== JOB || site.conflictDisposition !== null) {
    throw new Error('F0.6R3 restart reconciliation left the canonical field site conflicted or incoherent');
  }
  assertRetainedProductionHandoff(productionHandoff);
  if (!hasExactOccupiedStack(breadDepot, 'minecraft:bread', 64, 0)) {
    throw new Error('F0.6R3 retained worker did not close its exact wheat job into the conserved depot bread output');
  }
  if (rationedFarmer.nutrition !== 'NOURISHED') {
    throw new Error('F0.6R3 existing COLD provision owner did not restore the exact farmer from its conserved ration');
  }
  if (facility.clientPhysicalRead !== true || facility.cropSlots !== 64 || facility.farmlandSlots !== 64 || facility.waterSlots !== 4
      || facility.completedCropSlots !== 63 || facility.airCropSlots !== 63 || facility.wheatCropSlots !== 1) {
    throw new Error('F0.6R3 restart re-entry did not deliver one complete current field facility to the ordinary client');
  }
  const successorColdAdvance = assertSuccessorColdAdvance(terminalSite, successor);
  if (successor.claims?.lease?.status !== 'HOT' || successor.claims.lease.members !== 1 || completed(successor) !== 63
      || successor.identity?.worker !== before.identity.worker || successorSite.phase !== 'GROWING' || successorSite.growthEpoch !== 3
      || successorSite.activeWork !== '' || successorSite.conflictDisposition !== null || successorIntent.intentStatus !== 'CONFIRMED'
      || successorIntent.intentKind !== 'RESOURCE_SITE_HARVEST' || !Array.isArray(successorIntent.subjects)
      || !successorIntent.subjects.includes('item:site-harvest-7-wheat-field-2-wheat')) {
    throw new Error('F0.6R3 second released COLD interval did not recover one current successor facility after restart');
  }
  if (projection.structural?.lastTrigger !== 'INITIAL_CURSOR' || projection.structural?.lastDisposition !== 'COMPILE_STABLE_BASELINE'
      || projection.structural?.lastCompileTrigger !== 'INITIAL_CURSOR'
      || projection.structural?.planCompilations !== 1 || projection.callerPath?.status !== 'RECORDED'
      || !Number.isInteger(projection.callerPath?.maxNanos) || projection.callerPath.maxNanos < 0
      || !completeProjectionCost(projection.structural?.subpaths)) {
    throw new Error('F0.6R3 recovered-duty carrier rebuilt the whole graybox baseline after startup instead of retaining the bounded projection owner');
  }
  if (performance.status !== 'ok' || !Array.isArray(performance.stages) || performance.stages.length === 0
      || !performance.stages.every(stage => typeof stage?.stage === 'string' && typeof stage?.kind === 'string'
          && Number.isInteger(stage?.samples) && Number.isInteger(stage?.maxNanos))
      || typeof performance.worstSpan?.stage !== 'string' || typeof performance.worstSpan?.kind !== 'string'
      || typeof performance.worstSpan?.owner !== 'string' || !Number.isInteger(performance.worstSpan?.maxNanos)
      || performance.worstSpan.maxNanos < 0) {
    throw new Error('F0.6R3 recovered-duty carrier lacks a complete COLD-and-ingress cost account');
  }
  assertTerminalContinuity(motion, terminalMotion, terminalSite, postReleaseMotion, successor, successorMotion, before.identity.worker);
  assertLifecycle(manifest.lifecycle);
  assertResponsiveOrdinaryIngress(manifest.clientSegments);
  return Object.freeze({ job: before.identity.job, worker: before.identity.worker, releasedColdAdvance: [completed(before), completed(after)],
    successorColdAdvance, sitePhase: site.phase, completeFacility: true,
    motion: assertMotionDuty(motion), terminal: assertTerminalContinuity(motion, terminalMotion, terminalSite, postReleaseMotion, successor, successorMotion, before.identity.worker) });
}

function sameBatches(actual, expected) {
  return Array.isArray(actual) && actual.length === expected.length && actual.every((value, index) => value === expected[index]);
}

function assertConflictIncident(disposition, site) {
  const incident = disposition?.incident;
  if (typeof incident?.id !== 'string' || incident.id !== `incident:resource-site:${site.slice('site:'.length)}`
      || typeof incident.category !== 'string' || typeof incident.reason !== 'string'
      || incident.owner !== site || incident.subject !== site || typeof incident.source !== 'string'
      || typeof incident.expected !== 'string' || typeof incident.observed !== 'string'
      || typeof incident.preCanonical !== 'string' || typeof incident.postCanonical !== 'string'
      || typeof incident.disposition !== 'string' || incident.traceCorrelation !== `conflict:${incident.id}`) {
    throw new Error('F0.6R3 restart reconciliation left the canonical field site conflicted or incoherent: missing durable conflict incident');
  }
}
function completeProjectionCost(subpaths) {
  return subpaths !== null && typeof subpaths === 'object'
    && ['cursor', 'firstVisibility', 'stagingRetirement', 'deferredProjection'].every(name => {
      const cost = subpaths[name];
      return Number.isInteger(cost?.calls) && cost.calls >= 0 && Number.isInteger(cost?.totalNanos) && cost.totalNanos >= 0
        && Number.isInteger(cost?.maxNanos) && cost.maxNanos >= 0 && cost.maxNanos <= cost.totalNanos;
    });
}

function hasFastForwardSlice(slice) {
  return Number.isInteger(slice?.samples) && slice.samples > 0
    && ['advancedTicks', 'totalNanos', 'maxNanos', 'safetyNanos', 'maxSafetyNanos', 'advanceNanos', 'maxAdvanceNanos']
      .every(field => Number.isInteger(slice[field]) && slice[field] >= 0);
}

/**
 * The second no-player interval begins after the first harvest's terminal receipt.  At that
 * point there cannot be a successor cursor to compare with: the site is deliberately growing
 * and unassigned.  Its later job is therefore measured against that typed unassigned boundary,
 * not against the predecessor's completed field cursor.  Comparing the two numeric 63 values
 * was a category error that made real successor COLD work look like a no-op.
 */
function assertSuccessorColdAdvance(preColdSite, successor) {
  if (preColdSite?.phase !== 'GROWING' || preColdSite.growthEpoch !== 2 || preColdSite.activeWork !== ''
      || preColdSite.conflictDisposition !== null || typeof successor?.identity?.job !== 'string'
      || !successor.identity.job.endsWith('-2') || !Number.isInteger(completed(successor)) || completed(successor) <= 0) {
    throw new Error('F0.6R3 second released COLD interval did not create and advance a successor from its unassigned growth boundary');
  }
  return Object.freeze({ preCold: Object.freeze({ phase: preColdSite.phase, growthEpoch: preColdSite.growthEpoch, activeWork: preColdSite.activeWork }),
    successorJob: successor.identity.job, postColdCompletedCropSlots: completed(successor), createdAndAdvancedDuringCold: completed(successor) });
}

function assertTerminalContinuity(before, terminal, site, postRelease, successor, successorMotion, worker) {
  if (!Array.isArray(terminal?.samples) || terminal.samples.length < 540 || terminal.samples.length > 601
      || !Array.isArray(postRelease?.samples) || postRelease.samples.length < 540 || postRelease.samples.length > 601
      || typeof before?.entityUuid !== 'string' || before.entityUuid !== terminal.entityUuid || terminal.entityUuid !== postRelease.entityUuid
      || site?.phase !== 'GROWING' || site.growthEpoch !== 2 || site.activeWork !== ''
      || successor?.identity?.worker !== worker || successor?.result?.sitePhase !== 'HARVESTING'
      || successorMotion?.entityUuid !== before.entityUuid || !successorStationWork(successorMotion)) {
    throw new Error('F0.6R3 terminal harvest did not retain one exact farmer body through receipt, release, growth and successor assignment');
  }
  const positions = terminal.samples.map(sample => `${sample.x},${sample.y},${sample.z}`);
  if (positions.length !== terminal.samples.length || !terminal.samples.every(sample => typeof sample?.workAnimation === 'boolean')) {
    throw new Error('F0.6R3 terminal farmer continuity trace is malformed');
  }
  const terminalEnd = terminal.samples.at(-1);
  const successorStart = successorMotion.samples[0];
  // The terminal receipt action spans the final crop dwell and the immediate ambient WORK
  // hand-off. The following action begins after that departure has already reached its safe
  // return surface, so judging it as the departure itself would reject the lawful history.
  const finalCropIndex = terminal.samples.map(sample => sample.workAnimation === true).lastIndexOf(true);
  if (finalCropIndex < 0) throw new Error('F0.6R3 terminal farmer continuity trace lacks the final visible crop duty');
  const release = assertDirectedWorkRelease({ ...terminal, samples: terminal.samples.slice(finalCropIndex + 1) }, terminal.samples[finalCropIndex]);
  if (postRelease.samples.some(sample => sample.workAnimation === true || sample.semanticPhase !== 'AMBIENT:WORK:HOT')) {
    throw new Error('F0.6R3 released farmer lacks a typed shared-custody continuation after terminal output');
  }
  if (!exactPosition(terminalEnd) || !exactPosition(successorStart)
      || horizontalDistance(terminalEnd, successorStart) > 1.1) {
    throw new Error('F0.6R3 terminal harvest did not retain the exact physical resident through successor COLD and restart');
  }
  return Object.freeze({ entityUuid: terminal.entityUuid, samples: terminal.samples.length, postRelease: release,
    successorSamples: successorMotion.samples.length, successor: successor.id });
}

/**
 * The successor owns only the final crop, so its lawful physical station interval can be much
 * shorter than the predecessor's route-and-work trace.  It must still be an exact, rendered,
 * stationary harvest duty; waiting for a long arbitrary window would race the valid receipt and
 * mistake terminal retirement for lost custody.
 */
function successorStationWork(value) {
  const samples = value?.samples;
  if (value?.entityType !== 'minecraft:villager' || value.sampleEveryTicks !== 1 || value.durationTicks !== 6
      || !Array.isArray(samples) || samples.length < 4 || samples.length > 7
      || samples.some(sample => !exactPosition(sample) || !/^HARVESTING:(PREPARED|RUNNING)$/.test(sample?.semanticPhase)
          || typeof sample?.workAnimation !== 'boolean')) return false;
  const first = samples[0];
  return samples.some(sample => sample.workAnimation)
      && samples.every(sample => horizontalDistance(sample, first) <= .15);
}
/**
 * The terminal receipt has already proved output, task close and GROWING.  The same physical
 * farmer must then leave the exact final crop under its retained WORK lease.  This is deliberately
 * a client trace, not a synthetic position mutation or a new demand edge: it rejects r15's exact
 * history where CONFIRMED/GROWING existed while the body orbited the final harvested cell.
 */
function assertDirectedWorkRelease(value, finalCrop) {
  const samples = value?.samples;
  if (!Array.isArray(samples) || !exactPosition(finalCrop) || !samples.every(exactPosition)) {
    throw new Error('F0.6R3 terminal directed-work release trace is malformed');
  }
  const columns = new Set(samples.map(sample => `${Math.floor(sample.x)},${Math.floor(sample.z)}`));
  const farthest = Math.max(...samples.map(sample => horizontalDistance(sample, finalCrop)));
  let moves = 0; let stationaryRun = 0; let longestStationaryRun = 0;
  for (let index = 1; index < samples.length; index++) {
    const moved = horizontalDistance(samples[index], samples[index - 1]) > .01;
    if (moved) { moves++; stationaryRun = 0; }
    else { stationaryRun++; longestStationaryRun = Math.max(longestStationaryRun, stationaryRun); }
  }
  if (columns.size < 3 || moves < 12 || farthest < 3.0 || samples.some(sample => sample.workAnimation === true)) {
    throw new Error('F0.6R3 confirmed harvest leaves its same resident orbiting the final harvested cell instead of following retained WORK release');
  }
  return Object.freeze({ samples: samples.length, columns: columns.size, moves, farthest, longestStationaryRun });
}
function exactPosition(value) { return Number.isFinite(value?.x) && Number.isFinite(value?.y) && Number.isFinite(value?.z); }
function horizontalDistance(left, right) { return Math.hypot(left.x - right.x, left.z - right.z); }

function assertMotionDuty(value) {
  const samples = value?.samples;
  if (value?.entityType !== 'minecraft:villager' || value.sampleEveryTicks !== 1 || value.durationTicks !== 120 || typeof value.entityUuid !== 'string'
      || !Array.isArray(samples) || samples.length < 100 || samples.length > 121) {
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

function assertExactWorkerAdmission(actor, worker, motion) {
  const admission = actor?.physicalAdmission;
  if (actor?.status !== 'ok' || actor.id !== worker || (actor.ambientLease !== 'HOT' && admission?.status !== 'SCENE_OWNED')
      || typeof admission?.entityUuid !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(admission.entityUuid)
      || admission.entityUuid !== motion?.entityUuid || !['INDEXED', 'SCENE_OWNED'].includes(admission.status)) {
    throw new Error('F0.6R3 recovered-duty carrier lacks one exact HOT worker UUID admission before visible motion');
  }
  return Object.freeze({ worker, entityUuid: admission.entityUuid, status: admission.status });
}

function assertRetainedProductionHandoff(trace) {
  const causal = trace?.causal;
  const required = ['production_work_handoff', 'production_work_hot', 'production_work_draining:terminal-effect-ready', 'scene_released'];
  if (trace?.status !== 'ok' || trace.id !== PRODUCTION_TRACE || typeof trace.correlation !== 'string'
      || !trace.correlation.startsWith('production-work:job:production-7-') || trace.eventKind !== 'scene_released'
      || trace.subject !== trace.correlation.slice('production-work:'.length)
      || typeof causal?.lease !== 'string' || !causal.lease.startsWith('lease:production-work-7-118-')
      || !Array.isArray(causal.actors) || causal.actors.length !== 1 || causal.actors[0] !== 'resident:7-15'
      || !Array.isArray(trace.chain) || required.some(kind => !trace.chain.includes(kind))) {
    throw new Error('F0.6R3 retained production worker lacks one exact accepted handoff-to-terminal-release chain');
  }
  return Object.freeze({ worker: causal.actors[0], lease: causal.lease, chain: Object.freeze([...trace.chain]) });
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
function hasExactOccupiedStack(container, itemKind, count, slot) {
  return container?.status === 'ok' && Array.isArray(container.occupied)
    && container.occupied.filter(value => value?.itemKind === itemKind && value.count === count && value.slot === slot).length === 1;
}
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
