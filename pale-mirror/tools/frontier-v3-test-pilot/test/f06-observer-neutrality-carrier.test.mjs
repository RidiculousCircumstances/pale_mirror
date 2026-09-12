import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF06ObserverNeutralityCarrier, assertF06ObserverNeutralityDeclaration } from '../src/f06-observer-neutrality-carrier.mjs';
import { buildF06ObserverNeutralityReceipt, isolatedScenarioSupervisorInvocation, preflightF06ObserverNeutralityCarrier } from '../src/run-f06-observer-neutrality-carrier.mjs';
import { validateScenario } from '../src/scenario.mjs';

const declaration = JSON.parse(await readFile(new URL('../scenarios/disposable-f06-observer-neutrality.json', import.meta.url)));

function observed(actionStep, value) { return { observed: { actionStep, value } }; }
function manifest() {
  return { status: 'ok', scenarioId: declaration.id, recovery: { mode: 'graceful', splitAfterAction: 10 },
    actions: declaration.actions.map(action => ({ action: structuredClone(action) })), diagnostics: [
      observed(2, ingress()), observed(3, { kind: 'first_visibility', id: '24,-23', status: 'ok', visibility: 'READY', staticCells: 18, replicaRevision: 71 }),
      observed(10, conflict()), observed(11, conflict()), observed(13, ingress()), observed(14, conflict())
    ] };
}
function ingress() { return { kind: 'visit_ingress', id: 'ordinary_visit', targetDimensionSeen: true, targetChunkSeen: true,
  finalClientDimension: 'pale_mirror:frontier_graybox', finalClientPosition: { x: 388, y: 65, z: -355 } }; }
function conflict() { return { kind: 'site', id: 'site:4-wheat-field', status: 'ok', phase: 'CONFLICT' }; }

test('F0.6 declaration admits the production first-visibility vocabulary and bounds ordinary intervention continuity', () => {
  validateScenario(declaration);
  assert.deepEqual(assertF06ObserverNeutralityDeclaration(declaration).arrival, { x: 388, y: 65, z: -355 });
  const stale = structuredClone(declaration); stale.actions[2].id = '23,-22';
  assert.throws(() => assertF06ObserverNeutralityDeclaration(stale), /first-visibility/);
  const unsafe = structuredClone(declaration); [unsafe.actions[0], unsafe.actions[1]] = [unsafe.actions[1], unsafe.actions[0]];
  assert.throws(() => assertF06ObserverNeutralityDeclaration(unsafe), /pre-ingress safe advance/);
  const unrelated = structuredClone(declaration); unrelated.actions[8].position = { x: 388, y: 64, z: -355 };
  assert.throws(() => assertF06ObserverNeutralityDeclaration(unrelated), /exact field anchor/);
  const cachedReturn = structuredClone(declaration); cachedReturn.actions[13] = {
    type: 'wait_until_diagnostic', view: 'site', id: 'site:4-wheat-field', expect: { status: 'ok', phase: 'CONFLICT' }, timeoutMs: 30000
  };
  assert.throws(() => assertF06ObserverNeutralityDeclaration(cachedReturn), /first-visibility\/intervention continuity/);
});

test('F0.6 carrier requires natural static readiness before visible/dynamic work and retains intervention through restart/return', () => {
  const facts = assertF06ObserverNeutralityCarrier({ declaration, manifest: manifest() });
  assert.deepEqual(facts, { chunk: '24,-23', replicaRevision: 71, staticCells: 18,
    intervention: 'site:4-wheat-field', restartContinuity: true, returnContinuity: true });
});

test('F0.6 carrier reads assertion-bound facts once when a native manifest also retains raw diagnostic context', () => {
  const value = manifest();
  value.diagnostics.push({ actionStep: 3, value: structuredClone(value.diagnostics[1].observed.value) });
  assert.equal(assertF06ObserverNeutralityCarrier({ declaration, manifest: value }).chunk, '24,-23');
});

test('F0.6 carrier rejects an unobserved static chunk, client-only arrival, and replayed conflict', () => {
  for (const mutate of [
    value => { value.diagnostics[1].observed.value.visibility = 'STATIC_CURRENT'; },
    value => { value.diagnostics[1].observed.value.staticCells = 0; },
    value => { value.actions[1].action.dimension = 'minecraft:overworld'; },
    value => { value.diagnostics[3].observed.value.phase = 'GROWING'; },
    value => { value.diagnostics.push(structuredClone(value.diagnostics[1])); }
  ]) {
    const value = manifest(); mutate(value);
    assert.throws(() => assertF06ObserverNeutralityCarrier({ declaration, manifest: value }), /F0\.6/);
  }
});

test('F0.6 preflight binds the exact declaration bytes, manifest digest, and private process root', async () => {
  const source = await readFile(new URL('../scenarios/disposable-f06-observer-neutrality.json', import.meta.url));
  const terminal = Buffer.from(`${JSON.stringify(manifest())}\n`);
  const declarationSha256 = (await import('node:crypto')).createHash('sha256').update(source).digest('hex');
  const validated = preflightF06ObserverNeutralityCarrier({ declarationSource: source, declarationSha256, manifestSource: terminal });
  assert.equal(buildF06ObserverNeutralityReceipt(validated).finalManifest.sha256,
    (await import('node:crypto')).createHash('sha256').update(terminal).digest('hex'));
  const invocation = isolatedScenarioSupervisorInvocation({ scenarioPath: '/checkout/scenario.json', manifestPath: '/checkout/manifest.json',
    root: '/checkout/evidence', outerAttempt: '00000000-0000-0000-0000-000000000061' });
  assert.equal(invocation.options.env.FRONTIER_V3_NATIVE_PROCESS_ROOT, '/checkout/evidence/process');
});
