import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { access, mkdir, readFile, readdir, readlink, stat, writeFile } from 'node:fs/promises';
import { constants } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';

const PROC_ROOT = '/proc';
const MAX_THREADS = 256;
const MAX_TEXT_BYTES = 512 * 1024;
const CAPTURE_TIMEOUT_MS = 5_000;
// Cadence is forensic only. The final point leaves time to retain evidence
// before the unchanged 90-second graceful-save failure bound.
const DEFAULT_SLOT_OFFSETS_MS = Object.freeze([0, 20_000, 60_000, 80_000]);

/** External, fail-closed forensic observer for one runner-owned JVM. */
export async function prearmSaveOwnerObserver({ project, root, lifecycleIdentity, server,
  captureTimeoutMs = CAPTURE_TIMEOUT_MS, slotOffsetsMs = DEFAULT_SLOT_OFFSETS_MS }) {
  requireObject(lifecycleIdentity, 'owner observer lifecycle identity');
  requireServer(server);
  if (!Number.isInteger(captureTimeoutMs) || captureTimeoutMs < 25 || captureTimeoutMs > CAPTURE_TIMEOUT_MS) {
    throw new Error('owner observer capture timeout is invalid');
  }
  const slots = checkedSlotOffsets(slotOffsetsMs);
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
  const armed = Object.freeze({ schema: 2, kind: 'frontier-v3-save-owner-observation', status: 'armed',
    project: projectRoot, directory, lifecycleIdentity, server: {
      serverRunId: server.serverRunId, serverPid: server.serverPid, childPid: server.child.pid,
      processStartTime: pidIdentity.startTime, executable: pidIdentity.executable,
      commandLineSha256: sha256(pidIdentity.commandLine), jcmd,
      ...(server.worldDirectory === undefined ? {} : { worldDirectory: resolve(server.worldDirectory) })
    }, captureTimeoutMs, slotOffsetsMs: slots });
  const receipt = join(directory, 'prearm.json');
  await writeExclusiveJson(receipt, armed);
  return Object.freeze({ ...armed, receipt });
}

/**
 * Starts a multi-point observation after the ordinary `Saving worlds` marker.
 * `finish` records durable acknowledgement or timeout before cleanup; it never
 * changes the server and turns unneeded later slots into visible receipts.
 */
export function startSaveOwnerObservation(armed, { output, outputRevision, outputAfter }) {
  requireArmed(armed);
  if (typeof output !== 'function' || typeof outputRevision !== 'function' || typeof outputAfter !== 'function') {
    throw new Error('owner observer output source is malformed');
  }
  let terminal = null;
  let resolveTerminal;
  const terminalWait = new Promise(resolveWait => { resolveTerminal = resolveWait; });
  const finish = value => {
    if (terminal !== null) return;
    terminal = checkedTerminal(value);
    resolveTerminal(terminal);
  };
  return Object.freeze({ finish,
    result: observeSlots(armed, { output, outputRevision, outputAfter }, terminalWait, () => terminal) });
}

/** Backward-compatible one-shot form used by cheap observer tests. */
export async function captureSaveOwnerObservation(armed, source) {
  return await startSaveOwnerObservation(armed, source).result;
}

/** Verifies that every declared slot retained an artifact or explicit failure. */
export function requireCapturedSaveOwnerObservation(value) {
  requireObject(value, 'owner observation');
  if (value.schema !== 2 || value.kind !== 'frontier-v3-save-owner-observation' || value.status !== 'completed'
      || !value.lifecycleIdentity || !value.server || !Array.isArray(value.slots) || value.slots.length !== value.slotOffsetsMs?.length) {
    throw new Error('owner observation receipt is incomplete');
  }
  checkedSlotOffsets(value.slotOffsetsMs);
  if (!Number.isInteger(value.server.serverPid) || value.server.serverPid <= 1 || !nonEmpty(value.server.processStartTime)
      || !nonEmpty(value.server.serverRunId) || value.slots.some((slot, index) => !validSlot(slot, value.slotOffsetsMs[index]))) {
    throw new Error('owner observation receipt has invalid identity or artifacts');
  }
  return value;
}

/**
 * Classifies only retained late writer progress. It deliberately does not call
 * a progressing writer finite, or attribute a no-progress result to a module.
 */
export function classifySaveOwnerProgress(value) {
  const receipt = requireCapturedSaveOwnerObservation(value);
  const captured = receipt.slots.filter(slot => slot.status === 'captured' && slot.facts?.processWriteBytes !== null);
  if (captured.length < 2) return Object.freeze({ kind: 'insufficient', reason: 'two-captured-io-slots-required' });
  const [before, after] = captured.slice(-2);
  const ioProgress = after.facts.processWriteBytes > before.facts.processWriteBytes
    || after.facts.processSyscalls > before.facts.processSyscalls
    || after.facts.worldFilesSha256 !== before.facts.worldFilesSha256;
  return Object.freeze({ kind: ioProgress ? 'progressing_io' : 'no_io_progress', before: before.index, after: after.index,
    serverChunkUnloadInBoth: before.facts.serverChunkUnload === true && after.facts.serverChunkUnload === true,
    regionPwriteInBoth: before.facts.regionPwrite === true && after.facts.regionPwrite === true });
}

async function observeSlots(armed, source, terminalWait, terminal) {
  const slots = [];
  let savingWorldsAt = null;
  let terminalFact = null;
  try {
    savingWorldsAt = await awaitSavingWorlds(source.output, source.outputRevision, source.outputAfter, armed.captureTimeoutMs, terminalWait);
    if (savingWorldsAt === null) terminalFact = terminal() ?? { kind: 'saving_worlds_unavailable' };
  } catch (failure) {
    terminalFact = { kind: 'saving_worlds_unavailable', failure: bounded(String(failure?.message ?? failure)) };
  }
  for (const [index, offsetMs] of armed.slotOffsetsMs.entries()) {
    if (terminalFact === null && terminal() !== null) terminalFact = terminal();
    if (terminalFact === null) {
      const remaining = offsetMs - (Date.now() - savingWorldsAt);
      if (remaining > 0) {
        const reached = await waitForSlot(remaining, terminalWait);
        if (reached !== null) terminalFact = reached;
      }
    }
    const slot = terminalFact === null ? await captureSlot(armed, source, index, offsetMs) : unavailableSlot(index, offsetMs, terminalFact);
    const path = join(armed.directory, `slot-${String(index + 1).padStart(2, '0')}.json`);
    await writeExclusiveJson(path, slot);
    slots.push({ ...slot, receipt: { path, sha256: sha256(JSON.stringify(slot)) } });
  }
  const receipt = { ...armed, status: 'completed', savingWorldsAt, completedAt: new Date().toISOString(),
    terminal: terminal() ?? terminalFact, slots };
  const path = join(armed.directory, 'owner-observation.json');
  await writeExclusiveJson(path, receipt);
  return Object.freeze({ ...receipt, receipt: path });
}

async function captureSlot(armed, source, index, offsetMs) {
  try {
    requireSamePidIdentity(armed.server, await readPidIdentity(armed.server.serverPid));
    const capture = await captureThreadDump(armed, source, index);
    const snapshot = await procSnapshot(armed.server.serverPid, armed.server.worldDirectory);
    const snapshotPath = join(armed.directory, `slot-${String(index + 1).padStart(2, '0')}-proc.json`);
    await writeExclusiveJson(snapshotPath, snapshot);
    return { schema: 1, index, offsetMs, status: 'captured', capturedAt: new Date().toISOString(), capture,
      facts: snapshotFacts(snapshot, capture),
      snapshot: { path: snapshotPath, sha256: sha256(JSON.stringify(snapshot)), capturedAt: snapshot.capturedAt } };
  } catch (failure) {
    return unavailableSlot(index, offsetMs, { kind: 'capture_unavailable', failure: bounded(String(failure?.message ?? failure)) });
  }
}

async function captureThreadDump(armed, source, index) {
  const primary = await boundedCommand(armed.server.jcmd, [String(armed.server.serverPid), 'Thread.print', '-l'], armed.captureTimeoutMs);
  if (primary.ok && looksLikeThreadDump(primary.stdout)) {
    const path = join(armed.directory, `slot-${String(index + 1).padStart(2, '0')}-thread-primary.txt`);
    await writeExclusive(path, primary.stdout);
    return { method: 'jcmd-thread-print', path, sha256: sha256(primary.stdout), exitCode: primary.exitCode,
      serverChunkUnload: primary.stdout.includes('ChunkMap.processUnloads'), regionPwrite: primary.stdout.includes('RegionFile.write') && primary.stdout.includes('pwrite') };
  }
  const before = source.outputRevision();
  process.kill(armed.server.serverPid, 'SIGQUIT');
  const fallback = await awaitThreadDump(source.output, source.outputRevision, source.outputAfter, before, armed.captureTimeoutMs);
  const path = join(armed.directory, `slot-${String(index + 1).padStart(2, '0')}-thread-sigquit.txt`);
  await writeExclusive(path, fallback);
  return { method: 'sigquit-thread-dump', path, sha256: sha256(fallback), primaryFailure: boundedCommandFailure(primary),
    serverChunkUnload: fallback.includes('ChunkMap.processUnloads'), regionPwrite: fallback.includes('RegionFile.write') && fallback.includes('pwrite') };
}

function snapshotFacts(snapshot, capture) {
  const io = snapshot.process.io;
  return Object.freeze({ processWriteBytes: procCounter(io, 'write_bytes'), processSyscalls: procCounter(io, 'syscw'),
    worldFilesSha256: sha256(JSON.stringify(snapshot.worldFiles ?? [])), serverChunkUnload: capture.serverChunkUnload === true,
    regionPwrite: capture.regionPwrite === true });
}
function procCounter(value, name) {
  if (typeof value !== 'string') return null;
  const match = new RegExp(`(?:^|\\n)${name}:\\s*(\\d+)`).exec(value);
  return match === null ? null : Number(match[1]);
}

function unavailableSlot(index, offsetMs, reason) {
  return { schema: 1, index, offsetMs, status: 'unavailable', capturedAt: new Date().toISOString(), reason: checkedTerminal(reason) };
}

async function awaitSavingWorlds(output, outputRevision, outputAfter, timeoutMs, terminalWait) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (output().includes('Saving worlds')) return Date.now();
    const revision = outputRevision();
    const wake = await Promise.race([outputAfter(revision).then(() => null), delay(Math.max(1, deadline - Date.now())).then(() => null), terminalWait]);
    if (wake !== null) return null;
  }
  throw new Error('owner observer never received Saving worlds from exact JVM output');
}
async function waitForSlot(milliseconds, terminalWait) { return await Promise.race([delay(milliseconds).then(() => null), terminalWait]); }
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

async function procSnapshot(pid, worldDirectory = undefined) {
  const identity = await readPidIdentity(pid);
  const process = await boundedProcFiles(pid, ['status', 'stat', 'wchan', 'io']);
  const tasks = [];
  const names = (await readdir(join(PROC_ROOT, String(pid), 'task'))).filter(name => /^\d+$/.test(name)).sort((a, b) => Number(a) - Number(b));
  for (const tid of names.slice(0, MAX_THREADS)) tasks.push({ tid: Number(tid), ...(await boundedProcFiles(pid, [`task/${tid}/status`, `task/${tid}/stat`, `task/${tid}/wchan`, `task/${tid}/io`])) });
  const fds = [];
  for (const name of (await readdir(join(PROC_ROOT, String(pid), 'fd'))).filter(name => /^\d+$/.test(name)).sort((a, b) => Number(a) - Number(b)).slice(0, 512)) {
    try {
      const target = await readlink(join(PROC_ROOT, String(pid), 'fd', name));
      if (/(?:region|entities|poi|frontier-v3|wal-|level\.dat|session\.lock)/.test(target)) fds.push({ fd: Number(name), target: bounded(target) });
    } catch (error) { fds.push({ fd: Number(name), unavailable: error?.code ?? String(error) }); }
  }
  return Object.freeze({ schema: 2, pid, capturedAt: new Date().toISOString(), identity, process, tasks,
    tasksTruncated: names.length > MAX_THREADS, worldRelevantFds: fds,
    ...(worldDirectory === undefined ? {} : { worldFiles: await boundedWorldFiles(worldDirectory) }) });
}

async function boundedWorldFiles(root) {
  const selected = []; const pending = [resolve(root)];
  while (pending.length > 0 && selected.length < 128) {
    const directory = pending.shift();
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      if (selected.length >= 128) break;
      const path = join(directory, entry.name);
      if (entry.isDirectory()) pending.push(path);
      else if (entry.isFile() && /(?:region\/.*\.mca|entities\/.*\.mca|poi\/.*\.mca|frontier-v3\/.*(?:wal|snapshot)|level\.dat)/.test(relative(root, path))) {
        const metadata = await stat(path);
        selected.push({ path: relative(root, path), bytes: metadata.size, modifiedMs: metadata.mtimeMs });
      }
    }
  }
  return Object.freeze(selected.sort((left, right) => left.path.localeCompare(right.path)));
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
  const fields = match[4].trim().split(/\s+/); const startTime = fields[18];
  if (!/^\d+$/.test(startTime ?? '')) throw new Error('owner observer process start time is malformed');
  const [command, executable] = await Promise.all([readFile(join(PROC_ROOT, String(pid), 'cmdline')), readlink(join(PROC_ROOT, String(pid), 'exe'))]);
  const commandLine = command.toString('utf8').replaceAll('\0', ' ').trim();
  if (!nonEmpty(commandLine) || !nonEmpty(executable)) throw new Error('owner observer process identity is incomplete');
  return Object.freeze({ pid, startTime, commandLine, executable });
}
function requireExactPilotArguments(commandLine, serverRunId, lifecycleDirectory, pid) {
  for (const value of [`-Dpale_mirror.frontier_v3.pilot.run_id=${serverRunId}`, `-Dpale_mirror.frontier_v3.pilot.lifecycle_control_directory=${lifecycleDirectory}`]) if (!commandLine.includes(value)) throw new Error(`owner observer exact PID ${pid} does not carry the declared pilot identity`);
}
function requireSamePidIdentity(expected, actual) {
  if (actual.pid !== expected.serverPid || actual.startTime !== expected.processStartTime || actual.executable !== expected.executable || sha256(actual.commandLine) !== expected.commandLineSha256) throw new Error('owner observer exact PID identity drifted before capture');
}
function requireServer(server) {
  requireObject(server, 'owner observer server');
  if (!Number.isInteger(server.serverPid) || server.serverPid <= 1 || !nonEmpty(server.serverRunId) || !nonEmpty(server.lifecycleDirectory)) throw new Error('owner observer server identity is malformed');
}
function requireArmed(armed) { requireObject(armed, 'owner observer arm'); if (armed.status !== 'armed' || !nonEmpty(armed.directory) || !armed.server || !armed.lifecycleIdentity) throw new Error('owner observer is not armed'); }
function checkedRoot(project, root) {
  if (!nonEmpty(root)) throw new Error('owner observer root is required');
  const target = resolve(root); const rel = relative(project, target);
  if (!rel.startsWith('build/') || rel.includes('/..')) throw new Error('owner observer root must remain under project build/');
  return target;
}
function checkedSlotOffsets(value) {
  if (!Array.isArray(value) || value.length < 3 || value[0] !== 0 || value.some((entry, index) => !Number.isInteger(entry) || entry < 0 || entry > 85_000 || (index > 0 && entry <= value[index - 1]))) throw new Error('owner observer slot offsets are invalid');
  return Object.freeze([...value]);
}
function checkedTerminal(value) {
  requireObject(value, 'owner observer terminal');
  if (!nonEmpty(value.kind)) throw new Error('owner observer terminal kind is invalid');
  return Object.freeze({ kind: value.kind, ...(value.failure === undefined ? {} : { failure: bounded(String(value.failure)) }) });
}
function validSlot(value, offsetMs) {
  return value?.schema === 1 && value.index >= 0 && value.offsetMs === offsetMs && (value.status === 'captured'
    ? nonEmpty(value.capture?.path) && nonEmpty(value.capture?.sha256) && nonEmpty(value.snapshot?.path) && nonEmpty(value.snapshot?.sha256)
      && Number.isFinite(value.facts?.processWriteBytes) && Number.isFinite(value.facts?.processSyscalls) && nonEmpty(value.facts?.worldFilesSha256)
    : value.status === 'unavailable' && nonEmpty(value.reason?.kind));
}
async function boundedCommand(command, args, timeoutMs) {
  return await new Promise(resolveCommand => {
    let stdout = ''; let stderr = ''; let settled = false; let timer;
    const child = spawn(command, args, { stdio: ['ignore', 'pipe', 'pipe'] });
    const finish = value => { if (!settled) { settled = true; if (timer !== undefined) clearTimeout(timer); resolveCommand(value); } };
    child.stdout.setEncoding('utf8').on('data', value => { stdout = bounded(stdout + value); }); child.stderr.setEncoding('utf8').on('data', value => { stderr = bounded(stderr + value); });
    child.once('error', error => finish({ ok: false, error: String(error), stdout, stderr })); child.once('exit', (exitCode, signal) => finish({ ok: exitCode === 0, exitCode, signal, stdout, stderr }));
    timer = setTimeout(() => { child.kill('SIGKILL'); finish({ ok: false, timeout: true, stdout, stderr }); }, timeoutMs);
  });
}
function boundedCommandFailure(value) { return { exitCode: value.exitCode ?? null, signal: value.signal ?? null, timeout: value.timeout === true, error: value.error ?? null, stderr: bounded(value.stderr ?? '') }; }
function looksLikeThreadDump(text) { return text.includes('Full thread dump') || text.includes('java.lang.Thread.State'); }
function bounded(value) { return value.length <= MAX_TEXT_BYTES ? value : value.slice(-MAX_TEXT_BYTES); }
function sha256(value) { return createHash('sha256').update(value).digest('hex'); }
function nonEmpty(value) { return typeof value === 'string' && value.length > 0; }
function requireObject(value, name) { if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${name} is malformed`); }
async function writeExclusive(path, value) { await writeFile(path, value, { encoding: 'utf8', flag: 'wx' }); }
async function writeExclusiveJson(path, value) { await writeExclusive(path, `${JSON.stringify(value, null, 2)}\n`); }
function delay(ms) { return new Promise(resolveDelay => setTimeout(resolveDelay, ms)); }
