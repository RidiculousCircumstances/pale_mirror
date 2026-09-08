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
const jvmEnvelope = Object.freeze({ javaToolOptions: '-Xmx3G', maxHeapMiB: 3072, concurrentMinecraftProcesses: 2 });
if (process.env.JAVA_TOOL_OPTIONS !== jvmEnvelope.javaToolOptions) throw new Error('F0.2B normal-world semantic invocation has no exact bounded JVM envelope');
const gracefulSaveGate = `${process.env.F02B_GRACEFUL_SAVE_GATE ?? ''}`;
if (!gracefulSaveGate.startsWith('/') || !gracefulSaveGate.endsWith(`f02b-graceful-save-${values.run}-${values.attempt}`)) {
  throw new Error('F0.2B normal-world semantic invocation has no exact graceful-save gate');
}
const executionGate = `${process.env.F02B_EXECUTION_GATE ?? ''}`;
if (!executionGate.startsWith('/') || !executionGate.endsWith(`f02b-native-execution-${values.run}-${values.attempt}`)) {
  throw new Error('F0.2B normal-world semantic invocation has no exact native execution gate');
}
for (const field of ['gradle', 'cache', 'world', 'process']) await mkdir(namespaces[field], { recursive: true });
const scenarios = { 'normal-never-visited': ['disposable-f02b-normal-never-visited.json'], 'normal-visited-unloaded': ['disposable-f02b-normal-visited-unloaded.json'],
  'normal-zero-player-recovery': ['disposable-f02b-normal-zero-player.json', 'disposable-f02b-normal-product-recovery.json'],
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
    const declarationSource = await readFile(resolve(`tools/frontier-v3-test-pilot/scenarios/${scenario}`));
    const declarationSha256 = createHash('sha256').update(declarationSource).digest('hex');
    const declaration = JSON.parse(declarationSource);
    const manifest = `${root}/${scenario.replace(/\.json$/, '')}.manifest.json`;
    pilotPid = await run([process.execPath, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', `tools/frontier-v3-test-pilot/scenarios/${scenario}`, manifest], {
      GRADLE_USER_HOME: namespaces.gradle, FRONTIER_V3_PILOT_PORT: String(namespaces.port), FRONTIER_V3_NATIVE_PROCESS_ROOT: namespaces.process,
      FRONTIER_V3_PILOT_WORKER_ID: values.worker, FRONTIER_V3_PREPARED_BUILD_IDENTITY: prepared, FRONTIER_V3_PILOT_USE_PERSISTENT_CLIENT: 'false',
      FRONTIER_V3_PILOT_PREPARED_RUNTIME: 'true', FRONTIER_V3_PILOT_GRACEFUL_SAVE_GATE: gracefulSaveGate,
      FRONTIER_V3_PILOT_EXECUTION_GATE: executionGate, FRONTIER_V3_PILOT_INITIAL_CANONICAL_HOLD: 'true'
    });
    const value = JSON.parse(await readFile(resolve(manifest), 'utf8'));
    if (value.scenarioDeclarationSha256 !== declarationSha256) throw new Error(`F0.2B scenario receipt is not bound to its immutable declaration: ${scenario}`);
    const beforeRestartManifest = value?.recovery?.beforeRestartManifest;
    const beforeRestart = beforeRestartManifest
      ? JSON.parse(await readFile(resolve(beforeRestartManifest), 'utf8')) : null;
    manifests.push({ scenario, declaration, declarationSha256, manifest, value, beforeRestart });
  }
  runtimeContentSha256 = consumed.receipt.runtimeContentSha256;
} finally { stream.end(); await new Promise(resolveClose => stream.once('close', resolveClose)); }
const finishedAtMillis = Date.now(); const terminal = terminalFacts(manifests, lane);
const jar = (await readdir(resolve('pale-mirror-neoforge/build/libs'))).filter(name => name.endsWith('.jar') && !name.endsWith('-sources.jar')).sort().at(-1);
if (!jar) throw new Error('F0.2B normal-world launch produced no distributable jar');
const jarSha256 = createHash('sha256').update(await readFile(resolve('pale-mirror-neoforge/build/libs', jar))).digest('hex');
const identityFact = { qualificationId: values.qualification, repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'], workflowRef: values.workflow,
  runId: Number(values.run), runAttempt: Number(values.attempt), jobId: identity.jobId, runnerId: identity.runnerId, runnerName: identity.runnerName,
  launchTarget: 'normal-disposable-v3-server', requiredTest: `scenario:${scenarios.join('+')}`, requiredTestCount: scenarios.length, jvmEnvelope };
const primary = { schema: F02B_SCHEMA, kind: F02B_PRIMARY_KIND, status: 'passed', worker: values.worker, lane, identity: identityFact, runtimeContentSha256, jarSha256,
  runtime: { receipt: JSON.parse(await readFile(resolve(`${root}/consumer-${values.worker}.json`), 'utf8')), preparedIdentity: consumed.identity },
  gracefulSaveGate, manifests: manifests.map(value => ({ scenario: value.scenario, declarationSha256: value.declarationSha256, sha256: hashJson(value.value), value: value.value,
    ...(value.beforeRestart == null ? {} : { beforeRestartSha256: hashJson(value.beforeRestart), beforeRestart: value.beforeRestart }) })), terminal };
const primarySha256 = hashJson(primary);
const evidence = { schema: F02B_SCHEMA, kind: F02B_KIND, status: 'passed', worker: values.worker, lane, ...identityFact, startedAtMillis, finishedAtMillis, gradlePid: pilotPid,
  runtimeContentSha256, jarSha256, primarySha256, namespaces, gracefulSaveGate, executionGate, scenarios, manifests: manifests.map(value => ({ scenario: value.scenario, manifest: value.manifest })), terminal };
assertSemanticEvidence(evidence); await mkdir(dirname(output), { recursive: true }); await writeFile(resolve(dirname(output), 'primary.json'), `${JSON.stringify(primary)}\n`, { flag: 'wx' }); await writeFile(output, `${JSON.stringify(evidence)}\n`, { flag: 'wx' });

function terminalFacts(manifests, assignedLane) {
  if (!Array.isArray(manifests) || manifests.length === 0) throw new Error('F0.2B native scenario has no terminal manifest');
  if (assignedLane === 'conflict-restart') {
    const conflicts = manifests.map(({ scenario, value }) => ({ scenario, ...conflictFacts(value) }));
    const last = conflicts.at(-1);
    return { lane: assignedLane, scenarioIds: conflicts.map(value => value.scenario), recovery: last.recovery, container: last.container,
      replica: last.replica, custody: last.custody, conflicts, domain: { family: 'depot-conflict', recovery: last.recovery?.mode ?? null } };
  }
  const histories = manifests.map(({ scenario, declaration, value, beforeRestart }) => normalHistory(scenario, declaration, value, beforeRestart));
  const finalHistory = histories.at(-1);
  const selected = finalHistory.containers.depot;
  return { lane: assignedLane, scenarioIds: manifests.map(value => value.scenario), domain: { family: 'normal-world-product-comparator' },
    container: selected, replica: selected.replica, custody: selected.custody, histories };
}

function normalHistory(scenario, declaration, manifest, beforeRestart) {
  if (manifest?.status !== 'ok' || manifest.initialCanonicalHold !== true || !Array.isArray(manifest.diagnostics) || !Array.isArray(manifest.actions)) throw new Error('F0.2B normal scenario has no complete receipt');
  const profile = declaration?.server?.profile;
  const diagnostics = [beforeRestart, manifest].flatMap(receipt => Array.isArray(receipt?.diagnostics) ? receipt.diagnostics : [])
    .map(entry => ({ ...entry, actionStep: entry.actionStep ?? entry.observed?.actionStep ?? null, value: diagnosticValue(entry) }));
  const values = diagnostics.map(entry => ({ ...entry.value, actionStep: entry.actionStep })).filter(value => value?.status === 'ok');
  const at = (kind, id) => values.filter(value => value.kind === kind && value.id === id);
  const summary = at('summary', '').at(0); const depotObservations = at('container', 'container:1-depot'); const hiveObservations = at('container', 'container:hive-east-store');
  const initialWheatItem = at('item', 'item:bootstrap-1-wheat').at(0); const initialBiomassItem = at('item', 'item:bootstrap-hive-biomass').at(0);
  const settlement = at('settlement', 'settlement:1').at(-1); const hive = at('hive', 'hive:frontier').at(-1);
  const depot = depotObservations.at(-1); const store = hiveObservations.at(-1);
  if (!summary || !Number.isSafeInteger(summary.instant) || summary.instant < 0
      || !depot || !store || !settlement?.food || !hive || !depot.replica || !depot.custody || !store.replica || !store.custody) throw new Error('F0.2B normal scenario lacks terminal product or custody diagnostics');
  const actions = declaration.actions;
  const initialDepotAction = actions.findIndex(action => action.type === 'inspect' && action.view === 'container' && action.id === 'container:1-depot') + 1;
  const initialStoreAction = actions.findIndex(action => action.type === 'inspect' && action.view === 'container' && action.id === 'container:hive-east-store') + 1;
  const earlyDepot = depotObservations.find(value => value.actionStep === initialDepotAction);
  const earlyStore = hiveObservations.find(value => value.actionStep === initialStoreAction);
  const earlyWheat = earlyDepot?.occupied?.find(item => item.itemKind === 'minecraft:wheat')?.count ?? 0;
  const earlyBread = earlyDepot?.occupied?.find(item => item.itemKind === 'minecraft:bread')?.count ?? 0;
  const earlyBiomass = earlyStore?.occupied?.find(item => item.itemKind === 'minecraft:rotten_flesh')?.count ?? 0;
  // A restart receipt is deliberately split between pre- and post-restart
  // diagnostics.  It is not required to repeat auxiliary item diagnostics:
  // the first ordinary container inspection is the authoritative physical
  // proof of the exact initial contents.  If an item diagnostic is present,
  // still fence it against the same source of truth rather than accepting a
  // contradictory duplicate assertion.
  const initialWheat = initialWheatItem?.count ?? earlyWheat;
  const initialBiomass = initialBiomassItem?.count ?? earlyBiomass;
  if (!earlyDepot || !earlyStore || earlyDepot.replica !== null || earlyDepot.custody !== null || earlyStore.replica !== null || earlyStore.custody !== null
      || earlyWheat !== 64 || earlyBread !== 0 || earlyBiomass !== 64
      || initialWheat !== earlyWheat || initialBiomass !== earlyBiomass
      || (initialWheatItem && initialWheatItem.custody?.kind !== 'CONTAINER_SLOT')
      || (initialBiomassItem && initialBiomassItem.custody?.kind !== 'CONTAINER_SLOT')) {
    throw new Error('F0.2B normal scenario did not start from unseeded replica/custody');
  }
  const id = scenario.replace(/\.json$/, '');
  const history = id.includes('never-visited') ? 'never-visited' : id.includes('visited-unloaded') ? 'visited-unloaded'
    : id.includes('zero-player') ? 'zero-player' : id.includes('product-recovery') ? 'graceful-product-recovery' : null;
  if (!history) throw new Error('F0.2B normal scenario is not an admitted history');
  const due = actions.findIndex(action => action.type === 'fast_forward' || action.type === 'fast_forward_to_instant');
  const targetVisitsBeforeDue = actions.slice(0, due).filter(action => action.type === 'visit' && action.dimension === 'pale_mirror:frontier_graybox').length;
  const away = actions.findIndex(action => action.type === 'visit' && action.dimension === 'minecraft:overworld');
  const interim = diagnostics.filter(entry => entry.actionStep != null && entry.actionStep > away + 1 && entry.actionStep < due + 1)
    .map(entry => entry.value).filter(value => value?.kind === 'container');
  const observedEpochs = [...depotObservations, ...hiveObservations].map(value => value.custody?.epoch).filter(Number.isSafeInteger);
  const releasedEpochs = interim.filter(value => value.custody?.status === 'RELEASED').map(value => value.custody.epoch);
  const zeroPlayerScopes = zeroPlayerScopeObservations(diagnostics, actions);
  const zeroPlayerLoaded = zeroPlayerScopes.length === 2;
  const safeUnload = interim.some(value => value.physicalSocket?.chunk === 'UNLOADED' && value.custody?.status === 'RELEASED');
  // Retain both the exact transformed stack boundary and the later terminal depot
  // state: the ordinary provision scheduler can consume its one named ration only
  // after the production effect has been confirmed.  A terminal-only count would
  // hide the 64-wheat -> 64-bread product receipt.
  const bread = Math.max(0, ...depotObservations.map(value => value.occupied?.find(item => item.itemKind === 'minecraft:bread')?.count ?? 0));
  const terminalBread = depot.occupied?.find(value => value.itemKind === 'minecraft:bread')?.count ?? 0;
  const wheat = initialWheat;
  const biomass = initialBiomass;
  const admission = { profile, initialIntents: summary.intents, initialReplica: false, initialCustody: false, targetVisitsBeforeDue,
    initialInstant: summary.instant, initialInputs: { depot: { wheat: earlyWheat, bread: earlyBread }, hive: { biomass: earlyBiomass } },
    observedEpochs, releasedEpochs, safeUnload, zeroPlayerLoaded, zeroPlayerScopes, dueAction: due + 1 };
  const result = { history, admission, containers: { depot, hive: store }, families: {
    depot: { inputWheat: wheat, outputBread: bread, terminalBread, foodAvailable: settlement.food.available, foodFulfilled: settlement.food.fulfilled },
    hive: { inputBiomass: biomass, outputBiomass: store.occupied?.find(value => value.itemKind === 'minecraft:rotten_flesh')?.count ?? 0,
      growthJobs: hive.growthJobs, addedOrgans: hive.addedOrgans, spawnedBioforms: hive.spawnedBioforms }
  } };
  if (history === 'graceful-product-recovery') {
    const before = beforeRestart?.diagnostics?.map(diagnosticValue).filter(value => value?.kind === 'container' && value.id === 'container:1-depot').at(-1);
    result.recovery = { mode: manifest.recovery?.mode, beforeEpoch: before?.custody?.epoch ?? null, afterEpoch: depot.custody?.epoch ?? null,
      splitAfterAction: manifest.recovery?.splitAfterAction ?? null };
  }
  return result;
}

function diagnosticValue(entry) {
  return entry?.value ?? entry?.observed?.value ?? null;
}

// This cannot be inferred from a later return.  Retain the ordinary inspection
// while vanilla player loading holds the reference chunk, and prove the sole
// pilot was in a different chunk from each observed reference surface.
function zeroPlayerScopeObservations(diagnostics, actions) {
  const observations = new Map();
  for (const entry of diagnostics) {
    const value = entry?.value;
    if (!Number.isInteger(entry?.actionStep) || value?.kind !== 'container' || !value.id
        || value.physicalSocket?.chunk !== 'LOADED' || value.custody?.status !== 'ACQUIRED') continue;
    const visit = actions.slice(0, entry.actionStep - 1).map((action, index) => ({ action, index }))
      .filter(({ action }) => action?.type === 'visit' && action.dimension === 'pale_mirror:frontier_graybox').at(-1);
    if (!visit?.action?.position || !value.position) continue;
    const playerChunk = chunkOf(visit.action.position); const scopeChunk = chunkOf(value.position);
    if (playerChunk.x === scopeChunk.x && playerChunk.z === scopeChunk.z) continue;
    observations.set(value.id, { id: value.id, visitStep: visit.index + 1, observationStep: entry.actionStep,
      playerChunk, scopeChunk, custodyEpoch: value.custody.epoch, replicaRevision: value.replica?.revision ?? null });
  }
  return [...observations.values()].sort((left, right) => left.id.localeCompare(right.id));
}

function chunkOf(position) {
  return { x: Math.floor(position.x / 16), z: Math.floor(position.z / 16) };
}

function conflictFacts(manifestValue) {
  if (manifestValue?.status !== 'ok' || !Array.isArray(manifestValue.diagnostics)) throw new Error('F0.2B conflict scenario has no terminal manifest');
  const container = manifestValue.diagnostics.map(diagnosticValue)
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
