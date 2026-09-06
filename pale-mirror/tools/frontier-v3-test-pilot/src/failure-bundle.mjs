import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';

const MAX_TAIL_BYTES = 32 * 1024;

/**
 * Writes bounded, causal failure evidence without copying a world, a whole log or any operator
 * home-directory data.  Disposable-world preservation stays the runner's job; this bundle tells
 * an investigator exactly which retained world and exact process boundary to inspect.
 */
export async function writeFailureBundle({ project, output, scenarioPath, runId, timing, failure,
  serverLog, clientLog, serverLogText, clientLogText, tracePath, worldDirectory, process = {}, build = undefined,
  crash = undefined, decodedWalTail = undefined, termination = undefined, diagnosticSnapshots = [], lifecycleDirectory = undefined }) {
  const root = resolve(project, 'build');
  const target = resolve(project, output.replace(/\.json$/i, '') + '.failure');
  const rel = relative(root, target);
  if (rel === '' || rel.startsWith('..') || rel.includes('/..')) throw new Error('failure bundle must remain under build');
  const scenario = await readBounded(scenarioPath, 256 * 1024);
  const parsedScenario = parseScenario(scenario.text);
  const bundle = {
    schema: 2,
    kind: 'frontier-v3-failure-bundle',
    runId,
    createdAt: new Date().toISOString(),
    scenario: { path: relative(project, scenarioPath), sha256: sha256(scenario.text), truncated: scenario.truncated,
      seed: parsedScenario?.isolation?.seed ?? null, declaredFrames: (parsedScenario?.frames ?? []).map((frame) => frame.name) },
    failure: String(failure?.stack ?? failure),
    timing,
    build: build ?? null,
    process: { ...process, crash: crash ?? null },
    // Keep only the causal fields an investigator needs to reconnect a failed physical run to
    // its canonical owner.  Raw diagnostics can contain presentation detail and must not turn a
    // bounded failure bundle into a world dump.
    semantic: boundedSemanticDiagnostics(diagnosticSnapshots),
    termination: termination ?? null,
    // A pre-launch classpath drift has no world yet, but it is still a native-run failure that
    // must leave one attributable bundle rather than disappear before the disposable server.
    retainedWorld: worldDirectory === undefined ? null : relative(project, worldDirectory),
    artifacts: {
      trace: await correlatedTrace(project, tracePath, runId),
      serverLog: await tailArtifact(project, serverLog, serverLogText),
      clientLog: await tailArtifact(project, clientLog, clientLogText),
      decodedWalTail: decodedWalTail ?? { missing: true },
      lifecycle: await lifecycleArtifact(project, lifecycleDirectory)
    }
  };
  await mkdir(target, { recursive: true });
  await writeFile(resolve(target, 'bundle.json'), `${JSON.stringify(bundle, null, 2)}\n`, 'utf8');
  return target;
}

/** Keeps the bounded external barrier journal, never a mutable world or pilot state. */
async function lifecycleArtifact(project, directory) {
  if (directory === undefined) return { missing: true };
  const root = resolve(directory);
  const retained = {};
  for (const name of ['identity.json']) {
    try { retained[name] = JSON.parse((await readBounded(resolve(root, name), 16 * 1024)).text); }
    catch (error) { if (error?.code === 'ENOENT') return { path: relative(project, root), missing: true }; throw error; }
  }
  for (const group of ['events', 'signals']) {
    try {
      const entries = (await readdir(resolve(root, group))).sort().slice(-128);
      retained[group] = [];
      for (const entry of entries) {
        const value = await readBounded(resolve(root, group, entry), 16 * 1024);
        retained[group].push({ name: entry, value: JSON.parse(value.text), truncated: value.truncated });
      }
    } catch (error) {
      if (error?.code === 'ENOENT') retained[group] = { missing: true };
      else throw error;
    }
  }
  return { path: relative(project, root), ...retained };
}

function boundedSemanticDiagnostics(snapshots) {
  if (!Array.isArray(snapshots)) throw new Error('failure diagnostic snapshots must be an array');
  const selected = [];
  for (const snapshot of snapshots.slice(-24)) {
    const value = snapshot?.observed?.value ?? snapshot?.value ?? snapshot;
    if (!value || typeof value !== 'object' || Array.isArray(value) || typeof value.kind !== 'string') continue;
    const base = pick(value, ['kind', 'id', 'revision', 'instant', 'status']);
    switch (value.kind) {
      case 'process': selected.push({ ...base,
        family: value.family ?? null, identity: pick(value.identity, ['job', 'worker', 'outputItem']),
        claims: pick(value.claims, ['task', 'site', 'worker', 'intent', 'outputSlot', 'lease']),
        conservation: pick(value.conservation, ['outputItem', 'completedCropSlots', 'pendingCropSlot', 'totalCropSlots']),
        schedule: pick(value.schedule, ['count', 'entries']), cursor: pick(value.cursor, ['index', 'length', 'retainedBody', 'actorBody']),
        result: pick(value.result, ['sitePhase', 'intentStatus', 'complete']) }); break;
      case 'scene': selected.push({ ...base, lease: pick(value, ['leaseId', 'leaseStatus', 'sceneKind', 'harvestJob', 'primaryActor', 'primaryEntityUuid']) }); break;
      case 'intent': selected.push({ ...base, intent: pick(value, ['intentKind', 'intentStatus', 'subjects', 'receiptId', 'failureReason']) }); break;
      case 'actor': selected.push({ ...base, actor: pick(value, ['owner', 'role', 'life', 'assignment', 'assignmentOwner', 'sceneReserved']) }); break;
      case 'container': selected.push({ ...base, container: pick(value, ['owner', 'surface', 'position', 'occupiedCount', 'occupied']) }); break;
      default: selected.push(base);
    }
  }
  return Object.freeze({ snapshots: selected });
}

function pick(value, fields) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  return Object.fromEntries(fields.filter((field) => Object.prototype.hasOwnProperty.call(value, field))
    .map((field) => [field, value[field]]));
}

async function tailArtifact(project, path, inline = undefined) {
  if (inline !== undefined) {
    if (typeof inline !== 'string') throw new Error('failure inline log tail is malformed');
    const selected = inline.length <= MAX_TAIL_BYTES ? inline : inline.slice(-MAX_TAIL_BYTES);
    return { inline: true, tail: selected, truncated: inline.length > MAX_TAIL_BYTES };
  }
  if (path === undefined) return { missing: true };
  try {
    const data = await readBounded(path, MAX_TAIL_BYTES, true);
    return { path: relative(project, path), tail: data.text, truncated: data.truncated };
  } catch (error) {
    if (error?.code === 'ENOENT') return { path: relative(project, path), missing: true };
    throw error;
  }
}

async function correlatedTrace(project, path, runId) {
  if (path === undefined) return { missing: true };
  try {
    const data = await readBounded(path, MAX_TAIL_BYTES * 4, true);
    const lines = data.text.split('\n').filter((line) => line.includes('"source":"PMV3"') && line.includes(runId)).slice(-128);
    return { path: relative(project, path), records: lines, truncated: data.truncated };
  } catch (error) {
    if (error?.code === 'ENOENT') return { path: relative(project, path), missing: true };
    throw error;
  }
}

function parseScenario(text) {
  try { return JSON.parse(text); }
  catch { return undefined; }
}

async function readBounded(path, maximum, tail = false) {
  const value = await readFile(path);
  const truncated = value.length > maximum;
  const selected = !truncated ? value : tail ? value.subarray(value.length - maximum) : value.subarray(0, maximum);
  return { text: selected.toString('utf8'), truncated };
}

function sha256(value) { return createHash('sha256').update(value).digest('hex'); }
