import assert from 'node:assert/strict';
import test from 'node:test';
import { assertGracefulRecoveryLifecycle, assertPrimaryEvidence, assertRecoveryCarrierManifest, assertRecoveryCausalMilestones, f02bNamespaces, hashJson, laneFor, mergeSemanticMatrix } from '../src/f02b-native-semantic.mjs';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { validateScenario } from '../src/scenario.mjs';
import { createHash } from 'node:crypto';
import { execFile as execute } from 'node:child_process';
import { promisify } from 'node:util';
import { bindPortableRuntimeArtifact, stagePortableRuntimeArtifact, verifyPortableRuntimeArtifact } from '../src/f02b-portable-runtime-artifact.mjs';

const sha = 'a'.repeat(40); const hash = 'b'.repeat(64);
const execFile = promisify(execute);
const expected = Object.freeze({ qualificationId: 'f02b-r1', repository: 'RidiculousCircumstances/pale_mirror', headSha: sha, workflowSha: sha,
  workflowRef: 'RidiculousCircumstances/pale_mirror/.github/workflows/f02b-reference-container-semantic.yml@refs/heads/main', runId: 44, runAttempt: 1 });

function gracefulLifecycle({ includeDemandRelease = false } = {}) {
  const barriers = [
    ['server_run_ready', { serverRunId: 'initial-server', serverPid: 111 }],
    ['client_normally_disconnected', { segment: 'before_restart' }],
    ...(includeDemandRelease ? [['normal_demand_loss_release', { serverRunId: 'initial-server' }]] : []),
    ['durable_server_save', { serverRunId: 'initial-server' }],
    ['game_port_closed', { port: 25575 }],
    ['recovery_server_ready', { serverRunId: 'recovery-server', serverPid: 222 }],
    ['action_checkpoint_acknowledged', { segment: 'after_restart', actionStep: 1 }]
  ];
  return barriers.map(([barrier, detail], index) => ({ sequence: index + 1, barrier, detail }));
}

function evidence(worker, index = Number(worker.at(-1))) {
  const lane = laneFor(worker); const namespaces = f02bNamespaces({ workspace: `/tmp/f02b/workspace-${worker}`, temp: `/tmp/f02b/${worker}`, runId: 44, runAttempt: 1, worker });
  const conflict = lane === 'conflict-restart';
  const replica = conflict
    ? { state: 'CONFLICT', revision: 3, canonicalRevision: 7, conflict: 'FINGERPRINT_MISMATCH', fingerprint: 'sha256:expected', provenance: 'pale-mirror:reference-container:test', observedFingerprint: 'sha256:missing-container:1-depot', observedProvenance: 'missing:container:1-depot' }
    : { state: 'OBSERVED_CURRENT', revision: 3, canonicalRevision: 7, conflict: '', fingerprint: 'sha256:current', provenance: 'pale-mirror:reference-container:test', observedFingerprint: '', observedProvenance: '' };
  const activeTasks = [
    { id: 'task:hive-frontier-hive_grow_organism-1', kind: 'GROW_HIVE_ORGANISM', status: 'PENDING' },
    { id: 'task:settlement-1-settlement_produce_bread-1', kind: 'PRODUCE_BREAD', status: 'ACTIVE' }
  ];
  const activeSchedules = [
    { id: 'schedule:hive-growth-task-start-task-hive-frontier-hive_grow_organism-1', subject: 'task:hive-frontier-hive_grow_organism-1', kind: 'frontier.hive.growth.task.start', dueAt: 3300, weight: 1 },
    { id: 'schedule:production-task-complete-production-1-1', subject: 'job:production-1-1', kind: 'frontier.settlement.production.task.complete', dueAt: 3220, weight: 1 }
  ];
  const activeOrders = [{ task: 'task:settlement-1-settlement_produce_bread-1', job: 'job:production-1-1', reservation: 'reservation:production-1-1', reservationActive: true, status: 'ACCEPTED' }];
  const durableReference = { kind: 'reference_container', id: 'f02b', instant: 3210,
    tasks: structuredClone(activeTasks), schedules: activeSchedules.map(value => value.id.includes('production') ? { ...value, dueAt: 3230 } : { ...value, dueAt: 3310 }), orders: structuredClone(activeOrders) };
  const durableDepot = { kind: 'container', id: 'container:1-depot', instant: 3210,
    custody: { status: 'CHECKPOINTED', epoch: 2 }, replica: { revision: 3, fingerprint: 'sha256:depot-active' } };
  const checkpoint = (phase, serverRunId, serverPid) => ({ schema: 1, phase, serverRunId, serverPid,
    reference: structuredClone(durableReference), depot: structuredClone(durableDepot) });
  const normal = (history) => ({ history, admission: { profile: 'world', initialIntents: 0, initialReplica: false, initialCustody: false, initialInstant: 227,
    initialInputs: { depot: { wheat: 64, bread: 0 }, hive: { biomass: 64 } },
    targetVisitsBeforeDue: history === 'never-visited' ? 0 : 2, observedEpochs: [1, 2], releasedEpochs: history === 'visited-unloaded' ? [1] : [],
    safeUnload: history === 'visited-unloaded' || history === 'zero-player', zeroPlayerLoaded: history === 'zero-player',
    safeUnloadScopes: history === 'visited-unloaded' || history === 'zero-player' ? { 'container:1-depot': true, 'container:hive-east-store': true } : {},
    zeroPlayerScopes: history === 'zero-player' ? [
      { id: 'container:1-depot', priorReleasedActionStep: 1, priorReleasedCustodyEpoch: 1, visitStep: 2, loadedActionStep: 3, acquiredActionStep: 4, effectActionStep: 5, releasedActionStep: 7,
        loadedMilestone: 'zero_player_depot_loaded_no_demand', acquiredMilestone: 'zero_player_depot_acquired_no_demand', effectMilestone: 'zero_player_depot_effect_no_demand', releasedMilestone: 'zero_player_depot_released',
        playerChunk: { x: -21, z: -21 }, scopeChunk: { x: -22, z: -21 }, loadedCustodyEpoch: 1, custodyEpoch: 2, effectCustodyEpoch: 2, releasedCustodyEpoch: 2,
        loadedReplicaRevision: 1, replicaRevision: 1, effectReplicaRevision: 3, releasedReplicaRevision: 3, replicaFingerprint: 'sha256:depot-active', naturalChunkLoaded: true, ordinaryPlayerNearby: false, presentationDemand: false, eligibleObserverCount: 0, presentationObserverCount: 0 },
      { id: 'container:hive-east-store', priorReleasedActionStep: 1, priorReleasedCustodyEpoch: 1, visitStep: 2, loadedActionStep: 3, acquiredActionStep: 4, effectActionStep: 6, releasedActionStep: 8,
        loadedMilestone: 'zero_player_hive_loaded_no_demand', acquiredMilestone: 'zero_player_hive_acquired_no_demand', effectMilestone: 'zero_player_hive_effect_no_demand', releasedMilestone: 'zero_player_hive_released',
        playerChunk: { x: 26, z: 26 }, scopeChunk: { x: 25, z: 26 }, loadedCustodyEpoch: 1, custodyEpoch: 2, effectCustodyEpoch: 2, releasedCustodyEpoch: 2,
        loadedReplicaRevision: 1, replicaRevision: 1, effectReplicaRevision: 3, releasedReplicaRevision: 3, replicaFingerprint: 'sha256:hive-active', naturalChunkLoaded: true, ordinaryPlayerNearby: false, presentationDemand: false, eligibleObserverCount: 0, presentationObserverCount: 0 }
    ] : [], observerFreePhysicalEffects: history === 'zero-player' ? {
      depot: { actionStep: 5, custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:depot-bread', milestone: 'zero_player_depot_effect_no_demand', ordinaryPlayerNearby: false, presentationDemand: false, eligibleObserverCount: 0, presentationObserverCount: 0 },
      hive: { actionStep: 6, custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:hive-growth', milestone: 'zero_player_hive_effect_no_demand', ordinaryPlayerNearby: false, presentationDemand: false, eligibleObserverCount: 0, presentationObserverCount: 0 }
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
    ...(history === 'graceful-product-recovery' ? { recovery: { mode: 'graceful', beforeEpoch: 1, afterEpoch: 2, splitAfterAction: 5,
      world: 'same-disposable-world', isolationWorld: 'same-disposable-world', lifecycle: gracefulLifecycle(),
      checkpoints: { durable: checkpoint('durable_stop', 'initial-server', 111), recovered: checkpoint('recovery_boot', 'recovery-server', 222) }, milestones: {
      activeAdmission: { phase: 'before_restart', kind: 'reference_container', id: 'f02b', instant: 3200, actionStep: 14,
        tasks: structuredClone(activeTasks), taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: structuredClone(activeSchedules), orders: structuredClone(activeOrders) },
      activeDepotCustody: { phase: 'before_restart', kind: 'container', id: 'container:1-depot', instant: 3200,
        actionStep: 15, custodyStatus: 'CHECKPOINTED', custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:depot-active',
        chunk: 'LOADED', ordinaryPlayerNearby: false, presentationDemand: false, eligibleObserverCount: 0, presentationObserverCount: 0 },
      durableInflight: { phase: 'durable_stop', kind: 'reference_container', id: 'f02b', instant: 3210,
        tasks: structuredClone(durableReference.tasks), taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: structuredClone(durableReference.schedules), orders: structuredClone(activeOrders) },
      durableDepotCustody: { phase: 'durable_stop', kind: 'container', id: 'container:1-depot', instant: 3210,
        custodyStatus: 'CHECKPOINTED', custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:depot-active' },
      recoveredInflight: { phase: 'recovery_boot', kind: 'reference_container', id: 'f02b', instant: 3210,
        tasks: structuredClone(durableReference.tasks), taskKinds: ['GROW_HIVE_ORGANISM', 'PRODUCE_BREAD'], schedules: structuredClone(durableReference.schedules), orders: structuredClone(activeOrders) },
      recoveredDepotCustody: { phase: 'recovery_boot', kind: 'container', id: 'container:1-depot', instant: 3210,
        custodyStatus: 'CHECKPOINTED', custodyEpoch: 2, replicaRevision: 3, replicaFingerprint: 'sha256:depot-active' },
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
  assert.match(workflow, /name: f02b-\$\{\{ github\.run_id \}\}-producer-runtime[\s\S]*?f02b-\$\{\{ github\.run_id \}\}-producer-workspace\/pale-mirror\/build\/f02b-producer\/runtime\.json[\s\S]*?f02b-\$\{\{ github\.run_id \}\}-producer-workspace\/pale-mirror\/build\/f02b-producer\/portable-runtime/);
  assert.match(workflow, /name: f02b-\$\{\{ github\.run_id \}\}-producer-metadata[\s\S]*?\$\{\{ runner\.temp \}\}\/f02b-artifacts\/producer/);
  assert.match(workflow, /actions\/download-artifact@v4[\s\S]*?name: f02b-\$\{\{ github\.run_id \}\}-producer-runtime[\s\S]*?path: f02b-\$\{\{ github\.run_id \}\}-\$\{\{ matrix\.worker \}\}-workspace\/pale-mirror\/build\/f02b-producer/);
  assert.match(workflow, /f02b-portable-runtime-artifact\.mjs --mode=stage/);
  assert.match(workflow, /f02b-portable-runtime-artifact\.mjs --mode=bind/);
  assert.match(workflow, /--runtime="\$PWD\/build\/f02b-producer\/runtime\.consumer\.json"/);
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

test('F0.2B action-defined producer artifact extracts its runtime manifest and full immutable closure into a fresh worker', async (context) => {
  const root = await mkdtemp('/home/rd/proj/pm-f02b-portable-runtime-test-');
  context.after(() => rm(root, { recursive: true, force: true }));
  const actionWork = join(root, 'runner', '_work'); const producer = join(actionWork, 'pale_mirror', 'pale_mirror', 'f02b-34429834971-producer-workspace', 'pale-mirror', 'build', 'f02b-producer');
  const worker = join(root, 'fresh-worker'); const store = join(root, 'store');
  const entryBytes = Buffer.from('immutable-launch-member');
  const entrySha256 = createHash('sha256').update(entryBytes).digest('hex');
  const manifestCore = { schema: 1, kind: 'frontier-v3-f0vc-prepared-runtime', producerProject: producer,
    source: { sha256: 'b'.repeat(64) }, portablePreparedIdentity: { fixture: true }, environment: [],
    inputs: Array.from({ length: 8 }, (_, index) => ({ id: createHash('sha256').update(`entry-${index}`).digest('hex'), sha256: entrySha256,
      source: `${producer}/source-${index}`, target: `runtime-${index}`, text: false, role: 'fixture' })) };
  const actualContent = createHash('sha256').update(JSON.stringify(manifestCore)).digest('hex');
  const manifest = { ...manifestCore, contentSha256: actualContent, entriesRoot: 'entries' };
  const stored = join(store, 'prepared', actualContent); await mkdir(join(stored, 'entries'), { recursive: true });
  for (const entry of manifest.inputs) await writeFile(join(stored, 'entries', entry.id), entryBytes);
  await writeFile(join(stored, 'manifest.json'), JSON.stringify(manifest));
  await mkdir(producer, { recursive: true });
  const runtime = join(producer, 'runtime.json'); await writeFile(runtime, JSON.stringify({ manifest: join(stored, 'manifest.json'), contentSha256: actualContent }));
  const portable = join(producer, 'portable-runtime'); await stagePortableRuntimeArtifact({ runtime, output: portable });

  // This is the real failed v68 action layout: upload-artifact received the
  // producer directory and RUNNER_TEMP evidence, so its archive LCA was the
  // runner work root and download-artifact preserved that nested checkout path.
  const metadata = join(actionWork, '_temp', 'f02b-artifacts', 'producer'); await mkdir(metadata, { recursive: true });
  await writeFile(join(metadata, 'capacity.json'), '{}');
  const oldArchive = join(root, 'v68-mixed-producer.zip');
  await execFile('zip', ['-qr', oldArchive,
    'pale_mirror/pale_mirror/f02b-34429834971-producer-workspace/pale-mirror/build/f02b-producer',
    '_temp/f02b-artifacts/producer'], { cwd: actionWork });
  const oldDownloaded = join(worker, 'old-download'); await mkdir(oldDownloaded, { recursive: true });
  await execFile('unzip', ['-q', oldArchive, '-d', oldDownloaded]);
  const oldExpectedRoot = join(oldDownloaded, 'build', 'f02b-producer');
  await assert.rejects(bindPortableRuntimeArtifact({ runtime: join(oldExpectedRoot, 'runtime.json'), artifact: join(oldExpectedRoot, 'portable-runtime'), output: join(worker, 'old.json') }), /unreadable|ENOENT/,
    'mixed producer/TEMP action inputs must reject the observed nested v68 extraction shape');
  assert.equal((await readFile(join(oldDownloaded, 'pale_mirror', 'pale_mirror', 'f02b-34429834971-producer-workspace', 'pale-mirror', 'build', 'f02b-producer', 'runtime.json'), 'utf8')).length > 0, true,
    'the regression must retain the observed action-defined nested producer root');

  // The corrected action uploads only the two runtime inputs.  Their exact LCA
  // is f02b-producer, so download-artifact extracts runtime.json and the
  // portable closure directly into the worker's declared destination.
  const archive = join(root, 'runtime-only-producer.zip');
  await execFile('zip', ['-qr', archive, 'runtime.json', 'portable-runtime'], { cwd: producer });
  const downloaded = join(worker, 'build', 'f02b-producer'); await mkdir(downloaded, { recursive: true });
  await execFile('unzip', ['-q', archive, '-d', downloaded]);
  const downloadedRuntime = join(downloaded, 'runtime.json');
  const bound = join(worker, 'runtime.consumer.json'); await bindPortableRuntimeArtifact({ runtime: downloadedRuntime, artifact: join(downloaded, 'portable-runtime'), output: bound });
  await verifyPortableRuntimeArtifact({ runtime: downloadedRuntime, artifact: join(downloaded, 'portable-runtime') });
  const value = JSON.parse(await readFile(bound, 'utf8'));
  assert.ok(value.manifest.startsWith('build/f02b-producer/portable-runtime/'), 'worker must bind to its downloaded artifact, never the producer store');
});

test('F0.2B producer establishes isolated Gradle state and preserves a clean prepared identity', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const workflow = await readFile(
    resolve(project, '..', '.github/workflows/f02b-reference-container-semantic.yml'),
    'utf8',
  );
  const producer = workflow.slice(
    workflow.indexOf('  producer:\n'),
    workflow.indexOf('  semantic:\n'),
  );

  assertF02bProducerEnvelope(producer);
  assert.throws(
    () => assertF02bProducerEnvelope(producer.replace('Initialize isolated producer Gradle home', 'skip Gradle initialization')),
    /isolated producer Gradle home/,
  );
  assert.throws(
    () => assertF02bProducerEnvelope(producer.replace(
      '--output="$RUNNER_TEMP/f02b-artifacts/producer/capacity.json"',
      '--output="$PWD/f02b-artifacts/producer/capacity.json"',
    )),
    /outside the checkout/,
  );
  assert.throws(
    () => assertF02bProducerEnvelope(producer.replace(
      'if (identity.sourceDirty !== false) { throw new Error("F0.2B prepared source identity is dirty"); }',
      '',
    )),
    /clean prepared source identity/,
  );
  assert.throws(
    () => assertF02bProducerEnvelope(producer.replace(
      'Bootstrap isolated producer Gradle home online',
      'skip Gradle bootstrap',
    )),
    /bootstrap its private Gradle home online/,
  );
  assert.throws(
    () => assertF02bProducerEnvelope(producer.replace(
      'prepareFrontierV3PilotNativeEnvironment --no-daemon',
      'prepareFrontierV3PilotNativeEnvironment --offline --no-daemon',
    )),
    /bootstrap must resolve online/,
  );
});

function assertF02bProducerEnvelope(producer) {
  const gradleInitializer = producer.indexOf('Initialize isolated producer Gradle home');
  const capacity = producer.indexOf('--output="$RUNNER_TEMP/f02b-artifacts/producer/capacity.json"');
  const bootstrap = producer.indexOf('Bootstrap isolated producer Gradle home online');
  const preparation = producer.indexOf('prepare-native-build.mjs');
  const cleanIdentity = producer.indexOf('identity.sourceDirty !== false');
  const stage = producer.indexOf('f0vc-prepared-runtime.mjs');

  assert.ok(gradleInitializer >= 0, 'producer must initialize an isolated producer Gradle home');
  assert.match(producer, /test -n "\$\{RUNNER_TEMP:-\}"/);
  assert.match(producer, /test -n "\$\{GITHUB_ENV:-\}"/);
  assert.match(producer, /f02b_producer_gradle_home="\$RUNNER_TEMP\/f02b-\$\{\{ github\.run_id \}\}-producer-gradle"/);
  assert.match(producer, /mkdir -p "\$f02b_producer_gradle_home"/);
  assert.match(producer, /printf 'GRADLE_USER_HOME=%s\\n' "\$f02b_producer_gradle_home" >> "\$GITHUB_ENV"/);
  assert.ok(capacity >= 0, 'capacity evidence must be written outside the checkout');
  assert.doesNotMatch(producer, /--output="\$PWD\/f02b-artifacts\/producer\/capacity\.json"/);
  assert.ok(bootstrap >= 0, 'producer must bootstrap its private Gradle home online');
  assert.ok(
    /Bootstrap isolated producer Gradle home online[\s\S]*?\.\/gradlew :pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment --no-daemon/.test(producer),
    'bootstrap must resolve online',
  );
  assert.ok(preparation >= 0, 'producer must prepare its native build identity');
  assert.ok(cleanIdentity >= 0, 'producer must require a clean prepared source identity');
  assert.ok(stage >= 0, 'producer must stage its prepared runtime');
  assert.ok(
    gradleInitializer < capacity && capacity < bootstrap && bootstrap < preparation && preparation < cleanIdentity && cleanIdentity < stage,
    'Gradle initialization, external evidence and online bootstrap must precede clean offline preparation and staging',
  );
  assert.match(producer, /\$\{\{ runner\.temp \}\}\/f02b-artifacts\/producer/);
}

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

test('F0.2B zero-player history rejects missing demand-zero, wrong-family and reordered natural-streaming facts', () => {
  const complete = ['worker-0', 'worker-1', 'worker-2', 'worker-3'].map(evidence);
  const missingHive = structuredClone(complete); missingHive[2].terminal.histories[0].admission.zeroPlayerScopes.pop();
  assert.throws(() => mergeSemanticMatrix(missingHive, expected), /zero-player history is not causal/);
  const sameChunk = structuredClone(complete); sameChunk[2].terminal.histories[0].admission.zeroPlayerScopes[0].playerChunk = { x: -22, z: -21 };
  assert.throws(() => mergeSemanticMatrix(sameChunk, expected), /zero-player history is not causal/);
  const demand = structuredClone(complete); demand[2].terminal.histories[0].admission.zeroPlayerScopes[1].presentationDemand = true;
  assert.throws(() => mergeSemanticMatrix(demand, expected), /zero-player history is not causal/);
  const observer = structuredClone(complete); observer[2].terminal.histories[0].admission.zeroPlayerScopes[0].presentationObserverCount = 1;
  assert.throws(() => mergeSemanticMatrix(observer, expected), /zero-player history is not causal/);
  const wrongFamily = structuredClone(complete); wrongFamily[2].terminal.histories[0].admission.zeroPlayerScopes[0].effectMilestone = 'zero_player_hive_effect_no_demand';
  assert.throws(() => mergeSemanticMatrix(wrongFamily, expected), /zero-player history is not causal/);
  const reordered = structuredClone(complete); reordered[2].terminal.histories[0].admission.zeroPlayerScopes[0].acquiredActionStep = 2;
  assert.throws(() => mergeSemanticMatrix(reordered, expected), /zero-player history is not causal/);
  const staleRelease = structuredClone(complete); staleRelease[2].terminal.histories[0].admission.zeroPlayerScopes[0].priorReleasedActionStep = 3;
  assert.throws(() => mergeSemanticMatrix(staleRelease, expected), /zero-player history is not causal/);
  const forced = structuredClone(complete); forced[2].terminal.histories[0].admission.zeroPlayerScopes[0].naturalChunkLoaded = false;
  assert.throws(() => mergeSemanticMatrix(forced, expected), /zero-player history is not causal/);
});

test('F0.2B no-demand carriers are named ordinary visits, never fixture or force-load commands', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const zero = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-normal-zero-player.json'), 'utf8'));
  const recovery = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-normal-product-recovery.json'), 'utf8'));
  for (const scenario of [zero, recovery]) {
    assert.doesNotThrow(() => validateScenario(scenario));
    assert.equal(scenario.actions.some(action => action.type === 'command' || /force/i.test(action.type)), false,
      `${scenario.id} relies only on ordinary pilot actions`);
  }
  const named = zero.actions.filter(action => action.type === 'wait_until_diagnostic' && action.causalMilestone?.includes('_no_demand'));
  assert.deepEqual(named.map(action => action.causalMilestone), [
    'zero_player_depot_loaded_no_demand', 'zero_player_depot_acquired_no_demand',
    'zero_player_hive_loaded_no_demand', 'zero_player_hive_acquired_no_demand'
  ]);
  for (const action of named) {
    assert.deepEqual(action.expect.physicalSocket, { chunk: 'LOADED', ordinaryPlayerNearby: false, presentationDemand: false,
      eligibleObserverCount: 0, presentationObserverCount: 0 });
  }
  const recoveryCarrier = recovery.actions.filter(action => action.causalMilestone?.includes('_no_demand'));
  assert.deepEqual(recoveryCarrier.map(action => action.causalMilestone), ['recovery_depot_loaded_no_demand', 'recovery_depot_checkpointed_no_demand']);

  const scopes = Object.freeze({
    'container:1-depot': { x: -343, z: -326 },
    'container:hive-east-store': { x: 412, z: 420 }
  });
  // The observer-free depot proof is still a real production run: its one worker starts on
  // the retained residential apron and crosses the Workshop's west service port before the
  // exact depot write.  A carrier that only reaches the chest can acquire custody yet leave
  // the physical worker frozen in an unticking column, which would turn a geometry accident
  // into a false product failure.  These immutable bootstrap coordinates keep the smallest
  // ordinary view (8) wide enough for that exact bounded production envelope.
  const depotProductionEnvelope = Object.freeze({
    residentHome: { x: -362, z: -370 },
    workshopService: { x: -385, z: -326 }
  });
  const verifyCarrierGeometry = (scenario, milestones) => {
    assert.equal(scenario.server.viewDistance, 8, `${scenario.id} retains only the exact seven-chunk natural-streaming carrier`);
    for (const milestone of milestones) {
      const actionIndex = scenario.actions.findIndex(action => action.causalMilestone === milestone);
      assert.ok(actionIndex >= 0, `${scenario.id} retains ${milestone}`);
      const action = scenario.actions[actionIndex];
      const visit = scenario.actions.slice(0, actionIndex).findLast(candidate => candidate.type === 'visit'
        && candidate.dimension === 'pale_mirror:frontier_graybox');
      const scope = scopes[action.id];
      assert.ok(visit && scope, `${milestone} has an ordinary graybox visit and known physical scope`);
      assert.ok(Math.hypot(visit.position.x - scope.x, visit.position.z - scope.z) > 96,
        `${milestone} remains outside the presentation envelope`);
      const chunkDistance = Math.max(Math.abs(Math.floor(visit.position.x / 16) - Math.floor(scope.x / 16)),
        Math.abs(Math.floor(visit.position.z / 16) - Math.floor(scope.z / 16)));
      assert.equal(chunkDistance, 7, `${milestone} retains the bounded natural chunk distance`);
      assert.equal(scenario.server.viewDistance, chunkDistance + 1,
        `${milestone} keeps exactly the smallest inclusive natural-streaming carrier`);
      assert.deepEqual(action.expect.physicalSocket, { chunk: 'LOADED', ordinaryPlayerNearby: false, presentationDemand: false,
        eligibleObserverCount: 0, presentationObserverCount: 0 }, `${milestone} binds natural loading to zero eligible demand`);
    }
  };
  verifyCarrierGeometry(zero, ['zero_player_depot_loaded_no_demand', 'zero_player_depot_acquired_no_demand',
    'zero_player_hive_loaded_no_demand', 'zero_player_hive_acquired_no_demand']);
  verifyCarrierGeometry(recovery, ['recovery_depot_loaded_no_demand', 'recovery_depot_checkpointed_no_demand']);
  for (const milestone of ['zero_player_depot_loaded_no_demand', 'zero_player_depot_acquired_no_demand']) {
    const actionIndex = zero.actions.findIndex(action => action.causalMilestone === milestone);
    const visit = zero.actions.slice(0, actionIndex).findLast(candidate => candidate.type === 'visit'
      && candidate.dimension === 'pale_mirror:frontier_graybox');
    for (const [name, position] of Object.entries(depotProductionEnvelope)) {
      const distance = Math.max(Math.abs(Math.floor(visit.position.x / 16) - Math.floor(position.x / 16)),
        Math.abs(Math.floor(visit.position.z / 16) - Math.floor(position.z / 16)));
      assert.ok(distance <= zero.server.viewDistance,
        `${milestone} keeps the exact production ${name} column naturally ticking`);
    }
  }
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
  const observerFreeLoad = scenario.actions.findIndex(action => action.causalMilestone === 'zero_player_depot_loaded_no_demand');
  const observerFreeAcquire = scenario.actions.findIndex(action => action.causalMilestone === 'zero_player_depot_acquired_no_demand');
  assert.ok(wait64 >= 0 && leaveForCold > wait64 && finalReturn > leaveForCold && birthConsumed > finalReturn && confirmed > birthConsumed,
    'the live observer-free product proof precedes released COLD birth admission and its later physical catch-up');
  assert.ok(observerFreeLoad >= 0 && observerFreeAcquire === observerFreeLoad + 1
    && scenario.actions[observerFreeLoad].expect.custody.status === 'ACQUIRED'
    && scenario.actions[observerFreeAcquire].expect.custody.status === 'ACQUIRED',
  'the prior released/unloaded scope is naturally loaded and then retained under the exact observer-free custody epoch');
  assert.equal(scenario.assertions.find(assertion => assertion.after === 30)?.expect.food.available, 64);
  assert.ok(bridgeToCold >= 0 && bridgeToCold + 2 === leaveForCold,
    'the released COLD interval crosses the native bounded absolute-advance envelope without changing the due target');
  assert.equal(scenario.assertions.find(assertion => assertion.after === 50)?.expect.status, 'ok');
  assert.equal(scenario.actions[leaveForCold + 4]?.causalMilestone, 'zero_player_cold_birth_admitted',
    'the released COLD permit is retained before the ordinary return rather than inferred from a later endpoint');
  assert.equal(scenario.actions[birthConsumed]?.causalMilestone, 'zero_player_birth_consumed',
    'the exact same permit is read after ordinary return and physical consumption');
});

test('F0.2B graceful product recovery retains one exact observer-free production admission before restart', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const scenario = JSON.parse(await readFile(resolve(project, 'tools/frontier-v3-test-pilot/scenarios/disposable-f02b-normal-product-recovery.json'), 'utf8'));
  const restart = scenario.restart.afterAction;
  const beforeRestart = scenario.actions.slice(0, restart);
  assert.equal(beforeRestart.filter(action => action.type === 'visit' && action.dimension === 'pale_mirror:frontier_graybox').length, 2,
    'the second ordinary visit is the neutral-distance natural-load observation, not a fixture placement');
  assert.equal(beforeRestart.filter(action => action.type === 'wait_until_diagnostic' && action.expect?.custody?.status === 'CHECKPOINTED').length, 2,
    'the recovery carrier retains the stable checkpointed production phase without acquiring the non-subject hive');
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
    'the retained restart fence precedes production completion while the ordinary neutral-distance view has live custody of its exact production subject');
  const milestone = name => scenario.actions.findIndex(action => action.causalMilestone === name);
  const afterReacquire = milestone('recovery_after_reacquire');
  const resumedInflight = milestone('recovery_after_resume_inflight');
  const resumedCustody = milestone('recovery_after_resume_depot_custody');
  const terminalProduct = milestone('recovery_terminal_product');
  const liveBread = scenario.actions.findIndex((action, index) => index > resumedCustody && action.type === 'wait_until_container_item'
    && action.containerId === 'container:1-depot' && action.item === 'minecraft:bread' && action.count === 64);
  const coldHive = scenario.assertions.find(assertion => assertion.view === 'hive' && assertion.id === 'hive:frontier');
  assert.ok(liveBread > resumedCustody && liveBread < afterReacquire,
    'the recovered live custodian reaches the exact 64-bread product boundary before later hive continuation');
  assert.ok(resumedInflight === restart && resumedCustody === restart + 1 && afterReacquire > resumedCustody && terminalProduct > afterReacquire,
  'the post-client reads remain distinct from the server-owned recovered checkpoint and precede terminal completion');
  assert.equal(coldHive?.after, scenario.actions.length,
    'the separate hive terminal fact remains an ordinary product assertion, not an inferred recovery boundary');
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
  const reorderedActiveDue = structuredClone(milestones); reorderedActiveDue.activeAdmission.schedules[0].dueAt = 3210;
  assert.throws(() => assertRecoveryCausalMilestones(reorderedActiveDue), /exact durable/,
    'the earlier active observation must still retain valid production-before-hive due ordering');
  const releasedBeforeSave = structuredClone(milestones); releasedBeforeSave.activeDepotCustody.custodyStatus = 'RELEASED';
  assert.throws(() => assertRecoveryCausalMilestones(releasedBeforeSave), /active-custody/,
    'the declared stable checkpointed boundary cannot be replaced by a later release');
  const replacedOperation = structuredClone(milestones); replacedOperation.recoveredInflight.tasks[1].id = 'task:settlement-1-replaced';
  assert.throws(() => assertRecoveryCausalMilestones(replacedOperation), /recovered durable/);
  const lostReservation = structuredClone(milestones); lostReservation.recoveredInflight.orders[0].reservationActive = false;
  assert.throws(() => assertRecoveryCausalMilestones(lostReservation), /recovered durable/);
  const reorderedDue = structuredClone(milestones); reorderedDue.recoveredInflight.schedules[1].dueAt += 1;
  assert.throws(() => assertRecoveryCausalMilestones(reorderedDue), /recovered durable/);
  const staleCustody = structuredClone(milestones); staleCustody.recoveredDepotCustody.replicaRevision += 1;
  assert.throws(() => assertRecoveryCausalMilestones(staleCustody), /recovered durable/);
  const staleEpoch = structuredClone(milestones); staleEpoch.recoveredDepotCustody.custodyEpoch += 1;
  assert.throws(() => assertRecoveryCausalMilestones(staleEpoch), /recovered durable/,
    'the recovered custody epoch must be the persisted checkpoint chain, not a replacement lease');
  const fabricatedCustody = structuredClone(milestones); fabricatedCustody.recoveredDepotCustody.custodyStatus = 'ACQUIRED';
  assert.throws(() => assertRecoveryCausalMilestones(fabricatedCustody), /recovered durable/);
  const shiftedInstantAndDue = structuredClone(milestones);
  shiftedInstantAndDue.recoveredInflight.instant += 10;
  shiftedInstantAndDue.recoveredInflight.schedules[1].dueAt += 10;
  shiftedInstantAndDue.recoveredDepotCustody.instant += 10;
  assert.throws(() => assertRecoveryCausalMilestones(shiftedInstantAndDue), /recovered durable/,
    'equal remaining time cannot substitute for the exact persisted canonical instant and due');
  const wrongSubject = structuredClone(milestones); wrongSubject.terminalProduct.tasks[1].id = 'task:settlement-1-replaced';
  assert.throws(() => assertRecoveryCausalMilestones(wrongSubject), /exact terminal operation/);
  const duplicateTerminal = structuredClone(milestones); duplicateTerminal.terminalProduct.tasks.push({ ...duplicateTerminal.terminalProduct.tasks[1] });
  assert.throws(() => assertRecoveryCausalMilestones(duplicateTerminal), /exact terminal operation/);
});

test('F0.2B recovery post-child consumer requires the local durable-save, same-world and first-read chain', () => {
  const recovery = evidence('worker-2').terminal.histories.find(value => value.history === 'graceful-product-recovery').recovery;
  assert.doesNotThrow(() => assertGracefulRecoveryLifecycle(recovery));
  const missing = structuredClone(recovery); delete missing.lifecycle;
  assert.throws(() => assertGracefulRecoveryLifecycle(missing), /same-world lifecycle/);
  const staleDurable = structuredClone(recovery); staleDurable.lifecycle[2].detail.serverRunId = 'foreign-server';
  assert.throws(() => assertGracefulRecoveryLifecycle(staleDurable), /durable_server_save/);
  const wrongWorld = structuredClone(recovery); wrongWorld.isolationWorld = 'replacement-world';
  assert.throws(() => assertGracefulRecoveryLifecycle(wrongWorld), /same-world lifecycle/);
  const reordered = structuredClone(recovery); reordered.checkpoints.recovered.serverRunId = 'initial-server';
  assert.throws(() => assertGracefulRecoveryLifecycle(reordered), /stale, foreign, or reordered/);
  const swappedPid = structuredClone(recovery); swappedPid.checkpoints.recovered.serverPid = 111;
  assert.throws(() => assertGracefulRecoveryLifecycle(swappedPid), /stale, foreign, or reordered/,
    'a valid positive PID from the initial JVM cannot satisfy the recovery JVM identity');
  const demandLoss = structuredClone(recovery); demandLoss.lifecycle = gracefulLifecycle({ includeDemandRelease: true });
  assert.throws(() => assertGracefulRecoveryLifecycle(demandLoss), /demand-loss release/);
});

test('F0.2B direct recovery carrier cannot report the retained raw escape as semantic green', async () => {
  const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
  const directRunner = await readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs'), 'utf8');
  const matrixRunner = await readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-f02b-native-semantic.mjs'), 'utf8');
  assert.match(directRunner, /assertRecoveryCarrierManifest\(manifest, beforeRestart\)/,
    'the raw local child consumes the same semantic gate before it writes status=ok');
  assert.match(matrixRunner, /assertRecoveryCarrierManifest\(value, beforeRestart\)/,
    'the final matrix post-child consumer uses that same gate');

  const recovery = structuredClone(evidence('worker-2').terminal.histories.find(value => value.history === 'graceful-product-recovery').recovery);
  const reference = (fact, milestone) => ({ kind: 'reference_container', id: 'f02b', instant: fact.instant,
    pilotCausalMilestone: milestone, pilotActionStep: fact.actionStep, tasks: fact.tasks, schedules: fact.schedules, orders: fact.orders });
  const container = (fact, milestone) => ({ kind: 'container', id: 'container:1-depot', instant: fact.instant,
    pilotCausalMilestone: milestone, pilotActionStep: fact.actionStep,
    custody: { status: fact.custodyStatus, epoch: fact.custodyEpoch }, replica: { revision: fact.replicaRevision, fingerprint: fact.replicaFingerprint },
    physicalSocket: { chunk: fact.chunk, ordinaryPlayerNearby: fact.ordinaryPlayerNearby, presentationDemand: fact.presentationDemand,
      eligibleObserverCount: fact.eligibleObserverCount, presentationObserverCount: fact.presentationObserverCount } });
  const before = { status: 'ok', diagnostics: [
    { value: reference(recovery.milestones.activeAdmission, 'recovery_active_admission') },
    { value: container(recovery.milestones.activeDepotCustody, 'recovery_active_depot_custody') }
  ] };
  const manifest = { status: 'ok', isolation: { world: recovery.world }, recovery: { mode: 'graceful', world: recovery.world,
    checkpoints: recovery.checkpoints }, lifecycle: recovery.lifecycle, diagnostics: [
    { value: reference(recovery.milestones.afterReacquire, 'recovery_after_reacquire') },
    { value: reference(recovery.milestones.terminalProduct, 'recovery_terminal_product') }
  ] };
  assert.doesNotThrow(() => assertRecoveryCarrierManifest(manifest, before));

  // 600064 had only a later post-client, re-acquired view.  Deleting the two
  // server-owned snapshots models that raw shape; it must now fail before any
  // local or final path can declare a semantic success.
  const retainedEscape = structuredClone(manifest); delete retainedEscape.recovery.checkpoints;
  assert.throws(() => assertRecoveryCarrierManifest(retainedEscape, before), /missing causal milestone/);
  const wrongOperation = structuredClone(manifest); wrongOperation.recovery.checkpoints.recovered.reference.tasks[1].id = 'task:replacement';
  assert.throws(() => assertRecoveryCarrierManifest(wrongOperation, before), /recovered durable/);
  const wrongDue = structuredClone(manifest); wrongDue.recovery.checkpoints.recovered.reference.schedules[1].dueAt += 1;
  assert.throws(() => assertRecoveryCarrierManifest(wrongDue, before), /recovered durable/);
  const equalRemainingShift = structuredClone(manifest);
  equalRemainingShift.recovery.checkpoints.recovered.reference.instant += 10;
  equalRemainingShift.recovery.checkpoints.recovered.reference.schedules[1].dueAt += 10;
  equalRemainingShift.recovery.checkpoints.recovered.depot.instant += 10;
  assert.throws(() => assertRecoveryCarrierManifest(equalRemainingShift, before), /recovered durable/);
  const wrongFence = structuredClone(manifest); wrongFence.recovery.checkpoints.recovered.depot.custody.epoch += 1;
  assert.throws(() => assertRecoveryCarrierManifest(wrongFence, before), /recovered durable/);
  const foreignPid = structuredClone(manifest); foreignPid.recovery.checkpoints.recovered.serverPid = 111;
  assert.throws(() => assertRecoveryCarrierManifest(foreignPid, before), /stale, foreign, or reordered/);
  const wrongWorld = structuredClone(manifest); wrongWorld.isolation.world = 'foreign-world';
  assert.throws(() => assertRecoveryCarrierManifest(wrongWorld, before), /same-world lifecycle/);
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
