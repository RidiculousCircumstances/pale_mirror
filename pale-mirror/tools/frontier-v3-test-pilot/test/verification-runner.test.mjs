import assert from 'node:assert/strict';
import test from 'node:test';
import { execFile } from 'node:child_process';
import { mkdtemp, mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createVerificationExecution, executeVerificationExecution, focusedT1Descriptor } from '../src/verification-runner.mjs';
import { parse } from '../src/run-verification-plan.mjs';

const exec = promisify(execFile);
const HASH = (letter) => letter.repeat(64);
const build = Object.freeze({ preparedArtifact: { sha256: HASH('a') }, classpaths: { server: { sha256: HASH('b') }, client: { sha256: HASH('c') } } });
const runtime = Object.freeze({ jdkSha256: HASH('d'), platformSha256: HASH('e'), sdkSha256: HASH('f'), environmentPolicySha256: HASH('0'), host: 'test-host' });
const verify = async () => build;

test('executable runner uses only its closed tier catalog, caches successful non-final terminal records, and reuses them exactly', async (context) => {
  const project = await fixture(); context.after(() => rm(project, { recursive: true, force: true }));
  const first = await createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: {}, verifyPrepared: verify, runtime, runId: 'first' });
  assert.deepEqual(first.tiers.map((tier) => tier.tier), ['T0', 'T1', 'T3']);
  const calls = [];
  const result = await executeVerificationExecution(first, { execute: async (command) => { calls.push(command.id); return { status: 'ok', code: 0 }; } });
  assert.deepEqual(calls, ['node_schema', 'focused_node', 'persistent_native']);
  assert.deepEqual(result.results.map((entry) => entry.status), ['EXECUTED', 'EXECUTED', 'EXECUTED']);

  const second = await createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: {}, verifyPrepared: verify, runtime, runId: 'second' });
  assert.deepEqual(second.tiers.map((tier) => tier.cacheKey), first.tiers.map((tier) => tier.cacheKey));
  const reused = await executeVerificationExecution(second, { execute: async () => assert.fail('exact reusable evidence must not execute') });
  assert.deepEqual(reused.results.map((entry) => entry.status), ['REUSED', 'REUSED', 'REUSED']);
});

test('runner never reuses stale dirty-content evidence, failed evidence, corrupt proof, or an under-selected unknown path', async (context) => {
  const project = await fixture(); context.after(() => rm(project, { recursive: true, force: true }));
  const input = { project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'], preparedIdentity: {}, verifyPrepared: verify, runtime };
  const first = await createVerificationExecution({ ...input, runId: 'seed' });
  await executeVerificationExecution(first, { execute: async () => ({ status: 'ok', code: 0 }) });
  await writeFile(join(project, 'tools/frontier-v3-test-pilot/scenarios/example.json'), '{"changed":true}\n');
  const dirty = await createVerificationExecution({ ...input, runId: 'dirty' });
  let calls = 0;
  await executeVerificationExecution(dirty, { execute: async () => { calls++; return { status: 'ok', code: 0 }; } });
  assert.equal(calls, 3);

  const unknown = await createVerificationExecution({ ...input, changedPaths: ['README.md'], runId: 'unknown' });
  assert.deepEqual(unknown.selection.requiredTiers, ['T0', 'T1', 'T2', 'T3', 'T4']);
  assert.deepEqual(unknown.tiers.map((tier) => tier.tier), ['T0', 'T1', 'T2', 'T3']);
  assert.deepEqual(unknown.selection.deferredFinalTiers, ['T4']);

  const proof = join(project, 'build/frontier-v3-verification/dirty/T0.json');
  await writeFile(proof, '{"corrupt":true}\n');
  const corrupt = await createVerificationExecution({ ...input, runId: 'corrupt' });
  await assert.rejects(executeVerificationExecution(corrupt, { execute: async () => ({ status: 'ok', code: 0 }) }), /cache proof is stale/);

  await writeFile(join(project, 'docs/frontier-v3-note.md'), 'new documented input\n');
  const failed = await createVerificationExecution({ ...input, changedPaths: ['docs/frontier-v3-note.md'], runId: 'failed' });
  await assert.rejects(executeVerificationExecution(failed, { execute: async () => ({ status: 'failed', code: 1 }) }), /failed; no cache entry/);
  assert.equal(JSON.parse(await readFile(join(project, 'build/frontier-v3-verification/failed/failure.json'), 'utf8')).status, 'failed');
  const entries = await readdir(join(project, 'build/frontier-v3-evidence-cache/entries'));
  assert.equal(entries.length, 6, 'a failed run must not append cache entries');
});

test('fresh evidence mechanically bypasses every cached record and forbids fixture images', async (context) => {
  const project = await fixture(); context.after(() => rm(project, { recursive: true, force: true }));
  const ordinary = await createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: {}, verifyPrepared: verify, runtime, runId: 'ordinary' });
  await executeVerificationExecution(ordinary, { execute: async () => ({ status: 'ok', code: 0 }) });
  const fresh = await createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'], freshEvidence: true,
    preparedIdentity: {}, verifyPrepared: verify, runtime, runId: 'fresh' });
  assert.deepEqual(fresh.tiers.map((tier) => tier.tier), ['T0', 'T1', 'T2', 'T3', 'T4']);
  assert.ok(fresh.tiers.every((tier) => tier.cachePolicy === 'BYPASS_REQUIRED' && tier.fixtureImage === 'FORBIDDEN'));
  let calls = 0;
  const result = await executeVerificationExecution(fresh, { execute: async () => { calls++; return { status: 'ok', code: 0 }; } });
  assert.equal(calls, 6); assert.ok(result.results.every((entry) => entry.status === 'EXECUTED'));
});

test('a conservative timing cohort can add only lower iterative tiers, bypass cache, and passes one immutable prepared identity to every command', async (context) => {
  const project = await fixture(); context.after(() => rm(project, { recursive: true, force: true }));
  const execution = await createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: build, verifyPrepared: verify, runtime, runId: 'timing-baseline', extraIterativeTiers: ['T2'],
    reuseEvidence: false, nativeClientStrategy: 'INDEPENDENT' });
  assert.deepEqual(execution.tiers.map((tier) => tier.tier), ['T0', 'T1', 'T2', 'T3']);
  assert.ok(execution.tiers.every((tier) => tier.cachePolicy === 'BYPASS_REQUIRED'));
  assert.equal(execution.tiers.find((tier) => tier.tier === 'T3').descriptor.commands[0].id, 'independent_native');
  const identities = [];
  const t1Nonces = [];
  await executeVerificationExecution(execution, { execute: async (_command, contextValue) => {
    identities.push(contextValue.environment.FRONTIER_V3_PREPARED_BUILD_IDENTITY);
    if (contextValue.tier === 'T1') t1Nonces.push(contextValue.environment.FRONTIER_V3_VERIFICATION_EXECUTION_NONCE);
    return { status: 'ok', code: 0 };
  } });
  assert.equal(new Set(identities).size, 1);
  assert.deepEqual(t1Nonces, ['timing-baseline-T1-focused_node']);
  assert.deepEqual(JSON.parse(await readFile(join(project, identities[0]), 'utf8')), build);
  await assert.rejects(createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: build, verifyPrepared: verify, runtime, runId: 'bad-extra', extraIterativeTiers: ['T4'] }), /iterative execution policy/);
});

test('verification CLI accepts only an identity, safe declared paths, and an explicit fresh-evidence mode', () => {
  assert.deepEqual(parse(['--identity=build/prepared.json', '--changed=docs/frontier-v3-note.md', '--fresh-evidence']), {
    identity: 'build/prepared.json', changed: ['docs/frontier-v3-note.md'], freshEvidence: true
  });
  assert.throws(() => parse(['--identity=../escape.json']), /safe relative/);
  assert.throws(() => parse(['--identity=build/prepared.json', '--unknown']), /unknown/);
});

test('T1 commands are selected by the owning manifest and require an exact test filter', async (context) => {
  const project = await fixture(); context.after(() => rm(project, { recursive: true, force: true }));
  const execution = await createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: {}, verifyPrepared: verify, runtime, runId: 'focused-t1' });
  const commands = execution.tiers.find((tier) => tier.tier === 'T1').descriptor.commands;
  assert.deepEqual(commands.map((command) => command.id), ['focused_node']);
  assert.throws(() => focusedT1Descriptor(project, { rules: [] }, execution.selection), /absent from manifest/);
});

test('conservative T1 is an explicit full registered-owner baseline, never an accidental candidate shortcut', async (context) => {
  const project = await fixture(); context.after(() => rm(project, { recursive: true, force: true }));
  const selected = await createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: {}, verifyPrepared: verify, runtime, runId: 'selected' });
  const conservative = await createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: {}, verifyPrepared: verify, runtime, runId: 'conservative', t1Scope: 'CONSERVATIVE' });
  assert.equal(selected.t1Scope, 'SELECTED');
  assert.equal(conservative.t1Scope, 'CONSERVATIVE');
  assert.deepEqual(selected.tiers.find((tier) => tier.tier === 'T1').descriptor.commands.map((command) => command.id), ['focused_node']);
  assert.deepEqual(conservative.tiers.find((tier) => tier.tier === 'T1').descriptor.commands.map((command) => command.id), [
    'focused_node', 'focused_frontier', 'focused_neoforge', 'focused_domain', 'focused_api'
  ]);
  const selectedArgs = selected.tiers.find((tier) => tier.tier === 'T1').descriptor.commands[0].args;
  const conservativeArgs = conservative.tiers.find((tier) => tier.tier === 'T1').descriptor.commands[0].args;
  assert.ok(!selectedArgs.includes('tools/frontier-v3-test-pilot/test/ci-matrix.test.mjs'));
  assert.ok(conservativeArgs.includes('tools/frontier-v3-test-pilot/test/ci-matrix.test.mjs'));
  await assert.rejects(createVerificationExecution({ project, changedPaths: ['tools/frontier-v3-test-pilot/scenarios/example.json'],
    preparedIdentity: {}, verifyPrepared: verify, runtime, runId: 'bad-t1-scope', t1Scope: 'UNSAFE' }), /iterative execution policy/);
});

test('NeoForge T1 puts its exact test filter after the Test task, never after pilot preparation', async (context) => {
  const project = await fixture(); context.after(() => rm(project, { recursive: true, force: true }));
  const manifest = {
    rules: [{ id: 'neoforge-owner', t1: { nodeTests: [], gradleTests: [{ module: 'neoforge', className: 'example.ExactTest' }],
      allModules: { frontier: false, neoforge: false, domain: false, api: false } } }]
  };
  const commands = focusedT1Descriptor(project, manifest, { selections: [{ owner: 'neoforge-owner' }], conservative: false });
  const args = commands[0].args;
  assert.ok(args.indexOf(':pale-mirror-neoforge:compileJava') < args.indexOf(':pale-mirror-neoforge:pilotClasses'));
  assert.ok(args.indexOf(':pale-mirror-neoforge:pilotClasses') < args.indexOf(':pale-mirror-neoforge:test'));
  assert.ok(args.indexOf(':pale-mirror-neoforge:test') < args.indexOf('--tests'));
  assert.equal(args[args.indexOf('--tests') + 1], 'example.ExactTest');
});

async function fixture() {
  const project = await mkdtemp(join(tmpdir(), 'pmv3-verification-runner-'));
  await mkdir(join(project, 'tools/frontier-v3-test-pilot/contracts'), { recursive: true });
  await mkdir(join(project, 'tools/frontier-v3-test-pilot/scenarios'), { recursive: true });
  await mkdir(join(project, 'tools/frontier-v3-test-pilot/src'), { recursive: true });
  await mkdir(join(project, 'docs'), { recursive: true });
  await writeFile(join(project, '.gitignore'), 'build/\n');
  await writeFile(join(project, 'README.md'), 'fixture\n');
  await writeFile(join(project, 'tools/frontier-v3-test-pilot/package.json'), '{}\n');
  await writeFile(join(project, 'tools/frontier-v3-test-pilot/contracts/dependency-manifest.json'), JSON.stringify({
    schema: 1, kind: 'frontier-v3-verification-dependencies', tiers: ['T0', 'T1', 'T2', 'T3', 'T4'], rules: [
      { id: 'pilot-scenarios', prefixes: ['tools/frontier-v3-test-pilot/scenarios/', 'tools/frontier-v3-test-pilot/contracts/'], tiers: ['T0', 'T1', 'T3'], t1: t1(['tools/frontier-v3-test-pilot/test/scenario.test.mjs']) },
      { id: 'architecture', prefixes: ['docs/frontier-v3-'], tiers: ['T0', 'T1'], t1: t1(['tools/frontier-v3-test-pilot/test/verification-selector.test.mjs']) },
      { id: 'ci-workflow', prefixes: ['.github/workflows/'], tiers: ['T0', 'T1', 'T2', 'T3', 'T4'], t1: t1(['tools/frontier-v3-test-pilot/test/ci-matrix.test.mjs']) }
    ]
  }) + '\n');
  await writeFile(join(project, 'tools/frontier-v3-test-pilot/contracts/resource-site-harvest-f0v.json'), '{}\n');
  await writeFile(join(project, 'tools/frontier-v3-test-pilot/scenarios/example.json'), '{}\n');
  await exec('git', ['init', '-q'], { cwd: project });
  await exec('git', ['add', '.'], { cwd: project });
  await exec('git', ['-c', 'user.name=test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'fixture'], { cwd: project });
  return project;
}

function t1(nodeTests) {
  return { nodeTests, gradleTests: [], allModules: { frontier: false, neoforge: false, domain: false, api: false } };
}
