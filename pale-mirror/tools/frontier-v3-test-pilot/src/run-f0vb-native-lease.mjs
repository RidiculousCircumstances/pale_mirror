import { createServer } from 'node:net';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { F0VB_EVIDENCE_KIND, F0VB_EVIDENCE_SCHEMA, assertWorkerEvidence } from './f0vb-qualification.mjs';

const values = Object.fromEntries(process.argv.slice(2).map((value) => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const namespacePath = resolve(values.namespaces ?? '');
const identityPath = resolve(values.identity ?? '');
const output = resolve(values.output ?? '');
const holdMillis = Number(values['hold-millis'] ?? '');
if (!namespacePath.startsWith(`${process.cwd()}/`) || !identityPath.startsWith(`${process.cwd()}/`) || !output.startsWith(`${process.cwd()}/`)
  || !Number.isSafeInteger(holdMillis) || holdMillis < 1_000 || holdMillis > 60_000) throw new Error('F0.VB native lease invocation is malformed');
const namespaces = JSON.parse(await readFile(namespacePath, 'utf8'));
const identity = JSON.parse(await readFile(identityPath, 'utf8'));
assertConsumedNamespaces(namespaces);
for (const root of [namespaces.gradle, namespaces.cache, namespaces.world, namespaces.process]) await mkdir(root, { recursive: true });
const marker = resolve(namespaces.process, `f0vb-native-lease-${process.pid}.json`);
const server = createServer();
await listen(server, namespaces.port);
const startedAtMillis = Date.now();
await writeFile(marker, `${JSON.stringify({ kind: 'f0vb-native-process-marker', pid: process.pid, port: namespaces.port, display: process.env.DISPLAY, startedAtMillis })}\n`, { flag: 'wx' });
await Promise.all([
  writeFile(resolve(namespaces.temp, '.f0vb-native-temp-owner'), `${process.pid}\n`, { flag: 'wx' }),
  writeFile(resolve(namespaces.gradle, '.f0vb-native-gradle-owner'), `${process.pid}\n`, { flag: 'wx' }),
  writeFile(resolve(namespaces.cache, '.f0vb-native-cache-owner'), `${process.pid}\n`, { flag: 'wx' }),
  writeFile(resolve(namespaces.world, '.f0vb-native-world-owner'), `${process.pid}\n`, { flag: 'wx' })
]);
await new Promise((resolveDelay) => setTimeout(resolveDelay, holdMillis));
await close(server);
const finishedAtMillis = Date.now();
const evidence = {
  schema: F0VB_EVIDENCE_SCHEMA, kind: F0VB_EVIDENCE_KIND, worker: values.worker, qualificationId: values.qualification,
  repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'], workflowRef: values.workflow,
  runId: Number(values.run), runAttempt: Number(values.attempt), jobId: identity.jobId, runnerId: identity.runnerId,
  runnerName: identity.runnerName, lease: namespaces.process, startedAtMillis, finishedAtMillis, namespaces,
  consumption: { pid: process.pid, boundHost: '127.0.0.1', port: namespaces.port, display: process.env.DISPLAY, processMarker: marker }
};
assertWorkerEvidence(evidence);
await mkdir(dirname(output), { recursive: true });
await writeFile(output, `${JSON.stringify(evidence)}\n`, { flag: 'wx' });

function assertConsumedNamespaces(namespaces) {
  if (!namespaces || typeof namespaces !== 'object') throw new Error('F0.VB native lease has no namespaces');
  const expected = { GRADLE_USER_HOME: namespaces.gradle, FRONTIER_V3_NATIVE_WORK_ROOT: namespaces.workspace,
    FRONTIER_V3_NATIVE_TEMP_ROOT: namespaces.temp, FRONTIER_V3_NATIVE_CACHE_ROOT: namespaces.cache,
    FRONTIER_V3_PILOT_WORLD_ROOT: namespaces.world, FRONTIER_V3_PILOT_PORT: String(namespaces.port),
    FRONTIER_V3_NATIVE_PROCESS_ROOT: namespaces.process, DISPLAY: namespaces.display };
  for (const [key, value] of Object.entries(expected)) if (process.env[key] !== value) throw new Error(`F0.VB native lease does not consume ${key}`);
}

function listen(server, port) { return new Promise((resolveListen, rejectListen) => { server.once('error', rejectListen); server.listen({ host: '127.0.0.1', port }, resolveListen); }); }
function close(server) { return new Promise((resolveClose, rejectClose) => server.close((error) => error ? rejectClose(error) : resolveClose())); }
