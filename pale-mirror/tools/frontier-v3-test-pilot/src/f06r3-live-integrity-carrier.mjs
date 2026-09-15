import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f06r3_live_integrity';
const JOBS = Object.freeze(['job:site-harvest-7-wheat-field-1', 'job:site-harvest-4-wheat-field-1']);
const HIVE_ORGANS = Object.freeze(['west-ganglion', 'west-brood', 'west-store', 'west-hibernaculum-1', 'west-hibernaculum-2', 'west-hibernaculum-3',
  'east-ganglion', 'east-brood', 'east-store', 'east-hibernaculum-1', 'east-hibernaculum-2', 'east-hibernaculum-3']);

/** Terminal domain predicate for the three user-observed F0.6R3 regressions. */
export function assertF06r3LiveIntegrityCarrier({ declaration, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.zeroPlayerPrelude?.advanceTicks !== 24_000 || declaration?.restart?.mode !== 'graceful'
      || declaration?.restart?.zeroPlayerAdvanceTicks !== 40 || declaration?.assertNoServerTickStallDuringIngress !== true
      || declaration.restart.afterAction !== 20 || !Array.isArray(declaration.actions) || manifest?.status !== 'ok'
      || manifest.scenarioId !== SCENARIO || manifest.recovery?.mode !== 'graceful' || manifest.recovery?.splitAfterAction !== 20
      || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length) {
    throw new Error('F0.6R3 live-integrity manifest lacks its declared zero-player/restart lifecycle or ordinary-ingress responsiveness boundary');
  }
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) throw new Error('F0.6R3 live-integrity manifest substituted an action');
  });
  assertCurrentFieldActions(declaration.actions);
  assertSettlementPackageActions(declaration.actions);
  const terminalAssertionCount = assertDeclaredAssertions(declaration, manifest);
  const prelude = manifest.zeroPlayerPrelude;
  if (prelude?.status !== 'completed' || prelude.advanceTicks !== 24_000 || prelude.clientSegmentsBeforeCompletion !== 0) {
    throw new Error('F0.6R3 did not establish its COLD history before a natural test-player ingress');
  }
  const restartInterlude = manifest.restartZeroPlayerInterlude;
  if (restartInterlude?.status !== 'completed' || restartInterlude.advanceTicks !== 40 || restartInterlude.clientSegmentsBeforeCompletion !== 0) {
    throw new Error('F0.6R3 did not retain a zero-player COLD interval across its graceful restart');
  }
  assertResponsiveOrdinaryIngress(manifest.clientSegments);
  const diagnostics = observed(manifest);
  const coldInventory = exactly(diagnostics, 1, 'process_inventory', 'settlements');
  const projectionWork = exactly(diagnostics, 2, 'projection_work', '');
  const postArrivalInventory = exactly(diagnostics, 19, 'process_inventory', 'settlements');
  assertAllSettlementHarvestes(coldInventory, 'zero-player', true);
  assertProjectionWork(projectionWork);
  assertNoIngressReset(postArrivalInventory, coldInventory);
  const beforeSeven = exactly(diagnostics, 3, 'process', JOBS[0]);
  const beforeFour = exactly(diagnostics, 4, 'process', JOBS[1]);
  const afterSeven = exactly(diagnostics, 16, 'process', JOBS[0]);
  const afterFour = exactly(diagnostics, 18, 'process', JOBS[1]);
  const restartSeven = exactly(diagnostics, 20, 'process', JOBS[0]);
  [beforeSeven, beforeFour, afterSeven, afterFour, restartSeven].forEach(assertHarvestCursor);
  // The first arrival at site 7 is the one bounded ordinary HOT admission in this carrier.
  // Site 4 still proves a distinct unreset COLD arrival, but it must not be misreported as a
  // HOT lease before the scheduler has actually admitted it.
  assertFirstHotLeaseIsNotCropZero(afterSeven);
  assertCompleteCurrentFacility(diagnostics, 10);
  assertCompleteCurrentFacility(diagnostics, 23);
  if (!sameHarvest(beforeSeven, afterSeven) || !sameHarvest(beforeSeven, restartSeven) || !sameHarvest(beforeFour, afterFour)) {
    throw new Error('F0.6R3 representative first ingress or restart reset an exact COLD harvest identity/cursor');
  }
  for (const organ of HIVE_ORGANS) {
    const step = organ.startsWith('west-') ? 26 + HIVE_ORGANS.indexOf(organ) : 37 + HIVE_ORGANS.indexOf(organ) - 6;
    const foundry = exactly(diagnostics, step, 'hive_foundry', `settled@organ:${organ}`);
    if (foundry.phase !== 'SETTLED' || foundry.passed !== true || foundry.auditPassed !== true || foundry.runtimePending !== 0
        || foundry.runtimeMismatch !== 0 || foundry.runtimeUnverified !== 0 || foundry.blockers !== 0 || foundry.errors !== 0) {
      throw new Error(`F0.6R3 first-visible nest organ is not coherent: ${organ}`);
    }
  }
  if (!Array.isArray(manifest.frames) || manifest.frames.length !== 4
      || !manifest.frames.every(frame => frame.presentation === 'player' && typeof frame.path === 'string' && frame.path.endsWith('.png'))) {
    throw new Error('F0.6R3 natural settlement package or both seed nests lack declared player-height first-visible frames');
  }
  return Object.freeze({ terminalAssertionCount, zeroPlayerAllSites: coldInventory.count, projectionWork: Object.freeze({
    structural: projectionWork.structural, infectionOverlay: projectionWork.infectionOverlay }),
    coldCursors: Object.freeze(Object.fromEntries(coldInventory.entries.map(entry => [entry.site, entry.cursor]))), currentFacility: Object.freeze({ cropSlots: 64, farmlandSlots: 64, waterSlots: 4, completedCropSlots: 63 }),
    coldCompletedCropSlots: Object.freeze(Object.fromEntries(coldInventory.entries.map(entry => [entry.site, entry.completedCropSlots]))), representativeFirstArrivals: JOBS,
    restartContinuity: true, settlementPackage: Object.freeze({ farm: true, road: true }), seedNestOrgans: HIVE_ORGANS.length,
    playerFrames: manifest.frames.map(frame => frame.path) });
}

function assertSettlementPackageActions(actions) {
  const anchor = (action, field) => action?.position?.diagnostic?.view === 'settlement'
    && action.position.diagnostic.id === 'settlement:7' && action.position.diagnostic.field === field;
  if (actions[10]?.type !== 'inspect' || actions[10]?.view !== 'settlement' || actions[10]?.id !== 'settlement:7'
      || actions[11]?.type !== 'look' || actions[11]?.at?.diagnostic?.field !== 'farmAnchor'
      || actions[12]?.type !== 'assert_visible_block' || !anchor(actions[12], 'farmAnchor')
      || actions[13]?.type !== 'look' || actions[13]?.at?.diagnostic?.field !== 'routeSurface'
      || actions[14]?.type !== 'assert_visible_block' || !anchor(actions[14], 'routeSurface')) {
    throw new Error('F0.6R3 carrier does not prove the named farm and road package on ordinary first ingress');
  }
}

function assertCurrentFieldActions(actions) {
  const position = action => action?.position?.diagnostic;
  const site = 'site:7-wheat-field';
  if (actions[5]?.type !== 'wait_until_block' || actions[5]?.block !== 'minecraft:wheat'
      || position(actions[5])?.view !== 'site' || position(actions[5])?.id !== site || position(actions[5])?.field !== 'lastCrop'
      || actions[6]?.type !== 'look' || actions[7]?.type !== 'assert_visible_block'
      || actions[8]?.type !== 'wait_until_block' || actions[8]?.block !== 'minecraft:air'
      || position(actions[8])?.view !== 'site' || position(actions[8])?.id !== site || position(actions[8])?.field !== 'firstCrop'
      || !completeFacilityAction(actions[9]) || actions[20]?.type !== 'visit' || actions[20]?.dimension !== 'pale_mirror:frontier_graybox'
      || actions[20]?.causalMilestone !== 'site7_restart_natural_rearrival'
      || actions[21]?.type !== 'wait_until_block' || actions[21]?.block !== 'minecraft:wheat'
      || position(actions[21])?.view !== 'site' || position(actions[21])?.id !== site || position(actions[21])?.field !== 'lastCrop'
      || !completeFacilityAction(actions[22])) {
    throw new Error('F0.6R3 carrier does not prove the complete field and its exact COLD prefix through ordinary ingress and restart');
  }
}

function completeFacilityAction(action) {
  return action?.type === 'assert_complete_resource_site' && action.siteId === 'site:7-wheat-field'
    && action.completedCropSlots === 63 && action.timeoutMs === 5_000;
}

function assertResponsiveOrdinaryIngress(segments) {
  const results = segments?.flatMap(segment => Array.isArray(segment?.ingressResponsiveness)
    ? segment.ingressResponsiveness : [segment?.ingressResponsiveness]);
  if (!Array.isArray(results) || results.length !== 2 || results.some(result => result?.status !== 'ok'
      || result.ordinaryClientJoined !== true || result.noServerTickStall !== true)) {
    throw new Error('F0.6R3 native carrier lacks responsive ordinary-client ingress before and after restart');
  }
}

function assertCompleteCurrentFacility(diagnostics, step) {
  const proof = exactly(diagnostics, step, 'resource_site_facility', 'site:7-wheat-field');
  if (proof.clientPhysicalRead !== true || proof.cropSlots !== 64 || proof.farmlandSlots !== 64 || proof.waterSlots !== 4
      || proof.completedCropSlots !== 63 || proof.airCropSlots !== 63 || proof.wheatCropSlots !== 1) {
    throw new Error('F0.6R3 full current field facility is absent, partial, or not exact for its COLD aftermath');
  }
}

function assertProjectionWork(value) {
  const overlay = value.infectionOverlay;
  const structural = value.structural;
  // The canonical infection task itself changes the sparse overlay on its declared 600-tick
  // pulse; a fresh plan is therefore legitimate at that contributor boundary.  The 24k-tick
  // prelude admits at most forty such changes plus bootstrap.  This rejects a watchdog-style
  // compile per physical turn while preserving real infection evolution.
  if (!overlay || !Number.isSafeInteger(overlay.planCompilations) || overlay.planCompilations < 1 || overlay.planCompilations > 41
      || overlay.freshnessConstructions !== overlay.planCompilations || !Number.isSafeInteger(overlay.compatibilityChecks)
      || overlay.compatibilityChecks < overlay.planCompilations || overlay.compatibilityChecks > 2_400
      || !structural || !Number.isSafeInteger(structural.planCompilations) || structural.planCompilations < 1 || structural.planCompilations > 2
      || !Number.isSafeInteger(structural.compatibilityChecks) || structural.compatibilityChecks > 2_400
      || !Number.isSafeInteger(structural.providerAcquisitions) || structural.providerAcquisitions > 1_200) {
    throw new Error('F0.6R3 zero-player history exceeded its bounded projection-owner work envelope');
  }
}

function assertDeclaredAssertions(declaration, manifest) {
  const declared = declaration.assertions ?? [];
  const observedAssertions = (manifest.diagnostics ?? []).filter(entry => entry?.assertion !== undefined);
  if (declared.length === 0 || observedAssertions.length !== declared.length
      || observedAssertions.some((entry, index) => !isDeepStrictEqual(entry.assertion, declared[index]) || entry.observed?.value?.status !== 'ok')) {
    throw new Error('F0.6R3 native manifest lacks a complete terminal domain assertion set');
  }
  return declared.length;
}

function assertAllSettlementHarvestes(inventory, boundary, requireColdEligible = false) {
  if (inventory.count !== 12 || !Array.isArray(inventory.entries) || inventory.entries.length !== 12) {
    throw new Error(`F0.6R3 ${boundary} process inventory does not contain all current settlements`);
  }
  for (let site = 1; site <= 12; site++) {
    const expected = `site:${site}-wheat-field`;
    const entry = inventory.entries.find(value => value.site === expected);
    if (entry?.phase !== 'HARVESTING' || entry.job !== `job:site-harvest-${site}-wheat-field-1` || typeof entry.worker !== 'string'
        || !entry.worker.startsWith(`resident:${site}-`) || !Number.isSafeInteger(entry.cursor) || entry.cursor <= 0
        || !Number.isSafeInteger(entry.cropCursor) || entry.cursor > entry.cropCursor || !Number.isSafeInteger(entry.cursorLength) || entry.cropCursor >= entry.cursorLength
        || !Number.isSafeInteger(entry.completedCropSlots) || entry.completedCropSlots <= 0 || entry.pendingCropSlot !== -1
        || entry.nextCropSlot !== entry.completedCropSlots || entry.deferredMaterializationSlots !== entry.completedCropSlots
        || !['COLD_ELIGIBLE_SEMANTIC_HARVEST', 'HOT_OWNED_CURRENT_HARVEST'].includes(entry.waitReason)
        || (requireColdEligible && (entry.coldEligible !== true || entry.waitReason !== 'COLD_ELIGIBLE_SEMANTIC_HARVEST'))
        || !Number.isSafeInteger(entry.dueAt) || entry.dueAt < 0) {
      throw new Error(`F0.6R3 ${boundary} COLD progress is absent or ambiguous for ${expected}`);
    }
  }
}

function assertNoIngressReset(inventory, cold) {
  assertAllSettlementHarvestes(inventory, 'post-arrival');
  for (const before of cold.entries) {
    const after = inventory.entries.find(value => value.site === before.site);
    if (!after || after.job !== before.job || after.worker !== before.worker || after.completedCropSlots < before.completedCropSlots
        || after.completedCropSlots <= 0 || after.nextCropSlot <= 0) {
      throw new Error(`F0.6R3 first ingress reset ${before.site}`);
    }
  }
}

function assertHarvestCursor(value) {
  if (value.identity?.job == null || value.identity?.worker == null || !Number.isSafeInteger(value.cursor?.index) || value.cursor.index <= 0
      || !Number.isSafeInteger(value.cursor?.length) || value.cursor.index >= value.cursor.length
      || !Number.isSafeInteger(value.conservation?.completedCropSlots) || value.conservation.completedCropSlots <= 0
      || value.conservation?.nextCropSlot !== value.conservation.completedCropSlots || value.conservation?.deferredMaterializationSlots !== value.conservation.completedCropSlots
      || value.conservation?.pendingCropSlot !== -1
      || value.result?.sitePhase !== 'HARVESTING' || value.result?.intentStatus !== 'PREPARED') {
    throw new Error('F0.6R3 representative harvest lacks its exact pre-effect COLD cursor');
  }
}

function sameHarvest(left, right) {
  return left.identity?.job === right.identity?.job && left.identity?.worker === right.identity?.worker
    && left.claims?.intent === right.claims?.intent && left.cursor?.index === right.cursor?.index
    && left.cursor?.length === right.cursor?.length && right.conservation?.completedCropSlots >= left.conservation?.completedCropSlots
    && left.conservation?.completedCropSlots > 0;
}

function assertFirstHotLeaseIsNotCropZero(value) {
  // `lease` is the live authority fact.  `lastLease` is the same lease retained after a
  // naturally completed physical turn; it is deliberately diagnostic history, never a
  // resurrected owner.  Either receipt proves the ordinary ingress admitted the current crop.
  const lease = value.claims?.lease ?? value.claims?.lastLease;
  const match = typeof lease?.id === 'string' && lease.id.match(/-crop-(\d+)-r\d+$/);
  if (!match || Number(match[1]) <= 0) {
    throw new Error('F0.6R3 natural first ingress did not admit a current non-crop-0 HOT harvest lease');
  }
}

function exactly(diagnostics, actionStep, kind, id) {
  const values = diagnostics.filter(entry => entry.actionStep === actionStep && entry.value?.kind === kind && entry.value.id === id).map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6R3 lacks one ${kind}:${id} receipt at action ${actionStep}`);
  return values[0];
}

function observed(manifest) {
  const entries = manifest?.diagnostics ?? [];
  const asserted = entries.flatMap(entry => entry?.observed?.value ? [entry.observed] : []);
  const raw = entries.flatMap(entry => entry?.assertion === undefined && entry?.value ? [entry] : []);
  return asserted.length === 0 ? raw : [...asserted, ...raw.filter(entry => !asserted.some(receipt => receipt.actionStep === entry.actionStep
    && receipt.value?.kind === entry.value?.kind && receipt.value?.id === entry.value?.id))];
}
