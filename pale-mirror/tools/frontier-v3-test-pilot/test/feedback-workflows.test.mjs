import assert from 'node:assert/strict';
import test from 'node:test';
import { feedbackSampleFromExecution, feedbackWorkflowDefinition } from '../src/feedback-workflows.mjs';

const hashes = { artifactSha256: 'a'.repeat(64), serverClasspathSha256: 'b'.repeat(64), clientClasspathSha256: 'c'.repeat(64) };
const selection = Object.freeze({ manifestSha256: 'd'.repeat(64), tiers: ['T0', 'T1', 'T3'], requiredTiers: ['T0', 'T1', 'T3'], deferredFinalTiers: [] });
const execution = Object.freeze({ tiers: [{ tier: 'T0' }, { tier: 'T1' }, { tier: 'T3' }], source: { sha256: 'e'.repeat(64) },
  build: hashes, runtime: { host: 'same-host', jdkSha256: 'f'.repeat(64) }, selection });
const result = Object.freeze({ status: 'ok', results: [{ tier: 'T0', status: 'REUSED', proof: 'build/T0.json' },
  { tier: 'T1', status: 'EXECUTED', proof: 'build/T1.json' }, { tier: 'T3', status: 'EXECUTED', proof: 'build/T3.json' }] });

test('feedback workflows bind their declared representative edits to the real selected tiers', () => {
  assert.deepEqual(feedbackWorkflowDefinition('process_contract', selection).baselineTiers, ['T0', 'T1', 'T2', 'T3']);
  assert.throws(() => feedbackWorkflowDefinition('process_contract', { ...selection, tiers: ['T0'] }), /selector drifted/);
});

test('feedback samples retain tier-by-tier terminal proof and exact build/runtime identity', async () => {
  const sample = await feedbackSampleFromExecution({ cohort: 'candidate', workflow: 'process_contract', elapsedMillis: 100,
    execution, result, readTerminal: async (proof) => ({ invariant: `terminal:${proof}` }) });
  assert.deepEqual(sample.executedTiers, ['T1', 'T3']); assert.equal(sample.environment.host, 'same-host');
  await assert.rejects(feedbackSampleFromExecution({ cohort: 'candidate', workflow: 'process_contract', elapsedMillis: 100,
    execution, result: { ...result, results: result.results.slice(0, 2) }, readTerminal: async () => ({ invariant: 'ok' }) }), /complete selected-tier/);
});
