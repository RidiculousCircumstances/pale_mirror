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
  'worker-0': 'depot-never-visited',
  'worker-1': 'depot-visited-unloaded',
  'worker-2': 'hive-zero-player',
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
  if (value.requiredTestCount !== (value.lane === 'conflict-restart' ? 3 : 1)) throw new Error('F0.2B evidence has incomplete lane coverage');
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
  if (!primary.runtime?.receipt || primary.runtime.receipt.worker !== semantic.worker || primary.runtime.receipt.runtimeContentSha256 !== semantic.runtimeContentSha256
      || !primary.runtime.preparedIdentity?.sourceContent || !primary.runtime.preparedIdentity?.artifactSha256) {
    throw new Error('F0.2B primary evidence lacks the consumed prepared runtime identity');
  }
  if (!Array.isArray(primary.manifests) || primary.manifests.length !== semantic.requiredTestCount) throw new Error('F0.2B primary evidence has incomplete scenario receipts');
  for (const receipt of primary.manifests) {
    if (!receipt || typeof receipt.scenario !== 'string' || !HASH.test(receipt.sha256 ?? '') || !receipt.value
        || hashJson(receipt.value) !== receipt.sha256) throw new Error('F0.2B primary evidence has corrupt scenario receipt');
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
  if (lane === 'depot-never-visited') {
    if (terminal.domain.family !== 'settlement-provision' || terminal.domain.foodStatus !== 'SECURE' || terminal.domain.available !== 27
        || terminal.domain.fulfilled !== 37 || terminal.domain.intentStatus !== 'CONFIRMED') throw new Error('F0.2B settlement provision terminal is not exact');
  } else if (lane === 'depot-visited-unloaded') {
    if (terminal.domain.family !== 'settlement-production' || terminal.domain.orderStatus !== 'FULFILLED' || terminal.domain.itemCount !== 64
        || terminal.domain.itemCustody !== 'CONTAINER_SLOT') throw new Error('F0.2B production terminal is not exact');
  } else if (lane === 'hive-zero-player') {
    if (terminal.domain.family !== 'hive-growth' || terminal.domain.growthJobs !== 0 || terminal.domain.addedOrgans !== 1
        || terminal.domain.spawnedBioforms !== 1 || terminal.domain.intentStatus !== 'CONFIRMED') throw new Error('F0.2B hive terminal is not exact');
  }
}
