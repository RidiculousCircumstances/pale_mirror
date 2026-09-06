import assert from 'node:assert/strict';
import test from 'node:test';
import { independentMatrixPlan, validateIndependentMatrixEvidence } from '../src/independent-matrix.mjs';
import { isolatedScenarioInvocation } from '../src/run-f0va-independent-matrix.mjs';

const build = Object.freeze({ preparedArtifact: { sha256: 'a'.repeat(64) } });
const smoke = Object.freeze({ id: 'smoke' }); const graceful = Object.freeze({ id: 'graceful', restart: { mode: 'graceful' } });

function evidence(plan) {
  return plan.segments.map((segment, index) => ({ status: 'ok', runId: `run-${index}`, build,
    timing: { totalMillis: 100 + index }, isolation: { world: `world-${index}`, freshWorld: true },
    recovery: segment.restart ? { mode: 'graceful', world: `world-${index}` } : null,
    clientSegments: segment.restart ? [{}, {}] : [{}] }));
}

test('independent native matrix requires three distinct worlds and ordinary non-reused client sessions', () => {
  const plan = independentMatrixPlan({ build, smokeScenario: smoke, gracefulScenario: graceful });
  const result = validateIndependentMatrixEvidence(plan, evidence(plan));
  assert.equal(result.segmentCount, 3); assert.equal(result.totalMillis, 303);
});

test('independent native matrix rejects a persistent graceful client, a reused world and build drift', () => {
  const plan = independentMatrixPlan({ build, smokeScenario: smoke, gracefulScenario: graceful });
  const persistent = evidence(plan); persistent[1].recovery.clientSession = { reusedJvm: true };
  assert.throws(() => validateIndependentMatrixEvidence(plan, persistent), /reused a client/);
  const reusedWorld = evidence(plan); reusedWorld[2].isolation.world = reusedWorld[0].isolation.world;
  assert.throws(() => validateIndependentMatrixEvidence(plan, reusedWorld), /reused a run or world/);
  const drift = evidence(plan); drift[0].build = { preparedArtifact: { sha256: 'b'.repeat(64) } };
  assert.throws(() => validateIndependentMatrixEvidence(plan, drift), /exact successful identity/);
});

test('independent baseline invokes the isolated runner with its concrete scenario and manifest paths', () => {
  assert.deepEqual(isolatedScenarioInvocation('/runner.mjs', '/scenario.json', '/manifest.json'),
    ['/runner.mjs', '/scenario.json', '/manifest.json']);
  assert.throws(() => isolatedScenarioInvocation('/runner.mjs', undefined, '/manifest.json'), /malformed/);
});
