import { createHash, randomUUID } from 'node:crypto';
import { watch } from 'node:fs';
import { link, mkdir, open, readFile, unlink, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { LifecycleBarrier } from './lifecycle-barrier.mjs';
import { createPersistentExpectedCrashArm, createPersistentExpectedCrashRelease, validatePersistentExpectedCrashProof, validatePersistentExpectedCrashRelease } from './persistent-crash.mjs';

export const PERSISTENT_MATRIX_SCHEMA = 1;
const KIND = 'frontier-v3-persistent-matrix';
const ASSIGNED_KIND = 'frontier-v3-assigned-persistent-matrix';

/** Validates the external, noncanonical plan for one visible client JVM. */
export function validatePersistentMatrixPlan(value) {
  const assigned = value?.kind === ASSIGNED_KIND;
  const runtimeAssigned = assigned && value?.workerPlan === undefined;
  if (!value || value.schema !== PERSISTENT_MATRIX_SCHEMA || (!assigned && value.kind !== KIND) || !token(value.workerId)
      || !sha256(value.buildIdentitySha256) || !Array.isArray(value.segments) || value.segments.length < (assigned ? 1 : 3)
      || value.segments.length > 32) throw new Error('persistent matrix plan is malformed');
  if (assigned && (!value.source || !sha256(value.workerPlanSha256) || !sha256(value.contentSha256)
      || value.source.workerId !== value.workerId || value.source.buildIdentitySha256 !== value.buildIdentitySha256)) {
    throw new Error('assigned persistent matrix plan is not compiler-anchored');
  }
  if (runtimeAssigned && !sha256(value.compiledContentSha256)) throw new Error('assigned persistent matrix runtime compiled identity is malformed');
  if (runtimeAssigned) exactFields(value, ['schema', 'kind', 'workerId', 'buildIdentitySha256', 'source', 'workerPlanSha256', 'compiledContentSha256', 'segments', 'contentSha256'], 'assigned persistent runtime plan');
  const seen = new Set();
  const segments = value.segments.map((segment, epoch) => {
    const completion = segment?.completion ?? 'terminal';
    if (!segment || !token(segment.id) || !token(segment.scenarioId) || !sha256(segment.scenarioSha256)
        || !token(segment.worldKey) || !Number.isInteger(segment.actionCount) || segment.actionCount < 1 || segment.actionCount > 64
        || typeof segment.final !== 'boolean' || !['terminal', 'graceful_handoff', 'expected_crash', 'recovered_terminal'].includes(completion)
        || seen.has(segment.id) || (completion === 'expected_crash' ? !expectedCrash(segment.expectedCrash) : segment.expectedCrash !== undefined)) {
      throw new Error('persistent matrix segment is malformed');
    }
    if (segment.final !== (epoch === value.segments.length - 1)) throw new Error('persistent matrix final segment is malformed');
    if (segment.reuseServer !== undefined && segment.reuseServer !== true) {
      throw new Error('persistent matrix compatible-case declaration is malformed');
    }
    seen.add(segment.id);
    if (assigned && (!token(segment.laneId) || !token(segment.originalScenarioId) || !sha256(segment.originalScenarioSha256)
        || !Number.isInteger(segment.originalActionOffset) || segment.originalActionOffset < 0)) {
      throw new Error('assigned persistent matrix segment is malformed');
    }
    if (runtimeAssigned && !sha256(segment.compiledScenarioSha256)) throw new Error('assigned persistent runtime segment compiled identity is malformed');
    if (runtimeAssigned) exactFields(segment, ['id', 'laneId', 'scenarioId', 'scenarioSha256', 'compiledScenarioSha256', 'originalScenarioId', 'originalScenarioSha256', 'originalActionOffset', 'worldKey', 'actionCount', 'completion', 'final', ...(segment.reuseServer === true ? ['reuseServer'] : []), ...(completion === 'expected_crash' ? ['expectedCrash'] : [])], 'assigned persistent runtime segment');
    if (runtimeAssigned) return Object.freeze({ id: segment.id, laneId: segment.laneId, scenarioId: segment.scenarioId,
      scenarioSha256: segment.scenarioSha256, compiledScenarioSha256: segment.compiledScenarioSha256,
      originalScenarioId: segment.originalScenarioId, originalScenarioSha256: segment.originalScenarioSha256,
      originalActionOffset: segment.originalActionOffset, worldKey: segment.worldKey, actionCount: segment.actionCount,
      completion, ...(segment.reuseServer === true ? { reuseServer: true } : {}), ...(completion === 'expected_crash' ? { expectedCrash: Object.freeze(structuredClone(segment.expectedCrash)) } : {}), final: segment.final });
    return Object.freeze({ id: segment.id, scenarioId: segment.scenarioId, scenarioSha256: segment.scenarioSha256,
      worldKey: segment.worldKey, actionCount: segment.actionCount, final: segment.final, completion,
      ...(segment.reuseServer === true ? { reuseServer: true } : {}),
      ...(assigned ? { laneId: segment.laneId, originalScenarioId: segment.originalScenarioId,
        originalScenarioSha256: segment.originalScenarioSha256, compiledScenarioSha256: segment.compiledScenarioSha256,
        originalActionOffset: segment.originalActionOffset } : {}),
      ...(completion === 'expected_crash' ? { expectedCrash: Object.freeze(structuredClone(segment.expectedCrash)) } : {}) });
  });
  if (!assigned && new Set(segments.map((segment) => segment.worldKey)).size < 3) {
    throw new Error('persistent matrix requires three independent disposable worlds');
  }
  const core = { schema: PERSISTENT_MATRIX_SCHEMA, kind: value.kind, workerId: value.workerId,
    buildIdentitySha256: value.buildIdentitySha256, ...(assigned ? { source: Object.freeze(structuredClone(value.source)),
      workerPlanSha256: value.workerPlanSha256, ...(runtimeAssigned ? { compiledContentSha256: value.compiledContentSha256 } : {}) } : {}), segments: Object.freeze(segments) };
  const checked = { ...core, ...(assigned ? { contentSha256: value.contentSha256 } : {}) };
  if (runtimeAssigned) {
    // Match the runner's declared serialized order rather than retaining incidental input
    // order, so a successful validated plan is valid again after session storage/JSON parse.
    if (sha256Json(core) !== checked.contentSha256) throw new Error('assigned persistent matrix runtime content identity is stale');
  }
  return Object.freeze(checked);
}

/** Returns one validated immutable segment; callers must not infer terminality from runtime helpers. */
export function matrixSegment(plan, epoch) {
  const checked = validatePersistentMatrixPlan(plan);
  if (!Number.isInteger(epoch) || epoch < 0 || epoch >= checked.segments.length) {
    throw new Error('persistent matrix epoch is invalid');
  }
  return checked.segments[epoch];
}

/** The session owner derives an arm only from its pinned crash-capable descriptor. */
export function createPersistentMatrixExpectedCrashArm(session, epoch, { serverRunId, serverPid, clientPid, port, resolvedRevision, authorityEpoch = undefined }) {
  const checked = requireSession(session); const segment = matrixSegment(checked.plan, epoch);
  if (segment.completion !== 'expected_crash') throw new Error('persistent matrix segment is not crash-capable');
  const declaration = segment.expectedCrash;
  return createPersistentExpectedCrashArm({ identity: checked.identity, epoch, serverRunId, serverPid, clientPid, port, resolvedRevision, authorityEpoch,
    descriptor: { completion: segment.completion, segment: segment.id, scenarioId: segment.scenarioId, scenarioSha256: segment.scenarioSha256,
      worldKey: segment.worldKey, laneId: declaration.laneId, boundary: declaration.boundary, owner: declaration.owner, payloadType: declaration.payloadType,
      expectedRevision: declaration.expectedRevision, resolvedRevision, ...(declaration.expectedAuthorityEpoch === undefined ? {} : { expectedAuthorityEpoch: declaration.expectedAuthorityEpoch }) } });
}

/** Creates append-only session descriptors; no segment or resume token is ever overwritten. */
export async function createPersistentMatrixSession(directory, lifecycleIdentity, plan) {
  const checkedPlan = validatePersistentMatrixPlan(plan);
  if (checkedPlan.workerId !== lifecycleIdentity?.workerId || checkedPlan.buildIdentitySha256 !== lifecycleIdentity?.buildIdentitySha256) {
    throw new Error('persistent matrix plan is foreign to lifecycle identity');
  }
  const root = resolve(directory);
  await mkdir(root, { recursive: false });
  await mkdir(join(root, 'segments'));
  await mkdir(join(root, 'resume'));
  await mkdir(join(root, 'expected-crash'));
  await mkdir(join(root, 'server-ready'));
  await mkdir(join(root, 'recovery'));
  // Only the validated final terminal result may receive this supervisor-owned close token.
  // The client never chooses its own matrix shutdown boundary.
  await mkdir(join(root, 'close'));
  // The supervisor subscribes before the pilot can emit the first result.  Keep the directory
  // present from session creation so this is an exact filesystem barrier, not a retry over a
  // missing control surface.
  await mkdir(join(root, 'results'));
  await writeExclusive(join(root, 'run-id'), `${lifecycleIdentity.runId}\n`);
  for (const [epoch, segment] of checkedPlan.segments.entries()) {
    const descriptor = Object.freeze({ schema: PERSISTENT_MATRIX_SCHEMA, kind: 'frontier-v3-pilot-matrix-segment',
      runId: lifecycleIdentity.runId, epoch, segment: segment.id, scenarioId: segment.scenarioId,
      final: segment.final, completion: segment.completion, scenarioSha256: segment.scenarioSha256,
      worldKey: segment.worldKey, workerId: lifecycleIdentity.workerId, buildIdentitySha256: lifecycleIdentity.buildIdentitySha256,
      nonce: lifecycleIdentity.nonce, sessionId: lifecycleIdentity.sessionId,
      // These are runner provenance only. Java still binds the exact runtime byte through
      // scenarioSha256; retaining its source pair stops a port-adapted descriptor from
      // erasing the compiler/original identities from the final evidence.
      ...(segment.originalScenarioSha256 === undefined ? {} : { originalScenarioId: segment.originalScenarioId,
        originalScenarioSha256: segment.originalScenarioSha256, compiledScenarioSha256: segment.compiledScenarioSha256,
        originalActionOffset: segment.originalActionOffset }),
      ...(segment.completion === 'expected_crash' ? { expectedCrash: segment.expectedCrash } : {}) });
    await writeExclusive(join(root, 'segments', `${String(epoch).padStart(4, '0')}.json`), `${JSON.stringify(descriptor)}\n`);
  }
  await writeExclusive(join(root, 'plan.json'), `${JSON.stringify(checkedPlan)}\n`);
  return Object.freeze({ directory: root, identity: lifecycleIdentity, plan: checkedPlan });
}

export async function publishPersistentMatrixResume(session, epoch) {
  const checked = requireSession(session);
  if (!Number.isInteger(epoch) || epoch < 1 || epoch >= checked.plan.segments.length) throw new Error('persistent matrix resume epoch is invalid');
  const predecessor = checked.plan.segments[epoch - 1];
  if (predecessor.completion === 'expected_crash') {
    await readPersistentMatrixExpectedCrashRelease(checked, epoch);
  }
  const token = `${checked.identity.runId}:${epoch}\n`;
  const path = join(checked.directory, 'resume', `${String(epoch).padStart(4, '0')}.token`);
  // A visible control entry is itself a lifecycle barrier.  Do not expose a created-but-not-yet
  // complete token to the persistent client: it must observe either no entry or one exact token.
  await publishAtomicExclusive(path, token, 'resume control entry');
  return Object.freeze({ epoch, path, token: token.trim() });
}

/** Records the exact live server which an arm may reference; replacement fields cannot be self-certified by the arm. */
export async function publishPersistentMatrixServerReady(session, epoch, ready) {
  const checked = requireSession(session); const segment = matrixSegment(checked.plan, epoch);
  if (!ready || !token(ready.serverRunId) || !Number.isInteger(ready.serverPid) || ready.serverPid <= 1
      || !Number.isInteger(ready.port) || ready.port < 1024 || ready.port > 65535 || ready.worldKey !== segment.worldKey || ready.ready !== true) {
    throw new Error('persistent matrix server readiness is malformed or foreign');
  }
  const value = Object.freeze({ schema: PERSISTENT_MATRIX_SCHEMA, kind: 'frontier-v3-persistent-server-ready',
    runId: checked.identity.runId, epoch, segment: segment.id, scenarioSha256: segment.scenarioSha256, worldKey: segment.worldKey,
    serverRunId: ready.serverRunId, serverPid: ready.serverPid, port: ready.port, ready: true });
  await publishAtomicExclusive(join(checked.directory, 'server-ready', `${String(epoch).padStart(4, '0')}.json`), `${JSON.stringify(value)}\n`, 'server readiness');
  return value;
}

/** Publishes the one immutable arm only after comparing its runtime server fields to a prior ready receipt. */
export async function publishPersistentMatrixExpectedCrashArm(session, epoch, runtime) {
  const checked = requireSession(session); const segment = matrixSegment(checked.plan, epoch);
  if (segment.completion !== 'expected_crash') throw new Error('persistent matrix segment is not crash-capable');
  const ready = await readPersistentMatrixServerReady(checked, epoch);
  if (!runtime || runtime.serverRunId !== ready.serverRunId || runtime.serverPid !== ready.serverPid || runtime.port !== ready.port) {
    throw new Error('persistent expected crash arm does not match active server readiness');
  }
  const arm = createPersistentMatrixExpectedCrashArm(checked, epoch, runtime);
  await publishAtomicExclusive(join(checked.directory, 'expected-crash', `${String(epoch).padStart(4, '0')}.json`), `${JSON.stringify(arm)}\n`, 'expected crash arm');
  return arm;
}

export async function publishPersistentMatrixExpectedCrashRelease(session, epoch, proof, successor) {
  const checked = requireSession(session); const arm = JSON.parse(await readFile(join(checked.directory, 'expected-crash', `${String(epoch).padStart(4, '0')}.json`), 'utf8'));
  const ready = await readPersistentMatrixServerReady(checked, epoch + 1);
  if (!successor || successor.serverRunId !== ready.serverRunId || successor.serverPid !== ready.serverPid || successor.worldKey !== ready.worldKey
      || successor.segment !== checked.plan.segments[epoch + 1]?.id || successor.scenarioSha256 !== checked.plan.segments[epoch + 1]?.scenarioSha256) {
    throw new Error('persistent expected crash release does not match successor readiness');
  }
  const release = createPersistentExpectedCrashRelease({ arm, proof: validatePersistentExpectedCrashProof(arm, proof), successor: { ...successor, epoch: epoch + 1, ready: true } });
  await publishAtomicExclusive(join(checked.directory, 'recovery', `${String(epoch + 1).padStart(4, '0')}.json`), `${JSON.stringify(release)}\n`, 'expected crash release');
  return release;
}

async function readPersistentMatrixServerReady(session, epoch) {
  const checked = requireSession(session); const segment = matrixSegment(checked.plan, epoch);
  const value = JSON.parse(await readFile(join(checked.directory, 'server-ready', `${String(epoch).padStart(4, '0')}.json`), 'utf8'));
  if (!value || value.schema !== PERSISTENT_MATRIX_SCHEMA || value.kind !== 'frontier-v3-persistent-server-ready' || value.runId !== checked.identity.runId
      || value.epoch !== epoch || value.segment !== segment.id || value.scenarioSha256 !== segment.scenarioSha256 || value.worldKey !== segment.worldKey
      || !token(value.serverRunId) || !Number.isInteger(value.serverPid) || value.serverPid <= 1 || !Number.isInteger(value.port) || value.ready !== true) {
    throw new Error('persistent matrix server readiness is malformed or stale');
  }
  return Object.freeze(value);
}

async function readPersistentMatrixExpectedCrashRelease(session, epoch) {
  const checked = requireSession(session); const predecessor = matrixSegment(checked.plan, epoch - 1); const successor = matrixSegment(checked.plan, epoch);
  let value;
  try { value = JSON.parse(await readFile(join(checked.directory, 'recovery', `${String(epoch).padStart(4, '0')}.json`), 'utf8')); }
  catch (error) { if (error?.code === 'ENOENT') throw new Error('persistent expected crash release is missing'); throw error; }
  let arm;
  try { arm = JSON.parse(await readFile(join(checked.directory, 'expected-crash', `${String(epoch - 1).padStart(4, '0')}.json`), 'utf8')); }
  catch (error) { throw new Error('persistent expected crash release has no retained arm'); }
  const ready = await readPersistentMatrixServerReady(checked, epoch);
  const next = value?.successor; const proof = value?.proof;
  if (!next || next.epoch !== epoch || next.segment !== successor.id || next.scenarioSha256 !== successor.scenarioSha256
      || next.worldKey !== predecessor.worldKey || next.serverRunId !== ready.serverRunId || next.serverPid !== ready.serverPid
      || next.serverRunId === arm.serverRunId || !proof?.fired || !proof?.ownedExit || !proof?.clientLoss || !proof?.portClosed) {
    throw new Error('persistent expected crash release is malformed or stale');
  }
  return validatePersistentExpectedCrashRelease(value, { arm, proof, successor: next });
}

/**
 * Releases the final already-verified segment for one normal client shutdown.
 * This is deliberately distinct from resume: a final client may not infer that
 * a local assertion is enough to terminate the matrix before the supervisor
 * has received and authenticated the immutable result record.
 */
export async function publishPersistentMatrixFinalClose(session, epoch) {
  const checked = requireSession(session);
  const final = requireFinalEpoch(checked, epoch);
  const token = `${checked.identity.runId}:${epoch}\n`;
  const path = join(checked.directory, 'close', `${String(epoch).padStart(4, '0')}.token`);
  // The client is already watching this directory.  Atomic publication prevents it from reading
  // a partial close token and treating a valid supervisor decision as foreign evidence.
  await publishAtomicExclusive(path, token, 'final close control entry');
  return Object.freeze({ epoch, segment: final.id, path, token: token.trim() });
}

/** Waits for the one immutable supervisor close token and rejects foreign content. */
export async function awaitPersistentMatrixFinalClose(session, epoch, timeoutMs, cancellation = undefined) {
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 300_000) throw new Error('persistent matrix final close timeout is invalid');
  const checked = requireSession(session);
  requireFinalEpoch(checked, epoch);
  const abort = cancellationSignal(cancellation);
  const path = join(checked.directory, 'close', `${String(epoch).padStart(4, '0')}.token`);
  const expected = `${checked.identity.runId}:${epoch}\n`;
  const deadline = Date.now() + timeoutMs;
  while (Date.now() <= deadline) {
    if (abort?.aborted) throw cancelledFinalClose(epoch);
    const change = directoryChange(join(checked.directory, 'close'), deadline, abort);
    try {
      const actual = await readFile(path, 'utf8');
      change.close();
      if (actual !== expected) throw new Error('persistent matrix final close token is foreign or malformed');
      return Object.freeze({ epoch, token: expected.trim(), path });
    } catch (error) {
      if (error?.code !== 'ENOENT') { change.close(); throw error; }
    }
    if (!await change.wait) break;
  }
  if (abort?.aborted) throw cancelledFinalClose(epoch);
  throw new Error(`persistent matrix final close for epoch ${epoch} was not acknowledged within ${timeoutMs}ms`);
}

export async function readPersistentMatrixResult(session, epoch) {
  const checked = requireSession(session);
  if (!Number.isInteger(epoch) || epoch < 0 || epoch >= checked.plan.segments.length) throw new Error('persistent matrix result epoch is invalid');
  const path = join(checked.directory, 'results', `${String(epoch).padStart(4, '0')}.json`);
  return validatePersistentMatrixResult(checked, epoch, JSON.parse(await readFile(path, 'utf8')));
}

/**
 * Publishes one terminal result as a filesystem commit.  The exact result name does not appear
 * until a complete, synced temporary file has been linked into place, so an observing supervisor
 * never interprets a write-in-progress as malformed terminal evidence.
 */
export async function publishPersistentMatrixResult(session, epoch, value) {
  const checked = requireSession(session);
  if (!Number.isInteger(epoch) || epoch < 0 || epoch >= checked.plan.segments.length) throw new Error('persistent matrix result epoch is invalid');
  const result = validatePersistentMatrixResult(checked, epoch, value);
  const path = join(checked.directory, 'results', `${String(epoch).padStart(4, '0')}.json`);
  await publishAtomicExclusive(path, `${JSON.stringify(result)}\n`, 'terminal result');
  return result;
}

export async function awaitPersistentMatrixResult(session, epoch, timeoutMs, cancellation = undefined) {
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 300_000) throw new Error('persistent matrix result timeout is invalid');
  const checked = requireSession(session);
  const abort = cancellationSignal(cancellation);
  const deadline = Date.now() + timeoutMs;
  while (Date.now() <= deadline) {
    if (abort?.aborted) throw cancelledResult(epoch);
    const change = directoryChange(join(checked.directory, 'results'), deadline, abort);
    try {
      const result = await readPersistentMatrixResult(session, epoch);
      change.close();
      return result;
    }
    catch (error) {
      if (!String(error?.code ?? '').includes('ENOENT')) {
        if (!String(error?.message ?? error).includes('ENOENT')) { change.close(); throw error; }
      }
    }
    if (!await change.wait) break;
  }
  if (abort?.aborted) throw cancelledResult(epoch);
  throw new Error(`persistent matrix segment ${epoch} did not produce a terminal result within ${timeoutMs}ms`);
}

/**
 * Fails closed unless one prepared visible client and the exact planned server cycles produced
 * the whole matrix.  This is deliberately separate from the filesystem readers: callers first
 * authenticate every lifecycle/result record, then this function checks their semantic shape.
 */
export function validatePersistentMatrixEvidence(plan, evidence) {
  const checked = validatePersistentMatrixPlan(plan);
  if (!evidence || !Number.isInteger(evidence.clientPid) || evidence.clientPid <= 1
      || !Array.isArray(evidence.events) || !Array.isArray(evidence.results) || !Array.isArray(evidence.serverRuns) || !Array.isArray(evidence.crashReceipts)) {
    throw new Error('persistent matrix evidence is malformed');
  }
  const terminalSegments = checked.segments.filter((segment) => segment.completion !== 'expected_crash');
  if (evidence.results.length !== terminalSegments.length || evidence.serverRuns.length !== checked.segments.length) {
    throw new Error('persistent matrix evidence has incomplete segment coverage');
  }
  const runs = evidence.serverRuns.map((run, epoch) => {
    const segment = checked.segments[epoch];
    if (!run || run.segment !== segment.id || run.worldKey !== segment.worldKey || !token(run.serverRunId)
        || !Number.isInteger(run.serverPid) || run.serverPid <= 1) {
      throw new Error('persistent matrix server evidence is foreign or malformed');
    }
    return run;
  });
  for (const [epoch, run] of runs.entries()) {
    const segment = checked.segments[epoch];
    if (!segment.reuseServer) {
      if (runs.slice(0, epoch).some((prior) => prior.serverRunId === run.serverRunId || prior.serverPid === run.serverPid)) {
        throw new Error('persistent matrix reuses a server run identity outside a declared compatible case');
      }
      continue;
    }
    const predecessor = runs[epoch - 1]; const prior = checked.segments[epoch - 1];
    if (!predecessor || !prior || prior.completion === 'expected_crash' || predecessor.worldKey !== run.worldKey
        || predecessor.serverRunId !== run.serverRunId || predecessor.serverPid !== run.serverPid) {
      throw new Error('persistent matrix compatible case does not retain its exact predecessor server');
    }
  }
  const crashSegments = checked.segments.map((segment, epoch) => ({ segment, epoch })).filter(({ segment }) => segment.completion === 'expected_crash');
  if (evidence.crashReceipts.length !== crashSegments.length) throw new Error('persistent matrix crash receipt coverage is incomplete');
  for (const [{ segment, epoch }, receipt] of crashSegments.map((expected, index) => [expected, evidence.crashReceipts[index]])) {
    const successor = checked.segments[epoch + 1]; const current = runs[epoch]; const next = runs[epoch + 1];
    if (!receipt || receipt.epoch !== epoch || !receipt.arm || !receipt.proof || !receipt.release || !successor || !next
        || receipt.arm.epoch !== epoch || receipt.arm.serverRunId !== current.serverRunId || receipt.arm.serverPid !== current.serverPid
        || receipt.arm.clientPid !== evidence.clientPid || receipt.arm.port !== evidence.port
        || receipt.arm.identity?.workerId !== checked.workerId || receipt.arm.identity?.buildIdentitySha256 !== checked.buildIdentitySha256
        || !matchesCrashDescriptor(receipt.arm.descriptor, segment)) {
      throw new Error('persistent matrix crash receipt is foreign or incomplete');
    }
    const proof = validatePersistentExpectedCrashProof(receipt.arm, receipt.proof);
    validatePersistentExpectedCrashRelease(receipt.release, { arm: receipt.arm, proof, successor: {
      epoch: epoch + 1, segment: successor.id, scenarioSha256: successor.scenarioSha256, worldKey: successor.worldKey,
      serverRunId: next.serverRunId, serverPid: next.serverPid, ready: true
    } });
    if (expectedCrashPrefix(current, segment) !== proof.clientLoss.completedActionSteps) {
      throw new Error('persistent matrix crash receipt prefix diverges from lifecycle evidence');
    }
  }
  const terminalEpochs = new Set(checked.segments.map((segment, epoch) => segment.completion === 'expected_crash' ? undefined : epoch).filter((epoch) => epoch !== undefined));
  const seenTerminalEpochs = new Set();
  for (const result of evidence.results) {
    const segment = checked.segments[result?.epoch];
    if (!segment || segment.completion === 'expected_crash' || !result || result.status !== 'ok' || !Number.isInteger(result.epoch) || result.segment !== segment.id
        || result.scenarioSha256 !== segment.scenarioSha256 || !terminalEpochs.has(result.epoch) || seenTerminalEpochs.has(result.epoch) || !result.terminal
        || !Number.isInteger(result.terminal.assertionCount) || result.terminal.assertionCount < 1) {
      throw new Error('persistent matrix terminal evidence is missing or stale');
    }
    seenTerminalEpochs.add(result.epoch);
  }
  if (seenTerminalEpochs.size !== terminalEpochs.size) throw new Error('persistent matrix terminal evidence is incomplete');
  const expected = expectedLifecycle(checked, runs, evidence.clientPid, evidence.port);
  if (evidence.events.length !== expected.length) throw new Error('persistent matrix lifecycle coverage is incomplete or duplicated');
  for (const [index, requirement] of expected.entries()) {
    const actual = evidence.events[index];
    if (!actual || actual.barrier !== requirement.barrier || !matches(actual.detail, requirement.detail)) {
      throw new Error(`persistent matrix lifecycle diverges at event ${index + 1}`);
    }
  }
  return Object.freeze({ schema: PERSISTENT_MATRIX_SCHEMA, kind: 'frontier-v3-persistent-matrix-evidence',
    clientPid: evidence.clientPid, serverRunIds: Object.freeze(runs.map((run) => run.serverRunId)),
    worldKeys: Object.freeze([...new Set(checked.segments.map((segment) => segment.worldKey))]),
    segmentCount: checked.segments.length, crashReceiptCount: crashSegments.length, lifecycleEventCount: expected.length });
}

export function sha256Json(value) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }

function requireSession(value) {
  if (!value || typeof value.directory !== 'string' || !value.identity || !value.plan) throw new Error('persistent matrix session is malformed');
  return Object.freeze({ directory: resolve(value.directory), identity: value.identity, plan: validatePersistentMatrixPlan(value.plan) });
}
function requireFinalEpoch(checked, epoch) {
  if (!Number.isInteger(epoch) || epoch < 0 || epoch >= checked.plan.segments.length || !checked.plan.segments[epoch].final) {
    throw new Error('persistent matrix final close epoch is invalid');
  }
  return checked.plan.segments[epoch];
}
function validatePersistentMatrixResult(checked, epoch, value) {
  if (checked.plan.segments[epoch].completion === 'expected_crash') throw new Error('persistent expected crash segment cannot publish terminal result');
  if (!value || value.schema !== PERSISTENT_MATRIX_SCHEMA || value.kind !== 'frontier-v3-pilot-matrix-result'
      || value.runId !== checked.identity.runId || value.epoch !== epoch || value.segment !== checked.plan.segments[epoch].id
      || value.scenarioSha256 !== checked.plan.segments[epoch].scenarioSha256 || value.status !== 'ok'
      || !value.terminal || !Number.isInteger(value.terminal.assertionCount) || value.terminal.assertionCount < 1) {
    throw new Error('persistent matrix result is malformed, foreign or stale');
  }
  return Object.freeze({ schema: PERSISTENT_MATRIX_SCHEMA, kind: 'frontier-v3-pilot-matrix-result',
    runId: value.runId, epoch: value.epoch, segment: value.segment, scenarioSha256: value.scenarioSha256,
    status: 'ok', terminal: Object.freeze({ ...value.terminal }) });
}
async function publishAtomicExclusive(path, contents, label) {
  const temporary = `${path}.${randomUUID()}.partial`;
  let handle;
  try {
    handle = await open(temporary, 'wx', 0o600);
    await handle.writeFile(contents, 'utf8');
    await handle.sync();
    await handle.close();
    handle = undefined;
    await link(temporary, path);
  } catch (error) {
    if (error?.code === 'EEXIST') throw new Error(`persistent matrix ${label} already exists: ${path}`);
    throw error;
  } finally {
    if (handle !== undefined) await handle.close();
    await unlink(temporary).catch((error) => { if (error?.code !== 'ENOENT') throw error; });
  }
}
function expectedLifecycle(plan, runs, clientPid, port) {
  if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('persistent matrix evidence port is invalid');
  const events = [
    event(LifecycleBarrier.SERVER_RUN_READY, { serverRunId: runs[0].serverRunId, serverPid: runs[0].serverPid, segment: plan.segments[0].id }),
    event(LifecycleBarrier.PREPARED_CLIENT_READY, { clientPid }),
    event(LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY, { clientPid, segment: plan.segments[0].id })
  ];
  for (const [epoch, segment] of plan.segments.entries()) {
    const completed = segment.completion === 'expected_crash' ? expectedCrashPrefix(runs[epoch], segment) : actionCount(segment);
    for (let action = 1; action <= completed; action++) {
      events.push(event(LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, { actionStep: action, segment: segment.id }));
    }
    if (segment.completion === 'expected_crash') {
      const current = runs[epoch]; const next = runs[epoch + 1];
      if (!next) throw new Error('persistent matrix expected crash has no recovery successor');
      events.push(event(LifecycleBarrier.EXPECTED_LOSS_ARMED, { segment: segment.id }));
      events.push(event(LifecycleBarrier.CRASH_CONTROLLER_FIRED, { serverPid: current.serverPid, segment: segment.id }));
      events.push(event(LifecycleBarrier.OWNED_SERVER_EXIT, { serverPid: current.serverPid, segment: segment.id }));
      events.push(event(LifecycleBarrier.CLIENT_EXPECTED_LOSS, { clientPid, segment: segment.id }));
      events.push(event(LifecycleBarrier.GAME_PORT_CLOSED, { port, serverRunId: current.serverRunId, segment: segment.id }));
      events.push(event(LifecycleBarrier.RECOVERY_SERVER_READY, { serverRunId: next.serverRunId, serverPid: next.serverPid, segment: next.segment }));
      events.push(event(LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, { clientPid, segment: next.segment }));
      continue;
    }
    events.push(event(LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, { segment: segment.id }));
    if (!segment.final) {
      const current = runs[epoch]; const next = runs[epoch + 1];
      events.push(event(LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: segment.id }));
      events.push(event(LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, { serverRunId: current.serverRunId, segment: segment.id }));
      if (plan.segments[epoch + 1].reuseServer) {
        events.push(event(LifecycleBarrier.SAME_SERVER_RESET_ACKNOWLEDGED, { serverRunId: current.serverRunId, segment: next.segment }));
        events.push(event(LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, { clientPid, segment: next.segment }));
      } else {
        events.push(event(LifecycleBarrier.DURABLE_SERVER_SAVE, { serverRunId: current.serverRunId, segment: segment.id }));
        events.push(event(LifecycleBarrier.GAME_PORT_CLOSED, { port, serverRunId: current.serverRunId, segment: segment.id }));
        events.push(event(LifecycleBarrier.RECOVERY_SERVER_READY, { serverRunId: next.serverRunId, serverPid: next.serverPid, segment: next.segment }));
        events.push(event(LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED, { clientPid, segment: next.segment }));
      }
    } else {
      // The final departure is also a normal, typed disconnect. It happens only after the
      // supervisor has authenticated its terminal result and published the close token.
      events.push(event(LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, { segment: segment.id }));
      events.push(event(LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, { serverRunId: runs[epoch].serverRunId, segment: segment.id }));
    }
  }
  events.push(event(LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE, { assertionCount: plan.segments.length }));
  return events;
}
function actionCount(segment) {
  if (!Number.isInteger(segment.actionCount) || segment.actionCount < 1 || segment.actionCount > 64) {
    throw new Error('persistent matrix segment action count is unavailable');
  }
  return segment.actionCount;
}
function expectedCrashPrefix(run, segment) {
  if (!Number.isInteger(run?.completedActionSteps) || run.completedActionSteps < 0 || run.completedActionSteps > actionCount(segment)) {
    throw new Error('persistent expected crash evidence lacks its completed action prefix');
  }
  return run.completedActionSteps;
}
function matchesCrashDescriptor(actual, segment) {
  const declaration = segment.expectedCrash;
  return actual && declaration && actual.completion === 'expected_crash' && actual.segment === segment.id
    && actual.scenarioId === segment.scenarioId && actual.scenarioSha256 === segment.scenarioSha256 && actual.worldKey === segment.worldKey
    && actual.laneId === declaration.laneId && actual.boundary === declaration.boundary && actual.owner === declaration.owner
    && actual.payloadType === declaration.payloadType && actual.expectedRevision === declaration.expectedRevision
    && actual.expectedAuthorityEpoch === declaration.expectedAuthorityEpoch;
}
function event(barrier, detail) { return Object.freeze({ barrier, detail: Object.freeze(detail) }); }
function matches(actual, expected) {
  return actual && Object.keys(actual).length === Object.keys(expected).length
    && Object.entries(expected).every(([key, value]) => actual[key] === value);
}
async function writeExclusive(path, contents) {
  try { await writeFile(path, contents, { encoding: 'utf8', flag: 'wx' }); }
  catch (error) { if (error?.code === 'EEXIST') throw new Error('persistent matrix control entry already exists'); throw error; }
}
function cancellationSignal(value) {
  if (value === undefined) return undefined;
  if (!value || typeof value !== 'object' || typeof value.aborted !== 'boolean'
      || typeof value.addEventListener !== 'function' || typeof value.removeEventListener !== 'function') {
    throw new Error('persistent matrix cancellation is malformed');
  }
  return value;
}
function cancelledResult(epoch) { return new Error(`persistent matrix segment ${epoch} result wait was cancelled`); }
function cancelledFinalClose(epoch) { return new Error(`persistent matrix final close for epoch ${epoch} was cancelled`); }
function token(value) { return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(value); }
function exactFields(value, fields, label) { if (Object.keys(value).length !== fields.length || Object.keys(value).some((field) => !fields.includes(field))) throw new Error(`${label} has unknown or missing fields`); }
function expectedCrash(value) { return value && token(value.laneId) && token(value.boundary) && token(value.owner) && token(value.payloadType)
  && ((Number.isSafeInteger(value.expectedRevision) && value.expectedRevision >= 0) || value.expectedRevision === 'observed_at_boundary')
  && (value.expectedAuthorityEpoch === undefined || (Number.isSafeInteger(value.expectedAuthorityEpoch) && value.expectedAuthorityEpoch >= 0)); }
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function directoryChange(directory, deadline, abort = undefined) {
  let watcher; let timer; let abortListener = () => {}; let settled = false; let resolveWait; let rejectWait;
  const cleanup = () => {
    clearTimeout(timer);
    watcher?.close();
    abort?.removeEventListener('abort', abortListener);
  };
  const wait = new Promise((resolveWaiter, rejectWaiter) => { resolveWait = resolveWaiter; rejectWait = rejectWaiter; });
  const finish = (value) => {
    if (settled) return;
    settled = true;
    cleanup();
    resolveWait(value);
  };
  try {
    watcher = watch(directory, { persistent: false }, () => finish(true));
    watcher.once('error', (error) => {
      if (settled) return;
      settled = true;
      cleanup();
      rejectWait(new Error(`persistent matrix result watch failed: ${String(error?.message ?? error)}`));
    });
  } catch (error) {
    settled = true;
    rejectWait(new Error(`persistent matrix result watch failed: ${String(error?.message ?? error)}`));
  }
  const remaining = deadline - Date.now();
  if (remaining <= 0) finish(false);
  else timer = setTimeout(() => finish(false), remaining);
  abortListener = () => finish(false);
  if (abort?.aborted) finish(false);
  else abort?.addEventListener('abort', abortListener, { once: true });
  return Object.freeze({ wait, close: () => finish(false) });
}
