import assert from 'node:assert/strict';
import test from 'node:test';
import { compareFeedbackWorkflowMedians } from '../src/feedback-timing.mjs';

const environment = Object.freeze({ host: 'same-host', preparedArtifactSha256: 'a'.repeat(64), serverClasspathSha256: 'b'.repeat(64),
  clientClasspathSha256: 'c'.repeat(64), jdkSha256: 'd'.repeat(64), sourceContentSha256: 'e'.repeat(64),
  dependencyManifestSha256: 'f'.repeat(64), profile: 'world', viewDistance: 10 });
const workflows = ['process_contract', 'scenario_semantics', 'lifecycle_restart'];

test('feedback timing requires three attributable same-environment samples for every workflow and proves aggregate median speedup', () => {
  const baseline = series('baseline', [300, 600, 900]);
  const candidate = series('candidate', [75, 400, 75]);
  const result = compareFeedbackWorkflowMedians({ baseline, candidate });
  assert.ok(result.speedup >= 3); assert.equal(result.workflows.length, 3);
  assert.ok(result.workflows.some((entry) => entry.speedup < 3), 'the contract is aggregate, not three fabricated per-workflow claims');
});

test('cache-only, missing workflow, identity drift and sub-threshold feedback measurements fail closed', () => {
  const baseline = series('baseline', [300, 600, 900]); const candidate = series('candidate', [150, 300, 450]);
  assert.throws(() => compareFeedbackWorkflowMedians({ baseline, candidate }), /below required/);
  const cacheOnly = structuredClone(candidate); cacheOnly[0].executedTiers = [];
  assert.throws(() => compareFeedbackWorkflowMedians({ baseline, candidate: cacheOnly, requiredSpeedup: 1 }), /cache-only/);
  const hiddenReuse = structuredClone(candidate); hiddenReuse[0].tiers[0].status = 'REUSED';
  assert.throws(() => compareFeedbackWorkflowMedians({ baseline, candidate: hiddenReuse, requiredSpeedup: 1 }), /execution status diverges/);
  const reusedBaseline = structuredClone(baseline); reusedBaseline[0].tiers[0].status = 'REUSED'; reusedBaseline[0].executedTiers = ['T1'];
  assert.throws(() => compareFeedbackWorkflowMedians({ baseline: reusedBaseline, candidate, requiredSpeedup: 1 }), /baseline may not reuse/);
  const drift = structuredClone(candidate); drift[0].environment.host = 'other-host';
  assert.throws(() => compareFeedbackWorkflowMedians({ baseline, candidate: drift, requiredSpeedup: 1 }), /share one exact host/);
  assert.throws(() => compareFeedbackWorkflowMedians({ baseline: baseline.slice(1), candidate, requiredSpeedup: 1 }), /exactly three/);
});

function series(cohort, timings) {
  const executedTiers = cohort === 'baseline' ? ['T0', 'T1'] : ['T0'];
  const secondStatus = cohort === 'baseline' ? 'EXECUTED' : 'REUSED';
  return workflows.flatMap((workflow, index) => [0, 1, 2].map((sample) => ({ schema: 1, kind: 'frontier-v3-f0va-feedback-sample', cohort,
    workflow, status: 'ok', elapsedMillis: timings[index] + sample, selectedTiers: ['T0', 'T1'], executedTiers,
    tiers: [{ tier: 'T0', status: 'EXECUTED', invariant: `${workflow}:schema`, proof: `build/${workflow}-${sample}-T0.json` },
      { tier: 'T1', status: secondStatus, invariant: `${workflow}:pure`, proof: `build/${workflow}-${sample}-T1.json` }],
    terminal: { invariant: `${workflow}:terminal` }, environment })));
}
