import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { cp, mkdir, readFile, readdir, stat, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { homedir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { assertSemanticEvidence, F02B_KIND, F02B_PRIMARY_KIND, F02B_SCHEMA, hashJson, laneFor, recoveryMilestones } from './f02b-native-semantic.mjs';
import { consumeRuntime } from './f0vc-prepared-runtime.mjs';
import { requiresSaveOwnerObserver, writeSaveOwnerObserverContract } from './f02b-save-owner-observer-contract.mjs';

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
for (const field of ['gradle', 'cache', 'world', 'process']) await mkdir(namespaces[field], { recursive: true });
const scenarios = { 'normal-never-visited': ['disposable-f02b-normal-never-visited.json'], 'normal-visited-unloaded': ['disposable-f02b-normal-visited-unloaded.json'],
  'normal-zero-player-recovery': ['disposable-f02b-normal-zero-player.json', 'disposable-f02b-normal-product-recovery.json'],
  'conflict-restart': ['disposable-f02b-depot-changed-restart.json', 'disposable-f02b-depot-foreign-restart.json', 'disposable-f02b-depot-conflict-restart.json'] }[lane];
// The three conflict cases each restart the exact same disposable world without
// an injected client-loss crash.  Reuse the one ordinary client only for that
// declared compatible recovery: the isolated runner fences its reconnect with
// SAME_CLIENT_RECONNECTED_STATE_CLEARED before accepting the post-restart
// actions.  Product recovery retains separate pre/post client receipts.
const usePersistentClient = lane === 'conflict-restart';
const root = `build/f02b-native/${values.run}-${values.attempt}-${values.worker}`; const prepared = `${root}/prepared-build.json`;
const startedAtMillis = Date.now(); const stream = createWriteStream(log, { flags: 'wx' });
const run = async (args, extra = {}) => {
  const child = spawn(args[0], args.slice(1), { cwd: process.cwd(), env: { ...process.env, ...extra }, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(stream, { end: false }); child.stderr.pipe(stream, { end: false });
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', code => resolveExit(code)); });
  if (code !== 0) throw new Error(`F0.2B normal-world lane failed (${code})`);
  return child.pid;
};
const dispatchIsolatedScenario = async ({ scenario, declaration, declarationSha256, manifest }) => {
  const observerContract = requiresSaveOwnerObserver({ worker: values.worker, lane, scenario })
    ? await writeSaveOwnerObserverContract({ project: process.cwd(), output: `${root}/observer-contracts/${scenario}`,
      worker: values.worker, lane, runId: values.run, runAttempt: values.attempt, scenario, scenarioId: declaration.id,
      scenarioDeclarationSha256: declarationSha256, evidenceRoot: `${root}/owner-observation/${scenario.replace(/\.json$/, '')}` })
    : null;
  const pilotPid = await run([process.execPath, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', `tools/frontier-v3-test-pilot/scenarios/${scenario}`, manifest], {
    GRADLE_USER_HOME: namespaces.gradle, FRONTIER_V3_PILOT_PORT: String(namespaces.port), FRONTIER_V3_NATIVE_PROCESS_ROOT: namespaces.process,
    FRONTIER_V3_PILOT_WORKER_ID: values.worker, FRONTIER_V3_PREPARED_BUILD_IDENTITY: prepared,
    FRONTIER_V3_PILOT_USE_PERSISTENT_CLIENT: usePersistentClient ? 'true' : 'false',
    FRONTIER_V3_PILOT_PREPARED_RUNTIME: 'true', FRONTIER_V3_PILOT_GRACEFUL_SAVE_GATE: gracefulSaveGate,
    FRONTIER_V3_PILOT_INITIAL_CANONICAL_HOLD: 'true',
    ...(observerContract === null ? {} : {
      FRONTIER_V3_F02B_SAVE_OWNER_OBSERVER_REQUIREMENT: 'required',
      FRONTIER_V3_F02B_DIAGNOSTIC_LANE: lane,
      FRONTIER_V3_F02B_DIAGNOSTIC_RUN: String(values.run),
      FRONTIER_V3_F02B_DIAGNOSTIC_ATTEMPT: String(values.attempt),
      FRONTIER_V3_SAVE_OWNER_OBSERVER_CONTRACT: observerContract.path,
      FRONTIER_V3_SAVE_OWNER_OBSERVER_ROOT: observerContract.contract.evidenceRoot
    })
  });
  return { pilotPid, observerContract };
};
const readReturnedScenarioReceipt = async ({ manifest, scenario, declarationSha256, observerContract }) => {
  const value = JSON.parse(await readFile(resolve(manifest), 'utf8'));
  if (value.scenarioDeclarationSha256 !== declarationSha256) throw new Error(`F0.2B scenario receipt is not bound to its immutable declaration: ${scenario}`);
  if (observerContract !== null) assertRequiredObserverReturn(value, observerContract, declarationSha256, scenario);
  return value;
};
const diagnosticDispatchOnly = process.env.FRONTIER_V3_TEST_F02B_SAVE_OWNER_DISPATCH_ONLY;
if (diagnosticDispatchOnly !== undefined && diagnosticDispatchOnly !== 'true') throw new Error('F0.2B save-owner observer dispatch test mode is invalid');
if (diagnosticDispatchOnly === 'true') {
  const scenario = 'disposable-f02b-normal-product-recovery.json';
  const declarationSource = await readFile(resolve(`tools/frontier-v3-test-pilot/scenarios/${scenario}`));
  const declarationSha256 = createHash('sha256').update(declarationSource).digest('hex');
  const declaration = JSON.parse(declarationSource);
  const dispatched = await dispatchIsolatedScenario({ scenario, declaration, declarationSha256,
    manifest: `${root}/${scenario.replace(/\.json$/, '')}.manifest.json` });
  if (dispatched.observerContract === null) throw new Error('F0.2B diagnostic dispatch omitted its required observer contract');
  const returned = substitutedObserverReturn(dispatched.observerContract, declarationSha256);
  const mutation = process.env.FRONTIER_V3_TEST_F02B_SAVE_OWNER_RETURN_MUTATION;
  if (mutation !== undefined && !['absent', 'wrong-status', 'wrong-identity', 'incomplete'].includes(mutation)) {
    throw new Error('F0.2B save-owner observer return test mutation is invalid');
  }
  if (mutation === 'absent') delete returned.ownerObservationRequirement;
  if (mutation === 'wrong-status') returned.ownerObservationRequirement.status = 'admitted';
  if (mutation === 'wrong-identity') returned.ownerObservationRequirement.identity.worker = 'worker-3';
  if (mutation === 'incomplete') returned.ownerObservation.slots.pop();
  const manifest = `${root}/${scenario.replace(/\.json$/, '')}.manifest.json`;
  await writeFile(manifest, `${JSON.stringify(returned)}\n`, { flag: 'wx' });
  await readReturnedScenarioReceipt({ manifest, scenario, declarationSha256, observerContract: dispatched.observerContract });
  stream.end(); await new Promise(resolveClose => stream.once('close', resolveClose));
  console.log(JSON.stringify({ status: 'admitted', observerContract: dispatched.observerContract.path }));
  process.exit(0);
}
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
    const dispatched = await dispatchIsolatedScenario({ scenario, declaration, declarationSha256, manifest });
    const { observerContract } = dispatched; pilotPid = dispatched.pilotPid;
    const value = await readReturnedScenarioReceipt({ manifest, scenario, declarationSha256, observerContract });
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
  runtimeContentSha256, jarSha256, primarySha256, namespaces, gracefulSaveGate, scenarios, manifests: manifests.map(value => ({ scenario: value.scenario, manifest: value.manifest })), terminal };
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

function assertRequiredObserverReturn(value, observerContract, declarationSha256, scenario) {
  const requirement = value?.ownerObservationRequirement; const observation = value?.ownerObservation;
  const expected = observerContract.contract.identity; const slots = observerContract.contract.scheduledSlotOffsetsMs;
  if (requirement?.status !== 'accepted' || requirement.contract?.sha256 !== observerContract.sha256
      || JSON.stringify(requirement.identity) !== JSON.stringify(expected) || requirement.identity?.scenario !== scenario
      || requirement.identity?.scenarioDeclarationSha256 !== declarationSha256 || !Array.isArray(requirement.scheduledSlotOffsetsMs)
      || JSON.stringify(requirement.scheduledSlotOffsetsMs) !== JSON.stringify(slots) || observation?.status !== 'completed'
      || !Array.isArray(observation.slots) || observation.slots.length !== slots.length
      || observation.slots.some((slot, index) => slot?.index !== index || slot.offsetMs !== slots[index]
        || (slot.status !== 'captured' && slot.status !== 'unavailable') || typeof slot.receipt !== 'string' || slot.receipt.length === 0
        || !/^[0-9a-f]{64}$/.test(slot.sha256 ?? '') || (slot.status === 'unavailable' && !slot.reason?.kind))) {
    throw new Error(`F0.2B required save-owner observer return is absent, foreign, or incomplete: ${scenario}`);
  }
  return value;
}
function substitutedObserverReturn(observerContract, scenarioDeclarationSha256) {
  const slots = observerContract.contract.scheduledSlotOffsetsMs.map((offsetMs, index) => ({ index, offsetMs, status: 'captured',
    receipt: `build/f02b-native/substitute/slot-${index}.json`, sha256: 'a'.repeat(64) }));
  return { scenarioDeclarationSha256, ownerObservationRequirement: { status: 'accepted', contract: { sha256: observerContract.sha256 },
    identity: { ...observerContract.contract.identity }, scheduledSlotOffsetsMs: [...observerContract.contract.scheduledSlotOffsetsMs] },
  ownerObservation: { status: 'completed', slots } };
}

function normalHistory(scenario, declaration, manifest, beforeRestart) {
  if (manifest?.status !== 'ok' || manifest.initialCanonicalHold !== true || !Array.isArray(manifest.diagnostics) || !Array.isArray(manifest.actions)) throw new Error('F0.2B normal scenario has no complete receipt');
  const profile = declaration?.server?.profile;
  const diagnostics = [[beforeRestart, 'before_restart'], [manifest, 'after_restart']]
    .flatMap(([receipt, phase]) => Array.isArray(receipt?.diagnostics) ? receipt.diagnostics.map(entry => ({ ...entry, phase })) : [])
    .map(entry => ({ ...entry, actionStep: entry.actionStep ?? entry.observed?.actionStep ?? null, value: diagnosticValue(entry) }));
  const values = diagnostics.map(entry => ({ ...entry.value, actionStep: entry.actionStep })).filter(value => value?.status === 'ok');
  const at = (kind, id) => values.filter(value => value.kind === kind && value.id === id);
  const summary = at('summary', '').at(0); const depotObservations = at('container', 'container:1-depot'); const hiveObservations = at('container', 'container:hive-east-store');
  const referenceObservations = at('reference_container', 'f02b');
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
  const safelyUnloaded = entry => {
    if (!Number.isInteger(entry.actionStep) || entry.value?.kind !== 'container'
        || entry.value.physicalSocket?.chunk !== 'UNLOADED' || entry.value.custody?.status !== 'RELEASED') return false;
    // A per-scope unload is causal only after an ordinary departure and before
    // the next ordinary graybox arrival.  Do not tie it to the first due tick:
    // the zero-player history deliberately proves a later post-effect unload.
    const preceding = actions.slice(0, entry.actionStep - 1);
    const away = preceding.map((action, index) => ({ action, index }))
      .filter(({ action }) => action.type === 'visit' && action.dimension === 'minecraft:overworld').at(-1);
    return Boolean(away) && !preceding.slice(away.index + 1)
      .some(action => action.type === 'visit' && action.dimension === 'pale_mirror:frontier_graybox');
  };
  const safelyUnloadedEntries = diagnostics.filter(safelyUnloaded);
  const observedEpochs = [...depotObservations, ...hiveObservations].map(value => value.custody?.epoch).filter(Number.isSafeInteger);
  const releasedEpochs = safelyUnloadedEntries.map(entry => entry.value.custody.epoch);
  const zeroPlayerScopes = zeroPlayerScopeObservations(diagnostics, actions);
  const zeroPlayerLoaded = zeroPlayerScopes.length === 2;
  const safeUnloadScopes = Object.fromEntries(['container:1-depot', 'container:hive-east-store'].map(containerId => [containerId,
    safelyUnloadedEntries.some(entry => entry.value.id === containerId)]));
  const safeUnload = Object.values(safeUnloadScopes).every(Boolean);
  // Retain both the exact transformed stack boundary and the later terminal depot
  // state: the ordinary provision scheduler can consume its one named ration only
  // after the production effect has been confirmed.  A terminal-only count would
  // hide the 64-wheat -> 64-bread product receipt.
  const bread = Math.max(0, ...depotObservations.map(value => value.occupied?.find(item => item.itemKind === 'minecraft:bread')?.count ?? 0));
  const terminalBread = depot.occupied?.find(value => value.itemKind === 'minecraft:bread')?.count ?? 0;
  const wheat = initialWheat;
  const biomass = initialBiomass;
  const causal = referenceCausality(referenceObservations);
  const observerFreePhysicalEffects = {
    depot: physicalEffectObservation(depotObservations, 'zero_player_depot_effect_no_demand', value => (value.occupied?.find(item => item.itemKind === 'minecraft:bread')?.count ?? 0) === 64),
    hive: physicalEffectObservation(hiveObservations, 'zero_player_hive_effect_no_demand', value => (value.occupied?.find(item => item.itemKind === 'minecraft:rotten_flesh')?.count ?? 0) === 0)
  };
  const admission = { profile, initialIntents: summary.intents, initialReplica: false, initialCustody: false, targetVisitsBeforeDue,
    initialInstant: summary.instant, initialInputs: { depot: { wheat: earlyWheat, bread: earlyBread }, hive: { biomass: earlyBiomass } },
    observedEpochs, releasedEpochs, safeUnload, safeUnloadScopes, zeroPlayerLoaded, zeroPlayerScopes, observerFreePhysicalEffects, dueAction: due + 1, causal,
    ...(history === 'zero-player' ? { birthCatchup: zeroPlayerBirthCatchup(diagnostics, actions) } : {}) };
  const result = { history, admission, containers: { depot, hive: store }, families: {
    depot: { inputWheat: wheat, outputBread: bread, terminalBread, foodAvailable: settlement.food.available, foodFulfilled: settlement.food.fulfilled },
    hive: { inputBiomass: biomass, outputBiomass: store.occupied?.find(value => value.itemKind === 'minecraft:rotten_flesh')?.count ?? 0,
      growthJobs: hive.growthJobs, addedOrgans: hive.addedOrgans, spawnedBioforms: hive.spawnedBioforms }
  } };
  if (history === 'graceful-product-recovery') {
    const before = beforeRestart?.diagnostics?.map(diagnosticValue).filter(value => value?.kind === 'container' && value.id === 'container:1-depot').at(-1);
    result.recovery = { mode: manifest.recovery?.mode, beforeEpoch: before?.custody?.epoch ?? null, afterEpoch: depot.custody?.epoch ?? null,
      splitAfterAction: manifest.recovery?.splitAfterAction ?? null, milestones: recoveryMilestones(diagnostics) };
  }
  return result;
}

function referenceCausality(observations) {
  const normalized = observations.map(value => ({
    actionStep: value.actionStep,
    taskKinds: [...new Set((value.tasks ?? []).map(task => task.kind))].sort(),
    // The name alone is not an admission proof: retain each action's exact
    // owner, due instant, priority and id, and each work-order/reservation
    // relation.  The terminal three-history comparator consumes these values.
    schedules: (value.schedules ?? []).map(schedule => ({ id: schedule.id, subject: schedule.subject,
      kind: schedule.kind, dueAt: schedule.dueAt, weight: schedule.weight })).sort(compareJson),
    orders: (value.orders ?? []).map(order => ({ task: order.task, job: order.job, reservation: order.reservation,
      reservationActive: order.reservationActive, status: order.status })).sort(compareJson),
    productionJobs: (value.productionJobs ?? []).length,
    growthJobs: (value.growthJobs ?? []).length,
    physicalIntentKinds: [...new Set((value.physicalIntents ?? []).map(intent => `${intent.kind}:${intent.status}`))].sort()
  }));
  const taskKinds = [...new Set(normalized.flatMap(value => value.taskKinds))].sort();
  const schedules = uniqueJson(normalized.flatMap(value => value.schedules));
  const orders = uniqueJson(normalized.flatMap(value => value.orders));
  const physicalIntentKinds = [...new Set(normalized.flatMap(value => value.physicalIntentKinds))].sort();
  const admission = normalized.filter(value => value.taskKinds.includes('PRODUCE_BREAD') && value.taskKinds.includes('GROW_HIVE_ORGANISM')
    && value.orders.some(order => order.reservationActive === true) && Number.isSafeInteger(value.actionStep)).map(value => value.actionStep);
  return { observations: normalized.length, admissionAction: admission.length ? Math.min(...admission) : null, taskKinds, schedules, orders,
    productionStarted: normalized.some(value => value.productionJobs > 0), growthStarted: normalized.some(value => value.growthJobs > 0), physicalIntentKinds };
}

// The birth permit is not inferred from the convenient 63-bread endpoint.
// Both reads are ordinary, named diagnostics: the first is while both exact
// scopes are safely unloaded/released, and the second is after the ordinary
// return has allowed the registered exact-item executor to confirm that same
// permit.  This remains evidence only; it owns neither a schedule nor custody.
function zeroPlayerBirthCatchup(diagnostics, actions) {
  const milestone = name => diagnostics.map(entry => entry.value)
    .find(value => value?.pilotCausalMilestone === name);
  const admitted = milestone('zero_player_cold_birth_admitted');
  const consumed = milestone('zero_player_birth_consumed');
  const normalize = value => {
    const permit = Array.isArray(value?.birthJobs) && value.birthJobs.length === 1 ? value.birthJobs[0] : null;
    const intent = permit && Array.isArray(value?.physicalIntents)
      ? value.physicalIntents.find(candidate => candidate.id === permit.intent && candidate.cause === permit.id) : null;
    return value && permit && intent && { actionStep: value.pilotActionStep, instant: value.instant,
      job: { id: permit.id, food: permit.food, intent: permit.intent, resident: permit.resident },
      intent: { id: intent.id, cause: intent.cause, kind: intent.kind, status: intent.status } };
  };
  const cold = normalize(admitted); const afterReturn = normalize(consumed);
  const returnAction = cold && actions.findIndex((action, index) => index + 1 > cold.actionStep
    && action.type === 'visit' && action.dimension === 'pale_mirror:frontier_graybox') + 1;
  return { cold, afterReturn, returnAction };
}

function physicalEffectObservation(observations, milestone, hasExpectedOutput) {
  const value = observations.find(candidate => candidate.pilotCausalMilestone === milestone
    && naturallyLoadedWithoutPresentationDemand(candidate) && candidate.custody?.status === 'ACQUIRED'
    && Number.isSafeInteger(candidate.actionStep) && Number.isSafeInteger(candidate.custody?.epoch)
    && Number.isSafeInteger(candidate.replica?.revision) && hasExpectedOutput(candidate));
  return value && { actionStep: value.actionStep, custodyEpoch: value.custody.epoch, replicaRevision: value.replica.revision,
    replicaFingerprint: value.replica.fingerprint, milestone, ordinaryPlayerNearby: value.physicalSocket.ordinaryPlayerNearby,
    presentationDemand: value.physicalSocket.presentationDemand, eligibleObserverCount: value.physicalSocket.eligibleObserverCount,
    presentationObserverCount: value.physicalSocket.presentationObserverCount };
}

function compareJson(left, right) { return JSON.stringify(left).localeCompare(JSON.stringify(right)); }
function uniqueJson(values) {
  return [...new Map(values.map(value => [JSON.stringify(value), value])).values()].sort(compareJson);
}

function diagnosticValue(entry) {
  return entry?.value ?? entry?.observed?.value ?? null;
}

// These named reads are the authoritative natural-streaming proof.  A visit
// merely requests normal vanilla streaming; it is not evidence that the
// surface is loaded or observer-free.  Each family therefore retains its own
// loaded -> acquired -> effect -> released chain, with the server-calculated
// no-demand counts at every live boundary.
function zeroPlayerScopeObservations(diagnostics, actions) {
  const family = [
    { id: 'container:1-depot', name: 'depot' },
    { id: 'container:hive-east-store', name: 'hive' }
  ];
  return family.map(({ id, name }) => {
    const named = suffix => diagnostics.find(entry => entry?.value?.kind === 'container' && entry.value.id === id
      && entry.value.pilotCausalMilestone === `zero_player_${name}_${suffix}`);
    const loaded = named('loaded_no_demand'); const acquired = named('acquired_no_demand');
    const effect = named('effect_no_demand'); const released = named('released');
    const value = loaded?.value; const visit = loaded && latestGrayboxVisit(actions, loaded.actionStep);
    const releasedBeforeLoad = diagnostics.filter(entry => entry?.value?.kind === 'container' && entry.value.id === id
      && Number.isInteger(entry.actionStep) && entry.actionStep < loaded?.actionStep && entry.value.physicalSocket?.chunk === 'UNLOADED'
      && entry.value.custody?.status === 'RELEASED').at(-1);
    if (!loaded || !acquired || !effect || !released || !value?.position || !visit?.action?.position
        || !naturallyLoadedWithoutPresentationDemand(loaded.value) || !naturallyLoadedWithoutPresentationDemand(acquired.value)
        || !naturallyLoadedWithoutPresentationDemand(effect.value) || !releasedBeforeLoad
        || acquired.value.custody?.status !== 'ACQUIRED' || effect.value.custody?.status !== 'ACQUIRED'
        || released.value?.physicalSocket?.chunk !== 'UNLOADED' || released.value.custody?.status !== 'RELEASED'
        || !Number.isInteger(loaded.actionStep) || !Number.isInteger(acquired.actionStep) || !Number.isInteger(effect.actionStep)
        || !Number.isInteger(released.actionStep) || !(loaded.actionStep < acquired.actionStep && acquired.actionStep < effect.actionStep && effect.actionStep < released.actionStep)
        || !outsidePresentationEnvelope(visit.action.position, value.position)) return null;
    return { id, priorReleasedActionStep: releasedBeforeLoad.actionStep, priorReleasedCustodyEpoch: releasedBeforeLoad.value.custody?.epoch ?? null,
      visitStep: visit.index + 1, playerChunk: chunkOf(visit.action.position), scopeChunk: chunkOf(value.position),
      loadedMilestone: loaded.value.pilotCausalMilestone, acquiredMilestone: acquired.value.pilotCausalMilestone,
      effectMilestone: effect.value.pilotCausalMilestone, releasedMilestone: released.value.pilotCausalMilestone,
      loadedActionStep: loaded.actionStep, acquiredActionStep: acquired.actionStep, effectActionStep: effect.actionStep, releasedActionStep: released.actionStep,
      loadedCustodyEpoch: loaded.value.custody?.epoch ?? null, custodyEpoch: acquired.value.custody.epoch,
      effectCustodyEpoch: effect.value.custody?.epoch ?? null, releasedCustodyEpoch: released.value.custody?.epoch ?? null,
      loadedReplicaRevision: loaded.value.replica?.revision ?? null, replicaRevision: acquired.value.replica?.revision ?? null,
      effectReplicaRevision: effect.value.replica?.revision ?? null, releasedReplicaRevision: released.value.replica?.revision ?? null,
      replicaFingerprint: acquired.value.replica?.fingerprint ?? null, naturalChunkLoaded: true,
      ordinaryPlayerNearby: false, presentationDemand: false, eligibleObserverCount: 0, presentationObserverCount: 0 };
  }).filter(Boolean);
}

function naturallyLoadedWithoutPresentationDemand(value) {
  const socket = value?.physicalSocket;
  return socket?.chunk === 'LOADED' && socket.ordinaryPlayerNearby === false && socket.presentationDemand === false
    && socket.eligibleObserverCount === 0 && socket.presentationObserverCount === 0;
}

function latestGrayboxVisit(actions, actionStep) {
  return actions.slice(0, actionStep - 1).map((action, index) => ({ action, index }))
    .filter(({ action }) => action?.type === 'visit' && action.dimension === 'pale_mirror:frontier_graybox').at(-1);
}

function outsidePresentationEnvelope(visit, surface) {
  const dx = visit.x - surface.x; const dz = visit.z - surface.z;
  return dx * dx + dz * dz > 96 * 96;
}

function chunkOf(position) {
  return { x: Math.floor(position.x / 16), z: Math.floor(position.z / 16) };
}

function conflictFacts(manifestValue) {
  if (manifestValue?.status !== 'ok' || !Array.isArray(manifestValue.diagnostics)) throw new Error('F0.2B conflict scenario has no terminal manifest');
  const container = manifestValue.diagnostics.map(diagnosticValue)
    .filter(value => value?.kind === 'container' && value.status === 'ok').at(-1);
  const clientSession = manifestValue.recovery?.clientSession;
  if (!container?.replica || !container?.custody || manifestValue.recovery?.mode !== 'abrupt'
      || clientSession?.reusedJvm !== true || typeof clientSession.runId !== 'string' || clientSession.runId.length === 0) {
    throw new Error('F0.2B conflict scenario lacks recovered terminal evidence');
  }
  return { recovery: manifestValue.recovery, clientSession: { runId: clientSession.runId, reusedJvm: true },
    container, replica: container.replica, custody: container.custody };
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
