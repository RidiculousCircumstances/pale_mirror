const SCENARIO = 'disposable_f06r3_recovered_duty';
const JOB = 'job:site-harvest-7-wheat-field-1';
const SITE = 'site:7-wheat-field';
const FARMER = 'resident:7-31';
const INTENT = 'intent:site-harvest-7-wheat-field-1';
const OUTPUT = 'item:site-harvest-7-wheat-field-1-wheat';
const TRACE = `resource-site-harvest:${JOB}`;

/** A HOT prefix may release into continuous COLD closure; ingress reads that retained closure and never reenacts crop 64. */
export function assertF06r3RecoveredDutyCarrier({ declaration, beforeRestart, middleRestart, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.zeroPlayerPrelude?.targetInstant !== 21140
      || declaration.zeroPlayerPrelude.holdAtTarget !== true || declaration?.restart?.mode !== 'graceful'
      || declaration.restart.afterAction !== 6 || declaration.restart.zeroPlayerAdvanceTicks !== 19000
      || declaration.restart.secondary !== undefined || manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO
      || manifest.recovery?.splitAfterAction !== 6 || beforeRestart?.status !== 'ok' || middleRestart?.status !== 'ok') {
    throw new Error('F0.6R3 recovered-duty carrier lacks its declared HOT-to-COLD terminal recovery cycle');
  }
  const prelude = manifest.zeroPlayerPrelude;
  const interlude = manifest.restartZeroPlayerInterlude;
  if (prelude?.status !== 'held' || prelude.targetInstant !== 21140 || prelude.clientSegmentsBeforeCompletion !== 0
      || interlude?.status !== 'completed' || interlude.advanceTicks !== 19000 || interlude.clientSegmentsBeforeCompletion !== 0
      || ![prelude, interlude].every(interval => noStall(interval) && hasFastForwardSlice(interval.performance?.fastForwardSlice))) {
    throw new Error('F0.6R3 recovered-duty carrier lacks bounded isolated COLD receipts');
  }
  const hot = exactly(beforeRestart, 2, 'process', JOB);
  const hotActor = exactly(beforeRestart, 3, 'actor', FARMER);
  const hotMotion = exactly(beforeRestart, 5, 'pilot_motion', JOB);
  const release = exactly(beforeRestart, 6, 'process', JOB);
  assertHotPrefix(hot, hotActor, hotMotion, release);
  const site = exactly(middleRestart, 8, 'site', SITE);
  const actor = exactly(middleRestart, 9, 'actor', FARMER);
  const trace = exactlyStatus(middleRestart, 10, 'trace', TRACE, 'retained');
  const predecessorIntent = exactlyStatus(middleRestart, 11, 'intent', INTENT, 'not_found');
  const summary = exactly(middleRestart, 12, 'summary', '');
  assertColdTerminal(site, actor, trace, predecessorIntent, summary, hotActor);
  assertLifecycle(manifest.lifecycle); assertResponsiveIngress(manifest.clientSegments);
  return Object.freeze({ job: JOB, worker: FARMER, terminalOutput: OUTPUT, terminalTrace: trace.correlation,
    currentPhase: site.phase, currentGrowthEpoch: site.growthEpoch, coldTerminalNoReplay: true });
}

function assertHotPrefix(hot, actor, motion, release) {
  const completed = hot?.conservation?.completedCropSlots;
  if (hot.claims?.lease?.status !== 'HOT' || hot.claims.lease.members !== 1 || !Number.isInteger(completed)
      || completed < 0 || completed >= 63 || hot.identity?.worker !== FARMER || release.identity?.job !== JOB
      || release.identity?.worker !== FARMER || release.claims?.lease?.status !== 'HOT'
      || !['PREPARED', 'RUNNING'].includes(release.result?.intentStatus) || !sameEntity(actor, motion)
      || motion?.entityType !== 'minecraft:villager' || motion.sampleEveryTicks !== 1 || motion.durationTicks !== 120
      || !Array.isArray(motion.samples) || motion.samples.length < 100 || motion.samples.length > 121
      || motion.samples.some(sample => !Number.isFinite(sample?.x) || !Number.isFinite(sample?.y) || !Number.isFinite(sample?.z)
        || typeof sample?.workAnimation !== 'boolean' || !/^(TRAVELLING|HARVESTING):(PREPARED|RUNNING)$/.test(sample?.semanticPhase))) {
    throw new Error('F0.6R3 recovered-duty carrier lacks the exact same-farmer HOT prefix before COLD release');
  }
}

function assertColdTerminal(site, actor, trace, predecessorIntent, summary, hotActor) {
  const terminal = site?.terminalHarvest;
  if (site.phase !== 'GROWING' || site.activeWork !== '' || site.conflictDisposition !== null
      || !Number.isInteger(site.growthEpoch) || site.growthEpoch < 2 || !terminal || terminal.job !== JOB
      || terminal.worker !== FARMER || terminal.outputItem !== OUTPUT || terminal.outputSlot !== 0
      || terminal.outputOwned !== false || terminal.canonicalSuccessor !== true || terminal.successor !== null
      || terminal.physicalReceiptResolved !== true || terminal.physicalReceiptConfirmed !== false || terminal.intentStatus !== 'MISSING'
      || terminal.causalTrace?.reconciliation !== 'composed_cold_receipt' || terminal.causalTrace?.completeForColdHotReceipt !== false
      || trace.correlation !== TRACE || trace.complete !== false || trace.observation?.id !== 'not_observed' || !coldTrace(trace.cold)
      || summary.inventoryConflicts !== 0 || !sameEntity(hotActor, actor)) {
    throw new Error('F0.6R3 COLD terminal/restart lineage is incoherent, replayable, conflicted, or replaces its exact farmer');
  }
}

function coldTrace(value) { return value !== null && typeof value?.command === 'string' && value.command.length > 0 && typeof value?.event === 'string'
  && value.event.length > 0 && Number.isInteger(value?.revision) && value.revision > 0 && Number.isSafeInteger(value?.instant) && value.instant > 0; }
function sameEntity(left, right) {
  const leftId = left?.physicalAdmission?.entityUuid ?? left?.entityUuid, rightId = right?.physicalAdmission?.entityUuid ?? right?.entityUuid;
  return typeof leftId === 'string' && /^[0-9a-f-]{36}$/i.test(leftId) && leftId === rightId;
}
function noStall(interval) {
  const boundedness = interval?.boundedness;
  return boundedness?.status === 'NO_STALL' && boundedness.noServerTickStall === true && boundedness.stallCount === 0
    && boundedness.maxBehindMillis === 0 && boundedness.maxBehindTicks === 0;
}
function hasFastForwardSlice(slice) { return Number.isInteger(slice?.samples) && slice.samples > 0
  && ['advancedTicks', 'totalNanos', 'maxNanos', 'safetyNanos', 'maxSafetyNanos', 'advanceNanos', 'maxAdvanceNanos']
    .every(field => Number.isInteger(slice[field]) && slice[field] >= 0); }
function assertLifecycle(entries) {
  const names = Array.isArray(entries) ? entries.map(entry => entry?.barrier) : [];
  const required = ['client_normally_disconnected', 'normal_demand_loss_release', 'durable_server_save', 'recovery_server_ready'];
  if (required.some(name => !names.includes(name)) || names.indexOf('client_normally_disconnected') > names.indexOf('normal_demand_loss_release')
      || names.indexOf('normal_demand_loss_release') > names.indexOf('durable_server_save')
      || names.indexOf('durable_server_save') > names.indexOf('recovery_server_ready')) throw new Error('F0.6R3 recovered-duty carrier lacks ordered release/restart ownership evidence');
}
function assertResponsiveIngress(segments) {
  const receipts = segments?.flatMap(segment => Array.isArray(segment?.ingressResponsiveness) ? segment.ingressResponsiveness : [segment?.ingressResponsiveness]);
  if (!Array.isArray(receipts) || receipts.length !== 2 || receipts.some(value => value?.status !== 'ok' || value.ordinaryClientJoined !== true || value.noServerTickStall !== true)) {
    throw new Error('F0.6R3 recovered-duty carrier lacks both ordinary natural-ingress receipts');
  }
}
function exactly(manifest, actionStep, kind, id) { return exactlyStatus(manifest, actionStep, kind, id, 'ok'); }
function exactlyStatus(manifest, actionStep, kind, id, status) {
  const values = observed(manifest).filter(entry => entry?.actionStep === actionStep && entry.value?.kind === kind && entry.value?.id === id).map(entry => entry.value);
  const terminal = values.at(-1);
  if (!terminal || terminal.status !== status) throw new Error(`F0.6R3 recovered-duty carrier lacks ${status} ${kind}:${id} at action ${actionStep}`);
  return terminal;
}
function observed(manifest) {
  const entries = manifest?.diagnostics ?? [], asserted = entries.flatMap(entry => entry?.observed?.value ? [entry.observed] : []);
  const raw = entries.flatMap(entry => entry?.assertion === undefined && entry?.value ? [entry] : []);
  return asserted.length === 0 ? raw : [...asserted, ...raw.filter(entry => !asserted.some(receipt => receipt.actionStep === entry.actionStep && receipt.value?.kind === entry.value?.kind && receipt.value?.id === entry.value?.id))];
}
