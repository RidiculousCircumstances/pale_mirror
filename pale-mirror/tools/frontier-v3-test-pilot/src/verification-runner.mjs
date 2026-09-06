import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { readFile, mkdir, stat, writeFile } from 'node:fs/promises';
import { hostname, platform, arch, release } from 'node:os';
import { basename, dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { evidenceCacheKey, fingerprintWorkingContentWithMonorepoWorkflows, MONOREPO_WORKFLOW_PREFIXES, monorepoRoot, readReusableEvidence, storeSuccessfulEvidence } from './evidence-cache.mjs';
import { loadDependencyManifest, selectVerificationPlan, VERIFICATION_PLAN_SCHEMA } from './verification-selector.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';

export const VERIFICATION_EXECUTION_SCHEMA = 1;
const KIND = 'frontier-v3-verification-execution';
const ALL_TIERS = Object.freeze(['T0', 'T1', 'T2', 'T3', 'T4']);
const RELATIVE = Object.freeze({
  manifest: 'tools/frontier-v3-test-pilot/contracts/dependency-manifest.json',
  contract: 'tools/frontier-v3-test-pilot/contracts/resource-site-harvest-f0v.json',
  cache: 'build/frontier-v3-evidence-cache',
  output: 'build/frontier-v3-verification'
});

/**
 * Creates a complete, closed execution plan.  It never accepts a command from a manifest, CLI
 * flag or cache record: source ownership selects only these checked-in tier descriptors.
 */
export async function createVerificationExecution({ project, changedPaths, freshEvidence = false,
  preparedIdentity, verifyPrepared = defaultVerifyPrepared, runtime = undefined, runId = randomUUID(),
  extraIterativeTiers = [], forceExecutionTiers = [], reuseEvidence = !freshEvidence, nativeClientStrategy = 'PERSISTENT',
  t1Scope = 'SELECTED' }) {
  const root = resolve(project); requireRunId(runId);
  const manifestPath = resolve(root, RELATIVE.manifest);
  const contractPath = resolve(root, RELATIVE.contract);
  const [manifest, contractBytes, build] = await Promise.all([
    loadDependencyManifest(manifestPath), readFile(contractPath), verifyPrepared(root, preparedIdentity)
  ]);
  if (!Array.isArray(extraIterativeTiers) || extraIterativeTiers.some((tier) => !ALL_TIERS.includes(tier) || tier === 'T4')
      || !Array.isArray(forceExecutionTiers) || forceExecutionTiers.some((tier) => !ALL_TIERS.includes(tier))
      || typeof reuseEvidence !== 'boolean' || !['PERSISTENT', 'INDEPENDENT'].includes(nativeClientStrategy)
      || !['SELECTED', 'CONSERVATIVE'].includes(t1Scope)) {
    throw new Error('verification iterative execution policy is malformed');
  }
  const selection = selectVerificationPlan(manifest, { changedPaths, freshEvidence });
  const checkedBuild = validateBuildIdentity(build);
  const source = await fingerprintWorkingContentWithMonorepoWorkflows(root, sourcePrefixes(manifest, selection.changedPaths));
  const executionRuntime = runtime === undefined ? await runtimeIdentity() : validateRuntimeIdentity(runtime);
  const contractSha256 = hash(contractBytes);
  const directory = resolve(root, RELATIVE.output, runId);
  const descriptors = verificationTierCatalog(root, directory, contractPath, nativeClientStrategy, manifest, selection, t1Scope);
  const tiers = [];
  const selectedTiers = ALL_TIERS.filter((tier) => selection.tiers.includes(tier) || extraIterativeTiers.includes(tier));
  if (forceExecutionTiers.some((tier) => !selectedTiers.includes(tier))) throw new Error('verification forced execution tier is not selected');
  for (const tier of selectedTiers) {
    const descriptor = descriptors.get(tier);
    if (descriptor === undefined) throw new Error(`verification catalog does not own selected tier ${tier}`);
    const finalEvidence = tier === 'T4';
    const cachePolicy = freshEvidence || finalEvidence || !reuseEvidence || forceExecutionTiers.includes(tier) ? 'BYPASS_REQUIRED' : 'CANDIDATE_ONLY';
    const fixtureImage = freshEvidence || finalEvidence ? 'FORBIDDEN' : 'DEVELOPMENT_ONLY';
    const input = Object.freeze({ schema: 1, kind: 'frontier-v3-evidence-cache-key', sourceContentSha256: source.sha256,
      artifactSha256: checkedBuild.artifactSha256, serverClasspathSha256: checkedBuild.serverClasspathSha256,
      clientClasspathSha256: checkedBuild.clientClasspathSha256, jdkSha256: executionRuntime.jdkSha256,
      platformSha256: executionRuntime.platformSha256, sdkSha256: executionRuntime.sdkSha256,
      dependencyManifestSha256: selection.manifestSha256, verticalContractSha256: contractSha256,
      scenarioSha256: descriptor.scenarioSha256 === 'CONTRACT' ? contractSha256 : descriptor.scenarioSha256,
      commandSha256: descriptor.commandSha256, environmentPolicySha256: executionRuntime.environmentPolicySha256,
      seed: 41, profile: 'world', viewDistance: 10, tier, fixtureImage: null });
    tiers.push(Object.freeze({ tier, descriptor, cachePolicy, fixtureImage, cacheKey: evidenceCacheKey(input), cacheInput: input }));
  }
  return Object.freeze({ schema: VERIFICATION_EXECUTION_SCHEMA, kind: KIND, runId, project: root,
    directory, selection, source, build: checkedBuild, runtime: executionRuntime,
    preparedIdentity: Object.freeze(structuredClone(build)), freshEvidence, reuseEvidence, nativeClientStrategy,
    t1Scope,
    extraIterativeTiers: Object.freeze([...new Set(extraIterativeTiers)].sort(tierOrder)),
    forceExecutionTiers: Object.freeze([...new Set(forceExecutionTiers)].sort(tierOrder)), tiers: Object.freeze(tiers) });
}

/**
 * Runs only a validated plan and writes an immutable terminal record for every selected tier.
 * Successful non-final tiers may be reused only after the exact record still exists and verifies.
 */
export async function executeVerificationExecution(execution, { execute = defaultExecute } = {}) {
  const checked = validateExecution(execution);
  if (typeof execute !== 'function') throw new Error('verification executor is malformed');
  await mkdir(dirname(checked.directory), { recursive: true });
  await mkdir(checked.directory, { recursive: false });
  const preparedIdentityPath = resolve(checked.directory, 'prepared-build.json');
  await writeExclusive(preparedIdentityPath, `${JSON.stringify(checked.preparedIdentity, null, 2)}\n`);
  await writeExclusive(resolve(checked.directory, 'plan.json'), `${JSON.stringify(planRecord(checked), null, 2)}\n`);
  const cacheRoot = resolve(checked.project, RELATIVE.cache);
  const results = [];
  try {
    for (const tier of checked.tiers) {
      const cached = tier.cachePolicy === 'CANDIDATE_ONLY'
        ? await reusableTerminalCache(cacheRoot, checked.project, tier)
        : undefined;
      if (cached !== undefined) {
        results.push(Object.freeze({ tier: tier.tier, status: 'REUSED', cacheKey: tier.cacheKey,
          proof: cached.entry.proofs[0], commands: tier.descriptor.commands.length }));
        continue;
      }
      const commands = [];
      for (const command of tier.descriptor.commands) {
        const outcome = await execute(command, { project: checked.project, tier: tier.tier, runId: checked.runId,
          environment: Object.freeze({ FRONTIER_V3_PREPARED_BUILD_IDENTITY: relative(checked.project, preparedIdentityPath),
            // It is a Test-task input only.  Each terminal T1 command therefore executes its
            // selected assertions; an incidental Gradle UP-TO-DATE result cannot become proof.
            ...(tier.tier === 'T1' ? { FRONTIER_V3_VERIFICATION_EXECUTION_NONCE: `${checked.runId}-${tier.tier}-${command.id}` } : {}) }) });
        if (!outcome || outcome.status !== 'ok' || outcome.code !== 0) {
          throw new Error(`verification ${tier.tier}/${command.id} failed; no cache entry was written`);
        }
        commands.push(Object.freeze({ id: command.id, code: outcome.code, output: outcome.output ?? null }));
      }
      const recordPath = resolve(checked.directory, `${tier.tier}.json`);
      const record = Object.freeze({ schema: VERIFICATION_EXECUTION_SCHEMA, kind: 'frontier-v3-verification-terminal',
        status: 'ok', runId: checked.runId, tier: tier.tier, cacheKey: tier.cacheKey,
        freshEvidence: checked.freshEvidence, fixtureImage: tier.fixtureImage, selection: checked.selection,
        terminal: Object.freeze({ commandCount: commands.length, invariant: terminalInvariant(tier.descriptor) }), commands });
      await writeExclusive(recordPath, `${JSON.stringify(record, null, 2)}\n`);
      const proof = relative(checked.project, recordPath);
      if (tier.cachePolicy === 'CANDIDATE_ONLY') await storeSuccessfulEvidence(cacheRoot, tier.cacheInput, [proof]);
      results.push(Object.freeze({ tier: tier.tier, status: 'EXECUTED', cacheKey: tier.cacheKey, proof, commands: commands.length }));
    }
  } catch (failure) {
    await writeExclusive(resolve(checked.directory, 'failure.json'), `${JSON.stringify({ schema: VERIFICATION_EXECUTION_SCHEMA,
      kind: KIND, status: 'failed', runId: checked.runId, selection: checked.selection,
      completed: results, failure: String(failure?.stack ?? failure) }, null, 2)}\n`);
    throw failure;
  }
  const result = Object.freeze({ schema: VERIFICATION_EXECUTION_SCHEMA, kind: KIND, status: 'ok', runId: checked.runId,
    selection: checked.selection, source: checked.source, build: checked.build, runtime: checked.runtime,
    freshEvidence: checked.freshEvidence, results: Object.freeze(results) });
  await writeExclusive(resolve(checked.directory, 'result.json'), `${JSON.stringify(result, null, 2)}\n`);
  return result;
}

/** Reads dirty working paths; a clean tree is not silently treated as a narrow no-op selection. */
export async function discoverChangedPaths(project) {
  const { execFile } = await import('node:child_process'); const { promisify } = await import('node:util');
  const exec = promisify(execFile); const root = resolve(project);
  const monorepo = monorepoRoot(root);
  const [tracked, untracked] = await Promise.all([
    exec('git', ['diff', '--name-status', '-z', '--no-ext-diff', '--find-renames', 'HEAD'], { cwd: monorepo }),
    exec('git', ['ls-files', '--others', '--exclude-standard', '-z'], { cwd: monorepo })
  ]);
  const paths = [...new Set([
    ...nameStatusPaths(tracked.stdout),
    ...untracked.stdout.split('\0').filter(Boolean)
  ].flatMap(normalizeChangedCoordinate))].sort();
  if (paths.length === 0) throw new Error('verification selection needs explicit changed paths for a clean tree');
  return Object.freeze(paths);
}

export function verificationTierCatalog(project, outputDirectory, contractPath, nativeClientStrategy = 'PERSISTENT', manifest = undefined, selection = undefined,
  t1Scope = 'SELECTED') {
  const root = resolve(project); const output = resolve(outputDirectory); const contract = resolve(contractPath);
  if (!['PERSISTENT', 'INDEPENDENT'].includes(nativeClientStrategy)) throw new Error('verification native client strategy is malformed');
  const t1 = focusedT1Descriptor(root, manifest, selection, t1Scope);
  const script = (name) => resolve(root, 'tools/frontier-v3-test-pilot/src', name);
  const catalog = new Map([
    ['T0', descriptor('T0', 'node_schema', [{ id: 'node_schema', command: process.execPath, args: ['--test',
      'tools/frontier-v3-test-pilot/test/evidence-cache.test.mjs', 'tools/frontier-v3-test-pilot/test/verification-selector.test.mjs',
      'tools/frontier-v3-test-pilot/test/lifecycle-barrier.test.mjs', 'tools/frontier-v3-test-pilot/test/persistent-matrix.test.mjs',
      'tools/frontier-v3-test-pilot/test/development-fixture-image.test.mjs', 'tools/frontier-v3-test-pilot/test/development-fixture-runtime.test.mjs',
      'tools/frontier-v3-test-pilot/test/feedback-timing.test.mjs'] }], 'schema_and_protocol')],
    ['T1', descriptor('T1', 'focused_owner_contract', t1, 'focused_owner_and_codec')],
    ['T2', descriptor('T2', 'scene_gametest', [{ id: 'scene_gametest', command: resolve(root, 'gradlew'), args: [
      ':pale-mirror-neoforge:runFrontierV3SceneGameTestServer', '--offline', '--no-daemon'] }], 'named_scene_gametest')],
    ['T3', nativeDescriptor(nativeClientStrategy, script, output)],
    ['T4', descriptor('T4', 'fresh_f0v_and_critical', [
      { id: 'fresh_f0v_matrix', command: process.execPath, args: [script('run-f0v-matrix.mjs'), contract] },
      { id: 'critical_gate', command: resolve(root, 'gradlew'), args: ['guardrails', 'check', ':pale-mirror-neoforge:runGameTestServer',
        ':pale-mirror-neoforge:build', ':pale-mirror-neoforge:verifyPackagedJar', '--offline', '--no-daemon'] }
    ], 'fresh_f0v_semantic_matrix_and_packaged_gate')]
  ]);
  if (catalog.size !== ALL_TIERS.length || ALL_TIERS.some((tier) => !catalog.has(tier))) throw new Error('verification catalog lacks a required tier');
  return catalog;
}

/** Builds only manifest-owned lower-tier commands; unknown ownership arrives here already widened. */
export function focusedT1Descriptor(project, manifest, selection, t1Scope = 'SELECTED') {
  if (!manifest || !selection || !Array.isArray(selection.selections)) {
    throw new Error('verification T1 descriptor requires a selected dependency manifest');
  }
  if (!['SELECTED', 'CONSERVATIVE'].includes(t1Scope)) throw new Error('verification T1 scope is malformed');
  const selected = new Map(manifest.rules.map((rule) => [rule.id, rule]));
  const owners = [...new Set(selection.selections.map((entry) => entry.owner).filter(Boolean))].sort();
  const nodeTests = new Set(); const allModules = new Set(); const gradleTests = new Map();
  for (const owner of owners) {
    const rule = selected.get(owner);
    if (rule === undefined) throw new Error(`verification T1 owner is absent from manifest: ${owner}`);
    rule.t1.nodeTests.forEach((path) => nodeTests.add(path));
    for (const [module, all] of Object.entries(rule.t1.allModules)) if (all) allModules.add(module);
    rule.t1.gradleTests.forEach((entry) => {
      const bucket = gradleTests.get(entry.module) ?? new Set(); bucket.add(entry.className); gradleTests.set(entry.module, bucket);
    });
  }
  // Conservative unknown/multiple paths must actually execute every production owner, not a
  // convenient subset derived from a partial match.
  if (selection.conservative || t1Scope === 'CONSERVATIVE') {
    ['frontier', 'neoforge', 'domain', 'api'].forEach((module) => allModules.add(module));
    manifest.rules.flatMap((rule) => rule.t1.nodeTests).forEach((path) => nodeTests.add(path));
  }
  const commands = [];
  if (nodeTests.size > 0) commands.push({ id: 'focused_node', command: process.execPath,
    args: ['--test', ...[...nodeTests].sort()] });
  const task = Object.freeze({ frontier: ':pale-mirror-frontier:test', neoforge: ':pale-mirror-neoforge:test',
    domain: ':pale-mirror-domain:test', api: ':pale-mirror-api:test' });
  for (const module of Object.keys(task)) {
    const tests = [...(gradleTests.get(module) ?? [])].sort();
    if (!allModules.has(module) && tests.length === 0) continue;
    // Gradle associates --tests with the immediately preceding task.  Put preparation before
    // the Test task; appending it after pilotClasses makes Gradle reject the selector instead
    // of executing a focused test (and must never be reinterpreted as an unfiltered suite).
    const args = module === 'neoforge'
      ? [':pale-mirror-neoforge:compileJava', ':pale-mirror-neoforge:pilotClasses', task[module]]
      : [task[module]];
    tests.forEach((className) => args.push('--tests', className));
    args.push('--offline', '--no-daemon');
    commands.push({ id: `focused_${module}`, command: resolve(project, 'gradlew'), args });
  }
  if (commands.length === 0) throw new Error('verification T1 descriptor has no selected command');
  return Object.freeze(commands.map((command) => Object.freeze(command)));
}

function descriptor(tier, id, commands, terminal, commandIdentity = undefined) {
  const checked = commands.map((command) => {
    if (!command || !token(command.id) || typeof command.command !== 'string' || !command.command.startsWith('/')
        || !Array.isArray(command.args) || command.args.some((arg) => typeof arg !== 'string' || arg.length > 4096)) {
      throw new Error('verification catalog command is malformed');
    }
    return Object.freeze({ id: command.id, command: command.command, args: Object.freeze([...command.args]) });
  });
  const value = Object.freeze({ tier, id, commands: Object.freeze(checked), terminal });
  const identity = commandIdentity === undefined ? value : Object.freeze({ tier, id, terminal, commandIdentity });
  return Object.freeze({ ...value, commandSha256: hash(JSON.stringify(identity)), scenarioSha256: tier === 'T3' ? hash('f0va-persistent-matrix-v1') : 'CONTRACT' });
}
function nativeDescriptor(strategy, script, output) {
  const persistent = strategy === 'PERSISTENT';
  const file = persistent ? 'run-f0va-persistent-matrix.mjs' : 'run-f0va-independent-matrix.mjs';
  const id = persistent ? 'persistent_native' : 'independent_native';
  const terminal = persistent ? 'persistent_lifecycle_terminal' : 'independent_lifecycle_terminal';
  return descriptor('T3', id, [{ id, command: process.execPath, args: [script(file), resolve(output, 'T3-native.json')] }], terminal,
    { tier: 'T3', id, command: process.execPath, args: [script(file), '<immutable-evidence-output>'], strategy });
}
function sourcePrefixes(manifest, changedPaths) {
  return [...new Set([...manifest.rules.flatMap((rule) => rule.prefixes), ...changedPaths,
    'tools/frontier-v3-test-pilot/package.json', RELATIVE.manifest, RELATIVE.contract])].sort();
}
function nameStatusPaths(value) {
  const values = value.split('\0').filter(Boolean); const paths = [];
  for (let index = 0; index < values.length;) {
    const status = values[index++];
    if (status === undefined || !/^(?:[ADMTUXB]|[RC][0-9]{1,3})$/.test(status)) throw new Error('verification Git status output is malformed');
    const count = status.startsWith('R') || status.startsWith('C') ? 2 : 1;
    for (let path = 0; path < count; path++) {
      const candidate = values[index++];
      if (candidate === undefined) throw new Error('verification Git status path is malformed');
      paths.push(candidate);
    }
  }
  return paths;
}
function normalizeChangedCoordinate(path) {
  if (path.startsWith('pale-mirror/')) {
    const projectPath = path.slice('pale-mirror/'.length);
    if (!safeChangedPath(projectPath)) throw new Error('verification project change path is malformed');
    return [projectPath];
  }
  if (MONOREPO_WORKFLOW_PREFIXES.some((prefix) => path.startsWith(prefix))) {
    if (!safeChangedPath(path)) throw new Error('verification root workflow path is malformed');
    return [path];
  }
  return [];
}
/**
 * Verification is an admission check, not an identity projection. In particular, native child
 * runners must retain the exact source-content fingerprint supplied by preparation instead of
 * receiving the smaller artifact/classpath result returned by the verifier.
 */
async function defaultVerifyPrepared(project, preparedIdentity) {
  await requirePreparedF0vBuild(project, preparedIdentity);
  return preparedIdentity;
}
async function runtimeIdentity() {
  const { execFile } = await import('node:child_process'); const { promisify } = await import('node:util');
  const exec = promisify(execFile);
  let java;
  try { java = await exec('java', ['-version']); }
  catch (error) { throw new Error(`verification runtime cannot identify Java: ${String(error?.message ?? error)}`); }
  const sdk = JSON.stringify({ schema: VERIFICATION_EXECUTION_SCHEMA, catalog: ALL_TIERS, node: process.version });
  return validateRuntimeIdentity({ jdkSha256: hash(`${java.stdout}\n${java.stderr}`),
    platformSha256: hash(JSON.stringify({ platform: platform(), arch: arch(), release: release(), host: hostname() })),
    sdkSha256: hash(sdk), environmentPolicySha256: hash(JSON.stringify({ display: process.env.DISPLAY ?? null,
      offline: true, fixtureImages: 'development-only', freshEvidence: 'bypass' })), host: hostname() });
}
function validateRuntimeIdentity(value) {
  if (!value || !sha(value.jdkSha256) || !sha(value.platformSha256) || !sha(value.sdkSha256) || !sha(value.environmentPolicySha256)) {
    throw new Error('verification runtime identity is malformed');
  }
  if (typeof value.host !== 'string' || value.host.length === 0 || value.host.length > 255) throw new Error('verification runtime host is malformed');
  return Object.freeze({ jdkSha256: value.jdkSha256, platformSha256: value.platformSha256, sdkSha256: value.sdkSha256,
    environmentPolicySha256: value.environmentPolicySha256, host: value.host });
}
function validateBuildIdentity(value) {
  const artifactSha256 = value?.preparedArtifact?.sha256; const serverClasspathSha256 = value?.classpaths?.server?.sha256;
  const clientClasspathSha256 = value?.classpaths?.client?.sha256;
  if (!sha(artifactSha256) || !sha(serverClasspathSha256) || !sha(clientClasspathSha256)) throw new Error('verification prepared build identity is malformed');
  return Object.freeze({ artifactSha256, serverClasspathSha256, clientClasspathSha256 });
}
async function reusableTerminalCache(cacheRoot, project, tier) {
  let cached;
  try { cached = await readReusableEvidence(cacheRoot, tier.cacheInput); }
  catch (error) { throw new Error(`verification cache for ${tier.tier} is not reusable: ${String(error?.message ?? error)}`); }
  if (cached === undefined || cached.entry.proofs.length !== 1) return undefined;
  const proof = resolve(project, cached.entry.proofs[0]);
  if (!inside(project, proof)) throw new Error('verification cache proof escapes project');
  let record;
  try { record = JSON.parse(await readFile(proof, 'utf8')); await stat(proof); }
  catch { throw new Error(`verification cache proof is missing or corrupt: ${cached.entry.proofs[0]}`); }
  if (!record || record.schema !== VERIFICATION_EXECUTION_SCHEMA || record.kind !== 'frontier-v3-verification-terminal'
      || record.status !== 'ok' || record.tier !== tier.tier || record.cacheKey !== tier.cacheKey
      || !record.terminal || record.terminal.invariant !== terminalInvariant(tier.descriptor)) {
    throw new Error('verification cache proof is stale, failed or under-selected');
  }
  return cached;
}
function validateExecution(value) {
  if (!value || value.schema !== VERIFICATION_EXECUTION_SCHEMA || value.kind !== KIND || typeof value.project !== 'string'
      || typeof value.directory !== 'string' || !Array.isArray(value.tiers) || !Array.isArray(value.selection?.tiers)
      || typeof value.freshEvidence !== 'boolean' || typeof value.reuseEvidence !== 'boolean' || !value.source?.sha256 || !value.build || !value.runtime
      || !value.preparedIdentity || !['PERSISTENT', 'INDEPENDENT'].includes(value.nativeClientStrategy) || !['SELECTED', 'CONSERVATIVE'].includes(value.t1Scope) || !Array.isArray(value.extraIterativeTiers)
      || !Array.isArray(value.forceExecutionTiers)) {
    throw new Error('verification execution is malformed');
  }
  const tiers = value.tiers.map((tier) => {
    if (!tier || !ALL_TIERS.includes(tier.tier) || !tier.descriptor || !sha(tier.cacheKey) || !tier.cacheInput
        || !['CANDIDATE_ONLY', 'BYPASS_REQUIRED'].includes(tier.cachePolicy)
        || !['DEVELOPMENT_ONLY', 'FORBIDDEN'].includes(tier.fixtureImage)
        || evidenceCacheKey(tier.cacheInput) !== tier.cacheKey) throw new Error('verification execution tier is malformed');
    if (value.freshEvidence && (tier.cachePolicy !== 'BYPASS_REQUIRED' || tier.fixtureImage !== 'FORBIDDEN')) throw new Error('fresh verification may not use a fixture image');
    return tier;
  });
  if (value.extraIterativeTiers.some((tier) => !ALL_TIERS.includes(tier) || tier === 'T4')) throw new Error('verification extra iterative tier is malformed');
  const expectedTiers = ALL_TIERS.filter((tier) => value.selection.tiers.includes(tier) || value.extraIterativeTiers.includes(tier));
  if (value.forceExecutionTiers.some((tier) => !ALL_TIERS.includes(tier) || !expectedTiers.includes(tier))) throw new Error('verification forced execution tier is malformed');
  if (tiers.length === 0 || tiers.length !== expectedTiers.length || tiers.some((tier, index) => tier.tier !== expectedTiers[index])) {
    throw new Error('verification execution tier coverage is malformed');
  }
  return Object.freeze({ ...value, tiers: Object.freeze(tiers) });
}
async function defaultExecute(command, { project, environment = {} }) {
  return await new Promise((resolveOutcome, rejectOutcome) => {
    const child = spawn(command.command, command.args, { cwd: project, env: { ...process.env, ...environment }, stdio: 'inherit', shell: false });
    child.once('error', rejectOutcome);
    child.once('exit', (code, signal) => resolveOutcome(Object.freeze({ status: code === 0 ? 'ok' : 'failed', code: code ?? (signal == null ? 1 : 128) })));
  });
}
function terminalInvariant(descriptor) { return descriptor.terminal; }
function planRecord(execution) {
  return Object.freeze({ schema: VERIFICATION_EXECUTION_SCHEMA, kind: KIND, status: 'planned', runId: execution.runId,
    selection: execution.selection, source: execution.source, build: execution.build, runtime: execution.runtime,
    freshEvidence: execution.freshEvidence, reuseEvidence: execution.reuseEvidence, nativeClientStrategy: execution.nativeClientStrategy,
    extraIterativeTiers: execution.extraIterativeTiers, forceExecutionTiers: execution.forceExecutionTiers, t1Scope: execution.t1Scope, requiredTiers: execution.selection.requiredTiers,
    deferredFinalTiers: execution.selection.deferredFinalTiers, tiers: execution.tiers.map((tier) => ({ tier: tier.tier, cachePolicy: tier.cachePolicy,
      fixtureImage: tier.fixtureImage, cacheKey: tier.cacheKey, commandIds: tier.descriptor.commands.map((command) => command.id) })) });
}
function tierOrder(left, right) { return ALL_TIERS.indexOf(left) - ALL_TIERS.indexOf(right); }
function requireRunId(value) { if (!token(value)) throw new Error('verification run id is malformed'); }
async function writeExclusive(path, contents) {
  try { await writeFile(path, contents, { encoding: 'utf8', flag: 'wx' }); }
  catch (error) { if (error?.code === 'EEXIST') throw new Error(`verification evidence already exists: ${basename(path)}`); throw error; }
}
function inside(root, target) { const path = relative(resolve(root), resolve(target)); return path !== '' && !path.startsWith('..') && !path.includes('/..'); }
function safeChangedPath(value) { return typeof value === 'string' && value.length > 0 && !value.startsWith('/') && !value.includes('\\') && !value.split('/').includes('..'); }
function token(value) { return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(value); }
function sha(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
