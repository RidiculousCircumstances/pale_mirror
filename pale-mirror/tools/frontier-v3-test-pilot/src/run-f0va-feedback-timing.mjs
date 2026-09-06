import { randomUUID } from 'node:crypto';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { compareFeedbackWorkflowMedians, FEEDBACK_WORKFLOWS } from './feedback-timing.mjs';
import { FEEDBACK_WORKFLOW_CATALOG, feedbackSampleFromExecution, feedbackWorkflowDefinition } from './feedback-workflows.mjs';
import { writeFailureBundle } from './failure-bundle.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';
import { createVerificationExecution, executeVerificationExecution } from './verification-runner.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

/**
 * Measures the three declared feedback classes.  The baseline deliberately runs every ordinary
 * iterative tier with independent native clients; the candidate uses the real selector and forces
 * one current native terminal (while legal T0/T1 cache reuse remains attributable).  It is a
 * benchmark, never final F0.V evidence, so neither cohort may use T4 or a fixture image.
 */
export async function main(argumentsValue = process.argv.slice(2)) {
  const options = parse(argumentsValue);
  if (!process.env.DISPLAY) throw new Error('F0.VA feedback timing requires the one visible client on DISPLAY=:0');
  const identityPath = safeBuildPath(options.prepared, 'prepared build identity');
  const identity = Object.freeze(JSON.parse(await readFile(identityPath, 'utf8')));
  await requirePreparedF0vBuild(project, identity); await absent(options.output, 'feedback timing report');
  const runId = randomUUID(); const report = { schema: 1, kind: 'frontier-v3-f0va-feedback-timing-run', status: 'RUNNING', runId,
    preparedIdentity: relative(project, identityPath), baseline: [], candidate: [], completed: [] };
  try {
    for (const workflow of FEEDBACK_WORKFLOWS) {
      for (let sample = 1; sample <= 3; sample++) {
        const baseline = await measure('baseline', workflow, sample); report.baseline.push(baseline); report.completed.push(`${workflow}:baseline:${sample}`);
        const candidate = await measure('candidate', workflow, sample); report.candidate.push(candidate); report.completed.push(`${workflow}:candidate:${sample}`);
      }
    }
    report.comparison = compareFeedbackWorkflowMedians({ baseline: report.baseline, candidate: report.candidate });
    report.status = 'ok'; await writeExclusive(options.output, `${JSON.stringify(report, null, 2)}\n`);
    console.log(JSON.stringify({ status: 'ok', report: options.output, comparison: report.comparison }));
    return Object.freeze(report);
  } catch (error) {
    report.status = 'failed'; report.error = String(error?.stack ?? error);
    await writeExclusive(options.output, `${JSON.stringify(report, null, 2)}\n`);
    const bundle = await writeFailureBundle({ project, output: options.output, scenarioPath: identityPath, runId, failure: error,
      timing: { runner: 'f0va-feedback-timing', status: 'failed' }, build: identity,
      process: { completed: report.completed, baselineSamples: report.baseline.length, candidateSamples: report.candidate.length } });
    console.error(`PMV3_F0VA_FEEDBACK failure_bundle=${bundle}`);
    throw error;
  }

  async function measure(cohort, workflow, sample) {
    const started = process.hrtime.bigint();
    const execution = await createVerificationExecution({ project, preparedIdentity: identity,
      changedPaths: FEEDBACK_WORKFLOW_CATALOG_PATHS(workflow), runId: `${runId}-${workflow}-${cohort}-${sample}`,
      extraIterativeTiers: cohort === 'baseline' ? ['T0', 'T1', 'T2', 'T3'] : [],
      forceExecutionTiers: cohort === 'candidate' ? ['T3'] : [], reuseEvidence: cohort === 'candidate',
      nativeClientStrategy: cohort === 'baseline' ? 'INDEPENDENT' : 'PERSISTENT',
      // Baseline is deliberately the former conservative developer path: all declared T1
      // owners run. Candidate is the selector-owned subset being measured against it.
      t1Scope: cohort === 'baseline' ? 'CONSERVATIVE' : 'SELECTED' });
    const definition = feedbackWorkflowDefinition(workflow, execution.selection);
    if (cohort === 'baseline' && JSON.stringify(execution.tiers.map((tier) => tier.tier)) !== JSON.stringify(definition.baselineTiers)) {
      throw new Error(`feedback baseline ${workflow} did not retain every iterative tier`);
    }
    if (cohort === 'candidate' && JSON.stringify(execution.tiers.map((tier) => tier.tier)) !== JSON.stringify(definition.candidateTiers)) {
      throw new Error(`feedback candidate ${workflow} did not retain selector-owned tiers`);
    }
    const result = await executeVerificationExecution(execution);
    await requirePreparedF0vBuild(project, identity);
    return await feedbackSampleFromExecution({ cohort, workflow, elapsedMillis: Number(process.hrtime.bigint() - started) / 1_000_000,
      execution, result, readTerminal });
  }
}

function FEEDBACK_WORKFLOW_CATALOG_PATHS(workflow) {
  const definition = FEEDBACK_WORKFLOW_CATALOG[workflow];
  if (definition === undefined) throw new Error(`unknown feedback workflow ${workflow}`);
  return definition.changedPaths;
}

async function readTerminal(proof) {
  const path = safeProjectPath(proof, 'feedback tier proof');
  let record;
  try { record = JSON.parse(await readFile(path, 'utf8')); }
  catch (error) { throw new Error(`feedback tier proof is unreadable: ${proof}`, { cause: error }); }
  if (!record || record.status !== 'ok' || !record.terminal || typeof record.terminal.invariant !== 'string') {
    throw new Error(`feedback tier proof lacks terminal invariant: ${proof}`);
  }
  return record.terminal;
}

export function parse(argumentsValue) {
  const options = { prepared: undefined, output: undefined };
  for (const argument of argumentsValue) {
    const pair = [['--prepared=', 'prepared'], ['--output=', 'output']].find(([prefix]) => argument.startsWith(prefix));
    if (pair === undefined || options[pair[1]] !== undefined) throw new Error(`unknown or duplicate feedback timing option: ${argument}`);
    options[pair[1]] = argument.slice(pair[0].length);
  }
  if (options.prepared === undefined || options.output === undefined) {
    throw new Error('usage: node run-f0va-feedback-timing.mjs --prepared=<build/prepared.json> --output=<build/report.json>');
  }
  return Object.freeze({ prepared: safeRelative(options.prepared, 'prepared build identity'), output: safeRelative(options.output, 'feedback timing report') });
}

function safeProjectPath(candidate, label) { if (typeof candidate !== 'string') throw new Error(`${label} is malformed`); const target = resolve(project, candidate); const path = relative(project, target); if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} escapes project`); return target; }
function safeBuildPath(candidate, label) { const target = safeProjectPath(candidate, label); const path = relative(resolve(project, 'build'), target); if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} must remain under build/`); return target; }
function safeRelative(candidate, label) { if (typeof candidate !== 'string' || candidate.length === 0 || candidate.startsWith('/') || candidate.includes('\\') || candidate.split('/').includes('..')) throw new Error(`${label} must be a safe relative path`); return candidate; }
async function absent(path, label) { try { await stat(path); throw new Error(`refusing to overwrite ${label}: ${path}`); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
async function writeExclusive(path, contents) { await mkdir(dirname(path), { recursive: true }); await writeFile(path, contents, { encoding: 'utf8', flag: 'wx' }); }
