import { createHash, randomUUID } from 'node:crypto';
import { watch } from 'node:fs';
import { link, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { requestRconCommand } from './rcon.mjs';

const ARM_KIND = 'f02b-natural-demand-episode-arm';
const KIND = 'f02b-natural-demand-episode';
const ARM_ACK_KIND = 'f02b-natural-demand-episode-arm-ack';
const STOP_OUTCOME_KIND = 'f02b-natural-demand-stop-outcome';
const UUID = /^[a-f\d]{8}(?:-[a-f\d]{4}){3}-[a-f\d]{12}$/i;
export const NATURAL_DEMAND_STOP_COMMAND = 'pale_mirror_pilot_graceful_stop';

/** Arms one exact server before its ordinary pilot segment begins. */
export async function armNaturalDemandEpisode(lifecycle, server) {
  requireLifecycle(lifecycle); requireServer(server);
  const fact = { schema: 1, kind: ARM_KIND, identity: lifecycle.identity,
    serverRunId: server.serverRunId, serverPid: server.serverPid, stopAdmissionNonce: randomUUID() };
  const path = join(lifecycle.directory, `natural-demand-episode-arm-${server.serverRunId}.json`);
  await writeImmutable(lifecycle.directory, path, `${JSON.stringify(fact)}\n`);
  return Object.freeze({ ...fact, receipt: { path, sha256: sha256(JSON.stringify(fact)) } });
}

/** The actual runner may not begin its ordinary client segment until this server owns the arm. */
export async function runPilotWithNaturalDemandArm({ lifecycle, server, timeoutMs, runPilot }) {
  if (typeof runPilot !== 'function') throw new Error('natural-demand pilot callback is malformed');
  const arm = await armNaturalDemandEpisode(lifecycle, server);
  await awaitNaturalDemandEpisodeArmed(lifecycle, server, arm, timeoutMs);
  await runPilot();
  return arm;
}

/** Reads the immutable server-thread acknowledgement; it is a launch barrier, not stop authority. */
export async function awaitNaturalDemandEpisodeArmed(lifecycle, server, arm, timeoutMs) {
  requireLifecycle(lifecycle); requireServer(server); requireArm(arm);
  const path = join(lifecycle.directory, 'signals', `natural-demand-episode-armed-${server.serverRunId}.json`);
  return awaitReceipt(path, timeoutMs, value => {
    requireReceipt(value, ARM_ACK_KIND, 'armed', lifecycle.identity, server, arm.stopAdmissionNonce);
    return Object.freeze(value);
  }, 'natural-demand arm was not acknowledged before client work');
}

/**
 * Waits only for server-owned coordination evidence.  This never grants stop authority: the
 * subsequently requested pilot command performs its own fresh in-memory admission.
 */
export async function awaitNaturalDemandEpisodeReady(lifecycle, server, timeoutMs) {
  requireLifecycle(lifecycle); requireServer(server);
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 300_000) throw new Error('natural-demand episode timeout is invalid');
  const signals = join(lifecycle.directory, 'signals');
  const eligible = join(signals, `natural-demand-episode-eligible-${server.serverRunId}.json`);
  const invalid = join(signals, `natural-demand-episode-invalid-${server.serverRunId}.json`);
  const deadline = Date.now() + timeoutMs;
  while (Date.now() <= deadline) {
    if (await exists(invalid)) throw new Error('natural-demand episode was invalidated before pilot stop admission');
    try {
      validateNaturalDemandEpisode(JSON.parse(await readFile(eligible, 'utf8')), lifecycle.identity, server);
      return;
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
    }
    await changed(signals, deadline);
  }
  throw new Error('natural-demand episode did not become eligible before pilot stop admission');
}

/** Formats the pilot-only command. The server command, not this supervisor, owns admission. */
export function naturalDemandStopCommand(arm) {
  if (!arm || !UUID.test(arm.stopAdmissionNonce ?? '')) throw new Error('natural-demand stop admission is malformed');
  return `${NATURAL_DEMAND_STOP_COMMAND} ${arm.stopAdmissionNonce}`;
}

/** The isolated recovery consumer has no stop-fence authority; it only transports the armed command. */
export async function requestPilotNaturalDemandStop(lifecycle, server, arm, timeoutMs) {
  requireLifecycle(lifecycle); requireServer(server); requireArm(arm);
  await requestRconCommand({ port: server.rconPort, password: server.rconPassword, command: naturalDemandStopCommand(arm) });
  const path = join(lifecycle.directory, 'signals', `natural-demand-stop-outcome-${server.serverRunId}-${arm.stopAdmissionNonce}.json`);
  return awaitReceipt(path, timeoutMs, value => {
    requireReceipt(value, STOP_OUTCOME_KIND, value?.status, lifecycle.identity, server, arm.stopAdmissionNonce);
    if (value.status === 'admitted') return Object.freeze(value);
    if (value.status === 'rejected' && typeof value.reason === 'string' && value.reason) {
      throw new Error(`natural-demand pilot stop was rejected: ${value.reason}`);
    }
    throw new Error('natural-demand pilot stop outcome is malformed');
  }, 'natural-demand pilot stop outcome was absent');
}

/**
 * Actual carrier stop path. Admission is a terminal semantic receipt; only an admitted receipt
 * may enter the separately owned durable-save barrier.
 */
export async function stopPilotNaturalDemandCarrier({ lifecycle, server, arm, timeoutMs, awaitDurable }) {
  if (typeof awaitDurable !== 'function') throw new Error('natural-demand durable callback is malformed');
  await awaitNaturalDemandEpisodeReady(lifecycle, server, timeoutMs);
  const admitted = await requestPilotNaturalDemandStop(lifecycle, server, arm, timeoutMs);
  return await awaitDurable(admitted);
}

function validateNaturalDemandEpisode(value, identity, server) {
  if (!value || value.schema !== 1 || value.kind !== KIND || value.status !== 'eligible' || !sameJson(value.identity, identity)
      || value.server?.serverRunId !== server.serverRunId || value.server?.serverPid !== server.serverPid || value.released !== true
      || !Array.isArray(value.members) || value.members.length === 0) {
    throw new Error('natural-demand episode coordination evidence is absent, stale, or incomplete');
  }
  for (const member of value.members) {
    if (!member || member.generationRefCount !== 0 || member.readyForSaving !== true || member.terminal !== 'zero_ready') {
      throw new Error('natural-demand episode coordination evidence is incomplete');
    }
  }
}

function requireArm(value) {
  if (!value || !UUID.test(value.stopAdmissionNonce ?? '')) throw new Error('natural-demand stop admission is malformed');
}
function requireReceipt(value, kind, status, identity, server, nonce) {
  if (!value || value.schema !== 1 || value.kind !== kind || value.status !== status || !sameJson(value.identity, identity)
      || value.server?.serverRunId !== server.serverRunId || value.server?.serverPid !== server.serverPid
      || value.stopAdmissionNonce !== nonce) {
    throw new Error('natural-demand server receipt is absent, stale, foreign, or malformed');
  }
}

function requireLifecycle(value) {
  if (!value || typeof value.directory !== 'string') throw new Error('natural-demand lifecycle is malformed');
  requireIdentity(value.identity);
}
function requireIdentity(value) {
  if (!value || value.schema !== 1 || !/^[0-9a-f]{64}$/.test(value.buildIdentitySha256 ?? '')
      || typeof value.workerId !== 'string' || !UUID.test(value.runId ?? '') || typeof value.scenarioId !== 'string'
      || typeof value.segmentId !== 'string' || !UUID.test(value.nonce ?? '') || !UUID.test(value.sessionId ?? '')) {
    throw new Error('natural-demand lifecycle identity is malformed');
  }
}
function requireServer(value) {
  if (!value || !UUID.test(value.serverRunId ?? '') || !Number.isInteger(value.serverPid) || value.serverPid <= 1) {
    throw new Error('natural-demand server identity is malformed');
  }
}
function sha256(value) { return createHash('sha256').update(value).digest('hex'); }
function sameJson(left, right) { return JSON.stringify(left) === JSON.stringify(right); }
async function exists(path) { try { await readFile(path); return true; } catch (error) { if (error?.code === 'ENOENT') return false; throw error; } }
async function awaitReceipt(path, timeoutMs, validate, absent) {
  if (!Number.isInteger(timeoutMs) || timeoutMs < 1 || timeoutMs > 300_000) throw new Error('natural-demand receipt timeout is invalid');
  const deadline = Date.now() + timeoutMs;
  while (Date.now() <= deadline) {
    try { return validate(JSON.parse(await readFile(path, 'utf8'))); }
    catch (error) { if (error?.code !== 'ENOENT') throw error; }
    await changed(join(path, '..'), deadline);
  }
  throw new Error(absent);
}
async function writeImmutable(directory, target, value) {
  const temporary = join(directory, 'staging', `${randomUUID()}.pending`);
  try { await writeFile(temporary, value, { encoding: 'utf8', flag: 'wx' }); await link(temporary, target); }
  finally { await rm(temporary, { force: true }); }
}
function changed(directory, deadline) {
  return new Promise((resolveWait, reject) => {
    const remaining = deadline - Date.now();
    if (remaining <= 0) return resolveWait();
    const watcher = watch(directory, { persistent: false }, () => finish());
    const timer = setTimeout(() => finish(), remaining);
    const finish = () => { clearTimeout(timer); watcher.close(); resolveWait(); };
    watcher.once('error', error => { clearTimeout(timer); watcher.close(); reject(error); });
  });
}
