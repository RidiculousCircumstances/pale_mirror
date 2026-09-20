const SCENARIO = 'disposable_f06r3_first_visibility_cold_continuity';
const SITE = 'site:7-wheat-field';

/**
 * Binds a current, natural COLD/restart lineage to the first player-visible
 * graybox field.  The worker and successor are taken from the canonical
 * terminal receipt, never from a fixed epoch-specific scenario identifier.
 */
export function assertF06r3FirstVisibilityColdContinuityCarrier({ declaration, beforeRestart, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.isolation?.mode !== 'disposable_lite'
      || declaration?.zeroPlayerPrelude?.advanceTicks !== 24_000 || declaration?.restart?.mode !== 'graceful'
      || declaration.restart.afterAction !== 2 || declaration.actions?.[2]?.dimension !== 'pale_mirror:frontier_graybox'
      || declaration.actions[2].position?.diagnostic?.view !== 'site' || declaration.actions[2].position.diagnostic.id !== SITE
      || declaration.actions?.[3]?.type !== 'look' || declaration.actions[3].position?.diagnostic?.field !== 'firstCrop'
      || declaration.actions?.[5]?.type !== 'assert_visible_board' || declaration.actions[5].text !== 'WHEAT FIELD'
      || declaration.actions[5].position?.x !== 141 || declaration.actions[5].position?.y !== 67 || declaration.actions[5].position?.z !== -8
      || declaration?.assertNoServerTickStallDuringIngress !== true || manifest?.status !== 'ok'
      || manifest.scenarioId !== SCENARIO || !Array.isArray(manifest.frames) || manifest.frames.length !== 3
      || !manifest.frames.every(frame => frame?.presentation === 'player' && typeof frame.path === 'string' && frame.path.endsWith('.png'))) {
    throw new Error('F0.6R3 first-visibility carrier lacks its exact COLD/restart/player-frame declaration');
  }
  const prelude = manifest.zeroPlayerPrelude;
  const request = prelude?.performance?.fastForwardRequests?.at(-1);
  if (prelude?.status !== 'completed' || prelude.advanceTicks !== 24_000 || prelude.clientSegmentsBeforeCompletion !== 0
      || request?.status !== 'COMPLETED' || request?.requestedTicks !== 24_000 || manifest.restartZeroPlayerInterlude?.status !== 'completed') {
    throw new Error('F0.6R3 first-visibility carrier lacks its completed observer-free COLD/restart history');
  }
  const before = exactly(beforeRestart, 1, 'site', SITE);
  const population = exactly(beforeRestart, 2, 'settlement_population', 'settlement:7');
  const indexed = exactly(manifest, 10, 'settlement_population', 'settlement:7');
  const visible = exactly(manifest, 9, 'pilot_settlement_population', 'settlement:7');
  const after = exactly(manifest, 12, 'site', SITE);
  const settlement = exactly(manifest, 13, 'settlement', 'settlement:7');
  const terminal = before.terminalHarvest;
  const worker = terminal?.worker;
  if (before.phase !== 'GROWING' || before.activeWork !== '' || before.conflictDisposition !== null
      || typeof worker !== 'string' || !worker.startsWith('resident:7-') || !terminal?.canonicalSuccessor
      || typeof terminal.successor?.job !== 'string' || typeof terminal.successor?.worker !== 'string') {
    throw new Error('F0.6R3 current field does not expose one exact completed COLD lineage');
  }
  const canonicalWorker = exactlyResident(population, worker, 'canonical');
  const indexedWorker = exactlyResident(indexed, worker, 'indexed');
  const visibleWorker = exactlyResident(visible, worker, 'visible');
  if (indexedWorker.admission !== 'INDEXED' || indexedWorker.entityUuid !== canonicalWorker.entityUuid
      || canonicalWorker.entityUuid !== visibleWorker.entityUuid || canonicalWorker.dutyPhase !== visibleWorker.dutyPhase
      || !sameStation(canonicalWorker.position, visibleWorker.first)
      || visibleWorker.maxDisplacement > .45 || visibleWorker.maxStep > .2) {
    throw new Error('F0.6R3 first visibility replaced, converged, or arrival-started the current terminal worker');
  }
  if (after.phase !== before.phase || after.growthEpoch !== before.growthEpoch || after.growthStage !== before.growthStage
      || after.activeWork !== before.activeWork || after.terminalHarvest?.job !== terminal.job
      || after.terminalHarvest?.worker !== worker || settlement.harvestAdmission === 'FARMERS_NEEDED') {
    throw new Error('F0.6R3 first visibility reset current COLD field history or misreported its worker admission');
  }
  const ingress = manifest.clientSegments?.flatMap(segment => Array.isArray(segment?.ingressResponsiveness)
    ? segment.ingressResponsiveness : [segment?.ingressResponsiveness]).filter(Boolean) ?? [];
  if (!ingress.some(receipt => receipt.status === 'ok' && receipt.ordinaryClientJoined === true && receipt.noServerTickStall === true)) {
    throw new Error('F0.6R3 first visibility lacks its ordinary responsive client ingress receipt');
  }
  return Object.freeze({ worker, workerUuid: canonicalWorker.entityUuid, successorJob: terminal.successor.job,
    successorWorker: terminal.successor.worker, growthEpoch: before.growthEpoch, growthStage: before.growthStage,
    frames: manifest.frames.map(frame => frame.path), fieldBoard: { text: declaration.actions[5].text, position: declaration.actions[5].position } });
}

function exactly(manifest, actionStep, kind, id) {
  const rows = (manifest?.diagnostics ?? []).filter(entry => entry?.actionStep === actionStep
    && entry.value?.kind === kind && entry.value?.id === id).map(entry => entry.value);
  if (rows.length !== 1 || rows[0].status !== 'ok') throw new Error(`F0.6R3 first-visibility carrier lacks ${kind}:${id} at action ${actionStep}`);
  return rows[0];
}
function exactlyResident(population, actor, label) {
  const rows = Array.isArray(population?.residents) ? population.residents.filter(row => row?.actor === actor) : [];
  if (rows.length !== 1 || typeof rows[0].entityUuid !== 'string' || typeof rows[0].dutyPhase !== 'string'
      || !position(rows[0].position ?? rows[0].first)) throw new Error(`F0.6R3 ${label} worker receipt is incomplete`);
  return rows[0];
}
function position(value) { return value !== null && typeof value === 'object' && [value.x, value.y, value.z].every(Number.isFinite); }
function sameStation(canonical, visible) { return position(canonical) && position(visible)
  && Math.abs(canonical.x + .5 - visible.x) <= .35 && Math.abs(canonical.y - visible.y) <= .35 && Math.abs(canonical.z + .5 - visible.z) <= .35; }
