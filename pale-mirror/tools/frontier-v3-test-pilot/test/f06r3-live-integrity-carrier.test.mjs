import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import { assertF06r3LiveIntegrityCarrier } from '../src/f06r3-live-integrity-carrier.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

test('F0.6R3 live-integrity carrier rejects a receipt-only field even when its manifest action list is otherwise exact', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-live-integrity.json'), 'utf8'));
  const receiptOnly = structuredClone(declaration);
  receiptOnly.actions[5] = { type: 'inspect', view: 'process', id: 'job:site-harvest-7-wheat-field-1' };
  const manifest = {
    status: 'ok', scenarioId: receiptOnly.id,
    recovery: { mode: 'graceful', splitAfterAction: receiptOnly.restart.afterAction },
    actions: receiptOnly.actions.map(action => ({ action })), diagnostics: []
  };
  assert.throws(() => assertF06r3LiveIntegrityCarrier({ declaration: receiptOnly, manifest }),
    /complete field and its exact COLD prefix/);
});

test('F0.6R3 live-integrity carrier requires a natural post-restart return before inspecting the field', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-live-integrity.json'), 'utf8'));
  const noReturn = structuredClone(declaration);
  noReturn.actions[15] = { type: 'inspect', view: 'process', id: 'job:site-harvest-7-wheat-field-1' };
  const manifest = {
    status: 'ok', scenarioId: noReturn.id,
    recovery: { mode: 'graceful', splitAfterAction: noReturn.restart.afterAction },
    actions: noReturn.actions.map(action => ({ action })), diagnostics: []
  };
  assert.throws(() => assertF06r3LiveIntegrityCarrier({ declaration: noReturn, manifest }),
    /complete field and its exact COLD prefix/);
});

test('F0.6R3 live-integrity carrier rejects a crop-only endpoint in place of the complete facility probe', async () => {
  const declaration = JSON.parse(await readFile(resolve(root, 'scenarios/disposable-f06r3-live-integrity.json'), 'utf8'));
  const cropOnly = structuredClone(declaration);
  cropOnly.actions[9] = { type: 'wait_until_block', position: { diagnostic: { view: 'site', id: 'site:7-wheat-field', field: 'lastCrop' } }, block: 'minecraft:wheat', timeoutMs: 30000 };
  const manifest = {
    status: 'ok', scenarioId: cropOnly.id,
    recovery: { mode: 'graceful', splitAfterAction: cropOnly.restart.afterAction },
    actions: cropOnly.actions.map(action => ({ action })), diagnostics: []
  };
  assert.throws(() => assertF06r3LiveIntegrityCarrier({ declaration: cropOnly, manifest }),
    /complete field and its exact COLD prefix/);
});
