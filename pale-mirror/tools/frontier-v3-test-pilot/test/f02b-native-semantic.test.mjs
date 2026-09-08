import assert from 'node:assert/strict';
import test from 'node:test';
import { assertPrimaryEvidence, f02bNamespaces, hashJson, laneFor, mergeSemanticMatrix } from '../src/f02b-native-semantic.mjs';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const sha = 'a'.repeat(40); const hash = 'b'.repeat(64);
const expected = Object.freeze({ qualificationId: 'f02b-r1', repository: 'RidiculousCircumstances/pale_mirror', headSha: sha, workflowSha: sha,
  workflowRef: 'RidiculousCircumstances/pale_mirror/.github/workflows/f02b-reference-container-semantic.yml@refs/heads/main', runId: 44, runAttempt: 1 });

function evidence(worker, index = Number(worker.at(-1))) {
  const lane = laneFor(worker); const namespaces = f02bNamespaces({ workspace: `/tmp/f02b/workspace-${worker}`, temp: `/tmp/f02b/${worker}`, runId: 44, runAttempt: 1, worker });
  const conflict = lane === 'conflict-restart';
  const replica = conflict
    ? { state: 'CONFLICT', revision: 3, canonicalRevision: 7, conflict: 'FINGERPRINT_MISMATCH', fingerprint: 'sha256:expected', provenance: 'pale-mirror:reference-container:test', observedFingerprint: 'sha256:missing-container:1-depot', observedProvenance: 'missing:container:1-depot' }
    : { state: 'OBSERVED_CURRENT', revision: 3, canonicalRevision: 7, conflict: '', fingerprint: 'sha256:current', provenance: 'pale-mirror:reference-container:test', observedFingerprint: '', observedProvenance: '' };
  const normal = (history) => ({ history, admission: { profile: 'world', initialIntents: 0, initialReplica: false, initialCustody: false, initialInstant: 227,
    initialInputs: { depot: { wheat: 64, bread: 0 }, hive: { biomass: 64 } },
    targetVisitsBeforeDue: history === 'never-visited' ? 0 : 2, observedEpochs: [1, 2], releasedEpochs: history === 'visited-unloaded' ? [1] : [],
    safeUnload: history === 'visited-unloaded' || history === 'zero-player', zeroPlayerLoaded: history === 'zero-player',
    safeUnloadScopes: history === 'visited-unloaded' || history === 'zero-player' ? { 'container:1-depot': true, 'container:hive-east-store': true } : {},
    zeroPlayerScopes: history === 'zero-player' ? [
      { id: 'container:1-depot', visitStep: 3, observationStep: 5, playerChunk: { x: -21, z: -21 }, scopeChunk: { x: -22, z: -21 }, custodyEpoch: 1, replicaRevision: 1, ordinaryPlayerNearby: false },
      { id: 'container:hive-east-store', visitStep: 6, observationStep: 8, playerChunk: { x: 26, z: 26 }, scopeChunk: { x: 25, z: 26 }, custodyEpoch: 1, replicaRevision: 1, ordinaryPlayerNearby: false }
    ] : [], observerFreePhysicalEffects: history === 'zero-player' ? {
      depot: { actionStep: 5, custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:depot-bread', ordinaryPlayerNearby: false },
      hive: { actionStep: 6, custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:hive-growth', ordinaryPlayerNearby: false }
    } : { depot: null, hive: null }, dueAction: 3,
    causal: { observations: 1, taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: [
      { id: 'schedule:hive-growth', subject: 'job:hive-east-growth', kind: 'frontier.hive.growth.complete', dueAt: 227, weight: 1 },
      { id: 'schedule:produce-bread', subject: 'job:settlement-1-bread', kind: 'frontier.settlement.production.complete', dueAt: 227, weight: 1 }
    ], orders: [{ task: 'task:settlement-1-bread', job: 'job:settlement-1-bread', reservation: 'reservation:bread-input', reservationActive: true, status: 'ACCEPTED' }], productionStarted: true, growthStarted: true,
      physicalIntentKinds: ['EXACT_ITEM_CONSUMPTION:CONFIRMED', 'PRODUCTION_TRANSFORMATION:CONFIRMED'], admissionAction: 3 } },
    families: { depot: { inputWheat: 64, outputBread: 64, terminalBread: 63, foodAvailable: 63, foodFulfilled: 0 },
      hive: { inputBiomass: 64, outputBiomass: 0, growthJobs: 0, addedOrgans: 1, spawnedBioforms: 1 } },
    ...(history === 'graceful-product-recovery' ? { recovery: { mode: 'graceful', beforeEpoch: 1, afterEpoch: 2, splitAfterAction: 5 } } : {}) });
  const histories = lane === 'normal-never-visited' ? [normal('never-visited')]
    : lane === 'normal-visited-unloaded' ? [normal('visited-unloaded')]
      : lane === 'normal-zero-player-recovery' ? [normal('zero-player'), normal('graceful-product-recovery')] : undefined;
  const domain = lane === 'conflict-restart' ? { family: 'depot-conflict', recovery: 'abrupt' } : { family: 'normal-world-product-comparator' };
  const custody = { status: conflict ? 'RELEASED' : 'ACQUIRED', epoch: 1, replicaRevision: 3 };
  const conflicts = conflict ? [
    { scenario: 'disposable-f02b-depot-changed-restart.json', clientSession: { runId: 'changed-restart', reusedJvm: true }, replica: { ...replica, conflict: 'FINGERPRINT_MISMATCH', observedFingerprint: 'sha256:changed', observedProvenance: replica.provenance } },
    { scenario: 'disposable-f02b-depot-foreign-restart.json', clientSession: { runId: 'foreign-restart', reusedJvm: true }, replica: { ...replica, conflict: 'FINGERPRINT_AND_PROVENANCE_MISMATCH', observedFingerprint: 'sha256:foreign', observedProvenance: 'foreign:container-owner=untagged;replica-provenance=missing' } },
    { scenario: 'disposable-f02b-depot-conflict-restart.json', clientSession: { runId: 'missing-restart', reusedJvm: true }, replica }
  ] : undefined;
  return { schema: 1, kind: 'f02b-reference-container-native-semantic', status: 'passed', ...expected, worker, lane,
    jobId: 100 + index, runnerId: 200 + index, runnerName: `pm-f02b-${index}`, startedAtMillis: 1_700_000_000_000 + index,
    finishedAtMillis: 1_700_000_020_000 + index, gradlePid: 300 + index, launchTarget: 'forgeserverdev',
    requiredTest: `scenario:${lane}`, requiredTestCount: conflict ? 3 : lane === 'normal-zero-player-recovery' ? 2 : 1, runtimeContentSha256: hash, jarSha256: hash, primarySha256: hash, namespaces, launchTarget: 'normal-disposable-v3-server',
    jvmEnvelope: { javaToolOptions: '-Xmx3G', maxHeapMiB: 3072, concurrentMinecraftProcesses: 2 },
    gracefulSaveGate: `/tmp/f02b-graceful-save-${expected.runId}-${expected.runAttempt}`,
    terminal: { lane, domain, container: { status: 'ok', replica, custody }, replica, custody, ...(conflicts === undefined ? { histories } : { conflicts }) } };
}

test('F0.2B semantic aggregate requires four immutable native Minecraft lanes', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  const merged = mergeSemanticMatrix(complete, expected);
  assert.equal(merged.status, 'ok'); assert.equal(merged.overlapMillis, 19_997);
  assert.deepEqual(merged.lanes.map(value => value.lane), ['normal-never-visited', 'normal-visited-unloaded', 'normal-zero-player-recovery', 'conflict-restart']);
  const consumedInput = structuredClone(complete); consumedInput[0].terminal.histories[0].admission.initialInputs.depot.wheat = 0;
  assert.throws(() => mergeSemanticMatrix(consumedInput, expected), /seeded or lacks actual adapter observation/);
});

test('F0.2B primary receipts bind the retained runtime and complete normal-world scenario facts', () => {
  const semantic = evidence('worker-0');
  const primary = { schema: 1, kind: 'f02b-reference-container-native-semantic-primary', status: 'passed', worker: semantic.worker, lane: semantic.lane,
    runtimeContentSha256: semantic.runtimeContentSha256, jarSha256: semantic.jarSha256, gracefulSaveGate: semantic.gracefulSaveGate,
    identity: { qualificationId: semantic.qualificationId, repository: semantic.repository, headSha: semantic.headSha, workflowSha: semantic.workflowSha,
      workflowRef: semantic.workflowRef, runId: semantic.runId, runAttempt: semantic.runAttempt, jobId: semantic.jobId, runnerId: semantic.runnerId,
      runnerName: semantic.runnerName, launchTarget: semantic.launchTarget, requiredTest: semantic.requiredTest, requiredTestCount: semantic.requiredTestCount, jvmEnvelope: semantic.jvmEnvelope },
    runtime: { receipt: { worker: semantic.worker, runtimeContentSha256: semantic.runtimeContentSha256 }, preparedIdentity: { sourceContent: { sha256: hash }, preparedArtifact: { sha256: hash } } },
    manifests: [{ scenario: 'disposable-f02b-normal-never-visited.json', declarationSha256: hash, value: { status: 'ok', scenarioSha256: 'c'.repeat(64), scenarioDeclarationSha256: hash, recovery: { mode: 'graceful' }, diagnostics: [],
      gracefulSaveGate: [{ mode: 'serialized', directory: semantic.gracefulSaveGate }],
      } }], terminal: semantic.terminal };
  primary.manifests[0].sha256 = hashJson(primary.manifests[0].value);
  assert.equal(assertPrimaryEvidence(primary, semantic), primary);
  const drifted = structuredClone(primary); drifted.runtime.receipt.runtimeContentSha256 = 'c'.repeat(64);
  assert.throws(() => assertPrimaryEvidence(drifted, semantic), /lacks the consumed prepared runtime identity/);
  const flattened = structuredClone(primary); flattened.runtime.preparedIdentity.artifactSha256 = hash; delete flattened.runtime.preparedIdentity.preparedArtifact;
  assert.throws(() => assertPrimaryEvidence(flattened, semantic), /lacks the consumed prepared runtime identity/);
  const malformedPreparedArtifact = structuredClone(primary); malformedPreparedArtifact.runtime.preparedIdentity.preparedArtifact.sha256 = 'not-a-digest';
  assert.throws(() => assertPrimaryEvidence(malformedPreparedArtifact, semantic), /lacks the consumed prepared runtime identity/);
  const corrupt = structuredClone(primary); corrupt.manifests[0].value.status = 'foreign';
  assert.throws(() => assertPrimaryEvidence(corrupt, semantic), /corrupt scenario receipt/);
  const foreignDeclaration = structuredClone(primary); foreignDeclaration.manifests[0].value.scenarioDeclarationSha256 = 'd'.repeat(64);
  foreignDeclaration.manifests[0].sha256 = hashJson(foreignDeclaration.manifests[0].value);
  assert.throws(() => assertPrimaryEvidence(foreignDeclaration, semantic), /foreign scenario declaration/);
  primary.manifests[0].value.recovery = { beforeRestartManifest: '/private/pre-restart.json' };
  primary.manifests[0].sha256 = hashJson(primary.manifests[0].value);
  primary.manifests[0].beforeRestart = { status: 'ok', diagnostics: [] };
  primary.manifests[0].beforeRestartSha256 = hashJson(primary.manifests[0].beforeRestart);
  assert.equal(assertPrimaryEvidence(primary, semantic), primary);
  const missingRestart = structuredClone(primary); delete missingRestart.manifests[0].beforeRestart;
  assert.throws(() => assertPrimaryEvidence(missingRestart, semantic), /incomplete restart receipt/);
});

test('F0.2B primary receipts retain both sides of a graceful restart', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const runner = await readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-f02b-native-semantic.mjs'), 'utf8');
  assert.match(runner, /beforeRestartManifest/);
  assert.match(runner, /beforeRestartSha256/);
  assert.match(runner, /\[beforeRestart, manifest\]/);
});

test('F0.2B conflict recovery uses only the declared same-world persistent-client path', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const runner = await readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-f02b-native-semantic.mjs'), 'utf8');
  assert.match(runner, /const usePersistentClient = lane === 'conflict-restart';/);
  assert.match(runner, /FRONTIER_V3_PILOT_USE_PERSISTENT_CLIENT: usePersistentClient \? 'true' : 'false'/);
  assert.match(runner, /clientSession\?\.reusedJvm !== true/);
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  assert.doesNotThrow(() => mergeSemanticMatrix(complete, expected));
  complete[3].terminal.conflicts[1].clientSession.reusedJvm = false;
  assert.throws(() => mergeSemanticMatrix(complete, expected), /changed\/foreign\/missing evidence/);
});

test('F0.2B recovery validation derives the initial inputs from its retained ordinary chest observations', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const runner = await readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-f02b-native-semantic.mjs'), 'utf8');
  assert.match(runner, /const initialWheat = initialWheatItem\?\.count \?\? earlyWheat/);
  assert.match(runner, /const initialBiomass = initialBiomassItem\?\.count \?\? earlyBiomass/);
  assert.match(runner, /initialWheat !== earlyWheat \|\| initialBiomass !== earlyBiomass/);
  assert.match(runner, /const wheat = initialWheat;/);
  assert.match(runner, /const biomass = initialBiomass;/);
});

test('F0.2B reserves adjacent RCON ports outside every other worker game socket', () => {
  const spaces = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(worker => f02bNamespaces({ workspace: `/tmp/f02b/${worker}`, temp: `/tmp/f02b/${worker}`, runId: 44, runAttempt: 1, worker }));
  assert.deepEqual(spaces.map(value => value.port), [26200, 26202, 26204, 26206]);
  assert.equal(new Set(spaces.flatMap(value => [value.port, value.port + 1])).size, 8);
});

test('F0.2B consumers use a private checkout and prepare only a disposable world from immutable runtime bytes', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const [workflow, runner, isolated, scenarioRunner, build] = await Promise.all([
    readFile(resolve(project, '..', '.github/workflows/f02b-reference-container-semantic.yml'), 'utf8'),
    readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-f02b-native-semantic.mjs'), 'utf8'),
    readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs'), 'utf8'),
    readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-scenario.mjs'), 'utf8'),
    readFile(resolve(project, 'pale-mirror-neoforge/build.gradle'), 'utf8')
  ]);
  assert.match(workflow, /path: f02b-\$\{\{ github\.run_id \}\}-\$\{\{ matrix\.worker \}\}-workspace/);
  assert.match(workflow, /working-directory: f02b-\$\{\{ github\.run_id \}\}-\$\{\{ matrix\.worker \}\}-workspace\/pale-mirror/);
  assert.match(workflow, /path: f02b-\$\{\{ github\.run_id \}\}-merge-workspace/);
  assert.match(workflow, /path: f02b-\$\{\{ github\.run_id \}\}-\$\{\{ matrix\.worker \}\}-workspace\/pale-mirror\/build\/f02b-producer/);
  assert.match(workflow, /path: f02b-\$\{\{ github\.run_id \}\}-merge-workspace\/pale-mirror\/f02b-evidence/);
  assert.match(workflow, /--memory-per-worker-mib=6144/);
  assert.equal((workflow.match(/JAVA_TOOL_OPTIONS: -Xmx3G/g) ?? []).length, 2);
  assert.match(workflow, /F02B_GRACEFUL_SAVE_GATE: \$\{\{ inputs\.task_root \}\}\/f02b-graceful-save-\$\{\{ github\.run_id \}\}-\$\{\{ github\.run_attempt \}\}/);
  assert.match(runner, /FRONTIER_V3_PILOT_PREPARED_RUNTIME: 'true'/);
  assert.match(runner, /FRONTIER_V3_PILOT_GRACEFUL_SAVE_GATE: gracefulSaveGate/);
  assert.match(runner, /FRONTIER_V3_PILOT_INITIAL_CANONICAL_HOLD: 'true'/);
  assert.match(runner, /entry\?\.observed\?\.value/);
  assert.match(runner, /initialInputs/);
  assert.match(runner, /initialDepotAction/);
  assert.match(runner, /initialStoreAction/);
  assert.match(isolated, /withGracefulSaveGate/);
  assert.doesNotMatch(isolated, /acquireNativeExecutionGate/);
  assert.match(isolated, /initialCanonicalHold/);
  assert.match(await readFile(resolve(project, 'pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3ServerLifecycle.java'), 'utf8'), /INITIAL_CANONICAL_HOLDS\.containsKey\(server\)[\s\S]*?FrontierV3PhysicalExecutors\.registry\(\)\.tick/);
  assert.match(runner, /exact bounded JVM envelope/);
  assert.match(isolated, /-PfrontierV3PilotPreparedRuntime=true/);
  assert.match(scenarioRunner, /ensurePreparedLaunchWorkingDirectory/);
  assert.match(build, /frontierV3PilotPreparedRuntime != 'true'/);
});

test('F0.2B hive lane asserts the actual grown relay projection', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-hive-growth.json'), 'utf8'));
  assert.deepEqual(scenario.actions[3], {
    type: 'wait_until_block', position: { x: 430, y: 64, z: 432 }, block: 'minecraft:pink_concrete', timeoutMs: 30000
  });
  assert.equal(scenario.actions[5].type, 'assert_visible_board');
  assert.equal(scenario.actions[5].text, 'RELAY');
  assert.deepEqual(scenario.assertions[2], {
    after: 9, view: 'actor', id: 'bioform:east-grown-1',
    expect: { status: 'ok', actorKind: 'BIOFORM', role: 'RUNT/DEFEND', life: 'ALIVE' }
  });
});

test('F0.2B normal-history assertions bind their exact ordinary inspection action', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const names = [
    'disposable-f02b-normal-never-visited.json',
    'disposable-f02b-normal-visited-unloaded.json',
    'disposable-f02b-normal-zero-player.json',
    'disposable-f02b-normal-product-recovery.json'
  ];
  for (const name of names) {
    const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios', name), 'utf8'));
    for (const assertion of scenario.assertions) {
      const action = scenario.actions[assertion.after - 1];
      assert.ok(action, `${name} assertion after=${assertion.after} names no action`);
      assert.equal(action.type, 'inspect', `${name} assertion after=${assertion.after} is not an ordinary inspection`);
      assert.equal(action.view, assertion.view, `${name} assertion after=${assertion.after} names the wrong view`);
      assert.equal(action.id, assertion.id, `${name} assertion after=${assertion.after} names the wrong object`);
    }
  }
});

test('F0.2B zero-player history requires retained loaded observations outside both reference chunks', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  const missingHive = structuredClone(complete); missingHive[2].terminal.histories[0].admission.zeroPlayerScopes.pop();
  assert.throws(() => mergeSemanticMatrix(missingHive, expected), /zero-player history is not causal/);
  const sameChunk = structuredClone(complete); sameChunk[2].terminal.histories[0].admission.zeroPlayerScopes[0].playerChunk = { x: -22, z: -21 };
  assert.throws(() => mergeSemanticMatrix(sameChunk, expected), /zero-player history is not causal/);
});

test('F0.2B zero-player lane keeps live custody observer-free while its released COLD birth permit catches up only after return', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-normal-zero-player.json'), 'utf8'));
  const wait64 = scenario.actions.findIndex(action => action.type === 'wait_until_container_item' && action.count === 64);
  const leaveForCold = scenario.actions.findIndex(action => action.type === 'fast_forward_to_instant' && action.targetInstant === 14000);
  const finalReturn = scenario.actions.findIndex((action, index) => index > leaveForCold && action.type === 'visit'
    && action.dimension === 'pale_mirror:frontier_graybox');
  const wait63 = scenario.actions.findIndex(action => action.type === 'wait_until_container_item' && action.count === 63);
  assert.ok(wait64 >= 0 && leaveForCold > wait64 && finalReturn > leaveForCold && wait63 > finalReturn,
    'the live observer-free product proof precedes released COLD birth admission and its later physical catch-up');
  assert.equal(scenario.assertions.find(assertion => assertion.after === 28)?.expect.food.available, 64);
  assert.equal(scenario.assertions.find(assertion => assertion.after === 45)?.expect.food.available, 63);
});

test('F0.2B graceful product recovery retains both family admissions and their active reservation before restart', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-normal-product-recovery.json'), 'utf8'));
  const restart = scenario.restart.afterAction;
  const beforeRestart = scenario.actions.slice(0, restart);
  assert.equal(beforeRestart.filter(action => action.type === 'visit' && action.dimension === 'pale_mirror:frontier_graybox').length, 2);
  assert.equal(beforeRestart.filter(action => action.type === 'wait_until_diagnostic' && action.expect?.custody?.status === 'ACQUIRED').length, 2);
  assert.deepEqual(beforeRestart.at(-1), { type: 'inspect', view: 'reference_container', id: 'f02b' });
});

test('F0.2B foreign-container lane observes the ordinary break before placing foreign evidence', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-depot-foreign-restart.json'), 'utf8'));
  const position = { diagnostic: { view: 'container', id: 'container:1-depot', field: 'position' } };
  assert.deepEqual(scenario.actions.slice(1, 4), [
    { type: 'break', position },
    { type: 'wait_until_block', position, block: 'minecraft:air', timeoutMs: 30000 },
    { type: 'place', item: 'minecraft:chest', position, timeoutMs: 30000 }
  ]);
  assert.equal(scenario.restart.afterAction, 5);
  assert.deepEqual(scenario.assertions.map(assertion => assertion.after), [5, 6]);
});

test('F0.2B semantic aggregate fails closed for missing, stale, duplicate and non-native evidence', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  assert.throws(() => mergeSemanticMatrix(complete.slice(0, 3), expected), /missing or extra/);
  const stale = complete.map(value => ({ ...value })); stale[0].headSha = 'c'.repeat(40);
  assert.throws(() => mergeSemanticMatrix(stale, expected), /invalid immutable identity/);
  const duplicate = complete.map(value => structuredClone(value)); duplicate[3] = structuredClone(complete[2]);
  assert.throws(() => mergeSemanticMatrix(duplicate, expected), /duplicate workers/);
  const nonNative = complete.map(value => ({ ...value })); nonNative[1].launchTarget = 'node';
  assert.throws(() => mergeSemanticMatrix(nonNative, expected), /no completed native semantic assertion/);
  const semanticHole = complete.map(value => structuredClone(value)); semanticHole[0].terminal.histories[0].families.depot.outputBread = 63;
  assert.throws(() => mergeSemanticMatrix(semanticHole, expected), /non-equivalent product quantities/);
  const historyDrift = complete.map(value => structuredClone(value)); historyDrift[2].terminal.histories[0].families.hive.addedOrgans = 2;
  assert.throws(() => mergeSemanticMatrix(historyDrift, expected), /non-equivalent product quantities/);
  const terminalChestDrift = complete.map(value => structuredClone(value)); terminalChestDrift[2].terminal.histories[0].families.depot.terminalBread = 62;
  assert.throws(() => mergeSemanticMatrix(terminalChestDrift, expected), /terminal product drift/);
  const admissionDrift = complete.map(value => structuredClone(value)); admissionDrift[1].terminal.histories[0].admission.causal.orders[0].reservationActive = false;
  assert.throws(() => mergeSemanticMatrix(admissionDrift, expected), /seeded or lacks actual adapter observation/);
  const scheduleDrift = complete.map(value => structuredClone(value)); scheduleDrift[2].terminal.histories[0].admission.causal.schedules[0].dueAt = 228;
  assert.throws(() => mergeSemanticMatrix(scheduleDrift, expected), /terminal product drift/);
  const unsafeUnload = complete.map(value => structuredClone(value)); delete unsafeUnload[1].terminal.histories[0].admission.safeUnloadScopes['container:hive-east-store'];
  assert.throws(() => mergeSemanticMatrix(unsafeUnload, expected), /per-family release evidence/);
  const coldCatchup = complete.map(value => structuredClone(value)); coldCatchup[2].terminal.histories[0].admission.observerFreePhysicalEffects.hive = false;
  assert.throws(() => mergeSemanticMatrix(coldCatchup, expected), /zero-player history is not causal/);
  const fabricatedConflict = complete.map(value => structuredClone(value)); fabricatedConflict[3].terminal.conflicts[2].replica.observedProvenance = 'pale-mirror:reference-container:test';
  assert.throws(() => mergeSemanticMatrix(fabricatedConflict, expected), /changed\/foreign\/missing evidence/);
  const missingRuntime = complete.map(value => ({ ...value })); delete missingRuntime[0].runtimeContentSha256;
  assert.throws(() => mergeSemanticMatrix(missingRuntime, expected), /invalid immutable identity/);
  const mixedRuntime = complete.map(value => ({ ...value })); mixedRuntime[2].runtimeContentSha256 = 'c'.repeat(64);
  assert.throws(() => mergeSemanticMatrix(mixedRuntime, expected), /mixed prepared runtimes/);
  const unboundedJvm = complete.map(value => structuredClone(value)); delete unboundedJvm[0].jvmEnvelope;
  assert.throws(() => mergeSemanticMatrix(unboundedJvm, expected), /exact bounded JVM envelope/);
  const missingGate = complete.map(value => structuredClone(value)); delete missingGate[0].gracefulSaveGate;
  assert.throws(() => mergeSemanticMatrix(missingGate, expected), /graceful-save gate/);
});
