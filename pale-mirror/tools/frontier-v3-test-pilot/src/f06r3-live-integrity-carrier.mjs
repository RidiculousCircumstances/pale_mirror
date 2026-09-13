import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_f06r3_live_integrity';
const JOBS = Object.freeze(['job:site-harvest-7-wheat-field-1', 'job:site-harvest-4-wheat-field-1']);
const HIVE_ORGANS = Object.freeze(['west-ganglion', 'west-brood', 'west-store', 'west-hibernaculum-1', 'west-hibernaculum-2', 'west-hibernaculum-3',
  'east-ganglion', 'east-brood', 'east-store', 'east-hibernaculum-1', 'east-hibernaculum-2', 'east-hibernaculum-3']);

/** Terminal domain predicate for the three user-observed F0.6R3 regressions. */
export function assertF06r3LiveIntegrityCarrier({ declaration, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.zeroPlayerPrelude?.advanceTicks !== 24_000 || declaration?.restart?.mode !== 'graceful'
      || declaration.restart.afterAction !== 9 || !Array.isArray(declaration.actions) || manifest?.status !== 'ok'
      || manifest.scenarioId !== SCENARIO || manifest.recovery?.mode !== 'graceful' || manifest.recovery?.splitAfterAction !== 9
      || !Array.isArray(manifest.actions) || manifest.actions.length !== declaration.actions.length) {
    throw new Error('F0.6R3 live-integrity manifest lacks its declared zero-player/restart lifecycle');
  }
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) throw new Error('F0.6R3 live-integrity manifest substituted an action');
  });
  const terminalAssertionCount = assertDeclaredAssertions(declaration, manifest);
  const prelude = manifest.zeroPlayerPrelude;
  if (prelude?.status !== 'completed' || prelude.advanceTicks !== 24_000 || prelude.clientSegmentsBeforeCompletion !== 0) {
    throw new Error('F0.6R3 did not establish its COLD history before a natural test-player ingress');
  }
  const diagnostics = observed(manifest);
  const coldInventory = exactly(diagnostics, 1, 'process_inventory', 'settlements');
  const projectionWork = exactly(diagnostics, 2, 'projection_work', '');
  const postArrivalInventory = exactly(diagnostics, 9, 'process_inventory', 'settlements');
  assertAllSettlementHarvestes(coldInventory, 'zero-player');
  assertProjectionWork(projectionWork);
  assertNoIngressReset(postArrivalInventory, coldInventory);
  const beforeSeven = exactly(diagnostics, 3, 'process', JOBS[0]);
  const beforeFour = exactly(diagnostics, 4, 'process', JOBS[1]);
  const afterSeven = exactly(diagnostics, 6, 'process', JOBS[0]);
  const afterFour = exactly(diagnostics, 8, 'process', JOBS[1]);
  const restartSeven = exactly(diagnostics, 10, 'process', JOBS[0]);
  [beforeSeven, beforeFour, afterSeven, afterFour, restartSeven].forEach(assertHarvestCursor);
  if (!sameHarvest(beforeSeven, afterSeven) || !sameHarvest(beforeSeven, restartSeven) || !sameHarvest(beforeFour, afterFour)) {
    throw new Error('F0.6R3 representative first ingress or restart reset an exact COLD harvest identity/cursor');
  }
  for (const organ of HIVE_ORGANS) {
    const step = organ.startsWith('west-') ? 13 + HIVE_ORGANS.indexOf(organ) : 24 + HIVE_ORGANS.indexOf(organ) - 6;
    const foundry = exactly(diagnostics, step, 'hive_foundry', `settled@organ:${organ}`);
    if (foundry.phase !== 'SETTLED' || foundry.passed !== true || foundry.auditPassed !== true || foundry.runtimePending !== 0
        || foundry.runtimeMismatch !== 0 || foundry.runtimeUnverified !== 0 || foundry.blockers !== 0 || foundry.errors !== 0) {
      throw new Error(`F0.6R3 first-visible nest organ is not coherent: ${organ}`);
    }
  }
  if (!Array.isArray(manifest.frames) || manifest.frames.length !== 2
      || !manifest.frames.every(frame => frame.presentation === 'player' && typeof frame.path === 'string' && frame.path.endsWith('.png'))) {
    throw new Error('F0.6R3 both seed nests lack their declared player-height first-visible frames');
  }
  return Object.freeze({ terminalAssertionCount, zeroPlayerAllSites: coldInventory.count, projectionWork: Object.freeze({
    structural: projectionWork.structural, infectionOverlay: projectionWork.infectionOverlay }),
    coldCursors: Object.freeze(Object.fromEntries(coldInventory.entries.map(entry => [entry.site, entry.cursor]))), representativeFirstArrivals: JOBS,
    restartContinuity: true, seedNestOrgans: HIVE_ORGANS.length, playerFrames: manifest.frames.map(frame => frame.path) });
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

function assertAllSettlementHarvestes(inventory, boundary) {
  if (inventory.count !== 12 || !Array.isArray(inventory.entries) || inventory.entries.length !== 12) {
    throw new Error(`F0.6R3 ${boundary} process inventory does not contain all current settlements`);
  }
  for (let site = 1; site <= 12; site++) {
    const expected = `site:${site}-wheat-field`;
    const entry = inventory.entries.find(value => value.site === expected);
    if (entry?.phase !== 'HARVESTING' || entry.job !== `job:site-harvest-${site}-wheat-field-1` || typeof entry.worker !== 'string'
        || !entry.worker.startsWith(`resident:${site}-`) || !Number.isSafeInteger(entry.cursor) || entry.cursor <= 0
        || entry.cursor !== entry.cropCursor || !Number.isSafeInteger(entry.cursorLength) || entry.cursor >= entry.cursorLength
        || entry.waitReason !== 'AWAITING_HOT_CROP_EFFECT'
        || !Number.isSafeInteger(entry.dueAt) || entry.dueAt < 0) {
      throw new Error(`F0.6R3 ${boundary} COLD progress is absent or ambiguous for ${expected}`);
    }
  }
}

function assertNoIngressReset(inventory, cold) {
  assertAllSettlementHarvestes(inventory, 'post-arrival');
  for (const before of cold.entries) {
    const after = inventory.entries.find(value => value.site === before.site);
    if (!after || after.job !== before.job || after.worker !== before.worker || after.cursor !== before.cursor || after.cropCursor !== before.cropCursor) {
      throw new Error(`F0.6R3 first ingress reset ${before.site}`);
    }
  }
}

function assertHarvestCursor(value) {
  if (value.identity?.job == null || value.identity?.worker == null || !Number.isSafeInteger(value.cursor?.index) || value.cursor.index <= 0
      || !Number.isSafeInteger(value.cursor?.length) || value.cursor.index >= value.cursor.length
      || value.conservation?.completedCropSlots !== 0 || value.conservation?.pendingCropSlot !== -1
      || value.result?.sitePhase !== 'HARVESTING' || value.result?.intentStatus !== 'PREPARED') {
    throw new Error('F0.6R3 representative harvest lacks its exact pre-effect COLD cursor');
  }
}

function sameHarvest(left, right) {
  return left.identity?.job === right.identity?.job && left.identity?.worker === right.identity?.worker
    && left.claims?.intent === right.claims?.intent && left.cursor?.index === right.cursor?.index
    && left.cursor?.length === right.cursor?.length && left.conservation?.completedCropSlots === right.conservation?.completedCropSlots;
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
