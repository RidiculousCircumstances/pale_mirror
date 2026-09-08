import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { cp, mkdir, readFile, readdir, stat, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { homedir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { assertSemanticEvidence, F02B_KIND, F02B_PRIMARY_KIND, F02B_SCHEMA, hashJson, laneFor } from './f02b-native-semantic.mjs';
import { consumeRuntime } from './f0vc-prepared-runtime.mjs';

const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const namespaces = JSON.parse(await readFile(resolve(values.namespaces ?? ''), 'utf8'));
const identity = JSON.parse(await readFile(resolve(values.identity ?? ''), 'utf8'));
const output = resolve(values.output ?? ''); const log = resolve(values.log ?? ''); const lane = laneFor(values.worker);
const runtimePath = resolve(values.runtime ?? '');
if (!output.startsWith(`${process.cwd()}/`) || !log.startsWith(`${process.cwd()}/`) || !runtimePath.startsWith(`${process.cwd()}/`)
  || values.lane !== lane || process.env.DISPLAY !== namespaces.display) throw new Error('F0.2B normal-world semantic invocation is malformed');
for (const field of ['gradle', 'cache', 'world', 'process']) await mkdir(namespaces[field], { recursive: true });
const scenarios = { 'depot-never-visited': ['disposable-settlement-provision.json'], 'depot-visited-unloaded': ['disposable-materialized-production-work-restart.json'], 'hive-zero-player': ['disposable-hive-growth.json'],
  'conflict-restart': ['disposable-f02b-depot-changed-restart.json', 'disposable-f02b-depot-foreign-restart.json', 'disposable-f02b-depot-conflict-restart.json'] }[lane];
const root = `build/f02b-native/${values.run}-${values.attempt}-${values.worker}`; const prepared = `${root}/prepared-build.json`;
const startedAtMillis = Date.now(); const stream = createWriteStream(log, { flags: 'wx' });
const run = async (args, extra = {}) => {
  const child = spawn(args[0], args.slice(1), { cwd: process.cwd(), env: { ...process.env, ...extra }, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(stream, { end: false }); child.stderr.pipe(stream, { end: false });
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', code => resolveExit(code)); });
  if (code !== 0) throw new Error(`F0.2B normal-world lane failed (${code})`);
  return child.pid;
};
let pilotPid; let runtimeContentSha256; let consumed; const manifests = [];
try {
  // F0.VC owns the only build/package/transform pass.  This consumer gets a
  // private immutable launch view and may only prepare its disposable world and
  // execute the assigned normal-world scenario through that view.
  const runtime = JSON.parse(await readFile(runtimePath, 'utf8'));
  consumed = await consumeRuntime({ manifest: runtime.manifest, worker: values.worker, output: prepared });
  // World reset still invokes its declared offline Gradle task, but its mutable
  // cache is copied into this worker before use.  It never supplies launch
  // artifacts: those are the immutable F0.VC consumer view above.
  await seedOfflineGradleHome(namespaces.gradle);
  for (const scenario of scenarios) {
    const manifest = `${root}/${scenario.replace(/\.json$/, '')}.manifest.json`;
    pilotPid = await run([process.execPath, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', `tools/frontier-v3-test-pilot/scenarios/${scenario}`, manifest], {
      GRADLE_USER_HOME: namespaces.gradle, FRONTIER_V3_PILOT_PORT: String(namespaces.port), FRONTIER_V3_NATIVE_PROCESS_ROOT: namespaces.process,
      FRONTIER_V3_PILOT_WORKER_ID: values.worker, FRONTIER_V3_PREPARED_BUILD_IDENTITY: prepared, FRONTIER_V3_PILOT_USE_PERSISTENT_CLIENT: 'false',
      FRONTIER_V3_PILOT_PREPARED_RUNTIME: 'true'
    });
    const value = JSON.parse(await readFile(resolve(manifest), 'utf8'));
    const beforeRestartManifest = value?.recovery?.beforeRestartManifest;
    const beforeRestart = beforeRestartManifest
      ? JSON.parse(await readFile(resolve(beforeRestartManifest), 'utf8')) : null;
    manifests.push({ scenario, manifest, value, beforeRestart });
  }
  runtimeContentSha256 = consumed.receipt.runtimeContentSha256;
} finally { stream.end(); await new Promise(resolveClose => stream.once('close', resolveClose)); }
const finishedAtMillis = Date.now(); const terminal = terminalFacts(manifests, lane);
const jar = (await readdir(resolve('pale-mirror-neoforge/build/libs'))).filter(name => name.endsWith('.jar') && !name.endsWith('-sources.jar')).sort().at(-1);
if (!jar) throw new Error('F0.2B normal-world launch produced no distributable jar');
const jarSha256 = createHash('sha256').update(await readFile(resolve('pale-mirror-neoforge/build/libs', jar))).digest('hex');
const identityFact = { qualificationId: values.qualification, repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'], workflowRef: values.workflow,
  runId: Number(values.run), runAttempt: Number(values.attempt), jobId: identity.jobId, runnerId: identity.runnerId, runnerName: identity.runnerName,
  launchTarget: 'normal-disposable-v3-server', requiredTest: `scenario:${scenarios.join('+')}`, requiredTestCount: scenarios.length };
const primary = { schema: F02B_SCHEMA, kind: F02B_PRIMARY_KIND, status: 'passed', worker: values.worker, lane, identity: identityFact, runtimeContentSha256, jarSha256,
  runtime: { receipt: JSON.parse(await readFile(resolve(`${root}/consumer-${values.worker}.json`), 'utf8')), preparedIdentity: consumed.identity },
  manifests: manifests.map(value => ({ scenario: value.scenario, sha256: hashJson(value.value), value: value.value,
    ...(value.beforeRestart == null ? {} : { beforeRestartSha256: hashJson(value.beforeRestart), beforeRestart: value.beforeRestart }) })), terminal };
const primarySha256 = hashJson(primary);
const evidence = { schema: F02B_SCHEMA, kind: F02B_KIND, status: 'passed', worker: values.worker, lane, ...identityFact, startedAtMillis, finishedAtMillis, gradlePid: pilotPid,
  runtimeContentSha256, jarSha256, primarySha256, namespaces, scenarios, manifests: manifests.map(value => ({ scenario: value.scenario, manifest: value.manifest })), terminal };
assertSemanticEvidence(evidence); await mkdir(dirname(output), { recursive: true }); await writeFile(resolve(dirname(output), 'primary.json'), `${JSON.stringify(primary)}\n`, { flag: 'wx' }); await writeFile(output, `${JSON.stringify(evidence)}\n`, { flag: 'wx' });

function terminalFacts(manifests, assignedLane) {
  if (!Array.isArray(manifests) || manifests.length === 0) throw new Error('F0.2B native scenario has no terminal manifest');
  if (assignedLane === 'conflict-restart') {
    const conflicts = manifests.map(({ scenario, value }) => ({ scenario, ...conflictFacts(value) }));
    const last = conflicts.at(-1);
    return { lane: assignedLane, scenarioIds: conflicts.map(value => value.scenario), recovery: last.recovery, container: last.container,
      replica: last.replica, custody: last.custody, conflicts, domain: { family: 'depot-conflict', recovery: last.recovery?.mode ?? null } };
  }
  const manifestValue = manifests[0].value;
  if (manifestValue?.status !== 'ok' || !Array.isArray(manifestValue.diagnostics)) throw new Error('F0.2B native scenario has no terminal manifest');
  // A real restart has two durable receipts.  The pre-restart segment contains the
  // production completion while the resumed segment proves the recovered replica/custody
  // boundary.  Retain and compare both rather than treating the final client segment alone
  // as a complete product result.
  const values = [manifests[0].beforeRestart, manifestValue].flatMap(receipt =>
    Array.isArray(receipt?.diagnostics) ? receipt.diagnostics.map(value => value?.value) : [])
    .filter(value => value?.status === 'ok');
  const find = (kind, id) => values.filter(value => value.kind === kind && value.id === id).at(-1);
  const containers = values.filter(value => value.kind === 'container');
  const selected = containers.at(-1);
  if (!selected || !selected.replica || !selected.custody) throw new Error('F0.2B native scenario has no terminal domain/replica/custody comparison');
  const base = { lane: assignedLane, scenarioId: manifestValue.scenarioId, recovery: manifestValue.recovery ?? null,
    container: selected, replica: selected.replica, custody: selected.custody };
  if (assignedLane === 'depot-never-visited') {
    const settlement = find('settlement', 'settlement:1'); const intent = find('intent', 'intent:settlement-provision-1-2-0');
    if (!settlement?.food || !intent) throw new Error('F0.2B settlement provision lane lacks terminal domain facts');
    return { ...base, domain: { family: 'settlement-provision', foodStatus: settlement.food.status, available: settlement.food.available,
      fulfilled: settlement.food.fulfilled, intentStatus: intent.intentStatus } };
  }
  if (assignedLane === 'depot-visited-unloaded') {
    const order = find('market_order', 'order:development-production-input-theft'); const item = find('item', 'item:development-production-input-theft-bread');
    if (!order || !item) throw new Error('F0.2B production lane lacks terminal domain facts');
    return { ...base, domain: { family: 'settlement-production', orderStatus: order.orderStatus, itemCount: item.count, itemCustody: item.custody?.kind } };
  }
  if (assignedLane === 'hive-zero-player') {
    const hive = find('hive', 'hive:frontier'); const intent = find('intent', 'intent:hive-growth-biomass-1');
    if (!hive || !intent) throw new Error('F0.2B hive lane lacks terminal domain facts');
    return { ...base, domain: { family: 'hive-growth', growthJobs: hive.growthJobs, addedOrgans: hive.addedOrgans,
      spawnedBioforms: hive.spawnedBioforms, intentStatus: intent.intentStatus } };
  }
  throw new Error(`F0.2B has no terminal extractor for ${assignedLane}`);
}

function conflictFacts(manifestValue) {
  if (manifestValue?.status !== 'ok' || !Array.isArray(manifestValue.diagnostics)) throw new Error('F0.2B conflict scenario has no terminal manifest');
  const container = manifestValue.diagnostics.map(value => value?.value)
    .filter(value => value?.kind === 'container' && value.status === 'ok').at(-1);
  if (!container?.replica || !container?.custody || manifestValue.recovery?.mode !== 'abrupt') {
    throw new Error('F0.2B conflict scenario lacks recovered terminal evidence');
  }
  return { recovery: manifestValue.recovery, container, replica: container.replica, custody: container.custody };
}

async function seedOfflineGradleHome(destination) {
  const seed = resolve(process.env.FRONTIER_V3_GRADLE_SEED ?? `${homedir()}/.gradle`);
  try {
    if (!(await stat(seed)).isDirectory()) throw new Error('not a directory');
  } catch (error) {
    throw new Error(`F0.2B requires an existing offline Gradle seed at ${seed}`, { cause: error });
  }
  for (const entry of ['caches', 'wrapper']) {
    const source = resolve(seed, entry); const target = resolve(destination, entry);
    try {
      if ((await stat(source)).isDirectory()) await cp(source, target, { recursive: true, force: false, errorOnExist: true });
    } catch (error) {
      if (error?.code !== 'ENOENT') throw new Error(`F0.2B could not isolate Gradle ${entry}`, { cause: error });
    }
  }
}
