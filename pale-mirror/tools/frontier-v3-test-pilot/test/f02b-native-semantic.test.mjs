import assert from 'node:assert/strict';
import test from 'node:test';
import { assertPrimaryEvidence, assertRecoveryCausalMilestones, f02bNamespaces, hashJson, laneFor, mergeSemanticMatrix } from '../src/f02b-native-semantic.mjs';
import { readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateScenario } from '../src/scenario.mjs';

const sha = 'a'.repeat(40); const hash = 'b'.repeat(64);
const expected = Object.freeze({ qualificationId: 'f02b-r1', repository: 'RidiculousCircumstances/pale_mirror', headSha: sha, workflowSha: sha,
  workflowRef: 'RidiculousCircumstances/pale_mirror/.github/workflows/f02b-reference-container-semantic.yml@refs/heads/main', runId: 44, runAttempt: 1 });

function evidence(worker, index = Number(worker.at(-1))) {
  const lane = laneFor(worker); const namespaces = f02bNamespaces({ workspace: `/tmp/f02b/workspace-${worker}`, temp: `/tmp/f02b/${worker}`, runId: 44, runAttempt: 1, worker });
  const conflict = lane === 'conflict-restart';
  const replica = conflict
    ? { state: 'CONFLICT', revision: 3, canonicalRevision: 7, conflict: 'FINGERPRINT_MISMATCH', fingerprint: 'sha256:expected', provenance: 'pale-mirror:reference-container:test', observedFingerprint: 'sha256:missing-container:1-depot', observedProvenance: 'missing:container:1-depot' }
    : { state: 'OBSERVED_CURRENT', revision: 3, canonicalRevision: 7, conflict: '', fingerprint: 'sha256:current', provenance: 'pale-mirror:reference-container:test', observedFingerprint: '', observedProvenance: '' };
  const activeTasks = [
    { id: 'task:hive-frontier-hive_grow_organism-1', kind: 'GROW_HIVE_ORGANISM', status: 'ACTIVE' },
    { id: 'task:settlement-1-settlement_produce_bread-1', kind: 'PRODUCE_BREAD', status: 'ACTIVE' }
  ];
  const activeSchedules = [
    { id: 'schedule:hive-growth-task-complete-hive-growth-1', subject: 'job:hive-growth-1', kind: 'frontier.hive.growth.task.complete', dueAt: 3500, weight: 1 },
    { id: 'schedule:production-task-complete-production-1-1', subject: 'job:production-1-1', kind: 'frontier.settlement.production.task.complete', dueAt: 3320, weight: 1 }
  ];
  const activeOrders = [{ task: 'task:settlement-1-settlement_produce_bread-1', job: 'job:production-1-1', reservation: 'reservation:production-1-1', reservationActive: true, status: 'ACCEPTED' }];
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
    ...(history === 'zero-player' ? { birthCatchup: {
      cold: { actionStep: 38, instant: 30000, job: { id: 'job:resident-birth-1-3', food: 'item:production-1-1-bread', intent: 'intent:resident-birth-food-1-3', resident: 'resident:1-born-3' },
        intent: { id: 'intent:resident-birth-food-1-3', cause: 'job:resident-birth-1-3', kind: 'EXACT_ITEM_CONSUMPTION', status: 'PREPARED' } },
      returnAction: 39,
      afterReturn: { actionStep: 47, instant: 30020, job: { id: 'job:resident-birth-1-3', food: 'item:production-1-1-bread', intent: 'intent:resident-birth-food-1-3', resident: 'resident:1-born-3' },
        intent: { id: 'intent:resident-birth-food-1-3', cause: 'job:resident-birth-1-3', kind: 'EXACT_ITEM_CONSUMPTION', status: 'CONFIRMED' } }
    } } : {}),
    causal: { observations: 1, taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: [
      { id: 'schedule:hive-growth', subject: 'job:hive-east-growth', kind: 'frontier.hive.growth.complete', dueAt: 227, weight: 1 },
      { id: 'schedule:produce-bread', subject: 'job:settlement-1-bread', kind: 'frontier.settlement.production.complete', dueAt: 227, weight: 1 }
    ], orders: [{ task: 'task:settlement-1-bread', job: 'job:settlement-1-bread', reservation: 'reservation:bread-input', reservationActive: true, status: 'ACCEPTED' }], productionStarted: true, growthStarted: true,
      physicalIntentKinds: ['EXACT_ITEM_CONSUMPTION:CONFIRMED', 'PRODUCTION_TRANSFORMATION:CONFIRMED'], admissionAction: 3 } },
    families: { depot: { inputWheat: 64, outputBread: 64, terminalBread: 63, foodAvailable: 63, foodFulfilled: 0 },
      hive: { inputBiomass: 64, outputBiomass: 0, growthJobs: 0, addedOrgans: 1, spawnedBioforms: 1 } },
    ...(history === 'graceful-product-recovery' ? { recovery: { mode: 'graceful', beforeEpoch: 1, afterEpoch: 2, splitAfterAction: 5, milestones: {
      activeAdmission: { phase: 'before_restart', kind: 'reference_container', id: 'f02b', instant: 3200, actionStep: 14,
        tasks: structuredClone(activeTasks), taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: structuredClone(activeSchedules), orders: structuredClone(activeOrders) },
      activeDepotCustody: { phase: 'before_restart', kind: 'container', id: 'container:1-depot', instant: 3200,
        actionStep: 15, custodyStatus: 'ACQUIRED', custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:depot-active' },
      hydratedInflight: { phase: 'after_restart', kind: 'reference_container', id: 'f02b', instant: 3200, actionStep: 17,
        tasks: structuredClone(activeTasks), taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: structuredClone(activeSchedules), orders: structuredClone(activeOrders) },
      hydratedDepotCustody: { phase: 'after_restart', kind: 'container', id: 'container:1-depot', instant: 3200,
        actionStep: 18, custodyStatus: 'ACQUIRED', custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:depot-active' },
      afterReacquire: { phase: 'after_restart', kind: 'reference_container', id: 'f02b', instant: 12500,
        tasks: [{ ...activeTasks[0] }, { ...activeTasks[1], status: 'COMPLETED' }], taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: [],
        orders: [{ ...activeOrders[0], reservationActive: false, status: 'FULFILLED' }] },
      liveHivePending: { phase: 'after_restart', kind: 'hive', id: 'hive:frontier', instant: 12510, growthJobs: 1, addedOrgans: 0, spawnedBioforms: 0 },
      depotReleased: { phase: 'after_restart', kind: 'container', id: 'container:1-depot', instant: 12520, custodyStatus: 'RELEASED', chunk: 'UNLOADED' },
      hiveReleased: { phase: 'after_restart', kind: 'container', id: 'container:hive-east-store', instant: 12521, custodyStatus: 'RELEASED', chunk: 'UNLOADED' },
      coldEffectConfirmed: { phase: 'after_restart', kind: 'hive', id: 'hive:frontier', instant: 14000, growthJobs: 0, addedOrgans: 1, spawnedBioforms: 1 },
      terminalProduct: { phase: 'after_restart', kind: 'reference_container', id: 'f02b', instant: 14000, actionStep: 35,
        tasks: activeTasks.map(task => ({ ...task, status: 'COMPLETED' })), taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: [],
        orders: [{ ...activeOrders[0], reservationActive: false, status: 'FULFILLED' }] }
    } } } : {}) });
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
  assert.match(runner, /\[\[beforeRestart, 'before_restart'\], \[manifest, 'after_restart'\]\]/);
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

test('F0.2B zero-player evidence does not turn retained replica history into a fixed birth endpoint', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  complete[2].terminal.histories[0].families.depot.foodAvailable = 26;
  complete[2].terminal.histories[0].families.depot.foodFulfilled = 37;
  assert.doesNotThrow(() => mergeSemanticMatrix(complete, expected),
    'the same fenced COLD permit can be followed by ordinary additional population work');
});

test('F0.2B zero-player lane keeps live custody observer-free while its released COLD birth permit catches up only after return', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-normal-zero-player.json'), 'utf8'));
  const wait64 = scenario.actions.findIndex(action => action.type === 'wait_until_container_item' && action.count === 64);
  const bridgeToCold = scenario.actions.findIndex(action => action.type === 'fast_forward_to_instant' && action.targetInstant === 27000);
  const leaveForCold = scenario.actions.findIndex(action => action.type === 'fast_forward_to_instant' && action.targetInstant === 30000);
  const finalReturn = scenario.actions.findIndex((action, index) => index > leaveForCold && action.type === 'visit'
    && action.dimension === 'pale_mirror:frontier_graybox');
  const birthConsumed = scenario.actions.findIndex(action => action.causalMilestone === 'zero_player_birth_consumed');
  const confirmed = scenario.actions.findIndex((action, index) => index > birthConsumed && action.type === 'wait_until_diagnostic'
    && action.expect?.replica?.state === 'OBSERVED_CURRENT' && action.expect?.custody?.status === 'ACQUIRED');
  const observerFreeLoad = scenario.actions.findIndex((action, index) => index > 0 && action.type === 'wait_until_diagnostic'
    && action.id === 'container:1-depot' && action.expect?.custody?.status === 'RELEASED'
    && action.expect?.physicalSocket?.chunk === 'LOADED' && action.expect?.physicalSocket?.ordinaryPlayerNearby === false);
  const observerFreeAcquire = scenario.actions.findIndex((action, index) => index > observerFreeLoad && action.type === 'wait_until_diagnostic'
    && action.id === 'container:1-depot' && action.expect?.custody?.status === 'ACQUIRED'
    && action.expect?.physicalSocket?.ordinaryPlayerNearby === false);
  assert.ok(wait64 >= 0 && leaveForCold > wait64 && finalReturn > leaveForCold && birthConsumed > finalReturn && confirmed > birthConsumed,
    'the live observer-free product proof precedes released COLD birth admission and its later physical catch-up');
  assert.ok(observerFreeLoad >= 0 && observerFreeAcquire === observerFreeLoad + 1,
    'ordinary natural loading is observed before, and remains distinct from, its later zero-player custody acquire');
  assert.equal(scenario.assertions.find(assertion => assertion.after === 29)?.expect.food.available, 64);
  assert.ok(bridgeToCold >= 0 && bridgeToCold + 2 === leaveForCold,
    'the released COLD interval crosses the native bounded absolute-advance envelope without changing the due target');
  assert.equal(scenario.assertions.find(assertion => assertion.after === 50)?.expect.status, 'ok');
  assert.equal(scenario.actions[leaveForCold + 4]?.causalMilestone, 'zero_player_cold_birth_admitted',
    'the released COLD permit is retained before the ordinary return rather than inferred from a later endpoint');
  assert.equal(scenario.actions[birthConsumed]?.causalMilestone, 'zero_player_birth_consumed',
    'the exact same permit is read after ordinary return and physical consumption');
});

test('F0.2B graceful product recovery retains both family admissions and their active reservation before restart', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-normal-product-recovery.json'), 'utf8'));
  const restart = scenario.restart.afterAction;
  const beforeRestart = scenario.actions.slice(0, restart);
  assert.equal(beforeRestart.filter(action => action.type === 'visit' && action.dimension === 'pale_mirror:frontier_graybox').length, 3,
    'the third ordinary visit is the neutral-distance natural-load observation, not a fixture placement');
  assert.equal(beforeRestart.filter(action => action.type === 'wait_until_diagnostic' && action.expect?.custody?.status === 'ACQUIRED').length, 4,
    'both initial admissions are re-observed under the ordinary neutral-distance live-custody view');
  const activeAdmission = beforeRestart.findLastIndex(action => action.type === 'inspect'
    && action.view === 'reference_container' && action.id === 'f02b');
  assert.equal(beforeRestart[activeAdmission]?.causalMilestone, 'recovery_active_admission');
  assert.equal(beforeRestart[activeAdmission + 1]?.causalMilestone, 'recovery_active_depot_custody');
  assert.equal(beforeRestart[activeAdmission + 2]?.type, 'release_fast_forward_hold');
  assert.equal(restart, activeAdmission + 3,
    'the graceful boundary follows exact read-only operation and custody observations and releases only its artificial test hold before Minecraft saves');
  assert.equal(scenario.actions[activeAdmission - 1]?.type, 'fast_forward_to_instant',
    'the retained admission observation follows its engine-owned due advance');
  assert.equal(scenario.actions[activeAdmission - 1]?.targetInstant, 3200,
    'the retained restart fence precedes production completion while the ordinary neutral-distance view has live custody of both families');
  const milestone = name => scenario.actions.findIndex(action => action.causalMilestone === name);
  const afterReacquire = milestone('recovery_after_reacquire');
  const pendingHive = milestone('recovery_live_hive_pending');
  const depotReleased = milestone('recovery_depot_released');
  const hiveReleased = milestone('recovery_hive_released');
  const coldEffect = milestone('recovery_cold_effect_confirmed');
  const hydratedInflight = milestone('recovery_hydrated_inflight');
  const hydratedCustody = milestone('recovery_hydrated_depot_custody');
  const terminalProduct = milestone('recovery_terminal_product');
  const liveBread = scenario.actions.findIndex((action, index) => index > hydratedCustody && action.type === 'wait_until_container_item'
    && action.containerId === 'container:1-depot' && action.item === 'minecraft:bread' && action.count === 64);
  const coldHive = scenario.assertions.find(assertion => assertion.view === 'hive' && assertion.id === 'hive:frontier');
  assert.ok(liveBread > hydratedCustody && liveBread < afterReacquire,
    'the recovered live custodian reaches the exact 64-bread product boundary before later hive continuation');
  assert.ok(hydratedInflight === restart && hydratedCustody === restart + 1 && afterReacquire > hydratedCustody
    && pendingHive > afterReacquire && depotReleased > pendingHive && hiveReleased > pendingHive && coldEffect > hiveReleased && terminalProduct > coldEffect,
  'the first recovered reads hydrate the exact in-flight operation and custody before continuation, then retain release and terminal product order');
  assert.equal(scenario.actions[coldEffect]?.type, 'wait_until_diagnostic',
    'released COLD completion is a domain fact, not a fabricated absolute-time request');
  assert.equal(coldHive?.after, scenario.actions.length,
    'the hive terminal effect is asserted only after its retained released-COLD fact');
});

test('F0.2B recovery causal oracle fails closed for missing, completed, stale, reordered, replaced and duplicate facts', () => {
  const milestones = evidence('worker-2').terminal.histories.find(value => value.history === 'graceful-product-recovery').recovery.milestones;
  assert.doesNotThrow(() => assertRecoveryCausalMilestones(milestones));
  const missing = structuredClone(milestones); delete missing.activeAdmission;
  assert.throws(() => assertRecoveryCausalMilestones(missing), /missing causal milestone/);
  const stale = structuredClone(milestones); stale.afterReacquire.instant = 3199;
  assert.throws(() => assertRecoveryCausalMilestones(stale), /stale or wrong-subject/);
  const completedBeforeSave = structuredClone(milestones); completedBeforeSave.activeAdmission.tasks[1].status = 'COMPLETED';
  assert.throws(() => assertRecoveryCausalMilestones(completedBeforeSave), /active-admission/);
  const replacedOperation = structuredClone(milestones); replacedOperation.hydratedInflight.tasks[0].id = 'task:hive-frontier-replaced';
  assert.throws(() => assertRecoveryCausalMilestones(replacedOperation), /same-operation hydrated/);
  const lostReservation = structuredClone(milestones); lostReservation.hydratedInflight.orders[0].reservationActive = false;
  assert.throws(() => assertRecoveryCausalMilestones(lostReservation), /same-operation hydrated/);
  const staleCustody = structuredClone(milestones); staleCustody.hydratedDepotCustody.replicaRevision += 1;
  assert.throws(() => assertRecoveryCausalMilestones(staleCustody), /same-operation hydrated/);
  const reordered = structuredClone(milestones); reordered.hiveReleased.instant = 12509;
  assert.throws(() => assertRecoveryCausalMilestones(reordered), /reordered or wrong-subject/);
  const wrongSubject = structuredClone(milestones); wrongSubject.coldEffectConfirmed.id = 'hive:other';
  assert.throws(() => assertRecoveryCausalMilestones(wrongSubject), /confirmed released-COLD/);
  const duplicateTerminal = structuredClone(milestones); duplicateTerminal.terminalProduct.tasks.push({ ...duplicateTerminal.terminalProduct.tasks[0] });
  assert.throws(() => assertRecoveryCausalMilestones(duplicateTerminal), /exact terminal operation/);
});

test('F0.2B recovery semantic milestones remain stable when an unrelated evidence action is inserted', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-normal-product-recovery.json'), 'utf8'));
  const before = scenario.actions.filter(action => action.causalMilestone).map(action => action.causalMilestone);
  const inserted = structuredClone(scenario);
  inserted.actions.splice(12, 0, { type: 'inspect', view: 'summary', id: '' });
  validateScenario(inserted);
  assert.deepEqual(inserted.actions.filter(action => action.causalMilestone).map(action => action.causalMilestone), before,
    'a transport-only action insertion cannot rebind the retained causal facts to another semantic milestone');
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
  const admissionDrift = complete.map(value => structuredClone(value)); admissionDrift[1].terminal.histories[0].admission.causal.orders[0].reservationActive = false;
  assert.throws(() => mergeSemanticMatrix(admissionDrift, expected), /seeded or lacks actual adapter observation/);
  const scheduleDrift = complete.map(value => structuredClone(value)); scheduleDrift[2].terminal.histories[0].admission.causal.schedules[0].dueAt = 228;
  assert.throws(() => mergeSemanticMatrix(scheduleDrift, expected), /terminal product drift/);
  const unsafeUnload = complete.map(value => structuredClone(value)); delete unsafeUnload[1].terminal.histories[0].admission.safeUnloadScopes['container:hive-east-store'];
  assert.throws(() => mergeSemanticMatrix(unsafeUnload, expected), /per-family release evidence/);
  const coldCatchup = complete.map(value => structuredClone(value)); coldCatchup[2].terminal.histories[0].admission.observerFreePhysicalEffects.hive = false;
  assert.throws(() => mergeSemanticMatrix(coldCatchup, expected), /zero-player history is not causal/);
  const missingBirthPermit = complete.map(value => structuredClone(value)); delete missingBirthPermit[2].terminal.histories[0].admission.birthCatchup.cold;
  assert.throws(() => mergeSemanticMatrix(missingBirthPermit, expected), /released-COLD birth permit/);
  const replacedBirthPermit = complete.map(value => structuredClone(value)); replacedBirthPermit[2].terminal.histories[0].admission.birthCatchup.afterReturn.job.id = 'job:resident-birth-1-replaced';
  assert.throws(() => mergeSemanticMatrix(replacedBirthPermit, expected), /released-COLD birth permit/);
  const unconfirmedBirthPermit = complete.map(value => structuredClone(value)); unconfirmedBirthPermit[2].terminal.histories[0].admission.birthCatchup.afterReturn.intent.status = 'PREPARED';
  assert.throws(() => mergeSemanticMatrix(unconfirmedBirthPermit, expected), /released-COLD birth permit/);
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
