import { createHash, randomUUID } from 'node:crypto';
import { watch } from 'node:fs';
import { link, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { requestRconCommand } from './rcon.mjs';

const ARM_KIND = 'f02b-natural-demand-episode-arm';
const KIND = 'f02b-natural-demand-episode';
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
export function requestPilotNaturalDemandStop(server, arm) {
  requireServer(server);
  return requestRconCommand({ port: server.rconPort, password: server.rconPassword, command: naturalDemandStopCommand(arm) });
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
