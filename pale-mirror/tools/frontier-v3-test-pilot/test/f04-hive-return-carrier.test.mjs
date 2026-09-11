import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF04HiveReturnCarrier, assertF04HiveReturnDeclaration, assertF04HiveReturnIdentity } from '../src/f04-hive-return-carrier.mjs';
import { buildF04HiveReturnReceipt, isolatedScenarioSupervisorInvocation, preflightF04HiveReturnCarrier } from '../src/run-f04-hive-return-carrier.mjs';

const ids = ['actor:hive-return-a', 'actor:hive-return-b', 'actor:hive-return-c', 'actor:hive-return-d'];
const positions = Object.freeze(ids.map((id, index) => ({ id, x: -360 + index, y: 64, z: -340 })));

function snapshot(returnedMembers, survivorPositions = positions) {
  return { kind: 'hive_mobilization', id: 'mobilization:development-hive-mobilization', status: 'ok', mobilizationStatus: 'RETURNING',
    survivors: 4, returnedMembers, returnComplete: false, survivorPositions,
    physicalSurvivors: survivorPositions.map(value => ({ id: value.id, observed: { x: value.x, y: value.y + 1, z: value.z } })) };
}

const declarationSource = await readFile(new URL('../scenarios/disposable-hive-return-restart.json', import.meta.url));
const declaration = JSON.parse(declarationSource); const declarationSha256 = createHash('sha256').update(declarationSource).digest('hex');
const outerAttempt = '00000000-0000-0000-0000-000000000041'; const clientRunId = '00000000-0000-0000-0000-000000000042';

function manifest(before = snapshot(0), after = snapshot(0)) {
  return { status: 'ok', scenarioId: 'disposable_hive_return_restart', recovery: { mode: 'graceful', splitAfterAction: 3 },
    actions: Array.from({ length: 5 }, () => ({})), diagnostics: [{ observed: { actionStep: 3, value: before } }, { observed: { actionStep: 5, value: after } }] };
}

function persistentManifest(before = snapshot(0), after = snapshot(0)) {
  const value = manifest(before, after);
  value.schema = 2; value.scenarioSha256 = declarationSha256; value.scenarioDeclarationSha256 = declarationSha256; value.runId = clientRunId;
  value.recovery.clientSession = { runId: outerAttempt, reusedJvm: true };
  value.actions = declaration.actions.map((action, index) => ({ action, correlation: `scenario:${clientRunId}:${index + 1}` }));
  value.clientSegments = [{ segment: 'persistent_restart', runId: clientRunId, reusedJvm: true }];
  return value;
}

test('F0.4 declaration binds the return endpoint rather than a synthetic scene', () => {
  assert.deepEqual(assertF04HiveReturnDeclaration(declaration).returnFrontier, { x: 417, y: 64, z: 420 });
  const wrong = structuredClone(declaration); wrong.actions[0].position.z = -352;
  assert.throws(() => assertF04HiveReturnDeclaration(wrong), /exact retained-return lifecycle/);
});

test('F0.4 return carrier retains canonical survivor positions over its exact graceful restart', () => {
  const receipt = assertF04HiveReturnCarrier(manifest());
  assert.deepEqual(receipt.survivors, positions);
  assert.deepEqual(receipt.recovered, positions);
});

test('F0.4 persistent identity rejects a replayed client action before consuming survivor facts', () => {
  const valid = persistentManifest();
  assert.deepEqual(assertF04HiveReturnIdentity({ declaration, declarationSha256, manifest: valid, outerAttempt }), { outerAttempt, clientRunId });
  valid.actions[4].correlation = `scenario:${clientRunId}:4`;
  assert.throws(() => assertF04HiveReturnIdentity({ declaration, declarationSha256, manifest: valid, outerAttempt }), /stale or foreign action/);
});

test('F0.4 native preflight binds exact declaration, persistent manifest, and nonempty JFR receipt', () => {
  const value = persistentManifest(); const manifestSource = Buffer.from(`${JSON.stringify(value)}\n`);
  const validated = preflightF04HiveReturnCarrier({ declarationSource, declarationSha256, manifestSource, outerAttempt });
  const receipt = buildF04HiveReturnReceipt(validated, { file: '/checkout/build/profiles/f04.jfr', sha256: 'a'.repeat(64), bytes: 4 });
  assert.equal(receipt.finalManifest.sha256, createHash('sha256').update(manifestSource).digest('hex'));
  value.scenarioDeclarationSha256 = 'b'.repeat(64);
  assert.throws(() => preflightF04HiveReturnCarrier({ declarationSource, declarationSha256,
    manifestSource: Buffer.from(JSON.stringify(value)), outerAttempt }), /foreign persistent manifest/);
});

test('F0.4 native invocation gives the scenario one dedicated evidence process directory', () => {
  const invocation = isolatedScenarioSupervisorInvocation({ scenarioPath: '/checkout/scenario.json', manifestPath: '/checkout/manifest.json',
    root: '/checkout/evidence', outerAttempt });
  assert.equal(invocation.options.env.FRONTIER_V3_NATIVE_PROCESS_ROOT, '/checkout/evidence/process');
});

test('F0.4 return carrier permits only recorded physical return progress and rejects an unstamped reset', () => {
  const progressed = positions.map((value, index) => ({ ...value, z: value.z + index + 1 }));
  assert.equal(assertF04HiveReturnCarrier(manifest(snapshot(0), snapshot(1, progressed))).mobilization,
    'mobilization:development-hive-mobilization');
  assert.equal(assertF04HiveReturnCarrier(manifest(snapshot(0), snapshot(0, progressed))).mobilization,
    'mobilization:development-hive-mobilization');
  const replacement = positions.map((value, index) => ({ ...value, id: index === 0 ? 'actor:replacement' : value.id }));
  assert.throws(() => assertF04HiveReturnCarrier(manifest(snapshot(0), snapshot(1, replacement))), /reset, replacement, loss/);
});
