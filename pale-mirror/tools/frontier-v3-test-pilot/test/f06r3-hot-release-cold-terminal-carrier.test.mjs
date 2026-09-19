import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF06r3HotReleaseColdTerminalCarrier } from '../src/f06r3-hot-release-cold-terminal-carrier.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

test('F0.6R3 HOT release carrier rejects the r65 RUNNING receipt terminal quarantine path', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-hot-release-cold-terminal.json'), 'utf8'));
  const value = receipt();
  assert.deepEqual(assertF06r3HotReleaseColdTerminalCarrier({ declaration, ...value }), {
    job: 'job:site-harvest-1-wheat-field-1', receiptFence: 'recovery:intent_ecf2d4203d8387d3b971f13d8a72c83768e135885ff45d3dda535aaf5b6dd6e0', releaseCompletedCropSlots: 1,
    coldTerminalAfterRelease: true, restarted: true, firstIngressNoReplay: true
  });
  const prepared = receipt(); prepared.beforeRestart.diagnostics[0].value.result.intentStatus = 'PREPARED';
  assert.throws(() => assertF06r3HotReleaseColdTerminalCarrier({ declaration, ...prepared }), /physically observed RUNNING/);
  const quarantined = receipt(); quarantined.manifest.diagnostics[2].value.inventoryConflicts = 1;
  assert.throws(() => assertF06r3HotReleaseColdTerminalCarrier({ declaration, ...quarantined }), /without replay or quarantine/);
});

function receipt() {
  const job = 'job:site-harvest-1-wheat-field-1';
  return {
    beforeRestart: { status: 'ok', diagnostics: [{ actionStep: 7, value: { kind: 'process', id: job, status: 'ok', claims: { lease: null },
      result: { sitePhase: 'HARVESTING', intentStatus: 'RUNNING' }, conservation: { completedCropSlots: 1, pendingCropSlot: -1 } } }] },
    manifest: { status: 'ok', initialCanonicalHold: true, scenarioId: 'disposable_f06r3_hot_release_cold_terminal', restartZeroPlayerInterlude: { status: 'completed', advanceTicks: 24000,
      clientSegmentsBeforeCompletion: 0, boundedness: { status: 'NO_STALL', noServerTickStall: true } }, clientSegments: [{ ingressResponsiveness: [
        { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }, { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true }
      ] }], diagnostics: [
        { actionStep: 9, value: { kind: 'site', id: 'site:1-wheat-field', status: 'ok', phase: 'GROWING', growthEpoch: 3, growthStage: 2, activeWork: '', conflictDisposition: null } },
        { actionStep: 10, value: { kind: 'recovery', id: 'recovery:intent_ecf2d4203d8387d3b971f13d8a72c83768e135885ff45d3dda535aaf5b6dd6e0', status: 'retired', asset: 'EFFECT', owner: 'site:1-wheat-field', disposition: 'ABANDON', reason: 'terminal-physical-conflict' } },
        { actionStep: 11, value: { kind: 'summary', id: '', status: 'ok', inventoryConflicts: 0 } }
      ] }
  };
}
