import { spawn } from 'node:child_process';
import { createWriteStream } from 'node:fs';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { consumeRuntime } from './f0vc-prepared-runtime.mjs';
import { preflight } from './f0vc-pipeline.mjs';
import { validateFourWorkerMatrixPlan } from './ci-matrix.mjs';
import { compilePersistentWorkerPlan } from './persistent-worker-plan.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

async function main() {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.replace(/^--/, '').split('=', 2); return [key, value]; }));
  const worker = values.worker; const output = resolve(project, values.output ?? '');
  const plan = preflight(JSON.parse(await readFile(resolve(project, values.preflight ?? ''), 'utf8')));
  const assignment = plan.workers.find((entry) => entry.worker === worker); if (!assignment) throw new Error('F0.VC worker is not assigned by immutable preflight');
  if (process.env.FRONTIER_V3_PILOT_PORT !== String(assignment.port) || process.env.DISPLAY !== assignment.display) throw new Error('F0.VC worker native namespace is foreign');
  const matrixArtifact = JSON.parse(await readFile(resolve(project, values.matrix ?? ''), 'utf8'));
  const matrix = validateFourWorkerMatrixPlan(matrixArtifact.plan);
  if (matrix.planSha256 !== assignment.matrixPlanSha256
      || compilePersistentWorkerPlan(matrix, worker, 'correctness-1').contentSha256 !== assignment.assignmentContentSha256) {
    throw new Error('F0.VC worker executable assignment is foreign to immutable preflight');
  }
  const runtime = JSON.parse(await readFile(resolve(project, values.runtime ?? ''), 'utf8'));
  if (runtime.contentSha256 !== plan.runtimeContentSha256 || typeof runtime.manifest !== 'string') throw new Error('F0.VC worker runtime receipt is foreign');
  const started = Date.now();
  const localIdentity = `build/f0vc/consumer/${worker}/prepared-build.json`;
  const consumed = await consumeRuntime({ manifest: runtime.manifest, worker, output: localIdentity });
  const result = `build/f0vc/consumer/${worker}/matrix.json`;
  const nativeLog = `build/f0vc/consumer/${worker}/native-run.log`;
  const code = await child(process.execPath, [resolve(project, 'tools/frontier-v3-test-pilot/src/run-ci-matrix-shard.mjs'),
    `--plan=${values.matrix}`, `--prepared=${localIdentity}`, `--worker=${worker}`, '--measurement=correctness-1', `--output=${result}`], {
    FRONTIER_V3_PREPARED_BUILD_IDENTITY: resolve(project, localIdentity), FRONTIER_V3_F0VC_PREPARED_RUNTIME: 'true'
  }, resolve(project, nativeLog));
  if (code !== 0) throw new Error(`F0.VC native worker failed (${code})`);
  const matrixResult = JSON.parse(await readFile(resolve(project, result), 'utf8'));
  if (matrixResult.status !== 'ok') throw new Error('F0.VC native worker matrix is incomplete');
  const primaryLifecycle = await retainPrimaryLifecycle(matrixResult, worker, assignment, dirname(resolve(project, output)));
  await mkdir(dirname(output), { recursive: true }); await writeFile(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-f0vc-native-worker-result', status: 'ok', worker,
    runtimeContentSha256: runtime.contentSha256, preflightPlan: values.preflight, matrix: result, nativeLog, consumer: consumed.receipt,
    primaryLifecycle, timing: { startedEpochMillis: started, finishedEpochMillis: Date.now() } }, null, 2)}\n`, { flag: 'wx' });
}

async function retainPrimaryLifecycle(matrixResult, worker, assignment, consumerRoot) {
  const reports = new Set(matrixResult?.lanes?.map((lane) => lane?.report));
  if (reports.size !== 1 || typeof [...reports][0] !== 'string') throw new Error('F0.VC worker has no unique primary lifecycle receipt');
  const source = resolve(project, [...reports][0]);
  if (!inside(project, source)) throw new Error('F0.VC primary lifecycle receipt escapes consumer workspace');
  let receipt; try { receipt = JSON.parse(await readFile(source, 'utf8')); } catch { throw new Error('F0.VC primary lifecycle receipt is unreadable'); }
  const projection = lifecycleProjection(receipt, worker);
  const artifactPath = 'primary/persistent-matrix.json'; const target = resolve(consumerRoot, artifactPath);
  await mkdir(dirname(target), { recursive: true }); await copyFile(source, target);
  return Object.freeze({ worker, artifactPath, sha256: hash(await readFile(target)), assignmentContentSha256: assignment.assignmentContentSha256,
    matrixPlanSha256: assignment.matrixPlanSha256, projection });
}

export function lifecycleProjection(receipt, worker) {
  const plan = receipt?.session?.plan; const evidence = receipt?.evidence;
  if (receipt?.status !== 'ok' || plan?.kind !== 'frontier-v3-assigned-persistent-matrix' || plan.workerId !== worker
      || !Number.isInteger(receipt?.client?.clientPid) || receipt.client.clientPid <= 1 || !Array.isArray(receipt?.serverRuns)
      || receipt.serverRuns.length < 1 || !Array.isArray(evidence?.worldKeys) || evidence.worldKeys.length < 1
      || !Array.isArray(receipt?.lifecycle?.events) || receipt.lifecycle.events.length < 1 || !Array.isArray(receipt?.crashReceipts)
      || receipt?.finalStop?.durableSave !== true || receipt?.finalStop?.portClosed !== true) {
    throw new Error('F0.VC primary lifecycle receipt is incomplete or foreign');
  }
  return Object.freeze({ clientPid: receipt.client.clientPid, serverRunIds: Object.freeze(receipt.serverRuns.map((run) => run?.serverRunId)),
    worldKeys: Object.freeze([...evidence.worldKeys]), lifecycleEventCount: receipt.lifecycle.events.length,
    crashReceiptCount: receipt.crashReceipts.length, durableFinalStop: true });
}
function inside(root, path) { const value = relative(resolve(root), resolve(path)); return value !== '' && !value.startsWith('..') && !value.includes('/..'); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
function child(command, args, environment, logPath) {
  return new Promise((resolveExit, rejectExit) => {
    const log = createWriteStream(logPath, { flags: 'wx' });
    const task = spawn(command, args, { cwd: project, env: { ...process.env, ...environment }, stdio: ['ignore', 'pipe', 'pipe'] });
    task.stdout.pipe(log, { end: false }); task.stderr.pipe(log, { end: false });
    task.once('error', (error) => { log.end(); rejectExit(error); });
    task.once('exit', (code, signal) => log.end(() => resolveExit(code ?? (signal === null ? 1 : 128))));
  });
}
