const SCENARIO = 'disposable_f06r3_hot_release_cold_terminal';
const JOB = 'job:site-harvest-1-wheat-field-1';
const SITE = 'site:1-wheat-field';
const RECEIPT_FENCE = 'recovery:intent_ecf2d4203d8387d3b971f13d8a72c83768e135885ff45d3dda535aaf5b6dd6e0';

/**
 * The r65 regression boundary starts from the standard ordinary COLD-arrival profile, but no
 * fixture manufactures the result: the client first establishes a real HOT lease while canonical
 * time is held, then releases canonical time, makes the physical crop RUNNING, leaves so its exact
 * lease releases, and the real no-player duty cycle terminally continues the same receipt before a
 * graceful restart and ordinary re-entry.
 */
export function assertF06r3HotReleaseColdTerminalCarrier({ declaration, beforeRestart, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.server?.profile !== 'resource-site-harvest'
      || declaration?.restart?.mode !== 'graceful' || declaration.restart.afterAction !== 7 || declaration.restart.zeroPlayerAdvanceTicks !== 24000
      || declaration.actions?.[2]?.type !== 'release_fast_forward_hold'
      || declaration?.assertNoServerTickStallDuringIngress !== true || beforeRestart?.status !== 'ok'
      || manifest?.status !== 'ok' || manifest.initialCanonicalHold !== true || manifest.scenarioId !== SCENARIO) {
    throw new Error('F0.6R3 HOT-release COLD-terminal carrier lacks its exact causal declaration');
  }
  const release = exactly(beforeRestart, 7, 'process', JOB);
  if (release.claims?.lease !== null || release.result?.sitePhase !== 'HARVESTING'
      || release.result?.intentStatus !== 'RUNNING' || !Number.isInteger(release.conservation?.completedCropSlots)
      || release.conservation.completedCropSlots < 1 || release.conservation.completedCropSlots >= 63
      || release.conservation?.pendingCropSlot !== -1) {
    throw new Error('F0.6R3 HOT-release COLD-terminal carrier lacks its physically observed RUNNING receipt after ordinary demand loss');
  }
  const interlude = manifest.restartZeroPlayerInterlude;
  if (interlude?.status !== 'completed' || interlude.advanceTicks !== 24000 || interlude.clientSegmentsBeforeCompletion !== 0
      || interlude.boundedness?.status !== 'NO_STALL' || interlude.boundedness?.noServerTickStall !== true) {
    throw new Error('F0.6R3 HOT-release COLD-terminal carrier lacks its zero-player terminal duty interval');
  }
  const site = exactly(manifest, 9, 'site', SITE);
  const receiptFence = exactlyRetired(manifest, 10, 'recovery', RECEIPT_FENCE);
  const summary = exactly(manifest, 11, 'summary', '');
  if (site.phase !== 'GROWING' || site.growthEpoch !== 3 || site.growthStage !== 2 || site.activeWork !== '' || site.conflictDisposition !== null
      || receiptFence.asset !== 'EFFECT' || receiptFence.owner !== SITE || receiptFence.disposition !== 'ABANDON'
      || receiptFence.reason !== 'terminal-physical-conflict'
      || summary.inventoryConflicts !== 0) {
    throw new Error('F0.6R3 HOT-release COLD terminal did not bound its superseded RUNNING receipt locally without replay or quarantine');
  }
  const ingress = manifest.clientSegments?.[0]?.ingressResponsiveness;
  if (!Array.isArray(ingress) || ingress.length !== 2 || ingress.some(value => value?.status !== 'ok'
      || value.ordinaryClientJoined !== true || value.noServerTickStall !== true)) {
    throw new Error('F0.6R3 HOT-release COLD-terminal carrier lacks both ordinary responsive ingress receipts');
  }
  return Object.freeze({ job: JOB, receiptFence: RECEIPT_FENCE, releaseCompletedCropSlots: release.conservation.completedCropSlots,
    coldTerminalAfterRelease: true, restarted: true, firstIngressNoReplay: true });
}

function exactly(manifest, actionStep, kind, id) {
  const values = (manifest?.diagnostics ?? []).filter(entry => entry?.actionStep === actionStep && entry.value?.kind === kind && entry.value?.id === id)
    .map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'ok') throw new Error(`F0.6R3 HOT-release COLD-terminal carrier lacks ${kind}:${id} at action ${actionStep}`);
  return values[0];
}

function exactlyRetired(manifest, actionStep, kind, id) {
  const values = (manifest?.diagnostics ?? []).filter(entry => entry?.actionStep === actionStep && entry.value?.kind === kind && entry.value?.id === id)
    .map(entry => entry.value);
  if (values.length !== 1 || values[0].status !== 'retired') throw new Error(`F0.6R3 HOT-release COLD-terminal carrier lacks retired ${kind}:${id} at action ${actionStep}`);
  return values[0];
}
