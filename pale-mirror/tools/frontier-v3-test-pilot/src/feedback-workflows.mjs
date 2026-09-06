import { FEEDBACK_TIMING_SCHEMA } from './feedback-timing.mjs';

export const FEEDBACK_WORKFLOW_CATALOG = Object.freeze({
  process_contract: Object.freeze({
    changedPaths: Object.freeze(['tools/frontier-v3-test-pilot/contracts/resource-site-harvest-f0v.json']),
    expectedCandidateTiers: Object.freeze(['T0', 'T1', 'T3'])
  }),
  scenario_semantics: Object.freeze({
    changedPaths: Object.freeze(['tools/frontier-v3-test-pilot/scenarios/disposable-lite-smoke.json']),
    expectedCandidateTiers: Object.freeze(['T0', 'T1', 'T3'])
  }),
  lifecycle_restart: Object.freeze({
    changedPaths: Object.freeze(['tools/frontier-v3-test-pilot/src/lifecycle-barrier.mjs']),
    expectedCandidateTiers: Object.freeze(['T0', 'T1', 'T2', 'T3'])
  })
});
const ITERATIVE_TIERS = Object.freeze(['T0', 'T1', 'T2', 'T3']);

/** Binds the benchmark classes to the real selector instead of an invented cheap path. */
export function feedbackWorkflowDefinition(workflow, selection) {
  const definition = FEEDBACK_WORKFLOW_CATALOG[workflow];
  if (definition === undefined || !selection || !Array.isArray(selection.tiers) || !Array.isArray(selection.requiredTiers)
      || !Array.isArray(selection.deferredFinalTiers)) throw new Error('feedback workflow definition is malformed');
  if (JSON.stringify(selection.tiers) !== JSON.stringify(definition.expectedCandidateTiers)) {
    throw new Error(`feedback workflow ${workflow} selector drifted from its declared affected tiers`);
  }
  if (selection.requiredTiers.some((tier) => ![...ITERATIVE_TIERS, 'T4'].includes(tier))
      || selection.tiers.some((tier) => !selection.requiredTiers.includes(tier))
      || selection.deferredFinalTiers.some((tier) => !selection.requiredTiers.includes(tier))) {
    throw new Error(`feedback workflow ${workflow} has an invalid selector boundary`);
  }
  return Object.freeze({ workflow, changedPaths: definition.changedPaths, candidateTiers: definition.expectedCandidateTiers,
    baselineTiers: ITERATIVE_TIERS, deferredFinalTiers: Object.freeze([...selection.deferredFinalTiers]) });
}

/** Converts the immutable verification-runner result into attributable median evidence. */
export async function feedbackSampleFromExecution({ cohort, workflow, elapsedMillis, execution, result, readTerminal }) {
  if (!['baseline', 'candidate'].includes(cohort) || typeof workflow !== 'string' || !Number.isFinite(elapsedMillis) || elapsedMillis <= 0
      || !execution || !result || result.status !== 'ok' || typeof readTerminal !== 'function') {
    throw new Error('feedback execution sample is malformed');
  }
  const selectedTiers = execution.tiers.map((tier) => tier.tier);
  if (result.results.length !== selectedTiers.length || result.results.some((entry, index) => entry.tier !== selectedTiers[index])) {
    throw new Error('feedback execution did not retain complete selected-tier results');
  }
  const tiers = [];
  for (const entry of result.results) {
    if (!['EXECUTED', 'REUSED'].includes(entry.status) || typeof entry.proof !== 'string' || entry.proof.length === 0) {
      throw new Error('feedback execution has no attributable tier proof');
    }
    const terminal = await readTerminal(entry.proof);
    if (!terminal || typeof terminal.invariant !== 'string' || terminal.invariant.length === 0) {
      throw new Error('feedback execution tier proof lacks its terminal invariant');
    }
    tiers.push(Object.freeze({ tier: entry.tier, status: entry.status, invariant: terminal.invariant, proof: entry.proof }));
  }
  const executedTiers = tiers.filter((tier) => tier.status === 'EXECUTED').map((tier) => tier.tier);
  const environment = Object.freeze({ host: execution.runtime.host, sourceContentSha256: execution.source.sha256,
    preparedArtifactSha256: execution.build.artifactSha256, serverClasspathSha256: execution.build.serverClasspathSha256,
    clientClasspathSha256: execution.build.clientClasspathSha256, jdkSha256: execution.runtime.jdkSha256,
    dependencyManifestSha256: execution.selection.manifestSha256, profile: 'world', viewDistance: 10 });
  if (typeof environment.host !== 'string' || environment.host.length === 0) throw new Error('feedback execution runtime lacks its host identity');
  return Object.freeze({ schema: FEEDBACK_TIMING_SCHEMA, kind: 'frontier-v3-f0va-feedback-sample', cohort, workflow, status: 'ok', elapsedMillis,
    selectedTiers: Object.freeze(selectedTiers), executedTiers: Object.freeze(executedTiers), tiers: Object.freeze(tiers),
    terminal: Object.freeze({ invariant: `${workflow}:${tiers.map((tier) => `${tier.tier}=${tier.invariant}`).join(',')}` }), environment });
}
