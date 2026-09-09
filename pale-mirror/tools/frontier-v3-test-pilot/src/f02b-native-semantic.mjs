import { F0VB_XVFB_PORT_BASE, declaredNamespaces, workerNames } from './f0vb-qualification.mjs';
import { createHash } from 'node:crypto';

export const F02B_SCHEMA = 1;
export const F02B_KIND = 'f02b-reference-container-native-semantic';
export const F02B_PRIMARY_KIND = 'f02b-reference-container-native-semantic-primary';
// A normal disposable Minecraft server also owns RCON at gamePort + 1.  The
// F0.VB listener-only port cadence is consecutive, so it cannot be reused for
// this matrix without one worker's RCON colliding with its neighbour's game
// socket.
export const F02B_PRIVATE_PORT_BASE = 26200;
const SHA = /^[0-9a-f]{40}$/;
const HASH = /^[0-9a-f]{64}$/;
const QUALIFICATION = /^[A-Za-z0-9._-]{1,120}$/;
const LANES = Object.freeze({
  'worker-0': 'normal-never-visited',
  'worker-1': 'normal-visited-unloaded',
  'worker-2': 'normal-zero-player-recovery',
  'worker-3': 'conflict-restart'
});

export function laneFor(worker) {
  if (!(worker in LANES)) throw new Error('F0.2B semantic matrix has an unknown worker');
  return LANES[worker];
}

export function f02bNamespaces(identity) {
  const base = declaredNamespaces(identity);
  const index = Number(identity.worker.slice('worker-'.length));
  const port = F02B_PRIVATE_PORT_BASE + index * 2;
  return Object.freeze({ ...base, port, display: `:${port - F0VB_XVFB_PORT_BASE}` });
}

export function assertSemanticEvidence(value, expected = {}) {
  if (!value || typeof value !== 'object' || value.schema !== F02B_SCHEMA || value.kind !== F02B_KIND) throw new Error('F0.2B evidence has unknown schema or kind');
  if (laneFor(value.worker) !== value.lane || !QUALIFICATION.test(value.qualificationId ?? '')) throw new Error('F0.2B evidence has an invalid matrix assignment');
  if (!SHA.test(value.headSha ?? '') || value.headSha !== value.workflowSha || !HASH.test(value.runtimeContentSha256 ?? '') || !HASH.test(value.jarSha256 ?? '') || !HASH.test(value.primarySha256 ?? '')
    || !String(value.workflowRef ?? '').includes('.github/workflows/f02b-reference-container-semantic.yml@')) throw new Error('F0.2B evidence has an invalid immutable identity');
  for (const key of ['runId', 'runAttempt', 'jobId', 'runnerId', 'startedAtMillis', 'finishedAtMillis', 'gradlePid']) {
    if (!Number.isSafeInteger(value[key]) || value[key] <= 0) throw new Error(`F0.2B evidence has invalid ${key}`);
  }
  if (value.startedAtMillis >= value.finishedAtMillis || value.finishedAtMillis - value.startedAtMillis > 20 * 60 * 60_000) throw new Error('F0.2B evidence has an unbounded launch interval');
  if (value.launchTarget !== 'normal-disposable-v3-server' || !String(value.requiredTest ?? '').startsWith('scenario:')
    || !Number.isInteger(value.requiredTestCount) || value.requiredTestCount < 1 || value.status !== 'passed') throw new Error('F0.2B evidence has no completed native semantic assertion');
  if (!sameJson(value.jvmEnvelope, { javaToolOptions: '-Xmx3G', maxHeapMiB: 3072, concurrentMinecraftProcesses: 2 })) {
    throw new Error('F0.2B evidence has no exact bounded JVM envelope');
  }
  if (typeof value.gracefulSaveGate !== 'string' || !value.gracefulSaveGate.endsWith(`f02b-graceful-save-${value.runId}-${value.runAttempt}`)) {
    throw new Error('F0.2B evidence has no exact graceful-save gate');
  }
  const expectedScenarioCount = value.lane === 'conflict-restart' ? 3 : value.lane === 'normal-zero-player-recovery' ? 2 : 1;
  if (value.requiredTestCount !== expectedScenarioCount) throw new Error('F0.2B evidence has incomplete lane coverage');
  if (!value.terminal || value.terminal.lane !== value.lane || !value.terminal.domain || !value.terminal.container || value.terminal.container.status !== 'ok'
    || !value.terminal.replica || !value.terminal.custody) throw new Error('F0.2B evidence has no terminal domain/replica/custody facts');
  assertTerminalFacts(value.lane, value.terminal);
  if (!value.namespaces || typeof value.namespaces !== 'object') throw new Error('F0.2B evidence has no isolated namespaces');
  const expectedNamespaces = f02bNamespaces({ workspace: value.namespaces.workspace, temp: value.namespaces.temp.replace(/\/f0vb-[^/]+\/temp$/, ''),
    runId: value.runId, runAttempt: value.runAttempt, worker: value.worker });
  for (const field of ['workspace', 'temp', 'gradle', 'cache', 'world', 'process']) if (value.namespaces[field] !== expectedNamespaces[field]) throw new Error(`F0.2B evidence has invalid ${field} namespace`);
  if (value.namespaces.port !== expectedNamespaces.port || value.namespaces.display !== expectedNamespaces.display) throw new Error('F0.2B evidence has an unsafe native port namespace');
  for (const [key, valueExpected] of Object.entries(expected)) if (valueExpected !== undefined && value[key] !== valueExpected) throw new Error(`F0.2B evidence is foreign or stale for ${key}`);
  return value;
}

/**
 * A worker bundle keeps the complete normal-world receipts separate from the
 * compact aggregate record.  The aggregate names this exact immutable primary
 * document; it cannot recover terminal facts from a scenario name alone.
 */
export function assertPrimaryEvidence(primary, semantic) {
  if (!primary || typeof primary !== 'object' || primary.schema !== F02B_SCHEMA || primary.kind !== F02B_PRIMARY_KIND || primary.status !== 'passed') {
    throw new Error('F0.2B primary evidence has unknown schema or kind');
  }
  if (!semantic || primary.worker !== semantic.worker || primary.lane !== semantic.lane || primary.runtimeContentSha256 !== semantic.runtimeContentSha256
      || primary.jarSha256 !== semantic.jarSha256 || primary.gracefulSaveGate !== semantic.gracefulSaveGate
      || !sameJson(primary.identity, immutableIdentity(semantic)) || !sameJson(primary.terminal, semantic.terminal)) {
    throw new Error('F0.2B primary evidence is foreign to its semantic receipt');
  }
  // This is the F0.VC consumer identity, not the older flattened verification
  // projection.  Retain its actual source and prepared-artifact digests so a
  // primary receipt cannot name a valid runtime while eliding what it launched.
  if (!primary.runtime?.receipt || primary.runtime.receipt.worker !== semantic.worker || primary.runtime.receipt.runtimeContentSha256 !== semantic.runtimeContentSha256
      || !HASH.test(primary.runtime.preparedIdentity?.sourceContent?.sha256 ?? '')
      || !HASH.test(primary.runtime.preparedIdentity?.preparedArtifact?.sha256 ?? '')) {
    throw new Error('F0.2B primary evidence lacks the consumed prepared runtime identity');
  }
  if (!Array.isArray(primary.manifests) || primary.manifests.length !== semantic.requiredTestCount) throw new Error('F0.2B primary evidence has incomplete scenario receipts');
  for (const receipt of primary.manifests) {
    if (!receipt || typeof receipt.scenario !== 'string' || !HASH.test(receipt.sha256 ?? '') || !receipt.value
        || hashJson(receipt.value) !== receipt.sha256) throw new Error('F0.2B primary evidence has corrupt scenario receipt');
    if (!HASH.test(receipt.declarationSha256 ?? '') || receipt.declarationSha256 !== receipt.value.scenarioDeclarationSha256) {
      throw new Error('F0.2B primary evidence has a foreign scenario declaration');
    }
    if (!Array.isArray(receipt.value.gracefulSaveGate) || receipt.value.gracefulSaveGate.length === 0
        || receipt.value.gracefulSaveGate.some(value => value?.mode !== 'serialized' || value.directory !== semantic.gracefulSaveGate)) {
      throw new Error('F0.2B primary evidence has no retained serialized graceful-save receipt');
    }
    const declaredBeforeRestart = receipt.value?.recovery?.beforeRestartManifest;
    if (declaredBeforeRestart) {
      if (!receipt.beforeRestart || !HASH.test(receipt.beforeRestartSha256 ?? '')
          || hashJson(receipt.beforeRestart) !== receipt.beforeRestartSha256 || receipt.beforeRestart.status !== 'ok') {
        throw new Error('F0.2B primary evidence has incomplete restart receipt');
      }
    } else if ('beforeRestart' in receipt || 'beforeRestartSha256' in receipt) {
      throw new Error('F0.2B primary evidence has an undeclared restart receipt');
    }
    if (receipt.scenario === 'disposable-f02b-normal-product-recovery.json') {
      assertRequiredSaveOwnerObservation(receipt.value, primary, receipt);
    }
  }
  return primary;
}

/** The ordered discriminator's primary receipt must retain the child admission and every late slot. */
export function assertRequiredSaveOwnerObservation(value, primary, receipt) {
  const requirement = value?.ownerObservationRequirement;
  const observation = value?.ownerObservation;
  if (requirement?.status !== 'accepted' || !HASH.test(requirement.contract?.sha256 ?? '')
      || requirement.identity?.worker !== primary.worker || requirement.identity?.lane !== primary.lane
      || requirement.identity?.runId !== primary.identity.runId || requirement.identity?.runAttempt !== primary.identity.runAttempt
      || requirement.identity?.scenario !== receipt.scenario || requirement.identity?.scenarioDeclarationSha256 !== receipt.declarationSha256
      || !Array.isArray(requirement.scheduledSlotOffsetsMs) || !sameJson(requirement.scheduledSlotOffsetsMs, [0, 20_000, 60_000, 80_000])
      || observation?.status !== 'completed' || !Array.isArray(observation.slots)
      || observation.slots.length !== requirement.scheduledSlotOffsetsMs.length
      || observation.slots.some((slot, index) => slot?.index !== index || slot.offsetMs !== requirement.scheduledSlotOffsetsMs[index]
        || (slot.status !== 'captured' && slot.status !== 'unavailable') || typeof slot.receipt !== 'string' || slot.receipt.length === 0
        || !HASH.test(slot.sha256 ?? '') || (slot.status === 'unavailable' && !slot.reason?.kind))) {
    throw new Error('F0.2B primary evidence lacks required save-owner observation or scheduled-slot receipts');
  }
  return value;
}

export function mergeSemanticMatrix(evidence, expected) {
  if (!Array.isArray(evidence) || evidence.length !== workerNames().length) throw new Error('F0.2B merge rejects missing or extra worker evidence');
  const checked = evidence.map(value => assertSemanticEvidence(value, expected));
  if (new Set(checked.map(value => value.worker)).size !== workerNames().length) throw new Error('F0.2B merge rejects duplicate workers');
  for (const field of ['jobId', 'runnerId', 'runnerName']) if (new Set(checked.map(value => value[field])).size !== checked.length) throw new Error(`F0.2B merge rejects duplicate ${field}`);
  if (new Set(checked.map(value => value.runtimeContentSha256)).size !== 1) throw new Error('F0.2B merge rejects mixed prepared runtimes');
  for (const field of ['workspace', 'temp', 'gradle', 'cache', 'world', 'process', 'display', 'port']) if (new Set(checked.map(value => value.namespaces[field])).size !== checked.length) throw new Error('F0.2B merge rejects shared native namespace');
  const latestStart = Math.max(...checked.map(value => value.startedAtMillis));
  const earliestFinish = Math.min(...checked.map(value => value.finishedAtMillis));
  if (latestStart >= earliestFinish) throw new Error('F0.2B merge rejects non-overlapping Minecraft launches');
  assertHistoryComparator(checked);
  return { schema: F02B_SCHEMA, kind: 'f02b-reference-container-native-semantic-merge', status: 'ok', ...expected,
    workers: checked.map(value => value.worker).sort(), overlapMillis: earliestFinish - latestStart,
    runtimeContentSha256: checked[0].runtimeContentSha256,
    lanes: checked.map(({ worker, lane, jobId, runnerId, runnerName, runtimeContentSha256, jarSha256, primarySha256, namespaces, terminal }) => ({ worker, lane, jobId, runnerId, runnerName, runtimeContentSha256, jarSha256, primarySha256, namespaces, terminal })).sort((a, b) => a.worker.localeCompare(b.worker)) };
}

export function hashJson(value) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }

/** Extract the named, read-only recovery facts from both retained restart halves. */
export function recoveryMilestones(diagnostics) {
  const byName = new Map();
  for (const entry of diagnostics) {
    const value = entry?.value;
    const name = value?.pilotCausalMilestone;
    if (name) byName.set(name, { phase: entry.phase, value });
  }
  const reference = name => {
    const entry = byName.get(name); const value = entry?.value;
    return entry && { phase: entry.phase, kind: value.kind, id: value.id, instant: value.instant,
      actionStep: value.pilotActionStep,
      tasks: (value.tasks ?? []).map(task => ({ id: task.id, kind: task.kind, status: task.status })).sort(compareJson),
      taskKinds: [...new Set((value.tasks ?? []).map(task => task.kind))].sort(),
      schedules: (value.schedules ?? []).map(schedule => ({ id: schedule.id, subject: schedule.subject, kind: schedule.kind, dueAt: schedule.dueAt, weight: schedule.weight })).sort(compareJson),
      orders: (value.orders ?? []).map(order => ({ task: order.task, job: order.job, reservation: order.reservation, reservationActive: order.reservationActive, status: order.status })).sort(compareJson) };
  };
  const custody = name => {
    const entry = byName.get(name); const value = entry?.value;
    return entry && { phase: entry.phase, kind: value.kind, id: value.id, instant: value.instant,
      actionStep: value.pilotActionStep, custodyStatus: value.custody?.status, custodyEpoch: value.custody?.epoch,
      replicaRevision: value.replica?.revision, replicaFingerprint: value.replica?.fingerprint };
  };
  return { activeAdmission: reference('recovery_active_admission'), afterReacquire: reference('recovery_after_reacquire'),
    activeDepotCustody: custody('recovery_active_depot_custody'), hydratedInflight: reference('recovery_hydrated_inflight'),
    hydratedDepotCustody: custody('recovery_hydrated_depot_custody'), terminalProduct: reference('recovery_terminal_product') };
}

function immutableIdentity(value) {
  return { qualificationId: value.qualificationId, repository: value.repository, headSha: value.headSha, workflowSha: value.workflowSha,
    workflowRef: value.workflowRef, runId: value.runId, runAttempt: value.runAttempt, jobId: value.jobId, runnerId: value.runnerId,
    runnerName: value.runnerName, launchTarget: value.launchTarget, requiredTest: value.requiredTest, requiredTestCount: value.requiredTestCount,
    jvmEnvelope: value.jvmEnvelope };
}

function sameJson(left, right) { return JSON.stringify(left) === JSON.stringify(right); }
function compareJson(left, right) { return JSON.stringify(left).localeCompare(JSON.stringify(right)); }

function assertTerminalFacts(lane, terminal) {
  const replica = terminal.replica; const custody = terminal.custody;
  if (!Number.isSafeInteger(replica.revision) || replica.revision < 1 || !Number.isSafeInteger(replica.canonicalRevision) || replica.canonicalRevision < 1
      || !Number.isSafeInteger(custody.epoch) || custody.epoch < 1 || !Number.isSafeInteger(custody.replicaRevision) || custody.replicaRevision < 1) {
    throw new Error('F0.2B evidence has malformed terminal replica/custody fences');
  }
  if (typeof replica.fingerprint !== 'string' || !replica.fingerprint.startsWith('sha256:') || typeof replica.provenance !== 'string' || replica.provenance.length === 0) {
    throw new Error('F0.2B evidence has no exact terminal replica evidence');
  }
  if (lane === 'conflict-restart') {
    if (terminal.domain.family !== 'depot-conflict' || terminal.domain.recovery !== 'abrupt'
        || !Array.isArray(terminal.conflicts) || terminal.conflicts.length !== 3) {
      throw new Error('F0.2B conflict lane lacks complete recovered conflict evidence');
    }
    const actual = Object.fromEntries(terminal.conflicts.map(value => [value.scenario, value]));
    const changed = actual['disposable-f02b-depot-changed-restart.json'];
    const foreign = actual['disposable-f02b-depot-foreign-restart.json'];
    const missing = actual['disposable-f02b-depot-conflict-restart.json'];
    if (changed?.replica?.state !== 'CONFLICT' || changed.replica.conflict !== 'FINGERPRINT_MISMATCH' || changed.replica.observedProvenance !== changed.replica.provenance
        || foreign?.replica?.state !== 'CONFLICT' || foreign.replica.conflict !== 'FINGERPRINT_AND_PROVENANCE_MISMATCH' || !foreign.replica.observedProvenance?.startsWith('foreign:')
        || missing?.replica?.state !== 'CONFLICT' || !missing.replica.observedFingerprint?.startsWith('sha256:missing-') || !missing.replica.observedProvenance?.startsWith('missing:')
        || [changed, foreign, missing].some(value => value?.clientSession?.reusedJvm !== true || typeof value.clientSession.runId !== 'string' || value.clientSession.runId.length === 0)) {
      throw new Error('F0.2B conflict lane lacks retained actual changed/foreign/missing evidence');
    }
    return;
  }
  if (replica.state !== 'OBSERVED_CURRENT' || replica.conflict !== '') throw new Error('F0.2B owned lane has false replica drift');
  if (!Array.isArray(terminal.histories) || terminal.histories.length !== (lane === 'normal-zero-player-recovery' ? 2 : 1)) {
    throw new Error('F0.2B normal lane has incomplete causal history evidence');
  }
  const expected = lane === 'normal-never-visited' ? ['never-visited']
    : lane === 'normal-visited-unloaded' ? ['visited-unloaded']
      : lane === 'normal-zero-player-recovery' ? ['zero-player', 'graceful-product-recovery'] : null;
  if (!expected || terminal.histories.map(value => value.history).sort().join(',') !== expected.sort().join(',')) {
    throw new Error('F0.2B normal lane has a foreign causal history');
  }
  for (const history of terminal.histories) assertNormalHistory(history);
}

function assertNormalHistory(history) {
  if (!history || typeof history !== 'object' || !history.admission || !history.families) throw new Error('F0.2B normal history has no ordinary admission facts');
  const { admission, families } = history;
  if (admission.profile !== 'world' || admission.initialIntents !== 0 || admission.initialReplica !== false || admission.initialCustody !== false
      || !Number.isSafeInteger(admission.initialInstant) || admission.initialInstant < 0
      || admission.initialInputs?.depot?.wheat !== 64 || admission.initialInputs?.depot?.bread !== 0 || admission.initialInputs?.hive?.biomass !== 64
      || !Array.isArray(admission.observedEpochs) || admission.observedEpochs.length < 2
      || admission.causal?.observations < 1 || !admission.causal?.taskKinds?.includes('PRODUCE_BREAD')
      || !admission.causal?.taskKinds?.includes('GROW_HIVE_ORGANISM') || admission.causal?.productionStarted !== true
      || admission.causal?.growthStarted !== true || !Array.isArray(admission.causal?.schedules)
      || !Array.isArray(admission.causal?.orders) || admission.causal.schedules.length < 2 || admission.causal.orders.length < 1
      || !admission.causal.orders.some(order => order.reservationActive === true)
      || !Number.isSafeInteger(admission.causal.admissionAction)) {
    throw new Error('F0.2B normal history was seeded or lacks actual adapter observation');
  }
  if (!families.depot || !families.hive || families.depot.inputWheat !== 64 || families.depot.outputBread !== 64
      || !Number.isSafeInteger(families.depot.terminalBread) || families.depot.terminalBread < 0 || families.depot.terminalBread > 64
      // The reference receipt is the exact 64-wheat -> 64-bread transformation.
      // Population is a separately scheduled consumer: its number of completed
      // permits depends on the ordinary canonical interval after return, so a
      // fixed bread endpoint would turn retained-reference history into an
      // unauthorized birth throttle.  The zero-player oracle instead fences
      // the exact COLD-admitted permit and its own confirmed consumption.
      || !Number.isSafeInteger(families.depot.foodAvailable) || families.depot.foodAvailable < 0 || families.depot.foodAvailable > 64
      || !Number.isSafeInteger(families.depot.foodFulfilled) || families.depot.foodFulfilled < 0
      || families.hive.inputBiomass !== 64 || families.hive.outputBiomass !== 0
      || families.hive.growthJobs !== 0 || families.hive.addedOrgans !== 1 || families.hive.spawnedBioforms !== 1) {
    throw new Error('F0.2B normal history has non-equivalent product quantities');
  }
  if (history.history === 'never-visited') {
    if (admission.targetVisitsBeforeDue !== 0 || admission.safeUnload !== false || admission.zeroPlayerLoaded !== false) throw new Error('F0.2B never-visited history is not causal');
  } else if (history.history === 'visited-unloaded') {
    if (admission.targetVisitsBeforeDue < 2 || admission.safeUnload !== true || admission.safeUnloadScopes?.['container:1-depot'] !== true
        || admission.safeUnloadScopes?.['container:hive-east-store'] !== true || admission.zeroPlayerLoaded !== false || !admission.releasedEpochs?.length) throw new Error('F0.2B safely-unloaded history lacks per-family release evidence');
  } else if (history.history === 'zero-player') {
    const scopes = admission.zeroPlayerScopes;
    if (admission.targetVisitsBeforeDue < 2 || admission.zeroPlayerLoaded !== true || !Array.isArray(scopes) || scopes.length !== 2
        || scopes.map(value => value.id).join(',') !== 'container:1-depot,container:hive-east-store'
        || admission.safeUnload !== true || admission.safeUnloadScopes?.['container:1-depot'] !== true
        || admission.safeUnloadScopes?.['container:hive-east-store'] !== true
        || scopes.some(value => !Number.isSafeInteger(value.priorReleasedActionStep) || !Number.isSafeInteger(value.priorReleasedCustodyEpoch)
          || !Number.isSafeInteger(value.visitStep) || !Number.isSafeInteger(value.loadedActionStep)
          || !Number.isSafeInteger(value.acquiredActionStep) || !Number.isSafeInteger(value.effectActionStep)
          || !Number.isSafeInteger(value.releasedActionStep) || value.loadedActionStep >= value.acquiredActionStep
          || value.acquiredActionStep >= value.effectActionStep || value.effectActionStep >= value.releasedActionStep
          || value.priorReleasedActionStep >= value.visitStep || value.visitStep >= value.loadedActionStep
          || !Number.isSafeInteger(value.custodyEpoch) || !Number.isSafeInteger(value.replicaRevision)
          || !Number.isSafeInteger(value.loadedCustodyEpoch) || value.priorReleasedCustodyEpoch > value.loadedCustodyEpoch
          || value.loadedCustodyEpoch > value.custodyEpoch || value.effectCustodyEpoch !== value.custodyEpoch
          || value.releasedCustodyEpoch !== value.custodyEpoch || !Number.isSafeInteger(value.loadedReplicaRevision)
          || !Number.isSafeInteger(value.effectReplicaRevision) || !Number.isSafeInteger(value.releasedReplicaRevision)
          || value.loadedReplicaRevision > value.replicaRevision || value.effectReplicaRevision < value.replicaRevision
          || value.releasedReplicaRevision < value.effectReplicaRevision
          || typeof value.replicaFingerprint !== 'string' || !value.replicaFingerprint.startsWith('sha256:')
          || value.naturalChunkLoaded !== true || value.ordinaryPlayerNearby !== false || value.presentationDemand !== false
          || value.eligibleObserverCount !== 0 || value.presentationObserverCount !== 0
          || value.playerChunk?.x === value.scopeChunk?.x && value.playerChunk?.z === value.scopeChunk?.z
          || value.loadedMilestone !== `zero_player_${value.id === 'container:1-depot' ? 'depot' : 'hive'}_loaded_no_demand`
          || value.acquiredMilestone !== `zero_player_${value.id === 'container:1-depot' ? 'depot' : 'hive'}_acquired_no_demand`
          || value.effectMilestone !== `zero_player_${value.id === 'container:1-depot' ? 'depot' : 'hive'}_effect_no_demand`
          || value.releasedMilestone !== `zero_player_${value.id === 'container:1-depot' ? 'depot' : 'hive'}_released`
          || !physicalEffectAfterAdmission(admission.observerFreePhysicalEffects?.[value.id === 'container:1-depot' ? 'depot' : 'hive'], admission.causal.admissionAction, value))) {
      throw new Error('F0.2B zero-player history is not causal');
    }
    assertZeroPlayerBirthCatchup(admission.birthCatchup);
  } else if (history.history === 'graceful-product-recovery') {
    if (history.recovery?.mode !== 'graceful' || admission.targetVisitsBeforeDue < 1 || !history.recovery.beforeEpoch || !history.recovery.afterEpoch) throw new Error('F0.2B product recovery lacks a fenced custody boundary');
    assertRecoveryCausalMilestones(history.recovery.milestones);
  } else throw new Error('F0.2B normal history is unknown');
}

function assertZeroPlayerBirthCatchup(value) {
  const cold = value?.cold; const afterReturn = value?.afterReturn;
  if (!cold || !afterReturn || !Number.isSafeInteger(value.returnAction) || !Number.isSafeInteger(cold.actionStep)
      || !Number.isSafeInteger(afterReturn.actionStep) || !Number.isSafeInteger(cold.instant) || !Number.isSafeInteger(afterReturn.instant)
      || cold.actionStep >= value.returnAction || value.returnAction >= afterReturn.actionStep || cold.instant > afterReturn.instant
      || cold.job?.id !== afterReturn.job?.id || cold.job?.intent !== afterReturn.job?.intent || cold.job?.food !== 'item:production-1-1-bread'
      || cold.intent?.id !== cold.job.intent || cold.intent?.cause !== cold.job.id || cold.intent?.kind !== 'EXACT_ITEM_CONSUMPTION'
      || cold.intent?.status !== 'PREPARED' || afterReturn.intent?.id !== cold.intent.id || afterReturn.intent?.cause !== cold.intent.cause
      || afterReturn.intent?.kind !== 'EXACT_ITEM_CONSUMPTION' || afterReturn.intent?.status !== 'CONFIRMED') {
    throw new Error('F0.2B zero-player history lacks the released-COLD birth permit and exact return catch-up');
  }
}

/**
 * The recovery oracle consumes named read-only facts rather than scenario ordinals.  Each fact
 * is tied to its exact canonical subject and instant; a transient cleared request, a later
 * unrelated hive state, or a reordered receipt cannot satisfy this predicate.
 */
export function assertRecoveryCausalMilestones(milestones) {
  const required = ['activeAdmission', 'activeDepotCustody', 'hydratedInflight', 'hydratedDepotCustody', 'afterReacquire', 'terminalProduct'];
  if (!milestones || typeof milestones !== 'object' || required.some(key => !milestones[key])) {
    throw new Error('F0.2B product recovery has missing causal milestone evidence');
  }
  const active = milestones.activeAdmission;
  if (active.phase !== 'before_restart' || active.kind !== 'reference_container' || active.id !== 'f02b'
      || !Number.isSafeInteger(active.instant) || !active.taskKinds?.includes('PRODUCE_BREAD') || !active.taskKinds?.includes('GROW_HIVE_ORGANISM')
      || !exactInFlightProduction(active) || !exactPendingHiveStart(active)
      || !Number.isSafeInteger(active.actionStep)) {
    throw new Error('F0.2B product recovery lacks retained active-admission evidence');
  }
  const activeCustody = milestones.activeDepotCustody;
  if (!exactDepotCustody(activeCustody, 'before_restart', 'CHECKPOINTED') || activeCustody.actionStep !== active.actionStep + 1) {
    throw new Error('F0.2B product recovery lacks retained active-custody evidence');
  }
  const hydrated = milestones.hydratedInflight;
  const hydratedCustody = milestones.hydratedDepotCustody;
  if (hydrated.phase !== 'after_restart' || hydrated.kind !== 'reference_container' || hydrated.id !== 'f02b'
      || !Number.isSafeInteger(hydrated.instant) || hydrated.instant < active.instant
      || !sameInFlightProduction(exactInFlightProduction(active), exactInFlightProduction(hydrated))
      || !Number.isSafeInteger(hydrated.actionStep)
      || !exactDepotCustody(hydratedCustody, 'after_restart', 'RELEASED') || hydratedCustody.actionStep !== hydrated.actionStep + 1
      || !sameCustody(activeCustody, hydratedCustody)) {
    throw new Error('F0.2B product recovery lacks same-operation hydrated in-flight evidence');
  }
  const after = milestones.afterReacquire;
  if (after.phase !== 'after_restart' || after.kind !== 'reference_container' || after.id !== 'f02b'
      || !Number.isSafeInteger(after.instant) || after.instant < active.instant
      || !exactProductionCompletion(after)) {
    throw new Error('F0.2B product recovery has stale or wrong-subject reacquire evidence');
  }
  const terminalProduct = milestones.terminalProduct;
  if (terminalProduct.phase !== 'after_restart' || terminalProduct.kind !== 'reference_container' || terminalProduct.id !== 'f02b'
      || !Number.isSafeInteger(terminalProduct.instant) || terminalProduct.instant < after.instant
      || !exactProductionCompletion(terminalProduct)) {
    throw new Error('F0.2B product recovery lacks exact terminal operation evidence');
  }
  return milestones;
}

function exactProductionCompletion(value) {
  const production = value.tasks?.filter(candidate => candidate.id === 'task:settlement-1-settlement_produce_bread-1'
    && candidate.kind === 'PRODUCE_BREAD' && candidate.status === 'COMPLETED') ?? [];
  const order = value.orders?.filter(candidate => candidate.task === 'task:settlement-1-settlement_produce_bread-1'
    && candidate.job === 'job:production-1-1' && candidate.reservation === 'reservation:production-1-1'
    && candidate.reservationActive === false && candidate.status === 'FULFILLED') ?? [];
  return production.length === 1 && order.length === 1;
}

function exactDepotCustody(value, phase, status) {
  return value?.phase === phase && value.kind === 'container' && value.id === 'container:1-depot'
    && value.custodyStatus === status && Number.isSafeInteger(value.actionStep) && Number.isSafeInteger(value.custodyEpoch)
    && Number.isSafeInteger(value.replicaRevision) && typeof value.replicaFingerprint === 'string' && value.replicaFingerprint.startsWith('sha256:');
}

function sameCustody(left, right) {
  return left.custodyEpoch === right.custodyEpoch && left.replicaRevision === right.replicaRevision
    && left.replicaFingerprint === right.replicaFingerprint;
}

/**
 * The recovery boundary names one actually admitted operation.  The companion
 * hive task is deliberately still PENDING at that instant, so it is a control
 * for due ordering, not a fabricated second in-flight recovery subject.  A
 * later graceful save can advance canonical time, therefore the durable fact is
 * the operation's exact action identity and remaining due interval, rather than
 * two unrelated absolute instants from different server lifetimes.
 */
function exactInFlightProduction(value) {
  if (!value || !Number.isSafeInteger(value.instant)) return null;
  const task = value.tasks?.filter(candidate => candidate.id === 'task:settlement-1-settlement_produce_bread-1'
    && candidate.kind === 'PRODUCE_BREAD' && candidate.status === 'ACTIVE') ?? [];
  const order = value.orders?.filter(candidate => candidate.task === 'task:settlement-1-settlement_produce_bread-1'
    && candidate.job === 'job:production-1-1' && candidate.reservation === 'reservation:production-1-1'
    && candidate.reservationActive === true && candidate.status === 'ACCEPTED') ?? [];
  const schedule = value.schedules?.filter(candidate => candidate.id === 'schedule:production-task-complete-production-1-1'
    && candidate.subject === 'job:production-1-1' && candidate.kind === 'frontier.settlement.production.task.complete'
    && candidate.weight === 1 && Number.isSafeInteger(candidate.dueAt)) ?? [];
  if (task.length !== 1 || order.length !== 1 || schedule.length !== 1) return null;
  const remaining = schedule[0].dueAt - value.instant;
  if (!Number.isSafeInteger(remaining) || remaining <= 0) return null;
  return { task: task[0], order: order[0], schedule: schedule[0], remaining };
}

function exactPendingHiveStart(value) {
  if (!value || !Number.isSafeInteger(value.instant)) return false;
  const task = value.tasks?.filter(candidate => candidate.id === 'task:hive-frontier-hive_grow_organism-1'
    && candidate.kind === 'GROW_HIVE_ORGANISM' && candidate.status === 'PENDING') ?? [];
  const schedule = value.schedules?.filter(candidate => candidate.id === 'schedule:hive-growth-task-start-task-hive-frontier-hive_grow_organism-1'
    && candidate.subject === 'task:hive-frontier-hive_grow_organism-1' && candidate.kind === 'frontier.hive.growth.task.start'
    && candidate.weight === 1 && Number.isSafeInteger(candidate.dueAt) && candidate.dueAt > value.instant) ?? [];
  return task.length === 1 && schedule.length === 1;
}

function sameInFlightProduction(left, right) {
  return left != null && right != null && JSON.stringify(left.task) === JSON.stringify(right.task)
    && JSON.stringify(left.order) === JSON.stringify(right.order)
    && left.schedule.id === right.schedule.id && left.schedule.subject === right.schedule.subject
    && left.schedule.kind === right.schedule.kind && left.schedule.weight === right.schedule.weight
    && left.remaining === right.remaining;
}

function assertHistoryComparator(checked) {
  const histories = checked.filter(value => value.lane !== 'conflict-restart').flatMap(value => value.terminal.histories);
  const selected = new Map(histories.filter(value => ['never-visited', 'visited-unloaded', 'zero-player'].includes(value.history)).map(value => [value.history, value]));
  if (selected.size !== 3) throw new Error('F0.2B merge lacks all three ordinary histories');
  const projection = value => {
    const { admissionAction, ...causal } = value.admission.causal;
    // Compare each reference family at its own product boundary.  A later
    // resident-birth permit is neither depot production nor hive growth, so
    // its ordinary post-return food consumption cannot make the histories
    // falsely inequivalent.
    const depot = value.families.depot;
    return { depot: { inputWheat: depot.inputWheat, outputBread: depot.outputBread },
      hive: value.families.hive, causal };
  };
  const baseline = JSON.stringify(projection(selected.get('never-visited')));
  for (const name of ['visited-unloaded', 'zero-player']) {
    if (JSON.stringify(projection(selected.get(name))) !== baseline) throw new Error(`F0.2B merge rejects ${name} terminal product drift`);
  }
}

function physicalEffectAfterAdmission(effect, admissionAction, scope) {
  return effect && typeof effect === 'object' && Number.isSafeInteger(effect.actionStep)
    && effect.actionStep > admissionAction && effect.actionStep === scope.effectActionStep && Number.isSafeInteger(effect.custodyEpoch)
    && Number.isSafeInteger(effect.replicaRevision) && typeof effect.replicaFingerprint === 'string'
    && effect.replicaFingerprint.startsWith('sha256:') && effect.custodyEpoch === scope.custodyEpoch
    && effect.replicaRevision >= scope.replicaRevision
    && effect.milestone === scope.effectMilestone && effect.ordinaryPlayerNearby === false
    && effect.presentationDemand === false && effect.eligibleObserverCount === 0 && effect.presentationObserverCount === 0;
}
