import assert from 'node:assert/strict';
import { createHash, randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF05FencedRecoveryCarrier, assertF05FencedRecoveryDeclaration, assertF05FencedRecoveryIdentity } from '../src/f05-fenced-recovery-carrier.mjs';
import { buildF05FencedRecoveryReceipt, preflightF05FencedRecoveryCarrier } from '../src/run-f05-fenced-recovery-carrier.mjs';
import { loadScenario } from '../src/scenario.mjs';

const source = await readFile(new URL('../scenarios/disposable-f05-fenced-route-recovery.json', import.meta.url));
const declaration = JSON.parse(source); const outerAttempt = 'a0b1c2d3-e4f5-4678-9012-3456789abcde';

test('F0.5 carrier binds the exact route fence before and after its persistent restart', () => {
  const manifest = validManifest(); const sha = digest(source);
  assert.deepEqual(assertF05FencedRecoveryDeclaration(declaration), {
    scenario: 'disposable_f05_fenced_route_recovery', operation: 'operation:supply-1-2', binding: 'recovery:body_resident_1-16' });
  assert.deepEqual(assertF05FencedRecoveryIdentity({ declaration, declarationSha256: sha, manifest, outerAttempt }),
    { outerAttempt, clientRunId: manifest.runId });
  assert.equal(assertF05FencedRecoveryCarrier(manifest).epoch, 2);
  const validated = preflightF05FencedRecoveryCarrier({ declarationSource: source, declarationSha256: sha,
    manifestSource: Buffer.from(JSON.stringify(manifest)), outerAttempt });
  assert.equal(buildF05FencedRecoveryReceipt(validated).recovery.binding, 'recovery:body_resident_1-16');
});

test('F0.5 route recovery declaration passes the runner diagnostic grammar', async () => {
  const loaded = await loadScenario(new URL('../scenarios/disposable-f05-fenced-route-recovery.json', import.meta.url));
  assert.equal(loaded.scenario.id, declaration.id);
});

test('F0.5 carrier rejects a replacement epoch or malformed route declaration', () => {
  const changed = validManifest(); changed.diagnostics[1].observed.value.epoch = 3;
  assert.throws(() => assertF05FencedRecoveryCarrier(changed), /replaced or weakened/);
  const malformed = structuredClone(declaration); malformed.actions[7].id = 'recovery:body_resident_1-28';
  assert.throws(() => assertF05FencedRecoveryDeclaration(malformed), /exact ordinary reclaim path/);
});

function validManifest() {
  const runId = randomUUID(); const sha = digest(source); const value = epoch => ({ kind: 'recovery', id: 'recovery:body_resident_1-16', status: 'ok', asset: 'BODY',
    owner: 'scene:lease_route_recovery', ownerRevision: 7, epoch, phase: 'RUNNING', reversible: true, attempts: 0, nextAction: 'RECLAIM' });
  return { schema: 2, status: 'ok', scenarioId: declaration.id, scenarioSha256: sha, scenarioDeclarationSha256: sha, runId,
    recovery: { mode: 'graceful', splitAfterAction: 9, clientSession: { runId: outerAttempt, reusedJvm: true } },
    actions: declaration.actions.map((action, index) => ({ action, correlation: `scenario:${runId}:${index + 1}` })),
    diagnostics: [{ observed: { actionStep: 9, value: value(2) } }, { observed: { actionStep: 11, value: value(2) } }] };
}
function digest(value) { return createHash('sha256').update(value).digest('hex'); }
