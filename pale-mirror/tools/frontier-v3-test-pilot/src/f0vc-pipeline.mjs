import { createHash } from 'node:crypto';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const WORKERS = Object.freeze(['worker-0', 'worker-1', 'worker-2', 'worker-3']);
const SHA = /^[a-f0-9]{64}$/;
const JOB_STATUS = new Set(['success', 'failure', 'cancelled']);

/** Pure, early boundary: this has no Java/Gradle/Minecraft side effect. */
export function preflight(plan) {
  if (!plan || plan.schema !== 1 || plan.kind !== 'frontier-v3-f0vc-preflight' || !SHA.test(plan.runtimeContentSha256 ?? '')
      || !Array.isArray(plan.workers) || plan.workers.length !== 4 || !Array.isArray(plan.expectedArtifacts) || plan.expectedArtifacts.length !== 4) {
    throw new Error('F0.VC preflight plan is malformed');
  }
  const seen = new Set(); const namespaces = new Set(); const ports = new Set(); const displays = new Set(); const worlds = new Set();
  for (const entry of plan.workers) {
    if (!entry || !WORKERS.includes(entry.worker) || seen.has(entry.worker) || entry.runtimeContentSha256 !== plan.runtimeContentSha256
        || typeof entry.namespace !== 'string' || !entry.namespace.startsWith('/') || namespaces.has(entry.namespace)
        || !Number.isInteger(entry.port) || entry.port < 1024 || ports.has(entry.port) || typeof entry.display !== 'string' || !/^:[0-9]+$/.test(entry.display) || displays.has(entry.display)
        || typeof entry.world !== 'string' || !/^[a-z0-9][a-z0-9_-]{2,63}$/.test(entry.world) || worlds.has(entry.world)
        || !Array.isArray(entry.cases) || entry.cases.length < 1 || entry.cases.length > 32) throw new Error('F0.VC preflight worker assignment is malformed');
    seen.add(entry.worker); namespaces.add(entry.namespace); ports.add(entry.port); displays.add(entry.display); worlds.add(entry.world);
    const caseIds = new Set(); for (const item of entry.cases) {
      if (!item || typeof item.id !== 'string' || caseIds.has(item.id) || !['isolated', 'batch'].includes(item.lifecycle)
          || typeof item.world !== 'string' || (item.lifecycle === 'batch' && item.world !== entry.world)
          || (item.lifecycle === 'isolated' && item.world === entry.world && entry.cases.length > 1)) throw new Error('F0.VC preflight case reuse is incompatible');
      caseIds.add(item.id);
    }
  }
  if ([...seen].sort().join(',') !== WORKERS.join(',')) throw new Error('F0.VC preflight has missing or duplicate workers');
  const expected = new Set(plan.workers.map((entry) => `f0vc-${plan.runId}-${entry.worker}`));
  if (new Set(plan.expectedArtifacts).size !== 4 || plan.expectedArtifacts.some((name) => !expected.has(name))) throw new Error('F0.VC preflight artifact contract is incomplete or foreign');
  return Object.freeze(structuredClone(plan));
}

export function mergeEvidence(plan, evidence) {
  const checked = preflight(plan);
  const base = { schema: 1, kind: 'frontier-v3-f0vc-native-merge', planSha256: hash(JSON.stringify(checked)), runtimeContentSha256: checked.runtimeContentSha256 };
  try {
    if (!Array.isArray(evidence) || evidence.length !== 4) throw new Error('missing worker evidence');
    const expected = new Map(checked.workers.map((entry) => [entry.worker, entry])); const workers = new Set();
    for (const item of evidence) {
      const assignment = expected.get(item?.worker);
      const validJob = Number.isSafeInteger(item?.jobId) && item.jobId > 0;
      if (!assignment || workers.has(item.worker) || !JOB_STATUS.has(item.status) || item.runtimeContentSha256 !== checked.runtimeContentSha256
          || item.namespace !== assignment.namespace || item.port !== assignment.port || item.display !== assignment.display || item.world !== assignment.world
          || item.artifact !== `f0vc-${checked.runId}-${item.worker}` || (item.status === 'success' && !validJob)) throw new Error('foreign, duplicate or incomparable worker evidence');
      if (item.status !== 'success' || !item.result || item.result.status !== 'ok') throw new Error(`worker ${item.worker} did not produce successful fresh native evidence`);
      workers.add(item.worker);
    }
    if (workers.size !== 4) throw new Error('incomplete worker coverage');
    return Object.freeze({ ...base, status: 'ok', workers: [...evidence].sort((a, b) => a.worker.localeCompare(b.worker)) });
  } catch (failure) { return Object.freeze({ ...base, status: 'incomplete', failure: String(failure?.message ?? failure), availableWorkers: Array.isArray(evidence) ? evidence.map((item) => item?.worker).filter(Boolean).sort() : [] }); }
}

export async function writeWorkerEvidence({ output, worker, status, runtimeContentSha256, namespace, port, display, world, artifact, jobId, result }) {
  if (!WORKERS.includes(worker) || !JOB_STATUS.has(status) || !SHA.test(runtimeContentSha256 ?? '') || typeof namespace !== 'string' || !namespace.startsWith('/')
      || !Number.isInteger(port) || typeof display !== 'string' || typeof world !== 'string' || typeof artifact !== 'string'
      || (status === 'success' && (!Number.isSafeInteger(jobId) || jobId <= 0))
      || (jobId !== undefined && jobId !== null && (!Number.isSafeInteger(jobId) || jobId <= 0))) throw new Error('F0.VC worker evidence inputs are malformed');
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-f0vc-worker-evidence', worker, status, runtimeContentSha256, namespace, port, display, world, artifact,
    ...(jobId === undefined || jobId === null ? {} : { jobId }), result }, null, 2)}\n`, { flag: 'wx' });
}

export async function mergeDirectory({ plan, input, output }) {
  let entries; try { entries = await readdir(input, { withFileTypes: true }); } catch (error) { if (error?.code === 'ENOENT') entries = []; else throw error; }
  const evidence = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const path = resolve(input, entry.name, 'worker.json');
    try { evidence.push(JSON.parse(await readFile(path, 'utf8'))); } catch { /* represented by incomplete aggregate */ }
  }
  const merged = mergeEvidence(plan, evidence); await mkdir(dirname(output), { recursive: true }); await writeFile(output, `${JSON.stringify(merged, null, 2)}\n`, { flag: 'wx' });
  if (merged.status !== 'ok') throw new Error(`F0.VC merge is incomplete: ${merged.failure}`); return merged;
}
function hash(value) { return createHash('sha256').update(value).digest('hex'); }

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.replace(/^--/, '').split('=', 2); return [key, value]; }));
  if (values.mode === 'merge') await mergeDirectory({ plan: JSON.parse(await readFile(resolve(values.plan), 'utf8')), input: resolve(values.input), output: resolve(values.output) });
  else if (values.mode === 'preflight') { const checked = preflight(JSON.parse(await readFile(resolve(values.plan), 'utf8'))); console.log(JSON.stringify(checked)); }
  else throw new Error('usage: f0vc-pipeline --mode=preflight|merge ...');
}
