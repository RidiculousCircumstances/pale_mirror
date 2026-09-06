import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createVerificationExecution, discoverChangedPaths, executeVerificationExecution } from './verification-runner.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

export async function main(argumentsValue = process.argv.slice(2)) {
  const options = parse(argumentsValue);
  const preparedIdentity = JSON.parse(await readFile(resolve(project, options.identity), 'utf8'));
  const changedPaths = options.changed.length === 0 ? await discoverChangedPaths(project) : options.changed;
  const execution = await createVerificationExecution({ project, changedPaths, freshEvidence: options.freshEvidence, preparedIdentity });
  console.log(JSON.stringify({ status: 'planned', runId: execution.runId, tiers: execution.tiers.map((tier) => ({ tier: tier.tier,
    cachePolicy: tier.cachePolicy, fixtureImage: tier.fixtureImage, cacheKey: tier.cacheKey })), conservative: execution.selection.conservative }));
  const result = await executeVerificationExecution(execution);
  console.log(JSON.stringify({ status: 'ok', runId: result.runId, directory: execution.directory, results: result.results }));
  return result;
}

export function parse(argumentsValue) {
  const options = { identity: undefined, changed: [], freshEvidence: false };
  for (const option of argumentsValue) {
    if (option.startsWith('--identity=')) {
      if (options.identity !== undefined) throw new Error('verification plan accepts one --identity');
      options.identity = safeRelative(option.slice('--identity='.length), 'prepared identity');
    } else if (option.startsWith('--changed=')) {
      options.changed.push(safeRelative(option.slice('--changed='.length), 'changed path'));
    } else if (option === '--fresh-evidence') options.freshEvidence = true;
    else throw new Error(`unknown verification plan option: ${option}`);
  }
  if (options.identity === undefined) {
    throw new Error('usage: node run-verification-plan.mjs --identity=<prepared-build.json> [--changed=<path>]... [--fresh-evidence]');
  }
  return Object.freeze({ identity: options.identity, changed: Object.freeze([...new Set(options.changed)].sort()), freshEvidence: options.freshEvidence });
}

function safeRelative(value, label) {
  if (typeof value !== 'string' || value.length === 0 || value.startsWith('/') || value.includes('\\') || value.split('/').includes('..')) {
    throw new Error(`verification ${label} must be a safe relative path`);
  }
  return value;
}
