import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f06r3_cold_semantic';
const JOBS = Object.freeze(['job:site-harvest-7-wheat-field-1', 'job:site-harvest-4-wheat-field-1']);

/** Terminal COLD-aftermath predicate; F0.6R3's accepted movement/hive leaves stay out of this rerun. */
export function assertF06r3ColdSemanticCarrier({ declaration, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.zeroPlayerPrelude?.advanceTicks !== 24_000 || declaration?.restart?.mode !== 'graceful'
      || declaration.restart.afterAction !== 9 || manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO
      || manifest.recovery?.mode !== 'graceful' || manifest.recovery?.splitAfterAction !== 9
      || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length) {
    throw new Error('F0.6R3 COLD semantic manifest lacks its declared zero-player/restart lifecycle');
  }
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) throw new Error('F0.6R3 COLD semantic manifest substituted an action');
  });
  const terminalAssertionCount = assertDeclaredAssertions(declaration, manifest);
  const prelude = manifest.zeroPlayerPrelude;
  if (prelude?.status !== 'completed' || prelude.advanceTicks !== 24_000 || prelude.clientSegmentsBeforeCompletion !== 0) {
    throw new Error('F0.6R3 did not establish COLD crop receipts before natural ingress');
  }
  const diagnostics = observed(manifest);
  const coldInventory = exactly(diagnostics, 1, 'process_inventory', 'settlements');
  const projectionWork = exactly(diagnostics, 2, 'projection_work', '');
  const postArrivalInventory = exactly(diagnostics, 9, 'process_inventory', 'settlements');
  assertAllSettlementHarvestes(coldInventory, 'zero-player', true); assertProjectionWork(projectionWork); assertNoIngressReset(postArrivalInventory, coldInventory);
  const beforeSeven = exactly(diagnostics, 3, 'process', JOBS[0]); const beforeFour = exactly(diagnostics, 4, 'process', JOBS[1]);
  const afterSeven = exactly(diagnostics, 6, 'process', JOBS[0]); const afterFour = exactly(diagnostics, 8, 'process', JOBS[1]);
  const restartSeven = exactly(diagnostics, 10, 'process', JOBS[0]);
  [beforeSeven, beforeFour, afterSeven, afterFour, restartSeven].forEach(assertHarvestCursor);
  [afterSeven, afterFour].forEach(assertFirstIngressLeaseIsNotCropZero);
  if (!sameHarvest(beforeSeven, afterSeven) || !sameHarvest(beforeSeven, restartSeven) || !sameHarvest(beforeFour, afterFour)) {
    throw new Error('F0.6R3 representative ingress or restart reset an exact COLD harvest');
  }
  return Object.freeze({ terminalAssertionCount, zeroPlayerAllSites: coldInventory.count,
    coldCompletedCropSlots: Object.freeze(Object.fromEntries(coldInventory.entries.map(entry => [entry.site, entry.completedCropSlots]))),
    representativeFirstArrivals: JOBS, restartContinuity: true,
    projectionWork: Object.freeze({ structural: projectionWork.structural, infectionOverlay: projectionWork.infectionOverlay }) });
}

function assertDeclaredAssertions(declaration, manifest) {
  const declared = declaration.assertions ?? []; const observedAssertions = (manifest.diagnostics ?? []).filter(entry => entry?.assertion !== undefined);
  if (declared.length === 0 || observedAssertions.length !== declared.length
      || observedAssertions.some((entry, index) => !isDeepStrictEqual(entry.assertion, declared[index]) || entry.observed?.value?.status !== 'ok')) {
    throw new Error('F0.6R3 COLD native manifest lacks complete terminal assertions');
  }
  return declared.length;
}

function assertAllSettlementHarvestes(inventory, boundary, requireColdEligible = false) {
  if (inventory.count !== 12 || !Array.isArray(inventory.entries) || inventory.entries.length !== 12) throw new Error(`F0.6R3 ${boundary} inventory lacks all settlements`);
  for (let site = 1; site <= 12; site++) {
    const entry = inventory.entries.find(value => value.site === `site:${site}-wheat-field`);
    if (entry?.phase !== 'HARVESTING' || entry.job !== `job:site-harvest-${site}-wheat-field-1` || typeof entry.worker !== 'string'
        || !entry.worker.startsWith(`resident:${site}-`) || !Number.isSafeInteger(entry.cursor) || entry.cursor <= 0
        || !Number.isSafeInteger(entry.cropCursor) || entry.cursor > entry.cropCursor || !Number.isSafeInteger(entry.cursorLength) || entry.cropCursor >= entry.cursorLength
        || !Number.isSafeInteger(entry.completedCropSlots) || entry.completedCropSlots <= 0 || entry.pendingCropSlot !== -1
        || entry.nextCropSlot !== entry.completedCropSlots || entry.deferredMaterializationSlots !== entry.completedCropSlots
        || !['COLD_ELIGIBLE_SEMANTIC_HARVEST', 'HOT_OWNED_CURRENT_HARVEST'].includes(entry.waitReason)
        || (requireColdEligible && (entry.coldEligible !== true || entry.waitReason !== 'COLD_ELIGIBLE_SEMANTIC_HARVEST'))) {
      throw new Error(`F0.6R3 ${boundary} COLD crop receipt is absent or ambiguous for site:${site}`);
    }
  }
}

function assertNoIngressReset(inventory, cold) {
  assertAllSettlementHarvestes(inventory, 'post-arrival');
  for (const before of cold.entries) {
    const after = inventory.entries.find(value => value.site === before.site);
    if (!after || after.job !== before.job || after.worker !== before.worker || after.completedCropSlots < before.completedCropSlots || after.nextCropSlot <= 0) {
      throw new Error(`F0.6R3 ingress reset ${before.site}`);
    }
  }
}

function assertHarvestCursor(value) {
  if (value.identity?.job == null || value.identity?.worker == null || !Number.isSafeInteger(value.cursor?.index) || value.cursor.index <= 0
      || !Number.isSafeInteger(value.cursor?.length) || value.cursor.index >= value.cursor.length || value.conservation?.completedCropSlots <= 0
      || value.conservation?.nextCropSlot !== value.conservation.completedCropSlots || value.conservation?.deferredMaterializationSlots !== value.conservation.completedCropSlots
      || value.conservation?.pendingCropSlot !== -1 || value.result?.sitePhase !== 'HARVESTING'
      || !['PREPARED', 'RUNNING'].includes(value.result?.intentStatus)) {
    throw new Error('F0.6R3 representative harvest lacks its exact deferred COLD cursor');
  }
}

function assertFirstIngressLeaseIsNotCropZero(value) {
  const lease = value.claims?.lease ?? value.claims?.lastLease; const match = typeof lease?.id === 'string' && lease.id.match(/-crop-(\d+)-r\d+$/);
  if (lease?.status !== 'HOT' || !match || Number(match[1]) <= 0) {
    throw new Error('F0.6R3 natural ingress admitted crop-0 or no exact HOT harvest lease');
  }
}

function sameHarvest(left, right) {
  return left.identity?.job === right.identity?.job && left.identity?.worker === right.identity?.worker && left.claims?.intent === right.claims?.intent
    && right.cursor?.index >= left.cursor?.index && right.cursor?.length === left.cursor?.length && right.conservation?.completedCropSlots >= left.conservation?.completedCropSlots;
}

function assertProjectionWork(value) {
  const overlay = value.infectionOverlay; const structural = value.structural;
  if (!overlay || !Number.isSafeInteger(overlay.planCompilations) || overlay.planCompilations < 1 || overlay.planCompilations > 41
      || overlay.freshnessConstructions !== overlay.planCompilations || !Number.isSafeInteger(overlay.compatibilityChecks) || overlay.compatibilityChecks > 2400
      || !structural || !Number.isSafeInteger(structural.planCompilations) || structural.planCompilations < 1 || structural.planCompilations > 2
      || !Number.isSafeInteger(structural.compatibilityChecks) || structural.compatibilityChecks > 2400 || !Number.isSafeInteger(structural.providerAcquisitions)
      || structural.providerAcquisitions > 1200) throw new Error('F0.6R3 COLD history exceeded projection work envelope');
}

function exactly(diagnostics, step, kind, id) {
  const values = diagnostics.filter(entry => entry.actionStep === step && entry.value?.kind === kind && entry.value.id === id).map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6R3 lacks ${kind}:${id} receipt at action ${step}`); return values[0];
}
function observed(manifest) {
  const entries = manifest?.diagnostics ?? []; const asserted = entries.flatMap(entry => entry?.observed?.value ? [entry.observed] : []);
  const raw = entries.flatMap(entry => entry?.assertion === undefined && entry?.value ? [entry] : []);
  return asserted.length === 0 ? raw : [...asserted, ...raw.filter(entry => !asserted.some(receipt => receipt.actionStep === entry.actionStep && receipt.value?.kind === entry.value?.kind && receipt.value?.id === entry.value?.id))];
}
