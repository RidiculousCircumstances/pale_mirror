import { median } from './timing.mjs';

export const FEEDBACK_TIMING_SCHEMA = 1;
export const FEEDBACK_WORKFLOWS = Object.freeze(['process_contract', 'scenario_semantics', 'lifecycle_restart']);
const TIERS = Object.freeze(['T0', 'T1', 'T2', 'T3', 'T4']);

/**
 * F0.VA's feedback claim is deliberately about complete useful workflows, not a cheap cache
 * lookup.  Each sample must show an attributable terminal result and at least one actually
 * executed semantically selected tier.  Three same-host medians are summed, then compared.
 */
export function compareFeedbackWorkflowMedians({ baseline, candidate, requiredSpeedup = 3 }) {
  const checkedBaseline = validateSeries('baseline', baseline); const checkedCandidate = validateSeries('candidate', candidate);
  if (!Number.isFinite(requiredSpeedup) || requiredSpeedup < 1) throw new Error('feedback timing speedup requirement is malformed');
  const baselineByWorkflow = byWorkflow(checkedBaseline); const candidateByWorkflow = byWorkflow(checkedCandidate);
  const summaries = [];
  for (const workflow of FEEDBACK_WORKFLOWS) {
    const former = baselineByWorkflow.get(workflow); const accelerated = candidateByWorkflow.get(workflow);
    if (former === undefined || accelerated === undefined) throw new Error(`feedback timing lacks ${workflow} samples`);
    sameEnvironment(workflow, former, accelerated);
    const baselineMedianMillis = median(former.map((sample) => sample.elapsedMillis));
    const candidateMedianMillis = median(accelerated.map((sample) => sample.elapsedMillis));
    summaries.push(Object.freeze({ workflow, baselineMedianMillis, candidateMedianMillis,
      speedup: baselineMedianMillis / candidateMedianMillis, baselineTiers: former[0].selectedTiers,
      candidateTiers: accelerated[0].selectedTiers,
      baselineExecutedTiers: former[0].executedTiers, candidateExecutedTiers: accelerated[0].executedTiers }));
  }
  const baselineMillis = summaries.reduce((total, workflow) => total + workflow.baselineMedianMillis, 0);
  const candidateMillis = summaries.reduce((total, workflow) => total + workflow.candidateMedianMillis, 0);
  const speedup = baselineMillis / candidateMillis;
  if (speedup < requiredSpeedup) throw new Error(`feedback workflow speedup ${speedup.toFixed(3)}x is below required ${requiredSpeedup.toFixed(3)}x`);
  return Object.freeze({ schema: FEEDBACK_TIMING_SCHEMA, kind: 'frontier-v3-f0va-feedback-timing', status: 'ok',
    requiredSpeedup, baselineMillis, candidateMillis, speedup, workflows: Object.freeze(summaries) });
}

function validateSeries(label, samples) {
  if (!Array.isArray(samples) || samples.length !== FEEDBACK_WORKFLOWS.length * 3) throw new Error(`feedback ${label} needs exactly three samples per workflow`);
  const checked = samples.map((sample) => validateSample(label, sample));
  for (const workflow of FEEDBACK_WORKFLOWS) if (checked.filter((sample) => sample.workflow === workflow).length !== 3) {
    throw new Error(`feedback ${label} needs exactly three ${workflow} samples`);
  }
  return Object.freeze(checked);
}
function validateSample(cohort, value) {
  if (!value || value.schema !== FEEDBACK_TIMING_SCHEMA || value.kind !== 'frontier-v3-f0va-feedback-sample' || value.cohort !== cohort
      || !FEEDBACK_WORKFLOWS.includes(value.workflow) || value.status !== 'ok' || !Number.isFinite(value.elapsedMillis) || value.elapsedMillis <= 0
      || !Array.isArray(value.selectedTiers) || value.selectedTiers.length === 0 || new Set(value.selectedTiers).size !== value.selectedTiers.length
      || value.selectedTiers.some((tier) => !TIERS.includes(tier))
      || !Array.isArray(value.executedTiers) || value.executedTiers.length === 0 || value.executedTiers.some((tier) => !value.selectedTiers.includes(tier))
      || !value.terminal || typeof value.terminal !== 'object' || Array.isArray(value.terminal) || typeof value.terminal.invariant !== 'string'
      || value.terminal.invariant.length === 0 || !Array.isArray(value.tiers) || value.tiers.length !== value.selectedTiers.length
      || !value.environment || typeof value.environment !== 'object' || Array.isArray(value.environment)) {
    throw new Error(`feedback ${cohort} sample is malformed or cache-only`);
  }
  const evidence = validateTierEvidence(value.selectedTiers, value.executedTiers, value.tiers, cohort);
  const required = ['host', 'sourceContentSha256', 'preparedArtifactSha256', 'serverClasspathSha256', 'clientClasspathSha256',
    'jdkSha256', 'dependencyManifestSha256', 'profile', 'viewDistance'];
  if (required.some((key) => typeof value.environment[key] !== 'string' && typeof value.environment[key] !== 'number')) {
    throw new Error(`feedback ${cohort} sample lacks exact environment identity`);
  }
  return Object.freeze({ schema: value.schema, kind: value.kind, cohort: value.cohort, workflow: value.workflow, status: value.status,
    elapsedMillis: value.elapsedMillis, selectedTiers: Object.freeze([...value.selectedTiers]), executedTiers: Object.freeze([...value.executedTiers]),
    tiers: evidence, terminal: Object.freeze({ invariant: value.terminal.invariant }),
    environment: Object.freeze(Object.fromEntries(required.map((key) => [key, value.environment[key]]))) });
}
function validateTierEvidence(selectedTiers, executedTiers, entries, cohort) {
  const selected = new Set(selectedTiers); const executed = new Set(executedTiers); const seen = new Set();
  const checked = entries.map((entry) => {
    if (!entry || !selected.has(entry.tier) || seen.has(entry.tier) || !['EXECUTED', 'REUSED'].includes(entry.status)
        || typeof entry.invariant !== 'string' || entry.invariant.length === 0 || typeof entry.proof !== 'string' || entry.proof.length === 0) {
      throw new Error(`feedback ${cohort} sample has malformed tier evidence`);
    }
    seen.add(entry.tier);
    if ((entry.status === 'EXECUTED') !== executed.has(entry.tier)) throw new Error(`feedback ${cohort} tier execution status diverges from evidence`);
    return Object.freeze({ tier: entry.tier, status: entry.status, invariant: entry.invariant, proof: entry.proof });
  });
  if (seen.size !== selected.size || selectedTiers.some((tier) => !seen.has(tier))) throw new Error(`feedback ${cohort} sample lacks selected-tier evidence`);
  if (cohort === 'baseline' && checked.some((entry) => entry.status !== 'EXECUTED')) throw new Error('feedback baseline may not reuse cached evidence');
  return Object.freeze(checked);
}
function byWorkflow(samples) { return new Map(FEEDBACK_WORKFLOWS.map((workflow) => [workflow, samples.filter((sample) => sample.workflow === workflow)])); }
function sameEnvironment(workflow, baseline, candidate) {
  const identity = stableEnvironment(baseline[0].environment);
  for (const sample of [...baseline, ...candidate]) if (stableEnvironment(sample.environment) !== identity) {
    throw new Error(`feedback ${workflow} samples do not share one exact host/build/runtime environment`);
  }
}
function stableEnvironment(value) { return JSON.stringify(value); }
