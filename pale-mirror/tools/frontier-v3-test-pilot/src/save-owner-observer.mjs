import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { access, mkdir, readFile, readdir, readlink, writeFile } from 'node:fs/promises';
import { constants } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';

const PROC_ROOT = '/proc';
const MAX_THREADS = 256;
const MAX_TEXT_BYTES = 512 * 1024;
const CAPTURE_TIMEOUT_MS = 5_000;

/**
 * External, fail-closed forensic observer for one runner-owned JVM.  It has no
 * simulation authority: all mutable state remains with the ordinary server and
 * its lifecycle protocol.  A PID alone is never sufficient because it can be
 * reused; the Linux start-time and the exact pilot JVM arguments are part of
 * every retained receipt.
 */
export async function prearmSaveOwnerObserver({ project, root, lifecycleIdentity, server, captureTimeoutMs = CAPTURE_TIMEOUT_MS }) {
  requireObject(lifecycleIdentity, 'owner observer lifecycle identity');
  requireServer(server);
  if (!Number.isInteger(captureTimeoutMs) || captureTimeoutMs < 25 || captureTimeoutMs > CAPTURE_TIMEOUT_MS) {
    throw new Error('owner observer capture timeout is invalid');
  }
  const projectRoot = resolve(project);
  const observationRoot = checkedRoot(projectRoot, root);
  await mkdir(observationRoot, { recursive: true });
  const pidIdentity = await readPidIdentity(server.serverPid);
  requireExactPilotArguments(pidIdentity.commandLine, server.serverRunId, server.lifecycleDirectory, server.serverPid);
  if (server.child?.pid !== server.serverPid) throw new Error('owner observer exact server PID is not the runner child');
  const jcmd = join(dirname(pidIdentity.executable), 'jcmd');
  try { await access(jcmd, constants.X_OK); }
  catch { throw new Error(`owner observer primary capture is unavailable for exact JVM ${server.serverPid}`); }

  const directory = join(observationRoot, `${lifecycleIdentity.runId}-${server.serverRunId}`);
  await mkdir(directory, { recursive: false });
  const armed = Object.freeze({ schema: 1, kind: 'frontier-v3-save-owner-observation', status: 'armed',
    project: projectRoot, directory, lifecycleIdentity, server: {
      serverRunId: server.serverRunId, serverPid: server.serverPid, childPid: server.child.pid,
      processStartTime: pidIdentity.startTime, executable: pidIdentity.executable,
      commandLineSha256: sha256(pidIdentity.commandLine), jcmd
    }, captureTimeoutMs });
  const receipt = join(directory, 'prearm.json');
  await writeExclusiveJson(receipt, armed);
  return Object.freeze({ ...armed, receipt });
}

/** Captures only after the exact owned JVM has emitted its ordinary save marker. */
export async function captureSaveOwnerObservation(armed, { output, outputRevision, outputAfter }) {
  requireArmed(armed);
  if (typeof output !== 'function' || typeof outputRevision !== 'function' || typeof outputAfter !== 'function') {
    throw new Error('owner observer output source is malformed');
  }
  await awaitSavingWorlds(output, outputRevision, outputAfter, armed.captureTimeoutMs);
  const current = await readPidIdentity(armed.server.serverPid);
  requireSamePidIdentity(armed.server, current);
  const primary = await boundedCommand(armed.server.jcmd, [String(armed.server.serverPid), 'Thread.print', '-l'], armed.captureTimeoutMs);
  let capture;
  if (primary.ok && looksLikeThreadDump(primary.stdout)) {
    const path = join(armed.directory, 'thread-dump-primary.txt');
    await writeExclusive(path, primary.stdout);
    capture = { method: 'jcmd-thread-print', path, sha256: sha256(primary.stdout), exitCode: primary.exitCode };
  } else {
    const before = outputRevision();
    process.kill(armed.server.serverPid, 'SIGQUIT');
    const fallback = await awaitThreadDump(output, outputRevision, outputAfter, before, armed.captureTimeoutMs);
    const path = join(armed.directory, 'thread-dump-sigquit.txt');
    await writeExclusive(path, fallback);
    capture = { method: 'sigquit-thread-dump', path, sha256: sha256(fallback), primaryFailure: boundedCommandFailure(primary) };
  }
  const first = await procSnapshot(armed.server.serverPid);
  // This is capture cadence, never a completion oracle.  It distinguishes a
  // blocked owner from a live writer without extending the ordinary stop bound.
  await new Promise(resolveDelay => setTimeout(resolveDelay, 250));
  const second = await procSnapshot(armed.server.serverPid);
  const snapshots = [];
  for (const [index, value] of [first, second].entries()) {
    const path = join(armed.directory, `proc-${index + 1}.json`);
    await writeExclusiveJson(path, value);
    snapshots.push({ path, sha256: sha256(JSON.stringify(value)), capturedAt: value.capturedAt });
  }
  const receipt = { ...armed, status: 'captured', capturedAt: new Date().toISOString(), capture, snapshots };
  const path = join(armed.directory, 'owner-observation.json');
  await writeExclusiveJson(path, receipt);
  return Object.freeze({ ...receipt, receipt: path });
}

/** A small verifier used by the cheap owned-Java preflight and negative cases. */
export function requireCapturedSaveOwnerObservation(value) {
  requireObject(value, 'owner observation');
  if (value.schema !== 1 || value.kind !== 'frontier-v3-save-owner-observation' || value.status !== 'captured'
      || !value.lifecycleIdentity || !value.server || !value.capture || !Array.isArray(value.snapshots) || value.snapshots.length !== 2) {
    throw new Error('owner observation receipt is incomplete');
  }
  if (!Number.isInteger(value.server.serverPid) || value.server.serverPid <= 1 || !nonEmpty(value.server.processStartTime)
      || !nonEmpty(value.server.serverRunId) || !nonEmpty(value.capture.path) || !nonEmpty(value.capture.sha256)
      || value.snapshots.some(snapshot => !nonEmpty(snapshot.path) || !nonEmpty(snapshot.sha256))) {
    throw new Error('owner observation receipt has invalid identity or artifacts');
  }
  return value;
}

async function awaitSavingWorlds(output, outputRevision, outputAfter, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (output().includes('Saving worlds')) return;
    const revision = outputRevision();
    await Promise.race([outputAfter(revision), delay(Math.max(1, deadline - Date.now()))]);
  }
  throw new Error('owner observer never received Saving worlds from exact JVM output');
}

async function awaitThreadDump(output, outputRevision, outputAfter, priorRevision, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let revision = priorRevision;
  while (Date.now() < deadline) {
    const text = output();
    if (looksLikeThreadDump(text)) return bounded(text);
    revision = outputRevision();
    await Promise.race([outputAfter(revision), delay(Math.max(1, deadline - Date.now()))]);
  }
  throw new Error('owner observer fallback thread dump was not emitted by exact JVM');
}

async function procSnapshot(pid) {
  const identity = await readPidIdentity(pid);
  const process = await boundedProcFiles(pid, ['status', 'stat', 'wchan', 'io']);
  const tasks = [];
  const names = (await readdir(join(PROC_ROOT, String(pid), 'task'))).filter(name => /^\d+$/.test(name)).sort((a, b) => Number(a) - Number(b));
  for (const tid of names.slice(0, MAX_THREADS)) {
    tasks.push({ tid: Number(tid), ...(await boundedProcFiles(pid, [`task/${tid}/status`, `task/${tid}/stat`, `task/${tid}/wchan`, `task/${tid}/io`])) });
  }
  const fds = [];
  for (const name of (await readdir(join(PROC_ROOT, String(pid), 'fd'))).filter(name => /^\d+$/.test(name)).sort((a, b) => Number(a) - Number(b)).slice(0, 512)) {
    try {
      const target = await readlink(join(PROC_ROOT, String(pid), 'fd', name));
      if (/(?:region|entities|poi|frontier-v3|wal-|level\.dat|session\.lock)/.test(target)) fds.push({ fd: Number(name), target: bounded(target) });
    } catch (error) { fds.push({ fd: Number(name), unavailable: error?.code ?? String(error) }); }
  }
  return Object.freeze({ schema: 1, pid, capturedAt: new Date().toISOString(), identity, process, tasks,
    tasksTruncated: names.length > MAX_THREADS, worldRelevantFds: fds });
}

async function boundedProcFiles(pid, names) {
  const result = {};
  for (const name of names) {
    try { result[name.replaceAll('/', '_')] = bounded(await readFile(join(PROC_ROOT, String(pid), name), 'utf8')); }
    catch (error) { result[name.replaceAll('/', '_')] = { unavailable: error?.code ?? String(error) }; }
  }
  return result;
}

async function readPidIdentity(pid) {
  if (!Number.isInteger(pid) || pid <= 1) throw new Error('owner observer PID is invalid');
  let statText;
  try { statText = await readFile(join(PROC_ROOT, String(pid), 'stat'), 'utf8'); }
  catch { throw new Error(`owner observer exact PID ${pid} is unavailable`); }
  const match = /^(\d+) \((.*)\) ([A-Z]) (.*)$/.exec(statText.trim());
  if (!match || Number(match[1]) !== pid) throw new Error('owner observer process stat is malformed');
  const fields = match[4].trim().split(/\s+/);
  const startTime = fields[18];
  if (!/^\d+$/.test(startTime ?? '')) throw new Error('owner observer process start time is malformed');
  const [command, executable] = await Promise.all([
    readFile(join(PROC_ROOT, String(pid), 'cmdline')),
    readlink(join(PROC_ROOT, String(pid), 'exe'))
  ]);
  const commandLine = command.toString('utf8').replaceAll('\0', ' ').trim();
  if (!nonEmpty(commandLine) || !nonEmpty(executable)) throw new Error('owner observer process identity is incomplete');
  return Object.freeze({ pid, startTime, commandLine, executable });
}

function requireExactPilotArguments(commandLine, serverRunId, lifecycleDirectory, pid) {
  for (const value of [`-Dpale_mirror.frontier_v3.pilot.run_id=${serverRunId}`,
    `-Dpale_mirror.frontier_v3.pilot.lifecycle_control_directory=${lifecycleDirectory}`]) {
    if (!commandLine.includes(value)) throw new Error(`owner observer exact PID ${pid} does not carry the declared pilot identity`);
  }
}

function requireSamePidIdentity(expected, actual) {
  if (actual.pid !== expected.serverPid || actual.startTime !== expected.processStartTime
      || actual.executable !== expected.executable || sha256(actual.commandLine) !== expected.commandLineSha256) {
    throw new Error('owner observer exact PID identity drifted before capture');
  }
}

function requireServer(server) {
  requireObject(server, 'owner observer server');
  if (!Number.isInteger(server.serverPid) || server.serverPid <= 1 || !nonEmpty(server.serverRunId)
      || !nonEmpty(server.lifecycleDirectory)) {
    throw new Error('owner observer server identity is malformed');
  }
}

function requireArmed(armed) {
  requireObject(armed, 'owner observer arm');
  if (armed.status !== 'armed' || !nonEmpty(armed.directory) || !armed.server || !armed.lifecycleIdentity) {
    throw new Error('owner observer is not armed');
  }
}

function checkedRoot(project, root) {
  if (!nonEmpty(root)) throw new Error('owner observer root is required');
  const target = resolve(root); const rel = relative(project, target);
  if (!rel.startsWith('build/') || rel.includes('/..')) throw new Error('owner observer root must remain under project build/');
  return target;
}

async function boundedCommand(command, args, timeoutMs) {
  return await new Promise(resolveCommand => {
    let stdout = ''; let stderr = ''; let settled = false;
    let timer;
    const child = spawn(command, args, { stdio: ['ignore', 'pipe', 'pipe'] });
    const finish = value => { if (!settled) { settled = true; if (timer !== undefined) clearTimeout(timer); resolveCommand(value); } };
    child.stdout.setEncoding('utf8').on('data', value => { stdout = bounded(stdout + value); });
    child.stderr.setEncoding('utf8').on('data', value => { stderr = bounded(stderr + value); });
    child.once('error', error => finish({ ok: false, error: String(error), stdout, stderr }));
    child.once('exit', (exitCode, signal) => finish({ ok: exitCode === 0, exitCode, signal, stdout, stderr }));
    timer = setTimeout(() => { child.kill('SIGKILL'); finish({ ok: false, timeout: true, stdout, stderr }); }, timeoutMs);
  });
}

function boundedCommandFailure(value) { return { exitCode: value.exitCode ?? null, signal: value.signal ?? null,
  timeout: value.timeout === true, error: value.error ?? null, stderr: bounded(value.stderr ?? '') }; }
function looksLikeThreadDump(text) { return text.includes('Full thread dump') || text.includes('java.lang.Thread.State'); }
function bounded(value) { return value.length <= MAX_TEXT_BYTES ? value : value.slice(-MAX_TEXT_BYTES); }
function sha256(value) { return createHash('sha256').update(value).digest('hex'); }
function nonEmpty(value) { return typeof value === 'string' && value.length > 0; }
function requireObject(value, name) { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${name} is malformed`); }
async function writeExclusive(path, value) { await writeFile(path, value, { encoding: 'utf8', flag: 'wx' }); }
async function writeExclusiveJson(path, value) { await writeExclusive(path, `${JSON.stringify(value, null, 2)}\n`); }
function delay(ms) { return new Promise(resolveDelay => setTimeout(resolveDelay, ms)); }
