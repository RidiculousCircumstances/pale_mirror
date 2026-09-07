import { randomUUID } from 'node:crypto';
import { watch } from 'node:fs';
import { link, mkdir, readFile, readdir, rm, rmdir, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';

/**
 * Versioned, external control protocol for a single native pilot lifecycle.
 *
 * This journal has no simulation authority: it carries only exact acknowledgements between the
 * test supervisor and its one nonce-owned client/server pair.  The immutable identity is copied
 * into every record, so a stale filesystem marker cannot be mistaken for an acknowledgement from
 * a replacement JVM or another CI worker.
 */
export const LIFECYCLE_BARRIER_SCHEMA = 1;
export const LifecycleBarrier = Object.freeze({
  PREPARED_CLIENT_READY: 'prepared_client_ready',
  SERVER_RUN_READY: 'server_run_ready',
  CLIENT_CONNECTED_FIXTURE_READY: 'client_connected_fixture_ready',
  ACTION_CHECKPOINT_ACKNOWLEDGED: 'action_checkpoint_acknowledged',
  SCENARIO_SEGMENT_COMPLETE: 'scenario_segment_complete',
  EXPECTED_LOSS_ARMED: 'expected_loss_armed',
  CRASH_CONTROLLER_FIRED: 'crash_controller_fired',
  OWNED_SERVER_EXIT: 'owned_server_exit',
  CLIENT_EXPECTED_LOSS: 'client_expected_loss',
  CLIENT_NORMALLY_DISCONNECTED: 'client_normally_disconnected',
  NORMAL_DEMAND_LOSS_RELEASE: 'normal_demand_loss_release',
  SAME_SERVER_RESET_ACKNOWLEDGED: 'same_server_reset_acknowledged',
  DURABLE_SERVER_SAVE: 'durable_server_save',
  GAME_PORT_CLOSED: 'game_port_closed',
  RECOVERY_SERVER_READY: 'recovery_server_ready',
  SAME_CLIENT_RECONNECTED_STATE_CLEARED: 'same_client_reconnected_state_cleared',
  TERMINAL_ASSERTION_COMPLETE: 'terminal_assertion_complete'
});

/**
 * Typed acknowledgements emitted by the development-only Minecraft pilot.
 *
 * They deliberately live beside, rather than inside, the supervisor journal:
 * Minecraft must not choose journal ordering or write a supervisor event on
 * behalf of another JVM.  The supervisor validates this exact identity and
 * then appends the corresponding monotonic barrier itself.
 */
export const LifecycleSignal = Object.freeze({
  PREPARED_CLIENT_READY: 'prepared_client_ready',
  SERVER_RUN_READY: 'server_run_ready',
  DURABLE_SERVER_SAVE: 'durable_server_save',
  CLIENT_CONNECTED_FIXTURE_READY: 'client_connected_fixture_ready',
  ACTION_CHECKPOINT: 'action_checkpoint',
  SCENARIO_SEGMENT_COMPLETE: 'scenario_segment_complete',
  CRASH_BOUNDARY_OBSERVED: 'crash_boundary_observed',
  EXPECTED_LOSS_ARMED: 'expected_loss_armed',
  CLIENT_EXPECTED_LOSS: 'client_expected_loss',
  CLIENT_NORMALLY_DISCONNECTED: 'client_normally_disconnected',
  NORMAL_DEMAND_LOSS_RELEASE: 'normal_demand_loss_release',
  SAME_CLIENT_RECONNECTED_STATE_CLEARED: 'same_client_reconnected_state_cleared'
});

const KNOWN = new Set(Object.values(LifecycleBarrier));
const INITIAL = new Set([LifecycleBarrier.PREPARED_CLIENT_READY, LifecycleBarrier.SERVER_RUN_READY]);
// A recovery may retain one persistent client or start one exact replacement client. These
// barriers may occur once for each immutable server segment, but never twice with the same exact
// detail. The matrix plan additionally checks the exact bounded number of cycles; this generic
// journal only admits a causal transition, it never permits a replacement acknowledgement to
// overwrite one.
const REPEATABLE = new Set([
  LifecycleBarrier.PREPARED_CLIENT_READY,
  LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY,
  LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED,
  LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE,
  LifecycleBarrier.EXPECTED_LOSS_ARMED,
  LifecycleBarrier.CRASH_CONTROLLER_FIRED,
  LifecycleBarrier.OWNED_SERVER_EXIT,
  LifecycleBarrier.CLIENT_EXPECTED_LOSS,
  LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED,
  LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE,
  LifecycleBarrier.SAME_SERVER_RESET_ACKNOWLEDGED,
  LifecycleBarrier.DURABLE_SERVER_SAVE,
  LifecycleBarrier.GAME_PORT_CLOSED,
  LifecycleBarrier.RECOVERY_SERVER_READY,
  LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED
]);
const NEXT = Object.freeze({
  [LifecycleBarrier.PREPARED_CLIENT_READY]: new Set([LifecycleBarrier.SERVER_RUN_READY, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY]),
  [LifecycleBarrier.SERVER_RUN_READY]: new Set([LifecycleBarrier.PREPARED_CLIENT_READY, LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY]),
  [LifecycleBarrier.CLIENT_CONNECTED_FIXTURE_READY]: new Set([LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, LifecycleBarrier.EXPECTED_LOSS_ARMED]),
  [LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED]: new Set([LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE, LifecycleBarrier.EXPECTED_LOSS_ARMED, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE]),
  [LifecycleBarrier.EXPECTED_LOSS_ARMED]: new Set([LifecycleBarrier.CRASH_CONTROLLER_FIRED]),
  [LifecycleBarrier.CRASH_CONTROLLER_FIRED]: new Set([LifecycleBarrier.OWNED_SERVER_EXIT]),
  [LifecycleBarrier.OWNED_SERVER_EXIT]: new Set([LifecycleBarrier.CLIENT_EXPECTED_LOSS]),
  [LifecycleBarrier.CLIENT_EXPECTED_LOSS]: new Set([LifecycleBarrier.GAME_PORT_CLOSED]),
  // A release boundary is server-owned: the ordinary client can finish and disconnect before
  // the server durably releases its HOT lease.  That exact completed segment may therefore arm
  // the nonce-bound crash controller, but cannot otherwise skip its expected-loss sequence.
  [LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE]: new Set([LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED, LifecycleBarrier.EXPECTED_LOSS_ARMED, LifecycleBarrier.DURABLE_SERVER_SAVE, LifecycleBarrier.GAME_PORT_CLOSED, LifecycleBarrier.RECOVERY_SERVER_READY, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE]),
  [LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED]: new Set([LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE, LifecycleBarrier.DURABLE_SERVER_SAVE, LifecycleBarrier.GAME_PORT_CLOSED, LifecycleBarrier.RECOVERY_SERVER_READY, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE]),
  [LifecycleBarrier.NORMAL_DEMAND_LOSS_RELEASE]: new Set([LifecycleBarrier.SAME_SERVER_RESET_ACKNOWLEDGED, LifecycleBarrier.DURABLE_SERVER_SAVE, LifecycleBarrier.GAME_PORT_CLOSED, LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE]),
  [LifecycleBarrier.SAME_SERVER_RESET_ACKNOWLEDGED]: new Set([LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED]),
  [LifecycleBarrier.DURABLE_SERVER_SAVE]: new Set([LifecycleBarrier.GAME_PORT_CLOSED]),
  [LifecycleBarrier.GAME_PORT_CLOSED]: new Set([LifecycleBarrier.RECOVERY_SERVER_READY]),
  [LifecycleBarrier.RECOVERY_SERVER_READY]: new Set([
    LifecycleBarrier.PREPARED_CLIENT_READY,
    LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED
  ]),
  [LifecycleBarrier.SAME_CLIENT_RECONNECTED_STATE_CLEARED]: new Set([LifecycleBarrier.ACTION_CHECKPOINT_ACKNOWLEDGED, LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE]),
  [LifecycleBarrier.TERMINAL_ASSERTION_COMPLETE]: new Set([LifecycleBarrier.CLIENT_NORMALLY_DISCONNECTED])
});

export async function createLifecycleBarrierSession(root, identity) {
  const checked = validateIdentity(identity);
  const parent = resolve(root);
  const directory = resolve(parent, checked.sessionId);
  // A clean adopted checkout legitimately has no generated scenario root yet.  Create only
  // that declared parent, while retaining the non-recursive session creation as the collision
  // fence for one exact lifecycle identity.
  await mkdir(parent, { recursive: true });
  await mkdir(directory, { recursive: false });
  await mkdir(eventsDirectory(directory));
  await mkdir(signalsDirectory(directory));
  await mkdir(stagingDirectory(directory));
  await writeImmutableExclusive(stagingDirectory(directory), join(directory, 'identity.json'), `${JSON.stringify(checked)}\n`);
  return Object.freeze({ directory, identity: checked });
}

export async function openLifecycleBarrierSession(directory, expectedIdentity) {
  const root = resolve(directory);
  const actual = JSON.parse(await readFile(join(root, 'identity.json'), 'utf8'));
  const expected = validateIdentity(expectedIdentity);
  if (stable(actual) !== stable(expected)) throw new Error('lifecycle barrier identity is foreign or stale');
  return Object.freeze({ directory: root, identity: expected });
}

export async function publishLifecycleBarrier(session, barrier, detail = {}) {
  const checked = requireSession(session); requireBarrier(barrier); validateDetail(detail);
  const lock = await acquireJournalLock(checked.directory);
  try {
    const events = await readLifecycleBarriers(checked);
    const previous = events.at(-1)?.barrier;
    if (duplicateBarrier(events, barrier, detail)) {
      throw new Error(`duplicate lifecycle barrier: ${barrier}`);
    }
    if (previous === undefined) {
      if (!INITIAL.has(barrier)) throw new Error(`lifecycle barrier ${barrier} cannot start a session`);
    } else if (!NEXT[previous].has(barrier)) {
      throw new Error(`lifecycle barrier ${barrier} is out of order after ${previous}`);
    }
    const sequence = events.length + 1;
    const event = Object.freeze({ schema: LIFECYCLE_BARRIER_SCHEMA, sequence, barrier, identity: checked.identity,
      detail: checkedDetail(detail) });
    const target = join(eventsDirectory(checked.directory), `${String(sequence).padStart(4, '0')}-${barrier}.json`);
    await writeImmutableExclusive(stagingDirectory(checked.directory), target, `${JSON.stringify(event)}\n`);
    return event;
  } finally {
    await lock.release();
  }
}

export async function readLifecycleBarriers(session) {
  const checked = requireSession(session);
  let names;
  try { names = await readdir(eventsDirectory(checked.directory)); }
  catch (error) { throw new Error(`lifecycle barrier journal is unavailable: ${String(error?.message ?? error)}`); }
  const eventNames = names.filter((name) => /^\d{4}-[a-z_]+\.json$/.test(name)).sort();
  if (eventNames.length !== names.length) throw new Error('lifecycle barrier journal contains an unknown entry');
  const events = [];
  for (const name of eventNames) {
    const value = JSON.parse(await readFile(join(eventsDirectory(checked.directory), name), 'utf8'));
    validateEvent(value, checked.identity, events.length + 1);
    const expectedName = `${String(value.sequence).padStart(4, '0')}-${value.barrier}.json`;
    if (name !== expectedName) throw new Error('lifecycle barrier filename does not match its payload');
    const previous = events.at(-1)?.barrier;
    if (previous === undefined ? !INITIAL.has(value.barrier) : !NEXT[previous].has(value.barrier)) {
      throw new Error('lifecycle barrier journal is out of order');
    }
    if (duplicateBarrier(events, value.barrier, value.detail)) {
      throw new Error('lifecycle barrier journal contains a duplicate acknowledgement');
    }
    events.push(Object.freeze(value));
  }
  return Object.freeze(events);
}

/** Bounded waiter for one exact acknowledgement. Absence is failure, never inferred progress. */
export async function awaitLifecycleBarrier(session, barrier, timeoutMs, predicate = () => true) {
  requireBarrier(barrier);
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 300_000) throw new Error('lifecycle barrier timeout must be 1..300000ms');
  if (typeof predicate !== 'function') throw new Error('lifecycle barrier predicate must be a function');
  const deadline = Date.now() + timeoutMs;
  while (Date.now() <= deadline) {
    // Subscribe before reading so an acknowledgement between the previous read and the
    // subscription cannot be lost.  A lifecycle protocol is event-driven; a 25ms polling
    // delay would both obscure the causal boundary and add avoidable matrix latency.
    const change = directoryChange(eventsDirectory(requireSession(session).directory), deadline);
    const event = (await readLifecycleBarriers(session)).find((entry) => entry.barrier === barrier && predicate(entry));
    if (event !== undefined) { change.close(); return event; }
    if (!await change.wait) break;
  }
  throw new Error(`lifecycle barrier ${barrier} was not acknowledged within ${timeoutMs}ms`);
}

/**
 * Returns the one client JVM that a lifecycle journal has already bound to a
 * declared segment.  A supervisor may use this only for its own expected-loss
 * cleanup; a Node wrapper PID, a later replacement client, or an untyped
 * journal entry is never an eligible target.
 */
export function exactLifecycleClientPid(entry, segment) {
  requireSignalSuffix(segment);
  if (!entry || entry.barrier !== LifecycleBarrier.PREPARED_CLIENT_READY
      || !entry.detail || entry.detail.segment !== segment
      || !Number.isSafeInteger(entry.detail.clientPid) || entry.detail.clientPid <= 1) {
    throw new Error('lifecycle prepared client acknowledgement is foreign, stale or malformed');
  }
  return entry.detail.clientPid;
}

/**
 * Admits a clean pilot exit before a server-side crash rendezvous only after
 * the exact client segment has durably acknowledged completion. The server may
 * still need to complete an observer-free hysteresis before its own boundary.
 */
export function exactLifecycleCompletedSegment(entry, segment) {
  requireSignalSuffix(segment);
  if (!entry || entry.barrier !== LifecycleBarrier.SCENARIO_SEGMENT_COMPLETE
      || !entry.detail || entry.detail.segment !== segment) {
    throw new Error('lifecycle completed segment acknowledgement is foreign, stale or malformed');
  }
  return entry;
}

/**
 * Waits for one immutable, identity-bound pilot acknowledgement.  `suffix`
 * differentiates repeated checkpoints and replacement-server readiness while
 * retaining the semantic signal vocabulary in every payload.
 */
export async function awaitLifecycleSignal(session, signal, suffix, timeoutMs, cancellation = undefined) {
  const checked = requireSession(session); requireSignal(signal); requireSignalSuffix(suffix);
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 300_000) throw new Error('lifecycle signal timeout must be 1..300000ms');
  const abort = cancellationSignal(cancellation);
  const target = join(signalsDirectory(checked.directory), `${signal}-${suffix}.json`);
  const deadline = Date.now() + timeoutMs;
  while (Date.now() <= deadline) {
    if (abort?.aborted) throw cancelledSignal(signal, suffix);
    const change = directoryChange(signalsDirectory(checked.directory), deadline, abort);
    try {
      const value = JSON.parse(await readFile(target, 'utf8'));
      validateSignal(value, checked.identity, signal, suffix);
      change.close();
      return Object.freeze(value);
    } catch (error) {
      if (error?.code !== 'ENOENT') { change.close(); throw error; }
    }
    if (!await change.wait) break;
  }
  if (abort?.aborted) throw cancelledSignal(signal, suffix);
  throw new Error(`lifecycle signal ${signal}/${suffix} was not acknowledged within ${timeoutMs}ms`);
}

export function newLifecycleIdentity({ buildIdentitySha256, workerId, runId, scenarioId, segmentId, nonce = randomUUID(), sessionId = randomUUID() }) {
  return validateIdentity({ schema: LIFECYCLE_BARRIER_SCHEMA, buildIdentitySha256, workerId, runId, scenarioId, segmentId, nonce, sessionId });
}

function validateIdentity(value) {
  if (!value || value.schema !== LIFECYCLE_BARRIER_SCHEMA || !sha256(value.buildIdentitySha256)
      || !token(value.workerId) || !uuid(value.runId) || !token(value.scenarioId) || !token(value.segmentId)
      || !uuid(value.nonce) || !uuid(value.sessionId)) {
    throw new Error('lifecycle barrier identity is malformed');
  }
  return Object.freeze({ schema: value.schema, buildIdentitySha256: value.buildIdentitySha256, workerId: value.workerId,
    runId: value.runId, scenarioId: value.scenarioId, segmentId: value.segmentId, nonce: value.nonce, sessionId: value.sessionId });
}

function validateEvent(value, identity, sequence) {
  if (!value || value.schema !== LIFECYCLE_BARRIER_SCHEMA || value.sequence !== sequence || !KNOWN.has(value.barrier)
      || stable(value.identity) !== stable(identity)) throw new Error('lifecycle barrier event is malformed, foreign or stale');
  validateDetail(value.detail);
}

function validateSignal(value, identity, signal, suffix) {
  if (!value || value.schema !== LIFECYCLE_BARRIER_SCHEMA || value.signal !== signal || value.suffix !== suffix
      || stable(value.identity) !== stable(identity)) {
    throw new Error('lifecycle signal is malformed, foreign or stale');
  }
  validateDetail(value.detail);
}

function validateDetail(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('lifecycle barrier detail must be an object');
  for (const [key, entry] of Object.entries(value)) {
    if (!/^[a-z][A-Za-z0-9]{0,63}$/.test(key) || entry === undefined || (entry !== null && !['string', 'number', 'boolean'].includes(typeof entry))) {
      throw new Error('lifecycle barrier detail is malformed');
    }
  }
}

function duplicateBarrier(events, barrier, detail) {
  if (!REPEATABLE.has(barrier)) return events.some((event) => event.barrier === barrier);
  const exactDetail = stable(checkedDetail(detail));
  return events.some((event) => event.barrier === barrier && stable(event.detail) === exactDetail);
}

function checkedDetail(value) { return Object.freeze(Object.fromEntries(Object.entries(value).sort(([left], [right]) => left.localeCompare(right)))); }
function requireSession(value) {
  if (!value || typeof value.directory !== 'string') throw new Error('lifecycle barrier session is malformed');
  return Object.freeze({ directory: resolve(value.directory), identity: validateIdentity(value.identity) });
}
function requireBarrier(value) { if (!KNOWN.has(value)) throw new Error(`unknown lifecycle barrier: ${value}`); }
function requireSignal(value) { if (!Object.values(LifecycleSignal).includes(value)) throw new Error(`unknown lifecycle signal: ${value}`); }
function requireSignalSuffix(value) { if (typeof value !== 'string' || !/^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}$/.test(value)) throw new Error('lifecycle signal suffix is malformed'); }
function cancellationSignal(value) {
  if (value === undefined) return undefined;
  if (!value || typeof value !== 'object' || typeof value.aborted !== 'boolean'
      || typeof value.addEventListener !== 'function' || typeof value.removeEventListener !== 'function') {
    throw new Error('lifecycle signal cancellation is malformed');
  }
  return value;
}
function cancelledSignal(signal, suffix) { return new Error(`lifecycle signal ${signal}/${suffix} was cancelled`); }
function eventsDirectory(directory) { return join(directory, 'events'); }
function signalsDirectory(directory) { return join(directory, 'signals'); }
function stagingDirectory(directory) { return join(directory, 'staging'); }
/**
 * One filesystem-change rendezvous with a deadline.  This is intentionally a protocol wait,
 * not a periodic coordination sleep.  The caller creates it before inspecting immutable
 * evidence, then closes it if that inspection already found the target.
 */
function directoryChange(directory, deadline, abort = undefined) {
  let watcher; let timer; let abortListener = () => {}; let settled = false; let resolveWait; let rejectWait;
  const cleanup = () => {
    clearTimeout(timer);
    watcher?.close();
    abort?.removeEventListener('abort', abortListener);
  };
  const close = () => {
    if (settled) return;
    settled = true;
    cleanup();
    resolveWait(false);
  };
  const wait = new Promise((resolve, reject) => { resolveWait = resolve; rejectWait = reject; });
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
      rejectWait(new Error(`lifecycle directory watch failed: ${String(error?.message ?? error)}`));
    });
  } catch (error) {
    settled = true;
    rejectWait(new Error(`lifecycle directory watch failed: ${String(error?.message ?? error)}`));
    return Object.freeze({ wait, close: () => undefined });
  }
  const remaining = deadline - Date.now();
  if (remaining <= 0) finish(false);
  else timer = setTimeout(() => finish(false), remaining);
  abortListener = () => close();
  if (abort?.aborted) close();
  else abort?.addEventListener('abort', abortListener, { once: true });
  return Object.freeze({ wait, close });
}
/** Publishes complete immutable evidence without exposing a partially written final name. */
async function writeImmutableExclusive(staging, target, contents) {
  const temporary = join(staging, `${randomUUID()}.pending`);
  try {
    await writeFile(temporary, contents, { encoding: 'utf8', flag: 'wx' });
    // `rename` may replace an existing final name. A hard link is one atomic no-replace
    // publication: readers see either no acknowledgement or a complete immutable inode.
    await link(temporary, target);
  } catch (error) {
    if (error?.code === 'EEXIST') throw new Error('lifecycle barrier acknowledgement already exists');
    throw error;
  } finally {
    await rm(temporary, { force: true });
  }
}

/** Serializes independently running supervisor processes before they assign journal sequence IDs. */
async function acquireJournalLock(directory) {
  const path = join(directory, 'journal.lock');
  const deadline = Date.now() + 300_000;
  while (true) {
    try {
      await mkdir(path);
      return Object.freeze({ release: async () => { await rmdir(path); } });
    } catch (error) {
      if (error?.code !== 'EEXIST') throw new Error(`lifecycle barrier journal lock is unavailable: ${String(error?.message ?? error)}`);
    }
    const change = directoryChange(directory, deadline);
    try {
      if (!await change.wait) throw new Error('lifecycle barrier journal lock was not released within 300000ms');
    } finally {
      change.close();
    }
  }
}
function stable(value) { return JSON.stringify(value); }
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function token(value) { return typeof value === 'string' && /^[a-zA-Z0-9][a-zA-Z0-9_.:-]{0,127}$/.test(value); }
function uuid(value) { return typeof value === 'string' && /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(value); }
