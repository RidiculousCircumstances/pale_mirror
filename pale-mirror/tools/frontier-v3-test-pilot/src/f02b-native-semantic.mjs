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
  if (typeof value.executionGate !== 'string' || !value.executionGate.endsWith(`f02b-native-execution-${value.runId}-${value.runAttempt}`)) {
    throw new Error('F0.2B evidence has no exact native execution gate');
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
    if (receipt.value.nativeExecutionGate?.mode !== 'serialized' || receipt.value.nativeExecutionGate.directory !== semantic.executionGate) {
      throw new Error('F0.2B primary evidence has no retained serialized native execution receipt');
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
  }
  return primary;
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

function immutableIdentity(value) {
  return { qualificationId: value.qualificationId, repository: value.repository, headSha: value.headSha, workflowSha: value.workflowSha,
    workflowRef: value.workflowRef, runId: value.runId, runAttempt: value.runAttempt, jobId: value.jobId, runnerId: value.runnerId,
    runnerName: value.runnerName, launchTarget: value.launchTarget, requiredTest: value.requiredTest, requiredTestCount: value.requiredTestCount,
    jvmEnvelope: value.jvmEnvelope };
}

function sameJson(left, right) { return JSON.stringify(left) === JSON.stringify(right); }

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
    const actual = Object.fromEntries(terminal.conflicts.map(value => [value.scenario, value.replica]));
    const changed = actual['disposable-f02b-depot-changed-restart.json'];
    const foreign = actual['disposable-f02b-depot-foreign-restart.json'];
    const missing = actual['disposable-f02b-depot-conflict-restart.json'];
    if (changed?.state !== 'CONFLICT' || changed.conflict !== 'FINGERPRINT_MISMATCH' || changed.observedProvenance !== changed.provenance
        || foreign?.state !== 'CONFLICT' || foreign.conflict !== 'FINGERPRINT_AND_PROVENANCE_MISMATCH' || !foreign.observedProvenance?.startsWith('foreign:')
        || missing?.state !== 'CONFLICT' || !missing.observedFingerprint?.startsWith('sha256:missing-') || !missing.observedProvenance?.startsWith('missing:')) {
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
      || !Array.isArray(admission.observedEpochs) || admission.observedEpochs.length < 2) {
    throw new Error('F0.2B normal history was seeded or lacks actual adapter observation');
  }
  if (!families.depot || !families.hive || families.depot.inputWheat !== 64 || families.depot.outputBread !== 64 || families.depot.terminalBread !== 64
      || families.depot.foodAvailable !== 64 || families.depot.foodFulfilled !== 0
      || families.hive.inputBiomass !== 64 || families.hive.outputBiomass !== 0
      || families.hive.growthJobs !== 0 || families.hive.addedOrgans !== 1 || families.hive.spawnedBioforms !== 1) {
    throw new Error('F0.2B normal history has non-equivalent product quantities');
  }
  if (history.history === 'never-visited') {
    if (admission.targetVisitsBeforeDue !== 0 || admission.safeUnload !== false || admission.zeroPlayerLoaded !== false) throw new Error('F0.2B never-visited history is not causal');
  } else if (history.history === 'visited-unloaded') {
    if (admission.targetVisitsBeforeDue < 2 || admission.safeUnload !== true || admission.zeroPlayerLoaded !== false || !admission.releasedEpochs?.length) throw new Error('F0.2B safely-unloaded history lacks release evidence');
  } else if (history.history === 'zero-player') {
    if (admission.targetVisitsBeforeDue < 2 || admission.zeroPlayerLoaded !== true) throw new Error('F0.2B zero-player history is not causal');
  } else if (history.history === 'graceful-product-recovery') {
    if (history.recovery?.mode !== 'graceful' || admission.targetVisitsBeforeDue < 1 || !history.recovery.beforeEpoch || !history.recovery.afterEpoch) throw new Error('F0.2B product recovery lacks a fenced custody boundary');
  } else throw new Error('F0.2B normal history is unknown');
}

function assertHistoryComparator(checked) {
  const histories = checked.filter(value => value.lane !== 'conflict-restart').flatMap(value => value.terminal.histories);
  const selected = new Map(histories.filter(value => ['never-visited', 'visited-unloaded', 'zero-player'].includes(value.history)).map(value => [value.history, value]));
  if (selected.size !== 3) throw new Error('F0.2B merge lacks all three ordinary histories');
  const projection = value => ({ depot: value.families.depot, hive: value.families.hive });
  const baseline = JSON.stringify(projection(selected.get('never-visited')));
  for (const name of ['visited-unloaded', 'zero-player']) {
    if (JSON.stringify(projection(selected.get(name))) !== baseline) throw new Error(`F0.2B merge rejects ${name} terminal product drift`);
  }
}
